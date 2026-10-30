package com.betterappmafia.apophenia.core.analysis

import org.junit.Assert.*
import org.junit.Test

class ChordEvidenceTest {
    private fun lines(name: String) = javaClass.getResource("/chordtracking/$name")!!.readText().lineSequence().filter { it.isNotBlank() && !it.startsWith('#') }.toList()
    private data class Fixture(val segments: ChordFinder.Segments, val bounds: List<Double>, val notes: List<BasicPitch.TimedNote>)
    private fun fixture(name: String): Fixture {
        val rows = lines("$name.segments.tsv").map { it.split('\t') }
        val bounds = rows.map { it[0].toDouble() } + rows.last()[1].toDouble()
        fun vector(s: String) = s.split(',').map { it.toDouble() }.toDoubleArray()
        val segments = ChordFinder.Segments(rows.map { vector(it[3]) }, rows.map { vector(it[4]) }, rows.map { it[2].toDouble() }.toDoubleArray())
        val notes = lines("$name.notes.tsv").map { it.split('\t').let { BasicPitch.TimedNote(it[0].toDouble(), it[1].toDouble(), it[2].toInt(), it[3].toDouble(), null) } }
        return Fixture(segments, bounds, notes)
    }

    @Test fun `quiet detected seventh is retained in the chord evidence`() {
        // Actual GuitarSet audio features and Basic Pitch notes: the F#7 from 1.08 to 3.08 s
        // lost its seventh when a spectral noise-floor subtraction was applied to detected notes.
        val f = fixture("00_BN2-166-Ab_comp")
        val labels = ChordFinder.label(f.segments, null, ChordFinder.notesChroma(f.notes, f.bounds))
        val window = f.bounds.dropLast(1).zip(labels).filter { it.first in 1.08..2.52 }.map { it.second.first.substringBefore('/') }
        assertEquals(listOf("F#7", "F#7", "F#7"), window)
    }

    @Test fun `known funk root survives with or without separate bass evidence and notes stay unchanged`() {
        val f = fixture("00_Funk3-112-C#_comp")
        val before = f.notes.toList()
        val chroma = ChordFinder.notesChroma(f.notes, f.bounds)
        val soundBass = ChordFinder.label(f.segments, null, chroma)
        val combined = ChordFinder.label(f.segments, null, chroma, ChordFinder.notesBass(f.notes, f.bounds))
        fun window(labels: List<Pair<String, Double>>) = f.bounds.dropLast(1).zip(labels).filter { it.first in 19.3..20.92 }.map { it.second.first.substringBefore('/') }
        // Compression in 0.9.24 fixes this root even without the extra bass vote.
        assertEquals("root is retained before the extra bass vote", listOf("Ab", "Ab", "Ab", "Ab"), window(soundBass))
        assertEquals("G# major from the annotation, spelled Ab in this context", listOf("Ab", "Ab", "Ab", "Ab"), window(combined))
        assertEquals(before, f.notes)
    }

    @Test fun `a detected minor seventh survives repeated pitch classes in the guitar voicing`() {
        // Performed GuitarSet annotation: C#:min7/5. The 0.9.23 linear note weights
        // reduced this to C#m; all input notes and their amplitudes are real detections.
        val f = fixture("00_BN2-166-Ab_comp")
        val labels = ChordFinder.label(f.segments, com.betterappmafia.apophenia.core.catalog.KeyMode.parseOrNull("E major"),
            ChordFinder.notesChroma(f.notes, f.bounds), ChordFinder.notesBass(f.notes, f.bounds))
        val window = f.bounds.dropLast(1).zip(labels).filter { it.first in 11.54..12.28 }.map { it.second.first.substringBefore('/') }
        assertEquals(listOf("C#m7", "C#m7"), window)
    }

    @Test fun `bass evidence follows sounding notes and exact boundaries without doubling unisons`() {
        val notes = listOf(
            BasicPitch.TimedNote(0.0, 1.0, 48, 0.8, null),
            BasicPitch.TimedNote(0.2, 0.4, 40, 0.5, null),
            BasicPitch.TimedNote(0.4, 0.7, 43, 0.6, null),
            BasicPitch.TimedNote(0.4, 0.6, 43, 0.9, null)
        )
        val b = ChordFinder.notesBass(notes.reversed(), listOf(0.0, 0.5, 1.0, 1.5))
        val first = DoubleArray(12).also { it[0] = 0.16; it[4] = 0.10; it[7] = 0.09 }
        val second = DoubleArray(12).also { it[0] = 0.24; it[7] = 0.15 }
        assertArrayEquals(first, b[0], 1e-12)
        assertArrayEquals(second, b[1], 1e-12)
        assertArrayEquals(DoubleArray(12), b[2], 0.0)
    }
}
