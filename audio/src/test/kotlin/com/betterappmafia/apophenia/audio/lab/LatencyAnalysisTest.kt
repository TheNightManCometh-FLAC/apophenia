package com.betterappmafia.apophenia.audio.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LatencyAnalysisTest {
    private val rate = 48000
    private val burst = LatencyAnalysis.burst()

    /** Capture with the burst at [start] + [lag], passed through a crude speaker/mic filter, plus noise. */
    private fun capture(start: Int, lag: Int, gain: Float, noise: Float, seed: Int = 1): FloatArray {
        val x = FloatArray(start + rate)
        val rnd = Random(seed)
        for (i in x.indices) x[i] = noise * (rnd.nextFloat() * 2 - 1)
        var lp = 0f
        var prev = 0f
        for (i in burst.indices) {
            lp += 0.4f * (burst[i] - lp)          // speaker roll-off
            val hp = lp - prev; prev = lp          // small-speaker bass loss
            x[start + lag + i] += gain * hp
        }
        return x
    }

    @Test
    fun `a filtered burst 12 ms late is found within 2 frames of 576`() {
        val x = capture(start = 24000, lag = 576, gain = 0.2f, noise = 0.005f)
        val d = LatencyAnalysis.detect(x, 24000, burst, maxLag = rate * 9 / 10)
        assertNotNull(d)
        assertTrue("lag ${d!!.lagFrames} should be 576 ± 2 (filter group delay)", kotlin.math.abs(d.lagFrames - 576) <= 2)
        assertTrue("confidence ${d.confidence} should clear the threshold", d.confidence >= 8.0)
    }

    @Test
    fun `an inverted burst is still found and reported as inverted`() {
        val x = capture(start = 0, lag = 9600, gain = -0.2f, noise = 0.005f)
        val d = LatencyAnalysis.detect(x, 0, burst, maxLag = rate * 9 / 10)!!
        assertTrue("lag ${d.lagFrames} should be 9600 ± 2", kotlin.math.abs(d.lagFrames - 9600) <= 2)
        assertTrue(d.inverted)
    }

    @Test
    fun `pure noise with no burst stays below the confidence threshold`() {
        val x = capture(start = 0, lag = 0, gain = 0f, noise = 0.1f, seed = 7)
        val d = LatencyAnalysis.detect(x, 0, burst, maxLag = rate * 9 / 10)!!
        assertTrue("noise-only confidence ${d.confidence} must be below 8", d.confidence < 8.0)
        val summary = LatencyAnalysis.summarize(listOf(d), rate)
        assertNull("a run with no accepted burst has no median", summary.medianMs)
    }

    @Test
    fun `summary takes the median of accepted bursts and ignores rejected ones`() {
        val ds = listOf(
            LatencyAnalysis.Detection(480, 30.0, false),
            LatencyAnalysis.Detection(482, 30.0, false),
            LatencyAnalysis.Detection(5000, 3.0, false), // lost in noise
            LatencyAnalysis.Detection(486, 30.0, false),
            null,
        )
        val s = LatencyAnalysis.summarize(ds, rate)
        assertEquals(3, s.acceptedLagsMs.size)
        assertEquals(482 * 1000.0 / rate, s.medianMs!!, 1e-9)
        assertEquals(3 * 1000.0 / rate, s.halfSpreadMs!!, 1e-9)
    }

    @Test
    fun `a search window that runs off the capture returns nothing`() {
        assertNull(LatencyAnalysis.detect(FloatArray(500), 0, burst, maxLag = 100))
    }
}
