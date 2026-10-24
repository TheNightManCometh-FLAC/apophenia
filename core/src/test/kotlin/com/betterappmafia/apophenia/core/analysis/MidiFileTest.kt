package com.betterappmafia.apophenia.core.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiFileTest {
    /** A small reader for what [MidiFile] writes: (absolute tick, status, data bytes) of channel events, and the tempo. */
    private fun read(b: ByteArray): Pair<Int, List<Triple<Long, Int, List<Int>>>> {
        fun u(i: Int) = b[i].toInt() and 0xFF
        assertEquals("MThd", String(b, 0, 4))
        assertEquals(0, u(8) shl 8 or u(9))          // format 0
        assertEquals(480, u(12) shl 8 or u(13))
        assertEquals("MTrk", String(b, 14, 4))
        val end = 22 + (u(18) shl 24 or (u(19) shl 16) or (u(20) shl 8) or u(21))
        assertEquals(b.size, end)
        var p = 22
        var tick = 0L
        var tempo = 0
        val events = ArrayList<Triple<Long, Int, List<Int>>>()
        while (p < end) {
            var d = 0L
            while (true) { val c = u(p++); d = (d shl 7) or (c and 0x7F).toLong(); if (c and 0x80 == 0) break }
            tick += d
            val status = u(p++)
            when {
                status == 0xFF -> { val type = u(p++); val len = u(p++); if (type == 0x51) tempo = u(p) shl 16 or (u(p + 1) shl 8) or u(p + 2); p += len }
                status and 0xF0 == 0xC0 -> { events += Triple(tick, status, listOf(u(p))); p += 1 }
                else -> { events += Triple(tick, status, listOf(u(p), u(p + 1))); p += 2 }
            }
        }
        return tempo to events
    }

    @Test fun `notes land on the beat grid at the given tempo with velocity from their strength`() {
        val notes = listOf(BasicPitch.TimedNote(0.0, 0.5, 45, 1.0, null), BasicPitch.TimedNote(0.5, 1.0, 48, 0.5, null))
        val (tempo, ev) = read(MidiFile.write(notes, bpm = 120.0))
        assertEquals(500_000, tempo)
        val ons = ev.filter { it.second == 0x90 }
        assertEquals(listOf(0L, 480L), ons.map { it.first })      // half a second is a quarter at 120
        assertEquals(listOf(45, 48), ons.map { it.third[0] })
        assertEquals(listOf(127, 64), ons.map { it.third[1] })
        // At tick 480 the first note ends before the second starts.
        val at480 = ev.filter { it.first == 480L }.map { it.second }
        assertEquals(listOf(0x80, 0x90), at480)
    }

    @Test fun `a lone note carries its bends, overlapping notes don't`() {
        val bend = BasicPitch.TimedNote(0.0, 1.0, 40, 0.8, listOf(0, 3, 3))      // up a semitone by the end
        val ev = read(MidiFile.write(listOf(bend))).second.filter { it.second == 0xE0 }
        val values = ev.map { (it.third[1] shl 7 or it.third[0]) - 8192 }
        assertEquals(listOf(0, 4096, 4096, 0), values)                         // and back to centre after
        val chord = listOf(BasicPitch.TimedNote(0.0, 1.0, 40, 0.8, listOf(1, 1)), BasicPitch.TimedNote(0.2, 0.6, 47, 0.8, listOf(2, 2)))
        assertTrue(read(MidiFile.write(chord)).second.none { it.second == 0xE0 })
    }

    @Test fun `long deltas use more than one byte and the track length is exact`() {
        val notes = listOf(BasicPitch.TimedNote(100.0, 101.0, 60, 0.7, null))   // tick 96000
        val ev = read(MidiFile.write(notes)).second
        assertEquals(96000L, ev.first { it.second == 0x90 }.first)
    }

    @Test fun `transcriptions survive storage as text`() {
        val notes = listOf(BasicPitch.TimedNote(0.1, 0.4, 45, 0.61, listOf(0, -1)), BasicPitch.TimedNote(0.5, 0.9, 48, 0.4, null))
        assertEquals(notes, TranscriptionText.decode(TranscriptionText.encode("basic-pitch-icassp2022", notes)))
        assertEquals(null, TranscriptionText.decode("transcription\t2\tx\n"))
        // The quantise setting and the edited mark ride along without changing the notes.
        val s = TranscriptionText.encode("basic-pitch-icassp2022", notes, Grid(92.0, 4, 0.31), edited = true)
        assertEquals(notes, TranscriptionText.decode(s))
        assertEquals(Grid(92.0, 4, 0.31), TranscriptionText.grid(s))
        assertTrue(TranscriptionText.edited(s))
        assertEquals("basic-pitch-icassp2022", TranscriptionText.model(s))
        assertEquals(null, TranscriptionText.grid(TranscriptionText.encode("m", notes)))
    }
}
