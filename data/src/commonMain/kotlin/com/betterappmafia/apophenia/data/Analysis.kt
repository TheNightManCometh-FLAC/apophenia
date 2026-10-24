package com.betterappmafia.apophenia.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * What take analysis found (plan §10.2, schema v10): tempo and key candidates with their reasons and the
 * single-line notes, as the core's text form in [body]. Derived data: it can be thrown away and run
 * again, never changes the take, and goes with it when it's deleted. [version] is the analysis version
 * that wrote it, so a newer analysis can run again over older results.
 */
@Entity(
    tableName = "take_analysis",
    foreignKeys = [ForeignKey(TakeEntity::class, ["id"], ["take_id"], onDelete = ForeignKey.CASCADE)],
)
data class TakeAnalysisEntity(
    @PrimaryKey @ColumnInfo(name = "take_id") val takeId: Long,
    val version: Int,
    @ColumnInfo(name = "analysed_at") val analysedAt: Long,
    val body: String,
)

@Dao
interface AnalysisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(a: TakeAnalysisEntity)
    @Query("SELECT * FROM take_analysis WHERE take_id = :takeId") fun of(takeId: Long): Flow<TakeAnalysisEntity?>
    @Query("SELECT * FROM take_analysis WHERE take_id = :takeId") suspend fun get(takeId: Long): TakeAnalysisEntity?
    /** Takes with no analysis, or one older than [version], newest first: what the worker catches up on. */
    @Query(
        "SELECT take.* FROM take LEFT JOIN take_analysis a ON a.take_id = take.id " +
            "WHERE take.deleted_at IS NULL AND (a.take_id IS NULL OR a.version < :version) ORDER BY take.created_at DESC LIMIT :limit",
    )
    suspend fun pending(version: Int, limit: Int = 50): List<TakeEntity>
    /** Every live take's analysis with its scrap and letter (Learn: chords found in your recordings). */
    @Query("SELECT take.scrap_id AS scrapId, take.letter AS letter, a.body AS body FROM take_analysis a JOIN take ON take.id = a.take_id WHERE take.deleted_at IS NULL")
    suspend fun bodies(): List<AnalysisBody>
}

/**
 * A take turned into notes by a model (plan §10.3, schema v11): Basic Pitch's notes as the core's text
 * form in [body], with the [model] that made them. Its own item linked to the take, which it never
 * changes; it goes with the take when that's deleted. Corrections come with the correction screen.
 */
@Entity(
    tableName = "take_transcription",
    foreignKeys = [ForeignKey(TakeEntity::class, ["id"], ["take_id"], onDelete = ForeignKey.CASCADE)],
)
data class TakeTranscriptionEntity(
    @PrimaryKey @ColumnInfo(name = "take_id") val takeId: Long,
    val model: String,
    @ColumnInfo(name = "made_at") val madeAt: Long,
    val body: String,
)

@Dao
interface TranscriptionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(t: TakeTranscriptionEntity)
    @Query("SELECT * FROM take_transcription WHERE take_id = :takeId") fun of(takeId: Long): Flow<TakeTranscriptionEntity?>
    @Query("SELECT * FROM take_transcription WHERE take_id = :takeId") suspend fun get(takeId: Long): TakeTranscriptionEntity?
    @Query("DELETE FROM take_transcription WHERE take_id = :takeId") suspend fun delete(takeId: Long)
}

/** A transcription with the recording it came from, for the AI mode's list. */
data class TranscriptionRow(
    @ColumnInfo(name = "take_id") val takeId: Long,
    @ColumnInfo(name = "scrap_id") val scrapId: Long,
    val letter: String,
    val title: String?,
    @ColumnInfo(name = "made_at") val madeAt: Long,
)

@Dao
interface TranscriptionListDao {
    @Query(
        "SELECT t.take_id, k.scrap_id, k.letter, s.title, t.made_at FROM take_transcription t " +
            "JOIN take k ON k.id = t.take_id JOIN scrap s ON s.id = k.scrap_id " +
            "WHERE k.deleted_at IS NULL AND s.deleted_at IS NULL ORDER BY t.made_at DESC LIMIT :limit",
    )
    fun recent(limit: Int = 20): Flow<List<TranscriptionRow>>
}

/**
 * A stem separated from a take (plan §10.6, schema v12). The stem is its own recording item (its own scrap
 * with one take, [takeId], in the Library) and this row links it to the take it came from, [sourceTakeId],
 * by [stem] name ("vocals", "drums", "bass", "other") and the [model] that made it. Deleting either take
 * drops the link; the source take is never changed.
 */
@Entity(
    tableName = "take_stem",
    primaryKeys = ["source_take_id", "stem"],
    foreignKeys = [
        ForeignKey(TakeEntity::class, ["id"], ["source_take_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TakeEntity::class, ["id"], ["take_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("take_id")],
)
data class TakeStemEntity(
    @ColumnInfo(name = "source_take_id") val sourceTakeId: Long,
    val stem: String,
    @ColumnInfo(name = "take_id") val takeId: Long,
    val model: String,
    @ColumnInfo(name = "made_at") val madeAt: Long,
)

/** A stem with its item, for the STEMS tab and the AI mode. */
data class StemRow(
    @ColumnInfo(name = "source_take_id") val sourceTakeId: Long,
    val stem: String,
    @ColumnInfo(name = "take_id") val takeId: Long,
    @ColumnInfo(name = "scrap_id") val scrapId: Long,
    @ColumnInfo(name = "audio_path") val audioPath: String,
    @ColumnInfo(name = "duration_frames") val durationFrames: Long,
    @ColumnInfo(name = "sample_rate") val sampleRate: Int,
    val title: String?,
    @ColumnInfo(name = "made_at") val madeAt: Long,
)

@Dao
interface StemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(s: TakeStemEntity)

    /** The stems of a take still in the Library (a stem item in the trash is left out). */
    @Query(
        "SELECT st.source_take_id, st.stem, st.take_id, k.scrap_id, k.audio_path, k.duration_frames, k.sample_rate, s.title, st.made_at " +
            "FROM take_stem st JOIN take k ON k.id = st.take_id JOIN scrap s ON s.id = k.scrap_id " +
            "WHERE st.source_take_id = :sourceTakeId AND k.deleted_at IS NULL AND s.deleted_at IS NULL",
    )
    fun of(sourceTakeId: Long): Flow<List<StemRow>>

    /** The take a stem item was separated from, if it is one. */
    @Query("SELECT * FROM take_stem WHERE take_id = :takeId") suspend fun sourceOf(takeId: Long): TakeStemEntity?

    @Query(
        "SELECT st.source_take_id, st.stem, st.take_id, k.scrap_id, k.audio_path, k.duration_frames, k.sample_rate, s.title, st.made_at " +
            "FROM take_stem st JOIN take k ON k.id = st.take_id JOIN scrap s ON s.id = k.scrap_id " +
            "WHERE k.deleted_at IS NULL AND s.deleted_at IS NULL ORDER BY st.made_at DESC LIMIT :limit",
    )
    fun recent(limit: Int = 40): Flow<List<StemRow>>
}

/**
 * A take's words as text (plan §10.4, schema v13): Moonshine's time-stamped lines as the core's text form in
 * [body] (`TranscriptText`), with the [model] that read them. Editable; edits never touch the take, and it
 * goes with the take when that's deleted.
 */
@Entity(
    tableName = "take_speech",
    foreignKeys = [ForeignKey(TakeEntity::class, ["id"], ["take_id"], onDelete = ForeignKey.CASCADE)],
)
data class TakeSpeechEntity(
    @PrimaryKey @ColumnInfo(name = "take_id") val takeId: Long,
    val model: String,
    @ColumnInfo(name = "made_at") val madeAt: Long,
    val body: String,
)

@Dao
interface SpeechDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(t: TakeSpeechEntity)
    @Query("SELECT * FROM take_speech WHERE take_id = :takeId") fun of(takeId: Long): Flow<TakeSpeechEntity?>
    @Query("SELECT * FROM take_speech WHERE take_id = :takeId") suspend fun get(takeId: Long): TakeSpeechEntity?

    @Query(
        "SELECT t.take_id, k.scrap_id, k.letter, s.title, t.made_at FROM take_speech t " +
            "JOIN take k ON k.id = t.take_id JOIN scrap s ON s.id = k.scrap_id " +
            "WHERE k.deleted_at IS NULL AND s.deleted_at IS NULL ORDER BY t.made_at DESC LIMIT :limit",
    )
    fun recent(limit: Int = 20): Flow<List<TranscriptionRow>>
}

data class AnalysisBody(val scrapId: Long, val letter: String, val body: String)
