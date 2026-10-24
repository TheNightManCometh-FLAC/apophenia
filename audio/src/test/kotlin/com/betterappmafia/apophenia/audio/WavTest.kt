package com.betterappmafia.apophenia.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WavTest {
    private val dir: File = createTempDirectory("wavtest").toFile()

    /** The engine's layout: 44-byte header, 24-bit mono. [declared] is what the header claims. */
    private fun engineWav(samples: IntArray, declared: Int = samples.size * 3, tornBytes: Int = 0): File {
        val bb = ByteBuffer.allocate(44 + samples.size * 3 + tornBytes).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()).putInt(36 + declared).put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(48000).putInt(144000).putShort(3).putShort(24)
        bb.put("data".toByteArray()).putInt(declared)
        for (s in samples) bb.put((s and 0xFF).toByte()).put((s shr 8 and 0xFF).toByte()).put((s shr 16 and 0xFF).toByte())
        repeat(tornBytes) { bb.put(0x11) }
        return File(dir, "t${System.nanoTime()}.wav").apply { writeBytes(bb.array()) }
    }

    @Test fun `analysis reads a stereo file as mono at half the rate, stopping at the limit`() {
        val f = File(dir, "st${System.nanoTime()}.wav")
        // 1 s of stereo: left 0.5, right -0.1 on even frames, 0.3 and 0.1 on odd ones.
        WavWriter(f, 48000, 2).use { w -> w.write(FloatArray(96000) { i -> val frame = i / 2; if (i % 2 == 0) (if (frame % 2 == 0) 0.5f else 0.3f) else (if (frame % 2 == 0) -0.1f else 0.1f) }) }
        val (x, rate) = Wav.readMono(f, 24000, maxSeconds = 10)!!
        assertEquals(24000, rate)
        assertEquals(24000, x.size)
        // Each output sample is two frames averaged: ((0.5 - 0.1) / 2 + (0.3 + 0.1) / 2) / 2 = 0.2.
        assertTrue(x.all { kotlin.math.abs(it - 0.2f) < 1e-3f })
        assertEquals(0, Wav.readMono(f, 24000, maxSeconds = 0)!!.first.size)
    }

    @Test fun `stems read a take as left and right blocks, a mono take on both sides`() {
        val f = File(dir, "st${System.nanoTime()}.wav")
        WavWriter(f, 48000, 2).use { w -> w.write(FloatArray(2 * 20000) { i -> if (i % 2 == 0) 0.5f else -0.25f }) }
        var n = 0; var ok = true
        Wav.stream(f, 15000) { l, r, c -> for (i in 0 until c) ok = ok && kotlin.math.abs(l[i] - 0.5f) < 1e-6f && kotlin.math.abs(r[i] + 0.25f) < 1e-6f; n += c }
        assertEquals(15000, n)
        assertTrue(ok)
        var mono = 0
        Wav.stream(engineWav(IntArray(1000) { 4_194_304 }), Long.MAX_VALUE) { l, r, c -> for (i in 0 until c) if (l[i] == 0.5f && r[i] == 0.5f) mono++ }
        assertEquals(1000, mono)
    }

    @Test fun `stems mix sample by sample into a stereo file as long as the longest`() {
        val a = File(dir, "a${System.nanoTime()}.wav"); val b = File(dir, "b${System.nanoTime()}.wav")
        WavWriter(a, 48000, 2).use { w -> w.write(FloatArray(2 * 10000) { i -> if (i % 2 == 0) 0.25f else -0.125f }) }
        WavWriter(b, 48000, 2).use { w -> w.write(FloatArray(2 * 20000) { 0.25f }) }
        val out = File(dir, "m${System.nanoTime()}.wav")
        Wav.mix(listOf(a, b), out)
        assertEquals(20000L, Wav.info(out)!!.frames)
        var n = 0; var ok = true
        Wav.stream(out, Long.MAX_VALUE) { l, r, c ->
            for (i in 0 until c) {
                val both = n + i < 10000
                ok = ok && kotlin.math.abs(l[i] - (if (both) 0.5f else 0.25f)) < 1e-6f && kotlin.math.abs(r[i] - (if (both) 0.125f else 0.25f)) < 1e-6f
            }
            n += c
        }
        assertTrue(ok)
    }

    @Test fun `a finished 24-bit take reports its rate, format and length`() {
        val info = Wav.info(engineWav(IntArray(4800)))!!
        assertEquals(48000, info.rate)
        assertEquals(1, info.channels)
        assertEquals(24, info.bits)
        assertEquals(44L, info.dataOffset)
        assertEquals(4800L, info.frames)
    }

    @Test fun `a crashed take whose header still says zero bytes is measured from the file`() {
        assertEquals(1000L, Wav.info(engineWav(IntArray(1000), declared = 0))!!.frames)
    }

    @Test fun `peaks find the loud column and read negative 24-bit samples correctly`() {
        val s = IntArray(4000)
        s[3500] = -4194304   // -0.5 full scale, in the last quarter
        val p = Wav.peaks(engineWav(s), 4)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 0.5f), p, 1e-6f)
    }

    @Test fun `repair sets the header sizes, drops a torn frame and keeps every audio byte`() {
        val samples = IntArray(960) { (it * 1000) - 480000 }
        val part = engineWav(samples, declared = 0, tornBytes = 2)
        val audioBefore = part.readBytes().copyOfRange(44, 44 + 960 * 3)
        val target = File(dir, "fixed.wav")

        assertEquals(960L, Wav.repairPart(part, target))
        assertTrue(target.exists())
        val bytes = target.readBytes()
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(36 + 960 * 3, bb.getInt(4))
        assertEquals(960 * 3, bb.getInt(40))
        assertEquals(44 + 960 * 3, bytes.size)
        assertArrayEquals(audioBefore, bytes.copyOfRange(44, bytes.size))
    }

    @Test fun `a file that is not a WAV is refused, not repaired`() {
        val junk = File(dir, "junk.part").apply { writeBytes(ByteArray(100) { 7 }) }
        assertNull(Wav.info(junk))
        assertNull(Wav.repairPart(junk, File(dir, "junk.wav")))
        assertTrue(junk.exists())
    }

    @Test fun `a WAV with an extra chunk before the data still opens`() {
        val bb = ByteBuffer.allocate(44 + 12 + 6).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()).putInt(36 + 12 + 6).put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(44100).putInt(88200).putShort(2).putShort(16)
        bb.put("LIST".toByteArray()).putInt(4).put("INFO".toByteArray())
        bb.put("data".toByteArray()).putInt(6).putShort(1).putShort(2).putShort(3)
        val info = Wav.info(File(dir, "list.wav").apply { writeBytes(bb.array()) })
        assertNotNull(info)
        assertEquals(3L, info!!.frames)
        assertEquals(16, info.bits)
    }
}
