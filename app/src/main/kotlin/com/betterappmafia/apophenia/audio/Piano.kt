package com.betterappmafia.apophenia.audio

import android.content.Context
import com.betterappmafia.apophenia.core.analysis.BasicPitch
import com.betterappmafia.apophenia.core.sound.PianoSampler
import com.betterappmafia.apophenia.core.sound.PlayNote
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The piano's samples: the University of Iowa MIS Steinway B at mf (public domain), every minor third from
 * B0 to C8, trimmed to mono 16-bit WAV in `assets/piano/mf-<MIDI>.wav` by `tools/piano/PianoPrep.java`.
 * Loaded once, off the main thread, on first use (about 24 MB as floats).
 */
object Piano {
    /** The velocity the mf recordings stand for (MuseScore's mf). */
    const val RECORDED = 80

    @Volatile private var sampler: PianoSampler? = null

    fun sampler(context: Context): PianoSampler = sampler ?: synchronized(this) {
        sampler ?: load(context).also { sampler = it }
    }

    private fun load(context: Context): PianoSampler {
        val assets = context.assets
        val samples = HashMap<Int, FloatArray>()
        for (name in assets.list("piano").orEmpty()) {
            val midi = Regex("mf-(\\d+)\\.wav").matchEntire(name)?.groupValues?.get(1)?.toInt() ?: continue
            val bytes = assets.open("piano/$name").use { it.readBytes() }
            // Canonical 44-byte header, 16-bit mono PCM (written by PianoPrep).
            val pcm = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            samples[midi] = FloatArray(pcm.remaining()) { pcm.get(it) / 32768f }
        }
        return PianoSampler(listOf(PianoSampler.Layer(RECORDED, samples)))
    }

    fun render(context: Context, notes: List<PlayNote>, rate: Int, seconds: Double): FloatArray =
        sampler(context).render(notes, rate, seconds)

    /** A transcription's notes on the piano: amplitude 0–1 becomes velocity. */
    fun ofTranscription(notes: List<BasicPitch.TimedNote>): List<PlayNote> =
        notes.map { PlayNote(it.start, it.end, it.midi, (30 + 80 * it.amplitude.coerceIn(0.0, 1.0)).toInt()) }
}
