package com.betterappmafia.apophenia.core.tools

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * The practice log added up (Christopher, 2 Nov 2026: "a day streak and time spent a week and month counter/calendar
 * and a practice log"). Days are local calendar days in [zone]; a stretch counts on the day it started.
 */
class PracticeStats(spans: List<Pair<Long, Long>>, private val zone: ZoneId) {
    /** Seconds practised per day. Overlapping stretches count once (a take recorded while the timer ran is in both). */
    val byDay: Map<LocalDate, Long> = merged(spans).groupBy({ day(it.first) }, { it.second }).mapValues { it.value.sum() }

    fun day(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    /**
     * Days in a row with practice, ending today, or ending yesterday while today has none yet (the streak isn't broken
     * until a whole day goes by).
     */
    fun streak(today: LocalDate): Int {
        var d = if ((byDay[today] ?: 0) > 0) today else today.minusDays(1)
        var n = 0
        while ((byDay[d] ?: 0) > 0) { n++; d = d.minusDays(1) }
        return n
    }

    /** The longest run of days in a row ever. */
    fun bestStreak(): Int {
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in byDay.filterValues { it > 0 }.keys.sorted()) {
            run = if (prev != null && d == prev.plusDays(1)) run + 1 else 1
            best = maxOf(best, run)
            prev = d
        }
        return best
    }

    /** Seconds in the week holding [today], from [firstDay]. */
    fun week(today: LocalDate, firstDay: DayOfWeek): Long {
        val start = today.with(TemporalAdjusters.previousOrSame(firstDay))
        return (0L until 7).sumOf { byDay[start.plusDays(it)] ?: 0 }
    }

    fun month(m: YearMonth): Long = (1..m.lengthOfMonth()).sumOf { byDay[m.atDay(it)] ?: 0 }

    /** Days practised in [m]. */
    fun daysIn(m: YearMonth): Int = (1..m.lengthOfMonth()).count { (byDay[m.atDay(it)] ?: 0) > 0 }

    companion object {
        /** (start ms, seconds) stretches with overlaps joined: each joined stretch starts at its earliest start. */
        fun merged(spans: List<Pair<Long, Long>>): List<Pair<Long, Long>> {
            val out = ArrayList<Pair<Long, Long>>()
            var start = 0L
            var end = Long.MIN_VALUE
            for ((s, secs) in spans.filter { it.second > 0 }.sortedBy { it.first }) {
                val e = s + secs * 1000
                if (s < end) end = maxOf(end, e)
                else { if (end != Long.MIN_VALUE) out += start to (end - start) / 1000; start = s; end = e }
            }
            if (end != Long.MIN_VALUE) out += start to (end - start) / 1000
            return out
        }

        /**
         * A month as calendar rows of 7, from [firstDay]; null where the day belongs to the month before or after.
         */
        fun grid(m: YearMonth, firstDay: DayOfWeek): List<List<LocalDate?>> {
            val lead = Math.floorMod(m.atDay(1).dayOfWeek.value - firstDay.value, 7)
            val cells = List<LocalDate?>(lead) { null } + (1..m.lengthOfMonth()).map { m.atDay(it) }
            return cells.chunked(7).map { it + List(7 - it.size) { null } }
        }
    }
}
