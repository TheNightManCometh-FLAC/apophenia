package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.rhythm.Rational
import com.betterappmafia.apophenia.core.rhythm.TICKS_PER_WHOLE
import com.betterappmafia.apophenia.core.sound.PlayNote

/**
 * A score as the piano plays it: what [ScoreTime.notes] gives (play order through repeats and jumps, ties joined)
 * plus the markings. Dynamics hold for the whole part from where they're written (all voices, as on a staff);
 * sf, sfz, fz, rf and rfz hit one beat only, fp and sfp hit then drop. A hairpin moves from the level where it
 * starts to the dynamic written where it ends, or one level up or down when none is. Accents and stresses play
 * louder, ghost notes and unstress softer; staccato, staccatissimo, portato and marcato shorten the note.
 * Grace notes before the beat play on it and push the main note later (an acciaccatura quickly, an appoggiatura
 * for its written value, up to half the main note); grace notes after take the end of it. Trills, mordents and
 * turns use the notes a step above and below in the key; tremolo slashes repeat the note; arpeggios roll the chord
 * up or down; the sustain pedal holds every note released under it to the pedal's end. A note with its own velocity
 * (the piano roll's velocity lane) plays at exactly that, whatever the dynamics and accents say.
 */
object ScorePlayback {
    /** MuseScore 3's default velocities. */
    fun velocity(d: Dynamic): Int = when (d) {
        Dynamic.PPPP -> 10; Dynamic.PPP -> 16; Dynamic.PP -> 33; Dynamic.P -> 49; Dynamic.MP -> 64; Dynamic.MF -> 80
        Dynamic.F -> 96; Dynamic.FF -> 112; Dynamic.FFF -> 126; Dynamic.FFFF -> 127
        Dynamic.SF, Dynamic.SFZ, Dynamic.RF, Dynamic.RFZ, Dynamic.FZ, Dynamic.SFP, Dynamic.SFPP -> 112
        Dynamic.SFF, Dynamic.SFFZ -> 126
        Dynamic.FP -> 96; Dynamic.PF -> 49; Dynamic.N -> 1
    }

    /** The level a dynamic leaves after its own beat, or null for one that hits a single beat and goes back. */
    fun after(d: Dynamic): Int? = when (d) {
        Dynamic.SF, Dynamic.SFZ, Dynamic.SFF, Dynamic.SFFZ, Dynamic.RF, Dynamic.RFZ, Dynamic.FZ -> null
        Dynamic.FP, Dynamic.SFP -> 49
        Dynamic.SFPP -> 33
        Dynamic.PF -> 96
        else -> velocity(d)
    }

    const val DEFAULT_VELOCITY = 80
    private const val EPS = 1e-6
    /** How far apart the notes of a rolled chord start. */
    const val ROLL = 0.03
    /** An acciaccatura's length; it never takes more than a quarter of the main note. */
    const val CRUSH = 0.06

    private class Change(val time: Double, val level: Int?, val hit: Int)
    private class Hairpin(val from: Double, val to: Double, val v0: Int, val v1: Int)

    fun notes(score: Score, part: Part): List<PlayNote> {
        val slots = ScoreTime.slots(score, part)
        fun ev(s: ScoreTime.Slot) = part.bars[s.bar].voices[s.voice][s.index]
        val byVoice = slots.groupBy { it.voice }.mapValues { (_, s) -> s.sortedBy { it.start } }
        /** The end of the [n] events of the voice starting at [s]. */
        fun spanEnd(s: ScoreTime.Slot, n: Int): Double {
            val list = byVoice.getValue(s.voice)
            val i = list.indexOf(s)
            return list[minOf(i + n - 1, list.lastIndex)].end
        }

        val velocityAt = dynamics(part, slots)

        val out = ArrayList<PlayNote>()
        for ((v, list) in byVoice) {
            val held = HashMap<Int, Pair<Double, ScoreTime.Slot>>()   // MIDI → start and first slot of a tie still sounding
            for (slot in list) {
                val e = ev(slot)
                val len = slot.end - slot.start
                val tempo = score.tempoAt(slot.bar)
                val fresh = e.notes.any { it.pitch.midi !in held }
                // Grace notes: before the beat they push the main notes later, after it they take its end.
                var shift = 0.0
                var cut = 0.0
                if (fresh && e.graces.isNotEmpty()) {
                    val vel = velocityAt(slot.start)
                    val before = e.graces.filter { !it.after }
                    val each = before.map { g -> if (g.slash) minOf(CRUSH, len / 4 / before.size) else tempo.secondsFor(g.value.length) }
                    val scale = if (each.sum() > len / 2) len / 2 / each.sum() else 1.0
                    var t = slot.start
                    for ((g, d) in before.zip(each)) { for (n in g.notes) out += PlayNote(t, t + d * scale, n.pitch.midi, n.velocity ?: vel); t += d * scale }
                    shift = t - slot.start
                    val afterG = e.graces.filter { it.after }
                    if (afterG.isNotEmpty()) {
                        val d = minOf(CRUSH, (len - shift) / 2 / afterG.size)
                        cut = d * afterG.size
                        var a = slot.end - cut
                        for (g in afterG) { for (n in g.notes) out += PlayNote(a, a + d, n.pitch.midi, n.velocity ?: vel); a += d }
                    }
                }
                val sounding = ArrayList<Triple<Double, Note, ScoreTime.Slot>>()   // start, note, first slot
                for (n in e.notes) {
                    val (start, first) = held.remove(n.pitch.midi) ?: ((slot.start + shift) to slot)
                    if (n.tie) held[n.pitch.midi] = start to first else sounding += Triple(start, n, first)
                }
                if (sounding.isEmpty()) continue
                val gate = gateOf(e)
                val end = slot.end - cut - if (gate < 1.0) (len - cut - shift) * (1.0 - gate) else 0.0
                val roll = e.marks.firstOrNull { it.arpeggio }
                val order = when (roll) { null -> sounding; Mark.ARPEGGIO_DOWN -> sounding.sortedByDescending { it.second.pitch.midi }; else -> sounding.sortedBy { it.second.pitch.midi } }
                val top = sounding.maxBy { it.second.pitch.midi }.second
                for ((k, item) in order.withIndex()) {
                    val (start0, n, first) = item
                    val start = start0 + if (roll != null) k * ROLL else 0.0
                    val fe = ev(first)
                    var vel = velocityAt(first.start) + accentOf(fe, n)
                    if (Effect.GHOST in n.effects) vel = (vel * 0.45).toInt()
                    vel = (n.velocity ?: vel).coerceIn(1, 127)
                    val noteEnd = maxOf(start + 0.02, end)
                    val fifths = Notation.fifths(score.keyAt(first.bar))
                    val unit = minOf(tempo.secondsFor(Rational.of(1, 32)), (noteEnd - start) / 4)
                    val ornament = if (n === top) fe.marks.firstOrNull { it.group == MarkGroup.ORNAMENT && !it.arpeggio }
                        ?: (if (fe.spans.containsKey(Span.TRILL_LINE)) Mark.TRILL else null) else null
                    val reps = if (e.tremolo > 0) tempo.secondsFor(Rational.of(1, 4 shl e.tremolo)) else 0.0
                    when {
                        ornament != null -> out += ornamented(ornament, n, start, noteEnd, unit, vel, fifths)
                        reps > 0 && reps < noteEnd - start -> {
                            var t = start
                            while (t < noteEnd - EPS) { out += PlayNote(t, minOf(noteEnd, t + reps), n.pitch.midi, vel); t += reps }
                        }
                        else -> out += PlayNote(start, noteEnd, n.pitch.midi, vel)
                    }
                }
            }
            // A tie left hanging at the end still sounds.
            val last = list.lastOrNull()?.end ?: 0.0
            for ((midi, h) in held) out += PlayNote(h.first, last, midi, velocityAt(h.second.start))
        }

        // The sustain pedal holds everything released under it.
        val pedals = slots.flatMap { s -> ev(s).spans[Span.PEDAL]?.let { listOf(s.start to spanEnd(s, it)) } ?: emptyList() }
        val pedalled = out.map { n ->
            val p = pedals.firstOrNull { n.end > it.first + EPS && n.end < it.second - EPS }
            if (p != null) n.copy(end = p.second) else n
        }
        return pedalled.sortedWith(compareBy({ it.start }, { it.midi }))
    }

    /**
     * The level at any time on [slots]' timeline (seconds in play order, or ticks in written order): dynamics, their
     * one-beat hits, and hairpins between them.
     */
    private fun dynamics(part: Part, slots: List<ScoreTime.Slot>): (Double) -> Int {
        fun ev(s: ScoreTime.Slot) = part.bars[s.bar].voices[s.voice][s.index]
        val byVoice = slots.groupBy { it.voice }.mapValues { (_, s) -> s.sortedBy { it.start } }
        fun spanEnd(s: ScoreTime.Slot, n: Int): Double {
            val list = byVoice.getValue(s.voice)
            return list[minOf(list.indexOf(s) + n - 1, list.lastIndex)].end
        }
        // Dynamics, in time order.
        val changes = ArrayList<Change>()
        for (s in slots) ev(s).dynamic?.let { changes += Change(s.start, after(it), velocity(it)) }
        changes.sortBy { it.time }
        fun levelAt(t: Double, list: List<Change>): Int = list.lastOrNull { it.time <= t + EPS && it.level != null }?.level ?: DEFAULT_VELOCITY
        // Hairpins, each from the level at its start to the dynamic at its end (or a level on).
        val pins = ArrayList<Hairpin>()
        val made = ArrayList<Change>()
        for (s in slots) for ((span, n) in ev(s).spans) {
            if (span != Span.CRESC && span != Span.DIM) continue
            val end = spanEnd(s, n)
            val v0 = changes.firstOrNull { abs(it.time - s.start) < EPS }?.hit ?: levelAt(s.start, changes + made)
            val target = changes.firstOrNull { abs(it.time - end) < EPS }?.hit
            val v1 = target ?: (v0 + if (span == Span.CRESC) 16 else -16).coerceIn(1, 127)
            pins += Hairpin(s.start, end, v0, v1)
            if (target == null) made += Change(end, v1, v1)
        }
        val all = (changes + made).sortedBy { it.time }
        fun velocityAt(t: Double): Int {
            all.firstOrNull { abs(it.time - t) < EPS }?.let { return it.hit }
            pins.lastOrNull { t >= it.from - EPS && t < it.to - EPS }?.let { return (it.v0 + (it.v1 - it.v0) * (t - it.from) / (it.to - it.from)).toInt() }
            return levelAt(t, all)
        }
        return ::velocityAt
    }

    /** The level written at [tick] (bars in written order, repeats aside), as the velocity lane shows it. */
    fun velocityAtTick(score: Score, part: Part, tick: Long): Int {
        val slots = ArrayList<ScoreTime.Slot>()
        var barStart = 0L
        for ((b, bar) in part.bars.withIndex()) {
            for ((v, events) in bar.voices.withIndex()) {
                var t = barStart
                for ((i, e) in events.withIndex()) { val len = (e.length * TICKS_PER_WHOLE).let { it.num / it.den }; slots += ScoreTime.Slot(b, v, i, t.toDouble(), (t + len).toDouble()); t += len }
            }
            barStart += score.meterAt(b).barTicks
        }
        return dynamics(part, slots.sortedWith(compareBy({ it.start }, { it.voice })))(tick.toDouble())
    }

    /** How much of its length a note sounds. */
    fun gateOf(e: Event): Double = when {
        Mark.STACCATISSIMO in e.marks -> 0.3
        e.notes.any { Effect.STACCATO in it.effects } -> 0.5
        Mark.PORTATO in e.marks -> 0.75
        e.notes.any { Effect.HEAVY_ACCENT in it.effects } -> 0.75
        e.notes.any { Effect.DEAD in it.effects } -> 0.15
        else -> 1.0
    }

    /** Velocity added for accents and stresses (or taken off for unstress). */
    fun accentOf(e: Event, n: Note): Int =
        (if (Effect.HEAVY_ACCENT in n.effects) 28 else if (Effect.ACCENT in n.effects) 20 else 0) +
            (if (Mark.STRESS in e.marks) 12 else 0) + (if (Mark.SOFT_ACCENT in e.marks) 10 else 0) - (if (Mark.UNSTRESS in e.marks) 15 else 0)

    /** A trill, mordent or turn on [n], with the notes a step above and below in the key. */
    private fun ornamented(m: Mark, n: Note, start: Double, end: Double, unit: Double, vel: Int, fifths: Int): List<PlayNote> {
        val main = n.pitch.midi
        val up = Notation.stepped(n.pitch, 1, fifths).midi
        val down = Notation.stepped(n.pitch, -1, fifths).midi
        val seq: List<Int> = when (m) {
            Mark.TRILL -> return buildList {
                var t = start; var k = 0
                while (t + unit < end - EPS) { add(PlayNote(t, t + unit, if (k % 2 == 0) main else up, vel)); t += unit; k++ }
                add(PlayNote(t, end, if (k % 2 == 0) main else up, vel))
            }
            Mark.UPPER_MORDENT -> listOf(main, up)
            Mark.MORDENT -> listOf(main, down)
            Mark.TURN -> listOf(up, main, down)
            Mark.INVERTED_TURN, Mark.TURN_SLASH -> listOf(down, main, up)
            else -> emptyList()
        }
        var t = start
        val out = ArrayList<PlayNote>()
        for (p in seq) { out += PlayNote(t, t + unit, p, vel); t += unit }
        out += PlayNote(t, end, main, vel)
        return out
    }

    private fun abs(x: Double) = kotlin.math.abs(x)
}
