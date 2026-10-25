package com.betterappmafia.apophenia.export

import com.betterappmafia.apophenia.ApopheniaApp
import com.betterappmafia.apophenia.Breadcrumbs
import com.betterappmafia.apophenia.core.catalog.Conform
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.score.FlPackage
import com.betterappmafia.apophenia.core.score.ScoreMidi
import com.betterappmafia.apophenia.core.score.ScoreText
import com.betterappmafia.apophenia.data.LayerMode
import com.betterappmafia.apophenia.data.LayerRow
import com.betterappmafia.apophenia.data.ScrapFormat
import com.betterappmafia.apophenia.data.StackEntity
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The FL Studio package (plan §11) for the stack in the player and its project's Board, as one zip:
 * `Stems/` (each layer as the stack plays it, from the stack's start, plus `Mix.wav`), `Takes/` (the original
 * recordings, unchanged), `MIDI/` (each tab and sheet scrap as a MIDI file, each chord chart as a chord track) and
 * `README.txt`, the manifest. [Claude] The audio is the engine's: 48 kHz 24-bit mono, the same samples the bounce
 * writes; every take of a Board recording that isn't a layer goes in Takes/ too.
 */
class FlStudioExport(private val app: ApopheniaApp) {
    enum class Audio(val label: String, val stems: Boolean, val takes: Boolean) {
        BOTH("Stems and original takes", true, true), STEMS("Stems only", true, false), TAKES("Original takes only", false, true)
    }

    /** Writes the package to [out]; returns a one-line summary for the notice. */
    suspend fun write(out: OutputStream, stack: StackEntity, rows: List<LayerRow>, conforms: Map<Long, Conform>, bpm: Double?, key: KeyMode?, audio: Audio): String {
        val projectName = stack.projectId?.let { app.db.projects().get(it)?.name } ?: "Temporary stack"
        val root = FlPackage.fileName(projectName) + "/"
        val tmp = File(app.cacheDir, "fl-package").apply { deleteRecursively(); mkdirs() }
        val used = HashSet<String>()
        fun unique(path: String): String {
            if (used.add(path.lowercase())) return path
            val dot = path.lastIndexOf('.')
            var n = 2
            while (true) { val p = path.substring(0, dot) + " ($n)" + path.substring(dot); if (used.add(p.lowercase())) return p; n++ }
        }
        try {
            ZipOutputStream(out.buffered(1 shl 16)).use { zip ->
                zip.setLevel(Deflater.BEST_SPEED)
                fun put(name: String, file: File) {
                    zip.putNextEntry(ZipEntry(root + name).apply { time = file.lastModified() })
                    file.inputStream().use { it.copyTo(zip, 1 shl 16) }
                    zip.closeEntry()
                }
                fun put(name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(root + name)); zip.write(bytes); zip.closeEntry() }

                val playable = rows.filter { it.audioPath != null }
                val layers = ArrayList<FlPackage.Layer>()
                var mix: String? = null
                playable.forEachIndexed { i, r ->
                    val l = r.layer
                    val title = FlPackage.fileName(r.title ?: "Recording")
                    var stem: String? = null
                    if (audio.stems) {
                        val f = File(tmp, "stem-${l.id}.wav")
                        if (app.audio.bounceTo(f, l.id) != null) { stem = unique("Stems/%02d %s.wav".format(i + 1, title)); put(stem, f); f.delete() }
                    }
                    var original: String? = null
                    if (audio.takes) {
                        val src = File(app.audioDir, r.audioPath!!)
                        if (src.isFile) { original = unique("Takes/$title - ${r.takeLetter ?: "A"}.wav"); put(original, src) }
                    }
                    val c = conforms[l.id]
                    layers += FlPackage.Layer(
                        i + 1, r.title ?: "Recording", r.takeLetter,
                        when (l.mode) { LayerMode.LOOP -> "loop"; LayerMode.ONE_SHOT -> "one-shot"; LayerMode.FREE -> "free time" },
                        l.startFrames, l.fromFrames, l.toFrames, l.gainDb, l.muted,
                        c?.takeIf { !it.isIdentity }?.let { if (l.conform) it.label else "${it.label} (switched off: played as recorded)" },
                        stem, original,
                    )
                }
                if (audio.stems && playable.isNotEmpty()) {
                    val f = File(tmp, "mix.wav")
                    if (app.audio.bounceTo(f) != null) { mix = unique("Stems/Mix.wav"); put(mix, f); f.delete() }
                }

                // The Board: written music as MIDI, and recordings that aren't layers.
                val midi = ArrayList<Pair<String, String>>()
                val others = ArrayList<String>()
                val board = stack.projectId?.let { app.db.projects().boardRows(it).first() }.orEmpty()
                val inStack = rows.map { it.layer.scrapId }.toSet()
                for (b in board) {
                    val s = b.scrap
                    val title = FlPackage.fileName(s.title ?: s.format.token)
                    runCatching {
                        when (s.format) {
                            ScrapFormat.TAB, ScrapFormat.SHEET -> {
                                val score = ScoreText.decode(b.shownBody).let { if (it.title.isBlank()) it.copy(title = s.title.orEmpty()) else it }
                                val name = unique("MIDI/$title.mid"); put(name, ScoreMidi.write(score)); midi += name to FlPackage.about(score)
                            }
                            ScrapFormat.CHORD_CHART -> {
                                val score = FlPackage.chartScore(s.title.orEmpty(), b.shownBody, KeyMode.parseOrNull(s.key), s.bpmLow ?: bpm ?: 120.0)
                                val name = unique("MIDI/$title (chords).mid"); put(name, ScoreMidi.write(score)); midi += name to "chord track · " + FlPackage.about(score)
                            }
                            ScrapFormat.RECORDING -> if (audio.takes && s.id !in inStack) for (t in app.db.takes().takesOf(s.id)) {
                                val src = File(app.audioDir, t.audioPath)
                                if (src.isFile) { val name = unique("Takes/$title - ${t.letter}.wav"); put(name, src); others += name }
                            }
                            else -> {}
                        }
                    }.onFailure { Breadcrumbs.add("fl package skipped ${s.id}: $it") }
                }

                val info = FlPackage.Info(
                    projectName, stack.name, SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()), bpm, key, 48_000,
                    layers, mix, midi, others,
                )
                put("README.txt", FlPackage.manifest(info).replace("\n", "\r\n").toByteArray(Charsets.UTF_8))
                return listOfNotNull(
                    "${layers.count { it.stem != null }} stems".takeIf { audio.stems },
                    "${layers.count { it.original != null } + others.size} takes".takeIf { audio.takes },
                    "${midi.size} MIDI files",
                ).joinToString(", ")
            }
        } finally {
            tmp.deleteRecursively()
        }
    }
}
