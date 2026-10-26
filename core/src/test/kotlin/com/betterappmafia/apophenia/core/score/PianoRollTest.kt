package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.instrument.Instrument
import com.betterappmafia.apophenia.core.instrument.Tunings
import com.betterappmafia.apophenia.core.pitch.SpelledPitch
import com.betterappmafia.apophenia.core.rhythm.Duration
import com.betterappmafia.apophenia.core.rhythm.Tempo
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import com.betterappmafia.apophenia.core.tools.KeySpeller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PianoRollTest {
    private fun p(s: String) = SpelledPitch.parse(s)
    private val q = Duration.QUARTER
    private val e = Duration.EIGHTH
    private val Q = 960L
    private val cMajor = KeySpeller(KeyMode.parseOrNull("C major")!!)

    private fun sheet(vararg bars: List<Event>) =
        Score("", TimeSignature.of(4, 4), KeyMode.parseOrNull("C major"), Tempo(120.0), listOf(Part("Piano", Clef.TREBLE, null, bars.map { Bar(listOf(it)) })))
    private fun ev(d: Duration, vararg ps: String, tie: Boolean = false) = Event(d, ps.map { Note(p(it), tie = tie) })
    private fun voice(s: Score, b: Int, v: Int = 0) = s.parts[0].bars[b].voices[v]

    @Test fun a_tie_across_the_barline_is_one_note_in_the_roll() {
        val s = sheet(listOf(Event(Duration.HALF.dotted()), ev(q, "G4", tie = true)), listOf(ev(Duration.HALF, "G4"), Event(Duration.HALF)))
        val notes = PianoRoll.notes(s, s.parts[0])
        assertEquals(1, notes.size)
        assertEquals(3 * Q, notes[0].start)
        assertEquals(3 * Q, notes[0].length)
    }

    @Test fun moving_a_note_rewrites_only_the_bars_it_touched() {
        // Bar 1 has a triplet the roll must not disturb.
        val triplet = Duration.EIGHTH.inTuplet(3, 2)
        val s = sheet(
            listOf(ev(triplet, "C4"), ev(triplet, "D4"), ev(triplet, "E4"), ev(q, "F4"), ev(Duration.HALF, "G4")),
            listOf(ev(q, "A4"), Event(q), Event(Duration.HALF)),
        )
        val before = PianoRoll.notes(s, s.parts[0])
        val a4 = before.single { it.pitch == p("A4") }
        val after = PianoRoll.move(before, setOf(a4.id), 2 * Q, 2, cMajor)
        val out = PianoRoll.rebuild(s, 0, before, after)
        assertSame(s.parts[0].bars[0], out.parts[0].bars[0])
        // A4 moved to beat 3 and up a tone: B4, written as a rest of a half, then the quarter, then a quarter rest.
        assertEquals(listOf(Duration.HALF, q, q), voice(out, 1).map { it.duration })
        assertEquals(listOf(emptyList(), listOf(p("B4")), emptyList()), voice(out, 1).map { it.notes.map { n -> n.pitch } })
    }

    @Test fun a_lengthened_note_ties_across_the_bar_and_the_bar_after_is_rewritten() {
        val s = sheet(listOf(Event(Duration.HALF), ev(Duration.HALF, "E4")), listOf(Event(Duration.WHOLE)))
        val before = PianoRoll.notes(s, s.parts[0])
        val after = PianoRoll.resize(before, setOf(before.single().id), 2 * Q, Q / 4)
        val out = PianoRoll.rebuild(s, 0, before, after)
        assertTrue(voice(out, 0).last().notes.single().tie)
        assertEquals(listOf(Duration.HALF, Duration.HALF), voice(out, 1).map { it.duration })
        assertEquals(p("E4"), voice(out, 1)[0].notes.single().pitch)
        assertTrue(voice(out, 1)[0].notes.none { it.tie })
    }

    @Test fun drawing_past_the_end_adds_bars_and_markings_travel_with_their_note() {
        val s = sheet(listOf(Event(q, listOf(Note(p("C4"))), marks = setOf(Mark.FERMATA)), Event(q), Event(Duration.HALF)))
        val before = PianoRoll.notes(s, s.parts[0])
        var after = PianoRoll.add(before, 4 * Q + Q, Q, 67, 0, cMajor)
        after = PianoRoll.move(after, setOf(before.single().id), Q, 0, cMajor)
        val out = PianoRoll.rebuild(s, 0, before, after)
        assertEquals(2, out.parts[0].bars.size)
        val bar0 = voice(out, 0)
        assertEquals(setOf(Mark.FERMATA), bar0[1].marks)
        assertTrue(bar0[0].marks.isEmpty())
        assertEquals(p("G4"), voice(out, 1).first { !it.isRest }.notes.single().pitch)
    }

    @Test fun overlapping_notes_in_one_voice_become_chords_and_ties() {
        val s = sheet(listOf(Event(Duration.WHOLE)))
        val before = PianoRoll.notes(s, s.parts[0])
        var after = PianoRoll.add(before, 0, 2 * Q, 60, 0, cMajor)
        after = PianoRoll.add(after, Q, 2 * Q, 64, 0, cMajor)
        val out = PianoRoll.rebuild(s, 0, before, after)
        val evs = voice(out, 0)
        // C4 alone, then C4 (tied in) with E4, then E4 alone, then a rest.
        assertEquals(listOf(listOf(60), listOf(64, 60), listOf(64), emptyList()), evs.map { it.notes.map { n -> n.pitch.midi } })
        assertTrue(evs[0].notes.single().tie)
        assertTrue(evs[1].notes.single { it.pitch.midi == 64 }.tie)
    }

    @Test fun quantize_chop_glue_and_legato_do_what_fl_studio_does() {
        val start = listOf(PianoRoll.RollNote(0, 230, 500, p("C4")), PianoRoll.RollNote(1, 1900, 100, p("D4")))
        val qz = PianoRoll.quantize(start, setOf(0, 1), Q / 4)
        assertEquals(listOf(240L to 480L, 1920L to 240L), qz.map { it.start to it.length })
        val chopped = PianoRoll.chop(listOf(PianoRoll.RollNote(0, 0, Q, p("C4"))), setOf(0), Q / 4)
        assertEquals(listOf(0L, 240L, 480L, 720L), chopped.map { it.start })
        assertEquals(1, PianoRoll.glue(chopped, chopped.map { it.id }.toSet()).size)
        val leg = PianoRoll.legato(qz, setOf(0, 1))
        assertEquals(1920L - 240L, leg[0].length)
        assertEquals(240L, leg[1].length)
    }

    @Test fun flip_and_limit_respell_in_the_key() {
        val notes = listOf(PianoRoll.RollNote(0, 0, Q, p("C4")), PianoRoll.RollNote(1, Q, Q, p("E4")), PianoRoll.RollNote(2, 2 * Q, Q, p("G4")))
        val f = PianoRoll.flip(notes, setOf(0, 1, 2), cMajor)
        assertEquals(listOf(p("G4"), p("Eb4"), p("C4")), f.map { it.pitch })
        val l = PianoRoll.limit(listOf(PianoRoll.RollNote(0, 0, Q, p("C2"))), setOf(0), 48, 72, cMajor)
        assertEquals(p("C3"), l.single().pitch)
    }

    @Test fun snap_to_scale_takes_the_scale_tone_at_or_below() {
        val d = KeyMode.parseOrNull("D major")
        assertEquals(66, PianoRoll.snapToScale(66, d))   // F#4 is in D major
        assertEquals(64, PianoRoll.snapToScale(65, d))   // F4 isn't: E4
        assertEquals(65, PianoRoll.snapToScale(65, null))
    }

    @Test fun a_fretted_part_is_fingered_again_after_a_roll_edit() {
        val bass = Instrument(Tunings.BASS_STANDARD, frets = 24)
        val s = Score("", TimeSignature.of(4, 4), null, Tempo(120.0), listOf(Part("Bass", Clef.BASS, bass, listOf(Bar(listOf(listOf(Event(Duration.WHOLE, listOf(Note(p("E1"), 4, 0))))))))))
        val before = PianoRoll.notes(s, s.parts[0])
        val after = PianoRoll.move(before, setOf(before.single().id), 0, 5, KeySpeller(KeyMode.parseOrNull("C major")!!))
        val n = PianoRoll.rebuild(s, 0, before, after).parts[0].bars[0].voices[0][0].notes.single()
        assertEquals(p("A1"), n.pitch)
        assertEquals(p("A1").midi, bass.midiAt(n.string!!, n.fret!!))
    }

    @Test fun three_notes_on_a_triplet_snap_are_written_as_an_eighth_note_triplet() {
        val s = sheet(listOf(Event(Duration.WHOLE)))
        val before = PianoRoll.notes(s, s.parts[0])
        var after = before
        for ((i, n) in listOf("C4", "D4", "E4").withIndex()) after = PianoRoll.add(after, i * 320L, 320L, p(n).midi, 0, cMajor)
        assertEquals(12, PianoRoll.divisionFor(after))
        val out = PianoRoll.rebuild(s, 0, before, after, PianoRoll.divisionFor(after))
        val triplet = Duration.EIGHTH.inTuplet(3, 2)
        assertEquals(listOf(triplet, triplet, triplet), voice(out, 0).take(3).map { it.duration })
        assertEquals(listOf(p("C4"), p("D4"), p("E4")), voice(out, 0).take(3).map { it.notes.single().pitch })
        assertEquals(4, PianoRoll.divisionFor(PianoRoll.add(emptyList(), 120, 120, 60, 0, cMajor)))
    }

    @Test fun a_chord_stamp_spells_every_tone_from_the_root() {
        val e7s9 = PianoRoll.stamp(emptyList(), 0, Q, p("E3"), com.betterappmafia.apophenia.core.catalog.Chords.byId("7#9"), 0)
        assertEquals(listOf("E3", "G#3", "B3", "D4", "Fx4").map(::p), e7s9.map { it.pitch })
        val cdim7 = PianoRoll.stamp(emptyList(), 0, Q, p("C4"), com.betterappmafia.apophenia.core.catalog.Chords.byId("dim7"), 1)
        assertEquals(listOf("C4", "Eb4", "Gb4", "Bbb4").map(::p), cdim7.map { it.pitch })
        assertTrue(cdim7.all { it.voice == 1 && it.length == Q })
        // Fb7#9 needs no triple accidental, so it keeps its root: Fb Ab Cb Ebb G.
        assertEquals(listOf("Fb3", "Ab3", "Cb4", "Ebb4", "G4").map(::p), PianoRoll.stamp(emptyList(), 0, Q, p("Fb3"), com.betterappmafia.apophenia.core.catalog.Chords.byId("7#9"), 0).map { it.pitch })
        // E#7#9 would need F#x (a triple sharp) for its #9, so the root is respelled F: F A C Eb G#.
        assertEquals(listOf("F3", "A3", "C4", "Eb4", "G#4").map(::p), PianoRoll.stamp(emptyList(), 0, Q, p("E#3"), com.betterappmafia.apophenia.core.catalog.Chords.byId("7#9"), 0).map { it.pitch })
    }

    @Test fun arpeggiate_spreads_a_chord_in_snap_steps_until_it_ends() {
        val chord = PianoRoll.stamp(emptyList(), 0, 2 * Q, p("C4"), com.betterappmafia.apophenia.core.catalog.Chords.byId("maj"), 0)
        val lone = PianoRoll.add(chord, 2 * Q, Q, p("A4").midi, 0, cMajor)
        val all = lone.map { it.id }.toSet()
        fun names(ns: List<PianoRoll.RollNote>) = ns.sortedBy { it.start }.filter { it.start < 2 * Q }.map { it.pitch.toString() }
        val up = PianoRoll.arpeggiate(lone, all, Q / 4, PianoRoll.ArpPattern.UP)
        assertEquals(listOf("C4", "E4", "G4", "C4", "E4", "G4", "C4", "E4"), names(up))
        assertTrue(up.filter { it.start < 2 * Q }.all { it.length == Q / 4 })
        assertEquals(listOf("C4", "E4", "G4", "E4", "C4", "E4", "G4", "E4"), names(PianoRoll.arpeggiate(lone, all, Q / 4, PianoRoll.ArpPattern.UP_DOWN)))
        assertEquals(listOf("G4", "E4", "C4", "G4", "E4", "C4", "G4", "E4"), names(PianoRoll.arpeggiate(lone, all, Q / 4, PianoRoll.ArpPattern.DOWN)))
        // The lone A4 after the chord is left as it was; ids stay unique.
        assertEquals(listOf(Pair(2 * Q, Q)), up.filter { it.pitch == p("A4") }.map { it.start to it.length })
        assertEquals(up.size, up.map { it.id }.toSet().size)
        // Written back to notation: eight sixteenths in the first half of the bar.
        val s = sheet(listOf(Event(Duration.WHOLE)))
        val out = PianoRoll.rebuild(s, 0, PianoRoll.notes(s, s.parts[0]), up)
        assertEquals(List(8) { Duration.SIXTEENTH }, voice(out, 0).take(8).map { it.duration })
    }
}
