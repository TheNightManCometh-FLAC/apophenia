package com.betterappmafia.apophenia.core.analysis

import kotlin.math.abs
import kotlin.math.roundToInt

/** A pitch curve: fractional MIDI (NaN = nothing pitched) every [hop] seconds, the first value at [first] seconds. */
class PitchCurve(val hop: Double, val first: Double, val midi: DoubleArray) {
    fun time(f: Int) = first + f * hop
    val size get() = midi.size
}

/**
 * Performance check (plan §10.6; Christopher, 21 Oct 2026: "see how closely you matched it. It points out the sections
 * you got wrong, and why/how"). The single-line case the plan calls the strongest: a reference melody against the
 * pitch curve of your take.
 *
 * 1. Both become a pitch every 20 ms and are lined up by dynamic time warping in a band around the diagonal, on pitch
 *    class (so singing an octave down still lines up), so a slightly different tempo or a late start isn't "wrong".
 * 2. An octave shift over the whole take is found and taken off (a man singing a melody written an octave up).
 * 3. Each reference note gets your frames that lined up with it: missed, wrong note (by how many semitones), sharp or
 *    flat in cents, and early or late against the take's own tempo (a straight line through all your note starts).
 * 4. Why, where the curve shows it: scooping up into the note, drifting flat or sharp on a held note, vibrato centred
 *    off the pitch, rushing or dragging through three or more notes.
 * [Claude] the tolerances: 25 cents, half a semitone for a wrong note, 70 ms, and the "why" thresholds below.
 */
object PerformanceCheck {
    const val CENTS_OK = 25
    const val TIMING_OK_MS = 70
    private const val SILENCE_COST = 0.6

    data class RefNote(val start: Double, val end: Double, val midi: Int, val bar: Int? = null, val beat: Double? = null)

    enum class Issue(val label: String) { MISSED("missed"), WRONG_NOTE("wrong note"), SHARP("sharp"), FLAT("flat"), EARLY("early"), LATE("late") }
    enum class Why(val label: String) {
        SCOOP("scooping up into the note"), DRIFT_FLAT("drifting flat on a held note"), DRIFT_SHARP("drifting sharp on a held note"),
        VIBRATO_OFF("vibrato centred off the pitch"), RUSHING("rushing the phrase"), DRAGGING("dragging the phrase"),
    }

    /** One reference note: where you played it in the take, cents off, semitones off when wrong, ms early (−) or late (+). */
    data class NoteResult(
        val ref: RefNote, val takeStart: Double?, val takeEnd: Double?, val cents: Int?, val wrongBy: Int?, val timingMs: Int?,
        val issues: Set<Issue>, val why: Set<Why>,
    ) { val good get() = issues.isEmpty() }

    /** A pitched stretch of the take that lines up with silence in the reference. */
    data class Extra(val start: Double, val end: Double, val midi: Int)

    /**
     * [score] = the share of reference notes with nothing wrong, 0–100. [octaveShift] in semitones (a multiple of 12).
     * [warp] pairs reference time with take time along the alignment, for drawing.
     */
    data class Result(val notes: List<NoteResult>, val extra: List<Extra>, val score: Int, val octaveShift: Int, val warp: List<Pair<Double, Double>>)

    /** A single line from any notes: where notes overlap, the highest sounding one (the tune is usually on top). [Claude] */
    fun melody(notes: List<RefNote>): List<RefNote> {
        val sorted = notes.sortedWith(compareBy({ it.start }, { -it.midi }))
        val out = ArrayList<RefNote>()
        for (n in sorted) {
            val last = out.lastOrNull()
            when {
                last == null || n.start >= last.end -> out += n
                n.start == last.start -> {}                                   // same onset, lower: part of a chord
                n.midi > last.midi -> { out[out.lastIndex] = last.copy(end = n.start); out += n }
                else -> {}                                                    // a lower note under a held top note
            }
        }
        return out.filter { it.end - it.start > 0.03 }
    }

    fun check(reference: List<RefNote>, curve: PitchCurve, onsets: List<Double> = emptyList()): Result {
        val ref = melody(reference)
        if (ref.isEmpty() || curve.size == 0) return Result(ref.map { NoteResult(it, null, null, null, null, null, setOf(Issue.MISSED), emptySet()) }, emptyList(), 0, 0, emptyList())
        val hop = curve.hop
        // The reference with 2 s of silence before and after, so a take that starts late or runs on lines its silence up
        // with silence instead of stretching the first or last note over it.
        val pad = (2.0 / hop).toInt()
        val n = ((ref.last().end) / hop).toInt() + 1 + 2 * pad
        val r = DoubleArray(n) { Double.NaN }
        val noteOf = IntArray(n) { -1 }
        for ((k, note) in ref.withIndex()) for (j in pad + (note.start / hop).toInt() until minOf(n, pad + (note.end / hop).toInt())) { r[j] = note.midi.toDouble(); noteOf[j] = k }
        val t = curve.midi
        val m = t.size

        // Banded DTW, take frames i against reference frames j, on pitch-class distance.
        fun cost(i: Int, j: Int): Double {
            val a = t[i]; val b = r[j]
            return when {
                a.isNaN() && b.isNaN() -> 0.0
                a.isNaN() || b.isNaN() -> SILENCE_COST
                else -> { val d = Math.floorMod(((a - b) * 100).roundToInt(), 1200) / 100.0; minOf(d, 12 - d) / 6.0 }
            }
        }
        val band = maxOf(100, maxOf(n, m) / 8).coerceAtMost(2000)
        fun centre(i: Int) = (i.toLong() * (n - 1) / maxOf(1, m - 1)).toInt()
        val width = 2 * band + 1
        val back = ByteArray(m * width)                   // 0 diag, 1 from i-1 (take advanced), 2 from j-1 (reference advanced)
        var prev = DoubleArray(width) { Double.POSITIVE_INFINITY }
        var cur = DoubleArray(width)
        for (i in 0 until m) {
            java.util.Arrays.fill(cur, Double.POSITIVE_INFINITY)
            val c0 = centre(i); val p0 = if (i > 0) centre(i - 1) else 0
            for (w in 0 until width) {
                val j = c0 - band + w
                if (j < 0 || j >= n) continue
                val here = cost(i, j)
                if (i == 0 && j == 0) { cur[w] = here; continue }
                var best = Double.POSITIVE_INFINITY; var how = 0
                if (i > 0) {
                    val wd = j - 1 - (p0 - band); if (j > 0 && wd in 0 until width && prev[wd] < best) { best = prev[wd]; how = 0 }
                    val wu = j - (p0 - band); if (wu in 0 until width && prev[wu] < best) { best = prev[wu]; how = 1 }
                }
                if (w > 0 && cur[w - 1] < best) { best = cur[w - 1]; how = 2 }
                if (best.isInfinite()) continue
                cur[w] = best + here
                back[i * width + w] = how.toByte()
            }
            val s = prev; prev = cur; cur = s
        }
        // Walk back from the last take frame, from the reference frame it ended on best (the take may stop early or run on).
        val lastC = centre(m - 1)
        var bestW = -1
        for (w in 0 until width) { val j = lastC - band + w; if (j in 0 until n && prev[w].isFinite() && (bestW < 0 || prev[w] < prev[bestW])) bestW = w }
        val pairs = ArrayList<Pair<Int, Int>>()
        var i = m - 1; var j = lastC - band + bestW
        while (i >= 0 && j >= 0) {
            pairs += i to j
            if (i == 0 && j == 0) break
            val w = j - (centre(i) - band)
            when (back[i * width + w].toInt()) { 0 -> { i--; j-- }; 1 -> i--; else -> j-- }
        }
        pairs.reverse()

        // What each take frame lined up with: a reference note (its index), reference silence (−1), or nothing (−2).
        val refOf = IntArray(m) { -2 }
        for ((ti, rj) in pairs) if (noteOf[rj] >= 0 || refOf[ti] == -2) refOf[ti] = noteOf[rj]

        // The octave shift over the whole take.
        val diffs = (0 until m).filter { refOf[it] >= 0 && !t[it].isNaN() }.map { t[it] - ref[refOf[it]].midi }
        val shift = if (diffs.isEmpty()) 0 else (median(diffs) / 12.0).roundToInt() * 12

        // Your own notes: runs of pitched frames, split at a breath, a jump of more than 0.7 semitone from the run so far,
        // or an attack. Each run goes to the reference note it mostly lined up with, so a note's start is where you
        // actually came in, and two notes of the same pitch don't blur into each other.
        val onsetFrames = onsets.map { ((it - curve.first) / hop).roundToInt() }.toSet()
        val runs = ArrayList<IntRange>()
        run {
            var s = -1; val vals = ArrayList<Double>()
            for (f in 0..m) {
                val p = if (f < m) t[f] else Double.NaN
                if (s >= 0 && (p.isNaN() || abs(p - median(vals)) > 0.7 || (f in onsetFrames && (f - s) * hop > 0.08))) { runs += s until f; s = -1; vals.clear() }
                if (!p.isNaN()) { if (s < 0) s = f; vals += p }
            }
        }
        // A short run right against the next (or the last) is the way into (or out of) that note: a scoop, a slide.
        val merged = ArrayList<IntRange>()
        for ((q, r) in runs.withIndex()) {
            val short = r.count() * hop < 0.12
            val next = runs.getOrNull(q + 1)
            when {
                short && next != null && next.first - r.last <= 2 -> runs[q + 1] = r.first..next.last
                short && merged.isNotEmpty() && r.first - merged.last().last <= 2 -> merged[merged.lastIndex] = merged.last().first..r.last
                else -> merged += r
            }
        }
        val assigned = Array(ref.size) { ArrayList<IntRange>() }
        val extraRuns = ArrayList<IntRange>()
        assign(merged, ref, t, curve, hop, pad, pairs, assigned, extraRuns)
        fun devOf(r: IntRange, k: Int) = median(r.map { t[it] - shift - ref[k].midi })

        data class Partial(val k: Int, val start: Double?, val end: Double?, val dev: Double?, val voiced: List<Double>)
        val parts = ref.indices.map { k ->
            // A run that doesn't match the note while another one does is an extra note if it's long enough to be one
            // (a short one is the way into or out of the note: a scoop, a slide).
            val matching = assigned[k].filter { abs(devOf(it, k)) < 0.5 }
            val mine = if (matching.isEmpty()) assigned[k] else assigned[k].filter { r ->
                (r in matching || r.count() * hop < 0.12).also { keep -> if (!keep) extraRuns += r }
            }
            val frames = mine.flatten().sorted()
            if (frames.size * hop < 0.05) Partial(k, null, null, null, emptyList())
            else {
                var st = curve.time(frames.first())
                onsets.minByOrNull { abs(it - st) }?.takeIf { abs(it - st) < 0.06 }?.let { st = it }
                val vals = frames.map { t[it] - shift - ref[k].midi }
                Partial(k, st, curve.time(frames.last()) + hop, median((if (matching.isEmpty()) frames else matching.flatten()).map { t[it] - shift - ref[k].midi }), vals)
            }
        }
        // Timing against the take's own tempo: a straight line through (reference start, your start).
        val timed = parts.filter { it.start != null }
        val (a, b) = if (timed.size >= 2) fit(timed.map { ref[it.k].start }, timed.map { it.start!! }) else (timed.firstOrNull()?.let { it.start!! - ref[it.k].start } ?: 0.0) to 1.0
        val results = parts.map { p ->
            val note = ref[p.k]
            if (p.dev == null) return@map NoteResult(note, null, null, null, null, null, setOf(Issue.MISSED), emptySet())
            val issues = HashSet<Issue>(); val why = HashSet<Why>()
            var cents: Int? = null; var wrong: Int? = null
            if (abs(p.dev) >= 0.5) { wrong = p.dev.roundToInt(); issues += Issue.WRONG_NOTE }
            else { cents = (p.dev * 100).roundToInt(); if (cents > CENTS_OK) issues += Issue.SHARP; if (cents < -CENTS_OK) issues += Issue.FLAT }
            val ms = ((p.start!! - (a + b * note.start)) * 1000).roundToInt()
            if (ms > TIMING_OK_MS) issues += Issue.LATE
            if (ms < -TIMING_OK_MS) issues += Issue.EARLY
            why += reasons(p.voiced, hop)
            NoteResult(note, p.start, p.end, cents, wrong, ms, issues, why)
        }.toMutableList()
        // Rushing or dragging: three or more notes in a row each further early (or late) than the one before.
        // Each note's step against the one before: −1 further early (and early), +1 further late (and late), 0 neither.
        val step = results.indices.map { k ->
            val ms = results[k].timingMs; val before = results.getOrNull(k - 1)?.timingMs
            when {
                ms == null || before == null -> 0
                ms < before - 15 && ms < -TIMING_OK_MS / 2 -> -1
                ms > before + 15 && ms > TIMING_OK_MS / 2 -> 1
                else -> 0
            }
        }
        var k = 0
        while (k < results.size) {
            if (step[k] == 0) { k++; continue }
            var e = k
            while (e + 1 < results.size && step[e + 1] == step[k]) e++
            if (e - k + 1 >= 2) for (q in k - 1..e) results[q] = results[q].copy(why = results[q].why + if (step[k] < 0) Why.RUSHING else Why.DRAGGING)
            k = e + 1
        }
        // Extra notes: your runs of at least 0.12 s that line up with a rest, or sit beside a note you also got right.
        val extra = extraRuns.sortedBy { it.first }.map { r -> Extra(curve.time(r.first), curve.time(r.last) + hop, median(r.map { t[it] }).roundToInt()) }
        val score = (100.0 * results.count { it.good } / results.size).roundToInt()
        val every = maxOf(1, pairs.size / 400)
        return Result(results, extra, score, shift, pairs.filterIndexed { q, _ -> q % every == 0 }.map { (ti, rj) -> (rj - pad) * hop to curve.time(ti) })
    }

    /**
     * Your runs matched to the reference notes in order (a note-level alignment, Needleman–Wunsch style). Matching a
     * run to a note costs its pitch-class distance plus how far it starts from where the frame warp expects that note;
     * a run left over is an extra note, a note left over is missed, and a run may join the note the run before it
     * matched (a re-attack). The warp only guides the times, so three repeated notes in a row still go one to one.
     * [Claude] the costs.
     */
    private fun assign(
        runs: List<IntRange>, ref: List<RefNote>, t: DoubleArray, curve: PitchCurve, hop: Double, pad: Int,
        pairs: List<Pair<Int, Int>>, assigned: Array<ArrayList<IntRange>>, extraRuns: MutableList<IntRange>,
    ) {
        val rn = runs.size; val kn = ref.size
        val pitch = runs.map { r -> median(r.map { t[it] }) }
        val start = runs.map { curve.time(it.first) }
        // Where the warp puts each note's start in the take.
        val expected = ref.map { n ->
            val j = pad + (n.start / hop).toInt()
            curve.time(pairs.firstOrNull { it.second >= j }?.first ?: pairs.last().first)
        }
        fun pcCost(r: Int, k: Int): Double { val d = Math.floorMod(((pitch[r] - ref[k].midi) * 100).roundToInt(), 1200) / 100.0; return minOf(minOf(d, 12 - d), 3.0) / 3.0 }
        fun matchCost(r: Int, k: Int) = pcCost(r, k) + minOf(abs(start[r] - expected[k]) / 0.4, 1.5)
        fun skipRun(r: Int) = if (runs[r].count() * hop < 0.12) 0.15 else 0.9
        val skipNote = 0.9
        val inf = Double.POSITIVE_INFINITY
        // d[r][k]: best cost having used r runs and k notes. a[r][k]: the same, with run r−1 belonging to note k−1.
        val d = Array(rn + 1) { DoubleArray(kn + 1) { inf } }
        val a = Array(rn + 1) { DoubleArray(kn + 1) { inf } }
        val how = Array(rn + 1) { ByteArray(kn + 1) }     // d's move: 0 from a, 1 skip run, 2 skip note
        val howA = Array(rn + 1) { ByteArray(kn + 1) }    // a's move: 0 match, 1 join
        d[0][0] = 0.0
        for (r in 0..rn) for (k in 0..kn) {
            if (r > 0 && k > 0) {
                val m = d[r - 1][k - 1] + matchCost(r - 1, k - 1)
                val j = a[r - 1][k] + pcCost(r - 1, k - 1) + 0.2
                if (m <= j) { a[r][k] = m; howA[r][k] = 0 } else { a[r][k] = j; howA[r][k] = 1 }
            }
            if (r == 0 && k == 0) continue
            var best = a[r][k]; var mv = 0
            if (r > 0 && d[r - 1][k] + skipRun(r - 1) < best) { best = d[r - 1][k] + skipRun(r - 1); mv = 1 }
            if (k > 0 && d[r][k - 1] + skipNote < best) { best = d[r][k - 1] + skipNote; mv = 2 }
            d[r][k] = best; how[r][k] = mv.toByte()
        }
        var r = rn; var k = kn; var inA = false
        while (r > 0 || k > 0) {
            if (!inA) when (how[r][k].toInt()) {
                0 -> inA = true
                1 -> { if (runs[r - 1].count() * hop >= 0.12) extraRuns += runs[r - 1]; r-- }
                else -> k--
            } else {
                assigned[k - 1].add(0, runs[r - 1])
                if (howA[r][k].toInt() == 0) { r--; k--; inA = false } else r--
            }
        }
    }

    /** Why a note was off, from its own frames (semitones from the target, octave shift already off). */
    private fun reasons(v: List<Double>, hop: Double): Set<Why> {
        val out = HashSet<Why>()
        val len = v.size * hop
        val head = (0.1 / hop).toInt()
        if (len >= 0.15 && v.size > head + 2) {
            val start = median(v.take(head)); val rest = median(v.drop(head))
            if (start < -0.4 && abs(rest) < 0.3) out += Why.SCOOP
        }
        if (len >= 0.5) {
            val body = v.drop(head)
            val third = body.size / 3
            if (third >= 2) {
                val d = median(body.takeLast(third)) - median(body.take(third))
                if (d <= -0.3) out += Why.DRIFT_FLAT
                if (d >= 0.3) out += Why.DRIFT_SHARP
            }
        }
        if (len >= 0.4) {
            val body = v.drop(head)
            val mean = body.average()
            val crossings = body.zipWithNext().count { (x, y) -> (x - mean) * (y - mean) < 0 }
            val p2p = (body.maxOrNull() ?: 0.0) - (body.minOrNull() ?: 0.0)
            if (crossings >= 4 && p2p >= 0.3 && abs(mean) > 0.25 && Why.DRIFT_FLAT !in out && Why.DRIFT_SHARP !in out) out += Why.VIBRATO_OFF
        }
        return out
    }

    private fun median(v: List<Double>): Double = v.sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] }

    /**
     * y = a + b·x by Theil–Sen: the median of the slopes between every pair, then the median intercept. A few notes
     * well off the beat don't pull the line toward themselves (least squares would, and then hide them).
     */
    private fun fit(x: List<Double>, y: List<Double>): Pair<Double, Double> {
        val slopes = ArrayList<Double>()
        for (p in x.indices) for (q in p + 1 until x.size) if (x[q] - x[p] > 1e-6) slopes += (y[q] - y[p]) / (x[q] - x[p])
        val b = if (slopes.isEmpty()) 1.0 else median(slopes)
        return median(x.indices.map { y[it] - b * x[it] }) to b
    }
}
