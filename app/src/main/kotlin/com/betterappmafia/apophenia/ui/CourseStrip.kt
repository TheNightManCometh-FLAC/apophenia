package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.core.catalog.Scales
import com.betterappmafia.apophenia.core.learn.Course
import com.betterappmafia.apophenia.core.learn.CourseSee
import com.betterappmafia.apophenia.core.learn.Key
import com.betterappmafia.apophenia.core.score.Clef
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.FilmSerif
import com.betterappmafia.apophenia.ui.theme.Mono
import com.betterappmafia.apophenia.ui.theme.MusicFont

/*
 * The contents page's pictures (design option C, 1 Nov 2026): one line drawing per chapter, topic and Reference list,
 * painted from the same CourseSee the page itself draws, so the strip shows exactly what the page teaches. Line art in
 * the accent on whatever is behind; nothing plays from here, the page does that.
 */

/** [see] drawn as a strip across the width, [height] tall. */
@Composable
internal fun SeeStrip(see: CourseSee, modifier: Modifier = Modifier, height: Dp = 58.dp, ink: androidx.compose.ui.graphics.Color = C.acc) {
    val text = rememberTextMeasurer()
    Canvas(modifier.fillMaxWidth().height(height)) {
        when (see) {
            is CourseSee.Keys -> keys(ink, see.keys)
            is CourseSee.KeyStaff -> {
                val sc = Scales.byId(see.scaleId)
                keys(ink, (sc.semitones + 12).map { Key(60 + it, "") })
            }
            is CourseSee.Grid -> cells(ink, see.rows.map { it.label to (it.cells.indices.map { c -> c in it.lit }) }, text)
            is CourseSee.Chart -> words(ink, see.chords.map { it.numeral.ifEmpty { it.symbol } }, see.chords.joinToString("  ") { it.symbol }.takeIf { see.chords.any { c -> c.numeral.isNotEmpty() } }, text)
            is CourseSee.Staff -> staff(ink, see.score.parts.firstOrNull(), text)
            is CourseSee.Score -> staff(ink, see.score.parts.firstOrNull(), text)
            is CourseSee.Rhythm -> rhythm(ink, see.bars.take(3).map { (label, _, notes) -> label to notes.map { it.d.length.toDouble() to it.rest } }, text)
            CourseSee.Circle -> circle(ink, text)
            is CourseSee.Table -> words(ink, see.rows.take(5).mapNotNull { it.firstOrNull() }, null, text)
        }
    }
}

private val androidx.compose.ui.graphics.Color.line get() = copy(alpha = 0.45f)

/** Piano keys around the lit notes, centred; lit keys filled, faint (context) ones outlined. */
internal fun DrawScope.keys(ink: androidx.compose.ui.graphics.Color, keys: List<Key>) {
    if (keys.isEmpty()) return
    val ms = keys.map { it.midi }
    val lo = (ms.min() / 12) * 12
    val hi = maxOf(((ms.max() / 12) + 1) * 12, lo + 12)
    val whites = (lo..hi).filter { it % 12 in setOf(0, 2, 4, 5, 7, 9, 11) }
    val by = keys.associateBy { it.midi }
    val w = minOf(size.width / whites.size, size.height * 0.5f)
    val x0 = (size.width - w * whites.size) / 2
    val h = size.height
    whites.forEachIndexed { i, m ->
        val k = by[m]
        val o = Offset(x0 + i * w + 1, 0f); val s = Size(w - 2, h)
        if (k != null && !k.faint) drawRect(ink, o, s) else drawRect(ink.line, o, s, style = Stroke(1.dp.toPx()))
        if (k?.faint == true) drawRect(ink, o, s, style = Stroke(2.dp.toPx()))
    }
    whites.forEachIndexed { i, m ->
        val b = m + 1
        if (m % 12 in setOf(0, 2, 5, 7, 9) && b <= hi) {
            val k = by[b]
            val o = Offset(x0 + (i + 1) * w - w * 0.31f, 0f); val s = Size(w * 0.62f, h * 0.6f)
            drawRect(if (k != null && !k.faint) ink else C.bg, o, s)
            drawRect(if (k?.faint == true) ink else ink.line, o, s, style = Stroke(1.dp.toPx()))
        }
    }
}

/** Rows of cells, lit ones filled: fretboards, scale and chord grids, drum grooves. */
private fun DrawScope.cells(ink: androidx.compose.ui.graphics.Color, rows: List<Pair<String, List<Boolean>>>, text: TextMeasurer) {
    val shown = rows.take(6)
    if (shown.isEmpty()) return
    val labelW = 34.dp.toPx()
    val rh = size.height / shown.size
    val style = TextStyle(fontFamily = Mono, fontSize = (rh * 0.42f / density).coerceAtMost(10f).sp, color = ink.line)
    shown.forEachIndexed { r, (label, lit) ->
        val y = r * rh
        drawText(text, label.take(5), Offset(0f, y + rh * 0.18f), style)
        val cw = (size.width - labelW) / lit.size.coerceAtLeast(1)
        lit.forEachIndexed { c, on ->
            val o = Offset(labelW + c * cw + 1, y + 1); val s = Size(cw - 2, rh - 2)
            if (on) drawRect(ink, o, s) else drawRect(ink.line, o, s, style = Stroke(0.7.dp.toPx()))
        }
    }
}

/** Words set as a title card: numerals or names large in the serif, an optional line of symbols under them. */
private fun DrawScope.words(ink: androidx.compose.ui.graphics.Color, big: List<String>, small: String?, text: TextMeasurer) {
    if (big.isEmpty()) return
    val s = big.joinToString("  ·  ")
    var fs = 26f
    var m = text.measure(s, TextStyle(fontFamily = FilmSerif, fontStyle = FontStyle.Italic, fontSize = fs.sp, color = ink))
    while (m.size.width > size.width && fs > 12f) { fs -= 2f; m = text.measure(s, TextStyle(fontFamily = FilmSerif, fontStyle = FontStyle.Italic, fontSize = fs.sp, color = ink), maxLines = 1) }
    val sub = small?.let { text.measure(it, TextStyle(fontFamily = Mono, fontSize = 9.sp, color = ink.copy(alpha = 0.7f)), maxLines = 1) }
    val total = m.size.height + (sub?.size?.height ?: 0)
    val y = (size.height - total) / 2
    drawText(m, topLeft = Offset(0f, y))
    sub?.let { drawText(it, topLeft = Offset(0f, y + m.size.height)) }
}

/** The first bars of a part on five lines: clef, noteheads (chords stacked), ledger lines, sharps and flats. */
private fun DrawScope.staff(ink: androidx.compose.ui.graphics.Color, part: com.betterappmafia.apophenia.core.score.Part?, text: TextMeasurer) {
    part ?: return
    val gap = size.height / 7f
    val bottom = size.height / 2 + 2 * gap
    for (i in 0 until 5) drawLine(ink.line, Offset(0f, bottom - i * gap), Offset(size.width, bottom - i * gap), 1.dp.toPx())
    val clefGlyph = when (part.clef) { Clef.BASS -> "𝄢"; Clef.PERCUSSION -> ""; else -> "𝄞" }
    if (clefGlyph.isNotEmpty()) drawText(text, clefGlyph, Offset(2f, bottom - gap * 5.6f), TextStyle(fontFamily = MusicFont, fontSize = (gap * 5.2f / density).sp, color = ink))
    // The bottom line's letter position (7 × octave + letter): E4 treble, G2 bass, F3 alto, D3 tenor, the middle line for drums.
    val bottomPos = when (part.clef) { Clef.TREBLE -> 30; Clef.TREBLE_8VB -> 23; Clef.BASS -> 18; Clef.ALTO -> 24; Clef.TENOR -> 22; Clef.PERCUSSION -> 30 }
    val events = part.bars.flatMap { it.voices.first() }.take(14)
    if (events.isEmpty()) return
    val x0 = gap * 4f
    val dx = (size.width - x0) / events.size
    events.forEachIndexed { i, e ->
        val x = x0 + dx * (i + 0.5f)
        if (e.isRest) { drawLine(ink.line, Offset(x - gap * 0.5f, bottom - 2.5f * gap), Offset(x + gap * 0.5f, bottom - 2.5f * gap), 2.dp.toPx()); return@forEachIndexed }
        e.notes.forEach { n ->
            val k = if (part.clef == Clef.PERCUSSION) 4 else 7 * n.pitch.octave + n.pitch.letter.ordinal - bottomPos
            val y = bottom - k * gap / 2
            for (l in -2 downTo k step 2) drawLine(ink.line, Offset(x - gap, bottom - l * gap / 2), Offset(x + gap, bottom - l * gap / 2), 1.dp.toPx())
            for (l in 10..k step 2) drawLine(ink.line, Offset(x - gap, bottom - l * gap / 2), Offset(x + gap, bottom - l * gap / 2), 1.dp.toPx())
            rotate(-20f, Offset(x, y)) { drawOval(ink, Offset(x - gap * 0.62f, y - gap * 0.45f), Size(gap * 1.24f, gap * 0.9f)) }
            val acc = when (n.pitch.alter) { -2 -> "𝄫"; -1 -> "♭"; 1 -> "♯"; 2 -> "𝄪"; else -> "" }
            if (acc.isNotEmpty()) drawText(text, acc, Offset(x - gap * 2.3f, y - gap * 1.4f), TextStyle(fontFamily = MusicFont, fontSize = (gap * 1.8f / density).sp, color = ink))
        }
    }
}

/** Rhythms as lengths: each note a filled bar as long as it lasts, rests left empty, one row per example. */
private fun DrawScope.rhythm(ink: androidx.compose.ui.graphics.Color, rows: List<Pair<String, List<Pair<Double, Boolean>>>>, text: TextMeasurer) {
    if (rows.isEmpty()) return
    val labelW = 58.dp.toPx()
    val rh = size.height / rows.size
    val style = TextStyle(fontFamily = Mono, fontSize = (rh * 0.34f / density).coerceAtMost(9f).sp, color = ink.line)
    rows.forEachIndexed { r, (label, notes) ->
        val y = r * rh
        drawText(text, label.take(9), Offset(0f, y + rh * 0.22f), style, maxLines = 1)
        val total = notes.sumOf { it.first }.takeIf { it > 0 } ?: return@forEachIndexed
        var x = labelW
        val w = size.width - labelW
        notes.forEach { (len, rest) ->
            val nw = (len / total * w).toFloat()
            if (rest) drawRect(ink.line, Offset(x + 1, y + rh * 0.42f), Size(nw - 2, 1.dp.toPx()))
            else { drawRect(ink, Offset(x + 1, y + rh * 0.2f), Size(2.dp.toPx(), rh * 0.6f)); drawRect(ink.copy(alpha = 0.5f), Offset(x + 1, y + rh * 0.38f), Size(nw - 3, rh * 0.24f)) }
            x += nw
        }
    }
}

/** The circle of fifths: twelve majors, C at the top. */
private fun DrawScope.circle(ink: androidx.compose.ui.graphics.Color, text: TextMeasurer) {
    val r = size.height * 0.42f
    val c = Offset(size.width / 2, size.height / 2)
    drawCircle(ink.line, r, c, style = Stroke(1.dp.toPx()))
    Course.circleMajor.forEachIndexed { i, ascii ->
        val k = ascii.take(1) + ascii.drop(1).replace("#", "♯").replace("b", "♭")
        val a = i / 12.0 * Math.PI * 2
        val p = Offset(c.x + (r * Math.sin(a)).toFloat(), c.y - (r * Math.cos(a)).toFloat())
        val m = text.measure(k, TextStyle(fontFamily = FilmSerif, fontStyle = FontStyle.Italic, fontSize = 11.sp, color = if (i == 0) C.bg else ink))
        drawCircle(if (i == 0) ink else C.bg, 8.dp.toPx(), p)
        drawText(m, topLeft = Offset(p.x - m.size.width / 2, p.y - m.size.height / 2))
    }
}
