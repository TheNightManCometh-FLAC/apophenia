package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The practice timer (plan §7): runs while you practise, pauses, and on save goes into the log with
 * what it was on and an optional note. Wall-clock based and saved as it changes, so it survives the
 * app being closed. [label] is what the attachment is called on screen.
 */
data class PracticeTimer(
    val runningSince: Long? = null,
    val accumulatedMs: Long = 0,
    val firstStart: Long? = null,
    val scrapId: Long? = null,
    val projectId: Long? = null,
    val exercise: String? = null,
    val label: String = "",
) {
    val running: Boolean get() = runningSince != null
    fun elapsedMs(now: Long): Long = accumulatedMs + (runningSince?.let { now - it } ?: 0)
    fun start(now: Long) = if (running) this else copy(runningSince = now, firstStart = firstStart ?: now)
    fun pause(now: Long) = if (!running) this else copy(runningSince = null, accumulatedMs = elapsedMs(now))

    fun encode(): String = listOf("1", runningSince ?: "", accumulatedMs, firstStart ?: "", scrapId ?: "", projectId ?: "", (exercise ?: "").replace('|', '/'), label.replace('|', '/')).joinToString("|")

    companion object {
        fun decode(s: String?): PracticeTimer? = runCatching {
            val f = s!!.split('|')
            require(f.size == 8 && f[0] == "1")
            PracticeTimer(f[1].toLongOrNull(), f[2].toLong(), f[3].toLongOrNull(), f[4].toLongOrNull(), f[5].toLongOrNull(), f[6].ifEmpty { null }, f[7])
        }.getOrNull()
    }
}

/** 0:42, 12:05, 1:02:03. */
fun clockSeconds(s: Long): String = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)

@Composable
fun PracticeScreen(model: AppModel) {
    val tm = model.tools
    val t = tm.timer
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(t.running) { while (true) { now = System.currentTimeMillis(); delay(250) } }
    val history by remember { model.app.db.practice().history() }.collectAsState(emptyList())
    val dayStart = remember(now / 60_000) { Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis }
    val today by remember(dayStart) { model.app.db.practice().secondsSince(dayStart) }.collectAsState(0L)
    val week by remember(dayStart) { model.app.db.practice().secondsSince(dayStart - 6L * 24 * 3600 * 1000) }.collectAsState(0L)
    val player by model.app.audio.player.collectAsState()
    val projects by model.projects.collectAsState()
    var attach by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        TitleBar { Sq("‹", { tm.screen = null }); Text("  PRACTICE", style = T.title, modifier = Modifier.weight(1f)); Text("TODAY ${clockSeconds(today)} · 7 DAYS ${clockSeconds(week)}", style = T.label) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(clockSeconds(t.elapsedMs(now) / 1000), style = T.hi.copy(fontSize = 72.sp, letterSpacing = (-2).sp, color = if (t.running) C.hi else C.text), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 18.dp))
            Text(if (t.running) "RUNNING" else if (t.accumulatedMs > 0) "PAUSED" else "READY", style = T.label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
            Box {
                KvText("ON", "${t.label.ifEmpty { "Nothing in particular" }} ▾") { attach = true }
                ConsoleMenu(attach, { attach = false }, "ATTACH THE TIME TO", buildList {
                    add(MenuItem("Nothing in particular", if (t.label.isEmpty()) "✓" else "") { tm.changeTimer(t.copy(scrapId = null, projectId = null, exercise = null, label = "")) })
                    player?.takeIf { it.takeId > 0 && it.scrapId != REFERENCE_SCRAP }?.let { p ->
                        val title = p.title.substringBeforeLast(" · ")
                        add(MenuItem(title, "IN THE PLAYER") { tm.changeTimer(t.copy(scrapId = p.scrapId, projectId = null, exercise = null, label = title)) })
                    }
                    projects.firstOrNull { it.project.id == model.projectId }?.let { pr ->
                        add(MenuItem(pr.project.name, "THIS PROJECT") { tm.changeTimer(t.copy(scrapId = null, projectId = pr.project.id, exercise = null, label = pr.project.name)) })
                    }
                    add(MenuItem("An exercise…", "TYPE IT") { typing = t.exercise ?: "" })
                })
            }
            typing?.let { s ->
                ConsoleField("EXERCISE", s, { typing = it.take(80) }, placeholder = "Paradiddles at 100, major scales in 3rds…", onDone = {
                    val e = s.trim()
                    if (e.isNotEmpty()) tm.changeTimer(t.copy(scrapId = null, projectId = null, exercise = e, label = e))
                    typing = null
                })
            }
            ConsoleField("NOTE", note, { note = it.take(500) }, placeholder = "optional, saved with the time")
            Gap(10.dp)
            Text("HISTORY", style = T.label, modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 4.dp))
            if (history.isEmpty()) Text("Nothing logged yet.", style = T.small, modifier = Modifier.padding(horizontal = 14.dp))
            val fmt = remember { SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault()) }
            history.forEach { row ->
                Row(Modifier.fillMaxWidth().height(54.dp).underline(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Line1("${clockSeconds(row.practice.seconds)}  ${row.what.ifEmpty { "Practice" }}", T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
                        Line1(fmt.format(Date(row.practice.startedAt)) + (row.practice.note?.let { " · $it" } ?: ""), T.small)
                    }
                    Box(
                        Modifier.width(52.dp).fillMaxHeight().clickable {
                            model.app.io.launch { model.app.db.practice().delete(row.practice.id) }
                            model.notice("Removed from the log.") { model.app.io.launch { model.app.db.practice().add(row.practice.copy(id = 0)) } }
                        }.semantics { contentDescription = "Remove from the log" },
                        contentAlignment = Alignment.Center,
                    ) { Text("✕", style = T.body.copy(color = C.dim)) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(72.dp).drawBehindTop(C.acc)) {
            Box(
                Modifier.weight(1f).fillMaxHeight().background(if (t.running) C.selected else C.panel).clickable(role = Role.Button) {
                    val n = System.currentTimeMillis()
                    tm.changeTimer(if (t.running) t.pause(n) else t.start(n))
                },
                contentAlignment = Alignment.Center,
            ) { Text(if (t.running) "❚❚ PAUSE" else if (t.accumulatedMs > 0) "▶ GO ON" else "▶ START", style = T.button.copy(fontSize = 15.sp, color = if (t.running) C.acc else C.hi)) }
            Box(
                Modifier.weight(1f).fillMaxHeight().clickable(enabled = t.running || t.accumulatedMs > 0, role = Role.Button) { tm.savePractice(note); note = "" },
                contentAlignment = Alignment.Center,
            ) { Text("■ STOP & LOG", style = T.button.copy(fontSize = 15.sp, color = if (t.running || t.accumulatedMs > 0) C.hi else C.faint)) }
        }
    }
}
