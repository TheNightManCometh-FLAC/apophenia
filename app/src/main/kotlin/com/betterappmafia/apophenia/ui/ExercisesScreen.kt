package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.core.tools.TempoLadder
import com.betterappmafia.apophenia.data.ExerciseEntity
import com.betterappmafia.apophenia.data.ExerciseRow
import com.betterappmafia.apophenia.data.ScrapEntity
import com.betterappmafia.apophenia.data.ScrapFormat
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Written music that can be an exercise. [Claude] */
private val EXERCISE_FORMATS = listOf(ScrapFormat.TAB, ScrapFormat.SHEET, ScrapFormat.DRUM_CHART, ScrapFormat.CHORD_CHART)

private fun ExerciseEntity.ladder() = TempoLadder(startBpm, goalBpm, stepBpm, currentBpm, bestBpm)
private fun ExerciseEntity.with(l: TempoLadder, now: Long = System.currentTimeMillis()) =
    copy(startBpm = l.start, goalBpm = l.goal, stepBpm = l.step, currentBpm = l.current, bestBpm = l.best, lastAt = now)

private fun ExerciseRow.title() = scrapTitle?.takeIf { it.isNotBlank() } ?: exercise.curated ?: "Untitled"

/**
 * PRACTICE › EXERCISES (plan §7: "a library of curated exercises; contents chosen by Christopher at the end"). The
 * framework: a piece of written music practised on a tempo ladder (start, goal, step; up a step after each clean run,
 * best clean tempo kept), with the click at the ladder's tempo and the practice timer attached to it. Until the
 * curated set ships, any tab, sheet music, drum chart or chord chart in the Library can be made an exercise. [Claude]
 */
@Composable
fun ExercisesScreen(model: AppModel) {
    val rows by remember { model.app.db.exercises().all() }.collectAsState(null)
    val open = model.exerciseOpen
    val row = rows?.firstOrNull { it.exercise.id == open }
    if (open != null && row != null) { ExerciseDetail(model, row); return }

    var addMenu by remember { mutableStateOf(false) }
    var family by remember { mutableStateOf<ScrapFormat?>(null) }
    var library by remember { mutableStateOf<List<ScrapEntity>>(emptyList()) }
    LaunchedEffect(addMenu) { if (addMenu) library = withContext(Dispatchers.IO) { model.app.db.scraps().library() } }
    fun add(s: ScrapEntity) {
        val goal = (s.bpmLow?.toInt() ?: 120).coerceIn(TempoLadder.MIN, TempoLadder.MAX)
        // [Claude] Start at 60% of the written tempo, 4 BPM a step.
        val start = (goal * 0.6).toInt().coerceIn(TempoLadder.MIN, goal)
        model.app.io.launch {
            val id = model.app.db.exercises().add(ExerciseEntity(scrapId = s.id, startBpm = start, goalBpm = goal, stepBpm = 4, currentBpm = start, addedAt = System.currentTimeMillis()))
            if (id == null) model.notice("${displayTitle(s)} is an exercise already.", action = "", undo = null) else model.exerciseOpen = id
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "Written music to practise on a tempo ladder: start slow, go up a step after each clean run, aim for the goal. The click follows the ladder, and the practice timer logs the time against the exercise.",
            style = T.small.copy(lineHeight = 16.sp), modifier = Modifier.padding(14.dp),
        )
        Text("CURATED", style = T.label.copy(color = C.acc), modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 4.dp))
        Text("None yet: the curated exercises come with a later update.", style = T.body, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("YOURS", style = T.label.copy(color = C.acc), modifier = Modifier.weight(1f))
            Box {
                Sq("+ ADD ▾", { family = null; addMenu = true })
                val f = family
                ConsoleMenu(
                    addMenu, { addMenu = false }, if (f == null) "FROM THE LIBRARY" else formatWord(f),
                    if (f == null) EXERCISE_FORMATS.map { fm ->
                        val n = library.count { it.format == fm }
                        MenuItem("${formatWord(fm)} ›", "$n", enabled = n > 0) { family = fm }
                    } else library.filter { it.format == f }.map { s -> MenuItem(displayTitle(s), s.bpmLow?.let { "${it.toInt()} BPM" } ?: "") { addMenu = false; add(s) } },
                )
            }
        }
        val list = rows.orEmpty()
        if (rows != null && list.isEmpty()) Text("None yet. + ADD picks a tab, sheet music, drum chart or chord chart from your Library.", style = T.body, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
        for (r in list) {
            val e = r.exercise
            Row(
                Modifier.fillMaxWidth().heightIn(min = 58.dp).underline().clickable(role = Role.Button) { model.exerciseOpen = e.id }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(r.title(), style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
                    Text(
                        listOfNotNull(r.scrapFormat?.let { formatWord(ScrapFormat.of(it)) }, "${e.startBpm} → ${e.goalBpm} BPM", e.bestBpm?.let { "best $it" }).joinToString(" · "),
                        style = T.label,
                    )
                }
                Text(if (e.ladder().reached) "GOAL ✓" else "${e.currentBpm}", style = T.button.copy(color = if (e.ladder().reached) C.acc else C.hi))
            }
        }
    }
}

@Composable
private fun ExerciseDetail(model: AppModel, row: ExerciseRow) {
    val e = row.exercise
    val l = e.ladder()
    val click by model.app.audio.click.collectAsState()
    val tm = model.tools
    val name = row.title()
    fun save(n: TempoLadder) {
        model.app.io.launch { model.app.db.exercises().update(e.with(n)) }
        // The click follows the ladder while it's on.
        if (click.on && n.current != l.current) model.app.audio.setClick(true, n.current.toDouble())
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val timing = tm.timer.exercise == name && tm.timer.scrapId == e.scrapId
    LaunchedEffect(tm.timer.running) { while (tm.timer.running) { now = System.currentTimeMillis(); delay(250) } }
    var more by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TitleBar {
            Sq("‹", { model.exerciseOpen = null })
            Text(name.uppercase(), style = T.title, modifier = Modifier.weight(1f))
            Box {
                Sq("⋯", { more = true })
                ConsoleMenu(more, { more = false }, "EXERCISE", listOf(
                    MenuItem("Start the ladder again", "from ${e.startBpm} BPM; keeps the best") { more = false; save(l.copy(current = l.start)) },
                    MenuItem("Forget the best", e.bestBpm?.let { "$it BPM" } ?: "none yet", enabled = e.bestBpm != null) { more = false; save(l.copy(best = null)) },
                    MenuItem("Remove from Exercises", "the music stays in the Library") {
                        more = false; model.exerciseOpen = null
                        model.app.io.launch { model.app.db.exercises().delete(e.id) }
                    },
                ))
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            e.scrapId?.let { id ->
                Kv("THE MUSIC", onClick = { model.open(id) }) {
                    Text("$name ›", style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
                    row.scrapFormat?.let { Text(formatWord(ScrapFormat.of(it)) + " · OPENS IN ITS EDITOR", style = T.label) }
                }
            }
            // The ladder: where you are, then the three moves.
            Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${l.current}", style = T.hi.copy(fontSize = 64.sp, color = if (l.reached) C.acc else C.hi))
                Text(
                    "BPM · GOAL ${l.goal}" + (l.best?.let { " · BEST CLEAN $it" } ?: "") + if (l.reached) " · GOAL REACHED" else "",
                    style = T.label, textAlign = TextAlign.Center,
                )
            }
            Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton("↓ STEP", { save(l.down()) }, enabled = l.current > TempoLadder.MIN)
                BigButton("CLEAN ↑", { save(l.clean()) }, primary = true)
            }
            Text(
                "CLEAN ↑ after a run with no mistakes: it counts as your best and the ladder goes up ${l.step} BPM. A run that wasn't clean: just play it again.",
                style = T.small.copy(lineHeight = 15.sp), modifier = Modifier.padding(14.dp),
            )
            Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val onHere = click.on && click.bpm.toInt() == l.current
                BigButton(if (onHere) "■ CLICK" else "▶ CLICK AT ${l.current}", { model.app.audio.setClick(!onHere, l.current.toDouble()) }, primary = onHere)
                BigButton(
                    if (timing && tm.timer.running) "■ ${clockSeconds(tm.timer.elapsedMs(now) / 1000)} · LOG" else "◷ TIME IT",
                    {
                        val t = System.currentTimeMillis()
                        when {
                            timing && tm.timer.running -> tm.savePractice("")
                            timing -> tm.changeTimer(tm.timer.start(t))
                            tm.timer.running || tm.timer.elapsedMs(t) > 0 -> model.notice("The practice timer has time on ${tm.timer.label.ifBlank { "something else" }}: log it in Tools › Practice timer first.", action = "", undo = null)
                            else -> tm.changeTimer(PracticeTimer(scrapId = e.scrapId, exercise = name, label = name).start(t))
                        }
                    },
                    primary = timing && tm.timer.running,
                )
            }
            Text("THE LADDER", style = T.label.copy(color = C.acc), modifier = Modifier.padding(start = 14.dp, top = 22.dp, bottom = 4.dp))
            BpmRow("START", "${l.start} BPM", "Changing it starts the ladder again from there.", { save(l.withStart(l.start - 1)) }, { save(l.withStart(l.start + 1)) })
            BpmRow("GOAL", "${l.goal} BPM", "", { save(l.withGoal(l.goal - 1)) }, { save(l.withGoal(l.goal + 1)) })
            BpmRow("STEP", "${l.step} BPM", "How far up each clean run takes you.", { save(l.withStep(l.step - 1)) }, { save(l.withStep(l.step + 1)) })
            e.scrapId?.let { id ->
                val secs by remember(id) { model.app.db.practice().secondsOnScrap(id) }.collectAsState(0L)
                KvText("TIME LOGGED", if (secs == 0L) "None yet" else clockSeconds(secs))
            }
        }
    }
}

@Composable
private fun BpmRow(label: String, value: String, note: String, minus: () -> Unit, plus: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).underline().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = T.label)
            Text(value, style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
            if (note.isNotEmpty()) Text(note, style = T.small)
        }
        Sq("−", minus)
        Box(Modifier.width(8.dp))
        Sq("+", plus)
    }
}
