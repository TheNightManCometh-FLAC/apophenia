package com.betterappmafia.apophenia.data

import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryQueryTest {
    private var nextId = 1L

    private fun row(
        title: String? = null, tags: List<String> = emptyList(), format: ScrapFormat = ScrapFormat.RECORDING,
        bpm: Double? = null, bpmHigh: Double? = bpm, free: Boolean = false, key: String? = null, starred: Int = 0,
        created: Long = nextId, edited: Long = created, groups: List<Long> = emptyList(), uses: Int = 0,
    ) = LibraryRow(
        ScrapEntity(id = nextId++, format = format, title = title, createdAt = created, editedAt = edited,
            bpmLow = bpm, bpmHigh = bpmHigh, freeTime = free, key = key),
        tags = tags.joinToString(TAG_SEPARATOR.toString()).ifEmpty { null }, takes = 1, starred = starred,
        groupIds = groups.joinToString(",").ifEmpty { null }, projectUses = uses,
    )

    private fun titles(rows: List<LibraryRow>) = rows.map { it.scrap.title }

    @Test fun `search needs every word somewhere in the title or the tags, ignoring case`() {
        val a = row("Verse riff", listOf("bass", "dark"))
        val b = row("Hum melody", listOf("vocal"))
        assertEquals(listOf(a), LibraryQuery.apply(listOf(a, b), "RIFF dark", LibraryFilter(), LibrarySort.CAPTURED))
        assertEquals(emptyList(), LibraryQuery.apply(listOf(a, b), "riff vocal", LibraryFilter(), LibrarySort.CAPTURED))
        assertEquals(2, LibraryQuery.apply(listOf(a, b), "   ", LibraryFilter(), LibrarySort.CAPTURED).size)
    }

    @Test fun `included tags match any of them unless all are asked for, and excluded tags always win`() {
        val bass = row("A", listOf("bass"))
        val bassDark = row("B", listOf("Bass", "dark"))
        val vocal = row("C", listOf("vocal", "bass"))
        val rows = listOf(bass, bassDark, vocal)
        assertEquals(listOf("A", "B", "C"), titles(rows.filter { LibraryQuery.matches(it, LibraryFilter(includeTags = setOf("bass", "dark"))) }))
        assertEquals(listOf("B"), titles(rows.filter { LibraryQuery.matches(it, LibraryFilter(includeTags = setOf("bass", "dark"), allIncludeTags = true)) }))
        assertEquals(listOf("A", "B"), titles(rows.filter { LibraryQuery.matches(it, LibraryFilter(includeTags = setOf("bass"), excludeTags = setOf("VOCAL"))) }))
    }

    @Test fun `different properties must all match`() {
        val a = row("A", listOf("bass"), key = "D dorian", bpm = 92.0)
        val b = row("B", listOf("bass"), key = "A aeolian", bpm = 92.0)
        val f = LibraryFilter(includeTags = setOf("bass"), keys = setOf("D dorian"))
        assertEquals(listOf("A"), titles(listOf(a, b).filter { LibraryQuery.matches(it, f) }))
    }

    @Test fun `an unknown key is kept only when asked for`() {
        val unknown = row("U")
        assertEquals(false, LibraryQuery.matches(unknown, LibraryFilter(keys = setOf("D dorian"))))
        assertEquals(true, LibraryQuery.matches(unknown, LibraryFilter(keys = setOf("D dorian"), keepUnknownKey = true)))
    }

    @Test fun `a BPM range matches overlapping ranges and, when asked, half and double time`() {
        val f = LibraryFilter(bpmMin = 88.0, bpmMax = 96.0)
        assertEquals(true, LibraryQuery.matches(row(bpm = 92.0), f))
        assertEquals(true, LibraryQuery.matches(row(bpm = 80.0, bpmHigh = 90.0), f))
        assertEquals(false, LibraryQuery.matches(row(bpm = 184.0), f))
        assertEquals(true, LibraryQuery.matches(row(bpm = 184.0), f.copy(halfDouble = true)))
        assertEquals(true, LibraryQuery.matches(row(bpm = 46.0), f.copy(halfDouble = true)))
        assertEquals(false, LibraryQuery.matches(row(), f))
    }

    @Test fun `free time is kept only when asked for, and never counts as a tempo`() {
        val free = row(free = true)
        assertEquals(false, LibraryQuery.matches(free, LibraryFilter(bpmMin = 0.0, bpmMax = 400.0)))
        assertEquals(true, LibraryQuery.matches(free, LibraryFilter(bpmMin = 88.0, bpmMax = 96.0, keepFreeTime = true)))
    }

    @Test fun `starred only keeps scraps with a starred take`() {
        assertEquals(false, LibraryQuery.matches(row(starred = 0), LibraryFilter(starredOnly = true)))
        assertEquals(true, LibraryQuery.matches(row(starred = 2), LibraryFilter(starredOnly = true)))
    }

    @Test fun `tempo sort puts scraps without a tempo last instead of giving them one`() {
        val none = row("none")
        val free = row("free", free = true)
        val fast = row("fast", bpm = 140.0)
        val slow = row("slow", bpm = 70.0)
        assertEquals(listOf("slow", "fast"), titles(LibraryQuery.sort(listOf(none, fast, free, slow), LibrarySort.TEMPO)).take(2))
        assertEquals(setOf("none", "free"), titles(LibraryQuery.sort(listOf(none, fast, free, slow), LibrarySort.TEMPO)).drop(2).toSet())
    }

    @Test fun `name sort puts untitled scraps after titled ones`() {
        val rows = listOf(row(null), row("beta"), row("Alpha"))
        assertEquals(listOf("Alpha", "beta", null), titles(LibraryQuery.sort(rows, LibrarySort.NAME)))
    }

    @Test fun `captured and edited sorts are newest first`() {
        val old = row("old", created = 10, edited = 50)
        val new = row("new", created = 20, edited = 30)
        assertEquals(listOf("new", "old"), titles(LibraryQuery.sort(listOf(old, new), LibrarySort.CAPTURED)))
        assertEquals(listOf("old", "new"), titles(LibraryQuery.sort(listOf(old, new), LibrarySort.EDITED)))
    }

    @Test fun `a Group filter keeps scraps in any of the chosen Groups`() {
        val a = row("A", groups = listOf(1, 2))
        val b = row("B", groups = listOf(3))
        val c = row("C")
        val f = LibraryFilter(groups = setOf(2, 3))
        assertEquals(listOf("A", "B"), titles(listOf(a, b, c).filter { LibraryQuery.matches(it, f) }))
    }

    @Test fun `unused only drops scraps a project already uses`() {
        assertEquals(true, LibraryQuery.matches(row(uses = 0), LibraryFilter(unusedOnly = true)))
        assertEquals(false, LibraryQuery.matches(row(uses = 2), LibraryFilter(unusedOnly = true)))
    }

    @Test fun `the filter counts each narrowing property once`() {
        assertEquals(0, LibraryFilter().activeCount)
        assertEquals(3, LibraryFilter(includeTags = setOf("a", "b"), bpmMin = 90.0, bpmMax = 100.0, starredOnly = true).activeCount)
    }
}
