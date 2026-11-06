package com.betterappmafia.apophenia.core.learn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EarMasteryTest {
    private val thirds = EarPractice.withDirection(Ear.drill("iv-6-7")!!, EarDirection.MELODIC)
    private fun answers(item: String, rights: String, from: Long = 0) = rights.mapIndexed { i, c -> EarAnswerAt(item, c == '1', from + i) }

    @Test fun an_item_is_its_drill_and_answer_and_a_mixed_answer_keeps_its_own_drill() {
        assertEquals("focus:MELODIC:iv-6-7|m6", EarMastery.item(thirds, "m6"))
        assertEquals("iv-thirds|m3", EarMastery.item(Ear.mixed("Intervals"), "iv-thirds|m3"))
    }

    @Test fun only_the_latest_twenty_answers_count() {
        val item = EarMastery.item(thirds, "m6")
        val s = EarMastery.stats(answers(item, "0".repeat(30) + "1".repeat(20)))[item]!!
        assertEquals(20, s.answered); assertEquals(20, s.right)
        assertTrue(EarMastery.sure(s), "old misses are forgotten once twenty newer answers are right")
    }

    @Test fun weakest_is_the_drills_own_items_missed_most_not_its_whole_list() {
        val m6 = EarMastery.item(thirds, "m6"); val M6 = EarMastery.item(thirds, "M6"); val m7 = EarMastery.item(thirds, "m7"); val M7 = EarMastery.item(thirds, "M7")
        val stats = EarMastery.stats(answers(m6, "0101010101") + answers(M6, "1111111111") + answers(m7, "0011111111") + answers(M7, "01"))
        assertEquals(listOf(m6, m7), EarMastery.weakest(thirds, stats), "M6 is sure; M7 has too few answers to judge")
        assertFalse(EarMastery.passed(thirds, stats))
        assertEquals(1, EarMastery.passedCount(thirds, stats))
    }

    @Test fun a_quick_check_asks_passed_items_longest_unchecked_first_and_plays_as_a_pick() {
        val a = EarMastery.item(thirds, "M6"); val b = "iv-thirds|m3"; val weak = EarMastery.item(thirds, "m6")
        val stats = EarMastery.stats(answers(a, "1".repeat(10), from = 1000) + answers(b, "1".repeat(10), from = 0) + answers(weak, "0000"))
        assertEquals(listOf(b, a), EarMastery.review(stats))
        val drill = EarMastery.drillOf(EarMastery.review(stats))
        assertEquals(listOf(b, a), drill.answers)
        assertTrue(EarMastery.askable(b)); assertFalse(EarMastery.askable("iv-sing|m3"), "sung drills need the mic")
    }
}
