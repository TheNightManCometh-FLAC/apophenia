package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.key
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.audio.Wav
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.data.LayerMode
import com.betterappmafia.apophenia.data.LayerRow
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun modeLabel(m: LayerMode) = when (m) { LayerMode.LOOP -> "LOOP"; LayerMode.ONE_SHOT -> "ONE-SHOT"; LayerMode.FREE -> "FREE TIME" }

/**
 * The stack workspace (plan §5) as loose squares (his pick of three, 2 Nov 2026, after Splice Stacks as the reference
 * and "make it in the style of our app"): each layer a square still of its take painted in its own key's colour, the
 * part not yet played dimmed, the caption underneath (tag or stem · how it plays, title, take and conform). Tap a still
 * to mute it, hold to solo. Key, tempo and export along the top; add and record a part, then mixer, undo, play, redo
 * and bounce along the bottom. No shuffle or swap: the app doesn't suggest material (plan §5.2).
 */
@Composable
fun StackScreen(model: AppModel, requestMic: (() -> Unit) -> Unit) {
    val sm = model.stacks
    val s = sm.loaded
    val layers by sm.layers.collectAsState()
    val st by model.app.audio.stack.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var flMenu by remember { mutableStateOf(false) }
    var mixer by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TitleBar {
            Sq("‹", { model.showStack = false }, modifier = Modifier.semantics { contentDescription = "Back" })
            Column(Modifier.weight(1f)) {
                DisplayTitle(sm.title().ifEmpty { "Stack" })
                Text("${layers.size} LAYER${if (layers.size == 1) "" else "S"}", style = T.label)
            }
            Box {
                Sq("⋯", { menu = true }, modifier = Modifier.semantics { contentDescription = "Stack menu" })
                ConsoleMenu(menu, { menu = false }, "STACK", listOf(
                    MenuItem("Add a layer", "FROM THE LIBRARY ›") { sm.pickLayers() },
                    MenuItem("Take it out of the player", "STAYS SAVED") { model.showStack = false; model.app.audio.unloadStack() },
                ))
            }
        }
        // The stack's tempo and key (plan §5.3): from its first layer, editable; loop and one-shot layers conform to them.
        val tempo = sm.tempo(layers)
        val key = sm.key(layers)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Sq((key?.display ?: "No key").uppercase() + " ▾", { model.sheet = Sheet.StackTempoKey }, modifier = Modifier.weight(1.3f), on = s?.key != null)
            Sq((tempo?.let { "%.0f BPM".format(it) } ?: "— BPM") + " ▾", { model.sheet = Sheet.StackTempoKey }, modifier = Modifier.weight(1f), on = s?.bpm != null)
            Box(Modifier.weight(1f)) {
                Sq("EXPORT ▾", { flMenu = true }, modifier = Modifier.fillMaxWidth(), enabled = layers.isNotEmpty() && !model.exporting)
                ConsoleMenu(flMenu, { flMenu = false }, "FL STUDIO PACKAGE · WHICH AUDIO", flStudioItems { sm.flPackage(it) })
            }
        }
        val loaded = st
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            if (layers.isEmpty()) Text(
                "No layers yet. ＋ FROM LIBRARY adds recordings; ● RECORD A PART records one over the stack; + STACK on a recording's window on the Board adds it too.",
                style = T.body.copy(lineHeight = 20.sp), modifier = Modifier.padding(vertical = 14.dp),
            )
            val anySolo = layers.any { it.layer.solo }
            // Two staggered columns, like the Board's loose squares.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (col in 0..1) Column(Modifier.weight(1f).padding(top = if (col == 1) 44.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    layers.forEachIndexed { i, row ->
                        if (i % 2 == col) key(row.layer.id) { LayerSquare(model, row, i + 1, mixer, anySolo, loaded, tempo) }
                    }
                }
            }
            Gap(16.dp)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Sq("＋ FROM LIBRARY", { sm.pickLayers() }, modifier = Modifier.weight(1f))
            Sq("● RECORD A PART", {
                // Recording while the stack plays makes a new layer (plan §5.4); start it playing first.
                requestMic { if (loaded?.playing != true) model.app.audio.stackToggle(); model.startRecording() }
            }, modifier = Modifier.weight(1f), enabled = layers.isNotEmpty())
        }
        Row(Modifier.fillMaxWidth().height(64.dp).background(C.panel).drawBehindTop(C.line)) {
            @Composable fun cell(glyph: String, label: String, on: Boolean = false, enabled: Boolean = true, color: androidx.compose.ui.graphics.Color = C.hi, go: () -> Unit) =
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable(enabled = enabled, role = Role.Button, onClick = go).semantics { contentDescription = label },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Text(glyph, style = T.hi.copy(fontSize = 20.sp, color = if (!enabled) C.faint else if (on) C.acc else color))
                    Text(label, style = T.label.copy(fontSize = 8.sp, color = if (on) C.acc else C.dim))
                }
            val playing = loaded?.playing == true
            cell("☰", "MIXER", on = mixer) { mixer = !mixer }
            cell("↶", "UNDO", enabled = sm.canUndo) { sm.undo() }
            cell(if (playing) "■" else "▶", if (playing) "STOP" else "PLAY", enabled = layers.isNotEmpty(), color = C.acc) { model.app.audio.stackToggle() }
            cell("↷", "REDO", enabled = sm.canRedo) { sm.redo() }
            cell("●", "BOUNCE", enabled = layers.isNotEmpty(), color = C.rec) { sm.bounce() }
        }
    }
}

/**
 * How far through itself a layer is at the stack's [position]: a loop wraps at its loop length (stretched by conform
 * to the stack tempo), a one-shot or free-time layer runs once from where it starts. [Claude] Drawn from the stack
 * clock and the take's own length, so it is close, not sample-exact.
 */
private fun layerProgress(row: LayerRow, position: Long, stackTempo: Double?): Float {
    val l = row.layer
    val len = ((l.toFrames ?: row.durationFrames ?: 0L) - l.fromFrames).coerceAtLeast(1)
    val own = row.bpmLow?.let { lo -> (lo + (row.bpmHigh ?: lo)) / 2 }
    val stretch = if (l.mode != LayerMode.FREE && l.conform && own != null && stackTempo != null && stackTempo > 0) own / stackTempo else 1.0
    val span = (len * stretch).coerceAtLeast(1.0)
    val t = (position - l.startFrames).toDouble()
    return when {
        l.mode == LayerMode.LOOP -> (((t % span) + span) % span / span).toFloat()
        t <= 0 -> 0f
        else -> (t / span).toFloat().coerceIn(0f, 1f)
    }
}

/** One layer: its still (tap mutes, hold solos; in MIXER a fader), then the caption. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LayerSquare(model: AppModel, row: LayerRow, n: Int, mixer: Boolean, anySolo: Boolean, st: com.betterappmafia.apophenia.audio.StackLoaded?, stackTempo: Double?) {
    val sm = model.stacks
    val l = row.layer
    var more by remember { mutableStateOf(false) }
    val path = row.audioPath
    val peaks by produceState(FloatArray(0), path) {
        value = if (path == null) FloatArray(0) else withContext(Dispatchers.IO) { runCatching { Wav.peaks(File(model.app.audioDir, path), 96) }.getOrDefault(FloatArray(0)) }
    }
    val k = remember(row.key) { KeyMode.parseOrNull(row.key) }
    val frame = remember(path, peaks) {
        if (peaks.isEmpty()) null else com.betterappmafia.apophenia.core.art.FilmFrame.Input(
            com.betterappmafia.apophenia.core.art.FilmFrame.Kind.TAKE, (path ?: "").hashCode().toLong(), k?.tonic?.pitchClass, k?.let { isMinor(it) } ?: false, row.bpmLow, listOf(peaks),
        )
    }
    val audible = !l.muted && (!anySolo || l.solo)
    val label = (row.stem ?: row.firstTag ?: "Layer $n").uppercase()
    val name = row.title?.takeIf { it.isNotBlank() } ?: "Untitled"
    Column {
        var dragGain by remember(l.gainDb) { mutableStateOf(l.gainDb) }
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).background(STILL_BLACK)
                .then(if (l.solo) Modifier.border(2.dp, C.acc) else Modifier)
                .combinedClickable(enabled = !mixer, onClick = { sm.update(l.copy(muted = !l.muted)) }, onLongClick = { sm.update(l.copy(solo = !l.solo)) })
                .semantics { contentDescription = "$name. ${if (l.muted) "Muted" else "Playing"}. Tap to ${if (l.muted) "unmute" else "mute"}, hold to ${if (l.solo) "unsolo" else "solo"}." },
        ) {
            FilmBackdrop(frame, Modifier.fillMaxSize())
            if (!audible) Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xB3000000)))
            if (mixer) {
                // A hairline fader on the still: drag up and down, −24 to +6 dB; written once when the finger lifts.
                Box(Modifier.fillMaxSize().pointerInput(l.id) {
                    detectVerticalDragGestures(
                        onDragEnd = { sm.update(l.copy(gainDb = dragGain)) },
                        onVerticalDrag = { _, dy -> dragGain = (dragGain - dy / size.height * 30.0).coerceIn(-24.0, 6.0) },
                    )
                }.drawBehind {
                    val x = size.width - 14.dp.toPx()
                    val top = 12.dp.toPx(); val bottom = size.height - 12.dp.toPx()
                    drawLine(FILM_CREAM.copy(alpha = 0.45f), Offset(x, top), Offset(x, bottom), 1.dp.toPx())
                    val y = bottom - ((dragGain + 24.0) / 30.0).toFloat() * (bottom - top)
                    drawLine(FILM_CREAM, Offset(x - 9.dp.toPx(), y), Offset(x + 9.dp.toPx(), y), 2.dp.toPx())
                })
            } else if (st != null && audible) {
                val f = layerProgress(row, st.position, stackTempo)
                Box(Modifier.fillMaxSize().drawBehind { drawRect(androidx.compose.ui.graphics.Color(0x8C000000), Offset(size.width * f, 0f), Size(size.width * (1f - f), size.height)) })
            }
            if (row.audioPath == null) Text("NO TAKE LEFT TO PLAY", style = T.label.copy(color = C.acc), modifier = Modifier.align(Alignment.Center))
        }
        Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("$label · ${modeLabel(l.mode)}" + (if (l.solo) " · SOLO" else "") + (if (l.muted) " · MUTED" else ""), style = T.label.copy(color = C.acc), maxLines = 2)
                Text(name, style = T.display.copy(fontSize = 18.sp, lineHeight = 20.sp), maxLines = 2, modifier = Modifier.clickable { model.open(l.scrapId) })
                val conforms by sm.conforms.collectAsState()
                val c = conforms[l.id]
                val conf = when (c?.state) {
                    StackModel.LayerConform.State.READY -> c.amounts.label
                    StackModel.LayerConform.State.RENDERING -> "rendering " + c.amounts.label + "…"
                    StackModel.LayerConform.State.FAILED -> "too short to stretch"
                    StackModel.LayerConform.State.OFF -> "conform off"
                    else -> if (l.mode == LayerMode.FREE) "never conformed" else ""
                }
                Text(
                    if (mixer) "%+.1f dB".format(dragGain) else listOfNotNull(row.takeLetter?.let { "TAKE $it" }, conf.takeIf { it.isNotEmpty() }).joinToString(" · "),
                    style = T.label.copy(fontSize = 9.sp), maxLines = 2,
                )
            }
            Box {
                Text("⋯", style = T.hi.copy(color = C.dim), modifier = Modifier.clickable(role = Role.Button) { more = true }.padding(horizontal = 6.dp).semantics { contentDescription = "Layer menu" })
                ConsoleMenu(more, { more = false }, "LAYER $n", listOf(
                    MenuItem("Loop", if (l.mode == LayerMode.LOOP) "✓ REPEATS" else "REPEATS") { sm.update(l.copy(mode = LayerMode.LOOP)) },
                    MenuItem("One-shot", if (l.mode == LayerMode.ONE_SHOT) "✓ ONCE" else "ONCE FROM ITS START") { sm.update(l.copy(mode = LayerMode.ONE_SHOT)) },
                    MenuItem("Free time", if (l.mode == LayerMode.FREE) "✓ OWN TIMING" else "OWN TIMING, NEVER CONFORMED") { sm.update(l.copy(mode = LayerMode.FREE)) },
                    MenuItem(if (l.conform) "Conform off" else "Conform on", if (l.conform) "PLAY AS RECORDED" else "TO THE STACK'S TEMPO AND KEY", enabled = l.mode != LayerMode.FREE) { sm.update(l.copy(conform = !l.conform)) },
                    MenuItem("Start at the top", "OFFSET 0") { sm.update(l.copy(startFrames = 0)) },
                    MenuItem("Open the recording", "›") { model.open(l.scrapId) },
                    MenuItem("Remove from the stack", "STAYS IN THE LIBRARY") { sm.delete(row) },
                ))
            }
        }
    }
}

private val STILL_BLACK = androidx.compose.ui.graphics.Color(0xFF0A0A0B)
private val FILM_CREAM = androidx.compose.ui.graphics.Color(0xFFEDE6D8)

/** The FL Studio package's audio choice (plan §11: "originals and/or conformed stems"); MIDI and the manifest always go in. */
fun flStudioItems(go: (com.betterappmafia.apophenia.export.FlStudioExport.Audio) -> Unit) = com.betterappmafia.apophenia.export.FlStudioExport.Audio.entries.map { a ->
    MenuItem(a.label, when (a) {
        com.betterappmafia.apophenia.export.FlStudioExport.Audio.BOTH -> "LARGEST"
        com.betterappmafia.apophenia.export.FlStudioExport.Audio.STEMS -> "LINED UP"
        com.betterappmafia.apophenia.export.FlStudioExport.Audio.TAKES -> "UNTOUCHED"
    }) { go(a) }
}
