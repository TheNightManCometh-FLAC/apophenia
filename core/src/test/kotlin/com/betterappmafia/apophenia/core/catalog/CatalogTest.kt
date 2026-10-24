package com.betterappmafia.apophenia.core.catalog

import com.betterappmafia.apophenia.core.ALL_35_ROOTS
import com.betterappmafia.apophenia.core.degrees
import com.betterappmafia.apophenia.core.interval.alterAbove
import com.betterappmafia.apophenia.core.names
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.spelling.Spelling
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogTest {

    // Semitone columns copied from docs/research/music-theory-for-a-theory-engine.md (sections 5, 6, 8.1),
    // so the degree tokens typed into the catalog are checked against an independent source.
    private val scaleSemitones = mapOf(
        "major" to "0 2 4 5 7 9 11", "dorian" to "0 2 3 5 7 9 10", "phrygian" to "0 1 3 5 7 8 10",
        "lydian" to "0 2 4 6 7 9 11", "mixolydian" to "0 2 4 5 7 9 10", "aeolian" to "0 2 3 5 7 8 10",
        "locrian" to "0 1 3 5 6 8 10",
        "melodic-minor" to "0 2 3 5 7 9 11", "dorian-flat2" to "0 1 3 5 7 9 10", "lydian-augmented" to "0 2 4 6 8 9 11",
        "lydian-dominant" to "0 2 4 6 7 9 10", "mixolydian-flat6" to "0 2 4 5 7 8 10", "locrian-sharp2" to "0 2 3 5 6 8 10",
        "altered" to "0 1 3 4 6 8 10",
        "harmonic-minor" to "0 2 3 5 7 8 11", "locrian-sharp6" to "0 1 3 5 6 9 10", "ionian-sharp5" to "0 2 4 5 8 9 11",
        "dorian-sharp4" to "0 2 3 6 7 9 10", "phrygian-dominant" to "0 1 4 5 7 8 10", "lydian-sharp2" to "0 3 4 6 7 9 11",
        "ultralocrian" to "0 1 3 4 6 8 9",
        "harmonic-major" to "0 2 4 5 7 8 11",
        "major-pentatonic" to "0 2 4 7 9", "minor-pentatonic" to "0 3 5 7 10", "minor-blues" to "0 3 5 6 7 10",
        "major-blues" to "0 2 3 4 7 9", "whole-tone" to "0 2 4 6 8 10", "half-whole" to "0 1 3 4 6 7 9 10",
        "whole-half" to "0 2 3 5 6 8 9 11", "bebop-dominant" to "0 2 4 5 7 9 10 11", "bebop-major" to "0 2 4 5 7 8 9 11",
        "bebop-minor-3" to "0 2 3 4 5 7 9 10", "bebop-minor-7" to "0 2 3 5 7 9 10 11",
        "double-harmonic" to "0 1 4 5 7 8 11", "hungarian-minor" to "0 2 3 6 7 8 11", "hungarian-major" to "0 3 4 6 7 9 10",
        "neapolitan-minor" to "0 1 3 5 7 8 11", "neapolitan-major" to "0 1 3 5 7 9 11", "persian" to "0 1 4 5 6 8 11",
        "augmented" to "0 3 4 7 8 11", "suspended-pentatonic" to "0 2 5 7 10", "hirajoshi" to "0 2 3 7 8",
        "in" to "0 1 5 7 8", "iwato" to "0 1 5 6 10", "chromatic" to "0 1 2 3 4 5 6 7 8 9 10 11",
        "messiaen-3" to "0 2 3 4 6 7 8 10 11", "messiaen-4" to "0 1 2 5 6 7 8 11", "messiaen-5" to "0 1 5 6 7 11",
        "messiaen-6" to "0 2 4 5 6 8 10 11", "messiaen-7" to "0 1 2 3 5 6 7 8 9 11",
    )

    private val chordSemitones = mapOf(
        "maj" to "0 4 7", "m" to "0 3 7", "dim" to "0 3 6", "aug" to "0 4 8", "sus2" to "0 2 7", "sus4" to "0 5 7",
        "5" to "0 7", "6" to "0 4 7 9", "m6" to "0 3 7 9", "69" to "0 4 7 9 14", "m69" to "0 3 7 9 14",
        "maj7" to "0 4 7 11", "7" to "0 4 7 10", "m7" to "0 3 7 10", "mMaj7" to "0 3 7 11", "m7b5" to "0 3 6 10",
        "dim7" to "0 3 6 9", "7#5" to "0 4 8 10", "maj7#5" to "0 4 8 11", "7b5" to "0 4 6 10", "7sus4" to "0 5 7 10",
        "9" to "0 4 7 10 14", "maj9" to "0 4 7 11 14", "m9" to "0 3 7 10 14", "mMaj9" to "0 3 7 11 14",
        "11" to "0 4 7 10 14 17", "m11" to "0 3 7 10 14 17", "maj7#11" to "0 4 7 11 14 18", "13" to "0 4 7 10 14 21",
        "maj13" to "0 4 7 11 14 21", "m13" to "0 3 7 10 14 17 21", "7b9" to "0 4 7 10 13", "7#9" to "0 4 7 10 15",
        "7#11" to "0 4 7 10 18", "7b13" to "0 4 7 10 20", "7alt" to "0 4 8 10 13 15 18", // research example spells b13; the catalog uses the chord-tone #5
        "add9" to "0 4 7 14", "madd9" to "0 3 7 14", "add11" to "0 4 7 17", "add2" to "0 2 4 7", "add4" to "0 4 5 7",
    )

    private fun ints(s: String) = s.split(' ').map(String::toInt)

    @Test
    fun `every scale's degrees match the research table's semitones`() {
        assertEquals(scaleSemitones.keys, Scales.all.map { it.id }.toSet() - setOf("lydian-flat3", "dorian-flat5",
            "phrygian-flat4", "mixolydian-flat2", "lydian-augmented-sharp2", "locrian-doubleflat7"))
        for ((id, semis) in scaleSemitones) assertEquals(ints(semis), Scales.byId(id).semitones, id)
    }

    @Test
    fun `every chord's degrees match the research table's semitones`() {
        assertEquals(chordSemitones.keys, Chords.all.map { it.id }.toSet())
        for ((id, semis) in chordSemitones) assertEquals(ints(semis), Chords.byId(id).semitones, id)
    }

    @Test
    fun `catalog ids are unique`() {
        assertEquals(Scales.all.size, Scales.all.map { it.id }.toSet().size)
        assertEquals(Chords.all.size, Chords.all.map { it.id }.toSet().size)
    }

    @Test
    fun `catalog text is ASCII only`() {
        val text = Scales.all.flatMap { it.names + it.id + listOfNotNull(it.disputed) } +
            Chords.all.flatMap { it.symbols + it.id + listOfNotNull(it.note) }
        for (t in text) assertTrue(t.all { it.code < 128 }, "non-ASCII: $t")
    }

    @Test
    fun `modes are rotations of their parent`() {
        assertEquals(degrees("1 2 b3 4 5 6 b7"), Scales.byId("dorian").degrees)
        assertEquals(degrees("1 b2 b3 b4 b5 b6 b7"), Scales.byId("altered").degrees)
        assertEquals(degrees("1 b2 b3 b4 b5 b6 bb7"), Scales.byId("ultralocrian").degrees)
        assertEquals(degrees("1 2 3 #4 #5 6 7"), Scales.byId("lydian-augmented").degrees)
        assertEquals(7 * 4, Scales.all.count { it.modeIndex != null })
    }

    @Test
    fun `seven-note modes spell one letter per degree and the rest are degree labelled`() {
        assertEquals(SpellingKind.HEPTATONIC, Scales.byId("persian").spellingKind)
        assertEquals(SpellingKind.DEGREE_LABELLED, Scales.byId("whole-half").spellingKind)
        assertEquals(SpellingKind.DEGREE_LABELLED, Scales.byId("minor-pentatonic").spellingKind)
    }

    @Test
    fun `the research table's example spellings come out exactly`() {
        fun scale(id: String, root: String) = Spelling.spell(PitchName.parse(root), Scales.byId(id).degrees).notes
        assertEquals(names("C Db D# E F# G A Bb"), scale("half-whole", "C"))
        assertEquals(names("C D Eb F Gb Ab Bbb B"), scale("whole-half", "C"))
        assertEquals(names("C D E F# G# Bb"), scale("whole-tone", "C"))
        assertEquals(names("A B C D E F G#"), scale("harmonic-minor", "A"))
        assertEquals(names("E F G# A B C D"), scale("phrygian-dominant", "E"))
        assertEquals(names("D E F G# A B C"), scale("dorian-sharp4", "D"))
        assertEquals(names("C D# E G G# B"), scale("augmented", "C"))
        assertEquals(names("C Db E F Gb Ab B"), scale("persian", "C"))
        fun chord(id: String, root: String) = Spelling.spell(PitchName.parse(root), Chords.byId(id).degrees).notes
        assertEquals(names("C Eb Gb Bbb"), chord("dim7", "C"))
        assertEquals(names("E G# B D Fx"), chord("7#9", "E"))
        assertEquals(names("C E G B D F#"), chord("maj7#11", "C"))
    }

    @Test
    fun `voicings drop the natural fifth first and never the guide tones`() {
        assertEquals(degrees("5 1"), Chords.byId("7").omitOrder)
        assertEquals(degrees("3 b7"), Chords.byId("7").essential)
        assertEquals(degrees("5"), Chords.byId("maj").omitOrder)
        assertEquals(degrees("1 b3 b5"), Chords.byId("dim").essential)
        assertEquals(degrees("1 b3 b5 bb7"), Chords.byId("dim7").essential)
        for (c in Chords.all) for (guide in degrees("3 b3 b7 7")) {
            if (c.id != "11") assertTrue(guide !in c.omitOrder, "${c.id} may drop its guide tone $guide")
        }
    }

    @Test
    fun `every catalog scale and chord on all 35 roots spells with the right pitch classes and no triple accidentals`() {
        val sets = Scales.all.map { it.id to it.degrees } + Chords.all.map { it.id to it.degrees }
        var respelled = 0
        for (root in ALL_35_ROOTS) for ((id, ds) in sets) {
            val s = Spelling.spell(root, ds)
            if (s.respelledFrom != null) {
                respelled++
                // Respelling happens only when the requested root really needed a triple accidental.
                assertTrue(ds.any { alterAbove(root, it.interval) !in -2..2 }, "$root $id respelled needlessly")
                assertEquals(root.pitchClass, s.root.pitchClass, "$root $id respelled to another pitch")
            } else {
                assertNull(s.respelledFrom)
                assertEquals(root, s.root)
            }
            assertEquals(ds.size, s.notes.size)
            for ((note, d) in s.notes.zip(ds)) {
                assertEquals(Math.floorMod(root.pitchClass + d.interval.semitones, 12), note.pitchClass, "$root $id $d")
                assertEquals(s.root.letter + (d.number - 1), note.letter, "$root $id $d")
            }
        }
        // Natural roots never need respelling for these sets (Cb ultralocrian, by contrast, needs Abbb and is respelled).
        assertTrue(respelled > 0)
        for (root in ALL_35_ROOTS.filter { it.alter == 0 }) for ((id, ds) in sets) {
            assertNull(Spelling.spell(root, ds).respelledFrom, "$root $id")
        }
    }
}
