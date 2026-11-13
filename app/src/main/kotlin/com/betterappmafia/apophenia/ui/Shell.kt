package com.betterappmafia.apophenia.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.audio.Mic
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The frame every screen sits in (C4): project/mic bar, the click · tools · ● REC · + NEW strip, the sub-tabs, the
 * screen, the persistent player, the three tabs (navigation B, 25 Oct 2026). [requestMic] runs its block once the mic permission is granted.
 */
@Composable
fun Shell(model: AppModel, requestMic: (() -> Unit) -> Unit) {
    val audio = model.app.audio
    val mic by audio.mic.collectAsState()

    Box(Modifier.fillMaxSize().background(C.bg)) {
        // Film: the project's painted frame behind every screen (design plan, 31 Oct 2026).
        if (C.palette.film) {
            // Full brightness on every screen, light trails crossing list text included: Christopher read it fine and
            // preferred it to a darker scrim off the Board (31 Oct 2026).
            SectionBackdrop(model, Modifier.fillMaxSize())
        }
        // Stand mode takes the whole screen (plan §4.4); recording, sheets and dialogs still sit over it.
        val stand = model.standProject
        if (stand != null) Box(Modifier.fillMaxSize().systemBarsPadding()) { StandMode(model, stand, requestMic) { model.standProject = null } }
        // Edge to edge, the window doesn't shrink for the keyboard (adjustResize does nothing): the frame ends at the
        // keyboard's top itself, or long lyrics type on under it (Christopher, 9 Nov 2026).
        else Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            TopBar(model, mic)
            ClickRecBar(model, mic, requestMic)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val detail = model.detail
                when {
                    model.correcting != null -> CorrectionScreen(model, model.correcting!!)
                    detail != null -> ScrapDetail(model, detail, requestMic)
                    model.showStack -> StackScreen(model, requestMic)
                    model.showTrash -> TrashScreen(model)
                    model.showNotices -> NoticesScreen(model)
                    model.showSettings -> SettingsScreen(model, requestMic)
                    else -> Column(Modifier.fillMaxSize()) {
                        SubTabs(model)
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when (model.mode) {
                                Mode.BOARD -> BoardScreen(model, requestMic)
                                Mode.LIBRARY -> LibraryScreen(model)
                                Mode.TOOLS -> ToolsMode(model, requestMic) {}
                                Mode.EAR, Mode.COURSE, Mode.REFERENCE -> LearnScreen(model, requestMic)
                                Mode.AI -> AiScreen(model)
                                Mode.EXERCISES -> ExercisesScreen(model)
                            }
                        }
                    }
                }
            }
            NoticeBar(model)
            // While typing, the player and tabs would sit on the keyboard and squeeze the text; they were under it before.
            // [Claude] hidden while the keyboard is up.
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            val typing = WindowInsets.isImeVisible
            if (!typing) {
                Transport(model)
                NavBar(model)
            }
        }
        // In stand mode the sheet stays up while recording (plan §4.4: the lyric you're singing stays visible).
        if (mic == Mic.RECORDING && model.standProject == null) RecordingOverlay(model)
        SheetHost(model)
        model.crashText?.let { CrashDialog(model, it) }
        model.restartMessage?.let { RestartScreen(it) }
    }

    BackHandler(enabled = model.sheet != Sheet.None || model.correcting != null || model.detail != null || model.showTrash || model.showNotices || model.showSettings || model.showStack || model.selecting || model.performanceOpen || (model.mode == Mode.EXERCISES && (model.exerciseOpen != null || model.curatedOpen != null)) || (model.mode == Mode.TOOLS && model.tools.screen != null) || (model.mode.learn && (model.learnRudiment != null || model.learnGroove != null)) ||
        (model.mode == Mode.REFERENCE && (model.refPage != null || model.learnList != null)) || (model.mode == Mode.COURSE && (model.coursePage != null || model.courseCat != null))) {
        when {
            model.sheet != Sheet.None -> model.sheet = Sheet.None
            model.correcting != null -> model.correcting = null
            model.selecting -> model.endSelect()
            model.detail != null -> model.detail = null
            model.showStack -> model.showStack = false
            model.showNotices -> model.showNotices = false
            model.showSettings -> model.showSettings = false
            model.performanceOpen -> model.performanceOpen = false
            model.mode == Mode.EXERCISES && (model.exerciseOpen != null || model.curatedOpen != null) -> { model.exerciseOpen = null; model.curatedOpen = null }
            model.mode == Mode.TOOLS && model.tools.screen != null -> model.tools.screen = null
            model.mode.learn && model.learnGroove != null -> model.learnGroove = null
            model.mode.learn && model.learnRudiment != null -> model.learnRudiment = null
            // Back goes up one level, as each screen's ‹ does (it left the app from any Reference or Course page).
            model.mode.learn && model.refPage != null -> model.refPage = null
            model.mode == Mode.REFERENCE && model.learnList != null -> model.learnList = model.learnList!!.let { if (":" in it) it.substringBefore(":") else null }
            model.mode == Mode.COURSE && model.earStudyReturn -> model.returnToEar()
            model.mode == Mode.COURSE && model.coursePage != null -> { model.courseCat = com.betterappmafia.apophenia.core.learn.Course.categoryOf(model.coursePage!!)?.id; model.coursePage = null }
            model.mode == Mode.COURSE && model.courseCat != null -> model.courseCat = null
            else -> model.showTrash = false
        }
    }
}

@Composable
private fun TopBar(model: AppModel, mic: Mic) {
    Row(
        Modifier.fillMaxWidth().height(40.dp).underline().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProjectSwitcher(model, Modifier.weight(1f))
        val tunerMic by model.app.audio.tuner.collectAsState()
        val (label, color) = when (mic) {
            Mic.OFF -> if (tunerMic) "TUNER · MIC ON" to C.listening else "MIC OFF" to C.dim
            Mic.RECORDING -> "RECORDING" to C.rec
        }
        // The real state only: the mic opens for ● REC and the tuner, never on its own (no pre-roll).
        Row(
            Modifier.fillMaxHeight()
                .semantics { contentDescription = "Microphone: $label" }
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier.size(8.dp).let { if (mic == Mic.OFF && !tunerMic) it.border(1.5.dp, color, CircleShape) else it.background(color, CircleShape) }
            )
            Text(label, style = T.label.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 0.08.sp))
        }
        // Christopher, 5 Nov 2026: a coffee cup as a quick way to the donate link. Opens the browser; the app stays offline.
        val ctx = androidx.compose.ui.platform.LocalContext.current
        Box(
            Modifier.fillMaxHeight().padding(start = 4.dp).width(40.dp).clickable(role = Role.Button) {
                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(DONATE_URL))) }
            }.semantics { contentDescription = "Support Apophenia on Ko-fi" },
            contentAlignment = Alignment.Center,
        ) { Glyph(GlyphKind.CUP, C.dim, 18.dp) }
        Box(
            Modifier.fillMaxHeight().width(44.dp).clickable(role = Role.Button) { model.showSettings = !model.showSettings }
                .semantics { contentDescription = "Settings" },
            contentAlignment = Alignment.Center,
        ) { Text("⚙", style = T.hi.copy(fontSize = 18.sp, color = if (model.showSettings) C.acc else C.dim)) }
    }
}

/** The project ▾ at the top left (C4, plan §4.1): switch, make, rename or delete projects from any screen. */
@Composable
private fun ProjectSwitcher(model: AppModel, modifier: Modifier) {
    val projects by model.projects.collectAsState()
    val current = projects.firstOrNull { it.project.id == model.projectId }?.project
    var menu by remember { mutableStateOf(false) }
    Box(modifier.fillMaxHeight()) {
        Row(
            Modifier.fillMaxHeight().clickable(role = Role.Button) { menu = true }
                .semantics { contentDescription = "Project: ${current?.name ?: "none open"}. Tap to switch." },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Line1((current?.name ?: if (projects.isEmpty()) "NO PROJECT" else "NO PROJECT OPEN").uppercase(), T.button.copy(fontSize = 12.sp, color = if (current != null) C.hi else C.dim), Modifier.weight(1f, fill = false))
            Text("  ▾", style = T.label)
        }
        ConsoleMenu(menu, { menu = false }, "PROJECTS", buildList {
            projects.forEach { p ->
                add(MenuItem(p.project.name, if (p.project.id == current?.id) "✓ OPEN" else "${p.scraps} ON BOARD") {
                    model.openProject(p.project.id)
                    if (model.mode != Mode.BOARD || model.detail != null) model.go(Mode.BOARD)
                })
            }
            add(MenuItem("New project", "+") { model.sheet = Sheet.ProjectName(null) })
            if (current != null) {
                add(MenuItem("Rename ${current.name}", "") { model.sheet = Sheet.ProjectName(current.id) })
                add(MenuItem("Delete ${current.name}", "TO TRASH") { model.trashProject(current.id) })
            }
        })
    }
}

@Composable
private fun ClickRecBar(model: AppModel, mic: Mic, requestMic: (() -> Unit) -> Unit) {
    val audio = model.app.audio
    val click by audio.click.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(click.beats) {
        if (click.on && click.beats > 0) { flash = true; delay(90); flash = false }
    }
    Row(Modifier.fillMaxWidth().height(50.dp).background(C.panel).underline()) {
        Box(Modifier.weight(1.3f).fillMaxHeight()) {
            Row(
                Modifier.fillMaxSize().clickable(role = Role.Button) { menu = true }
                    .semantics { contentDescription = "Metronome menu. Click ${if (click.on) "on" else "off"}, ${click.bpm.toInt()} BPM" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                val accent = click.beats > 0 && (click.beats - 1) % 4 == 0L
                Box(Modifier.size(10.dp).background(if (flash) (if (accent) C.hi else C.acc) else C.line2, CircleShape))
                Text(" CLICK ", style = T.button.copy(color = if (click.on) C.hi else C.text))
                Text(click.bpm.toInt().toString(), style = T.hi.copy(fontSize = 17.sp))
                Text(" ▾", style = T.label)
            }
            ConsoleMenu(
                expanded = menu, onDismiss = { menu = false },
                header = {
                    Row(Modifier.width(260.dp).underline(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(64.dp).height(56.dp).clickable { audio.setClick(click.on, click.bpm - 1) }.semantics { contentDescription = "Slower" }, contentAlignment = Alignment.Center) { Text("−", style = T.hi.copy(fontSize = 24.sp)) }
                        Text(click.bpm.toInt().toString(), style = T.hi.copy(fontSize = 34.sp), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        Box(Modifier.width(64.dp).height(56.dp).clickable { audio.setClick(click.on, click.bpm + 1) }.semantics { contentDescription = "Faster" }, contentAlignment = Alignment.Center) { Text("+", style = T.hi.copy(fontSize = 24.sp)) }
                    }
                    Row(Modifier.width(260.dp).height(56.dp).underline().clickable { model.tap() }, verticalAlignment = Alignment.CenterVertically) {
                        Text("Tap tempo", style = T.body.copy(fontWeight = FontWeight.Bold, color = C.hi), modifier = Modifier.weight(1f).padding(start = 14.dp))
                        Text("TAP HERE, KEEP TAPPING", style = T.label, modifier = Modifier.padding(end = 14.dp))
                    }
                },
                items = listOf(
                    MenuItem(if (click.on) "Click is on" else "Click is off", if (click.on) "TURN OFF" else "TURN ON") { audio.setClick(!click.on) },
                    MenuItem("Full metronome", "${model.tools.metronome.meter} ›") { model.tools.open(ToolScreen.METRONOME) },
                ),
            )
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(C.line))
        ToolsSlots(model, Modifier.weight(1f))
        Box(Modifier.width(1.dp).fillMaxHeight().background(C.line))
        val recording = mic == Mic.RECORDING
        // While the stack plays, ● REC records a new layer of it (AppModel.startRecording); the spoken label says so.
        val stack by audio.stack.collectAsState()
        Box(
            Modifier.weight(0.85f).fillMaxHeight()
                .clickable(role = Role.Button) { if (recording) model.stopRecording() else requestMic { model.startRecording() } }
                .semantics { contentDescription = if (recording) "Stop recording" else if (stack?.playing == true) "Record a new layer of the playing stack" else "Record to the Library" },
            contentAlignment = Alignment.Center,
        ) { Text(if (recording) "■ STOP" else "● REC", style = T.button.copy(color = C.rec, fontSize = 12.sp)) }
        Box(Modifier.width(1.dp).fillMaxHeight().background(C.line))
        NewButton(model, requestMic, Modifier.weight(0.85f))
    }
}

/**
 * TOOLS ▾ in the top bar (C4; plan §2.4): the two tools pinned in Tools, with their live value, then
 * every tool. The drone starts and stops right here; the others open their screen.
 */
@Composable
private fun ToolsSlots(model: AppModel, modifier: Modifier) {
    val tm = model.tools
    val drone by model.app.audio.drone.collectAsState()
    var menu by remember { mutableStateOf(false) }
    Box(modifier.fillMaxHeight()) {
        Row(
            Modifier.fillMaxSize().clickable(role = Role.Button) { menu = true }.semantics { contentDescription = "Tools menu" },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            if (drone) Box(Modifier.size(8.dp).background(C.acc, CircleShape))
            Text(if (drone) " TOOLS" else "TOOLS", style = T.button)
            Text("  ▾", style = T.label)
        }
        ConsoleMenu(menu, { menu = false }, "TOOLS · TWO PINNED", buildList {
            tm.pins.distinct().forEach { t ->
                add(MenuItem(t.label, when (t) {
                    Tool.DRONE -> if (drone) "■ ${tm.drone.short}" else "▶ ${tm.drone.short}"
                    Tool.TUNER -> "${tm.tunerRange.label.uppercase()} ›"
                    Tool.METRONOME -> "${tm.metronome.meter} ›"
                    Tool.SPEED -> "${model.app.audio.player.value?.playback?.label ?: ""} ›"
                    Tool.ISOLATOR -> if (model.app.audio.player.value?.playback?.isolate == true) "ON ›" else "›"
                    Tool.PRACTICE -> if (tm.timer.running) "RUNNING ›" else "›"
                }) { tm.runPinned(t) })
            }
            add(MenuItem("All tools", "›") { model.go(Mode.TOOLS) })
        })
    }
}

/**
 * The persistent player (plan §2.2): the stack when one is loaded (tap it for the stack workspace),
 * and a take played from anywhere, as its own row, so a take can be tried over the stack.
 */
@Composable
private fun Transport(model: AppModel) {
    val p by model.app.audio.player.collectAsState()
    val s by model.app.audio.stack.collectAsState()
    val stack = s
    if (p != null || stack == null) TakeRow(model, p, slim = stack != null)
    if (stack != null) {
        // The stack in the player (his stacks pick, 2 Nov 2026): a bar per layer in its take's key colour, the name as a
        // title card, tempo · key · layers; ■/▶ plays, × takes it out of the player (it stays saved).
        val layers by model.stacks.layers.collectAsState()
        Row(Modifier.fillMaxWidth().height(60.dp).background(C.panel).drawBehindTop(C.line), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.padding(start = 12.dp).width(28.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                layers.take(6).forEach { r ->
                    val k = KeyMode.parseOrNull(r.key)
                    val warm = com.betterappmafia.apophenia.core.art.FilmFrame.palette(k?.tonic?.pitchClass, k?.let { isMinor(it) } ?: false).third
                    Box(Modifier.fillMaxWidth().height(4.dp).background(androidx.compose.ui.graphics.Color(warm[0] / 255f, warm[1] / 255f, warm[2] / 255f, if (r.layer.muted) 0.3f else 0.85f)))
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable { model.showStack = true; model.detail = null }.padding(horizontal = 12.dp)
                    .semantics { contentDescription = "${stack.title}. Open the stack." },
                verticalArrangement = Arrangement.Center,
            ) {
                Line1(stack.title, T.display.copy(fontSize = 19.sp))
                Line1(listOfNotNull(model.stacks.tempo(layers)?.let { "%.0f BPM".format(it) }, model.stacks.key(layers)?.display?.uppercase(), "${stack.layers} LAYER${if (stack.layers == 1) "" else "S"}").joinToString(" · ") +
                    "  ${clock(stack.position, model.app.audio.sampleRate)}", T.label)
            }
            Box(
                Modifier.width(52.dp).fillMaxHeight().drawBehind { drawLine(C.line, Offset(0f, 0f), Offset(0f, size.height), 1f) }
                    .clickable(enabled = stack.layers > 0, role = Role.Button) { model.app.audio.stackToggle() }
                    .semantics { contentDescription = if (stack.playing) "Stop the stack" else "Play the stack" },
                contentAlignment = Alignment.Center,
            ) { Text(if (stack.playing) "■" else "▶", style = T.hi.copy(fontSize = 20.sp, color = if (stack.layers == 0) C.faint else C.acc)) }
            Box(
                Modifier.width(48.dp).fillMaxHeight().drawBehind { drawLine(C.line, Offset(0f, 0f), Offset(0f, size.height), 1f) }
                    .clickable(role = Role.Button) { model.showStack = false; model.app.audio.unloadStack() }
                    .semantics { contentDescription = "Take the stack out of the player. It stays saved." },
                contentAlignment = Alignment.Center,
            ) { Text("×", style = T.hi.copy(fontSize = 20.sp, color = C.dim)) }
        }
    }
}


/**
 * The take in the player (Christopher, 4 Nov 2026: "The player needs better controls"; he picked a scrub bar, back to
 * start, and speed and loop): a scrub bar along the top (tap or drag to move), back to the top or to loop point A,
 * the take's speed and A–B loop as menus, and play/pause.
 */
@Composable
private fun TakeRow(model: AppModel, p: com.betterappmafia.apophenia.audio.Loaded?, slim: Boolean) {
    val audio = model.app.audio
    // Speed and loop apply to recordings and imported songs only (plan §7 Playback speed), not reference sounds or scores.
    val rec = p != null && p.scrapId != REFERENCE_SCRAP && p.takeId > 0
    var drag by remember { mutableStateOf<Float?>(null) }
    Column(Modifier.fillMaxWidth().background(C.panel).drawBehindTop(C.line)) {
        if (p != null) Scrubber(p, drag, { drag = it }) { f -> drag = null; audio.seekFrame((p.frames * f).toLong(), play = p.playing) }
        Row(Modifier.fillMaxWidth().height(if (slim) 40.dp else 50.dp)) {
            if (p != null) PlayerKey(44.dp, if (p.playback.hasLoop) "Back to loop point A" else "Back to the start", divider = false, onClick = {
                audio.seekFrame(if (p.playback.hasLoop) p.playback.loopA else 0, play = p.playing)
            }) { Glyph(GlyphKind.START, C.hi, if (slim) 12.dp else 14.dp) }
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable(enabled = p != null && p.scrapId != REFERENCE_SCRAP) { p?.let { model.open(it.scrapId) } }
                    .padding(horizontal = if (p == null) 12.dp else 4.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                if (p == null) {
                    Text("PLAYER", style = T.label)
                    Text("Nothing loaded · ▶ a take in the Library", style = T.small)
                } else {
                    val at = drag?.let { (p.frames * it).toLong() } ?: p.position
                    val time = "${clock(at, p.rate)} / ${clock(p.frames, p.rate)}"
                    if (slim) Line1("$time  ${p.title.uppercase()}", T.label)
                    else {
                        Line1(p.title.uppercase(), T.label)
                        Text(time, style = T.hi.copy(fontSize = 13.sp))
                    }
                }
            }
            if (rec) {
                SpeedKey(model, p!!)
                LoopKey(model, p)
            }
            PlayerKey(56.dp, if (p?.playing == true) "Pause" else "Play", enabled = p != null, onClick = { audio.togglePlay() }) {
                Glyph(if (p?.playing == true) GlyphKind.PAUSE else GlyphKind.PLAY, if (p == null) C.faint else C.hi, if (slim) 14.dp else 18.dp)
            }
        }
    }
}

/** The position along the take, with the A–B loop shaded: tap to move there, drag to scrub (it moves on release). */
@Composable
private fun Scrubber(p: com.betterappmafia.apophenia.audio.Loaded, drag: Float?, onDrag: (Float?) -> Unit, onSeek: (Float) -> Unit) {
    val n = p.frames.coerceAtLeast(1).toFloat()
    val at = drag ?: (p.position / n).coerceIn(0f, 1f)
    Canvas(
        Modifier.fillMaxWidth().height(22.dp)
            .pointerInput(p.path, p.frames) {
                val pad = 12.dp.toPx()
                detectTapGestures { o -> onSeek(((o.x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)) }
            }
            .pointerInput(p.path, p.frames) {
                val pad = 12.dp.toPx()
                fun frac(x: Float) = ((x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)
                var f = 0f
                detectHorizontalDragGestures(
                    onDragStart = { o -> f = frac(o.x); onDrag(f) },
                    onDragEnd = { onSeek(f) },
                    onDragCancel = { onDrag(null) },
                ) { change, _ -> f = frac(change.position.x); onDrag(f) }
            }
            .semantics { contentDescription = "Position ${clock(p.position, p.rate)} of ${clock(p.frames, p.rate)}. Tap or drag to move." },
    ) {
        val pad = 12.dp.toPx(); val w = size.width - 2 * pad; val y = size.height / 2
        val pb = p.playback
        if (pb.loopA >= 0) {
            val a = pad + w * (pb.loopA / n).coerceIn(0f, 1f)
            val b = if (pb.hasLoop) pad + w * (pb.loopB / n).coerceIn(0f, 1f) else a + 2.dp.toPx()
            drawRect(C.listening.copy(alpha = 0.4f), Offset(a, y - 5.dp.toPx()), Size(b - a, 10.dp.toPx()))
        }
        drawLine(C.line2, Offset(pad, y), Offset(pad + w, y), 2.dp.toPx())
        drawLine(C.acc, Offset(pad, y), Offset(pad + w * at, y), 2.dp.toPx())
        drawLine(C.hi, Offset(pad + w * at, y - 6.dp.toPx()), Offset(pad + w * at, y + 6.dp.toPx()), 3.dp.toPx())
    }
}

/** The take's speed as a menu of the Playback speed steps; the full screen has the fine steps, pitch and step-up. */
@Composable
private fun SpeedKey(model: AppModel, p: com.betterappmafia.apophenia.audio.Loaded) {
    val pb = p.playback
    var open by remember { mutableStateOf(false) }
    Box {
        PlayerKey(60.dp, "Speed %.2f times. Change it.".format(java.util.Locale.ROOT, pb.speed), onClick = { open = true }) {
            Text("%.2f×".format(java.util.Locale.ROOT, pb.speed), style = T.hi.copy(fontSize = 13.sp, color = if (pb.speed != 1.0) C.acc else C.hi))
        }
        ConsoleMenu(open, { open = false }, "SPEED · PITCH KEPT", com.betterappmafia.apophenia.audio.Playback.SPEEDS.map { s ->
            MenuItem("%.2f×".format(java.util.Locale.ROOT, s), if (abs(s - pb.speed) < 1e-6) "✓" else "${(s * 100).toInt()}%") { model.app.audio.setPlayback(pb.copy(speed = s)) }
        } + MenuItem("Pitch, step-up, marks…", "PLAYBACK SPEED") { model.tools.open(ToolScreen.SPEED) })
    }
}

/** The A–B loop: set A and B where the take is, or clear it. */
@Composable
private fun LoopKey(model: AppModel, p: com.betterappmafia.apophenia.audio.Loaded) {
    val pb = p.playback
    val audio = model.app.audio
    var open by remember { mutableStateOf(false) }
    val label = if (pb.hasLoop) "A–B" else if (pb.loopA >= 0) "A…" else "LOOP"
    Box {
        PlayerKey(52.dp, if (pb.hasLoop) "Loop on. Change it." else "Loop. Set it.", onClick = { open = true }) {
            Text(label, style = T.button.copy(fontSize = 11.sp, color = if (pb.loopA >= 0) C.acc else C.hi))
        }
        val here = clock(p.position, p.rate)
        ConsoleMenu(open, { open = false }, if (pb.hasLoop) "LOOP ${clock(pb.loopA, p.rate)}–${clock(pb.loopB, p.rate)}" else "LOOP", listOf(
            MenuItem("Set A here", here) { audio.setPlayback(pb.copy(loopA = p.position, loopB = if (pb.loopB > p.position) pb.loopB else -1)) },
            MenuItem("Set B here", here, enabled = pb.loopA >= 0 && p.position > pb.loopA) { audio.setPlayback(pb.copy(loopB = p.position)) },
            MenuItem("Clear the loop", "", enabled = pb.loopA >= 0) { audio.setPlayback(pb.copy(loopA = -1, loopB = -1, stepBy = 0.0, countInBeats = 0)) },
            MenuItem("Step-up, count-in, saved loops…", "PLAYBACK SPEED") { model.tools.open(ToolScreen.SPEED) },
        ))
    }
}

/** A key in the player row, with a divider on its left. */
@Composable
private fun PlayerKey(width: androidx.compose.ui.unit.Dp, description: String, enabled: Boolean = true, divider: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.width(width).fillMaxHeight().drawBehind { if (divider) drawLine(C.line, Offset(0f, 0f), Offset(0f, size.height), 1f) }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}

enum class GlyphKind { PLAY, PAUSE, START, CUP }

/** Transport glyphs drawn, not typed: the fonts disagree on pause and back-to-start, and ■ read as stopped when it was paused. */
@Composable
fun Glyph(kind: GlyphKind, color: Color, size: androidx.compose.ui.unit.Dp = 16.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        when (kind) {
            GlyphKind.PLAY -> drawPath(Path().apply { moveTo(w * 0.18f, 0f); lineTo(w * 0.95f, h / 2); lineTo(w * 0.18f, h); close() }, color)
            GlyphKind.PAUSE -> {
                drawRect(color, Offset(w * 0.18f, 0f), Size(w * 0.24f, h))
                drawRect(color, Offset(w * 0.58f, 0f), Size(w * 0.24f, h))
            }
            GlyphKind.START -> {
                drawRect(color, Offset(w * 0.1f, h * 0.08f), Size(w * 0.16f, h * 0.84f))
                drawPath(Path().apply { moveTo(w * 0.92f, h * 0.08f); lineTo(w * 0.3f, h / 2); lineTo(w * 0.92f, h * 0.92f); close() }, color)
            }
            GlyphKind.CUP -> {
                val sw = w * 0.09f
                val line = androidx.compose.ui.graphics.drawscope.Stroke(width = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                // Cup body: straight top, sides narrowing to a rounded base.
                drawPath(Path().apply {
                    moveTo(w * 0.08f, h * 0.42f); lineTo(w * 0.72f, h * 0.42f)
                    lineTo(w * 0.66f, h * 0.84f); quadraticTo(w * 0.64f, h * 0.94f, w * 0.54f, h * 0.94f)
                    lineTo(w * 0.26f, h * 0.94f); quadraticTo(w * 0.16f, h * 0.94f, w * 0.14f, h * 0.84f); close()
                }, color, style = line)
                // Handle.
                drawArc(color, -80f, 160f, false, Offset(w * 0.6f, h * 0.48f), Size(w * 0.3f, h * 0.26f), style = line)
                // Steam.
                for (x in listOf(0.3f, 0.5f)) drawPath(Path().apply {
                    moveTo(w * x, h * 0.32f); quadraticTo(w * (x - 0.08f), h * 0.2f, w * x, h * 0.12f); quadraticTo(w * (x + 0.08f), h * 0.04f, w * x, h * 0.0f)
                }, color, style = line)
            }
        }
    }
}

@Composable
private fun NavBar(model: AppModel) {
    Row(Modifier.fillMaxWidth().height(58.dp).drawBehindTop(C.line)) {
        Tab.entries.forEach { t ->
            val on = model.mode.tab == t && model.detail == null && !model.showTrash
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .drawBehind { if (on) drawLine(C.acc, Offset(0f, 1f), Offset(size.width, 1f), 4f) }
                    .clickable(role = Role.Tab) { model.go(t) }
                    .semantics { contentDescription = "${t.label}: ${t.modes.joinToString { it.label.lowercase() }}" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(t.label, style = T.button.copy(fontSize = 12.sp, color = if (on) C.hi else C.dim, letterSpacing = 0.1.sp))
                Text(t.modes.joinToString(" · ") { it.label }, style = T.label.copy(fontSize = 8.sp, letterSpacing = 0.sp, color = if (on) C.dim else C.faint), maxLines = 1, softWrap = false)
            }
        }
    }
}

/** The open tab's sub-tabs, over its screen. */
@Composable
private fun SubTabs(model: AppModel) {
    Row(Modifier.fillMaxWidth().height(42.dp).underline()) {
        model.mode.tab.modes.forEach { m ->
            val on = model.mode == m
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .drawBehind { if (on) drawLine(C.acc, Offset(size.width * 0.18f, size.height - 1f), Offset(size.width * 0.82f, size.height - 1f), 4f) }
                    .clickable(role = Role.Tab) { model.go(m) },
                contentAlignment = Alignment.Center,
            ) { Text(m.label, style = T.button.copy(color = if (on) C.hi else C.dim)) }
        }
    }
}

/**
 * + NEW in the strip (Christopher, 25 Oct 2026): pick the kind of fragment and its editor (or the recorder) opens.
 * It goes into the Library, or into the open project and the Library. [Claude] Starts on the project when you are on
 * its Board, on the Library everywhere else.
 */
@Composable
private fun NewButton(model: AppModel, requestMic: (() -> Unit) -> Unit, modifier: Modifier) {
    var menu by remember { mutableStateOf(false) }
    var toProject by remember { mutableStateOf(false) }
    val pid = model.projectId
    val name = model.projectName(pid)
    Box(modifier.fillMaxHeight()) {
        Box(
            Modifier.fillMaxSize().clickable(role = Role.Button) { toProject = name != null && model.mode == Mode.BOARD; menu = true }
                .semantics { contentDescription = "New fragment" },
            contentAlignment = Alignment.Center,
        ) { Text("+ NEW", style = T.button.copy(color = C.acc, fontSize = 12.sp)) }
        val into = if (toProject && name != null) pid else null
        ConsoleMenu(menu, { menu = false }, items = buildList {
            add(MenuItem("Record", "● NOW") {
                requestMic { if (into != null) model.startRecording(RecordTarget.Project(into, name!!)) else model.startRecording() }
            })
            WRITABLE.forEach { (f, label, note) -> add(MenuItem(label, note) { model.write(f, into) }) }
            add(MenuItem("Guitar tab", "STANDARD TUNING") { model.writeTab(bass = false, into) })
            add(MenuItem("Bass tab", "E STANDARD") { model.writeTab(bass = true, into) })
            add(MenuItem("Sheet music", "PIANO KEYS OR TAP THE STAFF") { model.writeSheet(into) })
            add(MenuItem("Piano roll", "SHEET MUSIC, FL STUDIO STYLE") { model.writeSheet(into, roll = true) })
            add(MenuItem("Import from the phone", "AUDIO · TEXT · MUSICXML · MIDI ›", enabled = model.importing == 0) { model.startImport(into) })
        }, header = {
            Column(Modifier.width(280.dp).underline()) {
                Text("NEW · INTO", style = T.label.copy(color = C.acc), modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 6.dp))
                Row(Modifier.fillMaxWidth().height(44.dp)) {
                    val options = listOfNotNull(false to "LIBRARY", name?.let { true to "${it.uppercase()} + LIBRARY" })
                    options.forEach { (p, label) ->
                        val on = (into != null) == p
                        Box(
                            Modifier.weight(1f).fillMaxHeight().background(if (on) C.selected else C.panel2).clickable { toProject = p }
                                .semantics { contentDescription = "Into ${label.lowercase()}" },
                            contentAlignment = Alignment.Center,
                        ) { Line1(label, T.button.copy(fontSize = 10.sp, color = if (on) C.hi else C.dim)) }
                    }
                }
            }
        })
    }
}

@Composable
private fun NoticeBar(model: AppModel) {
    val n = model.notice ?: return
    Row(
        Modifier.fillMaxWidth().background(C.panel2).drawBehindTop(C.acc).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(n.text, style = T.body.copy(color = C.hi), modifier = Modifier.weight(1f).padding(vertical = 12.dp))
        n.undo?.let { undo ->
            Box(Modifier.height(48.dp).clickable { undo(); model.notice = null }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text(n.action, style = T.button.copy(color = C.acc))
            }
        }
    }
}

@Composable
private fun RecordingOverlay(model: AppModel) {
    val audio = model.app.audio
    val frames by audio.recFrames.collectAsState()
    val level by audio.level.collectAsState()
    val paused by audio.recPaused.collectAsState()
    val target = model.recordTarget
    Column(Modifier.fillMaxSize().background(C.bg).systemBarsPadding().clickable(enabled = false) {}) {
        Row(Modifier.fillMaxWidth().height(40.dp).underline().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).let { if (paused) it.border(1.5.dp, C.rec, CircleShape) else it.background(C.rec, CircleShape) })
            Text(if (paused) "  PAUSED" else "  RECORDING", style = T.button.copy(color = C.rec), modifier = Modifier.weight(1f))
            Text(
                when (target) {
                    RecordTarget.Library -> "TO LIBRARY"
                    is RecordTarget.Project -> "TO ${target.name.uppercase()}"
                    is RecordTarget.TakeOf -> "NEXT TAKE"
                    is RecordTarget.StackLayer -> "TO ${target.label} · NEW LAYER"
                },
                style = T.label, maxLines = 1,
            )
        }
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(clock(frames, audio.sampleRate), style = T.hi.copy(fontSize = 88.sp, letterSpacing = (-4).sp, color = if (paused) C.dim else C.hi))
            if (paused) Text(if (target is RecordTarget.StackLayer) "PAUSED · SILENCE IS WRITTEN SO THE TAKE STAYS LINED UP WITH THE STACK" else "PAUSED · NOTHING IS WRITTEN · RESUME CONTINUES THE SAME TAKE", style = T.label, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
            Gap(24.dp)
            LevelLed(level)
            // Plan §7 Recorder: say when the input clips, and keep saying it for 2 s.
            var clipAt by remember { mutableStateOf(0L) }
            var now by remember { mutableStateOf(0L) }
            LaunchedEffect(level) { now = System.currentTimeMillis(); if (level >= 0.98f) clipAt = now }
            Text(
                if (clipAt > 0 && now - clipAt < 2000) "CLIPPING · MOVE BACK OR PLAY SOFTER" else " ",
                style = T.button.copy(color = C.rec, fontSize = 12.sp), modifier = Modifier.padding(top = 8.dp),
            )
            Gap(18.dp)
            if (target is RecordTarget.StackLayer) {
                // Plan §5.4: say when the speaker will leak the stack into the take.
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val phones = remember { headphonesConnected(ctx) }
                Text(
                    if (phones) "THE STACK IN YOUR HEADPHONES · THE PHONE'S MIC RECORDS YOU · THE TAKE IS LINED UP AFTER"
                    else "THE STACK IS PLAYING ON THE SPEAKER AND WILL BE IN THE TAKE. USE HEADPHONES (WIRED OR BLUETOOTH) TO PLAY ALONG.",
                    style = T.label.copy(lineHeight = 16.sp, color = if (phones) C.text else C.acc), textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            Text(
                "WRITTEN TO THE PHONE AS IT RECORDS · KEEPS GOING WITH THE SCREEN OFF",
                style = T.label.copy(lineHeight = 16.sp), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        Row(Modifier.fillMaxWidth().height(80.dp).drawBehindTop(C.acc)) {
            val marks by audio.markCount.collectAsState()
            // ✕ DELETE (Christopher, 2 Nov 2026): stops and puts the take in the trash, with UNDO; no save sheet.
            Box(
                Modifier.weight(0.8f).fillMaxHeight().background(C.panel).clickable(role = Role.Button) { model.stopRecording(discard = true) }
                    .semantics { contentDescription = "Stop and delete this take" },
                contentAlignment = Alignment.Center,
            ) { Text("✕ DELETE", style = T.button.copy(fontSize = 13.sp, color = C.dim)) }
            Box(Modifier.width(1.dp).fillMaxHeight().background(C.line))
            Box(
                Modifier.weight(0.8f).fillMaxHeight().background(C.panel).clickable(role = Role.Button, enabled = !paused) { audio.markRecording() }
                    .semantics { contentDescription = "Drop a marker here" },
                contentAlignment = Alignment.Center,
            ) { Text(if (marks == 0) "◆ MARK" else "◆ MARK · $marks", style = T.button.copy(fontSize = 13.sp, color = if (paused) C.faint else C.hi)) }
            Box(Modifier.width(1.dp).fillMaxHeight().background(C.line))
            Box(
                Modifier.weight(1f).fillMaxHeight().background(if (paused) C.selected else C.panel).clickable(role = Role.Button) { audio.pauseRecording(!paused) }
                    .semantics { contentDescription = if (paused) "Resume recording" else "Pause recording" },
                contentAlignment = Alignment.Center,
            ) { Text(if (paused) "● RESUME" else "❚❚ PAUSE", style = T.button.copy(fontSize = 14.sp, color = if (paused) C.rec else C.hi)) }
            Box(
                Modifier.weight(1f).fillMaxHeight().background(C.selected)
                    .clickable(role = Role.Button) { model.stopRecording() }.semantics { contentDescription = "Stop recording" },
                contentAlignment = Alignment.Center,
            ) { Text("■ STOP", style = T.button.copy(fontSize = 16.sp)) }
        }
    }
}

/** After a restore: the database is closed, so the app must be reopened. */
@Composable
private fun RestartScreen(message: String) {
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    Column(Modifier.fillMaxSize().background(C.bg).systemBarsPadding().clickable(enabled = false) {}) {
        TitleBar { Text("RESTORE", style = T.title) }
        Text(message, style = T.body.copy(lineHeight = 20.sp, color = C.hi), modifier = Modifier.padding(14.dp).weight(1f))
        Row {
            BigButton("CLOSE APOPHENIA", {
                activity?.finishAndRemoveTask()
                android.os.Process.killProcess(android.os.Process.myPid())
            }, primary = true)
        }
    }
}

/** Under the tools list: mic control (plan §7 puts permission control inside the app) and overdub latency. */
@Composable
fun ToolsExtras(model: AppModel, requestMic: (() -> Unit) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val mic by model.app.audio.mic.collectAsState()
    var granted by remember { mutableStateOf(false) }
    var revoked by remember { mutableStateOf(false) }
    LaunchedEffect(mic) { granted = ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED }
    Column(Modifier.fillMaxWidth()) {

        Kv("MIC") {
            Text("Now: ${mic.name.lowercase()} · permission ${if (revoked) "revoked when the app closes" else if (granted) "allowed" else "not given"}", style = T.body.copy(color = C.hi))
            Text("The microphone is open only while recording or while the tuner is on screen; the chip at the top shows it.", style = T.small, modifier = Modifier.padding(top = 4.dp))
        }
        Row {
            if (!granted) {
                BigButton("ALLOW THE MIC", { requestMic { granted = true } }, primary = true)
            } else if (!revoked && android.os.Build.VERSION.SDK_INT >= 33) {
                BigButton("REVOKE THE MIC PERMISSION", {
                    ctx.revokeSelfPermissionOnKill(android.Manifest.permission.RECORD_AUDIO)
                    revoked = true
                    model.notice("Takes effect when Apophenia closes (Android's rule).")
                })
            }
        }
        OverdubLatency(model, requestMic)

    }
}

@Composable
fun LaterScreen(title: String, milestone: String, what: String) {
    Column(Modifier.fillMaxSize()) {
        TitleBar { Text(title.uppercase(), style = T.title, modifier = Modifier.weight(1f)); Text("ARRIVES IN $milestone", style = T.label) }
        Text(what, style = T.body.copy(lineHeight = 20.sp), modifier = Modifier.padding(14.dp))
    }
}

/** m:ss, or h:mm:ss past an hour. */
fun clock(frames: Long, rate: Int): String {
    if (rate <= 0) return "0:00"
    val s = frames / rate
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun CrashDialog(model: AppModel, text: String) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Box(Modifier.fillMaxSize().background(C.scrim).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(Modifier.padding(16.dp).fillMaxWidth().background(C.panel).border(1.dp, C.acc)) {
            TitleBar { Text("LAST TIME IT CRASHED", style = T.title) }
            Text(text.take(1800), style = T.small.copy(fontSize = 10.sp), modifier = Modifier.padding(12.dp).height(320.dp))
            Row {
                BigButton("COPY", { clipboard.setText(androidx.compose.ui.text.AnnotatedString(text)) })
                BigButton("DISMISS", {
                    com.betterappmafia.apophenia.CrashLog.file(model.app).delete()
                    model.crashText = null
                }, primary = true)
            }
        }
    }
}


/** Wired, USB or Bluetooth headphones are an output right now. */
fun headphonesConnected(ctx: android.content.Context): Boolean {
    val am = ctx.getSystemService(android.media.AudioManager::class.java) ?: return false
    val phones = setOf(
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES, android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
        android.media.AudioDeviceInfo.TYPE_USB_HEADSET, android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        android.media.AudioDeviceInfo.TYPE_BLE_HEADSET,
    )
    return am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).any { it.type in phones }
}

/**
 * Playing along and overdub timing (plan §5.4; Christopher, 26 Oct 2026: headphones to play along, Bluetooth is the
 * standard, you hear your instrument in the room and the take is shifted to line up). Headphones are timed by playing
 * along (clap with clicks; the phone's mic times the claps), per pair; the speaker by its noise bursts. The delay is
 * taken off the start of every layer recorded over a stack.
 */
@Composable
private fun OverdubLatency(model: AppModel, requestMic: (() -> Unit) -> Unit) {
    val audio = model.app.audio
    val sm = model.stacks
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var route by remember { mutableStateOf(audio.route()) }
    var phones by remember { mutableStateOf(audio.headphonesName()) }
    var own by remember { mutableStateOf<Long?>(null) }
    var byRoute by remember { mutableStateOf<Map<com.betterappmafia.apophenia.audio.AudioController.Route, Long>>(emptyMap()) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(busy) {
        route = audio.route(); phones = audio.headphonesName()
        own = sm.latencyFramesOwn(route, phones)
        byRoute = com.betterappmafia.apophenia.audio.AudioController.Route.entries.mapNotNull { r -> sm.latencyFramesOwn(r, null)?.let { r to it } }.toMap()
    }
    val rate = audio.sampleRate.toDouble()
    fun ms(f: Long) = "%.0f ms".format(f * 1000 / rate)
    val headphones = route != com.betterappmafia.apophenia.audio.AudioController.Route.SPEAKER
    Kv("PLAYING\nALONG") {
        Text("Use headphones to play or sing along to a stack, wired or Bluetooth.", style = T.body.copy(color = C.hi))
        Text(
            "You hear your instrument in the room and the stack in your ears; the phone's own mic records you, and each take is moved earlier by these headphones' delay so it lines up with what you heard. On the speaker, the stack plays into the take.",
            style = T.small.copy(lineHeight = 17.sp), modifier = Modifier.padding(top = 4.dp),
        )
    }
    Kv("TIMING") {
        val now = if (headphones) (phones ?: route.label) else "Phone speaker"
        Text("$now: " + (own?.let { ms(it) } ?: "not timed yet"), style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
        if (headphones && own == null) byRoute[route]?.let { Text("Until then: ${ms(it)}, measured on other ${route.label.lowercase()}.", style = T.small) }
        val others = byRoute.filterKeys { it != route }
        if (others.isNotEmpty()) Text("Also measured: " + others.entries.joinToString(" · ") { "${it.key.label} ${ms(it.value)}" }, style = T.small, modifier = Modifier.padding(top = 2.dp))
        Text(
            if (headphones) "Earbuds in, volume up. ${com.betterappmafia.apophenia.core.analysis.PlayAlong.CLICKS} clicks play: clap with each one, from the ${com.betterappmafia.apophenia.core.analysis.PlayAlong.LEAD_IN + 1}th on. The phone's mic times your claps. About 13 seconds; once per pair of headphones."
            else "Volume up, room quiet. Five noise bursts play; the mic times them.",
            style = T.small.copy(lineHeight = 17.sp), modifier = Modifier.padding(top = 6.dp),
        )
        result?.let { Text(it, style = T.small.copy(color = C.acc, lineHeight = 17.sp), modifier = Modifier.padding(top = 4.dp)) }
    }
    Row {
        BigButton(
            when { busy && headphones -> "CLAP WITH EACH CLICK…"; busy -> "MEASURING…"; headphones -> "TIME THESE HEADPHONES"; else -> "TIME THE SPEAKER" },
            {
                requestMic {
                    busy = true
                    result = null
                    scope.launch {
                        if (headphones) {
                            val (name, r) = audio.playAlong()
                            result = r.problem ?: r.ms?.let { m ->
                                val frames = Math.round(m * rate / 1000)
                                sm.saveLatency(route, frames, name)
                                sm.saveLatency(route, frames)   // [Claude] also the fallback for a pair not timed yet
                                "Saved: %.0f ms for %s (%d claps, steady to ±%.0f ms).".format(m, name ?: route.label.lowercase(), r.used, r.spreadMs ?: 0.0)
                            }
                        } else {
                            val c = audio.calibrate()
                            val s = c.summary
                            result = c.problem ?: s?.medianMs?.let { m ->
                                sm.saveLatency(c.route, Math.round(m * rate / 1000))
                                "Saved: %.0f ms on the speaker (spread ±%.1f ms, %d of 5 bursts).".format(m, s.halfSpreadMs ?: 0.0, s.acceptedLagsMs.size)
                            }
                        }
                        busy = false
                    }
                }
            },
            primary = true, enabled = !busy,
        )
    }
}
