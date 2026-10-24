package com.betterappmafia.apophenia.core.tools

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** What the isolator does with what's panned around [IsolatorSettings.panCenter]. */
enum class PanMode { OFF, KEEP, REMOVE }

/**
 * The isolator's controls (plan §7: adjustable, dialled in by you): stereo position, tone ⇄ hit
 * (harmonic/percussive balance), band. The engine applies them on an STFT (engine `isolator.h`).
 */
data class IsolatorSettings(
    val panMode: PanMode = PanMode.OFF,
    /** -1 hard left .. +1 hard right. */
    val panCenter: Double = 0.0,
    /** Half-width of the zone, 0.05..1 of the stereo field. */
    val panWidth: Double = 0.25,
    /** -1 tone only (sustained, pitched) .. 0 both .. +1 hit only (attacks, drums). */
    val toneHit: Double = 0.0,
    /** Hz, 0 = off. */
    val lowCut: Int = 0,
    val highCut: Int = 0,
) {
    init {
        require(panCenter in -1.0..1.0 && panWidth in 0.05..1.0 && toneHit in -1.0..1.0) { "isolator setting out of range" }
        require(lowCut >= 0 && highCut >= 0 && (highCut == 0 || lowCut < highCut)) { "low cut must be under the high cut" }
    }

    val isNeutral: Boolean get() = panMode == PanMode.OFF && toneHit == 0.0 && lowCut == 0 && highCut == 0

    fun encode(): String = listOf("1", panMode.name, panCenter, panWidth, toneHit, lowCut, highCut).joinToString("|")

    companion object {
        fun decode(s: String?): IsolatorSettings? = runCatching {
            val f = s!!.split('|')
            require(f.size == 7 && f[0] == "1")
            IsolatorSettings(PanMode.valueOf(f[1]), f[2].toDouble(), f[3].toDouble(), f[4].toDouble(), f[5].toInt(), f[6].toInt())
        }.getOrNull()

        /** Starting points (plan §7: presets), each then dialled in by ear. [Claude] values. */
        val PRESETS: List<Pair<String, IsolatorSettings>> = listOf(
            "Bass" to IsolatorSettings(panMode = PanMode.KEEP, panWidth = 0.35, toneHit = -0.6, highCut = 350),
            "Lead vocal (centre)" to IsolatorSettings(panMode = PanMode.KEEP, panWidth = 0.15, toneHit = -0.4, lowCut = 150, highCut = 7000),
            "Remove the centre" to IsolatorSettings(panMode = PanMode.REMOVE, panWidth = 0.15),
            "Left side" to IsolatorSettings(panMode = PanMode.KEEP, panCenter = -0.6, panWidth = 0.4),
            "Right side" to IsolatorSettings(panMode = PanMode.KEEP, panCenter = 0.6, panWidth = 0.4),
            "Drums" to IsolatorSettings(toneHit = 0.8),
            "Everything but drums" to IsolatorSettings(toneHit = -1.0),
            "Kick" to IsolatorSettings(toneHit = 0.8, highCut = 150),
        )
    }
}

/** In-place radix-2 FFT on separate real and imaginary arrays (length a power of two). */
object Fft {
    fun transform(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        require(n == im.size && n > 0 && n and (n - 1) == 0) { "FFT size must be a power of two" }
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang); val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val a = i + k; val b = a + len / 2
                    val vr = re[b] * cr - im[b] * ci; val vi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - vr; im[b] = im[a] - vi
                    re[a] += vr; im[a] += vi
                    val nr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** Magnitudes of bins 0..n/2 of a Hann-windowed real signal. */
    fun magnitudes(x: FloatArray): DoubleArray {
        val n = x.size
        val re = DoubleArray(n) { x[it] * (0.5 - 0.5 * cos(2 * PI * it / n)) }
        val im = DoubleArray(n)
        transform(re, im)
        return DoubleArray(n / 2 + 1) { hypot(re[it], im[it]) }
    }
}

/**
 * The note finder (plan §7: spectrum with piano and fretboard showing the strongest notes). Each note's
 * strength is a weighted sum of the spectrum at its first five harmonics (counted only when its own
 * fundamental is at least a tenth of the loudest peak), so a bass note with a weak
 * fundamental still reads as itself and not an octave up. It shows what's there; it doesn't decide
 * what the music is.
 */
object NoteFinder {
    data class Note(val midi: Int, val strength: Double)

    private val WEIGHTS = doubleArrayOf(1.0, 0.8, 0.6, 0.5, 0.4)

    /** Every MIDI note in [lowMidi]..[highMidi] with its strength 0..1 (the strongest is 1). */
    fun strengths(x: FloatArray, rate: Int, lowMidi: Int = 28, highMidi: Int = 96): List<Note> {
        val mags = Fft.magnitudes(x)
        val binHz = rate.toDouble() / x.size
        val loudest = mags.maxOrNull()?.takeIf { it > 1e-9 } ?: return (lowMidi..highMidi).map { Note(it, 0.0) }
        fun peak(hz: Double): Double {
            if (hz >= rate / 2.0) return 0.0
            val halfBand = maxOf(binHz, hz * (2.0.pow(30.0 / 1200) - 1))   // ±30 cents, at least a bin
            val lo = maxOf(1, ((hz - halfBand) / binHz).roundToInt())
            val hi = minOf(mags.size - 1, ((hz + halfBand) / binHz).roundToInt())
            var m = 0.0
            for (k in lo..hi) if (mags[k] > m) m = mags[k]
            return m
        }
        val raw = (lowMidi..highMidi).map { m ->
            val f = 440.0 * 2.0.pow((m - 69) / 12.0)
            var s = 0.0
            for (h in WEIGHTS.indices) s += WEIGHTS[h] * peak(f * (h + 1))
            // Only a note whose own fundamental is there counts (else every subharmonic of a chord would light up).
            s *= (peak(f) / (0.1 * loudest)).coerceIn(0.0, 1.0)
            Note(m, s)
        }
        // A note's own upper octave also collects its harmonics 2 and 4: take back what's explained by the octave below.
        val byMidi = raw.associate { it.midi to it.strength }
        val adjusted = raw.map { n -> Note(n.midi, maxOf(0.0, n.strength - 0.6 * (byMidi[n.midi - 12] ?: 0.0))) }
        val max = adjusted.maxOf { it.strength }
        return if (max <= 0) adjusted.map { Note(it.midi, 0.0) } else adjusted.map { Note(it.midi, it.strength / max) }
    }

    /** The strongest notes, strongest first: local peaks at least [floor] of the strongest, up to [count]. */
    fun strongest(notes: List<Note>, count: Int = 6, floor: Double = 0.3): List<Note> =
        notes.filterIndexed { i, n ->
            n.strength >= floor && (i == 0 || notes[i - 1].strength <= n.strength) && (i == notes.size - 1 || notes[i + 1].strength < n.strength)
        }.sortedByDescending { it.strength }.take(count)

    /** Cents between [hz] and MIDI note [midi]. */
    fun cents(hz: Double, midi: Int) = 1200 * ln(hz / (440.0 * 2.0.pow((midi - 69) / 12.0))) / ln(2.0)
}
