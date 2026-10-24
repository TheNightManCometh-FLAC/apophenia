package com.betterappmafia.apophenia.core.tools

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.instrument.Tuning
import com.betterappmafia.apophenia.core.pitch.OctaveLabel
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.pitch.SpelledPitch
import com.betterappmafia.apophenia.core.spelling.Spelling
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * YIN pitch estimate (de Cheveigné & Kawahara 2002: difference function, cumulative mean normalised
 * difference, absolute threshold, parabolic interpolation), ported from reference/p0 PitchYin.
 *
 * Two passes so a phone can run it 20 times a second: the octave is decided on a box-filtered,
 * decimated copy (cheap, and the low-pass removes the upper harmonics that cause octave-up errors),
 * then the period is refined at the full rate in a narrow lag window, which is where the cents come from.
 */
object Yin {
    data class Estimate(val hz: Double, val confidence: Double)

    private const val THRESHOLD = 0.15
    private const val GIVE_UP = 0.3
    private const val SILENCE_RMS = 0.002

    /** Null for silence, noise, or a pitch outside [minHz, maxHz]. [x] needs at least 2 × rate / minHz frames. */
    fun estimate(x: FloatArray, rate: Int, minHz: Double, maxHz: Double): Estimate? {
        require(minHz > 0 && maxHz > minHz && maxHz < rate / 2.0) { "bad pitch range $minHz..$maxHz" }
        val n = x.size
        var mean = 0.0
        for (v in x) mean += v
        mean /= n
        var e = 0.0
        for (v in x) { val d = v - mean; e += d * d }
        if (sqrt(e / n) < SILENCE_RMS) return null

        // Pass 1: decimate so the highest wanted pitch sits well under the new Nyquist.
        val dec = maxOf(1, floor(rate / (maxHz * 8.0)).toInt())
        val m = n / dec
        val y = DoubleArray(m) { i -> var s = 0.0; for (k in 0 until dec) s += x[i * dec + k] - mean; s / dec }
        val r = rate.toDouble() / dec
        val minLag = maxOf(2, floor(r / maxHz).toInt())
        val maxLag = kotlin.math.ceil(r / minHz).toInt()
        if (maxLag * 2 > m) return null
        val span = m - maxLag
        val cmndf = DoubleArray(maxLag + 2)
        var cum = 0.0
        cmndf[0] = 1.0
        for (lag in 1..maxLag) {
            var d = 0.0
            for (i in 0 until span) { val t = y[i] - y[i + lag]; d += t * t }
            cum += d
            cmndf[lag] = if (cum == 0.0) 1.0 else d * lag / cum
        }
        var chosen = -1
        var lag = minLag
        while (lag <= maxLag) {
            if (cmndf[lag] < THRESHOLD) {
                while (lag + 1 <= maxLag && cmndf[lag + 1] < cmndf[lag]) lag++
                chosen = lag
                break
            }
            lag++
        }
        if (chosen < 0) {
            var best = minLag
            for (l in minLag..maxLag) if (cmndf[l] < cmndf[best]) best = l
            if (cmndf[best] > GIVE_UP) return null
            chosen = best
        }
        val confidence = (1.0 - cmndf[chosen]).coerceIn(0.0, 1.0)

        // Pass 2: the plain difference function at the full rate around the coarse period.
        val centre = chosen * dec
        val lo = maxOf(2, centre - dec - 2)
        val hi = minOf(n / 2 - 1, centre + dec + 2)
        if (hi <= lo + 2) return null
        val fullSpan = n - hi - 1
        val diff = DoubleArray(hi - lo + 1) { j ->
            val l = lo + j
            var d = 0.0
            for (i in 0 until fullSpan) { val t = x[i] - x[i + l]; d += t * t }
            d
        }
        var bj = 0
        for (j in diff.indices) if (diff[j] < diff[bj]) bj = j
        var period = (lo + bj).toDouble()
        if (bj in 1 until diff.size - 1) {
            val a = diff[bj - 1]; val b = diff[bj]; val c = diff[bj + 1]
            val curve = a - 2 * b + c
            if (abs(curve) > 1e-12) period += (0.5 * (a - c) / curve).coerceIn(-0.5, 0.5)
        }
        val hz = rate / period
        return if (hz in minHz * 0.97..maxHz * 1.03) Estimate(hz, confidence) else null
    }
}

/**
 * Temperaments relative to a tonic (plan §7). Each gives every degree's offset from equal temperament
 * in cents; the tonic itself sits at its equal-tempered pitch from the A4 reference.
 */
enum class Temperament(val label: String) {
    EQUAL("Equal"),
    /** 5-limit just intonation: 1 16/15 9/8 6/5 5/4 4/3 45/32 3/2 8/5 5/3 9/5 15/8. */
    JUST("Just"),
    /** Pure fifths: 1 256/243 9/8 32/27 81/64 4/3 729/512 3/2 128/81 27/16 16/9 243/128. */
    PYTHAGOREAN("Pythagorean"),
    /** Quarter-comma meantone: fifths of 1200·log2(5)/4 ≈ 696.58 ¢, from three flats to eight sharps of the tonic. */
    MEANTONE("¼-comma meantone");

    /** Offset in cents from equal temperament of the note [semitones] above the tonic. */
    fun offset(semitones: Int): Double = OFFSETS[ordinal][Math.floorMod(semitones, 12)]

    companion object {
        private fun cents(ratio: Double) = 1200.0 * log2(ratio)
        private fun fromRatios(r: List<Double>) = DoubleArray(12) { i -> cents(r[i]) - 100.0 * i }
        private val JUST_R = listOf(1.0, 16.0 / 15, 9.0 / 8, 6.0 / 5, 5.0 / 4, 4.0 / 3, 45.0 / 32, 3.0 / 2, 8.0 / 5, 5.0 / 3, 9.0 / 5, 15.0 / 8)
        private val PYTH_R = listOf(1.0, 256.0 / 243, 9.0 / 8, 32.0 / 27, 81.0 / 64, 4.0 / 3, 729.0 / 512, 3.0 / 2, 128.0 / 81, 27.0 / 16, 16.0 / 9, 243.0 / 128)
        private val MEANTONE_FIFTH = 1200.0 * log2(5.0) / 4.0
        private val MEANTONE_C = DoubleArray(12).also { a ->
            for (k in -3..8) {
                val pc = Math.floorMod(7 * k, 12)
                var c = (k * MEANTONE_FIFTH) % 1200.0
                if (c < 0) c += 1200.0
                var d = c - 100.0 * pc
                if (d > 600) d -= 1200.0
                if (d < -600) d += 1200.0
                a[pc] = d
            }
        }
        private val OFFSETS = arrayOf(DoubleArray(12), fromRatios(JUST_R), fromRatios(PYTH_R), MEANTONE_C)
    }
}

/** What the tuner shows: the nearest note, spelled, and how far off it is. */
data class TunerReading(val hz: Double, val pitch: SpelledPitch, val cents: Double, val targetHz: Double) {
    val midi: Int get() = pitch.midi
}

/** The tuner's setup: reference, temperament, tonic, key for spelling, and the search range. */
data class TunerSetup(
    val a4: Double = 440.0,
    val temperament: Temperament = Temperament.EQUAL,
    /** Tonic the temperament is built on; also spells the notes when no [key] is given. */
    val tonic: PitchName = PitchName.parse("C"),
    /** The key note names are spelled in (plan §7); null spells from [tonic] as a major key. */
    val key: KeyMode? = null,
    val range: TunerRange = TunerRange.ANY,
    val octaveLabel: OctaveLabel = OctaveLabel.MIDDLE_C_IS_C4,
) {
    init { require(a4 in 400.0..480.0) { "A4 must be 400..480 Hz, was $a4" } }

    private val speller by lazy { KeySpeller(key ?: KeyMode(tonic, "major")) }

    /** The nearest note to [hz] in this temperament, with the cents off it. */
    fun read(hz: Double): TunerReading {
        val et = 69 + 12 * ln(hz / a4) / ln(2.0)
        var best: TunerReading? = null
        for (m in et.roundToInt() - 1..et.roundToInt() + 1) {
            val target = targetHz(m)
            val c = 1200 * ln(hz / target) / ln(2.0)
            if (best == null || abs(c) < abs(best.cents)) best = TunerReading(hz, speller.spell(m), c, target)
        }
        return best!!
    }

    /** The frequency MIDI note [midi] is tuned to here. */
    fun targetHz(midi: Int): Double = a4 * 2.0.pow((midi - 69 + temperament.offset(midi - tonic.pitchClass) / 100.0) / 12.0)

    fun label(p: SpelledPitch): String = octaveLabel.label(p)
}

/**
 * A search range for the detector [Claude]: from a tuning it runs two semitones under the lowest open
 * string (drop tunings, slack strings) to two octaves over the highest. It only narrows the search
 * (fewer octave errors, less work); the tuner stays chromatic and never names strings (no string mode).
 */
data class TunerRange(val id: String, val label: String, val minHz: Double, val maxHz: Double) {
    companion object {
        val ANY = TunerRange("any", "Any instrument", 27.0, 2100.0)
        val VOICE = TunerRange("voice", "Voice", 70.0, 1200.0)

        fun of(t: Tuning): TunerRange {
            val lo = t.strings.minOf { it.midi } - 2
            val hi = t.strings.maxOf { it.midi } + 24
            fun hz(m: Int) = 440.0 * 2.0.pow((m - 69) / 12.0)
            return TunerRange(t.id, t.name, hz(lo) * 0.97, minOf(hz(hi) * 1.03, 2100.0))
        }
    }
}

/**
 * Spells a MIDI note in a key: notes of the key's scale take the scale's spelling; anything else takes
 * sharps in sharp keys and flats in flat keys (a key with neither, like C major, uses the default table).
 */
class KeySpeller(key: KeyMode) {
    private val byPc: Map<Int, PitchName>
    private val flats: Boolean

    init {
        val notes = runCatching { Spelling.spell(key.tonic, key.scale.degrees).notes }.getOrElse { emptyList() }
        byPc = notes.associateBy { it.pitchClass }
        flats = notes.any { it.alter < 0 } || (notes.none { it.alter > 0 } && key.tonic.alter < 0)
    }

    fun name(pc: Int): PitchName = byPc[Math.floorMod(pc, 12)] ?: run {
        val sharp = SHARPS[Math.floorMod(pc, 12)]
        if (flats) FLATS[Math.floorMod(pc, 12)] else if (byPc.values.any { it.alter > 0 }) sharp else DEFAULT[Math.floorMod(pc, 12)]
    }

    fun spell(midi: Int): SpelledPitch {
        val n = name(midi)
        val octave = Math.floorDiv(midi - n.letter.natural - n.alter, 12) - 1
        return n.at(octave)
    }

    private companion object {
        val SHARPS = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B").map(PitchName::parse)
        val FLATS = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B").map(PitchName::parse)
        val DEFAULT = listOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B").map(PitchName::parse)
    }
}

/**
 * The drone's notes (plan §7: any root, octave, fifth, third major/minor/off). The intervals over the
 * root are tuned pure [Claude] (3:2, 5:4, 6:5, 2:1): a drone is what you tune against, and beating
 * against an equal-tempered third is what makes one hard to hear. The root follows the tuner's setup.
 */
data class DroneSettings(
    val root: PitchName = PitchName.parse("D"),
    val octave: Int = 2,
    val addOctave: Boolean = false,
    val fifth: Boolean = true,
    val third: DroneThird = DroneThird.OFF,
) {
    val rootPitch: SpelledPitch get() = root.at(octave)

    fun frequencies(setup: TunerSetup): List<Double> {
        val f = setup.targetHz(rootPitch.midi)
        return buildList {
            add(f)
            if (fifth) add(f * 3 / 2)
            when (third) { DroneThird.MAJOR -> add(f * 5 / 4); DroneThird.MINOR -> add(f * 6 / 5); DroneThird.OFF -> {} }
            if (addOctave) add(f * 2)
        }
    }

    /** "D + 5TH", "D + 5TH + M3", for the tools list and the top-bar slot. */
    val short: String
        get() = buildString {
            append(KeyMode.displayName(root))
            if (fifth) append(" + 5TH")
            when (third) { DroneThird.MAJOR -> append(" + M3"); DroneThird.MINOR -> append(" + m3"); DroneThird.OFF -> {} }
            if (addOctave) append(" + 8VA")
        }
}

enum class DroneThird { OFF, MAJOR, MINOR }
