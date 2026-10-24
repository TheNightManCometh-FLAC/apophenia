package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.core.analysis.AnalysisText
import com.betterappmafia.apophenia.core.analysis.TakeAnalysis
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.tools.KeySpeller
import com.betterappmafia.apophenia.data.ScrapEntity
import com.betterappmafia.apophenia.data.TakeEntity
import com.betterappmafia.apophenia.data.ValueSource
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * What analysis found in one take (plan §10.2): tempo candidates (the pulse, its half and double, or
 * free time), key and mode candidates with the reason for each, and the single-line notes. Tapping a
 * candidate makes it the recording's (yours, so analysis never changes it again).
 */
@Composable
fun AnalysisSection(model: AppModel, s: ScrapEntity, take: TakeEntity) {
    val db = model.app.db
    val stored by remember(take.id) { db.analysis().of(take.id) }.collectAsState(null)
    val running by model.app.analyser.running.collectAsState()
    val result = remember(stored?.body) { AnalysisText.decode(stored?.body) }
    fun setBpm(bpm: Double?, free: Boolean = false) = model.app.io.launch {
        db.scraps().offerBpm(s.id, bpm?.let { Math.round(it * 10) / 10.0 }, freeTime = free, source = ValueSource.USER)
    }
    fun setKey(k: KeyMode) = model.app.io.launch { db.scraps().offerKey(s.id, k.token, ValueSource.USER) }

    Text("ANALYSIS · TAKE ${take.letter}", style = T.label, modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 4.dp))
    when {
        running == take.id -> { Hint("Analysing…"); return }
        stored == null || (stored!!.version < TakeAnalysis.VERSION) -> { Hint("Waiting to be analysed."); return }
        result == null -> {
            Hint("This take couldn't be analysed.")
            Row(Modifier.padding(horizontal = 14.dp)) { Sq("ANALYSE AGAIN", { model.app.analyser.again(take.id) }) }
            return
        }
    }
    val r = result!!
    val clicked = take.metronomeGrid != null

    // Tempo.
    Kv("TEMPO") {
        if (clicked) Text("Recorded with the click; the click's tempo is the take's.", style = T.small)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!r.tempo.free) r.tempo.candidates.forEach { c ->
                val label = "${(c.bpm * 10).roundToInt() / 10.0}".removeSuffix(".0") + when (c.why) { "half" -> " · HALF"; "double" -> " · DOUBLE"; else -> "" }
                Sq(label, { setBpm(c.bpm) }, on = s.bpmLow != null && kotlin.math.abs(s.bpmLow!! - c.bpm) < 0.6)
            }
            Sq("FREE TIME", { setBpm(null, free = true) }, on = s.freeTime)
        }
        Text(
            if (r.tempo.free) "No steady pulse found (regularity ${(r.tempo.clarity * 100).roundToInt()}%): free time, or too short to tell."
            else "From the attacks: regularity ${(r.tempo.clarity * 100).roundToInt()}%. Half and double are the same pulse counted differently.",
            style = T.small, modifier = Modifier.padding(top = 4.dp),
        )
    }

    // Key and mode.
    Kv("KEY") {
        Text(
            (if (r.key.uncertain) "Uncertain: the notes fit more than one key or mode equally. " else "") +
                if (r.key.from == "notes") "From the notes played." else "From the sound as a whole (chords or a mix).",
            style = T.small,
        )
    }
    r.key.candidates.take(4).forEach { c ->
        val mine = s.key == c.key.token
        Column(
            Modifier.fillMaxWidth().underline().clickable(role = Role.Button) { setKey(c.key) }.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(c.key.display + if (mine) "  ✓" else "", style = T.body.copy(color = if (mine) C.acc else C.hi, fontWeight = FontWeight.Bold))
            Text(c.reason, style = T.small.copy(lineHeight = 16.sp))
        }
    }

    // Chords (0.4.2): per beat, as a chart you can keep.
    Kv("CHORDS") {
        val c = r.chords
        if (c.beats.isEmpty() || c.beats.all { it.label == com.betterappmafia.apophenia.core.analysis.ChordFinder.NO_CHORD }) {
            Text("No chords found.", style = T.small)
        } else {
            Text(
                when {
                    c.free -> "No steady beat, so one bar per chord, in order."
                    clicked -> "On the click's beats, in ${c.meter}, from its first downbeat."
                    else -> "On the analysed pulse from the first attack, in ${c.meter} (the bar line is a guess)."
                },
                style = T.small,
            )
            Text(
                c.chart().lines().drop(1).filter { it.isNotBlank() }.take(8).joinToString("\n"),
                style = T.body.copy(color = C.hi, lineHeight = 21.sp), modifier = Modifier.padding(top = 6.dp),
            )
            Row(Modifier.padding(top = 8.dp)) {
                Sq("MAKE A CHORD CHART ▸", {
                    model.app.io.launch {
                        val now = System.currentTimeMillis()
                        val chart = com.betterappmafia.apophenia.data.ScrapEntity(
                            format = com.betterappmafia.apophenia.data.ScrapFormat.CHORD_CHART, body = c.chart(),
                            title = "Chords · ${displayTitle(s)} · ${take.letter}",
                            bpmLow = s.bpmLow, bpmHigh = s.bpmHigh, bpmSource = s.bpmSource, key = s.key, keySource = s.keySource,
                            createdAt = now, editedAt = now,
                        )
                        // [Claude] It goes where the recording lives: its project, or the Library.
                        val id = db.scraps().createWritten(chart, s.projectId)
                        model.open(id, s.projectId)
                    }
                }, on = true)
            }
        }
    }

    // Notes.
    Kv("NOTES") {
        if (r.notes.isEmpty()) Text("No single line to follow (chords, drums or a mix). Polyphonic transcription comes with Basic Pitch.", style = T.small)
        else {
            val speller = remember(s.key) { KeySpeller(KeyMode.parseOrNull(s.key) ?: r.key.candidates.firstOrNull()?.key ?: KeyMode.parseOrNull("C major")!!) }
            Text("${r.notes.size} notes, single line · pitched ${(r.voiced * 100).roundToInt()}% of the time" +
                if (r.seconds > TakeAnalysis.NOTE_SECONDS) " · first ${TakeAnalysis.NOTE_SECONDS / 60} minutes" else "", style = T.small)
            Text(
                r.notes.take(48).joinToString("  ") { n -> speller.spell(n.midi).let { KeyMode.displayName(it.name) + it.octave } } + if (r.notes.size > 48) "  …" else "",
                style = T.body.copy(color = C.hi, lineHeight = 20.sp), modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) { Sq("ANALYSE AGAIN", { model.app.analyser.again(take.id) }) }
}

@Composable
private fun Hint(text: String) = Text(text, style = T.small, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
