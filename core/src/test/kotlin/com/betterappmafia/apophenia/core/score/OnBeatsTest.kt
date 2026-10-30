package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.analysis.BasicPitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class OnBeatsTest {
    private fun note(t: Double, len: Double = 0.1) = BasicPitch.TimedNote(t, t + len, 60, 0.5, null)

    /** A take that speeds up from 100 to about 120 BPM over 12 beats, first downbeat at 1.0 s. */
    private val beats = run {
        val out = ArrayList<Double>(); var t = 1.0
        for (i in 0 until 13) { out += t; t += 0.6 - i * 0.008 }
        out
    }

    @Test fun notes_on_drifting_beats_land_on_the_steady_grid() {
        // A note on every beat and one halfway to the next.
        val notes = beats.dropLast(1).flatMapIndexed { i, b -> listOf(note(b), note((b + beats[i + 1]) / 2)) }
        val period = 60.0 / 110
        val placed = FromNotes.onBeats(notes, beats, 110.0)
        placed.forEachIndexed { k, n -> assertEquals("note $k", 1.0 + k * period / 2, n.start, 1e-9) }
        // Without it, the last note is more than a sixteenth off the steady grid.
        assert(kotlin.math.abs(notes.last().start - (1.0 + 23 * period / 2)) > period / 4)
    }

    @Test fun a_tempo_counted_double_or_half_still_follows_the_beats() {
        val notes = listOf(note(beats[4]), note(beats[8]))
        // Each found beat is two beats at the doubled tempo.
        val doubled = FromNotes.onBeats(notes, beats, 220.0)
        assertEquals(1.0 + 8 * 60.0 / 220, doubled[0].start, 1e-9)
        assertEquals(1.0 + 16 * 60.0 / 220, doubled[1].start, 1e-9)
    }

    @Test fun a_tempo_that_matches_no_beat_spacing_leaves_the_notes_alone() {
        val notes = listOf(note(2.0))
        assertSame(notes, FromNotes.onBeats(notes, beats, 87.0))
        assertSame(notes, FromNotes.onBeats(notes, listOf(1.0), 110.0))
    }
}
