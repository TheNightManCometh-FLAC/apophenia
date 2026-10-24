package com.betterappmafia.apophenia.core.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class TakeAnalysisTest {
    private val rate = 48000

    /** Short noise bursts (like a pick or a stick) at [times], [gains] loud. */
    private fun clicks(seconds: Double, times: List<Double>, gains: List<Double> = times.map { 1.0 }): FloatArray {
        val x = FloatArray((seconds * rate).toInt())
        val r = Random(7)
        times.forEachIndexed { k, t ->
            val at = (t * rate).toInt()
            for (i in 0 until (0.03 * rate).toInt()) if (at + i < x.size) x[at + i] += (gains[k] * (r.nextDouble() * 2 - 1) * exp(-i / (0.006 * rate))).toFloat()
        }
        return x
    }

    private fun beats(bpm: Double, seconds: Double, every: Double = 1.0) = generateSequence(0.2) { it + 60.0 / bpm * every }.takeWhile { it < seconds - 0.1 }.toList()

    private fun tempoOf(x: FloatArray): Tempo {
        val y = Resample.to(x, rate, TakeAnalysis.RATE)
        val env = Onsets.envelope(y, TakeAnalysis.RATE)
        return Tempo.estimate(env, Onsets.pick(env))
    }

    @Test fun `a steady click reads as its tempo, with half and double as alternatives`() {
        for (bpm in listOf(72.0, 120.0, 160.0)) {
            val t = tempoOf(clicks(12.0, beats(bpm, 12.0)))
            assertFalse("$bpm free", t.free)
            assertEquals("$bpm", bpm, t.best!!, bpm * 0.015)
            assertTrue(t.candidates.any { it.why == "half" && kotlin.math.abs(it.bpm - bpm / 2) < 2 })
        }
    }

    @Test fun `eighth notes accented on the beat read as the beat, the eighths as the double`() {
        val times = beats(92.0, 12.0, 0.5)
        val t = tempoOf(clicks(12.0, times, times.indices.map { if (it % 2 == 0) 1.0 else 0.45 }))
        assertEquals(92.0, t.best!!, 1.5)
        assertTrue(t.candidates.any { it.why == "double" && kotlin.math.abs(it.bpm - 184) < 3 })
    }

    @Test fun `scattered attacks and very short takes are free time, not a made-up number`() {
        val r = Random(3)
        var t = 0.2
        val times = ArrayList<Double>()
        while (t < 12) { times += t; t += 0.15 + r.nextDouble() * 0.9 }
        assertTrue(tempoOf(clicks(12.0, times)).free)
        assertTrue(tempoOf(clicks(3.0, beats(120.0, 3.0))).free)
    }

    /** Sine notes (midi, seconds) one after another with a short gap. */
    private fun melody(vararg notes: Pair<Int, Double>): FloatArray {
        val out = ArrayList<Float>()
        for ((m, d) in notes) {
            val hz = 440.0 * Math.pow(2.0, (m - 69) / 12.0)
            val n = (d * rate).toInt()
            for (i in 0 until n) out += (0.5 * sin(2 * PI * hz * i / rate) * minOf(1.0, i / 200.0, (n - i) / 200.0)).toFloat()
            repeat((0.04 * rate).toInt()) { out += 0f }
        }
        return out.toFloatArray()
    }

    @Test fun `a single line comes back as its notes, bass low E included`() {
        val x = melody(45 to 0.5, 48 to 0.4, 50 to 0.4, 52 to 0.6, 28 to 0.8)
        val r = TakeAnalysis.analyse(x, rate)
        assertEquals(r.notes.toString(), listOf(45, 48, 50, 52, 28), r.notes.map { it.midi })
        assertEquals(0.0, r.notes[0].start, 0.06)
        assertTrue(r.notes.all { kotlin.math.abs(it.cents) < 15 })
    }

    @Test fun `an A minor line that ends on A reads as A minor, with its reasons`() {
        val x = melody(57 to 0.4, 59 to 0.3, 60 to 0.3, 62 to 0.3, 64 to 0.3, 65 to 0.3, 67 to 0.3, 64 to 0.3, 60 to 0.3, 57 to 0.8)
        val k = TakeAnalysis.analyse(x, rate).key
        assertEquals("notes", k.from)
        assertFalse(k.uncertain)
        assertEquals("A aeolian", k.best.toString())
        assertTrue(k.candidates[0].reason, k.candidates[0].reason.contains("all in the scale"))
        assertTrue(k.candidates[0].reason, k.candidates[0].reason.contains("A is ends on it"))
    }

    @Test fun `a pentatonic riff leaves the mode uncertain, a tonic with no home clue too`() {
        // A C D E G: A dorian and A minor both fit every note.
        val penta = melody(57 to 0.4, 60 to 0.3, 62 to 0.3, 64 to 0.3, 67 to 0.3, 64 to 0.3, 57 to 0.8)
        val k = TakeAnalysis.analyse(penta, rate).key
        assertTrue(k.uncertain)
        assertTrue(k.candidates.take(2).all { it.key.tonic.toString() == "A" })
        val flat = KeyEvidence.rank(KeyEvidence.Evidence(DoubleArray(12) { if (it in listOf(0, 2, 4, 5, 7, 9, 11)) 1.0 else 0.0 }, DoubleArray(12), emptyMap(), "notes"))
        assertTrue(flat.uncertain)   // C major and A minor (and the other modes) share every note
    }

    @Test fun `a result survives being stored as text, and other text reads as nothing`() {
        val x = melody(57 to 0.4, 59 to 0.3, 60 to 0.3, 62 to 0.3, 64 to 0.3, 65 to 0.3, 67 to 0.3, 64 to 0.3, 60 to 0.3, 57 to 0.8)
        val r = TakeAnalysis.analyse(x, rate)
        assertEquals(r, AnalysisText.decode(AnalysisText.encode(r)))
        assertEquals(null, AnalysisText.decode("analysis\t99\n"))
        assertEquals(null, AnalysisText.decode(null))
    }

    @Test fun `tonics are spelled the way the key is written`() {
        assertEquals("Db major", KeyEvidence.keyFor(1, "major").toString())
        assertEquals("F# aeolian", KeyEvidence.keyFor(6, "aeolian").toString())
        assertEquals("Bb major", KeyEvidence.keyFor(10, "major").toString())
        assertEquals("G# aeolian", KeyEvidence.keyFor(8, "aeolian").toString())
        assertEquals("Eb dorian", KeyEvidence.keyFor(3, "dorian").toString())
    }

    @Test fun `chords read from the spectrum with the bass as the home clue`() {
        // C, F, G, C triads with the root an octave below, a second each.
        val chords = listOf(listOf(36, 60, 64, 67), listOf(41, 60, 65, 69), listOf(43, 62, 67, 71), listOf(36, 60, 64, 67))
        val x = FloatArray(4 * rate)
        chords.forEachIndexed { c, ms -> for (m in ms) { val hz = 440.0 * Math.pow(2.0, (m - 69) / 12.0); for (i in 0 until rate) x[c * rate + i] += (0.2 * sin(2 * PI * hz * i / rate)).toFloat() } }
        val k = KeyEvidence.rank(KeyEvidence.fromChroma(Resample.to(x, rate, TakeAnalysis.RATE), TakeAnalysis.RATE))
        assertEquals("chroma", k.from)
        assertEquals("C major", k.candidates[0].key.toString())
    }
}
