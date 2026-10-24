package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.instrument.Tunings
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CourseInstrumentsTest {
    private val I = CourseInstruments
    private fun pcs(midis: List<Int>, root: Int) = midis.map { Math.floorMod(it - root, 12) }.toSet()

    @Test
    fun `the keyboard and the guitar neck are what the pages say`() {
        assertEquals(88, I.KEYS.count()); assertEquals(52, I.whiteKeys())
        assertEquals("A0", I.spell(I.KEYS.first).toString()); assertEquals("C8", I.spell(I.KEYS.last).toString())
        assertEquals(listOf(2, 2, 3, 3), (6 downTo 3).map { I.octaveShape(I.GUITAR, it) }, "two frets from strings 6 and 5, three from 4 and 3")
        assertEquals(listOf(5 to 15, 4 to 10, 3 to 5, 2 to 1), I.places(I.GUITAR, 60), "middle C on four strings below the 15th fret")
        assertEquals(listOf(48, 60, 72), listOf(5 to 3, 3 to 5, 1 to 8).map { (s, f) -> I.GUITAR.midiAt(s, f) }, "C3 C4 C5")
        assertEquals(Tunings.GUITAR_STANDARD.strings.take(4).map { it.midi - 12 }, Tunings.BASS_STANDARD.strings.map { it.midi }, "bass is the guitar's low four an octave down")
        val line = I.bassLine.parts.first().bars.flatMap { b -> b.voices.first().flatMap { it.notes } }
        assertEquals(listOf("A1", "E2", "A2", "E2", "D2", "A2", "D3", "A2"), line.map { it.pitch.toString() })
        for (n in line) assertEquals(n.pitch.midi, I.BASS.midiAt(n.string!!, n.fret!!))
    }

    @Test
    fun `alternate tunings change the strings the page names, open G and open D are chords`() {
        val byId = I.GUITAR_TUNINGS.associate { it.id to I.changes(it) }
        assertEquals(listOf(6 to -2), byId.getValue("guitar-drop-d"))
        assertEquals((6 downTo 1).map { it to -2 }, byId.getValue("guitar-d-standard"))
        assertEquals(listOf(6 to -2, 2 to -2, 1 to -2), byId.getValue("guitar-dadgad"))
        assertEquals(setOf(0, 4, 7), pcs(Tunings.GUITAR_OPEN_G.strings.map { it.midi }, 7), "open G rings G major")
        assertEquals(setOf(0, 4, 7), pcs(Tunings.GUITAR_OPEN_D.strings.map { it.midi }, 2), "open D rings D major")
        assertEquals("B♭", I.capoSounds(7, 3)); assertEquals("A", I.capoSounds(7, 2))
        val check = Course.page("tunings-alt")!!.check!!
        for ((o, t) in check.options.zip(I.GUITAR_TUNINGS)) assertEquals(t.strings.map { it.midi }, o.make(50, Random(1)).piano.map { it.midi }, o.label)
    }

    @Test
    fun `the five CAGED shapes are all C major and climb the neck in order`() {
        assertEquals("CAGED", I.CAGED.joinToString("") { it.first })
        for ((name, f) in I.CAGED) {
            val ms = I.shapeNotes(f).map { (s, fr) -> I.GUITAR.midiAt(s, fr) }
            assertEquals(setOf(0, 4, 7), pcs(ms, 0), "$name shape is C E G")
            assertEquals(0, Math.floorMod(ms.first(), 12), "$name shape has C in the bass")
        }
        val lowest = I.CAGED.map { (_, f) -> f.filterNotNull().min() }
        assertEquals(lowest.sorted(), lowest)
        assertEquals("G", I.MAJOR_NAMES[Math.floorMod(I.GUITAR.midiAt(6, 3), 12)]); assertEquals("C", I.MAJOR_NAMES[Math.floorMod(I.GUITAR.midiAt(5, 3), 12)])
    }

    @Test
    fun `scale positions join end to end and stay in the scale`() {
        assertEquals(listOf(5, 8, 5, 7, 5, 7, 5, 7, 5, 8, 5, 8), I.PENT[0].map { it.second }, "box 1 in A")
        for (p in I.PENT + I.THREE_NPS) for ((s, f) in p) assertTrue(f in 0..22, "string $s fret $f")
        for (p in I.PENT) {
            assertTrue(p.all { (s, f) -> Math.floorMod(I.GUITAR.midiAt(s, f), 12) in I.A_MINOR_PENT })
            assertTrue(p.maxOf { it.second } - p.minOf { it.second } <= 4, "a box spans at most five frets")
        }
        for (i in 0 until 4) assertEquals(I.PENT[i][1].second, I.PENT[i + 1][0].second, "box ${i + 2} starts where box ${i + 1}'s second note was")
        assertEquals(17, I.PENT[4].last().second, "the fifth box ends at the 17th")
        for (p in I.THREE_NPS) {
            assertEquals(18, p.size)
            assertTrue(p.all { (s, f) -> Math.floorMod(I.GUITAR.midiAt(s, f), 12) in I.G_MAJOR })
            assertTrue(p.groupBy { it.first }.values.all { it.size == 3 && it.last().second - it.first().second <= 4 })
            assertTrue(p.maxOf { it.second } - p.minOf { it.second } <= I.threeSpan())
        }
        assertEquals(6, I.threeSpan(), "the position from C runs from the 8th fret to the 14th")
        assertEquals("C5", I.spell(I.THREE_NPS[0].last().let { (s, f) -> I.GUITAR.midiAt(s, f) }).toString(), "the run ends on C on the 1st string")
    }

    @Test
    fun `drop 2 and drop 3 of Cmaj7 lie on the strings the page says`() {
        assertEquals(listOf(55, 60, 64, 71), I.drop(listOf(60, 64, 67, 71), 2), "G C E B")
        assertEquals(listOf(48, 59, 64, 67), I.drop(listOf(59, 60, 64, 67), 3), "C B E G")
        assertEquals(listOf(5, 5, 5, 7), I.frets(I.D2_STRINGS, I.drop(I.CLOSE[0], 2)))
        assertEquals(listOf(8, 9, 9, 8), I.frets(I.D3_STRINGS, I.drop(I.CLOSE[3], 3)))
        for (c in I.CLOSE) for (k in listOf(2, 3)) {
            val d = I.drop(c, k)
            assertEquals(pcs(c, 0), pcs(d, 0)); assertEquals(setOf(0, 4, 7, 11), pcs(d, 0))
            val strings = if (k == 2) I.D2_STRINGS else I.D3_STRINGS
            val f = I.frets(strings, d)
            assertEquals(pcs(d, 0), pcs(strings.zip(f) { s, fr -> I.GUITAR.midiAt(s, fr) }, 0), "frets play the voicing")
            assertTrue(f.max() - f.min() <= 4, "within reach: $f")
        }
        val check = Course.page("drop")!!.check!!
        assertEquals(listOf(listOf(60, 64, 67, 71), listOf(55, 60, 64, 71), listOf(52, 60, 67, 71)), check.options.map { o -> o.make(60, Random(1)).piano.map { it.midi } })
    }

    @Test
    fun `the piano voicings have the tones the pages name`() {
        val dm = 2; val g = 7; val c = 0
        assertEquals(listOf(setOf(0, 10), setOf(0, 4), setOf(0, 11)), I.SHELLS.zip(I.II_V_I) { v, r -> pcs(I.midis(v), r) })
        assertEquals(setOf(3, 7, 10, 2), pcs(I.midis(I.ROOTLESS[0]), dm), "Dm9 without D: b3 5 b7 9")
        assertEquals(setOf(10, 2, 4, 9), pcs(I.midis(I.ROOTLESS[1]), g), "G13 without G: b7 9 3 13")
        assertEquals(setOf(4, 7, 11, 2), pcs(I.midis(I.ROOTLESS[2]), c), "Cmaj9 without C: 3 5 7 9")
        assertEquals(listOf(setOf(0, 10, 3, 7, 2), setOf(0, 10, 4, 2, 9), setOf(0, 11, 4, 7, 2)), I.SPREAD.zip(I.II_V_I) { v, r -> pcs(I.midis(v), r) })
        for (v in I.SPREAD) assertEquals(v.first(), v.minBy { com.betterappmafia.apophenia.core.pitch.SpelledPitch.parse(it).midi }, "root at the bottom")
        val moves = I.ROOTLESS.zipWithNext { a, b -> I.midis(a).zip(I.midis(b)) { x, y -> kotlin.math.abs(x - y) } }
        assertTrue(moves.flatten().all { it <= 2 }, "every rootless voice moves by step or stays")
        val check = Course.page("shells")!!.check!!
        assertEquals(listOf(listOf(50, 54), listOf(50, 60)), check.options.map { o -> o.make(50, Random(1)).piano.map { it.midi } })
    }

    @Test
    fun `the singer's keys are the shifts that fit`() {
        val m = { s: String -> com.betterappmafia.apophenia.core.pitch.SpelledPitch.parse(s).midi }
        assertEquals(listOf(-7, -6, -5), I.fits(m(I.SONG.first), m(I.SONG.second), m(I.SINGER.first), m(I.SINGER.second)))
        assertEquals(5, m(I.SONG.second) - m(I.SINGER.second), "as written the top is five semitones too high")
        for ((s, iv) in I.SHIFTS) assertEquals(s, -iv.semitones)
        for ((n, _, r) in I.SATB) assertTrue(m(r.first) < m(r.second), n)
    }
}
