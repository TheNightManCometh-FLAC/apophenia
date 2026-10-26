package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.score.Part
import com.betterappmafia.apophenia.core.score.PianoRoll
import com.betterappmafia.apophenia.core.score.Score
import com.betterappmafia.apophenia.core.tools.KeySpeller
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import com.betterappmafia.apophenia.core.catalog.Chords

/** Snap values (FL Studio's grid menu, trimmed to what notation can write; the triplet snaps are written as triplets): label and ticks; 0 = one bar. */
private val SNAPS = listOf("Bar" to 0L, "Beat (♩)" to 960L, "♪ 1/8" to 480L, "1/8 triplet" to 320L, "1/16" to 240L, "1/16 triplet" to 160L, "1/32" to 120L)

/**
 * The piano-roll view (plan §10.5a, C4; FL Studio's piano roll): pitch up the side, time along, every voice's
 * notes as bars of the voice's colour. TOOL ▾ Draw (tap empty space to add a note of the snap length in the
 * voice being entered, drag a note to move it, its right end to lengthen it), Select (tap notes to pick several,
 * drag to move them together) or Delete (tap a note). SNAP ▾ the grid, SCALE snaps drawn pitches to the key,
 * EDIT ▾ and TOOLS ▾ FL's menus (quantize, chop, glue, legato, flip, limit, duplicate, transpose). Each edit is
 * written back to the score ([commit]) through `core/score/PianoRoll.kt`, so notation and tab show it at once.
 */
@Composable
fun PianoRollPane(
    score: Score, part: Part, voice: Int, cursor: Pair<Int, Int>?, commit: (Score) -> Unit, play: (Int) -> Unit, notice: (String) -> Unit,
) {
    val density = LocalDensity.current
    val sp = remember(score.key) { KeySpeller(score.key ?: KeyMode.parseOrNull("C major")!!) }
    val notes = remember(score, part) { PianoRoll.notes(score, part) }
    val starts = remember(score, part) { PianoRoll.barStarts(score, part) }
    var tool by remember { mutableStateOf("draw") }
    // Chord stamp: the chord a tap draws, and its family menu then chord menu (long lists open on family rows).
    var stampChord by remember { mutableStateOf("maj") }
    // Paint: the cells the finger has crossed so far (start tick to MIDI), drawn until it lifts.
    var paintCells by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    // FL's ghost notes: the score's other parts, faint behind this one.
    var ghosts by remember { mutableStateOf(true) }
    val ghostNotes = remember(score, part) { score.parts.filter { it !== part }.flatMap { PianoRoll.notes(score, it) } }
    var stampFamilies by remember { mutableStateOf(false) }
    var stampFamily by remember { mutableStateOf<String?>(null) }
    var snap by remember { mutableStateOf(240L) }
    var scaleSnap by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(1f) }
    var selection by remember { mutableStateOf(setOf<Int>()) }
    var toolMenu by remember { mutableStateOf(false) }
    var snapMenu by remember { mutableStateOf(false) }
    var editMenu by remember { mutableStateOf(false) }
    var toolsMenu by remember { mutableStateOf(false) }
    // A note being dragged: its ids, how far in pixels, and whether its end is being pulled.
    var drag by remember { mutableStateOf<Triple<Set<Int>, Offset, Boolean>?>(null) }
    val sel = selection.filter { id -> notes.any { it.id == id } }.toSet()
    fun barAt(t: Long) = (starts.indexOfLast { it <= t }).coerceIn(0, part.bars.lastIndex.coerceAtLeast(0))
    fun grid(t: Long): Long = if (snap == 0L) score.meterAt(barAt(t)).barTicks.toLong() else snap
    fun apply(after: List<PianoRoll.RollNote>, newSel: Set<Int> = sel) {
        commit(PianoRoll.rebuild(score, 0, notes, after, PianoRoll.divisionFor(after))); selection = newSel
    }
    val targets = sel.ifEmpty { notes.map { it.id }.toSet() }
    val targetWord = if (sel.isEmpty()) "ALL" else "${sel.size} SELECTED"

    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
            Sq("TOOL: ${tool.uppercase()} ▾", { toolMenu = true }, on = true)
            ConsoleMenu(toolMenu, { toolMenu = false }, "PIANO ROLL TOOL", listOf(
                MenuItem("Draw", if (tool == "draw") "✓ TAP TO ADD · DRAG TO MOVE" else "TAP TO ADD · DRAG TO MOVE") { tool = "draw" },
                MenuItem("Select", if (tool == "select") "✓ TAP TO PICK SEVERAL" else "TAP TO PICK SEVERAL") { tool = "select" },
                MenuItem("Delete", if (tool == "delete") "✓ TAP A NOTE" else "TAP A NOTE") { tool = "delete" },
                MenuItem("Paint", if (tool == "paint") "✓ DRAG TO PAINT NOTES" else "DRAG TO PAINT NOTES") { tool = "paint" },
                MenuItem("Slice", if (tool == "slice") "✓ TAP A NOTE TO CUT IT" else "TAP A NOTE TO CUT IT") { tool = "slice" },
                MenuItem("Chord stamp", if (tool == "stamp") "✓ TAP FOR A WHOLE CHORD" else "TAP FOR A WHOLE CHORD") { tool = "stamp" },
            ))
        }
        if (tool == "stamp") Box {
            Sq("CHORD: ${chordName(stampChord)} ▾", { stampFamilies = true }, on = true)
            ConsoleMenu(stampFamilies, { stampFamilies = false }, "CHORD STAMP", Chords.all.groupBy { it.family }.map { (fam, list) ->
                MenuItem(fam.replace('-', ' ').replaceFirstChar(Char::uppercase), "${list.size} ›") { stampFamily = fam }
            })
            ConsoleMenu(stampFamily != null, { stampFamily = null }, stampFamily?.replace('-', ' ')?.uppercase(), Chords.all.filter { it.family == stampFamily }.map { c ->
                MenuItem(chordName(c.id), if (c.id == stampChord) "✓" else com.betterappmafia.apophenia.core.learn.accidentals(c.degrees.joinToString(" "))) { stampChord = c.id }
            })
        }
        Box {
            Sq("SNAP: ${SNAPS.first { it.second == snap }.first.uppercase()} ▾", { snapMenu = true })
            ConsoleMenu(snapMenu, { snapMenu = false }, "SNAP TO GRID", SNAPS.map { (label, t) -> MenuItem(label, if (snap == t) "✓" else "") { snap = t } })
        }
        Sq(if (scaleSnap) "SCALE ✓" else "SCALE", { if (score.key == null) notice("Snap to scale needs a key: ADD ▾ › Clefs · keys · meters.") else scaleSnap = !scaleSnap }, on = scaleSnap)
        if (score.parts.size > 1) Sq(if (ghosts) "GHOSTS ✓" else "GHOSTS", { ghosts = !ghosts }, on = ghosts)
        Box {
            Sq("EDIT ▾", { editMenu = true })
            ConsoleMenu(editMenu, { editMenu = false }, "EDIT · $targetWord", listOf(
                MenuItem("Select all", "${notes.size}") { selection = notes.map { it.id }.toSet() },
                MenuItem("Deselect", "", enabled = sel.isNotEmpty()) { selection = emptySet() },
                MenuItem("Duplicate", "AFTER IT", enabled = sel.isNotEmpty()) { val (n, ids) = PianoRoll.duplicate(notes, sel); apply(n, ids) },
                MenuItem("Delete", targetWord, enabled = sel.isNotEmpty()) { apply(PianoRoll.delete(notes, sel), emptySet()) },
                MenuItem("Transpose up a semitone", "+1") { apply(PianoRoll.move(notes, targets, 0, 1, sp)) },
                MenuItem("Transpose down a semitone", "−1") { apply(PianoRoll.move(notes, targets, 0, -1, sp)) },
                MenuItem("Transpose up an octave", "+12") { apply(PianoRoll.move(notes, targets, 0, 12, sp)) },
                MenuItem("Transpose down an octave", "−12") { apply(PianoRoll.move(notes, targets, 0, -12, sp)) },
                MenuItem("Shift right", "ONE SNAP") { apply(PianoRoll.move(notes, targets, grid(0), 0, sp)) },
                MenuItem("Shift left", "ONE SNAP") { apply(PianoRoll.move(notes, targets, -grid(0), 0, sp)) },
                MenuItem("Velocity from the dynamics", "CLEAR THE LANE") { apply(PianoRoll.setVelocity(notes, targets, null)) },
            ))
        }
        Box {
            Sq("TOOLS ▾", { toolsMenu = true })
            val inst = part.instrument
            val lo = inst?.let { it.tuning.strings.minOf { s -> s.midi } + it.capo } ?: 36
            val hi = inst?.let { it.tuning.strings.maxOf { s -> s.midi } + it.capo + it.frets } ?: 96
            ConsoleMenu(toolsMenu, { toolsMenu = false }, "TOOLS · $targetWord", listOf(
                MenuItem("Quantize", "STARTS AND ENDS TO THE SNAP") { apply(PianoRoll.quantize(notes, targets, grid(0))) },
                MenuItem("Quantize starts", "LENGTHS KEPT") { apply(PianoRoll.quantize(notes, targets, grid(0), ends = false)) },
                MenuItem("Chop", "INTO SNAP LENGTHS") { apply(PianoRoll.chop(notes, targets, grid(0))) },
                MenuItem("Glue", "SAME PITCH, TOUCHING") { apply(PianoRoll.glue(notes, targets)) },
                MenuItem("Legato", "HOLD TO THE NEXT NOTE") { apply(PianoRoll.legato(notes, targets)) },
                MenuItem("Flip", "MIRROR THE PITCHES") { apply(PianoRoll.flip(notes, targets, sp)) },
            ) + PianoRoll.ArpPattern.entries.map { pat ->
                MenuItem("Arpeggiate: ${pat.label.lowercase()}", "CHORDS INTO SNAP STEPS") { apply(PianoRoll.arpeggiate(notes, targets, grid(0), pat)) }
            } + listOf(
                MenuItem(if (inst != null) "Limit to the instrument" else "Limit to C2–C7", "BY OCTAVES") { apply(PianoRoll.limit(notes, targets, lo, hi, sp)) },
            ))
        }
        Sq("ZOOM −", { zoom = (zoom / 1.4f).coerceAtLeast(0.35f) })
        Sq("ZOOM +", { zoom = (zoom * 1.4f).coerceAtMost(4f) })
    }

    // Geometry: rows of semitones from the lowest note shown up to the highest; a quarter is 40 dp at zoom 1.
    val rowH = with(density) { 13.dp.toPx() }
    val keyW = with(density) { 38.dp.toPx() }
    val perTick = with(density) { 40.dp.toPx() } * zoom / 960f
    val inst = part.instrument
    val lowNote = (notes.minOfOrNull { it.pitch.midi } ?: 60) - 3
    val highNote = (notes.maxOfOrNull { it.pitch.midi } ?: 72) + 3
    val lo = minOf(lowNote, inst?.let { it.tuning.strings.minOf { s -> s.midi } } ?: 55, highNote - 24)
    val hi = maxOf(highNote, lo + 24)
    val rows = hi - lo + 1
    val totalTicks = maxOf(starts.last(), (notes.maxOfOrNull { it.end } ?: 0L)) + 3840L
    val widthPx = totalTicks * perTick
    val heightDp = with(density) { (rows * rowH).toDp() }
    fun yOf(midi: Int) = (hi - midi) * rowH
    fun midiAt(y: Float) = (hi - (y / rowH).toInt()).coerceIn(lo, hi)
    val scalePcs = remember(score.key) {
        score.key?.let { k -> com.betterappmafia.apophenia.core.catalog.Scales.byId(k.scaleId).degrees.map { Math.floorMod(k.tonic.pitchClass + it.interval.semitones, 12) }.toSet() }
    }
    val cursorTick = cursor?.let { (b, i) -> starts.getOrNull(b)?.plus(part.bars.getOrNull(b)?.voices?.get(0)?.take(i)?.sumOf { e -> (e.length.toDouble() * 3840).roundToLong() } ?: 0L) }
    val slop = LocalViewConfiguration.current.touchSlop
    val toolNow by rememberUpdatedState(tool)
    val stampNow by rememberUpdatedState(stampChord)
    val notesNow by rememberUpdatedState(notes)
    val selNow by rememberUpdatedState(sel)
    val applyNow by rememberUpdatedState<(List<PianoRoll.RollNote>, Set<Int>) -> Unit>({ a, s -> apply(a, s) })
    // The roll and the velocity lane scroll together.
    val hScroll = rememberScrollState()

    Row(Modifier.fillMaxWidth()) {
        // The keyboard down the side; C's named.
        Canvas(Modifier.width(38.dp).height(heightDp)) {
            for (m in lo..hi) {
                val black = Math.floorMod(m, 12) in setOf(1, 3, 6, 8, 10)
                drawRect(if (black) C.bg else C.hi.copy(alpha = 0.8f), Offset(0f, yOf(m) + 0.5f), Size(if (black) keyW * 0.62f else keyW, rowH - 1f))
                if (Math.floorMod(m, 12) == 0) drawContext.canvas.nativeCanvas.drawText("C${m / 12 - 1}", keyW * 0.66f, yOf(m) + rowH * 0.8f,
                    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = rowH * 0.7f; color = C.bg.toArgb() })
            }
        }
        Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
            Canvas(
                Modifier.width(with(density) { widthPx.toDp() }).height(heightDp)
                    .semantics { contentDescription = "Piano roll of ${part.name}: ${notes.size} notes." }
                    .pointerInput(score, perTick, lo, hi, snap, scaleSnap, voice) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val p0 = down.position
                            val t0 = (p0.x / perTick).toLong()
                            val hit = notesNow.lastOrNull { n -> t0 >= n.start && t0 < n.end && midiAt(p0.y) == n.pitch.midi }
                            if (toolNow == "paint") {
                                // Paint: every grid cell the finger crosses gets a note at the pitch it's over there.
                                down.consume()
                                val cells = LinkedHashMap<Long, Int>()
                                fun mark(pos: Offset) {
                                    val t = (pos.x / perTick).toLong().coerceAtLeast(0)
                                    val bar = barAt(t); val g = grid(t)
                                    val st = starts[bar] + ((t - starts[bar]) / g) * g
                                    val m = if (scaleSnap) PianoRoll.snapToScale(midiAt(pos.y), score.key) else midiAt(pos.y)
                                    if (cells[st] != m) { if (st !in cells) play(m); cells[st] = m; paintCells = cells.toMap() }
                                }
                                mark(p0)
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!c.pressed) break
                                    c.consume(); mark(c.position)
                                }
                                paintCells = emptyMap()
                                val before = notesNow
                                val after = PianoRoll.paint(before, cells, grid(cells.keys.first()), voice, sp)
                                if (after.size > before.size) applyNow(after, after.drop(before.size).map { it.id }.toSet())
                                return@awaitEachGesture
                            }
                            if (toolNow == "slice" && hit != null) {
                                down.consume()
                                val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                                if ((up.position - p0).getDistance() > slop) return@awaitEachGesture
                                val g = grid(t0)
                                val at = (Math.round(t0.toDouble() / g) * g).coerceIn(hit.start + 1, hit.end - 1)
                                applyNow(PianoRoll.slice(notesNow, hit.id, at), selNow)
                                return@awaitEachGesture
                            }
                            if (hit == null) {
                                // Empty grid: a tap draws (or clears the selection); a drag scrolls.
                                val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                                if ((up.position - p0).getDistance() > slop) return@awaitEachGesture
                                when (toolNow) {
                                    "draw" -> {
                                        val g = grid(t0)
                                        val bar = barAt(t0)
                                        val st = starts[bar] + ((t0 - starts[bar]) / g) * g
                                        val m = if (scaleSnap) PianoRoll.snapToScale(midiAt(p0.y), score.key) else midiAt(p0.y)
                                        play(m)
                                        val added = PianoRoll.add(notesNow, st, g, m, voice, sp)
                                        applyNow(added, setOf(added.last().id))
                                    }
                                    "stamp" -> {
                                        val g = grid(t0)
                                        val bar = barAt(t0)
                                        val st = starts[bar] + ((t0 - starts[bar]) / g) * g
                                        val m = if (scaleSnap) PianoRoll.snapToScale(midiAt(p0.y), score.key) else midiAt(p0.y)
                                        val before = notesNow
                                        val added = PianoRoll.stamp(before, st, g, sp.spell(m), Chords.byId(stampNow), voice)
                                        added.drop(before.size).forEach { play(it.pitch.midi) }
                                        applyNow(added, added.drop(before.size).map { it.id }.toSet())
                                    }
                                    else -> selection = emptySet()
                                }
                                return@awaitEachGesture
                            }
                            down.consume()
                            if (toolNow == "delete") { applyNow(PianoRoll.delete(notesNow, setOf(hit.id)), selNow - hit.id); waitForUpOrCancellation(); return@awaitEachGesture }
                            val edge = (hit.end * perTick - p0.x) < with(density) { 12.dp.toPx() } && hit.length * perTick > with(density) { 24.dp.toPx() }
                            val ids = if (hit.id in selNow) selNow else setOf(hit.id)
                            var total = Offset.Zero; var moved = false
                            while (true) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!c.pressed) break
                                total += c.positionChange(); c.consume()
                                if (!moved && total.getDistance() > slop) moved = true
                                if (moved) drag = Triple(ids, total, edge)
                            }
                            drag = null
                            if (!moved) {
                                play(hit.pitch.midi)
                                selection = if (toolNow == "select") (if (hit.id in selNow) selNow - hit.id else selNow + hit.id) else setOf(hit.id)
                                return@awaitEachGesture
                            }
                            val g = grid(hit.start)
                            val dt = ((total.x / perTick) / g).roundToLong() * g
                            if (edge) applyNow(PianoRoll.resize(notesNow, ids, dt, g), ids)
                            else {
                                var semis = -(total.y / rowH).roundToInt()
                                if (scaleSnap && semis != 0) semis = PianoRoll.snapToScale(hit.pitch.midi + semis, score.key) - hit.pitch.midi
                                if (dt != 0L || semis != 0) { if (semis != 0) play(hit.pitch.midi + semis); applyNow(PianoRoll.move(notesNow, ids, dt, semis, sp), ids) }
                            }
                        }
                    },
            ) {
                // Rows: black-key rows darker, the key's scale tones lit (FL's scale highlighting).
                for (m in lo..hi) {
                    val pc = Math.floorMod(m, 12)
                    val inScale = scalePcs?.contains(pc) == true
                    drawRect(if (pc in setOf(1, 3, 6, 8, 10)) C.bg else C.panel, Offset(0f, yOf(m)), Size(size.width, rowH))
                    if (inScale) drawRect(C.acc.copy(alpha = 0.06f), Offset(0f, yOf(m)), Size(size.width, rowH))
                    if (pc == 0) drawLine(C.line2, Offset(0f, yOf(m) + rowH), Offset(size.width, yOf(m) + rowH), 1f)
                }
                // Bars, beats and the snap grid.
                for ((b, st) in starts.dropLast(1).withIndex()) {
                    val m = score.meterAt(b)
                    for (bt in m.beatStarts) drawLine(C.line, Offset((st + (bt.toDouble() * 3840).roundToLong()) * perTick, 0f), Offset((st + (bt.toDouble() * 3840).roundToLong()) * perTick, size.height), 1f)
                    if (snap in 1..479) { var t = st; while (t < st + m.barTicks) { drawLine(C.line.copy(alpha = 0.4f), Offset(t * perTick, 0f), Offset(t * perTick, size.height), 1f); t += snap } }
                    drawLine(C.dim, Offset(st * perTick, 0f), Offset(st * perTick, size.height), 2f)
                    drawContext.canvas.nativeCanvas.drawText("${b + 1}", st * perTick + 4f, rowH * 0.8f,
                        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = rowH * 0.8f; color = C.faint.toArgb() })
                }
                cursorTick?.let { drawLine(C.acc, Offset(it * perTick, 0f), Offset(it * perTick, size.height), 3f) }
                if (ghosts) for (n in ghostNotes) drawRoundRect(C.dim.copy(alpha = 0.5f), Offset(n.start * perTick + 1f, yOf(n.pitch.midi) + 1f), Size(maxOf(4f, n.length * perTick) - 2f, rowH - 2f), CornerRadius(3f), style = Stroke(1.5f))
                // Notes, other voices dimmer than the one being entered; the dragged ones where the finger is.
                val d = drag
                for (n in notes.sortedBy { if (it.voice == voice) 1 else 0 }) {
                    val dragging = d != null && n.id in d.first
                    val dx = if (dragging && !d!!.third) d.second.x else 0f
                    val dy = if (dragging && !d!!.third) d.second.y else 0f
                    val dw = if (dragging && d!!.third) d.second.x else 0f
                    val col = VOICE_COLORS[n.voice.coerceIn(0, 3)].let { if (n.voice == voice) it else it.copy(alpha = 0.45f) }
                    val x = n.start * perTick + dx; val y = yOf(n.pitch.midi) + dy
                    val w = maxOf(4f, n.length * perTick + dw)
                    drawRoundRect(col, Offset(x + 1f, y + 1f), Size(w - 2f, rowH - 2f), CornerRadius(3f))
                    if (n.id in sel) drawRoundRect(C.hi, Offset(x + 1f, y + 1f), Size(w - 2f, rowH - 2f), CornerRadius(3f), style = Stroke(2.5f))
                    if (w > rowH * 3) drawContext.canvas.nativeCanvas.drawText(KeyMode.displayName(n.pitch.name) + n.pitch.octave, x + 4f, y + rowH * 0.78f,
                        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = rowH * 0.7f; color = C.bg.toArgb() })
                }
                // The stroke being painted.
                if (paintCells.isNotEmpty()) { val g = grid(paintCells.keys.first()); for ((st, m) in paintCells) drawRoundRect(VOICE_COLORS[voice.coerceIn(0, 3)].copy(alpha = 0.6f), Offset(st * perTick + 1f, yOf(m) + 1f), Size(maxOf(4f, g * perTick) - 2f, rowH - 2f), CornerRadius(3f)) }
            }
        }
    }
    // The velocity lane (FL's event editor under the roll): a stem at each note's start, as tall as its velocity.
    // A filled head is a velocity set here; a hollow one is what the dynamics and accents give it. Touch a stem
    // (a chord's stems together, or the selection when the stem is in it) and slide up or down; let go to set it.
    val laneH = with(density) { 72.dp.toPx() }
    val played = remember(score, notes) { notes.associate { it.id to PianoRoll.playedVelocity(score, 0, it) } }
    var velDrag by remember { mutableStateOf<Pair<Set<Int>, Int>?>(null) }
    val playedNow by rememberUpdatedState(played)
    Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Box(Modifier.width(38.dp).height(72.dp), contentAlignment = Alignment.Center) { Text("VEL", style = T.label.copy(fontSize = 8.sp)) }
        Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
            Canvas(
                Modifier.width(with(density) { widthPx.toDp() }).height(72.dp).background(C.panel)
                    .semantics { contentDescription = "Velocity lane: ${notes.count { it.note.velocity != null }} of ${notes.size} notes set here." }
                    .pointerInput(score, perTick, voice) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val reach = 10.dp.toPx()
                            val near = notesNow.filter { it.voice == voice }.minByOrNull { kotlin.math.abs(it.start * perTick - down.position.x) }
                                ?.takeIf { kotlin.math.abs(it.start * perTick - down.position.x) < reach } ?: return@awaitEachGesture
                            down.consume()
                            val ids = if (near.id in selNow) selNow else notesNow.filter { it.voice == voice && it.start == near.start }.map { it.id }.toSet()
                            fun vAt(y: Float) = ((1f - y / laneH) * 127f).roundToInt().coerceIn(1, 127)
                            velDrag = ids to vAt(down.position.y)
                            while (true) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!c.pressed) break
                                c.consume()
                                velDrag = ids to vAt(c.position.y)
                            }
                            val v = velDrag?.second
                            velDrag = null
                            if (v != null) { notice("Velocity $v"); applyNow(PianoRoll.setVelocity(notesNow, ids, v), selNow) }
                        }
                    },
            ) {
                for (st in starts) drawLine(C.line, Offset(st * perTick, 0f), Offset(st * perTick, size.height), 1f)
                drawLine(C.line.copy(alpha = 0.5f), Offset(0f, size.height * (1f - 80f / 127f)), Offset(size.width, size.height * (1f - 80f / 127f)), 1f)
                val vd = velDrag
                for (n in notes.sortedBy { if (it.voice == voice) 1 else 0 }) {
                    val own = n.note.velocity
                    val v = if (vd != null && n.id in vd.first) vd.second else own ?: playedNow[n.id] ?: 80
                    val col = VOICE_COLORS[n.voice.coerceIn(0, 3)].let { if (n.voice == voice) it else it.copy(alpha = 0.4f) }.let { if (n.id in sel) C.hi else it }
                    val x = n.start * perTick + 2f
                    val y = size.height * (1f - v / 127f)
                    drawLine(col, Offset(x, size.height), Offset(x, y), 2.5f)
                    if (own != null || (vd != null && n.id in vd.first)) drawCircle(col, 4.5f * density.density, Offset(x, y))
                    else drawCircle(col, 4f * density.density, Offset(x, y), style = Stroke(1.5f * density.density))
                }
            }
        }
    }
    Text(
        when (tool) {
            "draw" -> "Draw: tap empty space for a note of the snap length in voice ${voice + 1}; drag a note to move it, its right end to lengthen it. Drag empty space to scroll. Slide a stem in VEL to set how hard it plays."
            "stamp" -> "Chord stamp: tap empty space for the chord picked in CHORD ▾, its root on the row you tap, every note the snap length, spelled from the root. Drag a note to move it."
            "paint" -> "Paint: drag across the grid and every snap cell you cross gets a note at the pitch under your finger. Switch to Draw to scroll."
            "slice" -> "Slice: tap inside a note to cut it in two there (at the nearest snap line)."
            "select" -> "Select: tap notes to pick several (EDIT ▾ and TOOLS ▾ then work on them); drag one to move them all."
            else -> "Delete: tap a note to take it out."
        } + " Notation and tab show every change. Bars an edit doesn't reach are left exactly as they were.",
        style = T.small, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** A chord quality as written after a root (maj7, m7♭5, 7♯9), or "Major". */
private fun chordName(id: String) = Chords.byId(id).symbols.first().removePrefix("C").ifEmpty { "Major" }.let { com.betterappmafia.apophenia.core.learn.accidentals(it) }
