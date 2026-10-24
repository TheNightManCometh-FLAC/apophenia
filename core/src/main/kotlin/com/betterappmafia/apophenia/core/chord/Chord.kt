package com.betterappmafia.apophenia.core.chord

import com.betterappmafia.apophenia.core.degree.DegreeToken
import com.betterappmafia.apophenia.core.interval.Interval
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.pitch.SpelledPitch
import com.betterappmafia.apophenia.core.spelling.Spelling

/** MusicXML `<degree-type>`. */
enum class DegreeType(val xml: String) { ADD("add"), ALTER("alter"), SUBTRACT("subtract") }

/**
 * One MusicXML `<degree>` edit. The spec's arithmetic: for [DegreeType.ALTER] and
 * [DegreeType.SUBTRACT], [alter] is relative to the degree already in the kind; for
 * [DegreeType.ADD] it is relative to a dominant chord (major and perfect intervals except a
 * minor seventh), so adding a maj7 is +1, a b7 is 0, a b9 is -1 and a #11 is +1.
 * [printed] = false is MusicXML's print-object="no" (degrees implied by text such as "7alt").
 */
data class DegreeEdit(val value: Int, val alter: Int, val type: DegreeType, val printed: Boolean = true) {
    init {
        require(value in 1..15) { "degree value must be 1..15, was $value" }
    }

    companion object {
        fun add(value: Int, alter: Int = 0) = DegreeEdit(value, alter, DegreeType.ADD)
        fun alter(value: Int, alter: Int) = DegreeEdit(value, alter, DegreeType.ALTER)
        fun subtract(value: Int) = DegreeEdit(value, 0, DegreeType.SUBTRACT)

        /** The alter a dominant chord gives this degree number, relative to major/perfect: -1 for 7, else 0. */
        fun dominantReference(value: Int): Int = if ((value - 1) % 7 + 1 == 7) -1 else 0
    }
}

/** What a harmony is built on: a spelled root, or a MusicXML 4.0 `<numeral>`. */
sealed interface ChordRoot {
    data class Name(val name: PitchName) : ChordRoot

    /** Roman / Nashville numeral 1..7 with an alter, in a key given by fifths and mode. */
    data class Numeral(val numeral: Int, val alter: Int = 0, val key: NumeralKey? = null) : ChordRoot {
        init {
            require(numeral in 1..7) { "numeral must be 1..7, was $numeral" }
        }
    }
}

/** MusicXML `<numeral-key>`: fifths and one of major, minor, natural minor, melodic minor, harmonic minor. */
data class NumeralKey(val fifths: Int, val mode: String)

/**
 * A chord as the user wrote it, mirroring MusicXML 4.0 `<harmony>`: root (or numeral), one of
 * the 33 kinds, ordered degree edits, optional bass, optional second chord above it (a polychord,
 * drawn with a horizontal line). [quality] is the catalog id; [typed] the exact text the user typed;
 * [kindText] the `kind@text` to display. Meaning comes from kind + degrees only.
 */
data class Chord(
    val root: ChordRoot?,
    val kind: ChordKind,
    val degrees: List<DegreeEdit> = emptyList(),
    val bass: PitchName? = null,
    val poly: Chord? = null,
    val quality: String? = null,
    val typed: String? = null,
    val kindText: String? = null,
    val voicing: List<SpelledPitch>? = null,
) {
    init {
        require((kind == ChordKind.NONE) == (root == null)) { "only N.C. (kind none) has no root" }
    }

    val isNoChord: Boolean get() = kind == ChordKind.NONE

    /** The chord's degree tokens after applying the edits to the kind, in pitch order. Empty for N.C. */
    fun degreeTokens(): List<DegreeToken> {
        val tokens = (kind.degrees ?: return emptyList()).toMutableList()
        for (e in degrees) when (e.type) {
            DegreeType.ADD -> tokens += DegreeToken(e.value, DegreeEdit.dominantReference(e.value) + e.alter)
            DegreeType.ALTER -> {
                val i = tokens.indexOfFirst { it.number == e.value }
                if (i >= 0) tokens[i] = DegreeToken(e.value, tokens[i].alter + e.alter)
                // Altering a degree the kind lacks: writers do this; read it as an add.
                else tokens += DegreeToken(e.value, DegreeEdit.dominantReference(e.value) + e.alter)
            }
            DegreeType.SUBTRACT -> tokens.removeAll { it.number == e.value }
        }
        return tokens.sortedWith(compareBy({ it.interval.semitones }, { it.number }))
    }

    /** The chord's notes spelled above its named root (kept as written), or empty for N.C. or a numeral. */
    fun notes(): List<PitchName> {
        val r = (root as? ChordRoot.Name)?.name ?: return emptyList()
        return Spelling.spell(r, degreeTokens(), keepRoot = true).notes
    }

    /** The interval of the bass above the root, when the bass is a chord tone or not. */
    fun bassInterval(): Interval? {
        val r = (root as? ChordRoot.Name)?.name ?: return null
        return bass?.let { Interval.between(r, it) }
    }

    companion object {
        /** N.C.: kind none, no root. */
        val NO_CHORD = Chord(root = null, kind = ChordKind.NONE, kindText = "N.C.")

        fun of(root: String, kind: ChordKind, vararg degrees: DegreeEdit, bass: String? = null) =
            Chord(ChordRoot.Name(PitchName.parse(root)), kind, degrees.toList(), bass?.let(PitchName::parse))
    }
}
