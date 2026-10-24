package com.betterappmafia.apophenia.core.chord

import com.betterappmafia.apophenia.core.catalog.KeyMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordDisplayTest {
    private val c = KeyMode.parseOrNull("C major")
    private val am = KeyMode.parseOrNull("A aeolian")
    private fun show(s: String, style: ChordStyle, key: KeyMode? = null) = ChordDisplay.of(s, style, key)!!

    @Test fun pop_and_jazz_write_the_same_chords_their_own_way() {
        val pairs = listOf(
            "Cm7" to ("Cm7" to "C−7"), "Cmaj7" to ("Cmaj7" to "CΔ7"), "Cm7b5" to ("Cm7♭5" to "Cø7"), "Cdim7" to ("Cdim7" to "C°7"),
            "C+" to ("Caug" to "C+"), "Cm(maj7)" to ("Cmmaj7" to "C−Δ7"), "C7#5" to ("C7♯5" to "C+7"), "Bbm9" to ("B♭m9" to "B♭−9"),
        )
        for ((typed, want) in pairs) {
            assertEquals(typed, want.first, show(typed, ChordStyle.POP).text)
            assertEquals(typed, want.second, show(typed, ChordStyle.JAZZ).text)
        }
    }

    @Test fun extensions_are_raised_and_alterations_follow_the_fixed_rules() {
        val t = show("C7(b9,#11)", ChordStyle.POP)
        assertEquals("C" to "7", t.root to t.sup)
        assertEquals(listOf("♭9", "♯11"), t.alterations)
        assertEquals("C7(♭9,♯11)", t.text)
        assertTrue("jazz stacks them", show("C7(b9,#11)", ChordStyle.JAZZ).stacked)
        assertEquals("C7♭9", show("C7-9", ChordStyle.POP).text)          // ♭ never −
        assertEquals("C7sus4", show("C7sus", ChordStyle.POP).text)       // 7sus4, not sus7
        assertEquals("C9sus4", show("C9sus", ChordStyle.POP).text)
        assertEquals("C7sus4♭9", show("C7b9sus", ChordStyle.POP).text)
        assertEquals("Cadd9", show("C2", ChordStyle.POP).text)           // no 7th
        assertEquals("C6/9", show("C69", ChordStyle.POP).text)
        assertEquals("C7alt", show("C7alt", ChordStyle.POP).text)
        assertEquals("C7(13)", show("C7(13)", ChordStyle.POP).text)
        assertEquals("Cno3", show("C(no3)", ChordStyle.POP).text)
        assertEquals("C/E", show("C/E", ChordStyle.POP).text)
        assertEquals("D over C", show("D|C", ChordStyle.POP).text)
        assertEquals("C7b9#11", show("C7(b9,#11)", ChordStyle.PLAIN).text)
    }

    @Test fun classical_names_the_degree_in_the_key_with_quality_signs_and_stacked_figures() {
        assertEquals("V7", show("G7", ChordStyle.CLASSICAL, c).text)
        assertEquals(listOf("6", "5"), show("G7/B", ChordStyle.CLASSICAL, c).figures)
        assertEquals(listOf("4", "3"), show("G7/D", ChordStyle.CLASSICAL, c).figures)
        assertEquals(listOf("4", "2"), show("G7/F", ChordStyle.CLASSICAL, c).figures)
        assertEquals("ii7", show("Dm7", ChordStyle.CLASSICAL, c).text)
        assertEquals("I6", show("C/E", ChordStyle.CLASSICAL, c).text)
        assertEquals("I6/4", show("C/G", ChordStyle.CLASSICAL, c).text)
        assertEquals("viiø7", show("Bm7b5", ChordStyle.CLASSICAL, c).text)
        assertEquals("vii°", show("Bdim", ChordStyle.CLASSICAL, c).text)
        assertEquals("IM7", show("Cmaj7", ChordStyle.CLASSICAL, c).text)
        assertEquals("♭VII", show("Bb", ChordStyle.CLASSICAL, c).text)
        assertEquals("♭III", show("Eb", ChordStyle.CLASSICAL, c).text)
        assertEquals("VII", show("G", ChordStyle.CLASSICAL, am).text)   // natural minor: VII, no flat
        assertEquals("V", show("E", ChordStyle.CLASSICAL, am).text)
        // A♯ isn't a note of C major, so it keeps the spelling typed: ♯VI, not ♭VII.
        assertEquals("♯VI7", show("A#7", ChordStyle.CLASSICAL, c).text)
        // No key: pop.
        assertEquals("G7", show("G7", ChordStyle.CLASSICAL).text)
    }

    @Test fun names_follow_the_dialect_with_real_accidentals() {
        val german = ChordDialect(names = ChordDialect.Names.GERMAN)
        assertEquals("Hm7", ChordDisplay.of("Hm7", ChordStyle.POP, null, german)!!.text)
        assertEquals("B7", ChordDisplay.of("B7", ChordStyle.POP, null, german)!!.text)
        assertEquals("F𝄪", ChordDisplay.name(com.betterappmafia.apophenia.core.pitch.PitchName.parse("Fx"), ChordDialect.Names.ENGLISH))
        assertEquals("Si♭", ChordDisplay.name(com.betterappmafia.apophenia.core.pitch.PitchName.parse("Bb"), ChordDialect.Names.SOLFEGE))
    }
}
