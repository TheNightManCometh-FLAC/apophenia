package com.betterappmafia.apophenia.core.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class SpeechTest {
    private val rate = Speech.RATE

    /** [seconds] of a 200 Hz tone at [amp], or silence. */
    private fun tone(seconds: Double, amp: Double = 0.3) = FloatArray((seconds * rate).toInt()) { (amp * sin(2 * PI * 200 * it / rate)).toFloat() }

    @Test fun silence_has_no_pieces() {
        assertTrue(Speech.pieces(FloatArray(rate * 5)).isEmpty())
        assertTrue(Speech.pieces(FloatArray(0)).isEmpty())
    }

    @Test fun a_short_memo_is_one_piece_trimmed_to_the_sound() {
        val x = tone(1.0, 0.0) + tone(3.0) + tone(1.0, 0.0)
        val p = Speech.pieces(x).single()
        // Within a frame (480 samples) of margin of the sound, which runs from 1 s to 4 s.
        assertTrue("starts at ${p.start}", p.start in rate - 960..rate)
        assertTrue("ends at ${p.end}", p.end in 4 * rate..4 * rate + 960)
    }

    @Test fun a_long_memo_is_cut_in_the_pause_and_no_piece_is_over_twenty_seconds() {
        // 14 s of talk, a half-second pause, 14 s more: the cut falls in the pause.
        val x = tone(14.0) + tone(0.5, 0.0) + tone(14.0)
        val ps = Speech.pieces(x)
        assertEquals(2, ps.size)
        assertTrue("first ends at ${ps[0].end / rate.toDouble()} s", ps[0].end in (14.0 * rate).toInt()..(14.5 * rate).toInt())
        assertTrue("second starts at ${ps[1].start / rate.toDouble()} s", ps[1].start in (14.0 * rate).toInt()..(14.6 * rate).toInt())
        // With no pause at all, it still cuts by 20 s.
        val steady = Speech.pieces(tone(50.0))
        assertTrue(steady.size >= 3)
        assertTrue(steady.all { (it.end - it.start) <= Speech.MAX_PIECE * rate + 960 })
        assertEquals(0, steady.first().start)
        assertEquals(50 * rate, steady.last().end)
    }

    @Test fun the_token_limit_follows_the_length_and_the_decoders_positions() {
        assertEquals(13, Speech.maxTokens(2 * rate))         // 2 s × 6.5
        assertEquals(8, Speech.maxTokens(100))
        assertEquals(193, Speech.maxTokens(60 * rate))       // never past 194 positions
    }

    @Test fun the_tokenizer_turns_pieces_into_text_with_spaces_and_bytes() {
        val json = """{"model": {"type": "BPE", "vocab": {"<unk>": 0, "<s>": 1, "</s>": 2, "<0xC3>": 3, "<0xA9>": 4, "▁the": 5, "▁ca": 6, "f": 7, "▁say \"hi\"": 8, "ét": 9}, "merges": []}}"""
        val t = SpeechTokenizer.fromJson(json)
        assertEquals("the café", t.decode(listOf(1, 5, 6, 7, 3, 4, 2)))
        assertEquals("say \"hi\"ét", t.decode(listOf(8, 9)))
        // Ids past the vocabulary (the added <<ST_n>> tokens) are left out.
        assertEquals("the", t.decode(listOf(5, 32000)))
    }

    @Test fun the_bundled_tokenizer_reads_its_whole_vocabulary() {
        val t = SpeechTokenizer.fromJson(File("../app/src/main/assets/models/moonshine-base/tokenizer.json").readText())
        assertEquals("", t.decode(listOf(0, 1, 2)))
    }

    @Test fun a_transcript_round_trips_and_says_whether_it_was_edited() {
        val lines = listOf(Speech.Line(0.0, 2.5, "okay this is the verse"), Speech.Line(13.25, 16.0, "try it\tboth ways"))
        val s = TranscriptText.encode(Speech.MODEL_ID, lines, edited = true)
        assertEquals(listOf(Speech.Line(0.0, 2.5, "okay this is the verse"), Speech.Line(13.25, 16.0, "try it both ways")), TranscriptText.decode(s))
        assertTrue(TranscriptText.edited(s))
        assertEquals(Speech.MODEL_ID, TranscriptText.model(s))
        assertNull(TranscriptText.decode("notes\tsomething"))
    }

    companion object {
        /** A 16-bit mono WAV fixture as floats. */
        fun readWav(f: File): Pair<FloatArray, Int> {
            val b = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            var p = 12; var rate = 0
            while (p + 8 <= b.limit()) {
                val id = String(ByteArray(4) { b.get(p + it) }); val size = b.getInt(p + 4)
                if (id == "fmt ") rate = b.getInt(p + 12)
                if (id == "data") return FloatArray(size / 2) { b.getShort(p + 8 + 2 * it) / 32768f } to rate
                p += 8 + size + (size and 1)
            }
            error("no data in $f")
        }
    }
}
