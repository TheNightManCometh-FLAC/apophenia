package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.degree.DegreeToken
import com.betterappmafia.apophenia.core.interval.plus
import com.betterappmafia.apophenia.core.pitch.PitchName

/**
 * Ear › Triads (Christopher, 30 Oct 2026: "Yes, full triad set"): every three-note quality, the sus and modal triads
 * among them, and inversions of each one whose inversions are heard as inversions. An answer is a quality id, or
 * "id:k" for its k-th inversion. [Claude] Augmented and sus triads have no inversion drills: an augmented triad
 * inverted is another augmented triad, and sus2 inverted is a sus4 on another root, so the ear can't tell them apart.
 */
object EarTriads {
    data class Triad(val id: String, val name: String, val degrees: String) {
        val tokens: List<DegreeToken> get() = DegreeToken.list(degrees)
        val semitones: List<Int> get() = tokens.map { it.interval.semitones }
    }

    val all = listOf(
        Triad("maj", "Major", "1 3 5"), Triad("m", "Minor", "1 b3 5"),
        Triad("dim", "Diminished", "1 b3 b5"), Triad("aug", "Augmented", "1 3 #5"),
        Triad("sus2", "Sus2", "1 2 5"), Triad("sus4", "Sus4", "1 4 5"),
        Triad("phryg", "Phrygian (1 ♭2 5)", "1 b2 5"), Triad("lyd", "Lydian (1 ♯4 5)", "1 #4 5"),
        Triad("sus4b5", "Sus4♭5 (1 4 ♭5)", "1 4 b5"), Triad("locr", "Locrian (1 ♭2 ♭5)", "1 b2 b5"),
    )

    fun byId(id: String) = all.first { it.id == id }

    /** The quality and the inversion of an answer ("m:1" is a minor triad's 1st inversion). */
    fun parse(answer: String): Pair<Triad, Int> = byId(answer.substringBefore(':')) to (answer.substringAfter(':', "0").toInt())

    private fun inv(id: String) = (0..2).map { "$id:$it" }

    val drills = listOf(
        EarDrill("tr-majmin", "Triads", "Major or minor", EarKind.TRIAD, listOf("maj", "m"), "ROOT POSITION"),
        EarDrill("tr-dimaug", "Triads", "Diminished or augmented", EarKind.TRIAD, listOf("dim", "aug"), "ROOT POSITION"),
        EarDrill("tr-inv-maj", "Triads", "Major triad inversions", EarKind.TRIAD, inv("maj"), "WHICH NOTE IS IN THE BASS"),
        EarDrill("tr-inv-min", "Triads", "Minor triad inversions", EarKind.TRIAD, inv("m"), "WHICH NOTE IS IN THE BASS"),
        EarDrill("tr-inv-dim", "Triads", "Diminished triad inversions", EarKind.TRIAD, inv("dim"), "WHICH NOTE IS IN THE BASS"),
        EarDrill("tr-inv-all", "Triads", "Quality and inversion", EarKind.TRIAD, inv("maj") + inv("m") + inv("dim"), "MAJOR, MINOR OR DIMINISHED, AND WHICH INVERSION"),
        EarDrill("tr-sus2-phryg", "Triads", "Sus2 or Phrygian", EarKind.TRIAD, listOf("sus2", "phryg"), "1 2 5 OR 1 ♭2 5"),
        EarDrill("tr-sus4-lyd", "Triads", "Sus4 or Lydian", EarKind.TRIAD, listOf("sus4", "lyd"), "1 4 5 OR 1 ♯4 5"),
        EarDrill("tr-sus4b5-locr", "Triads", "Sus4♭5 or Locrian", EarKind.TRIAD, listOf("sus4b5", "locr"), "1 4 ♭5 OR 1 ♭2 ♭5"),
        EarDrill("tr-colour", "Triads", "Sus and modal triads", EarKind.TRIAD, listOf("sus2", "sus4", "phryg", "lyd", "sus4b5", "locr"), "NO 3RD: WHAT'S IN ITS PLACE"),
        EarDrill("tr-all", "Triads", "Every triad", EarKind.TRIAD, all.map { it.id }, "ALL TEN QUALITIES, ROOT POSITION"),
    )

    /** The triad on [rootPc] from about C3, inverted. */
    fun midis(rootPc: Int, answer: String): List<Int> {
        val (t, k) = parse(answer)
        return Ear.inversion(RefSound.midis(rootPc, t.semitones, 48), k)
    }

    private val INVERSIONS = listOf("Root position", "1st inversion", "2nd inversion")

    /** An inversions-only drill names the inversion; a drill of several qualities names the quality (and the inversion if it has any). */
    fun label(drill: EarDrill, answer: String): String {
        val (t, k) = parse(answer)
        val one = drill.answers.map { it.substringBefore(':') }.distinct().size == 1
        return when {
            one -> INVERSIONS[k]
            drill.answers.any { it.contains(':') } -> "${t.name} · ${INVERSIONS[k].lowercase()}"
            else -> t.name
        }
    }

    fun explain(answer: String, root: PitchName, names: (PitchName) -> String): String {
        val (t, k) = parse(answer)
        val notes = t.tokens.map { runCatching { names(root + it.interval) }.getOrDefault("?") }
        val played = notes.drop(k) + notes.take(k)
        fun pretty(d: String) = d.replace("b", "♭").replace("#", "♯")
        val bass = if (k == 0) "root" else pretty(t.degrees.split(" ")[k])
        return "${t.name} triad, ${pretty(t.degrees)}" + (if (k > 0) ", ${INVERSIONS[k].lowercase()} (the $bass in the bass)" else "") +
            ": ${played.joinToString(" ")} from the bottom."
    }

    fun pages(drill: EarDrill): List<String> {
        val ids = drill.answers.map { it.substringBefore(':') }.toSet()
        return buildList {
            if (ids.any { it in setOf("maj", "m", "dim", "aug") }) add("triad-four")
            if (ids.any { it !in setOf("maj", "m", "dim", "aug") }) add("sus")
            if (drill.answers.any { it.contains(':') }) add("inversions")
        }
    }
}
