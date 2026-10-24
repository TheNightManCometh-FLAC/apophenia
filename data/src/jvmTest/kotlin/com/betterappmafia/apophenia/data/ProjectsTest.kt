package com.betterappmafia.apophenia.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectsTest {
    private lateinit var db: ApopheniaDatabase
    private val now = 1_800_000_000_000L

    @BeforeTest fun open() {
        db = Room.inMemoryDatabaseBuilder<ApopheniaDatabase>().setDriver(BundledSQLiteDriver()).build()
    }

    @AfterTest fun close() = db.close()

    private fun take(path: String) = TakeEntity(scrapId = 0, letter = "?", audioPath = path, durationFrames = 96_000, sampleRate = 48_000, createdAt = now)
    private fun scrap(title: String? = null, at: Long = now) = ScrapEntity(format = ScrapFormat.RECORDING, title = title, createdAt = at, editedAt = at)
    private suspend fun project(name: String, at: Long = now) = db.projects().insert(ProjectEntity(name = name, createdAt = at, editedAt = at))

    @Test fun `a recording made in a project is on its Board and still in the Library`() = runBlocking<Unit> {
        val p = project("Low Tide")
        val id = db.scraps().createRecording(scrap("Verse riff"), take("a.wav"), projectId = p)
        assertEquals(p, db.scraps().get(id)!!.projectId)
        assertEquals(listOf(id), db.projects().boardRows(p).first().map { it.scrap.id })
        assertEquals(listOf(id), db.scraps().libraryRows().first().map { it.scrap.id })
    }

    @Test fun `switching show in Library off hides a project recording from the Library but not from its Board`() = runBlocking<Unit> {
        val p = project("Low Tide")
        val id = db.scraps().createRecording(scrap(), take("a.wav"), projectId = p)
        db.scraps().setShowInLibrary(id, false)
        assertTrue(db.scraps().libraryRows().first().isEmpty())
        assertEquals(1, db.projects().boardRows(p).first().size)
        assertEquals(1, db.scraps().rows(listOf(id)).first().size)
    }

    @Test fun `adding scraps to a project counts only the new ones and never moves them out of another project`() = runBlocking<Unit> {
        val a = project("Low Tide")
        val b = project("Porch Light")
        val x = db.scraps().createRecording(scrap("x"), take("x.wav"))
        val y = db.scraps().createRecording(scrap("y"), take("y.wav"))
        assertEquals(1, db.projects().addRefs(a, listOf(x), now + 1))
        assertEquals(1, db.projects().addRefs(a, listOf(x, y), now + 2))
        assertEquals(2, db.projects().addRefs(b, listOf(x, y), now + 3))
        assertEquals(setOf(x, y), db.projects().boardRows(a).first().map { it.scrap.id }.toSet())
        assertEquals(setOf(x, y), db.projects().boardRows(b).first().map { it.scrap.id }.toSet())
        assertEquals(2, db.scraps().row(x).first()!!.projectUses)
        assertEquals(setOf(RefKey(a, x), RefKey(b, x)), db.projects().usesOf(listOf(x)).first().toSet())
    }

    @Test fun `a Library row names the projects using it, and taking a hidden recording off its Board brings it back to the Library`() = runBlocking<Unit> {
        val a = project("Low Tide")
        val b = project("Porch Light")
        val id = db.scraps().createRecording(scrap(), take("a.wav"), projectId = a)
        db.projects().addRefs(b, listOf(id), now + 1)
        assertEquals(listOf("Low Tide", "Porch Light"), db.scraps().row(id).first()!!.projectList)
        db.scraps().setShowInLibrary(id, false)
        db.projects().takeOff(b, id)
        assertTrue(db.scraps().libraryRows().first().isEmpty())   // still hidden: its own project still has it
        db.projects().takeOff(a, id)
        assertEquals(listOf(id), db.scraps().libraryRows().first().map { it.scrap.id })
        assertEquals(emptyList(), db.scraps().row(id).first()!!.projectList)
    }

    @Test fun `a window keeps the place it was moved to, and placing it changes nothing else`() = runBlocking<Unit> {
        val p = project("Low Tide")
        val id = db.scraps().createRecording(scrap(), take("a.wav"), projectId = p)
        assertFalse(db.projects().boardRows(p).first().single().placed)
        db.projects().saveSettings(db.projects().ref(p, id)!!.copy(gainDb = -3.0))
        db.projects().place(p, id, 8, 148, 172, 132, 4)
        val row = db.projects().boardRows(p).first().single()
        assertTrue(row.placed)
        assertEquals(listOf(8, 148, 172, 132, 4), listOf(row.x, row.y, row.w, row.h, row.z))
        assertEquals(-3.0, db.projects().ref(p, id)!!.gainDb)
    }

    @Test fun `the window glimpse shows the newest starred take, else the newest`() = runBlocking<Unit> {
        val p = project("Low Tide")
        val id = db.scraps().createRecording(scrap(), take("a.wav"), projectId = p)
        db.scraps().addTake(id, take("b.wav"), now + 1)
        assertEquals("b.wav", db.projects().boardRows(p).first().single().glimpsePath)
        db.takes().setStarred(db.takes().takesOf(id).first { it.letter == "A" }.id, true)
        assertEquals("a.wav", db.projects().boardRows(p).first().single().glimpsePath)
    }

    @Test fun `the switcher lists live projects most recently worked on first, and a trashed one comes back with its Board`() = runBlocking<Unit> {
        val a = project("Low Tide", now)
        val b = project("Porch Light", now + 10)
        val id = db.scraps().createRecording(scrap(), take("a.wav"), projectId = a)
        db.projects().touch(a, now + 20)
        assertEquals(listOf("Low Tide", "Porch Light"), db.projects().liveRows().first().map { it.project.name })
        assertEquals(1, db.projects().liveRows().first().first().scraps)
        db.projects().moveToTrash(a, now + 30)
        assertEquals(listOf("Porch Light"), db.projects().liveRows().first().map { it.project.name })
        assertEquals(listOf("Low Tide"), db.projects().trashFlow().first().map { it.name })
        assertEquals(0, db.scraps().row(id).first()!!.projectUses)
        db.projects().restore(a)
        assertEquals(listOf(id), db.projects().boardRows(a).first().map { it.scrap.id })
        db.projects().rename(b, "Porch Light (slow)", now + 40)
        assertEquals("Porch Light (slow)", db.projects().liveRows().first().first().project.name)
        assertNull(db.projects().trashFlow().first().firstOrNull())
    }

    @Test fun `leaving all Groups takes scraps out of live Groups only, and the memberships can be put back`() = runBlocking<Unit> {
        val id = db.scraps().createRecording(scrap(), take("a.wav"))
        val love = db.groups().insert(GroupEntity(name = "Love songs", createdAt = now))
        val old = db.groups().insert(GroupEntity(name = "Old", createdAt = now))
        db.groups().addMember(GroupMemberEntity(love, id, now))
        db.groups().addMember(GroupMemberEntity(old, id, now))
        db.groups().moveToTrash(old, now + 1)
        val kept = db.groups().liveMemberships(listOf(id))
        assertEquals(listOf(love), kept.map { it.groupId })
        assertEquals(1, db.groups().leaveAllLive(listOf(id)))
        assertTrue(db.scraps().row(id).first()!!.groupSet.isEmpty())
        db.groups().restore(old)
        assertEquals(setOf(old), db.scraps().row(id).first()!!.groupSet)
        kept.forEach { db.groups().addMember(it) }
        assertEquals(setOf(love, old), db.scraps().row(id).first()!!.groupSet)
    }

    private fun lyric(body: String, at: Long = now) = ScrapEntity(format = ScrapFormat.LYRIC, body = body, createdAt = at, editedAt = at)

    @Test fun `a lyric written in a project is on its Board, and the project edits the scrap itself`() = runBlocking<Unit> {
        val p = project("Low Tide")
        val id = db.scraps().createWritten(lyric("[Dm7]Salt on the window"), projectId = p)
        val row = db.projects().boardRows(p).first().single()
        assertNull(row.ownBody)
        assertEquals("[Dm7]Salt on the window", row.shownBody)
        assertEquals("[Dm7]Salt on the window", db.projects().bodyIn(p, id).first()!!.shown)
        db.scraps().setBody(id, "[Dm7]Salt on the glass", now + 5)
        assertEquals("[Dm7]Salt on the glass", db.projects().boardRows(p).first().single().shownBody)
        assertEquals(now + 5, db.scraps().get(id)!!.editedAt)
    }

    @Test fun `a lyric added to another project becomes that project's own copy, edited independently`() = runBlocking<Unit> {
        val a = project("Low Tide")
        val b = project("Porch Light")
        val id = db.scraps().createWritten(lyric("Salt on the window"), projectId = a)
        db.projects().addRefs(b, listOf(id), now + 1)
        assertEquals("Salt on the window", db.projects().bodyIn(b, id).first()!!.ownBody)
        db.projects().setOwnBody(b, id, "Rust on the railing")
        assertEquals("Rust on the railing", db.projects().bodyIn(b, id).first()!!.shown)
        assertEquals("Salt on the window", db.projects().bodyIn(a, id).first()!!.shown)
        assertEquals("Salt on the window", db.scraps().get(id)!!.body)
    }

    @Test fun `recordings are never copied into a project`() = runBlocking<Unit> {
        val p = project("Low Tide")
        val id = db.scraps().createRecording(scrap(), take("a.wav"))
        db.projects().addRefs(p, listOf(id), now)
        assertNull(db.projects().ref(p, id)!!.body)
    }

    @Test fun `Library search finds a lyric by its words`() = runBlocking<Unit> {
        db.scraps().createWritten(lyric("{c: Verse 1}\n[Dm7]Salt on the [G]window"))
        db.scraps().createRecording(scrap("Verse riff"), take("a.wav"))
        val rows = db.scraps().libraryRows().first()
        assertEquals(listOf(ScrapFormat.LYRIC), LibraryQuery.apply(rows, "salt window", LibraryFilter(), LibrarySort.CAPTURED).map { it.scrap.format })
    }

    @Test fun `version 3 migrates to 4 with no text on existing rows`() {
        val dir = Files.createTempDirectory("migr4")
        val helper = MigrationTestHelper(
            schemaDirectoryPath = Paths.get("schemas"),
            databasePath = dir.resolve("m.db"),
            driver = BundledSQLiteDriver(),
            databaseClass = ApopheniaDatabase::class,
        )
        helper.createDatabase(3).apply {
            execSQL("INSERT INTO scrap (id, format, title, created_at, edited_at, show_in_library, free_time) VALUES (1, 'recording', 'Riff', 1, 1, 1, 0)")
            close()
        }
        val conn = helper.runMigrationsAndValidate(4)
        conn.prepare("SELECT title, body FROM scrap WHERE id = 1").use { st ->
            assertTrue(st.step())
            assertEquals("Riff", st.getText(0))
            assertTrue(st.isNull(1))
        }
        conn.close()
    }

    @Test fun `version 2 migrates to 3 keeping project settings and leaving windows unplaced`() {
        val dir = Files.createTempDirectory("migr3")
        val helper = MigrationTestHelper(
            schemaDirectoryPath = Paths.get("schemas"),
            databasePath = dir.resolve("m.db"),
            driver = BundledSQLiteDriver(),
            databaseClass = ApopheniaDatabase::class,
        )
        helper.createDatabase(2).apply {
            execSQL("INSERT INTO project (id, name, created_at, edited_at) VALUES (1, 'Low Tide', 1, 1)")
            execSQL("INSERT INTO scrap (id, format, created_at, edited_at, show_in_library, free_time) VALUES (1, 'recording', 1, 1, 1, 0)")
            execSQL(
                "INSERT INTO project_ref (project_id, scrap_id, added_at, trim_start_frames, gain_db, fade_in_frames, fade_out_frames, conform, offset_frames) " +
                    "VALUES (1, 1, 1, 480, -6.0, 0, 0, 0, 0)"
            )
            close()
        }
        val conn = helper.runMigrationsAndValidate(3)
        conn.prepare("SELECT trim_start_frames, gain_db, board_x, board_z FROM project_ref").use { st ->
            assertTrue(st.step())
            assertEquals(480L, st.getLong(0))
            assertEquals(-6.0, st.getDouble(1))
            assertTrue(st.isNull(2))
            assertEquals(0L, st.getLong(3))
        }
        conn.close()
    }
}

class BoardLayoutTest {
    @Test fun `new windows fill two columns left to right, then the next row`() {
        val width = 384
        val w = BoardLayout.defaultWidth(width)
        assertEquals(180, w)
        val first = BoardLayout.nextFree(emptyList(), width)
        val second = BoardLayout.nextFree(listOf(first), width)
        val third = BoardLayout.nextFree(listOf(first, second), width)
        assertEquals(BoardRect(8, 8, 180, 132), first)
        assertEquals(BoardRect(196, 8, 180, 132), second)
        assertEquals(BoardRect(8, 148, 180, 132), third)
    }

    @Test fun `a new window never covers one the musician moved`() {
        val moved = BoardRect(100, 20, 200, 200)
        val r = BoardLayout.nextFree(listOf(moved), 384)
        assertFalse(r.overlaps(moved))
        assertEquals(BoardRect(8, 288, 180, 132), r)
    }

    @Test fun `a dragged window stays on the Board and never shrinks below the minimum`() {
        assertEquals(BoardRect(0, 0, BoardLayout.MIN_W, BoardLayout.MIN_H), BoardLayout.clamp(BoardRect(-40, -10, 20, 10), 384))
        assertEquals(BoardRect(184, 500, 200, 132), BoardLayout.clamp(BoardRect(300, 500, 200, 132), 384))
        assertEquals(384, BoardLayout.clamp(BoardRect(0, 0, 900, 132), 384).w)
    }
}
