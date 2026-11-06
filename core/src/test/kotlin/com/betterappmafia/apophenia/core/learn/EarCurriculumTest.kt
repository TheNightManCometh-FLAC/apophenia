package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.tools.DrumVoice
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The drills added 30 Oct 2026: triads, progressions, modal harmony and voicings, bitonal, reharmonization, polyrhythm. */
class EarCurriculumTest {
    @Test
    fun `every drill says what it is and how to listen`() {
        val generic = EarAbout.of(EarDrill("nothing", "", "", EarKind.INTERVAL, emptyList()))
        for (d in Ear.drills + EarPractice.types.flatMap { b -> EarDirection.entries.map { EarPractice.withDirection(b, it) } } + Ear.chapters.mapNotNull { Ear.drill("mixed-" + it.lowercase().replace(' ', '-')) }) {
            val a = EarAbout.of(d)
            kotlin.test.assertTrue(a.what.length > 40 && a.listen.length > 40, "${d.id} is described")
            if (!d.id.startsWith("mixed")) kotlin.test.assertTrue(a != generic, "${d.id} has its own description")
        }
    }

    @Test
    fun `a harmony chord is voiced in the key it's asked for, not in C`() {
        fun rel(tokens: String, tonic: Int) = EarHarmony.voice(EarHarmony.HChord(com.betterappmafia.apophenia.core.degree.DegreeToken.list(tokens)), tonic).map { Math.floorMod(it - tonic, 12) }
        for (tonic in 48..71) {
            kotlin.test.assertEquals(0, rel("1 1 3 5", tonic).first(), "I's bass on $tonic")
            kotlin.test.assertEquals(setOf(0, 4, 7), rel("1 1 3 5", tonic).drop(1).toSet(), "I on $tonic")
            kotlin.test.assertEquals(setOf(7, 11, 2, 5), rel("5 5 7 2 4", tonic).drop(1).toSet(), "V7 on $tonic")
            kotlin.test.assertEquals(7, rel("5 5 7 2 4", tonic).first(), "V7's bass on $tonic")
        }
        // A stacked upper chord (II over a C7) also follows the key.
        val d = Ear.drill("hm-upper")!!
        for (tonic in listOf(60, 62, 67)) {
            val notes = EarHarmony.sound(d, "II", tonic).filter { it.start == 0.0 }.map { Math.floorMod(it.midi - tonic, 12) }.toSet()
            kotlin.test.assertEquals(setOf(0, 4, 10, 2, 6, 9), notes, "II over 7 on $tonic")
        }
    }

    private val c = PitchName.parse("C")
    private fun d(id: String) = Ear.drill(id)!!

    @Test fun every_drill_names_each_of_its_answers_differently() {
        for (drill in Ear.drills) {
            val labels = drill.answers.map { Ear.label(drill, it) }
            assertEquals(labels.size, labels.toSet().size, "${drill.id}: $labels")
        }
    }

    @Test fun triads_come_in_teaching_order_right_after_intervals() {
        assertEquals("Triads", Ear.chapters[1])
        assertEquals(listOf("tr-majmin", "tr-dimaug", "ch-triads"), Ear.drills.filter { it.chapter == "Triads" }.take(3).map { it.id })
        assertEquals(10, d("tr-all").answers.size)
    }

    @Test fun a_triad_inversion_is_named_and_spelled_from_its_bass() {
        val q = Ear.question(d("tr-inv-min"), EarSettings(randomKey = false), Random(3)).let { Ear.asAnswer(it, "m:1") }
        assertEquals(listOf(3, 7, 12), q.midis.map { it - 48 }, "C minor, 1st inversion: E♭ G C")
        assertEquals("1st inversion", Ear.label(d("tr-inv-min"), "m:1"))
        assertEquals("Minor · 2nd inversion", Ear.label(d("tr-inv-all"), "m:2"))
        assertTrue(EarTriads.explain("lyd:0", c) { it.toString().replace("#", "♯") }.contains("F♯"), "the Lydian triad on C is spelled with F♯")
    }

    @Test fun a_progression_plays_one_chord_per_numeral_and_names_them() {
        val drill = d("hm-prog-major")
        for (a in drill.answers) assertEquals(a.split('-').size, EarHarmony.phrase(drill, a).size)
        assertEquals("I – V – vi – IV", Ear.label(drill, "I-V-vi-IV"))
    }

    @Test fun reharmonization_changes_exactly_one_chord_of_the_repeat() {
        val drill = d("hm-reharm")
        for (a in drill.answers) {
            val p = EarHarmony.phrase(drill, a)
            val changed = (0 until 4).count { p[it] != p[it + 4] }
            assertEquals(1, changed, a)
        }
    }

    @Test fun modal_vamps_and_voicings_sound_different_for_each_mode() {
        for (id in listOf("hm-modal", "sc-modal-voicing")) {
            val drill = d(id)
            val sounds = drill.answers.map { a -> EarHarmony.sound(drill, a, 60).map { Math.floorMod(it.midi, 12) }.toSet() }
            assertEquals(sounds.size, sounds.toSet().size, "$id: every mode has its own notes")
        }
        val lydian = EarHarmony.sound(d("sc-modal-voicing"), "lydian", 60).map { Math.floorMod(it.midi - 60, 12) }.toSet()
        assertTrue(6 in lydian, "Lydian's voicing carries the ♯4")
    }

    @Test fun an_upper_structure_sits_above_its_dominant_seventh() {
        val drill = d("hm-upper")
        for (a in drill.answers) {
            val ch = EarHarmony.phrase(drill, a).single()
            val notes = EarHarmony.voice(ch, 60)
            val lower = notes.take(1 + ch.pcs.size)
            val upper = notes.drop(1 + ch.pcs.size)
            assertEquals(3, upper.size, a)
            assertTrue(upper.min() > lower.max(), "$a: the triad is above the 7th chord")
            assertEquals(upper.sorted(), upper, "$a: stacked upward")
        }
        assertTrue(EarHarmony.explain(drill, "bIII", c) { it.toString().replace("#", "♯") }.contains("D♯"), "♭III over C7 is spelled as a ♯9, D♯")
    }

    @Test fun polyrhythm_plays_each_pulse_evenly_for_three_cycles() {
        for (a in d("rh-poly-more").answers) {
            val (hi, lo) = a.split(':').map { it.toInt() }
            val drums = EarPoly.audio(a).drums
            assertEquals(3 * hi, drums.count { it.voice == DrumVoice.CROSS_STICK }, a)
            assertEquals(3 * lo, drums.count { it.voice == DrumVoice.KICK }, a)
            val gaps = drums.filter { it.voice == DrumVoice.CROSS_STICK }.map { it.at }.zipWithNext { x, y -> y - x }
            assertTrue(gaps.all { kotlin.math.abs(it - gaps[0]) < 1e-9 }, "$a: the high pulse is even")
        }
        assertNotEquals(EarPoly.label("3:2"), EarPoly.label("4:3"))
    }

    @Test fun melodies_by_step_by_leap_chromatic_and_mixed_do_what_they_say() {
        val r = Random(9)
        val major = setOf(0, 2, 4, 5, 7, 9, 11)
        repeat(200) {
            val step = Ear.question(d("dc-mel-3-step"), EarSettings(), r)
            assertTrue(step.midis.zipWithNext { a, b -> kotlin.math.abs(b - a) }.all { it in 1..2 }, "by step: ${step.midis}")
            val leap = Ear.question(d("dc-mel-3-leap"), EarSettings(), r)
            assertTrue(leap.midis.zipWithNext { a, b -> kotlin.math.abs(b - a) }.any { it >= 3 }, "a leap: ${leap.midis}")
            val chrom = Ear.question(d("dc-mel-chromatic"), EarSettings(), r)
            val outside = chrom.midis.filter { Math.floorMod(it - chrom.tonic!!, 12) !in major }
            assertTrue(outside.isNotEmpty(), "a chromatic note: ${chrom.midis}")
            for (m in outside) {
                val i = chrom.midis.indexOf(m)
                assertEquals(1, kotlin.math.abs(chrom.midis[i + 1] - m), "it leads by a half step into the next note")
            }
        }
        val minor = Ear.asAnswer(Ear.question(d("dc-mel-mixed"), EarSettings(), r), "5m")
        val chord = Ear.sound(minor, EarSettings()).piano.filter { it.start == 0.0 }.map { it.midi }.sorted()
        assertEquals(3, chord[1] - chord[0], "a minor melody starts after a minor chord")
    }
}
