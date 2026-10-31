package com.betterappmafia.apophenia.core.analysis

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import com.betterappmafia.apophenia.core.sound.PianoSampler
import com.betterappmafia.apophenia.core.sound.PlayNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.FloatBuffer

class BarMeterTest {
    /** An onset-strength curve with a spike at each of [times] (seconds), [loud] at the beats, [soft] elsewhere. */
    private fun envelope(seconds: Double, beats: List<Double>, extra: List<Double>): Onsets.Envelope {
        val hop = Onsets.HOP.toDouble() / TakeAnalysis.RATE
        val s = FloatArray((seconds / hop).toInt())
        fun spike(t: Double, v: Float) { val i = (t / hop - Onsets.FRAME / 2.0 / Onsets.HOP).toInt(); if (i in s.indices) s[i] = maxOf(s[i], v) }
        beats.forEach { spike(it, 10f) }
        extra.forEach { spike(it, 4f) }
        return Onsets.Envelope(s, FloatArray(s.size) { 0.1f }, hop)
    }

    /** [bars] bars of [perBar] beats [beat] seconds apart, every beat split in [split]; the downbeats every [perBar]. */
    private fun take(bars: Int, perBar: Int, beat: Double, split: Int): Pair<BeatThis.Track, Onsets.Envelope> {
        val beats = List(bars * perBar + 1) { 0.5 + it * beat }
        val downs = beats.filterIndexed { i, _ -> i % perBar == 0 }
        val extra = beats.dropLast(1).flatMap { b -> (1 until split).map { k -> b + k * beat / split } }
        return BeatThis.Track(beats, downs) to envelope(beats.last() + 1, beats, extra)
    }

    @Test fun `three beats between downbeats is 3-4, four is 4-4, two is 2-2`() {
        assertEquals(TimeSignature.of(3, 4), take(8, 3, 0.5, 2).let { (t, e) -> BarMeter.read(t, e)?.meter })
        assertEquals(TimeSignature.of(4, 4), take(8, 4, 0.5, 2).let { (t, e) -> BarMeter.read(t, e)?.meter })
        assertEquals(TimeSignature.of(2, 2), take(8, 2, 0.9, 2).let { (t, e) -> BarMeter.read(t, e)?.meter })
    }

    @Test fun `duple bars whose beats split in three are 6-8 or 12-8`() {
        val (t, e) = take(8, 2, 0.9, 3)
        val r = BarMeter.read(t, e)!!
        assertEquals(TimeSignature.of(6, 8), r.meter)
        assertTrue("triple ${r.triple}", r.triple > 0.9)
        assertEquals("2 beats a bar, 9 of 9 downbeats agree, each beat split in three", r.why)
        assertEquals(TimeSignature.of(12, 8), take(8, 4, 0.6, 3).let { (t2, e2) -> BarMeter.read(t2, e2)?.meter })
        // Three plain beats split in three stay 3/4: nothing here tells 9/8 from 3/4 played in triplets.
        assertEquals(TimeSignature.of(3, 4), take(8, 3, 0.6, 3).let { (t2, e2) -> BarMeter.read(t2, e2)?.meter })
    }

    @Test fun `stray downbeats are outvoted, and the bars start on the beat the downbeats agree on`() {
        // A waltz with a pickup beat, 12 bars; Beat This adds a downbeat on beat 2 of every fourth bar.
        val beats = List(1 + 12 * 3 + 1) { 0.5 + it * 0.6 }
        val downs = (1 until beats.size step 3).flatMap { i -> listOfNotNull(beats[i], beats.getOrNull(i + 1)?.takeIf { (i - 1) % 12 == 0 }) }
        val r = BarMeter.read(BeatThis.Track(beats, downs), envelope(beats.last() + 1, beats, beats.map { it + 0.3 }))!!
        assertEquals(TimeSignature.of(3, 4), r.meter)
        assertEquals(beats[1], r.start, 1e-9)
        assertEquals("3 beats a bar, 13 of 16 downbeats agree", r.why)
    }

    @Test fun `too few bars, or bars that disagree, leave the meter unread`() {
        take(3, 3, 0.5, 2).let { (t, e) -> assertNull(BarMeter.read(t, e)) }
        // Downbeats alternating three and four beats apart: no count holds 75% of the bars.
        val beats = List(36) { 0.5 + it * 0.5 }
        val downs = mutableListOf(0); while (downs.last() + 7 < beats.size) { downs += downs.last() + 3; downs += downs.last() + 4 }
        val t = BeatThis.Track(beats, downs.map { beats[it] })
        assertEquals(t.downbeats.size, BarMeter.onBeats(t.beats, t.downbeats).size)
        assertNull(BarMeter.read(t, envelope(20.0, beats, emptyList())))
    }

    @Test fun `the reading is stored with the analysis and read back`() {
        val r = TakeAnalysis.Result(10.0, Tempo(emptyList(), 0.0, true), emptyList(), 0.0, KeyResult(emptyList(), true, "sound"),
            ChordFinder.Track(emptyList(), TimeSignature.of(6, 8), false), meterWhy = "2 beats a bar, 9 of 9 downbeats agree, each beat split in three")
        assertEquals(r, AnalysisText.decode(AnalysisText.encode(r)))
        assertNull(AnalysisText.decode(AnalysisText.encode(r.copy(meterWhy = null)))!!.meterWhy)
    }

    // The real model on the app's piano: no drums, the chord changing on every bar line.
    private val net: BeatThis.Net by lazy {
        val env = OrtEnvironment.getEnvironment()
        val session = env.createSession("../app/src/main/assets/models/beat-this/beat_this_small0.onnx")
        BeatThis.Net { spect, frames ->
            OnnxTensor.createTensor(env, FloatBuffer.wrap(spect), longArrayOf(1, frames.toLong(), BeatThis.MELS.toLong())).use { x ->
                session.run(mapOf("spect" to x)).use { r ->
                    val b = (r.get(0) as OnnxTensor).floatBuffer.let { fb -> FloatArray(fb.remaining()).also { fb.get(it) } }
                    val d = (r.get(1) as OnnxTensor).floatBuffer.let { fb -> FloatArray(fb.remaining()).also { fb.get(it) } }
                    b to d
                }
            }
        }
    }

    private val piano by lazy {
        val samples = File("../app/src/main/assets/piano").listFiles()!!.mapNotNull { f ->
            val m = Regex("mf-(\\d+)\\.wav").matchEntire(f.name)?.groupValues?.get(1)?.toInt() ?: return@mapNotNull null
            val b = f.readBytes()
            val pcm = java.nio.ByteBuffer.wrap(b, 44, b.size - 44).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            m to FloatArray(pcm.remaining()) { pcm.get(it) / 32768f }
        }.toMap()
        PianoSampler(listOf(PianoSampler.Layer(80, samples)))
    }

    private val chords = listOf(listOf(48, 55, 60, 64), listOf(45, 52, 57, 60), listOf(41, 48, 53, 57), listOf(43, 50, 55, 59))
    /** I vi IV V ii V I I, as in the measurement set (`docs/STATUS.md` 0.9.37). */
    private val eight = chords + listOf(listOf(50, 57, 60, 65), listOf(43, 50, 55, 59), listOf(48, 55, 60, 64), listOf(48, 55, 60, 64))
    /** A player's timing: up to about 8 ms either side, the same every run. */
    private val hum = java.util.Random(7).let { r -> { r.nextGaussian() * 0.008 } }

    private fun analyse(notes: List<PlayNote>, seconds: Double): TakeAnalysis.Result {
        val rate = TakeAnalysis.RATE
        val x = piano.render(notes, rate, seconds + 0.5)
        val f = BeatThis.track(Resample.to(x, rate, BeatThis.RATE), net)
        return TakeAnalysis.analyse(x, rate, found = f)
    }

    @Test fun `a piano waltz reads 3-4 and a 6-8 arpeggio reads 6-8`() {
        // Waltz at 100: the bass on 1, the chord on 2 and 3, 16 bars of I vi IV V ii V I I, played (not quantised). Not
        // every waltz reads: perfectly quantised on four chords it got stray downbeats and stayed a guess. The measured
        // rates are in STATUS 0.9.37; this checks the path end to end on one that reads.
        val beat = 0.6
        val waltz = (0 until 16).flatMap { bar ->
            val c = eight[bar % 8]; val t = bar * 3 * beat
            listOf(PlayNote(maxOf(0.0, t + hum()), t + beat * 0.9, c[0] - 12, 100)) + (1..2).flatMap { k -> c.drop(1).map { PlayNote(t + k * beat + hum(), t + k * beat + beat * 0.6, it, 75) } }
        }
        val w = analyse(waltz, 16 * 3 * beat)
        println("waltz: ${w.chords.meter} ${w.meterWhy}")
        assertEquals(TimeSignature.of(3, 4), w.chords.meter)
        // 6/8 with the eighth at 180: bass on 1 and 4, two groups of three.
        val e = 60.0 / 180
        val six = (0 until 16).flatMap { bar ->
            val c = chords[bar % 4]; val t = bar * 6 * e
            listOf(c[0] - 12, c[2], c[3], c[1] - 12, c[2], c[3]).mapIndexed { k, m -> PlayNote(t + k * e, t + k * e + 1.5 * e, m, if (k % 3 == 0) 100 else 55) }
        }
        val s = analyse(six, 16 * 6 * e)
        println("6/8: ${s.chords.meter} ${s.meterWhy}")
        assertEquals(TimeSignature.of(6, 8), s.chords.meter)
        // Counted in eighths or in dotted quarters, the tempo is the dotted quarter's: 60.
        assertEquals(60.0, s.tempo.best!!, 1.5)
        assertTrue(s.chords.beats.zipWithNext().all { (a, b) -> kotlin.math.abs(b.time - a.time - 1.0) < 0.1 })
    }

    @Test fun `piano in 4-4 never reads as three or as compound`() {
        // Block chords on every beat at 100, the first of each bar louder.
        val beat = 0.6
        val block = (0 until 16).flatMap { bar ->
            val c = chords[bar % 4]
            (0 until 4).flatMap { k -> val t = (bar * 4 + k) * beat; c.map { PlayNote(t, t + beat * 0.8, if (it == c[0]) it - 12 else it, if (k == 0) 100 else 60) } }
        }
        val r = analyse(block, 16 * 4 * beat)
        println("4/4 block: ${r.chords.meter} ${r.meterWhy}")
        assertTrue("read ${r.chords.meter}", r.chords.meter in setOf(TimeSignature.of(4, 4), TimeSignature.of(2, 2)))
    }

    init { require(File("../app/src/main/assets/models/beat-this/beat_this_small0.onnx").isFile) }
}
