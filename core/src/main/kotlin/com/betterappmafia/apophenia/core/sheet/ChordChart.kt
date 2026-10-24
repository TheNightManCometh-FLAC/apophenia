package com.betterappmafia.apophenia.core.sheet

import com.betterappmafia.apophenia.core.rhythm.TimeSignature

/**
 * A chord chart: bars of chords, one slot per beat (C4: "one slash per beat"). Stored as text so it
 * stays readable and exportable:
 *
 * ```
 * {time: 4/4}
 * {c: Chorus}
 * | F / C / | G / Dm7 / |
 * ```
 *
 * Bars sit between `|`; each space-separated token is one beat; `/` holds the chord before it; `%`
 * repeats the whole previous bar; `N.C.` is no chord. `{time: 6/8}` sets the meter from that line on
 * (6/8 has two beats a bar). Chord names are kept as typed (ASCII, plan §6.5).
 */
object ChordChart {
    data class Bar(val beats: List<String>, val meter: TimeSignature) {
        /** The chord sounding on each beat: `/` resolved to the chord before it, empty before the first chord. */
        fun sounding(previous: String?): List<String?> {
            var current = previous
            return beats.map { b -> if (b != HOLD) current = b; current }
        }
        val fits: Boolean get() = beats.size == meter.beatsPerBar
    }

    data class Section(val label: String?, val bars: List<Bar>)

    data class Chart(val sections: List<Section>, val problems: List<String>) {
        val bars: List<Bar> get() = sections.flatMap { it.bars }
    }

    const val HOLD = "/"
    const val REPEAT = "%"
    val DEFAULT_METER: TimeSignature = TimeSignature.of(4, 4)

    fun parse(source: String): Chart {
        val sections = ArrayList<Section>()
        val problems = ArrayList<String>()
        var label: String? = null
        var bars = ArrayList<Bar>()
        var meter = DEFAULT_METER
        var barNo = 0
        fun close() {
            if (bars.isNotEmpty() || label != null) sections += Section(label, bars)
            bars = ArrayList()
        }
        source.replace("\r\n", "\n").split('\n').forEachIndexed { lineNo, raw ->
            val t = raw.trim()
            if (t.isEmpty()) return@forEachIndexed
            if (t.startsWith("{") && t.endsWith("}")) {
                val inner = t.substring(1, t.length - 1)
                val key = inner.substringBefore(':').trim().lowercase()
                val value = inner.substringAfter(':', "").trim()
                when (key) {
                    "time" -> meter = runCatching { TimeSignature.parse(value) }.getOrElse {
                        problems += "Line ${lineNo + 1}: \"$value\" isn't a meter (try 4/4, 6/8, 2+2+3/8)."; meter
                    }
                    "c", "comment", "start_of_part", "sov", "start_of_verse", "soc", "start_of_chorus", "sob", "start_of_bridge" -> {
                        close(); label = value.ifEmpty { null }
                    }
                }
                return@forEachIndexed
            }
            for (cell in t.split('|')) {
                val beats = cell.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                if (beats.isEmpty()) continue
                barNo++
                val bar = if (beats == listOf(REPEAT)) {
                    val prev = bars.lastOrNull() ?: sections.lastOrNull()?.bars?.lastOrNull()
                    if (prev == null) { problems += "Bar $barNo: % repeats a bar, but there is none before it."; Bar(beats, meter) }
                    else prev.copy(meter = meter)
                } else Bar(beats, meter)
                if (!bar.fits) problems += "Bar $barNo has ${bar.beats.size} beat${if (bar.beats.size == 1) "" else "s"}; $meter has ${meter.beatsPerBar}."
                bars += bar
            }
        }
        close()
        return Chart(sections, problems)
    }

    /** The first bars as one line, for a window glimpse. */
    fun preview(source: String, bars: Int = 4): String =
        parse(source).bars.take(bars).joinToString(" | ", "| ", " |") { b -> b.beats.joinToString(" ") { ChordText.display(it) } }
}
