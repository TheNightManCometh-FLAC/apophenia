package com.betterappmafia.apophenia.core.interval

import com.betterappmafia.apophenia.core.ALL_35_ROOTS
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.pitch.SpelledPitch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class IntervalTest {

    private fun p(s: String) = SpelledPitch.parse(s)

    @Test
    fun `the augmented fourth and the diminished fifth are different intervals with the same size`() {
        val a4 = Interval(4, 6)
        val d5 = Interval(5, 6)
        assertNotEquals(a4, d5)
        assertEquals("A4", a4.toString())
        assertEquals("d5", d5.toString())
    }

    @Test
    fun `qualities are derived from number and semitones`() {
        val expected = mapOf(
            Interval(1, 0) to "P1", Interval(2, 1) to "m2", Interval(2, 2) to "M2", Interval(2, 3) to "A2",
            Interval(3, 2) to "d3", Interval(3, 3) to "m3", Interval(3, 4) to "M3", Interval(4, 5) to "P4",
            Interval(5, 7) to "P5", Interval(5, 8) to "A5", Interval(6, 8) to "m6", Interval(7, 9) to "d7",
            Interval(7, 10) to "m7", Interval(7, 11) to "M7", Interval(8, 12) to "P8", Interval(1, -1) to "d1",
            Interval(4, 7) to "AA4", Interval(7, 8) to "dd7", Interval(5, 5) to "dd5",
        )
        for ((interval, name) in expected) assertEquals(name, interval.toString(), "$interval")
    }

    @Test
    fun `compound intervals keep their number and reduce to a simple interval`() {
        assertEquals("M9", Interval(9, 14).toString())
        assertEquals("A11", Interval(11, 18).toString())
        assertEquals("m13", Interval(13, 20).toString())
        assertEquals(Interval(2, 2), Interval(9, 14).simple)
        assertEquals(Interval(8, 12), Interval(8, 12).simple)
    }

    @Test
    fun `short names parse back to the same interval`() {
        for (s in listOf("P1", "d1", "m2", "M2", "A2", "d3", "m3", "M3", "d4", "P4", "A4", "AA4", "d5", "dd5",
            "P5", "A5", "m6", "M6", "A6", "d7", "dd7", "m7", "M7", "P8", "m9", "M9", "A9", "P11", "A11", "m13", "M13")) {
            assertEquals(s, Interval.parse(s).toString())
        }
    }

    @Test
    fun `qualities that don't fit the number are refused`() {
        for (s in listOf("M5", "P3", "m4", "X3", "3")) {
            assertFailsWith<IllegalArgumentException> { Interval.parse(s) }
        }
    }

    @Test
    fun `intervals between spelled pitches count letters`() {
        assertEquals("M3", Interval.between(p("C4"), p("E4")).toString())
        assertEquals("d2", Interval.between(p("B#3"), p("C4")).toString())
        assertEquals("A4", Interval.between(p("F4"), p("B4")).toString())
        assertEquals("d5", Interval.between(p("B3"), p("F4")).toString())
        assertEquals("M10", Interval.between(p("C4"), p("E5")).toString())
        assertEquals("d1", Interval.between(p("C4"), p("Cb4")).toString())
    }

    @Test
    fun `intervals between names stay within the octave`() {
        assertEquals("d2", Interval.between(PitchName.parse("B#"), PitchName.parse("C")).toString())
        assertEquals("M3", Interval.between(PitchName.parse("A"), PitchName.parse("C#")).toString())
        assertEquals("A6", Interval.between(PitchName.parse("Ab"), PitchName.parse("F#")).toString())
    }

    @Test
    fun `transposition is letter based`() {
        assertEquals(p("Fx5"), p("E4") + Interval.parse("A9"))
        assertEquals(p("Bbb4"), p("C4") + Interval.parse("d7"))
        assertEquals(p("Cb5"), p("Db4") + Interval.parse("m7"))
        assertEquals(p("A3"), p("C4") - Interval.parse("m3"))
    }

    private val simpleIntervals = listOf("P1", "m2", "M2", "A2", "m3", "M3", "P4", "A4", "d5", "P5", "A5", "m6", "M6", "d7", "m7", "M7", "P8")
        .map(Interval::parse)

    @Test
    fun `every simple interval above all 35 roots lands on the right letter and pitch, or is refused as a triple accidental`() {
        var spelled = 0
        for (root in ALL_35_ROOTS) for (iv in simpleIntervals) {
            val lower = root.at(4)
            val alter = alterAbove(root, iv)
            if (alter !in -2..2) {
                assertFailsWith<IllegalArgumentException> { lower + iv }
                continue
            }
            val upper = lower + iv
            assertEquals(iv, Interval.between(lower, upper), "$lower + $iv = $upper")
            assertEquals(lower.midi + iv.semitones, upper.midi)
            assertEquals(lower, upper - iv)
            assertEquals(upper.name, root + iv)
            assertEquals(root, upper.name - iv)
            spelled++
        }
        // Most combinations spell; only the extremes (e.g. Bbb + d5) need a triple accidental.
        assert(spelled > 35 * simpleIntervals.size * 8 / 10) { "only $spelled spelled" }
    }
}
