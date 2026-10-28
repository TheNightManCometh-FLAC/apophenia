package com.betterappmafia.apophenia.core.analysis

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * The port against beat_this 1.1.0 itself: `ref40.json` holds what PyTorch gave (LogMelSpect, split_predict_aggregate
 * with the small0 checkpoint, the minimal postprocessor) for a 40 s signal both sides build the same way: 220 and
 * 330 Hz held, and a 1 kHz click every half second. 40 s is 2,001 frames, so two chunks and their overlap.
 */
class BeatThisTest {
    private val ref = javaClass.getResource("/beatthis/ref40.json")!!.readText()

    /** Every number in the JSON value of [key], flattened. */
    private fun numbers(key: String): DoubleArray {
        val at = ref.indexOf("\"$key\":")
        require(at >= 0) { key }
        var i = ref.indexOf('[', at); var depth = 0; val start = i
        while (true) { if (ref[i] == '[') depth++ else if (ref[i] == ']') { depth--; if (depth == 0) break }; i++ }
        return Regex("-?[0-9.]+(e-?[0-9]+)?").findAll(ref.substring(start, i)).map { it.value.toDouble() }.toList().toDoubleArray()
    }

    private val signal: FloatArray = FloatArray(BeatThis.RATE * 40) { i ->
        val t = i.toDouble() / BeatThis.RATE
        val tb = t % 0.5
        (0.3 * sin(2 * PI * 220 * t) + 0.2 * sin(2 * PI * 330 * t) + if (tb < 0.05) 0.6 * sin(2 * PI * 1000 * tb) * exp(-tb * 60) else 0.0).toFloat()
    }

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

    @Test fun the_log_mel_spectrogram_matches_torchaudio() {
        val s = BeatThis.spect(signal)
        assertEquals(Regex("\"frames\": ([0-9]+)").find(ref)!!.groupValues[1].toInt(), s.size / BeatThis.MELS)
        val want = numbers("spect300")
        var worst = 0.0
        for (i in want.indices) worst = maxOf(worst, abs(s[i] - want[i]))
        println("log-mel worst difference $worst")
        assertTrue("log-mel differs by $worst", worst < 1e-3)
    }

    @Test fun the_logits_and_beats_match_pytorch_across_chunks() {
        val (b, d) = BeatThis.logits(BeatThis.spect(signal), net)
        val wb = numbers("beat"); val wd = numbers("downbeat")
        var worst = 0.0
        for (i in wb.indices) worst = maxOf(worst, abs(b[i] - wb[i]), abs(d[i] - wd[i]))
        println("logit worst difference $worst")
        assertTrue("logits differ by $worst", worst < 1e-2)
        val track = BeatThis.track(b, d)
        val want = numbers("beats")
        assertEquals(want.size, track.beats.size)
        for (i in want.indices) assertEquals(want[i], track.beats[i], 1e-9)
        assertEquals(numbers("downbeats").toList(), track.downbeats)
        // And it is the clicks: every half second.
        assertTrue(track.beats.zipWithNext().all { (a, c) -> abs(c - a - 0.5) < 0.03 })
    }

    @Test fun the_mel_bands_are_triangles_inside_30_hz_to_11_khz() {
        val f = BeatThis.fbank
        // Bin 0 is 0 Hz, below 30 Hz: no band; the top bin is 11,025 Hz, above 11 kHz: none either.
        assertTrue(f[0].all { it == 0.0 })
        assertTrue(f[f.size - 1].all { it == 0.0 })
        // Every band has a peak at most 1.
        for (m in 0 until BeatThis.MELS) assertTrue(f.maxOf { it[m] } in 0.3..1.0)
    }

    @Test fun a_piece_shorter_than_a_chunk_still_gets_its_beats() {
        val s = BeatThis.spect(signal.copyOf(BeatThis.RATE * 8))
        val (b, d) = BeatThis.logits(s, net)
        assertTrue(b.none { it == -1000f } && d.none { it == -1000f })
        val track = BeatThis.track(b, d)
        assertTrue("${track.beats}", track.beats.size in 15..17)
    }

    @Test fun the_tempo_of_even_beats_is_their_period_and_uneven_ones_are_free_time() {
        // 96 BPM with ±10 ms of jitter: least squares lands on it.
        val even = List(24) { it * 0.625 + if (it % 2 == 0) 0.01 else -0.01 }
        val t = Tempo.fromBeats(even)
        assertEquals(96.0, t.best!!, 0.3)
        assertEquals("beats", t.from)
        assertEquals(listOf("pulse", "half", "double"), t.candidates.map { it.why })
        // A gap where a beat was missed still counts the beats around it.
        assertEquals(96.0, Tempo.fromBeats(even.filterIndexed { i, _ -> i != 10 }).best!!, 0.3)
        // Scattered beats: free time.
        val scattered = listOf(0.0, 0.4, 1.3, 1.6, 2.9, 3.1, 4.4, 5.6, 5.9, 7.2)
        assertTrue(Tempo.fromBeats(scattered).free)
        assertTrue(Tempo.fromBeats(listOf(0.0, 0.5, 1.0)).free)
    }

    /** The app's own piano (Iowa MIS samples). */
    private val piano by lazy {
        val samples = File("../app/src/main/assets/piano").listFiles()!!.mapNotNull { f ->
            val m = Regex("mf-(\\d+)\\.wav").matchEntire(f.name)?.groupValues?.get(1)?.toInt() ?: return@mapNotNull null
            val b = f.readBytes()
            val pcm = java.nio.ByteBuffer.wrap(b, 44, b.size - 44).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            m to FloatArray(pcm.remaining()) { pcm.get(it) / 32768f }
        }.toMap()
        com.betterappmafia.apophenia.core.sound.PianoSampler(listOf(com.betterappmafia.apophenia.core.sound.PianoSampler.Layer(80, samples)))
    }

    /**
     * Not the evidence for the change: this synthetic piano attacks cleanly enough that the onset estimate gets it too
     * (192, double). The evidence is 42 real MUSDB18 excerpts with the drums taken out (docs/STATUS.md, 0.9.19),
     * which can't be kept in the repo.
     */
    @Test fun a_legato_piano_take_with_no_drums_gets_its_tempo() {
        // Broken chords C Am F G in eighths at 96 BPM, each note held into the next (no drums, soft attacks), 20 s.
        val rate = 24_000
        val chords = listOf(listOf(48, 55, 60, 64), listOf(45, 52, 57, 60), listOf(41, 48, 53, 57), listOf(43, 50, 55, 59))
        val eighth = 60.0 / 96 / 2
        val notes = (0 until 64).map { i ->
            val c = chords[(i / 8) % 4]; val m = c[listOf(0, 1, 2, 3, 2, 1, 2, 3)[i % 8]]
            com.betterappmafia.apophenia.core.sound.PlayNote(i * eighth, i * eighth + 3 * eighth, m, 80)
        }
        val x = piano.render(notes, rate, 64 * eighth + 0.5)
        val old = TakeAnalysis.analyse(x, rate).tempo
        val found = BeatThis.track(Resample.to(x, rate, BeatThis.RATE), net)
        val now = TakeAnalysis.analyse(x, rate, found = found).tempo
        println("legato piano at 96: onsets ${old.best} (${old.clarity}), beats ${now.best} (${now.clarity}), ${found.beats.size} beats")
        assertEquals("beats", now.from)
        val bpm = now.best!!
        assertTrue("read $bpm", listOf(96.0, 48.0, 192.0).any { kotlin.math.abs(bpm / it - 1) < 0.04 })
    }

    init { require(File("../app/src/main/assets/models/beat-this/beat_this_small0.onnx").isFile) }
}
