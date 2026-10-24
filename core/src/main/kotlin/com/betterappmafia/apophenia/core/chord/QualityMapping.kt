package com.betterappmafia.apophenia.core.chord

import com.betterappmafia.apophenia.core.chord.DegreeEdit.Companion.add
import com.betterappmafia.apophenia.core.chord.DegreeEdit.Companion.alter

/**
 * The one table that keeps a catalog quality id and a MusicXML kind + degrees consistent.
 * Every catalog chord has exactly one encoding; decoding is the reverse lookup.
 *
 * Exceptions, by the spec's own definitions: dominant-13th and major-13th include the 11th,
 * which the catalog's 13 and maj13 leave out because it clashes with the major 3rd
 * ([VOICING_OMITS]). 7alt has no MusicXML kind, so it is dominant + hidden degrees with
 * kind text "7alt" (MusicXML's '13sus' rule).
 */
object QualityMapping {

    data class Encoding(val kind: ChordKind, val degrees: List<DegreeEdit> = emptyList(), val kindText: String? = null)

    private fun e(kind: ChordKind, vararg degrees: DegreeEdit) = Encoding(kind, degrees.toList())

    val table: Map<String, Encoding> = linkedMapOf(
        "maj" to e(ChordKind.MAJOR),
        "m" to e(ChordKind.MINOR),
        "dim" to e(ChordKind.DIMINISHED),
        "aug" to e(ChordKind.AUGMENTED),
        "sus2" to e(ChordKind.SUSPENDED_SECOND),
        "sus4" to e(ChordKind.SUSPENDED_FOURTH),
        "5" to e(ChordKind.POWER),
        "6" to e(ChordKind.MAJOR_SIXTH),
        "m6" to e(ChordKind.MINOR_SIXTH),
        "69" to e(ChordKind.MAJOR_SIXTH, add(9)),
        "m69" to e(ChordKind.MINOR_SIXTH, add(9)),
        "maj7" to e(ChordKind.MAJOR_SEVENTH),
        "7" to e(ChordKind.DOMINANT),
        "m7" to e(ChordKind.MINOR_SEVENTH),
        "mMaj7" to e(ChordKind.MAJOR_MINOR),
        "m7b5" to e(ChordKind.HALF_DIMINISHED),
        "dim7" to e(ChordKind.DIMINISHED_SEVENTH),
        "7#5" to e(ChordKind.AUGMENTED_SEVENTH),
        "maj7#5" to e(ChordKind.MAJOR_SEVENTH, alter(5, 1)),
        "7b5" to e(ChordKind.DOMINANT, alter(5, -1)),
        "7sus4" to e(ChordKind.SUSPENDED_FOURTH, add(7)),
        "9" to e(ChordKind.DOMINANT_NINTH),
        "maj9" to e(ChordKind.MAJOR_NINTH),
        "m9" to e(ChordKind.MINOR_NINTH),
        "mMaj9" to e(ChordKind.MAJOR_MINOR, add(9)),
        "11" to e(ChordKind.DOMINANT_11TH),
        "m11" to e(ChordKind.MINOR_11TH),
        "maj7#11" to e(ChordKind.MAJOR_11TH, alter(11, 1)),
        "13" to e(ChordKind.DOMINANT_13TH),
        "maj13" to e(ChordKind.MAJOR_13TH),
        "m13" to e(ChordKind.MINOR_13TH),
        "7b9" to e(ChordKind.DOMINANT, add(9, -1)),
        "7#9" to e(ChordKind.DOMINANT, add(9, 1)),
        "7#11" to e(ChordKind.DOMINANT, add(11, 1)),
        "7b13" to e(ChordKind.DOMINANT, add(13, -1)),
        "7alt" to Encoding(
            ChordKind.DOMINANT,
            listOf(alter(5, 1), add(9, -1), add(9, 1), add(11, 1)).map { it.copy(printed = false) },
            kindText = "7alt",
        ),
        "add9" to e(ChordKind.MAJOR, add(9)),
        "madd9" to e(ChordKind.MINOR, add(9)),
        "add11" to e(ChordKind.MAJOR, add(11)),
        "add2" to e(ChordKind.MAJOR, add(2)),
        "add4" to e(ChordKind.MAJOR, add(4)),
    )

    /** Degrees a kind contains by definition that the catalog quality leaves out of its voicing. */
    val VOICING_OMITS: Map<String, Int> = mapOf("13" to 11, "maj13" to 11)

    fun encode(quality: String): Encoding = table[quality] ?: throw NoSuchElementException("no encoding for quality '$quality'")

    /** The catalog quality for a kind + degrees, ignoring print-object; null when no row matches. */
    fun decode(kind: ChordKind, degrees: List<DegreeEdit>): String? {
        val wanted = degrees.map { it.copy(printed = true) }
        return table.entries.firstOrNull { (_, enc) ->
            enc.kind == kind && enc.degrees.map { it.copy(printed = true) } == wanted
        }?.key
    }
}
