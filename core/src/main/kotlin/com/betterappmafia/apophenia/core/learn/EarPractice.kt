package com.betterappmafia.apophenia.core.learn

/** Visible choices before an interval drill starts; each presentation has its own score identity. */
object EarPractice {
    /**
     * Each interval type is offered melodic or harmonic (Christopher, 30 Oct 2026: "melodic seconds… harmonic seconds";
     * "No default is up and down. Both mixed."). Melodic plays up and down at random.
     */
    val directions = listOf(EarDirection.MELODIC, EarDirection.TOGETHER)
    fun title(direction: EarDirection) = when (direction) {
        EarDirection.MELODIC -> "Melodic · up and down"
        EarDirection.UP -> "Melodic · ascending"
        EarDirection.DOWN -> "Melodic · descending"
        EarDirection.TOGETHER -> "Harmonic"
        EarDirection.MIXED -> "Mixed · all presentations"
    }
    fun withDirection(base: EarDrill, direction: EarDirection): EarDrill {
        require(base.kind == EarKind.INTERVAL)
        return base.copy(id = "focus:${direction.name}:${base.id}", name = "${base.name} · ${title(direction)}", direction = direction)
    }
    fun focused(id: String): EarDrill? {
        if (!id.startsWith("focus:")) return null
        val parts = id.split(':', limit = 3)
        if (parts.size != 3) return null
        // Any presentation resolves, so scores kept from the earlier up/down/mixed drills still show their names.
        val direction = EarDirection.entries.firstOrNull { it.name == parts[1] } ?: return null
        val base = Ear.drills.firstOrNull { it.id == parts[2] && it.kind == EarKind.INTERVAL } ?: return null
        return withDirection(base, direction)
    }

    /** The interval types, each a drill offered in every presentation. */
    val types get() = Ear.drills.filter { it.kind == EarKind.INTERVAL }

    /** Focused topics followed by the broader combinations; no locked levels. */
    fun intervals(direction: EarDirection) = Ear.drills.filter { it.kind == EarKind.INTERVAL }.map { withDirection(it, direction) }
}

enum class EarMode { PRACTICE, TEST }

/** First answers count once in the UI. A completed test cannot grow; practice remains open-ended. */
class EarProgress(val mode: EarMode, val target: Int = Ear.TEST_LENGTH) {
    init { require(target > 0) }
    var asked = 0; private set
    var right = 0; private set
    var streak = 0; private set
    var best = 0; private set
    val complete: Boolean get() = mode == EarMode.TEST && asked == target
    val percentage: Double get() = if (asked == 0) 0.0 else right * 100.0 / asked
    val recordable: Boolean get() = asked > 0 && (mode == EarMode.PRACTICE || complete)

    fun answer(correct: Boolean): Boolean {
        if (complete) return false
        asked++
        if (correct) { right++; streak++; best = maxOf(best, streak) } else streak = 0
        return true
    }
}
