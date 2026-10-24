package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.audio.Separator
import com.betterappmafia.apophenia.audio.Wav
import com.betterappmafia.apophenia.core.analysis.Spleeter
import com.betterappmafia.apophenia.data.ScrapEntity
import com.betterappmafia.apophenia.data.StemRow
import com.betterappmafia.apophenia.data.TakeEntity
import com.betterappmafia.apophenia.ui.theme.C
import com.betterappmafia.apophenia.ui.theme.T
import kotlinx.coroutines.launch
import java.io.File

/**
 * A recording's STEMS tab (C4): Spleeter's four stems of this take, each its own recording in the Library,
 * with mute and solo (M · S ▾) for hearing them together. The take is never changed.
 */
@Composable
fun StemsSection(model: AppModel, s: ScrapEntity, take: TakeEntity, title: String) {
    val separator = model.app.separator
    val jobs by separator.jobs.collectAsState()
    val job = jobs.firstOrNull { it.takeId == take.id }
    val stems by remember(take.id) { model.app.db.stems().of(take.id) }.collectAsState(emptyList())

    when {
        job != null && job.error == null -> {
            Kv("STEMS") { Text("Separating on the phone… ${job.percent}%${if (job.total > 0) " · part ${job.done} of ${job.total}" else " · getting the take ready"}", style = T.body.copy(color = C.hi)) }
            Hint("It carries on if you leave this screen or the app; the AI mode lists it under JOBS.")
            return
        }
        job?.error != null -> {
            Kv("STEMS") { Text("It didn't finish: ${job!!.error}", style = T.body.copy(color = C.acc)) }
            Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) { Sq("TRY AGAIN", { separator.start(take, title) }) }
            return
        }
        stems.isEmpty() -> {
            Kv("STEMS") {
                Text("Spleeter splits this take into vocals, drums, bass and other, in stereo, on the phone. Each stem becomes its own recording in the Library, linked here. The take itself is never changed.", style = T.small.copy(lineHeight = 16.sp))
            }
            Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) { Sq("▶ SEPARATE TAKE ${take.letter}", { separator.start(take, title) }, on = true) }
            Limits()
            return
        }
    }
    val ordered = Spleeter.STEMS.mapNotNull { name -> stems.firstOrNull { it.stem == name } }
    var muted by remember(take.id) { mutableStateOf(setOf<String>()) }
    var soloed by remember(take.id) { mutableStateOf(setOf<String>()) }
    val audible = ordered.filter { if (soloed.isNotEmpty()) it.stem in soloed else it.stem !in muted }
    val player by model.app.audio.player.collectAsState()

    ordered.forEach { st ->
        var menu by remember(st.takeId) { mutableStateOf(false) }
        val state = when { st.stem in soloed -> "SOLO"; soloed.isNotEmpty() || st.stem in muted -> "MUTED"; else -> "" }
        Row(Modifier.fillMaxWidth().height(58.dp).underline().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(st.stem.uppercase(), style = T.body.copy(color = if (st in audible) C.hi else C.dim, fontWeight = FontWeight.Bold))
                Line1("${clock(st.durationFrames, st.sampleRate)} · its own recording${if (state.isNotEmpty()) " · $state" else ""}", T.small)
            }
            Box {
                Sq("M · S ▾", { menu = true }, on = state.isNotEmpty())
                ConsoleMenu(menu, { menu = false }, st.stem.uppercase(), listOf(
                    MenuItem("Mute", if (st.stem in muted) "✓" else "") { muted = if (st.stem in muted) muted - st.stem else muted + st.stem },
                    MenuItem("Solo", if (st.stem in soloed) "✓" else "") { soloed = if (st.stem in soloed) soloed - st.stem else soloed + st.stem },
                    MenuItem("Play this stem", "▶") {
                        model.app.audio.load(st.scrapId, st.takeId, stemTitle(st), File(model.app.audioDir, st.audioPath).absolutePath, true)
                    },
                    MenuItem("Open it", "›") { model.open(st.scrapId, null) },
                ))
            }
        }
    }
    val mixing = remember(take.id) { mutableStateOf(false) }
    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val label = when {
            mixing.value -> "MIXING…"
            audible.isEmpty() -> "ALL MUTED"
            audible.size == ordered.size -> "▶ PLAY ALL STEMS"
            else -> "▶ PLAY " + audible.joinToString(" + ") { it.stem.uppercase() }
        }
        Sq(label, {
            if (mixing.value || audible.isEmpty()) return@Sq
            mixing.value = true
            model.app.io.launch {
                try {
                    val files = audible.map { File(model.app.audioDir, it.audioPath) }
                    val out = File(model.app.cacheDir, "stem-mix/take-${take.id}-${audible.joinToString("-") { it.stem }}.wav")
                    if (!out.isFile) {
                        out.parentFile?.mkdirs()
                        val part = File(out.path + ".part")
                        Wav.mix(files, part)
                        part.renameTo(out)
                    }
                    val first = audible.first()
                    model.app.audio.load(first.scrapId, first.takeId, "$title ${take.letter} · " + audible.joinToString(" + ") { it.stem }, out.absolutePath, true)
                } finally {
                    mixing.value = false
                }
            }
        }, on = audible.isNotEmpty())
        if (player != null && ordered.any { it.takeId == player!!.takeId }) Sq("■ STOP", { model.app.audio.togglePlay() })
    }
    Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
        Sq("SEPARATE AGAIN", { separator.start(take, title) })
    }
    Hint("Played together with nothing muted, the stems add back up to the take. Separating again makes four new recordings; these stay until you delete them.")
    Limits()
}

private fun stemTitle(st: StemRow) = st.title?.takeIf { it.isNotBlank() } ?: st.stem

/** Plan §10.6: the honest limits, in the app. */
@Composable
private fun Limits() = Hint(
    "Spleeter separates up to about 11 kHz and carries the band above along with each stem. Expect some bleed between stems, " +
        "most of all on a dense mix; a solo instrument or voice usually lands in one stem with traces in the others. The first ${Separator.MAX_SECONDS / 60} minutes of a file.",
)

@Composable
private fun Hint(text: String) = Text(text, style = T.small.copy(lineHeight = 16.sp), modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
