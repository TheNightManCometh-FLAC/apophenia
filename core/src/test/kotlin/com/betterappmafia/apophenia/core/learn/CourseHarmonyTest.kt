package com.betterappmafia.apophenia.core.learn

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CourseHarmonyTest {
    private val H = CourseHarmony

    @Test
    fun `the chords of a key are named and spelled as the page says`() {
        val triads = H.diatonic("major", false)
        assertEquals(listOf("C E G", "D F A", "E G B", "F A C", "G B D", "A C E", "B D F"), triads.map { it[2] })
        val numerals = triads.map { it[0] }
        listOf("I", "ii", "iii", "IV", "V", "vi", "vii").forEachIndexed { i, n -> assertTrue(numerals[i].startsWith(n) && !numerals[i].startsWith(n + "i") && !numerals[i].startsWith(n + "I") || n == "vii" && numerals[i].startsWith("vii"), "${numerals[i]} is $n") }
        assertTrue(numerals[6].contains("°"), "vii° is diminished: ${numerals[6]}")
        val sevenths = H.diatonic("major", true).map { it[2] }
        assertEquals("G B D F", sevenths[4]); assertEquals("B D F A", sevenths[6]); assertEquals("C E G B", sevenths[0])
        val minor = H.diatonic("aeolian", false).map { it[2] }
        assertEquals(listOf("C E♭ G", "D F A♭", "E♭ G B♭", "F A♭ C", "G B♭ D", "A♭ C E♭", "B♭ D F"), minor)
        assertEquals("G B D", H.diatonic("harmonic-minor", false)[4][2], "harmonic minor's V is major")
    }

    @Test
    fun `progressions are spelled from their tokens`() {
        assertEquals("D F♯ A C", H.spell("2 #4 6 1")); assertEquals("E G♯ B D", H.spell("3 #5 7 2")); assertEquals("A C♯ E G", H.spell("6 #1 3 5"))
        assertEquals("B D♯ F♯ A", H.spell("7 #2 #4 6")); assertEquals("C E G B♭", H.spell("1 3 5 b7"))
        assertEquals("F A♭ C", H.spell("4 b6 1")); assertEquals("E♭ G B♭", H.spell("b3 5 b7")); assertEquals("A♭ C E♭", H.spell("b6 1 b3")); assertEquals("B♭ D F", H.spell("b7 2 4")); assertEquals("D F A♭", H.spell("2 4 b6"))
        assertEquals("A♭ C F♯", H.spell("b6|b6 1 #4")); assertEquals("A♭ C D F♯", H.spell("b6|b6 1 2 #4")); assertEquals("A♭ C E♭ F♯", H.spell("b6|b6 1 b3 #4"))
        assertEquals("E♭ G B♭ D♭", H.spell("b3 5 b7 b2")); assertEquals("A♭ C E♭ G", H.spell("b6 1 b3 5")); assertEquals("E G♯ B D♯", H.spell("3 #5 7 #2"))
        assertEquals(8, H.ch("b6|b6 1 b3 #4").bass, "the augmented sixth has the ♭6 in the bass")
    }

    @Test
    fun `the Neapolitan and the German sixth sound as the page says`() {
        // N6 in C minor: D♭ F A♭ over F.
        assertEquals(setOf(1, 5, 8), EarHarmony.HChord(com.betterappmafia.apophenia.core.degree.DegreeToken.list("4 b2 4 b6")).pcs.toSet())
        // Ger+6 sounds as A♭7, the tritone sub of D7 (V7/V).
        assertEquals(setOf(8, 0, 3, 6), H.ch("b6|b6 1 b3 #4").pcs.toSet())
        assertEquals(setOf(6, 0), setOf(2, 6, 9, 0).intersect(setOf(8, 0, 3, 6)), "A♭7 and D7 share C and F♯/G♭")
    }

    @Test
    fun `negative harmony maps n to 7 − n and turns G7 into Fm6, which is Dø7`() {
        assertEquals(setOf(0, 3, 7), setOf(0, 4, 7).map(H::negative).toSet(), "C → C minor")
        val g7 = setOf(7, 11, 2, 5).map(H::negative).toSet()
        assertEquals(setOf(5, 8, 0, 2), g7, "Fm6: F A♭ C D")
        assertEquals(setOf(2, 5, 8, 0), g7, "= Dø7: D F A♭ C")
        assertEquals(listOf(7, 6, 5, 4, 3, 2, 1, 0, 11, 10, 9, 8), (0..11).map(H::negative))
        assertTrue((0..11).all { H.negative(H.negative(it)) == it })
    }

    @Test
    fun `P, R and L keep two notes and undo themselves`() {
        val c = CourseHarmony.Triad(0, true)
        assertEquals(CourseHarmony.Triad(0, false), H.P(c)); assertEquals(CourseHarmony.Triad(9, false), H.R(c)); assertEquals(CourseHarmony.Triad(4, false), H.L(c))
        assertEquals(CourseHarmony.Triad(8, true), H.L(H.P(c)), "PL: C → A♭")
        for (root in 0..11) for (major in listOf(true, false)) {
            val t = CourseHarmony.Triad(root, major)
            for (f in listOf(H::P, H::R, H::L)) { assertEquals(t, f(f(t))); assertEquals(2, t.pcs.intersect(f(t).pcs).size) }
        }
    }

    @Test
    fun `prime forms and interval vectors are the standard ones`() {
        assertEquals(listOf(0, 3, 7), H.prime(setOf(0, 4, 7))); assertEquals(listOf(0, 3, 7), H.prime(setOf(0, 3, 7)))
        assertEquals(listOf(0, 3, 6), H.prime(setOf(0, 3, 6))); assertEquals(listOf(0, 4, 8), H.prime(setOf(0, 4, 8)))
        assertEquals(listOf(0, 2, 5, 8), H.prime(setOf(0, 4, 7, 10))); assertEquals(listOf(0, 2, 5, 8), H.prime(setOf(0, 3, 6, 10)))
        assertEquals(listOf(0, 1, 5, 8), H.prime(setOf(0, 4, 7, 11))); assertEquals(listOf(0, 3, 5, 8), H.prime(setOf(0, 3, 7, 10)))
        assertEquals(listOf(0, 3, 6, 9), H.prime(setOf(0, 3, 6, 9))); assertEquals(listOf(0, 2, 4, 6, 8, 10), H.prime(setOf(0, 2, 4, 6, 8, 10)))
        assertEquals(listOf(0, 0, 1, 1, 1, 0), H.vector(setOf(0, 4, 7)))
        assertEquals(listOf(0, 0, 4, 0, 0, 2), H.vector(setOf(0, 3, 6, 9)))
        assertEquals(listOf(0, 6, 0, 6, 0, 3), H.vector(setOf(0, 2, 4, 6, 8, 10)))
    }

    @Test
    fun `the harmony checks play what their answers say`() {
        fun basses(id: String, o: Int) = Course.page(id)!!.check!!.options[o].make(60, Random(1)).piano.groupBy { it.start }.toSortedMap().values.map { c -> Math.floorMod(c.minOf { it.midi } - 60, 12) }
        assertEquals(listOf(0, 9, 5, 0), basses("function", 0)); assertEquals(listOf(0, 9, 7, 0), basses("function", 1))
        assertEquals(listOf(0, 5, 7, 0), basses("numerals-minor", 0))
        fun tones(id: String, o: Int, chord: Int) = Course.page(id)!!.check!!.options[o].make(60, Random(1)).piano.groupBy { it.start }.toSortedMap().values.toList()[chord].map { Math.floorMod(it.midi - 60, 12) }.toSet()
        assertEquals(setOf(7, 10, 2), tones("numerals-minor", 0, 2)); assertEquals(setOf(7, 11, 2), tones("numerals-minor", 1, 2))
        assertEquals(setOf(7, 11, 2, 5), tones("negative-harmony", 0, 0)); assertEquals(setOf(5, 8, 0, 2), tones("negative-harmony", 1, 0))
        assertEquals(setOf(2, 5, 9, 0), tones("jazz-251", 0, 0)); assertEquals(setOf(2, 5, 8, 0), tones("jazz-251", 1, 0))
        assertEquals(setOf(4, 8, 11, 2), tones("secondary-chains", 0, 0)); assertEquals(setOf(4, 7, 11, 2), tones("secondary-chains", 1, 0))
        assertEquals(setOf(7, 11, 2), tones("modulation-direct", 0, 1)); assertEquals(setOf(4, 8, 11), tones("modulation-direct", 1, 1))
        // P R L from C: the second chord
        assertEquals(listOf(setOf(0, 3, 7), setOf(9, 0, 4), setOf(4, 7, 11)), (0..2).map { tones("neo-riemannian", it, 1) })
        // the cadence check reuses the Ear's phrases
        assertEquals(EarHarmony.sound(Ear.drill("hm-cadence")!!, "deceptive", 60), Course.page("cadences")!!.check!!.options[3].make(60, Random(1)).piano)
    }
}
