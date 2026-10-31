package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.audio.DemucsModel
import com.betterappmafia.apophenia.data.ScrapFormat
import com.betterappmafia.apophenia.data.TakeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.betterappmafia.apophenia.audio.Separator
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One AI tool as C4 lists it: what it does, the model behind it, and whether it's in this build. */
private data class AiTool(val name: String, val note: String, val model: String, val ready: String?)

private val AI_TOOLS = listOf(
    AiTool("Separate stems", "vocals, drums, bass, other in stereo · tap: the model, then a recording", "SPLEETER", "READY"),
    AiTool("Notes → MIDI", "Basic Pitch for vocals and other instruments, or Guitar · tap: the model, then a recording", "BASIC PITCH · GAPS", "READY"),
    AiTool("Sheet music and tab", "from the MIDI, spelled in the key, in your tuning · tap, then a recording", "CORE", "READY"),
    AiTool("Find the chords", "per beat → a chord chart · automatic on every take · tap to see one", "ANALYSIS", "READY"),
    AiTool("Tempo and key", "ranked guesses with the reason shown · automatic on every take · tap to see one", "ANALYSIS", "READY"),
    AiTool("Speech → text", "voice memos and thinking out loud, time-stamped · tap, then a recording", "MOONSHINE", "READY"),
    AiTool("Performance check", "a take of you against a written part, a transcription or a drum chart: single lines, chords, drums; where and why you were off · tap to start", "PITCH · NOTES · ONSETS", "READY"),
)

/**
 * The AI mode (plan §2.1, §10.6; C4): every on-phone model in one place, and the job list. A tool runs on
 * a recording from that recording's AI ▾ menu; here are what each does and what's running or done.
 */
@Composable
fun AiScreen(model: AppModel) {
    val jobs by model.app.transcriber.jobs.collectAsState()
    val done by remember { model.app.db.transcriptionList().recent() }.collectAsState(emptyList())
    val stemJobs by model.app.separator.jobs.collectAsState()
    val stemsDone by remember { model.app.db.stems().recent() }.collectAsState(emptyList())
    val speechJobs by model.app.speechReader.jobs.collectAsState()
    val spokenDone by remember { model.app.db.speech().recent() }.collectAsState(emptyList())
    if (model.performanceOpen) { PerformanceScreen(model); return }
    Column(Modifier.fillMaxSize()) {
        TitleBar { Text("AI", style = T.title, modifier = Modifier.weight(1f)); Text("ALL ON THE PHONE", style = T.label) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Kv("ON PHONE") {
                Text("Every model runs on the phone. Nothing leaves it. Results are new items linked to the source; the source is never changed.", style = T.small.copy(lineHeight = 16.sp))
            }
            val demucs by model.app.demucs.state.collectAsState()
            val added = demucs is DemucsModel.State.Added
            var openTool by remember { mutableStateOf<String?>(null) }
            AI_TOOLS.forEach { t ->
                val stems = t.name == "Separate stems"
                val open = openTool == t.name
                Row(
                    Modifier.fillMaxWidth().height(62.dp).underline()
                        .let {
                            when {
                                t.name == "Performance check" -> it.clickable(role = Role.Button) { model.performanceOpen = true }
                                else -> it.clickable(role = Role.Button) { openTool = if (open) null else t.name }
                            }
                        }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t.name, style = T.body.copy(color = if (t.ready != null) C.hi else C.dim, fontWeight = FontWeight.Bold))
                        Line1(t.note, T.small)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        val shown = when {
                            stems -> modelName(defaultStemsModel(model, added))
                            t.name == "Notes → MIDI" -> model.midiModel.label.uppercase()
                            else -> t.model
                        }
                        Text(shown + if (t.name == "Performance check") "" else if (open) " ▴" else " ▾", style = T.label)
                        Text(t.ready ?: "LATER", style = T.label.copy(color = if (t.ready != null) C.acc else C.faint))
                    }
                }
                if (open) ToolPanel(model, t.name, demucs)
            }
            Text("JOBS", style = T.label, modifier = Modifier.padding(start = 14.dp, top = 16.dp, bottom = 4.dp))
            if (jobs.isEmpty() && done.isEmpty() && stemJobs.isEmpty() && stemsDone.isEmpty() && speechJobs.isEmpty() && spokenDone.isEmpty()) Text("Nothing yet. Open a recording and use AI ▾.", style = T.small, modifier = Modifier.padding(horizontal = 14.dp))
            jobs.forEach { j ->
                JobRow("MIDI · ${j.title}", if (j.error != null) "${j.model.label} · didn't finish: ${j.error}" else "${j.model.label} · transcribing · window ${j.done} of ${j.total}", if (j.error != null) "FAILED" else "${j.percent}%") {
                    model.open(j.scrapId, null)
                }
            }
            stemJobs.forEach { j ->
                JobRow("STEMS · ${j.title} · ${j.letter}", if (j.error != null) "didn't finish: ${j.error}" else if (j.total > 0) "separating · part ${j.done} of ${j.total}" else "separating · getting the take ready", if (j.error != null) "FAILED" else "${j.percent}%") {
                    model.open(j.scrapId, null)
                }
            }
            val fmt = remember { SimpleDateFormat("d MMM HH:mm", Locale.getDefault()) }
            speechJobs.forEach { j ->
                JobRow("TEXT · ${j.title}", if (j.error != null) "didn't finish: ${j.error}" else "listening · part ${j.done} of ${j.total}", if (j.error != null) "FAILED" else "${j.percent}%") {
                    model.open(j.scrapId, null)
                }
            }
            spokenDone.filter { d -> speechJobs.none { it.takeId == d.takeId } }.forEach { d ->
                JobRow("TEXT · ${d.title?.takeIf { it.isNotBlank() } ?: "Untitled"} · ${d.letter}", "done ${fmt.format(Date(d.madeAt))} · open to read it", "DONE") {
                    model.open(d.scrapId, null)
                }
            }
            stemsDone.groupBy { it.sourceTakeId }.filterKeys { k -> stemJobs.none { it.takeId == k } }.forEach { (_, rows) ->
                val first = rows.first()
                JobRow("STEMS · ${first.title?.substringBeforeLast(" · ")?.takeIf { it.isNotBlank() } ?: "Untitled"}", "done ${fmt.format(Date(first.madeAt))} · ${rows.size} stems, each in the Library", "DONE") {
                    model.open(first.scrapId, null)
                }
            }
            done.filter { d -> jobs.none { it.takeId == d.takeId } }.forEach { d ->
                JobRow("MIDI · ${d.title?.takeIf { it.isNotBlank() } ?: "Untitled"} · ${d.letter}", "done ${fmt.format(Date(d.madeAt))} · open to see the notes", "DONE") {
                    model.open(d.scrapId, null)
                }
            }
        }
    }
}

/**
 * The default stems model (Christopher, 30 Oct 2026). Picking Demucs before it's added keeps the pick and shows the two
 * steps to add it; separations start on Spleeter until it's in.
 */
/** One recording a tool can run on: its newest starred take, or its newest take. */
private class Rec(val scrapId: Long, val title: String, val take: TakeEntity, val midi: Boolean, val text: Boolean)

/**
 * A tool's panel on the AI screen (Christopher, 30 Oct 2026: "If you select the ai option there it should let you pick a
 * file after that… And if there are settings access them there"). Its settings first, then the Library's recordings:
 * picking one runs the tool on it and opens the recording on that tool's tab. The other way round (a recording, then
 * AI ▾) is unchanged. [Claude] Stems open the STEMS tab rather than starting, because the stems are picked there
 * (28 Oct 2026); the automatic tools open INFO, Sheet music and tab opens MIDI.
 */
@Composable
private fun ToolPanel(model: AppModel, tool: String, demucs: DemucsModel.State) {
    when (tool) {
        "Separate stems" -> StemsDefault(model, demucs)
        "Notes → MIDI" -> TranscriptionChoice(model.midiModel) { model.chooseMidiModel(it) }
    }
    if (tool == "Separate stems" && model.stemsModel == Separator.DEMUCS_ID && demucs !is DemucsModel.State.Added) return
    var recs by remember { mutableStateOf<List<Rec>?>(null) }
    LaunchedEffect(Unit) {
        recs = withContext(Dispatchers.IO) {
            model.app.db.scraps().library().filter { it.format == ScrapFormat.RECORDING }.mapNotNull { s ->
                val takes = model.app.db.takes().takesOf(s.id)
                (takes.lastOrNull { it.starred } ?: takes.lastOrNull())?.let { Rec(s.id, displayTitle(s), it, model.app.db.transcriptions().get(it.id) != null, model.app.db.speech().get(it.id) != null) }
            }
        }
    }
    val list = recs
    Text("PICK A RECORDING", style = T.label.copy(color = C.acc), modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 2.dp))
    when {
        list == null -> Text("Reading the Library…", style = T.small, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
        list.isEmpty() -> Text("No recordings in the Library yet.", style = T.small, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
        else -> list.forEach { r ->
            // A take already transcribed or read opens on its result; running again is that tab's own button (it can lose fixes).
            val done = (tool == "Notes → MIDI" && r.midi) || (tool == "Speech → text" && r.text)
            JobRow(r.title, "take ${r.take.letter}", when { done -> "✓ DONE ›"; tool == "Notes → MIDI" || tool == "Speech → text" -> "RUN ›"; else -> "OPEN ›" }) {
                val tab = when (tool) {
                    "Separate stems" -> "stems"
                    "Notes → MIDI" -> { if (!done) model.app.transcriber.start(r.take, r.title, model.midiModel); "midi" }
                    "Speech → text" -> { if (!done) model.app.speechReader.start(r.take, r.title); "text" }
                    "Sheet music and tab" -> "midi"
                    else -> "info"
                }
                model.open(r.scrapId, null, view = tab)
            }
        }
    }
}

@Composable
private fun StemsDefault(model: AppModel, demucs: DemucsModel.State) {
    val added = demucs is DemucsModel.State.Added
    val picked = model.stemsModel ?: defaultStemsModel(model, added)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("DEFAULT", style = T.label)
        Sq((if (picked == Separator.MODEL_ID) "✓ " else "") + "SPLEETER", { model.chooseStemsModel(Separator.MODEL_ID) }, on = picked == Separator.MODEL_ID)
        Sq((if (picked == Separator.DEMUCS_ID) "✓ " else "") + if (added) "DEMUCS" else "DEMUCS · ADD ›", { model.chooseStemsModel(Separator.DEMUCS_ID) }, on = picked == Separator.DEMUCS_ID)
    }
    if (picked == Separator.DEMUCS_ID && !added) AddDemucs(model, demucs)
    else Text(
        "New separations start on ${modelName(picked)}; a recording's STEMS tab can still switch for one take. Run it from a recording's AI ▾ › Separate stems.",
        style = T.small.copy(lineHeight = 16.sp), modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
    )
}

@Composable
private fun JobRow(title: String, sub: String, right: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(58.dp).underline().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Line1(title, T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
            Line1(sub, T.small)
        }
        Text(right, style = T.label.copy(color = if (right == "FAILED") C.rec else C.acc))
    }
}
