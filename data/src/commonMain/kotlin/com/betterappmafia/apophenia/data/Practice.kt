package com.betterappmafia.apophenia.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One stretch of practice (plan §7 Practice log: a simple timer you can attach to a scrap, project or
 * exercise; optional note; history; no structured session builder). Schema v9. Deleting the scrap or
 * project keeps the time (the attachment is cleared), so the history never shrinks by itself.
 */
@Entity(
    tableName = "practice",
    foreignKeys = [
        ForeignKey(ScrapEntity::class, ["id"], ["scrap_id"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(ProjectEntity::class, ["id"], ["project_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("scrap_id"), Index("project_id"), Index("started_at")],
)
data class PracticeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    val seconds: Long,
    @ColumnInfo(name = "scrap_id") val scrapId: Long? = null,
    @ColumnInfo(name = "project_id") val projectId: Long? = null,
    /** An exercise or anything else practised, in your words (the curated Exercises come later). */
    val exercise: String? = null,
    val note: String? = null,
)

/** A practice row with what it was attached to, for the history. */
data class PracticeRow(
    @androidx.room.Embedded val practice: PracticeEntity,
    @ColumnInfo(name = "scrap_title") val scrapTitle: String?,
    @ColumnInfo(name = "project_name") val projectName: String?,
) {
    /** "Verse riff B", "Low Tide", "Paradiddles", or "" for time on its own. */
    val what: String get() = listOfNotNull(scrapTitle, projectName, practice.exercise).firstOrNull { it.isNotBlank() } ?: ""
}

@Dao
interface PracticeDao {
    @Insert suspend fun insertRaw(p: PracticeEntity): Long

    /** Saves a stretch of practice; under 5 seconds is a mis-tap and isn't kept. Returns its id or null. */
    suspend fun add(p: PracticeEntity): Long? {
        if (p.seconds < 5) return null
        return insertRaw(p.copy(exercise = p.exercise?.trim()?.take(80)?.ifEmpty { null }, note = p.note?.trim()?.take(500)?.ifEmpty { null }))
    }

    @Query(
        "SELECT practice.*, scrap.title AS scrap_title, project.name AS project_name FROM practice " +
            "LEFT JOIN scrap ON scrap.id = practice.scrap_id LEFT JOIN project ON project.id = practice.project_id " +
            "ORDER BY practice.started_at DESC LIMIT :limit",
    )
    fun history(limit: Int = 500): Flow<List<PracticeRow>>

    @Query("SELECT COALESCE(SUM(seconds), 0) FROM practice WHERE started_at >= :since") fun secondsSince(since: Long): Flow<Long>
    @Query("SELECT COALESCE(SUM(seconds), 0) FROM practice WHERE scrap_id = :scrapId") fun secondsOnScrap(scrapId: Long): Flow<Long>
    @Query("DELETE FROM practice WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE practice SET note = :note WHERE id = :id") suspend fun setNote(id: Long, note: String?)
}
