---
name: audio-spectral-fft
description: >
  Implementation-ready DSP for FFT and STFT-based spectral processing in VST3 plugins
  built on the Steinberg VST3 SDK (no JUCE, Windows x64 target). Use this whenever
  designing or coding ANY frequency-domain audio: real-time spectrum analyzers,
  linear-phase FIR EQ applied via FFT, fast FFT convolution, phase vocoders, time-stretch
  or pitch-shift, spectral gates and de-noisers, spectral freeze/blur, or the STFT
  front-end for spectral dynamics and resonance suppression. Covers the verified pffft
  real-FFT API (size rule, alignment, FFTPACK DC/Nyquist packing), windowing with
  numerically-computed coherent gain and overlap-add (COLA) normalization, the STFT loop,
  magnitude/phase and bin-to-Hz extraction, principal-argument instantaneous-frequency
  estimation and phase locking, overlap-add/save FIR convolution, analyzer dBFS
  calibration and log-frequency smoothing, honest latency reporting, and the lock-free
  audio-to-UI hand-off. Prefer this over guessing FFT layout, window normalization, or the
  phase-vocoder formula.
---

# Audio Spectral / FFT Processing (VST3 SDK, no JUCE)

Verified, allocation-free FFT and short-time Fourier transform (STFT) DSP for closed-source
commercial VST3 plugins. The three things that are easy to get wrong here — **the FFT
library's data layout, the overlap-add normalization, and the phase-vocoder frequency
formula** — are each pinned to a primary source below. Do **not** substitute a remembered
real-FFT bin layout or a memorized window-overlap constant; both are computed by
construction in the code so there is no magic number in the hot path.

The FFT library is **pffft** (Julien Pommier's Pretty Fast FFT, derived from FFTPACK v4). Its
license is the FFTPACK / NCAR-UCAR "BSD-like" license — permissive and compatible with
closed-source commercial distribution (confirmed in the `pffft.h` header). It is single
precision (`float`) and 1-D only. No GPL code is reproduced anywhere in this skill.

## The pffft API — verified against pffft.h

These signatures are copied from the original `pffft.h` (Julien Pommier, 2013). Do not guess
at them.

```c
typedef struct PFFFT_Setup PFFFT_Setup;                 /* opaque, read-only, thread-shareable */
typedef enum { PFFFT_FORWARD, PFFFT_BACKWARD } pffft_direction_t;
typedef enum { PFFFT_REAL,    PFFFT_COMPLEX  } pffft_transform_t;

PFFFT_Setup *pffft_new_setup(int N, pffft_transform_t transform);   /* ALLOCATES — not on audio thread */
void         pffft_destroy_setup(PFFFT_Setup *);
void pffft_transform        (PFFFT_Setup*, const float* in, float* out, float* work, pffft_direction_t);
void pffft_transform_ordered(PFFFT_Setup*, const float* in, float* out, float* work, pffft_direction_t);
void pffft_zreorder         (PFFFT_Setup*, const float* in, float* out, pffft_direction_t);
void pffft_zconvolve_accumulate(PFFFT_Setup*, const float* a, const float* b, float* ab, float scaling);
void *pffft_aligned_malloc(size_t nb_bytes);            /* 16-byte aligned (x86/PowerPC) */
void  pffft_aligned_free(void *);
int   pffft_simd_size();                                /* 4 if SSE/NEON enabled at build, else 1 */
```

The non-negotiable rules straight from the header comments:

- **Size rule:** `N` must be of the form `(2^a)·(3^b)·(5^c)` with **a ≥ 5** — i.e. a multiple
  of 32. For real transforms use a multiple of 32. `pffft_new_setup` returns `NULL` for an
  invalid `N`; check it. **Use powers of two in [512, 8192]** for STFT work (the header notes
  performance is best for `128 ≤ N ≤ 8192`).
- **Alignment:** every `float*` passed to a transform must be **16-byte aligned**. Allocate
  buffers with `pffft_aligned_malloc` (or `_mm_malloc(n,16)` / `posix_memalign`). A misaligned
  pointer is undefined behavior, not a slow path.
- **No internal scaling:** `PFFFT_BACKWARD(PFFFT_FORWARD(x)) = N·x`. You must divide by `N`
  somewhere. In the STFT below the `1/N` is folded into the overlap-add normalization so it
  costs nothing extra.
- **`work` buffer:** point it at `N` aligned floats for a real transform (`2N` for complex). If
  `NULL`, pffft uses the stack — fine for small `N`, but for an audio-thread engine
  **pre-allocate a persistent aligned work buffer** and pass it, so behavior is identical
  regardless of `N` and there is no large `alloca`.
- **`pffft_new_setup` allocates** — build setups in `setActive(true)` / `setupProcessing`, never
  in `process()`. The `PFFFT_Setup` is read-only, so one setup can be shared by many threads
  (e.g. the audio thread and an analysis thread) for the same `N`.

### The real-transform data layout (the #1 gotcha)

For a real forward transform, pffft packs the spectrum FFTPACK-style. After
`pffft_transform_ordered(.., PFFFT_FORWARD)` the `N`-float output is **interleaved complex**,
**except** the two purely-real components — DC (bin 0) and Nyquist (bin `N/2`) — are packed
together into the **first** complex slot. The header states it exactly: the 0-frequency and
half-frequency components "are assembled in the first entry as `F(0)+i*F(n/2+1)`". Concretely,
for output array `X` of length `N`:

```
X[0]            = Re(bin 0)      = DC        (real)
X[1]            = Re(bin N/2)    = Nyquist   (real)   <-- packed into the imag slot of bin 0
X[2k], X[2k+1]  = Re(bin k), Im(bin k)       for k = 1 .. N/2 - 1
```

So there are `N/2 + 1` logical bins (0 .. N/2). Getting magnitude wrong at DC/Nyquist is the
classic first-day bug; the wrapper below handles it.

> **Convolution caveat:** `pffft_zconvolve_accumulate` expects spectra from
> `pffft_transform` (the **unordered**/internal layout), **not** reordered with
> `pffft_zreorder`. The fast-convolution path therefore uses `pffft_transform` (unordered) for
> both operands, accumulates, then transforms back — see
> `references/fft-convolution-and-analyzer.md`. The magnitude/phase analyzer path below uses
> the **ordered** layout instead, because you need real per-bin magnitudes.

### Allocation-free real-FFT wrapper

```cpp
#define _USE_MATH_DEFINES
#include <cmath>
#include <cstddef>
extern "C" {
#include "pffft.h"
}

// Build/destroy OFF the audio thread (pffft_new_setup allocates). Every buffer here is
// 16-byte aligned via pffft_aligned_malloc, as pffft requires.
class RealFFT {
public:
    void init(int fftSize) {
        N_     = fftSize;
        setup_ = pffft_new_setup(N_, PFFFT_REAL);                 // returns NULL if N invalid
        work_  = (float*)pffft_aligned_malloc(sizeof(float) * (size_t)N_);  // N floats (real)
        freq_  = (float*)pffft_aligned_malloc(sizeof(float) * (size_t)N_);  // FFTPACK-packed spectrum
    }
    void destroy() {
        if (setup_) { pffft_destroy_setup(setup_); setup_ = nullptr; }
        if (work_)  { pffft_aligned_free(work_);   work_  = nullptr; }
        if (freq_)  { pffft_aligned_free(freq_);   freq_  = nullptr; }
    }
    ~RealFFT() { destroy(); }
    bool valid()  const { return setup_ != nullptr; }
    int  size()   const { return N_; }
    int  numBins()const { return N_ / 2 + 1; }                    // bins 0 .. N/2 inclusive

    // Forward real FFT of N aligned time samples into the internal ordered spectrum.
    void forward(const float* timeAligned) {
        pffft_transform_ordered(setup_, timeAligned, freq_, work_, PFFFT_FORWARD);
    }
    // Inverse back to time (UNSCALED: result is N * ifft). The 1/N is applied by the caller's
    // overlap-add normalization, so do not scale here.
    void inverse(float* timeAlignedOut) {
        pffft_transform_ordered(setup_, freq_, timeAlignedOut, work_, PFFFT_BACKWARD);
    }

    float* spectrum() { return freq_; }                          // FFTPACK-packed (see layout note)

    float magnitude(int k) const {                               // |bin k|, k in 0..N/2
        if (k == 0)      return std::fabs(freq_[0]);             // DC     (real)
        if (k == N_ / 2) return std::fabs(freq_[1]);             // Nyquist(real)
        const float re = freq_[2 * k], im = freq_[2 * k + 1];
        return std::sqrt(re * re + im * im);
    }
    float phase(int k) const {                                   // radians, k in 0..N/2
        if (k == 0)      return (freq_[0] >= 0.f) ? 0.f : (float)M_PI;
        if (k == N_ / 2) return (freq_[1] >= 0.f) ? 0.f : (float)M_PI;
        return std::atan2(freq_[2 * k + 1], freq_[2 * k]);
    }
    // Write a bin back into the packed spectrum (for spectral processing ops).
    void setBin(int k, float re, float im) {
        if      (k == 0)      freq_[0] = re;                     // DC: imag is forced to 0
        else if (k == N_ / 2) freq_[1] = re;                     // Nyquist: stored in slot 1
        else { freq_[2 * k] = re; freq_[2 * k + 1] = im; }
    }
private:
    PFFFT_Setup* setup_ = nullptr;
    float* work_ = nullptr;
    float* freq_ = nullptr;
    int    N_    = 0;
};
```

## Windows — periodic form, numerically-measured gains

For STFT/overlap-add you want the **periodic (DFT-even)** window — denominator `N`, **not**
`N-1` — so that shifted copies tile cleanly under overlap-add (F. J. Harris, "On the Use of
Windows for Harmonic Analysis with the Discrete Fourier Transform," *Proc. IEEE* 66(1),
1978; J. O. Smith, *Spectral Audio Signal Processing*, CCRMA). The symmetric form is for
one-shot spectral measurement, not for streaming reconstruction.

```cpp
enum class WindowType { Hann, SqrtHann, Hamming, BlackmanHarris };

// Periodic windows: argument is 2*pi*n/N (NOT n/(N-1)).
inline void makeWindow(WindowType type, int N, float* w) {
    const double twoPi = 6.283185307179586;
    for (int n = 0; n < N; ++n) {
        const double p = twoPi * (double)n / (double)N;          // periodic
        double v;
        switch (type) {
            case WindowType::Hann:     v = 0.5 - 0.5 * std::cos(p); break;
            case WindowType::SqrtHann: v = std::sqrt(0.5 - 0.5 * std::cos(p)); break;
            case WindowType::Hamming:  v = 0.54 - 0.46 * std::cos(p); break;
            case WindowType::BlackmanHarris:                        // 4-term minimum (Harris 1978)
                v = 0.35875 - 0.48829 * std::cos(p)
                  + 0.14128 * std::cos(2.0 * p) - 0.01168 * std::cos(3.0 * p);
                break;
            default: v = 1.0; break;
        }
        w[n] = (float)v;
    }
}

// Coherent gain (CG) and equivalent noise bandwidth (ENBW, in bins), measured from the
// window itself so the analyzer never depends on a memorized per-window constant.
// ENBW(bins) = N * sum(w^2) / (sum(w))^2  (Harris 1978).
struct WindowStats { double coherentGain; double enbwBins; double sumW; };
inline WindowStats analyzeWindow(const float* w, int N) {
    double s1 = 0.0, s2 = 0.0;
    for (int n = 0; n < N; ++n) { s1 += w[n]; s2 += (double)w[n] * w[n]; }
    return { s1 / N, (double)N * s2 / (s1 * s1), s1 };
}
```

Window choice for the common jobs: **Hann (or sqrt-Hann)** for general STFT analysis/resynthesis
and phase vocoders (smooth, well-behaved overlap); **Blackman-Harris** when you need very low
spectral leakage for a clean analyzer at the cost of a wider main lobe. For a **WOLA spectral
processor** (window on both analysis and synthesis), set analysis = synthesis = `SqrtHann` so
the effective window is Hann.

## The overlap-add STFT framework

The STFT slides a window of length `N` along the signal in hops of `H = N/overlap`,
transforms each frame, optionally modifies it, transforms back, applies a synthesis window,
and overlap-adds (R. Allen and L. Rabiner, "A unified approach to short-time Fourier analysis
and synthesis," *Proc. IEEE* 65(11), 1977). Default overlap is **4 (75%, H = N/4)** — the
standard choice that satisfies the constant-overlap-add (COLA) condition for Hann/sqrt-Hann
and gives good time resolution.

**COLA / normalization, computed by construction.** Perfect reconstruction needs the
overlap-added product of the analysis and synthesis windows to be constant across output
positions. Rather than memorizing the per-window constant (e.g. "1.5 for Hann at 75%"), the
engine **measures** the steady-state overlap weight at every intra-hop phase `p` and divides
by it. Because the analysis window also multiplied the input, the weight at phase `p` is
`Σ_j wa[p+jH]·ws[p+jH]` over all frames `j` whose window covers that sample; pffft's missing
`1/N` is folded into the same divisor. This is exact for any window/overlap pair that
actually satisfies COLA, with zero magic numbers.

**Latency = N.** The engine must buffer a full window before producing the first output of
that frame, so the end-to-end latency is exactly the window length `N` samples. (The internal
index offset `N - H` is a buffering detail, not the reported latency — verified by tracing an
impulse through the indexing. Confirm empirically with an impulse test; see the testing skill.)
The frame structure follows the well-known shift-register STFT (S. M. Bernsee, "Pitch Shifting
Using The Fourier Transform," dspdimension.com, 1999), generalized to an arbitrary in-place
spectral op.

```cpp
#include <vector>
#include <cstring>     // memmove, memset
#include <cassert>

class STFTProcessor {
public:
    // OFF the audio thread. fftSize: power of two in [512,8192]. overlap: 2 or 4 (4 = 75%).
    void prepare(int fftSize, int overlap, WindowType aType, WindowType sType) {
        N_ = fftSize; H_ = fftSize / overlap;
        fft_.init(N_);
        aWin_.resize(N_); sWin_.resize(N_);
        makeWindow(aType, N_, aWin_.data());
        makeWindow(sType, N_, sWin_.data());

        // Steady-state overlap weight per intra-hop phase, including pffft's 1/N. Invariant
        // under the hop shift, so dividing each accumulator term by recip_[n % H_] correctly
        // normalizes every sample (all contributions to one output sample share phase n%H_).
        recip_.assign(H_, 0.0f);
        for (int phase = 0; phase < H_; ++phase) {
            double g = 0.0;
            for (int idx = phase; idx < N_; idx += H_) g += (double)aWin_[idx] * sWin_[idx];
            g *= (double)N_;                                    // fold in the 1/N (inverse() is unscaled)
            recip_[phase] = (g > 1e-12) ? (float)(1.0 / g) : 0.0f;
        }

        frameTime_ = (float*)pffft_aligned_malloc(sizeof(float) * (size_t)N_);
        frameOut_  = (float*)pffft_aligned_malloc(sizeof(float) * (size_t)N_);
        inFifo_.assign(N_, 0.0f);
        outFifo_.assign(N_, 0.0f);
        outAccum_.assign(N_, 0.0f);
        reset();
    }
    void reset() {
        std::fill(inFifo_.begin(),   inFifo_.end(),   0.0f);
        std::fill(outFifo_.begin(),  outFifo_.end(),  0.0f);
        std::fill(outAccum_.begin(), outAccum_.end(), 0.0f);
        rover_ = N_ - H_;                                       // input-FIFO latency offset
    }
    void release() {
        fft_.destroy();
        if (frameTime_) { pffft_aligned_free(frameTime_); frameTime_ = nullptr; }
        if (frameOut_)  { pffft_aligned_free(frameOut_);  frameOut_  = nullptr; }
    }
    int latencySamples() const { return N_; }                  // report this from getLatencySamples()

    // SpectralOp: void op(RealFFT& fft) — mutate fft.spectrum() in place. Empty op = passthrough
    // (output equals input delayed by N, within reconstruction error). Allocation-free.
    template <class SpectralOp>
    void process(const float* in, float* out, int numSamples, SpectralOp&& op) {
        const int inFifoLatency = N_ - H_;
        for (int i = 0; i < numSamples; ++i) {
            inFifo_[rover_] = in[i];
            out[i]          = outFifo_[rover_ - inFifoLatency];
            ++rover_;
            if (rover_ >= N_) {
                rover_ = inFifoLatency;
                for (int n = 0; n < N_; ++n) frameTime_[n] = inFifo_[n] * aWin_[n];
                fft_.forward(frameTime_);
                op(fft_);                                       // spectral domain edit
                fft_.inverse(frameOut_);                        // = N * ifft
                for (int n = 0; n < N_; ++n)
                    outAccum_[n] += frameOut_[n] * sWin_[n] * recip_[n % H_];
                for (int k = 0; k < H_; ++k) outFifo_[k] = outAccum_[k];
                std::memmove(outAccum_.data(), outAccum_.data() + H_,
                             (size_t)(N_ - H_) * sizeof(float));
                std::memset (outAccum_.data() + (N_ - H_), 0, (size_t)H_ * sizeof(float));
                std::memmove(inFifo_.data(), inFifo_.data() + H_,
                             (size_t)inFifoLatency * sizeof(float));
            }
        }
    }

    RealFFT& fft() { return fft_; }
    int fftSize() const { return N_; }
    int hop()     const { return H_; }
    double binToHz(int k, double fs) const { return (double)k * fs / (double)N_; }

private:
    RealFFT fft_;
    std::vector<float> aWin_, sWin_, recip_;
    std::vector<float> inFifo_, outFifo_, outAccum_;
    float* frameTime_ = nullptr;
    float* frameOut_  = nullptr;
    int N_ = 0, H_ = 0, rover_ = 0;
};
```

The `memmove`/`memset` calls move plain `float` data and allocate nothing — they are real-time
safe. They run once per hop, not per sample. A production build can replace the shift register
with a true ring buffer to avoid the copies; the shift form is used here because its
correctness (and the latency = `N` proof) is easy to see.

### Bin ↔ Hz, and the simplest spectral op

Bin `k` is centered at `f_k = k · fs / N` Hz (`k = 0 .. N/2`); resolution is `fs / N` Hz per
bin. A trivial spectral gate (zero bins below a magnitude floor) shows the op pattern:

```cpp
// Example op: hard spectral gate. Captured by value-friendly lambda; no allocation.
float floorMag = 0.01f;
stft.process(in, out, n, [&](RealFFT& f) {
    const int bins = f.numBins();
    for (int k = 0; k < bins; ++k)
        if (f.magnitude(k) < floorMag) f.setBin(k, 0.0f, 0.0f);
});
```

Magnitude/phase manipulation, instantaneous-frequency estimation, time-stretch/pitch-shift,
and the richer spectral effects live in `references/phase-vocoder-and-spectral-effects.md`.

## Wiring into the VST3 processor

Audio-thread rules are strict and unchanged: no allocation, no locks, no file I/O inside
`process()`. Build FFT setups and windows in `setActive(true)`; size everything from
`maxSamplesPerBlock` and `sampleRate` cached in `setupProcessing()`; enable flush-to-zero.
The symbols below are confirmed against the project's VST3 production references.

```cpp
#include <xmmintrin.h>   // _MM_SET_FLUSH_ZERO_MODE
#include <pmmintrin.h>   // _MM_SET_DENORMALS_ZERO_MODE

tresult PLUGIN_API setupProcessing(ProcessSetup& setup) override {
    fs_       = setup.sampleRate;
    maxBlock_ = setup.maxSamplesPerBlock;
    return AudioEffect::setupProcessing(setup);
}

tresult PLUGIN_API setActive(TBool state) override {
    if (state) { stftL_.prepare(fftSize_, 4, WindowType::SqrtHann, WindowType::SqrtHann);
                 stftR_.prepare(fftSize_, 4, WindowType::SqrtHann, WindowType::SqrtHann); }
    else       { stftL_.release(); stftR_.release(); }
    return AudioEffect::setActive(state);
}

tresult PLUGIN_API process(ProcessData& data) override {
    _MM_SET_FLUSH_ZERO_MODE(_MM_FLUSH_ZERO_ON);
    _MM_SET_DENORMALS_ZERO_MODE(_MM_DENORMALS_ZERO_ON);
    if (data.numInputs == 0 || data.numOutputs == 0 || data.numSamples == 0)
        return kResultOk;                                   // param-flush call: handle params, return

    const int32 numCh = data.inputs[0].numChannels;
    float* const* in  = data.inputs[0].channelBuffers32;
    float* const* out = data.outputs[0].channelBuffers32;
    for (int32 c = 0; c < numCh; ++c) {
        STFTProcessor& s = (c == 0) ? stftL_ : stftR_;      // independent state per channel
        s.process(in[c], out[c], data.numSamples, [](RealFFT&){ /* your spectral op */ });
    }
    return kResultOk;
}
```

### Latency reporting (verified symbols)

The STFT processor adds **`fftSize` samples** of latency; report it exactly. Undeclared latency
causes phase cancellation when the host runs the track in parallel with dry signal.

```cpp
uint32 PLUGIN_API getLatencySamples() override { return (uint32)stftL_.latencySamples(); }
```

If the FFT size changes at runtime (user switches resolution), the latency changes — notify
the host: `componentHandler->restartComponent(Steinberg::Vst::kLatencyChanged);` where
`kLatencyChanged = 8`. Both `getLatencySamples()` and the `kLatencyChanged` restart flag are
confirmed in the project's VST3 production references. The bus/component-handler plumbing is
processor-skeleton work — use the project's VST3 references and the forthcoming
`vst3-plugin-architecture` skill rather than re-deriving it.

### Real-time vs deferred FFT, and the audio→UI hand-off

For a **spectrum analyzer**, the FFT is cheap enough to run in the audio thread once buffers
are pre-allocated; push each finished magnitude frame to the UI through a **lock-free
single-producer/single-consumer buffer or a triple buffer** (never a lock). For **heavy
offline spectral work** (e.g. an ONNX spectral model), keep the FFT in the audio thread but
move the model to a dedicated inference thread with a lock-free queue — see the project's
ONNX integration reference and the forthcoming `vst3-onnx-integration` and
`vst3-threading-realtime-safety` skills. The analyzer dBFS calibration, log-frequency
smoothing, and the exact triple-buffer pattern are in
`references/fft-convolution-and-analyzer.md`.

### Defensive input handling

A spectral processor can produce `NaN`/`Inf` if fed garbage (it then poisons the overlap-add
accumulator for `N` samples). Keep FTZ/DAZ on (above), and if the host can send bad data,
NaN/Inf-check the input block and bypass on bad data using the `validateAudioBuffer` pattern in
the project's `VST3_Critical_Missing_Sections` reference.

## What this skill deliberately defers

- **Oversampling, half-band filters, and antiderivative anti-aliasing (ADAA)** for nonlinear
  processors → the `audio-saturation-oversampling` skill. Spectral processing is linear; it
  does not need them.
- **FIR tap design / target magnitude response** (the filter you then convolve via FFT) → the
  `audio-filters-eq` skill and the DSP-primitives filter reference. This skill covers applying
  such a filter by fast convolution, not designing its coefficients.
- **Peak/RMS/LUFS meter math and true-peak detection** → the forthcoming `audio-metering`
  skill. The analyzer *display* is here; the loudness *meters* are there.
- **Convolution-reverb tail handling and FDN reverb** → the forthcoming `audio-reverb` skill.
  This skill covers partitioned FFT convolution as a general tool (overview in the reference).
- **TPDF dither and noise shaping** for final-stage requantization → the output stage of the
  `audio-metering` skill; it is not an STFT topic.

## References (read when relevant)

- `references/phase-vocoder-and-spectral-effects.md` — Magnitude/phase processing in depth:
  **principal-argument phase unwrapping** and the verified instantaneous-frequency formula
  (Dolson 1986 — differentiate the unwrapped phase and add the bin-center frequency back;
  the expected-phase-advance term made explicit), synthesis-phase accumulation for
  **time-scale modification**, **pitch shifting** (stretch-then-resample and the resampling
  trade-offs), **phase locking** for vertical coherence (Laroche-Dolson 1999; Puckette 1995),
  and a validated catalog of **spectral effects** — freeze, blur, robotization,
  whisperization, spectral gate, and spectral-subtraction de-noise — with the transient-smearing
  limitation called out. Read this to build a Pro-DS/soothe2-class spectral dynamics front-end,
  a time-stretch, or a creative spectral effect.
- `references/fft-convolution-and-analyzer.md` — Two production subsystems. **Fast FFT
  convolution:** overlap-add and overlap-save with the exact FFT-size rule `N ≥ L + M - 1`
  (next valid pffft size), using `pffft_zconvolve_accumulate` on the **unordered** spectra
  with `scaling = 1/N`, plus an overview of **uniform and non-uniform partitioned convolution**
  for long impulse responses (latency vs CPU) — this is the engine for linear-phase FIR EQ and
  feeds the reverb skill. **Spectrum analyzer:** the **dBFS calibration** (coherent-gain
  normalization, the single-sided factor of 2, and why DC/Nyquist are exempt), **log-frequency
  axis mapping**, **fractional-octave magnitude smoothing** and **temporal ballistics**
  (attack/release, peak-hold), pre/post overlay, and the **lock-free triple-buffer** audio→UI
  hand-off. Read this for the Pro-Q-class analyzer overlay and any linear-phase EQ mode (note:
  exact FabFilter smoothing curves are proprietary and flagged as such).
