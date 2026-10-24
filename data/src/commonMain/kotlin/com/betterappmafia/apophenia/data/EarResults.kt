package com.betterappmafia.apophenia.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One Ear session (plan §9; Learn › Ear, Practice or Test): which drill, how many right out of how many, and the best
 * run of right answers in a row. Schema v14. Listen mode keeps nothing.
 */
@Entity(tableName = "ear_result", indices = [Index("drill"), Index("finished_at")])
data class EarResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The drill id from core.learn.Ear. */
    val drill: String,
    /** "practice" or "test". */
    val mode: String,
    val correct: Int,
    val total: Int,
    @ColumnInfo(name = "best_streak") val bestStreak: Int,
    @ColumnInfo(name = "finished_at") val finishedAt: Long,
)

/** Per drill: the best test score and how many sessions there have been. */
data class EarBest(val drill: String, @ColumnInfo(name = "best_correct") val bestCorrect: Int?, @ColumnInfo(name = "best_total") val bestTotal: Int?, val sessions: Int)

@Dao
interface EarResultDao {
    @Insert suspend fun insertRaw(r: EarResultEntity): Long

    /** Keeps a session with at least one answer; returns its id or null. */
    suspend fun add(r: EarResultEntity): Long? = if (r.total <= 0) null else insertRaw(r)

    @Query(
        "SELECT drill, " +
            "(SELECT t.correct FROM ear_result t WHERE t.drill = r.drill AND t.mode = 'test' ORDER BY t.correct * 1.0 / t.total DESC, t.total DESC LIMIT 1) AS best_correct, " +
            "(SELECT t.total FROM ear_result t WHERE t.drill = r.drill AND t.mode = 'test' ORDER BY t.correct * 1.0 / t.total DESC, t.total DESC LIMIT 1) AS best_total, " +
            "COUNT(*) AS sessions FROM ear_result r GROUP BY drill"
    )
    fun bests(): Flow<List<EarBest>>

    @Query("SELECT * FROM ear_result ORDER BY finished_at DESC LIMIT :limit") suspend fun recent(limit: Int = 50): List<EarResultEntity>
}
