package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.analysis.BasicPitch
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.chord.ChordDialect
import com.betterappmafia.apophenia.core.pitch.PitchName
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YourEarTest {
    private val chart = YourSource(1, "Low Tide", "chord_chart", body = "{time: 4/4}\nDm7 / G7 / | Cmaj7 / / / | E7#9 / / / | Am/C / / /")
    private val clip = YourClip("a.wav", 0.0, 12.0)

    @Test
    fun `your chords are the catalog chords in your material, slash chords left out, each saying where`() {
        val c = YourEar.chords(listOf(chart), ChordDialect())
        assertEquals(listOf("m7", "7", "maj7", "7#9"), c.map { it.quality })
        assertEquals(PitchName.parse("E"), c[3].root)
        assertEquals("bar 3", c[3].where)
    }

    @Test
    fun `phrases are single lines of four to six notes with short gaps`() {
        fun n(s: Double, e: Double, m: Int) = BasicPitch.TimedNote(s, e, m, 0.5, null)
        val notes = listOf(n(0.0, 0.4, 60), n(0.45, 0.8, 62), n(0.85, 1.2, 64), n(1.25, 1.6, 65), n(1.65, 2.0, 67),
            n(4.0, 4.3, 50), n(4.35, 4.6, 52),                    // too short a run after the gap
            n(6.0, 7.0, 40), n(6.1, 6.5, 47))                     // overlapping: not a single line
        val p = YourEar.phrases(notes)
        assertEquals(listOf(listOf(60, 62, 64, 65, 67)), p.map { r -> r.map { it.midi } })
        val c = YourEar.clipOf("t.wav", p.first())
        assertEquals(0.0, c.start)
        assertEquals(2.45, c.length, 1e-9)
    }

    @Test
    fun `the drills appear only when your material has enough for them`() {
        assertTrue(YourEar.drills(YourPool()).isEmpty())
        assertEquals(3, YourEar.missing(YourPool()).size)
        val pool = YourPool(
            chords = YourEar.chords(listOf(chart), ChordDialect()),
            keys = listOf(YourKey(KeyMode(PitchName.parse("D"), "dorian"), "Hum", clip), YourKey(KeyMode(PitchName.parse("E"), "aeolian"), "Riff", clip)),
            phrases = listOf(YourPhrase(listOf(60, 62, 64, 65), "Hum", "B", clip)),
        )
        val drills = YourEar.drills(pool)
        assertEquals(listOf("own-chords", "own-keys", "own-phrases"), drills.map { it.id })
        val r = Random(4)
        val chordQ = Ear.question(drills[0], EarSettings(), r)
        assertTrue(Ear.explain(chordQ) { it.toString() }.contains("From Low Tide · bar"))
        val keyQ = Ear.question(drills[1], EarSettings(), r)
        assertEquals(clip, Ear.sound(keyQ, EarSettings()).clip)
        val other = Ear.asAnswer(keyQ, drills[1].answers.first { it != keyQ.answer })
        assertTrue(Ear.sound(other, EarSettings()).piano.isNotEmpty(), "another key is heard as a cadence in that key")
        val phraseQ = Ear.question(drills[2], EarSettings(), r)
        assertTrue(Ear.check(phraseQ, listOf(60, 62, 64, 77)))
    }
}
