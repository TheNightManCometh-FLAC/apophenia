package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.learn.CourseReading
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.pitch.SpelledPitch
import com.betterappmafia.apophenia.core.rhythm.Duration
import com.betterappmafia.apophenia.core.rhythm.Tempo
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import com.betterappmafia.apophenia.core.sheet.Exports
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScoreMidiTest {
    /** A minimal SMF reader: per track, (tick, status, data) with metas as status 0xFF and data = type + payload. */
    private fun read(b: ByteArray): Pair<Int, List<List<Triple<Long, Int, List<Int>>>>> {
        var p = 0
        fun u8() = b[p++].toInt() and 0xFF
        fun u16() = (u8() shl 8) or u8()
        fun u32() = (u16() shl 16) or u16()
        fun vlq(): Long { var v = 0L; while (true) { val c = u8(); v = (v shl 7) or (c and 0x7F).toLong(); if (c and 0x80 == 0) return v } }
        assertEquals("MThd", String(b, 0, 4)); p = 4; assertEquals(6, u32())
        val format = u16(); val n = u16(); assertEquals(960, u16())
        val tracks = (0 until n).map {
            assertEquals("MTrk", String(b, p, 4)); p += 4
            val end = u32() + p
            var t = 0L
            val ev = ArrayList<Triple<Long, Int, List<Int>>>()
            while (p < end) {
                t += vlq()
                val s = u8()
                if (s == 0xFF) { val type = u8(); val len = vlq().toInt(); ev += Triple(t, s, listOf(type) + (0 until len).map { u8() }) }
                else { val len = if (s and 0xF0 == 0xC0) 1 else 2; ev += Triple(t, s, (0 until len).map { u8() }) }
            }
            ev
        }
        return format to tracks
    }

    private fun notes(track: List<Triple<Long, Int, List<Int>>>): List<Triple<Long, Long, Int>> {
        val open = HashMap<Int, Long>()
        val out = ArrayList<Triple<Long, Long, Int>>()
        for ((t, s, d) in track) when {
            s and 0xF0 == 0x90 && d[1] > 0 -> open[d[0]] = t
            s and 0xF0 == 0x80 -> out += Triple(open.remove(d[0])!!, t, d[0])
        }
        return out.sortedBy { it.first }
    }

    @Test
    fun `a score becomes a format 1 file with a conductor track, tied notes joined and repeats played out`() {
        val q = Duration.QUARTER
        val s = Score(title = "Tide", meter = TimeSignature.additive(2, 2, 3, denominator = 8), tempo = Tempo(90.0), key = KeyMode(PitchName.parse("D"), "dorian"),
            parts = listOf(Part("Lead", Clef.TREBLE, bars = listOf(
                Bar(listOf(listOf(Event(q, listOf(Note(SpelledPitch.parse("D4"), tie = true))), Event(q, listOf(Note(SpelledPitch.parse("D4")))), Event(Duration.EIGHTH.dotted(), listOf(Note(SpelledPitch.parse("F4"))))))),
            ))))
        val (format, tracks) = read(ScoreMidi.write(s))
        assertEquals(1, format); assertEquals(2, tracks.size)
        val tempo = tracks[0].first { it.second == 0xFF && it.third[0] == 0x51 }.third.drop(1)
        assertEquals(666_667, (tempo[0] shl 16) or (tempo[1] shl 8) or tempo[2], "90 BPM in microseconds a quarter")
        assertEquals(listOf(0x58, 7, 3, 12, 8), tracks[0].first { it.second == 0xFF && it.third[0] == 0x58 }.third, "7/8")
        val n = notes(tracks[1])
        assertEquals(listOf(Triple(0L, 1920L, 62), Triple(1920L, 2640L, 65)), n, "the tied D4 is one half note")
        assertEquals(listOf(0xC0, 0), tracks[1].first { it.second == 0xC0 }.let { listOf(it.second, it.third[0]) }, "piano")
    }

    @Test
    fun `the road map plays in order and a guitar part gets a guitar program`() {
        val (_, rm) = read(ScoreMidi.write(CourseReading.roadmap))
        assertEquals(CourseReading.playOrder().size, notes(rm[1]).size)
        assertEquals(listOf(60, 62, 64, 60, 62, 65, 67, 60, 62), notes(rm[1]).map { it.third }, "bars 1 2 3 1 2 4 5 1 2")
        val (_, tab) = read(ScoreMidi.write(CourseReading.tabScore))
        assertEquals(25, tab[1].first { it.second == 0xC0 }.third[0])
        assertEquals(13, notes(tab[1]).size)
        assertEquals(40, notes(tab[1]).first().third, "sounding E2")
    }

    @Test
    fun `ChordPro and text tab`() {
        val chart = Exports.chart("Low Tide", "{time: 4/4}\n{c: Verse}\nDm7 / G7 / | Cmaj7 / / / | %\n{c: Chorus}\nF / / / | E7#9 / / /", KeyMode(PitchName.parse("C"), "major"), 96.0)
        assertTrue(chart.startsWith("{title: Low Tide}\n{key: C}\n{tempo: 96}\n"))
        assertTrue("{start_of_grid: label=\"Verse\"}\n| Dm7 . G7 . | Cmaj7 . . . | Cmaj7 . . . |\n{end_of_grid}" in chart, chart)
        assertTrue("{start_of_grid: label=\"Chorus\"}\n| F . . . | E7#9 . . . |" in chart)
        assertEquals(1, Regex("\\{time:").findAll(chart).count())
        val lyric = Exports.lyrics("Tide", "[Dm7]show me what you [G7]took", KeyMode(PitchName.parse("A"), "aeolian"))
        assertEquals("{title: Tide}\n{key: Am}\n\n[Dm7]show me what you [G7]took\n", lyric)
        assertEquals("{title: Mine}\n[C]la\n", Exports.lyrics("Other", "{title: Mine}\n[C]la"), "a title already there is kept")
        val tab = Exports.tab(CourseReading.tabScore.copy(title = "Run"))
        val lines = tab.lines()
        assertTrue(lines.any { it.startsWith("E|") || it.startsWith("E |") }, tab)
        assertEquals(6, lines.count { it.contains("|-") && it.first().isLetter() }, "six string lines")
    }
}
