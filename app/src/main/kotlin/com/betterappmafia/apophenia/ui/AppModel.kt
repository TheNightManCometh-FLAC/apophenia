package com.betterappmafia.apophenia.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.betterappmafia.apophenia.ApopheniaApp
import com.betterappmafia.apophenia.Breadcrumbs
import com.betterappmafia.apophenia.backup.BackupArchive
import com.betterappmafia.apophenia.data.AppSettingEntity
import com.betterappmafia.apophenia.data.GroupRow
import com.betterappmafia.apophenia.data.ProjectEntity
import com.betterappmafia.apophenia.data.ProjectRow
import com.betterappmafia.apophenia.data.ScrapEntity
import com.betterappmafia.apophenia.data.ScrapFormat
import com.betterappmafia.apophenia.data.ValueSource
import com.betterappmafia.apophenia.data.LibraryFilter
import com.betterappmafia.apophenia.data.LibraryRow
import com.betterappmafia.apophenia.data.LibrarySort
import com.betterappmafia.apophenia.data.TakeEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The three bottom tabs, by what you are doing (Christopher, 25 Oct 2026: navigation option B). */
enum class Tab(val label: String) { MAKE("MAKE"), PRACTICE("PRACTICE"), STUDY("STUDY") }

/** The screens, each a sub-tab of one [Tab]. AI is under MAKE (Christopher, 25 Oct 2026: "ai is not study It's make"). */
enum class Mode(val label: String, val tab: Tab) {
    BOARD("BOARD", Tab.MAKE), LIBRARY("LIBRARY", Tab.MAKE), AI("AI", Tab.MAKE),
    TOOLS("TOOLS", Tab.PRACTICE), EAR("EAR", Tab.PRACTICE),
    COURSE("COURSE", Tab.STUDY), REFERENCE("REFERENCE", Tab.STUDY);

    /** The screens that were Learn: Course, Ear and Reference share its screen code. */
    val learn get() = this == EAR || this == COURSE || this == REFERENCE
}

val Tab.modes get() = Mode.entries.filter { it.tab == this }

/** What slides up over the screen. */
sealed interface Sheet {
    data object None : Sheet
    data object Filter : Sheet
    data class Save(val scrapId: Long, val letter: String) : Sheet
    data class Tags(val scrapId: Long) : Sheet
    data class Key(val scrapId: Long) : Sheet
    data class Bpm(val scrapId: Long) : Sheet
    data class Rename(val scrapId: Long) : Sheet
    data class Restore(val ok: BackupArchive.Result.Ok) : Sheet
    /** Tags for several scraps at once (multi-select). */
    data class BulkTags(val ids: Set<Long>) : Sheet
    /** Group membership for one or several scraps. */
    data class Groups(val ids: Set<Long>) : Sheet
    /** Name a new project ([projectId] null) or rename one. */
    data class ProjectName(val projectId: Long?) : Sheet
    /** Put one or several scraps on project Boards, or take them off. */
    data class Projects(val ids: Set<Long>) : Sheet
    /** A yes/no before something that replaces what's on screen. */
    data class Confirm(val title: String, val text: String, val yes: String, val onYes: () -> Unit) : Sheet
    /** The loaded stack's tempo and key. */
    data object StackTempoKey : Sheet
}

/** The bar above the player. [undo] is its one action, labelled [action] (UNDO unless it offers something else). */
data class Notice(val text: String, val undo: (() -> Unit)? = null, val action: String = "UNDO", val id: Long = System.nanoTime())

/** Where the take being recorded goes (plan §10.1). */
sealed interface RecordTarget {
    /** Quick capture: a new Library recording. */
    data object Library : RecordTarget
    /** Recorded from a project's Board: a new recording in that project, also in Library search unless switched off. */
    data class Project(val id: Long, val name: String) : RecordTarget
    /** The next take of an existing recording. */
    data class TakeOf(val scrapId: Long) : RecordTarget
    /** Top ● REC while the stack plays (plan §5.4): a new layer of that stack, in its project. */
    data class StackLayer(val stackId: Long, val label: String) : RecordTarget
}

private const val LAST_PROJECT = "last_project"
private const val CHORD_DIALECT = "chords.dialect"
private const val CHORD_STYLE = "chords.style"
private const val LEARN_SHOW = "learn.show"
private const val LEARN_GUITAR = "learn.guitar"
private const val LEARN_BASS = "learn.bass"
private const val THEME = "theme"

/** UI state that outlives the Activity (rotation, returning from the background). */
class AppModel(val app: ApopheniaApp) {
    var mode by mutableStateOf(Mode.BOARD)
        private set
    /** The sub-tab each tab was last left on, so the tab opens there again. */
    private val lastMode = androidx.compose.runtime.mutableStateMapOf<Tab, Mode>()
    /** The drum rudiment open in Learn › Reference (its PAS number), or null for the list. */
    var learnRudiment by mutableStateOf<Int?>(null)
    /** The groove open in Learn › Reference (its id), or null. */
    var learnGroove by mutableStateOf<String?>(null)
    /** The recording open full size, over the current mode. */
    /** The take whose transcription is open in the correction screen (plan §10.3), over everything else. */
    var correcting by mutableStateOf<Long?>(null)
    var detail by mutableStateOf<Long?>(null)
    var showTrash by mutableStateOf(false)
    var showNotices by mutableStateOf(false)
    /** Settings (the ⚙ in the top bar) over the current screen. */
    var showSettings by mutableStateOf(false)
    var sheet by mutableStateOf<Sheet>(Sheet.None)
    var search by mutableStateOf("")
    var filter by mutableStateOf(LibraryFilter())
    var sort by mutableStateOf(LibrarySort.CAPTURED)
    var notice by mutableStateOf<Notice?>(null)
    var crashText by mutableStateOf<String?>(null)
    var recordTarget by mutableStateOf<RecordTarget>(RecordTarget.Library)
        private set

    val library: StateFlow<List<LibraryRow>> = app.db.scraps().libraryRows().stateIn(app.io, SharingStarted.Eagerly, emptyList())
    val trash: StateFlow<List<LibraryRow>> = app.db.scraps().trashRows().stateIn(app.io, SharingStarted.Eagerly, emptyList())

    private var noticeJob: Job? = null

    init {
        app.audio.onRecordingInterrupted = { stopRecording("The microphone was taken (a call or another app). The take is saved up to that point.") }
        app.audio.onPausedBySystem = { notice(it) }
    }
    private val taps = ArrayDeque<Long>()

    val groups: StateFlow<List<GroupRow>> = app.db.groups().liveRows().stateIn(app.io, SharingStarted.Eagerly, emptyList())

    // ---- Projects (plan §4.1). The open one decides what the Board shows; launch reopens the last one (§2.3). ----
    val projects: StateFlow<List<ProjectRow>> = app.db.projects().liveRows().stateIn(app.io, SharingStarted.Eagerly, emptyList())
    val trashedProjects: StateFlow<List<ProjectEntity>> = app.db.projects().trashFlow().stateIn(app.io, SharingStarted.Eagerly, emptyList())
    var projectId by mutableStateOf<Long?>(null)
        private set
    /** Library multi-select started from a Board's "Add from the Library": the project the picks go to. */
    var addingTo by mutableStateOf<Long?>(null)
        private set

    init {
        app.io.launch {
            val last = app.db.settings().get(LAST_PROJECT)?.toLongOrNull()
            if (last != null) app.db.projects().get(last)?.takeIf { it.deletedAt == null }?.let { projectId = it.id }
        }
    }

    // ---- How chord symbols are read (plan §6.2): pop or jazz for the ambiguous ones, and the note names. ----
    /** The project shown in stand mode (plan §4.4), or null. Kept here so turning the phone doesn't leave it. */
    var standProject by mutableStateOf<Long?>(null)

    var chordDialect by mutableStateOf(com.betterappmafia.apophenia.core.chord.ChordDialect())
        private set

    init {
        app.io.launch { chordDialect = com.betterappmafia.apophenia.core.chord.ChordDialect.parse(app.db.settings().get(CHORD_DIALECT)) }
    }

    /** The Ear drill open (plan §9), its mode (LISTEN, PRACTICE, TEST), the sound settings and how answers are given. */
    var earDrill by mutableStateOf<String?>(null)
    var earMode by mutableStateOf("PRACTICE")
    var earSettings by mutableStateOf(com.betterappmafia.apophenia.core.learn.EarSettings())
    /** NAME, PIANO or GUITAR. */
    var earAnswerOn by mutableStateOf("NAME")
    /** What the Ear's "Your music" drills draw from, loaded when that chip is opened. */
    var earPool by mutableStateOf<com.betterappmafia.apophenia.core.learn.YourPool?>(null)

    /** The Learn part on screen (COURSE, EAR or REFERENCE), now its own sub-tab. */
    val learnPart: String get() = mode.name
    /** The Course category open (its topics), the LEVEL ▾ filter (0 all, 1–3 only, 4 four and up), the page open, and the key its TRY and HEAR start on. */
    var courseCat by mutableStateOf<String?>(null)
    var courseLevel by mutableStateOf(0)
    var coursePage by mutableStateOf<String?>(null)
    var courseRoot by mutableStateOf<com.betterappmafia.apophenia.core.pitch.PitchName?>(null)
    /** The Reference list open (a RefKind name, or GROOVES / RUDIMENTS), or null for the categories. */
    var learnList by mutableStateOf<String?>(null)
    /** The Reference page open, on its root. */
    var refPage by mutableStateOf<com.betterappmafia.apophenia.core.learn.RefHit?>(null)
    /** What Reference pages are drawn on: GUITAR, BASS, PIANO or STAFF. Remembered. */
    var learnShow by mutableStateOf("GUITAR")
        private set
    /** The guitar and bass tunings Reference pages use ("your tuning", plan §8). Remembered. */
    var learnGuitar by mutableStateOf(com.betterappmafia.apophenia.core.instrument.Tunings.GUITAR_STANDARD)
        private set
    var learnBass by mutableStateOf(com.betterappmafia.apophenia.core.instrument.Tunings.BASS_STANDARD)
        private set

    init {
        app.io.launch {
            val s = app.db.settings()
            s.get(LEARN_SHOW)?.let { learnShow = it }
            fun tuning(id: String?) = com.betterappmafia.apophenia.core.instrument.Tunings.all.firstOrNull { it.id == id } ?: com.betterappmafia.apophenia.core.learn.TuningReference.byId(id ?: "")?.tuning
            tuning(s.get(LEARN_GUITAR))?.let { learnGuitar = it }
            tuning(s.get(LEARN_BASS))?.let { learnBass = it }
        }
    }

    fun chooseLearnShow(v: String) {
        learnShow = v
        app.io.launch { app.db.settings().put(AppSettingEntity(LEARN_SHOW, v)) }
    }

    fun chooseLearnTuning(t: com.betterappmafia.apophenia.core.instrument.Tuning, bass: Boolean) {
        if (bass) learnBass = t else learnGuitar = t
        app.io.launch { app.db.settings().put(AppSettingEntity(if (bass) LEARN_BASS else LEARN_GUITAR, t.id)) }
    }

    /** How chord symbols are drawn (plan §6.2): pop, jazz, classical numerals or plain. */
    var chordStyle by mutableStateOf(com.betterappmafia.apophenia.core.chord.ChordStyle.POP)
        private set

    init {
        app.io.launch { chordStyle = com.betterappmafia.apophenia.core.chord.ChordStyle.parse(app.db.settings().get(CHORD_STYLE)) }
    }

    fun chooseChordStyle(s: com.betterappmafia.apophenia.core.chord.ChordStyle) {
        chordStyle = s
        app.io.launch { app.db.settings().put(AppSettingEntity(CHORD_STYLE, s.name)) }
    }

    fun chooseChordDialect(d: com.betterappmafia.apophenia.core.chord.ChordDialect) {
        chordDialect = d
        app.io.launch { app.db.settings().put(AppSettingEntity(CHORD_DIALECT, d.token)) }
    }

    init {
        app.io.launch { com.betterappmafia.apophenia.ui.theme.C.palette = com.betterappmafia.apophenia.ui.theme.Palettes.byId(app.db.settings().get(THEME)) }
    }

    /** The colour scheme (Settings › THEME), kept. */
    fun chooseTheme(p: com.betterappmafia.apophenia.ui.theme.Palette) {
        com.betterappmafia.apophenia.ui.theme.C.palette = p
        app.io.launch { app.db.settings().put(AppSettingEntity(THEME, p.id)) }
    }

    fun projectName(id: Long?): String? = projects.value.firstOrNull { it.project.id == id }?.project?.name

    fun openProject(id: Long?) {
        Breadcrumbs.add("open project $id")
        projectId = id
        app.io.launch {
            app.db.settings().put(AppSettingEntity(LAST_PROJECT, id?.toString() ?: ""))
            if (id != null) app.db.projects().touch(id, System.currentTimeMillis())
        }
    }

    fun newProject(name: String) {
        val n = name.trim().ifEmpty { return }
        app.io.launch {
            val now = System.currentTimeMillis()
            val id = app.db.projects().insert(ProjectEntity(name = n, createdAt = now, editedAt = now))
            openProject(id)
            mode = Mode.BOARD; detail = null; showTrash = false; showNotices = false
        }
    }

    fun renameProject(id: Long, name: String) {
        val n = name.trim().ifEmpty { return }
        app.io.launch { app.db.projects().rename(id, n, System.currentTimeMillis()) }
    }

    /** To the trash (plan §3.8): its Board and settings come back with it. Its scraps stay in the Library. */
    fun trashProject(id: Long) {
        val name = projectName(id) ?: return
        app.io.launch {
            app.db.projects().moveToTrash(id, System.currentTimeMillis())
            if (projectId == id) openProject(null)
            notice("Project $name moved to the trash. Its scraps stay in the Library.") {
                app.io.launch { app.db.projects().restore(id); openProject(id) }
            }
        }
    }

    fun restoreProject(id: Long) {
        app.io.launch { app.db.projects().restore(id) }
    }

    /** Board ⋯ → Add from the Library: the Library opens in multi-select, and its bar adds the picks here. */
    fun pickFromLibrary(projectId: Long) {
        go(Mode.LIBRARY)
        addingTo = projectId
        selecting = true
    }

    /**
     * Puts scraps on a project's Board (references only, plan §4.2). Then, if any of them are in
     * Groups, offers the one-tap removal from those Groups (§3.5), itself undoable.
     */
    fun addToProject(projectId: Long, ids: Set<Long>, nameHint: String? = null) {
        val name = nameHint ?: projectName(projectId) ?: return
        app.io.launch {
            val now = System.currentTimeMillis()
            val added = app.db.projects().addRefs(projectId, ids, now)
            val also = if (ids.size == 1) "It's also in" else "Some of them are also in"
            val base = when {
                added == 0 -> "Already on the $name Board."
                ids.size == 1 -> "Added to $name."
                else -> "$added added to $name${if (added < ids.size) " (${ids.size - added} were already there)" else ""}."
            }
            val inGroups = app.db.groups().liveMemberships(ids.toList())
            if (inGroups.isEmpty() || added == 0) { notice(base); return@launch }
            val names = groups.value.filter { g -> inGroups.any { it.groupId == g.group.id } }.map { it.group.name }
            notice("$base $also ${names.joinToString(", ")}.", action = "REMOVE FROM GROUPS") {
                app.io.launch {
                    app.db.groups().leaveAllLive(ids.toList())
                    notice("Taken out of ${names.joinToString(", ")}.") { app.io.launch { inGroups.forEach { app.db.groups().addMember(it) } } }
                }
            }
        }
    }

    /** Takes scraps off a project's Board, keeping the rows so UNDO puts back their settings and windows. */
    fun takeOffProject(projectId: Long, ids: Set<Long>) {
        val name = projectName(projectId) ?: return
        app.io.launch {
            val refs = ids.mapNotNull { app.db.projects().ref(projectId, it) }
            refs.forEach { app.db.projects().takeOff(projectId, it.scrapId) }
            notice(if (refs.size == 1) "Taken off the $name Board. It stays in the Library." else "${refs.size} taken off the $name Board.") {
                app.io.launch { refs.forEach { app.db.projects().addRef(it) } }
            }
        }
    }

    // ---- Multi-select in the Library (long-press, or Library ⋯ → Select) ----
    var selecting by mutableStateOf(false)
    var selected by mutableStateOf<Set<Long>>(emptySet())

    fun toggleSelect(id: Long) {
        selecting = true
        selected = if (id in selected) selected - id else selected + id
    }

    fun endSelect() {
        if (addingToStack != null) filterBeforePick?.let { filter = it }
        filterBeforePick = null
        selecting = false; selected = emptySet(); addingTo = null; addingToStack = null
    }

    /** The Library filter as it was before a stack pick narrowed it to recordings; put back when the pick ends. */
    var filterBeforePick: LibraryFilter? = null

    /** Library multi-select started from the stack's "Add a layer": the stack the picks go into. */
    var addingToStack by mutableStateOf<Long?>(null)

    fun trashSelected() {
        val ids = selected
        endSelect()
        app.io.launch {
            val now = System.currentTimeMillis()
            if (app.audio.player.value?.scrapId in ids) app.audio.unload()
            ids.forEach { app.db.scraps().moveToTrash(it, now) }
            notice("${ids.size} moved to the trash.") { app.io.launch { ids.forEach { app.db.scraps().restore(it) } } }
        }
    }

    /** A bottom tab: the sub-tab it was last on (the first the first time). */
    fun go(t: Tab) = go(lastMode[t] ?: t.modes.first())

    /** Shows [m] without closing what is open in it (a Course link to an Ear drill keeps the lesson behind it). */
    fun show(m: Mode) { mode = m; lastMode[m.tab] = m }

    fun go(m: Mode) {
        Breadcrumbs.add("mode $m")
        mode = m; lastMode[m.tab] = m; detail = null; showTrash = false; showNotices = false; showSettings = false; showStack = false; sheet = Sheet.None; endSelect()
        tools.screen = null
        learnRudiment = null
        learnGroove = null
        learnList = null
        refPage = null
        earDrill = null
        coursePage = null
        courseCat = null
    }

    /** Opens a scrap full size. [fromProject] = opened on that project's Board, so a written scrap shows and edits that project's version. */
    fun open(scrapId: Long, fromProject: Long? = null, edit: Boolean = false, view: String? = null) {
        Breadcrumbs.add("open scrap $scrapId from $fromProject")
        detailProject = fromProject
        detailEdit = edit
        detailView = view
        detail = scrapId
    }

    /** Lyrics auto-scroll speed 1–6, kept while the app runs. */
    var lyricSpeed by mutableStateOf(3)

    /** The stack workspace (C4) over the current mode; the top bars and the player stay. */
    var showStack by mutableStateOf(false)

    /** The project a full view was opened from (null = the Library), and whether it opens in edit mode. */
    var detailProject by mutableStateOf<Long?>(null)
        private set
    var detailEdit by mutableStateOf(false)
        private set
    /** The view a score opens in ("roll" for a new piano roll), or null for its usual one. */
    var detailView by mutableStateOf<String?>(null)
        private set

    /**
     * Write lyrics, a text note or a chord chart (plan §2.2): a new scrap, opened straight in its
     * editor. On a project's Board it goes to that project (and the Library), elsewhere to the Library.
     */
    fun write(format: ScrapFormat, projectId: Long? = null) {
        app.io.launch {
            val now = System.currentTimeMillis()
            // A drum chart starts as an empty bar in the metronome's meter (when a chart can take it) and tempo (plan §3.1).
            val scrap = if (format == ScrapFormat.DRUM_CHART) {
                val m = tools.metronome
                val start = com.betterappmafia.apophenia.core.tools.DrumChart.starter()
                val chart = if (m.meter in com.betterappmafia.apophenia.core.tools.DrumChart.METERS) start.copy(grid = start.grid.resized(meter = m.meter)) else start
                ScrapEntity(format = format, body = chart.text(), bpmLow = m.bpm, bpmHigh = m.bpm, bpmSource = ValueSource.METRONOME, createdAt = now, editedAt = now)
            } else ScrapEntity(format = format, body = "", createdAt = now, editedAt = now)
            val id = app.db.scraps().createWritten(scrap, projectId)
            open(id, projectId, edit = true)
        }
    }

    /**
     * New sheet music: four empty treble-clef bars in the metronome's meter and tempo (the clef changes with ADD ▾ later).
     * [roll]: it opens in the piano roll, the other way of writing sheet music (Christopher, 25 Oct 2026).
     */
    fun writeSheet(projectId: Long? = null, roll: Boolean = false) {
        app.io.launch {
            val now = System.currentTimeMillis()
            val m = tools.metronome
            val beat = com.betterappmafia.apophenia.core.score.FromNotes.beatLength(m.meter)
            val unit = com.betterappmafia.apophenia.core.rhythm.Duration(
                com.betterappmafia.apophenia.core.rhythm.NoteValue.entries.first { it.length == if (m.meter.isCompound) beat * com.betterappmafia.apophenia.core.rhythm.Rational.of(2, 3) else beat },
                if (m.meter.isCompound) 1 else 0,
            )
            val score = com.betterappmafia.apophenia.core.score.ScoreEdit.emptySheet("Notes", com.betterappmafia.apophenia.core.score.Clef.TREBLE, m.meter, com.betterappmafia.apophenia.core.rhythm.Tempo(m.bpm, unit), null)
            val scrap = ScrapEntity(
                format = ScrapFormat.SHEET, title = "Sheet music", body = com.betterappmafia.apophenia.core.score.ScoreText.encode(score),
                bpmLow = m.bpm, bpmHigh = m.bpm, bpmSource = ValueSource.METRONOME, createdAt = now, editedAt = now,
            )
            val id = app.db.scraps().createWritten(scrap, projectId)
            open(id, projectId, edit = true, view = if (roll) "roll" else null)
        }
    }

    /** A new empty tab, four bars in the metronome's meter and tempo, for a guitar or bass in standard tuning (TUNING ▾ changes it). */
    fun writeTab(bass: Boolean, projectId: Long? = null) {
        app.io.launch {
            val now = System.currentTimeMillis()
            val m = tools.metronome
            val tuning = if (bass) com.betterappmafia.apophenia.core.instrument.Tunings.BASS_STANDARD else com.betterappmafia.apophenia.core.instrument.Tunings.GUITAR_STANDARD
            val inst = com.betterappmafia.apophenia.core.instrument.Instrument(tuning, frets = if (bass) 24 else 22)
            val beat = com.betterappmafia.apophenia.core.score.FromNotes.beatLength(m.meter)
            val unit = com.betterappmafia.apophenia.core.rhythm.Duration(
                com.betterappmafia.apophenia.core.rhythm.NoteValue.entries.first { it.length == if (m.meter.isCompound) beat * com.betterappmafia.apophenia.core.rhythm.Rational.of(2, 3) else beat },
                if (m.meter.isCompound) 1 else 0,
            )
            val score = com.betterappmafia.apophenia.core.score.ScoreEdit.emptyTab(if (bass) "Bass" else "Guitar", inst, m.meter, com.betterappmafia.apophenia.core.rhythm.Tempo(m.bpm, unit), null)
            val scrap = ScrapEntity(
                format = ScrapFormat.TAB, title = if (bass) "Bass tab" else "Guitar tab", body = com.betterappmafia.apophenia.core.score.ScoreText.encode(score),
                bpmLow = m.bpm, bpmHigh = m.bpm, bpmSource = ValueSource.METRONOME, createdAt = now, editedAt = now,
            )
            val id = app.db.scraps().createWritten(scrap, projectId)
            open(id, projectId, edit = true)
        }
    }

    fun notice(text: String, action: String = "UNDO", undo: (() -> Unit)? = null) {
        val n = Notice(text, undo, action)
        notice = n
        noticeJob?.cancel()
        noticeJob = app.io.launch { delay(if (undo != null) 8000 else 4000); if (notice?.id == n.id) notice = null }
    }

    /** ● REC into [target] (top ● REC: the Library). Call after the mic permission is granted. */
    fun startRecording(target: RecordTarget = RecordTarget.Library) {
        // Top ● REC while the stack plays records a new layer of it (plan §5.4).
        val s = app.audio.stack.value
        recordTarget = if (target == RecordTarget.Library && s?.playing == true) RecordTarget.StackLayer(s.stackId, stacks.title().uppercase()) else target
        app.io.launch {
            if (!app.audio.startRecording()) notice("Couldn't open the microphone. Another app may be using it.")
        }
    }

    fun stopRecording(why: String? = null) {
        app.io.launch {
            val r = app.audio.stopRecording() ?: return@launch
            val target = recordTarget
            recordTarget = RecordTarget.Library
            if (r.frames <= 0) { r.file.delete(); notice("Nothing was recorded."); return@launch }
            val (id, letter) = when (target) {
                is RecordTarget.TakeOf -> target.scrapId to app.captures.saveAsTakeOf(target.scrapId, r)
                is RecordTarget.Project -> app.captures.saveNew(r, projectId = target.id) to "A"
                is RecordTarget.StackLayer -> stacks.saveLayerTake(target.stackId, r) to "A"
                RecordTarget.Library -> app.captures.saveNew(r) to "A"
            }
            if (r.lostFrames > 0) notice("The disk fell behind: ${"%.1f".format(r.lostFrames / r.rate.toDouble())} s missing from this take.")
            if (r.diskError) notice("The phone reported a write error while recording. Check free storage.")
            if (why != null) notice(why)
            sheet = Sheet.Save(id, letter)
            app.analyser.kick()
        }
    }

    // ---- Backup and restore (plan §11). The Activity supplies the two system pickers. ----
    var pickBackupFolder: () -> Unit = {}
    var pickBackupFile: () -> Unit = {}
    var backupBusy by mutableStateOf(false)
    /** After a restore the database is closed: the app has to be closed and opened again. This says why. */
    var restartMessage by mutableStateOf<String?>(null)

    // ---- Importing files from the phone (plan §3.1). The Activity supplies the picker. ----
    var pickImport: () -> Unit = {}

    // ---- Saving a transcription as a MIDI file (plan §10.3). The Activity supplies the save picker. ----
    var pickMidiTarget: (String) -> Unit = {}
    private var pendingMidi: ByteArray? = null

    fun exportMidi(bytes: ByteArray, name: String) {
        pendingMidi = bytes
        pickMidiTarget(name)
    }

    fun midiTargetPicked(uri: android.net.Uri?) {
        val bytes = pendingMidi ?: return
        pendingMidi = null
        if (uri == null) return
        app.io.launch {
            val ok = runCatching { app.contentResolver.openOutputStream(uri, "w")!!.use { it.write(bytes) } }.isSuccess
            notice(if (ok) "MIDI file saved." else "The MIDI file couldn't be written there.")
        }
    }

    // ---- Saving an export (MusicXML now; ChordPro, tab text later). The Activity supplies the save picker. ----
    var pickFileTarget: (String) -> Unit = {}
    private var pendingExport: Pair<ByteArray, String>? = null

    fun exportFile(bytes: ByteArray, name: String, what: String) {
        pendingStream = null
        pendingExport = bytes to what
        pickFileTarget(name)
    }

    /** A large export written straight to the file picked (the FL Studio package): [write] returns a summary for the notice. */
    private var pendingStream: Pair<suspend (java.io.OutputStream) -> String, String>? = null
    var exporting by mutableStateOf(false)
        private set

    fun exportStream(name: String, what: String, write: suspend (java.io.OutputStream) -> String) {
        pendingExport = null
        pendingStream = write to what
        pickFileTarget(name)
    }

    fun fileTargetPicked(uri: android.net.Uri?) {
        pendingStream?.let { (write, what) ->
            pendingStream = null
            if (uri == null) return
            app.io.launch {
                exporting = true
                notice("Making the $what…")
                runCatching { app.contentResolver.openOutputStream(uri, "w")!!.use { write(it) } }
                    .onSuccess { notice("$what saved: $it.") }
                    .onFailure {
                        Breadcrumbs.add("export failed: $it")
                        runCatching { android.provider.DocumentsContract.deleteDocument(app.contentResolver, uri) }
                        notice("The $what couldn't be written: ${it.message}")
                    }
                exporting = false
            }
            return
        }
        val (bytes, what) = pendingExport ?: return
        pendingExport = null
        if (uri == null) return
        app.io.launch {
            val ok = runCatching { app.contentResolver.openOutputStream(uri, "w")!!.use { it.write(bytes) } }.isSuccess
            notice(if (ok) "$what saved." else "The $what couldn't be written there.")
        }
    }
    private var importInto: Long? = null
    var importing by mutableStateOf(0)
        private set

    /** Library ⋯ → Import (to the Library), or Board ⋯ → Import into the project. */
    fun startImport(projectId: Long? = null) {
        importInto = projectId
        pickImport()
    }

    fun importFiles(uris: List<android.net.Uri>) {
        val projectId = importInto
        importInto = null
        app.io.launch {
            importing = uris.size
            notice("Importing ${uris.size} file${if (uris.size == 1) "" else "s"}…")
            var done = 0
            val refused = ArrayList<String>()
            val partly = ArrayList<String>()
            var last: Long? = null
            val base = System.currentTimeMillis()
            uris.forEachIndexed { i, uri ->
                when (val r = app.imports.import(uri, projectId, base + i)) {
                    is com.betterappmafia.apophenia.capture.Imports.Result.Done -> { done++; last = r.scrapId; if (r.notRead.isNotEmpty()) partly += "${r.name} without " + r.notRead.joinToString(", ") }
                    is com.betterappmafia.apophenia.capture.Imports.Result.Refused -> refused += "${r.name}: ${r.why}"
                }
                importing = uris.size - i - 1
            }
            importing = 0
            app.analyser.kick()
            val where = projectId?.let { projectName(it) } ?: "the Library"
            notice(
                buildString {
                    append(if (done == 1) "Imported 1 file to $where" else "Imported $done files to $where")
                    if (refused.isNotEmpty()) append(". Not imported: ").append(refused.joinToString("; "))
                    if (partly.isNotEmpty()) append(". Read ").append(partly.joinToString("; "))
                    append('.')
                }
            )
            if (done == 1 && uris.size == 1) last?.let { open(it, projectId) }
        }
    }

    fun backupNow() {
        app.io.launch {
            val tree = app.backups.folder() ?: run { pickBackupFolder(); return@launch }
            if (app.audio.mic.value == com.betterappmafia.apophenia.audio.Mic.RECORDING) { notice("Stop the recording first."); return@launch }
            backupBusy = true
            runCatching { app.backups.backupNow(tree) }
                .onSuccess { notice("Backed up to ${app.backups.folderName(tree)}: $it") }
                .onFailure { notice("Backup failed: ${it.message}. Pick the folder again from Library ⋯.") }
            backupBusy = false
        }
    }

    fun folderPicked(tree: android.net.Uri) {
        app.io.launch {
            runCatching { app.backups.setFolder(tree) }.onFailure { notice("That folder can't be used: ${it.message}"); return@launch }
            backupNow()
        }
    }

    fun backupFilePicked(file: android.net.Uri) {
        app.io.launch {
            backupBusy = true
            when (val r = runCatching { app.backups.inspect(file) }.getOrElse { BackupArchive.Result.Refused("It couldn't be read: ${it.message}") }) {
                is BackupArchive.Result.Ok -> sheet = Sheet.Restore(r)
                is BackupArchive.Result.Refused -> notice("Not restored. ${r.reason}")
            }
            backupBusy = false
        }
    }

    fun confirmRestore(ok: BackupArchive.Result.Ok) {
        sheet = Sheet.None
        app.io.launch {
            if (app.audio.mic.value == com.betterappmafia.apophenia.audio.Mic.RECORDING) { notice("Stop the recording first."); return@launch }
            app.audio.unload().join()
            runCatching { app.backups.apply(ok) }
                .onSuccess { restartMessage = "Restored: ${ok.summary.audioCount} recordings. What was on the phone before is kept aside (files/before-restore) until the next restore." }
                .onFailure { restartMessage = "The restore failed and the phone's own data was put back as it was (${it.message})." }
        }
    }

    /** Tap tempo (core MetronomeSettings.tapBpm: median of the last eight taps, reset after a 2 s pause). Returns the BPM or null. */
    fun tap(now: Long = System.currentTimeMillis()): Double? {
        if (taps.isNotEmpty() && now - taps.last() > 2000) taps.clear()
        taps.addLast(now)
        while (taps.size > 8) taps.removeFirst()
        if (taps.size < 2) return null
        val bpm = com.betterappmafia.apophenia.core.tools.MetronomeSettings.tapBpm(taps.toList()) ?: return null
        app.audio.setClick(app.audio.click.value.on, Math.round(bpm).toDouble())
        return bpm
    }

    fun play(scrapId: Long, take: TakeEntity, title: String, start: Boolean = true) {
        app.audio.load(scrapId, take.id, "$title · ${take.letter}", app.captures.fileOf(take).absolutePath, start)
    }

    fun trashScrap(id: Long, title: String) {
        app.io.launch {
            if (app.audio.player.value?.scrapId == id) app.audio.unload()
            app.db.scraps().moveToTrash(id, System.currentTimeMillis())
            if (detail == id) detail = null
            notice("$title moved to the trash.") { app.io.launch { app.db.scraps().restore(id) } }
        }
    }

    fun trashTake(take: TakeEntity, title: String) {
        app.io.launch {
            if (app.audio.player.value?.takeId == take.id) app.audio.unload()
            app.db.takes().moveToTrash(take.id, System.currentTimeMillis())
            app.db.scraps().touch(take.scrapId, System.currentTimeMillis())
            notice("$title · take ${take.letter} moved to the trash.") { app.io.launch { app.db.takes().restore(take.id) } }
        }
    }

    /** Metronome, tuner, drone and the top-bar slots (plan §7). */
    val tools = ToolsModel(this)

    /** The stack in the player (plan §5). Last, so everything it reads is initialised. */
    val stacks = StackModel(this)
}
