package com.betterappmafia.apophenia.core.rhythm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RhythmTest {

    private fun r(n: Int, d: Int = 1) = Rational.of(n, d)

    @Test
    fun `rationals stay exact and reduced`() {
        assertEquals(r(1, 2), r(2, 4))
        assertEquals(r(-1, 3), Rational.of(1, -3))
        assertEquals(r(5, 6), r(1, 2) + r(1, 3))
        assertEquals(r(1, 6), r(1, 2) - r(1, 3))
        assertEquals(r(1, 1), r(1, 3) * 3)
        assertEquals(r(3, 2), r(1, 2) / r(1, 3))
        assertTrue(r(1, 3) < r(1, 2))
        assertFailsWith<IllegalArgumentException> { Rational.of(1, 0) }
    }

    @Test
    fun `dots add half, then a quarter, then an eighth of the value`() {
        assertEquals(r(3, 8), Duration.QUARTER.dotted(1).length)
        assertEquals(r(7, 16), Duration.QUARTER.dotted(2).length)
        assertEquals(r(15, 32), Duration.QUARTER.dotted(3).length)
        assertFailsWith<IllegalArgumentException> { Duration.QUARTER.dotted(4) }
    }

    @Test
    fun `common durations land on whole ticks at 960 per quarter`() {
        assertEquals(960, Duration.QUARTER.ticks)
        assertEquals(1440, Duration.QUARTER.dotted().ticks)
        assertEquals(320, Duration.EIGHTH.inTuplet(3, 2).ticks)
        assertEquals(192, Duration.SIXTEENTH.inTuplet(5, 4).ticks)
        assertEquals(160, Duration.SIXTEENTH.inTuplet(6, 4).ticks)
        assertEquals(720, Duration.EIGHTH.inTuplet(2, 3).ticks)
    }

    @Test
    fun `durations that miss the tick grid keep their exact length`() {
        val septuplet = Duration.SIXTEENTH.inTuplet(7, 4)
        assertEquals(r(1, 28), septuplet.length)
        assertNull(septuplet.ticksOrNull)
        assertFailsWith<IllegalStateException> { septuplet.ticks }
        // Seven of them still fill exactly one quarter.
        assertEquals(r(1, 4), septuplet.length * 7)
    }

    @Test
    fun `a triplet nested inside a triplet is 4 over 9 of the written value`() {
        val inner = Duration.EIGHTH.inTuplet(3, 2).inTuplet(3, 2)
        assertEquals(r(1, 18), inner.length)
        assertEquals(Tuplet(9, 4), inner.timeModification)
        val outerQuarter = Duration.QUARTER.inTuplet(3, 2)
        assertEquals(640, outerQuarter.ticks)
        // Three inner eighths fill one outer triplet quarter, and the outer triplet fills a half note.
        assertEquals(outerQuarter.length, inner.length * 3)
        assertEquals(r(1, 2), outerQuarter.length * 3)
    }

    @Test
    fun `six eight has two beats and nine eight has three`() {
        assertEquals(2, TimeSignature.of(6, 8).beatsPerBar)
        assertEquals(3, TimeSignature.of(9, 8).beatsPerBar)
        assertEquals(4, TimeSignature.of(12, 8).beatsPerBar)
        assertEquals(1, TimeSignature.of(3, 8).beatsPerBar)
        assertEquals(listOf(r(3, 8), r(3, 8)), TimeSignature.of(6, 8).beatLengths)
        assertTrue(TimeSignature.of(6, 8).isCompound)
    }

    @Test
    fun `simple meters count one beat per denominator note`() {
        assertEquals(4, TimeSignature.of(4, 4).beatsPerBar)
        assertEquals(3, TimeSignature.of(3, 4).beatsPerBar)
        assertEquals(2, TimeSignature.of(2, 2).beatsPerBar)
        assertEquals(3840, TimeSignature.of(4, 4).barTicks)
    }

    @Test
    fun `seven eight as 2 plus 2 plus 3 has three uneven beats`() {
        val ts = TimeSignature.additive(2, 2, 3, denominator = 8)
        assertEquals(3, ts.beatsPerBar)
        assertEquals(7, ts.numerator)
        assertEquals(listOf(r(0), r(1, 4), r(1, 2)), ts.beatStarts)
        assertEquals(listOf(r(1, 4), r(1, 4), r(3, 8)), ts.beatLengths)
        assertEquals("2+2+3/8", ts.toString())
        assertEquals(ts, TimeSignature.parse("2+2+3/8"))
        assertTrue(ts != TimeSignature.parse("3+2+2/8"))
    }

    @Test
    fun `irregular meters must be given a grouping`() {
        assertFailsWith<IllegalArgumentException> { TimeSignature.of(7, 8) }
        assertFailsWith<IllegalArgumentException> { TimeSignature.of(5, 8) }
        assertEquals(5, TimeSignature.of(5, 4).beatsPerBar)
    }

    @Test
    fun `signatures round trip through their MusicXML text`() {
        for (s in listOf("4/4", "3/4", "6/8", "12/8", "2+2+3/8", "3+3+2/8", "5/4", "2/2")) {
            assertEquals(s, TimeSignature.parse(s).toString())
        }
    }

    @Test
    fun `six eight at dotted quarter 60 is the same speed as quarter 90`() {
        val dottedQuarter = Tempo(60.0, Duration.QUARTER.dotted())
        assertEquals(90.0, dottedQuarter.quarterNotesPerMinute, 1e-9)
        assertEquals(Tempo(90.0).secondsFor(r(3, 4)), dottedQuarter.secondsFor(r(3, 4)), 1e-12)
        assertEquals(60.0, Tempo(90.0).inUnit(Duration.QUARTER.dotted()).bpm, 1e-9)
    }

    @Test
    fun `a 4 4 bar at 120 lasts two seconds`() {
        assertEquals(2.0, Tempo(120.0).secondsFor(TimeSignature.of(4, 4).barLength), 1e-12)
        assertEquals(2.0, Tempo(120.0).secondsForTicks(3840), 1e-12)
    }

    @Test
    fun `swing moves the off-beat eighth and leaves the beats alone`() {
        val off = r(1, 8)
        assertEquals(0.125, Swing.STRAIGHT.sounding(off), 1e-12)
        assertEquals(1.0 / 6.0, Swing.TRIPLET.sounding(off), 1e-12)
        for (beat in 0..4) assertEquals(beat / 4.0, Swing.TRIPLET.sounding(r(beat, 4)), 1e-12)
        assertFailsWith<IllegalArgumentException> { Swing(0.9) }
    }
}
