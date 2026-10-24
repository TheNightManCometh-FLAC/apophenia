---
name: audio-filters-eq
description: >
  Implementation-ready DSP for audio filters and EQ in VST3 plugins built on the
  Steinberg VST3 SDK (no JUCE, Windows x64 target). Use this whenever designing or
  coding ANY frequency-shaping DSP: parametric/peaking EQ bands, low/high shelves,
  low-cut/high-cut filters, state-variable filters (SVF), biquads, resonant filters,
  tilt EQ, dynamic EQ, or anything involving cutoff/Q/gain, filter coefficients,
  transfer functions, or stable filter modulation/automation. Covers the Cytomic
  zero-delay-feedback (ZDF) trapezoidal SVF (the production approach), RBJ biquad
  coefficients, TPT one-poles, cascading for steep Butterworth slopes, parameter
  smoothing, denormal protection, and computing magnitude response for analyzer
  overlays. Prefer this over deriving or guessing filter coefficients from memory.
---

# Audio Filters & EQ (VST3 SDK, no JUCE)

This skill gives verified, allocation-free filter DSP for closed-source commercial
VST3 plugins. All coefficient formulas here are confirmed against canonical sources
(cited inline). Do **not** substitute remembered coefficients — use these.

## The one decision that matters: SVF vs biquad

Default to the **linear trapezoidal State Variable Filter (SVF)** for everything in a
modern EQ. A Direct-Form biquad with bilinear-transform coefficients is fine when its
coefficients are *static*, but when cutoff/Q/gain are automated or modulated it
produces zipper noise and can momentarily go unstable while sweeping. The trapezoidal
SVF stays stable and smooth under audio-rate modulation, yields all filter shapes from
one cheap per-sample tick, and is numerically accurate in single precision — which is
exactly why high-quality EQs use this filter class.
(Source: Andrew Simper, Cytomic, "Solving the continuous SVF equations using
trapezoidal integration and equivalent currents," 2013, rev. 2016;
https://cytomic.com/technical-papers/ )

Use a biquad only when you specifically need DF1/TDF2 behavior or are porting fixed
coefficients — see `references/biquad-and-rbj.md`.

## The workhorse: linear trapezoidal SVF

State and one tick are shared by every filter shape; only the coefficients differ.
Keep filter math in `double` even though VST3 audio buffers are `float` — the extra
precision matters at low cutoff frequencies. Read `float` from the buffer, process in
`double`, write `float` back.

```cpp
#include <cmath>

constexpr double kPi = 3.14159265358979323846;  // MSVC: M_PI is not defined by
                                                 // default; use this constant.

// ---- Coefficients: recompute only when fc / Q / gain / fs change (control rate).
struct SvfCoeffs {
    double a1 = 0.0, a2 = 0.0, a3 = 0.0;   // integrator coefficients
    double m0 = 0.0, m1 = 0.0, m2 = 0.0;   // output mix of (input, band, low)
};

// ---- State: one instance PER CHANNEL. Coefficients are channel-independent.
struct SvfState {
    double ic1eq = 0.0;   // integrator 1 equivalent-current state
    double ic2eq = 0.0;   // integrator 2 equivalent-current state
    void reset() { ic1eq = 0.0; ic2eq = 0.0; }
};

// ---- One sample. v0 is the input sample. Real-time safe: no allocation, no branch.
inline double svfTick(const SvfCoeffs& c, SvfState& s, double v0) {
    const double v3 = v0 - s.ic2eq;
    const double v1 = c.a1 * s.ic1eq + c.a2 * v3;
    const double v2 = s.ic2eq + c.a2 * s.ic1eq + c.a3 * v3;
    s.ic1eq = 2.0 * v1 - s.ic1eq;
    s.ic2eq = 2.0 * v2 - s.ic2eq;
    return c.m0 * v0 + c.m1 * v1 + c.m2 * v2;
}

// Shared core: prewarp cutoff and form the integrator coefficients from g and k.
// Clamp fc to (0, 0.49*fs]; tan() diverges as fc approaches Nyquist (fs/2).
inline void svfCore(double fc, double fs, double k, SvfCoeffs& c) {
    if (fc > 0.49 * fs) fc = 0.49 * fs;
    if (fc < 1.0e-4)    fc = 1.0e-4;
    const double g = std::tan(kPi * fc / fs);   // prewarped gain
    c.a1 = 1.0 / (1.0 + g * (g + k));
    c.a2 = g * c.a1;
    c.a3 = g * c.a2;
}
```

### Coefficient builders (all verified against the Cytomic paper)

`k = 1/Q` sets damping/resonance. For the EQ shapes, `A = 10^(gainDb/40)` is the
**amplitude** ratio, so `A*A` is the power/voltage-squared gain. Boost is `gainDb > 0`,
cut is `gainDb < 0`; at `gainDb == 0` every EQ shape collapses to a flat pass-through.

```cpp
// --- Plain filter shapes (low-cut / high-cut ends, sweeps, utility) ---
inline SvfCoeffs svfLowpass(double fc, double Q, double fs) {
    SvfCoeffs c; const double k = 1.0 / Q; svfCore(fc, fs, k, c);
    c.m0 = 0.0; c.m1 = 0.0;      c.m2 = 1.0;  return c;
}
inline SvfCoeffs svfHighpass(double fc, double Q, double fs) {
    SvfCoeffs c; const double k = 1.0 / Q; svfCore(fc, fs, k, c);
    c.m0 = 1.0; c.m1 = -k;       c.m2 = -1.0; return c;
}
inline SvfCoeffs svfBandpass(double fc, double Q, double fs) {  // peak gain = Q
    SvfCoeffs c; const double k = 1.0 / Q; svfCore(fc, fs, k, c);
    c.m0 = 0.0; c.m1 = 1.0;      c.m2 = 0.0;  return c;
}
inline SvfCoeffs svfNotch(double fc, double Q, double fs) {
    SvfCoeffs c; const double k = 1.0 / Q; svfCore(fc, fs, k, c);
    c.m0 = 1.0; c.m1 = -k;       c.m2 = 0.0;  return c;
}
inline SvfCoeffs svfAllpass(double fc, double Q, double fs) {
    SvfCoeffs c; const double k = 1.0 / Q; svfCore(fc, fs, k, c);
    c.m0 = 1.0; c.m1 = -2.0 * k; c.m2 = 0.0;  return c;
}

// --- EQ band shapes (parametric EQ) ---
inline SvfCoeffs svfBell(double fc, double Q, double gainDb, double fs) {
    SvfCoeffs c;
    const double A = std::pow(10.0, gainDb / 40.0);
    const double k = 1.0 / (Q * A);              // note: k divided by A
    svfCore(fc, fs, k, c);
    c.m0 = 1.0; c.m1 = k * (A * A - 1.0); c.m2 = 0.0;
    return c;
}
inline SvfCoeffs svfLowShelf(double fc, double Q, double gainDb, double fs) {
    SvfCoeffs c;
    const double A = std::pow(10.0, gainDb / 40.0);
    const double k = 1.0 / Q;
    double fcw = fc; if (fcw > 0.49 * fs) fcw = 0.49 * fs; if (fcw < 1.0e-4) fcw = 1.0e-4;
    const double g = std::tan(kPi * fcw / fs) / std::sqrt(A);   // cutoff shifts with A
    c.a1 = 1.0 / (1.0 + g * (g + k));
    c.a2 = g * c.a1;
    c.a3 = g * c.a2;
    c.m0 = 1.0; c.m1 = k * (A - 1.0); c.m2 = (A * A - 1.0);
    return c;
}
inline SvfCoeffs svfHighShelf(double fc, double Q, double gainDb, double fs) {
    SvfCoeffs c;
    const double A = std::pow(10.0, gainDb / 40.0);
    const double k = 1.0 / Q;
    double fcw = fc; if (fcw > 0.49 * fs) fcw = 0.49 * fs; if (fcw < 1.0e-4) fcw = 1.0e-4;
    const double g = std::tan(kPi * fcw / fs) * std::sqrt(A);   // cutoff shifts with A
    c.a1 = 1.0 / (1.0 + g * (g + k));
    c.a2 = g * c.a1;
    c.a3 = g * c.a2;
    c.m0 = A * A; c.m1 = k * (1.0 - A) * A; c.m2 = (1.0 - A * A);
    return c;
}
```

## First-order section (TPT one-pole)

A 2nd-order SVF section is 12 dB/oct. For 6 dB/oct slopes, odd-order Butterworth
cuts, tilt EQ, or simple one-pole shelves, use Zavalishin's topology-preserving
one-pole. (Source: Vadim Zavalishin, "The Art of VA Filter Design," free from Native
Instruments.)

```cpp
struct OnePoleTPT {
    double G = 0.0;   // prewarped integrator gain g/(1+g)
    double s = 0.0;   // integrator state
    void set(double fc, double fs) {
        double fcw = fc; if (fcw > 0.49 * fs) fcw = 0.49 * fs; if (fcw < 1.0e-4) fcw = 1.0e-4;
        const double g = std::tan(kPi * fcw / fs);
        G = g / (1.0 + g);
    }
    void reset() { s = 0.0; }
    double lowpass(double x) { const double v = (x - s) * G; const double y = v + s; s = y + v; return y; }
    double highpass(double x) { return x - lowpass(x); }
};
```

## Steep slopes: cascade Butterworth sections

Cascade SVF sections set to the **same cutoff** with per-section Butterworth Q values.
For an order-`N` Butterworth low/high-pass, section `m` (m = 1..N/2) uses:

    Q_m = 1 / (2 * cos( (2*m - 1) * pi / (2*N) ))

For odd `N`, add one `OnePoleTPT` first-order section plus (N-1)/2 second-order
sections. Each second-order section contributes 12 dB/oct; verified examples:
N=2 → Q≈0.7071; N=4 → Q≈{0.5412, 1.3066}; N=6 → Q≈{0.5177, 0.7071, 1.9319}.
Map a UI slope of 6/12/18/.../96 dB/oct to N = slope/6.

```cpp
inline double butterworthSectionQ(int N, int m) {   // m = 1 .. N/2
    return 1.0 / (2.0 * std::cos((2.0 * m - 1.0) * kPi / (2.0 * N)));
}
```

## Wiring into the VST3 processor

Audio-thread rules are strict: no allocation, no locks, no file I/O inside `process()`.
Allocate state in `setActive(true)` / `setupProcessing()` and read sample rate there.
Buffer access uses the confirmed SDK symbols below.

```cpp
// In setupProcessing(ProcessSetup& setup): cache setup.sampleRate (double) and
// setup.maxSamplesPerBlock. Size per-channel state to the channel count.

// Enable flush-to-zero so silent tails don't decay into denormals (x86 CPU spike).
// Call once at the top of process().
#include <xmmintrin.h>   // _MM_SET_FLUSH_ZERO_MODE
#include <pmmintrin.h>   // _MM_SET_DENORMALS_ZERO_MODE

tresult PLUGIN_API process(ProcessData& data) override {
    _MM_SET_FLUSH_ZERO_MODE(_MM_FLUSH_ZERO_ON);
    _MM_SET_DENORMALS_ZERO_MODE(_MM_DENORMALS_ZERO_ON);

    // Parameter-flush calls have null buffers — handle inputParameterChanges, return.
    if (data.numInputs == 0 || data.numOutputs == 0 || data.numSamples == 0)
        return kResultOk;

    // 1) Pull smoothed parameters and rebuild coefficients ONCE per block.
    //    (Recomputing tan() per sample is wasteful; per-block is smooth enough when
    //    parameters are themselves smoothed. For heavy automation, sub-divide the
    //    block into ~32-sample chunks and rebuild per chunk.)
    const double fs = currentSampleRate_;            // cached from setupProcessing
    SvfCoeffs c = svfBell(freqSmoothed_.next(), qSmoothed_.next(),
                          gainSmoothed_.next(), fs);

    // 2) Process each channel through its own state.
    const int32 numCh = data.inputs[0].numChannels;
    for (int32 ch = 0; ch < numCh; ++ch) {
        const float* in  = data.inputs[0].channelBuffers32[ch];
        float*       out = data.outputs[0].channelBuffers32[ch];
        SvfState&    st  = bandState_[ch];           // pre-sized per-channel state
        for (int32 i = 0; i < data.numSamples; ++i)
            out[i] = static_cast<float>(svfTick(c, st, static_cast<double>(in[i])));
    }
    return kResultOk;
}
```

(For the canonical full `process()` skeleton — bus setup, NaN/Inf guards, sample-size
handling, parameter-change iteration — defer to the project's VST3 production
references rather than re-deriving them here.)

## Parameter smoothing

Jumps in cutoff/Q/gain cause zipper noise. Smooth the *target parameter* with a
one-pole and rebuild coefficients from the smoothed value each block.

```cpp
struct SmoothedValue {
    double current = 0.0, target = 0.0, coeff = 0.0;
    void setTimeConstant(double ms, double fs) {     // call when fs is known
        coeff = std::exp(-1.0 / (0.001 * ms * fs));
    }
    void setTarget(double t)  { target = t; }
    void snap(double v)       { current = target = v; }   // use on preset load
    double next() { current = target + (current - target) * coeff; return current; }
};
```

Smooth **frequency in log domain** (smooth log2(fc), then exp2) so sweeps sound even;
smooth **gain in dB**; smooth Q linearly. A 5–20 ms time constant is typical.

## References (read when relevant)

- `references/biquad-and-rbj.md` — Biquad DF1/TDF2 structures, the full RBJ Audio EQ
  Cookbook coefficient set, and exactly when a biquad is the right choice over the SVF.
- `references/parametric-eq-design.md` — Assembling a multi-band parametric EQ
  (Pro-Q-class): per-band model, slope/order mapping for cuts, mid/side and L/R
  routing, a dynamic-EQ hook, and computing the magnitude response for an analyzer
  curve overlay (so the displayed curve matches what the SVF actually does).
