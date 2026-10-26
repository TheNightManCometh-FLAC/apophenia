package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.audio.Playback
import com.betterappmafia.apophenia.audio.Wav
import com.betterappmafia.apophenia.core.analysis.MonoNotes
import com.betterappmafia.apophenia.core.analysis.Onsets
import com.betterappmafia.apophenia.core.analysis.PerformanceCheck
import com.betterappmafia.apophenia.core.analysis.PerformanceCheck.RefNote
import com.betterappmafia.apophenia.core.analysis.TakeAnalysis
import com.betterappmafia.apophenia.core.analysis.TranscriptionText
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.learn.RefSound
import com.betterappmafia.apophenia.core.pitch.PitchClass
import com.betterappmafia.apophenia.core.score.ScoreText
import com.betterappmafia.apophenia.core.score.ScoreTime
import com.betterappmafia.apophenia.core.sound.PlayNote
import com.betterappmafia.apophenia.data.ScrapFormat
import com.betterappmafia.apophenia.data.TakeEntity
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Something to be measured against: a written part, or a recording's transcription. */
private class RefChoice(val label: String, val note: String, val drums: Boolean = false, val load: suspend () -> List<RefNote>)
private class TakeChoice(val label: String, val title: String, val take: TakeEntity)

/** The longest take checked, in seconds. [Claude] Five minutes keeps the alignment inside the phone's memory. */
private const val MAX_TAKE_SECONDS = 300

/**
 * Performance check (plan §10.6): pick what you were playing or singing (a written part, or the notes transcribed from a
 * recording) and the take of you doing it; the app lines them up and shows where you were off and why. This build
 * checks a single line (voice, bass, a lead line); chords and drums come later.
 */
@Composable
fun PerformanceScreen(model: AppModel) {
    val scope = rememberCoroutineScope()
    var refs by remember { mutableStateOf<List<RefChoice>?>(null) }
    var takes by remember { mutableStateOf<List<TakeChoice>?>(null) }
    var ref by remember { mutableStateOf<RefChoice?>(null) }
    var take by remember { mutableStateOf<TakeChoice?>(null) }
    var refMenu by remember { mutableStateOf(false) }
    var takeMenu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<PerformanceCheck.Result?>(null) }
    var checked by remember { mutableStateOf<TakeChoice?>(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val lib = model.app.db.scraps().library()
            refs = lib.filter { it.format == ScrapFormat.TAB || it.format == ScrapFormat.SHEET }.flatMap { s ->
                val score = runCatching { ScoreText.decode(s.body ?: "") }.getOrNull() ?: return@flatMap emptyList()
                score.parts.mapIndexed { p, part ->
                    RefChoice(displayTitle(s) + if (score.parts.size > 1) " · ${part.name}" else "", if (s.format == ScrapFormat.TAB) "TAB" else "SHEET MUSIC") {
                        val slots = ScoreTime.slots(score, score.parts[p])
                        ScoreTime.notes(score, score.parts[p]).map { n -> RefNote(n.start, n.end, n.midi, slots.lastOrNull { it.start <= n.start + 1e-6 }?.bar) }
                    }
                }
            } + lib.filter { it.format == ScrapFormat.DRUM_CHART }.mapNotNull { s ->
                val chart = runCatching { com.betterappmafia.apophenia.core.tools.DrumChart.parse(s.body ?: "") }.getOrNull() ?: return@mapNotNull null
                val bpm = (s.bpmLow ?: DRUM_CHART_DEFAULT_BPM.toDouble())
                RefChoice(displayTitle(s), "DRUM CHART · ${bpm.toInt()} BPM · TIMING ONLY", drums = true) { PerformanceCheck.drumHits(chart, bpm) }
            } + lib.filter { it.format == ScrapFormat.RECORDING }.flatMap { s ->
                model.app.db.takes().takesOf(s.id).mapNotNull { t ->
                    model.app.db.transcriptions().get(t.id)?.let { tr ->
                        RefChoice("${displayTitle(s)} · ${t.letter}", "ITS TRANSCRIBED NOTES") {
                            TranscriptionText.decode(tr.body).orEmpty().map { RefNote(it.start, it.end, it.midi) }
                        }
                    }
                }
            }
            takes = lib.filter { it.format == ScrapFormat.RECORDING }.flatMap { s ->
                model.app.db.takes().takesOf(s.id).map { t -> TakeChoice("${displayTitle(s)} · ${t.letter}", displayTitle(s), t) }
            }
        }
    }
    fun run() {
        val r = ref ?: return; val t = take ?: return
        busy = true; problem = null; result = null
        scope.launch {
            val out = withContext(Dispatchers.Default) {
                runCatching {
                    val notes = r.load()
                    require(notes.isNotEmpty()) { "That reference has no notes." }
                    val (x, rate) = Wav.readMono(model.app.captures.fileOf(t.take), TakeAnalysis.RATE, MAX_TAKE_SECONDS) ?: error("Couldn't read the take.")
                    if (r.drums) return@runCatching PerformanceCheck.drums(notes, Onsets.pick(Onsets.envelope(x, rate)))
                    // Listen from an octave and a tone below the reference's lowest note to an octave and a tone above its highest.
                    fun hz(m: Int) = 440.0 * 2.0.pow((m - 69) / 12.0)
                    val lo = hz(notes.minOf { it.midi } - 14).coerceAtLeast(30.0)
                    val hi = hz(notes.maxOf { it.midi } + 14).coerceAtMost(rate / 2.5)
                    val curve = MonoNotes.curve(x, rate, lo, hi)
                    val onsets = Onsets.pick(Onsets.envelope(x, rate))
                    PerformanceCheck.check(notes, curve, onsets)
                }
            }
            out.onSuccess { result = it; checked = t }.onFailure { problem = it.message ?: "The check didn't finish." }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        TitleBar {
            Sq("‹", { model.performanceOpen = false })
            Text("PERFORMANCE CHECK", style = T.title, modifier = Modifier.weight(1f))
            Text("PITCH + DTW", style = T.label)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                "Pick what you were playing or singing, and the take of you doing it. The app lines them up (a slower or later take isn't wrong) and shows each note you were off on, by how much, and why. Pitch and timing for single lines (voice, bass, lead lines); timing only against a drum chart. Chords come later.",
                style = T.small.copy(lineHeight = 16.sp), modifier = Modifier.padding(14.dp),
            )
            Box {
                Kv("REFERENCE", onClick = { refMenu = true }) {
                    Text(ref?.label ?: if (refs == null) "Loading…" else if (refs!!.isEmpty()) "No tab, sheet music or transcription yet" else "Pick ▾", style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
                    ref?.let { Text(it.note, style = T.label) }
                }
                ConsoleMenu(refMenu, { refMenu = false }, "MEASURED AGAINST", refs.orEmpty().map { c -> MenuItem(c.label, c.note) { ref = c; result = null } })
            }
            Box {
                Kv("YOUR TAKE", onClick = { takeMenu = true }) {
                    Text(take?.label ?: if (takes == null) "Loading…" else if (takes!!.isEmpty()) "No recordings yet" else "Pick ▾", style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
                }
                ConsoleMenu(takeMenu, { takeMenu = false }, "YOUR TAKE", takes.orEmpty().map { c -> MenuItem(c.label, clock(c.take.durationFrames, c.take.sampleRate)) { take = c; result = null } })
            }
            Row { BigButton(if (busy) "CHECKING…" else "CHECK", { run() }, primary = true, enabled = !busy && ref != null && take != null) }
            problem?.let { Text(it, style = T.body.copy(color = C.rec), modifier = Modifier.padding(14.dp)) }
            val res = result
            val t = checked
            if (res != null && t != null) Results(model, res, t)
        }
    }
}

private fun noteName(n: RefNote) = n.label ?: noteName(n.midi)

private fun noteName(midi: Int) = if (midi < 0) "A hit" else KeyMode.displayName(PitchClass(Math.floorMod(midi, 12)).defaultSpelling()) + (Math.floorDiv(midi, 12) - 1)

private fun where(n: RefNote) = n.bar?.let { b -> "Bar ${b + 1}" + (n.beat?.let { " beat %s".format(java.util.Locale.ROOT, if (it == it.toInt().toDouble()) "${it.toInt() + 1}" else "%.2f".format(java.util.Locale.ROOT, it + 1)) } ?: "") } ?: "%d:%04.1f".format(java.util.Locale.ROOT, (n.start / 60).toInt(), n.start % 60)

/** What was wrong with a note, in words. */
private fun what(r: PerformanceCheck.NoteResult): String = buildList {
    val ms = r.timingMs
    for (i in r.issues) add(when (i) {
        PerformanceCheck.Issue.MISSED -> "missed"
        PerformanceCheck.Issue.WRONG_NOTE -> r.wrongBy!!.let { w -> "${noteName(r.ref.midi + w)} instead (${kotlin.math.abs(w)} semitone${if (kotlin.math.abs(w) == 1) "" else "s"} ${if (w < 0) "low" else "high"})" }
        PerformanceCheck.Issue.SHARP -> "${r.cents} cents sharp"
        PerformanceCheck.Issue.FLAT -> "${-r.cents!!} cents flat"
        PerformanceCheck.Issue.EARLY -> "${-ms!!} ms early"
        PerformanceCheck.Issue.LATE -> "$ms ms late"
    })
}.joinToString(" · ")

@Composable
private fun Results(model: AppModel, res: PerformanceCheck.Result, t: TakeChoice) {
    val bad = res.notes.count { !it.good }
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${res.score}", style = T.hi.copy(fontSize = 52.sp, color = C.acc))
        Column(Modifier.padding(start = 12.dp)) {
            Text("${res.notes.size - bad} OF ${res.notes.size} NOTES RIGHT", style = T.button)
            Text(
                (if (res.octaveShift != 0) "Taken an octave ${if (res.octaveShift < 0) "down" else "up"}, which isn't counted. " else "") +
                    "Within ${PerformanceCheck.CENTS_OK} cents and ${PerformanceCheck.TIMING_OK_MS} ms of where your own tempo puts the note.",
                style = T.small.copy(lineHeight = 15.sp),
            )
        }
    }
    if (res.notes.any { it.ref.midi >= 0 }) Graph(res)
    // Problems in sections: notes in a row that were off, with what and why, each to hear against the reference.
    val sections = ArrayList<List<PerformanceCheck.NoteResult>>()
    var cur = ArrayList<PerformanceCheck.NoteResult>()
    for (n in res.notes) { if (!n.good) cur.add(n) else if (cur.isNotEmpty()) { sections += cur; cur = ArrayList() } }
    if (cur.isNotEmpty()) sections += cur
    if (sections.isEmpty() && res.extra.isEmpty()) Text("Nothing to point out.", style = T.body, modifier = Modifier.padding(14.dp))
    for (s in sections) {
        val head = if (s.size == 1) where(s[0].ref) else "${where(s.first().ref)} – ${where(s.last().ref).removePrefix("Bar ")}"
        val why = s.flatMap { it.why }.distinct()
        Text(
            "$head · ${s.size} NOTE${if (s.size == 1) "" else "S"} OFF" + if (why.isNotEmpty()) " · " + why.joinToString(", ") { it.label }.uppercase() else "",
            style = T.label.copy(color = C.acc), modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp),
        )
        for (n in s) ProblemRow(model, n, res, t)
    }
    if (res.extra.isNotEmpty()) {
        Text("EXTRA NOTES (THE REFERENCE RESTS THERE)", style = T.label.copy(color = C.acc), modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 4.dp))
        for (e in res.extra) Text("${noteName(e.midi)} at ${"%d:%04.1f".format(java.util.Locale.ROOT, (e.start / 60).toInt(), e.start % 60)} in your take", style = T.body, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
    }
}

@Composable
private fun ProblemRow(model: AppModel, n: PerformanceCheck.NoteResult, res: PerformanceCheck.Result, t: TakeChoice) {
    Column(Modifier.fillMaxWidth().heightIn(min = 58.dp).underline().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text("${where(n.ref)} · ${noteName(n.ref)}: ${what(n)}", style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
        if (n.why.isNotEmpty()) Text(n.why.joinToString(", ") { it.label }.replaceFirstChar(Char::uppercase), style = T.small)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Yours: the stretch of the take that lined up with this note, looped, half a second either side.
            val (from, to) = (n.takeStart ?: res.warp.firstOrNull { it.first >= n.ref.start }?.second ?: 0.0) to (n.takeEnd ?: res.warp.firstOrNull { it.first >= n.ref.end }?.second ?: 0.0)
            Sq("▶ YOURS", {
                val rate = t.take.sampleRate
                val a = ((from - 0.5).coerceAtLeast(0.0) * rate).toLong(); val b = minOf(t.take.durationFrames, ((to + 0.5) * rate).toLong())
                model.app.audio.load(t.take.scrapId, t.take.id, "${t.title} · ${t.take.letter} · ${where(n.ref)}", model.app.captures.fileOf(t.take).absolutePath, true, a, Playback(loopA = a, loopB = b))
            }, enabled = to > from)
            // The reference: the note with the notes around it, on the piano.
            if (n.ref.midi >= 0) Sq("▶ REFERENCE", {
                val near = res.notes.map { it.ref }.filter { it.end > n.ref.start - 1.0 && it.start < n.ref.end + 1.0 }
                val t0 = near.minOf { it.start }
                hearNotes(model, "perf-${n.ref.start}-${n.ref.midi}", "Reference · ${where(n.ref)}", near.map { PlayNote(it.start - t0, it.end - t0, it.midi, RefSound.VELOCITY) })
            })
        }
    }
}

/** The reference's notes as bars, your pitch drawn over them along the alignment; notes you were off on in red. */
@Composable
private fun Graph(res: PerformanceCheck.Result) {
    val notes = res.notes
    if (notes.isEmpty()) return
    val lo = notes.minOf { it.ref.midi } - 3
    val hi = notes.maxOf { it.ref.midi } + 3
    val seconds = notes.maxOf { it.ref.end }
    val pxPerSecond = 60f
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Canvas(
            Modifier.width((seconds * pxPerSecond + 24).toFloat().dp).height(180.dp).background(C.panel)
                .semantics { contentDescription = "Your pitch over the reference: ${notes.count { !it.good }} notes off" },
        ) {
            val rowH = size.height / (hi - lo + 1)
            fun y(m: Double) = (hi - m) * rowH
            val k = size.width / (seconds.toFloat() + 0.4f)
            for (n in notes) {
                val col = if (n.good) C.line2 else C.rec.copy(alpha = 0.55f)
                drawRect(col, Offset(n.ref.start.toFloat() * k, y(n.ref.midi.toDouble()).toFloat()), Size(((n.ref.end - n.ref.start).toFloat() * k).coerceAtLeast(2f), rowH))
            }
            // Your notes, placed at reference time, at the pitch you sang (octave shift taken off).
            for (n in notes) {
                if (n.issues.contains(PerformanceCheck.Issue.MISSED)) continue
                val dev = (n.wrongBy?.toDouble() ?: (n.cents ?: 0) / 100.0)
                val yy = y(n.ref.midi + dev).toFloat() + rowH / 2
                drawLine(C.acc, Offset(n.ref.start.toFloat() * k, yy), Offset(n.ref.end.toFloat() * k, yy), 3f)
            }
            drawRect(C.line, Offset.Zero, size, style = Stroke(1f))
        }
    }
    Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("▬ REFERENCE", style = T.label)
        Text("▬ YOU", style = T.label.copy(color = C.acc))
        Text("▬ OFF", style = T.label.copy(color = C.rec))
    }
}
