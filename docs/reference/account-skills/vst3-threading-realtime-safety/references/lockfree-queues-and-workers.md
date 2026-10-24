# Lock-Free Queues & Worker Threads

Depth for the SPSC queue and worker pattern in `SKILL.md`. The queue and the inference engine are the
verified implementations from the project's ONNX and VST3 production references; the memory ordering is
the part that must be exact.

## Why each `memory_order` is what it is

The SPSC queue is correct because the two atomics fence the data writes around the index publish:

- **Producer `push`:** `writePos_.load(relaxed)` — only this thread writes `writePos_`, so it needs no
  synchronization to read its own value. `readPos_.load(acquire)` — must see the consumer's latest read
  position so we don't overwrite an unread slot; `acquire` pairs with the consumer's `release` store of
  `readPos_`. The data write `buffer_[w] = item` happens **before** `writePos_.store(next, release)`; the
  `release` guarantees the consumer that later does an `acquire` load of `writePos_` will see the
  completed data write. **Publish the index only after the payload is written** — reversing those two
  lines is the classic bug.
- **Consumer `pop`:** mirror image. `readPos_.load(relaxed)` (own), `writePos_.load(acquire)` (pairs with
  producer's `release`), read `buffer_[r]` **before** `readPos_.store(release)` so the producer can't
  reuse the slot until the read completes.

`relaxed` on the owning side is safe because there is exactly one writer of each index. If you ever made
it multi-producer or multi-consumer, this all breaks — see the constraints below.

## Capacity and sizing

`% Size` works for any `Size`; if you want to drop the modulo for speed, make `Size` a power of two and
mask with `& (Size-1)`. One slot is always reserved (the queue holds `Size-1` items) so `writePos ==
readPos` unambiguously means *empty*. Size for the worst case: enough slots that a momentary scheduling
hiccup on the consumer doesn't make the producer's `push` fail. For audio→worker, a handful of blocks
(8–16) is typical; for meter→UI, size for the UI refresh interval (a 30 Hz timer draining a 1 kHz
producer needs ~34+ slots, round up).

## The two hard constraints (violating either = corruption or a hidden allocation)

1. **Exactly one producer thread and one consumer thread.** This is SPSC, not MPMC. Two audio threads, or
   the audio thread *and* a worker both pushing, will corrupt the indices. If you need fan-in/fan-out, use
   one queue per producer/consumer pair, not a shared queue.
2. **`T` must be trivially copyable POD.** `buffer_[w] = item` is a plain copy on the audio thread; if `T`
   contains `std::string`, `std::vector`, `std::function`, `shared_ptr`, or any type whose copy/destroy
   allocates or refcounts, you have reintroduced allocation/locking on the audio thread — the exact thing
   the queue exists to avoid. Pass fixed-size arrays, PODs, and indices/handles instead. To hand over a
   large buffer, pre-allocate a pool and pass an **index** into it, not the buffer by value.

## The worker engine, full lifecycle

Bind the worker's life to the processor's active span so no thread outlives it (and so `setActive(false)`
is a clean join point, not a leak):

```cpp
// In the processor:
tresult PLUGIN_API setActive(TBool state) override {
    if (state) { engine_.start(); }      // spawn worker; session already built (onnx-integration)
    else       { engine_.stop(); }       // signal + join — worker gone before we return
    return AudioEffect::setActive(state);
}

tresult PLUGIN_API process(ProcessData& data) override {
    // ... guard + FTZ/DAZ ...
    engine_.submitAudio(in[0], data.numSamples, data.processContext ?
                        data.processContext->projectTimeSamples : 0);   // lock-free, may return false
    int64_t ts;
    if (!engine_.retrieveResults(out[0], data.numSamples, ts))
        /* no result yet: output the live/dry path so we never stall */;
    return kResultOk;
}
```

`stop()` sets `running_ = false` and `join()`s; never `detach()` a worker that touches plugin memory, or
it can run after the processor is destroyed. The worker loop should `yield`/short-`sleep` when the input
queue is empty rather than busy-spinning a core.

## Double-buffering and the latency it implies

Async inference returns results **late** — the block you submitted at time *t* comes back some blocks
later. Two ways to handle it:

- **Report the latency.** If the model runs on fixed-size frames with a known turnaround, return that
  delay from `getLatencySamples()` and let the host delay-compensate (`vst3-plugin-architecture`).
- **Blend live + late.** Run a cheap deterministic DSP path on the audio thread for zero-latency output,
  and crossfade in the model's result when it arrives (good for "enhancement" effects where the live path
  is already acceptable). Keep a small **double buffer** so the UI/late result and the live path don't
  tear.

Either way, label the latency honestly; silent async latency causes phase smear when the host runs the
track against others.

## Convolution-tail worker variant

A long impulse response (reverb, cab IR) has a tail longer than one block. The real-time-safe structure
(detail in `audio-reverb`): partition the IR; convolve the **first few partitions live** on the audio
thread (bounded, deterministic work via the FFT convolver in `audio-spectral-fft`), and compute the
**later partitions on a worker**, handing their output back through an SPSC queue to be summed into the
output a known number of blocks later. The threading scaffold is identical to the inference engine — two
SPSC queues, a `std::thread` whose life is bound to `setActive` — only the work in `loop()` differs. The
fixed block-offset between "submit" and "sum back" is reported as latency.

## Audio → UI / host push (meters, waveform, and parameter feedback)

For continuous data to the GUI, the producer is the audio thread and the consumer is a UI-thread timer:

```cpp
// shared: LockFreeQueue<MeterFrame, 64> meterQueue_;   MeterFrame is POD
// audio thread (process):     meterQueue_.push(frame);          // lock-free, drop if full
// UI thread (CVSTGUITimer):   MeterFrame f; while (meterQueue_.pop(f)) updateMeters(f);
```

The `CVSTGUITimer` (UI thread; `vstgui` skill) drains every frame and repaints. For pushing a *parameter*
value the audio thread computed (e.g. an envelope follower the user can see), enqueue `(ParamID, value)`
and have the same timer issue `beginEdit`/`performEdit`/`endEdit` — the gesture protocol and thread rule
are in `vst3-parameters-automation`. Never call `performEdit` or `sendMessage` from `process()`.
