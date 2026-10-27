package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.audio.WavWriter
import com.betterappmafia.apophenia.audio.Piano
import com.betterappmafia.apophenia.core.score.ScorePlayback
import com.betterappmafia.apophenia.core.sheet.ChordSound
import com.betterappmafia.apophenia.core.sound.PlayNote
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.instrument.Instrument
import com.betterappmafia.apophenia.core.pitch.SpelledPitch
import com.betterappmafia.apophenia.core.instrument.Tunings
import com.betterappmafia.apophenia.core.rhythm.Duration
import com.betterappmafia.apophenia.core.rhythm.NoteValue
import com.betterappmafia.apophenia.core.rhythm.Rational
import com.betterappmafia.apophenia.core.rhythm.Tuplet
import com.betterappmafia.apophenia.core.rhythm.Tempo
import com.betterappmafia.apophenia.core.score.Barline
import com.betterappmafia.apophenia.core.score.Clef
import com.betterappmafia.apophenia.core.score.Dynamic
import com.betterappmafia.apophenia.core.score.Effect
import com.betterappmafia.apophenia.core.score.Jump
import com.betterappmafia.apophenia.core.score.Mark
import com.betterappmafia.apophenia.core.score.MarkGroup
import com.betterappmafia.apophenia.core.score.Notation
import com.betterappmafia.apophenia.core.score.Span
import com.betterappmafia.apophenia.core.score.StaffText
import com.betterappmafia.apophenia.core.score.TextKind
import com.betterappmafia.apophenia.core.sheet.ChordText
import com.betterappmafia.apophenia.core.score.ScoreEdit
import com.betterappmafia.apophenia.core.score.Event
import com.betterappmafia.apophenia.core.score.Fingering
import com.betterappmafia.apophenia.core.score.Part
import com.betterappmafia.apophenia.core.score.Score
import com.betterappmafia.apophenia.core.score.ScoreText
import com.betterappmafia.apophenia.core.score.ScoreTime
import com.betterappmafia.apophenia.core.spelling.Spelling
import com.betterappmafia.apophenia.data.BodyPair
import com.betterappmafia.apophenia.data.LibraryRow
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.MusicFont
import com.betterappmafia.apophenia.ui.theme.T
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.layout.Spacer
import java.io.File
import kotlin.math.sqrt

/** Where an event is in the score: bar, voice, index. */
private data class At(val bar: Int, val voice: Int, val index: Int)

/**
 * A tab or sheet-music item opened full size (plan §10.5a, C4 notation screen): the score's tab with the rhythm
 * under it and a note row, tap a note to select it, the fretboard shows it and every other place it can be
 * played (tap one to move it there), ▶ PLAY hears it with a cursor. Opened from a Board it shows that project's
 * version, as written items do (plan §4.2).
 */
@Composable
fun ScoreDetail(model: AppModel, row: LibraryRow, projectId: Long?) {
    val db = model.app.db
    val s = row.scrap
    val bodyFlow = remember(s.id, projectId) {
        if (projectId != null) db.projects().bodyIn(projectId, s.id)
        else db.scraps().row(s.id).map { it?.let { r -> BodyPair(r.scrap.body, null) } }
    }
    val pair by bodyFlow.collectAsState(null)
    val loaded = pair ?: return
    val ownCopy = projectId != null && loaded.ownBody != null
    val first = remember(s.id, projectId) { runCatching { ScoreText.decode(loaded.shown) } }
    val history = remember(s.id, projectId) { mutableStateListOf<Score>().apply { first.getOrNull()?.let { add(it) } } }
    var at by remember(s.id, projectId) { mutableStateOf(0) }
    var selected by remember(s.id) { mutableStateOf<At?>(null) }
    var selNote by remember(s.id) { mutableStateOf(0) }
    var rowMode by remember { mutableStateOf("names") }
    var viewMenu by remember { mutableStateOf(false) }
    var editMenu by remember { mutableStateOf(false) }
    var rowMenu by remember { mutableStateOf(false) }
    var view by remember(s.id) { mutableStateOf(model.detailView ?: if (s.format == com.betterappmafia.apophenia.data.ScrapFormat.SHEET) "notation" else "tab") }
    var input by remember(s.id) { mutableStateOf(if (s.format == com.betterappmafia.apophenia.data.ScrapFormat.SHEET) "piano" else "frets") }
    var chordAdd by remember(s.id) { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var addCat by remember { mutableStateOf<String?>(null) }
    var spanEdit by remember { mutableStateOf<com.betterappmafia.apophenia.core.score.Span?>(null) }
    var voice by remember(s.id) { mutableStateOf(0) }                 // the voice notes go into (0 = voice 1)
    var mode by remember(s.id) { mutableStateOf("step") }             // MuseScore input modes: step, rhythm, repitch, insert, realtime, tap
    var modeMenu by remember { mutableStateOf(false) }
    var realtimeOff by remember { mutableStateOf(false) }
    var lastInserted by remember(s.id) { mutableStateOf<ScoreEdit.Pos?>(null) }
    var typing by remember(s.id) { mutableStateOf<String?>(null) }
    var typed by remember(s.id) { mutableStateOf("") }
    var octave by remember(s.id) { mutableStateOf(4) }
    var entryValue by remember(s.id) { mutableStateOf(NoteValue.QUARTER) }
    var entryDots by remember(s.id) { mutableStateOf(0) }
    var entryTuplet by remember(s.id) { mutableStateOf<Tuplet?>(null) }
    var tupletMenu by remember { mutableStateOf(false) }
    var voiceMenu by remember { mutableStateOf(false) }
    var inputMenu by remember { mutableStateOf(false) }
    var fxMenu by remember { mutableStateOf(false) }
    var fxCat by remember { mutableStateOf<String?>(null) }
    var tuningMenu by remember { mutableStateOf(false) }
    var retuneTo by remember { mutableStateOf<Instrument?>(null) }
    var clipboard by remember { mutableStateOf<com.betterappmafia.apophenia.core.score.Bar?>(null) }
    val title = displayTitle(s)

    Column(Modifier.fillMaxSize()) {
        TitleBar {
            Sq("‹", { model.detail = null }, modifier = Modifier.semantics { contentDescription = "Back" })
            Column(Modifier.weight(1f).clickable { model.sheet = Sheet.Rename(s.id) }.semantics { contentDescription = "Rename $title" }) {
                Line1(title, T.title)
                Text(
                    (if (s.format == com.betterappmafia.apophenia.data.ScrapFormat.SHEET) "SHEET MUSIC" else "TAB") + when {
                        projectId == null -> " · LIBRARY"
                        ownCopy -> " · ${model.projectName(projectId)?.uppercase()}'S OWN COPY"
                        else -> " · ${model.projectName(projectId)?.uppercase()}"
                    },
                    style = T.label.copy(fontSize = 8.sp), maxLines = 1,
                )
            }
            Box {
                val fretted = history.getOrNull(at)?.parts?.firstOrNull()?.instrument != null
                Sq(if (view == "chart") "CHORD CHART ▾" else if (view == "roll") "PIANO ROLL ▾" else if (view == "tab" && fretted) "TAB ▾" else "NOTATION ▾", { viewMenu = true }, on = true)
                ConsoleMenu(viewMenu, { viewMenu = false }, "VIEW", listOf(
                    MenuItem("Notation", if (view == "notation" || (!fretted && view != "roll" && view != "chart")) "✓" else "") { view = "notation"; if (input == "frets" && !fretted) input = "piano" },
                    MenuItem("Tab", if (!fretted) "FRETTED PARTS" else if (view == "tab") "✓" else "", enabled = fretted) { view = "tab"; if (fretted) input = "frets" },
                    MenuItem("Piano roll", if (view == "roll") "✓" else "FL STUDIO STYLE") { view = "roll" },
                    MenuItem("Chord chart", if (view == "chart") "✓" else "CHORD SYMBOLS, BAR BY BAR") { view = "chart" },
                ))
            }
            Box {
                var exportMenu by remember { mutableStateOf(false) }
                Sq("⋯", { exportMenu = true }, modifier = Modifier.semantics { contentDescription = "Export" })
                ConsoleMenu(exportMenu, { exportMenu = false }, "EXPORT", listOf(
                    MenuItem("MIDI", "ONE TRACK A PART, REPEATS PLAYED · .mid", enabled = history.getOrNull(at) != null) {
                        history.getOrNull(at)?.let { sc -> model.exportFile(com.betterappmafia.apophenia.core.score.ScoreMidi.write(if (sc.title.isBlank()) sc.copy(title = title) else sc), safeFileName(title) + ".mid", "MIDI file") }
                    },
                    MenuItem("Text tab", if (history.getOrNull(at)?.parts?.any { it.instrument != null } == true) "FRETTED PARTS · .txt" else "NO FRETTED PART", enabled = history.getOrNull(at)?.parts?.any { it.instrument != null } == true) {
                        history.getOrNull(at)?.let { sc -> model.exportFile(com.betterappmafia.apophenia.core.sheet.Exports.tab(if (sc.title.isBlank()) sc.copy(title = title) else sc).toByteArray(), safeFileName(title) + ".txt", "Tab text file") }
                    },
                    MenuItem("MusicXML", "NOTES, TAB, CHORDS · .musicxml", enabled = history.getOrNull(at) != null) {
                        history.getOrNull(at)?.let { sc ->
                            val named = if (sc.title.isBlank()) sc.copy(title = title) else sc
                            model.exportFile(com.betterappmafia.apophenia.core.score.MusicXml.write(named).toByteArray(), safeFileName(title) + ".musicxml", "MusicXML file")
                        }
                    },
                ))
            }
        }
        val score = history.getOrNull(at)
        if (score == null) {
            Text("This score couldn't be read: ${first.exceptionOrNull()?.message}", style = T.body, modifier = Modifier.padding(14.dp))
            return@Column
        }
        val part = score.parts.firstOrNull()
        if (part == null) {
            Text("This score has no parts.", style = T.body, modifier = Modifier.padding(14.dp))
            return@Column
        }
        val inst = part.instrument
        val roll = view == "roll"
        val chartView = view == "chart"
        val notation = !roll && !chartView && (view == "notation" || inst == null)
        fun commit(next: Score) {
            while (history.size > at + 1) history.removeAt(history.lastIndex)
            history += next; at = history.lastIndex
            if (history.size > 200) { history.removeAt(0); at-- }
            val text = ScoreText.encode(next)
            model.app.io.launch {
                val now = System.currentTimeMillis()
                if (ownCopy) { db.projects().setOwnBody(projectId!!, s.id, text); db.projects().touch(projectId, now) }
                else db.scraps().setBody(s.id, text, now)
            }
        }
        fun restore(i: Int) {
            at = i
            val text = ScoreText.encode(history[i])
            model.app.io.launch {
                val now = System.currentTimeMillis()
                if (ownCopy) db.projects().setOwnBody(projectId!!, s.id, text) else db.scraps().setBody(s.id, text, now)
            }
        }

        val slots = remember(score) { ScoreTime.slots(score, part) }
        val player by model.app.audio.player.collectAsState()
        val p = player
        // The notes and the chords are separate renders; each view plays (and follows) its own.
        val playingHere = p != null && p.scrapId == s.id && p.takeId == -1L && p.path.contains("-chords-") == chartView
        val now = if (playingHere && p!!.playing) p.position.toDouble() / model.app.audio.sampleRate else -1.0
        val cursor = slots.firstOrNull { now >= it.start && now < it.end && it.voice == 0 }?.let { At(it.bar, 0, it.index) }

        val selAt = selected ?: At(0, voice, 0)
        val pos = ScoreEdit.Pos(selAt.bar, selAt.index, selAt.voice)
        val selEvent = ScoreEdit.event(score, 0, pos)
        // In step mode the length bar shows the selected beat's length (it opened on 1/4 over a whole rest, and the
        // first note went in whole); NEXT and the first beat on opening move it too, not only a tap.
        LaunchedEffect(selAt, selEvent?.duration, mode) {
            val d = selEvent?.duration
            if (mode == "step" && d != null) { entryValue = d.value; entryDots = d.dots; entryTuplet = d.tuplets.firstOrNull() }
        }
        fun select(p: ScoreEdit.Pos, note: Int = 0) { selected = At(p.bar, p.voice, p.index); selNote = note }
        fun entry() = Duration(entryValue, entryDots, if (entryTuplet == null) emptyList() else listOf(entryTuplet!!))
        /** The middle line of the staff at [bar]: where rhythm mode puts its notes, as MuseScore does. */
        fun middle(bar: Int) = Notation.pitchAt(part, 4, Notation.fifths(score.keyAt(bar)), part.clefAt(bar))
        /**
         * A length picked on the bar: in step mode it goes on the selected beat; in rhythm mode a length key ([enter])
         * puts in a note of it and moves on; the other modes use it for what they enter next.
         */
        fun applyDuration(enter: Boolean = false) {
            if (selEvent == null) return
            when (mode) {
                "step" -> commit(ScoreEdit.setDuration(score, 0, pos, entry()))
                "rhythm" -> if (enter) ScoreEdit.enterRhythm(score, 0, pos, entry(), middle(pos.bar))?.let { (n, p2) -> commit(n); select(p2) }
                    ?: model.notice("The middle line is out of this instrument's reach.")
            }
        }
        /** Moves to [v] for entry: with a selection, onto the beat of that voice at the same time when there is one. */
        fun enterVoice(v: Int) {
            voice = v
            val s2 = ScoreEdit.ensureVoice(score, 0, pos.bar, v)
            if (s2 != score) commit(s2)
            val part2 = s2.parts[0]
            val start = ScoreEdit.onset(part2, pos)
            val idx = part2.bars[pos.bar].voices[v].indices.firstOrNull { ScoreEdit.onset(part2, ScoreEdit.Pos(pos.bar, it, v)) == start } ?: 0
            select(ScoreEdit.Pos(pos.bar, idx, v))
        }

        // C4's duration bar: pick a length (it goes on the selected beat and on what NEXT adds), dots, rest, tie, tuplet, voice.
        val lenScroll = rememberScrollState()
        Row(Modifier.fillMaxWidth().underline().scrollCue(lenScroll).horizontalScroll(lenScroll).padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DURATION_BAR.forEach { (v, glyph, name) ->
                DurKey(glyph, name, on = entryValue == v) { entryValue = v; applyDuration(enter = true) }
            }
            DurKey(if (entryDots == 0) "·" else "·".repeat(entryDots), "DOT", on = entryDots > 0) { entryDots = (entryDots + 1) % 3; applyDuration() }
            DurKey(restGlyph(NoteValue.QUARTER), "REST", on = selEvent?.isRest == true) {
                when (mode) {
                    "rhythm" -> if (selEvent != null) ScoreEdit.enterRhythm(score, 0, pos, entry(), null)?.let { (n, p2) -> commit(n); select(p2) }
                    "insert" -> if (selEvent != null) ScoreEdit.insertBefore(score, 0, pos, entry(), null)?.let { (n, p2) -> commit(n); select(p2); lastInserted = pos }
                    else -> if (selEvent != null && !selEvent.isRest) commit(selEvent.notes.indices.fold(score) { acc, _ -> ScoreEdit.deleteNote(acc, 0, pos, 0) })
                }
            }
            DurKey("‿", "TIE", on = selEvent?.notes?.any { it.tie } == true) { if (selEvent?.notes?.isNotEmpty() == true) commit(ScoreEdit.toggleTie(score, 0, pos)) }
            Box {
                DurKey(entryTuplet?.let { "${it.actual}" } ?: "–", "TUPLET ▾", on = entryTuplet != null) { tupletMenu = true }
                ConsoleMenu(tupletMenu, { tupletMenu = false }, "TUPLET", TUPLETS.map { (t, label) ->
                    MenuItem(label, if (t == entryTuplet) "✓" else "") { entryTuplet = t; applyDuration() }
                })
            }
            Box {
                DurKey("V${voice + 1}", "VOICE ▾", on = voice > 0, color = VOICE_COLORS[voice]) { voiceMenu = true }
                val note = selEvent?.notes?.getOrNull(selNote)
                val barVoices = part.bars[pos.bar].voices.size
                ConsoleMenu(voiceMenu, { voiceMenu = false }, "VOICE · BAR ${pos.bar + 1}", (0..3).map { v ->
                    MenuItem("Enter in voice ${v + 1}", (if (voice == v) "✓ " else "") + (if (v % 2 == 0) "STEMS UP" else "STEMS DOWN")) { enterVoice(v) }
                } + (0..3).filter { it != pos.voice }.map { v ->
                    MenuItem(if (note != null && (selEvent?.notes?.size ?: 0) > 1) "Move ${pitchName(note.pitch)} to voice ${v + 1}" else "Move this beat to voice ${v + 1}", if (selEvent?.isRest != false) "SELECT A NOTE" else "SAME RHYTHM THERE", enabled = selEvent?.isRest == false) {
                        val single = if ((selEvent?.notes?.size ?: 0) > 1) selNote else null
                        ScoreEdit.moveToVoice(score, 0, pos, single, v)?.let { (n, dest) -> commit(n); select(dest) }
                            ?: model.notice("Voice ${v + 1} has no beat of this length starting here: enter one there first.")
                    }
                } + listOf(
                    MenuItem("Exchange voices 1 and 2 in this bar", "BAR ${pos.bar + 1}") { commit(ScoreEdit.swapVoices(score, 0, pos.bar, 0, 1)) },
                    MenuItem(if (selEvent?.hidden == true) "Show this rest" else "Hide this rest", if (pos.voice == 0) "VOICES 2–4" else if (selEvent?.isRest != true) "SELECT A REST" else "",
                        enabled = pos.voice > 0 && selEvent?.isRest == true) { commit(ScoreEdit.toggleHidden(score, 0, pos)) },
                ) + (1 until barVoices).map { v ->
                    MenuItem("Clear voice ${v + 1} in this bar", if (v == barVoices - 1) "REMOVES IT" else "BACK TO RESTS") {
                        commit(ScoreEdit.clearVoice(score, 0, pos.bar, v)); if (pos.voice == v) select(ScoreEdit.Pos(pos.bar, 0, 0)); if (voice == v && v == barVoices - 1) voice = 0
                    }
                })
            }
        }
        // C4's tab toolbar: INPUT, EFFECTS, TUNING, EDIT, SCORE.
        val menuScroll = rememberScrollState()
        Row(Modifier.fillMaxWidth().underline().scrollCue(menuScroll).horizontalScroll(menuScroll).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box {
                Sq("INPUT: ${when (input) { "frets" -> "FRETS"; "staff" -> "STAFF"; else -> "PIANO" }} ▾", { inputMenu = true })
                ConsoleMenu(inputMenu, { inputMenu = false }, "ENTER NOTES WITH", listOf(
                    MenuItem("Fretboard", if (inst == null) "FRETTED PARTS" else if (input == "frets") "✓ YOUR TUNING" else "YOUR TUNING", enabled = inst != null) { input = "frets" },
                    MenuItem("Piano keys", if (input == "piano") "✓" else "") { input = "piano" },
                    MenuItem("Tap on the staff", if (input == "staff") "✓ NOTATION VIEW" else "NOTATION VIEW") { input = "staff"; view = "notation" },
                    MenuItem("Sing or play it", "PITCH TRACKER · NEXT", enabled = false) {},
                    MenuItem("MIDI keyboard", "USB · NEXT", enabled = false) {},
                    MenuItem("From a recording", "MIDI TAB › MAKE TAB", enabled = false) {},
                ))
            }
            Box {
                Sq("MODE: ${MODES.first { it.first == mode }.second.uppercase()} ▾", { modeMenu = true }, on = mode != "step")
                ConsoleMenu(modeMenu, { modeMenu = false }, "NOTE INPUT MODE", MODES.map { (id, label, note) ->
                    MenuItem(label, if (mode == id) "✓ $note" else note) {
                        if (mode == "realtime" || mode == "tap") { /* leaving real-time: stop the click, tidy the ties */ realtimeOff = true }
                        mode = id; lastInserted = null
                        if (id == "realtime" || id == "tap") { if (input == "frets") input = "piano"; chordAdd = false }
                    }
                })
            }
            if (notation) Box {
                val note = selEvent?.notes?.getOrNull(selNote)
                Sq("ADD ▾", { addMenu = true; addCat = null })
                val cat = addCat
                fun open(c: String?) { addCat = c; addMenu = true }
                val back = listOf(MenuItem("‹ All categories", "") { open(null) })
                fun ev(f: (Event) -> Event) { if (selEvent != null) commit(ScoreEdit.editEvent(score, 0, pos, f)) }
                fun nt(f: (com.betterappmafia.apophenia.core.score.Note) -> com.betterappmafia.apophenia.core.score.Note) { if (note != null) commit(ScoreEdit.editNote(score, 0, pos, selNote, f)) }
                fun bar(all: Boolean = true, f: (com.betterappmafia.apophenia.core.score.Bar) -> com.betterappmafia.apophenia.core.score.Bar) = commit(ScoreEdit.editBar(score, 0, pos.bar, all, f))
                val curBar = part.bars[pos.bar]
                val hasNotes = selEvent?.isRest == false
                val needBeat = if (selEvent == null) "SELECT A BEAT" else ""
                val needNotes = if (!hasNotes) "ON NOTES" else ""
                fun markItem(m: Mark) = MenuItem(m.label, if (selEvent == null) needBeat else if (m in selEvent.marks) "✓" else "", enabled = selEvent != null) { commit(ScoreEdit.toggleMark(score, 0, pos, m)) }
                fun spanItem(s: Span) = MenuItem(s.label, selEvent?.spans?.get(s)?.let { "OVER $it ›" } ?: if (selEvent == null) needBeat else "›", enabled = selEvent != null) { spanEdit = s; open("Line") }
                // Bar 0's clef, key, meter and tempo are the part's and the score's own; later bars hold changes (none when it matches what's already in force).
                fun setClef(c: Clef) = if (pos.bar == 0) commit(score.copy(parts = score.parts.mapIndexed { i, pp -> if (i == 0) pp.copy(clef = c, bars = pp.bars.mapIndexed { b, x -> if (b == 0) x.copy(clef = null) else x }) else pp }))
                    else bar(false) { it.copy(clef = if (c == part.clefAt(pos.bar - 1)) null else c) }
                fun setKey(k: KeyMode) = if (pos.bar == 0) commit(ScoreEdit.editBar(score.copy(key = k), 0, 0) { it.copy(key = null) })
                    else bar { it.copy(key = if (k == score.keyAt(pos.bar - 1)) null else k) }
                fun setMeter(m: com.betterappmafia.apophenia.core.rhythm.TimeSignature) = if (pos.bar == 0) commit(ScoreEdit.editBar(score.copy(meter = m), 0, 0) { it.copy(meter = null) })
                    else bar { it.copy(meter = if (m == score.meterAt(pos.bar - 1)) null else m) }
                val items: List<MenuItem> = when (cat) {
                    null -> ADD_CATEGORIES.map { name -> MenuItem(name, "›") { open(name) } }
                    "Accidentals" -> back + listOf(
                        "♯ Sharp" to 1, "♭ Flat" to -1, "♮ Natural" to 0, "𝄪 Double sharp" to 2, "𝄫 Double flat" to -2,
                    ).map { (label, alter) ->
                        MenuItem(label, if (note == null) "SELECT A NOTE" else if (note.pitch.alter == alter) "✓" else "", enabled = note != null) {
                            ScoreEdit.setAlter(score, 0, pos, selNote, alter)?.let { commit(it) } ?: model.notice("The instrument can't play that.")
                        }
                    } + listOf(MenuItem("Respell", note?.let { "${pitchName(it.pitch)} ⇄ SAME SOUND" } ?: "SELECT A NOTE", enabled = note != null) {
                        ScoreEdit.respell(score, 0, pos, selNote)?.let { commit(it) }
                    })
                    "Clefs · keys · meters" -> back + listOf(
                        MenuItem("Key from bar ${pos.bar + 1}", (score.keyAt(pos.bar)?.short ?: "NONE") + " ›") { open("Key") },
                        MenuItem("Meter from bar ${pos.bar + 1}", "${score.meterAt(pos.bar)} ›") { open("Meter") },
                    ) + Clef.entries.map { c -> MenuItem("${CLEF_NAMES.getValue(c)} from bar ${pos.bar + 1}", if (part.clefAt(pos.bar) == c) "✓" else "") { setClef(c) } }
                    "Key" -> listOf(MenuItem("‹ Clefs · keys · meters", "") { open("Clefs · keys · meters") }) + KEYS.map { k ->
                        MenuItem(k.display, if (score.keyAt(pos.bar) == k) "✓" else "${Notation.fifths(k).let { f -> if (f > 0) "$f♯" else if (f < 0) "${-f}♭" else "–" }}") { setKey(k) }
                    }
                    "Meter" -> listOf(MenuItem("‹ Clefs · keys · meters", "") { open("Clefs · keys · meters") }) + METERS.map { m ->
                        MenuItem("$m", if (score.meterAt(pos.bar) == m) "✓" else if (m.groups.distinct().size > 1) m.groups.joinToString("+") else "") { setMeter(m) }
                    } + listOf(MenuItem("Notes stay put", "RED BARS DON'T ADD UP", enabled = false) {})
                    "Articulations" -> back + listOf(Effect.ACCENT to "Accent", Effect.HEAVY_ACCENT to "Marcato", Effect.STACCATO to "Staccato").map { (fx, label) ->
                        MenuItem(label, if (!hasNotes) needNotes else if (selEvent!!.notes.all { fx in it.effects }) "✓" else "", enabled = hasNotes) { commit(ScoreEdit.toggleEffectAll(score, 0, pos, fx)) }
                    } + Mark.entries.filter { it.group == MarkGroup.ARTICULATION }.map(::markItem)
                    "Ornaments" -> back + Mark.entries.filter { it.group == MarkGroup.ORNAMENT }.map(::markItem) + spanItem(Span.TRILL_LINE)
                    "Dynamics · hairpins" -> back + listOf(spanItem(Span.CRESC), spanItem(Span.DIM)) + Dynamic.entries.map { d ->
                        MenuItem(d.token, if (selEvent?.dynamic == d) "✓" else needBeat, enabled = selEvent != null) { ev { it.copy(dynamic = if (it.dynamic == d) null else d) } }
                    }
                    "Lines" -> back + listOf(Span.SLUR, Span.GLISS, Span.OTTAVA_UP, Span.OTTAVA_DOWN, Span.QUINDICESIMA_UP, Span.QUINDICESIMA_DOWN, Span.PEDAL, Span.TRILL_LINE, Span.CRESC, Span.DIM).map(::spanItem)
                    "Line" -> {
                        val s = spanEdit ?: Span.SLUR
                        val left = if (selEvent == null) 0 else ScoreEdit.eventsFrom(score, 0, pos)
                        val toBarEnd = part.bars[pos.bar].voices[0].size - pos.index
                        val now = selEvent?.spans?.get(s)
                        back + listOf(2, 3, 4, 6, 8, 12, 16).filter { it <= left }.map { n ->
                            MenuItem("${s.label} over $n beats", if (now == n) "✓" else "") { commit(ScoreEdit.setSpan(score, 0, pos, s, n)) }
                        } + listOf(
                            MenuItem("To the end of bar ${pos.bar + 1}", if (toBarEnd < 2) "LAST BEAT" else "$toBarEnd BEATS", enabled = toBarEnd >= 2) { commit(ScoreEdit.setSpan(score, 0, pos, s, toBarEnd)) },
                            MenuItem("Remove the ${s.label.lowercase()}", "", enabled = now != null) { commit(ScoreEdit.setSpan(score, 0, pos, s, 0)) },
                        )
                    }
                    "Repeats · barlines" -> back + listOf(
                        MenuItem("Start repeat  |:", if (curBar.repeatStart) "✓ BAR ${pos.bar + 1}" else "BAR ${pos.bar + 1}") { bar { it.copy(repeatStart = !it.repeatStart) } },
                    ) + Barline.entries.map { bl ->
                        MenuItem("${bl.label} barline", if (curBar.end == bl) "✓ END OF BAR ${pos.bar + 1}" else "END OF BAR ${pos.bar + 1}") { bar { it.copy(end = bl) } }
                    } + (if (curBar.end == Barline.REPEAT) listOf(2, 3, 4).map { t -> MenuItem("Play $t times", if (curBar.times == t) "✓" else "") { bar { it.copy(times = t) } } } else emptyList()) +
                        VOLTAS.map { (v, label) -> MenuItem(label, if (curBar.volta == v) "✓" else "") { bar { it.copy(volta = v) } } } +
                        Jump.entries.map { j -> MenuItem(j.text, if (j in curBar.jumps) "✓" else if (j.atStart) "BAR START" else "BAR END") { bar { it.copy(jumps = if (j in it.jumps) it.jumps - j else it.jumps + j) } } }
                    "Text · tempo · rehearsal" -> {
                        val letter = ('A' + part.bars.take(pos.bar).count { it.rehearsal != null }).toString()
                        back + listOf(
                            MenuItem("Expression…", "BELOW · dolce", enabled = selEvent != null) { typing = "e"; typed = "" },
                            MenuItem("Technique…", "ABOVE · pizz.", enabled = selEvent != null) { typing = "t"; typed = "" },
                            MenuItem("Staff text…", "ABOVE", enabled = selEvent != null) { typing = "s"; typed = "" },
                        ) + (selEvent?.texts ?: emptyList()).map { t -> MenuItem("Remove “${t.text}”", t.kind.label.uppercase()) { ev { it.copy(texts = it.texts - t) } } } + listOf(
                            MenuItem("Rehearsal mark…", curBar.rehearsal?.let { "✓ $it" } ?: letter) { typing = "rehearsal"; typed = curBar.rehearsal ?: letter },
                            MenuItem("Tempo change…", "♩ = ${fmtBpm(score.tempoAt(pos.bar).bpm)}") { typing = "bpm"; typed = fmtBpm(score.tempoAt(pos.bar).bpm) },
                            MenuItem("Tempo text", (curBar.tempoText ?: "") + " ›") { open("Tempo text") },
                        ) + listOfNotNull(
                            curBar.rehearsal?.let { MenuItem("Remove rehearsal mark", it) { bar { b -> b.copy(rehearsal = null) } } },
                            curBar.tempo?.let { MenuItem("Remove tempo change", "BAR ${pos.bar + 1}") { bar { b -> b.copy(tempo = null) } } },
                        )
                    }
                    "Tempo text" -> listOf(MenuItem("‹ Text · tempo · rehearsal", "") { open("Text · tempo · rehearsal") }) + TEMPO_WORDS.map { w ->
                        MenuItem(w, if (curBar.tempoText == w) "✓" else "") { bar { it.copy(tempoText = if (it.tempoText == w) null else w) } }
                    } + listOf(
                        MenuItem("Other…", "") { typing = "tempo-text"; typed = curBar.tempoText ?: "" },
                        MenuItem("None", "", enabled = curBar.tempoText != null) { bar { it.copy(tempoText = null) } },
                    )
                    "Lyrics" -> back + (1..3).map { v ->
                        MenuItem("Verse $v…", selEvent?.lyrics?.firstOrNull { it.verse == v }?.text ?: needNotes, enabled = hasNotes) { typing = "lyric$v"; typed = selEvent?.lyrics?.firstOrNull { it.verse == v }?.text ?: "" }
                    } + listOf(MenuItem("Remove lyrics on this beat", "", enabled = selEvent?.lyrics?.isNotEmpty() == true) { ev { it.copy(lyrics = emptyList()) } })
                    "Chord symbols · diagrams" -> back + listOf(
                        MenuItem("Chord symbol…", selEvent?.chord?.let { ChordText.display(it) } ?: needBeat, enabled = selEvent != null) { typing = "chord"; typed = selEvent?.chord ?: "" },
                        MenuItem("Diagram from these frets", if (inst == null) "FRETTED PARTS" else if (selEvent?.let { ScoreEdit.diagramOf(part, it) } == null) "ON A FRETTED CHORD" else "", enabled = selEvent?.let { ScoreEdit.diagramOf(part, it) } != null) {
                            ev { it.copy(diagram = ScoreEdit.diagramOf(part, it)) }
                        },
                        MenuItem("Diagram typed…", "LOW STRING FIRST · x32010", enabled = selEvent != null) { typing = "diagram"; typed = selEvent?.diagram?.joinToString(" ") { it?.toString() ?: "x" } ?: "" },
                        MenuItem("Remove chord symbol", "", enabled = selEvent?.chord != null) { ev { it.copy(chord = null) } },
                        MenuItem("Remove diagram", "", enabled = selEvent?.diagram != null) { ev { it.copy(diagram = null) } },
                    )
                    "Fingering" -> back + com.betterappmafia.apophenia.core.score.Note.FINGERS.map { f ->
                        MenuItem(if (f == "T") "Thumb (T)" else "Finger $f", if (note == null) "SELECT A NOTE" else if (note.finger == f) "✓" else "", enabled = note != null) { nt { it.copy(finger = if (it.finger == f) "" else f) } }
                    } + PLUCKS.map { (p, label) ->
                        MenuItem(label, if (note == null) "SELECT A NOTE" else if (note.pluck == p) "✓" else "RIGHT HAND", enabled = note != null) { nt { it.copy(pluck = if (it.pluck == p) "" else p) } }
                    } + listOf(
                        MenuItem("String number", if (note?.string == null) "FRETTED PARTS" else if (note.showString) "✓ ${note.string}" else "${note.string}", enabled = note?.string != null) { nt { it.copy(showString = !it.showString) } },
                        MenuItem("Clear fingering", "", enabled = note != null && (note.finger + note.pluck).isNotEmpty() || note?.showString == true) { nt { it.copy(finger = "", pluck = "", showString = false) } },
                    )
                    "Noteheads" -> back + com.betterappmafia.apophenia.core.score.Head.entries.map { h ->
                        MenuItem(h.label, if (note == null) "SELECT A NOTE" else if (note.head == h) "✓" else "", enabled = note != null) { nt { it.copy(head = h) } }
                    }
                    "Tremolo" -> back + (0..4).map { n ->
                        MenuItem(if (n == 0) "None" else "$n stroke${if (n > 1) "s" else ""}", if (!hasNotes) needNotes else if (selEvent!!.tremolo == n) "✓" else if (n > 0) listOf("8THS", "16THS", "32NDS", "64THS")[n - 1] else "", enabled = hasNotes) { ev { it.copy(tremolo = n) } }
                    }
                    "Grace notes" -> back + GRACES.map { (label, g) ->
                        MenuItem(label, needNotes, enabled = hasNotes) { ScoreEdit.addGrace(score, 0, pos, g.first, g.second, g.third)?.let { commit(it) } }
                    } + listOf(
                        MenuItem("Last grace note up a step", "", enabled = selEvent?.graces?.isNotEmpty() == true) { commit(ScoreEdit.stepGrace(score, 0, pos, 1)); addMenu = true },
                        MenuItem("Last grace note down a step", "", enabled = selEvent?.graces?.isNotEmpty() == true) { commit(ScoreEdit.stepGrace(score, 0, pos, -1)); addMenu = true },
                        MenuItem("Remove grace notes", "", enabled = selEvent?.graces?.isNotEmpty() == true) { ev { it.copy(graces = emptyList()) } },
                    )
                    else -> back
                }
                ConsoleMenu(addMenu, { addMenu = false }, when (cat) { null -> "ADD TO THE SELECTION"; "Line" -> "ADD › ${(spanEdit ?: Span.SLUR).label.uppercase()} FROM HERE"; else -> "ADD › ${cat.uppercase()}" }, items)
            }
            Box {
                val note = selEvent?.notes?.getOrNull(selNote)
                Sq("EFFECTS ▾", { fxMenu = true; fxCat = null }, on = note?.effects?.isNotEmpty() == true)
                val cat = fxCat
                ConsoleMenu(fxMenu, { fxMenu = false }, if (note == null) "SELECT A NOTE FIRST" else if (cat == null) "EFFECTS ON ${pitchName(note.pitch)}" else "EFFECTS › ${cat.uppercase()}",
                    if (cat == null) FX_GROUPS.map { (name, list) ->
                        MenuItem(name, "${list.count { note?.effects?.contains(it) == true }.takeIf { it > 0 }?.let { "$it ON · " } ?: ""}${list.size} ›", enabled = note != null) { fxCat = name; fxMenu = true }
                    } else listOf(MenuItem("‹ All effects", "") { fxCat = null; fxMenu = true }) + FX_GROUPS.first { it.first == cat }.second.map { fx ->
                        MenuItem(fx.label, if (note?.effects?.contains(fx) == true) "✓ ${fx.tab}" else fx.tab) { commit(ScoreEdit.toggleEffect(score, 0, pos, selNote, fx)) }
                    })
            }
            if (inst != null) Box {
                Sq("TUNING ▾", { tuningMenu = true })
                val pending = retuneTo
                ConsoleMenu(tuningMenu, { tuningMenu = false; retuneTo = null },
                    if (pending == null) "TUNING · CAPO" else "${pending.tuning.name.uppercase()} · CAPO ${pending.capo}: KEEP WHICH?",
                    if (pending == null) Tunings.all.filter { it.stringCount == inst.tuning.stringCount }.map { t ->
                        MenuItem(t.name, (if (t == inst.tuning) "✓ " else "") + t.strings.joinToString(" ") { KeyMode.displayName(it.name) }) {
                            if (t != inst.tuning) { retuneTo = inst.copy(tuning = t); tuningMenu = true }
                        }
                    } + listOf(0, 1, 2, 3, 4, 5, 7).filter { it < inst.frets }.map { c ->
                        MenuItem("Capo $c", if (c == inst.capo) "✓" else "") { if (c != inst.capo) { retuneTo = inst.copy(capo = c); tuningMenu = true } }
                    } else listOf(
                        MenuItem("Keep the notes", "FINGER THEM AGAIN") { commit(ScoreEdit.retune(score, 0, pending, keepNotes = true)); retuneTo = null },
                        MenuItem("Keep the fingering", "THE PITCHES MOVE") { commit(ScoreEdit.retune(score, 0, pending, keepNotes = false)); retuneTo = null },
                    ))
            }
            Box {
                Sq("EDIT ▾", { editMenu = true })
                ConsoleMenu(editMenu, { editMenu = false }, "EDIT", listOf(
                    MenuItem("Undo", if (at > 0) "" else "NOTHING", enabled = at > 0) { restore(at - 1) },
                    MenuItem("Redo", if (at < history.lastIndex) "" else "NOTHING", enabled = at < history.lastIndex) { restore(at + 1) },
                    MenuItem("Delete this note", "", enabled = selEvent?.notes?.getOrNull(selNote) != null) { commit(ScoreEdit.deleteNote(score, 0, pos, selNote)); selNote = 0 },
                    MenuItem("Delete this beat", "", enabled = selEvent != null && part.bars[pos.bar].voices[pos.voice].size > 1) {
                        commit(ScoreEdit.deleteEvent(score, 0, pos)); select(ScoreEdit.Pos(pos.bar, (pos.index - 1).coerceAtLeast(0), pos.voice))
                    },
                    MenuItem("Insert a beat after", "REST · ${durationName(Event(entry()))}") { val (n, p2) = ScoreEdit.insertAfter(score, 0, pos, entry()); commit(n); select(p2) },
                    MenuItem("Insert a bar after", "BAR ${pos.bar + 2}") { commit(ScoreEdit.insertBar(score, 0, pos.bar)); select(ScoreEdit.Pos(pos.bar + 1, 0)) },
                    MenuItem("Delete this bar", "BAR ${pos.bar + 1}", enabled = part.bars.size > 1) { commit(ScoreEdit.deleteBar(score, 0, pos.bar)); select(ScoreEdit.Pos((pos.bar - 1).coerceAtLeast(0), 0)) },
                    MenuItem("Copy this bar", if (clipboard != null) "COPIED" else "") { clipboard = part.bars[pos.bar] },
                    MenuItem("Paste after this bar", if (clipboard == null) "COPY ONE FIRST" else "", enabled = clipboard != null) { commit(ScoreEdit.pasteBar(score, 0, clipboard!!, pos.bar)); select(ScoreEdit.Pos(pos.bar + 1, 0)) },
                    MenuItem("Transpose up a semitone", "+1") { commit(ScoreEdit.transpose(score, 0, 1)) },
                    MenuItem("Transpose down a semitone", "−1") { commit(ScoreEdit.transpose(score, 0, -1)) },
                    MenuItem("Transpose up an octave", "+12") { commit(ScoreEdit.transpose(score, 0, 12)) },
                    MenuItem("Transpose down an octave", "−12") { commit(ScoreEdit.transpose(score, 0, -12)) },
                ))
            }
            Box {
                Sq("SCORE ⋯", { rowMenu = true })
                ConsoleMenu(rowMenu, { rowMenu = false }, "NOTE ROW UNDER THE TAB", listOf(
                    MenuItem("Note names", if (rowMode == "names") "✓" else "") { rowMode = "names" },
                    MenuItem("Scale degrees", if (score.key == null) "NEEDS A KEY" else if (rowMode == "degrees") "✓" else "", enabled = score.key != null) { rowMode = "degrees" },
                    MenuItem("Off", if (rowMode == "off") "✓" else "") { rowMode = "off" },
                ))
            }
        }
        // Typed additions from ADD ▾: chord symbols, staff text, lyrics (NEXT › moves on to the next sung note), rehearsal and tempo marks.
        typing?.let { kind ->
            val verse = kind.removePrefix("lyric").toIntOrNull()
            fun done() { typing = null; typed = "" }
            fun put(hyphen: Boolean = false, extend: Boolean = false, advance: Boolean = false) {
                val t = typed.trim()
                when {
                    verse != null -> {
                        if (selEvent == null || selEvent.isRest) { done(); return }
                        val next = ScoreEdit.setLyric(score, 0, pos, verse, t, hyphen, extend)
                        commit(next)
                        val to = if (advance) ScoreEdit.nextSung(next, 0, pos) else null
                        if (to == null) done() else { select(to); typed = ScoreEdit.event(next, 0, to)?.lyrics?.firstOrNull { it.verse == verse }?.text ?: "" }
                        return
                    }
                    kind == "chord" -> commit(ScoreEdit.editEvent(score, 0, pos) { it.copy(chord = t.replace("♭", "b").replace("♯", "#").replace("𝄪", "##").replace("𝄫", "bb").ifEmpty { null }) })
                    kind == "diagram" -> {
                        val parts = if (' ' in t || '.' in t) t.split(' ', '.').filter { it.isNotEmpty() } else t.map { it.toString() }
                        val frets = parts.map { if (it.equals("x", true)) null else it.toIntOrNull() ?: -1 }
                        if (t.isNotEmpty() && (frets.any { it != null && it !in 0..24 } || frets.size < 2)) { model.notice("A diagram is one fret or x per string, lowest string first: x32010 or x 3 2 0 1 0."); return }
                        commit(ScoreEdit.editEvent(score, 0, pos) { it.copy(diagram = frets.takeIf { t.isNotEmpty() }) })
                    }
                    kind == "e" || kind == "t" || kind == "s" -> if (t.isNotEmpty()) commit(ScoreEdit.editEvent(score, 0, pos) { it.copy(texts = it.texts + StaffText(TextKind.of(kind), t)) })
                    kind == "rehearsal" -> commit(ScoreEdit.editBar(score, 0, pos.bar) { it.copy(rehearsal = t.ifEmpty { null }) })
                    kind == "tempo-text" -> commit(ScoreEdit.editBar(score, 0, pos.bar) { it.copy(tempoText = t.ifEmpty { null }) })
                    kind == "bpm" -> {
                        val bpm = t.toDoubleOrNull()
                        if (bpm == null || bpm < 10 || bpm > 400) { model.notice("A tempo from 10 to 400."); return }
                        val tempo = Tempo(bpm, score.tempoAt(pos.bar).beatUnit)
                        commit(if (pos.bar == 0) score.copy(tempo = tempo) else ScoreEdit.editBar(score, 0, pos.bar) { it.copy(tempo = if (tempo == score.tempoAt(pos.bar - 1)) null else tempo) })
                    }
                }
                done()
            }
            Column(Modifier.fillMaxWidth().underline()) {
                ConsoleField(
                    when (kind) {
                        "chord" -> "CHORD"; "diagram" -> "FRETS"; "e" -> "EXPRESSION"; "t" -> "TECHNIQUE"; "s" -> "TEXT"; "rehearsal" -> "REHEARSAL"
                        "tempo-text" -> "TEMPO TEXT"; "bpm" -> "♩ ="; else -> "VERSE $verse"
                    },
                    typed, { typed = it.take(60) },
                    placeholder = when (kind) {
                        "chord" -> "Am7, F#m7b5, C/E, Bb13(#11)"; "diagram" -> "x32010"; "e" -> "dolce, espress., cresc."; "t" -> "pizz., arco, con sord."
                        "rehearsal" -> "A, Verse, Chorus"; "tempo-text" -> "Allegro, Swing, rit."; "bpm" -> "BPM"; "s" -> "anything"; else -> "one syllable"
                    },
                    number = kind == "bpm", onDone = { put(advance = verse != null) },
                )
                if (kind == "chord") ChordCheck(typed, model, score.keyAt(pos.bar))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (verse != null) {
                        Sq("NEXT ›", { put(advance = true) }, on = true)
                        Sq("HYPHEN –", { put(hyphen = true, advance = true) })
                        Sq("HOLD ___", { put(extend = true, advance = true) })
                        Sq("DONE", { put() })
                    } else Sq("SET", { put() }, on = true)
                    Sq("CANCEL", { done() })
                }
            }
        }
        // Playback scrolls the sheet to the system the cursor is in (plan §4.4: "The sheet scrolls with playback").
        val sheetScroll = rememberScrollState()
        var sheetTop by remember { mutableStateOf(0f) }
        Column(Modifier.weight(1f).verticalScroll(sheetScroll)) {
            Text(
                "${part.name.uppercase()} · " + (inst?.let { i -> "${i.tuning.name.uppercase()} (${i.tuning.strings.joinToString(" ") { KeyMode.displayName(it.name) }}) · CAPO ${i.capo} · " } ?: "${part.clef.token.uppercase()} CLEF · ") + "${score.meter}" +
                    (score.key?.let { " · ${it.display.uppercase()}" } ?: "") + " · ♩ = ${score.tempo.bpm.toInt()}",
                style = T.label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            val offBars = remember(score) { part.bars.indices.filter { b -> (0 until part.bars[b].voices.size).any { v -> ScoreEdit.fill(score, part, b, v) != Rational.ZERO } }.toSet() }
            fun picked(a: At, noteIndex: Int) {
                selected = a; selNote = noteIndex.coerceAtLeast(0); lastInserted = null
                ScoreEdit.event(score, 0, ScoreEdit.Pos(a.bar, a.index, a.voice))?.let { e -> entryValue = e.duration.value; entryDots = e.duration.dots; entryTuplet = e.duration.tuplets.firstOrNull() }
            }
            val speller = remember(score.key) { com.betterappmafia.apophenia.core.tools.KeySpeller(score.key ?: KeyMode.parseOrNull("C major")!!) }
            /** Piano keys, a staff tap, or a fret outside step mode: the pitch goes in as MODE ▾ says, and is heard. */
            fun enterPitch(to: ScoreEdit.Pos, pitch: SpelledPitch) {
                playPitch(model, pitch.midi)
                val e = ScoreEdit.event(score, 0, to) ?: return
                val reach = "${pitchName(pitch)} is out of this instrument's reach."
                when (mode) {
                    "repitch" -> {
                        val r = ScoreEdit.repitch(score, 0, to, pitch, add = chordAdd)
                            ?: run { model.notice(if (chordAdd) "+ CHORD adds to a note, not a rest." else "No more notes to re-pitch from here."); return }
                        commit(r.first); select(r.second ?: to)
                    }
                    "insert" -> {
                        val last = lastInserted
                        if (chordAdd && last != null) { ScoreEdit.setPitch(score, 0, last, pitch, add = true)?.let { commit(it) } ?: model.notice(reach); return }
                        val r = ScoreEdit.insertBefore(score, 0, to, entry(), pitch) ?: run { model.notice(reach); return }
                        commit(r.first); select(r.second); lastInserted = to
                    }
                    else -> {
                        val next = ScoreEdit.setPitch(score, 0, to, pitch, add = chordAdd && !e.isRest)
                        if (next == null) { model.notice(reach); return }
                        commit(next)
                        selected = At(to.bar, to.voice, to.index)
                        selNote = ScoreEdit.event(next, 0, to)!!.notes.indexOfFirst { it.pitch == pitch }.coerceAtLeast(0)
                    }
                }
            }
            // Real-time entry (MuseScore's two real-time modes): the keys held when the click (or ADVANCE) comes go in;
            // holding on through the next click ties on. Stopping writes the tied runs as beat-sized notes.
            val held = remember { mutableStateListOf<Int>() }
            var rtRunning by remember { mutableStateOf(false) }
            var rtLast by remember { mutableStateOf<List<Int>>(emptyList()) }
            var rtPrev by remember { mutableStateOf<ScoreEdit.Pos?>(null) }
            var rtJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
            var rtUserBpm by remember { mutableStateOf(120.0) }   // the metronome tool's own tempo, put back afterwards
            val scope = rememberCoroutineScope()
            val followTo: (Float) -> Unit = { y -> if (playingHere && p!!.playing) scope.launch { sheetScroll.animateScrollTo((sheetTop + y - 40f).toInt().coerceAtLeast(0)) } }
            fun rtTick(restIfNone: Boolean) {
                val sc = history.getOrNull(at) ?: return
                val here = selected?.let { ScoreEdit.Pos(it.bar, it.index, it.voice) } ?: ScoreEdit.Pos(0, 0, voice)
                val keys = held.toList().distinct().sorted()
                if (keys.isEmpty() && !restIfNone) return
                val tie = keys.isNotEmpty() && keys == rtLast && rtPrev != null
                val (n, p2) = ScoreEdit.realtimeStep(sc, 0, here, entry(), keys.map { speller.spell(it) }, rtPrev, tie)
                commit(n); rtPrev = here; rtLast = keys; select(p2)
            }
            fun rtFinish() {
                rtJob?.cancel(); rtJob = null
                if (rtRunning) model.app.audio.setClick(false, rtUserBpm)
                rtRunning = false; rtPrev = null; rtLast = emptyList()
                history.getOrNull(at)?.let { sc -> val m = ScoreEdit.mergeTies(sc, 0, selAt.voice); if (m != sc) commit(m) }
            }
            fun rtStart() {
                val sc = history.getOrNull(at) ?: return
                val secs = sc.tempoAt(pos.bar).secondsFor(entry().length)
                rtRunning = true; rtPrev = null; rtLast = emptyList()
                // One click per length entered, as MuseScore does; faster than the click can go (400), one per beat instead.
                rtUserBpm = model.app.audio.click.value.bpm
                model.app.audio.setClick(true, (60.0 / secs).let { if (it > 400.0) sc.tempoAt(pos.bar).bpm else it })
                // Each tick is due at a fixed time from the start, so the beats don't drift from the click.
                rtJob = scope.launch {
                    val t0 = System.nanoTime(); val period = (secs * 1e9).toLong(); var k = 1L
                    while (true) {
                        kotlinx.coroutines.delay(((t0 + k * period - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
                        rtTick(restIfNone = false); k++
                    }
                }
            }
            DisposableEffect(Unit) { onDispose { rtJob?.cancel(); if (rtRunning) model.app.audio.setClick(false, rtUserBpm) } }
            LaunchedEffect(realtimeOff) { if (realtimeOff) { rtFinish(); realtimeOff = false } }
            if (roll) {
                PianoRollPane(score, part, voice, cursor?.let { it.bar to it.index }, { commit(it) }, { playPitch(model, it) }, { model.notice(it) })
            } else if (chartView) {
                ChordChartPane(score, part, title, playingHere && p!!.playing, { if (playingHere) model.app.audio.togglePlay() else playScore(model, s.id, title, score, part, chords = true) }, model) { commit(it) }
            } else if (notation) {
                Spacer(Modifier.fillMaxWidth().height(0.dp).onGloballyPositioned { sheetTop = it.positionInParent().y })
                NotationCanvas(score, part, NoteSel(selAt.bar, selAt.voice, selAt.index, selNote), cursor?.let { it.bar to it.index }, offBars, voice, model.chordDialect, model.chordStyle, onCursorY = followTo) { bar, v, index, note, step ->
                    if (input == "staff" && note < 0 && mode != "realtime" && mode != "tap") {
                        if (v != voice) {
                            // The bar has no beat in this voice yet: it starts as rests, then the next tap enters.
                            commit(ScoreEdit.ensureVoice(score, 0, bar, voice)); select(ScoreEdit.Pos(bar, 0, voice))
                            model.notice("Voice ${voice + 1} starts in bar ${bar + 1}: tap again to put the note in.")
                            return@NotationCanvas
                        }
                        selected = At(bar, v, index)
                        val shift = ottavaOf(part, Notation.ottavas(part), Triple(bar, v, index))
                        enterPitch(ScoreEdit.Pos(bar, index, v), Notation.pitchAt(part, step + 7 * shift, Notation.fifths(score.keyAt(bar)), part.clefAt(bar)))
                    } else picked(At(bar, v, index), note)
                }
            } else {
                Spacer(Modifier.fillMaxWidth().height(0.dp).onGloballyPositioned { sheetTop = it.positionInParent().y })
                TabCanvas(score, part, selAt, selNote, cursor, rowMode, offBars, onCursorY = followTo) { a, noteIndex -> picked(a, noteIndex) }
            }
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sq(if (playingHere && p!!.playing) "■ STOP" else "▶ PLAY", {
                    if (playingHere) model.app.audio.togglePlay() else playScore(model, s.id, title, score, part)
                }, on = true)
                when (mode) {
                    "realtime" -> {
                        Sq(if (rtRunning) "■ STOP CLICK" else "● START CLICK", { if (rtRunning) rtFinish() else rtStart() }, on = true, color = if (rtRunning) C.rec else C.hi)
                        Sq("ADVANCE ›", { rtTick(restIfNone = true) })
                    }
                    "tap" -> {
                        Sq("ADVANCE ›", { rtTick(restIfNone = true) }, on = true)
                        Sq("DONE", { rtFinish() })
                    }
                    else -> Sq("NEXT ›", {
                        val (n, p2) = ScoreEdit.next(score, 0, pos, entry())
                        if (n != score) commit(n)
                        select(p2); lastInserted = null
                    }, on = true)
                }
                if (input != "frets" && mode != "realtime" && mode != "tap") Sq(if (chordAdd) "+ CHORD ✓" else "+ CHORD", { chordAdd = !chordAdd }, on = chordAdd)
            }
            if (!roll && !chartView) {
            val ev = selEvent
            val note = ev?.notes?.getOrNull(selNote)
            Kv("SELECTED") {
                Text(
                    when {
                        ev == null -> "Tap a beat."
                        note == null -> "${if (ev.hidden) "Hidden rest" else "Rest"} · ${durationName(ev)} · bar ${pos.bar + 1}, beat ${pos.index + 1}" + if (pos.voice > 0) " · voice ${pos.voice + 1}" else ""
                        else -> buildString {
                            append(pitchName(note.pitch))
                            val written = com.betterappmafia.apophenia.core.score.Notation.written(part, note.pitch)
                            if (written != note.pitch) append(" (written ").append(pitchName(written)).append(")")
                            if (inst != null) note.string?.let { st -> append(" · ").append(stringName(inst, st)) }?.append(" string, fret ")?.append(note.fret)
                            append(" · ").append(durationName(ev))
                            if (note.tie) append(" · tied on")
                            if (pos.voice > 0) append(" · voice ").append(pos.voice + 1)
                            if (note.effects.isNotEmpty()) append(" · ").append(note.effects.joinToString(", ") { it.label.lowercase() })
                            score.key?.let { append(" · ").append(degree(it, note.pitch)).append(" of ").append(it.display) }
                        }
                    },
                    style = T.body.copy(color = C.hi),
                )
                if (note != null && inst != null) {
                    val others = Fingering.places(inst, note.pitch.midi).filter { it.string != note.string }
                    Text(if (others.isEmpty()) "Only here on this tuning." else "Also: " + others.joinToString(" · ") { "${stringName(inst, it.string)} string fret ${it.fret}" }, style = T.small)
                }
                if (ev != null && ev.notes.size > 1) Text("Chord of ${ev.notes.size}: tap another note of it to pick that one.", style = T.small)
                ev?.let { e ->
                    val on = buildList {
                        e.chord?.let { add(ChordText.display(it)) }
                        e.dynamic?.let { add(it.token) }
                        e.marks.forEach { add(it.label.lowercase()) }
                        e.spans.forEach { (sp, n) -> add("${sp.label.lowercase()} over $n") }
                        e.texts.forEach { add("“${it.text}”") }
                        e.lyrics.forEach { add("verse ${it.verse} “${it.text}${if (it.hyphen) "-" else ""}”") }
                        if (e.tremolo > 0) add("tremolo ${e.tremolo}")
                        if (e.graces.isNotEmpty()) add("${e.graces.size} grace note${if (e.graces.size > 1) "s" else ""}")
                        note?.let { n -> if (n.head != com.betterappmafia.apophenia.core.score.Head.NORMAL) add("${n.head.label.lowercase()} head"); if (n.finger.isNotEmpty()) add("finger ${n.finger}"); if (n.pluck.isNotEmpty()) add(n.pluck) }
                    }
                    if (on.isNotEmpty()) Text("On it: " + on.joinToString(" · "), style = T.small)
                }
                if (pos.bar in offBars) {
                    val f = ScoreEdit.fill(score, part, pos.bar, pos.voice).takeIf { it != Rational.ZERO } ?: ScoreEdit.fill(score, part, pos.bar)
                    Text("Bar ${pos.bar + 1} is ${fraction(if (f < Rational.ZERO) -f else f)} ${if (f < Rational.ZERO) "short" else "over"}.", style = T.small.copy(color = C.rec))
                }
            }
            if (input == "frets" && inst != null && mode != "realtime" && mode != "tap") {
                Text(if (mode == "step") "FRETBOARD · TAP A FRET TO PUT IT ON THE BEAT · A HOLLOW DOT MOVES THE NOTE THERE" else "FRETBOARD · A FRET GIVES THE PITCH", style = T.label, modifier = Modifier.padding(start = 12.dp, top = 10.dp))
                Fretboard(inst, note?.let { n -> n.string?.let { Fingering.Place(it, n.fret ?: 0) } }, note?.let { Fingering.places(inst, it.pitch.midi) } ?: emptyList()) { place ->
                    if (ev == null) { playPitch(model, inst.midiAt(place.string, place.fret)); return@Fretboard }
                    if (mode != "step") { enterPitch(pos, speller.spell(inst.midiAt(place.string, place.fret))); return@Fretboard }
                    playPitch(model, inst.midiAt(place.string, place.fret))
                    val n = note
                    if (n != null && inst.midiAt(place.string, place.fret) == n.pitch.midi && place.string != n.string && ev.notes.none { it !== n && it.string == place.string }) {
                        val moved = ScoreEdit.move(score, 0, pos, selNote, place.string, place.fret)
                        commit(moved)
                        selNote = ScoreEdit.event(moved, 0, pos)!!.notes.indexOfFirst { it.string == place.string }.coerceAtLeast(0)
                    } else {
                        val set = ScoreEdit.setFret(score, 0, pos, place.string, place.fret)
                        commit(set)
                        selNote = ScoreEdit.event(set, 0, pos)!!.notes.indexOfFirst { it.string == place.string }.coerceAtLeast(0)
                    }
                }
            }
            if (input == "piano" || mode == "realtime" || mode == "tap") {
                val realtime = mode == "realtime" || mode == "tap"
                Row(Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("PIANO · C$octave–B${octave + 1} · " + if (realtime) "HOLD KEYS THROUGH THE CLICKS" else "TAP A KEY TO PUT IT ON THE BEAT", style = T.label, modifier = Modifier.weight(1f))
                    Sq("OCT ▼", { octave = (octave - 1).coerceAtLeast(0) })
                    Sq("OCT ▲", { octave = (octave + 1).coerceAtMost(7) })
                }
                PianoKeys(octave, note?.pitch?.midi, held.toSet(),
                    onDown = { midi -> if (realtime) { held += midi; playPitch(model, midi) } else enterPitch(pos, speller.spell(midi)) },
                    onUp = { midi -> held.remove(midi) },
                )
            }
            Text(
                when (mode) {
                    "rhythm" -> "Rhythm: each length you pick on the bar above goes in as a note on the middle line and moves on (REST puts a rest). Then MODE ▾ › Re-pitch to give the notes their pitches."
                    "repitch" -> "Re-pitch: each pitch you enter replaces the next note's pitch and keeps its rhythm, ties and markings; rests are passed over. + CHORD adds to the note instead."
                    "insert" -> "Insert: a pitch goes in before the selected beat at the length picked above, and the rest of the bar moves later (its number goes red until it adds up). REST inserts a rest."
                    "realtime" -> "Real-time: START CLICK, then hold keys: each click puts in the length picked above, and holding on ties on. Nothing held, nothing goes in: ADVANCE puts a rest. STOP writes tied runs as whole beats."
                    "tap" -> "Real-time, your tempo: hold keys and tap ADVANCE for each length picked above; holding on ties on, nothing held is a rest. DONE writes tied runs as whole beats."
                    else -> when (input) {
                        "frets" -> "Step entry: pick a length on the bar above, tap a fret, NEXT ›. A fret on another string makes a chord; REST clears the beat."
                        "staff" -> "Tap the staff where the note goes: on a rest it becomes that note; with + CHORD it joins the beat. The key signature decides sharps and flats; ADD ▾ › Accidentals changes them."
                        else -> "Pick a length, tap a key, NEXT ›. With + CHORD a key joins the beat instead of replacing it. Notes are spelled in the key; ADD ▾ › Accidentals respells them."
                    }
                } + " Entering in voice ${voice + 1}" + (if (voice > 0) " (VOICE ▾ to change)." else ".") + " A bar that doesn't add up has its number in red.",
                style = T.small.copy(lineHeight = 16.sp), modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
            }
        }
    }
}

/** MuseScore's note input modes (handbook: Alternative note input methods), with what each does in a word. */
private val MODES = listOf(
    Triple("step", "Step", "LENGTH, THEN PITCH"), Triple("rhythm", "Rhythm", "LENGTHS FIRST"), Triple("repitch", "Re-pitch", "KEEP THE RHYTHM"),
    Triple("insert", "Insert", "PUSH THE BAR ON"), Triple("realtime", "Real-time", "TO A CLICK"), Triple("tap", "Real-time, tapped", "YOUR TEMPO"),
)
/** C4's ADD ▾ categories, mirroring MuseScore 4's palettes. */
private val ADD_CATEGORIES = listOf(
    "Accidentals", "Clefs · keys · meters", "Articulations", "Ornaments", "Dynamics · hairpins",
    "Lines", "Repeats · barlines", "Text · tempo · rehearsal", "Lyrics", "Chord symbols · diagrams",
    "Fingering", "Noteheads", "Tremolo", "Grace notes",
)

private val CLEF_NAMES = mapOf(
    Clef.TREBLE to "Treble clef", Clef.TREBLE_8VB to "Treble 8vb clef", Clef.BASS to "Bass clef",
    Clef.ALTO to "Alto clef", Clef.TENOR to "Tenor clef", Clef.PERCUSSION to "Percussion clef",
)

/** Every key signature: majors from C♭ to C♯ round the circle, then their relative minors. */
private val KEYS: List<KeyMode> =
    listOf("Cb", "Gb", "Db", "Ab", "Eb", "Bb", "F", "C", "G", "D", "A", "E", "B", "F#", "C#").map { KeyMode.parseOrNull("$it major")!! } +
        listOf("Ab", "Eb", "Bb", "F", "C", "G", "D", "A", "E", "B", "F#", "C#", "G#", "D#", "A#").map { KeyMode.parseOrNull("$it aeolian")!! }

// 5/8 and 7/8 are listed by grouping: a bare 5/8 has two (TimeSignature.of refuses it, which crashed this screen from 0.5.3 to 0.9.5).
internal val METERS = listOf("2/4", "3/4", "4/4", "5/4", "6/4", "2/2", "3/2", "3/8", "2+3/8", "3+2/8", "6/8", "2+2+3/8", "3+2+2/8", "9/8", "12/8")
    .map { com.betterappmafia.apophenia.core.rhythm.TimeSignature.parse(it) }

private val VOLTAS = listOf(listOf(1) to "1st ending", listOf(2) to "2nd ending", listOf(1, 2) to "1st and 2nd ending", listOf(3) to "3rd ending", emptyList<Int>() to "No ending")

private val TEMPO_WORDS = listOf(
    "Grave", "Largo", "Adagio", "Andante", "Moderato", "Allegretto", "Allegro", "Vivace", "Presto",
    "rit.", "rall.", "accel.", "a tempo", "Rubato", "Swing", "Straight", "Half time", "Double time",
)

private val PLUCKS = listOf("p" to "p (thumb)", "i" to "i (index)", "m" to "m (middle)", "a" to "a (ring)", "c" to "c (little)")

/** Grace notes ADD ▾ offers: value, slashed, after. */
private val GRACES = listOf(
    "Acciaccatura (slashed eighth)" to Triple(NoteValue.EIGHTH, true, false),
    "Appoggiatura (eighth)" to Triple(NoteValue.EIGHTH, false, false),
    "Appoggiatura (sixteenth)" to Triple(NoteValue.SIXTEENTH, false, false),
    "Appoggiatura (quarter)" to Triple(NoteValue.QUARTER, false, false),
    "Grace note after (eighth)" to Triple(NoteValue.EIGHTH, false, true),
)

private fun fmtBpm(b: Double) = if (b % 1.0 == 0.0) b.toLong().toString() else b.toString()

/** Two octaves of keys from C[octave]; the selected note's key lit. */
@Composable
private fun PianoKeys(octave: Int, lit: Int?, held: Set<Int>, onDown: (Int) -> Unit, onUp: (Int) -> Unit) {
    val whites = listOf(0, 2, 4, 5, 7, 9, 11)
    val blacks = listOf(1 to 0, 3 to 1, 6 to 3, 8 to 4, 10 to 5)     // semitone to the white key it sits after
    val down by rememberUpdatedState(onDown)
    val up by rememberUpdatedState(onUp)
    Canvas(
        Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { contentDescription = "Piano keys from C$octave, two octaves" }
            .pointerInput(octave) {
                fun keyAt(pos: Offset): Int {
                    val ww = size.width / 14f
                    val o = (pos.x / (ww * 7)).toInt().coerceIn(0, 1)
                    val local = pos.x - o * ww * 7
                    val base = 12 * (octave + 1 + o)
                    if (pos.y < size.height * 0.6f) {
                        blacks.firstOrNull { (_, after) -> local > (after + 1) * ww - ww * 0.3f && local < (after + 1) * ww + ww * 0.3f }?.let { return base + it.first }
                    }
                    return base + whites[(local / ww).toInt().coerceIn(0, 6)]
                }
                // Every finger is tracked from press to release, so keys can be held together (real-time entry).
                awaitPointerEventScope {
                    val pressed = HashMap<androidx.compose.ui.input.pointer.PointerId, Int>()
                    while (true) {
                        val ev = awaitPointerEvent()
                        for (c in ev.changes) {
                            if (c.pressed && !c.previousPressed) { val k = keyAt(c.position); pressed[c.id] = k; down(k); c.consume() }
                            else if (!c.pressed && c.previousPressed) { pressed.remove(c.id)?.let { up(it) }; c.consume() }
                        }
                    }
                }
            },
    ) {
        val ww = size.width / 14f
        for (o in 0..1) for ((i, semi) in whites.withIndex()) {
            val x = (o * 7 + i) * ww
            val m = 12 * (octave + 1 + o) + semi
            val on = lit == m || m in held
            drawRect(if (on) C.acc else C.hi.copy(alpha = 0.85f), Offset(x + 1f, 0f), Size(ww - 2f, size.height))
        }
        for (o in 0..1) for ((semi, after) in blacks) {
            val x = (o * 7 + after + 1) * ww - ww * 0.3f
            val m = 12 * (octave + 1 + o) + semi
            val on = lit == m || m in held
            drawRect(if (on) C.acc else C.bg, Offset(x, 0f), Size(ww * 0.6f, size.height * 0.6f))
        }
    }
}

/** The duration bar's note values, Noto Music glyphs, names. */
private val DURATION_BAR = listOf(
    Triple(NoteValue.WHOLE, "𝅝", "1"), Triple(NoteValue.HALF, "𝅗𝅥", "1/2"), Triple(NoteValue.QUARTER, "𝅘𝅥", "1/4"),
    Triple(NoteValue.EIGHTH, "𝅘𝅥𝅮", "1/8"), Triple(NoteValue.SIXTEENTH, "𝅘𝅥𝅯", "1/16"), Triple(NoteValue.THIRTY_SECOND, "𝅘𝅥𝅰", "1/32"),
    Triple(NoteValue.SIXTY_FOURTH, "𝅘𝅥𝅱", "1/64"),
)

private val TUPLETS = listOf(null to "None", Tuplet(3, 2) to "Triplet (3 in 2)", Tuplet(5, 4) to "Quintuplet (5 in 4)", Tuplet(6, 4) to "Sextuplet (6 in 4)", Tuplet(7, 4) to "Septuplet (7 in 4)", Tuplet(2, 3) to "Duplet (2 in 3)")

/** Guitar Pro 8's effect categories (docs/research/notation-pianoroll-tab-editors-2026-10-21.md). */
private val FX_GROUPS = listOf(
    "Dynamics · tone" to listOf(Effect.GHOST, Effect.ACCENT, Effect.HEAVY_ACCENT, Effect.STACCATO, Effect.LET_RING, Effect.PALM_MUTE, Effect.DEAD, Effect.FADE_IN, Effect.FADE_OUT, Effect.VOLUME_SWELL),
    "Touch" to listOf(Effect.HAMMER, Effect.LEGATO_SLIDE, Effect.SHIFT_SLIDE, Effect.SLIDE_IN_BELOW, Effect.SLIDE_IN_ABOVE, Effect.SLIDE_OUT_DOWN, Effect.SLIDE_OUT_UP, Effect.BEND, Effect.WHAMMY, Effect.LEFT_TAP, Effect.TAP),
    "Harmonics" to listOf(Effect.NATURAL_HARMONIC, Effect.ARTIFICIAL_HARMONIC),
    "Vibrato · tremolo" to listOf(Effect.VIBRATO, Effect.WIDE_VIBRATO, Effect.TREMOLO_PICKING),
    "Bass and percussive" to listOf(Effect.SLAP, Effect.POP),
)

private fun fraction(r: Rational) = if (r.num == 1L) "1/${r.den}" else "${r.num}/${r.den}"

/** One key of the duration bar: the glyph over a small name. */
@Composable
private fun DurKey(glyph: String, name: String, on: Boolean, color: Color = C.acc, onClick: () -> Unit) {
    Column(
        Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .then(if (on) Modifier.background(color.copy(alpha = 0.2f)) else Modifier)
            .padding(horizontal = 7.dp, vertical = 3.dp)
            .semantics { contentDescription = name },
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Text(glyph, style = TextStyle(fontFamily = MusicFont, fontSize = 18.sp, color = if (on) color else C.hi))
        Text(name, style = T.label.copy(fontSize = 7.sp, color = if (on) color else C.dim))
    }
}

private fun pitchName(p: SpelledPitch) = KeyMode.displayName(p.name) + p.octave

private fun stringName(inst: Instrument, string: Int): String {
    val open = inst.tuning.open(string)
    // Two strings with the same letter (drop D's two Ds) are told apart as low and high.
    val same = (1..inst.tuning.stringCount).filter { inst.tuning.open(it).name == open.name }
    val name = KeyMode.displayName(open.name)
    return if (same.size < 2) name else if (open.midi == same.minOf { inst.tuning.open(it).midi }) "low $name" else "high $name"
}

private fun durationName(e: Event): String {
    val d = e.duration
    val base = when (d.value) {
        NoteValue.BREVE -> "breve"; NoteValue.WHOLE -> "whole"; NoteValue.HALF -> "half"; NoteValue.QUARTER -> "quarter"
        NoteValue.EIGHTH -> "eighth"; NoteValue.SIXTEENTH -> "16th"; NoteValue.THIRTY_SECOND -> "32nd"; NoteValue.SIXTY_FOURTH -> "64th"; else -> "128th"
    }
    val dots = when (d.dots) { 0 -> ""; 1 -> "dotted "; 2 -> "double-dotted "; else -> "triple-dotted " }
    val tuplet = d.timeModification?.let { if (it.actual == 3 && it.normal == 2) " triplet" else " (${it.actual}:${it.normal})" } ?: ""
    return dots + base + tuplet
}

/** The pitch's scale degree in [key]: its letter's place from the tonic, with ♭ or ♯ against the key's own note on that letter. */
private fun degree(key: KeyMode, p: SpelledPitch): String {
    val number = Math.floorMod(p.letter.ordinal - key.tonic.letter.ordinal, 7) + 1
    val scaleNote = runCatching { Spelling.spell(key.tonic, key.scale.degrees).notes }.getOrNull()?.firstOrNull { it.letter == p.letter }
    val diff = p.alter - (scaleNote?.alter ?: 0)
    val acc = when { diff > 0 -> "♯".repeat(diff); diff < 0 -> "♭".repeat(-diff); else -> "" }
    return acc + number
}

private fun noteLabel(score: Score, p: SpelledPitch, mode: String): String = when {
    mode == "degrees" && score.key != null -> degree(score.key!!, p)
    else -> KeyMode.displayName(p.name)
}

/** One event's place on screen, for taps and the cursor. */
private data class EvBox(val at: At, val x: Float, val top: Float, val stringTop: Float, val gap: Float)

/** Tab with the rhythm under it, wrapped into systems that fit the width. */
@Composable
private fun TabCanvas(score: Score, part: Part, selected: At?, selNote: Int, cursor: At?, rowMode: String, offBars: Set<Int>, onCursorY: (Float) -> Unit = {}, onTap: (At, Int) -> Unit) {
    val inst = part.instrument ?: return
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        val width = with(density) { maxWidth.toPx() }
        val px = { d: Dp -> with(density) { d.toPx() } }
        val strings = inst.tuning.stringCount
        val gap = px(11.dp)
        val head = px(16.dp)
        val rhythm = px(30.dp)
        val rowH = if (rowMode == "off") 0f else px(14.dp)
        val sysGap = px(14.dp)
        val label = px(16.dp)
        val systemH = head + gap * (strings - 1) + rhythm + rowH + sysGap
        fun evW(e: Event) = px(20.dp) + px(22.dp) * sqrt((e.length.toDouble() / 0.25).toFloat())
        val barPad = px(8.dp)
        // Lay bars into systems.
        val barWidths = part.bars.map { b -> barPad * 2 + b.voices[0].sumOf { evW(it).toDouble() }.toFloat() }
        val systems = ArrayList<List<Int>>()
        var cur = ArrayList<Int>(); var used = label
        for ((i, w) in barWidths.withIndex()) {
            if (cur.isNotEmpty() && used + w > width) { systems += cur; cur = ArrayList(); used = label }
            cur += i; used += w
        }
        if (cur.isNotEmpty()) systems += cur
        // Following playback: where the system holding the cursor starts, for the screen to scroll to.
        val followY = cursor?.let { c -> systems.indexOfFirst { c.bar in it }.takeIf { it >= 0 }?.let { it * systemH } }
        val follow by rememberUpdatedState(onCursorY)
        LaunchedEffect(followY) { followY?.let { follow(it) } }
        val boxes = ArrayList<EvBox>()
        val barStarts = HashMap<Int, Float>(); val barEnds = HashMap<Int, Float>()
        for ((si, bars) in systems.withIndex()) {
            val top = si * systemH + head
            val natural = bars.sumOf { barWidths[it].toDouble() }.toFloat()
            val stretch = if (si < systems.lastIndex) (width - label) / natural else 1f
            var x = label
            for (b in bars) {
                barStarts[b] = x
                x += barPad * stretch
                for ((i, e) in part.bars[b].voices[0].withIndex()) {
                    val w = evW(e) * stretch
                    boxes += EvBox(At(b, 0, i), x + w / 2 - px(8.dp), si * systemH, top, gap)
                    x += w
                }
                // Voices 2–4 sit where their beats fall in time between voice 1's.
                val v0 = part.bars[b].voices[0]
                val v0Boxes = boxes.filter { it.at.bar == b && it.at.voice == 0 }
                for (v in 1 until part.bars[b].voices.size) {
                    var t = 0.0
                    for ((i, e) in part.bars[b].voices[v].withIndex()) {
                        var s0 = 0.0; var k = 0
                        while (k + 1 < v0.size && s0 + v0[k].length.toDouble() <= t + 1e-9) { s0 += v0[k].length.toDouble(); k++ }
                        val bx0 = v0Boxes.getOrNull(k)
                        val nextX = v0Boxes.getOrNull(k + 1)?.x ?: (x - px(8.dp))
                        val frac = if (bx0 == null || v0.isEmpty()) 0.0 else ((t - s0) / v0[k].length.toDouble()).coerceIn(0.0, 1.0)
                        val vx = if (bx0 == null) x else bx0.x + ((nextX - bx0.x) * frac).toFloat()
                        boxes += EvBox(At(b, v, i), vx, si * systemH, top, gap)
                        t += e.length.toDouble()
                    }
                }
                x += barPad * stretch
                barEnds[b] = x
            }
        }
        val heightDp = with(density) { (systems.size * systemH).toDp() }
        Canvas(
            Modifier.fillMaxWidth().height(heightDp)
                .semantics { contentDescription = "Tab of ${part.name}, ${part.bars.size} bars. Tap a fret number to select it." }
                .pointerInput(score, rowMode) {
                    detectTapGestures { pos ->
                        val inSys = boxes.filter { pos.y >= it.top && pos.y < it.top + systemH }
                        val nearest = inSys.minByOrNull { kotlin.math.abs(it.x - pos.x) } ?: return@detectTapGestures
                        val string0 = ((pos.y - nearest.stringTop) / nearest.gap).let { Math.round(it) + 1 }.coerceIn(1, strings)
                        // Of the beats under the tap, the one with a note on the tapped string, voice 1 first.
                        fun evOf(bx: EvBox) = part.bars[bx.at.bar].voices[bx.at.voice][bx.at.index]
                        val b = inSys.filter { kotlin.math.abs(it.x - nearest.x) < px(10.dp) }
                            .sortedWith(compareBy({ if (evOf(it).notes.any { n -> n.string == string0 }) 0 else 1 }, { it.at.voice })).first()
                        val ev = evOf(b)
                        val string = string0
                        val idx = ev.notes.indexOfFirst { it.string == string }.takeIf { it >= 0 }
                            ?: ev.notes.withIndex().minByOrNull { kotlin.math.abs((it.value.string ?: 0) - string) }?.index ?: 0
                        onTap(b.at, idx)
                    }
                },
        ) {
            val fret = TextStyle(fontSize = 11.sp, color = C.hi, fontWeight = FontWeight.Bold)
            val small = TextStyle(fontSize = 8.sp, color = C.dim)
            val rest = TextStyle(fontFamily = MusicFont, fontSize = 16.sp, color = C.dim)
            val tiedIn = HashSet<Int>()
            var tiedEvent = -1
            for ((si, bars) in systems.withIndex()) {
                val top = si * systemH + head
                val bottom = top + gap * (strings - 1)
                val lastBar = bars.last()
                val xEnd = if (si < systems.lastIndex) width else barEnds.getValue(lastBar)
                // String names, strings, barlines.
                for (st in 1..strings) {
                    val y = top + gap * (st - 1)
                    drawLine(C.line, Offset(label, y), Offset(xEnd, y), 1f)
                    centered(measurer, KeyMode.displayName(inst.tuning.open(st).name), Offset(label / 2, y), small)
                }
                drawLine(C.dim, Offset(label, top), Offset(label, bottom), 1f)
                for (b in bars) {
                    val bx = boxes.filter { it.at.bar == b && it.at.voice == 0 }
                    val endX = if (b == lastBar) xEnd else barEnds.getValue(b)
                    drawLine(C.dim, Offset(endX, top), Offset(endX, bottom), 1f)
                    drawText(measurer, "${b + 1}", Offset(barStarts.getValue(b) + px(2.dp), top - px(14.dp)), if (b in offBars) small.copy(color = C.rec) else small)
                    val events = part.bars[b].voices[0]
                    // Flagged notes, with the beat each starts in: neighbours in one beat share beams.
                    data class Flagged(val i: Int, val beat: Int, val box: EvBox, val e: Event)
                    val flagged = ArrayList<Flagged>()
                    var pos = com.betterappmafia.apophenia.core.rhythm.Rational.ZERO
                    val beatStarts = score.meterAt(b).beatStarts
                    for ((i, e) in events.withIndex()) {
                        val box = bx[i]
                        val here = At(b, 0, i)
                        val isSel = here == selected
                        val isCur = here == cursor
                        if (isCur) drawRoundRect(C.acc.copy(alpha = 0.18f), Offset(box.x - px(6.dp), top - px(6.dp)), Size(px(28.dp), gap * (strings - 1) + px(12.dp)), CornerRadius(4f))
                        if (e.isRest) {
                            centered(measurer, restGlyph(e.duration.value), Offset(box.x + px(8.dp), (top + bottom) / 2), rest)
                        }
                        val tiedNow = HashSet<Int>()
                        e.notes.forEachIndexed { ni, n ->
                            val st = n.string ?: return@forEachIndexed
                            val y = top + gap * (st - 1)
                            val txt = (if (tiedEvent >= 0 && st in tiedIn) "(${n.fret})" else "${n.fret}")
                            val layout = measurer.measure(txt, fret.copy(color = if (isSel && ni == selNote) C.bg else if (isSel) C.acc else C.hi))
                            val tl = Offset(box.x + px(8.dp) - layout.size.width / 2f, y - layout.size.height / 2f)
                            drawRect(if (isSel && ni == selNote) C.acc else C.bg, tl - Offset(2f, 0f), Size(layout.size.width + 4f, layout.size.height.toFloat()))
                            drawText(layout, topLeft = tl)
                            if (n.tie) tiedNow += st
                            val fx = n.effects.joinToString(" ") { it.tab }
                            if (fx.isNotEmpty() && ni == 0) drawText(measurer, fx, Offset(box.x, top - px(26.dp) + px(12.dp)), small)
                        }
                        tiedIn.clear(); tiedIn += tiedNow; tiedEvent = if (tiedNow.isEmpty()) -1 else i
                        // Rhythm under the tab.
                        if (!e.isRest) drawStem(e, box.x + px(8.dp), bottom, px)
                        if (e.duration.timeModification != null) centered(measurer, "${e.duration.timeModification!!.actual}", Offset(box.x + px(8.dp), bottom + px(29.dp)), small)
                        if (!e.isRest && flagCount(e.duration.value) > 0) flagged += Flagged(i, beatStarts.indexOfLast { it <= pos }, box, e)
                        pos += e.length
                        // Note row.
                        if (rowMode != "off" && e.notes.isNotEmpty()) {
                            centered(measurer, e.notes.sortedBy { it.pitch.midi }.joinToString("") { noteLabel(score, it.pitch, rowMode) }.take(8), Offset(box.x + px(8.dp), bottom + rhythm + px(4.dp)), small.copy(color = if (isSel) C.acc else C.dim))
                        }
                    }
                    var k = 0
                    while (k < flagged.size) {
                        var j = k
                        while (j + 1 < flagged.size && flagged[j + 1].i == flagged[j].i + 1 && flagged[j + 1].beat == flagged[k].beat) j++
                        val run = flagged.subList(k, j + 1)
                        if (run.size >= 2) drawBeams(run.map { it.box to it.e }, bottom + px(26.dp), px)
                        else drawFlags(run[0].box.x + px(8.dp), bottom + px(26.dp), flagCount(run[0].e.duration.value), px)
                        k = j + 1
                    }
                    // Voices 2–4: their fret numbers in their colours (the rhythm under the tab is voice 1's).
                    for (box in boxes.filter { it.at.bar == b && it.at.voice > 0 }) {
                        val e = part.bars[b].voices[box.at.voice][box.at.index]
                        val isSel = box.at == selected
                        val col = VOICE_COLORS[box.at.voice]
                        e.notes.forEachIndexed { ni, n ->
                            val st = n.string ?: return@forEachIndexed
                            val y = top + gap * (st - 1)
                            val layout = measurer.measure("${n.fret}", fret.copy(color = if (isSel && ni == selNote) C.bg else col))
                            val tl = Offset(box.x + px(8.dp) - layout.size.width / 2f, y - layout.size.height / 2f)
                            drawRect(if (isSel && ni == selNote) col else C.bg, tl - Offset(2f, 0f), Size(layout.size.width + 4f, layout.size.height.toFloat()))
                            drawText(layout, topLeft = tl)
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.centered(measurer: TextMeasurer, text: String, center: Offset, style: TextStyle) {
    val l = measurer.measure(text, style)
    drawText(l, topLeft = Offset(center.x - l.size.width / 2f, center.y - l.size.height / 2f))
}

private fun flagCount(v: NoteValue) = when (v) {
    NoteValue.EIGHTH -> 1; NoteValue.SIXTEENTH -> 2; NoteValue.THIRTY_SECOND -> 3; NoteValue.SIXTY_FOURTH -> 4; NoteValue.HUNDRED_TWENTY_EIGHTH -> 5; else -> 0
}

private fun restGlyph(v: NoteValue) = when (v) {
    NoteValue.BREVE -> "𝄺"; NoteValue.WHOLE -> "𝄻"; NoteValue.HALF -> "𝄼"; NoteValue.QUARTER -> "𝄽"
    NoteValue.EIGHTH -> "𝄾"; NoteValue.SIXTEENTH -> "𝄿"; NoteValue.THIRTY_SECOND -> "𝅀"; else -> "𝅁"
}

/** Guitar Pro's rhythm under tab: no stem for a whole, a short stem for a half, a full one otherwise; dots beside it. */
private fun DrawScope.drawStem(e: Event, x: Float, bottom: Float, px: (Dp) -> Float) {
    val v = e.duration.value
    val y0 = bottom + px(6.dp)
    val y1 = bottom + px(26.dp)
    when (v) {
        NoteValue.BREVE, NoteValue.WHOLE -> {}
        NoteValue.HALF -> drawLine(C.dim, Offset(x, y0 + px(10.dp)), Offset(x, y1), 1.5f)
        else -> drawLine(C.dim, Offset(x, y0), Offset(x, y1), 1.5f)
    }
    repeat(e.duration.dots) { drawCircle(C.dim, px(1.5.dp), Offset(x + px(4.dp) + it * px(4.dp), y1 - px(2.dp))) }
}

private fun DrawScope.drawFlags(x: Float, y: Float, n: Int, px: (Dp) -> Float) {
    repeat(n) { k -> val yy = y - k * px(4.dp); drawLine(C.dim, Offset(x, yy), Offset(x + px(6.dp), yy - px(4.dp)), 1.5f) }
}

private fun DrawScope.drawBeams(group: List<Pair<EvBox, Event>>, y: Float, px: (Dp) -> Float) {
    val xs = group.map { it.first.x + px(8.dp) }
    val flags = group.map { flagCount(it.second.duration.value) }
    for (level in 1..flags.max()) {
        val yy = y - (level - 1) * px(4.dp)
        var i = 0
        while (i < group.size) {
            if (flags[i] < level) { i++; continue }
            var j = i
            while (j + 1 < group.size && flags[j + 1] >= level) j++
            if (j > i) drawLine(C.dim, Offset(xs[i], yy), Offset(xs[j], yy), px(2.dp))
            else drawLine(C.dim, Offset(xs[i], yy), Offset(xs[i] + (if (i > 0) -px(6.dp) else px(6.dp)), yy), px(2.dp))
            i = j + 1
        }
    }
}

/** The neck, string 1 on top: the selected note filled, its other places hollow. Tap a fret. */
@Composable
private fun Fretboard(inst: Instrument, at: Fingering.Place?, places: List<Fingering.Place>, onTap: (Fingering.Place) -> Unit) {
    val strings = inst.tuning.stringCount
    val last = (places.maxOfOrNull { it.fret } ?: 0).coerceAtLeast(12).let { minOf(inst.frets - inst.capo, it + 1) }
    val measurer = rememberTextMeasurer()
    Canvas(
        Modifier.fillMaxWidth().height((22 + strings * 16).dp).padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { contentDescription = "Fretboard in ${inst.tuning.name}. ${if (at != null) "Selected: string ${at.string}, fret ${at.fret}." else ""}" }
            .pointerInput(inst, last) {
                detectTapGestures { pos ->
                    val fw = size.width / (last + 1f)
                    val gap = (size.height - 16.dp.toPx()) / (strings - 1).coerceAtLeast(1)
                    val fret = (pos.x / fw).toInt().coerceIn(0, last)
                    val string = Math.round(pos.y / gap).toInt().plus(1).coerceIn(1, strings)
                    onTap(Fingering.Place(string, fret))
                }
            },
    ) {
        val fw = size.width / (last + 1f)
        val gap = (size.height - 16.dp.toPx()) / (strings - 1).coerceAtLeast(1)
        val bottom = gap * (strings - 1)
        for (f in 0..last) {
            val x = (f + 1) * fw
            drawLine(if (f == 0) C.hi else C.line, Offset(x, 0f), Offset(x, bottom), if (f == 0) 3f else 1f)
            if (f in setOf(3, 5, 7, 9, 12, 15, 17, 19, 21, 24)) centered(measurer, "$f", Offset(x - fw / 2, bottom + 9.dp.toPx()), TextStyle(fontSize = 8.sp, color = C.faint))
        }
        for (st in 1..strings) drawLine(C.dim, Offset(fw, (st - 1) * gap), Offset(size.width, (st - 1) * gap), 1f + (st - 1) * 0.3f)
        fun center(p: Fingering.Place) = Offset(if (p.fret == 0) fw / 2 else (p.fret + 0.5f) * fw, (p.string - 1) * gap)
        for (pl in places) if (pl != at && pl.fret <= last) drawCircle(C.acc, 5.dp.toPx(), center(pl), style = Stroke(1.5.dp.toPx()))
        if (at != null && at.fret <= last) drawCircle(C.acc, 6.dp.toPx(), center(at))
    }
}

/**
 * The score on the piano, rendered once per version into the cache, played with the score's own id: its notes
 * with their markings, or ([chords]) its chord symbols as the chord chart shows them.
 */
internal fun playScore(model: AppModel, scrapId: Long, title: String, score: Score, part: Part, chords: Boolean = false) {
    model.app.io.launch {
        val text = ScoreText.encode(score)
        val dir = File(model.app.cacheDir, "score").apply { mkdirs() }
        val f = File(dir, "score-$scrapId-${text.hashCode().toUInt().toString(16)}${if (chords) "-chords-" + model.chordDialect.token.replace(" ", "-") else ""}-piano1.wav")
        if (f.length() <= 44) {
            val rate = model.app.audio.sampleRate
            val notes = if (chords) ChordSound.ofScore(score, part, model.chordDialect) else ScorePlayback.notes(score, part)
            val pcm = Piano.render(model.app, notes, rate, ScoreTime.seconds(score, part))
            val tmp = File(f.path + ".part")
            WavWriter(tmp, rate).use { it.write(pcm) }
            tmp.renameTo(f)
        }
        model.app.audio.load(scrapId, -1, title, f.absolutePath, play = true)
    }
}

/**
 * One note on the piano, at mf, held most of a second. It sounds beside the bottom player (Christopher, 26 Oct
 * 2026): whatever is loaded there stays loaded and keeps its place.
 */
private fun playPitch(model: AppModel, midi: Int) {
    model.app.io.launch {
        val pcm = synchronized(pitchCache) {
            pitchCache.getOrPut(midi) { Piano.render(model.app, listOf(PlayNote(0.0, 0.8, midi, Piano.RECORDED)), model.app.audio.sampleRate, 1.0) }
        }
        model.app.audio.cue(pcm)
    }
}

/** Rendered notes by MIDI number: 88 keys at one second each is 17 MB at most, so they are kept. */
private val pitchCache = HashMap<Int, FloatArray>()

/** A read-only tab staff for [part], as the Course draws it. */
@Composable
internal fun TabPreview(score: Score, part: Part) = TabCanvas(score, part, null, 0, null, "off", emptySet()) { _, _ -> }

/** A title as a file name: letters, digits, spaces and . _ - only. */
internal fun safeFileName(title: String) = title.replace(Regex("[^A-Za-z0-9 ._-]"), "").trim().ifEmpty { "Apophenia" }
