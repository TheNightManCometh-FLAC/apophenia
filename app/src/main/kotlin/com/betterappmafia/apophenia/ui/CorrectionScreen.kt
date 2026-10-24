package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.audio.Wav
import com.betterappmafia.apophenia.audio.WavWriter
import com.betterappmafia.apophenia.core.analysis.AnalysisText
import com.betterappmafia.apophenia.core.analysis.BasicPitch
import com.betterappmafia.apophenia.core.analysis.Grid
import com.betterappmafia.apophenia.core.analysis.NoteEdits
import com.betterappmafia.apophenia.audio.Piano
import com.betterappmafia.apophenia.core.analysis.TranscriptionText
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.tools.KeySpeller
import com.betterappmafia.apophenia.data.TakeTranscriptionEntity
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * The correction screen (plan §10.3): the take's waveform and its transcribed notes on one time line.
 * Tap a note to fix its pitch, timing or length; long-press an empty place to add one; play the
 * original, the transcription, or both. Quantising is a setting on top: the played notes stay, outlined,
 * under the quantised ones. Every change is saved to the transcription; the take is never touched.
 * Choosing the string comes with tab (M5).
 */
@Composable
fun CorrectionScreen(model: AppModel, takeId: Long) {
    val db = model.app.db
    val audio = model.app.audio
    val take by produceState<com.betterappmafia.apophenia.data.TakeEntity?>(null, takeId) { value = withContext(Dispatchers.IO) { db.takes().get(takeId) } }
    val stored by remember(takeId) { db.transcriptions().of(takeId) }.collectAsState(null)
    val t = take ?: return
    val st = stored ?: return
    val scrap by remember(t.scrapId) { db.scraps().row(t.scrapId) }.collectAsState(null)
    val s = scrap?.scrap ?: return
    val title = displayTitle(s)

    // Edits: the list being worked on, with undo and redo; saved as they happen.
    var notes by remember(takeId) { mutableStateOf(TranscriptionText.decode(st.body) ?: emptyList()) }
    var grid by remember(takeId) { mutableStateOf(TranscriptionText.grid(st.body)) }
    val undo = remember(takeId) { ArrayDeque<List<BasicPitch.TimedNote>>() }
    val redo = remember(takeId) { ArrayDeque<List<BasicPitch.TimedNote>>() }
    var selected by remember(takeId) { mutableIntStateOf(-1) }
    // Undo and redo stacks aren't state; reading [version] makes their buttons update.
    var version by remember { mutableIntStateOf(0) }
    fun save() = model.app.io.launch {
        db.transcriptions().put(TakeTranscriptionEntity(takeId, st.model, System.currentTimeMillis(), TranscriptionText.encode(st.model, notes, grid, edited = true)))
    }
    fun edit(next: List<BasicPitch.TimedNote>, select: Int = selected) {
        undo.addLast(notes); if (undo.size > 200) undo.removeFirst(); redo.clear()
        notes = next; selected = select.coerceIn(-1, next.size - 1); version++; save()
    }

    val seconds = t.durationFrames.toDouble() / t.sampleRate
    val peaks by produceState(FloatArray(0), takeId) { value = withContext(Dispatchers.IO) { runCatching { Wav.peaks(model.app.captures.fileOf(t), 600) }.getOrDefault(FloatArray(0)) } }
    // The analysis gives the beat a grid starts on: the click's first downbeat, or the first attack.
    val analysis by remember(takeId) { db.analysis().of(takeId) }.collectAsState(null)
    val origin = remember(analysis?.body) { AnalysisText.decode(analysis?.body)?.chords?.beats?.firstOrNull()?.time ?: 0.0 }
    val shown = remember(notes, grid) { grid?.apply(notes) ?: notes }
    val speller = remember(s.key) { KeySpeller(KeyMode.parseOrNull(s.key) ?: KeyMode.parseOrNull("C major")!!) }
    fun name(m: Int) = speller.spell(m).let { KeyMode.displayName(it.name) + it.octave }

    var pps by remember(takeId) { mutableFloatStateOf(0f) }   // pixels per second; 0 until laid out (fit the take)
    var scroll by remember(takeId) { mutableFloatStateOf(0f) }  // seconds at the left edge
    var playMenu by remember { mutableStateOf(false) }
    var quantMenu by remember { mutableStateOf(false) }
    val player by audio.player.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TitleBar {
            Sq("‹", { model.correcting = null }, modifier = Modifier.semantics { contentDescription = "Back" })
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Line1("CORRECT · $title · ${t.letter}", T.title)
                Text("${notes.size} NOTES · THE TAKE IS NEVER CHANGED", style = T.label.copy(fontSize = 8.sp))
            }
            Sq("UNDO", { if (undo.isNotEmpty()) { redo.addLast(notes); notes = undo.removeLast(); selected = -1; version++; save() } }, enabled = undo.isNotEmpty() && version >= 0)
            Sq("REDO", { if (redo.isNotEmpty()) { undo.addLast(notes); notes = redo.removeLast(); selected = -1; version++; save() } }, enabled = redo.isNotEmpty() && version >= 0)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box {
                Sq("PLAY ▾", { playMenu = true }, on = true)
                ConsoleMenu(playMenu, { playMenu = false }, "PLAY FROM THE LEFT EDGE", listOf(
                    MenuItem("The take", "ORIGINAL") { audio.load(s.id, t.id, "$title · ${t.letter}", model.app.captures.fileOf(t).absolutePath, true, (scroll * t.sampleRate).toLong().coerceAtLeast(0)) },
                    MenuItem("The transcription", if (grid != null) "QUANTISED" else "AS PLAYED") { playRender(model, t, shown, seconds, withTake = false, title = "Transcription · $title · ${t.letter}") },
                    MenuItem("Both together", "TAKE + NOTES") { playRender(model, t, shown, seconds, withTake = true, title = "Take + notes · $title · ${t.letter}") },
                ))
            }
            Box {
                Sq(grid?.let { g -> "QUANTISE ${Grid.DIVISIONS.first { it.first == g.division }.second} ▾" } ?: "QUANTISE ▾", { quantMenu = true }, on = grid != null)
                ConsoleMenu(quantMenu, { quantMenu = false }, "QUANTISE · A SETTING, THE PLAYED NOTES STAY", buildList {
                    add(MenuItem("Off", if (grid == null) "✓" else "AS PLAYED") { grid = null; save() })
                    val bpm = s.bpmLow
                    Grid.DIVISIONS.forEach { (d, label) ->
                        add(MenuItem(label, if (grid?.division == d) "✓" else if (bpm == null) "NEEDS A BPM" else "${bpm.toInt()} BPM", enabled = bpm != null) {
                            grid = Grid(bpm!!, d, origin); save()
                        })
                    }
                })
            }
            Sq("−", { pps = (pps / 1.5f).coerceAtLeast(4f) })
            Sq("+", { pps = (pps * 1.5f).coerceAtMost(2000f) })
        }

        // Waveform over the notes, one time line; drag sideways to move along it.
        val low = remember(notes) { (notes.minOfOrNull { it.midi } ?: 48) - 3 }
        val high = remember(notes) { maxOf((notes.maxOfOrNull { it.midi } ?: 60) + 3, low + 24) }
        val headSec = player?.let { p ->
            if (p.frames <= 0) null
            else if (p.takeId == t.id) p.position.toDouble() / p.rate
            else if (p.scrapId == REFERENCE_SCRAP && p.title.endsWith("· $title · ${t.letter}")) p.position.toDouble() / p.rate
            else null
        }
        Canvas(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)
                .semantics { contentDescription = "Waveform and ${notes.size} notes. Tap a note to select it; long-press an empty place to add one; drag to move along." }
                .pointerInput(takeId) {
                    detectHorizontalDragGestures { change, dx -> change.consume(); if (pps > 0) scroll = (scroll - dx / pps).coerceIn(0f, maxOf(0f, seconds.toFloat() - size.width / pps)) }
                }
                .pointerInput(takeId, notes, grid, low, high) {
                    val waveH = 64.dp.toPx()
                    fun pitchAt(y: Float): Int = high - ((y - waveH) / ((size.height - waveH) / (high - low + 1))).toInt()
                    fun timeAt(x: Float): Double = (scroll + x / pps).toDouble()
                    detectTapGestures(
                        onTap = { pos ->
                            if (pos.y < waveH) { audio.load(s.id, t.id, "$title · ${t.letter}", model.app.captures.fileOf(t).absolutePath, true, (timeAt(pos.x) * t.sampleRate).toLong()); return@detectTapGestures }
                            val tm = timeAt(pos.x); val m = pitchAt(pos.y)
                            selected = shown.indexOfFirst { tm >= it.start && tm <= maxOf(it.end, it.start + 8 / pps) && it.midi == m }
                        },
                        onLongPress = { pos ->
                            if (pos.y >= waveH) {
                                val len = grid?.step ?: 0.25
                                val start = grid?.snapTime(timeAt(pos.x)) ?: timeAt(pos.x)
                                val (list, i) = NoteEdits.add(notes, start, len, pitchAt(pos.y))
                                edit(list, i)
                            }
                        },
                    )
                },
        ) {
            if (pps <= 0f) pps = (size.width / seconds.toFloat()).coerceAtLeast(4f)
            val waveH = 64.dp.toPx()
            fun x(sec: Double) = ((sec - scroll) * pps).toFloat()
            // Waveform.
            if (peaks.isNotEmpty()) {
                val mid = waveH / 2
                val step = maxOf(1f, size.width / 300f)
                var px = 0f
                while (px < size.width) {
                    val sec = scroll + px / pps
                    val p = peaks[((sec / seconds) * peaks.size).toInt().coerceIn(0, peaks.size - 1)]
                    drawLine(C.wave, Offset(px, mid - p * mid), Offset(px, mid + p * mid), step)
                    px += step + 1
                }
            }
            drawLine(C.line, Offset(0f, waveH), Offset(size.width, waveH), 1f)
            // Rows: C of each octave marked; grid lines when quantising.
            val rowH = (size.height - waveH) / (high - low + 1)
            for (m in low..high) if (m % 12 == 0) {
                val y = waveH + (high - m) * rowH
                drawLine(C.line, Offset(0f, y + rowH), Offset(size.width, y + rowH), 1f)
            }
            grid?.let { g ->
                var gt = g.origin + Math.floor((scroll - g.origin) / g.step) * g.step
                val right = scroll + size.width / pps
                var k = 0
                while (gt <= right && k < 2000) { val gx = x(gt); drawLine(C.line.copy(alpha = 0.6f), Offset(gx, waveH), Offset(gx, size.height), 1f); gt += g.step; k++ }
            }
            // Played notes outlined under quantised ones; the selected note bright.
            if (grid != null) for (n in notes) {
                val y = waveH + (high - n.midi) * rowH
                drawRect(C.dim, Offset(x(n.start), y), Size(maxOf(2f, ((n.end - n.start) * pps).toFloat()), rowH - 1f), style = Stroke(1f))
            }
            shown.forEachIndexed { i, n ->
                val y = waveH + (high - n.midi) * rowH
                val col = if (i == selected) C.hi else C.acc.copy(alpha = (0.4f + 0.6f * n.amplitude.toFloat()).coerceIn(0f, 1f))
                drawRect(col, Offset(x(n.start), y), Size(maxOf(3f, ((n.end - n.start) * pps).toFloat()), maxOf(2f, rowH - 1f)))
            }
            headSec?.let { h -> val hx = x(h); if (hx in 0f..size.width) drawLine(C.hi, Offset(hx, 0f), Offset(hx, size.height), 2f) }
        }

        // The selected note.
        val sel = notes.getOrNull(selected)
        Column(Modifier.fillMaxWidth().drawBehindTop(C.acc).padding(horizontal = 8.dp, vertical = 6.dp)) {
            if (sel == null) {
                Text("Tap a note to fix it. Long-press an empty place to add one. Tap the waveform to play from there.", style = T.small.copy(lineHeight = 16.sp), modifier = Modifier.padding(6.dp))
                return@Column
            }
            val nudge = grid?.step ?: 0.01
            Text(
                String.format(Locale.ROOT, "%s · starts %.2f s · %.2f s long%s", name(sel.midi), sel.start, sel.end - sel.start, if (sel.bends != null) " · bends" else ""),
                style = T.body.copy(color = C.hi), modifier = Modifier.padding(4.dp),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Sq("▼", { edit(NoteEdits.transpose(notes, selected, -1)) })
                Sq("▲", { edit(NoteEdits.transpose(notes, selected, 1)) })
                Sq("OCT ▼", { edit(NoteEdits.transpose(notes, selected, -12)) })
                Sq("OCT ▲", { edit(NoteEdits.transpose(notes, selected, 12)) })
                Sq("◀", { edit(NoteEdits.move(notes, selected, -nudge)) })
                Sq("▶", { edit(NoteEdits.move(notes, selected, nudge)) })
                Sq("SHORTER", { edit(NoteEdits.resize(notes, selected, -nudge)) })
                Sq("LONGER", { edit(NoteEdits.resize(notes, selected, nudge)) })
                Sq("HEAR", { audio.load(s.id, t.id, "$title · ${t.letter}", model.app.captures.fileOf(t).absolutePath, true, (sel.start * t.sampleRate).toLong()) })
                Sq("DELETE", { edit(NoteEdits.delete(notes, selected), -1) })
            }
        }
    }
}

/** Renders the notes (and, for "both", the take under them) into the cache and plays it. */
private fun playRender(model: AppModel, t: com.betterappmafia.apophenia.data.TakeEntity, notes: List<BasicPitch.TimedNote>, seconds: Double, withTake: Boolean, title: String) {
    model.app.io.launch {
        val rate = model.app.audio.sampleRate
        val key = Integer.toHexString(notes.hashCode()) + if (withTake) "-take" else ""
        val dir = File(model.app.cacheDir, "transcriptions").apply { mkdirs() }
        val f = File(dir, "t${t.id}-$key-piano1.wav")
        if (f.length() <= 44) {
            val synth = Piano.render(model.app, Piano.ofTranscription(notes), rate, seconds)
            if (withTake) {
                Wav.readMono(model.app.captures.fileOf(t), rate, com.betterappmafia.apophenia.audio.Transcriber.MAX_SECONDS)?.first?.let { x ->
                    for (i in synth.indices) if (i < x.size) synth[i] = (synth[i] * 0.6f + x[i] * 0.8f).coerceIn(-1f, 1f)
                }
            }
            val part = File(dir, "${f.name}.part")
            WavWriter(part, rate).use { it.write(synth) }
            part.renameTo(f)
        }
        model.app.audio.load(REFERENCE_SCRAP, -1, title, f.absolutePath, play = true)
    }
}
