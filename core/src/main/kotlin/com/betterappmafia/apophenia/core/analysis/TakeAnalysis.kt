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
 * candidates with the evidence for each. Runs on a worker after a take is saved, using model beats
 * and transcribed notes when supplied. The user's entry always wins (plan §3.4).
 *
 * Input is mono float audio at any rate; it is reduced to [RATE] first.
 */
object TakeAnalysis {
    /** 2 added the chords (0.4.2). 3 (0.9.17): speech gets no key or chords, vibrato is one note, chords and keys from the bass; every take is analysed again.
     *  4 (0.9.19): tempo and the chords' beats from Beat This!, and speech gets no tempo; every take is analysed again.
     *  5 (0.9.20): beats always give a tempo (uneven ones say so), as the key always does; every take is analysed again.
     *  6 (0.9.22): rhythmic guitar survives speech detection; chords combine sound with transcribed notes.
     *  7 (0.9.23): retain quiet detected chord tones; combine bass evidence and allow inversions.
     *  8 (0.9.24): compress pitch-class evidence so repeated/louder roots do not bury detected chord tones.
     *  9 (0.9.25): harmonic spectral fitting for chord evidence, with or without a transcription. */
    const val VERSION = 9
    const val RATE = 24000
    /** [Claude] Notes are tracked over the first two minutes; longer files (imported songs) get tempo and key only. */
    const val NOTE_SECONDS = 120
    /** [Claude] Below this share of held pitch ([MonoNotes.held]) a single line is speech: between speech's 0.27 and singing's 0.44. */
    const val SPEECH_HELD = 0.35
    /** [Claude] Speech is pitched only part of the time (TTS speech 0.34–0.62); notes ringing into each other (a piano arpeggio, 0.83) hold little too. */
    const val SPEECH_VOICED = 0.7
    /** [Claude] Bypass the pitch-only speech heuristic with this many steady model beats; eleven speech probes had at most 5. */
    const val MUSIC_MIN_BEATS = 8

    /** [oneAtATime]: what the take's Notes → MIDI said (true: one note at a time), or null when it hasn't been transcribed. */
    data class Result(val seconds: Double, val tempo: Tempo, val notes: List<Note>, val voiced: Double, val key: KeyResult, val chords: ChordFinder.Track, val oneAtATime: Boolean? = null)

    /**
     * The share of sounding time with two or more notes at once, from a transcription's notes (start to end, seconds).
     * [Claude] Under [SINGLE_LINE] is one note at a time. Measured 27 Oct 2026 on the emulator's Basic Pitch: a spoken
     * memo 0.00, a sung line with ±85-cent vibrato 0.24 (Basic Pitch cut its 6 notes into 55, some overlapping), piano
     * chords C F G C 0.66 (three or more at once 0.54). Two-note chords (power chords) stay chords. Three takes only.
     */
    fun overlap(notes: List<Pair<Double, Double>>): Double {
        val edges = notes.flatMap { (s, e) -> listOf(s to 1, e to -1) }.sortedWith(compareBy({ it.first }, { it.second }))
        var count = 0; var last = 0.0; var sounding = 0.0; var two = 0.0
        for ((t, d) in edges) {
            if (count >= 1) sounding += t - last
            if (count >= 2) two += t - last
            count += d; last = t
        }
        return if (sounding > 0) two / sounding else 0.0
    }
    const val SINGLE_LINE = 0.4

    /**
     * [clickBeats] (seconds, from the first downbeat) and [meter] come from the metronome when the take was
     * recorded with it; otherwise the chords sit on the analysed pulse, from the first attack, in 4/4 [Claude].
     * [oneAtATime] comes from the take's Notes → MIDI when it has one (Christopher, 27 Oct 2026): a single line gets
     * no chord chart, and its key comes from its notes only.
     * [found] is what [BeatThis] found in the take (the app runs the model); then the tempo comes from its beats and the
     * chords sit on them from its first downbeat, and the onset estimate is only the fallback without it.
     */
    fun analyse(x: FloatArray, rate: Int, minHz: Double = 30.0, maxHz: Double = 1000.0, clickBeats: List<Double>? = null, meter: TimeSignature = TimeSignature.of(4, 4), oneAtATime: Boolean? = null, found: BeatThis.Track? = null, heard: List<BasicPitch.TimedNote>? = null): Result {
        val y = Resample.to(x, rate, RATE)
        val env = Onsets.envelope(y, RATE)
        val onsets = Onsets.pick(env)
        val pulse = found?.let { Tempo.fromBeats(it.beats) } ?: Tempo.estimate(env, onsets)
        val noteSpan = y.copyOf(minOf(y.size, RATE * NOTE_SECONDS))
        val (notes, voiced, held) = MonoNotes.track(noteSpan, RATE, minHz, maxHz, Onsets.pick(Onsets.envelope(noteSpan, RATE, vibrato = true)))
        val line = voiced >= 0.3 && notes.size >= 4
        // A single line that hardly holds a pitch is speech (Christopher, 27 Oct 2026: a spoken memo gets no key).
        // Chords also look like a line (the pitch tracker finds one pitch in them), but their notes name no key: when the
        // notes leave it open and the sound as a whole names one, that one is taken. Measured 27 Oct 2026 on ten piano
        // progressions (Iowa samples): the notes named none of the ten. [Claude]
        // A steady beat exempts a take from this speech heuristic (0.9.22): strummed chords confuse the pitch tracker, and 63 of GuitarSet's 180
        // strummed takes looked like speech by held pitch alone. Measured 28 Oct 2026: speech (eleven takes, Windows TTS
        // voices and the memo) had at most 5 beats, at most 50% evenly spaced; those 63 guitar takes at least 61%.
        val steady = found != null && found.beats.size >= MUSIC_MIN_BEATS && pulse.clarity >= Tempo.MIN_REGULARITY
        val key = when {
            line && held < SPEECH_HELD && voiced < SPEECH_VOICED && !steady -> KeyResult(emptyList(), true, "speech")
            // A known single line never takes the sound-as-a-whole key: that one is read from its overtones.
            oneAtATime == true -> KeyEvidence.rank(KeyEvidence.fromNotes(notes))
            line -> KeyEvidence.rank(KeyEvidence.fromNotes(notes)).let { byNotes ->
                if (!byNotes.uncertain) byNotes else KeyEvidence.rank(KeyEvidence.fromChroma(y, RATE)).takeIf { !it.uncertain } ?: byNotes
            }
            else -> KeyEvidence.rank(KeyEvidence.fromChroma(y, RATE))
        }
        // Nor a tempo (Christopher, 28 Oct 2026: speech gets no BPM).
        val tempo = if (key.from == "speech") Tempo(emptyList(), pulse.clarity, true, pulse.from) else pulse
        val seconds = y.size.toDouble() / RATE
        // The beats found, from the first downbeat (the bar line), when they hold a tempo.
        val foundBeats = found?.takeIf { tempo.best != null }?.let { f -> f.beats.filter { it >= (f.downbeats.firstOrNull() ?: f.beats.first()) - 1e-6 } }?.takeIf { it.size >= 2 }
        val beats = clickBeats ?: foundBeats ?: tempo.best?.let { bpm -> onsets.firstOrNull()?.let { first -> generateSequence(first) { it + 60.0 / bpm }.takeWhile { it < seconds }.toList() } }
        // Speech has no chords either (Christopher, 27 Oct 2026: "No chart"): nothing for the chart, In yours or Ear to read.
        val chords = if (key.from == "speech" || oneAtATime == true) ChordFinder.Track(emptyList(), meter, beats == null)
            else ChordFinder.find(y, RATE, beats, meter, key.best ?: key.candidates.firstOrNull()?.key, heard)
        return Result(seconds, tempo, notes, voiced, key, chords, oneAtATime)
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

    fun envelope(x: FloatArray, rate: Int, vibrato: Boolean = false): Envelope {
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
            for (b in 1 until half) mag[b] = ln(1 + 100 * sqrt(re[b] * re[b] + im[b] * im[b]))
            // Vibrato suppression (SuperFlux, Böck & Widmer, DAFx 2013): each bin is compared with the loudest of its
            // neighbours a frame earlier, so a harmonic drifting with vibrato isn't new energy. The paper's ±1 band of a
            // 24-per-octave filterbank is about ±50 cents; these bins are linear, so the reach grows with frequency,
            // about ±6% (±100 cents), at least one bin. [Claude] the width. Only for splitting notes ([vibrato]): on a full
            // mix it blurred the beat (a YuE2 song's pulse regularity fell from 0.44 to 0.14), so tempo, beats and drum
            // checks keep the plain flux.
            var flux = 0.0
            for (b in 1 until half) {
                val k = if (vibrato) maxOf(1, (b * 0.06).toInt()) else 0
                var near = 0.0
                for (j in maxOf(1, b - k)..minOf(half - 1, b + k)) if (prev[j] > near) near = prev[j]
                val d = mag[b] - near
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
        // A take that starts mid-sound (an imported file, a tight trim) has its first attack in frame 0, which has no
        // frame before it to rise from: a loud first frame counts. [Claude] 30% of the loudest frame.
        val loudest = env.energy.maxOrNull() ?: 0f
        if (env.energy.isNotEmpty() && env.energy[0] >= 0.3f * loudest) { out += (FRAME / 2).toDouble() * env.hopSeconds / HOP; last = 0 }
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
data class Tempo(val candidates: List<Candidate>, val clarity: Double, val free: Boolean, val from: String = "onsets") {
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

        /**
         * Below this share of evenly spaced beats the tempo is shown as uneven (check it). It is still given, as the key
         * is when it is uncertain (Christopher, 28 Oct 2026: "It runs that for key immediately, it should also for the
         * tempo"). Measured 28 Oct 2026 on 42 MUSDB18 excerpts (7 s) with the drums taken out: of the tempos under 0.55,
         * 5 of 6 were wrong; above it, 33 of 36 were right (or half or double).
         */
        const val MIN_REGULARITY = 0.55

        /**
         * The tempo of beats found by [BeatThis] ("beats"): the beat period by least squares over the beats, each
         * numbered by counting every gap in whole median intervals [Claude]; half and double as alternatives.
         * [clarity] is the share of gaps within 10% of a whole number of median intervals (a missed beat is still
         * even). Free time only with fewer than four beats.
         */
        fun fromBeats(beats: List<Double>): Tempo {
            if (beats.size < 4) return Tempo(emptyList(), 0.0, true, "beats")
            val gaps = beats.zipWithNext { a, b -> b - a }.sorted()
            val median = gaps[gaps.size / 2]
            val regularity = gaps.count { g -> val k = maxOf(1, (g / median).roundToInt()); abs(g / (k * median) - 1) < 0.1 }.toDouble() / gaps.size
            // Each gap counts as a whole number of beats (a missed beat is two), so the count can't drift over a long take.
            val n = DoubleArray(beats.size)
            for (i in 1 until beats.size) n[i] = n[i - 1] + maxOf(1, ((beats[i] - beats[i - 1]) / median).roundToInt())
            val mn = n.average(); val mt = beats.average()
            var sxy = 0.0; var sxx = 0.0
            for (i in beats.indices) { sxy += (n[i] - mn) * (beats[i] - mt); sxx += (n[i] - mn) * (n[i] - mn) }
            val period = if (sxx > 0) sxy / sxx else median
            val bpm = 60.0 / (if (period > 0) period else median)
            val list = buildList {
                add(Candidate(bpm, "pulse"))
                if (bpm / 2 >= MIN_BPM / 2) add(Candidate(bpm / 2, "half"))
                if (bpm * 2 <= MAX_BPM * 1.25) add(Candidate(bpm * 2, "double"))
            }
            return Tempo(list, regularity, false, "beats")
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

    /** The continuous pitch of [x] (fractional MIDI, NaN where nothing is pitched), one value every [HOP_SECONDS]: Performance check's input. */
    fun curve(x: FloatArray, rate: Int, minHz: Double, maxHz: Double): PitchCurve {
        val win = (2.2 * rate / minHz).toInt()
        val hop = (HOP_SECONDS * rate).toInt()
        val centre = win / 2.0 / rate
        if (x.size < win) return PitchCurve(HOP_SECONDS, centre, DoubleArray(0))
        val frames = (x.size - win) / hop + 1
        val buf = FloatArray(win)
        val out = DoubleArray(frames) { Double.NaN }
        for (f in 0 until frames) {
            System.arraycopy(x, f * hop, buf, 0, win)
            Yin.estimate(buf, rate, minHz, maxHz)?.let { e -> out[f] = 69 + 12 * log2(e.hz / 440.0) }
        }
        return PitchCurve(HOP_SECONDS, centre, out)
    }

    /** A single line's notes, the fraction of time something was pitched, and how much of that time held a pitch ([held]). */
    data class Line(val notes: List<Note>, val voiced: Double, val held: Double)

    /**
     * The share of pitched time spent holding a pitch: runs of at least 0.3 s within ¾ of a semitone of their
     * median, after smoothing over 0.18 s (one cycle of a 5.5 Hz vibrato, so a wide vibrato still counts as held).
     * Speech slides between syllables and holds little; singing holds notes. Measured 27 Oct 2026 (settings chosen
     * from a grid of smoothing, tolerance and run length): Windows TTS speech, seven files in two voices, 0.00–0.27;
     * the Spleeter vocal stems of five YuE2 songs 0.44 (metal)–0.76; a rap vocal 0.38; a synthetic sung line with
     * vibrato from none to ±120 cents 0.96–0.99.
     */
    fun held(pitch: DoubleArray): Double {
        val voiced = pitch.count { !it.isNaN() }
        if (voiced == 0) return 0.0
        val smooth = smoothed(pitch)
        var held = 0
        var i = 0
        while (i < smooth.size) {
            if (smooth[i].isNaN()) { i++; continue }
            val run = ArrayList<Double>()
            var j = i
            while (j < smooth.size && !smooth[j].isNaN() && (run.isEmpty() || abs(smooth[j] - run.sorted()[run.size / 2]) <= 0.75)) { run += smooth[j]; j++ }
            if (run.size >= 15) held += run.size
            i = maxOf(j, i + 1)
        }
        return held.toDouble() / voiced
    }

    /** [pitch] averaged over ±4 frames (0.18 s, one cycle of a 5.5 Hz vibrato), pitched frames only; NaN stays NaN. */
    private fun smoothed(pitch: DoubleArray): DoubleArray = DoubleArray(pitch.size) { f ->
        if (pitch[f].isNaN()) Double.NaN else {
            var sum = 0.0; var n = 0
            for (k in maxOf(0, f - 4)..minOf(pitch.size - 1, f + 4)) if (!pitch[k].isNaN()) { sum += pitch[k]; n++ }
            sum / n
        }
    }

    fun track(x: FloatArray, rate: Int, minHz: Double, maxHz: Double, onsets: List<Double>): Line {
        val win = (2.2 * rate / minHz).toInt()
        val hop = (HOP_SECONDS * rate).toInt()
        if (x.size < win) return Line(emptyList(), 0.0, 0.0)
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
        val runSmooth = ArrayList<Double>()
        var splitByPitch = false
        fun close(endFrame: Int, gap: Boolean = false) {
            val seconds = (endFrame - start) * HOP_SECONDS
            if (start >= 0 && gap && splitByPitch && seconds < 0.1 && notes.isNotEmpty() && abs(notes.last().end - (time(start) - HOP_SECONDS / 2)) < HOP_SECONDS / 2) {
                // A piece split off by pitch just before a silence: the pitch window straddling the next note, or the
                // note's own release. It belongs to the note it came from.
                notes[notes.size - 1] = notes.last().copy(end = time(endFrame - 1) + HOP_SECONDS / 2)
            } else if (start >= 0 && run.isNotEmpty() && seconds >= MIN_NOTE) {
                val m = run.sorted()[run.size / 2]
                val midi = m.roundToInt()
                val c = (start until endFrame).map { conf[it] }.average()
                notes += Note(time(start) - HOP_SECONDS / 2, time(endFrame - 1) + HOP_SECONDS / 2, midi, ((m - midi) * 100).roundToInt(), c)
            }
            start = -1; run.clear(); runSmooth.clear(); splitByPitch = false
        }
        // A new pitch needs the smoothed pitch to leave the note too: vibrato swings the raw pitch only, a real change
        // moves both at once. A note holds its first 0.12 s (the smoothing still carries the note before). [Claude] A trill
        // as fast as a vibrato (about 6 a second) stays one note unless its notes have attacks.
        val smooth = smoothed(pitch)
        // The note's smoothed centre, from after its first four frames (those still carry the note before).
        fun settled(s: List<Double>) = s.subList(4, s.size).sorted()[(s.size - 4) / 2]
        for (f in 0 until frames) {
            val p = pitch[f]
            if (p.isNaN()) { close(f, gap = true); continue }
            if (start >= 0) {
                val m = run.sorted()[run.size / 2]
                // An onset splits a held pitch (a repeated note), unless it is this note's own attack found a little late.
                val byPitch = run.size >= 6 && abs(p - m) > 0.6 && abs(smooth[f] - settled(runSmooth)) > 0.6
                val byOnset = f in onsetFrames && (f - start) * HOP_SECONDS > 0.08
                if (byPitch || byOnset) { close(f); splitByPitch = !byOnset }
            }
            if (start < 0) start = f
            run += p; runSmooth += smooth[f]
        }
        close(frames, gap = true)
        val voiced = pitch.count { !it.isNaN() }.toDouble() / frames
        return Line(notes, voiced, held(pitch))
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

    /**
     * Chroma from the whole file (for chords and mixes): log magnitudes 55 Hz–2 kHz folded to pitch classes. The home
     * clues come from the bass: the pitch class most often lowest, and the last bass note (as a line's last note is).
     */
    fun fromChroma(x: FloatArray, rate: Int): Evidence {
        val n = 8192
        val hop = n
        val w = DoubleArray(12); val bass = DoubleArray(12)
        var lastBass = -1
        val win = DoubleArray(n) { 0.5 - 0.5 * cos(2 * PI * it / n) }
        val re = DoubleArray(n); val im = DoubleArray(n)
        fun pcAt(hz: Double) = Math.floorMod((69 + 12 * log2(hz / 440.0)).roundToInt(), 12)
        val pcOf = IntArray(n / 2) { b ->
            val hz = b * rate.toDouble() / n
            if (hz < 55 || hz > 2000) -1 else pcAt(hz)
        }
        val mags = DoubleArray(n / 2)
        var f = 0
        while (f + n <= x.size) {
            for (i in 0 until n) { re[i] = x[f + i] * win[i]; im[i] = 0.0 }
            Fft.transform(re, im)
            val frame = DoubleArray(12)
            for (b in 1 until n / 2) {
                mags[b] = sqrt(re[b] * re[b] + im[b] * im[b])
                val pc = pcOf[b]
                if (pc >= 0) frame[pc] += mags[b]
            }
            // The bass: the lowest strong peak (ChordFinder.bassHz), not the loudest bin.
            val bassHz = ChordFinder.bassHz(mags, rate.toDouble() / n)
            // Each frame counts by its shape, not its loudness.
            val total = frame.sum()
            if (total > 1e-6) {
                for (i in 0 until 12) w[i] += frame[i] / total
                if (bassHz != null) { val pc = pcAt(bassHz); bass[pc] += 1.0; lastBass = pc }
            }
            f += hop
        }
        val h = DoubleArray(12)
        val clues = HashMap<Int, MutableList<String>>()
        fun clue(pc: Int, amount: Double, words: String) { h[pc] += amount; clues.getOrPut(pc) { ArrayList() } += words }
        if (lastBass >= 0) clue(lastBass, 0.5, "the last bass note")
        bass.indices.maxByOrNull { bass[it] }?.takeIf { bass[it] > 0 }?.let { clue(it, 0.3, "most often in the bass") }
        return Evidence(w, h, clues, "chroma")
    }

    /** The bass search: 40–300 Hz (E1 up to about D4), the lowest local peak at least a quarter of the band's loudest. [Claude] */
    const val BASS_LOW_HZ = 40.0
    const val BASS_HIGH_HZ = 300.0
    const val BASS_PEAK = 0.25

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
        // Noise spreads over every note; a strong bass note can still lift one mode past 0.5. Real piano chords and lines
        // keep 0.795–0.838 of their weight in the top key's scale, noise and hum 0.59–0.72 (measured 26 Oct 2026).
        val firstPcs = Scales.byId(first.second).semitones.map { (first.first + it) % 12 }.toSet()
        val inScale = firstPcs.sumOf { e.weights[it] } / total
        return KeyResult(top, tie || first.third < 0.5 || inScale < MIN_IN_SCALE, e.from)
    }

    /** The share of the weight that must sit in the top key's scale before that key is named. */
    const val MIN_IN_SCALE = 0.75

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
        line("tempo", r.tempo.free, r.tempo.clarity, r.tempo.from)
        r.tempo.candidates.forEach { line("bpm", it.bpm, it.why) }
        line("voiced", r.voiced)
        line("key", r.key.uncertain, r.key.from)
        r.key.candidates.forEach { line("mode", it.key.token, it.score, it.reason) }
        r.notes.forEach { line("note", it.start, it.end, it.midi, it.cents, it.confidence) }
        line("chords", r.chords.meter, r.chords.free)
        r.chords.beats.forEach { line("chord", it.time, it.label, it.match) }
        r.oneAtATime?.let { line("line", it) }
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
            tempo = Tempo(lines.filter { it[0] == "bpm" }.map { Tempo.Candidate(it[1].toDouble(), it[2]) }, tempoLine[2].toDouble(), tempoLine[1].toBoolean(), tempoLine.getOrNull(3) ?: "onsets"),
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
            oneAtATime = lines.firstOrNull { it[0] == "line" }?.get(1)?.toBooleanStrictOrNull(),
        )
    }.getOrNull()
}
