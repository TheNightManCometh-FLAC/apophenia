package com.betterappmafia.apophenia.core.learn

/** Explicit, checked links into the existing written lessons. Mixed drills offer their source topics. */
object EarLearning {
    fun pages(drill: EarDrill, answer: String? = null): List<String> {
        val sources = if (answer != null) listOf(Ear.source(drill, answer).first)
            else drill.answers.map { Ear.source(drill, it).first }.distinctBy { it.id }
        return sources.flatMap { d ->
            val id = d.id.substringAfterLast(':')
            when (d.kind) {
                EarKind.INTERVAL -> listOf("iv-melodic", when (id) {
                    "iv-compound" -> "iv-compound"
                    "iv-4-5" -> "iv-tritone-what"
                    "iv-perfect", "iv-unison-octave" -> "iv-perfect"
                    "iv-simple" -> "iv-all"
                    else -> "iv-major-minor"
                })
                EarKind.SING -> listOf("iv-melodic", "voice-ranges")
                EarKind.CHORD -> listOf(when (id) {
                    "ch-triads" -> "triad-four"
                    "ch-sus" -> "sus"
                    "ch-7-basic", "ch-7-all" -> "sevenths"
                    "ch-6-add" -> "sixths"
                    else -> "extensions"
                })
                EarKind.INVERSION -> listOf("inversions")
                EarKind.VOICING -> listOf("drop", "spread")
                EarKind.NOTE -> listOf("names-letters")
                EarKind.OCTAVE -> listOf("names-octaves")
                EarKind.CENTS -> listOf("tuning-cents")
                EarKind.MATCH -> listOf("pitch-frequency", "tuning-cents")
                EarKind.DEGREE -> listOf("scale-degrees", if (d.minor) "minor-natural" else "scale-major")
                EarKind.MODE, EarKind.CHARACTER -> listOf(when (id) {
                    "mode-melodic" -> "modes-melodic"
                    "mode-harmonic" -> "modes-harmonic"
                    "mode-symmetric" -> "symmetric"
                    else -> "modes-seven"
                })
                EarKind.HARMONY -> listOf(when (id) {
                    "hm-minor" -> "numerals-minor"
                    "hm-cadence" -> "cadences"
                    "hm-secondary" -> "secondary"
                    "hm-borrowed" -> "mixture"
                    "hm-predominant" -> "neapolitan"
                    "hm-subs" -> "jazz-subs"
                    "hm-modulation" -> "modulation-pivot"
                    else -> "numerals-major"
                }) + if (id == "hm-predominant") listOf("aug-sixths") else emptyList()
                EarKind.TUNING -> listOf("tuning-equal", "beats-tune")
                EarKind.BEATS -> listOf("beats-what")
                EarKind.PARTIAL -> listOf("timbre-harmonics")
                EarKind.MELODY, EarKind.OWN_PHRASE -> listOf("contour", "motif")
                EarKind.BASS -> listOf("bass-neck", "changes")
                EarKind.CHORDTONE -> listOf("nct-passing", "nct-other")
                EarKind.TEMPO -> listOf("pulse")
                EarKind.RHYTHM, EarKind.TAPBACK -> listOf("values", "meter-signatures", "tuplets", "odd")
                EarKind.SWING -> listOf("swing")
                EarKind.OWN_CHORD -> listOf("triad-four", "numerals-major")
                EarKind.OWN_KEY -> listOf("scale-major", "minor-natural", "modes-seven")
            }
        }.distinct()
    }
}
