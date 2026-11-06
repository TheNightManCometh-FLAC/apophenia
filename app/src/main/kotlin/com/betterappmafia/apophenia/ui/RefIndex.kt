package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.core.catalog.Chords
import com.betterappmafia.apophenia.core.catalog.Scales
import com.betterappmafia.apophenia.core.learn.ChordPages
import com.betterappmafia.apophenia.core.learn.CourseSee
import com.betterappmafia.apophenia.core.learn.IntervalPages
import com.betterappmafia.apophenia.core.learn.Key
import com.betterappmafia.apophenia.core.sound.PlayNote
import com.betterappmafia.apophenia.core.learn.Progressions
import com.betterappmafia.apophenia.core.learn.RefHit
import com.betterappmafia.apophenia.core.learn.RefItem
import com.betterappmafia.apophenia.core.learn.RefKind
import com.betterappmafia.apophenia.core.learn.RefSound
import com.betterappmafia.apophenia.core.learn.Reference
import com.betterappmafia.apophenia.core.learn.ScalePages
import com.betterappmafia.apophenia.core.learn.TuningReference
import com.betterappmafia.apophenia.core.interval.Interval
import com.betterappmafia.apophenia.core.pitch.Letter
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.tools.CuratedExercise
import com.betterappmafia.apophenia.core.tools.Exercises
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T

/*
 * The Reference home as an index (design option A, his pick 1 Nov 2026: "quick to look at and find what you need",
 * unlike the chapter list, and "be able to play the sound of whatever you're actively looking at"). One scroll of
 * compact chips grouped by family, all on the root picked at the top; a tap shows the item on the card above, which
 * plays it and opens its full page.
 */

private val INDEX_ROOTS = listOf(
    PitchName(Letter.C), PitchName(Letter.D, -1), PitchName(Letter.D), PitchName(Letter.E, -1), PitchName(Letter.E), PitchName(Letter.F),
    PitchName(Letter.F, 1), PitchName(Letter.G), PitchName(Letter.A, -1), PitchName(Letter.A), PitchName(Letter.B, -1), PitchName(Letter.B),
)

/** What the card shows and plays for an item on a root: title, notes, a picture, and the notes to play. */
internal class Peek(val title: String, val notes: String, val see: CourseSee, val play: List<PlayNote>)

private fun accidentals(s: String) = s.replace("#", "♯").replace(Regex("(?<=[A-G])b"), "♭")

internal fun peek(model: AppModel, item: RefItem, root: PitchName): Peek = when (item.kind) {
    RefKind.INTERVAL -> {
        val p = IntervalPages.page(Interval.parse(item.id), root)
        val lo = 60 + root.pitchClass
        Peek(p.name.replaceFirstChar(Char::uppercase), refName(model, root) + " → " + (p.above?.let { refName(model, it) } ?: "?"),
            CourseSee.Keys("", listOf(Key(lo, ""), Key(lo + p.semitones, ""))), RefSound.interval(lo, lo + p.semitones))
    }
    RefKind.SCALE -> {
        val s = Scales.byId(item.id)
        val p = ScalePages.page(s, root)
        val ms = RefSound.midis(p.notes.first().pitchClass, s.semitones, 60)
        Peek(refName(model, p.notes.first()) + " " + degreeText(item.title), p.notes.joinToString(" ") { refName(model, it) },
            CourseSee.Keys("", (ms + (ms.first() + 12)).map { Key(it, "") }), RefSound.scale(ms))
    }
    RefKind.CHORD -> {
        val c = Chords.byId(item.id)
        val p = ChordPages.page(c, root)
        val ms = RefSound.midis(p.notes.first().pitchClass, c.semitones, 55)
        Peek(symbolText(p.symbols.first()), p.notes.joinToString(" ") { refName(model, it) }, CourseSee.Keys("", ms.map { Key(it, "") }), RefSound.chord(ms))
    }
    RefKind.PROGRESSION -> {
        val p = Progressions.page(Progressions.byId(item.id), root)
        val play = p.chords.flatMapIndexed { i, ch -> RefSound.block(ch.midis, at = i * 1.4, length = 1.3) }
        Peek(item.title + " in " + refName(model, p.key), p.chords.joinToString("  ") { symbolText(it.symbol) },
            CourseSee.Table("", listOf(""), p.chords.map { listOf(symbolText(it.symbol)) }), play)
    }
    RefKind.TUNING -> {
        val p = TuningReference.page(TuningReference.byId(item.id)!!)
        val low = p.strings.sortedBy { it.midi }
        Peek(item.title, low.joinToString(" ") { accidentals(it.pitch) },
            CourseSee.Table("", listOf(""), listOf(listOf(low.joinToString("  ") { accidentals(it.pitch.filter { c -> !c.isDigit() }) }))),
            RefSound.upDown(low.map { it.midi }, down = false))
    }
}

/** The arpeggio exercises the index shows, with the chord each one spells. */
private val ARPEGGIOS = listOf("arp-major" to "maj", "arp-minor" to "m", "arp-dom7" to "7", "arp-maj7" to "maj7", "arp-m7" to "m7", "arp-m7b5" to "m7b5", "arp-dim7" to "dim7")

/** An arpeggio on [root]: its notes up to the octave, drawn on the keys, played up and back. */
private fun arpeggioPeek(model: AppModel, ex: CuratedExercise, root: PitchName): Peek {
    val s = ex.score(root)!!.let { it.copy(tempo = com.betterappmafia.apophenia.core.rhythm.Tempo(132.0)) }
    val notes = s.parts.first().bars.flatMap { it.voices.flatten() }.flatMap { it.notes }.map { it.pitch }
    val up = notes.take(notes.size / 2 + 1)
    return Peek(refName(model, root) + " " + ex.name.removeSuffix(" arpeggio").lowercase().replaceFirstChar(Char::uppercase) + " arpeggio",
        up.joinToString(" ") { refName(model, it.name) }, CourseSee.Keys("", up.map { Key(it.midi, "") }),
        s.parts.flatMap { com.betterappmafia.apophenia.core.score.ScorePlayback.notes(s, it) })
}

/** A chord's chip: its first catalog symbol (written on C) moved to the index root. */
private fun chordChip(item: RefItem, rootName: String) = symbolText(item.also.first()).let { s -> if (s.startsWith("C")) rootName + s.drop(1) else s }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReferenceIndex(model: AppModel) {
    val root = model.refRoot
    val pick = model.refPick ?: Reference.item(RefKind.CHORD, "m7")!!
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        INDEX_ROOTS.forEach { p ->
            val on = p == root
            Box(Modifier.size(38.dp).background(if (on) C.acc else C.panel).border(1.dp, if (on) C.acc else C.line).clickable(role = Role.Button) { model.refRoot = p },
                contentAlignment = Alignment.Center) {
                Text(refName(model, p), style = T.body.copy(color = if (on) C.bg else C.hi, fontWeight = FontWeight.Bold))
            }
        }
    }
    val arp = model.refArp?.let { Exercises.byId(it) }
    val pk = remember(pick, arp, root, model.chordDialect) { runCatching { if (arp != null) arpeggioPeek(model, arp, root) else peek(model, pick, root) }.getOrNull() }
    if (pk != null) Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().background(C.panel2).border(1.dp, C.acc).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(pk.title, style = T.display.copy(fontSize = 24.sp), modifier = Modifier.weight(1f), maxLines = 2)
            Text(if (arp != null) "ARPEGGIO" else pick.family.uppercase(), style = T.label)
        }
        Text(pk.notes, style = T.body.copy(color = C.acc, fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 2.dp))
        SeeStrip(pk.see, Modifier.padding(vertical = 10.dp), height = 52.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sq("▶ PLAY", { hearNotes(model, "index-${arp?.id ?: "${pick.kind}-${pick.id}"}-${root.letter}${root.alter}", pk.title, pk.play) })
            if (arp != null) Sq("PRACTISE IT ›", { model.exerciseRoots = model.exerciseRoots + (arp.id to root); model.curatedOpen = arp.id; model.go(Mode.EXERCISES) })
            else Sq("OPEN THE PAGE ›", { model.refPage = RefHit(pick, root) })
        }
    }
    val rn = refName(model, root)
    IndexSection("Intervals", "FROM $rn") {
        FlowRow(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Reference.items.filter { it.kind == RefKind.INTERVAL }.forEach { IndexChip(model, it, pick, degreeText(it.id), "${Interval.parse(it.id).semitones} ST") }
        }
    }
    IndexSection("Scales and modes", "ON $rn") { IndexFamilies(model, RefKind.SCALE, pick) { degreeText(it.title) } }
    IndexSection("Chords", "ON $rn") { IndexFamilies(model, RefKind.CHORD, pick) { chordChip(it, rn) } }
    // Arpeggios (Christopher, 2 Nov 2026: "I don't see arpeggios at all"): a chord's notes one at a time, up and back.
    IndexSection("Arpeggios", "ON $rn") {
        FlowRow(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ARPEGGIOS.forEach { (id, chord) ->
                val on = model.refArp == id
                Column(Modifier.background(C.panel).border(1.dp, if (on) C.acc else C.line).clickable(role = Role.Button) { model.refArp = id }.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Text(symbolText(Chords.byId(chord).symbols.first()).let { if (it.startsWith("C")) rn + it.drop(1) else it }, style = T.small.copy(color = if (on) C.acc else C.hi, fontWeight = FontWeight.Bold), maxLines = 1)
                }
            }
        }
    }
    IndexSection("Progressions", "IN $rn") { IndexFamilies(model, RefKind.PROGRESSION, pick) { it.title } }
    IndexSection("Guitar tunings", "LOW TO HIGH") { IndexFamilies(model, RefKind.TUNING, pick) { it.title } }
    IndexSection("Drums and tools", "") {
        FlowRow(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Sq("${com.betterappmafia.apophenia.core.tools.Groove.COMMON.size} GROOVES ›", { model.learnList = "GROOVES" })
            Sq("40 RUDIMENTS ›", { model.learnList = "RUDIMENTS" })
            Sq("NAME A CHORD ›", { model.learnList = "NAME" })
        }
    }
    Gap(24.dp)
}

@Composable
private fun IndexSection(title: String, note: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 6.dp).underline(), verticalAlignment = Alignment.Bottom) {
        Text(title, style = T.display.copy(fontSize = 21.sp), modifier = Modifier.weight(1f))
        Text(note, style = T.label)
    }
    content()
}

@Composable
private fun IndexChip(model: AppModel, item: RefItem, pick: RefItem, label: String, sub: String? = null) {
    val on = item == pick && model.refArp == null
    Column(Modifier.background(C.panel).border(1.dp, if (on) C.acc else C.line).clickable(role = Role.Button) { model.refPick = item; model.refArp = null }.padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text(label, style = T.small.copy(color = if (on) C.acc else C.hi, fontWeight = FontWeight.Bold), maxLines = 1)
        sub?.let { Text(it, style = T.label.copy(fontSize = 8.sp)) }
    }
}

/** A kind's items as rows of chips, one row per family, the family named on the left. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IndexFamilies(model: AppModel, kind: RefKind, pick: RefItem, label: (RefItem) -> String) {
    Reference.items.filter { it.kind == kind }.groupBy { it.family }.forEach { (family, items) ->
        Row(Modifier.padding(horizontal = 14.dp, vertical = 3.dp)) {
            Text(family.uppercase(), style = T.label.copy(fontSize = 8.sp), modifier = Modifier.width(64.dp).padding(top = 8.dp))
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEach { IndexChip(model, it, pick, label(it)) }
            }
        }
    }
}
