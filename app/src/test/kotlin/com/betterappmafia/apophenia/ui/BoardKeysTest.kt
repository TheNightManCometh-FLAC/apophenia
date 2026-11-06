package com.betterappmafia.apophenia.ui

import com.betterappmafia.apophenia.data.BoardRow
import com.betterappmafia.apophenia.data.ScrapEntity
import com.betterappmafia.apophenia.data.ScrapFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class BoardKeysTest {
    private fun row(id: Long, key: String, added: Long, z: Int) = BoardRow(
        ScrapEntity(id = id, format = ScrapFormat.RECORDING, createdAt = 0, editedAt = 0, key = key),
        tags = null, takes = 1, starred = 0, addedAt = added, z = z,
    )

    @Test fun `bringing a window forward never changes which key paints the frame`() {
        val a = row(1, "A aeolian", added = 10, z = 0); val e = row(2, "E major", added = 20, z = 1)
        // The Board's query lists rows by which window is on top; the frame must not care.
        val before = boardKeys(listOf(a, e)).map { it.token }
        val after = boardKeys(listOf(e.copy(z = 0), a.copy(z = 1)).sortedBy { it.z }).map { it.token }
        assertEquals("one of each: the key that came onto the Board first wins the tie", before, after)
        assertEquals(before.first(), boardKeys(listOf(a)).first().token)
    }

    @Test fun `the commonest key wins over the first one`() {
        val rows = listOf(row(1, "A aeolian", 10, 0), row(2, "E major", 20, 0), row(3, "E major", 30, 0))
        assertEquals(boardKeys(listOf(rows[1])).first().token, boardKeys(rows).first().token)
    }
}
