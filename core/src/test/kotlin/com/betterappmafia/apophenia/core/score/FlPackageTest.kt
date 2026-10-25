package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.rhythm.Duration
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlPackageTest {
    private val chart = "{time: 4/4}\n{c: Verse}\n| Dm7 / G7 / | Cmaj7 / / / |\n{c: Chorus}\n| N.C. / F / |\n{time: 3/4}\n| Bb / / |"

    @Test
    fun `a chord chart becomes a chord track with its symbols, sections and meter change, and plays as MIDI`() {
        val s = FlPackage.chartScore("Low Tide chords", chart, KeyMode(PitchName.parse("F"), "major"), 96.0)
        val bars = s.parts.single().bars
        assertEquals(4, bars.size)
        assertEquals(listOf("Verse", null, "Chorus", null), bars.map { it.rehearsal })
        assertEquals(TimeSignature.of(3, 4), bars[3].meter)
        val v1 = bars[0].voices[0]
        assertEquals(listOf("Dm7", "G7"), v1.mapNotNull { it.chord })
        assertEquals(listOf(Duration.HALF, Duration.HALF), v1.map { it.duration })
        assertEquals(listOf("D2", "D4", "F4", "A4", "C5"), v1[0].notes.map { it.pitch.toString() }, "bass D2, then D F A C from the root between G3 and F#4")
        assertEquals(Duration.WHOLE, bars[1].voices[0].single().duration, "Cmaj7 holds the bar")
        assertTrue(bars[2].voices[0][0].isRest, "N.C. is silence")
        assertEquals(listOf("Bb2"), bars[3].voices[0][0].notes.take(1).map { it.pitch.toString() }, "spelled in F major: Bb, not A#")
        // through MIDI and back, the same notes
        val back = MidiRead.read(ScoreMidi.write(s)).score
        assertEquals(PianoRoll.notes(s, s.parts[0]).map { Triple(it.start, it.length, it.pitch.midi) }.sortedWith(compareBy({ it.first }, { it.third })),
            PianoRoll.notes(back, back.parts[0]).map { Triple(it.start, it.length, it.pitch.midi) }.sortedWith(compareBy({ it.first }, { it.third })))
    }

    @Test
    fun `the manifest says where every layer sits, in seconds and bars, and what each file is`() {
        val text = FlPackage.manifest(FlPackage.Info(
            project = "Low Tide", stack = "Stack 1", made = "2026-10-24 18:40", bpm = 120.0, key = KeyMode(PitchName.parse("D"), "dorian"), rate = 48_000,
            layers = listOf(FlPackage.Layer(1, "Bass riff", "B", "loop", start = 96_000, from = 48_000, to = 240_000, gainDb = -3.0, muted = false,
                conform = "+2 st", stem = "Stems/01 Bass riff.wav", original = "Takes/Bass riff - B.wav")),
            mix = "Stems/Mix.wav", midi = listOf("MIDI/Verse tab.mid" to "1 track"), otherTakes = listOf("Takes/Hum - A.wav"),
        ))
        assertTrue("Tempo: 120 BPM · Key: D Dorian" in text, text)
        assertTrue("starts 0:02.000 (bar 2 beat 1)" in text, text)
        assertTrue("plays the take from 0:01.000 to 0:05.000 · gain -3.0 dB" in text, text)
        assertTrue("Set the FL Studio project tempo to 120 BPM." in text)
        assertTrue("Takes/Hum - A.wav" in text)
    }

    @Test
    fun `clock, bar and beat, and file names`() {
        assertEquals("1:01.500", FlPackage.clock(2_952_000, 48_000))
        assertEquals("bar 1 beat 2.50", FlPackage.barBeat(36_000, 48_000, 120.0), "0.75 s at 120 is a beat and a half")
        assertEquals("bar 3 beat 1", FlPackage.barBeat(192_000, 48_000, 120.0))
        assertEquals("AC DC Back in Black", FlPackage.fileName("AC/DC: Back in Black?"))
        assertEquals("Untitled", FlPackage.fileName("  ..  "))
    }
}
