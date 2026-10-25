package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.instrument.Instrument
import com.betterappmafia.apophenia.core.instrument.Tunings
import com.betterappmafia.apophenia.core.learn.CourseReading
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.pitch.SpelledPitch
import com.betterappmafia.apophenia.core.rhythm.Duration
import com.betterappmafia.apophenia.core.rhythm.Tempo
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MidiReadTest {
    private val q = Duration.QUARTER
    private val e = Duration.EIGHTH
    private fun ev(d: Duration, vararg p: String) = Event(d, p.map { Note(SpelledPitch.parse(it)) })
    private fun tied(d: Duration, p: String) = Event(d, listOf(Note(SpelledPitch.parse(p), tie = true)))

    /** Each part's notes as (start tick, length, spelled pitch): what a piano roll shows. */
    private fun roll(s: Score) = s.parts.map { p -> PianoRoll.notes(s, p).map { Triple(it.start, it.length, it.pitch.toString()) }.sortedWith(compareBy({ it.first }, { it.third })) }

    @Test
    fun `a score written as MIDI reads back with the same notes, spelling, meters, tempo, key and markers`() {
        val trip = q.inTuplet(3, 2)
        val s = Score(
            title = "Tide", meter = TimeSignature.of(4, 4), key = KeyMode(PitchName.parse("Bb"), "major"), tempo = Tempo(90.0),
            parts = listOf(
                Part("Lead", Clef.TREBLE, bars = listOf(
                    Bar(listOf(listOf(ev(q, "Bb4"), ev(q, "D5", "F5"), Event(q), tied(q, "Eb5"))), rehearsal = "Verse"),
                    Bar(listOf(listOf(ev(q, "Eb5"), ev(trip, "C5"), ev(trip, "D5"), ev(trip, "Eb5")))),
                    Bar(listOf(listOf(ev(e, "F5"), ev(e, "G5"), ev(Duration.HALF, "A5"))), meter = TimeSignature.of(3, 4), tempo = Tempo(120.0)),
                )),
                Part("Bass", Clef.BASS, Instrument(Tunings.BASS_STANDARD), listOf(
                    Bar(listOf(listOf(Event(Duration.WHOLE, listOf(Note(SpelledPitch.parse("Bb1"), 3, 1)))))),
                    Bar(listOf(listOf(Event(Duration.WHOLE, listOf(Note(SpelledPitch.parse("Eb2"), 2, 1)))))),
                    Bar(listOf(listOf(Event(Duration.HALF.dotted(), listOf(Note(SpelledPitch.parse("F2"), 2, 3)))))),
                )),
            ),
        )
        val r = MidiRead.read(ScoreMidi.write(s), "Tide").score
        assertEquals(roll(s), roll(r))
        assertEquals(listOf("Lead", "Bass"), r.parts.map { it.name })
        assertEquals(KeyMode(PitchName.parse("Bb"), "major"), r.key)
        assertEquals(90.0, r.tempo.bpm)
        assertEquals(TimeSignature.of(3, 4), r.parts[0].bars[2].meter)
        assertEquals(120.0, r.parts[0].bars[2].tempo?.bpm)
        assertEquals("Verse", r.parts[0].bars[0].rehearsal)
        assertEquals(Tunings.BASS_STANDARD, r.parts[1].instrument?.tuning, "program 33 is a bass")
        assertEquals(Clef.BASS, r.parts[1].clef)
        assertEquals(listOf(3 to 1, 2 to 1, 2 to 3), r.parts[1].bars.map { b -> b.voices[0][0].notes[0].let { it.string!! to it.fret!! } }, "fingered on the bass")
        assertTrue(r.parts[0].bars[1].voices[0].drop(1).dropLast(1).all { it.duration.tuplets.isNotEmpty() }, "the triplet stays a triplet: " + r.parts[0].bars[1].voices[0].map { ScoreText.eventToken(it) })
        assertNull(r.parts[0].bars[0].voices[0][0].notes[0].velocity, "one velocity throughout isn't stored")
    }

    @Test
    fun `repeats come in played out, and the course tab comes back as guitar tab`() {
        val road = MidiRead.read(ScoreMidi.write(CourseReading.roadmap)).score
        assertEquals(listOf(60, 62, 64, 60, 62, 65, 67, 60, 62), PianoRoll.notes(road, road.parts[0]).map { it.pitch.midi })
        val tab = MidiRead.read(ScoreMidi.write(CourseReading.tabScore)).score
        assertEquals(Tunings.GUITAR_STANDARD, tab.parts[0].instrument?.tuning)
        assertEquals(roll(CourseReading.tabScore).single().map { it.first to it.second }, roll(tab).single().map { it.first to it.second })
    }

    /** A tiny SMF writer for hand-made files: format, ppq, and tracks of (delta, bytes). */
    private fun smf(format: Int, ppq: Int, vararg tracks: List<Pair<Int, IntArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        fun i32(v: Int) = out.write(byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte()))
        fun i16(v: Int) = out.write(byteArrayOf((v shr 8).toByte(), v.toByte()))
        fun vlq(v: Int): ByteArray { val b = ArrayList<Byte>(); var x = v; b += (x and 0x7F).toByte(); x = x shr 7; while (x > 0) { b.add(0, ((x and 0x7F) or 0x80).toByte()); x = x shr 7 }; return b.toByteArray() }
        out.write("MThd".toByteArray()); i32(6); i16(format); i16(tracks.size); i16(ppq)
        for (t in tracks) {
            val body = ByteArrayOutputStream()
            for ((d, bytes) in t) { body.write(vlq(d)); body.write(ByteArray(bytes.size) { bytes[it].toByte() }) }
            body.write(byteArrayOf(0, 0xFF.toByte(), 0x2F, 0))
            out.write("MTrk".toByteArray()); i32(body.size()); out.write(body.toByteArray())
        }
        return out.toByteArray()
    }

    @Test
    fun `played timing snaps to sixteenths or triplets, running status and velocity-0 note-offs are read, drums get their own part`() {
        // ppq 480. Channel 1: four notes a little early and late on the sixteenths of beat 1, then a played triplet on beat 2.
        // Channel 10, after the lead: a kick at 955 and 1915 (beat 3 of bars 1 and 2). Running status after the first note-on; note-off as note-on at velocity 0.
        val t = listOf(
            0 to intArrayOf(0x90, 60, 90), 118 to intArrayOf(60, 0), 4 to intArrayOf(62, 70), 116 to intArrayOf(62, 0),
            5 to intArrayOf(64, 100), 110 to intArrayOf(64, 0), 6 to intArrayOf(65, 80), 117 to intArrayOf(65, 0),
            // beat 2 at 480: triplet eighths at 480, 640, 800
            4 to intArrayOf(67, 90), 150 to intArrayOf(67, 0), 12 to intArrayOf(69, 90), 152 to intArrayOf(69, 0), 6 to intArrayOf(71, 90), 155 to intArrayOf(71, 0),
            0 to intArrayOf(0x99, 36, 100), 10 to intArrayOf(0x89, 36, 0), 950 to intArrayOf(0x99, 36, 100), 10 to intArrayOf(36, 0),
        )
        val r = MidiRead.read(smf(0, 480, t)).score
        assertEquals(2, r.parts.size)
        val lead = PianoRoll.notes(r, r.parts[0])
        assertEquals(listOf(0L, 240L, 480L, 720L, 960L, 1280L, 1600L), lead.map { it.start })
        assertEquals(listOf("C4", "D4", "E4", "F4", "G4", "A4", "B4"), lead.map { it.pitch.toString() })
        assertTrue(r.parts[0].bars[0].voices[0].any { it.duration.tuplets.isNotEmpty() }, "beat 2 written as a triplet")
        assertEquals(listOf(90, 70, 100, 80), lead.take(4).map { it.note.velocity }, "velocities vary, so they're kept")
        assertEquals(Clef.PERCUSSION, r.parts[1].clef)
        assertEquals("Drums", r.parts[1].name)
        assertEquals(listOf(1920L, 3840L), PianoRoll.notes(r, r.parts[1]).map { it.start }, "deltas run on through the track: kicks on beat 3 of bar 1 and bar 2")
    }

    @Test
    fun `a file that isn't MIDI, or has no notes, is refused`() {
        assertFailsWith<IllegalArgumentException> { MidiRead.read("RIFF....WAVE".toByteArray()) }
        assertFailsWith<IllegalArgumentException> { MidiRead.read(smf(1, 480, listOf(0 to intArrayOf(0xFF, 0x51, 3, 7, 0xA1, 0x20)))) }
    }
}
