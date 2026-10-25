package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.catalog.Scales
import com.betterappmafia.apophenia.core.pitch.SpelledPitch
import com.betterappmafia.apophenia.core.rhythm.TICKS_PER_WHOLE
import com.betterappmafia.apophenia.core.tools.KeySpeller

/**
 * The piano-roll view of a part (plan §10.5a, FL Studio's piano roll as the model): the same notes as start and
 * length in ticks (960 a quarter) and pitch. Ties are joined into one bar of colour. An edit works on the list,
 * then [rebuild] writes back only the bars it touched, as notated rhythm (beat-aware durations and ties,
 * `FromNotes.durations`), so bars the edit didn't reach keep their tuplets and spelling exactly.
 */
object PianoRoll {
    /**
     * One note: [id] stays the same through edits; [voice]; the [note] it came from (its pitch, ties aside, is
     * [pitch]); [event] the markings of the beat it started on (articulations, lyrics…), carried to wherever it goes.
     */
    data class RollNote(val id: Int, val start: Long, val length: Long, val pitch: SpelledPitch, val voice: Int = 0, val note: Note = Note(pitch), val event: Event? = null) {
        val end: Long get() = start + length
    }

    /** Where each bar starts, in ticks, plus the end of the last one. */
    fun barStarts(score: Score, part: Part): List<Long> {
        val out = ArrayList<Long>(); var t = 0L
        for (b in part.bars.indices) { out += t; t += score.meterAt(b).barTicks }
        out += t
        return out
    }

    fun notes(score: Score, part: Part): List<RollNote> {
        val starts = barStarts(score, part)
        val out = ArrayList<RollNote>()
        var id = 0
        for (v in 0 until (part.bars.maxOfOrNull { it.voices.size } ?: 1)) {
            // MIDI → the note still held by a tie, as an index into out.
            val held = HashMap<Int, Int>()
            for ((b, bar) in part.bars.withIndex()) {
                var t = starts[b]
                for (e in bar.voices.getOrNull(v) ?: emptyList()) {
                    val len = ticks(e)
                    for (n in e.notes) {
                        val k = held.remove(n.pitch.midi)
                        if (k != null && out[k].end == t) out[k] = out[k].copy(length = out[k].length + len)
                        else out += RollNote(id++, t, len, n.pitch, v, n.copy(tie = false), e.copy(notes = emptyList()))
                        if (n.tie) held[n.pitch.midi] = k?.takeIf { out[it].end == t + len } ?: out.lastIndex
                    }
                    t += len
                }
            }
        }
        return out
    }

    private fun ticks(e: Event): Long = (e.length * TICKS_PER_WHOLE).let { it.num / it.den }

    /**
     * The part's bars that [before] and [after] differ in rewritten from [after] (bars added at the end if notes
     * run past it, at the last bar's meter); every other bar is kept as it was.
     * [division] is the grid the notes sit on (12 lets triplets be written).
     */
    fun rebuild(score: Score, p: Int, before: List<RollNote>, after: List<RollNote>, division: Int = 4): Score {
        val part0 = score.parts[p]
        // Room for every note: bars added at the end as needed.
        var s = score
        while (true) {
            val starts = barStarts(s, s.parts[p])
            if ((after.maxOfOrNull { it.end } ?: 0L) <= starts.last()) break
            s = ScoreEdit.insertBar(s, p, s.parts[p].bars.lastIndex)
        }
        val part = s.parts[p]
        val starts = barStarts(s, part)
        fun barsOf(n: RollNote) = (0 until part.bars.size).filter { b -> n.start < starts[b + 1] && n.end > starts[b] }
        val beforeById = before.associateBy { it.id }; val afterById = after.associateBy { it.id }
        val touched = HashSet<Int>()
        for (id in beforeById.keys + afterById.keys) {
            val a = beforeById[id]; val z = afterById[id]
            if (a != z) { a?.let { touched += barsOf(it) }; z?.let { touched += barsOf(it) } }
        }
        touched += (part0.bars.size until part.bars.size)
        if (touched.isEmpty()) return score
        val voices = maxOf(after.maxOfOrNull { it.voice } ?: 0, part.bars.maxOf { it.voices.size } - 1) + 1
        val inst = part.instrument
        val bars = part.bars.mapIndexed { b, bar ->
            if (b !in touched) bar else {
                val meter = s.meterAt(b)
                val v = (0 until voices).map { voice ->
                    val here = after.filter { it.voice == voice && it.start < starts[b + 1] && it.end > starts[b] }
                    if (here.isEmpty() && voice >= bar.voices.size) null
                    else writeVoice(here, starts[b], starts[b + 1], meter, division)
                }
                // Voices keep their numbers; empty top voices go away.
                val kept = v.mapIndexed { i, x -> x ?: FromNotes.durations(0, meter.barTicks.toLong(), meter, 4).map { Event(it) } }
                    .let { list -> list.take((list.indices.lastOrNull { i -> i == 0 || v[i] != null && v[i]!!.any { !it.isRest } } ?: 0) + 1) }
                bar.copy(voices = kept)
            }
        }
        var out = s.copy(parts = s.parts.mapIndexed { i, x -> if (i == p) x.copy(bars = bars) else x })
        if (inst != null) out = out.copy(parts = out.parts.mapIndexed { i, x -> if (i == p) ScoreEdit.refinger(x, inst) else x })
        return out
    }

    /** One voice of one bar from the notes sounding in it: a beat wherever a note starts or ends, ties where notes go on. */
    private fun writeVoice(notes: List<RollNote>, from: Long, to: Long, meter: com.betterappmafia.apophenia.core.rhythm.TimeSignature, division: Int = 4): List<Event> {
        val cuts = (notes.flatMap { listOf(it.start, it.end) }.filter { it in from..to } + from + to).distinct().sorted()
        val out = ArrayList<Event>()
        for (k in 0 until cuts.size - 1) {
            val a = cuts[k]; val z = cuts[k + 1]
            val sounding = notes.filter { it.start <= a && it.end > a }.sortedByDescending { it.pitch.midi }
            val pieces = FromNotes.durations(a - from, z - a, meter, division)
            // Markings of beats that start here travel with them.
            val starting = sounding.filter { it.start == a }.mapNotNull { it.event }
            pieces.forEachIndexed { i, d ->
                val last = i == pieces.lastIndex
                val notesHere = sounding.map { n -> n.note.copy(pitch = n.pitch, tie = !last || n.end > z) }
                val base = if (i == 0 && starting.isNotEmpty()) merge(starting) else Event(d)
                out += base.copy(duration = d, notes = notesHere, hidden = false)
            }
        }
        return out
    }

    /** The markings of several beats that now start together, joined (the first one's single values win). */
    private fun merge(events: List<Event>): Event = events.drop(1).fold(events.first()) { a, e ->
        a.copy(
            marks = a.marks + e.marks, dynamic = a.dynamic ?: e.dynamic, spans = e.spans + a.spans, chord = a.chord ?: e.chord,
            diagram = a.diagram ?: e.diagram, texts = (a.texts + e.texts).distinct(), lyrics = (a.lyrics + e.lyrics).distinctBy { it.verse },
            tremolo = maxOf(a.tremolo, e.tremolo), graces = a.graces + e.graces,
        )
    }

    // FL Studio's edits (Edit and Tools menus), each on the selected notes [ids] and returning the whole new list.

    /** Draw: a new note; spelled in the key. */
    fun add(notes: List<RollNote>, start: Long, length: Long, midi: Int, voice: Int, sp: KeySpeller): List<RollNote> =
        notes + RollNote((notes.maxOfOrNull { it.id } ?: -1) + 1, start, length, sp.spell(midi), voice)

    fun delete(notes: List<RollNote>, ids: Set<Int>) = notes.filter { it.id !in ids }

    /** The velocity lane: notes [ids] set to [velocity] (1–127), or back to the written dynamics with null. */
    fun setVelocity(notes: List<RollNote>, ids: Set<Int>, velocity: Int?) =
        notes.map { if (it.id in ids) it.copy(note = it.note.copy(velocity = velocity?.coerceIn(1, 127))) else it }

    /** The velocity a note plays at when it has none of its own: the written dynamics, accents and all. */
    fun playedVelocity(score: Score, p: Int, n: RollNote): Int {
        n.note.velocity?.let { return it }
        val e = n.event ?: Event(com.betterappmafia.apophenia.core.rhythm.Duration.QUARTER)
        return (ScorePlayback.velocityAtTick(score, score.parts[p], n.start) + ScorePlayback.accentOf(e, n.note)).coerceIn(1, 127)
    }

    /** Moved [dt] ticks and [semis] semitones (respelled in the key when the pitch moves), never before the start. */
    fun move(notes: List<RollNote>, ids: Set<Int>, dt: Long, semis: Int, sp: KeySpeller): List<RollNote> {
        val shift = maxOf(dt, -(notes.filter { it.id in ids }.minOfOrNull { it.start } ?: 0L))
        return notes.map { n -> if (n.id !in ids) n else n.copy(start = n.start + shift, pitch = if (semis == 0) n.pitch else sp.spell(n.pitch.midi + semis)) }
    }

    /** New length (at least [min]). */
    fun resize(notes: List<RollNote>, ids: Set<Int>, dl: Long, min: Long) = notes.map { n -> if (n.id !in ids) n else n.copy(length = maxOf(min, n.length + dl)) }

    /** Duplicate: copies placed right after the selection; returns the list and the copies' ids. */
    fun duplicate(notes: List<RollNote>, ids: Set<Int>): Pair<List<RollNote>, Set<Int>> {
        val sel = notes.filter { it.id in ids }
        if (sel.isEmpty()) return notes to ids
        val span = sel.maxOf { it.end } - sel.minOf { it.start }
        var next = (notes.maxOfOrNull { it.id } ?: -1) + 1
        val copies = sel.map { it.copy(id = next++, start = it.start + span) }
        return (notes + copies) to copies.map { it.id }.toSet()
    }

    /** Quantize: starts (and ends, when [ends]) to the nearest [grid]; a note never shorter than the grid. */
    fun quantize(notes: List<RollNote>, ids: Set<Int>, grid: Long, ends: Boolean = true) = notes.map { n ->
        if (n.id !in ids) n else {
            val s = Math.round(n.start.toDouble() / grid) * grid
            val e = if (ends) maxOf(s + grid, Math.round(n.end.toDouble() / grid) * grid) else s + n.length
            n.copy(start = s, length = e - s)
        }
    }

    /** Chop: each note cut into pieces of [grid]. */
    fun chop(notes: List<RollNote>, ids: Set<Int>, grid: Long): List<RollNote> {
        var next = (notes.maxOfOrNull { it.id } ?: -1) + 1
        return notes.flatMap { n ->
            if (n.id !in ids || n.length <= grid) listOf(n)
            else (0 until (n.length + grid - 1) / grid).map { k ->
                val st = n.start + k * grid
                n.copy(id = if (k == 0L) n.id else next++, start = st, length = minOf(grid, n.end - st), event = if (k == 0L) n.event else null)
            }
        }
    }

    /** Glue: notes of one pitch in one voice that touch or overlap become one. */
    fun glue(notes: List<RollNote>, ids: Set<Int>): List<RollNote> {
        val (sel, rest) = notes.partition { it.id in ids }
        val out = ArrayList<RollNote>()
        for ((_, group) in sel.groupBy { it.voice to it.pitch.midi }) {
            var cur: RollNote? = null
            for (n in group.sortedBy { it.start }) {
                val c = cur
                cur = if (c != null && n.start <= c.end) c.copy(length = maxOf(c.end, n.end) - c.start) else { c?.let { out += it }; n }
            }
            cur?.let { out += it }
        }
        return (rest + out).sortedBy { it.id }
    }

    /** Legato: each note held until the next note starts in its voice (the last left as it is). */
    fun legato(notes: List<RollNote>, ids: Set<Int>): List<RollNote> = notes.map { n ->
        if (n.id !in ids) n else notes.filter { it.voice == n.voice && it.start > n.start }.minOfOrNull { it.start }?.let { n.copy(length = it - n.start) } ?: n
    }

    /** Flip: pitches mirrored around the middle of the selection's range, respelled in the key. */
    fun flip(notes: List<RollNote>, ids: Set<Int>, sp: KeySpeller): List<RollNote> {
        val sel = notes.filter { it.id in ids }
        if (sel.isEmpty()) return notes
        val sum = sel.minOf { it.pitch.midi } + sel.maxOf { it.pitch.midi }
        return notes.map { n -> if (n.id !in ids) n else n.copy(pitch = sp.spell(sum - n.pitch.midi)) }
    }

    /** Limit: notes outside [lo]..[hi] moved by octaves into it. */
    fun limit(notes: List<RollNote>, ids: Set<Int>, lo: Int, hi: Int, sp: KeySpeller): List<RollNote> = notes.map { n ->
        if (n.id !in ids) n else {
            var m = n.pitch.midi
            while (m < lo && m + 12 <= hi) m += 12
            while (m > hi && m - 12 >= lo) m -= 12
            if (m == n.pitch.midi) n else n.copy(pitch = sp.spell(m))
        }
    }

    /** Snap to scale: the nearest pitch of [key]'s scale at or below [midi] (FL's Snap to scale). */
    fun snapToScale(midi: Int, key: KeyMode?): Int {
        val k = key ?: return midi
        val pcs = Scales.byId(k.scaleId).let { scale -> scale.degrees.map { Math.floorMod(k.tonic.pitchClass + it.interval.semitones, 12) }.toSet() }
        var m = midi
        while (Math.floorMod(m, 12) !in pcs) m--
        return m
    }
}
