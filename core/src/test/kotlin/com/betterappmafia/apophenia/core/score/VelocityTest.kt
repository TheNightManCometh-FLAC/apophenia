package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.pitch.SpelledPitch
import com.betterappmafia.apophenia.core.rhythm.Duration
import com.betterappmafia.apophenia.core.rhythm.Tempo
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VelocityTest {
    private fun p(s: String) = SpelledPitch.parse(s)
    private val q = Duration.QUARTER

    private fun sheet(vararg bars: List<Event>) =
        Score("", TimeSignature.parse("4/4"), KeyMode.parseOrNull("C major"), Tempo(60.0), listOf(Part("Piano", Clef.TREBLE, null, bars.map { Bar(listOf(it)) })))

    @Test fun a_note_velocity_survives_the_stored_form_and_older_scores_have_none() {
        val s = sheet(listOf(Event(q, listOf(Note(p("C4"), velocity = 101), Note(p("E4")))), Event(Duration.HALF.dotted())))
        val text = ScoreText.encode(s)
        assertTrue(text, "C4[vel=101]" in text)
        val back = ScoreText.decode(text)
        assertEquals(listOf(101, null), back.parts[0].bars[0].voices[0][0].notes.map { it.velocity })
        val old = ScoreText.encode(sheet(listOf(Event(q, listOf(Note(p("C4")))), Event(Duration.HALF.dotted())))).replace("score ${ScoreText.VERSION}", "score 3")
        assertNull(ScoreText.decode(old).parts[0].bars[0].voices[0][0].notes[0].velocity)
    }

    @Test fun a_note_with_its_own_velocity_plays_at_it_whatever_the_dynamics_say() {
        val s = sheet(listOf(Event(q, listOf(Note(p("C4"), velocity = 20, effects = setOf(Effect.ACCENT))), dynamic = Dynamic.FF), Event(q, listOf(Note(p("D4")))), Event(Duration.HALF)))
        assertEquals(listOf(20, 112), ScorePlayback.notes(s, s.parts[0]).map { it.velocity })
    }

    @Test fun the_lane_sets_velocity_on_the_whole_tied_note_and_shows_the_written_level_otherwise() {
        // A whole note tied over the barline into a half.
        val s = sheet(
            listOf(Event(Duration.WHOLE, listOf(Note(p("G4"), tie = true)), dynamic = Dynamic.P)),
            listOf(Event(Duration.HALF, listOf(Note(p("G4")))), Event(Duration.HALF, listOf(Note(p("A4"))), dynamic = Dynamic.F)),
        )
        val before = PianoRoll.notes(s, s.parts[0])
        assertEquals(listOf(49, 96), before.map { PianoRoll.playedVelocity(s, 0, it) })
        val g = before.first { it.pitch.midi == 67 }
        val after = PianoRoll.setVelocity(before, setOf(g.id), 90)
        val out = PianoRoll.rebuild(s, 0, before, after)
        val gs = out.parts[0].bars.flatMap { it.voices[0] }.flatMap { it.notes }.filter { it.pitch.midi == 67 }
        assertEquals(listOf(90, 90), gs.map { it.velocity })
        assertEquals(listOf(90, 96), ScorePlayback.notes(out, out.parts[0]).map { it.velocity })
        // Back to the dynamics.
        val reset = PianoRoll.rebuild(out, 0, PianoRoll.notes(out, out.parts[0]), PianoRoll.setVelocity(PianoRoll.notes(out, out.parts[0]), setOf(g.id), null))
        assertEquals(listOf(49, 96), ScorePlayback.notes(reset, reset.parts[0]).map { it.velocity })
    }
}
