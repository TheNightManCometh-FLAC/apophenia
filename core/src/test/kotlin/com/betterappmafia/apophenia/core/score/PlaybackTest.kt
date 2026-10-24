package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.pitch.SpelledPitch
import com.betterappmafia.apophenia.core.rhythm.Duration
import com.betterappmafia.apophenia.core.rhythm.NoteValue
import com.betterappmafia.apophenia.core.rhythm.Tempo
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import com.betterappmafia.apophenia.core.sheet.ChordChart
import com.betterappmafia.apophenia.core.sheet.ChordSound
import com.betterappmafia.apophenia.core.sound.PianoSampler
import com.betterappmafia.apophenia.core.sound.PlayNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class PlaybackTest {
    private fun p(s: String) = SpelledPitch.parse(s)
    private val q = Duration.QUARTER
    private fun n(s: String, vararg fx: Effect) = Note(p(s), effects = fx.toSet())

    // 60 bpm: a quarter is one second.
    private fun sheet(vararg events: Event, key: String = "C major") =
        Score("", TimeSignature.parse("4/4"), KeyMode.parseOrNull(key), Tempo(60.0), listOf(Part("Piano", Clef.TREBLE, null, listOf(Bar(listOf(events.toList()))))))

    private fun play(s: Score) = ScorePlayback.notes(s, s.parts[0])

    @Test fun dynamics_hold_until_the_next_and_sforzando_hits_one_beat() {
        val s = sheet(Event(q, listOf(n("C4")), dynamic = Dynamic.P), Event(q, listOf(n("D4"))), Event(q, listOf(n("E4")), dynamic = Dynamic.SFZ), Event(q, listOf(n("F4"))))
        assertEquals(listOf(49, 49, 112, 49), play(s).map { it.velocity })
        val fp = sheet(Event(q, listOf(n("C4")), dynamic = Dynamic.FP), Event(q, listOf(n("D4"))))
        assertEquals(listOf(96, 49), play(fp).map { it.velocity })
    }

    @Test fun a_hairpin_climbs_to_the_dynamic_where_it_ends_or_a_level_without_one() {
        val s = sheet(
            Event(q, listOf(n("C4")), dynamic = Dynamic.P, spans = mapOf(Span.CRESC to 3)), Event(q, listOf(n("D4"))), Event(q, listOf(n("E4"))),
            Event(q, listOf(n("F4")), dynamic = Dynamic.F),
        )
        val v = play(s).map { it.velocity }
        assertEquals(49, v[0]); assertEquals(96, v[3])
        assertTrue("rising: $v", v[0] < v[1] && v[1] < v[2] && v[2] < v[3])
        val open = sheet(Event(q, listOf(n("C4")), dynamic = Dynamic.MF, spans = mapOf(Span.DIM to 2)), Event(q, listOf(n("D4"))), Event(q, listOf(n("E4"))))
        assertEquals(80 - 16, play(open).last().velocity)
    }

    @Test fun staccato_halves_a_note_accents_play_louder_ghosts_softer() {
        val s = sheet(Event(q, listOf(n("C4", Effect.STACCATO))), Event(q, listOf(n("D4", Effect.ACCENT))), Event(q, listOf(n("E4", Effect.GHOST))), Event(q, listOf(n("F4")), marks = setOf(Mark.STACCATISSIMO)))
        val out = play(s)
        assertEquals(0.5, out[0].end - out[0].start, 1e-9)
        assertEquals(100, out[1].velocity)
        assertEquals((80 * 0.45).toInt(), out[2].velocity)
        assertEquals(0.3, out[3].end - out[3].start, 1e-9)
    }

    @Test fun an_acciaccatura_plays_on_the_beat_and_the_main_note_follows() {
        val s = sheet(Event(q, listOf(n("D4")), graces = listOf(Grace(NoteValue.EIGHTH, listOf(n("C#4"))))), Event(q, listOf(n("E4"))))
        val out = play(s)
        assertEquals(listOf(61, 62, 64), out.map { it.midi })
        assertEquals(0.0, out[0].start, 1e-9)
        assertEquals(ScorePlayback.CRUSH, out[1].start, 1e-9)
        // An appoggiatura takes its written value (an eighth: half a second here), up to half the note.
        val app = sheet(Event(q, listOf(n("D4")), graces = listOf(Grace(NoteValue.EIGHTH, listOf(n("E4")), slash = false))))
        assertEquals(0.5, play(app)[1].start, 1e-9)
    }

    @Test fun a_trill_alternates_with_the_step_above_in_the_key_and_a_mordent_dips_below() {
        // F major: the step above A4 is B♭4.
        val s = sheet(Event(q, listOf(n("A4")), marks = setOf(Mark.TRILL)), key = "F major")
        val out = play(s)
        assertEquals(listOf(69, 70), out.map { it.midi }.distinct())
        assertEquals(1.0, out.last().end, 1e-9)
        val m = sheet(Event(q, listOf(n("A4")), marks = setOf(Mark.MORDENT)))
        assertEquals(listOf(69, 67, 69), play(m).map { it.midi })
    }

    @Test fun tremolo_repeats_arpeggios_roll_and_the_pedal_holds() {
        val t = sheet(Event(q, listOf(n("C4")), tremolo = 2))
        assertEquals(4, play(t).size)
        val arp = sheet(Event(q, listOf(n("C4"), n("E4"), n("G4")), marks = setOf(Mark.ARPEGGIO_DOWN)))
        val a = play(arp).sortedBy { it.start }
        assertEquals(listOf(67, 64, 60), a.map { it.midi })
        assertEquals(2 * ScorePlayback.ROLL, a[2].start, 1e-9)
        val ped = sheet(Event(q, listOf(n("C4")), spans = mapOf(Span.PEDAL to 3)), Event(q, listOf(n("E4"))), Event(q, listOf(n("G4"))), Event(q))
        val out = play(ped)
        assertEquals(listOf(3.0, 3.0, 3.0), out.map { it.end })
    }

    @Test fun chord_symbols_sound_as_bass_and_close_chord() {
        // Bass in the octave below C3, root of the chord between G3 and F#4.
        assertEquals(listOf(38, 62, 65, 69, 72), ChordSound.midis("Dm7"))
        assertEquals(listOf(40, 60, 64, 67, 70), ChordSound.midis("C7/E"))
        assertEquals(listOf(40, 64, 68, 71, 74, 79), ChordSound.midis("E7#9"))
        assertEquals(listOf(36, 60, 64, 67, 69, 74), ChordSound.midis("C6/9"))
        assertEquals(listOf(36, 60, 67), ChordSound.midis("Cno3"))
        assertEquals(listOf(42, 66, 69, 72, 76), ChordSound.midis("F#m7b5"))
        assertTrue(ChordSound.midis("N.C.").isEmpty())
        assertNull(ChordSound.read("Cxyz"))
        val (notes, seconds) = ChordSound.ofChart(ChordChart.parse("| C / / / | G / Am / |"), 60.0)
        assertEquals(8.0, seconds, 1e-9)
        assertEquals(listOf(0.0, 4.0, 6.0), notes.map { it.start }.distinct())
    }

    @Test fun the_sampler_plays_the_nearest_sample_at_the_note_pitch_and_softer_for_lower_velocity() {
        val rate = 44_100
        // A pure sine for A4 as the only sample: A5 must come out at 880 Hz.
        val a4 = FloatArray(rate * 2) { (0.3 * sin(2 * PI * 440.0 * it / rate)).toFloat() }
        val piano = PianoSampler(listOf(PianoSampler.Layer(80, mapOf(69 to a4))))
        val out = piano.render(listOf(PlayNote(0.0, 0.5, 81, 80)), rate, 0.5)
        var crossings = 0
        for (i in 1 until rate / 2) if (out[i - 1] < 0 && out[i] >= 0) crossings++
        assertEquals(440.0, crossings.toDouble(), 2.0)   // 880 Hz over half a second
        val loud = piano.render(listOf(PlayNote(0.0, 0.5, 69, 112)), rate, 0.5).maxOf { abs(it) }
        val soft = piano.render(listOf(PlayNote(0.0, 0.5, 69, 33)), rate, 0.5).maxOf { abs(it) }
        assertTrue("pp quieter than ff: $soft vs $loud", soft < loud / 4)
        // The damper: well after the release it's silent.
        val tail = piano.render(listOf(PlayNote(0.0, 0.2, 69, 80)), rate, 1.0)
        assertTrue(tail.size < rate || tail.drop(rate * 7 / 10).all { abs(it) < 1e-3f })
    }
}
