package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.chord.ChordDialect
import com.betterappmafia.apophenia.core.chord.ChordDisplay
import com.betterappmafia.apophenia.core.chord.ChordStyle
import com.betterappmafia.apophenia.core.chord.Typeset
import com.betterappmafia.apophenia.core.sheet.ChordText

/*
 * Chord symbols typeset (plan §6.2; music-core-and-theory-design §2.2): root and quality at full size, the extension
 * raised, alterations raised in parentheses or stacked, a numeral's figures stacked (V⁶₅), the slash bass at full
 * size, a polychord's upper chord over a horizontal line. Text views get an AnnotatedString (stacks of alterations
 * fall back to parentheses there); the notation canvas draws the real stacks.
 */

/** [text] as the user's style shows it, spelled in [key]; text that isn't a chord symbol comes back as typed. */
fun chordShown(model: AppModel, text: String, key: KeyMode?): AnnotatedString =
    ChordDisplay.of(text, model.chordStyle, key, model.chordDialect)?.annotated() ?: AnnotatedString(ChordText.display(text))

private val SUP = SpanStyle(fontSize = 0.66.em, baselineShift = BaselineShift(0.45f))
private val SUB = SpanStyle(fontSize = 0.66.em, baselineShift = BaselineShift(-0.15f))

fun Typeset.annotated(): AnnotatedString = buildAnnotatedString {
    over?.let { up ->
        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(up.annotated()) }
        append("\n")
    }
    append(root)
    append(quality)
    val raised = sup + when {
        alterations.isEmpty() -> ""
        alterations.size == 1 && !stacked -> alterations[0]
        else -> alterations.joinToString(",", "(", ")")
    }
    if (raised.isNotEmpty()) withStyle(SUP) { append(raised) }
    figures.getOrNull(0)?.let { f -> withStyle(SUP) { append(f) } }
    figures.getOrNull(1)?.let { f -> withStyle(SUB) { append(f) } }
    bass?.let { append("/"); append(it) }
}

/**
 * Draws [t] with its left edge at [x] and its baseline at [y] in [paint]'s size and colour; returns the width.
 * Alterations in a jazz stack and a numeral's figures are columns of small type; a polychord's upper chord sits above
 * a horizontal line.
 */
fun DrawScope.drawTypeset(t: Typeset, x: Float, y: Float, paint: android.graphics.Paint): Float {
    val canvas = drawContext.canvas.nativeCanvas
    val size = paint.textSize
    val small = android.graphics.Paint(paint).apply { textSize = size * 0.66f }
    var cx = x
    fun big(s: String) { if (s.isNotEmpty()) { canvas.drawText(s, cx, y, paint); cx += paint.measureText(s) } }
    fun column(items: List<String>, top: Float) {
        var w = 0f
        items.forEachIndexed { i, s -> canvas.drawText(s, cx, top + i * size * 0.62f, small); w = maxOf(w, small.measureText(s)) }
        cx += w + size * 0.08f
    }
    big(t.root + t.quality)
    if (t.sup.isNotEmpty()) { canvas.drawText(t.sup, cx, y - size * 0.42f, small); cx += small.measureText(t.sup) }
    when {
        t.alterations.isEmpty() -> {}
        t.stacked -> column(t.alterations, y - size * 0.42f - (t.alterations.size - 1) * size * 0.31f)
        else -> {
            val s = if (t.alterations.size == 1) t.alterations[0] else t.alterations.joinToString(",", "(", ")")
            canvas.drawText(s, cx, y - size * 0.42f, small); cx += small.measureText(s)
        }
    }
    if (t.figures.isNotEmpty()) column(t.figures, y - size * 0.42f)
    t.bass?.let { big("/$it") }
    t.over?.let { up ->
        val lineY = y - size * 1.05f
        val w = drawTypeset(up, x, lineY - size * 0.25f, paint)
        canvas.drawLine(x, lineY, x + maxOf(w, cx - x), lineY, paint)
    }
    return cx - x
}

/** CHORDS ▾: how typed chords are read (pop or jazz, the note names) and how they're shown. Applies everywhere. */
@Composable
fun ChordsMenu(model: AppModel) {
    var open by remember { mutableStateOf(false) }
    val d = model.chordDialect
    val s = model.chordStyle
    Box {
        Sq("CHORDS: ${s.label.substringBefore(' ').uppercase()} ▾", { open = true })
        ConsoleMenu(open, { open = false }, "CHORD SYMBOLS", listOf(MenuItem("SHOW AS", "", enabled = false) {}) +
            ChordStyle.entries.map { st -> MenuItem(st.label, if (s == st) "✓" else st.note) { model.chooseChordStyle(st) } } +
            MenuItem("READ TYPED CHORDS AS", "", enabled = false) {} +
            ChordDialect.Style.entries.map { st ->
                MenuItem(st.label, if (d.style == st) "✓" else if (st == ChordDialect.Style.POP) "C2 = ADD9 · C° = TRIAD" else "C2 = SUS2 · C° = °7") { model.chooseChordDialect(d.copy(style = st)) }
            } +
            MenuItem("NOTE NAMES", "", enabled = false) {} +
            ChordDialect.Names.entries.map { n -> MenuItem(n.label, if (d.names == n) "✓" else "") { model.chooseChordDialect(d.copy(names = n)) } },
        )
    }
}
