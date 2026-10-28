package com.betterappmafia.apophenia.audio

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.betterappmafia.apophenia.core.analysis.BeatThis
import com.betterappmafia.apophenia.core.analysis.Lowpass
import com.betterappmafia.apophenia.core.analysis.Resample
import java.nio.FloatBuffer

/**
 * Beat This! (small0, 10.5 MB, MIT) on ONNX Runtime for take analysis: the take low-passed and brought to the
 * model's 22,050 Hz, then `core/analysis/BeatThis.kt`. The session is made on first use and kept; analysis runs one
 * take at a time on its own worker, which is the only caller.
 */
class BeatTracker(private val context: Context) {
    private val env by lazy { OrtEnvironment.getEnvironment() }
    private val session: OrtSession by lazy {
        val bytes = context.assets.open(ASSET).use { it.readBytes() }
        env.createSession(bytes, OrtSession.SessionOptions().apply { setIntraOpNumThreads(THREADS) }).also { s ->
            require(s.inputNames == setOf("spect") && s.outputNames == setOf("beat", "downbeat")) { "unexpected beat model: ${s.inputNames} → ${s.outputNames}" }
        }
    }

    private val net = BeatThis.Net { spect, frames ->
        OnnxTensor.createTensor(env, FloatBuffer.wrap(spect), longArrayOf(1, frames.toLong(), BeatThis.MELS.toLong())).use { x ->
            session.run(mapOf("spect" to x)).use { r ->
                val b = (r.get(0) as OnnxTensor).floatBuffer.let { fb -> FloatArray(fb.remaining()).also { fb.get(it) } }
                val d = (r.get(1) as OnnxTensor).floatBuffer.let { fb -> FloatArray(fb.remaining()).also { fb.get(it) } }
                b to d
            }
        }
    }

    /** The beats and downbeats of mono audio [x] at [rate]. */
    fun track(x: FloatArray, rate: Int): BeatThis.Track {
        // [Claude] 10.5 kHz low-pass before the cheap resampler, so nothing folds into the model's top band (11 kHz).
        val y = if (rate > BeatThis.RATE) Resample.to(Lowpass.apply(x, rate, 10_500.0), rate, BeatThis.RATE) else Resample.to(x, rate, BeatThis.RATE)
        return BeatThis.track(y, net)
    }

    companion object {
        const val ASSET = "models/beat-this/beat_this_small0.onnx"
        /** [Claude] As the stems: the Snapdragon 8 Gen 1 has four performance cores. */
        const val THREADS = 4
    }
}
