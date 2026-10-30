package com.betterappmafia.apophenia.ui

import com.betterappmafia.apophenia.core.learn.*
import org.junit.Assert.*
import org.junit.Test

class EarRunTest {
    private val drill = EarDrill("repeated-third", "Intervals", "Third", EarKind.INTERVAL, listOf("M3"), direction = EarDirection.UP)
    private val settings = EarSettings(direction = EarDirection.UP, randomKey = false)

    @Test fun equal_musical_questions_are_distinct_attempts_with_fresh_input_and_audio_keys() {
        val run = EarRun(drill, EarMode.PRACTICE, 25, settings, 6)
        val original = run.question
        assertEquals(0, run.questionNumber)
        assertNull(run.picked)
        run.picked = "M3"; run.tapped = setOf(64); run.frets = List(6) { 3 }
        run.progress.answer(true)
        run.next(6)
        assertEquals("one possible musical question on a fixed root", original, run.question)
        assertEquals("audio/reply state gets a fresh key even when questions compare equal", 1, run.questionNumber)
        assertNull(run.picked); assertTrue(run.tapped.isEmpty()); assertTrue(run.frets.all { it == null })
        assertEquals("the session continues instead of resetting its score", 1, run.progress.asked)
    }

    @Test fun a_completed_test_cannot_start_an_extra_question() {
        val run = EarRun(drill, EarMode.TEST, 1, settings, 6)
        val original = run.question
        run.progress.answer(false); run.picked = "m3"
        run.next(6)
        assertEquals(0, run.questionNumber)
        assertEquals(original, run.question)
        assertEquals("m3", run.picked)
        assertEquals(1, run.progress.asked)
        assertTrue(run.progress.complete)
    }
}
