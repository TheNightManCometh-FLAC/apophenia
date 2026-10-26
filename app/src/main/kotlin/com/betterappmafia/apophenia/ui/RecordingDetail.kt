package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.audio.Wav
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.data.ScrapEntity
import com.betterappmafia.apophenia.data.TakeEntity
import com.betterappmafia.apophenia.data.TakeLetters
import com.betterappmafia.apophenia.data.ValueSource
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun sourceLabel(s: ValueSource?) = when (s) {
    ValueSource.USER -> "YOURS"
    ValueSource.METRONOME -> "CLICK"
    ValueSource.ANALYSIS -> "ANALYSIS"
    null -> ""
}

/** A recording's full view (plan §4.3): its takes, waveform, and everything about it. */
@Composable
fun RecordingDetail(model: AppModel, scrapId: Long, requestMic: (() -> Unit) -> Unit) {
    val db = model.app.db
    val row by remember(scrapId) { db.scraps().row(scrapId) }.collectAsState(null)
    val takes by remember(scrapId) { db.takes().takesFlow(scrapId) }.collectAsState(emptyList())
    val player by model.app.audio.player.collectAsState()
    var chosen by remember(scrapId) { mutableStateOf<Long?>(null) }
    var takeMenu by remember { mutableStateOf(false) }
    var aiMenu by remember { mutableStateOf(false) }
    var tab by remember(scrapId) { mutableStateOf("info") }

    val r = row ?: return
    val s = r.scrap
    val take = takes.firstOrNull { it.id == chosen } ?: takes.lastOrNull { it.starred } ?: takes.lastOrNull()
    val title = displayTitle(s)

    Column(Modifier.fillMaxSize()) {
        TitleBar {
            Sq("‹", { model.detail = null }, modifier = Modifier.semantics { contentDescription = "Back" })
            Box(Modifier.weight(1f).clickable { model.sheet = Sheet.Rename(scrapId) }.semantics { contentDescription = "Rename $title" }) {
                Line1(title + if (take?.starred == true) " ★" else "", T.title)
            }
            Box {
                Sq(if (take != null) "TAKE ${take.letter} ▾" else "TAKES ▾", { takeMenu = true }, on = true)
                ConsoleMenu(takeMenu, { takeMenu = false }, "TAKES", buildList {
                    takes.forEach { t -> add(MenuItem("Take ${t.letter}${if (t.starred) " ★" else ""}", if (t.id == take?.id) "✓" else clock(t.durationFrames, t.sampleRate)) { chosen = t.id }) }
                    if (take != null) add(MenuItem(if (take.starred) "Unstar take ${take.letter}" else "Star take ${take.letter}", "★") {
                        model.app.io.launch { db.takes().setStarred(take.id, !take.starred) }
                    })
                    val next = TakeLetters.next(takes.map { it.letter })
                    add(MenuItem("Record take $next", "● NEW TAKE") { requestMic { model.startRecording(RecordTarget.TakeOf(scrapId)) } })
                    if (take != null && takes.size > 1) add(MenuItem("Delete take ${take.letter}", "TO TRASH") { chosen = null; model.trashTake(take, title) })
                    add(MenuItem("Delete this recording", if (takes.size > 1) "ALL ${takes.size} TAKES" else "TO TRASH") { model.trashScrap(scrapId, title) })
                })
            }
            if (take != null) Box {
                val jobs by model.app.transcriber.jobs.collectAsState()
                val transcribed by remember(take.id) { db.transcriptions().of(take.id) }.collectAsState(null)
                val job = jobs.firstOrNull { it.takeId == take.id }
                val stemJobs by model.app.separator.jobs.collectAsState()
                val stemJob = stemJobs.firstOrNull { it.takeId == take.id }
                val stems by remember(take.id) { db.stems().of(take.id) }.collectAsState(emptyList())
                val speechJobs by model.app.speechReader.jobs.collectAsState()
                val speechJob = speechJobs.firstOrNull { it.takeId == take.id }
                val spoken by remember(take.id) { db.speech().of(take.id) }.collectAsState(null)
                Sq("AI ▾", { aiMenu = true }, on = true)
                ConsoleMenu(aiMenu, { aiMenu = false }, "AI ON THIS RECORDING · TAKE ${take.letter}", listOf(
                    MenuItem("Notes → MIDI", when { job?.error != null -> "FAILED ›"; job != null -> "${job.percent}%"; transcribed != null -> "✓ DONE"; else -> "RUN" }) {
                        if (job == null && transcribed == null) model.app.transcriber.start(take, title)
                        tab = "midi"
                    },
                    MenuItem("Tempo and key", "✓ AUTOMATIC") { tab = "info" },
                    MenuItem("Separate stems", when { stemJob?.error != null -> "FAILED ›"; stemJob != null -> "${stemJob.percent}%"; stems.isNotEmpty() -> "✓ DONE"; else -> "RUN" }) {
                        if (stemJob == null && stems.isEmpty()) model.app.separator.start(take, title)
                        tab = "stems"
                    },
                    MenuItem("Find the chords", "✓ AUTOMATIC") { tab = "info" },
                    MenuItem("Speech → text", when { speechJob?.error != null -> "FAILED ›"; speechJob != null -> "${speechJob.percent}%"; spoken != null -> "✓ DONE"; else -> "RUN" }) {
                        if (speechJob == null && spoken == null) model.app.speechReader.start(take, title)
                        tab = "text"
                    },
                    MenuItem("Sheet music and tab", "M5", enabled = false) {},
                    MenuItem("Check a performance against it", "LATER", enabled = false) {},
                ))
            }
        }
        if (take == null) {
            Text("No takes left. Restore one from the trash or delete the recording.", style = T.body, modifier = Modifier.padding(14.dp))
            return@Column
        }
        val file = remember(take.id) { model.app.captures.fileOf(take) }
        val peaks by produceState(FloatArray(0), take.id) { value = withContext(Dispatchers.IO) { runCatching { Wav.peaks(file, 120) }.getOrDefault(FloatArray(0)) } }
        val loadedHere = player?.takeId == take.id
        val progress = if (loadedHere && player!!.frames > 0) player!!.position.toFloat() / player!!.frames else -1f
        val mark = if (take.prerollFrames > 0 && take.durationFrames > 0) take.prerollFrames.toFloat() / take.durationFrames else -1f

        Waveform(
            peaks, mark = mark, progress = progress,
            modifier = Modifier.fillMaxWidth().height(84.dp).padding(horizontal = 12.dp, vertical = 10.dp)
                .semantics { contentDescription = "Waveform of take ${take.letter}. Tap to play from that point." }
                .pointerInput(take.id) {
                    detectTapGestures { pos ->
                        val frac = pos.x / size.width
                        if (loadedHere) model.app.audio.seek(frac) else model.app.audio.load(scrapId, take.id, "$title · ${take.letter}", file.absolutePath, true, (take.durationFrames * frac).toLong())
                    }
                },
        )
        Row(Modifier.fillMaxWidth().drawBehindTop(C.line)) {
            val playing = loadedHere && player?.playing == true
            BigButton(if (playing) "■ STOP" else "▶ PLAY TAKE ${take.letter}", {
                if (loadedHere) model.app.audio.togglePlay() else model.play(scrapId, take, title)
            }, primary = true)
        }
        // C4's tabs; SHEET joins with notation (M5).
        Row(Modifier.fillMaxWidth().underline().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Sq("INFO", { tab = "info" }, on = tab == "info")
            Sq("STEMS", { tab = "stems" }, on = tab == "stems")
            Sq("MIDI", { tab = "midi" }, on = tab == "midi")
            Sq("TEXT", { tab = "text" }, on = tab == "text")
        }
        if (tab == "text") {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { SpeechSection(model, s, take, title) }
            return@Column
        }
        if (tab == "midi") {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { MidiSection(model, s, take, title) }
            return@Column
        }
        if (tab == "stems") {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { StemsSection(model, s, take, title) }
            return@Column
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Kv("TAGS", onClick = { model.sheet = Sheet.Tags(scrapId) }) {
                Text(r.tagList.joinToString(" · ").ifEmpty { "none · tap to add" }, style = T.body.copy(color = if (r.tagList.isEmpty()) C.dim else C.hi, fontWeight = FontWeight.Bold))
            }
            val from by produceState<Pair<Long, String>?>(null, take.id) {
                value = withContext(Dispatchers.IO) {
                    db.stems().sourceOf(take.id)?.let { link ->
                        val src = db.takes().get(link.sourceTakeId) ?: return@let null
                        val srcScrap = db.scraps().get(src.scrapId) ?: return@let null
                        src.scrapId to "the ${link.stem} stem of ${displayTitle(srcScrap)}, take ${src.letter}"
                    }
                }
            }
            from?.let { (srcId, text) ->
                Kv("FROM", onClick = { model.open(srcId, null) }, mark = false) { Text("$text ›", style = T.body.copy(color = C.hi)) }
            }
            Kv("BPM", onClick = { model.sheet = Sheet.Bpm(scrapId) }) { ValueWithSource(bpmText(s).let { if (it == "—") "unknown" else it }, s.bpmSource) }
            Kv("KEY", onClick = { model.sheet = Sheet.Key(scrapId) }) { ValueWithSource(KeyMode.parseOrNull(s.key)?.display ?: "unknown", s.keySource) }
            val groups by model.groups.collectAsState()
            Kv("GROUPS", onClick = { model.sheet = Sheet.Groups(setOf(scrapId)) }) {
                val names = groups.filter { it.group.id in r.groupSet }.map { it.group.name }
                Text(names.joinToString(" · ").ifEmpty { "none · tap to add" }, style = T.body.copy(color = if (names.isEmpty()) C.dim else C.hi, fontWeight = FontWeight.Bold))
            }
            Kv("PROJECTS", onClick = { model.sheet = Sheet.Projects(setOf(scrapId)) }) {
                val names = r.projectList
                Text(names.joinToString(" · ").ifEmpty { "none · tap to add" }, style = T.body.copy(color = if (names.isEmpty()) C.dim else C.hi, fontWeight = FontWeight.Bold))
                if (s.projectId != null && !s.showInLibrary) Text("KEPT OUT OF LIBRARY SEARCH", style = T.label)
            }
            KvText("TAKES","${takes.size}${if (r.starred > 0) " · ★ ${r.starred}" else ""}")
            Kv("RECORDED") { Text(recordedLine(take), style = T.body.copy(lineHeight = 19.sp)) }
            if (take.prerollFrames > 0) KvText("PRE-ROLL", "%.1f s before ● REC, kept in the take (green line)".format(take.prerollFrames / take.sampleRate.toDouble()))
            AnalysisSection(model, s, take)
        }
    }
}

@Composable
private fun ValueWithSource(value: String, source: ValueSource?) {
    Row {
        Text(value, style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
        val src = sourceLabel(source)
        if (src.isNotEmpty()) Text("  $src", style = T.label)
    }
}

private fun recordedLine(t: TakeEntity): String {
    val date = SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault()).format(Date(t.createdAt))
    return "$date · ${clock(t.durationFrames, t.sampleRate)} · ${t.sampleRate / 1000.0} kHz · 24-bit WAV"
}

fun titleFor(s: ScrapEntity?) = s?.let { displayTitle(it) } ?: ""
