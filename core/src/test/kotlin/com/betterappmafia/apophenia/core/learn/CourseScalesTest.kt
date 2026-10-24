package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.catalog.Scales
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.score.Notation
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CourseScalesTest {
    private fun p(s: String) = PitchName.parse(s)
    private fun major(k: String) = ScalePages.page(Scales.byId("major"), p(k)).notes

    @Test
    fun `each of the fifteen signatures holds exactly the accidentals its major scale spells`() {
        CourseScales.MAJOR_KEYS.forEachIndexed { i, k ->
            val f = Notation.fifths(KeyMode(p(k), "major"))
            assertEquals(i - 7, f, k)
            val spelled = major(k).filter { it.alter != 0 }.map { it.letter.name + if (it.alter > 0) "♯" else "♭" }.toSet()
            assertEquals(CourseScales.signature(f).toSet(), spelled, k)
        }
    }

    @Test
    fun `the shortcuts work for every key`() {
        for (k in CourseScales.MAJOR_KEYS) {
            val f = Notation.fifths(KeyMode(p(k), "major"))
            val sig = CourseScales.signature(f)
            if (f > 0) assertEquals(Math.floorMod(p(sig.last().dropLast(1)).pitchClass + (if (sig.last().endsWith("♯")) 1 else 0) + 1, 12), p(k).pitchClass, "$k: a half step above the last sharp")
            if (f < -1) assertEquals(p(sig[sig.size - 2].dropLast(1) + "b"), p(k), "$k: the second-to-last flat")
        }
        assertEquals("F", CourseScales.MAJOR_KEYS[6])
        assertEquals(listOf("A", "E"), listOf("C", "G").map { major(it)[5].toString() }, "relative minors")
        assertEquals(p("Ab"), major("Cb")[5])
        assertEquals(listOf(p("B").pitchClass, p("F#").pitchClass, p("C#").pitchClass), listOf("Cb", "Gb", "Db").map { p(it).pitchClass }, "the three enharmonic pairs")
    }

    @Test
    fun `the circle goes up a fifth each step, adding a sharp or taking away a flat`() {
        val keys = CourseScales.CIRCLE_MAJOR
        keys.indices.forEach { i -> assertEquals(Math.floorMod(p(keys[i]).pitchClass + 7, 12), p(keys[(i + 1) % 12]).pitchClass) }
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, -5, -4, -3, -2, -1), keys.map { Notation.fifths(KeyMode(p(it), "major")) })
        CourseScales.CIRCLE_MINOR.forEachIndexed { i, m -> assertEquals(Math.floorMod(p(keys[i]).pitchClass - 3, 12), p(m).pitchClass, "relative minor of ${keys[i]}") }
        assertEquals(listOf(7, 6, 5, 4, 3, 2, 2), (0..6).map { CourseScales.shared(it) })
    }

    @Test
    fun `the scale facts on the pages are right`() {
        fun notes(id: String, r: String) = CourseScales.notesOn(id, r)
        assertEquals("C D E F G A B", notes("major", "C"))
        assertEquals("C D E♭ F G A♭ B♭", notes("aeolian", "C"))
        assertEquals("A B C D E F G♯", notes("harmonic-minor", "A"))
        assertEquals(3, Scales.byId("harmonic-minor").semitones.let { it[6] - it[5] }, "the augmented second")
        assertEquals(listOf(0, 2, 3, 5, 7, 9, 11), Scales.byId("melodic-minor").semitones)
        assertEquals("A C D E♭ E G", notes("minor-blues", "A"))
        assertEquals("A C D E G", notes("minor-pentatonic", "A"))
        assertEquals(notes("major-pentatonic", "C").split(" ").toSet(), notes("minor-pentatonic", "A").split(" ").toSet())
        assertEquals(CourseScales.BLACK_KEYS.map { it % 12 }.toSet(), Scales.byId("major-pentatonic").semitones.map { (it + 6) % 12 }.toSet(), "black keys = G♭ major pentatonic")
        assertEquals("G A♭ B♭ C♭ D♭ E♭ F", notes("altered", "G"))
        assertEquals("G A B C♯ D E F", notes("lydian-dominant", "G"))
        assertEquals("B C♯ D E F G A", notes("locrian-sharp2", "B"))
        assertEquals("E F G♯ A B C D", notes("phrygian-dominant", "E"))
        assertEquals(listOf("tonic", "supertonic", "mediant", "subdominant", "dominant", "submediant", "leading tone"), CourseScales.DEGREE_NAMES)
        assertEquals(listOf(2, 2, 1), Scales.byId("major").semitones.let { s -> listOf(s[1] - s[0], s[2] - s[1], s[3] - s[2]) })
        assertEquals(listOf(2, 2, 1), Scales.byId("major").semitones.let { s -> listOf(s[5] - s[4], s[6] - s[5], 12 - s[6]) }, "two matching tetrachords")
    }

    @Test
    fun `bright to dark changes one note per step`() {
        val order = CourseScales.BRIGHT_TO_DARK
        order.zipWithNext().forEach { (a, b) ->
            val sa = Scales.byId(a).semitones; val sb = Scales.byId(b).semitones
            assertEquals(1, sa.indices.count { sa[it] != sb[it] }, "$a → $b")
            assertTrue(sb.sum() < sa.sum(), "$b is darker than $a")
        }
        assertEquals(setOf("major", "dorian", "phrygian", "lydian", "mixolydian", "aeolian", "locrian"), order.toSet())
    }

    @Test
    fun `symmetric scales and Messiaen's modes come in the counts the pages give`() {
        fun t(id: String) = CourseScales.transpositions(Scales.byId(id).semitones.toSet())
        assertEquals(2, t("whole-tone")); assertEquals(3, t("half-whole")); assertEquals(3, t("whole-half")); assertEquals(4, t("augmented"))
        assertEquals(listOf(2, 3, 4, 6, 6, 6, 6), CourseScales.MESSIAEN.map { CourseScales.transpositions(it) })
        assertEquals(Scales.byId("whole-tone").semitones.toSet(), CourseScales.MESSIAEN[0])
        assertEquals(Scales.byId("half-whole").semitones.toSet(), CourseScales.MESSIAEN[1])
        assertEquals(listOf(0, 1, 3, 4, 6, 7, 9, 10), Scales.byId("half-whole").semitones, "C7♭9 tones lie in C half-whole")
        assertTrue(Scales.byId("half-whole").semitones.containsAll(listOf(0, 4, 7, 10, 1)))
        assertTrue(Scales.byId("whole-half").semitones.containsAll(listOf(0, 3, 6, 9)))
        assertEquals(7, CourseScales.MELODIC_MODES.size); assertEquals(7, CourseScales.HARMONIC_MODES.size)
        assertTrue(CourseScales.WORLD.containsAll(listOf("hirajoshi", "in", "iwato", "double-harmonic", "hungarian-minor", "persian")))
    }

    @Test
    fun `the scale checks play what their answers say`() {
        fun played(id: String, option: Int, seed: Int = 1) = Course.page(id)!!.check!!.options[option].make(60, Random(seed)).piano.map { it.midi - 60 }
        assertEquals(Scales.byId("major").semitones + 12, played("scale-major", 0))
        (1..10).forEach { s -> assertEquals(1, played("scale-major", 1, s).zip(Scales.byId("major").semitones + 12).count { (a, b) -> a != b }, "one note off") }
        assertEquals(Scales.byId("aeolian").semitones + 12, played("minor-natural", 1))
        assertEquals(Scales.byId("melodic-minor").semitones + 12, played("minor-melodic", 2))
        assertEquals(Scales.byId("minor-blues").semitones + 12, played("blues", 1))
        assertEquals(listOf(12), played("scale-degrees", 0).takeLast(1)); assertEquals(listOf(11), played("scale-degrees", 1).takeLast(1))
        // the circle check: bass of the second triad up 7 or down 7
        assertEquals(7, played("circle-fifths", 0)[3]); assertEquals(-7, played("circle-fifths", 1)[3])
        val relative = played("key-all", 1)
        assertEquals(Scales.byId("aeolian").semitones.map { it - 3 } + 9, relative)
        assertEquals(60 - 12, Course.page("modes-seven")!!.check!!.options[0].make(60, Random(1)).drone)
    }
}
