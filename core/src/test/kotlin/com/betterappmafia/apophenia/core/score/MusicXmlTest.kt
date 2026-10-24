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
                    "note" -> if (c.kids("chord").isEmpty()) t += c.text("duration")!!.toInt()
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
}
