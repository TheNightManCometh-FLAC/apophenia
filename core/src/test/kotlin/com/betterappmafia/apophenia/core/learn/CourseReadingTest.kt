package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.rhythm.Rational
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CourseReadingTest {
    private val R = CourseReading

    @Test
    fun `each clef's lines and spaces are the ones the page names`() {
        assertEquals("E4 G4 B4 D5 F5", R.lines("E4").joinToString(" ")); assertEquals("F4 A4 C5 E5", R.spaces("E4").joinToString(" "))
        assertEquals("G2 B2 D3 F3 A3", R.lines("G2").joinToString(" ")); assertEquals("A2 C3 E3 G3", R.spaces("G2").joinToString(" "))
        assertEquals("C4", R.lines("F3")[2].toString(), "alto: middle C on the middle line")
        assertEquals("C4", R.lines("D3")[3].toString(), "tenor: middle C on the fourth line")
        assertEquals("G4", R.lines("E4")[1].toString(), "the treble clef curls round G4"); assertEquals("F3", R.lines("G2")[3].toString(), "the bass clef's dots sit round F3")
    }

    @Test
    fun `every score example's bars add up to their meter`() {
        val scores = Course.pages.mapNotNull { (it.see as? CourseSee.Score)?.score }
        assertTrue(scores.size >= 7)
        for (s in scores) for (p in s.parts) p.bars.forEachIndexed { i, b ->
            assertEquals(s.meter.barLength, b.voices.first().fold(Rational.ZERO) { a, e -> a + e.duration.length }, "bar ${i + 1}")
        }
        for (s in scores) assertTrue(R.played(s).piano.isNotEmpty())
    }

    @Test
    fun `the tab's frets play the pitches written`() {
        val part = R.tabScore.parts.first()
        val tuning = part.instrument!!.tuning
        val notes = part.bars.flatMap { b -> b.voices.first().flatMap { it.notes } }
        assertEquals(13, notes.size, "seven run notes and a six-note chord")
        for (n in notes) assertEquals(n.pitch.midi, tuning.strings[tuning.stringCount - n.string!!].midi + n.fret!!, "${n.pitch} on string ${n.string} fret ${n.fret}")
    }

    @Test
    fun `the chart in Nashville numbers`() {
        assertEquals("1 6m 4 5 1/3 4 5⁷ 1 ♭7 4/6 5 1", R.CHART.joinToString(" ") { R.nashville(it, "C") })
        assertEquals("1 6m 4 5", listOf("G", "Em", "C", "D").joinToString(" ") { R.nashville(it, "G") })
    }

    @Test
    fun `the road map plays in the order the page states`() {
        assertEquals(listOf(1, 2, 3, 1, 2, 4, 5, 1, 2), R.playOrder())
        assertTrue(Course.page("roadmap")!!.explain.contains("1 2 3 1 2 4 5 1 2"))
    }
}
