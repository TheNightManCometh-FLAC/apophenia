package com.betterappmafia.apophenia.core.learn

import kotlin.random.Random
import kotlin.test.*

class EarPracticeTest {
    @Test fun focused_intervals_keep_the_selected_presentation_and_their_own_ids() {
        val ids = mutableSetOf<String>()
        for (direction in EarPractice.directions) for (drill in EarPractice.intervals(direction)) {
            assertTrue(ids.add(drill.id), "distinct score identity")
            assertEquals(drill, Ear.drill(drill.id))
            val questions = (0 until 80).map { Ear.question(drill, EarSettings(direction = EarDirection.MIXED), Random(it)) }
            when (direction) {
                EarDirection.MIXED -> assertEquals(setOf(EarDirection.UP, EarDirection.DOWN, EarDirection.TOGETHER), questions.map { it.direction }.toSet())
                EarDirection.MELODIC -> assertEquals(setOf(EarDirection.UP, EarDirection.DOWN), questions.map { it.direction }.toSet(), "melodic is up or down, never together")
                else -> assertEquals(setOf(direction), questions.map { it.direction }.toSet())
            }
            val intervals = questions.map { Ear.source(drill, it.answer).second }.toSet()
            assertTrue(intervals.all { it in drill.answers })
            if (drill.id.endsWith("iv-thirds")) assertEquals(setOf("m3", "M3"), intervals)
        }
        assertEquals(listOf(EarDirection.MELODIC, EarDirection.TOGETHER), EarPractice.directions, "each type is offered melodic or harmonic")
        assertNotNull(Ear.drill("focus:UP:iv-thirds"), "a score kept from the earlier ascending drill still resolves")
        assertNull(Ear.drill("focus:INVALID:iv-thirds"))
        assertNull(Ear.drill("focus:UP:ch-triads"))
    }

    @Test fun custom_interval_picks_keep_their_presentation_when_mixed() {
        val options = Ear.pickable("Intervals", EarDirection.TOGETHER).flatMap { it.second }
        val chosen = listOf("m3", "M3").map { interval -> options.first { it.substringAfter('|') == interval } }
        val drill = Ear.pick(chosen)
        val restored = assertNotNull(Ear.drill(drill.id))
        assertEquals(EarDirection.TOGETHER, restored.direction)
        assertTrue(restored.name.contains("Harmonic"), "the score's name identifies its presentation")
        val asked = (0 until 80).map { Ear.question(restored, EarSettings(direction = EarDirection.DOWN), Random(it)) }
        assertEquals(setOf(EarDirection.TOGETHER), asked.map { it.direction }.toSet())
        assertEquals(chosen.toSet(), asked.map { it.answer }.toSet())
    }

    @Test fun tests_default_to_25_stop_exactly_and_partial_tests_do_not_make_records() {
        assertEquals(25, Ear.TEST_LENGTH)
        val progress = EarProgress(EarMode.TEST)
        assertEquals(0.0, progress.percentage)
        assertFalse(progress.complete); assertFalse(progress.recordable)
        repeat(24) { assertTrue(progress.answer(it < 18)) }
        assertFalse(progress.complete); assertFalse(progress.recordable)
        assertTrue(progress.answer(true))
        assertTrue(progress.complete); assertTrue(progress.recordable)
        assertEquals(19, progress.right); assertEquals(25, progress.asked)
        assertEquals(76.0, progress.percentage)
        assertFalse(progress.answer(true)); assertEquals(25, progress.asked)
        for (count in listOf(1, 7, 50, 100)) {
            val test = EarProgress(EarMode.TEST, count)
            repeat(count) { test.answer(true) }
            assertTrue(test.complete); assertEquals(100.0, test.percentage)
        }
        assertFailsWith<IllegalArgumentException> { EarProgress(EarMode.TEST, 0) }
    }

    @Test fun practice_keeps_the_longest_streak_after_a_miss_and_has_no_question_limit() {
        val p = EarProgress(EarMode.PRACTICE, 2)
        assertFalse(p.recordable)
        repeat(4) { p.answer(true) }
        assertEquals(4, p.streak); assertEquals(4, p.best)
        p.answer(false)
        assertEquals(0, p.streak); assertEquals(4, p.best)
        repeat(2) { p.answer(true) }
        assertEquals(2, p.streak); assertEquals(4, p.best)
        assertEquals(7, p.asked); assertTrue(p.recordable); assertFalse(p.complete)
    }

    @Test fun unisons_and_compound_intervals_have_reachable_instrument_answers() {
        val unison = Ear.drill("iv-unison-octave")!!.copy(answers = listOf("P1"))
        val q = Ear.question(unison, EarSettings(), Random(8))
        assertEquals(q.midis[0], q.midis[1])
        assertTrue(Ear.check(q, listOf(q.given())))
        assertFalse(Ear.check(q, listOf(q.given() + 12)))
        assertFalse(Ear.check(q, emptyList()))
        val compound = Ear.drill("iv-compound")!!
        assertTrue(compound.answers.containsAll(listOf("m14", "M14", "P15")), "fourteenths and double octave are offered")
        for (drill in Ear.drills.filter { it.kind == EarKind.INTERVAL }) repeat(80) {
            val question = Ear.question(drill, EarSettings(), Random(it))
            assertTrue(question.midis.all { midi -> midi in 40..76 }, "${drill.id}: ${question.midis}")
        }
    }

    @Test fun every_drill_and_every_custom_source_has_a_real_related_learning_page() {
        val all = Ear.drills + Ear.chapters.map(Ear::mixed) + Ear.mixed(null) + EarPractice.directions.flatMap(EarPractice::intervals) +
            listOf(EarDrill("own-chords", "Your music", "Own chords", EarKind.OWN_CHORD, listOf("maj")),
                EarDrill("own-keys", "Your music", "Own keys", EarKind.OWN_KEY, listOf("C major")),
                EarDrill("own-phrases", "Your music", "Own phrases", EarKind.OWN_PHRASE, listOf("phrase")))
        for (drill in all) {
            val pages = EarLearning.pages(drill)
            assertTrue(pages.isNotEmpty(), drill.id)
            for (id in pages) assertNotNull(Course.page(id), "${drill.id} links to $id")
            for (answer in drill.answers) assertTrue(EarLearning.pages(drill, answer).isNotEmpty(), "${drill.id}|$answer")
        }
        assertEquals(listOf("iv-melodic", "iv-major-minor"), EarLearning.pages(Ear.drill("iv-thirds")!!))
        assertEquals(listOf("cadences"), EarLearning.pages(Ear.drill("hm-cadence")!!))
        assertEquals(listOf("swing"), EarLearning.pages(Ear.drill("rh-swing")!!))
    }
}

class EarNoRepeatTest {
    @Test fun the_same_notes_never_play_twice_running_and_repeats_are_rare() {
        for (id in listOf("iv-seconds", "focus:MELODIC:iv-thirds", "tr-majmin", "iv-simple", "hm-modal")) {
            val drill = assertNotNull(Ear.drill(id))
            val r = Random(11)
            var q = Ear.question(drill, EarSettings(), r)
            var sameAnswer = 0
            repeat(3000) {
                val next = Ear.question(drill, EarSettings(), r, previous = q)
                assertFalse(next.answer == q.answer && next.root.pitchClass == q.root.pitchClass, "$id: the same question twice running")
                if (next.answer == q.answer) sameAnswer++
                q = next
            }
            assertTrue(sameAnswer < 3000 * 0.25, "$id: the last answer came straight back $sameAnswer times in 3000")
        }
    }

    @Test fun with_the_key_fixed_the_answer_always_changes() {
        val drill = assertNotNull(Ear.drill("iv-sixths"))
        val r = Random(5)
        var q = Ear.question(drill, EarSettings(randomKey = false), r)
        repeat(500) {
            val next = Ear.question(drill, EarSettings(randomKey = false), r, previous = q)
            assertNotEquals(q.answer, next.answer)
            q = next
        }
    }
}
