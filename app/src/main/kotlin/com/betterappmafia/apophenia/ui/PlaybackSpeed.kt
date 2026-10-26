package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.audio.Loaded
import com.betterappmafia.apophenia.audio.Playback
import com.betterappmafia.apophenia.audio.Wav
import com.betterappmafia.apophenia.data.TakeMarkEntity
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/**
 * Playback speed (plan §7, renamed from slow-down player by Christopher): the take in the player at
 * 0.25–2× without pitch change, pitch ±24 semitones and ±100 cents, A/B loop, step-up loops, a count-in
 * before each pass, and marks. The take itself is never changed; marks are stored with it.
 */
@Composable
fun PlaybackSpeedScreen(model: AppModel) {
    val audio = model.app.audio
    val player by audio.player.collectAsState()
    val p = player?.takeIf { it.scrapId != REFERENCE_SCRAP && it.takeId > 0 }
    Column(Modifier.fillMaxSize()) {
        TitleBar {
            Sq("‹", { model.tools.screen = null })
            Text("  PLAYBACK SPEED", style = T.title, modifier = Modifier.weight(1f))
            if (p != null) Text(p.playback.label, style = T.label)
        }
        if (p == null) {
            Text(
                (player?.let { "The player has \"${it.title}\", which isn't a recording: this works on recordings and imported songs only. " } ?: "") +
                    "Play a recording first (▶ on any row in the Library), or import a song from the phone: it lands in the Library, then ▶ it. It stays in the player while you come back here.",
                style = T.body.copy(lineHeight = 20.sp), modifier = Modifier.padding(14.dp),
            )
            Row {
                BigButton("LIBRARY", { model.go(Mode.LIBRARY) })
                BigButton("IMPORT A SONG", { model.startImport() }, primary = true)
            }
            return@Column
        }
        SpeedControls(model, p)
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.SpeedControls(model: AppModel, p: Loaded) {
    val audio = model.app.audio
    val pb = p.playback
    val rate = audio.sampleRate
    val marks by remember(p.takeId) { model.app.db.marks().of(p.takeId) }.collectAsState(emptyList())
    val bpm by produceState<Double?>(null, p.scrapId) { value = model.app.db.scraps().get(p.scrapId)?.bpmLow }
    val peaks by produceState(FloatArray(0), p.path) {
        value = withContext(Dispatchers.IO) { runCatching { Wav.peaks(File(p.path), 160) }.getOrDefault(FloatArray(0)) }
    }
    fun set(n: Playback) = audio.setPlayback(n)
    fun seekTo(frame: Long) = audio.seek(frame.toFloat() / p.frames.coerceAtLeast(1), play = p.playing)
    val frac = { f: Long -> (f.toFloat() / p.frames.coerceAtLeast(1)).coerceIn(0f, 1f) }

    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Text(p.title.uppercase(), style = T.label, modifier = Modifier.padding(start = 14.dp, top = 12.dp))
        // Waveform: the loop shaded, marks as lines, tap to play from there.
        Box(
            Modifier.fillMaxWidth().height(110.dp).padding(horizontal = 12.dp, vertical = 8.dp)
                .pointerInput(p.path, p.frames) { detectTapGestures { o -> audio.seek(o.x / size.width, play = p.playing) } }
                .semantics { contentDescription = "Waveform. Tap to play from there." },
        ) {
            if (pb.loopA >= 0) Canvas(Modifier.fillMaxSize()) {
                val a = frac(pb.loopA) * size.width
                val b = if (pb.loopB > pb.loopA) frac(pb.loopB) * size.width else a + 2f
                drawRect(C.selected, Offset(a, 0f), Size(b - a, size.height))
            }
            Waveform(peaks, Modifier.fillMaxSize(), progress = frac(p.position))
            Canvas(Modifier.fillMaxSize()) {
                marks.forEach { m ->
                    val x = frac(m.frame) * size.width
                    drawLine(C.acc, Offset(x, 0f), Offset(x, size.height * 0.3f), 2f)
                }
                if (pb.loopA >= 0) drawLine(C.listening, Offset(frac(pb.loopA) * size.width, 0f), Offset(frac(pb.loopA) * size.width, size.height), 2f)
                if (pb.hasLoop) drawLine(C.listening, Offset(frac(pb.loopB) * size.width, 0f), Offset(frac(pb.loopB) * size.width, size.height), 2f)
            }
        }
        Text(
            "${clock(p.position, rate)} / ${clock(p.frames, rate)}" +
                (if (pb.hasLoop) " · LOOP ${clock(pb.loopA, rate)}–${clock(pb.loopB, rate)} · PASS ${p.passes + 1}" else "") +
                (if (pb.stepBy != 0.0) " · NOW %.2f×".format(java.util.Locale.ROOT, p.speedNow) else ""),
            style = T.label, modifier = Modifier.padding(horizontal = 14.dp),
        )
        // Transport.
        Row(Modifier.fillMaxWidth().height(60.dp).padding(top = 6.dp).underline().drawBehindTop(C.line)) {
            Key("−5 s", Modifier.weight(1f)) { seekTo((p.position - 5 * rate).coerceAtLeast(0)) }
            Box(
                Modifier.weight(1.4f).fillMaxHeight().background(C.selected).clickable(role = Role.Button) { audio.togglePlay() }
                    .semantics { contentDescription = if (p.playing) "Stop" else "Play" },
                contentAlignment = Alignment.Center,
            ) { Text(if (p.playing) "■" else "▶", style = T.hi.copy(fontSize = 22.sp, color = C.acc)) }
            Key("+5 s", Modifier.weight(1f)) { seekTo((p.position + 5 * rate).coerceAtMost(p.frames - 1)) }
        }
        // Speed: a menu plus fine steps.
        Row(Modifier.fillMaxWidth().height(56.dp).underline(), verticalAlignment = Alignment.CenterVertically) {
            Key("−0.05", Modifier.width(72.dp)) { set(pb.copy(speed = (pb.speed - 0.05).coerceIn(0.25, 2.0).r2())) }
            Box(Modifier.weight(1f)) {
                var open by remember { mutableStateOf(false) }
                Column(Modifier.fillMaxWidth().clickable { open = true }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.2f×".format(java.util.Locale.ROOT, pb.speed), style = T.hi.copy(fontSize = 26.sp))
                    Text("SPEED ▾ · PITCH KEPT", style = T.label)
                }
                ConsoleMenu(open, { open = false }, "SPEED", Playback.SPEEDS.map { s ->
                    MenuItem("%.2f×".format(java.util.Locale.ROOT, s), if (abs(s - pb.speed) < 1e-6) "✓" else "${(s * 100).toInt()}%") { set(pb.copy(speed = s)) }
                })
            }
            Key("+0.05", Modifier.width(72.dp)) { set(pb.copy(speed = (pb.speed + 0.05).coerceIn(0.25, 2.0).r2())) }
        }
        PbMenuRow("PITCH", if (pb.semitones == 0) "0 st" else "${if (pb.semitones > 0) "+" else "−"}${abs(pb.semitones)} st", "SEMITONES", (24 downTo -24).map { s ->
            MenuItem(if (s == 0) "0" else "${if (s > 0) "+" else "−"}${abs(s)} st", if (s == pb.semitones) "✓" else if (abs(s) == 12) "OCTAVE" else "") { set(pb.copy(semitones = s)) }
        })
        PbMenuRow("FINE TUNE", "${if (pb.cents > 0) "+" else if (pb.cents < 0) "−" else ""}${abs(pb.cents)} ¢", "CENTS", (100 downTo -100 step 5).map { c ->
            MenuItem("${if (c > 0) "+" else if (c < 0) "−" else ""}${abs(c)} ¢", if (c == pb.cents) "✓" else "") { set(pb.copy(cents = c)) }
        })
        // Loop.
        Row(Modifier.fillMaxWidth().height(56.dp).underline().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("LOOP", style = T.label, modifier = Modifier.weight(1f))
            Sq("SET A", { set(pb.copy(loopA = p.position, loopB = if (pb.loopB > p.position) pb.loopB else -1)) }, on = pb.loopA >= 0)
            Sq("SET B", { if (pb.loopA >= 0 && p.position > pb.loopA) set(pb.copy(loopB = p.position)) }, on = pb.hasLoop, enabled = pb.loopA >= 0 && p.position > pb.loopA)
            Sq("CLEAR", { set(pb.copy(loopA = -1, loopB = -1, stepBy = 0.0, countInBeats = 0)) }, enabled = pb.loopA >= 0)
        }
        PbMenuRow("STEP-UP", if (pb.stepBy == 0.0) "Off" else "+%.2f a pass to %.2f×".format(java.util.Locale.ROOT, pb.stepBy, pb.stepTo), "SPEED RISES EACH PASS",
            listOf(MenuItem("Off", if (pb.stepBy == 0.0) "✓" else "") { set(pb.copy(stepBy = 0.0)) }) + STEPS.map { (by, to) ->
                MenuItem("+%.2f each pass, up to %.2f×".format(java.util.Locale.ROOT, by, to), if (pb.stepBy == by && pb.stepTo == to) "✓" else "") { set(pb.copy(stepBy = by, stepTo = to)) }
            }, enabled = pb.hasLoop)
        val b = bpm
        PbMenuRow("COUNT-IN EACH PASS", if (pb.countInBeats == 0) "Off" else "${pb.countInBeats} beats", if (b != null) "AT ${b.toInt()} BPM × SPEED" else "",
            listOf(0, 1, 2, 4, 8).map { n ->
                MenuItem(if (n == 0) "Off" else "$n beat${if (n > 1) "s" else ""}", if (n == pb.countInBeats) "✓" else "") {
                    set(pb.copy(countInBeats = n, beatFrames = if (b != null && b > 0) 60.0 / b * rate else 0.0))
                }
            }, enabled = pb.hasLoop && b != null)
        if (b == null) Text("Count-in needs the recording's BPM: set it on the recording (▸ its row in the Library).", style = T.small.copy(lineHeight = 16.sp), modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
        // Marks.
        Row(Modifier.fillMaxWidth().height(52.dp).underline().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("MARKS", style = T.label, modifier = Modifier.weight(1f))
            Sq("+ MARK HERE", {
                model.app.io.launch { model.app.db.marks().add(TakeMarkEntity(takeId = p.takeId, frame = p.position, label = "Mark ${marks.count { !it.isLoop } + 1}", createdAt = System.currentTimeMillis())) }
            })
            Sq("SAVE LOOP", {
                model.app.io.launch { model.app.db.marks().add(TakeMarkEntity(takeId = p.takeId, frame = pb.loopA, endFrame = pb.loopB, label = "Loop ${marks.count { it.isLoop } + 1}", createdAt = System.currentTimeMillis())) }
            }, enabled = pb.hasLoop)
        }
        marks.forEach { m ->
            Row(Modifier.fillMaxWidth().height(48.dp).underline(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).fillMaxHeight().clickable {
                        if (m.isLoop) set(pb.copy(loopA = m.frame, loopB = m.endFrame!!)) else seekTo(m.frame)
                    }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(m.label.ifEmpty { "Mark" }, style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                    Text(if (m.isLoop) "${clock(m.frame, rate)}–${clock(m.endFrame!!, rate)} · LOOP IT" else "${clock(m.frame, rate)} · GO", style = T.label)
                }
                Box(Modifier.width(52.dp).fillMaxHeight().clickable { model.app.io.launch { model.app.db.marks().delete(m.id) } }.semantics { contentDescription = "Delete ${m.label}" }, contentAlignment = Alignment.Center) {
                    Text("✕", style = T.body.copy(color = C.dim))
                }
            }
        }
        Text(
            "The recording is never changed: speed, pitch and loops apply while it plays, and marks are kept with the take.",
            style = T.small.copy(lineHeight = 16.sp), modifier = Modifier.padding(14.dp),
        )
    }
}

private val STEPS = listOf(0.05 to 1.0, 0.1 to 1.0, 0.05 to 1.25)

private fun Double.r2() = Math.round(this * 100) / 100.0

@Composable
private fun Key(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.fillMaxHeight().clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, style = T.button.copy(fontSize = 13.sp))
    }
}

@Composable
private fun PbMenuRow(label: String, value: String, title: String, items: List<MenuItem>, enabled: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().height(48.dp).underline().clickable(enabled = enabled) { open = true }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = T.label, modifier = Modifier.weight(1f))
            Text("$value ▾", style = T.body.copy(color = if (enabled) C.hi else C.faint, fontWeight = FontWeight.Bold))
        }
        ConsoleMenu(open, { open = false }, title.ifEmpty { null }, items)
    }
}

