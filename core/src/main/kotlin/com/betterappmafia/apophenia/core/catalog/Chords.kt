package com.betterappmafia.apophenia.core.catalog

import com.betterappmafia.apophenia.core.degree.DegreeToken

/**
 * One chord quality; roots are applied at runtime. [symbols] lists the common written forms,
 * the app default first. [omitOrder] is what a voicing drops first (natural 5th, then the root);
 * [essential] is never dropped. Slash chords, polychords and quartal/cluster voicings are
 * structures, not catalog rows.
 */
data class ChordDef(
    val id: String,
    val symbols: List<String>,
    val degrees: List<DegreeToken>,
    val family: String,
    val omitOrder: List<DegreeToken>,
    val note: String? = null,
) {
    val essential: List<DegreeToken> get() = degrees - omitOrder.toSet()

    /** Semitones above the root, in degree order (compound degrees keep their octave). */
    val semitones: List<Int> get() = degrees.map { it.interval.semitones }
}

object Chords {

    private fun d(s: String) = DegreeToken.list(s)

    private val ROOT = DegreeToken(1)
    private val FIFTH = DegreeToken(5)

    /**
     * Default omit order: the natural 5th first, then the root, and the root only when the chord
     * has four or more tones (a triad without its root stops being that triad).
     */
    private fun defaultOmit(degrees: List<DegreeToken>) = buildList {
        if (FIFTH in degrees) add(FIFTH)
        if (degrees.size >= 4) add(ROOT)
    }

    private fun chord(id: String, symbols: List<String>, degrees: String, family: String, omit: String? = null, note: String? = null): ChordDef {
        val ds = d(degrees)
        return ChordDef(id, symbols, ds, family, omit?.let { if (it.isBlank()) emptyList() else d(it) } ?: defaultOmit(ds), note)
    }

    val all: List<ChordDef> = listOf(
        chord("maj", listOf("C", "CM", "Cmaj"), "1 3 5", "triad"),
        chord("m", listOf("Cm", "Cmin", "Cmi", "C-"), "1 b3 5", "triad"),
        chord("dim", listOf("Cdim", "Co", "Cm(b5)"), "1 b3 b5", "triad", note = "A lone o (the degree sign) means the triad in pop charts but the diminished 7th in iReal Pro and many jazz charts."),
        chord("aug", listOf("Caug", "C+"), "1 3 #5", "triad", note = "C+ is the augmented triad; C7+ is read as C7#5 by most, but as Cmaj7 in some Latin and European charts."),
        chord("sus2", listOf("Csus2"), "1 2 5", "sus", omit = "", note = "C2 means sus2 in jazz charts but add9 in many pop charts; the app reads it by your chord dialect (pop or jazz) and shows the other reading while you type."),
        chord("sus4", listOf("Csus4", "Csus"), "1 4 5", "sus", omit = ""),
        chord("5", listOf("C5"), "1 5", "power", omit = ""),
        chord("6", listOf("C6"), "1 3 5 6", "sixth"),
        chord("m6", listOf("Cm6"), "1 b3 5 6", "sixth", note = "The 6th of m6 is major."),
        chord("69", listOf("C6/9", "C69"), "1 3 5 6 9", "sixth", note = "No 7th; never a slash chord."),
        chord("m69", listOf("Cm6/9", "Cm69"), "1 b3 5 6 9", "sixth"),
        chord("maj7", listOf("Cmaj7", "CM7", "C^7", "C^"), "1 3 5 7", "seventh", note = "Cmaj on its own is ambiguous: a major triad to some, a major 7th to others. The triangle (^) and M7 always mean the major 7th."),
        chord("7", listOf("C7"), "1 3 5 b7", "dominant"),
        chord("m7", listOf("Cm7", "C-7"), "1 b3 5 b7", "seventh"),
        chord("mMaj7", listOf("Cm(maj7)", "CmM7", "C-^7"), "1 b3 5 7", "seventh"),
        chord("m7b5", listOf("Cm7b5", "Ch7", "Ch"), "1 b3 b5 b7", "seventh", note = "Half-diminished: written Cm7b5 in pop and Ch7 (o with a slash) in jazz."),
        chord("dim7", listOf("Cdim7", "Co7"), "1 b3 b5 bb7", "seventh", omit = "", note = "Symmetric: four roots share these keys."),
        chord("7#5", listOf("C7#5", "C+7", "Caug7"), "1 3 #5 b7", "dominant", note = "Also written C7(b13) when the 5th is heard as a 13th; C7+ usually means this chord, but some charts mean Cmaj7."),
        chord("maj7#5", listOf("Cmaj7#5", "C+maj7"), "1 3 #5 7", "seventh"),
        chord("7b5", listOf("C7b5"), "1 3 b5 b7", "dominant"),
        chord("7sus4", listOf("C7sus4", "C7sus"), "1 4 5 b7", "sus", note = "Written 7sus4, never sus7. Jazz charts often write C9sus or C13sus for the fuller sound."),
        chord("9", listOf("C9"), "1 3 5 b7 9", "dominant"),
        chord("maj9", listOf("Cmaj9"), "1 3 5 7 9", "seventh"),
        chord("m9", listOf("Cm9"), "1 b3 5 b7 9", "seventh"),
        chord("mMaj9", listOf("Cm(maj9)"), "1 b3 5 7 9", "seventh"),
        chord("11", listOf("C11"), "1 3 5 b7 9 11", "dominant", omit = "3 5 1",
            note = "The 3rd is normally omitted (it clashes with the 11); close to C9sus4 and Bb/C."),
        chord("m11", listOf("Cm11"), "1 b3 5 b7 9 11", "seventh", omit = "5 9 1"),
        chord("maj7#11", listOf("Cmaj7#11"), "1 3 5 7 9 #11", "seventh", omit = "5 9 1",
            note = "The natural 11 is avoided over a major 3rd."),
        chord("13", listOf("C13"), "1 3 5 b7 9 13", "dominant", omit = "5 9 1",
            note = "The 11 is omitted over the major 3rd; core 1 3 b7 13."),
        chord("maj13", listOf("Cmaj13"), "1 3 5 7 9 13", "seventh", omit = "5 9 1"),
        chord("m13", listOf("Cm13"), "1 b3 5 b7 9 11 13", "seventh", omit = "5 11 9 1"),
        chord("7b9", listOf("C7b9", "C7(b9)"), "1 3 5 b7 b9", "dominant-altered"),
        chord("7#9", listOf("C7#9", "C7(#9)"), "1 3 5 b7 #9", "dominant-altered"),
        chord("7#11", listOf("C7#11", "C7(#11)"), "1 3 5 b7 #11", "dominant-altered"),
        chord("7b13", listOf("C7b13", "C7(b13)"), "1 3 5 b7 b13", "dominant-altered", note = "The 5th is usually omitted."),
        chord("7alt", listOf("C7alt", "Calt"), "1 3 #5 b7 b9 #9 #11", "dominant-altered", omit = "1",
            note = "Any of b9 #9 #11 b13 over 1 3 b7, with no natural 5, 9 or 13; a voicing picks a subset. " +
                "Stored in its chord-tone spelling (#5, sounding as b13); the degree set is an encoder choice."),
        chord("add9", listOf("Cadd9"), "1 3 5 9", "add", note = "No 7th. Pop charts sometimes write it C2, which jazz charts read as sus2."),
        chord("madd9", listOf("Cm(add9)", "Cmadd9"), "1 b3 5 9", "add"),
        chord("add11", listOf("Cadd11"), "1 3 5 11", "add"),
        chord("add2", listOf("Cadd2"), "1 2 3 5", "add", note = "The added note sits inside the triad."),
        chord("add4", listOf("Cadd4"), "1 3 4 5", "add", note = "The added note sits inside the triad."),
    )

    fun byId(id: String): ChordDef = all.firstOrNull { it.id == id } ?: throw NoSuchElementException("no chord '$id'")
}
