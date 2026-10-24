package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.catalog.Chords
import com.betterappmafia.apophenia.core.catalog.Scales
import com.betterappmafia.apophenia.core.interval.Interval
import com.betterappmafia.apophenia.core.names
import com.betterappmafia.apophenia.core.pitch.PitchName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InYoursTest {
    private fun p(s: String) = PitchName.parse(s)

    private val chart = YourSource(1, "Low Tide", "chord_chart", body = "{time: 4/4}\nDm7 / G7 / | Cmaj7 / / / | E7#9 / / / | Am / / /")
    private val lyric = YourSource(2, "Chorus words", "lyric", body = "[Dm7]show me what you [G7]took\n[E7#9]all of it")
    private val own = YourSource(1, "Low Tide", "chord_chart", body = "A7#9 / / /", project = "Album")
    private val recording = YourSource(3, "Hum", "recording", key = "D dorian")

    @Test
    fun `a chord page finds its quality on every root in charts, lyrics and project copies, saying where`() {
        val hits = InYours.chord(Chords.byId("7#9"), listOf(chart, lyric, own))
        assertEquals(listOf("bar 3", "line 2", "Album · bar 1"), hits.map { it.where })
        assertEquals(listOf(p("E"), p("E"), p("A")), hits.map { it.root })
        assertEquals(listOf("bar 1", "line 1"), InYours.chord(Chords.byId("m7"), listOf(chart, lyric)).map { it.where })
    }

    @Test
    fun `a scale page finds scraps in that key`() {
        val hits = InYours.scale(Scales.byId("dorian"), listOf(recording, chart))
        assertEquals(1, hits.size)
        assertEquals(p("D"), hits.first().root)
        assertTrue(InYours.scale(Scales.byId("major"), listOf(recording)).isEmpty())
    }

    @Test
    fun `an interval page finds melodic steps up and down in a score`() {
        val score = com.betterappmafia.apophenia.core.score.ScoreText.encode(
            com.betterappmafia.apophenia.core.score.Score(parts = listOf(com.betterappmafia.apophenia.core.score.Part("Voice", com.betterappmafia.apophenia.core.score.Clef.TREBLE, bars = listOf(
                com.betterappmafia.apophenia.core.score.Bar(listOf(listOf("A4", "C5", "A4", "E5").map { n ->
                    com.betterappmafia.apophenia.core.score.Event(com.betterappmafia.apophenia.core.rhythm.Duration.QUARTER, listOf(com.betterappmafia.apophenia.core.score.Note(com.betterappmafia.apophenia.core.pitch.SpelledPitch.parse(n))))
                })),
            )))),
        )
        val src = YourSource(4, "Tune", "sheet", body = score)
        val m3 = InYours.interval(Interval.parse("m3"), listOf(src))
        assertEquals(listOf("A4 → C5 up", "C5 → A4 down"), m3.map { it.text })
        assertEquals(1, InYours.interval(Interval.parse("P5"), listOf(src)).size)
    }

    @Test
    fun `naming notes finds the exact chord first, then forms with a left-out tone, and slash chords over another bass`() {
        assertEquals("C", ChordNamer.name(names("C E G"), p("C")).first().symbol)
        assertEquals("Am7", ChordNamer.name(names("A C E G"), p("A")).first().symbol)
        assertTrue(ChordNamer.name(names("A C E G"), p("A")).any { it.symbol == "C6/A" })
        val noFifth = ChordNamer.name(names("C E Bb"), p("C")).first()
        assertEquals("C7", noFifth.symbol)
        assertEquals(listOf("5"), noFifth.missing)
        assertEquals("E7#9", ChordNamer.name(names("E G# D Fx"), p("E")).first().symbol)
        assertEquals("C/E", ChordNamer.name(names("E G C"), p("E")).first().symbol)
    }
}
