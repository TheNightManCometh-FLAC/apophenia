package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.interval.Interval
import com.betterappmafia.apophenia.core.interval.plus
import com.betterappmafia.apophenia.core.interval.minus
import com.betterappmafia.apophenia.core.pitch.Letter
import com.betterappmafia.apophenia.core.pitch.SpelledPitch
import com.betterappmafia.apophenia.core.spelling.Spelling

/**
 * Where things go on a five-line staff (plan §10.5, the notation view). Positions are **steps** counted from the
 * bottom line: lines are 0, 2, 4, 6, 8, spaces 1, 3, 5, 7; below 0 and above 8 need ledger lines. Everything is
 * computed from spelled pitches and the clef, never looked up by MIDI number, so B♯ and C sit on different steps.
 */
object Notation {
    /** Diatonic number of a pitch: octave × 7 + letter (C = 0). */
    fun diatonic(p: SpelledPitch): Int = p.octave * 7 + p.letter.ordinal

    /** The pitch on the bottom line: treble E4, bass G2, alto F3, tenor D3; the 8vb treble reads as treble. */
    fun bottomLine(clef: Clef): Int = when (clef) {
        Clef.TREBLE, Clef.TREBLE_8VB, Clef.PERCUSSION -> 4 * 7 + 2
        Clef.BASS -> 2 * 7 + 4
        Clef.ALTO -> 3 * 7 + 3
        Clef.TENOR -> 3 * 7 + 1
    }

    /**
     * The pitch as written on the part's staff: an instrument's written pitch (guitar and bass an octave above
     * sounding); without one, a treble-8vb staff is written an octave up too.
     */
    fun written(part: Part, sounding: SpelledPitch, clef: Clef = part.clef): SpelledPitch = when {
        part.instrument != null -> part.instrument.written(sounding)
        clef == Clef.TREBLE_8VB -> sounding + Interval(8, 12)
        else -> sounding
    }

    /**
     * Events of the first voice under an 8va/8vb/15ma/15mb line, as (bar, index) → the octaves their notes are
     * written below sounding (8va → 1: the line says play an octave higher than written).
     */
    fun ottavas(part: Part): Map<Pair<Int, Int>, Int> {
        val flat = part.bars.flatMapIndexed { b, bar -> bar.voices[0].indices.map { b to it } }
        val out = HashMap<Pair<Int, Int>, Int>()
        for ((k, pos) in flat.withIndex()) {
            val e = part.bars[pos.first].voices[0][pos.second]
            for ((span, n) in e.spans) if (span.octaves != 0) for (j in k until minOf(k + n, flat.size)) out[flat[j]] = span.octaves
        }
        return out
    }

    /** [p] moved [by] steps along the staff, taking the key signature's alteration for its new letter. */
    fun stepped(p: SpelledPitch, by: Int, fifths: Int): SpelledPitch {
        val d = diatonic(p) + by
        val letter = Letter.entries[Math.floorMod(d, 7)]
        return SpelledPitch(letter, signatureAlters(fifths)[letter] ?: 0, Math.floorDiv(d, 7))
    }

    fun step(clef: Clef, written: SpelledPitch): Int = diatonic(written) - bottomLine(clef)

    /** The ledger lines a note on [step] needs: every line step beyond the staff, out to the note. */
    fun ledgers(step: Int): List<Int> = when {
        step <= -2 -> (-2 downTo step).filter { it % 2 == 0 }
        step >= 10 -> (10..step).filter { it % 2 == 0 }
        else -> emptyList()
    }

    /** Single notes and chords: stem up when the note furthest from the middle line is below it, else down. */
    fun stemUp(steps: List<Int>): Boolean {
        if (steps.isEmpty()) return true
        val hi = steps.max() - 4; val lo = 4 - steps.min()
        return lo > hi   // a note on the middle line, or a chord balanced across it, takes its stem down
    }

    private val MODES = listOf("major", "dorian", "phrygian", "lydian", "mixolydian", "aeolian", "locrian")
    private val MODE_OFFSETS = listOf(0, 2, 4, 5, 7, 9, 11)
    private val LETTER_FIFTHS = mapOf(Letter.C to 0, Letter.G to 1, Letter.D to 2, Letter.A to 3, Letter.E to 4, Letter.B to 5, Letter.F to -1)

    /**
     * The key signature as sharps (+) or flats (−). A diatonic mode takes its parent major's (D Dorian has none,
     * F♯ Phrygian two sharps); any other scale takes the major or natural minor of its tonic by its third.
     * Keys past seven are written with no signature and their accidentals in the bars [Claude].
     */
    fun fifths(key: KeyMode?): Int {
        if (key == null) return 0
        val mode = MODES.indexOf(key.scaleId).takeIf { it >= 0 } ?: run {
            val notes = runCatching { Spelling.spell(key.tonic, key.scale.degrees).notes }.getOrNull()
            val third = notes?.firstOrNull { Math.floorMod(it.letter.ordinal - key.tonic.letter.ordinal, 7) == 2 }
            val minor = third != null && Math.floorMod(third.pitchClass - key.tonic.pitchClass, 12) == 3
            if (minor) 5 else 0
        }
        val parentLetter = Letter.entries[Math.floorMod(key.tonic.letter.ordinal - mode, 7)]
        val pc = Math.floorMod(key.tonic.pitchClass - MODE_OFFSETS[mode], 12)
        var alter = pc - parentLetter.natural
        while (alter > 6) alter -= 12
        while (alter < -6) alter += 12
        val f = LETTER_FIFTHS.getValue(parentLetter) + 7 * alter
        return if (f in -7..7) f else 0
    }

    private val SHARP_ORDER = listOf(Letter.F, Letter.C, Letter.G, Letter.D, Letter.A, Letter.E, Letter.B)
    private val FLAT_ORDER = SHARP_ORDER.reversed()
    // Treble positions, the standard pattern: sharps F5 C5 G5 D5 A4 E5 B4, flats B4 E5 A4 D5 G4 C5 F4.
    private val TREBLE_SHARPS = listOf(8, 5, 9, 6, 3, 7, 4)
    private val TREBLE_FLATS = listOf(4, 7, 3, 6, 2, 5, 1)

    /**
     * The signature's accidentals in order with their steps: the same shape on every clef, a line lower in bass
     * clef and a step lower in alto. Tenor clef's sharps use a different shape and aren't drawn yet [Claude].
     */
    fun signature(clef: Clef, fifths: Int): List<Pair<Int, Int>> {
        val shift = when (clef) { Clef.BASS -> -2; Clef.ALTO -> -1; Clef.TENOR -> return emptyList(); else -> 0 }
        return if (fifths >= 0) (0 until fifths).map { 1 to TREBLE_SHARPS[it] + shift }
        else (0 until -fifths).map { -1 to TREBLE_FLATS[it] + shift }
    }

    /** The alteration each letter has in the signature. */
    fun signatureAlters(fifths: Int): Map<Letter, Int> =
        if (fifths >= 0) SHARP_ORDER.take(fifths).associateWith { 1 } else FLAT_ORDER.take(-fifths).associateWith { -1 }

    /**
     * The accidental to print on each note of a bar (per event, per note): null for none, else the alteration
     * (0 = natural). An accidental lasts to the end of the bar on that letter and octave; a note tied in from the
     * last bar shows none.
     */
    fun accidentals(events: List<Pair<List<SpelledPitch>, List<Boolean>>>, fifths: Int): List<List<Int?>> {
        val sig = signatureAlters(fifths)
        val now = HashMap<Pair<Letter, Int>, Int>()
        return events.map { (pitches, tiedIn) ->
            pitches.mapIndexed { i, p ->
                val key = p.letter to p.octave
                val current = now[key] ?: sig[p.letter] ?: 0
                now[key] = p.alter
                if (tiedIn.getOrElse(i) { false } || p.alter == current) null else p.alter
            }
        }
    }

    /**
     * The sounding pitch of a note put on [step] of [part]'s staff: the line's letter and octave, altered as the
     * key signature says, brought down an octave when the part is written above sounding.
     */
    fun pitchAt(part: Part, step: Int, fifths: Int, clef: Clef = part.clef): SpelledPitch {
        val d = bottomLine(clef) + step
        val letter = Letter.entries[Math.floorMod(d, 7)]
        val written = SpelledPitch(letter, signatureAlters(fifths)[letter] ?: 0, Math.floorDiv(d, 7))
        return when {
            part.instrument != null -> part.instrument.sounding(written)
            clef == Clef.TREBLE_8VB -> written - Interval(8, 12)
            else -> written
        }
    }
}
