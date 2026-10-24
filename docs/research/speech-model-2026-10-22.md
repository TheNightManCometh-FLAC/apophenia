# Speech → text: which Moonshine, which runtime (22 Oct 2026)

Desk research and desktop tests for M4 part 6 (plan §10.4). Nothing here has run on the S22 Ultra.

## What exists
- **Moonshine Base, standard ONNX** (`moonshine-ai/moonshine`, `onnx/merged/base/`): Hugging Face Optimum layout, an encoder that takes raw 16 kHz audio (`input_values [1, n]` → `last_hidden_state [1, T, 416]`) and a merged decoder with a key/value cache (`input_ids`, `encoder_hidden_states`, `past_key_values.{0..7}.{decoder,encoder}.{key,value} [1, 8, t, 52]`, `use_cache_branch`; out `logits [1, s, 32768]` and `present.*`). 8 layers, 8 heads, hidden 416, at most 194 decoder positions, start token 1, end token 2 (`config.json`, `generation_config.json`).
  - int8 ("quantized"): encoder 20.5 MB, decoder 42.5 MB. float: 81 + 166 MB.
- **Moonshine streaming (tiny/small/medium)**: only in `moonshine-ai/moonshine-voice-assets` as ORT-format pieces (frontend, encoder, adapter, cross_kv, decoder_kv). Their decoding loop lives in Moonshine's C++ library and isn't documented as a file format. Small Streaming is about 123 MB with the best published English WER of the small ones (7.84%).
- **Moonshine's Android library** `ai.moonshine:moonshine-voice` 0.1.5 (Maven): its POM pulls in OkHttp, AppCompat, Material and WorkManager (it downloads models), and the 24 MB AAR carries its own native code. Not a fit for an offline app that already ships Microsoft's ONNX Runtime.
- **sherpa-onnx** runs Moonshine too, but bundles its own `libonnxruntime.so`, which would clash with `onnxruntime-android` (stems).

## Decision [Claude, implementation]
Moonshine **Base int8** on the app's existing ONNX Runtime 1.31.0, with our own greedy decoding loop (`core/analysis/Speech.kt`). It's the only Moonshine format with a documented interface; it keeps one runtime for stems and speech. The plan's "[Claude: Small Streaming if the phone test shows acceptable speed, otherwise Base]" becomes: Base now; Small Streaming would mean porting Moonshine's C++ streaming loop, worth it only if Base's accuracy on Christopher's memos is not good enough (plan §14.2 measurement).

## Licence
MIT for the code and every Moonshine model (the repo LICENSE, kept as `assets/licenses/moonshine-LICENSE.txt`).

## Files (downloaded 22 Oct 2026 to `app/src/main/assets/models/moonshine-base/`, SHA-256 equal to the repo's LFS ids)
- encoder_model.onnx (from `onnx/merged/base/quantized/`): 20513063 bytes, sha256 1dd9ab0a7f987113d30affcba5a068d11c8f90fa0223caa3e491ade431ad9751
- decoder_model_merged.onnx (from `onnx/merged/base/quantized/`): 42498870 bytes, sha256 cc9f3cd6698a369c6008b41aa60aa3fb3322e7f03c9bdf19d8e6b7200afca4f3
- tokenizer.json, config.json, generation_config.json, preprocessor_config.json (from `onnx/merged/base/float/`)

## Desktop check
`SpeechModelTest`: a sentence spoken by Windows' Microsoft David voice (6.0 s, 16 kHz) read back word for word ("Maybe the chorus should go up to the high note then the second verse drops back down") in 0.36 s on this PC's CPU; three seconds of silence read as nothing. A synthetic voice is the easy case.
