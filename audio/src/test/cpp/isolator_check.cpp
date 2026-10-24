// Host check for isolator.h (no Android needed): cl /std:c++17 /EHsc /O2 /I ..\..\main\cpp isolator_check.cpp
// 1. Null test: neutral settings give back (l + r) / 2, from any start frame, read in odd-sized pieces.
// 2. A low cut at 400 Hz removes a 100 Hz sine and keeps a 2 kHz one.
// 3. Keep the centre removes a hard-left sine; remove the centre removes a centred one.
// 4. Hit only takes most of a steady sine away; tone only keeps it.
#define _USE_MATH_DEFINES
#include <cmath>
#include <cstdio>
#include "isolator.h"

using V = std::vector<float>;
static int fails = 0;
static void check(bool ok, const char *what, double v) { std::printf("%s %s (%.6g)\n", ok ? "PASS" : "FAIL", what, v); if (!ok) ++fails; }

static std::shared_ptr<V> sine(double hz, double amp, int n) {
    auto v = std::make_shared<V>(n);
    for (int i = 0; i < n; ++i) (*v)[i] = static_cast<float>(amp * std::sin(2 * M_PI * hz * i / 48000.0));
    return v;
}
static double rms(const V &x, int from, int to) { double s = 0; for (int i = from; i < to; ++i) s += x[i] * x[i]; return std::sqrt(s / (to - from)); }

int main() {
    const int n = 48000 * 2;
    {
        auto l = std::make_shared<V>(n), r = std::make_shared<V>(n);
        unsigned seed = 1;
        for (int i = 0; i < n; ++i) { seed = seed * 1664525u + 1013904223u; (*l)[i] = ((seed >> 9) / 8388608.0f - 1.0f) * 0.5f; (*r)[i] = 0.3f * std::sin(i * 0.01f); }
        iso::Params p;
        iso::Source s(l, r, 48000, p);
        V out(40000);
        int done = 0;
        const int64_t start = 12345;
        while (done < 40000) { const int k = std::min(777, 40000 - done); s.read(start + done, out.data() + done, k); done += k; }
        double worst = 0;
        for (int i = 0; i < 40000; ++i) worst = std::max(worst, std::abs(out[i] - 0.5 * ((*l)[start + i] + (*r)[start + i])));
        check(worst < 1e-4, "null: neutral settings give back the mono mix", worst);
        V again(1000);
        s.read(5000, again.data(), 1000);   // a jump re-primes
        double w2 = 0;
        for (int i = 0; i < 1000; ++i) w2 = std::max(w2, std::abs(again[i] - 0.5 * ((*l)[5000 + i] + (*r)[5000 + i])));
        check(w2 < 1e-4, "null after a jump back", w2);
    }
    {
        auto lo = sine(100, 0.5, n), hi = sine(2000, 0.5, n);
        iso::Params p; p.lowCut.store(400);
        V out(n / 2);
        iso::Source a(lo, lo, 48000, p); a.read(10000, out.data(), static_cast<int>(out.size()));
        const double r1 = rms(out, 2000, static_cast<int>(out.size())) / rms(*lo, 0, n);
        check(r1 < 0.01, "low cut 400 Hz removes 100 Hz", r1);
        iso::Source b(hi, hi, 48000, p); b.read(10000, out.data(), static_cast<int>(out.size()));
        const double r2 = rms(out, 2000, static_cast<int>(out.size())) / rms(*hi, 0, n);
        check(r2 > 0.98 && r2 < 1.02, "low cut 400 Hz keeps 2 kHz", r2);
    }
    {
        auto left = sine(440, 0.5, n), silent = std::make_shared<V>(n);
        iso::Params p; p.panMode.store(1); p.panCenter.store(0); p.panWidth.store(0.3f);
        V out(n / 2);
        iso::Source a(left, silent, 48000, p); a.read(10000, out.data(), static_cast<int>(out.size()));
        const double r1 = rms(out, 2000, static_cast<int>(out.size())) / (0.5 * rms(*left, 0, n));
        check(r1 < 0.01, "keep the centre removes a hard-left sine", r1);
        auto mid = sine(440, 0.5, n);
        p.panMode.store(2);
        auto midR = std::make_shared<V>(*mid);   // two buffers: the same pointer twice means a mono file, which has no stereo position
        iso::Source b(mid, midR, 48000, p); b.read(10000, out.data(), static_cast<int>(out.size()));
        const double r2 = rms(out, 2000, static_cast<int>(out.size())) / rms(*mid, 0, n);
        check(r2 < 0.01, "remove the centre removes a centred sine", r2);
    }
    {
        auto s = sine(330, 0.5, n);
        iso::Params p; p.toneHit.store(1.0f);
        V out(n / 2);
        iso::Source a(s, s, 48000, p); a.read(10000, out.data(), static_cast<int>(out.size()));
        const double r1 = rms(out, 2000, static_cast<int>(out.size())) / rms(*s, 0, n);
        check(r1 < 0.2, "hit only takes most of a steady sine away", r1);
        p.toneHit.store(-1.0f);
        iso::Source b(s, s, 48000, p); b.read(10000, out.data(), static_cast<int>(out.size()));
        const double r2 = rms(out, 2000, static_cast<int>(out.size())) / rms(*s, 0, n);
        check(r2 > 0.9, "tone only keeps a steady sine", r2);
    }
    std::printf(fails ? "%d FAILED\n" : "ALL PASS\n", fails);
    return fails;
}
