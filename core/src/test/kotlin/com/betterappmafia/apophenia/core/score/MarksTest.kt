package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.instrument.Instrument
import com.betterappmafia.apophenia.core.instrument.Tunings
import com.betterappmafia.apophenia.core.pitch.SpelledPitch
import com.betterappmafia.apophenia.core.rhythm.Duration
import com.betterappmafia.apophenia.core.rhythm.NoteValue
import com.betterappmafia.apophenia.core.rhythm.Tempo
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import com.betterappmafia.apophenia.core.tools.KeySpeller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MarksTest {
    private fun p(s: String) = SpelledPitch.parse(s)
    private val q = Duration.QUARTER
    private val guitar = Instrument(Tunings.GUITAR_STANDARD)

    private fun sheet(bars: List<Bar>) = Score("", TimeSignature.of(4, 4), KeyMode.parseOrNull("C major"), Tempo(120.0), listOf(Part("Voice", Clef.TREBLE, null, bars)))
    private fun bar(vararg fields: (Bar) -> Bar) = fields.fold(Bar(listOf(listOf(Event(Duration.WHOLE, listOf(Note(p("C4")))))))) { b, f -> f(b) }

    @Test fun every_marking_survives_the_stored_form_and_stays_ascii() {
        val e1 = Event(
            q, listOf(Note(p("E4"), head = Head.DIAMOND, finger = "3", pluck = "i", showString = true, effects = setOf(Effect.ACCENT))),
            marks = setOf(Mark.FERMATA, Mark.TENUTO, Mark.ARPEGGIO_UP), dynamic = Dynamic.SFZ,
            spans = mapOf(Span.SLUR to 3, Span.CRESC to 2, Span.OTTAVA_UP to 4),
            chord = "C#m7b5/G", diagram = listOf(null, 3, 2, 0, 1, 0),
            texts = listOf(StaffText(TextKind.EXPRESSION, "dolce; con   moto"), StaffText(TextKind.TECHNIQUE, "pizz.")),
            lyrics = listOf(Lyric(1, "Ha-", hyphen = true), Lyric(2, "née {x}", extend = true)),
            tremolo = 3,
            graces = listOf(Grace(NoteValue.EIGHTH, listOf(Note(p("D4")), Note(p("F#4"))), slash = true), Grace(NoteValue.SIXTEENTH, listOf(Note(p("Bb3"))), slash = false, after = true)),
        )
        val b = Bar(
            listOf(listOf(e1, Event(Duration.HALF.dotted()))), repeatStart = true, end = Barline.REPEAT, times = 3, volta = listOf(1, 2),
            jumps = setOf(Jump.SEGNO, Jump.DS_AL_CODA), rehearsal = "Verse 2", tempo = Tempo(72.0, Duration.QUARTER.dotted()), tempoText = "Più mosso",
            clef = Clef.BASS, key = KeyMode.parseOrNull("Eb major"), meter = TimeSignature.parse("2+2+3/8"),
        )
        val score = sheet(listOf(b, bar()))
        val text = ScoreText.encode(score)
        assertEquals(score, ScoreText.decode(text))
        assertTrue(text.all { it.code < 128 })
        assertTrue(text.startsWith("score ${ScoreText.VERSION}\n"))
    }

    @Test fun a_version_1_score_still_reads() {
        val v1 = "score 1\ntitle Old\nmeter 4/4\ntempo 96 q\npart\tBass\tclef=bass\ttuning=E1 A1 D2 G2\ttuning-id=bass-standard\tcapo=0\tfrets=24\twritten-up=12\n" +
            "bar\tv1 h:E1@4/0~[palm-mute] h:E1@4/0 \n"
        val s = ScoreText.decode(v1)
        val events = s.parts[0].bars[0].voices[0]
        assertEquals(2, events.size)
        assertTrue(events[0].notes[0].tie)
        assertEquals(setOf(Effect.PALM_MUTE), events[0].notes[0].effects)
        assertEquals(Head.NORMAL, events[0].notes[0].head)
        assertEquals(Barline.SINGLE, s.parts[0].bars[0].end)
    }

    @Test fun a_newer_format_and_unknown_fields_are_refused_not_half_read() {
        try { ScoreText.decode("score ${ScoreText.VERSION + 1}\nmeter 4/4\n"); fail("a newer version read") } catch (e: IllegalArgumentException) {}
        try { ScoreText.parseEvent("q:C4{wobble=1}"); fail("unknown field read") } catch (e: IllegalArgumentException) {}
    }

    @Test fun meter_key_and_tempo_changes_hold_from_their_bar_on() {
        val s = sheet(listOf(bar(), bar({ it.copy(meter = TimeSignature.of(3, 4), tempo = Tempo(60.0), key = KeyMode.parseOrNull("G major")) }), bar()))
        assertEquals(TimeSignature.of(4, 4), s.meterAt(0))
        assertEquals(TimeSignature.of(3, 4), s.meterAt(2))
        assertEquals(60.0, s.tempoAt(2).bpm, 0.0)
        assertEquals("G major", s.keyAt(1)!!.token)
        // Bar 1 at 120 (2 s), bars 2 and 3 in 3/4 at 60 (3 s each).
        assertEquals(8.0, ScoreTime.seconds(s, s.parts[0]), 1e-9)
    }

    @Test fun repeats_and_voltas_play_in_order() {
        // |: 0 | 1 [1. 2 :| [2. 3 | 4
        val s = sheet(listOf(bar({ it.copy(repeatStart = true) }), bar(), bar({ it.copy(volta = listOf(1), end = Barline.REPEAT) }), bar({ it.copy(volta = listOf(2)) }), bar()))
        assertEquals(listOf(0, 1, 2, 0, 1, 3, 4), ScoreTime.order(s.parts[0]))
        // Three times round with no endings.
        val t = sheet(listOf(bar(), bar({ it.copy(end = Barline.REPEAT, times = 3) }), bar()))
        assertEquals(listOf(0, 1, 0, 1, 0, 1, 2), ScoreTime.order(t.parts[0]))
    }

    @Test fun da_capo_al_fine_and_dal_segno_al_coda_jump_once_without_repeats() {
        // 0 | 1 Fine :| | 2 D.C. al Fine  →  0 1 0 1 2 0 1
        val a = sheet(listOf(bar(), bar({ it.copy(end = Barline.REPEAT, jumps = setOf(Jump.FINE)) }), bar({ it.copy(jumps = setOf(Jump.DC_AL_FINE)) })))
        assertEquals(listOf(0, 1, 0, 1, 2, 0, 1), ScoreTime.order(a.parts[0]))
        // 0 | segno 1 | 2 To Coda | 3 D.S. al Coda | coda 4  →  0 1 2 3 1 2 4
        val b = sheet(listOf(bar(), bar({ it.copy(jumps = setOf(Jump.SEGNO)) }), bar({ it.copy(jumps = setOf(Jump.TO_CODA)) }), bar({ it.copy(jumps = setOf(Jump.DS_AL_CODA)) }), bar({ it.copy(jumps = setOf(Jump.CODA)) })))
        assertEquals(listOf(0, 1, 2, 3, 1, 2, 4), ScoreTime.order(b.parts[0]))
    }

    @Test fun a_repeated_bar_sounds_on_each_pass() {
        val s = sheet(listOf(bar({ it.copy(end = Barline.REPEAT) })))
        val notes = ScoreTime.notes(s, s.parts[0])
        assertEquals(listOf(0.0, 2.0), notes.map { it.start })
    }

    @Test fun lines_are_clamped_to_the_events_left_and_removed_below_two() {
        var s = sheet(listOf(Bar(listOf(listOf(Event(q, listOf(Note(p("C4")))), Event(q, listOf(Note(p("D4")))))))))
        s = ScoreEdit.setSpan(s, 0, ScoreEdit.Pos(0, 0), Span.SLUR, 9)
        assertEquals(2, ScoreEdit.event(s, 0, ScoreEdit.Pos(0, 0))!!.spans[Span.SLUR])
        s = ScoreEdit.setSpan(s, 0, ScoreEdit.Pos(0, 0), Span.SLUR, 1)
        assertNull(ScoreEdit.event(s, 0, ScoreEdit.Pos(0, 0))!!.spans[Span.SLUR])
    }

    @Test fun a_grace_note_starts_on_the_main_note_and_steps_through_the_key() {
        var s = sheet(listOf(Bar(listOf(listOf(Event(Duration.WHOLE, listOf(Note(p("E4")), Note(p("G4"))))))))).copy(key = KeyMode.parseOrNull("D major"))
        s = ScoreEdit.addGrace(s, 0, ScoreEdit.Pos(0, 0), NoteValue.EIGHTH, slash = true, after = false)!!
        s = ScoreEdit.stepGrace(s, 0, ScoreEdit.Pos(0, 0), -1)
        // G4 down a step in D major is F#4, not F4.
        assertEquals(p("F#4"), ScoreEdit.event(s, 0, ScoreEdit.Pos(0, 0))!!.graces.single().notes.single().pitch)
    }

    @Test fun lyrics_go_to_the_next_sung_note_skipping_rests_and_ties() {
        val s = sheet(listOf(Bar(listOf(listOf(
            Event(q, listOf(Note(p("C4"), tie = true))), Event(q, listOf(Note(p("C4")))), Event(q), Event(q, listOf(Note(p("E4")))),
        )))))
        assertEquals(ScoreEdit.Pos(0, 3), ScoreEdit.nextSung(s, 0, ScoreEdit.Pos(0, 0)))
        assertNull(ScoreEdit.nextSung(s, 0, ScoreEdit.Pos(0, 3)))
    }

    @Test fun chord_symbols_transpose_root_and_bass_in_the_new_key() {
        val eb = KeySpeller(KeyMode.parseOrNull("Eb major")!!)
        assertEquals("F7", ScoreEdit.transposeChord("D7", 3, eb))
        assertEquals("Abmaj7/C", ScoreEdit.transposeChord("Fmaj7/A", 3, eb))
        val e = KeySpeller(KeyMode.parseOrNull("E major")!!)
        assertEquals("C#m7b5/G#", ScoreEdit.transposeChord("Bbm7b5/F", 3, e))
        // Through the chord parser: the quality and alterations kept as typed, 6/9 not taken for a slash.
        assertEquals("Eb7(b9,#11)/G", ScoreEdit.transposeChord("C7(b9,#11)/E", 3, eb))
        assertEquals("Eb6/9", ScoreEdit.transposeChord("C6/9", 3, eb))
        assertEquals("N.C.", ScoreEdit.transposeChord("N.C.", 3, eb))
    }

    @Test fun a_diagram_reads_the_chord_frets_lowest_string_first() {
        val part = Part("Guitar", Clef.TREBLE_8VB, guitar)
        // C major open: x32010 (string 6 not played).
        val c = Event(Duration.WHOLE, listOf(Note(p("C3"), 5, 3), Note(p("E3"), 4, 2), Note(p("G3"), 3, 0), Note(p("C4"), 2, 1), Note(p("E4"), 1, 0)))
        assertEquals(listOf(null, 3, 2, 0, 1, 0), ScoreEdit.diagramOf(part, c))
    }

    @Test fun dynamics_spell_with_smufl_letters() {
        assertEquals(listOf(0xE524, 0xE522, 0xE525), Dynamic.SFZ.glyphs.codePoints().toArray().toList())
        assertEquals("mf", Dynamic.MF.token)
    }
}
