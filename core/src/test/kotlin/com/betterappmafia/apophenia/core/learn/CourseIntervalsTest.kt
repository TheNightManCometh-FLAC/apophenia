package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.catalog.Chords
import com.betterappmafia.apophenia.core.catalog.Scales
import com.betterappmafia.apophenia.core.interval.Interval
import com.betterappmafia.apophenia.core.pitch.PitchName
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CourseIntervalsTest {
    private val C = PitchName.parse("C")
    private fun p(s: String) = PitchName.parse(s)
    private fun notes(id: String, root: String) = ChordPages.page(Chords.byId(id), p(root)).notes.joinToString(" ")

    @Test
    fun `in C major every fourth is perfect except F up to B, and every fifth except B up to F`() {
        val scale = ScalePages.page(Scales.byId("major"), C).notes
        val fourths = scale.indices.map { i -> scale[i] to Interval.between(scale[i], scale[(i + 3) % 7]) }
        assertEquals(listOf("F"), fourths.filter { it.second.toString() != "P4" }.map { it.first.toString() })
        assertEquals("A4", fourths.first { it.first == p("F") }.second.toString())
        val fifths = scale.indices.map { i -> scale[i] to Interval.between(scale[i], scale[(i + 4) % 7]) }
        assertEquals(listOf("B"), fifths.filter { it.second.toString() != "P5" }.map { it.first.toString() })
        assertEquals("d5", fifths.first { it.first == p("B") }.second.toString())
    }

    @Test
    fun `the sizes and spellings quoted on C are right`() {
        fun above(id: String) = IntervalPages.page(Interval.parse(id), C).above.toString()
        assertEquals(listOf(2, 4, 9, 11), listOf("M2", "M3", "M6", "M7").map { Interval.parse(it).semitones })
        assertEquals(listOf(1, 3, 8, 10), listOf("m2", "m3", "m6", "m7").map { Interval.parse(it).semitones })
        assertEquals(listOf(5, 7, 12), listOf("P4", "P5", "P8").map { Interval.parse(it).semitones })
        assertEquals("F#", above("A4")); assertEquals("Gb", above("d5")); assertEquals("D#", above("A2")); assertEquals("Bbb", above("d7"))
        assertEquals(Interval.parse("m3").semitones, Interval.parse("A2").semitones)
        assertEquals(Interval.parse("M6").semitones, Interval.parse("d7").semitones)
        assertEquals(listOf(14, 16, 17, 19, 21), listOf("M9", "M10", "P11", "P12", "M13").map { Interval.parse(it).semitones })
        assertTrue(CourseIntervals.SIMPLE.containsAll(listOf("m2", "M2", "m3", "M3", "P4", "A4", "d5", "P5", "m6", "M6", "m7", "M7", "P8")))
        assertEquals("B𝄫", CourseIntervals.shown(p("Bbb"))); assertEquals("F𝄪", CourseIntervals.shown(p("Fx")))
    }

    @Test
    fun `inversions add to nine and twelve, swap major and minor, keep perfect`() {
        for (id in CourseIntervals.INVERTED) {
            val i = Interval.parse(id)
            val inv = IntervalPages.page(i, C).inversion
            assertEquals(9, i.number + inv.number, id)
            assertEquals(12, i.semitones + inv.semitones, id)
        }
        fun inv(id: String) = IntervalPages.page(Interval.parse(id), C).inversion.toString()
        assertEquals("m6", inv("M3")); assertEquals("M6", inv("m3")); assertEquals("P4", inv("P5")); assertEquals("d5", inv("A4")); assertEquals("A4", inv("d5")); assertEquals("m2", inv("M7"))
    }

    @Test
    fun `compound intervals are their simple interval plus an octave`() {
        for (id in CourseIntervals.COMPOUND.filter { it != "P15" }) {
            val i = Interval.parse(id)
            assertEquals(i.number - 7, i.simple.number, id)
            assertEquals(i.semitones - 12, i.simple.semitones, id)
        }
        assertEquals(24, Interval.parse("P15").semitones)
    }

    @Test
    fun `the chords quoted hold the tritones the page says`() {
        assertEquals("G B D F", notes("7", "G"))
        assertEquals("Db F Ab Cb", notes("7", "Db"))
        assertEquals("d5", Interval.between(p("B"), p("F")).toString())
        assertEquals("A4", Interval.between(p("F"), p("B")).toString())
        assertEquals(setOf(5, 11), setOf(p("F").pitchClass, p("Cb").pitchClass), "D♭7's 3rd and 7th are G7's B and F")
        assertEquals("B D F A", notes("m7b5", "B"))
        assertEquals("C Eb Gb Bbb", notes("dim7", "C"))
        assertEquals("d5", Interval.between(p("Eb"), p("Bbb")).toString())
        assertEquals("C F G", notes("sus4", "C"))
        assertEquals(6, CourseIntervals.TRITONES.map { (a, b, _) -> setOf(a.pitchClass, b.pitchClass) }.distinct().size)
        assertTrue(CourseIntervals.TRITONES.all { (_, _, i) -> i.semitones == 6 && i.toString() in setOf("A4", "d5") })
        // The resolution voicings: B–F to C–E (inward), F–B to E–C (outward) by half steps.
        val resolved = Course.page("iv-resolve")!!.check!!.options[0].make(60, Random(1)).piano.map { it.midi }
        assertEquals(listOf(59, 65, 60, 64), resolved, "B up to C, F down to E")
    }

    @Test
    fun `the interval checks play what their answers say`() {
        fun sizes(id: String, option: Int) = (1..20).map { s ->
            Course.page(id)!!.check!!.options[option].make(60, Random(s)).piano.let { n -> Math.abs(n[1].midi - n[0].midi) }
        }.toSet()
        assertEquals(setOf(1, 2), sizes("iv-count", 0))
        assertTrue(sizes("iv-count", 1).all { it in 3..7 })
        assertEquals(setOf(5), sizes("iv-perfect", 0)); assertEquals(setOf(7), sizes("iv-perfect", 1))
        assertEquals(setOf(4), sizes("iv-major-minor", 0)); assertEquals(setOf(3), sizes("iv-major-minor", 1))
        assertEquals(setOf(4), sizes("iv-invert", 0)); assertEquals(setOf(8), sizes("iv-invert", 1))
        assertEquals(setOf(2), sizes("iv-compound", 0)); assertEquals(setOf(14), sizes("iv-compound", 1))
        assertEquals(listOf(setOf(5), setOf(6), setOf(7)), (0..2).map { sizes("iv-tritone-what", it) })
        assertEquals(listOf(setOf(1), setOf(4), setOf(7), setOf(12)), (0..3).map { sizes("iv-all", it) })
        assertTrue(sizes("iv-consonance", 0).all { it in CourseIntervals.CONSONANT })
        assertTrue(sizes("iv-consonance", 1).all { it in CourseIntervals.DISSONANT })
        val chords = Course.page("iv-tritone-chords")!!.check!!.options
        assertEquals(60 - 17, chords[0].make(60, Random(1)).piano.minOf { it.midi }, "G7: bass G, a fifth above the C below")
        assertEquals(49, chords[1].make(60, Random(1)).piano.first().midi, "D♭7: bass D♭3, a half step above the C3 it goes to")
    }
}
