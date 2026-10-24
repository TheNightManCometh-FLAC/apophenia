package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.instrument.Instrument
import com.betterappmafia.apophenia.core.instrument.Tunings
import com.betterappmafia.apophenia.core.rhythm.Duration
import com.betterappmafia.apophenia.core.rhythm.Rational
import com.betterappmafia.apophenia.core.rhythm.Tempo
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreEditTest {
    private val bass = Instrument(Tunings.BASS_STANDARD, frets = 24)
    private val q = Duration.QUARTER
    private fun tab(key: String? = null) = ScoreEdit.emptyTab("Bass", bass, TimeSignature.of(4, 4), Tempo(100.0), KeyMode.parseOrNull(key), bars = 2)
    private val first = ScoreEdit.Pos(0, 0)
    private fun ev(s: Score, at: ScoreEdit.Pos) = ScoreEdit.event(s, 0, at)!!

    @Test fun a_new_tab_is_full_bars_of_rest() {
        val s = tab()
        assertEquals(2, s.parts[0].bars.size)
        for (b in s.parts[0].bars.indices) assertEquals(Rational.ZERO, ScoreEdit.fill(s, s.parts[0], b))
        assertTrue(s.parts[0].bars.all { b -> b.voices[0].all { it.isRest } })
        assertEquals(Clef.BASS, s.parts[0].clef)
    }

    @Test fun a_fret_on_a_rest_makes_the_note_spelled_in_the_key() {
        var s = ScoreEdit.setDuration(tab("F major"), 0, first, q)
        s = ScoreEdit.setFret(s, 0, first, 3, 3)                 // A string, fret 3: C2
        assertEquals("C2", ev(s, first).notes.single().pitch.toString())
        s = ScoreEdit.setFret(s, 0, first, 3, 1)                 // same string, fret 1: replaced, and Bb in F major, not A#
        assertEquals(listOf("Bb1"), ev(s, first).notes.map { it.pitch.toString() })
        s = ScoreEdit.setFret(s, 0, first, 1, 2)                 // another string joins as a chord: A2 on the G string
        assertEquals(listOf(1 to "A2", 3 to "Bb1"), ev(s, first).notes.map { it.string to it.pitch.toString() })
        s = ScoreEdit.deleteNote(s, 0, first, 0)
        s = ScoreEdit.deleteNote(s, 0, first, 0)
        assertTrue(ev(s, first).isRest)
    }

    @Test fun next_steps_on_and_fills_the_bar_then_starts_another() {
        // Bar 1 holds one quarter; NEXT adds quarters until the bar is full, then moves to bar 2.
        var s = tab().let { t -> t.copy(parts = listOf(t.parts[0].copy(bars = listOf(Bar(listOf(listOf(Event(q)))))))) }
        var at = first
        repeat(3) { val (n, p) = ScoreEdit.next(s, 0, at, q); s = n; at = p }
        assertEquals(ScoreEdit.Pos(0, 3), at)
        assertEquals(Rational.ZERO, ScoreEdit.fill(s, s.parts[0], 0))
        val (n, p) = ScoreEdit.next(s, 0, at, q)
        assertEquals(ScoreEdit.Pos(1, 0), p)
        assertEquals(2, n.parts[0].bars.size)
        // A half note asked for with only a quarter of room left is cut to fit.
        var t = tab().let { x -> x.copy(parts = listOf(x.parts[0].copy(bars = listOf(Bar(listOf(listOf(Event(Duration.HALF.dotted())))))))) }
        t = ScoreEdit.next(t, 0, first, Duration.HALF).first
        assertEquals(q, ev(t, ScoreEdit.Pos(0, 1)).duration)
    }

    @Test fun a_bar_says_how_far_off_it_is() {
        val s = ScoreEdit.setDuration(tab(), 0, first, Duration.HALF)   // the whole rest becomes a half: short by a half
        assertEquals(Rational.of(-1, 2), ScoreEdit.fill(s, s.parts[0], 0))
    }

    @Test fun retuning_keeps_the_notes_or_the_fingering() {
        val s = ScoreEdit.setFret(ScoreEdit.setDuration(tab(), 0, first, Duration.WHOLE), 0, first, 4, 0)   // open E1
        val dropD = Instrument(Tunings.BASS_DROP_D, frets = 24)
        val keepNotes = ScoreEdit.retune(s, 0, dropD, keepNotes = true)
        ev(keepNotes, first).notes.single().let { assertEquals("E1", it.pitch.toString()); assertEquals(4 to 2, it.string to it.fret) }
        val keepFingering = ScoreEdit.retune(s, 0, dropD, keepNotes = false)
        ev(keepFingering, first).notes.single().let { assertEquals("D1", it.pitch.toString()); assertEquals(4 to 0, it.string to it.fret) }
    }

    @Test fun transposing_moves_every_note_and_fingers_it_again() {
        val s = ScoreEdit.setFret(ScoreEdit.setDuration(tab("D major"), 0, first, Duration.WHOLE), 0, first, 3, 3)   // C2
        val up = ScoreEdit.transpose(s, 0, 2)
        val n = ev(up, first).notes.single()
        assertEquals("D2", n.pitch.toString())
        assertEquals(n.pitch.midi, bass.midiAt(n.string!!, n.fret!!))
        // A semitone up from C2 in D major is spelled C#2.
        assertEquals("C#2", ev(ScoreEdit.transpose(s, 0, 1), first).notes.single().pitch.toString())
    }

    @Test fun entering_a_pitch_fingers_it_and_respelling_keeps_the_sound() {
        val p = com.betterappmafia.apophenia.core.pitch.SpelledPitch.parse("C2")
        var s = ScoreEdit.setPitch(tab(), 0, first, p, add = false)!!
        ev(s, first).notes.single().let { assertEquals(3 to 3, it.string to it.fret) }   // A string, fret 3: the lowest fret that plays C2
        // Add G2 as a chord note: it can't take the A string, so it goes elsewhere.
        s = ScoreEdit.setPitch(s, 0, first, com.betterappmafia.apophenia.core.pitch.SpelledPitch.parse("G2"), add = true)!!
        assertEquals(setOf("C2", "G2"), ev(s, first).notes.map { it.pitch.toString() }.toSet())
        assertEquals(2, ev(s, first).notes.map { it.string }.toSet().size)
        // Too low for a bass: refused.
        assertEquals(null, ScoreEdit.setPitch(s, 0, first, com.betterappmafia.apophenia.core.pitch.SpelledPitch.parse("C1"), add = true))
        // C2 → C#2 (a sharp on the same letter) moves the fret; respelling C#2 gives Db2 at the same fret.
        val c = ev(s, first).notes.indexOfFirst { it.pitch.toString() == "C2" }
        s = ScoreEdit.setAlter(s, 0, first, c, 1)!!
        val sharp = ev(s, first).notes.first { it.pitch.toString() == "C#2" }
        assertEquals(sharp.pitch.midi, bass.midiAt(sharp.string!!, sharp.fret!!))
        val i = ev(s, first).notes.indexOf(sharp)
        val re = ScoreEdit.respell(s, 0, first, i)!!
        val db = ev(re, first).notes[i]
        assertEquals("Db2", db.pitch.toString())
        assertEquals(sharp.string to sharp.fret, db.string to db.fret)
        // And on round: Db2 → B##1 → C#2.
        val again = ScoreEdit.respell(ScoreEdit.respell(re, 0, first, i)!!, 0, first, i)!!
        assertEquals("C#2", ev(again, first).notes[i].pitch.toString())
    }

    @Test fun ties_and_effects_toggle() {
        var s = ScoreEdit.setFret(tab(), 0, first, 3, 3)
        s = ScoreEdit.toggleTie(s, 0, first)
        assertTrue(ev(s, first).notes.single().tie)
        s = ScoreEdit.toggleEffect(s, 0, first, 0, Effect.PALM_MUTE)
        assertEquals(setOf(Effect.PALM_MUTE), ev(s, first).notes.single().effects)
        s = ScoreEdit.toggleEffect(s, 0, first, 0, Effect.PALM_MUTE)
        assertTrue(ev(s, first).notes.single().effects.isEmpty())
    }
}
