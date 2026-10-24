package com.betterappmafia.apophenia.audio

import com.betterappmafia.apophenia.Breadcrumbs
import com.betterappmafia.apophenia.core.analysis.AnalysisText
import com.betterappmafia.apophenia.core.analysis.TakeAnalysis
import com.betterappmafia.apophenia.data.ApopheniaDatabase
import com.betterappmafia.apophenia.data.TakeAnalysisEntity
import com.betterappmafia.apophenia.data.ValueSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Take analysis in the background (plan §10.2): one take at a time on a low-priority worker, newest
 * first, never touching playback or saving. Each result is stored with the take; the first analysed
 * take of a scrap offers its tempo and key, which only fill in what nobody has set (the click's grid
 * and the user's entry always win, plan §3.4).
 */
class Analyser(private val db: ApopheniaDatabase, private val audioDir: File) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private var job: Job? = null
    private val _running = MutableStateFlow<Long?>(null)
    /** The take being analysed now, if any. */
    val running: StateFlow<Long?> = _running.asStateFlow()

    /** Analyses everything that needs it; calling it again while it runs does nothing (it picks new takes up). */
    fun kick() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                val next = db.analysis().pending(TakeAnalysis.VERSION, limit = 1).firstOrNull() ?: break
                _running.value = next.id
                val ok = runCatching { analyse(next.id, next.scrapId, File(audioDir, next.audioPath), next.metronomeGrid) }
                if (ok.isFailure) {
                    Breadcrumbs.add("analysis failed take ${next.id}: ${ok.exceptionOrNull()}")
                    // Store the failure so the queue moves on; "Analyse again" retries it.
                    db.analysis().put(TakeAnalysisEntity(next.id, TakeAnalysis.VERSION, System.currentTimeMillis(), ""))
                }
                _running.value = null
            }
        }
    }

    /** Throws away a take's analysis and runs it again. */
    fun again(takeId: Long) = scope.launch {
        db.analysis().put(TakeAnalysisEntity(takeId, 0, 0, ""))
        kick()
    }

    private suspend fun analyse(takeId: Long, scrapId: Long, file: File, gridJson: String?) {
        val clicked = gridJson != null
        // The click's own beats and meter place the chords, from its first downbeat after the count-in.
        val grid = com.betterappmafia.apophenia.core.tools.ClickGrid.fromJson(gridJson)
        val clickBeats = grid?.let { g ->
            val playing = g.frames.indices.filter { it !in g.countIn }
            val first = playing.firstOrNull { it in g.downbeats } ?: playing.firstOrNull()
            first?.let { s -> playing.filter { it >= s }.map { g.frames[it].toDouble() / g.rate } }
        }
        val meter = grid?.meter?.let { runCatching { com.betterappmafia.apophenia.core.rhythm.TimeSignature.parse(it) }.getOrNull() } ?: com.betterappmafia.apophenia.core.rhythm.TimeSignature.of(4, 4)
        val (x, rate) = Wav.readMono(file, TakeAnalysis.RATE, MAX_SECONDS) ?: error("can't read ${file.name}")
        val r = TakeAnalysis.analyse(x, rate, clickBeats = clickBeats, meter = meter)
        db.analysis().put(TakeAnalysisEntity(takeId, TakeAnalysis.VERSION, System.currentTimeMillis(), AnalysisText.encode(r)))
        val s = db.scraps().get(scrapId) ?: return
        // A take recorded with the click already has its tempo from the metronome.
        if (!clicked && s.bpmLow == null && !s.freeTime) r.tempo.best?.let { db.scraps().offerBpm(scrapId, Math.round(it * 10) / 10.0, source = ValueSource.ANALYSIS) }
        if (s.key == null && s.keySource == null) r.key.best?.let { db.scraps().offerKey(scrapId, it.token, ValueSource.ANALYSIS) }
        Breadcrumbs.add("analysed take $takeId: ${r.tempo.best} ${r.key.best} ${r.notes.size} notes")
    }

    companion object {
        /** [Claude] Analysis reads at most the first 5 minutes of a file. */
        const val MAX_SECONDS = 300
    }
}
