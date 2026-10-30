package com.betterappmafia.apophenia.core.analysis

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ChordTrackingTest {
    private fun resource(name: String) = File(javaClass.getResource("/chordtracking/$name")!!.toURI())

    @Test fun `rhythmic strumming survives speech detection but a spoken memo does not`() {
        val (x, rate) = SpeechTest.readWav(resource("guitar.wav"))
        val beats = resource("beats.txt").readLines().map { it.toDouble() }
        val found = BeatThis.Track(beats, emptyList())
        val before = TakeAnalysis.analyse(x, rate)
        assertEquals("fixture exhibits the old pitch-only mistake", "speech", before.key.from)
        val after = TakeAnalysis.analyse(x, rate, found = found)
        assertNotEquals("rhythmic guitar is music", "speech", after.key.from)
        assertNotNull("guitar keeps its detected tempo", after.tempo.best)
        assertTrue("guitar gets a chart", after.chords.beats.isNotEmpty())
        val (speech, sr) = SpeechTest.readWav(File(javaClass.getResource("/speech/memo.wav")!!.toURI()))
        val memo = TakeAnalysis.analyse(speech, sr, found = BeatThis.Track(emptyList(), emptyList()))
        assertEquals("speech", memo.key.from)
        assertNull(memo.tempo.best)
        assertTrue(memo.chords.beats.isEmpty())
    }

    @Test fun `heard notes contribute chord evidence without being rewritten`() {
        val (x, rate) = SpeechTest.readWav(resource("guitar.wav"))
        val beats = resource("beats.txt").readLines().map { it.toDouble() }
        val heard = resource("notes.tsv").readLines().map { row -> row.split('\t').let {
            BasicPitch.TimedNote(it[0].toDouble(), it[1].toDouble(), it[2].toInt(), it[3].toDouble(), null)
        } }
        val snapshot = heard.toList()
        val y = Resample.to(x, rate, TakeAnalysis.RATE)
        val four = com.betterappmafia.apophenia.core.rhythm.TimeSignature.of(4, 4)
        val sound = ChordFinder.find(y, TakeAnalysis.RATE, beats, four, null)
        val combined = ChordFinder.find(y, TakeAnalysis.RATE, beats, four, null, heard)
        // GuitarSet's instructed opening is Em; its performed-note annotation is Em7,
        // then A7 at 1.832 s. Harmonic evidence now retains that performed seventh too.
        assertEquals("sound alone misses the opening third", "E5", sound.beats.first().label)
        assertEquals("heard tones retain the performed minor seventh", "Em7", combined.beats.first().label)
        assertEquals("the following dominant seventh survives", listOf("A7", "A7"), combined.beats.filter { it.time in 2.0..3.0 }.map { it.label })
        val analysed = TakeAnalysis.analyse(x, rate, found = BeatThis.Track(beats, emptyList()), oneAtATime = false, heard = heard)
        assertEquals("take analysis forwards the transcription to the chart", "Em7", analysed.chords.beats.first().label)
        assertEquals(snapshot, heard)
        assertEquals(sound.beats.map { it.time }, combined.beats.map { it.time })
        assertNotEquals("the notes actually affect the chart", sound.beats.map { it.label }, combined.beats.map { it.label })
        assertEquals(sound, ChordFinder.find(y, TakeAnalysis.RATE, beats, four, null, emptyList()))
    }
}
