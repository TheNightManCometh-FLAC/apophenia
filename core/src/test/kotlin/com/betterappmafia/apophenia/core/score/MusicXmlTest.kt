package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.instrument.Instrument
import com.betterappmafia.apophenia.core.instrument.Tunings
import com.betterappmafia.apophenia.core.learn.CourseReading
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.pitch.SpelledPitch
import com.betterappmafia.apophenia.core.rhythm.Duration
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MusicXmlTest {
    private val q = Duration.QUARTER
    private val e = Duration.EIGHTH
    private fun n(p: String, tie: Boolean = false) = Note(SpelledPitch.parse(p), tie = tie)
    private fun ev(d: Duration, vararg p: String) = Event(d, p.map { n(it) })

    private fun parse(xml: String): Element {
        val f = DocumentBuilderFactory.newInstance()
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        return f.newDocumentBuilder().parse(xml.byteInputStream()).documentElement
    }
    private fun Element.all(tag: String) = getElementsByTagName(tag).let { l -> (0 until l.length).map { l.item(it) as Element } }
    private fun Element.one(tag: String) = all(tag).first()
    private fun Element.kids(tag: String) = (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }
    private fun Element.text(tag: String) = all(tag).firstOrNull()?.textContent

    /** Every voice of every measure fills the bar: note durations (not chord notes) plus forwards, and each backup is a full bar. */
    private fun assertBarsAddUp(root: Element, score: Score) {
        val div = root.text("divisions")!!.toInt()
        root.kids("part").forEach { part ->
            part.kids("measure").forEachIndexed { bi, m ->
                val bar = score.meterAt(bi).barLength * 4 * div
                var t = 0
                for (c in (0 until m.childNodes.length).map { m.childNodes.item(it) }.filterIsInstance<Element>()) when (c.tagName) {
                    "note" -> if (c.kids("chord").isEmpty() && c.kids("grace").isEmpty()) t += c.text("duration")!!.toInt()
                    "forward" -> t += c.text("duration")!!.toInt()
                    "backup" -> { assertEquals(bar.num.toInt(), t, "measure ${bi + 1} voice length"); t -= c.text("duration")!!.toInt(); assertEquals(0, t) }
                }
                assertEquals(bar.num.toInt(), t, "measure ${bi + 1} last voice")
            }
        }
    }

    @Test
    fun `a guitar part is written at sounding pitch on an octave clef, with a TAB staff whose two numberings run opposite ways`() {
        val s = CourseReading.tabScore
        val root = parse(MusicXml.write(s))
        assertEquals("4.0", root.getAttribute("version"))
        assertBarsAddUp(root, s)
        assertEquals("2", root.text("staves"))
        val clefs = root.all("clef")
        assertEquals("G", clefs[0].text("sign")); assertEquals("-1", clefs[0].text("clef-octave-change")); assertEquals("TAB", clefs[1].text("sign"))
        val tunings = root.all("staff-tuning")
        assertEquals(listOf("E2", "A2", "D3", "G3", "B3", "E4"), tunings.map { it.text("tuning-step") + it.text("tuning-octave") }, "line 1 is the lowest string")
        assertEquals(listOf("1", "2", "3", "4", "5", "6"), tunings.map { it.getAttribute("line") })
        val first = root.all("note").first()
        assertEquals("E2", first.text("step") + first.text("octave"), "sounding pitch")
        assertTrue(root.all("transpose").isEmpty(), "an octave clef, never a transpose as well")
        val tabNotes = root.all("note").filter { it.text("staff") == "2" }
        assertEquals("6", tabNotes.first().text("string"), "string counts from the highest: the low E is 6")
        assertEquals("0", tabNotes.first().text("fret"))
        assertEquals(13, tabNotes.size)
        assertTrue(root.all("note").filter { it.text("staff") == "1" }.none { it.all("technical").isNotEmpty() })
    }

    @Test
    fun `ties, triplets, an additive meter, chord symbols and lyrics`() {
        val trip = e.inTuplet(3, 2)
        val s = Score(meter = TimeSignature.additive(2, 2, 3, denominator = 8), key = KeyMode(PitchName.parse("D"), "dorian"), parts = listOf(Part("Lead", Clef.TREBLE, bars = listOf(
            Bar(listOf(listOf(
                Event(q, listOf(n("D4", tie = true)), chord = "Dm7", lyrics = listOf(Lyric(1, "hel", hyphen = true))),
                Event(e.dotted(), listOf(n("D4")), chord = "E7#9", lyrics = listOf(Lyric(1, "lo"))),
                ev(Duration.SIXTEENTH, "F4"),
            ))),
            Bar(listOf(listOf(ev(trip, "A4"), ev(trip, "C5"), ev(trip, "E5"), Event(q, chord = "C/E"), Event(e)))),
        ))))
        val root = parse(MusicXml.write(s))
        assertBarsAddUp(root, s)
        assertEquals("2+2+3", root.text("beats")); assertEquals("8", root.text("beat-type"))
        assertEquals("0", root.one("key").text("fifths")); assertEquals("dorian", root.one("key").text("mode"))
        assertEquals(0, root.text("divisions")!!.toInt() % 3, "divisions hold a triplet eighth")
        val notes = root.all("note")
        assertEquals(listOf("start"), notes[0].all("tie").map { it.getAttribute("type") }); assertEquals(listOf("start"), notes[0].all("tied").map { it.getAttribute("type") })
        assertEquals(listOf("stop"), notes[1].all("tie").map { it.getAttribute("type") }); assertEquals(listOf("stop"), notes[1].all("tied").map { it.getAttribute("type") })
        val triplets = notes.filter { it.all("time-modification").isNotEmpty() }
        assertEquals(3, triplets.size); assertEquals("3", triplets[0].text("actual-notes")); assertEquals("2", triplets[0].text("normal-notes"))
        assertEquals(listOf("start", "", "stop"), triplets.map { t -> t.all("tuplet").joinToString { it.getAttribute("type") } })
        val harmonies = root.all("harmony")
        assertEquals(listOf("D", "E", "C"), harmonies.map { it.text("root-step") })
        assertEquals(listOf("minor-seventh", "dominant", "major"), harmonies.map { it.text("kind") })
        assertTrue(harmonies[1].all("degree").any { it.text("degree-value") == "9" && it.text("degree-alter") == "1" }, "♯9")
        assertEquals("E", harmonies[2].text("bass-step"))
        assertEquals(listOf("begin", "end"), root.all("syllabic").map { it.textContent })
    }

    @Test
    fun `repeats, endings and road-map words`() {
        val s = CourseReading.roadmap
        val root = parse(MusicXml.write(s))
        assertBarsAddUp(root, s)
        val m = root.one("part").kids("measure")
        assertEquals("forward", m[0].one("repeat").getAttribute("direction"))
        assertEquals("Fine", m[1].text("words"))
        val e3 = m[2].all("ending"); assertEquals(listOf("start", "stop"), e3.map { it.getAttribute("type") }); assertEquals("1", e3[0].getAttribute("number"))
        assertEquals("backward", m[2].one("repeat").getAttribute("direction"))
        val e4 = m[3].all("ending"); assertEquals(listOf("start", "discontinue"), e4.map { it.getAttribute("type") }); assertEquals("2", e4[0].getAttribute("number"))
        assertEquals("D.C. al Fine", m[4].text("words")); assertEquals("light-heavy", m[4].all("bar-style").last().textContent)
    }

    @Test
    fun `every score example in the Course exports and adds up`() {
        val scores = com.betterappmafia.apophenia.core.learn.Course.pages.mapNotNull { (it.see as? com.betterappmafia.apophenia.core.learn.CourseSee.Score)?.score }
        assertTrue(scores.size >= 10)
        for (s in scores) assertBarsAddUp(parse(MusicXml.write(s)), s)
    }

    @Test
    fun `every export is valid against the MusicXML 4 schema`() {
        val scores = com.betterappmafia.apophenia.core.learn.Course.pages.mapNotNull { (it.see as? com.betterappmafia.apophenia.core.learn.CourseSee.Score)?.score } +
            listOf(CourseReading.tabScore, CourseReading.roadmap)
        for (s in scores) assertValid(MusicXml.write(s), s.title)
    }

    private fun g(s: Int, f: Int, p: String, vararg fx: Effect) = Note(SpelledPitch.parse(p), string = s, fret = f, effects = fx.toSet())

    // A piano line and a guitar part, between them every kind of marking the stored score has.
    private val marked = Score(parts = listOf(
        Part("Piano", Clef.TREBLE, bars = listOf(
            Bar(listOf(listOf(
                Event(q, listOf(n("C5")), marks = setOf(Mark.STACCATISSIMO, Mark.FERMATA, Mark.TRILL), spans = mapOf(Span.SLUR to 3, Span.CRESC to 2)),
                Event(q, listOf(Note(SpelledPitch.parse("D5"), head = Head.DIAMOND, finger = "3"))),
                Event(q, listOf(n("E5")), marks = setOf(Mark.MORDENT), graces = listOf(Grace(com.betterappmafia.apophenia.core.rhythm.NoteValue.EIGHTH, listOf(n("D5"))))),
                Event(q, listOf(n("C4"), n("E4"), n("G4")), marks = setOf(Mark.ARPEGGIO_UP), tremolo = 3),
            ))),
            Bar(listOf(listOf(
                Event(Duration.HALF, listOf(n("G5")), spans = mapOf(Span.OTTAVA_UP to 2, Span.PEDAL to 2)),
                Event(Duration.HALF, listOf(n("A5")), marks = setOf(Mark.FERMATA_SHORT, Mark.TURN_SLASH)),
            ))),
        )),
        Part("Guitar", Clef.TREBLE_8VB, com.betterappmafia.apophenia.core.instrument.Instrument(Tunings.GUITAR_STANDARD), listOf(
            Bar(listOf(listOf(
                Event(e, listOf(g(3, 0, "G3", Effect.HAMMER))), Event(e, listOf(g(3, 2, "A3"))),
                Event(e, listOf(g(2, 3, "D4", Effect.HAMMER))), Event(e, listOf(g(2, 1, "C4"))),
                Event(e, listOf(g(1, 0, "E4", Effect.LEGATO_SLIDE))), Event(e, listOf(g(1, 2, "F#4", Effect.GHOST))),
                Event(q, listOf(g(5, 3, "C3", Effect.PALM_MUTE), g(4, 2, "E3"), g(3, 0, "G3"), g(2, 1, "C4"), g(1, 0, "E4")), chord = "C", diagram = listOf(null, 3, 2, 0, 1, 0)),
            ))),
            Bar(listOf(listOf(Event(Duration.WHOLE, listOf(g(2, 7, "F#4", Effect.BEND, Effect.DEAD)))))),
        )),
    ))

    @Test
    fun `markings are written as MusicXML 4 notations, directions and frames, and the file is valid`() {
        val xml = MusicXml.write(marked)
        assertValid(xml, "marked")
        val root = parse(xml)
        assertBarsAddUp(root, marked)
        val (piano, guitar) = root.kids("part")
        val pn = piano.all("note")
        fun Element.has(tag: String) = all(tag).isNotEmpty()
        // C5: staccatissimo, an upright fermata, a trill, and the slur and hairpin starting.
        assertTrue(pn[0].has("staccatissimo") && pn[0].has("trill-mark"))
        assertEquals("upright", pn[0].one("fermata").getAttribute("type"))
        val slurNo = pn[0].one("slur").let { assertEquals("start", it.getAttribute("type")); it.getAttribute("number") }
        assertEquals("diamond", pn[1].text("notehead")); assertEquals("3", pn[1].text("fingering"))
        // The grace note before E5: slashed, no duration.
        assertEquals("yes", pn[2].one("grace").getAttribute("slash")); assertTrue(!pn[2].has("duration"))
        assertTrue(pn[3].has("mordent"))
        assertEquals(listOf("stop"), pn[3].all("slur").map { it.getAttribute("type") }, "the slur covers three events")
        assertEquals(slurNo, pn[3].one("slur").getAttribute("number"))
        assertEquals(listOf("up", "up", "up"), pn.subList(4, 7).map { it.one("arpeggiate").getAttribute("direction") }, "every note of the chord")
        assertEquals("single", pn[4].one("tremolo").getAttribute("type")); assertEquals("3", pn[4].one("tremolo").textContent)
        assertEquals(listOf("crescendo", "stop"), piano.all("wedge").map { it.getAttribute("type") })
        assertEquals(listOf("down" to "8", "stop" to "8"), piano.all("octave-shift").map { it.getAttribute("type") to it.getAttribute("size") }, "8va is a shift down from the written pitch")
        assertEquals(listOf("start", "stop"), piano.all("pedal").map { it.getAttribute("type") })
        assertEquals("angled", pn[8].one("fermata").textContent); assertEquals("yes", pn[8].one("turn").getAttribute("slash"))
        // Guitar, both staves: a hammer-on up, a pull-off down, a slide, each start/stop with matching numbers.
        val g1 = guitar.all("note").filter { it.text("staff") == "1" }
        val g2 = guitar.all("note").filter { it.text("staff") == "2" }
        for ((notes, off) in listOf(g1 to 0, g2 to 8)) {
            assertEquals("start" to "${1 + off}", notes[0].one("hammer-on").let { it.getAttribute("type") to it.getAttribute("number") })
            assertEquals("stop", notes[1].one("hammer-on").getAttribute("type"))
            assertEquals("P", notes[2].one("pull-off").textContent); assertEquals("stop", notes[3].one("pull-off").getAttribute("type"))
            assertEquals("start", notes[4].one("slide").getAttribute("type")); assertEquals("stop", notes[5].one("slide").getAttribute("type"))
            assertEquals("Palm mute", notes[6].text("other-technical"))
            assertEquals("2", notes.last().text("bend-alter"))
        }
        assertEquals("yes", g1[5].one("notehead").getAttribute("parentheses"), "a ghost note")
        assertEquals("x", g1.last().text("notehead"), "a dead note")
        val frame = guitar.one("harmony").one("frame")
        assertEquals("6", frame.text("frame-strings"))
        assertEquals(listOf("5:3", "4:2", "3:0", "2:1", "1:0"), frame.all("frame-note").map { it.text("string") + ":" + it.text("fret") }, "x32010, the low E not played")
    }

    @Test
    fun `the guitar techniques come back when the file is imported`() {
        val back = MusicXmlRead.read(MusicXml.write(marked).toByteArray()).score.parts[1]
        val notes = back.bars.flatMap { b -> b.voices[0].flatMap { it.notes } }
        assertEquals(setOf(Effect.HAMMER), notes[0].effects)
        assertEquals(setOf(Effect.HAMMER), notes[2].effects)
        assertEquals(setOf(Effect.LEGATO_SLIDE), notes[4].effects)
        assertEquals(setOf(Effect.GHOST), notes[5].effects)
        assertEquals(setOf(Effect.PALM_MUTE), notes[6].effects)
        assertEquals(setOf(Effect.BEND), notes.last().effects)
        assertEquals(Head.X, notes.last().head, "a dead note comes back as an x notehead")
    }

    @Test
    fun `lines, tremolo, marks and chord diagrams come back when the file is imported`() {
        val r = MusicXmlRead.read(MusicXml.write(marked).toByteArray())
        val (piano, guitar) = r.score.parts
        val p1 = piano.bars[0].voices[0]; val p2 = piano.bars[1].voices[0]
        assertEquals(mapOf(Span.SLUR to 3, Span.CRESC to 2), p1[0].spans)
        assertEquals(setOf(Mark.STACCATISSIMO, Mark.FERMATA, Mark.TRILL), p1[0].marks)
        assertEquals(Head.DIAMOND, p1[1].notes[0].head); assertEquals("3", p1[1].notes[0].finger)
        assertEquals(listOf("D5"), p1[2].graces.single().notes.map { it.pitch.toString() }); assertEquals(setOf(Mark.MORDENT), p1[2].marks)
        assertEquals(3, p1[3].tremolo); assertEquals(setOf(Mark.ARPEGGIO_UP), p1[3].marks)
        assertEquals(mapOf(Span.OTTAVA_UP to 2, Span.PEDAL to 2), p2[0].spans)
        assertEquals(setOf(Mark.FERMATA_SHORT, Mark.TURN_SLASH), p2[1].marks)
        assertEquals(listOf(null, 3, 2, 0, 1, 0), guitar.bars[0].voices[0].last().diagram)
        assertTrue(r.dropped.none { "line" in it }, r.dropped.toString())
    }

    @Test
    fun `lines across a barline, glissando, trill line and 8vb come back`() {
        val s = Score(parts = listOf(Part("Line", Clef.TREBLE, bars = listOf(
            Bar(listOf(listOf(ev(q, "C5"), Event(q, listOf(n("D5")), spans = mapOf(Span.DIM to 4, Span.SLUR to 2)), ev(q, "E5"), Event(q, listOf(n("F5")), spans = mapOf(Span.GLISS to 2))))),
            Bar(listOf(listOf(ev(q, "C6"), Event(q, listOf(n("B5")), spans = mapOf(Span.TRILL_LINE to 3)), ev(q, "A5"), Event(q, listOf(n("G5")))))),
            Bar(listOf(listOf(Event(Duration.WHOLE, listOf(n("C3")), spans = mapOf(Span.OTTAVA_DOWN to 2)), ))),
            Bar(listOf(listOf(ev(Duration.WHOLE, "D3")))),
        ))))
        val xml = MusicXml.write(s)
        assertValid(xml, "lines")
        val back = MusicXmlRead.read(xml.toByteArray()).score.parts.single().bars.map { it.voices[0] }
        assertEquals(mapOf(Span.DIM to 4, Span.SLUR to 2), back[0][1].spans, "the diminuendo runs into the next bar")
        assertEquals(mapOf(Span.GLISS to 2), back[0][3].spans)
        assertEquals(mapOf(Span.TRILL_LINE to 3), back[1][1].spans)
        assertTrue(Mark.TRILL !in back[1][1].marks, "the trill sign of a trill line isn't a second trill")
        assertEquals(mapOf(Span.OTTAVA_DOWN to 2), back[2][0].spans)
        assertEquals("C3", back[2][0].notes.single().pitch.toString(), "pitches stay as they sound under an octave line")
    }

    @Test
    fun `the schema check rejects a file that isn't valid`() {
        val bad = MusicXml.write(CourseReading.tabScore).replaceFirst("<divisions>", "<division>").replaceFirst("</divisions>", "</division>")
        kotlin.test.assertFailsWith<AssertionError> { assertValid(bad, "bad") }
    }

    /** Checks [xml] against the official MusicXML 4.0 XSD (test resources), resolving its imports locally. */
    private fun assertValid(xml: String, name: String) {
        val sf = javax.xml.validation.SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI)
        val base = "/musicxml-4.0/"
        sf.resourceResolver = org.w3c.dom.ls.LSResourceResolver { _, namespace, _, systemId, _ ->
            val file = when (namespace) {
                "http://www.w3.org/XML/1998/namespace" -> "xml.xsd"
                "http://www.w3.org/1999/xlink" -> "xlink.xsd"
                else -> systemId?.substringAfterLast('/') ?: return@LSResourceResolver null
            }
            val impl = (org.w3c.dom.bootstrap.DOMImplementationRegistry.newInstance().getDOMImplementation("LS") as org.w3c.dom.ls.DOMImplementationLS)
            impl.createLSInput().apply { byteStream = MusicXmlTest::class.java.getResourceAsStream(base + file)!!; this.systemId = systemId }
        }
        val schema = sf.newSchema(javax.xml.transform.stream.StreamSource(javaClass.getResourceAsStream(base + "musicxml.xsd"), "musicxml.xsd"))
        val f = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true; setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        val doc = f.newDocumentBuilder().parse(xml.byteInputStream())
        try { schema.newValidator().validate(javax.xml.transform.dom.DOMSource(doc)) }
        catch (x: org.xml.sax.SAXParseException) {
            val line = xml.lines().getOrNull(x.lineNumber - 1)?.take(300)
            throw AssertionError("$name: ${x.message} at line ${x.lineNumber}: $line", x)
        }
    }
}
