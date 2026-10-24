package com.betterappmafia.apophenia.core.catalog

import com.betterappmafia.apophenia.core.pitch.Letter
import com.betterappmafia.apophenia.core.pitch.PitchName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyModeTest {
    @Test fun `D Dorian is stored as an ASCII token and shown with its mode name`() {
        val k = KeyMode(PitchName(Letter.D), "dorian")
        assertEquals("D dorian", k.token)
        assertEquals("D Dorian", k.display)
        assertEquals("D DOR", k.short)
    }

    @Test fun `sharps and flats are ASCII in storage and real accidentals on screen`() {
        val k = KeyMode(PitchName(Letter.F, 1), "aeolian")
        assertEquals("F# aeolian", k.token)
        assertEquals("F♯ minor", k.display)
        assertEquals("B♭ major", KeyMode(PitchName(Letter.B, -1), "major").display)
    }

    @Test fun `C major and A minor are different keys`() {
        assertNotEquals(KeyMode(PitchName(Letter.C), "major").token, KeyMode(PitchName(Letter.A), "aeolian").token)
    }

    @Test fun `every token round trips for every tonic and every catalog scale`() {
        for (t in KeyMode.TONICS) for (s in Scales.all) {
            val k = KeyMode(t, s.id)
            assertEquals(k, KeyMode.parseOrNull(k.token))
        }
    }

    @Test fun `a malformed or unknown token is refused rather than guessed`() {
        assertNull(KeyMode.parseOrNull(null))
        assertNull(KeyMode.parseOrNull(""))
        assertNull(KeyMode.parseOrNull("D"))
        assertNull(KeyMode.parseOrNull("H major"))
        assertNull(KeyMode.parseOrNull("D blorpian"))
    }

    @Test fun `the picker offers 21 tonics, each letter natural, flat and sharp`() {
        assertEquals(21, KeyMode.TONICS.size)
        assertEquals(21, KeyMode.TONICS.toSet().size)
    }

    @Test fun `a mode without a short form uses the first three letters of its name`() {
        assertEquals("E PHR", KeyMode(PitchName(Letter.E), "phrygian-dominant").short)
    }
}
