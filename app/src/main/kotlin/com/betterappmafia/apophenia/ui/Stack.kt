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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
 * The stack workspace (plan §5, C4): the layers, each with its mode, level, mute and solo; play;
 * the stack's ● bounce. Shown over the current mode so the top ● REC stays in reach for adding a part.
 */
@Composable
fun StackScreen(model: AppModel) {
    val sm = model.stacks
    val s = sm.loaded
    val layers by sm.layers.collectAsState()
    val st by model.app.audio.stack.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var flMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TitleBar {
            Sq("‹", { model.showStack = false }, modifier = Modifier.semantics { contentDescription = "Back" })
            Column(Modifier.weight(1f)) {
                Line1(sm.title().ifEmpty { "Stack" }, T.title)
                Text("${layers.size} LAYER${if (layers.size == 1) "" else "S"}", style = T.label)
            }
            // The stack's tempo and key (plan §5.3): from its first layer, editable; loop and one-shot layers conform to them.
            val tempo = sm.tempo(layers)
            val key = sm.key(layers)
            Sq(
                listOf(tempo?.let { "%.0f".format(it) } ?: "— BPM", key?.short ?: "NO KEY").joinToString(" · ") + " ▾",
                { model.sheet = Sheet.StackTempoKey }, on = s?.bpm != null || s?.key != null,
            )
            Box {
                Sq("⋯", { menu = true }, modifier = Modifier.semantics { contentDescription = "Stack menu" })
                ConsoleMenu(menu, { menu = false }, "STACK", listOf(
                    MenuItem("Add a layer", "FROM THE LIBRARY ›") { sm.pickLayers() },
                    MenuItem("Bounce the stack", "● NO MIC", enabled = layers.isNotEmpty()) { sm.bounce() },
                    MenuItem("Export for FL Studio", "ZIP ▾", enabled = layers.isNotEmpty() && !model.exporting) { flMenu = true },
                    MenuItem("Take it out of the player", "STAYS SAVED") { model.showStack = false; model.app.audio.unloadStack(); },
                ))
                ConsoleMenu(flMenu, { flMenu = false }, "FL STUDIO PACKAGE · WHICH AUDIO", flStudioItems { sm.flPackage(it) })
            }
        }
        // The transport line: tap to move the playhead.
        val loaded = st
        val frac = if (loaded != null && loaded.length > 0) loaded.position.toFloat() / loaded.length else 0f
        Box(
            Modifier.fillMaxWidth().height(34.dp).underline()
                .pointerInput(loaded?.length) { detectTapGestures { p -> model.app.audio.stackSeek(p.x / size.width) } }
                .drawBehind {
                    drawRect(C.panel2, Offset(0f, size.height / 2 - 2f), Size(size.width, 4f))
                    drawRect(C.acc, Offset(0f, size.height / 2 - 2f), Size(size.width * frac, 4f))
                }
                .semantics { contentDescription = "Stack position. Tap to move it." },
        ) {
            Text(
                if (loaded != null) "${clock(loaded.position, model.app.audio.sampleRate)} / ${clock(loaded.length, model.app.audio.sampleRate)}" else "",
                style = T.label, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            )
        }
        if (layers.isEmpty()) {
            Text(
                "No layers yet. Add recordings with ⋯ → Add a layer, or + STACK on a recording's window on the Board.",
                style = T.body.copy(lineHeight = 20.sp), modifier = Modifier.padding(14.dp),
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(layers, key = { it.layer.id }) { row -> LayerView(model, row, layers.indexOf(row) + 1) }
            item {
                Kv("TO ADD A PART") { Text("Play the stack and press ● REC at the top: you hear the stack, the mic records you, the take lands here as a new layer.", style = T.small.copy(lineHeight = 17.sp)) }
                Kv("● BELOW") { Text("Bounces the stack's own output, no mic, as a new recording.", style = T.small.copy(lineHeight = 17.sp)) }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            val playing = loaded?.playing == true
            BigButton(if (playing) "■ STOP" else "▶ PLAY", { model.app.audio.stackToggle() }, primary = true, enabled = layers.isNotEmpty())
            BigButton("● BOUNCE", { sm.bounce() }, enabled = layers.isNotEmpty())
        }
    }
}

@Composable
private fun LayerView(model: AppModel, row: LayerRow, n: Int) {
    val sm = model.stacks
    val l = row.layer
    var modeMenu by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    val name = row.title?.takeIf { it.isNotBlank() } ?: "Untitled"
    Column(Modifier.fillMaxWidth().underline().background(if (l.solo) C.selected else C.bg).padding(bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$n", style = T.label.copy(fontWeight = FontWeight.ExtraBold), modifier = Modifier.width(22.dp))
            Column(Modifier.weight(1f).clickable { model.open(l.scrapId) }) {
                Line1(name + (row.takeLetter?.let { " · $it" } ?: ""), T.hi.copy(fontSize = 13.sp, color = if (l.muted) C.dim else C.hi))
                if (row.audioPath == null) Text("no take left to play", style = T.small.copy(color = C.acc))
            }
            Box {
                Sq(modeLabel(l.mode) + " ▾", { modeMenu = true }, on = true)
                ConsoleMenu(modeMenu, { modeMenu = false }, "HOW IT PLAYS", listOf(
                    MenuItem("Loop", if (l.mode == LayerMode.LOOP) "✓ REPEATS" else "REPEATS") { sm.update(l.copy(mode = LayerMode.LOOP)) },
                    MenuItem("One-shot", if (l.mode == LayerMode.ONE_SHOT) "✓ ONCE" else "ONCE FROM ITS START") { sm.update(l.copy(mode = LayerMode.ONE_SHOT)) },
                    MenuItem("Free time", if (l.mode == LayerMode.FREE) "✓ OWN TIMING" else "OWN TIMING, NEVER CONFORMED") { sm.update(l.copy(mode = LayerMode.FREE)) },
                ))
            }
            Box {
                Sq("⋯", { more = true }, modifier = Modifier.semantics { contentDescription = "Layer menu" })
                ConsoleMenu(more, { more = false }, "LAYER $n", listOf(
                    MenuItem("Start at the top", "OFFSET 0") { sm.update(l.copy(startFrames = 0)) },
                    MenuItem("Remove from the stack", "STAYS IN THE LIBRARY") { sm.delete(row) },
                ))
            }
        }
        LayerWave(model, row)
        val conforms by sm.conforms.collectAsState()
        conforms[l.id]?.takeIf { it.state != StackModel.LayerConform.State.NONE }?.let { c ->
            val text = when (c.state) {
                StackModel.LayerConform.State.READY -> "CONFORMED " + c.amounts.label
                StackModel.LayerConform.State.RENDERING -> "RENDERING " + c.amounts.label + "…"
                StackModel.LayerConform.State.FAILED -> "TOO SHORT TO STRETCH · PLAYS AS RECORDED"
                StackModel.LayerConform.State.OFF -> "CONFORM OFF · WOULD BE " + c.amounts.label
                StackModel.LayerConform.State.NONE -> ""
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 36.dp).clickable { sm.update(l.copy(conform = !l.conform)) }.padding(horizontal = 12.dp)
                    .semantics { contentDescription = "$text. Tap to turn conform ${if (l.conform) "off" else "on"}." },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text, style = T.label.copy(color = if (l.conform) C.acc else C.dim), modifier = Modifier.weight(1f))
                Text(if (l.conform) "TAP: OFF" else "TAP: ON", style = T.label.copy(fontSize = 8.sp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Sq("−", { sm.update(l.copy(gainDb = l.gainDb - 1.5)) }, modifier = Modifier.semantics { contentDescription = "Quieter" })
            Text("%+.1f dB".format(l.gainDb), style = T.body.copy(fontWeight = FontWeight.Bold), modifier = Modifier.width(76.dp))
            Sq("+", { sm.update(l.copy(gainDb = l.gainDb + 1.5)) }, modifier = Modifier.semantics { contentDescription = "Louder" })
            Box(Modifier.weight(1f))
            Sq("M", { sm.update(l.copy(muted = !l.muted)) }, on = l.muted, modifier = Modifier.semantics { contentDescription = if (l.muted) "Unmute" else "Mute" })
            Sq("S", { sm.update(l.copy(solo = !l.solo)) }, on = l.solo, modifier = Modifier.semantics { contentDescription = if (l.solo) "Unsolo" else "Solo" })
        }
    }
}

/** The take's waveform with the part that plays lit, and where the layer starts on the stack's timeline. */
@Composable
private fun LayerWave(model: AppModel, row: LayerRow) {
    val path = row.audioPath ?: return
    val peaks by produceState(FloatArray(0), path) {
        value = withContext(Dispatchers.IO) { runCatching { Wav.peaks(File(model.app.audioDir, path), 80) }.getOrDefault(FloatArray(0)) }
    }
    val total = (row.durationFrames ?: 0L).coerceAtLeast(1)
    val from = row.layer.fromFrames.toFloat() / total
    val to = (row.layer.toFrames ?: total).toFloat() / total
    Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp, vertical = 4.dp)) {
        Waveform(peaks, Modifier.fillMaxSize(), color = if (row.layer.muted) C.panel2 else C.wave)
        Box(Modifier.fillMaxSize().drawBehind {
            drawRect(C.scrim, Offset(0f, 0f), Size(size.width * from, size.height))
            drawRect(C.scrim, Offset(size.width * to, 0f), Size(size.width * (1f - to), size.height))
        })
        if (row.layer.mode != LayerMode.LOOP && row.layer.startFrames != 0L) {
            Text(
                "STARTS AT ${clock(row.layer.startFrames.coerceAtLeast(0), model.app.audio.sampleRate)}",
                style = T.label.copy(fontSize = 8.sp), modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

/** The FL Studio package's audio choice (plan §11: "originals and/or conformed stems"); MIDI and the manifest always go in. */
fun flStudioItems(go: (com.betterappmafia.apophenia.export.FlStudioExport.Audio) -> Unit) = com.betterappmafia.apophenia.export.FlStudioExport.Audio.entries.map { a ->
    MenuItem(a.label, when (a) {
        com.betterappmafia.apophenia.export.FlStudioExport.Audio.BOTH -> "LARGEST"
        com.betterappmafia.apophenia.export.FlStudioExport.Audio.STEMS -> "LINED UP"
        com.betterappmafia.apophenia.export.FlStudioExport.Audio.TAKES -> "UNTOUCHED"
    }) { go(a) }
}
