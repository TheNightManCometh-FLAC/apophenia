package com.betterappmafia.apophenia.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryAndMigrationTest {
    private lateinit var db: ApopheniaDatabase
    private val now = 1_800_000_000_000L

    @BeforeTest fun open() {
        db = Room.inMemoryDatabaseBuilder<ApopheniaDatabase>().setDriver(BundledSQLiteDriver()).build()
    }

    @AfterTest fun close() = db.close()

    private fun take(path: String, preroll: Long = 0) =
        TakeEntity(scrapId = 0, letter = "?", audioPath = path, durationFrames = 96_000, sampleRate = 48_000, createdAt = now, prerollFrames = preroll)

    @Test fun `a new recording gets take A and the row shows its tags and take counts`() = runBlocking<Unit> {
        val id = db.scraps().createRecording(ScrapEntity(format = ScrapFormat.RECORDING, createdAt = now, editedAt = now), take("a.wav", preroll = 4800))
        db.tags().tag(id, "Bass", now)
        db.tags().tag(id, "dark", now)
        val takes = db.takes().takesOf(id)
        assertEquals(listOf("A"), takes.map { it.letter })
        assertEquals(4800L, takes.single().prerollFrames)

        val row = db.scraps().libraryRows().first().single()
        assertEquals(listOf("Bass", "dark"), row.tagList)
        assertEquals(1, row.takes)
        assertEquals(0, row.starred)
    }

    @Test fun `another take is lettered after every letter used, even a trashed one`() = runBlocking<Unit> {
        val id = db.scraps().createRecording(ScrapEntity(format = ScrapFormat.RECORDING, createdAt = now, editedAt = now), take("a.wav"))
        assertEquals("B", db.scraps().addTake(id, take("b.wav"), now + 1))
        val b = db.takes().takesOf(id).first { it.letter == "B" }
        db.takes().moveToTrash(b.id, now + 2)
        db.takes().setStarred(db.takes().takesOf(id).single().id, true)
        assertEquals("C", db.scraps().addTake(id, take("c.wav"), now + 3))

        val row = db.scraps().libraryRows().first().single()
        assertEquals(2, row.takes)      // A and C; B is in the trash
        assertEquals(1, row.starred)
        assertEquals(now + 3, row.scrap.editedAt)
    }

    @Test fun `a trashed scrap leaves the Library rows and appears in the trash rows`() = runBlocking<Unit> {
        val id = db.scraps().createRecording(ScrapEntity(format = ScrapFormat.RECORDING, title = "Room take", createdAt = now, editedAt = now), take("a.wav"))
        db.scraps().moveToTrash(id, now + 5)
        assertTrue(db.scraps().libraryRows().first().isEmpty())
        assertEquals("Room take", db.scraps().trashRows().first().single().scrap.title)
        db.scraps().restore(id)
        assertEquals(1, db.scraps().libraryRows().first().size)
    }

    @Test fun `a scrap with no tags has an empty tag list, not one empty tag`() = runBlocking<Unit> {
        db.scraps().createRecording(ScrapEntity(format = ScrapFormat.RECORDING, createdAt = now, editedAt = now), take("a.wav"))
        val row = db.scraps().libraryRows().first().single()
        assertNull(row.tags)
        assertEquals(emptyList(), row.tagList)
    }

    @Test fun `a row lists its live Groups and counts the live projects using it`() = runBlocking<Unit> {
        val id = db.scraps().createRecording(ScrapEntity(format = ScrapFormat.RECORDING, createdAt = now, editedAt = now), take("a.wav"))
        val love = db.groups().insert(GroupEntity(name = "Love songs", createdAt = now))
        val dark = db.groups().insert(GroupEntity(name = "Dark riffs", createdAt = now))
        db.groups().addMember(GroupMemberEntity(love, id, now))
        db.groups().addMember(GroupMemberEntity(dark, id, now))
        db.groups().moveToTrash(dark, now + 1)
        val p1 = db.projects().insert(ProjectEntity(name = "Low Tide", createdAt = now, editedAt = now))
        db.projects().addRef(ProjectRefEntity(projectId = p1, scrapId = id, addedAt = now))
        val row = db.scraps().libraryRows().first().single()
        assertEquals(setOf(love), row.groupSet)
        assertEquals(1, row.projectUses)
        val groups = db.groups().liveRows().first()
        assertEquals(listOf("Love songs"), groups.map { it.group.name })
        assertEquals(1, groups.single().members)
    }

    @Test fun `version 1 migrates to 2 keeping takes and giving them zero pre-roll`() {
        val dir = Files.createTempDirectory("migr")
        val helper = MigrationTestHelper(
            schemaDirectoryPath = Paths.get("schemas"),
            databasePath = dir.resolve("m.db"),
            driver = BundledSQLiteDriver(),
            databaseClass = ApopheniaDatabase::class,
        )
        helper.createDatabase(1).apply {
            execSQL("INSERT INTO scrap (id, format, created_at, edited_at, show_in_library, free_time) VALUES (1, 'recording', 1, 1, 1, 0)")
            execSQL("INSERT INTO take (id, scrap_id, letter, starred, audio_path, duration_frames, sample_rate, created_at) VALUES (1, 1, 'A', 1, 'a.wav', 480, 48000, 1)")
            close()
        }
        val conn = helper.runMigrationsAndValidate(2)
        conn.prepare("SELECT letter, starred, preroll_frames FROM take WHERE id = 1").use { st ->
            assertTrue(st.step())
            assertEquals("A", st.getText(0))
            assertEquals(1L, st.getLong(1))
            assertEquals(0L, st.getLong(2))
        }
        conn.close()
    }

    @Test fun `schema version 2 is exported next to version 1`() {
        assertTrue(File("schemas/com.betterappmafia.apophenia.data.ApopheniaDatabase/2.json").isFile)
    }
}
