---
name: onnx-integration
description: >
  ONNX Runtime integration for VST3 plugins on the Steinberg VST3 SDK (no JUCE, Win x64). Use when running
  a neural model (pitch-to-MIDI, denoise, separation) inside a plugin: why an InferenceSession deadlocks if
  built in a static initializer or DllMain (loader lock plus ONNX's thread pool) and the ORT_API_MANUAL_INIT
  / Ort::InitApi fix, building the Ort::Session once on the UI thread in setActive (not thread-safe) with
  single-threaded SessionOptions (SetIntraOpNumThreads one, ORT_SEQUENTIAL, allow_spinning off), loading the
  model from an embedded byte array or disk with timeout protection and warmup, running inference only on a
  dedicated worker thread via the lock-free handoff (never in the audio callback) because it heap-allocates,
  allocator/memory config, and sourcing a permissively-licensed model where the WEIGHTS license (not just
  the code) permits commercial use (Basic Pitch Apache 2.0, CREPE MIT; avoid MusicGen/RAVE/EnCodec). Prefer
  this over guessing the ONNX API, init timing, or model licensing.
---

# ONNX Runtime Integration (VST3 SDK, no JUCE)

Running a neural model inside a VST3 plugin, without deadlocking the host loader, blocking the audio
thread, or shipping weights you have no license to sell. ONNX Runtime gives broad model support but is
hostile to two things VST3 cares about: the **Windows DLL loader** (its session construction spawns a
thread that deadlocks against the loader lock) and the **real-time audio thread** (inference heap-allocates
every call). This skill plugs the session into the worker scaffold owned by
`vst3-threading-realtime-safety`, is linked and shipped by `vst3-cmake-build`, and emits results as MIDI via
`vst3-midi-events`. Every ONNX API symbol and config here is from the project's ONNX integration,
config-verification, and dependency references. **ONNX Runtime is MIT** (commercial use fine, with an
attribution notice) — but a model's **weights** carry their own license, separate from its code. No GPL is
used (the NeuralNote plugin is GPL — read it for reference only, copy nothing).

Things easy to get wrong, each pinned below: **constructing an `InferenceSession` in a global/static or
`DllMain`** (deadlock — its internal thread pool waits on the loader lock), **building the session on the
audio thread or sharing one session across threads** (not thread-safe), **calling `Run()` in `process()`**
(it allocates — instant real-time violation), **skipping warmup** so the first real inference allocates
mid-callback, and **shipping a model whose code is MIT/Apache but whose weights are non-commercial**
(MusicGen, RAVE, EnCodec — a license violation that looks fine at a glance).

## The one decision that matters: is ONNX even the right tool — and if so, off the audio thread always

Two-part. **First, should this be ONNX at all?** ONNX Runtime is built for *non-real-time or heavier*
inference — transcription, source separation, classification, pitch-to-MIDI with buffering. For a
sub-millisecond real-time effect (a small recurrent net modeling an amp, a tiny denoiser), **RTNeural
(BSD-3)** or a hand-coded network is the right tool, not ONNX — most real-time ML audio plugins use
RTNeural for exactly this reason. Reach for ONNX when the model is large or the latency budget is tens of
milliseconds, not microseconds.

**Second, if ONNX: the lifecycle is fixed and non-negotiable** — build the session **once**, on the **UI
thread**, in `setActive(true)` (deferred, with manual init), and run it **only on the dedicated worker
thread** from `vst3-threading-realtime-safety`, **never** in `process()`. Everything below is that
lifecycle.

## The static-initialization deadlock, and the fix

Creating an `InferenceSession` builds an internal **thread pool**; spawning a thread while the **loader
lock** is held (during `DllMain`/static initialization) deadlocks — the new thread waits for `DllMain` to
finish, which is waiting for the session to construct:

```
DllMain(DLL_PROCESS_ATTACH)  [holds loader lock]
  -> static initializer -> InferenceSession() -> CreateThreadPool() -> new thread waits on DllMain  => DEADLOCK
```

The official guidance is blunt: you can't put an `InferenceSession` in a global variable because of its
thread pool. The fix is to **defer all ONNX init** out of static/`DllMain` scope. For the C++ API, define
`ORT_API_MANUAL_INIT`, then call `Ort::InitApi()` when safe (in `setActive`):

```cpp
#define ORT_API_MANUAL_INIT
#include <onnxruntime_cxx_api.h>
#undef ORT_API_MANUAL_INIT

class OnnxEngine {
    std::unique_ptr<Ort::Env>     env_;
    std::unique_ptr<Ort::Session> session_;
public:
    OnnxEngine() = default;                 // ctor does NO ORT work — safe as a member
    void buildSession(const std::wstring& modelPath);   // called from setActive (UI thread)
};
```

The C API (`onnxruntime_c_api.h`) has no static init at all — `OrtGetApiBase()->GetApi(...)` and
`CreateEnv` happen only when you call them — and is the safest option for plugins. Either way: **no ORT
work in `DllMain` or a static initializer**, ever. (The DLL-search side of this — finding
`onnxruntime.dll` at all — is in `vst3-cmake-build`.)

## Building the session (UI thread, in `setActive`, single-threaded)

Session construction is **not thread-safe** and is heavy, so it belongs in `setActive(true)` (or a worker
started there), never in `process()` and never in `initialize()` (scan budget — `vst3-plugin-architecture`,
the PitchSprite lesson). Configure for predictable latency: single-threaded, sequential, no spinning.

```cpp
void OnnxEngine::buildSession(const std::wstring& modelPath) {
    Ort::InitApi();                                    // required under ORT_API_MANUAL_INIT
    env_ = std::make_unique<Ort::Env>(ORT_LOGGING_LEVEL_WARNING, "plugin");

    Ort::SessionOptions opts;
    opts.SetIntraOpNumThreads(1);                      // one thread -> predictable latency, fewer pool issues
    opts.SetInterOpNumThreads(1);
    opts.SetExecutionMode(ExecutionMode::ORT_SEQUENTIAL);
    opts.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);
    opts.AddConfigEntry("session.intra_op.allow_spinning", "0");  // don't burn CPU spinning

    session_ = std::make_unique<Ort::Session>(*env_, modelPath.c_str(), opts);
    // then: validate input/output shapes, and run a warmup inference (see reference)
}
```

`ORT_ENABLE_ALL` maximizes speed; if you need bit-exact determinism across machines, drop to
`ORT_DISABLE_ALL` and `DisablePerSessionThreads()` (some graph optimizations reorder ops). Load from an
**embedded byte array** (the `xxd`/`file(READ HEX)` header from `vst3-cmake-build`) with the array
overload, or from a **disk path** (UTF-16 `wchar_t*` on Windows). Wrap the load in a **timeout** (a
corrupted model can hang) and **validate the architecture** (input/output counts and shapes) before use.
The array overload, timeout pattern, shape validation, and warmup are in
`references/onnx-init-and-session.md`.

## Inference runs on the worker, never in `process()`

`Run()` allocates internally, so it cannot touch the audio thread. The audio thread only `submit`s input
and `retrieve`s results through the lock-free SPSC queues from `vst3-threading-realtime-safety`; the
worker thread calls `Run()`:

```cpp
// process() (audio thread) — lock-free only:
float* in = data.inputs[0].channelBuffers32[0];
asyncEngine_.submitAudio(in, data.numSamples, data.processContext ? data.processContext->projectTimeSamples : 0);
float out[2048]; int64_t ts;
if (asyncEngine_.retrieveResults(out, 2048, ts))
    emitMidiFromModelOutput(out, data.outputEvents);   // see vst3-midi-events

// worker loop (dedicated thread) — Run() and its allocations are fine here:
//   pop input block -> CreateTensor over a pre-allocated buffer -> session_->Run(...) -> push result
```

The worker's `start()`/`stop()` are bound to `setActive(true/false)` (joined on suspend). Because results
return **late** (a block or more after submit), report the turnaround as latency via `getLatencySamples()`
or blend a live path until the result arrives (`vst3-threading-realtime-safety`). The exact worker `Run()`
call (with `Ort::Value::CreateTensor`, input/output names, and `MemoryInfo`) and the output-to-MIDI
conversion are in `references/inference-handoff-and-models.md`.

## Allocator and memory

ONNX manages its own arena; you keep your I/O buffers pre-allocated and warm the arena once so the first
real inference doesn't allocate mid-stream:

- `Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault)` for tensor memory info.
- `Ort::AllocatorWithDefaultOptions` to query input/output names (`GetInputNameAllocated`).
- **Warmup**: in `setActive`, run one inference on a zero tensor to trigger ONNX's internal allocations
  *then*, not in the first `process()`. Pre-allocate the input/output `std::vector<float>` to the model's
  fixed frame size.

Details and the arena config are in `references/onnx-init-and-session.md`.

## Model sourcing and licensing (weights, not just code)

The trap: a model's **code** license and its **weights** license can differ, and only the *weights* matter
when you ship them in a product. MusicGen (MIT code), RAVE (CC-BY-NC), EnCodec (MIT code), Open-Unmix
`umxl`, and most Silero TTS advertise permissive code but carry **non-commercial weights** — distributing
them in a paid plugin violates the license. Always read the actual `LICENSE`/model card, not repo metadata.

Commercially-safe, ONNX-ready picks: **Basic-Pitch** (Apache 2.0, ~20–30 MB, native ONNX, polyphonic
pitch-to-MIDI), **CREPE / CREPE-tiny** (MIT, monophonic pitch, ~3 MB tiny via onnxcrepe), **DeepFilterNet**
(MIT/Apache, denoise), **RNNoise** (BSD, noise suppression), **Demucs v4** (MIT, separation, needs custom
ONNX export), **Vocos** (MIT, vocoder), **Whisper** (MIT, ASR). Attribution is required (MIT/BSD: copyright
+ license text; Apache 2.0: + NOTICE + state changes, includes a patent grant) — ship a `LICENSES.txt` in
`Contents/Resources/`. The full model tables, the avoid-list, Hugging Face license filtering, `optimum-cli`
conversion, and the attribution template are in `references/inference-handoff-and-models.md`.

## Sibling skills

- `vst3-threading-realtime-safety` — owns the SPSC queues and the worker engine this skill runs `Run()`
  inside; the audio-thread rules; the latency of async inference.
- `vst3-cmake-build` — linking ONNX (static vs app-local DLL), the DLL-search fix, embedding the model.
- `vst3-plugin-architecture` — `setActive` as the build point, `initialize` scan budget, latency reporting.
- `vst3-midi-events` — converting model output (pitch/onset) into `IEventList` note on/off.

## References (read when relevant)

- `references/onnx-init-and-session.md` — The static-init deadlock mechanism in full (loader lock + thread
  pool), `ORT_API_MANUAL_INIT`/`Ort::InitApi` vs the C API (`OrtGetApiBase`/`CreateEnv`), the
  deferred-to-`setActive` lifecycle, the complete `SessionOptions` config with the **determinism-vs-latency**
  tradeoff (`DisablePerSessionThreads`, `ORT_DISABLE_ALL`), loading from an embedded array vs a disk path
  (Windows `wchar_t*`), **timeout-protected loading** and **model-architecture validation**
  (`GetInputCount`/`GetShape`), **warmup inference**, the arena/allocator config, and execution-provider
  dependencies (DirectML/CUDA DLLs) with the ODR warning. Read this when building or debugging the session.
- `references/inference-handoff-and-models.md` — The worker-thread inference loop (the exact `Run()` call
  with `Ort::Value::CreateTensor`, input/output names, `MemoryInfo`, pre-allocated tensors), the
  `process()`-side submit/retrieve and output-to-MIDI conversion, latency reporting for async results, and
  the full **model-sourcing-and-licensing** guide — the code-vs-weights trap, commercially-safe model tables
  by size/use-case, the non-commercial-weights avoid-list, Hugging Face filtering / `onnx-community` /
  `optimum-cli`, RTNeural as the real-time alternative, and the attribution template + `LICENSES.txt`
  placement. Read this when wiring inference to MIDI or choosing a model.
