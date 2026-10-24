package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.catalog.Chords
import com.betterappmafia.apophenia.core.catalog.Scales
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CommentaryTest {
    private fun set(id: String) = Scales.byId(id).semitones.toSet()
    private fun rotate(s: Set<Int>, from: Int) = s.map { Math.floorMod(it - from, 12) }.toSet()

    @Test
    fun `every catalog chord and scale has a hand-written function row, every interval row names a real interval`() {
        for (c in Chords.all) assertNotNull(Commentary.chords[c.id]?.function, c.id)
        for (s in Scales.all) assertNotNull(Commentary.scales[s.id]?.function, s.id)
        assertTrue(Commentary.chords.keys.all { id -> Chords.all.any { it.id == id } }); assertTrue(Commentary.scales.keys.all { id -> Scales.all.any { it.id == id } })
        val ids = Reference.intervals.map { it.toString() }.toSet()
        for (k in Commentary.intervals.keys) assertTrue(k in ids, k)
        for (i in Reference.intervals.filter { !it.isCompound && it.quality.kind.name in setOf("PERFECT", "MAJOR", "MINOR") }) assertNotNull(Commentary.intervals[i.toString()]?.function, "$i")
        assertNotNull(Commentary.intervals["m3"]?.where, "the old WHERE rows are kept")
    }

    @Test
    fun `the relations the rows state hold`() {
        assertEquals(set("major-pentatonic").let { rotate(it, 2) }, set("suspended-pentatonic"), "suspended pentatonic = major pentatonic from its 2nd")
        assertEquals(rotate(set("in"), 7), set("iwato"), "Iwato = In from its 4th note")
        assertEquals(rotate(set("double-harmonic"), 5), set("hungarian-minor"), "Hungarian minor = 4th mode of the double harmonic")
        assertEquals(set("double-harmonic") - 7 + 6, set("persian"))
        assertEquals(set("harmonic-minor") - 2 + 1, set("neapolitan-minor")); assertEquals(set("melodic-minor") - 2 + 1, set("neapolitan-major"))
        assertEquals(setOf(0, 4, 8) + setOf(3, 7, 11), set("augmented"))
        val messiaen = (3..7).map { set("messiaen-$it") }
        assertEquals(listOf(9, 8, 6, 8, 10), messiaen.map { it.size })
        assertEquals(listOf(4, 6, 6, 6, 6), messiaen.map { s -> (0 until 12).map { t -> s.map { (it + t) % 12 }.toSet() }.distinct().size })
        for (s in messiaen.drop(1)) assertEquals(s, s.map { (it + 6) % 12 }.toSet(), "modes 4–7 repeat at the tritone")
        // G7♭5 and D♭7♭5 share their notes, and both are the French sixth in C (A♭ C D F♯ = D7♭5).
        val b5 = Chords.byId("7b5").semitones.map { Math.floorMod(it, 12) }
        assertEquals(b5.map { (it + 7) % 12 }.toSet(), b5.map { (it + 1) % 12 }.toSet())
        assertEquals(setOf(8, 0, 2, 6), b5.map { (it + 2) % 12 }.toSet())
        // The So What chord E A D G B is Em11 (no 9th).
        assertTrue(listOf(4, 9, 2, 7, 11).map { Math.floorMod(it - 4, 12) }.toSet().all { it in Chords.byId("m11").semitones.map { s -> s % 12 } })
    }
}
