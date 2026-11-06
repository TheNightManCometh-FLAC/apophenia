package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.learn.ScalePages
import com.betterappmafia.apophenia.core.score.Notation
import kotlin.math.atan2
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.core.catalog.Chords
import com.betterappmafia.apophenia.core.catalog.Scales
import com.betterappmafia.apophenia.core.interval.Interval
import com.betterappmafia.apophenia.core.learn.Course
import com.betterappmafia.apophenia.core.learn.CourseCategory
import com.betterappmafia.apophenia.core.learn.CourseCheck
import com.betterappmafia.apophenia.core.learn.CoursePage
import com.betterappmafia.apophenia.core.learn.CourseSee
import com.betterappmafia.apophenia.core.learn.CourseTarget
import com.betterappmafia.apophenia.core.learn.EarAudio
import com.betterappmafia.apophenia.core.learn.Play
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.betterappmafia.apophenia.core.learn.InYours
import com.betterappmafia.apophenia.core.learn.RefHit
import com.betterappmafia.apophenia.core.learn.RefKind
import com.betterappmafia.apophenia.core.learn.RefSound
import com.betterappmafia.apophenia.core.learn.Reference
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import kotlin.random.Random

/*
 * The Course (plan §8; C4 'learn', 'cat' and 'lesson'): category tiles, a category's topics with LEVEL ▾, and the lesson
 * page, Explain → See → Hear → Try → Check by ear → Related, with ⋯ for the previous and next page. Levels are only a
 * guide; every page is open.
 */

/**
 * Theory basics as a contents page (design option C, then plates, his picks 1 Nov 2026). Each chapter opens on a painted
 * plate: the Film painter's long exposure in the chapter's own colour, its light rising and falling with the chapter's
 * topic levels, and the chapter's drawing in cream over it. Then the title card, topics, pages and levels, and every
 * topic named, so you see what a chapter teaches before opening it.
 */
@Composable
internal fun CourseHome(model: AppModel) {
    Course.categories.forEachIndexed { i, c ->
        val pages = c.topics.sumOf { it.pages.size }
        val (title, sub) = c.name.split(" · ", limit = 2).let { it[0] to it.getOrNull(1) }
        Column(Modifier.fillMaxWidth().underline().clickable(role = Role.Button) { model.courseCat = c.id }.padding(bottom = 16.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 7f).background(PLATE_BLACK)) {
                FilmBackdrop(remember(c.id) { plate(i, c) }, Modifier.fillMaxSize())
                Course.cover(c)?.let { SeeStrip(it, Modifier.align(Alignment.Center).padding(horizontal = 48.dp), height = 76.dp, ink = PLATE_CREAM) }
                Text("${i + 1}", style = chapterNumber.copy(color = PLATE_CREAM), modifier = Modifier.padding(start = 14.dp, top = 6.dp))
            }
            Column(Modifier.padding(horizontal = 14.dp).padding(top = 12.dp)) {
                Text(title, style = T.display)
                Text(listOfNotNull(sub?.uppercase(), "${c.topics.size} TOPICS", if (pages > 0) "$pages PAGES" else "TO BE WRITTEN").joinToString(" · ") + "  " + c.range,
                    style = T.label, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                Text(topicList(c.topics.map { it.name }), style = T.small.copy(color = C.text, lineHeight = 18.sp))
            }
        }
    }
    Kv("LEVELS") { Text("● to ●●●●● is only a guide to how complex a page is. Everything is open.", style = T.small.copy(lineHeight = 16.sp)) }
}

private val PLATE_BLACK = androidx.compose.ui.graphics.Color(0xFF0A0A0B)
private val PLATE_CREAM = androidx.compose.ui.graphics.Color(0xFFF6F2EA)

/** [Claude] each chapter's colour, as the painter's key hue (pitch class × 30°), spread so neighbours differ. */
private val PLATE_HUE = intArrayOf(7, 0, 9, 5, 1, 11, 3, 2, 8)

/**
 * A chapter's plate: the painter's long-exposure frame, seeded by the chapter, its light following the topic levels in
 * order (a chapter that climbs from ● to ●●●●● burns brighter as it goes). Same input, same pixels, painted once and cached.
 */
private fun plate(i: Int, c: CourseCategory): com.betterappmafia.apophenia.core.art.FilmFrame.Input {
    val levels = c.topics.map { it.level }.ifEmpty { listOf(1) }
    val env = FloatArray(96) { x -> 0.25f + 0.15f * levels[minOf(levels.size - 1, x * levels.size / 96)] }
    return com.betterappmafia.apophenia.core.art.FilmFrame.Input(com.betterappmafia.apophenia.core.art.FilmFrame.Kind.TAKE, 7000L + i, PLATE_HUE[i % 9], false, null, listOf(env))
}

private val chapterNumber get() = T.display.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Normal, fontSize = 40.sp, color = C.acc)

/** Topic names run together with quiet dots between them. */
private fun topicList(names: List<String>) = androidx.compose.ui.text.buildAnnotatedString {
    names.forEachIndexed { i, n ->
        if (i > 0) { pushStyle(androidx.compose.ui.text.SpanStyle(color = C.faint)); append("  ·  "); pop() }
        append(n)
    }
}

/** A chapter's topics, filtered by LEVEL ▾: number, title card, what it covers, and the topic's own first picture. A topic opens its first page. */
@Composable
internal fun CourseTopics(model: AppModel, cat: CourseCategory) {
    var menu by remember { mutableStateOf(false) }
    val levels = listOf(0 to "All", 1 to "● only", 2 to "●● only", 3 to "●●● only", 4 to "●●●● and up")
    val chapter = Course.categories.indexOf(cat) + 1
    Column(Modifier.fillMaxSize()) {
        TitleBar {
            Sq("‹", { model.courseCat = null })
            Text(cat.name.substringBefore(" · ").uppercase(), style = T.title, modifier = Modifier.weight(1f), maxLines = 1)
            Box {
                Sq("LEVEL: ${levels.first { it.first == model.courseLevel }.second.uppercase()} ▾", { menu = true })
                ConsoleMenu(menu, { menu = false }, "SHOW LEVEL", levels.map { (l, name) ->
                    MenuItem(name, if (l == model.courseLevel) "✓" else "") { model.courseLevel = l }
                })
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            val shown = Course.shown(cat.topics, model.courseLevel)
            if (shown.isEmpty()) Text("No topics at this level here.", style = T.small, modifier = Modifier.padding(14.dp))
            shown.forEach { t ->
                val written = t.pages.isNotEmpty()
                Row(
                    Modifier.fillMaxWidth().underline()
                        .clickable(enabled = written, role = Role.Button) { model.courseRoot = null; model.coursePage = t.pages.first().id }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    Text("$chapter.${cat.topics.indexOf(t) + 1}", style = chapterNumber.copy(fontSize = 22.sp, color = if (written) C.acc else C.faint), modifier = Modifier.width(48.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.name, style = T.display.copy(fontSize = 22.sp, lineHeight = 24.sp, color = if (written) C.hi else C.dim))
                        Text(t.note, style = T.small.copy(color = C.text), modifier = Modifier.padding(top = 2.dp))
                        Text((if (written) "${t.pages.size} PAGE${if (t.pages.size > 1) "S" else ""}" else "TO BE WRITTEN") + "  " + Course.gauge(t.level), style = T.label, modifier = Modifier.padding(top = 4.dp))
                        if (written) Course.cover(t)?.let { SeeStrip(it, Modifier.padding(top = 10.dp), height = 64.dp) }
                    }
                }
            }
            Gap(24.dp)
        }
    }
}

/** One lesson page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LessonView(model: AppModel, page: CoursePage) {
    val topic = Course.topicOf(page.id)!!
    val cat = Course.categoryOf(page.id)!!
    val prev = Course.previous(page.id)
    val next = Course.next(page.id)
    val root = model.courseRoot ?: PitchName.parse(page.tryIt?.root ?: "C")
    val rootMidi = RefSound.midis(root.pitchClass, listOf(0), 60).first()
    var menu by remember { mutableStateOf(false) }
    fun open(p: CoursePage) { model.courseRoot = null; model.coursePage = p.id }
    fun go(t: CourseTarget) = when (t) {
        is CourseTarget.Ref -> Reference.item(t.kind, t.id)?.let { model.refPage = RefHit(it, PitchName.parse(t.root)) }
        is CourseTarget.Ear -> { model.show(Mode.EAR); com.betterappmafia.apophenia.core.learn.Ear.drill(t.drill)?.let { model.startEar(it) } }
        is CourseTarget.Page -> Course.page(t.id)?.let { open(it) }
    }
    Column(Modifier.fillMaxSize()) {
        TitleBar {
            Sq(if (model.earStudyReturn) "‹ EAR" else "‹", { if (model.earStudyReturn) model.returnToEar() else { model.coursePage = null; model.courseCat = cat.id } })
            Text(page.title.uppercase(), style = T.title, modifier = Modifier.weight(1f), maxLines = 1)
            Text(Course.gauge(page.level), style = T.label.copy(color = C.acc))
            Box {
                Sq("⋯", { menu = true })
                ConsoleMenu(menu, { menu = false }, "THIS PAGE", buildList {
                    prev?.let { add(MenuItem("Previous: ${it.title}", "‹") { open(it) }) }
                    next?.let { add(MenuItem("Next: ${it.title}", "›") { open(it) }) }
                    page.related.forEach { l -> add(MenuItem(l.label, "›") { go(l.target) }) }
                })
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            val at = topic.pages.indexOf(page)
            Text("${cat.name.uppercase()} › ${topic.name.uppercase()} · PAGE ${at + 1} OF ${topic.pages.size}", style = T.label, modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 14.dp))
            Section("EXPLAIN")
            Text(page.explain, style = T.body.copy(lineHeight = 20.sp), modifier = Modifier.padding(horizontal = 14.dp))
            page.steps.forEachIndexed { i, s ->
                Text("${i + 1}", style = T.label.copy(color = C.acc), modifier = Modifier.padding(start = 14.dp, top = 22.dp))
                Text(s.text, style = T.body.copy(lineHeight = 20.sp), modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp))
                s.see?.let { See(model, page, it, root) }
                if (s.hear.isNotEmpty()) FlowRow(Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.hear.forEach { h -> Sq("▶ ${h.label}", { hearCourse(model, page, h.label, h.make(rootMidi, Random(0))) }) }
                }
            }
            page.see?.let { See(model, page, it, root) }
            if (page.inShort.isNotEmpty()) {
                Section("IN SHORT")
                Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    page.inShort.forEach { Row { Text("— ", style = T.body.copy(color = C.acc)); Text(it, style = T.body.copy(lineHeight = 20.sp)) } }
                }
            }
            if (page.hear.isNotEmpty()) {
                Section(if (page.steps.isEmpty()) "HEAR" else "HEAR IT IN ${refName(model, root).uppercase()} · CHANGE THE KEY UNDER TRY")
                FlowRow(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    page.hear.forEach { s -> Sq("▶ ${s.label}", { hearCourse(model, page, s.label, s.make(rootMidi, Random(0))) }) }
                }
            }
            page.tryIt?.let { t ->
                var keys by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 12.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("TRY · ${t.prompt}", style = T.label.copy(color = C.acc), modifier = Modifier.weight(1f))
                    if (!t.fixed) Box {
                        Sq("KEY: ${refName(model, root)} ▾", { keys = true })
                        ConsoleMenu(keys, { keys = false }, "START ON", Reference.roots.map { r ->
                            MenuItem(refName(model, r), if (r == root) "✓" else "") { model.courseRoot = r }
                        })
                    }
                }
                ShowRef(model, t.kind, t.id, root)
            }
            page.check?.let { Check(model, page, it) }
            if (page.related.isNotEmpty()) {
                Section("RELATED")
                FlowRow(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    page.related.forEach { l -> Sq("${l.label} →", { go(l.target) }) }
                }
            }
            page.yours?.let { (kind, id) ->
                Gap(10.dp)
                Yours(model, root) { sources ->
                    when (kind) {
                        RefKind.INTERVAL -> InYours.interval(Interval.parse(id), sources)
                        RefKind.SCALE -> InYours.scale(Scales.byId(id), sources)
                        RefKind.CHORD -> InYours.chord(Chords.byId(id), sources, model.chordDialect)
                        RefKind.PROGRESSION, RefKind.TUNING -> emptyList()
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (prev != null) Sq("‹ ${prev.title.uppercase()}", { open(prev) }, Modifier.weight(1f))
                if (next != null) Sq("${next.title.uppercase()} ›", { open(next) }, Modifier.weight(1f))
            }
            Gap(24.dp)
        }
    }
}

@Composable
private fun Section(label: String) = Text(label, style = T.label.copy(color = C.acc), modifier = Modifier.padding(start = 14.dp, top = 18.dp, bottom = 6.dp))

@Composable
private fun See(model: AppModel, page: CoursePage, see: CourseSee, root: PitchName) = when (see) {
    is CourseSee.Table -> {
        Section("SEE · ${see.caption}")
        val plays = see.play.filterNotNull()
        if (plays.isNotEmpty()) Row(Modifier.padding(start = 14.dp, bottom = 6.dp)) {
            Sq("▶ PLAY THE TABLE", { hearPlay(model, page, see.caption, Play(plays.flatMap { it.chords }, plays.first().step)) })
        }
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = 14.dp)) {
                val widths = see.header.indices.map { c -> (listOf(see.header[c]) + see.rows.map { it.getOrElse(c) { "" } }).maxOf { it.length }.coerceIn(3, 22) * 8 + 10 }
                Row { see.header.forEachIndexed { c, h -> Text(h, style = T.label, modifier = Modifier.width(widths[c].dp)) } }
                see.rows.forEachIndexed { i, r ->
                    val p = see.play.getOrNull(i)
                    Row(Modifier.padding(top = 4.dp).let { if (p != null) it.clickable(role = Role.Button) { hearPlay(model, page, r.first(), p) } else it }, verticalAlignment = Alignment.CenterVertically) {
                        r.forEachIndexed { c, v -> Text(v, style = if (c == 0) T.body.copy(color = C.hi, fontWeight = FontWeight.Bold) else T.small, modifier = Modifier.width(widths[c].dp)) }
                        if (p != null) Text("▶", style = T.small.copy(color = C.acc), modifier = Modifier.width(30.dp))
                    }
                }
            }
        }
        if (plays.isNotEmpty()) Text("TAP A ROW TO HEAR IT", style = T.label, modifier = Modifier.padding(start = 14.dp, top = 6.dp)) else Unit
    }
    is CourseSee.Keys -> {
        Section(see.caption)
        CourseKeys(see.keys)
        see.play?.let { p -> Row(Modifier.padding(start = 14.dp)) { Sq("▶ PLAY WHAT'S LIT", { hearPlay(model, page, see.caption, p) }) } }
    }
    is CourseSee.Grid -> {
        Section(see.caption)
        CourseGrid(model, page, see)
    }
    is CourseSee.Chart -> {
        Section(see.caption)
        CourseChart(model, page, see)
    }
    is CourseSee.Staff -> {
        Section(see.caption)
        see.score.parts.forEach { part -> NotationCanvas(see.score, part, null, null, emptySet(), 0, model.chordDialect, model.chordStyle) { _, _, _, _, _ -> } }
        see.play?.let { p -> Row(Modifier.padding(start = 14.dp)) { Sq("▶ PLAY THESE BARS", { hearPlay(model, page, see.caption, p) }) } }
    }
    is CourseSee.KeyStaff -> {
        val key = KeyMode(root, see.scaleId)
        val notes = remember(key) { ScalePages.page(Scales.byId(see.scaleId), root).notes }
        val midis = RefSound.midis(root.pitchClass, Scales.byId(see.scaleId).semitones, 60)
        val f = Notation.fifths(key)
        Section("SEE · ${key.display.uppercase()} · " + when { f == 0 -> "NO SHARPS OR FLATS"; f > 0 -> "$f SHARP${if (f > 1) "S" else ""}"; else -> "${-f} FLAT${if (f < -1) "S" else ""}" })
        StaffView(model, notes, midis.map { listOf(it) } + listOf(listOf(midis.first() + 12)), key)
    }
    is CourseSee.Rhythm -> {
        Section("SEE · ${see.caption}")
        see.bars.forEach { (label, meter, notes) ->
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Sq("▶ $label", {
                    val start = com.betterappmafia.apophenia.core.learn.EarRhythm.barSeconds(meter, see.bpm)
                    hearCourse(model, page, label, EarAudio(emptyList(), drums = com.betterappmafia.apophenia.core.learn.EarRhythm.countIn(meter, see.bpm) +
                        com.betterappmafia.apophenia.core.learn.EarRhythm.drums(notes, meter, see.bpm, start)))
                })
            }
            val events = notes.map { n -> com.betterappmafia.apophenia.core.score.Event(n.d, if (n.rest) emptyList() else listOf(com.betterappmafia.apophenia.core.score.Note(com.betterappmafia.apophenia.core.pitch.SpelledPitch.parse("C5")))) }
            val part = com.betterappmafia.apophenia.core.score.Part("", com.betterappmafia.apophenia.core.score.Clef.PERCUSSION, bars = listOf(com.betterappmafia.apophenia.core.score.Bar(listOf(events))))
            Box(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
                NotationCanvas(com.betterappmafia.apophenia.core.score.Score(meter = meter, parts = listOf(part)), part, null, null, emptySet(), 0, model.chordDialect, model.chordStyle) { _, _, _, _, _ -> }
            }
        }
    }
    is CourseSee.Score -> {
        Section("SEE · ${see.caption}")
        see.score.parts.forEachIndexed { i, part ->
            see.labels.getOrNull(i)?.takeIf { it.isNotEmpty() }?.let { Text(it, style = T.label, modifier = Modifier.padding(start = 14.dp, top = 6.dp)) }
            NotationCanvas(see.score, part, null, null, emptySet(), 0, model.chordDialect, model.chordStyle) { _, _, _, _, _ -> }
            if (part.fretted) TabPreview(see.score, part)
            if (page.tryIt == null && part.clef != com.betterappmafia.apophenia.core.score.Clef.PERCUSSION) {
                val pitches = part.bars.flatMap { it.voices.flatten() }.flatMap { it.notes }.map { it.pitch }
                if (pitches.isNotEmpty()) {
                    Text("NOTES USED IN THIS EXAMPLE", style = T.label, modifier = Modifier.padding(12.dp))
                    val names = pitches.map { it.name }.distinctBy { it.pitchClass }
                    ShowOn(model, pitches.map { it.midi }.distinct().sorted(), names, names.map { refName(model, it) }, null, pitches.map { listOf(it.midi) })
                }
            }
        }
    }
    CourseSee.Circle -> {
        Section("SEE · TAP A KEY TO HEAR ITS SCALE")
        CircleOfFifths(model, page, root)
    }
}

/** The circle of fifths: majors on the outer ring, relative minors inside; the page's key lit. A tap plays that key's scale and moves TRY to it. */
@Composable
private fun CircleOfFifths(model: AppModel, page: CoursePage, root: PitchName) {
    val majors = Course.circleMajor.map { PitchName.parse(it) }
    val minors = Course.circleMinor.map { PitchName.parse(it) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.padding(8.dp).size(300.dp).pointerInput(Unit) {
                detectTapGestures { o ->
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val d = o - c
                    val r = d.getDistance() / (size.width / 2f)
                    if (r < 0.2f || r > 1f) return@detectTapGestures
                    val i = Math.floorMod(Math.round(Math.toDegrees(atan2(d.x.toDouble(), -d.y.toDouble())) / 30.0).toInt(), 12)
                    val minor = r < 0.6f
                    val tonic = if (minor) minors[i] else majors[i]
                    val id = if (minor) "aeolian" else "major"
                    model.courseRoot = majors[i]
                    hearCourse(model, page, "${refName(model, tonic)} ${if (minor) "minor" else "major"}",
                        EarAudio(RefSound.scale(RefSound.midis(tonic.pitchClass, Scales.byId(id).semitones, 60))))
                }
            },
        ) {
            val c = Offset(size.width / 2, size.height / 2)
            val big = size.width / 2
            drawCircle(C.line2, big * 0.98f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
            drawCircle(C.line2, big * 0.6f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
            drawCircle(C.line2, big * 0.2f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
            val paint = android.graphics.Paint().apply { isAntiAlias = true; typeface = android.graphics.Typeface.MONOSPACE; textAlign = android.graphics.Paint.Align.CENTER }
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30.0)
                fun at(f: Float) = Offset(c.x + (big * f * Math.sin(a)).toFloat(), c.y - (big * f * Math.cos(a)).toFloat())
                val lit = majors[i].pitchClass == root.pitchClass
                if (lit) drawCircle(C.acc, 22.dp.toPx(), at(0.8f))
                paint.textSize = 15.dp.toPx(); paint.isFakeBoldText = true
                paint.color = (if (lit) C.bg else C.hi).toArgb()
                val o = at(0.8f)
                drawContext.canvas.nativeCanvas.drawText(refName(model, majors[i]), o.x, o.y + 5.dp.toPx(), paint)
                paint.textSize = 11.dp.toPx(); paint.isFakeBoldText = false; paint.color = C.dim.toArgb()
                val m = at(0.42f)
                drawContext.canvas.nativeCanvas.drawText(refName(model, minors[i]).lowercase() + "m", m.x, m.y + 4.dp.toPx(), paint)
                val sig = Notation.fifths(KeyMode(majors[i], "major"))
                paint.textSize = 9.dp.toPx(); paint.color = C.faint.toArgb()
                val s = at(0.64f)
                if (i != 0) drawContext.canvas.nativeCanvas.drawText(if (sig > 0) "${sig}♯" else "${-sig}♭", s.x, s.y + 3.dp.toPx(), paint)
            }
        }
    }
}

/** Check by ear: ▶ plays one of the answers, chosen at random from a random start note; tap which it was. After an answer each one plays on tap, to compare. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Check(model: AppModel, page: CoursePage, check: CourseCheck) {
    var seed by remember(page.id) { mutableIntStateOf(Random.nextInt()) }
    var picked by remember(page.id) { mutableStateOf<Int?>(null) }
    var right by remember(page.id) { mutableIntStateOf(0) }
    var asked by remember(page.id) { mutableIntStateOf(0) }
    val rnd = Random(seed)
    val answer = rnd.nextInt(check.options.size)
    val root = check.roots.first + rnd.nextInt(check.roots.last - check.roots.first + 1)
    fun audio(i: Int): EarAudio = check.options[i].make(root, Random(seed + i))
    Section("CHECK BY EAR")
    Text(check.question, style = T.body.copy(color = C.hi), modifier = Modifier.padding(horizontal = 14.dp))
    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Sq("▶ PLAY", { hearCourse(model, page, "Check", audio(answer)) })
        Sq("NEW ›", { seed = Random.nextInt(); picked = null })
        if (asked > 0) Text("$right / $asked RIGHT", style = T.label)
    }
    FlowRow(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        check.options.forEachIndexed { i, o ->
            val p = picked
            Sq(if (p == null) o.label else "▶ ${o.label}", {
                if (p == null) { picked = i; asked++; if (i == answer) right++ }
                else hearCourse(model, page, o.label, audio(i))
            }, on = p != null && i == answer, color = when { p == null -> C.text; i == answer -> C.acc; i == p -> C.rec; else -> C.text })
        }
    }
    picked?.let { p ->
        Text((if (p == answer) "Right. " else "It was ${check.options[answer].label}. ") + check.options[answer].explain, style = T.small.copy(lineHeight = 16.sp), modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

private fun hearPlay(model: AppModel, page: CoursePage, title: String, p: Play) = hearCourse(model, page, title, p.audio())

/** Piano keys around [keys]: lit ones filled copper and named, faint ones (context) outlined in copper. */
@Composable
private fun CourseKeys(keys: List<com.betterappmafia.apophenia.core.learn.Key>) {
    if (keys.isEmpty()) return
    val ms = keys.map { it.midi }
    val lo = (ms.min() / 12) * 12
    val hi = maxOf(((ms.max() / 12) + 1) * 12, lo + 12)
    val whites = (lo..hi).filter { it % 12 in setOf(0, 2, 4, 5, 7, 9, 11) }
    val byMidi = keys.associateBy { it.midi }
    val keyW = 28.dp
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Canvas(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).width(keyW * whites.size).height(128.dp)) {
            val w = keyW.toPx()
            val h = size.height
            val paint = android.graphics.Paint().apply { isAntiAlias = true; textSize = 10.dp.toPx(); typeface = android.graphics.Typeface.MONOSPACE; textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true }
            fun label(m: Int, x: Float, y: Float) {
                val k = byMidi[m] ?: return
                paint.color = (if (k.faint) C.acc else C.bg).toArgb()
                drawContext.canvas.nativeCanvas.drawText(k.label, x, y, paint)
            }
            fun fill(m: Int, base: androidx.compose.ui.graphics.Color) = byMidi[m]?.let { if (it.faint) base else C.acc } ?: base
            whites.forEachIndexed { i, m ->
                drawRect(fill(m, C.text.copy(alpha = 0.6f)), Offset(i * w + 1, 0f), androidx.compose.ui.geometry.Size(w - 2, h))
                if (byMidi[m]?.faint == true) drawRect(C.acc, Offset(i * w + 3, h - 26.dp.toPx()), androidx.compose.ui.geometry.Size(w - 6, 22.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                label(m, i * w + w / 2, h - 10.dp.toPx())
            }
            whites.forEachIndexed { i, m ->
                val b = m + 1
                if (m % 12 in setOf(0, 2, 5, 7, 9) && b <= hi) {
                    val x = (i + 1) * w - w * 0.32f
                    drawRect(fill(b, C.bg), Offset(x, 0f), androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.6f))
                    if (byMidi[b]?.faint == true) drawRect(C.acc, Offset(x + 2, h * 0.6f - 22.dp.toPx()), androidx.compose.ui.geometry.Size(w * 0.64f - 4, 20.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                    label(b, x + w * 0.32f, h * 0.6f - 8.dp.toPx())
                }
            }
        }
    }
}

/** Rows against columns; lit cells filled copper. A row with its own sound plays on tap. */
@Composable
private fun CourseGrid(model: AppModel, page: CoursePage, g: CourseSee.Grid) {
    var on by remember(g) { mutableStateOf<Int?>(null) }
    g.play?.let { p -> Row(Modifier.padding(start = 14.dp, bottom = 6.dp)) { Sq("▶ PLAY EVERY ROW", { on = null; hearPlay(model, page, g.caption, p) }) } }
    val labelW = (g.rows.maxOfOrNull { it.label.length } ?: 2).coerceIn(2, 14) * 9 + 12
    val cellW = maxOf(g.columns.maxOfOrNull { it.length } ?: 1, g.rows.flatMap { it.cells }.maxOfOrNull { it.length } ?: 1).coerceIn(2, 8) * 9 + 12
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column(Modifier.padding(horizontal = 14.dp)) {
            Row {
                Text("", modifier = Modifier.width(labelW.dp))
                g.columns.forEach { Text(it, style = T.label, modifier = Modifier.width(cellW.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
            g.rows.forEachIndexed { i, r ->
                Row(Modifier.padding(top = 3.dp).let { m -> r.play?.let { p -> m.clickable(role = Role.Button) { on = i; hearPlay(model, page, r.label, p) } } ?: m }, verticalAlignment = Alignment.CenterVertically) {
                    Text(r.label, style = T.body.copy(color = if (on == i) C.acc else C.hi, fontWeight = FontWeight.Bold), modifier = Modifier.width(labelW.dp))
                    r.cells.forEachIndexed { c, v ->
                        val lit = c in r.lit
                        Box(Modifier.width(cellW.dp).padding(1.dp).heightIn(min = 26.dp).background(if (lit) C.acc else C.panel2), contentAlignment = Alignment.Center) {
                            Text(v, style = T.small.copy(color = if (lit) C.bg else C.faint, fontWeight = if (lit) FontWeight.Bold else FontWeight.Normal))
                        }
                    }
                }
            }
        }
    }
    if (g.rows.any { it.play != null }) Text("TAP A ROW TO HEAR IT", style = T.label, modifier = Modifier.padding(start = 14.dp, top = 6.dp))
}

/** A progression as boxes in order: numeral, symbol, notes and job. Tap a box to hear that chord; ▶ plays them all. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseChart(model: AppModel, page: CoursePage, c: CourseSee.Chart) {
    var on by remember(c) { mutableStateOf<Int?>(null) }
    Row(Modifier.padding(start = 14.dp, bottom = 6.dp)) { Sq("▶ PLAY IN ORDER", { on = null; hearPlay(model, page, c.caption, c.play) }) }
    FlowRow(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        c.chords.forEachIndexed { i, ch ->
            Column(
                Modifier.width(92.dp).background(if (on == i) C.selected else C.panel).border(1.dp, if (on == i) C.acc else C.line2)
                    .clickable(role = Role.Button) { on = i; hearPlay(model, page, ch.symbol.ifEmpty { ch.numeral }, Play(listOf(ch.midis), 1.6, spread = true)) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(ch.numeral, style = T.hi.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                if (ch.symbol.isNotEmpty()) Text(ch.symbol, style = T.body.copy(color = C.acc, fontWeight = FontWeight.Bold), maxLines = 1)
                Text(ch.notes, style = T.small, maxLines = 2)
                if (ch.tag.isNotEmpty()) Text(ch.tag.uppercase(), style = T.label, maxLines = 2)
            }
        }
    }
    Text("TAP A CHORD TO HEAR IT ON ITS OWN", style = T.label, modifier = Modifier.padding(start = 14.dp, top = 6.dp))
}

private fun hearCourse(model: AppModel, page: CoursePage, title: String, audio: EarAudio) =
    hearEar(model, "course-${page.id}-${audio.hashCode()}", "${page.title} · $title", audio)
