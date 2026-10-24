package com.betterappmafia.apophenia.audio.lab

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/** Pure-Kotlin analysis for the round-trip latency test; no Android types, tested on the JVM. */
object LatencyAnalysis {

    /**
     * A Hann-windowed burst of pseudo-random ±1 samples. Broadband, so its autocorrelation has one
     * sharp peak even after the speaker and mic have filtered it.
     */
    fun burst(length: Int = 1024, amplitude: Float = 0.5f, seed: Long = 0x5EED): FloatArray {
        var state = seed
        return FloatArray(length) { i ->
            state = (state * 6364136223846793005L + 1442695040888963407L)
            val sign = if ((state ushr 33) and 1L == 0L) 1f else -1f
            val w = 0.5 - 0.5 * cos(2.0 * PI * i / (length - 1))
            (amplitude * sign * w).toFloat()
        }
    }

    /** One burst found in the capture. [lagFrames] is the round trip; [confidence] is peak over correlation RMS. */
    data class Detection(val lagFrames: Int, val confidence: Double, val inverted: Boolean)

    /**
     * Cross-correlates [burst] against [capture] for lags 0 until [maxLag] after [start].
     * Returns null when the window runs off the capture.
     */
    fun detect(capture: FloatArray, start: Int, burst: FloatArray, maxLag: Int): Detection? {
        val usableLags = minOf(maxLag, capture.size - start - burst.size)
        if (usableLags <= 0) return null
        val corr = DoubleArray(usableLags)
        for (lag in 0 until usableLags) {
            var sum = 0.0
            val base = start + lag
            for (i in burst.indices) sum += burst[i] * capture[base + i]
            corr[lag] = sum
        }
        var best = 0
        for (lag in corr.indices) if (abs(corr[lag]) > abs(corr[best])) best = lag
        var sq = 0.0
        for (c in corr) sq += c * c
        val rms = sqrt(sq / corr.size)
        val confidence = if (rms > 0.0) abs(corr[best]) / rms else 0.0
        return Detection(best, confidence, corr[best] < 0)
    }

    data class Summary(
        val detections: List<Detection?>,
        val acceptedLagsMs: List<Double>,
        val medianMs: Double?,
        val minMs: Double?,
        val maxMs: Double?,
    ) {
        /** Half of max − min: how far a single calibration could be off on this route. */
        val halfSpreadMs: Double? get() = if (minMs != null && maxMs != null) (maxMs - minMs) / 2 else null
    }

    /** Detections below [minConfidence] are listed but not used: the burst was lost in noise. */
    fun summarize(detections: List<Detection?>, sampleRate: Int, minConfidence: Double = 8.0): Summary {
        val accepted = detections.filterNotNull().filter { it.confidence >= minConfidence }
            .map { it.lagFrames * 1000.0 / sampleRate }
        if (accepted.isEmpty()) return Summary(detections, accepted, null, null, null)
        val sorted = accepted.sorted()
        val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2]
        else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        return Summary(detections, accepted, median, sorted.first(), sorted.last())
    }
}
