package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.instrument.Tuning
import com.betterappmafia.apophenia.core.sound.PlayNote

/**
 * Chord shapes on any tuning, found by search rather than from a chord book (as Chordopia and Fabulus's Guitar Chord
 * Finder do): every way to fret the chord inside a four-fret window, with open strings allowed anywhere.
 * [Claude] rules: the bass is the chord's root; every essential tone sounds; at least three strings (two for a power
 * chord) sound; at most four fingers, a barre at the lowest fret counting as one; at most one muted string between
 * sounding ones. Ranked by muted strings, missing optional tones, stretch and height up the neck.
 */
data class Voicing(
    /** One entry per string in string order (lowest-positioned string first): the fret, or null when muted. */
    val frets: List<Int?>,
    /** The sounding MIDI notes, low to high in string order. */
    val midis: List<Int>,
) {
    /** The usual chart form, lowest string first: x-7-6-7-8-x. */
    val text: String get() = frets.joinToString("-") { it?.toString() ?: "x" }
    val lowest: Int get() = frets.filterNotNull().filter { it > 0 }.minOrNull() ?: 0
}

object Voicings {
    const val WINDOW = 4

    /**
     * Shapes for a chord whose tones are [pcs] (pitch classes, root first) on [tuning] with a [capo], up to [maxFret].
     * [optional] tones may be left out. Returns at most [limit], best first.
     */
    fun find(tuning: Tuning, pcs: List<Int>, optional: Set<Int>, capo: Int = 0, maxFret: Int = 15, limit: Int = 80): List<Voicing> {
        val open = tuning.strings.map { it.midi + capo }
        val n = open.size
        val tones = pcs.toSet()
        val root = pcs.first()
        val essential = tones - optional
        val minStrings = if (tones.size <= 2) 2 else 3
        val seen = HashSet<List<Int?>>()
        val scored = ArrayList<Pair<Double, Voicing>>()
        for (start in 1..maxOf(1, maxFret - WINDOW + 1)) {
            val choices = open.map { o ->
                buildList<Int?> {
                    add(null)
                    if (Math.floorMod(o, 12) in tones) add(0)
                    for (f in start until start + WINDOW) if (f <= maxFret && Math.floorMod(o + f, 12) in tones) add(f)
                }
            }
            val pick = arrayOfNulls<Int>(n)
            fun walk(s: Int) {
                if (s == n) {
                    val frets = pick.toList()
                    if (frets in seen) return
                    score(frets, open, root, tones, essential, minStrings)?.let { sc -> seen += frets; scored += sc to Voicing(frets, frets.indices.mapNotNull { i -> frets[i]?.let { open[i] + it } }) }
                    return
                }
                for (c in choices[s]) { pick[s] = c; walk(s + 1) }
            }
            walk(0)
        }
        return scored.sortedBy { it.first }.take(limit).map { it.second }
    }

    private fun score(frets: List<Int?>, open: List<Int>, root: Int, tones: Set<Int>, essential: Set<Int>, minStrings: Int): Double? {
        val sounding = frets.indices.filter { frets[it] != null }
        if (sounding.size < minStrings) return null
        val midis = sounding.map { open[it] + frets[it]!! }
        if (Math.floorMod(midis.min(), 12) != root) return null
        val heard = midis.map { Math.floorMod(it, 12) }.toSet()
        if (!heard.containsAll(essential)) return null
        val first = sounding.first()
        val last = sounding.last()
        val interiorMutes = (first..last).count { frets[it] == null }
        if (interiorMutes > 1) return null
        val fretted = sounding.mapNotNull { frets[it]?.takeIf { f -> f > 0 } }
        if (fretted.isNotEmpty()) {
            val low = fretted.min()
            val atLow = fretted.count { it == low }
            val fingers = fretted.size - (if (atLow > 1) atLow - 1 else 0)
            if (fingers > 4) return null
            if (fretted.max() - low >= WINDOW) return null
        }
        val muted = frets.size - sounding.size
        val missing = (tones - heard).size
        val stretch = if (fretted.isEmpty()) 0 else fretted.max() - fretted.min()
        val height = fretted.minOrNull() ?: 0
        return muted * 1.0 + interiorMutes * 3.0 + missing * 2.5 + stretch * 0.6 + height * 0.15
    }
}

/** The notes the Reference plays for "hear it": block chords, arpeggios, scales and intervals on the piano. */
object RefSound {
    const val STEP = 0.32
    const val VELOCITY = 80

    /** [semitones] above a root placed so the root sits between [low] and [low] + 11. */
    fun midis(rootPc: Int, semitones: List<Int>, low: Int): List<Int> {
        val r = low + Math.floorMod(rootPc - low, 12)
        return semitones.map { r + it }
    }

    fun block(midis: List<Int>, at: Double = 0.0, length: Double = 1.6) = midis.map { PlayNote(at, at + length, it, VELOCITY) }

    fun upDown(midis: List<Int>, at: Double = 0.0, down: Boolean = true): List<PlayNote> {
        val path = if (down) midis + midis.reversed().drop(1) else midis
        return path.mapIndexed { i, m -> PlayNote(at + i * STEP, at + i * STEP + STEP * 0.95, m, VELOCITY) }
    }

    /** A chord, then its arpeggio. */
    fun chord(midis: List<Int>) = block(midis) + upDown(midis, at = 2.0, down = false).let { a -> a + block(midis, at = 2.0 + a.size * STEP + 0.2) }

    /** The scale up an octave and back down. */
    fun scale(midis: List<Int>) = upDown(midis + (midis.first() + 12))

    /** An interval melodic up, melodic down, then together. */
    fun interval(lower: Int, upper: Int) = listOf(
        PlayNote(0.0, 0.7, lower, VELOCITY), PlayNote(0.75, 1.45, upper, VELOCITY),
        PlayNote(2.0, 2.7, upper, VELOCITY), PlayNote(2.75, 3.45, lower, VELOCITY),
        PlayNote(4.0, 5.6, lower, VELOCITY), PlayNote(4.0, 5.6, upper, VELOCITY),
    )

    /** [a] then [b] after a short gap: the A/B comparison. */
    fun versus(a: List<PlayNote>, b: List<PlayNote>): List<PlayNote> {
        val off = (a.maxOfOrNull { it.end } ?: 0.0) + 0.6
        return a + b.map { it.copy(start = it.start + off, end = it.end + off) }
    }

    fun length(notes: List<PlayNote>) = (notes.maxOfOrNull { it.end } ?: 0.0) + 1.3
}
