# Harmonic analysis, strum timing and guitar model trial — 29 Oct 2026

Christopher: “Lets try that.” Scope: test harmonic analysis and strum-aware timing,
and benchmark a guitar-specific transcription model. Preserve original audio and
all existing MIDI notes. A model-choice question is pending; no Guitar model is
bundled or exposed in the app by this trial.

## Harmonic fit

Own Kotlin implementation in `core/analysis/HarmonicChroma.kt`, following the
non-negative spectral-fitting idea described by
[Mauch/Cannam's NNLS Chroma documentation](https://isophonics.net/nnls-chroma).
This is not a port of the full NNLS Chroma plugin: no log-frequency front end,
global tuning correction or spectral whitening is shipped here.

The existing periodic-Hann FFT (4096, hop 1024, analysis at 24 kHz) supplies
magnitudes. Each beat's spectrum is explained by harmonic note profiles, E1–E6
fundamentals, up to twelve harmonics through 2.5 kHz, geometric amplitude .6.
Non-negative least squares is solved with a cached dictionary and Gram matrix,
cyclic coordinate descent, at most 256 iterations. Fitted fundamentals are folded
to pitch classes and matched to fundamental chord templates. The bass bonus is
.1 without transcription and .2 with it. The existing detected-note evidence,
50/50 blend and twelve chord qualities remain. No fitted component becomes a
MIDI event. Dictionary/scratch work stays on the analysis worker.

The solver agreed with SciPy NNLS within 1.8e-6 of the largest coefficient in a
sample of recorded spectra. A test-only spectral fixture and independent SciPy
answer (CC BY 4.0 GuitarSet attribution in NOTICE/fixture README) guard dictionary,
scaling and solver together. Digital silence produces zero evidence; a generated
C with its harmonics fits a single C pitch class rather than separate overtone
classes. Existing speech suppression is retained. White/brown noise and hum can
still produce false chord labels, as the old path did; this is not a new general
speech/music/noise classifier.

Primary comparison: GuitarSet 180 mono-mic comping takes, same model beat grids
and Basic Pitch detections as 0.9.24. Players 00–02 for trials, 03–05 for comparison;
the comparison data have been inspected repeatedly, so this is not a blind test.
Final scoring uses compiled Kotlin predictions and the original mir_eval interval
merging, against both instructed and performed-note annotations. Full quality is
root plus pitch-class quality (`tetrads`); bass inversion is excluded. A real
piano regression caught a ringing final C being called C7. A .03 seventh prior
in the untranscribed harmonic path fixes that guard. All scores below include it;
the preliminary audio-only performed score .307 was before this correction.

Final comparison players 03–05 (90 takes):

| Annotation / path | Major/minor | Root | Thirds | Full quality |
| --- | --- | --- | --- | --- |
| Instructed, old sound only | .226 | .613 | .512 | .157 |
| Instructed, harmonic sound only | .338 | .630 | .557 | .252 |
| Instructed, 0.9.24 with notes | .635 | .719 | .685 | .453 |
| Instructed, 0.9.25 with notes | .659 | .727 | .703 | .454 |
| Performed, old sound only | .283 | .613 | .515 | .192 |
| Performed, harmonic sound only | .431 | .630 | .562 | .278 |
| Performed, 0.9.24 with notes | .788 | .719 | .688 | .470 |
| Performed, 0.9.25 with notes | .809 | .727 | .699 | .489 |

Trial players 00–02 (90 takes), performed full quality: old sound only .274,
harmonic sound only .392, 0.9.24 with notes .653, 0.9.25 with notes .657.
The note-backed improvement is modest and varies by style; this does not solve
missed detections or between-beat chord timing. Final log:
`D:/junkdrawer/apo-ref/harmonic25-native-final-scores.txt`.

The existing 03_BN2 guitar fixture's performed annotation is Em7/1, although its
sheet instruction says Em. The new integration expectation follows the performed
seventh. Clean C/E, E5, Am, G7, sus and dim tests remain. Deliberately changing the
dictionary to the wrong harmonic shape, or disconnecting the new reader in
`find`, causes assertion failures. Sources restored after each mutation.

**Unit-tested / build-verified:** 635 tests pass (501 core, 88 data, 19 app,
27 audio). Debug APK, signed arm64 APK and AAB built for 0.9.25/code106.
Signature/certificate, permissions and bundle validation checked; artifacts in
`dist/release`. No phone or ear verification. Analysis version 9 reruns existing
takes while reusing saved transcriptions; no retranscription is required.

## Timing trials — not shipped

On the 90 tuning recordings, performed full quality was .6527 at 0.9.24.
Discarding the first 40/80/120 ms of note evidence gave .6521/.6458/.6412.
Reducing old ringing-note contributions to .5/.75 when new attacks appeared gave
.6521/.6520. Moving the complete observation window by −80/−40/+40/+80 ms gave
.6480/.6542/.6517/.6405. None justified a new timing rule: useful held notes were
lost and roots could worsen. MIDI timings and beat positions were not changed.
The chart remains one slot per beat; between-beat chord changes are not implemented.

## Guitar-specific model benchmark — desktop only

Author's published [model repository](https://huggingface.co/xavriley/midi-transcription-models)
declares MIT. Artifact `guitar-gaps.pth`, pinned revision
`b7bec65a2b860aca72856b0feef58b5df407b777`, 99,178,877 bytes, 24,651,903 model
parameters. Code from the author's
[hf_midi_transcription](https://github.com/xavriley/hf_midi_transcription) at
`96f6797881e9497cbfc8f8e5deccea9c1f2f7adc`, inference fork at
`7568dc7f78b625e40cf9776e2806d164006610e3`. The README's monophonic claims conflict
with its strumming claims; inspected code produces independent per-pitch
onset/offset/frame/velocity outputs for all 88 pitches and decodes overlapping
notes. The checkpoint loads with every tensor matching strictly. Numeric NumPy
checkpoint metadata was allowlisted for `weights_only=True`; unrestricted pickle
loading was not used. Exact training overlap with this GuitarSet subset has not
been independently established.

Ten comping clips, deterministic first/last file per style among players 03–05:
two each of bossa, funk, jazz, rock and singer-songwriter; 275.51 s total. Manifest
is on D:. Author defaults: onset .3, offset .3, frame .1; 16 kHz mono, ten-second
windows with half overlap. Compare note precision/recall/F at 50 ms onset tolerance
and without offset scoring, against GuitarSet's note annotations. No threshold
tuning on these ten clips.

| Variant | Precision | Recall | Note F | Desktop runtime |
| --- | --- | --- | --- | --- |
| Existing Basic Pitch .6/.35/8 | .7325 | .7790 | .7450 | not retimed in this trial |
| Published guitar checkpoint, FP32 | .8812 | .8715 | .8750 | 181.25 s |
| Compact ONNX candidate | .8808 | .8713 | .8747 | 100.03 s |

ONNX export splits off the fixed STFT/log-mel front end. The FP32 back end is
80,969,797 bytes, MatMul/Gemm dynamic-int8 version 63,694,553 bytes. Storing GRU
weights as FP16 with explicit FP32 casts before CPU GRUs reduces it to
38,690,655 bytes, plus a 0.94 MB mel-filter matrix. CPU GRU computation remains
FP32. Silence alone was not treated as quantisation validation: the compact model
was rescored on all ten musical clips. Runtime includes the Torch reference
front end, ONNX CPU inference and reference decoder on this PC with two threads;
this is not an Android speed or memory measurement. Different training data and
the small, inspected sample limit what can be inferred about generalisation.

No new model is in the APK. Its Kotlin front end/decoder, app integration,
instrument/model selection, device speed/memory and distribution size remain
unverified. Christopher was asked whether to add an explicit Guitar choice to
Notes → MIDI or keep this model as a desktop benchmark; no answer yet. The app
must not guess the instrument automatically (AGENTS.md).

## Reproduction

All large assets and optional probes: `D:/junkdrawer/apo-ref`.
`harmonic_features25.py`, `harmonic_evaluate25.py`, `harmonic_audio_only25.py`,
`harmonic_check25.py`, `harmonic_with_notes_check25.py`, `nnls_coordinate25.py`,
`strum_evaluate25.py`, `strum_audio25.py`, `ZHarmonic25Probe.kt`,
`harmonic_native_scores25.py`, `harmonic_mutations25.py`.
Raw spectra in `harmonic25/`, predictions `chords/*.harmonic25*.txt`.
Model: `gaps25_benchmark.py`, `gaps25_export.py`, `gaps25_compress.py`,
`gaps25_compact_benchmark.py`; manifests, weights, JSON results and converted
models in `gaps25/`. Probes are removed from normal unit tests after extraction.

## 0.9.26: explicit Guitar option authorised and integrated

Christopher: “So make it an option and leave basic pitch for vocals and other
instruments.” This answers the earlier pending model-choice question. Basic Pitch
remains the default on a take with no saved transcription. Notes → MIDI shows a
MODEL dropdown for Basic Pitch / Guitar, as does Performance check's missing-MIDI
prompt. The recording's AI menu now opens this choice before starting a new job.
Existing results initialise the choice from their saved model ID; changing the
choice alone does not run inference or replace notes. Retranscription is explicit,
with the existing LOSES FIXES label on corrected results. Jobs retain their model
choice, errors retry the chosen model, and saved results show their actual model.
No instrument is inferred and there is no silent fallback between the models.

Bundled assets: compact ONNX 38,690,655 bytes and the checkpoint's exact mel
matrix 938,900 bytes; revision and SHA-256 hashes in `model-metadata.json`.
Runtime uses the existing ONNX Runtime on the transcription worker, two threads,
one session per job, closed afterwards. The same five-minute limit applies.
The reader supplies mono audio, low-passed at 7.5 kHz before resampling to 16 kHz;
Basic Pitch's existing preprocessing/thresholds/decoder remain unchanged.

New `GuitarPitch.kt` implements periodic Hann power FFT, checkpoint mel matrix,
decibel conversion, reference overlapping-window selection and regression
onset/offset decoding. It supplies the same timed-note structure used by MIDI,
correction, tab, sheet music and subsequent key/chord analysis. No chord-derived
notes are inserted. The guitar checkpoint provides no continuous pitch bends;
the UI says that bends/slides may split into notes. Conversion and independent
reference generation are reproducible with `tools/guitar/export.py` and
`tools/guitar/reference.py`; see their README for pinned sources/dependencies.

**Unit-tested on the desktop JVM:** the actual bundled model and Kotlin frontend
on the ten-second recorded GuitarSet fixture produce the same 69 notes and MIDI
velocities as the Python reference, onset/offset differences below 1 ms. Mel
maximum difference .00364 dB, RMS .0000268 dB against torchlibrosa's convolution
STFT; maximum activation difference .00448 between the two runtime/frontend
paths. Reference-output decoding agrees within 1 μs. The actual model on digital
silence produces no notes. A three-window seam test rejects discarded edge
predictions and keeps each central attack once. Stored model IDs distinguish old
Basic Pitch and Guitar results, including corrected data. Deliberate frontend,
decoder and provenance mutations each fail the relevant assertion; restored.

These port checks do not expand the earlier ten-clip accuracy benchmark, verify
Android resampling equivalence, or establish phone speed/RAM. The published
87.5% / compact 87.47% note F remains a desktop Python benchmark with unknown
checkpoint training overlap. Phone performance, listening accuracy, bends/slides,
new selector layout and long-take resource use still require a device walk.

**Build-verified:** full 639 tests pass; arm64 debug APK and signed release APK/AAB
built, signature/certificate/permissions and bundle validation checked. Local
`dist/release/Apophenia-0.9.26.apk` and `.aab`; no publication. Bundletool compressed
download estimate 200,055,103–200,059,291 bytes (190.79 MiB). The emulator remained
offline after the cold-boot attempt; no UI/device/ear verification was completed.
