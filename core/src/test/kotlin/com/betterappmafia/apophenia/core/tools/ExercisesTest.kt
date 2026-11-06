package com.betterappmafia.apophenia.core.tools

import com.betterappmafia.apophenia.core.catalog.Chords
import com.betterappmafia.apophenia.core.catalog.Scales
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.rhythm.Rational
import com.betterappmafia.apophenia.core.score.Note
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Vetting the curated exercises (Christopher, 2 Nov 2026: "vet them very carefully"): every note, every key. */
class ExercisesTest {
    private val all = Exercises.all
    private fun pn(s: String) = PitchName.parse(s)
    private fun notes(ex: CuratedExercise, root: PitchName = ex.defaultRoot): List<Note> =
        ex.score(root)!!.parts.flatMap { p -> p.bars.flatMap { b -> b.voices.flatten() } }.flatMap { it.notes }
    private fun frets(ns: List<Note>) = ns.groupBy { it.string!! }.toSortedMap(reverseOrder()).values.joinToString(" | ") { s -> s.joinToString(" ") { "${it.fret}" } }
    private fun keysOf(ex: CuratedExercise) = if (ex.inKeys) ex.roots else listOf(ex.defaultRoot)

    @Test fun every_exercise_has_an_id_of_its_own_a_name_a_description_and_a_source() {
        assertEquals(all.size, all.map { it.id }.toSet().size)
        all.forEach { assertTrue(it.name.isNotBlank() && it.what.isNotBlank() && it.source.isNotBlank(), it.id) }
        assertTrue(ExerciseFor.entries.all { f -> all.any { it.forWhom == f } }, "every instrument has exercises")
    }

    @Test fun the_drum_exercises_are_the_forty_pas_rudiments_in_order() {
        val drums = all.filter { it.forWhom == ExerciseFor.DRUMS }
        assertEquals((1..40).toList(), drums.map { it.rudiment!!.number })
        assertEquals("Single Paradiddle", drums[15].name)
        assertTrue(drums.all { it.score() == null })
    }

    @Test fun every_written_exercise_fills_its_bars_exactly_in_every_key() {
        all.filter { it.rudiment == null }.forEach { ex -> keysOf(ex).forEach { r ->
            val s = ex.score(r)!!
            s.parts.forEach { p -> p.bars.forEachIndexed { i, b ->
                val sum = b.voices.first().fold(Rational.ZERO) { acc, ev -> acc + ev.length }
                assertEquals(s.meter.barLength, sum, "${ex.id} in $r, bar ${i + 1}")
            } }
        } }
    }

    @Test fun every_tab_note_is_the_pitch_its_string_and_fret_sound_in_every_key() {
        all.filter { it.forWhom == ExerciseFor.GUITAR || it.forWhom == ExerciseFor.BASS }.forEach { ex -> keysOf(ex).forEach { r ->
            val inst = ex.score(r)!!.parts.single().instrument!!
            notes(ex, r).forEach { n ->
                assertTrue(n.fret!! in 0..inst.frets, "${ex.id} in $r: fret ${n.fret}")
                assertEquals(inst.midiAt(n.string!!, n.fret!!), n.pitch.midi, "${ex.id} in $r: ${n.pitch} on string ${n.string} fret ${n.fret}")
            }
        } }
    }

    @Test fun every_note_belongs_to_the_scale_or_chord_it_practises_in_every_key() {
        fun pcs(root: PitchName, semis: List<Int>) = semis.map { Math.floorMod(root.pitchClass + it, 12) }.toSet()
        val expected = mapOf(
            "major-scale" to Scales.byId("major").semitones, "natural-minor" to Scales.byId("aeolian").semitones,
            "harmonic-minor" to Scales.byId("harmonic-minor").semitones,
            "melodic-minor" to (Scales.byId("melodic-minor").semitones + Scales.byId("aeolian").semitones),
            "major-pentatonic" to Scales.byId("major-pentatonic").semitones, "minor-pentatonic" to Scales.byId("minor-pentatonic").semitones,
            "major-thirds" to Scales.byId("major").semitones, "piano-major-scale" to Scales.byId("major").semitones,
            "voice-five" to Scales.byId("major").semitones, "voice-nine" to Scales.byId("major").semitones, "piano-five-finger" to Scales.byId("major").semitones,
            "bass-major" to Scales.byId("major").semitones,
            "arp-major" to Chords.byId("maj").semitones, "arp-minor" to Chords.byId("m").semitones, "arp-dom7" to Chords.byId("7").semitones,
            "arp-maj7" to Chords.byId("maj7").semitones, "arp-m7" to Chords.byId("m7").semitones, "arp-m7b5" to Chords.byId("m7b5").semitones,
            "arp-dim7" to Chords.byId("dim7").semitones, "piano-arpeggio" to Chords.byId("maj").semitones, "voice-arpeggio" to Chords.byId("maj").semitones,
            "bass-arp-major" to Chords.byId("maj").semitones, "bass-arp-minor" to Chords.byId("m").semitones, "bass-root-fifth" to listOf(0, 7),
        ) + (1..5).associate { "pent-box-$it" to Scales.byId("minor-pentatonic").semitones } + (1..7).associate { "3nps-$it" to Scales.byId("major").semitones }
        expected.forEach { (id, semis) ->
            val ex = assertNotNull(Exercises.byId(id), id)
            keysOf(ex).forEach { r ->
                val ok = pcs(r, semis)
                notes(ex, r).forEach { n -> assertTrue(n.pitch.pitchClass in ok, "$id in $r has ${n.pitch}") }
            }
        }
    }

    @Test fun seven_note_scales_step_through_the_letters_and_keep_to_single_accidentals_in_the_usual_keys() {
        listOf("major-scale", "natural-minor", "melodic-minor", "piano-major-scale").forEach { id ->
            val ex = Exercises.byId(id)!!
            ex.roots.forEach { r ->
                val ps = notes(ex, r).map { it.pitch }
                ps.zipWithNext().forEach { (a, b) -> assertEquals(1, abs((b.letter.ordinal + 7 * b.octave) - (a.letter.ordinal + 7 * a.octave)), "$id in $r: $a to $b is a step") }
                // G# melodic minor's raised 7th is Fx: that one note may be a double sharp, nothing else.
                assertTrue(ps.all { abs(it.alter) <= 1 || (id == "melodic-minor" && it.alter == 2 && Math.floorMod(it.pitchClass - r.pitchClass, 12) == 11) }, "$id in $r: ${ps.joinToString(" ")}")
            }
        }
    }

    @Test fun the_familiar_spellings_come_out_right() {
        fun line(id: String, r: String) = notes(Exercises.byId(id)!!, pn(r)).joinToString(" ") { it.pitch.toString() }
        assertEquals("F#4 G#4 A#4 B4 C#5 D#5 E#5 F#5 E#5 D#5 C#5 B4 A#4 G#4 F#4", line("major-scale", "F#"))
        assertEquals("G#4 A#4 B4 C#5 D#5 E5 Fx5 G#5 Fx5 E5 D#5 C#5 B4 A#4 G#4", line("harmonic-minor", "G#"))
        assertEquals("A4 B4 C5 D5 E5 F#5 G#5 A5 G5 F5 E5 D5 C5 B4 A4", line("melodic-minor", "A"), "melodic minor comes down as natural minor")
        assertEquals("C4 E4 D4 F4 E4 G4 F4 A4 G4 B4 A4 C5 B4 D5 C5 A4 B4 G4 A4 F4 G4 E4 F4 D4 E4 C4 D4 B3 C4", line("major-thirds", "C"))
        assertEquals("B4 D5 F5 Ab5 B5 Ab5 F5 D5 B4", line("arp-dim7", "B"))
        assertEquals("C4 Eb4 Gb4 Bbb4 C5 Bbb4 Gb4 Eb4 C4", line("arp-dim7", "C"), "C°7 has B𝄫, not A")
    }

    /** Box 5 is shown at frets 3–5, below box 1 (the same shape as 15–17). */
    @Test fun the_pentatonic_boxes_are_the_five_standard_a_minor_positions() {
        val a = pn("A")
        assertEquals("5 8 | 5 7 | 5 7 | 5 7 | 5 8 | 5 8", frets(Exercises.pentatonicBox(a, true, 0)))
        assertEquals("8 10 | 7 10 | 7 10 | 7 9 | 8 10 | 8 10", frets(Exercises.pentatonicBox(a, true, 1)))
        assertEquals("10 12 | 10 12 | 10 12 | 9 12 | 10 13 | 10 12", frets(Exercises.pentatonicBox(a, true, 2)))
        assertEquals("12 15 | 12 15 | 12 14 | 12 14 | 13 15 | 12 15", frets(Exercises.pentatonicBox(a, true, 3)))
        assertEquals("3 5 | 3 5 | 2 5 | 2 5 | 3 5 | 3 5", frets(Exercises.pentatonicBox(a, true, 4)))
    }

    @Test fun every_pentatonic_box_and_three_a_string_pattern_climbs_and_stays_within_a_hand_on_each_string() {
        Exercises.MINOR_ROOTS.forEach { r -> (0..4).forEach { b ->
            val ns = Exercises.pentatonicBox(r, true, b)
            assertTrue(ns.zipWithNext().all { (x, y) -> y.pitch.midi > x.pitch.midi }, "box ${b + 1} in $r climbs")
            assertTrue(ns.groupBy { it.string }.values.all { it.size == 2 && it.maxOf { n -> n.fret!! } - it.minOf { n -> n.fret!! } <= 3 }, "box ${b + 1} in $r: ${frets(ns)}")
            assertTrue(ns.maxOf { it.fret!! } - ns.minOf { it.fret!! } <= 4, "box ${b + 1} in $r spans a hand: ${frets(ns)}")
        } }
        Exercises.MAJOR_ROOTS.forEach { r -> (0..6).forEach { p ->
            val ns = Exercises.threePerString(r, p)
            assertTrue(ns.zipWithNext().all { (x, y) -> y.pitch.midi > x.pitch.midi })
            assertTrue(ns.groupBy { it.string }.values.all { it.size == 3 && it.maxOf { n -> n.fret!! } - it.minOf { n -> n.fret!! } <= 4 }, "pattern ${p + 1} in $r: ${frets(ns)}")
        } }
        assertEquals("3 5 7 | 3 5 7 | 4 5 7 | 4 5 7 | 5 7 8 | 5 7 8", frets(Exercises.threePerString(pn("G"), 0)))
    }

    @Test fun the_spider_puts_each_finger_on_its_own_fret_from_the_low_e_and_back() {
        val ns = notes(Exercises.byId("spider-1324")!!)
        assertEquals(48, ns.size)
        assertEquals(listOf(41, 43, 42, 44), ns.take(4).map { it.pitch.midi }, "F, G, F#, G# on the low E")
        assertTrue(ns.all { it.finger == "${it.fret}" })
        assertEquals(6, ns.first().string); assertEquals(6, ns.last().string)
    }

    @Test fun bass_positions_stay_in_reach_and_g_major_is_the_usual_fingering() {
        assertEquals("3 5 | 2 3 5 | 2 4 5", frets(Exercises.bassPosition(pn("G"), (1..8).map { com.betterappmafia.apophenia.core.degree.DegreeToken(it) })))
        assertEquals("3 6 | 5 | 5", frets(Exercises.bassPosition(pn("G"), listOf(1, 3, 5, 8).map { com.betterappmafia.apophenia.core.degree.DegreeToken.parse(if (it == 3) "b3" else "$it") }, behind = 0, ahead = 3)))
    }

    @Test fun piano_fingering_is_the_standard_one_where_it_is_given() {
        val c = notes(Exercises.byId("piano-major-scale")!!, pn("C"))
        assertEquals("C4:1 D4:2 E4:3 F4:1 G4:2 A4:3 B4:4 C5:5", c.take(8).joinToString(" ") { "${it.pitch}:${it.finger}" })
        assertEquals("1 2 3 4 1 2 3 4", notes(Exercises.byId("piano-major-scale")!!, pn("F")).take(8).joinToString(" ") { it.finger })
        assertTrue(notes(Exercises.byId("piano-major-scale")!!, pn("B")).all { it.finger.isEmpty() }, "no fingering where the C-major fingering would be wrong")
        val chrom = notes(Exercises.byId("piano-chromatic")!!)
        assertEquals("1 3 1 3 1 2 3 1 3 1 3 1 2", chrom.take(13).joinToString(" ") { it.finger })
        assertEquals("C5", chrom[12].pitch.toString()); assertEquals("Bb4", chrom[14].pitch.toString())
        assertNull(Exercises.pianoScaleFingers(pn("Db")))
    }

    @Test fun contrary_motion_mirrors_the_hands_from_middle_c() {
        val s = Exercises.byId("piano-contrary")!!.score()!!
        val rh = s.parts[0].bars.flatMap { it.voices.flatten() }.flatMap { it.notes }.map { it.pitch.toString() }
        val lh = s.parts[1].bars.flatMap { it.voices.flatten() }.flatMap { it.notes }.map { it.pitch.toString() }
        assertEquals(listOf("C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5"), rh.take(8))
        assertEquals(listOf("C4", "B3", "A3", "G3", "F3", "E3", "D3", "C3"), lh.take(8))
        assertEquals(rh.size, lh.size)
    }
}
