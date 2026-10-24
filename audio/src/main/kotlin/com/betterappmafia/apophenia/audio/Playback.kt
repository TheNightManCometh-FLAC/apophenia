package com.betterappmafia.apophenia.audio

import kotlin.math.pow

/**
 * Playback-speed settings for the take in the player (plan §7): speed 0.25–2× without pitch change,
 * pitch ±24 semitones and ±100 cents, an A/B loop (frames, -1 = none), step-up loops (speed rises
 * by [stepBy] each pass until [stepTo]), and a count-in of [countInBeats] before each pass at
 * [beatFrames] frames per beat of the take (0 = its tempo isn't known, so no count-in).
 */
data class Playback(
    val speed: Double = 1.0,
    val semitones: Int = 0,
    val cents: Int = 0,
    val loopA: Long = -1,
    val loopB: Long = -1,
    val stepBy: Double = 0.0,
    val stepTo: Double = 1.0,
    val countInBeats: Int = 0,
    val beatFrames: Double = 0.0,
    /** Played through the isolator (its settings are live, see NativeEngine.setIsolator). */
    val isolate: Boolean = false,
) {
    init {
        require(speed in 0.25..2.0) { "speed must be 0.25..2, was $speed" }
        require(semitones in -24..24 && cents in -100..100) { "pitch out of range" }
    }

    /** Pitch as a frequency factor: 2^((semitones + cents/100) / 12). */
    val transpose: Double get() = 2.0.pow((semitones + cents / 100.0) / 12.0)

    val hasLoop: Boolean get() = loopA >= 0 && loopB > loopA

    /** True when nothing is changed (played straight from the file). */
    val isPlain: Boolean get() = speed == 1.0 && semitones == 0 && cents == 0 && !hasLoop && !isolate

    /** "0.60× · −2 st", "1.00×", for the player bar. */
    val label: String
        get() = buildString {
            append(String.format(java.util.Locale.ROOT, "%.2f×", speed))
            if (semitones != 0) append(" · ${if (semitones > 0) "+" else "−"}${kotlin.math.abs(semitones)} st")
            if (cents != 0) append(" · ${if (cents > 0) "+" else "−"}${kotlin.math.abs(cents)}¢")
            if (hasLoop) append(" · LOOP")
            if (isolate) append(" · ISOLATED")
        }

    companion object {
        /** The speed menu's steps. */
        val SPEEDS = listOf(0.25, 0.4, 0.5, 0.6, 0.65, 0.7, 0.75, 0.8, 0.85, 0.9, 0.95, 1.0, 1.1, 1.25, 1.5, 2.0)
    }
}
