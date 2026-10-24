# On-device models: transcription, stems, speech (research, 21 Oct 2026)

Desk research only. Nothing here has been run on the S22 Ultra (SM-S908U); every number from other devices must be re-measured in the Device Lab (official plan §14.2).

## Transcription — Spotify Basic Pitch
- Code Apache-2.0 (Copyright 2022 Spotify AB). Repo ships TensorFlow, CoreML, TensorFlow Lite and ONNX versions. Instrument-agnostic, polyphonic, outputs MIDI with pitch bends; "works best on one instrument at a time". Audio is resampled to 22,050 Hz mono. — https://github.com/spotify/basic-pitch
- LiteRT (TFLite) build: **0.84 MB** FP32, Apache-2.0. Input `[1, 43844]` float32 = 2 s mono at 22,050 Hz in [-1, 1]; windows overlap by 7,680 samples, keep centre frames when stitching. Outputs sigmoid posteriorgrams (~11.6 ms frames) for contour, note and onset; note/onset bins map to MIDI 21–108. Benchmarks published there: Pixel 8a GPU ~4.4 ms per 2 s window; Galaxy S26 GPU median 248 ms per 2 s window (NPU much slower). — https://huggingface.co/litert-community/Basic-Pitch-LiteRT
- Still needed: post-processing (posteriorgram → note events) ported from the Python reference; S22 Ultra speed/accuracy on phone-mic takes.

## Stem separation
| Model | Weights licence | Verdict |
|---|---|---|
| **Spleeter** (Deezer) | Code **and** pretrained models MIT; trained on Deezer internal data (not released). 2/4/5 stems. ~100× real time on one GPU (paper); no CPU figure. — https://www.theoj.org/joss-papers/joss.02154/10.21105.joss.02154.pdf | **Use.** Convert to TFLite/ONNX; STFT/iSTFT likely done in app code |
| **Demucs / HT-Demucs** (Meta) | Code MIT; author previously said weights are research-only; a July 2026 Hugging Face upload is tagged MIT with the question unanswered. — https://huggingface.co/adefossez/HTDemucs/discussions/1 | **Don't use** until the weights' licence is clear |
| **Open-Unmix** | UMX-L weights CC BY-NC-SA 4.0; licence of MUSDB-trained weights unstated. — https://pypi.org/project/openunmix/ | **Don't use** |
- Only phone timing found: a hobby TF2 Spleeter-style vocal model on Android took "3 to 5 minutes" (track length and device not stated); STFT done in app code. — https://fritz.ai/audio-source-separation-on-android/

## Speech to text
| Option | Licence | Size | Notes |
|---|---|---|---|
| **Moonshine** English models | MIT (code and English models); non-English models under a separate community licence | Tiny ~26 MB, Tiny Streaming ~34 MB, Base ~58 MB, Small Streaming ~123 MB, Medium Streaming ~245 MB | WER: Medium Streaming 6.65%, Small Streaming 7.84%, Whisper Small 8.59%, Whisper Large v3 7.44%. Android library on Maven (`ai.moonshine:moonshine-voice`); no phone latency published. — https://mintlify.wiki/moonshine-ai/moonshine/resources/faq |
| Whisper via whisper.cpp | MIT | base.en Q5_K ~57 MB | Pixel 6a: ~0.6× real time CPU, ~0.25× with Vulkan GPU encoder. — https://dev.to/software_mvp-factory/wiring-whispercpp-to-androids-audiorecord-api-hjo |
| NVIDIA Parakeet-TDT 0.6B (sherpa-onnx int8) | CC BY 4.0 | large | — https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8 |
| **sherpa-onnx** runtime | Apache-2.0 | — | Runs Moonshine, Whisper, Parakeet on Android arm64; VAD (silero), diarization. — https://pkg.go.dev/github.com/csukuangfj/sherpa-onnx@v1.12.33 |

Plan choice: Moonshine English via sherpa-onnx (or Moonshine's own Android library, whichever measures better). Model size chosen by S22 Ultra speed. No benchmark covers mumbled lyric ideas or speech over an instrument — measure on Christopher's own voice memos.

## Time-stretch / pitch-shift candidates (for conform and the slow-down player)
- **Rubber Band Library** — GPL-2.0-or-later (compatible with the app's GPL-3.0); high quality; C++.
- **Signalsmith Stretch** — MIT; C++.
- Choose by on-phone quality and speed test (official plan §14.2). See also `p0-research-2026-10-20.md` R3.
