---
name: vst3-threading-realtime-safety
description: >
  Real-time audio-thread safety and cross-thread communication for VST3 plugins on the Steinberg VST3
  SDK (no JUCE, Win x64). Use whenever the audio thread shares data with the UI thread or a worker: the
  cardinal rule that process() is allocation-free, lock-free, non-blocking, with no I/O and no exceptions;
  the thread-affinity contract (which SDK method runs on which thread); why locks cause priority-inversion
  dropouts; atomic handoff for single scalars and its limits; the lock-free SPSC queue with correct
  acquire/release ordering (this skill owns the queue the architecture, parameters, and ONNX skills
  reference); the dedicated worker-thread pattern for ONNX inference and convolution tails (submit/retrieve
  from the audio thread); processor-controller messaging via IConnectionPoint/IMessage (UI/timer thread
  only, never from process); real-time-safe state reset on transport discontinuity; and denormal flush
  (FTZ/DAZ). Prefer this over guessing memory ordering, thread rules, or where allocation is legal.
---

# VST3 Threading & Realtime Safety (VST3 SDK, no JUCE)

The rules and primitives that keep the audio callback glitch-free, and the lock-free machinery every other
skill leans on. This skill **owns the SPSC queue** that `vst3-plugin-architecture` (DSP seam),
`vst3-parameters-automation` (audio→UI value push), and `onnx-integration` (inference handoff) all
reference; it builds on the processor/controller skeleton from `vst3-plugin-architecture`. Patterns and
the verified queue come from the project's VST3 production, failure-mode, and ONNX references. The
`LockFreeQueue` below is self-contained (no SDK-internal dependency, which is also the safer choice for
closed-source). No GPL; SDK is MIT (3.8.0+).

**The cardinal rule (from the project's VST3 production reference):** the audio thread must be
**allocation-free, lock-free, and non-blocking** — and additionally must do **no I/O and throw no
exceptions**. Things that violate it and cause dropouts or crashes: `new`/`malloc`/`std::vector` resize,
`std::lock_guard`/any mutex, file or console I/O (`std::ofstream`, `printf`, `std::cout`), loading an ONNX
model, sending an `IMessage`, calling `performEdit`, and letting a C++ exception escape. All of these
appear in real plugins as "convenience" code that compiles and runs in REAPER but fails under the
validator and stutters under load.

## The one decision that matters: the audio thread never waits

Every value the audio thread shares with another thread crosses by one of exactly two **wait-free**
mechanisms — never a lock, never a host message:

1. **`std::atomic` scalar** — for one independent value (a single gain, a bypass flag).
2. **A lock-free SPSC queue** — for anything bigger (a struct of correlated parameters, an audio block to
   a worker, meter frames to the GUI).

If you reach for a mutex, a `std::string`, a `shared_ptr`, or `sendMessage()` on the audio thread, stop —
that is the bug. Why a lock is fatal here: the audio thread runs at real-time priority with a hard
deadline (the buffer must be filled before the soundcard underruns). If it blocks on a mutex held by a
lower-priority thread that the OS then preempts, the audio thread misses its deadline and you get an
audible dropout. That is **priority inversion**, and it is why "just a quick lock" is never acceptable in
`process()`.

## The thread map (which SDK method runs where)

| Thread | Methods |
|---|---|
| **UI thread only** | `initialize`, `terminate`, `setupProcessing`, `setActive`, `getState`, `setState`, all `IEditController` methods, all `IPlugView` methods, `getBusInfo`, `activateBus`, `setBusArrangements` |
| **Audio thread only** | `process` |
| **Either thread** | `setProcessing` — lightweight, lock-free operations only |

`setActive(true)` is where allocation is legal (see `vst3-plugin-architecture`); `process()` is where it
is forbidden. `setProcessing()` may be called from either thread, so use it only to flip an atomic flag.

## Atomic handoff (single scalars) — and its hard limit

A single independent value crosses with a plain atomic; `relaxed` ordering is fine because nothing else
depends on its ordering relative to other data:

```cpp
std::atomic<float> sensitivity_{0.5f};
std::atomic<bool>  bypass_{false};
// UI thread:    sensitivity_.store(v, std::memory_order_relaxed);
// Audio thread: float s = sensitivity_.load(std::memory_order_relaxed);
```

**The limit:** separate atomics cannot update *correlated* values atomically. If a filter needs `freq`,
`Q`, and `gain` together, three atomics can be read torn — the audio thread might combine a new `freq`
with an old `Q` for one block, producing a transient artifact or, for some DSP, instability. For any
multi-field update, push the **whole struct** through the SPSC queue (one publish = one consistent
snapshot), or use a double-buffer. Don't paper over correlated state with a pile of atomics.

## The lock-free SPSC queue (the owned primitive)

Single-producer / single-consumer, wait-free, fixed-capacity, no allocation after construction. This is
the verified implementation from the project's ONNX reference; the memory ordering is the point — get it
wrong and you get rare, unreproducible corruption.

```cpp
#include <atomic>
#include <array>
#include <cstddef>

template <typename T, size_t Size>
class LockFreeQueue {                       // SPSC: exactly ONE producer thread, ONE consumer thread
    std::array<T, Size> buffer_;
    std::atomic<size_t> readPos_{0};
    std::atomic<size_t> writePos_{0};
public:
    bool push(const T& item) {              // producer thread only
        const size_t w    = writePos_.load(std::memory_order_relaxed);
        const size_t next = (w + 1) % Size;
        if (next == readPos_.load(std::memory_order_acquire)) return false; // full (one slot reserved)
        buffer_[w] = item;
        writePos_.store(next, std::memory_order_release);                   // publish AFTER the write
        return true;
    }
    bool pop(T& item) {                      // consumer thread only
        const size_t r = readPos_.load(std::memory_order_relaxed);
        if (r == writePos_.load(std::memory_order_acquire)) return false;   // empty
        item = buffer_[r];
        readPos_.store((r + 1) % Size, std::memory_order_release);
        return true;
    }
};
```

Rules that make it safe: **exactly one producer thread and one consumer thread** (it is *not* MPMC); `T`
must be **trivially copyable POD** — no `std::string`, `std::function`, `shared_ptr`, or anything that
allocates or refcounts on copy (those reintroduce allocation/locking on the audio thread). One slot is
deliberately left empty so `full` and `empty` are distinguishable. Capacity, the producer/consumer
direction for each use, and why each `memory_order` is what it is are in
`references/lockfree-queues-and-workers.md`.

## The worker-thread pattern (ONNX inference, convolution tails)

When work is too heavy or non-real-time for the audio thread (ONNX inference heap-allocates; a long
convolution tail is unbounded), run it on a **dedicated `std::thread`** and exchange data through two SPSC
queues. The audio thread only `push`es input and `pop`s results — both wait-free. This is the structure
`onnx-integration` plugs the actual session into.

```cpp
class AsyncInferenceEngine {
    struct AudioBlock { std::array<float, 2048> samples; size_t numSamples; int64_t timestamp; };
    LockFreeQueue<AudioBlock, 16> inputQueue_;
    LockFreeQueue<AudioBlock, 16> outputQueue_;
    std::thread        worker_;
    std::atomic<bool>  running_{false};
public:
    void start() { running_ = true; worker_ = std::thread(&AsyncInferenceEngine::loop, this); }
    void stop()  { running_ = false; if (worker_.joinable()) worker_.join(); }   // call from setActive(false)

    bool submitAudio(const float* x, size_t n, int64_t ts) {           // audio thread, lock-free
        AudioBlock b; b.numSamples = std::min(n, b.samples.size());
        std::copy_n(x, b.numSamples, b.samples.begin()); b.timestamp = ts;
        return inputQueue_.push(b);                                    // false => drop / fall back to dry
    }
    bool retrieveResults(float* out, size_t maxN, int64_t& ts) {       // audio thread, lock-free
        AudioBlock b; if (!outputQueue_.pop(b)) return false;
        std::copy_n(b.samples.begin(), std::min(maxN, b.numSamples), out); ts = b.timestamp; return true;
    }
private:
    void loop() {                                                     // worker: BLOCKING work is OK here
        while (running_.load(std::memory_order_relaxed)) {
            AudioBlock in;
            if (inputQueue_.pop(in)) { /* runInference(in) -> push to outputQueue_ */ }
            else { /* brief sleep/yield to avoid a busy spin */ }
        }
    }
};
```

`start()`/`stop()` are tied to `setActive(true/false)` (the worker is joined on suspend so no thread
outlives the active span). Async inference is **not instantaneous** — results return a block or more
later, so this adds latency you must either report via `getLatencySamples()` or design around (e.g. run a
cheap DSP path live and blend the model result when it arrives). The ONNX session construction itself
(thread-pool, `ORT_API_MANUAL_INIT`, `SetIntraOpNumThreads(1)`) belongs to `onnx-integration`; this skill
owns only the threading scaffold around it. The convolution-tail worker variant is in the reference.

## Processor ↔ controller messaging (`IConnectionPoint` / `IMessage`)

For occasional **structured** data between processor and controller (not per-block audio), VST3 provides
`IConnectionPoint`/`IMessage`. It is routed by the host and is **UI/timer-thread work — never call
`sendMessage()` from `process()`**.

```cpp
// Sender (UI thread, or a worker — NOT process()):
if (auto* msg = allocateMessage()) {
    msg->setMessageID("WaveformData");
    msg->getAttributes()->setBinary("data", ptr, size);   // IAttributeList: setInt/setFloat/setString too
    sendMessage(msg);
}
// Receiver: override notify(IMessage* m); dispatch on m->getMessageID(); read m->getAttributes().
```

Use messages for things like "controller asks processor for a spectrum snapshot" or pushing a model path.
For **continuous** audio→UI data (meters, waveform, spectrum frames) use the **SPSC queue** drained by a
`CVSTGUITimer` on the UI thread, not messages — messages allocate and are far too slow per block. And for
pushing a *parameter* value from the audio thread to the host, never `performEdit` in `process()`: enqueue
`(id, value)` and have the UI-thread timer issue `beginEdit`/`performEdit`/`endEdit`
(`vst3-parameters-automation`).

## Real-time-safe state reset, denormals, exceptions

- **Reset without allocating.** On a transport discontinuity (jump/seek/loop-wrap — detection is in
  `vst3-plugin-architecture`), reset DSP by **clearing pre-allocated buffers in place** (`std::fill` /
  `memset`) and zeroing state — never reallocate, never lock. Buffers were sized in `setActive(true)`.
- **Flush denormals.** Set FTZ/DAZ at the top of `process()`; a denormal cascade can spike CPU ~100× and
  cause the very dropout you are avoiding:
  ```cpp
  #include <xmmintrin.h>   // _MM_SET_FLUSH_ZERO_MODE
  #include <pmmintrin.h>   // _MM_SET_DENORMALS_ZERO_MODE
  _MM_SET_FLUSH_ZERO_MODE(_MM_FLUSH_ZERO_ON);
  _MM_SET_DENORMALS_ZERO_MODE(_MM_DENORMALS_ZERO_ON);
  ```
- **Never throw across the SDK boundary.** Return `tresult` codes; catch exceptions in the worker, not on
  the audio thread. An exception unwinding through `process()` or an SDK callback is undefined behavior in
  many hosts.

## Sibling skills

- `vst3-plugin-architecture` — the processor/controller skeleton, `setActive` allocation/worker-start
  point, the `process()` guard, transport-discontinuity detection, and latency reporting.
- `vst3-parameters-automation` — the audio→UI parameter-push (queue + timer + begin/perform/end) and the
  smoothing that absorbs block-rate targets.
- `onnx-integration` — the ONNX session specifics (`ORT_API_MANUAL_INIT`, static-init deadlock, allocator)
  that plug into the worker scaffold above.
- DSP skills (`audio-reverb` convolution tail, `audio-spectral-fft` analyzer feed, `audio-metering`) — all
  hand data across threads with the queue here.

## References (read when relevant)

- `references/lockfree-queues-and-workers.md` — The SPSC queue in depth: the memory-ordering rationale
  line by line, capacity/sizing, the strict single-producer/single-consumer and trivially-copyable-`T`
  constraints (and what breaks if you violate them), the full worker engine with `start`/`stop`/`join`
  lifecycle bound to `setActive`, double-buffering for block-aligned results and the latency it implies,
  the convolution-tail worker variant, and the audio→UI/host push pattern (SPSC + `CVSTGUITimer` +
  `beginEdit`/`performEdit`/`endEdit`). Read this when wiring ONNX inference, a convolution tail, or any
  meter/analyzer feed to the GUI.
- `references/thread-affinity-and-messaging.md` — The complete SDK thread-affinity contract (every method
  and its thread), the priority-inversion explanation in full, the `IConnectionPoint`/`IMessage` API
  (`connect`/`notify`, `allocateMessage`/`sendMessage`, `IMessage::setMessageID`/`getAttributes`,
  `IAttributeList` setters) with the UI/timer-thread-only rule, the `RTTransferT` alternative for
  transferring a whole state object across threads, denormal (FTZ/DAZ) details, and exception-safety
  across the SDK boundary. Read this for processor↔controller messaging or when auditing which code runs
  on which thread.
