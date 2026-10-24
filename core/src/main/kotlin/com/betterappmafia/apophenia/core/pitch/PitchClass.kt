package com.betterappmafia.apophenia.core.pitch

/**
 * A pitch class 0-11 with no spelling. Used only where spelling genuinely doesn't exist:
 * detector output, raw MIDI input, set theory. Turning one into a name needs a context.
 */
@JvmInline
value class PitchClass private constructor(val value: Int) {

    operator fun plus(semitones: Int) = PitchClass(value + semitones)

    /** Spells this pitch class with no key: the default table C Db D Eb E F F# G Ab A Bb B. */
    fun defaultSpelling(): PitchName = DEFAULT_SPELLING[value]

    override fun toString() = "pc$value"

    companion object {
        operator fun invoke(n: Int) = PitchClass(Math.floorMod(n, 12))

        private val DEFAULT_SPELLING = listOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
            .map(PitchName::parse)
    }
}
