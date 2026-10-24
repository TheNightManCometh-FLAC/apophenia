package com.betterappmafia.apophenia.core.pitch

import kotlin.math.pow

/** The seven note letters, in order from C. */
enum class Letter(val natural: Int) {
    C(0), D(2), E(4), F(5), G(7), A(9), B(11);

    /** The letter [steps] letters above this one (negative = below), wrapping round the octave. */
    operator fun plus(steps: Int): Letter = entries[Math.floorMod(ordinal + steps, 7)]

    companion object {
        fun parse(c: Char): Letter = entries.firstOrNull { it.name[0] == c }
            ?: throw IllegalArgumentException("not a note letter: '$c'")
    }
}

/**
 * The ASCII accidental tokens used in storage: `bb b n # x`.
 * Unicode accidentals belong to the renderer only.
 */
enum class Accidental(val alter: Int, val token: String) {
    DOUBLE_FLAT(-2, "bb"), FLAT(-1, "b"), NATURAL(0, "n"), SHARP(1, "#"), DOUBLE_SHARP(2, "x");

    companion object {
        fun ofAlter(alter: Int): Accidental = entries.firstOrNull { it.alter == alter }
            ?: throw IllegalArgumentException("no single accidental for alter $alter")

        fun parse(token: String): Accidental = entries.firstOrNull { it.token == token }
            ?: throw IllegalArgumentException("not an accidental token: '$token'")

        /** Storage form of an alter: empty for natural, which is implied. */
        fun format(alter: Int): String = if (alter == 0) "" else ofAlter(alter).token

        /** Reads a leading accidental token from [s] at [start]; returns (alter, length read). */
        internal fun read(s: String, start: Int): Pair<Int, Int> = when {
            s.startsWith("bb", start) -> -2 to 2
            s.startsWith("b", start) -> -1 to 1
            s.startsWith("n", start) -> 0 to 1
            s.startsWith("#", start) -> 1 to 1
            s.startsWith("x", start) -> 2 to 1
            else -> 0 to 0
        }
    }
}

/** A spelled note name without an octave: what a chord root, bass or key tonic is. */
data class PitchName(val letter: Letter, val alter: Int = 0) {
    init {
        require(alter in -2..2) { "alter must be -2..+2, was $alter" }
    }

    val pitchClass: Int get() = Math.floorMod(letter.natural + alter, 12)

    fun at(octave: Int) = SpelledPitch(letter, alter, octave)

    /** ASCII storage form: `C`, `Bb`, `F#`, `Ebb`, `Fx`. */
    override fun toString() = letter.name + Accidental.format(alter)

    companion object {
        /** Parses the ASCII storage form; `n` is accepted as an explicit natural. */
        fun parse(s: String): PitchName {
            require(s.isNotEmpty()) { "empty pitch name" }
            val letter = Letter.parse(s[0])
            val (alter, len) = Accidental.read(s, 1)
            require(1 + len == s.length) { "not a pitch name: '$s'" }
            return PitchName(letter, alter)
        }
    }
}

/**
 * A pitch with its spelling: letter + alter (-2..+2) + octave.
 *
 * The octave belongs to the letter, so B#3 is MIDI 60 (the same key as C4) and Cb4 is MIDI 59.
 * Pitch class and MIDI number are derived, never stored.
 */
data class SpelledPitch(val letter: Letter, val alter: Int, val octave: Int) {
    init {
        require(alter in -2..2) { "alter must be -2..+2, was $alter" }
    }

    val name: PitchName get() = PitchName(letter, alter)

    val pitchClass: Int get() = Math.floorMod(letter.natural + alter, 12)

    val midi: Int get() = 12 * (octave + 1) + letter.natural + alter

    /** Letter position counted from C-1, so letter distance between two pitches is a subtraction. */
    internal val letterPosition: Int get() = 7 * octave + letter.ordinal

    /** Sounding frequency in Hz: reference x 2^((midi-69)/12), shifted by a temperament offset in cents. */
    fun frequency(a4Hz: Double = 440.0, centsOffset: Double = 0.0): Double =
        a4Hz * 2.0.pow((midi - 69 + centsOffset / 100.0) / 12.0)

    /** ASCII storage form, octave in the C4 = middle C convention: `C4`, `Bb3`, `F#-1`. */
    override fun toString() = "$name$octave"

    companion object {
        fun parse(s: String): SpelledPitch {
            require(s.isNotEmpty()) { "empty pitch" }
            val letter = Letter.parse(s[0])
            val (alter, len) = Accidental.read(s, 1)
            val octave = s.substring(1 + len).toIntOrNull()
                ?: throw IllegalArgumentException("not a pitch: '$s'")
            return SpelledPitch(letter, alter, octave)
        }

        internal fun fromLetterPosition(position: Int, alter: Int) =
            SpelledPitch(Letter.entries[Math.floorMod(position, 7)], alter, Math.floorDiv(position, 7))
    }
}

/**
 * Which octave number middle C gets on screen. Storage always uses C4; the MIDI number is
 * always available alongside.
 */
enum class OctaveLabel(private val shift: Int) {
    MIDDLE_C_IS_C4(0), MIDDLE_C_IS_C3(-1);

    fun label(p: SpelledPitch): String = "${p.name}${p.octave + shift}"
}
