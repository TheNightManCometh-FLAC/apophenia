package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.instrument.Instrument
import com.betterappmafia.apophenia.core.instrument.Tunings
import com.betterappmafia.apophenia.core.rhythm.TICKS_PER_QUARTER
import com.betterappmafia.apophenia.core.rhythm.Tempo
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import com.betterappmafia.apophenia.core.tools.KeySpeller

/**
 * MIDI import (plan §11 "Import: … MIDI"): a Standard MIDI File, format 0 or 1, as written music.
 *
 * Read: notes (note-on at velocity 0 is a note-off), the tempo map, time signatures, key signatures, track names,
 * markers (as rehearsal marks) and program changes. Each track becomes a part, split by channel when a track uses
 * more than one; channel 10 is drums (a percussion part).
 *
 * [Claude] choices:
 * - Times are snapped to sixteenths, or for a quarter-note beat whose notes sit closer to a triplet grid, to
 *   sixteenth triplets; the bars are then written the way the piano roll writes them (beat-aware durations, ties
 *   over beats and barlines, overlapping notes as tied chords in one voice).
 * - A General MIDI guitar program (25–32) gives a part a standard-tuned guitar and tab, a bass program (33–40) a
 *   4-string bass; anything else is notation, bass clef when its middle note is below A3.
 * - Pitches are spelled in the file's first key signature, else C major. Tempo and key changes go on the bar
 *   they fall in. A meter given only as 5/8 or 7/8 is grouped twos then a three (MIDI can't say).
 * - Velocities are kept when they vary; when every note has the same velocity none is stored.
 * - Lyrics and other text events, pitch bend and controllers aren't read; SMPTE-timed files are read as 120 BPM.
 */
object MidiRead {
    class Result(val score: Score, val dropped: List<String>)

    private class Ev(val tick: Long, val track: Int, val status: Int, val a: Int, val b: Int, val meta: Int = -1, val data: ByteArray = ByteArray(0))

    private class Raw(val start: Long, var end: Long, val midi: Int, val velocity: Int)

    fun read(bytes: ByteArray, name: String = ""): Result {
        var p = 0
        fun need(n: Int) = require(p + n <= bytes.size) { "The MIDI file ends too early." }
        fun u8(): Int { need(1); return bytes[p++].toInt() and 0xFF }
        fun u16() = (u8() shl 8) or u8()
        fun u32() = (u16().toLong() shl 16) or u16().toLong()
        fun vlq(): Long { var v = 0L; repeat(4) { val c = u8(); v = (v shl 7) or (c and 0x7F).toLong(); if (c and 0x80 == 0) return v }; return v }
        require(bytes.size >= 14 && String(bytes, 0, 4, Charsets.ISO_8859_1) == "MThd") { "It isn't a Standard MIDI File." }
        p = 4
        val headLen = u32().toInt()
        val format = u16(); val ntracks = u16(); val division = u16()
        p = 8 + headLen
        require(format in 0..2) { "MIDI format $format isn't one this app reads." }
        val dropped = LinkedHashSet<String>()
        // SMPTE timing (top bit set) has no beats: count it as 120 BPM, so a quarter is half a second.
        val ppq = if (division and 0x8000 != 0) {
            dropped += "SMPTE timing (read as 120 BPM)"
            val fps = 256 - (division shr 8); val perFrame = division and 0xFF
            (fps * perFrame / 2).coerceAtLeast(1)
        } else division.coerceAtLeast(1)

        val events = ArrayList<Ev>()
        val trackNames = HashMap<Int, String>()
        var track = 0
        while (p + 8 <= bytes.size && track < ntracks) {
            val id = String(bytes, p, 4, Charsets.ISO_8859_1); p += 4
            val len = u32().toInt()
            val end = minOf(bytes.size, p + len)
            if (id != "MTrk") { p = end; continue }
            var t = 0L
            var running = 0
            while (p < end) {
                t += vlq()
                var s = u8()
                if (s < 0x80) { require(running != 0) { "The MIDI file is damaged (no running status)." }; p--; s = running }
                when {
                    s == 0xFF -> {
                        val type = u8(); val l = vlq().toInt(); need(l)
                        val data = bytes.copyOfRange(p, p + l); p += l
                        if (type == 0x03 && !trackNames.containsKey(track)) trackNames[track] = String(data, Charsets.UTF_8).trim()
                        if (type == 0x2F) p = end
                        else events += Ev(t, track, 0xFF, 0, 0, type, data)
                    }
                    s == 0xF0 || s == 0xF7 -> { val l = vlq().toInt(); p += l; running = 0 }
                    else -> {
                        running = s
                        val a = u8()
                        val b = if (s and 0xF0 == 0xC0 || s and 0xF0 == 0xD0) 0 else u8()
                        events += Ev(t, track, s, a, b)
                    }
                }
            }
            p = end
            track++
        }
        fun at960(t: Long) = Math.round(t.toDouble() * TICKS_PER_QUARTER / ppq)

        // The conductor: tempo, meter, key and markers from every track.
        val metas = events.filter { it.status == 0xFF }.sortedBy { it.tick }
        val tempos = metas.filter { it.meta == 0x51 && it.data.size >= 3 }.map { e ->
            val us = ((e.data[0].toInt() and 0xFF) shl 16) or ((e.data[1].toInt() and 0xFF) shl 8) or (e.data[2].toInt() and 0xFF)
            at960(e.tick) to Math.round(60_000_000.0 / us.coerceAtLeast(1) * 100) / 100.0
        }
        val meters = metas.filter { it.meta == 0x58 && it.data.size >= 2 }.mapNotNull { e ->
            MusicXmlRead.meter("${e.data[0].toInt() and 0xFF}", "${1 shl (e.data[1].toInt() and 0x0F)}")?.let { at960(e.tick) to it }
        }
        val keys = metas.filter { it.meta == 0x59 && it.data.size >= 2 }.mapNotNull { e ->
            MusicXmlRead.key(e.data[0].toInt(), if (e.data[1].toInt() == 1) "minor" else null)?.let { at960(e.tick) to it }
        }
        val markers = metas.filter { it.meta == 0x06 }.map { at960(it.tick) to String(it.data, Charsets.UTF_8).trim() }.filter { it.second.isNotEmpty() }
        if (metas.any { it.meta == 0x05 }) dropped += "lyrics"

        // Notes per (track, channel), and the program each starts with.
        val notes = LinkedHashMap<Pair<Int, Int>, MutableList<Raw>>()
        val programs = HashMap<Pair<Int, Int>, Int>()
        val open = HashMap<Triple<Int, Int, Int>, ArrayDeque<Pair<Long, Int>>>()
        for (e in events.filter { it.status != 0xFF }.sortedWith(compareBy({ it.tick }, { if (it.status and 0xF0 == 0x80 || it.b == 0) 0 else 1 }))) {
            val ch = e.status and 0x0F
            val key = e.track to ch
            when (e.status and 0xF0) {
                0xC0 -> if (key !in programs) programs[key] = e.a
                0x90, 0x80 -> {
                    val on = e.status and 0xF0 == 0x90 && e.b > 0
                    val k = Triple(e.track, ch, e.a)
                    if (on) open.getOrPut(k) { ArrayDeque() }.addLast(at960(e.tick) to e.b)
                    else open[k]?.removeFirstOrNull()?.let { (s, v) -> notes.getOrPut(key) { ArrayList() } += Raw(s, at960(e.tick), e.a, v) }
                }
                0xE0, 0xB0 -> dropped += "pitch bend and controllers"
            }
        }
        // A note never released ends at the last event.
        val last = events.maxOfOrNull { at960(it.tick) } ?: 0L
        for ((k, q) in open) for ((s, v) in q) notes.getOrPut(k.first to k.second) { ArrayList() } += Raw(s, maxOf(last, s + 1), k.third, v)
        val kept = notes.filter { it.value.isNotEmpty() }
        require(kept.isNotEmpty()) { "There are no notes in it." }

        // Bars, from the meter map, covering every note.
        val meterAt0 = meters.firstOrNull { it.first == 0L }?.second ?: TimeSignature.of(4, 4)
        val endTick = kept.values.flatten().maxOf { it.end }
        val barStarts = ArrayList<Long>(); val barMeters = ArrayList<TimeSignature>()
        var t = 0L; var m = meterAt0
        while (t < endTick || barStarts.isEmpty()) {
            meters.lastOrNull { it.first <= t }?.let { m = it.second }
            barStarts += t; barMeters += m
            t += m.barTicks
        }
        barStarts += t
        fun barOf(tick: Long) = (barStarts.indexOfLast { it <= tick }).coerceIn(0, barMeters.size - 1)

        val key0 = keys.firstOrNull { it.first == 0L }?.second ?: keys.firstOrNull()?.second
        val tempo0 = tempos.firstOrNull { it.first == 0L }?.second ?: 120.0
        val bars = barMeters.mapIndexed { i, meter ->
            val from = barStarts[i]; val to = barStarts[i + 1]
            Bar(
                voices = listOf(FromNotes.durations(0, meter.barTicks.toLong(), meter, 4).map { Event(it) }),
                meter = if (i > 0 && meter != barMeters[i - 1]) meter else null,
                tempo = if (i > 0) tempos.lastOrNull { it.first in from until to }?.second?.let(::Tempo)?.takeIf { it.bpm != tempoNow(tempos, from) } else null,
                key = if (i > 0) keys.lastOrNull { it.first in from until to && it.second != key0 }?.second else null,
                rehearsal = markers.firstOrNull { barOf(it.first) == i }?.second,
            )
        }
        val trackCount = kept.keys.map { it.first }.distinct()
        val speller = KeySpeller(key0 ?: KeyMode.parseOrNull("C major")!!)
        var score = Score(name, meterAt0, key0, Tempo(tempo0), emptyList())
        for ((k, raw) in kept) {
            val (tr, ch) = k
            val drums = ch == 9
            val program = programs[k]
            val instrument = when {
                drums || program == null -> null
                program in 24..31 -> Instrument(Tunings.GUITAR_STANDARD)
                program in 32..39 -> Instrument(Tunings.BASS_STANDARD)
                else -> null
            }
            val channels = kept.keys.count { it.first == tr }
            val named = trackNames[tr]?.takeIf { it.isNotBlank() }
            val partName = when {
                named != null -> named + if (channels > 1) (if (drums) " (drums)" else " (channel ${ch + 1})") else ""
                drums -> "Drums"
                channels > 1 -> "Channel ${ch + 1}"
                else -> "Track ${trackCount.indexOf(tr) + 1}"
            }
            val snapped = snap(raw, barStarts, barMeters)
            val sorted = snapped.map { it.midi }.sorted()
            val clef = when {
                drums -> Clef.PERCUSSION
                instrument != null -> if (instrument.tuning.strings.first().midi < 36) Clef.BASS else Clef.TREBLE
                (sorted.getOrNull(sorted.size / 2) ?: 60) < 57 -> Clef.BASS
                else -> Clef.TREBLE
            }
            val varied = snapped.map { it.velocity }.distinct().size > 1
            val roll = snapped.mapIndexed { i, r ->
                val pitch = speller.spell(r.midi)
                PianoRoll.RollNote(i, r.start, r.end - r.start, pitch, 0, Note(pitch, velocity = if (varied) r.velocity.coerceIn(1, 127) else null))
            }
            val triplets = roll.any { it.start % 240 != 0L || it.end % 240 != 0L }
            score = score.copy(parts = score.parts + Part(partName, clef, instrument, bars))
            score = PianoRoll.rebuild(score, score.parts.lastIndex, emptyList(), roll, if (triplets) 12 else 4)
        }
        return Result(score, dropped.toList())
    }

    private fun tempoNow(tempos: List<Pair<Long, Double>>, before: Long) = tempos.lastOrNull { it.first < before }?.second ?: 120.0

    /**
     * Starts and ends on the grid of the beat they fall in: sixteenths (240 ticks), or sixteenth triplets (160) for a
     * quarter beat whose boundaries sit at least twice as close to that grid. Each note lasts at least one step; a
     * note is cut short where the next note of the same pitch starts.
     */
    private fun snap(notes: List<Raw>, barStarts: List<Long>, meters: List<TimeSignature>): List<Raw> {
        data class Beat(val start: Long, val length: Long)
        val beats = ArrayList<Beat>()
        for (i in meters.indices) for ((s, l) in meters[i].beatStarts.zip(meters[i].beatLengths)) {
            beats += Beat(barStarts[i] + (s * 3840).let { it.num / it.den }, (l * 3840).let { it.num / it.den })
        }
        fun beatOf(t: Long) = beats.indexOfLast { it.start <= t }.coerceAtLeast(0)
        fun err(t: Long, from: Long, g: Long) = Math.abs((t - from) - Math.round((t - from).toDouble() / g) * g)
        val boundaries = notes.flatMap { listOf(it.start, it.end) }.groupBy(::beatOf)
        val grid = beats.indices.map { i ->
            val b = beats[i]
            val ts = boundaries[i].orEmpty()
            if (b.length == 960L && ts.size >= 2) {
                val straight = ts.sumOf { err(it, b.start, 240) }
                val triplet = ts.sumOf { err(it, b.start, 160) }
                if (triplet * 2 < straight) 160L else 240L
            } else if (b.length % 240 == 0L) 240L else 120L
        }
        fun q(t: Long): Long {
            val i = beatOf(t); val b = beats[i]; val g = grid[i]
            return minOf(b.start + Math.round((t - b.start).toDouble() / g) * g, b.start + b.length).let { if (it < 0) 0 else it }
        }
        val out = notes.map { n -> val s = q(n.start); Raw(s, maxOf(q(n.end), s + grid[beatOf(s)]), n.midi, n.velocity) }
            .sortedWith(compareBy({ it.start }, { it.midi }))
            .distinctBy { it.start to it.midi }
        // The same pitch again before the last one ends: the last one stops there.
        for ((_, same) in out.groupBy { it.midi }) for (i in 0 until same.size - 1) if (same[i].end > same[i + 1].start) same[i].end = same[i + 1].start
        return out.filter { it.end > it.start }
    }

}
