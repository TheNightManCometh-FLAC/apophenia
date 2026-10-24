package com.betterappmafia.apophenia.core.analysis

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Correcting a transcription (plan §10.3): the edits the correction screen makes. Notes are always kept
 * at the times they were played; quantising is a separate setting ([Grid]) applied when they are shown,
 * played or saved as MIDI, so it can be changed or turned off without losing anything.
 */
object NoteEdits {
    const val MIN_LENGTH = 0.03
    const val LOWEST = 21
    const val HIGHEST = 108

    fun transpose(notes: List<BasicPitch.TimedNote>, i: Int, semitones: Int) = notes.mapIndexed { k, n ->
        if (k == i) n.copy(midi = (n.midi + semitones).coerceIn(LOWEST, HIGHEST), bends = null) else n
    }

    /** Moves note [i] by [seconds], never before the start. */
    fun move(notes: List<BasicPitch.TimedNote>, i: Int, seconds: Double) = notes.mapIndexed { k, n ->
        if (k != i) n else { val d = maxOf(seconds, -n.start); n.copy(start = n.start + d, end = n.end + d) }
    }

    /** Changes note [i]'s length by [seconds], never shorter than [MIN_LENGTH]. */
    fun resize(notes: List<BasicPitch.TimedNote>, i: Int, seconds: Double) = notes.mapIndexed { k, n ->
        if (k == i) n.copy(end = maxOf(n.start + MIN_LENGTH, n.end + seconds)) else n
    }

    fun delete(notes: List<BasicPitch.TimedNote>, i: Int) = notes.filterIndexed { k, _ -> k != i }

    /** A new note, kept in time order; returns the list and the new note's index. */
    fun add(notes: List<BasicPitch.TimedNote>, start: Double, length: Double, midi: Int): Pair<List<BasicPitch.TimedNote>, Int> {
        val n = BasicPitch.TimedNote(maxOf(0.0, start), maxOf(0.0, start) + maxOf(MIN_LENGTH, length), midi.coerceIn(LOWEST, HIGHEST), 0.7, null)
        val list = (notes + n).sortedWith(compareBy({ it.start }, { it.midi }))
        return list to list.indexOf(n)
    }
}

/**
 * A quantise grid: [division] steps a beat (1 quarter, 2 eighths, 3 eighth triplets, 4 sixteenths) at
 * [bpm], from beat one at [origin] seconds. [snap] moves a note's start to the nearest step and its end
 * to the nearest step after that, so no note shrinks to nothing.
 */
data class Grid(val bpm: Double, val division: Int, val origin: Double = 0.0) {
    val step: Double get() = 60.0 / bpm / division

    fun snapTime(t: Double): Double = origin + ((t - origin) / step).roundToInt() * step

    fun snap(n: BasicPitch.TimedNote): BasicPitch.TimedNote {
        val s = maxOf(0.0, snapTime(n.start))
        val e = maxOf(s + step, snapTime(n.end))
        return n.copy(start = s, end = e)
    }

    fun apply(notes: List<BasicPitch.TimedNote>) = notes.map(::snap)

    companion object {
        /** The divisions the QUANTISE menu offers, with their names. */
        val DIVISIONS = listOf(1 to "1/4", 2 to "1/8", 3 to "1/8 triplets", 4 to "1/16", 6 to "1/16 triplets")
    }
}

/**
 * Hearing a transcription: each note a soft tone (fundamental plus two partials, quick attack,
 * exponential release), mixed and kept under full scale. Deterministic, so it can be cached.
 */
object NoteSynth {
    fun render(notes: List<BasicPitch.TimedNote>, rate: Int, seconds: Double): FloatArray {
        val out = FloatArray((seconds * rate).toInt() + rate / 2)
        for (n in notes) {
            val hz = 440.0 * 2.0.pow((n.midi - 69) / 12.0)
            val from = (n.start * rate).toInt()
            val held = ((n.end - n.start) * rate).toInt()
            val len = held + (0.15 * rate).toInt()
            val g = 0.25 * (0.4 + 0.6 * n.amplitude.coerceIn(0.0, 1.0))
            for (i in 0 until len) {
                val k = from + i
                if (k !in out.indices) break
                val t = i.toDouble() / rate
                val env = minOf(1.0, i / (0.005 * rate)) * if (i < held) exp(-t * 1.5) else exp(-(held / rate.toDouble()) * 1.5) * exp(-(i - held) / (0.04 * rate))
                val v = sin(2 * PI * hz * t) + 0.3 * sin(4 * PI * hz * t) + 0.12 * sin(6 * PI * hz * t)
                out[k] += (g * env * v).toFloat()
            }
        }
        val peak = out.maxOfOrNull { abs(it) } ?: 0f
        if (peak > 0.9f) for (i in out.indices) out[i] *= 0.9f / peak
        return out
    }
}
