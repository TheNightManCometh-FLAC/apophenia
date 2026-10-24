# Parametric EQ Design (Pro-Q-class)

Assembles the verified primitives from `SKILL.md` into a full multi-band parametric
EQ: the per-band model, low/high-cut slope handling, channel routing (stereo / mid-side
/ L-R), a dynamic-EQ hook, and the magnitude-response math for an analyzer overlay.
The analyzer math below is **derived from the same SVF coefficients** so the drawn
curve is guaranteed to match what the audio path does.

## Contents
1. Per-band model
2. Mapping a band to SVF coefficients (single source of truth)
3. Low-cut / high-cut slopes
4. Channel routing: stereo, mid-side, L-R
5. Dynamic EQ hook
6. Magnitude response for the analyzer curve

## 1. Per-band model

```cpp
enum class BandType {
    Bell, LowShelf, HighShelf, LowCut, HighCut, Notch, BandPass, AllPass
};
enum class BandChannel { Stereo, Mid, Side, Left, Right };

struct EqBand {
    bool        enabled  = false;
    BandType    type     = BandType::Bell;
    BandChannel channel  = BandChannel::Stereo;
    double      freqHz   = 1000.0;   // 20 .. ~0.49*fs
    double      Q        = 0.707;    // ~0.1 .. 40
    double      gainDb   = 0.0;      // bell/shelf only
    int         slopeDbOct = 12;     // cuts only: 6,12,18,24,...,96  (order = /6)
    double      dynGainDb = 0.0;     // runtime offset from the dynamic-EQ detector
};
```

Typical UI parameter ranges (match commercial EQ feel): freq 20 Hz–20 kHz on a log
scale, gain ±30 dB (bell/shelf), Q 0.1–40. Use enough bands (24 is common) but only
run `enabled` ones.

## 2. Mapping a band to SVF coefficients (single source of truth)

Both the audio path and the analyzer must derive coefficients the same way. Centralize
it: a function that returns the SVF building blocks `(g, k, m0, m1, m2)` for a band,
and a thin wrapper that produces audio-ready `SvfCoeffs`. (`g`/`k` are also what the
analyzer needs — see §6 — so nothing is computed twice from different formulas.)

```cpp
struct SvfParams { double g=0, k=0, m0=0, m1=0, m2=0; };

inline SvfParams bandSvfParams(BandType t, double fc, double Q, double gainDb, double fs) {
    if (fc > 0.49 * fs) fc = 0.49 * fs;
    if (fc < 1.0e-4)    fc = 1.0e-4;
    SvfParams p;
    const double A = std::pow(10.0, gainDb / 40.0);
    switch (t) {
        case BandType::Bell:
            p.g = std::tan(kPi * fc / fs);
            p.k = 1.0 / (Q * A);
            p.m0 = 1.0; p.m1 = p.k * (A * A - 1.0); p.m2 = 0.0; break;
        case BandType::LowShelf:
            p.g = std::tan(kPi * fc / fs) / std::sqrt(A);
            p.k = 1.0 / Q;
            p.m0 = 1.0; p.m1 = p.k * (A - 1.0); p.m2 = (A * A - 1.0); break;
        case BandType::HighShelf:
            p.g = std::tan(kPi * fc / fs) * std::sqrt(A);
            p.k = 1.0 / Q;
            p.m0 = A * A; p.m1 = p.k * (1.0 - A) * A; p.m2 = (1.0 - A * A); break;
        case BandType::Notch:
            p.g = std::tan(kPi * fc / fs); p.k = 1.0 / Q;
            p.m0 = 1.0; p.m1 = -p.k; p.m2 = 0.0; break;
        case BandType::BandPass:
            p.g = std::tan(kPi * fc / fs); p.k = 1.0 / Q;
            p.m0 = 0.0; p.m1 = 1.0; p.m2 = 0.0; break;
        case BandType::AllPass:
            p.g = std::tan(kPi * fc / fs); p.k = 1.0 / Q;
            p.m0 = 1.0; p.m1 = -2.0 * p.k; p.m2 = 0.0; break;
        case BandType::LowCut:   // single 2nd-order section; see §3 for steeper slopes
            p.g = std::tan(kPi * fc / fs); p.k = 1.0 / Q;
            p.m0 = 1.0; p.m1 = -p.k; p.m2 = -1.0; break;   // high-pass
        case BandType::HighCut:
            p.g = std::tan(kPi * fc / fs); p.k = 1.0 / Q;
            p.m0 = 0.0; p.m1 = 0.0; p.m2 = 1.0; break;     // low-pass
    }
    return p;
}

inline SvfCoeffs toCoeffs(const SvfParams& p) {
    SvfCoeffs c;
    c.a1 = 1.0 / (1.0 + p.g * (p.g + p.k));
    c.a2 = p.g * c.a1;
    c.a3 = p.g * c.a2;
    c.m0 = p.m0; c.m1 = p.m1; c.m2 = p.m2;
    return c;
}
```

For a bell/shelf band, feed `gainDb + dynGainDb` (see §5) so the dynamic offset rides
on top of the static gain.

## 3. Low-cut / high-cut slopes

A single 2nd-order section is 12 dB/oct. For 6/12/.../96 dB/oct, set order `N =
slopeDbOct / 6` and build a cascade at the **same** cutoff: one `OnePoleTPT` section if
`N` is odd, plus `N/2` (floored) SVF sections using the Butterworth Q values from
`SKILL.md` (`butterworthSectionQ(N, m)`). Use a fixed-capacity cascade so it stays
allocation-free (max order 16 → at most 8 SVF sections + 1 one-pole).

```cpp
struct CutCascade {
    static constexpr int kMaxSections = 8;
    bool        useOnePole = false;
    OnePoleTPT  onePole;                          // per channel
    int         numSvf = 0;
    SvfCoeffs   svf[kMaxSections];                // coefficients (channel-independent)
    SvfState    svfState[kMaxSections];           // per channel
    bool        highCut = true;                   // true = low-pass, false = high-pass

    // Rebuild coefficients (control rate). 'highPass' selects low-cut vs high-cut.
    void set(int order, double fc, double fs, bool highPass) {
        highCut = !highPass;
        useOnePole = (order % 2) != 0;
        numSvf = order / 2;
        if (numSvf > kMaxSections) numSvf = kMaxSections;
        if (useOnePole) onePole.set(fc, fs);
        for (int m = 0; m < numSvf; ++m) {
            const double q = butterworthSectionQ(order, m + 1);
            svf[m] = highPass ? svfHighpass(fc, q, fs) : svfLowpass(fc, q, fs);
        }
    }
    void reset() { onePole.reset(); for (auto& s : svfState) s.reset(); }

    double tick(double x) {
        if (useOnePole) x = highCut ? onePole.lowpass(x) : onePole.highpass(x);
        for (int m = 0; m < numSvf; ++m) x = svfTick(svf[m], svfState[m], x);
        return x;
    }
};
```

(For a "brickwall"/linear-phase option later, that is an FFT-domain design — out of
scope here; this is the minimum-phase IIR path that matches the SVF audio.)

## 4. Channel routing: stereo, mid-side, L-R

Stereo and L-R bands process the existing channels directly. Mid-side bands require
converting around the M/S-processed bands. Convert once per block: build M/S buffers,
run the M/S bands on them, convert back, and run stereo/L-R bands on L/R. Encode/decode
must be inverse-consistent:

```cpp
// L/R -> M/S
const double M = 0.5 * (L + R);
const double S = 0.5 * (L - R);
// M/S -> L/R  (exact inverse of the above)
const double Lout = M + S;
const double Rout = M - S;
```

Each routing target keeps its own per-channel `SvfState`/`CutCascade` state (a Mid band
and a Left band are different state, even at the same settings).

## 5. Dynamic EQ hook

Dynamic EQ = a band whose gain is reduced (or boosted) by the level in a detection
signal — usually the band's own filtered energy, optionally an external sidechain. The
**detector** (peak/RMS envelope follower with attack/release, threshold, ratio) belongs
to the dynamics skill; this EQ only needs the resulting `dynGainDb`:

1. Tap a detection signal. For self-detection, run the input through a copy of the
   band as a band-pass/bell probe and measure its level; for sidechain, measure the
   sidechain bus (the SDK sidechain bus pattern is in the project's VST3 references).
2. The dynamics detector turns that level into a gain offset in dB (e.g., −6 dB when
   the band is hot, easing back to 0 by the release).
3. Each block, set `band.dynGainDb` from the detector and rebuild the band coefficients
   with `bandSvfParams(type, fc, Q, gainDb + dynGainDb, fs)`.

Because coefficients are rebuilt per block from a smoothed gain, the dynamic motion is
click-free; keep the detector's attack/release as the audible time constants, not the
coefficient smoother.

## 6. Magnitude response for the analyzer curve

To draw a curve that matches the audio exactly, evaluate the SVF's own transfer
function. The trapezoidal SVF is the analog prototype under the bilinear transform with
the prewarp already folded into `g`, so at frequency `f` substitute
`s = (1/g)·(z − 1)/(z + 1)` with `z = e^{jω}`, `ω = 2π f / fs`, into the prototype

    H(s) = ( m0·s² + (m0·k + m1)·s + (m0 + m2) ) / ( s² + k·s + 1 )

This is derived from the same `(g, k, m0, m1, m2)` the audio path uses (verified: it
reproduces each shape, e.g. a bell's center gain comes out to exactly `gainDb`).

```cpp
#include <complex>

// Linear magnitude of one 2nd-order SVF section at frequency f.
inline double svfMagnitude(const SvfParams& p, double f, double fs) {
    using cd = std::complex<double>;
    const double w = 2.0 * kPi * f / fs;
    const cd z = std::polar(1.0, w);
    const cd s = (1.0 / p.g) * (z - 1.0) / (z + 1.0);
    const cd num = p.m0 * s * s + (p.m0 * p.k + p.m1) * s + (p.m0 + p.m2);
    const cd den = s * s + p.k * s + 1.0;
    return std::abs(num / den);
}

// Linear magnitude of one TPT one-pole section. lowpass: 1/(s+1); highpass: s/(s+1).
inline double onePoleMagnitude(double fc, double f, double fs, bool lowpass) {
    using cd = std::complex<double>;
    double fcw = fc; if (fcw > 0.49 * fs) fcw = 0.49 * fs; if (fcw < 1.0e-4) fcw = 1.0e-4;
    const double g = std::tan(kPi * fcw / fs);
    const double w = 2.0 * kPi * f / fs;
    const cd z = std::polar(1.0, w);
    const cd s = (1.0 / g) * (z - 1.0) / (z + 1.0);
    const cd h = lowpass ? (cd(1.0) / (s + 1.0)) : (s / (s + 1.0));
    return std::abs(h);
}
```

Composite curve: the EQ is a series cascade, so multiply section magnitudes (equivalently
sum dB). For a band, sum the dB of each of its sections (a steep cut sums its cascade
sections); then sum across enabled bands routed to the channel you are drawing:

```cpp
// dB at frequency f for the whole chain on one routing target.
double totalDb = 0.0;
for (const EqBand& b : bands) {
    if (!b.enabled /* or not routed to this target */) continue;
    if (b.type == BandType::LowCut || b.type == BandType::HighCut) {
        const int N = b.slopeDbOct / 6;
        const bool hp = (b.type == BandType::LowCut);
        if (N % 2) totalDb += 20.0 * std::log10(onePoleMagnitude(b.freqHz, f, fs, !hp));
        for (int m = 0; m < N / 2; ++m) {
            const double q = butterworthSectionQ(N, m + 1);
            SvfParams p = bandSvfParams(b.type, b.freqHz, q, 0.0, fs);
            totalDb += 20.0 * std::log10(svfMagnitude(p, f, fs));
        }
    } else {
        SvfParams p = bandSvfParams(b.type, b.freqHz, b.Q, b.gainDb + b.dynGainDb, fs);
        totalDb += 20.0 * std::log10(svfMagnitude(p, f, fs));
    }
}
```

Evaluate `totalDb` across log-spaced frequencies (e.g., ~256 points from 20 Hz to
20 kHz) to draw the curve. Run this on the UI thread from a snapshot of the parameters,
never inside `process()`.
