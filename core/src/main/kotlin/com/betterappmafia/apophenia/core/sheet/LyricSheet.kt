package com.betterappmafia.apophenia.core.sheet

import com.betterappmafia.apophenia.core.chord.ChordDialect
import com.betterappmafia.apophenia.core.chord.ChordParser

/**
 * Lyrics with chords over the words, stored as a subset of ChordPro (chordpro.org, v6): a chord in
 * square brackets sits over the syllable after it (`[Dm7]Salt on the [G]window`), a `{comment: Verse 1}`
 * line (or `{c: ...}`, or the section directives `{start_of_verse: ...}`) is a section heading, and
 * every other line is words. Storing ChordPro means the M7 export is the text itself.
 *
 * Chord names are kept exactly as typed, in ASCII (plan §6.5); [ChordText.display] draws ♭ and ♯.
 * Checking and transposing them needs the chord-symbol parser (plan §6.2), which reads the same text.
 */
object LyricSheet {
    sealed interface Line {
        data class Section(val label: String) : Line
        /** Words with chords: each part is a chord (or null) and the words it sits over. */
        data class Words(val parts: List<Part>) : Line {
            val text: String get() = parts.joinToString("") { it.text }
            val chords: List<String> get() = parts.mapNotNull { it.chord }
        }
        data object Blank : Line
    }

    data class Part(val chord: String?, val text: String)

    /** The heading directives this reads; anything else in braces is kept as a heading with its own text. */
    private val SECTION_KEYS = mapOf(
        "c" to null, "comment" to null, "ci" to null, "comment_italic" to null, "cb" to null, "comment_box" to null,
        "soc" to "Chorus", "start_of_chorus" to "Chorus", "sov" to "Verse", "start_of_verse" to "Verse",
        "sob" to "Bridge", "start_of_bridge" to "Bridge", "start_of_part" to null,
    )
    private val END_KEYS = setOf("eoc", "end_of_chorus", "eov", "end_of_verse", "eob", "end_of_bridge", "end_of_part")

    fun parse(source: String): List<Line> = source.replace("\r\n", "\n").split('\n').mapNotNull(::parseLine)

    private fun parseLine(raw: String): Line? {
        val line = raw.trimEnd()
        if (line.isBlank()) return Line.Blank
        val t = line.trim()
        if (t.startsWith("{") && t.endsWith("}")) {
            val inner = t.substring(1, t.length - 1)
            val key = inner.substringBefore(':').trim().lowercase()
            val value = if (':' in inner) inner.substringAfter(':').trim() else ""
            if (key in END_KEYS) return null
            if (key in SECTION_KEYS) return Line.Section(value.ifEmpty { SECTION_KEYS[key] ?: "" })
            return null // other directives (title, key, tempo) are metadata, not lines
        }
        val parts = ArrayList<Part>()
        var chord: String? = null
        val text = StringBuilder()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            val close = if (c == '[') line.indexOf(']', i + 1) else -1
            if (close > i) {
                if (chord != null || text.isNotEmpty()) parts += Part(chord, text.toString())
                chord = line.substring(i + 1, close).trim().ifEmpty { null }
                text.clear()
                i = close + 1
            } else {
                text.append(c); i++
            }
        }
        if (chord != null || text.isNotEmpty()) parts += Part(chord, text.toString())
        return Line.Words(parts)
    }

    /**
     * Two monospace rows for a line: chords above, words below, each chord starting over the
     * character it was placed before. When a chord is wider than the words under it, the words are
     * padded so the next chord never touches it (one space between chords at least).
     */
    fun chordsOverWords(line: Line.Words, display: (String) -> String = ChordText::display): Pair<String, String> {
        val top = StringBuilder()
        val bottom = StringBuilder()
        for (p in line.parts) {
            if (p.chord != null) {
                // The previous chord runs past this point: push the words along, one space past it.
                if (top.isNotEmpty() && top.length + 1 > bottom.length) {
                    val need = top.length + 1 - bottom.length
                    val midWord = bottom.isNotEmpty() && bottom.last() != ' ' && p.text.firstOrNull()?.isLetter() == true
                    bottom.append(if (midWord) "-" + " ".repeat(need - 1) else " ".repeat(need))
                }
                while (top.length < bottom.length) top.append(' ')
                top.append(display(p.chord))
            }
            bottom.append(p.text)
        }
        return top.toString().trimEnd() to bottom.toString().trimEnd()
    }

    /** The words only, for previews and search. */
    fun plainText(source: String): String = parse(source).joinToString("\n") {
        when (it) {
            is Line.Section -> it.label
            is Line.Words -> it.text
            Line.Blank -> ""
        }
    }.trim()

    /**
     * A Board square's look at the sheet: the chords over the words, as the editor shows them. [titled] false means
     * the square's title is already the first line of words, so that line is left out unless it has chords over it
     * (the square showed "Down by the water" twice, and no chord).
     */
    fun glimpse(source: String, titled: Boolean, display: (String) -> String = ChordText::display): String {
        val lines = parse(source)
        val first = lines.indexOfFirst { it is Line.Words && it.text.isNotBlank() }
        return lines.withIndex().flatMap { (i, line) ->
            when (line) {
                is Line.Section -> listOf(line.label)
                Line.Blank -> listOf("")
                is Line.Words -> when {
                    line.chords.isNotEmpty() -> chordsOverWords(line, display).toList()
                    i == first && !titled -> emptyList()
                    else -> listOf(line.text)
                }
            }
        }.joinToString("\n").trim('\n')
    }

    /** The first line of words, for a title when the scrap has none. */
    fun firstWords(source: String): String? =
        parse(source).firstNotNullOfOrNull { (it as? Line.Words)?.text?.trim()?.takeIf(String::isNotEmpty) }
}

/** Chord names as typed (ASCII) shown with real accidentals. */
object ChordText {
    /**
     * Whether [chord] reads as a chord symbol in [dialect] ([ChordParser]). Text it can't read is still kept as typed
     * (music-core skill), only flagged.
     */
    fun readable(chord: String, dialect: ChordDialect = ChordDialect()): Boolean = ChordParser.parse(chord, dialect).readable

    /**
     * `#` becomes ♯; `b` becomes ♭ where it is an accidental: right after a note letter (Bb, Ebm7,
     * C/Bb) or before a degree number (7b9, b5). Words inside chord names (sus, dim, add, maj, alt)
     * contain no b, so nothing else changes.
     */
    fun display(chord: String): String {
        val out = StringBuilder(chord.length)
        for (i in chord.indices) {
            val c = chord[i]
            out.append(
                when {
                    c == '#' -> '♯'
                    c == 'b' && ((i > 0 && chord[i - 1] in 'A'..'G') || (i + 1 < chord.length && chord[i + 1].isDigit())) -> '♭'
                    else -> c
                }
            )
        }
        return out.toString()
    }
}
