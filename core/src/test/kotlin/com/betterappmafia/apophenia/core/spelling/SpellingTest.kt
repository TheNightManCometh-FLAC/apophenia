package com.betterappmafia.apophenia.core.spelling

import com.betterappmafia.apophenia.core.degrees
import com.betterappmafia.apophenia.core.names
import com.betterappmafia.apophenia.core.pitch.PitchClass
import com.betterappmafia.apophenia.core.pitch.PitchName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SpellingTest {

    private fun spell(root: String, ds: String) = Spelling.spell(PitchName.parse(root), degrees(ds))

    private fun assertSpells(expected: String, root: String, ds: String) {
        val s = spell(root, ds)
        assertNull(s.respelledFrom, "$root should not be respelled")
        assertEquals(names(expected), s.notes, "$root $ds")
    }

    @Test
    fun `F major has B flat`() = assertSpells("F G A Bb C D E", "F", "1 2 3 4 5 6 7")

    @Test
    fun `C diminished seventh has B double flat, not A`() = assertSpells("C Eb Gb Bbb", "C", "1 b3 b5 bb7")

    @Test
    fun `E7 sharp 9 has F double sharp, not G`() = assertSpells("E G# B D Fx", "E", "1 3 5 b7 #9")

    @Test
    fun `C7 sharp 9 has D sharp, not E flat`() = assertSpells("C E G Bb D#", "C", "1 3 5 b7 #9")

    @Test
    fun `G sharp major has B sharp`() = assertSpells("G# B# D#", "G#", "1 3 5")

    @Test
    fun `the German sixth in C is A flat C E flat F sharp`() = assertSpells("Ab C Eb F#", "Ab", "1 3 5 #6")

    @Test
    fun `the tritone sub of G7 is D flat F A flat C flat`() = assertSpells("Db F Ab Cb", "Db", "1 3 5 b7")

    @Test
    fun `the altered scale on G sharp spells one letter per degree`() =
        assertSpells("G# A B C D E F#", "G#", "1 b2 b3 b4 b5 b6 b7")

    @Test
    fun `the altered dominant on C spells its chord tones by function`() =
        assertSpells("C Db D# E F# G# Bb", "C", "1 b9 #9 3 #11 #5 b7")

    @Test
    fun `a root that would need a triple accidental is respelled enharmonically`() {
        val s = spell("Fb", "1 b3 b5 bb7")
        assertEquals(PitchName.parse("Fb"), s.respelledFrom)
        assertEquals(PitchName.parse("E"), s.root)
        assertEquals(names("E G Bb Db"), s.notes)
    }

    @Test
    fun `a root the context forces is kept, with double accidentals allowed`() {
        val s = Spelling.spell(PitchName.parse("D#"), degrees("1 2 3 4 5 6 7"), keepRoot = true)
        assertEquals(names("D# E# Fx G# A# B# Cx"), s.notes)
    }

    @Test
    fun `a forced root that needs a triple accidental is reported, not silently respelled`() {
        assertFailsWith<TheoreticalSpellingException> {
            Spelling.spell(PitchName.parse("Fb"), degrees("1 b3 b5 bb7"), keepRoot = true)
        }
    }

    @Test
    fun `an unkeyed pitch class uses the default root table`() {
        assertEquals(names("Eb G Bb"), Spelling.spell(PitchClass(3), degrees("1 3 5")).notes)
        assertEquals(names("F# A# C#"), Spelling.spell(PitchClass(6), degrees("1 3 5")).notes)
    }

    @Test
    fun `enharmonic twins stay within double accidentals`() {
        assertEquals(names("Bx Db").toSet(), Spelling.enharmonicTwins(PitchName.parse("C#")).toSet())
        assertEquals(names("Fb Dx").toSet(), Spelling.enharmonicTwins(PitchName.parse("E")).toSet())
    }
}
