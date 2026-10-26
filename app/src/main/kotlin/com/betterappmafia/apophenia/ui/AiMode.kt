package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One AI tool as C4 lists it: what it does, the model behind it, and whether it's in this build. */
private data class AiTool(val name: String, val note: String, val model: String, val ready: String?)

private val AI_TOOLS = listOf(
    AiTool("Separate stems", "vocals, drums, bass, other in stereo · a recording's AI ▾ › Separate stems", "SPLEETER", "READY"),
    AiTool("Notes → MIDI", "polyphonic transcription of a take · a recording's AI ▾ › Notes → MIDI", "BASIC PITCH", "READY"),
    AiTool("Sheet music and tab", "from the MIDI, spelled in the key, in your tuning · a recording's MIDI tab › MAKE TAB · SHEET ▾", "CORE", "READY"),
    AiTool("Find the chords", "per beat → a chord chart · automatic on every take", "ANALYSIS", "READY"),
    AiTool("Tempo and key", "ranked guesses with the reason shown · automatic on every take", "ANALYSIS", "READY"),
    AiTool("Speech → text", "voice memos and thinking out loud, time-stamped · a recording's AI ▾ › Speech → text", "MOONSHINE", "READY"),
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
            AI_TOOLS.forEach { t ->
                Row(
                    Modifier.fillMaxWidth().height(62.dp).underline()
                        .let { if (t.name == "Performance check") it.clickable(role = Role.Button) { model.performanceOpen = true } else it }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t.name, style = T.body.copy(color = if (t.ready != null) C.hi else C.dim, fontWeight = FontWeight.Bold))
                        Line1(t.note, T.small)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(t.model, style = T.label)
                        Text(t.ready ?: "LATER", style = T.label.copy(color = if (t.ready != null) C.acc else C.faint))
                    }
                }
            }
            Text("JOBS", style = T.label, modifier = Modifier.padding(start = 14.dp, top = 16.dp, bottom = 4.dp))
            if (jobs.isEmpty() && done.isEmpty() && stemJobs.isEmpty() && stemsDone.isEmpty() && speechJobs.isEmpty() && spokenDone.isEmpty()) Text("Nothing yet. Open a recording and use AI ▾.", style = T.small, modifier = Modifier.padding(horizontal = 14.dp))
            jobs.forEach { j ->
                JobRow("MIDI · ${j.title}", if (j.error != null) "didn't finish: ${j.error}" else "transcribing · window ${j.done} of ${j.total}", if (j.error != null) "FAILED" else "${j.percent}%") {
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
