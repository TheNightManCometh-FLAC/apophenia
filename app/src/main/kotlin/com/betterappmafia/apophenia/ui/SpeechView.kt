package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.audio.SpeechReader
import com.betterappmafia.apophenia.core.analysis.Speech
import com.betterappmafia.apophenia.core.analysis.TranscriptText
import com.betterappmafia.apophenia.data.ScrapEntity
import com.betterappmafia.apophenia.data.ScrapFormat
import com.betterappmafia.apophenia.data.TakeEntity
import com.betterappmafia.apophenia.data.TakeSpeechEntity
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import kotlinx.coroutines.launch

/**
 * A recording's TEXT tab (C4): what was said, line by line with the time it starts in the take (tap the time
 * to hear it), editable, and made into a text note or a lyric sheet on request. The take is never changed.
 */
@Composable
fun SpeechSection(model: AppModel, s: ScrapEntity, take: TakeEntity, title: String) {
    val reader = model.app.speechReader
    val db = model.app.db
    val stored by remember(take.id) { db.speech().of(take.id) }.collectAsState(null)
    val jobs by reader.jobs.collectAsState()
    val job = jobs.firstOrNull { it.takeId == take.id }
    val lines = remember(stored?.body) { TranscriptText.decode(stored?.body) }

    when {
        job != null && job.error == null -> {
            Kv("TEXT") { Text("Listening on the phone… ${job.percent}%${if (job.total > 0) " · part ${job.done} of ${job.total}" else ""}", style = T.body.copy(color = C.hi)) }
            Hint("It carries on if you leave this screen or the app; the AI mode lists it under JOBS.")
            return
        }
        job?.error != null -> {
            Kv("TEXT") { Text("It didn't finish: ${job!!.error}", style = T.body.copy(color = C.acc)) }
            Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) { Sq("TRY AGAIN", { reader.start(take, title) }) }
            return
        }
        lines == null -> {
            Kv("TEXT") {
                Text("Moonshine writes down what's said in this take, line by line with where each starts, on the phone. The recording itself is never changed.", style = T.small.copy(lineHeight = 16.sp))
            }
            Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) { Sq("▶ WRITE DOWN TAKE ${take.letter}", { reader.start(take, title) }, on = true) }
            Limits()
            return
        }
    }
    val list = lines!!
    val edited = TranscriptText.edited(stored?.body)
    var editing by remember(take.id) { mutableStateOf(false) }
    val drafts = remember(take.id, stored?.body, editing) { mutableStateListOf(*list.map { it.text }.toTypedArray()) }
    if (list.isEmpty()) Hint("No words found in this take.")
    list.forEachIndexed { i, l ->
        Kv(clock((l.start * 48_000).toLong(), 48_000), onClick = if (editing) null else ({ playFrom(model, s, take, title, l.start) })) {
            if (editing) BasicTextField(
                drafts[i], { drafts[i] = it }, textStyle = T.body.copy(color = C.hi), cursorBrush = SolidColor(C.acc),
                modifier = Modifier.fillMaxWidth().underline(),
            ) else Text(l.text, style = T.body.copy(color = C.hi))
        }
    }
    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (editing) {
            Sq("SAVE", {
                val fixed = list.mapIndexed { i, l -> l.copy(text = drafts[i].trim()) }.filter { it.text.isNotEmpty() }
                val model0 = TranscriptText.model(stored?.body) ?: Speech.MODEL_ID
                model.app.io.launch { db.speech().put(TakeSpeechEntity(take.id, model0, stored?.madeAt ?: System.currentTimeMillis(), TranscriptText.encode(model0, fixed, edited = true))) }
                editing = false
            }, on = true)
            Sq("CANCEL", { editing = false })
        } else {
            if (list.isNotEmpty()) Sq("EDIT", { editing = true }, on = true)
            if (list.isNotEmpty()) Box {
                var menu by remember { mutableStateOf(false) }
                Sq("MAKE ▾", { menu = true })
                ConsoleMenu(menu, { menu = false }, "MAKE FROM THESE WORDS", listOf(
                    MenuItem("A text note", "›") { make(model, s, take, list, ScrapFormat.TEXT, "Words") },
                    MenuItem("A lyric sheet", "›") { make(model, s, take, list, ScrapFormat.LYRIC, "Lyric") },
                ))
            }
            Sq(if (edited) "WRITE DOWN AGAIN (LOSES FIXES)" else "WRITE DOWN AGAIN", { reader.start(take, title) })
        }
    }
    Hint("${if (edited) "Corrected · " else ""}Moonshine Base on the phone. Tap a time to hear that line.")
    Limits()
}

private fun playFrom(model: AppModel, s: ScrapEntity, take: TakeEntity, title: String, seconds: Double) {
    val frame = (seconds * take.sampleRate).toLong().coerceIn(0, maxOf(0, take.durationFrames - 1))
    model.app.audio.load(s.id, take.id, "$title · ${take.letter}", model.app.captures.fileOf(take).absolutePath, true, frame)
}

/** [Claude] The new item goes where the recording lives (its project, or the Library), one line per spoken line. */
private fun make(model: AppModel, s: ScrapEntity, take: TakeEntity, lines: List<Speech.Line>, format: ScrapFormat, what: String) {
    model.app.io.launch {
        val now = System.currentTimeMillis()
        val item = ScrapEntity(format = format, body = lines.joinToString("\n") { it.text }, title = "$what · ${displayTitle(s)} · ${take.letter}", createdAt = now, editedAt = now)
        val id = model.app.db.scraps().createWritten(item, s.projectId)
        model.open(id, s.projectId)
    }
}

/** Plan §10.4: the honest limits, in the app. */
@Composable
private fun Limits() = Hint(
    "English only. Clear speech close to the phone reads best; words sung or spoken over an instrument come out less reliably. " +
        "Long pauses split lines. The first ${SpeechReader.MAX_SECONDS / 60} minutes of a take.",
)

@Composable
private fun Hint(text: String) = Text(text, style = T.small.copy(lineHeight = 16.sp), modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
