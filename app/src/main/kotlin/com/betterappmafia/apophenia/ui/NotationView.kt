package com.betterappmafia.apophenia.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.betterappmafia.apophenia.R
import com.betterappmafia.apophenia.core.chord.ChordDialect
import com.betterappmafia.apophenia.core.chord.ChordDisplay
import com.betterappmafia.apophenia.core.chord.ChordStyle
import com.betterappmafia.apophenia.core.rhythm.Duration
import com.betterappmafia.apophenia.core.rhythm.NoteValue
import com.betterappmafia.apophenia.core.score.Barline
import com.betterappmafia.apophenia.core.score.Clef
import com.betterappmafia.apophenia.core.score.Effect
import com.betterappmafia.apophenia.core.score.Event
import com.betterappmafia.apophenia.core.score.Head
import com.betterappmafia.apophenia.core.score.Jump
import com.betterappmafia.apophenia.core.score.Mark
import com.betterappmafia.apophenia.core.score.Notation
import com.betterappmafia.apophenia.core.score.Part
import com.betterappmafia.apophenia.core.score.Score
import com.betterappmafia.apophenia.core.score.Span
import com.betterappmafia.apophenia.core.score.TextKind
import com.betterappmafia.apophenia.core.sheet.ChordText
import com.betterappmafia.apophenia.ui.theme.C
import kotlin.math.abs
import kotlin.math.sqrt

/** Bravura (SMuFL) code points used here, checked against the SMuFL glyph list (glyphnames.json). */
private object G {
    val G_CLEF = cp(0xE050); val G_CLEF_8VB = cp(0xE052); val F_CLEF = cp(0xE062); val C_CLEF = cp(0xE05C); val PERC_CLEF = cp(0xE069)
    val WHOLE = cp(0xE0A2); val HALF = cp(0xE0A3); val BLACK = cp(0xE0A4); val X = cp(0xE0A9)
    val FLAT = cp(0xE260); val NATURAL = cp(0xE261); val SHARP = cp(0xE262); val DOUBLE_SHARP = cp(0xE263); val DOUBLE_FLAT = cp(0xE264)
    val DOT = cp(0xE1E7)
    val PAREN_L = cp(0xE0F5); val PAREN_R = cp(0xE0F6)               // noteheadParenthesisLeft/Right
    const val ACCENT = 0xE4A0; const val MARCATO = 0xE4AC; const val STACCATO = 0xE4A2   // artic…Above; Below = +1
    val SEGNO = cp(0xE047); val CODA = cp(0xE048); val REPEAT_DOTS = cp(0xE043)
    val PED = cp(0xE650); val PED_UP = cp(0xE655)                    // keyboardPedalPed, keyboardPedalUp
    val TRILL = cp(0xE566)                                            // ornamentTrill
    fun flag(n: Int, up: Boolean) = cp(0xE240 + (n - 1) * 2 + if (up) 0 else 1)
    fun digit(d: Int) = cp(0xE080 + d)
    fun tremolo(n: Int) = cp(0xE220 + n - 1)                          // tremolo1–4
    fun finger(f: String) = cp(if (f == "T") 0xED16 else 0xED10 + (f.toIntOrNull() ?: 0))
    fun pluck(p: String) = cp(when (p) { "p" -> 0xED17; "i" -> 0xED19; "m" -> 0xED1A; "a" -> 0xED1B; else -> 0xED1C })
    /** ottavaAlta, ottavaBassaVb, quindicesimaAlta, quindicesimaBassaMb. */
    fun ottava(s: Span) = cp(when (s) { Span.OTTAVA_UP -> 0xE511; Span.OTTAVA_DOWN -> 0xE51C; Span.QUINDICESIMA_UP -> 0xE515; else -> 0xE51D })
    /** metNote… for a tempo mark's beat unit, with metAugmentationDot. */
    fun metNote(d: Duration) = cp(when (d.value) { NoteValue.WHOLE -> 0xECA2; NoteValue.HALF -> 0xECA3; NoteValue.EIGHTH -> 0xECA7; else -> 0xECA5 }) + cp(0xECB7).repeat(d.dots)
    fun rest(v: NoteValue) = cp(when (v) {
        NoteValue.BREVE, NoteValue.WHOLE -> 0xE4E3; NoteValue.HALF -> 0xE4E4; NoteValue.QUARTER -> 0xE4E5; NoteValue.EIGHTH -> 0xE4E6
        NoteValue.SIXTEENTH -> 0xE4E7; NoteValue.THIRTY_SECOND -> 0xE4E8; else -> 0xE4E9
    })
    fun accidental(alter: Int) = when (alter) { -2 -> DOUBLE_FLAT; -1 -> FLAT; 0 -> NATURAL; 1 -> SHARP; else -> DOUBLE_SHARP }
    fun head(h: Head, v: NoteValue) = cp(when (v) { NoteValue.BREVE, NoteValue.WHOLE -> h.whole; NoteValue.HALF -> h.half; else -> h.black })
}

private fun cp(code: Int) = String(Character.toChars(code))

private fun flags(v: NoteValue) = when (v) {
    NoteValue.EIGHTH -> 1; NoteValue.SIXTEENTH -> 2; NoteValue.THIRTY_SECOND -> 3; NoteValue.SIXTY_FOURTH -> 4; NoteValue.HUNDRED_TWENTY_EIGHTH -> 4; else -> 0
}

/** Where an event ended up, for the lines drawn across events afterwards. */
private class EvGeo(val system: Int, val x: Float, val top: Float, val bottom: Float, val up: Boolean, val steps: List<Int>)

/** The text paints one draw uses. */
private class Inks(sp: Float) {
    val chord = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp * 1.9f; isFakeBoldText = true; color = C.hi.toArgb() }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp * 1.6f; color = C.hi.toArgb() }
    val italic = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp * 1.7f; typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC); color = C.hi.toArgb() }
    val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp * 1.8f; isFakeBoldText = true; color = C.hi.toArgb() }
    val lyric = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp * 1.7f; color = C.hi.toArgb(); textAlign = Paint.Align.CENTER }
    val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp * 1.2f; color = C.dim.toArgb() }
    val tuplet = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp * 1.5f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC); color = C.hi.toArgb(); textAlign = Paint.Align.CENTER }
}

/** A selected note: bar, voice (0 = voice 1), index in the voice, note in the chord. */
data class NoteSel(val bar: Int, val voice: Int, val index: Int, val note: Int)

/** MuseScore gives each voice its own colour for the cursor and selection: voice 1 takes the app's accent. */
val VOICE_COLORS get() = listOf(C.acc, C.listening, Color(0xFF6F9BD1), Color(0xFFB08AC9))

/** One column of a bar: a start time shared by events in any voice, how long until the next, and those events (voice, index, event). */
private class Col(val at: com.betterappmafia.apophenia.core.rhythm.Rational, val len: Double, val events: List<Triple<Int, Int, Event>>)

private fun columns(score: Score, part: Part, b: Int): List<Col> {
    val bar = part.bars[b]
    val starts = ArrayList<Pair<com.betterappmafia.apophenia.core.rhythm.Rational, Triple<Int, Int, Event>>>()
    var end = score.meterAt(b).barLength.toDouble()
    for ((v, evs) in bar.voices.withIndex()) {
        var t = com.betterappmafia.apophenia.core.rhythm.Rational.ZERO
        for ((i, e) in evs.withIndex()) { starts += t to Triple(v, i, e); t += e.length }
        end = maxOf(end, t.toDouble())
    }
    val groups = starts.groupBy { it.first }.entries.sortedBy { it.key.toDouble() }
    return groups.mapIndexed { k, (at, list) ->
        val next = groups.getOrNull(k + 1)?.key?.toDouble() ?: end
        Col(at, (next - at.toDouble()).coerceAtLeast(list.minOf { it.second.third.length.toDouble() }.coerceAtMost(0.25)), list.map { it.second })
    }
}

/**
 * The notation view of the same score (plan §10.5, C4): staff with clef, key and meter (and their changes),
 * noteheads placed from spelled pitches (`core/score/Notation.kt`), accidentals as the bar needs them, ledger
 * lines, stems by the middle-line rule, beams within a beat, flags, dots, ties, rests, and what ADD ▾ puts on
 * the score: articulations, ornaments, arpeggios, dynamics, hairpins, slurs, 8va/15ma, pedal, glissandos,
 * trill lines, barlines, repeats, voltas, segno/coda/D.S./D.C., rehearsal and tempo marks, staff text,
 * lyrics, chord symbols and diagrams, fingering, noteheads, tremolo strokes and grace notes. Up to four voices
 * share the staff, lined up in time (MuseScore's way: voices 1 and 3 stem up, 2 and 4 down, rests moved apart,
 * each voice's selection in its colour). Readable rather than engraved (plan §10.5). Tap: [onTap] gets the
 * note tapped in any voice, or else the beat of [voice] under the tap with note −1, and the staff step tapped.
 */
@Composable
fun NotationCanvas(
    score: Score, part: Part, selected: NoteSel?, cursor: Pair<Int, Int>?, offBars: Set<Int>, voice: Int, dialect: ChordDialect = ChordDialect(), style: ChordStyle = ChordStyle.POP,
    onCursorY: (Float) -> Unit = {},
    onTap: (bar: Int, voice: Int, index: Int, note: Int, step: Int) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val bravura = remember { ResourcesCompat.getFont(context, R.font.bravura) ?: Typeface.DEFAULT }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        val width = with(density) { maxWidth.toPx() }
        val sp = with(density) { 7.dp.toPx() }                 // one staff space
        val allEvents = part.bars.flatMap { it.voices.flatten() }
        val verses = allEvents.flatMap { it.lyrics }.maxOfOrNull { it.verse } ?: 0
        val diagramH = if (allEvents.any { it.diagram != null }) sp * 6.5f else 0f
        val above = sp * 9.5f + diagramH; val below = sp * 7f + verses * sp * 2.2f
        val systemH = above + sp * 4 + below
        val clefW = sp * 3.4f
        fun sigW(bar: Int) = Notation.signature(part.clefAt(bar), Notation.fifths(score.keyAt(bar))).size.let { n -> n * sp * 1.1f + if (n > 0) sp * 0.6f else 0f }
        val meterW = sp * 2.4f
        fun meterShown(b: Int) = b == 0 || part.bars[b].meter != null
        fun graceW(e: Event) = e.graces.count { !it.after } * sp * 1.6f
        fun arpW(e: Event) = if (e.marks.any { it.arpeggio }) sp * 1.2f else 0f
        val cols = remember(score, part) { part.bars.indices.map { columns(score, part, it) } }
        // A column's room in front of the notes (grace notes, arpeggio) and its room that stretches with the line.
        fun colFixed(c: Col) = c.events.maxOf { graceW(it.third) + arpW(it.third) }
        fun colFlex(c: Col) = sp * 3.2f + sp * 3f * sqrt((c.len / 0.25).toFloat()) + c.events.maxOf { it.third.graces.count { g -> g.after } * sp * 1.6f } +
            if (c.events.any { it.first > 0 }) sp * 1.2f else 0f
        /** Room a bar needs in front of its first beat for changes: all of them mid-system, just a meter at a system's start. */
        fun changeW(b: Int, first: Boolean): Float {
            val bar = part.bars[b]
            var w = if (bar.repeatStart) sp * 1.4f else 0f
            if (first) return w + if (meterShown(b)) meterW else 0f
            if (bar.clef != null) w += clefW * 0.85f
            if (bar.key != null) w += sigW(b)
            if (bar.meter != null) w += meterW
            return w
        }
        val barPad = sp * 1.2f
        val baseW = part.bars.indices.map { b -> barPad * 2 + cols[b].sumOf { (colFixed(it) + colFlex(it)).toDouble() }.toFloat() }
        val systems = ArrayList<List<Int>>()
        run {
            var cur = ArrayList<Int>(); var used = 0f
            for (i in part.bars.indices) {
                val w = baseW[i] + changeW(i, false)
                if (cur.isNotEmpty() && used + w > width) { systems += cur; cur = ArrayList() }
                if (cur.isEmpty()) { cur += i; used = clefW + sigW(i) + changeW(i, true) + baseW[i] } else { cur += i; used += w }
            }
            if (cur.isNotEmpty()) systems += cur
        }
        // Following playback: where the system holding the cursor starts, for the screen to scroll to.
        val followY = cursor?.let { c -> systems.indexOfFirst { c.first in it }.takeIf { it >= 0 }?.let { it * systemH } }
        val follow by rememberUpdatedState(onCursorY)
        LaunchedEffect(followY) { followY?.let { follow(it) } }
        // Event x (the notehead's left edge) by (bar, voice, index), each bar's span, and where its changes go.
        val xs = HashMap<Triple<Int, Int, Int>, Float>(); val barStart = HashMap<Int, Float>(); val barEnd = HashMap<Int, Float>()
        for ((si, bars) in systems.withIndex()) {
            val first = bars.first()
            val lead = clefW + sigW(first) + changeW(first, true)
            val pre = bars.drop(1).sumOf { changeW(it, false).toDouble() }.toFloat()
            val natural = bars.sumOf { baseW[it].toDouble() }.toFloat()
            val stretch = if (si < systems.lastIndex) ((width - lead - pre) / natural).coerceAtLeast(0.5f) else 1f
            var x = lead
            for (b in bars) {
                barStart[b] = x
                if (b != first) x += changeW(b, false)
                x += barPad * stretch
                for (c in cols[b]) {
                    val fixed = colFixed(c)
                    val w = colFlex(c) * stretch
                    for ((v, i, _) in c.events) xs[Triple(b, v, i)] = x + fixed + w * 0.25f
                    x += fixed + w
                }
                x += barPad * stretch; barEnd[b] = x
            }
        }
        val ottavas = remember(part) { Notation.ottavas(part) }
        val heightDp = with(density) { (systems.size * systemH).toDp() }
        fun bottomY(si: Int) = si * systemH + above + sp * 4
        Canvas(
            Modifier.fillMaxWidth().height(heightDp)
                .semantics { contentDescription = "Notation of ${part.name}, ${part.bars.size} bars. Tap a note to select it." }
                .pointerInput(score, voice) {
                    detectTapGestures { pos ->
                        val si = (pos.y / systemH).toInt().coerceIn(0, systems.lastIndex.coerceAtLeast(0))
                        val bars = systems.getOrNull(si) ?: return@detectTapGestures
                        val step = Math.round((bottomY(si) - pos.y) / (sp / 2))
                        val near = xs.entries.filter { it.key.first in bars }
                        val col = near.minByOrNull { abs(it.value + sp * 0.6f - pos.x) } ?: return@detectTapGestures
                        // A note tapped in any voice of the nearest column; else the active voice's beat there (or voice 1's when the bar lacks it).
                        fun stepOf(key: Triple<Int, Int, Int>, n: com.betterappmafia.apophenia.core.score.Note): Int {
                            val clef = part.clefAt(key.first)
                            return Notation.step(clef, Notation.written(part, n.pitch, clef)) - 7 * ottavaOf(part, ottavas, key)
                        }
                        val sameCol = near.filter { abs(it.value - col.value) < sp * 1.5f }
                        val hit = sameCol.flatMap { (k, _) -> part.bars[k.first].voices[k.second][k.third].notes.withIndex().map { (ni, n) -> Triple(k, ni, abs(stepOf(k, n) - step)) } }
                            .filter { it.third <= 1 }.minByOrNull { it.third }
                        if (hit != null) { onTap(hit.first.first, hit.first.second, hit.first.third, hit.second, step); return@detectTapGestures }
                        val mine = near.filter { it.key.second == voice && it.key.first == col.key.first }.minByOrNull { abs(it.value + sp * 0.6f - pos.x) } ?: col
                        onTap(mine.key.first, mine.key.second, mine.key.third, -1, step)
                    }
                },
        ) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bravura; textSize = sp * 4 }
            val inks = Inks(sp)
            fun glyph(g: String, x: Float, y: Float, color: Color = C.hi, scale: Float = 1f) {
                paint.color = color.toArgb(); paint.textSize = sp * 4 * scale
                drawIntoCanvas { it.nativeCanvas.drawText(g, x, y, paint) }
            }
            fun text(s: String, x: Float, y: Float, p: Paint) = drawIntoCanvas { it.nativeCanvas.drawText(s, x, y, p) }
            val geo = HashMap<Pair<Int, Int>, EvGeo>()
            for ((si, bars) in systems.withIndex()) {
                val bottom = bottomY(si)
                fun y(step: Int) = bottom - step * sp / 2
                val left = 0f; val right = barEnd.getValue(bars.last())
                for (l in 0..4) drawLine(C.dim, Offset(left, y(l * 2)), Offset(right, y(l * 2)), sp * 0.13f)
                fun clefGlyph(c: Clef, x: Float, scale: Float) = when (c) {
                    Clef.TREBLE -> glyph(G.G_CLEF, x, y(2), scale = scale)
                    Clef.TREBLE_8VB -> glyph(G.G_CLEF_8VB, x, y(2), scale = scale)
                    Clef.BASS -> glyph(G.F_CLEF, x, y(6), scale = scale)
                    Clef.ALTO -> glyph(G.C_CLEF, x, y(4), scale = scale)
                    Clef.TENOR -> glyph(G.C_CLEF, x, y(6), scale = scale)
                    Clef.PERCUSSION -> glyph(G.PERC_CLEF, x, y(4), scale = scale)
                }
                fun keyGlyphs(b: Int, x: Float) = Notation.signature(part.clefAt(b), Notation.fifths(score.keyAt(b)))
                    .forEachIndexed { i, (alter, st) -> glyph(G.accidental(alter), x + i * sp * 1.1f, y(st)) }
                fun meterGlyphs(b: Int, x: Float) {
                    val m = score.meterAt(b)
                    m.numerator.toString().forEachIndexed { i, c -> glyph(G.digit(c - '0'), x + i * sp * 1.8f, y(6)) }
                    m.denominator.toString().forEachIndexed { i, c -> glyph(G.digit(c - '0'), x + i * sp * 1.8f, y(2)) }
                }
                // System start: clef, key, and the meter where it's new.
                val first = bars.first()
                clefGlyph(part.clefAt(first), sp * 0.4f, 1f)
                keyGlyphs(first, clefW)
                if (meterShown(first)) meterGlyphs(first, clefW + sigW(first))
                for (b in bars) {
                    val bar = part.bars[b]
                    val startX = barStart.getValue(b); val endX = barEnd.getValue(b)
                    // Changes inside the system, at the bar's front.
                    if (b != first) {
                        var cx = startX + sp * 0.4f
                        bar.clef?.let { clefGlyph(it, cx, 0.8f); cx += clefW * 0.85f }
                        if (bar.key != null) { keyGlyphs(b, cx); cx += sigW(b) }
                        if (bar.meter != null) meterGlyphs(b, cx)
                    }
                    if (bar.repeatStart) {
                        val rx = if (b == first) clefW + sigW(first) + (if (meterShown(first)) meterW else 0f) else startX + changeW(b, false) - sp * 1.4f
                        drawLine(C.hi, Offset(rx + sp * 0.25f, y(8)), Offset(rx + sp * 0.25f, y(0)), sp * 0.5f)
                        drawLine(C.hi, Offset(rx + sp * 0.8f, y(8)), Offset(rx + sp * 0.8f, y(0)), sp * 0.16f)
                        glyph(G.REPEAT_DOTS, rx + sp * 1.0f, y(0))
                    }
                    // The closing barline.
                    val last = b == part.bars.lastIndex
                    val kind = if (last && bar.end == Barline.SINGLE) Barline.FINAL else bar.end
                    when (kind) {
                        Barline.SINGLE -> drawLine(C.dim, Offset(endX, y(8)), Offset(endX, y(0)), sp * 0.16f)
                        Barline.DOUBLE -> { drawLine(C.dim, Offset(endX, y(8)), Offset(endX, y(0)), sp * 0.16f); drawLine(C.dim, Offset(endX - sp * 0.5f, y(8)), Offset(endX - sp * 0.5f, y(0)), sp * 0.16f) }
                        Barline.DASHED -> drawLine(C.dim, Offset(endX, y(8)), Offset(endX, y(0)), sp * 0.16f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(sp * 0.5f, sp * 0.4f)))
                        Barline.FINAL, Barline.REPEAT -> {
                            drawLine(C.hi, Offset(endX - sp * 0.25f, y(8)), Offset(endX - sp * 0.25f, y(0)), sp * 0.5f)
                            drawLine(C.hi, Offset(endX - sp * 0.8f, y(8)), Offset(endX - sp * 0.8f, y(0)), sp * 0.16f)
                            if (kind == Barline.REPEAT) {
                                glyph(G.REPEAT_DOTS, endX - sp * 1.9f, y(0))
                                if (bar.times > 2) text("×${bar.times}", endX - sp * 2.4f, y(8) - sp * 1.0f, inks.small)
                            }
                        }
                    }
                    inks.small.color = (if (b in offBars) C.rec else C.faint).toArgb()
                    text("${b + 1}", startX + sp * 0.2f, y(8) - sp * 1.5f, inks.small)
                    inks.small.color = C.dim.toArgb()
                    // The top band: volta, rehearsal, tempo, segno/coda, jumps.
                    val topY = y(8) - sp * 7.2f - diagramH
                    if (bar.volta.isNotEmpty()) {
                        val prevSame = part.bars.getOrNull(b - 1)?.volta == bar.volta && b != first
                        val nextSame = part.bars.getOrNull(b + 1)?.volta == bar.volta
                        val vy = topY + sp * 1.6f
                        drawLine(C.hi, Offset(startX, vy), Offset(endX - sp * 0.3f, vy), sp * 0.14f)
                        if (!prevSame) { drawLine(C.hi, Offset(startX, vy), Offset(startX, vy + sp * 1.6f), sp * 0.14f); text(bar.volta.joinToString(", ") + ".", startX + sp * 0.4f, vy + sp * 1.5f, inks.text) }
                        if (!nextSame && bar.end == Barline.REPEAT) drawLine(C.hi, Offset(endX - sp * 0.3f, vy), Offset(endX - sp * 0.3f, vy + sp * 1.6f), sp * 0.14f)
                    }
                    var tx = startX + sp * 0.4f
                    bar.rehearsal?.let { r ->
                        val w = inks.bold.measureText(r)
                        drawRect(C.hi, Offset(tx - sp * 0.3f, topY - sp * 1.6f), Size(w + sp * 0.6f, sp * 2.2f), style = Stroke(sp * 0.14f))
                        text(r, tx, topY, inks.bold); tx += w + sp * 1.2f
                    }
                    for (j in bar.jumps.filter { it.atStart }) { glyph(if (j == Jump.SEGNO) G.SEGNO else G.CODA, tx, topY + sp * 0.3f, scale = 0.8f); tx += sp * 3f }
                    if (bar.tempo != null || bar.tempoText != null) {
                        bar.tempoText?.let { t -> text(t, tx, topY, inks.bold); tx += inks.bold.measureText(t) + sp * 0.6f }
                        bar.tempo?.let { t ->
                            glyph(G.metNote(t.beatUnit), tx, topY, scale = 0.7f); tx += sp * (2.2f + t.beatUnit.dots * 0.6f)
                            text("= ${if (t.bpm % 1.0 == 0.0) t.bpm.toLong() else t.bpm}", tx, topY, inks.text)
                        }
                    }
                    val endJumps = bar.jumps.filter { !it.atStart }
                    if (endJumps.isNotEmpty()) {
                        val s = endJumps.joinToString("  ") { it.text }
                        inks.italic.isFakeBoldText = true
                        text(s, endX - inks.italic.measureText(s) - sp * 0.4f, y(8) - sp * 4.2f, inks.italic)
                        inks.italic.isFakeBoldText = false
                    }
                    val accAll = barAccidentals(score, part, b, ottavas)
                    for (v in bar.voices.indices) drawBar(score, part, b, v, si, xs, ::y, sp, ottavas, ::glyph, ::text, inks, diagramH, selected, cursor, geo, accAll, dialect, style)
                }
            }
            drawSpans(part, systems, barStart, barEnd, geo, sp, ::bottomY, ::glyph, inks)
        }
    }
}


/** The 8va/8vb octaves over an event of any voice: voice 1's own, or the voice-1 beat sounding when it starts. */
internal fun ottavaOf(part: Part, ottavas: Map<Pair<Int, Int>, Int>, key: Triple<Int, Int, Int>): Int {
    val (b, v, i) = key
    if (v == 0) return ottavas[b to i] ?: 0
    val bar = part.bars[b]
    val start = bar.voices[v].take(i).sumOf { it.length.toDouble() }
    var t = 0.0
    for ((k, e) in bar.voices[0].withIndex()) {
        if (start >= t - 1e-9 && start < t + e.length.toDouble() - 1e-9) return ottavas[b to k] ?: 0
        t += e.length.toDouble()
    }
    return 0
}

/** A note's place on the page: written pitch for the part and clef, an octave down under 8va (up under 8vb). */
private fun writtenAt(part: Part, ottavas: Map<Pair<Int, Int>, Int>, key: Triple<Int, Int, Int>, p: com.betterappmafia.apophenia.core.pitch.SpelledPitch): com.betterappmafia.apophenia.core.pitch.SpelledPitch {
    val clef = part.clefAt(key.first)
    val w = Notation.written(part, p, clef)
    val oct = ottavaOf(part, ottavas, key)
    return if (oct == 0) w else com.betterappmafia.apophenia.core.pitch.SpelledPitch(w.letter, w.alter, w.octave - oct)
}

/**
 * The accidentals of a bar with all its voices read in time order (an accidental in one voice holds for the
 * others on the same staff for the rest of the bar), by (bar, voice, index).
 */
private fun barAccidentals(score: Score, part: Part, b: Int, ottavas: Map<Pair<Int, Int>, Int>): Map<Triple<Int, Int, Int>, List<Int?>> {
    val bar = part.bars[b]
    val items = ArrayList<Pair<Double, Triple<Int, Int, Int>>>()
    for ((v, evs) in bar.voices.withIndex()) { var t = 0.0; for (i in evs.indices) { items += t to Triple(b, v, i); t += evs[i].length.toDouble() } }
    items.sortWith(compareBy({ it.first }, { it.second.second }))
    fun tiedIn(v: Int, i: Int): Set<com.betterappmafia.apophenia.core.pitch.SpelledPitch> {
        val prev = if (i > 0) bar.voices[v][i - 1] else part.bars.getOrNull(b - 1)?.voices?.getOrNull(v)?.lastOrNull()
        return prev?.notes?.filter { it.tie }?.map { it.pitch }?.toSet() ?: emptySet()
    }
    val input = items.map { (_, k) ->
        val e = bar.voices[k.second][k.third]
        val before = tiedIn(k.second, k.third)
        e.notes.map { writtenAt(part, ottavas, k, it.pitch) } to e.notes.map { it.pitch in before }
    }
    val out = Notation.accidentals(input, Notation.fifths(score.keyAt(b)))
    return items.mapIndexed { n, (_, k) -> k to out[n] }.toMap()
}
private fun DrawScope.drawBar(
    score: Score, part: Part, b: Int, v: Int, si: Int, xs: Map<Triple<Int, Int, Int>, Float>, y: (Int) -> Float, sp: Float, ottavas: Map<Pair<Int, Int>, Int>,
    glyph: (String, Float, Float, Color, Float) -> Unit, text: (String, Float, Float, Paint) -> Unit, inks: Inks, diagramH: Float,
    selected: NoteSel?, cursor: Pair<Int, Int>?, geo: MutableMap<Pair<Int, Int>, EvGeo>, accAll: Map<Triple<Int, Int, Int>, List<Int?>>,
    dialect: ChordDialect, style: ChordStyle,
) {
    val bar = part.bars[b]
    val events = bar.voices[v]
    val clef = part.clefAt(b)
    val meter = score.meterAt(b)
    // With more than one voice showing, voices 1 and 3 stem up and 2 and 4 down (MuseScore).
    val multi = bar.voices.size > 1 && bar.voices.drop(1).any { vs -> vs.any { !it.hidden } }
    val vColor = VOICE_COLORS[v]
    val fifths = Notation.fifths(score.keyAt(b))
    fun written(i: Int, p: com.betterappmafia.apophenia.core.pitch.SpelledPitch) = writtenAt(part, ottavas, Triple(b, v, i), p)
    val acc = events.indices.map { i -> accAll[Triple(b, v, i)] ?: emptyList() }
    val headW = sp * 1.18f
    val stepsV0 = bar.voices[0].mapIndexed { i, e -> xs[Triple(b, 0, i)] to e.notes.map { Notation.step(clef, writtenAt(part, ottavas, Triple(b, 0, i), it.pitch)) } }
    // Beam groups: flagged notes in one beat, side by side.
    var pos = com.betterappmafia.apophenia.core.rhythm.Rational.ZERO
    val beatOf = events.map { e -> meter.beatStarts.indexOfLast { it <= pos }.also { pos += e.length } }
    val groups = ArrayList<List<Int>>()
    var cur = ArrayList<Int>()
    for ((i, e) in events.withIndex()) {
        val beamable = !e.isRest && flags(e.duration.value) > 0
        if (beamable && cur.isNotEmpty() && beatOf[cur.last()] == beatOf[i] && cur.last() == i - 1) cur += i
        else { if (cur.isNotEmpty()) groups += cur; cur = if (beamable) arrayListOf(i) else ArrayList() }
    }
    if (cur.isNotEmpty()) groups += cur
    val groupOf = HashMap<Int, List<Int>>(); groups.forEach { g -> g.forEach { groupOf[it] = g } }
    // Stem direction per beat group (or note), and the beam's height.
    val stepsOf = events.mapIndexed { i, e -> e.notes.map { Notation.step(clef, written(i, it.pitch)) } }
    val upOf = HashMap<Int, Boolean>()
    for ((i, _) in events.withIndex()) {
        val g = groupOf[i]
        upOf[i] = if (multi) v % 2 == 0 else if (g != null && g.size > 1) Notation.stemUp(g.flatMap { stepsOf[it] }) else Notation.stemUp(stepsOf[i])
    }
    val chordY = y(8) - sp * 5.0f
    for ((i, e) in events.withIndex()) {
        val x0 = xs.getValue(Triple(b, v, i))
        // A note a step or less from voice 1's at the same time moves aside.
        val x = if (v > 0 && stepsOf[i].any { s -> stepsV0.any { (vx, steps) -> vx != null && abs(vx - x0) < 1f && steps.any { abs(it - s) <= 1 } } }) x0 + headW * 1.05f else x0
        val isCur = v == 0 && cursor == (b to i)
        val evSel = selected?.let { it.bar == b && it.voice == v && it.index == i } == true
        if (isCur) drawRect(C.acc.copy(alpha = 0.18f), Offset(x - sp * 0.6f, y(10)), Size(headW + sp * 1.2f, y(-2) - y(10)))
        // What sits above and below the staff whatever the event holds.
        // Spelled in the key (A♯7 in F major shows as B♭7), in the reader's note names.
        e.chord?.let { c -> ChordDisplay.of(c, style, score.keyAt(b), dialect)?.let { drawTypeset(it, x, chordY, inks.chord) } ?: text(ChordText.display(c), x, chordY, inks.chord) }
        e.diagram?.let { drawDiagram(it, x, chordY - sp * 2.2f - sp * 4f, sp) }
        var aboveY = y(8) - sp * 2.8f
        for (t in e.texts.filter { it.kind != TextKind.EXPRESSION }) { text(t.text, x, aboveY, if (t.kind == TextKind.TECHNIQUE) inks.text else inks.bold); aboveY -= sp * 1.9f }
        var belowY = y(0) + sp * 4.2f
        e.dynamic?.let { d -> glyph(d.glyphs, x - sp * 0.3f, belowY, C.hi, 1f); belowY += sp * 1.0f }
        for (t in e.texts.filter { it.kind == TextKind.EXPRESSION }) { text(t.text, x + if (e.dynamic != null) sp * 3.4f else 0f, y(0) + sp * 4.2f, inks.italic) }
        for (l in e.lyrics) {
            val ly = y(0) + sp * (6.6f + (l.verse - 1) * 2.2f)
            text(l.text, x + headW / 2, ly, inks.lyric)
            val nx = xs[Triple(b, v, i + 1)] ?: xs[Triple(b + 1, v, 0)]?.takeIf { it > x }
            val half = inks.lyric.measureText(l.text) / 2
            if (l.hyphen && nx != null) text("-", (x + headW / 2 + half + nx + headW / 2 - inks.lyric.measureText(" ")) / 2 + sp * 0.4f, ly, inks.lyric)
            if (l.extend) drawLine(C.hi, Offset(x + headW / 2 + half + sp * 0.2f, ly), Offset((nx ?: (x + sp * 4)) + headW, ly), sp * 0.12f)
        }
        if (e.isRest) {
            // Rests of several voices move apart: voice 1 and 3 up, 2 and 4 down. A hidden rest shows faintly while editing.
            val st = when (e.duration.value) { NoteValue.WHOLE, NoteValue.BREVE -> 6; else -> 4 } + if (multi) (if (v % 2 == 0) 4 else -4) else 0
            val restColor = if (evSel) vColor else if (e.hidden) C.faint.copy(alpha = 0.5f) else C.hi
            glyph(G.rest(e.duration.value), x, y(st), restColor, 1f)
            repeat(e.duration.dots) { k -> glyph(G.DOT, x + sp * (1.6f + k * 0.6f), y(st + 1), restColor, 1f) }
            if (!e.hidden) drawMarks(e, x, y(8) - sp * 1.0f, y(0) + sp * 1.4f, true, sp, glyph)
            if (v == 0) geo[b to i] = EvGeo(si, x, y(6), y(2), true, listOf(4))
            continue
        }
        val up = upOf.getValue(i)
        val steps = stepsOf[i]
        val order = steps.indices.sortedBy { steps[it] }.let { if (up) it else it.reversed() }
        // Seconds in a chord: every other note of a cluster moves to the far side of the stem.
        val shifted = HashSet<Int>()
        var prev: Int? = null
        for (k in order) { val s = steps[k]; if (prev != null && abs(s - steps[prev]) == 1 && prev !in shifted) shifted += k; prev = k }
        var accCol = 0
        val accCols = acc[i].count { it != null }.coerceAtMost(3)
        val accW = if (accCols > 0) sp * (0.4f + accCols * 1.1f) else 0f
        for (k in steps.indices.sortedByDescending { steps[it] }) {
            val s = steps[k]
            val n = e.notes[k]
            val hx = x + if (k in shifted) (if (up) headW else -headW) else 0f
            for (l in Notation.ledgers(s)) drawLine(C.dim, Offset(hx - sp * 0.4f, y(l)), Offset(hx + headW + sp * 0.4f, y(l)), sp * 0.16f)
            val sel = selected == NoteSel(b, v, i, k)
            val color = if (sel) vColor else if (evSel) vColor.copy(alpha = 0.75f) else C.hi
            val head = if (Effect.DEAD in n.effects && n.head == Head.NORMAL) G.X else G.head(n.head, e.duration.value)
            glyph(head, hx, y(s), color, 1f)
            if (Effect.GHOST in n.effects) { glyph(G.PAREN_L, hx - sp * 0.55f, y(s), color, 1f); glyph(G.PAREN_R, hx + headW + sp * 0.12f, y(s), color, 1f) }
            acc[i][k]?.let { a -> glyph(G.accidental(a), x - sp * (1.4f + accCol * 1.1f) - if (k in shifted && !up) headW else 0f, y(s), C.hi, 1f); accCol = (accCol + 1) % 3 }
            repeat(e.duration.dots) { d -> glyph(G.DOT, x + headW + sp * (0.4f + d * 0.6f) + if (up && shifted.isNotEmpty()) headW else 0f, y(if (s % 2 == 0) s + 1 else s), C.hi, 1f) }
            // Fingering to the right of the head: finger, pluck, circled string.
            var fx = hx + headW + sp * (0.35f + e.duration.dots * 0.6f)
            if (n.finger.isNotEmpty()) { glyph(G.finger(n.finger), fx, y(s) + sp * 0.5f, C.hi, 0.55f); fx += sp * 1.0f }
            if (n.pluck.isNotEmpty()) { glyph(G.pluck(n.pluck), fx, y(s) + sp * 0.5f, C.hi, 0.55f); fx += sp * 1.0f }
            if (n.showString && n.string != null) {
                drawCircle(C.hi, sp * 0.7f, Offset(fx + sp * 0.6f, y(s)), style = Stroke(sp * 0.1f))
                inks.small.color = C.hi.toArgb(); inks.small.textAlign = Paint.Align.CENTER
                text("${n.string}", fx + sp * 0.6f, y(s) + sp * 0.42f, inks.small)
                inks.small.color = C.dim.toArgb(); inks.small.textAlign = Paint.Align.LEFT
            }
            if (n.tie) {
                // A tie to the next note: a curve under (stem up) or over (stem down) the head.
                val nx = xs[Triple(b, v, i + 1)] ?: (x + sp * 5)
                val ty = y(s) + if (up) sp * 0.7f else -sp * 0.7f
                val path = Path().apply { moveTo(hx + headW, ty); quadraticTo((hx + headW + nx) / 2, ty + if (up) sp * 0.9f else -sp * 0.9f, nx, ty) }
                drawPath(path, C.hi, style = Stroke(sp * 0.18f))
            }
        }
        // Arpeggio: a wavy line left of the chord, an arrowhead for a direction.
        e.marks.firstOrNull { it.arpeggio }?.let { m ->
            val ax = x - accW - sp * 1.0f
            val top = y(steps.max()) - sp * 0.8f; val bot = y(steps.min()) + sp * 0.8f
            val path = Path().apply {
                moveTo(ax, bot); var yy = bot; var right = true
                while (yy > top) { yy -= sp * 0.5f; lineTo(ax + if (right) sp * 0.35f else 0f, yy); right = !right }
            }
            drawPath(path, C.hi, style = Stroke(sp * 0.14f))
            if (m == Mark.ARPEGGIO_UP) drawPath(Path().apply { moveTo(ax - sp * 0.4f, top + sp * 0.5f); lineTo(ax + sp * 0.17f, top - sp * 0.3f); lineTo(ax + sp * 0.75f, top + sp * 0.5f) }, C.hi, style = Stroke(sp * 0.14f))
            if (m == Mark.ARPEGGIO_DOWN) drawPath(Path().apply { moveTo(ax - sp * 0.4f, bot - sp * 0.5f); lineTo(ax + sp * 0.17f, bot + sp * 0.3f); lineTo(ax + sp * 0.75f, bot - sp * 0.5f) }, C.hi, style = Stroke(sp * 0.14f))
        }
        // Grace notes: small, stem up, slashed when acciaccaturas; before the note (left of accidentals and arpeggio) or after it.
        val before = e.graces.filter { !it.after }; val after = e.graces.filter { it.after }
        fun grace(g: com.betterappmafia.apophenia.core.score.Grace, gx: Float) {
            val gs = g.notes.map { Notation.step(clef, written(i, it.pitch)) }
            val sig = Notation.signatureAlters(fifths)
            for ((k, n) in g.notes.withIndex()) {
                val s = gs[k]
                for (l in Notation.ledgers(s)) drawLine(C.dim, Offset(gx - sp * 0.3f, y(l)), Offset(gx + headW * 0.65f + sp * 0.3f, y(l)), sp * 0.12f)
                glyph(G.BLACK, gx, y(s), C.hi, 0.65f)
                val wn = written(i, n.pitch)
                if (wn.alter != (sig[wn.letter] ?: 0)) glyph(G.accidental(wn.alter), gx - sp * 0.9f, y(s), C.hi, 0.65f)
            }
            val sx = gx + headW * 0.65f - sp * 0.05f
            val top = y(gs.max()) - sp * 2.6f
            drawLine(C.hi, Offset(sx, y(gs.min())), Offset(sx, top), sp * 0.1f)
            glyph(G.flag(flags(g.value).coerceAtLeast(1), true), sx - sp * 0.04f, top, C.hi, 0.65f)
            if (g.slash) drawLine(C.hi, Offset(sx - sp * 0.6f, top + sp * 1.6f), Offset(sx + sp * 0.8f, top + sp * 0.4f), sp * 0.12f)
        }
        before.forEachIndexed { k, g -> grace(g, x - accW - (if (e.marks.any { it.arpeggio }) sp * 1.2f else 0f) - sp * 1.6f * (before.size - k)) }
        after.forEachIndexed { k, g -> grace(g, x + headW + sp * (1.0f + e.duration.dots * 0.6f + k * 1.6f)) }
        // Stems and flags (beams after the loop).
        var stemEnd = if (up) y(steps.max()) else y(steps.min())
        if (e.duration.value != NoteValue.WHOLE && e.duration.value != NoteValue.BREVE) {
            val g = groupOf[i]
            val sx = if (up) x + headW - sp * 0.06f else x + sp * 0.06f
            val farY = if (up) y(steps.min()) - sp * 0.168f else y(steps.max()) + sp * 0.168f
            val endY = if (g != null && g.size > 1) beamY(g, stepsOf, up, y, sp) else if (up) y(steps.max()) - sp * 3.5f else y(steps.min()) + sp * 3.5f
            drawLine(C.hi, Offset(sx, farY), Offset(sx, endY), sp * 0.12f)
            if (g == null || g.size == 1) flags(e.duration.value).takeIf { it > 0 }?.let { glyph(G.flag(it, up), sx - sp * 0.06f, endY, C.hi, 1f) }
            stemEnd = endY
            // Tremolo strokes across the stem, between the notes and the stem's end.
            val strokes = maxOf(e.tremolo, if (e.notes.any { Effect.TREMOLO_PICKING in it.effects }) 3 else 0)
            if (strokes > 0) glyph(G.tremolo(strokes), sx - sp * 0.6f, (farY + endY) / 2 + (if (up) sp * 0.6f else sp * 0.2f), C.hi, 1f)
        } else {
            val strokes = maxOf(e.tremolo, if (e.notes.any { Effect.TREMOLO_PICKING in it.effects }) 3 else 0)
            if (strokes > 0) glyph(G.tremolo(strokes), x + sp * 0.0f, y(steps.max()) - sp * 1.6f, C.hi, 1f)
        }
        e.duration.timeModification?.let { t ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp * 1.4f; color = C.dim.toArgb(); isFakeBoldText = true }
            if (groupOf[i]?.first() == i || groupOf[i] == null) drawContext.canvas.nativeCanvas.drawText("${t.actual}", x, if (up) y(steps.max()) - sp * 4.6f else y(steps.min()) + sp * 5.4f, p)
        }
        // Articulations on the notehead side (accent, marcato and staccato from the notes' effects), the rest above.
        val headSideY = if (up) y(steps.min()) + sp * 1.6f else y(steps.max()) - sp * 1.0f
        val topY = minOf(y(10), if (up) stemEnd - sp * 0.8f else y(steps.max()) - sp * 1.2f)
        drawMarks(e, x, topY, headSideY, up, sp, glyph)
        if (v == 0) geo[b to i] = EvGeo(si, x, minOf(y(steps.max()), if (up) stemEnd else y(steps.max())), maxOf(y(steps.min()), if (!up) stemEnd else y(steps.min())), up, steps)
    }
    for (g in groups) if (g.size > 1) {
        val up = upOf.getValue(g.first())
        val by = beamY(g, stepsOf, up, y, sp)
        val xsG = g.map { (xs.getValue(Triple(b, v, it))) + if (up) sp * 1.12f else sp * 0.06f }
        val levels = g.map { flags(events[it].duration.value) }
        for (level in 1..levels.max()) {
            val yy = by + (level - 1) * sp * 0.75f * if (up) 1 else -1
            var a = 0
            while (a < g.size) {
                if (levels[a] < level) { a++; continue }
                var z = a
                while (z + 1 < g.size && levels[z + 1] >= level) z++
                val x0 = xsG[a]; val x1 = if (z > a) xsG[z] else xsG[a] + (if (a > 0) -sp else sp)
                val top = if (up) yy else yy - sp * 0.5f
                drawRect(C.hi, Offset(minOf(x0, x1), top), Size(abs(x1 - x0) + sp * 0.06f, sp * 0.5f))
                a = z + 1
            }
        }
    }
    // Tuplets: the number over each run of tuplet notes in one beat (Gould: italic, on the stem side or above), with a
    // bracket when the run isn't one beam.
    val runs = ArrayList<List<Int>>()
    var run = ArrayList<Int>()
    for ((i, e) in events.withIndex()) {
        val t = e.duration.tuplets.firstOrNull()
        if (t != null && run.isNotEmpty() && events[run.last()].duration.tuplets.firstOrNull() == t && beatOf[run.last()] == beatOf[i]) run += i
        else { if (run.isNotEmpty()) runs += run; run = if (t != null) arrayListOf(i) else ArrayList() }
    }
    if (run.isNotEmpty()) runs += run
    for (r in runs) {
        val t = events[r.first()].duration.tuplets.first()
        val x0 = xs.getValue(Triple(b, v, r.first()))
        val x1 = xs.getValue(Triple(b, v, r.last())) + headW
        val top = minOf(y(10), r.minOf { i -> stepsOf[i].maxOrNull()?.let { y(it) - sp * 4.2f } ?: y(8) })
        val label = "${t.actual}"
        val mid = (x0 + x1) / 2
        val w = inks.tuplet.measureText(label)
        text(label, mid, top, inks.tuplet)
        val beamed = groupOf[r.first()]?.let { g -> r.all { it in g } } == true
        if (!beamed) {
            val ly = top - sp * 0.55f
            drawLine(C.hi, Offset(x0, ly + sp * 0.6f), Offset(x0, ly), 1.2f)
            drawLine(C.hi, Offset(x0, ly), Offset(mid - w, ly), 1.2f)
            drawLine(C.hi, Offset(mid + w, ly), Offset(x1, ly), 1.2f)
            drawLine(C.hi, Offset(x1, ly), Offset(x1, ly + sp * 0.6f), 1.2f)
        }
    }
}

/**
 * An event's articulations: accent, marcato and staccato (note effects) and the [Mark]s that have a notehead
 * side go at [headSideY], stacking away from the note; fermatas, breaths and ornaments stack up from [topY].
 */
private fun drawMarks(e: Event, x: Float, topY: Float, headSideY: Float, up: Boolean, sp: Float, glyph: (String, Float, Float, Color, Float) -> Unit) {
    val fx = e.notes.flatMap { it.effects }.toSet()
    val side = buildList {
        if (Effect.STACCATO in fx) add(G.STACCATO)
        if (Effect.ACCENT in fx) add(G.ACCENT)
        if (Effect.HEAVY_ACCENT in fx) add(G.MARCATO)
    }.map { if (up) it + 1 else it } + e.marks.filter { !it.alwaysAbove && !it.arpeggio }.map { if (up) it.below else it.above }
    var sy = headSideY
    for (g in side) { glyph(cp(g), x, sy, C.hi, 1f); sy += if (up) sp * 1.2f else -sp * 1.2f }
    var ty = topY
    for (m in e.marks.filter { it.alwaysAbove && !it.arpeggio }) { glyph(cp(m.above), x - if (m.group == com.betterappmafia.apophenia.core.score.MarkGroup.ORNAMENT) 0f else sp * 0.4f, ty, C.hi, 1f); ty -= sp * 1.8f }
}

/** A fretboard diagram: strings lowest first, four frets from the lowest fretted note (a "5fr" label when it isn't the nut). */
private fun DrawScope.drawDiagram(frets: List<Int?>, x: Float, top: Float, sp: Float) {
    val n = frets.size.coerceAtLeast(2)
    val gap = sp * 0.75f; val w = gap * (n - 1); val fh = sp
    val played = frets.filterNotNull().filter { it > 0 }
    val base = if (played.isEmpty() || played.max() <= 4) 1 else played.min()
    val stroke = sp * 0.1f
    for (s in 0 until n) drawLine(C.dim, Offset(x + s * gap, top), Offset(x + s * gap, top + fh * 4), stroke)
    for (f in 0..4) drawLine(C.dim, Offset(x, top + f * fh), Offset(x + w, top + f * fh), if (f == 0 && base == 1) sp * 0.3f else stroke)
    for ((s, f) in frets.withIndex()) {
        val sx = x + s * gap
        when {
            f == null -> { drawLine(C.hi, Offset(sx - sp * 0.25f, top - sp * 0.85f), Offset(sx + sp * 0.25f, top - sp * 0.35f), stroke); drawLine(C.hi, Offset(sx + sp * 0.25f, top - sp * 0.85f), Offset(sx - sp * 0.25f, top - sp * 0.35f), stroke) }
            f == 0 -> drawCircle(C.hi, sp * 0.25f, Offset(sx, top - sp * 0.6f), style = Stroke(stroke))
            else -> drawCircle(C.hi, sp * 0.3f, Offset(sx, top + (f - base + 0.5f) * fh))
        }
    }
    if (base > 1) drawContext.canvas.nativeCanvas.drawText("${base}fr", x + w + sp * 0.3f, top + fh * 0.8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp * 1.1f; color = C.dim.toArgb() })
}

/**
 * Lines over several events, drawn after every bar so they can cross barlines and systems: slurs (on the
 * notehead side), hairpins and pedal below the staff, 8va/15ma above (8vb/15mb below), trill lines above,
 * glissandos from note to note. A line that leaves a system continues on the next one.
 */
private fun DrawScope.drawSpans(
    part: Part, systems: List<List<Int>>, barStart: Map<Int, Float>, barEnd: Map<Int, Float>, geo: Map<Pair<Int, Int>, EvGeo>, sp: Float,
    bottomY: (Int) -> Float, glyph: (String, Float, Float, Color, Float) -> Unit, inks: Inks,
) {
    val flat = part.bars.flatMapIndexed { b, bar -> bar.voices[0].indices.map { b to it } }
    for ((k, pos) in flat.withIndex()) {
        val e = part.bars[pos.first].voices[0][pos.second]
        for ((span, count) in e.spans) {
            val endPos = flat[minOf(k + count - 1, flat.lastIndex)]
            val a = geo[pos] ?: continue; val z = geo[endPos] ?: continue
            for (si in a.system..z.system) {
                val bottom = bottomY(si)
                fun y(step: Int) = bottom - step * sp / 2
                val x0 = if (si == a.system) a.x else barStart.getValue(systems[si].first()) + sp * 0.5f
                val x1 = if (si == z.system) z.x + sp * 1.2f else barEnd.getValue(systems[si].last()) - sp * 0.3f
                val starts = si == a.system; val ends = si == z.system
                when (span) {
                    Span.SLUR -> {
                        val above = !a.up
                        val y0 = if (starts) (if (above) a.top - sp * 0.8f else a.bottom + sp * 0.8f) else if (above) y(10) else y(-2)
                        val y1 = if (ends) (if (above) z.top - sp * 0.8f else z.bottom + sp * 0.8f) else y0
                        val lift = if (above) -sp * 1.6f else sp * 1.6f
                        val path = Path().apply { moveTo(x0 + sp * 0.5f, y0); cubicTo(x0 + (x1 - x0) * 0.3f, minOf(y0, y1, y0 + lift).let { if (above) it + lift / 2 else maxOf(y0, y1) + lift / 2 }, x0 + (x1 - x0) * 0.7f, if (above) minOf(y0, y1) + lift else maxOf(y0, y1) + lift, x1 - sp * 0.4f, y1) }
                        drawPath(path, C.hi, style = Stroke(sp * 0.16f))
                    }
                    Span.CRESC, Span.DIM -> {
                        val hy = y(0) + sp * 3.4f
                        val open = sp * 0.8f
                        // Crescendo opens to the right; a part on a later system starts half open.
                        val l = if (span == Span.CRESC) (if (starts) 0f else open / 2) else (if (starts) open else open / 2)
                        val r = if (span == Span.CRESC) (if (ends) open else open / 2) else (if (ends) 0f else open / 2)
                        drawLine(C.hi, Offset(x0, hy - l), Offset(x1, hy - r), sp * 0.13f)
                        drawLine(C.hi, Offset(x0, hy + l), Offset(x1, hy + r), sp * 0.13f)
                    }
                    Span.OTTAVA_UP, Span.QUINDICESIMA_UP, Span.OTTAVA_DOWN, Span.QUINDICESIMA_DOWN -> {
                        val up = span.octaves > 0
                        val ly = if (up) y(8) - sp * 3.6f else y(0) + sp * 5.6f
                        if (starts) glyph(G.ottava(span), x0, ly + sp * 0.5f, C.hi, 0.75f)
                        val lx = x0 + if (starts) sp * 3.2f else 0f
                        drawLine(C.hi, Offset(lx, ly), Offset(x1, ly), sp * 0.12f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(sp * 0.6f, sp * 0.5f)))
                        if (ends) drawLine(C.hi, Offset(x1, ly), Offset(x1, ly + if (up) sp * 1.0f else -sp * 1.0f), sp * 0.12f)
                    }
                    Span.PEDAL -> {
                        val py = y(0) + sp * 5.8f
                        if (starts) glyph(G.PED, x0 - sp * 0.4f, py, C.hi, 0.7f)
                        val lx = x0 + if (starts) sp * 3.2f else 0f
                        drawLine(C.hi, Offset(lx, py - sp * 0.3f), Offset(x1, py - sp * 0.3f), sp * 0.12f)
                        if (ends) glyph(G.PED_UP, x1, py, C.hi, 0.7f)
                    }
                    Span.TRILL_LINE -> {
                        val ty = y(8) - sp * 3.0f
                        if (starts) glyph(G.TRILL, x0, ty + sp * 0.4f, C.hi, 1f)
                        val path = Path().apply {
                            var xx = x0 + if (starts) sp * 2.6f else 0f; moveTo(xx, ty); var upw = true
                            while (xx < x1) { xx += sp * 0.45f; lineTo(xx, ty + if (upw) -sp * 0.35f else 0f); upw = !upw }
                        }
                        drawPath(path, C.hi, style = Stroke(sp * 0.14f))
                    }
                    Span.GLISS -> {
                        val ya = if (starts) y(a.steps.max()) else y(4)
                        val yz = if (ends) y(z.steps.max()) else ya
                        drawLine(C.hi, Offset(x0 + sp * 1.6f, ya), Offset(x1 - sp * 1.6f, yz), sp * 0.14f)
                        if (starts) {
                            inks.small.color = C.hi.toArgb()
                            drawContext.canvas.nativeCanvas.drawText("gliss.", (x0 + x1) / 2 - sp * 1.5f, (ya + yz) / 2 - sp * 0.6f, inks.small)
                            inks.small.color = C.dim.toArgb()
                        }
                    }
                }
            }
        }
    }
}

/** A flat beam, three and a half spaces beyond the group's outermost note. */
private fun beamY(g: List<Int>, stepsOf: List<List<Int>>, up: Boolean, y: (Int) -> Float, sp: Float): Float {
    val all = g.flatMap { stepsOf[it] }
    return if (up) y(all.max()) - sp * 3.5f else y(all.min()) + sp * 3.5f
}
