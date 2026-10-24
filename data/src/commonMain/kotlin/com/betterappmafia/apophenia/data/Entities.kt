package com.betterappmafia.apophenia.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Schema v1: the tables M1 needs; v2 adds take.preroll_frames (official plan sections 3, 4.2 and 9). Board blocks, sync points,
 * stacks/layers, transcriptions, stems and the practice log arrive by migration in their milestones.
 * Times are epoch milliseconds; audio positions are frames at the take's sample rate.
 * Everything deletable carries deleted_at for the 30-day trash (plan 3.8).
 */

/**
 * The storage format of a scrap: what the app needs to open and edit it. It is not how the
 * musician sorts things; that is tags (plan 3.4: "lyrics" is a tag like any other).
 * Stored ASCII tokens; never reorder or rename a token once it has shipped.
 */
enum class ScrapFormat(val token: String) {
    RECORDING("recording"), LYRIC("lyric"), TEXT("text"), CHORD_CHART("chord_chart"),
    TAB("tab"), MIDI("midi"), SHEET("sheet"), DRUM_CHART("drum_chart");

    companion object {
        fun of(token: String) = entries.firstOrNull { it.token == token }
            ?: throw IllegalArgumentException("unknown scrap format '$token' (written by a newer version?)")
    }
}

/**
 * Where BPM or key came from (plan 3.4). Higher [rank] wins: the user's entry is never
 * overwritten by the metronome or by re-analysis.
 */
enum class ValueSource(val token: String, val rank: Int) {
    ANALYSIS("analysis", 0), METRONOME("metronome", 1), USER("user", 2);

    companion object {
        fun of(token: String) = entries.firstOrNull { it.token == token }
            ?: throw IllegalArgumentException("unknown value source '$token' (written by a newer version?)")
    }
}

@Entity(tableName = "project", indices = [Index("deleted_at")])
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "edited_at") val editedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

/**
 * One Library item: a fragment that may end up in any number of projects. [projectId] only
 * records where it was made; being made in a project never takes it out of the Library or keeps
 * it out of other projects. [showInLibrary] is the save sheet's switch (on by default).
 *
 * BPM and key are the only built-in values (plan 3.4), each with where it came from.
 * BPM is unknown when [bpmLow] is null and not [freeTime]; a single tempo has [bpmHigh] = [bpmLow].
 * [key] is an ASCII key-and-mode token (e.g. "D dorian") whose format the music core owns.
 */
@Entity(
    tableName = "scrap",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["project_id"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("project_id"), Index("deleted_at"), Index("created_at"), Index("bpm_low"), Index("key")],
)
data class ScrapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val format: ScrapFormat,
    val title: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "edited_at") val editedAt: Long,
    @ColumnInfo(name = "project_id") val projectId: Long? = null,
    @ColumnInfo(name = "show_in_library", defaultValue = "1") val showInLibrary: Boolean = true,
    @ColumnInfo(name = "bpm_low") val bpmLow: Double? = null,
    @ColumnInfo(name = "bpm_high") val bpmHigh: Double? = null,
    @ColumnInfo(name = "free_time", defaultValue = "0") val freeTime: Boolean = false,
    @ColumnInfo(name = "bpm_source") val bpmSource: ValueSource? = null,
    val key: String? = null,
    @ColumnInfo(name = "key_source") val keySource: ValueSource? = null,
    /** Lyrics, text and chord charts: the content itself (ChordPro-style text, core.sheet). Null for recordings. */
    val body: String? = null,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

/**
 * One take of a recording scrap, lettered A, B, C... The audio file at [audioPath] (relative to the
 * app's audio folder) is written once and never modified.
 */
@Entity(
    tableName = "take",
    foreignKeys = [ForeignKey(ScrapEntity::class, ["id"], ["scrap_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["scrap_id", "letter"], unique = true), Index("deleted_at")],
)
data class TakeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "scrap_id") val scrapId: Long,
    val letter: String,
    @ColumnInfo(defaultValue = "0") val starred: Boolean = false,
    @ColumnInfo(name = "audio_path") val audioPath: String,
    @ColumnInfo(name = "duration_frames") val durationFrames: Long,
    @ColumnInfo(name = "sample_rate") val sampleRate: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** The metronome grid the take was recorded against, as versioned JSON; null when recorded without one. */
    @ColumnInfo(name = "metronome_grid") val metronomeGrid: String? = null,
    /** Round-trip latency compensation applied when the take was aligned, in frames: set for takes recorded over a stack, from the route's calibration (plan 5.4). */
    @ColumnInfo(name = "latency_frames") val latencyFrames: Int? = null,
    /**
     * Frames at the start of the file that Listening captured before ● REC was pressed (schema v2; builds before 0.3.7,
     * which removed pre-roll: new takes write 0).
     * They are part of the take, never cut off; this only marks where ● REC fell.
     */
    @ColumnInfo(name = "preroll_frames", defaultValue = "0") val prerollFrames: Long = 0,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

/**
 * A tag in the musician's own words ("lyrics", "bass", "love song", "political song"). Names are
 * unique ignoring case, so "Bass" and "bass" are the same tag. Tags belong to scraps, never to a
 * project (plan 3.4).
 */
@Entity(tableName = "tag", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** A tag on a scrap. Removing a tag from a scrap, or deleting the tag, never deletes the scrap. */
@Entity(
    tableName = "scrap_tag",
    primaryKeys = ["scrap_id", "tag_id"],
    foreignKeys = [
        ForeignKey(ScrapEntity::class, ["id"], ["scrap_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tag_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tag_id")],
)
data class ScrapTagEntity(
    @ColumnInfo(name = "scrap_id") val scrapId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)

/** A user-named Group ("Love songs"). Table name avoids the SQL keyword GROUP. */
@Entity(tableName = "scrap_group", indices = [Index("deleted_at")])
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

/** Membership only: deleting a Group never deletes its scraps. */
@Entity(
    tableName = "group_member",
    primaryKeys = ["group_id", "scrap_id"],
    foreignKeys = [
        ForeignKey(GroupEntity::class, ["id"], ["group_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ScrapEntity::class, ["id"], ["scrap_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("scrap_id")],
)
data class GroupMemberEntity(
    @ColumnInfo(name = "group_id") val groupId: Long,
    @ColumnInfo(name = "scrap_id") val scrapId: Long,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

/**
 * A scrap being considered for a project: this is what puts it on the project's Board. The same
 * scrap can be referenced by many projects at once and stays in the Library. The row carries that
 * project's own playback settings (plan 4.2); changing them never touches the audio, the scrap,
 * its tags, the Library or another project. Settings are typed columns so Room validates them and
 * every change is a tested migration.
 */
@Entity(
    tableName = "project_ref",
    primaryKeys = ["project_id", "scrap_id"],
    foreignKeys = [
        ForeignKey(ProjectEntity::class, ["id"], ["project_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ScrapEntity::class, ["id"], ["scrap_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("scrap_id")],
)
data class ProjectRefEntity(
    @ColumnInfo(name = "project_id") val projectId: Long,
    @ColumnInfo(name = "scrap_id") val scrapId: Long,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "trim_start_frames", defaultValue = "0") val trimStartFrames: Long = 0,
    /** Null = play to the end of the take. */
    @ColumnInfo(name = "trim_end_frames") val trimEndFrames: Long? = null,
    @ColumnInfo(name = "loop_start_frames") val loopStartFrames: Long? = null,
    @ColumnInfo(name = "loop_end_frames") val loopEndFrames: Long? = null,
    @ColumnInfo(name = "gain_db", defaultValue = "0") val gainDb: Double = 0.0,
    @ColumnInfo(name = "fade_in_frames", defaultValue = "0") val fadeInFrames: Long = 0,
    @ColumnInfo(name = "fade_out_frames", defaultValue = "0") val fadeOutFrames: Long = 0,
    /** Tempo/pitch conform on (loop and one-shot layers); amounts arrive with stacks in a later migration. */
    @ColumnInfo(defaultValue = "0") val conform: Boolean = false,
    @ColumnInfo(name = "offset_frames", defaultValue = "0") val offsetFrames: Long = 0,
    /** Its window on this project's Board, in dp from the Board's top left (plan 4.3); null until first placed. */
    @ColumnInfo(name = "board_x") val boardX: Int? = null,
    @ColumnInfo(name = "board_y") val boardY: Int? = null,
    @ColumnInfo(name = "board_w") val boardW: Int? = null,
    @ColumnInfo(name = "board_h") val boardH: Int? = null,
    /** Stacking order: the window moved last is drawn on top. */
    @ColumnInfo(name = "board_z", defaultValue = "0") val boardZ: Int = 0,
    /**
     * This project's own version of a lyric, text or chord chart (plan 4.2), edited independently of the
     * Library's. Null = the project shows and edits the scrap itself (it was made in this project).
     */
    val body: String? = null,
) {
    /**
     * Settings made safe after any read or edit: no NaN/Infinity gain, no negative positions,
     * loop and trim ranges that are empty or reversed are dropped instead of trusted.
     */
    fun sanitized(): ProjectRefEntity {
        val start = trimStartFrames.coerceAtLeast(0)
        val end = trimEndFrames?.takeIf { it > start }
        val loopOk = loopStartFrames != null && loopEndFrames != null &&
            loopStartFrames >= 0 && loopEndFrames > loopStartFrames
        return copy(
            trimStartFrames = start,
            trimEndFrames = end,
            loopStartFrames = if (loopOk) loopStartFrames else null,
            loopEndFrames = if (loopOk) loopEndFrames else null,
            gainDb = if (gainDb.isFinite()) gainDb else 0.0,
            fadeInFrames = fadeInFrames.coerceAtLeast(0),
            fadeOutFrames = fadeOutFrames.coerceAtLeast(0),
        )
    }
}

@Entity(tableName = "app_setting")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
