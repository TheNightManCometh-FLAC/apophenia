package com.betterappmafia.apophenia.data

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverter
import androidx.room.TypeConverters

const val DATABASE_NAME = "apophenia.db"

/** The schema version this build writes; backups from a newer one are refused. */
const val SCHEMA_VERSION = 15

/** Everything deleted waits this long in the trash before it is purged (plan 3.8). */
const val TRASH_DAYS = 30
const val TRASH_MILLIS = TRASH_DAYS * 24L * 60 * 60 * 1000

/** Rows deleted before this time have been in the trash for [TRASH_DAYS] and can be purged. */
fun trashCutoff(now: Long) = now - TRASH_MILLIS

/** Enums are stored as their ASCII tokens, never by ordinal or Kotlin name. */
class Converters {
    @TypeConverter fun format(f: ScrapFormat) = f.token
    @TypeConverter fun format(s: String) = ScrapFormat.of(s)
    @TypeConverter fun source(s: ValueSource?) = s?.token
    @TypeConverter fun source(s: String?) = s?.let(ValueSource::of)
    @TypeConverter fun mode(m: LayerMode) = m.token
    @TypeConverter fun mode(s: String) = LayerMode.of(s)
}

@Database(
    entities = [
        ProjectEntity::class, ScrapEntity::class, TakeEntity::class, TagEntity::class, ScrapTagEntity::class,
        GroupEntity::class, GroupMemberEntity::class, ProjectRefEntity::class, AppSettingEntity::class,
        StackEntity::class, StackLayerEntity::class, LyricSyncEntity::class, TakeMarkEntity::class, PracticeEntity::class, TakeAnalysisEntity::class, TakeTranscriptionEntity::class, TakeStemEntity::class, TakeSpeechEntity::class, EarResultEntity::class, ExerciseEntity::class,
    ],
    version = SCHEMA_VERSION,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7), AutoMigration(from = 7, to = 8), AutoMigration(from = 8, to = 9), AutoMigration(from = 9, to = 10), AutoMigration(from = 10, to = 11), AutoMigration(from = 11, to = 12), AutoMigration(from = 12, to = 13), AutoMigration(from = 13, to = 14), AutoMigration(from = 14, to = 15)],
)
@TypeConverters(Converters::class)
@ConstructedBy(ApopheniaDatabaseConstructor::class)
abstract class ApopheniaDatabase : RoomDatabase() {
    abstract fun scraps(): ScrapDao
    abstract fun takes(): TakeDao
    abstract fun tags(): TagDao
    abstract fun groups(): GroupDao
    abstract fun projects(): ProjectDao
    abstract fun settings(): SettingDao
    abstract fun stacks(): StackDao
    abstract fun lyricSyncs(): LyricSyncDao
    abstract fun marks(): MarkDao
    abstract fun practice(): PracticeDao
    abstract fun analysis(): AnalysisDao
    abstract fun transcriptions(): TranscriptionDao
    abstract fun transcriptionList(): TranscriptionListDao
    abstract fun stems(): StemDao
    abstract fun speech(): SpeechDao
    abstract fun earResults(): EarResultDao
    abstract fun exercises(): ExerciseDao
}

// The Room compiler generates the actual implementations.
@Suppress("KotlinNoActualForExpect")
expect object ApopheniaDatabaseConstructor : RoomDatabaseConstructor<ApopheniaDatabase> {
    override fun initialize(): ApopheniaDatabase
}
