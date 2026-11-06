package com.betterappmafia.apophenia.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.betterappmafia.apophenia.ui.theme.Palettes
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return (maxOf(la, lb) / minOf(la, lb)).toDouble()
    }

    @Test fun `every theme's text, accent and record colour read at WCAG AA on its grounds`() {
        for (p in Palettes.ALL) for ((ground, g) in listOf("bg" to p.bg, "panel" to p.panel, "panel2" to p.panel2)) {
            for ((name, c) in listOf("text" to p.text, "hi" to p.hi, "acc" to p.acc, "rec" to p.rec)) {
                val r = ratio(c, g)
                assertTrue("${p.label}: $name on $ground is ${"%.2f".format(r)}:1, needs 4.5", r >= 4.5)
            }
        }
    }

    /** A see-through panel as it is seen: laid over what's behind it. */
    private fun over(panel: Color, ground: Color) = Color(
        panel.red * panel.alpha + ground.red * (1 - panel.alpha),
        panel.green * panel.alpha + ground.green * (1 - panel.alpha),
        panel.blue * panel.alpha + ground.blue * (1 - panel.alpha),
    )

    @Test fun `Film's see-through panels keep their text readable over the painted frame, even over a light trail`() {
        val p = Palettes.FILM
        // The frame's quiet ground (FilmFrameTest keeps the upper part under 0.16 luma) and the core of a light trail.
        val ground = Color(0xFF2A2420); val trail = Color(0xFFFFF0D7)
        // PRACTICE's violet columns and STUDY's lit white keys (0.9.48) are as bright as a trail's core, in other colours.
        val violet = Color(0xFFFFE6FF); val key = Color(0xFFE4BE8F)
        for ((g, gname) in listOf(ground to "ground", trail to "a light trail", violet to "a violet column", key to "a sepia key")) for ((panel, pname) in listOf(p.panel to "panel", p.panel2 to "panel2")) {
            val seen = over(panel, g)
            for ((name, c) in listOf("text" to p.text, "hi" to p.hi)) {
                val r = ratio(c, seen)
                assertTrue("Film: $name on $pname over $gname is ${"%.2f".format(r)}:1, needs 4.5", r >= 4.5)
            }
        }
        for ((name, c) in listOf("acc" to p.acc, "rec" to p.rec)) {
            val r = ratio(c, over(p.panel, ground))
            assertTrue("Film: $name on panel over the ground is ${"%.2f".format(r)}:1, needs 4.5", r >= 4.5)
        }
    }

    @Test fun `Film's bare text reads on the brightest area the painted frame lets text sit on`() {
        // The grey of the frame's text ceiling (core FilmRooms.TEXT_CEILING, relative luminance). Record red (1.8:1 here) is for
        // panels and buttons (the strip, REC and RECORD), not bare text on the frame.
        val l = com.betterappmafia.apophenia.core.art.FilmRooms.TEXT_CEILING
        val v = (if (l <= 0.0031308) l * 12.92 else 1.055 * Math.pow(l, 1 / 2.4) - 0.055).toFloat()
        val ceiling = Color(v, v, v)
        val p = Palettes.FILM
        for ((name, c, need) in listOf(Triple("text", p.text, 4.5), Triple("hi", p.hi, 4.5), Triple("dim", p.dim, 3.0), Triple("acc", p.acc, 3.0))) {
            val r = ratio(c, ceiling)
            assertTrue("Film: bare $name over the ceiling is ${"%.2f".format(r)}:1, needs $need", r >= need)
        }
    }

    @Test fun `every theme's dim labels read at least 3 to 1, and the kind tag reads on its accent`() {
        for (p in Palettes.ALL) {
            assertTrue("${p.label}: dim on bg is ${"%.2f".format(ratio(p.dim, p.bg))}:1", ratio(p.dim, p.bg) >= 3.0)
            assertTrue("${p.label}: bg on acc is ${"%.2f".format(ratio(p.bg, p.acc))}:1", ratio(p.bg, p.acc) >= 4.5)
        }
    }

    @Test fun `theme ids are unique and an unknown id falls back to Film, the default since 0_9_38`() {
        assertTrue(Palettes.ALL.map { it.id }.toSet().size == Palettes.ALL.size)
        assertTrue(Palettes.byId("gone") == Palettes.FILM && Palettes.byId(null) == Palettes.FILM)
        assertTrue(Palettes.byId("console") == Palettes.CONSOLE)
    }
}
