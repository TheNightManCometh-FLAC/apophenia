package com.betterappmafia.apophenia.core.sheet

import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricSheetTest {
    @Test fun `a chord in brackets sits over the words after it`() {
        val line = LyricSheet.parse("[Dm7]Salt on the [G]window, the").single() as LyricSheet.Line.Words
        assertEquals("Salt on the window, the", line.text)
        assertEquals(listOf("Dm7", "G"), line.chords)
        assertEquals("Dm7         G" to "Salt on the window, the", LyricSheet.chordsOverWords(line))
    }

    @Test fun `words before the first chord have no chord over them`() {
        val line = LyricSheet.parse("I left the [C]porch light on").single() as LyricSheet.Line.Words
        assertEquals("           C" to "I left the porch light on", LyricSheet.chordsOverWords(line))
    }

    @Test fun `a chord wider than its words pushes the next words along, never touching the next chord`() {
        val line = LyricSheet.parse("[Cmaj7]Oh [G]no").single() as LyricSheet.Line.Words
        val (top, bottom) = LyricSheet.chordsOverWords(line)
        assertEquals("Cmaj7 G", top)
        assertEquals("Oh    no", bottom)
        assertEquals(top.indexOf('G'), bottom.indexOf("no"))
    }

    @Test fun `a chord inside a word that has to be pushed gets a hyphen`() {
        val line = LyricSheet.parse("[Ebmaj7]Wa[Bb]ter").single() as LyricSheet.Line.Words
        val (top, bottom) = LyricSheet.chordsOverWords(line)
        assertEquals("E♭maj7 B♭", top)
        assertEquals("Wa-    ter", bottom)
        assertEquals(top.indexOf('B'), bottom.indexOf("ter"))
    }

    @Test fun `comments and section directives are headings, other directives are not lines`() {
        val lines = LyricSheet.parse("{title: Low Tide}\n{c: Verse 1}\nSalt\n\n{soc}\n[F]Low tide\n{eoc}")
        assertEquals(
            listOf(
                LyricSheet.Line.Section("Verse 1"), LyricSheet.Line.Words(listOf(LyricSheet.Part(null, "Salt"))), LyricSheet.Line.Blank,
                LyricSheet.Line.Section("Chorus"), LyricSheet.Line.Words(listOf(LyricSheet.Part("F", "Low tide"))),
            ),
            lines,
        )
    }

    @Test fun `an unclosed bracket is just words`() {
        val line = LyricSheet.parse("tide [keeps the time").single() as LyricSheet.Line.Words
        assertEquals("tide [keeps the time", line.text)
        assertTrue(line.chords.isEmpty())
    }

    @Test fun `the first words skip headings and chords`() {
        assertEquals("Salt on the window, the", LyricSheet.firstWords("{c: Verse 1}\n\n[Dm7]Salt on the [G]window, the"))
        assertEquals("Verse 1\nSalt", LyricSheet.plainText("{c: Verse 1}\n[Dm7]Salt"))
    }

    @Test fun `a board square shows the chords over the words and doesn't repeat its title`() {
        val sheet = "Down by the water\n[G]Salt on the [D]window"
        assertEquals("G           D\nSalt on the window", LyricSheet.glimpse(sheet, titled = false) { it })
        assertEquals("Down by the water\nG           D\nSalt on the window", LyricSheet.glimpse(sheet, titled = true) { it })
        // A first line with chords over it stays: the chords are what the title doesn't show.
        assertEquals("Am\nDown by the water", LyricSheet.glimpse("[Am]Down by the water", titled = false) { it })
    }
}

class ChordTextTest {
    @Test fun `sharps and flats are drawn as accidentals only where they are accidentals`() {
        assertEquals("F♯m7", ChordText.display("F#m7"))
        assertEquals("B♭", ChordText.display("Bb"))
        assertEquals("E7♭9", ChordText.display("E7b9"))
        assertEquals("C/B♭", ChordText.display("C/Bb"))
        assertEquals("Dm7♭5", ChordText.display("Dm7b5"))
        assertEquals("E7♯9", ChordText.display("E7#9"))
        assertEquals("Csus4", ChordText.display("Csus4"))
        assertEquals("B♭dim", ChordText.display("Bbdim"))
        assertEquals("Cadd9", ChordText.display("Cadd9"))
    }
}

class ChordChartTest {
    @Test fun `bars between bars lines, one token per beat, slash holds the chord`() {
        val chart = ChordChart.parse("{c: Chorus}\n| F / C / | G / Dm7 / |")
        assertEquals(1, chart.sections.size)
        assertEquals("Chorus", chart.sections[0].label)
        assertEquals(listOf(listOf("F", "/", "C", "/"), listOf("G", "/", "Dm7", "/")), chart.bars.map { it.beats })
        assertEquals(listOf("F", "F", "C", "C"), chart.bars[0].sounding(null))
        assertTrue(chart.problems.isEmpty())
    }

    @Test fun `a bar with the wrong number of beats for its meter is reported, not changed`() {
        val chart = ChordChart.parse("| F / C | G / Dm7 / |")
        assertEquals(listOf("Bar 1 has 3 beats; 4/4 has 4."), chart.problems)
        assertEquals(listOf("F", "/", "C"), chart.bars[0].beats)
    }

    @Test fun `six-eight has two beats a bar and the meter holds from its line on`() {
        val chart = ChordChart.parse("| C / C / |\n{time: 6/8}\n| Am / | F G |")
        assertEquals(listOf(TimeSignature.of(4, 4), TimeSignature.of(6, 8), TimeSignature.of(6, 8)), chart.bars.map { it.meter })
        assertTrue(chart.problems.isEmpty())
    }

    @Test fun `percent repeats the bar before it, a slash at the start holds the last chord of the bar before`() {
        val chart = ChordChart.parse("| Am / / / | % | / / G / |")
        assertEquals(listOf("Am", "/", "/", "/"), chart.bars[1].beats)
        assertEquals(listOf("Am", "Am", "G", "G"), chart.bars[2].sounding("Am"))
    }

    @Test fun `a meter that can't be read is reported and the old one kept`() {
        val chart = ChordChart.parse("{time: 7/8}\n| A / / / |")
        assertEquals(1, chart.problems.size)
        assertEquals(TimeSignature.of(4, 4), chart.bars.single().meter)
    }

    @Test fun `seven-eight written as groups has three beats`() {
        val chart = ChordChart.parse("{time: 2+2+3/8}\n| D / E |")
        assertTrue(chart.problems.isEmpty())
        assertEquals("| D / E |", ChordChart.preview("{time: 2+2+3/8}\n| D / E |"))
    }
}
