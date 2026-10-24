package com.betterappmafia.apophenia.core.spelling

import com.betterappmafia.apophenia.core.degree.DegreeToken
import com.betterappmafia.apophenia.core.interval.alterAbove
import com.betterappmafia.apophenia.core.pitch.Letter
import com.betterappmafia.apophenia.core.pitch.PitchClass
import com.betterappmafia.apophenia.core.pitch.PitchName
import kotlin.math.abs

/**
 * A spelled scale or chord. [respelledFrom] is set when the requested root would have needed a
 * triple accidental and the engine moved to its enharmonic twin (Fb dim7 -> E dim7).
 */
data class SpelledSet(val root: PitchName, val notes: List<PitchName>, val respelledFrom: PitchName? = null)

/** Thrown when a spelling needs a triple accidental and the context forbids respelling the root. */
class TheoreticalSpellingException(message: String) : IllegalArgumentException(message)

object Spelling {

    /**
     * Spells [degrees] above [root]: letter from the degree number, alter from its semitones.
     * If any note would need a triple accidental the root is respelled enharmonically, unless
     * [keepRoot] says the context forces this root, in which case this throws.
     */
    fun spell(root: PitchName, degrees: List<DegreeToken>, keepRoot: Boolean = false): SpelledSet {
        spellOrNull(root, degrees)?.let { return SpelledSet(root, it) }
        if (keepRoot) throw TheoreticalSpellingException("$root ${degrees.joinToString(" ")} needs a triple accidental")
        val twin = enharmonicTwins(root)
            .mapNotNull { r -> spellOrNull(r, degrees)?.let { r to it } }
            .minByOrNull { (r, notes) -> abs(r.alter) * 10 + notes.sumOf { abs(it.alter) } }
            ?: throw TheoreticalSpellingException("no spelling of ${degrees.joinToString(" ")} on $root avoids triple accidentals")
        return SpelledSet(twin.first, twin.second, respelledFrom = root)
    }

    /** Spells on a root that has no key context, named from the default table. */
    fun spell(root: PitchClass, degrees: List<DegreeToken>): SpelledSet = spell(root.defaultSpelling(), degrees)

    /** The other names for the same pitch class within double accidentals (C# -> Db, Bx). */
    fun enharmonicTwins(name: PitchName): List<PitchName> = Letter.entries.mapNotNull { letter ->
        if (letter == name.letter) return@mapNotNull null
        val raw = name.pitchClass - letter.natural
        val alter = raw - 12 * Math.round(raw / 12.0).toInt()
        if (alter in -2..2) PitchName(letter, alter) else null
    }

    private fun spellOrNull(root: PitchName, degrees: List<DegreeToken>): List<PitchName>? {
        val notes = ArrayList<PitchName>(degrees.size)
        for (d in degrees) {
            val alter = alterAbove(root, d.interval)
            if (alter !in -2..2) return null
            notes += PitchName(root.letter + (d.number - 1), alter)
        }
        return notes
    }
}
