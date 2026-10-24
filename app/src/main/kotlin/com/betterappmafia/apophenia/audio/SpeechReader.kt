package com.betterappmafia.apophenia.audio

import android.content.Context
import com.betterappmafia.apophenia.Breadcrumbs
import com.betterappmafia.apophenia.core.analysis.Moonshine
import com.betterappmafia.apophenia.core.analysis.Speech
import com.betterappmafia.apophenia.core.analysis.SpeechTokenizer
import com.betterappmafia.apophenia.core.analysis.TranscriptText
import com.betterappmafia.apophenia.data.ApopheniaDatabase
import com.betterappmafia.apophenia.data.TakeEntity
import com.betterappmafia.apophenia.data.TakeSpeechEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Speech → text (plan §10.4, C4 AI ▾): Moonshine Base on ONNX Runtime, one take at a time on a background
 * worker. The take is read at 16 kHz mono, cut into pieces at its pauses, and each piece read into a
 * time-stamped line (`core/analysis/Speech.kt`). The lines are stored with the take, which is never changed.
 */
class SpeechReader(private val context: Context, private val db: ApopheniaDatabase, private val audioDir: File) {
    /** A job in the AI mode's JOBS list: [done] of [total] pieces, or why it failed. */
    data class Job(val takeId: Long, val scrapId: Long, val title: String, val audioPath: String, val done: Int = 0, val total: Int = 0, val error: String? = null) {
        val percent: Int get() = if (total == 0) 0 else done * 100 / total
    }

    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs: StateFlow<List<Job>> = _jobs.asStateFlow()
    private val queue = Channel<Job>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private var tokenizer: SpeechTokenizer? = null

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
        val takeId = job.takeId
        fun progress(done: Int, total: Int) = _jobs.update { l -> l.map { if (it.takeId == takeId) it.copy(done = done, total = total) else it } }
        try {
            val (x, rate) = Wav.readMono(File(audioDir, job.audioPath), Speech.RATE, MAX_SECONDS) ?: error("couldn't read the take")
            check(rate == Speech.RATE) { "the take's rate ($rate Hz) isn't a multiple of 16 kHz" }
            val tok = tokenizer ?: SpeechTokenizer.fromJson(context.assets.open("$ASSET_DIR/tokenizer.json").use { it.readBytes().toString(Charsets.UTF_8) }).also { tokenizer = it }
            val lines = Moonshine(asset("encoder_model.onnx"), asset("decoder_model_merged.onnx"), THREADS).use { m ->
                m.transcribe(x, tok) { done, total -> progress(done, total) }
            }
            db.speech().put(TakeSpeechEntity(takeId, Speech.MODEL_ID, System.currentTimeMillis(), TranscriptText.encode(Speech.MODEL_ID, lines)))
            Breadcrumbs.add("read speech take $takeId: ${lines.size} lines")
            dismiss(takeId)
        } catch (t: Throwable) {
            Breadcrumbs.add("speech failed take $takeId: $t")
            _jobs.update { l -> l.map { if (it.takeId == takeId) it.copy(error = t.message ?: t.javaClass.simpleName) else it } }
        } finally {
            AiWork.end(context)
        }
    }

    private fun asset(name: String) = context.assets.open("$ASSET_DIR/$name").use { it.readBytes() }

    companion object {
        const val ASSET_DIR = "models/moonshine-base"
        /** [Claude] The first 10 minutes of a take. */
        const val MAX_SECONDS = 600
        const val THREADS = 4
    }
}
