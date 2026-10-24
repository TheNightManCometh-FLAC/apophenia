package com.betterappmafia.apophenia.core.interval

import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.pitch.SpelledPitch

/**
 * An interval as (generic number, semitones). The number counts letters inclusively
 * (1 unison, 3 third, 9 ninth), so A4 = (4, 6) and d5 = (5, 6) are different intervals.
 * Quality is derived, never stored.
 */
data class Interval(val number: Int, val semitones: Int) {
    init {
        require(number >= 1) { "interval number must be 1 or more, was $number" }
    }

    /** 1..7: the number with octaves removed. */
    val simpleNumber: Int get() = (number - 1) % 7 + 1

    val octaves: Int get() = (number - 1) / 7

    val isPerfectClass: Boolean get() = simpleNumber in PERFECT_CLASS

    /** Semitones away from the major/perfect interval of the same number. */
    val deviation: Int get() = semitones - referenceSemitones(number)

    val quality: Quality get() = Quality.of(isPerfectClass, deviation)

    val isCompound: Boolean get() = number > 8

    /** The same interval reduced to an octave or less: M9 -> M2, P15 -> P8 (an octave stays P8, not P1). */
    val simple: Interval get() {
        if (number <= 8) return this
        val drop = (number - 2) / 7
        return Interval(number - 7 * drop, semitones - 12 * drop)
    }

    /** Short name: `P5`, `m3`, `A4`, `d5`, `M9`, `AA4`, `dd7`. */
    override fun toString() = "${quality.symbol}$number"

    companion object {
        private val PERFECT_CLASS = setOf(1, 4, 5)
        private val SIMPLE_REFERENCE = intArrayOf(0, 2, 4, 5, 7, 9, 11)

        /** Semitones of the major (or perfect) interval with this number. */
        fun referenceSemitones(number: Int): Int =
            SIMPLE_REFERENCE[(number - 1) % 7] + 12 * ((number - 1) / 7)

        /** Parses a short name such as `P5`, `m3`, `A4`, `dd7`, `M13`. */
        fun parse(s: String): Interval {
            val symbol = s.takeWhile { !it.isDigit() }
            val number = s.drop(symbol.length).toIntOrNull()
                ?: throw IllegalArgumentException("not an interval: '$s'")
            val perfectClass = ((number - 1) % 7 + 1) in PERFECT_CLASS
            val deviation = Quality.deviationOf(symbol, perfectClass)
            return Interval(number, referenceSemitones(number) + deviation)
        }

        /**
         * The interval from [lower] up to [upper]. [upper] must not sit on a lower letter position
         * (a diminished unison such as C4 to Cb4 is allowed: same letter, one semitone down).
         */
        fun between(lower: SpelledPitch, upper: SpelledPitch): Interval {
            val letters = upper.letterPosition - lower.letterPosition
            require(letters >= 0) { "$upper is below $lower by letter; intervals here are measured upward" }
            return Interval(letters + 1, upper.midi - lower.midi)
        }

        /** The simple interval from [lower] up to the next [upper] by letter, within one octave. */
        fun between(lower: PitchName, upper: PitchName): Interval {
            val letters = Math.floorMod(upper.letter.ordinal - lower.letter.ordinal, 7)
            val semitones = Math.floorMod(upper.pitchClass - lower.pitchClass, 12)
            // Wrap the semitones so they sit nearest the reference for this number (Cb above B# is d2, not d9).
            val ref = SIMPLE_REFERENCE[letters]
            val wrapped = semitones - 12 * Math.round((semitones - ref) / 12.0).toInt()
            return Interval(letters + 1, wrapped)
        }
    }
}

/** Interval quality; [count] > 1 means doubly (or more) augmented / diminished. */
data class Quality(val kind: Kind, val count: Int = 1) {
    enum class Kind(val symbol: String) { DIMINISHED("d"), MINOR("m"), PERFECT("P"), MAJOR("M"), AUGMENTED("A") }

    val symbol: String get() = kind.symbol.repeat(if (kind == Kind.AUGMENTED || kind == Kind.DIMINISHED) count else 1)

    companion object {
        fun of(perfectClass: Boolean, deviation: Int): Quality = if (perfectClass) when {
            deviation == 0 -> Quality(Kind.PERFECT)
            deviation > 0 -> Quality(Kind.AUGMENTED, deviation)
            else -> Quality(Kind.DIMINISHED, -deviation)
        } else when {
            deviation == 0 -> Quality(Kind.MAJOR)
            deviation == -1 -> Quality(Kind.MINOR)
            deviation > 0 -> Quality(Kind.AUGMENTED, deviation)
            else -> Quality(Kind.DIMINISHED, -deviation - 1)
        }

        internal fun deviationOf(symbol: String, perfectClass: Boolean): Int {
            fun repeated(c: Char) = symbol.isNotEmpty() && symbol.all { it == c }
            return when {
                symbol == "P" && perfectClass -> 0
                symbol == "M" && !perfectClass -> 0
                symbol == "m" && !perfectClass -> -1
                repeated('A') -> symbol.length
                repeated('d') -> if (perfectClass) -symbol.length else -symbol.length - 1
                else -> throw IllegalArgumentException("quality '$symbol' doesn't fit a ${if (perfectClass) "perfect" else "major/minor"}-class number")
            }
        }
    }
}

/** Letter-based transposition up by [interval]. Throws if the result needs a triple accidental. */
operator fun SpelledPitch.plus(interval: Interval): SpelledPitch {
    val position = letterPosition + interval.number - 1
    val alter = alterAt(position, midi + interval.semitones)
    require(alter in -2..2) { "$this + $interval needs a triple accidental" }
    return SpelledPitch.fromLetterPosition(position, alter)
}

/** Letter-based transposition down by [interval]. Throws if the result needs a triple accidental. */
operator fun SpelledPitch.minus(interval: Interval): SpelledPitch {
    val position = letterPosition - (interval.number - 1)
    val alter = alterAt(position, midi - interval.semitones)
    require(alter in -2..2) { "$this - $interval needs a triple accidental" }
    return SpelledPitch.fromLetterPosition(position, alter)
}

/** Letter-based transposition of a name. Throws if the result needs a triple accidental. */
operator fun PitchName.plus(interval: Interval): PitchName {
    val alter = alterAbove(this, interval)
    require(alter in -2..2) { "$this + $interval needs a triple accidental" }
    return PitchName(letter + (interval.number - 1), alter)
}

/** Letter-based transposition of a name downward. Throws if the result needs a triple accidental. */
operator fun PitchName.minus(interval: Interval): PitchName {
    val target = letter + -(interval.number - 1)
    val raw = (pitchClass - interval.semitones) - target.natural
    val alter = raw - 12 * Math.round(raw / 12.0).toInt()
    require(alter in -2..2) { "$this - $interval needs a triple accidental" }
    return PitchName(target, alter)
}

/**
 * The alter the letter [interval] above [root] needs, which may fall outside -2..+2
 * (that is how the spelling engine detects a theoretical spelling).
 */
fun alterAbove(root: PitchName, interval: Interval): Int {
    val target = root.letter + (interval.number - 1)
    val raw = (root.pitchClass + interval.semitones) - target.natural
    return raw - 12 * Math.round(raw / 12.0).toInt()
}

private fun alterAt(letterPosition: Int, midi: Int): Int {
    val letter = Math.floorMod(letterPosition, 7)
    val octave = Math.floorDiv(letterPosition, 7)
    val natural = intArrayOf(0, 2, 4, 5, 7, 9, 11)[letter]
    return midi - (12 * (octave + 1) + natural)
}
