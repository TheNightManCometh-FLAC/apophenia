package com.betterappmafia.apophenia.core.transpose

import com.betterappmafia.apophenia.core.chord.Chord
import com.betterappmafia.apophenia.core.chord.ChordKind
import com.betterappmafia.apophenia.core.chord.ChordRoot
import com.betterappmafia.apophenia.core.chord.DegreeEdit.Companion.add
import com.betterappmafia.apophenia.core.interval.Interval
import com.betterappmafia.apophenia.core.names
import com.betterappmafia.apophenia.core.pitch.PitchName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransposeTest {

    private fun n(s: String) = PitchName.parse(s)

    @Test
    fun `C to E flat turns D7 into F7, not E sharp 7`() {
        val t = Transpose.chord(Chord.of("D", ChordKind.DOMINANT), n("C"), n("Eb"))
        assertEquals(ChordRoot.Name(n("F")), t.chord.root)
        assertEquals(names("F A C Eb"), t.chord.notes())
        assertFalse(t.theoretical)
    }

    @Test
    fun `root, bass and degrees all move`() {
        val g7b9overB = Chord.of("G", ChordKind.DOMINANT, add(9, -1), bass = "B")
        val t = Transpose.chord(g7b9overB, n("C"), n("Bb")).chord
        assertEquals(ChordRoot.Name(n("F")), t.root)
        assertEquals(n("A"), t.bass)
        assertEquals(names("F A C Eb Gb"), t.notes())
    }

    @Test
    fun `a polychord moves both layers`() {
        val poly = Chord.of("D", ChordKind.MAJOR).copy(poly = Chord.of("C", ChordKind.MAJOR))
        val t = Transpose.chord(poly, Interval.parse("M2")).chord
        assertEquals(ChordRoot.Name(n("E")), t.root)
        assertEquals(ChordRoot.Name(n("D")), t.poly!!.root)
    }

    @Test
    fun `a chord that would need a triple accidental is respelled and reported`() {
        // Fb up a diminished third is Abbb, a triple flat, so the root is respelled from the default table.
        val t = Transpose.chord(Chord.of("Fb", ChordKind.DIMINISHED_SEVENTH), Interval.parse("d3"))
        assertTrue(t.theoretical)
        assertEquals(names("F# A C Eb"), t.chord.notes())
        // A root that moves fine but whose chord tones would need a triple accidental is also respelled.
        val t2 = Transpose.chord(Chord.of("C", ChordKind.DIMINISHED_SEVENTH), Interval.parse("d4"))
        assertTrue(t2.theoretical)
        assertEquals(names("E G Bb Db"), t2.chord.notes())
    }

    @Test
    fun `transposing clears the typed text and keeps N C as N C`() {
        val typed = Chord.of("C", ChordKind.MAJOR).copy(typed = "C")
        assertEquals(null, Transpose.chord(typed, Interval.parse("M2")).chord.typed)
        assertTrue(Transpose.chord(Chord.NO_CHORD, Interval.parse("M2")).chord.isNoChord)
    }
}
