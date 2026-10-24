---
license: apache-2.0
tags:
  - audio
  - audio-to-audio
  - source-separation
  - music-source-separation
  - spleeter
  - onnx
library_name: onnx
pipeline_tag: audio-to-audio
---

# Spleeter 4stems — ONNX

Deezer's [Spleeter](https://github.com/deezer/spleeter) **4stems** — vocals,
drums, bass, other — converted to ONNX.

Deezer ship 4stems as a TensorFlow 1 checkpoint from October 2019.
[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) ship an excellent ONNX
export of **2stems** and state plainly: *"We only support the `2-stem` model at
present."* This is the missing half.

| file | size | notes |
|---|---|---|
| `{vocals,drums,bass,other}.fp16.onnx` | 19.7 MB each | **use these** — self-contained |
| `{vocals,drums,bass,other}.onnx` | 39.4 MB each | fp32, self-contained |

Each stem is an independent U-Net (9,826,759 params). Every file is
self-contained — no external-data sidecars.

## Verification

| | |
|---|---|
| TF vs PyTorch port, per stem | vocals 2.6e-04 · drums 1.3e-02 · bass 1.1e-03 · other 2.6e-03 |
| `sum(4 stems) − mix` | **−153.1 dB** |
| Inference, all four stems | 1.69 s for 30 s of 44.1 kHz stereo (~18× realtime, CPU) |

The −153.1 dB is the one to trust. Spleeter's ratio masks sum to exactly 1 by
construction, so the stems *must* reconstruct the mix — and they do, to machine
precision. That single assertion tests the STFT, the mask, the band extension
and the iSTFT at once, without anyone listening to anything.

## ⚠️ These models use ELU. This is the whole trick.

If you plan to run these through an existing Spleeter port, read this first.
Spleeter reads its activations from config:

```jsonc
// configs/2stems/base_config.json
"model": { "type": "unet.unet", "params": {} }
//                              ^^ defaults: LeakyReLU(0.2) + ReLU

// configs/4stems/base_config.json
"model": { "type": "unet.unet", "params": {
    "conv_activation": "ELU", "deconv_activation": "ELU" } }
```

2stems and 4stems have **identical architecture and identical weight shapes**,
kernel for kernel. So a 2stems-shaped implementation loads 4stems weights
**without a murmur** and returns garbage. That is why sherpa-onnx only publish
2stems: their `unet.py` hardcodes LeakyReLU/ReLU, and nothing about the shapes
tells you.

It presented as a max error of **945 against a TF output whose entire range was
178** — the error larger than the signal. With ELU: **945 → 2.6e-04**.

The activations are recorded in each file's ONNX metadata (`conv_activation`,
`deconv_activation`) so the next person doesn't have to find this the hard way.

## 16 kHz: the `.wide` files

Deezer's `4stems-16kHz` configuration is **not a separate model**. It runs the
same 4stems checkpoint over 1536 frequency bins instead of 1024, so it
separates up to 16 kHz rather than 11. That works because the U-Net is fully
convolutional. The original files fix the bin axis at 1024 in the graph.
`{vocals,drums,bass,other}.wide{,.fp16}.onnx` are the same weights exported
with the bin axis dynamic too:

| | |
|---|---|
| at 1024 bins | **bit-identical** to the original files (max abs diff 0) |
| at 1536 bins | Spleeter's 4stems-16kHz |

What 16 kHz buys, measured on 40 s of a full mix: the energy between 11 and
16 kHz (cymbals, hats, sibilance) goes 72% to drums and 27% to vocals when it
is masked by extending 11 kHz downward, and 90% to drums and 9% to vocals when
the net models it. It costs about 1.5× the inference, at the same file size.

## Interface

```
input   x : float32 [2, num_splits, 512, 1024]   # [channels, splits, frames, bins]
output  y : float32 [2, num_splits, 512, 1024]   # that stem's magnitude estimate
```

`num_splits` is dynamic. The graph takes **magnitudes and returns magnitudes** —
no complex numbers cross the boundary, which is why this ports cleanly where
time-domain models don't. The STFT, the mask and the iSTFT are yours to do.

### The STFT contract

The forward transform must produce the magnitudes the net was trained on:
**periodic Hann, frame 4096, hop 1024**, 44.1 kHz stereo. `np.hanning` is
*symmetric* and is **not** this — it differs by one sample, and that sample is
the difference between matching training-time spectrograms and merely
resembling them.

Feed `abs(stft(x))[..., :1024]` (1024 of 2049 bins), padded and partitioned to
512-frame splits.

### The mask

Spleeter's soft ratio mask, across all four stems:

```python
total = sum(e ** 2 for e in estimates.values()) + 1e-10
mask  = (estimate ** 2 + 1e-10 / 4) / total
```

Apply it to the **original complex STFT** — you keep the original phase, so
there is nothing to reconstruct. You cannot cherry-pick one stem: the
denominator needs all four.

### Band extension — use `average`, not `zeros`

The net models 1024 of 2049 bins (to ~11 kHz). Everything above needs a value.
Spleeter's default `zeros` **discards** it, which is a −23 dB hole in the
reconstruction. `average` carries the per-frame mean up and reconstructs
exactly (the −153.1 dB above is with `average`; with `zeros` it is −23.0 dB).

## Usage

Complete and runnable — the graphs are only half a separator, so here is the
other half.

```python
import numpy as np, onnxruntime as ort, soundfile as sf

N_FFT, HOP, T, F, BINS = 4096, 1024, 512, 1024, 2049
PAD = N_FFT - HOP
W = np.hanning(N_FFT + 1)[:-1]      # PERIODIC. np.hanning(N_FFT) is symmetric
STEMS = ("vocals", "drums", "bass", "other")

def stft(x):
    n = int(np.ceil((PAD + len(x)) / HOP))
    p = np.zeros((n - 1) * HOP + N_FFT)
    p[PAD:PAD + len(x)] = x          # front pad: see note below
    idx = np.arange(N_FFT)[None, :] + HOP * np.arange(n)[:, None]
    return np.fft.rfft(p[idx] * W, axis=-1)

def istft(spec, length):
    frames = np.fft.irfft(spec, n=N_FFT, axis=-1)
    total = (len(spec) - 1) * HOP + N_FFT
    out, wsum = np.zeros(total), np.zeros(total)
    for i in range(len(spec)):
        at = i * HOP
        out[at:at + N_FFT] += frames[i] * W
        wsum[at:at + N_FFT] += W ** 2
    out = np.divide(out, wsum, out=np.zeros_like(out), where=wsum > 1e-8)
    return out[PAD:PAD + length]

wave, sr = sf.read("song.wav", dtype="float64")   # 44.1 kHz stereo
assert sr == 44100 and wave.shape[1] == 2
n = len(wave)

spec = np.stack([stft(wave[:, c]) for c in range(2)])
frames = spec.shape[1]
splits = int(np.ceil(frames / T))
mag = np.zeros((2, splits * T, F), dtype=np.float32)
mag[:, :frames] = np.abs(spec[:, :, :F])
net_in = mag.reshape(2, splits, T, F)

est = {}
for s in STEMS:                       # all four: the mask needs every one
    sess = ort.InferenceSession(f"{s}.fp16.onnx", providers=["CPUExecutionProvider"])
    out = sess.run(["y"], {"x": net_in})[0]
    est[s] = out.reshape(2, -1, F)[:, :frames]

stems = {}
denom = sum(e ** 2 for e in est.values()) + 1e-10
for s, e in est.items():
    mask = (e ** 2 + 1e-10 / len(est)) / denom
    # extend 1024 -> 2049 bins with the per-frame mean ("average", not "zeros")
    tail = np.repeat(mask.mean(axis=-1, keepdims=True), BINS - F, axis=-1)
    full = np.concatenate([mask, tail], axis=-1)
    # applied to the ORIGINAL complex spectrum: the phase is already correct
    stems[s] = np.stack([istft(spec[c] * full[c], n) for c in range(2)], axis=-1)

# The invariant. Check this, not your ears -- see below.
res = 10 * np.log10(np.mean((sum(stems.values()) - wave) ** 2) / np.mean(wave ** 2))
print(f"sum(stems) - mix = {res:.1f} dB")     # -153 dB

for s, y in stems.items():
    sf.write(f"out_{s}.wav", y, sr, subtype="FLOAT")
```

### Check the invariant, not your ears

The four masks sum to 1 by construction, so `sum(stems)` **must** equal the mix.
It does, to **−153.1 dB**. If yours doesn't, your window or your hop is wrong,
and no amount of listening will tell you which — every one of those mistakes
produces audio that sounds approximately right.

Two ways to measure it wrongly, both learned the hard way:

- **Measure it in memory, before writing.** `sf.write` defaults to `PCM_16`, and
  16-bit quantisation of four stems costs ~75 dB on its own — enough to turn
  −153 into −77 and send you hunting a bug that isn't there. Hence
  `subtype="FLOAT"` above.
- **`zeros` is not a bug.** With Spleeter's default `mask_extension`, −23 dB is
  the *correct* answer: that is the >11 kHz band being discarded, exactly as
  asked.

### Why the front pad

`stft` above prepends `N_FFT-HOP` zeros, which Spleeter does not. Alignment is
not part of the contract — the U-Net is convolutional and translation-equivariant
in time — and without the pad, reconstruction is exact in theory and broken in
practice: on the ramp-in a periodic Hann is ~1e-7, so `W²` is ~1e-13, and
dividing by it turns float noise into the one stretch of signal with no
redundancy to spare. Unpadded, the round-trip error is **2.3**. Padded, it is
**1e-15**.

This is also why there is no `WINDOW_COMPENSATION_FACTOR` here. Spleeter's 2/3
constant and its `inverse_stft_window_fn` exist to undo TensorFlow-specific
normalisation; plain weighted overlap-add inverts this forward with no constants
at all.

## Source

Conversion scripts, the full pipeline, the tests, and the debugging trail that
found the ELU:
**[github.com/madewith-bestpractice/spleeter-4stems-onnx](https://github.com/madewith-bestpractice/spleeter-4stems-onnx)**

## License and attribution

**Apache-2.0**, and deliberately the stricter of the two licences in play, since
these artefacts carry both:

- **The weights are Deezer's Spleeter, MIT.** Spleeter's README licenses "the
  code"; the authors' own peer-reviewed JOSS paper (Hennequin, Khlif, Voituret &
  Moussallam, JOSS 5(50):2154, 2020, [doi:10.21105/joss.02154](https://doi.org/10.21105/joss.02154))
  states: *"Spleeter source code and pre-trained models are available on github
  and distributed under a MIT license."* That ambiguity is real and unresolved —
  [deezer/spleeter#898](https://github.com/deezer/spleeter/issues/898) has been
  open and unanswered since 2024-04-26 — so it is stated here rather than
  glossed. Deezer trained on their own licensed catalogue (the "Bean" dataset)
  and released the weights *because* the data could not be released, so unlike
  models trained on MUSDB18 there is no non-commercial dataset term upstream.
- **The graph derives from [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)
  (Apache-2.0)**, whose PyTorch reimplementation of Spleeter's U-Net these were
  exported through. Per Apache-2.0 §4(b): that U-Net was **modified** — its two
  activation functions were parameterised instead of hardcoded.

## Credits

- **[Deezer](https://github.com/deezer/spleeter)** — Spleeter, and for training
  on a licensed catalogue and releasing the weights *because* the data couldn't
  be. That decision is the only reason this model is usable rather than merely
  good.
- **[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)** (Xiaomi, Fangjun
  Kuang) — the TF→PyTorch→ONNX route, the U-Net port, and the TF-vs-torch
  assertion that made the ELU bug findable instead of silent. This is a
  four-stem extension of their work, not a replacement for it.

```bibtex
@article{spleeter2020,
  doi = {10.21105/joss.02154},
  author = {Romain Hennequin and Anis Khlif and Felix Voituret and Manuel Moussallam},
  title = {Spleeter: a fast and efficient music source separation tool with pre-trained models},
  journal = {Journal of Open Source Software},
  volume = {5}, number = {50}, pages = {2154}, year = {2020}
}
```
