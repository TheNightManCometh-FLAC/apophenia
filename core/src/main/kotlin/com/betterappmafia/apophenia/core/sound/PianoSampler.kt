package com.betterappmafia.apophenia.core.sound

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/** One note to hear: seconds from the start, MIDI number, MIDI velocity 1–127. */
data class PlayNote(val start: Double, val end: Double, val midi: Int, val velocity: Int)

/**
 * The piano (plan: University of Iowa MIS Steinway, one dynamic now, built to add a second later). Each
 * sample is a recorded note at its MIDI number ([roots]); a note plays the nearest sample, resampled by cubic
 * Hermite to its pitch. Velocity sets the level and, below mf, a gentle low-pass, the way a softer key sounds
 * duller; with a second dynamic layer the softer notes would pick their own recording instead ([layers]).
 * Release is the damper: about a tenth of a second, except the top 18 keys, which have no dampers on a grand.
 * A key struck again cuts its own previous note short (the same string). Deterministic, so renders can be cached.
 */
class PianoSampler(private val layers: List<Layer>, private val sampleRate: Int = 44_100) {
    /** One recorded dynamic: [velocity] is the velocity it was played at; [samples] are its notes by MIDI number. */
    class Layer(val velocity: Int, val samples: Map<Int, FloatArray>) {
        val roots: IntArray = samples.keys.sorted().toIntArray()
    }

    init { require(layers.isNotEmpty() && layers.all { it.samples.isNotEmpty() }) { "a piano needs samples" } }

    fun render(notes: List<PlayNote>, outRate: Int, seconds: Double): FloatArray {
        val out = FloatArray((seconds * outRate).toInt() + outRate * 2)
        val sorted = notes.sortedBy { it.start }
        // The next strike of the same key, which damps this one.
        val nextStrike = DoubleArray(sorted.size) { Double.MAX_VALUE }
        val last = HashMap<Int, Int>()
        for ((i, n) in sorted.withIndex()) { last[n.midi]?.let { nextStrike[it] = n.start }; last[n.midi] = i }
        for ((i, n) in sorted.withIndex()) add(out, n, minOf(n.end, nextStrike[i]), nextStrike[i], outRate)
        var peak = 0f
        for (v in out) peak = maxOf(peak, abs(v))
        if (peak > 0.95f) for (i in out.indices) out[i] *= 0.95f / peak
        var end = out.size
        while (end > (seconds * outRate).toInt() && abs(out[end - 1]) < 1e-4f) end--
        return out.copyOf(end)
    }

    private fun add(out: FloatArray, n: PlayNote, release: Double, cut: Double, outRate: Int) {
        val layer = layers.minByOrNull { abs(it.velocity - n.velocity) }!!
        val root = layer.roots.minByOrNull { abs(it - n.midi) }!!
        val src = layer.samples.getValue(root)
        val step = 2.0.pow((n.midi - root) / 12.0) * sampleRate / outRate
        val v = n.velocity.coerceIn(1, 127)
        val gain = gainFor(v, layer.velocity)
        // Softer than the recording: a one-pole low-pass, from off at the recorded velocity down to about 1.2 kHz at velocity 1.
        val soft = ((layer.velocity - v).coerceAtLeast(0)) / layer.velocity.toDouble()
        val cutoff = 18_000.0 * (1.0 - soft) + 1_200.0 * soft
        val a = if (soft <= 0.0) 1.0 else 1.0 - exp(-2.0 * Math.PI * cutoff / outRate)
        val damped = n.midi < 89
        val releaseSec = if (damped) 0.12 else 1.2
        val from = (n.start * outRate).toLong()
        val held = ((release - n.start).coerceAtLeast(0.01) * outRate).toLong()
        // A restruck key cuts this note within 30 ms of the new strike, damper or not.
        val cutAt = if (cut < Double.MAX_VALUE) ((cut - n.start) * outRate).toLong() else Long.MAX_VALUE
        var lp = 0.0
        var pos = 0.0
        var k = 0L
        while (true) {
            val i = (from + k).toInt()
            if (i >= out.size || pos >= src.size - 2) break
            var env = 1.0
            if (k > held) env = exp(-(k - held) / (releaseSec / 4.6 * outRate))
            if (k > cutAt) env *= exp(-(k - cutAt) / (0.03 / 4.6 * outRate))
            if (env < 1e-4) break
            val x = hermite(src, pos)
            lp += a * (x - lp)
            if (i >= 0) out[i] += (lp * gain * env).toFloat()
            pos += step
            k++
        }
    }

    private fun hermite(s: FloatArray, pos: Double): Double {
        val i = pos.toInt()
        val t = pos - i
        val y0 = if (i > 0) s[i - 1].toDouble() else s[i].toDouble()
        val y1 = s[i].toDouble()
        val y2 = s[i + 1].toDouble()
        val y3 = if (i + 2 < s.size) s[i + 2].toDouble() else y2
        val c1 = 0.5 * (y2 - y0)
        val c2 = y0 - 2.5 * y1 + 2.0 * y2 - 0.5 * y3
        val c3 = 0.5 * (y3 - y0) + 1.5 * (y1 - y2)
        return ((c3 * t + c2) * t + c1) * t + y1
    }

    companion object {
        /**
         * Level for velocity [v] against a recording made at [recorded]: amplitude follows (v/127)², the usual
         * MIDI velocity curve (pp about 15 dB under mf, ff about 6 dB over), scaled so a chord and its bass sum under full scale.
         */
        fun gainFor(v: Int, recorded: Int): Double = 0.35 * (v / 127.0).pow(2) / (recorded / 127.0).pow(2)
    }
}
