package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.core.learn.ChordNamer
import com.betterappmafia.apophenia.core.learn.ChordPages
import com.betterappmafia.apophenia.core.learn.Commentary
import com.betterappmafia.apophenia.core.learn.RefHit
import com.betterappmafia.apophenia.core.learn.RefKind
import com.betterappmafia.apophenia.core.learn.RefSound
import com.betterappmafia.apophenia.core.learn.Reference
import com.betterappmafia.apophenia.core.learn.YourHit
import com.betterappmafia.apophenia.core.learn.YourSource
import com.betterappmafia.apophenia.core.pitch.PitchClass
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.sound.PlayNote
import com.betterappmafia.apophenia.data.ScrapFormat
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * The Reference's links to your own material (plan §8, C4 "IN YOURS"), the hand-written rows, and naming a chord from
 * notes you tap.
 */

/** Everything a Reference page searches: live scraps, each project's own copies, and every take's chords. */
internal suspend fun loadYours(model: AppModel): List<YourSource> = withContext(Dispatchers.IO) {
    val db = model.app.db
    val scraps = db.scraps().live()
    val titles = scraps.associate { it.id to (it.title ?: it.format.token) }
    scraps.map { YourSource(it.id, titles.getValue(it.id), it.format.token, it.body, it.key) } +
        db.projects().ownBodies().filter { it.scrapId in titles }.map { YourSource(it.scrapId, titles.getValue(it.scrapId), formatOf(scraps, it.scrapId), it.body, project = it.projectName, projectId = it.projectId) } +
        db.analysis().bodies().filter { it.scrapId in titles }.map { YourSource(it.scrapId, titles.getValue(it.scrapId), ScrapFormat.RECORDING.token, analysis = it.body, take = it.letter) }
}

private fun formatOf(scraps: List<com.betterappmafia.apophenia.data.ScrapEntity>, id: Long) = scraps.first { it.id == id }.format.token

/** IN YOURS: the hits on this root first, then on other roots; tap one to open the scrap there. */
@Composable
internal fun Yours(model: AppModel, root: PitchName?, find: (List<YourSource>) -> List<YourHit>) {
    val hits by produceState<List<YourHit>?>(null, root) { value = withContext(Dispatchers.Default) { find(loadYours(model)) } }
    Kv("IN YOURS") {
        val h = hits
        when {
            h == null -> Text("Looking…", style = T.small)
            h.isEmpty() -> Text("Not in your scraps yet.", style = T.small)
            else -> {
                val here = h.filter { root == null || it.root?.pitchClass == root.pitchClass }
                val other = h - here.toSet()
                here.take(30).forEach { YourLine(model, it, null) }
                if (other.isNotEmpty()) {
                    Text("OTHER ROOTS", style = T.label, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                    other.take(30).forEach { YourLine(model, it, it.root) }
                }
                if (h.size > 60) Text("${h.size - 60} more", style = T.small)
            }
        }
    }
}

@Composable
private fun YourLine(model: AppModel, h: YourHit, root: PitchName?) {
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp).clickable(role = Role.Button) { model.open(h.scrapId, h.projectId) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(h.title, style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold), maxLines = 1)
            Text((root?.let { refName(model, it) + " · " } ?: "") + "${h.where.uppercase()} · ${h.text}", style = T.small, maxLines = 1)
        }
        Text("›", style = T.hi.copy(color = C.acc))
    }
}

/** FUNCTION and WHERE, when the page has them written. */
@Composable
internal fun Written(rows: Commentary.Rows?) {
    rows?.function?.let { Kv("FUNCTION") { Text(it, style = T.body.copy(lineHeight = 18.sp)) } }
    rows?.where?.let { Kv("WHERE") { Text(it, style = T.body.copy(lineHeight = 18.sp)) } }
}

// ------------------------------------------------------------------------------------------------------- name a chord

/**
 * Name a chord: tap notes on two octaves of piano, or frets on your guitar (one per string, tap again to mute); every
 * catalog chord those notes make is listed, exact names first, then forms with a left-out tone, then slash chords.
 */
@Composable
internal fun NameChord(model: AppModel) {
    var onGuitar by remember { mutableStateOf(model.learnShow == "GUITAR") }
    var keys by remember { mutableStateOf(setOf<Int>()) }
    val tuning = model.learnGuitar
    var frets by remember(tuning) { mutableStateOf(List<Int?>(tuning.stringCount) { null }) }
    val midis = if (onGuitar) frets.mapIndexedNotNull { s, f -> f?.let { tuning.strings[s].midi + it } } else keys.toList()
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Sq("PIANO", { onGuitar = false }, on = !onGuitar)
        Sq("GUITAR · ${tuning.name.uppercase()}", { onGuitar = true }, on = onGuitar)
        Box(Modifier.weight(1f))
        Sq("CLEAR", { keys = emptySet(); frets = List(tuning.stringCount) { null } })
    }
    if (onGuitar) {
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            TapNeck(tuning.strings.map { it.midi }, frets) { s, f -> frets = frets.toMutableList().also { it[s] = if (it[s] == f) null else f } }
        }
        Text(frets.joinToString("-") { it?.toString() ?: "x" }, style = T.hi.copy(fontSize = 15.sp), modifier = Modifier.padding(horizontal = 14.dp))
    } else {
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            TapPiano(48, 72, keys) { m -> keys = if (m in keys) keys - m else keys + m }
        }
    }
    val sorted = midis.sorted()
    val notes = sorted.map { PitchClass(it).defaultSpelling() }
    Kv("NOTES") { Text(if (notes.isEmpty()) "Tap notes" else notes.joinToString(" ") { refName(model, it) }, style = T.hi) }
    if (sorted.isNotEmpty()) Kv("HEAR") { Sq("▶ PLAY THEM", { hearNotes(model, "name-${sorted.joinToString("_")}", "Notes", sorted.mapIndexed { i, m -> PlayNote(i * 0.035, 2.4, m, RefSound.VELOCITY) }) }) }
    val named = remember(sorted) { ChordNamer.name(notes, notes.firstOrNull()) }
    if (sorted.size >= 2) {
        if (named.isEmpty()) Text("No catalog chord has exactly these notes.", style = T.small, modifier = Modifier.padding(14.dp))
        named.take(24).forEach { n ->
            val page = ChordPages.page(n.def, n.root)
            val bass = n.bass?.let { b -> page.notes.firstOrNull { it.pitchClass == b.pitchClass } ?: b }
            val symbol = page.symbols.first() + (bass?.let { "/" + Reference.ascii(it) } ?: "")
            Row(
                Modifier.fillMaxWidth().heightIn(min = 54.dp).underline().clickable(role = Role.Button) {
                    Reference.item(RefKind.CHORD, n.def.id)?.let { model.refPage = RefHit(it, page.root) }
                }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(chordShown(model, symbol, null), style = T.hi.copy(fontSize = 16.sp))
                    Text(listOfNotNull(Reference.CHORD_NAMES[n.def.id], n.missing.takeIf { it.isNotEmpty() }?.let { "NO " + it.joinToString(" ") { m -> degreeText(m) } }, bass?.let { "${refName(model, it)} IN THE BASS" }).joinToString(" · ").uppercase(), style = T.label)
                }
                Text("›", style = T.hi.copy(color = C.acc))
            }
        }
    }
}

@Composable
internal fun TapPiano(lo: Int, hi: Int, lit: Set<Int>, onTap: (Int) -> Unit) {
    val whites = (lo..hi).filter { it % 12 in setOf(0, 2, 4, 5, 7, 9, 11) }
    val keyW = 30.dp
    Canvas(
        Modifier.padding(horizontal = 12.dp, vertical = 8.dp).width(keyW * whites.size).height(130.dp).pointerInput(lit) {
            detectTapGestures { o ->
                val w = keyW.toPx()
                val i = (o.x / w).toInt().coerceIn(0, whites.size - 1)
                val m = whites[i]
                val inBlack = o.y < size.height * 0.6f
                val black = when {
                    !inBlack -> null
                    o.x - i * w > w * 0.68f && m % 12 in setOf(0, 2, 5, 7, 9) && m + 1 <= hi -> m + 1
                    o.x - i * w < w * 0.32f && (m - 1) % 12 in setOf(1, 3, 6, 8, 10) && m - 1 >= lo -> m - 1
                    else -> null
                }
                onTap(black ?: m)
            }
        },
    ) {
        val w = keyW.toPx()
        val h = size.height
        whites.forEachIndexed { i, m ->
            drawRect(if (m in lit) C.acc else C.text.copy(alpha = 0.55f), Offset(i * w + 1, 0f), androidx.compose.ui.geometry.Size(w - 2, h))
            if (m % 12 == 0) {
                val paint = android.graphics.Paint().apply { isAntiAlias = true; textSize = 9.dp.toPx(); color = C.bg.toArgb(); textAlign = android.graphics.Paint.Align.CENTER }
                drawContext.canvas.nativeCanvas.drawText("C${m / 12 - 1}", i * w + w / 2, h - 8.dp.toPx(), paint)
            }
        }
        whites.forEachIndexed { i, m ->
            if (m % 12 in setOf(0, 2, 5, 7, 9) && m + 1 <= hi) {
                val x = (i + 1) * w - w * 0.32f
                drawRect(if (m + 1 in lit) C.acc else C.bg, Offset(x, 0f), androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.6f))
            }
        }
    }
}

/** A neck to tap, frets 0–12, string 1 on top; a dot per string at its fret. */
@Composable
internal fun TapNeck(open: List<Int>, frets: List<Int?>, onTap: (string: Int, fret: Int) -> Unit) {
    val n = open.size
    val rowH = 30.dp
    val cell = 44.dp
    Canvas(
        Modifier.padding(horizontal = 12.dp, vertical = 8.dp).width(cell * 12 + 50.dp).height(rowH * n).pointerInput(n) {
            detectTapGestures { o ->
                val left = 40.dp.toPx()
                val s = n - 1 - (o.y / rowH.toPx()).toInt().coerceIn(0, n - 1)
                val f = if (o.x < left) 0 else ((o.x - left) / cell.toPx()).toInt() + 1
                onTap(s, f.coerceIn(0, 12))
            }
        },
    ) {
        val left = 40.dp.toPx()
        val cw = cell.toPx()
        val rh = rowH.toPx()
        fun y(s: Int) = rh / 2 + (n - 1 - s) * rh
        for (s in 0 until n) drawLine(C.line2, Offset(left, y(s)), Offset(left + cw * 12, y(s)), 1.5f)
        for (f in 0..12) drawLine(if (f == 0) C.hi else C.line2, Offset(left + f * cw, y(n - 1)), Offset(left + f * cw, y(0)), if (f == 0) 4f else 1.5f)
        val paint = android.graphics.Paint().apply { isAntiAlias = true; textSize = 10.dp.toPx(); typeface = android.graphics.Typeface.MONOSPACE; textAlign = android.graphics.Paint.Align.CENTER }
        frets.forEachIndexed { s, f ->
            val cx = if (f == null || f == 0) left - 18.dp.toPx() else left + (f - 0.5f) * cw
            if (f == null) { paint.color = C.dim.toArgb(); drawContext.canvas.nativeCanvas.drawText("x", cx, y(s) + 4.dp.toPx(), paint); return@forEachIndexed }
            drawCircle(C.acc, 11.dp.toPx(), Offset(cx, y(s)))
            paint.color = C.bg.toArgb()
            drawContext.canvas.nativeCanvas.drawText(com.betterappmafia.apophenia.core.chord.ChordDisplay.name(PitchClass(open[s] + f).defaultSpelling(), com.betterappmafia.apophenia.core.chord.ChordDialect.Names.ENGLISH), cx, y(s) + 3.5f.dp.toPx(), paint)
        }
    }
}

/** Everything the Ear's "Your music" drills draw from: your chords, your recordings with a key, and phrases from your transcribed takes. */
internal suspend fun loadPool(model: AppModel): com.betterappmafia.apophenia.core.learn.YourPool = withContext(Dispatchers.IO) {
    val db = model.app.db
    val sources = loadYours(model)
    val chords = com.betterappmafia.apophenia.core.learn.YourEar.chords(sources, model.chordDialect)
    val keys = ArrayList<com.betterappmafia.apophenia.core.learn.YourKey>()
    val phrases = ArrayList<com.betterappmafia.apophenia.core.learn.YourPhrase>()
    for (s in db.scraps().live().filter { it.format == ScrapFormat.RECORDING }) {
        val takes = db.takes().takesOf(s.id)
        val title = s.title ?: "Recording"
        val main = takes.firstOrNull { it.starred } ?: takes.lastOrNull()
        com.betterappmafia.apophenia.core.catalog.KeyMode.parseOrNull(s.key)?.let { k ->
            if (main != null) keys += com.betterappmafia.apophenia.core.learn.YourKey(k, title,
                com.betterappmafia.apophenia.core.learn.YourClip(java.io.File(model.app.audioDir, main.audioPath).absolutePath, 0.0,
                    minOf(com.betterappmafia.apophenia.core.learn.YourEar.KEY_CLIP, main.durationFrames.toDouble() / main.sampleRate)))
        }
        for (t in takes) {
            val notes = com.betterappmafia.apophenia.core.analysis.TranscriptionText.decode(db.transcriptions().get(t.id)?.body) ?: continue
            val path = java.io.File(model.app.audioDir, t.audioPath).absolutePath
            com.betterappmafia.apophenia.core.learn.YourEar.phrases(notes).forEach { run ->
                phrases += com.betterappmafia.apophenia.core.learn.YourPhrase(run.map { it.midi }, title, t.letter, com.betterappmafia.apophenia.core.learn.YourEar.clipOf(path, run))
            }
        }
    }
    com.betterappmafia.apophenia.core.learn.YourPool(chords, keys, phrases)
}
