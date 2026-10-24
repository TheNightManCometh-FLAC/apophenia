package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.analysis.BasicPitch
import com.betterappmafia.apophenia.core.rhythm.Rational

/** Where things are in time in a score: each event's start (for the playback cursor) and the notes to hear. */
object ScoreTime {
    /** An event's place: bar, voice, index in the voice, and its start and end in seconds. */
    data class Slot(val bar: Int, val voice: Int, val index: Int, val start: Double, val end: Double)

    /**
     * The bars in the order they play: end repeats go back to the last start repeat (or the top) until played
     * [Bar.times] times, volta bars play only on their pass, D.C. and D.S. go back once to the top or the segno
     * (repeats are not taken again after a jump, and only the last ending of each volta group plays), "al Fine"
     * stops at Fine, "al Coda" leaves at To Coda for the Coda bar. Capped so a malformed score can't loop forever.
     */
    fun order(part: Part): List<Int> {
        val bars = part.bars
        val out = ArrayList<Int>()
        // Each volta bar's group (a run of bars with endings) and that group's last ending number.
        val lastEnding = IntArray(bars.size)
        run {
            var i = 0
            while (i < bars.size) {
                if (bars[i].volta.isEmpty()) { i++; continue }
                var j = i; var top = 0
                while (j < bars.size && bars[j].volta.isNotEmpty()) { top = maxOf(top, bars[j].volta.max()); j++ }
                for (k in i until j) lastEnding[k] = top
                i = j
            }
        }
        val done = IntArray(bars.size)
        var start = 0; var pass = 1; var jumped = false; var toCoda = false; var toFine = false
        var i = 0
        while (i < bars.size && out.size < 10_000) {
            val b = bars[i]
            if (b.repeatStart && !jumped && pass == 1) start = i
            if (b.volta.isNotEmpty()) {
                val plays = if (jumped) lastEnding[i] in b.volta else pass in b.volta
                if (!plays) { i++; continue }
            } else if (i > 0 && bars[i - 1].volta.isNotEmpty() && !jumped) { pass = 1; start = i }
            out += i
            if (toFine && Jump.FINE in b.jumps) break
            if (toCoda && Jump.TO_CODA in b.jumps) {
                val coda = bars.indices.firstOrNull { it > i && Jump.CODA in bars[it].jumps } ?: break
                toCoda = false; i = coda; continue
            }
            if (b.end == Barline.REPEAT && !jumped && done[i] < b.times - 1) {
                done[i]++; pass = done[i] + 1; i = start; continue
            }
            if (b.end == Barline.REPEAT && !jumped) { pass = 1; start = i + 1 }
            val jump = b.jumps.firstOrNull { !it.atStart && it != Jump.FINE && it != Jump.TO_CODA }
            if (jump != null && !jumped) {
                jumped = true
                toFine = jump == Jump.DC_AL_FINE || jump == Jump.DS_AL_FINE
                toCoda = jump == Jump.DC_AL_CODA || jump == Jump.DS_AL_CODA
                val ds = jump in setOf(Jump.DS, Jump.DS_AL_FINE, Jump.DS_AL_CODA)
                i = if (ds) bars.indices.firstOrNull { Jump.SEGNO in bars[it].jumps } ?: 0 else 0
                continue
            }
            i++
        }
        return out
    }

    /** Every event of [part] with its time, in play order (a repeated bar appears once per pass), each bar at its own meter and tempo. */
    fun slots(score: Score, part: Part): List<Slot> {
        val out = ArrayList<Slot>()
        var barStart = 0.0
        for (b in order(part)) {
            val bar = part.bars[b]
            val tempo = score.tempoAt(b)
            for ((v, events) in bar.voices.withIndex()) {
                var at = Rational.ZERO
                for ((i, e) in events.withIndex()) {
                    val end = at + e.length
                    out += Slot(b, v, i, barStart + tempo.secondsFor(at), barStart + tempo.secondsFor(end))
                    at = end
                }
            }
            barStart += tempo.secondsFor(score.meterAt(b).barLength)
        }
        return out.sortedWith(compareBy({ it.start }, { it.voice }))
    }

    /** The notes as they sound, in play order: ties joined into one note, each with its MIDI number. */
    fun notes(score: Score, part: Part): List<BasicPitch.TimedNote> {
        val out = ArrayList<BasicPitch.TimedNote>()
        // Slots in play order per voice, so a repeated bar's events come round again.
        val byVoice = slots(score, part).groupBy { it.voice }.mapValues { (_, s) -> s.sortedBy { it.start } }
        for ((v, slots) in byVoice) {
            val held = HashMap<Int, Double>()   // MIDI → start of a tie still sounding
            for (slot in slots) {
                val e = part.bars[slot.bar].voices[v][slot.index]
                for (n in e.notes) {
                    val start = held.remove(n.pitch.midi) ?: slot.start
                    if (n.tie) held[n.pitch.midi] = start
                    else out += BasicPitch.TimedNote(start, slot.end, n.pitch.midi, if (Effect.GHOST in n.effects) 0.35 else 0.8, emptyList())
                }
            }
            // A tie left hanging at the end still sounds.
            val end = slots.lastOrNull()?.end ?: 0.0
            for ((midi, start) in held) out += BasicPitch.TimedNote(start, end, midi, 0.8, emptyList())
        }
        return out.sortedBy { it.start }
    }

    /** Seconds the whole part lasts, repeats included. */
    fun seconds(score: Score, part: Part): Double = order(part).sumOf { score.tempoAt(it).secondsFor(score.meterAt(it).barLength) }
}

/**
 * Plain-text tab (the Board's glimpse, and a start on tab export): one line per string, highest string first,
 * each event a column as wide as its widest fret, `-` between, `|` at barlines; a tied-on fret in brackets.
 */
object TabText {
    fun render(part: Part, fromBar: Int = 0, bars: Int = part.bars.size): List<String> {
        val inst = part.instrument ?: return emptyList()
        val n = inst.tuning.stringCount
        val names = (1..n).map { inst.tuning.open(it).name.toString() }
        val pad = names.maxOf { it.length }
        val lines = Array(n) { StringBuilder(names[it].padEnd(pad)).append("|") }
        val tiedIn = HashSet<Int>()
        for (bar in part.bars.drop(fromBar).take(bars)) {
            for (e in bar.voices[0]) {
                val cells = Array(n) { "" }
                for (note in e.notes) {
                    val s = note.string ?: continue; val f = note.fret ?: continue
                    cells[s - 1] = if (s in tiedIn) "($f)" else f.toString()
                }
                tiedIn.clear()
                for (note in e.notes) if (note.tie) note.string?.let { tiedIn += it }
                val w = maxOf(1, cells.maxOf { it.length })
                for (s in 0 until n) lines[s].append('-').append(cells[s].padEnd(w, '-'))
            }
            for (s in 0 until n) lines[s].append("-|")
        }
        return lines.map { it.toString() }
    }
}
