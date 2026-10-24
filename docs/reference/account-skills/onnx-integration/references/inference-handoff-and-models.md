# Inference Handoff & Model Sourcing

Depth for running inference off the audio thread and choosing a model, summarized in `SKILL.md`. The ONNX
API and the model/license tables are from the project's ONNX integration and permissively-licensed-models
references.

## The worker-thread inference loop

`Run()` allocates internally, so it executes on the dedicated worker thread from
`vst3-threading-realtime-safety`, never in `process()`. Names and `MemoryInfo` are cached at session build;
the worker pops an input block, wraps a pre-allocated buffer in an `Ort::Value`, runs, and pushes the
result.

```cpp
// member state (built in setActive): session_, env_, cached names, memInfo_, pre-allocated buffers
void OnnxEngine::inferenceLoop() {                       // dedicated std::thread; blocking work is fine here
    const char* inNames[]  = { inName_.get() };          // cached Allocated names (see init reference)
    const char* outNames[] = { outName_.get() };
    auto memInfo = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);

    while (running_.load(std::memory_order_relaxed)) {
        AudioBlock in;
        if (!inputQueue_.pop(in)) { std::this_thread::yield(); continue; }

        std::vector<int64_t> shape{1, (int64_t)in.numSamples};
        Ort::Value input = Ort::Value::CreateTensor<float>(
            memInfo, in.samples.data(), in.numSamples, shape.data(), shape.size());

        auto outputs = session_->Run(Ort::RunOptions{nullptr}, inNames, &input, 1, outNames, 1);

        AudioBlock result; result.timestamp = in.timestamp;
        const float* y = outputs[0].GetTensorData<float>();
        const size_t n = std::min(result.samples.size(),
            (size_t)outputs[0].GetTensorTypeAndShapeInfo().GetElementCount());
        std::copy_n(y, n, result.samples.begin()); result.numSamples = n;
        outputQueue_.push(result);                        // back to the audio thread, lock-free
    }
}
```

`outputs[0].GetTensorData<float>()` points into ORT-owned memory valid until the next `Run`; copy it out
before the next iteration (the `std::copy_n` above). The queues, `AudioBlock`, and `start`/`stop` (bound to
`setActive`) are all from `vst3-threading-realtime-safety`.

## `process()` side: submit, retrieve, emit MIDI

```cpp
tresult PLUGIN_API process(ProcessData& data) {
    // ... FTZ/DAZ + null-buffer guard (vst3-plugin-architecture) ...
    float* in = data.inputs[0].channelBuffers32[0];
    asyncEngine_.submitAudio(in, data.numSamples,
        data.processContext ? data.processContext->projectTimeSamples : 0);   // lock-free; false = drop

    float out[2048]; int64_t ts;
    if (asyncEngine_.retrieveResults(out, 2048, ts))
        emitMidiFromModelOutput(out, ts, data.outputEvents);                  // vst3-midi-events
    return kResultOk;
}
```

`emitMidiFromModelOutput` turns the model's pitch/onset/offset output into `Event` note-on/note-off and
adds them to `data.outputEvents` (`IEventList`) at the right `sampleOffset` — that conversion and the
musical-correctness rules are in `vst3-midi-events`.

## Latency of async inference

Results return **late** — the block submitted at time *t* comes back one or more blocks later (Basic-Pitch,
for instance, is ~120 ms near-real-time with buffering). Handle it honestly:

- Report the fixed turnaround via `getLatencySamples()` so the host delay-compensates
  (`vst3-plugin-architecture`), or
- run a cheap deterministic path live and blend the model result when it arrives.

Never pretend inference is instantaneous — unreported latency smears timing against other tracks.

## Pre-allocated tensors

Keep the input/output `std::vector<float>` sized to the model's fixed frame and reuse them every inference
(as above) rather than allocating per call. For tighter control, ONNX's `IoBinding` lets you bind fixed
input/output buffers to the session so `Run` writes into your memory; it's an optimization, not required
for correctness, and still runs on the worker.

## Model sourcing: weights license, not just code license

The decisive rule: when you **ship** a model, only the **weights** license governs you, and it often
differs from the code license. Verify the actual `LICENSE`/model card — repo metadata lies.

**Commercially safe, ONNX-ready (weights permissive):**

| Size | Model | License | Use |
|---|---|---|---|
| <5 MB | CREPE-tiny (onnxcrepe) | MIT | monophonic pitch (<10 ms) |
| <5 MB | Silero VAD | MIT | voice activity |
| <5 MB | RNNoise | BSD | noise suppression |
| <25 MB | Basic-Pitch (Spotify) | Apache 2.0 | polyphonic pitch-to-MIDI (native ONNX) |
| <25 MB | DeepFilterNet | MIT/Apache | real-time denoise |
| <25 MB | Vocos | MIT | fast CPU vocoder |
| <25 MB | YAMNet | Apache 2.0 | audio classification |
| <100 MB | CREPE-full | MIT | best monophonic pitch |
| <100 MB | Demucs v4 | MIT | source separation (custom ONNX export) |
| <100 MB | Whisper tiny | MIT | speech transcription |
| <100 MB | PANNs CNN14 | MIT | audio tagging |

**Avoid for commercial products — permissive code but NON-commercial weights:**

| Model | Code | Weights | Issue |
|---|---|---|---|
| MusicGen | MIT | CC-BY-NC 4.0 | weights restricted |
| RAVE | CC-BY-NC | CC-BY-NC | non-commercial |
| AudioLDM / AudioLDM2 | Apache 2.0 | CC-BY-NC-SA 4.0 | weights restricted |
| EnCodec | MIT | CC-BY-NC 4.0 | weights restricted |
| Open-Unmix umxl | MIT | CC-BY-NC-SA 4.0 | weights restricted (umxhq is MIT, OK) |
| Silero TTS (most) | MIT | CC-BY-NC 4.0 | weights restricted |
| madmom (models) | BSD (code) | CC-BY-NC-SA | models restricted (code OK) |
| Facebook Denoiser | MIT | CC-BY-NC 4.0 | non-commercial |

For restricted models, the legal path is to **train your own** weights on permissively-licensed code.

## Finding and converting models

- **Hugging Face license filter**: `huggingface.co/models?license=license:apache-2.0&pipeline_tag=audio-...`
  (and `license:mit`); **`huggingface.co/onnx-community`** for pre-converted ONNX.
- **`optimum-cli export onnx --model [name] [out-dir]`** to convert; `torch.onnx.export()` preserves the
  source model's license.
- **`sherpa-onnx`** (speech), **ONNX Runtime Extensions** (audio preprocessing ops).
- **RTNeural (BSD-3)** — not ONNX, but the right tool for *small real-time* models (sub-ms): most real-time
  ML audio plugins use it instead of ONNX. Reach for ONNX for the heavier/non-real-time tasks above.

## Attribution (required for every permissive license)

| License | Requirements |
|---|---|
| MIT | copyright notice + license text |
| Apache 2.0 | copyright + license + NOTICE (if present) + state changes; includes a patent grant |
| BSD-3-Clause | copyright + license; no name endorsement |
| CC-BY-4.0 | credit creator, link license, indicate changes |

Ship a `LICENSES.txt` in the bundle's `Contents/Resources/` and reference it from the plugin's About
dialog:

```
THIRD-PARTY LICENSES

ONNX Runtime — Copyright (c) Microsoft Corporation — MIT License — https://github.com/microsoft/onnxruntime
Basic Pitch — Copyright (c) 2022 Spotify AB — Apache License 2.0 — https://github.com/spotify/basic-pitch
[include full license texts]
```
