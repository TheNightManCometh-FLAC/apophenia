package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.instrument.Instrument
import com.betterappmafia.apophenia.core.instrument.Tunings
import com.betterappmafia.apophenia.core.pitch.SpelledPitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotationTest {
    private fun p(s: String) = SpelledPitch.parse(s)

    @Test fun notes_sit_on_their_lines_and_spaces() {
        // Treble: E4 bottom line, B4 middle line, F5 top line, middle C one ledger line below.
        assertEquals(0, Notation.step(Clef.TREBLE, p("E4")))
        assertEquals(4, Notation.step(Clef.TREBLE, p("B4")))
        assertEquals(8, Notation.step(Clef.TREBLE, p("F5")))
        assertEquals(-2, Notation.step(Clef.TREBLE, p("C4")))
        // B#3 and C4 are the same key but different places: B#3 sits a step lower.
        assertEquals(-3, Notation.step(Clef.TREBLE, p("B#3")))
        // Bass: G2 bottom line, D3 middle, A3 top; middle C one ledger line above.
        assertEquals(0, Notation.step(Clef.BASS, p("G2")))
        assertEquals(4, Notation.step(Clef.BASS, p("D3")))
        assertEquals(10, Notation.step(Clef.BASS, p("C4")))
        // Alto: middle C on the middle line.
        assertEquals(4, Notation.step(Clef.ALTO, p("C4")))
    }

    @Test fun guitar_and_bass_are_written_an_octave_up() {
        val guitar = Part("Guitar", Clef.TREBLE_8VB, Instrument(Tunings.GUITAR_STANDARD))
        // Low E (E2 sounding) is written E3: three ledger lines below the treble staff.
        assertEquals(-7, Notation.step(guitar.clef, Notation.written(guitar, p("E2"))))
        assertEquals(listOf(-2, -4, -6), Notation.ledgers(-7))
        val bass = Part("Bass", Clef.BASS, Instrument(Tunings.BASS_STANDARD))
        // The bass's low E (E1) is written E2: one ledger line below the bass staff.
        assertEquals(-2, Notation.step(bass.clef, Notation.written(bass, p("E1"))))
        assertEquals(listOf(-2), Notation.ledgers(-2))
        assertTrue(Notation.ledgers(8).isEmpty())
        assertEquals(listOf(10, 12), Notation.ledgers(12))
    }

    @Test fun key_signatures_come_from_the_parent_major() {
        fun f(k: String) = Notation.fifths(KeyMode.parseOrNull(k))
        assertEquals(0, f("C major"))
        assertEquals(2, f("D major"))
        assertEquals(-2, f("Bb major"))
        assertEquals(0, f("A aeolian"))
        assertEquals(0, f("D dorian"))          // the white notes
        assertEquals(2, f("F# phrygian"))       // D major's notes
        assertEquals(-1, f("C mixolydian"))     // F major's notes
        assertEquals(1, f("C lydian"))          // G major's notes
        assertEquals(-3, f("C harmonic-minor")) // takes C minor's signature; the B natural goes in the bars
        assertEquals(-6, f("Gb major"))
        assertEquals(6, f("F# major"))
        assertEquals(7, f("C# major"))
        assertEquals(0, f("G# major"))          // eight sharps: no signature, accidentals in the bars
        assertEquals(0, Notation.fifths(null))
    }

    @Test fun signatures_are_placed_like_printed_music() {
        // D major treble: F# on the top line, C# in the third space.
        assertEquals(listOf(1 to 8, 1 to 5), Notation.signature(Clef.TREBLE, 2))
        // B-flat major bass: B-flat on the second line, E-flat in the third space.
        assertEquals(listOf(-1 to 2, -1 to 5), Notation.signature(Clef.BASS, -2))
        // E major bass: F3 fourth line, C3 second space, G3 top space, D3 middle line.
        assertEquals(listOf(6, 3, 7, 4), Notation.signature(Clef.BASS, 4).map { it.second })
        assertEquals(7, Notation.signature(Clef.TREBLE, -7).size)
    }

    @Test fun accidentals_last_the_bar_and_respect_the_signature() {
        // G major (F#): F#4 needs nothing, F4 needs a natural, a second F4 then nothing, F#4 a sharp again.
        val bar = listOf("F#4", "F4", "F4", "F#4", "F#5").map { listOf(p(it)) to listOf(false) }
        assertEquals(listOf(listOf(null), listOf(0), listOf(null), listOf(1), listOf(null)), Notation.accidentals(bar, 1))
        // C major: Bb4 flat; a tied-in Bb shows nothing.
        assertEquals(listOf(listOf<Int?>(null), listOf<Int?>(-1)), Notation.accidentals(listOf(listOf(p("Bb4")) to listOf(true), listOf(p("Bb3")) to listOf(false)), 0))
    }

    @Test fun stems_point_away_from_the_side_the_notes_are_on() {
        assertTrue(Notation.stemUp(listOf(0)))       // bottom line: up
        assertTrue(Notation.stemUp(listOf(3)))
        assertFalse(Notation.stemUp(listOf(4)))      // the middle line: down
        assertFalse(Notation.stemUp(listOf(6)))      // above the middle: down
        assertFalse(Notation.stemUp(listOf(2, 9)))   // the top note is further out: down
    }

    @Test fun a_tap_on_the_staff_gives_the_pitch_with_the_key_and_written_octave() {
        val piano = Part("Piano", Clef.TREBLE)
        assertEquals("F#5", Notation.pitchAt(piano, 8, 1).toString())     // top line in G major
        assertEquals("C4", Notation.pitchAt(piano, -2, 1).toString())     // middle C, no sharp in G major
        assertEquals("Bb4", Notation.pitchAt(piano, 4, -1).toString())    // middle line in F major
        val bass = Part("Bass", Clef.BASS, Instrument(Tunings.BASS_STANDARD))
        assertEquals("E1", Notation.pitchAt(bass, -2, 0).toString())      // written E2 under the staff sounds E1
    }
}
