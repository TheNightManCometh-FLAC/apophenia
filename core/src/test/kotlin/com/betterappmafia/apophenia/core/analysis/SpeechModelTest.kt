package com.betterappmafia.apophenia.core.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The bundled Moonshine Base on the desktop ONNX Runtime, reading a sentence spoken by Windows' own speech
 * synthesizer (Microsoft David, 16 kHz; the fixture is `speech/memo.wav`). A synthetic voice is easy;
 * real memos over an instrument are the phone test.
 */
class SpeechModelTest {
    private val dir = File("../app/src/main/assets/models/moonshine-base")

    @Test fun moonshine_reads_a_spoken_sentence() {
        val (x, rate) = SpeechTest.readWav(File(javaClass.getResource("/speech/memo.wav")!!.toURI()))
        assertEquals(Speech.RATE, rate)
        val tokenizer = SpeechTokenizer.fromJson(File(dir, "tokenizer.json").readText())
        Moonshine(File(dir, "encoder_model.onnx").readBytes(), File(dir, "decoder_model_merged.onnx").readBytes()).use { m ->
            val started = System.nanoTime()
            val lines = m.transcribe(x, tokenizer)
            val ms = (System.nanoTime() - started) / 1_000_000
            println("Moonshine on ${x.size / rate.toDouble()} s: $ms ms → $lines")
            val text = lines.joinToString(" ") { it.text }.lowercase()
            // "Maybe the chorus should go up to the high note. Then the second verse drops back down."
            val words = listOf("maybe", "chorus", "should", "high", "note", "second", "verse", "drops", "back", "down")
            val missing = words.filter { it !in text }
            assertTrue("missing $missing in \"$text\"", missing.size <= 1)
            assertTrue(lines.first().start < 0.5)
        }
    }

    @Test fun silence_reads_as_nothing() {
        Moonshine(File(dir, "encoder_model.onnx").readBytes(), File(dir, "decoder_model_merged.onnx").readBytes()).use { m ->
            val tokenizer = SpeechTokenizer.fromJson(File(dir, "tokenizer.json").readText())
            assertTrue(m.transcribe(FloatArray(Speech.RATE * 3), tokenizer).isEmpty())
        }
    }
}
