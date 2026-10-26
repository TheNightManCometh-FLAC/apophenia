package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.instrument.Tunings
import com.betterappmafia.apophenia.core.tools.IsolatorSettings
import com.betterappmafia.apophenia.core.tools.NoteFinder
import com.betterappmafia.apophenia.core.tools.PanMode
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The isolator (plan §7): stereo position, tone ⇄ hit, band, presets as starting points, bypass A/B,
 * and the note finder under it. It works on the take in the player; the take is never changed.
 */
@Composable
fun IsolatorScreen(model: AppModel) {
    val tm = model.tools
    val audio = model.app.audio
    val player by audio.player.collectAsState()
    val p = player?.takeIf { it.scrapId != REFERENCE_SCRAP && it.takeId > 0 }
    Column(Modifier.fillMaxSize()) {
        TitleBar {
            Sq("‹", { tm.screen = null })
            Text("  ISOLATOR", style = T.title, modifier = Modifier.weight(1f))
            if (p != null) Text(p.playback.label, style = T.label)
        }
        if (p == null) {
            Text(
                (player?.let { "The player has \"${it.title}\", which isn't a recording: this works on recordings and imported songs only. " } ?: "") +
                    "Play a recording first (▶ on any row in the Library), or import a song. Stereo songs can be split by where things are panned; your own recordings are mono, so tone ⇄ hit and the band work on them.",
                style = T.body.copy(lineHeight = 20.sp), modifier = Modifier.padding(14.dp),
            )
            Row {
                BigButton("LIBRARY", { model.go(Mode.LIBRARY) })
                BigButton("IMPORT A SONG", { model.startImport() }, primary = true)
            }
            return@Column
        }
        val s = tm.isolator
        val on = p.playback.isolate
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(p.title.uppercase(), style = T.label, modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 6.dp))
            // Bypass A/B: the same take with and without the isolator, crossfaded, from where it is.
            Row(Modifier.fillMaxWidth().height(60.dp).underline().drawBehindTop(C.line)) {
                Box(
                    Modifier.weight(1f).height(60.dp).background(if (on) C.selected else C.panel)
                        .clickable(role = Role.Switch) { audio.setPlayback(p.playback.copy(isolate = !on)) }
                        .semantics { contentDescription = if (on) "Isolator on. Tap to bypass." else "Bypassed. Tap to isolate." },
                    contentAlignment = Alignment.Center,
                ) { Text(if (on) "● ISOLATING · TAP TO BYPASS" else "○ BYPASSED · TAP TO ISOLATE", style = T.button.copy(color = if (on) C.acc else C.hi)) }
                Box(Modifier.height(60.dp).clickable { audio.togglePlay() }.padding(horizontal = 22.dp), contentAlignment = Alignment.Center) {
                    Text(if (p.playing) "■" else "▶", style = T.hi.copy(fontSize = 20.sp, color = C.acc))
                }
            }
            var presets by remember { mutableStateOf(false) }
            Box {
                KvText("PRESET", "Start from… ▾") { presets = true }
                ConsoleMenu(presets, { presets = false }, "STARTING POINTS · THEN DIAL IT IN", IsolatorSettings.PRESETS.map { (name, v) ->
                    MenuItem(name, if (v == s) "✓" else "") { tm.changeIsolator(v); if (!on) audio.setPlayback(p.playback.copy(isolate = true)) }
                } + MenuItem("Reset everything", "") { tm.changeIsolator(IsolatorSettings()) })
            }
            IsoMenuRow("STEREO POSITION", when (s.panMode) { PanMode.OFF -> "Off"; PanMode.KEEP -> "Keep"; PanMode.REMOVE -> "Remove" }, PanMode.entries.map { m ->
                MenuItem(when (m) { PanMode.OFF -> "Off"; PanMode.KEEP -> "Keep what's panned there"; PanMode.REMOVE -> "Remove what's panned there" }, if (m == s.panMode) "✓" else "") { tm.changeIsolator(s.copy(panMode = m)) }
            })
            if (s.panMode != PanMode.OFF) {
                IsoSlider("WHERE", panLabel(s.panCenter), s.panCenter.toFloat(), -1f..1f) { tm.changeIsolator(s.copy(panCenter = it.toDouble().r2())) }
                IsoSlider("HOW WIDE", "${(s.panWidth * 100).roundToInt()}%", s.panWidth.toFloat(), 0.05f..1f) { tm.changeIsolator(s.copy(panWidth = it.toDouble().r2().coerceIn(0.05, 1.0))) }
            }
            IsoSlider(
                "TONE ⇄ HIT",
                when { s.toneHit == 0.0 -> "both"; s.toneHit < 0 -> "tone ${(-s.toneHit * 100).roundToInt()}%"; else -> "hit ${(s.toneHit * 100).roundToInt()}%" },
                s.toneHit.toFloat(), -1f..1f,
            ) { tm.changeIsolator(s.copy(toneHit = it.toDouble().r2())) }
            IsoMenuRow("LOW CUT", if (s.lowCut == 0) "Off" else "${s.lowCut} Hz", LOW_CUTS.filter { s.highCut == 0 || it < s.highCut }.map { v ->
                MenuItem(if (v == 0) "Off" else "$v Hz", if (v == s.lowCut) "✓" else "") { tm.changeIsolator(s.copy(lowCut = v)) }
            })
            IsoMenuRow("HIGH CUT", if (s.highCut == 0) "Off" else hzLabel(s.highCut), HIGH_CUTS.filter { it == 0 || it > s.lowCut }.map { v ->
                MenuItem(if (v == 0) "Off" else hzLabel(v), if (v == s.highCut) "✓" else "") { tm.changeIsolator(s.copy(highCut = v)) }
            })
            NoteFinderView(model, p.path, p.position, on)
        }
    }
}

private val LOW_CUTS = listOf(0, 40, 60, 80, 100, 150, 200, 300, 500, 1000)
private val HIGH_CUTS = listOf(0, 150, 250, 350, 500, 1000, 2000, 4000, 7000, 10000)
private fun hzLabel(v: Int) = if (v >= 1000) "${v / 1000} kHz" else "$v Hz"
private fun panLabel(c: Double) = when { abs(c) < 0.03 -> "centre"; c < 0 -> "left ${(-c * 100).roundToInt()}%"; else -> "right ${(c * 100).roundToInt()}%" }
private fun Double.r2() = Math.round(this * 100) / 100.0

@Composable
private fun IsoSlider(label: String, value: String, v: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().underline().padding(horizontal = 14.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = T.label, modifier = Modifier.weight(1f))
            Text(value, style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
        }
        Slider(
            value = v, onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = C.acc, activeTrackColor = C.acc, inactiveTrackColor = C.line2),
            modifier = Modifier.semantics { contentDescription = "$label: $value" },
        )
    }
}

@Composable
private fun IsoMenuRow(label: String, value: String, items: List<MenuItem>) {
    var open by remember { mutableStateOf(false) }
    Box {
        KvText(label, "$value ▾") { open = true }
        ConsoleMenu(open, { open = false }, label, items)
    }
}

/**
 * The note finder (plan §7): the strongest notes in the last third of a second before the playhead,
 * isolated when the isolator is on, on a piano and on the fretboard of the tuner's tuning (bass in
 * standard tuning when the tuner listens for any instrument).
 */
@Composable
private fun NoteFinderView(model: AppModel, path: String, position: Long, isolated: Boolean) {
    val tm = model.tools
    val audio = model.app.audio
    var notes by remember { mutableStateOf<List<NoteFinder.Note>>(emptyList()) }
    val pos by androidx.compose.runtime.rememberUpdatedState(position)
    LaunchedEffect(path, isolated) {
        while (isActive) {
            val x = audio.isolatedWindow(path, (pos - WINDOW).coerceAtLeast(0), WINDOW, isolated)
            notes = if (x == null) emptyList() else withContext(Dispatchers.Default) { NoteFinder.strengths(x, audio.sampleRate) }
            delay(250)
        }
    }
    val top = NoteFinder.strongest(notes)
    val name = { m: Int -> tm.tuner.read(440.0 * Math.pow(2.0, (m - 69) / 12.0)).pitch.let { tm.tuner.label(it).replaceFirst(it.name.toString(), KeyMode.displayName(it.name)) } }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text("NOTE FINDER · ${if (isolated) "WHAT THE ISOLATOR LEAVES" else "THE WHOLE MIX"}", style = T.label, modifier = Modifier.padding(horizontal = 14.dp))
        Text(
            if (top.isEmpty()) "—" else top.joinToString("  ") { name(it.midi) },
            style = T.hi.copy(fontSize = 22.sp), modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
        Piano(notes, top.map { it.midi }.toSet())
        val tuning = Tunings.all.firstOrNull { it.id == tm.tunerRangeId } ?: Tunings.BASS_STANDARD
        Text("ON ${tuning.name.uppercase()} (THE TUNER'S TUNING)", style = T.label, modifier = Modifier.padding(start = 14.dp, top = 10.dp))
        Fretboard(tuning.strings.map { it.midi }, top.map { it.midi })
        Text(
            "It shows what's loudest around the playhead, a guide for your ear, not a transcription. Pause on a note to read it steadily.",
            style = T.small.copy(lineHeight = 16.sp), modifier = Modifier.padding(14.dp),
        )
    }
}

private const val WINDOW = 16384   // 0.34 s at 48 kHz: 2.9 Hz bins, enough for low bass notes

/** A piano from E1 to C7, each key as bright as its note is strong; the strongest notes outlined. */
@Composable
private fun Piano(notes: List<NoteFinder.Note>, top: Set<Int>) {
    val strength = notes.associate { it.midi to it.strength }
    Canvas(Modifier.fillMaxWidth().height(70.dp).padding(horizontal = 10.dp)) {
        val lo = 28; val hi = 96
        val whites = (lo..hi).filter { (it % 12) !in BLACK }
        val w = size.width / whites.size
        whites.forEachIndexed { i, m ->
            val s = (strength[m] ?: 0.0).toFloat()
            drawRect(lerpColor(C.panel2, C.acc, s), Offset(i * w, 0f), Size(w - 1f, size.height))
            if (m in top) drawRect(C.hi, Offset(i * w, size.height - 6f), Size(w - 1f, 6f))
        }
        for (m in lo..hi) if ((m % 12) in BLACK) {
            val i = whites.indexOfFirst { it > m }
            if (i <= 0) continue
            val s = (strength[m] ?: 0.0).toFloat()
            drawRect(lerpColor(C.bg, C.acc, s), Offset(i * w - w * 0.3f, 0f), Size(w * 0.6f, size.height * 0.6f))
            if (m in top) drawRect(C.hi, Offset(i * w - w * 0.3f, size.height * 0.6f - 5f), Size(w * 0.6f, 5f))
        }
    }
}

private val BLACK = setOf(1, 3, 6, 8, 10)

/** Frets 0–15 of [strings] (MIDI of the open strings, lowest first, drawn at the bottom), dots where the strongest notes are. */
@Composable
private fun Fretboard(strings: List<Int>, top: List<Int>) {
    Canvas(Modifier.fillMaxWidth().height((strings.size * 22 + 16).dp).padding(horizontal = 10.dp, vertical = 8.dp)) {
        val frets = 15
        val fw = size.width / (frets + 1)
        val sh = size.height / strings.size
        for (f in 0..frets) drawLine(if (f == 0) C.hi else C.line2, Offset((f + 1) * fw, 0f), Offset((f + 1) * fw, size.height), if (f == 0) 3f else 1f)
        listOf(3, 5, 7, 9, 12, 15).forEach { f -> drawCircle(C.line, 3f, Offset(f * fw + fw / 2, size.height + 4f)) }
        strings.forEachIndexed { i, open ->
            val y = size.height - (i + 0.5f) * sh
            drawLine(C.dim, Offset(0f, y), Offset(size.width, y), 1f + (strings.size - i) * 0.3f)
            for (f in 0..frets) {
                val m = open + f
                val rank = top.indexOf(m)
                if (rank < 0) continue
                val x = if (f == 0) fw / 2 else f * fw + fw / 2
                if (f == 0) drawCircle(if (rank < 3) C.acc else C.dim, sh * 0.3f, Offset(x, y), style = Stroke(3f))
                else drawCircle(if (rank < 3) C.acc else C.dim, sh * 0.3f, Offset(x, y))
            }
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(a.red + (b.red - a.red) * k, a.green + (b.green - a.green) * k, a.blue + (b.blue - a.blue) * k, 1f)
}
