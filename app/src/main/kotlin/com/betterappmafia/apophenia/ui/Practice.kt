package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.core.tools.PracticeStats
import com.betterappmafia.apophenia.data.PracticeRow
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The practice timer (plan §7): runs while you practise, pauses, and on save goes into the log with what it was on, the
 * tempo when it was an exercise ([bpm]) and an optional note. Wall-clock based and saved as it changes, so it survives
 * the app being closed. [label] is what the attachment is called on screen. Lives in PRACTICE › EXERCISES
 * (Christopher, 2 Nov 2026).
 */
data class PracticeTimer(
    val runningSince: Long? = null,
    val accumulatedMs: Long = 0,
    val firstStart: Long? = null,
    val scrapId: Long? = null,
    val projectId: Long? = null,
    val exercise: String? = null,
    val label: String = "",
    val bpm: Int? = null,
) {
    val running: Boolean get() = runningSince != null
    fun elapsedMs(now: Long): Long = accumulatedMs + (runningSince?.let { now - it } ?: 0)
    fun start(now: Long) = if (running) this else copy(runningSince = now, firstStart = firstStart ?: now)
    fun pause(now: Long) = if (!running) this else copy(runningSince = null, accumulatedMs = elapsedMs(now))
    val hasTime: Boolean get() = running || accumulatedMs > 0

    fun encode(): String = listOf("2", runningSince ?: "", accumulatedMs, firstStart ?: "", scrapId ?: "", projectId ?: "", (exercise ?: "").replace('|', '/'), label.replace('|', '/'), bpm ?: "").joinToString("|")

    companion object {
        /** Reads version 2, and version 1 (no tempo). */
        fun decode(s: String?): PracticeTimer? = runCatching {
            val f = s!!.split('|')
            require((f.size == 8 && f[0] == "1") || (f.size == 9 && f[0] == "2"))
            PracticeTimer(f[1].toLongOrNull(), f[2].toLong(), f[3].toLongOrNull(), f[4].toLongOrNull(), f[5].toLongOrNull(), f[6].ifEmpty { null }, f[7], f.getOrNull(8)?.toIntOrNull())
        }.getOrNull()
    }
}

/** 0:42, 12:05, 1:02:03. */
fun clockSeconds(s: Long): String = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)

/** Hours and minutes for totals: 0m, 45m, 3h 05m. */
fun hoursMinutes(s: Long): String = if (s < 3600) "${s / 60}m" else "%dh %02dm".format(s / 3600, s / 60 % 60)

/** The practice log added up, kept current. */
@Composable
internal fun practiceStats(model: AppModel): PracticeStats {
    val spans by remember { model.app.db.practice().spans() }.collectAsState(emptyList())
    return remember(spans) { PracticeStats(spans.map { it.startedAt to it.seconds }, ZoneId.systemDefault()) }
}

/** Day streak, this week and this month, large. */
@Composable
internal fun PracticeTotals(stats: PracticeStats) {
    val today = LocalDate.now()
    val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
        listOf(
            Triple("${stats.streak(today)}", "DAY STREAK", "BEST ${stats.bestStreak()}"),
            Triple(hoursMinutes(stats.week(today, firstDay)), "THIS WEEK", ""),
            Triple(hoursMinutes(stats.month(YearMonth.from(today))), "THIS MONTH", "${stats.daysIn(YearMonth.from(today))} DAYS"),
        ).forEach { (big, label, small) ->
            Column(Modifier.weight(1f)) {
                Text(big, style = T.display.copy(fontSize = 30.sp, fontStyle = FontStyle.Normal))
                Text(label, style = T.label)
                if (small.isNotEmpty()) Text(small, style = T.label.copy(color = C.faint))
            }
        }
    }
}

/**
 * The practice timer as a card: the time large, what it's on (▾ to change), START/PAUSE and LOG.
 */
@Composable
internal fun PracticeTimerCard(model: AppModel) {
    val tm = model.tools
    val t = tm.timer
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(t.running) { while (true) { now = System.currentTimeMillis(); delay(250) } }
    val player by model.app.audio.player.collectAsState()
    val projects by model.projects.collectAsState()
    var attach by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp).background(C.panel).border(1.dp, if (t.running) C.acc else C.line)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (t.running) "PRACTICE TIMER · RUNNING" else if (t.hasTime) "PRACTICE TIMER · PAUSED" else "PRACTICE TIMER", style = T.label.copy(color = if (t.running) C.acc else C.dim))
                Text(clockSeconds(t.elapsedMs(now) / 1000), style = T.display.copy(fontSize = 40.sp, fontStyle = FontStyle.Normal, color = if (t.running) C.hi else C.text))
            }
            Box(
                Modifier.height(48.dp).width(96.dp).background(if (t.running) C.acc else C.panel2).border(1.dp, if (t.running) C.acc else C.line2).clickable(role = Role.Button) {
                    val n = System.currentTimeMillis()
                    tm.changeTimer(if (t.running) t.pause(n) else t.start(n))
                },
                contentAlignment = Alignment.Center,
            ) { Text(if (t.running) "❚❚ PAUSE" else if (t.hasTime) "▶ GO ON" else "▶ START", style = T.button.copy(color = if (t.running) C.bg else C.hi)) }
            Box(
                Modifier.padding(start = 6.dp).height(48.dp).width(76.dp).border(1.dp, C.line2).clickable(enabled = t.hasTime, role = Role.Button) { tm.savePractice(note.orEmpty()); note = null },
                contentAlignment = Alignment.Center,
            ) { Text("■ LOG", style = T.button.copy(color = if (t.hasTime) C.hi else C.faint)) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Text(
                    "ON · ${t.label.ifEmpty { "nothing in particular" }}${t.bpm?.let { " · $it BPM" }.orEmpty()} ▾", style = T.small.copy(color = C.hi), maxLines = 1,
                    modifier = Modifier.clickable(role = Role.Button) { attach = true }.padding(vertical = 8.dp),
                )
                ConsoleMenu(attach, { attach = false }, "ATTACH THE TIME TO", buildList {
                    add(MenuItem("Nothing in particular", if (t.label.isEmpty()) "✓" else "") { tm.changeTimer(t.copy(scrapId = null, projectId = null, exercise = null, label = "", bpm = null)) })
                    player?.takeIf { it.takeId > 0 && it.scrapId != REFERENCE_SCRAP }?.let { p ->
                        val title = p.title.substringBeforeLast(" · ")
                        add(MenuItem(title, "IN THE PLAYER") { tm.changeTimer(t.copy(scrapId = p.scrapId, projectId = null, exercise = null, label = title, bpm = null)) })
                    }
                    projects.firstOrNull { it.project.id == model.projectId }?.let { pr ->
                        add(MenuItem(pr.project.name, "THIS PROJECT") { tm.changeTimer(t.copy(scrapId = null, projectId = pr.project.id, exercise = null, label = pr.project.name, bpm = null)) })
                    }
                    add(MenuItem("Something else…", "TYPE IT") { typing = t.exercise ?: "" })
                })
            }
            Text(if (note == null) "+ NOTE" else "", style = T.label.copy(color = C.acc), modifier = Modifier.clickable(enabled = note == null, role = Role.Button) { note = "" }.padding(8.dp))
        }
        typing?.let { s ->
            ConsoleField("WHAT", s, { typing = it.take(80) }, placeholder = "Paradiddles, the solo in bar 12…", onDone = {
                val e = s.trim()
                if (e.isNotEmpty()) tm.changeTimer(t.copy(scrapId = null, projectId = null, exercise = e, label = e, bpm = null))
                typing = null
            })
        }
        note?.let { n -> ConsoleField("NOTE", n, { note = it.take(500) }, placeholder = "saved with the time when you log it") }
    }
}

/**
 * The month as a calendar (Christopher, 2 Nov 2026: "time spent a week and month counter/calendar"): each day shaded by
 * how long you practised, today outlined. Tap a day to see its log.
 */
@Composable
internal fun PracticeCalendar(stats: PracticeStats, month: YearMonth, onMonth: (YearMonth) -> Unit, selected: LocalDate?, onDay: (LocalDate?) -> Unit) {
    val today = LocalDate.now()
    val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Sq("‹", { onMonth(month.minusMonths(1)) })
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + month.year, style = T.display.copy(fontSize = 22.sp))
            Text("${hoursMinutes(stats.month(month))} · ${stats.daysIn(month)} DAYS", style = T.label)
        }
        Sq("›", { onMonth(month.plusMonths(1)) }, enabled = month < YearMonth.from(today))
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
        (0 until 7).forEach { i ->
            Text(firstDay.plus(i.toLong()).getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = T.label, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
    }
    PracticeStats.grid(month, firstDay).forEach { week ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            week.forEach { day ->
                val secs = day?.let { stats.byDay[it] } ?: 0L
                // [Claude] Shades by minutes: under 15, under 30, under 60, an hour or more.
                val fill = when {
                    secs <= 0 -> Color.Transparent
                    secs < 15 * 60 -> C.acc.copy(alpha = 0.25f)
                    secs < 30 * 60 -> C.acc.copy(alpha = 0.5f)
                    secs < 60 * 60 -> C.acc.copy(alpha = 0.75f)
                    else -> C.acc
                }
                Box(
                    Modifier.weight(1f).aspectRatio(1f).padding(vertical = 1.5.dp).background(fill)
                        .border(if (day == today || day == selected) 1.5.dp else 0.5.dp, if (day == selected) C.hi else if (day == today) C.acc else if (day == null) Color.Transparent else C.line)
                        .clickable(enabled = day != null && day <= today, role = Role.Button) { onDay(if (day == selected) null else day) }
                        .semantics { if (day != null) contentDescription = "$day: ${if (secs > 0) hoursMinutes(secs) else "no practice"}" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (day != null) Text("${day.dayOfMonth}", style = T.small.copy(color = if (secs >= 30 * 60) C.bg else if (day > today) C.faint else C.hi, fontSize = 11.sp))
                }
            }
        }
    }
}

/** The log: what was practised, how long, at what tempo, with a note; ✕ takes an entry out (with undo). */
@Composable
internal fun PracticeLogRows(model: AppModel, rows: List<PracticeRow>, empty: String) {
    if (rows.isEmpty()) Text(empty, style = T.small, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
    val fmt = remember { SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault()) }
    rows.forEach { row ->
        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).underline(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 14.dp, top = 6.dp, bottom = 6.dp)) {
                Line1("${clockSeconds(row.practice.seconds)}  ${row.what.ifEmpty { "Practice" }}${row.practice.bpm?.let { " · $it BPM" }.orEmpty()}", T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
                Text(fmt.format(Date(row.practice.startedAt)) + (row.practice.note?.let { " · $it" } ?: ""), style = T.small, maxLines = 2)
            }
            Box(
                Modifier.width(52.dp).height(54.dp).clickable {
                    model.app.io.launch { model.app.db.practice().delete(row.practice.id) }
                    model.notice("Removed from the log.") { model.app.io.launch { model.app.db.practice().add(row.practice.copy(id = 0)) } }
                }.semantics { contentDescription = "Remove from the log" },
                contentAlignment = Alignment.Center,
            ) { Text("✕", style = T.body.copy(color = C.dim)) }
        }
    }
}
