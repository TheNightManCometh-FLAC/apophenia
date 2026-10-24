package com.betterappmafia.apophenia.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.core.score.ScoreText
import com.betterappmafia.apophenia.core.score.ScoreTime
import com.betterappmafia.apophenia.core.score.TabText
import com.betterappmafia.apophenia.core.sheet.ChordChart
import com.betterappmafia.apophenia.core.sheet.ChordText
import com.betterappmafia.apophenia.core.sheet.LyricSheet
import com.betterappmafia.apophenia.core.tools.DrumChart
import com.betterappmafia.apophenia.core.tools.DrumVoice
import com.betterappmafia.apophenia.data.BoardRow
import com.betterappmafia.apophenia.data.LyricSyncEntity
import com.betterappmafia.apophenia.data.ScrapFormat
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import kotlinx.coroutines.flow.first

/** One line of the stand sheet: the key that playback scrolls to, its text, and how it's drawn. */
private data class StandLine(val key: String, val text: String, val kind: Kind) {
    enum class Kind { TITLE, SECTION, CHORDS, WORDS, MONO, NOTE }
}

/**
 * Stand mode (plan §4.4: "large type, landscape, scroll locked to playback, one-thumb controls"): the Board's
 * readable blocks as one sheet in large type, top to bottom in the order they sit: lyrics with chords over the
 * words, chord charts four bars a line, tab and drum tab a line of bars at a time, the chord symbols of sheet music,
 * text notes. While something plays, the sheet scrolls to it and keeps the line being played a third of the way
 * down: the bar of a tab, chart or score being played, or the lyric line of a lyric synced to the take playing.
 * The controls sit in one column on the right for the thumb. The screen stays on.
 */
@Composable
fun StandMode(model: AppModel, projectId: Long, requestMic: (() -> Unit) -> Unit, exit: () -> Unit) {
    val mic by model.app.audio.mic.collectAsState()
    val recording = mic == com.betterappmafia.apophenia.audio.Mic.RECORDING
    val rows = remember(projectId) { model.app.db.projects().boardRows(projectId) }.collectAsState(emptyList()).value
    val inStack = remember(projectId) { model.app.db.stacks().scrapIdsInProjectStack(projectId) }.collectAsState(emptyList()).value.isNotEmpty()
    val activity = LocalContext.current as? Activity
    val view = LocalView.current
    DisposableEffect(Unit) {
        val before = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        view.keepScreenOn = true
        onDispose {
            activity?.requestedOrientation = before ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            view.keepScreenOn = false
        }
    }
    BackHandler { exit() }
    var size by remember { mutableStateOf(26) }
    var playMenu by remember { mutableStateOf(false) }
    val ordered = remember(rows) { rows.sortedWith(compareBy({ it.y ?: 0 }, { it.x ?: 0 })) }
    val lines = remember(ordered, model.chordDialect) { ordered.flatMap { standLines(it) } }
    val player by model.app.audio.player.collectAsState()
    val p = player?.takeIf { pl -> ordered.any { it.scrap.id == pl.scrapId } }
    // Lyric syncs for the take that's playing, by lyric id.
    val syncs = remember { mutableStateMapOf<Long, LyricSyncEntity>() }
    LaunchedEffect(p?.takeId, ordered) {
        syncs.clear()
        val take = p?.takeId?.takeIf { it >= 0 } ?: return@LaunchedEffect
        for (r in ordered.filter { it.scrap.format == ScrapFormat.LYRIC }) {
            model.app.db.lyricSyncs().of(r.scrap.id).first().firstOrNull { it.takeId == take }?.let { syncs[r.scrap.id] = it }
        }
    }
    val current: String? = p?.takeIf { it.playing }?.let { pl -> playingKey(ordered, pl, syncs) }
    val scroll = rememberScrollState()
    val ys = remember { HashMap<String, Float>() }
    var height by remember { mutableStateOf(0) }
    LaunchedEffect(current) {
        val y = current?.let { ys[it] } ?: return@LaunchedEffect
        scroll.animateScrollTo((y - height / 3f).toInt().coerceAtLeast(0))
    }

    Row(Modifier.fillMaxSize().background(C.bg)) {
        Column(
            Modifier.weight(1f).fillMaxHeight().onGloballyPositioned { height = it.size.height }.verticalScroll(scroll).padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            if (lines.isEmpty()) Text("Nothing on this Board to read yet.", style = T.body.copy(fontSize = size.sp, color = C.faint))
            for (l in lines) {
                val lit = l.key == current
                val base = when (l.kind) {
                    StandLine.Kind.TITLE -> T.label.copy(fontSize = (size * 0.5f).sp, color = C.acc)
                    StandLine.Kind.SECTION -> T.label.copy(fontSize = (size * 0.6f).sp, color = C.dim)
                    StandLine.Kind.CHORDS -> T.body.copy(fontFamily = FontFamily.Monospace, fontSize = size.sp, fontWeight = FontWeight.Bold, color = C.acc)
                    StandLine.Kind.WORDS -> T.body.copy(fontFamily = FontFamily.Monospace, fontSize = size.sp, color = C.hi)
                    StandLine.Kind.MONO -> T.body.copy(fontFamily = FontFamily.Monospace, fontSize = (size * 0.8f).sp, color = C.hi, lineHeight = (size * 0.95f).sp)
                    StandLine.Kind.NOTE -> T.body.copy(fontSize = (size * 0.8f).sp, color = C.text)
                }
                Text(
                    l.text, style = base, softWrap = l.kind == StandLine.Kind.NOTE,
                    modifier = Modifier.fillMaxWidth()
                        .then(if (lit) Modifier.background(C.acc.copy(alpha = 0.16f)) else Modifier)
                        .padding(top = if (l.kind == StandLine.Kind.TITLE) 18.dp else 2.dp)
                        .onGloballyPositioned { if (l.key !in ys || ys[l.key] != it.positionInParent().y) ys[l.key] = it.positionInParent().y },
                )
            }
            Box(Modifier.height(400.dp))
        }
        // One-thumb controls.
        Column(Modifier.width(92.dp).fillMaxHeight().background(C.panel), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                StandKey(if (p?.playing == true) "■" else "▶", if (p?.playing == true) "Stop" else "Play", C.acc) {
                    if (p?.playing == true) model.app.audio.togglePlay() else playMenu = true
                }
                ConsoleMenu(playMenu, { playMenu = false }, "PLAY ALONG", playAlongItems(model, rows, p, projectId, inStack))
            }
            StandKey("⟲", "Back to the start", C.hi) { if (p != null) model.app.audio.seek(0f, play = p.playing) }
            StandKey(if (p?.playback?.hasLoop == true) "⟳✓" else "⟳", if (p?.playback?.hasLoop == true) "Stop looping" else "Loop the line being played", if (p?.playback?.hasLoop == true) C.acc else C.hi) {
                val pl = p ?: return@StandKey
                if (pl.playback.hasLoop) model.app.audio.setPlayback(pl.playback.copy(loopA = -1, loopB = -1))
                else loopSpan(ordered, pl, syncs)?.let { (a, b) -> model.app.audio.setPlayback(pl.playback.copy(loopA = a, loopB = b)) }
                    ?: model.notice("Play something on the Board first; a recording loops a line of a lyric synced to it.")
            }
            StandKey(if (recording) "■" else "●", if (recording) "Stop recording" else "Record into this project over the playback", C.rec) {
                if (recording) model.stopRecording()
                else requestMic { model.startRecording(RecordTarget.Project(projectId, model.projectName(projectId) ?: "")) }
            }
            StandKey("A+", "Larger type", C.hi) { size = (size + 4).coerceAtMost(56) }
            StandKey("A−", "Smaller type", C.hi) { size = (size - 4).coerceAtLeast(14) }
            StandKey("✕", "Leave stand mode", C.dim) { exit() }
        }
    }
}

@Composable
private fun StandKey(label: String, description: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.width(76.dp).height(64.dp).background(C.bg).clickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(label, style = T.title.copy(fontSize = 28.sp, color = color), textAlign = TextAlign.Center) }
}

/** The key of the line being played: a bar of the score or chart playing, or the sung line of a synced lyric. */
private fun playingKey(rows: List<BoardRow>, p: com.betterappmafia.apophenia.audio.Loaded, syncs: Map<Long, LyricSyncEntity>): String? {
    val row = rows.firstOrNull { it.scrap.id == p.scrapId } ?: return null
    val now = p.position.toDouble() / p.rate
    when (row.scrap.format) {
        ScrapFormat.TAB, ScrapFormat.SHEET -> {
            val score = runCatching { ScoreText.decode(row.shownBody) }.getOrNull() ?: return null
            val part = score.parts.firstOrNull() ?: return null
            val bar = ScoreTime.slots(score, part).lastOrNull { it.voice == 0 && it.start <= now }?.bar ?: 0
            return "${row.scrap.id}:bar:${bar / BARS_A_LINE}"
        }
        ScrapFormat.CHORD_CHART -> {
            val chart = ChordChart.parse(row.shownBody)
            val tempo = com.betterappmafia.apophenia.core.rhythm.Tempo(row.scrap.bpmLow ?: DRUM_CHART_DEFAULT_BPM.toDouble())
            var t = 0.0; var bar = 0
            for ((i, b) in chart.bars.withIndex()) { bar = i; val len = tempo.secondsFor(b.meter.barLength); if (now < t + len) break; t += len }
            return "${row.scrap.id}:bar:${bar / BARS_A_LINE}"
        }
        else -> {}
    }
    // A recording: the first lyric synced to the take that's playing, at its sung line.
    for ((lyricId, sync) in syncs) {
        val line = LyricSyncEntity.lineAt(sync.lineFrames, p.position)
        if (line >= 0) return "$lyricId:line:$line"
    }
    return null
}

private const val BARS_A_LINE = 4

/** A Board block as stand lines. */
private fun standLines(row: BoardRow): List<StandLine> {
    val s = row.scrap
    val id = s.id
    val body = row.shownBody
    val title = StandLine("$id:title", displayTitle(s).uppercase(), StandLine.Kind.TITLE)
    val out = mutableListOf(title)
    when (s.format) {
        ScrapFormat.LYRIC -> {
            var words = 0
            for (line in LyricSheet.parse(body)) when (line) {
                is LyricSheet.Line.Section -> out += StandLine("$id:s:${out.size}", line.label.uppercase(), StandLine.Kind.SECTION)
                is LyricSheet.Line.Words -> {
                    val (top, bottom) = LyricSheet.chordsOverWords(line)
                    val key = "$id:line:${words++}"
                    if (top.isNotBlank()) out += StandLine("$key:c", ChordText.display(top), StandLine.Kind.CHORDS)
                    out += StandLine(key, bottom, StandLine.Kind.WORDS)
                }
                LyricSheet.Line.Blank -> out += StandLine("$id:b:${out.size}", " ", StandLine.Kind.NOTE)
            }
        }
        ScrapFormat.CHORD_CHART -> ChordChart.parse(body).bars.chunked(BARS_A_LINE).forEachIndexed { i, bars ->
            out += StandLine("$id:bar:$i", bars.joinToString(" | ", "| ", " |") { b -> b.beats.joinToString(" ") { ChordText.display(it) } }, StandLine.Kind.CHORDS)
        }
        ScrapFormat.TAB, ScrapFormat.SHEET -> {
            val score = runCatching { ScoreText.decode(body) }.getOrNull()
            val part = score?.parts?.firstOrNull()
            if (part != null) for (i in 0 until (part.bars.size + BARS_A_LINE - 1) / BARS_A_LINE) {
                val tab = TabText.render(part, i * BARS_A_LINE, BARS_A_LINE)
                val text = if (tab.isNotEmpty()) tab.joinToString("\n") else
                    "${i * BARS_A_LINE + 1}  " + part.bars.drop(i * BARS_A_LINE).take(BARS_A_LINE).joinToString(" | ", "| ", " |") { b ->
                        b.voices.firstOrNull()?.mapNotNull { it.chord }?.joinToString(" ") { ChordText.display(it) }?.ifEmpty { "–" } ?: "–"
                    }
                out += StandLine("$id:bar:$i", text, if (tab.isNotEmpty()) StandLine.Kind.MONO else StandLine.Kind.CHORDS)
            }
        }
        ScrapFormat.DRUM_CHART -> {
            val g = DrumChart.parse(body).grid
            out += StandLine("$id:drums", DrumVoice.entries.filter { it in g.rows }.joinToString("\n") { v -> "${v.tab} |${g.tab(v)}" }, StandLine.Kind.MONO)
        }
        ScrapFormat.TEXT -> if (body.isNotBlank()) out += StandLine("$id:text", body.trim(), StandLine.Kind.NOTE)
        ScrapFormat.RECORDING -> out[0] = title.copy(text = "● " + title.text)
        else -> {}
    }
    return out
}

/**
 * The frames to loop for the line being played: the four bars of a score or chart line in the pass playing now, or
 * the line of a lyric synced to the recording playing (to the next tapped line, or four seconds).
 */
private fun loopSpan(rows: List<BoardRow>, p: com.betterappmafia.apophenia.audio.Loaded, syncs: Map<Long, LyricSyncEntity>): Pair<Long, Long>? {
    val row = rows.firstOrNull { it.scrap.id == p.scrapId } ?: return null
    val now = p.position.toDouble() / p.rate
    fun f(sec: Double) = (sec * p.rate).toLong()
    when (row.scrap.format) {
        ScrapFormat.TAB, ScrapFormat.SHEET -> {
            val score = runCatching { ScoreText.decode(row.shownBody) }.getOrNull() ?: return null
            val part = score.parts.firstOrNull() ?: return null
            val starts = ScoreTime.slots(score, part).filter { it.voice == 0 && it.index == 0 }
            val here = starts.lastOrNull { it.start <= now } ?: return null
            val line = here.bar / BARS_A_LINE
            val first = starts.lastOrNull { it.bar == line * BARS_A_LINE && it.start <= now } ?: here
            val next = starts.firstOrNull { it.start > first.start && it.bar / BARS_A_LINE != line }?.start ?: ScoreTime.seconds(score, part)
            return f(first.start) to f(next)
        }
        ScrapFormat.CHORD_CHART -> {
            val chart = ChordChart.parse(row.shownBody)
            val tempo = com.betterappmafia.apophenia.core.rhythm.Tempo(row.scrap.bpmLow ?: DRUM_CHART_DEFAULT_BPM.toDouble())
            val times = chart.bars.runningFold(0.0) { t, b -> t + tempo.secondsFor(b.meter.barLength) }
            val bar = (times.indexOfLast { it <= now }).coerceIn(0, (chart.bars.size - 1).coerceAtLeast(0))
            val line = bar / BARS_A_LINE
            return f(times[line * BARS_A_LINE]) to f(times[minOf((line + 1) * BARS_A_LINE, chart.bars.size)])
        }
        else -> {}
    }
    for ((_, sync) in syncs) {
        val frames = sync.lineFrames
        val line = LyricSyncEntity.lineAt(frames, p.position)
        if (line < 0) continue
        val a = frames[line]!!
        val b = frames.drop(line + 1).firstOrNull { it != null } ?: (a + 4L * p.rate)
        return a to b
    }
    return null
}
