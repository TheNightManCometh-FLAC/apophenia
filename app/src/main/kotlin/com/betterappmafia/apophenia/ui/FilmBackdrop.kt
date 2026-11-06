package com.betterappmafia.apophenia.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.betterappmafia.apophenia.audio.Mic
import com.betterappmafia.apophenia.audio.Wav
import com.betterappmafia.apophenia.core.art.FilmFrame
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.data.BoardRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/**
 * The Film theme's painted frames (design plan, decisions of 31 Oct 2026). Each frame is painted once, off the main
 * thread, at half the screen's pixel size (the grain and haze lose nothing), then kept in memory and as a JPEG in the
 * cache folder (evictable, like renders). Drawing one is a plain bitmap draw: no live blur or shader. A section's
 * frame comes in two layers so its light can move over a still ground (see [SectionBackdrop]).
 */
object FilmFrames {
    /** About three sections' two layers, or six single frames, at half size. */
    private val mem = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val lock = Mutex()

    /** The last frame painted on this phone: (milliseconds, width, height). Shown in Settings so it can be measured. */
    var lastPaint by mutableStateOf<Triple<Long, Int, Int>?>(null)
        private set

    suspend fun get(ctx: Context, input: FilmFrame.Input, w: Int, h: Int): Bitmap = lock.withLock {
        val key = "${input.signature()}_${w}x$h"
        mem.get(key)?.let { return it }
        withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "frames").apply { mkdirs() }
            val file = File(dir, "$key.jpg")
            val cached = if (file.exists()) runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() else null
            val bmp = cached ?: run {
                val t0 = System.nanoTime()
                val px = withContext(Dispatchers.Default) { FilmFrame.paint(w, h, input) }
                val b = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
                lastPaint = Triple((System.nanoTime() - t0) / 1_000_000, w, h)
                save(b, dir, file)
                trim(dir)
                b
            }
            mem.put(key, bmp)
            bmp
        }
    }

    /**
     * A section's frame as its still ground and its light (black where there is none), each painted once and cached
     * like [get]. The app adds the light over the ground and moves only the light.
     */
    suspend fun layers(ctx: Context, input: FilmFrame.Input, w: Int, h: Int): Pair<Bitmap, Bitmap> = lock.withLock {
        val key = "${input.signature()}_${w}x$h"
        val g0 = mem.get("${key}_g"); val l0 = mem.get("${key}_l")
        if (g0 != null && l0 != null) return g0 to l0
        withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "frames").apply { mkdirs() }
            val gf = File(dir, "${key}_g.jpg"); val lf = File(dir, "${key}_l.jpg")
            fun load(f: File) = if (f.exists()) runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull() else null
            var g = load(gf); var l = load(lf)
            if (g == null || l == null) {
                val t0 = System.nanoTime()
                val px = withContext(Dispatchers.Default) { FilmFrame.paintLayers(w, h, input) }
                g = Bitmap.createBitmap(px.ground, w, h, Bitmap.Config.ARGB_8888)
                l = Bitmap.createBitmap(px.light, w, h, Bitmap.Config.ARGB_8888)
                lastPaint = Triple((System.nanoTime() - t0) / 1_000_000, w, h)
                save(g, dir, gf); save(l, dir, lf)
                trim(dir)
            }
            mem.put("${key}_g", g); mem.put("${key}_l", l)
            g to l
        }
    }

    private fun save(b: Bitmap, dir: File, file: File) = runCatching {
        val tmp = File(dir, file.name + ".tmp")
        tmp.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        tmp.renameTo(file)
    }

    /** Keeps the newest 60 files on disk (a section's frame is two). */
    private fun trim(dir: File) {
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(60).forEach { it.delete() }
    }
}

/**
 * The Board's keys, commonest first; a tie goes to the key that came onto the Board first. The order never depends on
 * which window is on top (until 0.9.48 it did: bringing a window forward could flip the whole frame's colour).
 */
fun boardKeys(rows: List<BoardRow>): List<KeyMode> {
    val keys = rows.sortedWith(compareBy({ it.addedAt }, { it.scrap.id })).mapNotNull { KeyMode.parseOrNull(it.scrap.key) }
    val counts = LinkedHashMap<String, Pair<KeyMode, Int>>()
    keys.forEach { k -> counts[k.token] = k to ((counts[k.token]?.second ?: 0) + 1) }
    return counts.values.sortedByDescending { it.second }.map { it.first }
}

/**
 * What a project's frame is painted from: its fragments' keys (the commonest for the ground, the next for the haze),
 * tempos (the median for the main bands, up to two more for their own), and its first five takes, each with its own
 * key and tempo. All read in the order fragments came onto the Board, so only the material changes the frame.
 */
suspend fun projectFrameInput(audioDir: File, projectId: Long, rows: List<BoardRow>): FilmFrame.Input {
    val byAdded = rows.sortedWith(compareBy({ it.addedAt }, { it.scrap.id }))
    val keys = boardKeys(byAdded)
    val key = keys.getOrNull(0); val key2 = keys.getOrNull(1)
    val timed = byAdded.filter { !it.scrap.freeTime }.mapNotNull { it.scrap.bpmLow }
    val bpm = timed.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
    val others = timed.groupingBy { Math.round(it) }.eachCount().entries.sortedByDescending { it.value }.map { it.key.toDouble() }
        .filter { t -> bpm == null || kotlin.math.abs(t - bpm) / bpm > 0.03 }.take(2)
    val takes = byAdded.filter { it.glimpsePath != null }.take(5)
    val trails = withContext(Dispatchers.IO) {
        takes.map { r -> runCatching { Wav.peaks(File(audioDir, r.glimpsePath!!), 240) }.getOrDefault(FloatArray(0)) }
    }
    return FilmFrame.Input(
        FilmFrame.Kind.PROJECT, seed = projectId,
        tonicPc = key?.tonic?.pitchClass, minor = key?.let { isMinor(it) } ?: false, bpm = bpm,
        trails = trails, starred = takes.indexOfFirst { it.starred > 0 },
        trailKeys = takes.map { r -> KeyMode.parseOrNull(r.scrap.key)?.let { it.tonic.pitchClass to isMinor(it) } },
        trailBpm = takes.map { r -> if (r.scrap.freeTime) null else r.scrap.bpmLow },
        tempos = others, tonic2 = key2?.tonic?.pitchClass, minor2 = key2?.let { isMinor(it) } ?: false,
    )
}

/** A mode with a minor third (Aeolian, Dorian, Phrygian, Locrian, the minors…) paints darker. */
fun isMinor(k: KeyMode) = k.scale.degrees.any { it.number == 3 && it.alter == -1 }

/** A frame filling its box, faded in once when it arrives. Nothing is drawn until it has been painted. */
@Composable
fun FilmBackdrop(input: FilmFrame.Input?, modifier: Modifier = Modifier) {
    if (input == null) return
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val ctx = LocalContext.current
        val w = (with(density) { maxWidth.toPx() } / 2).toInt().coerceIn(64, 1200)
        val h = (with(density) { maxHeight.toPx() } / 2).toInt().coerceIn(64, 2400)
        val sig = remember(input) { input.signature() }
        val bmp by produceState<ImageBitmap?>(null, sig, w, h) {
            value = runCatching { FilmFrames.get(ctx, input, w, h).asImageBitmap() }.getOrNull()
        }
        val shown = bmp ?: return@BoxWithConstraints
        val fade = remember(shown) { Animatable(0f) }
        LaunchedEffect(shown) { fade.animateTo(1f, tween(450)) }
        Image(shown, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(fade.value))
    }
}

/**
 * The section's frame behind every screen (Christopher, 3 Nov 2026): PLAN shows the open project's own frame, PRACTICE
 * the violet room, STUDY the sepia keys, the last two placed and lit by the project. Switching section crossfades.
 * The light moves ([BackdropMotion]); the ground never does, and nothing is painted again for the movement.
 */
@Composable
fun SectionBackdrop(model: AppModel, modifier: Modifier = Modifier) {
    val pid = model.projectId
    val tab = model.mode.tab
    val rows by remember(pid) { if (pid != null) model.app.db.projects().boardRows(pid) else flowOf(emptyList()) }.collectAsState(null)
    val list = rows ?: return
    // Only what the painting reads, in the order fragments came onto the Board: moving, resizing or bringing a window
    // forward changes nothing here.
    val what = list.sortedWith(compareBy({ it.addedAt }, { it.scrap.id }))
        .map { listOf(it.scrap.id, it.scrap.key, it.scrap.bpmLow, it.scrap.freeTime, it.glimpsePath, it.starred) }
    val input by produceState<FilmFrame.Input?>(null, pid, what, tab) {
        val key = boardKeys(list).firstOrNull()
        val pc = key?.tonic?.pitchClass; val minor = key?.let { isMinor(it) } ?: false
        value = when (tab) {
            Tab.PRACTICE -> FilmFrame.Input(FilmFrame.Kind.PRACTICE, pid ?: 0, pc, minor, null, emptyList())
            Tab.STUDY -> FilmFrame.Input(FilmFrame.Kind.STUDY, pid ?: 0, pc, minor, null, emptyList())
            Tab.MAKE -> if (pid == null) FilmFrame.Input(FilmFrame.Kind.PROJECT, 0, null, false, null, emptyList())
                else projectFrameInput(model.app.audioDir, pid, list)
        }
    }
    val motion = rememberBackdropMotion(model)
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val ctx = LocalContext.current
        val w = (with(density) { maxWidth.toPx() } / 2).toInt().coerceIn(64, 1200)
        val h = (with(density) { maxHeight.toPx() } / 2).toInt().coerceIn(64, 2400)
        var shown by remember { mutableStateOf<Pair<ImageBitmap, ImageBitmap>?>(null) }
        var prev by remember { mutableStateOf<Pair<ImageBitmap, ImageBitmap>?>(null) }
        val fade = remember { Animatable(1f) }
        val sig = input?.signature()
        LaunchedEffect(sig, w, h) {
            val inp = input ?: return@LaunchedEffect
            val (g, l) = runCatching { FilmFrames.layers(ctx, inp, w, h) }.getOrNull() ?: return@LaunchedEffect
            prev = shown; shown = g.asImageBitmap() to l.asImageBitmap()
            fade.snapTo(0f); fade.animateTo(1f, tween(if (prev == null) 450 else 600)); prev = null
        }
        Canvas(Modifier.fillMaxSize()) {
            prev?.let { drawFrame(it, 1f, motion) }
            shown?.let { drawFrame(it, fade.value, motion) }
        }
    }
}

private fun DrawScope.drawFrame(l: Pair<ImageBitmap, ImageBitmap>, alpha: Float, m: BackdropMotion) {
    val dst = IntSize(size.width.toInt(), size.height.toInt())
    drawImage(l.first, dstSize = dst, alpha = alpha)
    // The light sways a little sideways and swells on the beat; drawn a touch larger so its edges never show.
    val pulse = m.pulse
    withTransform({
        translate(left = m.drift * size.width * 0.03f)
        scale(1.06f, 1.06f + 0.015f * pulse, Offset(size.width / 2, size.height * 0.62f))
    }) {
        drawImage(l.second, dstSize = dst, alpha = alpha, blendMode = BlendMode.Plus)
        val extra = (m.breathe * 0.12f + pulse * 0.5f).coerceIn(0f, 1f)
        if (extra > 0.01f) drawImage(l.second, dstSize = dst, alpha = alpha * extra, blendMode = BlendMode.Plus)
    }
}

/** What the light does right now; read only while drawing, so it redraws the backdrop without recomposing anything. */
class BackdropMotion {
    /** -1..1, a slow sway. */
    var drift by mutableFloatStateOf(0f)
    /** 0..1, a slow breath. */
    var breathe by mutableFloatStateOf(0f)
    /** 0..1, high on each beat of what plays, decaying before the next. */
    var pulse by mutableFloatStateOf(0f)
}

/**
 * Drives [BackdropMotion]. With nothing playing the light sways and breathes slowly (about 15 redraws a second); with
 * the click, a stack or a take with a tempo playing, it pulses on the beat (about 30). It stops in the background,
 * while recording, under a full-screen detail or stand mode, and when Android's animations are off or Battery Saver
 * is on. [Claude] The beat comes from the click's own beats, else from the position and the tempo: a take's first
 * beat isn't stored, so its pulse keeps the take's tempo but may sit off its beat.
 */
@Composable
fun rememberBackdropMotion(model: AppModel): BackdropMotion {
    val m = remember { BackdropMotion() }
    val audio = model.app.audio
    val ctx = LocalContext.current
    val life by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val mic by audio.mic.collectAsState()
    val playingScrap by remember { audio.player.map { it?.scrapId }.distinctUntilChanged() }.collectAsState(null)
    val takeBpm by produceState<Double?>(null, playingScrap) {
        value = playingScrap?.let { id -> model.app.db.scraps().get(id)?.takeIf { !it.freeTime }?.bpmLow }
    }
    val resumed = life.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    val system = remember(resumed) { motionAllowed(ctx) }
    val allowed = resumed && system && mic != Mic.RECORDING && model.detail == null && model.standProject == null
    LaunchedEffect(allowed) {
        if (!allowed) { m.pulse = 0f; return@LaunchedEffect }
        var lastBeats = -1L; var beatAt = 0L
        var lastPos = -1L; var posAt = 0L
        while (true) {
            val now = withFrameNanos { it }
            val sec = now / 1e9
            m.drift = sin(2 * PI * sec / 47).toFloat()
            m.breathe = (0.5 + 0.5 * sin(2 * PI * sec / 9)).toFloat()
            val click = audio.click.value; val stack = audio.stack.value; val player = audio.player.value
            var phase = -1.0; var accent = 0.75f
            fun fromPosition(pos: Long, bpm: Double, speed: Double) {
                if (pos != lastPos) { lastPos = pos; posAt = now }
                val t = pos.toDouble() / audio.sampleRate + (now - posAt) / 1e9 * speed
                val beats = t * bpm / 60
                phase = beats - floor(beats)
            }
            if (click.on) {
                if (click.beats != lastBeats) { lastBeats = click.beats; beatAt = now }
                phase = (now - beatAt) / 1e9 * click.bpmNow / 60
                accent = if (click.beat == 0) 1f else 0.7f
            } else if (stack?.playing == true) {
                model.stacks.tempo()?.let { fromPosition(stack.position, it, 1.0) }
            } else if (player?.playing == true) {
                takeBpm?.let { fromPosition(player.position, it, player.speedNow) }
            }
            m.pulse = if (phase >= 0) (exp(-phase * 5) * accent).toFloat() else 0f
            delay(if (phase >= 0) 16 else 55)
        }
    }
    return m
}

/** Android's "Remove animations" (animator scale 0) and Battery Saver both keep the backdrop still. */
private fun motionAllowed(ctx: Context): Boolean {
    val scale = runCatching {
        android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    val saver = (ctx.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager)?.isPowerSaveMode == true
    return scale > 0f && !saver
}
