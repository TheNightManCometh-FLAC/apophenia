package com.betterappmafia.apophenia.audio.lab

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Levels for the microphone-source comparison. All levels are dB relative to a full-scale
 * square wave (RMS 1.0), so a full-scale sine reads −3.01 dB. No Android types; tested on the JVM.
 */
object LevelAnalysis {

    val OCTAVE_CENTRES = doubleArrayOf(31.5, 63.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)

    fun rmsDb(x: FloatArray, from: Int = 0, to: Int = x.size): Double {
        var sq = 0.0
        for (i in from until to) sq += x[i].toDouble() * x[i]
        return toDb(sq / max(1, to - from))
    }

    fun peakDb(x: FloatArray): Double {
        var p = 0.0
        for (v in x) p = max(p, abs(v.toDouble()))
        return 20 * log10(max(p, 1e-12))
    }

    /** Quietest 100 ms window: the noise floor between notes or in a silent room. */
    fun floorDb(x: FloatArray, sampleRate: Int): Double {
        val win = sampleRate / 10
        if (x.size < win) return rmsDb(x)
        var best = Double.MAX_VALUE
        var i = 0
        while (i + win <= x.size) {
            best = minOf(best, rmsDb(x, i, i + win))
            i += win / 2
        }
        return best
    }

    /**
     * Octave-band levels (bands fc/√2..fc·√2) from a Welch average: 32768-point Hann frames (1.46 Hz bins at 48 kHz, so a
     * 41 Hz low E does not leak across the 44.5 Hz band edge), 50 %
     * overlap. Band level = mean-square of the signal inside the band, so the bands of white noise
     * add up to its broadband RMS.
     */
    fun octaveBandsDb(x: FloatArray, sampleRate: Int, n: Int = 32768): DoubleArray {
        val window = DoubleArray(n) { 0.5 - 0.5 * cos(2 * PI * it / n) }
        val windowPower = window.sumOf { it * it }
        val power = DoubleArray(n / 2 + 1)
        var frames = 0
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        var start = 0
        while (start + n <= x.size) {
            for (i in 0 until n) { re[i] = x[start + i] * window[i]; im[i] = 0.0 }
            fft(re, im)
            for (k in 0..n / 2) power[k] += re[k] * re[k] + im[k] * im[k]
            frames++
            start += n / 2
        }
        val result = DoubleArray(OCTAVE_CENTRES.size) { Double.NEGATIVE_INFINITY }
        if (frames == 0) return result
        val binHz = sampleRate.toDouble() / n
        for ((b, fc) in OCTAVE_CENTRES.withIndex()) {
            val lo = fc / sqrt(2.0)
            val hi = fc * sqrt(2.0)
            var sum = 0.0
            for (k in 1 until n / 2) {
                val f = k * binHz
                if (f >= lo && f < hi) sum += 2 * power[k]
            }
            result[b] = toDb(sum / frames / (n * windowPower))
        }
        return result
    }

    private fun toDb(meanSquare: Double) = 10 * log10(max(meanSquare, 1e-24))

    /** In-place iterative radix-2 FFT. [re].size must be a power of two. */
    fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        require(n == im.size && n > 0 && n and (n - 1) == 0) { "FFT size must be a power of two" }
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j or bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang)
            val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until len / 2) {
                    val ar = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val ai = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k + len / 2] = re[i + k] - ar
                    im[i + k + len / 2] = im[i + k] - ai
                    re[i + k] += ar
                    im[i + k] += ai
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
