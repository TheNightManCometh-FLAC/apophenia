package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.audio.Piano
import com.betterappmafia.apophenia.core.catalog.Chords
import com.betterappmafia.apophenia.core.catalog.Scales
import com.betterappmafia.apophenia.core.chord.ChordDialect
import com.betterappmafia.apophenia.core.chord.ChordDisplay
import com.betterappmafia.apophenia.core.chord.ChordStyle
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.interval.Interval
import com.betterappmafia.apophenia.core.instrument.Tuning
import com.betterappmafia.apophenia.core.instrument.Tunings
import com.betterappmafia.apophenia.core.learn.ChordPages
import com.betterappmafia.apophenia.core.learn.Commentary
import com.betterappmafia.apophenia.core.learn.IntervalPages
import com.betterappmafia.apophenia.core.learn.TuningReference
import com.betterappmafia.apophenia.core.learn.Progressions
import com.betterappmafia.apophenia.core.learn.RefHit
import com.betterappmafia.apophenia.core.learn.RefItem
import com.betterappmafia.apophenia.core.learn.RefKind
import com.betterappmafia.apophenia.core.learn.RefSound
import com.betterappmafia.apophenia.core.learn.Reference
import com.betterappmafia.apophenia.core.learn.ScalePages
import com.betterappmafia.apophenia.core.learn.Voicing
import com.betterappmafia.apophenia.core.learn.Voicings
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.pitch.SpelledPitch
import com.betterappmafia.apophenia.core.rhythm.Duration
import com.betterappmafia.apophenia.core.score.Bar
import com.betterappmafia.apophenia.core.score.Clef
import com.betterappmafia.apophenia.core.score.Event
import com.betterappmafia.apophenia.core.score.Note
import com.betterappmafia.apophenia.core.score.Part
import com.betterappmafia.apophenia.core.score.Score
import com.betterappmafia.apophenia.core.sound.PlayNote
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import kotlinx.coroutines.launch

/*
 * Learn's home and the Reference (plan §8; C4 'learn' screen): FIND, the categories, and a generated page for every
 * interval, scale/mode and chord: notes with their intervals, the page drawn on your guitar or bass tuning, the piano or
 * the staff (SHOW ON ▾), hear it with an A/B comparison, and what the catalogs say about it. Change the root and the
 * page regenerates.
 */

@Composable
internal fun LearnHome(model: AppModel) {
    var query by remember { mutableStateOf("") }
    val list = model.learnList
    Column(Modifier.fillMaxSize()) {
        TitleBar {
            if (list != null) Sq("‹", { model.learnList = if (":" in list) list.substringBefore(":") else null })
            Text(model.learnPart, style = T.title)
            Text(
                when (model.learnPart) { "COURSE" -> "PICK A CATEGORY"; "EAR" -> "PRACTICE · HIGH SCORES"; else -> "EVERYTHING, ON YOUR TUNING" },
                style = T.label, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f), maxLines = 1,
            )
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when {
                model.learnPart == "EAR" -> EarHome(model)
                model.learnPart == "COURSE" -> CourseHome(model)
                model.learnPart != "REFERENCE" -> Text(
                    "The ${model.learnPart.lowercase().replaceFirstChar(Char::uppercase)} arrives in a later M6 build. The Reference is here now.",
                    style = T.small.copy(lineHeight = 16.sp), modifier = Modifier.padding(14.dp),
                )
                list == "GROOVES" -> DrumLists(model, grooves = true)
                list == "RUDIMENTS" -> DrumLists(model, grooves = false)
                list == "NAME" -> NameChord(model)
                list == "TUNING" -> TuningFamilies(model)
                list == "PROGRESSION" -> ProgressionFamilies(model)
                list?.startsWith("PROGRESSION:") == true -> Progressions.inFamily(list.removePrefix("PROGRESSION:")).forEach { RefRow(model, Reference.item(RefKind.PROGRESSION, it.id)!!, null) }
                list?.startsWith("TUNING:") == true -> TuningReference.inFamily(list.removePrefix("TUNING:")).forEach { RefRow(model, Reference.item(RefKind.TUNING, it.tuning.id)!!, null) }
                list != null -> RefKindList(model, RefKind.valueOf(list))
                else -> {
                    ConsoleField("FIND", query, { query = it }, placeholder = "E7#9 · D dorian · m3 · lydian")
                    if (query.isNotBlank()) {
                        val hits = remember(query) { Reference.find(query).take(40) }
                        if (hits.isEmpty()) Text("Nothing by that name.", style = T.small, modifier = Modifier.padding(14.dp))
                        hits.forEach { h -> RefRow(model, h.item, h.root) }
                    } else {
                        RefKind.entries.forEach { k ->
                            CategoryRow("${Reference.items.count { it.kind == k }}", k.label, when (k) {
                                RefKind.INTERVAL -> "UNISON TO DOUBLE OCTAVE"
                                RefKind.SCALE -> "MODES OF MAJOR, MELODIC, HARMONIC MINOR AND MAJOR · SYMMETRIC · BEBOP · OTHER"
                                RefKind.CHORD -> "TRIADS TO THIRTEENTHS, ALTERED, SUS, ADD"
                                RefKind.PROGRESSION -> "POP, MODAL, JAZZ, BLUES, CADENCES · ON ANY KEY"
                                RefKind.TUNING -> "STANDARD, DROP, OPEN MAJOR AND MINOR, MODAL, REGULAR, 7 AND 8 STRINGS"
                            }) { model.learnList = k.name }
                        }
                        CategoryRow("${com.betterappmafia.apophenia.core.tools.Groove.COMMON.size}", "Drum grooves", "COMMON BEATS AS DRUM TAB") { model.learnList = "GROOVES" }
                        CategoryRow("40", "Drum rudiments", "THE PAS RUDIMENTS") { model.learnList = "RUDIMENTS" }
                        CategoryRow("·", "Name a chord", "TAP NOTES ON THE PIANO OR YOUR GUITAR") { model.learnList = "NAME" }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(count: String, name: String, note: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 62.dp).underline().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(count, style = T.label, modifier = Modifier.width(34.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
            Line1(note, T.label)
        }
        Text("›", style = T.hi.copy(color = C.acc))
    }
}

/** Guitar tunings by family: one row each, then the family's tunings. */
@Composable
private fun TuningFamilies(model: AppModel) {
    TuningReference.families.forEach { f ->
        val list = TuningReference.inFamily(f)
        CategoryRow("${list.size}", f.replaceFirstChar(Char::uppercase), (TuningReference.familyNotes[f] ?: "") + " · " + list.take(3).joinToString(", ") { it.tuning.name }) {
            model.learnList = "TUNING:$f"
        }
    }
}

@Composable
private fun RefKindList(model: AppModel, kind: RefKind) {
    Reference.items.filter { it.kind == kind }.groupBy { it.family }.forEach { (family, list) ->
        Text(family.uppercase().replace('-', ' '), style = T.label.copy(color = C.acc), modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 4.dp))
        list.forEach { RefRow(model, it, null) }
    }
}

@Composable
private fun RefRow(model: AppModel, item: RefItem, root: PitchName?) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).underline().clickable(role = Role.Button) { model.refPage = RefHit(item, root ?: model.refPage?.root) }.padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text((root?.let { refName(model, it) + " " } ?: "") + item.title, style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
            Line1(rowNote(item), T.small)
        }
        Text("›", style = T.hi.copy(color = C.acc))
    }
}

private fun rowNote(item: RefItem) = when (item.kind) {
    RefKind.INTERVAL -> "${item.id} · ${Interval.parse(item.id).semitones} SEMITONES"
    RefKind.SCALE -> Scales.byId(item.id).degrees.joinToString(" ") { degreeText(it.toString()) } + (if (item.also.isNotEmpty()) " · " + item.also.joinToString(" · ") else "")
    RefKind.CHORD -> Chords.byId(item.id).let { c -> c.symbols.joinToString(" · ") + "  " + c.degrees.joinToString(" ") { degreeText(it.toString()) } }
    RefKind.PROGRESSION -> Progressions.byId(item.id).let { p -> (if (p.minor) "MINOR · " else "MAJOR · ") + item.also.joinToString(" · ") }
    RefKind.TUNING -> item.also.joinToString(" ").replace("#", "♯").replace(Regex("(?<=[A-G])b"), "♭")
}

/** A degree token or interval with real accidentals: b3 → ♭3, bb7 → 𝄫7, #11 → ♯11. */
internal fun degreeText(s: String) = s.replace(Regex("bb(?=\\d)"), "𝄫").replace(Regex("b(?=\\d)"), "♭").replace(Regex("#(?=\\d)"), "♯").replace(Regex("(?<![A-Za-z])x(?=\\d)"), "𝄪")

/** A note name with real accidentals, in the user's note names (CHORDS ▾). */
internal fun refName(model: AppModel, p: PitchName) = ChordDisplay.name(p, model.chordDialect.names)

// ------------------------------------------------------------------------------------------------------------- pages

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RefPageView(model: AppModel, hit: RefHit) {
    val root = hit.root ?: PitchName.parse("C")
    var rootMenu by remember { mutableStateOf(false) }
    val item = hit.item
    fun open(kind: RefKind, id: String, r: PitchName = root) { Reference.item(kind, id)?.let { model.refPage = RefHit(it, r) } }
    Column(Modifier.fillMaxSize()) {
        TitleBar {
            Sq("‹", { model.refPage = null })
            Text(item.title.uppercase(), style = T.title, modifier = Modifier.weight(1f), maxLines = 1)
            if (item.kind != RefKind.TUNING) Box {
                Sq("ROOT: ${refName(model, root)} ▾", { rootMenu = true })
                ConsoleMenu(rootMenu, { rootMenu = false }, "ROOT", Reference.roots.map { r ->
                    MenuItem(refName(model, r), if (r == root) "✓" else "") { model.refPage = RefHit(item, r) }
                })
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (item.kind) {
                RefKind.INTERVAL -> IntervalBody(model, Interval.parse(item.id), root, ::open)
                RefKind.SCALE -> ScaleBody(model, item.id, root, ::open)
                RefKind.CHORD -> ChordBody(model, item.id, root, ::open)
                RefKind.PROGRESSION -> ProgressionBody(model, item.id, root, ::open)
                RefKind.TUNING -> TuningBody(model, item.id)
            }
            Gap(24.dp)
        }
    }
}

private typealias Open = (RefKind, String, PitchName) -> Unit

@Composable
private fun Header(big: AnnotatedString, also: String) {
    Column(Modifier.fillMaxWidth().underline().padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(big, style = T.hi.copy(fontSize = 34.sp))
        if (also.isNotEmpty()) Text(also, style = T.label, modifier = Modifier.padding(top = 4.dp))
    }
}

/** The NOTES row: each note over its interval and semitones above the root. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotesRow(model: AppModel, notes: List<PitchName>, labels: List<String>) {
    Kv("NOTES") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            notes.forEachIndexed { i, n ->
                Column(Modifier.width(54.dp)) {
                    Text(refName(model, n), style = T.hi.copy(fontSize = 15.sp, color = if (i == 0) C.acc else C.hi))
                    Text(labels[i], style = T.label)
                }
            }
        }
    }
}

/** Tappable chips that open other pages. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Links(label: String, links: List<Pair<String, () -> Unit>>) {
    if (links.isEmpty()) return
    Kv(label) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            links.forEach { (t, go) -> Sq(t, go) }
        }
    }
}

@Composable
private fun Para(label: String, text: String) = Kv(label) { Text(text, style = T.body.copy(lineHeight = 18.sp)) }

// ---------------------------------------------------------------------------------------------------------- interval

@Composable
private fun IntervalBody(model: AppModel, i: Interval, root: PitchName, open: Open) {
    val page = remember(i, root) { IntervalPages.page(i, root) }
    Header(AnnotatedString(i.toString()), page.name.uppercase())
    val above = page.above
    NotesRow(model, listOfNotNull(root, above), listOf("ROOT · 0", "${i} · ${i.semitones}"))
    val lowMidi = RefSound.midis(root.pitchClass, listOf(0), 60).first()
    val midis = listOf(lowMidi, lowMidi + i.semitones)
    ShowOn(model, midis, listOfNotNull(root, above), listOf("R", i.toString()), voicing = null, staff = listOf(midis))
    Hear(model, "interval-${i}-${root}", RefSound.interval(midis[0], midis[1]), "UP · DOWN · TOGETHER",
        Reference.intervals.filter { it != i && !it.isCompound }.map { other -> other.toString() to RefSound.interval(lowMidi, lowMidi + other.semitones) })
    Para("SIZE", "${page.semitones} semitones · ${page.semitones * 100} ¢ in equal temperament" + (if (page.semitones % 12 == 6) " · the tritone" else ""))
    page.just?.let { j ->
        val diff = j.cents - page.semitones % 12 * 100.0
        Para("JUST", "${j.ratio} · ${"%.2f".format(j.cents)} ¢ (${if (diff >= 0) "+" else ""}${"%.2f".format(diff)} ¢ against equal)" + (j.note?.let { "\n$it" } ?: ""))
    }
    Links("INVERSION", listOf(page.inversion.toString() + " · " + Reference.intervalName(page.inversion) to { open(RefKind.INTERVAL, page.inversion.toString(), root) }))
    Links("SAME KEY ON A PIANO", page.enharmonic.map { e -> "$e · ${Reference.intervalName(e)}" to { open(RefKind.INTERVAL, e.toString(), root) } })
    Para("ON ${refName(model, root)}", listOfNotNull(
        page.above?.let { "${refName(model, root)} up to ${refName(model, it)}" },
        page.below?.let { "${refName(model, it)} up to ${refName(model, root)} (the interval below)" },
    ).joinToString("\n"))
    Written(Commentary.intervals[i.toString()])
    Yours(model, root) { com.betterappmafia.apophenia.core.learn.InYours.interval(i, it) }
}

// ------------------------------------------------------------------------------------------------------------- scale

@Composable
private fun ScaleBody(model: AppModel, id: String, root: PitchName, open: Open) {
    val def = Scales.byId(id)
    val page = remember(id, root) { ScalePages.page(def, root) }
    val r = page.root
    Header(AnnotatedString("${refName(model, r)} ${def.names.first()}"), def.names.drop(1).joinToString(" · ").uppercase())
    page.respelledFrom?.let { Para("SPELLED ON", "${refName(model, r)}: on ${refName(model, it)} this scale would need triple accidentals.") }
    NotesRow(model, page.notes, def.degrees.map { d -> degreeText(d.toString()) + " · " + Math.floorMod(d.interval.semitones, 12) })
    val semis = def.semitones
    val midis = RefSound.midis(r.pitchClass, semis, 60)
    ShowOn(model, midis, page.notes, def.degrees.map { degreeText(it.toString()) }, voicing = null, staff = midis.map { listOf(it) } + listOf(listOf(midis.first() + 12)))
    Hear(model, "scale-$id-$r", RefSound.scale(midis), "UP AND DOWN",
        Scales.all.filter { it.id != id }.map { s -> s.names.first() to RefSound.scale(RefSound.midis(r.pitchClass, s.semitones, 60)) })
    Para("STEPS", page.steps.joinToString(" ") { ScalePages.stepName(it) } + "   (H half · W whole · 3 a minor third)")
    page.parent?.let { (p, pr) -> Links("MODE OF", listOf("Mode ${def.modeIndex} of ${refName(model, pr)} ${p.names.first()}" to { open(RefKind.SCALE, p.id, pr) })) }
    Links("SAME NOTES", page.modes.filter { it.first.id != id }.map { (m, mr) -> "${refName(model, mr)} ${m.names.first()}" to { open(RefKind.SCALE, m.id, mr) } } +
        page.sameNotes.map { (s, sr) -> "${refName(model, sr)} ${s.names.first()}" to { open(RefKind.SCALE, s.id, sr) } })
    val key = KeyMode(r, id)
    fun chordLinks(list: List<com.betterappmafia.apophenia.core.learn.DegreeChord>) = list.map { dc ->
        val sym = dc.symbol
        val numeral = sym?.let { ChordDisplay.of(it, ChordStyle.CLASSICAL, key)?.text } ?: "${dc.degree}"
        val shown = sym?.let { chordShown(model, it, key).text } ?: dc.notes.joinToString(" ") { refName(model, it) }
        "$numeral  $shown" to { val c = dc.chord; if (c != null) open(RefKind.CHORD, c.id, dc.root) }
    }
    Links("TRIADS", chordLinks(page.triads))
    Links("SEVENTHS", chordLinks(page.sevenths))
    Written(Commentary.scales[id])
    Yours(model, r) { com.betterappmafia.apophenia.core.learn.InYours.scale(def, it) }
    def.disputed?.let { Para("NAMING", it) }
}

// ------------------------------------------------------------------------------------------------------------- chord

@Composable
private fun ChordBody(model: AppModel, id: String, root: PitchName, open: Open) {
    val def = Chords.byId(id)
    val page = remember(id, root) { ChordPages.page(def, root) }
    val r = page.root
    Header(chordShown(model, page.symbols.first(), null), (listOf((Reference.CHORD_NAMES[id] ?: "").uppercase()) + page.symbols.drop(1).map { com.betterappmafia.apophenia.core.sheet.ChordText.display(it) }.distinct()).joinToString(" · "))
    page.respelledFrom?.let { Para("SPELLED ON", "${refName(model, r)}: on ${refName(model, it)} this chord would need triple accidentals.") }
    NotesRow(model, page.notes, def.degrees.map { d -> d.interval.toString() + " · " + d.interval.semitones })
    val midis = RefSound.midis(r.pitchClass, def.semitones, 48)
    val optional = def.omitOrder.map { Math.floorMod(r.pitchClass + it.interval.semitones, 12) }.toSet()
    val tuning = if (model.learnShow == "BASS") model.learnBass else model.learnGuitar
    val shapes = remember(id, r, tuning) { Voicings.find(tuning, page.notes.map { it.pitchClass }, optional) }
    val staffMidis = RefSound.midis(r.pitchClass, def.semitones, 60)
    ShowOn(model, midis, page.notes, def.degrees.map { degreeText(it.toString()) }, voicing = shapes, staff = listOf(staffMidis) + staffMidis.map { listOf(it) })
    Hear(model, "chord-$id-$r", RefSound.chord(midis), "CHORD · ARPEGGIO",
        Chords.all.filter { it.id != id }.map { c -> (c.symbols.first().removePrefix("C").ifEmpty { "major" }) to RefSound.chord(RefSound.midis(r.pitchClass, c.semitones, 48)) })
    Para("FORMULA", def.degrees.joinToString(" ") { degreeText(it.toString()) } +
        if (def.omitOrder.isNotEmpty()) "\nVoicings leave out first: " + def.omitOrder.joinToString(", ") { degreeText(it.toString()) } else "")
    Links("INVERSIONS", page.inversions.drop(1).map { inv -> "${chordShown(model, inv.symbol, null).text}  ${inv.name}${if (inv.figures.isNotEmpty()) " · " + inv.figures else ""}" to {} })
    Links("SAME NOTES", page.sameNotes.map { (c, cr) -> chordShown(model, Reference.ascii(cr) + c.symbols.first().removePrefix("C"), null).text to { open(RefKind.CHORD, c.id, cr) } })
    Links("SCALES THAT HOLD IT", page.scales.map { s -> "${refName(model, r)} ${s.names.first()}" to { open(RefKind.SCALE, s.id, r) } })
    Written(Commentary.chords[id])
    Yours(model, r) { com.betterappmafia.apophenia.core.learn.InYours.chord(def, it, model.chordDialect) }
    def.note?.let { Para("NAMING", it) }
}

// ------------------------------------------------------------------------------------------------------- progression

/** Named progressions by family. */
@Composable
private fun ProgressionFamilies(model: AppModel) {
    Progressions.families.forEach { f ->
        val list = Progressions.inFamily(f)
        CategoryRow("${list.size}", f.replaceFirstChar(Char::uppercase), (Progressions.familyNotes[f] ?: "") + " · " + list.take(3).joinToString(", ") { it.name }) {
            model.learnList = "PROGRESSION:$f"
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressionBody(model: AppModel, id: String, root: PitchName, open: Open) {
    val def = Progressions.byId(id)
    val page = remember(id, root) { Progressions.page(def, root) }
    var sel by remember(id, root) { mutableIntStateOf(0) }
    val key = page.keyMode
    Header(AnnotatedString(def.name), def.also.joinToString(" · "))
    page.respelledFrom?.let { Para("SPELLED IN", "${refName(model, page.key)}: in ${refName(model, it)} a chord would need a double sharp or flat.") }
    Kv("IN ${refName(model, page.key)} ${if (def.minor) "MINOR" else "MAJOR"}") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            page.chords.forEachIndexed { i, c ->
                Column(Modifier.background(if (i == sel) C.selected else C.bg).clickable(role = Role.Button) { sel = i }.padding(horizontal = 6.dp, vertical = 4.dp)) {
                    Text(chordShown(model, c.symbol, key), style = T.hi.copy(fontSize = 15.sp))
                    Text(ChordDisplay.of(c.symbol, ChordStyle.CLASSICAL, key)?.annotated() ?: AnnotatedString(c.step.degree), style = T.label)
                }
            }
        }
    }
    page.chords.getOrNull(sel)?.let { c ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(chordShown(model, c.symbol, key), style = T.hi.copy(fontSize = 15.sp), modifier = Modifier.weight(1f))
            Sq("▶", { hearNotes(model, "prog-chord-${c.symbol}", c.symbol, RefSound.block(c.midis)) })
            Sq("OPEN ›", { open(RefKind.CHORD, c.step.chord, c.root) })
        }
        ShowRef(model, RefKind.CHORD, c.step.chord, c.root)
    }
    Para("BARS", Progressions.bars(page).joinToString(" | ", "| ", " |") { bar -> if (bar.isEmpty()) "%" else bar.joinToString(" ") { chordShown(model, it.symbol, key).text } })
    Hear(model, "progression-$id-${page.key}", Progressions.sound(page), "PLAY IT",
        Progressions.all.filter { it.id != id }.map { o -> o.name to Progressions.sound(Progressions.page(o, page.key)) })
    Para("WHERE", def.where)
    Yours(model, page.key) { Progressions.inYours(def, it, model.chordDialect) }
}

// ------------------------------------------------------------------------------------------------------------ tuning

@Composable
private fun TuningBody(model: AppModel, id: String) {
    val entry = TuningReference.byId(id) ?: return
    val t = entry.tuning
    val page = remember(id) { TuningReference.page(entry) }
    fun open(s: List<com.betterappmafia.apophenia.core.pitch.SpelledPitch>) = s.mapIndexed { i, p -> PlayNote(i * 0.06, 2.6, p.midi, RefSound.VELOCITY) }
    Header(AnnotatedString(t.strings.joinToString(" ") { refName(model, it.name) }), "${t.stringCount} STRINGS · ${entry.family.uppercase()}")
    Kv("STRINGS") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            page.strings.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${s.number}", style = T.label, modifier = Modifier.width(22.dp))
                    Text(s.pitch, style = T.hi.copy(fontSize = 15.sp), modifier = Modifier.width(54.dp))
                    Text("${s.hz} Hz", style = T.small, modifier = Modifier.width(84.dp))
                    Text(s.change?.let { c -> if (c == 0) "·" else if (c > 0) "+$c" else "−${-c}" } ?: "", style = T.small.copy(color = if ((s.change ?: 0) != 0) C.acc else C.faint), modifier = Modifier.width(34.dp))
                    Text(s.toNext ?: "", style = T.small)
                }
            }
        }
    }
    Para("FROM STANDARD", if (page.strings.all { it.change == null }) "No standard to compare with." else if (page.changed == 0) "This is standard." else
        "${page.changed} of ${t.stringCount} strings retuned (semitones from standard in the third column).")
    Para("OPEN STRINGS", if (page.chords.isEmpty()) "No chord from the catalog." else page.chords.joinToString(" · ") { chordShown(model, it.symbol, null).text })
    Hear(model, "tuning-$id", open(t.strings), "OPEN STRINGS",
        TuningReference.all.filter { it.tuning.id != id && it.tuning.stringCount == t.stringCount }.map { o -> o.tuning.name to open(o.tuning.strings) })
    entry.note?.let { Para("NOTE", it) }
    Kv("ON THE NECK") {
        val using = model.learnGuitar.id == id
        Sq(if (using) "✓ THE REFERENCE NECK USES THIS TUNING" else "USE FOR THE REFERENCE NECK", { model.chooseLearnTuning(t, false) })
    }
    val names = t.strings.associate { it.name.pitchClass to it.name }
    NeckView(model, t, t.strings.associate { it.name.pitchClass to refName(model, it.name) }, t.strings.minBy { it.midi }.name.pitchClass, names)
}

// ------------------------------------------------------------------------------------------------------- show on ▾

@Composable
internal fun ShowOn(model: AppModel, midis: List<Int>, notes: List<PitchName>, labels: List<String>, voicing: List<Voicing>?, staff: List<List<Int>>) {
    var menu by remember { mutableStateOf(false) }
    var tuneMenu by remember { mutableStateOf(false) }
    var tuneFamily by remember { mutableStateOf<String?>(null) }
    val show = model.learnShow
    val bass = show == "BASS"
    val tuning = if (bass) model.learnBass else model.learnGuitar
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            Sq("SHOW ON: $show ▾", { menu = true })
            ConsoleMenu(menu, { menu = false }, "SHOW ON", listOf("GUITAR" to model.learnGuitar.name, "BASS" to model.learnBass.name, "PIANO" to "", "STAFF" to "").map { (k, note) ->
                MenuItem(k.lowercase().replaceFirstChar(Char::uppercase), if (show == k) "✓ ${note.uppercase()}" else note.uppercase()) { model.chooseLearnShow(k) }
            })
        }
        if (show == "GUITAR" || bass) Box {
            Sq("TUNING ▾", { tuneMenu = true })
            val family = tuneFamily
            val items = when {
                bass -> Tunings.all.filter { it.id.startsWith("bass") }.map { t ->
                    MenuItem(t.name, if (t == tuning) "✓" else t.strings.joinToString(" ") { refName(model, it.name) }) { model.chooseLearnTuning(t, true) }
                }
                family == null -> TuningReference.families.map { f ->
                    val list = TuningReference.inFamily(f)
                    MenuItem(f.replaceFirstChar(Char::uppercase), (if (list.any { it.tuning == tuning }) "✓ " else "") + "${list.size} ›") { tuneFamily = f; tuneMenu = true }
                }
                else -> listOf(MenuItem("‹ All families") { tuneFamily = null; tuneMenu = true }) + TuningReference.inFamily(family).map { e ->
                    MenuItem(e.tuning.name, if (e.tuning == tuning) "✓" else e.tuning.strings.joinToString(" ") { refName(model, it.name) }) { model.chooseLearnTuning(e.tuning, false); tuneFamily = null }
                }
            }
            ConsoleMenu(tuneMenu, { tuneMenu = false }, if (bass) "BASS TUNING" else family?.uppercase() ?: "GUITAR TUNING · PICK A FAMILY", items)
        }
    }
    val pcs = notes.map { it.pitchClass }
    val names = notes.associate { it.pitchClass to it }
    when (show) {
        "PIANO" -> PianoView(model, midis, names)
        "STAFF" -> StaffView(model, notes, staff)
        else -> {
            if (voicing != null && !bass) ShapeView(model, tuning, voicing, names)
            NeckView(model, tuning, pcs.zip(labels).toMap(), pcs.firstOrNull(), names)
        }
    }
}


/** A Reference item (interval, scale or chord) on [root], drawn the way its own page draws it, for the Course's TRY. */
@Composable
internal fun ShowRef(model: AppModel, kind: RefKind, id: String, root: PitchName) {
    when (kind) {
        RefKind.INTERVAL -> {
            val i = Interval.parse(id)
            val page = remember(i, root) { IntervalPages.page(i, root) }
            val low = RefSound.midis(root.pitchClass, listOf(0), 60).first()
            val midis = listOf(low, low + i.semitones)
            ShowOn(model, midis, listOfNotNull(root, page.above), listOf("R", i.toString()), voicing = null, staff = listOf(midis))
        }
        RefKind.SCALE -> {
            val def = Scales.byId(id)
            val page = remember(id, root) { ScalePages.page(def, root) }
            val midis = RefSound.midis(page.root.pitchClass, def.semitones, 60)
            ShowOn(model, midis, page.notes, def.degrees.map { degreeText(it.toString()) }, voicing = null, staff = midis.map { listOf(it) } + listOf(listOf(midis.first() + 12)))
        }
        RefKind.CHORD -> {
            val def = Chords.byId(id)
            val page = remember(id, root) { ChordPages.page(def, root) }
            val r = page.root
            val optional = def.omitOrder.map { Math.floorMod(r.pitchClass + it.interval.semitones, 12) }.toSet()
            val tuning = if (model.learnShow == "BASS") model.learnBass else model.learnGuitar
            val shapes = remember(id, r, tuning) { Voicings.find(tuning, page.notes.map { it.pitchClass }, optional) }
            val staffMidis = RefSound.midis(r.pitchClass, def.semitones, 60)
            ShowOn(model, RefSound.midis(r.pitchClass, def.semitones, 48), page.notes, def.degrees.map { degreeText(it.toString()) }, voicing = shapes, staff = listOf(staffMidis) + staffMidis.map { listOf(it) })
        }
        RefKind.PROGRESSION, RefKind.TUNING -> {}
    }
}
/** A chord's shapes on the guitar, one at a time with ‹ ›, the notes named in the dots. */
@Composable
private fun ShapeView(model: AppModel, tuning: Tuning, shapes: List<Voicing>, names: Map<Int, PitchName>) {
    var at by remember(shapes) { mutableIntStateOf(0) }
    if (shapes.isEmpty()) { Text("No shape within four frets on ${tuning.name}.", style = T.small, modifier = Modifier.padding(14.dp)); return }
    val v = shapes[at.coerceIn(0, shapes.size - 1)]
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Sq("‹", { at = (at - 1 + shapes.size) % shapes.size })
        Column(Modifier.weight(1f)) {
            Text(v.text, style = T.hi.copy(fontSize = 15.sp))
            Text("SHAPE ${at + 1} OF ${shapes.size} · ${tuning.name.uppercase()}", style = T.label)
        }
        Sq("▶", { hearNotes(model, "shape-${tuning.id}-${v.text}-${v.midis.joinToString("_")}", "Shape ${v.text}", strum(v.midis)) })
        Sq("›", { at = (at + 1) % shapes.size })
    }
    val first = v.frets.filterNotNull().filter { it > 0 }.minOrNull() ?: 1
    val from = if (v.frets.filterNotNull().max() <= 4) 0 else first - 1
    Fretboard(tuning, from, from + 5, v.frets.mapIndexedNotNull { s, f -> f?.let { Triple(s, it, names[Math.floorMod(tuning.strings[s].midi + it, 12)]?.let { n -> refName(model, n) } ?: "") } },
        muted = v.frets.indices.filter { v.frets[it] == null }.toSet(), rootPc = v.midis.min() % 12, cell = 46)
}

private fun strum(midis: List<Int>): List<PlayNote> = midis.sorted().mapIndexed { i, m -> PlayNote(i * 0.035, 2.4, m, RefSound.VELOCITY) }

/** Every place the page's notes fall on the neck, frets 0–15, labelled with their degree; the root in copper. */
@Composable
private fun NeckView(model: AppModel, tuning: Tuning, labels: Map<Int, String>, rootPc: Int?, names: Map<Int, PitchName>) {
    val dots = tuning.strings.indices.flatMap { s -> (0..15).mapNotNull { f -> labels[Math.floorMod(tuning.strings[s].midi + f, 12)]?.let { Triple(s, f, it) } } }
    Text("ON THE NECK · ${tuning.name.uppercase()} · ${tuning.strings.joinToString(" ") { refName(model, it.name) }}", style = T.label, modifier = Modifier.padding(start = 14.dp, top = 10.dp))
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Fretboard(tuning, 0, 15, dots, emptySet(), rootPc, cell = 44)
    }
}

/**
 * A neck from fret [from] to [to], string 1 at the top (as tab reads), dots carrying [dots]' labels at (string index,
 * fret); fret 0 dots sit left of the nut. [muted] strings get an x.
 */
@Composable
private fun Fretboard(tuning: Tuning, from: Int, to: Int, dots: List<Triple<Int, Int, String>>, muted: Set<Int>, rootPc: Int?, cell: Int) {
    val n = tuning.stringCount
    val rowH = 26.dp
    Canvas(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).width((cell * (to - from) + 64).dp).height(rowH * n + 20.dp)) {
        val left = 40.dp.toPx()
        val cw = cell.dp.toPx()
        val rh = rowH.toPx()
        fun y(s: Int) = rh / 2 + (n - 1 - s) * rh
        val paint = android.graphics.Paint().apply { isAntiAlias = true; textSize = 10.dp.toPx(); typeface = android.graphics.Typeface.MONOSPACE; textAlign = android.graphics.Paint.Align.CENTER }
        for (s in 0 until n) drawLine(C.line2, Offset(left, y(s)), Offset(left + cw * (to - from), y(s)), 1.5f)
        for (f in from..to) {
            val x = left + (f - from) * cw
            drawLine(if (f == 0) C.hi else C.line2, Offset(x, y(n - 1)), Offset(x, y(0)), if (f == 0) 4f else 1.5f)
            if (f > 0 && f in setOf(3, 5, 7, 9, 12, 15)) {
                paint.color = C.dim.toArgb()
                drawContext.canvas.nativeCanvas.drawText("$f", x - cw / 2, y(0) + rh * 0.85f, paint)
            }
        }
        for (s in muted) { paint.color = C.dim.toArgb(); drawContext.canvas.nativeCanvas.drawText("x", left - 18.dp.toPx(), y(s) + 4.dp.toPx(), paint) }
        for ((s, f, label) in dots) {
            if (f < from || f > to) continue
            val cx = if (f == 0) left - 18.dp.toPx() else left + (f - from - 0.5f) * cw
            val root = rootPc != null && Math.floorMod(tuning.strings[s].midi + f, 12) == rootPc
            drawCircle(if (root) C.acc else C.hi, 10.dp.toPx(), Offset(cx, y(s)))
            paint.color = C.bg.toArgb()
            drawContext.canvas.nativeCanvas.drawText(label, cx, y(s) + 3.5f.dp.toPx(), paint)
        }
    }
}

/** Piano keys around the notes, the page's keys lit and named, the root in copper. */
@Composable
private fun PianoView(model: AppModel, midis: List<Int>, names: Map<Int, PitchName>) {
    val lo = (midis.min() / 12) * 12
    val hi = ((midis.max() / 12) + 1) * 12
    val whites = (lo..hi).filter { it % 12 in setOf(0, 2, 4, 5, 7, 9, 11) }
    val keyW = 26.dp
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Canvas(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).width(keyW * whites.size).height(120.dp)) {
            val w = keyW.toPx()
            val h = size.height
            val paint = android.graphics.Paint().apply { isAntiAlias = true; textSize = 9.dp.toPx(); typeface = android.graphics.Typeface.MONOSPACE; textAlign = android.graphics.Paint.Align.CENTER }
            fun label(m: Int, x: Float, y: Float) {
                if (m !in midis) return
                paint.color = C.bg.toArgb()
                drawContext.canvas.nativeCanvas.drawText(names[m % 12]?.let { refName(model, it) } ?: "", x, y, paint)
            }
            whites.forEachIndexed { i, m ->
                val lit = m in midis
                drawRect(if (m == midis.first()) C.acc else if (lit) C.hi else C.text.copy(alpha = 0.55f), Offset(i * w + 1, 0f), androidx.compose.ui.geometry.Size(w - 2, h))
                label(m, i * w + w / 2, h - 10.dp.toPx())
            }
            whites.forEachIndexed { i, m ->
                val b = m + 1
                if (m % 12 in setOf(0, 2, 5, 7, 9) && b <= hi) {
                    val lit = b in midis
                    val x = (i + 1) * w - w * 0.32f
                    drawRect(if (b == midis.first()) C.acc else if (lit) C.hi else C.bg, Offset(x, 0f), androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.6f))
                    label(b, x + w * 0.32f, h * 0.6f - 8.dp.toPx())
                }
            }
        }
    }
}

/** The notes on a treble staff (bass staff when they sit low): each entry of [events] is one chord or note. */
@Composable
internal fun StaffView(model: AppModel, notes: List<PitchName>, events: List<List<Int>>, key: KeyMode? = null) {
    val byPc = notes.associateBy { it.pitchClass }
    fun spelled(m: Int): SpelledPitch {
        val n = byPc[Math.floorMod(m, 12)] ?: com.betterappmafia.apophenia.core.pitch.PitchClass(m).defaultSpelling()
        return n.at(Math.floorDiv(m - n.letter.natural - n.alter, 12) - 1)
    }
    val all = events.flatten()
    val clef = if (all.average() < 57) Clef.BASS else Clef.TREBLE
    val evs = events.map { e -> Event(if (e.size > 1 && events.size > 1 && e === events.first()) Duration.WHOLE else Duration.QUARTER, e.map { Note(spelled(it)) }) }
    // A whole-note chord fills its own bar; quarters four to a bar, the last bar padded with quarter rests.
    val bars = ArrayList<Bar>()
    var cur = ArrayList<Event>()
    var beats = 0
    for (e in evs) {
        val len = if (e.duration == Duration.WHOLE) 4 else 1
        if (beats + len > 4) { while (beats < 4) { cur += Event(Duration.QUARTER); beats++ }; bars += Bar(listOf(cur)); cur = ArrayList(); beats = 0 }
        cur += e; beats += len
    }
    if (cur.isNotEmpty()) { while (beats < 4) { cur += Event(Duration.QUARTER); beats++ }; bars += Bar(listOf(cur)) }
    val part = Part("", clef, bars = bars)
    NotationCanvas(Score(key = key, parts = listOf(part)), part, null, null, emptySet(), 0, model.chordDialect, model.chordStyle) { _, _, _, _, _ -> }
}

// ------------------------------------------------------------------------------------------------------------- hear

/** ▶ plays the page's sound; VS ▾ plays it, then the one you pick, for an A/B comparison. */
@Composable
private fun Hear(model: AppModel, key: String, notes: List<PlayNote>, what: String, others: List<Pair<String, List<PlayNote>>>) {
    var vs by remember { mutableStateOf(false) }
    Kv("HEAR") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Sq("▶ $what", { hearNotes(model, key, what, notes) })
            Box {
                Sq("▶ VS ▾", { vs = true })
                ConsoleMenu(vs, { vs = false }, "THIS, THEN…", others.map { (name, b) ->
                    MenuItem(name.let(::degreeText)) { hearNotes(model, "$key-vs-${name.hashCode()}", "A/B · $name", RefSound.versus(notes, b)) }
                })
            }
        }
    }
}

/** Renders [notes] on the piano into the reference cache once, then plays them in the player. */
internal fun hearNotes(model: AppModel, key: String, title: String, notes: List<PlayNote>) {
    model.app.io.launch {
        val f = renderReference(model, "ref-${key.replace(Regex("[^A-Za-z0-9_#-]"), "_")}-v1.wav") {
            Piano.render(model.app, notes, model.app.audio.sampleRate, RefSound.length(notes))
        }
        model.app.audio.load(REFERENCE_SCRAP, -1, title, f.absolutePath, play = true)
    }
}
