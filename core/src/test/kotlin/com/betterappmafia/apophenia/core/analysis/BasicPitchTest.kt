package com.betterappmafia.apophenia.core.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class BasicPitchTest {
    private fun matrix(frames: Int, bins: Int = BasicPitch.N_NOTES) = Array(frames) { FloatArray(bins) }

    @Test fun `audio is cut into overlapping two-second windows behind half an overlap of silence`() {
        val audio = FloatArray(80000) { 1f }
        val w = BasicPitch.windows(audio)
        // 3840 + 80000 samples at a 36164 hop: windows start at 0, 36164, 72328.
        assertEquals(3, w.size)
        assertTrue(w.all { it.size == BasicPitch.AUDIO_N_SAMPLES })
        assertEquals(0f, w[0][3839])
        assertEquals(1f, w[0][3840])
        assertEquals(0f, w[2][83840 - 72328])   // past the end is zero-padded
    }

    @Test fun `window outputs join without their overlapping ends, trimmed to the audio`() {
        val perWindow = (0 until 3).map { w -> Array(BasicPitch.ANNOT_N_FRAMES) { t -> FloatArray(1) { (w * 1000 + t).toFloat() } } }
        val out = BasicPitch.unwrap(perWindow, originalLength = 80000)
        // Each window keeps frames 15..156 (142); 80000 / 36164 windows' worth = 314 frames.
        assertEquals((80000.0 / 36164 * 142).toInt(), out.size)
        assertEquals(15f, out[0][0])
        assertEquals(156f, out[141][0])
        assertEquals(1015f, out[142][0])
    }

    @Test fun `frame times follow the hop, less the reference's correction per window`() {
        assertEquals(0.0, BasicPitch.frameTime(0), 1e-12)
        assertEquals(171 * 256.0 / 22050, BasicPitch.frameTime(171), 1e-12)
        val offset = 256.0 / 22050 * (172 - 43844.0 / 256) + 0.0018
        assertEquals(172 * 256.0 / 22050 - offset, BasicPitch.frameTime(172), 1e-12)
        assertEquals(11, BasicPitch.MIN_NOTE_LEN)
    }

    @Test fun `a held note with an onset comes back with its start, end and pitch`() {
        val frames = matrix(100); val onsets = matrix(100)
        for (t in 10 until 60) frames[t][39] = 0.8f
        onsets[10][39] = 0.9f
        val e = BasicPitch.notes(frames, onsets, inferOnsets = false).single()
        assertEquals(10, e.startFrame)
        assertEquals(60, e.endFrame)
        assertEquals(60, e.midi)                      // bin 39 + 21: middle C
        assertEquals(0.8, e.amplitude, 1e-6)
    }

    @Test fun `energy with no onset is still found by the melodia pass, and a blip too short to be a note isn't`() {
        val frames = matrix(100); val onsets = matrix(100)
        for (t in 20 until 50) frames[t][28] = 0.6f
        for (t in 70 until 78) frames[t][50] = 0.9f    // 8 frames: under the 11-frame minimum
        val events = BasicPitch.notes(frames, onsets, inferOnsets = false)
        assertEquals(listOf(49), events.map { it.midi })
        assertEquals(20, events[0].startFrame)
        assertEquals(49, events[0].endFrame)        // the reference ends a melodia note on its last frame above threshold
    }

    @Test fun `two notes at once are both found, and a note's neighbour bins don't make a second note`() {
        val frames = matrix(100); val onsets = matrix(100)
        for (t in 10 until 40) { frames[t][39] = 0.9f; frames[t][40] = 0.5f; frames[t][43] = 0.7f }
        onsets[10][39] = 0.8f; onsets[10][43] = 0.7f
        val events = BasicPitch.notes(frames, onsets, inferOnsets = false)
        assertEquals(listOf(60, 64), events.map { it.midi }.sorted())
    }

    @Test fun `a rise in the frames counts as an onset when the onset output missed it`() {
        val frames = matrix(100); val onsets = matrix(100)
        onsets[5][10] = 0.6f                           // somewhere else, sets the scale
        for (t in 30 until 60) frames[t][20] = 0.9f
        val inferred = BasicPitch.inferredOnsets(onsets, frames)
        assertEquals(0.6f, inferred[30][20], 1e-6f)    // the first frame of the rise, scaled to the onsets' maximum
        assertEquals(0f, inferred[31][20], 1e-6f)
        val events = BasicPitch.notes(frames, onsets, melodiaTrick = false)
        assertTrue(events.any { it.midi == 41 && it.startFrame in 30..31 })
    }

    @Test fun `pitch bends read the contour a third of a semitone at a time`() {
        val contours = Array(40) { FloatArray(BasicPitch.N_CONTOURS) }
        // Middle C sits at contour bin 3 * 39 = 117; the energy is one bin sharp.
        for (t in 0 until 40) contours[t][118] = 1f
        val e = BasicPitch.withBends(contours, listOf(BasicPitch.Event(5, 15, 60, 0.5))).single()
        assertEquals(List(10) { 1 }, e.bends)
    }

    @Test fun `the low-pass keeps what a model listens to and takes out what would fold back`() {
        val rate = 24000
        fun level(hz: Double): Double {
            val x = FloatArray(rate) { (sin(2 * PI * hz * it / rate)).toFloat() }
            val y = Lowpass.apply(x, rate, 10000.0)
            return y.copyOfRange(1000, rate - 1000).maxOf { abs(it) }.toDouble()
        }
        assertEquals(1.0, level(1000.0), 0.01)
        assertTrue(level(11800.0) < 0.01)
    }
}
