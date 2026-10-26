package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.chord.ChordParser
import com.betterappmafia.apophenia.core.instrument.Instrument
import com.betterappmafia.apophenia.core.instrument.Tuning
import com.betterappmafia.apophenia.core.instrument.Tunings
import com.betterappmafia.apophenia.core.interval.Interval
import com.betterappmafia.apophenia.core.interval.minus
import com.betterappmafia.apophenia.core.interval.plus
import com.betterappmafia.apophenia.core.pitch.Letter
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.pitch.SpelledPitch
import com.betterappmafia.apophenia.core.rhythm.Duration
import com.betterappmafia.apophenia.core.rhythm.NoteValue
import com.betterappmafia.apophenia.core.rhythm.Rational
import com.betterappmafia.apophenia.core.rhythm.Tempo
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import com.betterappmafia.apophenia.core.rhythm.Tuplet
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * MusicXML import (plan §11): a `score-partwise` file (.musicxml, .xml) or a compressed .mxl into a [Score].
 *
 * Read: notes, rests, chords, voices (`<backup>`/`<forward>`, gaps filled with rests, hidden in voices 2–4), dots,
 * tuplets (nesting from the `<tuplet>` brackets and the cumulative `<time-modification>`), ties, graces, key with
 * mode, time (additive "2+2+3"), clefs (octave clefs included) and their changes, `<transpose>` (pitches are kept
 * sounding), tab (a TAB staff's `<staff-tuning>` lines from the lowest string, `<technical><string>/<fret>` from the
 * highest), chord symbols from `<harmony>`, lyrics, dynamics, words, rehearsal marks, tempo, repeats, endings,
 * barline styles, segno/coda and D.C./D.S./Fine/To Coda words, common articulations, ornaments, fermatas,
 * noteheads, fingering, single-note tremolo, chord diagrams (`<frame>`), the guitar techniques the app writes, and lines:
 * slurs, glissandos, trill lines, hairpins, 8va/8vb/15ma/15mb and pedal (each start paired with its stop, stored as
 * how many events of its voice the line covers).
 *
 * [Claude] Choices: a part with a notation staff and a TAB staff becomes one fretted part (the TAB staff gives the
 * frets); other multi-staff parts (piano) become one part per staff; voices beyond four, overlapping notes and
 * `score-timewise` files are not read (timewise is refused); a line written as a direction ends on the last event
 * that starts before its stop; dashed and bracket lines, two-note tremolos and 22ma aren't read.
 * Odd meters given as one number (5/8, 7/8) are grouped as twos then a three.
 */
object MusicXmlRead {
    /** The score and what couldn't be kept, in plain words. */
    class Result(val score: Score, val dropped: List<String>)

    /** Reads MusicXML text or a .mxl zip; throws IllegalArgumentException with a readable reason. */
    fun read(bytes: ByteArray): Result {
        val xml = if (bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) unzip(bytes) else bytes
        val doc = runCatching {
            val f = DocumentBuilderFactory.newInstance()
            f.isNamespaceAware = false
            f.isValidating = false
            runCatching { f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            val b = f.newDocumentBuilder()
            // Never fetch the DTD (the app has no internet, and a parser mustn't try).
            b.setEntityResolver { _, _ -> org.xml.sax.InputSource(java.io.StringReader("")) }
            b.parse(ByteArrayInputStream(xml))
        }.getOrElse { throw IllegalArgumentException("It isn't readable XML (${it.message}).") }
        val root = doc.documentElement
        when (root.tagName) {
            "score-partwise" -> {}
            "score-timewise" -> throw IllegalArgumentException("It's a timewise MusicXML file; only partwise files are read.")
            else -> throw IllegalArgumentException("It isn't a MusicXML score.")
        }
        return Reader(root).run()
    }

    /** The score file inside a .mxl: META-INF/container.xml's first rootfile, else the first .musicxml/.xml. */
    internal fun unzip(bytes: ByteArray): ByteArray {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var total = 0L
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory) continue
                val b = z.readBytes()
                total += b.size
                require(total < 64_000_000) { "The .mxl is too large." }
                files[e.name] = b
            }
        }
        val path = files["META-INF/container.xml"]?.let { c ->
            Regex("full-path=\"([^\"]+)\"").find(String(c, Charsets.UTF_8))?.groupValues?.get(1)
        }
        return path?.let { files[it] }
            ?: files.entries.filter { !it.key.startsWith("META-INF/") && (it.key.endsWith(".musicxml") || it.key.endsWith(".xml")) }.minByOrNull { it.key }?.value
            ?: throw IllegalArgumentException("The .mxl has no score in it.")
    }

    // ------------------------------------------------------------------------------------------------- helpers

    private fun Element.kids(name: String? = null): List<Element> {
        val out = ArrayList<Element>()
        val n = childNodes
        for (i in 0 until n.length) { val c = n.item(i); if (c is Element && (name == null || c.tagName == name)) out += c }
        return out
    }
    private fun Element.kid(name: String) = kids(name).firstOrNull()
    private fun Element.text(name: String) = kid(name)?.textContent?.trim()
    private fun Element.has(name: String) = kid(name) != null
    private fun Element.attr(name: String) = getAttribute(name).takeIf { it.isNotEmpty() }

    private val VALUES = NoteValue.entries.associateBy { it.xml }

    /** Every plain and single-dotted value, longest first, for filling a length with rests. */
    private val PIECES = NoteValue.entries.flatMap { v -> listOf(Duration(v, 1), Duration(v)) }.sortedByDescending { it.length }

    private fun dyadic(r: Rational) = r.den > 0 && (r.den and (r.den - 1)) == 0L

    /** A length as written durations: plain and dotted values, or (for a tuplet length) the same inside a tuplet. */
    internal fun split(len: Rational): List<Duration> {
        if (len <= Rational.ZERO) return emptyList()
        if (!dyadic(len)) {
            for (t in listOf(Tuplet(3, 2), Tuplet(5, 4), Tuplet(6, 4), Tuplet(7, 4), Tuplet(7, 8), Tuplet(9, 8), Tuplet(5, 2))) {
                val inner = len / t.factor
                if (dyadic(inner)) return split(inner).map { it.copy(tuplets = it.tuplets + t) }
            }
        }
        val out = ArrayList<Duration>()
        var left = len
        while (left > Rational.ZERO && out.size < 64) {
            val d = PIECES.firstOrNull { it.length <= left } ?: break
            out += d
            left -= d.length
        }
        return out
    }

    /** The key for a `<fifths>` and `<mode>`: the mode's tonic in the major key with that signature. */
    internal fun key(fifths: Int, mode: String?): KeyMode? {
        val (index, id) = when (mode?.lowercase()) {
            null, "", "major", "ionian" -> 0 to "major"
            "dorian" -> 1 to "dorian"; "phrygian" -> 2 to "phrygian"; "lydian" -> 3 to "lydian"
            "mixolydian" -> 4 to "mixolydian"; "minor", "aeolian" -> 5 to "aeolian"; "locrian" -> 6 to "locrian"
            else -> return null
        }
        val majorLetter = Math.floorMod(4 * fifths, 7)
        val majorPc = Math.floorMod(7 * fifths, 12)
        val letter = Letter.entries[(majorLetter + index) % 7]
        val pc = (majorPc + intArrayOf(0, 2, 4, 5, 7, 9, 11)[index]) % 12
        var alter = pc - letter.natural
        while (alter > 6) alter -= 12
        while (alter < -6) alter += 12
        if (alter !in -2..2) return null
        return runCatching { KeyMode(PitchName(letter, alter), id) }.getOrNull()
    }

    internal fun meter(beats: String, beatType: String): TimeSignature? {
        val den = beatType.trim().toIntOrNull() ?: return null
        return runCatching {
            if ('+' in beats) TimeSignature(beats.split('+').map { it.trim().toInt() }, den)
            else {
                val n = beats.trim().toInt()
                runCatching { TimeSignature.of(n, den) }.getOrElse {
                    // 5/8, 7/8, 11/8… with no grouping given: twos, then a three.
                    TimeSignature(List((n - 3) / 2) { 2 } + 3, den)
                }
            }
        }.getOrNull()
    }

    private val JUMP_WORDS = mapOf(
        "d.c." to Jump.DC, "da capo" to Jump.DC, "d.c. al fine" to Jump.DC_AL_FINE, "d.c. al coda" to Jump.DC_AL_CODA,
        "d.s." to Jump.DS, "dal segno" to Jump.DS, "d.s. al fine" to Jump.DS_AL_FINE, "d.s. al coda" to Jump.DS_AL_CODA,
        "fine" to Jump.FINE, "to coda" to Jump.TO_CODA,
    )

    internal fun jump(words: String): Jump? = JUMP_WORDS[words.trim().lowercase().replace(Regex("\\s+"), " ")]

    /** MusicXML `<kind>` values as the ASCII suffix the chord parser reads. */
    private val KIND = mapOf(
        "major" to "", "minor" to "m", "augmented" to "aug", "diminished" to "dim", "dominant" to "7",
        "major-seventh" to "maj7", "minor-seventh" to "m7", "diminished-seventh" to "dim7", "augmented-seventh" to "7#5",
        "half-diminished" to "m7b5", "major-minor" to "m(maj7)", "major-sixth" to "6", "minor-sixth" to "m6",
        "dominant-ninth" to "9", "major-ninth" to "maj9", "minor-ninth" to "m9", "dominant-11th" to "11",
        "major-11th" to "maj11", "minor-11th" to "m11", "dominant-13th" to "13", "major-13th" to "maj13",
        "minor-13th" to "m13", "suspended-second" to "sus2", "suspended-fourth" to "sus4", "power" to "5",
    )

    /** Kinds that already have a seventh, so an added ninth reads as an extension. */
    private fun sevenths(kind: String) = kind != "major-sixth" && kind != "minor-sixth" && (kind.contains("seventh") || kind.contains("ninth") || kind.contains("11th") || kind.contains("13th") || kind in setOf("dominant", "half-diminished", "major-minor"))

    private fun acc(alter: Int) = when { alter > 0 -> "#".repeat(alter); alter < 0 -> "b".repeat(-alter); else -> "" }

    /** Stored chord text is ASCII: the display symbols some programs put in `kind@text` become their spellings. */
    private fun ascii(s: String) = s.replace("Δ", "maj").replace("ø", "m7b5").replace("°", "dim").replace("−", "m")
        .replace("♭", "b").replace("♯", "#").replace("⁺", "+").replace("+", "aug").let { if (it.all { c -> c.code < 128 }) it else null }

    /** A `<harmony>` as typed chord text ("Dm7", "E7#9", "C/G", "N.C."), or null when it has no root. */
    internal fun harmony(h: Element): String? {
        val kindEl = h.kid("kind")
        val kind = kindEl?.textContent?.trim().orEmpty()
        if (kind == "none") return "N.C."
        val root = h.kid("root") ?: return null
        val step = root.text("root-step") ?: return null
        val rootText = step + acc(root.text("root-alter")?.toDoubleOrNull()?.toInt() ?: 0)
        val bass = h.kid("bass")?.let { b -> b.text("bass-step")?.let { "/" + it + acc(b.text("bass-alter")?.toDoubleOrNull()?.toInt() ?: 0) } } ?: ""
        val degrees = h.kids("degree").mapNotNull { d ->
            val v = d.text("degree-value")?.toIntOrNull() ?: return@mapNotNull null
            val a = d.text("degree-alter")?.toDoubleOrNull()?.toInt() ?: 0
            when (d.text("degree-type")) {
                // An added 9, 11 or 13 on a seventh chord is written as an extension (7b9), elsewhere as add9.
                "add" -> if (v >= 9 && sevenths(kind)) "${acc(a)}$v" else "add${acc(a)}$v"
                "alter" -> "${acc(a)}$v"
                "subtract" -> "no$v"
                else -> null
            }
        }
        val fromKind = (KIND[kind] ?: "") + degrees.joinToString("")
        val typed = kindEl?.attr("text")?.let(::ascii)
        // A program's own text wins when it reads as a chord with at least the degrees the file lists.
        if (typed != null) {
            val t = rootText + typed + bass
            val c = ChordParser.parse(t).chord
            if (c != null && c.degrees.size >= h.kids("degree").size) return t
        }
        val t = rootText + fromKind + bass
        return if (ChordParser.parse(t).chord != null || typed == null) t else rootText + typed + bass
    }

    // ------------------------------------------------------------------------------------------------- reader

    /** One event as read, before the voices are laid out. */
    private class Raw(val staff: Int, val voice: String, val start: Rational, val length: Rational, var duration: Duration?, val rest: Boolean, val hidden: Boolean) {
        val notes = ArrayList<Note>()
        val marks = LinkedHashSet<Mark>()
        val lyrics = ArrayList<Lyric>()
        var graces: List<Grace> = emptyList()
        var tuplets: List<Tuplet> = emptyList()
        var tremolo = 0
    }

    /** Something written at a point in the bar that belongs to the event there. */
    private class At(val staff: Int, val pos: Rational, val chord: String? = null, val dynamic: Dynamic? = null, val text: StaffText? = null, val diagram: List<Int?>? = null)

    /**
     * One end of a line as read, in document order: [group] pairs a start with its stop (a wedge's stop doesn't say
     * crescendo or diminuendo), [span] is what a start begins. A stop on a note ([inclusive]) ends on that note; a stop
     * written as a direction ends on the last event that starts before it (where the app writes it, at the end of the
     * line's last note). [voice] null means the staff's first voice.
     */
    private class LineMark(val group: String, val span: Span?, val start: Boolean, val number: Int, val staff: Int, val voice: String?, val measure: Int, val pos: Rational, val inclusive: Boolean)

    private class Instr(var lines: MutableMap<Int, SpelledPitch> = HashMap(), var capo: Int = 0, var count: Int = 0)

    /** One measure of a part as read: its bar fields, voices per staff and the clefs set in it. */
    private class MeasureOut(val bar: Bar, val voicesByStaff: Map<Int, List<List<Event>>>, val clefByStaff: Map<Int, Clef>)

    private class Reader(val root: Element) {
        val dropped = LinkedHashSet<String>()

        fun run(): Result {
            val title = root.kid("work")?.text("work-title")?.takeIf { it.isNotBlank() } ?: root.text("movement-title").orEmpty()
            val names = HashMap<String, String>()
            root.kid("part-list")?.kids("score-part")?.forEach { sp -> names[sp.getAttribute("id")] = sp.text("part-name").orEmpty() }
            var meter: TimeSignature? = null
            var key: KeyMode? = null
            var tempo: Tempo? = null
            val parts = ArrayList<Part>()
            root.kids("part").forEachIndexed { pi, p ->
                val read = part(p, names[p.getAttribute("id")]?.takeIf { it.isNotBlank() } ?: "Part ${pi + 1}")
                if (pi == 0) { meter = read.meter; key = read.key; tempo = read.tempo }
                parts += read.parts
            }
            require(parts.isNotEmpty()) { "The score has no parts." }
            return Result(Score(title = title, meter = meter ?: TimeSignature.of(4, 4), key = key, tempo = tempo ?: Tempo(120.0), parts = parts), dropped.toList())
        }

        class PartRead(val parts: List<Part>, val meter: TimeSignature?, val key: KeyMode?, val tempo: Tempo?)

        fun part(p: Element, name: String): PartRead {
            var div = 1
            var staves = 1
            val clefs = HashMap<Int, Clef>()          // staff → clef in force
            val clefOctave = HashMap<Int, Boolean>()  // staff → octave-down clef
            var tabStaff: Int? = null
            val instr = Instr()
            var transpose: Interval? = null
            var transposeDown = false
            var firstMeter: TimeSignature? = null
            var firstKey: KeyMode? = null
            var firstTempo: Tempo? = null
            var meterNow: TimeSignature? = null
            var keyNow: KeyMode? = null
            var tempoNow: Tempo? = null
            var openVolta = emptyList<Int>()
            val voiceIds = HashMap<Int, MutableList<String>>()   // staff → voice ids in the order first met
            val measures = ArrayList<MeasureOut>()
            val lineMarks = ArrayList<LineMark>()
            /** Slurs, glissandos and trill lines on a note at [at] in measure [mi]. */
            fun noteLines(el: Element, staff: Int, voice: String, mi: Int, at: Rational) {
                val notations = el.kids("notations")
                fun add(group: String, span: Span, x: Element) {
                    val type = x.attr("type") ?: return
                    if (type != "start" && type != "stop") return
                    lineMarks += LineMark(group, span, type == "start", x.attr("number")?.toIntOrNull() ?: 1, staff, voice, mi, at, inclusive = true)
                }
                for (n in notations) {
                    n.kids("slur").forEach { add("slur", Span.SLUR, it) }
                    n.kids("glissando").forEach { add("gliss", Span.GLISS, it) }
                    n.kids("ornaments").flatMap { it.kids("wavy-line") }.forEach { add("trill", Span.TRILL_LINE, it) }
                }
            }

            for ((mi, m) in p.kids("measure").withIndex()) {
                var pos = Rational.ZERO
                var end = Rational.ZERO
                val raws = ArrayList<Raw>()
                val ats = ArrayList<At>()
                val lastByVoice = HashMap<Pair<Int, String>, Raw>()
                val pendingGraces = HashMap<Pair<Int, String>, MutableList<Grace>>()
                val activeTuplets = HashMap<Pair<Int, String>, MutableMap<Int, Tuplet>>()
                var repeatStart = false
                var barEnd = Barline.SINGLE
                var times = 2
                val jumps = LinkedHashSet<Jump>()
                var rehearsal: String? = null
                var barTempo: Tempo? = null
                var tempoText: String? = null
                var barKey: KeyMode? = null
                var barMeter: TimeSignature? = null
                val clefHere = HashMap<Int, Clef>()
                var volta = openVolta
                var closeVolta = false
                fun units(e: Element) = (e.text("duration")?.toDoubleOrNull()?.let { Math.round(it) } ?: 0L).let { Rational.of(it, 4L * div) }

                for (el in m.kids()) when (el.tagName) {
                    "attributes" -> {
                        el.text("divisions")?.toDoubleOrNull()?.let { if (it >= 1) div = Math.round(it).toInt() }
                        el.text("staves")?.toIntOrNull()?.let { staves = it.coerceIn(1, 8) }
                        el.kid("key")?.let { k -> k.text("fifths")?.toIntOrNull()?.let { f ->
                            // No accidentals and no mode is how a score with no key is written (the app writes C major with its mode).
                            if (f == 0 && k.text("mode") == null && mi == 0) barKey = null
                            else key(f, k.text("mode"))?.let { barKey = it } ?: dropped.add("a key in a mode MusicXML can't name")
                        } }
                        el.kid("time")?.let { t ->
                            val b = t.text("beats"); val bt = t.text("beat-type")
                            if (b != null && bt != null) meter(b, bt)?.let { barMeter = it } ?: dropped.add("time signature $b/$bt")
                        }
                        for (c in el.kids("clef")) {
                            val staff = c.attr("number")?.toIntOrNull() ?: 1
                            val sign = c.text("sign").orEmpty()
                            val line = c.text("line")?.toIntOrNull()
                            val oct = (c.text("clef-octave-change")?.toIntOrNull() ?: 0) < 0
                            if (sign == "TAB") { tabStaff = staff; continue }
                            clefOctave[staff] = oct
                            val clef = when (sign) {
                                "G" -> if (oct) Clef.TREBLE_8VB else Clef.TREBLE
                                "F" -> Clef.BASS
                                "C" -> if ((line ?: 3) >= 4) Clef.TENOR else Clef.ALTO
                                "percussion" -> Clef.PERCUSSION
                                else -> null
                            }
                            if (clef != null) clefHere[staff] = clef
                        }
                        for (sd in el.kids("staff-details")) {
                            val staff = sd.attr("number")?.toIntOrNull() ?: 1
                            val tunings = sd.kids("staff-tuning")
                            if (tunings.isEmpty()) continue
                            if (tabStaff == null) tabStaff = staff
                            instr.count = sd.text("staff-lines")?.toIntOrNull() ?: tunings.size
                            for (st in tunings) {
                                val line = st.attr("line")?.toIntOrNull() ?: continue
                                val step = st.text("tuning-step")?.firstOrNull() ?: continue
                                val alter = st.text("tuning-alter")?.toDoubleOrNull()?.toInt() ?: 0
                                val oct = st.text("tuning-octave")?.toIntOrNull() ?: continue
                                runCatching { instr.lines[line] = SpelledPitch(Letter.parse(step), alter, oct) }
                            }
                            sd.text("capo")?.toIntOrNull()?.let { instr.capo = it }
                        }
                        el.kid("transpose")?.let { t ->
                            val chrom = (t.text("chromatic")?.toIntOrNull() ?: 0) + 12 * (t.text("octave-change")?.toIntOrNull() ?: 0)
                            val diat = (t.text("diatonic")?.toIntOrNull() ?: 0) + 7 * (t.text("octave-change")?.toIntOrNull() ?: 0)
                            transpose = if (chrom == 0 && diat == 0) null else Interval(Math.abs(diat) + 1, Math.abs(chrom))
                            transposeDown = chrom < 0 || (chrom == 0 && diat < 0)
                        }
                    }
                    "backup" -> pos = maxOf(Rational.ZERO, pos - units(el))
                    "forward" -> { pos += units(el); end = maxOf(end, pos) }
                    "harmony" -> {
                        val staff = el.text("staff")?.toIntOrNull() ?: 1
                        val off = el.text("offset")?.toDoubleOrNull()?.let { Rational.of(Math.round(it), 4L * div) } ?: Rational.ZERO
                        harmony(el)?.let { ats += At(staff, maxOf(Rational.ZERO, pos + off), chord = it, diagram = el.kid("frame")?.let(::frameOf)) }
                    }
                    "direction" -> {
                        val staff = el.text("staff")?.toIntOrNull() ?: 1
                        val off = el.text("offset")?.toDoubleOrNull()?.let { Rational.of(Math.round(it), 4L * div) } ?: Rational.ZERO
                        val at = maxOf(Rational.ZERO, pos + off)
                        var words: String? = null
                        var italic = false
                        var metronome: Tempo? = null
                        for (dt in el.kids("direction-type")) for (x in dt.kids()) when (x.tagName) {
                            "words" -> { val w = x.textContent.trim(); if (w.isNotEmpty()) { words = listOfNotNull(words, w).joinToString(" "); italic = italic || x.attr("font-style") == "italic" } }
                            "rehearsal" -> rehearsal = x.textContent.trim().takeIf { it.isNotEmpty() }
                            "segno" -> jumps += Jump.SEGNO
                            "coda" -> jumps += Jump.CODA
                            "dynamics" -> x.kids().firstOrNull()?.tagName?.let { t -> Dynamic.entries.firstOrNull { it.token == t } }?.let { ats += At(staff, at, dynamic = it) }
                            "metronome" -> {
                                val unit = x.text("beat-unit")?.let { VALUES[it] }
                                val bpm = x.text("per-minute")?.let { Regex("[0-9]+(\\.[0-9]+)?").find(it)?.value?.toDoubleOrNull() }
                                if (unit != null && bpm != null && bpm > 0) metronome = Tempo(bpm, Duration(unit, x.kids("beat-unit-dot").size.coerceAtMost(3)))
                            }
                            "wedge", "octave-shift", "pedal" -> {
                                val type = x.attr("type")
                                val (group, span) = when (x.tagName) {
                                    "wedge" -> "wedge" to when (type) { "crescendo" -> Span.CRESC; "diminuendo" -> Span.DIM; else -> null }
                                    // 8va above the staff is a shift down from the pitches in the notes.
                                    "octave-shift" -> "octave" to when (type to (x.attr("size") ?: "8")) {
                                        "down" to "8" -> Span.OTTAVA_UP; "up" to "8" -> Span.OTTAVA_DOWN
                                        "down" to "15" -> Span.QUINDICESIMA_UP; "up" to "15" -> Span.QUINDICESIMA_DOWN
                                        else -> null
                                    }
                                    else -> "pedal" to if (type == "start") Span.PEDAL else null
                                }
                                when {
                                    span != null -> lineMarks += LineMark(group, span, true, x.attr("number")?.toIntOrNull() ?: 1, staff, el.text("voice"), mi, at, inclusive = false)
                                    type == "stop" -> lineMarks += LineMark(group, null, false, x.attr("number")?.toIntOrNull() ?: 1, staff, el.text("voice"), mi, at, inclusive = false)
                                    x.tagName == "octave-shift" && type != "continue" -> dropped.add("22ma lines")
                                }
                            }
                            "dashes", "bracket" -> dropped.add("dashed and bracket lines")
                        }
                        if (metronome == null) el.kid("sound")?.attr("tempo")?.toDoubleOrNull()?.takeIf { it > 0 }?.let { metronome = Tempo(it) }
                        val w = words
                        if (metronome != null) { barTempo = metronome; if (w != null) tempoText = w }
                        else if (w != null) {
                            val j = jump(w)
                            if (j != null) jumps += j else ats += At(staff, at, text = StaffText(if (italic) TextKind.EXPRESSION else TextKind.STAFF, w))
                        }
                    }
                    "barline" -> {
                        val right = (el.attr("location") ?: "right") == "right"
                        el.kid("repeat")?.let { r ->
                            if (r.attr("direction") == "forward") repeatStart = true
                            else { barEnd = Barline.REPEAT; times = (r.attr("times")?.toIntOrNull() ?: 2).coerceIn(2, 99) }
                        }
                        if (right && barEnd != Barline.REPEAT) when (el.text("bar-style")) {
                            "light-heavy", "heavy" -> barEnd = Barline.FINAL
                            "light-light", "heavy-heavy", "heavy-light" -> barEnd = Barline.DOUBLE
                            "dashed", "dotted" -> barEnd = Barline.DASHED
                        }
                        el.kid("ending")?.let { e ->
                            val nums = Regex("[0-9]+").findAll(e.attr("number").orEmpty()).map { it.value.toInt() }.toList()
                            when (e.attr("type")) {
                                "start" -> { volta = nums; openVolta = nums }
                                "stop", "discontinue" -> { if (volta.isEmpty()) volta = nums; closeVolta = true }
                            }
                        }
                        el.kid("segno")?.let { jumps += Jump.SEGNO }
                        el.kid("coda")?.let { jumps += Jump.CODA }
                    }
                    "note" -> {
                        val staff = el.text("staff")?.toIntOrNull() ?: 1
                        val voice = el.text("voice") ?: "1"
                        val vk = staff to voice
                        if (el.has("cue")) { if (!el.has("chord")) { pos += units(el); end = maxOf(end, pos) }; dropped.add("cue notes"); continue }
                        val isRest = el.has("rest")
                        val pitch = if (isRest) null else pitchOf(el, transpose, transposeDown)
                        if (!isRest && pitch == null) { dropped.add("notes with microtones or no pitch"); if (!el.has("chord") && !el.has("grace")) pos += units(el); continue }
                        val note = pitch?.let { noteOf(el, it) }
                        val typed = el.text("type")?.let { VALUES[it] }
                        val dots = el.kids("dot").size.coerceAtMost(3)
                        val grace = el.kid("grace")
                        if (grace != null) {
                            if (note == null) continue
                            val list = pendingGraces.getOrPut(vk) { ArrayList() }
                            if (el.has("chord") && list.isNotEmpty()) list[list.size - 1] = list.last().let { it.copy(notes = it.notes + note) }
                            else list += Grace(typed ?: NoteValue.EIGHTH, listOf(note), slash = grace.attr("slash") == "yes")
                            continue
                        }
                        if (el.has("chord")) {
                            val prev = lastByVoice[vk]
                            if (prev != null && note != null && !prev.rest) prev.notes += note
                            if (prev != null) noteLines(el, staff, voice, mi, prev.start)
                            continue
                        }
                        val len = units(el)
                        // Tuplets: the bracket stack, checked against the cumulative time-modification.
                        val tm = el.kid("time-modification")?.let { t ->
                            val a = t.text("actual-notes")?.toIntOrNull(); val n = t.text("normal-notes")?.toIntOrNull()
                            if (a != null && n != null && a > 0 && n > 0 && a != n) Tuplet(a, n) else null
                        }
                        val active = activeTuplets.getOrPut(vk) { java.util.TreeMap() }
                        val tupletEls = el.kids("notations").flatMap { it.kids("tuplet") }
                        for (t in tupletEls.filter { it.attr("type") == "start" }) {
                            val number = t.attr("number")?.toIntOrNull() ?: 1
                            val a = t.kid("tuplet-actual")?.text("tuplet-number")?.toIntOrNull()
                            val n = t.kid("tuplet-normal")?.text("tuplet-number")?.toIntOrNull()
                            val outer = active.values.fold(Tuplet(1, 1)) { acc, x -> Tuplet(acc.actual * x.actual, acc.normal * x.normal) }
                            active[number] = if (a != null && n != null && a > 0 && n > 0) Tuplet(a, n)
                            else if (tm != null && tm.actual % outer.actual == 0 && tm.normal % outer.normal == 0) Tuplet(tm.actual / outer.actual, tm.normal / outer.normal)
                            else tm ?: Tuplet(3, 2)
                        }
                        var tuplets: List<Tuplet> = emptyList()
                        if (tm != null) {
                            val stack = active.values.toList()
                            val product = stack.fold(Tuplet(1, 1)) { acc, x -> Tuplet(acc.actual * x.actual, acc.normal * x.normal) }
                            tuplets = when {
                                product == tm -> stack
                                Rational.of(product.normal, product.actual) == Rational.of(tm.normal, tm.actual) -> stack
                                stack.isNotEmpty() && tm.actual % product.actual == 0 && tm.normal % product.normal == 0 -> stack + Tuplet(tm.actual / product.actual, tm.normal / product.normal)
                                else -> listOf(tm)
                            }
                        }
                        for (t in tupletEls.filter { it.attr("type") == "stop" }) active.remove(t.attr("number")?.toIntOrNull() ?: 1)
                        val duration = typed?.let { Duration(it, dots, tuplets) }?.takeIf { it.length == len }
                        if (typed != null && duration == null && !(isRest && el.kid("rest")?.attr("measure") == "yes")) dropped.add("notes whose written value doesn't match their length")
                        val raw = Raw(staff, voice, pos, len, duration, isRest, el.attr("print-object") == "no")
                        note?.let { raw.notes += it }
                        raw.tuplets = tuplets
                        pendingGraces.remove(vk)?.let { raw.graces = it }
                        marksOf(el, raw.marks)
                        lyricsOf(el, raw.lyrics)
                        noteLines(el, staff, voice, mi, pos)
                        for (t in el.kids("notations").flatMap { it.kids("ornaments") }.flatMap { it.kids("tremolo") }) when (t.attr("type") ?: "single") {
                            "single" -> t.textContent.trim().toIntOrNull()?.let { n -> if (n in 1..4) raw.tremolo = n else if (n > 4) { raw.tremolo = 4; dropped.add("tremolos of more than four strokes (kept as four)") } }
                            "start", "stop" -> dropped.add("two-note tremolos")
                        }
                        raws += raw
                        lastByVoice[vk] = raw
                        if (len > Rational.ZERO) { voiceIds.getOrPut(staff) { ArrayList() }.let { if (voice !in it) it += voice } }
                        pos += len
                        end = maxOf(end, pos)
                    }
                }
                if (closeVolta) openVolta = emptyList()

                // Bar-level changes, compared with what was in force.
                val meterChange = barMeter?.takeIf { it != meterNow }
                val keyChange = barKey?.takeIf { it != keyNow }
                val tempoChange = barTempo?.takeIf { it != tempoNow }
                if (mi == 0) { firstMeter = barMeter; firstKey = barKey; firstTempo = barTempo }
                barMeter?.let { meterNow = it }; barKey?.let { keyNow = it }; barTempo?.let { tempoNow = it }
                val bar = Bar(
                    repeatStart = repeatStart, end = barEnd, times = times, volta = volta, jumps = jumps, rehearsal = rehearsal,
                    tempo = if (mi == 0) null else tempoChange, tempoText = tempoText,
                    key = if (mi == 0) null else keyChange, meter = if (mi == 0) null else meterChange,
                )
                // Voices per staff.
                val byStaff = HashMap<Int, List<List<Event>>>()
                for (staff in raws.map { it.staff }.toSet() + (1..staves)) {
                    val ids = voiceIds[staff].orEmpty()
                    val lists = ids.take(4).map { id -> raws.filter { it.staff == staff && it.voice == id }.sortedBy { it.start } }
                    if (ids.size > 4 && raws.any { it.staff == staff && it.voice in ids.drop(4) }) dropped.add("voices beyond four")
                    val voices = lists.mapIndexed { vi, list -> layout(list, vi > 0) }.toMutableList()
                    while (voices.size > 1 && voices.last().isEmpty()) voices.removeAt(voices.size - 1)
                    if (voices.isEmpty()) voices += emptyList<Event>()
                    // what sits at a point in the bar goes on voice 1's event there
                    val v1 = voices[0].toMutableList()
                    if (v1.isNotEmpty()) {
                        val starts = v1.runningFold(Rational.ZERO) { a, e -> a + e.length }
                        for (at in ats.filter { it.staff == staff || (staff == 1 && it.staff !in 1..staves) }) {
                            var i = starts.indexOfFirst { it >= at.pos }.let { if (it < 0 || it >= v1.size) v1.size - 1 else it }
                            if (i > 0 && starts[i] > at.pos) i--
                            val e = v1[i]
                            v1[i] = when {
                                at.chord != null -> if (e.chord == null) e.copy(chord = at.chord, diagram = at.diagram ?: e.diagram) else e
                                at.dynamic != null -> if (e.dynamic == null) e.copy(dynamic = at.dynamic) else e
                                at.text != null -> e.copy(texts = e.texts + at.text)
                                else -> e
                            }
                        }
                        voices[0] = v1
                    }
                    byStaff[staff] = voices
                }
                measures += MeasureOut(bar, byStaff, clefHere.toMap())
            }

            // Tab: a TAB staff with a notation staff gives the notation staff its frets.
            val tuningLines = instr.lines
            val instrument = if (tabStaff != null && tuningLines.isNotEmpty()) {
                val n = maxOf(instr.count, tuningLines.keys.max())
                val strings = (1..n).map { tuningLines[it] }
                if (strings.any { it == null }) { dropped.add("a tab staff with a string missing from its tuning"); null }
                else {
                    val s = strings.filterNotNull()
                    val known = Tunings.all.firstOrNull { it.strings == s }
                        ?: com.betterappmafia.apophenia.core.learn.TuningReference.all.firstOrNull { it.tuning.strings == s }?.tuning
                    val tuning = known ?: Tuning("custom", "Custom", s)
                    val up = if (s.first().midi >= 60) null else Interval(8, 12)
                    runCatching { Instrument(tuning, instr.capo.coerceIn(0, 21), 22, up) }.getOrNull()
                }
            } else null
            val notationStaves = (1..staves).filter { it != tabStaff }
            val parts = ArrayList<Part>()
            if (instrument != null && tabStaff != null) {
                val tab = tabStaff!!
                val main = notationStaves.firstOrNull()
                val fallback = if (instrument.tuning.strings.first().midi < 36) Clef.BASS else Clef.TREBLE
                // A fretted part's clef is plain treble or bass: the writer adds the octave-down sign itself.
                parts += staffPart(name, main, measures, instrument, fallback, lineMarks, main?.let { voiceIds[it] }.orEmpty()) { mo ->
                    if (main != null) withFrets(mo.voicesByStaff[main].orEmpty(), mo.voicesByStaff[tab].orEmpty(), instrument)
                    else mo.voicesByStaff[tab].orEmpty()
                }
                notationStaves.drop(1).forEach { s -> parts += staffPart("$name (staff $s)", s, measures, lines = lineMarks, ids = voiceIds[s].orEmpty()) }
            } else {
                val staffList = if (staves == 1) listOf(1) else notationStaves
                staffList.forEachIndexed { i, s ->
                    val label = when {
                        staffList.size == 1 -> name
                        staffList.size == 2 -> "$name (${if (i == 0) "upper" else "lower"})"
                        else -> "$name (staff $s)"
                    }
                    parts += staffPart(label, s, measures, lines = lineMarks, ids = voiceIds[s].orEmpty())
                }
                if (tabStaff != null && instrument == null && staves == 1) dropped.add("a tab staff with no tuning")
            }
            return PartRead(parts, firstMeter, firstKey, firstTempo)
        }

        /** One part from one staff's voices (or [voices]), with its clef changes where they differ from the clef in force. */
        private fun staffPart(
            label: String, staff: Int?, measures: List<MeasureOut>, instrument: Instrument? = null, fallback: Clef = Clef.TREBLE,
            lines: List<LineMark> = emptyList(), ids: List<String> = emptyList(),
            voices: (MeasureOut) -> List<List<Event>> = { mo -> staff?.let { mo.voicesByStaff[it] }.orEmpty() },
        ): Part {
            fun norm(c: Clef) = if (instrument != null && c == Clef.TREBLE_8VB) Clef.TREBLE else c
            var current: Clef? = null
            val bars = measures.mapIndexed { bi, mo ->
                val clef = staff?.let { mo.clefByStaff[it] }?.let(::norm)
                val change = if (bi > 0 && clef != null && clef != current) clef else null
                if (clef != null) current = clef
                mo.bar.copy(voices = voices(mo).ifEmpty { listOf(emptyList()) }, clef = change)
            }
            val first = staff?.let { measures.firstOrNull()?.clefByStaff?.get(it) }?.let(::norm) ?: fallback
            return Part(label, first, instrument, if (staff == null) bars else withLines(bars, lines.filter { it.staff == staff }, ids))
        }

        /**
         * The lines read from the file, as each start event's [Event.spans] (how many events of its voice the line
         * covers, across bars). A start is paired with the next stop of its kind and number in the same voice; a line
         * that never ends, or covers a single event, is left out.
         */
        private fun withLines(bars: List<Bar>, marks: List<LineMark>, ids: List<String>): List<Bar> {
            if (marks.isEmpty()) return bars
            class Slot(val bar: Int, val i: Int, val start: Rational, val end: Rational)
            val out = bars.map { b -> b.voices.map { it.toMutableList() } }
            for (vi in 0 until (bars.maxOfOrNull { it.voices.size } ?: 0)) {
                val seq = ArrayList<Slot>()
                bars.forEachIndexed { bi, b -> var t = Rational.ZERO; b.voices.getOrNull(vi).orEmpty().forEachIndexed { i, e -> seq += Slot(bi, i, t, t + e.length); t += e.length } }
                fun on(m: Int, p: Rational) = seq.indexOfFirst { it.bar == m && p >= it.start && p < it.end }.takeIf { it >= 0 } ?: seq.indexOfLast { it.bar == m && it.start <= p }
                fun before(m: Int, p: Rational) = seq.indexOfLast { it.bar < m || (it.bar == m && it.start < p) }
                val here = marks.filter { (it.voice ?: ids.firstOrNull()) == ids.getOrNull(vi) }
                for ((q, s) in here.withIndex()) {
                    if (!s.start) continue
                    val stop = here.drop(q + 1).firstOrNull { !it.start && it.group == s.group && it.number == s.number }
                    if (stop == null) { dropped.add("lines that never end"); continue }
                    val a = on(s.measure, s.pos)
                    val z = if (stop.inclusive) on(stop.measure, stop.pos) else before(stop.measure, stop.pos)
                    if (a < 0 || z - a + 1 < 2) continue
                    val slot = seq[a]
                    val e = out[slot.bar][vi][slot.i]
                    out[slot.bar][vi][slot.i] = e.copy(spans = e.spans + (s.span!! to z - a + 1))
                }
            }
            return bars.mapIndexed { bi, b -> b.copy(voices = out[bi]) }
        }

        /** A `<frame>` as a diagram, lowest string first (null = not played); frame strings count from the highest. */
        fun frameOf(f: Element): List<Int?>? {
            val n = f.text("frame-strings")?.toIntOrNull()?.takeIf { it in 1..12 } ?: return null
            val frets = MutableList<Int?>(n) { null }
            for (fn in f.kids("frame-note")) {
                val s = fn.text("string")?.toIntOrNull() ?: continue
                val fret = fn.text("fret")?.toIntOrNull() ?: continue
                if (s in 1..n && fret >= 0) frets[n - s] = fret
            }
            return frets
        }

        /** A voice from its raw events in order: gaps become rests (hidden in voices 2–4), overlaps are dropped. */
        fun layout(list: List<Raw>, inner: Boolean): List<Event> {
            val out = ArrayList<Event>()
            var at = Rational.ZERO
            for (r in list) {
                if (r.start < at) { if (r.length > Rational.ZERO) dropped.add("overlapping notes in one voice"); continue }
                if (r.start > at) split(r.start - at).forEach { out += Event(it, hidden = inner) }
                val durations = r.duration?.let { listOf(it) } ?: split(r.length)
                durations.forEachIndexed { i, d ->
                    val last = i == durations.size - 1
                    val notes = if (last) r.notes.toList() else r.notes.map { it.copy(tie = true) }
                    out += if (i == 0) Event(d, notes, marks = r.marks.toSet(), lyrics = r.lyrics.toList(), tremolo = if (r.rest) 0 else r.tremolo, graces = r.graces, hidden = r.rest && r.hidden && inner)
                    else Event(d, notes, hidden = r.rest && r.hidden && inner)
                }
                at = r.start + r.length
            }
            return out
        }

        /** Notation-staff voices with each note's string and fret from the TAB staff's note at the same time and pitch. */
        fun withFrets(notation: List<List<Event>>, tab: List<List<Event>>, inst: Instrument): List<List<Event>> {
            val frets = HashMap<Pair<Rational, Int>, Note>()
            for (v in tab) { var t = Rational.ZERO; for (e in v) { for (n in e.notes) if (n.string != null && n.fret != null) frets[t to n.pitch.midi] = n; t += e.length } }
            return notation.map { v ->
                var t = Rational.ZERO
                v.map { e ->
                    val start = t
                    t += e.length
                    e.copy(notes = e.notes.map { n ->
                        if (n.string != null && n.fret != null) n
                        else frets[start to n.pitch.midi]?.let { f -> n.copy(string = f.string, fret = f.fret, effects = n.effects + f.effects) } ?: n
                    })
                }
            }.let { voices -> voices.map { v -> v.map { e -> e.copy(notes = e.notes.map { n -> if (n.string != null && n.string !in 1..inst.tuning.stringCount) n.copy(string = null, fret = null) else n }) } } }
        }

        fun pitchOf(el: Element, transpose: Interval?, down: Boolean): SpelledPitch? {
            val p = el.kid("pitch")
            val written = if (p != null) {
                val step = p.text("step")?.firstOrNull() ?: return null
                val alterD = p.text("alter")?.toDoubleOrNull() ?: 0.0
                if (alterD % 1.0 != 0.0) return null
                val oct = p.text("octave")?.toIntOrNull() ?: return null
                runCatching { SpelledPitch(Letter.parse(step), alterD.toInt(), oct) }.getOrNull() ?: return null
            } else el.kid("unpitched")?.let { u ->
                val step = u.text("display-step")?.firstOrNull() ?: return null
                val oct = u.text("display-octave")?.toIntOrNull() ?: return null
                return runCatching { SpelledPitch(Letter.parse(step), 0, oct) }.getOrNull()
            } ?: return null
            if (transpose == null) return written
            return runCatching { if (down) written - transpose else written + transpose }.getOrNull()
        }

        fun noteOf(el: Element, pitch: SpelledPitch): Note {
            val notations = el.kids("notations")
            val tech = notations.flatMap { it.kids("technical") }
            val string = tech.firstNotNullOfOrNull { it.text("string")?.toIntOrNull() }
            val fret = tech.firstNotNullOfOrNull { it.text("fret")?.toIntOrNull() }
            val tie = el.kids("tie").any { it.attr("type") == "start" } || notations.flatMap { it.kids("tied") }.any { it.attr("type") == "start" }
            val effects = LinkedHashSet<Effect>()
            for (a in notations.flatMap { it.kids("articulations") }.flatMap { it.kids() }) when (a.tagName) {
                "staccato" -> effects += Effect.STACCATO
                "accent" -> effects += Effect.ACCENT
                "strong-accent" -> effects += Effect.HEAVY_ACCENT
                "scoop" -> effects += Effect.SLIDE_IN_BELOW
                "plop" -> effects += Effect.SLIDE_IN_ABOVE
                "falloff" -> effects += Effect.SLIDE_OUT_DOWN
                "doit" -> effects += Effect.SLIDE_OUT_UP
            }
            for (t in tech.flatMap { it.kids() }) when (t.tagName) {
                "hammer-on", "pull-off" -> if (t.attr("type") != "stop") effects += Effect.HAMMER
                "harmonic" -> effects += if (t.has("artificial")) Effect.ARTIFICIAL_HARMONIC else Effect.NATURAL_HARMONIC
                "bend" -> effects += Effect.BEND
                "tap" -> effects += if (t.attr("hand") == "left") Effect.LEFT_TAP else Effect.TAP
                // What Apophenia (and others) write for techniques MusicXML has no element for: the technique's name.
                "other-technical" -> t.textContent.trim().let { w -> Effect.entries.firstOrNull { it.label.equals(w, ignoreCase = true) } }?.let { effects += it }
            }
            if (notations.flatMap { it.kids("slide") }.any { it.attr("type") == "start" }) effects += Effect.LEGATO_SLIDE
            if (notations.flatMap { it.kids("ornaments") }.flatMap { it.kids("tremolo") }.any { it.attr("type") == "unmeasured" }) effects += Effect.TREMOLO_PICKING
            val headEl = el.kid("notehead")
            if (headEl?.attr("parentheses") == "yes") effects += Effect.GHOST
            val head = el.text("notehead")?.let { h -> Head.entries.firstOrNull { it.token == h } } ?: Head.NORMAL
            val finger = tech.firstNotNullOfOrNull { it.text("fingering") }?.takeIf { it in Note.FINGERS }.orEmpty()
            val pluck = tech.firstNotNullOfOrNull { it.text("pluck") }?.takeIf { it in Note.PLUCKS }.orEmpty()
            return Note(pitch, string, fret, tie, effects, head, finger, pluck)
        }

        fun marksOf(el: Element, out: MutableSet<Mark>) {
            val notations = el.kids("notations")
            for (a in notations.flatMap { it.kids("articulations") }.flatMap { it.kids() }) when (a.tagName) {
                "tenuto" -> out += Mark.TENUTO
                "staccatissimo", "spiccato" -> out += Mark.STACCATISSIMO
                "detached-legato" -> out += Mark.PORTATO
                "stress" -> out += Mark.STRESS
                "unstress" -> out += Mark.UNSTRESS
                "soft-accent" -> out += Mark.SOFT_ACCENT
                "breath-mark" -> out += Mark.BREATH
                "caesura" -> out += Mark.CAESURA
            }
            for (o in notations.flatMap { it.kids("ornaments") }.flatMap { it.kids() }) when (o.tagName) {
                // A trill mark starting a wavy line is the line's sign (Span.TRILL_LINE), not a trill of its own.
                "trill-mark" -> if (notations.flatMap { it.kids("ornaments") }.flatMap { it.kids("wavy-line") }.none { it.attr("type") == "start" }) out += Mark.TRILL
                "mordent" -> out += Mark.MORDENT
                "inverted-mordent" -> out += Mark.UPPER_MORDENT
                "turn" -> out += if (o.attr("slash") == "yes") Mark.TURN_SLASH else Mark.TURN
                "inverted-turn" -> out += Mark.INVERTED_TURN
            }
            for (t in notations.flatMap { it.kids("technical") }.flatMap { it.kids() }) when (t.tagName) {
                "up-bow" -> out += Mark.UP_BOW
                "down-bow" -> out += Mark.DOWN_BOW
                "open-string", "open" -> out += Mark.OPEN
                "stopped" -> out += Mark.STOPPED
                "snap-pizzicato" -> out += Mark.SNAP_PIZZ
            }
            for (f in notations.flatMap { it.kids("fermata") }) out += when (f.textContent.trim()) {
                "angled", "double-angled" -> Mark.FERMATA_SHORT
                "square", "double-square" -> Mark.FERMATA_LONG
                else -> Mark.FERMATA
            }
            for (a in notations.flatMap { it.kids("arpeggiate") }) out += when (a.attr("direction")) { "up" -> Mark.ARPEGGIO_UP; "down" -> Mark.ARPEGGIO_DOWN; else -> Mark.ARPEGGIO }
        }

        private val verses = ArrayList<String>()

        fun lyricsOf(el: Element, out: MutableList<Lyric>) {
            for (l in el.kids("lyric")) {
                val text = l.kids("text").joinToString(" ") { it.textContent }.trim()
                if (text.isEmpty() && !l.has("extend")) continue
                val number = l.attr("number") ?: "1"
                val verse = number.toIntOrNull()?.takeIf { it in 1..9 } ?: run {
                    if (number !in verses) verses += number
                    (verses.indexOf(number) + 1).coerceAtMost(9)
                }
                val syl = l.kids("syllabic").lastOrNull()?.textContent?.trim()
                if (text.isEmpty()) continue
                out += Lyric(verse, text, hyphen = syl == "begin" || syl == "middle", extend = l.has("extend"))
            }
        }
    }
}
