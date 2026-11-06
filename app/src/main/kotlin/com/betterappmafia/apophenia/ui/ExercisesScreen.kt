package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.pitch.PitchClass
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.rhythm.Tempo
import com.betterappmafia.apophenia.core.score.Score
import com.betterappmafia.apophenia.core.score.ScorePlayback
import com.betterappmafia.apophenia.core.tools.CuratedExercise
import com.betterappmafia.apophenia.core.tools.ExerciseFor
import com.betterappmafia.apophenia.core.tools.Exercises
import com.betterappmafia.apophenia.core.tools.Rudiment
import com.betterappmafia.apophenia.data.ExerciseEntity
import com.betterappmafia.apophenia.data.ExerciseRow
import com.betterappmafia.apophenia.data.ScrapEntity
import com.betterappmafia.apophenia.data.ScrapFormat
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Written music of your own that can be an exercise. [Claude] */
private val EXERCISE_FORMATS = listOf(ScrapFormat.TAB, ScrapFormat.SHEET, ScrapFormat.DRUM_CHART, ScrapFormat.CHORD_CHART)

/** The YOURS tab in the instrument row. */
private const val YOURS = "YOURS"

private fun ExerciseRow.title() = scrapTitle?.takeIf { it.isNotBlank() } ?: exercise.curated?.let { Exercises.byId(it)?.name } ?: "Untitled"

/** No tempo is preset: an exercise starts at 60 until you set your own. [Claude] the 60. */
private const val FIRST_BPM = 60

/**
 * PRACTICE › EXERCISES (plan §7; Christopher, 2 Nov 2026: "a day streak and time spent a week and month counter/calendar
 * and a practice log and the practice timer to be in exercises… The tempo raising should be on them to decide"). From
 * the top: your streak, week and month; the practice timer; the exercises by instrument; the month's calendar; the log.
 * An exercise keeps the tempo you set and logs the tempo you practised it at; nothing raises it for you.
 */
@Composable
fun ExercisesScreen(model: AppModel) {
    val rows by remember { model.app.db.exercises().all() }.collectAsState(null)
    model.curatedOpen?.let { id -> Exercises.byId(id)?.let { CuratedDetail(model, it, rows.orEmpty().firstOrNull { r -> r.exercise.curated == id }); return } }
    val open = model.exerciseOpen
    val row = rows?.firstOrNull { it.exercise.id == open }
    if (open != null && row != null) { YourDetail(model, row); return }

    val stats = practiceStats(model)
    val history by remember { model.app.db.practice().history() }.collectAsState(emptyList())
    var month by remember { mutableStateOf(YearMonth.now()) }
    var day by remember { mutableStateOf<LocalDate?>(null) }
    val forWhom = model.exerciseFor ?: ExerciseFor.DRUMS.name

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PracticeTotals(stats)
        PracticeTimerCard(model)

        // Exercises by instrument.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 10.dp, end = 10.dp, top = 18.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (ExerciseFor.entries.map { it.name to it.label.uppercase() } + (YOURS to "YOURS")).forEach { (id, label) ->
                val on = forWhom == id
                Text(
                    label, style = T.label.copy(color = if (on) C.bg else C.hi),
                    modifier = Modifier.background(if (on) C.acc else C.panel).border(1.dp, if (on) C.acc else C.line).clickable(role = Role.Tab) { model.exerciseFor = id }.padding(horizontal = 11.dp, vertical = 10.dp),
                )
            }
        }
        if (forWhom == YOURS) YourExercises(model, rows) else CuratedList(model, ExerciseFor.valueOf(forWhom), rows.orEmpty())

        // The month, then the log for the day picked (or the latest).
        Text("CALENDAR", style = T.label.copy(color = C.acc), modifier = Modifier.padding(start = 14.dp, top = 24.dp, bottom = 6.dp))
        PracticeCalendar(stats, month, { month = it; day = null }, day) { day = it }
        val zone = ZoneId.systemDefault()
        val shown = day?.let { d -> history.filter { java.time.Instant.ofEpochMilli(it.practice.startedAt).atZone(zone).toLocalDate() == d } } ?: history.take(20)
        Text(day?.let { "LOG · ${it.dayOfMonth} ${it.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()).uppercase()} · ${hoursMinutes(stats.byDay[it] ?: 0)}" } ?: "LOG · LATEST",
            style = T.label.copy(color = C.acc), modifier = Modifier.padding(start = 14.dp, top = 18.dp, bottom = 4.dp))
        PracticeLogRows(model, shown, if (day != null) "Nothing logged that day." else "Nothing logged yet. Start the timer, practise, then ■ LOG.")
        Gap(24.dp)
    }
}

/** The curated exercises for one instrument, by family; a long family list opens on its families. */
@Composable
private fun CuratedList(model: AppModel, forWhom: ExerciseFor, rows: List<ExerciseRow>) {
    val list = remember(forWhom) { Exercises.all.filter { it.forWhom == forWhom } }
    val families = list.groupBy { it.family }
    var openFamily by remember(forWhom) { mutableStateOf<String?>(null) }
    val folded = list.size > 15
    val tempoOf = rows.mapNotNull { r -> r.exercise.curated?.let { it to r.exercise.currentBpm } }.toMap()
    families.forEach { (family, items) ->
        val expanded = !folded || openFamily == family
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp).clickable(enabled = folded, role = Role.Button) { openFamily = if (expanded) null else family },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(family.uppercase(), style = T.label.copy(color = C.acc), modifier = Modifier.weight(1f))
            if (folded) Text("${items.size} ${if (expanded) "▴" else "▾"}", style = T.label)
        }
        if (expanded) items.forEach { ex ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).underline().clickable(role = Role.Button) { model.curatedOpen = ex.id }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(ex.name, style = T.display.copy(fontSize = 19.sp))
                    Text(ex.rudiment?.strokes?.joinToString(" ") ?: ex.what, style = T.small, maxLines = 1)
                }
                Text(tempoOf[ex.id]?.let { "$it" } ?: "›", style = T.hi.copy(color = C.acc, fontSize = 13.sp), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
    Text(
        when (forWhom) {
            ExerciseFor.DRUMS -> "The 40 PAS International Drum Rudiments."
            else -> "Standard patterns from the teaching methods; the app spells every note and works out every fret in each key, and tests check them."
        },
        style = T.small.copy(lineHeight = 15.sp), modifier = Modifier.padding(14.dp),
    )
}

@Composable
private fun YourExercises(model: AppModel, rows: List<ExerciseRow>?) {
    var addMenu by remember { mutableStateOf(false) }
    var family by remember { mutableStateOf<ScrapFormat?>(null) }
    var library by remember { mutableStateOf<List<ScrapEntity>>(emptyList()) }
    LaunchedEffect(addMenu) { if (addMenu) library = withContext(Dispatchers.IO) { model.app.db.scraps().library() } }
    fun add(s: ScrapEntity) {
        val bpm = (s.bpmLow?.toInt() ?: FIRST_BPM).coerceIn(20, 400)
        model.app.io.launch {
            val id = model.app.db.exercises().add(ExerciseEntity(scrapId = s.id, startBpm = bpm, goalBpm = bpm, stepBpm = 1, currentBpm = bpm, addedAt = System.currentTimeMillis()))
            if (id == null) model.notice("${displayTitle(s)} is an exercise already.", action = "", undo = null) else model.exerciseOpen = id
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("FROM YOUR LIBRARY", style = T.label.copy(color = C.acc), modifier = Modifier.weight(1f))
        Box {
            Sq("+ ADD ▾", { family = null; addMenu = true })
            val f = family
            ConsoleMenu(
                addMenu, { addMenu = false }, if (f == null) "FROM THE LIBRARY" else formatWord(f),
                if (f == null) EXERCISE_FORMATS.map { fm ->
                    val n = library.count { it.format == fm }
                    MenuItem("${formatWord(fm)} ›", "$n", enabled = n > 0) { family = fm; addMenu = true }
                } else library.filter { it.format == f }.map { s -> MenuItem(displayTitle(s), s.bpmLow?.let { "${it.toInt()} BPM" } ?: "") { addMenu = false; add(s) } },
            )
        }
    }
    val list = rows.orEmpty().filter { it.exercise.scrapId != null }
    if (rows != null && list.isEmpty()) Text("None yet. + ADD picks a tab, sheet music, drum chart or chord chart from your Library.", style = T.body, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
    list.forEach { r ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = 58.dp).underline().clickable(role = Role.Button) { model.exerciseOpen = r.exercise.id }.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(r.title(), style = T.display.copy(fontSize = 19.sp))
                r.scrapFormat?.let { Text(formatWord(ScrapFormat.of(it)), style = T.label) }
            }
            Text("${r.exercise.currentBpm}", style = T.hi.copy(color = C.acc, fontSize = 13.sp))
        }
    }
}

// ---- One exercise ------------------------------------------------------------------------------------------------------

/** Saves [bpm] as the tempo of curated exercise [id] (adding its row the first time). */
private fun saveCuratedTempo(model: AppModel, id: String, row: ExerciseRow?, bpm: Int) {
    model.app.io.launch {
        val now = System.currentTimeMillis()
        if (row == null) model.app.db.exercises().add(ExerciseEntity(curated = id, startBpm = bpm, goalBpm = bpm, stepBpm = 1, currentBpm = bpm, addedAt = now, lastAt = now))
        else model.app.db.exercises().update(row.exercise.copy(startBpm = bpm, goalBpm = bpm, currentBpm = bpm, lastAt = now))
    }
}

@Composable
private fun CuratedDetail(model: AppModel, ex: CuratedExercise, row: ExerciseRow?) {
    val root = model.exerciseRoots[ex.id] ?: ex.defaultRoot
    val score = remember(ex.id, root) { ex.score(root) }
    val keyName = if (ex.inKeys) " · ${KeyMode.displayName(root)}${if (ex.minor) " minor" else ""}" else ""
    ExerciseFrame(
        model, ex.name, "${ex.forWhom.label.uppercase()} · ${ex.family.uppercase()}", row?.exercise?.currentBpm ?: FIRST_BPM, logName = ex.name,
        onTempo = { saveCuratedTempo(model, ex.id, row, it) }, onBack = { model.curatedOpen = null },
        hear = { bpm -> hearExercise(model, ex, score, bpm, root) },
        heardTitle = { bpm -> "${ex.name}$keyName · $bpm" },
    ) { lit ->
        Text(ex.what, style = T.body.copy(lineHeight = 19.sp), modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
        if (ex.inKeys) {
            var keyMenu by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Sq("KEY · ${KeyMode.displayName(root)}${if (ex.minor) "m" else ""} ▾", { keyMenu = true })
                    ConsoleMenu(keyMenu, { keyMenu = false }, "KEY", ex.roots.map { r ->
                        MenuItem(KeyMode.displayName(r) + if (ex.minor) " minor" else " major", if (r == root) "✓" else "") { model.exerciseRoots = model.exerciseRoots + (ex.id to r) }
                    })
                }
                // Voice goes up a half step at a time; everything else round the circle of fifths.
                val next = if (ex.forWhom == ExerciseFor.VOICE) PitchClass(root.pitchClass + 1).defaultSpelling() else ex.roots[(ex.roots.indexOf(root).coerceAtLeast(0) + 1) % ex.roots.size]
                Sq(if (ex.forWhom == ExerciseFor.VOICE) "UP A HALF STEP ›" else "NEXT KEY · ${KeyMode.displayName(next)} ›", { model.exerciseRoots = model.exerciseRoots + (ex.id to next) })
            }
        }
        ex.rudiment?.let { RudimentStrokes(it, lit) }
        score?.let { ExerciseMusic(model, it) }
        Text("SOURCE · ${ex.source}", style = T.small.copy(lineHeight = 15.sp), modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

@Composable
private fun YourDetail(model: AppModel, row: ExerciseRow) {
    val e = row.exercise
    val name = row.title()
    var more by remember { mutableStateOf(false) }
    ExerciseFrame(
        model, name, row.scrapFormat?.let { formatWord(ScrapFormat.of(it)).uppercase() } ?: "YOURS", e.currentBpm, logName = name, scrapId = e.scrapId,
        onTempo = { bpm -> model.app.io.launch { model.app.db.exercises().update(e.copy(startBpm = bpm, goalBpm = bpm, currentBpm = bpm, lastAt = System.currentTimeMillis())) } },
        onBack = { model.exerciseOpen = null }, hear = null, heardTitle = { "" },
        menu = {
            Box {
                Sq("⋯", { more = true })
                ConsoleMenu(more, { more = false }, "EXERCISE", listOf(
                    MenuItem("Remove from Exercises", "the music stays in the Library") {
                        more = false; model.exerciseOpen = null
                        model.app.io.launch { model.app.db.exercises().delete(e.id) }
                    },
                ))
            }
        },
    ) {
        e.scrapId?.let { id ->
            Kv("THE MUSIC", onClick = { model.open(id) }, mark = false) {
                Text("$name ›", style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
                Text("OPENS IN ITS EDITOR", style = T.label)
            }
        }
    }
}

/**
 * What every exercise screen has: its music ([content], given the stroke lit while a rudiment plays), YOUR TEMPO (set by
 * you, never raised for you), ▶ HEAR and ▶ CLICK at that tempo, the timer started on it, and its own log.
 */
@Composable
private fun ExerciseFrame(
    model: AppModel, name: String, kind: String, savedBpm: Int, logName: String, scrapId: Long? = null,
    onTempo: (Int) -> Unit, onBack: () -> Unit, hear: ((Int) -> Unit)?, heardTitle: (Int) -> String,
    menu: @Composable () -> Unit = {}, content: @Composable (lit: Int) -> Unit,
) {
    val tm = model.tools
    val audio = model.app.audio
    val click by audio.click.collectAsState()
    val player by audio.player.collectAsState()
    var bpm by remember(name) { mutableStateOf(savedBpm) }
    LaunchedEffect(savedBpm) { bpm = savedBpm }
    var typing by remember { mutableStateOf<String?>(null) }
    fun set(v: Int) {
        val n = v.coerceIn(20, 400)
        bpm = n; onTempo(n)
        if (click.on) audio.setClick(true, n.toDouble())
        if (tm.timer.exercise == logName) tm.changeTimer(tm.timer.copy(bpm = n))
    }
    val history by remember { model.app.db.practice().history() }.collectAsState(emptyList())
    val mine = history.filter { it.practice.exercise == logName || (scrapId != null && it.practice.scrapId == scrapId) }
    val p = player
    val playing = p != null && p.scrapId == REFERENCE_SCRAP && p.title == heardTitle(bpm) && p.playing
    // While a rudiment plays, the stroke sounding.
    val ex = model.curatedOpen?.let { Exercises.byId(it) }
    val lit = ex?.rudiment?.let { r -> if (playing) ((p!!.position - Rudiment.LEAD_IN * audio.sampleRate) / r.slotFrames(bpm.toDouble(), audio.sampleRate)).toInt().takeIf { it >= 0 }?.rem(r.strokes.size) ?: -1 else -1 } ?: -1
    Column(Modifier.fillMaxSize()) {
        TitleBar {
            Sq("‹", onBack)
            Text(kind, style = T.label, modifier = Modifier.weight(1f).padding(start = 10.dp))
            menu()
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(name, style = T.display, modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp))
            content(lit)
            // Your tempo: yours to set; logged with the time.
            Text("YOUR TEMPO", style = T.label.copy(color = C.acc), modifier = Modifier.padding(start = 14.dp, top = 16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Sq("−5", { set(bpm - 5) }); Sq("−", { set(bpm - 1) })
                Text(
                    "$bpm", style = T.display.copy(fontSize = 54.sp, fontStyle = FontStyle.Normal), textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clickable(role = Role.Button) { typing = "$bpm" }.semantics { contentDescription = "Your tempo $bpm BPM. Tap to type." },
                )
                Sq("+", { set(bpm + 1) }); Sq("+5", { set(bpm + 5) })
            }
            typing?.let { t ->
                ConsoleField("TYPE BPM", t, { typing = it.filter(Char::isDigit).take(3) }, number = true, onDone = { t.toIntOrNull()?.let(::set); typing = null })
            }
            Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hear != null) BigButton(if (playing) "■ STOP" else "▶ HEAR AT $bpm", { if (playing) audio.togglePlay() else hear(bpm) }, primary = playing)
                val onHere = click.on && click.bpm.toInt() == bpm
                BigButton(if (onHere) "■ CLICK" else "▶ CLICK AT $bpm", { audio.setClick(!onHere, bpm.toDouble()) }, primary = onHere)
            }
            val timing = tm.timer.exercise == logName
            Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton(
                    when {
                        timing && tm.timer.running -> "❚❚ PAUSE TIMER"
                        timing && tm.timer.hasTime -> "▶ GO ON"
                        else -> "◷ TIME IT"
                    },
                    {
                        val t = System.currentTimeMillis()
                        when {
                            timing && tm.timer.running -> tm.changeTimer(tm.timer.pause(t))
                            timing -> tm.changeTimer(tm.timer.start(t))
                            tm.timer.hasTime -> model.notice("The timer has time on ${tm.timer.label.ifBlank { "something else" }}: log it first (■ LOG on the Exercises page).", action = "", undo = null)
                            else -> tm.changeTimer(PracticeTimer(scrapId = scrapId, exercise = logName, label = logName, bpm = bpm).start(t))
                        }
                    },
                    primary = timing && tm.timer.running,
                )
                BigButton("■ LOG", { tm.savePractice("") }, enabled = timing && tm.timer.hasTime)
            }
            if (timing) PracticeTimerCard(model)
            Text("YOUR LOG", style = T.label.copy(color = C.acc), modifier = Modifier.padding(start = 14.dp, top = 18.dp, bottom = 2.dp))
            val fastest = mine.mapNotNull { it.practice.bpm }.maxOrNull()
            Text(
                "${hoursMinutes(mine.sumOf { it.practice.seconds })} IN ${mine.size} ${if (mine.size == 1) "TIME" else "TIMES"}" + (fastest?.let { " · FASTEST LOGGED $it BPM" }.orEmpty()),
                style = T.label, modifier = Modifier.padding(start = 14.dp, bottom = 4.dp),
            )
            PracticeLogRows(model, mine.take(30), "Nothing logged on this yet: ◷ TIME IT, practise, then ■ LOG.")
            Gap(24.dp)
        }
    }
}

/** The exercise's music: each part on its staff, with tab under a fretted part. */
@Composable
private fun ExerciseMusic(model: AppModel, score: Score) {
    score.parts.forEach { part ->
        if (score.parts.size > 1) Text(part.name.uppercase(), style = T.label, modifier = Modifier.padding(start = 14.dp, top = 8.dp))
        NotationCanvas(score, part, null, null, emptySet(), 0, model.chordDialect, model.chordStyle) { _, _, _, _, _ -> }
        if (part.fretted) TabPreview(score, part)
    }
}

/** Plays a rudiment on the snare, or the written exercise on the piano, at [bpm]. */
private fun hearExercise(model: AppModel, ex: CuratedExercise, score: Score?, bpm: Int, root: PitchName) {
    val keyName = if (ex.inKeys) " · ${KeyMode.displayName(root)}${if (ex.minor) " minor" else ""}" else ""
    val title = "${ex.name}$keyName · $bpm"
    ex.rudiment?.let { r ->
        model.app.io.launch {
            val f = renderRudiment(model, r, bpm)
            model.app.audio.load(REFERENCE_SCRAP, -1, title, f.absolutePath, play = true)
        }
        return
    }
    val s = score?.copy(tempo = Tempo(bpm.toDouble())) ?: return
    hearEar(model, "exercise-${ex.id}-$root-$bpm", title, com.betterappmafia.apophenia.core.learn.EarAudio(s.parts.flatMap { ScorePlayback.notes(s, it) }))
}
