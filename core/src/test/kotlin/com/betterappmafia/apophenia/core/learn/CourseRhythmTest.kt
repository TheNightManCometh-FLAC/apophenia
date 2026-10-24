package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.rhythm.Rational
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import com.betterappmafia.apophenia.core.tools.DrumVoice
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CourseRhythmTest {
    @Test
    fun `every written bar fills its meter exactly`() {
        val sees = Course.pages.mapNotNull { it.see as? CourseSee.Rhythm }
        assertTrue(sees.size >= 8)
        for (s in sees) for ((label, meter, notes) in s.bars) {
            assertEquals(meter.barLength, notes.fold(Rational.ZERO) { a, n -> a + n.d.length }, "${s.caption} · $label")
        }
    }

    @Test
    fun `the meters are grouped as the pages say`() {
        assertEquals(2, CourseRhythm.SIX_EIGHT.beatsPerBar)
        assertEquals(listOf(Rational.of(3, 8), Rational.of(3, 8)), CourseRhythm.SIX_EIGHT.beatLengths)
        assertTrue(CourseRhythm.SIX_EIGHT.isCompound)
        assertEquals(listOf(2, 2, 3), CourseRhythm.SEVEN_EIGHT.groups)
        assertEquals(CourseRhythm.SIX_EIGHT.barLength, CourseRhythm.THREE.barLength, "6/8 and 3/4 hold the same six eighths")
        assertEquals(listOf(3, 3), TimeSignature.of(6, 8).groups); assertEquals(listOf(3, 3, 3, 3), TimeSignature.of(12, 8).groups)
        assertEquals(5, CourseRhythm.FIVE_FOUR.numerator)
    }

    @Test
    fun `swung eighths fall two thirds of the way through the beat`() {
        val bar = List(8) { RNote(com.betterappmafia.apophenia.core.rhythm.Duration.EIGHTH) }
        val snare = EarRhythm.drums(bar, CourseRhythm.FOUR, 100, 0.0, swing = true).filter { it.voice == DrumVoice.SNARE }.map { it.at }
        val beat = 0.6
        assertEquals(beat * CourseRhythm.SWING, snare[1], 1e-9)
        assertEquals(beat + beat * 2 / 3, snare[3], 1e-9)
        assertEquals(beat * 0.5, EarRhythm.drums(bar, CourseRhythm.FOUR, 100, 0.0).filter { it.voice == DrumVoice.SNARE }[1].at, 1e-9)
    }

    @Test
    fun `polyrhythms meet only on the first note`() {
        assertEquals(listOf(0, 2, 4) to listOf(0, 3), CourseRhythm.poly(3, 2))
        assertEquals(listOf(0, 3, 6, 9) to listOf(0, 4, 8), CourseRhythm.poly(4, 3))
        for ((a, b) in listOf(3 to 2, 4 to 3, 5 to 4)) { val (x, y) = CourseRhythm.poly(a, b); assertEquals(setOf(0), x.toSet().intersect(y.toSet())) }
    }

    @Test
    fun `metric modulation and tempo arithmetic are right`() {
        assertEquals(180.0, CourseRhythm.modulate(120.0, Rational.of(1, 3), Rational.of(1, 2)), 1e-9)
        assertEquals(80.0, CourseRhythm.modulate(120.0, Rational.of(3, 2), Rational.ONE), 1e-9)
        assertEquals(60.0, CourseRhythm.modulate(120.0, Rational.of(2), Rational.ONE), 1e-9)
        assertEquals(240.0, CourseRhythm.modulate(120.0, Rational.of(1, 2), Rational.ONE), 1e-9)
        assertEquals(180.0, CourseRhythm.modulate(120.0, Rational.of(2, 3), Rational.ONE), 1e-9)
        assertEquals(2.0, EarRhythm.barSeconds(CourseRhythm.FOUR, 120), 1e-9)
        assertEquals(1.0, 60.0 / 60, 1e-9)
    }

    @Test
    fun `the rhythm checks play what their answers say`() {
        fun snares(id: String, o: Int, seed: Int = 1) = Course.page(id)!!.check!!.options[o].make(60, Random(seed)).drums.filter { it.voice == DrumVoice.SNARE }
        assertEquals(listOf(4, 8, 16), (0..2).map { snares("values", it).size })
        assertEquals(listOf(8, 12), (0..1).map { snares("tuplets", it).size })
        fun hats(id: String, o: Int) = Course.page(id)!!.check!!.options[o].make(60, Random(1)).drums.filter { it.voice == DrumVoice.HAT }.map { it.at }
        val eighth = 60.0 / 100 / 2
        assertEquals(listOf(0, 2, 4), hats("odd", 0).map { Math.round((it - EarRhythm.barSeconds(CourseRhythm.SEVEN_EIGHT, 100)) / eighth).toInt() })
        assertEquals(listOf(0, 3, 5), hats("odd", 1).map { Math.round((it - EarRhythm.barSeconds(TimeSignature(listOf(3, 2, 2), 8), 100)) / eighth).toInt() })
        fun accents(id: String, o: Int) = Course.page(id)!!.check!!.options[o].make(60, Random(1)).drums.take(6).map { it.hit == com.betterappmafia.apophenia.core.tools.DrumHit.ACCENT }
        assertEquals(listOf(true, false, false, true, false, false), accents("meter-compound", 0))
        assertEquals(listOf(true, false, true, false, true, false), accents("meter-compound", 1))
        val poly = Course.page("poly")!!.check!!.options
        assertEquals(9 to 6, poly[0].make(60, Random(1)).piano.partition { it.midi == 79 }.let { (hi, lo) -> hi.size to lo.size })
        assertEquals(12 to 9, poly[1].make(60, Random(1)).piano.partition { it.midi == 79 }.let { (hi, lo) -> hi.size to lo.size })
        // faster/slower
        (1..10).forEach { s ->
            val f = Course.page("pulse")!!.check!!.options[0].make(60, Random(s)).drums.map { it.at }
            assertTrue(f[5] - f[4] < f[1] - f[0])
            val sl = Course.page("pulse")!!.check!!.options[1].make(60, Random(s)).drums.map { it.at }
            assertTrue(sl[5] - sl[4] > sl[1] - sl[0])
        }
    }
}
