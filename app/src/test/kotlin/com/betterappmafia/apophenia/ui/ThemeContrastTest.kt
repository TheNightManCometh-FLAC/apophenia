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

    @Test fun `every theme's dim labels read at least 3 to 1, and the kind tag reads on its accent`() {
        for (p in Palettes.ALL) {
            assertTrue("${p.label}: dim on bg is ${"%.2f".format(ratio(p.dim, p.bg))}:1", ratio(p.dim, p.bg) >= 3.0)
            assertTrue("${p.label}: bg on acc is ${"%.2f".format(ratio(p.bg, p.acc))}:1", ratio(p.bg, p.acc) >= 4.5)
        }
    }

    @Test fun `theme ids are unique and an unknown id falls back to Console`() {
        assertTrue(Palettes.ALL.map { it.id }.toSet().size == Palettes.ALL.size)
        assertTrue(Palettes.byId("gone") == Palettes.CONSOLE && Palettes.byId(null) == Palettes.CONSOLE)
    }
}
