package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.catalog.Chords
import com.betterappmafia.apophenia.core.catalog.Scales
import com.betterappmafia.apophenia.core.degree.DegreeToken
import com.betterappmafia.apophenia.core.interval.Interval
import com.betterappmafia.apophenia.core.interval.plus
import com.betterappmafia.apophenia.core.pitch.PitchClass
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.sound.PlayNote
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.random.Random

/*
 * Ear (plan §9; C4 'ear' screen): drills grouped in chapters, each played in Listen (hear any answer, no score),
 * Practice (answer, hear the right one and yours, keep going) or Test (twenty questions, scored and kept). Questions
 * are on the piano, in a random key unless that's turned off. Commonly confused intervals (m6/M6, m7/M7, the tritone)
 * come up twice as often, and anything missed in a session comes up more until it's answered right. Drills you sing or
 * play into the mic (pitch matching, sung intervals) are graded by the tuner and open the mic only when you tap to.
 */

enum class EarKind {
    INTERVAL, CHORD, INVERSION,
    /** A seventh chord with its root in the bass: close, drop 2, drop 3 or spread. */
    VOICING,
    /** Name a note, after an A reference when the drill gives one. */
    NOTE,
    OCTAVE,
    /** Two tones; is the second higher or lower? */
    CENTS,
    /** A scale degree after a cadence or over a drone. */
    DEGREE,
    /** A scale or mode played over a drone of its tonic. */
    MODE,
    /** A mode's characteristic note against its tonic, over a drone. */
    CHARACTER,
    /** Sing or play the note you hear (graded by the tuner). */
    MATCH,
    /** Sing the interval asked for above the note you hear (graded by the tuner). */
    SING,
    /** A short phrase of chords (EarHarmony): numerals, cadences, secondary dominants, borrowed chords, chromatic chords, substitutes, modulation. */
    HARMONY,
    /** Equal or just: a triad in plain tones. */
    TUNING,
    /** How fast two near-unison tones beat. */
    BEATS,
    /** Which harmonic of a low note. */
    PARTIAL,
    /** Play back a melody note by note (EarDictation). */
    MELODY,
    /** Play back a bass line under chords. */
    BASS,
    /** Which melody note over a chord is not a chord tone. */
    CHORDTONE,
    /** How fast a short groove is. */
    TEMPO,
    /** Hear a bar, pick it from three written bars (EarRhythm). */
    RHYTHM,
    /** Straight or swung eighths. */
    SWING,
    /** Tap a bar back after hearing it; scored in milliseconds. */
    TAPBACK,
    /** Your music (YourEar): a chord from your own charts, lyrics and takes. */
    OWN_CHORD,
    /** The key of one of your recordings. */
    OWN_KEY,
    /** A phrase from one of your takes, played back note by note. */
    OWN_PHRASE,
}

/**
 * One drill. [answers] are interval short names (m3), catalog chord ids (m7), inversion numbers (0, 1, 2), pitch classes
 * (0–11), octave numbers, "up"/"down", degree tokens (b3), or catalog scale ids. [reference] gives an A first (NOTE);
 * [cents] is the step of a CENTS drill; [minor] makes a DEGREE drill's key minor.
 */
data class EarDrill(
    val id: String, val chapter: String, val name: String, val kind: EarKind, val answers: List<String>, val note: String = "",
    val reference: Boolean = false, val cents: Int = 0, val minor: Boolean = false,
    /** Your-music drills: the material they draw from. */
    val pool: YourPool? = null,
) {
    /** Drills answered into the mic. */
    val sung: Boolean get() = kind == EarKind.MATCH || kind == EarKind.SING
}

enum class EarDirection(val label: String) { UP("Up"), DOWN("Down"), TOGETHER("Together"), MIXED("Mixed") }
enum class ChordSound(val label: String) { BLOCK("Block chord"), ARPEGGIO("Arpeggio up"), BOTH("Block, then arpeggio") }
enum class DegreeContext(val label: String) { CADENCE("After a cadence"), DRONE("Over a drone") }

data class EarSettings(
    val direction: EarDirection = EarDirection.MIXED,
    val chord: ChordSound = ChordSound.BLOCK,
    val randomKey: Boolean = true,
    val context: DegreeContext = DegreeContext.CADENCE,
)

/**
 * One question: the [answer] and the notes, low to high ([midis]); [direction] is how an interval is played. The root's
 * spelling ([root]) names the notes in the explanation. [tonic] is the key's tonic (MIDI) for degrees, modes and
 * drones; [cents] the second tone's offset in a CENTS question.
 */
data class EarQuestion(
    val drill: EarDrill, val answer: String, val midis: List<Int>, val root: PitchName, val direction: EarDirection,
    val tonic: Int? = null, val cents: Int = 0,
    /** Chords sounding with the notes (bass dictation, chord tones, the tempo groove), voiced as MIDI. */
    val chords: List<List<Int>> = emptyList(),
    val bpm: Int = 0,
    /** Rhythm questions: the bars (the options of a dictation question; one bar otherwise) and their meter. */
    val rhythm: List<List<RNote>> = emptyList(),
    val meter: com.betterappmafia.apophenia.core.rhythm.TimeSignature? = null,
    /** Your-music questions: where it came from, and the stretch of your recording to play. */
    val note: String = "",
    val clip: YourClip? = null,
)

/** A synthesized tone: [hz] from [start] to [end] seconds, with [partials] (amplitudes of harmonics 1, 2, 3…; the default is a soft organ-like tone) at [gain]. */
data class Tone(val start: Double, val end: Double, val hz: Double, val partials: List<Double> = DEFAULT_PARTIALS, val gain: Double = 0.18)

val DEFAULT_PARTIALS = listOf(1.0, 0.45, 0.2, 0.1)

/** What a question sounds like: piano notes, synthesized tones, and a drone on [drone] (MIDI) for its whole length. */
data class EarAudio(val piano: List<PlayNote>, val tones: List<Tone> = emptyList(), val drone: Int? = null, val drums: List<DrumAt> = emptyList(), val clip: YourClip? = null) {
    val seconds: Double get() = maxOf(piano.maxOfOrNull { it.end } ?: 0.0, tones.maxOfOrNull { it.end } ?: 0.0, drums.maxOfOrNull { it.at } ?: 0.0, clip?.length ?: 0.0) + 1.3
}

object Ear {
    const val TEST_LENGTH = 20
    /** How far a sung note may be from its target and still count, in cents. [Claude] */
    const val SUNG_TOLERANCE = 30.0
    private val CONFUSED = setOf("m6", "M6", "m7", "M7", "A4")
    private val PCS = (0..11).map { it.toString() }
    private val SIMPLE = listOf("m2", "M2", "m3", "M3", "P4", "A4", "P5", "m6", "M6", "m7", "M7", "P8")

    val drills: List<EarDrill> = listOf(
        EarDrill("iv-2-3", "Intervals", "Seconds and thirds", EarKind.INTERVAL, listOf("m2", "M2", "m3", "M3")),
        EarDrill("iv-4-5", "Intervals", "Fourths, fifths, tritone", EarKind.INTERVAL, listOf("P4", "A4", "P5")),
        EarDrill("iv-6-7", "Intervals", "Sixths and sevenths", EarKind.INTERVAL, listOf("m6", "M6", "m7", "M7"), "THE PAIRS MOST OFTEN CONFUSED"),
        EarDrill("iv-simple", "Intervals", "Every simple interval", EarKind.INTERVAL, SIMPLE),
        EarDrill("iv-compound", "Intervals", "Compound intervals", EarKind.INTERVAL, listOf("m9", "M9", "m10", "M10", "P11", "A11", "P12", "m13", "M13")),
        EarDrill("iv-sing", "Intervals", "Sing the interval", EarKind.SING, SIMPLE, "HEAR A NOTE, SING THE INTERVAL ABOVE · MIC"),
        EarDrill("ch-triads", "Chords", "Major · minor · dim · aug", EarKind.CHORD, listOf("maj", "m", "dim", "aug")),
        EarDrill("ch-sus", "Chords", "Major · sus2 · sus4", EarKind.CHORD, listOf("maj", "sus2", "sus4")),
        EarDrill("ch-7-basic", "Chords", "Maj7 · m7 · 7", EarKind.CHORD, listOf("maj7", "m7", "7")),
        EarDrill("ch-7-all", "Chords", "Every seventh chord", EarKind.CHORD, listOf("maj7", "7", "m7", "mMaj7", "m7b5", "dim7", "7#5", "maj7#5")),
        EarDrill("ch-6-add", "Chords", "Sixths and added notes", EarKind.CHORD, listOf("6", "m6", "add9", "madd9", "69")),
        EarDrill("ch-ext", "Chords", "Ninths and beyond", EarKind.CHORD, listOf("9", "maj9", "m9", "11", "13", "7b9", "7#9", "7alt")),
        EarDrill("ch-inv", "Chords", "Triad inversions", EarKind.INVERSION, listOf("0", "1", "2"), "MAJOR AND MINOR TRIADS"),
        EarDrill("ch-inv7", "Chords", "Seventh-chord inversions", EarKind.INVERSION, listOf("0", "1", "2", "3"), "DOMINANT AND MINOR SEVENTHS"),
        EarDrill("ch-voicing", "Chords", "Voicing spread", EarKind.VOICING, listOf("close", "drop2", "drop3", "spread"), "CLOSE · DROP 2 · DROP 3 · SPREAD, ROOT IN THE BASS"),
        EarDrill("pitch-ref", "Pitch", "Name the note, after an A", EarKind.NOTE, PCS, "A4 FIRST, THEN THE NOTE", reference = true),
        EarDrill("pitch-abs", "Pitch", "Name the note, no reference", EarKind.NOTE, PCS, "ABSOLUTE PITCH"),
        EarDrill("pitch-octave", "Pitch", "Which octave", EarKind.OCTAVE, listOf("2", "3", "4", "5", "6"), "C2 TO B6"),
        EarDrill("pitch-cents-25", "Pitch", "Higher or lower · 25 cents", EarKind.CENTS, listOf("up", "down"), cents = 25),
        EarDrill("pitch-cents-10", "Pitch", "Higher or lower · 10 cents", EarKind.CENTS, listOf("up", "down"), cents = 10),
        EarDrill("pitch-cents-5", "Pitch", "Higher or lower · 5 cents", EarKind.CENTS, listOf("up", "down"), cents = 5),
        EarDrill("pitch-match", "Pitch", "Sing or play the note", EarKind.MATCH, PCS, "GRADED BY THE TUNER · MIC"),
        EarDrill("deg-major", "Scale degrees", "Degrees in a major key", EarKind.DEGREE, listOf("1", "2", "3", "4", "5", "6", "7")),
        EarDrill("deg-minor", "Scale degrees", "Degrees in a minor key", EarKind.DEGREE, listOf("1", "2", "b3", "4", "5", "b6", "b7", "7"), "NATURAL MINOR, AND THE RAISED 7", minor = true),
        EarDrill("deg-chromatic", "Scale degrees", "Chromatic degrees", EarKind.DEGREE, listOf("1", "b2", "2", "b3", "3", "4", "#4", "5", "b6", "6", "b7", "7")),
        EarDrill("mode-major", "Scales", "Modes of major", EarKind.MODE, listOf("major", "dorian", "phrygian", "lydian", "mixolydian", "aeolian", "locrian"), "OVER A DRONE"),
        EarDrill("mode-melodic", "Scales", "Modes of melodic minor", EarKind.MODE, Scales.all.filter { it.family == "melodic-minor" }.map { it.id }, "OVER A DRONE"),
        EarDrill("mode-harmonic", "Scales", "Modes of harmonic minor", EarKind.MODE, Scales.all.filter { it.family == "harmonic-minor" }.map { it.id }, "OVER A DRONE"),
        EarDrill("mode-symmetric", "Scales", "Symmetric scales", EarKind.MODE, listOf("whole-tone", "half-whole", "whole-half", "augmented"), "OVER A DRONE"),
        EarDrill("mode-character", "Scales", "Characteristic notes", EarKind.CHARACTER, listOf("dorian", "phrygian", "lydian", "mixolydian", "aeolian", "locrian"), "THE ONE NOTE THAT MAKES THE MODE"),
    ) + EarHarmony.drills + EarDictation.drills + EarRhythm.drills

    /** The note each mode of major is known by: the degree that sets it apart from major or natural minor. */
    val CHARACTERISTIC = mapOf("dorian" to "6", "phrygian" to "b2", "lydian" to "#4", "mixolydian" to "b7", "aeolian" to "b6", "locrian" to "b5")

    val chapters: List<String> = drills.map { it.chapter }.distinct()

    /** Mixed practice (the default, plan §9): questions drawn from every drill in [chapter], or from all when null; never the mic drills. */
    fun mixed(chapter: String?): EarDrill {
        val pool = drills.filter { (chapter == null || it.chapter == chapter) && !it.sung && it.kind != EarKind.TAPBACK }
        return EarDrill("mixed-${chapter?.lowercase()?.replace(' ', '-') ?: "all"}", chapter ?: "Mixed", "Mixed: ${chapter?.lowercase() ?: "everything"}", pool.first().kind, pool.flatMap { d -> d.answers.map { "${d.id}|$it" } }, "EVERY DRILL IN THE CHAPTER")
    }

    fun drill(id: String): EarDrill? = drills.firstOrNull { it.id == id } ?: when {
        id == "mixed-all" -> mixed(null)
        id.startsWith(PICK) -> runCatching { pick(id.removePrefix(PICK).split(',')) }.getOrNull()
        else -> chapters.firstOrNull { "mixed-${it.lowercase().replace(' ', '-')}" == id }?.let(::mixed)
    }

    private const val PICK = "pick:"

    /**
     * What can be picked one by one in [chapter] (Christopher, 25 Oct 2026: "There's no way to practice specific
     * intervals and so on"): every answer of its drills as "drillId|answer", grouped under the drill it first appears in.
     * An answer that sounds the same in two drills (m3 in "Seconds and thirds" and "Every simple interval") is listed
     * once. Mic drills and tap-back are left out, as in mixed practice.
     */
    fun pickable(chapter: String): List<Pair<EarDrill, List<String>>> {
        val seen = HashSet<String>()
        return drills.filter { it.chapter == chapter && !it.sung && it.kind != EarKind.TAPBACK }.map { d ->
            d to d.answers.filter { a -> seen.add("${d.kind}|${d.minor}|${d.reference}|${d.cents}|$a") }.map { "${d.id}|$it" }
        }.filter { it.second.isNotEmpty() }
    }

    /** A drill of just the [answers] picked ("drillId|answer"); its id keeps the picks, so its high score is theirs. */
    fun pick(answers: List<String>): EarDrill {
        require(answers.isNotEmpty())
        val sources = answers.map { a -> source(EarDrill("", "", "", EarKind.INTERVAL, emptyList()), a) }
        val first = sources.first().first
        val name = sources.joinToString(" · ") { (d, a) -> label(d, a) }
        return EarDrill(PICK + answers.joinToString(","), first.chapter, "Your pick: $name", first.kind, answers, "PICKED ONE BY ONE")
    }

    /** A mixed drill's answer "drillId|answer" resolved to the drill it comes from. */
    fun source(drill: EarDrill, answer: String): Pair<EarDrill, String> =
        if ('|' in answer) answer.split('|', limit = 2).let { (d, a) -> drills.first { it.id == d } to a } else drill to answer

    /** How often [answer] should come up: confused intervals ×2, each miss this session +1 until answered right. */
    fun weight(answer: String, missed: Map<String, Int>): Double = (if (answer.substringAfter('|') in CONFUSED) 2.0 else 1.0) + (missed[answer] ?: 0)

    fun question(drill: EarDrill, settings: EarSettings, random: Random, missed: Map<String, Int> = emptyMap(), previous: EarQuestion? = null): EarQuestion {
        val weights = drill.answers.map { a -> weight(a, missed) * (if (previous?.answer == a && drill.answers.size > 2) 0.3 else 1.0) }
        var pick = random.nextDouble() * weights.sum()
        var index = 0
        while (index < weights.size - 1 && pick >= weights[index]) { pick -= weights[index]; index++ }
        val full = drill.answers[index]
        val (src, answer) = source(drill, full)
        val rootPc = if (settings.randomKey) random.nextInt(12) else 0
        val root = PitchClass(rootPc).defaultSpelling()
        val direction = if (settings.direction == EarDirection.MIXED) listOf(EarDirection.UP, EarDirection.DOWN, EarDirection.TOGETHER)[random.nextInt(3)] else settings.direction
        fun at(pc: Int, low: Int) = RefSound.midis(pc, listOf(0), low).first()
        return when (src.kind) {
            EarKind.INTERVAL, EarKind.SING -> {
                val lo = at(rootPc, 55)
                EarQuestion(drill, full, listOf(lo, lo + Interval.parse(answer).semitones), root, if (src.kind == EarKind.SING) EarDirection.UP else direction)
            }
            EarKind.CHORD -> EarQuestion(drill, full, RefSound.midis(rootPc, Chords.byId(answer).semitones, 48), root, direction)
            EarKind.VOICING -> EarQuestion(drill, full, voicing(RefSound.midis(rootPc, Chords.byId(listOf("maj7", "7", "m7")[random.nextInt(3)]).semitones, 48), answer), root, direction)
            EarKind.INVERSION -> {
                val type = if (src.answers.size == 4) listOf("7", "m7")[random.nextInt(2)] else listOf("maj", "m")[random.nextInt(2)]
                EarQuestion(drill, full, inversion(RefSound.midis(rootPc, Chords.byId(type).semitones, 48), answer.toInt()), root, direction)
            }
            EarKind.NOTE, EarKind.MATCH -> {
                val pc = answer.toInt()
                val m = at(pc, if (src.kind == EarKind.MATCH) 48 + random.nextInt(2) * 12 else 48 + random.nextInt(3) * 12)
                EarQuestion(drill, full, listOf(m), PitchClass(pc).defaultSpelling(), direction)
            }
            EarKind.OCTAVE -> {
                val pc = random.nextInt(12)
                val m = 12 * (answer.toInt() + 1) + pc
                EarQuestion(drill, full, listOf(m), PitchClass(pc).defaultSpelling(), direction)
            }
            EarKind.CENTS -> {
                val m = 55 + random.nextInt(17)
                EarQuestion(drill, full, listOf(m), PitchClass(m).defaultSpelling(), direction, cents = if (answer == "up") src.cents else -src.cents)
            }
            EarKind.DEGREE -> {
                val tonic = at(rootPc, 60)
                val d = DegreeToken.parse(answer)
                EarQuestion(drill, full, listOf(tonic + Math.floorMod(d.interval.semitones, 12)), root, direction, tonic = tonic)
            }
            EarKind.MODE -> {
                val tonic = at(rootPc, 57)
                val semis = Scales.byId(answer).semitones
                EarQuestion(drill, full, semis.map { tonic + it } + (tonic + 12), root, direction, tonic = tonic)
            }
            EarKind.CHARACTER -> {
                val tonic = at(rootPc, 57)
                val s = Scales.byId(answer).semitones
                val ch = tonic + Math.floorMod(DegreeToken.parse(CHARACTERISTIC.getValue(answer)).interval.semitones, 12)
                EarQuestion(drill, full, listOf(tonic, ch, tonic, tonic + s[2], tonic + s[4], ch, tonic), root, direction, tonic = tonic)
            }
            EarKind.HARMONY -> {
                val tonic = at(rootPc, 60)
                EarQuestion(drill, full, EarHarmony.sound(src, answer, tonic).map { it.midi }, root, direction, tonic = tonic)
            }
            EarKind.TUNING -> EarQuestion(drill, full, listOf(at(rootPc, 48)), root, direction)
            EarKind.BEATS -> EarQuestion(drill, full, listOf(at(rootPc, 57)), root, direction)
            EarKind.PARTIAL -> EarQuestion(drill, full, listOf(at(rootPc, 36)), root, direction)
            EarKind.MELODY, EarKind.BASS, EarKind.CHORDTONE, EarKind.TEMPO -> EarDictation.question(src, answer, full, rootPc, root, random).copy(drill = drill)
            EarKind.RHYTHM, EarKind.SWING, EarKind.TAPBACK -> EarRhythmQuestions.question(src, answer, full, root, random).copy(drill = drill)
            EarKind.OWN_CHORD, EarKind.OWN_KEY, EarKind.OWN_PHRASE -> YourEar.question(src, answer, random)
        }
    }


    /** Seventh-chord voicings with the root in the bass: name and the chord tones from the bass up. [Claude] */
    val VOICINGS: Map<String, Pair<String, String>> = linkedMapOf(
        "close" to ("Close" to "root, 3rd, 5th, 7th, all within an octave"),
        "drop2" to ("Drop 2" to "root, 5th, 7th, 3rd"),
        "drop3" to ("Drop 3" to "root, 7th, 3rd, 5th"),
        "spread" to ("Spread" to "root, 5th, then the 3rd and 7th more than an octave above (1 5 10 14)"),
    )

    /** A root-position close seventh chord [c] (root, 3rd, 5th, 7th) voiced as [v]. */
    fun voicing(c: List<Int>, v: String): List<Int> = when (v) {
        "drop2" -> listOf(c[0], c[2], c[3], c[1] + 12)
        "drop3" -> listOf(c[0], c[3], c[1] + 12, c[2] + 12)
        "spread" -> listOf(c[0] - 12, c[2] - 12, c[1], c[3])
        else -> c
    }

    /** Undoes [voicing]: the close chord a voiced one came from. */
    fun close(m: List<Int>, v: String): List<Int> = when (v) {
        "drop2" -> listOf(m[0], m[3] - 12, m[1], m[2])
        "drop3" -> listOf(m[0], m[2] - 12, m[3] - 12, m[1])
        "spread" -> listOf(m[0] + 12, m[2], m[1] + 12, m[3])
        else -> m
    }
    /** Close position with chord tone [k] in the bass: the tones below it move up an octave. */
    fun inversion(rootPosition: List<Int>, k: Int): List<Int> = (rootPosition.drop(k) + rootPosition.take(k).map { it + 12 })

    private fun note(at: Double, len: Double, m: Int) = PlayNote(at, at + len, m, RefSound.VELOCITY)

    /** The cadence that sets a key: I – IV – V – I, or i – iv – V – i in minor (V from harmonic minor). */
    fun cadence(tonic: Int, minor: Boolean): List<PlayNote> {
        val third = if (minor) 3 else 4
        val chords = listOf(listOf(0, third, 7), listOf(5, if (minor) 8 else 9, 12), listOf(-1, 2, 7), listOf(0, third, 7))
        return chords.flatMapIndexed { i, c -> c.map { note(i * 0.75, 0.7, tonic - 12 + it) } + note(i * 0.75, 0.7, tonic - 24 + listOf(0, 5, 7, 0)[i]) }
    }

    /** What the question sounds like. */
    fun sound(q: EarQuestion, settings: EarSettings): EarAudio {
        val (src, _) = source(q.drill, q.answer)
        return when (src.kind) {
            EarKind.INTERVAL -> EarAudio(when (q.direction) {
                EarDirection.DOWN -> listOf(note(0.0, 0.9, q.midis[1]), note(1.0, 0.9, q.midis[0]))
                EarDirection.TOGETHER -> RefSound.block(q.midis, length = 1.8)
                else -> listOf(note(0.0, 0.9, q.midis[0]), note(1.0, 0.9, q.midis[1]))
            })
            EarKind.SING -> EarAudio(listOf(note(0.0, 1.6, q.midis[0])))
            EarKind.CHORD, EarKind.INVERSION, EarKind.VOICING -> EarAudio(when (settings.chord) {
                ChordSound.BLOCK -> RefSound.block(q.midis)
                ChordSound.ARPEGGIO -> RefSound.upDown(q.midis, down = false)
                ChordSound.BOTH -> RefSound.block(q.midis) + RefSound.upDown(q.midis, at = 2.0, down = false)
            })
            EarKind.NOTE -> EarAudio(if (src.reference) listOf(note(0.0, 1.0, 69), note(1.6, 1.2, q.midis[0])) else listOf(note(0.0, 1.4, q.midis[0])))
            EarKind.OCTAVE, EarKind.MATCH -> EarAudio(listOf(note(0.0, 1.6, q.midis[0])))
            EarKind.CENTS -> {
                val hz = 440.0 * Math.pow(2.0, (q.midis[0] - 69) / 12.0)
                EarAudio(emptyList(), listOf(Tone(0.0, 1.0, hz), Tone(1.3, 2.3, hz * Math.pow(2.0, q.cents / 1200.0))))
            }
            EarKind.DEGREE -> {
                val t = q.tonic!!
                if (settings.context == DegreeContext.DRONE) EarAudio(listOf(note(1.2, 1.4, q.midis[0])), drone = t - 24)
                else EarAudio(cadence(t, src.minor) + note(3.6, 1.4, q.midis[0]))
            }
            EarKind.MODE -> EarAudio(RefSound.upDown(q.midis).map { it.copy(start = it.start + 1.0, end = it.end + 1.0) }, drone = q.tonic!! - 24)
            EarKind.CHARACTER -> EarAudio(q.midis.mapIndexed { i, m -> note(1.0 + i * 0.45, 0.42, m) }, drone = q.tonic!! - 24)
            EarKind.HARMONY -> EarAudio(EarHarmony.sound(src, q.answer.substringAfter('|'), q.tonic!!))
            EarKind.TUNING, EarKind.BEATS, EarKind.PARTIAL -> EarAudio(emptyList(), EarHarmony.tones(src, q.answer.substringAfter('|'), q.midis[0]))
            EarKind.MELODY, EarKind.BASS, EarKind.CHORDTONE, EarKind.TEMPO -> EarDictation.audio(q, settings)
            EarKind.RHYTHM, EarKind.SWING, EarKind.TAPBACK -> EarRhythmQuestions.audio(q)
            EarKind.OWN_CHORD, EarKind.OWN_KEY, EarKind.OWN_PHRASE -> YourEar.audio(q, settings)
        }
    }

    /** [answer] (another option of the same question) played on the same root, for hearing yours against the right one. */
    fun asAnswer(q: EarQuestion, answer: String): EarQuestion {
        val (src, a) = source(q.drill, answer)
        val rootPc = q.root.pitchClass
        val midis = when (src.kind) {
            EarKind.INTERVAL, EarKind.SING -> listOf(q.midis[0], q.midis[0] + Interval.parse(a).semitones)
            EarKind.CHORD -> RefSound.midis(rootPc, Chords.byId(a).semitones, 48)
            EarKind.VOICING -> voicing(close(q.midis, source(q.drill, q.answer).second), a)
            EarKind.INVERSION -> {
                val rootPos = q.midis.let { m -> val k = source(q.drill, q.answer).second.toInt(); m.takeLast(k).map { it - 12 } + m.dropLast(k) }
                inversion(rootPos, a.toInt())
            }
            EarKind.NOTE, EarKind.MATCH -> listOf(RefSound.midis(a.toInt(), listOf(0), q.midis[0] - 6).first())
            EarKind.OCTAVE -> listOf(12 * (a.toInt() + 1) + Math.floorMod(q.midis[0], 12))
            EarKind.CENTS -> q.midis
            EarKind.DEGREE -> listOf(q.tonic!! + Math.floorMod(DegreeToken.parse(a).interval.semitones, 12))
            EarKind.MODE -> Scales.byId(a).semitones.map { q.tonic!! + it } + (q.tonic!! + 12)
            EarKind.CHARACTER -> {
                val t = q.tonic!!
                val s = Scales.byId(a).semitones
                val ch = t + Math.floorMod(DegreeToken.parse(CHARACTERISTIC.getValue(a)).interval.semitones, 12)
                listOf(t, ch, t, t + s[2], t + s[4], ch, t)
            }
            EarKind.HARMONY -> EarHarmony.sound(src, a, q.tonic!!).map { it.midi }
            EarKind.TUNING, EarKind.BEATS, EarKind.PARTIAL, EarKind.MELODY, EarKind.BASS, EarKind.CHORDTONE -> q.midis
            EarKind.TEMPO -> return q.copy(answer = answer, bpm = a.toInt())
            EarKind.RHYTHM, EarKind.SWING, EarKind.TAPBACK -> return q.copy(answer = answer)
            EarKind.OWN_CHORD, EarKind.OWN_KEY, EarKind.OWN_PHRASE -> return YourEar.asAnswer(q, answer)
        }
        return q.copy(answer = answer, midis = midis, cents = if (src.kind == EarKind.CENTS) (if (a == "up") src.cents else -src.cents) else q.cents)
    }

    /** The answer's name on an answer button; [names] names a pitch class's note. */
    fun label(drill: EarDrill, answer: String, names: (PitchName) -> String = { it.toString() }): String {
        val (src, a) = source(drill, answer)
        return when (src.kind) {
            EarKind.INTERVAL, EarKind.SING -> a
            EarKind.CHORD -> Chords.byId(a).symbols.first().removePrefix("C").ifEmpty { "Major" }.let { if (it == "m") "Minor" else accidentals(it) }
            EarKind.INVERSION -> listOf("Root position", "1st inversion", "2nd inversion", "3rd inversion")[a.toInt()]
            EarKind.VOICING -> VOICINGS.getValue(a).first
            EarKind.NOTE, EarKind.MATCH -> names(PitchClass(a.toInt()).defaultSpelling())
            EarKind.OCTAVE -> "C$a–B$a"
            EarKind.CENTS -> if (a == "up") "Higher" else "Lower"
            EarKind.DEGREE -> accidentals(a)
            EarKind.MODE, EarKind.CHARACTER -> Scales.byId(a).names.first()
            EarKind.HARMONY -> EarHarmony.label(src, a)
            EarKind.TUNING, EarKind.BEATS, EarKind.PARTIAL -> EarHarmony.tuningLabel(src, a)
            EarKind.MELODY, EarKind.BASS, EarKind.CHORDTONE, EarKind.TEMPO -> EarDictation.label(src, a)
            EarKind.RHYTHM, EarKind.SWING, EarKind.TAPBACK -> EarRhythmQuestions.label(src, a)
            EarKind.OWN_CHORD, EarKind.OWN_KEY, EarKind.OWN_PHRASE -> YourEar.label(src, a)
        }
    }

    /** The right answer explained, with the notes it was played on. */
    fun explain(q: EarQuestion, names: (PitchName) -> String): String {
        val (src, a) = source(q.drill, q.answer)
        fun keyName() = names(q.root)
        return when (src.kind) {
            EarKind.INTERVAL, EarKind.SING -> {
                val i = Interval.parse(a)
                val upper = runCatching { q.root + i }.getOrNull()
                "$a, ${Reference.intervalName(i)}: ${i.semitones} semitones" + (upper?.let { ", ${names(q.root)} up to ${names(it)}" } ?: "") +
                    if (src.kind == EarKind.INTERVAL) " (played ${q.direction.label.lowercase()})." else "."
            }
            EarKind.CHORD -> {
                val page = ChordPages.page(Chords.byId(a), q.root)
                "${page.symbols.first()}: ${page.notes.joinToString(" ") { names(it) }}. ${Reference.CHORD_NAMES[a] ?: ""}."
            }
            EarKind.INVERSION -> "${label(q.drill, q.answer)}: the ${listOf("root", "3rd", "5th", "7th")[a.toInt()]} is in the bass."
            EarKind.VOICING -> "${VOICINGS.getValue(a).first}: from the bass up, ${VOICINGS.getValue(a).second}."
            EarKind.NOTE, EarKind.MATCH, EarKind.OCTAVE -> "${names(PitchClass(q.midis[0]).defaultSpelling())}${q.midis[0] / 12 - 1} (MIDI ${q.midis[0]})."
            EarKind.CENTS -> "The second tone was ${abs(q.cents)} cents ${if (q.cents > 0) "higher" else "lower"}."
            EarKind.DEGREE -> {
                val d = DegreeToken.parse(a)
                val n = runCatching { q.root + d.interval }.getOrNull()
                "${accidentals(a)} in ${keyName()} ${if (src.minor) "minor" else "major"}" + (n?.let { ": ${names(it)}" } ?: "") + "."
            }
            EarKind.MODE -> {
                val page = ScalePages.page(Scales.byId(a), q.root)
                "${keyName()} ${Scales.byId(a).names.first()}: ${page.notes.joinToString(" ") { names(it) }} (${page.steps.joinToString(" ") { ScalePages.stepName(it) }})."
            }
            EarKind.CHARACTER -> "${Scales.byId(a).names.first()}: its ${accidentals(CHARACTERISTIC.getValue(a))} against the tonic ${keyName()}."
            EarKind.HARMONY -> EarHarmony.explain(src, a, q.root, names)
            EarKind.TUNING, EarKind.BEATS, EarKind.PARTIAL -> EarHarmony.tuningExplain(src, a)
            EarKind.MELODY, EarKind.BASS, EarKind.CHORDTONE, EarKind.TEMPO -> EarDictation.explain(q, names)
            EarKind.RHYTHM, EarKind.SWING, EarKind.TAPBACK -> EarRhythmQuestions.explain(q)
            EarKind.OWN_CHORD, EarKind.OWN_KEY, EarKind.OWN_PHRASE -> YourEar.explain(q, names)
        }
    }

    /**
     * Answering on the piano or fretboard: for an interval, the second note at the exact pitch; for a chord, the same
     * pitch classes as the chord (any octave, any voicing). The first note (an interval's lower, a chord's root) is given.
     */
    fun check(q: EarQuestion, tapped: Collection<Int>): Boolean {
        val (src, _) = source(q.drill, q.answer)
        return when (src.kind) {
            EarKind.INTERVAL -> tapped.size == 1 && tapped.first() == q.midis[if (q.direction == EarDirection.DOWN) 0 else 1]
            EarKind.MELODY, EarKind.BASS, EarKind.OWN_PHRASE -> tapped.size == q.midis.size && EarDictation.compare(q, tapped.toList()).all { it }
            EarKind.NOTE, EarKind.OCTAVE, EarKind.DEGREE -> tapped.size == 1 && Math.floorMod(tapped.first(), 12) == Math.floorMod(q.midis.last(), 12) &&
                (src.kind != EarKind.OCTAVE || tapped.first() == q.midis[0])
            else -> tapped.map { Math.floorMod(it, 12) }.toSet() == q.midis.map { Math.floorMod(it, 12) }.toSet()
        }
    }

    /** The MIDI note a sung answer aims at: the note heard (MATCH) or the interval above it (SING). */
    fun target(q: EarQuestion): Int = if (source(q.drill, q.answer).first.kind == EarKind.SING) q.midis[1] else q.midis[0]

    /**
     * Grades a sung or played pitch: cents from the target's pitch class in whichever octave is nearest (a voice or
     * instrument in another octave still matches), within [SUNG_TOLERANCE]. [Claude]
     */
    fun grade(q: EarQuestion, hz: Double, a4: Double = 440.0): Pair<Boolean, Int> {
        val sung = 69 + 12 * ln(hz / a4) / ln(2.0)
        val t = target(q)
        val off = (sung - t) * 100
        val folded = off - 1200 * Math.round(off / 1200.0)
        return (abs(folded) <= SUNG_TOLERANCE) to folded.roundToInt()
    }
}

/** The note an instrument answer starts from: a descending interval gives its upper note, everything else its lowest. */
fun EarQuestion.given(): Int = if (direction == EarDirection.DOWN && Ear.source(drill, answer).first.kind == EarKind.INTERVAL) midis[1] else midis[0]

/** Pure tones and drones for the Ear (the piano can't hold a note or bend it by cents): a few harmonics, soft edges. */
object EarTone {
    fun render(audio: EarAudio, rate: Int, into: FloatArray) {
        fun add(start: Double, end: Double, hz: Double, gain: Double, partials: List<Double> = DEFAULT_PARTIALS) {
            val a = (start * rate).toInt().coerceAtLeast(0)
            val b = (end * rate).toInt().coerceAtMost(into.size)
            val edge = (0.03 * rate).toInt()
            for (i in a until b) {
                val t = (i - a).toDouble() / rate
                val env = minOf(1.0, (i - a).toDouble() / edge, (b - i).toDouble() / edge)
                var s = 0.0
                for (k in partials.indices) if (hz * (k + 1) < rate / 2) s += partials[k] * Math.sin(2 * Math.PI * hz * (k + 1) * t)
                into[i] += (gain * env * s).toFloat()
            }
        }
        audio.tones.forEach { add(it.start, it.end, it.hz, it.gain, it.partials) }
        if (audio.drums.isNotEmpty()) {
            val hits = audio.drums.map { com.betterappmafia.apophenia.core.tools.DrumKit.Hit((it.at * rate).toInt(), it.voice, it.hit) }
            val pcm = com.betterappmafia.apophenia.core.tools.DrumKit.render(hits, into.size, rate)
            val lead = (com.betterappmafia.apophenia.core.tools.DrumKit.LEAD_IN * rate).toInt()
            for (i in into.indices) into[i] += 0.8f * pcm.getOrElse(lead + i) { 0f }
        }
        audio.drone?.let { d ->
            val hz = 440.0 * Math.pow(2.0, (d - 69) / 12.0)
            add(0.0, into.size.toDouble() / rate, hz, 0.07)
            add(0.0, into.size.toDouble() / rate, hz * 2, 0.04)
        }
    }
}

/** A degree token or chord quality with real accidentals: b3 → ♭3, m7b5 → m7♭5, #11 → ♯11. Only for tokens, never prose. */
fun accidentals(token: String) = token.replace(Regex("bb(?=\\d)"), "𝄫").replace(Regex("b(?=\\d)"), "♭").replace(Regex("#(?=\\d)"), "♯")
