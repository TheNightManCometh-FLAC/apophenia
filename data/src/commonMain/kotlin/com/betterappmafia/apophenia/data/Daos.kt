package com.betterappmafia.apophenia.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ScrapDao {
    @Insert abstract suspend fun insert(scrap: ScrapEntity): Long
    @Update abstract suspend fun update(scrap: ScrapEntity)
    @Query("SELECT * FROM scrap WHERE id = :id") abstract suspend fun get(id: Long): ScrapEntity?

    @Query("UPDATE scrap SET bpm_low = :low, bpm_high = :high, free_time = :freeTime, bpm_source = :source WHERE id = :id")
    protected abstract suspend fun writeBpm(id: Long, low: Double?, high: Double?, freeTime: Boolean, source: ValueSource)

    @Query("UPDATE scrap SET `key` = :key, key_source = :source WHERE id = :id")
    protected abstract suspend fun writeKey(id: Long, key: String?, source: ValueSource)

    /**
     * Offers a BPM from [source]: a single tempo ([high] = [low]), a range, free time, or unknown
     * (all null, not free time). A lower-ranked source never replaces a higher one, so re-analysis
     * never overwrites the metronome or the user. Returns whether it was stored.
     */
    @Transaction
    open suspend fun offerBpm(id: Long, low: Double?, high: Double? = low, freeTime: Boolean = false, source: ValueSource): Boolean {
        val current = get(id) ?: return false
        if ((current.bpmSource?.rank ?: -1) > source.rank) return false
        require(low == null || (low > 0 && low.isFinite())) { "BPM must be positive, was $low" }
        require(high == null || (low != null && high >= low && high.isFinite())) { "BPM range $low-$high is reversed" }
        writeBpm(id, low, high, freeTime, source)
        return true
    }

    /** Offers a key-and-mode token from [source], with the same precedence as [offerBpm]. */
    @Transaction
    open suspend fun offerKey(id: Long, key: String?, source: ValueSource): Boolean {
        val current = get(id) ?: return false
        if ((current.keySource?.rank ?: -1) > source.rank) return false
        writeKey(id, key, source)
        return true
    }

    /** The Library: live scraps, minus project recordings switched out of Library search. */
    @Query(
        "SELECT * FROM scrap WHERE deleted_at IS NULL AND (project_id IS NULL OR show_in_library = 1) " +
            "ORDER BY created_at DESC, id DESC"
    )
    abstract suspend fun library(): List<ScrapEntity>

    /** Every live scrap, Library and project-only alike (Learn: in your material). */
    @Query("SELECT * FROM scrap WHERE deleted_at IS NULL ORDER BY edited_at DESC")
    abstract suspend fun live(): List<ScrapEntity>

    @Query("SELECT * FROM scrap WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
    abstract suspend fun trash(): List<ScrapEntity>

    /** The Library as the list shows it: each live scrap with its tags, live takes and starred takes, newest first. */
    @Query(
        "SELECT scrap.*, " + ROW_EXTRAS + " FROM scrap WHERE deleted_at IS NULL AND (project_id IS NULL OR show_in_library = 1) " +
            "ORDER BY created_at DESC, id DESC"
    )
    abstract fun libraryRows(): Flow<List<LibraryRow>>

    /** The trash as a list, most recently deleted first. */
    @Query("SELECT scrap.*, " + ROW_EXTRAS + " FROM scrap WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
    abstract fun trashRows(): Flow<List<LibraryRow>>

    @Query("SELECT scrap.*, " + ROW_EXTRAS + " FROM scrap WHERE id = :id")
    abstract fun row(id: Long): Flow<LibraryRow?>

    @Query("UPDATE scrap SET title = :title, edited_at = :now WHERE id = :id")
    abstract suspend fun rename(id: Long, title: String?, now: Long)

    @Query("UPDATE scrap SET edited_at = :now WHERE id = :id") abstract suspend fun touch(id: Long, now: Long)

    @Insert protected abstract suspend fun insertTake(take: TakeEntity): Long
    @Query("SELECT letter FROM take WHERE scrap_id = :scrapId") protected abstract suspend fun lettersOf(scrapId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE) protected abstract suspend fun insertRef(ref: ProjectRefEntity): Long

    /**
     * A new recording scrap with its first take, A, in one transaction. Returns the scrap id.
     * With [projectId] it was recorded in that project (plan 10.1): it remembers the project and
     * goes on its Board in the same transaction; it is still a Library scrap.
     */
    @Transaction
    open suspend fun createRecording(scrap: ScrapEntity, take: TakeEntity, projectId: Long? = null): Long {
        val id = insert(scrap.copy(format = ScrapFormat.RECORDING, projectId = projectId ?: scrap.projectId))
        insertTake(take.copy(scrapId = id, letter = TakeLetters.next(lettersOf(id))))
        if (projectId != null) insertRef(ProjectRefEntity(projectId = projectId, scrapId = id, addedAt = scrap.createdAt))
        return id
    }

    /** A written scrap's text (the Library version); edited time moves with it. */
    @Query("UPDATE scrap SET body = :body, edited_at = :now WHERE id = :id") abstract suspend fun setBody(id: Long, body: String, now: Long)

    /** A new lyric, text or chord chart; with [projectId] it was written there and goes on its Board. Returns the id. */
    @Transaction
    open suspend fun createWritten(scrap: ScrapEntity, projectId: Long? = null): Long {
        require(scrap.format != ScrapFormat.RECORDING) { "recordings are made with createRecording" }
        val id = insert(scrap.copy(projectId = projectId ?: scrap.projectId))
        if (projectId != null) insertRef(ProjectRefEntity(projectId = projectId, scrapId = id, addedAt = scrap.createdAt))
        return id
    }

    /** The save sheet's switch for project recordings: whether it also shows in Library search. */
    @Query("UPDATE scrap SET show_in_library = :show WHERE id = :id") abstract suspend fun setShowInLibrary(id: Long, show: Boolean)

    /** Several scraps as rows, whether or not they show in the Library (multi-select sheets, a project's own recordings). */
    @Query("SELECT scrap.*, " + ROW_EXTRAS + " FROM scrap WHERE id IN (:ids)")
    abstract fun rows(ids: List<Long>): Flow<List<LibraryRow>>

    /** Another take of an existing recording, lettered after every letter it has used. Returns the letter. */
    @Transaction
    open suspend fun addTake(scrapId: Long, take: TakeEntity, now: Long): String {
        val letter = TakeLetters.next(lettersOf(scrapId))
        insertTake(take.copy(scrapId = scrapId, letter = letter))
        touch(scrapId, now)
        return letter
    }

    @Query("UPDATE scrap SET deleted_at = :now WHERE id = :id") abstract suspend fun moveToTrash(id: Long, now: Long)
    @Query("UPDATE scrap SET deleted_at = NULL WHERE id = :id") abstract suspend fun restore(id: Long)

    /** Audio files of every take that the next purge will remove, so they can be deleted from disk first. */
    @Query(
        "SELECT take.audio_path FROM take JOIN scrap ON scrap.id = take.scrap_id " +
            "WHERE take.deleted_at <= :cutoff OR scrap.deleted_at <= :cutoff"
    )
    abstract suspend fun audioToPurge(cutoff: Long): List<String>

    @Query("DELETE FROM scrap WHERE deleted_at <= :cutoff") abstract suspend fun purgeExpired(cutoff: Long): Int
}

@Dao
interface TakeDao {
    @Insert suspend fun insert(take: TakeEntity): Long
    @Query("SELECT * FROM take WHERE id = :id") suspend fun get(id: Long): TakeEntity?

    /** Live takes of a scrap in letter order (A..Z, then AA...). */
    @Query("SELECT * FROM take WHERE scrap_id = :scrapId AND deleted_at IS NULL ORDER BY length(letter), letter")
    suspend fun takesOf(scrapId: Long): List<TakeEntity>

    @Query("SELECT * FROM take WHERE scrap_id = :scrapId AND deleted_at IS NULL ORDER BY length(letter), letter")
    fun takesFlow(scrapId: Long): Flow<List<TakeEntity>>

    /** Every take audio file the database knows, trashed ones included: a file not listed is an orphan. */
    @Query("SELECT audio_path FROM take") suspend fun allAudioPaths(): List<String>

    /** Every letter used, trashed takes included, so a restored take never collides with a newer one. */
    @Query("SELECT letter FROM take WHERE scrap_id = :scrapId") suspend fun lettersUsed(scrapId: Long): List<String>

    @Query("UPDATE take SET starred = :starred WHERE id = :id") suspend fun setStarred(id: Long, starred: Boolean)
    @Query("UPDATE take SET deleted_at = :now WHERE id = :id") suspend fun moveToTrash(id: Long, now: Long)
    @Query("UPDATE take SET deleted_at = NULL WHERE id = :id") suspend fun restore(id: Long)
    @Query("DELETE FROM take WHERE deleted_at <= :cutoff") suspend fun purgeExpired(cutoff: Long): Int
}

@Dao
abstract class TagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) protected abstract suspend fun insertTag(tag: TagEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) protected abstract suspend fun insertLink(link: ScrapTagEntity): Long

    /** Case-insensitive lookup: "Bass" finds "bass". */
    @Query("SELECT * FROM tag WHERE name = :name") abstract suspend fun named(name: String): TagEntity?

    /** Every tag the musician has made, alphabetically. */
    @Query("SELECT * FROM tag ORDER BY name") abstract suspend fun all(): List<TagEntity>

    /**
     * The list tags are picked from: every existing tag, most-used first (counting live scraps),
     * then alphabetically, optionally narrowed to names containing [filter].
     */
    @Query(
        "SELECT tag.* FROM tag LEFT JOIN scrap_tag ON scrap_tag.tag_id = tag.id " +
            "LEFT JOIN scrap ON scrap.id = scrap_tag.scrap_id AND scrap.deleted_at IS NULL " +
            "WHERE tag.name LIKE '%' || :filter || '%' " +
            "GROUP BY tag.id ORDER BY COUNT(scrap.id) DESC, tag.name"
    )
    abstract suspend fun pickList(filter: String = ""): List<TagEntity>

    @Query("SELECT tag.* FROM tag JOIN scrap_tag ON scrap_tag.tag_id = tag.id WHERE scrap_tag.scrap_id = :scrapId ORDER BY tag.name")
    abstract suspend fun tagsOf(scrapId: Long): List<TagEntity>

    /** The existing tag with this name (ignoring case and surrounding spaces), or a new one. */
    @Transaction
    open suspend fun findOrCreate(name: String, now: Long): TagEntity {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "a tag needs a name" }
        named(clean)?.let { return it }
        insertTag(TagEntity(name = clean, createdAt = now))
        return named(clean)!!
    }

    /** Tags a scrap; tagging it twice with the same name is harmless. Returns the tag. */
    @Transaction
    open suspend fun tag(scrapId: Long, name: String, now: Long): TagEntity {
        val t = findOrCreate(name, now)
        insertLink(ScrapTagEntity(scrapId, t.id))
        return t
    }

    @Query("DELETE FROM scrap_tag WHERE scrap_id = :scrapId AND tag_id = :tagId") abstract suspend fun untag(scrapId: Long, tagId: Long)

    @Query("UPDATE tag SET name = :name WHERE id = :tagId") abstract suspend fun rename(tagId: Long, name: String)

    /** Deletes the tag everywhere; the scraps it was on stay. */
    @Query("DELETE FROM tag WHERE id = :tagId") abstract suspend fun delete(tagId: Long)

    /** Live scraps carrying any of [tagIds]. */
    @Query(
        "SELECT * FROM scrap WHERE deleted_at IS NULL AND id IN " +
            "(SELECT scrap_id FROM scrap_tag WHERE tag_id IN (:tagIds)) ORDER BY created_at DESC, id DESC"
    )
    abstract suspend fun scrapsWithAny(tagIds: List<Long>): List<ScrapEntity>

    /** Live scraps carrying every one of [tagIds] ("lyrics" and "love song"). */
    @Query(
        "SELECT * FROM scrap WHERE deleted_at IS NULL AND id IN " +
            "(SELECT scrap_id FROM scrap_tag WHERE tag_id IN (:tagIds) GROUP BY scrap_id HAVING COUNT(*) = :count) " +
            "ORDER BY created_at DESC, id DESC"
    )
    protected abstract suspend fun scrapsWithAll(tagIds: List<Long>, count: Int): List<ScrapEntity>

    open suspend fun scrapsWithAll(tagIds: List<Long>): List<ScrapEntity> {
        val distinct = tagIds.distinct()
        return scrapsWithAll(distinct, distinct.size)
    }
}

@Dao
interface GroupDao {
    @Insert suspend fun insert(group: GroupEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addMember(member: GroupMemberEntity)
    @Query("DELETE FROM group_member WHERE group_id = :groupId AND scrap_id = :scrapId") suspend fun removeMember(groupId: Long, scrapId: Long)

    @Query("SELECT * FROM scrap_group WHERE deleted_at IS NULL ORDER BY name") suspend fun live(): List<GroupEntity>

    @Query(
        "SELECT scrap_group.*, (SELECT COUNT(*) FROM group_member JOIN scrap ON scrap.id = group_member.scrap_id " +
            "WHERE group_member.group_id = scrap_group.id AND scrap.deleted_at IS NULL) AS members " +
            "FROM scrap_group WHERE deleted_at IS NULL ORDER BY name COLLATE NOCASE"
    )
    fun liveRows(): Flow<List<GroupRow>>

    @Query("UPDATE scrap_group SET name = :name WHERE id = :id") suspend fun rename(id: Long, name: String)

    /** The live-Group memberships of these scraps (kept so a removal can be undone). */
    @Query(
        "SELECT group_member.* FROM group_member JOIN scrap_group ON scrap_group.id = group_member.group_id " +
            "WHERE group_member.scrap_id IN (:scrapIds) AND scrap_group.deleted_at IS NULL"
    )
    suspend fun liveMemberships(scrapIds: List<Long>): List<GroupMemberEntity>

    /** Takes these scraps out of every live Group (plan 3.5's one-tap offer); Groups in the trash keep them. */
    @Query(
        "DELETE FROM group_member WHERE scrap_id IN (:scrapIds) " +
            "AND group_id IN (SELECT id FROM scrap_group WHERE deleted_at IS NULL)"
    )
    suspend fun leaveAllLive(scrapIds: List<Long>): Int

    @Query(
        "SELECT scrap.* FROM scrap JOIN group_member ON group_member.scrap_id = scrap.id " +
            "WHERE group_member.group_id = :groupId AND scrap.deleted_at IS NULL ORDER BY group_member.added_at"
    )
    suspend fun scrapsIn(groupId: Long): List<ScrapEntity>

    @Query(
        "SELECT scrap_group.* FROM scrap_group JOIN group_member ON group_member.group_id = scrap_group.id " +
            "WHERE group_member.scrap_id = :scrapId AND scrap_group.deleted_at IS NULL ORDER BY scrap_group.name"
    )
    suspend fun groupsOf(scrapId: Long): List<GroupEntity>

    @Query("UPDATE scrap_group SET deleted_at = :now WHERE id = :id") suspend fun moveToTrash(id: Long, now: Long)
    @Query("UPDATE scrap_group SET deleted_at = NULL WHERE id = :id") suspend fun restore(id: Long)
    @Query("DELETE FROM scrap_group WHERE deleted_at <= :cutoff") suspend fun purgeExpired(cutoff: Long): Int
}

@Dao
abstract class ProjectDao {
    @Insert abstract suspend fun insert(project: ProjectEntity): Long
    @Query("SELECT * FROM project WHERE id = :id") abstract suspend fun get(id: Long): ProjectEntity?
    @Query("SELECT * FROM project WHERE deleted_at IS NULL ORDER BY edited_at DESC") abstract suspend fun live(): List<ProjectEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE) protected abstract suspend fun insertRef(ref: ProjectRefEntity): Long
    @Update protected abstract suspend fun updateRef(ref: ProjectRefEntity)

    /** Puts a scrap on a project's Board: a reference only. The scrap stays in the Library and in any other project. */
    open suspend fun addRef(ref: ProjectRefEntity) {
        insertRef(ref.sanitized())
    }

    /** Takes a scrap off this project's Board only; the scrap and its other projects are untouched. */
    @Query("DELETE FROM project_ref WHERE project_id = :projectId AND scrap_id = :scrapId")
    abstract suspend fun removeRef(projectId: Long, scrapId: Long)

    @Query("UPDATE scrap SET show_in_library = 1 WHERE id = :scrapId AND project_id = :projectId")
    protected abstract suspend fun unhide(projectId: Long, scrapId: Long)

    /**
     * Takes a scrap off a project's Board. A recording made there and switched out of Library search
     * comes back into the Library, so nothing is left that no list shows.
     */
    @Transaction
    open suspend fun takeOff(projectId: Long, scrapId: Long) {
        removeRef(projectId, scrapId)
        unhide(projectId, scrapId)
    }

    @Insert protected abstract suspend fun insertScrap(scrap: ScrapEntity): Long

    /** Something made inside a project: stored as a Library scrap that remembers its origin, and on that project's Board. */
    @Transaction
    open suspend fun createIn(projectId: Long, scrap: ScrapEntity, now: Long): Long {
        val id = insertScrap(scrap.copy(projectId = projectId))
        insertRef(ProjectRefEntity(projectId = projectId, scrapId = id, addedAt = now))
        return id
    }

    /** What the project's Board can show: live scraps it references, in the order they were added. */
    @Query(
        "SELECT scrap.* FROM scrap JOIN project_ref ON project_ref.scrap_id = scrap.id " +
            "WHERE project_ref.project_id = :projectId AND scrap.deleted_at IS NULL ORDER BY project_ref.added_at, scrap.id"
    )
    abstract suspend fun boardScraps(projectId: Long): List<ScrapEntity>

    /** Changes this project's settings for one scrap; nothing outside this row changes. */
    open suspend fun saveSettings(ref: ProjectRefEntity) = updateRef(ref.sanitized())

    @Query("SELECT * FROM project_ref WHERE project_id = :projectId AND scrap_id = :scrapId")
    protected abstract suspend fun rawRef(projectId: Long, scrapId: Long): ProjectRefEntity?

    /** Settings are sanitized on read too, so a damaged row is never trusted. */
    open suspend fun ref(projectId: Long, scrapId: Long): ProjectRefEntity? = rawRef(projectId, scrapId)?.sanitized()

    @Query("SELECT * FROM project_ref WHERE project_id = :projectId ORDER BY added_at")
    abstract suspend fun refsOf(projectId: Long): List<ProjectRefEntity>

    @Query("SELECT project.* FROM project JOIN project_ref ON project_ref.project_id = project.id WHERE project_ref.scrap_id = :scrapId AND project.deleted_at IS NULL ORDER BY project.name")
    abstract suspend fun projectsUsing(scrapId: Long): List<ProjectEntity>

    @Query("UPDATE project SET deleted_at = :now WHERE id = :id") abstract suspend fun moveToTrash(id: Long, now: Long)
    @Query("UPDATE project SET deleted_at = NULL WHERE id = :id") abstract suspend fun restore(id: Long)
    @Query("DELETE FROM project WHERE deleted_at <= :cutoff") abstract suspend fun purgeExpired(cutoff: Long): Int

    @Query("UPDATE project SET name = :name, edited_at = :now WHERE id = :id") abstract suspend fun rename(id: Long, name: String, now: Long)
    @Query("UPDATE project SET edited_at = :now WHERE id = :id") abstract suspend fun touch(id: Long, now: Long)

    /** Live projects for the switcher, most recently worked on first, with how many live scraps each Board holds. */
    @Query(
        "SELECT project.*, (SELECT COUNT(*) FROM project_ref JOIN scrap ON scrap.id = project_ref.scrap_id " +
            "WHERE project_ref.project_id = project.id AND scrap.deleted_at IS NULL) AS scraps " +
            "FROM project WHERE deleted_at IS NULL ORDER BY edited_at DESC, id DESC"
    )
    abstract fun liveRows(): Flow<List<ProjectRow>>

    @Query("SELECT * FROM project WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
    abstract fun trashFlow(): Flow<List<ProjectEntity>>

    /** A project's Board: each live scrap it references, with the row extras, its window and the take its glimpse shows. */
    @Query(
        "SELECT scrap.*, " + ROW_EXTRAS + ", project_ref.added_at AS addedAt, project_ref.board_x AS x, project_ref.board_y AS y, " +
            "project_ref.board_w AS w, project_ref.board_h AS h, project_ref.board_z AS z, project_ref.body AS ownBody, " +
            "(SELECT take.audio_path FROM take WHERE take.scrap_id = scrap.id AND take.deleted_at IS NULL ORDER BY take.starred DESC, take.id DESC LIMIT 1) AS glimpsePath " +
            "FROM scrap JOIN project_ref ON project_ref.scrap_id = scrap.id " +
            "WHERE project_ref.project_id = :projectId AND scrap.deleted_at IS NULL ORDER BY project_ref.board_z, project_ref.added_at, scrap.id"
    )
    abstract fun boardRows(projectId: Long): Flow<List<BoardRow>>

    /** Moves or resizes one window on this project's Board; nothing else about the scrap changes. */
    @Query(
        "UPDATE project_ref SET board_x = :x, board_y = :y, board_w = :w, board_h = :h, board_z = :z " +
            "WHERE project_id = :projectId AND scrap_id = :scrapId"
    )
    abstract suspend fun place(projectId: Long, scrapId: Long, x: Int, y: Int, w: Int, h: Int, z: Int)

    /** The text of a written scrap that another project adds: it gets its own copy. Null for recordings and for its own project. */
    @Query("SELECT body FROM scrap WHERE id = :scrapId AND format != 'recording' AND (project_id IS NULL OR project_id != :projectId)")
    protected abstract suspend fun bodyToCopy(projectId: Long, scrapId: Long): String?

    /**
     * Puts several scraps on a project's Board. Ones already there keep their settings. Returns how many were new.
     * Plan 4.2: a lyric, text or chord chart added from elsewhere becomes this project's own copy, edited
     * independently. [Claude] The copy is made when it is added (the plan's wording; copy-on-first-edit
     * would be a change to this one function).
     */
    @Transaction
    open suspend fun addRefs(projectId: Long, scrapIds: Collection<Long>, now: Long): Int {
        var added = 0
        for (id in scrapIds) {
            val ref = ProjectRefEntity(projectId = projectId, scrapId = id, addedAt = now, body = bodyToCopy(projectId, id))
            if (insertRef(ref) != -1L) added++
        }
        if (added > 0) touch(projectId, now)
        return added
    }

    /** What a project shows for a scrap: its own copy if it has one, else the scrap's text. */
    @Query(
        "SELECT scrap.body AS libraryBody, project_ref.body AS ownBody FROM scrap " +
            "LEFT JOIN project_ref ON project_ref.scrap_id = scrap.id AND project_ref.project_id = :projectId WHERE scrap.id = :scrapId"
    )
    abstract fun bodyIn(projectId: Long, scrapId: Long): Flow<BodyPair?>

    /** Every project's own version of a scrap's text, with the project's name (Learn: in your material). */
    @Query(
        "SELECT project_ref.scrap_id AS scrapId, project.id AS projectId, project.name AS projectName, project_ref.body AS body FROM project_ref " +
            "JOIN project ON project.id = project_ref.project_id WHERE project_ref.body IS NOT NULL AND project.deleted_at IS NULL"
    )
    abstract suspend fun ownBodies(): List<OwnBody>

    @Query("UPDATE project_ref SET body = :body WHERE project_id = :projectId AND scrap_id = :scrapId")
    abstract suspend fun setOwnBody(projectId: Long, scrapId: Long, body: String)

    /** Which of these scraps each live project already uses (for the add-to-project picker). */
    @Query(
        "SELECT project_ref.project_id AS projectId, project_ref.scrap_id AS scrapId FROM project_ref " +
            "JOIN project ON project.id = project_ref.project_id WHERE project.deleted_at IS NULL AND project_ref.scrap_id IN (:scrapIds)"
    )
    abstract fun usesOf(scrapIds: List<Long>): Flow<List<RefKey>>
}

/** A live project with how many live scraps are on its Board. */
data class ProjectRow(@Embedded val project: ProjectEntity, val scraps: Int)

data class RefKey(val projectId: Long, val scrapId: Long)

/** A written scrap's text in the Library and, if the project has its own copy, there. */
data class BodyPair(val libraryBody: String?, val ownBody: String?) {
    val shown: String get() = ownBody ?: libraryBody.orEmpty()
}

/** One window on a Board: the scrap as a Library row plus where its window sits ([x] null = not placed yet). */
data class BoardRow(
    @Embedded val scrap: ScrapEntity,
    val tags: String?,
    val takes: Int,
    val starred: Int,
    val groupIds: String? = null,
    val projectUses: Int = 0,
    val projectNames: String? = null,
    val addedAt: Long,
    val x: Int? = null,
    val y: Int? = null,
    val w: Int? = null,
    val h: Int? = null,
    val z: Int = 0,
    /** The take the window's waveform shows: the newest starred one, else the newest. */
    val glimpsePath: String? = null,
    /** This project's own copy of a written scrap, if it has one. */
    val ownBody: String? = null,
) {
    val tagList: List<String> get() = tags?.split(TAG_SEPARATOR)?.filter { it.isNotEmpty() }?.sortedBy { it.lowercase() } ?: emptyList()
    val placed: Boolean get() = x != null && y != null && w != null && h != null
    /** The text this Board shows: its own copy, else the scrap's. */
    val shownBody: String get() = ownBody ?: scrap.body.orEmpty()
}

@Dao
interface SettingDao {
    @Upsert suspend fun put(setting: AppSettingEntity)
    @Query("SELECT value FROM app_setting WHERE `key` = :key") suspend fun get(key: String): String?
}

/** Tags are joined with this in [LibraryRow.tags] (ASCII unit separator, char(31) in SQL). */
const val TAG_SEPARATOR = '\u001F'

private const val ROW_EXTRAS =
    "(SELECT group_concat(tag.name, char(31)) FROM scrap_tag JOIN tag ON tag.id = scrap_tag.tag_id WHERE scrap_tag.scrap_id = scrap.id) AS tags, " +
        "(SELECT COUNT(*) FROM take WHERE take.scrap_id = scrap.id AND take.deleted_at IS NULL) AS takes, " +
        "(SELECT COUNT(*) FROM take WHERE take.scrap_id = scrap.id AND take.deleted_at IS NULL AND take.starred = 1) AS starred, " +
        "(SELECT group_concat(group_member.group_id) FROM group_member JOIN scrap_group ON scrap_group.id = group_member.group_id " +
        "WHERE group_member.scrap_id = scrap.id AND scrap_group.deleted_at IS NULL) AS groupIds, " +
        "(SELECT COUNT(*) FROM project_ref JOIN project ON project.id = project_ref.project_id " +
        "WHERE project_ref.scrap_id = scrap.id AND project.deleted_at IS NULL) AS projectUses, " +
        "(SELECT group_concat(project.name, char(31)) FROM project_ref JOIN project ON project.id = project_ref.project_id " +
        "WHERE project_ref.scrap_id = scrap.id AND project.deleted_at IS NULL) AS projectNames"

/** One Library row: the scrap plus what the list shows beside it. */
data class LibraryRow(
    @Embedded val scrap: ScrapEntity,
    val tags: String?,
    val takes: Int,
    val starred: Int,
    /** Live Groups it's in, as comma-joined ids. */
    val groupIds: String? = null,
    /** How many live projects use it. */
    val projectUses: Int = 0,
    /** Their names, joined like [tags]. */
    val projectNames: String? = null,
) {
    val projectList: List<String> get() = projectNames?.split(TAG_SEPARATOR)?.filter { it.isNotEmpty() }?.sortedBy { it.lowercase() } ?: emptyList()
    val tagList: List<String> get() = tags?.split(TAG_SEPARATOR)?.filter { it.isNotEmpty() }?.sortedBy { it.lowercase() } ?: emptyList()
    val groupSet: Set<Long> get() = groupIds?.split(',')?.mapNotNull { it.trim().toLongOrNull() }?.toSet() ?: emptySet()
}

/** A live Group with how many live scraps are in it. */
data class GroupRow(@Embedded val group: GroupEntity, val members: Int)

/** Take letters: A..Z, then AA, AB... (spreadsheet order). */
object TakeLetters {
    fun next(used: Collection<String>): String {
        var i = 0
        while (true) {
            val l = letter(i++)
            if (l !in used) return l
        }
    }

    fun letter(index: Int): String {
        require(index >= 0)
        val sb = StringBuilder()
        var n = index
        do {
            sb.append('A' + n % 26)
            n = n / 26 - 1
        } while (n >= 0)
        return sb.reverse().toString()
    }
}

/** A project's own copy of a scrap's text. */
data class OwnBody(val scrapId: Long, val projectId: Long, val projectName: String, val body: String)
