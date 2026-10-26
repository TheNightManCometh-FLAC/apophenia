package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T

/** A hairline under a row, the Console's only divider. */
fun Modifier.underline(color: Color = C.line) = drawBehind {
    drawLine(color, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f)
}

/** A hairline along the top edge. */
fun Modifier.drawBehindTop(color: Color) = drawBehind {
    drawLine(color, Offset(0f, 0.5f), Offset(size.width, 0.5f), 1f)
}

/** The outlined square button ("SORT ▾", "‹"). [on] = outlined in copper, never inverted. */
@Composable
fun Sq(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, on: Boolean = false, color: Color = if (on) C.hi else C.text, enabled: Boolean = true) {
    Box(
        modifier
            .heightIn(min = 40.dp)
            .widthIn(min = 44.dp)
            .border(1.dp, if (on) C.acc else C.line2)
            .background(if (on) C.selected else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.button.copy(fontSize = 10.sp, color = if (enabled) color else C.faint), maxLines = 1) }
}

/** The title bar under the two top bars on every screen. */
@Composable
fun TitleBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).underline().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Label column + value, the Console's key-value row. */
@Composable
fun Kv(label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, mark: Boolean = true, value: @Composable ColumnScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().underline().let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = T.label, modifier = Modifier.width(96.dp).padding(start = 12.dp, end = 6.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp), content = value)
        if (onClick != null && mark) Text("▾", style = T.label.copy(fontSize = 10.sp), modifier = Modifier.padding(end = 12.dp))
    }
}

@Composable
fun KvText(label: String, value: String, onClick: (() -> Unit)? = null) =
    // A value that already ends in ▾ or › gets no second ▾ at the edge (the drum chart's rows had two).
    Kv(label, onClick = onClick, mark = !(value.endsWith("▾") || value.endsWith("›"))) { Text(value, style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold)) }

/** One item in a Console menu. */
data class MenuItem(val label: String, val note: String = "", val enabled: Boolean = true, val onClick: () -> Unit)

/** The Console dropdown: square, hairline border, label left, small note right. */
@Composable
fun ConsoleMenu(expanded: Boolean, onDismiss: () -> Unit, title: String? = null, items: List<MenuItem>, header: (@Composable () -> Unit)? = null) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = C.panel2,
        border = BorderStroke(1.dp, C.line2),
        modifier = Modifier.widthIn(min = 230.dp),
    ) {
        if (title != null) Text(title, style = T.label, modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 14.dp))
        header?.invoke()
        items.forEach { item ->
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.label, style = T.body.copy(fontWeight = FontWeight.Bold, color = if (item.enabled) C.hi else C.faint), modifier = Modifier.weight(1f))
                        if (item.note.isNotEmpty()) Text(item.note, style = T.label.copy(letterSpacing = 0.08.em), modifier = Modifier.padding(start = 10.dp))
                    }
                },
                enabled = item.enabled,
                onClick = { onDismiss(); item.onClick() },
                modifier = Modifier.heightIn(min = 46.dp).underline(),
            )
        }
    }
}

/** A Console text field: mono, no box, a label in front. */
@Composable
fun ConsoleField(
    label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "",
    number: Boolean = false, onDone: (() -> Unit)? = null, focus: Boolean = false,
) {
    Row(modifier.fillMaxWidth().underline().heightIn(min = 48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = T.label, modifier = Modifier.padding(end = 10.dp))
        // [focus]: the field takes the cursor and the keyboard when it appears (a name sheet needed a tap first).
        val asked = remember { FocusRequester() }
        if (focus) LaunchedEffect(Unit) { asked.requestFocus() }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = T.body.copy(color = C.faint, fontSize = 14.sp))
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = T.body.copy(color = C.hi, fontSize = 14.sp),
                cursorBrush = SolidColor(C.acc),
                keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
                modifier = Modifier.fillMaxWidth().then(if (focus) Modifier.focusRequester(asked) else Modifier),
            )
        }
    }
}

/** A chip-like toggle used in sheets ("+ BASS", "− VOCAL"). */
@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit, color: Color = if (on) C.hi else C.text) =
    Sq(text, onClick, on = on, color = color, modifier = Modifier.height(34.dp))

/** The big full-width button at the bottom of a sheet. */
@Composable
fun RowScope.BigButton(text: String, onClick: () -> Unit, primary: Boolean = false, enabled: Boolean = true) {
    Box(
        Modifier.weight(1f).height(54.dp)
            .background(if (primary) C.selected else Color.Transparent)
            .drawBehindTop(if (primary) C.acc else C.line)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.button.copy(fontSize = 12.sp, color = if (enabled) C.hi else C.faint)) }
}

/** A sheet that slides over the screen from the bottom, with a scrim; tap the scrim to close. */
@Composable
fun BoxScope.Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.matchParentSize().background(C.scrim).clickable(onClick = onDismiss))
    Column(
        Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.statusBars).fillMaxWidth()
            .heightIn(max = 760.dp)
            .background(C.panel)
            .drawBehindTop(C.acc)
            .clickable(enabled = false) {}
            // Above the gesture bar and the keyboard (edge to edge, the keyboard covered the New project sheet whole).
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
        content = content,
    )
}

/** Peaks drawn as a centred bar per column; [progress] 0..1 tints the played part; [mark] 0..1 draws a line. */
@Composable
fun Waveform(peaks: FloatArray, modifier: Modifier = Modifier, progress: Float = -1f, mark: Float = -1f, color: Color = C.wave) {
    Canvas(modifier) {
        if (peaks.isEmpty()) return@Canvas
        val w = size.width / peaks.size
        val mid = size.height / 2
        for (i in peaks.indices) {
            val h = (peaks[i].coerceIn(0f, 1f) * size.height).coerceAtLeast(2f)
            val x = i * w
            val c = if (progress >= 0 && (i + 0.5f) / peaks.size <= progress) C.acc else color
            drawRect(c, Offset(x, mid - h / 2), androidx.compose.ui.geometry.Size((w - 1f).coerceAtLeast(1f), h))
        }
        if (mark in 0f..1f) drawLine(C.listening, Offset(mark * size.width, 0f), Offset(mark * size.width, size.height), 2f)
        if (progress in 0f..1f) drawLine(C.hi, Offset(progress * size.width, 0f), Offset(progress * size.width, size.height), 1.5f)
    }
}

/** 21-segment level meter, copper at the top segment (clipping warning). */
@Composable
fun LevelLed(level: Float, modifier: Modifier = Modifier, segments: Int = 21) {
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        // dB scale: -60..0 dBFS over the segments
        val db = if (level <= 1e-6f) -120f else 20f * kotlin.math.log10(level)
        val lit = ((db + 60f) / 60f * segments).toInt().coerceIn(0, segments)
        for (i in 0 until segments) {
            val color = when {
                i >= lit -> C.panel2
                i >= segments - 1 -> C.rec
                i >= segments - 4 -> C.acc
                else -> C.wave
            }
            Box(Modifier.weight(1f).height(36.dp).background(color))
        }
    }
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun Line1(text: String, style: androidx.compose.ui.text.TextStyle = T.body, modifier: Modifier = Modifier) =
    Text(text, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)

/**
 * A row that scrolls sideways fades out at the edge it can still scroll to, so it's plain there's more (the tab
 * editor's length and INPUT rows ran off the right edge with nothing to say so). Put it before horizontalScroll.
 */
fun Modifier.scrollCue(state: androidx.compose.foundation.ScrollState): Modifier = drawWithContent {
    drawContent()
    val w = 28.dp.toPx()
    if (state.canScrollForward) drawRect(
        androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(C.bg.copy(alpha = 0f), C.bg), startX = size.width - w, endX = size.width),
        topLeft = Offset(size.width - w, 0f), size = androidx.compose.ui.geometry.Size(w, size.height),
    )
    if (state.canScrollBackward) drawRect(
        androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(C.bg, C.bg.copy(alpha = 0f)), startX = 0f, endX = w),
        size = androidx.compose.ui.geometry.Size(w, size.height),
    )
}
