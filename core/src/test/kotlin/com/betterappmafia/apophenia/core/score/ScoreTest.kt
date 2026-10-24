package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.analysis.BasicPitch
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.instrument.Instrument
import com.betterappmafia.apophenia.core.instrument.Tunings
import com.betterappmafia.apophenia.core.pitch.SpelledPitch
import com.betterappmafia.apophenia.core.rhythm.Duration
import com.betterappmafia.apophenia.core.rhythm.NoteValue
import com.betterappmafia.apophenia.core.rhythm.Rational
import com.betterappmafia.apophenia.core.rhythm.Tempo
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ScoreTest {
    private fun p(s: String) = SpelledPitch.parse(s)
    private val q = Duration.QUARTER
    private val e = Duration.EIGHTH
    private val bass = Instrument(Tunings.BASS_STANDARD, frets = 24)

    @Test fun a_score_round_trips_through_its_stored_form() {
        val score = Score(
            "Chorus", TimeSignature.parse("2+2+3/8"), KeyMode.parseOrNull("D dorian"), Tempo(96.0),
            listOf(
                Part("Bass", Clef.BASS, Instrument(Tunings.BASS_DROP_D, capo = 2, frets = 24), listOf(
                    Bar(listOf(
                        listOf(Event(Duration.HALF.dotted(), listOf(Note(p("D2"), 3, 0, tie = true))), Event(e, listOf(Note(p("D2"), 3, 0, effects = setOf(Effect.PALM_MUTE, Effect.HAMMER))))),
                        listOf(Event(q.inTuplet(3, 2)), Event(e.inTuplet(3, 2).inTuplet(5, 4), listOf(Note(p("F#2")), Note(p("Bb2"))))),
                    )),
                )),
                Part("Voice", Clef.TREBLE, null, listOf(Bar(listOf(listOf(Event(Duration.WHOLE, listOf(Note(p("Ebb4"))))))))),
            ),
        )
        val text = ScoreText.encode(score)
        assertEquals(score, ScoreText.decode(text))
        assertTrue(text.lines().all { line -> line.all { it.code < 128 } })   // plan §6.5: ASCII only
    }

    @Test fun a_score_from_a_newer_version_is_refused() {
        try { ScoreText.decode("score ${ScoreText.VERSION + 1}\nmeter 4/4\n"); fail("read a newer score") } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("newer")) }
    }

    @Test fun durations_are_written_so_the_beats_show() {
        val m = TimeSignature.of(4, 4)
        assertEquals(listOf(Duration.WHOLE), FromNotes.durations(0, 3840, m, 4))
        assertEquals(listOf(q.dotted()), FromNotes.durations(0, 1440, m, 4))           // dotted quarter on beat 1
        assertEquals(listOf(q.dotted()), FromNotes.durations(960, 1440, m, 4))         // dotted quarter on beat 2
        assertEquals(listOf(e, q), FromNotes.durations(480, 1440, m, 4))              // off the beat: an eighth to beat 2, then a quarter
        assertEquals(listOf(e, e), FromNotes.durations(480, 960, m, 4))              // off-beat quarter: tied at beat 2
        assertEquals(listOf(Duration.HALF), FromNotes.durations(1920, 1920, m, 4))
        assertEquals(listOf(q.dotted()), FromNotes.durations(0, 1440, TimeSignature.of(6, 8), 6))   // one beat of 6/8
        assertEquals(listOf(e.inTuplet(3, 2)), FromNotes.durations(0, 320, m, 3))
    }

    @Test fun every_place_a_bass_note_can_be_played() {
        // D2 on E-standard bass: open D string, A string fret 5, E string fret 10.
        assertEquals(listOf(Fingering.Place(2, 0), Fingering.Place(3, 5), Fingering.Place(4, 10)), Fingering.places(bass, 38))
        assertTrue(Fingering.places(bass, 20).isEmpty())   // below the low E
    }

    @Test fun the_fingering_keeps_a_riff_in_one_position() {
        // A1 C2 D2 C2 A1: A string open, 3, then D open (or A 5), back: one position, nothing up the neck.
        val f = Fingering.choose(bass, listOf(listOf(33), listOf(36), listOf(38), listOf(36), listOf(33)))
        assertEquals(listOf(3 to 0, 3 to 3, 2 to 0, 3 to 3, 3 to 0), f.map { it.single()!!.let { p -> p.string to p.fret } })
        // A higher run stays in one place too: G2 A2 B2 (frets 0 2 4 on the G string, or 5 7 9 on D).
        val g = Fingering.choose(bass, listOf(listOf(43), listOf(45), listOf(47)))
        val frets = g.map { it.single()!! }
        assertTrue("$frets", frets.map { it.fret }.let { it.max() - it.min() } <= 4)
    }

    @Test fun a_chord_gets_one_string_per_note_within_a_hand_span() {
        val guitar = Instrument(Tunings.GUITAR_STANDARD)
        // C major, open position: C3 E3 G3 C4 E4.
        val c = Fingering.choose(guitar, listOf(listOf(48, 52, 55, 60, 64))).single()
        assertTrue(c.all { it != null })
        assertEquals(5, c.map { it!!.string }.toSet().size)
        assertTrue(c.map { it!!.fret }.filter { it > 0 }.let { it.max() - it.min() } <= 4)
        // Out of reach: a note under the low E gets no place.
        assertNull(Fingering.choose(guitar, listOf(listOf(30))).single().single())
    }

    @Test fun a_transcribed_riff_becomes_bars_with_ties_rests_and_frets() {
        // 120 BPM, 4/4: a beat is 0.5 s. F major, so the B-flat is spelled Bb.
        val notes = listOf(
            BasicPitch.TimedNote(0.0, 0.5, 41, 0.8, emptyList()),    // F2, beat 1, quarter
            BasicPitch.TimedNote(0.5, 0.75, 46, 0.8, emptyList()),   // Bb2, beat 2, eighth
            BasicPitch.TimedNote(1.5, 2.5, 45, 0.8, emptyList()),    // A2, beat 4 to beat 2 of bar 2: tied over the bar
        )
        val s = FromNotes.build(notes, FromNotes.Settings(120.0, key = KeyMode.parseOrNull("F major")), "Bass", bass, "Riff")
        val part = s.parts.single()
        assertEquals(Clef.BASS, part.clef)
        assertEquals(2, part.bars.size)
        val b1 = part.bars[0].voices[0]
        // q F2 · e Bb2 · e rest · q rest · q A2~
        assertEquals(listOf(q, e, e, q, q), b1.map { it.duration })
        assertEquals(listOf("F2", "Bb2", null, null, "A2"), b1.map { it.notes.firstOrNull()?.pitch?.toString() })
        assertTrue(b1[4].notes.single().tie)
        val b2 = part.bars[1].voices[0]
        assertEquals("A2", b2[0].notes.single().pitch.toString())
        assertTrue(!b2[0].notes.single().tie)
        // Bar 2 fills with rests to a whole bar.
        assertEquals(Rational.of(1), b2.fold(Rational.ZERO) { a, ev -> a + ev.length })
        // Whatever strings were chosen, each string and fret plays the written pitch.
        for (ev in b1 + b2) for (n in ev.notes) assertEquals(n.pitch.midi, bass.midiAt(n.string!!, n.fret!!))
        // Every bar adds up to 4/4.
        for (b in part.bars) assertEquals(Rational.of(1), b.voices[0].fold(Rational.ZERO) { a, ev -> a + ev.length })
    }

    @Test fun ties_sound_as_one_note_and_every_event_has_its_time() {
        // 120 BPM: a quarter is 0.5 s. Bar 1: q C3, h E3~ ; bar 2: q E3 (tied in), dotted half rest.
        val part = Part("Piano", Clef.BASS, null, listOf(
            Bar(listOf(listOf(Event(q, listOf(Note(p("C3")))), Event(Duration.HALF, listOf(Note(p("E3"), tie = true))), Event(q)))),
            Bar(listOf(listOf(Event(q, listOf(Note(p("E3")))), Event(Duration.HALF.dotted())))),
        ))
        val score = Score(meter = TimeSignature.of(4, 4), tempo = Tempo(120.0), parts = listOf(part))
        val notes = ScoreTime.notes(score, part)
        assertEquals(2, notes.size)
        assertEquals(48, notes[0].midi); assertEquals(0.0, notes[0].start, 1e-9); assertEquals(0.5, notes[0].end, 1e-9)
        // E3 from beat 2 of bar 1 to the end of beat 1 of bar 2: 0.5 s to 2.5 s.
        assertEquals(52, notes[1].midi); assertEquals(0.5, notes[1].start, 1e-9); assertEquals(2.5, notes[1].end, 1e-9)
        val slots = ScoreTime.slots(score, part)
        assertEquals(listOf(0.0, 0.5, 1.5, 2.0, 2.5), slots.map { it.start })
        assertEquals(4.0, ScoreTime.seconds(score, part), 1e-9)
    }

    @Test fun plain_text_tab_has_a_line_per_string_highest_first() {
        // E-standard bass: A1 (A string open), C2 (A string 3), D2 tied over (D string open).
        val part = Part("Bass", Clef.BASS, bass, listOf(
            Bar(listOf(listOf(Event(q, listOf(Note(p("A1"), 3, 0))), Event(q, listOf(Note(p("C2"), 3, 3))), Event(Duration.HALF, listOf(Note(p("D2"), 2, 0, tie = true)))))),
            Bar(listOf(listOf(Event(Duration.WHOLE, listOf(Note(p("D2"), 2, 0)))))),
        ))
        assertEquals(
            listOf(
                "G|-------|-----|",
                "D|-----0-|-(0)-|",
                "A|-0-3---|-----|",
                "E|-------|-----|",
            ),
            TabText.render(part),
        )
    }

    @Test fun a_pickup_before_the_first_downbeat_pushes_the_music_a_bar_on() {
        val notes = listOf(BasicPitch.TimedNote(0.5, 1.0, 48, 0.8, emptyList()), BasicPitch.TimedNote(1.0, 1.5, 50, 0.8, emptyList()))
        val s = FromNotes.build(notes, FromNotes.Settings(120.0, origin = 1.0), "Piano", null)
        val bars = s.parts.single().bars
        // The C3 a beat before the downbeat ends bar 1; the D3 starts bar 2.
        assertEquals("C3", bars[0].voices[0].last().notes.single().pitch.toString())
        assertEquals("D3", bars[1].voices[0].first().notes.single().pitch.toString())
        assertEquals(Clef.BASS, s.parts.single().clef)
        assertEquals(NoteValue.QUARTER, s.tempo.beatUnit.value)
    }
}
