package com.betterappmafia.apophenia.core.chord

import com.betterappmafia.apophenia.core.catalog.Chords
import com.betterappmafia.apophenia.core.chord.DegreeEdit.Companion.add
import com.betterappmafia.apophenia.core.chord.DegreeEdit.Companion.alter
import com.betterappmafia.apophenia.core.chord.DegreeEdit.Companion.subtract
import com.betterappmafia.apophenia.core.degree.DegreeToken
import com.betterappmafia.apophenia.core.names
import com.betterappmafia.apophenia.core.pitch.PitchName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChordTest {

    @Test
    fun `there are exactly the 33 MusicXML kind values, spelled as the spec spells them`() {
        assertEquals(33, ChordKind.entries.size)
        val spec = "major minor augmented diminished dominant major-seventh minor-seventh diminished-seventh " +
            "augmented-seventh half-diminished major-minor major-sixth minor-sixth dominant-ninth major-ninth " +
            "minor-ninth dominant-11th major-11th minor-11th dominant-13th major-13th minor-13th suspended-second " +
            "suspended-fourth Neapolitan Italian French German pedal power Tristan other none"
        assertEquals(spec.split(' '), ChordKind.entries.map { it.xml })
        for (k in ChordKind.entries) assertEquals(k, ChordKind.fromXml(k.xml))
    }

    @Test
    fun `C7(b9,#11) is dominant plus b9 and #11 added relative to a dominant chord`() {
        val c = Chord.of("C", ChordKind.DOMINANT, add(9, -1), add(11, 1))
        assertEquals(names("C E G Bb Db F#"), c.notes())
    }

    @Test
    fun `the research encoding of C7alt gives C E G# Bb Db D#`() {
        val c = Chord.of("C", ChordKind.DOMINANT, add(9, -1), add(9, 1), alter(5, 1))
        assertEquals(names("C E G# Bb Db D#"), c.notes())
    }

    @Test
    fun `Cm7b5 is the half-diminished kind`() =
        assertEquals(names("C Eb Gb Bb"), Chord.of("C", ChordKind.HALF_DIMINISHED).notes())

    @Test
    fun `C6 over 9 is major-sixth plus an added 9`() =
        assertEquals(names("C E G A D"), Chord.of("C", ChordKind.MAJOR_SIXTH, add(9)).notes())

    @Test
    fun `Cadd9 is a major triad plus 9 with no seventh`() =
        assertEquals(names("C E G D"), Chord.of("C", ChordKind.MAJOR, add(9)).notes())

    @Test
    fun `C(no3) subtracts the third`() =
        assertEquals(names("C G"), Chord.of("C", ChordKind.MAJOR, subtract(3)).notes())

    @Test
    fun `C over E keeps a C major triad with E in the bass`() {
        val c = Chord.of("C", ChordKind.MAJOR, bass = "E")
        assertEquals(names("C E G"), c.notes())
        assertEquals(PitchName.parse("E"), c.bass)
        assertEquals("M3", c.bassInterval().toString())
    }

    @Test
    fun `D over C is a polychord with D major above C major`() {
        val lower = Chord.of("C", ChordKind.MAJOR)
        val poly = Chord.of("D", ChordKind.MAJOR).copy(poly = lower)
        assertEquals(names("D F# A"), poly.notes())
        assertEquals(names("C E G"), poly.poly!!.notes())
    }

    @Test
    fun `N C is kind none with no root and no notes`() {
        assertTrue(Chord.NO_CHORD.isNoChord)
        assertEquals(emptyList(), Chord.NO_CHORD.notes())
        assertFailsWith<IllegalArgumentException> { Chord(root = null, kind = ChordKind.MAJOR) }
    }

    @Test
    fun `added sevenths are relative to a dominant chord`() {
        assertEquals(names("C E G Bb"), Chord.of("C", ChordKind.MAJOR, add(7)).notes())
        assertEquals(names("C E G B"), Chord.of("C", ChordKind.MAJOR, add(7, 1)).notes())
    }

    @Test
    fun `alterations are relative to the degree already in the kind`() {
        // The diminished-seventh's bb7 raised one semitone is b7: Cdim7 with alter 7 +1 is Cm7b5.
        assertEquals(names("C Eb Gb Bb"), Chord.of("C", ChordKind.DIMINISHED_SEVENTH, alter(7, 1)).notes())
        // The minor-sixth's 6 lowered is b6, not the dominant reference.
        assertEquals(names("C Eb G Ab"), Chord.of("C", ChordKind.MINOR_SIXTH, alter(6, -1)).notes())
    }

    @Test
    fun `the functional sixths and Tristan spell from their stored roots`() {
        assertEquals(names("Db F Ab"), Chord.of("Db", ChordKind.NEAPOLITAN).notes())
        assertEquals(names("Ab C F#"), Chord.of("Ab", ChordKind.ITALIAN).notes())
        assertEquals(names("Ab C D F#"), Chord.of("Ab", ChordKind.FRENCH).notes())
        assertEquals(names("Ab C Eb F#"), Chord.of("Ab", ChordKind.GERMAN).notes())
        assertEquals(names("F B D# G#"), Chord.of("F", ChordKind.TRISTAN).notes())
    }

    @Test
    fun `a chord's written root is kept, even when it forces double accidentals`() {
        assertEquals(names("E G# B D Fx"), Chord.of("E", ChordKind.DOMINANT, add(9, 1)).notes())
        assertEquals(names("D# Fx A#"), Chord.of("D#", ChordKind.MAJOR).notes())
    }

    @Test
    fun `every catalog quality has an encoding whose pitches are the catalog's degrees`() {
        assertEquals(Chords.all.map { it.id }.toSet(), QualityMapping.table.keys)
        for (def in Chords.all) {
            val enc = QualityMapping.encode(def.id)
            val chord = Chord.of("C", enc.kind, *enc.degrees.toTypedArray())
            val omitted = QualityMapping.VOICING_OMITS[def.id]
            val fromKind = chord.degreeTokens().filter { it.number != omitted }
            assertEquals(def.degrees.toSet(), fromKind.toSet(), def.id)
        }
    }

    @Test
    fun `every encoding decodes back to its quality`() {
        for ((quality, enc) in QualityMapping.table) assertEquals(quality, QualityMapping.decode(enc.kind, enc.degrees), quality)
        // print-object doesn't change meaning
        val alt = QualityMapping.encode("7alt")
        assertEquals("7alt", QualityMapping.decode(alt.kind, alt.degrees.map { it.copy(printed = true) }))
        assertEquals(null, QualityMapping.decode(ChordKind.PEDAL, emptyList()))
    }

    @Test
    fun `no two qualities share an encoding`() {
        val encodings = QualityMapping.table.values.map { it.kind to it.degrees.map { d -> d.copy(printed = true) } }
        assertEquals(encodings.size, encodings.toSet().size)
    }

    @Test
    fun `degree tokens come out in pitch order`() {
        val c = Chord.of("C", ChordKind.DOMINANT, add(9, -1), add(9, 1), alter(5, 1), add(11, 1))
        assertEquals(DegreeToken.list("1 3 #5 b7 b9 #9 #11"), c.degreeTokens())
    }
}
