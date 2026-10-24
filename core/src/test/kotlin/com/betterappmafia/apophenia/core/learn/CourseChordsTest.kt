package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.catalog.Chords
import com.betterappmafia.apophenia.core.pitch.PitchName
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CourseChordsTest {
    private fun n(id: String, r: String = "C") = CourseChords.notes(id, r)
    private fun pcs(id: String, r: String) = ChordPages.page(Chords.byId(id), PitchName.parse(r)).notes.map { it.pitchClass }.toSet()

    @Test
    fun `the spellings the chord pages quote are right`() {
        assertEquals("C E G", n("maj")); assertEquals("C E♭ G", n("m")); assertEquals("C E♭ G♭", n("dim")); assertEquals("C E G♯", n("aug"))
        assertEquals("G♯ B♯ D♯", n("maj", "G#"))
        assertEquals("C E G B", n("maj7")); assertEquals("C E G B♭", n("7")); assertEquals("C E♭ G B♭", n("m7"))
        assertEquals("C E♭ G♭ B♭", n("m7b5")); assertEquals("C E♭ G♭ B𝄫", n("dim7"))
        assertEquals("C D G", n("sus2")); assertEquals("C F G", n("sus4")); assertEquals("G C D", n("sus4", "G"))
        assertEquals("C E G D", n("add9")); assertEquals("C E G B♭ D", n("9")); assertEquals("C E G B D", n("maj9")); assertEquals("C E♭ G B♭ D", n("m9"))
        assertEquals("C E G A", n("6")); assertEquals("C E♭ G A", n("m6")); assertEquals("C E G A D", n("69"))
        assertEquals("C E G B♭ D♯", n("7#9")); assertEquals("E G♯ B D F𝄪", n("7#9", "E"))
        assertEquals("C7♭9", CourseChords.symbol("7b9", "C")); assertEquals("G♯", CourseChords.symbol("maj", "G#"))
    }

    @Test
    fun `chords said to share notes do`() {
        assertEquals(pcs("6", "C"), pcs("m7", "A"))
        assertEquals(pcs("m6", "C"), pcs("m7b5", "A"))
        assertEquals(pcs("sus2", "C"), pcs("sus4", "G"))
        assertEquals(pcs("aug", "C"), pcs("aug", "E")); assertEquals(pcs("aug", "C"), pcs("aug", "G#"))
        assertEquals(pcs("7b5", "C"), pcs("7b5", "Gb"))
        assertEquals(pcs("dim7", "C"), pcs("dim7", "Eb"))
        // C11 sounds close to B♭/C: C plus B♭ D F
        assertTrue(pcs("11", "C").containsAll(setOf(0) + pcs("maj", "Bb")))
        // F/G and D/C
        assertEquals(setOf(7, 5, 9, 0), setOf(7) + pcs("maj", "F"))
        assertEquals(pcs("7", "D"), setOf(0) + pcs("maj", "D"), "D/C is D7 in third inversion")
    }

    @Test
    fun `extensions imply the stack and drop the 5th before the guide tones, except the 11 chord`() {
        assertTrue(Chords.byId("9").degrees.map { it.toString() }.containsAll(listOf("b7", "9")))
        assertTrue(Chords.byId("11").degrees.map { it.toString() }.containsAll(listOf("b7", "9", "11")))
        assertTrue("11" !in Chords.byId("13").degrees.map { it.toString() }, "C13 has no natural 11 over its major 3rd")
        assertTrue(Chords.byId("maj7#11").degrees.map { it.toString() }.contains("#11"))
        for (c in Chords.all.filter { it.id != "11" }) {
            assertTrue(c.omitOrder.none { it.toString() in setOf("3", "b3", "7", "b7") }, "${c.id} keeps its guide tones")
        }
        for (id in CourseChords.EXTENDED - "11") assertEquals("5", Chords.byId(id).omitOrder.first().toString(), id)
        assertEquals("3", Chords.byId("11").omitOrder.first().toString())
    }

    @Test
    fun `inversion figures are the classical ones`() {
        assertEquals(listOf("5/3", "6", "6/4"), ChordPages.page(Chords.byId("maj"), PitchName.parse("C")).inversions.map { it.figures })
        assertEquals(listOf("7", "6/5", "4/3", "4/2"), ChordPages.page(Chords.byId("7"), PitchName.parse("G")).inversions.map { it.figures })
        assertEquals(listOf("C", "E", "G"), ChordPages.page(Chords.byId("maj"), PitchName.parse("C")).inversions.map { it.bass.toString() })
    }

    @Test
    fun `upper-structure triads give the tensions the page lists`() {
        assertEquals(listOf("9", "♯11", "13"), CourseChords.upperTensions(2))
        assertEquals(listOf("♯9", "5", "♭7"), CourseChords.upperTensions(3))
        assertEquals(listOf("♯11", "♭7", "♭9"), CourseChords.upperTensions(6))
        assertEquals(listOf("♭13", "1", "♯9"), CourseChords.upperTensions(8))
        assertEquals(listOf("13", "♭9", "3"), CourseChords.upperTensions(9))
    }

    @Test
    fun `the chord checks play what their answers say`() {
        fun played(id: String, o: Int, seed: Int = 1) = Course.page(id)!!.check!!.options[o].make(60, Random(seed)).piano
        fun block(id: String, o: Int, seed: Int = 1) = played(id, o, seed).filter { it.start == 0.0 }.map { it.midi - 60 }.sorted()
        assertEquals(listOf(0, 4, 7), block("triad-build", 0)); assertEquals(listOf(0, 3, 7), block("triad-build", 1))
        assertEquals(listOf(listOf(0, 4, 7), listOf(0, 3, 7), listOf(0, 3, 6), listOf(0, 4, 8)), (0..3).map { block("triad-four", it) })
        assertEquals(listOf(-12, -8, -5), (0..2).map { block("inversions", it).first() })
        assertEquals(listOf(Chords.byId("maj7").semitones, Chords.byId("7").semitones, Chords.byId("m7").semitones), (0..2).map { block("sevenths", it) })
        assertEquals(Chords.byId("m7b5").semitones, block("sevenths-more", 0)); assertEquals(Chords.byId("dim7").semitones, block("sevenths-more", 1))
        assertEquals(listOf(0, 2, 7), block("sus", 1)); assertEquals(listOf(0, 5, 7), block("sus", 2))
        assertEquals(listOf(0, 4, 7, 14), block("add", 0)); assertEquals(listOf(0, 4, 7, 11, 14), block("add", 1))
        assertEquals(listOf(0, 4, 7, 9), block("sixths", 0))
        assertEquals(listOf(-12, 4, 10, 21), block("extensions-left", 1))
        assertEquals(listOf(0, 4, 7, 10, 13), block("altered", 0)); assertEquals(listOf(0, 4, 7, 10, 15), block("altered", 1))
        assertEquals(listOf(0, 5, 10, 15), block("quartal", 0))
        (1..10).forEach { s -> assertTrue(block("slash", 1, s).first() != -12, "another bass note") }
        assertEquals(listOf(-12, -8, -2, 2, 6, 9), block("upper", 0)); assertEquals(listOf(-12, -8, -2, 8, 12, 15), block("upper", 1))
    }
}
