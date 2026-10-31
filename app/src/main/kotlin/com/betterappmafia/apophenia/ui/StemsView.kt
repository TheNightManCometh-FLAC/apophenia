package com.betterappmafia.apophenia.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterappmafia.apophenia.audio.DemucsModel
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
 * A recording's STEMS tab (C4): the four stems of this take (Spleeter, or Demucs once the user adds it), each its own recording in the Library,
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
            Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) { Sq("TRY AGAIN", { separator.start(take, title, job.stems, job.model) }) }
            return
        }
        stems.isEmpty() -> {
            Kv("STEMS") {
                Text("Splits this take into vocals, drums, bass and other, in stereo, on the phone. Pick the stems you want; each becomes its own recording in the Library, linked here. The take itself is never changed.", style = T.small.copy(lineHeight = 16.sp))
            }
            Pick(model, take) { picked, which -> separator.start(take, title, picked, which) }
            return
        }
    }
    // The latest of each stem, when a take was separated more than once (say Spleeter, then Demucs).
    val ordered = Spleeter.STEMS.mapNotNull { name -> stems.filter { it.stem == name }.maxByOrNull { it.madeAt } }
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
                Line1("${clock(st.durationFrames, st.sampleRate)} · ${modelName(st.model)} · its own recording${if (state.isNotEmpty()) " · $state" else ""}", T.small)
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
    var again by remember(take.id) { mutableStateOf(false) }
    if (again) Pick(model, take) { picked, which -> again = false; separator.start(take, title, picked, which) }
    else Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) { Sq("SEPARATE AGAIN ▾", { again = true }) }
    Hint(if (ordered.size == Spleeter.STEMS.size && ordered.all { it.model == Separator.MODEL_ID }) "Played together with nothing muted, the stems add back up to the take. Separating again makes new recordings of the stems you pick; these stay until you delete them."
        else "Separating again makes new recordings of the stems you pick (the ones not made yet, new copies, or with the other model); these stay until you delete them.")
    if (!again) Limits(ordered.lastOrNull()?.model ?: Separator.MODEL_ID)
}

fun modelName(id: String) = if (id == Separator.DEMUCS_ID) "DEMUCS" else "SPLEETER"

/** The model a separation starts on: the one picked in AI › Separate stems, but never Demucs before it's added. */
fun defaultStemsModel(model: AppModel, added: Boolean) =
    if (added && model.stemsModel != Separator.MODEL_ID) Separator.DEMUCS_ID else Separator.MODEL_ID

/**
 * Which stems to make (Christopher, 28 Oct 2026: pick before separating) and with which model. [Claude] All four start
 * picked; the model starts on the default from AI › Separate stems ([defaultStemsModel]). Demucs isn't part of the app: choosing it before
 * it's added shows the two steps, a link that downloads the file in the browser and a picker for the download
 * (Christopher, 30 Oct 2026: "a link to it when you see the option so it can be as easy as possible").
 */
@Composable
private fun Pick(model: AppModel, take: TakeEntity, go: (Set<String>, String) -> Unit) {
    val demucs by model.app.demucs.state.collectAsState()
    val added = demucs is DemucsModel.State.Added
    var picked by remember(take.id) { mutableStateOf(Spleeter.STEMS.toSet()) }
    var which by remember(take.id, added, model.stemsModel) { mutableStateOf(defaultStemsModel(model, added)) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("MODEL", style = T.label)
        Sq((if (which == Separator.MODEL_ID) "✓ " else "") + "SPLEETER", { which = Separator.MODEL_ID }, on = which == Separator.MODEL_ID)
        Sq((if (which == Separator.DEMUCS_ID) "✓ " else "") + if (added) "DEMUCS" else "DEMUCS · ADD ›", { which = Separator.DEMUCS_ID }, on = which == Separator.DEMUCS_ID)
    }
    if (which == Separator.DEMUCS_ID && !added) {
        AddDemucs(model, demucs)
        return
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Spleeter.STEMS.forEach { name ->
            Sq((if (name in picked) "✓ " else "") + name.uppercase(), { picked = if (name in picked) picked - name else picked + name }, on = name in picked)
        }
    }
    Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
        Sq(if (picked.isEmpty()) "PICK A STEM" else "▶ SEPARATE TAKE ${take.letter}", { if (picked.isNotEmpty()) go(picked, which) }, on = picked.isNotEmpty())
    }
    Limits(which)
}

/** The two steps for adding Demucs: download the file in the browser, then pick it. Also in Settings › AI MODELS. */
@Composable
fun AddDemucs(model: AppModel, state: DemucsModel.State) {
    val ctx = LocalContext.current
    Hint(
        "Demucs (Meta's HT-Demucs) separates more cleanly than Spleeter, over the full band, but takes longer. Its model isn't part of " +
            "Apophenia: download the file (${DemucsModel.MB} MB) in your browser, then pick it here. The app checks it's the right file " +
            "and keeps a copy; you can delete the download after.",
    )
    if (state is DemucsModel.State.Adding) {
        Kv("DEMUCS") { Text("Adding it… ${state.percent}%", style = T.body.copy(color = C.hi)) }
        return
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Sq("1 · DOWNLOAD IT ›", { model.openLink(ctx, DemucsModel.DOWNLOAD_URL) })
        Sq("2 · PICK THE DOWNLOAD ›", { model.pickDemucs() })
    }
}

private fun stemTitle(st: StemRow) = st.title?.takeIf { it.isNotBlank() } ?: st.stem

/** Plan §10.6: the honest limits, in the app, for the model chosen. */
@Composable
private fun Limits(which: String) = Hint(
    if (which == Separator.DEMUCS_ID)
        "Demucs predicts each stem over the full band; played together they come close to the take but don't add back exactly. " +
            "It is slower than Spleeter and needs about 1 GB of memory while it runs. The first ${Separator.MAX_SECONDS / 60} minutes of a file."
    else "Spleeter separates up to about 11 kHz and carries the band above along with each stem. Expect some bleed between stems, " +
        "most of all on a dense mix; a solo instrument or voice usually lands in one stem with traces in the others. The first ${Separator.MAX_SECONDS / 60} minutes of a file.",
)

@Composable
private fun Hint(text: String) = Text(text, style = T.small.copy(lineHeight = 16.sp), modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
