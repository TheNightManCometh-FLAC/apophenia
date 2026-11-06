package com.betterappmafia.apophenia.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.core.learn.*
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T

/** [AppModel.earIntervalType] for Intervals › Your picks. */
internal const val EAR_PICKS = "picks"

internal fun earPercent(correct: Int, total: Int): String = if (total <= 0) "—" else String.format(java.util.Locale.getDefault(), "%.1f%%", correct * 100.0 / total)

/**
 * Ear's modes (Christopher, 2 Nov 2026: "They are somewhat building on each other in complexity… Those can be features of
 * the ear training. Separate modes"): PATH, the drills in their order with the ones passed marked; WEAKEST, a drill's
 * own answers as a map, missed most to sure, and drill the ones you miss; QUICK, a short check of what you've passed;
 * TEST; SCORES.
 */
private val EAR_MODES = listOf("PATH" to "PATH", "WEAKEST" to "WEAKEST", "QUICK" to "QUICK CHECK", "TEST" to "TEST", "SCORES" to "SCORES")

/** Each item's recent answers, kept current. */
@Composable
internal fun earStats(model: AppModel): Map<String, EarItemStat> {
    val rows by remember { model.app.db.earAnswers().recent() }.collectAsState(emptyList())
    return remember(rows) { EarMastery.stats(rows.map { EarAnswerAt(it.item, it.correct, it.at) }) }
}

/** What an item is called: "m6", or "m6 · Harmonic" for a focused interval. */
internal fun earItemLabel(item: String): String = runCatching {
    val (d, a) = Ear.source(EarDrill("", "", "", EarKind.INTERVAL, emptyList()), item)
    Ear.label(d, a) + (d.direction?.let { " · " + EarPractice.title(it) }.orEmpty())
}.getOrDefault(item.substringAfter('|'))

@Composable
internal fun EarHome(model: AppModel) {
    var section by model::earSection
    if (EAR_MODES.none { it.first == section }) section = "PATH"
    val stats = earStats(model)
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        EAR_MODES.forEach { (id, label) ->
            val on = section == id
            Box(
                Modifier.weight(if (id == "QUICK" || id == "WEAKEST") 1.3f else 1f).height(40.dp)
                    .background(if (on) C.acc else C.panel).border(1.dp, if (on) C.acc else C.line)
                    .clickable(role = Role.Tab) { section = id },
                contentAlignment = Alignment.Center,
            ) { Text(label, style = T.label.copy(color = if (on) C.bg else C.hi, fontSize = 8.5.sp), textAlign = TextAlign.Center, maxLines = 1) }
        }
    }
    when (section) {
        "WEAKEST" -> EarWeakest(model, stats)
        "QUICK" -> EarQuick(model, stats)
        "SCORES" -> EarScores(model)
        else -> EarPath(model, stats)
    }
}

@Composable
private fun ModeNote(text: String) = Text(text, style = T.small.copy(lineHeight = 15.sp), modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))

/** The drills a chapter's PATH shows: interval types in both presentations, the others as they are. */
private fun pathDrills(chapter: String): List<EarDrill> =
    if (chapter == "Intervals") EarPractice.types.flatMap { t -> EarPractice.directions.map { EarPractice.withDirection(t, it) } }
    else Ear.drills.filter { it.chapter == chapter && !it.sung && it.kind != EarKind.TAPBACK }

/** A thin bar: how much of something is done. */
@Composable
private fun PassBar(done: Int, of: Int, modifier: Modifier = Modifier) {
    Row(modifier.height(3.dp).background(C.line)) {
        if (done > 0) Box(Modifier.weight(done.toFloat()).fillMaxHeight().background(C.acc))
        if (of - done > 0) Box(Modifier.weight((of - done).toFloat()).fillMaxHeight())
    }
}

@Composable
private fun EarPath(model: AppModel, stats: Map<String, EarItemStat>) {
    var family by model::earFamily
    var group by model::earDirectionGroup
    var type by model::earIntervalType
    val streaks by model.app.db.earResults().streaks().collectAsState(emptyList())
    val tests by model.app.db.earResults().bests().collectAsState(emptyList())
    val streakOf = streaks.associateBy { it.drill }
    val testOf = tests.associateBy { it.drill }
    val test = model.earSection == "TEST"
    val validCount = model.earTestQuestionsText.toIntOrNull()?.let { it > 0 } == true
    fun start(d: EarDrill) { model.startEar(d, if (test) EarMode.TEST else EarMode.PRACTICE) }
    if (test) {
        ConsoleField("QUESTIONS", model.earTestQuestionsText, model::editEarTestQuestions, number = true)
        Text(if (validCount) "A completed test counts its first answers. Practice keeps your longest streak." else "Enter a whole number greater than zero.", style = T.small, modifier = Modifier.padding(12.dp))
    }
    val f = family
    if (f == null) {
        if (!test) ModeNote("The drills in order, each building on the one before. A drill is passed when every answer in it is right ${(EarMastery.SURE * 100).toInt()}% of its last ${EarMastery.WINDOW} times (at least ${EarMastery.SURE_MIN}).")
        (Ear.chapters + YourEar.CHAPTER + "Mixed").forEachIndexed { i, chapter ->
            val drills = if (chapter == "Mixed" || chapter == YourEar.CHAPTER) emptyList() else pathDrills(chapter)
            val passed = drills.count { EarMastery.passed(it, stats) }
            Row(Modifier.fillMaxWidth().heightIn(min = 66.dp).underline().clickable(role = Role.Button, enabled = !test || validCount) {
                if (chapter == "Mixed") start(Ear.mixed(null)) else { family = chapter; group = null; type = null }
            }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (chapter == "Mixed" || chapter == YourEar.CHAPTER) "·" else "${i + 1}", style = T.display.copy(fontSize = 26.sp, color = if (drills.isNotEmpty() && passed == drills.size) C.acc else C.hi), modifier = Modifier.width(38.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (chapter == "Mixed") "Mixed: everything" else chapter, style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
                    Text(when {
                        drills.isNotEmpty() -> "$passed OF ${drills.size} PASSED"
                        chapter == "Mixed" -> "EVERY DRILL"
                        else -> "YOUR OWN SCRAPS"
                    }, style = T.label)
                    if (drills.isNotEmpty()) PassBar(passed, drills.size, Modifier.fillMaxWidth(0.6f).padding(top = 5.dp))
                }
                if (chapter == "Mixed") EarLessonLink(model, Ear.mixed(null))
                Text("›", style = T.title.copy(color = C.acc))
            }
        }
        return
    }
    fun back() {
        when {
            f == "Intervals" && group != null -> group = null
            f == "Intervals" && type != null -> type = null
            else -> { family = null; group = null; type = null }
        }
    }
    BackHandler { back() }
    val base = if (f == "Intervals") EarPractice.types.firstOrNull { it.id == type } else null
    TitleBar {
        Sq("‹", { back() })
        Text(
            when {
                f != "Intervals" -> f
                type == EAR_PICKS -> "Your picks" + (group?.let { " · " + EarPractice.title(it) } ?: "")
                base != null -> base.name
                else -> f
            },
            style = T.title, modifier = Modifier.weight(1f), maxLines = 2,
        )
    }
    fun score(d: EarDrill) = if (test) testOf[d.id]?.bestTotal?.let { "BEST ${earPercent(testOf[d.id]?.bestCorrect ?: 0, it)} · ${testOf[d.id]?.bestCorrect}/$it" } ?: "NO COMPLETED TEST" else null
    // Intervals (Christopher, 30 Oct 2026): the interval type first, then how it's played, melodic or harmonic, each its
    // own drill and score.
    if (f == "Intervals") {
        when {
            type == null -> {
                EarPractice.types.forEachIndexed { i, d ->
                    val both = EarPractice.directions.map { EarPractice.withDirection(d, it) }
                    val passed = both.count { EarMastery.passed(it, stats) }
                    Row(Modifier.fillMaxWidth().heightIn(min = 66.dp).underline().clickable(role = Role.Button) { type = d.id; group = null }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("1.${i + 1}", style = T.label.copy(color = if (passed == both.size) C.acc else C.dim), modifier = Modifier.width(38.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.name + if (passed == both.size) "  ✓" else "", style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
                            Text(d.answers.joinToString(" · ") { Ear.label(d, it) }, style = T.label, maxLines = 2)
                        }
                        Text("›", style = T.title.copy(color = C.acc))
                    }
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 66.dp).underline().clickable(role = Role.Button) { type = EAR_PICKS; group = null }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Your picks", style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
                        Text("CHOOSE THE INTERVALS ONE BY ONE", style = T.label)
                    }
                    Text("›", style = T.title.copy(color = C.acc))
                }
                EarDrillRow(model, Ear.drills.first { it.id == "iv-sing" }, 0, null, !test || validCount, ::start, stats)
            }
            type == EAR_PICKS && group == null -> EarPractice.directions.forEach { direction ->
                Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).underline().clickable(role = Role.Button) { group = direction }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(EarPractice.title(direction), style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                    Text("›", style = T.title.copy(color = C.acc))
                }
            }
            type == EAR_PICKS -> PickYourOwn(model, f, group)
            base != null -> EarPractice.directions.forEach { direction ->
                val d = EarPractice.withDirection(base, direction)
                EarDrillRow(model, d, streakOf[d.id]?.best ?: 0, score(d), !test || validCount, ::start, stats, title = EarPractice.title(direction))
            }
        }
        return
    }
    val own = f == YourEar.CHAPTER
    if (own) {
        val pool by produceState(model.earPool, f) { value = loadPool(model).also { model.earPool = it } }
        pool?.let { YourEar.missing(it).forEach { note -> Text(note, style = T.small, modifier = Modifier.padding(12.dp)) } }
            ?: Text("Looking through your material…", style = T.small, modifier = Modifier.padding(12.dp))
    }
    val rows = when {
        own -> model.earPool?.let { YourEar.drills(it) }.orEmpty()
        else -> listOf(Ear.mixed(f)) + Ear.drills.filter { it.chapter == f }
    }
    rows.forEach { d -> EarDrillRow(model, d, streakOf[d.id]?.best ?: 0, score(d), !test || validCount, ::start, stats) }
    if (!own) PickYourOwn(model, f, null)
}

@Composable
private fun EarDrillRow(model: AppModel, drill: EarDrill, streak: Int, testScore: String?, enabled: Boolean, start: (EarDrill) -> Unit, stats: Map<String, EarItemStat>, title: String? = null) {
    val items = EarMastery.items(drill)
    val passed = EarMastery.passedCount(drill, stats)
    val all = passed == items.size && items.isNotEmpty() && drill.pool == null
    Row(Modifier.fillMaxWidth().heightIn(min = 66.dp).underline().clickable(role = Role.Button, enabled = enabled) { start(drill) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text((title ?: drill.name.substringBefore(" · Melodic").substringBefore(" · Harmonic").substringBefore(" · Mixed")) + if (all) "  ✓" else "", style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
            Text(testScore ?: if (streak > 0) "BEST $streak IN A ROW" else drill.note.ifEmpty { drill.answers.joinToString(" · ") { Ear.label(drill, it) } }, style = T.label, maxLines = 2)
            if (drill.pool == null && !drill.sung && passed > 0) {
                Text(if (all) "PASSED" else "$passed OF ${items.size} ANSWERS SURE", style = T.label.copy(color = C.acc), modifier = Modifier.padding(top = 3.dp))
            }
        }
        EarLessonLink(model, drill)
    }
}

// ---- Weakest: one drill's answers as a map ----------------------------------------------------------------------------

/** Accuracy as a colour: red where it's missed most, the accent where it's sure. */
private fun shade(s: EarItemStat?): Color = when {
    s == null || s.answered < EarMastery.WEAK_MIN -> Color.Transparent
    s.accuracy >= EarMastery.SURE -> C.acc
    s.accuracy >= 0.75 -> C.acc.copy(alpha = 0.55f)
    s.accuracy >= 0.6 -> C.acc.copy(alpha = 0.28f)
    else -> C.rec.copy(alpha = 0.5f)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EarWeakest(model: AppModel, stats: Map<String, EarItemStat>) {
    // The drills you've answered, the most recent first; each drill's map is its own answers.
    val drills = remember(stats) {
        stats.values.groupBy { it.item.substringBefore('|') }
            .mapNotNull { (id, s) -> Ear.drill(id)?.takeIf { !it.sung && it.kind != EarKind.TAPBACK && !id.startsWith("mixed") }?.let { it to s.maxOf { x -> x.lastAt } } }
            .sortedByDescending { it.second }.map { it.first }
    }
    ModeNote("One drill's answers, coloured by how often you get each right lately: red is missed most. Drill the ones you miss, or tap answers to pick your own.")
    if (drills.isEmpty()) {
        Text("Nothing answered yet. Practise a drill on the PATH and its answers show up here.", style = T.body, modifier = Modifier.padding(14.dp))
        return
    }
    val selected = drills.firstOrNull { it.id == model.earMapDrill } ?: drills.first()
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        drills.forEach { d ->
            val on = d.id == selected.id
            Text(
                d.name.uppercase(), style = T.label.copy(color = if (on) C.bg else C.hi),
                modifier = Modifier.background(if (on) C.acc else C.panel).border(1.dp, if (on) C.acc else C.line).clickable(role = Role.Tab) { model.earMapDrill = d.id }.padding(horizontal = 10.dp, vertical = 9.dp),
            )
        }
    }
    val items = EarMastery.items(selected)
    var picked by remember(selected.id) { mutableStateOf(setOf<String>()) }
    Text(selected.name, style = T.display.copy(fontSize = 22.sp), modifier = Modifier.padding(start = 14.dp, top = 10.dp))
    Text("LAST ${EarMastery.WINDOW} ANSWERS OF EACH", style = T.label, modifier = Modifier.padding(start = 14.dp, bottom = 8.dp))
    FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        items.forEach { item ->
            val s = stats[item]
            val bg = shade(s)
            val dark = s != null && s.answered >= EarMastery.WEAK_MIN && s.accuracy >= 0.75
            val on = item in picked
            Column(
                Modifier.width(74.dp).height(62.dp).background(bg).border(if (on) 2.dp else 1.dp, if (on) C.hi else C.line2)
                    .clickable(role = Role.Checkbox) { picked = if (on) picked - item else picked + item }
                    .semantics { contentDescription = "${earItemLabel(item)}: ${s?.let { "${it.right} of ${it.answered} right" } ?: "not answered"}" }
                    .padding(6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(Ear.label(selected, item.substringAfter('|')), style = T.body.copy(color = if (dark) C.bg else C.hi, fontWeight = FontWeight.Bold), maxLines = 1)
                Text(s?.let { "${(it.accuracy * 100).toInt()}% · ${it.answered}" } ?: "—", style = T.label.copy(color = if (dark) C.bg else C.dim))
            }
        }
    }
    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(C.rec.copy(alpha = 0.5f))); Text("MISSED MOST", style = T.label)
        Box(Modifier.size(12.dp).background(C.acc)); Text("SURE", style = T.label)
        Box(Modifier.size(12.dp).border(1.dp, C.line2)); Text("TOO FEW YET", style = T.label)
    }
    val weakest = remember(selected.id, stats) {
        val w = EarMastery.weakest(selected, stats)
        // A drill of one answer has nothing to tell it from: add the next least sure, so there's a choice to make.
        if (w.size == 1) w + (items - w.toSet()).sortedBy { stats[it]?.accuracy ?: 2.0 }.take(1) else w
    }
    Row(Modifier.padding(top = 6.dp)) {
        BigButton(
            if (weakest.isEmpty()) "NOTHING MISSED OFTEN YET" else "DRILL MY WEAKEST · " + weakest.joinToString(" ") { Ear.label(selected, it.substringAfter('|')) },
            { model.startEar(EarMastery.drillOf(weakest), EarMode.PRACTICE, record = false) },
            primary = true, enabled = weakest.size >= 2,
        )
    }
    Row(Modifier.padding(top = 6.dp)) {
        BigButton(if (picked.size < 2) "TAP TWO OR MORE TO PICK" else "DRILL THESE ${picked.size}", { model.startEar(EarMastery.drillOf(items.filter { it in picked }), EarMode.PRACTICE, record = false) }, enabled = picked.size >= 2)
        BigButton("THE WHOLE DRILL", { model.startEar(selected, EarMode.PRACTICE) })
    }
}

// ---- Quick check ---------------------------------------------------------------------------------------------------

@Composable
private fun EarQuick(model: AppModel, stats: Map<String, EarItemStat>) {
    val review = remember(stats) { EarMastery.review(stats) }
    val sure = remember(stats) { stats.values.count { EarMastery.sure(it) && EarMastery.askable(it.item) } }
    ModeNote("A short check on what you've already passed, the answers checked longest ago first, so they stay passed. Anything you miss drops off the passed list and shows up in WEAKEST.")
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text("$sure", style = T.display.copy(fontSize = 54.sp))
        Text("ANSWERS PASSED", style = T.label)
    }
    if (review.size < 2) {
        Text("Pass at least two answers first: an answer is passed once it's right ${(EarMastery.SURE * 100).toInt()}% of its last ${EarMastery.WINDOW} times, over at least ${EarMastery.SURE_MIN}.", style = T.body.copy(lineHeight = 19.sp), modifier = Modifier.padding(14.dp))
        return
    }
    Text("THIS CHECK ASKS FROM", style = T.label, modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 4.dp))
    review.groupBy { it.substringBefore('|') }.forEach { (id, list) ->
        Row(Modifier.fillMaxWidth().underline().padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(Ear.drill(id)?.name ?: id, style = T.small, modifier = Modifier.weight(1f))
            Text(list.joinToString(" ") { Ear.label(Ear.drill(id)!!, it.substringAfter('|')) }, style = T.body.copy(color = C.hi))
        }
    }
    Row(Modifier.padding(top = 12.dp)) {
        BigButton("START · ${EarMastery.QUICK} QUESTIONS", { model.startEar(EarMastery.drillOf(review), EarMode.TEST, questions = EarMastery.QUICK, record = false) }, primary = true)
    }
}

// ---- Scores ----------------------------------------------------------------------------------------------------------

@Composable
private fun EarScores(model: AppModel) {
    val streaks by model.app.db.earResults().streaks().collectAsState(emptyList())
    val tests by model.app.db.earResults().bests().collectAsState(emptyList())
    val streakOf = streaks.associateBy { it.drill }
    val testOf = tests.associateBy { it.drill }
    fun resolve(id: String) = Ear.drill(id) ?: model.earPool?.let { YourEar.drills(it).firstOrNull { d -> d.id == id } }
    val ids = (streaks.map { it.drill } + tests.filter { it.bestTotal != null }.map { it.drill }).distinct()
    if (ids.isEmpty()) Text("No scores yet. Complete a test for a percentage record, or practise for your longest run in a row.", style = T.body, modifier = Modifier.padding(14.dp))
    ids.forEach { id ->
        val drill = resolve(id)
        val best = testOf[id]
        Row(Modifier.fillMaxWidth().underline().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(drill?.name ?: id, style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
                Text(best?.bestTotal?.let { "TEST BEST ${earPercent(best.bestCorrect ?: 0, it)} · ${best.bestCorrect}/$it QUESTIONS" } ?: "TEST · NO COMPLETED TEST", style = T.label)
                Text("PRACTICE BEST ${streakOf[id]?.best ?: 0} IN A ROW", style = T.label.copy(color = C.acc))
            }
            if (drill != null) EarLessonLink(model, drill)
        }
    }
}

@Composable
internal fun EarLessonLink(model: AppModel, drill: EarDrill, answer: String? = null, root: PitchName? = null) {
    val pages = EarLearning.pages(drill, answer).mapNotNull(Course::page)
    var menu by remember(drill.id, answer) { mutableStateOf(false) }
    Box {
        Sq(if (pages.size == 1) "LEARN ›" else "LEARN ▾", {
            if (pages.size == 1) model.openEarLearning(pages.single().id, root) else menu = true
        })
        ConsoleMenu(menu, { menu = false }, "RELATED LEARNING", pages.map { page -> MenuItem(page.title, "›") { model.openEarLearning(page.id, root) } })
    }
}
