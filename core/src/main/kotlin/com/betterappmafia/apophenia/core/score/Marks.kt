package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.rhythm.NoteValue

/*
 * What C4's ADD ▾ puts on a score besides pitches and rhythm (MuseScore 4's palettes, Dorico's Notations and
 * Text panels: `docs/research/notation-pianoroll-tab-editors-2026-10-21.md`). Each carries its stored token
 * (ASCII) and its SMuFL code points; accent, marcato and staccato are the note [Effect]s the tab view already
 * shows, so they live there and nowhere else.
 */

enum class MarkGroup { ARTICULATION, ORNAMENT }

/**
 * A mark on a whole event. [above]/[below] are the SMuFL glyphs for each side of the note (the same glyph
 * when it has no below form); arpeggios have none (drawn as a line beside the chord).
 */
enum class Mark(val token: String, val label: String, val group: MarkGroup, val above: Int, val below: Int) {
    STACCATISSIMO("staccatissimo", "Staccatissimo", MarkGroup.ARTICULATION, 0xE4A6, 0xE4A7),
    TENUTO("tenuto", "Tenuto", MarkGroup.ARTICULATION, 0xE4A4, 0xE4A5),
    PORTATO("portato", "Portato (tenuto-staccato)", MarkGroup.ARTICULATION, 0xE4B2, 0xE4B3),
    STRESS("stress", "Stress", MarkGroup.ARTICULATION, 0xE4B6, 0xE4B7),
    UNSTRESS("unstress", "Unstress", MarkGroup.ARTICULATION, 0xE4B8, 0xE4B9),
    SOFT_ACCENT("soft-accent", "Soft accent", MarkGroup.ARTICULATION, 0xED40, 0xED41),
    UP_BOW("up-bow", "Up bow", MarkGroup.ARTICULATION, 0xE612, 0xE612),
    DOWN_BOW("down-bow", "Down bow", MarkGroup.ARTICULATION, 0xE610, 0xE610),
    OPEN("open", "Open", MarkGroup.ARTICULATION, 0xE5E7, 0xE5E7),
    STOPPED("stopped", "Stopped / muted (+)", MarkGroup.ARTICULATION, 0xE5E5, 0xE5E5),
    SNAP_PIZZ("snap-pizz", "Snap pizzicato", MarkGroup.ARTICULATION, 0xE631, 0xE630),
    FERMATA("fermata", "Fermata", MarkGroup.ARTICULATION, 0xE4C0, 0xE4C1),
    FERMATA_SHORT("fermata-short", "Short fermata", MarkGroup.ARTICULATION, 0xE4C4, 0xE4C5),
    FERMATA_LONG("fermata-long", "Long fermata", MarkGroup.ARTICULATION, 0xE4C6, 0xE4C7),
    BREATH("breath", "Breath mark", MarkGroup.ARTICULATION, 0xE4CE, 0xE4CE),
    CAESURA("caesura", "Caesura", MarkGroup.ARTICULATION, 0xE4D1, 0xE4D1),
    TRILL("trill", "Trill", MarkGroup.ORNAMENT, 0xE566, 0xE566),
    UPPER_MORDENT("upper-mordent", "Upper mordent (pralltriller)", MarkGroup.ORNAMENT, 0xE56C, 0xE56C),
    MORDENT("mordent", "Lower mordent", MarkGroup.ORNAMENT, 0xE56D, 0xE56D),
    TURN("turn", "Turn", MarkGroup.ORNAMENT, 0xE567, 0xE567),
    INVERTED_TURN("inverted-turn", "Inverted turn", MarkGroup.ORNAMENT, 0xE568, 0xE568),
    TURN_SLASH("turn-slash", "Turn with slash", MarkGroup.ORNAMENT, 0xE569, 0xE569),
    ARPEGGIO("arpeggio", "Arpeggio", MarkGroup.ORNAMENT, 0, 0),
    ARPEGGIO_UP("arpeggio-up", "Arpeggio up", MarkGroup.ORNAMENT, 0xE634, 0xE634),
    ARPEGGIO_DOWN("arpeggio-down", "Arpeggio down", MarkGroup.ORNAMENT, 0xE635, 0xE635);

    /** Fermatas, breaths and caesuras sit above the staff whatever the stem does. */
    val alwaysAbove: Boolean get() = this in setOf(FERMATA, FERMATA_SHORT, FERMATA_LONG, BREATH, CAESURA) || group == MarkGroup.ORNAMENT
    val arpeggio: Boolean get() = this == ARPEGGIO || this == ARPEGGIO_UP || this == ARPEGGIO_DOWN

    companion object {
        fun of(token: String) = entries.firstOrNull { it.token == token } ?: throw IllegalArgumentException("unknown mark '$token'")
    }
}

/** Dynamics as written; each letter is a SMuFL dynamic glyph (p m f r s z n). */
enum class Dynamic {
    PPPP, PPP, PP, P, MP, MF, F, FF, FFF, FFFF, FP, PF, SF, SFZ, SFF, SFFZ, SFP, SFPP, RF, RFZ, FZ, N;

    val token: String get() = name.lowercase()
    val glyphs: String get() = buildString {
        for (c in token) appendCodePoint(when (c) { 'p' -> 0xE520; 'm' -> 0xE521; 'f' -> 0xE522; 'r' -> 0xE523; 's' -> 0xE524; 'z' -> 0xE525; else -> 0xE526 })
    }

    companion object {
        fun of(token: String) = entries.firstOrNull { it.token == token } ?: throw IllegalArgumentException("unknown dynamic '$token'")
    }
}

/**
 * A line that starts on an event and covers that event and the ones after it in the same voice, across bars
 * (stored as a count of events, at least 2, so a line can never be left open).
 */
enum class Span(val token: String, val label: String) {
    SLUR("slur", "Slur"), CRESC("cresc", "Crescendo hairpin"), DIM("dim", "Diminuendo hairpin"),
    OTTAVA_UP("8va", "8va (octave up)"), OTTAVA_DOWN("8vb", "8vb (octave down)"),
    QUINDICESIMA_UP("15ma", "15ma (two octaves up)"), QUINDICESIMA_DOWN("15mb", "15mb (two octaves down)"),
    PEDAL("ped", "Sustain pedal"), GLISS("gliss", "Glissando"), TRILL_LINE("trill-line", "Trill line");

    /** Octaves the notes under it are written below their sounding place (8va → 1, 8vb → −1). */
    val octaves: Int get() = when (this) { OTTAVA_UP -> 1; OTTAVA_DOWN -> -1; QUINDICESIMA_UP -> 2; QUINDICESIMA_DOWN -> -2; else -> 0 }

    companion object {
        fun of(token: String) = entries.firstOrNull { it.token == token } ?: throw IllegalArgumentException("unknown line '$token'")
    }
}

/** Staff text: expression (italic, below: dolce), technique (above: pizz.), plain staff text (above). */
enum class TextKind(val token: String, val label: String) {
    EXPRESSION("e", "Expression"), TECHNIQUE("t", "Technique"), STAFF("s", "Staff text");

    companion object {
        fun of(token: String) = entries.firstOrNull { it.token == token } ?: throw IllegalArgumentException("unknown text kind '$token'")
    }
}

data class StaffText(val kind: TextKind, val text: String)

/** One syllable under an event: [hyphen] joins it to the next; [extend] draws a melisma line after it. */
data class Lyric(val verse: Int, val text: String, val hyphen: Boolean = false, val extend: Boolean = false) {
    init { require(verse in 1..9) { "verses 1 to 9" } }
}

/** A grace note (or grace chord) before the event, or after it ([after]); [slash] makes it an acciaccatura. */
data class Grace(val value: NoteValue, val notes: List<Note>, val slash: Boolean = true, val after: Boolean = false) {
    init { require(notes.isNotEmpty()) { "a grace note has a pitch" } }
}

/** Noteheads other than the normal oval, each as SMuFL black / half / whole. */
enum class Head(val token: String, val label: String, val black: Int, val half: Int, val whole: Int) {
    NORMAL("normal", "Normal", 0xE0A4, 0xE0A3, 0xE0A2),
    X("x", "Cross (x)", 0xE0A9, 0xE0A8, 0xE0A7),
    CIRCLE_X("circle-x", "Circled cross", 0xE0B3, 0xE0B3, 0xE0B3),
    DIAMOND("diamond", "Diamond", 0xE0DB, 0xE0DD, 0xE0D8),
    TRIANGLE("triangle", "Triangle", 0xE0BE, 0xE0BD, 0xE0BB),
    SLASH("slash", "Slash", 0xE101, 0xE103, 0xE102),
    SQUARE("square", "Square", 0xE0B9, 0xE0B8, 0xE0B8);

    companion object {
        fun of(token: String) = entries.firstOrNull { it.token == token } ?: throw IllegalArgumentException("unknown notehead '$token'")
    }
}

/** The barline at a bar's end; REPEAT is an end-repeat (:|). */
enum class Barline(val token: String, val label: String) {
    SINGLE("single", "Single"), DOUBLE("double", "Double"), FINAL("final", "Final"), DASHED("dashed", "Dashed"), REPEAT("repeat", "End repeat :|");

    companion object {
        fun of(token: String) = entries.firstOrNull { it.token == token } ?: throw IllegalArgumentException("unknown barline '$token'")
    }
}

/** Navigation marks: segno and coda at a bar's start, the rest at its end. */
enum class Jump(val token: String, val text: String, val atStart: Boolean) {
    SEGNO("segno", "Segno", true), CODA("coda", "Coda", true),
    TO_CODA("to-coda", "To Coda", false), FINE("fine", "Fine", false),
    DC("dc", "D.C.", false), DC_AL_FINE("dc-al-fine", "D.C. al Fine", false), DC_AL_CODA("dc-al-coda", "D.C. al Coda", false),
    DS("ds", "D.S.", false), DS_AL_FINE("ds-al-fine", "D.S. al Fine", false), DS_AL_CODA("ds-al-coda", "D.S. al Coda", false);

    companion object {
        fun of(token: String) = entries.firstOrNull { it.token == token } ?: throw IllegalArgumentException("unknown jump '$token'")
    }
}

/** Free text in the stored form: anything outside a plain set is %XX of its UTF-8 bytes, so it stays ASCII. */
internal object Esc {
    private const val PLAIN = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789.'!?()*#/-_&^<>~"

    fun encode(s: String): String = buildString {
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = (b.toInt() and 0xFF)
            if (c < 128 && c.toChar() in PLAIN) append(c.toChar()) else append('%').append("%02X".format(c))
        }
    }

    fun decode(s: String): String {
        if ('%' !in s) return s
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            if (s[i] == '%' && i + 2 < s.length) { out.write(s.substring(i + 1, i + 3).toInt(16)); i += 3 }
            else { out.write(s[i].code); i++ }
        }
        return out.toString("UTF-8")
    }
}
