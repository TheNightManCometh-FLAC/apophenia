package com.betterappmafia.apophenia.core.art

import com.betterappmafia.apophenia.core.art.FilmFrame.Input
import com.betterappmafia.apophenia.core.art.FilmFrame.Kind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class FilmFrameTest {
    private val w = 270
    private val h = 579

    /** A take's loudness: a riff with a loud start, a dip, and a long tail. */
    private fun envelope(n: Int = 96, phase: Double = 0.0) = FloatArray(n) { i ->
        val t = i / (n - 1.0)
        (0.35 + 0.45 * sin(t * 9 + phase) * sin(t * 3.1 + 1) + 0.25 * (1 - t)).coerceIn(0.05, 1.0).toFloat()
    }

    private fun project(pc: Int? = 9, minor: Boolean = true, bpm: Double? = 92.0, trails: List<FloatArray> = listOf(envelope(), envelope(phase = 1.0), envelope(phase = 2.0)), seed: Long = 7) =
        Input(Kind.PROJECT, seed, pc, minor, bpm, trails, starred = trails.lastIndex)

    private fun hueOf(px: IntArray): Double {
        var r = 0.0; var g = 0.0; var b = 0.0
        // The upper half: ground and haze only, no trails.
        for (y in 0 until h / 2) for (x in 0 until w) { val p = px[y * w + x]; r += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255 }
        return Math.toDegrees(Math.atan2(Math.sqrt(3.0) * (g - b), 2 * r - g - b)).let { if (it < 0) it + 360 else it }
    }

    @Test fun `the same project always paints the same frame`() {
        assertArrayEquals(FilmFrame.paint(w, h, project()), FilmFrame.paint(w, h, project()))
        assertEquals(project().signature(), project().signature())
    }

    @Test fun `a different key gives the frame a different colour`() {
        val a = hueOf(FilmFrame.paint(w, h, project(pc = 9)))
        val d = hueOf(FilmFrame.paint(w, h, project(pc = 2)))
        val diff = Math.abs(a - d).let { if (it > 180) 360 - it else it }
        assertTrue("A and D should sit well apart on the colour wheel, were $a and $d", diff > 40)
        assertNotEquals(project(pc = 9).signature(), project(pc = 2).signature())
    }

    @Test fun `a minor key is darker than its major`() {
        val minor = FilmFrame.paint(w, h, project(minor = true))
        val major = FilmFrame.paint(w, h, project(minor = false))
        assertTrue(FilmFrame.meanLuma(minor, w, 0, 0, w, h / 2) < FilmFrame.meanLuma(major, w, 0, 0, w, h / 2))
    }

    @Test fun `each take adds its own trail of light in the lower part`() {
        val none = FilmFrame.paint(w, h, project(trails = emptyList()))
        val three = FilmFrame.paint(w, h, project())
        val lowNone = FilmFrame.meanLuma(none, w, 0, (h * 0.62).toInt(), w, (h * 0.86).toInt())
        val lowThree = FilmFrame.meanLuma(three, w, 0, (h * 0.62).toInt(), w, (h * 0.86).toInt())
        assertTrue("three takes should light the lower band (none $lowNone, three $lowThree)", lowThree > lowNone + 0.05)
    }

    @Test fun `the upper part stays dark enough to read light text on`() {
        val px = FilmFrame.paint(w, h, project())
        val top = FilmFrame.meanLuma(px, w, 0, 0, w, (h * 0.55).toInt())
        assertTrue("upper part mean luma $top", top < 0.16)
    }

    @Test fun `a faster tempo packs the bands of light closer together`() {
        fun bands(bpm: Double): Int {
            // Count peaks of the row brightness in the quiet upper part, on a frame with no trails.
            val px = FilmFrame.paint(w, h, project(bpm = bpm, trails = emptyList()))
            val rows = DoubleArray(h / 2) { y -> FilmFrame.meanLuma(px, w, 0, y, w, y + 1) }
            val smooth = DoubleArray(rows.size) { i -> (maxOf(0, i - 2)..minOf(rows.size - 1, i + 2)).map { rows[it] }.average() }
            val mean = smooth.average()
            return (4 until smooth.size - 4).count { i -> smooth[i] > mean && (i - 4..i + 4).all { j -> j == i || smooth[j] < smooth[i] } }
        }
        val slow = bands(60.0); val fast = bands(160.0)
        assertTrue("160 BPM should show more bands than 60 BPM (60: $slow, 160: $fast)", fast > slow)
    }

    @Test fun `a frame with no key, no tempo and no takes still paints`() {
        val px = FilmFrame.paint(w, h, Input(Kind.PROJECT, 1, null, false, null, emptyList()))
        assertEquals(w * h, px.size)
        assertTrue(px.all { (it ushr 24) == 0xFF })
    }

    @Test fun `a take's frame puts its long exposure in the upper third`() {
        val px = FilmFrame.paint(w, h, Input(Kind.TAKE, 3, 9, true, 92.0, listOf(envelope())))
        val band = FilmFrame.meanLuma(px, w, 0, (h * 0.22).toInt(), w, (h * 0.38).toInt())
        val below = FilmFrame.meanLuma(px, w, 0, (h * 0.55).toInt(), w, h)
        assertTrue("trail band $band should outshine the lower part $below", band > below * 3)
    }

    @Test fun `painting a half-resolution phone frame is quick on a desktop`() {
        FilmFrame.paint(540, 1158, project())   // warm up the JIT
        val t0 = System.nanoTime()
        FilmFrame.paint(540, 1158, project())
        val ms = (System.nanoTime() - t0) / 1e6
        println("FilmFrame 540x1158: %.1f ms on this machine".format(ms))
        assertTrue("took $ms ms", ms < 1500)
    }

    /** Not an assertion: writes sample frames to build/ so they can be looked at. */
    @Test fun `sample frames are written for review`() {
        fun write(name: String, px: IntArray, ww: Int, hh: Int) {
            val img = java.awt.image.BufferedImage(ww, hh, java.awt.image.BufferedImage.TYPE_INT_RGB)
            img.setRGB(0, 0, ww, hh, px, 0, ww)
            java.io.File("build/film-samples").mkdirs()
            javax.imageio.ImageIO.write(img, "png", java.io.File("build/film-samples/$name.png"))
        }
        write("a-minor-92", FilmFrame.paint(540, 1158, project()), 540, 1158)
        write("e-major-140", FilmFrame.paint(540, 1158, project(pc = 4, minor = false, bpm = 140.0, seed = 3)), 540, 1158)
        write("no-key", FilmFrame.paint(540, 1158, Input(Kind.PROJECT, 5, null, false, null, listOf(envelope()))), 540, 1158)
        write("take", FilmFrame.paint(540, 1158, Input(Kind.TAKE, 3, 9, true, 92.0, listOf(envelope()))), 540, 1158)
        val board = listOf(spiky(), spiky(phase = 1.0), spiky(phase = 2.0))
        for (seed in 1L..6L) write("plan-$seed", FilmFrame.paint(540, 1158, project(seed = seed, trails = board).let {
            Input(Kind.PROJECT, seed, it.tonicPc, it.minor, it.bpm, it.trails, it.starred, trailKeys = listOf(9 to true, 4 to false, 9 to true),
                trailBpm = listOf(92.0, 140.0, null), tempos = listOf(140.0), tonic2 = 4, minor2 = false)
        }), 540, 1158)
        write("practice", FilmFrame.paint(540, 1158, Input(Kind.PRACTICE, 7, 9, true, 92.0, emptyList())), 540, 1158)
        write("study-a", FilmFrame.paint(540, 1158, Input(Kind.STUDY, 7, 9, true, 92.0, emptyList())), 540, 1158)
        write("study-fsharp", FilmFrame.paint(540, 1158, Input(Kind.STUDY, 8, 6, false, 92.0, emptyList())), 540, 1158)
    }

    /** A real take's loudness is spiky, not smooth: notes and gaps. */
    private fun spiky(n: Int = 240, phase: Double = 0.0) = FloatArray(n) { i ->
        val t = i / (n - 1.0); val note = (i * 7 + (phase * 13).toInt()) % 11
        ((0.25 + 0.6 * Math.abs(sin(t * 23 + phase)) * (if (note < 2) 0.3 else 1.0)) * (1.1 - 0.4 * t)).coerceIn(0.05, 1.0).toFloat()
    }

    private fun mean(px: IntArray, ww: Int, x0: Int, y0: Int, x1: Int, y1: Int): DoubleArray {
        val m = DoubleArray(3); var n = 0
        for (y in y0 until y1) for (x in x0 until x1) { val p = px[y * ww + x]; m[0] += (p shr 16) and 255; m[1] += (p shr 8) and 255; m[2] += p and 255; n++ }
        return DoubleArray(3) { m[it] / n }
    }

    @Test fun `the two layers added together are the frame, and the light layer is the light alone`() {
        val l = FilmFrame.paintLayers(w, h, project())
        assertArrayEquals(FilmFrame.paint(w, h, project()), l.composite())
        val top = mean(l.light, w, 0, 0, w, h / 8); val low = mean(l.light, w, 0, (h * 0.62).toInt(), w, (h * 0.86).toInt())
        assertTrue("the light layer should be near black where nothing shines (${top.toList()}) and bright on the trails (${low.toList()})", top.sum() < 30 && low.sum() > top.sum() * 4)
    }

    @Test fun `PRACTICE is the violet room and STUDY the sepia print, whatever the project's key`() {
        for (pc in listOf(0, 4, 9)) {
            val pr = mean(FilmFrame.paint(w, h, Input(Kind.PRACTICE, 7, pc, false, null, emptyList())), w, 0, 0, w, h / 2)
            assertTrue("PRACTICE in pc $pc: blue over red over green, was ${pr.toList()}", pr[2] > pr[0] && pr[0] > pr[1] * 1.3)
            val st = mean(FilmFrame.paint(w, h, Input(Kind.STUDY, 7, pc, false, null, emptyList())), w, 0, 0, w, h / 2)
            assertTrue("STUDY in pc $pc: red over green over blue, was ${st.toList()}", st[0] > st[1] && st[1] > st[2] * 1.1)
        }
    }

    @Test fun `STUDY's light falls on the project's tonic`() {
        val lit = FilmFrame.paint(w, h, Input(Kind.STUDY, 7, 7, false, null, emptyList()))
        val dark = FilmFrame.paint(w, h, Input(Kind.STUDY, 7, null, false, null, emptyList()))
        val x = FilmRooms.keyCentre(7, w).toInt()
        val a = FilmFrame.meanLuma(lit, w, x - 6, (h * 0.33).toInt(), x + 6, (h * 0.47).toInt())
        val b = FilmFrame.meanLuma(dark, w, x - 6, (h * 0.33).toInt(), x + 6, (h * 0.47).toInt())
        assertTrue("the G key should be lit with a G tonic ($a) and not without one ($b)", a > b + 0.08)
    }

    @Test fun `two projects with the same key, tempo and takes still look clearly different`() {
        val a = FilmFrame.paint(w, h, project(seed = 1)); val b = FilmFrame.paint(w, h, project(seed = 2))
        var d = 0.0
        for (i in a.indices) for (s in intArrayOf(0, 8, 16)) d += Math.abs(((a[i] shr s) and 255) - ((b[i] shr s) and 255))
        d /= a.size * 3
        assertTrue("mean difference per channel $d", d > 8)
        assertNotEquals(FilmRooms.layout(1), FilmRooms.layout(2))
    }

    @Test fun `each take's trail burns in its own key's colour`() {
        fun lowHue(pc: Int): Double {
            // The light alone, so the ground's slate doesn't drown the trail's colour.
            val px = FilmFrame.paintLayers(w, h, Input(Kind.PROJECT, 1, null, false, null, listOf(envelope()), trailKeys = listOf(pc to false))).light
            val m = mean(px, w, 0, (h * 0.5).toInt(), w, h)
            return Math.toDegrees(Math.atan2(Math.sqrt(3.0) * (m[1] - m[2]), 2 * m[0] - m[1] - m[2]))
        }
        assertTrue("an A trail and a D trail should differ in colour (${lowHue(9)} vs ${lowHue(2)})", Math.abs(lowHue(9) - lowHue(2)) > 15)
    }

    private fun rooms(): List<Pair<String, Input>> {
        val board = listOf(spiky(), spiky(phase = 1.0), spiky(phase = 2.0))
        return (1L..6L).map { s ->
            "plan-$s" to Input(Kind.PROJECT, s, (s * 5 % 12).toInt(), s % 2 == 0L, 92.0 + s * 10, board, trailKeys = listOf(9 to true, 4 to false, 9 to true),
                trailBpm = listOf(92.0, 140.0, null), tempos = listOf(140.0), tonic2 = 4, minor2 = false)
        } + listOf(0, 4, 6, 9, 11).flatMap { pc ->
            listOf("practice-$pc" to Input(Kind.PRACTICE, pc + 1L, pc, true, 92.0, emptyList()), "study-$pc" to Input(Kind.STUDY, pc + 1L, pc, false, 92.0, emptyList()))
        } + listOf("study-none" to Input(Kind.STUDY, 3, null, false, null, emptyList()))
    }

    @Test fun `no area a line of text could sit in is brighter than light text can be read on`() {
        for ((name, inp) in rooms()) {
            val worst = FilmRooms.textAreaLuminance(FilmRooms.paint(w, h, inp)).max()
            // A little over the ceiling is from re-averaging the scaled frame.
            assertTrue("$name: the brightest text-sized area is ${"%.3f".format(worst)}, should be at most ${FilmRooms.TEXT_CEILING}",
                worst <= FilmRooms.TEXT_CEILING + 0.005)
        }
    }

    @Test fun `keeping text readable leaves the trails bright and the keys reading as keys`() {
        for (s in 1L..6L) {
            val px = FilmFrame.paint(w, h, rooms()[s.toInt() - 1].second)
            val luma = px.map { p -> 0.299 * ((p shr 16) and 255) + 0.587 * ((p shr 8) and 255) + 0.114 * (p and 255) }.sorted()
            val top = luma[(luma.size * 0.995).toInt()]
            assertTrue("plan-$s: the trails keep their light (0.9.48 measured 169 to 213 here), was $top", top >= 165)
        }
        val st = FilmFrame.paint(w, h, Input(Kind.STUDY, 3, null, false, null, emptyList()))
        val y0 = (h * 0.27).toInt(); val y1 = (h * 0.33).toInt()
        // In the focus band: white key E (left half of its key) against the black key between C and D.
        val white = FilmFrame.meanLuma(st, w, FilmRooms.keyCentre(4, w).toInt() - 6, y0, FilmRooms.keyCentre(4, w).toInt() + 6, y1)
        val black = FilmFrame.meanLuma(st, w, FilmRooms.keyCentre(1, w).toInt() - 4, y0, FilmRooms.keyCentre(1, w).toInt() + 4, y1)
        assertTrue("STUDY's white keys ($white) should still stand well clear of its black keys ($black)", white > black * 2.5 && white > 0.2)
    }

    @Test fun `box blur keeps the total light`() {
        val src = FloatArray(40 * 30).also { it[15 * 40 + 20] = 100f }
        val out = FilmFrame.boxBlur3(src, 40, 30, 3)
        assertEquals(100.0, out.sum().toDouble(), 0.5)
    }
}
