package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.chord.Chord
import com.betterappmafia.apophenia.core.chord.ChordParser
import com.betterappmafia.apophenia.core.chord.ChordRoot
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.pitch.SpelledPitch
import com.betterappmafia.apophenia.core.rhythm.Duration
import com.betterappmafia.apophenia.core.rhythm.NoteValue
import com.betterappmafia.apophenia.core.rhythm.Rational
import com.betterappmafia.apophenia.core.rhythm.Tempo
import com.betterappmafia.apophenia.core.rhythm.TimeSignature

/**
 * MusicXML 4.0 partwise export (plan §11: notes, chord symbols, tab, tuplets).
 *
 * Pitches are written as they sound. A fretted part (guitar, bass) gets an octave clef (G2 or F4 with
 * `<clef-octave-change>-1`) instead of a `<transpose>` (never both), and a second staff in TAB with
 * `<staff-details>`: `<staff-tuning line>` counts from the lowest string while `<technical><string>` counts from
 * the highest, the opposite directions MusicXML uses. Ties write both `<tie>` (sound) and `<tied>` (notation).
 * Tuplets carry the cumulative `<time-modification>`, with `<tuplet>` brackets on the outermost group.
 *
 * Written: notes, rests (hidden ones print-object="no"), chords, dots, tuplets, ties, voices (backup), key, time
 * (additive "2+2+3"), clef and their mid-part changes, tempo, chord symbols as `<harmony>`, lyrics, dynamics,
 * staff text, rehearsal marks, repeats, endings, final and double barlines, segno/coda and the D.C./D.S. words;
 * articulations, ornaments, fermatas, arpeggios, single-note tremolo, noteheads, fingering and plucking, grace notes,
 * slurs, glissandos, trill lines, hairpins, octave lines and pedal, chord diagrams as `<frame>`, and the Guitar Pro
 * techniques (hammer-ons, pull-offs and slides as start/stop pairs; the ones MusicXML has no element for as
 * `<other-technical>` with their name). Checked against the MusicXML 4.0 XSD in the tests.
 */
object MusicXml {
    private const val DOCTYPE = "<!DOCTYPE score-partwise PUBLIC \"-//Recordare//DTD MusicXML 4.0 Partwise//EN\" \"http://www.musicxml.org/dtds/partwise.dtd\">"

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)

    /** Divisions per quarter so that every event (and every bar) lasts a whole number of them. */
    fun divisions(score: Score): Int {
        var d = 1L
        val lengths = score.parts.flatMap { p -> p.bars.flatMap { b -> b.voices.flatten().map { it.length } } } + (0 until score.bars).map { score.meterAt(it).barLength }
        for (len in lengths) {
            val q = len * 4
            d = d / gcd(d, q.den) * q.den
        }
        return d.toInt()
    }

    private fun units(len: Rational, div: Int): Int {
        val u = len * 4 * div
        require(u.isInteger) { "$len is not a whole number of divisions ($div per quarter)" }
        return u.num.toInt()
    }

    /** MusicXML 4.0 `<mode>` for a scale id, or null when MusicXML has no name for it. */
    private fun mode(scaleId: String) = when (scaleId) {
        "major" -> "major"; "aeolian" -> "minor"
        "dorian", "phrygian", "lydian", "mixolydian", "locrian" -> scaleId
        else -> null
    }

    private fun clef(c: Clef, fretted: Boolean, number: Int? = null): String {
        val n = number?.let { " number=\"$it\"" } ?: ""
        return when (c) {
            Clef.TREBLE -> if (fretted) "<clef$n><sign>G</sign><line>2</line><clef-octave-change>-1</clef-octave-change></clef>" else "<clef$n><sign>G</sign><line>2</line></clef>"
            Clef.TREBLE_8VB -> "<clef$n><sign>G</sign><line>2</line><clef-octave-change>-1</clef-octave-change></clef>"
            Clef.BASS -> if (fretted) "<clef$n><sign>F</sign><line>4</line><clef-octave-change>-1</clef-octave-change></clef>" else "<clef$n><sign>F</sign><line>4</line></clef>"
            Clef.ALTO -> "<clef$n><sign>C</sign><line>3</line></clef>"
            Clef.TENOR -> "<clef$n><sign>C</sign><line>4</line></clef>"
            Clef.PERCUSSION -> "<clef$n><sign>percussion</sign></clef>"
        }
    }

    private fun time(t: TimeSignature) = "<time><beats>${t.xmlBeats}</beats><beat-type>${t.denominator}</beat-type></time>"

    private fun pitch(p: SpelledPitch, unpitched: Boolean) = if (unpitched)
        "<unpitched><display-step>${p.letter.name}</display-step><display-octave>${p.octave}</display-octave></unpitched>"
    else "<pitch><step>${p.letter.name}</step>${if (p.alter != 0) "<alter>${p.alter}</alter>" else ""}<octave>${p.octave}</octave></pitch>"

    private fun step(n: PitchName, tag: String) = "<$tag-step>${n.letter.name}</$tag-step>${if (n.alter != 0) "<$tag-alter>${n.alter}</$tag-alter>" else ""}"

    /**
     * A chord diagram as `<frame>`: [frets] lowest string first (null = not played, 0 = open). Frame strings are numbered
     * like `<technical><string>`, 1 the highest; a string with no `<frame-note>` isn't played (`unplayed="x"`). Up to the
     * 4th fret the diagram starts at the nut; higher, `<first-fret>` is the lowest fretted note. [Claude] at least 4 frets shown.
     */
    internal fun frame(frets: List<Int?>): String {
        val fretted = frets.filterNotNull().filter { it > 0 }
        val lo = fretted.minOrNull() ?: 1; val hi = fretted.maxOrNull() ?: 1
        val first = if (hi <= 4) 1 else lo
        return buildString {
            append("<frame unplayed=\"x\"><frame-strings>${frets.size}</frame-strings><frame-frets>${maxOf(4, hi - first + 1)}</frame-frets>")
            if (first > 1) append("<first-fret>$first</first-fret>")
            frets.forEachIndexed { i, f -> if (f != null) append("<frame-note><string>${frets.size - i}</string><fret>$f</fret></frame-note>") }
            append("</frame>")
        }
    }

    /** A chord symbol as `<harmony>`, from its typed text, with its [diagram] if it has one; null when it can't be read. */
    fun harmony(text: String, diagram: List<Int?>? = null): String? {
        val c: Chord = ChordParser.parse(text).chord ?: return null
        if (c.isNoChord) return "<harmony><root><root-step>C</root-step></root><kind text=\"N.C.\">none</kind></harmony>"
        val root = (c.root as? ChordRoot.Name)?.name ?: return null
        return buildString {
            append("<harmony><root>").append(step(root, "root")).append("</root>")
            append("<kind text=\"").append(esc(c.kindText ?: text.substringAfter(Regex("^[A-G][#b]*").find(text)?.value ?: "").substringBefore('/'))).append("\">").append(c.kind.xml).append("</kind>")
            c.bass?.let { append("<bass>").append(step(it, "bass")).append("</bass>") }
            for (d in c.degrees) {
                append("<degree").append(if (!d.printed) " print-object=\"no\"" else "").append("><degree-value>${d.value}</degree-value><degree-alter>${d.alter}</degree-alter><degree-type>${d.type.xml}</degree-type></degree>")
            }
            diagram?.takeIf { it.isNotEmpty() }?.let { append(frame(it)) }
            append("</harmony>")
        }
    }

    private fun tempo(t: Tempo, words: String?): String {
        val quarterBpm = t.bpm * (t.beatUnit.length / Rational.of(1, 4)).let { it.num.toDouble() / it.den }
        val dots = "<beat-unit-dot/>".repeat(t.beatUnit.dots)
        val bpm = if (t.bpm % 1.0 == 0.0) t.bpm.toInt().toString() else "%.2f".format(java.util.Locale.ROOT, t.bpm)
        val q = if (quarterBpm % 1.0 == 0.0) quarterBpm.toInt().toString() else "%.2f".format(java.util.Locale.ROOT, quarterBpm)
        return "<direction placement=\"above\"><direction-type>" + (words?.let { "<words>${esc(it)}</words></direction-type><direction-type>" } ?: "") +
            "<metronome><beat-unit>${t.beatUnit.value.xml}</beat-unit>$dots<per-minute>$bpm</per-minute></metronome></direction-type><sound tempo=\"$q\"/></direction>"
    }

    private fun durationXml(d: Duration): String = buildString {
        append("<type>${d.value.xml}</type>")
        repeat(d.dots) { append("<dot/>") }
        d.timeModification?.let { append("<time-modification><actual-notes>${it.actual}</actual-notes><normal-notes>${it.normal}</normal-notes></time-modification>") }
    }

    /** For each event of a voice: "start", "stop", "both" or null, bracketing runs of the outermost tuplet. */
    internal fun tupletBrackets(voice: List<Event>): List<String?> {
        val out = MutableList<String?>(voice.size) { null }
        var i = 0
        while (i < voice.size) {
            val t = voice[i].duration.tuplets.firstOrNull()
            if (t == null) { i++; continue }
            var j = i
            var untupled = Rational.ZERO
            while (j < voice.size && voice[j].duration.tuplets.firstOrNull() == t) {
                untupled += voice[j].length / t.factor
                j++
                val unit = untupled / t.actual
                if (unit.num == 1L && unit.den > 0 && (unit.den and (unit.den - 1)) == 0L) break
            }
            out[i] = "start"; out[j - 1] = if (j - 1 == i) "both" else "stop"
            i = j
        }
        return out
    }

    /** Where each [Mark] goes in `<notations>`: the element and its group (articulations, ornaments, technical), or a fermata / arpeggiate. */
    private fun markXml(m: Mark): Pair<String, String> = when (m) {
        Mark.STACCATISSIMO -> "articulations" to "<staccatissimo/>"
        Mark.TENUTO -> "articulations" to "<tenuto/>"
        Mark.PORTATO -> "articulations" to "<detached-legato/>"
        Mark.STRESS -> "articulations" to "<stress/>"
        Mark.UNSTRESS -> "articulations" to "<unstress/>"
        Mark.SOFT_ACCENT -> "articulations" to "<soft-accent/>"
        Mark.BREATH -> "articulations" to "<breath-mark/>"
        Mark.CAESURA -> "articulations" to "<caesura/>"
        Mark.UP_BOW -> "technical" to "<up-bow/>"
        Mark.DOWN_BOW -> "technical" to "<down-bow/>"
        Mark.OPEN -> "technical" to "<open/>"
        Mark.STOPPED -> "technical" to "<stopped/>"
        Mark.SNAP_PIZZ -> "technical" to "<snap-pizzicato/>"
        Mark.FERMATA -> "" to "<fermata type=\"upright\"/>"
        Mark.FERMATA_SHORT -> "" to "<fermata type=\"upright\">angled</fermata>"
        Mark.FERMATA_LONG -> "" to "<fermata type=\"upright\">square</fermata>"
        Mark.TRILL -> "ornaments" to "<trill-mark/>"
        Mark.UPPER_MORDENT -> "ornaments" to "<inverted-mordent/>"
        Mark.MORDENT -> "ornaments" to "<mordent/>"
        Mark.TURN -> "ornaments" to "<turn/>"
        Mark.INVERTED_TURN -> "ornaments" to "<inverted-turn/>"
        Mark.TURN_SLASH -> "ornaments" to "<turn slash=\"yes\"/>"
        Mark.ARPEGGIO -> "" to "<arpeggiate/>"
        Mark.ARPEGGIO_UP -> "" to "<arpeggiate direction=\"up\"/>"
        Mark.ARPEGGIO_DOWN -> "" to "<arpeggiate direction=\"down\"/>"
    }

    /**
     * Where each note [Effect] goes, or null for the ones written elsewhere (ghost and dead as the notehead; hammer-ons
     * and slides as start/stop pairs). Guitar Pro techniques MusicXML has no element for are `<other-technical>` with
     * the effect's name, which is how they are read back. [Claude] a bend is written as a full bend (2 semitones):
     * the stored score doesn't keep the amount.
     */
    private fun effectXml(f: Effect): Pair<String, String>? = when (f) {
        Effect.ACCENT -> "articulations" to "<accent/>"
        Effect.HEAVY_ACCENT -> "articulations" to "<strong-accent/>"
        Effect.STACCATO -> "articulations" to "<staccato/>"
        Effect.SLIDE_IN_BELOW -> "articulations" to "<scoop/>"
        Effect.SLIDE_IN_ABOVE -> "articulations" to "<plop/>"
        Effect.SLIDE_OUT_DOWN -> "articulations" to "<falloff/>"
        Effect.SLIDE_OUT_UP -> "articulations" to "<doit/>"
        Effect.NATURAL_HARMONIC -> "technical" to "<harmonic><natural/></harmonic>"
        Effect.ARTIFICIAL_HARMONIC -> "technical" to "<harmonic><artificial/></harmonic>"
        Effect.BEND -> "technical" to "<bend><bend-alter>2</bend-alter></bend>"
        Effect.TAP -> "technical" to "<tap hand=\"right\"/>"
        Effect.LEFT_TAP -> "technical" to "<tap hand=\"left\"/>"
        Effect.TREMOLO_PICKING -> "ornaments" to "<tremolo type=\"unmeasured\">0</tremolo>"
        Effect.GHOST, Effect.DEAD, Effect.HAMMER, Effect.LEGATO_SLIDE, Effect.SHIFT_SLIDE -> null
        Effect.LET_RING, Effect.PALM_MUTE, Effect.FADE_IN, Effect.FADE_OUT, Effect.VOLUME_SWELL, Effect.WHAMMY,
        Effect.VIBRATO, Effect.WIDE_VIBRATO, Effect.SLAP, Effect.POP -> "technical" to "<other-technical>${esc(f.label)}</other-technical>"
    }

    /** The direction that starts (or, [stop], ends) a line drawn as a direction: hairpins, octave lines, pedal. */
    private fun spanDirection(s: Span, stop: Boolean, voiceNo: Int): String? {
        val (place, el) = when (s) {
            Span.CRESC -> "below" to if (stop) "<wedge type=\"stop\"/>" else "<wedge type=\"crescendo\"/>"
            Span.DIM -> "below" to if (stop) "<wedge type=\"stop\"/>" else "<wedge type=\"diminuendo\"/>"
            // An 8va line is a shift down from the pitches written in the notes (MusicXML 4.0, octave-shift).
            Span.OTTAVA_UP, Span.QUINDICESIMA_UP, Span.OTTAVA_DOWN, Span.QUINDICESIMA_DOWN -> {
                val size = if (s == Span.OTTAVA_UP || s == Span.OTTAVA_DOWN) 8 else 15
                (if (s.octaves > 0) "above" else "below") to "<octave-shift type=\"${if (stop) "stop" else if (s.octaves > 0) "down" else "up"}\" size=\"$size\"/>"
            }
            Span.PEDAL -> "below" to "<pedal type=\"${if (stop) "stop" else "start"}\" line=\"yes\"/>"
            Span.SLUR, Span.GLISS, Span.TRILL_LINE -> return null
        }
        return "<direction placement=\"$place\"><direction-type>$el</direction-type><voice>$voiceNo</voice></direction>"
    }

    /**
     * What reaches from one event to a later one in the same voice, across bars: lines ([Span]: where each ends, and
     * the number a slur, glissando or trill line carries, 1–4 per voice so overlapping ones stay apart) and the
     * note-to-note techniques (a hammer-on or pull-off, by whether the next note is higher, and slides: started on this
     * note, stopped on the next event's note on the same string, or the nearest pitch). Events are (voice, bar, index).
     */
    private class Links(p: Part) {
        val spanStops = HashMap<Triple<Int, Int, Int>, MutableList<Pair<Span, Int>>>()
        val spanNumber = HashMap<Pair<Triple<Int, Int, Int>, Span>, Int>()
        val linkStarts = HashMap<Triple<Int, Int, Int>, HashMap<Int, Pair<String, Int>>>()
        val linkStops = HashMap<Triple<Int, Int, Int>, HashMap<Int, Pair<String, Int>>>()

        init {
            val voices = p.bars.maxOfOrNull { it.voices.size } ?: 0
            for (vi in 0 until voices) {
                val seq = p.bars.flatMapIndexed { bi, b -> b.voices.getOrNull(vi).orEmpty().mapIndexed { ei, e -> Triple(vi, bi, ei) to e } }
                val open = ArrayList<Pair<Int, Int>>()          // (index of the last event, number) of the lines still open
                seq.forEachIndexed { k, (at, e) ->
                    open.removeAll { it.first < k }
                    for ((s, count) in e.spans) {
                        val end = minOf(k + count - 1, seq.lastIndex)
                        val num = vi * 4 + ((1..4).firstOrNull { c -> open.none { it.second == vi * 4 + c } } ?: 1)
                        open += end to num
                        spanNumber[at to s] = num
                        spanStops.getOrPut(seq[end].first) { ArrayList() } += s to num
                    }
                    val next = seq.getOrNull(k + 1)?.takeIf { !it.second.isRest } ?: return@forEachIndexed
                    e.notes.forEachIndexed { ni, n ->
                        val slide = Effect.LEGATO_SLIDE in n.effects || Effect.SHIFT_SLIDE in n.effects
                        if (Effect.HAMMER !in n.effects && !slide) return@forEachIndexed
                        val to = next.second.notes.indexOfFirst { n.string != null && it.string == n.string }.takeIf { it >= 0 }
                            ?: next.second.notes.indices.minBy { kotlin.math.abs(next.second.notes[it].pitch.midi - n.pitch.midi) }
                        val tag = if (slide) "slide" else if (next.second.notes[to].pitch.midi >= n.pitch.midi) "hammer-on" else "pull-off"
                        linkStarts.getOrPut(at) { HashMap() }[ni] = tag to ni + 1
                        linkStops.getOrPut(next.first) { HashMap() }[to] = tag to ni + 1
                    }
                }
            }
        }
    }

    /** `<notehead>`: a written notehead, a dead note as x, a ghost note in parentheses. */
    private fun headXml(n: Note): String? {
        val ghost = Effect.GHOST in n.effects
        val head = if (n.head != Head.NORMAL) n.head.token else if (Effect.DEAD in n.effects) "x" else if (ghost) "normal" else return null
        return "<notehead${if (ghost) " parentheses=\"yes\"" else ""}>$head</notehead>"
    }

    /** A grace note or chord: `<grace>` (slashed for an acciaccatura), no duration; on the TAB staff with its string and fret. */
    private fun graceXml(sb: StringBuilder, g: Grace, voiceNo: Int, staff: Int, staves: Int, unpitched: Boolean) {
        g.notes.forEachIndexed { i, n ->
            sb.append("      <note><grace").append(if (g.slash) " slash=\"yes\"" else "").append("/>")
            if (i > 0) sb.append("<chord/>")
            sb.append(pitch(n.pitch, unpitched)).append("<voice>$voiceNo</voice><type>${g.value.xml}</type>")
            if (staves == 2) sb.append("<staff>$staff</staff>")
            if (staff == 2 && n.string != null && n.fret != null) sb.append("<notations><technical><string>${n.string}</string><fret>${n.fret}</fret></technical></notations>")
            sb.append("</note>\n")
        }
    }

    private fun syllabic(prev: Lyric?, l: Lyric) = when {
        prev?.hyphen == true && l.hyphen -> "middle"
        prev?.hyphen == true -> "end"
        l.hyphen -> "begin"
        else -> "single"
    }

    fun write(score: Score): String {
        val div = divisions(score)
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n").append(DOCTYPE).append('\n')
        sb.append("<score-partwise version=\"4.0\">\n")
        if (score.title.isNotBlank()) sb.append("  <work><work-title>${esc(score.title)}</work-title></work>\n")
        sb.append("  <identification><encoding><software>Apophenia</software><encoding-date>${java.time.LocalDate.now()}</encoding-date>")
        sb.append("<supports element=\"accidental\" type=\"no\"/><supports element=\"beam\" type=\"no\"/><supports element=\"stem\" type=\"no\"/></encoding></identification>\n")
        sb.append("  <part-list>\n")
        score.parts.forEachIndexed { i, p -> sb.append("    <score-part id=\"P${i + 1}\"><part-name>${esc(p.name.ifBlank { "Part ${i + 1}" })}</part-name></score-part>\n") }
        sb.append("  </part-list>\n")
        score.parts.forEachIndexed { pi, p -> part(sb, score, p, pi, div) }
        sb.append("</score-partwise>\n")
        return sb.toString()
    }

    private fun part(sb: StringBuilder, score: Score, p: Part, pi: Int, div: Int) {
        val fretted = p.instrument != null
        val staves = if (fretted) 2 else 1
        val unpitched = p.clef == Clef.PERCUSSION
        sb.append("  <part id=\"P${pi + 1}\">\n")
        val lastLyric = HashMap<Pair<Int, Int>, Lyric>()   // (voice, verse) → previous syllable
        val tiedFrom = HashMap<Int, Set<Int>>()             // voice → MIDI numbers tied into the next event
        val links = Links(p)
        p.bars.forEachIndexed { bi, bar ->
            sb.append("    <measure number=\"${bi + 1}\">\n")
            val meter = score.meterAt(bi)
            // attributes: the first bar has everything; later bars only what changes
            val attrs = StringBuilder()
            if (bi == 0) {
                attrs.append("<divisions>$div</divisions>")
                val key = score.keyAt(0)
                attrs.append("<key><fifths>${Notation.fifths(key)}</fifths>${key?.let { mode(it.scaleId) }?.let { "<mode>$it</mode>" } ?: ""}</key>")
                attrs.append(time(meter))
                if (staves == 2) attrs.append("<staves>2</staves>")
                attrs.append(clef(p.clefAt(0), fretted, if (staves == 2) 1 else null))
                p.instrument?.let { inst ->
                    attrs.append("<clef number=\"2\"><sign>TAB</sign><line>5</line></clef>")
                    attrs.append("<staff-details number=\"2\"><staff-lines>${inst.tuning.stringCount}</staff-lines>")
                    // <staff-tuning line> counts from the lowest string: line 1 is the last string number.
                    for (line in 1..inst.tuning.stringCount) {
                        val open = inst.tuning.strings[line - 1]
                        attrs.append("<staff-tuning line=\"$line\"><tuning-step>${open.letter.name}</tuning-step>${if (open.alter != 0) "<tuning-alter>${open.alter}</tuning-alter>" else ""}<tuning-octave>${open.octave}</tuning-octave></staff-tuning>")
                    }
                    if (inst.capo > 0) attrs.append("<capo>${inst.capo}</capo>")
                    attrs.append("</staff-details>")
                }
            } else {
                bar.key?.let { k -> attrs.append("<key><fifths>${Notation.fifths(k)}</fifths>${mode(k.scaleId)?.let { "<mode>$it</mode>" } ?: ""}</key>") }
                bar.meter?.let { attrs.append(time(it)) }
                bar.clef?.let { attrs.append(clef(it, fretted, if (staves == 2) 1 else null)) }
            }
            if (attrs.isNotEmpty()) sb.append("      <attributes>").append(attrs).append("</attributes>\n")
            // left barline
            val prevVolta = p.bars.getOrNull(bi - 1)?.volta.orEmpty()
            val nextVolta = p.bars.getOrNull(bi + 1)?.volta.orEmpty()
            if (bar.repeatStart || (bar.volta.isNotEmpty() && bar.volta != prevVolta)) {
                sb.append("      <barline location=\"left\">")
                if (bar.repeatStart) sb.append("<bar-style>heavy-light</bar-style>")
                if (bar.volta.isNotEmpty() && bar.volta != prevVolta) sb.append("<ending number=\"${bar.volta.joinToString(",")}\" type=\"start\">${bar.volta.joinToString(", ") { "$it." }}</ending>")
                if (bar.repeatStart) sb.append("<repeat direction=\"forward\"/>")
                sb.append("</barline>\n")
            }
            // directions at the start of the bar
            if (bi == 0) sb.append("      ").append(tempo(score.tempoAt(0), bar.tempoText)).append('\n')
            else bar.tempo?.let { sb.append("      ").append(tempo(it, bar.tempoText)).append('\n') }
            bar.rehearsal?.let { sb.append("      <direction placement=\"above\"><direction-type><rehearsal>${esc(it)}</rehearsal></direction-type></direction>\n") }
            for (j in bar.jumps.filter { it.atStart }) sb.append("      <direction placement=\"above\"><direction-type>${if (j == Jump.SEGNO) "<segno/>" else "<coda/>"}</direction-type></direction>\n")
            // voices on staff 1, then (fretted) the same notes on the TAB staff
            val barUnits = units(meter.barLength, div)
            for (staff in 1..staves) bar.voices.forEachIndexed { vi, voice ->
                val voiceNo = vi + 1 + (staff - 1) * 4
                if (staff > 1 || vi > 0) sb.append("      <backup><duration>$barUnits</duration></backup>\n")
                val brackets = tupletBrackets(voice)
                var used = 0
                voice.forEachIndexed { ei, e ->
                    val units = units(e.length, div)
                    used += units
                    val at = Triple(vi, bi, ei)
                    if (staff == 1) {
                        e.chord?.let { harmony(it, e.diagram) }?.let { sb.append("      ").append(it).append('\n') }
                        e.dynamic?.let { sb.append("      <direction placement=\"below\"><direction-type><dynamics><${it.token}/></dynamics></direction-type><voice>$voiceNo</voice></direction>\n") }
                        for (t in e.texts) sb.append("      <direction placement=\"${if (t.kind == TextKind.EXPRESSION) "below" else "above"}\"><direction-type><words${if (t.kind == TextKind.EXPRESSION) " font-style=\"italic\"" else ""}>${esc(t.text)}</words></direction-type><voice>$voiceNo</voice></direction>\n")
                        for (s in e.spans.keys) spanDirection(s, false, voiceNo)?.let { sb.append("      ").append(it).append('\n') }
                    }
                    for (g in e.graces.filter { !it.after }) graceXml(sb, g, voiceNo, staff, staves, unpitched)
                    val notes: List<Note?> = if (e.isRest) listOf(null) else e.notes
                    val tiedIn = tiedFrom[voiceNo].orEmpty()
                    notes.forEachIndexed { ni, n ->
                        sb.append("      <note").append(if (e.isRest && e.hidden) " print-object=\"no\"" else "").append(">")
                        if (ni > 0) sb.append("<chord/>")
                        if (n == null) sb.append("<rest/>") else sb.append(pitch(n.pitch, unpitched))
                        sb.append("<duration>$units</duration>")
                        val stop = n != null && n.pitch.midi in tiedIn
                        val start = n != null && n.tie
                        if (stop) sb.append("<tie type=\"stop\"/>")
                        if (start) sb.append("<tie type=\"start\"/>")
                        sb.append("<voice>$voiceNo</voice>").append(durationXml(e.duration))
                        if (staff == 1 && n != null) headXml(n)?.let { sb.append(it) }
                        if (staves == 2) sb.append("<staff>$staff</staff>")
                        val notations = StringBuilder()
                        if (stop) notations.append("<tied type=\"stop\"/>")
                        if (start) notations.append("<tied type=\"start\"/>")
                        if (ni == 0) brackets[ei]?.let { b ->
                            val outer = e.duration.tuplets.first()
                            val show = "<tuplet-actual><tuplet-number>${outer.actual}</tuplet-number></tuplet-actual><tuplet-normal><tuplet-number>${outer.normal}</tuplet-number></tuplet-normal>"
                            if (b == "start" || b == "both") notations.append("<tuplet type=\"start\" bracket=\"yes\">$show</tuplet>")
                            if (b == "stop" || b == "both") notations.append("<tuplet type=\"stop\"/>")
                        }
                        val groups = linkedMapOf("ornaments" to StringBuilder(), "technical" to StringBuilder(), "articulations" to StringBuilder())
                        fun put(g: String, x: String) { if (g.isEmpty()) notations.append(x) else groups.getValue(g).append(x) }
                        if (staff == 1) {
                            if (ni == 0) {
                                links.spanStops[at]?.forEach { (s, num) -> when (s) {
                                    Span.SLUR -> put("", "<slur type=\"stop\" number=\"$num\"/>")
                                    Span.GLISS -> put("", "<glissando type=\"stop\" number=\"$num\"/>")
                                    Span.TRILL_LINE -> put("ornaments", "<wavy-line type=\"stop\" number=\"$num\"/>")
                                    else -> {}
                                } }
                                for (s in e.spans.keys) { val num = links.spanNumber.getValue(at to s); when (s) {
                                    Span.SLUR -> put("", "<slur type=\"start\" number=\"$num\"/>")
                                    Span.GLISS -> put("", "<glissando type=\"start\" line-type=\"wavy\" number=\"$num\"/>")
                                    Span.TRILL_LINE -> { put("ornaments", "<trill-mark/>"); put("ornaments", "<wavy-line type=\"start\" number=\"$num\"/>") }
                                    else -> {}
                                } }
                                for (m in e.marks.filter { !it.arpeggio }) markXml(m).let { (g, x) -> put(g, x) }
                                if (e.tremolo > 0) put("ornaments", "<tremolo type=\"single\">${e.tremolo}</tremolo>")
                            }
                            for (m in e.marks.filter { it.arpeggio }) put("", markXml(m).second)
                            n?.let {
                                if (it.finger.isNotEmpty()) put("technical", "<fingering>${it.finger}</fingering>")
                                if (it.pluck.isNotEmpty()) put("technical", "<pluck>${it.pluck}</pluck>")
                                if (it.showString && it.string != null) put("technical", "<string>${it.string}</string>")
                            }
                        }
                        if (n != null) {
                            for (f in n.effects) effectXml(f)?.let { (g, x) -> put(g, x) }
                            // Hammer-ons and slides: numbered by the note's place in the chord, the TAB staff's from 9.
                            val off = (staff - 1) * 8
                            links.linkStops[at]?.get(ni)?.let { (tag, num) -> if (tag == "slide") put("", "<slide type=\"stop\" number=\"${num + off}\"/>") else put("technical", "<$tag type=\"stop\" number=\"${num + off}\"/>") }
                            links.linkStarts[at]?.get(ni)?.let { (tag, num) ->
                                when (tag) {
                                    "slide" -> put("", "<slide type=\"start\" line-type=\"solid\" number=\"${num + off}\"/>")
                                    "hammer-on" -> put("technical", "<hammer-on type=\"start\" number=\"${num + off}\">H</hammer-on>")
                                    else -> put("technical", "<pull-off type=\"start\" number=\"${num + off}\">P</pull-off>")
                                }
                            }
                        }
                        if (staff == 2 && n?.string != null && n.fret != null) put("technical", "<string>${n.string}</string><fret>${n.fret}</fret>")
                        for ((g, x) in groups) if (x.isNotEmpty()) notations.append("<$g>").append(x).append("</$g>")
                        if (notations.isNotEmpty()) sb.append("<notations>").append(notations).append("</notations>")
                        if (staff == 1 && ni == 0) for (l in e.lyrics) {
                            val key = voiceNo to l.verse
                            sb.append("<lyric number=\"${l.verse}\"><syllabic>${syllabic(lastLyric[key], l)}</syllabic><text>${esc(l.text)}</text>${if (l.extend) "<extend/>" else ""}</lyric>")
                            lastLyric[key] = l
                        }
                        sb.append("</note>\n")
                    }
                    for (g in e.graces.filter { it.after }) graceXml(sb, g, voiceNo, staff, staves, unpitched)
                    if (staff == 1) links.spanStops[at]?.forEach { (s, _) -> spanDirection(s, true, voiceNo)?.let { sb.append("      ").append(it).append('\n') } }
                    tiedFrom[voiceNo] = e.notes.filter { it.tie }.map { it.pitch.midi }.toSet()
                }
                if (used < barUnits) sb.append("      <forward><duration>${barUnits - used}</duration><voice>$voiceNo</voice>${if (staves == 2) "<staff>$staff</staff>" else ""}</forward>\n")
            }
            // the end of the bar
            for (j in bar.jumps.filter { !it.atStart }) sb.append("      <direction placement=\"above\"><direction-type><words>${esc(j.text)}</words></direction-type></direction>\n")
            val endingStop = bar.volta.isNotEmpty() && bar.volta != nextVolta
            if (bar.end != Barline.SINGLE || endingStop) {
                sb.append("      <barline location=\"right\">")
                when (bar.end) {
                    Barline.REPEAT, Barline.FINAL -> sb.append("<bar-style>light-heavy</bar-style>")
                    Barline.DOUBLE -> sb.append("<bar-style>light-light</bar-style>")
                    Barline.DASHED -> sb.append("<bar-style>dashed</bar-style>")
                    Barline.SINGLE -> {}
                }
                if (endingStop) sb.append("<ending number=\"${bar.volta.joinToString(",")}\" type=\"${if (bar.end == Barline.REPEAT) "stop" else "discontinue"}\"/>")
                if (bar.end == Barline.REPEAT) sb.append("<repeat direction=\"backward\"${if (bar.times != 2) " times=\"${bar.times}\"" else ""}/>")
                sb.append("</barline>\n")
            }
            sb.append("    </measure>\n")
        }
        sb.append("  </part>\n")
    }
}
