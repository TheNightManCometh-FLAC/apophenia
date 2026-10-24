package com.betterappmafia.apophenia.audio.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class LevelAnalysisTest {
    private val rate = 48000

    private fun sine(hz: Double, amp: Double, seconds: Double = 2.0) =
        FloatArray((rate * seconds).toInt()) { (amp * sin(2 * PI * hz * it / rate)).toFloat() }

    @Test
    fun `a half-scale sine reads minus 9 dB broadband and in its own octave band`() {
        val x = sine(1000.0, 0.5)
        assertEquals(-9.03, LevelAnalysis.rmsDb(x), 0.05)
        val bands = LevelAnalysis.octaveBandsDb(x, rate)
        val k = LevelAnalysis.OCTAVE_CENTRES.toList().indexOf(1000.0)
        assertEquals(-9.03, bands[k], 0.2)
        assertTrue("500 Hz band ${bands[k - 1]} should be far below", bands[k - 1] < -60)
        assertTrue("2 kHz band ${bands[k + 1]} should be far below", bands[k + 1] < -60)
    }

    @Test
    fun `a 41 Hz low E lands in the 31_5 Hz octave, not the 63 Hz one`() {
        val bands = LevelAnalysis.octaveBandsDb(sine(41.2, 0.25, 4.0), rate)
        assertEquals(20 * log10(0.25 / kotlin.math.sqrt(2.0)), bands[0], 0.3)
        assertTrue(bands[1] < bands[0] - 20)
    }

    @Test
    fun `octave bands of white noise add up to its broadband level`() {
        val rnd = Random(3)
        val x = FloatArray(rate * 4) { (rnd.nextFloat() * 2 - 1) * 0.1f }
        val total = LevelAnalysis.octaveBandsDb(x, rate).sumOf { 10.0.pow(it / 10) }
        // Bands cover 22 Hz–22.6 kHz of a 24 kHz Nyquist: about 94 % of white-noise power.
        assertEquals(LevelAnalysis.rmsDb(x) + 10 * log10(22600.0 / 24000.0), 10 * log10(total), 0.3)
    }

    @Test
    fun `noise floor finds the quiet gap between two loud notes`() {
        val x = sine(220.0, 0.5, 3.0)
        for (i in rate until 2 * rate) x[i] *= 0.001f
        assertEquals(-69.0, LevelAnalysis.floorDb(x, rate), 1.0)
        assertEquals(20 * log10(0.5), LevelAnalysis.peakDb(x), 0.01)
    }

    @Test
    fun `the FFT of an impulse is flat`() {
        val re = DoubleArray(16).also { it[0] = 1.0 }
        val im = DoubleArray(16)
        LevelAnalysis.fft(re, im)
        for (k in 0 until 16) assertEquals(1.0, re[k], 1e-12)
    }
}
