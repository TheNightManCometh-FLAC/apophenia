package com.betterappmafia.apophenia

import android.app.Application
import com.betterappmafia.apophenia.audio.AudioController
import com.betterappmafia.apophenia.capture.Captures
import com.betterappmafia.apophenia.data.ApopheniaDatabase
import com.betterappmafia.apophenia.data.openApopheniaDatabase
import com.betterappmafia.apophenia.ui.AppModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Process-wide objects: the database, the audio controller, and the app's UI state (it survives rotation). */
class ApopheniaApp : Application() {
    lateinit var db: ApopheniaDatabase
    lateinit var audio: AudioController
    lateinit var captures: Captures
    val imports by lazy { com.betterappmafia.apophenia.capture.Imports(this, db, audioDir) }
    /** Conformed stack layers, in the evictable cache folder. */
    val renders by lazy { com.betterappmafia.apophenia.audio.Renders(File(cacheDir, "renders")) }
    lateinit var backups: com.betterappmafia.apophenia.backup.Backups
    lateinit var model: AppModel
    val audioDir: File by lazy { File(filesDir, "audio") }
    /** Take analysis in the background (plan §10.2). */
    val analyser by lazy { com.betterappmafia.apophenia.audio.Analyser(db, audioDir) }
    /** Notes → MIDI with Basic Pitch (plan §10.3). */
    val transcriber by lazy { com.betterappmafia.apophenia.audio.Transcriber(this, db, audioDir) }
    val separator by lazy { com.betterappmafia.apophenia.audio.Separator(this, db, audioDir) }
    val speechReader by lazy { com.betterappmafia.apophenia.audio.SpeechReader(this, db, audioDir) }
    val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        db = openApopheniaDatabase(this)
        audio = AudioController(this, audioDir)
        captures = Captures(db, audioDir)
        backups = com.betterappmafia.apophenia.backup.Backups(this)
        model = AppModel(this)
        io.launch {
            val n = captures.recover(skip = { audio.activePart })
            if (n > 0) model.notice("Recovered $n take${if (n == 1) "" else "s"} cut off last time. Tagged \"recovered\".")
            captures.purgeTrash()
            analyser.kick()
        }
    }
}

/** The last user actions, written into the crash log: framework-only stack traces don't say where you were. */
object Breadcrumbs {
    private val ring = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized fun add(what: String) {
        ring.addLast("${fmt.format(Date())} $what")
        while (ring.size > 24) ring.removeFirst()
    }

    @Synchronized fun dump(): String = ring.joinToString("\n")
}

/** An uncaught crash is written to files/crash.txt and shown, with Copy, on the next launch (there's no logcat on the phone). */
object CrashLog {
    fun file(app: Application) = File(app.filesDir, "crash.txt")

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val trace = android.util.Log.getStackTraceString(e)
                file(app).writeText("Apophenia ${BuildInfo.version(app)} crashed on thread ${thread.name}\n\n$trace\nLast actions:\n${Breadcrumbs.dump()}\n")
            }
            previous?.uncaughtException(thread, e)
        }
    }
}

object BuildInfo {
    fun version(app: Application): String = app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "?"
}
