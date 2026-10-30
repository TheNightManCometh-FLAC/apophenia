package com.betterappmafia.apophenia.core.analysis

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/** Shared JVM/Android ONNX boundary. Called and closed on the transcription worker, once per job. */
class GuitarModel(model: ByteArray, threads: Int = 2) : GuitarPitch.Net, AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session = OrtSession.SessionOptions().use { options ->
        options.setIntraOpNumThreads(threads)
        options.setInterOpNumThreads(1)
        env.createSession(model, options).also { s ->
            try {
                require(s.inputNames == setOf("mel") && s.outputNames == GuitarPitch.OUTPUTS.toSet()) { "unexpected guitar model signature" }
            } catch (t: Throwable) { s.close(); throw t }
        }
    }

    override fun run(mel: FloatArray): List<FloatArray> {
        require(mel.size == GuitarPitch.FRAMES * GuitarPitch.MELS)
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(mel), longArrayOf(1, 1, GuitarPitch.FRAMES.toLong(), GuitarPitch.MELS.toLong())).use { input ->
            session.run(mapOf("mel" to input)).use { result ->
                GuitarPitch.OUTPUTS.map { name ->
                    val value = result.get(name).orElseThrow { IllegalStateException("missing guitar output $name") } as OnnxTensor
                    val buffer = value.floatBuffer
                    FloatArray(buffer.remaining()).also { buffer.get(it) }
                }
            }
        }
    }

    override fun close() = session.close()
}
