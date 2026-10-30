package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.analysis.BasicPitch
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.instrument.Instrument
import com.betterappmafia.apophenia.core.rhythm.Duration
import com.betterappmafia.apophenia.core.rhythm.NoteValue
import com.betterappmafia.apophenia.core.rhythm.Rational
import com.betterappmafia.apophenia.core.rhythm.TICKS_PER_WHOLE
import com.betterappmafia.apophenia.core.rhythm.Tempo
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import com.betterappmafia.apophenia.core.rhythm.Tuplet
import com.betterappmafia.apophenia.core.tools.KeySpeller
import kotlin.math.roundToLong

/**
 * Transcription → written music (plan §10.3): a take's notes (seconds) put into bars at the recording's tempo
 * and meter, on a grid, spelled in its key, and for a fretted part given strings and frets.
 *
 * [Claude] choices:
 * - One voice: a chord is the notes that start together; each lasts until the next one starts at the latest,
 *   and a gap becomes a rest.
 * - Durations are written the usual way: a note starting on a beat may run to a later beat (or be dotted, like
 *   a dotted quarter on a beat), one starting off the beat is tied at the next beat, and everything is tied over
 *   the barline.
 * - A note before the first downbeat (a pickup) pushes the music on by whole bars, so bar 1 starts with rests.
 */
object FromNotes {
    data class Settings(
        val bpm: Double,
        val meter: TimeSignature = TimeSignature.of(4, 4),
        /** Grid steps per counted beat (the QUANTISE divisions: 4 = sixteenths in 4/4, 3 = triplet eighths). */
        val division: Int = 4,
        /** Where the first downbeat is in the take, in seconds. */
        val origin: Double = 0.0,
        val key: KeyMode? = null,
    )

    /** Length of the counted beat (the metronome's: the denominator, or the dotted beat in compound meters). */
    /**
     * The notes moved onto steady beats at [bpm] (0.9.21): [beats] are the beats found in the take (seconds, from its
     * first downbeat), and a note between two of them keeps its place between them, so a take that speeds up or drags
     * still lands on the grid. Each found beat is one, two or half a beat at [bpm] (whichever fits, within 4%); if
     * none fits, or there are fewer than two beats, the notes come back as they were. Before the first beat and after
     * the last, the nearest gap's spacing carries on. [Claude]
     */
    fun onBeats(notes: List<BasicPitch.TimedNote>, beats: List<Double>, bpm: Double): List<BasicPitch.TimedNote> {
        if (beats.size < 2 || bpm <= 0) return notes
        val gaps = beats.zipWithNext { a, b -> b - a }.sorted()
        val found = gaps[gaps.size / 2]
        val period = 60.0 / bpm
        val per = listOf(1.0, 2.0, 0.5).firstOrNull { kotlin.math.abs(found / (period * it) - 1) < 0.04 } ?: return notes
        val step = period * per
        val b0 = beats[0]
        fun map(t: Double): Double {
            if (t <= beats[0]) return b0 + (t - beats[0]) * step / (beats[1] - beats[0])
            val last = beats.size - 1
            if (t >= beats[last]) return b0 + last * step + (t - beats[last]) * step / (beats[last] - beats[last - 1])
            var i = java.util.Collections.binarySearch(beats, t).let { if (it >= 0) it else -it - 2 }
            i = i.coerceIn(0, last - 1)
            return b0 + (i + (t - beats[i]) / (beats[i + 1] - beats[i])) * step
        }
        return notes.map { it.copy(start = map(it.start), end = maxOf(map(it.start), map(it.end))) }
    }

    fun beatLength(meter: TimeSignature): Rational = if (meter.isCompound) Rational.of(3, meter.denominator) else Rational.of(1, meter.denominator)

    fun build(notes: List<BasicPitch.TimedNote>, settings: Settings, partName: String, instrument: Instrument?, title: String = ""): Score {
        val meter = settings.meter
        val beatTicks = (beatLength(meter) * TICKS_PER_WHOLE).num
        val step = beatTicks / settings.division
        require(step * settings.division == beatTicks) { "a ${settings.division}-step grid doesn't divide the beat" }
        val barTicks = meter.barTicks.toLong()
        val ticksPerSecond = settings.bpm / 60.0 * beatTicks
        fun tick(t: Double) = ((t - settings.origin) * ticksPerSecond / step).roundToLong() * step

        // Attacks: notes that start on the same step make one chord.
        data class Raw(val start: Long, val end: Long, val midi: Int)
        val raw = notes.map { n -> tick(n.start).let { s -> Raw(s, maxOf(s + step, tick(n.end)), n.midi) } }
        val shift = raw.minOfOrNull { it.start }?.let { if (it < 0) ((-it + barTicks - 1) / barTicks) * barTicks else 0L } ?: 0L
        val groups = raw.map { it.copy(start = it.start + shift, end = it.end + shift) }.groupBy { it.start }.toSortedMap()
        val starts = groups.keys.toList()
        data class Attack(val start: Long, val end: Long, val midis: List<Int>)
        val attacks = starts.mapIndexed { i, s ->
            val g = groups.getValue(s)
            val next = starts.getOrNull(i + 1) ?: Long.MAX_VALUE
            Attack(s, minOf(g.maxOf { it.end }, next), g.map { it.midi }.distinct().sorted())
        }

        val speller = KeySpeller(settings.key ?: KeyMode.parseOrNull("C major")!!)
        val placed = if (instrument != null) Fingering.choose(instrument, attacks.map { it.midis }) else attacks.map { a -> a.midis.map { null } }
        val chords: List<List<Note>> = attacks.mapIndexed { i, a ->
            a.midis.mapIndexedNotNull { j, m ->
                val p = placed[i][j]
                if (instrument != null && p == null) null      // out of the instrument's reach
                else Note(speller.spell(m), p?.string, p?.fret)
            }
        }

        // The timeline: (start, end, notes) with rests in the gaps, filled out to whole bars.
        val end = attacks.maxOfOrNull { it.end } ?: 0L
        val total = maxOf(barTicks, ((end + barTicks - 1) / barTicks) * barTicks)
        data class Span(val start: Long, val end: Long, val notes: List<Note>)
        val spans = ArrayList<Span>()
        var at = 0L
        for ((i, a) in attacks.withIndex()) {
            if (a.start > at) spans += Span(at, a.start, emptyList())
            spans += Span(a.start, a.end, chords[i])
            at = a.end
        }
        if (total > at) spans += Span(at, total, emptyList())

        val bars = ArrayList<Bar>()
        var current = ArrayList<Event>()
        var barStart = 0L
        for (sp in spans) {
            var pos = sp.start
            while (pos < sp.end) {
                val barEnd = barStart + barTicks
                val pieceEnd = minOf(sp.end, barEnd)
                val pieces = durations(pos - barStart, pieceEnd - pos, meter, settings.division)
                for ((k, d) in pieces.withIndex()) {
                    val last = k == pieces.lastIndex && pieceEnd == sp.end
                    current += Event(d, if (sp.notes.isEmpty()) emptyList() else sp.notes.map { it.copy(tie = !last) })
                }
                pos = pieceEnd
                if (pos == barEnd) { bars += Bar(listOf(current)); current = ArrayList(); barStart = barEnd }
            }
        }
        if (current.isNotEmpty()) bars += Bar(listOf(current))

        val clef = when {
            instrument != null && instrument.tuning.strings.first().midi < 40 -> Clef.BASS
            instrument != null -> Clef.TREBLE_8VB
            (attacks.flatMap { it.midis }.sorted().let { it.getOrNull(it.size / 2) } ?: 60) < 57 -> Clef.BASS
            else -> Clef.TREBLE
        }
        val tempo = Tempo(settings.bpm, if (meter.isCompound) Duration(NoteValue.entries.first { it.length == Rational.of(1, meter.denominator / 2) }, 1) else Duration(NoteValue.entries.first { it.length == Rational.of(1, meter.denominator) }))
        return Score(title, meter, settings.key, tempo, listOf(Part(partName, clef, instrument, bars)))
    }

    private val STRAIGHT = listOf(
        Duration(NoteValue.WHOLE, 1), Duration(NoteValue.WHOLE), Duration(NoteValue.HALF, 1), Duration(NoteValue.HALF),
        Duration(NoteValue.QUARTER, 1), Duration(NoteValue.QUARTER), Duration(NoteValue.EIGHTH, 1), Duration(NoteValue.EIGHTH),
        Duration(NoteValue.SIXTEENTH, 1), Duration(NoteValue.SIXTEENTH), Duration(NoteValue.THIRTY_SECOND), Duration(NoteValue.SIXTY_FOURTH),
    )
    private val TRIPLETS = listOf(Duration(NoteValue.HALF), Duration(NoteValue.QUARTER), Duration(NoteValue.EIGHTH), Duration(NoteValue.SIXTEENTH))
        .map { it.inTuplet(3, 2) }

    /**
     * Written durations for [length] ticks starting [pos] ticks into a bar of [meter]: largest first, a piece
     * starting on a beat may end on a later beat, any other piece stays inside its beat.
     */
    fun durations(pos: Long, length: Long, meter: TimeSignature, division: Int): List<Duration> {
        val beats = meter.beatStarts.map { (it * TICKS_PER_WHOLE).num } + meter.barTicks.toLong()
        val candidates = (STRAIGHT + if (division % 3 == 0 && !meter.isCompound) TRIPLETS else emptyList())
            .map { it to (it.length * TICKS_PER_WHOLE) }.filter { it.second.isInteger }.map { it.first to it.second.num }
            .sortedByDescending { it.second }
        val out = ArrayList<Duration>()
        var p = pos; var left = length
        while (left > 0) {
            val beatIndex = beats.indexOfLast { it <= p }
            val onBeat = beats[beatIndex] == p
            val nextBeat = beats.getOrElse(beatIndex + 1) { meter.barTicks.toLong() }
            val pick = candidates.firstOrNull { (d, t) ->
                // On a beat: end on a beat, stay inside it, or be a dotted note whose undotted part ends on one (q. on a beat).
                t <= left && (p + t <= nextBeat || onBeat && (p + t in beats || d.dots == 1 && d.tuplets.isEmpty() && p + t * 2 / 3 in beats))
            } ?: candidates.lastOrNull { it.second <= left } ?: break
            out += pick.first
            p += pick.second; left -= pick.second
        }
        return out
    }
}
