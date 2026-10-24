package com.betterappmafia.apophenia.core.analysis

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * The bundled Spleeter models themselves, on the desktop ONNX Runtime (the same Java API the app uses on
 * the phone), over a synthetic four-part song. It checks the files load and take the documented shapes,
 * that the stems still add up to the mix with the real masks, and how each part was shared out.
 */
class SpleeterModelTest {
    private val dir = File("../app/src/main/assets/models/spleeter-4stems")

    private fun parts(seconds: Int): Map<String, FloatArray> {
        val n = seconds * Spleeter.RATE
        val rate = Spleeter.RATE.toDouble()
        val r = Random(7)
        val beat = 0.5          // 120 BPM
        val drums = FloatArray(n) { i ->
            val t = i / rate
            val b = (t % beat); val half = (t % (beat / 2))
            val kick = if ((t / beat).toInt() % 2 == 0) 0.9 * sin(2 * PI * (50 + 80 * exp(-b * 30)) * b) * exp(-b * 8) else 0.0
            val snare = if ((t / beat).toInt() % 2 == 1) 0.5 * (r.nextDouble() - 0.5) * 2 * exp(-b * 18) else 0.0
            val hat = 0.15 * (r.nextDouble() - 0.5) * 2 * exp(-half * 60)
            (kick + snare + hat).toFloat()
        }
        val bassNotes = doubleArrayOf(55.0, 55.0, 73.42, 82.41)
        val bass = FloatArray(n) { i ->
            val t = i / rate
            val f = bassNotes[((t / 1.0).toInt()) % 4]
            var v = 0.0
            for (h in 1..8) v += sin(2 * PI * f * h * t) / h
            (0.35 * v * (0.6 + 0.4 * exp(-(t % 1.0) * 3))).toFloat()
        }
        val chord = doubleArrayOf(220.0, 277.18, 329.63, 440.0)
        val other = FloatArray(n) { i ->
            val t = i / rate
            var v = 0.0
            for (f in chord) for (h in 1..4) v += sin(2 * PI * f * h * t) / (h * h)
            (0.08 * v).toFloat()
        }
        // A sung "ah": a 220–330 Hz line with vibrato, harmonics weighted by two formants.
        val melody = doubleArrayOf(293.66, 329.63, 392.0, 329.63)
        var phase = 0.0
        val vocals = FloatArray(n) { i ->
            val t = i / rate
            val f0 = melody[((t / 2.0).toInt()) % 4] * (1 + 0.012 * sin(2 * PI * 5.5 * t))
            phase += 2 * PI * f0 / rate
            var v = 0.0
            for (h in 1..20) {
                val hz = f0 * h
                val g = exp(-((hz - 700) / 180).let { it * it }) + 0.6 * exp(-((hz - 1200) / 220).let { it * it }) + 0.1
                v += g * sin(phase * h)
            }
            (0.12 * v).toFloat()
        }
        return mapOf("vocals" to vocals, "drums" to drums, "bass" to bass, "other" to other)
    }

    @Test fun the_bundled_models_separate_a_synthetic_song() {
        val files = Spleeter.STEMS.map { File(dir, "$it.fp16.onnx") }
        files.forEach { assertTrue("missing $it", it.isFile) }
        val env = OrtEnvironment.getEnvironment()
        val sessions = files.map { env.createSession(it.path, OrtSession.SessionOptions()) }
        try {
            for (s in sessions) {
                assertTrue(s.inputNames.toString(), s.inputNames == setOf("x"))
                assertTrue(s.outputNames.toString(), s.outputNames == setOf("y"))
            }
            val p = parts(16)
            val n = p.getValue("vocals").size
            // Mono parts panned a little apart so the channels differ.
            val pan = mapOf("vocals" to 0.5f, "drums" to 0.45f, "bass" to 0.5f, "other" to 0.65f)
            val l = FloatArray(n) { i -> p.entries.sumOf { (k, v) -> (v[i] * (1 - pan.getValue(k))).toDouble() }.toFloat() }
            val r = FloatArray(n) { i -> p.entries.sumOf { (k, v) -> (v[i] * pan.getValue(k)).toDouble() }.toFloat() }
            val outL = Array(4) { FloatArray(n) }; val outR = Array(4) { FloatArray(n) }; val at = IntArray(4)
            val net = Spleeter.Net { m ->
                OnnxTensor.createTensor(env, FloatBuffer.wrap(m), longArrayOf(2, 1, Spleeter.FRAMES.toLong(), Spleeter.NET_BINS.toLong())).use { x ->
                    sessions.map { s ->
                        s.run(mapOf("x" to x)).use { res ->
                            val fb = (res.get(0) as OnnxTensor).floatBuffer
                            FloatArray(fb.remaining()).also { fb.get(it) }
                        }
                    }
                }
            }
            val started = System.nanoTime()
            Spleeter.separate(n.toLong(), { start, a, b, off, count ->
                System.arraycopy(l, start.toInt(), a, off, count); System.arraycopy(r, start.toInt(), b, off, count)
            }, net, { s, a, b, count ->
                System.arraycopy(a, 0, outL[s], at[s], count); System.arraycopy(b, 0, outR[s], at[s], count); at[s] += count
            })
            val ms = (System.nanoTime() - started) / 1_000_000
            var worst = 0.0
            for (i in 0 until n) worst = maxOf(worst, abs(outL.sumOf { it[i].toDouble() } - l[i]), abs(outR.sumOf { it[i].toDouble() } - r[i]))
            println("Spleeter on 16 s: $ms ms, sum error $worst")
            assertTrue("the stems don't add up to the mix: $worst", worst < 1e-4)

            // How much of each part's energy each stem holds (projection of the stem's mono sum onto the part).
            val shares = HashMap<Pair<String, String>, Double>()
            for ((k, v) in p) {
                val vv = v.sumOf { it.toDouble() * it }
                for ((s, name) in Spleeter.STEMS.withIndex()) {
                    var dot = 0.0
                    for (i in 0 until n) dot += (outL[s][i] + outR[s][i]) * v[i].toDouble()
                    shares[k to name] = dot / vv
                }
                println("$k → " + Spleeter.STEMS.joinToString("  ") { "$it %.2f".format(shares.getValue(k to it)) })
            }
            // The parts the models are surest about must land mostly in their own stem.
            for (k in listOf("drums", "bass")) {
                val own = shares.getValue(k to k)
                assertTrue("$k kept only $own of itself", Spleeter.STEMS.all { it == k || shares.getValue(k to it) < own })
            }
        } finally {
            sessions.forEach { it.close() }
        }
    }
}
