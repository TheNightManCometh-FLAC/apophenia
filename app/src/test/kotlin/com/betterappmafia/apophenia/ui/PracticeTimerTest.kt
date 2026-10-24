package com.betterappmafia.apophenia.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeTimerTest {
    @Test fun `the timer adds up runs between pauses and keeps the first start`() {
        var t = PracticeTimer().start(1_000)
        assertTrue(t.running)
        t = t.pause(61_000)                 // 60 s
        assertFalse(t.running)
        assertEquals(60_000, t.elapsedMs(500_000))   // paused: time doesn't move
        t = t.start(100_000)
        assertEquals(90_000, t.elapsedMs(130_000))   // 60 + 30
        assertEquals(1_000L, t.firstStart)
        assertEquals(t, t.start(200_000))            // starting while running changes nothing
    }

    @Test fun `a running timer survives being saved and read back, with what it's on`() {
        val t = PracticeTimer(runningSince = 5, accumulatedMs = 10, firstStart = 1, scrapId = 7, exercise = "Paradiddles | at 100", label = "Paradiddles | at 100")
        val back = PracticeTimer.decode(t.encode())!!
        assertEquals(5L, back.runningSince)
        assertEquals(7L, back.scrapId)
        assertEquals("Paradiddles / at 100", back.label)
        assertEquals(null, PracticeTimer.decode("2|x"))
    }

    @Test fun `clock shows minutes and hours`() {
        assertEquals("0:42", clockSeconds(42))
        assertEquals("12:05", clockSeconds(725))
        assertEquals("1:02:03", clockSeconds(3723))
    }
}
