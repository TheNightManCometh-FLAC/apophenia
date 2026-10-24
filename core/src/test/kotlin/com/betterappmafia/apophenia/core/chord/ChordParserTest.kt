package com.betterappmafia.apophenia.core.chord

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.chord.DegreeEdit.Companion.add
import com.betterappmafia.apophenia.core.chord.DegreeEdit.Companion.alter
import com.betterappmafia.apophenia.core.chord.DegreeEdit.Companion.subtract
import com.betterappmafia.apophenia.core.pitch.PitchName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordParserTest {
    private val pop = ChordDialect()
    private val jazz = ChordDialect(ChordDialect.Style.JAZZ)
    private val german = ChordDialect(names = ChordDialect.Names.GERMAN)
    private val solfege = ChordDialect(names = ChordDialect.Names.SOLFEGE)

    private fun read(s: String, d: ChordDialect = pop): Chord = ChordParser.parse(s, d).chord ?: throw AssertionError("$s didn't read: ${ChordParser.parse(s, d).problem}")
    private fun shape(s: String, d: ChordDialect = pop) = read(s, d).let { it.kind to it.degrees }
    private fun root(s: String, d: ChordDialect = pop) = (read(s, d).root as ChordRoot.Name).name

    @Test fun every_common_way_of_writing_a_quality_reads_as_the_same_structure() {
        val same = mapOf(
            ChordKind.MAJOR_SEVENTH to listOf("Cmaj7", "CM7", "CΔ7", "CΔ", "C^7", "C^", "Cma7", "Cj7", "C7M"),
            ChordKind.MINOR_SEVENTH to listOf("Cm7", "Cmin7", "Cmi7", "C-7"),
            ChordKind.HALF_DIMINISHED to listOf("Cø", "Cø7", "Ch", "Ch7", "Cm7b5", "Cm7-5", "C-7b5", "Cmi7(♭5)", "Cm7(b5)"),
            ChordKind.DIMINISHED_SEVENTH to listOf("Cdim7", "C°7", "Co7"),
            ChordKind.AUGMENTED_SEVENTH to listOf("C+7", "C7#5", "C7(+5)", "Caug7", "C7♯5"),
            ChordKind.MAJOR_MINOR to listOf("Cm(maj7)", "CmM7", "Cm^7", "C-^7", "C-Δ7", "CmMaj7", "Cm#7", "Cmi(ma7)"),
            ChordKind.AUGMENTED to listOf("C+", "Caug", "C(#5)", "C+5"),
            ChordKind.MINOR to listOf("Cm", "Cmin", "C-", "Cmi"),
            ChordKind.DOMINANT_NINTH to listOf("C9", "C7(9)"),
            ChordKind.MINOR_NINTH to listOf("Cm9", "C-9", "Cm7(9)"),
        )
        for ((kind, forms) in same) for (f in forms) assertEquals("$f", kind to emptyList<DegreeEdit>(), shape(f))
    }

    @Test fun extensions_alterations_and_omissions_become_degree_edits() {
        val b9s11 = ChordKind.DOMINANT to listOf(add(9, -1), add(11, 1))
        for (f in listOf("C7(b9,#11)", "C7(b9 #11)", "C7b9#11", "C7(♭9,♯11)", "C7-9+11")) assertEquals(f, b9s11, shape(f))
        assertEquals(ChordKind.DOMINANT to listOf(add(9, 1)), shape("C7+9"))
        assertEquals(ChordKind.MAJOR_SIXTH to listOf(add(9)), shape("C6/9"))
        assertNull("6/9 is not a slash chord", read("C6/9").bass)
        assertEquals(ChordKind.MAJOR to listOf(add(9)), shape("Cadd9"))
        assertEquals(ChordKind.MAJOR to listOf(add(9)), shape("C(9)"))
        assertEquals(ChordKind.MAJOR to listOf(subtract(3)), shape("C(no3)"))
        assertEquals(ChordKind.MAJOR to listOf(subtract(3)), shape("Comit3"))
        assertEquals(ChordKind.MAJOR to listOf(subtract(3)), shape("C(no 3rd)"))
        assertEquals(ChordKind.DOMINANT to listOf(alter(5, -1)), shape("C7b5"))
        assertEquals(ChordKind.SUSPENDED_FOURTH to listOf(add(7), add(9)), shape("C9sus4"))
        assertEquals(ChordKind.SUSPENDED_FOURTH to listOf(add(7), add(9, -1)), shape("C7b9sus"))
        assertEquals(ChordKind.SUSPENDED_FOURTH to listOf(add(7)), shape("C7sus"))
        assertEquals(ChordKind.MINOR to listOf(add(13, -1)), shape("C-b13"))
        assertEquals("7alt", read("C7alt").quality)
        assertEquals("m7b5", read("Cø7").quality)
        assertEquals(ChordKind.POWER, read("C5").kind)
    }

    @Test fun slash_basses_polychords_and_no_chord() {
        assertEquals(PitchName.parse("E"), read("C/E").bass)
        assertEquals(PitchName.parse("G#"), read("Am/G#").bass)
        assertEquals(PitchName.parse("F#"), read("D/F♯").bass)
        val poly = read("D|C")
        assertEquals(PitchName.parse("C"), (poly.root as ChordRoot.Name).name)
        assertEquals(PitchName.parse("D"), (poly.poly!!.root as ChordRoot.Name).name)
        assertEquals(poly.poly, read("D over C").poly)
        for (nc in listOf("N.C.", "NC", "n", "N", "nc")) assertTrue(nc, read(nc).isNoChord)
    }

    @Test fun ambiguous_symbols_follow_the_dialect_and_show_the_other_reading() {
        fun alt(s: String, d: ChordDialect) = ChordParser.parse(s, d).alternatives.single().chord.let { it.kind to it.degrees }
        assertEquals(ChordKind.MAJOR to listOf(add(9)), shape("C2"))
        assertEquals(ChordKind.SUSPENDED_SECOND to emptyList<DegreeEdit>(), alt("C2", pop))
        assertEquals(ChordKind.SUSPENDED_SECOND to emptyList<DegreeEdit>(), shape("C2", jazz))
        assertEquals(ChordKind.MAJOR to listOf(add(9)), alt("C2", jazz))
        assertEquals(ChordKind.DIMINISHED, read("C°").kind)
        assertEquals(ChordKind.DIMINISHED_SEVENTH, read("C°", jazz).kind)
        assertEquals(ChordKind.AUGMENTED_SEVENTH, read("C7+").kind)
        assertEquals(ChordKind.MAJOR_SEVENTH to emptyList<DegreeEdit>(), alt("C7+", pop))
        assertEquals(ChordKind.MAJOR, read("Cmaj").kind)
        assertEquals(ChordKind.MAJOR_SEVENTH to emptyList<DegreeEdit>(), alt("Cmaj", pop))
        assertEquals(ChordKind.DOMINANT_11TH, read("C11").kind)
        assertEquals(ChordKind.SUSPENDED_FOURTH to listOf(add(7), add(9)), alt("C11", pop))
        assertEquals(ChordKind.MAJOR, read("c").kind)
        assertEquals(ChordKind.MINOR to emptyList<DegreeEdit>(), alt("c", pop))
        // Plain symbols carry no alternatives.
        assertTrue(ChordParser.parse("Dm7").alternatives.isEmpty())
    }

    @Test fun german_and_solfege_names() {
        assertEquals(PitchName.parse("B"), root("H7", german))
        assertEquals(PitchName.parse("Bb"), root("B", german))
        assertEquals(PitchName.parse("F#"), root("Fis-7", german))
        assertEquals(ChordKind.MINOR_SEVENTH, read("Fis-7", german).kind)
        assertEquals(PitchName.parse("Eb"), root("Es", german))
        assertEquals(PitchName.parse("Ab"), root("As7", german))
        assertEquals(PitchName.parse("C#"), root("Cis", german))
        assertEquals(PitchName.parse("F#"), root("F#m", german))
        // E sus4 and A sus, not E♭ "us4".
        assertEquals(PitchName.parse("E") to ChordKind.SUSPENDED_FOURTH, root("Esus4", german) to read("Esus4", german).kind)
        assertEquals(PitchName.parse("A") to ChordKind.SUSPENDED_FOURTH, root("Asus", german) to read("Asus", german).kind)
        assertEquals(PitchName.parse("B"), read("G/H", german).bass)
        assertEquals(PitchName.parse("B"), root("Sim", solfege))
        assertEquals(ChordKind.MINOR, read("Sim", solfege).kind)
        assertEquals(PitchName.parse("C#"), root("Do#7", solfege))
        assertEquals(PitchName.parse("G"), root("Sol7", solfege))
        assertEquals(PitchName.parse("A") to ChordKind.MINOR_SEVENTH, root("Lam7", solfege) to read("Lam7", solfege).kind)
        // German is never guessed from English input.
        assertFalse(ChordParser.parse("H7").readable)
        assertEquals("H", ChordParser.name(PitchName.parse("B"), ChordDialect.Names.GERMAN))
        assertEquals("B", ChordParser.name(PitchName.parse("Bb"), ChordDialect.Names.GERMAN))
        assertEquals("Fis", ChordParser.name(PitchName.parse("F#"), ChordDialect.Names.GERMAN))
        assertEquals("Sib", ChordParser.name(PitchName.parse("Bb"), ChordDialect.Names.SOLFEGE))
    }

    @Test fun parsed_chords_spell_their_traps_correctly() {
        assertEquals("C Eb Gb Bbb", read("Cdim7").notes().joinToString(" "))
        assertEquals("E G# B D Fx", read("E7#9").notes().joinToString(" "))
        assertEquals("C E G Bb D#", read("C7#9").notes().joinToString(" "))
        assertEquals("G# B# D#", read("G#").notes().joinToString(" "))
        assertEquals("Db F Ab Cb", read("Db7").notes().joinToString(" "))
    }

    @Test fun chords_are_spelled_in_the_key_where_the_root_is_a_note_of_it() {
        val f = KeyMode.parseOrNull("F major"); val c = KeyMode.parseOrNull("C major"); val e = KeyMode.parseOrNull("E major")
        assertEquals("Bb7", ChordSpelling.inKey("A#7", f))
        assertEquals("Db7", ChordSpelling.inKey("Db7", c))
        assertEquals("C#m7/F#", ChordSpelling.inKey("Dbm7/Gb", e))
        assertEquals("Dm7", ChordSpelling.inKey("Dm7", c))
        assertEquals("B7", ChordSpelling.inKey("Ais7", f, german))
        assertEquals("Cxyz", ChordSpelling.inKey("Cxyz", f))
    }

    @Test fun every_quality_in_the_ireal_pro_list_reads_on_every_root_in_both_styles() {
        // The official quality vocabulary (irealpro.com/ireal-pro-file-format), facts not content.
        val qualities = ("5, 2, add9, +, o, h, sus, ^, -, ^7, -7, 7, 7sus, h7, o7, ^9, ^13, 6, 69, ^7#11, ^9#11, ^7#5, -6, -69, -^7, " +
            "-^9, -9, -11, -7b5, h9, -b6, -#5, 9, 7b9, 7#9, 7#11, 7b5, 7#5, 9#11, 9b5, 9#5, 7b13, 7#9#5, 7#9b5, 7#9#11, 7b9#11, " +
            "7b9b5, 7b9#5, 7b9#9, 7b9b13, 7alt, 13, 13#11, 13b9, 13#9, 7b9sus, 7susadd3, 9sus, 13sus, 7b13sus, 11, min13, min^11, " +
            "min^13, maj13#11, maj7b5, maj7#9, min7b6, min9b6, maj(add4), min(add4), 7(add13)").split(", ")
        val roots = "C, C#, Db, D, D#, Eb, E, F, F#, Gb, G, G#, Ab, A, A#, Bb, B".split(", ")
        for (d in listOf(pop, jazz)) for (r in roots) for (q in qualities) {
            val p = ChordParser.parse(r + q, d)
            assertTrue("$r$q: ${p.problem}", p.readable)
            assertEquals("$r$q", PitchName.parse(r), (p.chord!!.root as ChordRoot.Name).name)
        }
        assertEquals(ChordKind.DOMINANT_13TH to listOf(alter(11, 1)), shape("C13#11"))
        assertEquals(ChordKind.MINOR to listOf(add(7, 1), add(9)), shape("C-^9"))
        assertEquals(ChordKind.SUSPENDED_FOURTH to listOf(add(7), add(3)), shape("C7susadd3"))
    }

    @Test fun a_reading_is_written_out_plainly_so_the_other_reading_can_be_shown() {
        fun alt(s: String) = ChordSpelling.symbol(ChordParser.parse(s).alternatives.single().chord)
        assertEquals("Csus2", alt("C2"))
        assertEquals("C9sus4", alt("C11"))
        assertEquals("Cmaj7", alt("Cmaj"))
        assertEquals("Cm", alt("c"))
        assertEquals("C7b9#11", ChordSpelling.symbol(read("C7(b9,#11)")))
        assertEquals("Cno3", ChordSpelling.symbol(read("C(no3)")))
        assertEquals("C7(13)/E", ChordSpelling.symbol(read("C7(13)/E")))
        assertEquals("Bm7b5", ChordSpelling.symbol(read("Hø", german)))
        assertEquals("Hm7b5", ChordSpelling.symbol(read("Hø", german), ChordDialect.Names.GERMAN))
    }

    @Test fun text_that_isnt_a_chord_says_why() {
        for (bad in listOf("Cxyz", "", "C/", "C/H", "Dm7/", "Comit", "X7"))
            assertFalse(bad, ChordParser.parse(bad).readable)
        assertTrue(ChordParser.parse("Q7").problem!!.contains("root"))
    }
}
