package com.betterappmafia.apophenia.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DatabaseTest {

    private lateinit var db: ApopheniaDatabase
    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    @BeforeTest
    fun open() {
        db = Room.inMemoryDatabaseBuilder<ApopheniaDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    @AfterTest
    fun close() = db.close()

    private suspend fun scrap(
        title: String? = "Verse riff", format: ScrapFormat = ScrapFormat.RECORDING,
        projectId: Long? = null, showInLibrary: Boolean = true, at: Long = now,
    ) = db.scraps().insert(
        ScrapEntity(format = format, title = title, createdAt = at, editedAt = at, projectId = projectId, showInLibrary = showInLibrary)
    )

    private suspend fun project(name: String) = db.projects().insert(ProjectEntity(name = name, createdAt = now, editedAt = now))

    private suspend fun take(scrapId: Long, letter: String) =
        db.takes().insert(TakeEntity(scrapId = scrapId, letter = letter, audioPath = "audio/$scrapId-$letter.wav", durationFrames = 48_000, sampleRate = 48_000, createdAt = now))

    private suspend fun tagNames(scrapId: Long) = db.tags().tagsOf(scrapId).map { it.name }

    // --- Scraps and takes ---------------------------------------------------------------------

    @Test
    fun `a scrap round trips with its format stored as a token`() = runBlocking<Unit> {
        val id = scrap("Bridge idea", ScrapFormat.CHORD_CHART)
        val back = db.scraps().get(id)!!
        assertEquals(ScrapFormat.CHORD_CHART, back.format)
        assertEquals("Bridge idea", back.title)
        assertTrue(back.showInLibrary)
        assertNull(back.deletedAt)
        assertNull(back.bpmLow)
        assertNull(back.key)
    }

    @Test
    fun `takes are lettered and listed A to Z then AA`() = runBlocking<Unit> {
        val s = scrap()
        for (l in listOf("A", "B", "AA", "Z")) take(s, l)
        assertEquals(listOf("A", "B", "Z", "AA"), db.takes().takesOf(s).map { it.letter })
        assertEquals("C", TakeLetters.next(db.takes().lettersUsed(s)))
        assertEquals("AA", TakeLetters.letter(26))
        assertEquals("AZ", TakeLetters.letter(51))
        assertEquals("BA", TakeLetters.letter(52))
    }

    @Test
    fun `a trashed take keeps its letter so a restore never collides`() = runBlocking<Unit> {
        val s = scrap()
        take(s, "A")
        val b = take(s, "B")
        db.takes().moveToTrash(b, now)
        assertEquals("C", TakeLetters.next(db.takes().lettersUsed(s)))
        assertEquals(listOf("A"), db.takes().takesOf(s).map { it.letter })
        db.takes().restore(b)
        assertEquals(listOf("A", "B"), db.takes().takesOf(s).map { it.letter })
    }

    @Test
    fun `starring a take is stored`() = runBlocking<Unit> {
        val t = take(scrap(), "A")
        db.takes().setStarred(t, true)
        assertTrue(db.takes().get(t)!!.starred)
    }

    @Test
    fun `foreign keys are enforced and purging a scrap removes its takes and tag links but not the tags`() = runBlocking<Unit> {
        val s = scrap()
        take(s, "A")
        db.tags().tag(s, "uneasy", now)
        assertTrue(runCatching { take(9999, "A") }.isFailure, "a take for a missing scrap must be refused")
        db.scraps().moveToTrash(s, now - 31 * day)
        assertEquals(1, db.scraps().purgeExpired(trashCutoff(now)))
        assertEquals(emptyList(), db.takes().takesOf(s))
        assertEquals(emptyList(), db.tags().tagsOf(s))
        assertNotNull(db.tags().named("uneasy"), "the tag stays available to pick")
    }

    // --- Library and trash --------------------------------------------------------------------

    @Test
    fun `the Library shows live scraps and honours the show-in-library switch`() = runBlocking<Unit> {
        val p = project("Love Song")
        val plain = scrap("plain", at = now)
        val inProjectShown = scrap("in project, shown", projectId = p, at = now + 1)
        scrap("in project, hidden", projectId = p, showInLibrary = false, at = now + 2)
        val trashed = scrap("trashed", at = now + 3)
        db.scraps().moveToTrash(trashed, now)
        assertEquals(listOf(inProjectShown, plain), db.scraps().library().map { it.id })
        assertEquals(listOf(trashed), db.scraps().trash().map { it.id })
    }

    @Test
    fun `trash keeps things for 30 days, then purges them`() = runBlocking<Unit> {
        val young = scrap("young")
        val old = scrap("old")
        take(old, "A")
        db.scraps().moveToTrash(young, now - 29 * day)
        db.scraps().moveToTrash(old, now - 30 * day)
        val cutoff = trashCutoff(now)
        assertEquals(listOf("audio/$old-A.wav"), db.scraps().audioToPurge(cutoff))
        assertEquals(1, db.scraps().purgeExpired(cutoff))
        assertNull(db.scraps().get(old))
        assertNotNull(db.scraps().get(young))
        db.scraps().restore(young)
        assertNull(db.scraps().get(young)!!.deletedAt)
    }

    @Test
    fun `trashed takes, projects and Groups expire on the same clock`() = runBlocking<Unit> {
        val s = scrap()
        val t = take(s, "A")
        val p = project("Old project")
        val g = db.groups().insert(GroupEntity(name = "Dark riffs", createdAt = now))
        db.takes().moveToTrash(t, now - 31 * day)
        db.projects().moveToTrash(p, now - 31 * day)
        db.groups().moveToTrash(g, now - 31 * day)
        val cutoff = trashCutoff(now)
        assertEquals(listOf("audio/$s-A.wav"), db.scraps().audioToPurge(cutoff))
        assertEquals(1, db.takes().purgeExpired(cutoff))
        assertEquals(1, db.projects().purgeExpired(cutoff))
        assertEquals(1, db.groups().purgeExpired(cutoff))
        assertNotNull(db.scraps().get(s), "purging a take leaves its scrap")
    }

    // --- Tags: the musician's own words, reusable ---------------------------------------------

    @Test
    fun `one lyric can be both a love song and a political song, and lyrics is a tag too`() = runBlocking<Unit> {
        val lyric = scrap("Paper walls", ScrapFormat.LYRIC)
        db.tags().tag(lyric, "lyrics", now)
        db.tags().tag(lyric, "love song", now)
        db.tags().tag(lyric, "political song", now)
        assertEquals(listOf("love song", "lyrics", "political song"), tagNames(lyric))
    }

    @Test
    fun `a tag is made once and reused, whatever the case`() = runBlocking<Unit> {
        val riff = scrap("Low riff")
        val line = scrap("Walking line")
        val first = db.tags().tag(riff, "bass", now)
        val again = db.tags().tag(line, "Bass ", now + 1)
        assertEquals(first.id, again.id)
        assertEquals("bass", again.name, "the first spelling is kept")
        assertEquals(1, db.tags().all().size)
        db.tags().tag(riff, "BASS", now)
        assertEquals(listOf("bass"), tagNames(riff), "tagging twice is harmless")
    }

    @Test
    fun `the pick list offers every tag, most used first, and narrows as you type`() = runBlocking<Unit> {
        val a = scrap("a")
        val b = scrap("b")
        val c = scrap("c")
        for (s in listOf(a, b, c)) db.tags().tag(s, "guitar", now)
        for (s in listOf(a, b)) db.tags().tag(s, "lyrics", now)
        db.tags().tag(a, "uneasy", now)
        db.tags().findOrCreate("city at night", now)
        assertEquals(listOf("guitar", "lyrics", "uneasy", "city at night"), db.tags().pickList().map { it.name })
        assertEquals(listOf("lyrics"), db.tags().pickList("LYR").map { it.name })
        // Trashed scraps don't count toward "most used".
        db.scraps().moveToTrash(a, now)
        db.scraps().moveToTrash(b, now)
        assertEquals("guitar", db.tags().pickList().first().name)
        assertEquals(listOf("city at night", "lyrics", "uneasy"), db.tags().pickList().drop(1).map { it.name })
    }

    @Test
    fun `scraps are found by any or all of their tags`() = runBlocking<Unit> {
        val loveLyric = scrap("love lyric", ScrapFormat.LYRIC)
        val protestLyric = scrap("protest lyric", ScrapFormat.LYRIC)
        val bassRiff = scrap("bass riff")
        val lyrics = db.tags().tag(loveLyric, "lyrics", now).id
        db.tags().tag(protestLyric, "lyrics", now)
        val love = db.tags().tag(loveLyric, "love song", now).id
        val political = db.tags().tag(protestLyric, "political song", now).id
        db.tags().tag(bassRiff, "bass", now)
        assertEquals(setOf(loveLyric, protestLyric), db.tags().scrapsWithAny(listOf(love, political)).map { it.id }.toSet())
        assertEquals(listOf(loveLyric), db.tags().scrapsWithAll(listOf(lyrics, love)).map { it.id })
        assertEquals(listOf(loveLyric), db.tags().scrapsWithAll(listOf(lyrics, love, love)).map { it.id })
    }

    @Test
    fun `removing or deleting a tag never deletes scraps`() = runBlocking<Unit> {
        val s = scrap()
        val t = db.tags().tag(s, "maybe chorus", now)
        db.tags().tag(s, "guitar", now)
        db.tags().untag(s, t.id)
        assertEquals(listOf("guitar"), tagNames(s))
        assertNotNull(db.tags().named("maybe chorus"), "untagging keeps the tag in the pick list")
        db.tags().delete(db.tags().named("guitar")!!.id)
        assertEquals(emptyList(), tagNames(s))
        assertNotNull(db.scraps().get(s))
    }

    @Test
    fun `renaming a tag renames it on every scrap`() = runBlocking<Unit> {
        val a = scrap("a")
        val b = scrap("b")
        val t = db.tags().tag(a, "sad", now)
        db.tags().tag(b, "sad", now)
        db.tags().rename(t.id, "melancholy")
        assertEquals(listOf("melancholy"), tagNames(a))
        assertEquals(listOf("melancholy"), tagNames(b))
    }

    // --- BPM and key: the only built-in values ------------------------------------------------

    @Test
    fun `your BPM is never overwritten by the metronome or re-analysis`() = runBlocking<Unit> {
        val s = scrap()
        assertTrue(db.scraps().offerBpm(s, 118.0, source = ValueSource.ANALYSIS))
        assertTrue(db.scraps().offerBpm(s, 120.0, source = ValueSource.METRONOME))
        assertFalse(db.scraps().offerBpm(s, 119.0, source = ValueSource.ANALYSIS))
        assertTrue(db.scraps().offerBpm(s, 60.0, source = ValueSource.USER))
        assertFalse(db.scraps().offerBpm(s, 120.0, source = ValueSource.METRONOME))
        assertFalse(db.scraps().offerBpm(s, 121.0, source = ValueSource.ANALYSIS))
        val back = db.scraps().get(s)!!
        assertEquals(60.0, back.bpmLow)
        assertEquals(60.0, back.bpmHigh)
        assertEquals(ValueSource.USER, back.bpmSource)
    }

    @Test
    fun `BPM can be a range or free time, and reversed ranges are refused`() = runBlocking<Unit> {
        val s = scrap()
        db.scraps().offerBpm(s, 90.0, 96.0, source = ValueSource.USER)
        assertEquals(90.0 to 96.0, db.scraps().get(s)!!.let { it.bpmLow to it.bpmHigh })
        db.scraps().offerBpm(s, null, null, freeTime = true, source = ValueSource.USER)
        val free = db.scraps().get(s)!!
        assertTrue(free.freeTime)
        assertNull(free.bpmLow)
        assertTrue(runCatching { db.scraps().offerBpm(s, 120.0, 100.0, source = ValueSource.USER) }.isFailure)
        assertTrue(runCatching { db.scraps().offerBpm(s, Double.NaN, source = ValueSource.USER) }.isFailure)
    }

    @Test
    fun `your key is never overwritten by analysis`() = runBlocking<Unit> {
        val s = scrap()
        assertTrue(db.scraps().offerKey(s, "A aeolian", ValueSource.ANALYSIS))
        assertTrue(db.scraps().offerKey(s, "D dorian", ValueSource.USER))
        assertFalse(db.scraps().offerKey(s, "C ionian", ValueSource.ANALYSIS))
        assertEquals("D dorian" to ValueSource.USER, db.scraps().get(s)!!.let { it.key to it.keySource })
    }

    // --- Projects: what's being considered; scraps live in several places ---------------------

    @Test
    fun `something made in a project is on its Board and still in the Library`() = runBlocking<Unit> {
        val p = project("Love Song")
        val s = db.projects().createIn(p, ScrapEntity(format = ScrapFormat.LYRIC, title = "Chorus draft", createdAt = now, editedAt = now), now)
        assertEquals(listOf(s), db.projects().boardScraps(p).map { it.id })
        assertEquals(listOf(s), db.scraps().library().map { it.id })
        assertEquals(p, db.scraps().get(s)!!.projectId)
    }

    @Test
    fun `one scrap can sit on several Boards at once, with its tags unchanged everywhere`() = runBlocking<Unit> {
        val riff = scrap("Low riff")
        db.tags().tag(riff, "bass", now)
        val p1 = project("Love Song")
        val p2 = project("Night Drive")
        db.projects().addRef(ProjectRefEntity(p1, riff, now))
        db.projects().addRef(ProjectRefEntity(p2, riff, now))
        assertEquals(listOf(riff), db.projects().boardScraps(p1).map { it.id })
        assertEquals(listOf(riff), db.projects().boardScraps(p2).map { it.id })
        assertEquals(listOf(riff), db.scraps().library().map { it.id })
        assertEquals(listOf("Love Song", "Night Drive"), db.projects().projectsUsing(riff).map { it.name })
        assertEquals(listOf("bass"), tagNames(riff))

        db.projects().removeRef(p1, riff)
        assertEquals(emptyList(), db.projects().boardScraps(p1))
        assertEquals(listOf(riff), db.projects().boardScraps(p2).map { it.id })
        assertEquals(listOf(riff), db.scraps().library().map { it.id })
        assertEquals(listOf("bass"), tagNames(riff))
    }

    @Test
    fun `changing one project's trim leaves the other project and the Library untouched`() = runBlocking<Unit> {
        val s = scrap()
        take(s, "A")
        val p1 = project("Love Song")
        val p2 = project("Night Drive")
        db.projects().addRef(ProjectRefEntity(p1, s, now))
        db.projects().addRef(ProjectRefEntity(p2, s, now))
        val before = db.scraps().get(s)
        val takesBefore = db.takes().takesOf(s)

        db.projects().saveSettings(db.projects().ref(p1, s)!!.copy(trimStartFrames = 4_800, gainDb = -3.0, loopStartFrames = 0, loopEndFrames = 24_000))

        val r1 = db.projects().ref(p1, s)!!
        assertEquals(4_800, r1.trimStartFrames)
        assertEquals(-3.0, r1.gainDb)
        assertEquals(24_000L, r1.loopEndFrames)
        assertEquals(ProjectRefEntity(p2, s, now), db.projects().ref(p2, s))
        assertEquals(before, db.scraps().get(s))
        assertEquals(takesBefore, db.takes().takesOf(s))
    }

    @Test
    fun `damaged project settings are sanitized instead of trusted`() = runBlocking<Unit> {
        val s = scrap()
        val p = project("P")
        db.projects().addRef(
            ProjectRefEntity(
                p, s, now, trimStartFrames = -10, trimEndFrames = -5, loopStartFrames = 500, loopEndFrames = 100,
                gainDb = Double.NaN, fadeInFrames = -1, fadeOutFrames = -1,
            )
        )
        val r = db.projects().ref(p, s)!!
        assertEquals(0, r.trimStartFrames)
        assertNull(r.trimEndFrames)
        assertNull(r.loopStartFrames)
        assertNull(r.loopEndFrames)
        assertEquals(0.0, r.gainDb)
        assertEquals(0, r.fadeInFrames)
        assertEquals(0, r.fadeOutFrames)
        assertEquals(0.0, ProjectRefEntity(p, s, now, gainDb = Double.POSITIVE_INFINITY).sanitized().gainDb)
    }

    // --- Groups, settings, format ---------------------------------------------------------------

    @Test
    fun `Groups hold scraps without owning them`() = runBlocking<Unit> {
        val s = scrap()
        val g1 = db.groups().insert(GroupEntity(name = "Possible choruses", createdAt = now))
        val g2 = db.groups().insert(GroupEntity(name = "Love songs", createdAt = now))
        db.groups().addMember(GroupMemberEntity(g1, s, now))
        db.groups().addMember(GroupMemberEntity(g2, s, now))
        db.groups().addMember(GroupMemberEntity(g2, s, now + 1))
        assertEquals(listOf("Love songs", "Possible choruses"), db.groups().groupsOf(s).map { it.name })
        db.groups().moveToTrash(g1, now - 31 * day)
        db.groups().purgeExpired(trashCutoff(now))
        assertNotNull(db.scraps().get(s), "deleting a Group never deletes its scraps")
        assertEquals(listOf(s), db.groups().scrapsIn(g2).map { it.id })
    }

    @Test
    fun `app settings upsert by key`() = runBlocking<Unit> {
        db.settings().put(AppSettingEntity("octave_label", "C4"))
        db.settings().put(AppSettingEntity("octave_label", "C3"))
        assertEquals("C3", db.settings().get("octave_label"))
        assertNull(db.settings().get("missing"))
    }

    @Test
    fun `stored tokens from a newer version are refused, not silently mapped`() {
        assertTrue(runCatching { ScrapFormat.of("hologram") }.isFailure)
        assertTrue(runCatching { ValueSource.of("ai") }.isFailure)
    }

    @Test
    fun `schema version 1 is exported for migration tests`() {
        val schema = File("schemas/com.betterappmafia.apophenia.data.ApopheniaDatabase/1.json")
        assertTrue(schema.isFile, "missing ${schema.absolutePath}")
        val text = schema.readText()
        for (table in listOf("scrap", "take", "tag", "scrap_tag", "scrap_group", "group_member", "project", "project_ref", "app_setting")) {
            assertTrue("\"tableName\": \"$table\"" in text, "schema lacks table $table")
        }
    }
}
