package com.betterappmafia.apophenia.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class ImportAudioTest {
    private fun resample(input: FloatArray, inRate: Int, chunk: Int = 1000): FloatArray {
        val r = Resampler(inRate)
        val out = ArrayList<Float>()
        var i = 0
        while (i < input.size) {
            val n = minOf(chunk, input.size - i)
            r.process(input.copyOfRange(i, i + n), n) { buf, k -> for (j in 0 until k) out += buf[j] }
            i += n
        }
        return out.toFloatArray()
    }

    /** Zero crossings per second of the middle half, so edges don't count. */
    private fun frequency(x: FloatArray, rate: Int): Double {
        val a = x.size / 4
        val b = 3 * x.size / 4
        var crossings = 0
        for (i in a + 1 until b) if ((x[i - 1] < 0f) != (x[i] < 0f)) crossings++
        return crossings / 2.0 / ((b - a).toDouble() / rate)
    }

    @Test fun `44_1 kHz becomes 48 kHz with the same pitch and duration`() {
        val inRate = 44_100
        val tone = FloatArray(inRate * 2) { (0.5 * sin(2 * PI * 440.0 * it / inRate)).toFloat() }
        val out = resample(tone, inRate)
        assertEquals(96_000.0, out.size.toDouble(), 10.0)
        assertEquals(440.0, frequency(out, 48_000), 1.0)
        assertTrue(out.all { abs(it) <= 0.52f })
    }

    @Test fun `chunk size doesn't change the result`() {
        val inRate = 22_050
        val tone = FloatArray(inRate) { (0.3 * sin(2 * PI * 220.0 * it / inRate)).toFloat() }
        val a = resample(tone, inRate, 37)
        val b = resample(tone, inRate, 4096)
        assertEquals(a.size, b.size)
        for (i in a.indices) assertEquals(a[i], b[i], 1e-6f)
    }

    @Test fun `96 kHz comes down to 48 kHz without changing pitch`() {
        val inRate = 96_000
        val tone = FloatArray(inRate) { (0.5 * sin(2 * PI * 1000.0 * it / inRate)).toFloat() }
        val out = resample(tone, inRate)
        assertEquals(48_000.0, out.size.toDouble(), 10.0)
        assertEquals(1000.0, frequency(out, 48_000), 2.0)
    }

    @Test fun `the writer makes a take the app can read`() {
        val f = File.createTempFile("imp", ".wav")
        WavWriter(f).use { w -> w.write(FloatArray(48_000) { if (it % 2 == 0) 0.5f else -0.5f }) }
        val info = Wav.info(f)!!
        assertEquals(48_000, info.rate)
        assertEquals(1, info.channels)
        assertEquals(24, info.bits)
        assertEquals(48_000L, info.frames)
        f.delete()
    }
}
