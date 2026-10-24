# ONNX Initialization & Session

Depth for the deadlock fix and session build in `SKILL.md`. ONNX C++ API symbols and config are from the
project's ONNX integration, config-verification, and dependency references.

## The static-initialization deadlock

Constructing an `InferenceSession` creates an internal thread pool (and may load execution-provider DLLs).
Spawning a thread while the Windows **loader lock** is held — which it is during `DllMain` and the static
initialization that runs under it — deadlocks:

```
Main thread (holds loader lock)
  -> DllMain(DLL_PROCESS_ATTACH)
       -> static initializer
            -> InferenceSession()
                 -> CreateThreadPool()
                      -> new thread blocks waiting for DllMain to finish   ==> DEADLOCK
```

Official ABI guidance: an `InferenceSession` can't live in a global variable because of its thread pool.
So: **never construct a session (or any ORT object) in a static initializer or `DllMain`.** Defer it to
plugin activation.

## Deferring init: `ORT_API_MANUAL_INIT` vs the C API

**C++ API** — define `ORT_API_MANUAL_INIT` before including the header so the API table isn't grabbed at
static-init time, then call `Ort::InitApi()` when you're safely past `DllMain` (in `setActive`):

```cpp
#define ORT_API_MANUAL_INIT
#include <onnxruntime_cxx_api.h>
#undef ORT_API_MANUAL_INIT
// ... later, on the UI thread in setActive(true):
Ort::InitApi();
Ort::Env env(ORT_LOGGING_LEVEL_WARNING, "plugin");
```

**C API** (`onnxruntime_c_api.h`) — no static init exists; you control exactly when the API and env are
created, which is the safest option for plugins:

```cpp
static const OrtApi* g_ort = nullptr;
static OrtEnv*       g_env = nullptr;
void initializeOnnx() {                         // call in setActive, NOT static init / DllMain
    g_ort = OrtGetApiBase()->GetApi(ORT_API_VERSION);
    g_ort->CreateEnv(ORT_LOGGING_LEVEL_WARNING, "plugin", &g_env);
}
```

Use `std::unique_ptr` members for the `Env`/`Session` so the owning object can be constructed (as a
processor member) without doing any ORT work; the actual build happens in `buildSession()` called from
`setActive`.

## Full `SessionOptions` (single-threaded, predictable)

```cpp
Ort::SessionOptions opts;
opts.SetIntraOpNumThreads(1);                                  // predictable latency; avoids pool churn
opts.SetInterOpNumThreads(1);
opts.SetExecutionMode(ExecutionMode::ORT_SEQUENTIAL);
opts.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);
opts.AddConfigEntry("session.intra_op.allow_spinning", "0");   // stop threads busy-waiting -> lower CPU
```

**Determinism vs latency tradeoff.** `ORT_ENABLE_ALL` is fastest but some graph optimizations reorder
floating-point ops, so results can differ slightly across machines/runs. If you need bit-exact
reproducibility (e.g. for automated regression tests), use instead:

```cpp
opts.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_DISABLE_ALL);
opts.DisablePerSessionThreads();                               // share/avoid per-session thread pool
```

For most audio plugins the slight nondeterminism of `ORT_ENABLE_ALL` is fine; choose `ORT_DISABLE_ALL`
only when reproducibility matters more than speed.

## Loading the model: embedded array vs disk path

**Embedded byte array** (the `xxd` / `file(READ HEX)` header from `vst3-cmake-build`) — no file to locate
at runtime, and nothing for the user to misplace:

```cpp
extern unsigned char g_model[]; extern unsigned int g_model_len;
session_ = std::make_unique<Ort::Session>(env, g_model, g_model_len, opts);   // array overload
```

**Disk path** — on Windows the path is UTF-16 `wchar_t*`; convert from your stored UTF-8/`std::string`:

```cpp
#ifdef _WIN32
    std::wstring wpath(path.begin(), path.end());             // (use a proper UTF-8->UTF-16 conv for non-ASCII)
    session_ = std::make_unique<Ort::Session>(env, wpath.c_str(), opts);
#else
    session_ = std::make_unique<Ort::Session>(env, path.c_str(), opts);
#endif
```

(Resolve the disk path from the plugin's module directory — `GetModuleFileName` on the `hinstDLL` captured
in `DllMain`, same as the resource path in `vstgui` / `vst3-cmake-build`.)

## Timeout-protected loading + file validation

A corrupted model can hang the loader; guard it and sanity-check the file first:

```cpp
if (!std::filesystem::exists(modelPath)) { /* fail: not found */ }
auto sz = std::filesystem::file_size(modelPath);
if (sz < 1024 || sz > 100 * 1024 * 1024) { /* fail: implausible size */ }

auto fut = std::async(std::launch::async, [&]{ return std::make_unique<Ort::Session>(env, modelPath.c_str(), opts); });
if (fut.wait_for(std::chrono::seconds(5)) == std::future_status::timeout)
    throw std::runtime_error("Model load timeout (corrupted?)");
session_ = fut.get();
```

(`buildSession` runs on the UI thread, so this `std::async`/timeout is acceptable here — it is *not* the
audio thread.)

## Validate the model architecture

Fail fast if the model isn't the one you expect (shape mismatches otherwise crash inside `Run`):

```cpp
Ort::AllocatorWithDefaultOptions alloc;
if (session_->GetInputCount() != 1 || session_->GetOutputCount() != 1) { /* architecture mismatch */ }
auto shape = session_->GetInputTypeInfo(0).GetTensorTypeAndShapeInfo().GetShape();
if (shape.size() != 2 || shape[1] != 2048) { /* unexpected input shape */ }
// cache names for the worker:
auto inName  = session_->GetInputNameAllocated(0, alloc);
auto outName = session_->GetOutputNameAllocated(0, alloc);
```

## Warmup inference (so the first real call doesn't allocate)

Trigger ONNX's internal allocations during `setActive`, not in the first `process()`:

```cpp
std::vector<float> zero(2048, 0.0f);
std::vector<int64_t> shape{1, 2048};
auto mem = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
auto t   = Ort::Value::CreateTensor<float>(mem, zero.data(), zero.size(), shape.data(), shape.size());
const char* ins[]  = {"input"};
const char* outs[] = {"output"};
session_->Run(Ort::RunOptions{nullptr}, ins, &t, 1, outs, 1);   // discard result; arena is now warm
```

ONNX still allocates *internally* per inference, which is exactly why `Run` lives on the worker thread, not
the audio thread — warmup minimizes but does not eliminate it.

## Execution-provider dependencies (and ODR)

The default CPU provider needs only the runtime. GPU providers pull extra DLLs that must be findable
(`vst3-cmake-build`):

- **DirectML**: `onnxruntime_providers_dml.dll` loads `DirectML.dll` and creates DirectX 12 resources at
  session creation.
- **CUDA**: `cudart64_*.dll`, `cublas64_*.dll`, `cudnn64_*.dll` on `PATH`; since ONNX Runtime v1.21.0 the
  CUDA libs must be present even when only DirectML is used.

For audio plugins the CPU provider is usually the right choice (no GPU dependency to ship). And heed the
ort-builder warning: static-linking ONNX risks **ODR/symbol collisions** with hosts that load ONNX
themselves — test in every target DAW (`vst3-cmake-build`).
