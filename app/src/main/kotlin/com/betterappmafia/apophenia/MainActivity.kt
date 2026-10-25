package com.betterappmafia.apophenia

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.betterappmafia.apophenia.audio.Mic
import com.betterappmafia.apophenia.capture.RecordWidget
import com.betterappmafia.apophenia.ui.BamSplash
import com.betterappmafia.apophenia.ui.Shell
import com.betterappmafia.apophenia.ui.theme.ConsoleTheme

/** The one Activity: the C4 shell. The mic permission is asked for the first time something needs the mic. */
class MainActivity : ComponentActivity() {
    private val app get() = application as ApopheniaApp
    private var pending: (() -> Unit)? = null
    /** A widget/shortcut take waits for onResume: a microphone service may only start once the app is on screen. */
    private var recordOnResume = false

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val run = pending
        pending = null
        if (granted) run?.invoke() else app.model.notice("Apophenia needs the microphone to record. You can allow it in Android's app settings.")
    }

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let { app.model.folderPicked(it) } }
    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { app.model.backupFilePicked(it) } }
    private val pickImports = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) app.model.importFiles(uris) }
    private val pickMidiTarget = registerForActivityResult(ActivityResultContracts.CreateDocument("audio/midi")) { uri -> app.model.midiTargetPicked(uri) }
    private val pickFileTarget = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> app.model.fileTargetPicked(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app.model.pickBackupFolder = { pickFolder.launch(null) }
        app.model.pickBackupFile = { pickFile.launch(arrayOf("application/zip", "application/octet-stream")) }
        app.model.pickImport = { pickImports.launch(arrayOf("audio/*", "text/*", "video/*", "application/octet-stream", "application/xml", "application/vnd.recordare.musicxml+xml", "application/vnd.recordare.musicxml", "application/zip")) }
        app.model.pickMidiTarget = { name -> pickMidiTarget.launch(name) }
        app.model.pickFileTarget = { name -> pickFileTarget.launch(name) }
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        CrashLog.file(app).takeIf { it.isFile }?.let { app.model.crashText = it.readText() }
        // The BAM splash on a cold start only; never over a widget/shortcut take, where the screen must show the mic.
        var splash by mutableStateOf(savedInstanceState == null && intent?.action != RecordWidget.ACTION_RECORD)
        setContent {
            ConsoleTheme {
                Box {
                    Shell(app.model, ::withMic)
                    if (splash) BamSplash { splash = false }
                }
            }
        }
        if (savedInstanceState == null) handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Widget or shortcut: start a Library take (unless one is already recording). */
    private fun handle(intent: Intent?) {
        if (intent?.action != RecordWidget.ACTION_RECORD) return
        Breadcrumbs.add("record from widget/shortcut")
        setIntent(Intent(this, MainActivity::class.java))
        recordOnResume = true
    }

    override fun onResume() {
        super.onResume()
        if (recordOnResume) {
            recordOnResume = false
            if (app.audio.mic.value != Mic.RECORDING) withMic { app.model.startRecording() }
        }
    }

    private fun withMic(block: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            block()
        } else {
            pending = block
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) app.audio.onBackground()
    }
}
