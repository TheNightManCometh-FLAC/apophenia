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
 * A place in a take (schema v8): a mark ([endFrame] null) or a loop region (plan §7: marks and A/B
 * loops in Playback speed, markers while recording). Marks belong to the take, never change its audio,
 * and go with it when it's deleted.
 */
@Entity(
    tableName = "take_mark",
    foreignKeys = [ForeignKey(TakeEntity::class, ["id"], ["take_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("take_id")],
)
data class TakeMarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "take_id") val takeId: Long,
    val frame: Long,
    @ColumnInfo(name = "end_frame") val endFrame: Long? = null,
    val label: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    val isLoop: Boolean get() = endFrame != null && endFrame > frame

    /** A mark always starts before it ends and never before the take. */
    fun sanitized(): TakeMarkEntity {
        val f = frame.coerceAtLeast(0)
        val e = endFrame?.takeIf { it > f }
        return copy(frame = f, endFrame = e, label = label.trim().take(60))
    }
}

@Dao
interface MarkDao {
    @Insert suspend fun insertRaw(mark: TakeMarkEntity): Long
    suspend fun add(mark: TakeMarkEntity): Long = insertRaw(mark.sanitized())
    @Query("SELECT * FROM take_mark WHERE take_id = :takeId ORDER BY frame, id") fun of(takeId: Long): Flow<List<TakeMarkEntity>>
    @Query("DELETE FROM take_mark WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM take WHERE audio_path = :path LIMIT 1") suspend fun takeByPath(path: String): TakeEntity?
}
