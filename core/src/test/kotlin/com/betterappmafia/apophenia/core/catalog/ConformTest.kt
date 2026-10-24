package com.betterappmafia.apophenia.core.catalog

import com.betterappmafia.apophenia.core.pitch.Letter
import com.betterappmafia.apophenia.core.pitch.PitchName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConformTest {
    private fun key(l: Letter, alter: Int = 0, mode: String = "major") = KeyMode(PitchName(l, alter), mode)

    @Test fun `a 92 BPM layer in a 96 BPM stack gets shorter, and the label says so`() {
        val c = Conform.of(92.0, 92.0, false, null, 96.0, null)
        assertEquals(92.0 / 96.0, c.lengthFactor, 1e-9)
        assertEquals(0, c.semitones)
        assertEquals("92→96 BPM", c.label)
    }

    @Test fun `D dorian into E minor moves up two semitones`() {
        val c = Conform.of(null, null, false, key(Letter.D, mode = "dorian"), null, key(Letter.E, mode = "aeolian"))
        assertEquals(2, c.semitones)
        assertEquals("+2 st", c.label)
        assertEquals(1.0, c.lengthFactor, 0.0)
    }

    @Test fun `the shift takes the shortest way, and a tritone goes down`() {
        assertEquals(-1, Conform.of(null, null, false, key(Letter.C), null, key(Letter.B)).semitones)
        assertEquals(-6, Conform.of(null, null, false, key(Letter.C), null, key(Letter.F, 1)).semitones)
        assertEquals(5, Conform.of(null, null, false, key(Letter.C), null, key(Letter.F)).semitones)
        assertEquals(0, Conform.of(null, null, false, key(Letter.C, 1), null, key(Letter.D, -1)).semitones)
    }

    @Test fun `a double-time riff is not slowed to half speed`() {
        val c = Conform.of(184.0, 184.0, false, null, 92.0, null)
        assertTrue(c.isIdentity)
        assertEquals("", c.label)
        val d = Conform.of(180.0, 180.0, false, null, 96.0, null)
        assertEquals(90.0 / 96.0, d.lengthFactor, 1e-9)
        assertEquals("180→96 BPM (½×)", d.label)
    }

    @Test fun `free time, unknown tempo and unknown key change nothing`() {
        assertTrue(Conform.of(92.0, 92.0, true, null, 96.0, null).isIdentity)
        assertTrue(Conform.of(null, null, false, null, 96.0, key(Letter.C)).isIdentity)
        assertTrue(Conform.of(92.0, 92.0, false, key(Letter.D), null, null).let { it.semitones == 0 && it.lengthFactor == 1.0 })
    }

    @Test fun `a tempo range conforms from its middle`() {
        val c = Conform.of(88.0, 96.0, false, null, 92.0, null)
        assertTrue(c.isIdentity)
    }
}
