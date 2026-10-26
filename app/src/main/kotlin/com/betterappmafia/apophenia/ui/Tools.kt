package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.instrument.Tunings
import com.betterappmafia.apophenia.core.pitch.OctaveLabel
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import com.betterappmafia.apophenia.core.tools.Accent
import com.betterappmafia.apophenia.core.tools.DroneThird
import com.betterappmafia.apophenia.core.tools.MetronomeSettings
import com.betterappmafia.apophenia.core.tools.SilentBars
import com.betterappmafia.apophenia.core.tools.TempoTrainer
import com.betterappmafia.apophenia.core.tools.Temperament
import com.betterappmafia.apophenia.core.tools.TunerRange
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** Tools mode (plan §7, C4 'tools'): the list, or the tool opened from it. */
@Composable
fun ToolsMode(model: AppModel, requestMic: (() -> Unit) -> Unit, rest: @Composable () -> Unit) {
    when (model.tools.screen) {
        ToolScreen.METRONOME -> MetronomeScreen(model)
        ToolScreen.TUNER -> TunerScreen(model, requestMic)
        ToolScreen.DRONE -> DroneScreen(model)
        ToolScreen.SPEED -> PlaybackSpeedScreen(model)
        ToolScreen.ISOLATOR -> IsolatorScreen(model)
        ToolScreen.PRACTICE -> PracticeScreen(model)
        null -> ToolList(model, requestMic, rest)
    }
}

@Composable
private fun ToolList(model: AppModel, requestMic: (() -> Unit) -> Unit, rest: @Composable () -> Unit) {
    val tm = model.tools
    val click by model.app.audio.click.collectAsState()
    val drone by model.app.audio.drone.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TitleBar { Text("TOOLS", style = T.title, modifier = Modifier.weight(1f)); Text("PIN TWO IN THE TOP BAR", style = T.label) }
        ToolRow("Metronome", "${tm.metronome.meter} · accents, subdivision, swing, polyrhythm, trainer", "${click.bpm.roundToInt()}") { tm.screen = ToolScreen.METRONOME }
        ToolRow("Tuner", "${tm.tunerRange.label.lowercase()} · A4 ${fmtHz(tm.tuner.a4)} · ${tm.tuner.temperament.label.lowercase()}", "›") { tm.screen = ToolScreen.TUNER }
        ToolRow("Drone", "any root, fifth, third", "${tm.drone.short}${if (drone) " · ON" else ""}") { tm.screen = ToolScreen.DRONE }
        val player by model.app.audio.player.collectAsState()
        ToolRow("Playback speed", "the take in the player · 0.25–2× · pitch ±24 st · A/B loop, step-up, count-in, marks", player?.takeIf { it.takeId > 0 }?.playback?.let { "%.2f×".format(java.util.Locale.ROOT, it.speed) } ?: "›") { tm.screen = ToolScreen.SPEED }
        ToolRow("Isolator", "pan, tone ⇄ hit, band · note finder", if (player?.playback?.isolate == true) "ON" else "›") { tm.screen = ToolScreen.ISOLATOR }
        ToolRow("Practice timer", "attach to a recording, project or exercise · note · history", if (tm.timer.running) "RUNNING" else "›") { tm.screen = ToolScreen.PRACTICE }
        ToolRow("Recorder", "same as ● REC at the top · markers, clipping warning", "●") { requestMic { model.startRecording() } }
        repeat(2) { slot ->
            var open by remember { mutableStateOf(false) }
            Box {
                KvText("TOP BAR\nSLOT ${slot + 1}", "${tm.pins[slot].label} ▾") { open = true }
                ConsoleMenu(open, { open = false }, "SLOT ${slot + 1}", Tool.entries.map { t -> MenuItem(t.label, if (tm.pins[slot] == t) "✓" else "") { tm.pin(slot, t) } })
            }
        }
        rest()
    }
}

@Composable
private fun ToolRow(name: String, note: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(62.dp).underline().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
            Line1(note, T.small)
        }
        Text(value, style = T.hi.copy(color = C.acc, fontSize = 13.sp), modifier = Modifier.padding(start = 10.dp))
    }
}

/** A labelled dropdown row: the label, the current value ▾, its menu. */
@Composable
internal fun MenuRow(label: String, value: String, title: String, items: List<MenuItem>, enabled: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().height(48.dp).underline().clickable(enabled = enabled) { open = true }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = T.label, modifier = Modifier.weight(1f))
            Text("$value ▾", style = T.body.copy(color = if (enabled) C.hi else C.faint, fontWeight = FontWeight.Bold))
        }
        ConsoleMenu(open, { open = false }, title, items)
    }
}

internal fun fmtHz(v: Double) = if (v == v.roundToInt().toDouble()) "${v.roundToInt()}" else "%.1f".format(v)

// ---- Metronome (C4 'metro') -------------------------------------------------------------------

@Composable
private fun MetronomeScreen(model: AppModel) {
    val tm = model.tools
    val audio = model.app.audio
    val s = tm.metronome
    val click by audio.click.collectAsState()
    var meterMenu by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        TitleBar {
            Sq("‹", { tm.screen = null })
            Text("  METRONOME", style = T.title, modifier = Modifier.weight(1f))
            Box {
                Sq("${s.meter} ▾", { meterMenu = true })
                ConsoleMenu(meterMenu, { meterMenu = false }, "METER", MetronomeSettings.COMMON.map { m ->
                    MenuItem(m.toString(), if (m == s.meter) "✓" else if (m.groups.distinct().size > 1) m.groups.joinToString("+") else "") { tm.changeMetronome(s.withMeter(m)) }
                } + MenuItem("Custom…", "›") { custom = true })
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // Tempo: drag sideways on the number, tap it to type, − / TAP / + under it.
            var dragged by remember { mutableFloatStateOf(0f) }
            val shown = if (click.on) click.bpmNow else s.clampedBpm
            Box(
                Modifier.fillMaxWidth().height(120.dp)
                    .pointerInput(s.bpm) {
                        detectHorizontalDragGestures(onDragEnd = { dragged = 0f }) { _, dx ->
                            dragged += dx
                            val steps = (dragged / 24f).toInt()
                            if (steps != 0) { dragged -= steps * 24f; audio.setClick(click.on, (audio.click.value.bpm + steps).coerceIn(20.0, 400.0)) }
                        }
                    }
                    .clickable { typing = s.clampedBpm.roundToInt().toString() }
                    .semantics { contentDescription = "Tempo ${shown.roundToInt()} BPM. Drag sideways to change, tap to type." },
                contentAlignment = Alignment.Center,
            ) {
                Text(fmtHz(shown), style = T.hi.copy(fontSize = 84.sp, letterSpacing = (-3).sp))
            }
            Text(
                when {
                    !click.on -> "BPM · DRAG OR TAP THE NUMBER"
                    click.bar < 0 -> "COUNT-IN · ${-click.bar} TO GO"
                    s.trainer != null -> "BAR ${click.bar + 1} · TRAINER ${fmtHz(click.bpmNow)} → ${s.trainer!!.maxBpm}"
                    else -> "BAR ${click.bar + 1}"
                } + if (click.on && s.silent != null && !s.audible(click.bar)) " · SILENT" else "",
                style = T.label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            typing?.let { t ->
                ConsoleField("TYPE BPM", t, { typing = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, number = true, onDone = {
                    t.toDoubleOrNull()?.let { audio.setClick(click.on, it.coerceIn(20.0, 400.0)) }
                    typing = null
                })
            }
            Gap(10.dp)
            BeatLights(s, click.on, click.beat) { tm.changeMetronome(s.cycleAccent(it)) }
            Text("TAP A BEAT: STRONG › ACCENT › NORMAL › SILENT", style = T.label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            Gap(12.dp)
            Row(Modifier.fillMaxWidth().height(64.dp).underline().drawBehindTop(C.line)) {
                BpmKey("−5") { audio.setClick(click.on, click.bpm - 5) }
                BpmKey("−") { audio.setClick(click.on, click.bpm - 1) }
                Box(Modifier.weight(1.4f).fillMaxHeight().background(C.selected).clickable { model.tap() }.semantics { contentDescription = "Tap tempo" }, contentAlignment = Alignment.Center) {
                    Text("TAP", style = T.button.copy(fontSize = 15.sp))
                }
                BpmKey("+") { audio.setClick(click.on, click.bpm + 1) }
                BpmKey("+5") { audio.setClick(click.on, click.bpm + 5) }
            }
            MenuRow("SUBDIVISION", SUBS[s.subdivision - 1], "CLICKS PER BEAT", (1..9).map { n ->
                MenuItem(SUBS[n - 1], if (n == s.subdivision) "✓" else "$n") { tm.changeMetronome(s.copy(subdivision = n, swing = if (n % 2 == 0) s.swing else 0.5)) }
            })
            MenuRow("SWING", swingLabel(s.swing), "SWING (EVEN SUBDIVISIONS)", SWINGS.map { w ->
                MenuItem(swingLabel(w), if (abs(w - s.swing) < 1e-6) "✓" else "") { tm.changeMetronome(s.copy(swing = w)) }
            }, enabled = s.subdivision % 2 == 0)
            MenuRow("POLYRHYTHM", if (s.poly == 0) "Off" else "${s.poly} against the bar", "SECOND LAYER, EVEN ACROSS THE BAR", (0..9).filter { it != 1 }.map { n ->
                MenuItem(if (n == 0) "Off" else "$n against ${s.meter.beatsPerBar}", if (n == s.poly) "✓" else "") { tm.changeMetronome(s.copy(poly = n)) }
            })
            MenuRow("COUNT-IN", if (s.countInBars == 0) "Off" else "${s.countInBars} bar${if (s.countInBars > 1) "s" else ""}", "BEFORE THE FIRST BAR", (0..2).map { n ->
                MenuItem(if (n == 0) "Off" else "$n bar${if (n > 1) "s" else ""}", if (n == s.countInBars) "✓" else "") { tm.changeMetronome(s.copy(countInBars = n)) }
            })
            val t = s.trainer
            MenuRow("TEMPO TRAINER", t?.let { "${if (it.stepBpm > 0) "+" else ""}${it.stepBpm} every ${it.everyBars}" } ?: "Off", "CHANGE THE TEMPO AS YOU PLAY",
                listOf(MenuItem("Off", if (t == null) "✓" else "") { tm.changeMetronome(s.copy(trainer = null)) }) + TRAINERS.map { (step, every) ->
                    MenuItem("${if (step > 0) "+" else ""}$step BPM every $every bars", if (t?.stepBpm == step && t.everyBars == every) "✓" else "") {
                        val limit = (s.clampedBpm + if (step > 0) 30 else -30).roundToInt().coerceIn(20, 400)
                        tm.changeMetronome(s.copy(trainer = TempoTrainer(step, every, t?.maxBpm?.takeIf { (it > s.clampedBpm) == (step > 0) } ?: limit)))
                    }
                })
            if (t != null) MenuRow("TRAINER STOPS AT", "${t.maxBpm} BPM", "STOP AT", (1..6).map { k ->
                val v = (s.clampedBpm + (if (t.stepBpm > 0) 10 else -10) * k).roundToInt().coerceIn(20, 400)
                MenuItem("$v BPM", if (v == t.maxBpm) "✓" else "") { tm.changeMetronome(s.copy(trainer = t.copy(maxBpm = v))) }
            })
            MenuRow("SILENT BARS", s.silent?.let { "${it.playBars} on, ${it.muteBars} off" } ?: "Off", "PLAY, THEN SILENCE (THE COUNT GOES ON)",
                listOf(MenuItem("Off", if (s.silent == null) "✓" else "") { tm.changeMetronome(s.copy(silent = null)) }) + SILENTS.map { (p, m) ->
                    MenuItem("$p bar${if (p > 1) "s" else ""} on, $m off", if (s.silent == SilentBars(p, m)) "✓" else "") { tm.changeMetronome(s.copy(silent = SilentBars(p, m))) }
                })
            MenuRow("CLICK VOLUME", "${(tm.clickVolume * 100).roundToInt()}%", "VOLUME", listOf(0.25f, 0.4f, 0.55f, 0.7f, 0.85f, 1f).map { v ->
                MenuItem("${(v * 100).roundToInt()}%", if (abs(v - tm.clickVolume) < 0.01f) "✓" else "") { tm.setVolume(v) }
            })
            Text(
                "Takes recorded while the click runs keep its beat grid (the click as played, not a measure of your timing).",
                style = T.small.copy(lineHeight = 16.sp), modifier = Modifier.padding(14.dp),
            )
        }
        Box(
            Modifier.fillMaxWidth().height(72.dp).background(if (click.on) C.selected else C.panel).drawBehindTop(if (click.on) C.acc else C.line)
                .clickable(role = Role.Button) { audio.setClick(!click.on) },
            contentAlignment = Alignment.Center,
        ) { Text(if (click.on) "■ STOP" else "▶ START", style = T.button.copy(fontSize = 16.sp, color = if (click.on) C.acc else C.hi)) }
    }
    if (custom) CustomMeter(onDismiss = { custom = false }) { tm.changeMetronome(s.withMeter(it)) }
}

private val SUBS = listOf("None", "Eighths", "Triplets", "Sixteenths", "Quintuplets", "Sextuplets", "Septuplets", "Thirty-seconds", "Nine")
private val SWINGS = listOf(0.5, 0.54, 0.58, 0.62, 2.0 / 3, 0.71, 0.75)
private fun swingLabel(w: Double) = if (w <= 0.5) "Straight" else if (abs(w - 2.0 / 3) < 1e-6) "Triplet (67%)" else "${(w * 100).roundToInt()}%"
private val TRAINERS = listOf(1 to 2, 2 to 4, 5 to 4, 5 to 8, 10 to 8, -5 to 4)
private val SILENTS = listOf(1 to 1, 2 to 1, 2 to 2, 3 to 1, 4 to 4)

@Composable
private fun androidx.compose.foundation.layout.RowScope.BpmKey(label: String, onClick: () -> Unit) {
    Box(Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = "$label BPM" }, contentAlignment = Alignment.Center) {
        Text(label, style = T.hi.copy(fontSize = 20.sp))
    }
}

/** One light per beat, as tall as its accent; outlined when silent; the sounding beat lit. Tap to change its accent. */
@Composable
private fun BeatLights(s: MetronomeSettings, on: Boolean, beat: Int, onTap: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        val units = s.beatUnits
        for (i in 0 until s.meter.beatsPerBar) {
            val a = s.accentOf(i)
            val h = when (a) { Accent.STRONG -> 52.dp; Accent.ACCENT -> 38.dp; Accent.NORMAL -> 24.dp; Accent.SILENT -> 24.dp }
            val lit = on && beat == i
            Box(
                Modifier.weight(units[i].toFloat()).fillMaxHeight().clickable(role = Role.Button) { onTap(i) }
                    .semantics { contentDescription = "Beat ${i + 1}: ${a.name.lowercase()}. Tap to change." },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    Modifier.fillMaxWidth().height(h).let {
                        if (a == Accent.SILENT) it.border(1.dp, C.line2) else it.background(if (lit) (if (a == Accent.STRONG) C.hi else C.acc) else C.line2)
                    },
                )
            }
        }
    }
}

@Composable
private fun CustomMeter(onDismiss: () -> Unit, onPick: (TimeSignature) -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(C.scrim).clickable(onClick = onDismiss)) {
        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(C.panel).clickable(enabled = false) {}) {
            TitleBar { Text("CUSTOM METER", style = T.title) }
            ConsoleField("METER", text, { text = it; error = null }, placeholder = "5/4, 11/8 as 3+3+3+2/8, 15/16 …")
            Text(
                error ?: "Write the beat groups with + when the beats aren't equal: 2+2+3/8 is 7/8 in three beats.",
                style = T.small.copy(color = if (error != null) C.acc else C.dim, lineHeight = 16.sp), modifier = Modifier.padding(14.dp),
            )
            Row {
                BigButton("CANCEL", onDismiss)
                BigButton("USE IT", {
                    runCatching { TimeSignature.parse(text.trim()) }
                        .onSuccess { if (it.beatsPerBar > 32) error = "Up to 32 beats in a bar." else { onPick(it); onDismiss() } }
                        .onFailure { error = it.message ?: "Not a meter." }
                }, primary = true)
            }
        }
    }
}

// ---- Tuner (C4 'tuner') ------------------------------------------------------------------------

@Composable
private fun TunerScreen(model: AppModel, requestMic: (() -> Unit) -> Unit) {
    val tm = model.tools
    val s = tm.tuner
    val r = tm.reading
    val listening by model.app.audio.tuner.collectAsState()
    val drone by model.app.audio.drone.collectAsState()
    DisposableEffect(Unit) {
        requestMic { tm.startTuner() }
        onDispose { tm.stopTuner() }
    }
    // Back from the background (where the mic is released): open it again.
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME && !model.app.audio.tuner.value) requestMic { tm.startTuner() } }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    Column(Modifier.fillMaxSize()) {
        TitleBar {
            Sq("‹", { tm.screen = null })
            Text("  TUNER", style = T.title, modifier = Modifier.weight(1f))
            var open by remember { mutableStateOf(false) }
            Box {
                Sq("${tm.tunerRange.label.uppercase()} ▾", { open = true })
                ConsoleMenu(open, { open = false }, "LISTEN FOR", buildList {
                    add(MenuItem(TunerRange.ANY.label, if (tm.tunerRangeId == TunerRange.ANY.id) "✓" else "") { tm.setTuner(s, TunerRange.ANY.id) })
                    add(MenuItem(TunerRange.VOICE.label, if (tm.tunerRangeId == TunerRange.VOICE.id) "✓" else "") { tm.setTuner(s, TunerRange.VOICE.id) })
                    Tunings.all.forEach { t -> add(MenuItem(t.name, if (tm.tunerRangeId == t.id) "✓" else t.strings.joinToString(" ")) { tm.setTuner(s, t.id) }) }
                })
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Gap(18.dp)
            val inTune = r != null && abs(r.cents) < 3
            Text(
                r?.let { KeyMode.displayName(it.pitch.name) + (s.label(it.pitch).removePrefix(it.pitch.name.toString())) } ?: "—",
                style = T.hi.copy(fontSize = 84.sp, letterSpacing = (-2).sp, color = if (inTune) C.listening else C.hi),
            )
            Text(
                r?.let { "${if (it.cents >= 0) "+" else "−"}%02d ¢".format(abs(it.cents).roundToInt()) } ?: if (listening) "PLAY A NOTE" else "MIC CLOSED",
                style = T.hi.copy(fontSize = 22.sp, color = if (inTune) C.listening else C.acc),
            )
            Gap(14.dp)
            CentsLed(r?.cents)
            Gap(10.dp)
            Text(
                r?.let { "%.2f Hz · TARGET %.2f · MIDI %d".format(it.hz, it.targetHz, it.midi) } ?: "MIC ON WHILE THIS SCREEN IS OPEN · NO PRE-ROLL",
                style = T.label,
            )
            Gap(18.dp)
            MenuRow("A4", "${fmtHz(s.a4)} Hz", "REFERENCE", A4S.map { a -> MenuItem("$a Hz", if (abs(a - s.a4) < 0.01) "✓" else "") { tm.setTuner(s.copy(a4 = a)) } })
            MenuRow("TEMPERAMENT", s.temperament.label, "RELATIVE TO THE TONIC", Temperament.entries.map { t -> MenuItem(t.label, if (t == s.temperament) "✓" else "") { tm.setTuner(s.copy(temperament = t)) } })
            MenuRow("TONIC", KeyMode.displayName(s.tonic), "TONIC", KeyMode.TONICS.map { p -> MenuItem(KeyMode.displayName(p), if (p == s.tonic) "✓" else "") { tm.setTuner(s.copy(tonic = p, key = s.key?.let { KeyMode(p, it.scaleId) })) } })
            MenuRow("SPELL NOTES IN", s.key?.display ?: "${KeyMode.displayName(s.tonic)} major", "KEY FOR NOTE NAMES", listOf("major", "aeolian", "dorian", "mixolydian", "phrygian", "lydian", "locrian").map { id ->
                val k = KeyMode(s.tonic, id)
                MenuItem(k.display, if (s.key?.scaleId == id || (s.key == null && id == "major")) "✓" else "") { tm.setTuner(s.copy(key = k)) }
            })
            MenuRow("OCTAVE NUMBERS", if (s.octaveLabel == OctaveLabel.MIDDLE_C_IS_C4) "Middle C = C4" else "Middle C = C3", "OCTAVE NUMBERS", OctaveLabel.entries.map { o ->
                MenuItem(if (o == OctaveLabel.MIDDLE_C_IS_C4) "Middle C = C4" else "Middle C = C3 (Yamaha, FL Studio)", if (o == s.octaveLabel) "✓" else "") { tm.setTuner(s.copy(octaveLabel = o)) }
            })
            ToolRow("Drone", "tune against it · ${tm.drone.short}", if (drone) "■ STOP" else "▶ PLAY") { tm.toggleDrone() }
        }
    }
}

internal val A4S = listOf(415.0, 430.0, 432.0, 435.0, 438.0, 439.0, 440.0, 441.0, 442.0, 443.0, 444.0, 445.0, 446.0)

/** −50…+50 cents in 21 segments; the middle one is in tune (±2.5 ¢). */
@Composable
private fun CentsLed(cents: Double?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        val seg = cents?.let { ((it + 50) / 100 * 20).roundToInt().coerceIn(0, 20) }
        for (i in 0..20) {
            val color = when {
                seg == null -> C.panel2
                i == seg && i == 10 -> C.listening
                i == seg -> C.acc
                i == 10 -> C.line2
                else -> C.panel2
            }
            Box(Modifier.weight(1f).height(if (i == 10) 44.dp else 36.dp).background(color))
        }
    }
}

// ---- Drone ---------------------------------------------------------------------------------------

@Composable
private fun DroneScreen(model: AppModel) {
    val tm = model.tools
    val d = tm.drone
    val on by model.app.audio.drone.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TitleBar { Sq("‹", { tm.screen = null }); Text("  DRONE", style = T.title, modifier = Modifier.weight(1f)); Text("TUNED PURE OVER THE ROOT", style = T.label) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(d.short, style = T.hi.copy(fontSize = 40.sp), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp))
            MenuRow("ROOT", KeyMode.displayName(d.root), "ROOT", KeyMode.TONICS.map { p -> MenuItem(KeyMode.displayName(p), if (p == d.root) "✓" else "") { tm.changeDrone(d.copy(root = p)) } })
            MenuRow("OCTAVE", tm.tuner.label(d.rootPitch), "ROOT OCTAVE", (1..5).map { o -> MenuItem(tm.tuner.label(d.root.at(o)), if (o == d.octave) "✓" else "") { tm.changeDrone(d.copy(octave = o)) } })
            MenuRow("FIFTH", if (d.fifth) "On" else "Off", "FIFTH (3:2)", listOf(true, false).map { b -> MenuItem(if (b) "On" else "Off", if (b == d.fifth) "✓" else "") { tm.changeDrone(d.copy(fifth = b)) } })
            MenuRow("THIRD", when (d.third) { DroneThird.OFF -> "Off"; DroneThird.MAJOR -> "Major (5:4)"; DroneThird.MINOR -> "Minor (6:5)" }, "THIRD", DroneThird.entries.map { t ->
                MenuItem(when (t) { DroneThird.OFF -> "Off"; DroneThird.MAJOR -> "Major (5:4)"; DroneThird.MINOR -> "Minor (6:5)" }, if (t == d.third) "✓" else "") { tm.changeDrone(d.copy(third = t)) }
            })
            MenuRow("OCTAVE ABOVE", if (d.addOctave) "On" else "Off", "ADD THE OCTAVE", listOf(true, false).map { b -> MenuItem(if (b) "On" else "Off", if (b == d.addOctave) "✓" else "") { tm.changeDrone(d.copy(addOctave = b)) } })
            Text(
                "A sustained tone, root at A4 ${fmtHz(tm.tuner.a4)} in ${tm.tuner.temperament.label.lowercase()} on ${KeyMode.displayName(tm.tuner.tonic)} (set in the tuner). The piano drone arrives with the piano sampler (M5).",
                style = T.small.copy(lineHeight = 16.sp), modifier = Modifier.padding(14.dp),
            )
        }
        Box(
            Modifier.fillMaxWidth().height(72.dp).background(if (on) C.selected else C.panel).drawBehindTop(if (on) C.acc else C.line)
                .clickable(role = Role.Button) { tm.toggleDrone() },
            contentAlignment = Alignment.Center,
        ) { Text(if (on) "■ STOP" else "▶ PLAY", style = T.button.copy(fontSize = 16.sp, color = if (on) C.acc else C.hi)) }
    }
}
