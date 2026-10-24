package com.betterappmafia.apophenia.core.chord

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.spelling.Spelling

/** How chord symbols are drawn (plan §6.2; `docs/reference/music-core-and-theory-design.md` §2.2). */
enum class ChordStyle(val label: String, val note: String) {
    POP("Pop", "Cm7 · Cmaj7 · Cm7♭5 · C7(♭9,♯11)"),
    JAZZ("Jazz", "C−7 · CΔ7 · Cø7 · alterations stacked"),
    CLASSICAL("Classical / analysis", "ROMAN NUMERALS IN THE KEY · V⁶₅"),
    PLAIN("Plain text", "ASCII, AS EXPORTED");

    companion object {
        fun parse(token: String?) = entries.firstOrNull { it.name.equals(token?.trim(), ignoreCase = true) } ?: POP
    }
}

/**
 * A chord laid out for drawing: [root] and [quality] at full size, [sup] raised (the extension, or a numeral's
 * quality sign), [alterations] after it (stacked in a column when [stacked], else in parentheses), [figures] a
 * numeral's inversion figures stacked top to bottom (V⁶₅), the slash [bass] at full size, and a polychord's upper
 * chord [over] a horizontal line. [text] is the same on one line, for places that can't typeset.
 */
data class Typeset(
    val root: String,
    val quality: String = "",
    val sup: String = "",
    val alterations: List<String> = emptyList(),
    val stacked: Boolean = false,
    val figures: List<String> = emptyList(),
    val bass: String? = null,
    val over: Typeset? = null,
) {
    val text: String
        get() = (over?.let { it.text + " over " } ?: "") + root + quality + sup +
            (if (alterations.isEmpty()) "" else if (alterations.size == 1 && !stacked) alterations[0] else alterations.joinToString(",", "(", ")")) +
            figures.joinToString("/") + (bass?.let { "/$it" } ?: "")
}

/**
 * The style profiles. Pop: m, maj7, m7♭5, dim7, aug, several alterations in parentheses. Jazz: −, Δ, ø, °, +,
 * alterations stacked. Classical: a Roman numeral for the root's degree in the key (lower case when the third is
 * minor, ♭ or ♯ before it off the scale), the quality sign raised (° ø +, M7 for a major seventh), the inversion
 * from the bass as stacked figures (6, ⁶₄; 7, ⁶₅, ⁴₃, ⁴₂), N⁶ and It⁺⁶ / Fr⁺⁶ / Ger⁺⁶; without a key it falls back
 * to pop. Plain: ASCII, as exported. Fixed rules in every style: the 7 is written when an extension is altered,
 * alterations use ♭ and ♯ (never − and +), 7sus4 not sus7, add9 has no 7th.
 */
object ChordDisplay {
    /** Typed text read in [dialect], spelled in [key] and laid out in [style]; null when it isn't a chord symbol. */
    fun of(text: String, style: ChordStyle, key: KeyMode?, dialect: ChordDialect = ChordDialect()): Typeset? =
        ChordParser.parse(text, dialect).chord?.let { typeset(ChordSpelling.inKey(it, key), style, key, dialect.names) }

    fun typeset(chord: Chord, style: ChordStyle, key: KeyMode? = null, names: ChordDialect.Names = ChordDialect.Names.ENGLISH): Typeset {
        if (chord.isNoChord) return Typeset("N.C.")
        val root = (chord.root as? ChordRoot.Name)?.name ?: return Typeset(chord.typed ?: chord.kind.xml)
        val over = chord.poly?.let { typeset(it, style, key, names) }
        if (style == ChordStyle.PLAIN) return Typeset(ChordSpelling.symbol(chord.copy(poly = null), names), over = over?.let { Typeset(it.text) })
        if (style == ChordStyle.CLASSICAL && key != null) numeral(chord, root, key)?.let { return it.copy(over = over) }
        val jazz = style == ChordStyle.JAZZ
        val (quality, sup) = qualityOf(chord, jazz)
        val alterations = chord.degrees.mapNotNull { e ->
            if (chord.kindText == "7alt" && !e.printed) null
            else when (e.type) {
                DegreeType.SUBTRACT -> "no${e.value}"
                DegreeType.ALTER -> acc(e.alter) + e.value
                DegreeType.ADD -> when {
                    (sup.contains("sus") || sup == "6/9") && e.alter == 0 && e.value in setOf(7, 9, 13) -> null
                    e.alter == 0 && chord.kind.degrees?.none { it.number == 7 } != false && e.value != 7 -> "add${e.value}"
                    e.value == 7 && e.alter == 1 -> "maj7"
                    e.alter == 0 -> "(${e.value})"
                    else -> acc(e.alter) + e.value
                }
            }
        }
        return Typeset(name(root, names), quality, sup, alterations, stacked = jazz && alterations.size > 1, bass = chord.bass?.let { name(it, names) }, over = over)
    }

    private fun acc(a: Int) = when (a) { -2 -> "𝄫"; -1 -> "♭"; 1 -> "♯"; 2 -> "𝄪"; else -> "" }

    /** A note name for display: the dialect's name with real accidentals. */
    fun name(p: PitchName, names: ChordDialect.Names): String {
        val s = ChordParser.name(p, names)
        return when (names) {
            ChordDialect.Names.GERMAN -> s.replace("#", "♯").replace(Regex("(?<=[A-H])b"), "♭")
            else -> s.replace("##", "𝄪").replace("#", "♯").replace(Regex("(?<=[A-Gaeio])bb$"), "𝄫").replace(Regex("(?<=[A-Gaeio])b$"), "♭")
        }
    }

    /** The quality at full size and the raised part, for pop or jazz. */
    private fun qualityOf(c: Chord, jazz: Boolean): Pair<String, String> {
        val m = if (jazz) "−" else "m"
        // A sus chord with its 7th (and 9th, 13th) added reads as 7sus4, 9sus4, 13sus4.
        if (c.kind == ChordKind.SUSPENDED_FOURTH || c.kind == ChordKind.SUSPENDED_SECOND) {
            val n = if (c.kind == ChordKind.SUSPENDED_FOURTH) "4" else "2"
            val adds = c.degrees.filter { it.type == DegreeType.ADD && it.alter == 0 }.map { it.value }
            val ext = when { 13 in adds && 7 in adds -> "13"; 9 in adds && 7 in adds -> "9"; 7 in adds -> "7"; else -> "" }
            return "" to "${ext}sus$n"
        }
        if (c.kindText == "7alt") return "" to "7alt"
        return when (c.kind) {
            ChordKind.MAJOR -> "" to ""
            ChordKind.MINOR -> m to ""
            ChordKind.AUGMENTED -> (if (jazz) "+" else "aug") to ""
            ChordKind.DIMINISHED -> (if (jazz) "°" else "dim") to ""
            ChordKind.DOMINANT -> "" to "7"
            ChordKind.MAJOR_SEVENTH -> "" to (if (jazz) "Δ7" else "maj7")
            ChordKind.MINOR_SEVENTH -> m to "7"
            ChordKind.DIMINISHED_SEVENTH -> (if (jazz) "°" else "dim") to "7"
            ChordKind.AUGMENTED_SEVENTH -> if (jazz) "+" to "7" else "" to "7♯5"
            ChordKind.HALF_DIMINISHED -> if (jazz) "ø" to "7" else m to "7♭5"
            ChordKind.MAJOR_MINOR -> m to (if (jazz) "Δ7" else "maj7")
            ChordKind.MAJOR_SIXTH -> "" to (if (c.degrees == listOf(DegreeEdit.add(9))) "6/9" else "6")
            ChordKind.MINOR_SIXTH -> m to (if (c.degrees == listOf(DegreeEdit.add(9))) "6/9" else "6")
            ChordKind.DOMINANT_NINTH -> "" to "9"
            ChordKind.MAJOR_NINTH -> "" to (if (jazz) "Δ9" else "maj9")
            ChordKind.MINOR_NINTH -> m to "9"
            ChordKind.DOMINANT_11TH -> "" to "11"
            ChordKind.MAJOR_11TH -> "" to (if (jazz) "Δ11" else "maj11")
            ChordKind.MINOR_11TH -> m to "11"
            ChordKind.DOMINANT_13TH -> "" to "13"
            ChordKind.MAJOR_13TH -> "" to (if (jazz) "Δ13" else "maj13")
            ChordKind.MINOR_13TH -> m to "13"
            ChordKind.POWER -> "" to "5"
            ChordKind.PEDAL -> "" to "ped"
            else -> "" to c.kind.xml
        }
    }

    private val ROMAN = listOf("I", "II", "III", "IV", "V", "VI", "VII")

    /** The chord as a Roman numeral in [key], or null when its root isn't a letter name. */
    private fun numeral(c: Chord, root: PitchName, key: KeyMode): Typeset? {
        val scale = runCatching { Spelling.spell(key.tonic, key.scale.degrees).notes }.getOrNull() ?: return null
        val degree = Math.floorMod(root.letter.ordinal - key.tonic.letter.ordinal, 7)
        val diatonic = scale.firstOrNull { it.letter == root.letter } ?: return null
        val alter = (Math.floorMod(root.pitchClass - diatonic.pitchClass + 6, 12) - 6)
        val tokens = c.degreeTokens()
        val semis = tokens.map { it.interval.semitones % 12 }.toSet()
        val minorThird = 3 in semis && 4 !in semis
        val seventh = tokens.any { it.number == 7 }
        val prefix = acc(alter)
        // The functional sixths by name.
        when (c.kind) {
            ChordKind.NEAPOLITAN -> return Typeset("N", figures = listOf("6"))
            ChordKind.ITALIAN -> return Typeset("It", sup = "+6")
            ChordKind.FRENCH -> return Typeset("Fr", sup = "+6")
            ChordKind.GERMAN -> return Typeset("Ger", sup = "+6")
            else -> {}
        }
        val numeral = ROMAN[degree].let { if (minorThird) it.lowercase() else it }
        val sign = when (c.kind) {
            ChordKind.DIMINISHED, ChordKind.DIMINISHED_SEVENTH -> "°"
            ChordKind.HALF_DIMINISHED -> "ø"
            ChordKind.AUGMENTED, ChordKind.AUGMENTED_SEVENTH -> "+"
            ChordKind.MAJOR_SEVENTH, ChordKind.MAJOR_MINOR, ChordKind.MAJOR_NINTH, ChordKind.MAJOR_11TH, ChordKind.MAJOR_13TH -> "M"
            else -> ""
        }
        // Inversion from the bass: which chord tone it is.
        val inversion = c.bass?.let { b ->
            when (Math.floorMod(b.pitchClass - root.pitchClass, 12)) { 3, 4 -> 1; 6, 7, 8 -> 2; 9, 10, 11 -> if (seventh) 3 else -1; 0 -> 0; else -> -1 }
        } ?: 0
        val figures = when {
            inversion < 0 -> emptyList()
            seventh -> listOf(listOf("7"), listOf("6", "5"), listOf("4", "3"), listOf("4", "2"))[inversion]
            else -> listOf(emptyList(), listOf("6"), listOf("6", "4"))[inversion.coerceAtMost(2)]
        }
        val ext = tokens.filter { it.number > 7 }.map { acc(it.alter - DegreeEdit.dominantReference(it.number)) + it.number }
        return Typeset(prefix + numeral, sup = sign, alterations = ext, figures = figures, bass = if (inversion < 0) c.bass?.let { name(it, ChordDialect.Names.ENGLISH) } else null)
    }
}
