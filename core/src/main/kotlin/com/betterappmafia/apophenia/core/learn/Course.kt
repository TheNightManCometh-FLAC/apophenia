package com.betterappmafia.apophenia.core.learn

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
)

sealed interface CourseSee {
    /** A small table: [header] then [rows]. */
    data class Table(val caption: String, val header: List<String>, val rows: List<List<String>>) : CourseSee
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
}
