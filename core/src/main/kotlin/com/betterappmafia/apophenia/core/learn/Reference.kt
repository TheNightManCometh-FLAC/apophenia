package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.catalog.ChordDef
import com.betterappmafia.apophenia.core.catalog.Chords
import com.betterappmafia.apophenia.core.catalog.ScaleDef
import com.betterappmafia.apophenia.core.catalog.Scales
import com.betterappmafia.apophenia.core.catalog.SpellingKind
import com.betterappmafia.apophenia.core.chord.Chord
import com.betterappmafia.apophenia.core.chord.ChordParser
import com.betterappmafia.apophenia.core.chord.ChordRoot
import com.betterappmafia.apophenia.core.chord.QualityMapping
import com.betterappmafia.apophenia.core.degree.DegreeToken
import com.betterappmafia.apophenia.core.interval.Interval
import com.betterappmafia.apophenia.core.interval.Quality
import com.betterappmafia.apophenia.core.interval.minus
import com.betterappmafia.apophenia.core.interval.plus
import com.betterappmafia.apophenia.core.pitch.Accidental
import com.betterappmafia.apophenia.core.pitch.Letter
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.spelling.Spelling

/*
 * The Reference (plan §8): a page for every interval, scale/mode and chord in the catalogs, generated on any root.
 * Everything here is computed from the catalogs (spelling, steps, modes, chords on each degree, chords and scales with
 * the same notes, scales containing a chord's tones); hand-written commentary arrives page by page later.
 */

enum class RefKind(val label: String) { INTERVAL("Intervals"), SCALE("Scales and modes"), CHORD("Chords"), PROGRESSION("Named progressions"), TUNING("Guitar tunings") }

/** One Reference page: [id] is the interval's short name (m3, A11) or the catalog id. */
data class RefItem(val kind: RefKind, val id: String, val title: String, val also: List<String>, val family: String)

/** A search result: the page and, when the query named one, the root to show it on. */
data class RefHit(val item: RefItem, val root: PitchName? = null)

object Reference {
    /** Every interval from the unison to the double octave in the usual qualities (no diminished unison, no doubles). */
    val intervals: List<Interval> = (1..15).flatMap { n ->
        val perfect = (n - 1) % 7 + 1 in setOf(1, 4, 5)
        val qualities = if (perfect) listOf(-1, 0, 1) else listOf(-2, -1, 0, 1)
        qualities.filterNot { n == 1 && it < 0 }.map { Interval(n, Interval.referenceSemitones(n) + it) }
    }

    val items: List<RefItem> by lazy { intervals.map { RefItem(RefKind.INTERVAL, it.toString(), intervalName(it).replaceFirstChar(Char::uppercase), listOf(it.toString()), if (it.isCompound) "compound" else "simple") } +
        Scales.all.map { RefItem(RefKind.SCALE, it.id, it.names.first(), it.names.drop(1), it.family) } +
        Chords.all.map { RefItem(RefKind.CHORD, it.id, chordTitle(it), it.symbols, it.family) } +
        Progressions.all.map { RefItem(RefKind.PROGRESSION, it.id, it.name, it.also, it.family) } +
        TuningReference.all.map { RefItem(RefKind.TUNING, it.tuning.id, it.tuning.name, listOf(it.tuning.strings.joinToString(" ") { s -> s.name.toString() }), it.family) } }

    fun item(kind: RefKind, id: String): RefItem? = items.firstOrNull { it.kind == kind && it.id == id }

    /** Chord names in words, by catalog id. */
    val CHORD_NAMES = mapOf(
        "maj" to "Major triad", "m" to "Minor triad", "dim" to "Diminished triad", "aug" to "Augmented triad",
        "sus2" to "Suspended second", "sus4" to "Suspended fourth", "5" to "Power chord", "6" to "Major sixth", "m6" to "Minor sixth",
        "69" to "Six-nine", "m69" to "Minor six-nine", "maj7" to "Major seventh", "7" to "Dominant seventh", "m7" to "Minor seventh",
        "mMaj7" to "Minor-major seventh", "m7b5" to "Half-diminished seventh", "dim7" to "Diminished seventh", "7#5" to "Augmented seventh",
        "maj7#5" to "Augmented major seventh", "7b5" to "Dominant seventh, flat five", "7sus4" to "Dominant seventh, suspended fourth",
        "9" to "Dominant ninth", "maj9" to "Major ninth", "m9" to "Minor ninth", "mMaj9" to "Minor-major ninth", "11" to "Dominant eleventh",
        "m11" to "Minor eleventh", "maj7#11" to "Major seventh, sharp eleven", "13" to "Dominant thirteenth", "maj13" to "Major thirteenth",
        "m13" to "Minor thirteenth", "7b9" to "Dominant seventh, flat nine", "7#9" to "Dominant seventh, sharp nine",
        "7#11" to "Dominant seventh, sharp eleven", "7b13" to "Dominant seventh, flat thirteen", "7alt" to "Altered dominant",
        "add9" to "Added ninth", "madd9" to "Minor added ninth", "add11" to "Added eleventh", "add2" to "Added second", "add4" to "Added fourth",
    )

    private fun chordTitle(c: ChordDef) = CHORD_NAMES[c.id] ?: c.symbols.first()

    private val ORDINALS = listOf("", "unison", "second", "third", "fourth", "fifth", "sixth", "seventh", "octave", "ninth", "tenth", "eleventh", "twelfth", "thirteenth", "fourteenth", "double octave")

    fun intervalName(i: Interval): String {
        val q = i.quality
        val word = when (q.kind) {
            Quality.Kind.DIMINISHED -> if (q.count > 1) "doubly diminished" else "diminished"
            Quality.Kind.MINOR -> "minor"
            Quality.Kind.PERFECT -> "perfect"
            Quality.Kind.MAJOR -> "major"
            Quality.Kind.AUGMENTED -> if (q.count > 1) "doubly augmented" else "augmented"
        }
        return "$word ${ORDINALS.getOrElse(i.number) { "${i.number}th" }}"
    }

    /**
     * Search: a chord symbol (E7#9) opens that chord on its root; a root and a scale name (D dorian) that scale; an
     * interval (m3, A4) that interval; anything else matches names and symbols.
     */
    fun find(query: String): List<RefHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<RefHit>()
        runCatching { Interval.parse(q) }.getOrNull()?.let { i -> items.firstOrNull { it.kind == RefKind.INTERVAL && it.id == i.toString() }?.let { out += RefHit(it) } }
        ChordParser.parse(q).chord?.let { c ->
            val root = (c.root as? ChordRoot.Name)?.name
            val id = c.quality
            if (root != null && id != null && c.bass == null && c.poly == null) item(RefKind.CHORD, id)?.let { out += RefHit(it, root) }
        }
        val words = q.split(Regex("\\s+"), limit = 2)
        if (words.size == 2) runCatching { PitchName.parse(words[0]) }.getOrNull()?.let { root ->
            val name = words[1].lowercase()
            items.filter { it.kind == RefKind.SCALE && (listOf(it.title) + it.also).any { n -> n.lowercase() == name || n.lowercase().startsWith(name) } }
                .forEach { out += RefHit(it, root) }
        }
        val low = q.lowercase()
        items.filter { (listOf(it.title, it.id) + it.also).any { n -> n.lowercase().contains(low) } }.forEach { out += RefHit(it) }
        return out.distinct()
    }

    /** The roots a page can be shown on: every letter with a flat, natural or sharp (the 21 of a page's ROOT menu). */
    val roots: List<PitchName> = Letter.entries.flatMap { l -> listOf(PitchName(l, -1), PitchName(l, 0), PitchName(l, 1)) }

    /** [p] in ASCII storage form, as the chord parser reads it. */
    fun ascii(p: PitchName) = p.letter.name + Accidental.format(p.alter)
}

// ---------------------------------------------------------------------------------------------------------- intervals

data class IntervalPage(
    val interval: Interval,
    val name: String,
    val semitones: Int,
    val inversion: Interval,
    /** Other intervals with the same number of semitones (A4 and d5). */
    val enharmonic: List<Interval>,
    /** A 5-limit just ratio for the simple interval, its size in cents, and a note when sources give another. */
    val just: JustRatio?,
    val above: PitchName?,
    val below: PitchName?,
)

data class JustRatio(val ratio: String, val cents: Double, val note: String? = null)

object IntervalPages {
    private val JUST = mapOf(
        "P1" to JustRatio("1:1", 0.0), "m2" to JustRatio("16:15", 111.73), "M2" to JustRatio("9:8", 203.91, "10:9 (182.40 ¢) is the minor whole tone."),
        "m3" to JustRatio("6:5", 315.64), "M3" to JustRatio("5:4", 386.31), "P4" to JustRatio("4:3", 498.04),
        "A4" to JustRatio("45:32", 590.22, "7:5 (582.51 ¢) in 7-limit tuning."), "d5" to JustRatio("64:45", 609.78, "10:7 (617.49 ¢) in 7-limit tuning."),
        "P5" to JustRatio("3:2", 701.96), "m6" to JustRatio("8:5", 813.69), "M6" to JustRatio("5:3", 884.36),
        "m7" to JustRatio("16:9", 996.09, "9:5 (1017.60 ¢) and the harmonic 7:4 (968.83 ¢) are also used."),
        "M7" to JustRatio("15:8", 1088.27), "P8" to JustRatio("2:1", 1200.0),
    )

    fun page(i: Interval, root: PitchName): IntervalPage {
        val simple = i.simple
        val inv = if (simple.number == 8) Interval(1, 0) else Interval(9 - simple.number, 12 - simple.semitones)
        return IntervalPage(
            interval = i,
            name = Reference.intervalName(i),
            semitones = i.semitones,
            inversion = inv,
            enharmonic = Reference.intervals.filter { it != i && it.semitones == i.semitones },
            just = if (i.isCompound) null else JUST[i.toString()],
            above = runCatching { root + i }.getOrNull(),
            below = runCatching { root - i }.getOrNull(),
        )
    }
}

// ------------------------------------------------------------------------------------------------------------- scales

/** A chord built in thirds on one degree of a scale: the catalog chord it makes, or null when none matches. */
data class DegreeChord(val degree: Int, val root: PitchName, val notes: List<PitchName>, val chord: ChordDef?) {
    val symbol: String? get() = chord?.let { Reference.ascii(root) + it.symbols.first().removePrefix("C") }
}

data class ScalePage(
    val scale: ScaleDef,
    val root: PitchName,
    /** The spelling used (a root that would need triple accidentals is respelled: G♯ major → A♭). */
    val notes: List<PitchName>,
    val respelledFrom: PitchName?,
    /** Steps between neighbours, closing back to the octave, in semitones. */
    val steps: List<Int>,
    /** The parent scale and its root when this is a mode (D Dorian → C major). */
    val parent: Pair<ScaleDef, PitchName>?,
    /** Every mode of the same parent on the roots that share these notes, this one included. */
    val modes: List<Pair<ScaleDef, PitchName>>,
    val triads: List<DegreeChord>,
    val sevenths: List<DegreeChord>,
    /** Other catalog scales with exactly these pitch classes, on the root that gives them. */
    val sameNotes: List<Pair<ScaleDef, PitchName>>,
)

object ScalePages {
    fun page(s: ScaleDef, root: PitchName): ScalePage {
        val set = Spelling.spell(root, s.degrees)
        val r = set.root
        val semis = s.semitones
        val steps = semis.indices.map { k -> if (k + 1 < semis.size) semis[k + 1] - semis[k] else 12 - semis[k] }
        val parentId = s.modeOf ?: s.id.takeIf { s.modeIndex == 1 }
        val parentDef = parentId?.let(Scales::byId)
        val parentRoot = parentDef?.let { p -> runCatching { r - p.degrees[(s.modeIndex ?: 1) - 1].interval }.getOrNull() }
        val modes = if (parentDef != null && parentRoot != null) Scales.all.filter { it.id == parentDef.id || it.modeOf == parentDef.id }.mapNotNull { m ->
            runCatching { m to Spelling.spell(parentRoot + parentDef.degrees[(m.modeIndex ?: 1) - 1].interval, m.degrees).root }.getOrNull()
        } else emptyList()
        val heptatonic = s.spellingKind == SpellingKind.HEPTATONIC
        fun stack(size: Int) = if (!heptatonic) emptyList() else (0 until 7).map { k ->
            val tones = (0 until size).map { j -> set.notes[(k + 2 * j) % 7] }
            val rel = tones.map { Math.floorMod(it.pitchClass - tones[0].pitchClass, 12) }
            DegreeChord(k + 1, tones[0], tones, Chords.all.firstOrNull { c -> c.degrees.size == size && c.semitones.map { Math.floorMod(it, 12) } == rel })
        }
        val pcs = set.notes.map { it.pitchClass }.toSet()
        val same = Scales.all.flatMap { other ->
            set.notes.mapNotNull { n ->
                if (other.id == s.id && n == r) return@mapNotNull null
                val otherPcs = other.semitones.map { Math.floorMod(n.pitchClass + it, 12) }.toSet()
                if (otherPcs == pcs && other.degrees.size == s.degrees.size) other to n else null
            }
        }.filterNot { (d, n) -> modes.any { it.first.id == d.id && it.second.pitchClass == n.pitchClass } }
        return ScalePage(s, r, set.notes, set.respelledFrom, steps,
            if (s.modeOf != null && parentDef != null && parentRoot != null) parentDef to parentRoot else null,
            modes, stack(3), stack(4), same)
    }

    /** Steps in the usual letters: H (half), W (whole), and the semitone count for larger gaps (3 for a minor third). */
    fun stepName(semitones: Int) = when (semitones) { 1 -> "H"; 2 -> "W"; else -> "$semitones" }
}

// ------------------------------------------------------------------------------------------------------------- chords

data class Inversion(val bass: PitchName, val name: String, val figures: String, val symbol: String)

data class ChordPage(
    val def: ChordDef,
    val root: PitchName,
    val chord: Chord,
    val notes: List<PitchName>,
    val respelledFrom: PitchName?,
    /** Every written form on this root, the app default first. */
    val symbols: List<String>,
    val inversions: List<Inversion>,
    /** Other catalog chords with the same pitch classes, on the root that gives them (C6 = Am7). */
    val sameNotes: List<Pair<ChordDef, PitchName>>,
    /** Catalog scales on the same root that contain every chord tone. */
    val scales: List<ScaleDef>,
)

object ChordPages {
    /** The chord as the music core stores it: catalog quality, MusicXML kind and degree edits. */
    fun chord(def: ChordDef, root: PitchName): Chord {
        val e = QualityMapping.encode(def.id)
        return Chord(ChordRoot.Name(root), e.kind, e.degrees, quality = def.id, kindText = e.kindText)
    }

    private val FIGURES_TRIAD = listOf("root position" to "5/3", "1st inversion" to "6", "2nd inversion" to "6/4")
    private val FIGURES_SEVENTH = listOf("root position" to "7", "1st inversion" to "6/5", "2nd inversion" to "4/3", "3rd inversion" to "4/2")

    fun page(def: ChordDef, root: PitchName): ChordPage {
        val set = Spelling.spell(root, def.degrees)
        val r = set.root
        val base = def.symbols.map { Reference.ascii(r) + it.removePrefix("C") }
        val figures = when {
            def.degrees.size == 3 -> FIGURES_TRIAD
            def.degrees.size == 4 && def.degrees.any { it.number == 7 } -> FIGURES_SEVENTH
            else -> null
        }
        val inversions = set.notes.mapIndexed { k, n ->
            val (name, fig) = figures?.getOrNull(k) ?: ((if (k == 0) "root position" else "${def.degrees[k]} in the bass") to "")
            Inversion(n, name, fig, if (k == 0) base.first() else base.first() + "/" + Reference.ascii(n))
        }
        val pcs = set.notes.map { it.pitchClass }.toSet()
        val same = Chords.all.flatMap { other ->
            set.notes.mapNotNull { n ->
                if (other.id == def.id && n == r) return@mapNotNull null
                val otherPcs = other.semitones.map { Math.floorMod(n.pitchClass + it, 12) }.toSet()
                if (otherPcs == pcs && other.degrees.size == def.degrees.size) other to n else null
            }
        }
        val scales = Scales.all.filter { s -> def.semitones.all { Math.floorMod(it, 12) in s.semitones } }
        return ChordPage(def, r, chord(def, r), set.notes, set.respelledFrom, base, inversions, same, scales)
    }

    /** Tones that may be left out of a voicing, in the catalog's order; the rest are essential. */
    fun optional(def: ChordDef): List<DegreeToken> = def.omitOrder
}
