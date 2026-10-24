package com.betterappmafia.apophenia.core.tools

import com.betterappmafia.apophenia.core.rhythm.TimeSignature

/** How a beat sounds (plan §7: accents per beat). SILENT keeps the beat in the count but plays nothing. */
enum class Accent { STRONG, ACCENT, NORMAL, SILENT }

/** The sounds the engine knows. Ids are shared with engine.cpp (kSound*); never renumber. */
enum class ClickSound(val id: Int) { STRONG(0), ACCENT(1), NORMAL(2), SUB(3), POLY(4) }

/** One click in a bar, [at] counted in BPM units from the bar's start. */
data class ClickEvent(val at: Double, val sound: ClickSound, val gain: Float, val beat: Int)

/** One bar, ready for the engine: its length in BPM units and its clicks in time order. */
data class BarProgram(val units: Double, val events: List<ClickEvent>)

/** +[stepBpm] every [everyBars] bars, stopping at [maxBpm] (plan §7 tempo trainer). */
data class TempoTrainer(val stepBpm: Int, val everyBars: Int, val maxBpm: Int) {
    init { require(everyBars >= 1 && stepBpm != 0) { "trainer needs a step and at least one bar" } }
}

/** Play [playBars] bars, then [muteBars] bars of silence, repeating; the count goes on underneath (plan §7). */
data class SilentBars(val playBars: Int, val muteBars: Int) {
    init { require(playBars >= 1 && muteBars >= 1) { "silent bars need bars to play and to mute" } }
}

/**
 * Everything the full metronome can be set to (plan §7). Pure data: [bar] compiles it for the engine,
 * [bpmAtBar] and [audible] are the rules engine.cpp mirrors bar by bar.
 *
 * What the BPM counts [Claude]: the beat. In simple meters that is the denominator (4/4 counts
 * quarters); in compound meters (6/8, 9/8, 12/8) the dotted beat (6/8 at 60 = two dotted quarters a
 * second); in additive meters with unequal beats (7/8 as 2+2+3) the denominator, because the beats
 * have no common length.
 */
data class MetronomeSettings(
    val bpm: Double = 96.0,
    val meter: TimeSignature = TimeSignature.of(4, 4),
    /** One per beat; missing entries take the default (strong first beat, the rest normal). */
    val accents: List<Accent> = emptyList(),
    /** Clicks per beat, 1..9 (5 and 7 included). */
    val subdivision: Int = 1,
    /** Where the off pulse of each pair falls, 0.5 (straight) to 0.75 (hard swing). Even subdivisions only. */
    val swing: Double = 0.5,
    /** Clicks spread evenly across the bar against the beats (3 against 4), 0 = off. */
    val poly: Int = 0,
    val countInBars: Int = 0,
    val trainer: TempoTrainer? = null,
    val silent: SilentBars? = null,
) {
    init {
        require(bpm.isFinite()) { "bpm must be a number" }
        require(subdivision in 1..9) { "subdivision must be 1..9, was $subdivision" }
        require(swing in 0.5..0.75) { "swing must be 0.5..0.75, was $swing" }
        require(poly in 0..16) { "poly must be 0..16, was $poly" }
        require(countInBars in 0..4) { "count-in must be 0..4 bars" }
    }

    val clampedBpm: Double get() = bpm.coerceIn(MIN_BPM, MAX_BPM)

    /** Each beat's length in BPM units. */
    val beatUnits: List<Double>
        get() = when {
            meter.isCompound -> meter.groups.map { 1.0 }
            meter.groups.all { it == meter.groups[0] } -> meter.groups.map { 1.0 }
            else -> meter.groups.map { it.toDouble() }
        }

    fun accentOf(beat: Int): Accent = accents.getOrNull(beat) ?: if (beat == 0) Accent.STRONG else Accent.NORMAL

    /** A beat accent cycles STRONG → ACCENT → NORMAL → SILENT → STRONG. */
    fun cycleAccent(beat: Int): MetronomeSettings {
        val all = List(meter.beatsPerBar) { accentOf(it) }.toMutableList()
        all[beat] = Accent.entries[(all[beat].ordinal + 1) % Accent.entries.size]
        return copy(accents = all)
    }

    /** Same settings in another meter; accents are kept for beats that still exist. */
    fun withMeter(m: TimeSignature) = copy(meter = m, accents = accents.take(m.beatsPerBar), subdivision = if (m.isCompound && !meter.isCompound) 3 else if (!m.isCompound && meter.isCompound) 1 else subdivision)

    /** The bar the engine plays: beats with their accents, subdivision clicks (swung), the poly layer. */
    fun bar(): BarProgram {
        val lens = beatUnits
        val events = ArrayList<ClickEvent>()
        var start = 0.0
        lens.forEachIndexed { beat, len ->
            val accent = accentOf(beat)
            if (accent != Accent.SILENT) {
                val sound = when (accent) { Accent.STRONG -> ClickSound.STRONG; Accent.ACCENT -> ClickSound.ACCENT; else -> ClickSound.NORMAL }
                events += ClickEvent(start, sound, 1f, beat)
            }
            for (k in 1 until subdivision) events += ClickEvent(start + pulseAt(k, len), ClickSound.SUB, SUB_GAIN, beat)
            start += len
        }
        val units = start
        if (poly > 0) for (k in 0 until poly) events += ClickEvent(units * k / poly, ClickSound.POLY, POLY_GAIN, -1)
        return BarProgram(units, events.sortedWith(compareBy({ it.at }, { it.sound.id })))
    }

    /** Pulse k of a beat [len] long: even divisions swing in pairs, the first of each pair on time. */
    private fun pulseAt(k: Int, len: Double): Double {
        val d = len / subdivision
        if (subdivision % 2 != 0 || k % 2 == 0) return k * d
        return (k - 1) * d + 2 * d * swing
    }

    /** The count-in bar: every beat sounds, accents as set but nothing silent, no subdivision or poly. */
    fun countInBar(): BarProgram {
        val lens = beatUnits
        var start = 0.0
        val events = lens.mapIndexed { beat, len ->
            val e = ClickEvent(start, if (beat == 0) ClickSound.STRONG else ClickSound.NORMAL, 1f, beat)
            start += len
            e
        }
        return BarProgram(start, events)
    }

    /** Tempo of bar [bar] (0 = the first bar after the count-in; count-in bars are negative and play at [bpm]). */
    fun bpmAtBar(bar: Long): Double {
        val t = trainer ?: return clampedBpm
        if (bar < 0) return clampedBpm
        val steps = bar / t.everyBars
        val raw = clampedBpm + steps * t.stepBpm
        return if (t.stepBpm > 0) raw.coerceAtMost(t.maxBpm.toDouble()) else raw.coerceAtLeast(t.maxBpm.toDouble())
    }

    /** Whether bar [bar] is heard (silent bars mute some; the count-in always sounds). */
    fun audible(bar: Long): Boolean {
        val s = silent ?: return true
        if (bar < 0) return true
        return bar % (s.playBars + s.muteBars) < s.playBars
    }

    /** Storage form for app settings: `1|bpm|meter|accents|sub|swing|poly|countIn|trainer|silent`. */
    fun encode(): String = listOf(
        "1", bpm.toString(), meter.toString(), List(meter.beatsPerBar) { "SANX"[accentOf(it).ordinal] }.joinToString(""),
        subdivision.toString(), swing.toString(), poly.toString(), countInBars.toString(),
        trainer?.let { "${it.stepBpm},${it.everyBars},${it.maxBpm}" } ?: "-",
        silent?.let { "${it.playBars},${it.muteBars}" } ?: "-",
    ).joinToString("|")

    companion object {
        /** Reads [encode]'s output; null for anything else (the caller keeps its defaults). */
        fun decode(s: String?): MetronomeSettings? = runCatching {
            val f = s!!.split('|')
            require(f.size == 10 && f[0] == "1")
            val accents = f[3].map { c -> Accent.entries["SANX".indexOf(c)] }
            fun ints(x: String) = x.split(',').map(String::toInt)
            MetronomeSettings(
                bpm = f[1].toDouble(), meter = TimeSignature.parse(f[2]), accents = accents,
                subdivision = f[4].toInt(), swing = f[5].toDouble(), poly = f[6].toInt(), countInBars = f[7].toInt(),
                trainer = if (f[8] == "-") null else ints(f[8]).let { TempoTrainer(it[0], it[1], it[2]) },
                silent = if (f[9] == "-") null else ints(f[9]).let { SilentBars(it[0], it[1]) },
            )
        }.getOrNull()

        const val MIN_BPM = 20.0
        const val MAX_BPM = 400.0
        const val SUB_GAIN = 0.45f
        const val POLY_GAIN = 0.7f

        /** Meters the meter menu offers before Custom (C4: 4/4, 3/4, 6/8, 7/8 · 2+2+3, Custom…). */
        val COMMON: List<TimeSignature> = listOf(
            "4/4", "3/4", "2/4", "5/4", "6/8", "9/8", "12/8", "2+3/8", "3+2/8", "2+2+3/8", "3+2+2/8", "3+3+2/8",
        ).map(TimeSignature::parse)

        /**
         * Tap tempo from tap times in milliseconds: the median interval of the last taps (up to 8),
         * so one late tap doesn't drag it; a gap over 2 s starts again. Null until two taps.
         */
        fun tapBpm(tapsMs: List<Long>): Double? {
            if (tapsMs.size < 2) return null
            var start = tapsMs.size - 1
            while (start > 0 && tapsMs[start] - tapsMs[start - 1] <= 2000 && tapsMs.size - start < 8) start--
            val recent = tapsMs.subList(start, tapsMs.size)
            if (recent.size < 2) return null
            val gaps = recent.zipWithNext { a, b -> (b - a).toDouble() }.sorted()
            val mid = gaps.size / 2
            val median = if (gaps.size % 2 == 1) gaps[mid] else (gaps[mid - 1] + gaps[mid]) / 2
            return if (median <= 0) null else (60_000.0 / median).coerceIn(MIN_BPM, MAX_BPM)
        }
    }
}

/**
 * The click grid saved with a take recorded while the metronome ran (plan §7, skill: labelled as the
 * click, not as proof of the performed tempo). Frames are in the take file's timeline, count-in clicks
 * included and flagged. [aligned] says whether output/input timestamps lined the clicks up with the
 * mic; when false the grid is late by the route's round trip.
 */
data class ClickGrid(
    val rate: Int,
    val bpm: Double,
    val meter: String,
    val frames: List<Long>,
    val downbeats: Set<Int>,
    val countIn: Set<Int>,
    val aligned: Boolean,
) {
    /** Versioned JSON for TakeEntity.metronome_grid (no library: the format is tiny and fixed). */
    fun toJson(): String = buildString {
        append("{\"v\":1,\"source\":\"metronome\",\"rate\":").append(rate)
        append(",\"bpm\":").append(bpm)
        append(",\"meter\":\"").append(meter).append('"')
        append(",\"aligned\":").append(aligned)
        append(",\"clicks\":[").append(frames.joinToString(",")).append(']')
        append(",\"downbeats\":[").append(downbeats.sorted().joinToString(",")).append(']')
        append(",\"countIn\":[").append(countIn.sorted().joinToString(",")).append("]}")
    }

    companion object {
        /** Reads [toJson]'s output; null for anything else, including a newer version. */
        fun fromJson(s: String?): ClickGrid? = runCatching {
            if (s == null) return null
            fun field(name: String): String {
                val k = "\"$name\":"
                val i = s.indexOf(k).also { require(it >= 0) } + k.length
                return if (s[i] == '[') s.substring(i + 1, s.indexOf(']', i)) else if (s[i] == '"') s.substring(i + 1, s.indexOf('"', i + 1))
                else s.substring(i).takeWhile { it != ',' && it != '}' }
            }
            fun longs(name: String) = field(name).split(',').filter { it.isNotBlank() }.map { it.trim().toLong() }
            if (field("v") != "1") return null
            ClickGrid(
                field("rate").toInt(), field("bpm").toDouble(), field("meter"), longs("clicks"),
                longs("downbeats").map { it.toInt() }.toSet(), longs("countIn").map { it.toInt() }.toSet(), field("aligned").toBoolean(),
            )
        }.getOrNull()
    }
}
