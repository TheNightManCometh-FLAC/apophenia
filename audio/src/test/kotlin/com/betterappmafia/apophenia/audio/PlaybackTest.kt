package com.betterappmafia.apophenia.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTest {
    @Test fun `twelve semitones up doubles the pitch, a hundred cents is a semitone`() {
        assertEquals(2.0, Playback(semitones = 12).transpose, 1e-12)
        assertEquals(Playback(semitones = 1).transpose, Playback(cents = 100).transpose, 1e-12)
        assertEquals(1.0, Playback(semitones = 1, cents = -100).transpose, 1e-12)
    }

    @Test fun `plain means straight from the file, a loop or any change is not plain`() {
        assertTrue(Playback().isPlain)
        assertFalse(Playback(speed = 0.6).isPlain)
        assertFalse(Playback(loopA = 0, loopB = 48000).isPlain)
        assertFalse(Playback(loopA = 48000, loopB = 100).hasLoop)
    }

    @Test fun `the label says speed, pitch and loop`() {
        assertEquals("0.60× · −2 st · +15¢ · LOOP", Playback(speed = 0.6, semitones = -2, cents = 15, loopA = 0, loopB = 9).label)
        assertEquals("1.00×", Playback().label)
    }

    @Test fun `speed and pitch outside their ranges are refused`() {
        assertTrue(runCatching { Playback(speed = 0.2) }.isFailure)
        assertTrue(runCatching { Playback(semitones = 25) }.isFailure)
    }
}
