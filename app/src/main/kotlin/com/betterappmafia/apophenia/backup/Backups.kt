package com.betterappmafia.apophenia.backup

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.useWriterConnection
import com.betterappmafia.apophenia.ApopheniaApp
import com.betterappmafia.apophenia.Breadcrumbs
import com.betterappmafia.apophenia.BuildInfo
import com.betterappmafia.apophenia.data.AppSettingEntity
import com.betterappmafia.apophenia.data.DATABASE_NAME
import com.betterappmafia.apophenia.data.SCHEMA_VERSION
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup to a folder you choose and restore from one (plan §11), with no permissions: the folder
 * comes from Android's own picker. Restoring keeps the phone's current data aside in
 * files/before-restore until the next restore, so a wrong pick can be undone by hand.
 */
class Backups(private val app: ApopheniaApp) {
    private val settings get() = app.db.settings()

    suspend fun folder(): Uri? = settings.get(KEY_FOLDER)?.let(Uri::parse)
    suspend fun lastBackup(): Long? = settings.get(KEY_LAST)?.toLongOrNull()

    suspend fun setFolder(tree: Uri) {
        app.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        settings.put(AppSettingEntity(KEY_FOLDER, tree.toString()))
    }

    fun folderName(tree: Uri): String = runCatching {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        app.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: "chosen folder"

    /** Writes a new backup file into the chosen folder. Returns its name. */
    suspend fun backupNow(tree: Uri, now: Long = System.currentTimeMillis()): String {
        val snapshot = File(app.cacheDir, "backup-snapshot.db").apply { delete() }
        // A consistent copy while the app keeps running: SQLite's VACUUM INTO.
        app.db.useWriterConnection { c -> c.usePrepared("VACUUM INTO ?") { st -> st.bindText(1, snapshot.absolutePath); st.step() } }
        val audio = app.audioDir.listFiles { f -> f.isFile && f.name.endsWith(".wav") }.orEmpty().sortedBy { it.name }
        val name = "Apophenia backup " + SimpleDateFormat("yyyy-MM-dd HHmm", Locale.US).format(Date(now)) + ".zip"
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val doc = DocumentsContract.createDocument(app.contentResolver, parent, "application/zip", name)
            ?: error("The folder wouldn't take a new file.")
        try {
            app.contentResolver.openOutputStream(doc, "w")!!.use { out ->
                BackupArchive.write(out, snapshot, audio, BuildInfo.version(app), SCHEMA_VERSION, now)
            }
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(app.contentResolver, doc) }   // never leave a half backup behind
            throw e
        } finally {
            snapshot.delete()
        }
        settings.put(AppSettingEntity(KEY_LAST, now.toString()))
        Breadcrumbs.add("backup ${audio.size} takes")
        return name
    }

    /** Unpacks and checks a backup without touching the current data. */
    fun inspect(file: Uri): BackupArchive.Result {
        val input = app.contentResolver.openInputStream(file) ?: return BackupArchive.Result.Refused("The file couldn't be opened.")
        return input.use { BackupArchive.extract(it, File(app.filesDir, "restore-tmp"), SCHEMA_VERSION) }
    }

    /**
     * Swaps a checked backup in. The app must restart afterwards (the open database is closed here).
     * The current database and audio move to files/before-restore first.
     */
    fun apply(ok: BackupArchive.Result.Ok) {
        val aside = File(app.filesDir, "before-restore")
        aside.deleteRecursively(); aside.mkdirs()
        app.db.close()
        val dbFile = app.getDatabasePath(DATABASE_NAME)
        for (suffix in listOf("", "-wal", "-shm")) {
            val f = File(dbFile.path + suffix)
            if (f.exists()) { f.copyTo(File(aside, f.name), overwrite = true); f.delete() }
        }
        try {
            if (app.audioDir.exists() && !app.audioDir.renameTo(File(aside, "audio"))) error("Couldn't move the current recordings aside.")
            dbFile.parentFile?.mkdirs()
            ok.db.copyTo(dbFile, overwrite = true)
            if (!ok.audioDir.renameTo(app.audioDir)) error("Couldn't move the restored recordings in.")
        } catch (e: Exception) {
            // Put the phone's own data back exactly as it was.
            Breadcrumbs.add("restore failed, rolling back: $e")
            app.audioDir.deleteRecursively()
            File(aside, "audio").renameTo(app.audioDir)
            for (suffix in listOf("", "-wal", "-shm")) File(aside, dbFile.name + suffix).takeIf { it.exists() }?.copyTo(File(dbFile.path + suffix), overwrite = true)
            throw e
        }
        File(app.filesDir, "restore-tmp").deleteRecursively()
        Breadcrumbs.add("restored ${ok.summary.audioCount} takes")
    }

    companion object {
        private const val KEY_FOLDER = "backup.folder"
        private const val KEY_LAST = "backup.last"
    }
}
