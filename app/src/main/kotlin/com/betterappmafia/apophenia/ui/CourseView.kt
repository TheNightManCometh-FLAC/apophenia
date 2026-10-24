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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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

/** Learn · Course: the nine categories. */
@Composable
internal fun CourseHome(model: AppModel) {
    Course.categories.forEach { c ->
        val pages = c.topics.sumOf { it.pages.size }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 66.dp).underline().clickable(role = Role.Button) { model.courseCat = c.id }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("${c.topics.size} TOPICS" + (if (pages > 0) " · $pages PAGES" else " · TO BE WRITTEN"), style = T.label)
                Text(c.name.uppercase(), style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
                Text(c.range, style = T.label.copy(color = C.acc))
            }
            Text("›", style = T.hi.copy(color = C.acc))
        }
    }
    Kv("LEVELS") { Text("● to ●●●●● is only a guide to how complex a page is. Everything is open.", style = T.small.copy(lineHeight = 16.sp)) }
}

/** A category's topics, filtered by LEVEL ▾. A topic opens its first page. */
@Composable
internal fun CourseTopics(model: AppModel, cat: CourseCategory) {
    var menu by remember { mutableStateOf(false) }
    val levels = listOf(0 to "All", 1 to "● only", 2 to "●● only", 3 to "●●● only", 4 to "●●●● and up")
    Column(Modifier.fillMaxSize()) {
        TitleBar {
            Sq("‹", { model.courseCat = null })
            Text(cat.name.uppercase(), style = T.title, modifier = Modifier.weight(1f), maxLines = 1)
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
                    Modifier.fillMaxWidth().heightIn(min = 62.dp).underline()
                        .clickable(enabled = written, role = Role.Button) { model.courseRoot = null; model.coursePage = t.pages.first().id }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t.name, style = T.body.copy(color = if (written) C.hi else C.dim, fontWeight = FontWeight.Bold))
                        Line1(if (written) "${t.pages.size} PAGES · ${t.note.uppercase()}" else "TO BE WRITTEN · ${t.note.uppercase()}", T.label)
                    }
                    Text(Course.gauge(t.level), style = T.label.copy(color = if (written) C.acc else C.faint))
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
        is CourseTarget.Ear -> { model.earDrill = t.drill; model.learnPart = "EAR" }
        is CourseTarget.Page -> Course.page(t.id)?.let { open(it) }
    }
    Column(Modifier.fillMaxSize()) {
        TitleBar {
            Sq("‹", { model.coursePage = null; model.courseCat = cat.id })
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
            page.see?.let { See(model, page, it, root) }
            if (page.hear.isNotEmpty()) {
                Section("HEAR")
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
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = 14.dp)) {
                val widths = see.header.indices.map { c -> (listOf(see.header[c]) + see.rows.map { it.getOrElse(c) { "" } }).maxOf { it.length }.coerceIn(3, 22) * 8 + 10 }
                Row { see.header.forEachIndexed { c, h -> Text(h, style = T.label, modifier = Modifier.width(widths[c].dp)) } }
                see.rows.forEach { r ->
                    Row(Modifier.padding(top = 4.dp)) {
                        r.forEachIndexed { c, v -> Text(v, style = if (c == 0) T.body.copy(color = C.hi, fontWeight = FontWeight.Bold) else T.small, modifier = Modifier.width(widths[c].dp)) }
                    }
                }
            }
        }
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

private fun hearCourse(model: AppModel, page: CoursePage, title: String, audio: EarAudio) =
    hearEar(model, "course-${page.id}-${audio.hashCode()}", "${page.title} · $title", audio)
