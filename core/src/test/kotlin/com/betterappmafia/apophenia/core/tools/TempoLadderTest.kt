package com.betterappmafia.apophenia.core.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TempoLadderTest {
    @Test fun clean_runs_climb_a_step_at_a_time_and_stop_at_the_goal() {
        var l = TempoLadder(start = 80, goal = 90, step = 4)
        l = l.clean(); assertEquals(84, l.current); assertEquals(80, l.best)
        l = l.clean(); assertEquals(88, l.current)
        l = l.clean(); assertEquals(90, l.current, "the last step is shortened to land on the goal")
        assertFalse(l.reached)
        l = l.clean(); assertEquals(90, l.current); assertEquals(90, l.best)
        assertTrue(l.reached)
    }

    @Test fun going_down_keeps_the_best_and_stops_at_the_slowest_tempo() {
        val l = TempoLadder(start = 60, goal = 100, step = 8).clean().clean()
        assertEquals(76, l.current)
        assertEquals(68, l.best)
        assertEquals(68, l.down().current)
        assertEquals(68, l.down().best)
        assertEquals(TempoLadder.MIN, TempoLadder(start = 22, goal = 30, step = 8).down().current)
    }

    @Test fun a_new_start_restarts_the_ladder_and_lifts_a_goal_below_it() {
        val l = TempoLadder(start = 60, goal = 100).clean().withStart(110)
        assertEquals(110, l.current)
        assertEquals(110, l.goal)
        assertEquals(110, l.withGoal(90).goal, "the goal can't go below the start")
        assertEquals(TempoLadder.MAX_STEP, l.withStep(99).step)
    }
}
