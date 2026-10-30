package com.betterappmafia.apophenia.core.analysis

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

class HarmonicChromaTest {
    private val rate = 24000
    private val size = 4096
    private fun read(name: String) = javaClass.getResource("/chordtracking/$name")!!.readText().lineSequence().filter { it.isNotBlank() }.map { it.toDouble() }.toList().toDoubleArray()

    @Test fun `a zero spectrum produces no pitch class evidence`() {
        val dictionary = HarmonicChroma.dictionary(rate, size)
        assertArrayEquals(DoubleArray(12), dictionary.chroma(DoubleArray(dictionary.bins)), 0.0)
    }

    @Test fun `harmonic fit agrees with independent scipy NNLS on a recorded guitar spectrum`() {
        val dictionary = HarmonicChroma.dictionary(rate, size)
        val expected = read("harmonic-expected.tsv")
        val actual = dictionary.chroma(read("harmonic-spectrum.tsv"))
        val sum = actual.sum()
        assertTrue("the fit contains actual evidence", sum > 0.0)
        val normalised = DoubleArray(12) { actual[it] / sum }
        val reference = DoubleArray(12) { expected[it] / expected.sum() }
        // Coordinate-descent/Scipy error measured below 2e-6 of the largest coefficient;
        // this fixture independently guards the dictionary, scaling and solver together.
        assertArrayEquals(reference, normalised, 1e-5)
    }

    @Test fun `upper partials of a C do not become separate chord tones`() {
        val hz = 440.0 * 2.0.pow((48 - 69) / 12.0)
        val x = FloatArray(size * 8) { i -> (1..12).sumOf { h -> 0.2 * 0.6.pow(h - 1) * cos(2 * PI * h * hz * i / rate) }.toFloat() }
        val fit = ChordFinder.chroma(x, rate, listOf(0.0, x.size.toDouble() / rate), harmonic = true).chroma.single()
        println("Pure C fitted fractions: " + fit.map { it / fit.sum() })
        assertTrue("fundamental remains", fit[0] > 0.0)
        assertTrue("overtones are explained by C", fit.drop(1).sum() < fit[0] * 1e-4)
    }
}
