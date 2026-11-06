---
name: apophenia-realtime-audio
description: Use whenever writing or changing Apophenia's C++/Oboe audio engine, the Kotlin–native bridge, recording/playback, the metronome, tuner input, stacks/player mixing, latency calibration, time-stretch/pitch-shift rendering, or any ML inference (Basic Pitch, Spleeter, Moonshine) that consumes audio. Holds the real-time and data-safety rules.
---

# Apophenia real-time audio

Deeper references: `docs/reference/account-skills/vst3-threading-realtime-safety/` (lock-free SPSC queue, worker pattern, memory ordering), `audio-spectral-fft/` (STFT, phase vocoder, HPSS front end), `onnx-integration/` (inference only on a worker thread, session built once). They're written for VST3; the rules apply here unchanged.

## Shape of the engine
- **One output stream** (Oboe, AAudio, `PerformanceMode::LowLatency`, `SharingMode::Exclusive` when available) feeding one mixer: player layers, metronome, drone, piano sampler, slow-down player.
- **One microphone owner**: a single input stream whose frames fan out to consumers (recorder, tuner, speech, pitch-tracking note entry). Never open a second input stream; never let two features fight over the mic.
- Mic states are explicit: **Off** (input stream closed and released, not muted) and **Recording**. There is no Listening state and no pre-roll (Christopher, 22 Oct 2026). The tuner may open the mic while it's on screen without enabling pre-roll. UI always shows the real state.

## The audio callback is sacred
In `onAudioReady` (and anything it calls): no allocation, no locks/mutexes, no JNI calls into Kotlin, no logging, no file or database I/O, no exceptions, no blocking. Pre-allocate everything when the stream starts.
- UI → audio: lock-free SPSC queue of small command structs, or atomics for single scalars.
- Audio → UI (meters, tuner frames, playhead): lock-free SPSC queue / ring buffer read on a UI-side timer.
- Recording: the callback copies frames into a pre-allocated ring buffer; a **writer thread** drains it to disk.
- Expensive work (analysis, time-stretch rendering, ML inference, waveform peaks) runs on worker threads, never in the callback.
- Flush denormals at stream start.

## Timing
- Metronome clicks are scheduled at exact sample frames on the output timeline (no Handler/timer scheduling). Its beat grid (frame positions, meter, accents, tempo changes) is saved with any take recorded while it ran, labelled as the click, not as proof of the performed tempo.
- Distinguish source frames, source seconds, beat/bar position, rendered frames and display position; conversions get round-trip tests (6/8, tempo changes, count-ins, sample-rate changes).
- Overdub alignment: measure round-trip latency per output route (wired, Bluetooth, speaker) with a calibration step and compensate; store the latency used with each take. Use `AAudioStream_getTimestamp`/Oboe `getTimestamp` for visual sync. Target ±5 ms after calibration **[measure]**.
- Handle route changes, interruptions (calls, focus loss), stream disconnects (reopen), sample-rate mismatches.

## Recordings are never edited
- Write audio to disk **as it records** (temp file, appended; finalized atomically, then the database row is committed). On launch, detect and recover partial takes. Closing the save sheet never loses audio.
- **Pausing a take** (0.9.46/0.9.47): the mic stays open; pauses are input-ring gaps `[from, to)` kept under a mutex shared by the control and writer threads (never the audio thread). The writer skips a gap (holds while it is open, without counting it as overrun) or, for a take over a playing stack, writes zeros for it so later audio stays lined up with the stack. Anything mapped from input frames to file frames (click grid, markers) goes through the same gap list.
- Original files are immutable. Trim, gain, fades, loop points, offsets and tempo/pitch conform are **per-project settings** applied at playback or in cached renders.
- Conformed layers are rendered on a worker into an evictable cache keyed by (source file, settings, stretcher version); playback mixes cached renders so many layers stay cheap. Free-time layers are never conformed; unknown-key layers aren't pitch-shifted.
- Loop seams get short crossfades; compensate the stretcher's latency/padding before writing rendered length.

## Foreground service and Android rules
Recording or playing with the screen off needs a foreground service with the correct type (`microphone` / `mediaPlayback`) and `FOREGROUND_SERVICE_MICROPHONE` + `RECORD_AUDIO`. Android 14+ restricts starting a microphone foreground service from the background; widget/shortcut capture must open an activity (user-initiated) and start capture from there. Test every entry path on the S22 Ultra (Android 16): app open, widget, shortcut, lock screen, process death.

## ML inference
Models (Basic Pitch TFLite, Spleeter TFLite/ONNX, Moonshine via sherpa-onnx) load once off the main thread, run on workers, never in the audio callback, and sit behind interfaces so the app works if a model fails to load. Results are derived data linked to the source take with model name/version; the user's corrections always win.
Models in the app (Oct 2026): Beat This! (tempo, beats, downbeats; ONNX), Basic Pitch and the GAPS guitar model (Notes → MIDI, the user picks), Spleeter with the multichannel Wiener filter (stems, fp16 weights with fp32 arithmetic), Demucs added by the user (link, file picker, SHA check), Moonshine (speech). What each is measured to do, and what was dropped: the personal skill `music-audio-analysis-mir`. ONNX Runtime on Android quirks: `onnx-integration`. Bump `TakeAnalysis.VERSION` whenever results change, so old takes are analysed again; never overwrite a value the user set.

## Measurements belong in the Device Lab
Latency per route, mic source quality (UNPROCESSED vs VOICE_RECOGNITION vs MIC), simultaneous in/out stability, max conformed layers, stretcher quality, model speed. Record results in `docs/STATUS.md` with the device model and date; never state a performance claim that wasn't measured.
