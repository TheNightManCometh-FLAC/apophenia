package com.betterappmafia.apophenia.audio

import android.content.Context
import com.betterappmafia.apophenia.Breadcrumbs
import com.betterappmafia.apophenia.core.analysis.BasicPitch
import com.betterappmafia.apophenia.core.analysis.Lowpass
import com.betterappmafia.apophenia.core.analysis.TranscriptionText
import com.betterappmafia.apophenia.data.ApopheniaDatabase
import com.betterappmafia.apophenia.data.TakeEntity
import com.betterappmafia.apophenia.data.TakeTranscriptionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Notes → MIDI (plan §10.3, C4 AI ▾): Spotify Basic Pitch on LiteRT, one take at a time on a background
 * worker. The take is read at 24 kHz, low-passed at 10 kHz and resampled to the model's 22,050 Hz; each
 * two-second window goes through the model, and the core turns the joined output into notes
 * (`core/analysis/BasicPitch.kt`, ported from the reference). The result is stored with the take, which
 * is never changed.
 */
class Transcriber(private val context: Context, private val db: ApopheniaDatabase, private val audioDir: File) {
    /** A job in the AI mode's JOBS list: [done] of [total] windows, or why it failed. */
    data class Job(val takeId: Long, val scrapId: Long, val title: String, val audioPath: String, val done: Int = 0, val total: Int = 0, val error: String? = null) {
        val percent: Int get() = if (total == 0) 0 else done * 100 / total
    }

    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs: StateFlow<List<Job>> = _jobs.asStateFlow()
    private val queue = Channel<Job>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    init {
        scope.launch { for (job in queue) run(job) }
    }

    fun start(take: TakeEntity, title: String) {
        if (_jobs.value.any { it.takeId == take.id && it.error == null }) return
        val job = Job(take.id, take.scrapId, title, take.audioPath)
        _jobs.update { list -> list.filter { it.takeId != take.id } + job }
        AiWork.begin(context)
        queue.trySend(job)
    }

    fun dismiss(takeId: Long) = _jobs.update { list -> list.filter { it.takeId != takeId } }

    private suspend fun run(job: Job) {
        val path = job.audioPath
        val takeId = job.takeId
        fun progress(done: Int, total: Int) = _jobs.update { l -> l.map { if (it.takeId == takeId) it.copy(done = done, total = total) else it } }
        try {
            val (x24, rate) = Wav.readMono(File(audioDir, path), 24000, MAX_SECONDS) ?: error("couldn't read the take")
            val filtered = if (rate > BasicPitch.SAMPLE_RATE) Lowpass.apply(x24, rate, 10_000.0) else x24
            val audio = resample(filtered, rate, BasicPitch.SAMPLE_RATE)
            val windows = BasicPitch.windows(audio)
            progress(0, windows.size)
            val notes = ArrayList<Array<FloatArray>>(); val onsets = ArrayList<Array<FloatArray>>(); val contours = ArrayList<Array<FloatArray>>()
            Model(context).use { m ->
                windows.forEachIndexed { i, w ->
                    val out = m.run(w)
                    notes += out.getValue("note"); onsets += out.getValue("onset"); contours += out.getValue("contour")
                    progress(i + 1, windows.size)
                }
            }
            val frames = BasicPitch.unwrap(notes, audio.size)
            val events = BasicPitch.notes(frames, BasicPitch.unwrap(onsets, audio.size))
            val timed = BasicPitch.timed(BasicPitch.withBends(BasicPitch.unwrap(contours, audio.size), events))
            db.transcriptions().put(TakeTranscriptionEntity(takeId, MODEL_ID, System.currentTimeMillis(), TranscriptionText.encode(MODEL_ID, timed)))
            Breadcrumbs.add("transcribed take $takeId: ${timed.size} notes from ${windows.size} windows")
            dismiss(takeId)
        } catch (t: Throwable) {
            Breadcrumbs.add("transcription failed take $takeId: $t")
            _jobs.update { l -> l.map { if (it.takeId == takeId) it.copy(error = t.message ?: t.javaClass.simpleName) else it } }
        } finally {
            AiWork.end(context)
        }
    }

    private fun resample(x: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to) return x
        val out = FloatArray((x.size.toLong() * to / from).toInt() + 8)
        var n = 0
        Resampler(from, to).process(x, x.size) { buf, count -> val c = minOf(count, out.size - n); System.arraycopy(buf, 0, out, n, c); n += c }
        return out.copyOf(n)
    }

    /** The model on LiteRT, loaded once per job from the app's assets. */
    private class Model(context: Context) : AutoCloseable {
        private val interpreter: Interpreter
        private val key: String
        private val input: ByteBuffer = ByteBuffer.allocateDirect(BasicPitch.AUDIO_N_SAMPLES * 4).order(ByteOrder.nativeOrder())
        private val outputs = HashMap<String, Pair<ByteBuffer, IntArray>>()

        init {
            val bytes = context.assets.open(ASSET).use { it.readBytes() }
            val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply { put(bytes); rewind() }
            interpreter = Interpreter(buf, Interpreter.Options().setNumThreads(4))
            key = interpreter.signatureKeys.firstOrNull() ?: error("the model has no signature")
            val inName = interpreter.getSignatureInputs(key).single()
            val inTensor = interpreter.getInputTensorFromSignature(inName, key)
            require(inTensor.numElements() == BasicPitch.AUDIO_N_SAMPLES) { "unexpected model input ${inTensor.shape().joinToString("×")}" }
            for (name in interpreter.getSignatureOutputs(key)) {
                val shape = interpreter.getOutputTensorFromSignature(name, key).shape()
                outputs[name] = ByteBuffer.allocateDirect(shape.fold(1) { a, b -> a * b } * 4).order(ByteOrder.nativeOrder()) to shape
            }
            require(outputs.keys.containsAll(listOf("note", "onset", "contour"))) { "unexpected model outputs ${outputs.keys}" }
        }

        /** One window: each output as frames × bins. */
        fun run(window: FloatArray): Map<String, Array<FloatArray>> {
            input.rewind(); input.asFloatBuffer().put(window)
            outputs.values.forEach { it.first.rewind() }
            interpreter.runSignature(mapOf(interpreter.getSignatureInputs(key).single() to input), outputs.mapValues { it.value.first }, key)
            return outputs.mapValues { (_, v) ->
                val (buf, shape) = v
                buf.rewind()
                val fb = buf.asFloatBuffer()
                val frames = shape[shape.size - 2]; val bins = shape[shape.size - 1]
                Array(frames) { FloatArray(bins).also { row -> fb.get(row) } }
            }
        }

        override fun close() = interpreter.close()
    }

    companion object {
        const val MODEL_ID = "basic-pitch-icassp2022"
        const val ASSET = "models/basic-pitch-icassp2022.tflite"
        /** [Claude] Like analysis, the first 5 minutes of a file. */
        const val MAX_SECONDS = 300
    }
}
