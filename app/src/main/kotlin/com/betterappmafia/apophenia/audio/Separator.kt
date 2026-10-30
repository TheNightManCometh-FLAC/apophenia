package com.betterappmafia.apophenia.audio

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.betterappmafia.apophenia.Breadcrumbs
import com.betterappmafia.apophenia.core.analysis.Demucs
import com.betterappmafia.apophenia.core.analysis.Spleeter
import com.betterappmafia.apophenia.data.ApopheniaDatabase
import com.betterappmafia.apophenia.data.ScrapEntity
import com.betterappmafia.apophenia.data.ScrapFormat
import com.betterappmafia.apophenia.data.TakeEntity
import com.betterappmafia.apophenia.data.TakeStemEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.channels.FileChannel

/**
 * Stem separation (plan §10.6, C4 AI ▾ "Separate stems"): Deezer's Spleeter 4 stems on ONNX Runtime, one
 * take at a time on a background worker. The take is resampled to the model's 44.1 kHz stereo into a
 * scratch file, the core runs the STFT, the four models, the masks and the inverse STFT split by split
 * (`core/analysis/Spleeter.kt`), and each stem streams back to 48 kHz into its own WAV. Each stem becomes
 * its own recording in the Library, linked to the take; the take is never changed. With the optional HT-Demucs
 * model added ([DemucsModel]), the same scratch file goes through `core/analysis/Demucs.kt` instead.
 */
class Separator(private val context: Context, private val db: ApopheniaDatabase, private val audioDir: File) {
    /** A job in the AI mode's JOBS list: [done] of [total] parts (about 12 s of audio each), or why it failed. */
    data class Job(val takeId: Long, val scrapId: Long, val title: String, val letter: String, val audioPath: String, val stems: Set<String>, val model: String = MODEL_ID, val done: Int = 0, val total: Int = 0, val error: String? = null) {
        val percent: Int get() = if (total == 0) 0 else done * 100 / total
    }

    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs: StateFlow<List<Job>> = _jobs.asStateFlow()
    private val queue = Channel<Job>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    init {
        scope.launch { for (job in queue) run(job) }
    }

    /** Separates [take]; only the [stems] picked (Christopher, 28 Oct 2026) are saved. All four models still run: each mask needs all four. */
    fun start(take: TakeEntity, title: String, stems: Set<String>, model: String = MODEL_ID) {
        if (stems.isEmpty() || _jobs.value.any { it.takeId == take.id && it.error == null }) return
        val job = Job(take.id, take.scrapId, title, take.letter, take.audioPath, stems, model)
        _jobs.update { list -> list.filter { it.takeId != take.id } + job }
        AiWork.begin(context)
        queue.trySend(job)
    }

    fun dismiss(takeId: Long) = _jobs.update { list -> list.filter { it.takeId != takeId } }

    private suspend fun run(job: Job) {
        val takeId = job.takeId
        fun progress(done: Int, total: Int) = _jobs.update { l -> l.map { if (it.takeId == takeId) it.copy(done = done, total = total) else it } }
        val scratch = File(context.cacheDir, "stems-in.f32")
        val now = System.currentTimeMillis()
        val keep = Spleeter.STEMS.map { it in job.stems }
        val parts = Spleeter.STEMS.map { File(audioDir, "take-$now-$it.wav.separating") }
        try {
            val file = File(audioDir, job.audioPath)
            val info = Wav.info(file) ?: error("couldn't read the take")
            // The take at 44.1 kHz stereo, interleaved little-endian floats.
            var length = 0L
            val rl = Resampler(info.rate, Spleeter.RATE); val rr = Resampler(info.rate, Spleeter.RATE)
            val outL = FloatArray(16384); val outR = FloatArray(16384)
            var nl = 0; var nr = 0
            BufferedOutputStream(FileOutputStream(scratch), 1 shl 16).use { out ->
                val bytes = ByteBuffer.allocate(16384 * 8).order(ByteOrder.LITTLE_ENDIAN)
                Wav.stream(file, info.rate.toLong() * MAX_SECONDS) { l, r, c ->
                    nl = 0; nr = 0
                    rl.process(l, c) { b, k -> System.arraycopy(b, 0, outL, nl, k); nl += k }
                    rr.process(r, c) { b, k -> System.arraycopy(b, 0, outR, nr, k); nr += k }
                    val k = minOf(nl, nr)
                    bytes.clear()
                    for (i in 0 until k) { bytes.putFloat(outL[i]); bytes.putFloat(outR[i]) }
                    out.write(bytes.array(), 0, k * 8)
                    length += k
                }
            }
            if (length == 0L) error("the take is empty")
            val writers = parts.mapIndexed { s, f -> if (keep[s]) WavWriter(f, 48_000, 2) else null }
            val back = Spleeter.STEMS.map { Resampler(Spleeter.RATE, 48_000) to Resampler(Spleeter.RATE, 48_000) }
            val inter = FloatArray(2 * 4096)
            val bl = FloatArray(4096); val br = FloatArray(4096)
            try {
                RandomAccessFile(scratch, "r").use { raf ->
                    val fb: FloatBuffer = raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                    val source = Spleeter.Source { start, l, r, off, count ->
                        var p = (start * 2).toInt()
                        for (i in 0 until count) { l[off + i] = fb.get(p); r[off + i] = fb.get(p + 1); p += 2 }
                    }
                    val sink = Spleeter.Sink { s, l, r, count ->
                        val w = writers[s] ?: return@Sink
                        val (ra, rb) = back[s]
                        var nl = 0; var nr = 0
                        ra.process(l, count) { b, k -> System.arraycopy(b, 0, bl, nl, k); nl += k }
                        rb.process(r, count) { b, k -> System.arraycopy(b, 0, br, nr, k); nr += k }
                        val k = minOf(nl, nr)
                        for (i in 0 until k) { inter[2 * i] = bl[i]; inter[2 * i + 1] = br[i] }
                        w.write(inter, 2 * k)
                    }
                    if (job.model == DEMUCS_ID) DemucsNet(context).use { net -> Demucs.separate(length, source, net, sink) { done, total -> progress(done, total) } }
                    else Models(context).use { models -> Spleeter.separate(length, source, models, sink, wiener = true) { done, total -> progress(done, total) } }
                }
            } finally {
                writers.forEach { w -> runCatching { w?.close() } }
            }
            val source = db.scraps().get(job.scrapId)
            for ((s, name) in Spleeter.STEMS.withIndex()) {
                if (!keep[s]) continue
                val final = File(audioDir, "take-$now-$name.wav")
                if (!parts[s].renameTo(final)) error("couldn't save the $name stem")
                val frames = Wav.info(final)?.frames ?: 0L
                val id = db.scraps().createRecording(
                    ScrapEntity(format = ScrapFormat.RECORDING, title = "${job.title} ${job.letter} · $name", createdAt = now, editedAt = now, bpmLow = source?.bpmLow, bpmHigh = source?.bpmHigh, freeTime = source?.freeTime ?: false, bpmSource = source?.bpmSource, key = source?.key, keySource = source?.keySource),
                    TakeEntity(scrapId = 0, letter = "A", audioPath = final.name, durationFrames = frames, sampleRate = 48_000, createdAt = now),
                    source?.projectId,
                )
                db.tags().tag(id, "stem", now)
                val stemTake = db.takes().takesOf(id).single()
                db.stems().put(TakeStemEntity(takeId, name, stemTake.id, job.model, now))
            }
            Breadcrumbs.add("separated take $takeId with ${job.model}: ${length / Spleeter.RATE} s into ${job.stems.sorted()}")
            dismiss(takeId)
        } catch (t: Throwable) {
            Breadcrumbs.add("separation failed take $takeId: $t")
            parts.forEach { it.delete() }
            _jobs.update { l -> l.map { if (it.takeId == takeId) it.copy(error = t.message ?: t.javaClass.simpleName) else it } }
        } finally {
            scratch.delete()
            AiWork.end(context)
        }
    }

    /** The four stem models on ONNX Runtime, loaded once per job from the app's assets. */
    private class Models(context: Context) : Spleeter.Net, AutoCloseable {
        private val env = OrtEnvironment.getEnvironment()
        private val sessions: List<OrtSession>
        private val input = ByteBuffer.allocateDirect(Spleeter.NET_SIZE * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

        init {
            val opts = OrtSession.SessionOptions().apply { setIntraOpNumThreads(THREADS) }
            sessions = Spleeter.STEMS.map { name ->
                val bytes = context.assets.open("$ASSET_DIR/$name.fp16.onnx").use { it.readBytes() }
                env.createSession(bytes, opts).also { s ->
                    require(s.inputNames == setOf("x") && s.outputNames == setOf("y")) { "unexpected $name model: ${s.inputNames} → ${s.outputNames}" }
                }
            }
        }

        override fun run(magnitudes: FloatArray): List<FloatArray> {
            input.rewind(); input.put(magnitudes); input.rewind()
            return OnnxTensor.createTensor(env, input, longArrayOf(2, 1, Spleeter.FRAMES.toLong(), Spleeter.NET_BINS.toLong())).use { x ->
                sessions.map { s ->
                    s.run(mapOf("x" to x)).use { res ->
                        val fb = (res.get(0) as OnnxTensor).floatBuffer
                        FloatArray(fb.remaining()).also { fb.get(it) }
                    }
                }
            }
        }

        override fun close() = sessions.forEach { it.close() }
    }

    /**
     * HT-Demucs from the file the user added ([DemucsModel]), loaded from storage, never into the Java heap. Constant
     * folding is off: with it on, loading peaks at 2.5–4.5 GB; off, about 1 GB, for 40% more time (this PC, 30 Oct 2026).
     */
    private class DemucsNet(context: Context) : Demucs.Net, AutoCloseable {
        private val env = OrtEnvironment.getEnvironment()
        private val session: OrtSession
        private val input = ByteBuffer.allocateDirect(2 * Demucs.SEGMENT * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

        init {
            val file = DemucsModel(context).file
            require(file.isFile) { "the Demucs model isn't added" }
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(THREADS)
                setCPUArenaAllocator(false)
                addConfigEntry("optimization.disable_specified_optimizers", "ConstantFolding")
            }
            session = env.createSession(file.absolutePath, opts)
            require(session.inputNames == setOf("mix") && session.outputNames == setOf("stems")) { "unexpected Demucs model: ${session.inputNames} → ${session.outputNames}" }
        }

        override fun run(mix: FloatArray): FloatArray {
            input.rewind(); input.put(mix); input.rewind()
            return OnnxTensor.createTensor(env, input, longArrayOf(1, 2, Demucs.SEGMENT.toLong())).use { x ->
                session.run(mapOf("mix" to x)).use { res ->
                    val fb = (res.get(0) as OnnxTensor).floatBuffer
                    FloatArray(fb.remaining()).also { fb.get(it) }
                }
            }
        }

        override fun close() = session.close()
    }

    companion object {
        const val MODEL_ID = "spleeter-4stems"
        const val DEMUCS_ID = "htdemucs"
        const val ASSET_DIR = "models/spleeter-4stems"
        /** [Claude] The first 10 minutes of a file. */
        const val MAX_SECONDS = 600
        /** [Claude] Threads for ONNX Runtime; the Snapdragon 8 Gen 1 has four performance cores. */
        const val THREADS = 4
    }
}
