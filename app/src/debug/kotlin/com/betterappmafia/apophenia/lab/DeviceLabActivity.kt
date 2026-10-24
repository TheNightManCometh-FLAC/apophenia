package com.betterappmafia.apophenia.lab

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Paint
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.betterappmafia.apophenia.audio.AudioEngine
import com.betterappmafia.apophenia.audio.lab.LabEngine
import com.betterappmafia.apophenia.audio.lab.LabEngine.InputPreset
import com.betterappmafia.apophenia.audio.lab.LatencyAnalysis
import com.betterappmafia.apophenia.audio.lab.LevelAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Device Lab (official plan §14.2): the M0 measurements that need no later feature — round-trip
 * latency per route, microphone sources, simultaneous play + record + click, battery and heat,
 * and glyphs. Debug builds only. The mic opens only while a test runs and closes after it.
 */
class DeviceLabActivity : ComponentActivity() {

    private var busy by mutableStateOf<String?>(null)
    private var latencyText by mutableStateOf("")
    private var micText by mutableStateOf("")
    private var stabilityText by mutableStateOf("")
    private var stabilityMinutes by mutableStateOf(10)
    private var stabilityRunning by mutableStateOf(false)
    private var logText by mutableStateOf("")

    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val logFile by lazy { File(filesDir, "lab/results.txt").also { it.parentFile?.mkdirs() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        logText = if (logFile.exists()) logFile.readText() else ""
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) { LabScreen() }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Screen-off running needs the M1 foreground service; until then, leaving stops the run.
        if (stabilityRunning) stabilityRunning = false
    }

    // ---------------------------------------------------------------------------------------- UI

    @Composable
    private fun LabScreen() {
        var granted by remember { mutableStateOf(hasMic()) }
        val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
        Column(
            modifier = Modifier.safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Device Lab", style = MaterialTheme.typography.headlineMedium)
            Mono(deviceSummary())
            if (!granted) {
                Button(onClick = { askMic.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Allow microphone") }
            }
            busy?.let { Text("Running: $it", color = MaterialTheme.colorScheme.primary) }

            Section("1 · Round-trip latency (this route)",
                "Plays five noise bursts and finds them in the mic. Speaker: phone on a table, quiet room. " +
                    "Headphones or Bluetooth: hold one earpiece against the bottom mic. Run once per route.") {
                Button(enabled = granted && busy == null && !stabilityRunning, onClick = ::runLatency) { Text("Measure latency") }
                Mono(latencyText)
            }

            Section("2 · Microphone sources",
                "Records 6 s from each source in turn: UNPROCESSED, VOICE_RECOGNITION, MIC. Run once in a silent room, " +
                    "once while playing the same open string. WAVs go to Music/Apophenia Device Lab for listening.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = granted && busy == null && !stabilityRunning, onClick = { runMics("silent room") }) { Text("Silent room") }
                    Button(enabled = granted && busy == null && !stabilityRunning, onClick = { runMics("instrument") }) { Text("Instrument") }
                }
                Mono(micText)
            }

            Section("3 · Play + record + click, battery and heat",
                "Pad + 120 BPM click out, mic recorded to a file, all at once. Keep the screen on and the app in front " +
                    "(screen-off running needs the M1 foreground service). Target: no dropouts over 10 minutes.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in listOf(1, 10, 30)) {
                        FilterChip(selected = stabilityMinutes == m, enabled = !stabilityRunning,
                            onClick = { stabilityMinutes = m }, label = { Text("$m min") })
                    }
                }
                if (stabilityRunning) OutlinedButton(onClick = { stabilityRunning = false }) { Text("Stop") }
                else Button(enabled = granted && busy == null, onClick = ::runStability) { Text("Start") }
                Mono(stabilityText)
            }

            Section("4 · Glyphs", "System font. Bravura is checked once the fonts are bundled.") {
                Text("𝄪 𝄫 ♯ ♭ ♮   C𝄪  B𝄫  F♯m7♭5", fontSize = 32.sp)
                Mono(glyphReport())
                OutlinedButton(onClick = { log("Glyphs", glyphReport() + "\nOn-screen look: judged by eye, see walkthrough") }) { Text("Log glyph check") }
            }

            Section("Later milestones", "") {
                Mono("Tuner on bass (M3) · stack layer limit and stretcher (M2) · Basic Pitch, Spleeter, Moonshine (M4) · " +
                    "widget/shortcut capture with the phone locked (M1)")
            }

            Section("Results log", "Every run is appended here and to files/lab/results.txt.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = ::shareLog) { Text("Share log") }
                    OutlinedButton(onClick = { logFile.delete(); logText = "" }) { Text("Clear") }
                }
                Mono(logText.takeLast(6000))
            }
        }
    }

    @Composable
    private fun Section(title: String, help: String, content: @Composable () -> Unit) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (help.isNotEmpty()) Text(help, style = MaterialTheme.typography.bodySmall)
                content()
            }
        }
    }

    @Composable
    private fun Mono(text: String) {
        if (text.isNotEmpty()) Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }

    // ---------------------------------------------------------------------------------- tests

    private fun runLatency() {
        val preset = if (unprocessedSupported()) InputPreset.Unprocessed else InputPreset.VoiceRecognition
        val route = outputRoute()
        busy = "latency"
        latencyText = "Measuring on $route…"
        lifecycleScope.launch {
            val report = withContext(Dispatchers.Default) {
                val burst = LatencyAnalysis.burst()
                LabEngine.runLatency(burst, startSeconds = 0.5f, spacingSeconds = 1.2f, count = 5, preset = preset).fold(
                    onSuccess = { cap ->
                        val maxLag = (cap.sampleRate * 1.1).toInt()
                        val ds = cap.burstStarts.map { LatencyAnalysis.detect(cap.samples, it, burst, maxLag) }
                        val s = LatencyAnalysis.summarize(ds, cap.sampleRate)
                        buildString {
                            appendLine("Route: $route · input ${preset.label}")
                            appendLine("Bursts (ms, confidence):")
                            ds.forEachIndexed { i, d ->
                                appendLine("  ${i + 1}: " + (d?.let { "%.2f ms  c=%.0f%s".format(it.lagFrames * 1000.0 / cap.sampleRate, it.confidence, if (it.inverted) " inverted" else "") } ?: "no window"))
                            }
                            if (s.medianMs != null) appendLine("Round trip: median %.2f ms, spread ±%.2f ms (%d of %d bursts)".format(s.medianMs, s.halfSpreadMs, s.acceptedLagsMs.size, ds.size))
                            else appendLine("Round trip: NOT FOUND — too quiet or too noisy; raise volume or move closer")
                            appendLine("Short input reads: ${cap.shortReads}" + if (cap.shortReads > 0) " (frame counters may have slipped; repeat)" else "")
                            if (cap.disconnected) appendLine("Route changed during the run: discard and repeat")
                            appendLine("Media volume: ${volumePercent()}%")
                            append(withDeviceNames(cap.streamInfo))
                        }
                    },
                    onFailure = { "Could not open streams: ${it.message}" },
                )
            }
            latencyText = report
            log("Round-trip latency", report)
            busy = null
        }
    }

    private fun runMics(condition: String) {
        busy = "mic sources ($condition)"
        micText = "Recording…"
        lifecycleScope.launch {
            val sb = StringBuilder("Condition: $condition\n")
            sb.appendLine("Bands (Hz):  " + LevelAnalysis.OCTAVE_CENTRES.joinToString(" ") { fmtHz(it) })
            for (preset in InputPreset.entries) {
                micText = sb.toString() + "Recording ${preset.label}…"
                val line = withContext(Dispatchers.Default) {
                    LabEngine.recordInput(preset, 6f).fold(
                        onSuccess = { rec ->
                            // Skip the first 0.3 s: some sources ramp their gain in.
                            val x = rec.samples.copyOfRange(minOf(rec.samples.size, rec.sampleRate * 3 / 10), rec.samples.size)
                            val bands = LevelAnalysis.octaveBandsDb(x, rec.sampleRate)
                            val saved = saveWav("lab-${condition.replace(' ', '-')}-${preset.name}", rec.samples, rec.sampleRate)
                            buildString {
                                appendLine("${preset.label}: RMS %.1f dB, peak %.1f dB, floor %.1f dB".format(
                                    LevelAnalysis.rmsDb(x), LevelAnalysis.peakDb(x), LevelAnalysis.floorDb(x, rec.sampleRate)))
                                appendLine("  bands dB:  " + bands.joinToString(" ") { "%5.0f".format(it) })
                                appendLine("  " + withDeviceNames(rec.streamInfo))
                                append("  saved: $saved")
                            }
                        },
                        onFailure = { "${preset.label}: did not open (${it.message})" },
                    )
                }
                sb.appendLine(line)
                delay(500)
            }
            micText = sb.toString().trimEnd()
            log("Microphone sources", micText)
            busy = null
        }
    }

    private fun runStability() {
        val minutes = stabilityMinutes
        val preset = if (unprocessedSupported()) InputPreset.Unprocessed else InputPreset.VoiceRecognition
        val wav = File(cacheDir, "lab-stability.wav")
        val before = battery()
        val route = outputRoute()
        stabilityRunning = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        lifecycleScope.launch {
            val started = withContext(Dispatchers.Default) { LabEngine.startStability(wav.path, preset, 120.0) }
            if (started.isFailure) {
                stabilityText = "Could not start: ${started.exceptionOrNull()?.message}"
                stabilityRunning = false
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                return@launch
            }
            val t0 = SystemClock.elapsedRealtime()
            val endAt = t0 + minutes * 60_000L
            var last: LabEngine.StabilityStats? = null
            while (stabilityRunning && SystemClock.elapsedRealtime() < endAt) {
                last = withContext(Dispatchers.Default) { LabEngine.pollStability() } ?: last
                stabilityText = stabilityReport(last, SystemClock.elapsedRealtime() - t0, minutes, route, before, null)
                if (last?.disconnected == true) break
                delay(1000)
            }
            last = withContext(Dispatchers.Default) {
                val final = LabEngine.pollStability() ?: last
                LabEngine.stopStability()
                final
            }
            val elapsed = SystemClock.elapsedRealtime() - t0
            val report = stabilityReport(last, elapsed, minutes, route, before, battery()) +
                "\nRecorded file: %.1f MB (deleted after the run)".format(wav.length() / 1e6) +
                (if (elapsed < minutes * 60_000L - 2000) "\nStopped early (by hand, app left, or route change)" else "") +
                "\n" + withDeviceNames(started.getOrDefault(""))
            wav.delete()
            stabilityText = report
            log("Play + record + click ($minutes min)", report)
            stabilityRunning = false
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun stabilityReport(
        s: LabEngine.StabilityStats?, elapsedMs: Long, minutes: Int, route: String, before: String, after: String?,
    ): String = buildString {
        appendLine("Route: $route · %d:%02d of $minutes:00".format(elapsedMs / 60000, elapsedMs / 1000 % 60))
        if (s != null) {
            val expected = s.sampleRate * elapsedMs / 1000.0
            appendLine("Output xruns: ${s.outXRuns}   input xruns: ${s.inXRuns}")
            appendLine("Short input reads: ${s.shortReads}   writer overflow frames: ${s.ringOverflowFrames}")
            appendLine("Callbacks: ${s.callbacks}   max callback: ${s.maxCallbackMicros} µs")
            appendLine("Frames out: ${s.framesOut} (%.1f%% of wall clock)   in: ${s.framesIn}".format(100.0 * s.framesOut / expected.coerceAtLeast(1.0)))
            appendLine("Clicks: ${s.clicks}   written: %.1f MB, slowest write %d ms".format(s.bytesWritten / 1e6, s.maxWriteMicros / 1000))
            if (s.disconnected) appendLine("Route changed: run ended")
        }
        appendLine("Battery/heat before: $before")
        if (after != null) append("Battery/heat after:  $after")
    }

    // -------------------------------------------------------------------------------- helpers

    private fun hasMic() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun unprocessedSupported() = audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"

    private fun volumePercent(): Int {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return (100.0 * audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / max).roundToInt()
    }

    private fun deviceSummary(): String = buildString {
        appendLine("${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
        appendLine("Build ${Build.DISPLAY}")
        appendLine("Oboe ${AudioEngine.oboeVersion()}")
        val pm = packageManager
        appendLine("low_latency=${pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY)} pro=${pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO)}")
        appendLine("Output rate ${audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)} Hz, " +
            "frames/buffer ${audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)}, " +
            "UNPROCESSED supported=${unprocessedSupported()}")
        append("Route now: ${outputRoute()}")
    }

    /** What media output would use now: the first non-speaker sink if one is connected. */
    private fun outputRoute(): String {
        val outs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.isSink }
        val preferred = outs.firstOrNull { it.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER && it.type != AudioDeviceInfo.TYPE_BUILTIN_EARPIECE && it.type != AudioDeviceInfo.TYPE_TELEPHONY }
        return preferred?.let { "${typeName(it.type)} (${it.productName})" } ?: "built-in speaker"
    }

    /** Appends the device type to each `dev=N` in Oboe's stream description. */
    private fun withDeviceNames(info: String): String {
        val all = audioManager.getDevices(AudioManager.GET_DEVICES_ALL)
        return Regex("dev=(\\d+)").replace(info) { m ->
            val d = all.firstOrNull { it.id == m.groupValues[1].toInt() }
            "dev=${m.groupValues[1]}(${d?.let { typeName(it.type) } ?: "?"})"
        }
    }

    private fun typeName(t: Int) = when (t) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "earpiece"
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "built-in mic"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired headphones"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired headset"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB device"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE headset"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth LE speaker"
        AudioDeviceInfo.TYPE_TELEPHONY -> "telephony"
        else -> "type $t"
    }

    private fun battery(): String {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return "unknown"
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val thermal = getSystemService(PowerManager::class.java).currentThermalStatus
        return "$level%%, battery %.1f °C, thermal status $thermal%s".format(temp, if (plugged) ", CHARGING" else "")
    }

    private fun glyphReport(): String {
        val p = Paint()
        return listOf("𝄪" to "U+1D12A double sharp", "𝄫" to "U+1D12B double flat", "♯" to "U+266F", "♭" to "U+266D", "♮" to "U+266E")
            .joinToString("\n") { (g, name) -> "$name: hasGlyph=${p.hasGlyph(g)}" }
    }

    private fun fmtHz(hz: Double) = if (hz >= 1000) "%4.0fk".format(hz / 1000) else "%5.0f".format(hz)

    /** 16-bit mono WAV into Music/Apophenia Device Lab (no storage permission on API 29+). */
    private fun saveWav(name: String, x: FloatArray, rate: Int): String {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$name-${stamp("yyyyMMdd-HHmmss")}.wav")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Apophenia Device Lab")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return "not saved"
        return try {
            contentResolver.openOutputStream(uri)?.use { writeWav(it, x, rate) }
            contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            "Music/Apophenia Device Lab/${values.getAsString(MediaStore.Audio.Media.DISPLAY_NAME)}"
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            "not saved (${e.message})"
        }
    }

    private fun writeWav(out: OutputStream, x: FloatArray, rate: Int) {
        val data = x.size * 2
        val b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(data)
        for (v in x) b.putShort((v.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort())
        out.write(b.array())
    }

    private fun stamp(pattern: String) = SimpleDateFormat(pattern, Locale.US).format(Date())

    private fun log(title: String, body: String) {
        val entry = "=== $title — ${stamp("yyyy-MM-dd HH:mm:ss")} — ${Build.MODEL} API ${Build.VERSION.SDK_INT}\n$body\n\n"
        logFile.appendText(entry)
        logText = logFile.readText()
    }

    private fun shareLog() {
        if (!logFile.exists()) return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Apophenia Device Lab results")
            putExtra(Intent.EXTRA_TEXT, logFile.readText())
        }
        startActivity(Intent.createChooser(send, "Share Device Lab results"))
    }
}
