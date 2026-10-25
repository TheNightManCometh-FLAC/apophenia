package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.instrument.Instrument
import com.betterappmafia.apophenia.core.instrument.Tunings
import com.betterappmafia.apophenia.core.learn.CourseReading
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.pitch.SpelledPitch
import com.betterappmafia.apophenia.core.rhythm.Duration
import com.betterappmafia.apophenia.core.rhythm.NoteValue
import com.betterappmafia.apophenia.core.rhythm.Rational
import com.betterappmafia.apophenia.core.rhythm.Tempo
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MusicXmlReadTest {
    private val q = Duration.QUARTER
    private val e = Duration.EIGHTH
    private fun n(p: String, tie: Boolean = false) = Note(SpelledPitch.parse(p), tie = tie)
    private fun t(p: String, string: Int, fret: Int) = Note(SpelledPitch.parse(p), string, fret)
    private fun ev(d: Duration, vararg p: String) = Event(d, p.map { n(it) })

    private fun read(xml: String) = MusicXmlRead.read(xml.toByteArray())

    /** Clefs the writer can't tell apart (a fretted part's treble and treble-8vb both go out as an octave clef). */
    private fun same(s: Score) = s.copy(parts = s.parts.mapIndexed { i, p -> (if (p.fretted && p.clef == Clef.TREBLE_8VB) p.copy(clef = Clef.TREBLE) else p).let { if (it.name.isBlank()) it.copy(name = "Part ${i + 1}") else it } })

    private fun assertRoundTrip(s: Score) {
        val back = MusicXmlRead.read(MusicXml.write(s).toByteArray())
        assertEquals(ScoreText.encode(same(s)), ScoreText.encode(back.score))
    }

    private val tide = Score(
        title = "Low Tide", meter = TimeSignature.of(3, 4), key = KeyMode(PitchName.parse("D"), "dorian"), tempo = Tempo(96.0),
        parts = listOf(
            Part("Lead", Clef.TREBLE, bars = listOf(
                Bar(listOf(listOf(
                    ev(q, "D4").copy(chord = "Dm7", lyrics = listOf(Lyric(1, "Show", hyphen = true))),
                    ev(q, "E4").copy(lyrics = listOf(Lyric(1, "me"))),
                    Event(q, listOf(n("F4", tie = true), n("A4")), dynamic = Dynamic.MF, texts = listOf(StaffText(TextKind.EXPRESSION, "dolce"))),
                )), repeatStart = true, rehearsal = "A"),
                Bar(listOf(listOf(
                    ev(q, "F4"), ev(e.inTuplet(3, 2), "G4"), ev(e.inTuplet(3, 2), "A4"), ev(e.inTuplet(3, 2), "B4"), Event(q),
                )), volta = listOf(1), end = Barline.REPEAT),
                Bar(listOf(
                    listOf(ev(e, "C5"), ev(e, "B4"), ev(q, "A4"), ev(q.dotted(), "G4")),
                    listOf(Event(q, hidden = true), ev(q, "D4"), ev(q.dotted(), "E4")),
                ), volta = listOf(2), meter = TimeSignature.additive(2, 2, 3, denominator = 8)),
                Bar(listOf(listOf(ev(Duration.HALF.dotted(), "A4").copy(chord = "E7#9"))),
                    meter = TimeSignature.of(3, 4), key = KeyMode(PitchName.parse("A"), "aeolian"), tempo = Tempo(120.0),
                    jumps = setOf(Jump.SEGNO, Jump.DC_AL_FINE), end = Barline.FINAL),
            )),
            Part("Bass", Clef.BASS, Instrument(Tunings.BASS_STANDARD), listOf(
                Bar(listOf(listOf(Event(q, listOf(t("E1", 4, 0))), Event(q, listOf(t("G1", 4, 3))), Event(q, listOf(t("A1", 3, 0)))))),
                Bar(listOf(listOf(Event(Duration.HALF.dotted(), listOf(t("D2", 2, 0)))))),
                Bar(listOf(listOf(Event(e, listOf(t("A1", 3, 0))), Event(e, listOf(t("B1", 3, 2))), Event(q, listOf(t("C2", 3, 3))), Event(q.dotted(), listOf(t("D2", 2, 0)))))),
                Bar(listOf(listOf(Event(Duration.HALF.dotted(), listOf(t("G2", 1, 0)))))),
            )),
        ),
    )

    @Test
    fun `what the app writes it reads back the same, notes, chords, ties, tuplets, voices, lyrics, bar marks, meter, key and tempo changes, and tab`() {
        assertRoundTrip(tide)
        assertRoundTrip(CourseReading.tabScore)
        assertRoundTrip(CourseReading.roadmap)
    }

    @Test
    fun `a triplet inside a triplet keeps both brackets`() {
        val inner = e.inTuplet(3, 2).inTuplet(3, 2)
        val s = Score(parts = listOf(Part("P", Clef.TREBLE, bars = listOf(Bar(listOf(listOf(
            ev(e.inTuplet(3, 2), "C4"), ev(inner, "D4"), ev(inner, "E4"), ev(inner, "F4"), ev(e.inTuplet(3, 2), "G4"),
            ev(q, "A4"), ev(Duration.HALF, "B4"),
        )))))))
        val back = read(MusicXml.write(s)).score
        assertEquals(listOf(listOf(listOf(3 to 2)), List(3) { listOf(3 to 2, 3 to 2) }, listOf(listOf(3 to 2))).flatten(),
            back.parts[0].bars[0].voices[0].take(5).map { ev -> ev.duration.tuplets.map { it.actual to it.normal } })
    }

    private fun doc(parts: String, list: String) = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE score-partwise PUBLIC "-//Recordare//DTD MusicXML 4.0 Partwise//EN" "http://www.musicxml.org/dtds/partwise.dtd">
<score-partwise version="4.0"><movement-title>Found</movement-title><part-list>$list</part-list>$parts</score-partwise>"""

    @Test
    fun `a guitar written an octave up with transpose comes in at sounding pitch, frets from technical, articulations and a grace`() {
        val xml = doc(
            """<part id="P1"><measure number="1">
<attributes><divisions>2</divisions><key><fifths>-3</fifths><mode>minor</mode></key><time><beats>4</beats><beat-type>4</beat-type></time>
<clef><sign>G</sign><line>2</line></clef><transpose><diatonic>0</diatonic><chromatic>0</chromatic><octave-change>-1</octave-change></transpose></attributes>
<direction><direction-type><metronome><beat-unit>quarter</beat-unit><beat-unit-dot/><per-minute>60</per-minute></metronome></direction-type></direction>
<note><grace slash="yes"/><pitch><step>B</step><alter>-1</alter><octave>4</octave></pitch><voice>1</voice><type>eighth</type></note>
<note><pitch><step>C</step><octave>5</octave></pitch><duration>2</duration><voice>1</voice><type>quarter</type>
  <notations><technical><string>2</string><fret>1</fret></technical><articulations><staccato/><tenuto/></articulations><fermata/></notations></note>
<note><pitch><step>E</step><alter>-1</alter><octave>5</octave></pitch><duration>6</duration><voice>1</voice><type>half</type><dot/>
  <notations><technical><string>1</string><fret>-1</fret></technical></notations></note>
</measure></part>""",
            """<score-part id="P1"><part-name>Electric Guitar</part-name></score-part>""",
        )
        val r = read(xml)
        val s = r.score
        assertEquals("Found", s.title)
        assertEquals(KeyMode(PitchName.parse("C"), "aeolian"), s.key, "three flats, minor: C minor")
        assertEquals(Tempo(60.0, q.dotted()), s.tempo)
        val v = s.parts[0].bars[0].voices[0]
        assertEquals(listOf("C4", "Eb4"), v.map { it.notes[0].pitch.toString() }, "written C5 sounds C4")
        assertEquals(2 to 1, v[0].notes[0].string to v[0].notes[0].fret)
        assertEquals(setOf(Effect.STACCATO), v[0].notes[0].effects)
        assertEquals(setOf(Mark.TENUTO, Mark.FERMATA), v[0].marks)
        assertEquals(listOf("Bb3"), v[0].graces.single().notes.map { it.pitch.toString() })
        assertTrue(v[0].graces.single().slash)
        assertEquals(Duration.HALF.dotted(), v[1].duration)
    }

    @Test
    fun `a piano's two staves become two parts, voices fill their gaps, a 5-8 measure rest is split, and harmony keeps its degrees`() {
        val xml = doc(
            """<part id="P1">
<measure number="1"><attributes><divisions>4</divisions><key><fifths>2</fifths><mode>dorian</mode></key><time><beats>5</beats><beat-type>8</beat-type></time><staves>2</staves>
<clef number="1"><sign>G</sign><line>2</line></clef><clef number="2"><sign>F</sign><line>4</line></clef></attributes>
<harmony><root><root-step>G</root-step></root><kind text="7">dominant</kind><bass><bass-step>B</bass-step></bass><degree><degree-value>9</degree-value><degree-alter>-1</degree-alter><degree-type>add</degree-type></degree></harmony>
<note><pitch><step>E</step><octave>5</octave></pitch><duration>4</duration><voice>1</voice><type>quarter</type><staff>1</staff></note>
<note><chord/><pitch><step>G</step><octave>5</octave></pitch><duration>4</duration><voice>1</voice><type>quarter</type><staff>1</staff></note>
<note><pitch><step>F</step><alter>1</alter><octave>5</octave></pitch><duration>6</duration><voice>1</voice><type>quarter</type><dot/><staff>1</staff></note>
<backup><duration>10</duration></backup>
<forward><duration>4</duration><voice>2</voice><staff>1</staff></forward>
<note><pitch><step>B</step><octave>4</octave></pitch><duration>6</duration><voice>2</voice><type>quarter</type><dot/><staff>1</staff></note>
<backup><duration>10</duration></backup>
<note><rest measure="yes"/><duration>10</duration><voice>5</voice><staff>2</staff></note>
</measure></part>""",
            """<score-part id="P1"><part-name>Piano</part-name></score-part>""",
        )
        val s = read(xml).score
        assertEquals(listOf("Piano (upper)", "Piano (lower)"), s.parts.map { it.name })
        assertEquals(listOf(Clef.TREBLE, Clef.BASS), s.parts.map { it.clef })
        assertEquals(KeyMode(PitchName.parse("E"), "dorian"), s.key, "two sharps, Dorian: E Dorian")
        assertEquals(TimeSignature.additive(2, 3, denominator = 8), s.meter)
        val upper = s.parts[0].bars[0].voices
        assertEquals(listOf("E5", "G5"), upper[0][0].notes.map { it.pitch.toString() })
        assertEquals("G7b9/B", upper[0][0].chord, "the degree the file lists is kept")
        assertEquals(listOf(true, false), upper[1].map { it.isRest }, "voice 2 starts with a hidden rest")
        assertTrue(upper[1][0].hidden)
        assertEquals(Rational.of(5, 8), s.parts[1].bars[0].voices[0].fold(Rational.ZERO) { a, ev -> a + ev.length }, "the lower staff's measure rest fills 5/8")
    }

    @Test
    fun `a TAB-only part gets its tuning from staff-tuning and a bass clef when it is a bass`() {
        val xml = doc(
            """<part id="P1"><measure number="1"><attributes><divisions>1</divisions><time><beats>4</beats><beat-type>4</beat-type></time>
<clef><sign>TAB</sign><line>5</line></clef><staff-details><staff-lines>4</staff-lines>
<staff-tuning line="1"><tuning-step>D</tuning-step><tuning-octave>1</tuning-octave></staff-tuning>
<staff-tuning line="2"><tuning-step>A</tuning-step><tuning-octave>1</tuning-octave></staff-tuning>
<staff-tuning line="3"><tuning-step>D</tuning-step><tuning-octave>2</tuning-octave></staff-tuning>
<staff-tuning line="4"><tuning-step>G</tuning-step><tuning-octave>2</tuning-octave></staff-tuning></staff-details></attributes>
<note><pitch><step>D</step><octave>1</octave></pitch><duration>4</duration><voice>1</voice><type>whole</type><notations><technical><string>4</string><fret>0</fret></technical></notations></note>
</measure></part>""",
            """<score-part id="P1"><part-name>Bass</part-name></score-part>""",
        )
        val p = read(xml).score.parts.single()
        assertEquals(Tunings.BASS_DROP_D, p.instrument!!.tuning)
        assertEquals(Clef.BASS, p.clef)
        assertEquals(4 to 0, p.bars[0].voices[0][0].notes[0].let { it.string to it.fret })
    }

    @Test
    fun `a compressed mxl is opened through its container, and timewise or non-MusicXML files are refused`() {
        val inner = MusicXml.write(CourseReading.roadmap).toByteArray()
        val zip = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                z.putNextEntry(ZipEntry("META-INF/container.xml")); z.write("<container><rootfiles><rootfile full-path=\"score/road.musicxml\"/></rootfiles></container>".toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("score/road.musicxml")); z.write(inner); z.closeEntry()
            }
        }.toByteArray()
        assertEquals(ScoreText.encode(same(CourseReading.roadmap)), ScoreText.encode(MusicXmlRead.read(zip).score))
        assertFailsWith<IllegalArgumentException> { read("<score-timewise version=\"4.0\"/>") }
        assertFailsWith<IllegalArgumentException> { read("<html><body/></html>") }
        assertFailsWith<IllegalArgumentException> { read("not xml at all") }
    }

    @Test
    fun `keys from fifths and mode, odd meters, rests to fill a length, and jump words`() {
        assertEquals(KeyMode(PitchName.parse("F#"), "major"), MusicXmlRead.key(6, null))
        assertEquals(KeyMode(PitchName.parse("Bb"), "mixolydian"), MusicXmlRead.key(-3, "mixolydian"))
        assertEquals(KeyMode(PitchName.parse("G#"), "aeolian"), MusicXmlRead.key(5, "minor"))
        assertEquals(TimeSignature.additive(2, 2, 3, denominator = 8), MusicXmlRead.meter("7", "8"))
        assertEquals(TimeSignature.additive(3, 3, 2, denominator = 8), MusicXmlRead.meter("3+3+2", "8"))
        assertEquals(TimeSignature.of(6, 8), MusicXmlRead.meter("6", "8"))
        assertEquals(listOf(Duration.HALF.dotted(), Duration.EIGHTH), MusicXmlRead.split(Rational.of(7, 8)))
        assertEquals(listOf(Duration(NoteValue.QUARTER, tuplets = listOf(com.betterappmafia.apophenia.core.rhythm.Tuplet(3, 2)))), MusicXmlRead.split(Rational.of(1, 6)), "a triplet quarter")
        assertEquals(Jump.DS_AL_CODA, MusicXmlRead.jump(" D.S.  al Coda "))
        assertEquals(null, MusicXmlRead.jump("dolce"))
    }
}
