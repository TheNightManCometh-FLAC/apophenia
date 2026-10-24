package com.betterappmafia.apophenia.core.pitch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpelledPitchTest {

    @Test
    fun `middle C is MIDI 60`() {
        assertEquals(60, SpelledPitch(Letter.C, 0, 4).midi)
    }

    @Test
    fun `B sharp 3 is MIDI 60 because the octave belongs to the letter`() {
        assertEquals(60, SpelledPitch(Letter.B, 1, 3).midi)
    }

    @Test
    fun `C flat 4 is MIDI 59 because the octave belongs to the letter`() {
        assertEquals(59, SpelledPitch(Letter.C, -1, 4).midi)
    }

    @Test
    fun `A4 is MIDI 69`() {
        assertEquals(69, SpelledPitch(Letter.A, 0, 4).midi)
    }

    @Test
    fun `C double flat has pitch class 10`() {
        assertEquals(10, SpelledPitch(Letter.C, -2, 4).pitchClass)
    }

    @Test
    fun `B double sharp has pitch class 1`() {
        assertEquals(1, SpelledPitch(Letter.B, 2, 4).pitchClass)
    }

    @Test
    fun `B flat and A sharp share a key but are different pitches`() {
        val bFlat = SpelledPitch(Letter.B, -1, 3)
        val aSharp = SpelledPitch(Letter.A, 1, 3)
        assertEquals(bFlat.midi, aSharp.midi)
        assert(bFlat != aSharp)
    }

    @Test
    fun `triple accidentals are refused`() {
        assertFailsWith<IllegalArgumentException> { SpelledPitch(Letter.F, 3, 4) }
    }
}

class PitchNamingTest {

    @Test
    fun `every accidental token reads back as the same alter`() {
        for (a in Accidental.entries) assertEquals(a, Accidental.parse(a.token))
    }

    @Test
    fun `pitch names round trip through their ASCII form on all 35 roots`() {
        for (l in Letter.entries) for (alter in -2..2) {
            val n = PitchName(l, alter)
            assertEquals(n, PitchName.parse(n.toString()))
        }
    }

    @Test
    fun `an explicit natural parses as the plain letter`() {
        assertEquals(PitchName(Letter.F), PitchName.parse("Fn"))
        assertEquals("F", PitchName.parse("Fn").toString())
    }

    @Test
    fun `spelled pitches round trip including negative octaves`() {
        for (s in listOf("C4", "Bb3", "F#-1", "Ebb5", "Gx2", "Cb4", "B#3")) {
            assertEquals(s, SpelledPitch.parse(s).toString())
        }
    }

    @Test
    fun `junk is refused rather than guessed`() {
        for (s in listOf("", "H", "c4", "C#", "Cbbb4", "C#x4")) {
            assertFailsWith<IllegalArgumentException> { SpelledPitch.parse(s) }
        }
    }

    @Test
    fun `A4 sounds at the reference frequency`() {
        assertEquals(440.0, SpelledPitch.parse("A4").frequency(), 1e-9)
        assertEquals(442.0, SpelledPitch.parse("A4").frequency(a4Hz = 442.0), 1e-9)
    }

    @Test
    fun `middle C is 261 point 626 Hz at A440`() {
        assertEquals(261.6256, SpelledPitch.parse("C4").frequency(), 1e-4)
    }

    @Test
    fun `a 100 cent offset raises the frequency by one equal-tempered semitone`() {
        assertEquals(
            SpelledPitch.parse("A#4").frequency(),
            SpelledPitch.parse("A4").frequency(centsOffset = 100.0), 1e-9,
        )
    }

    @Test
    fun `the C3 octave label calls middle C C3 while MIDI stays 60`() {
        val c = SpelledPitch.parse("C4")
        assertEquals("C4", OctaveLabel.MIDDLE_C_IS_C4.label(c))
        assertEquals("C3", OctaveLabel.MIDDLE_C_IS_C3.label(c))
        assertEquals(60, c.midi)
    }

    @Test
    fun `unkeyed pitch classes take the default spellings C Db D Eb E F F# G Ab A Bb B`() {
        assertEquals(
            "C Db D Eb E F F# G Ab A Bb B",
            (0..11).joinToString(" ") { PitchClass(it).defaultSpelling().toString() },
        )
    }

    @Test
    fun `letters wrap round the octave`() {
        assertEquals(Letter.C, Letter.B + 1)
        assertEquals(Letter.B, Letter.C + -1)
        assertEquals(Letter.A, Letter.C + 12)
    }
}
