// The app's audio engine (official plan §12): one output stream feeding one mixer (take playback,
// metronome click), and one microphone owner whose frames fan out to the recorder and the tuner.
//
// Threads:
// - control: every JNI entry point, called from one Kotlin worker thread (AudioController), never
//   the UI thread and never two at once;
// - audio: the two Oboe callbacks. They allocate nothing, lock nothing, log nothing, call no JNI;
// - player reader: decodes the loaded take into an SPSC ring ahead of the output callback (stack
//   layers are decoded whole on the control thread instead);
// - recorder writer: drains the input ring to a .part WAV, finalized and renamed on stop.

#include <jni.h>
#include <oboe/Oboe.h>
#include <signalsmith-stretch/signalsmith-stretch.h>
#include "isolator.h"

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <map>
#include <memory>
#include <string>
#include <thread>
#include <vector>
#include <unistd.h>

namespace {

constexpr int kRate = 48000;                 // requested for both streams; files are written at this rate
constexpr int kInputRingSeconds = 30;        // slack for a slow disk while recording
constexpr int kMaxCallbackFrames = 8192;
constexpr int kSeamFrames = kRate / 100;     // 10 ms linear crossfade at a stack loop seam

void flushDenormals() {
#if defined(__aarch64__)
    uint64_t fpcr;
    asm volatile("mrs %0, fpcr" : "=r"(fpcr));
    asm volatile("msr fpcr, %0" : : "r"(fpcr | (1ULL << 24)));
#endif
}

// ---- WAV: 24-bit PCM mono, fixed 44-byte header ---------------------------------------------

void writeWavHeader(FILE *f, int rate, uint32_t dataBytes) {
    auto u32 = [&](uint32_t v) { fwrite(&v, 4, 1, f); };
    auto u16 = [&](uint16_t v) { fwrite(&v, 2, 1, f); };
    fwrite("RIFF", 1, 4, f); u32(36 + dataBytes); fwrite("WAVE", 1, 4, f);
    fwrite("fmt ", 1, 4, f); u32(16); u16(1); u16(1); u32(rate); u32(rate * 3); u16(3); u16(24);
    fwrite("data", 1, 4, f); u32(dataBytes);
}

struct WavInfo {
    int rate = 0, channels = 0, bits = 0;
    long dataOffset = 0;
    int64_t frames = 0;
};

// Walks the RIFF chunks, so WAVs with extra chunks before "data" still play.
bool readWavInfo(FILE *f, WavInfo &info) {
    char id[4]; uint32_t size;
    if (fread(id, 1, 4, f) != 4 || memcmp(id, "RIFF", 4) != 0) return false;
    fread(&size, 4, 1, f);
    if (fread(id, 1, 4, f) != 4 || memcmp(id, "WAVE", 4) != 0) return false;
    bool haveFmt = false;
    while (fread(id, 1, 4, f) == 4 && fread(&size, 4, 1, f) == 1) {
        if (memcmp(id, "fmt ", 4) == 0) {
            uint16_t fmt, ch, align, bits; uint32_t rate, byteRate;
            fread(&fmt, 2, 1, f); fread(&ch, 2, 1, f); fread(&rate, 4, 1, f);
            fread(&byteRate, 4, 1, f); fread(&align, 2, 1, f); fread(&bits, 2, 1, f);
            if (fmt != 1 || (bits != 16 && bits != 24) || ch < 1 || ch > 2) return false;
            info.rate = static_cast<int>(rate); info.channels = ch; info.bits = bits;
            haveFmt = true;
            fseek(f, static_cast<long>(size) - 16 + (size & 1), SEEK_CUR);
        } else if (memcmp(id, "data", 4) == 0) {
            if (!haveFmt) return false;
            info.dataOffset = ftell(f);
            fseek(f, 0, SEEK_END);
            const long avail = ftell(f) - info.dataOffset;
            // A crashed take can still say 0 here; trust the file length then.
            const long bytes = (size == 0 || size > static_cast<uint32_t>(avail)) ? avail : static_cast<long>(size);
            info.frames = bytes / (info.channels * info.bits / 8);
            return true;
        } else {
            fseek(f, static_cast<long>(size) + (size & 1), SEEK_CUR);
        }
    }
    return false;
}

// ---- Rings ----------------------------------------------------------------------------------

// Single writer (input callback), any number of cursor readers. The writer never waits: a reader
// that falls more than a ring behind has lost frames and is told so.
class InputRing {
public:
    explicit InputRing(size_t capacity) : buf_(capacity) {}
    void write(const float *data, int n) {
        const int64_t w = written_.load(std::memory_order_relaxed);
        for (int i = 0; i < n; ++i) buf_[static_cast<size_t>(w + i) % buf_.size()] = data[i];
        written_.store(w + n, std::memory_order_release);
    }
    int64_t written() const { return written_.load(std::memory_order_acquire); }
    int64_t capacity() const { return static_cast<int64_t>(buf_.size()); }
    // Copies frames [from, from + n). Returns false if any of them were overwritten meanwhile.
    bool read(int64_t from, float *dst, int n) const {
        for (int i = 0; i < n; ++i) dst[i] = buf_[static_cast<size_t>(from + i) % buf_.size()];
        return written_.load(std::memory_order_acquire) - from <= capacity();
    }
    void reset() { written_.store(0, std::memory_order_release); }
private:
    std::vector<float> buf_;
    std::atomic<int64_t> written_{0};
};

// ---- Stack: layers decoded into memory, mixed sample-accurately on the stack's timeline -----
// [Claude] Layers are whole takes in RAM (192 KB per second of audio), so loops, offsets and the
// bounce need no disk reads on the audio thread. Conform (tempo/key) will mix cached renders the same way.

using Pcm = std::vector<float>;

enum LayerMode { kLoop = 0, kOneShot = 1, kFree = 2 };

struct Layer {
    std::shared_ptr<const Pcm> pcm;
    std::string path;
    int64_t id = 0;
    int mode = kLoop;
    int64_t start = 0;            // stack frame where the layer begins
    int64_t from = 0, to = 0;     // region of the take played (loop points / trim)
    std::atomic<float> gain{1.0f};
    std::atomic<bool> mute{false};
    std::atomic<bool> solo{false};
    float applied = 0.0f;         // audio thread: the gain actually reached (ramped per block)

    int64_t regionLength() const { return to - from; }
    // How much of the stack timeline the layer covers once (a loop counts one pass).
    int64_t span() const { return start + regionLength(); }
    bool sameGeometry(const Layer &o) const { return path == o.path && mode == o.mode && start == o.start && from == o.from && to == o.to; }

    // The layer's sample at stack frame t. Loop seams are crossfaded linearly (the two sides are the
    // same sound, so equal-gain keeps the level; see the audio-crossfade-laws skill).
    float at(int64_t t) const {
        const int64_t rel = t - start;
        const int64_t len = regionLength();
        if (rel < 0 || len <= 0) return 0.0f;
        const Pcm &p = *pcm;
        if (mode != kLoop) {
            const int64_t i = from + rel;
            return i < to ? p[static_cast<size_t>(i)] : 0.0f;
        }
        const int64_t k = rel % len;
        const int64_t n = std::min<int64_t>(kSeamFrames, len / 4);
        const float v = p[static_cast<size_t>(from + k)];
        if (n <= 0) return v;
        if (from >= n && k >= len - n) {                 // tail into the frames just before the loop start
            const float t01 = static_cast<float>(k - (len - n) + 1) / static_cast<float>(n + 1);
            return v * (1.0f - t01) + p[static_cast<size_t>(from - n + (k - (len - n)))] * t01;
        }
        if (from < n && to + n <= static_cast<int64_t>(p.size()) && k < n && rel >= len) {   // head after a repeat
            const float t01 = static_cast<float>(k + 1) / static_cast<float>(n + 1);
            return v * t01 + p[static_cast<size_t>(to + k)] * (1.0f - t01);
        }
        if (from < n && to + n > static_cast<int64_t>(p.size()) && k >= len - n) {          // whole take looped
            const float t01 = static_cast<float>(k - (len - n) + 1) / static_cast<float>(n + 1);
            return v * (1.0f - t01) + p[static_cast<size_t>(from + (k - (len - n)))] * t01;
        }
        return v;
    }
};

struct Mix {
    std::vector<std::shared_ptr<Layer>> layers;
    int64_t length = 0;           // the stack repeats at its longest layer [Claude]
    bool anySolo = false;
};

// Decodes a WAV (16 or 24-bit, any channel count) to mono float. Control thread only.
std::shared_ptr<const Pcm> decodeWav(const std::string &path) {
    FILE *f = fopen(path.c_str(), "rb");
    if (!f) return nullptr;
    WavInfo info;
    if (!readWavInfo(f, info)) { fclose(f); return nullptr; }
    fseek(f, info.dataOffset, SEEK_SET);
    auto out = std::make_shared<Pcm>(static_cast<size_t>(info.frames));
    const int bytesPer = info.bits / 8;
    std::vector<uint8_t> raw(4096 * info.channels * bytesPer);
    int64_t done = 0;
    while (done < info.frames) {
        const int want = static_cast<int>(std::min<int64_t>(4096, info.frames - done));
        const size_t got = fread(raw.data(), static_cast<size_t>(info.channels * bytesPer), want, f);
        if (got == 0) break;
        for (size_t i = 0; i < got; ++i) {
            float sum = 0.0f;
            for (int c = 0; c < info.channels; ++c) {
                const uint8_t *q = raw.data() + (i * info.channels + c) * bytesPer;
                int32_t v = bytesPer == 3 ? static_cast<int32_t>((q[0] << 8) | (q[1] << 16) | (q[2] << 24)) >> 8
                                          : static_cast<int16_t>(q[0] | (q[1] << 8));
                sum += static_cast<float>(v) / (bytesPer == 3 ? 8388608.0f : 32768.0f);
            }
            (*out)[static_cast<size_t>(done) + i] = sum / static_cast<float>(info.channels);
        }
        done += static_cast<int64_t>(got);
    }
    fclose(f);
    out->resize(static_cast<size_t>(done));
    return out;
}

// Decodes a WAV keeping its two channels (mono: both sides are the same buffer), for the isolator's
// stereo position. Control thread only.
struct StereoPcm { std::shared_ptr<const Pcm> l, r; };

StereoPcm decodeWavStereo(const std::string &path) {
    FILE *f = fopen(path.c_str(), "rb");
    if (!f) return {};
    WavInfo info;
    if (!readWavInfo(f, info)) { fclose(f); return {}; }
    fseek(f, info.dataOffset, SEEK_SET);
    auto l = std::make_shared<Pcm>(static_cast<size_t>(info.frames));
    auto r = info.channels > 1 ? std::make_shared<Pcm>(static_cast<size_t>(info.frames)) : l;
    const int bytesPer = info.bits / 8;
    std::vector<uint8_t> raw(4096 * info.channels * bytesPer);
    int64_t done = 0;
    while (done < info.frames) {
        const int want = static_cast<int>(std::min<int64_t>(4096, info.frames - done));
        const size_t got = fread(raw.data(), static_cast<size_t>(info.channels * bytesPer), want, f);
        if (got == 0) break;
        for (size_t i = 0; i < got; ++i) {
            for (int c = 0; c < std::min(info.channels, 2); ++c) {
                const uint8_t *q = raw.data() + (i * info.channels + c) * bytesPer;
                int32_t v = bytesPer == 3 ? static_cast<int32_t>((q[0] << 8) | (q[1] << 16) | (q[2] << 24)) >> 8
                                          : static_cast<int16_t>(q[0] | (q[1] << 8));
                (c == 0 ? *l : *r)[static_cast<size_t>(done) + i] = static_cast<float>(v) / (bytesPer == 3 ? 8388608.0f : 32768.0f);
            }
        }
        done += static_cast<int64_t>(got);
    }
    fclose(f);
    l->resize(static_cast<size_t>(done));
    if (r != l) r->resize(static_cast<size_t>(done));
    return {l, r};
}

// The isolator's live settings (plan §7), shared by the playing take and the note finder.
iso::Params gIso;

// ---- Player: one take, from memory, through the stretcher on a reader thread -------------------
// Playback speed (plan §7): 0.25–2× without pitch change, pitch ±24 semitones and ±100 cents, an A/B
// loop whose speed can rise each pass, and a count-in before each pass. The reader thread does the
// stretching (Signalsmith Stretch, streaming) and pushes frames tagged with the source frame they
// came from, so the position shown is always a place in the take. At 1× and no transpose the take is
// copied straight through. Changing any setting starts a new player at the same place; the output
// crossfades the two over one callback.

struct PlayParams {
    double speed = 1.0;            // 0.25..2
    double transpose = 1.0;        // pitch factor (semitones and cents together)
    int64_t loopA = -1, loopB = -1;
    double stepBy = 0.0, stepTo = 1.0;    // step-up loops: speed += stepBy after each pass, up to stepTo
    int countInBeats = 0;
    double beatFrames = 0.0;       // the beat length in source frames (from the take's BPM), 0 = unknown
    bool isolate = false;          // read through the isolator (gIso settings, live)
};

struct PlayFrame { float v; int64_t src; };

template <typename T>
class Ring {
public:
    explicit Ring(size_t capacity) : buf_(capacity) {}
    size_t capacity() const { return buf_.size(); }
    size_t freeSpace() const { return buf_.size() - (write_.load(std::memory_order_relaxed) - read_.load(std::memory_order_acquire)); }
    size_t push(const T *data, size_t n) {
        const size_t w = write_.load(std::memory_order_relaxed);
        const size_t r = read_.load(std::memory_order_acquire);
        const size_t k = std::min(n, buf_.size() - (w - r));
        for (size_t i = 0; i < k; ++i) buf_[(w + i) % buf_.size()] = data[i];
        write_.store(w + k, std::memory_order_release);
        return k;
    }
    size_t pop(T *data, size_t n) {
        const size_t r = read_.load(std::memory_order_relaxed);
        const size_t w = write_.load(std::memory_order_acquire);
        const size_t k = std::min(n, w - r);
        for (size_t i = 0; i < k; ++i) data[i] = buf_[(r + i) % buf_.size()];
        read_.store(r + k, std::memory_order_release);
        return k;
    }
    bool empty() const { return write_.load(std::memory_order_acquire) == read_.load(std::memory_order_acquire); }
private:
    std::vector<T> buf_;
    std::atomic<size_t> write_{0};
    std::atomic<size_t> read_{0};
};

class Player {
public:
    Ring<PlayFrame> ring{static_cast<size_t>(kRate)};   // 1 s ahead
    std::atomic<bool> paused{false};
    std::atomic<bool> readerDone{false};
    std::atomic<bool> ended{false};
    std::atomic<int64_t> position{0};   // source frame of the last frame handed to the output
    std::atomic<int> pass{0};           // loop passes completed
    std::atomic<float> speedNow{1.0f};
    int64_t frames = 0;
    int rate = kRate;

    ~Player() { stop_.store(true); if (reader_.joinable()) reader_.join(); }

    bool open(std::shared_ptr<const Pcm> p, int64_t startFrame, const PlayParams &params, StereoPcm stereo = {}) {
        if (!p || p->empty()) return false;
        pcm_ = std::move(p);
        prm_ = params;
        prm_.speed = std::clamp(prm_.speed, 0.25, 2.0);
        prm_.transpose = std::clamp(prm_.transpose, 0.25, 4.0);
        frames = static_cast<int64_t>(pcm_->size());
        if (!(prm_.loopA >= 0 && prm_.loopB > prm_.loopA + kRate / 20 && prm_.loopB <= frames)) prm_.loopA = prm_.loopB = -1;
        start_ = std::clamp<int64_t>(startFrame, 0, frames);
        if (prm_.loopA >= 0 && (start_ < prm_.loopA || start_ >= prm_.loopB)) start_ = prm_.loopA;
        position.store(start_);
        speedNow.store(static_cast<float>(prm_.speed));
        if (prm_.isolate && stereo.l) iso_ = std::make_unique<iso::Source>(stereo.l, stereo.r, kRate, gIso);
        reader_ = std::thread([this] { readLoop(); });
        return true;
    }

private:
    // Pushes everything, waiting for room; false if stopped meanwhile.
    bool pushAll(const PlayFrame *f, size_t n) {
        while (n > 0) {
            if (stop_.load()) return false;
            const size_t k = ring.push(f, n);
            f += k; n -= k;
            if (n > 0) std::this_thread::sleep_for(std::chrono::milliseconds(5));
        }
        return true;
    }

    // [Claude] Count-in clicks at the playing tempo (the beat divided by the speed), the first accented.
    bool countIn(double speed, int64_t src) {
        const auto beat = static_cast<size_t>(prm_.beatFrames / speed);
        if (beat < static_cast<size_t>(kRate / 20)) return true;
        std::vector<PlayFrame> buf(beat);
        for (int b = 0; b < prm_.countInBeats; ++b) {
            const double hz = b == 0 ? 1600.0 : 1000.0;
            const double len = kRate / 40.0;
            for (size_t i = 0; i < beat; ++i) {
                const double t = static_cast<double>(i);
                const float v = t < len ? static_cast<float>(0.5 * std::exp(-t / (len / 5.0)) * std::sin(2.0 * M_PI * hz * t / kRate)) : 0.0f;
                buf[i] = {v, src};
            }
            if (!pushAll(buf.data(), buf.size())) return false;
        }
        return true;
    }

    void readLoop() {
        const Pcm &x = *pcm_;
        const int64_t n = static_cast<int64_t>(x.size());
        const bool loop = prm_.loopA >= 0;
        const int64_t end = loop ? prm_.loopB : n;
        const int fade = kRate / 100;   // 10 ms out and in at a loop seam
        double speed = prm_.speed;
        signalsmith::stretch::SignalsmithStretch<float> st;
        st.presetDefault(1, static_cast<float>(kRate));
        st.setTransposeFactor(static_cast<float>(prm_.transpose));
        constexpr int kBlock = 512;
        std::vector<float> in(static_cast<size_t>(kBlock * 3 + st.outputSeekLength(2.0f) + 16)), out(kBlock);
        std::vector<PlayFrame> buf(kBlock);
        double outSrc = 0, inFrac = 0;
        int64_t inCursor = 0;
        int fadeIn = 0;
        auto plain = [&] { return speed == 1.0 && prm_.transpose == 1.0; };
        // The source: the take, or the take through the isolator (reads are sequential except at seeks).
        auto source = [&](int64_t from, float *dst, int len) {
            if (iso_) { iso_->read(from, dst, len); return; }
            for (int i = 0; i < len; ++i) { const int64_t k = from + i; dst[i] = (k >= 0 && k < n) ? x[static_cast<size_t>(k)] : 0.0f; }
        };
        auto fill = [&](int64_t from, int len) {
            if (static_cast<size_t>(len) > in.size()) in.resize(static_cast<size_t>(len));
            source(from, in.data(), len);
        };
        auto seekTo = [&](int64_t at, bool fadeFromSilence) {
            outSrc = static_cast<double>(at);
            fadeIn = fadeFromSilence ? fade : 0;
            if (plain()) { inCursor = at; return; }
            const int len = st.outputSeekLength(static_cast<float>(speed));
            fill(at, len);
            float *ch[1] = {in.data()};
            st.outputSeek(ch, len);
            inCursor = at + len;
            inFrac = 0;
        };
        seekTo(start_, start_ > 0);
        while (!stop_.load()) {
            if (ring.freeSpace() < static_cast<size_t>(kBlock)) { std::this_thread::sleep_for(std::chrono::milliseconds(5)); continue; }
            if (outSrc >= static_cast<double>(end)) {
                if (!loop) { readerDone.store(true, std::memory_order_release); break; }
                pass.fetch_add(1);
                if (prm_.stepBy != 0.0) {
                    speed = prm_.stepBy > 0 ? std::min(speed + prm_.stepBy, prm_.stepTo) : std::max(speed + prm_.stepBy, prm_.stepTo);
                    speed = std::clamp(speed, 0.25, 2.0);
                    speedNow.store(static_cast<float>(speed));
                }
                if (prm_.countInBeats > 0 && prm_.beatFrames > 0 && !countIn(speed, prm_.loopA)) break;
                seekTo(prm_.loopA, true);
                continue;
            }
            if (plain()) {
                source(inCursor, out.data(), kBlock);
                inCursor += kBlock;
            } else {
                const double want = kBlock * speed + inFrac;
                const int inN = static_cast<int>(want);
                inFrac = want - inN;
                fill(inCursor, inN);
                float *ich[1] = {in.data()};
                float *och[1] = {out.data()};
                st.process(ich, inN, och, kBlock);
                inCursor += inN;
            }
            const double step = plain() ? 1.0 : speed;
            int count = 0;
            for (int i = 0; i < kBlock && outSrc < static_cast<double>(end); ++i) {
                float g = 1.0f;
                if (fadeIn > 0) { g = 1.0f - static_cast<float>(fadeIn) / static_cast<float>(fade + 1); --fadeIn; }
                if (loop) g *= static_cast<float>(std::clamp((static_cast<double>(end) - outSrc) / (step * fade), 0.0, 1.0));
                buf[static_cast<size_t>(i)] = {out[static_cast<size_t>(i)] * g, static_cast<int64_t>(outSrc)};
                outSrc += step;
                ++count;
            }
            if (!pushAll(buf.data(), static_cast<size_t>(count))) break;
        }
    }

    std::shared_ptr<const Pcm> pcm_;
    std::unique_ptr<iso::Source> iso_;
    PlayParams prm_;
    int64_t start_ = 0;
    std::atomic<bool> stop_{false};
    std::thread reader_;
};

// ---- Metronome program (built by core MetronomeSettings.bar(); see the plan §7) ---------------

constexpr int kMaxClicks = 256;
enum ClickSound { kStrong = 0, kAccent = 1, kNormal = 2, kSub = 3, kPoly = 4 };

struct BarEvents {
    double units = 4.0;          // bar length in BPM units
    int n = 0;
    double at[kMaxClicks];       // event time in BPM units from the bar start, ascending
    int sound[kMaxClicks];
    float gain[kMaxClicks];
    int beat[kMaxClicks];        // beat index in the bar, -1 for the poly layer
};

struct ClickProgram {
    BarEvents bar, countIn;
    int countInBars = 0;
    int trainerStep = 0, trainerEvery = 1, trainerMax = 0;   // step 0 = no trainer
    int silentPlay = 0, silentMute = 0;                      // play 0 = no silent bars

    // Mirrors MetronomeSettings.bpmAtBar / audible.
    double bpmAt(int64_t barIndex, double base) const {
        if (trainerStep == 0 || barIndex < 0) return base;
        const double raw = base + static_cast<double>(barIndex / trainerEvery) * trainerStep;
        return trainerStep > 0 ? std::min(raw, static_cast<double>(trainerMax)) : std::max(raw, static_cast<double>(trainerMax));
    }
    bool audible(int64_t barIndex) const {
        if (silentPlay <= 0 || barIndex < 0) return true;
        return barIndex % (silentPlay + silentMute) < silentPlay;
    }
};

// Clicks heard, for the grid saved with a take. Written by the audio thread only; the control thread
// reads entries count-kGridLog..count-1 (an entry older than that has been overwritten).
constexpr int kGridLog = 8192;
constexpr int kGridBeat = 1, kGridDownbeat = 2, kGridCountIn = 4;

struct ClickVoice { bool on = false; int sound = 0; float gain = 0; double t = 0; };

// ---- Recorder ---------------------------------------------------------------------------------

struct Recording {
    std::string partPath, finalPath;
    FILE *file = nullptr;
    int64_t fileStart = 0;              // input-ring frame of the file's first frame
    int inputOpensAtStart = 0, outputOpensAtStart = 0;
    int64_t cursor = 0;                 // next input-ring frame to write
    int64_t prerollFrames = 0;
    std::atomic<int64_t> stopAt{-1};    // input-ring frame to stop at; -1 = keep going
    std::atomic<int64_t> framesWritten{0};
    std::atomic<int64_t> lostFrames{0};
    std::atomic<bool> diskError{false};
    std::thread writer;
};

// ---- Engine state shared with the callbacks ---------------------------------------------------

class Engine;
Engine *gEngine = nullptr;

class Engine {
public:
    // output
    std::shared_ptr<oboe::AudioStream> out;
    std::atomic<Player *> player{nullptr};
    std::vector<Player *> retired;           // control thread only
    std::atomic<int64_t> outCallbacks{0};
    std::vector<int64_t> retiredAt;
    std::atomic<bool> clickOn{false};
    std::atomic<double> bpm{96.0};
    std::atomic<int64_t> beats{0};           // beats played since the click was turned on
    std::atomic<ClickProgram *> program{nullptr};
    std::vector<ClickProgram *> retiredPrograms;   // control thread only
    std::vector<int64_t> retiredProgramsAt;
    // audio thread: where the click is
    const ClickProgram *clickSeen = nullptr;
    bool clickWasOn = false;
    int64_t clickBar = 0;                    // negative while counting in
    double clickUnits = 0.0;                 // BPM units into the bar
    int clickNext = 0;                       // next event in the bar
    ClickVoice voices[8];
    // shown by the UI
    std::atomic<int64_t> clickBarOut{0};
    std::atomic<int> clickBeatOut{-1};
    std::atomic<double> clickBpmOut{0.0};
    std::atomic<float> clickVolume{0.7f};
    // the grid log
    int64_t gridFrame[kGridLog];
    int gridFlags[kGridLog];
    std::atomic<int64_t> gridCount{0};
    std::atomic<int64_t> outFrames{0};       // frames rendered since the engine started (all output streams)
    std::atomic<int64_t> outFramesAtOpen{0}; // outFrames when the current output stream opened

    // drone: up to four partials of a sustained tone
    std::atomic<bool> droneOn{false};
    std::atomic<int> droneCount{0};
    std::atomic<float> droneHz[4];
    double dronePhase[4] = {0, 0, 0, 0};
    float droneLevel = 0.0f;                 // audio thread: ramps to on/off

    // cue: a short sound heard while writing (one note, a chord), mixed beside the player so the take
    // loaded there stays put. Five slots: the control thread fills one that is neither published nor
    // claimed, playing or fading by the audio thread, then publishes it (slot + 5 * generation).
    static constexpr int kCueSlots = 5;
    static constexpr int kCueFrames = kRate * 2;
    static constexpr int kCueFade = kRate / 200;   // 5 ms: a new cue fades the old one out instead of cutting it
    std::vector<float> cue[kCueSlots];
    int cueLen[kCueSlots] = {};              // written before publish, read after the load of cuePublished
    std::atomic<int> cuePublished{-1};
    std::atomic<int> cueClaim{-1};           // audio thread: the slot it is about to start
    std::atomic<int> cuePlaying{-1};         // audio thread: the slot it reads
    std::atomic<int> cueFadingSlot{-1};      // audio thread: the slot it fades out
    int cueGen = 0;                          // control thread
    int cueSeen = -1, cueSlot = -1, cueOld = -1;   // audio thread
    int64_t cuePos = 0, cueOldPos = 0;
    int cueOldLeft = 0;

    std::vector<float> scratch = std::vector<float>(kMaxCallbackFrames);
    std::vector<PlayFrame> playScratch = std::vector<PlayFrame>(kMaxCallbackFrames);
    Player *lastPlayer = nullptr;            // audio thread: the player mixed in the last callback
    std::atomic<bool> outDisconnected{false};

    // stack
    std::atomic<Mix *> mix{nullptr};
    std::atomic<bool> stackPlaying{false};
    std::atomic<int64_t> stackPos{0};        // audio thread writes; the control thread reads
    std::atomic<int64_t> stackSeek{-1};      // control thread asks; the audio thread applies
    std::vector<Mix *> retiredMixes;         // control thread only
    std::vector<int64_t> retiredMixesAt;
    std::map<std::string, std::weak_ptr<const Pcm>> decoded;   // control thread only
    std::map<std::string, std::pair<std::weak_ptr<const Pcm>, std::weak_ptr<const Pcm>>> decodedStereo;   // control thread only

    // input
    std::shared_ptr<oboe::AudioStream> in;
    InputRing inputRing{static_cast<size_t>(kRate) * kInputRingSeconds};
    std::atomic<float> peak{0.0f};
    std::atomic<bool> tuner{false};          // the tuner's screen holds the mic open, without pre-roll
    std::atomic<bool> inDisconnected{false};
    int inputOpens = 0, outputOpens = 0;     // control thread: a reopen mid-take breaks grid alignment
    int64_t inRingAtOpen = 0;                // input-ring frame of the current input stream's first frame
    std::vector<int64_t> lastGrid;           // control thread: [aligned, n, frame, flags, frame, flags…]
    std::vector<int64_t> lastGridCounted;    // control thread: the same clicks placed by frame counts only, as overdubs are

    Engine() {
        for (auto &h : droneHz) h.store(0.0f);
        for (auto &c : cue) c.assign(kCueFrames, 0.0f);
    }
    int inputPreset = static_cast<int>(oboe::InputPreset::VoiceRecognition);
    int inputDevice = oboe::kUnspecified;    // control thread: the mic to open (the phone's own while headphones are the output)
    std::unique_ptr<Recording> rec;

    void retire(Player *p) {
        if (!p) return;
        retired.push_back(p);
        retiredAt.push_back(outCallbacks.load());
    }
    void retire(Mix *m) {
        if (!m) return;
        retiredMixes.push_back(m);
        retiredMixesAt.push_back(outCallbacks.load());
    }
    void retire(ClickProgram *p) {
        if (!p) return;
        retiredPrograms.push_back(p);
        retiredProgramsAt.push_back(outCallbacks.load());
    }
    // A retired player, mix or click program is freed once the output has run two callbacks since, or is stopped.
    void sweep() {
        const bool running = out && out->getState() == oboe::StreamState::Started;
        for (size_t i = 0; i < retiredPrograms.size();) {
            if (!running || outCallbacks.load() > retiredProgramsAt[i] + 1) {
                delete retiredPrograms[i];
                retiredPrograms.erase(retiredPrograms.begin() + static_cast<long>(i));
                retiredProgramsAt.erase(retiredProgramsAt.begin() + static_cast<long>(i));
            } else ++i;
        }
        for (size_t i = 0; i < retiredMixes.size();) {
            if (!running || outCallbacks.load() > retiredMixesAt[i] + 1) {
                delete retiredMixes[i];
                retiredMixes.erase(retiredMixes.begin() + static_cast<long>(i));
                retiredMixesAt.erase(retiredMixesAt.begin() + static_cast<long>(i));
            } else ++i;
        }
        for (size_t i = 0; i < retired.size();) {
            if (!running || outCallbacks.load() > retiredAt[i] + 1) {
                delete retired[i];
                retired.erase(retired.begin() + static_cast<long>(i));
                retiredAt.erase(retiredAt.begin() + static_cast<long>(i));
            } else ++i;
        }
    }
};

// The metronome, sample-accurate: the bar position advances in BPM units at the current bar's tempo,
// and a click starts on the first frame at or after its event time. Tempo changes (the − / + keys, the
// trainer) take effect from the next frame without restarting the bar. Audio thread only.
void renderClick(Engine &e, float *mix, int frames, int rate) {
    const bool on = e.clickOn.load(std::memory_order_relaxed);
    const ClickProgram *p = e.program.load(std::memory_order_acquire);
    if (!p) return;
    if (on && !e.clickWasOn) {
        e.clickBar = -p->countInBars;
        e.clickUnits = 0.0;
        e.clickNext = 0;
        e.clickBeatOut.store(-1, std::memory_order_relaxed);
        e.clickBarOut.store(e.clickBar, std::memory_order_relaxed);
        e.clickBpmOut.store(p->bpmAt(e.clickBar, std::clamp(e.bpm.load(std::memory_order_relaxed), 20.0, 400.0)), std::memory_order_relaxed);
        e.clickSeen = p;
    }
    e.clickWasOn = on;
    if (p != e.clickSeen) {   // a new program mid-bar: carry on from the same place in the bar
        e.clickSeen = p;
        const BarEvents &b = e.clickBar < 0 ? p->countIn : p->bar;
        if (e.clickUnits >= b.units) e.clickUnits = 0.0;
        e.clickNext = 0;
        while (e.clickNext < b.n && b.at[e.clickNext] < e.clickUnits) ++e.clickNext;
    }
    const double base = std::clamp(e.bpm.load(std::memory_order_relaxed), 20.0, 400.0);
    const float volume = e.clickVolume.load(std::memory_order_relaxed);
    const int64_t frame0 = e.outFrames.load(std::memory_order_relaxed);
    const double clickLen = rate / 40.0;
    for (int i = 0; i < frames; ++i) {
        if (on) {
            const BarEvents *b = e.clickBar < 0 ? &p->countIn : &p->bar;
            while (e.clickNext < b->n && b->at[e.clickNext] <= e.clickUnits) {
                const int k = e.clickNext++;
                const bool counting = e.clickBar < 0;
                if (b->beat[k] >= 0 && b->sound[k] != kSub) {
                    int flags = kGridBeat | (b->beat[k] == 0 ? kGridDownbeat : 0) | (counting ? kGridCountIn : 0);
                    const int64_t g = e.gridCount.load(std::memory_order_relaxed);
                    e.gridFrame[g % kGridLog] = frame0 + i;
                    e.gridFlags[g % kGridLog] = flags;
                    e.gridCount.store(g + 1, std::memory_order_release);
                    e.beats.fetch_add(1, std::memory_order_relaxed);
                    e.clickBeatOut.store(b->beat[k], std::memory_order_relaxed);
                    e.clickBarOut.store(e.clickBar, std::memory_order_relaxed);
                }
                if (p->audible(e.clickBar)) {
                    for (auto &v : e.voices) if (!v.on) { v.on = true; v.sound = b->sound[k]; v.gain = b->gain[k]; v.t = 0; break; }
                }
            }
            const double bpmNow = p->bpmAt(e.clickBar, base);
            e.clickUnits += bpmNow / (60.0 * rate);
            if (e.clickUnits >= b->units) {
                e.clickUnits -= b->units;
                ++e.clickBar;
                e.clickNext = 0;
                e.clickBpmOut.store(p->bpmAt(e.clickBar, base), std::memory_order_relaxed);
            }
        }
        float s = 0.0f;
        for (auto &v : e.voices) {
            if (!v.on) continue;
            // Short pitched blips: strong 1.6 kHz, accent 1.25 kHz, beat 1 kHz, subdivision 800 Hz, poly 2.4 kHz.
            static constexpr double hz[5] = {1600.0, 1250.0, 1000.0, 800.0, 2400.0};
            const double env = std::exp(-v.t / (clickLen / 5.0));
            s += v.gain * static_cast<float>(env * std::sin(2.0 * M_PI * hz[v.sound] * v.t / rate));
            v.t += 1.0;
            if (v.t >= clickLen) v.on = false;
        }
        mix[i] += 0.5f * volume * s;
    }
}

// The drone: each note a sine with a little second and third harmonic, faded in and out over 200 ms.
void renderDrone(Engine &e, float *mix, int frames, int rate) {
    const bool on = e.droneOn.load(std::memory_order_relaxed);
    if (!on && e.droneLevel <= 0.0f) return;
    const int n = std::clamp(e.droneCount.load(std::memory_order_acquire), 0, 4);
    const float step = 1.0f / (0.2f * static_cast<float>(rate));
    const float perNote = n > 0 ? 0.3f / std::sqrt(static_cast<float>(n)) : 0.0f;
    double inc[4];
    for (int k = 0; k < n; ++k) inc[k] = 2.0 * M_PI * e.droneHz[k].load(std::memory_order_relaxed) / rate;
    for (int i = 0; i < frames; ++i) {
        e.droneLevel = on ? std::min(1.0f, e.droneLevel + step) : std::max(0.0f, e.droneLevel - step);
        float s = 0.0f;
        for (int k = 0; k < n; ++k) {
            const double ph = e.dronePhase[k];
            s += static_cast<float>(std::sin(ph) + 0.3 * std::sin(2 * ph) + 0.12 * std::sin(3 * ph));
            e.dronePhase[k] = std::fmod(ph + inc[k], 2.0 * M_PI);
        }
        mix[i] += e.droneLevel * perNote * s;
    }
}

// The cue: the newest published sound, once. Starting a new one fades the old one out over kCueFade.
// The claim, then a second look at cuePublished, keeps the control thread off a slot this is about to read.
void renderCue(Engine &e, float *mix, int frames) {
    const int v = e.cuePublished.load();
    if (v >= 0 && v != e.cueSeen) {
        const int s = v % Engine::kCueSlots;
        e.cueClaim.store(s);
        if (e.cuePublished.load() == v) {
            if (e.cueSlot >= 0 && e.cuePos < e.cueLen[e.cueSlot]) {
                e.cueOld = e.cueSlot; e.cueOldPos = e.cuePos; e.cueOldLeft = Engine::kCueFade;
                e.cueFadingSlot.store(e.cueOld);
            }
            e.cuePlaying.store(s);
            e.cueSeen = v; e.cueSlot = s; e.cuePos = 0;
        }
        e.cueClaim.store(-1);
    }
    if (e.cueOld >= 0) {
        const float *o = e.cue[e.cueOld].data();
        const int64_t len = e.cueLen[e.cueOld];
        for (int i = 0; i < frames && e.cueOldLeft > 0 && e.cueOldPos < len; ++i, --e.cueOldLeft)
            mix[i] += o[e.cueOldPos++] * static_cast<float>(e.cueOldLeft) / Engine::kCueFade;
        if (e.cueOldLeft <= 0 || e.cueOldPos >= len) { e.cueOld = -1; e.cueFadingSlot.store(-1); }
    }
    if (e.cueSlot >= 0) {
        const float *c = e.cue[e.cueSlot].data();
        const int64_t len = e.cueLen[e.cueSlot];
        for (int i = 0; i < frames && e.cuePos < len; ++i) mix[i] += c[e.cuePos++];
    }
}

class OutputCallback : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *s, void *data, int32_t n) override {
        Engine &e = *gEngine;
        if (e.outCallbacks.load(std::memory_order_relaxed) == 0) flushDenormals();
        auto *o = static_cast<float *>(data);
        const int ch = s->getChannelCount();
        const int rate = s->getSampleRate();
        const int frames = std::min<int32_t>(n, kMaxCallbackFrames);
        float *mix = e.scratch.data();
        std::fill(mix, mix + frames, 0.0f);

        // The take: a new player (any setting changed) fades in over this block while the one it
        // replaced fades out; the replaced one stays alive two callbacks after it was retired.
        Player *p = e.player.load(std::memory_order_acquire);
        Player *was = e.lastPlayer;
        e.lastPlayer = p;
        auto pull = [&](Player *q, float g0, float g1) {
            if (!q || q->paused.load(std::memory_order_relaxed) || q->ended.load(std::memory_order_relaxed)) return;
            PlayFrame *f = e.playScratch.data();
            const size_t got = q->ring.pop(f, static_cast<size_t>(frames));
            const float step = (g1 - g0) / static_cast<float>(frames);
            for (size_t i = 0; i < got; ++i) mix[i] += f[i].v * (g0 + step * static_cast<float>(i));
            if (got > 0) q->position.store(f[got - 1].src, std::memory_order_relaxed);
            if (got < static_cast<size_t>(frames) && q->readerDone.load(std::memory_order_acquire) && q->ring.empty())
                q->ended.store(true, std::memory_order_release);
        };
        if (was != p && was) { pull(was, 1.0f, 0.0f); pull(p, 0.0f, 1.0f); } else pull(p, 1.0f, 1.0f);

        // Stack: every layer at its own gain, ramped across the block so gain, mute and solo never click.
        Mix *m = e.mix.load(std::memory_order_acquire);
        const int64_t seek = e.stackSeek.exchange(-1, std::memory_order_acq_rel);
        if (seek >= 0) e.stackPos.store(seek, std::memory_order_relaxed);
        const bool stackOn = e.stackPlaying.load(std::memory_order_relaxed);
        // Stopping ramps every layer to silence over one block (no click), then nothing is mixed.
        if (m && m->length > 0) {
            const int64_t pos = e.stackPos.load(std::memory_order_relaxed) % m->length;
            bool played = false;
            for (const auto &sp : m->layers) {
                Layer *l = sp.get();
                const bool silent = !stackOn || l->mute.load(std::memory_order_relaxed) || (m->anySolo && !l->solo.load(std::memory_order_relaxed));
                const float target = silent ? 0.0f : l->gain.load(std::memory_order_relaxed);
                const float g0 = l->applied;
                if (g0 == 0.0f && target == 0.0f) continue;
                const float step = (target - g0) / static_cast<float>(frames);
                int64_t t = pos;
                for (int i = 0; i < frames; ++i) {
                    mix[i] += (g0 + step * static_cast<float>(i + 1)) * l->at(t);
                    if (++t >= m->length) t = 0;
                }
                l->applied = target;
                played = true;
            }
            if (played || stackOn) e.stackPos.store((pos + frames) % m->length, std::memory_order_relaxed);
        }

        renderClick(e, mix, frames, rate);
        renderDrone(e, mix, frames, rate);
        renderCue(e, mix, frames);

        for (int i = 0; i < frames; ++i) {
            const float v = std::clamp(mix[i], -1.0f, 1.0f);
            for (int c = 0; c < ch; ++c) o[i * ch + c] = v;
        }
        for (int i = frames * ch; i < n * ch; ++i) o[i] = 0.0f;
        e.outFrames.fetch_add(n, std::memory_order_relaxed);
        e.outCallbacks.fetch_add(1, std::memory_order_release);
        return oboe::DataCallbackResult::Continue;
    }
    void onErrorAfterClose(oboe::AudioStream *, oboe::Result) override { gEngine->outDisconnected.store(true); }
};

class InputCallback : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *, void *data, int32_t n) override {
        Engine &e = *gEngine;
        const auto *in = static_cast<const float *>(data);
        e.inputRing.write(in, n);
        float pk = e.peak.load(std::memory_order_relaxed);
        for (int i = 0; i < n; ++i) pk = std::max(pk, std::fabs(in[i]));
        e.peak.store(pk, std::memory_order_relaxed);
        return oboe::DataCallbackResult::Continue;
    }
    void onErrorAfterClose(oboe::AudioStream *, oboe::Result) override { gEngine->inDisconnected.store(true); }
};

std::shared_ptr<OutputCallback> gOutCb = std::make_shared<OutputCallback>();
std::shared_ptr<InputCallback> gInCb = std::make_shared<InputCallback>();

Engine &engine() {
    if (!gEngine) {
        gEngine = new Engine();   // lives for the process
        // 4/4, first beat strong, until the app sends the saved metronome settings.
        auto *p = new ClickProgram();
        for (BarEvents *b : {&p->bar, &p->countIn}) {
            b->units = 4.0; b->n = 4;
            for (int i = 0; i < 4; ++i) { b->at[i] = i; b->sound[i] = i == 0 ? kStrong : kNormal; b->gain[i] = 1.0f; b->beat[i] = i; }
        }
        gEngine->program.store(p);
    }
    return *gEngine;
}

bool ensureOutput() {
    Engine &e = engine();
    if (e.out && !e.outDisconnected.load() && e.out->getState() == oboe::StreamState::Started) return true;
    if (e.out) { e.out->close(); e.out.reset(); }
    e.outDisconnected.store(false);
    oboe::AudioStreamBuilder b;
    b.setDirection(oboe::Direction::Output)
     ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
     ->setSharingMode(oboe::SharingMode::Exclusive)
     ->setFormat(oboe::AudioFormat::Float)
     ->setChannelCount(oboe::ChannelCount::Stereo)
     ->setSampleRate(kRate)
     ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
     ->setUsage(oboe::Usage::Media)
     ->setContentType(oboe::ContentType::Music)
     ->setDataCallback(gOutCb)
     ->setErrorCallback(gOutCb);
    if (b.openStream(e.out) != oboe::Result::OK) { e.out.reset(); return false; }
    e.outFramesAtOpen.store(e.outFrames.load());
    e.lastPlayer = nullptr;   // the stream is not running yet: no callback can be using it
    ++e.outputOpens;
    if (e.out->requestStart() != oboe::Result::OK) { e.out->close(); e.out.reset(); return false; }
    return true;
}

bool ensureInput() {
    Engine &e = engine();
    if (e.in && !e.inDisconnected.load() && e.in->getState() == oboe::StreamState::Started) return true;
    if (e.in) { e.in->close(); e.in.reset(); }
    e.inDisconnected.store(false);
    if (!e.rec) e.inputRing.reset();   // a reopen mid-take keeps the ring counter running
    oboe::AudioStreamBuilder b;
    b.setDirection(oboe::Direction::Input)
     ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
     ->setSharingMode(oboe::SharingMode::Exclusive)
     ->setFormat(oboe::AudioFormat::Float)
     ->setFormatConversionAllowed(true)
     ->setChannelCount(oboe::ChannelCount::Mono)
     ->setChannelConversionAllowed(true)
     ->setSampleRate(kRate)
     ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
     ->setInputPreset(static_cast<oboe::InputPreset>(e.inputPreset))
     ->setDeviceId(e.inputDevice)
     ->setDataCallback(gInCb)
     ->setErrorCallback(gInCb);
    if (b.openStream(e.in) != oboe::Result::OK) { e.in.reset(); return false; }
    e.inRingAtOpen = e.inputRing.written();
    ++e.inputOpens;
    if (e.in->requestStart() != oboe::Result::OK) { e.in->close(); e.in.reset(); return false; }
    return true;
}

void closeInput() {
    Engine &e = engine();
    if (e.in) { e.in->stop(); e.in->close(); e.in.reset(); }
    e.inputRing.reset();
}

void writerLoop(Recording *r) {
    Engine &e = engine();
    std::vector<float> chunk(4096);
    std::vector<uint8_t> pcm(4096 * 3);
    int64_t sinceSync = 0;
    for (;;) {
        const int64_t written = e.inputRing.written();
        const int64_t stopAt = r->stopAt.load(std::memory_order_acquire);
        const int64_t limit = stopAt >= 0 ? std::min(stopAt, written) : written;
        if (written - r->cursor > e.inputRing.capacity()) {           // fell a whole ring behind
            const int64_t skipTo = written - e.inputRing.capacity() / 2;
            r->lostFrames.fetch_add(skipTo - r->cursor);
            r->cursor = skipTo;
            continue;
        }
        const int n = static_cast<int>(std::min<int64_t>(chunk.size(), limit - r->cursor));
        if (n <= 0) {
            if (stopAt >= 0 && r->cursor >= stopAt) break;
            if (stopAt >= 0 && e.inDisconnected.load()) break;      // input gone: keep what arrived
            std::this_thread::sleep_for(std::chrono::milliseconds(20));
            continue;
        }
        if (!e.inputRing.read(r->cursor, chunk.data(), n)) { r->lostFrames.fetch_add(n); r->cursor += n; continue; }
        for (int i = 0; i < n; ++i) {
            const int32_t v = static_cast<int32_t>(std::lrint(std::clamp(chunk[i], -1.0f, 1.0f) * 8388607.0f));
            pcm[i * 3] = static_cast<uint8_t>(v & 0xFF);
            pcm[i * 3 + 1] = static_cast<uint8_t>((v >> 8) & 0xFF);
            pcm[i * 3 + 2] = static_cast<uint8_t>((v >> 16) & 0xFF);
        }
        if (fwrite(pcm.data(), 3, static_cast<size_t>(n), r->file) != static_cast<size_t>(n)) r->diskError.store(true);
        r->cursor += n;
        r->framesWritten.fetch_add(n);
        sinceSync += n;
        if (sinceSync >= kRate * 5) { fflush(r->file); fsync(fileno(r->file)); sinceSync = 0; }  // a crash loses ≤ 5 s
    }
    const auto bytes = static_cast<uint32_t>(r->framesWritten.load() * 3);
    fflush(r->file);
    fseek(r->file, 0, SEEK_SET);
    writeWavHeader(r->file, kRate, bytes);
    fflush(r->file);
    fsync(fileno(r->file));
    fclose(r->file);
    r->file = nullptr;
    if (rename(r->partPath.c_str(), r->finalPath.c_str()) != 0) r->diskError.store(true);
}

// The clicks heard during a take, in the take file's frames (plan §7: the grid is saved with the take).
// Output and input timestamps put each click at the moment it left the speaker and find the mic frame
// captured at that moment, so the grid lines up with what was played against it. Without timestamps,
// or if a stream reopened mid-take, frames are matched by count only and the grid is marked unaligned
// (it then sits late by the round trip). Control thread, after the writer has finished.
void captureGrid(Engine &e, const Recording &r) {
    e.lastGrid.assign(2, 0);
    e.lastGridCounted.clear();
    const int64_t stopAt = r.stopAt.load();
    const int64_t count = e.gridCount.load(std::memory_order_acquire);
    const int64_t first = std::max<int64_t>(0, count - kGridLog + 256);   // leave room for the audio thread to keep writing
    if (count == first) return;
    const double rate = kRate;
    bool aligned = false;
    double outPos = 0, outNs = 0, inPos = 0, inNs = 0;
    if (e.out && e.in && r.inputOpensAtStart == e.inputOpens && r.outputOpensAtStart == e.outputOpens) {
        auto to = e.out->getTimestamp(CLOCK_MONOTONIC);
        auto ti = e.in->getTimestamp(CLOCK_MONOTONIC);
        if (to && ti) {
            aligned = true;
            outPos = static_cast<double>(to.value().position); outNs = static_cast<double>(to.value().timestamp);
            inPos = static_cast<double>(ti.value().position); inNs = static_cast<double>(ti.value().timestamp);
        }
    }
    const int64_t outNow = e.outFrames.load(), inNow = e.inputRing.written();
    const int64_t atOpen = e.outFramesAtOpen.load();
    for (int64_t g = first; g < count; ++g) {
        const int64_t c = e.gridFrame[g % kGridLog];
        const int flags = e.gridFlags[g % kGridLog];
        double ring;
        if (aligned) {
            const double t = outNs + (static_cast<double>(c - atOpen) - outPos) * 1e9 / rate;
            ring = static_cast<double>(e.inRingAtOpen) + inPos + (t - inNs) * rate / 1e9;
        } else {
            ring = static_cast<double>(inNow - (outNow - c));
        }
        // By counts alone (frames handed to the output against frames taken from the input), the way a layer recorded
        // over a stack is placed: the play-along calibration measures the round trip against this.
        const int64_t counted = inNow - (outNow - c) - r.fileStart;
        if (counted >= 0 && counted < stopAt - r.fileStart) e.lastGridCounted.push_back(counted);
        const auto f = static_cast<int64_t>(std::llround(ring)) - r.fileStart;
        if (f < 0 || f >= stopAt - r.fileStart) continue;
        e.lastGrid.push_back(f);
        e.lastGrid.push_back(flags);
    }
    e.lastGrid[0] = aligned ? 1 : 0;
    e.lastGrid[1] = static_cast<int64_t>((e.lastGrid.size() - 2) / 2);
}

// The take's two channels, decoded once and shared while anything holds them. Control thread only.
StereoPcm stereoOf(Engine &e, const std::string &file) {
    auto &w = e.decodedStereo[file];
    StereoPcm s{w.first.lock(), w.second.lock()};
    if (!s.l || !s.r) { s = decodeWavStereo(file); w = {s.l, s.r}; }
    return s;
}

std::string str(JNIEnv *env, jstring s) {
    const char *c = env->GetStringUTFChars(s, nullptr);
    std::string out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}

} // namespace

// ---- JNI: com.betterappmafia.apophenia.audio.NativeEngine -----------------------------------
#define JNI() extern "C" JNIEXPORT
#define FN(name) Java_com_betterappmafia_apophenia_audio_NativeEngine_##name

JNI() void JNICALL FN(nativeSetInputPreset)(JNIEnv *, jobject, jint preset) { engine().inputPreset = preset; }

JNI() void JNICALL FN(nativeSetClick)(JNIEnv *, jobject, jboolean on, jdouble bpm) {
    Engine &e = engine();
    e.bpm.store(bpm);
    if (on) ensureOutput();
    e.clickOn.store(on);
}

JNI() jlong JNICALL FN(nativeBeats)(JNIEnv *, jobject) { return engine().beats.load(); }

namespace {
void fillBar(JNIEnv *env, BarEvents &b, jdouble units, jdoubleArray at, jintArray sound, jfloatArray gain, jintArray beat) {
    b.units = units > 0 ? units : 4.0;
    b.n = std::min<jsize>(env->GetArrayLength(at), kMaxClicks);
    env->GetDoubleArrayRegion(at, 0, b.n, b.at);
    env->GetIntArrayRegion(sound, 0, b.n, b.sound);
    env->GetFloatArrayRegion(gain, 0, b.n, b.gain);
    env->GetIntArrayRegion(beat, 0, b.n, b.beat);
    for (int i = 0; i < b.n; ++i) b.sound[i] = std::clamp(b.sound[i], 0, 4);
}
}

// The metronome's bar and count-in bar (from MetronomeSettings), trainer and silent bars. Swapping it
// while the click runs keeps the place in the bar.
JNI() void JNICALL FN(nativeClickProgram)(JNIEnv *env, jobject,
        jdouble units, jdoubleArray at, jintArray sound, jfloatArray gain, jintArray beat,
        jdouble cUnits, jdoubleArray cAt, jintArray cSound, jfloatArray cGain, jintArray cBeat,
        jint countInBars, jint trStep, jint trEvery, jint trMax, jint silPlay, jint silMute) {
    Engine &e = engine();
    auto *p = new ClickProgram();
    fillBar(env, p->bar, units, at, sound, gain, beat);
    fillBar(env, p->countIn, cUnits, cAt, cSound, cGain, cBeat);
    p->countInBars = std::clamp(countInBars, 0, 4);
    p->trainerStep = trStep; p->trainerEvery = std::max(1, trEvery); p->trainerMax = trMax;
    p->silentPlay = silPlay > 0 && silMute > 0 ? silPlay : 0; p->silentMute = silMute;
    e.retire(e.program.exchange(p, std::memory_order_acq_rel));
    e.sweep();
}

// state: [beats, bar (negative in the count-in), beat in the bar (-1 before the first), bpm × 1000]
JNI() void JNICALL FN(nativeClickState)(JNIEnv *env, jobject, jlongArray outArr) {
    Engine &e = engine();
    jlong s[4] = {e.beats.load(), e.clickBarOut.load(), e.clickBeatOut.load(), static_cast<jlong>(std::llround(e.clickBpmOut.load() * 1000))};
    env->SetLongArrayRegion(outArr, 0, 4, s);
}

JNI() void JNICALL FN(nativeSetClickVolume)(JNIEnv *, jobject, jfloat v) { engine().clickVolume.store(std::clamp(v, 0.0f, 1.0f)); }

// The grid of the take just stopped: [aligned, n, frame, flags, …] (flags: 1 beat, 2 downbeat, 4 count-in).
JNI() jlongArray JNICALL FN(nativeLastGrid)(JNIEnv *env, jobject) {
    const auto &g = engine().lastGrid;
    jlongArray a = env->NewLongArray(static_cast<jsize>(g.size()));
    if (a && !g.empty()) env->SetLongArrayRegion(a, 0, static_cast<jsize>(g.size()), g.data());
    return a;
}

// The clicks of the take just stopped, placed by frame counts only (see captureGrid).
JNI() jlongArray JNICALL FN(nativeLastGridCounted)(JNIEnv *env, jobject) {
    const auto &g = engine().lastGridCounted;
    jlongArray a = env->NewLongArray(static_cast<jsize>(g.size()));
    if (a && !g.empty()) env->SetLongArrayRegion(a, 0, static_cast<jsize>(g.size()), g.data());
    return a;
}

// The mic to open next (an AudioDeviceInfo id; 0 lets Android choose). Takes effect when the input next opens.
JNI() void JNICALL FN(nativeSetInputDevice)(JNIEnv *, jobject, jint id) { engine().inputDevice = id; }

// The drone: up to four frequencies; off fades out.
JNI() void JNICALL FN(nativeSetDrone)(JNIEnv *env, jobject, jboolean on, jfloatArray hzA) {
    Engine &e = engine();
    const jsize n = std::min<jsize>(env->GetArrayLength(hzA), 4);
    float hz[4] = {0, 0, 0, 0};
    env->GetFloatArrayRegion(hzA, 0, n, hz);
    for (int k = 0; k < 4; ++k) e.droneHz[k].store(std::clamp(hz[k], 0.0f, 8000.0f));
    e.droneCount.store(n, std::memory_order_release);
    if (on) ensureOutput();
    e.droneOn.store(on);
}

// Plays [pcm] (mono, kRate, at most two seconds) once, beside the player: a note heard while writing.
// Control thread only (one caller at a time).
JNI() void JNICALL FN(nativeCue)(JNIEnv *env, jobject, jfloatArray pcmA) {
    Engine &e = engine();
    const int published = e.cuePublished.load();
    const int busy[4] = {published < 0 ? -1 : published % Engine::kCueSlots, e.cueClaim.load(), e.cuePlaying.load(), e.cueFadingSlot.load()};
    int s = 0;
    while (std::find(std::begin(busy), std::end(busy), s) != std::end(busy)) ++s;   // five slots, four busy at most
    const jsize n = std::min<jsize>(env->GetArrayLength(pcmA), Engine::kCueFrames);
    env->GetFloatArrayRegion(pcmA, 0, n, e.cue[s].data());
    e.cueLen[s] = n;
    e.cuePublished.store(s + Engine::kCueSlots * (e.cueGen++ % 100000));
    ensureOutput();
}

// The tuner holds the mic open while its screen is up (plan §7), with no pre-roll. Returns false if
// the mic couldn't open.
JNI() jboolean JNICALL FN(nativeSetTuner)(JNIEnv *, jobject, jboolean on) {
    Engine &e = engine();
    e.tuner.store(on);
    if (on) return ensureInput();
    if (!e.rec) closeInput();
    return JNI_TRUE;
}

// Copies the newest dst.length mic frames (oldest first). Returns how many are real (fewer just after opening).
JNI() jint JNICALL FN(nativeReadInput)(JNIEnv *env, jobject, jfloatArray dst) {
    Engine &e = engine();
    const jsize n = env->GetArrayLength(dst);
    if (!e.in) return 0;
    const int64_t w = e.inputRing.written();
    const int64_t have = std::min<int64_t>(w, n);
    std::vector<float> buf(static_cast<size_t>(n), 0.0f);
    e.inputRing.read(w - have, buf.data() + (n - have), static_cast<int>(have));
    env->SetFloatArrayRegion(dst, 0, n, buf.data());
    return static_cast<jint>(have);
}

// Loads a take at startFrame (paused if !play) with playback-speed settings: speed 0.25..2, transpose
// as a pitch factor, an A/B loop (loopA < 0 = none) whose speed steps by stepBy each pass up to stepTo,
// countInBeats clicks before each pass at beatFrames per beat. The decoded take is shared with the
// stack and with the player it replaces, so changing a setting reloads nothing. Returns frames, or -1.
JNI() jlong JNICALL FN(nativePlay)(JNIEnv *env, jobject, jstring path, jlong startFrame, jboolean play, jdouble speed, jdouble transpose,
                                   jlong loopA, jlong loopB, jdouble stepBy, jdouble stepTo, jint countInBeats, jdouble beatFrames, jboolean isolate) {
    Engine &e = engine();
    if (!ensureOutput()) return -1;
    const std::string file = str(env, path);
    std::shared_ptr<const Pcm> pcm = e.decoded[file].lock();
    if (!pcm) { pcm = decodeWav(file); if (!pcm) return -1; e.decoded[file] = pcm; }
    PlayParams prm;
    prm.speed = speed; prm.transpose = transpose; prm.loopA = loopA; prm.loopB = loopB;
    prm.stepBy = stepBy; prm.stepTo = stepTo; prm.countInBeats = std::clamp(countInBeats, 0, 8); prm.beatFrames = beatFrames;
    prm.isolate = isolate;
    StereoPcm stereo;
    if (isolate) stereo = stereoOf(e, file);
    auto *p = new Player();
    p->paused.store(!play);
    if (!p->open(pcm, startFrame, prm, stereo)) { delete p; return -1; }
    e.retire(e.player.exchange(p, std::memory_order_acq_rel));
    e.sweep();
    return p->frames;
}

JNI() void JNICALL FN(nativeSetPaused)(JNIEnv *, jobject, jboolean paused) {
    Engine &e = engine();
    if (!paused) ensureOutput();
    if (Player *p = e.player.load()) p->paused.store(paused);
}

JNI() void JNICALL FN(nativeUnload)(JNIEnv *, jobject) {
    Engine &e = engine();
    e.retire(e.player.exchange(nullptr, std::memory_order_acq_rel));
    e.sweep();
}

// state: [position, frames, rate, ended, paused, loaded, loop passes, speed now × 1000]
JNI() void JNICALL FN(nativePlayerState)(JNIEnv *env, jobject, jlongArray outArr) {
    Engine &e = engine();
    Player *p = e.player.load();
    jlong s[8] = {0, 0, 0, 0, 1, 0, 0, 1000};
    if (p) s[0] = p->position.load(), s[1] = p->frames, s[2] = p->rate, s[3] = p->ended.load(), s[4] = p->paused.load(), s[5] = 1,
           s[6] = p->pass.load(), s[7] = std::llround(p->speedNow.load() * 1000.0);
    env->SetLongArrayRegion(outArr, 0, 8, s);
    e.sweep();
}

JNI() jfloat JNICALL FN(nativeTakePeak)(JNIEnv *, jobject) { return engine().peak.exchange(0.0f); }

// Starts writing partPath; on stop it is finalized and renamed to finalPath. Returns 0 (pre-roll frames), or -1.
JNI() jlong JNICALL FN(nativeStartRecording)(JNIEnv *env, jobject, jstring partPath, jstring finalPath) {
    Engine &e = engine();
    if (e.rec) return -1;
    if (!ensureInput()) return -1;
    auto r = std::make_unique<Recording>();
    r->partPath = str(env, partPath);
    r->finalPath = str(env, finalPath);
    r->file = fopen(r->partPath.c_str(), "wb");
    if (!r->file) { if (!e.tuner.load()) closeInput(); return -1; }
    writeWavHeader(r->file, kRate, 0);
    const int64_t now = e.inputRing.written();
    r->prerollFrames = 0;   // no pre-roll (Christopher, 22 Oct 2026); the field stays for older takes
    r->cursor = now - r->prerollFrames;
    r->fileStart = r->cursor;
    r->inputOpensAtStart = e.inputOpens;
    r->outputOpensAtStart = e.outputOpens;
    Recording *raw = r.get();
    r->writer = std::thread([raw] { writerLoop(raw); });
    e.rec = std::move(r);
    return raw->prerollFrames;
}

// rec: [framesWritten, prerollFrames, lostFrames, diskError, inputDisconnected]
JNI() jboolean JNICALL FN(nativeRecordingState)(JNIEnv *env, jobject, jlongArray outArr) {
    Engine &e = engine();
    if (!e.rec) return JNI_FALSE;
    jlong s[5] = {e.rec->framesWritten.load(), e.rec->prerollFrames, e.rec->lostFrames.load(),
                  e.rec->diskError.load() ? 1 : 0, e.inDisconnected.load() ? 1 : 0};
    env->SetLongArrayRegion(outArr, 0, 5, s);
    return JNI_TRUE;
}

// Stops at the current input frame, waits for the writer to finalize. Returns the same array as
// nativeRecordingState, or false if nothing was recording.
JNI() jboolean JNICALL FN(nativeStopRecording)(JNIEnv *env, jobject, jlongArray outArr) {
    Engine &e = engine();
    if (!e.rec) return JNI_FALSE;
    e.rec->stopAt.store(e.inputRing.written(), std::memory_order_release);
    e.rec->writer.join();
    captureGrid(e, *e.rec);
    jlong s[5] = {e.rec->framesWritten.load(), e.rec->prerollFrames, e.rec->lostFrames.load(),
                  e.rec->diskError.load() ? 1 : 0, e.inDisconnected.load() ? 1 : 0};
    env->SetLongArrayRegion(outArr, 0, 5, s);
    e.rec.reset();
    if (!e.tuner.load()) closeInput();
    return JNI_TRUE;
}

// Called when nothing needs sound any more (app in the background, nothing playing or recording).
JNI() void JNICALL FN(nativeIdle)(JNIEnv *, jobject) {
    Engine &e = engine();
    if (e.clickOn.load() || e.stackPlaying.load() || e.droneOn.load()) return;
    Player *p = e.player.load();
    if (p && !p->paused.load() && !p->ended.load()) return;
    if (e.out) { e.out->stop(); e.out->close(); e.out.reset(); }
    e.sweep();
}

JNI() jint JNICALL FN(nativeSampleRate)(JNIEnv *, jobject) { return kRate; }

// A stream the system closed (route change, another app took the device). Reading clears nothing:
// the next ensureOutput/ensureInput reopens and resets the flag.
JNI() jboolean JNICALL FN(nativeOutputLost)(JNIEnv *, jobject) { return engine().outDisconnected.load(); }
JNI() jboolean JNICALL FN(nativeInputLost)(JNIEnv *, jobject) { return engine().inDisconnected.load(); }

// ---- Stack ------------------------------------------------------------------------------------

// Replaces the stack's layers. Arrays are parallel, one entry per layer; to < 0 = the end of the take,
// gain is linear. A layer whose file can't be read is left out. Layers whose file and timing didn't
// change are kept (only their gain, mute and solo are updated), so editing one never clicks the others.
// Returns the stack length in frames, or -1 if no output.
JNI() jlong JNICALL FN(nativeStackSet)(JNIEnv *env, jobject, jlongArray idsA, jobjectArray pathsA, jintArray modesA,
                                       jlongArray startsA, jlongArray fromsA, jlongArray tosA, jfloatArray gainsA,
                                       jbooleanArray mutesA, jbooleanArray solosA) {
    Engine &e = engine();
    if (!ensureOutput()) return -1;
    const jsize n = env->GetArrayLength(idsA);
    std::vector<jlong> ids(n), starts(n), froms(n), tos(n);
    std::vector<jint> modes(n);
    std::vector<jfloat> gains(n);
    std::vector<jboolean> mutes(n), solos(n);
    env->GetLongArrayRegion(idsA, 0, n, ids.data());
    env->GetLongArrayRegion(startsA, 0, n, starts.data());
    env->GetLongArrayRegion(fromsA, 0, n, froms.data());
    env->GetLongArrayRegion(tosA, 0, n, tos.data());
    env->GetIntArrayRegion(modesA, 0, n, modes.data());
    env->GetFloatArrayRegion(gainsA, 0, n, gains.data());
    env->GetBooleanArrayRegion(mutesA, 0, n, mutes.data());
    env->GetBooleanArrayRegion(solosA, 0, n, solos.data());

    Mix *old = e.mix.load();
    auto *m = new Mix();
    for (jsize i = 0; i < n; ++i) {
        auto *js = static_cast<jstring>(env->GetObjectArrayElement(pathsA, i));
        const std::string path = str(env, js);
        env->DeleteLocalRef(js);
        std::shared_ptr<const Pcm> pcm = e.decoded[path].lock();
        if (!pcm) { pcm = decodeWav(path); if (!pcm) continue; e.decoded[path] = pcm; }
        auto l = std::make_shared<Layer>();
        l->pcm = pcm; l->path = path; l->id = ids[i]; l->mode = modes[i]; l->start = starts[i];
        const auto size = static_cast<int64_t>(pcm->size());
        l->from = std::clamp<int64_t>(froms[i], 0, size);
        l->to = tos[i] < 0 ? size : std::clamp<int64_t>(tos[i], l->from, size);
        if (l->regionLength() <= 0) continue;
        if (old) for (const auto &o : old->layers) if (o->id == l->id && o->sameGeometry(*l)) { l = o; break; }
        l->gain.store(std::isfinite(gains[i]) ? std::clamp(gains[i], 0.0f, 4.0f) : 1.0f);
        l->mute.store(mutes[i]);
        l->solo.store(solos[i]);
        m->anySolo = m->anySolo || solos[i];
        m->length = std::max(m->length, l->span());
        m->layers.push_back(l);
    }
    for (auto it = e.decoded.begin(); it != e.decoded.end();) it = it->second.expired() ? e.decoded.erase(it) : std::next(it);
    if (m->layers.empty()) { delete m; m = nullptr; }
    e.retire(e.mix.exchange(m, std::memory_order_acq_rel));
    e.sweep();
    if (!m) e.stackPlaying.store(false);
    return m ? m->length : 0;
}

JNI() void JNICALL FN(nativeStackPlay)(JNIEnv *, jobject, jboolean play) {
    Engine &e = engine();
    if (play && !ensureOutput()) return;
    e.stackPlaying.store(play && e.mix.load() != nullptr);
}

JNI() void JNICALL FN(nativeStackSeek)(JNIEnv *, jobject, jlong frame) { engine().stackSeek.store(std::max<jlong>(0, frame)); }

// state: [position, length, playing, layers]
JNI() void JNICALL FN(nativeStackState)(JNIEnv *env, jobject, jlongArray outArr) {
    Engine &e = engine();
    Mix *m = e.mix.load();
    jlong s[4] = {e.stackPos.load(), m ? m->length : 0, e.stackPlaying.load() ? 1 : 0, m ? static_cast<jlong>(m->layers.size()) : 0};
    env->SetLongArrayRegion(outArr, 0, 4, s);
    e.sweep();
}

// The stack's ● (confirmed by Christopher, 21 Oct 2026): its own output, no mic. [Claude] Rendered
// offline, one pass of the stack at the current gains, mutes and solos, to partPath, then renamed.
// Returns frames written, or -1. Writes peak (0..) into peakOut[0] so the UI can say if it clipped.
// The stack's output to a 24-bit WAV; with onlyLayer != 0, that one layer alone at its gain (a stem for the FL Studio
// package), whatever mute and solo say, over the whole stack length so every stem lines up from 0.
JNI() jlong JNICALL FN(nativeBounceStack)(JNIEnv *env, jobject, jstring partPath, jstring finalPath, jfloatArray peakOut, jlong onlyLayer) {
    Engine &e = engine();
    Mix *m = e.mix.load();
    if (!m || m->length <= 0) return -1;
    const std::string part = str(env, partPath), fin = str(env, finalPath);
    FILE *f = fopen(part.c_str(), "wb");
    if (!f) return -1;
    writeWavHeader(f, kRate, 0);
    std::vector<float> buf(4096);
    std::vector<uint8_t> pcm(4096 * 3);
    float peak = 0.0f;
    bool ok = true;
    for (int64_t t0 = 0; t0 < m->length && ok; t0 += static_cast<int64_t>(buf.size())) {
        const int n = static_cast<int>(std::min<int64_t>(buf.size(), m->length - t0));
        std::fill(buf.begin(), buf.begin() + n, 0.0f);
        for (const auto &l : m->layers) {
            if (onlyLayer != 0) { if (l->id != onlyLayer) continue; }
            else if (l->mute.load() || (m->anySolo && !l->solo.load())) continue;
            const float g = l->gain.load();
            for (int i = 0; i < n; ++i) buf[static_cast<size_t>(i)] += g * l->at(t0 + i);
        }
        for (int i = 0; i < n; ++i) {
            peak = std::max(peak, std::fabs(buf[static_cast<size_t>(i)]));
            const int32_t v = static_cast<int32_t>(std::lrint(std::clamp(buf[static_cast<size_t>(i)], -1.0f, 1.0f) * 8388607.0f));
            pcm[i * 3] = static_cast<uint8_t>(v & 0xFF);
            pcm[i * 3 + 1] = static_cast<uint8_t>((v >> 8) & 0xFF);
            pcm[i * 3 + 2] = static_cast<uint8_t>((v >> 16) & 0xFF);
        }
        ok = fwrite(pcm.data(), 3, static_cast<size_t>(n), f) == static_cast<size_t>(n);
    }
    fflush(f);
    fseek(f, 0, SEEK_SET);
    writeWavHeader(f, kRate, static_cast<uint32_t>(m->length * 3));
    fflush(f);
    fsync(fileno(f));
    fclose(f);
    env->SetFloatArrayRegion(peakOut, 0, 1, &peak);
    if (!ok || rename(part.c_str(), fin.c_str()) != 0) { remove(part.c_str()); return -1; }
    return m->length;
}

// ---- Conform renders --------------------------------------------------------------------------
// Called from the app's render worker, not the audio-control thread: it touches no engine state and
// may take seconds. Renders the whole take stretched to lengthFactor × its length and shifted by
// semitones (Signalsmith Stretch, exact(): latency-compensated, exact output length), 24-bit WAV to
// partPath, renamed to finalPath. Returns output frames, or -1.
JNI() jlong JNICALL FN(nativeRender)(JNIEnv *env, jobject, jstring inPath, jstring partPath, jstring finalPath,
                                     jdouble lengthFactor, jint semitones) {
    const auto pcm = decodeWav(str(env, inPath));
    if (!pcm || pcm->empty() || !(lengthFactor > 0.1 && lengthFactor < 10.0)) return -1;
    const int inLen = static_cast<int>(pcm->size());
    const int outLen = static_cast<int>(std::lround(inLen * lengthFactor));
    std::vector<float> out(static_cast<size_t>(outLen));
    signalsmith::stretch::SignalsmithStretch<float> stretch;
    stretch.presetDefault(1, static_cast<float>(kRate));
    stretch.setTransposeSemitones(static_cast<float>(semitones));
    const float *inCh[1] = {pcm->data()};
    float *outCh[1] = {out.data()};
    if (!stretch.exact(inCh, inLen, outCh, outLen)) {
        // Shorter than the stretcher's window: plain resampling would change pitch, so leave it as it was.
        return -1;
    }
    const std::string part = str(env, partPath), fin = str(env, finalPath);
    FILE *f = fopen(part.c_str(), "wb");
    if (!f) return -1;
    writeWavHeader(f, kRate, 0);
    std::vector<uint8_t> bytes(static_cast<size_t>(outLen) * 3);
    for (int i = 0; i < outLen; ++i) {
        const int32_t v = static_cast<int32_t>(std::lrint(std::clamp(out[static_cast<size_t>(i)], -1.0f, 1.0f) * 8388607.0f));
        bytes[i * 3] = static_cast<uint8_t>(v & 0xFF);
        bytes[i * 3 + 1] = static_cast<uint8_t>((v >> 8) & 0xFF);
        bytes[i * 3 + 2] = static_cast<uint8_t>((v >> 16) & 0xFF);
    }
    const bool ok = fwrite(bytes.data(), 1, bytes.size(), f) == bytes.size();
    fflush(f);
    fseek(f, 0, SEEK_SET);
    writeWavHeader(f, kRate, static_cast<uint32_t>(bytes.size()));
    fclose(f);
    if (!ok || rename(part.c_str(), fin.c_str()) != 0) { remove(part.c_str()); return -1; }
    return outLen;
}

// Before latency calibration (which opens its own duplex pair): stop the stack and the click, pause
// the take, and close the output so nothing competes for the device. The next play reopens it.
JNI() void JNICALL FN(nativeReleaseOutput)(JNIEnv *, jobject) {
    Engine &e = engine();
    e.stackPlaying.store(false);
    e.clickOn.store(false);
    e.droneOn.store(false);
    if (Player *p = e.player.load()) p->paused.store(true);
    if (e.out) { e.out->stop(); e.out->close(); e.out.reset(); }
    e.sweep();
}

// ---- Isolator (plan §7) -----------------------------------------------------------------------

// Live isolator settings: panMode 0 off / 1 keep / 2 remove around panCenter (-1..1) ± panWidth;
// toneHit -1 (tone only) .. +1 (hit only); low and high cut in Hz (0 = off). A playing take that was
// loaded with isolate on hears changes within one STFT hop.
JNI() void JNICALL FN(nativeSetIsolator)(JNIEnv *, jobject, jint panMode, jfloat panCenter, jfloat panWidth, jfloat toneHit, jfloat lowCut, jfloat highCut) {
    gIso.panMode.store(std::clamp(panMode, 0, 2));
    gIso.panCenter.store(std::clamp(panCenter, -1.0f, 1.0f));
    gIso.panWidth.store(std::clamp(panWidth, 0.05f, 1.0f));
    gIso.toneHit.store(std::clamp(toneHit, -1.0f, 1.0f));
    gIso.lowCut.store(std::max(0.0f, lowCut));
    gIso.highCut.store(std::max(0.0f, highCut));
}

// For the note finder: dst.length frames of the take from `from`, through the isolator when isolate,
// else the plain mono mix. Returns false if the file can't be read. Control thread.
JNI() jboolean JNICALL FN(nativeIsolatedWindow)(JNIEnv *env, jobject, jstring path, jlong from, jboolean isolate, jfloatArray dst) {
    Engine &e = engine();
    const StereoPcm s = stereoOf(e, str(env, path));
    if (!s.l) return JNI_FALSE;
    const jsize n = env->GetArrayLength(dst);
    std::vector<float> buf(static_cast<size_t>(n));
    if (isolate) {
        iso::Source src(s.l, s.r, kRate, gIso);
        src.read(from, buf.data(), n);
    } else {
        for (jsize i = 0; i < n; ++i) {
            const int64_t k = from + i;
            buf[static_cast<size_t>(i)] = (k >= 0 && k < static_cast<int64_t>(s.l->size())) ? 0.5f * ((*s.l)[static_cast<size_t>(k)] + (*s.r)[static_cast<size_t>(k)]) : 0.0f;
        }
    }
    env->SetFloatArrayRegion(dst, 0, n, buf.data());
    return JNI_TRUE;
}
