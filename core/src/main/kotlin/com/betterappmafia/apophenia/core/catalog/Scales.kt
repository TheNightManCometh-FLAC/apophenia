package com.betterappmafia.apophenia.core.catalog

import com.betterappmafia.apophenia.core.degree.DegreeToken
import com.betterappmafia.apophenia.core.interval.Interval

/** Bumped whenever a catalog entry's id, degrees or meaning changes. */
const val CATALOG_VERSION = 1

/**
 * How a scale is spelled: [HEPTATONIC] scales take one letter per degree, so their spelling is
 * unique; [DEGREE_LABELLED] scales (5, 6, 8 or 12 notes) are spelled from their degree tokens.
 */
enum class SpellingKind { HEPTATONIC, DEGREE_LABELLED }

/**
 * One scale or mode. [names] lists the primary name first. [modeOf]/[modeIndex] link a mode to
 * its parent. [disputed] records where sources disagree, so the Theory pages can say so.
 */
data class ScaleDef(
    val id: String,
    val names: List<String>,
    val degrees: List<DegreeToken>,
    val family: String,
    val modeOf: String? = null,
    val modeIndex: Int? = null,
    val disputed: String? = null,
) {
    val spellingKind: SpellingKind
        get() = if (degrees.size == 7 && degrees.map { it.number }.toSet().size == 7) SpellingKind.HEPTATONIC
        else SpellingKind.DEGREE_LABELLED

    /** Semitones above the root, in degree order. */
    val semitones: List<Int> get() = degrees.map { Math.floorMod(it.interval.semitones, 12) }
}

object Scales {

    /**
     * Mode [index] (1-based) of a seven-note parent: rotate the parent so its [index]th degree is
     * the new 1, keeping one letter per degree.
     */
    fun mode(parent: List<DegreeToken>, index: Int): List<DegreeToken> {
        require(parent.size == 7) { "modes are derived from seven-note parents" }
        require(index in 1..7) { "mode index must be 1..7, was $index" }
        val start = parent[index - 1].interval.semitones
        return (0 until 7).map { i ->
            val d = parent[(index - 1 + i) % 7]
            val semis = Math.floorMod(d.interval.semitones - start, 12)
            val number = i + 1
            DegreeToken(number, semis - Interval.referenceSemitones(number))
        }
    }

    private fun d(s: String) = DegreeToken.list(s)

    private val MAJOR = d("1 2 3 4 5 6 7")
    private val MELODIC_MINOR = d("1 2 b3 4 5 6 7")
    private val HARMONIC_MINOR = d("1 2 b3 4 5 b6 7")
    private val HARMONIC_MAJOR = d("1 2 3 4 5 b6 7")
    private const val MESSIAEN_NOTE = "Messiaen defined his modes as sets of notes, not spellings; this is one spelling of several."

    private fun modes(parentId: String, family: String, parent: List<DegreeToken>, vararg modes: Pair<String, List<String>>) =
        modes.mapIndexed { i, (id, n) ->
            ScaleDef(
                id = id,
                names = n, degrees = mode(parent, i + 1), family = family,
                modeOf = if (i == 0) null else parentId, modeIndex = i + 1,
            )
        }

    val all: List<ScaleDef> = buildList {
        addAll(modes("major", "major", MAJOR,
            "major" to listOf("Ionian", "Major"), "dorian" to listOf("Dorian"), "phrygian" to listOf("Phrygian"),
            "lydian" to listOf("Lydian"), "mixolydian" to listOf("Mixolydian"),
            "aeolian" to listOf("Aeolian", "Natural minor"), "locrian" to listOf("Locrian")))
        addAll(modes("melodic-minor", "melodic-minor", MELODIC_MINOR,
            "melodic-minor" to listOf("Melodic minor", "Jazz minor"), "dorian-flat2" to listOf("Dorian b2", "Phrygian #6"),
            "lydian-augmented" to listOf("Lydian augmented"), "lydian-dominant" to listOf("Lydian dominant", "Overtone"),
            "mixolydian-flat6" to listOf("Mixolydian b6", "Aeolian dominant"),
            "locrian-sharp2" to listOf("Locrian #2", "Half-diminished"), "altered" to listOf("Altered", "Super Locrian")))
        addAll(modes("harmonic-minor", "harmonic-minor", HARMONIC_MINOR,
            "harmonic-minor" to listOf("Harmonic minor"), "locrian-sharp6" to listOf("Locrian #6"),
            "ionian-sharp5" to listOf("Ionian #5"), "dorian-sharp4" to listOf("Dorian #4", "Ukrainian Dorian"),
            "phrygian-dominant" to listOf("Phrygian dominant", "Spanish Phrygian"), "lydian-sharp2" to listOf("Lydian #2"),
            "ultralocrian" to listOf("Ultralocrian", "Superlocrian bb7")))
        addAll(modes("harmonic-major", "harmonic-major", HARMONIC_MAJOR,
            "harmonic-major" to listOf("Harmonic major"), "dorian-flat5" to listOf("Dorian b5"), "phrygian-flat4" to listOf("Phrygian b4"),
            "lydian-flat3" to listOf("Lydian b3"), "mixolydian-flat2" to listOf("Mixolydian b2"),
            "lydian-augmented-sharp2" to listOf("Lydian augmented #2"), "locrian-doubleflat7" to listOf("Locrian bb7")).map {
            if (it.modeIndex == 1) it else it.copy(disputed = "Harmonic-major mode names vary between sources; verify before the Theory pages ship.")
        })

        add(ScaleDef("major-pentatonic", listOf("Major pentatonic"), d("1 2 3 5 6"), "pentatonic"))
        add(ScaleDef("minor-pentatonic", listOf("Minor pentatonic"), d("1 b3 4 5 b7"), "pentatonic"))
        add(ScaleDef("suspended-pentatonic", listOf("Suspended pentatonic", "Egyptian"), d("1 2 4 5 b7"), "pentatonic"))
        add(ScaleDef("minor-blues", listOf("Minor blues", "Blues"), d("1 b3 4 b5 5 b7"), "blues",
            disputed = "The added note is spelled b5 or #4 depending on the source."))
        add(ScaleDef("major-blues", listOf("Major blues"), d("1 2 b3 3 5 6"), "blues"))
        add(ScaleDef("chromatic", listOf("Chromatic"), d("1 b2 2 b3 3 4 #4 5 b6 6 b7 7"), "symmetric",
            disputed = "Spelling by convention: sharps ascending, flats descending."))
        add(ScaleDef("whole-tone", listOf("Whole tone", "Messiaen mode 1"), d("1 2 3 #4 #5 b7"), "symmetric"))
        add(ScaleDef("half-whole", listOf("Half-whole octatonic", "Dominant diminished", "Messiaen mode 2"), d("1 b2 #2 3 #4 5 6 b7"), "symmetric"))
        add(ScaleDef("whole-half", listOf("Whole-half octatonic", "Diminished"), d("1 2 b3 4 b5 b6 bb7 7"), "symmetric"))
        add(ScaleDef("augmented", listOf("Augmented", "Augmented hexatonic"), d("1 #2 3 5 #5 7"), "symmetric"))
        add(ScaleDef("messiaen-3", listOf("Messiaen mode 3"), d("1 2 b3 3 #4 5 #5 #6 7"), "symmetric", disputed = MESSIAEN_NOTE))
        add(ScaleDef("messiaen-4", listOf("Messiaen mode 4"), d("1 b2 2 4 #4 5 b6 7"), "symmetric", disputed = MESSIAEN_NOTE))
        add(ScaleDef("messiaen-5", listOf("Messiaen mode 5"), d("1 b2 4 #4 5 7"), "symmetric", disputed = MESSIAEN_NOTE))
        add(ScaleDef("messiaen-6", listOf("Messiaen mode 6"), d("1 2 3 4 #4 #5 #6 7"), "symmetric", disputed = MESSIAEN_NOTE))
        add(ScaleDef("messiaen-7", listOf("Messiaen mode 7"), d("1 b2 2 b3 4 #4 5 b6 6 7"), "symmetric", disputed = MESSIAEN_NOTE))
        add(ScaleDef("bebop-dominant", listOf("Bebop dominant"), d("1 2 3 4 5 6 b7 7"), "bebop"))
        add(ScaleDef("bebop-major", listOf("Bebop major"), d("1 2 3 4 5 #5 6 7"), "bebop"))
        add(ScaleDef("bebop-minor-3", listOf("Bebop minor (Dorian + natural 3)"), d("1 2 b3 3 4 5 6 b7"), "bebop",
            disputed = "Sources disagree on bebop minor: Dorian + natural 3 or Dorian + natural 7. Both are stored."))
        add(ScaleDef("bebop-minor-7", listOf("Bebop minor (Dorian + natural 7)"), d("1 2 b3 4 5 6 b7 7"), "bebop",
            disputed = "Sources disagree on bebop minor: Dorian + natural 3 or Dorian + natural 7. Both are stored."))
        add(ScaleDef("double-harmonic", listOf("Double harmonic major", "Byzantine", "Arabic"), d("1 b2 3 4 5 b6 7"), "other"))
        add(ScaleDef("hungarian-minor", listOf("Hungarian minor"), d("1 2 b3 #4 5 b6 7"), "other"))
        add(ScaleDef("hungarian-major", listOf("Hungarian major"), d("1 #2 3 #4 5 6 b7"), "other"))
        add(ScaleDef("neapolitan-minor", listOf("Neapolitan minor"), d("1 b2 b3 4 5 b6 7"), "other"))
        add(ScaleDef("neapolitan-major", listOf("Neapolitan major"), d("1 b2 b3 4 5 6 7"), "other"))
        add(ScaleDef("persian", listOf("Persian"), d("1 b2 3 4 b5 b6 7"), "other"))
        add(ScaleDef("hirajoshi", listOf("Hirajoshi"), d("1 2 b3 5 b6"), "japanese",
            disputed = "Hirajoshi versions differ between sources; this is one version."))
        add(ScaleDef("in", listOf("In", "Miyako-bushi"), d("1 b2 4 5 b6"), "japanese"))
        add(ScaleDef("iwato", listOf("Iwato"), d("1 b2 4 b5 b7"), "japanese"))
    }

    fun byId(id: String): ScaleDef = all.firstOrNull { it.id == id } ?: throw NoSuchElementException("no scale '$id'")
}
