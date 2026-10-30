package com.betterappmafia.apophenia.core.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Demucs's chunking around a stand-in model: the streamed result against a whole-array port of demucs 4.0.1's
 * `apply_model` (split, overlap 0.25, triangular weight, `TensorChunk.padded` and `center_trim`) with
 * `separate.py`'s normalisation. The stand-in weights its output by the position inside the segment, so
 * wrong offsets, padding or blending all show. The real model is checked against demucs itself in DemucsModelTest.
 */
class DemucsTest {
    private val n = Demucs.SEGMENT

    /** Stem s, channel c: (s + 1) · x · (1 + position / N), with the position inside the model's segment. */
    private val net = Demucs.Net { x ->
        FloatArray(4 * 2 * n) { k ->
            val s = k / (2 * n); val c = (k / n) % 2; val i = k % n
            (s + 1) * x[c * n + i] * (1f + i.toFloat() / n)
        }
    }

    private fun song(length: Int) = Array(2) { c -> FloatArray(length) { i -> (0.3 * sin(i * (0.01 + 0.003 * c)) + 0.05 + 0.1 * sin(i * 0.00007)).toFloat() } }

    private fun streamed(x: Array<FloatArray>, model: Demucs.Net = net): Array<Array<FloatArray>> {
        val len = x[0].size
        val out = Array(4) { Array(2) { FloatArray(len) } }
        val at = IntArray(4)
        Demucs.separate(len.toLong(), { start, l, r, off, count ->
            System.arraycopy(x[0], start.toInt(), l, off, count); System.arraycopy(x[1], start.toInt(), r, off, count)
        }, model, { s, l, r, count ->
            assertTrue("a write carries at most ${Demucs.OUT} samples, as the app's resampling buffers expect (got $count)", count <= Demucs.OUT)
            System.arraycopy(l, 0, out[s][0], at[s], count); System.arraycopy(r, 0, out[s][1], at[s], count); at[s] += count
        })
        assertTrue("every stem is written to the end", at.all { it == len })
        return out
    }

    /** apply_model + separate.py, whole arrays, in doubles. Output in Spleeter.STEMS order. */
    private fun reference(x: Array<FloatArray>): Array<Array<DoubleArray>> {
        val len = x[0].size
        val mono = DoubleArray(len) { (x[0][it] + x[1][it]) / 2.0 }
        val mean = mono.average()
        val std = sqrt(mono.sumOf { (it - mean) * (it - mean) } / (len - 1))
        val wav = Array(2) { c -> DoubleArray(len) { (x[c][it] - mean) / std } }
        val stride = (0.75 * n).toInt()
        val half = n / 2
        val weight = DoubleArray(n) { i -> (if (i < half) i + 1 else n - i).toDouble() / half }
        val out = Array(4) { Array(2) { DoubleArray(len) } }
        val sum = DoubleArray(len)
        var offset = 0
        while (offset < len) {
            val chunkLength = minOf(n, len - offset)
            val delta = n - chunkLength
            val start = offset - delta / 2
            val input = FloatArray(2 * n)
            for (c in 0 until 2) for (i in 0 until n) { val p = start + i; if (p in 0 until len) input[c * n + i] = wav[c][p].toFloat() }
            val y = net.run(input)
            for (m in 0 until 4) for (c in 0 until 2) for (i in 0 until chunkLength)
                out[m][c][offset + i] += weight[i] * y[(m * 2 + c) * n + delta / 2 + i]
            for (i in 0 until chunkLength) sum[offset + i] += weight[i]
            offset += stride
        }
        val app = Array(4) { Array(2) { DoubleArray(len) } }
        for (m in 0 until 4) {
            val s = Spleeter.STEMS.indexOf(Demucs.MODEL_STEMS[m])
            for (c in 0 until 2) for (i in 0 until len) app[s][c][i] = out[m][c][i] / sum[i] * std + mean
        }
        return app
    }

    private fun assertMatches(length: Int) {
        val x = song(length)
        val got = streamed(x); val want = reference(x)
        var worst = 0.0
        for (s in 0 until 4) for (c in 0 until 2) for (i in 0 until length) worst = maxOf(worst, abs(got[s][c][i] - want[s][c][i]))
        assertTrue("a $length-sample take streams out as demucs's apply_model has it (worst difference $worst)", worst < 2e-5)
    }

    @Test fun `a take shorter than one segment is widened around its middle`() = assertMatches(100_000)
    @Test fun `a take of exactly one segment is one chunk`() = assertMatches(n)
    @Test fun `one sample past a segment makes a second chunk`() = assertMatches(n + 1)
    @Test fun `several chunks blend with the triangular weight`() = assertMatches(3 * Demucs.STRIDE + 12_345)

    @Test fun `a model that returns its input gives back the take in every stem`() {
        val x = song(2 * Demucs.STRIDE + 999)
        val got = streamed(x) { input -> FloatArray(4 * 2 * n) { input[it % (2 * n)] } }
        var worst = 0.0
        for (s in 0 until 4) for (c in 0 until 2) for (i in x[c].indices) worst = maxOf(worst, abs(got[s][c][i] - x[c][i]).toDouble())
        assertTrue("the normalisation is undone and the weights divided out (worst $worst)", worst < 1e-5)
    }

    @Test fun `the transition weight rises from 1 over N-half to 1 and falls back`() {
        assertEquals(1f / (n / 2), Demucs.weight(0), 0f)
        assertEquals(1f, Demucs.weight(n / 2 - 1), 0f)
        assertEquals(1f, Demucs.weight(n / 2), 0f)
        assertEquals(1f / (n / 2), Demucs.weight(n - 1), 0f)
        assertEquals("chunks start every three quarters of a segment", 257_985, Demucs.STRIDE)
    }

    @Test fun `a silent take gives silent stems, not NaN`() {
        val x = Array(2) { FloatArray(50_000) }
        val got = streamed(x)
        assertTrue(got.all { st -> st.all { ch -> ch.all { it == 0f } } })
    }
}
