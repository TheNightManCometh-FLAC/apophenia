package com.betterappmafia.apophenia.data

/**
 * Search, filter and sort for the Library list (plan 3.6): OR within one property, AND across
 * properties; excluded tags remove a scrap whatever else matches. Pure, so it's tested on the JVM.
 * An empty set or null bound means "any".
 */
data class LibraryFilter(
    val formats: Set<ScrapFormat> = emptySet(),
    val includeTags: Set<String> = emptySet(),
    /** Include tags: false = any of them (the plan's OR), true = all of them. */
    val allIncludeTags: Boolean = false,
    val excludeTags: Set<String> = emptySet(),
    /** Key tokens, e.g. "D dorian". */
    val keys: Set<String> = emptySet(),
    /** With [keys] set, also keep scraps whose key isn't known. */
    val keepUnknownKey: Boolean = false,
    val bpmMin: Double? = null,
    val bpmMax: Double? = null,
    /** Also match half and double the BPM range (a 92 riff matches a 184 filter). */
    val halfDouble: Boolean = false,
    /** With a BPM range set, also keep free-time scraps. */
    val keepFreeTime: Boolean = false,
    val starredOnly: Boolean = false,
    /** Group ids: in any of them. */
    val groups: Set<Long> = emptySet(),
    /** Only scraps no project uses yet (plan 3.5: a Group's "unused only"). */
    val unusedOnly: Boolean = false,
) {
    val bpmActive: Boolean get() = bpmMin != null || bpmMax != null

    /** How many properties are narrowing the list, for the "FILTER · 3" label. */
    val activeCount: Int get() = listOf(formats.isNotEmpty(), includeTags.isNotEmpty(), excludeTags.isNotEmpty(), keys.isNotEmpty(), bpmActive, starredOnly, groups.isNotEmpty(), unusedOnly).count { it }
}

enum class LibrarySort { CAPTURED, EDITED, NAME, TEMPO }

object LibraryQuery {
    fun apply(rows: List<LibraryRow>, search: String, filter: LibraryFilter, sort: LibrarySort): List<LibraryRow> =
        sort(rows.filter { matchesSearch(it, search) && matches(it, filter) }, sort)

    /** Search: every word typed must appear in the title or one of the tags, ignoring case. */
    fun matchesSearch(row: LibraryRow, search: String): Boolean {
        val words = search.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val hay = buildString {
            append(row.scrap.title.orEmpty().lowercase())
            row.scrap.body?.let { append(' ').append(it.lowercase()) }
            row.tagList.forEach { append(' ').append(it.lowercase()) }
        }
        return words.all { it in hay }
    }

    fun matches(row: LibraryRow, f: LibraryFilter): Boolean {
        val s = row.scrap
        val tags = row.tagList.map { it.lowercase() }.toSet()
        if (f.formats.isNotEmpty() && s.format !in f.formats) return false
        if (f.excludeTags.any { it.lowercase() in tags }) return false
        if (f.includeTags.isNotEmpty()) {
            val want = f.includeTags.map { it.lowercase() }
            val ok = if (f.allIncludeTags) want.all { it in tags } else want.any { it in tags }
            if (!ok) return false
        }
        if (f.keys.isNotEmpty()) {
            val k = s.key
            if (k == null) { if (!f.keepUnknownKey) return false } else if (k !in f.keys) return false
        }
        if (f.bpmActive && !bpmMatches(s, f)) return false
        if (f.starredOnly && row.starred == 0) return false
        if (f.groups.isNotEmpty() && row.groupSet.none { it in f.groups }) return false
        if (f.unusedOnly && row.projectUses > 0) return false
        return true
    }

    private fun bpmMatches(s: ScrapEntity, f: LibraryFilter): Boolean {
        if (s.freeTime) return f.keepFreeTime
        val low = s.bpmLow ?: return false
        val high = s.bpmHigh ?: low
        val min = f.bpmMin ?: 0.0
        val max = f.bpmMax ?: Double.MAX_VALUE
        fun overlaps(factor: Double) = high * factor >= min && low * factor <= max
        return overlaps(1.0) || (f.halfDouble && (overlaps(0.5) || overlaps(2.0)))
    }

    fun sort(rows: List<LibraryRow>, sort: LibrarySort): List<LibraryRow> = when (sort) {
        LibrarySort.CAPTURED -> rows.sortedWith(compareByDescending<LibraryRow> { it.scrap.createdAt }.thenByDescending { it.scrap.id })
        LibrarySort.EDITED -> rows.sortedWith(compareByDescending<LibraryRow> { it.scrap.editedAt }.thenByDescending { it.scrap.id })
        // Untitled scraps sort after titled ones, never by a made-up name.
        LibrarySort.NAME -> rows.sortedWith(compareBy<LibraryRow> { it.scrap.title.isNullOrBlank() }.thenBy { it.scrap.title?.lowercase() }.thenByDescending { it.scrap.createdAt })
        // No tempo (unknown or free time) goes last, never given a fake value (plan 3.6).
        LibrarySort.TEMPO -> rows.sortedWith(compareBy<LibraryRow> { it.scrap.bpmLow == null }.thenBy { it.scrap.bpmLow }.thenByDescending { it.scrap.createdAt })
    }
}
