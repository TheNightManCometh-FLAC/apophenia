package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T

/** One bundled piece: what it does in Apophenia, its licence, who holds the copyright, and its licence text in assets/licenses. */
data class Licence(val name: String, val use: String, val licence: String, val holder: String, val files: List<String>)

/**
 * Licences and privacy (plan §13, M7). Every third-party piece the APK ships, with its licence text, read from
 * assets/licenses. [Claude] The list was made from the Gradle dependencies, third_party/ and assets/ on 24 Oct 2026.
 */
object Notices {
    val all = listOf(
        Licence("Apophenia", "This app", "GPL-3.0-or-later", "Better App Mafia", listOf("apophenia-GPL-3.0.txt")),
        Licence("Oboe 1.11.0", "The audio engine: recording and playback", "Apache-2.0", "The Android Open Source Project", listOf("apache-2.0.txt")),
        Licence("Signalsmith Stretch", "Time-stretch and pitch-shift for stack layers", "MIT", "Geraint Luff / Signalsmith Audio Ltd.", listOf("signalsmith-stretch-LICENSE.txt")),
        Licence("Signalsmith Linear", "The FFT inside Signalsmith Stretch", "MIT", "Signalsmith Audio", listOf("signalsmith-linear-LICENSE.txt")),
        Licence("LiteRT 2.3.0", "Runs the Basic Pitch model on the phone", "Apache-2.0, with its third-party notices", "Google LLC", listOf("litert-LICENSE.txt", "litert-THIRD_PARTY_NOTICE.txt")),
        Licence("ONNX Runtime 1.31.0", "Runs the Spleeter and Moonshine models on the phone", "MIT, with its third-party notices", "Microsoft Corporation", listOf("onnxruntime-LICENSE.txt", "onnxruntime-ThirdPartyNotices.txt")),
        Licence("Basic Pitch (ICASSP 2022 model)", "Turns recordings into notes", "Apache-2.0", "Spotify AB", listOf("basic-pitch-LICENSE.txt")),
        Licence("Spleeter 4 stems", "Separates a song into vocals, drums, bass and other", "MIT (Spleeter); Apache-2.0 (the ONNX conversion)", "Deezer SA", listOf("spleeter-LICENSE.txt", "spleeter-4stems-onnx-README.md")),
        Licence("Moonshine base", "Speech to text for spoken lyric ideas", "MIT", "Moonshine AI", listOf("moonshine-LICENSE.txt")),
        Licence("Bravura", "The music font on the staff", "SIL Open Font License 1.1", "Steinberg Media Technologies GmbH", listOf("Bravura-OFL.txt")),
        Licence("Noto Music", "Music symbols in text", "SIL Open Font License 1.1", "The Noto Project Authors", listOf("NotoMusic-OFL.txt")),
        Licence("JetBrains Mono", "The app's typeface", "SIL Open Font License 1.1", "The JetBrains Mono Project Authors", listOf("JetBrainsMono-OFL.txt")),
        Licence("University of Iowa MIS piano", "The piano sound", "Free to use without restrictions (the source's terms)", "University of Iowa Electronic Music Studios", listOf("iowa-piano-NOTICE.txt")),
        Licence("Android Jetpack (Compose, Room, Lifecycle, Activity, Core) and Kotlin", "The app's interface, database and language", "Apache-2.0", "The Android Open Source Project; JetBrains s.r.o.", listOf("apache-2.0.txt")),
    )

    const val PRIVACY =
        "Apophenia has no internet permission: it can't send anything anywhere, and it has no accounts, ads or analytics.\n\n" +
            "The microphone is off when the app opens. It opens only while you record, while the tuner is on screen, and for a " +
            "sung or played Ear drill, and the screen always shows when it is on.\n\n" +
            "Your recordings, writing and projects stay in the app's storage on this phone. A backup goes only to the folder you " +
            "pick, and an export only to the file you save. Analysis, transcription, stem separation and speech to text all run " +
            "on the phone."
}

@Composable
fun NoticesScreen(model: AppModel) {
    var open by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        TitleBar {
            Sq("‹", { model.showNotices = false }, modifier = Modifier.semantics { contentDescription = "Back to the Library" })
            Text("LICENCES AND PRIVACY", style = T.title, modifier = Modifier.weight(1f), maxLines = 1)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Kv("PRIVACY") { Text(Notices.PRIVACY, style = T.body.copy(lineHeight = 18.sp)) }
            Text("WHAT THE APP IS MADE WITH", style = T.label.copy(color = C.acc), modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 4.dp))
            for (n in Notices.all) {
                Column(Modifier.fillMaxWidth().underline().clickable(role = Role.Button) { open = if (open == n.name) null else n.name }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(n.name, style = T.body.copy(color = C.hi, fontWeight = FontWeight.Bold))
                    Text(n.use, style = T.small)
                    Text("${n.licence} · © ${n.holder}${if (open == n.name) "" else "  ›"}", style = T.label)
                }
                if (open == n.name) for (f in n.files) {
                    val text = remember(f) { runCatching { model.app.assets.open("licenses/$f").bufferedReader().use { it.readText() } }.getOrElse { "The licence file $f couldn't be read." } }
                    // The LiteRT notices run to 2 MB: show the start and say so rather than lay out all of it.
                    val shown = if (text.length > 60_000) text.take(60_000) + "\n\n… (${text.length / 1000} kB in all; the full text is in the APK at assets/licenses/$f)" else text
                    Text(shown, style = T.small.copy(fontSize = 11.sp, lineHeight = 14.sp), modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        }
    }
}
