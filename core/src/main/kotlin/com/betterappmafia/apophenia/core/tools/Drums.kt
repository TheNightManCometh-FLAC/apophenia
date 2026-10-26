package com.betterappmafia.apophenia.core.tools

import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/** The drums a groove (and later a drum chart, plan §3.1) is written for, top of the tab to the bottom. */
enum class DrumVoice(val tab: String, val label: String) {
    CRASH("CC", "Crash"), RIDE("RD", "Ride"), HAT("HH", "Hi-hat"), HAT_FOOT("HF", "Hi-hat foot"), BELL("BL", "Bell"),
    TOM_HI("T1", "High tom"), TOM_MID("T2", "Mid tom"), SNARE("SD", "Snare"), CROSS_STICK("XS", "Cross-stick"),
    TOM_FLOOR("FT", "Floor tom"), KICK("BD", "Kick"),
}

/** One cell of drum tab: x hit, X accent, g ghost note, o open hi-hat, - nothing. */
enum class DrumHit(val char: Char, val gain: Float) {
    HIT('x', 0.7f), ACCENT('X', 1f), GHOST('g', 0.22f), OPEN('o', 0.7f);

    companion object { fun of(c: Char): DrumHit? = entries.firstOrNull { it.char == c } }
}

/**
 * Drum tab: one row per drum, [stepsPerBeat] cells to a beat (4 = sixteenths, 3 = triplet eighths),
 * over [bars] bars of [meter]. Rows are written like `x-x-x-x-|x-x-x-x-` ('|' and spaces are ignored).
 */
data class DrumGrid(val meter: TimeSignature, val stepsPerBeat: Int, val bars: Int, val rows: Map<DrumVoice, List<DrumHit?>>) {
    val beatsPerBar: Int get() = meter.beatsPerBar
    val steps: Int get() = beatsPerBar * stepsPerBeat * bars

    init {
        require(stepsPerBeat in 1..8 && bars in 1..DrumChart.MAX_BARS) { "bad grid size" }
        rows.forEach { (v, r) -> require(r.size == steps) { "${v.tab} has ${r.size} steps, the grid has $steps" } }
    }

    /** The row as drum tab, a bar line every bar. */
    fun tab(v: DrumVoice): String = (rows[v] ?: List(steps) { null }).chunked(beatsPerBar * stepsPerBeat)
        .joinToString("|") { bar -> bar.joinToString("") { it?.char?.toString() ?: "-" } }

    val stepsPerBar: Int get() = beatsPerBar * stepsPerBeat

    /** The next mark for a tap on a cell: rest, x, X, g, then o on the hi-hat, back to rest. */
    fun cycled(v: DrumVoice, i: Int): DrumGrid {
        val row = rows[v] ?: List(steps) { null }
        val order = listOf(null, DrumHit.HIT, DrumHit.ACCENT, DrumHit.GHOST) + listOfNotNull(DrumHit.OPEN.takeIf { v == DrumVoice.HAT })
        val next = order[(order.indexOf(row[i]).coerceAtLeast(0) + 1) % order.size]
        return copy(rows = rows + (v to row.toMutableList().also { it[i] = next }))
    }

    fun withVoice(v: DrumVoice) = if (v in rows) this else copy(rows = rows + (v to List(steps) { null }))
    fun withoutVoice(v: DrumVoice) = copy(rows = rows - v)

    /**
     * The same grid in another meter, step size or length. A hit stays where its bar, beat and place in
     * the beat still exist (eighths survive going from sixteenths to eighths; the sixteenths between don't).
     */
    fun resized(meter: TimeSignature = this.meter, stepsPerBeat: Int = this.stepsPerBeat, bars: Int = this.bars): DrumGrid {
        val per = meter.beatsPerBar * stepsPerBeat
        return DrumGrid(meter, stepsPerBeat, bars, rows.mapValues { (_, row) ->
            val out = MutableList<DrumHit?>(per * bars) { null }
            row.forEachIndexed { i, h ->
                if (h == null) return@forEachIndexed
                val bar = i / stepsPerBar
                val beat = i % stepsPerBar / this.stepsPerBeat
                val num = (i % this.stepsPerBeat) * stepsPerBeat
                if (bar < bars && beat < meter.beatsPerBar && num % this.stepsPerBeat == 0) out[bar * per + beat * stepsPerBeat + num / this.stepsPerBeat] = h
            }
            out
        })
    }

    companion object {
        fun of(meter: String, stepsPerBeat: Int, bars: Int, vararg rows: Pair<DrumVoice, String>) = DrumGrid(
            TimeSignature.parse(meter), stepsPerBeat, bars,
            rows.associate { (v, s) ->
                v to s.filter { it != '|' && it != ' ' }.map { c -> if (c == '-') null else requireNotNull(DrumHit.of(c)) { "'$c' isn't a drum tab mark" } }
            },
        )
    }
}

/**
 * A common groove, Reference material for drummers (plan §8; Christopher, 22 Oct 2026: "Add common
 * grooves to the drum Reference too"). [Claude] Generic textbook versions of each style, not
 * transcriptions of particular records. [swing] works like the metronome's: where each off step of a
 * pair falls, 0.5 straight.
 */
data class Groove(val id: String, val name: String, val style: String, val bpm: Int, val grid: DrumGrid, val about: String, val swing: Double = 0.5) {
    /** Where step [i] starts, in beats from the top, swing applied. */
    fun stepBeats(i: Int): Double {
        val d = 1.0 / grid.stepsPerBeat
        if (grid.stepsPerBeat % 2 != 0 || i % 2 == 0) return i * d
        return (i - 1) * d + 2 * d * swing
    }

    /** The step sounding [beats] beats after the top (the grid repeats). */
    fun stepAt(beats: Double): Int {
        val cycle = grid.steps.toDouble() / grid.stepsPerBeat
        val b = ((beats % cycle) + cycle) % cycle
        var s = 0
        while (s + 1 < grid.steps && stepBeats(s + 1) <= b) s++
        return s
    }

    fun render(bpm: Double, rate: Int, passes: Int): FloatArray {
        val beat = rate * 60.0 / bpm
        val cycle = grid.steps.toDouble() / grid.stepsPerBeat
        val hits = ArrayList<DrumKit.Hit>()
        for (p in 0 until passes) for ((v, row) in grid.rows) row.forEachIndexed { i, h ->
            if (h != null) hits += DrumKit.Hit(((p * cycle + stepBeats(i)) * beat).toInt(), v, h)
        }
        return DrumKit.render(hits, (passes * cycle * beat).toInt(), rate)
    }

    companion object {
        private const val ROCK = "Rock and pop"
        private const val FUNK = "Funk, soul and R&B"
        private const val TRIPLET = "Shuffle, blues and jazz"
        private const val DANCE = "Dance and hip-hop"
        private const val WORLD = "Reggae, country and Afro-Cuban"
        private fun g(id: String, name: String, style: String, bpm: Int, about: String, grid: DrumGrid, swing: Double = 0.5) =
            Groove(id, name, style, bpm, grid, about, swing)
        private val H = DrumVoice.HAT; private val S = DrumVoice.SNARE; private val K = DrumVoice.KICK
        private val R = DrumVoice.RIDE; private val F = DrumVoice.HAT_FOOT; private val X = DrumVoice.CROSS_STICK
        private val B = DrumVoice.BELL; private val C = DrumVoice.CRASH

        val COMMON: List<Groove> = listOf(
            g("rock8", "Rock beat", ROCK, 100, "Eighths on the hat, kick on 1 and 3, snare backbeat on 2 and 4.",
                DrumGrid.of("4/4", 4, 1, H to "x-x-x-x-x-x-x-x-", S to "----X-------X---", K to "x-------x-------")),
            g("rock8b", "Rock beat, kick on the and of 3", ROCK, 110, "The same backbeat with a second kick on the 'and' of 3.",
                DrumGrid.of("4/4", 4, 1, H to "x-x-x-x-x-x-x-x-", S to "----X-------X---", K to "x-------x-x-----")),
            g("pop16", "Sixteenth-note hat", ROCK, 92, "Sixteenths on the hat, accent on each beat; kick on 1, the 'and' of 2, and 3.",
                DrumGrid.of("4/4", 4, 1, H to "XxxxXxxxXxxxXxxx", S to "----X-------X---", K to "x-----x-x-------")),
            g("halftime", "Half-time", ROCK, 140, "Snare only on 3, so the bar feels twice as long.",
                DrumGrid.of("4/4", 4, 1, H to "x-x-x-x-x-x-x-x-", S to "--------X-------", K to "x-----x---------")),
            g("ride8", "Rock beat on the ride, crash on 1", ROCK, 120, "Chorus feel: ride eighths, crash and kick together on 1.",
                DrumGrid.of("4/4", 4, 2, C to "x---------------|----------------", R to "--x-x-x-x-x-x-x-|x-x-x-x-x-x-x-x-", S to "----X-------X---|----X-------X---", K to "x-------x-------|x-------x-x-----")),
            g("punk", "Fast punk beat", ROCK, 180, "Kick on every beat, snare on every 'and'.",
                DrumGrid.of("4/4", 2, 1, H to "xxxxxxxx", S to "-X-X-X-X", K to "x-x-x-x-")),
            g("motown", "Motown backbeat", FUNK, 116, "Snare on all four beats, the backbeat accented; eighths on the hat.",
                DrumGrid.of("4/4", 4, 1, H to "x-x-x-x-x-x-x-x-", S to "x---X---x---X---", K to "x-----x-x-----x-")),
            g("funk16", "Funk with ghost notes", FUNK, 96, "Sixteenths on the hat, a hard backbeat, quiet ghost notes on the snare between.",
                DrumGrid.of("4/4", 4, 1, H to "xxxxxxxxxxxxxxxx", S to "----X--g-g--X--g", K to "x-x-------x--x--")),
            g("shuffle", "Shuffle", TRIPLET, 120, "Triplet feel: the hat plays the first and last of each triplet; backbeat on 2 and 4.",
                DrumGrid.of("4/4", 3, 1, H to "x-xx-xx-xx-x", S to "---X-----X--", K to "x-----x-----")),
            g("blues128", "12/8 slow blues", TRIPLET, 60, "Every eighth of the 12/8 bar on the hat, backbeat on 2 and 4.",
                DrumGrid.of("12/8", 3, 1, H to "xxxxxxxxxxxx", S to "---X-----X--", K to "x-----x--x--")),
            g("swing", "Jazz swing", TRIPLET, 160, "The ride's 'ding, ding-a ding': quarters with the last triplet of 2 and 4; hat foot on 2 and 4, kick feathered on every beat.",
                DrumGrid.of("4/4", 3, 1, R to "x--x-xx--x-x", F to "---x-----x--", K to "g--g--g--g--")),
            g("disco", "Disco / four on the floor", DANCE, 120, "Kick on every beat, open hat on every 'and', clap or snare on 2 and 4.",
                DrumGrid.of("4/4", 4, 1, H to "x-o-x-o-x-o-x-o-", S to "----X-------X---", K to "x---x---x---x---")),
            g("boombap", "Boom bap (hip-hop)", DANCE, 90, "Swung sixteenths on the hat, kick on 1 and the 'and' of 2 and 3, backbeat on 2 and 4.",
                DrumGrid.of("4/4", 4, 1, H to "x-x-x-x-x-x-x-x-", S to "----X-------X---", K to "x-----x---x-----"), swing = 0.58),
            g("trap", "Trap (half-time)", DANCE, 140, "Half-time snare on 3, sixteenth hats with a thirty-second roll at the end, sparse kick.",
                DrumGrid.of("4/4", 8, 1, H to "x-x-x-x-x-x-x-x-x-x-x-x-x-xxxxxx", S to "----------------X---------------", K to "x-----------x---------x---------")),
            g("onedrop", "Reggae one drop", WORLD, 76, "Nothing on 1: kick and cross-stick together on 3; eighths on the hat.",
                DrumGrid.of("4/4", 4, 1, H to "x-x-x-x-x-x-x-x-", X to "--------x-------", K to "--------x-------")),
            g("train", "Country train beat", WORLD, 160, "Sixteenths on the snare, accents on 2 and 4, kick on 1 and 3.",
                DrumGrid.of("4/4", 4, 1, S to "ggggXgggggggXggg", K to "x-------x-------", F to "----x-------x---")),
            g("bembe", "6/8 bell (bembé)", WORLD, 100, "The standard 12-pulse bell over two dotted-quarter beats; kick on each beat.",
                DrumGrid.of("6/8", 6, 1, B to "x-x-xx-x-x-x", K to "x-----x-----")),
        )
    }
}

/**
 * A small synthesized kit for hearing grooves and rudiments: kick (falling sine), snare (body + noise),
 * hats and ride (high-passed noise, metallic partials), crash, toms, cross-stick, bell. Deterministic.
 */
object DrumKit {
    data class Hit(val frame: Int, val voice: DrumVoice, val hit: DrumHit)

    /** Seconds of silence before the first hit (room for flams and drags). */
    const val LEAD_IN = 0.1

    fun render(hits: List<Hit>, length: Int, rate: Int): FloatArray {
        val lead = (LEAD_IN * rate).toInt()
        val out = FloatArray(lead + length + rate)
        var seed = 0x2545F491L
        fun noise(): Double {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            return ((seed ushr 33).toDouble() / (1L shl 31) - 0.5) * 2
        }
        // An open hat rings until the next hi-hat hit closes it.
        val hatHits = hits.filter { it.voice == DrumVoice.HAT || it.voice == DrumVoice.HAT_FOOT }.map { it.frame }.sorted()
        for (h in hits) {
            val at = lead + h.frame
            val g = h.hit.gain
            val seconds = when (h.voice) {
                DrumVoice.CRASH -> 1.6; DrumVoice.RIDE -> 0.9; DrumVoice.KICK -> 0.35; DrumVoice.HAT -> if (h.hit == DrumHit.OPEN) 0.45 else 0.06
                DrumVoice.TOM_HI, DrumVoice.TOM_MID, DrumVoice.TOM_FLOOR -> 0.45; else -> 0.2
            }
            var len = (seconds * rate).toInt()
            if (h.voice == DrumVoice.HAT && h.hit == DrumHit.OPEN) hatHits.firstOrNull { it > h.frame }?.let { len = minOf(len, it - h.frame) }
            var prev = 0.0
            for (i in 0 until len) {
                val k = at + i
                if (k !in out.indices) break
                val t = i.toDouble() / rate
                val n = noise(); val hp = n - prev; prev = n
                val v = when (h.voice) {
                    DrumVoice.KICK -> sin(2 * PI * (50 * t + 70 * 0.04 * (1 - exp(-t / 0.04)))) * exp(-t / 0.12) * 1.1
                    DrumVoice.SNARE -> 0.55 * hp * exp(-t / 0.05) + 0.6 * sin(2 * PI * 185 * t) * exp(-t / 0.025)
                    DrumVoice.HAT -> 0.35 * hp * exp(-t / if (h.hit == DrumHit.OPEN) 0.18 else 0.015)
                    DrumVoice.HAT_FOOT -> 0.3 * hp * exp(-t / 0.012)
                    DrumVoice.RIDE -> 0.12 * hp * exp(-t / 0.4) + 0.12 * (sin(2 * PI * 3150 * t) + sin(2 * PI * 4610 * t) + sin(2 * PI * 5230 * t)) * exp(-t / 0.3)
                    DrumVoice.CRASH -> 0.4 * hp * exp(-t / 0.6)
                    DrumVoice.BELL -> 0.35 * (sin(2 * PI * 800 * t) + 0.6 * sin(2 * PI * 1220 * t)) * exp(-t / 0.08)
                    DrumVoice.CROSS_STICK -> 0.6 * sin(2 * PI * 1700 * t) * exp(-t / 0.008) + 0.2 * hp * exp(-t / 0.01)
                    DrumVoice.TOM_HI -> sin(2 * PI * (200 * t + 60 * 0.05 * (1 - exp(-t / 0.05)))) * exp(-t / 0.15)
                    DrumVoice.TOM_MID -> sin(2 * PI * (150 * t + 50 * 0.05 * (1 - exp(-t / 0.05)))) * exp(-t / 0.17)
                    DrumVoice.TOM_FLOOR -> sin(2 * PI * (100 * t + 40 * 0.05 * (1 - exp(-t / 0.05)))) * exp(-t / 0.2)
                }
                out[k] += (g * v).toFloat()
            }
        }
        val peak = out.maxOf { abs(it) }
        if (peak > 0.9f) for (i in out.indices) out[i] *= 0.9f / peak
        return out
    }
}

/**
 * A drum chart scrap (plan §3.1; Christopher, 22 Oct 2026: "Also drummer charts as a fragment"), kept as
 * drum tab text so it reads anywhere:
 * ```
 * {time: 4/4}
 * {steps: 4}
 * HH |x-x-x-x-x-x-x-x-|
 * SD |----X-------X---|
 * BD |x-------x-------|
 * ```
 * A drum written again, or a blank line, starts the next line of music, which carries on from the bars
 * above. `-` or `.` is a rest; `{swing: 58}` swings the off steps like the metronome. The tempo is the
 * scrap's BPM. [problems] lists what couldn't be read; the rest of the chart still works.
 */
data class DrumChart(val grid: DrumGrid, val swing: Double = 0.5, val problems: List<String> = emptyList()) {
    /** Plays like a groove: the same render and lit step. */
    val groove: Groove get() = Groove("chart", "Drum chart", "", 100, grid, "", swing)

    /** The chart as text, a few bars to a line of music, drums in kit order. */
    fun text(): String = buildString {
        append("{time: ${grid.meter}}\n{steps: ${grid.stepsPerBeat}}\n")
        if (swing > 0.5) append("{swing: ${(swing * 100).roundToInt()}}\n")
        val voices = DrumVoice.entries.filter { it in grid.rows }
        val perLine = (32 / grid.stepsPerBar).coerceAtLeast(1)
        for (first in 0 until grid.bars step perLine) {
            if (first > 0) append('\n')
            for (v in voices) {
                val row = grid.rows.getValue(v)
                append(v.tab).append(" |")
                for (b in first until minOf(grid.bars, first + perLine)) {
                    for (i in 0 until grid.stepsPerBar) append(row[b * grid.stepsPerBar + i]?.char ?: '-')
                    append('|')
                }
                append('\n')
            }
        }
    }

    companion object {
        const val MAX_BARS = 16
        /** Steps a beat can be split into: quarters, eighths, triplets, sixteenths, sextuplets, thirty-seconds. */
        val STEPS = listOf(1, 2, 3, 4, 6, 8)
        /** Meters a chart offers: every beat the same length, so a step is the same everywhere in the bar. */
        val METERS: List<TimeSignature> = listOf("4/4", "3/4", "2/4", "5/4", "6/4", "7/4", "6/8", "9/8", "12/8").map(TimeSignature::parse)

        /** [Claude] A new chart: one bar of 4/4 in sixteenths, hi-hat, snare and kick, nothing written yet. */
        fun starter() = DrumChart(DrumGrid.of("4/4", 4, 1, DrumVoice.HAT to "-".repeat(16), DrumVoice.SNARE to "-".repeat(16), DrumVoice.KICK to "-".repeat(16)))

        // The } is escaped: desktop Java takes a bare one, Android's ICU regex refuses it (every drum chart crashed).
        private val DIRECTIVE = Regex("""^\{\s*(\w+)\s*:\s*(.*?)\s*\}$""")
        private val ROW = Regex("""^([A-Za-z0-9]{2})\s*\|(.*)$""")

        fun parse(text: String): DrumChart {
            val problems = ArrayList<String>()
            var meter = TimeSignature.parse("4/4")
            var steps: Int? = null
            var swing = 0.5
            val lines = ArrayList<MutableMap<DrumVoice, MutableList<DrumHit?>>>()
            var current: MutableMap<DrumVoice, MutableList<DrumHit?>>? = null
            for ((n, raw) in text.lines().withIndex()) {
                val line = raw.trim()
                if (line.isEmpty()) { current = null; continue }
                DIRECTIVE.matchEntire(line)?.let { m ->
                    val (key, value) = m.destructured
                    when (key.lowercase()) {
                        "time" -> runCatching { TimeSignature.parse(value) }.getOrNull()?.takeIf { it in METERS }?.let { meter = it }
                            ?: problems.add("Line ${n + 1}: $value isn't a meter a drum chart takes (${METERS.joinToString()}).")
                        "steps" -> value.toIntOrNull()?.takeIf { it in STEPS }?.let { steps = it }
                            ?: problems.add("Line ${n + 1}: steps to a beat is one of ${STEPS.joinToString()}.")
                        "swing" -> value.removeSuffix("%").toIntOrNull()?.takeIf { it in 50..75 }?.let { swing = it / 100.0 }
                            ?: problems.add("Line ${n + 1}: swing is 50 to 75.")
                        else -> problems.add("Line ${n + 1}: {$key} isn't something a drum chart uses.")
                    }
                    return@let
                } ?: ROW.matchEntire(line)?.let { m ->
                    val (tab, cells) = m.destructured
                    val v = DrumVoice.entries.firstOrNull { it.tab.equals(tab, ignoreCase = true) }
                    if (v == null) { problems.add("Line ${n + 1}: $tab isn't a drum (${DrumVoice.entries.joinToString(" ") { it.tab }})."); return@let }
                    val c = current?.takeIf { v !in it } ?: mutableMapOf<DrumVoice, MutableList<DrumHit?>>().also { lines += it; current = it }
                    val bad = HashSet<Char>()
                    c[v] = cells.filter { it != '|' && !it.isWhitespace() }.map { ch ->
                        if (ch == '-' || ch == '.') null else DrumHit.of(ch) ?: run { bad += ch; null }
                    }.toMutableList()
                    if (bad.isNotEmpty()) problems.add("Line ${n + 1}: ${bad.joinToString(" ")} isn't a drum mark (x X g o, - for a rest); read as a rest.")
                } ?: problems.add("Line ${n + 1}: a drum line starts with the drum, like HH |x-x-|.")
            }
            // Steps to a beat, when not given: the first bar of the first drum line.
            val spb = steps ?: run {
                val first = text.lines().firstNotNullOfOrNull { ROW.matchEntire(it.trim()) }?.groupValues?.get(2)
                    ?.split('|')?.map { s -> s.count { !it.isWhitespace() } }?.firstOrNull { it > 0 }
                first?.takeIf { it % meter.beatsPerBar == 0 }?.div(meter.beatsPerBar)?.takeIf { it in STEPS } ?: 4
            }
            val per = meter.beatsPerBar * spb
            // Each line of music is as long as its longest drum, in whole bars; a drum missing from a line rests.
            val voices = lines.flatMap { it.keys }.toSet()
            val rows = voices.associateWith { ArrayList<DrumHit?>() }
            for (l in lines) {
                val len = ((l.values.maxOf { it.size } + per - 1) / per).coerceAtLeast(1) * per
                for (v in voices) {
                    val r = l[v].orEmpty()
                    rows.getValue(v).addAll(r + List(len - r.size) { null })
                }
            }
            var bars = ((rows.values.maxOfOrNull { it.size } ?: per) / per).coerceAtLeast(1)
            if (bars > MAX_BARS) { problems.add("A chart is up to $MAX_BARS bars; the bars after that are left out."); bars = MAX_BARS }
            val grid = DrumGrid(meter, spb, bars, rows.mapValues { (_, r) -> (r + List(per * bars) { null }).take(per * bars) })
            return DrumChart(grid, swing, problems)
        }
    }
}
