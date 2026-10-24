package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.audio.WavWriter
import com.betterappmafia.apophenia.core.tools.DrumHit
import com.betterappmafia.apophenia.core.tools.DrumKit
import com.betterappmafia.apophenia.core.tools.DrumVoice
import com.betterappmafia.apophenia.core.tools.Groove
import com.betterappmafia.apophenia.core.tools.Rudiment
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import kotlinx.coroutines.launch
import java.io.File

/**
 * Learn (plan §2.1, §8). Course, Ear and most of the Reference arrive in M6; the drum rudiments are
 * here already (Christopher, 22 Oct 2026: reference material, so drummers are included).
 */
@Composable
fun LearnScreen(model: AppModel, requestMic: (() -> Unit) -> Unit) {
    val page = model.learnRudiment?.let { n -> Rudiment.PAS.firstOrNull { it.number == n } }
    if (page != null) { RudimentPage(model, page); return }
    model.learnGroove?.let { id -> Groove.COMMON.firstOrNull { it.id == id } }?.let { GroovePage(model, it); return }
    model.refPage?.let { RefPageView(model, it); return }
    if (model.learnPart == "COURSE") {
        model.coursePage?.let { id -> com.betterappmafia.apophenia.core.learn.Course.page(id) }?.let { LessonView(model, it); return }
        model.courseCat?.let { id -> com.betterappmafia.apophenia.core.learn.Course.category(id) }?.let { CourseTopics(model, it); return }
    }
    if (model.learnPart == "EAR") model.earDrill?.let { id -> com.betterappmafia.apophenia.core.learn.Ear.drill(id) ?: model.earPool?.let { p -> com.betterappmafia.apophenia.core.learn.YourEar.drills(p).firstOrNull { it.id == id } } }?.let { EarDrillView(model, it, requestMic); return }
    LearnHome(model)
}

/** The drum grooves and the 40 rudiments, a Reference list. */
@Composable
internal fun ColumnScope.DrumLists(model: AppModel, grooves: Boolean) {
    if (grooves) {
        Groove.COMMON.groupBy { it.style }.forEach { (style, list) ->
            Text(style.uppercase(), style = T.label.copy(color = C.acc), modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 4.dp))
            list.forEach { g ->
                Row(
                    Modifier.fillMaxWidth().height(58.dp).underline().clickable(role = Role.Button) { model.learnGroove = g.id }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(g.name, style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
                        Line1("${g.grid.meter} · ${g.bpm} BPM · ${g.about}", T.small)
                    }
                    Text("›", style = T.hi.copy(color = C.acc))
                }
            }
        }
    } else {
        Rudiment.PAS.groupBy { it.family }.forEach { (family, list) ->
            Text(family.uppercase(), style = T.label.copy(color = C.acc), modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 4.dp))
            list.forEach { r ->
                Row(
                    Modifier.fillMaxWidth().height(58.dp).underline().clickable(role = Role.Button) { model.learnRudiment = r.number }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("%2d".format(r.number), style = T.label, modifier = Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
                        Line1(r.strokes.filter { it.hand != null }.joinToString(" "), T.small)
                    }
                    Text("›", style = T.hi.copy(color = C.acc))
                }
            }
        }
    }
}

private val TEMPOS = listOf(40, 50, 60, 70, 80, 90, 100, 110, 120, 140, 160, 180, 200)

/** One rudiment: its sticking, and hear it at a tempo, the stroke being played lit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RudimentPage(model: AppModel, r: Rudiment) {
    val audio = model.app.audio
    val player by audio.player.collectAsState()
    var bpm by remember { mutableIntStateOf(80) }
    var tempoMenu by remember { mutableStateOf(false) }
    val title = "${r.name} · $bpm"
    val p = player
    val mine = p != null && p.scrapId == REFERENCE_SCRAP && p.title == title
    val lit = if (mine && p!!.playing) {
        val f = p.position - Rudiment.LEAD_IN * audio.sampleRate
        if (f < 0) -1 else (f / r.slotFrames(bpm.toDouble(), audio.sampleRate)).toInt() % r.strokes.size
    } else -1
    Column(Modifier.fillMaxSize()) {
        TitleBar {
            Sq("‹", { model.learnRudiment = null })
            Text("  ${r.number}. ${r.name.uppercase()}", style = T.title, modifier = Modifier.weight(1f))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("${r.family.uppercase()} · PAS #${r.number}", style = T.label, modifier = Modifier.padding(14.dp))
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                r.strokes.forEachIndexed { i, st ->
                    Column(
                        Modifier.width(40.dp).padding(2.dp).background(if (i == lit) C.selected else Color.Transparent).border(1.dp, if (i == lit) C.acc else C.line),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(if (st.accent) ">" else " ", style = T.label.copy(color = C.acc, fontSize = 11.sp))
                        Text(st.grace.ifEmpty { " " }, style = T.label.copy(fontSize = 9.sp, color = C.dim))
                        Text(st.hand?.toString() ?: "·", style = T.hi.copy(fontSize = 20.sp, color = if (st.hand == null) C.faint else if (i == lit) C.acc else C.hi))
                        Text(if (st.buzz) "z" else " ", style = T.label)
                    }
                }
            }
            Text(
                "> accent · small letters over a stroke are grace notes from that hand (one: a flam, two: a drag) · z buzz · · the note before lasts longer.\n\n" +
                    "${r.slotsPerBeat} strokes to a beat. Rhythm is simplified to equal steps; the sticking, accents and grace notes are the rudiment's.",
                style = T.small.copy(lineHeight = 16.sp), modifier = Modifier.padding(14.dp),
            )
            Box {
                KvText("TEMPO", "$bpm BPM ▾") { tempoMenu = true }
                ConsoleMenu(tempoMenu, { tempoMenu = false }, "TEMPO (A BEAT = ${r.slotsPerBeat} STROKES)", TEMPOS.map { t ->
                    MenuItem("$t BPM", if (t == bpm) "✓" else "") { bpm = t }
                })
            }
        }
        Box(
            Modifier.fillMaxWidth().height(72.dp).background(if (mine && p!!.playing) C.selected else C.panel).drawBehindTop(C.acc)
                .clickable(role = Role.Button) {
                    if (mine) audio.togglePlay() else model.app.io.launch {
                        val f = renderRudiment(model, r, bpm)
                        audio.load(REFERENCE_SCRAP, -1, title, f.absolutePath, play = true)
                    }
                },
            contentAlignment = Alignment.Center,
        ) { Text(if (mine && p!!.playing) "■ STOP" else "▶ HEAR IT", style = T.button.copy(fontSize = 16.sp)) }
    }
}

private val GROOVE_TEMPOS = listOf(50, 60, 70, 76, 80, 90, 92, 96, 100, 110, 116, 120, 130, 140, 160, 180, 200)

/** One groove as drum tab (a row per drum, a column per step), and hear it with the step being played lit. */
@Composable
private fun GroovePage(model: AppModel, g: Groove) {
    val audio = model.app.audio
    val player by audio.player.collectAsState()
    var bpm by remember(g.id) { mutableIntStateOf(g.bpm) }
    var tempoMenu by remember { mutableStateOf(false) }
    val title = "${g.name} · $bpm"
    val p = player
    val mine = p != null && p.scrapId == REFERENCE_SCRAP && p.title == title
    val lit = if (mine && p!!.playing) {
        val beats = (p.position - DrumKit.LEAD_IN * audio.sampleRate) / (audio.sampleRate * 60.0 / bpm)
        if (beats < 0) -1 else g.stepAt(beats)
    } else -1
    val grid = g.grid
    val perBar = grid.beatsPerBar * grid.stepsPerBeat
    Column(Modifier.fillMaxSize()) {
        TitleBar {
            Sq("‹", { model.learnGroove = null })
            Text("  ${g.name.uppercase()}", style = T.title, modifier = Modifier.weight(1f))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("${g.style.uppercase()} · ${grid.meter}${if (g.swing > 0.5) " · SWUNG" else ""}", style = T.label, modifier = Modifier.padding(14.dp))
            Text(g.about, style = T.body.copy(lineHeight = 19.sp), modifier = Modifier.padding(horizontal = 14.dp))
            Gap(12.dp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
                Column {
                    // Beat numbers over the grid.
                    Row {
                        Box(Modifier.width(30.dp))
                        for (i in 0 until grid.steps) {
                            val inBar = i % perBar
                            Text(
                                if (inBar % grid.stepsPerBeat == 0) "${inBar / grid.stepsPerBeat + 1}" else "",
                                style = T.label, modifier = Modifier.width(cellWidth(grid.stepsPerBeat)).padding(start = if (i > 0 && inBar == 0) 6.dp else 0.dp),
                            )
                        }
                    }
                    DrumVoice.entries.filter { it in grid.rows }.forEach { v ->
                        val row = grid.rows.getValue(v)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(v.tab, style = T.label.copy(color = C.dim), modifier = Modifier.width(30.dp))
                            row.forEachIndexed { i, h ->
                                Box(
                                    Modifier.padding(start = if (i > 0 && i % perBar == 0) 6.dp else 0.dp).width(cellWidth(grid.stepsPerBeat)).height(26.dp)
                                        .background(if (i == lit) C.selected else if ((i % perBar) / grid.stepsPerBeat % 2 == 1) C.panel else Color.Transparent)
                                        .border(0.5.dp, if (i == lit) C.acc else C.line),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        h?.char?.toString() ?: "",
                                        style = T.hi.copy(fontSize = 13.sp, color = when (h) { DrumHit.ACCENT -> C.acc; DrumHit.GHOST -> C.dim; else -> C.hi }),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Text(
                "x hit · X accent · g ghost note · o open hi-hat. ${grid.stepsPerBeat} steps to a beat" +
                    (if (g.swing > 0.5) ", the off steps swung to ${(g.swing * 100).toInt()}%." else ".") +
                    "\n\nA generic version of the style, not a transcription of any record.",
                style = T.small.copy(lineHeight = 16.sp), modifier = Modifier.padding(14.dp),
            )
            Box {
                KvText("TEMPO", "$bpm BPM ▾") { tempoMenu = true }
                ConsoleMenu(tempoMenu, { tempoMenu = false }, "TEMPO", (GROOVE_TEMPOS + g.bpm).distinct().sorted().map { t ->
                    MenuItem("$t BPM", if (t == bpm) "✓" else if (t == g.bpm) "TYPICAL" else "") { bpm = t }
                })
            }
        }
        Box(
            Modifier.fillMaxWidth().height(72.dp).background(if (mine && p!!.playing) C.selected else C.panel).drawBehindTop(C.acc)
                .clickable(role = Role.Button) {
                    if (mine) audio.togglePlay() else model.app.io.launch {
                        val f = renderReference(model, "groove-${g.id}-$bpm-v1.wav") { g.render(bpm.toDouble(), model.app.audio.sampleRate, passes = 8 / grid.bars) }
                        audio.load(REFERENCE_SCRAP, -1, title, f.absolutePath, play = true)
                    }
                },
            contentAlignment = Alignment.Center,
        ) { Text(if (mine && p!!.playing) "■ STOP" else "▶ HEAR IT", style = T.button.copy(fontSize = 16.sp)) }
    }
}

private fun cellWidth(stepsPerBeat: Int) = if (stepsPerBeat >= 8) 18.dp else 24.dp

/** Renders reference audio once into the cache (derived, evictable) and keeps it. */
fun renderReference(model: AppModel, name: String, pcm: () -> FloatArray): File {
    val dir = File(model.app.cacheDir, "reference").apply { mkdirs() }
    val f = File(dir, name)
    if (f.length() > 44) return f
    val part = File(dir, "$name.part")
    WavWriter(part, model.app.audio.sampleRate).use { it.write(pcm()) }
    part.renameTo(f)
    return f
}

/** Takes in the player that aren't scraps (Reference audio) carry this id; the player bar doesn't open them. */
const val REFERENCE_SCRAP = -1L

/** Four passes of the rudiment at [bpm], rendered once into the cache and kept there (it's derived, evictable). */
private fun renderRudiment(model: AppModel, r: Rudiment, bpm: Int): File {
    val dir = File(model.app.cacheDir, "reference").apply { mkdirs() }
    val f = File(dir, "rudiment-${r.number}-$bpm-v1.wav")
    if (f.length() > 44) return f
    val part = File(dir, "${f.name}.part")
    val pcm = r.render(bpm.toDouble(), model.app.audio.sampleRate, passes = 4)
    WavWriter(part, model.app.audio.sampleRate).use { it.write(pcm) }
    part.renameTo(f)
    return f
}
