package com.betterappmafia.apophenia.core.chord

import com.betterappmafia.apophenia.core.chord.DegreeEdit.Companion.add
import com.betterappmafia.apophenia.core.chord.DegreeEdit.Companion.alter
import com.betterappmafia.apophenia.core.chord.DegreeEdit.Companion.subtract
import com.betterappmafia.apophenia.core.pitch.Letter
import com.betterappmafia.apophenia.core.pitch.PitchName

/**
 * How chord symbols are read (plan §6.2): [style] decides the symbols charts disagree on (pop or jazz/iReal Pro),
 * [names] the language of the note names.
 */
data class ChordDialect(val style: Style = Style.POP, val names: Names = Names.ENGLISH) {
    enum class Style(val label: String) { POP("Pop"), JAZZ("Jazz / iReal Pro") }
    enum class Names(val label: String) { ENGLISH("C D E F G A B"), GERMAN("German: H = B♮, B = B♭, Fis, Es…"), SOLFEGE("Do Re Mi Fa Sol La Si") }

    /** Stored form, e.g. `pop english`. */
    val token: String get() = "${style.name.lowercase()} ${names.name.lowercase()}"

    companion object {
        fun parse(token: String?): ChordDialect = runCatching {
            val (s, n) = token!!.trim().split(' ')
            ChordDialect(Style.valueOf(s.uppercase()), Names.valueOf(n.uppercase()))
        }.getOrDefault(ChordDialect())
    }
}

/** Another way the same text is read, and why. */
data class ChordAlternative(val chord: Chord, val why: String)

/**
 * The result of reading one chord symbol: the [chord] (null when it can't be read, with [problem] saying why), the
 * other readings when the symbol is ambiguous, and the text as split: [rootText], [rest] (quality, extensions,
 * alterations) and [bassText], ASCII, so the root and bass can be respelled without touching the rest.
 */
data class ChordParse(
    val text: String,
    val chord: Chord?,
    val alternatives: List<ChordAlternative> = emptyList(),
    val problem: String? = null,
    val rootText: String = "",
    val rest: String = "",
    val bassText: String? = null,
) {
    val readable: Boolean get() = chord != null
}

/**
 * The chord-symbol parser (music-core skill; `docs/research/music-core-reference-musicxml-advanced-theory-chord-corpus.md`,
 * part 3). Reads how people write chords into the MusicXML structure ([Chord]: root, kind, degree edits, bass, a
 * polychord above): Unicode or ASCII accidentals; the root in the dialect's names; the quality by longest match that
 * leaves a readable rest (so `C-9` is Cm9 and `C7-9` is C7♭9, `Comit3` isn't a diminished chord); then extensions,
 * alterations, add/no/omit/sus, bare or in parentheses with commas or spaces (`+9`/`-9` in alteration position are
 * ♯9/♭9); then the slash bass (`6/9` is never a slash chord); `D over C` and `D|C` are polychords; N.C., NC, n and N
 * are no chord. Ambiguous symbols read as the dialect says and carry the other reading: C2 (add9 in pop, sus2 in
 * jazz), C° (triad in pop, °7 in jazz), C7+ (7♯5, or maj7 in some Latin and European charts), Cmaj (triad, or maj7),
 * C11 (dominant 11th, or 9sus4), and a lowercase root (a typo for major, or minor). Text it can't read is kept and
 * flagged by the caller; it never throws.
 */
object ChordParser {
    private class Form(val kind: ChordKind, val degrees: List<DegreeEdit> = emptyList(), val kindText: String? = null)
    private class Quality(val token: String, val pop: Form, val jazz: Form = pop, val other: Form? = null, val why: String? = null)

    private fun f(kind: ChordKind, vararg d: DegreeEdit) = Form(kind, d.toList())
    private fun q(form: Form, vararg tokens: String) = tokens.map { Quality(it, form) }

    private val MAJ = f(ChordKind.MAJOR)
    private val MAJ7 = f(ChordKind.MAJOR_SEVENTH)
    private val DIM = f(ChordKind.DIMINISHED)
    private val DIM7 = f(ChordKind.DIMINISHED_SEVENTH)
    private val AUG7 = f(ChordKind.AUGMENTED_SEVENTH)
    private val ADD9 = f(ChordKind.MAJOR, add(9))
    private val SUS2 = f(ChordKind.SUSPENDED_SECOND)
    private val NINE_SUS = f(ChordKind.SUSPENDED_FOURTH, add(7), add(9))
    private val ALT = QualityMapping.encode("7alt").let { Form(it.kind, it.degrees, it.kindText) }

    private val QUALITIES: List<Quality> = (
        q(MAJ, "", "M", "major") +
            listOf("maj", "Maj", "ma").map { Quality(it, MAJ, other = MAJ7, why = "Cmaj sometimes means Cmaj7") } +
            q(f(ChordKind.MINOR), "m", "min", "mi", "-", "minor") +
            q(f(ChordKind.AUGMENTED), "+", "aug", "+5") +
            q(DIM, "dim") +
            listOf("°", "o", "º").map { Quality(it, DIM, DIM7, other = null, why = "C° is a diminished triad in some charts, °7 in many jazz charts") } +
            q(DIM7, "dim7", "°7", "o7", "º7") +
            q(f(ChordKind.HALF_DIMINISHED), "ø", "ø7", "Ø", "Ø7", "h", "h7", "m7b5", "m7-5", "-7b5", "-7-5", "min7b5", "mi7b5") +
            q(MAJ7, "maj7", "Maj7", "M7", "Δ7", "Δ", "^7", "^", "ma7", "j7", "7M") +
            q(f(ChordKind.DOMINANT), "7", "dom7", "dom") +
            q(f(ChordKind.MINOR_SEVENTH), "m7", "min7", "mi7", "-7") +
            q(f(ChordKind.MAJOR_MINOR), "m(maj7)", "mMaj7", "mmaj7", "mM7", "m^7", "-^7", "-Δ7", "mΔ7", "-Δ", "m#7", "mi(ma7)", "min(maj7)", "-maj7", "m(ma7)", "-(maj7)", "minmaj7") +
            q(f(ChordKind.MAJOR_SIXTH), "6", "M6", "maj6") +
            q(f(ChordKind.MINOR_SIXTH), "m6", "-6", "min6", "mi6") +
            q(f(ChordKind.MAJOR_SIXTH, add(9)), "6/9", "69", "6(9)", "6add9", "M6/9") +
            q(f(ChordKind.MINOR_SIXTH, add(9)), "m6/9", "m69", "-69", "-6/9", "min6/9") +
            q(f(ChordKind.DOMINANT_NINTH), "9") +
            q(f(ChordKind.MAJOR_NINTH), "maj9", "M9", "^9", "Δ9", "ma9") +
            q(f(ChordKind.MINOR_NINTH), "m9", "-9", "min9", "mi9") +
            listOf(Quality("11", f(ChordKind.DOMINANT_11TH), other = NINE_SUS, why = "C11 is often played as C9sus4")) +
            q(f(ChordKind.MAJOR_11TH), "maj11", "M11", "^11", "Δ11") +
            q(f(ChordKind.MINOR_11TH), "m11", "-11", "min11", "mi11") +
            q(f(ChordKind.DOMINANT_13TH), "13") +
            q(f(ChordKind.MAJOR_13TH), "maj13", "M13", "^13", "Δ13") +
            q(f(ChordKind.MINOR_13TH), "m13", "-13", "min13", "mi13") +
            q(f(ChordKind.SUSPENDED_FOURTH), "sus", "sus4", "4") +
            q(SUS2, "sus2") +
            listOf(Quality("2", ADD9, SUS2, other = null, why = "C2 is add9 in pop and worship charts, sus2 in iReal Pro")) +
            q(f(ChordKind.SUSPENDED_FOURTH, add(7)), "7sus", "7sus4") +
            q(f(ChordKind.SUSPENDED_SECOND, add(7)), "7sus2") +
            q(NINE_SUS, "9sus", "9sus4") +
            q(f(ChordKind.SUSPENDED_FOURTH, add(7), add(9), add(13)), "13sus", "13sus4") +
            q(f(ChordKind.POWER), "5") +
            q(ALT, "alt", "7alt") +
            listOf(Quality("7+", AUG7, other = MAJ7, why = "C7+ is 7♯5 in most charts, maj7 in some Latin and European ones")) +
            q(AUG7, "+7", "aug7")
        ).sortedByDescending { it.token.length }

    private val NO_CHORD = Regex("^(N\\.?C\\.?|n|N|-)$", RegexOption.IGNORE_CASE)
    private val MOD = Regex("^(add|no|omit|sus)?([b#+\\-])?(13|11|9|7|6|5|4|3|2)(st|nd|rd|th)?")
    private val MAJ_MOD = Regex("^(maj|ma|M|\\^|Δ|j)(13|11|9|7)")

    fun parse(text: String, dialect: ChordDialect = ChordDialect()): ChordParse {
        val s = normalize(text)
        if (s.isEmpty()) return ChordParse(text, null, problem = "Empty.")
        if (NO_CHORD.matches(s)) return ChordParse(text, Chord.NO_CHORD.copy(typed = text), rootText = s)
        // A polychord: the upper chord over the lower, written "D over C" or "D|C".
        val poly = Regex("\\s+over\\s+|\\|", RegexOption.IGNORE_CASE).split(s)
        if (poly.size == 2) {
            val upper = parse(poly[0], dialect); val lower = parse(poly[1], dialect)
            if (upper.chord == null || lower.chord == null || upper.chord.isNoChord || lower.chord.isNoChord)
                return ChordParse(text, null, problem = "A polychord needs a chord above and below.")
            return lower.copy(text = text, chord = lower.chord.copy(poly = upper.chord, typed = text), alternatives = emptyList())
        }
        val flat = s.replace(" ", "")
        val roots = readRoots(flat, dialect.names)
        if (roots.isEmpty()) return ChordParse(text, null, problem = "No root note at the start (${namesHint(dialect.names)}).")
        var failure: ChordParse? = null
        // Each way of reading the root (German "Esus4" is E sus4, not E♭ "us4"), longest first.
        for ((root, rootLen, lowercase) in roots) {
            var rest = flat.substring(rootLen)
            // The slash bass: a note name after the last slash (so 6/9 stays a sixth-ninth).
            var bass: PitchName? = null
            var bassText: String? = null
            val slash = rest.lastIndexOf('/')
            if (slash >= 0) {
                val after = rest.substring(slash + 1)
                val b = readRoots(after, dialect.names).firstOrNull { it.second == after.length }
                if (b != null) {
                    bass = b.first; bassText = after; rest = rest.substring(0, slash)
                } else if (!rest.substring(0, slash).endsWith("6") || !after.startsWith("9")) {
                    failure = failure ?: ChordParse(text, null, problem = "\"$after\" after the slash isn't a note.")
                    continue
                }
            }
            // Quality by longest match, as long as what follows it reads.
            for (quality in QUALITIES) {
                if (!rest.startsWith(quality.token)) continue
                // "7+" alone is the ambiguous chord; followed by a degree it is a 7th with a raised one (C7+9).
                if (quality.token == "7+" && rest != "7+") continue
                val form = if (dialect.style == ChordDialect.Style.JAZZ) quality.jazz else quality.pop
                val built = mods(form, rest.substring(quality.token.length)) ?: continue
                val chord = finish(Chord(ChordRoot.Name(root), built.kind, built.degrees, bass, typed = text, kindText = built.kindText ?: quality.token.ifEmpty { null }))
                val alternatives = buildList {
                    // The other reading of an ambiguous symbol, when nothing after it changed the picture.
                    if (quality.why != null && rest == quality.token) {
                        val other = quality.other ?: if (form === quality.pop) quality.jazz else quality.pop
                        if (other !== form) add(ChordAlternative(finish(chord.copy(kind = other.kind, degrees = other.degrees)), quality.why))
                    }
                    if (lowercase && rest.isEmpty()) add(ChordAlternative(finish(chord.copy(kind = ChordKind.MINOR)), "A lowercase root means minor in some charts"))
                }
                return ChordParse(text, chord, alternatives, rootText = flat.substring(0, rootLen), rest = rest, bassText = bassText)
            }
            failure = failure ?: ChordParse(text, null, problem = "Can't read \"$rest\" after the root.", rootText = flat.substring(0, rootLen), rest = rest, bassText = bassText)
        }
        return failure!!
    }

    /** ASCII accidentals, one kind of minus sign; spaces kept only where they separate words. */
    private fun normalize(t: String): String = t.trim()
        .replace("𝄫", "bb").replace("𝄪", "x").replace("♭", "b").replace("♯", "#").replace("♮", "")
        .replace("−", "-").replace("–", "-").replace("˚", "°").replace("∆", "Δ")

    /** The degrees after the quality; null when something there isn't a chord degree. */
    private fun mods(form: Form, after: String): Form? {
        var rest = after.replace("(", "").replace(")", "").replace(",", "")
        var kind = form.kind
        val edits = form.degrees.toMutableList()
        fun has(n: Int) = (kind.degrees ?: emptyList()).any { it.number == n } || edits.any { it.value == n && it.type != DegreeType.SUBTRACT }
        while (rest.isNotEmpty()) {
            if (rest.startsWith("alt")) { if (kind != ChordKind.DOMINANT) return null; edits += ALT.degrees; rest = rest.substring(3); continue }
            val mm = MAJ_MOD.find(rest)
            if (mm != null) {
                edits += add(7, 1)
                val n = mm.groupValues[2].toInt()
                if (n >= 9) edits += add(9)
                if (n == 11) edits += add(11)
                if (n == 13) edits += add(13)
                rest = rest.substring(mm.value.length)
                continue
            }
            if (rest.startsWith("sus")) {
                val n = if (rest.startsWith("sus2")) 2 else 4
                rest = rest.substring(if (rest.startsWith("sus2") || rest.startsWith("sus4")) 4 else 3)
                val (k, extra) = suspend(kind, n)
                kind = k; edits.removeAll { it.value == 3 }; edits.addAll(0, extra)
                continue
            }
            val m = MOD.find(rest) ?: return null
            val (word, acc, num) = m.destructured
            val n = num.toInt()
            val a = when (acc) { "b", "-" -> -1; "#", "+" -> 1; else -> 0 }
            when (word) {
                "no", "omit" -> { if (a != 0) return null; edits += subtract(n) }
                "sus" -> return null
                "add" -> edits += add(n, a)
                else -> when {
                    a == 0 && has(n) -> {}
                    a != 0 && (kind.degrees ?: emptyList()).any { it.number == n } -> edits += alter(n, a)
                    else -> edits += add(n, a)
                }
            }
            rest = rest.substring(m.value.length)
        }
        return Form(kind, edits, form.kindText)
    }

    /** A sus on a chord with a third: the third goes, the 2nd or 4th comes in, the 7th and extensions stay. */
    private fun suspend(kind: ChordKind, n: Int): Pair<ChordKind, List<DegreeEdit>> {
        val tokens = kind.degrees ?: return kind to emptyList()
        val target = if (n == 2) ChordKind.SUSPENDED_SECOND else ChordKind.SUSPENDED_FOURTH
        val extra = tokens.filter { it.number >= 6 && !(n == 4 && it.number == 11) }
            .map { add(it.number, it.alter - DegreeEdit.dominantReference(it.number)) }
        return target to extra
    }

    /** The canonical kind for edits that spell a kind of their own (C7♯5 is augmented-seventh), and the catalog quality. */
    private fun finish(c: Chord): Chord {
        val d = c.degrees
        val (kind, degrees) = when {
            c.kind == ChordKind.DOMINANT && d == listOf(alter(5, 1)) -> ChordKind.AUGMENTED_SEVENTH to emptyList()
            c.kind == ChordKind.MINOR_SEVENTH && d == listOf(alter(5, -1)) -> ChordKind.HALF_DIMINISHED to emptyList()
            c.kind == ChordKind.MAJOR && d == listOf(alter(5, 1)) -> ChordKind.AUGMENTED to emptyList()
            c.kind == ChordKind.MINOR && d == listOf(alter(5, -1)) -> ChordKind.DIMINISHED to emptyList()
            c.kind == ChordKind.DOMINANT && d == listOf(add(9)) -> ChordKind.DOMINANT_NINTH to emptyList()
            c.kind == ChordKind.MAJOR_SEVENTH && d == listOf(add(9)) -> ChordKind.MAJOR_NINTH to emptyList()
            c.kind == ChordKind.MINOR_SEVENTH && d == listOf(add(9)) -> ChordKind.MINOR_NINTH to emptyList()
            else -> c.kind to d
        }
        return c.copy(kind = kind, degrees = degrees, quality = QualityMapping.decode(kind, degrees))
    }

    private val GERMAN: List<Pair<String, PitchName>> = buildList {
        val base = listOf("C" to Letter.C, "D" to Letter.D, "E" to Letter.E, "F" to Letter.F, "G" to Letter.G, "A" to Letter.A, "H" to Letter.B)
        for ((name, letter) in base) {
            val flat = when (name) { "E" -> "Es"; "A" -> "As"; "H" -> "B"; else -> name + "es" }
            val flat2 = when (name) { "E" -> "Eses"; "A" -> "Ases"; "H" -> "Heses"; else -> name + "eses" }
            add(name + "isis" to PitchName(letter, 2)); add(name + "is" to PitchName(letter, 1))
            add(flat2 to PitchName(letter, -2)); add(flat to PitchName(letter, -1)); add(name to PitchName(letter, 0))
        }
        add("Asas" to PitchName(Letter.A, -2))
    }.sortedByDescending { it.first.length }

    private val SOLFEGE = listOf("Sol" to Letter.G, "So" to Letter.G, "Do" to Letter.C, "Re" to Letter.D, "Mi" to Letter.E, "Fa" to Letter.F, "La" to Letter.A, "Si" to Letter.B, "Ti" to Letter.B)

    private fun accidental(s: String, from: Int): Pair<Int, Int> = when {
        s.startsWith("bb", from) -> -2 to 2
        s.startsWith("##", from) -> 2 to 2
        s.startsWith("b", from) -> -1 to 1
        s.startsWith("#", from) -> 1 to 1
        s.startsWith("x", from) -> 2 to 1
        else -> 0 to 0
    }

    /**
     * The ways to read a root (or bass) at the start of [s], longest first: its name, how many characters it took,
     * and whether it was lowercase.
     */
    private fun readRoots(s: String, names: ChordDialect.Names): List<Triple<PitchName, Int, Boolean>> {
        if (s.isEmpty()) return emptyList()
        val out = ArrayList<Triple<PitchName, Int, Boolean>>()
        when (names) {
            ChordDialect.Names.ENGLISH -> {
                val c = s[0]
                val letter = c.uppercaseChar().takeIf { it in "ABCDEFG" }?.let { Letter.parse(it) } ?: return out
                val (alter, len) = accidental(s, 1)
                if (len > 0) out += Triple(PitchName(letter, alter), 1 + len, c.isLowerCase())
                out += Triple(PitchName(letter, 0), 1, c.isLowerCase())
            }
            ChordDialect.Names.GERMAN -> {
                val lower = s[0].isLowerCase()
                val up = s[0].uppercaseChar() + s.substring(1)
                for ((name, pitch) in GERMAN) if (up.startsWith(name)) {
                    // English accidentals after a German letter (F#, Hb) are common too; B takes none (it is B♭ already).
                    if (pitch.alter == 0 && name != "B") {
                        val (a, len) = accidental(up, name.length)
                        if (len > 0 && !up.startsWith("x", name.length)) out += Triple(PitchName(pitch.letter, a), name.length + len, lower)
                    }
                    out += Triple(pitch, name.length, lower)
                }
            }
            ChordDialect.Names.SOLFEGE -> {
                for ((name, letter) in SOLFEGE) if (s.startsWith(name, ignoreCase = true)) {
                    val (alter, len) = accidental(s, name.length)
                    if (len > 0) out += Triple(PitchName(letter, alter), name.length + len, false)
                    out += Triple(PitchName(letter, 0), name.length, false)
                }
            }
        }
        return out.sortedByDescending { it.second }
    }

    private fun namesHint(n: ChordDialect.Names) = when (n) {
        ChordDialect.Names.ENGLISH -> "A to G"
        ChordDialect.Names.GERMAN -> "C D E F G A H, B for B♭, Fis, Es…"
        ChordDialect.Names.SOLFEGE -> "Do Re Mi Fa Sol La Si"
    }

    /** [p] as the dialect writes it, ASCII: `Bb`, or `B` in German (and `H` for B♮, `Fis`), or `Sib`. */
    fun name(p: PitchName, names: ChordDialect.Names): String = when (names) {
        ChordDialect.Names.ENGLISH -> p.toString().replace("x", "##")
        ChordDialect.Names.GERMAN -> GERMAN.firstOrNull { it.second == p && it.first != "Asas" }?.first ?: p.toString()
        ChordDialect.Names.SOLFEGE -> SOLFEGE.first { it.second == p.letter && it.first != "So" && it.first != "Ti" }.first + when (p.alter) { -2 -> "bb"; -1 -> "b"; 1 -> "#"; 2 -> "##"; else -> "" }
    }
}
