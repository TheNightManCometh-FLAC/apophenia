package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.interval.plus
import com.betterappmafia.apophenia.core.pitch.PitchName
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

/*
 * The Course (plan §8; C4 'learn' Course tiles, 'cat' topics and 'lesson' page). Nine categories of topics, each topic a
 * few pages; every page is Explain → See → Hear → Try (on your guitar, bass, piano or staff, in any key) → Check by
 * ear → Related (Reference pages, Ear drills, your own material). Nothing is locked and there is no prescribed order:
 * the level dots (● to ●●●●●) only say how complex a page is. Organised after the design of Rick Beato's courses
 * (theory and ear together, a few things at a time); the text is Apophenia's own.
 */

data class CourseCategory(val id: String, val name: String, val topics: List<CourseTopic>) {
    val range: String get() = topics.map { it.level }.let { l -> if (l.isEmpty()) "" else Course.dots(l.min()) + (if (l.max() > l.min()) "–" + Course.dots(l.max()) else "") }
}

/** A topic: [pages] empty until it's written. */
data class CourseTopic(val id: String, val name: String, val note: String, val level: Int, val pages: List<CoursePage> = emptyList())

data class CoursePage(
    val id: String,
    val title: String,
    val level: Int,
    val explain: String,
    val see: CourseSee? = null,
    val hear: List<CourseSound> = emptyList(),
    /** Drawn on the instrument the reader picks, from a Reference item, starting on any key. */
    val tryIt: CourseTry? = null,
    val check: CourseCheck? = null,
    val related: List<CourseLink> = emptyList(),
    /** Where it appears in your own material: a Reference item to look for. */
    val yours: Pair<RefKind, String>? = null,
    /** The lesson walked through after [explain]: each step's words, then the picture they point at, with ▶ playing exactly that. */
    val steps: List<CourseStep> = emptyList(),
    /** IN SHORT: the few things to take away, after the steps. */
    val inShort: List<String> = emptyList(),
) {
    /** All the page's words, for checks and search. */
    val text: String get() = (listOf(explain) + steps.map { it.text } + inShort).joinToString(" ")
    /** Every picture on the page, in order. */
    val pictures: List<CourseSee> get() = steps.mapNotNull { it.see } + listOfNotNull(see)
}

/** One step of a lesson: a paragraph, then optionally a picture and sounds that go with it. */
data class CourseStep(val text: String, val see: CourseSee? = null, val hear: List<CourseSound> = emptyList())

/**
 * What a picture's ▶ plays, exactly as drawn: [chords] one after another (each a list of MIDI notes), [step] seconds
 * apart. [spread] plays each chord's notes upward first, then together, so you can hear what it's made of.
 */
data class Play(val chords: List<List<Int>>, val step: Double = 1.0, val spread: Boolean = false) {
    fun audio(): EarAudio {
        val out = ArrayList<com.betterappmafia.apophenia.core.sound.PlayNote>()
        var t = 0.0
        for (c in chords) {
            if (spread && c.size > 1) {
                c.sorted().forEachIndexed { i, m -> out += com.betterappmafia.apophenia.core.sound.PlayNote(t + i * 0.22, t + i * 0.22 + 0.5, m, RefSound.VELOCITY) }
                t += c.size * 0.22 + 0.15
            }
            c.forEach { m -> out += com.betterappmafia.apophenia.core.sound.PlayNote(t, t + step * 0.92, m, RefSound.VELOCITY) }
            t += step
        }
        return EarAudio(out)
    }
}

sealed interface CourseSee {
    /** A small table: [header] then [rows]; a row with a [play] entry plays on tap, and ▶ PLAY THE TABLE plays them all in order. */
    data class Table(val caption: String, val header: List<String>, val rows: List<List<String>>, val play: List<Play?> = emptyList()) : CourseSee
    /** Piano keys with [keys] lit and labelled; [Key.faint] ones are context, such as the scale around a chord. */
    data class Keys(val caption: String, val keys: List<Key>, val play: Play? = null) : CourseSee
    /**
     * Rows against columns, the [GridRow.lit] cells drawn filled: a chord stacked on a scale, a step pattern, voices
     * moving from chord to chord. A row with its own [GridRow.play] plays on tap.
     */
    data class Grid(val caption: String, val columns: List<String>, val rows: List<GridRow>, val play: Play? = null) : CourseSee
    /** A progression as a row of chords, each with its numeral, symbol, notes and job; tap one to hear it, ▶ plays them in order. */
    data class Chart(val caption: String, val chords: List<ChartChord>, val step: Double = 1.1) : CourseSee {
        val play: Play get() = Play(chords.map { it.midis }, step)
    }
    /** A few bars on a staff (chord symbols above, numerals or words below), with ▶ playing them. */
    data class Staff(val caption: String, val score: com.betterappmafia.apophenia.core.score.Score, val play: Play? = null) : CourseSee
    /** The page key's [scaleId] on a treble staff, with its key signature. */
    data class KeyStaff(val scaleId: String) : CourseSee
    /** The circle of fifths, majors outside and relative minors inside; tap a key to hear its scale and start TRY on it. */
    data object Circle : CourseSee
    /** A small score drawn by the notation view (and tab, for a fretted part), one [labels] entry per part, with ▶ to play it. */
    data class Score(val caption: String, val score: com.betterappmafia.apophenia.core.score.Score, val labels: List<String>) : CourseSee
    /** Bars written on a percussion staff, each with ▶ to hear it after a count-in at [bpm]: [rows] in [meter], then [extra] in their own meters. */
    data class Rhythm(val caption: String, val meter: com.betterappmafia.apophenia.core.rhythm.TimeSignature, val bpm: Int, val rows: List<Pair<String, List<RNote>>>, val extra: List<Triple<String, com.betterappmafia.apophenia.core.rhythm.TimeSignature, List<RNote>>> = emptyList()) : CourseSee {
        val bars: List<Triple<String, com.betterappmafia.apophenia.core.rhythm.TimeSignature, List<RNote>>> get() = rows.map { Triple(it.first, meter, it.second) } + extra
    }
}

data class Key(val midi: Int, val label: String, val faint: Boolean = false)
data class GridRow(val label: String, val cells: List<String>, val lit: Set<Int>, val play: Play? = null)
data class ChartChord(val numeral: String, val symbol: String, val notes: String, val midis: List<Int>, val tag: String = "")

/** A sound on the page. [make] gets the key's root (MIDI) and a source of randomness for questions. */
class CourseSound(val label: String, val make: (root: Int, random: Random) -> EarAudio)

/** Try it: [kind]/[id] drawn from [root] (a note name; the reader can change it unless [fixed]). */
data class CourseTry(val prompt: String, val kind: RefKind, val id: String, val root: String = "C", val fixed: Boolean = false)

/** One answer of a check: what's played when it's the right one, and what to say after. */
class CourseOption(val label: String, val explain: String, val make: (root: Int, random: Random) -> EarAudio)

/** Check by ear: one of [options] is played (from a root in [roots]); you pick which. */
class CourseCheck(val question: String, val options: List<CourseOption>, val roots: IntRange = 48..64)

data class CourseLink(val label: String, val target: CourseTarget)

sealed interface CourseTarget {
    data class Ref(val kind: RefKind, val id: String, val root: String = "C") : CourseTarget
    data class Ear(val drill: String) : CourseTarget
    data class Page(val id: String) : CourseTarget
}

object Course {
    val categories: List<CourseCategory> by lazy { CourseCatalog.categories }

    /** Every written page, in course order. */
    val pages: List<CoursePage> by lazy { categories.flatMap { c -> c.topics.flatMap { it.pages } } }

    fun category(id: String) = categories.firstOrNull { it.id == id }
    fun page(id: String) = pages.firstOrNull { it.id == id }
    fun topicOf(pageId: String) = categories.flatMap { it.topics }.firstOrNull { t -> t.pages.any { it.id == pageId } }
    fun categoryOf(pageId: String) = categories.firstOrNull { c -> c.topics.any { t -> t.pages.any { it.id == pageId } } }
    fun previous(pageId: String) = pages.indexOfFirst { it.id == pageId }.let { if (it > 0) pages[it - 1] else null }
    fun next(pageId: String) = pages.indexOfFirst { it.id == pageId }.let { if (it in 0 until pages.size - 1) pages[it + 1] else null }

    /** The circle of fifths clockwise from C: major keys and their relative minors. */
    val circleMajor: List<String> get() = CourseScales.CIRCLE_MAJOR
    val circleMinor: List<String> get() = CourseScales.CIRCLE_MINOR

    /**
     * The picture on a topic's line in the contents: the first drawing in its pages, so the list shows what the topic
     * really teaches. A table is words, so it's used only when the topic has no drawing at all.
     */
    fun cover(topic: CourseTopic): CourseSee? = topic.pages.flatMap { it.pictures }.let { ps -> ps.firstOrNull { it !is CourseSee.Table } ?: ps.firstOrNull() }

    /** A chapter's picture: the first of its topics' covers that is a drawing. */
    fun cover(category: CourseCategory): CourseSee? =
        category.topics.mapNotNull { cover(it) }.let { cs -> cs.firstOrNull { it !is CourseSee.Table } ?: cs.firstOrNull() }

    fun dots(level: Int) = "●".repeat(level)
    fun gauge(level: Int) = "●".repeat(level) + "○".repeat(5 - level)

    /** LEVEL ▾: 0 all, 1–3 that level only, 4 four and up. */
    fun shown(topics: List<CourseTopic>, level: Int) = when (level) {
        0 -> topics
        4 -> topics.filter { it.level >= 4 }
        else -> topics.filter { it.level == level }
    }
}

/** The acoustics the Sound & pitch pages quote; their numbers come from here, so the text can't drift from the maths. */
object Acoustics {
    /** Equal-tempered frequency of [midi] with A4 at [a4] Hz. */
    fun hz(midi: Int, a4: Double = 440.0) = a4 * 2.0.pow((midi - 69) / 12.0)
    fun cents(ratio: Double) = 1200 * ln(ratio) / ln(2.0)
    /** Harmonic [n] above a fundamental: the nearest equal-tempered semitone count and how far off it is, in cents. */
    fun harmonic(n: Int): Pair<Int, Double> {
        val c = cents(n.toDouble())
        val s = Math.round(c / 100).toInt()
        return s to c - s * 100
    }
    /** Twelve pure fifths against seven octaves. */
    val pythagoreanComma: Double get() = 12 * cents(1.5) - 8400
    fun fmt(places: Int, x: Double): String = String.format(java.util.Locale.ROOT, "%.${places}f", x)
    fun hzText(x: Double) = fmt(if (x == Math.rint(x)) 0 else 2, x)
    fun centsText(x: Double) = (if (x > 0.005) "+" else if (x < -0.005) "−" else "") + fmt(2, Math.abs(x))
}

/** What the chapter files write pages with: piano notes, sounds, answers and links. */
internal abstract class CourseKit {
    protected fun note(at: Double, len: Double, m: Int) = com.betterappmafia.apophenia.core.sound.PlayNote(at, at + len, m, RefSound.VELOCITY)
    protected fun seq(midis: List<Int>, step: Double = 0.55, len: Double = 0.5) = midis.mapIndexed { i, m -> note(i * step, len, m) }
    protected fun piano(notes: List<com.betterappmafia.apophenia.core.sound.PlayNote>) = EarAudio(notes)
    protected fun sound(label: String, f: (Int) -> EarAudio) = CourseSound(label) { r, _ -> f(r) }
    protected fun option(label: String, explain: String, f: (Int, Random) -> EarAudio) = CourseOption(label, explain, f)
    protected fun ref(kind: RefKind, id: String, label: String, root: String = "C") = CourseLink(label, CourseTarget.Ref(kind, id, root))
    protected fun ear(id: String, label: String) = CourseLink("Ear: $label", CourseTarget.Ear(id))
    protected fun page(id: String, label: String) = CourseLink(label, CourseTarget.Page(id))
    /** One note, then [semitones] above (negative: below), then both together. */
    protected fun melodicThenHarmonic(r: Int, semitones: Int) = piano(seq(listOf(r, r + semitones), step = 0.8, len = 0.75) + RefSound.block(listOf(r, r + semitones), at = 1.9, length = 1.6))
    protected fun together(r: Int, semitones: Int, at: Double = 0.0) = RefSound.block(listOf(r, r + semitones), at = at, length = 1.8)

    // ------------------------------------------------------------------- pictures: drawn in a fixed key, played as drawn

    protected fun say(text: String, see: CourseSee? = null, vararg hear: CourseSound) = CourseStep(text, see, hear.toList())
    protected fun sp(s: String) = com.betterappmafia.apophenia.core.pitch.SpelledPitch.parse(s)
    protected fun pname(s: String) = CourseIntervals.shown(sp(s).name)
    /** Spelled pitches with octaves ("C4 E4 G4") as MIDI. */
    protected fun mids(s: String) = s.split(' ').filter { it.isNotBlank() }.map { sp(it).midi }

    /** Keys lit for [notes] ("C4 E4 G4"), labelled with [labels] (default: the note names); [faint] notes are drawn as context. */
    protected fun keys(caption: String, notes: String, labels: List<String>? = null, faint: String = "", play: Play? = Play(listOf(mids(notes)), 1.6, spread = true)): CourseSee.Keys {
        val lit = notes.split(' ').filter { it.isNotBlank() }
        val ls = labels ?: lit.map { pname(it) }
        val context = faint.split(' ').filter { it.isNotBlank() && it !in lit }.map { Key(sp(it).midi, pname(it), faint = true) }
        return CourseSee.Keys(caption, lit.mapIndexed { i, n -> Key(sp(n).midi, ls.getOrElse(i) { "" }) } + context, play)
    }
    /** Keys for a run of single notes played one at a time. */
    protected fun keysLine(caption: String, notes: String, labels: List<String>? = null, step: Double = 0.45) =
        keys(caption, notes, labels, play = Play(mids(notes).map { listOf(it) }, step))

    /** A chord from degree tokens above the tonic ("b6|b6 1 #4" puts the first part in the bass). */
    internal fun ch(t: String): EarHarmony.HChord {
        val (bass, tones) = if ("|" in t) t.substringBefore("|") to t.substringAfter("|") else t.substringBefore(" ") to t
        return EarHarmony.HChord(com.betterappmafia.apophenia.core.degree.DegreeToken.list("$bass $tones"))
    }
    /** A chord's notes spelled on [key]; a bass outside the chord (C/B) comes first. */
    internal fun spell(t: String, key: String = "C") = ch(t).tokens.let { tk -> if (tk.first() in tk.drop(1)) tk.drop(1) else tk }.distinct().joinToString(" ") {
        CourseIntervals.shown(PitchName.parse(key) + it.interval)
    }
    /** The piano voicing every progression uses: bass low, the rest close around the tonic ([tonic] MIDI, C4 = 60). */
    internal fun voiced(t: String, tonic: Int = 60) = EarHarmony.voice(ch(t), tonic)
    /** One chord of a chart: numeral, symbol, tokens above [key]'s tonic, and its job. */
    protected fun cc(numeral: String, symbol: String, tokens: String, tag: String = "", key: String = "C") =
        ChartChord(numeral, symbol, spell(tokens, key), voiced(tokens, tonicMidi(key)), tag)
    /** [key]'s tonic between F♯3 and F4, so every key sounds in the same range. */
    protected fun tonicMidi(key: String) = PitchName.parse(key).pitchClass.let { if (it > 5) 48 + it else 60 + it }
    protected fun chart(caption: String, vararg chords: ChartChord, step: Double = 1.1) = CourseSee.Chart(caption, chords.toList(), step)
    /** A sound that plays a chart exactly as drawn (for HEAR buttons that name it). */
    protected fun hearChart(label: String, c: CourseSee.Chart) = CourseSound(label) { _, _ -> c.play.audio() }
    protected fun hearPlay(label: String, p: Play) = CourseSound(label) { _, _ -> p.audio() }

    /** A grid of cells: [columns] across, each row lighting the columns in [lit]. */
    protected fun row(label: String, cells: List<String>, lit: Set<Int>, play: Play? = null) = GridRow(label, cells, lit, play)

    /**
     * Chords stacked on a scale: the scale written twice across ([notes], one octave of spelled pitches starting at
     * octave 4), each row lighting every other note from its degree. [size] 3 = triads, 4 = sevenths.
     */
    protected fun stackGrid(caption: String, notes: List<String>, labels: List<String>, size: Int, octave: Int = 4): CourseSee.Grid {
        val two = notes.map { it to octave } + notes.map { it to octave + 1 }
        val ms = run {
            var last = -1
            two.map { (n, o) -> var m = sp("$n$o").midi; while (m <= last) m += 12; last = m; m }
        }
        val cols = two.map { CourseIntervals.shown(PitchName.parse(it.first)) }
        val rows = labels.mapIndexed { d, l ->
            val lit = (0 until size).map { d + 2 * it }.toSet()
            row(l, cols, lit, Play(listOf(lit.sorted().map { ms[it] }), 1.4, spread = true))
        }
        return CourseSee.Grid(caption, cols, rows, Play(rows.map { r -> r.lit.sorted().map { ms[it] } }, 1.0))
    }

    /**
     * A few bars on a treble (or bass) staff: each entry of [chords] is one whole-note chord ("C4 E4 G4"), with an optional
     * symbol above and words below (a numeral, a function).
     */
    protected fun staff(caption: String, chords: List<String>, above: List<String> = emptyList(), below: List<String> = emptyList(),
                        clef: com.betterappmafia.apophenia.core.score.Clef = com.betterappmafia.apophenia.core.score.Clef.TREBLE,
                        key: com.betterappmafia.apophenia.core.catalog.KeyMode? = null, perBar: Int = 1, step: Double = 1.1): CourseSee.Staff {
        val d = when (perBar) { 1 -> com.betterappmafia.apophenia.core.rhythm.Duration.WHOLE; 2 -> com.betterappmafia.apophenia.core.rhythm.Duration.HALF; else -> com.betterappmafia.apophenia.core.rhythm.Duration.QUARTER }
        val events = chords.mapIndexed { i, c ->
            com.betterappmafia.apophenia.core.score.Event(d, c.split(' ').filter { it.isNotBlank() }.map { com.betterappmafia.apophenia.core.score.Note(sp(it)) },
                chord = above.getOrNull(i)?.takeIf { it.isNotEmpty() },
                lyrics = below.getOrNull(i)?.takeIf { it.isNotEmpty() }?.let { listOf(com.betterappmafia.apophenia.core.score.Lyric(1, it)) } ?: emptyList())
        }
        val bars = events.chunked(perBar).map { com.betterappmafia.apophenia.core.score.Bar(listOf(it)) }
        val part = com.betterappmafia.apophenia.core.score.Part("", clef, bars = bars)
        return CourseSee.Staff(caption, com.betterappmafia.apophenia.core.score.Score(key = key, parts = listOf(part)), Play(chords.map { mids(it) }, step))
    }
    protected fun table(caption: String, header: List<String>, rows: List<List<String>>, play: List<Play?> = emptyList()) = CourseSee.Table(caption, header, rows, play)

    /** Note names across ("C D Eb E", ASCII), shown with real accidentals. */
    protected fun cols(columns: String) = columns.split(' ').filter { it.isNotBlank() }.map { CourseIntervals.shown(PitchName.parse(it)) }

    /**
     * Chords against note-name [columns]: each row ([label] to tokens above [key]'s tonic) lights the notes it holds, so
     * you can see which notes chords share and which one moves. A row plays its chord on tap; ▶ plays them in order.
     */
    protected fun noteGrid(caption: String, columns: String, rows: List<Pair<String, String>>, key: String = "C", step: Double = 1.1): CourseSee.Grid {
        val c = cols(columns)
        val rs = rows.map { (label, t) ->
            val names = spell(t, key).split(' ').toSet()
            row(label, c, c.indices.filter { c[it] in names }.toSet(), Play(listOf(voiced(t, tonicMidi(key))), 1.4, spread = true))
        }
        return CourseSee.Grid(caption, c, rs, Play(rows.map { voiced(it.second, tonicMidi(key)) }, step))
    }

    /** A scale as a grid row: the [columns] it uses lit, played up one note at a time ([notes] with octaves). */
    protected fun scaleRow(label: String, columns: List<String>, notes: String): GridRow {
        val names = notes.split(' ').filter { it.isNotBlank() }.map { pname(it) }.toSet()
        return row(label, columns, columns.indices.filter { columns[it] in names }.toSet(), Play(mids(notes).map { listOf(it) }, 0.4))
    }
}
