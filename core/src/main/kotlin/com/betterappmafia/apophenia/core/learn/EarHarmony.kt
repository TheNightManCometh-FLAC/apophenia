package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.degree.DegreeToken
import com.betterappmafia.apophenia.core.interval.Interval
import com.betterappmafia.apophenia.core.interval.plus
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.sound.PlayNote

/**
 * Harmony and tuning drills for the Ear (plan §9 "Harmony" and "Tuning and timbre"). A harmony question is a short
 * phrase of chords, each written as degree tokens above the key's tonic with the bass first, so the explanation spells
 * every note (Ger+6 in C is A♭ C E♭ F♯, never G♭).
 */
object EarHarmony {
    /** One chord of a phrase: its tokens above the tonic (bass first), shifted by [shift] semitones for a new key. */
    data class HChord(val tokens: List<DegreeToken>, val shift: Int = 0, val label: String = "", val upper: List<DegreeToken> = emptyList()) {
        val bass: Int get() = Math.floorMod(tokens.first().interval.semitones + shift, 12)
        val pcs: List<Int> get() = tokens.drop(1).map { Math.floorMod(it.interval.semitones + shift, 12) }.distinct()
    }

    private fun c(t: String, label: String = "", shift: Int = 0) = HChord(DegreeToken.list(t).let { listOf(it.first()) + it }, shift, label)
    /** A chord whose bass isn't its root: [bass] then the chord tones. */
    private fun cb(bass: String, t: String, label: String) = HChord(DegreeToken.list("$bass $t"), 0, label)

    private val MAJOR = mapOf(
        "I" to c("1 3 5", "I"), "ii" to c("2 4 6", "ii"), "iii" to c("3 5 7", "iii"), "IV" to c("4 6 1", "IV"),
        "V" to c("5 7 2", "V"), "vi" to c("6 1 3", "vi"), "vii°" to c("7 2 4", "vii°"),
    )
    private val MINOR = mapOf(
        "i" to c("1 b3 5", "i"), "ii°" to c("2 4 b6", "ii°"), "III" to c("b3 5 b7", "III"), "iv" to c("4 b6 1", "iv"),
        "v" to c("5 b7 2", "v"), "V" to c("5 7 2", "V"), "VI" to c("b6 1 b3", "VI"), "VII" to c("b7 2 4", "VII"),
    )
    private val SECONDARY = mapOf(
        "V7/ii" to (c("6 #1 3 5", "V7/ii") to MAJOR.getValue("ii")), "V7/iii" to (c("7 #2 #4 6", "V7/iii") to MAJOR.getValue("iii")),
        "V7/IV" to (c("1 3 5 b7", "V7/IV") to MAJOR.getValue("IV")), "V7/V" to (c("2 #4 6 1", "V7/V") to MAJOR.getValue("V")),
        "V7/vi" to (c("3 #5 7 2", "V7/vi") to MAJOR.getValue("vi")),
    )
    private val BORROWED = mapOf(
        "iv" to c("4 b6 1", "iv"), "bIII" to c("b3 5 b7", "♭III"), "bVI" to c("b6 1 b3", "♭VI"), "bVII" to c("b7 2 4", "♭VII"), "ii°" to c("2 4 b6", "ii°"),
    )
    private val PREDOMINANT = mapOf(
        "iv" to c("4 b6 1", "iv"), "N6" to cb("4", "b2 4 b6", "N⁶"),
        "It+6" to cb("b6", "b6 1 #4", "It⁺⁶"), "Fr+6" to cb("b6", "b6 1 2 #4", "Fr⁺⁶"), "Ger+6" to cb("b6", "b6 1 b3 #4", "Ger⁺⁶"),
    )
    private val SUBS = mapOf(
        "V7" to c("5 7 2 4", "V7"), "bII7" to c("b2 4 b6 b1", "♭II7"), "bVII7" to c("b7 2 4 b6", "♭VII7"),
    )
    /** Modulation targets from a major key: the new tonic in semitones above the old one, and whether it's minor. */
    private val TARGETS = mapOf("V" to (7 to false), "IV" to (5 to false), "vi" to (9 to true), "ii" to (2 to true), "iii" to (4 to true), "i" to (0 to true))

    val CADENCES = listOf("authentic", "plagal", "half", "deceptive")

    // -------------------------- added 30 Oct 2026 (Christopher: progressions, modal harmony and voicings, bitonal, reharmonization)

    /** A chord with a second chord stacked above it ([HChord.upper], voiced above the lower chord). */
    private fun st(lower: String, upper: String, label: String) = c(lower, label).copy(upper = DegreeToken.list(upper))

    private val SEVENTHS = mapOf(
        "Imaj7" to c("1 3 5 7", "Imaj7"), "ii7" to c("2 4 6 1", "ii7"), "iii7" to c("3 5 7 2", "iii7"), "IVmaj7" to c("4 6 1 3", "IVmaj7"),
        "V7" to c("5 7 2 4", "V7"), "vi7" to c("6 1 3 5", "vi7"), "iiø7" to c("2 4 b6 1", "iiø7"), "i7" to c("1 b3 5 b7", "i7"),
    )
    /** Whole progressions, named by their numerals; each answer is the numerals joined by "-". */
    private val PROG_MAJOR = listOf("I-IV-V-I", "I-V-vi-IV", "I-vi-IV-V", "vi-IV-I-V", "I-IV-vi-V", "ii-V-I")
    private val PROG_MINOR = listOf("i-iv-V-i", "i-VI-III-VII", "i-VII-VI-VII", "i-VI-VII-i", "i-iv-VII-III", "VI-VII-i-i")
    private val PROG_SEVENTHS = listOf("ii7-V7-Imaj7", "Imaj7-vi7-ii7-V7", "iii7-vi7-ii7-V7", "Imaj7-IVmaj7-iii7-vi7", "iiø7-V7-i7")

    /** A vamp that sets each mode: its tonic chord against the chord carrying its characteristic note. */
    private val MODAL = mapOf(
        "major" to listOf(c("1 3 5", "I"), c("4 6 1", "IV"), c("5 7 2", "V"), c("1 3 5", "I")),
        "dorian" to listOf(c("1 b3 5", "i"), c("4 6 1", "IV"), c("1 b3 5", "i"), c("4 6 1", "IV")),
        "phrygian" to listOf(c("1 b3 5", "i"), c("b2 4 b6", "♭II"), c("1 b3 5", "i"), c("b2 4 b6", "♭II")),
        "lydian" to listOf(c("1 3 5", "I"), c("2 #4 6", "II"), c("1 3 5", "I"), c("2 #4 6", "II")),
        "mixolydian" to listOf(c("1 3 5", "I"), c("b7 2 4", "♭VII"), c("1 3 5", "I"), c("b7 2 4", "♭VII")),
        "aeolian" to listOf(c("1 b3 5", "i"), c("b6 1 b3", "♭VI"), c("b7 2 4", "♭VII"), c("1 b3 5", "i")),
        "locrian" to listOf(c("1 b3 b5", "i°"), c("b2 4 b6", "♭II"), c("1 b3 b5", "i°"), c("b2 4 b6", "♭II")),
    )
    /** One voicing per mode over its tonic in the bass: 3rd and 7th, the 9th, and the mode's characteristic note. */
    private val MODAL_VOICINGS = mapOf(
        "major" to st("1", "3 7 2 4", "Imaj7(add 11)"), "dorian" to st("1", "b3 b7 2 6", "im13"),
        "phrygian" to st("1", "4 b7 b2 5", "isus4(♭9)"), "lydian" to st("1", "3 7 2 #4", "Imaj7(♯11)"),
        "mixolydian" to st("1", "3 b7 2 6", "I13"), "aeolian" to st("1", "b3 b7 2 b6", "im9(♭13)"),
        "locrian" to st("1", "b3 b5 b7 b2", "iø7(♭9)"),
    )
    /** Upper-structure triads over a dominant 7th (root, 3rd, ♭7 below): the triad's root above the bass names it. */
    private val UPPER = mapOf(
        "II" to st("1 3 b7", "2 #4 6", "II over 7 · 9 ♯11 13"), "bIII" to st("1 3 b7", "#2 5 b7", "♭III over 7 · ♯9"),
        "bV" to st("1 3 b7", "#4 #6 #1", "♭V over 7 · ♭9 ♯11"), "VI" to st("1 3 b7", "6 #1 3", "VI over 7 · ♭9 13"),
        "bVI" to st("1 3 b7", "b6 1 b3", "♭VI over 7 · ♯9 ♭13"), "bII" to st("1 3 b7", "b2 4 b6", "♭II over 7 · ♭9 11 ♭13"),
    )
    /** Two major triads at once: the lower on the tonic, the upper on the degree named. */
    private val POLYCHORDS = mapOf(
        "II" to st("1 3 5", "2 #4 6", "II over I"), "bIII" to st("1 3 5", "b3 5 b7", "♭III over I"), "III" to st("1 3 5", "3 #5 7", "III over I"),
        "bV" to st("1 3 5", "b5 b7 b2", "♭V over I"), "VI" to st("1 3 5", "6 #1 3", "VI over I"), "bVII" to st("1 3 5", "b7 2 4", "♭VII over I"),
    )
    /** I – IV – V7 – I, then again with one chord replaced: the answer is the replacement. */
    private val REHARM_BASE = listOf(c("1 3 5", "I"), c("4 6 1", "IV"), c("5 7 2 4", "V7"), c("1 3 5", "I"))
    private val REHARM = mapOf(
        "ii" to (1 to c("2 4 6", "ii")), "iv" to (1 to c("4 b6 1", "iv")), "II7" to (1 to c("2 #4 6 1", "II7")),
        "bII7" to (2 to c("b2 4 b6 b1", "♭II7")), "bVII7" to (2 to c("b7 2 4 b6", "♭VII7")), "vi" to (3 to c("6 1 3", "vi")),
    )

    val drills: List<EarDrill> = listOf(
        EarDrill("hm-major", "Harmony", "Chords in a major key", EarKind.HARMONY, MAJOR.keys.toList(), "A CADENCE, THEN ONE CHORD"),
        EarDrill("hm-minor", "Harmony", "Chords in a minor key", EarKind.HARMONY, MINOR.keys.toList(), "A CADENCE, THEN ONE CHORD", minor = true),
        EarDrill("hm-cadence", "Harmony", "Cadences", EarKind.HARMONY, CADENCES, "HOW THE PHRASE ENDS"),
        EarDrill("hm-secondary", "Harmony", "Secondary dominants", EarKind.HARMONY, SECONDARY.keys.toList(), "THE DOMINANT, THEN THE CHORD IT POINTS AT"),
        EarDrill("hm-borrowed", "Harmony", "Borrowed chords", EarKind.HARMONY, BORROWED.keys.toList(), "FROM THE PARALLEL MINOR: I, THE CHORD, I"),
        EarDrill("hm-predominant", "Harmony", "Neapolitan and augmented sixths", EarKind.HARMONY, PREDOMINANT.keys.toList(), "IN MINOR: i, THE CHORD, V, i", minor = true),
        EarDrill("hm-subs", "Harmony", "Tritone and backdoor substitutes", EarKind.HARMONY, SUBS.keys.toList(), "ii7, THE DOMINANT, Imaj7"),
        EarDrill("hm-modulation", "Harmony", "Where it modulates", EarKind.HARMONY, TARGETS.keys.toList(), "FROM A MAJOR KEY"),
        EarDrill("hm-prog-major", "Harmony", "Progressions in a major key", EarKind.HARMONY, PROG_MAJOR, "NAME THE WHOLE PROGRESSION"),
        EarDrill("hm-prog-minor", "Harmony", "Progressions in a minor key", EarKind.HARMONY, PROG_MINOR, "NAME THE WHOLE PROGRESSION", minor = true),
        EarDrill("hm-prog-sevenths", "Harmony", "Seventh-chord progressions", EarKind.HARMONY, PROG_SEVENTHS, "ii–V–I AND THE TURNAROUNDS"),
        EarDrill("hm-modal", "Harmony", "Modal harmony", EarKind.HARMONY, MODAL.keys.toList(), "A VAMP: WHICH MODE"),
        EarDrill("hm-upper", "Harmony", "Upper-structure triads", EarKind.HARMONY, UPPER.keys.toList(), "A TRIAD OVER A DOMINANT 7TH"),
        EarDrill("hm-polychord", "Harmony", "Polychords", EarKind.HARMONY, POLYCHORDS.keys.toList(), "A MAJOR TRIAD OVER ANOTHER"),
        EarDrill("hm-reharm", "Harmony", "Reharmonization", EarKind.HARMONY, REHARM.keys.toList(), "I–IV–V7–I, THEN WITH ONE CHORD CHANGED"),
        EarDrill("sc-modal-voicing", "Scales", "Modal voicings", EarKind.HARMONY, MODAL_VOICINGS.keys.toList(), "ONE CHORD: WHICH MODE IT SPELLS"),
        EarDrill("tt-just", "Tuning and timbre", "Equal or just", EarKind.TUNING, listOf("equal", "just"), "A MAJOR TRIAD IN PLAIN TONES"),
        EarDrill("tt-beats", "Tuning and timbre", "Beating", EarKind.BEATS, listOf("0", "2", "5"), "TWO TONES ALMOST IN UNISON"),
        EarDrill("tt-partials", "Tuning and timbre", "Which harmonic", EarKind.PARTIAL, listOf("2", "3", "4", "5", "6", "7", "8"), "A LOW NOTE, THEN ONE OF ITS HARMONICS"),
    )

    /** The chords of a question, after any cadence that sets the key. */
    fun phrase(drill: EarDrill, answer: String): List<HChord> {
        val maj = MAJOR
        return when (drill.id) {
            "hm-major" -> listOf(maj.getValue(answer))
            "hm-minor" -> listOf(MINOR.getValue(answer))
            "hm-cadence" -> when (answer) {
                "authentic" -> listOf(maj.getValue("I"), maj.getValue("IV"), maj.getValue("V"), maj.getValue("I"))
                "plagal" -> listOf(maj.getValue("I"), maj.getValue("vi"), maj.getValue("IV"), maj.getValue("I"))
                "half" -> listOf(maj.getValue("I"), maj.getValue("vi"), maj.getValue("IV"), maj.getValue("V"))
                else -> listOf(maj.getValue("I"), maj.getValue("IV"), maj.getValue("V"), maj.getValue("vi"))
            }
            "hm-secondary" -> SECONDARY.getValue(answer).let { (d, t) -> listOf(maj.getValue("I"), d, t) }
            "hm-borrowed" -> listOf(maj.getValue("I"), BORROWED.getValue(answer), maj.getValue("I"))
            "hm-predominant" -> listOf(MINOR.getValue("i"), PREDOMINANT.getValue(answer), MINOR.getValue("V"), MINOR.getValue("i"))
            "hm-subs" -> listOf(c("2 4 6 1", "ii7"), SUBS.getValue(answer), c("1 3 5 7", "Imaj7"))
            "hm-prog-major" -> answer.split('-').map { MAJOR.getValue(it) }
            "hm-prog-minor" -> answer.split('-').map { MINOR.getValue(it) }
            "hm-prog-sevenths" -> answer.split('-').map { SEVENTHS.getValue(it) }
            "hm-modal" -> MODAL.getValue(answer)
            "sc-modal-voicing" -> listOf(MODAL_VOICINGS.getValue(answer))
            "hm-upper" -> listOf(UPPER.getValue(answer))
            "hm-polychord" -> listOf(POLYCHORDS.getValue(answer))
            "hm-reharm" -> REHARM.getValue(answer).let { (at, ch) -> REHARM_BASE + REHARM_BASE.mapIndexed { i, b -> if (i == at) ch else b } }
            "hm-modulation" -> {
                val (shift, minor) = TARGETS.getValue(answer)
                val home = listOf(maj.getValue("I"), maj.getValue("IV"), maj.getValue("V"), maj.getValue("I"))
                val third = if (minor) "b3" else "3"
                val one = if (minor) "i" else "I"
                val four = if (minor) "iv" else "IV"
                val away = listOf(c("5 7 2 4", "V7", shift), c("1 $third 5", one, shift), c("4 ${if (minor) "b6" else "6"} 1", four, shift), c("5 7 2 4", "V7", shift), c("1 $third 5", one, shift))
                home + away
            }
            else -> emptyList()
        }
    }

    /** Whether the drill plays the key's cadence before its chords. */
    fun prefixed(drill: EarDrill) = drill.id == "hm-major" || drill.id == "hm-minor"

    /** A chord voiced on the piano: the bass two octaves under the tonic, the rest close around the tonic. */
    fun voice(ch: HChord, tonic: Int): List<Int> {
        val bass = tonic - 24 + ch.bass
        val lower = ch.pcs.map { pc -> (tonic - 7) + Math.floorMod(pc - (tonic - 7), 12) }.sorted()
        // A stacked chord's upper notes go up in order, each above the last, starting above the lower chord.
        var top = lower.maxOrNull() ?: (tonic - 13)
        val upper = ch.upper.map { t ->
            val pc = Math.floorMod(t.interval.semitones + ch.shift, 12)
            (top + 1 + Math.floorMod(pc - (top + 1), 12)).also { top = it }
        }
        return listOf(bass) + lower + upper
    }

    fun sound(drill: EarDrill, answer: String, tonic: Int): List<PlayNote> {
        val start = if (prefixed(drill)) Ear.cadence(tonic, drill.minor) else emptyList()
        val at = if (prefixed(drill)) 3.6 else 0.0
        val step = when (drill.id) { "hm-modulation" -> 0.8; "hm-upper", "hm-polychord", "sc-modal-voicing" -> 2.4; else -> 0.9 }
        val chords = phrase(drill, answer)
        // Reharmonization: a breath between the original and the changed one.
        fun t(i: Int) = at + i * step + if (drill.id == "hm-reharm" && i >= REHARM_BASE.size) 0.9 else 0.0
        val held = chords.flatMapIndexed { i, ch -> voice(ch, tonic).map { PlayNote(t(i), t(i) + step * 0.92, it, RefSound.VELOCITY) } }
        // A single stacked chord is held, then arpeggiated from the bass, then held again.
        if (chords.size == 1 && step > 2) {
            val notes = voice(chords[0], tonic)
            val arp = notes.mapIndexed { i, m -> PlayNote(at + step + i * 0.28, at + step + i * 0.28 + 0.6, m, RefSound.VELOCITY) }
            val again = at + step + notes.size * 0.28 + 0.5
            return start + held + arp + notes.map { PlayNote(again, again + step * 0.92, it, RefSound.VELOCITY) }
        }
        return start + held
    }

    fun label(drill: EarDrill, answer: String): String = when (drill.id) {
        "hm-cadence" -> answer.replaceFirstChar(Char::uppercase)
        "hm-borrowed" -> BORROWED.getValue(answer).label
        "hm-predominant" -> PREDOMINANT.getValue(answer).label
        "hm-subs" -> SUBS.getValue(answer).label + when (answer) { "bII7" -> " · tritone sub"; "bVII7" -> " · backdoor"; else -> "" }
        "hm-prog-major", "hm-prog-minor", "hm-prog-sevenths" -> phrase(drill, answer).joinToString(" – ") { it.label }
        "hm-modal", "sc-modal-voicing" -> com.betterappmafia.apophenia.core.catalog.Scales.byId(answer).names.first()
        "hm-upper" -> UPPER.getValue(answer).label
        "hm-polychord" -> POLYCHORDS.getValue(answer).label
        "hm-reharm" -> REHARM.getValue(answer).let { (at, ch) -> "${ch.label} for ${REHARM_BASE[at].label}" } +
            when (answer) { "bII7" -> " · tritone sub"; "bVII7" -> " · backdoor"; "II7" -> " · V7/V"; "iv" -> " · borrowed"; "vi" -> " · deceptive"; else -> "" }
        "hm-modulation" -> mapOf("V" to "To V", "IV" to "To IV", "vi" to "To vi (relative minor)", "ii" to "To ii", "iii" to "To iii", "i" to "To i (parallel minor)").getValue(answer)
        else -> answer
    }

    /** The phrase spelled on [tonic]: each chord's label and notes. */
    fun explain(drill: EarDrill, answer: String, tonic: PitchName, names: (PitchName) -> String): String {
        fun notes(ch: HChord): String {
            val base = if (ch.shift == 0) tonic else runCatching { tonic + Interval(intervalNumber(ch.shift), ch.shift) }.getOrNull() ?: tonic
            val lower = ch.tokens.drop(1).distinct().mapNotNull { t -> runCatching { names(base + t.interval) }.getOrNull() }.joinToString(" ")
            val upper = ch.upper.mapNotNull { t -> runCatching { names(base + t.interval) }.getOrNull() }.joinToString(" ")
            return if (upper.isEmpty()) lower else "$lower | $upper"
        }
        val chords = phrase(drill, answer)
        val what = when (drill.id) {
            "hm-cadence" -> mapOf("authentic" to "V – I", "plagal" to "IV – I", "half" to "ending on V", "deceptive" to "V – vi").getValue(answer)
            "hm-modulation" -> TARGETS.getValue(answer).let { (s, minor) ->
                val key = runCatching { tonic + Interval(intervalNumber(s), s) }.getOrNull()
                "the new key is ${key?.let(names) ?: ""} ${if (minor) "minor" else "major"}"
            }
            else -> ""
        }
        return label(drill, answer) + (if (what.isNotEmpty()) ": $what" else "") + ". " +
            chords.joinToString(" → ") { "${it.label} (${notes(it)})" } + "."
    }

    /** The interval number for a key shift from a major tonic (5 semitones = a 4th, 9 = a 6th…). */
    private fun intervalNumber(semitones: Int) = mapOf(0 to 1, 2 to 2, 4 to 3, 5 to 4, 7 to 5, 9 to 6).getValue(semitones)

    // --------------------------------------------------------------------------------------------- tuning and timbre

    fun hz(midi: Int) = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)

    fun tones(drill: EarDrill, answer: String, root: Int): List<Tone> = when (drill.kind) {
        EarKind.TUNING -> {
            val f = hz(root)
            val ratios = if (answer == "just") listOf(1.0, 5.0 / 4, 3.0 / 2) else listOf(1.0, Math.pow(2.0, 4 / 12.0), Math.pow(2.0, 7 / 12.0))
            ratios.map { Tone(0.0, 3.0, f * it) }
        }
        EarKind.BEATS -> {
            val f = hz(root)
            listOf(Tone(0.0, 3.5, f), Tone(0.0, 3.5, f + answer.toDouble()))
        }
        EarKind.PARTIAL -> {
            val f = hz(root)
            listOf(Tone(0.0, 1.3, f), Tone(1.5, 2.8, f * answer.toInt()), Tone(3.1, 4.6, f), Tone(3.1, 4.6, f * answer.toInt()))
        }
        else -> emptyList()
    }

    fun tuningLabel(drill: EarDrill, answer: String) = when (drill.kind) {
        EarKind.TUNING -> if (answer == "just") "Just" else "Equal"
        EarKind.BEATS -> when (answer) { "0" -> "No beats"; "2" -> "Slow · 2 a second"; else -> "Fast · 5 a second" }
        else -> mapOf("2" to "2nd · octave", "3" to "3rd · octave + 5th", "4" to "4th · two octaves", "5" to "5th · two octaves + M3",
            "6" to "6th · two octaves + 5th", "7" to "7th · near a ♭7", "8" to "8th · three octaves").getValue(answer)
    }

    fun tuningExplain(drill: EarDrill, answer: String) = when (drill.kind) {
        EarKind.TUNING -> if (answer == "just") "Just: the 3rd at 5:4 (386 ¢) and the 5th at 3:2 (702 ¢); the partials line up and the chord sits still."
            else "Equal: the 3rd at 400 ¢, 14 ¢ wider than 5:4, and the 5th at 700 ¢; the 3rd's partials beat against the root's."
        EarKind.BEATS -> if (answer == "0") "The two tones were the same frequency." else "The tones were $answer Hz apart, so they beat $answer times a second."
        else -> "Harmonic $answer: $answer times the low note's frequency" + if (answer == "7") ", about 31 ¢ flatter than an equal-tempered ♭7." else "."
    }
}
