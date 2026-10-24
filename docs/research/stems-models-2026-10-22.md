# Stem separation: which Spleeter, which runtime, and how the model gets onto the phone (22 Oct 2026)

Desk research for M4 part 5 (plan §10.6 "Separate stems", §14.2 target: 4 stems of a 3-minute song in under 5 minutes, in the background). Nothing here has run on the S22 Ultra.

## What exists
- **sherpa-onnx's own Spleeter (Apache-2.0 code):** 2 stems only (vocals, accompaniment), in fp32, fp16 and int8 (`csukuangfj/sherpa-onnx-spleeter-2stems*` on Hugging Face). sherpa-onnx's C++ does the STFT, masks and inverse STFT (`OfflineSourceSeparation` with a `spleeter` config of vocals plus accompaniment). It does not give the plan's bass and drums stems. Source: the k2-fsa source-separation demo, https://huggingface.co/spaces/k2-fsa/source-separation
- **Community 4-stem conversion,** https://huggingface.co/Best-Practice/spleeter-4stems-onnx. Deezer's October 2019 4stems checkpoint went TensorFlow → PyTorch → ONNX, on sherpa-onnx's U-Net with the activations made settable (4stems needs ELU).
  - **Files:** one U-Net per stem (vocals, drums, bass, other), 9.8 M parameters each: fp16 19.7 MB each, fp32 39.4 MB each.
  - **Tensors:** input `x` and output `y` are `[2, splits, 512, 1024]` magnitudes.
  - **Pre-processing (ours to do):** 44.1 kHz stereo; periodic Hann window, 4096-point frames, 1024 hop; keep 1024 of 2049 bins; 512-frame chunks; 3072 zeros in front.
  - **Post-processing (ours to do):** a ratio mask, each stem's square over the sum of all four squares, so all four models are needed; masks extended to 2049 bins by the per-frame mean ("average"); applied to the original complex STFT; weighted overlap-add inverse STFT.
  - **Self-reported checks** (not reproduced here): per-stem error against TensorFlow 2.6e-4 to 1.3e-2; the four stems summed against the mix −153 dB; 30 s of stereo in 1.69 s on a desktop CPU.
  - The card lists Apache-2.0 for the graph.

## Licence of the weights
Deezer's README licenses the code under MIT and doesn't name the weights. Issue deezer/spleeter#898 ("license for model weights", opened April 2024) is still open with no maintainer answer. The last comment, on 14 Oct 2026, asks the same question before shipping an app.

The JOSS paper by Deezer's authors (https://www.theoj.org/joss-papers/joss.02154/10.21105.joss.02154.pdf) says the source code and the pre-trained models are MIT; plan research already used that. The question is the same for sherpa-onnx's 2-stem files (same weights). Apophenia is free and GPL, but redistribution still needs the licence, so the notices should cite the JOSS statement.

## Runtime
- Running the 4-stem files needs ONNX Runtime: `com.microsoft.onnxruntime:onnxruntime-android` on the phone, and `com.microsoft.onnxruntime:onnxruntime` on the desktop, which can run the same models in a unit test on this PC (LiteRT can't).
- **Conflict to avoid:** sherpa-onnx's Android libraries carry their own `libonnxruntime.so`. Using both sherpa-onnx (for Moonshine) and Microsoft's onnxruntime-android would put two copies in the APK under the same name. One runtime should serve both stems and speech: either run Moonshine's ONNX files on the same ONNX Runtime with our own decoding loop, or use Moonshine's own Android library if it depends on the same Maven onnxruntime. To check at M4 part 6.

## Size: the decision for Christopher
- 4 stems in fp16 is about 79 MB of model, so the APK goes from about 49 MB to about 130 MB.
- Moonshine adds about 58 MB (Base) or about 123 MB (Small Streaming).
- Model weights barely compress, so the zip sent to the phone grows the same amount.
- The Claude app on the phone takes files up to about 30 MiB (memory note), so a bundled APK can't reach the phone that way.

The options:
1. **Model files imported once** [recommended]:
   - The APK stays small. Each model file (19.7 MB, under the limit) is sent on its own and added in the app with the system file picker (AI › Models › Add).
   - The app copies it into its own storage and checks its size and SHA-256 against a list built into the app.
   - No internet permission.
   - Play and F-Droid releases could still bundle the models (Play asset delivery), decided at M7.
2. **Bundled in the APK:** one file to install, but about 130 MB now and about 190–250 MB with speech. It needs USB or adb, or another way onto the phone (memory: his USB link is unreliable).
3. **2 stems for now** (sherpa-onnx's vocals and accompaniment, about 20–40 MB): no bass stem. The plan asks for 4 or 5 stems, and the M3 goal is learning a bass line.

## Decision and download (22 Oct 2026)
**Christopher chose to bundle the models in the APK** ("Bundle."). The fp16 files were downloaded to `app/src/main/assets/models/spleeter-4stems/`, and each SHA-256 matches the repo's LFS id:
- vocals.fp16.onnx: 19714141 bytes, sha256 db47148ab1c52709ce694893f532c91abfe3edc4d46238939570e036a22878ca
- drums.fp16.onnx: 19714140 bytes, sha256 7ae4002e5633634674f74dc3356d5875b0da894d59ce0f60e844bb8f9cb8aa92
- bass.fp16.onnx: 19714139 bytes, sha256 ba4c4949a27222492cca49859901a873b4b71461dc48c7c5a51f93d31eb11f55
- other.fp16.onnx: 19714140 bytes, sha256 3cc59116cb7195946ab9596d8ca25984d09c0f8a70db8cf85d063132f97bc61d

The model card is kept as `assets/licenses/spleeter-4stems-onnx-README.md`.
