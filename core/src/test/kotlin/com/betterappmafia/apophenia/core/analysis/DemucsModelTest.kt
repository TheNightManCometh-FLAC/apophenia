package com.betterappmafia.apophenia.core.analysis

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.log10

/**
 * The user-supplied HT-Demucs ONNX file through [Demucs.separate] on the desktop ONNX Runtime (the app's Java API),
 * against demucs 4.0.1's own `apply_model` and `separate.py` normalisation around the same file
 * (`D:/junkdrawer/apo-ref/dm_ref.py`, 40 s of a song, 7 chunks).
 *
 * The weights are never in the repository, so this runs only when given them:
 * `gradlew :core:test -PdemucsModel=<htdemucs-dml.onnx> -PdemucsRef=<dir with in.f32 and stems.f32>`.
 * Without them it is skipped (and says so), never passed.
 */
class DemucsModelTest {
    private fun floats(f: File): FloatArray {
        val b = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(b.remaining()).also { b.get(it) }
    }

    @Test fun `the streamed stems match demucs's own chunking of the same model`() {
        val model = System.getProperty("demucs.model"); val ref = System.getProperty("demucs.ref")
        assumeTrue("needs -PdemucsModel and -PdemucsRef (the weights aren't in the repo)", model != null && ref != null)
        val inter = floats(File(ref, "in.f32"))
        val len = inter.size / 2
        val want = floats(File(ref, "stems.f32"))   // [4][2][len], model order
        val env = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4); setCPUArenaAllocator(false)
            addConfigEntry("optimization.disable_specified_optimizers", "ConstantFolding")
        }
        val got = Array(4) { Array(2) { FloatArray(len) } }
        val at = IntArray(4)
        env.createSession(model, opts).use { session ->
            Demucs.separate(len.toLong(), { start, l, r, off, count ->
                for (i in 0 until count) { l[off + i] = inter[2 * (start.toInt() + i)]; r[off + i] = inter[2 * (start.toInt() + i) + 1] }
            }, { x ->
                OnnxTensor.createTensor(env, FloatBuffer.wrap(x), longArrayOf(1, 2, Demucs.SEGMENT.toLong())).use { t ->
                    session.run(mapOf("mix" to t)).use { res -> val fb = (res.get(0) as OnnxTensor).floatBuffer; FloatArray(fb.remaining()).also { fb.get(it) } }
                }
            }, { s, l, r, count ->
                System.arraycopy(l, 0, got[s][0], at[s], count); System.arraycopy(r, 0, got[s][1], at[s], count); at[s] += count
            })
        }
        for ((m, name) in Demucs.MODEL_STEMS.withIndex()) {
            val s = Spleeter.STEMS.indexOf(name)
            var sig = 0.0; var err = 0.0
            for (c in 0 until 2) for (i in 0 until len) {
                val w = want[(m * 2 + c) * len + i].toDouble(); val d = got[s][c][i] - w
                sig += w * w; err += d * d
            }
            val db = 10 * log10(err / sig)
            println("demucs $name: error ${"%.1f".format(db)} dB against apply_model")
            assertTrue("the $name stem matches demucs's apply_model to better than -80 dB (got ${"%.1f".format(db)} dB)", db < -80)
        }
    }
}
