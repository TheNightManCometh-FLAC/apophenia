package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.instrument.Instrument
import com.betterappmafia.apophenia.core.instrument.Tunings
import com.betterappmafia.apophenia.core.pitch.SpelledPitch
import com.betterappmafia.apophenia.core.rhythm.Duration
import com.betterappmafia.apophenia.core.rhythm.Rational
import com.betterappmafia.apophenia.core.rhythm.Tempo
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicesTest {
    private fun p(s: String) = SpelledPitch.parse(s)
    private val q = Duration.QUARTER
    private val e = Duration.EIGHTH
    private fun n(vararg ps: String) = ps.map { Note(p(it)) }

    private fun sheet(vararg voices: List<Event>) =
        Score("", TimeSignature.of(4, 4), KeyMode.parseOrNull("C major"), Tempo(120.0), listOf(Part("Piano", Clef.TREBLE, null, listOf(Bar(voices.toList())))))
    private fun ev(s: Score, at: ScoreEdit.Pos) = ScoreEdit.event(s, 0, at)!!

    @Test fun a_new_voice_starts_as_rests_filling_the_bar_and_step_entry_works_in_it() {
        var s = sheet(listOf(Event(Duration.WHOLE, n("E5"))))
        s = ScoreEdit.ensureVoice(s, 0, 0, 1)
        assertEquals(Rational.ZERO, ScoreEdit.fill(s, s.parts[0], 0, 1))
        val at = ScoreEdit.Pos(0, 0, 1)
        s = ScoreEdit.setDuration(s, 0, at, Duration.HALF)
        s = ScoreEdit.setPitch(s, 0, at, p("C4"), add = false)!!
        // Voice 1 untouched; voice 2 holds C4 then is short of a half until NEXT fills it.
        assertEquals(n("E5"), ev(s, ScoreEdit.Pos(0, 0)).notes)
        assertEquals(n("C4"), ev(s, at).notes)
        val (s2, nxt) = ScoreEdit.next(s, 0, at, Duration.HALF)
        assertEquals(ScoreEdit.Pos(0, 1, 1), nxt)
        assertTrue(ev(s2, nxt).isRest)
    }

    @Test fun next_past_the_last_bar_in_voice_2_adds_a_bar_with_voice_1_as_rests() {
        var s = ScoreEdit.ensureVoice(sheet(listOf(Event(Duration.WHOLE, n("E5")))), 0, 0, 1)
        s = ScoreEdit.setDuration(s, 0, ScoreEdit.Pos(0, 0, 1), Duration.WHOLE)
        val (s2, at) = ScoreEdit.next(s, 0, ScoreEdit.Pos(0, 0, 1), q)
        assertEquals(ScoreEdit.Pos(1, 0, 1), at)
        assertEquals(2, s2.parts[0].bars[1].voices.size)
        assertTrue(s2.parts[0].bars[1].voices[0].all { it.isRest })
    }

    @Test fun a_note_moves_to_another_voice_only_where_the_rhythm_matches() {
        var s = sheet(listOf(Event(q, n("C4", "E4", "G4")), Event(q, n("F4")), Event(Duration.HALF)))
        s = ScoreEdit.ensureVoice(s, 0, 0, 1)
        // Voice 2 is a whole rest: no quarter beat to take the note.
        assertNull(ScoreEdit.moveToVoice(s, 0, ScoreEdit.Pos(0, 0), 0, 1))
        s = ScoreEdit.setDuration(s, 0, ScoreEdit.Pos(0, 0, 1), q)
        val (moved, dest) = ScoreEdit.moveToVoice(s, 0, ScoreEdit.Pos(0, 0), 0, 1)!!
        assertEquals(ScoreEdit.Pos(0, 0, 1), dest)
        assertEquals(n("C4"), ev(moved, dest).notes)
        assertEquals(n("E4", "G4"), ev(moved, ScoreEdit.Pos(0, 0)).notes)
    }

    @Test fun clearing_the_top_voice_removes_it_and_a_lower_one_becomes_rests() {
        var s = ScoreEdit.ensureVoice(sheet(listOf(Event(Duration.WHOLE, n("E5")))), 0, 0, 2)
        s = ScoreEdit.setPitch(s, 0, ScoreEdit.Pos(0, 0, 1), p("C4"), add = false)!!
        s = ScoreEdit.clearVoice(s, 0, 0, 1)
        assertEquals(3, s.parts[0].bars[0].voices.size)
        assertTrue(s.parts[0].bars[0].voices[1].all { it.isRest })
        s = ScoreEdit.clearVoice(s, 0, 0, 2)
        assertEquals(2, s.parts[0].bars[0].voices.size)
    }

    @Test fun only_rests_in_voices_2_to_4_hide_and_hiding_survives_the_stored_form() {
        var s = ScoreEdit.ensureVoice(sheet(listOf(Event(Duration.WHOLE))), 0, 0, 1)
        assertEquals(s, ScoreEdit.toggleHidden(s, 0, ScoreEdit.Pos(0, 0)))
        s = ScoreEdit.toggleHidden(s, 0, ScoreEdit.Pos(0, 0, 1))
        assertTrue(ev(s, ScoreEdit.Pos(0, 0, 1)).hidden)
        assertEquals(s, ScoreText.decode(ScoreText.encode(s)))
    }

    @Test fun rhythm_mode_puts_each_length_on_the_middle_line_and_moves_on() {
        var s = sheet(listOf(Event(Duration.WHOLE)))
        val b4 = Notation.pitchAt(s.parts[0], 4, 0)
        assertEquals(p("B4"), b4)
        var at = ScoreEdit.Pos(0, 0)
        for (d in listOf(q, e, e, Duration.HALF)) { val (n2, a2) = ScoreEdit.enterRhythm(s, 0, at, d, b4)!!; s = n2; at = a2 }
        assertEquals(listOf(q, e, e, Duration.HALF), s.parts[0].bars[0].voices[0].map { it.duration })
        assertTrue(s.parts[0].bars[0].voices[0].all { it.notes == n("B4") })
        assertEquals(ScoreEdit.Pos(1, 0), at)
    }

    @Test fun repitch_keeps_the_rhythm_skips_rests_and_carries_ties() {
        var s = sheet(listOf(
            Event(q, n("B4")), Event(q), Event(q, listOf(Note(p("B4"), tie = true))), Event(q, n("B4")),
        ))
        var (s2, nxt) = ScoreEdit.repitch(s, 0, ScoreEdit.Pos(0, 0), p("C5"), add = false)!!
        assertEquals(ScoreEdit.Pos(0, 2), nxt)   // the rest is passed over
        val r = ScoreEdit.repitch(s2, 0, nxt!!, p("D5"), add = false)!!
        s = r.first
        val evs = s.parts[0].bars[0].voices[0]
        assertEquals(listOf(q, q, q, q), evs.map { it.duration })
        assertEquals(listOf(listOf(p("C5")), emptyList(), listOf(p("D5")), listOf(p("D5"))), evs.map { ev -> ev.notes.map { it.pitch } })
        assertTrue(evs[2].notes.single().tie)
        assertNull(r.second)   // the tied-in D5 isn't re-pitched again
    }

    @Test fun repitch_on_a_fretted_part_fingers_the_new_note() {
        val bass = Instrument(Tunings.BASS_STANDARD, frets = 24)
        val s = Score("", TimeSignature.of(4, 4), null, Tempo(120.0), listOf(Part("Bass", Clef.BASS, bass, listOf(Bar(listOf(listOf(Event(Duration.WHOLE, listOf(Note(p("E1"), 4, 0))))))))))
        val (out, _) = ScoreEdit.repitch(s, 0, ScoreEdit.Pos(0, 0), p("A1"), add = false)!!
        val note = ScoreEdit.event(out, 0, ScoreEdit.Pos(0, 0))!!.notes.single()
        assertEquals(bass.midiAt(note.string!!, note.fret!!), p("A1").midi)
    }

    @Test fun insert_mode_pushes_the_bar_on_and_keeps_the_cursor_before_the_same_beat() {
        val s = sheet(listOf(Event(q, n("C4")), Event(q, n("D4")), Event(Duration.HALF, n("E4"))))
        val (s2, at) = ScoreEdit.insertBefore(s, 0, ScoreEdit.Pos(0, 1), e, p("G4"))!!
        val evs = s2.parts[0].bars[0].voices[0]
        assertEquals(listOf("C4", "G4", "D4", "E4"), evs.map { it.notes.single().pitch.toString() })
        assertEquals(ScoreEdit.Pos(0, 2), at)
        assertEquals(Rational.of(1, 8), ScoreEdit.fill(s2, s2.parts[0], 0))
    }

    @Test fun realtime_ties_held_notes_and_merges_them_into_beat_sized_notes() {
        var s = sheet(listOf(Event(Duration.WHOLE)))
        var at = ScoreEdit.Pos(0, 0); var prev: ScoreEdit.Pos? = null
        // Eight eighth-note clicks: C4 held for four, then D4 for two, then nothing (rests).
        val held = listOf(listOf("C4"), listOf("C4"), listOf("C4"), listOf("C4"), listOf("D4"), listOf("D4"), emptyList(), emptyList())
        var last: List<String> = emptyList()
        for (h in held) {
            val (n2, a2) = ScoreEdit.realtimeStep(s, 0, at, e, h.map(::p), prev, tieOn = h.isNotEmpty() && h == last)
            s = n2; prev = at; at = a2; last = h
        }
        s = ScoreEdit.mergeTies(s, 0)
        val evs = s.parts[0].bars[0].voices[0]
        // C4 tied over four eighths from beat 1 = a half; D4 two eighths on beat 3 = a quarter; two eighth rests.
        assertEquals(listOf(Duration.HALF, q, e, e), evs.map { it.duration })
        assertEquals(listOf(listOf("C4"), listOf("D4"), emptyList(), emptyList()), evs.map { ev -> ev.notes.map { it.pitch.toString() } })
        assertTrue(evs.all { ev -> ev.notes.none { it.tie } })
    }

    @Test fun every_voice_plays() {
        val s = sheet(listOf(Event(Duration.WHOLE, n("E5"))), listOf(Event(Duration.HALF, n("C4")), Event(Duration.HALF, n("D4"))))
        val notes = ScoreTime.notes(s, s.parts[0])
        assertEquals(listOf(76, 60, 62), notes.sortedWith(compareBy({ it.start }, { -it.midi })).map { it.midi })
    }
}
