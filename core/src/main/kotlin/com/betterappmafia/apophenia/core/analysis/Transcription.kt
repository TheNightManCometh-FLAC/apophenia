package com.betterappmafia.apophenia.core.analysis

import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Transcribed notes as text for storage: `transcription<TAB>1<TAB>model`, then one `note` line each. */
object TranscriptionText {
    const val VERSION = 1

    /** [grid] is the quantise setting (null = off); [edited] marks that someone corrected the notes. Both are optional lines. */
    fun encode(model: String, notes: List<BasicPitch.TimedNote>, grid: Grid? = null, edited: Boolean = false): String = buildString {
        append("transcription\t$VERSION\t$model\n")
        if (grid != null) append("grid\t${grid.division}\t${grid.bpm}\t${grid.origin}\n")
        if (edited) append("edited\n")
        for (n in notes) append("note\t${n.start}\t${n.end}\t${n.midi}\t${n.amplitude}\t${n.bends?.joinToString(",") ?: "-"}\n")
    }

    fun decode(s: String?): List<BasicPitch.TimedNote>? = runCatching {
        val lines = s!!.lines().filter { it.isNotEmpty() }.map { it.split('\t') }
        require(lines[0][0] == "transcription" && lines[0][1] == VERSION.toString())
        lines.drop(1).filter { it[0] == "note" }.map {
            BasicPitch.TimedNote(it[1].toDouble(), it[2].toDouble(), it[3].toInt(), it[4].toDouble(), if (it[5] == "-") null else it[5].split(',').map(String::toInt))
        }
    }.getOrNull()

    fun grid(s: String?): Grid? = runCatching { s!!.lines().first { it.startsWith("grid\t") }.split('\t').let { Grid(it[2].toDouble(), it[1].toInt(), it[3].toDouble()) } }.getOrNull()

    fun edited(s: String?): Boolean = s?.lines()?.any { it == "edited" } == true

    fun model(s: String?): String? = s?.lineSequence()?.firstOrNull()?.split('\t')?.getOrNull(2)
}

/**
 * A Standard MIDI File (format 0, 480 ticks a quarter) of transcribed notes, made the way Basic Pitch's
 * reference makes its MIDI: velocity is 127 × the note's activation, and pitch bends (±2 semitones,
 * 4096 a semitone) go only on notes that overlap no other, since a channel's bend moves every note on it.
 */
object MidiFile {
    const val PPQ = 480

    fun write(notes: List<BasicPitch.TimedNote>, bpm: Double = 120.0, name: String = "Apophenia transcription", program: Int = 4): ByteArray {
        fun tick(sec: Double) = (sec * bpm / 60.0 * PPQ).roundToLong().coerceAtLeast(0)
        // (tick, order, bytes): at one tick, bends and offs go before ons.
        val events = ArrayList<Triple<Long, Int, ByteArray>>()
        val sorted = notes.sortedWith(compareBy({ it.start }, { it.end }, { it.midi }))
        val overlapping = BooleanArray(sorted.size)
        for (i in sorted.indices) for (j in i + 1 until sorted.size) {
            if (sorted[j].start >= sorted[i].end) break
            overlapping[i] = true; overlapping[j] = true
        }
        sorted.forEachIndexed { i, n ->
            val vel = (127 * n.amplitude).roundToInt().coerceIn(1, 127)
            val on = tick(n.start)
            val off = maxOf(on + 1, tick(n.end))
            events += Triple(on, 2, byteArrayOf(0x90.toByte(), n.midi.toByte(), vel.toByte()))
            events += Triple(off, 0, byteArrayOf(0x80.toByte(), n.midi.toByte(), 0))
            val bends = n.bends
            if (!overlapping[i] && !bends.isNullOrEmpty()) {
                bends.forEachIndexed { k, b ->
                    val t = if (bends.size == 1) n.start else n.start + (n.end - n.start) * k / (bends.size - 1)
                    val v = ((b * 4096.0 / 3).roundToInt()).coerceIn(-8192, 8191) + 8192
                    events += Triple(tick(t), 1, byteArrayOf(0xE0.toByte(), (v and 0x7F).toByte(), (v shr 7 and 0x7F).toByte()))
                }
                // Back to centre after the note, so the next one starts in tune.
                events += Triple(off, 1, byteArrayOf(0xE0.toByte(), 0, 0x40))
            }
        }
        events.sortWith(compareBy({ it.first }, { it.second }))

        val track = ByteArrayOutputStream()
        fun vlq(v: Long) {
            var buffer = v and 0x7F
            var x = v shr 7
            while (x > 0) { buffer = (buffer shl 8) or ((x and 0x7F) or 0x80); x = x shr 7 }
            while (true) { track.write((buffer and 0xFF).toInt()); if (buffer and 0x80 != 0L) buffer = buffer shr 8 else break }
        }
        val usPerQuarter = (60_000_000.0 / bpm).roundToInt()
        vlq(0); track.write(byteArrayOf(0xFF.toByte(), 0x51, 3, (usPerQuarter shr 16).toByte(), (usPerQuarter shr 8).toByte(), usPerQuarter.toByte()))
        val nameBytes = name.toByteArray(Charsets.UTF_8).take(127).toByteArray()
        vlq(0); track.write(byteArrayOf(0xFF.toByte(), 0x03, nameBytes.size.toByte())); track.write(nameBytes)
        vlq(0); track.write(byteArrayOf(0xC0.toByte(), program.toByte()))
        var last = 0L
        for ((t, _, bytes) in events) { vlq(t - last); track.write(bytes); last = t }
        vlq(0); track.write(byteArrayOf(0xFF.toByte(), 0x2F, 0))

        val out = ByteArrayOutputStream()
        fun int32(v: Int) = out.write(byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte()))
        fun int16(v: Int) = out.write(byteArrayOf((v shr 8).toByte(), v.toByte()))
        out.write("MThd".toByteArray()); int32(6); int16(0); int16(1); int16(PPQ)
        val body = track.toByteArray()
        out.write("MTrk".toByteArray()); int32(body.size); out.write(body)
        return out.toByteArray()
    }
}
