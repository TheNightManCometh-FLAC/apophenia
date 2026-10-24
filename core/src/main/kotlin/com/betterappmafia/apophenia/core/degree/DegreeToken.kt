package com.betterappmafia.apophenia.core.degree

import com.betterappmafia.apophenia.core.interval.Interval
import com.betterappmafia.apophenia.core.pitch.Accidental

/**
 * A degree token: optional accidental (`bb b n # x`) + number 1..15, read against the major scale.
 * `b3` = (3, 3), `bb7` = (7, 9), `#11` = (11, 18), `b13` = (13, 20). Scales and chords are lists of these.
 */
data class DegreeToken(val number: Int, val alter: Int = 0) {
    init {
        require(number in 1..15) { "degree number must be 1..15, was $number" }
        require(alter in -2..2) { "degree alter must be -2..+2, was $alter" }
    }

    val interval: Interval get() = Interval(number, Interval.referenceSemitones(number) + alter)

    /** Canonical ASCII form; a natural is implied, so `n3` formats as `3`. */
    override fun toString() = Accidental.format(alter) + number

    companion object {
        fun parse(s: String): DegreeToken {
            val (alter, len) = Accidental.read(s, 0)
            val number = s.substring(len).toIntOrNull()
                ?: throw IllegalArgumentException("not a degree token: '$s'")
            require(s.substring(len).all(Char::isDigit)) { "not a degree token: '$s'" }
            return DegreeToken(number, alter)
        }

        fun of(interval: Interval) = DegreeToken(interval.number, interval.deviation)

        fun list(tokens: String): List<DegreeToken> = tokens.trim().split(Regex("\\s+")).map(::parse)
    }
}
