package com.betterappmafia.apophenia.core.catalog

import com.betterappmafia.apophenia.core.pitch.PitchName

/**
 * A key and mode as the Library stores it (plan 3.4): a spelled tonic plus a catalog scale id.
 * Storage token is ASCII, `"<tonic> <scale id>"`, e.g. `"D dorian"`, `"F# aeolian"`, `"Bb major"`.
 * C major and A minor stay distinct: they are different tokens.
 */
data class KeyMode(val tonic: PitchName, val scaleId: String) {
    init {
        Scales.byId(scaleId)   // throws for an id the catalog doesn't have
    }

    val scale: ScaleDef get() = Scales.byId(scaleId)

    val token: String get() = "$tonic $scaleId"

    /** "D Dorian", "F♯ minor", "B♭ major", "E Phrygian dominant". */
    val display: String get() = "${displayName(tonic)} ${modeName(scaleId)}"

    /** Compact form for list columns: "D DOR", "F♯ MIN", "B♭ MAJ". */
    val short: String get() = "${displayName(tonic)} ${SHORT[scaleId] ?: modeName(scaleId).take(3).uppercase()}"

    override fun toString() = token

    companion object {
        private val SHORT = mapOf(
            "major" to "MAJ", "aeolian" to "MIN", "dorian" to "DOR", "phrygian" to "PHR", "lydian" to "LYD",
            "mixolydian" to "MIX", "locrian" to "LOC", "harmonic-minor" to "HMIN", "melodic-minor" to "MMIN",
        )

        /** Parses a storage token; null for null, blank or anything malformed (never trusted, never thrown at the UI). */
        fun parseOrNull(token: String?): KeyMode? {
            if (token.isNullOrBlank()) return null
            val space = token.indexOf(' ')
            if (space <= 0) return null
            return runCatching { KeyMode(PitchName.parse(token.substring(0, space)), token.substring(space + 1)) }.getOrNull()
        }

        /** The mode's name as musicians say it in a key: "major" and "minor" lower case, modes capitalised. */
        fun modeName(scaleId: String): String = when (scaleId) {
            "major" -> "major"
            "aeolian" -> "minor"
            else -> Scales.byId(scaleId).names.first()
        }

        /** Note name with Unicode accidentals, for display only. */
        fun displayName(p: PitchName): String = p.letter.name + when (p.alter) {
            -2 -> "𝄫"; -1 -> "♭"; 1 -> "♯"; 2 -> "𝄪"; else -> ""
        }

        /** The 21 tonics a key picker offers: every letter natural, flat and sharp, in circle-of-fifths-free letter order. */
        val TONICS: List<PitchName> = com.betterappmafia.apophenia.core.pitch.Letter.entries.flatMap { l ->
            listOf(PitchName(l, 0), PitchName(l, -1), PitchName(l, 1))
        }
    }
}
