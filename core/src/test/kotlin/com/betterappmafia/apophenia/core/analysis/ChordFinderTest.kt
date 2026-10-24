package com.betterappmafia.apophenia.core.analysis

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import com.betterappmafia.apophenia.core.sheet.ChordChart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

class ChordFinderTest {
    private val rate = 24000
    private val four = TimeSignature.parse("4/4")

    /** Each chord (MIDI notes) for [seconds], sine tones with a few harmonics, like a soft keyboard. */
    private fun play(vararg chords: List<Int>, seconds: Double = 1.0): FloatArray {
        val n = (seconds * rate).toInt()
        val x = FloatArray(n * chords.size)
        chords.forEachIndexed { c, notes ->
            for (m in notes) {
                val hz = 440.0 * 2.0.pow((m - 69) / 12.0)
                for (i in 0 until n) {
                    val t = i.toDouble() / rate
                    x[c * n + i] += (0.15 * (sin(2 * PI * hz * t) + 0.4 * sin(4 * PI * hz * t) + 0.2 * sin(6 * PI * hz * t))).toFloat()
                }
            }
        }
        return x
    }

    private val beats = (0 until 8).map { it * 0.5 }

    @Test fun `a I-IV-V-I with its roots in the bass reads as C F G C, two beats each`() {
        val x = play(listOf(36, 60, 64, 67), listOf(41, 60, 65, 69), listOf(43, 62, 67, 71), listOf(36, 60, 64, 67))
        val t = ChordFinder.find(x, rate, beats, four, KeyMode.parseOrNull("C major"))
        assertEquals(listOf("C", "C", "F", "F", "G", "G", "C", "C"), t.beats.map { it.label })
        assertEquals("{time: 4/4}\n| C / F / | G / C / |\n", t.chart())
        assertTrue(ChordChart.parse(t.chart()).problems.isEmpty())
    }

    @Test fun `a chord over its third in the bass is an inversion, a power chord is a 5, minor and seventh are named`() {
        val x = play(listOf(40, 60, 64, 67), listOf(40, 47, 52, 59), listOf(45, 57, 60, 64), listOf(43, 59, 62, 65, 67))
        val labels = ChordFinder.find(x, rate, beats, four, KeyMode.parseOrNull("C major")).beats.map { it.label }.filterIndexed { i, _ -> i % 2 == 0 }
        assertEquals(listOf("C/E", "E5", "Am", "G7"), labels)
    }

    @Test fun `roots are spelled in the key and silence is no chord`() {
        val x = play(listOf(46, 58, 62, 65), listOf(), listOf(42, 54, 57, 61))
        val inF = ChordFinder.find(x.copyOf(rate), rate, listOf(0.0, 0.5), four, KeyMode.parseOrNull("F major")).beats.map { it.label }
        assertEquals(listOf("Bb", "Bb"), inF)
        val all = ChordFinder.find(x, rate, (0 until 6).map { it * 0.5 }, four, KeyMode.parseOrNull("D major")).beats.map { it.label }
        assertEquals("N.C.", all[2])
        assertEquals("F#m", all[4])
    }

    @Test fun `with no beat the chart is one bar per chord, in order`() {
        val x = play(listOf(36, 60, 64, 67), listOf(41, 60, 65, 69), seconds = 1.5)
        val t = ChordFinder.find(x, rate, null, four, null)
        assertTrue(t.free)
        assertEquals("{time: 4/4}\n| C / / / | F / / / |\n", t.chart())
    }
}
