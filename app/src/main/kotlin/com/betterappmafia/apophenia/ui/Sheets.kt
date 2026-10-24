package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.catalog.Scales
import com.betterappmafia.apophenia.data.TagEntity
import com.betterappmafia.apophenia.data.ValueSource
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import kotlinx.coroutines.launch

@Composable
fun SheetHost(model: AppModel) {
    when (val s = model.sheet) {
        Sheet.None -> {}
        Sheet.Filter -> FilterSheet(model)
        is Sheet.Save -> SaveSheet(model, s.scrapId, s.letter)
        is Sheet.Tags -> SimpleSheet(model, "Tags", "YOUR OWN WORDS") { TagPicker(model, s.scrapId) }
        is Sheet.Bpm -> BpmSheet(model, s.scrapId)
        is Sheet.Key -> KeySheet(model, s.scrapId)
        is Sheet.Rename -> RenameSheet(model, s.scrapId)
        is Sheet.Restore -> RestoreSheet(model, s.ok)
        is Sheet.BulkTags -> SimpleSheet(model, "Tag ${s.ids.size}", "ON ALL OF THEM") { BulkTagPicker(model, s.ids) }
        is Sheet.Groups -> SimpleSheet(model, if (s.ids.size == 1) "Groups" else "Groups · ${s.ids.size}", "A SCRAP CAN BE IN MANY") { GroupPicker(model, s.ids) }
        is Sheet.ProjectName -> ProjectNameSheet(model, s.projectId)
        Sheet.StackTempoKey -> StackTempoKeySheet(model)
        is Sheet.Confirm -> Box(Modifier.fillMaxSize()) {
            Sheet({ model.sheet = Sheet.None }) {
                TitleBar { Text(s.title.uppercase(), style = T.title, modifier = Modifier.weight(1f)) }
                Text(s.text, style = T.body.copy(lineHeight = 20.sp), modifier = Modifier.padding(14.dp))
                Row {
                    BigButton("CANCEL", { model.sheet = Sheet.None })
                    BigButton(s.yes, { model.sheet = Sheet.None; s.onYes() }, primary = true)
                }
            }
        }
        is Sheet.Projects -> SimpleSheet(model, if (s.ids.size == 1) "Projects" else "Projects · ${s.ids.size}", "ON A BOARD · STILL IN THE LIBRARY") { ProjectPicker(model, s.ids) }
    }
}

/** New project, or rename one. A project needs a name; there is no fake default project (plan §2.3). */
@Composable
private fun ProjectNameSheet(model: AppModel, projectId: Long?) {
    val old = model.projectName(projectId)
    var text by remember(projectId) { mutableStateOf(old.orEmpty()) }
    val ok = text.isNotBlank()
    val done = {
        if (ok) { if (projectId == null) model.newProject(text) else model.renameProject(projectId, text) }
        model.sheet = Sheet.None
    }
    Box(Modifier.fillMaxSize()) {
        Sheet({ model.sheet = Sheet.None }) {
            TitleBar { Text(if (projectId == null) "NEW PROJECT" else "RENAME", style = T.title, modifier = Modifier.weight(1f)); Text("ONE SONG'S WORKING SPACE", style = T.label) }
            ConsoleField("NAME", text, { text = it.replace(Regex("[\\u0000-\\u001F]"), "").take(80) }, placeholder = "Low Tide", onDone = done)
            Row {
                BigButton("CANCEL", { model.sheet = Sheet.None })
                BigButton(if (projectId == null) "MAKE IT" else "RENAME", done, primary = true, enabled = ok)
            }
        }
    }
}

/**
 * Add to project (plan §3.3, §4.2): tap a project to put the scraps on its Board, as references
 * only; tap one they're all on to take them off it. A scrap can be on any number of Boards.
 */
@Composable
private fun ProjectPicker(model: AppModel, ids: Set<Long>) {
    val projects by model.projects.collectAsState()
    val uses by remember(ids) { model.app.db.projects().usesOf(ids.toList()) }.collectAsState(emptyList())
    var name by remember { mutableStateOf("") }
    fun create() {
        val n = name.trim()
        if (n.isEmpty()) return
        name = ""
        model.app.io.launch {
            val now = System.currentTimeMillis()
            val p = model.app.db.projects().insert(com.betterappmafia.apophenia.data.ProjectEntity(name = n, createdAt = now, editedAt = now))
            model.addToProject(p, ids, nameHint = n)
        }
    }
    Column {
        ConsoleField("NEW", name, { name = it.replace(Regex("[\\u0000-\\u001F]"), "").take(80) }, placeholder = "a new project for ${if (ids.size == 1) "it" else "these ${ids.size}"}", onDone = ::create)
        if (name.isNotBlank()) Row(Modifier.padding(12.dp)) { Chip("+ MAKE \"${name.trim()}\" WITH ${if (ids.size == 1) "IT" else "THESE ${ids.size}"}", true, ::create) }
        if (projects.isEmpty()) Text("No projects yet.", style = T.small, modifier = Modifier.padding(12.dp))
        projects.forEach { p ->
            val on = uses.count { it.projectId == p.project.id }
            val state = when (on) { 0 -> ""; ids.size -> "✓ ON ITS BOARD"; else -> "$on OF ${ids.size} ON IT" }
            Kv(p.project.name.uppercase(), onClick = {
                if (on == ids.size) model.takeOffProject(p.project.id, ids) else model.addToProject(p.project.id, ids)
            }) { Text("${p.scraps} on its Board  $state", style = T.body.copy(color = if (on > 0) C.acc else C.text)) }
        }
    }
}

@Composable
private fun SimpleSheet(model: AppModel, title: String, note: String, done: () -> Unit = { model.sheet = Sheet.None }, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Sheet(done) {
            TitleBar { Text(title.uppercase(), style = T.title, modifier = Modifier.weight(1f)); Text(note, style = T.label) }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { content() }
            Row { BigButton("DONE", done, primary = true) }
        }
    }
}

/**
 * After ● STOP (plan §10.1): the take is already saved; everything here is optional and saved as
 * you go. Closing the sheet any way keeps it all.
 */
@Composable
private fun SaveSheet(model: AppModel, scrapId: Long, letter: String) {
    val db = model.app.db
    val row by remember(scrapId) { db.scraps().row(scrapId) }.collectAsState(null)
    var title by remember(scrapId) { mutableStateOf<String?>(null) }
    LaunchedEffect(row?.scrap?.id) { if (title == null && row != null) title = row?.scrap?.title.orEmpty() }
    DisposableEffect(scrapId) {
        onDispose {
            val t = title ?: return@onDispose
            if (t.trim() != row?.scrap?.title.orEmpty()) model.app.io.launch { db.scraps().rename(scrapId, t.trim().ifEmpty { null }, System.currentTimeMillis()) }
        }
    }
    val s = row?.scrap
    SimpleSheet(model, "Take $letter · saved", "ALL OPTIONAL") {
        ConsoleField("TITLE", title.orEmpty(), { title = it }, placeholder = s?.let { displayTitle(it.copy(title = null)) } ?: "")
        if (s != null) {
            Kv("BPM", onClick = { model.sheet = Sheet.Bpm(scrapId) }) { Text("${bpmText(s).let { if (it == "—") "unknown" else it }}  ${sourceLabel(s.bpmSource)}", style = T.body.copy(color = C.hi)) }
            Kv("KEY", onClick = { model.sheet = Sheet.Key(scrapId) }) { Text("${KeyMode.parseOrNull(s.key)?.display ?: "unknown"}  ${sourceLabel(s.keySource)}", style = T.body.copy(color = C.hi)) }
            val pid = s.projectId
            if (pid == null) KvText("GOES TO", "Library")
            else {
                // Plan §10.1: a project recording also shows in Library search unless switched off here.
                val pname = model.projectName(pid) ?: "the project"
                Kv("GOES TO", onClick = { model.app.io.launch { db.scraps().setShowInLibrary(scrapId, !s.showInLibrary) } }) {
                    Text(if (s.showInLibrary) "$pname + Library" else "$pname only", style = T.body.copy(color = C.hi))
                    Text(if (s.showInLibrary) "TAP: KEEP IT OUT OF LIBRARY SEARCH" else "TAP: SHOW IT IN LIBRARY SEARCH TOO", style = T.label)
                }
            }
        }
        Text("TAGS", style = T.label, modifier = Modifier.padding(start = 12.dp, top = 10.dp))
        TagPicker(model, scrapId)
    }
}

/** Pick existing tags (most used first, narrowed as you type) or make a new one; tap a tag on the scrap to take it off. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPicker(model: AppModel, scrapId: Long) {
    val db = model.app.db
    val row by remember(scrapId) { db.scraps().row(scrapId) }.collectAsState(null)
    var text by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<TagEntity>>(emptyList()) }
    val on = row?.tagList.orEmpty()
    LaunchedEffect(text, on) { suggestions = db.tags().pickList(text.trim()).filter { t -> on.none { it.equals(t.name, true) } }.take(24) }
    fun add(name: String) {
        if (name.isBlank()) return
        model.app.io.launch { db.tags().tag(scrapId, name, System.currentTimeMillis()); db.scraps().touch(scrapId, System.currentTimeMillis()) }
        text = ""
    }
    Column {
        if (on.isNotEmpty()) FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            on.forEach { t ->
                Chip("$t  ✕", true, {
                    model.app.io.launch { db.tags().named(t)?.let { db.tags().untag(scrapId, it.id) } }
                })
            }
        }
        ConsoleField("ADD", text, { text = it.replace(Regex("[\\u0000-\\u001F]"), "") }, placeholder = "type a tag, or pick one below", onDone = { add(text) })
        FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (text.isNotBlank() && suggestions.none { it.name.equals(text.trim(), true) }) Chip("+ NEW \"${text.trim()}\"", true, { add(text) })
            suggestions.forEach { t -> Chip(t.name, false, { add(t.name) }) }
            if (suggestions.isEmpty() && text.isBlank() && on.isEmpty()) Text("No tags yet. Type one: \"bass\", \"lyrics\", \"love song\".", style = T.small)
        }
    }
}

@Composable
private fun BpmSheet(model: AppModel, scrapId: Long) {
    val db = model.app.db
    val row by remember(scrapId) { db.scraps().row(scrapId) }.collectAsState(null)
    var text by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val s = row?.scrap
    LaunchedEffect(s?.id) { if (text == null && s != null) text = if (s.bpmLow == null || s.freeTime) "" else bpmText(s) }
    fun write(low: Double?, high: Double?, free: Boolean) {
        model.app.io.launch {
            db.scraps().offerBpm(scrapId, low, high, free, ValueSource.USER)
            db.scraps().touch(scrapId, System.currentTimeMillis())
        }
        model.sheet = Sheet.None
    }
    SimpleSheet(model, "BPM", "YOURS ALWAYS WINS", done = {
        val t = text.orEmpty().trim()
        if (t.isEmpty()) { model.sheet = Sheet.None; return@SimpleSheet }
        val parts = t.split('-', '–').map { it.trim().toDoubleOrNull() }
        when {
            parts.any { it == null || it <= 0 || it > 999 } -> error = "Type a number like 92, or a range like 88-96."
            parts.size == 1 -> write(parts[0], parts[0], false)
            parts.size == 2 && parts[1]!! >= parts[0]!! -> write(parts[0], parts[1], false)
            else -> error = "A range goes low to high: 88-96."
        }
    }) {
        ConsoleField("BPM", text.orEmpty(), { text = it.filter { c -> c.isDigit() || c == '.' || c == '-' }.take(9); error = null }, placeholder = "92 or 88-96", number = true)
        error?.let { Text(it, style = T.small.copy(color = C.rec), modifier = Modifier.padding(12.dp)) }
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("FREE TIME", s?.freeTime == true, { write(null, null, true) })
            Chip("UNKNOWN", s != null && s.bpmLow == null && !s.freeTime, { write(null, null, false) })
        }
        if (s?.bpmSource == ValueSource.METRONOME) Text("Now from the click while it recorded. Your entry replaces it.", style = T.small, modifier = Modifier.padding(horizontal = 12.dp))
    }
}

private val MAJOR_MODES = listOf("major", "aeolian", "dorian", "phrygian", "lydian", "mixolydian", "locrian")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeySheet(model: AppModel, scrapId: Long) {
    val db = model.app.db
    val row by remember(scrapId) { db.scraps().row(scrapId) }.collectAsState(null)
    val current = KeyMode.parseOrNull(row?.scrap?.key)
    var tonic by remember(current) { mutableStateOf(current?.tonic) }
    var mode by remember(current) { mutableStateOf(current?.scaleId ?: "major") }
    var more by remember { mutableStateOf(current != null && current.scaleId !in MAJOR_MODES) }
    fun save(k: KeyMode?) {
        model.app.io.launch { db.scraps().offerKey(scrapId, k?.token, ValueSource.USER); db.scraps().touch(scrapId, System.currentTimeMillis()) }
    }
    SimpleSheet(model, "Key and mode", tonic?.let { KeyMode(it, mode).display.uppercase() } ?: "UNKNOWN", done = {
        tonic?.let { save(KeyMode(it, mode)) }
        model.sheet = Sheet.None
    }) {
        Text("HOME NOTE", style = T.label, modifier = Modifier.padding(start = 12.dp, top = 10.dp))
        FlowRow(Modifier.padding(12.dp), maxItemsInEachRow = 7, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // natural row, flat row, sharp row
            for (alter in listOf(0, -1, 1)) for (p in KeyMode.TONICS.filter { it.alter == alter }) {
                Chip(KeyMode.displayName(p), tonic == p, { tonic = p })
            }
        }
        Text("MODE", style = T.label, modifier = Modifier.padding(start = 12.dp))
        FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val ids = if (more) Scales.all.map { it.id } else MAJOR_MODES
            ids.forEach { id -> Chip(KeyMode.modeName(id), mode == id, { mode = id }) }
            Chip(if (more) "FEWER" else "ALL 50 SCALES ›", false, { more = !more })
        }
        Row(Modifier.padding(12.dp)) {
            Chip("KEY UNKNOWN", current == null && row?.scrap?.keySource == ValueSource.USER, { save(null); model.sheet = Sheet.None })
        }
        Text("Spelled as you pick it: F♯ and G♭ are different keys here.", style = T.small, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
    }
}

@Composable
private fun RenameSheet(model: AppModel, scrapId: Long) {
    val db = model.app.db
    val row by remember(scrapId) { db.scraps().row(scrapId) }.collectAsState(null)
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(row?.scrap?.id) { if (text == null && row != null) text = row?.scrap?.title.orEmpty() }
    val done = {
        val t = text?.trim()
        if (t != null) model.app.io.launch { db.scraps().rename(scrapId, t.ifEmpty { null }, System.currentTimeMillis()) }
        model.sheet = Sheet.None
    }
    SimpleSheet(model, "Title", "EMPTY = UNTITLED", done = done) {
        ConsoleField("TITLE", text.orEmpty(), { text = it }, placeholder = "Verse riff", onDone = done)
    }
}

/** Tags for several scraps: tags all of them share can be removed; any tag picked goes on all of them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BulkTagPicker(model: AppModel, ids: Set<Long>) {
    val db = model.app.db
    val rows by remember(ids) { db.scraps().rows(ids.toList()) }.collectAsState(emptyList())
    val shared = rows.map { r -> r.tagList.map { it.lowercase() }.toSet() }.reduceOrNull { a, b -> a intersect b }.orEmpty()
    val sharedNames = rows.firstOrNull()?.tagList?.filter { it.lowercase() in shared }.orEmpty()
    var text by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<TagEntity>>(emptyList()) }
    LaunchedEffect(text, shared) { suggestions = db.tags().pickList(text.trim()).filter { it.name.lowercase() !in shared }.take(24) }
    fun add(name: String) {
        if (name.isBlank()) return
        model.app.io.launch { val now = System.currentTimeMillis(); ids.forEach { db.tags().tag(it, name, now); db.scraps().touch(it, now) } }
        text = ""
    }
    Column {
        if (sharedNames.isNotEmpty()) {
            Text("ON ALL ${rows.size}", style = T.label, modifier = Modifier.padding(start = 12.dp, top = 10.dp))
            FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sharedNames.forEach { t -> Chip("$t  ✕", true, { model.app.io.launch { db.tags().named(t)?.let { tag -> ids.forEach { db.tags().untag(it, tag.id) } } } }) }
            }
        }
        ConsoleField("ADD", text, { text = it.replace(Regex("[\\u0000-\\u001F]"), "") }, placeholder = "type a tag, or pick one below", onDone = { add(text) })
        FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (text.isNotBlank() && suggestions.none { it.name.equals(text.trim(), true) }) Chip("+ NEW \"${text.trim()}\"", true, { add(text) })
            suggestions.forEach { t -> Chip(t.name, false, { add(t.name) }) }
        }
    }
}

/**
 * Groups (plan §3.5): user-named collections. Tap a Group to put the scraps in it or take them out;
 * deleting a Group never deletes its scraps.
 */
@Composable
private fun GroupPicker(model: AppModel, ids: Set<Long>) {
    val db = model.app.db
    val groups by model.groups.collectAsState()
    val rows by remember(ids) { db.scraps().rows(ids.toList()) }.collectAsState(emptyList())
    var name by remember { mutableStateOf("") }
    fun create() {
        val n = name.trim()
        if (n.isEmpty()) return
        name = ""
        model.app.io.launch {
            val now = System.currentTimeMillis()
            val g = db.groups().insert(com.betterappmafia.apophenia.data.GroupEntity(name = n, createdAt = now))
            ids.forEach { db.groups().addMember(com.betterappmafia.apophenia.data.GroupMemberEntity(g, it, now)) }
        }
    }
    Column {
        ConsoleField("NEW", name, { name = it }, placeholder = "Love songs, Dark riffs…", onDone = ::create)
        if (name.isNotBlank()) Row(Modifier.padding(12.dp)) { Chip("+ MAKE \"${name.trim()}\" WITH ${if (ids.size == 1) "IT" else "THESE ${ids.size}"}", true, ::create) }
        if (groups.isEmpty()) Text("No Groups yet.", style = T.small, modifier = Modifier.padding(12.dp))
        groups.forEach { g ->
            val inIt = rows.count { g.group.id in it.groupSet }
            val state = when (inIt) { 0 -> ""; rows.size -> "✓ IN"; else -> "$inIt OF ${rows.size} IN" }
            Row(Modifier.fillMaxWidth().underline(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Kv(g.group.name.uppercase(), Modifier.weight(1f), onClick = {
                    model.app.io.launch {
                        val now = System.currentTimeMillis()
                        if (inIt == rows.size) ids.forEach { db.groups().removeMember(g.group.id, it) }
                        else ids.forEach { db.groups().addMember(com.betterappmafia.apophenia.data.GroupMemberEntity(g.group.id, it, now)) }
                    }
                }) { Text("${g.members} in it  ${state}", style = T.body.copy(color = if (inIt > 0) C.acc else C.text)) }
                Sq("✕", {
                    model.app.io.launch { db.groups().moveToTrash(g.group.id, System.currentTimeMillis()) }
                    model.notice("Group \"${g.group.name}\" deleted. Its scraps stay.") { model.app.io.launch { db.groups().restore(g.group.id) } }
                }, modifier = Modifier.padding(end = 8.dp))
            }
        }
    }
}

/** Last check before a restore replaces what's on the phone. */
@Composable
private fun RestoreSheet(model: AppModel, ok: com.betterappmafia.apophenia.backup.BackupArchive.Result.Ok) {
    val now by model.library.collectAsState()
    val sum = ok.summary
    val date = java.text.SimpleDateFormat("d MMM yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(sum.created))
    Box(Modifier.fillMaxSize()) {
        Sheet({ model.sheet = Sheet.None }) {
            TitleBar { Text("RESTORE", style = T.title, modifier = Modifier.weight(1f)); Text("CHECKED · NOTHING CHANGED YET", style = T.label) }
            KvText("BACKUP", "$date · ${sum.audioCount} recordings · %.1f MB · app ${sum.appVersion}".format(sum.audioBytes / 1e6))
            KvText("REPLACES", "Everything on this phone now: ${now.size} Library items and their takes")
            KvText("KEPT ASIDE", "The current data moves to files/before-restore until the next restore")
            Text("Apophenia closes after restoring; open it again.", style = T.small, modifier = Modifier.padding(12.dp))
            Row {
                BigButton("CANCEL", { model.sheet = Sheet.None })
                BigButton("RESTORE", { model.confirmRestore(ok) }, primary = true)
            }
        }
    }
}


/**
 * The stack's tempo and key (plan §5.3). Left empty they follow the first layer that has them;
 * loop and one-shot layers conform to what's here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StackTempoKeySheet(model: AppModel) {
    val sm = model.stacks
    val s = sm.loaded
    var text by remember { mutableStateOf(s?.bpm?.let { "%.1f".format(it).removeSuffix(".0") } ?: "") }
    val current = KeyMode.parseOrNull(s?.key)
    var tonic by remember { mutableStateOf(current?.tonic) }
    var mode by remember { mutableStateOf(current?.scaleId ?: "major") }
    val fromLayers = "from the first layer: " + listOfNotNull(
        sm.layerTempo()?.let { "%.0f BPM".format(it) }, sm.layerKey()?.display,
    ).joinToString(" · ").ifEmpty { "none" }
    SimpleSheet(model, "Stack tempo and key", "LAYERS CONFORM TO THESE", done = {
        val bpm = text.trim().toDoubleOrNull()?.takeIf { it in 20.0..400.0 }
        sm.setTempoKey(bpm, tonic?.let { KeyMode(it, mode) })
        model.sheet = Sheet.None
    }) {
        ConsoleField("BPM", text, { text = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, placeholder = "empty = $fromLayers", number = true)
        Text("HOME NOTE", style = T.label, modifier = Modifier.padding(start = 12.dp, top = 10.dp))
        FlowRow(Modifier.padding(12.dp), maxItemsInEachRow = 7, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (alter in listOf(0, -1, 1)) for (p in KeyMode.TONICS.filter { it.alter == alter }) Chip(KeyMode.displayName(p), tonic == p, { tonic = p })
        }
        FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MAJOR_MODES.forEach { id -> Chip(KeyMode.modeName(id), mode == id, { mode = id }) }
            Chip("FROM THE LAYERS", tonic == null, { tonic = null })
        }
        Text("Empty BPM or FROM THE LAYERS: $fromLayers. Free-time layers are never conformed; a layer with no key isn't shifted.", style = T.small.copy(lineHeight = 17.sp), modifier = Modifier.padding(12.dp))
    }
}
