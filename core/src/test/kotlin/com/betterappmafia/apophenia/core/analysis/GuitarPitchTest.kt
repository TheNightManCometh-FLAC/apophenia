package com.betterappmafia.apophenia.core.analysis

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream
import kotlin.math.abs
import kotlin.math.sqrt

class GuitarPitchTest {
    private val assets = File("../app/src/main/assets/models/guitar-gaps")
    private val weights by lazy { GuitarPitch.melWeights(File(assets, "mel-filter.f32").readBytes()) }
    private val reference by lazy {
        val bytes = GZIPInputStream(javaClass.getResourceAsStream("/guitarpitch/reference.f32.gz")!!).use { it.readBytes() }
        val floats = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        fun read(n: Int) = FloatArray(n).also { floats.get(it) }
        val x = read(GuitarPitch.SAMPLES)
        val mel = read(GuitarPitch.FRAMES * GuitarPitch.MELS)
        val output = List(4) { read(GuitarPitch.FRAMES * GuitarPitch.PITCHES) }
        check(!floats.hasRemaining())
        Triple(x, mel, output)
    }
    private val expected by lazy {
        javaClass.getResource("/guitarpitch/notes.tsv")!!.readText().lineSequence().filter { it.isNotBlank() }.map { line ->
            val v = line.split('\t')
            BasicPitch.TimedNote(v[0].toDouble(), minOf(10.0, v[1].toDouble()), v[2].toInt(), v[3].toInt().coerceIn(1, 127) / 127.0, null)
        }.filter { it.start < 10.0 && it.end > it.start }.toList()
    }

    private fun sameNotes(want: List<BasicPitch.TimedNote>, actual: List<BasicPitch.TimedNote>, tolerance: Double) {
        assertTrue("reference includes polyphonic guitar notes", want.zipWithNext().any { (a, b) -> b.start < a.end && a.midi != b.midi })
        assertEquals("number of notes", want.size, actual.size)
        want.zip(actual).forEachIndexed { i, (a, b) ->
            assertEquals("pitch $i", a.midi, b.midi)
            assertEquals("onset $i", a.start, b.start, tolerance)
            assertEquals("offset $i", a.end, b.end, tolerance)
            assertEquals("velocity $i", a.amplitude, b.amplitude, 1e-12)
        }
    }

    @Test fun the_decoder_matches_the_authors_recorded_guitar_reference() {
        sameNotes(expected, GuitarPitch.notes(reference.third, 10.0), 1e-6)
        assertTrue("zero activations produce no notes", GuitarPitch.notes(List(4) { FloatArray(100 * 88) }, 1.0).isEmpty())
    }

    @Test fun the_frontend_and_packaged_model_match_the_python_path() {
        val mel = GuitarPitch.spect(reference.first, weights)
        val differences = mel.indices.map { abs(mel[it] - reference.second[it]).toDouble() }
        val rms = sqrt(differences.sumOf { it * it } / differences.size)
        println("Guitar mel max ${differences.max()} RMS $rms")
        // Measured max .00364, RMS .0000268 dB against torchlibrosa's float32 convolution STFT.
        assertTrue("mel maximum error", differences.max() < .005)
        assertTrue("mel RMS error", rms < .00005)
        GuitarModel(File(assets, "guitar-mel-compact.onnx").readBytes()).use { model ->
            val outputs = model.run(mel)
            val errors = outputs.indices.map { h -> outputs[h].indices.maxOf { abs(outputs[h][it] - reference.third[h][it]) } }
            println("Guitar output max differences $errors")
            // Measured maximum .00448 on frame activation; notes still agree within 1 ms, including velocity.
            assertTrue("model activations agree", errors.all { it < .006f })
            sameNotes(expected, GuitarPitch.notes(outputs, 10.0), .001)
            assertTrue("the actual model on silence produces no notes", GuitarPitch.notes(model.run(GuitarPitch.spect(FloatArray(GuitarPitch.SAMPLES), weights)), 10.0).isEmpty())
        }
    }

    @Test fun overlaps_keep_central_predictions_without_duplicate_or_discarded_edge_attacks() {
        assertEquals(1, GuitarPitch.windowCount(1))
        assertEquals(1, GuitarPitch.windowCount(160000))
        assertEquals(3, GuitarPitch.windowCount(160001))
        assertEquals(3, GuitarPitch.windowCount(320000))
        assertEquals(5, GuitarPitch.windowCount(320001))
        var window = 0
        val notes = GuitarPitch.transcribe(FloatArray(320000), weights, GuitarPitch.Net {
            val heads = List(4) { FloatArray(1001 * 88) }
            fun attack(at: Int) {
                val p = 40
                for ((d, v) in listOf(-2 to .1f, -1 to .2f, 0 to .9f, 1 to .2f, 2 to .1f)) heads[0][(at + d) * 88 + p] = v
                for (i in at until at + 5) heads[2][i * 88 + p] = .9f
                heads[3][at * 88 + p] = .6f
            }
            when (window++) {
                0 -> { attack(740); attack(850) }
                1 -> { attack(100); attack(260) }
                2 -> { attack(100); attack(300) }
            }
            heads
        })
        assertEquals("one attack per kept region", 3, notes.size)
        assertEquals(listOf(7.4, 7.6, 13.0), notes.map { kotlin.math.round(it.start * 100) / 100 })
        assertTrue(notes.all { it.midi == 61 && it.end > it.start })
    }

    @Test fun model_provenance_keeps_old_and_guitar_transcriptions_distinct() {
        assertEquals(TranscriptionModel.BASIC_PITCH, TranscriptionModel.fromId("basic-pitch-icassp2022"))
        assertNull(TranscriptionModel.fromId("future-model"))
        assertEquals("future-model", TranscriptionModel.name("future-model"))
        val body = TranscriptionText.encode(GuitarPitch.MODEL_ID, expected, edited = true)
        assertTrue(body.startsWith("transcription\t1\t${GuitarPitch.MODEL_ID}\n"))
        assertEquals(TranscriptionModel.GUITAR, TranscriptionModel.fromId(TranscriptionText.model(body)))
        assertTrue(TranscriptionText.edited(body))
        assertEquals(expected, TranscriptionText.decode(body))
    }
}
