package com.betterappmafia.apophenia.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** How a layer plays (plan 5.2). Stored by token. */
enum class LayerMode(val token: String) {
    /** Repeats between its loop points. */
    LOOP("loop"),
    /** Plays once from a chosen point. */
    ONE_SHOT("one_shot"),
    /** Plays at its own natural timing, never forced onto a grid or conformed. */
    FREE("free");

    companion object {
        fun of(token: String) = entries.firstOrNull { it.token == token }
            ?: throw IllegalArgumentException("unknown layer mode '$token' (written by a newer version?)")
    }
}

/**
 * A stack (plan 5): recordings played together. With a project it saves with that project; without
 * one it is the temporary stack, whose recordings go to the Library (plan 10.1).
 * Tempo and key are the stack's own: null = taken from its first layer that has one (plan 5.3), editable.
 */
@Entity(
    tableName = "stack",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["project_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("project_id")],
)
data class StackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "project_id") val projectId: Long? = null,
    val name: String,
    val bpm: Double? = null,
    val key: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "edited_at") val editedAt: Long,
)

/**
 * One layer: a recording on the stack's timeline with this layer's own settings. The audio is never
 * touched (plan 4.2). Positions are frames of the take (from, to) and of the stack timeline (start).
 * [takeId] null = whichever take is starred, else the newest, when the stack loads.
 */
@Entity(
    tableName = "stack_layer",
    foreignKeys = [
        ForeignKey(StackEntity::class, ["id"], ["stack_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ScrapEntity::class, ["id"], ["scrap_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("stack_id"), Index("scrap_id")],
)
data class StackLayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "stack_id") val stackId: Long,
    @ColumnInfo(name = "scrap_id") val scrapId: Long,
    @ColumnInfo(name = "take_id") val takeId: Long? = null,
    val mode: LayerMode = LayerMode.LOOP,
    /** Where on the stack timeline the layer begins, in frames (negative = it starts before the stack's 0). */
    @ColumnInfo(name = "start_frames", defaultValue = "0") val startFrames: Long = 0,
    /** The part of the take played: loop points for a loop, trim for the others. Null `to` = the end. */
    @ColumnInfo(name = "from_frames", defaultValue = "0") val fromFrames: Long = 0,
    @ColumnInfo(name = "to_frames") val toFrames: Long? = null,
    @ColumnInfo(name = "gain_db", defaultValue = "0") val gainDb: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val muted: Boolean = false,
    @ColumnInfo(defaultValue = "0") val solo: Boolean = false,
    /** Conform to the stack's tempo and key (plan 5.3): on by default; free-time layers ignore it. */
    @ColumnInfo(defaultValue = "1") val conform: Boolean = true,
    /** Order in the list (top to bottom). */
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
    @ColumnInfo(name = "added_at") val addedAt: Long,
) {
    /** Never trusted as read: finite gain within -60..+12 dB, a region that isn't empty or reversed. */
    fun sanitized(): StackLayerEntity {
        val from = fromFrames.coerceAtLeast(0)
        return copy(
            fromFrames = from,
            toFrames = toFrames?.takeIf { it > from },
            gainDb = if (gainDb.isFinite()) gainDb.coerceIn(-60.0, 12.0) else 0.0,
        )
    }
}

/** A layer as the stack sheet and the engine need it: the layer, its recording's title and the take that plays. */
data class LayerRow(
    @Embedded val layer: StackLayerEntity,
    val title: String?,
    val scrapCreatedAt: Long,
    val bpmLow: Double?,
    val bpmHigh: Double?,
    val freeTime: Boolean,
    val key: String?,
    val takeLetter: String?,
    val audioPath: String?,
    val durationFrames: Long?,
)

@Dao
abstract class StackDao {
    @Insert abstract suspend fun insert(stack: StackEntity): Long
    @Update abstract suspend fun update(stack: StackEntity)
    @Query("SELECT * FROM stack WHERE id = :id") abstract suspend fun get(id: Long): StackEntity?
    @Query("SELECT * FROM stack WHERE id = :id") abstract fun flow(id: Long): Flow<StackEntity?>

    /** A project's stack (the first; more than one per project arrives with naming and duplicating, plan 5.5). */
    @Query("SELECT * FROM stack WHERE project_id = :projectId ORDER BY id LIMIT 1") abstract suspend fun ofProject(projectId: Long): StackEntity?
    @Query("SELECT * FROM stack WHERE project_id IS NULL ORDER BY id LIMIT 1") abstract suspend fun temporary(): StackEntity?

    open suspend fun ofProjectOrNew(projectId: Long?, now: Long): StackEntity {
        val found = if (projectId != null) ofProject(projectId) else temporary()
        if (found != null) return found
        val s = StackEntity(projectId = projectId, name = if (projectId != null) "Stack 1" else "Temporary stack", createdAt = now, editedAt = now)
        return s.copy(id = insert(s))
    }

    @Insert protected abstract suspend fun insertLayer(layer: StackLayerEntity): Long
    @Update protected abstract suspend fun updateLayerRow(layer: StackLayerEntity)
    @Query("DELETE FROM stack_layer WHERE id = :id") abstract suspend fun deleteLayer(id: Long)
    @Query("SELECT COALESCE(MAX(position), -1) FROM stack_layer WHERE stack_id = :stackId") protected abstract suspend fun lastPosition(stackId: Long): Int
    @Query("UPDATE stack SET edited_at = :now WHERE id = :id") abstract suspend fun touch(id: Long, now: Long)

    /** Adds a layer at the bottom of the list. Returns its id. */
    @Transaction
    open suspend fun addLayer(layer: StackLayerEntity, now: Long): Long {
        val id = insertLayer(layer.sanitized().copy(position = lastPosition(layer.stackId) + 1, addedAt = now))
        touch(layer.stackId, now)
        return id
    }

    open suspend fun updateLayer(layer: StackLayerEntity, now: Long) {
        updateLayerRow(layer.sanitized())
        touch(layer.stackId, now)
    }

    @Query("SELECT * FROM stack_layer WHERE id = :id") abstract suspend fun layer(id: Long): StackLayerEntity?

    /**
     * The layers of a stack, top to bottom, each with the take that plays: the chosen take if it is
     * still live, else the newest starred, else the newest. Layers of trashed recordings are left out.
     */
    @Query(
        "SELECT stack_layer.*, scrap.title AS title, scrap.created_at AS scrapCreatedAt, scrap.bpm_low AS bpmLow, scrap.bpm_high AS bpmHigh, scrap.free_time AS freeTime, scrap.`key` AS `key`, " +
            "take.letter AS takeLetter, take.audio_path AS audioPath, take.duration_frames AS durationFrames " +
            "FROM stack_layer JOIN scrap ON scrap.id = stack_layer.scrap_id " +
            "LEFT JOIN take ON take.id = COALESCE(" +
            "(SELECT t.id FROM take t WHERE t.id = stack_layer.take_id AND t.deleted_at IS NULL), " +
            "(SELECT t.id FROM take t WHERE t.scrap_id = scrap.id AND t.deleted_at IS NULL ORDER BY t.starred DESC, t.id DESC LIMIT 1)) " +
            "WHERE stack_layer.stack_id = :stackId AND scrap.deleted_at IS NULL ORDER BY stack_layer.position, stack_layer.id"
    )
    abstract fun layers(stackId: Long): Flow<List<LayerRow>>

    /** Which scraps are layers of this stack (for + STACK / IN STACK on Board windows). */
    @Query("SELECT DISTINCT scrap_id FROM stack_layer WHERE stack_id = :stackId")
    abstract fun scrapIds(stackId: Long): Flow<List<Long>>

    /** Which scraps are layers of a project's stack, whether or not that stack exists yet. */
    @Query("SELECT DISTINCT stack_layer.scrap_id FROM stack_layer JOIN stack ON stack.id = stack_layer.stack_id WHERE stack.project_id = :projectId")
    abstract fun scrapIdsInProjectStack(projectId: Long): Flow<List<Long>>

    @Query("SELECT id FROM stack_layer WHERE stack_id = :stackId AND scrap_id = :scrapId")
    abstract suspend fun layerIdsOf(stackId: Long, scrapId: Long): List<Long>
}

/**
 * A lyric synced to one take (plan 4.4): when each line of words starts, tapped once while listening,
 * then nudged. [frames] holds one take frame per line of words, in order, comma-separated (a line
 * not tapped yet is empty). Deleting the lyric or the take deletes the sync.
 */
@Entity(
    tableName = "lyric_sync",
    primaryKeys = ["lyric_id", "take_id"],
    foreignKeys = [
        ForeignKey(ScrapEntity::class, ["id"], ["lyric_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TakeEntity::class, ["id"], ["take_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("take_id")],
)
data class LyricSyncEntity(
    @ColumnInfo(name = "lyric_id") val lyricId: Long,
    @ColumnInfo(name = "take_id") val takeId: Long,
    val frames: String,
    @ColumnInfo(name = "edited_at") val editedAt: Long,
) {
    /** Line index → take frame; null where a line wasn't tapped. */
    val lineFrames: List<Long?> get() = if (frames.isEmpty()) emptyList() else frames.split(',').map { it.trim().toLongOrNull() }

    companion object {
        fun of(lyricId: Long, takeId: Long, lines: List<Long?>, now: Long) =
            LyricSyncEntity(lyricId, takeId, lines.joinToString(",") { it?.toString() ?: "" }, now)

        /** The line playing at [position]: the last line whose start has passed, or -1 before the first. */
        fun lineAt(lines: List<Long?>, position: Long): Int {
            var current = -1
            lines.forEachIndexed { i, f -> if (f != null && f <= position) current = i }
            return current
        }
    }
}

@Dao
interface LyricSyncDao {
    @androidx.room.Upsert suspend fun put(sync: LyricSyncEntity)
    @Query("SELECT * FROM lyric_sync WHERE lyric_id = :lyricId") fun of(lyricId: Long): Flow<List<LyricSyncEntity>>
    @Query("DELETE FROM lyric_sync WHERE lyric_id = :lyricId AND take_id = :takeId") suspend fun delete(lyricId: Long, takeId: Long)
}
