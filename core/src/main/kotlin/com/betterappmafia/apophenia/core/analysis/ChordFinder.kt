package com.betterappmafia.apophenia.core.analysis

import com.betterappmafia.apophenia.core.catalog.Chords
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import com.betterappmafia.apophenia.core.tools.Fft
import com.betterappmafia.apophenia.core.tools.KeySpeller
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Finding the chords (plan §10.3 "per-beat chord recognition → chord chart"; P0 research R2): a chroma
 * per beat, matched against chord templates from the core catalog, smoothed so a chord holds unless the
 * sound really changes (Viterbi with a cost per change), with "no chord" when nothing fits. The bass
 * names an inversion when it's a chord tone other than the root. These are chord labels for a chart,
 * not voicings.
 */
object ChordFinder {
    /** [Claude] The qualities it names: the common triads, power chord, sevenths and suspensions. */
    val QUALITIES = listOf("maj", "m", "5", "7", "maj7", "m7", "dim", "aug", "sus2", "sus4", "m7b5", "dim7")
    const val NO_CHORD = "N.C."
    /** [Claude] Below this match a beat is "no chord". */
    const val MIN_MATCH = 0.6
    /** [Claude] What one change of chord costs against the sum of matches. */
    const val CHANGE_COST = 0.08
    /** [Claude] Without a beat, the sound is looked at in steps this long (free time). */
    const val FREE_STEP = 0.5

    /** One beat's chord: [label] as written in a chart (ASCII, e.g. "F#m7", "C/E", "N.C."), and how well it matched. */
    data class Beat(val time: Double, val label: String, val match: Double)

    /** The chords of a take, beat by beat; [free] means no beat was found and [beats] are fixed steps. */
    data class Track(val beats: List<Beat>, val meter: TimeSignature, val free: Boolean) {
        /** As a chord chart (`core/sheet/ChordChart`): a bar per meter, the chord named at each change and each bar's start. */
        fun chart(): String = buildString {
            append("{time: $meter}\n")
            if (free) {
                // No beat: one bar per chord, in order (the rhythm isn't known).
                val changes = beats.map { it.label }.fold(ArrayList<String>()) { acc, l -> if (acc.lastOrNull() != l) acc += l; acc }
                changes.chunked(4).forEach { line -> append(line.joinToString(" ") { "| $it ${List(meter.beatsPerBar - 1) { "/" }.joinToString(" ")}" }).append(" |\n") }
                return@buildString
            }
            val per = meter.beatsPerBar
            var previous: String? = null
            beats.chunked(per).chunked(4).forEach { line ->
                line.forEach { bar ->
                    append("| ")
                    bar.forEachIndexed { i, b ->
                        append(if (i > 0 && b.label == previous) HOLD else b.label).append(' ')
                        previous = b.label
                    }
                    repeat(per - bar.size) { append("$HOLD ") }
                }
                append("|\n")
            }
        }
    }

    private const val HOLD = "/"

    /** Pitch-class offset and weight of harmonics 1–4 (0.6 per harmonic, as in harmonic-template chord recognition). */
    private val HARMONICS = listOf(0 to 1.0, 0 to 0.6, 7 to 0.36, 0 to 0.216)

    private class Template(val root: Int, val quality: String, val pcs: Set<Int>, val vector: DoubleArray)

    private val templates: List<Template> by lazy {
        QUALITIES.flatMap { q ->
            val def = Chords.byId(q)
            (0 until 12).map { r ->
                val pcs = def.semitones.map { Math.floorMod(r + it, 12) }.toSet()
                // Each tone also brings its overtones (harmonics 2–4, weight 0.6 a step down): the 3rd
                // harmonic is a fifth up, so a triad's D brings an A, and that A isn't a major seventh.
                val v = DoubleArray(12)
                for (p in pcs) for ((h, w) in HARMONICS) v[Math.floorMod(p + h, 12)] += w
                val n = sqrt(v.sumOf { it * it })
                Template(r, q, pcs, DoubleArray(12) { v[it] / n })
            }
        }
    }

    /** Chroma and the bass note of each segment [bounds] (seconds; n+1 edges for n segments). */
    /** Per segment: chroma (linear magnitudes, so overtones keep their real, smaller size), the bass note, and the mean frame energy. */
    class Segments(val chroma: List<DoubleArray>, val bass: List<DoubleArray>, val energy: DoubleArray)

    fun chroma(x: FloatArray, rate: Int, bounds: List<Double>): Segments {
        val n = 4096
        val hop = 1024
        val win = DoubleArray(n) { 0.5 - 0.5 * cos(2 * PI * it / n) }
        val pcOf = IntArray(n / 2) { b ->
            val hz = b * rate.toDouble() / n
            if (hz < 60 || hz > 2000) -1 else Math.floorMod((69 + 12 * log2(hz / 440.0)).roundToInt(), 12)
        }
        val segs = bounds.size - 1
        val chroma = List(segs) { DoubleArray(12) }
        val energy = DoubleArray(segs)
        val frames = IntArray(segs)
        val bass = List(segs) { DoubleArray(12) }
        val re = DoubleArray(n); val im = DoubleArray(n)
        var f = 0
        var seg = 0
        while (f + n <= x.size && seg < segs) {
            val centre = (f + n / 2).toDouble() / rate
            while (seg < segs && centre >= bounds[seg + 1]) seg++
            if (seg >= segs) break
            if (centre >= bounds[seg]) {
                for (i in 0 until n) { re[i] = x[f + i] * win[i]; im[i] = 0.0 }
                Fft.transform(re, im)
                var lowBin = -1; var lowMag = 0.0
                for (b in 1 until n / 2) {
                    val pc = pcOf[b]
                    if (pc < 0) continue
                    val m = sqrt(re[b] * re[b] + im[b] * im[b])
                    chroma[seg][pc] += m
                    energy[seg] += m * m
                    if (b * rate.toDouble() / n < 220 && m > lowMag) { lowMag = m; lowBin = b }
                }
                if (lowBin > 0) bass[seg][pcOf[lowBin]] += lowMag
                frames[seg]++
            }
            f += hop
        }
        for (s in 0 until segs) if (frames[s] > 0) energy[s] /= frames[s]
        return Segments(chroma, bass, energy)
    }

    /**
     * The chords over segments whose chroma and bass are given. Root names are spelled in [key] (C major
     * when unknown), ASCII as charts store them.
     */
    fun label(seg: Segments, key: KeyMode?): List<Pair<String, Double>> {
        val chroma = seg.chroma; val bass = seg.bass
        if (chroma.isEmpty()) return emptyList()
        val speller = KeySpeller(key ?: KeyMode.parseOrNull("C major")!!)
        val k = templates.size  // index k = no chord
        val loudest = seg.energy.maxOrNull() ?: 0.0
        // Match of each segment to each template (cosine of the mean-removed chroma), and to "no chord".
        val match = chroma.mapIndexed { s, c ->
            val total = c.sum()
            val mean = total / 12
            val v = DoubleArray(12) { maxOf(0.0, c[it] - mean) }
            val norm = sqrt(v.sumOf { it * it })
            val row = DoubleArray(k + 1)
            for ((i, t) in templates.withIndex()) {
                var dot = 0.0
                for (p in 0 until 12) dot += v[p] * t.vector[p]
                // A little behind for every extra note, so a triad wins a tie with its seventh.
                row[i] = (if (norm > 0) dot / norm else 0.0) - 0.02 * (t.pcs.size - 3).coerceAtLeast(0)
            }
            // [Claude] Under 2% of the loudest beat's energy (-17 dB) is silence.
            row[k] = if (seg.energy[s] < loudest * 0.02 || norm == 0.0) 1.0 else MIN_MATCH
            row
        }
        // Viterbi: the path with the best total match, less CHANGE_COST for every change.
        val n = match.size
        val score = Array(n) { DoubleArray(k + 1) }
        val back = Array(n) { IntArray(k + 1) }
        score[0] = match[0].copyOf()
        for (t in 1 until n) {
            var bestPrev = 0
            for (j in 0..k) if (score[t - 1][j] > score[t - 1][bestPrev]) bestPrev = j
            for (j in 0..k) {
                val stay = score[t - 1][j]
                val move = score[t - 1][bestPrev] - CHANGE_COST
                if (stay >= move) { score[t][j] = stay + match[t][j]; back[t][j] = j }
                else { score[t][j] = move + match[t][j]; back[t][j] = bestPrev }
            }
        }
        val path = IntArray(n)
        path[n - 1] = (0..k).maxByOrNull { score[n - 1][it] }!!
        for (t in n - 1 downTo 1) path[t - 1] = back[t][path[t]]
        return path.mapIndexed { t, j ->
            if (j == k) NO_CHORD to match[t][k]
            else {
                val tpl = templates[j]
                val root = speller.name(tpl.root)
                var label = Chords.byId(tpl.quality).symbols.first().replaceFirst("C", root.toString())
                // An inversion: the strongest bass note is a chord tone other than the root.
                val b = bass[t]
                val low = (0 until 12).maxByOrNull { b[it] }
                if (low != null && b[low] > 0 && low != tpl.root && low in tpl.pcs) label += "/" + speller.name(low)
                label to match[t][j]
            }
        }
    }

    /**
     * The chords of a take: on [beats] (seconds) when there is a beat, otherwise in [FREE_STEP] steps.
     * [meter] is the click's when it ran, else 4/4 [Claude].
     */
    fun find(x: FloatArray, rate: Int, beats: List<Double>?, meter: TimeSignature, key: KeyMode?): Track {
        val seconds = x.size.toDouble() / rate
        val free = beats == null || beats.size < 2
        val starts = if (free) generateSequence(0.0) { it + FREE_STEP }.takeWhile { it < seconds }.toList() else beats!!.filter { it < seconds }
        if (starts.isEmpty()) return Track(emptyList(), meter, free)
        val step = if (free) FREE_STEP else (starts.last() - starts.first()) / maxOf(1, starts.size - 1)
        val bounds = starts + minOf(seconds, starts.last() + step)

        val labels = label(chroma(x, rate, bounds), key)
        return Track(starts.zip(labels) { t, (l, m) -> Beat(t, l, m) }, meter, free)
    }
}
