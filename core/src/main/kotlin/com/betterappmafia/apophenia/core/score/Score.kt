package com.betterappmafia.apophenia.core.score

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.instrument.Instrument
import com.betterappmafia.apophenia.core.instrument.Tuning
import com.betterappmafia.apophenia.core.instrument.Tunings
import com.betterappmafia.apophenia.core.pitch.SpelledPitch
import com.betterappmafia.apophenia.core.rhythm.Duration
import com.betterappmafia.apophenia.core.rhythm.NoteValue
import com.betterappmafia.apophenia.core.rhythm.Rational
import com.betterappmafia.apophenia.core.rhythm.Tempo
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import com.betterappmafia.apophenia.core.rhythm.Tuplet

/**
 * Written music (plan §6, §10.5a): one model that the notation, tab and piano-roll views all show, so an
 * edit in one is in the others. A score has parts; a part has bars; a bar has voices; a voice is a run of
 * events (a rest, a note or a chord) with exact durations. Pitches are spelled and **sounding** (a bass's
 * notation is written an octave up from them); a fretted part's notes also carry string and fret.
 */
data class Score(
    val title: String = "",
    val meter: TimeSignature = TimeSignature.of(4, 4),
    val key: KeyMode? = null,
    val tempo: Tempo = Tempo(120.0),
    val parts: List<Part> = emptyList(),
) {
    val bars: Int get() = parts.maxOfOrNull { it.bars.size } ?: 0

    /** The meter, key and tempo in force at [bar]: the last change at or before it (read from the first part), else the score's. */
    fun meterAt(bar: Int): TimeSignature = lastChange(bar) { it.meter } ?: meter
    fun keyAt(bar: Int): KeyMode? = lastChange(bar) { it.key } ?: key
    fun tempoAt(bar: Int): Tempo = lastChange(bar) { it.tempo } ?: tempo

    private fun <T> lastChange(bar: Int, f: (Bar) -> T?): T? {
        val bars = parts.firstOrNull()?.bars ?: return null
        for (i in minOf(bar, bars.size - 1) downTo 0) f(bars[i])?.let { return it }
        return null
    }
}

enum class Clef(val token: String) {
    TREBLE("treble"), TREBLE_8VB("treble-8vb"), BASS("bass"), ALTO("alto"), TENOR("tenor"), PERCUSSION("percussion");

    companion object {
        fun of(token: String) = entries.firstOrNull { it.token == token } ?: throw IllegalArgumentException("unknown clef '$token'")
    }
}

/** A part: its name, clef, and for fretted parts the instrument (tuning, capo, frets). */
data class Part(val name: String, val clef: Clef, val instrument: Instrument? = null, val bars: List<Bar> = emptyList()) {
    val fretted: Boolean get() = instrument != null

    /** The clef in force at [bar]: a mid-part clef change, else the part's. */
    fun clefAt(bar: Int): Clef {
        for (i in minOf(bar, bars.size - 1) downTo 0) bars[i].clef?.let { return it }
        return clef
    }
}

/**
 * One bar of a part: voice 1 first; up to four voices. The rest is what sits on the bar itself: a start
 * repeat, the closing [end] barline (an end repeat plays [times] times), volta endings, segno/coda/D.S.
 * marks, a rehearsal mark, and changes of clef, key, meter or tempo that start here.
 */
data class Bar(
    val voices: List<List<Event>> = listOf(emptyList()),
    val repeatStart: Boolean = false,
    val end: Barline = Barline.SINGLE,
    val times: Int = 2,
    val volta: List<Int> = emptyList(),
    val jumps: Set<Jump> = emptySet(),
    val rehearsal: String? = null,
    val tempo: Tempo? = null,
    val tempoText: String? = null,
    val clef: Clef? = null,
    val key: KeyMode? = null,
    val meter: TimeSignature? = null,
) {
    init {
        require(voices.size in 1..4) { "a bar has one to four voices" }
        require(times in 2..99) { "a repeat plays 2 to 99 times" }
    }
}

/**
 * A rest (no notes), a note, or a chord, lasting [duration], with what is written on it: [marks]
 * (articulations, ornaments, fermatas), a [dynamic], [spans] (slurs, hairpins, 8va, pedal… each covering
 * this event and the next n−1 in the voice), a [chord] symbol as typed (ASCII) with an optional fretboard
 * [diagram] (frets lowest string first, null = not played), staff [texts], [lyrics], single-note [tremolo]
 * strokes (0–4) and [graces]. A rest in voices 2–4 can be [hidden].
 */
data class Event(
    val duration: Duration,
    val notes: List<Note> = emptyList(),
    val marks: Set<Mark> = emptySet(),
    val dynamic: Dynamic? = null,
    val spans: Map<Span, Int> = emptyMap(),
    val chord: String? = null,
    val diagram: List<Int?>? = null,
    val texts: List<StaffText> = emptyList(),
    val lyrics: List<Lyric> = emptyList(),
    val tremolo: Int = 0,
    val graces: List<Grace> = emptyList(),
    val hidden: Boolean = false,
) {
    init {
        require(tremolo in 0..4) { "tremolo strokes 0 to 4" }
        require(spans.values.all { it >= 2 }) { "a line covers at least two events" }
    }
    val isRest: Boolean get() = notes.isEmpty()
    val length: Rational get() = duration.length
}

/**
 * A note: sounding [pitch], and on a fretted part its [string] (1 = highest-positioned) and [fret];
 * [tie] ties it to the same pitch in the next event; [effects] are Guitar Pro's techniques; [head] its
 * notehead; fingering as written: [finger] (left hand 0–4 and T, or piano 1–5), [pluck] (p i m a c) and a
 * circled string number when [showString].
 */
data class Note(
    val pitch: SpelledPitch,
    val string: Int? = null,
    val fret: Int? = null,
    val tie: Boolean = false,
    val effects: Set<Effect> = emptySet(),
    val head: Head = Head.NORMAL,
    val finger: String = "",
    val pluck: String = "",
    val showString: Boolean = false,
    /** MIDI velocity 1–127 for playback; null plays at the written dynamics (piano roll velocity lane, v4). */
    val velocity: Int? = null,
) {
    init {
        require(finger.isEmpty() || finger in FINGERS) { "finger '$finger'" }
        require(pluck.isEmpty() || pluck in PLUCKS) { "pluck '$pluck'" }
        require(velocity == null || velocity in 1..127) { "velocity 1 to 127" }
    }

    companion object {
        val FINGERS = listOf("0", "1", "2", "3", "4", "5", "T")
        val PLUCKS = listOf("p", "i", "m", "a", "c")
    }
}

/**
 * Techniques on a note, Guitar Pro 8's effect set (`docs/research/notation-pianoroll-tab-editors-2026-10-21.md`).
 * [tab] is how the tab view marks it; tokens are the stored form.
 */
enum class Effect(val token: String, val label: String, val tab: String) {
    GHOST("ghost", "Ghost note", "( )"), ACCENT("accent", "Accent", ">"), HEAVY_ACCENT("heavy-accent", "Heavy accent", "^"),
    STACCATO("staccato", "Staccato", "·"), LET_RING("let-ring", "Let ring", "l.r."), PALM_MUTE("palm-mute", "Palm mute", "P.M."),
    DEAD("dead", "Dead note", "x"), FADE_IN("fade-in", "Fade in", "<"), FADE_OUT("fade-out", "Fade out", ">"),
    VOLUME_SWELL("swell", "Volume swell", "<>"),
    HAMMER("hammer", "Hammer-on / pull-off", "h"), LEGATO_SLIDE("legato-slide", "Legato slide", "/"), SHIFT_SLIDE("shift-slide", "Shift slide", "/"),
    SLIDE_IN_BELOW("slide-in-below", "Slide in from below", "/"), SLIDE_IN_ABOVE("slide-in-above", "Slide in from above", "\\"),
    SLIDE_OUT_DOWN("slide-out-down", "Slide out down", "\\"), SLIDE_OUT_UP("slide-out-up", "Slide out up", "/"),
    BEND("bend", "Bend", "b"), WHAMMY("whammy", "Whammy bar", "w/bar"), LEFT_TAP("left-tap", "Left-hand tapping", "+"), TAP("tap", "Tapping", "T"),
    NATURAL_HARMONIC("harmonic", "Natural harmonic", "<>"), ARTIFICIAL_HARMONIC("artificial-harmonic", "Artificial harmonic", "A.H."),
    TREMOLO_PICKING("tremolo-picking", "Tremolo picking", "///"), VIBRATO("vibrato", "Vibrato", "~"), WIDE_VIBRATO("wide-vibrato", "Wide vibrato", "~~"),
    SLAP("slap", "Slap", "S"), POP("pop", "Pop", "P");

    companion object {
        fun of(token: String) = entries.firstOrNull { it.token == token } ?: throw IllegalArgumentException("unknown effect '$token'")
    }
}
/**
 * The stored form (plan §6.5: ASCII only), version 3. Line by line:
 * ```
 * score 3
 * title Chorus
 * meter 4/4                     (or 2+2+3/8)
 * key D dorian                  (optional)
 * tempo 96 q                    (BPM of a beat unit: q, q., e, h…)
 * part Bass  clef=bass  tuning=D1 A1 D2 G2  capo=0  frets=24
 * bar  rehearsal=A  v1 h:D2@4/0 q:C3@3/3[palm-mute] q:r{marks=fermata}  |  v2 …
 * ```
 * Events are `<duration>:<notes>`: the duration's value letter (`W h q e s t x` for whole to 64th, `B` for
 * breve), dots as `.`, tuplets as `/3:2` (outermost first); notes are `r` or comma-separated pitches with an
 * optional `@string/fret`, a `~` when tied on, and `[…]` holding effects and `head=`, `finger=`, `pluck=`,
 * `show-string`, `vel=` (playback velocity, v4). What else is on an event follows in `{…}`, `;`-separated: `marks=`, `dyn=`,
 * `line=slur:2,cresc:3`, `chord=`, `diagram=x.3.2.0.1.0`, `text=e:dolce`, `lyric=1-:la` (verse, then `-` for
 * a hyphen and `_` for a melisma line), `trem=`, `hidden=1` (a hidden rest, v3), `grace=e/:D4+s:E4` (value, `/` slashed, `>` after the
 * note). A bar's own fields (`start=repeat`, `end=`, `times=`, `volta=1,2`, `jumps=`, `rehearsal=`, `tempo=`,
 * `tempo-text=`, `clef=`, `key=`, `meter=`) come before its voices. Free text is %-escaped. Fields within a
 * line are tab-separated. Version 1 (no `[…]` properties, `{…}` or bar fields) reads as it always did; newer
 * versions are refused, not half read.
 */
object ScoreText {
    const val VERSION = 4

    fun encode(s: Score): String = buildString {
        append("score ").append(VERSION).append('\n')
        if (s.title.isNotEmpty()) append("title ").append(s.title.replace('\n', ' ')).append('\n')
        append("meter ").append(s.meter).append('\n')
        s.key?.let { append("key ").append(it.token).append('\n') }
        append("tempo ").append(tempoToken(s.tempo)).append('\n')
        for (p in s.parts) {
            append("part\t").append(p.name.replace('\t', ' ').replace('\n', ' ')).append("\tclef=").append(p.clef.token)
            p.instrument?.let { i ->
                append("\ttuning=").append(i.tuning.strings.joinToString(" ")).append("\ttuning-id=").append(i.tuning.id)
                append("\tcapo=").append(i.capo).append("\tfrets=").append(i.frets)
                append("\twritten-up=").append(i.writtenAboveSounding?.let { it.semitones } ?: 0)
            }
            append('\n')
            for (b in p.bars) {
                append("bar")
                for ((k, v) in barFields(b)) append('\t').append(k).append('=').append(v)
                b.voices.forEachIndexed { v, events ->
                    append("\tv").append(v + 1)
                    for (e in events) append(' ').append(eventToken(e))
                }
                append('\n')
            }
        }
    }

    fun decode(text: String): Score {
        val lines = text.lines().filter { it.isNotBlank() }
        require(lines.isNotEmpty()) { "empty score" }
        val head = lines[0].split(' ')
        require(head.size == 2 && head[0] == "score") { "not a score" }
        val v = head[1].toIntOrNull() ?: error("bad score version")
        require(v <= VERSION) { "this score was written by a newer version of Apophenia (format $v)" }
        var title = ""; var meter = TimeSignature.of(4, 4); var key: KeyMode? = null; var tempo = Tempo(120.0)
        val parts = ArrayList<Part>()
        var bars = ArrayList<Bar>()
        fun closePart() { if (parts.isNotEmpty()) parts[parts.size - 1] = parts.last().copy(bars = bars) }
        for (line in lines.drop(1)) {
            when {
                line.startsWith("title ") -> title = line.substring(6)
                line.startsWith("meter ") -> meter = TimeSignature.parse(line.substring(6).trim())
                line.startsWith("key ") -> key = KeyMode.parseOrNull(line.substring(4).trim())
                line.startsWith("tempo ") -> tempo = parseTempo(line.substring(6))
                line.startsWith("part\t") -> {
                    closePart(); bars = ArrayList()
                    val f = line.split('\t')
                    val kv = f.drop(2).associate { it.substringBefore('=') to it.substringAfter('=') }
                    val instrument = kv["tuning"]?.let { t ->
                        val strings = t.split(' ').map(SpelledPitch::parse)
                        val id = kv["tuning-id"] ?: "custom"
                        val known = Tunings.all.firstOrNull { it.id == id && it.strings == strings }
                        val up = kv["written-up"]?.toIntOrNull() ?: 12
                        Instrument(
                            known ?: Tuning(id, "Custom", strings), kv["capo"]?.toIntOrNull() ?: 0, kv["frets"]?.toIntOrNull() ?: 22,
                            if (up == 12) com.betterappmafia.apophenia.core.interval.Interval(8, 12) else null,
                        )
                    }
                    parts += Part(f[1], Clef.of(kv["clef"] ?: "treble"), instrument)
                }
                line.startsWith("bar") -> {
                    require(parts.isNotEmpty()) { "a bar before any part" }
                    val fields = line.split('\t').drop(1)
                    val voices = fields.filter { VOICE.matches(it) }.map { field ->
                        field.substringAfter(' ', "").split(' ').filter { it.isNotEmpty() }.map(::parseEvent)
                    }
                    val kv = fields.filter { !VOICE.matches(it) && '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
                    bars += parseBar(kv, voices.ifEmpty { listOf(emptyList()) })
                }
            }
        }
        closePart()
        return Score(title, meter, key, tempo, parts)
    }

    private val VOICE = Regex("v[1-4]( .*)?")

    private fun barFields(b: Bar): List<Pair<String, String>> = buildList {
        if (b.repeatStart) add("start" to "repeat")
        if (b.end != Barline.SINGLE) add("end" to b.end.token)
        if (b.end == Barline.REPEAT && b.times != 2) add("times" to b.times.toString())
        if (b.volta.isNotEmpty()) add("volta" to b.volta.joinToString(","))
        if (b.jumps.isNotEmpty()) add("jumps" to b.jumps.sortedBy { it.ordinal }.joinToString(",") { it.token })
        b.rehearsal?.let { add("rehearsal" to Esc.encode(it)) }
        b.tempo?.let { add("tempo" to tempoToken(it)) }
        b.tempoText?.let { add("tempo-text" to Esc.encode(it)) }
        b.clef?.let { add("clef" to it.token) }
        b.key?.let { add("key" to it.token) }
        b.meter?.let { add("meter" to it.toString()) }
    }

    private fun parseBar(kv: Map<String, String>, voices: List<List<Event>>) = Bar(
        voices,
        repeatStart = kv["start"] == "repeat",
        end = kv["end"]?.let(Barline::of) ?: Barline.SINGLE,
        times = kv["times"]?.toInt() ?: 2,
        volta = kv["volta"]?.split(',')?.filter { it.isNotEmpty() }?.map { it.toInt() } ?: emptyList(),
        jumps = kv["jumps"]?.split(',')?.filter { it.isNotEmpty() }?.map(Jump::of)?.toSet() ?: emptySet(),
        rehearsal = kv["rehearsal"]?.let(Esc::decode),
        tempo = kv["tempo"]?.let(::parseTempo),
        tempoText = kv["tempo-text"]?.let(Esc::decode),
        clef = kv["clef"]?.let(Clef::of),
        key = kv["key"]?.let { KeyMode.parseOrNull(it) ?: throw IllegalArgumentException("unknown key '$it'") },
        meter = kv["meter"]?.let(TimeSignature::parse),
    )

    private fun fmtBpm(b: Double) = if (b == Math.floor(b)) b.toLong().toString() else b.toString()
    private fun tempoToken(t: Tempo) = fmtBpm(t.bpm) + " " + durationToken(t.beatUnit)
    private fun parseTempo(s: String): Tempo {
        val (bpm, unit) = s.trim().split(' ').let { it[0] to (it.getOrNull(1) ?: "q") }
        return Tempo(bpm.toDouble(), parseDuration(unit))
    }

    private val VALUE_LETTERS = mapOf(
        NoteValue.BREVE to "B", NoteValue.WHOLE to "W", NoteValue.HALF to "h", NoteValue.QUARTER to "q", NoteValue.EIGHTH to "e",
        NoteValue.SIXTEENTH to "s", NoteValue.THIRTY_SECOND to "t", NoteValue.SIXTY_FOURTH to "x", NoteValue.HUNDRED_TWENTY_EIGHTH to "o",
    )
    private val LETTER_VALUES = VALUE_LETTERS.entries.associate { (k, v) -> v to k }

    fun durationToken(d: Duration): String = buildString {
        append(VALUE_LETTERS.getValue(d.value)); repeat(d.dots) { append('.') }
        for (t in d.tuplets) append('/').append(t.actual).append(':').append(t.normal)
    }

    fun parseDuration(s: String): Duration {
        val value = LETTER_VALUES[s.take(1)] ?: throw IllegalArgumentException("unknown note value in '$s'")
        var i = 1; var dots = 0
        while (i < s.length && s[i] == '.') { dots++; i++ }
        val tuplets = if (i < s.length) s.substring(i + 1).split('/').map { t ->
            val (a, n) = t.split(':'); Tuplet(a.toInt(), n.toInt())
        } else emptyList()
        return Duration(value, dots, tuplets)
    }

    private fun noteToken(n: Note): String = buildString {
        append(n.pitch)
        if (n.string != null && n.fret != null) append('@').append(n.string).append('/').append(n.fret)
        if (n.tie) append('~')
        val props = n.effects.sortedBy { it.ordinal }.map { it.token } +
            listOfNotNull(
                if (n.head != Head.NORMAL) "head=${n.head.token}" else null,
                if (n.finger.isNotEmpty()) "finger=${n.finger}" else null,
                if (n.pluck.isNotEmpty()) "pluck=${n.pluck}" else null,
                if (n.showString) "show-string" else null,
                n.velocity?.let { "vel=$it" },
            )
        if (props.isNotEmpty()) append('[').append(props.joinToString(",")).append(']')
    }

    private fun parseNote(t: String): Note {
        val props = t.substringAfter('[', "").substringBefore(']').split(',').filter { it.isNotEmpty() }
        var core = t.substringBefore('[')
        val tie = core.endsWith('~').also { if (it) core = core.dropLast(1) }
        val pitch = SpelledPitch.parse(core.substringBefore('@'))
        val at = core.substringAfter('@', "")
        val (string, fret) = if (at.isEmpty()) null to null else at.split('/').let { it[0].toInt() to it[1].toInt() }
        val kv = props.filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
        val flags = props.filter { '=' !in it }
        return Note(
            pitch, string, fret, tie, flags.filter { it != "show-string" }.map(Effect::of).toSet(),
            head = kv["head"]?.let(Head::of) ?: Head.NORMAL, finger = kv["finger"] ?: "", pluck = kv["pluck"] ?: "",
            showString = "show-string" in flags, velocity = kv["vel"]?.toInt(),
        )
    }

    /** Comma-separated note tokens, splitting outside `[ ]`. */
    private fun splitNotes(body: String): List<String> {
        val out = ArrayList<String>(); var depth = 0; var from = 0
        for ((i, c) in body.withIndex()) {
            if (c == '[') depth++ else if (c == ']') depth-- else if (c == ',' && depth == 0) { out += body.substring(from, i); from = i + 1 }
        }
        out += body.substring(from)
        return out
    }

    fun eventToken(e: Event): String = buildString {
        append(durationToken(e.duration)).append(':')
        append(if (e.isRest) "r" else e.notes.joinToString(",", transform = ::noteToken))
        val items = buildList {
            if (e.marks.isNotEmpty()) add("marks=" + e.marks.sortedBy { it.ordinal }.joinToString(",") { it.token })
            e.dynamic?.let { add("dyn=" + it.token) }
            if (e.spans.isNotEmpty()) add("line=" + e.spans.entries.sortedBy { it.key.ordinal }.joinToString(",") { "${it.key.token}:${it.value}" })
            e.chord?.let { add("chord=" + Esc.encode(it)) }
            e.diagram?.let { d -> add("diagram=" + d.joinToString(".") { it?.toString() ?: "x" }) }
            for (t in e.texts) add("text=" + t.kind.token + ":" + Esc.encode(t.text))
            for (l in e.lyrics) add("lyric=" + l.verse + (if (l.hyphen) "-" else "") + (if (l.extend) "_" else "") + ":" + Esc.encode(l.text))
            if (e.tremolo > 0) add("trem=" + e.tremolo)
            if (e.hidden) add("hidden=1")
            if (e.graces.isNotEmpty()) add("grace=" + e.graces.joinToString("+") { g ->
                VALUE_LETTERS.getValue(g.value) + (if (g.slash) "/" else "") + (if (g.after) ">" else "") + ":" + g.notes.joinToString(",", transform = ::noteToken)
            })
        }
        if (items.isNotEmpty()) append('{').append(items.joinToString(";")).append('}')
    }

    fun parseEvent(s: String): Event {
        val brace = s.indexOf('{')
        val main = if (brace < 0) s else s.substring(0, brace)
        val colon = main.lastIndexOf(':')   // tuplets use ":" too (e/3:2); notes never do
        require(colon > 0) { "bad event '$s'" }
        val d = parseDuration(main.substring(0, colon))
        val body = main.substring(colon + 1)
        val notes = if (body == "r") emptyList() else splitNotes(body).map(::parseNote)
        if (brace < 0) return Event(d, notes)
        require(s.endsWith('}')) { "bad event '$s'" }
        var e = Event(d, notes)
        for (item in s.substring(brace + 1, s.length - 1).split(';').filter { it.isNotEmpty() }) {
            val k = item.substringBefore('='); val v = item.substringAfter('=')
            e = when (k) {
                "marks" -> e.copy(marks = e.marks + v.split(',').filter { it.isNotEmpty() }.map(Mark::of))
                "dyn" -> e.copy(dynamic = Dynamic.of(v))
                "line" -> e.copy(spans = e.spans + v.split(',').filter { it.isNotEmpty() }.associate { Span.of(it.substringBefore(':')) to it.substringAfter(':').toInt() })
                "chord" -> e.copy(chord = Esc.decode(v))
                "diagram" -> e.copy(diagram = v.split('.').map { if (it == "x") null else it.toInt() })
                "text" -> e.copy(texts = e.texts + StaffText(TextKind.of(v.substringBefore(':')), Esc.decode(v.substringAfter(':'))))
                "lyric" -> {
                    val flags = v.substringBefore(':')
                    e.copy(lyrics = e.lyrics + Lyric(flags.take(1).toInt(), Esc.decode(v.substringAfter(':')), '-' in flags, '_' in flags))
                }
                "trem" -> e.copy(tremolo = v.toInt())
                "hidden" -> e.copy(hidden = v == "1")
                "grace" -> e.copy(graces = e.graces + v.split('+').map { g ->
                    val head = g.substringBefore(':')
                    Grace(LETTER_VALUES[head.take(1)] ?: throw IllegalArgumentException("bad grace '$g'"), splitNotes(g.substringAfter(':')).map(::parseNote), '/' in head, '>' in head)
                })
                else -> throw IllegalArgumentException("unknown event field '$k'")
            }
        }
        return e
    }
}
