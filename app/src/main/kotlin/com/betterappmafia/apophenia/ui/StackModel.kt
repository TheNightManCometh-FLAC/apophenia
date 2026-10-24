package com.betterappmafia.apophenia.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.betterappmafia.apophenia.Breadcrumbs
import com.betterappmafia.apophenia.audio.AudioController
import com.betterappmafia.apophenia.audio.NativeEngine
import com.betterappmafia.apophenia.audio.Recorded
import com.betterappmafia.apophenia.core.catalog.Conform
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.data.LayerMode
import com.betterappmafia.apophenia.data.LayerRow
import com.betterappmafia.apophenia.data.ScrapEntity
import com.betterappmafia.apophenia.data.ScrapFormat
import com.betterappmafia.apophenia.data.StackEntity
import com.betterappmafia.apophenia.data.StackLayerEntity
import com.betterappmafia.apophenia.data.TakeEntity
import java.io.File
import kotlin.math.pow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The stack in the persistent player (plan §5): which one is loaded, its layers kept in step with
 * the engine, adding and removing layers, the stack's ● bounce, and takes recorded over it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StackModel(private val model: AppModel) {
    private val app = model.app
    private val db = app.db

    private val loadedId = MutableStateFlow<Long?>(null)
    var loaded by mutableStateOf<StackEntity?>(null)
        private set

    /** The loaded stack's layers, top to bottom; every change is pushed to the engine. */
    val layers: StateFlow<List<LayerRow>> = loadedId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else db.stacks().layers(id) }
        .stateIn(app.io, SharingStarted.Eagerly, emptyList())

    /** The loaded stack as stored (tempo and key edits show up here). */
    private val stackRow: StateFlow<StackEntity?> = loadedId.flatMapLatest { id -> if (id == null) flowOf(null) else db.stacks().flow(id) }
        .stateIn(app.io, SharingStarted.Eagerly, null)

    /** Each layer's conform amounts and whether its render is playing yet (plan §5.3: amounts shown). */
    data class LayerConform(val amounts: Conform, val state: State) { enum class State { NONE, RENDERING, READY, FAILED, OFF } }
    private val _conforms = MutableStateFlow<Map<Long, LayerConform>>(emptyMap())
    val conforms: StateFlow<Map<Long, LayerConform>> = _conforms

    init {
        app.io.launch {
            combine(layers, stackRow, app.renders.finished) { rows, s, _ -> rows to s }.collect { (rows, s) ->
                if (s != null && s.id == loaded?.id) loaded = s
                push(rows)
            }
        }
    }

    /** The stack's tempo and key: its own, else from its first layer that has one (plan §5.3). */
    fun tempo(rows: List<LayerRow> = layers.value): Double? = loaded?.bpm ?: layerTempo(rows)
    fun layerTempo(rows: List<LayerRow> = layers.value): Double? = rows.firstOrNull { it.bpmLow != null && !it.freeTime }?.let { r -> val lo = r.bpmLow!!; (lo + (r.bpmHigh ?: lo)) / 2 }
    fun key(rows: List<LayerRow> = layers.value): KeyMode? = KeyMode.parseOrNull(loaded?.key) ?: layerKey(rows)
    fun layerKey(rows: List<LayerRow> = layers.value): KeyMode? = rows.firstNotNullOfOrNull { KeyMode.parseOrNull(it.key) }

    fun setTempoKey(bpm: Double?, key: KeyMode?) {
        val s = loaded ?: return
        app.io.launch { db.stacks().update(s.copy(bpm = bpm, key = key?.token, editedAt = System.currentTimeMillis())) }
    }

    // ---- Overdub latency per output route (plan §5.4), kept in app_setting as frames. ----
    private fun latencyKey(route: AudioController.Route) = "latency." + route.name.lowercase()

    suspend fun latencyFrames(route: AudioController.Route): Long? = db.settings().get(latencyKey(route))?.toLongOrNull()?.takeIf { it > 0 }

    suspend fun saveLatency(route: AudioController.Route, frames: Long) =
        db.settings().put(com.betterappmafia.apophenia.data.AppSettingEntity(latencyKey(route), frames.toString()))

    /** "Stack 1 · Low Tide", or "Temporary stack". */
    fun title(s: StackEntity? = loaded): String = s?.let { st -> st.projectId?.let { "${st.name} · ${model.projectName(it) ?: ""}" } ?: st.name } ?: ""

    private fun push(rows: List<LayerRow>) {
        val s = loaded ?: return
        val stackTempo = tempo(rows)
        val stackKey = key(rows)
        val states = HashMap<Long, LayerConform>()
        val spec = rows.mapNotNull { r ->
            val path = r.audioPath ?: return@mapNotNull null
            val l = r.layer
            val source = File(app.audioDir, path)
            // The first layer sets the stack's tempo and key, so it never moves unless they were edited.
            val c = if (l.mode == LayerMode.FREE) Conform.NONE
                else Conform.of(r.bpmLow, r.bpmHigh, r.freeTime, KeyMode.parseOrNull(r.key), stackTempo, stackKey)
            var file = source
            var factor = 1.0
            states[l.id] = when {
                c.isIdentity -> LayerConform(c, LayerConform.State.NONE)
                !l.conform -> LayerConform(c, LayerConform.State.OFF)
                else -> {
                    val ready = app.renders.ready(source, c)
                    when {
                        ready != null -> { file = ready; factor = c.lengthFactor; LayerConform(c, LayerConform.State.READY) }
                        app.renders.hasFailed(source, c) -> LayerConform(c, LayerConform.State.FAILED)
                        else -> { app.renders.request(source, c); LayerConform(c, LayerConform.State.RENDERING) }
                    }
                }
            }
            // Loop points and trim are take positions: a stretched render moves them with it.
            NativeEngine.StackLayer(
                id = l.id, path = file.absolutePath,
                mode = when (l.mode) { LayerMode.LOOP -> 0; LayerMode.ONE_SHOT -> 1; LayerMode.FREE -> 2 },
                start = l.startFrames, from = Math.round(l.fromFrames * factor), to = l.toFrames?.let { Math.round(it * factor) } ?: -1,
                gain = 10.0.pow(l.gainDb / 20.0).toFloat(), mute = l.muted, solo = l.solo,
            )
        }
        _conforms.value = states
        app.audio.setStack(s.id, title(s), spec)
    }

    private suspend fun load(s: StackEntity) {
        loaded = s
        loadedId.value = s.id
        Breadcrumbs.add("stack load ${s.id}")
    }

    /**
     * Opens [projectId]'s stack (null = the temporary stack) in the player, making it if needed, then
     * runs [then]. Plan §5.5: if another stack is in the player, ask before replacing it.
     */
    fun use(projectId: Long?, then: (StackEntity) -> Unit = {}) {
        app.io.launch {
            val target = db.stacks().ofProjectOrNew(projectId, System.currentTimeMillis())
            val current = loaded
            if (current == null || current.id == target.id) { load(target); then(target); return@launch }
            model.sheet = Sheet.Confirm(
                "Replace the stack?",
                "The player holds ${title(current)}. It stays saved with its project; ${title(target)} takes its place.",
                "REPLACE",
            ) { app.io.launch { load(target); then(target) } }
        }
    }

    /** + STACK / Add to the player: each recording becomes a loop layer at the bottom of the stack. */
    fun add(projectId: Long?, scrapIds: Collection<Long>) = use(projectId) { s ->
        app.io.launch {
            val now = System.currentTimeMillis()
            var n = 0
            for (id in scrapIds) {
                if (db.scraps().get(id)?.format != ScrapFormat.RECORDING) continue
                db.stacks().addLayer(StackLayerEntity(stackId = s.id, scrapId = id, mode = LayerMode.LOOP, addedAt = now), now)
                n++
            }
            // A recording put in a project's stack is on that project's Board too.
            if (s.projectId != null && n > 0) db.projects().addRefs(s.projectId!!, scrapIds, now)
            model.notice(if (n == 1) "Added to ${title(s)}." else "$n layers added to ${title(s)}.")
        }
    }

    /** Takes a recording's layers out of [projectId]'s stack (the recording stays on the Board and in the Library). */
    fun removeScrap(projectId: Long, scrapId: Long) {
        app.io.launch {
            val s = db.stacks().ofProject(projectId) ?: return@launch
            db.stacks().layerIdsOf(s.id, scrapId).forEach { db.stacks().deleteLayer(it) }
            db.stacks().touch(s.id, System.currentTimeMillis())
        }
    }

    fun update(layer: StackLayerEntity) {
        app.io.launch { db.stacks().updateLayer(layer, System.currentTimeMillis()) }
    }

    fun delete(row: LayerRow) {
        app.io.launch {
            db.stacks().deleteLayer(row.layer.id)
            model.notice("Layer ${row.title ?: "untitled"} removed. The recording stays in the Library.") {
                app.io.launch { db.stacks().addLayer(row.layer.copy(id = 0), System.currentTimeMillis()) }
            }
        }
    }

    /** Stack ⋯ → Add a layer: the Library in pick mode, showing recordings only (plan §5.2: by filtering, no suggestions). */
    fun pickLayers() {
        val s = loaded ?: return
        model.go(Mode.LIBRARY)
        model.filterBeforePick = model.filter
        model.filter = model.filter.copy(formats = setOf(ScrapFormat.RECORDING))
        model.addingToStack = s.id
        model.selecting = true
    }

    fun addPicked(ids: Set<Long>) {
        val s = loaded ?: return
        add(s.projectId, ids)
    }

    /**
     * The stack's ● (plan §2.4, confirmed 21 Oct 2026): the stack's own output, no mic. Saved as a new
     * recording in the stack's project (and the Library), tagged "stack mix", with the save sheet.
     */
    fun bounce() {
        val s = loaded ?: return
        app.io.launch {
            val (r, peak) = app.audio.bounceStack() ?: run { model.notice("Nothing to bounce: the stack has no playable layers."); return@launch }
            val now = System.currentTimeMillis()
            val id = db.scraps().createRecording(
                ScrapEntity(format = ScrapFormat.RECORDING, title = "${s.name} mix", createdAt = now, editedAt = now),
                TakeEntity(scrapId = 0, letter = "A", audioPath = r.file.name, durationFrames = r.frames, sampleRate = r.rate, createdAt = now),
                s.projectId,
            )
            db.tags().tag(id, "stack mix", now)
            if (peak > 1f) model.notice("The mix went over full scale (peak %.1f dB) and was clipped. Turn some layers down.".format(20 * kotlin.math.log10(peak)))
            model.sheet = Sheet.Save(id, "A")
        }
    }

    /**
     * A take recorded while the stack played (plan §5.4): a new recording in the stack's project (the
     * Library for the temporary stack) and, right away, a one-shot layer starting where ● REC fell
     * (pre-roll included, so the layer starts that much earlier). [Claude] Not yet latency-compensated:
     * the calibration step comes later in M2.
     */
    suspend fun saveLayerTake(stackId: Long, r: Recorded): Long {
        val s = db.stacks().get(stackId) ?: return app.captures.saveNew(r)
        // What you played lines up with what you heard: the route's measured round trip moves the layer earlier (plan §5.4).
        val latency = latencyFrames(app.audio.route()) ?: 0L
        val id = app.captures.saveNew(r, projectId = s.projectId, latencyFrames = latency)
        if (latency == 0L) model.notice("This route isn't calibrated, so the new layer may sit a little late. Tools → Overdub latency measures it.")
        val start = (r.stackPosAtStart ?: 0L) - r.prerollFrames - latency
        val now = System.currentTimeMillis()
        db.stacks().addLayer(StackLayerEntity(stackId = s.id, scrapId = id, mode = LayerMode.ONE_SHOT, startFrames = start, addedAt = now), now)
        return id
    }
}
