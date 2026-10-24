package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.audio.Transcriber
import com.betterappmafia.apophenia.core.analysis.AnalysisText
import com.betterappmafia.apophenia.core.analysis.BasicPitch
import com.betterappmafia.apophenia.core.analysis.MidiFile
import com.betterappmafia.apophenia.core.analysis.TranscriptionText
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.instrument.Instrument
import com.betterappmafia.apophenia.core.instrument.Tunings
import com.betterappmafia.apophenia.core.interval.Interval
import com.betterappmafia.apophenia.core.rhythm.TICKS_PER_WHOLE
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import com.betterappmafia.apophenia.core.score.FromNotes
import com.betterappmafia.apophenia.core.score.ScoreText
import com.betterappmafia.apophenia.core.tools.KeySpeller
import com.betterappmafia.apophenia.data.ScrapEntity
import com.betterappmafia.apophenia.data.ScrapFormat
import com.betterappmafia.apophenia.data.TakeEntity
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A recording's MIDI tab (C4): Basic Pitch's notes for this take as a piano roll (tap it to hear that
 * moment), spelled in the recording's key, saved as a MIDI file on request. The take is never changed.
 */
@Composable
fun MidiSection(model: AppModel, s: ScrapEntity, take: TakeEntity, title: String) {
    val transcriber = model.app.transcriber
    val stored by remember(take.id) { model.app.db.transcriptions().of(take.id) }.collectAsState(null)
    val jobs by transcriber.jobs.collectAsState()
    val job = jobs.firstOrNull { it.takeId == take.id }
    val notes = remember(stored?.body) { TranscriptionText.decode(stored?.body) }

    when {
        job != null && job.error == null -> {
            Kv("NOTES → MIDI") { Text("Transcribing on the phone… ${job.percent}%${if (job.total > 0) " · window ${job.done} of ${job.total}" else ""}", style = T.body.copy(color = C.hi)) }
            Hint("It carries on if you leave this screen; the AI mode lists it under JOBS.")
            return
        }
        job?.error != null -> {
            Kv("NOTES → MIDI") { Text("It didn't finish: ${job!!.error}", style = T.body.copy(color = C.acc)) }
            Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) { Sq("TRY AGAIN", { transcriber.start(take, title) }) }
            return
        }
        notes == null -> {
            Kv("NOTES → MIDI") {
                Text("Basic Pitch turns this take into notes, chords included, on the phone. The recording itself is never changed.", style = T.small.copy(lineHeight = 16.sp))
            }
            Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) { Sq("▶ TRANSCRIBE TAKE ${take.letter}", { transcriber.start(take, title) }, on = true) }
            Limits()
            return
        }
    }
    val list = notes!!
    val grid = remember(stored?.body) { TranscriptionText.grid(stored?.body) }
    val edited = remember(stored?.body) { TranscriptionText.edited(stored?.body) }
    val made = stored?.madeAt?.let { SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(it)) } ?: ""
    Kv("NOTES → MIDI") {
        Text("${list.size} notes · Basic Pitch on the phone${if (edited) ", corrected" else ""} · $made${if (grid != null) " · quantised" else ""}", style = T.body.copy(color = C.hi))
        if (take.durationFrames > take.sampleRate.toLong() * Transcriber.MAX_SECONDS) Text("The first ${Transcriber.MAX_SECONDS / 60} minutes.", style = T.small)
    }
    PianoRoll(model, s, take, title, list)
    val speller = remember(s.key) { KeySpeller(KeyMode.parseOrNull(s.key) ?: KeyMode.parseOrNull("C major")!!) }
    if (list.isNotEmpty()) Text(
        list.take(64).joinToString("  ") { n -> speller.spell(n.midi).let { KeyMode.displayName(it.name) + it.octave } } + if (list.size > 64) "  …" else "",
        style = T.body.copy(color = C.hi, lineHeight = 20.sp), modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
    )
    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Sq("SAVE AS MIDI ▸", {
            val bpm = s.bpmLow ?: 120.0
            model.exportMidi(MidiFile.write(grid?.apply(list) ?: list, bpm, "$title · ${take.letter}"), "${title.replace(Regex("[^A-Za-z0-9 ._-]"), "").trim().ifEmpty { "Apophenia" }} ${take.letter}.mid")
        }, on = true)
        Sq("CORRECT ›", { model.correcting = take.id }, on = true)
        MakeTab(model, s, take, title, list, grid?.division)
        Sq(if (edited) "TRANSCRIBE AGAIN (LOSES FIXES)" else "TRANSCRIBE AGAIN", { transcriber.start(take, title) })
    }
    Hint(if (s.bpmLow != null) "The MIDI file's tempo is the recording's ${s.bpmLow} BPM, so the notes land where they were played." else "No BPM set, so the MIDI file is at 120 BPM; the notes still land where they were played.")
    Limits()
}

/**
 * MAKE TAB ▾ (plan §10.3, M5 "transcribe a take, correct it in tab"): the notes into bars at the recording's BPM
 * and meter, from its first downbeat, spelled in its key, fingered for the chosen tuning; a new tab item in the
 * recording's project or the Library. The transcription and the take stay as they are.
 */
@Composable
private fun MakeTab(model: AppModel, s: ScrapEntity, take: TakeEntity, title: String, notes: List<BasicPitch.TimedNote>, division: Int?) {
    var menu by remember { mutableStateOf(false) }
    val analysis by remember(take.id) { model.app.db.analysis().of(take.id) }.collectAsState(null)
    val bpm = s.bpmLow
    Box {
        Sq("MAKE TAB · SHEET ▾", { menu = true })
        ConsoleMenu(menu, { menu = false }, if (bpm == null) "SET THE RECORDING'S BPM FIRST" else "SHEET MUSIC, OR TAB FOR WHICH TUNING", (listOf<com.betterappmafia.apophenia.core.instrument.Tuning?>(null) + Tunings.all).map { t ->
            MenuItem(t?.name ?: "Sheet music, no tab", t?.strings?.joinToString(" ") { KeyMode.displayName(it.name) } ?: "NOTATION ONLY", enabled = bpm != null) {
                model.app.io.launch {
                    val track = AnalysisText.decode(analysis?.body)?.chords
                    val meter = track?.meter ?: TimeSignature.of(4, 4)
                    val beat = FromNotes.beatLength(meter)
                    // [Claude] Without a quantise setting: sixteenths (a quarter beat in four, a dotted beat in six).
                    val div = division?.takeIf { (beat * TICKS_PER_WHOLE).num % it == 0L } ?: if (meter.isCompound) 6 else (beat * 16).num.toInt().coerceAtLeast(1)
                    val instrument = t?.let { Instrument(it, frets = if (it.id.startsWith("ukulele")) 18 else if (it.id.startsWith("bass")) 24 else 22, writtenAboveSounding = if (it.id.startsWith("ukulele")) null else Interval(8, 12)) }
                    val score = FromNotes.build(
                        notes, FromNotes.Settings(bpm!!, meter, div, track?.beats?.firstOrNull()?.time ?: 0.0, KeyMode.parseOrNull(s.key)),
                        when { t == null -> "Notes"; t.id.startsWith("bass") -> "Bass"; t.id.startsWith("ukulele") -> "Ukulele"; else -> "Guitar" }, instrument, "$title · ${take.letter}",
                    )
                    val now = System.currentTimeMillis()
                    val item = ScrapEntity(
                        format = if (t == null) ScrapFormat.SHEET else ScrapFormat.TAB, body = ScoreText.encode(score), title = (if (t == null) "Sheet · " else "Tab · ") + "$title · ${take.letter}",
                        bpmLow = s.bpmLow, bpmHigh = s.bpmHigh, bpmSource = s.bpmSource, key = s.key, keySource = s.keySource, createdAt = now, editedAt = now,
                    )
                    val id = model.app.db.scraps().createWritten(item, s.projectId)
                    model.open(id, s.projectId)
                }
            }
        })
    }
}

/** Plan §10.3: the honest limits, in the app. */
@Composable
private fun Limits() = Hint(
    "Works best on one instrument at a time. Bends, slides and vibrato come out as wobbles to clean up; a dense mix is much less accurate; " +
        "low bass on the phone mic can come out an octave off.",
)

@Composable
private fun Hint(text: String) = Text(text, style = T.small.copy(lineHeight = 16.sp), modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))

/** Notes over time, low to high; the playhead when this take is playing. Tap to play from there. */
@Composable
private fun PianoRoll(model: AppModel, s: ScrapEntity, take: TakeEntity, title: String, notes: List<BasicPitch.TimedNote>) {
    val player by model.app.audio.player.collectAsState()
    val seconds = take.durationFrames.toDouble() / take.sampleRate
    if (notes.isEmpty() || seconds <= 0) { Hint("No notes found."); return }
    val low = notes.minOf { it.midi } - 2
    val high = notes.maxOf { it.midi } + 2
    val p = player
    val here = p != null && p.takeId == take.id && p.frames > 0
    val head = if (here) p!!.position.toFloat() / p.frames else -1f
    Canvas(
        Modifier.fillMaxWidth().height(160.dp).padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { contentDescription = "Piano roll of ${notes.size} notes. Tap to play from that point." }
            .pointerInput(take.id) {
                detectTapGestures { pos ->
                    val frac = (pos.x / size.width).coerceIn(0f, 1f)
                    if (here) model.app.audio.seek(frac)
                    else model.app.audio.load(s.id, take.id, "$title · ${take.letter}", model.app.captures.fileOf(take).absolutePath, true, (take.durationFrames * frac).toLong())
                }
            },
    ) {
        drawRect(C.line, style = Stroke(1f))
        val rows = (high - low + 1).toFloat()
        val rowH = size.height / rows
        // C of every octave as a faint line.
        for (m in low..high) if (m % 12 == 0) {
            val y = size.height - (m - low + 1) * rowH
            drawLine(C.line, Offset(0f, y + rowH), Offset(size.width, y + rowH), 1f)
        }
        for (n in notes) {
            val x0 = (n.start / seconds * size.width).toFloat()
            val x1 = (n.end / seconds * size.width).toFloat()
            val y = size.height - (n.midi - low + 1) * rowH
            drawRect(C.acc.copy(alpha = (0.35f + 0.65f * n.amplitude.toFloat()).coerceIn(0f, 1f)), Offset(x0, y), Size(maxOf(2f, x1 - x0), maxOf(2f, rowH - 1f)))
        }
        if (head in 0f..1f) drawLine(C.hi, Offset(head * size.width, 0f), Offset(head * size.width, size.height), 2f)
    }
}
