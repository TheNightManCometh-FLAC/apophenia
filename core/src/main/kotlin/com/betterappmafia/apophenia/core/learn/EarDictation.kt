package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.catalog.Scales
import com.betterappmafia.apophenia.core.pitch.PitchClass
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.sound.PlayNote
import kotlin.random.Random

/**
 * Dictation for the Ear (plan §9 "Melody and bass"; "tempo estimation" from Rhythm): a short melody or bass line in a
 * key to play back note by note, which note over a chord is not a chord tone, and how fast a groove is.
 * [Claude] Phrases are generated, not taken from songs: mostly steps, a few leaps up to a 5th, starting on 1, 3 or 5
 * and ending on the tonic.
 */
object EarDictation {
    val TEMPOS = listOf("60", "72", "84", "96", "108", "120", "132", "144", "160", "176")

    val drills: List<EarDrill> = listOf(
        EarDrill("dc-mel-3-step", "Dictation", "Melody · three notes by step", EarKind.MELODY, listOf("3"), "MAJOR KEYS · NEIGHBOURING SCALE NOTES ONLY"),
        EarDrill("dc-mel-3-leap", "Dictation", "Melody · three notes with a leap", EarKind.MELODY, listOf("3"), "MAJOR KEYS · AT LEAST ONE LEAP OF A 3RD OR MORE"),
        EarDrill("dc-mel-4", "Dictation", "Melody · four notes", EarKind.MELODY, listOf("4"), "MAJOR KEYS · THE FIRST NOTE IS GIVEN"),
        EarDrill("dc-mel-6", "Dictation", "Melody · six notes", EarKind.MELODY, listOf("6"), "MAJOR KEYS"),
        EarDrill("dc-mel-minor", "Dictation", "Melody · minor key", EarKind.MELODY, listOf("5"), "NATURAL MINOR", minor = true),
        EarDrill("dc-mel-mixed", "Dictation", "Melody · major or minor", EarKind.MELODY, listOf("5", "5m"), "THE CHORD BEFORE IT SAYS WHICH"),
        EarDrill("dc-mel-chromatic", "Dictation", "Melody · chromatic notes", EarKind.MELODY, listOf("5", "6"), "A NOTE OUTSIDE THE KEY, A HALF STEP FROM THE NEXT"),
        EarDrill("dc-bass", "Dictation", "Bass line", EarKind.BASS, listOf("4"), "FOUR CHORDS IN A MAJOR KEY · THE FIRST NOTE IS GIVEN"),
        EarDrill("dc-bass-minor", "Dictation", "Bass line · minor key", EarKind.BASS, listOf("4"), "FOUR CHORDS IN A MINOR KEY", minor = true),
        EarDrill("dc-passing", "Dictation", "Chord tones and passing tones", EarKind.CHORDTONE, listOf("2", "3"), "WHICH NOTE ISN'T IN THE CHORD"),
        EarDrill("dc-tempo", "Dictation", "How fast", EarKind.TEMPO, TEMPOS, "BPM OF A SHORT GROOVE"),
    )

    private fun scale(minor: Boolean) = Scales.byId(if (minor) "aeolian" else "major").semitones

    /** Scale step [i] (0 = tonic, 7 = the octave above, negative below) as MIDI above [tonic]. */
    fun step(tonic: Int, i: Int, minor: Boolean): Int {
        val s = scale(minor)
        return tonic + 12 * Math.floorDiv(i, 7) + s[Math.floorMod(i, 7)]
    }

    /** A melody answer: its note count, and "m" for a minor key (the mixed drill). */
    fun count(answer: String) = answer.removeSuffix("m").toInt()
    fun minor(drill: EarDrill, answer: String) = drill.minor || answer.endsWith("m")

    /**
     * Added 30 Oct 2026 (Christopher, more melodic dictation). [Claude] "by step": every move is to a neighbouring scale
     * note; "with a leap": at least one move of a 3rd or more. Drawn again until it fits.
     */
    fun melody(n: Int, random: Random, style: String): List<Int> {
        repeat(500) {
            val m = melody(n, random)
            val moves = m.zipWithNext { a, b -> kotlin.math.abs(b - a) }
            val ok = when (style) { "step" -> moves.all { it == 1 }; "leap" -> moves.any { it >= 2 }; else -> true }
            if (ok) return m
        }
        return melody(n, random)
    }

    /**
     * Chromatic approach notes: one inner note ([n] ≥ 6: two) becomes the note a half step from the next one, on the side
     * it came from, when that note is outside the key. [Claude]
     */
    fun chromatic(midis: List<Int>, tonic: Int, minor: Boolean, random: Random): List<Int> {
        val inKey = scale(minor).map { Math.floorMod(tonic + it, 12) }.toSet()
        val out = midis.toMutableList()
        // Two altered notes are never side by side, so each still leads into an unaltered note.
        val spots = (1 until out.size - 1).shuffled(random).fold(listOf<Int>()) { acc, i -> if (acc.none { kotlin.math.abs(it - i) <= 1 }) acc + i else acc }
            .take(if (out.size >= 6) 2 else 1)
        for (i in spots) {
            val next = out[i + 1]
            val from = out[i - 1]
            val c = if (from > next) next + 1 else next - 1
            if (Math.floorMod(c, 12) !in inKey && c != from) out[i] = c
        }
        return out
    }

    /** A phrase of [n] scale steps: starts on 1, 3 or 5, moves mostly by step, ends on the tonic (0 or 7). */
    fun melody(n: Int, random: Random): List<Int> {
        val out = arrayListOf(listOf(0, 2, 4)[random.nextInt(3)])
        while (out.size < n - 1) {
            val r = random.nextDouble()
            val size = when { r < 0.6 -> 1; r < 0.85 -> 2; r < 0.95 -> 3; else -> 4 }
            val up = random.nextBoolean()
            val next = (out.last() + if (up) size else -size).coerceIn(-3, 9)
            if (next != out.last()) out += next
        }
        val last = out.last()
        out += if (kotlin.math.abs(last - 7) < kotlin.math.abs(last)) 7 else 0
        return out
    }

    private val MAJOR_PROGRESSIONS = listOf(
        listOf(0, 5, 3, 4), listOf(0, 3, 4, 0), listOf(0, 4, 5, 3), listOf(0, 2, 3, 4), listOf(0, 1, 4, 0), listOf(0, 3, 5, 4),
    )
    private val MINOR_PROGRESSIONS = listOf(listOf(0, 3, 4, 0), listOf(0, 5, 2, 6), listOf(0, 6, 5, 4), listOf(0, 3, 6, 2))

    /** A four-chord progression by scale-step roots (0 = i or I). */
    fun progression(minor: Boolean, random: Random): List<Int> = (if (minor) MINOR_PROGRESSIONS else MAJOR_PROGRESSIONS).let { it[random.nextInt(it.size)] }

    /** A triad on scale step [root], voiced near the tonic. */
    fun triad(tonic: Int, root: Int, minor: Boolean): List<Int> = listOf(root, root + 2, root + 4).map { step(tonic, it, minor) }

    /** The bass for a progression: each root in the octave from two octaves under the tonic. */
    fun bassLine(tonic: Int, roots: List<Int>, minor: Boolean): List<Int> = roots.map { step(tonic - 24, it, minor) }

    /**
     * A chord-tone question: a triad on [root] and four melody notes over it, the one at [position] (2 or 3) a passing
     * tone (a step between two chord tones a third apart) or, when that can't fit, a neighbour tone.
     */
    fun passing(tonic: Int, root: Int, position: Int, minor: Boolean, random: Random): List<Int> {
        val up = random.nextBoolean()
        // Chord tones are root, root+2, root+4 (scale steps); pick a third to fill.
        val a = root + 2 * random.nextInt(2)
        val (from, to) = if (up) a to a + 2 else a + 2 to a
        val p = (from + to) / 2
        val other = listOf(root, root + 2, root + 4, root + 7).filter { it != from && it != to }.let { it[random.nextInt(it.size)] }
        val steps = if (position == 3) listOf(other, from, p, to) else listOf(from, p, to, other)
        return steps.map { step(tonic, it, minor) }
    }

    fun audio(q: EarQuestion, settings: EarSettings): EarAudio {
        val (src, a) = Ear.source(q.drill, q.answer)
        val t = q.tonic!!
        fun n(at: Double, len: Double, m: Int) = PlayNote(at, at + len, m, RefSound.VELOCITY)
        return when (src.kind) {
            EarKind.MELODY -> EarAudio(triad(t - 12, 0, minor(src, a)).map { n(0.0, 1.2, it) } + q.midis.mapIndexed { i, m -> n(1.6 + i * 0.6, 0.56, m) })
            EarKind.BASS -> EarAudio(q.chords.flatMapIndexed { i, ch -> (ch + q.midis[i]).map { n(i * 1.0, 0.95, it) } })
            EarKind.CHORDTONE -> EarAudio(q.chords.first().map { n(0.0, 3.4, it - 12) } + q.midis.mapIndexed { i, m -> n(0.4 + i * 0.7, 0.66, m) })
            else -> {
                val beat = 60.0 / q.bpm
                EarAudio((0 until 8).flatMap { b ->
                    val at = b * beat
                    if (b % 2 == 0) listOf(n(at, beat * 0.9, t - 24)) else q.chords.first().map { n(at, beat * 0.45, it) }
                })
            }
        }
    }

    fun question(drill: EarDrill, answer: String, full: String, rootPc: Int, root: PitchName, random: Random): EarQuestion {
        val tonic = RefSound.midis(rootPc, listOf(0), 60).first()
        val minor = drill.minor
        return when (drill.kind) {
            EarKind.MELODY -> {
                val m = minor(drill, answer)
                val style = when (drill.id) { "dc-mel-3-step" -> "step"; "dc-mel-3-leap" -> "leap"; else -> "" }
                fun draw() = melody(count(answer), random, style).map { step(tonic, it, m) }
                val inKey = scale(m).map { Math.floorMod(tonic + it, 12) }.toSet()
                // A chromatic melody is drawn again until a note outside the key fits.
                val notes = if (drill.id != "dc-mel-chromatic") draw()
                    else generateSequence { chromatic(draw(), tonic, m, random) }.take(100).firstOrNull { n -> n.any { Math.floorMod(it, 12) !in inKey } } ?: draw()
                EarQuestion(drill, full, notes, root, EarDirection.UP, tonic = tonic)
            }
            EarKind.BASS -> {
                val roots = progression(minor, random)
                EarQuestion(drill, full, bassLine(tonic, roots, minor), root, EarDirection.UP, tonic = tonic, chords = roots.map { triad(tonic - 12, it, minor) })
            }
            EarKind.CHORDTONE -> {
                val r = random.nextInt(7)
                val t = if (r > 3) tonic - 12 else tonic
                EarQuestion(drill, full, passing(t, r, answer.toInt(), minor, random), root, EarDirection.UP, tonic = t, chords = listOf(triad(t, r, minor)))
            }
            else -> EarQuestion(drill, full, emptyList(), root, EarDirection.UP, tonic = tonic, bpm = answer.toInt(), chords = listOf(triad(tonic - 12, 0, false)))
        }
    }

    /**
     * A played-back line against the question, note by note: right when every pitch class matches in order. [Claude] The
     * octave isn't marked, since a phone keyboard is small; the result says where an octave differs.
     */
    fun compare(q: EarQuestion, played: List<Int>): List<Boolean> = q.midis.mapIndexed { i, m -> played.getOrNull(i)?.let { Math.floorMod(it - m, 12) == 0 } ?: false }

    fun label(drill: EarDrill, answer: String) = when (drill.kind) {
        EarKind.CHORDTONE -> "Note $answer"
        EarKind.TEMPO -> "$answer BPM"
        else -> "${count(answer)} notes" + if (answer.endsWith("m")) " · minor" else if (drill.id == "dc-mel-mixed") " · major" else ""
    }

    fun explain(q: EarQuestion, names: (PitchName) -> String): String {
        val (src, a) = Ear.source(q.drill, q.answer)
        fun name(m: Int) = names(spell(q, m))
        return when (src.kind) {
            EarKind.MELODY, EarKind.BASS -> "${names(q.root)} ${if (minor(src, a)) "minor" else "major"}: " + q.midis.joinToString(" ") { name(it) } +
                if (src.kind == EarKind.BASS) " (under " + q.chords.joinToString(" – ") { names(spell(q, it.first())) + (if (isMinorTriad(it)) "m" else if (isDimTriad(it)) "dim" else "") } + ")." else "."
            EarKind.CHORDTONE -> {
                val ch = q.chords.first()
                val i = a.toInt() - 1
                "Note ${a} (${name(q.midis[i])}) isn't in the chord ${ch.joinToString(" ") { name(it) }}: it moves by step between ${name(q.midis[i - 1])} and ${name(q.midis[i + 1])}."
            }
            else -> "${q.bpm} BPM: a beat every ${60000 / q.bpm} ms."
        }
    }

    /** A note named in the question's key (its scale's spelling), else from the default table. */
    fun spell(q: EarQuestion, midi: Int): PitchName {
        val (src, a) = Ear.source(q.drill, q.answer)
        val notes = runCatching { ScalePages.page(Scales.byId(if (minor(src, a)) "aeolian" else "major"), q.root).notes }.getOrNull().orEmpty()
        return notes.firstOrNull { it.pitchClass == Math.floorMod(midi, 12) } ?: PitchClass(midi).defaultSpelling()
    }

    private fun isMinorTriad(t: List<Int>) = (t[1] - t[0]) == 3 && (t[2] - t[1]) == 4
    private fun isDimTriad(t: List<Int>) = (t[1] - t[0]) == 3 && (t[2] - t[1]) == 3
}
