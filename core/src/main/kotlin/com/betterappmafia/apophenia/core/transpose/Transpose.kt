package com.betterappmafia.apophenia.core.transpose

import com.betterappmafia.apophenia.core.chord.Chord
import com.betterappmafia.apophenia.core.chord.ChordRoot
import com.betterappmafia.apophenia.core.interval.Interval
import com.betterappmafia.apophenia.core.interval.alterAbove
import com.betterappmafia.apophenia.core.pitch.PitchClass
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.spelling.Spelling

/** A transposed chord; [theoretical] is true when the target spelling needed an enharmonic respelling. */
data class Transposed(val chord: Chord, val theoretical: Boolean)

object Transpose {

    /**
     * Moves root, bass and any polychord by the interval from [fromKey] to [toKey], letter by letter,
     * so C -> Eb turns D7 into F7, never E#7. A root or bass that would need a triple accidental, or a
     * root whose chord tones would, is respelled enharmonically and reported. Numeral roots are
     * key-relative and stay as they are. [Chord.typed] is cleared: it no longer matches.
     */
    fun chord(chord: Chord, fromKey: PitchName, toKey: PitchName): Transposed =
        chord(chord, Interval.between(fromKey, toKey))

    fun chord(chord: Chord, interval: Interval): Transposed {
        var theoretical = false

        fun move(n: PitchName): PitchName {
            val alter = alterAbove(n, interval)
            if (alter in -2..2) return PitchName(n.letter + (interval.number - 1), alter)
            theoretical = true
            return PitchClass(n.pitchClass + interval.semitones).defaultSpelling()
        }

        val root = when (val r = chord.root) {
            is ChordRoot.Name -> {
                val moved = move(r.name)
                val spelled = Spelling.spell(moved, chord.degreeTokens())
                if (spelled.respelledFrom != null) theoretical = true
                ChordRoot.Name(spelled.root)
            }
            else -> r
        }
        val poly = chord.poly?.let { p -> chord(p, interval).also { if (it.theoretical) theoretical = true }.chord }
        val moved = chord.copy(root = root, bass = chord.bass?.let(::move), poly = poly, typed = null)
        return Transposed(moved, theoretical)
    }
}
