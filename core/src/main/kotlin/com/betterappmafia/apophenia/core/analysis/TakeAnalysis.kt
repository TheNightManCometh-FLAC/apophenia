package com.betterappmafia.apophenia.core.analysis

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.catalog.Scales
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import com.betterappmafia.apophenia.core.spelling.Spelling
import com.betterappmafia.apophenia.core.tools.Fft
import com.betterappmafia.apophenia.core.tools.KeySpeller
import com.betterappmafia.apophenia.core.tools.Yin
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Take analysis (plan §10.2): tempo candidates from onsets, a single-line note track, and key and mode
 * candidates with the evidence for each. No ML; everything here is signal processing that runs on a
 * worker after a take is saved. Results are suggestions: the user's entry always wins (plan §3.4).
 *
 * Input is mono float audio at any rate; it is reduced to [RATE] first.
 */
object TakeAnalysis {
    /** 2 added the chords (0.4.2). */
    const val VERSION = 2
    const val RATE = 24000
    /** [Claude] Notes are tracked over the first two minutes; longer files (imported songs) get tempo and key only. */
    const val NOTE_SECONDS = 120

    data class Result(val seconds: Double, val tempo: Tempo, val notes: List<Note>, val voiced: Double, val key: KeyResult, val chords: ChordFinder.Track)

    /**
     * [clickBeats] (seconds, from the first downbeat) and [meter] come from the metronome when the take was
     * recorded with it; otherwise the chords sit on the analysed pulse, from the first attack, in 4/4 [Claude].
     */
    fun analyse(x: FloatArray, rate: Int, minHz: Double = 30.0, maxHz: Double = 1000.0, clickBeats: List<Double>? = null, meter: TimeSignature = TimeSignature.of(4, 4)): Result {
        val y = Resample.to(x, rate, RATE)
        val env = Onsets.envelope(y, RATE)
        val onsets = Onsets.pick(env)
        val tempo = Tempo.estimate(env, onsets)
        val noteSpan = y.copyOf(minOf(y.size, RATE * NOTE_SECONDS))
        val (notes, voiced) = MonoNotes.track(noteSpan, RATE, minHz, maxHz, onsets)
        val key = KeyEvidence.rank(if (voiced >= 0.3 && notes.size >= 4) KeyEvidence.fromNotes(notes) else KeyEvidence.fromChroma(y, RATE))
        val seconds = y.size.toDouble() / RATE
        val beats = clickBeats ?: tempo.best?.let { bpm -> onsets.firstOrNull()?.let { first -> generateSequence(first) { it + 60.0 / bpm }.takeWhile { it < seconds }.toList() } }
        val chords = ChordFinder.find(y, RATE, beats, meter, key.best ?: key.candidates.firstOrNull()?.key)
        return Result(seconds, tempo, notes, voiced, key, chords)
    }
}

/** Block-average then linear resampling: enough for analysis, never for listening. */
object Resample {
    fun to(x: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to) return x
        val step = from.toDouble() / to
        // Average over the step first so what lands above the new Nyquist is mostly gone.
        val k = maxOf(1, step.toInt())
        val avg = if (k == 1) x else FloatArray(x.size) { i ->
            var s = 0f; var n = 0
            for (j in i until minOf(x.size, i + k)) { s += x[j]; n++ }
            s / n
        }
        val n = (x.size / step).toInt()
        return FloatArray(n) { i ->
            val p = i * step
            val a = p.toInt()
            val f = (p - a).toFloat()
            if (a + 1 < avg.size) avg[a] * (1 - f) + avg[a + 1] * f else avg[minOf(a, avg.size - 1)]
        }
    }
}

/** Onset strength as log-magnitude spectral flux, and onsets picked from it. */
object Onsets {
    const val FRAME = 1024
    const val HOP = 256

    /** [strength] one value per hop; [hopSeconds] its spacing; [energy] the frame's RMS (for silence). */
    class Envelope(val strength: FloatArray, val energy: FloatArray, val hopSeconds: Double)

    fun envelope(x: FloatArray, rate: Int): Envelope {
        val frames = if (x.size < FRAME) 0 else (x.size - FRAME) / HOP + 1
        val win = DoubleArray(FRAME) { 0.5 - 0.5 * cos(2 * PI * it / FRAME) }
        val half = FRAME / 2
        var prev = DoubleArray(half)
        val strength = FloatArray(frames)
        val energy = FloatArray(frames)
        val re = DoubleArray(FRAME); val im = DoubleArray(FRAME)
        for (f in 0 until frames) {
            var e = 0.0
            for (i in 0 until FRAME) { val v = x[f * HOP + i]; re[i] = v * win[i]; im[i] = 0.0; e += v * v }
            energy[f] = sqrt(e / FRAME).toFloat()
            Fft.transform(re, im)
            val mag = DoubleArray(half)
            var flux = 0.0
            for (b in 1 until half) {
                mag[b] = ln(1 + 100 * sqrt(re[b] * re[b] + im[b] * im[b]))
                val d = mag[b] - prev[b]
                if (d > 0) flux += d
            }
            strength[f] = if (f == 0) 0f else flux.toFloat()
            prev = mag
        }
        return Envelope(strength, energy, HOP.toDouble() / rate)
    }

    /** Peaks over a moving mean, at least 50 ms apart; times in seconds (the frame's centre). */
    fun pick(env: Envelope): List<Double> {
        val s = env.strength
        val out = ArrayList<Double>()
        val w = maxOf(1, (0.15 / env.hopSeconds).toInt())
        val gap = (0.05 / env.hopSeconds).toInt()
        var max = 0f
        for (v in s) if (v > max) max = v
        if (max <= 0f) return out
        var last = -gap - 1
        for (i in 1 until s.size - 1) {
            if (s[i] < s[i - 1] || s[i] < s[i + 1]) continue
            var mean = 0.0; var n = 0
            for (j in maxOf(0, i - w)..minOf(s.size - 1, i + w)) { mean += s[j]; n++ }
            mean /= n
            // A note stopping short also splatters across the spectrum: only rising energy is an attack.
            val rising = env.energy[i] > env.energy[maxOf(0, i - 2)] * 1.05f
            if (rising && s[i] > mean * 1.5 + 0.05 * max && i - last > gap) {
                out += (i * Onsets.HOP + FRAME / 2).toDouble() * env.hopSeconds / HOP
                last = i
            }
        }
        return out
    }
}

/**
 * Tempo candidates (plan §10.2): the fastest clear pulse in the onsets, with its half and double, or
 * free time. [clarity] is the autocorrelation peak against lag 0 (1 = perfectly periodic).
 */
data class Tempo(val candidates: List<Candidate>, val clarity: Double, val free: Boolean) {
    /** [why] says how it relates to the first: "pulse", "half", "double", "next". */
    data class Candidate(val bpm: Double, val why: String)

    val best: Double? get() = if (free) null else candidates.firstOrNull()?.bpm

    companion object {
        const val MIN_BPM = 40.0
        const val MAX_BPM = 240.0
        /** [Claude] Below this the onsets aren't regular enough to call a tempo. */
        const val MIN_CLARITY = 0.3

        fun estimate(env: Onsets.Envelope, onsets: List<Double>): Tempo {
            val s = env.strength
            val seconds = s.size * env.hopSeconds
            if (onsets.size < 6 || seconds < 4.0) return Tempo(emptyList(), 0.0, true)
            var mean = 0.0
            for (v in s) mean += v
            mean /= s.size
            val d = DoubleArray(s.size) { s[it] - mean }
            fun acf(lag: Int): Double { var a = 0.0; for (i in 0 until d.size - lag) a += d[i] * d[i + lag]; return a }
            val zero = acf(0)
            if (zero <= 0) return Tempo(emptyList(), 0.0, true)
            val minLag = (60.0 / MAX_BPM / env.hopSeconds).toInt()
            val maxLag = minOf(d.size / 2, (60.0 / (MIN_BPM / 2) / env.hopSeconds).toInt() + 1)
            val a = DoubleArray(maxLag + 2) { if (it in 1..maxLag + 1 && it < d.size) acf(it) / zero else 0.0 }
            // The strongest peak at or above MIN_BPM / 2, then the fastest peak nearly as strong: that's the pulse.
            val peaks = (maxOf(2, minLag)..maxLag).filter { a[it] >= a[it - 1] && a[it] >= a[it + 1] && a[it] > 0 }
            val top = peaks.maxByOrNull { a[it] } ?: return Tempo(emptyList(), 0.0, true)
            val pulse = peaks.filter { a[it] >= a[top] * 0.8 }.minOrNull() ?: top
            val clarity = a[pulse]
            // Parabolic refinement of the lag.
            val y0 = a[pulse - 1]; val y1 = a[pulse]; val y2 = a[pulse + 1]
            val den = y0 - 2 * y1 + y2
            val lag = pulse + if (den != 0.0) 0.5 * (y0 - y2) / den else 0.0
            val fast = 60.0 / (lag * env.hopSeconds)
            // The pulse or its half, whichever sits nearer 120 (log scale), leads; accents on every other pulse decide it.
            val slow = fast / 2
            val alternate = accentRatio(env, onsets, 60.0 / fast)
            val leadSlow = slow >= MIN_BPM && (alternate > 1.3 || (alternate > 0.77 && abs(log2(slow / 120)) < abs(log2(fast / 120))))
            val lead = if (leadSlow) slow else fast
            val list = buildList {
                add(Candidate(lead, "pulse"))
                if (lead / 2 >= MIN_BPM / 2) add(Candidate(lead / 2, "half"))
                if (lead * 2 <= MAX_BPM * 1.25) add(Candidate(lead * 2, "double"))
            }
            return Tempo(list, clarity, clarity < MIN_CLARITY)
        }

        /** Mean onset strength on even pulses over odd ones, from the onset times; 1 when there's no pattern. */
        private fun accentRatio(env: Onsets.Envelope, onsets: List<Double>, period: Double): Double {
            if (onsets.size < 4) return 1.0
            // Phase from the first onset; each onset votes for the pulse it's nearest.
            var even = 0.0; var odd = 0.0; var ne = 0; var no = 0
            for (t in onsets) {
                val k = ((t - onsets[0]) / period).roundToInt()
                if (abs((t - onsets[0]) / period - k) > 0.25) continue
                val i = ((t / env.hopSeconds) - Onsets.FRAME / 2.0 / Onsets.HOP).roundToInt().coerceIn(0, env.strength.size - 1)
                if (k % 2 == 0) { even += env.strength[i]; ne++ } else { odd += env.strength[i]; no++ }
            }
            if (ne == 0 || no == 0) return if (no == 0 && ne > 2) 2.0 else 1.0
            return (even / ne) / maxOf(1e-9, odd / no)
        }
    }
}

/** One note of a single-line track: seconds, a MIDI note, how far off it was in cents, and how sure. */
data class Note(val start: Double, val end: Double, val midi: Int, val cents: Int, val confidence: Double)

/**
 * Single-line notes (plan §10.3, the fast first pass): YIN every 20 ms over a window long enough for
 * the lowest pitch, frames grouped while the pitch holds, split at onsets and pitch changes.
 * Returns the notes and the fraction of time something was pitched.
 */
object MonoNotes {
    const val HOP_SECONDS = 0.02
    const val MIN_NOTE = 0.06

    fun track(x: FloatArray, rate: Int, minHz: Double, maxHz: Double, onsets: List<Double>): Pair<List<Note>, Double> {
        val win = (2.2 * rate / minHz).toInt()
        val hop = (HOP_SECONDS * rate).toInt()
        if (x.size < win) return emptyList<Note>() to 0.0
        val frames = (x.size - win) / hop + 1
        val pitch = DoubleArray(frames) { Double.NaN }
        val conf = DoubleArray(frames)
        val buf = FloatArray(win)
        for (f in 0 until frames) {
            System.arraycopy(x, f * hop, buf, 0, win)
            Yin.estimate(buf, rate, minHz, maxHz)?.let { e -> pitch[f] = 69 + 12 * log2(e.hz / 440.0); conf[f] = e.confidence }
        }
        // Frame f covers [f*hop, f*hop+win]; its time is the window's centre.
        val centre = win / 2.0 / rate
        fun time(f: Int) = f * HOP_SECONDS + centre
        val onsetFrames = onsets.map { ((it - centre) / HOP_SECONDS).roundToInt() }.toSet()
        val notes = ArrayList<Note>()
        var start = -1
        val run = ArrayList<Double>()
        fun close(endFrame: Int) {
            if (start >= 0 && run.isNotEmpty() && (endFrame - start) * HOP_SECONDS >= MIN_NOTE) {
                val m = run.sorted()[run.size / 2]
                val midi = m.roundToInt()
                val c = (start until endFrame).map { conf[it] }.average()
                notes += Note(time(start) - HOP_SECONDS / 2, time(endFrame - 1) + HOP_SECONDS / 2, midi, ((m - midi) * 100).roundToInt(), c)
            }
            start = -1; run.clear()
        }
        for (f in 0 until frames) {
            val p = pitch[f]
            if (p.isNaN()) { close(f); continue }
            if (start >= 0) {
                val m = run.sorted()[run.size / 2]
                // An onset splits a held pitch (a repeated note), unless it is this note's own attack found a little late.
                if (abs(p - m) > 0.6 || (f in onsetFrames && (f - start) * HOP_SECONDS > 0.08)) close(f)
            }
            if (start < 0) start = f
            run += p
        }
        close(frames)
        val voiced = pitch.count { !it.isNaN() }.toDouble() / frames
        return notes to voiced
    }
}

/** Key and mode candidates with the evidence for each (plan §10.2; P0 research R1). */
data class KeyResult(val candidates: List<Candidate>, val uncertain: Boolean, val from: String) {
    /** [reason] in words: the notes used, what's outside the scale, and the home-note clues. */
    data class Candidate(val key: KeyMode, val score: Double, val reason: String)

    val best: KeyMode? get() = if (uncertain) null else candidates.firstOrNull()?.key
}

object KeyEvidence {
    /** The modes ranked: the seven diatonic modes, then harmonic and melodic minor (a little behind when they fit the same). */
    val MODES = listOf("major", "dorian", "phrygian", "lydian", "mixolydian", "aeolian", "locrian", "harmonic-minor", "melodic-minor")
    private val EXTRA_PENALTY = mapOf("harmonic-minor" to 0.04, "melodic-minor" to 0.04)

    /**
     * What a key is chosen from: time spent on each pitch class, and home-note clues per pitch class
     * (0..1) with the words for them. [from] is "notes" or "chroma".
     */
    class Evidence(val weights: DoubleArray, val hints: DoubleArray, val clues: Map<Int, List<String>>, val from: String)

    fun fromNotes(notes: List<Note>): Evidence {
        val w = DoubleArray(12); val h = DoubleArray(12)
        val clues = HashMap<Int, MutableList<String>>()
        for (n in notes) w[Math.floorMod(n.midi, 12)] += n.end - n.start
        fun clue(pc: Int, amount: Double, words: String) { h[pc] += amount; clues.getOrPut(pc) { ArrayList() } += words }
        notes.lastOrNull()?.let { clue(Math.floorMod(it.midi, 12), 0.5, "ends on it") }
        notes.minByOrNull { it.midi }?.let { clue(Math.floorMod(it.midi, 12), 0.3, "the lowest note") }
        notes.maxByOrNull { it.end - it.start }?.let { clue(Math.floorMod(it.midi, 12), 0.2, "the longest note") }
        notes.firstOrNull()?.let { clue(Math.floorMod(it.midi, 12), 0.1, "starts on it") }
        return Evidence(w, h, clues, "notes")
    }

    /** Chroma from the whole file (for chords and mixes): log magnitudes 55 Hz–2 kHz folded to pitch classes, the bass band as the clue. */
    fun fromChroma(x: FloatArray, rate: Int): Evidence {
        val n = 8192
        val hop = n
        val w = DoubleArray(12); val bass = DoubleArray(12)
        val win = DoubleArray(n) { 0.5 - 0.5 * cos(2 * PI * it / n) }
        val re = DoubleArray(n); val im = DoubleArray(n)
        val pcOf = IntArray(n / 2) { b ->
            val hz = b * rate.toDouble() / n
            if (hz < 55 || hz > 2000) -1 else Math.floorMod((69 + 12 * log2(hz / 440.0)).roundToInt(), 12)
        }
        var f = 0
        while (f + n <= x.size) {
            for (i in 0 until n) { re[i] = x[f + i] * win[i]; im[i] = 0.0 }
            Fft.transform(re, im)
            val frame = DoubleArray(12)
            // Down low a semitone is about one bin, so the bass is the single strongest bin under 250 Hz, not a fold.
            var lowBin = -1; var lowMag = 0.0
            for (b in 1 until n / 2) {
                val pc = pcOf[b]
                if (pc < 0) continue
                val m = sqrt(re[b] * re[b] + im[b] * im[b])
                frame[pc] += m
                if (b * rate.toDouble() / n < 250 && m > lowMag) { lowMag = m; lowBin = b }
            }
            // Each frame counts by its shape, not its loudness.
            val total = frame.sum()
            if (total > 1e-6) {
                for (i in 0 until 12) w[i] += frame[i] / total
                if (lowBin > 0) bass[pcOf[lowBin]] += lowMag / total
            }
            f += hop
        }
        val h = DoubleArray(12)
        val clues = HashMap<Int, List<String>>()
        val top = bass.indices.maxByOrNull { bass[it] }
        if (top != null && bass[top] > 0) { h[top] = 0.6; clues[top] = listOf("strongest in the bass") }
        return Evidence(w, h, clues, "chroma")
    }

    fun rank(e: Evidence): KeyResult {
        val total = e.weights.sum()
        if (total <= 0) return KeyResult(emptyList(), true, e.from)
        val scored = ArrayList<Triple<Int, String, Double>>()
        for (t in 0 until 12) for (m in MODES) {
            val pcs = Scales.byId(m).semitones.map { (t + it) % 12 }.toSet()
            var agree = 0.0
            for (pc in 0 until 12) agree += e.weights[pc] * if (pc in pcs) 1.0 else -1.3
            scored += Triple(t, m, agree / total + 0.45 * e.hints[t] - (EXTRA_PENALTY[m] ?: 0.0))
        }
        scored.sortByDescending { it.third }
        val top = scored.take(6).map { (t, m, s) ->
            val key = keyFor(t, m)
            KeyResult.Candidate(key, s, reason(key, t, e))
        }
        val first = scored[0]
        // Relative modes share every note; only home-note evidence separates them (P0 research R1).
        // A pentatonic riff fits two modes on the same home note equally: that's uncertain too.
        val tie = scored.drop(1).any {
            (it.third > first.third - 0.05 && it.first != first.first) || (it.third > first.third - 0.02 && it.second != first.second)
        }
        return KeyResult(top, tie || first.third < 0.5, e.from)
    }

    /** The tonic spelled with the fewest accidentals in the key (D♭ major, not C♯; but F♯ for F♯ minor over G♭). */
    fun keyFor(pc: Int, mode: String): KeyMode {
        val degrees = Scales.byId(mode).degrees
        val tonic = KeyMode.TONICS.filter { it.pitchClass == pc }.minByOrNull { r ->
            runCatching { abs(r.alter) * 10 + Spelling.spell(r, degrees, keepRoot = true).notes.sumOf { abs(it.alter) } * 3 }.getOrDefault(1000)
        } ?: PitchName.parse("C")
        return KeyMode(tonic, mode)
    }

    private fun reason(key: KeyMode, t: Int, e: Evidence): String {
        val speller = KeySpeller(key)
        val total = e.weights.sum()
        val pcs = Scales.byId(key.scaleId).semitones.map { (t + it) % 12 }.toSet()
        val used = (0 until 12).filter { e.weights[it] / total >= 0.03 }
        val outside = used.filter { it !in pcs }
        val name = { pc: Int -> KeyMode.displayName(speller.name(pc)) }
        return buildString {
            append(if (e.from == "notes") "Notes " else "Strongest notes ")
            append(used.sortedBy { Math.floorMod(it - t, 12) }.joinToString(" ") { name(it) })
            if (outside.isEmpty()) append(", all in the scale") else append("; outside it: ").append(outside.joinToString(" ") { "${name(it)} ${(e.weights[it] / total * 100).roundToInt()}%" })
            e.clues[t]?.let { append(". ${name(t)} is ").append(it.joinToString(", ")) }
            append('.')
        }
    }
}

/**
 * A result as text for storage: one tab-separated record per line, the first `analysis<TAB>VERSION`.
 * Reasons never hold a tab or a line break. [decode] returns null for anything it can't read, including
 * a newer version, and the worker simply analyses the take again.
 */
object AnalysisText {
    fun encode(r: TakeAnalysis.Result): String = buildString {
        fun line(vararg f: Any) { append(f.joinToString("\t") { it.toString().replace('\t', ' ').replace('\n', ' ') }).append('\n') }
        line("analysis", TakeAnalysis.VERSION)
        line("seconds", r.seconds)
        line("tempo", r.tempo.free, r.tempo.clarity)
        r.tempo.candidates.forEach { line("bpm", it.bpm, it.why) }
        line("voiced", r.voiced)
        line("key", r.key.uncertain, r.key.from)
        r.key.candidates.forEach { line("mode", it.key.token, it.score, it.reason) }
        r.notes.forEach { line("note", it.start, it.end, it.midi, it.cents, it.confidence) }
        line("chords", r.chords.meter, r.chords.free)
        r.chords.beats.forEach { line("chord", it.time, it.label, it.match) }
    }

    fun decode(s: String?): TakeAnalysis.Result? = runCatching {
        val lines = s!!.lines().filter { it.isNotEmpty() }.map { it.split('\t') }
        require(lines[0] == listOf("analysis", TakeAnalysis.VERSION.toString()))
        fun one(tag: String) = lines.first { it[0] == tag }
        val tempoLine = one("tempo")
        val keyLine = one("key")
        val chordLine = one("chords")
        TakeAnalysis.Result(
            seconds = one("seconds")[1].toDouble(),
            tempo = Tempo(lines.filter { it[0] == "bpm" }.map { Tempo.Candidate(it[1].toDouble(), it[2]) }, tempoLine[2].toDouble(), tempoLine[1].toBoolean()),
            notes = lines.filter { it[0] == "note" }.map { Note(it[1].toDouble(), it[2].toDouble(), it[3].toInt(), it[4].toInt(), it[5].toDouble()) },
            voiced = one("voiced")[1].toDouble(),
            key = KeyResult(
                lines.filter { it[0] == "mode" }.map { KeyResult.Candidate(KeyMode.parseOrNull(it[1])!!, it[2].toDouble(), it[3]) },
                keyLine[1].toBoolean(), keyLine[2],
            ),
            chords = ChordFinder.Track(
                lines.filter { it[0] == "chord" }.map { ChordFinder.Beat(it[1].toDouble(), it[2], it[3].toDouble()) },
                TimeSignature.parse(chordLine[1]), chordLine[2].toBoolean(),
            ),
        )
    }.getOrNull()
}
