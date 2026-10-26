package com.betterappmafia.apophenia.core.tools

/**
 * An exercise's tempo ladder (plan §7 Exercises; the shape is [Claude]'s, proposed 25 Oct 2026): start at [start] BPM,
 * go up [step] after each clean run, aim for [goal]. [current] is the tempo you're on; [best] the fastest clean run.
 * A run that wasn't clean leaves the ladder where it is; [down] goes back a step.
 */
data class TempoLadder(val start: Int = 60, val goal: Int = 120, val step: Int = 4, val current: Int = start, val best: Int? = null) {
    init {
        require(start in MIN..MAX && goal in MIN..MAX && current in MIN..MAX) { "tempos are $MIN–$MAX BPM" }
        require(goal >= start) { "the goal is at or above the start" }
        require(step in 1..MAX_STEP) { "the step is 1–$MAX_STEP BPM" }
    }

    /** A clean run at [current]: the best moves up to it, and the ladder goes up a step, not past the goal. */
    fun clean() = copy(best = maxOf(best ?: 0, current), current = if (current >= goal) current else minOf(current + step, goal))

    /** Back a step, not below the slowest tempo. */
    fun down() = copy(current = maxOf(current - step, MIN))

    /** True once a clean run has been at the goal or faster. */
    val reached get() = (best ?: 0) >= goal

    /** A new start; the goal moves up with it if it would be below, and you begin again from it. */
    fun withStart(bpm: Int) = bpm.coerceIn(MIN, MAX).let { s -> copy(start = s, goal = maxOf(goal, s), current = s) }

    /** A new goal, not below the start. */
    fun withGoal(bpm: Int) = copy(goal = bpm.coerceIn(start, MAX))

    fun withStep(bpm: Int) = copy(step = bpm.coerceIn(1, MAX_STEP))

    companion object {
        /** The metronome's range. */
        const val MIN = 20
        const val MAX = 400
        const val MAX_STEP = 40
    }
}
