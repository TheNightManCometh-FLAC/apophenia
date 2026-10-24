package com.betterappmafia.apophenia.core.degree

import com.betterappmafia.apophenia.core.interval.Interval
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DegreeTokenTest {

    @Test
    fun `each degree token maps to exactly one interval`() {
        assertEquals(Interval(7, 9), DegreeToken.parse("bb7").interval)
        assertEquals(Interval(11, 18), DegreeToken.parse("#11").interval)
        assertEquals(Interval(13, 20), DegreeToken.parse("b13").interval)
        assertEquals(Interval(3, 3), DegreeToken.parse("b3").interval)
        assertEquals(Interval(5, 6), DegreeToken.parse("b5").interval)
        assertEquals(Interval(4, 6), DegreeToken.parse("#4").interval)
        assertEquals(Interval(9, 16), DegreeToken.parse("x9").interval)
    }

    @Test
    fun `the vocabulary round trips through text`() {
        val tokens = "1 b2 2 #2 b3 3 4 #4 b5 5 #5 b6 6 bb7 b7 7 b9 9 #9 11 #11 b13 13 x4 bb3 15"
        assertEquals(tokens, DegreeToken.list(tokens).joinToString(" "))
    }

    @Test
    fun `an explicit natural is accepted and formats as the bare number`() {
        assertEquals("3", DegreeToken.parse("n3").toString())
    }

    @Test
    fun `a degree token rebuilt from its interval is the same token`() {
        for (t in DegreeToken.list("1 b2 #2 bb7 #11 b13 x4")) assertEquals(t, DegreeToken.of(t.interval))
    }

    @Test
    fun `malformed tokens are refused`() {
        for (s in listOf("", "b", "16", "0", "###3", "3b", "+9", "-9")) {
            assertFailsWith<IllegalArgumentException> { DegreeToken.parse(s) }
        }
    }
}
