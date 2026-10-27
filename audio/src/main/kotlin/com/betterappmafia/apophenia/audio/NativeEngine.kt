package com.betterappmafia.apophenia.audio

/**
 * JNI surface of `engine.cpp`. Every call must come from one worker thread (the app's audio
 * controller), never the UI thread: some calls open streams or join threads.
 */
object NativeEngine {
    init {
        System.loadLibrary("apophenia_audio")
    }

    /** Oboe's numbers for the input presets the app uses. */
    const val PRESET_VOICE_RECOGNITION = 6
    const val PRESET_UNPROCESSED = 9

    val sampleRate: Int get() = nativeSampleRate()

    fun setInputPreset(preset: Int) = nativeSetInputPreset(preset)
    fun setClick(on: Boolean, bpm: Double) = nativeSetClick(on, bpm)
    fun beats(): Long = nativeBeats()

    /** One bar for the metronome: event times in BPM units, sounds (core ClickSound ids), gains, beat indexes (-1 = poly). */
    class Bar(val units: Double, val at: DoubleArray, val sound: IntArray, val gain: FloatArray, val beat: IntArray)

    fun clickProgram(bar: Bar, countIn: Bar, countInBars: Int, trainerStep: Int, trainerEvery: Int, trainerMax: Int, silentPlay: Int, silentMute: Int) =
        nativeClickProgram(
            bar.units, bar.at, bar.sound, bar.gain, bar.beat, countIn.units, countIn.at, countIn.sound, countIn.gain, countIn.beat,
            countInBars, trainerStep, trainerEvery, trainerMax, silentPlay, silentMute,
        )

    /** Where the metronome is: beats played, bar (negative while counting in), beat in the bar (-1 before the first), current BPM. */
    data class ClickPosition(val beats: Long, val bar: Long, val beat: Int, val bpm: Double)

    fun clickState(): ClickPosition {
        val s = LongArray(4)
        nativeClickState(s)
        return ClickPosition(s[0], s[1], s[2].toInt(), s[3] / 1000.0)
    }

    fun setClickVolume(v: Float) = nativeSetClickVolume(v)

    /** The clicks heard during the take just stopped, in its frames. */
    data class Grid(val aligned: Boolean, val frames: LongArray, val flags: IntArray)

    fun lastGrid(): Grid {
        val a = nativeLastGrid()
        if (a.size < 2) return Grid(false, LongArray(0), IntArray(0))
        val n = a[1].toInt()
        return Grid(a[0] == 1L, LongArray(n) { a[2 + 2 * it] }, IntArray(n) { a[3 + 2 * it].toInt() })
    }

    /** The clicks of the take just stopped placed by frame counts only, as a layer recorded over a stack is placed. */
    fun lastGridCounted(): LongArray = nativeLastGridCounted()

    /** The mic to open next, an AudioDeviceInfo id (0: Android chooses); takes effect when the input next opens. */
    fun setInputDevice(id: Int) = nativeSetInputDevice(id)
    private external fun nativeLastGridCounted(): LongArray
    private external fun nativeSetInputDevice(id: Int)

    fun setDrone(on: Boolean, hz: FloatArray) = nativeSetDrone(on, hz)

    /** Plays [pcm] (mono, at [sampleRate], two seconds at most) once beside the player, which it leaves alone. */
    fun cue(pcm: FloatArray) = nativeCue(pcm)
    private external fun nativeCue(pcm: FloatArray)

    /** Isolator settings (live): panMode 0 off / 1 keep / 2 remove; toneHit -1..1; cuts in Hz, 0 = off. */
    fun setIsolator(panMode: Int, panCenter: Float, panWidth: Float, toneHit: Float, lowCut: Float, highCut: Float) =
        nativeSetIsolator(panMode, panCenter, panWidth, toneHit, lowCut, highCut)

    /** dst.size frames of the take from [from], isolated or plain, for the note finder. */
    fun isolatedWindow(path: String, from: Long, isolate: Boolean, dst: FloatArray): Boolean = nativeIsolatedWindow(path, from, isolate, dst)
    private external fun nativeSetIsolator(panMode: Int, panCenter: Float, panWidth: Float, toneHit: Float, lowCut: Float, highCut: Float)
    private external fun nativeIsolatedWindow(path: String, from: Long, isolate: Boolean, dst: FloatArray): Boolean
    fun setTuner(on: Boolean): Boolean = nativeSetTuner(on)
    /** Fills [dst] with the newest mic frames; returns how many are real. */
    fun readInput(dst: FloatArray): Int = nativeReadInput(dst)

    private external fun nativeClickProgram(
        units: Double, at: DoubleArray, sound: IntArray, gain: FloatArray, beat: IntArray,
        cUnits: Double, cAt: DoubleArray, cSound: IntArray, cGain: FloatArray, cBeat: IntArray,
        countInBars: Int, trStep: Int, trEvery: Int, trMax: Int, silPlay: Int, silMute: Int,
    )
    private external fun nativeClickState(out: LongArray)
    private external fun nativeSetClickVolume(v: Float)
    private external fun nativeLastGrid(): LongArray
    private external fun nativeSetDrone(on: Boolean, hz: FloatArray)
    private external fun nativeSetTuner(on: Boolean): Boolean
    private external fun nativeReadInput(dst: FloatArray): Int

    /** Loads [path] at [startFrame]; returns its length in frames, or -1 if it can't be opened. */
    fun play(path: String, startFrame: Long, play: Boolean, pb: Playback = Playback()): Long = nativePlay(
        path, startFrame, play, pb.speed, pb.transpose, pb.loopA, pb.loopB, pb.stepBy, pb.stepTo, pb.countInBeats, pb.beatFrames, pb.isolate,
    )
    fun setPaused(paused: Boolean) = nativeSetPaused(paused)
    fun unload() = nativeUnload()

    fun playerState(): PlayerState {
        val s = LongArray(8)
        nativePlayerState(s)
        return PlayerState(position = s[0], frames = s[1], rate = s[2].toInt(), ended = s[3] != 0L, paused = s[4] != 0L, loaded = s[5] != 0L, passes = s[6].toInt(), speedNow = s[7] / 1000.0)
    }


    /** Highest absolute input sample since the last call, 0..1. */
    fun takePeak(): Float = nativeTakePeak()

    /** Returns the pre-roll frames written at the start of the file, or -1 on failure. */
    fun startRecording(partPath: String, finalPath: String): Long = nativeStartRecording(partPath, finalPath)

    fun recordingState(): RecordingState? {
        val s = LongArray(5)
        return if (nativeRecordingState(s)) RecordingState.of(s) else null
    }

    /** Blocks until the file is finalized and renamed. */
    fun stopRecording(): RecordingState? {
        val s = LongArray(5)
        return if (nativeStopRecording(s)) RecordingState.of(s) else null
    }

    /** True once the system closed the output (headphones out, route change); the next play reopens it. */
    fun outputLost(): Boolean = nativeOutputLost()
    /** True once the system closed the microphone stream (a call, another app). */
    fun inputLost(): Boolean = nativeInputLost()

    /** Lets the engine close the output stream if nothing is sounding. */
    fun idle() = nativeIdle()

    /** One stack layer as the engine plays it. [to] < 0 = to the end of the take; [gain] is linear. */
    data class StackLayer(
        val id: Long, val path: String, val mode: Int, val start: Long, val from: Long, val to: Long,
        val gain: Float, val mute: Boolean, val solo: Boolean,
    )

    /** Replaces the stack's layers (unchanged ones keep playing without a click). Returns the stack length in frames, or -1. */
    fun stackSet(layers: List<StackLayer>): Long = nativeStackSet(
        LongArray(layers.size) { layers[it].id }, Array(layers.size) { layers[it].path }, IntArray(layers.size) { layers[it].mode },
        LongArray(layers.size) { layers[it].start }, LongArray(layers.size) { layers[it].from }, LongArray(layers.size) { layers[it].to },
        FloatArray(layers.size) { layers[it].gain }, BooleanArray(layers.size) { layers[it].mute }, BooleanArray(layers.size) { layers[it].solo },
    )
    fun stackPlay(play: Boolean) = nativeStackPlay(play)
    fun stackSeek(frame: Long) = nativeStackSeek(frame)
    fun stackState(): StackState {
        val s = LongArray(4)
        nativeStackState(s)
        return StackState(position = s[0], length = s[1], playing = s[2] != 0L, layers = s[3].toInt())
    }

    /** Renders one pass of the stack to [partPath], renamed to [finalPath]. Returns frames and the peak (above 1 = it clipped), or null. */
    /** The stack's mix, or with [onlyLayer] that layer alone (a stem), over the whole stack length. */
    fun bounceStack(partPath: String, finalPath: String, onlyLayer: Long = 0): Pair<Long, Float>? {
        val peak = FloatArray(1)
        val n = nativeBounceStack(partPath, finalPath, peak, onlyLayer)
        return if (n > 0) n to peak[0] else null
    }

    private external fun nativeStackSet(
        ids: LongArray, paths: Array<String>, modes: IntArray, starts: LongArray, froms: LongArray, tos: LongArray,
        gains: FloatArray, mutes: BooleanArray, solos: BooleanArray,
    ): Long
    private external fun nativeStackPlay(play: Boolean)
    private external fun nativeStackSeek(frame: Long)
    private external fun nativeStackState(out: LongArray)
    private external fun nativeBounceStack(partPath: String, finalPath: String, peak: FloatArray, onlyLayer: Long): Long

    /**
     * Conform render (Signalsmith Stretch): the whole take at [lengthFactor] × its length, shifted [semitones].
     * The one call made off the audio-control thread: it touches no engine state and can take seconds,
     * so the app's render worker runs it. Returns output frames, or -1.
     */
    fun render(inPath: String, partPath: String, finalPath: String, lengthFactor: Double, semitones: Int): Long =
        nativeRender(inPath, partPath, finalPath, lengthFactor, semitones)
    /** Stops everything sounding and closes the output (latency calibration opens its own streams). */
    fun releaseOutput() = nativeReleaseOutput()
    private external fun nativeReleaseOutput()

    private external fun nativeRender(inPath: String, partPath: String, finalPath: String, lengthFactor: Double, semitones: Int): Long

    private external fun nativeSampleRate(): Int
    private external fun nativeSetInputPreset(preset: Int)
    private external fun nativeSetClick(on: Boolean, bpm: Double)
    private external fun nativeBeats(): Long
    private external fun nativePlay(
        path: String, startFrame: Long, play: Boolean, speed: Double, transpose: Double,
        loopA: Long, loopB: Long, stepBy: Double, stepTo: Double, countInBeats: Int, beatFrames: Double, isolate: Boolean,
    ): Long
    private external fun nativeSetPaused(paused: Boolean)
    private external fun nativeUnload()
    private external fun nativePlayerState(out: LongArray)
    private external fun nativeTakePeak(): Float
    private external fun nativeStartRecording(partPath: String, finalPath: String): Long
    private external fun nativeRecordingState(out: LongArray): Boolean
    private external fun nativeStopRecording(out: LongArray): Boolean
    private external fun nativeIdle()
    private external fun nativeOutputLost(): Boolean
    private external fun nativeInputLost(): Boolean
}

data class PlayerState(val position: Long, val frames: Long, val rate: Int, val ended: Boolean, val paused: Boolean, val loaded: Boolean, val passes: Int = 0, val speedNow: Double = 1.0)

/** The stack transport: [position] within one pass of [length] frames. */
data class StackState(val position: Long, val length: Long, val playing: Boolean, val layers: Int)

data class RecordingState(
    val framesWritten: Long,
    val prerollFrames: Long,
    val lostFrames: Long,
    val diskError: Boolean,
    val inputDisconnected: Boolean,
) {
    companion object {
        fun of(s: LongArray) = RecordingState(s[0], s[1], s[2], s[3] != 0L, s[4] != 0L)
    }
}
