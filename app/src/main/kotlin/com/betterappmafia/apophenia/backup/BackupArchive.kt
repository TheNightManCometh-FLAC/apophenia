package com.betterappmafia.apophenia.backup

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The backup file (plan §11): one zip holding `manifest.properties`, `db/apophenia.db` and every
 * take as `audio/<name>.wav`. Versioned: a build refuses a backup from a newer format or a newer
 * database schema instead of half-reading it. Pure JVM, so it's tested on the desktop.
 */
object BackupArchive {
    const val FORMAT = "apophenia-backup"
    const val VERSION = 1
    private const val MANIFEST = "manifest.properties"
    private const val DB = "db/apophenia.db"
    private const val AUDIO = "audio/"

    data class Summary(val created: Long, val appVersion: String, val schema: Int, val audioCount: Int, val audioBytes: Long)

    sealed interface Result {
        data class Ok(val summary: Summary, val db: File, val audioDir: File) : Result
        data class Refused(val reason: String) : Result
    }

    /** Writes [db] (a consistent snapshot) and [audio]. Audio is stored, not deflated: WAV barely shrinks. */
    fun write(out: OutputStream, db: File, audio: List<File>, appVersion: String, schema: Int, now: Long) {
        ZipOutputStream(out.buffered()).use { zip ->
            val m = Properties().apply {
                setProperty("format", FORMAT); setProperty("v", VERSION.toString())
                setProperty("app", appVersion); setProperty("schema", schema.toString()); setProperty("created", now.toString())
                setProperty("audio.count", audio.size.toString()); setProperty("audio.bytes", audio.sumOf { it.length() }.toString())
                audio.forEachIndexed { i, f -> setProperty("audio.$i", f.name); setProperty("audio.$i.size", f.length().toString()) }
            }
            zip.putNextEntry(ZipEntry(MANIFEST)); m.store(zip, "Apophenia backup"); zip.closeEntry()
            zip.putNextEntry(ZipEntry(DB)); db.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            for (f in audio) {
                val e = ZipEntry(AUDIO + f.name).apply {
                    method = ZipEntry.STORED; size = f.length(); compressedSize = f.length(); crc = crcOf(f)
                }
                zip.putNextEntry(e); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
    }

    /**
     * Unpacks into [dest] (emptied first) and checks it against the manifest. Nothing outside [dest]
     * is touched; the caller swaps it in only on [Result.Ok]. Zip CRCs are checked as entries are read.
     */
    fun extract(input: InputStream, dest: File, maxSchema: Int): Result {
        dest.deleteRecursively(); dest.mkdirs()
        val audioDir = File(dest, "audio").apply { mkdirs() }
        val dbFile = File(dest, "apophenia.db")
        var manifest: Properties? = null
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name
                    when {
                        e.isDirectory -> {}
                        name == MANIFEST -> manifest = Properties().apply { load(zip) }
                        name == DB -> dbFile.outputStream().use { zip.copyTo(it) }
                        name.startsWith(AUDIO) && isBareWav(name.removePrefix(AUDIO)) ->
                            File(audioDir, name.removePrefix(AUDIO)).outputStream().use { zip.copyTo(it) }
                        else -> return Result.Refused("It holds a file this version doesn't know (\"$name\").")
                    }
                }
            }
        } catch (e: java.util.zip.ZipException) {
            return Result.Refused("The file is damaged (${e.message}).")
        }
        val m = manifest ?: return Result.Refused("This isn't an Apophenia backup (no manifest).")
        if (m.getProperty("format") != FORMAT) return Result.Refused("This isn't an Apophenia backup.")
        val v = m.getProperty("v")?.toIntOrNull() ?: return Result.Refused("The backup's version is unreadable.")
        if (v > VERSION) return Result.Refused("It was made by a newer Apophenia. Update the app, then restore.")
        val schema = m.getProperty("schema")?.toIntOrNull() ?: return Result.Refused("The backup's database version is unreadable.")
        if (schema > maxSchema) return Result.Refused("Its database is newer than this app's. Update the app, then restore.")
        if (!dbFile.isFile || dbFile.length() == 0L) return Result.Refused("The backup has no database.")
        val count = m.getProperty("audio.count")?.toIntOrNull() ?: return Result.Refused("The backup's file list is unreadable.")
        for (i in 0 until count) {
            val name = m.getProperty("audio.$i") ?: return Result.Refused("The backup's file list is incomplete.")
            val size = m.getProperty("audio.$i.size")?.toLongOrNull()
            val f = File(audioDir, name)
            if (!isBareWav(name) || !f.isFile || f.length() != size) return Result.Refused("Recording $name is missing or cut short in the backup.")
        }
        val found = audioDir.listFiles().orEmpty().size
        if (found != count) return Result.Refused("The backup holds $found recordings but lists $count.")
        val summary = Summary(
            created = m.getProperty("created")?.toLongOrNull() ?: 0, appVersion = m.getProperty("app") ?: "?",
            schema = schema, audioCount = count, audioBytes = m.getProperty("audio.bytes")?.toLongOrNull() ?: 0,
        )
        return Result.Ok(summary, dbFile, audioDir)
    }

    /** A plain file name ending in .wav: no folders, no "..", nothing that could land outside the audio folder. */
    fun isBareWav(name: String) = name.endsWith(".wav") && name.length > 4 && '/' !in name && '\\' !in name && name != ".." && !name.startsWith(".")

    private fun crcOf(f: File): Long {
        val c = CRC32()
        f.inputStream().use { s -> val b = ByteArray(1 shl 16); while (true) { val n = s.read(b); if (n < 0) break; c.update(b, 0, n) } }
        return c.value
    }
}
