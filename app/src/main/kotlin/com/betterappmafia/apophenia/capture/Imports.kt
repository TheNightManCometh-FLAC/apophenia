package com.betterappmafia.apophenia.capture

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import com.betterappmafia.apophenia.Breadcrumbs
import com.betterappmafia.apophenia.audio.Resampler
import com.betterappmafia.apophenia.audio.WavWriter
import com.betterappmafia.apophenia.core.score.MusicXmlRead
import com.betterappmafia.apophenia.core.score.ScoreText
import com.betterappmafia.apophenia.data.ApopheniaDatabase
import com.betterappmafia.apophenia.data.ScrapEntity
import com.betterappmafia.apophenia.data.ScrapFormat
import com.betterappmafia.apophenia.data.TakeEntity
import java.io.File
import java.nio.ByteOrder

/**
 * Files from the phone as fragments (plan §3.1, Christopher 22 Oct 2026). Audio in any format the
 * phone decodes (MP3, AAC/M4A, FLAC, Ogg, Opus, WAV…) becomes a recording, converted to the app's
 * take format; .txt/.md become text notes; ChordPro files (.cho, .chopro, .chordpro, .crd, .pro)
 * become lyrics; MusicXML (.musicxml, .xml, .mxl) becomes tab or sheet music. Each is tagged "imported", titled from its file name.
 */
class Imports(private val context: Context, private val db: ApopheniaDatabase, private val audioDir: File) {

    sealed interface Result {
        data class Done(val scrapId: Long, val name: String, val notRead: List<String> = emptyList()) : Result
        data class Refused(val name: String, val why: String) : Result
    }

    private val chordPro = setOf("cho", "chopro", "chordpro", "crd", "pro")
    private val text = setOf("txt", "md", "text")
    private val musicXml = setOf("musicxml", "xml", "mxl")

    suspend fun import(uri: Uri, projectId: Long?, now: Long = System.currentTimeMillis()): Result {
        val name = displayName(uri) ?: uri.lastPathSegment ?: "file"
        val ext = name.substringAfterLast('.', "").lowercase()
        val mime = context.contentResolver.getType(uri).orEmpty()
        val title = name.substringBeforeLast('.').ifBlank { name }.take(80)
        return runCatching {
            when {
                ext in chordPro -> written(ScrapFormat.LYRIC, readText(uri), title, projectId, now)
                ext in musicXml || mime.contains("musicxml") -> score(uri, title, projectId, now)
                ext in text || mime.startsWith("text/") -> written(ScrapFormat.TEXT, readText(uri), title, projectId, now)
                ext == "mid" || ext == "midi" || mime.contains("midi") -> Result.Refused(name, "MIDI files arrive with written music (M5).")
                mime.startsWith("audio/") || mime.startsWith("video/") || ext in setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "amr", "3gp", "mp4", "webm", "aif", "aiff") ->
                    audio(uri, title, projectId, now)
                else -> Result.Refused(name, "Not audio or text (${mime.ifEmpty { ext.ifEmpty { "unknown type" } }}).")
            }
        }.getOrElse { Breadcrumbs.add("import failed $name: $it"); Result.Refused(name, it.message ?: it.toString()) }
            .let { if (it is Result.Done) it.copy(name = name) else it }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun readText(uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readNBytes(2_000_000) } ?: error("It couldn't be opened.")
        return String(bytes, Charsets.UTF_8).removePrefix("﻿").replace("\r\n", "\n")
    }

    /** A MusicXML score (plan §11) as written music: a tab scrap when a part is fretted, else sheet music. */
    private suspend fun score(uri: Uri, title: String, projectId: Long?, now: Long): Result {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readNBytes(32_000_000) } ?: error("It couldn't be opened.")
        val read = MusicXmlRead.read(bytes)
        val s = read.score
        val format = if (s.parts.any { it.fretted }) ScrapFormat.TAB else ScrapFormat.SHEET
        val name = s.title.ifBlank { title }.take(80)
        val id = db.scraps().createWritten(ScrapEntity(format = format, title = name, body = ScoreText.encode(s), createdAt = now, editedAt = now), projectId)
        db.tags().tag(id, "imported", now)
        return Result.Done(id, name, read.dropped)
    }

    private suspend fun written(format: ScrapFormat, body: String, title: String, projectId: Long?, now: Long): Result {
        val id = db.scraps().createWritten(ScrapEntity(format = format, title = title, body = body, createdAt = now, editedAt = now), projectId)
        db.tags().tag(id, "imported", now)
        return Result.Done(id, title)
    }

    /** Decodes with MediaExtractor + MediaCodec, converts to 48 kHz 24-bit and writes a take: stereo stays stereo (first two channels), mono stays mono. */
    private suspend fun audio(uri: Uri, title: String, projectId: Long?, now: Long): Result {
        audioDir.mkdirs()
        val final = File(audioDir, "take-$now.wav")
        val part = File(audioDir, "take-$now.wav.importing")  // not .part: launch recovery must not take a half import for a crashed take
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: return Result.Refused(title, "No audio in it.")
            extractor.selectTrack(track)
            val inFormat = extractor.getTrackFormat(track)
            val codec = MediaCodec.createDecoderByType(inFormat.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(inFormat, null, null, 0)
            codec.start()
            var rate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false
            var resampler = Resampler(rate)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var left = FloatArray(8192)
            var right = FloatArray(8192)
            val stereo = channels >= 2
            var resamplerR = Resampler(rate)
            val outL = ArrayList<Float>(); val outR = ArrayList<Float>()
            var inter = FloatArray(16384)
            WavWriter(part, channels = if (stereo) 2 else 1).use { w ->
                while (true) {
                    if (!inputDone) {
                        val ib = codec.dequeueInputBuffer(10_000)
                        if (ib >= 0) {
                            val n = extractor.readSampleData(codec.getInputBuffer(ib)!!, 0)
                            if (n < 0) { codec.queueInputBuffer(ib, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                            else { codec.queueInputBuffer(ib, 0, n, extractor.sampleTime, 0); extractor.advance() }
                        }
                    }
                    val ob = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        ob == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val f = codec.outputFormat
                            rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                            resampler = Resampler(rate)
                            resamplerR = Resampler(rate)
                        }
                        ob >= 0 -> {
                            val buf = codec.getOutputBuffer(ob)!!.order(ByteOrder.nativeOrder())
                            buf.position(info.offset); buf.limit(info.offset + info.size)
                            val frames = info.size / (channels * if (floatPcm) 4 else 2)
                            if (left.size < frames) { left = FloatArray(frames); right = FloatArray(frames) }
                            for (i in 0 until frames) {
                                // Stereo keeps the first two channels (the isolator needs where things are panned); more are dropped.
                                for (c in 0 until channels) {
                                    val v = if (floatPcm) buf.getFloat() else buf.getShort() / 32768f
                                    if (c == 0) { left[i] = v; right[i] = v } else if (c == 1) right[i] = v
                                }
                                if (!stereo) left[i] = (left[i] + right[i]) / 2
                            }
                            if (stereo) {
                                outL.clear(); outR.clear()
                                resampler.process(left, frames) { out, k -> for (j in 0 until k) outL.add(out[j]) }
                                resamplerR.process(right, frames) { out, k -> for (j in 0 until k) outR.add(out[j]) }
                                val k = minOf(outL.size, outR.size)
                                if (inter.size < 2 * k) inter = FloatArray(2 * k)
                                for (j in 0 until k) { inter[2 * j] = outL[j]; inter[2 * j + 1] = outR[j] }
                                w.write(inter, 2 * k)
                            } else resampler.process(left, frames) { out, k -> w.write(out, k) }
                            codec.releaseOutputBuffer(ob, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                        }
                    }
                }
                codec.stop(); codec.release()
                if (w.frames == 0L) { part.delete(); return Result.Refused(title, "No sound decoded.") }
            }
        } finally {
            extractor.release()
        }
        if (!part.renameTo(final)) return Result.Refused(title, "It couldn't be saved.")
        val frames = com.betterappmafia.apophenia.audio.Wav.info(final)?.frames ?: 0L
        val id = db.scraps().createRecording(
            ScrapEntity(format = ScrapFormat.RECORDING, title = title, createdAt = now, editedAt = now),
            TakeEntity(scrapId = 0, letter = "A", audioPath = final.name, durationFrames = frames, sampleRate = 48_000, createdAt = now),
            projectId,
        )
        db.tags().tag(id, "imported", now)
        return Result.Done(id, title)
    }
}
