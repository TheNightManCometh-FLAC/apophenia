package com.betterappmafia.apophenia.core.analysis

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Spotify Basic Pitch's pre- and post-processing (plan §10.3), ported from the reference Python
 * (github.com/spotify/basic-pitch, `basic_pitch/inference.py` and `note_creation.py`, Apache-2.0,
 * Copyright 2022 Spotify AB). The model itself runs in the app on LiteRT; everything around it is here
 * so it is tested on the desktop. Names follow the reference so the two can be read side by side.
 */
object BasicPitch {
    const val SAMPLE_RATE = 22050
    const val FFT_HOP = 256
    const val AUDIO_N_SAMPLES = SAMPLE_RATE * 2 - FFT_HOP          // 43844
    const val ANNOTATIONS_FPS = SAMPLE_RATE / FFT_HOP               // 86
    const val ANNOT_N_FRAMES = ANNOTATIONS_FPS * 2                  // 172
    const val N_OVERLAPPING_FRAMES = 30
    const val OVERLAP_LEN = N_OVERLAPPING_FRAMES * FFT_HOP          // 7680
    const val HOP_SIZE = AUDIO_N_SAMPLES - OVERLAP_LEN              // 36164
    const val N_NOTES = 88
    const val N_CONTOURS = 88 * 3
    const val MIDI_OFFSET = 21
    const val MAX_FREQ_IDX = 87
    const val MAGIC_ALIGNMENT_OFFSET = 0.0018

    const val ONSET_THRESHOLD = 0.5
    const val FRAME_THRESHOLD = 0.3
    /** 127.7 ms in frames, rounded as the reference does: 11. */
    val MIN_NOTE_LEN: Int = (127.7 / 1000 * (SAMPLE_RATE.toDouble() / FFT_HOP)).roundToInt()
    const val ENERGY_TOL = 11

    /** A note as the reference returns it: frames, MIDI number, mean frame activation, and bends in thirds of a semitone. */
    data class Event(val startFrame: Int, val endFrame: Int, val midi: Int, val amplitude: Double, val bends: List<Int>? = null)

    /** The model's input windows: [OVERLAP_LEN]/2 zeros in front, then [AUDIO_N_SAMPLES] every [HOP_SIZE], zero-padded. */
    fun windows(audio: FloatArray): List<FloatArray> {
        val padded = FloatArray(OVERLAP_LEN / 2 + audio.size)
        System.arraycopy(audio, 0, padded, OVERLAP_LEN / 2, audio.size)
        val out = ArrayList<FloatArray>()
        var i = 0
        while (i < padded.size) {
            val w = FloatArray(AUDIO_N_SAMPLES)
            System.arraycopy(padded, i, w, 0, minOf(AUDIO_N_SAMPLES, padded.size - i))
            out += w
            i += HOP_SIZE
        }
        return out
    }

    /**
     * Joins per-window outputs (each [ANNOT_N_FRAMES] × bins) into one matrix: half the overlapping
     * frames dropped from each end of every window, trimmed to the expected length.
     */
    fun unwrap(perWindow: List<Array<FloatArray>>, originalLength: Int): Array<FloatArray> {
        val olap = N_OVERLAPPING_FRAMES / 2
        val rows = ArrayList<FloatArray>()
        for (w in perWindow) for (t in olap until w.size - olap) rows += w[t]
        val expected = (originalLength.toDouble() / HOP_SIZE * (ANNOT_N_FRAMES - N_OVERLAPPING_FRAMES)).toInt()
        return rows.take(expected).toTypedArray()
    }

    /** Frame index to seconds, with the reference's per-window correction (`model_frames_to_time`). */
    fun frameTime(frame: Int): Double {
        val original = frame.toDouble() * FFT_HOP / SAMPLE_RATE
        val windowOffset = FFT_HOP.toDouble() / SAMPLE_RATE * (ANNOT_N_FRAMES - AUDIO_N_SAMPLES.toDouble() / FFT_HOP) + MAGIC_ALIGNMENT_OFFSET
        return original - windowOffset * floor(frame.toDouble() / ANNOT_N_FRAMES)
    }

    /** `get_infered_onsets`: onsets, or large rises in the frame activations scaled to the onsets' maximum, whichever is higher. */
    fun inferredOnsets(onsets: Array<FloatArray>, frames: Array<FloatArray>, nDiff: Int = 2): Array<FloatArray> {
        val t = frames.size
        if (t == 0) return onsets
        val bins = frames[0].size
        val diff = Array(t) { FloatArray(bins) }
        var maxDiff = 0f
        var maxOnset = 0f
        for (i in 0 until t) for (b in 0 until bins) {
            var m = Float.MAX_VALUE
            for (n in 1..nDiff) {
                val prev = if (i - n >= 0) frames[i - n][b] else 0f
                m = minOf(m, frames[i][b] - prev)
            }
            val v = if (i < nDiff || m < 0) 0f else m
            diff[i][b] = v
            if (v > maxDiff) maxDiff = v
            if (onsets[i][b] > maxOnset) maxOnset = onsets[i][b]
        }
        return Array(t) { i -> FloatArray(bins) { b -> maxOf(onsets[i][b], if (maxDiff > 0) maxOnset * diff[i][b] / maxDiff else 0f) } }
    }

    /** `output_to_notes_polyphonic`: notes from onset peaks, then (melodia trick) from what energy is left. */
    fun notes(
        frames: Array<FloatArray>,
        onsetsIn: Array<FloatArray>,
        onsetThresh: Double = ONSET_THRESHOLD,
        frameThresh: Double = FRAME_THRESHOLD,
        minNoteLen: Int = MIN_NOTE_LEN,
        inferOnsets: Boolean = true,
        melodiaTrick: Boolean = true,
        energyTol: Int = ENERGY_TOL,
    ): List<Event> {
        val nFrames = frames.size
        if (nFrames == 0) return emptyList()
        val bins = frames[0].size
        val onsets = if (inferOnsets) inferredOnsets(onsetsIn, frames) else onsetsIn
        // Peaks along time (scipy argrelmax: strictly greater than both neighbours; the ends never qualify).
        val peaks = ArrayList<IntArray>()
        for (i in 1 until nFrames - 1) for (b in 0 until bins) {
            val v = onsets[i][b]
            if (v > onsets[i - 1][b] && v > onsets[i + 1][b] && v >= onsetThresh) peaks += intArrayOf(i, b)
        }
        // np.where order is time-major then frequency; the reference walks it backwards.
        peaks.reverse()
        val remaining = Array(nFrames) { frames[it].copyOf() }
        val events = ArrayList<Event>()
        for ((start, f) in peaks.map { it[0] to it[1] }) {
            if (start >= nFrames - 1) continue
            var i = start + 1
            var k = 0
            while (i < nFrames - 1 && k < energyTol) {
                if (remaining[i][f] < frameThresh) k++ else k = 0
                i++
            }
            i -= k
            if (i - start <= minNoteLen) continue
            for (t in start until i) {
                remaining[t][f] = 0f
                if (f < MAX_FREQ_IDX) remaining[t][f + 1] = 0f
                if (f > 0) remaining[t][f - 1] = 0f
            }
            events += Event(start, i, f + MIDI_OFFSET, mean(frames, start, i, f))
        }
        if (melodiaTrick) {
            while (true) {
                var best = 0f; var mid = -1; var f = -1
                for (t in 0 until nFrames) for (b in 0 until bins) if (remaining[t][b] > best) { best = remaining[t][b]; mid = t; f = b }
                if (best <= frameThresh) break
                remaining[mid][f] = 0f
                var i = mid + 1
                var k = 0
                while (i < nFrames - 1 && k < energyTol) {
                    if (remaining[i][f] < frameThresh) k++ else k = 0
                    remaining[i][f] = 0f
                    if (f < MAX_FREQ_IDX) remaining[i][f + 1] = 0f
                    if (f > 0) remaining[i][f - 1] = 0f
                    i++
                }
                val end = i - 1 - k
                i = mid - 1
                k = 0
                while (i > 0 && k < energyTol) {
                    if (remaining[i][f] < frameThresh) k++ else k = 0
                    remaining[i][f] = 0f
                    if (f < MAX_FREQ_IDX) remaining[i][f + 1] = 0f
                    if (f > 0) remaining[i][f - 1] = 0f
                    i--
                }
                val start = i + 1 + k
                if (end - start <= minNoteLen) continue
                events += Event(start, end, f + MIDI_OFFSET, mean(frames, start, end, f))
            }
        }
        return events
    }

    private fun mean(frames: Array<FloatArray>, from: Int, to: Int, f: Int): Double {
        var s = 0.0
        for (t in from until to) s += frames[t][f]
        return if (to > from) s / (to - from) else 0.0
    }

    /** `get_pitch_bends`: per frame, the contour bin (thirds of a semitone) off the note's own, Gaussian-weighted within ±25 bins. */
    fun withBends(contours: Array<FloatArray>, events: List<Event>, tolerance: Int = 25): List<Event> {
        val window = tolerance * 2 + 1
        // scipy.signal.windows.gaussian(window, std=5)
        val gauss = DoubleArray(window) { val n = it - (window - 1) / 2.0; exp(-0.5 * (n / 5.0) * (n / 5.0)) }
        return events.map { e ->
            val hz = 440.0 * Math.pow(2.0, (e.midi - 69) / 12.0)
            val freqIdx = (12.0 * 3 * ln(hz / 27.5) / ln(2.0)).roundToInt()
            val lo = maxOf(freqIdx - tolerance, 0)
            val hi = minOf(N_CONTOURS, freqIdx + tolerance + 1)
            val gStart = maxOf(0, tolerance - freqIdx)
            val shift = tolerance - maxOf(0, tolerance - freqIdx)
            val bends = (e.startFrame until e.endFrame).map { t ->
                var best = Double.NEGATIVE_INFINITY; var arg = 0
                for (b in lo until hi) {
                    val v = contours[t][b] * gauss[gStart + (b - lo)]
                    if (v > best) { best = v; arg = b - lo }
                }
                arg - shift
            }
            e.copy(bends = bends)
        }
    }

    /** A note in seconds, ready to store. */
    data class TimedNote(val start: Double, val end: Double, val midi: Int, val amplitude: Double, val bends: List<Int>?)

    fun timed(events: List<Event>): List<TimedNote> =
        events.map { TimedNote(frameTime(it.startFrame), frameTime(it.endFrame), it.midi, it.amplitude, it.bends) }.sortedWith(compareBy({ it.start }, { it.midi }))
}

/** A windowed-sinc low-pass (Blackman), for reducing a take's rate without aliasing into what a model listens to. */
object Lowpass {
    fun apply(x: FloatArray, rate: Int, cutoffHz: Double, taps: Int = 63): FloatArray {
        require(taps % 2 == 1)
        val fc = cutoffHz / rate
        val m = taps - 1
        val h = DoubleArray(taps) { n ->
            val k = n - m / 2.0
            val sinc = if (k == 0.0) 2 * fc else sin(2 * PI * fc * k) / (PI * k)
            sinc * (0.42 - 0.5 * cos(2 * PI * n / m) + 0.08 * cos(4 * PI * n / m))
        }
        val sum = h.sum()
        for (i in h.indices) h[i] /= sum
        val half = m / 2
        return FloatArray(x.size) { i ->
            var s = 0.0
            for (j in 0 until taps) { val idx = i + j - half; if (idx in x.indices) s += h[j] * x[idx] }
            s.toFloat()
        }
    }
}
