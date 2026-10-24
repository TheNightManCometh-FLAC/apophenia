# Thread Affinity & Messaging

Depth for the thread map and messaging in `SKILL.md`. The affinity contract and the messaging API are from
the project's VST3 production and Advanced references.

## Complete thread-affinity contract

The host guarantees these calling threads; respecting them is what makes the lock-free design valid.

| Method | Thread | Notes |
|---|---|---|
| `initialize` / `terminate` | UI | once at load / unload; `initialize` must be fast (scan budget) |
| `setupProcessing` | UI | cache `sampleRate` / `maxSamplesPerBlock`; coefficient prep only |
| `setActive` | UI | allocate/free audio buffers; start/stop worker threads HERE |
| `getState` / `setState` | UI | serialization |
| all `IEditController` (`setParamNormalized`, `setComponentState`, `getParameterInfo`, `getParamStringByValue`, …) | UI | parameter + controller logic |
| all `IPlugView` (`attached`, `removed`, `getSize`, `onSize`, …) | UI | editor lifecycle |
| `getBusInfo`, `activateBus`, `setBusArrangements` | UI | bus config/negotiation |
| `notify` (`IConnectionPoint`) | UI | message receipt is marshaled to the UI thread |
| `setProcessing` | **either** | flip an atomic flag only; no allocation, no locks |
| `process` | **audio** | real-time; the forbidden-ops list in `SKILL.md` applies |

So: the only method on the audio thread is `process()`. Everything that allocates, serializes, or touches
the GUI is on the UI thread. The two communicate solely through atomics and the SPSC queue
(`references/lockfree-queues-and-workers.md`), plus the occasional `IMessage` (below).

## Priority inversion, in full

The audio thread is scheduled at real-time/high priority with a hard deadline: it must fill the output
buffer before the audio device underruns (a few milliseconds at typical buffer sizes). A mutex is a
*blocking* primitive — if the audio thread calls `lock()` on a mutex currently held by a normal-priority
worker, and the OS scheduler then runs some other thread instead of that worker, the audio thread sits
blocked past its deadline → an audible click or dropout. The worker holding the lock is "inverting" the
audio thread's priority. No amount of "the lock is only held briefly" makes this safe, because the *worker*
can be preempted at any instant while holding it.

A non-blocking `try_lock()` is technically safe (it returns immediately on failure) but means you
sometimes skip the update — at which point a lock-free queue or atomic is simpler and strictly better. The
conclusion is the same: **no blocking synchronization on the audio thread, ever.** Use atomics for scalars
and the SPSC queue for everything else.

## `IConnectionPoint` / `IMessage` (structured processor ↔ controller data)

For occasional structured data that isn't per-block audio — a model path, a "send me a spectrum snapshot"
request, a preset blob too large for a parameter — VST3 connects processor and controller through
`IConnectionPoint` and carries data in `IMessage`. The helper bases (`AudioEffect`, `EditController`)
provide `allocateMessage()` and `sendMessage()`; the host routes delivery to `notify()` on the **UI
thread**.

```cpp
// IConnectionPoint:  connect(IConnectionPoint* other);  disconnect(...);  notify(IMessage* msg);
// (the host connects the two components; with the helper bases this is wired for you)

// Send (UI thread, or a worker — NEVER from process()):
if (IMessage* msg = allocateMessage()) {
    msg->setMessageID("ModelPath");
    IAttributeList* a = msg->getAttributes();
    a->setString("path", reinterpret_cast<const TChar*>(pathUtf16));
    // a->setInt("len", n);  a->setFloat("x", f);  a->setBinary("blob", ptr, size);
    sendMessage(msg);
}

// Receive (override on the peer; called on the UI thread):
tresult PLUGIN_API notify(IMessage* msg) override {
    if (!msg) return kInvalidArgument;
    if (FIDStringsEqual(msg->getMessageID(), "ModelPath")) {
        const TChar* p = nullptr;
        if (msg->getAttributes()->getString("path", buf, sizeof(buf)) == kResultOk) { /* use p */ }
        return kResultOk;
    }
    return ConnectionPoint::notify(msg);   // or EditController::notify / base as appropriate
}
```

`IAttributeList` carries typed entries: `setInt`/`getInt`, `setFloat`/`getFloat`, `setString`/`getString`,
`setBinary`/`getBinary`. **Do not** use messaging for continuous data — it allocates and is far too slow
per block; meters/waveform/spectrum go through the SPSC queue drained by a `CVSTGUITimer`. And never call
`sendMessage()` from `process()` (it is not lock-free).

## `RTTransferT` — moving a whole state object across threads

When you need to hand a *whole object* (a full DSP parameter set, a rebuilt coefficient table) from the UI
thread to the audio thread without locks, the SDK's `RTTransferT` (project Advanced reference) provides a
lock-free single-object transfer: the UI thread builds the object and transfers ownership; the audio
thread accesses the latest transferred object inside a callback.

```cpp
using RTTransfer = RTTransferT<StateModel>;
RTTransfer stateTransfer_;
// UI thread:
auto model = std::make_unique<StateModel>();   // build off the audio thread
model->gain = newGain; /* ... */
stateTransfer_.transferObject_ui(std::move(model));
// Audio thread (inside process):
stateTransfer_.accessTransferObject_rt([this](const StateModel& m){ applyConsistentSnapshot(m); });
```

This is the clean answer to the "correlated multi-field update" limit from `SKILL.md`: one transfer = one
consistent snapshot, no torn reads, no locks. Confirm the exact `RTTransferT` method names against your
installed SDK headers before relying on them.

## Denormals (FTZ/DAZ) and exception safety

- **Denormals** are subnormal floats that some CPUs process ~100× slower; a feedback path decaying to
  silence can fill with them and spike CPU into a dropout. Set flush-to-zero / denormals-are-zero on the
  audio thread (the MXCSR control register is per-thread), at the top of `process()`:
  ```cpp
  #include <xmmintrin.h>
  #include <pmmintrin.h>
  _MM_SET_FLUSH_ZERO_MODE(_MM_FLUSH_ZERO_ON);
  _MM_SET_DENORMALS_ZERO_MODE(_MM_DENORMALS_ZERO_ON);
  ```
  This is an audio-thread *safety* measure, not just an optimization. (Belt-and-suspenders: a tiny DC
  offset or `+1e-20` injection in feedback loops also prevents denormals; see the DSP skills.)
- **Exceptions must not cross the SDK boundary.** SDK methods return `tresult`; an exception unwinding
  through `process()` or any SDK callback is undefined behavior in many hosts and can take the DAW down.
  Catch in the worker thread (where blocking and `try`/`catch` are fine), convert failures to `tresult`
  codes or a fallback path, and keep the audio thread `noexcept` in spirit.
