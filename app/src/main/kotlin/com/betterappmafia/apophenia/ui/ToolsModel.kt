package com.betterappmafia.apophenia.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.instrument.Tunings
import com.betterappmafia.apophenia.core.pitch.OctaveLabel
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.tools.DroneSettings
import com.betterappmafia.apophenia.core.tools.DroneThird
import com.betterappmafia.apophenia.core.tools.MetronomeSettings
import com.betterappmafia.apophenia.core.tools.Temperament
import com.betterappmafia.apophenia.core.tools.TunerRange
import com.betterappmafia.apophenia.core.tools.TunerReading
import com.betterappmafia.apophenia.core.tools.TunerSetup
import com.betterappmafia.apophenia.core.tools.Yin
import com.betterappmafia.apophenia.data.AppSettingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ln

/** The tools the top bar's two slots can hold (plan §2.4: chosen in Tools, default Tuner and Drone). */
enum class Tool(val label: String) { METRONOME("Metronome"), TUNER("Tuner"), DRONE("Drone"), SPEED("Playback speed"), ISOLATOR("Isolator"), PRACTICE("Practice timer") }

/** Full-screen tools inside the Tools mode. */
enum class ToolScreen { METRONOME, TUNER, DRONE, SPEED, ISOLATOR, PRACTICE }

/**
 * The tools' state (plan §7): the full metronome, tuner and drone, the pinned top-bar slots, and the
 * drum pattern shown over the click. Everything is saved in app_setting and restored at launch.
 */
class ToolsModel(private val model: AppModel) {
    private val app = model.app
    private val audio = app.audio

    var screen by mutableStateOf<ToolScreen?>(null)
    var metronome by mutableStateOf(MetronomeSettings())
        private set
    var tuner by mutableStateOf(TunerSetup())
        private set
    var tunerRangeId by mutableStateOf(TunerRange.ANY.id)
        private set
    var drone by mutableStateOf(DroneSettings())
        private set
    var pins by mutableStateOf(listOf(Tool.TUNER, Tool.DRONE))
        private set
    var isolator by mutableStateOf(com.betterappmafia.apophenia.core.tools.IsolatorSettings())
        private set
    var clickVolume by mutableStateOf(0.7f)
        private set
    /** The tuner's latest reading, null while nothing pitched is heard. */
    var reading by mutableStateOf<TunerReading?>(null)
        private set

    private var tunerJob: Job? = null

    init {
        audio.onMetronomeChanged = { s ->
            metronome = s
            app.io.launch { app.db.settings().put(AppSettingEntity(K_METRONOME, s.encode())) }
        }
        app.io.launch {
            val st = app.db.settings()
            MetronomeSettings.decode(st.get(K_METRONOME))?.let { metronome = it }
            audio.setMetronome(metronome)
            decodeTuner(st.get(K_TUNER))
            decodeDrone(st.get(K_DRONE))
            st.get(K_PINS)?.split(',')?.mapNotNull { n -> Tool.entries.firstOrNull { it.name == n } }?.takeIf { it.size == 2 }?.let { pins = it }
            com.betterappmafia.apophenia.core.tools.IsolatorSettings.decode(st.get(K_ISOLATOR))?.let { isolator = it }
            audio.setIsolator(isolator)
            PracticeTimer.decode(st.get(K_TIMER))?.let { timer = it }
            st.get(K_VOLUME)?.toFloatOrNull()?.let { clickVolume = it.coerceIn(0.1f, 1f); audio.setClickVolume(clickVolume) }
        }
    }

    // ---- Metronome ----

    fun changeMetronome(s: MetronomeSettings) { metronome = s; audio.setMetronome(s) }

    fun setVolume(v: Float) {
        clickVolume = v
        audio.setClickVolume(v)
        put(K_VOLUME, v.toString())
    }

    // ---- Practice log (plan §7: a simple timer attached to a scrap, project or exercise; note; history) ----

    var timer by mutableStateOf(PracticeTimer())
        private set

    fun changeTimer(t: PracticeTimer) { timer = t; put(K_TIMER, t.encode()) }

    /** Saves the time so far (with [note]) to the log and resets the timer. */
    fun savePractice(note: String, now: Long = System.currentTimeMillis()) {
        val t = timer
        val secs = t.elapsedMs(now) / 1000
        app.io.launch {
            val id = app.db.practice().add(com.betterappmafia.apophenia.data.PracticeEntity(startedAt = t.firstStart ?: now, seconds = secs, scrapId = t.scrapId, projectId = t.projectId, exercise = t.exercise, note = note))
            model.notice(if (id == null) "Under 5 seconds: not logged." else "Logged ${clockSeconds(secs)}.")
        }
        changeTimer(PracticeTimer(scrapId = t.scrapId, projectId = t.projectId, exercise = t.exercise, label = t.label))
    }

    // ---- Isolator ----

    fun changeIsolator(s: com.betterappmafia.apophenia.core.tools.IsolatorSettings) {
        isolator = s
        audio.setIsolator(s)
        put(K_ISOLATOR, s.encode())
    }

    // ---- Tuner ----

    val tunerRange: TunerRange
        get() = when (tunerRangeId) {
            TunerRange.ANY.id -> TunerRange.ANY
            TunerRange.VOICE.id -> TunerRange.VOICE
            else -> Tunings.all.firstOrNull { it.id == tunerRangeId }?.let(TunerRange::of) ?: TunerRange.ANY
        }

    fun setTuner(s: TunerSetup, rangeId: String = tunerRangeId) {
        tuner = s
        tunerRangeId = rangeId
        put(K_TUNER, listOf(s.a4, s.temperament.name, s.tonic, s.key?.token ?: "-", s.octaveLabel.name, rangeId).joinToString("|"))
        if (audio.drone.value) audio.setDrone(true, drone.frequencies(s))   // the drone follows A4 and the temperament
    }

    private fun decodeTuner(v: String?) = runCatching {
        val f = v!!.split('|')
        tuner = TunerSetup(
            a4 = f[0].toDouble(), temperament = Temperament.valueOf(f[1]), tonic = PitchName.parse(f[2]),
            key = if (f[3] == "-") null else KeyMode.parseOrNull(f[3]), octaveLabel = OctaveLabel.valueOf(f[4]),
        )
        tunerRangeId = f[5]
    }

    /** Opens the mic for the tuner and reads it 20 times a second until [stopTuner]. */
    fun startTuner() {
        audio.setTuner(true)
        if (tunerJob?.isActive == true) return
        tunerJob = app.io.launch(Dispatchers.Default) {
            val recent = ArrayDeque<Double>()
            var quiet = 0
            while (isActive) {
                delay(50)
                val buf = audio.readInput(4096) ?: continue
                val r = tunerRange
                val e = Yin.estimate(buf, audio.sampleRate, r.minHz, r.maxHz)
                if (e == null) {
                    if (++quiet > 8) { reading = null; recent.clear() }   // hold the last note for ~0.4 s
                    continue
                }
                quiet = 0
                // Median of the last five estimates on the same note, so one stray frame doesn't jump the needle.
                if (recent.isNotEmpty() && abs(1200 * ln(e.hz / recent.last()) / ln(2.0)) > 60) recent.clear()
                recent.addLast(e.hz)
                while (recent.size > 5) recent.removeFirst()
                val hz = recent.sorted()[recent.size / 2]
                reading = tuner.read(hz)
            }
        }
    }

    fun stopTuner() {
        tunerJob?.cancel()
        tunerJob = null
        reading = null
        audio.setTuner(false)
    }

    // ---- Drone ----

    fun changeDrone(d: DroneSettings) {
        drone = d
        put(K_DRONE, listOf(d.root, d.octave, d.addOctave, d.fifth, d.third.name).joinToString("|"))
        if (audio.drone.value) audio.setDrone(true, d.frequencies(tuner))
    }

    fun toggleDrone() = audio.setDrone(!audio.drone.value, drone.frequencies(tuner))

    private fun decodeDrone(v: String?) = runCatching {
        val f = v!!.split('|')
        drone = DroneSettings(PitchName.parse(f[0]), f[1].toInt(), f[2].toBoolean(), f[3].toBoolean(), DroneThird.valueOf(f[4]))
    }

    // ---- Top-bar slots ----

    fun pin(slot: Int, t: Tool) {
        val p = pins.toMutableList()
        p[slot] = t
        pins = p
        put(K_PINS, p.joinToString(",") { it.name })
    }

    /** What a slot does from the top bar: the drone toggles in place, the others open their screen. */
    fun runPinned(t: Tool) {
        when (t) {
            Tool.DRONE -> toggleDrone()
            Tool.TUNER -> open(ToolScreen.TUNER)
            Tool.METRONOME -> open(ToolScreen.METRONOME)
            Tool.SPEED -> open(ToolScreen.SPEED)
            Tool.ISOLATOR -> open(ToolScreen.ISOLATOR)
            Tool.PRACTICE -> open(ToolScreen.PRACTICE)
        }
    }

    fun open(s: ToolScreen) {
        model.go(Mode.TOOLS)
        screen = s
    }

    private fun put(key: String, value: String) = app.io.launch { app.db.settings().put(AppSettingEntity(key, value)) }

    companion object {
        const val K_METRONOME = "tools.metronome"
        const val K_TUNER = "tools.tuner"
        const val K_DRONE = "tools.drone"
        const val K_PINS = "tools.pins"
        const val K_VOLUME = "tools.click_volume"
        const val K_ISOLATOR = "tools.isolator"
        const val K_TIMER = "tools.practice_timer"
    }
}
