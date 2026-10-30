package com.betterappmafia.apophenia.core.analysis

import com.betterappmafia.apophenia.core.tools.Fft
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

/** Harmonic spectral fit for the first-pass chord reader. It explains upper partials before
 * folding the fitted fundamentals into pitch classes. These are matching features, never MIDI notes.
 * Measured against the old reader on GuitarSet; see docs/research/harmonic-analysis-2026-10-29.md.
 */
internal object HarmonicChroma {
    private const val LOW_MIDI = 28
    private const val HIGH_MIDI = 88
    private const val SHAPE = 0.6
    private const val ITERATIONS = 256
    @Volatile private var cached: Dictionary? = null

    fun dictionary(rate: Int, size: Int): Dictionary {
        cached?.takeIf { it.rate == rate && it.size == size }?.let { return it }
        return synchronized(this) {
            cached?.takeIf { it.rate == rate && it.size == size }
                ?: Dictionary(rate, size).also { cached = it }
        }
    }

    class Dictionary(val rate: Int, val size: Int) {
        val lowBin = maxOf(1, ceil(40.0 * size / rate).toInt())
        val highBin = minOf(size / 2 - 1, (2500.0 * size / rate).toInt())
        val bins = highBin - lowBin + 1
        private val count = HIGH_MIDI - LOW_MIDI + 1
        private val norms = DoubleArray(count)
        private val columns = Array(count) { DoubleArray(bins) }
        private val gram = Array(count) { DoubleArray(count) }

        init {
            val window = DoubleArray(size) { 0.5 - 0.5 * cos(2 * PI * it / size) }
            val real = DoubleArray(size); val imag = DoubleArray(size)
            for (j in 0 until count) {
                val hz = 440.0 * 2.0.pow((LOW_MIDI + j - 69) / 12.0)
                for (harmonic in 1..12) {
                    if (harmonic * hz > minOf(2500.0, (size / 2 - 1.0) * rate / size)) break
                    for (i in 0 until size) {
                        real[i] = cos(2 * PI * harmonic * hz * i / rate) * window[i]
                        imag[i] = 0.0
                    }
                    Fft.transform(real, imag)
                    val weight = SHAPE.pow(harmonic - 1)
                    for (b in lowBin..highBin) columns[j][b - lowBin] += weight * sqrt(real[b] * real[b] + imag[b] * imag[b])
                }
                norms[j] = sqrt(columns[j].sumOf { it * it })
                if (norms[j] > 0) for (b in 0 until bins) columns[j][b] /= norms[j]
            }
            for (j in 0 until count) for (k in 0..j) {
                var dot = 0.0
                for (b in 0 until bins) dot += columns[j][b] * columns[k][b]
                gram[j][k] = dot; gram[k][j] = dot
            }
        }

        /** Non-negative least squares by cyclic coordinate descent with a cached Gram matrix.
         * All scratch belongs to this call; the cached dictionary is immutable and safe for workers.
         */
        fun chroma(spectrum: DoubleArray): DoubleArray {
            require(spectrum.size == bins)
            val gradient = DoubleArray(count) { j ->
                var dot = 0.0
                for (b in 0 until bins) dot += columns[j][b] * spectrum[b]
                dot
            }
            val tolerance = maxOf(1e-12, (gradient.maxOrNull() ?: 0.0) * 1e-8)
            val fit = DoubleArray(count)
            for (iteration in 0 until ITERATIONS) {
                var largest = 0.0
                for (j in 0 until count) {
                    if (gram[j][j] <= 0.0) continue
                    val value = maxOf(0.0, fit[j] + gradient[j] / gram[j][j])
                    val delta = value - fit[j]
                    if (delta == 0.0) continue
                    fit[j] = value
                    for (k in 0 until count) gradient[k] -= gram[k][j] * delta
                    largest = maxOf(largest, kotlin.math.abs(delta))
                }
                if (largest < tolerance) break
            }
            return DoubleArray(12).also { c ->
                for (j in 0 until count) if (norms[j] > 0) c[(LOW_MIDI + j) % 12] += fit[j] / norms[j]
            }
        }
    }
}
