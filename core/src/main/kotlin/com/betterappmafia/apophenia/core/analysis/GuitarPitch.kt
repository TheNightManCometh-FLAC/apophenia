package com.betterappmafia.apophenia.core.analysis

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10

/**
 * GAPS guitar checkpoint: 16 kHz, periodic Hann power spectrum, the checkpoint's mel matrix,
 * 10 log10(max(power, 1e-10)). Ten-second windows overlap by half; only their central predictions
 * are joined. Regression peaks and note boundaries follow Kong/Riley's reference inference.
 * Explicitly selected by the musician; no instrument detection or chord-completion rule.
 */
object GuitarPitch {
    const val MODEL_ID = "guitar-gaps-b7bec65-compact-v1"
    const val RATE = 16_000
    const val FFT = 2048
    const val HOP = 160
    const val MELS = 229
    const val PITCHES = 88
    const val SAMPLES = RATE * 10
    const val FRAMES = 1001
    val OUTPUTS = listOf("reg_onset_output", "reg_offset_output", "frame_output", "velocity_output")

    /** Row-major [FRAMES] × [PITCHES] for each of the four named outputs. */
    fun interface Net { fun run(mel: FloatArray): List<FloatArray> }

    private val hann = DoubleArray(FFT) { .5 - .5 * cos(2 * PI * it / FFT) }

    fun melWeights(bytes: ByteArray): FloatArray {
        require(bytes.size == (FFT / 2 + 1) * MELS * 4) { "unexpected guitar mel matrix size" }
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(b.remaining()).also { b.get(it) }
    }

    /** One full, zero-padded audio window. Reflect only at this window's two boundaries. */
    fun spect(window: FloatArray, weights: FloatArray): FloatArray {
        require(window.size == SAMPLES && weights.size == (FFT / 2 + 1) * MELS)
        val out = FloatArray(FRAMES * MELS)
        val plan = FftPlan(FFT)
        val re = DoubleArray(FFT); val im = DoubleArray(FFT)
        val power = DoubleArray(FFT / 2 + 1)
        // Each triangular mel filter touches few bins; skip the matrix's zero entries.
        val bins = Array(MELS) { m -> (0..FFT / 2).filter { weights[it * MELS + m] != 0f }.toIntArray() }
        for (f in 0 until FRAMES) {
            val start = f * HOP - FFT / 2
            for (i in 0 until FFT) {
                val p = start + i
                val j = when { p < 0 -> -p; p >= SAMPLES -> 2 * (SAMPLES - 1) - p; else -> p }
                re[i] = window[j] * hann[i]; im[i] = 0.0
            }
            plan.forward(re, im)
            for (k in power.indices) power[k] = re[k] * re[k] + im[k] * im[k]
            for (m in 0 until MELS) {
                var sum = 0.0
                for (k in bins[m]) sum += power[k] * weights[k * MELS + m]
                out[f * MELS + m] = (10 * log10(maxOf(1e-10, sum))).toFloat()
            }
        }
        return out
    }

    fun windowCount(samples: Int): Int {
        require(samples >= 0)
        val blocks = maxOf(1, (samples + SAMPLES - 1) / SAMPLES)
        return blocks * 2 - 1
    }

    /** Decode only model evidence. Notes past the source's end are discarded/clipped. */
    fun transcribe(audio: FloatArray, weights: FloatArray, net: Net, progress: (Int, Int) -> Unit = { _, _ -> }): List<BasicPitch.TimedNote> {
        if (audio.isEmpty()) return emptyList()
        val count = windowCount(audio.size)
        val frames = if (count == 1) FRAMES else (count + 1) * 500
        val joined = List(4) { FloatArray(frames * PITCHES) }
        progress(0, count)
        var dest = 0
        for (w in 0 until count) {
            val start = w * (SAMPLES / 2)
            val window = FloatArray(SAMPLES)
            val length = minOf(SAMPLES, audio.size - start).coerceAtLeast(0)
            if (length > 0) System.arraycopy(audio, start, window, 0, length)
            val output = net.run(spect(window, weights))
            require(output.size == 4 && output.all { it.size == FRAMES * PITCHES }) { "unexpected guitar model outputs" }
            val from = if (w == 0) 0 else 250
            val until = when { count == 1 -> FRAMES; w == count - 1 -> 1000; else -> 750 }
            for (head in 0..3) System.arraycopy(output[head], from * PITCHES, joined[head], dest * PITCHES, (until - from) * PITCHES)
            dest += until - from
            progress(w + 1, count)
        }
        check(dest == frames)
        return notes(joined, audio.size.toDouble() / RATE)
    }

    /** Regression peak's sub-frame shift, or NaN when not a peak. Flat plateaus have zero shift. */
    private fun peaks(x: FloatArray, pitch: Int, neighbour: Int): FloatArray {
        val n = x.size / PITCHES
        val shifts = FloatArray(n) { Float.NaN }
        fun at(t: Int) = x[t * PITCHES + pitch]
        for (t in neighbour until n - neighbour) {
            if (at(t) <= .3f) continue
            if ((0 until neighbour).any { at(t - it) < at(t - it - 1) || at(t + it) < at(t + it + 1) }) continue
            val left = at(t - 1); val right = at(t + 1)
            val denominator = at(t) - minOf(left, right)
            shifts[t] = if (denominator > 0f) (right - left) / denominator / 2f else 0f
        }
        return shifts
    }

    fun notes(output: List<FloatArray>, duration: Double): List<BasicPitch.TimedNote> {
        require(output.size == 4 && output.all { it.size == output[0].size && it.size % PITCHES == 0 })
        val (onset, offset, frame, velocity) = output
        val n = frame.size / PITCHES
        val notes = ArrayList<BasicPitch.TimedNote>()
        for (p in 0 until PITCHES) {
            val on = peaks(onset, p, 2); val off = peaks(offset, p, 4)
            var begin = -1; var disappeared = -1; var ended = -1
            fun emit(end: Int, offsetShift: Float) {
                val start = ((begin + on[begin].toDouble()) / 100).toFloat().toDouble()
                val finish = minOf(duration, ((end + offsetShift.toDouble()) / 100).toFloat().toDouble())
                if (start >= 0 && start < duration && finish > start) {
                    // Reference velocity is int(128 * activation); normalise for the shared MIDI writer.
                    val v = (128f * velocity[begin * PITCHES + p]).toInt().coerceIn(1, 127)
                    notes += BasicPitch.TimedNote(start, finish, p + 21, v / 127.0, null)
                }
            }
            for (i in 0 until n) {
                if (!on[i].isNaN()) {
                    if (begin >= 0) { emit(maxOf(0, i - 1), 0f); disappeared = -1; ended = -1 }
                    begin = i
                }
                if (begin >= 0 && i > begin) {
                    if (frame[i * PITCHES + p] <= .1f && disappeared < 0) disappeared = i
                    if (!off[i].isNaN() && ended < 0) ended = i
                    if (disappeared >= 0) {
                        val end = if (ended >= 0 && ended - begin > disappeared - ended) ended else disappeared
                        emit(end, off[end].takeUnless { it.isNaN() } ?: 0f)
                        begin = -1; disappeared = -1; ended = -1
                    }
                    if (begin >= 0 && (i - begin >= 600 || i == n - 1)) {
                        emit(i, off[i].takeUnless { it.isNaN() } ?: 0f)
                        begin = -1; disappeared = -1; ended = -1
                    }
                }
            }
        }
        return notes.sortedWith(compareBy({ it.start }, { it.midi }, { it.end }))
    }
}
