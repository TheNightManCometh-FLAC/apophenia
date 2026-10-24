package com.betterappmafia.apophenia.core.rhythm

/** Positions and durations in ticks use 960 per quarter note (divisible by 2, 3, 4, 5, 6, 8, 10, 12, 15, 16...). */
const val TICKS_PER_QUARTER = 960

/** Ticks per whole note. */
const val TICKS_PER_WHOLE = 4 * TICKS_PER_QUARTER

/** Written note values, named as MusicXML `<type>` names them. */
enum class NoteValue(val xml: String, val length: Rational) {
    BREVE("breve", Rational.of(2)),
    WHOLE("whole", Rational.ONE),
    HALF("half", Rational.of(1, 2)),
    QUARTER("quarter", Rational.of(1, 4)),
    EIGHTH("eighth", Rational.of(1, 8)),
    SIXTEENTH("16th", Rational.of(1, 16)),
    THIRTY_SECOND("32nd", Rational.of(1, 32)),
    SIXTY_FOURTH("64th", Rational.of(1, 64)),
    HUNDRED_TWENTY_EIGHTH("128th", Rational.of(1, 128)),
}

/** A tuplet ratio: [actual] notes in the time of [normal] (3:2 triplet, 5:4 quintuplet, 2:3 duplet, 7:4...). */
data class Tuplet(val actual: Int, val normal: Int) {
    init {
        require(actual >= 1 && normal >= 1) { "tuplet numbers must be positive: $actual:$normal" }
    }

    val factor: Rational get() = Rational.of(normal, actual)

    override fun toString() = "$actual:$normal"
}

/**
 * A written duration: note value, 0-3 dots, and a tuplet stack (outermost first; nesting allowed).
 * [length] is exact; [ticks] exists only when the length lands on a whole tick.
 */
data class Duration(val value: NoteValue, val dots: Int = 0, val tuplets: List<Tuplet> = emptyList()) {
    init {
        require(dots in 0..3) { "dots must be 0..3, was $dots" }
    }

    /** Length as a fraction of a whole note: base x (2 - 2^-dots) x each tuplet's normal/actual. */
    val length: Rational
        get() {
            val dotted = value.length * Rational.of((1 shl (dots + 1)) - 1, 1 shl dots)
            return tuplets.fold(dotted) { acc, t -> acc * t.factor }
        }

    /** The cumulative tuplet ratio, as MusicXML `<time-modification>` writes it (9:4 for a triplet inside a triplet). */
    val timeModification: Tuplet?
        get() = if (tuplets.isEmpty()) null
        else Tuplet(tuplets.fold(1) { a, t -> a * t.actual }, tuplets.fold(1) { a, t -> a * t.normal })

    val ticksOrNull: Int? get() = (length * TICKS_PER_WHOLE).let { if (it.isInteger) it.num.toInt() else null }

    val ticks: Int get() = ticksOrNull ?: throw IllegalStateException("$this is not a whole number of ticks; use length")

    fun dotted(n: Int = 1) = copy(dots = n)
    fun inTuplet(actual: Int, normal: Int) = copy(tuplets = tuplets + Tuplet(actual, normal))

    companion object {
        val WHOLE = Duration(NoteValue.WHOLE)
        val HALF = Duration(NoteValue.HALF)
        val QUARTER = Duration(NoteValue.QUARTER)
        val EIGHTH = Duration(NoteValue.EIGHTH)
        val SIXTEENTH = Duration(NoteValue.SIXTEENTH)
    }
}
