package com.betterappmafia.apophenia.capture

import com.betterappmafia.apophenia.Breadcrumbs
import com.betterappmafia.apophenia.audio.Recorded
import com.betterappmafia.apophenia.audio.Wav
import com.betterappmafia.apophenia.data.ApopheniaDatabase
import com.betterappmafia.apophenia.data.ScrapEntity
import com.betterappmafia.apophenia.data.ScrapFormat
import com.betterappmafia.apophenia.data.TakeEntity
import com.betterappmafia.apophenia.data.ValueSource
import com.betterappmafia.apophenia.data.trashCutoff
import java.io.File

/** Turning audio on disk into Library rows: new takes, recovered takes, and the trash purge. */
class Captures(private val db: ApopheniaDatabase, private val audioDir: File) {

    /**
     * Saves a finished take as a new Library recording, in [projectId] when recorded from its Board (plan §10.1: quick capture goes to the
     * Library). The row is written right away; the save sheet only adds to it, so closing the
     * sheet never loses anything. If the click ran, its tempo is offered as the BPM (source: click).
     */
    suspend fun saveNew(r: Recorded, now: Long = System.currentTimeMillis(), projectId: Long? = null, latencyFrames: Long = 0): Long {
        val id = db.scraps().createRecording(
            ScrapEntity(format = ScrapFormat.RECORDING, createdAt = now, editedAt = now),
            TakeEntity(scrapId = 0, letter = "A", audioPath = r.file.name, durationFrames = r.frames, sampleRate = r.rate, createdAt = now, prerollFrames = r.prerollFrames, latencyFrames = latencyFrames.takeIf { it > 0 }?.toInt(), metronomeGrid = r.metronomeGrid),
            projectId,
        )
        if (r.clickBpm != null) db.scraps().offerBpm(id, r.clickBpm, source = ValueSource.METRONOME)
        saveMarks(r, now)
        return id
    }

    /** Markers set while recording, stored on the take just saved. */
    private suspend fun saveMarks(r: Recorded, now: Long) {
        if (r.marks.isEmpty()) return
        val take = db.marks().takeByPath(r.file.name) ?: return
        r.marks.forEachIndexed { i, f -> db.marks().add(com.betterappmafia.apophenia.data.TakeMarkEntity(takeId = take.id, frame = f, label = "Marker ${i + 1}", createdAt = now)) }
    }

    /** Saves a finished take as the next take of an existing recording. Returns its letter. */
    suspend fun saveAsTakeOf(scrapId: Long, r: Recorded, now: Long = System.currentTimeMillis()): String {
        val letter = db.scraps().addTake(scrapId, TakeEntity(scrapId = scrapId, letter = "?", audioPath = r.file.name, durationFrames = r.frames, sampleRate = r.rate, createdAt = now, prerollFrames = r.prerollFrames, metronomeGrid = r.metronomeGrid), now)
        if (r.clickBpm != null) db.scraps().offerBpm(scrapId, r.clickBpm, source = ValueSource.METRONOME)
        saveMarks(r, now)
        return letter
    }

    /**
     * Launch recovery (plan §10.1): a `.part` left by a crash or a killed process is finished in
     * place (header sizes from the file length) and saved; a finished file the database never got
     * (killed between rename and insert) is saved too. [skip] gives the file recording right now, checked per file.
     * Returns how many takes were recovered.
     */
    suspend fun recover(skip: () -> File?, now: Long = System.currentTimeMillis()): Int {
        if (!audioDir.isDirectory) return 0
        var n = 0
        val known = db.takes().allAudioPaths().toMutableSet()
        audioDir.listFiles { f -> f.name.endsWith(".wav.importing") }.orEmpty().forEach { it.delete() }  // an import cut off: the file is still on the phone
        for (part in audioDir.listFiles { f -> f.name.endsWith(".wav.part") }.orEmpty()) {
            if (part == skip()) continue
            val target = File(audioDir, part.name.removeSuffix(".part"))
            val frames = Wav.repairPart(part, target)
            Breadcrumbs.add("recover part ${part.name} -> $frames")
        }
        for (wav in audioDir.listFiles { f -> f.name.startsWith("take-") && f.name.endsWith(".wav") }.orEmpty()) {
            if (wav.name in known) continue
            val info = Wav.info(wav) ?: continue
            if (info.frames > 0) { saveRecovered(wav, info.frames, now); known += wav.name; n++ }
        }
        return n
    }

    private suspend fun saveRecovered(file: File, frames: Long, now: Long) {
        val made = file.name.removePrefix("take-").removeSuffix(".wav").toLongOrNull() ?: now
        val id = db.scraps().createRecording(
            ScrapEntity(format = ScrapFormat.RECORDING, title = "Recovered take", createdAt = made, editedAt = now),
            TakeEntity(scrapId = 0, letter = "A", audioPath = file.name, durationFrames = frames, sampleRate = Wav.info(file)?.rate ?: 48000, createdAt = made),
        )
        db.tags().tag(id, "recovered", now)
    }

    /** Empties what has been in the trash for 30 days: audio files first, then the rows. */
    suspend fun purgeTrash(now: Long = System.currentTimeMillis()) {
        val cutoff = trashCutoff(now)
        for (path in db.scraps().audioToPurge(cutoff)) File(audioDir, path).delete()
        db.takes().purgeExpired(cutoff)
        db.scraps().purgeExpired(cutoff)
        db.groups().purgeExpired(cutoff)
        db.projects().purgeExpired(cutoff)
    }

    fun fileOf(take: TakeEntity) = File(audioDir, take.audioPath)
}
