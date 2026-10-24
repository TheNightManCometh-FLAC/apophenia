// The isolator (plan §7): dialled in by you, never automatic. Three controls on an STFT of the take:
// - stereo position: keep or remove what's panned around a place (needs a stereo source);
// - tone ⇄ hit: harmonic/percussive balance, from median filtering the magnitude spectrogram across
//   time (tone) and across frequency (hit) (Fitzgerald, "Harmonic/percussive separation using median
//   filtering", DAFx 2010), soft masks;
// - band: low and high cuts.
// Output is mono. It runs on the player's reader thread (or the control thread for the note finder),
// never in the audio callback: it allocates when built, and the time median needs frames from the
// future, which the reader's read-ahead hides.
#pragma once

#include <algorithm>
#include <atomic>
#include <cmath>
#include <complex>
#include <cstdint>
#include <memory>
#include <vector>

namespace iso {

using Cx = std::complex<float>;

// In-place radix-2 complex FFT; inverse when inv (unscaled).
inline void fft(std::vector<Cx> &a, bool inv) {
    const size_t n = a.size();
    for (size_t i = 1, j = 0; i < n; ++i) {
        size_t bit = n >> 1;
        for (; j & bit; bit >>= 1) j ^= bit;
        j ^= bit;
        if (i < j) std::swap(a[i], a[j]);
    }
    for (size_t len = 2; len <= n; len <<= 1) {
        const double ang = 2.0 * M_PI / static_cast<double>(len) * (inv ? 1.0 : -1.0);
        const Cx wl(static_cast<float>(std::cos(ang)), static_cast<float>(std::sin(ang)));
        for (size_t i = 0; i < n; i += len) {
            Cx w(1.0f, 0.0f);
            for (size_t k = 0; k < len / 2; ++k) {
                const Cx u = a[i + k], v = a[i + k + len / 2] * w;
                a[i + k] = u + v;
                a[i + k + len / 2] = u - v;
                w *= wl;
            }
        }
    }
}

// Live settings, written by the control thread, read once per frame.
struct Params {
    std::atomic<int> panMode{0};          // 0 off, 1 keep around panCenter, 2 remove around it
    std::atomic<float> panCenter{0.0f};   // -1 left .. +1 right
    std::atomic<float> panWidth{0.25f};   // half-width of the kept/removed zone, 0.05..1
    std::atomic<float> toneHit{0.0f};     // -1 tone only .. 0 both .. +1 hit only
    std::atomic<float> lowCut{0.0f};      // Hz, 0 = off
    std::atomic<float> highCut{0.0f};     // Hz, 0 = off
};

// A stereo (or mono: r == l) take in memory, read through the isolator from any frame.
class Source {
public:
    static constexpr int N = 2048, H = 512, T = 17;   // window, hop, time-median length (frames)
    static constexpr int F = 17;                      // frequency-median length (bins)
    static constexpr int B = N / 2 + 1;

    Source(std::shared_ptr<const std::vector<float>> l, std::shared_ptr<const std::vector<float>> r, int rate, const Params &p)
        : l_(std::move(l)), r_(std::move(r)), rate_(rate), p_(p), win_(N), spec_(N) {
        for (int i = 0; i < N; ++i) win_[static_cast<size_t>(i)] = static_cast<float>(std::sqrt(0.5 - 0.5 * std::cos(2.0 * M_PI * i / N)));
        frames_.assign(T, std::vector<Cx>(static_cast<size_t>(B) * 2));
        mags_.assign(T, std::vector<float>(B));
        tmp_.resize(std::max(T, F));
    }

    bool stereo() const { return l_ != r_; }
    int64_t length() const { return static_cast<int64_t>(l_->size()); }

    // Fills out[0..n) with isolated frames [from, from + n). Sequential reads are cheap; a jump re-primes.
    void read(int64_t from, float *out, int n) {
        if (from != next_) reset(from);
        int done = 0;
        while (done < n) {
            const int64_t ready = olaStart_ + H;              // frames before this are final
            while (next_ < ready && done < n) {
                const int64_t k = next_ - olaStart_;
                out[done++] = ola_[static_cast<size_t>(k)];
                ++next_;
            }
            if (done < n) step();
        }
    }

private:
    float at(const std::vector<float> &x, int64_t i) const { return (i >= 0 && i < static_cast<int64_t>(x.size())) ? x[static_cast<size_t>(i)] : 0.0f; }

    void reset(int64_t from) {
        next_ = from;
        // The first synthesized frame must start N - H before `from` so every sample from `from` on gets
        // all four overlapping frames; T/2 frames before it fill the time median.
        const int64_t firstOut = from - (N - H);
        anaPos_ = firstOut - static_cast<int64_t>(T / 2) * H;
        count_ = 0;
        ola_.assign(static_cast<size_t>(N), 0.0f);
        olaStart_ = firstOut - H;   // nothing final yet
        for (int i = 0; i < T; ++i) analyse();
        // Synthesize until the window that starts at firstOut has been added.
        while (olaStart_ + H <= from) step();
    }

    // Analyse the next window into the ring (L in the first B bins, R in the next B).
    void analyse() {
        for (int i = 0; i < N; ++i) {
            const float w = win_[static_cast<size_t>(i)];
            spec_[static_cast<size_t>(i)] = Cx(w * at(*l_, anaPos_ + i), w * at(*r_, anaPos_ + i));
        }
        fft(spec_, false);
        auto &f = frames_[static_cast<size_t>(count_ % T)];
        auto &m = mags_[static_cast<size_t>(count_ % T)];
        for (int k = 0; k < B; ++k) {
            const Cx a = spec_[static_cast<size_t>(k)], b = std::conj(spec_[static_cast<size_t>((N - k) % N)]);
            const Cx L = (a + b) * 0.5f, R = (a - b) * Cx(0.0f, -0.5f);
            f[static_cast<size_t>(k)] = L;
            f[static_cast<size_t>(B + k)] = R;
            m[static_cast<size_t>(k)] = std::abs(L + R) * 0.5f;
        }
        ++count_;
        anaPos_ += H;
    }

    float median(float *v, int n) {
        std::nth_element(v, v + n / 2, v + n);
        return v[n / 2];
    }

    // Masks the middle frame of the ring, adds it to the output, then analyses one more frame.
    void step() {
        const int mid = static_cast<int>((count_ - 1 - T / 2) % T);
        const auto &f = frames_[static_cast<size_t>(mid)];
        const auto &m = mags_[static_cast<size_t>(mid)];
        const int panMode = p_.panMode.load(std::memory_order_relaxed);
        const float c = p_.panCenter.load(std::memory_order_relaxed);
        const float w = std::clamp(p_.panWidth.load(std::memory_order_relaxed), 0.05f, 1.0f);
        const float th = std::clamp(p_.toneHit.load(std::memory_order_relaxed), -1.0f, 1.0f);
        const float lo = p_.lowCut.load(std::memory_order_relaxed), hi = p_.highCut.load(std::memory_order_relaxed);
        const float gTone = std::min(1.0f, 1.0f - th), gHit = std::min(1.0f, 1.0f + th);
        const bool hpss = th != 0.0f;
        const bool pan = panMode != 0 && stereo();
        for (int k = 0; k < B; ++k) {
            float g = 1.0f;
            if (hpss) {
                for (int t = 0; t < T; ++t) tmp_[static_cast<size_t>(t)] = mags_[static_cast<size_t>(t)][static_cast<size_t>(k)];
                const float ht = median(tmp_.data(), T);
                int n = 0;
                for (int j = k - F / 2; j <= k + F / 2; ++j) if (j >= 0 && j < B) tmp_[static_cast<size_t>(n++)] = m[static_cast<size_t>(j)];
                const float pf = median(tmp_.data(), n);
                const float mh = ht * ht / (ht * ht + pf * pf + 1e-12f);
                g *= gTone * mh + gHit * (1.0f - mh);
            }
            const Cx L = f[static_cast<size_t>(k)], R = f[static_cast<size_t>(B + k)];
            if (pan) {
                const float al = std::abs(L), ar = std::abs(R);
                const float position = (ar - al) / (al + ar + 1e-12f);
                const float d = std::abs(position - c) / w;
                const float inZone = d >= 1.0f ? 0.0f : 0.5f + 0.5f * std::cos(static_cast<float>(M_PI) * d);
                g *= panMode == 1 ? inZone : 1.0f - inZone;
            }
            const float hz = static_cast<float>(k) * rate_ / N;
            if (lo > 0.0f) g *= std::clamp(std::log2(std::max(hz, 1.0f) / lo) * 3.0f + 1.0f, 0.0f, 1.0f);   // a third of an octave ramp up to the cut
            if (hi > 0.0f) g *= std::clamp(std::log2(hi / std::max(hz, 1.0f)) * 3.0f + 1.0f, 0.0f, 1.0f);
            const Cx y = (L + R) * (0.5f * g);
            spec_[static_cast<size_t>(k)] = y;
            if (k > 0 && k < N / 2) spec_[static_cast<size_t>(N - k)] = std::conj(y);
        }
        fft(spec_, true);
        // Overlap-add: shift the finished hop out, add the new frame (sqrt-Hann twice = Hann; four Hanns overlap to 2).
        std::move(ola_.begin() + H, ola_.end(), ola_.begin());
        std::fill(ola_.end() - H, ola_.end(), 0.0f);
        olaStart_ += H;
        const float scale = 0.5f / static_cast<float>(N);
        for (int i = 0; i < N; ++i) ola_[static_cast<size_t>(i)] += spec_[static_cast<size_t>(i)].real() * win_[static_cast<size_t>(i)] * scale;
        analyse();
    }

    std::shared_ptr<const std::vector<float>> l_, r_;
    int rate_;
    const Params &p_;
    std::vector<float> win_;
    std::vector<Cx> spec_;
    std::vector<std::vector<Cx>> frames_;
    std::vector<std::vector<float>> mags_;
    std::vector<float> tmp_;
    std::vector<float> ola_;
    int64_t olaStart_ = 0;   // source frame of ola_[0]; frames < olaStart_ + H are final
    int64_t anaPos_ = 0;     // start of the next window to analyse
    int64_t next_ = -1;      // next frame read() will hand out
    int64_t count_ = 0;      // windows analysed since the last reset
};

}  // namespace iso
