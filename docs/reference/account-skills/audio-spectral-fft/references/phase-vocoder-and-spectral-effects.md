# Phase Vocoder & Spectral Effects

Depth reference for `audio-spectral-fft`. Everything here operates on the `RealFFT` /
`STFTProcessor` from `SKILL.md`: each effect is an **in-place spectral op** `void op(RealFFT&)`
run once per STFT frame, holding its own pre-allocated state. The FFT data is single-precision
(`float`, pffft); phase accumulation is kept in **`double`** so it does not drift over long
runs. Nothing here allocates on the audio thread.

All formulas are from the standard phase-vocoder literature, cited inline. The one formula
people get wrong — instantaneous frequency — is derived explicitly so there is no guessing.

## Magnitude / phase, and the packed-bin reminder

The op reads bins via `RealFFT::magnitude(k)` / `RealFFT::phase(k)` and writes via
`RealFFT::setBin(k, re, im)`, which already handle the FFTPACK DC/Nyquist packing
(`SKILL.md`). To convert a (magnitude, phase) pair back to a bin:

```cpp
inline void setBinPolar(RealFFT& f, int k, double mag, double phase) {
    f.setBin(k, (float)(mag * std::cos(phase)), (float)(mag * std::sin(phase)));
}
```

## Instantaneous frequency via principal-argument unwrapping

The center frequency of bin `k` is `ω_k = 2π k / N` rad/sample, but the actual partial in that
bin is rarely exactly at center. Its true frequency is recovered by **differentiating the
unwrapped phase** between consecutive frames and **adding the bin-center frequency back in** —
this is exactly the classic phase-vocoder procedure (M. Dolson, "The Phase Vocoder: A
Tutorial," *Computer Music Journal* 10(4), 1986: the per-bin instantaneous frequency "refers
only to the difference frequency between the heterodyning sinusoid — the filter center
frequency — and the input signal… the final step is simply to add the filter center frequency
back in"). The steps, for analysis hop `H` and bin `k`:

```
1. measured phase delta:    Δφ = φ_m[k] − φ_{m−1}[k]
2. expected center advance: Δφ_exp = ω_k · H = k · (2π H / N)
3. principal-argument dev:  Δφ_dev = princarg(Δφ − Δφ_exp)      // wrap to (−π, π]
4. true frequency:          f_true[k] = (k + Δφ_dev / (2π H / N)) · (fs / N)   [Hz]
   equivalently in bins:    b_true[k] = k + Δφ_dev / (2π H / N)
```

`princarg(x) = x − 2π·round(x / 2π)`. Step 2 (subtracting the expected per-hop center advance)
is the part a naive implementation drops — without it the unwrap is wrong for every bin except
DC. Defining `expectedPerBin = 2π H / N` once, the true partial frequency expressed as a
**fractional bin number** is `b_true = k + princarg(Δφ − k·expectedPerBin) / expectedPerBin`.

```cpp
#define _USE_MATH_DEFINES
#include <cmath>
#include <vector>
#include <random>

inline double princarg(double x) {                  // wrap to (−pi, pi]
    const double twoPi = 6.283185307179586;
    return x - twoPi * std::round(x / twoPi);
}

// Shared phase-vocoder state: previous analysis phase and accumulated synthesis phase
// per bin. Allocate in prepare(); reset() zeroes it.
struct PVState {
    std::vector<double> prevPhase;   // φ_{m−1}[k]
    std::vector<double> sumPhase;    // accumulated synthesis phase ψ[k]
    std::vector<double> anaMag;      // analysis magnitude per bin
    std::vector<double> anaFreqBin;  // analysis true frequency as fractional bin number
    std::vector<double> synMag;      // synthesis magnitude per bin
    std::vector<double> synFreqBin;  // synthesis true frequency (bins)
    double expectedPerBin = 0.0;     // 2*pi*H/N

    void prepare(int numBins, int N, int H) {
        prevPhase.assign(numBins, 0.0); sumPhase.assign(numBins, 0.0);
        anaMag.assign(numBins, 0.0);    anaFreqBin.assign(numBins, 0.0);
        synMag.assign(numBins, 0.0);    synFreqBin.assign(numBins, 0.0);
        expectedPerBin = 6.283185307179586 * (double)H / (double)N;
    }
    void reset() {
        std::fill(prevPhase.begin(), prevPhase.end(), 0.0);
        std::fill(sumPhase.begin(),  sumPhase.end(),  0.0);
    }
    // Analysis: fill anaMag / anaFreqBin from the current spectrum.
    void analyze(RealFFT& f) {
        const int bins = f.numBins();
        for (int k = 0; k < bins; ++k) {
            const double mag   = f.magnitude(k);
            const double phase = f.phase(k);
            double d = phase - prevPhase[k];
            prevPhase[k] = phase;
            d -= (double)k * expectedPerBin;          // subtract expected center advance
            d  = princarg(d);
            anaMag[k]     = mag;
            anaFreqBin[k] = (double)k + d / expectedPerBin;   // fractional true bin
        }
    }
    // Synthesis: write spectrum from synMag / synFreqBin by accumulating phase.
    void synthesize(RealFFT& f) {
        const int bins = f.numBins();
        for (int k = 0; k < bins; ++k) {
            sumPhase[k] += synFreqBin[k] * expectedPerBin;     // advance by this hop
            setBinPolar(f, k, synMag[k], sumPhase[k]);
        }
    }
};
```

## Pitch shifting in the fixed-hop engine

The `STFTProcessor` uses one hop for analysis and synthesis, so it preserves stream length. A
constant-length pitch shift therefore **moves each analysis bin's energy to a new bin and
scales its true frequency by the pitch ratio**, then resynthesizes at the same hop (the
approach in S. M. Bernsee, "Pitch Shifting Using The Fourier Transform," dspdimension.com,
1999). Ratio `2^(semitones/12)`.

```cpp
struct PitchShifter {
    PVState pv;
    double ratio = 1.0;                                  // 2^(semitones/12)
    void prepare(int numBins, int N, int H) { pv.prepare(numBins, N, H); }
    void reset() { pv.reset(); }

    void operator()(RealFFT& f) {
        const int bins = f.numBins();
        pv.analyze(f);
        std::fill(pv.synMag.begin(),     pv.synMag.end(),     0.0);
        std::fill(pv.synFreqBin.begin(), pv.synFreqBin.end(), 0.0);
        for (int k = 0; k < bins; ++k) {
            const int j = (int)std::lround((double)k * ratio);   // destination bin
            if (j >= 0 && j < bins) {
                pv.synMag[j]    += pv.anaMag[k];                  // accumulate magnitudes
                pv.synFreqBin[j] = pv.anaFreqBin[k] * ratio;      // scale true frequency
            }
        }
        pv.synthesize(f);
    }
};
```

This is real-time and keeps length constant, but bin-rounding limits quality (formants shift
with pitch; transients smear — see below). **Higher-quality / time-stretch** pitch shifting
decouples the analysis and synthesis hops to *time-stretch* by `1/ratio`, then *resamples* by
`ratio` to restore pitch-shift-with-constant-length — that needs a resampler and an engine
whose input and output rates differ, which is outside the 1:1 `STFTProcessor`. Use the
stretch-then-resample structure when you need transparent, formant-aware shifting; the
resampler belongs to the saturation/oversampling skill's polyphase FIR machinery. For
formant preservation, apply a spectral-envelope correction (cepstral or LPC envelope) after
shifting — a documented enhancement, flagged here as an extension rather than reproduced.

## Phase locking (vertical coherence)

Shifting bins independently lets partials that belong to one sinusoid drift out of phase with
each other, producing the watery "phasiness" the phase vocoder is infamous for. The fix is
**phase locking**: detect spectral peaks, and lock the phase of each bin in a peak's region of
influence to the peak's phase (J. Laroche and M. Dolson, "Improved Phase Vocoder Time-Scale
Modification of Audio," *IEEE Trans. Speech and Audio Processing* 7(3), 1999; the precursor
identity-phase-locking idea is M. Puckette, "Phase-locked Vocoder," IEEE ASSP Workshop, 1995).
Identity phase locking (Puckette) is the cheap, robust version: for each bin, rotate its
synthesis phase by the phase relationship it had to the nearest peak in the analysis frame,
instead of accumulating each bin independently. Peaks are local magnitude maxima (`mag[k] >
mag[k−1]` and `mag[k] > mag[k+1]`); each non-peak bin is assigned to its nearest peak. This
markedly cleans up time-stretch and pitch-shift on tonal material; it still does not fix
**transients** (note onsets), which require onset detection and a phase reset on the onset
frame (Röbel) — a documented extension, not reproduced here.

## Spectral effects catalog

Each is an in-place op; the simpler ones need no PV state. (Effect taxonomy consistent with
the project's spectral-effects notes.)

**Spectral gate / expander** — zero or attenuate bins below a magnitude floor (shown in
`SKILL.md`). A soft version multiplies each bin by a smooth gain `g(mag)` instead of a hard
zero, to avoid musical-noise artifacts.

**Spectral freeze** — capture one magnitude frame, then hold it while advancing synthesis
phase at each bin's center frequency so the sustained spectrum stays phase-coherent:

```cpp
struct SpectralFreeze {
    std::vector<double> frozenMag, sumPhase;
    double perBinCenter = 0.0;          // 2*pi*H/N
    bool frozen = false, capture = false;
    void prepare(int numBins, int N, int H) {
        frozenMag.assign(numBins, 0.0); sumPhase.assign(numBins, 0.0);
        perBinCenter = 6.283185307179586 * (double)H / (double)N;
    }
    void operator()(RealFFT& f) {
        const int bins = f.numBins();
        if (capture) { for (int k=0;k<bins;++k) frozenMag[k]=f.magnitude(k); capture=false; frozen=true; }
        if (!frozen) return;            // pass through until frozen
        for (int k = 0; k < bins; ++k) {
            sumPhase[k] += (double)k * perBinCenter;        // bin-center advance
            setBinPolar(f, k, frozenMag[k], sumPhase[k]);
        }
    }
};
```

**Spectral blur** — average each bin's magnitude over the last `K` frames (a small ring of
magnitude frames), keeping the current phase. Smears the spectrum in time for pads/textures.

**Robotization** — set every bin's phase to zero each frame (`setBin(k, mag, 0)`). Forcing a
constant phase imposes a pitch equal to the frame rate `fs/H`, giving the classic monotone
robot voice.

**Whisperization** — randomize every bin's phase each frame:

```cpp
struct Whisperize {
    std::mt19937 rng{0x9E3779B9u};
    std::uniform_real_distribution<double> dist{-3.141592653589793, 3.141592653589793};
    void operator()(RealFFT& f) {
        const int bins = f.numBins();
        for (int k = 0; k < bins; ++k) setBinPolar(f, k, f.magnitude(k), dist(rng));
    }
};
```

Destroying phase coherence turns voiced sound into a whispered/aspirated texture. (The RNG is
pre-seeded and stateful — no allocation; reproducible if you save its state, which the project's
state-serialization pattern covers.)

**Spectral-subtraction de-noise** — estimate a per-bin noise magnitude floor `Nest[k]` (from a
captured noise frame or a slowly-tracking per-bin minimum), then half-wave-rectified-subtract
it from each magnitude while keeping phase (S. Boll, "Suppression of Acoustic Noise in Speech
Using Spectral Subtraction," *IEEE Trans. ASSP* 27(2), 1979):

```cpp
// over-subtraction factor alpha (1..2) and spectral floor beta (e.g. 0.02) reduce musical noise
double mag = f.magnitude(k);
double out = mag - alpha * Nest[k];
if (out < beta * mag) out = beta * mag;           // floor instead of hard zero
setBinPolar(f, k, out, f.phase(k));
```

This is the documented core for a **dynamic resonance suppressor / de-esser** front-end (the
Pro-DS / soothe2-class target): per-bin or per-region magnitude attenuation driven by a
detector. The exact soothe2 detection/selectivity is proprietary; the STFT-domain
per-bin-attenuation approach above is the documented, permissive equivalent and is the method
to build from.

## The transient-smearing caveat

Every magnitude/phase technique here filters over a window of length `N`, so sharp onsets get
smeared in time — the unavoidable time/frequency trade-off of the STFT. Larger `N` gives finer
frequency resolution but worse transient response; smaller `N` the reverse. For
transient-sensitive material, either use a smaller window with higher overlap, detect onsets
and reset phase on the onset frame, or split processing into transient and steady-state
streams. Choose `N` for the job: `2048` is a good general default at 44.1/48 kHz (≈43 ms
window, ≈21–23 Hz bins); drop to `1024` or `512` for percussive material, raise to `4096`/`8192`
for fine spectral surgery on sustained tones.
