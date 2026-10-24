# FFT Convolution & Spectrum Analyzer

Depth reference for `audio-spectral-fft`. Two production subsystems that both sit on the
verified pffft API from `SKILL.md`:

1. **Fast FFT convolution** — apply a long FIR (linear-phase EQ curve, impulse response) far
   cheaper than time-domain taps. This is the engine a linear-phase EQ mode and a
   convolution reverb front-end share.
2. **Spectrum analyzer** — turn STFT magnitude frames into a calibrated, smoothed,
   log-frequency display and hand them to the UI thread without a lock.

Everything here is allocation-free on the audio thread; all FFT buffers are 16-byte aligned
via `pffft_aligned_malloc` (see `SKILL.md`). Math is in `double`, buffers stay `float`.

---

## Part 1 — Fast FFT convolution

### Why, and the one sizing rule that prevents wrap-around

Direct convolution of a block of `L` input samples with an `M`-tap FIR costs `O(L·M)`. Doing
it through the FFT costs `O(K log K)` for an FFT of size `K`, which wins decisively once `M`
is more than a few dozen taps (a linear-phase EQ or a reverb IR is thousands to hundreds of
thousands of taps). The catch is that **the DFT computes _circular_ convolution**, so a naive
multiply-and-invert wraps the filter's tail back onto the start of the block. The fix is the
only rule you must not get wrong:

> **Linear convolution of length `L` with length `M` produces `L + M − 1` samples.** Pick the
> FFT size `K ≥ L + M − 1`, rounded up to the next valid pffft size (a `(2^a)(3^b)(5^c)`,
> `a ≥ 5`; in practice the next power of two). Zero-pad both operands to `K`. The first
> `M − 1` samples of each block then overlap the previous block's tail — that overlap is
> exactly what overlap-add and overlap-save reconcile (A. V. Oppenheim and R. W. Schafer,
> *Discrete-Time Signal Processing*, the standard treatment of block convolution).

### The pffft convolution primitive (verified contract)

`pffft_zconvolve_accumulate` does the spectral multiply-accumulate for you, **on the unordered
spectra**. From the `pffft.h` header comment, verbatim contract:

- Both input spectra **must** come from `pffft_transform(.., PFFFT_FORWARD)` — the unordered
  internal layout — and **must not** have been passed through `pffft_zreorder`. (If you
  reorder them, the function's assumptions break; you would then have to do the complex
  multiply yourself on the interleaved data.)
- The operation performed is exactly `dft_ab += (dft_a * dft_b) * scaling`.
- `dft_a`, `dft_b`, `dft_ab` may alias; all must be 16-byte aligned, length `K`.

The accumulate semantics are the gift here: for partitioned convolution you call it once per
partition into the *same* `dft_ab` and the sum builds up for free. The DC/Nyquist packing is
handled internally because you are on the unordered representation — you never touch it.

**Scaling = 1/K.** pffft's round trip is `BACKWARD(FORWARD(x)) = K·x`, and `FORWARD` is the
unscaled DFT, so `BACKWARD(A·B)` equals `K · IDFT(DFT(a)·DFT(b))` = `K ·` (the convolution you
want). Folding `scaling = 1/K` into the accumulate cancels it precisely. Do not also scale the
inverse — scale in exactly one place.

### Overlap-add (the default for streaming a fixed filter)

Partition the input stream into blocks of `L`; convolve each block with the (fixed,
pre-transformed) filter; the `M − 1`-sample tail of each result adds into the head of the
next. The filter is transformed **once** when it is set, off the audio thread.

```cpp
#include <cmath>
#include <cstring>
#include <vector>
extern "C" {
#include "pffft.h"
}

// Fixed-filter overlap-add convolver. setFilter() is OFF the audio thread (allocates +
// transforms). process() is allocation-free. One channel; instantiate one per channel.
class OverlapAddConvolver {
public:
    // h: M FIR taps. blockL: input block length you will feed (e.g. host block size or a
    // fixed internal chunk). Chooses K = next pffft size >= blockL + M - 1.
    void setFilter(const float* h, int M, int blockL) {
        release();
        L_ = blockL; M_ = M;
        K_ = nextPffftSize(L_ + M_ - 1);
        setup_ = pffft_new_setup(K_, PFFFT_REAL);          // NULL if K invalid — check in debug
        work_  = alloc(K_);
        Hf_    = alloc(K_);                                 // filter spectrum (unordered)
        Xf_    = alloc(K_);                                 // input-block spectrum (unordered)
        Yf_    = alloc(K_);                                 // product accumulator (unordered)
        xpad_  = alloc(K_);
        ytime_ = alloc(K_);
        tail_.assign(M_ - 1, 0.0f);                         // overlap state

        // Transform the zero-padded filter once, UNORDERED (for zconvolve).
        std::memset(xpad_, 0, sizeof(float) * (size_t)K_);
        std::memcpy(xpad_, h, sizeof(float) * (size_t)M_);
        pffft_transform(setup_, xpad_, Hf_, work_, PFFFT_FORWARD);
    }

    // Convolve exactly L_ input samples -> L_ output samples (steady-state streaming).
    void process(const float* in, float* out) {
        std::memset(xpad_, 0, sizeof(float) * (size_t)K_);
        std::memcpy(xpad_, in, sizeof(float) * (size_t)L_); // zero-pad block to K
        pffft_transform(setup_, xpad_, Xf_, work_, PFFFT_FORWARD);  // unordered

        std::memset(Yf_, 0, sizeof(float) * (size_t)K_);    // accumulator must start at 0
        pffft_zconvolve_accumulate(setup_, Xf_, Hf_, Yf_, 1.0f / (float)K_);  // Yf = Xf*Hf/K
        pffft_transform(setup_, Yf_, ytime_, work_, PFFFT_BACKWARD);          // linear conv, length K

        // First M-1 samples: add the saved tail. Emit L_ samples; save the new M-1 tail.
        const int overlap = M_ - 1;
        for (int n = 0; n < L_; ++n) {
            float v = ytime_[n];
            if (n < overlap) v += tail_[n];
            out[n] = v;
        }
        for (int n = 0; n < overlap; ++n)
            tail_[n] = ytime_[L_ + n];                      // tail beyond the block
    }

    void release() {
        if (setup_) { pffft_destroy_setup(setup_); setup_ = nullptr; }
        for (float** p : { &work_, &Hf_, &Xf_, &Yf_, &xpad_, &ytime_ })
            if (*p) { pffft_aligned_free(*p); *p = nullptr; }
    }
    int latencySamples() const { return L_; }               // one full block of buffering

private:
    static float* alloc(int n) { return (float*)pffft_aligned_malloc(sizeof(float) * (size_t)n); }
    static int nextPffftSize(int n) {                       // smallest power of two >= n, >= 32
        int s = 32; while (s < n) s <<= 1; return s;        // always a valid (2^a), a>=5
    }
    PFFFT_Setup* setup_ = nullptr;
    float *work_=nullptr,*Hf_=nullptr,*Xf_=nullptr,*Yf_=nullptr,*xpad_=nullptr,*ytime_=nullptr;
    std::vector<float> tail_;
    int L_=0, M_=0, K_=0;
};
```

`nextPffftSize` restricts to powers of two, which are always valid `(2^a)` with `a ≥ 5` once
`≥ 32`; pffft also accepts the `3` and `5` factors if you want a tighter `K`, but powers of
two keep the rule trivially correct. **Latency is `L_` samples** — you buffer a whole block
before emitting it. Report it (`getLatencySamples`); if the filter is also symmetric
(linear-phase FIR), its own `(M−1)/2` group delay is *already inside* the convolution result,
so do not add it again — the only added latency versus dry is the block buffering `L_`.

### Overlap-save (the alternative)

Overlap-save keeps a sliding window of `K` samples that overlaps the previous window by
`M − 1`, transforms it, multiplies by the filter spectrum, inverse-transforms, and **discards
the first `M − 1` output samples** (the ones corrupted by circular wrap), keeping the last
`K − (M − 1) = L` good samples. It avoids the output-side add of overlap-add at the cost of
re-transforming overlapping input. The two are mathematically equivalent; pick overlap-add
when the filter is fixed and you stream fixed blocks (above), overlap-save when you prefer to
discard rather than accumulate. Same sizing rule `K ≥ L + M − 1`, same verified
`zconvolve`/`scaling = 1/K` mechanics.

### Long impulse responses: partitioned convolution (overview)

A single FFT over a 2-second reverb IR (≈ 96 000 taps at 48 kHz) forces a block latency of
that whole length — unusable. **Partitioned convolution** splits the IR into pieces:

- **Uniform partitioning** — chop the IR into `Q` equal partitions of `P` samples, transform
  each once. Each audio block, transform the input once and multiply-accumulate against every
  partition's spectrum with the correct frame delay (a frequency-domain delay line). Latency
  drops to one partition `P`. CPU is roughly constant per block. `pffft_zconvolve_accumulate`'s
  accumulate semantics implement the "sum over partitions" directly into one `dft_ab`.
- **Non-uniform partitioning** — use small partitions for the start of the IR (low latency)
  and progressively larger partitions for the tail (fewer, cheaper FFTs where latency no
  longer matters). This is the zero-input/output-latency convolution of W. G. Gardner,
  "Efficient Convolution Without Input/Output Delay," *J. Audio Eng. Soc.*, 1995. It is the
  standard architecture for a commercial convolution reverb.

The full partitioned-convolution engine — scheduling the growing partition sizes, the
frequency-domain delay line, and running the largest partitions on a worker thread so they do
not spike the audio callback — belongs to the **forthcoming `audio-reverb` skill**, which
builds the convolution reverb on top of this primitive. For a **linear-phase EQ**, the FIR is
short enough (a few thousand taps) that plain overlap-add above is sufficient; design the
linear-phase tap set with the FIR designer in the `audio-filters-eq` skill, then convolve it
here.

---

## Part 2 — Spectrum analyzer display

The analyzer runs an analysis-only STFT (no synthesis, no overlap-add reconstruction — just
window → FFT → magnitude per frame). The hard parts are calibration (so the numbers mean
dBFS), frequency-axis mapping (so it reads like every other analyzer), smoothing (so it is not
a hairball), and getting the frame to the UI thread safely.

### dBFS calibration — coherent gain, the single-sided factor of 2, and DC/Nyquist

A windowed DFT does not give you signal amplitude directly: the window scales it by its
**coherent gain** (its sample sum), and a real sinusoid's energy is split between its positive-
and negative-frequency bins, of which a real FFT shows only the positive one. Calibrate so a
full-scale sine (amplitude 1.0) reads **0 dBFS** at its bin.

For a real sinusoid of amplitude `A` landing on bin `k`, the windowed magnitude is
`|X[k]| = A · sumW / 2` for an interior bin, where `sumW = Σ w[n]` (the window's coherent gain
before the `1/N`). Invert that. The factor of 2 is the single-sided correction — and it does
**not** apply to DC (`k = 0`) or Nyquist (`k = N/2`), which have no mirror-image partner bin:

```
sumW = Σ w[n]                                  // analyzeWindow(...).sumW from SKILL.md

interior bin k (1 .. N/2-1):  level_dB = 20·log10( 2·|X[k]| / sumW )
DC (k = 0) and Nyquist(N/2):  level_dB = 20·log10(    |X[k]| / sumW )   // no factor of 2
```

```cpp
#include <cmath>
#include <vector>

// Convert one analysis frame's packed spectrum (RealFFT, SKILL.md) to dBFS per bin.
// sumW = sum of the analysis window (WindowStats::sumW). floorDb clamps silence.
inline void magnitudesToDbfs(const RealFFT& fft, double sumW, float floorDb, float* outDb) {
    const int bins = fft.numBins();                     // N/2 + 1
    const int last = bins - 1;                          // Nyquist index N/2
    const double invW = 1.0 / sumW;
    for (int k = 0; k < bins; ++k) {
        const double mag = fft.magnitude(k);            // handles DC/Nyquist packing internally
        const bool edge  = (k == 0 || k == last);       // no single-sided doubling at edges
        const double amp = (edge ? mag : 2.0 * mag) * invW;
        outDb[k] = (amp > 1e-9) ? (float)(20.0 * std::log10(amp)) : floorDb;
    }
}
```

This calibration uses the window's coherent gain, which is why `analyzeWindow` (SKILL.md)
returns `sumW`. (If you instead want **noise** to read correctly — e.g. a noise-floor display —
normalize power by the window's equivalent noise bandwidth `ENBW`, also from `analyzeWindow`,
rather than by coherent gain. Coherent gain is right for tones; ENBW is right for broadband
energy per bin. A music analyzer uses coherent gain.)

### Log-frequency axis

Audio analyzers are logarithmic in frequency. Bin `k` sits at `f_k = k · fs / N`; map a
frequency to a normalized x in `[0,1]` across the displayed decade span:

```cpp
struct LogFreqAxis {
    double fMin = 20.0, fMax = 20000.0;                 // clamp fMax to fs/2 at setup
    double lg = 0.0;                                     // 1 / log10(fMax/fMin), precomputed
    void prepare(double fs) { if (fMax > fs * 0.5) fMax = fs * 0.5;
                              lg = 1.0 / std::log10(fMax / fMin); }
    double xOf(double f) const {                         // -> [0,1], clamp outside the span
        if (f <= fMin) return 0.0; if (f >= fMax) return 1.0;
        return std::log10(f / fMin) * lg;
    }
};
```

The pixel mapping is then `x_pixel = plotLeft + xOf(f) · plotWidth`. The inverse (pixel →
frequency, for a readout cursor) is `f = fMin · pow(fMax/fMin, xNorm)`.

### Fractional-octave smoothing

A raw FFT has linear bin spacing, so a log display is dense and jagged at the top and sparse
at the bottom. Smooth by averaging **power** (magnitude squared) over a fractional-octave band
around each display frequency, then convert to dB — power averaging is energy-correct, whereas
averaging dB values biases toward the troughs. A `1/6`- to `1/24`-octave band is the usual
range (wider = smoother, less detail).

```cpp
// frac = octave fraction (e.g. 6 => 1/6 octave, 24 => 1/24). For display frequency f, average
// power over [f * 2^(-1/(2*frac)), f * 2^(+1/(2*frac))]. magLin: per-bin linear magnitude.
inline double smoothedDbAt(double f, double frac, double fs, int N,
                           const float* magLin, int bins) {
    const double half = std::pow(2.0, 0.5 / frac);      // band edge ratio
    const double fLo = f / half, fHi = f * half;
    const double binHz = fs / (double)N;
    int kLo = (int)std::floor(fLo / binHz);
    int kHi = (int)std::ceil (fHi / binHz);
    if (kLo < 0) kLo = 0; if (kHi > bins - 1) kHi = bins - 1;
    if (kHi < kLo) kHi = kLo;
    double sumP = 0.0; int cnt = 0;
    for (int k = kLo; k <= kHi; ++k) { sumP += (double)magLin[k] * magLin[k]; ++cnt; }
    const double p = (cnt > 0) ? sumP / cnt : 0.0;
    return (p > 1e-18) ? 10.0 * std::log10(p) : -120.0;  // 10*log10 for power
}
```

(Apply the same coherent-gain / single-sided calibration to `magLin` first, or carry the
calibration constants into the conversion — just be consistent so the smoothed curve still
reads dBFS.) FabFilter-style analyzers use a specific, proprietary smoothing/“slope” curve
(e.g. a per-frequency tilt and an adaptive resolution); **those exact curves are not public
and are not reproduced here** — the fractional-octave method above is the standard, honest
equivalent. Add an optional display **tilt** (e.g. +3 or +4.5 dB/octave) by adding
`tilt · log2(f / fRef)` to each point if you want the “pink-ish” look; that is a cosmetic
overlay, not part of the calibration.

### Temporal ballistics (attack/release, peak-hold)

Per display point, smooth across frames with a one-pole: instant (or fast) attack, slow
release, so the analyzer rises quickly and falls gracefully. The coefficient is set from a
time constant and the **analyzer update rate** (frames per second = `fs / hop` if you update
per STFT frame, or the UI refresh rate if you decimate to it).

```cpp
struct BinBallistics {
    float valueDb = -120.f, peakDb = -120.f;
    float aAtt = 0.f, aRel = 0.f, aPeakFall = 0.f;
    // tauAtt/tauRel/tauPeak in seconds; rate = analyzer updates per second.
    void prepare(double tauAtt, double tauRel, double tauPeak, double rate) {
        aAtt = (float)std::exp(-1.0 / (tauAtt  * rate));
        aRel = (float)std::exp(-1.0 / (tauRel  * rate));
        aPeakFall = (float)std::exp(-1.0 / (tauPeak * rate));
    }
    void push(float inDb) {
        const float a = (inDb > valueDb) ? aAtt : aRel;  // fast up, slow down
        valueDb = inDb + a * (valueDb - inDb);
        if (inDb > peakDb) peakDb = inDb;                // instant peak capture
        else peakDb = inDb + aPeakFall * (peakDb - inDb);// slow peak decay
    }
};
```

Typical starting points: attack ~10 ms, release ~150–300 ms, peak-hold decay ~1–2 s. Keep one
`BinBallistics` per displayed point (not per FFT bin — per the smoothed display resolution).

### Pre/post overlay

To show input vs processed spectrum (a common EQ feature), run **two** analysis STFTs — one
tapped before the processing chain, one after — each with its own ballistics, and draw both
curves. They share the calibration and axis code above; only the tap point differs.

### Lock-free audio → UI hand-off (triple buffer)

The audio thread produces magnitude frames; the UI thread consumes them at its own refresh
rate. Never share them under a lock (priority inversion → audio dropout) and never allocate.
A **single-producer/single-consumer triple buffer** gives the UI always-the-freshest complete
frame, wait-free on both sides. Three frame buffers cycle among three roles (writing, freshest,
reading) tracked by one atomic; a dirty bit reports whether new data arrived since the last
read.

```cpp
#include <atomic>

// SPSC triple buffer. Producer (audio): write into producerBuffer(), then publish().
// Consumer (UI): fetch() returns the freshest frame or nullptr if nothing new. Wait-free,
// no allocation, no lock. Frame is a fixed-size POD (e.g. std::array<float, kNumPoints>).
template <class Frame>
class SpscTripleBuffer {
public:
    Frame& producerBuffer() { return buf_[write_]; }
    void publish() {                                     // audio thread
        const int prev = shared_.exchange(write_ | kDirty, std::memory_order_acq_rel);
        write_ = prev & kIndexMask;                      // recycle previously-shared buffer
    }
    Frame* fetch() {                                     // UI thread
        if (!(shared_.load(std::memory_order_acquire) & kDirty)) return nullptr;
        const int prev = shared_.exchange(read_, std::memory_order_acq_rel); // clears dirty
        read_ = prev & kIndexMask;
        return &buf_[read_];
    }
private:
    static constexpr int kIndexMask = 0x3;
    static constexpr int kDirty     = 0x4;
    Frame buf_[3];
    int write_ = 0, read_ = 1;
    std::atomic<int> shared_{2};                         // index (+ dirty bit) of freshest frame
};
```

The three indices `{0,1,2}` are always partitioned among `write_`, `read_`, and `shared_`;
each `exchange` swaps exactly one index in and one out, so no frame is ever lost or aliased
between the threads — the producer never writes the buffer the consumer is reading. The audio
side computes magnitudes into `producerBuffer()` once per (decimated) frame and calls
`publish()`; the UI timer calls `fetch()` and redraws only when it returns non-null. This is
the same lock-free discipline as the project's ONNX/threading references and the forthcoming
`vst3-threading-realtime-safety` skill — use those for the broader real-time-safety rules
(no locks/allocation/syscalls on the audio thread) and for queueing to a worker thread.

---

## Cross-references

- **FIR tap design** for the linear-phase EQ you convolve here → `audio-filters-eq` skill.
- **Convolution reverb** (full non-uniform partitioned engine, IR tail handling) → forthcoming
  `audio-reverb` skill; it builds on Part 1.
- **Peak/RMS/LUFS meters and true-peak** (numeric loudness, distinct from this visual analyzer)
  → forthcoming `audio-metering` skill.
- **Window math** (`makeWindow`, `analyzeWindow`, `sumW`, `ENBW`) and the `RealFFT` /
  `STFTProcessor` classes used throughout → `SKILL.md`.
- **Real-time threading rules** and worker-thread queueing → project ONNX/threading references
  and forthcoming `vst3-threading-realtime-safety` skill.
