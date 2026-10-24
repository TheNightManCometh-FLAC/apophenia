package com.betterappmafia.apophenia.core.chord

import com.betterappmafia.apophenia.core.degree.DegreeToken

/**
 * The 33 MusicXML 4.0 `<kind>` values, with the exact spellings the spec uses ([xml]).
 * [degrees] is the kind's pitch content per the spec's definitions; `null` for `none`.
 *
 * The four functional sixths are measured from their lowest note in root position, which is the
 * root the app stores [Claude]: Neapolitan from bII (Db F Ab in C), the augmented sixths from b6
 * (Italian Ab C F#, French Ab C D F#, German Ab C Eb F#). Tristan is F B D# G#: A4, A6, A9.
 */
enum class ChordKind(val xml: String, degrees: String?) {
    MAJOR("major", "1 3 5"),
    MINOR("minor", "1 b3 5"),
    AUGMENTED("augmented", "1 3 #5"),
    DIMINISHED("diminished", "1 b3 b5"),
    DOMINANT("dominant", "1 3 5 b7"),
    MAJOR_SEVENTH("major-seventh", "1 3 5 7"),
    MINOR_SEVENTH("minor-seventh", "1 b3 5 b7"),
    DIMINISHED_SEVENTH("diminished-seventh", "1 b3 b5 bb7"),
    AUGMENTED_SEVENTH("augmented-seventh", "1 3 #5 b7"),
    HALF_DIMINISHED("half-diminished", "1 b3 b5 b7"),
    MAJOR_MINOR("major-minor", "1 b3 5 7"),
    MAJOR_SIXTH("major-sixth", "1 3 5 6"),
    MINOR_SIXTH("minor-sixth", "1 b3 5 6"),
    DOMINANT_NINTH("dominant-ninth", "1 3 5 b7 9"),
    MAJOR_NINTH("major-ninth", "1 3 5 7 9"),
    MINOR_NINTH("minor-ninth", "1 b3 5 b7 9"),
    DOMINANT_11TH("dominant-11th", "1 3 5 b7 9 11"),
    MAJOR_11TH("major-11th", "1 3 5 7 9 11"),
    MINOR_11TH("minor-11th", "1 b3 5 b7 9 11"),
    DOMINANT_13TH("dominant-13th", "1 3 5 b7 9 11 13"),
    MAJOR_13TH("major-13th", "1 3 5 7 9 11 13"),
    MINOR_13TH("minor-13th", "1 b3 5 b7 9 11 13"),
    SUSPENDED_SECOND("suspended-second", "1 2 5"),
    SUSPENDED_FOURTH("suspended-fourth", "1 4 5"),
    NEAPOLITAN("Neapolitan", "1 3 5"),
    ITALIAN("Italian", "1 3 #6"),
    FRENCH("French", "1 3 #4 #6"),
    GERMAN("German", "1 3 5 #6"),
    PEDAL("pedal", "1"),
    POWER("power", "1 5"),
    TRISTAN("Tristan", "1 #4 #6 #9"),
    OTHER("other", ""),
    NONE("none", null);

    val degrees: List<DegreeToken>? =
        degrees?.let { if (it.isEmpty()) emptyList() else DegreeToken.list(it) }

    companion object {
        fun fromXml(value: String): ChordKind = entries.firstOrNull { it.xml == value }
            ?: throw IllegalArgumentException("not a MusicXML kind value: '$value'")
    }
}
