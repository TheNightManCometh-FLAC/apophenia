package com.betterappmafia.apophenia.core.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class SpleeterTest {
    /** A stereo test song: noise plus tones, one above the 11 kHz the net sees. */
    private fun song(length: Int, seed: Int = 1): Pair<FloatArray, FloatArray> {
        val r = Random(seed)
        val l = FloatArray(length) { i -> (0.2 * (r.nextDouble() - 0.5) + 0.3 * sin(2 * PI * 110 * i / Spleeter.RATE) + 0.1 * sin(2 * PI * 15_000 * i / Spleeter.RATE)).toFloat() }
        val rr = FloatArray(length) { i -> (0.2 * (r.nextDouble() - 0.5) + 0.3 * sin(2 * PI * 440 * i / Spleeter.RATE) + 0.1 * l[i]).toFloat() }
        return l to rr
    }

    private class Collect(length: Int) {
        val l = Array(4) { FloatArray(length) }; val r = Array(4) { FloatArray(length) }
        val at = IntArray(4)
        val sink = Spleeter.Sink { s, left, right, count ->
            System.arraycopy(left, 0, l[s], at[s], count); System.arraycopy(right, 0, r[s], at[s], count); at[s] += count
        }
    }

    private fun run(left: FloatArray, right: FloatArray, net: Spleeter.Net): Collect {
        val c = Collect(left.size)
        val source = Spleeter.Source { start, l, r, offset, count ->
            System.arraycopy(left, start.toInt(), l, offset, count); System.arraycopy(right, start.toInt(), r, offset, count)
        }
        Spleeter.separate(left.size.toLong(), source, net, c.sink)
        return c
    }

    /** Worst |sum of the four stems − mix| over both channels. */
    private fun sumError(c: Collect, l: FloatArray, r: FloatArray): Double {
        var worst = 0.0
        for (i in l.indices) {
            worst = maxOf(worst, abs(c.l.sumOf { it[i].toDouble() } - l[i]), abs(c.r.sumOf { it[i].toDouble() } - r[i]))
        }
        return worst
    }

    /** A stand-in for the models: four positive, uneven estimates that depend on the input. */
    private val scrambled = Spleeter.Net { m ->
        List(4) { s -> FloatArray(m.size) { i -> m[i] * (0.2f + ((i * 7919 + s * 104729) % 97) / 97f) + s * 0.01f } }
    }

    @Test fun the_fft_inverts_itself_and_matches_a_direct_dft() {
        val n = 64
        val plan = FftPlan(n)
        val x = DoubleArray(n) { sin(it * 0.37) + 0.3 * cos(it * 1.9) }
        val re = x.copyOf(); val im = DoubleArray(n)
        plan.forward(re, im)
        for (k in 0 until n) {
            var dr = 0.0; var di = 0.0
            for (t in 0 until n) { dr += x[t] * cos(2 * PI * k * t / n); di -= x[t] * sin(2 * PI * k * t / n) }
            assertEquals(dr, re[k], 1e-9); assertEquals(di, im[k], 1e-9)
        }
        plan.inverse(re, im)
        for (t in 0 until n) { assertEquals(x[t], re[t], 1e-12); assertEquals(0.0, im[t], 1e-12) }
    }

    @Test fun the_window_is_the_periodic_hann() {
        assertEquals(0.0, Spleeter.window[0], 0.0)
        assertEquals(1.0, Spleeter.window[Spleeter.N_FFT / 2], 1e-15)
        // Periodic: w[1] equals w[N − 1], and there is no second zero at the end.
        assertEquals(Spleeter.window[1], Spleeter.window[Spleeter.N_FFT - 1], 1e-15)
        assertTrue(Spleeter.window[Spleeter.N_FFT - 1] > 0)
    }

    @Test fun frames_and_splits_follow_the_reference_padding() {
        // n = ceil((3072 + len) / 1024)
        assertEquals(4, Spleeter.frameCount(1))
        assertEquals(4, Spleeter.frameCount(1024))
        assertEquals(5, Spleeter.frameCount(1025))
        assertEquals(1, Spleeter.splitCount(1))
        assertEquals(1, Spleeter.splitCount(512L * 1024 - 3072))
        assertEquals(2, Spleeter.splitCount(512L * 1024 - 3071))
    }

    @Test fun the_four_stems_add_back_up_to_the_mix_across_a_split_boundary() {
        // 1.3 splits, so frames on both sides of the boundary and a short last split.
        val (l, r) = song(690_000)
        val c = run(l, r, scrambled)
        assertTrue(c.at.all { it == l.size })
        assertTrue("sum error ${sumError(c, l, r)}", sumError(c, l, r) < 1e-5)
        // And the stand-in really did split it: no stem is the whole mix.
        for (s in 0 until 4) assertTrue(c.l[s].indices.any { abs(c.l[s][it] - l[it]) > 0.01 })
    }

    @Test fun a_song_shorter_than_one_frame_still_comes_back_whole() {
        val (l, r) = song(500, seed = 2)
        val c = run(l, r, scrambled)
        assertTrue(c.at.all { it == 500 })
        assertTrue(sumError(c, l, r) < 1e-5)
    }

    @Test fun a_stem_that_gets_everything_is_the_mix_including_above_the_nets_bins() {
        // Everything to drums: its mask is 1 below 11 kHz and, carried by the mean, above it too.
        val (l, r) = song(100_000, seed = 3)
        val c = run(l, r, Spleeter.Net { m -> List(4) { s -> if (s == 1) m.copyOf() else FloatArray(m.size) } })
        var worst = 0.0; var rest = 0.0
        for (i in l.indices) {
            worst = maxOf(worst, abs(c.l[1][i] - l[i].toDouble()), abs(c.r[1][i] - r[i].toDouble()))
            for (s in listOf(0, 2, 3)) rest = maxOf(rest, abs(c.l[s][i].toDouble()), abs(c.r[s][i].toDouble()))
        }
        assertTrue("drums differ from the mix by $worst", worst < 1e-5)
        assertTrue("the other stems hold $rest", rest < 1e-5)
    }

    @Test fun the_net_sees_each_channels_magnitudes_in_its_own_half() {
        // Left silent, right a tone: every left magnitude is 0 and the right half isn't.
        val len = 50_000
        val l = FloatArray(len)
        val r = FloatArray(len) { (0.5 * sin(2 * PI * 1000 * it / Spleeter.RATE)).toFloat() }
        var leftMax = 0f; var rightMax = 0f
        run(l, r, Spleeter.Net { m ->
            val half = m.size / 2
            for (i in 0 until half) leftMax = maxOf(leftMax, m[i])
            for (i in half until m.size) rightMax = maxOf(rightMax, m[i])
            List(4) { m.copyOf() }
        })
        assertEquals(0f, leftMax, 1e-4f)
        assertTrue(rightMax > 100f)
    }
}
