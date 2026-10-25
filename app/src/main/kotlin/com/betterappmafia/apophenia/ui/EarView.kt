package com.betterappmafia.apophenia.ui

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
import com.betterappmafia.apophenia.core.learn.given
import com.betterappmafia.apophenia.data.EarResultEntity
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import kotlinx.coroutines.launch
import kotlin.random.Random

/*
 * Learn › Ear (plan §9; Christopher, 25 Oct 2026: "drop the course approach and just have the practice and high score
 * section", with "a streak counter for how many in a row"). PRACTICE: the drill types as rows that open on their drills
 * (no levels, no order to follow); tap a drill and you're practising. HIGH SCORES: every drill's best streak. The drill
 * screen: the streak large, the drill's best beside it, ▶ PLAY, SOUND ▾, ANSWER ▾ (by name, on the piano, on your
 * guitar), and the answer explained with the right one and yours to hear.
 */

@Composable
internal fun EarHome(model: AppModel) {
    var section by remember { mutableStateOf("PRACTICE") }
    var family by remember { mutableStateOf<String?>(null) }
    val streaks by model.app.db.earResults().streaks().collectAsState(emptyList())
    val bestOf = streaks.associate { it.drill to it.best }
    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Sq("PRACTICE", { section = "PRACTICE" }, on = section == "PRACTICE")
        Sq("HIGH SCORES", { section = "HIGH SCORES" }, on = section == "HIGH SCORES")
    }
    if (section == "HIGH SCORES") {
        val all = (listOf(Ear.mixed(null)) + Ear.chapters.map(Ear::mixed) + Ear.drills).associateBy { it.id }
        val played = streaks.filter { it.best > 0 && it.drill in all }.sortedWith(compareByDescending<com.betterappmafia.apophenia.data.EarStreak> { it.best }.thenByDescending { it.last })
        if (played.isEmpty()) Text("No streaks yet. Practise any drill: every answer right in a row counts, and your longest run on each drill is its high score.", style = T.body.copy(lineHeight = 18.sp), modifier = Modifier.padding(14.dp))
        played.forEach { s ->
            val d = all.getValue(s.drill)
            Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).underline().clickable(role = Role.Button) { model.earDrill = d.id }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(d.name, style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
                    Text("${d.chapter.uppercase()} · ${s.sessions} ${if (s.sessions == 1) "SESSION" else "SESSIONS"}", style = T.label)
                }
                Text("${s.best}", style = T.hi.copy(fontSize = 26.sp, color = C.acc))
                Text(" IN A ROW", style = T.label, modifier = Modifier.padding(start = 4.dp))
            }
        }
        return
    }
    val f = family
    if (f == null) {
        // The drill types, each opening on its drills (long lists open on family rows).
        val families = listOf(null) + Ear.chapters + com.betterappmafia.apophenia.core.learn.YourEar.CHAPTER
        families.forEach { c ->
            val label = c ?: "Mixed"
            val drills = when (c) {
                null -> listOf(Ear.mixed(null))
                com.betterappmafia.apophenia.core.learn.YourEar.CHAPTER -> emptyList()
                else -> listOf(Ear.mixed(c)) + Ear.drills.filter { it.chapter == c }
            }
            val top = drills.maxOfOrNull { bestOf[it.id] ?: 0 } ?: 0
            Row(
                Modifier.fillMaxWidth().heightIn(min = 58.dp).underline().clickable(role = Role.Button) { if (c == null) model.earDrill = Ear.mixed(null).id else family = c }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(if (c == null) "Mixed: everything" else label, style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
                    Text(when (c) {
                        null -> "QUESTIONS FROM EVERY DRILL"
                        com.betterappmafia.apophenia.core.learn.YourEar.CHAPTER -> "DRILLS BUILT FROM YOUR OWN SCRAPS"
                        else -> "${drills.size - 1} DRILLS"
                    } + if (top > 0) " · BEST $top IN A ROW" else "", style = T.label)
                }
                Text(if (c == null) "▶" else "›", style = T.title.copy(color = C.acc))
            }
        }
        return
    }
    TitleBar {
        Sq("‹", { family = null })
        Text(f.uppercase(), style = T.title, modifier = Modifier.weight(1f), maxLines = 1)
    }
    val own = f == com.betterappmafia.apophenia.core.learn.YourEar.CHAPTER
    if (own) {
        val pool by androidx.compose.runtime.produceState(model.earPool, f) { value = loadPool(model).also { model.earPool = it } }
        val p = pool
        if (p == null) Text("Looking through your material…", style = T.small, modifier = Modifier.padding(14.dp))
        else com.betterappmafia.apophenia.core.learn.YourEar.missing(p).forEach { Text(it, style = T.small.copy(lineHeight = 16.sp), modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) }
    }
    val rows = if (own) model.earPool?.let { com.betterappmafia.apophenia.core.learn.YourEar.drills(it) }.orEmpty()
        else listOf(Ear.mixed(f)) + Ear.drills.filter { it.chapter == f }
    rows.forEach { d ->
        val best = bestOf[d.id] ?: 0
        Row(
            Modifier.fillMaxWidth().heightIn(min = 62.dp).underline().clickable(role = Role.Button) { model.earDrill = d.id }.padding(start = 14.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(d.name, style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
                Text(d.note.ifEmpty { null } ?: if (d.id.startsWith("mixed")) "" else d.answers.joinToString(" · ") { Ear.label(d, it) }.uppercase(), style = T.label, maxLines = 2)
            }
            if (best > 0) Column(horizontalAlignment = Alignment.End) {
                Text("$best", style = T.hi.copy(fontSize = 20.sp, color = C.acc))
                Text("BEST", style = T.label)
            }
        }
    }
}

/** The answers and how the session is going, kept across recompositions of one drill screen. */
private class Session {
    var asked = 0
    var right = 0
    var streak = 0
    var best = 0
    var saved = false
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EarDrillView(model: AppModel, drill: EarDrill, requestMic: (() -> Unit) -> Unit) {
    // Practice only (Christopher, 25 Oct 2026): the old Listen and Test modes are gone.
    val mode = "PRACTICE"
    val streaks by model.app.db.earResults().streaks().collectAsState(emptyList())
    val stored = streaks.firstOrNull { it.drill == drill.id }?.best ?: 0
    val settings = model.earSettings
    val random = remember { Random(System.nanoTime()) }
    var round by remember { mutableIntStateOf(0) }
    val session = remember(drill.id, mode, round) { Session() }
    val missed = remember(drill.id, mode, round) { mutableStateMapOf<String, Int>() }
    var question by remember(drill.id, mode, round) { mutableStateOf(Ear.question(drill, settings, random)) }
    var picked by remember(question) { mutableStateOf<String?>(null) }
    var tapped by remember(question) { mutableStateOf(setOf<Int>()) }
    var frets by remember(question) { mutableStateOf(List<Int?>(model.learnGuitar.stringCount) { null }) }
    var counts by remember(drill.id, mode, round) { mutableStateOf(Triple(0, 0, 0)) }
    var soundMenu by remember { mutableStateOf(false) }
    var answerMenu by remember { mutableStateOf(false) }
    val kind = Ear.source(drill, question.answer).first.kind

    val currentMode by rememberUpdatedState(mode)
    fun save() {
        if (session.saved || session.asked == 0) return
        session.saved = true
        val r = EarResultEntity(drill = drill.id, mode = currentMode.lowercase(), correct = session.right, total = session.asked, bestStreak = session.best, finishedAt = System.currentTimeMillis())
        model.app.io.launch { model.app.db.earResults().add(r) }
    }
    DisposableEffect(drill.id, mode) { onDispose { save() } }

    fun play(q: EarQuestion = question) = hearEar(model, "ear-${Ear.source(drill, q.answer).first.kind}-${q.midis.joinToString("_")}-${q.direction}-${settings.chord}-${settings.context}-${q.cents}-${q.tonic}-${q.bpm}-${q.chords.flatten().joinToString("_")}-${q.rhythm.hashCode()}-${q.meter}-${q.answer}-${q.clip?.let { "${it.path.hashCode()}_${(it.start * 1000).toInt()}_${(it.length * 1000).toInt()}" }}", drill.name, Ear.sound(q, settings))
    LaunchedEffect(question) { if (mode != "LISTEN" && Ear.source(drill, question.answer).first.kind != EarKind.TAPBACK) play() }

    fun answer(a: String, right: Boolean) {
        if (picked != null) return
        picked = a
        session.asked++
        if (right) { session.right++; session.streak++; session.best = maxOf(session.best, session.streak); missed.remove(question.answer)?.let { if (it > 1) missed[question.answer] = it - 1 } }
        else { session.streak = 0; missed[question.answer] = (missed[question.answer] ?: 0) + 1 }
        counts = Triple(session.right, session.asked, session.streak)
    }
    fun next() { question = Ear.question(drill, settings, random, missed, question) }

    Column(Modifier.fillMaxSize()) {
        TitleBar {
            Sq("‹", { save(); model.earDrill = null })
            Text(drill.name, style = T.title, modifier = Modifier.weight(1f), maxLines = 1)
        }
        // The streak, large: right answers in a row this session, against the drill's high score.
        val streak = counts.third
        val best = maxOf(stored, session.best)
        val newBest = session.best > stored && session.best > 0
        Row(Modifier.fillMaxWidth().underline().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("STREAK", style = T.label)
                Text("$streak", style = T.hi.copy(fontSize = 44.sp, color = if (streak > 0) C.acc else C.faint))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (newBest) "NEW BEST" else "BEST", style = T.label.copy(color = if (newBest) C.acc else C.dim))
                Text("$best", style = T.hi.copy(fontSize = 26.sp))
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Sq(if (mode == "LISTEN") "▶ NEW ROOT" else "▶ PLAY", { if (mode == "LISTEN") next() else play() }, on = true)
                Box {
                    Sq("SOUND ▾", { soundMenu = true })
                    ConsoleMenu(soundMenu, { soundMenu = false }, "SOUND",
                        listOf(MenuItem("INTERVALS", "", enabled = false) {}) +
                            EarDirection.entries.map { d -> MenuItem(d.label, if (settings.direction == d) "✓" else "") { model.earSettings = settings.copy(direction = d) } } +
                            MenuItem("CHORDS", "", enabled = false) {} +
                            ChordSound.entries.map { s -> MenuItem(s.label, if (settings.chord == s) "✓" else "") { model.earSettings = settings.copy(chord = s) } } +
                            MenuItem("SCALE DEGREES", "", enabled = false) {} +
                            com.betterappmafia.apophenia.core.learn.DegreeContext.entries.map { c -> MenuItem(c.label, if (settings.context == c) "✓" else "") { model.earSettings = settings.copy(context = c) } } +
                            MenuItem("Random key", if (settings.randomKey) "✓ ON" else "OFF · ON C") { model.earSettings = settings.copy(randomKey = !settings.randomKey) })
                }
                if (mode != "LISTEN") Box {
                    Sq("ANSWER ▾", { answerMenu = true })
                    ConsoleMenu(answerMenu, { answerMenu = false }, "ANSWER", listOf("NAME" to "By name", "PIANO" to "On the piano", "GUITAR" to "On your guitar · ${model.learnGuitar.name}").map { (k, t) ->
                        MenuItem(t, if (model.earAnswerOn == k) "✓" else "") { model.earAnswerOn = k }
                    })
                }
            }
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
                    TapPiano(if (kind == EarKind.OCTAVE) 36 else 43, if (kind == EarKind.OCTAVE) 96 else 84, lit) { m -> if (picked == null && m != given) tapped = if (single) setOf(m) else if (m in tapped) tapped - m else tapped + m }
                } else Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    TapNeck(model.learnGuitar.strings.map { it.midi }, frets) { s, f -> if (picked == null) frets = frets.toMutableList().also { it[s] = if (it[s] == f) null else f } }
                }
                val played = if (model.earAnswerOn == "PIANO") tapped else frets.mapIndexedNotNull { s, f -> f?.let { model.learnGuitar.strings[s].midi + it } }.filter { it != given }.toSet()
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
