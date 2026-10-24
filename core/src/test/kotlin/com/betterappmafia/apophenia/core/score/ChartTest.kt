package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.pitch.SpelledPitch
import com.betterappmafia.apophenia.core.rhythm.Duration
import com.betterappmafia.apophenia.core.rhythm.Tempo
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import com.betterappmafia.apophenia.core.sheet.ChordText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartTest {
    private fun p(s: String) = SpelledPitch.parse(s)
    private val q = Duration.QUARTER

    private fun sheet(meter: String, vararg events: Event) =
        Score("", TimeSignature.parse(meter), KeyMode.parseOrNull("D dorian"), Tempo(96.0), listOf(Part("Voice", Clef.TREBLE, null, listOf(Bar(listOf(events.toList()))))))

    @Test fun the_chart_shows_one_slot_per_beat_with_the_symbols_starting_in_it() {
        val s = sheet("4/4", Event(q, listOf(Note(p("F4"))), chord = "F"), Event(q), Event(Duration.EIGHTH, chord = "C"), Event(Duration.EIGHTH, chord = "C/E"), Event(q))
        val beats = ScoreEdit.chart(s, s.parts[0]).single()
        assertEquals(listOf(listOf("F"), emptyList(), listOf("C", "C/E"), emptyList()), beats.map { it.chords })
        // 6/8: two beats of a dotted quarter.
        val six = sheet("6/8", Event(Duration.HALF.dotted()))
        assertEquals(listOf(0L, 1440L), ScoreEdit.chart(six, six.parts[0]).single().map { it.tick })
    }

    @Test fun a_chord_on_beat_3_of_a_whole_note_splits_it_into_two_tied_halves() {
        val s = sheet("4/4", Event(Duration.WHOLE, listOf(Note(p("D4"))), chord = "Dm7"))
        val out = ScoreEdit.setChordAt(s, 0, 0, 1920, "G7")
        val evs = out.parts[0].bars[0].voices[0]
        assertEquals(listOf(Duration.HALF, Duration.HALF), evs.map { it.duration })
        assertEquals(listOf("Dm7", "G7"), evs.map { it.chord })
        assertTrue(evs[0].notes.single().tie)
        assertFalse(evs[1].notes.single().tie)
        // Taking it off leaves the split (the notes still sound the same).
        assertEquals(null, ScoreEdit.setChordAt(out, 0, 0, 1920, null).parts[0].bars[0].voices[0][1].chord)
    }

    @Test fun real_chord_symbols_read_and_typing_slips_dont() {
        for (c in listOf("Dm7", "E7#9", "C/E", "N.C.", "NC", "F#m7b5", "Bb13(#11)", "Cmaj7", "C6/9", "Csus4", "C7sus4", "Ebmaj7#11",
            "G7alt", "Am(maj7)", "C+", "Cdim7", "Cø7", "C-7", "Cadd9", "C5", "C7(b9,#11)", "C7b9#11", "Dbmaj9", "F#7/A#", "C°", "Cm6", "C69", "C9sus4", "Cno3"))
            assertTrue("$c should read", ChordText.readable(c))
        for (c in listOf("H7", "Cxyz", "C/", "", "C/H", "Dm7/"))
            assertFalse("$c shouldn't read", ChordText.readable(c))
    }
}
