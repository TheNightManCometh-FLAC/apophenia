package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.audio.Wav
import com.betterappmafia.apophenia.core.sheet.ChordText
import com.betterappmafia.apophenia.core.sheet.ChordChart
import com.betterappmafia.apophenia.core.sheet.LyricSheet
import com.betterappmafia.apophenia.data.BoardLayout
import com.betterappmafia.apophenia.data.BoardRect
import com.betterappmafia.apophenia.data.BoardRow
import com.betterappmafia.apophenia.data.ProjectEntity
import com.betterappmafia.apophenia.data.ProjectRow
import com.betterappmafia.apophenia.data.ScrapFormat
import com.betterappmafia.apophenia.core.tools.DrumChart
import com.betterappmafia.apophenia.core.tools.DrumVoice
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** BOARD (plan §4.3): the open project's working sheet, or the launch choices when none is open (§2.3). */
@Composable
fun BoardScreen(model: AppModel, requestMic: (() -> Unit) -> Unit) {
    val projects by model.projects.collectAsState()
    val pid = model.projectId
    val project = projects.firstOrNull { it.project.id == pid }?.project
    when {
        project != null -> ProjectBoard(model, project, requestMic)
        pid != null && projects.isEmpty() -> {} // the list is still loading at launch
        else -> NoProjectBoard(model, projects, requestMic)
    }
}

/** No fake default project: New / Open / Record / Library (plan §2.3; Write arrives with the text editors). */
@Composable
private fun NoProjectBoard(model: AppModel, projects: List<ProjectRow>, requestMic: (() -> Unit) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TitleBar { Text("BOARD", style = T.title, modifier = Modifier.weight(1f)); Text("NO PROJECT OPEN", style = T.label) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                "A project's Board holds windows onto the scraps you're weighing for one song. Recordings made here go to the project and stay in the Library.",
                style = T.body.copy(lineHeight = 20.sp), modifier = Modifier.padding(14.dp),
            )
            if (projects.isNotEmpty()) {
                Text("OPEN A PROJECT", style = T.label, modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 4.dp))
                projects.forEach { p ->
                    Kv(p.project.name.uppercase(), onClick = { model.openProject(p.project.id) }) { Text("${p.scraps} on its Board", style = T.body) }
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            BigButton("+ NEW PROJECT", { model.sheet = Sheet.ProjectName(null) }, primary = true)
        }
        Row(Modifier.fillMaxWidth()) {
            BigButton("● RECORD", { requestMic { model.startRecording() } })
            WriteButton(model, null)
            BigButton("LIBRARY ›", { model.go(Mode.LIBRARY) })
        }
    }
}

@Composable
private fun ProjectBoard(model: AppModel, project: ProjectEntity, requestMic: (() -> Unit) -> Unit) {
    val rows by remember(project.id) { model.app.db.projects().boardRows(project.id) }.collectAsState(null)
    var menu by remember { mutableStateOf(false) }
    var flMenu by remember { mutableStateOf(false) }
    var playMenu by remember { mutableStateOf(false) }
    val stackHere by remember(project.id) { model.app.db.stacks().scrapIdsInProjectStack(project.id) }.collectAsState(emptyList())
    val record = { requestMic { model.startRecording(RecordTarget.Project(project.id, project.name)) } }
    Column(Modifier.fillMaxSize()) {
        TitleBar {
            Text("BOARD", style = T.title)
            Text(if (rows.isNullOrEmpty()) "" else "HOLD AND DRAG TO ARRANGE · CORNER TO RESIZE", style = T.label.copy(fontSize = 8.sp), modifier = Modifier.weight(1f))
            Box {
                val player by model.app.audio.player.collectAsState()
                val here = player?.takeIf { pl -> rows.orEmpty().any { it.scrap.id == pl.scrapId } }
                Sq(if (here?.playing == true) "■ STOP" else "▶ PLAY ▾", { if (here?.playing == true) model.app.audio.togglePlay() else playMenu = true }, on = here?.playing == true)
                ConsoleMenu(playMenu, { playMenu = false }, "PLAY ALONG · THE BOARD FOLLOWS", playAlongItems(model, rows.orEmpty(), here, project.id, stackHere.isNotEmpty()))
            }
            Box {
                Sq("⋯", { menu = true }, modifier = Modifier.semantics { contentDescription = "Board menu" })
                ConsoleMenu(menu, { menu = false }, "BOARD · ${project.name.uppercase()}", listOf(
                    MenuItem("Record into ${project.name}", "● NEW") { record() },
                    MenuItem("Add from the Library", "PICK ›") { model.pickFromLibrary(project.id) },
                    MenuItem("Stand mode", "LANDSCAPE · LARGE TYPE") { model.standProject = project.id },
                ) + WRITABLE.map { (f, label, _) -> MenuItem("Write ${label.lowercase()}", formatTag(f)) { model.write(f, project.id) } } +
                    listOf(MenuItem("Write a guitar tab", "TAB") { model.writeTab(false, project.id) }, MenuItem("Write a bass tab", "TAB") { model.writeTab(true, project.id) }, MenuItem("Write sheet music", "NOT") { model.writeSheet(project.id) }, MenuItem("Write in the piano roll", "NOT") { model.writeSheet(project.id, roll = true) }) +
                    MenuItem("Import from the phone", "INTO ${project.name.uppercase()} ›", enabled = model.importing == 0) { model.startImport(project.id) } +
                    MenuItem("Export for FL Studio", "ZIP ▾", enabled = !model.exporting) { flMenu = true }
                )
                ConsoleMenu(flMenu, { flMenu = false }, "FL STUDIO PACKAGE · WHICH AUDIO", flStudioItems { model.stacks.flPackage(it, project.id) })
            }
        }
        val list = rows ?: return@Column
        if (list.isEmpty()) {
            Text(
                "Nothing on this Board yet. Record, write lyrics, a note or a chord chart, or add scraps from the Library (they stay there, and in any other project too).",
                style = T.body.copy(lineHeight = 20.sp), modifier = Modifier.padding(14.dp).weight(1f),
            )
            Row(Modifier.fillMaxWidth()) {
                BigButton("● RECORD", { record() })
                WriteButton(model, project.id)
                BigButton("LIBRARY ›", { model.pickFromLibrary(project.id) }, primary = true)
            }
            return@Column
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val boardW = maxWidth.value.toInt()
            val layout = remember(list, boardW) { layoutOf(list, boardW) }
            // New windows get the first free spot once; after that they stay where the musician puts them.
            LaunchedEffect(layout, list) {
                val fresh = list.filter { isFresh(it, boardW) }
                if (fresh.isNotEmpty()) model.app.io.launch {
                    fresh.forEach { r -> layout[r.scrap.id]?.let { b -> model.app.db.projects().place(project.id, r.scrap.id, b.x, b.y, b.w, b.h, r.z) } }
                }
            }
            val topZ = (list.maxOfOrNull { it.z } ?: 0) + 1
            val inStack by remember(project.id) { model.app.db.stacks().scrapIdsInProjectStack(project.id) }.collectAsState(emptyList())
            val contentH = (layout.values.maxOfOrNull { it.bottom } ?: 0) + 200
            // The Board follows what plays (plan §4.4): the window being played is brought into view.
            val boardScroll = rememberScrollState()
            val player by model.app.audio.player.collectAsState()
            val following = player?.takeIf { it.playing }?.scrapId?.takeIf { layout.containsKey(it) }
            val density = LocalDensity.current
            LaunchedEffect(following) { following?.let { id -> layout[id]?.let { r -> boardScroll.animateScrollTo(with(density) { (r.y - 12).coerceAtLeast(0).dp.roundToPx() }) } } }
            Box(Modifier.fillMaxSize().verticalScroll(boardScroll)) {
                Box(Modifier.fillMaxWidth().height(contentH.dp)) {
                    for (row in list) {
                        val rect = layout[row.scrap.id] ?: continue
                        key(row.scrap.id) {
                            BoardWindow(model, project.id, row, rect, boardW, inStack = row.scrap.id in inStack) { r ->
                                model.app.io.launch { model.app.db.projects().place(project.id, row.scrap.id, r.x, r.y, r.w, r.h, topZ) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Not placed yet, or still where an older build put it by default (laid out again as a square). */
private fun isFresh(r: BoardRow, boardW: Int) = !r.placed || BoardLayout.isOldDefault(BoardRect(r.x!!, r.y!!, r.w!!, r.h!!), boardW)

/** Saved places, plus the first free spot for each fresh window, in the order they were added. */
private fun layoutOf(rows: List<BoardRow>, boardW: Int): Map<Long, BoardRect> {
    val out = LinkedHashMap<Long, BoardRect>()
    rows.filter { !isFresh(it, boardW) }.forEach { out[it.scrap.id] = BoardRect(it.x!!, it.y!!, it.w!!, it.h!!) }
    rows.filter { isFresh(it, boardW) }.sortedBy { it.addedAt }.forEach { out[it.scrap.id] = BoardLayout.nextFree(out.values.toList(), boardW) }
    return out
}

/**
 * One window onto a scrap (C4): type, title, a glimpse of the content, its numbers and tags. Tap
 * opens it full size; hold then drag moves it; the corner resizes it. [place] saves the new rect.
 */
@Composable
private fun BoardWindow(model: AppModel, projectId: Long, row: BoardRow, rect: BoardRect, boardW: Int, inStack: Boolean, place: (BoardRect) -> Unit) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var moveBy by remember { mutableStateOf(Offset.Zero) }
    var sizeBy by remember { mutableStateOf(Offset.Zero) }
    var active by remember { mutableStateOf(false) }
    // What was just dropped, shown until the saved rect comes back from the database.
    var pending by remember { mutableStateOf<BoardRect?>(null) }
    LaunchedEffect(rect) { pending = null }
    val base = pending ?: rect
    fun px2dp(v: Float) = with(density) { v.toDp().value }.roundToInt()
    val shown = BoardLayout.clamp(BoardRect(base.x + px2dp(moveBy.x), base.y + px2dp(moveBy.y), base.w + px2dp(sizeBy.x), base.h + px2dp(sizeBy.y)), boardW)
    val current by rememberUpdatedState(shown)
    val placeNow by rememberUpdatedState(place)
    val s = row.scrap
    val title = displayTitle(s)
    val open = { model.open(s.id, projectId) }
    fun drop() {
        val r = current
        pending = r; moveBy = Offset.Zero; sizeBy = Offset.Zero; active = false
        placeNow(r)
    }

    Box(
        Modifier
            .offset { IntOffset(shown.x.dp.roundToPx(), shown.y.dp.roundToPx()) }
            .size(shown.w.dp, shown.h.dp)
            .zIndex(if (active) 10_000f else row.z.toFloat())
            // Laid down by hand, not in rows: a slight tilt, the same for a scrap every time; straight while it's being moved.
            .graphicsLayer { rotationZ = if (active) 0f else ((s.id * 37 % 5) - 2) * 0.6f }
            .background(C.panel)
            .border(1.dp, if (active) C.acc else C.line2)
            .pointerInput(s.id) { detectTapGestures(onTap = { open() }, onLongPress = {}) }
            .pointerInput(s.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { active = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                    onDragEnd = { drop() },
                    onDragCancel = { drop() },
                ) { change, amount -> change.consume(); moveBy += amount }
            }
            .semantics {
                contentDescription = "${formatTag(s.format)} window: $title. Hold and drag to move it."
                onClick("Open") { open(); true }
            },
    ) {
        Column(Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 6.dp)) {
            // What it is, at a glance (Christopher, 25 Oct 2026: "instantly understand what they are by the tags"):
            // the kind of fragment as a filled tag, then its own tags, then the title.
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                maxLines = 2,
            ) {
                Text(
                    formatWord(s.format), style = T.label.copy(fontWeight = FontWeight.ExtraBold, fontSize = 9.sp, color = C.bg),
                    modifier = Modifier.background(C.acc).padding(horizontal = 5.dp, vertical = 2.dp),
                )
                row.tagList.forEach { t ->
                    Text(t.uppercase(), style = T.label.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp, color = C.hi), maxLines = 1,
                        modifier = Modifier.border(1.dp, C.line2).padding(horizontal = 5.dp, vertical = 2.dp))
                }
            }
            Line1(title, T.hi.copy(fontSize = 13.sp), Modifier.padding(top = 6.dp))
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 5.dp)) { Glimpse(model, row) }
            val meta = buildList {
                if (bpmText(s) != "—") add(bpmText(s) + if (s.freeTime) "" else " BPM")
                if (keyShort(s) != "—") add(keyShort(s))
                if (row.takes > 1) add("${row.takes} TAKES")
                if (row.starred > 0) add("★ ${row.starred}")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Line1(meta.joinToString(" · "), T.label.copy(fontSize = 9.sp), Modifier.weight(1f))
                if (s.format == ScrapFormat.RECORDING) {
                    // C4: + STACK puts it in this project's stack (and the stack in the player); IN STACK takes it out.
                    Box(
                        Modifier.padding(end = 22.dp).border(1.dp, if (inStack) C.acc else C.line2)
                            .clickable { if (inStack) model.stacks.removeScrap(projectId, s.id) else model.stacks.add(projectId, setOf(s.id)) }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .semantics { contentDescription = if (inStack) "In the stack. Tap to take it out." else "Add to the stack" },
                    ) { Text(if (inStack) "IN STACK" else "+ STACK", style = T.label.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (inStack) C.acc else C.text)) }
                }
            }
        }
        // The resize corner.
        Box(
            Modifier.align(Alignment.BottomEnd).size(30.dp)
                .pointerInput(s.id) {
                    detectDragGestures(
                        onDragStart = { active = true },
                        onDragEnd = { drop() },
                        onDragCancel = { drop() },
                    ) { change, amount -> change.consume(); sizeBy += amount }
                }
                .semantics { contentDescription = "Resize $title" },
        ) {
            Canvas(Modifier.fillMaxSize().padding(7.dp)) {
                val c = if (active) C.acc else C.dim
                drawLine(c, Offset(size.width, 0f), Offset(0f, size.height), 1.5f)
                drawLine(c, Offset(size.width, size.height * 0.5f), Offset(size.width * 0.5f, size.height), 1.5f)
            }
        }
    }
}

/** What the window shows of the scrap: a recording's waveform (its newest starred take, else its newest). */
@Composable
private fun Glimpse(model: AppModel, row: BoardRow) {
    when (row.scrap.format) {
        ScrapFormat.RECORDING -> {
            val path = row.glimpsePath ?: return
            val peaks by produceState(FloatArray(0), path) {
                value = withContext(Dispatchers.IO) { runCatching { Wav.peaks(File(model.app.audioDir, path), 48) }.getOrDefault(FloatArray(0)) }
            }
            val player by model.app.audio.player.collectAsState()
            val p = player
            val progress = if (p != null && p.scrapId == row.scrap.id && p.frames > 0) p.position.toFloat() / p.frames else -1f
            Waveform(peaks, Modifier.fillMaxSize(), progress = progress)
        }
        ScrapFormat.LYRIC, ScrapFormat.TEXT, ScrapFormat.CHORD_CHART, ScrapFormat.DRUM_CHART -> {
            val body = row.shownBody
            val player by model.app.audio.player.collectAsState()
            val p = player?.takeIf { it.playing }
            val syncs by remember(row.scrap.id) { model.app.db.lyricSyncs().of(row.scrap.id) }.collectAsState(emptyList())
            // Playing along (plan §4.4): a lyric synced to the take that's playing shows from the line being sung,
            // a chord chart being played from the bar it's on.
            val following: String? = when {
                p == null -> null
                row.scrap.format == ScrapFormat.LYRIC -> syncs.firstOrNull { it.takeId == p.takeId }?.let { sync ->
                    val words = LyricSheet.parse(body).filterIsInstance<LyricSheet.Line.Words>().map { it.text }
                    val at = com.betterappmafia.apophenia.data.LyricSyncEntity.lineAt(sync.lineFrames, p.position).coerceAtLeast(0)
                    words.drop(at).mapIndexed { i, w -> if (i == 0) "▶ $w" else w }.joinToString("\n")
                }
                row.scrap.format == ScrapFormat.CHORD_CHART && p.scrapId == row.scrap.id && p.takeId == -1L -> {
                    val chart = ChordChart.parse(body)
                    val tempo = com.betterappmafia.apophenia.core.rhythm.Tempo(row.scrap.bpmLow ?: DRUM_CHART_DEFAULT_BPM.toDouble())
                    val now = p.position.toDouble() / p.rate
                    var t = 0.0
                    var bar = 0
                    for ((i, b) in chart.bars.withIndex()) { bar = i; val len = tempo.secondsFor(b.meter.barLength); if (now < t + len) break; t += len }
                    "▶ BAR ${bar + 1} OF ${chart.bars.size}\n" + chart.bars.drop(bar).take(8).joinToString(" | ", "| ", " |") { b -> b.beats.joinToString(" ") { ChordText.display(it) } }
                }
                else -> null
            }
            val text = following ?: when (row.scrap.format) {
                ScrapFormat.LYRIC -> LyricSheet.plainText(body)
                ScrapFormat.CHORD_CHART -> ChordChart.preview(body, 8).takeIf { body.isNotBlank() }.orEmpty()
                ScrapFormat.DRUM_CHART -> DrumChart.parse(body).grid.let { g ->
                    "${g.meter} · ${g.bars} BAR${if (g.bars > 1) "S" else ""}\n" +
                        DrumVoice.entries.filter { it in g.rows }.joinToString("\n") { v -> "${v.tab} |${g.tab(v).take(g.stepsPerBar * 2 + 1)}" }
                }
                else -> body.trim()
            }
            Text(
                text.ifEmpty { "empty · tap to write" },
                style = T.small.copy(fontSize = 11.sp, lineHeight = 15.sp, color = if (text.isEmpty()) C.faint else C.text),
                overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
            )
        }
        ScrapFormat.TAB, ScrapFormat.SHEET -> {
            val score = remember(row.shownBody) { runCatching { com.betterappmafia.apophenia.core.score.ScoreText.decode(row.shownBody) }.getOrNull() }
            val part = score?.parts?.firstOrNull()
            // Played on the piano from here or the Board, it follows the cursor: the bar being played and the next.
            val player by model.app.audio.player.collectAsState()
            val p = player?.takeIf { it.playing && it.scrapId == row.scrap.id && it.takeId == -1L && !it.path.contains("-chords-") }
            val slots = remember(score) { if (score == null || part == null) emptyList() else com.betterappmafia.apophenia.core.score.ScoreTime.slots(score, part) }
            val from = p?.let { pl -> slots.lastOrNull { it.voice == 0 && it.start <= pl.position.toDouble() / pl.rate }?.bar } ?: 0
            val text = if (score == null || part == null) "" else
                (if (p != null) "▶ BAR ${from + 1} OF ${part.bars.size}" else "${part.name.uppercase()} · ${score.meter} · ${part.bars.size} BAR${if (part.bars.size == 1) "" else "S"}") + "\n" +
                    com.betterappmafia.apophenia.core.score.TabText.render(part, from, 2).joinToString("\n").ifEmpty {
                        part.bars.drop(from).take(4).mapNotNull { b -> b.voices.firstOrNull()?.mapNotNull { it.chord }?.joinToString(" ")?.takeIf { it.isNotEmpty() } }.joinToString(" | ") { ChordText.display(it) }
                    }
            Text(
                text.ifEmpty { "empty" },
                style = T.small.copy(fontSize = 11.sp, lineHeight = 15.sp, color = if (text.isEmpty()) C.faint else C.text),
                overflow = androidx.compose.ui.text.style.TextOverflow.Clip, softWrap = false,
            )
        }
        else -> {}
    }
}

/** What you can write (plan §2.2: lyrics or a note; C4 adds chord charts; §3.1 drum charts). */
val WRITABLE = listOf(
    Triple(ScrapFormat.LYRIC, "Lyrics", "WORDS · CHORDS OVER THEM"),
    Triple(ScrapFormat.TEXT, "Text note", "ANYTHING"),
    Triple(ScrapFormat.CHORD_CHART, "Chord chart", "BARS · ONE SLOT PER BEAT"),
    Triple(ScrapFormat.DRUM_CHART, "Drum chart", "DRUM TAB · A ROW PER DRUM"),
)

/** WRITE ▾: a menu of [WRITABLE], new scrap in [projectId] (null = the Library). */
@Composable
fun androidx.compose.foundation.layout.RowScope.WriteButton(model: AppModel, projectId: Long?) {
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.weight(1f)) {
        Row { BigButton("WRITE ▾", { menu = true }) }
        ConsoleMenu(menu, { menu = false }, "WRITE", WRITABLE.map { (f, label, note) -> MenuItem(label, note) { model.write(f, projectId) } } + listOf(
            MenuItem("Guitar tab", "STANDARD TUNING · TUNING ▾ CHANGES IT") { model.writeTab(bass = false, projectId) },
            MenuItem("Bass tab", "E STANDARD · TUNING ▾ CHANGES IT") { model.writeTab(bass = true, projectId) },
            MenuItem("Sheet music", "NOTATION · PIANO KEYS OR TAP THE STAFF") { model.writeSheet(projectId) },
            MenuItem("Piano roll", "SHEET MUSIC, FL STUDIO STYLE") { model.writeSheet(projectId, roll = true) },
        ))
    }
}

/**
 * PLAY ▾ on the Board (plan §4.4, "choose what plays"): every recording on it (its starred take, else its newest),
 * and its tab, sheet music and chord charts on the piano, in the order they sit on the Board; and the one that was
 * paused, to resume it.
 */
internal fun playAlongItems(model: AppModel, rows: List<BoardRow>, here: com.betterappmafia.apophenia.audio.Loaded?, projectId: Long, inStack: Boolean): List<MenuItem> = buildList {
    if (here != null && !here.playing) add(MenuItem("Resume ${here.title}", "▶") { model.app.audio.togglePlay() })
    // The project's stack plays in the stack player (its own transport).
    if (inStack) add(MenuItem("The stack", "IN THE PLAYER") { model.stacks.use(projectId) { model.app.audio.stackToggle() } })
    for (r in rows.sortedWith(compareBy({ it.y ?: 0 }, { it.x ?: 0 }))) {
        val s = r.scrap
        val title = displayTitle(s)
        when (s.format) {
            ScrapFormat.RECORDING -> add(MenuItem(title, "RECORDING") {
                model.app.io.launch {
                    val takes = model.app.db.takes().takesOf(s.id)
                    val take = takes.lastOrNull { it.starred } ?: takes.lastOrNull() ?: return@launch
                    model.play(s.id, take, title)
                }
            })
            ScrapFormat.TAB, ScrapFormat.SHEET -> {
                val score = runCatching { com.betterappmafia.apophenia.core.score.ScoreText.decode(r.shownBody) }.getOrNull()
                val part = score?.parts?.firstOrNull()
                if (score != null && part != null && part.bars.isNotEmpty()) add(MenuItem(title, "ON PIANO") { playScore(model, s.id, title, score, part) })
            }
            ScrapFormat.CHORD_CHART -> if (r.shownBody.isNotBlank()) add(MenuItem(title, "CHORDS ON PIANO") { playChordChart(model, s, r.shownBody) })
            else -> {}
        }
    }
    if (isEmpty()) add(MenuItem("Nothing on this Board plays yet", "", enabled = false) {})
}
