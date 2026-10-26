package com.betterappmafia.apophenia.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * An exercise (plan §7 Exercises; schema v15): a piece of written music to practise on a tempo ladder. It is either one
 * of your own Library scraps ([scrapId]; deleting the scrap for good removes the exercise) or, later, one of the curated
 * exercises shipped with the app ([curated], its id in the catalogue). The ladder's numbers are core.tools.TempoLadder.
 */
@Entity(
    tableName = "exercise",
    foreignKeys = [ForeignKey(ScrapEntity::class, ["id"], ["scrap_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["scrap_id"], unique = true), Index(value = ["curated"], unique = true)],
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "scrap_id") val scrapId: Long? = null,
    val curated: String? = null,
    @ColumnInfo(name = "start_bpm") val startBpm: Int,
    @ColumnInfo(name = "goal_bpm") val goalBpm: Int,
    @ColumnInfo(name = "step_bpm") val stepBpm: Int,
    @ColumnInfo(name = "current_bpm") val currentBpm: Int,
    @ColumnInfo(name = "best_bpm") val bestBpm: Int? = null,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    /** When the ladder last moved. */
    @ColumnInfo(name = "last_at") val lastAt: Long? = null,
)

/** An exercise with its scrap's title and format, for the list. */
data class ExerciseRow(
    @Embedded val exercise: ExerciseEntity,
    @ColumnInfo(name = "scrap_title") val scrapTitle: String?,
    @ColumnInfo(name = "scrap_format") val scrapFormat: String?,
)

@Dao
interface ExerciseDao {
    /** Adds an exercise; null when that scrap (or curated exercise) is one already. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertRaw(e: ExerciseEntity): Long
    suspend fun add(e: ExerciseEntity): Long? = insertRaw(e).takeIf { it > 0 }

    @Update suspend fun update(e: ExerciseEntity)
    @Query("DELETE FROM exercise WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM exercise WHERE id = :id") fun watch(id: Long): Flow<ExerciseEntity?>

    /** Every exercise whose scrap isn't in the trash, the most recently practised first. */
    @Query(
        "SELECT exercise.*, scrap.title AS scrap_title, scrap.format AS scrap_format FROM exercise " +
            "LEFT JOIN scrap ON scrap.id = exercise.scrap_id WHERE exercise.scrap_id IS NULL OR scrap.deleted_at IS NULL " +
            "ORDER BY COALESCE(exercise.last_at, exercise.added_at) DESC",
    )
    fun all(): Flow<List<ExerciseRow>>
}
