package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.rhythm.TICKS_PER_WHOLE
import com.betterappmafia.apophenia.core.rhythm.Tempo
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import com.betterappmafia.apophenia.core.sheet.ChordChart
import com.betterappmafia.apophenia.core.sheet.ChordSound
import com.betterappmafia.apophenia.core.tools.KeySpeller

/**
 * The FL Studio package (plan §11): what goes in it besides audio. A chord chart becomes a chord track (a score
 * whose notes are each chord's bass and voicing, struck where it changes and again at each barline it holds over,
 * with the chord symbols and section labels kept), and the manifest says what every file is and where it goes.
 *
 * [Claude] The stack has no meter of its own, so stem positions are given in seconds and as bar.beat at the
 * stack's tempo in 4/4, labelled so.
 */
object FlPackage {
    /** A chord chart as a one-part score at [bpm] (quarter notes a minute): chords as notes, symbols on them. */
    fun chartScore(title: String, body: String, key: KeyMode?, bpm: Double): Score {
        val chart = ChordChart.parse(body)
        val speller = KeySpeller(key ?: KeyMode.parseOrNull("C major")!!)
        val bars = ArrayList<Bar>()
        var previous: String? = null
        var lastMeter: TimeSignature? = null
        for (section in chart.sections) for ((bi, bar) in section.bars.withIndex()) {
            val meter = bar.meter
            val starts = meter.beatStarts.map { (it * TICKS_PER_WHOLE).num }
            val lengths = meter.beatLengths.map { (it * TICKS_PER_WHOLE).num }
            // Spans of one chord: (start tick, length, symbol or null for silence).
            val spans = ArrayList<Triple<Long, Long, String?>>()
            for ((i, beat) in bar.beats.take(meter.beatsPerBar).withIndex()) {
                val sym = if (beat == ChordChart.HOLD) previous else beat.takeUnless { it.equals("N.C.", true) || it.equals("NC", true) }
                if (beat != ChordChart.HOLD) previous = sym
                if (i > 0 && beat == ChordChart.HOLD) spans[spans.size - 1] = spans.last().let { it.copy(second = it.second + lengths[i]) }
                else spans += Triple(starts[i], lengths[i], sym)
            }
            // A short bar is filled with rests so every bar is whole.
            val used = spans.sumOf { it.second }
            if (used < meter.barTicks) spans += Triple(used, meter.barTicks - used, null)
            val events = ArrayList<Event>()
            for ((start, length, sym) in spans) {
                val notes = sym?.let { ChordSound.midis(it) }.orEmpty().distinct().sorted().map { Note(speller.spell(it)) }
                val pieces = FromNotes.durations(start, length, meter, 4)
                pieces.forEachIndexed { k, d ->
                    val last = k == pieces.lastIndex
                    events += Event(d, notes.map { it.copy(tie = !last) }, chord = if (k == 0 && sym != null) sym else null)
                }
            }
            bars += Bar(listOf(events), rehearsal = if (bi == 0) section.label else null, meter = meter.takeIf { lastMeter != null && it != lastMeter })
            lastMeter = meter
        }
        val first = chart.bars.firstOrNull()?.meter ?: ChordChart.DEFAULT_METER
        val clean = bars.mapIndexed { i, b -> if (i == 0) b.copy(meter = null) else b }
        return Score(title, first, key, Tempo(bpm), listOf(Part("Chords", Clef.BASS, bars = clean.ifEmpty { listOf(Bar()) })))
    }

    /** One stack layer as the manifest lists it. */
    data class Layer(
        val number: Int, val title: String, val take: String?, val mode: String,
        /** Stack frames where it starts; take frames played (to null = the end). */
        val start: Long, val from: Long, val to: Long?,
        val gainDb: Double, val muted: Boolean, val conform: String?,
        val stem: String?, val original: String?,
    )

    data class Info(
        val project: String, val stack: String?, val made: String, val bpm: Double?, val key: KeyMode?, val rate: Int,
        val layers: List<Layer>, val mix: String?, val midi: List<Pair<String, String>>, val otherTakes: List<String>,
    )

    /** "1:02.345" from frames at [rate]. */
    fun clock(frames: Long, rate: Int): String {
        val ms = Math.round(frames * 1000.0 / rate)
        val sign = if (ms < 0) "-" else ""
        val a = Math.abs(ms)
        return "%s%d:%02d.%03d".format(java.util.Locale.ROOT, sign, a / 60_000, a / 1000 % 60, a % 1000)
    }

    /** "bar 3 beat 2.50": where [frames] falls at [bpm] in 4/4, counting from bar 1 beat 1. */
    fun barBeat(frames: Long, rate: Int, bpm: Double): String {
        val beats = frames.toDouble() / rate * bpm / 60.0
        val bar = Math.floorDiv(Math.floor(beats).toLong(), 4L) + 1
        val beat = beats - (bar - 1) * 4 + 1
        val b = if (Math.abs(beat - Math.round(beat)) < 0.005) Math.round(beat).toString() else "%.2f".format(java.util.Locale.ROOT, beat)
        return "bar $bar beat $b"
    }

    private fun bpmText(b: Double) = if (Math.abs(b - Math.round(b)) < 0.005) Math.round(b).toString() else "%.2f".format(java.util.Locale.ROOT, b)

    fun manifest(i: Info): String = buildString {
        appendLine("APOPHENIA · FL STUDIO PACKAGE")
        appendLine("${i.project}${i.stack?.let { " · $it" } ?: ""} · made ${i.made}")
        appendLine("Tempo: ${i.bpm?.let { bpmText(it) + " BPM" } ?: "none set"} · Key: ${i.key?.display ?: "none set"} · Audio: ${i.rate / 1000} kHz, 24-bit mono WAV")
        appendLine()
        appendLine("HOW IT GOES TOGETHER")
        var step = 1
        if (i.bpm != null) appendLine("${step++}. Set the FL Studio project tempo to ${bpmText(i.bpm)} BPM.")
        if (i.layers.any { it.stem != null } || i.mix != null)
            appendLine("${step++}. Stems/: drop every file onto the Playlist at bar 1. Each stem starts at the stack's start and runs the whole stack (loops written out, conformed as the stack plays them, at the layer's gain), so they line up. Mix.wav is the whole stack.")
        if (i.midi.isNotEmpty())
            appendLine("${step++}. MIDI/: File > Import > MIDI file, one file at a time. Each written scrap is its own file with its own tempo, meter and rehearsal marks (also listed below, in case they don't come in).")
        if (i.layers.any { it.original != null } || i.otherTakes.isNotEmpty())
            appendLine("${step++}. Takes/: the original recordings, never edited. Where each one sits in the stack is listed below.")
        if (i.layers.isNotEmpty()) {
            appendLine()
            appendLine("STACK LAYERS (positions in seconds, and as bars of 4/4 at the stack's tempo)")
            for (l in i.layers) {
                append("%02d ".format(l.number)).append(l.title).append(l.take?.let { " (take $it)" } ?: "").append(" · ").append(l.mode)
                if (l.muted) append(" · muted in the stack")
                appendLine()
                append("   starts ").append(clock(l.start, i.rate))
                if (i.bpm != null) append(" (").append(barBeat(l.start, i.rate, i.bpm)).append(")")
                append(" · plays the take from ").append(clock(l.from, i.rate)).append(" to ").append(l.to?.let { clock(it, i.rate) } ?: "the end")
                append(" · gain ").append("%+.1f dB".format(java.util.Locale.ROOT, l.gainDb))
                appendLine()
                l.conform?.let { appendLine("   conformed: $it") }
                l.stem?.let { appendLine("   stem: $it") }
                l.original?.let { appendLine("   original: $it") }
            }
        }
        i.mix?.let { appendLine(); appendLine("MIX: $it") }
        if (i.midi.isNotEmpty()) {
            appendLine()
            appendLine("MIDI")
            for ((file, about) in i.midi) appendLine("$file · $about")
        }
        if (i.otherTakes.isNotEmpty()) {
            appendLine()
            appendLine("OTHER RECORDINGS ON THE BOARD (not in the stack)")
            for (t in i.otherTakes) appendLine(t)
        }
    }

    /** One line about a score for the manifest: its parts, tempo, meter and changes, and rehearsal marks. */
    fun about(s: Score): String {
        val bars = s.parts.firstOrNull()?.bars.orEmpty()
        val meters = listOf(s.meter) + bars.mapNotNull { it.meter }
        val tempos = listOf(s.tempo) + bars.mapNotNull { it.tempo }
        val marks = bars.withIndex().mapNotNull { (n, b) -> b.rehearsal?.let { "$it at bar ${n + 1}" } }
        return buildString {
            append(s.parts.size).append(if (s.parts.size == 1) " track" else " tracks").append(" (").append(s.parts.joinToString(", ") { it.name.ifBlank { "Part" } }).append(")")
            append(" · ").append(tempos.distinct().joinToString(" → ") { bpmText(it.quarterNotesPerMinute) + " BPM" })
            append(" · ").append(meters.distinct().joinToString(" → ") { it.toString() })
            s.key?.let { append(" · ").append(it.display) }
            if (marks.isNotEmpty()) append(" · marks: ").append(marks.joinToString(", "))
        }
    }

    /** A file name safe on Windows and in a zip: no path or reserved characters, not blank. */
    fun fileName(s: String): String = s.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ").replace(Regex("\\s+"), " ").trim().trimEnd('.').ifBlank { "Untitled" }.take(80)
}
