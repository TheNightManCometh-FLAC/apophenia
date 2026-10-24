package com.betterappmafia.apophenia.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupArchiveTest {
    private val dir: File = createTempDirectory("backup").toFile()

    private fun file(name: String, bytes: ByteArray) = File(dir, name).apply { writeBytes(bytes) }

    private fun backup(audio: List<File>, schema: Int = 2): ByteArray {
        val db = file("snap.db", ByteArray(4096) { (it % 251).toByte() })
        return ByteArrayOutputStream().also { BackupArchive.write(it, db, audio, "0.1.1", schema, 1_800_000_000_000L) }.toByteArray()
    }

    private fun refusedReason(r: BackupArchive.Result) = (r as BackupArchive.Result.Refused).reason

    @Test fun `a backup restores the database and every take byte for byte`() {
        val a = file("take-1.wav", ByteArray(10_000) { 7 })
        val b = file("take-2.wav", ByteArray(333) { (it * 3).toByte() })
        val r = BackupArchive.extract(ByteArrayInputStream(backup(listOf(a, b))), File(dir, "out"), maxSchema = 2)
        r as BackupArchive.Result.Ok
        assertEquals(2, r.summary.audioCount)
        assertEquals(10_333L, r.summary.audioBytes)
        assertEquals("0.1.1", r.summary.appVersion)
        assertArrayEquals(a.readBytes(), File(r.audioDir, "take-1.wav").readBytes())
        assertArrayEquals(b.readBytes(), File(r.audioDir, "take-2.wav").readBytes())
        assertArrayEquals(File(dir, "snap.db").readBytes(), r.db.readBytes())
    }

    @Test fun `a backup with an empty Library still restores`() {
        assertTrue(BackupArchive.extract(ByteArrayInputStream(backup(emptyList())), File(dir, "out"), 2) is BackupArchive.Result.Ok)
    }

    @Test fun `a backup from a newer database schema is refused, not half-read`() {
        val r = BackupArchive.extract(ByteArrayInputStream(backup(emptyList(), schema = 3)), File(dir, "out"), maxSchema = 2)
        assertTrue(refusedReason(r).contains("newer"))
    }

    @Test fun `an older schema is accepted, since the database migrates it on open`() {
        assertTrue(BackupArchive.extract(ByteArrayInputStream(backup(emptyList(), schema = 1)), File(dir, "out"), 2) is BackupArchive.Result.Ok)
    }

    @Test fun `a truncated backup is refused`() {
        val whole = backup(listOf(file("take-1.wav", ByteArray(50_000) { 1 })))
        val r = BackupArchive.extract(ByteArrayInputStream(whole.copyOf(whole.size - 30_000)), File(dir, "out"), 2)
        assertTrue(r is BackupArchive.Result.Refused)
    }

    @Test fun `an entry that tries to climb out of the folder is refused and nothing lands outside`() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z -> z.putNextEntry(ZipEntry("audio/../../evil.wav")); z.write(1); z.closeEntry() }
        }.toByteArray()
        val r = BackupArchive.extract(ByteArrayInputStream(bytes), File(dir, "out"), 2)
        assertTrue(r is BackupArchive.Result.Refused)
        assertFalse(File(dir, "evil.wav").exists())
    }

    @Test fun `a zip that isn't a backup is refused`() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z -> z.putNextEntry(ZipEntry("audio/x.wav")); z.write(1); z.closeEntry() }
        }.toByteArray()
        assertTrue(refusedReason(BackupArchive.extract(ByteArrayInputStream(bytes), File(dir, "out"), 2)).contains("manifest"))
    }

    @Test fun `only bare wav names are allowed`() {
        assertTrue(BackupArchive.isBareWav("take-1.wav"))
        assertFalse(BackupArchive.isBareWav("../take.wav"))
        assertFalse(BackupArchive.isBareWav("sub/take.wav"))
        assertFalse(BackupArchive.isBareWav(".wav"))
        assertFalse(BackupArchive.isBareWav("take.mp3"))
    }
}
