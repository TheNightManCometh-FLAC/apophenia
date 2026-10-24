package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.data.LibraryFilter
import com.betterappmafia.apophenia.data.LibraryQuery
import com.betterappmafia.apophenia.data.LibraryRow
import com.betterappmafia.apophenia.data.LibrarySort
import com.betterappmafia.apophenia.data.ScrapEntity
import com.betterappmafia.apophenia.data.ScrapFormat
import com.betterappmafia.apophenia.data.TRASH_DAYS
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val SORTS = listOf(
    LibrarySort.CAPTURED to "Recently captured", LibrarySort.EDITED to "Recently edited",
    LibrarySort.NAME to "Name", LibrarySort.TEMPO to "Tempo",
)

@Composable
fun LibraryScreen(model: AppModel) {
    val all by model.library.collectAsState()
    val trash by model.trash.collectAsState()
    val rows = remember(all, model.search, model.filter, model.sort) { LibraryQuery.apply(all, model.search, model.filter, model.sort) }
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        if (model.selecting) SelectionBar(model, rows) else TitleBar {
            Text("LIBRARY", style = T.title, modifier = Modifier.weight(1f))
            val n = model.filter.activeCount
            Sq(if (n > 0) "FILTER · $n ▾" else "FILTER ▾", { model.sheet = Sheet.Filter }, on = n > 0)
            Box {
                Sq("SORT ▾", { sortMenu = true })
                ConsoleMenu(sortMenu, { sortMenu = false }, "SORT", SORTS.map { (s, label) ->
                    MenuItem(label, if (model.sort == s) "✓" else if (s == LibrarySort.TEMPO) "NONE LAST" else "") { model.sort = s }
                })
            }
            Box {
                Sq("⋯", { moreMenu = true }, modifier = Modifier.semantics { contentDescription = "Library menu" })
                val last by produceState<Long?>(null, moreMenu) { value = model.app.backups.lastBackup() }
                ConsoleMenu(moreMenu, { moreMenu = false }, "LIBRARY", listOf(
                    MenuItem("Select", "TAG · GROUP · DELETE SEVERAL", enabled = rows.isNotEmpty()) { model.selecting = true },
                    MenuItem("Trash", "${trash.size} · KEPT $TRASH_DAYS DAYS ›") { model.showTrash = true },
                ) + WRITABLE.map { (f, label, _) -> MenuItem("Write ${label.lowercase()}", formatTag(f)) { model.write(f) } } + listOf(
                    MenuItem("Write a guitar tab", "TAB") { model.writeTab(false) }, MenuItem("Write a bass tab", "TAB") { model.writeTab(true) }, MenuItem("Write sheet music", "NOT") { model.writeSheet() },
                    MenuItem("Import from the phone", "AUDIO · TEXT · CHORDPRO ›", enabled = model.importing == 0) { model.startImport() },
                    MenuItem("Back up now", last?.let { "LAST " + SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(it)) } ?: "PICK A FOLDER ›", enabled = !model.backupBusy) { model.backupNow() },
                    MenuItem("Backup folder", "CHANGE ›") { model.pickBackupFolder() },
                    MenuItem("Restore from a backup", "PICK THE FILE ›", enabled = !model.backupBusy) { model.pickBackupFile() },
                    MenuItem("Licences and privacy", "WHAT THE APP IS MADE WITH ›") { model.showNotices = true },
                ))
            }
        }
        ConsoleField("FIND", model.search, { model.search = it }, placeholder = "title or tag")
        if (all.isEmpty()) {
            Text(
                "Nothing here yet. ● REC at the top records straight into the Library; the take is saved the moment you stop.",
                style = T.body.copy(lineHeight = 20.sp), modifier = Modifier.padding(14.dp),
            )
        } else if (rows.isEmpty()) {
            Text("Nothing matches. ${if (model.filter.activeCount > 0) "Filters are on." else ""}", style = T.body, modifier = Modifier.padding(14.dp))
        }
        LazyColumn(Modifier.weight(1f)) {
            items(rows, key = { it.scrap.id }) { row -> LibraryRowView(model, row) }
        }
    }
}

/** While selecting: how many, the actions, select all of what's shown, done. */
@Composable
private fun SelectionBar(model: AppModel, shown: List<LibraryRow>) {
    var menu by remember { mutableStateOf(false) }
    val n = model.selected.size
    val adding = model.addingTo
    if (adding != null) {
        // Started from Board ⋯ → Add from the Library: one job, one button.
        val name = model.projectName(adding) ?: ""
        TitleBar {
            Column(Modifier.weight(1f)) {
                Text("ADD TO ${name.uppercase()}", style = T.title, maxLines = 1)
                Text(if (n == 0) "TAP WHAT TO PUT ON ITS BOARD" else "$n PICKED", style = T.label)
            }
            Sq("CANCEL", { model.endSelect() })
            Sq(if (n == 0) "ADD" else "ADD $n", {
                val ids = model.selected
                model.endSelect()
                model.addToProject(adding, ids)
                model.go(Mode.BOARD)
            }, on = n > 0, enabled = n > 0)
        }
        return
    }
    if (model.addingToStack != null) {
        // Started from the stack's "Add a layer": recordings only (the filter shows them), one button.
        TitleBar {
            Column(Modifier.weight(1f)) {
                Text("ADD TO ${model.stacks.title().uppercase()}", style = T.title, maxLines = 1)
                Text(if (n == 0) "TAP RECORDINGS TO LAYER · FILTER BY KEY OR TEMPO" else "$n PICKED", style = T.label, maxLines = 1)
            }
            Sq("CANCEL", { model.endSelect(); model.showStack = true })
            Sq(if (n == 0) "ADD" else "ADD $n", {
                val ids = model.selected
                model.endSelect()
                model.stacks.addPicked(ids)
                model.showStack = true
            }, on = n > 0, enabled = n > 0)
        }
        return
    }
    TitleBar {
        Text("$n SELECTED", style = T.title, modifier = Modifier.weight(1f))
        Box {
            Sq("ACTIONS ▾", { menu = true }, on = n > 0, enabled = n > 0)
            ConsoleMenu(menu, { menu = false }, "$n SELECTED", listOf(
                MenuItem("Tag them", "ADD OR REMOVE ›") { model.sheet = Sheet.BulkTags(model.selected) },
                MenuItem("Groups", "ADD TO · TAKE OUT OF ›") { model.sheet = Sheet.Groups(model.selected) },
                MenuItem("Add to project", "ON A BOARD ›") { model.sheet = Sheet.Projects(model.selected) },
                MenuItem("Add to the player", "AS STACK LAYERS") {
                    val ids = model.selected
                    model.endSelect()
                    // Into the stack in the player; else the open project's stack; else the temporary stack.
                    val loaded = model.stacks.loaded
                    model.stacks.add(if (loaded != null) loaded.projectId else model.projectId, ids)
                },
                MenuItem("Delete", "$n TO THE TRASH") { model.trashSelected() },
            ))
        }
        Sq("ALL", { model.selected = shown.map { it.scrap.id }.toSet() })
        Sq("DONE", { model.endSelect() })
    }
}

/** A title, or for written scraps their first words, or "Untitled · date". */
fun displayTitle(s: ScrapEntity): String = s.title?.takeIf { it.isNotBlank() }
    ?: when (s.format) {
        ScrapFormat.LYRIC -> s.body?.let { com.betterappmafia.apophenia.core.sheet.LyricSheet.firstWords(it) }
        ScrapFormat.TEXT -> s.body?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }
        else -> null
    }?.take(60)
    ?: "Untitled · " + SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(s.createdAt))

fun bpmText(s: ScrapEntity): String {
    val low = s.bpmLow
    val high = s.bpmHigh
    return when {
        s.freeTime -> "FREE"
        low == null -> "—"
        high != null && high != low -> "${low.toInt()}–${high.toInt()}"
        else -> low.toInt().toString()
    }
}

fun keyShort(s: ScrapEntity): String = KeyMode.parseOrNull(s.key)?.short ?: "—"

fun formatTag(f: ScrapFormat) = when (f) {
    ScrapFormat.RECORDING -> "REC"; ScrapFormat.LYRIC -> "LYR"; ScrapFormat.TEXT -> "TXT"; ScrapFormat.CHORD_CHART -> "CHD"
    ScrapFormat.TAB -> "TAB"; ScrapFormat.MIDI -> "MID"; ScrapFormat.SHEET -> "NOT"; ScrapFormat.DRUM_CHART -> "DRM"
}

@Composable
private fun LibraryRowView(model: AppModel, row: LibraryRow) {
    val s = row.scrap
    val picked = s.id in model.selected
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).underline()
            .background(if (picked) C.selected else Color.Transparent)
            .combinedClickable(
                role = Role.Button,
                onLongClick = { model.toggleSelect(s.id) },
                onLongClickLabel = "Select",
            ) { if (model.selecting) model.toggleSelect(s.id) else model.open(s.id) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (picked) "✓" else formatTag(s.format), style = T.label.copy(color = if (picked) C.acc else C.text, fontWeight = FontWeight.ExtraBold, fontSize = if (picked) 14.sp else 9.sp), modifier = Modifier.width(44.dp).padding(start = 12.dp))
        Column(Modifier.weight(1f).padding(vertical = 10.dp, horizontal = 6.dp)) {
            Line1(displayTitle(s), T.hi)
            val extras = buildList {
                if (row.takes > 1) add("${row.takes} TAKES")
                if (row.starred > 0) add("★ ${row.starred}")
                if (row.projectUses > 0) add("in " + row.projectList.joinToString(", "))
            }
            Line1((row.tagList + extras).joinToString(" · ").ifEmpty { "no tags" }, T.small.copy(fontSize = 10.sp), Modifier.padding(top = 3.dp))
        }
        Text(bpmText(s), style = T.body.copy(fontWeight = FontWeight.Bold), modifier = Modifier.width(52.dp))
        Text(keyShort(s), style = T.body.copy(fontWeight = FontWeight.Bold), modifier = Modifier.width(64.dp))
        if (s.format == ScrapFormat.RECORDING && !model.selecting) {
            Box(
                Modifier.width(48.dp).heightIn(min = 60.dp).fillMaxHeight()
                    .clickable(role = Role.Button) {
                        model.app.io.launch {
                            val takes = model.app.db.takes().takesOf(s.id)
                            val take = takes.lastOrNull { it.starred } ?: takes.lastOrNull() ?: return@launch
                            model.play(s.id, take, displayTitle(s))
                        }
                    }
                    .semantics { contentDescription = "Play ${displayTitle(s)}" },
                contentAlignment = Alignment.Center,
            ) { Text("▶", style = T.hi.copy(color = C.acc)) }
        }
    }
}

@Composable
fun TrashScreen(model: AppModel) {
    val rows by model.trash.collectAsState()
    val projects by model.trashedProjects.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TitleBar {
            Sq("‹", { model.showTrash = false }, modifier = Modifier.semantics { contentDescription = "Back to the Library" })
            Text("TRASH", style = T.title, modifier = Modifier.weight(1f))
            Text("EMPTIED AFTER $TRASH_DAYS DAYS", style = T.label)
        }
        if (rows.isEmpty() && projects.isEmpty()) Text("The trash is empty.", style = T.body, modifier = Modifier.padding(14.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(projects, key = { "p" + it.id }) { p ->
                val days = ((System.currentTimeMillis() - (p.deletedAt ?: 0)) / 86_400_000L).toInt()
                Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).underline().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("PRJ", style = T.label.copy(fontWeight = FontWeight.ExtraBold, fontSize = 9.sp), modifier = Modifier.width(32.dp))
                    Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        Line1(p.name, T.hi)
                        Text("project · its Board comes back with it · gone in ${(TRASH_DAYS - days).coerceAtLeast(0)} d", style = T.small)
                    }
                    Sq("RESTORE", { model.restoreProject(p.id) }, modifier = Modifier.padding(end = 12.dp))
                }
            }
            items(rows, key = { it.scrap.id }) { row ->
                val s = row.scrap
                val days = ((System.currentTimeMillis() - (s.deletedAt ?: 0)) / 86_400_000L).toInt()
                Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).underline().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        Line1(displayTitle(s), T.hi)
                        Text("deleted ${if (days == 0) "today" else "$days d ago"} · gone in ${(TRASH_DAYS - days).coerceAtLeast(0)} d", style = T.small)
                    }
                    Sq("RESTORE", { model.app.io.launch { model.app.db.scraps().restore(s.id) } }, modifier = Modifier.padding(end = 12.dp))
                }
            }
        }
    }
}

/** Filter sheet (plan §3.6). Tags cycle off → + include → − exclude. Everything applies live; SHOW closes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(model: AppModel) {
    val all by model.library.collectAsState()
    val f = model.filter
    val count = LibraryQuery.apply(all, model.search, f, model.sort).size
    val tags = remember(all) { all.flatMap { it.tagList }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() } }
    val keys = remember(all) { all.mapNotNull { it.scrap.key }.distinct().sorted() }
    val formats = remember(all) { all.map { it.scrap.format }.distinct() }
    val groups by model.groups.collectAsState()
    var bpmMin by remember { mutableStateOf(f.bpmMin?.toInt()?.toString() ?: "") }
    var bpmMax by remember { mutableStateOf(f.bpmMax?.toInt()?.toString() ?: "") }
    fun set(nf: LibraryFilter) { model.filter = nf }

    Box(Modifier.fillMaxSize()) {
        Sheet({ model.sheet = Sheet.None }) {
            TitleBar { Text("FILTER", style = T.title, modifier = Modifier.weight(1f)); Text("$count MATCH", style = T.label) }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                if (formats.size > 1) Section("TYPE") {
                    formats.forEach { fm -> Chip(formatTag(fm), fm in f.formats, { set(f.copy(formats = f.formats.toggle(fm))) }) }
                }
                Section("TAGS") {
                    if (tags.isEmpty()) Text("No tags yet.", style = T.small)
                    tags.forEach { t ->
                        val inc = f.includeTags.any { it.equals(t, true) }
                        val exc = f.excludeTags.any { it.equals(t, true) }
                        Chip(when { inc -> "+ $t"; exc -> "− $t"; else -> t }, inc || exc, {
                            set(when {
                                inc -> f.copy(includeTags = f.includeTags.filterNot { it.equals(t, true) }.toSet(), excludeTags = f.excludeTags + t)
                                exc -> f.copy(excludeTags = f.excludeTags.filterNot { it.equals(t, true) }.toSet())
                                else -> f.copy(includeTags = f.includeTags + t)
                            })
                        }, color = if (exc) C.rec else if (inc) C.hi else C.text)
                    }
                    if (f.includeTags.size > 1) Chip(if (f.allIncludeTags) "+ ALL OF THEM" else "+ ANY OF THEM", true, { set(f.copy(allIncludeTags = !f.allIncludeTags)) })
                }
                Section("KEY") {
                    if (keys.isEmpty()) Text("No keys entered yet.", style = T.small)
                    keys.forEach { k -> Chip(KeyMode.parseOrNull(k)?.display ?: k, k in f.keys, { set(f.copy(keys = f.keys.toggle(k))) }) }
                    if (f.keys.isNotEmpty()) Chip("+ UNKNOWN KEY", f.keepUnknownKey, { set(f.copy(keepUnknownKey = !f.keepUnknownKey)) })
                }
                Section("BPM") {
                    Row(Modifier.fillMaxWidth()) {
                        ConsoleField("FROM", bpmMin, { bpmMin = it.filter(Char::isDigit).take(3); set(f.copy(bpmMin = bpmMin.toDoubleOrNull())) }, Modifier.weight(1f), number = true)
                        ConsoleField("TO", bpmMax, { bpmMax = it.filter(Char::isDigit).take(3); set(f.copy(bpmMax = bpmMax.toDoubleOrNull())) }, Modifier.weight(1f), number = true)
                    }
                    Chip("½ AND 2×", f.halfDouble, { set(f.copy(halfDouble = !f.halfDouble)) })
                    Chip("+ FREE TIME", f.keepFreeTime, { set(f.copy(keepFreeTime = !f.keepFreeTime)) })
                }
                Section("GROUP") {
                    if (groups.isEmpty()) Text("No Groups yet. Make one from a recording or from Select.", style = T.small)
                    groups.forEach { g -> Chip("${g.group.name} · ${g.members}", g.group.id in f.groups, { set(f.copy(groups = f.groups.toggle(g.group.id))) }) }
                    Chip("UNUSED ONLY", f.unusedOnly, { set(f.copy(unusedOnly = !f.unusedOnly)) })
                }
                Section("TAKES") { Chip("STARRED ONLY", f.starredOnly, { set(f.copy(starredOnly = !f.starredOnly)) }) }
            }
            Row {
                BigButton("CLEAR", { bpmMin = ""; bpmMax = ""; set(LibraryFilter()) })
                BigButton("SHOW $count", { model.sheet = Sheet.None }, primary = true)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().underline().padding(vertical = 8.dp)) {
        Text(label, style = T.label, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
        FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

private fun <T> Set<T>.toggle(x: T): Set<T> = if (x in this) this - x else this + x

