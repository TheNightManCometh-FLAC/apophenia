package com.betterappmafia.apophenia.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.core.learn.ChordSound
import com.betterappmafia.apophenia.core.learn.Ear
import com.betterappmafia.apophenia.core.learn.EarDirection
import com.betterappmafia.apophenia.core.learn.EarDrill
import com.betterappmafia.apophenia.core.learn.EarKind
import com.betterappmafia.apophenia.core.learn.EarQuestion
import com.betterappmafia.apophenia.core.learn.EarMode
import com.betterappmafia.apophenia.core.learn.given
import com.betterappmafia.apophenia.data.EarResultEntity
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import kotlinx.coroutines.launch
import kotlin.random.Random

/*
 * Learn › Ear (plan §9; updated by Christopher 29 Oct 2026): focused practice, configurable tests and high scores.
 * EarHome exposes the presentation before the interval drill; records keep test percentages and practice streaks separate.
 * Drill screen: ▶ PLAY, SOUND ▾, ANSWER ▾ (by name, on the piano, on your
 * guitar), and the answer explained with the right one and yours to hear.
 */

/**
 * PICK YOUR OWN under a chapter's drills (Christopher, 25 Oct 2026: "There's no way to practice specific intervals and
 * so on"): every interval, chord, degree, mode… of the chapter as a chip, grouped by the drill it comes from; tap the
 * ones you want and practise just those. The picks are kept per chapter while the app runs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PickYourOwn(model: AppModel, chapter: String, direction: EarDirection? = null) {
    val groups = remember(chapter, direction) { Ear.pickable(chapter, direction) }
    val key = if (direction == null) chapter else "$chapter:${direction.name}"
    val picked = model.earPicks[key].orEmpty()
    fun set(p: Set<String>) { model.earPicks = model.earPicks + (key to p) }
    val test = model.earSection == "TEST"
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("PICK YOUR OWN", style = T.label.copy(color = C.acc), modifier = Modifier.weight(1f))
        if (picked.isNotEmpty()) Sq("CLEAR", { set(emptySet()) })
    }
    groups.forEach { (d, answers) ->
        Text(d.name.uppercase(), style = T.label, modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 6.dp))
        FlowRow(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            answers.forEach { a ->
                val on = a in picked
                Text(
                    Ear.label(d, a.substringAfter('|')),
                    style = T.button.copy(color = if (on) C.bg else C.hi),
                    modifier = Modifier.background(if (on) C.acc else C.panel).border(1.dp, if (on) C.acc else C.line2)
                        .clickable(role = Role.Checkbox) { set(if (on) picked - a else picked + a) }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                )
            }
        }
    }
    Row(Modifier.padding(top = 14.dp)) {
        BigButton(
            if (picked.size < 2) "PICK TWO OR MORE" else "${if (test) "TEST" else "PRACTISE"} THESE ${picked.size}",
            { model.startEar(Ear.pick(groups.flatMap { it.second }.filter { it in picked }), if (test) EarMode.TEST else EarMode.PRACTICE) },
            primary = true, enabled = picked.size >= 2 && (!test || model.earTestQuestionsText.toIntOrNull()?.let { it > 0 } == true),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EarDrillView(model: AppModel, drill: EarDrill, requestMic: (() -> Unit) -> Unit) {
    val run = model.earRun?.takeIf { it.drill.id == drill.id } ?: EarRun(drill, model.earMode,
        model.earTestQuestionsText.toIntOrNull()?.takeIf { it > 0 } ?: Ear.TEST_LENGTH, model.earSettings, model.learnGuitar.stringCount).also { model.earRun = it }
    val mode = run.mode.name
    val streaks by model.app.db.earResults().streaks().collectAsState(emptyList())
    val stored = streaks.firstOrNull { it.drill == drill.id }?.best ?: 0
    val settings = run.settings
    val session = run.progress
    val missed = run.missed
    var question by run::question
    var picked by run::picked
    var tapped by run::tapped
    var frets by run::frets
    var counts by run::counts
    var soundMenu by remember { mutableStateOf(false) }
    var answerMenu by remember { mutableStateOf(false) }
    val kind = Ear.source(drill, question.answer).first.kind

    fun save() = run.save(model)
    DisposableEffect(run) { onDispose { run.save(model) } }

    fun play(q: EarQuestion = question) = hearEar(model, "ear-${Ear.source(drill, q.answer).first.kind}-${q.midis.joinToString("_")}-${q.direction}-${settings.chord}-${settings.context}-${q.cents}-${q.tonic}-${q.bpm}-${q.chords.flatten().joinToString("_")}-${q.rhythm.hashCode()}-${q.meter}-${q.answer}-${q.clip?.let { "${it.path.hashCode()}_${(it.start * 1000).toInt()}_${(it.length * 1000).toInt()}" }}", drill.name, Ear.sound(q, settings))
    LaunchedEffect(run, run.questionNumber) { if (picked == null && Ear.source(drill, question.answer).first.kind != EarKind.TAPBACK) play() }

    fun answer(a: String, right: Boolean) {
        if (picked != null || session.complete) return
        picked = a
        session.answer(right)
        // Every first answer counts for its item (Ear › Weakest and Quick check).
        val item = com.betterappmafia.apophenia.core.learn.EarMastery.item(drill, question.answer)
        model.app.io.launch { model.app.db.earAnswers().add(com.betterappmafia.apophenia.data.EarAnswerEntity(item = item, correct = right, at = System.currentTimeMillis())) }
        if (right) { missed.remove(question.answer)?.let { if (it > 1) missed[question.answer] = it - 1 } }
        else { missed[question.answer] = (missed[question.answer] ?: 0) + 1 }
        counts = Triple(session.right, session.asked, session.streak)
        save()
    }
    fun next() = run.next(model.learnGuitar.stringCount)

    BackHandler { save(); model.earDrill = null }
    Column(Modifier.fillMaxSize()) {
        TitleBar {
            Sq("‹", { save(); model.earDrill = null })
            Text(drill.name, style = T.title, modifier = Modifier.weight(1f), maxLines = 1)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (mode == "TEST") "TEST · ${minOf(counts.second + (if (picked == null) 1 else 0), session.target)} OF ${session.target}" else "PRACTICE", style = T.label, modifier = Modifier.weight(1f))
            EarLessonLink(model, drill, question.answer, question.root)
        }
        // What the drill is: open until the first answer, then folded to one tap (Christopher, 31 Oct 2026).
        var about by remember(drill.id) { mutableStateOf(counts.second == 0) }
        val text = remember(drill.id) { com.betterappmafia.apophenia.core.learn.EarAbout.of(drill) }
        Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { about = !about }.padding(horizontal = 14.dp, vertical = 6.dp)) {
            Text(if (about) "WHAT THIS DRILL IS ▴" else "WHAT THIS DRILL IS ▾", style = T.label.copy(color = C.acc))
            if (about) {
                Text(text.what, style = T.body.copy(lineHeight = 19.sp), modifier = Modifier.padding(top = 4.dp))
                Text("HOW TO LISTEN", style = T.label, modifier = Modifier.padding(top = 8.dp))
                Text(text.listen, style = T.body.copy(lineHeight = 19.sp, color = C.dim), modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (counts.second > 0 && session.complete) {
            val scores by model.app.db.earResults().bests().collectAsState(emptyList())
            val best = scores.firstOrNull { it.drill == drill.id }
            Kv(if (run.record) "TEST COMPLETE" else "CHECK COMPLETE") {
                Text(earPercent(counts.first, counts.second), style = T.hi.copy(color = C.acc, fontSize = 38.sp))
                Text("${counts.first} right out of ${counts.second} questions", style = T.body)
                best?.bestTotal?.let { Text("BEST ${earPercent(best.bestCorrect ?: 0, it)} · ${best.bestCorrect}/$it", style = T.label) }
            }
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (run.record) Sq("TEST AGAIN", { model.startEar(drill, EarMode.TEST) }, on = true)
                Sq("BACK TO DRILLS", { model.earDrill = null; model.earRun = null })
            }
            return@Column
        }
        // The streak, large: right answers in a row this session, against the drill's high score.
        val streak = counts.third
        val best = maxOf(stored, session.best)
        val newBest = session.best > stored && session.best > 0
        Row(Modifier.fillMaxWidth().underline().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (mode == "TEST") "SCORE" else "STREAK", style = T.label)
                Text(if (mode == "TEST") earPercent(counts.first, counts.second) else "$streak", style = T.hi.copy(fontSize = 44.sp, color = if (streak > 0) C.acc else C.faint))
            }
            if (mode == "PRACTICE") Column(horizontalAlignment = Alignment.End) {
                Text(if (newBest) "NEW BEST" else "BEST", style = T.label.copy(color = if (newBest) C.acc else C.dim))
                Text("$best", style = T.hi.copy(fontSize = 26.sp))
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Sq(if (mode == "LISTEN") "▶ NEW ROOT" else "▶ PLAY", { if (mode == "LISTEN") next() else play() }, on = true)
                Box {
                    Sq("SOUND ▾", { soundMenu = true }, enabled = mode == "PRACTICE")
                    ConsoleMenu(soundMenu, { soundMenu = false }, "SOUND",
                        listOf(MenuItem("INTERVALS", "", enabled = false) {}) +
                            EarDirection.entries.map { d -> MenuItem(d.label, if ((drill.direction ?: settings.direction) == d) "✓" else "", enabled = drill.direction == null) { run.settings = settings.copy(direction = d); model.earSettings = run.settings } } +
                            MenuItem("CHORDS", "", enabled = false) {} +
                            ChordSound.entries.map { s -> MenuItem(s.label, if (settings.chord == s) "✓" else "") { run.settings = settings.copy(chord = s); model.earSettings = run.settings } } +
                            MenuItem("SCALE DEGREES", "", enabled = false) {} +
                            com.betterappmafia.apophenia.core.learn.DegreeContext.entries.map { c -> MenuItem(c.label, if (settings.context == c) "✓" else "") { run.settings = settings.copy(context = c); model.earSettings = run.settings } } +
                            MenuItem("Random key", if (settings.randomKey) "✓ ON" else "OFF · ON C") { run.settings = settings.copy(randomKey = !settings.randomKey); model.earSettings = run.settings })
                }
                if (mode != "LISTEN") Box {
                    Sq("ANSWER ▾", { answerMenu = true })
                    ConsoleMenu(answerMenu, { answerMenu = false }, "ANSWER", listOf("NAME" to "By name", "PIANO" to "On the piano", "GUITAR" to "On your guitar · ${model.learnGuitar.name}").map { (k, t) ->
                        MenuItem(t, if (model.earAnswerOn == k) "✓" else "") { model.earAnswerOn = k }
                    })
                }
            }
            key(run, run.questionNumber) {
                val sungDrill = Ear.source(drill, question.answer).first.sung
                val instrumentOk = kind in setOf(EarKind.INTERVAL, EarKind.CHORD, EarKind.NOTE, EarKind.OCTAVE, EarKind.DEGREE)
                val byName = mode == "LISTEN" || model.earAnswerOn == "NAME" || !instrumentOk
                if (kind == EarKind.TAPBACK && mode != "LISTEN") {
                    TapBack(model, question, picked == null, { play() }) { ok -> answer(if (ok) question.answer else "", ok) }
                } else if (kind == EarKind.RHYTHM) {
                    RhythmOptions(model, question, picked, mode) { a -> if (mode == "LISTEN") play(Ear.asAnswer(question, a)) else answer(a, a == question.answer) }
                } else if ((kind == EarKind.MELODY || kind == EarKind.BASS) && mode != "LISTEN") {
                    SequenceAnswer(model, question, picked == null) { ok -> answer(if (ok) question.answer else "", ok) }
                } else if (sungDrill && mode != "LISTEN") {
                    SungAnswer(model, question, picked == null, requestMic) { ok, cents -> answer(if (ok) question.answer else "sung:$cents", ok) }
                } else if (byName) {
                    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val src = Ear.source(drill, question.answer).first
                        val options = if (drill.id.startsWith("mixed")) src.answers.map { "${src.id}|$it" } else drill.answers
                        options.forEach { a ->
                            val isRight = a == question.answer
                            val colour = when {
                                picked == null || mode == "LISTEN" -> C.line2
                                isRight -> C.acc
                                a == picked -> C.rec
                                else -> C.line
                            }
                            Box(
                                Modifier.widthIn(min = 96.dp).heightIn(min = 54.dp).border(1.5.dp, colour)
                                    .clickable(role = Role.Button) {
                                        if (mode == "LISTEN") play(Ear.asAnswer(question, a)) else answer(a, isRight)
                                    }.padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(Ear.label(drill, a) { refName(model, it) }, style = T.button.copy(fontSize = 13.sp, color = if (colour == C.line) C.faint else C.hi), textAlign = TextAlign.Center) }
                        }
                    }
                } else {
                    val given: Int? = when (kind) { EarKind.INTERVAL, EarKind.CHORD -> question.given(); EarKind.DEGREE -> question.tonic; else -> null }
                    val single = kind != EarKind.CHORD
                    Kv(if (given != null) "GIVEN" else "PLAY IT") {
                        Text(
                            (given?.let { "${com.betterappmafia.apophenia.core.pitch.PitchClass(it).defaultSpelling().let { n -> refName(model, n) }}${it / 12 - 1} " } ?: "") +
                                when (kind) {
                                    EarKind.INTERVAL -> "is the first note: play the other"
                                    EarKind.DEGREE -> "is the tonic: play the degree you heard"
                                    EarKind.OCTAVE -> "Play the note you heard in its octave"
                                    EarKind.NOTE -> "Play the note you heard, any octave"
                                    else -> "is the bass: play the rest of the chord, any octave"
                                },
                            style = T.small,
                        )
                    }
                    val lit = given?.let { tapped + it } ?: tapped
                    if (model.earAnswerOn == "PIANO") Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        TapPiano(if (kind == EarKind.OCTAVE) 36 else minOf(43, question.midis.minOrNull()?.minus(3) ?: 43), if (kind == EarKind.OCTAVE) 96 else maxOf(84, question.midis.maxOrNull()?.plus(3) ?: 84), lit) { m -> if (picked == null && (kind != EarKind.CHORD || m != given)) tapped = if (single) setOf(m) else if (m in tapped) tapped - m else tapped + m }
                    } else Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        TapNeck(model.learnGuitar.strings.map { it.midi }, frets) { s, f -> if (picked == null) frets = frets.toMutableList().also { it[s] = if (it[s] == f) null else f } }
                    }
                    val played = if (model.earAnswerOn == "PIANO") tapped else frets.mapIndexedNotNull { s, f -> f?.let { model.learnGuitar.strings[s].midi + it } }.filter { kind != EarKind.CHORD || it != given }.toSet()
                    val heard = (given?.let { played + it } ?: played).sorted()
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Sq("▶ MINE", { if (played.isNotEmpty()) hearNotes(model, "mine-${heard.joinToString("_")}", "Yours", com.betterappmafia.apophenia.core.learn.RefSound.block(heard)) })
                        Sq("CHECK", {
                            val ok = Ear.check(question, if (kind == EarKind.CHORD) heard else played)
                            answer(if (ok) question.answer else "", ok)
                        }, enabled = picked == null && played.isNotEmpty())
                    }
                }
                val p = picked
                if (p != null && mode != "LISTEN") {
                    val right = p == question.answer
                    Kv(if (right) "RIGHT" else "ANSWER") { Text(Ear.explain(question) { refName(model, it) }, style = T.body.copy(lineHeight = 18.sp, color = if (right) C.hi else C.text)) }
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Sq("▶ RIGHT ONE", { play() })
                        if (!right && p.isNotEmpty() && !p.startsWith("sung:") && mode == "PRACTICE") Sq("▶ YOURS: ${Ear.label(drill, p) { refName(model, it) }}", { play(Ear.asAnswer(question, p)) })
                        if (p.startsWith("sung:")) Text("YOU WERE ${p.removePrefix("sung:")} ¢ OFF", style = T.label.copy(color = C.rec), modifier = Modifier.align(Alignment.CenterVertically))
                        Box(Modifier.weight(1f))
                        Sq("NEXT ›", { next() }, on = true)
                    }
                }
            }
            Kv("SESSION") { Text("${counts.first} right of ${counts.second}", style = T.small) }
        }
    }
}

/**
 * Sing or play it: ● LISTEN opens the mic through the tuner (it stays Off until you tap), shows the note it hears and
 * how far it is from the target, and grades the median of the first 0.6 s of steady pitch; it gives up after 6 s.
 */
@Composable
private fun SungAnswer(model: AppModel, q: EarQuestion, open: Boolean, requestMic: (() -> Unit) -> Unit, graded: (Boolean, Int) -> Unit) {
    val tm = model.tools
    var listening by remember(q) { mutableStateOf(false) }
    var status by remember(q) { mutableStateOf("") }
    val src = Ear.source(q.drill, q.answer)
    val target = Ear.target(q)
    Kv("SING IT") {
        Text(
            if (src.first.kind == EarKind.SING) "Sing a ${src.second} (${com.betterappmafia.apophenia.core.learn.Reference.intervalName(com.betterappmafia.apophenia.core.interval.Interval.parse(src.second))}) above the note you heard. Any octave counts."
            else "Sing or play the note you heard. Any octave counts.",
            style = T.body.copy(lineHeight = 18.sp),
        )
    }
    DisposableEffect(q) { onDispose { if (listening) tm.stopTuner() } }
    LaunchedEffect(listening) {
        if (!listening) return@LaunchedEffect
        val heard = ArrayList<Double>()
        val start = System.currentTimeMillis()
        while (listening && System.currentTimeMillis() - start < 6000) {
            kotlinx.coroutines.delay(50)
            val r = tm.reading ?: continue
            heard += r.hz
            val (_, cents) = Ear.grade(q, r.hz, tm.tuner.a4)
            status = "${refName(model, r.pitch.name)}${r.pitch.octave} · ${if (cents >= 0) "+" else ""}$cents ¢ from the target"
            if (heard.size >= 12) break
        }
        tm.stopTuner()
        listening = false
        if (heard.size < 12) { status = "Didn't hear a steady note. Try again."; return@LaunchedEffect }
        val hz = heard.sorted()[heard.size / 2]
        val (ok, cents) = Ear.grade(q, hz, tm.tuner.a4)
        status = if (ok) "Right: ${if (cents >= 0) "+" else ""}$cents ¢" else "$cents ¢ off"
        graded(ok, cents)
    }
    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Sq(if (listening) "■ STOP" else "● LISTEN", {
            if (listening) { listening = false } else if (open) requestMic { tm.startTuner(); listening = true }
        }, on = listening, enabled = open || listening)
        Sq("▶ TARGET", { hearNotes(model, "target-$target", "Target", listOf(com.betterappmafia.apophenia.core.sound.PlayNote(0.0, 1.4, target, com.betterappmafia.apophenia.core.learn.RefSound.VELOCITY))) }, enabled = !open)
        Text(status, style = T.small, modifier = Modifier.weight(1f))
    }
}

/** Renders a question's piano notes, pure tones and drone together into the reference cache, then plays them. */
internal fun hearEar(model: AppModel, key: String, title: String, audio: com.betterappmafia.apophenia.core.learn.EarAudio) {
    model.app.io.launch {
        val rate = model.app.audio.sampleRate
        val f = renderReference(model, "ref-${key.replace(Regex("[^A-Za-z0-9_#-]"), "_")}-v1.wav") {
            val pcm = if (audio.piano.isEmpty()) FloatArray((audio.seconds * rate).toInt()) else com.betterappmafia.apophenia.audio.Piano.render(model.app, audio.piano, rate, audio.seconds)
            com.betterappmafia.apophenia.core.learn.EarTone.render(audio, rate, pcm)
            audio.clip?.let { c ->
                // A stretch of your own recording, mono, with 20 ms fades so the cut doesn't click.
                val read = com.betterappmafia.apophenia.audio.Wav.readMono(java.io.File(c.path), rate, kotlin.math.ceil(c.start + c.length).toInt() + 1)
                if (read != null) {
                    val (samples, r) = read
                    val from = (c.start * r).toInt()
                    val n = minOf((c.length * r).toInt(), samples.size - from, pcm.size)
                    val fade = (0.02 * r).toInt().coerceAtLeast(1)
                    for (i in 0 until maxOf(0, n)) pcm[i] += samples[from + i] * minOf(1f, i.toFloat() / fade, (n - i).toFloat() / fade)
                }
            }
            pcm
        }
        model.app.audio.load(REFERENCE_SCRAP, -1, title, f.absolutePath, play = true)
    }
}

/**
 * Play it back (melodic and bass dictation): tap the notes in order on the piano, or on your guitar or bass; the first
 * note is given. CHECK marks each note; a wrong one shows what it was.
 */
@Composable
private fun SequenceAnswer(model: AppModel, q: EarQuestion, open: Boolean, checked: (Boolean) -> Unit) {
    val bass = Ear.source(q.drill, q.answer).first.kind == EarKind.BASS
    var seq by remember(q) { mutableStateOf(listOf(q.midis.first())) }
    var marks by remember(q) { mutableStateOf<List<Boolean>?>(null) }
    val tuning = if (bass) model.learnBass else model.learnGuitar
    var frets by remember(q, tuning) { mutableStateOf(List<Int?>(tuning.stringCount) { null }) }
    fun name(m: Int) = refName(model, com.betterappmafia.apophenia.core.learn.EarDictation.spell(q, m))
    fun add(m: Int) { if (open && seq.size < q.midis.size) seq = seq + m }
    Kv("PLAY IT BACK") {
        Text("${q.midis.size} notes. The first, ${name(q.midis.first())}, is given; tap the rest in order.", style = T.small)
    }
    if (model.earAnswerOn == "GUITAR") Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        TapNeck(tuning.strings.map { it.midi }, frets) { s, f -> frets = List(tuning.stringCount) { i -> if (i == s) f else null }; add(tuning.strings[s].midi + f) }
    } else Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        val low = bass || q.midis.min() < 52
        TapPiano(if (low) 28 else 52, if (low) 72 else 84, setOfNotNull(seq.lastOrNull())) { add(it) }
    }
    Kv("YOURS") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            seq.forEachIndexed { i, m ->
                val mark = marks?.getOrNull(i)
                Column {
                    Text(name(m), style = T.hi.copy(fontSize = 15.sp, color = when (mark) { true -> C.acc; false -> C.rec; null -> C.hi }))
                    if (mark == false) Text(name(q.midis[i]), style = T.label)
                }
            }
            repeat(q.midis.size - seq.size) { Text("·", style = T.hi.copy(fontSize = 15.sp, color = C.faint)) }
        }
    }
    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Sq("⌫", { if (open && seq.size > 1) seq = seq.dropLast(1) }, enabled = open && seq.size > 1)
        Sq("CLEAR", { if (open) seq = listOf(q.midis.first()) }, enabled = open)
        Sq("▶ MINE", { hearNotes(model, "seq-${seq.joinToString("_")}", "Yours", seq.mapIndexed { i, m -> com.betterappmafia.apophenia.core.sound.PlayNote(i * 0.6, i * 0.6 + 0.56, m, com.betterappmafia.apophenia.core.learn.RefSound.VELOCITY) }) })
        Sq("CHECK", {
            marks = com.betterappmafia.apophenia.core.learn.EarDictation.compare(q, seq)
            checked(Ear.check(q, seq))
        }, enabled = open && seq.size == q.midis.size)
    }
}

/** Rhythm dictation: the three bars written out (percussion staff), lettered; tap the one you heard. */
@Composable
private fun RhythmOptions(model: AppModel, q: EarQuestion, picked: String?, mode: String, pick: (String) -> Unit) {
    val meter = q.meter ?: return
    q.rhythm.forEachIndexed { i, bar ->
        val a = if ('|' in q.answer) q.answer.substringBefore('|') + "|$i" else "$i"
        val colour = when {
            picked == null || mode == "LISTEN" -> C.line2
            a == q.answer -> C.acc
            a == picked -> C.rec
            else -> C.line
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).border(1.5.dp, colour).clickable(role = Role.Button) { pick(a) }.padding(6.dp)) {
            Text("ABC"[i].toString(), style = T.button.copy(fontSize = 13.sp))
            val events = bar.map { n ->
                com.betterappmafia.apophenia.core.score.Event(n.d, if (n.rest) emptyList() else listOf(com.betterappmafia.apophenia.core.score.Note(com.betterappmafia.apophenia.core.pitch.SpelledPitch.parse("C5"))))
            }
            val part = com.betterappmafia.apophenia.core.score.Part("", com.betterappmafia.apophenia.core.score.Clef.PERCUSSION, bars = listOf(com.betterappmafia.apophenia.core.score.Bar(listOf(events))))
            NotationCanvas(com.betterappmafia.apophenia.core.score.Score(meter = meter, parts = listOf(part)), part, null, null, emptySet(), 0, model.chordDialect, model.chordStyle) { _, _, _, _, _ -> pick(a) }
        }
    }
    if (mode == "LISTEN") Text("Tap a bar to hear it.", style = T.small, modifier = Modifier.padding(14.dp))
}

/**
 * Tap it back: ▶ START plays a bar of clicks, the bar, then a bar of clicks while you tap it on the pad. Taps are timed
 * against the player's own position, so the phone's delay stays constant and is taken out (EarRhythm.grade).
 */
@Composable
private fun TapBack(model: AppModel, q: EarQuestion, open: Boolean, start: () -> Unit, done: (Boolean) -> Unit) {
    val audio = model.app.audio
    var armed by remember(q) { mutableStateOf(false) }
    var taps by remember(q) { mutableStateOf(listOf<Double>()) }
    var result by remember(q) { mutableStateOf<com.betterappmafia.apophenia.core.learn.TapResult?>(null) }
    val expected = remember(q) { com.betterappmafia.apophenia.core.learn.EarRhythmQuestions.expected(q) }
    val bar = com.betterappmafia.apophenia.core.learn.EarRhythm.barSeconds(q.meter!!, q.bpm)
    LaunchedEffect(armed) {
        if (!armed) return@LaunchedEffect
        var started = false
        val begun = System.currentTimeMillis()
        while (true) {
            kotlinx.coroutines.delay(40)
            val p = audio.player.value
            val ours = p != null && p.scrapId == REFERENCE_SCRAP && p.title == q.drill.name
            if (ours && p!!.playing) started = true
            if (started && (!ours || !p!!.playing || p.position >= p.frames)) break
            if (!started && System.currentTimeMillis() - begun > 8000) break
        }
        armed = false
        val r = com.betterappmafia.apophenia.core.learn.EarRhythm.grade(expected, taps.filter { it in (2 * bar - 0.3)..(3 * bar + 0.3) })
        result = r
        done(r.ok)
    }
    Kv("TAP IT BACK") { Text("A bar of clicks, the rhythm, then clicks again: tap the rhythm on the pad in that last bar.", style = T.small) }
    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Sq("▶ START", { taps = emptyList(); result = null; armed = true; start() }, on = true, enabled = open && !armed)
    }
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).heightIn(min = 150.dp).border(1.5.dp, if (armed) C.acc else C.line2)
            .pointerInput(armed) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val p = audio.player.value
                    if (armed && p != null && p.playing) taps = taps + audio.playerFrame().toDouble() / p.rate
                }
            },
        contentAlignment = Alignment.Center,
    ) { Text(if (armed) "TAP" else "▶ START, THEN TAP HERE", style = T.button.copy(fontSize = 16.sp, color = if (armed) C.hi else C.dim)) }
    result?.let { r ->
        Kv("TIMING") {
            Text(
                if (r.tapped != r.expected) "${r.tapped} taps for ${r.expected} notes."
                else "Each note: " + r.errorsMs.joinToString("  ") { (if (it >= 0) "+" else "") + it } + " ms (within ±${com.betterappmafia.apophenia.core.learn.EarRhythm.TAP_WINDOW_MS} counts). " +
                    "Your constant delay, taken out: ${r.offsetMs} ms (the phone's sound and touch delay plus yours).",
                style = T.body.copy(lineHeight = 18.sp),
            )
        }
    }
}
