package com.betterappmafia.apophenia.core.sheet

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.score.Score
import com.betterappmafia.apophenia.core.score.TabText

/**
 * Text exports (plan §11: ChordPro, plain-text tab). ChordPro follows chordpro.org version 6: `{title}`, `{key}`,
 * `{time}`, and a chord chart as a `{start_of_grid}` block where `.` holds the chord for a beat.
 */
object Exports {
    /** ChordPro's `{key}`: the tonic, with m for a minor (Aeolian) key; null for other modes, which ChordPro can't name. */
    fun chordProKey(key: KeyMode?): String? = when (key?.scaleId) {
        "major" -> key.tonic.toString()
        "aeolian" -> key.tonic.toString() + "m"
        else -> null
    }

    private fun header(title: String, key: KeyMode?, bpm: Double?) = buildString {
        if (title.isNotBlank()) append("{title: ").append(title).append("}\n")
        chordProKey(key)?.let { append("{key: ").append(it).append("}\n") }
        bpm?.let { append("{tempo: ").append(if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()).append("}\n") }
    }

    /** A lyric scrap (already stored as ChordPro) with its title, key and tempo directives added if it lacks them. */
    fun lyrics(title: String, body: String, key: KeyMode? = null, bpm: Double? = null): String {
        val has = { d: String -> Regex("\\{\\s*$d\\s*:", RegexOption.IGNORE_CASE).containsMatchIn(body) }
        val head = header(if (has("title|t")) "" else title, if (has("key")) null else key, if (has("tempo")) null else bpm)
        return head + (if (head.isNotEmpty()) "\n" else "") + body.trimEnd() + "\n"
    }

    /** A chord chart as ChordPro grids, one per section, four bars a line, `{time}` where the meter changes. */
    fun chart(title: String, body: String, key: KeyMode? = null, bpm: Double? = null): String {
        val c = ChordChart.parse(body)
        val sb = StringBuilder(header(title, key, bpm))
        var meter = ChordChart.DEFAULT_METER
        var first = true
        for (s in c.sections) {
            sb.append('\n')
            if (s.bars.firstOrNull()?.meter.let { it != null && (it != meter || first) }) { meter = s.bars.first().meter; sb.append("{time: ").append(meter).append("}\n") }
            first = false
            sb.append(if (s.label != null) "{start_of_grid: label=\"${s.label}\"}\n" else "{start_of_grid}\n")
            s.bars.chunked(4).forEach { line ->
                sb.append("|")
                for (bar in line) {
                    sb.append(' ').append(bar.beats.joinToString(" ") { if (it == ChordChart.HOLD) "." else it }).append(" |")
                }
                sb.append('\n')
            }
            sb.append("{end_of_grid}\n")
        }
        return sb.toString()
    }

    /** Plain-text tab of every fretted part, [perLine] bars to a line. */
    fun tab(score: Score, perLine: Int = 4): String = buildString {
        if (score.title.isNotBlank()) append(score.title).append("\n\n")
        append("Tempo ").append(score.tempo.bpm.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }).append(" · ").append(score.meter).append('\n')
        for (p in score.parts.filter { it.instrument != null }) {
            val inst = p.instrument!!
            append('\n').append(p.name.ifBlank { "Guitar" }).append(" · ").append(inst.tuning.name)
            if (inst.capo > 0) append(" · capo ").append(inst.capo)
            append("\n\n")
            for (from in 0 until p.bars.size step perLine) {
                TabText.render(p, from, perLine).forEach { append(it).append('\n') }
                append('\n')
            }
        }
    }
}
