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

    @Test fun `a spread of nearly every note names no key, even with a strong bass note`() {
        // The emulator's microphone (26 Oct 2026): eleven pitch classes at 4–15% each, G strongest in the bass. It ranked
        // G Lydian without doubt. Real piano chords and lines put 0.795–0.838 of their chroma in the top key's scale.
        val w = DoubleArray(12).also { for ((pc, v) in listOf(7 to 15.0, 9 to 11.0, 10 to 8.0, 0 to 2.0, 2 to 13.0, 4 to 5.0, 6 to 12.0, 1 to 7.0, 3 to 4.0, 5 to 8.0, 8 to 9.0, 11 to 6.0)) it[pc] = v }
        val hints = DoubleArray(12).also { it[7] = 0.6 }
        val k = KeyEvidence.rank(KeyEvidence.Evidence(w, hints, mapOf(7 to listOf("strongest in the bass")), "chroma"))
        assertTrue(k.candidates[0].key.toString(), k.uncertain)
        assertEquals(null, k.best)
    }

    /** A sung "ah" (two formants) along [hz], each held [each] seconds, with a 5.5 Hz vibrato [cents] wide either way. */
    private fun sung(hz: List<Double>, each: Double, cents: Double): FloatArray {
        var phase = 0.0
        return FloatArray((hz.size * each * rate).toInt()) { i ->
            val t = i.toDouble() / rate
            val f0 = hz[(t / each).toInt()] * Math.pow(2.0, cents / 1200 * sin(2 * PI * 5.5 * t))
            phase += 2 * PI * f0 / rate
            var v = 0.0
            for (h in 1..20) v += (exp(-Math.pow((f0 * h - 700) / 180, 2.0)) + 0.6 * exp(-Math.pow((f0 * h - 1200) / 220, 2.0)) + 0.1) * sin(phase * h)
            (0.1 * v * minOf(1.0, (t % each) / 0.03, (each - t % each) / 0.03)).toFloat()
        }
    }

    @Test fun `a spoken memo gets no key, a sung line with wide vibrato still holds its notes`() {
        val (memo, memoRate) = SpeechTest.readWav(java.io.File(javaClass.getResource("/speech/memo.wav")!!.toURI()))
        val memoResult = TakeAnalysis.analyse(memo, memoRate)
        val spoken = memoResult.key
        assertTrue("speech has no chords: ${memoResult.chords.beats.map { it.label }}", memoResult.chords.beats.isEmpty())
        assertEquals("speech", spoken.from)
        assertEquals(null, spoken.best)
        // D E G E D C, 1.5 s each, vibrato ±85 cents: the smoothing keeps it held, so it is not taken for speech.
        val line = sung(listOf(293.66, 329.63, 392.0, 329.63, 293.66, 261.63), 1.5, 85.0)
        val curve = MonoNotes.curve(Resample.to(line, rate, TakeAnalysis.RATE), TakeAnalysis.RATE, 30.0, 1000.0).midi
        assertTrue("held ${MonoNotes.held(curve)}", MonoNotes.held(curve) > 0.9)
        assertEquals("notes", TakeAnalysis.analyse(line, rate).key.from)
    }

    @Test fun `a sung note with wide vibrato is one note, at its centre pitch`() {
        // D E G E D C, 1.5 s each; vibrato ±85 cents used to cut each note into a dozen or more.
        for (cents in listOf(0.0, 50.0, 85.0, 100.0, 120.0)) {
            val notes = TakeAnalysis.analyse(sung(listOf(293.66, 329.63, 392.0, 329.63, 293.66, 261.63), 1.5, cents), rate).notes
            assertEquals("±$cents: $notes", listOf(62, 64, 67, 64, 62, 60), notes.map { it.midi })
        }
    }

    /** The app's own piano (Iowa MIS, real recordings: a low note's overtones are louder than its fundamental). */
    private val piano by lazy {
        val samples = java.io.File("../app/src/main/assets/piano").listFiles()!!.mapNotNull { f ->
            val m = Regex("mf-(\\d+)\\.wav").matchEntire(f.name)?.groupValues?.get(1)?.toInt() ?: return@mapNotNull null
            val b = f.readBytes()
            val pcm = java.nio.ByteBuffer.wrap(b, 44, b.size - 44).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            m to FloatArray(pcm.remaining()) { pcm.get(it) / 32768f }
        }.toMap()
        com.betterappmafia.apophenia.core.sound.PianoSampler(listOf(com.betterappmafia.apophenia.core.sound.PianoSampler.Layer(80, samples)))
    }

    /** Chords (MIDI, bass first), 1.5 s each, played twice through on the piano. */
    private fun pianoChords(vararg chords: List<Int>): FloatArray {
        val all = chords.toList() + chords.toList()
        return piano.render(all.flatMapIndexed { i, ms -> ms.map { com.betterappmafia.apophenia.core.sound.PlayNote(i * 1.5, (i + 1) * 1.5, it, 80) } }, rate, all.size * 1.5)
    }

    @Test fun `piano chords name their key from the bass, and ringing arpeggios are not speech`() {
        // Before 0.9.15 none of these named a key: chords went down the single-line path, and the bass clue took the
        // loudest bin under 250 Hz (G, the overtone of a low C; B3 inside a G7).
        assertEquals("C major", TakeAnalysis.analyse(pianoChords(listOf(36, 60, 64, 67), listOf(41, 60, 65, 69), listOf(43, 62, 67, 71), listOf(36, 60, 64, 67)), rate).key.best.toString())
        assertEquals("C major", TakeAnalysis.analyse(pianoChords(listOf(38, 60, 65, 69), listOf(43, 59, 62, 65), listOf(36, 59, 64, 67), listOf(36, 59, 64, 67)), rate).key.best.toString())
        assertEquals("A aeolian", TakeAnalysis.analyse(pianoChords(listOf(45, 60, 64, 69), listOf(41, 60, 65, 69), listOf(43, 59, 62, 67), listOf(45, 60, 64, 69)), rate).key.best.toString())
        assertEquals("Eb major", TakeAnalysis.analyse(pianoChords(listOf(39, 63, 67, 70), listOf(44, 63, 68, 72), listOf(46, 62, 65, 70), listOf(39, 63, 67, 70)), rate).key.best.toString())
        // Its chords, a beat each from the first attack at 0 s (the file starts mid-sound): before, the first C was lost
        // (no onset at frame 0) and C read as C/G (the low C's overtone taken for the bass).
        val cfgc = TakeAnalysis.analyse(pianoChords(listOf(36, 60, 64, 67), listOf(41, 60, 65, 69), listOf(43, 62, 67, 71), listOf(36, 60, 64, 67)), rate).chords
        assertEquals(listOf("C", "F", "G", "C", "C", "F", "G", "C"), cfgc.beats.take(8).map { it.label })
        // A C arpeggio whose notes ring into each other: the pitch jumps between them, but it is pitched nearly throughout.
        val arp = listOf(48, 52, 55, 60, 55, 52, 48, 52, 55, 60).mapIndexed { i, m -> com.betterappmafia.apophenia.core.sound.PlayNote(i * 0.3, i * 0.3 + 0.9, m, 80) }
        assertTrue(TakeAnalysis.analyse(piano.render(arp, rate, 4.0), rate).key.from != "speech")
    }

    @Test fun `overlap is the share of sounding time with two or more notes`() {
        assertEquals(0.0, TakeAnalysis.overlap(listOf(0.0 to 1.0, 1.0 to 2.0, 2.5 to 3.0)), 1e-9)          // one after another
        assertEquals(0.1, TakeAnalysis.overlap(listOf(0.0 to 1.1, 0.9 to 2.0)), 1e-9)                       // 0.2 s of 2.0 s
        assertEquals(1.0, TakeAnalysis.overlap(listOf(0.0 to 1.0, 0.0 to 1.0, 0.0 to 1.0)), 1e-9)          // a chord
        assertTrue(TakeAnalysis.overlap(listOf(0.0 to 1.1, 0.9 to 2.0)) < TakeAnalysis.SINGLE_LINE)
    }

    @Test fun `a line Notes to MIDI calls one note at a time gets no chords, and no key read from its overtones`() {
        // Both read a confident wrong key from the chord path in 0.9.15 (G major, C Lydian); their own notes leave it open.
        val pianoLine = piano.render(listOf(60, 62, 64, 67, 64, 62, 60, 55).mapIndexed { i, m -> com.betterappmafia.apophenia.core.sound.PlayNote(i * 0.8, i * 0.8 + 0.75, m, 80) }, rate, 7.4)
        val sungLine = sung(listOf(293.66, 329.63, 392.0, 329.63, 293.66, 261.63), 1.5, 0.0)
        for (x in listOf(pianoLine, sungLine)) {
            val r = TakeAnalysis.analyse(x, rate, oneAtATime = true)
            assertEquals("notes", r.key.from)
            assertTrue("chords: ${r.chords.beats.map { it.label }}", r.chords.beats.isEmpty())
            assertEquals(true, AnalysisText.decode(AnalysisText.encode(r))?.oneAtATime)
        }
        assertEquals("G major", TakeAnalysis.analyse(pianoLine, rate).key.best.toString())   // untranscribed: as before
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
