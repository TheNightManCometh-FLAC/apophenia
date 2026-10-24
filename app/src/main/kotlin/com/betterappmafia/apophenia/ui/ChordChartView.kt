package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.core.score.Part
import com.betterappmafia.apophenia.core.score.Score
import com.betterappmafia.apophenia.core.score.ScoreEdit
import com.betterappmafia.apophenia.core.sheet.ChordText
import com.betterappmafia.apophenia.core.chord.ChordDialect
import com.betterappmafia.apophenia.core.chord.ChordParser
import com.betterappmafia.apophenia.core.chord.ChordSpelling
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T

/**
 * The chord chart view (C4): the score's chord symbols bar by bar, two bars to a row, one slash per beat. It is
 * the same data as the symbols over the notation: tap a beat, type a chord, and it's there too. A symbol on a
 * beat in the middle of a longer note splits that note there (tied), so the change has a beat to sit on.
 * ▶ PLAY CHORDS hears them on the piano, bass and chord, struck at each change and again at each barline.
 */
@Composable
fun ChordChartPane(score: Score, part: Part, title: String, playing: Boolean, play: () -> Unit, model: AppModel, commit: (Score) -> Unit) {
    val dialect = model.chordDialect
    val chart = remember(score, part) { ScoreEdit.chart(score, part) }
    var sel by remember { mutableStateOf<Pair<Int, Int>?>(null) }      // bar, beat
    var typed by remember { mutableStateOf("") }
    Text(
        listOfNotNull(title.uppercase().ifEmpty { null }, "${score.meter}", score.key?.display?.uppercase(), "ONE SLASH PER BEAT").joinToString(" · "),
        style = T.label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
    )
    Row(Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp)) {
        Sq(if (playing) "■ STOP" else "▶ PLAY CHORDS", play, on = true)
        Spacer(Modifier.width(6.dp))
        ChordsMenu(model)
    }
    Column(Modifier.padding(horizontal = 12.dp)) {
        for (row in chart.indices.chunked(2)) {
            Row(Modifier.fillMaxWidth().height(64.dp)) {
                for (b in row) {
                    val beats = chart[b]
                    val last = b == chart.lastIndex
                    Row(Modifier.weight(1f).fillMaxHeight().border(if (last) 2.dp else 1.dp, if (last) C.dim else C.faint), verticalAlignment = Alignment.CenterVertically) {
                        for ((k, beat) in beats.withIndex()) {
                            val on = sel == b to k
                            val shown = beat.chords.map { chordShown(model, it, score.keyAt(b)) }
                            val chord = shown.joinToString(" ") { it.text }
                            Box(
                                Modifier.weight(1f).fillMaxHeight().then(if (on) Modifier.background(C.acc.copy(alpha = 0.2f)) else Modifier)
                                    .clickable { sel = b to k; typed = beat.chords.firstOrNull() ?: "" }
                                    .semantics { contentDescription = "Bar ${b + 1} beat ${k + 1}: ${chord.ifEmpty { "no change" }}" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (shown.isEmpty()) androidx.compose.ui.text.AnnotatedString("/") else shown.reduce { a, x -> a + androidx.compose.ui.text.AnnotatedString(" ") + x }, textAlign = TextAlign.Center, maxLines = 2,
                                    style = T.body.copy(fontSize = if (chord.length > 6) 13.sp else 18.sp, fontWeight = FontWeight.ExtraBold, color = if (chord.isEmpty()) C.faint else if (beat.chords.all { ChordText.readable(it, dialect) }) C.hi else C.rec),
                                )
                            }
                        }
                    }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
    val at = sel
    Kv("SAME AS") { Text("The chord symbols over the notation. Change one here and it changes there.", style = T.small) }
    if (at == null) {
        Kv("EDIT") { Text("Tap a beat and type a chord (Dm7, E7♯9, C/E, N.C.). It is checked as you type; one it can't read is kept as typed and shown red.", style = T.small) }
        return
    }
    val (bar, beat) = at
    val tick = chart[bar][beat].tick
    ConsoleField("BAR ${bar + 1} · BEAT ${beat + 1}", typed, { typed = it.take(24) }, placeholder = "Dm7, E7#9, C/E, N.C.", onDone = {
        commit(ScoreEdit.setChordAt(score, 0, bar, tick, typed.replace("♭", "b").replace("♯", "#").ifBlank { null }))
        // On to the next beat, as typing a chart goes.
        val nb = if (beat + 1 < chart[bar].size) bar to beat + 1 else if (bar + 1 < chart.size) bar + 1 to 0 else null
        sel = nb; typed = nb?.let { (b2, k2) -> chart[b2][k2].chords.firstOrNull() } ?: ""
    })
    ChordCheck(typed, model, score.keyAt(bar))
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Sq("SET", {
            commit(ScoreEdit.setChordAt(score, 0, bar, tick, typed.replace("♭", "b").replace("♯", "#").ifBlank { null }))
        }, on = true)
        Sq("N.C.", { commit(ScoreEdit.setChordAt(score, 0, bar, tick, "N.C.")); typed = "N.C." })
        Sq("SAME AS BEFORE", {
            val prev = chart.take(bar).flatten().plus(chart[bar].take(beat)).lastOrNull { it.chords.isNotEmpty() }?.chords?.last()
            if (prev != null) { typed = prev; commit(ScoreEdit.setChordAt(score, 0, bar, tick, prev)) }
        })
        Sq("CLEAR", { typed = ""; commit(ScoreEdit.setChordAt(score, 0, bar, tick, null)) })
    }
}

/**
 * Under a chord being typed: how it reads (spelled in the key), the other reading when the symbol is ambiguous,
 * or why it can't be read (it is kept as typed all the same, and shown red).
 */
@Composable
fun ChordCheck(typed: String, model: AppModel, key: com.betterappmafia.apophenia.core.catalog.KeyMode?) {
    val dialect = model.chordDialect
    fun shown(c: com.betterappmafia.apophenia.core.chord.Chord) = com.betterappmafia.apophenia.core.chord.ChordDisplay.typeset(ChordSpelling.inKey(c, key), model.chordStyle, key, dialect.names).text
    if (typed.isBlank()) {
        Text("Empty: SET takes the chord off.", style = T.small.copy(color = C.dim), modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
        return
    }
    val parse = remember(typed, dialect) { ChordParser.parse(typed, dialect) }
    val chord = parse.chord
    val lines = if (chord == null) listOf("Not a chord symbol I can read (${parse.problem?.removeSuffix(".")}): it'll be kept as typed, in red." to C.rec)
    else buildList {
        val inKey = ChordText.display(ChordSpelling.inKey(typed, key, dialect))
        add("Reads as ${ChordText.display(ChordSpelling.symbol(chord, dialect.names))}${if (inKey != ChordText.display(typed)) ", spelled ${inKey} in the key" else ""}; shown as ${shown(chord)}." to C.dim)
        for (a in parse.alternatives) add("Or ${ChordText.display(ChordSpelling.symbol(a.chord, dialect.names))}: ${a.why}. CHORDS ▾ › READ TYPED CHORDS AS sets which comes first." to C.acc)
    }
    Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) { for ((t, c) in lines) Text(t, style = T.small.copy(color = c)) }
}
