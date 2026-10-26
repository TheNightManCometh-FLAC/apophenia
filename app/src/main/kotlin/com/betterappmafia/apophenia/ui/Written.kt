package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.key
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.core.sheet.ChordChart
import com.betterappmafia.apophenia.core.sheet.ChordText
import com.betterappmafia.apophenia.core.sheet.LyricSheet
import com.betterappmafia.apophenia.data.BodyPair
import com.betterappmafia.apophenia.audio.Piano
import com.betterappmafia.apophenia.core.sheet.ChordSound
import com.betterappmafia.apophenia.core.chord.ChordSpelling
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.data.LibraryRow
import com.betterappmafia.apophenia.data.LyricSyncEntity
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import com.betterappmafia.apophenia.data.ScrapFormat
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A scrap opened full size: recordings get the recording view, everything written gets its editor. */
@Composable
fun ScrapDetail(model: AppModel, scrapId: Long, requestMic: (() -> Unit) -> Unit) {
    // A fresh composition per item: collectAsState keeps the last item's value until the new flow emits, and the
    // editors read their first text from it, so + NEW over an open tab opened the new sheet music with the tab in it.
    key(scrapId, model.detailProject) {
        val row by remember(scrapId) { model.app.db.scraps().row(scrapId) }.collectAsState(null)
        val r = row
        if (r == null) Unit
        else if (r.scrap.format == ScrapFormat.RECORDING) RecordingDetail(model, scrapId, requestMic)
        else if (r.scrap.format == ScrapFormat.TAB || r.scrap.format == ScrapFormat.SHEET) ScoreDetail(model, r, model.detailProject)
        else WrittenDetail(model, r, model.detailProject)
    }
}

/** Lyrics auto-scroll speeds 1–6 (C4), in dp per second. [Claude] values; dial them in on the phone. */
private val SCROLL_DP_PER_S = listOf(6f, 10f, 15f, 22f, 32f, 46f)
private val TEXT_SIZES = listOf(14, 17, 20, 24, 30)

/**
 * The editor for a lyric, text note or chord chart (plan §4.3, §4.5, C4). Opened from a Board it
 * shows that project's version (its own copy when it was added from elsewhere, plan §4.2);
 * otherwise the Library's. Everything saves as you type; UNDO/REDO cover every edit.
 */
@OptIn(FlowPreview::class)
@Composable
private fun WrittenDetail(model: AppModel, row: LibraryRow, projectId: Long?) {
    val db = model.app.db
    val s = row.scrap
    val bodyFlow = remember(s.id, projectId) {
        if (projectId != null) db.projects().bodyIn(projectId, s.id)
        else db.scraps().row(s.id).map { it?.let { r -> BodyPair(r.scrap.body, null) } }
    }
    val pair by bodyFlow.collectAsState(null)
    val loaded = pair ?: return
    val ownCopy = projectId != null && loaded.ownBody != null
    // The editor's text is read once; after that the field is the source and the database follows it.
    val state = remember(s.id, projectId) { TextFieldState(loaded.shown) }
    // A drum chart opens on its grid, which is its editor; EDIT shows the text behind it.
    var editing by remember(s.id) { mutableStateOf(s.format != ScrapFormat.DRUM_CHART && (model.detailEdit || s.format == ScrapFormat.TEXT)) }
    var menu by remember { mutableStateOf(false) }
    val save: (String) -> Unit = { text ->
        model.app.io.launch {
            val now = System.currentTimeMillis()
            if (ownCopy) { db.projects().setOwnBody(projectId!!, s.id, text); db.projects().touch(projectId, now) }
            else db.scraps().setBody(s.id, text, now)
        }
    }
    val saveNow by rememberUpdatedState(save)
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.drop(1).debounce(500).collect { saveNow(it) }
    }
    DisposableEffect(state) {
        onDispose { val t = state.text.toString(); if (t != loaded.shown) saveNow(t) }
    }

    val title = displayTitle(s.copy(body = state.text.toString()))
    val kind = when (s.format) { ScrapFormat.LYRIC -> "LYRICS"; ScrapFormat.CHORD_CHART -> "CHORD CHART"; ScrapFormat.DRUM_CHART -> "DRUM CHART"; else -> "TEXT" }
    Column(Modifier.fillMaxSize()) {
        TitleBar {
            Sq("‹", { model.detail = null }, modifier = Modifier.semantics { contentDescription = "Back" })
            Column(Modifier.weight(1f).clickable { model.sheet = Sheet.Rename(s.id) }.semantics { contentDescription = "Rename $title" }) {
                Line1(title, T.title)
                Text(
                    kind + when {
                        projectId == null -> " · LIBRARY"
                        ownCopy -> " · ${model.projectName(projectId)?.uppercase()}'S OWN COPY"
                        else -> " · ${model.projectName(projectId)?.uppercase()}"
                    },
                    style = T.label.copy(fontSize = 8.sp), maxLines = 1,
                )
            }
            if (s.format == ScrapFormat.DRUM_CHART) Sq(if (editing) "GRID" else "TEXT", { editing = !editing }, on = editing)
            else if (s.format != ScrapFormat.TEXT) Sq(if (editing) "DONE" else "EDIT", { editing = !editing }, on = editing)
            Box {
                Sq("⋯", { menu = true }, modifier = Modifier.semantics { contentDescription = "More" })
                ConsoleMenu(menu, { menu = false }, kind, listOf(
                    MenuItem("Tags", row.tagList.size.takeIf { it > 0 }?.let { "$it ›" } ?: "ADD ›") { model.sheet = Sheet.Tags(s.id) },
                    MenuItem("Key", keyShort(s).takeIf { it != "—" } ?: "UNKNOWN ›") { model.sheet = Sheet.Key(s.id) },
                    MenuItem("BPM", bpmText(s).takeIf { it != "—" } ?: "UNKNOWN ›") { model.sheet = Sheet.Bpm(s.id) },
                    MenuItem("Groups", "${row.groupSet.size} ›") { model.sheet = Sheet.Groups(setOf(s.id)) },
                    MenuItem("Projects", row.projectList.size.takeIf { it > 0 }?.let { "IN $it ›" } ?: "ADD ›") { model.sheet = Sheet.Projects(setOf(s.id)) },
                    MenuItem(if (s.format == ScrapFormat.TEXT || s.format == ScrapFormat.DRUM_CHART) "Export text" else "Export ChordPro", if (s.format == ScrapFormat.TEXT || s.format == ScrapFormat.DRUM_CHART) ".txt" else ".cho") {
                        val body = state.text.toString()
                        val key = com.betterappmafia.apophenia.core.catalog.KeyMode.parseOrNull(s.key)
                        val bpm = s.bpmLow?.takeIf { s.bpmHigh == null || s.bpmHigh == it }?.toDouble()
                        when (s.format) {
                            ScrapFormat.LYRIC -> model.exportFile(com.betterappmafia.apophenia.core.sheet.Exports.lyrics(title, body, key, bpm).toByteArray(), safeFileName(title) + ".cho", "ChordPro file")
                            ScrapFormat.CHORD_CHART -> model.exportFile(com.betterappmafia.apophenia.core.sheet.Exports.chart(title, body, key, bpm).toByteArray(), safeFileName(title) + ".cho", "ChordPro file")
                            else -> model.exportFile(body.toByteArray(), safeFileName(title) + ".txt", "Text file")
                        }
                    },
                    MenuItem("Delete", "TO TRASH") { model.trashScrap(s.id, title) },
                ))
            }
        }
        if (editing) Editor(s.format, state)
        else when (s.format) {
            ScrapFormat.LYRIC -> LyricsView(model, s.id, state.text.toString())
            ScrapFormat.CHORD_CHART -> ChartView(model, s, state.text.toString())
            ScrapFormat.DRUM_CHART -> DrumChartView(model, s, state)
            else -> Editor(s.format, state)
        }
    }
}

/** The source editor: monospace, with keys for what's awkward to type on a phone keyboard, and undo. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun androidx.compose.foundation.layout.ColumnScope.Editor(format: ScrapFormat, state: TextFieldState) {
    fun put(text: String, cursorBack: Int = 0) = state.edit {
        val at = selection.min
        replace(selection.min, selection.max, text)
        selection = TextRange(at + text.length - cursorBack)
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Sq("UNDO", { state.undoState.undo() }, enabled = state.undoState.canUndo)
        Sq("REDO", { state.undoState.redo() }, enabled = state.undoState.canRedo)
        when (format) {
            ScrapFormat.LYRIC -> {
                Sq("[ CHORD ]", { put("[]", 1) })
                Sq("SECTION", { put("{c: }", 1) })
            }
            ScrapFormat.CHORD_CHART -> {
                Sq("|", { put(" | ") })
                Sq("/", { put(" / ") })
                Sq("%", { put(" % ") })
                Sq("SECTION", { put("{c: }", 1) })
                Sq("METER", { put("{time: 4/4}") })
            }
            ScrapFormat.DRUM_CHART -> {
                Sq("|", { put("|") })
                Sq("-", { put("-") })
                Sq("x", { put("x") })
                Sq("X", { put("X") })
                Sq("g", { put("g") })
                Sq("o", { put("o") })
            }
            else -> {}
        }
    }
    val hint = when (format) {
        ScrapFormat.LYRIC -> "[Dm7]Salt on the [G]window, the\n{c: Chorus} starts a section"
        ScrapFormat.CHORD_CHART -> "{c: Chorus}\n| F / C / | G / Dm7 / |\none chord or / per beat"
        ScrapFormat.DRUM_CHART -> "{time: 4/4}\n{steps: 4}\nHH |x-x-x-x-x-x-x-x-|\nSD |----X-------X---|\nBD |x-------x-------|"
        else -> "Write anything"
    }
    Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (state.text.isEmpty()) Text(hint, style = T.body.copy(color = C.faint, fontSize = 15.sp, lineHeight = 22.sp))
        BasicTextField(
            state = state,
            textStyle = T.body.copy(color = C.hi, fontSize = 15.sp, lineHeight = 22.sp),
            cursorBrush = SolidColor(C.acc),
            lineLimits = TextFieldLineLimits.MultiLine(),
            modifier = Modifier.fillMaxSize(),
        )
    }
    if (format == ScrapFormat.CHORD_CHART) {
        val problems = remember(state.text) { ChordChart.parse(state.text.toString()).problems }
        problems.take(3).forEach { Text(it, style = T.small.copy(color = C.acc), modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) }
    }
    if (format == ScrapFormat.DRUM_CHART) {
        val problems = remember(state.text) { com.betterappmafia.apophenia.core.tools.DrumChart.parse(state.text.toString()).problems }
        problems.take(3).forEach { Text(it, style = T.small.copy(color = C.acc), modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) }
    }
}

/**
 * Lyrics full screen with chords over the words (C4). Unsynced, they auto-scroll at a speed you set.
 * Synced to the take in the player (plan §4.4: tap each line as it goes by, once, then nudge), the
 * line being sung is lit and kept a third of the way down while that take plays.
 */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.LyricsView(model: AppModel, lyricId: Long, source: String) {
    val lines = remember(source) { LyricSheet.parse(source) }
    // Sync counts lines of words only, in order.
    val wordIndex = remember(lines) { var k = 0; lines.map { if (it is LyricSheet.Line.Words) k++ else -1 } }
    val wordCount = wordIndex.count { it >= 0 }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val audio = model.app.audio
    val player by audio.player.collectAsState()
    val syncs by remember(lyricId) { model.app.db.lyricSyncs().of(lyricId) }.collectAsState(emptyList())
    val active = syncs.firstOrNull { it.takeId == player?.takeId }
    var running by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var showChords by remember { mutableStateOf(true) }
    var size by remember { mutableIntStateOf(2) }
    var syncing by remember { mutableStateOf(false) }
    val taps = remember { androidx.compose.runtime.mutableStateListOf<Long?>() }
    val lineY = remember { HashMap<Int, Int>() }
    val p = player
    val current = when {
        syncing -> taps.size - 1
        active != null && p != null -> LyricSyncEntity.lineAt(active.lineFrames, p.position)
        else -> -1
    }
    fun saveSync(frames: List<Long?>) {
        val take = p ?: return
        model.app.io.launch { model.app.db.lyricSyncs().put(LyricSyncEntity.of(lyricId, take.takeId, frames, System.currentTimeMillis())) }
    }
    LaunchedEffect(running, model.lyricSpeed) {
        if (!running) return@LaunchedEffect
        val pxPerNs = with(density) { SCROLL_DP_PER_S[model.lyricSpeed - 1].dp.toPx() } / 1e9f
        var last = withFrameNanos { it }
        var carry = 0f
        while (isActive && scroll.value < scroll.maxValue) {
            val now = withFrameNanos { it }
            carry += (now - last) * pxPerNs
            last = now
            if (carry >= 1f) { val step = carry.toInt(); carry -= step; scroll.scrollTo(scroll.value + step) }
        }
        running = false
    }
    // Following a synced take: keep the line being sung a third of the way down.
    LaunchedEffect(current, active != null, p?.playing) {
        if (current < 0 || (active == null && !syncing) || p?.playing != true) return@LaunchedEffect
        val y = lineY[current] ?: return@LaunchedEffect
        scroll.animateScrollTo((y - scroll.viewportSize / 3).coerceAtLeast(0))
    }
    val fs = TEXT_SIZES[size].sp
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 220.dp)) {
        if (lines.isEmpty() || lines.all { it == LyricSheet.Line.Blank }) Text("No words yet. Tap EDIT.", style = T.body.copy(color = C.faint))
        lines.forEachIndexed { i, line ->
            when (line) {
                is LyricSheet.Line.Section -> Text(line.label.uppercase(), style = T.label.copy(fontSize = (fs.value * 0.6f).sp, color = C.acc), modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                LyricSheet.Line.Blank -> Gap(10.dp)
                is LyricSheet.Line.Words -> {
                    val w = wordIndex[i]
                    val lit = w == current
                    val (top, bottom) = LyricSheet.chordsOverWords(line)
                    Column(
                        Modifier.fillMaxWidth().background(if (lit) C.selected else androidx.compose.ui.graphics.Color.Transparent)
                            .onGloballyPositioned { lineY[w] = it.positionInParent().y.toInt() }
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        if (showChords && top.isNotEmpty()) Text(top, style = T.body.copy(fontSize = fs, color = C.acc, fontWeight = FontWeight.Bold), softWrap = false)
                        Text(if (showChords) bottom else line.text, style = T.body.copy(fontSize = fs, color = if (current >= 0 && !lit) C.text else C.hi, lineHeight = (fs.value * 1.35f).sp), softWrap = false)
                    }
                }
            }
        }
    }
    if (syncing) {
        // Tap as each line starts. The take plays from the top; every tap marks the next line.
        val next = taps.size
        Row(Modifier.fillMaxWidth().height(76.dp).background(C.selected).drawBehindTop(C.acc)) {
            Box(
                Modifier.weight(1f).fillMaxHeight().clickable {
                    taps.add(audio.player.value?.position)
                    if (taps.size >= wordCount) { saveSync(taps.toList()); syncing = false; model.notice("Synced. Nudge it from ⋯ if it runs early or late.") }
                }.semantics { contentDescription = "Tap as line ${next + 1} starts" },
                contentAlignment = Alignment.Center,
            ) { Text(if (next < wordCount) "TAP AS LINE ${next + 1} OF $wordCount STARTS" else "DONE", style = T.button.copy(fontSize = 14.sp)) }
            Box(Modifier.width(96.dp).fillMaxHeight().clickable {
                if (taps.isNotEmpty()) saveSync(taps.toList() + List(wordCount - taps.size) { null })
                syncing = false
            }, contentAlignment = Alignment.Center) { Text("STOP", style = T.button) }
        }
        return
    }
    Row(Modifier.fillMaxWidth().height(54.dp).background(C.panel).drawBehindTop(C.line)) {
        Box(Modifier.weight(1f).fillMaxHeight().clickable { running = !running }, contentAlignment = Alignment.Center) {
            Text(if (running) "■ STOP" else "▶ SCROLL", style = T.button.copy(fontSize = 12.sp, color = if (running) C.acc else C.hi))
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Box(Modifier.fillMaxSize().clickable { speedMenu = true }, contentAlignment = Alignment.Center) { Text("SPEED ${model.lyricSpeed} ▾", style = T.button.copy(fontSize = 12.sp)) }
            ConsoleMenu(speedMenu, { speedMenu = false }, "AUTO-SCROLL SPEED", (1..6).map { v -> MenuItem("$v", if (v == model.lyricSpeed) "✓" else "") { model.lyricSpeed = v } })
        }
        Box(Modifier.weight(0.6f).fillMaxHeight()) {
            Box(Modifier.fillMaxSize().clickable { moreMenu = true }.semantics { contentDescription = "View options" }, contentAlignment = Alignment.Center) { Text("⋯", style = T.button) }
            val nudge = audio.sampleRate / 10L
            ConsoleMenu(moreMenu, { moreMenu = false }, "WORDS", buildList {
                add(MenuItem("Sync lines to the recording playing", p?.title?.uppercase() ?: "▶ ONE FIRST", enabled = p != null && wordCount > 0) {
                    running = false
                    taps.clear()
                    syncing = true
                    audio.seek(0f)
                })
                if (active != null) {
                    add(MenuItem("Sync earlier", "−0.1 s") { saveSync(active.lineFrames.map { it?.let { f -> (f - nudge).coerceAtLeast(0) } }) })
                    add(MenuItem("Sync later", "+0.1 s") { saveSync(active.lineFrames.map { it?.let { f -> f + nudge } }) })
                    add(MenuItem("Remove this sync", "") { model.app.io.launch { model.app.db.lyricSyncs().delete(lyricId, active.takeId) } })
                }
                add(MenuItem("Text size", listOf("S", "M", "L", "XL", "XXL")[size] + " ›") { size = (size + 1) % TEXT_SIZES.size })
                add(MenuItem("Chords", if (showChords) "SHOWN" else "HIDDEN") { showChords = !showChords })
                add(MenuItem("Back to the top", "") { running = false; model.app.io.launch { scroll.scrollTo(0) } })
            })
        }
    }
    if (active != null) Text(
        "SYNCED TO ${p?.title?.uppercase()} · FOLLOWS IT AS IT PLAYS",
        style = T.label.copy(fontSize = 8.sp), modifier = Modifier.fillMaxWidth().background(C.panel).padding(horizontal = 12.dp, vertical = 3.dp),
    )
}

/**
 * A chord chart as bars, two to a row, one slot per beat (C4). ▶ PLAY CHORDS hears it on the piano at the scrap's
 * BPM (the drum chart's default when it has none), bass and chord, struck at each change and again at each barline.
 */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ChartView(model: AppModel, s: com.betterappmafia.apophenia.data.ScrapEntity, source: String) {
    val chart = remember(source) { ChordChart.parse(source) }
    val audio = model.app.audio
    val bpm = s.bpmLow?.toInt() ?: DRUM_CHART_DEFAULT_BPM
    val key = KeyMode.parseOrNull(s.key)
    val player by audio.player.collectAsState()
    val title = "${displayTitle(s)} · chords · $bpm"
    val p = player
    val mine = p != null && p.scrapId == s.id && p.takeId == -1L && p.title == title
    if (chart.bars.isNotEmpty()) Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Sq(if (mine && p!!.playing) "■ STOP" else "▶ PLAY CHORDS", {
            if (mine) audio.togglePlay() else playChordChart(model, s, source)
        }, on = true)
        Sq("$bpm BPM${if (s.bpmLow == null) " (NOT SET)" else ""} ›", { model.sheet = Sheet.Bpm(s.id) })
        ChordsMenu(model)
    }
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
        if (chart.bars.isEmpty()) Text("No bars yet. Tap EDIT.", style = T.body.copy(color = C.faint), modifier = Modifier.padding(14.dp))
        var lastMeter: com.betterappmafia.apophenia.core.rhythm.TimeSignature? = null
        chart.sections.forEach { sec ->
            val meter = sec.bars.firstOrNull()?.meter
            val head = listOfNotNull(sec.label?.uppercase(), meter?.takeIf { it != lastMeter }?.toString()).joinToString(" · ")
            if (head.isNotEmpty()) Text(head, style = T.label, modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 6.dp))
            lastMeter = sec.bars.lastOrNull()?.meter ?: lastMeter
            sec.bars.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).heightIn(min = 60.dp).border(0.5.dp, C.line2)) {
                    pair.forEach { bar ->
                        Row(Modifier.weight(1f).heightIn(min = 60.dp).border(0.5.dp, if (bar.fits) C.line2 else C.acc), verticalAlignment = Alignment.CenterVertically) {
                            bar.beats.forEach { b ->
                                Text(
                                    if (b == ChordChart.HOLD) androidx.compose.ui.text.AnnotatedString("/") else chordShown(model, b, key),
                                    style = T.hi.copy(fontSize = if (b.length > 4) 13.sp else 17.sp, color = if (b == ChordChart.HOLD) C.faint else if (b == ChordChart.REPEAT || ChordText.readable(b, model.chordDialect)) C.hi else C.rec),
                                    maxLines = 1, softWrap = false,
                                    modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
        chart.problems.forEach { Text(it, style = T.small.copy(color = C.acc), modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)) }
    }
}

/** A chord-chart scrap's chords on the piano at its BPM, played under the scrap's own id so the Board can follow it. */
internal fun playChordChart(model: AppModel, s: com.betterappmafia.apophenia.data.ScrapEntity, source: String) {
    val bpm = s.bpmLow?.toInt() ?: DRUM_CHART_DEFAULT_BPM
    val audio = model.app.audio
    model.app.io.launch {
        val f = renderReference(model, "chords-${Integer.toHexString(source.hashCode())}-$bpm-${model.chordDialect.token.replace(" ", "-")}-piano1.wav") {
            val (notes, seconds) = ChordSound.ofChart(ChordChart.parse(source), bpm.toDouble(), model.chordDialect)
            Piano.render(model.app, notes, audio.sampleRate, seconds)
        }
        audio.load(s.id, -1, "${displayTitle(s)} · chords · $bpm", f.absolutePath, play = true)
    }
}
