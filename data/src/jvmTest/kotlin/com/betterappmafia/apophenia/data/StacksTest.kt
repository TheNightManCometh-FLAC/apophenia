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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StacksTest {
    private lateinit var db: ApopheniaDatabase
    private val now = 1_800_000_000_000L

    @BeforeTest fun open() {
        db = Room.inMemoryDatabaseBuilder<ApopheniaDatabase>().setDriver(BundledSQLiteDriver()).build()
    }

    @AfterTest fun close() = db.close()

    private fun take(path: String) = TakeEntity(scrapId = 0, letter = "?", audioPath = path, durationFrames = 96_000, sampleRate = 48_000, createdAt = now)
    private suspend fun recording(title: String) = db.scraps().createRecording(ScrapEntity(format = ScrapFormat.RECORDING, title = title, createdAt = now, editedAt = now), take("$title.wav"))
    private suspend fun project(name: String) = db.projects().insert(ProjectEntity(name = name, createdAt = now, editedAt = now))

    @Test fun `a project gets one stack, made the first time it's asked for, and the temporary stack is separate`() = runBlocking<Unit> {
        val p = project("Low Tide")
        val a = db.stacks().ofProjectOrNew(p, now)
        assertEquals(a, db.stacks().ofProjectOrNew(p, now + 1))
        assertEquals("Stack 1", a.name)
        val t = db.stacks().ofProjectOrNew(null, now)
        assertNull(t.projectId)
        assertTrue(t.id != a.id)
    }

    @Test fun `layers list top to bottom with the take that plays, the starred one unless one was chosen`() = runBlocking<Unit> {
        val s = db.stacks().ofProjectOrNew(project("Low Tide"), now)
        val riff = recording("riff")
        val pad = recording("pad")
        db.scraps().addTake(riff, take("riff-b.wav"), now + 1)
        db.stacks().addLayer(StackLayerEntity(stackId = s.id, scrapId = riff, addedAt = 0), now)
        db.stacks().addLayer(StackLayerEntity(stackId = s.id, scrapId = pad, mode = LayerMode.FREE, addedAt = 0), now)
        var rows = db.stacks().layers(s.id).first()
        assertEquals(listOf("riff", "pad"), rows.map { it.title })
        assertEquals(listOf(0, 1), rows.map { it.layer.position })
        assertEquals("riff-b.wav", rows[0].audioPath)       // newest when none is starred
        val a = db.takes().takesOf(riff).first { it.letter == "A" }
        db.takes().setStarred(a.id, true)
        assertEquals("riff.wav", db.stacks().layers(s.id).first()[0].audioPath)
        val b = db.takes().takesOf(riff).first { it.letter == "B" }
        db.stacks().updateLayer(rows[0].layer.copy(takeId = b.id), now)
        rows = db.stacks().layers(s.id).first()
        assertEquals("B", rows[0].takeLetter)
        db.takes().moveToTrash(b.id, now)                     // a trashed chosen take falls back
        assertEquals("A", db.stacks().layers(s.id).first()[0].takeLetter)
    }

    @Test fun `a layer's settings are kept safe and a trashed recording drops out of the stack`() = runBlocking<Unit> {
        val s = db.stacks().ofProjectOrNew(null, now)
        val riff = recording("riff")
        val id = db.stacks().addLayer(StackLayerEntity(stackId = s.id, scrapId = riff, gainDb = Double.NaN, fromFrames = 500, toFrames = 100, addedAt = 0), now)
        val l = db.stacks().layer(id)!!
        assertEquals(0.0, l.gainDb)
        assertNull(l.toFrames)
        db.stacks().updateLayer(l.copy(gainDb = 40.0), now)
        assertEquals(12.0, db.stacks().layer(id)!!.gainDb)
        assertEquals(listOf(riff), db.stacks().scrapIds(s.id).first())
        db.scraps().moveToTrash(riff, now)
        assertTrue(db.stacks().layers(s.id).first().isEmpty())
    }

    @Test fun `a Board knows which recordings are in its project's stack, and deleting the project deletes its stack`() = runBlocking<Unit> {
        val p = project("Low Tide")
        val riff = recording("riff")
        assertTrue(db.stacks().scrapIdsInProjectStack(p).first().isEmpty())
        val s = db.stacks().ofProjectOrNew(p, now)
        db.stacks().addLayer(StackLayerEntity(stackId = s.id, scrapId = riff, addedAt = 0), now)
        db.stacks().addLayer(StackLayerEntity(stackId = s.id, scrapId = riff, mode = LayerMode.ONE_SHOT, addedAt = 0), now)
        assertEquals(listOf(riff), db.stacks().scrapIdsInProjectStack(p).first())
        db.projects().moveToTrash(p, now)
        db.projects().purgeExpired(now)
        assertNull(db.stacks().get(s.id))
        assertEquals(1, db.scraps().libraryRows().first().size)   // the recording itself stays
    }

    @Test fun `a lyric synced to a take keeps a frame per line, and the playing line is the last one started`() = runBlocking<Unit> {
        val lyric = db.scraps().createWritten(ScrapEntity(format = ScrapFormat.LYRIC, body = "a\nb\nc", createdAt = now, editedAt = now))
        val rec = recording("vocal")
        val take = db.takes().takesOf(rec).single()
        db.lyricSyncs().put(LyricSyncEntity.of(lyric, take.id, listOf(0L, 96_000L, null), now))
        val sync = db.lyricSyncs().of(lyric).first().single()
        assertEquals(listOf(0L, 96_000L, null), sync.lineFrames)
        assertEquals(-1, LyricSyncEntity.lineAt(listOf(10L, 20L), 5))
        assertEquals(0, LyricSyncEntity.lineAt(sync.lineFrames, 50_000))
        assertEquals(1, LyricSyncEntity.lineAt(sync.lineFrames, 200_000))
        db.takes().moveToTrash(take.id, now)
        db.takes().purgeExpired(now)
        assertTrue(db.lyricSyncs().of(lyric).first().isEmpty())
    }

    @Test fun `a take keeps its marks and loops in order, and they go when the take is deleted`() = runBlocking<Unit> {
        val rec = recording("song")
        val take = db.takes().takesOf(rec).single()
        db.marks().add(TakeMarkEntity(takeId = take.id, frame = 96_000, label = "  chorus  ", createdAt = now))
        db.marks().add(TakeMarkEntity(takeId = take.id, frame = 48_000, endFrame = 72_000, createdAt = now))
        db.marks().add(TakeMarkEntity(takeId = take.id, frame = -5, endFrame = -10, createdAt = now))
        val marks = db.marks().of(take.id).first()
        assertEquals(listOf(0L, 48_000L, 96_000L), marks.map { it.frame })
        assertEquals(listOf(false, true, false), marks.map { it.isLoop })
        assertEquals("chorus", marks[2].label)
        assertEquals(take.id, db.marks().takeByPath("song.wav")?.id)
        db.takes().moveToTrash(take.id, now)
        db.takes().purgeExpired(now)
        assertTrue(db.marks().of(take.id).first().isEmpty())
    }

    @Test fun `practice is kept with what it was on, mis-taps aren't, and deleting the recording keeps the time`() = runBlocking<Unit> {
        val rec = recording("bass line")
        val p = project("Low Tide")
        assertNull(db.practice().add(PracticeEntity(startedAt = now, seconds = 3)))
        db.practice().add(PracticeEntity(startedAt = now, seconds = 600, scrapId = rec, note = "  slow at 0.6  "))
        db.practice().add(PracticeEntity(startedAt = now + 1000, seconds = 300, projectId = p))
        db.practice().add(PracticeEntity(startedAt = now + 2000, seconds = 120, exercise = " Paradiddles "))
        val h = db.practice().history().first()
        assertEquals(listOf("Paradiddles", "Low Tide", "bass line"), h.map { it.what })
        assertEquals("slow at 0.6", h[2].practice.note)
        assertEquals(1020L, db.practice().secondsSince(now).first())
        assertEquals(600L, db.practice().secondsOnScrap(rec).first())
        db.scraps().moveToTrash(rec, now)
        db.scraps().purgeExpired(now + 31L * 24 * 3600 * 1000)
        val after = db.practice().history().first()
        assertEquals(3, after.size)
        assertNull(after[2].practice.scrapId)
    }

    @Test fun `version 8 migrates to 9 with an empty practice log`() {
        val dir = Files.createTempDirectory("migr9")
        val helper = MigrationTestHelper(
            schemaDirectoryPath = Paths.get("schemas"),
            databasePath = dir.resolve("m.db"),
            driver = BundledSQLiteDriver(),
            databaseClass = ApopheniaDatabase::class,
        )
        helper.createDatabase(8).apply { close() }
        val conn = helper.runMigrationsAndValidate(9)
        conn.prepare("SELECT COUNT(*) FROM practice").use { st -> assertTrue(st.step()); assertEquals(0L, st.getLong(0)) }
        conn.close()
    }

    @Test fun `version 7 migrates to 8 with an empty marks table`() {
        val dir = Files.createTempDirectory("migr8")
        val helper = MigrationTestHelper(
            schemaDirectoryPath = Paths.get("schemas"),
            databasePath = dir.resolve("m.db"),
            driver = BundledSQLiteDriver(),
            databaseClass = ApopheniaDatabase::class,
        )
        helper.createDatabase(7).apply {
            execSQL("INSERT INTO scrap (id, format, created_at, edited_at, show_in_library, free_time) VALUES (1, 'recording', 1, 1, 1, 0)")
            close()
        }
        val conn = helper.runMigrationsAndValidate(8)
        conn.prepare("SELECT COUNT(*) FROM take_mark").use { st -> assertTrue(st.step()); assertEquals(0L, st.getLong(0)) }
        conn.prepare("SELECT COUNT(*) FROM scrap").use { st -> assertTrue(st.step()); assertEquals(1L, st.getLong(0)) }
        conn.close()
    }

    @Test fun `version 5 migrates to 6 with conform on for existing layers`() {
        val dir = Files.createTempDirectory("migr6")
        val helper = MigrationTestHelper(
            schemaDirectoryPath = Paths.get("schemas"),
            databasePath = dir.resolve("m.db"),
            driver = BundledSQLiteDriver(),
            databaseClass = ApopheniaDatabase::class,
        )
        helper.createDatabase(5).apply {
            execSQL("INSERT INTO scrap (id, format, created_at, edited_at, show_in_library, free_time) VALUES (1, 'recording', 1, 1, 1, 0)")
            execSQL("INSERT INTO stack (id, name, created_at, edited_at) VALUES (1, 'Temporary stack', 1, 1)")
            execSQL("INSERT INTO stack_layer (id, stack_id, scrap_id, mode, start_frames, from_frames, gain_db, muted, solo, position, added_at) VALUES (1, 1, 1, 'loop', 0, 0, 0, 0, 0, 0, 1)")
            close()
        }
        val conn = helper.runMigrationsAndValidate(6)
        conn.prepare("SELECT conform FROM stack_layer WHERE id = 1").use { st -> assertTrue(st.step()); assertEquals(1L, st.getLong(0)) }
        conn.close()
    }

    @Test fun `version 4 migrates to 5 with the stack tables empty`() {
        val dir = Files.createTempDirectory("migr5")
        val helper = MigrationTestHelper(
            schemaDirectoryPath = Paths.get("schemas"),
            databasePath = dir.resolve("m.db"),
            driver = BundledSQLiteDriver(),
            databaseClass = ApopheniaDatabase::class,
        )
        helper.createDatabase(4).apply {
            execSQL("INSERT INTO project (id, name, created_at, edited_at) VALUES (1, 'Low Tide', 1, 1)")
            close()
        }
        val conn = helper.runMigrationsAndValidate(5)
        conn.prepare("SELECT COUNT(*) FROM stack_layer").use { st -> assertTrue(st.step()); assertEquals(0L, st.getLong(0)) }
        conn.prepare("SELECT name FROM project").use { st -> assertTrue(st.step()); assertEquals("Low Tide", st.getText(0)) }
        conn.close()
    }

    @Test fun `analysis is kept per take, replaced when run again, and pending lists what still needs it`() = runBlocking<Unit> {
        val riff = recording("riff")
        val a = db.takes().takesOf(riff).single()
        assertEquals(listOf(a.id), db.analysis().pending(version = 1).map { it.id })
        db.analysis().put(TakeAnalysisEntity(a.id, 1, now, "v1"))
        assertEquals(emptyList<Long>(), db.analysis().pending(version = 1).map { it.id })
        assertEquals(listOf(a.id), db.analysis().pending(version = 2).map { it.id })   // a newer analysis runs again
        db.analysis().put(TakeAnalysisEntity(a.id, 2, now + 1, "v2"))
        assertEquals("v2", db.analysis().get(a.id)!!.body)
        db.takes().moveToTrash(a.id, now)
        assertEquals(emptyList<Long>(), db.analysis().pending(version = 3).map { it.id })  // trashed takes wait
    }

    @Test fun `version 9 migrates to 10 with no analysis yet`() {
        val dir = Files.createTempDirectory("migr10")
        val helper = MigrationTestHelper(
            schemaDirectoryPath = Paths.get("schemas"),
            databasePath = dir.resolve("m.db"),
            driver = BundledSQLiteDriver(),
            databaseClass = ApopheniaDatabase::class,
        )
        helper.createDatabase(9).apply { close() }
        val conn = helper.runMigrationsAndValidate(10)
        conn.prepare("SELECT COUNT(*) FROM take_analysis").use { st -> assertTrue(st.step()); assertEquals(0L, st.getLong(0)) }
        conn.close()
    }

    @Test fun `a transcription belongs to its take and goes when the take is deleted for good`() = runBlocking<Unit> {
        val riff = recording("riff")
        val a = db.takes().takesOf(riff).single()
        db.transcriptions().put(TakeTranscriptionEntity(a.id, "basic-pitch-icassp2022", now, "notes"))
        assertEquals("basic-pitch-icassp2022", db.transcriptions().get(a.id)!!.model)
        db.transcriptions().put(TakeTranscriptionEntity(a.id, "basic-pitch-icassp2022", now + 1, "again"))
        assertEquals("again", db.transcriptions().get(a.id)!!.body)
        db.takes().moveToTrash(a.id, now)
        db.takes().purgeExpired(now)                       // emptied from the trash
        assertNull(db.transcriptions().get(a.id))
    }

    @Test fun `version 10 migrates to 11 with no transcriptions yet`() {
        val dir = Files.createTempDirectory("migr11")
        val helper = MigrationTestHelper(
            schemaDirectoryPath = Paths.get("schemas"),
            databasePath = dir.resolve("m.db"),
            driver = BundledSQLiteDriver(),
            databaseClass = ApopheniaDatabase::class,
        )
        helper.createDatabase(10).apply { close() }
        val conn = helper.runMigrationsAndValidate(11)
        conn.prepare("SELECT COUNT(*) FROM take_transcription").use { st -> assertTrue(st.step()); assertEquals(0L, st.getLong(0)) }
        conn.close()
    }

    @Test fun `a stem is its own item linked to its source, and the link goes with either take`() = runBlocking<Unit> {
        val song = recording("song")
        val src = db.takes().takesOf(song).single()
        val bass = recording("song · bass")
        val bassTake = db.takes().takesOf(bass).single()
        db.stems().put(TakeStemEntity(src.id, "bass", bassTake.id, "spleeter-4stems", now))
        val rows = db.stems().of(src.id).first()
        assertEquals(listOf("bass"), rows.map { it.stem })
        assertEquals(bass, rows.single().scrapId)
        assertEquals(src.id, db.stems().sourceOf(bassTake.id)!!.sourceTakeId)
        // The stem's item in the trash drops out of the tab; emptied, the link goes too.
        db.scraps().moveToTrash(bass, now)
        assertTrue(db.stems().of(src.id).first().isEmpty())
        db.scraps().purgeExpired(now)
        assertNull(db.stems().sourceOf(bassTake.id))
    }

    @Test fun `version 11 migrates to 12 with no stems yet`() {
        val dir = Files.createTempDirectory("migr12")
        val helper = MigrationTestHelper(
            schemaDirectoryPath = Paths.get("schemas"),
            databasePath = dir.resolve("m.db"),
            driver = BundledSQLiteDriver(),
            databaseClass = ApopheniaDatabase::class,
        )
        helper.createDatabase(11).apply { close() }
        val conn = helper.runMigrationsAndValidate(12)
        conn.prepare("SELECT COUNT(*) FROM take_stem").use { st -> assertTrue(st.step()); assertEquals(0L, st.getLong(0)) }
        conn.close()
    }

    @Test fun `a take's words are kept with it, replaced when read again, and go when it is deleted for good`() = runBlocking<Unit> {
        val memo = recording("memo")
        val a = db.takes().takesOf(memo).single()
        db.speech().put(TakeSpeechEntity(a.id, "moonshine-base", now, "first"))
        db.speech().put(TakeSpeechEntity(a.id, "moonshine-base", now + 1, "again"))
        assertEquals("again", db.speech().get(a.id)!!.body)
        assertEquals(listOf(a.id), db.speech().recent().first().map { it.takeId })
        db.takes().moveToTrash(a.id, now)
        db.takes().purgeExpired(now)
        assertNull(db.speech().get(a.id))
    }

    @Test fun `version 12 migrates to 13 with no transcripts yet`() {
        val dir = Files.createTempDirectory("migr13")
        val helper = MigrationTestHelper(
            schemaDirectoryPath = Paths.get("schemas"),
            databasePath = dir.resolve("m.db"),
            driver = BundledSQLiteDriver(),
            databaseClass = ApopheniaDatabase::class,
        )
        helper.createDatabase(12).apply { close() }
        val conn = helper.runMigrationsAndValidate(13)
        conn.prepare("SELECT COUNT(*) FROM take_speech").use { st -> assertTrue(st.step()); assertEquals(0L, st.getLong(0)) }
        conn.close()
    }

    @Test fun `version 13 migrates to 14 with no ear results yet`() {
        val dir = Files.createTempDirectory("migr14")
        val helper = MigrationTestHelper(
            schemaDirectoryPath = Paths.get("schemas"),
            databasePath = dir.resolve("m.db"),
            driver = BundledSQLiteDriver(),
            databaseClass = ApopheniaDatabase::class,
        )
        helper.createDatabase(13).apply { close() }
        val conn = helper.runMigrationsAndValidate(14)
        conn.prepare("SELECT COUNT(*) FROM ear_result").use { st -> assertTrue(st.step()); assertEquals(0L, st.getLong(0)) }
        conn.close()
    }

    @Test fun `ear sessions keep the best test score per drill and skip empty sessions`() = runBlocking<Unit> {
        val ear = db.earResults()
        assertNull(ear.add(EarResultEntity(drill = "iv-simple", mode = "practice", correct = 0, total = 0, bestStreak = 0, finishedAt = now)))
        ear.add(EarResultEntity(drill = "iv-simple", mode = "test", correct = 14, total = 20, bestStreak = 5, finishedAt = now))
        ear.add(EarResultEntity(drill = "iv-simple", mode = "test", correct = 18, total = 20, bestStreak = 9, finishedAt = now + 1))
        ear.add(EarResultEntity(drill = "iv-simple", mode = "practice", correct = 30, total = 30, bestStreak = 30, finishedAt = now + 2))
        val best = ear.bests().first().single()
        assertEquals(18 to 20, best.bestCorrect to best.bestTotal)
        assertEquals(3, best.sessions)
    }

    @Test fun `a drill's high score is its longest streak in any session`() = runBlocking<Unit> {
        val ear = db.earResults()
        ear.add(EarResultEntity(drill = "iv-simple", mode = "practice", correct = 12, total = 20, bestStreak = 7, finishedAt = now))
        ear.add(EarResultEntity(drill = "iv-simple", mode = "practice", correct = 25, total = 26, bestStreak = 19, finishedAt = now + 5))
        ear.add(EarResultEntity(drill = "iv-simple", mode = "practice", correct = 3, total = 9, bestStreak = 2, finishedAt = now + 9))
        ear.add(EarResultEntity(drill = "ch-triads", mode = "practice", correct = 4, total = 4, bestStreak = 4, finishedAt = now + 1))
        val s = ear.streaks().first().associateBy { it.drill }
        assertEquals(19, s.getValue("iv-simple").best, "the best run, not the last session's")
        assertEquals(3, s.getValue("iv-simple").sessions)
        assertEquals(now + 9, s.getValue("iv-simple").last)
        assertEquals(4, s.getValue("ch-triads").best)
    }
}
