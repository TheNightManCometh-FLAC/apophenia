package com.betterappmafia.apophenia.core.instrument

import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.pitch.SpelledPitch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InstrumentTest {

    private fun p(s: String) = SpelledPitch.parse(s)

    @Test
    fun `string 1 on a guitar is the high E and string 6 the low E`() {
        assertEquals(p("E4"), Tunings.GUITAR_STANDARD.open(1))
        assertEquals(p("E2"), Tunings.GUITAR_STANDARD.open(6))
        assertFailsWith<IllegalArgumentException> { Tunings.GUITAR_STANDARD.open(7) }
    }

    @Test
    fun `MusicXML staff-tuning lines count from the lowest string, opposite to string numbers`() {
        assertEquals(1, Tunings.GUITAR_STANDARD.staffTuningLine(6))
        assertEquals(6, Tunings.GUITAR_STANDARD.staffTuningLine(1))
        assertEquals(1, Tunings.BASS_5_STRING.staffTuningLine(5))
    }

    @Test
    fun `the ukulele keeps string order, not pitch order`() {
        val u = Tunings.UKULELE
        assertEquals(p("A4"), u.open(1))
        assertEquals(p("G4"), u.open(4))
        // re-entrant: string 4 sounds above string 3
        assert(u.open(4).midi > u.open(3).midi)
    }

    @Test
    fun `presets resolve to the notes the plan names`() {
        assertEquals("D2 A2 D3 G3 B3 E4", Tunings.GUITAR_DROP_D.strings.joinToString(" "))
        assertEquals("D2 A2 D3 F#3 A3 D4", Tunings.GUITAR_OPEN_D.strings.joinToString(" "))
        assertEquals("B1 E2 A2 D3 G3 B3 E4", Tunings.GUITAR_7_STRING.strings.joinToString(" "))
        assertEquals("B0 E1 A1 D2 G2 C3", Tunings.BASS_6_STRING.strings.joinToString(" "))
        assertEquals(Tunings.all.size, Tunings.all.map { it.id }.toSet().size)
    }

    @Test
    fun `tab pitch is open string plus capo plus fret`() {
        val guitar = Instrument(Tunings.GUITAR_STANDARD, capo = 2)
        val n = guitar.tabNote(string = 5, fret = 3)
        assertEquals(5, n.string)
        assertEquals(3, n.fret)
        assertEquals(45 + 2 + 3, n.pitch.midi)
        assertEquals(p("D3"), n.pitch)
    }

    @Test
    fun `a tab note takes the spelling its context gives`() {
        val guitar = Instrument(Tunings.GUITAR_STANDARD)
        assertEquals(p("A#2"), guitar.tabNote(6, 6, PitchName.parse("A#")).pitch)
        assertEquals(p("Bb2"), guitar.tabNote(6, 6).pitch)
        assertEquals(p("B#2"), guitar.tabNote(5, 3, PitchName.parse("B#")).pitch)
        assertFailsWith<IllegalArgumentException> { guitar.tabNote(6, 6, PitchName.parse("B")) }
    }

    @Test
    fun `guitar and bass are written an octave above sounding`() {
        val guitar = Instrument(Tunings.GUITAR_STANDARD)
        assertEquals(p("E3"), guitar.written(p("E2")))
        assertEquals(p("E2"), guitar.sounding(p("E3")))
        val bass = Instrument(Tunings.BASS_STANDARD)
        assertEquals(p("E2"), bass.written(p("E1")))
        val uke = Instrument(Tunings.UKULELE, frets = 18, writtenAboveSounding = null)
        assertEquals(p("A4"), uke.written(p("A4")))
    }

    @Test
    fun `frets off the neck are refused`() {
        val guitar = Instrument(Tunings.GUITAR_STANDARD, frets = 22)
        assertFailsWith<IllegalArgumentException> { guitar.tabNote(1, 23) }
        assertFailsWith<IllegalArgumentException> { Instrument(Tunings.GUITAR_STANDARD, capo = 22) }
    }
}
