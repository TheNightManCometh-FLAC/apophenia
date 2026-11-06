package com.betterappmafia.apophenia.core.learn

/** One first answer to one item: the item ("drillId|answer"), right or not, and when (ms). */
data class EarAnswerAt(val item: String, val correct: Boolean, val at: Long)

/** How an item has gone lately: of its last [answered] answers (up to [EarMastery.WINDOW]), [right] were right. */
data class EarItemStat(val item: String, val answered: Int, val right: Int, val lastAt: Long) {
    val accuracy: Double get() = if (answered == 0) 0.0 else right.toDouble() / answered
}

/**
 * Per-item scores for the Ear modes (Christopher, 2 Nov 2026): WEAKEST drills "specific things you get wrong a lot in
 * the practice, not the entire list of practices"; QUICK CHECK starts "answering over ones your score says you pass to
 * make sure you remain competent"; PATH keeps the drills in their order of complexity and marks the ones passed.
 *
 * An item is one answer of one drill as it is played: "focus:MELODIC:iv-thirds|m3" (a minor third, melodic) is not
 * "focus:TOGETHER:iv-thirds|m3" (harmonic). [Claude] the numbers: an item counts over its last [WINDOW] answers; it is
 * passed (sure) at [SURE] or better over at least [SURE_MIN] of them; it is weak below [SURE] once it has [WEAK_MIN].
 */
object EarMastery {
    const val WINDOW = 20
    const val SURE_MIN = 8
    const val SURE = 0.9
    const val WEAK_MIN = 3
    /** Items a "drill my weakest" session takes. */
    const val WEAKEST = 4
    /** Questions in a quick check, and the most items it draws from. */
    const val QUICK = 10
    const val QUICK_ITEMS = 12

    /** The item an [answer] of [drill] is: mixed and picked drills carry "drillId|answer" already. */
    fun item(drill: EarDrill, answer: String): String =
        if ('|' in answer) answer else "${drill.id}|$answer"

    /** The items a drill asks. */
    fun items(drill: EarDrill): List<String> = drill.answers.map { item(drill, it) }

    /** Each item's stat from its latest [WINDOW] answers, in any order. */
    fun stats(answers: List<EarAnswerAt>): Map<String, EarItemStat> =
        answers.groupBy { it.item }.mapValues { (item, list) ->
            val recent = list.sortedByDescending { it.at }.take(WINDOW)
            EarItemStat(item, recent.size, recent.count { it.correct }, recent.first().at)
        }

    fun sure(s: EarItemStat?): Boolean = s != null && s.answered >= SURE_MIN && s.accuracy >= SURE
    fun weak(s: EarItemStat?): Boolean = s != null && s.answered >= WEAK_MIN && s.accuracy < SURE

    /** Every item of [drill] is passed. */
    fun passed(drill: EarDrill, stats: Map<String, EarItemStat>): Boolean = items(drill).all { sure(stats[it]) }

    /** How many of [drill]'s items are passed. */
    fun passedCount(drill: EarDrill, stats: Map<String, EarItemStat>): Int = items(drill).count { sure(stats[it]) }

    /** [drill]'s items missed most, worst first (lowest accuracy, then the most answered): at most [WEAKEST]. */
    fun weakest(drill: EarDrill, stats: Map<String, EarItemStat>, n: Int = WEAKEST): List<String> =
        items(drill).mapNotNull { stats[it] }.filter(::weak)
            .sortedWith(compareBy<EarItemStat> { it.accuracy }.thenByDescending { it.answered })
            .take(n).map { it.item }

    /** Can [item] be asked on its own again: its drill exists, isn't sung into the mic and isn't tap-back. */
    fun askable(item: String): Boolean {
        val d = Ear.drill(item.substringBefore('|')) ?: return false
        return !d.sung && d.kind != EarKind.TAPBACK && item.substringAfter('|') in d.answers
    }

    /** A quick check's items: the passed ones checked longest ago first. Empty when nothing is passed yet. */
    fun review(stats: Map<String, EarItemStat>, n: Int = QUICK_ITEMS): List<String> =
        stats.values.filter { sure(it) && askable(it.item) }.sortedBy { it.lastAt }.take(n).map { it.item }

    /** The drill an item list is practised as (a pick of "drillId|answer" items). */
    fun drillOf(items: List<String>): EarDrill = Ear.pick(items)
}
