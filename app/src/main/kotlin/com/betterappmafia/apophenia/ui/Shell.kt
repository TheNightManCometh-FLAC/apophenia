package com.betterappmafia.apophenia.ui

import androidx.activity.compose.BackHandler
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
        // Stand mode takes the whole screen (plan §4.4); recording, sheets and dialogs still sit over it.
        val stand = model.standProject
        if (stand != null) Box(Modifier.fillMaxSize().systemBarsPadding()) { StandMode(model, stand, requestMic) { model.standProject = null } }
        else Column(Modifier.fillMaxSize().systemBarsPadding()) {
            TopBar(model, mic)
            ClickRecBar(model, mic, requestMic)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val detail = model.detail
                when {
                    model.correcting != null -> CorrectionScreen(model, model.correcting!!)
                    detail != null -> ScrapDetail(model, detail, requestMic)
                    model.showStack -> StackScreen(model)
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
            Transport(model)
            NavBar(model)
        }
        // In stand mode the sheet stays up while recording (plan §4.4: the lyric you're singing stays visible).
        if (mic == Mic.RECORDING && model.standProject == null) RecordingOverlay(model)
        SheetHost(model)
        model.crashText?.let { CrashDialog(model, it) }
        model.restartMessage?.let { RestartScreen(it) }
    }

    BackHandler(enabled = model.sheet != Sheet.None || model.correcting != null || model.detail != null || model.showTrash || model.showNotices || model.showSettings || model.showStack || model.selecting || model.performanceOpen || (model.mode == Mode.EXERCISES && model.exerciseOpen != null) || (model.mode == Mode.TOOLS && model.tools.screen != null) || (model.mode.learn && (model.learnRudiment != null || model.learnGroove != null))) {
        when {
            model.sheet != Sheet.None -> model.sheet = Sheet.None
            model.correcting != null -> model.correcting = null
            model.selecting -> model.endSelect()
            model.detail != null -> model.detail = null
            model.showStack -> model.showStack = false
            model.showNotices -> model.showNotices = false
            model.showSettings -> model.showSettings = false
            model.performanceOpen -> model.performanceOpen = false
            model.mode == Mode.EXERCISES && model.exerciseOpen != null -> model.exerciseOpen = null
            model.mode == Mode.TOOLS && model.tools.screen != null -> model.tools.screen = null
            model.mode.learn && model.learnGroove != null -> model.learnGroove = null
            model.mode.learn && model.learnRudiment != null -> model.learnRudiment = null
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
        Box(
            Modifier.weight(0.85f).fillMaxHeight()
                .clickable(role = Role.Button) { if (recording) model.stopRecording() else requestMic { model.startRecording() } }
                .semantics { contentDescription = if (recording) "Stop recording" else "Record to the Library" },
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
        Row(Modifier.fillMaxWidth().height(56.dp).background(C.panel).drawBehindTop(C.line)) {
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable { model.showStack = true; model.detail = null }.padding(horizontal = 12.dp)
                    .semantics { contentDescription = "${stack.title}. Open the stack." },
                verticalArrangement = Arrangement.Center,
            ) {
                Line1("${stack.title} · ${stack.layers} LAYER${if (stack.layers == 1) "" else "S"}".uppercase(), T.label)
                Text("${if (stack.playing) "▶" else "■"} ${clock(stack.position, model.app.audio.sampleRate)} / ${clock(stack.length, model.app.audio.sampleRate)}", style = T.hi.copy(fontSize = 13.sp))
            }
            Box(
                Modifier.width(60.dp).fillMaxHeight().drawBehind { drawLine(C.line, Offset(0f, 0f), Offset(0f, size.height), 1f) }
                    .clickable(enabled = stack.layers > 0, role = Role.Button) { model.app.audio.stackToggle() }
                    .semantics { contentDescription = if (stack.playing) "Stop the stack" else "Play the stack" },
                contentAlignment = Alignment.Center,
            ) { Text(if (stack.playing) "■" else "▶", style = T.hi.copy(fontSize = 20.sp, color = if (stack.layers == 0) C.faint else C.acc)) }
        }
    }
}

@Composable
private fun TakeRow(model: AppModel, p: com.betterappmafia.apophenia.audio.Loaded?, slim: Boolean) {
    Row(Modifier.fillMaxWidth().height(if (slim) 42.dp else 56.dp).background(C.panel).drawBehindTop(C.line)) {
        Column(
            Modifier.weight(1f).fillMaxHeight().clickable(enabled = p != null && p.scrapId != REFERENCE_SCRAP) { p?.let { model.open(it.scrapId) } }.padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            if (p == null) {
                Text("PLAYER", style = T.label)
                Text("Nothing loaded · ▶ a take in the Library", style = T.small)
            } else if (slim) {
                Line1("${if (p.playing) "▶" else "■"} ${clock(p.position, p.rate)}  ${p.title.uppercase()}", T.label)
            } else {
                Line1(p.title.uppercase() + if (p.playback.isPlain) "" else " · ${p.playback.label}", T.label)
                Text("${if (p.playing) "▶" else "■"} ${clock(p.position, p.rate)} / ${clock(p.frames, p.rate)}", style = T.hi.copy(fontSize = 13.sp))
            }
        }
        Box(
            Modifier.width(60.dp).fillMaxHeight().drawBehind { drawLine(C.line, Offset(0f, 0f), Offset(0f, size.height), 1f) }
                .clickable(enabled = p != null, role = Role.Button) { model.app.audio.togglePlay() }
                .semantics { contentDescription = if (p?.playing == true) "Stop" else "Play" },
            contentAlignment = Alignment.Center,
        ) { Text(if (p?.playing == true) "■" else "▶", style = T.hi.copy(fontSize = if (slim) 16.sp else 20.sp, color = if (p == null) C.faint else C.hi)) }
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
    val target = model.recordTarget
    Column(Modifier.fillMaxSize().background(C.bg).systemBarsPadding().clickable(enabled = false) {}) {
        Row(Modifier.fillMaxWidth().height(40.dp).underline().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(C.rec, CircleShape))
            Text("  RECORDING", style = T.button.copy(color = C.rec), modifier = Modifier.weight(1f))
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
            Text(clock(frames, audio.sampleRate), style = T.hi.copy(fontSize = 88.sp, letterSpacing = (-4).sp))
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
            Box(
                Modifier.weight(1f).fillMaxHeight().background(C.panel).clickable(role = Role.Button) { audio.markRecording() }
                    .semantics { contentDescription = "Drop a marker here" },
                contentAlignment = Alignment.Center,
            ) { Text(if (marks == 0) "◆ MARK" else "◆ MARK · $marks", style = T.button.copy(fontSize = 14.sp)) }
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
