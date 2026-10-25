package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.rhythm.Rational
import com.betterappmafia.apophenia.core.rhythm.TICKS_PER_WHOLE
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import java.io.ByteArrayOutputStream

/**
 * A score as a Standard MIDI File (plan §11), format 1 at 960 ticks a quarter: a conductor track with the tempo
 * and time-signature changes, key signatures (sharps or flats, major or minor; a mode goes out as its signature) and rehearsal marks as markers, then one track per part (its name, a General MIDI
 * program, its notes). Bars are written in playing order, repeats and endings taken as the app plays them
 * (ScoreTime.order); tied notes are one long note. Percussion parts go on channel 10. [Claude] Velocity is the
 * note's own playback velocity, else 80; dynamics, articulations and chord symbols aren't written yet.
 */
object ScoreMidi {
    const val PPQ = 960

    private class Track {
        val events = ArrayList<Triple<Long, Int, ByteArray>>()   // tick, order at that tick (offs before ons), bytes
        fun add(t: Long, order: Int, vararg b: Int) { events += Triple(t, order, ByteArray(b.size) { b[it].toByte() }) }
        fun meta(t: Long, type: Int, data: ByteArray) { events += Triple(t, 1, byteArrayOf(0xFF.toByte(), type.toByte()) + vlq(data.size.toLong()) + data) }
        fun bytes(): ByteArray {
            val out = ByteArrayOutputStream()
            var last = 0L
            for ((t, _, b) in events.sortedWith(compareBy({ it.first }, { it.second }))) { out.write(vlq(t - last)); out.write(b); last = t }
            out.write(vlq(0)); out.write(byteArrayOf(0xFF.toByte(), 0x2F, 0))
            return out.toByteArray()
        }
    }

    private fun vlq(v: Long): ByteArray {
        val out = ArrayList<Byte>()
        var x = v
        out += (x and 0x7F).toByte()
        x = x shr 7
        while (x > 0) { out.add(0, ((x and 0x7F) or 0x80).toByte()); x = x shr 7 }
        return out.toByteArray()
    }

    private fun ticks(r: Rational): Long = Math.round((r * TICKS_PER_WHOLE).let { it.num.toDouble() / it.den })

    /** The General MIDI program for a part: bass 33 (finger), guitar 25 (steel string), else piano 0. */
    fun program(part: Part): Int = when {
        part.instrument?.tuning?.id?.startsWith("bass") == true -> 33
        part.instrument != null -> 25
        else -> 0
    }

    private fun timeSig(t: TimeSignature): ByteArray {
        val dd = Integer.numberOfTrailingZeros(t.denominator)
        // MIDI clocks per metronome click: a quarter is 24; compound meters click on the dotted quarter.
        val click = if (t.isCompound) 36 else 24 * 4 / t.denominator
        return byteArrayOf(t.numerator.toByte(), dd.toByte(), click.toByte(), 8)
    }

    fun write(score: Score): ByteArray {
        val conductor = Track()
        if (score.title.isNotBlank()) conductor.meta(0, 0x03, score.title.toByteArray(Charsets.UTF_8))
        val first = score.parts.firstOrNull()
        val order = first?.let { ScoreTime.order(it) } ?: emptyList()
        // Where each played bar starts, in ticks.
        val starts = order.runningFold(0L) { t, b -> t + ticks(score.meterAt(b).barLength) }
        var lastMeter: TimeSignature? = null
        var lastBpm: Double? = null
        var lastKey: com.betterappmafia.apophenia.core.catalog.KeyMode? = null
        order.forEachIndexed { i, b ->
            val t = starts[i]
            val meter = score.meterAt(b)
            if (meter != lastMeter) { conductor.meta(t, 0x58, timeSig(meter)); lastMeter = meter }
            val key = score.keyAt(b)
            if (i == 0 && key != null || i > 0 && key != lastKey) { key?.let { conductor.meta(t, 0x59, byteArrayOf(Notation.fifths(it).toByte(), if (it.scaleId == "aeolian") 1 else 0)) }; lastKey = key }
            val tempo = score.tempoAt(b)
            val quarterBpm = tempo.bpm * (tempo.beatUnit.length / Rational.of(1, 4)).let { it.num.toDouble() / it.den }
            if (quarterBpm != lastBpm) {
                val us = Math.round(60_000_000.0 / quarterBpm).toInt()
                conductor.meta(t, 0x51, byteArrayOf((us shr 16).toByte(), (us shr 8).toByte(), us.toByte()))
                lastBpm = quarterBpm
            }
            first?.bars?.getOrNull(b)?.rehearsal?.let { conductor.meta(t, 0x06, it.toByteArray(Charsets.UTF_8)) }
        }
        val tracks = mutableListOf(conductor)
        var melodic = 0
        score.parts.forEachIndexed { pi, part ->
            val track = Track()
            val drums = part.clef == Clef.PERCUSSION
            val ch = if (drums) 9 else (melodic++).let { if (it >= 9) it + 1 else it } % 16
            track.meta(0, 0x03, part.name.ifBlank { "Part ${pi + 1}" }.toByteArray(Charsets.UTF_8))
            if (!drums) track.add(0, 1, 0xC0 or ch, program(part))
            val partOrder = ScoreTime.order(part)
            val partStarts = partOrder.runningFold(0L) { t, b -> t + ticks(score.meterAt(b).barLength) }
            val voices = part.bars.maxOfOrNull { it.voices.size } ?: 1
            for (v in 0 until voices) {
                val open = HashMap<Int, Pair<Long, Int>>()   // midi → (start tick, velocity) of a note tied on
                partOrder.forEachIndexed { i, b ->
                    var at = Rational.ZERO
                    for (e in part.bars[b].voices.getOrNull(v).orEmpty()) {
                        val on = partStarts[i] + ticks(at)
                        at += e.length
                        val off = partStarts[i] + ticks(at)
                        val held = HashSet<Int>()
                        for (n in e.notes) {
                            val m = n.pitch.midi.coerceIn(0, 127)
                            val (start, vel) = open.remove(m) ?: (on to (n.velocity ?: 80))
                            if (n.tie) { open[m] = start to vel; held += m }
                            else { track.add(start, 2, 0x90 or ch, m, vel); track.add(maxOf(start + 1, off), 0, 0x80 or ch, m, 0) }
                        }
                        // A tie that the next event doesn't continue ends where it was.
                        for ((m, sv) in open.entries.toList()) if (m !in held) { track.add(sv.first, 2, 0x90 or ch, m, sv.second); track.add(on, 0, 0x80 or ch, m, 0); open.remove(m) }
                    }
                }
                val end = partStarts.last()
                for ((m, sv) in open) { track.add(sv.first, 2, 0x90 or ch, m, sv.second); track.add(maxOf(sv.first + 1, end), 0, 0x80 or ch, m, 0) }
            }
            tracks += track
        }
        val out = ByteArrayOutputStream()
        fun int32(v: Int) = out.write(byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte()))
        fun int16(v: Int) = out.write(byteArrayOf((v shr 8).toByte(), v.toByte()))
        out.write("MThd".toByteArray()); int32(6); int16(1); int16(tracks.size); int16(PPQ)
        for (t in tracks) { val b = t.bytes(); out.write("MTrk".toByteArray()); int32(b.size); out.write(b) }
        return out.toByteArray()
    }
}
