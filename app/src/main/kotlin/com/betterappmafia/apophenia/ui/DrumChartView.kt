package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.core.tools.DrumChart
import com.betterappmafia.apophenia.core.tools.DrumHit
import com.betterappmafia.apophenia.core.tools.DrumKit
import com.betterappmafia.apophenia.core.tools.DrumVoice
import com.betterappmafia.apophenia.data.ScrapEntity
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import kotlinx.coroutines.launch

/** [Claude] The tempo a drum chart plays at when it has no BPM yet. */
const val DRUM_CHART_DEFAULT_BPM = 100

private val STEP_NAMES = mapOf(1 to "QUARTERS", 2 to "EIGHTHS", 3 to "TRIPLETS", 4 to "SIXTEENTHS", 6 to "SEXTUPLETS", 8 to "THIRTY-SECONDS")
private val SWINGS = listOf(50, 54, 58, 62, 67)

/**
 * A drum chart (plan §3.1) as a grid: tap a cell to go rest, x, X, g (and o on the hi-hat). Meter,
 * steps, bars and swing are menus; the tempo is the scrap's BPM. Every change is written back to the
 * chart's text in [state], so it saves and undoes like the other written scraps, and TEXT shows that text.
 */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun ColumnScope.DrumChartView(model: AppModel, s: ScrapEntity, state: TextFieldState) {
    val audio = model.app.audio
    val chart = remember(state.text) { DrumChart.parse(state.text.toString()) }
    val grid = chart.grid
    fun put(c: DrumChart) = state.edit { replace(0, length, c.text()) }
    val bpm = s.bpmLow?.toInt() ?: DRUM_CHART_DEFAULT_BPM
    val player by audio.player.collectAsState()
    val title = "${displayTitle(s)} · $bpm"
    val p = player
    val mine = p != null && p.scrapId == REFERENCE_SCRAP && p.title == title
    val lit = if (mine && p!!.playing) {
        val beats = (p.position - DrumKit.LEAD_IN * audio.sampleRate) / (audio.sampleRate * 60.0 / bpm)
        if (beats < 0) -1 else chart.groove.stepAt(beats)
    } else -1
    var menu by remember { mutableStateOf<String?>(null) }
    var drumMenu by remember { mutableStateOf<DrumVoice?>(null) }
    val voices = DrumVoice.entries.filter { it in grid.rows }

    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Sq("UNDO", { state.undoState.undo() }, enabled = state.undoState.canUndo)
        Sq("REDO", { state.undoState.redo() }, enabled = state.undoState.canRedo)
    }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Box {
            KvText("METER", "${grid.meter} ▾") { menu = "meter" }
            ConsoleMenu(menu == "meter", { menu = null }, "METER", DrumChart.METERS.map { m ->
                MenuItem("$m", if (m == grid.meter) "✓" else "") { put(chart.copy(grid = grid.resized(meter = m))) }
            })
        }
        Box {
            KvText("STEPS", "${STEP_NAMES[grid.stepsPerBeat]} · ${grid.stepsPerBeat} A BEAT ▾") { menu = "steps" }
            ConsoleMenu(menu == "steps", { menu = null }, "STEPS TO A BEAT", DrumChart.STEPS.map { n ->
                MenuItem("${STEP_NAMES[n]!!.lowercase().replaceFirstChar { it.uppercase() }}", if (n == grid.stepsPerBeat) "✓" else "$n A BEAT") {
                    put(chart.copy(grid = grid.resized(stepsPerBeat = n), swing = if (n % 2 == 0) chart.swing else 0.5))
                }
            })
        }
        Box {
            KvText("BARS", "${grid.bars} ▾") { menu = "bars" }
            ConsoleMenu(menu == "bars", { menu = null }, "BARS", (1..DrumChart.MAX_BARS).map { n ->
                MenuItem("$n", if (n == grid.bars) "✓" else if (n < grid.bars) "DROPS BARS ${n + 1}–${grid.bars}" else "") { put(chart.copy(grid = grid.resized(bars = n))) }
            })
        }
        if (grid.stepsPerBeat % 2 == 0) Box {
            val sw = (chart.swing * 100).toInt()
            KvText("SWING", "${if (sw <= 50) "STRAIGHT" else "$sw%"} ▾") { menu = "swing" }
            ConsoleMenu(menu == "swing", { menu = null }, "SWING · WHERE THE OFF STEP FALLS", SWINGS.map { v ->
                MenuItem(if (v == 50) "Straight" else "$v%", if (v == sw) "✓" else if (v == 67) "TRIPLET" else "") { put(chart.copy(swing = v / 100.0)) }
            })
        }
        KvText("TEMPO", "$bpm BPM${if (s.bpmLow == null) " (NOT SET)" else ""} ›") { model.sheet = Sheet.Bpm(s.id) }
        chart.problems.take(3).forEach {
            Text(it, style = T.small.copy(color = C.acc), modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
        }
        if (chart.problems.isNotEmpty()) Text(
            "Editing the grid rewrites the text without what couldn't be read.",
            style = T.small, modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
        )
        Gap(8.dp)
        // One bar to a line, beat numbers over it; it fills the width, or scrolls when the steps are too fine.
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
            val per = grid.stepsPerBar
            val cell = min(32.dp, ((maxWidth - 34.dp) / per).coerceAtLeast(16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Column {
                    for (bar in 0 until grid.bars) {
                        Row {
                            Text("${bar + 1}", style = T.label.copy(color = C.acc), modifier = Modifier.width(34.dp))
                            for (k in 0 until per) Text(
                                if (k % grid.stepsPerBeat == 0) "${k / grid.stepsPerBeat + 1}" else "",
                                style = T.label, modifier = Modifier.width(cell),
                            )
                        }
                        voices.forEach { v ->
                            val row = grid.rows.getValue(v)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box {
                                    Text(
                                        v.tab, style = T.label.copy(color = C.text),
                                        modifier = Modifier.width(34.dp).height(30.dp).clickable { drumMenu = v }.padding(top = 8.dp)
                                            .semantics { contentDescription = v.label },
                                    )
                                    if (bar == 0) ConsoleMenu(drumMenu == v, { drumMenu = null }, v.label.uppercase(), listOf(
                                        MenuItem("Clear the ${v.label.lowercase()}", "ALL BARS") { put(chart.copy(grid = grid.withoutVoice(v).withVoice(v))) },
                                        MenuItem("Remove the ${v.label.lowercase()}", "FROM THE CHART", enabled = voices.size > 1) { put(chart.copy(grid = grid.withoutVoice(v))) },
                                    ))
                                }
                                for (k in 0 until per) {
                                    val i = bar * per + k
                                    val h = row[i]
                                    Box(
                                        Modifier.width(cell).height(30.dp)
                                            .background(if (i == lit) C.selected else if (k / grid.stepsPerBeat % 2 == 1) C.panel else Color.Transparent)
                                            .border(0.5.dp, if (i == lit) C.acc else C.line)
                                            .clickable(role = Role.Button) { put(chart.copy(grid = grid.cycled(v, i))) }
                                            .semantics { contentDescription = "${v.label}, bar ${bar + 1}, step ${k + 1}: ${h?.name?.lowercase() ?: "rest"}" },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            h?.char?.toString() ?: "",
                                            style = T.hi.copy(fontSize = 14.sp, color = when (h) { DrumHit.ACCENT -> C.acc; DrumHit.GHOST -> C.dim; else -> C.hi }),
                                        )
                                    }
                                }
                            }
                        }
                        Gap(10.dp)
                    }
                }
            }
        }
        Box {
            val missing = DrumVoice.entries.filter { it !in grid.rows }
            KvText("ADD A DRUM", "${missing.size} MORE ▾") { menu = "add" }
            ConsoleMenu(menu == "add", { menu = null }, "ADD A DRUM", missing.map { v ->
                MenuItem(v.label, v.tab) { put(chart.copy(grid = grid.withVoice(v))) }
            })
        }
        Text(
            "Tap a cell: x hit · X accent · g ghost note · o open hi-hat · empty is a rest. Tap a drum's name to clear or remove it.",
            style = T.small.copy(lineHeight = 16.sp), modifier = Modifier.padding(14.dp),
        )
    }
    Box(
        Modifier.fillMaxWidth().height(72.dp).background(if (mine && p!!.playing) C.selected else C.panel).drawBehindTop(C.acc)
            .clickable(role = Role.Button) {
                if (mine) audio.togglePlay() else model.app.io.launch {
                    val text = chart.text()
                    val f = renderReference(model, "chart-${Integer.toHexString(text.hashCode())}-$bpm-v1.wav") {
                        chart.groove.render(bpm.toDouble(), audio.sampleRate, passes = (8 / grid.bars).coerceAtLeast(1))
                    }
                    audio.load(REFERENCE_SCRAP, -1, title, f.absolutePath, play = true)
                }
            },
        contentAlignment = Alignment.Center,
    ) { Text(if (mine && p!!.playing) "■ STOP" else "▶ HEAR IT", style = T.button.copy(fontSize = 16.sp)) }
}
