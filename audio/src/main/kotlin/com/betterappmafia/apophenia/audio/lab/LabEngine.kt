package com.betterappmafia.apophenia.audio.lab

import com.betterappmafia.apophenia.audio.AudioEngine

/**
 * Native side of the Device Lab (official plan §14.2). Every call blocks or touches the streams,
 * so call it from a worker thread, never the main thread.
 */
object LabEngine {
    init {
        AudioEngine.oboeVersion() // loads libapophenia_audio
    }

    /** Oboe/AAudio input presets, values from oboe/Definitions.h (aaudio_input_preset_t). */
    enum class InputPreset(val value: Int, val label: String) {
        Unprocessed(9, "UNPROCESSED"),
        VoiceRecognition(6, "VOICE_RECOGNITION"),
        Generic(1, "MIC (generic)"),
    }

    class LatencyCapture(
        val samples: FloatArray,
        val sampleRate: Int,
        val burstStarts: IntArray,
        val shortReads: Int,
        val disconnected: Boolean,
        val streamInfo: String,
    )

    class InputRecording(val samples: FloatArray, val sampleRate: Int, val streamInfo: String)

    class StabilityStats(
        val callbacks: Long,
        val framesOut: Long,
        val framesIn: Long,
        val shortReads: Long,
        val ringOverflowFrames: Long,
        val maxCallbackMicros: Long,
        val outXRuns: Long,
        val inXRuns: Long,
        val bytesWritten: Long,
        val maxWriteMicros: Long,
        val disconnected: Boolean,
        val clicks: Long,
        val sampleRate: Int,
    )

    fun runLatency(
        burst: FloatArray,
        startSeconds: Float,
        spacingSeconds: Float,
        count: Int,
        preset: InputPreset,
    ): Result<LatencyCapture> {
        val meta = IntArray(4)
        val samples = nativeRunLatency(burst, startSeconds, spacingSeconds, count, preset.value, meta)
            ?: return Result.failure(IllegalStateException(nativeLastStreamInfo()))
        val rate = meta[0]
        val starts = IntArray(count) { k -> ((startSeconds + k * spacingSeconds) * rate).toLong().toInt() }
        return Result.success(LatencyCapture(samples, rate, starts, meta[2], meta[3] != 0, nativeLastStreamInfo()))
    }

    fun recordInput(preset: InputPreset, seconds: Float): Result<InputRecording> {
        val meta = IntArray(2)
        val samples = nativeRecordInput(preset.value, seconds, meta)
            ?: return Result.failure(IllegalStateException(nativeLastStreamInfo()))
        return Result.success(InputRecording(samples, meta[0], nativeLastStreamInfo()))
    }

    fun startStability(wavPath: String, preset: InputPreset, bpm: Double): Result<String> =
        if (nativeStartStability(wavPath, preset.value, bpm)) Result.success(nativeLastStreamInfo())
        else Result.failure(IllegalStateException(nativeLastStreamInfo()))

    fun pollStability(): StabilityStats? {
        val s = LongArray(13)
        if (!nativePollStability(s)) return null
        return StabilityStats(s[0], s[1], s[2], s[3], s[4], s[5], s[6], s[7], s[8], s[9], s[10] != 0L, s[11], s[12].toInt())
    }

    fun stopStability() = nativeStopStability()

    private external fun nativeLastStreamInfo(): String
    private external fun nativeRunLatency(
        burst: FloatArray, startSeconds: Float, spacingSeconds: Float, count: Int, preset: Int, meta: IntArray,
    ): FloatArray?
    private external fun nativeRecordInput(preset: Int, seconds: Float, meta: IntArray): FloatArray?
    private external fun nativeStartStability(wavPath: String, preset: Int, bpm: Double): Boolean
    private external fun nativePollStability(stats: LongArray): Boolean
    private external fun nativeStopStability()
}
