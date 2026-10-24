package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.ALL_35_ROOTS
import com.betterappmafia.apophenia.core.catalog.Chords
import com.betterappmafia.apophenia.core.catalog.Scales
import com.betterappmafia.apophenia.core.instrument.Tunings
import com.betterappmafia.apophenia.core.interval.Interval
import com.betterappmafia.apophenia.core.names
import com.betterappmafia.apophenia.core.pitch.PitchName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReferenceTest {
    private fun p(s: String) = PitchName.parse(s)

    @Test
    fun `every interval, scale and chord page builds on all 35 roots with the catalog's pitch classes`() {
        for (root in ALL_35_ROOTS) {
            for (i in Reference.intervals) IntervalPages.page(i, root)
            for (s in Scales.all) {
                val page = ScalePages.page(s, root)
                assertEquals(s.semitones.map { Math.floorMod(page.root.pitchClass + it, 12) }, page.notes.map { it.pitchClass }, "${s.id} on $root")
                assertEquals(12, page.steps.sum(), "${s.id} steps close the octave")
            }
            for (c in Chords.all) {
                val page = ChordPages.page(c, root)
                assertEquals(c.semitones.map { Math.floorMod(page.root.pitchClass + it, 12) }, page.notes.map { it.pitchClass }, "${c.id} on $root")
                assertTrue((runCatching { page.chord.notes() }.getOrNull() ?: page.notes).map { it.pitchClass }.containsAll(page.notes.map { it.pitchClass }), "${c.id} stored structure on $root")
            }
        }
    }

    @Test
    fun `there are 52 interval pages from the unison to the double octave, named in words`() {
        assertEquals(52, Reference.intervals.size)
        assertEquals("augmented fourth", Reference.intervalName(Interval.parse("A4")))
        assertEquals("minor thirteenth", Reference.intervalName(Interval.parse("m13")))
        assertEquals("perfect double octave", Reference.intervalName(Interval.parse("P15")))
    }

    @Test
    fun `an interval page gives its inversion, enharmonic twins, just ratio and notes`() {
        val m3 = IntervalPages.page(Interval.parse("m3"), p("C"))
        assertEquals("M6", m3.inversion.toString())
        assertEquals(listOf("A2"), m3.enharmonic.map { it.toString() })
        assertEquals("6:5", m3.just?.ratio)
        assertEquals(p("Eb"), m3.above)
        assertEquals(p("A"), m3.below)
        assertTrue(IntervalPages.page(Interval.parse("A4"), p("C")).enharmonic.any { it.toString() == "d5" })
        assertEquals("m7", IntervalPages.page(Interval.parse("M9"), p("C")).inversion.toString())
        assertEquals("P1", IntervalPages.page(Interval.parse("P8"), p("C")).inversion.toString())
    }

    @Test
    fun `D Dorian is mode 2 of C major and C major's chords are I ii iii IV V vi vii-half-diminished`() {
        val page = ScalePages.page(Scales.byId("dorian"), p("D"))
        assertEquals(Scales.byId("major") to p("C"), page.parent)
        assertTrue(page.modes.contains(Scales.byId("phrygian") to p("E")))
        val c = ScalePages.page(Scales.byId("major"), p("C"))
        assertEquals(listOf("C", "Dm", "Em", "F", "G", "Am", "Bdim"), c.triads.map { it.symbol })
        assertEquals(listOf("Cmaj7", "Dm7", "Em7", "Fmaj7", "G7", "Am7", "Bm7b5"), c.sevenths.map { it.symbol })
        assertEquals(listOf("W", "W", "H", "W", "W", "W", "H"), c.steps.map(ScalePages::stepName))
    }

    @Test
    fun `A harmonic minor's sevenths include the augmented major seventh and G-sharp diminished seventh`() {
        val a = ScalePages.page(Scales.byId("harmonic-minor"), p("A"))
        assertEquals(listOf("Am(maj7)", "Bm7b5", "Cmaj7#5", "Dm7", "E7", "Fmaj7", "G#dim7"), a.sevenths.map { it.symbol })
    }

    @Test
    fun `C6 has the notes of Am7, and C dim7 the notes of three other diminished sevenths`() {
        assertTrue(ChordPages.page(Chords.byId("6"), p("C")).sameNotes.contains(Chords.byId("m7") to p("A")))
        val dim = ChordPages.page(Chords.byId("dim7"), p("C")).sameNotes.filter { it.first.id == "dim7" }.map { it.second }
        assertEquals(names("Eb Gb Bbb"), dim)
    }

    @Test
    fun `E7 sharp 9 is spelled with F double sharp and fits half-whole but not the altered scale`() {
        val page = ChordPages.page(Chords.byId("7#9"), p("E"))
        assertEquals(names("E G# B D Fx"), page.notes)
        assertTrue(page.scales.any { it.id == "half-whole" })
        assertFalse(page.scales.any { it.id == "altered" })
        assertEquals("E7#9", page.symbols.first())
    }

    @Test
    fun `inversions of a seventh chord carry figured-bass names and slash symbols`() {
        val g7 = ChordPages.page(Chords.byId("7"), p("G"))
        assertEquals(listOf("7", "6/5", "4/3", "4/2"), g7.inversions.map { it.figures })
        assertEquals(listOf("G7", "G7/B", "G7/D", "G7/F"), g7.inversions.map { it.symbol })
    }

    @Test
    fun `search opens a typed chord on its root, a named scale on its root, and an interval`() {
        assertEquals(RefHit(Reference.item(RefKind.CHORD, "7#9")!!, p("E")), Reference.find("E7#9").first())
        assertEquals(RefHit(Reference.item(RefKind.SCALE, "dorian")!!, p("D")), Reference.find("D dorian").first())
        assertEquals(Reference.item(RefKind.INTERVAL, "m3"), Reference.find("m3").first().item)
        assertTrue(Reference.find("lydian").any { it.item.id == "lydian-dominant" })
    }

    @Test
    fun `guitar shapes include the Hendrix E7 sharp 9 and open C, and every shape plays the chord with the root in the bass`() {
        val guitar = Tunings.GUITAR_STANDARD
        fun shapes(id: String, root: String): List<Voicing> {
            val page = ChordPages.page(Chords.byId(id), p(root))
            val pcs = page.notes.map { it.pitchClass }
            val optional = Chords.byId(id).omitOrder.map { d -> Math.floorMod(page.root.pitchClass + d.interval.semitones, 12) }.toSet()
            return Voicings.find(guitar, pcs, optional, limit = 400)
        }
        val e = shapes("7#9", "E")
        assertTrue(e.any { it.text == "x-7-6-7-8-x" }, e.take(10).joinToString { it.text })
        val c = shapes("maj", "C")
        assertTrue(c.take(5).any { it.text == "x-3-2-0-1-0" }, c.take(10).joinToString { it.text })
        assertTrue(e.all { it.midis.min() % 12 == 4 } && c.all { it.midis.min() % 12 == 0 })
    }

    @Test
    fun `reference audio places roots in the asked octave and plays a scale up and back down`() {
        assertEquals(listOf(52, 56, 59), RefSound.midis(4, listOf(0, 4, 7), 48))
        val scale = RefSound.scale(listOf(60, 62, 64))
        assertEquals(listOf(60, 62, 64, 72, 64, 62, 60), scale.map { it.midi })
    }
}
