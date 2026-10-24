package com.betterappmafia.apophenia.core.learn

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CourseMelodyTest {
    private val M = CourseMelody
    private val C = setOf(0, 4, 7)
    private val G = setOf(7, 11, 2)

    @Test
    fun `non-chord tones are named by how they're approached and left`() {
        assertEquals("chord tone", M.classify(60, 64, 67, C))
        assertEquals("passing tone", M.classify(64, 65, 67, C)); assertEquals("passing tone", M.classify(67, 65, 64, C))
        assertEquals("neighbour tone", M.classify(64, 65, 64, C)); assertEquals("neighbour tone", M.classify(64, 62, 64, C))
        assertEquals("suspension", M.classify(72, 72, 71, G))
        assertEquals("appoggiatura", M.classify(67, 74, 72, C))
        assertEquals("escape tone", M.classify(64, 65, 69, C))
        assertEquals("anticipation", M.classify(62, 60, 60, G))
    }

    @Test
    fun `motion between voices and parallel perfects are detected`() {
        assertEquals("parallel", M.motion(67, 60, 69, 62)); assertTrue(M.parallelPerfect(67, 60, 69, 62))
        assertEquals("similar", M.motion(67, 60, 69, 64)); assertFalse(M.parallelPerfect(67, 60, 69, 64))
        assertEquals("oblique", M.motion(67, 60, 67, 59)); assertEquals("contrary", M.motion(67, 60, 65, 62))
        assertEquals("parallel", M.motion(64, 60, 66, 62))
        assertFalse(M.parallelPerfect(64, 60, 66, 62), "parallel thirds are fine")
        assertTrue(M.parallelPerfect(72, 60, 74, 62), "parallel octaves")
    }

    @Test
    fun `the first-species example keeps every rule the page states`() {
        val cf = M.CANTUS; val cp = M.COUNTER
        assertEquals(cf.size, cp.size)
        assertTrue(cf.indices.all { Math.floorMod(cp[it] - cf[it], 12) in M.CONSONANT }, "consonances only")
        assertTrue(Math.floorMod(cp.first() - cf.first(), 12) in setOf(0, 7) && Math.floorMod(cp.last() - cf.last(), 12) == 0, "perfect at both ends")
        assertTrue((1 until cf.size).none { M.parallelPerfect(cp[it - 1], cf[it - 1], cp[it], cf[it]) }, "no parallel fifths or octaves")
        assertEquals(71 to 72, cp[cp.size - 2] to cp.last(), "leading tone up to the tonic")
        val motions = (1 until cf.size).map { M.motion(cp[it - 1], cf[it - 1], cp[it], cf[it]) }
        assertTrue(motions.count { it == "contrary" } > motions.size / 2, "mostly contrary: $motions")
        assertEquals(listOf(7, 9, 4, 8, 9, 12), cf.indices.map { cp[it] - cf[it] }, "P5, M6, M3, m6, M6, P8")
    }

    @Test
    fun `the forms add up`() {
        assertEquals(32, M.AABA.sumOf { it.second })
        assertEquals(12, M.TWELVE_BAR.size)
        assertEquals(listOf(4, 2, 2, 1, 1, 1, 1), listOf("I7" to 0..3, "IV7" to 4..5, "I7" to 6..7, "V7" to 8..8, "IV7" to 9..9, "I7" to 10..10, "V7" to 11..11).map { (c, r) -> r.count { M.TWELVE_BAR[it] == c } })
        assertEquals("IV7", M.QUICK_CHANGE[1]); assertEquals(M.TWELVE_BAR.drop(2), M.QUICK_CHANGE.drop(2))
    }

    @Test
    fun `the melody checks play what their answers say`() {
        fun notes(id: String, o: Int, seed: Int = 1) = Course.page(id)!!.check!!.options[o].make(60, Random(seed)).piano
        fun leaps(id: String, o: Int, s: Int) = notes(id, o, s).sortedBy { it.start }.zipWithNext().count { (a, b) -> Math.abs(b.midi - a.midi) > 2 }
        (1..6).forEach { s -> assertTrue(leaps("contour", 0, s) <= 1); assertTrue(leaps("contour", 1, s) >= 5) }
        assertEquals(listOf(0, 2, 4, 0, 0, 2, 4, 0), notes("motif", 0).map { it.midi - 60 })
        assertEquals(listOf(0, 2, 4, 0, 2, 4, 5, 2), notes("motif", 1).map { it.midi - 60 })
        // the suspension check: the melody's second note is C (held) or B (arrived)
        fun melody(id: String, o: Int) = notes(id, o).filter { it.midi >= 70 }.sortedBy { it.start }.map { it.midi - 60 }
        assertEquals(listOf(12, 12, 11, 12), melody("nct-other", 0)); assertEquals(listOf(12, 11, 11, 12), melody("nct-other", 1))
        // parallel thirds vs fifths
        fun gaps(id: String, o: Int) = notes(id, o).groupBy { it.start }.values.map { v -> v.maxOf { it.midi } - v.minOf { it.midi } }.toSet()
        assertEquals(setOf(3, 4), gaps("counterpoint-motion", 0)); assertEquals(setOf(7), gaps("counterpoint-motion", 1))
        assertEquals(setOf(7), gaps("species", 1))
    }
}
