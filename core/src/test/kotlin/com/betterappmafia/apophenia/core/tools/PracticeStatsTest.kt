package com.betterappmafia.apophenia.core.tools

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PracticeStatsTest {
    private val zone = ZoneOffset.UTC
    private fun at(d: String, hour: Int = 12) = LocalDate.parse(d).atTime(hour, 0).toInstant(zone).toEpochMilli()

    @Test fun the_streak_counts_days_in_a_row_and_survives_until_today_ends() {
        val s = PracticeStats(listOf(at("2026-10-30") to 600L, at("2026-10-31") to 60L, at("2026-11-01") to 1200L, at("2026-10-27") to 300L), zone)
        assertEquals(3, s.streak(LocalDate.parse("2026-11-01")), "30, 31 and 1")
        assertEquals(3, s.streak(LocalDate.parse("2026-11-02")), "nothing yet today: yesterday's streak still stands")
        assertEquals(0, s.streak(LocalDate.parse("2026-11-03")), "a whole day missed breaks it")
        assertEquals(3, s.bestStreak())
    }

    @Test fun week_and_month_add_up_their_own_days() {
        val s = PracticeStats(listOf(at("2026-11-01", 9) to 600L, at("2026-11-01", 20) to 300L, at("2026-10-31") to 60L, at("2026-11-02") to 120L), zone)
        assertEquals(900L, s.byDay[LocalDate.parse("2026-11-01")])
        // Monday-first week of 2 Nov 2026 holds only the 2nd; Sunday-first week holds the 1st and 2nd.
        assertEquals(120L, s.week(LocalDate.parse("2026-11-02"), DayOfWeek.MONDAY))
        assertEquals(1020L, s.week(LocalDate.parse("2026-11-02"), DayOfWeek.SUNDAY))
        assertEquals(1020L, s.month(YearMonth.of(2026, 11)))
        assertEquals(2, s.daysIn(YearMonth.of(2026, 11)))
    }

    @Test fun a_take_recorded_while_the_timer_ran_counts_once() {
        // Timer 12:00 for 20 min; a 5-min take at 12:10 inside it, and one at 12:18 running 4 min past the timer's end.
        val ten = 10 * 60_000L
        val s = PracticeStats(listOf(at("2026-11-02") to 1200L, at("2026-11-02") + ten to 300L, at("2026-11-02") + ten + 8 * 60_000L to 360L), zone)
        assertEquals(1440L, s.byDay[LocalDate.parse("2026-11-02")], "12:00 to 12:24, not 20 + 5 + 6 minutes")
        val apart = PracticeStats(listOf(at("2026-11-02", 9) to 600L, at("2026-11-02", 20) to 300L), zone)
        assertEquals(900L, apart.byDay[LocalDate.parse("2026-11-02")], "stretches that don't overlap all count")
    }

    @Test fun the_calendar_starts_on_the_right_weekday_and_has_rows_of_seven() {
        val g = PracticeStats.grid(YearMonth.of(2026, 11), DayOfWeek.MONDAY)
        assertEquals(6, g[0].indexOfFirst { it != null }, "1 Nov 2026 is a Sunday")
        assertNull(g[0][0])
        assertEquals(LocalDate.parse("2026-11-30"), g.last().first())
        assert(g.all { it.size == 7 })
        assertEquals(0, PracticeStats.grid(YearMonth.of(2026, 11), DayOfWeek.SUNDAY)[0].indexOfFirst { it != null })
    }
}
