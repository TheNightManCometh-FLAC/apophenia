package com.betterappmafia.apophenia.core.chord

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.spelling.Spelling

/**
 * Chord symbols spelled in the key (C4: "It is checked as you type and spelled in the key"): a root or slash bass
 * that is a note of the key's scale takes the scale's spelling (A♯7 in F major shows as B♭7, D♭/G♭ in E major as
 * C♯/F♯). A root outside the scale keeps the spelling you gave it, because there it is a choice (D♭7 as the tritone
 * substitute in C stays D♭7). The quality and extensions are left exactly as typed; text that doesn't read as a
 * chord is left alone.
 */
object ChordSpelling {
    /** [chord] with its root and bass (and a polychord's) spelled as the key spells them, where they're notes of its scale. */
    fun inKey(chord: Chord, key: KeyMode?): Chord {
        if (key == null || chord.isNoChord) return chord
        val scale = runCatching { Spelling.spell(key.tonic, key.scale.degrees).notes }.getOrElse { return chord }.associateBy { it.pitchClass }
        fun spelled(p: PitchName) = scale[p.pitchClass] ?: p
        val root = (chord.root as? ChordRoot.Name)?.let { ChordRoot.Name(spelled(it.name)) } ?: chord.root
        return chord.copy(root = root, bass = chord.bass?.let(::spelled), poly = chord.poly?.let { inKey(it, key) })
    }

    fun inKey(text: String, key: KeyMode?, dialect: ChordDialect = ChordDialect()): String {
        if (key == null) return text
        val parse = ChordParser.parse(text, dialect)
        val chord = parse.chord ?: return text
        if (chord.isNoChord || chord.poly != null) return text
        val scale = runCatching { Spelling.spell(key.tonic, key.scale.degrees).notes }.getOrElse { return text }.associateBy { it.pitchClass }
        fun spelled(p: PitchName) = scale[p.pitchClass] ?: p
        val root = (chord.root as? ChordRoot.Name)?.name ?: return text
        val newRoot = spelled(root)
        val newBass = chord.bass?.let(::spelled)
        if (newRoot == root && newBass == chord.bass) return text
        val names = dialect.names
        return ChordParser.name(newRoot, names) + parse.rest + (newBass?.let { "/" + ChordParser.name(it, names) } ?: "")
    }

    private val SUFFIX = mapOf(
        ChordKind.MAJOR to "", ChordKind.MINOR to "m", ChordKind.AUGMENTED to "+", ChordKind.DIMINISHED to "dim", ChordKind.DOMINANT to "7",
        ChordKind.MAJOR_SEVENTH to "maj7", ChordKind.MINOR_SEVENTH to "m7", ChordKind.DIMINISHED_SEVENTH to "dim7", ChordKind.AUGMENTED_SEVENTH to "7#5",
        ChordKind.HALF_DIMINISHED to "m7b5", ChordKind.MAJOR_MINOR to "m(maj7)", ChordKind.MAJOR_SIXTH to "6", ChordKind.MINOR_SIXTH to "m6",
        ChordKind.DOMINANT_NINTH to "9", ChordKind.MAJOR_NINTH to "maj9", ChordKind.MINOR_NINTH to "m9", ChordKind.DOMINANT_11TH to "11",
        ChordKind.MAJOR_11TH to "maj11", ChordKind.MINOR_11TH to "m11", ChordKind.DOMINANT_13TH to "13", ChordKind.MAJOR_13TH to "maj13",
        ChordKind.MINOR_13TH to "m13", ChordKind.SUSPENDED_SECOND to "sus2", ChordKind.SUSPENDED_FOURTH to "sus4", ChordKind.POWER to "5",
    )

    /**
     * A chord written out plainly (ASCII, the pop style), to show what a reading means: the catalog's own symbol when
     * the chord is a catalog quality (Csus2, Cadd9, C7alt), else the kind and its edits (C7b9#11, Cno3).
     */
    fun symbol(chord: Chord, names: ChordDialect.Names = ChordDialect.Names.ENGLISH): String {
        if (chord.isNoChord) return "N.C."
        val root = (chord.root as? ChordRoot.Name)?.name ?: return chord.typed ?: chord.kind.xml
        val quality = chord.quality?.let { q -> com.betterappmafia.apophenia.core.catalog.Chords.all.firstOrNull { it.id == q }?.symbols?.first()?.removePrefix("C") }
        val body = quality ?: run {
            var kind = SUFFIX[chord.kind] ?: chord.kind.xml
            var edits = chord.degrees
            if (chord.kind == ChordKind.SUSPENDED_FOURTH && edits.firstOrNull() == DegreeEdit.add(7)) {
                kind = if (edits.getOrNull(1) == DegreeEdit.add(9)) { edits = edits.drop(2); "9sus4" } else { edits = edits.drop(1); "7sus4" }
            }
            kind + edits.joinToString("") { e ->
                val acc = when (e.alter) { -2 -> "bb"; -1 -> "b"; 1 -> "#"; 2 -> "##"; else -> "" }
                when (e.type) {
                    DegreeType.SUBTRACT -> "no${e.value}"
                    DegreeType.ADD -> when {
                        e.value == 7 && e.alter == 1 -> "maj7"
                        acc.isEmpty() && chord.kind.degrees?.none { it.number == 7 } != false -> "add${e.value}"
                        acc.isEmpty() -> "(${e.value})"
                        else -> "$acc${e.value}"
                    }
                    DegreeType.ALTER -> "$acc${e.value}"
                }
            }
        }
        return ChordParser.name(root, names) + body + (chord.bass?.let { "/" + ChordParser.name(it, names) } ?: "") +
            (chord.poly?.let { " over " + symbol(it, names) }.orEmpty())
    }
}
