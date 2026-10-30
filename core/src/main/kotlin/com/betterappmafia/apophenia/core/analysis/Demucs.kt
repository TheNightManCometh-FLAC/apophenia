package com.betterappmafia.apophenia.core.analysis

import kotlin.math.sqrt

/**
 * HT-Demucs 4-stem separation (Rouard, Massa and Défossez, ICASSP 2023) around an ONNX export of the whole
 * model, STFT included (StemSplit's `htdemucs.onnx`, which the user downloads and picks: the app never ships
 * the weights). The model maps 7.8 s of 44.1 kHz stereo (343,980 samples) to four stems of the same length.
 *
 * Ported from demucs 4.0.1, the way its own command line runs it (`separate.py` and `apply.py`, no shifts):
 * - the whole take is normalised by the mean and the (unbiased) standard deviation of its mono mix, and the
 *   stems are scaled back after;
 * - chunks of one segment start every 3/4 segment (`overlap=0.25`); each chunk shorter than a segment is
 *   widened to a segment around its middle with the audio on either side, zeros past the ends
 *   (`TensorChunk.padded`), and the middle of the output kept (`center_trim`);
 * - chunks are blended with a triangular weight, 1 … N/2 then N/2 … 1 over the max (`transition_power=1`),
 *   divided by the summed weight.
 *
 * Unlike Spleeter's masks, the stems are the model's own predictions and do not sum exactly to the take.
 * One chunk at a time is in memory; samples are sent to the sink as soon as no later chunk covers them.
 */
object Demucs {
    const val RATE = 44_100
    const val SEGMENT = 343_980
    const val STRIDE = (SEGMENT * 3) / 4
    /** The most samples one sink write carries: Spleeter's hop. */
    const val OUT = Spleeter.HOP
    /** The model's output order; the sink gets [Spleeter.STEMS] indices. */
    val MODEL_STEMS = listOf("drums", "bass", "other", "vocals")
    private val TO_APP = IntArray(4) { Spleeter.STEMS.indexOf(MODEL_STEMS[it]) }

    /** The model on one segment: input `[2][SEGMENT]` channel-major, output `[4 stems][2][SEGMENT]` in [MODEL_STEMS] order. */
    fun interface Net { fun run(mix: FloatArray): FloatArray }

    fun chunkCount(length: Long): Int = ((length + STRIDE - 1) / STRIDE).toInt()

    /** demucs's transition weight: `cat(arange(1, N//2 + 1), arange(N - N//2, 0, -1)) / max`. */
    fun weight(i: Int): Float {
        val half = SEGMENT / 2
        val w = if (i < half) i + 1 else SEGMENT - i
        return w.toFloat() / half
    }

    fun separate(length: Long, source: Spleeter.Source, net: Net, sink: Spleeter.Sink, progress: (done: Int, total: Int) -> Unit = { _, _ -> }) {
        if (length <= 0) return
        val block = 1 shl 16
        val l = FloatArray(block); val r = FloatArray(block)
        // Mean and unbiased standard deviation of the mono mix, as `wav.mean(0)`, `ref.mean()`, `ref.std()`.
        var sum = 0.0; var sq = 0.0
        var at = 0L
        while (at < length) {
            val c = minOf(block.toLong(), length - at).toInt()
            source.read(at, l, r, 0, c)
            for (i in 0 until c) { val m = (l[i] + r[i]) * 0.5; sum += m; sq += m * m }
            at += c
        }
        val mean = sum / length
        val variance = if (length > 1) (sq - sum * mean) / (length - 1) else 0.0
        // A silent take would divide by zero (demucs gives NaN there); its stems are silent at any scale.
        val std = sqrt(variance.coerceAtLeast(0.0)).takeIf { it > 1e-8 } ?: 1.0

        val chunks = chunkCount(length)
        val input = FloatArray(2 * SEGMENT)
        // Accumulators over [offset, offset + SEGMENT) of the current chunk: [stem][channel][i], and the weight.
        val acc = Array(4) { Array(2) { FloatArray(SEGMENT) } }
        val wsum = FloatArray(SEGMENT)
        // The sink takes at most OUT samples at a time (the app resamples each write into a fixed buffer), as from Spleeter.
        val outL = FloatArray(OUT); val outR = FloatArray(OUT)
        progress(0, chunks)
        for (k in 0 until chunks) {
            val offset = k.toLong() * STRIDE
            val chunkLength = minOf(length - offset, SEGMENT.toLong()).toInt()
            // TensorChunk.padded(SEGMENT): start = offset − delta / 2, zeros outside the take.
            val start = offset - (SEGMENT - chunkLength) / 2
            input.fill(0f)
            val from = maxOf(0L, start); val to = minOf(length, start + SEGMENT)
            var p = from
            while (p < to) {
                val c = minOf(block.toLong(), to - p).toInt()
                source.read(p, l, r, 0, c)
                val dst = (p - start).toInt()
                for (i in 0 until c) {
                    input[dst + i] = ((l[i] - mean) / std).toFloat()
                    input[SEGMENT + dst + i] = ((r[i] - mean) / std).toFloat()
                }
                p += c
            }
            val y = net.run(input)
            require(y.size == 4 * 2 * SEGMENT) { "the model gave ${y.size} values, not ${4 * 2 * SEGMENT}" }
            // center_trim to chunkLength: the same left offset as the padding.
            val trim = (SEGMENT - chunkLength) / 2
            for (s in 0 until 4) for (ch in 0 until 2) {
                val a = acc[s][ch]; val base = (s * 2 + ch) * SEGMENT + trim
                for (i in 0 until chunkLength) a[i] += weight(i) * y[base + i]
            }
            for (i in 0 until chunkLength) wsum[i] += weight(i)
            // Final: everything before the next chunk's start (or the end).
            val done = if (k == chunks - 1) chunkLength else STRIDE
            for (s in 0 until 4) {
                val aL = acc[s][0]; val aR = acc[s][1]
                var i = 0
                while (i < done) {
                    val c = minOf(OUT, done - i)
                    for (j in 0 until c) {
                        val w = wsum[i + j]
                        outL[j] = (aL[i + j] / w * std + mean).toFloat()
                        outR[j] = (aR[i + j] / w * std + mean).toFloat()
                    }
                    sink.write(TO_APP[s], outL, outR, c)
                    i += c
                }
            }
            // Slide the accumulators by one stride.
            if (k < chunks - 1) {
                for (s in 0 until 4) for (ch in 0 until 2) {
                    val a = acc[s][ch]
                    System.arraycopy(a, STRIDE, a, 0, SEGMENT - STRIDE); a.fill(0f, SEGMENT - STRIDE, SEGMENT)
                }
                System.arraycopy(wsum, STRIDE, wsum, 0, SEGMENT - STRIDE); wsum.fill(0f, SEGMENT - STRIDE, SEGMENT)
            }
            progress(k + 1, chunks)
        }
    }
}
