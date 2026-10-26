package com.betterappmafia.apophenia.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.core.content.ContextCompat
import com.betterappmafia.apophenia.Breadcrumbs
import com.betterappmafia.apophenia.capture.RecordingService
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The mic's real state, always on screen (plan §7 Mic control). No Listening: there is no pre-roll (Christopher, 22 Oct 2026). */
enum class Mic { OFF, RECORDING }

/** The metronome as the UI shows it: [bar] is negative while counting in, [beat] -1 before the first, [bpmNow] includes the trainer. */
data class ClickState(val on: Boolean = false, val bpm: Double = 96.0, val beats: Long = 0, val bar: Long = 0, val beat: Int = -1, val bpmNow: Double = 96.0)

/** What the persistent player holds: one take of one scrap. */
data class Loaded(
    val scrapId: Long, val takeId: Long, val title: String, val path: String, val frames: Long, val rate: Int, val position: Long, val playing: Boolean,
    /** Speed, pitch and loop it plays with (plan §7 Playback speed); [passes] and [speedNow] change as a step-up loop runs. */
    val playback: Playback = Playback(), val passes: Int = 0, val speedNow: Double = 1.0,
)

/** What a finished recording left on disk, ready to be saved as a take. */
data class Recorded(
    val file: File, val frames: Long, val prerollFrames: Long, val lostFrames: Long, val diskError: Boolean, val rate: Int, val clickBpm: Double?,
    /** Where the stack was (frames into its pass) when ● REC was pressed while it played, else null. */
    val stackPosAtStart: Long? = null,
    /** The click grid heard during the take, as ClickGrid JSON (plan §7), or null if the click was off. */
    val metronomeGrid: String? = null,
    /** Markers set while recording (plan §7 Recorder), in the take file's frames. */
    val marks: List<Long> = emptyList(),
)

/** The stack in the persistent player (plan §5). */
data class StackLoaded(val stackId: Long, val title: String, val length: Long, val position: Long, val playing: Boolean, val layers: Int)

/**
 * Owns [NativeEngine]: every engine call runs on one worker thread, in order. The UI reads
 * StateFlows and calls the suspend/fire-and-forget functions here; nothing blocks the UI thread.
 */
class AudioController(private val context: Context, private val audioDir: File) {
    private val dispatcher = Executors.newSingleThreadExecutor { Thread(it, "audio-control") }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _mic = MutableStateFlow(Mic.OFF)
    val mic: StateFlow<Mic> = _mic.asStateFlow()
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()
    private val _recFrames = MutableStateFlow(0L)
    val recFrames: StateFlow<Long> = _recFrames.asStateFlow()
    private val _click = MutableStateFlow(ClickState())
    val click: StateFlow<ClickState> = _click.asStateFlow()
    private val _player = MutableStateFlow<Loaded?>(null)
    val player: StateFlow<Loaded?> = _player.asStateFlow()

    private val _stack = MutableStateFlow<StackLoaded?>(null)
    val stack: StateFlow<StackLoaded?> = _stack.asStateFlow()
    private var stackPosAtStart: Long? = null

    private var recording: Pair<File, File>? = null
    private var poller: Job? = null
    val sampleRate = 48000

    /** Called (on the audio worker) when the system takes the mic mid-take; the app stops and saves what arrived. */
    var onRecordingInterrupted: (() -> Unit)? = null
    /** Called when something else stops sound: another app took audio focus, or headphones came out. */
    var onPausedBySystem: ((String) -> Unit)? = null
    private var interruptReported = false
    private var serviceRunning = false

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pauseBySystem("Paused: another app is playing.")
        }
        .build()

    init {
        scope.launch {
            val unprocessed = audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
            NativeEngine.setInputPreset(if (unprocessed) NativeEngine.PRESET_UNPROCESSED else NativeEngine.PRESET_VOICE_RECOGNITION)
        }
        // Headphones out: stop before the speaker blares (Android's "becoming noisy").
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { pauseBySystem("Paused: headphones disconnected.") }
        }, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun pauseBySystem(why: String) = scope.launch {
        var stopped = false
        _player.value?.let { if (it.playing) { NativeEngine.setPaused(true); _player.value = it.copy(playing = false); stopped = true } }
        if (_click.value.on) { NativeEngine.setClick(false, _click.value.bpm); _click.value = _click.value.copy(on = false); stopped = true }
        if (_drone.value) { NativeEngine.setDrone(false, FloatArray(0)); _drone.value = false; stopped = true }
        _stack.value?.let { if (it.playing) { NativeEngine.stackPlay(false); _stack.value = it.copy(playing = false); stopped = true } }
        if (stopped) { Breadcrumbs.add(why); onPausedBySystem?.invoke(why) }
    }

    private fun gainFocus() { audioManager.requestAudioFocus(focusRequest) }

    /** The .part file being written right now, so launch recovery never touches it. */
    @Volatile var activePart: File? = null
        private set

    /** Starts a take in the audio folder. Returns false if the mic or the file couldn't be opened. */
    suspend fun startRecording(): Boolean = withContext(dispatcher) {
        if (recording != null) return@withContext false
        audioDir.mkdirs()
        val name = "take-${System.currentTimeMillis()}.wav"
        val final = File(audioDir, name)
        val part = File(audioDir, "$name.part")
        activePart = part
        NativeEngine.setInputDevice(preferredInput())
        val preroll = NativeEngine.startRecording(part.absolutePath, final.absolutePath)
        if (preroll < 0) {
            activePart = null
            Breadcrumbs.add("record start failed")
            return@withContext false
        }
        // Only now: a service started and then stopped before startForeground() crashes the app.
        val service = runCatching { ContextCompat.startForegroundService(context, RecordingService.intent(context)) }
        if (service.isFailure) Breadcrumbs.add("foreground service refused: ${service.exceptionOrNull()}")
        serviceRunning = service.isSuccess
        recording = part to final
        stackPosAtStart = _stack.value?.takeIf { it.playing }?.let { NativeEngine.stackState().position }
        interruptReported = false
        _recFrames.value = preroll
        _mic.value = Mic.RECORDING
        Breadcrumbs.add("record start")
        ensurePolling()
        true
    }

    /** Stops, waits for the file to be finalized, and returns what was written. */
    suspend fun stopRecording(): Recorded? = withContext(dispatcher) {
        val (_, final) = recording ?: return@withContext null
        val st = NativeEngine.stopRecording()
        recording = null
        activePart = null
        // Queued behind the start command, so the service has always reached startForeground() first.
        if (serviceRunning) runCatching { context.startService(RecordingService.stopIntent(context)) }
        serviceRunning = false
        _mic.value = Mic.OFF
        Breadcrumbs.add("record stop frames=${st?.framesWritten} lost=${st?.lostFrames}")
        val click = _click.value
        val g = NativeEngine.lastGrid()
        val grid = if (g.frames.isEmpty()) null else com.betterappmafia.apophenia.core.tools.ClickGrid(
            sampleRate, metronome.clampedBpm, metronome.meter.toString(), g.frames.toList(),
            g.flags.indices.filter { g.flags[it] and 2 != 0 }.toSet(), g.flags.indices.filter { g.flags[it] and 4 != 0 }.toSet(), g.aligned,
        ).toJson()
        val marks = recMarks.toList()
        recMarks.clear()
        _markCount.value = 0
        st?.let { Recorded(final, it.framesWritten, it.prerollFrames, it.lostFrames, it.diskError, sampleRate, if (click.on || grid != null) metronome.clampedBpm else null, stackPosAtStart, grid, marks.filter { m -> m < it.framesWritten }) }
    }

    private val recMarks = java.util.concurrent.CopyOnWriteArrayList<Long>()
    private val _markCount = MutableStateFlow(0)
    /** Markers set in the take being recorded. */
    val markCount: StateFlow<Int> = _markCount.asStateFlow()

    /** Drops a marker at the newest recorded frame (plan §7 Recorder: markers while recording). */
    fun markRecording() = scope.launch {
        if (_mic.value != Mic.RECORDING) return@launch
        NativeEngine.recordingState()?.let { recMarks.add(it.framesWritten); _markCount.value = recMarks.size }
    }

    /** The full metronome's settings (plan §7); the top bar's − / + and Tap change only the BPM. */
    @Volatile var metronome = com.betterappmafia.apophenia.core.tools.MetronomeSettings()
        private set

    fun setClick(on: Boolean, bpm: Double = _click.value.bpm) = scope.launch {
        val b = bpm.coerceIn(20.0, 400.0)
        if (on) gainFocus()
        metronome = metronome.copy(bpm = b)
        NativeEngine.setClick(on, b)
        _click.value = _click.value.copy(on = on, bpm = b, bpmNow = if (on) _click.value.bpmNow else b)
        onMetronomeChanged?.invoke(metronome)
        ensurePolling()
    }

    /** Called (on the audio worker) whenever the metronome's settings change, so they can be saved. */
    var onMetronomeChanged: ((com.betterappmafia.apophenia.core.tools.MetronomeSettings) -> Unit)? = null

    /** Sends the whole metronome (meter, accents, subdivision, swing, poly, count-in, trainer, silent bars) to the engine. */
    fun setMetronome(s: com.betterappmafia.apophenia.core.tools.MetronomeSettings) = scope.launch {
        metronome = s
        fun bar(p: com.betterappmafia.apophenia.core.tools.BarProgram) = NativeEngine.Bar(
            p.units, p.events.map { it.at }.toDoubleArray(), p.events.map { it.sound.id }.toIntArray(),
            p.events.map { it.gain }.toFloatArray(), p.events.map { it.beat }.toIntArray(),
        )
        val t = s.trainer
        NativeEngine.clickProgram(bar(s.bar()), bar(s.countInBar()), s.countInBars, t?.stepBpm ?: 0, t?.everyBars ?: 1, t?.maxBpm ?: 0, s.silent?.playBars ?: 0, s.silent?.muteBars ?: 0)
        NativeEngine.setClick(_click.value.on, s.clampedBpm)
        _click.value = _click.value.copy(bpm = s.clampedBpm)
        onMetronomeChanged?.invoke(s)
    }

    fun setClickVolume(v: Float) = scope.launch { NativeEngine.setClickVolume(v) }

    private val _drone = MutableStateFlow(false)
    val drone: StateFlow<Boolean> = _drone.asStateFlow()
    private var droneHz = FloatArray(0)

    /** The drone (plan §7): [hz] are its notes, already tuned; off fades out. */
    fun setDrone(on: Boolean, hz: List<Double>) = scope.launch {
        if (on) gainFocus()
        droneHz = hz.map { it.toFloat() }.toFloatArray()
        NativeEngine.setDrone(on, droneHz)
        _drone.value = on
        ensurePolling()
    }

    private val _tuner = MutableStateFlow(false)
    /** True while the tuner's screen holds the mic open (no pre-roll). */
    val tuner: StateFlow<Boolean> = _tuner.asStateFlow()

    fun setTuner(on: Boolean) = scope.launch {
        if (on) NativeEngine.setInputDevice(preferredInput())
        val ok = NativeEngine.setTuner(on)
        _tuner.value = on && ok
        ensurePolling()
    }

    /** The newest [n] mic frames for the tuner, or null if fewer arrived yet. */
    suspend fun readInput(n: Int): FloatArray? = withContext(dispatcher) {
        if (!_tuner.value) return@withContext null
        val buf = FloatArray(n)
        if (NativeEngine.readInput(buf) < n) null else buf
    }

    fun load(scrapId: Long, takeId: Long, title: String, path: String, play: Boolean, startFrame: Long = 0, playback: Playback = Playback()) = scope.launch {
        if (play) gainFocus()
        val frames = NativeEngine.play(path, startFrame, play, playback)
        if (frames < 0) { _player.value = null; Breadcrumbs.add("load failed $path"); return@launch }
        _player.value = Loaded(scrapId, takeId, title, path, frames, sampleRate, startFrame, play, playback, 0, playback.speed)
        ensurePolling()
    }

    /** The player's position right now, read from the engine (frames of the loaded file): for timing taps against it. */
    fun playerFrame(): Long = NativeEngine.playerState().position

    fun togglePlay() = scope.launch {
        val p = _player.value ?: return@launch
        if (p.playing) {
            NativeEngine.setPaused(true)
            _player.value = p.copy(playing = false)
        } else {
            gainFocus()
            val st = NativeEngine.playerState()
            if (!st.loaded || st.ended) {
                // Played to the end: start again from the top.
                NativeEngine.play(p.path, if (p.playback.hasLoop) p.playback.loopA else 0, true, p.playback)
                _player.value = p.copy(position = if (p.playback.hasLoop) p.playback.loopA else 0, playing = true, passes = 0)
            } else {
                NativeEngine.setPaused(false)
                _player.value = p.copy(playing = true)
            }
        }
        ensurePolling()
    }

    fun seek(fraction: Float, play: Boolean = true) {
        val p = _player.value ?: return
        load(p.scrapId, p.takeId, p.title, p.path, play, (p.frames * fraction.coerceIn(0f, 1f)).toLong(), p.playback)
    }

    /** Changes speed, pitch or the loop of the take in the player; it carries on from where it is (crossfaded). */
    fun setPlayback(pb: Playback) = scope.launch {
        val p = _player.value ?: return@launch
        val pos = NativeEngine.playerState().position
        if (p.playing) gainFocus()
        if (NativeEngine.play(p.path, pos, p.playing, pb) < 0) return@launch
        _player.value = p.copy(playback = pb, position = pos, passes = 0, speedNow = pb.speed)
    }

    /** The isolator's controls (plan §7); live for a take loaded with isolate on. */
    fun setIsolator(s: com.betterappmafia.apophenia.core.tools.IsolatorSettings) = scope.launch {
        NativeEngine.setIsolator(s.panMode.ordinal, s.panCenter.toFloat(), s.panWidth.toFloat(), s.toneHit.toFloat(), s.lowCut.toFloat(), s.highCut.toFloat())
    }

    /** [n] frames of [path] from [from], through the isolator when [isolate], for the note finder; null if unreadable. */
    suspend fun isolatedWindow(path: String, from: Long, n: Int, isolate: Boolean): FloatArray? = withContext(dispatcher) {
        val buf = FloatArray(n)
        if (NativeEngine.isolatedWindow(path, from, isolate, buf)) buf else null
    }

    fun unload() = scope.launch {
        NativeEngine.unload()
        _player.value = null
    }

    /**
     * Puts a stack in the player, or updates the one there (layers unchanged keep playing without a
     * click). Decoding new layers happens here, on the audio worker.
     */
    fun setStack(stackId: Long, title: String, layers: List<NativeEngine.StackLayer>) = scope.launch {
        val current = _stack.value
        if (current != null && current.stackId != stackId) NativeEngine.stackPlay(false)
        val length = NativeEngine.stackSet(layers)
        if (length < 0) { Breadcrumbs.add("stack set failed"); return@launch }
        if (current == null || current.stackId != stackId) NativeEngine.stackSeek(0)
        val st = NativeEngine.stackState()
        _stack.value = StackLoaded(stackId, title, st.length, st.position, st.playing, st.layers)
        ensurePolling()
    }

    fun stackToggle() = scope.launch {
        val s = _stack.value ?: return@launch
        val play = !s.playing
        if (play) gainFocus()
        NativeEngine.stackPlay(play)
        _stack.value = s.copy(playing = NativeEngine.stackState().playing)
        ensurePolling()
    }

    fun stackSeek(fraction: Float) = scope.launch {
        val s = _stack.value ?: return@launch
        NativeEngine.stackSeek((s.length * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun unloadStack() = scope.launch {
        NativeEngine.stackPlay(false)
        NativeEngine.stackSet(emptyList())
        _stack.value = null
    }

    /** The stack's ●: one pass of its own output, no mic, written as a new take file. Returns it with its peak, or null. */
    /** The loaded stack's mix ([layer] 0) or one layer's stem into [file] (for exports, not a take). Frames written, or null. */
    suspend fun bounceTo(file: File, layer: Long = 0): Long? = withContext(dispatcher) {
        val part = File(file.path + ".part")
        NativeEngine.bounceStack(part.absolutePath, file.absolutePath, layer)?.first
    }

    suspend fun bounceStack(): Pair<Recorded, Float>? = withContext(dispatcher) {
        audioDir.mkdirs()
        val name = "take-${System.currentTimeMillis()}.wav"
        val final = File(audioDir, name)
        val part = File(audioDir, "$name.part")
        activePart = part
        val r = NativeEngine.bounceStack(part.absolutePath, final.absolutePath)
        activePart = null
        Breadcrumbs.add("bounce ${r?.first}")
        r?.let { (frames, peak) -> Recorded(final, frames, 0, 0, false, sampleRate, null) to peak }
    }

    /**
     * The output route right now, as calibrations are kept (plan §5.4: per route). Bluetooth wins
     * over wired, which wins over the speaker, the order Android routes media in.
     */
    fun route(): Route {
        val types = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }.toSet()
        return when {
            types.any { it == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET || it == android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER } -> Route.BLUETOOTH
            types.any { it == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET || it == android.media.AudioDeviceInfo.TYPE_USB_HEADSET } -> Route.WIRED
            else -> Route.SPEAKER
        }
    }

    enum class Route(val label: String) { SPEAKER("Phone speaker"), WIRED("Wired / USB headphones"), BLUETOOTH("Bluetooth") }

    /** The headphones sound goes to now, by the name Android gives them (null on the speaker). */
    fun headphonesName(): String? {
        val outs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bt = setOf(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, android.media.AudioDeviceInfo.TYPE_BLE_HEADSET, android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER)
        val wired = setOf(android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES, android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET, android.media.AudioDeviceInfo.TYPE_USB_HEADSET)
        val d = outs.firstOrNull { it.type in bt } ?: outs.firstOrNull { it.type in wired } ?: return null
        return d.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * The mic a take is recorded from (Christopher, 26 Oct 2026: you hear the instrument in the room; the phone records
     * it). With headphones as the output it's the phone's own mic: a Bluetooth headset's mic would put Android into
     * phone-call audio (low quality, more delay) and a wired headset's in-line mic is a poor instrument mic. A USB audio
     * interface plugged in is left to Android, as you'd plug one in to record through it. On the speaker, Android chooses.
     */
    private fun preferredInput(): Int {
        if (route() == Route.SPEAKER) return 0
        val ins = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        if (ins.any { it.type == android.media.AudioDeviceInfo.TYPE_USB_DEVICE }) return 0
        return ins.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC }?.id ?: 0
    }

    /**
     * Overdub latency by playing along ([com.betterappmafia.apophenia.core.analysis.PlayAlong]): plain clicks at 80 BPM
     * in the headphones while the phone's mic records you clapping with them; each clap's distance from its click, in
     * the frame counts a layer over a stack is placed by. The metronome is put back as it was, and the recording deleted.
     */
    suspend fun playAlong(): Pair<String?, com.betterappmafia.apophenia.core.analysis.PlayAlong.Result> = withContext(dispatcher) {
        val pa = com.betterappmafia.apophenia.core.analysis.PlayAlong
        val name = headphonesName()
        if (_mic.value != Mic.OFF) return@withContext name to com.betterappmafia.apophenia.core.analysis.PlayAlong.Result(null, null, 0, "Turn the mic off first.")
        _player.value?.let { _player.value = it.copy(playing = false) }
        _stack.value?.let { _stack.value = it.copy(playing = false) }
        val savedMetronome = metronome
        val savedClick = _click.value.on
        setMetronome(com.betterappmafia.apophenia.core.tools.MetronomeSettings(bpm = pa.BPM)).join()
        if (!startRecording()) {
            setMetronome(savedMetronome).join(); setClick(savedClick, savedMetronome.clampedBpm).join()
            return@withContext name to com.betterappmafia.apophenia.core.analysis.PlayAlong.Result(null, null, 0, "The mic couldn't open.")
        }
        setClick(true, pa.BPM).join()
        delay(((pa.CLICKS + 1) * 60.0 / pa.BPM * 1000).toLong())
        val rec = stopRecording()
        val counted = NativeEngine.lastGridCounted()
        setClick(false).join()
        setMetronome(savedMetronome).join()
        if (savedClick) setClick(true, savedMetronome.clampedBpm).join()
        val file = rec?.file ?: return@withContext name to com.betterappmafia.apophenia.core.analysis.PlayAlong.Result(null, null, 0, "Nothing was recorded.")
        val result = runCatching {
            val (x, rate) = Wav.readMono(file, com.betterappmafia.apophenia.core.analysis.TakeAnalysis.RATE, 60) ?: error("unreadable")
            val claps = com.betterappmafia.apophenia.core.analysis.Onsets.pick(com.betterappmafia.apophenia.core.analysis.Onsets.envelope(x, rate))
            pa.measure(counted.map { it.toDouble() / sampleRate }, claps)
        }.getOrElse { com.betterappmafia.apophenia.core.analysis.PlayAlong.Result(null, null, 0, "The recording couldn't be read.") }
        file.delete()
        Breadcrumbs.add("play-along ${route()} '$name' ms=${result.ms} spread=${result.spreadMs} used=${result.used} clicks=${counted.size}")
        name to result
    }

    data class Calibration(val route: Route, val summary: com.betterappmafia.apophenia.audio.lab.LatencyAnalysis.Summary?, val problem: String?)

    /**
     * Measures the round trip on the current route (plan §5.4): five noise bursts out, the mic hears
     * them, cross-correlation finds the lag (the Device Lab's method). Everything sounding stops first
     * and the engine's output closes, so the two never share the device. Needs the mic Off.
     */
    suspend fun calibrate(): Calibration = withContext(dispatcher) {
        val route = route()
        if (_mic.value != Mic.OFF) return@withContext Calibration(route, null, "Turn the mic off first.")
        _player.value?.let { _player.value = it.copy(playing = false) }
        _stack.value?.let { _stack.value = it.copy(playing = false) }
        if (_click.value.on) _click.value = _click.value.copy(on = false)
        NativeEngine.releaseOutput()
        val burst = com.betterappmafia.apophenia.audio.lab.LatencyAnalysis.burst()
        val unprocessed = audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val preset = if (unprocessed) com.betterappmafia.apophenia.audio.lab.LabEngine.InputPreset.Unprocessed else com.betterappmafia.apophenia.audio.lab.LabEngine.InputPreset.VoiceRecognition
        gainFocus()
        com.betterappmafia.apophenia.audio.lab.LabEngine.runLatency(burst, startSeconds = 0.5f, spacingSeconds = 1.2f, count = 5, preset = preset).fold(
            onSuccess = { cap ->
                val maxLag = (cap.sampleRate * 1.1).toInt()
                val ds = cap.burstStarts.map { com.betterappmafia.apophenia.audio.lab.LatencyAnalysis.detect(cap.samples, it, burst, maxLag) }
                val s = com.betterappmafia.apophenia.audio.lab.LatencyAnalysis.summarize(ds, cap.sampleRate)
                Breadcrumbs.add("calibrate $route median=${s.medianMs} spread=${s.halfSpreadMs}")
                when {
                    cap.disconnected -> Calibration(route, null, "The route changed during the measurement. Try again.")
                    s.medianMs == null -> Calibration(route, s, "The bursts weren't heard. Turn the volume up, and for headphones hold an earbud against the phone's mic.")
                    else -> Calibration(route, s, null)
                }
            },
            onFailure = { Calibration(route, null, "The streams couldn't open: ${it.message}") },
        )
    }

    /** The app left the screen: the tuner lets go of the mic; recording carries on. */
    fun onBackground() = scope.launch {
        if (_tuner.value) { NativeEngine.setTuner(false); _tuner.value = false }
        NativeEngine.idle()
    }

    private fun ensurePolling() {
        if (poller?.isActive == true) return
        poller = scope.launch {
            var level = 0f
            while (isActive) {
                val p = _player.value
                val s = _stack.value
                val active = _mic.value != Mic.OFF || _click.value.on || (p?.playing == true) || (s?.playing == true) || _tuner.value || _drone.value
                if (!active) { _level.value = 0f; break }
                if (_mic.value != Mic.OFF || _tuner.value) {
                    level = maxOf(NativeEngine.takePeak(), level * 0.85f)
                    _level.value = level
                }
                if (_mic.value == Mic.RECORDING) NativeEngine.recordingState()?.let { _recFrames.value = it.framesWritten }
                if (_mic.value == Mic.RECORDING && !interruptReported && NativeEngine.inputLost()) {
                    interruptReported = true
                    Breadcrumbs.add("input lost while recording")
                    onRecordingInterrupted?.invoke()
                }
                if (NativeEngine.outputLost()) {
                    // Route change: reopen for the click; a playing take pauses where it was (press play to go on).
                    if (_click.value.on) NativeEngine.setClick(true, _click.value.bpm)
                    if (_drone.value) NativeEngine.setDrone(true, droneHz)
                    _player.value?.let { if (it.playing) { NativeEngine.setPaused(true); _player.value = it.copy(playing = false) } }
                    _stack.value?.let { if (it.playing) { NativeEngine.stackPlay(false); _stack.value = it.copy(playing = false) } }
                }
                if (_click.value.on) {
                    val c = NativeEngine.clickState()
                    if (c.beats != _click.value.beats || c.bar != _click.value.bar) _click.value = _click.value.copy(beats = c.beats, bar = c.bar, beat = c.beat, bpmNow = c.bpm)
                }
                if (p != null) {
                    val st = NativeEngine.playerState()
                    _player.value = p.copy(position = st.position, playing = p.playing && !st.ended, passes = st.passes, speedNow = st.speedNow)
                }
                if (s != null) {
                    val st = NativeEngine.stackState()
                    _stack.value = s.copy(position = st.position, length = st.length, playing = st.playing)
                }
                delay(33)
            }
        }
    }
}
