package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.chord.ChordDisplay
import com.betterappmafia.apophenia.core.chord.ChordStyle
import com.betterappmafia.apophenia.core.pitch.PitchName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProgressionsTest {
    private val P = Progressions
    private val C = PitchName.parse("C")
    private fun roots(id: String, key: String = "C") = P.page(P.byId(id), PitchName.parse(key)).chords.joinToString(" ") { it.root.toString() + ":" + it.step.chord }

    @Test
    fun `every progression is well formed and in a family with a note`() {
        assertTrue(P.all.size >= 25)
        assertEquals(P.all.size, P.all.map { it.id }.distinct().size)
        for (p in P.all) { for (s in p.steps) { s.def; s.token }; assertNotNull(Reference.item(RefKind.PROGRESSION, p.id)); assertTrue(p.where.isNotBlank()) }
        for (f in P.families) assertNotNull(P.familyNotes[f], f)
        assertEquals(5, P.families.size)
    }

    @Test
    fun `progressions spell the chords the musicians would write`() {
        assertEquals("C:maj F:maj G:maj C:maj", roots("one-four-five"))
        assertEquals("C:maj G:maj A:m F:maj", roots("axis"))
        assertEquals("D:m7 G:7 C:maj7", roots("ii-v-i"))
        assertEquals("D:m7b5 G:7b9 C:m6", roots("ii-v-i-minor"))
        assertEquals("D:m7 Db:7 C:maj7", roots("tritone-sub"))
        assertEquals("F:m7 Bb:7 C:maj7", roots("backdoor"))
        assertEquals("E:7 A:7 D:7 G:7 C:maj", roots("ragtime"))
        assertEquals("D:m7 Eb:7 Ab:maj7 B:7 E:maj7 G:7 C:maj7", roots("coltrane"))
        assertEquals("A:m G:maj F:maj E:maj", roots("andalusian", "A"))
        assertEquals("C:m Ab:maj Bb:maj C:m", roots("aeolian"))
        assertEquals("C:maj Bb:maj F:maj C:maj", roots("mixolydian"))
        assertEquals("C:maj F:maj B:dim E:m A:m D:m G:maj C:maj", roots("circle-major"))
        assertEquals("A:m D:m G:maj C:maj F:maj B:dim E:maj A:m", roots("circle-minor", "A"))
        assertEquals("D:maj A:maj B:m F#:m G:maj D:maj G:maj A:maj", roots("pachelbel", "D"))
        assertEquals("Ab:7", P.page(P.byId("minor-blues"), C).chords[8].let { it.root.toString() + ":" + it.step.chord }, "bar 9 of the minor blues")
        assertEquals("Db7", P.page(P.byId("tritone-sub"), C).chords[1].symbol)
    }

    @Test
    fun `a key that would need double accidentals is respelled`() {
        val p = P.page(P.byId("tritone-sub"), PitchName.parse("Fb"))
        assertEquals(PitchName.parse("Fb"), p.respelledFrom); assertEquals("E", p.key.toString())
        assertEquals("F#:m7 F:7 E:maj7", p.chords.joinToString(" ") { it.root.toString() + ":" + it.step.chord })
        for (p2 in P.all) for (k in Reference.roots) for (c in P.page(p2, k).chords) assertTrue(kotlin.math.abs(c.root.alter) < 2, "${p2.id} in $k")
    }

    @Test
    fun `the sound has the root in the bass and only chord tones above`() {
        for (p in P.all) for (c in P.page(p, C).chords) {
            assertEquals(c.root.pitchClass, Math.floorMod(c.midis.first(), 12), "${p.id} ${c.symbol}")
            val tones = c.step.def.degrees.map { Math.floorMod(c.root.pitchClass + it.interval.semitones, 12) }.toSet()
            assertTrue(c.midis.all { Math.floorMod(it, 12) in tones }); assertTrue(c.midis.size <= 5)
            assertNotNull(ChordDisplay.of(c.symbol, ChordStyle.CLASSICAL, P.page(p, C).keyMode), "${p.id} ${c.symbol} has a numeral")
        }
        val blues = P.page(P.byId("twelve-bar"), C)
        assertEquals(12, P.bars(blues).size); assertEquals(12 * 4 * 0.6, P.sound(blues).maxOf { it.start } + 4 * 0.6, 1e-9)
        assertEquals(4, P.bars(P.page(P.byId("coltrane"), C)).size)
    }

    @Test
    fun `in yours finds the progression in any key, sevenths or not`() {
        val chart = YourSource(1, "Tide", "chord_chart", body = "Em7 / / / | A7 / / / | Dmaj7 / / / | Dmaj7 / / / | G / / / | D / A / | Bm / G / ")
        assertEquals(listOf("bar 1"), P.inYours(P.byId("ii-v-i"), listOf(chart)).map { it.where }, "ii–V–I in D")
        assertEquals("E", P.inYours(P.byId("ii-v-i"), listOf(chart)).single().root.toString())
        assertEquals(listOf("bar 6"), P.inYours(P.byId("axis"), listOf(chart)).map { it.where }, "D A Bm G is I–V–vi–IV")
        val cliche = YourSource(2, "Ballad", "chord_chart", body = "Am / / / | AmMaj7 / / / | Am7 / / / | Am6 / / /")
        assertEquals(1, P.inYours(P.byId("line-cliche"), listOf(cliche)).size)
        assertEquals(0, P.inYours(P.byId("line-cliche"), listOf(chart)).size, "matched on exact chords, not on any minor chord")
    }
}
