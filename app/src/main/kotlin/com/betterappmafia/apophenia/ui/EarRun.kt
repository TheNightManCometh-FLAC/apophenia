package com.betterappmafia.apophenia.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.betterappmafia.apophenia.core.learn.*
import com.betterappmafia.apophenia.data.EarResultEntity
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/** Kept on AppModel while a related lesson is open, so returning resumes the same question and score. */
class EarRun(val drill: EarDrill, val mode: EarMode, target: Int, settings: EarSettings, strings: Int) {
    val progress = EarProgress(mode, target)
    val random = Random(System.nanoTime())
    var settings by mutableStateOf(settings)
    val missed = mutableStateMapOf<String, Int>()
    var question by mutableStateOf(Ear.question(drill, settings, random))
    /** Distinct attempts may contain equal musical questions, especially a two-option drill on a fixed root. */
    var questionNumber by mutableStateOf(0)
        private set
    var picked by mutableStateOf<String?>(null)
    var tapped by mutableStateOf(setOf<Int>())
    var frets by mutableStateOf(List<Int?>(strings) { null })
    var counts by mutableStateOf(Triple(0, 0, 0))
    private val mutex = Mutex()
    private var recordId = 0L
    private var savedAnswers = 0

    fun next(strings: Int) {
        if (progress.complete) return
        question = Ear.question(drill, settings, random, if (mode == EarMode.PRACTICE) missed else emptyMap(), question)
        picked = null; tapped = emptySet(); frets = List(strings) { null }
        questionNumber++
    }

    fun save(model: AppModel) {
        if (!progress.recordable) return
        val result = EarResultEntity(drill = drill.id, mode = mode.name.lowercase(), correct = progress.right,
            total = progress.asked, bestStreak = progress.best, finishedAt = System.currentTimeMillis())
        model.app.io.launch {
            mutex.withLock {
                if (result.total <= savedAnswers) return@withLock
                if (recordId == 0L) recordId = model.app.db.earResults().add(result) ?: return@withLock
                else model.app.db.earResults().update(result.copy(id = recordId))
                savedAnswers = result.total
            }
        }
    }
}
