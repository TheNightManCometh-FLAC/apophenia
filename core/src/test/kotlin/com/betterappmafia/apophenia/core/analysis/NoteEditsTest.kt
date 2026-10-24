package com.betterappmafia.apophenia.core.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class NoteEditsTest {
    private fun n(start: Double, end: Double, midi: Int) = BasicPitch.TimedNote(start, end, midi, 0.6, listOf(1, 1))
    private val riff = listOf(n(0.02, 0.48, 40), n(0.52, 0.71, 43), n(0.74, 1.3, 45))

    @Test fun `a corrected pitch drops its bends, timing moves and stretches without going below zero or nothing`() {
        val up = NoteEdits.transpose(riff, 1, 2)
        assertEquals(45, up[1].midi)
        assertEquals(null, up[1].bends)
        assertEquals(riff[0], up[0])
        assertEquals(0.0, NoteEdits.move(riff, 0, -1.0)[0].start, 1e-12)
        assertEquals(0.46, NoteEdits.move(riff, 0, -1.0)[0].end, 1e-12)
        assertEquals(0.52 + NoteEdits.MIN_LENGTH, NoteEdits.resize(riff, 1, -5.0)[1].end, 1e-12)
        assertEquals(108, NoteEdits.transpose(riff, 2, 100)[2].midi)
    }

    @Test fun `added notes go in time order and deleting removes just that one`() {
        val (list, i) = NoteEdits.add(riff, 0.6, 0.1, 50)
        assertEquals(2, i)
        assertEquals(listOf(40, 43, 50, 45), list.map { it.midi })
        assertEquals(listOf(40, 50, 45), NoteEdits.delete(list, 1).map { it.midi })
    }

    @Test fun `quantising snaps starts and ends to the grid, keeps every note at least a step, and leaves the notes untouched`() {
        val g = Grid(bpm = 120.0, division = 2)     // eighths: 0.25 s
        val q = g.apply(riff)
        assertEquals(listOf(0.0, 0.5, 0.75), q.map { it.start })
        assertEquals(listOf(0.5, 0.75, 1.25), q.map { it.end })
        assertEquals(0.02, riff[0].start, 1e-12)    // the played times are still there
        val short = Grid(120.0, 2).snap(n(0.3, 0.32, 60))
        assertEquals(0.25, short.end - short.start, 1e-12)
        // A grid that starts where the click's first downbeat was.
        assertEquals(0.6, Grid(120.0, 1, origin = 0.1).snapTime(0.55), 1e-12)
    }

    @Test fun `the synth sounds each note where it is and stays under full scale`() {
        val rate = 8000
        val x = NoteSynth.render(listOf(n(0.5, 0.7, 69), n(0.5, 0.7, 72), n(0.5, 0.7, 76)), rate, 1.0)
        assertTrue(x.copyOfRange(0, (0.49 * rate).toInt()).all { it == 0f })
        assertTrue(x.copyOfRange((0.52 * rate).toInt(), (0.6 * rate).toInt()).any { abs(it) > 0.2f })
        assertTrue(x.all { abs(it) <= 0.9f + 1e-6f })
    }
}
