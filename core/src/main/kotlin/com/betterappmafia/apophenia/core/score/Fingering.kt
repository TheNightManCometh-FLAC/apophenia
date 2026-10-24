package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.instrument.Instrument
import kotlin.math.abs

/**
 * The fingering chooser (plan §6.4, §10.3): a string and fret for every note of a fretted part, chosen for
 * the whole passage at once so the hand moves as little as it has to. Every position stays changeable; this
 * is only the first guess.
 *
 * [Claude] The cost, per chord or note: a little for each fret up the neck (low positions are easier to read
 * and to reach), more for a wide stretch; and between one and the next, the distance the hand moves (open
 * strings don't fix the hand anywhere). A chord's notes go on different strings within a five-fret span;
 * the best path is found by dynamic programming over the passage.
 */
object Fingering {
    data class Place(val string: Int, val fret: Int)

    /** A fingering for one chord or note: one place per note, in the order given. */
    private data class Shape(val places: List<Place>) {
        val fretted = places.filter { it.fret > 0 }.map { it.fret }
        /** Where the hand is, or null when every string is open. */
        val position: Double? = if (fretted.isEmpty()) null else fretted.min().toDouble()
        val span = if (fretted.isEmpty()) 0 else fretted.max() - fretted.min()
        val cost = places.sumOf { it.fret } * 0.04 + span * 0.25
    }

    /** Every place [midi] can be played on [instrument], highest string first. */
    fun places(instrument: Instrument, midi: Int): List<Place> {
        val n = instrument.tuning.stringCount
        return (1..n).mapNotNull { s ->
            val fret = midi - instrument.tuning.open(s).midi - instrument.capo
            if (fret in 0..instrument.frets - instrument.capo) Place(s, fret) else null
        }
    }

    /**
     * Places for each chord or note in [passage] (MIDI numbers per event). A note the instrument can't reach
     * gets no place (null); a chord with more notes than can be fingered drops to its playable notes.
     */
    fun choose(instrument: Instrument, passage: List<List<Int>>): List<List<Place?>> {
        val options = passage.map { shapes(instrument, it) }
        val n = options.size
        if (n == 0) return emptyList()
        val cost = Array(n) { DoubleArray(options[it].size) }
        val from = Array(n) { IntArray(options[it].size) { -1 } }
        // The last real hand position before each event, carried through open-string shapes.
        val pos = Array(n) { arrayOfNulls<Double>(options[it].size) }
        for ((j, s) in options[0].withIndex()) { cost[0][j] = s.cost; pos[0][j] = s.position }
        for (i in 1 until n) {
            for ((j, s) in options[i].withIndex()) {
                var best = Double.MAX_VALUE; var arg = 0
                for ((k, prev) in options[i - 1].withIndex()) {
                    val p = pos[i - 1][k]
                    val move = if (p == null || s.position == null) 0.0 else abs(s.position - p)
                    val c = cost[i - 1][k] + move + s.cost
                    if (c < best) { best = c; arg = k }
                }
                cost[i][j] = best; from[i][j] = arg
                pos[i][j] = s.position ?: pos[i - 1][arg]
            }
        }
        val out = ArrayList<List<Place?>>(n)
        var j = cost[n - 1].indices.minByOrNull { cost[n - 1][it] } ?: 0
        for (i in n - 1 downTo 0) {
            val shape = options[i][j]
            val midis = passage[i]
            out += midis.map { m -> shape.places.firstOrNull { p -> instrument.midiAt(p.string, p.fret) == m } }
            j = if (i > 0) from[i][j] else 0
        }
        return out.reversed()
    }

    private fun shapes(instrument: Instrument, midis: List<Int>): List<Shape> {
        val choices = midis.distinct().map { places(instrument, it) }.filter { it.isNotEmpty() }
        if (choices.isEmpty()) return listOf(Shape(emptyList()))
        val out = ArrayList<Shape>()
        fun go(i: Int, used: Set<Int>, acc: List<Place>) {
            if (out.size >= 400) return
            if (i == choices.size) {
                val s = Shape(acc)
                if (s.span <= 4) out += s
                return
            }
            for (p in choices[i]) if (p.string !in used) go(i + 1, used + p.string, acc + p)
        }
        go(0, emptySet(), emptyList())
        if (out.isNotEmpty()) return out
        // Unplayable as a whole: keep the lowest notes that fit, one per string.
        return listOf(Shape(choices.take(instrument.tuning.stringCount).mapIndexedNotNull { i, c -> c.getOrNull(minOf(i, c.size - 1)) }.distinctBy { it.string }))
    }
}
