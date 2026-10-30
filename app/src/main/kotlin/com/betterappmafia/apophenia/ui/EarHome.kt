package com.betterappmafia.apophenia.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.betterappmafia.apophenia.core.learn.*
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T

internal fun earPercent(correct: Int, total: Int): String = if (total <= 0) "—" else String.format(java.util.Locale.getDefault(), "%.1f%%", correct * 100.0 / total)

@Composable
internal fun EarHome(model: AppModel) {
    var section by model::earSection
    var family by model::earFamily
    var group by model::earDirectionGroup
    val streaks by model.app.db.earResults().streaks().collectAsState(emptyList())
    val tests by model.app.db.earResults().bests().collectAsState(emptyList())
    val streakOf = streaks.associateBy { it.drill }
    val testOf = tests.associateBy { it.drill }
    val test = section == "TEST"
    val validCount = model.earTestQuestionsText.toIntOrNull()?.let { it > 0 } == true
    fun start(d: EarDrill) { model.startEar(d, if (test) EarMode.TEST else EarMode.PRACTICE) }
    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("PRACTICE", "TEST", "HIGH SCORES").forEach { s -> Sq(s, { section = s }, on = section == s) }
    }
    if (test) {
        ConsoleField("QUESTIONS", model.earTestQuestionsText, model::editEarTestQuestions, number = true)
        Text(if (validCount) "A completed test counts its first answers. Practice keeps your longest streak." else "Enter a whole number greater than zero.", style = T.small, modifier = Modifier.padding(12.dp))
    }
    fun resolve(id: String) = Ear.drill(id) ?: model.earPool?.let { YourEar.drills(it).firstOrNull { d -> d.id == id } }
    if (section == "HIGH SCORES") {
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
        return
    }
    val f = family
    if (f == null) {
        (Ear.chapters + YourEar.CHAPTER + "Mixed").forEach { chapter ->
            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).underline().clickable(role = Role.Button, enabled = !test || validCount) {
                if (chapter == "Mixed") start(Ear.mixed(null)) else { family = chapter; group = null }
            }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (chapter == "Mixed") "Mixed: everything" else chapter, style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
                    Text(when (chapter) { "Intervals" -> "MELODIC UP · MELODIC DOWN · HARMONIC · MIXED"; "Mixed" -> "EVERY DRILL"; YourEar.CHAPTER -> "YOUR OWN SCRAPS"; else -> "FOCUSED DRILLS AND YOUR OWN PICKS" }, style = T.label)
                }
                if (chapter == "Mixed") EarLessonLink(model, Ear.mixed(null))
                Text("›", style = T.title.copy(color = C.acc))
            }
        }
        return
    }
    fun back() { if (f == "Intervals" && group != null) group = null else { family = null; group = null } }
    BackHandler { back() }
    TitleBar {
        Sq("‹", { back() })
        Text(if (f == "Intervals" && group != null) EarPractice.title(group!!) else f, style = T.title, modifier = Modifier.weight(1f), maxLines = 2)
    }
    if (f == "Intervals" && group == null) {
        EarPractice.directions.forEach { direction ->
            Row(Modifier.fillMaxWidth().heightIn(min = 66.dp).underline().clickable(role = Role.Button) { group = direction }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(EarPractice.title(direction), style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
                    Text("SMALL COMPARISONS → SIMPLE → COMPOUND → YOUR PICKS", style = T.label)
                }
                EarLessonLink(model, EarPractice.withDirection(Ear.drills.first { it.id == "iv-simple" }, direction))
                Text("›", style = T.title.copy(color = C.acc))
            }
        }
        EarDrillRow(model, Ear.drills.first { it.id == "iv-sing" }, 0, null, !test || validCount, ::start)
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
        f == "Intervals" -> EarPractice.intervals(group!!)
        else -> listOf(Ear.mixed(f)) + Ear.drills.filter { it.chapter == f }
    }
    rows.forEach { d ->
        val best = testOf[d.id]
        val score = if (test) best?.bestTotal?.let { "BEST ${earPercent(best.bestCorrect ?: 0, it)} · ${best.bestCorrect}/$it" } ?: "NO COMPLETED TEST" else null
        EarDrillRow(model, d, streakOf[d.id]?.best ?: 0, score, !test || validCount, ::start)
    }
    if (!own) PickYourOwn(model, f, if (f == "Intervals") group else null)
}

@Composable
private fun EarDrillRow(model: AppModel, drill: EarDrill, streak: Int, testScore: String?, enabled: Boolean, start: (EarDrill) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 66.dp).underline().clickable(role = Role.Button, enabled = enabled) { start(drill) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(drill.name.substringBefore(" · Melodic").substringBefore(" · Harmonic").substringBefore(" · Mixed"), style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
            Text(testScore ?: if (streak > 0) "BEST $streak IN A ROW" else drill.note.ifEmpty { drill.answers.joinToString(" · ") { Ear.label(drill, it) } }, style = T.label, maxLines = 2)
        }
        EarLessonLink(model, drill)
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
