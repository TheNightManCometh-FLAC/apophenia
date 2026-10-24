package com.betterappmafia.apophenia.core.sheet

import com.betterappmafia.apophenia.core.chord.Chord
import com.betterappmafia.apophenia.core.chord.ChordDialect
import com.betterappmafia.apophenia.core.chord.ChordKind
import com.betterappmafia.apophenia.core.chord.ChordParser
import com.betterappmafia.apophenia.core.chord.ChordRoot
import com.betterappmafia.apophenia.core.rhythm.Tempo
import com.betterappmafia.apophenia.core.score.Part
import com.betterappmafia.apophenia.core.score.Score
import com.betterappmafia.apophenia.core.score.ScoreTime
import com.betterappmafia.apophenia.core.sound.PlayNote

/**
 * Chord symbols as the piano plays them (plan §4.4: the chord chart played on piano): the bass (the slash note,
 * else the root) in the octave below C3, and the chord tones close above the root, the root between G3 and F♯4;
 * a polychord's upper chord an octave above its root's place. Read by [ChordParser]; a 13th chord leaves out the
 * natural 11 over its major 3rd and an 11th chord its 3rd, as the catalog voices them. A symbol that can't be read,
 * and N.C., are silent.
 */
object ChordSound {
    /** Root pitch class, semitones above it, and the bass pitch class; null for N.C. or a symbol it can't read. */
    fun read(symbol: String, dialect: ChordDialect = ChordDialect()): Triple<Int, List<Int>, Int>? {
        val c = ChordParser.parse(symbol, dialect).chord ?: return null
        if (c.isNoChord) return null
        val root = (c.root as? ChordRoot.Name)?.name ?: return null
        val tones = tonesOf(c).toMutableList()
        c.poly?.let { up ->
            val upRoot = (up.root as? ChordRoot.Name)?.name ?: return@let
            val offset = 12 + Math.floorMod(upRoot.pitchClass - root.pitchClass, 12)
            tones += tonesOf(up).map { it + offset }
        }
        return Triple(root.pitchClass, tones.distinct().sorted(), (c.bass ?: root).pitchClass)
    }

    private fun tonesOf(c: Chord): List<Int> {
        var t = c.degreeTokens().map { it.interval.semitones }
        if (c.kind in setOf(ChordKind.DOMINANT_13TH, ChordKind.MAJOR_13TH) && 4 in t) t = t - 17
        if (c.kind == ChordKind.DOMINANT_11TH) t = t - 4
        return t
    }

    /** The MIDI notes for [symbol]: bass first, then the chord; empty when silent. */
    fun midis(symbol: String, dialect: ChordDialect = ChordDialect()): List<Int> {
        val (root, tones, bass) = read(symbol, dialect) ?: return emptyList()
        val low = 36 + bass                                      // C2..B2
        val base = 55 + Math.floorMod(root - 7, 12)              // G3..F#4
        return listOf(low) + tones.map { base + it }.distinct()
    }

    /** Each chord struck where it changes and again at every barline it holds across; null symbols are silence. */
    private fun strike(changes: List<Pair<Double, String?>>, barStarts: List<Double>, end: Double, velocity: Int, dialect: ChordDialect): List<PlayNote> {
        val out = ArrayList<PlayNote>()
        for ((k, c) in changes.withIndex()) {
            val (t, sym) = c
            val until = changes.getOrNull(k + 1)?.first ?: end
            val ms = sym?.let { midis(it, dialect) } ?: continue
            val hits = listOf(t) + barStarts.filter { it > t + 1e-6 && it < until - 1e-6 }
            for ((i, h) in hits.withIndex()) {
                val off = hits.getOrNull(i + 1) ?: until
                for (m in ms) out += PlayNote(h, off, m, velocity)
            }
        }
        return out
    }

    /** The chord symbols of a score (voice 1, as the chart view shows them), in play order through repeats. */
    fun ofScore(score: Score, part: Part, dialect: ChordDialect = ChordDialect(), velocity: Int = 72): List<PlayNote> {
        val slots = ScoreTime.slots(score, part).filter { it.voice == 0 }
        val changes = slots.mapNotNull { s -> part.bars[s.bar].voices[0][s.index].chord?.let { s.start to it } }
        val barStarts = slots.filter { it.index == 0 }.map { it.start }
        return strike(changes, barStarts, ScoreTime.seconds(score, part), velocity, dialect)
    }

    /** A chord chart (the chord-chart scrap) at [bpm] quarter notes a minute: `/` holds, each bar at its own meter. */
    fun ofChart(chart: ChordChart.Chart, bpm: Double, dialect: ChordDialect = ChordDialect(), velocity: Int = 72): Pair<List<PlayNote>, Double> {
        val tempo = Tempo(bpm)
        val changes = ArrayList<Pair<Double, String?>>()
        val barStarts = ArrayList<Double>()
        var t = 0.0
        for (bar in chart.bars) {
            barStarts += t
            val lengths = bar.meter.beatLengths
            for ((i, beat) in bar.beats.withIndex()) {
                if (beat != ChordChart.HOLD) changes += t to beat
                t += tempo.secondsFor(lengths.getOrElse(i) { lengths.last() })
            }
        }
        return strike(changes, barStarts, t, velocity, dialect) to t
    }
}
