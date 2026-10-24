package com.betterappmafia.apophenia.core.rhythm

/**
 * A time signature as additive groups over a denominator: 4/4 = [1,1,1,1]/4, 6/8 = [3,3]/8 (two beats),
 * 7/8 = [2,2,3]/8. Each group is one beat; beaming, metronome accents and the beat count come from them.
 */
data class TimeSignature(val groups: List<Int>, val denominator: Int) {
    init {
        require(groups.isNotEmpty() && groups.all { it >= 1 }) { "groups must be positive: $groups" }
        require(denominator in setOf(1, 2, 4, 8, 16, 32, 64)) { "denominator must be a power of two, was $denominator" }
    }

    val numerator: Int get() = groups.sum()

    val beatsPerBar: Int get() = groups.size

    val barLength: Rational get() = Rational.of(numerator, denominator)

    val barTicks: Int get() = (barLength * TICKS_PER_WHOLE).num.toInt()

    /** Length of each beat in the bar, in order (7/8 as 2+2+3 gives quarter, quarter, dotted quarter). */
    val beatLengths: List<Rational> get() = groups.map { Rational.of(it, denominator) }

    /** Bar-relative start of each beat. */
    val beatStarts: List<Rational>
        get() = beatLengths.runningFold(Rational.ZERO) { a, b -> a + b }.dropLast(1)

    val isCompound: Boolean get() = groups.all { it == 3 } && denominator >= 8

    /** MusicXML form: `<beats>` "2+2+3" or "4", `<beat-type>` "8". */
    val xmlBeats: String
        get() = if (groups.all { it == 1 } || isCompound) "$numerator" else groups.joinToString("+")

    override fun toString() = "$xmlBeats/$denominator"

    companion object {
        /**
         * A standard signature: simple (n beats of 1/den) or compound (3/8 in one, 6/8, 9/8, 12/8: groups of three).
         * Irregular meters such as 5/8 and 7/8 have more than one grouping, so they need [additive].
         */
        fun of(numerator: Int, denominator: Int): TimeSignature {
            require(numerator >= 1) { "numerator must be positive" }
            if (denominator >= 8 && numerator % 3 == 0) {
                return TimeSignature(List(numerator / 3) { 3 }, denominator)
            }
            require(denominator < 8 || numerator in setOf(1, 2, 4)) {
                "$numerator/$denominator has more than one grouping; give it explicitly (e.g. 2+2+3)"
            }
            return TimeSignature(List(numerator) { 1 }, denominator)
        }

        fun additive(vararg groups: Int, denominator: Int) = TimeSignature(groups.toList(), denominator)

        /** Parses "4/4", "6/8", "2+2+3/8". */
        fun parse(s: String): TimeSignature {
            val (beats, den) = s.split('/').also { require(it.size == 2) { "not a time signature: '$s'" } }
            val d = den.toIntOrNull() ?: throw IllegalArgumentException("not a time signature: '$s'")
            return if ('+' in beats) TimeSignature(beats.split('+').map { it.toInt() }, d)
            else of(beats.toIntOrNull() ?: throw IllegalArgumentException("not a time signature: '$s'"), d)
        }
    }
}

/** Tempo as BPM of a beat unit: 6/8 at dotted quarter = 60 is the same speed as quarter = 90. */
data class Tempo(val bpm: Double, val beatUnit: Duration = Duration.QUARTER) {
    init {
        require(bpm > 0 && bpm.isFinite()) { "bpm must be positive, was $bpm" }
    }

    val quarterNotesPerMinute: Double get() = bpm * (beatUnit.length * 4).toDouble()

    fun secondsFor(length: Rational): Double = (length * 4).toDouble() * 60.0 / quarterNotesPerMinute

    fun secondsForTicks(ticks: Long): Double = ticks.toDouble() / TICKS_PER_QUARTER * 60.0 / quarterNotesPerMinute

    /** Same speed, counted in another beat unit. */
    fun inUnit(unit: Duration) = Tempo(quarterNotesPerMinute / (unit.length * 4).toDouble(), unit)
}

/**
 * Swing as a playback parameter: within each pair of [unit] notes, the off-beat moves from halfway to
 * [ratio] of the pair. 0.5 is straight, 2/3 is triplet swing. Notation never changes.
 */
data class Swing(val ratio: Double, val unit: Duration = Duration.EIGHTH) {
    init {
        require(ratio in 0.5..0.8) { "swing ratio must be 0.5..0.8, was $ratio" }
    }

    /** Where a written position (fraction of a whole note) sounds. */
    fun sounding(written: Rational): Double {
        val pair = (unit.length * 2).toDouble()
        val w = written.toDouble()
        val pairStart = Math.floor(w / pair) * pair
        val f = (w - pairStart) / pair
        val shaped = if (f < 0.5) f / 0.5 * ratio else ratio + (f - 0.5) / 0.5 * (1 - ratio)
        return pairStart + shaped * pair
    }

    companion object {
        val STRAIGHT = Swing(0.5)
        val TRIPLET = Swing(2.0 / 3.0)
    }
}
