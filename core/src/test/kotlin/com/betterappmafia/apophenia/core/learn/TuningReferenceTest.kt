package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.catalog.Scales
import com.betterappmafia.apophenia.core.instrument.Tunings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TuningReferenceTest {
    private val R = TuningReference
    private fun pcs(id: String) = R.byId(id)!!.tuning.strings.map { it.pitchClass }.toSet()
    private fun chord(root: Int, vararg semis: Int) = semis.map { Math.floorMod(root + it, 12) }.toSet()

    @Test
    fun `every tuning has a unique id, and the app's own tunings are the same objects`() {
        assertEquals(R.all.size, R.all.map { it.tuning.id }.distinct().size)
        for (t in Tunings.all.filter { it.id.startsWith("guitar") }) assertTrue(R.all.any { it.tuning == t }, t.id)
        assertTrue(R.all.size >= 40)
        for (e in R.all) assertNotNull(Reference.item(RefKind.TUNING, e.tuning.id))
        assertEquals(9, R.families.size); for (f in R.families) assertNotNull(R.familyNotes[f], f)
        assertEquals(R.all.size, R.families.sumOf { R.inFamily(it).size })
    }

    @Test
    fun `open and chord tunings ring the chord they're named for`() {
        val expect = mapOf(
            "guitar-open-d" to chord(2, 0, 4, 7), "guitar-open-e" to chord(4, 0, 4, 7), "guitar-open-g" to chord(7, 0, 4, 7),
            "guitar-open-a" to chord(9, 0, 4, 7), "guitar-open-c" to chord(0, 0, 4, 7), "guitar-open-b" to chord(11, 0, 4, 7),
            "guitar-open-f" to chord(5, 0, 4, 7), "guitar-dobro-g" to chord(7, 0, 4, 7),
            "guitar-open-dm" to chord(2, 0, 3, 7), "guitar-open-em" to chord(4, 0, 3, 7), "guitar-open-gm" to chord(7, 0, 3, 7),
            "guitar-open-am" to chord(9, 0, 3, 7), "guitar-open-cm" to chord(0, 0, 3, 7),
            "guitar-dadgad" to chord(2, 0, 5, 7), "guitar-dadead" to chord(2, 0, 2, 7), "guitar-csus2" to chord(0, 0, 2, 7),
            "guitar-gsus4" to chord(7, 0, 5, 7), "guitar-orkney" to chord(0, 0, 2, 7),
            "guitar-c6" to chord(0, 0, 4, 7, 9), "guitar-d6" to chord(2, 0, 4, 7, 9), "guitar-g6" to chord(7, 0, 4, 7, 9),
            "guitar-dmaj7" to chord(2, 0, 4, 7, 11), "guitar-dadd9" to chord(2, 0, 2, 4, 7), "guitar-dm7" to chord(2, 0, 3, 7, 10),
        )
        for ((id, want) in expect) assertEquals(want, pcs(id), id)
        assertEquals("D", R.page(R.byId("guitar-open-d")!!).chords.first().symbol)
        assertEquals("G/D", R.page(R.byId("guitar-open-g")!!).chords.first().symbol)
    }

    @Test
    fun `lowered and drop tunings move the strings their names say`() {
        fun changes(id: String) = R.page(R.byId(id)!!).strings.map { it.change }
        assertEquals(List(6) { -1 }, changes("guitar-eb-standard")); assertEquals(List(6) { -2 }, changes("guitar-d-standard"))
        assertEquals(List(6) { -3 }, changes("guitar-db-standard")); assertEquals(List(6) { -4 }, changes("guitar-c-standard")); assertEquals(List(6) { -5 }, changes("guitar-b-standard"))
        assertEquals(listOf(-2, 0, 0, 0, 0, 0), changes("guitar-drop-d")); assertEquals(listOf(-3, -1, -1, -1, -1, -1), changes("guitar-drop-db"))
        assertEquals(listOf(-4, -2, -2, -2, -2, -2), changes("guitar-drop-c")); assertEquals(listOf(-5, -3, -3, -3, -3, -3), changes("guitar-drop-b"))
        assertEquals(listOf(-7, -5, -5, -5, -5, -5), changes("guitar-drop-a")); assertEquals(listOf(-2, 0, 0, 0, 0, -2), changes("guitar-double-drop-d"))
        assertEquals(listOf(0, 0, 0, 0, 1, 1), changes("guitar-all-fourths")); assertEquals(listOf(12, 12, 12, 12, 0, 0), changes("guitar-nashville"))
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 0, 0), changes("guitar-8-standard")); assertEquals(listOf(-2, 0, 0, 0, 0, 0, 0, 0), changes("guitar-8-drop-e"))
        assertEquals(listOf(-2, 0, 0, 0, 0, 0, 0), changes("guitar-7-drop-a"))
        // Every 6-string tuning stays within a fifth below and a fourth above standard, except the octave-strung Nashville and the regular tunings (all fifths raises the top string to B4).
        for (e in R.all.filter { it.tuning.stringCount == 6 && it.tuning.id != "guitar-nashville" && it.family != "regular" }) for (s in R.page(e).strings) assertTrue(s.change!! in -7..5, "${e.tuning.id} string ${s.number}")
    }

    @Test
    fun `regular tunings repeat one interval, and the string steps are named`() {
        fun steps(id: String) = R.byId(id)!!.tuning.strings.zipWithNext { a, b -> b.midi - a.midi }
        assertEquals(List(5) { 5 }, steps("guitar-all-fourths")); assertEquals(List(5) { 4 }, steps("guitar-major-thirds")); assertEquals(List(5) { 7 }, steps("guitar-all-fifths"))
        assertEquals(listOf(7, 7, 7, 7, 3), steps("guitar-new-standard"))
        assertEquals(listOf("up a P4", "up a P4", "up a P4", "up a M3", "up a P4", null), R.page(R.byId("guitar-standard")!!).strings.map { it.toNext })
        assertEquals("down a m6", R.page(R.byId("guitar-nashville")!!).strings[3].toNext, "G4 down to B3")
        assertEquals("82.41", R.page(R.byId("guitar-standard")!!).strings.first().hz)
    }

    @Test
    fun `Messiaen's modes are in the scale catalog with their sets`() {
        val sets = (3..7).map { Scales.byId("messiaen-$it").semitones.toSet() }
        assertEquals(CourseScales.MESSIAEN.drop(2), sets)
        assertTrue(Scales.byId("whole-tone").names.contains("Messiaen mode 1")); assertTrue(Scales.byId("half-whole").names.contains("Messiaen mode 2"))
        for (i in 3..7) assertNotNull(Scales.byId("messiaen-$i").disputed)
    }
}
