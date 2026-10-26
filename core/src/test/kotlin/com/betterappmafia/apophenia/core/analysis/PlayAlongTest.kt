package com.betterappmafia.apophenia.core.analysis

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayAlongTest {
    private val beat = 60.0 / PlayAlong.BPM
    private val clicks = List(PlayAlong.CLICKS) { 0.5 + it * beat }

    /** Claps [delay] s after each click with ±[jitter] s of human unevenness; [skip] claps missed. */
    private fun claps(delay: Double, jitter: Double, skip: Set<Int> = emptySet(), seed: Int = 3): List<Double> {
        val r = Random(seed)
        return clicks.indices.filter { it !in skip }.map { clicks[it] + delay + (r.nextDouble() * 2 - 1) * jitter }
    }

    @Test fun bluetooth_delay_is_found_through_uneven_clapping() {
        val r = PlayAlong.measure(clicks, claps(0.240, 0.02))
        assertNull(r.problem, r.toString())
        assertTrue(kotlin.math.abs(r.ms!! - 240) < 12, "ms ${r.ms}")
        assertEquals(PlayAlong.CLICKS - PlayAlong.LEAD_IN, r.used)
    }

    @Test fun wired_delay_and_a_clap_ahead_of_the_click_both_count() {
        val r = PlayAlong.measure(clicks, claps(0.030, 0.04))
        assertNull(r.problem, r.toString())
        assertTrue(kotlin.math.abs(r.ms!! - 30) < 15, "ms ${r.ms}")
    }

    @Test fun missed_claps_and_a_stray_noise_are_left_out() {
        val c = claps(0.180, 0.01, skip = setOf(4, 9, 12)) + (clicks[6] + 0.45)
        val r = PlayAlong.measure(clicks, c)
        assertNull(r.problem, r.toString())
        assertTrue(kotlin.math.abs(r.ms!! - 180) <= 10, "ms ${r.ms} (claps uneven by ±10 ms)")
        assertEquals(PlayAlong.CLICKS - PlayAlong.LEAD_IN - 3, r.used, "claps 4, 9 and 12 are missed and the stray isn't used")
    }

    @Test fun too_few_or_too_uneven_claps_are_refused() {
        assertNotNull(PlayAlong.measure(clicks, claps(0.2, 0.0).take(7)).problem, "4 claps after the lead-in")
        val wild = PlayAlong.measure(clicks, claps(0.2, 0.11, seed = 9))
        assertNotNull(wild.problem, wild.toString())
    }
}
