package com.betterappmafia.apophenia.core.art

import com.betterappmafia.apophenia.core.art.FilmFrame.Input
import com.betterappmafia.apophenia.core.art.FilmFrame.Kind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The three sections' frames, painted in two layers (Christopher, 3 Nov 2026: "different from plan, practice and study",
 * "the bright violet from the mockup", "the lines look so much less bright", and slow movement that follows the tempo).
 *
 * - **PLAN** ([Kind.PROJECT]): the project's own frame. The Board's commonest key sets the ground, its second key the
 *   duotone haze; each tempo on the Board lays its own set of bands (one beat apart); each take burns one trail of light
 *   in its own key's colour, textured by its own tempo. The seed picks the layout (low, rising, arched) and whether
 *   a light leak burns in from an edge, so two projects in the same key still look apart.
 * - **PRACTICE**: the mockup's violet room, two soft columns of light; the seed places them.
 * - **STUDY**: the mockup's sepia print of piano keys out of focus, light pooling on the project's tonic.
 *
 * The [Layers.ground] stays still; [Layers.light] (black where there is no light) is added over it, so the app can move
 * and pulse the light with plain bitmap draws and never paint again. Trails and their tight glows are painted at the
 * output size, as the mockup was; haze and wide glows at a quarter of it with box blurs. Same input, same pixels.
 */
object FilmRooms {

    class Layers(val w: Int, val h: Int, val ground: IntArray, val light: IntArray) {
        /**
         * False on PLAN: its light is the takes' trails, which Christopher reads through ("I could still read fine",
         * 1 Nov 2026) and wants bright ("the lines look so much less bright", 3 Nov 2026), so only its ground is held down.
         */
        internal var lightUnderText = true
        /** The light added onto the ground: what the app shows when nothing moves. */
        fun composite(): IntArray = IntArray(w * h) { i ->
            val g = ground[i]; val l = light[i]
            FilmFrame.argb(
                (((g shr 16) and 255) + ((l shr 16) and 255)).toFloat(),
                (((g shr 8) and 255) + ((l shr 8) and 255)).toFloat(),
                ((g and 255) + (l and 255)).toFloat(),
            )
        }
    }

    fun paint(w: Int, h: Int, input: Input): Layers {
        require(w > 0 && h > 0)
        return legible(when (input.kind) {
            Kind.PROJECT -> project(w, h, input)
            Kind.PRACTICE -> practice(w, h, input)
            Kind.STUDY -> study(w, h, input)
            Kind.TAKE -> error("a take's frame is one picture: FilmFrame.paint")
        })
    }

    /**
     * The light layer as the app shows it at rest (FilmBackdrop: 1 + breathe·0.12; a beat adds up to 0.5 more for a
     * moment, which is allowed to flash past the ceiling).
     */
    const val REST_LIGHT = 1.12f
    /**
     * The most light text may sit on, as relative luminance averaged over about a line of text: 0.12 keeps body text
     * at 4.5:1 and the Film dim labels at 3:1 (ThemeContrastTest). Christopher, 3 Nov 2026: STUDY's "bright lettering on
     * the bright background"; "also the other screens. Just needs to be readable, but looks good".
     */
    const val TEXT_CEILING = 0.12
    private const val KNEE = 0.07

    private val LIN = DoubleArray(256) { c -> val v = c / 255.0; if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4) }

    /** Relative luminance under text, per pixel: the ground plus the light at rest (the ground alone on PLAN). */
    fun underTextLuminance(l: Layers): DoubleArray = DoubleArray(l.w * l.h) { i ->
        val g = l.ground[i]; val p = if (l.lightUnderText) l.light[i] else 0
        fun ch(s: Int) = LIN[min(255, ((g shr s) and 255) + (((p shr s) and 255) * REST_LIGHT).roundToInt())]
        0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)
    }

    /** [underTextLuminance] averaged over about a line of text (a soft window some 30 px across at the 540 px frame). */
    fun textAreaLuminance(l: Layers): FloatArray {
        val lw = max(8, l.w / 4); val lh = max(8, l.h / 4)
        val y = underTextLuminance(l)
        val f = FloatArray(l.w * l.h) { i -> y[i].toFloat() }
        return FilmFrame.boxBlur3(downsample(f, l.w, l.h, lw, lh), lw, lh, max(1, (lw / 34f).roundToInt()))
    }

    /**
     * Pulls down only what is bright over an area text could sit in (a lit key, a column, a pool), with a soft knee, so
     * the picture keeps its shape. Ground and light are scaled together, so the hues and the movement stay as painted.
     */
    private fun legible(l: Layers): Layers {
        var out = l
        // Light pushed past white clips, so one pass undershoots; a few settle it.
        repeat(3) { out = legibleOnce(out) }
        return out
    }

    private fun legibleOnce(l: Layers): Layers {
        val lw = max(8, l.w / 4); val lh = max(8, l.h / 4)
        val m = textAreaLuminance(l)
        val gain = FloatArray(m.size) { i ->
            val v = m[i].toDouble()
            if (v <= KNEE) 1f else {
                val span = TEXT_CEILING - KNEE
                val to = KNEE + span * (1 - exp(-(v - KNEE) / span))
                (to / v).pow(1 / 2.2).toFloat()   // a luminance gain, as a gain on the gamma-coded values
            }
        }
        if (gain.all { it >= 0.999f }) return l
        fun scale(px: IntArray) = IntArray(px.size) { i ->
            val x = i % l.w; val y = i / l.w
            val k = bilinear(gain, lw, lh, (x + 0.5f) / l.w * lw - 0.5f, (y + 0.5f) / l.h * lh - 0.5f)
            val p = px[i]
            FilmFrame.argb(((p shr 16) and 255) * k, ((p shr 8) and 255) * k, (p and 255) * k)
        }
        return Layers(l.w, l.h, scale(l.ground), if (l.lightUnderText) scale(l.light) else l.light).also { it.lightUnderText = l.lightUnderText }
    }

    /** Which arrangement the project's trails take: 0 low and level, 1 rising, 2 arched. Fixed by the seed. */
    fun layout(seed: Long): Int {
        var z = seed + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L; z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return Math.floorMod(z xor (z ushr 31), 3L).toInt()
    }

    // ---- PLAN ----------------------------------------------------------------------------------------------------

    private fun project(w: Int, h: Int, inp: Input): Layers {
        val lw = max(8, w / 4); val lh = max(8, h / 4); val n = lw * lh
        val (deep, base, warm) = FilmFrame.palette(inp.tonicPc, inp.minor)
        val rnd = java.util.Random(inp.seed * 7919 + 17)
        val base2 = if (inp.tonic2 != null) FilmFrame.palette(inp.tonic2, inp.minor2).second
            else rotateHue(base, if (rnd.nextBoolean()) 0.07f else -0.07f)
        val haze = FilmFrame.valueNoise(lw, lh, inp.seed, lw / 3f, 4)
        val duo = FilmFrame.valueNoise(lw, lh, inp.seed * 13 + 5, lw / 2f, 3)

        // Ground: deep at the top to the key's colour at the bottom, in two colours where the haze turns (duotone).
        val gr = FloatArray(n); val gg = FloatArray(n); val gb = FloatArray(n)
        for (y in 0 until lh) {
            val t = y / (lh - 1f)
            for (x in 0 until lw) {
                val i = y * lw + x
                val m = smoothstep(0.3f, 0.7f, duo[i]) * 0.6f
                val k = 0.55f + 0.9f * haze[i]
                for (c in 0..2) {
                    val col = base[c] * (1 - m) + base2[c] * m
                    val v = (deep[c] * (1 - t) + col * t) * k
                    when (c) { 0 -> gr[i] = v; 1 -> gg[i] = v; else -> gb[i] = v }
                }
            }
        }
        // A soft glow of the key's own colour in the upper half, placed by the seed.
        run {
            val gx = lw * (0.15f + 0.7f * rnd.nextFloat()); val gy = lh * (0.08f + 0.3f * rnd.nextFloat())
            val rad = lw * (0.5f + 0.3f * rnd.nextFloat())
            for (y in 0 until lh) for (x in 0 until lw) {
                val dx = (x - gx) / rad; val dy = (y - gy) / (rad * 1.4f)
                val i = y * lw + x
                val k = exp(-(dx * dx + dy * dy)) * 0.55f * (0.6f + 0.4f * haze[i])
                gr[i] += base[0] * k; gg[i] += base[1] * k; gb[i] += base[2] * k
            }
        }

        // Light: trails at full size, wide glows and the leak at a quarter.
        val Lr = FloatArray(w * h); val Lg = FloatArray(w * h); val Lb = FloatArray(w * h)
        val lr = FloatArray(n); val lg = FloatArray(n); val lb = FloatArray(n)
        val unit = h / 1688f          // the mockup's pixels -> output pixels
        val lunit = lh / 1688f
        val trails = inp.trails.withIndex().filter { it.value.isNotEmpty() }.take(5)
        val layout = layout(inp.seed)
        val step = if (trails.size > 3) 0.21f / (trails.size - 1) else 0.07f
        val cream = floatArrayOf(255f, 240f, 215f)
        trails.forEachIndexed { k, (idx, env) ->
            val bright = if (inp.starred == idx) 1f else 0.62f + 0.14f * (k % 2)
            val tk = inp.trailKeys.getOrNull(idx)
            val tint = if (tk != null) FilmFrame.palette(tk.first, tk.second).third else warm
            val tBase = if (tk != null) FilmFrame.palette(tk.first, tk.second).second else base
            val tBpm = inp.trailBpm.getOrNull(idx)
            val core = FloatArray(w * h)
            val shift = k * 0.37f
            for (x in 0 until w) {
                val tt = x / (w - 1f)
                // Mirrored rather than wrapped, so a shifted trail has no seam.
                val u = (tt + shift) % 2f
                var a = FilmFrame.sample(env, if (u <= 1f) u else 2f - u)
                // The take's own tempo beats along its trail: a faster take, closer knots of light.
                if (tBpm != null && tBpm > 20) { val c = cos(PI * tt * tBpm / 8.0).toFloat(); a *= 0.68f + 0.32f * c * c }
                val cy = when (layout) {
                    0 -> h * (0.665f + step * k) + sin(tt * 4f + k) * 22f * unit
                    1 -> h * (0.66f + step * k) + (0.5f - tt) * h * 0.14f + sin(tt * 3f + k) * 14f * unit
                    else -> h * (0.745f + step * k) - sin(PI.toFloat() * tt) * h * (0.10f + 0.025f * k)
                }
                val sp = max((2f + a * 28f) * unit, 0.6f)
                val lo = max(0, (cy - sp * 4).toInt()); val hi = min(h - 1, (cy + sp * 4).toInt())
                for (y in lo..hi) {
                    val d = (y - cy) / sp
                    core[y * w + x] += exp(-d * d) * (0.2f + 0.8f * a) * bright
                }
            }
            val g1 = FilmFrame.boxBlur3(core, w, h, max(0, unit.roundToInt()))
            val g5 = FilmFrame.boxBlur3(core, w, h, max(1, (7 * unit).roundToInt()))
            for (i in core.indices) {
                val a1 = g1[i] * 0.9f; val a5 = g5[i] * 0.6f
                Lr[i] += a1 * cream[0] + a5 * tint[0]; Lg[i] += a1 * cream[1] + a5 * tint[1]; Lb[i] += a1 * cream[2] + a5 * tint[2]
            }
            val small = downsample(core, w, h, lw, lh)
            val g22 = FilmFrame.boxBlur3(small, lw, lh, max(1, (22 * lunit * 1.4f).roundToInt()))
            val g60 = FilmFrame.boxBlur3(small, lw, lh, max(1, (60 * lunit * 1.4f).roundToInt()))
            for (i in 0 until n) {
                val a = g22[i] * 0.45f; val b = g60[i] * 0.3f * 2.2f
                lr[i] += a * tint[0] + b * tBase[0]; lg[i] += a * tint[1] + b * tBase[1]; lb[i] += a * tint[2] + b * tBase[2]
            }
        }
        // A light leak burning in from one edge, as on a roll's first frames: some projects get one.
        if (rnd.nextFloat() < 0.55f) {
            val right = rnd.nextBoolean()
            val cx = if (right) lw * 1.03f else -lw * 0.03f; val cy = lh * (0.5f + 0.32f * rnd.nextFloat())
            val rx = lw * (0.16f + 0.12f * rnd.nextFloat()); val ry = lh * (0.22f + 0.16f * rnd.nextFloat())
            val col = floatArrayOf(255f, 60f + 80f * rnd.nextFloat(), 22f)
            for (y in 0 until lh) for (x in 0 until lw) {
                val dx = (x - cx) / rx; val dy = (y - cy) / ry; val i = y * lw + x
                val k = exp(-(dx * dx + dy * dy)) * 0.6f * (0.7f + 0.6f * haze[i])
                lr[i] += col[0] * k; lg[i] += col[1] * k; lb[i] += col[2] * k
            }
        }
        halation(gr, gg, gb, lr, lg, lb, Lr, Lg, Lb, w, h, lw, lh, 170f, 26 * lunit * 1.4f, floatArrayOf(1.65f, 0.58f, 0.3f))

        // Keep the upper part quiet for the text; vignette everywhere.
        val shade = FloatArray(n) { i ->
            val x = i % lw; val y = i / lw
            (1f - 0.35f * (1f - y / (lh * 0.55f)).coerceIn(0f, 1f)) * vignette(x, y, lw, lh, 0.6f)
        }
        // Bands: one set per tempo on the Board, one beat apart; the first level, the others a little tilted.
        val tempos = (listOfNotNull(inp.bpm) + inp.tempos).filter { it > 20 }.distinct().take(3)
        val bandRnd = java.util.Random(inp.seed * 104729 + 1)
        val bands = tempos.mapIndexed { j, bpm ->
            Band(
                period = (h * (9000.0 / bpm / 1688.0)).toFloat(),
                tilt = if (j == 0) 0f else (bandRnd.nextFloat() - 0.5f) * 0.12f,
                tint = when (j) { 0 -> base; 1 -> base2; else -> floatArrayOf(warm[0] * 0.35f, warm[1] * 0.35f, warm[2] * 0.35f) },
                strength = if (j == 0) 0.4f else 0.22f,
            )
        }
        return finish(w, h, lw, lh, gr, gg, gb, lr, lg, lb, Lr, Lg, Lb, shade, haze, bands, grain = 16f, seed = inp.seed).also { it.lightUnderText = false }
    }

    private class Band(val period: Float, val tilt: Float, val tint: FloatArray, val strength: Float)

    /** (0.5 + 0.5 cos)^10 over one period, the mockup's strata, looked up instead of computed per pixel. */
    private val BAND = FloatArray(512) { i -> (0.5 + 0.5 * cos(2 * PI * i / 512)).pow(10).toFloat() }

    // ---- PRACTICE ------------------------------------------------------------------------------------------------

    private fun practice(w: Int, h: Int, inp: Input): Layers {
        val lw = max(8, w / 4); val lh = max(8, h / 4); val n = lw * lh
        val haze = FilmFrame.valueNoise(lw, lh, inp.seed + 101, lw / 3f, 4)
        val rnd = java.util.Random(inp.seed * 31 + 7)
        val gr = FloatArray(n); val gg = FloatArray(n); val gb = FloatArray(n)
        val lr = FloatArray(n); val lg = FloatArray(n); val lb = FloatArray(n)
        // The mockup's violet: lighter toward the bottom, the haze in violet and magenta.
        for (y in 0 until lh) {
            val t = y / (lh - 1f)
            for (x in 0 until lw) {
                val i = y * lw + x; val z = haze[i] - 0.5f
                gr[i] = 40 + 50 * t + z * 60; gg[i] = 12 + 6 * t + z * 20; gb[i] = 80 + 90 * t + z * 80
            }
        }
        val cy = lh * (0.30f + 0.08f * rnd.nextFloat())
        val cols = listOf(lw * (0.13f + 0.10f * rnd.nextFloat()), lw * (0.77f + 0.10f * rnd.nextFloat()))
        for (cx in cols) {
            val sx = lw * (0.09f + 0.04f * rnd.nextFloat()); val sy = lh * (0.19f + 0.06f * rnd.nextFloat())
            for (y in 0 until lh) for (x in 0 until lw) {
                val dx = (x - cx) / sx; val dy = (y - cy) / sy; val i = y * lw + x
                val k = exp(-(dx * dx + dy * dy)) * (0.8f + 0.4f * haze[i])
                // Saturated violet: vivid at the brightness text can sit on, where near-white went grey.
                lr[i] += 175f * k; lg[i] += 85f * k; lb[i] += 255f * k
            }
        }
        // Dark between the columns; the lower half darkens for the drill's text.
        for (y in 0 until lh) for (x in 0 until lw) {
            val i = y * lw + x
            val dx = (x - lw / 2f) / (lw * 0.13f); val dy = (y - lh * 0.34f) / (lh * 0.3f)
            val k = (1 - 0.75f * exp(-(dx * dx + dy * dy))) * (1 - 0.72f * ((y - lh * 0.5f) / (lh * 0.3f)).coerceIn(0f, 1f))
            gr[i] *= k; gg[i] *= k; gb[i] *= k; lr[i] *= k; lg[i] *= k; lb[i] *= k
        }
        // A slow shutter's vertical drag, then softness.
        for (a in arrayOf(lr, lg, lb)) {
            val s = FilmFrame.boxBlur3(a, lw, lh, max(1, lw / 60)); s.copyInto(a)
        }
        halation(gr, gg, gb, lr, lg, lb, null, null, null, w, h, lw, lh, 190f, lh / 1688f * 30 * 1.4f, floatArrayOf(0.9f, 0.45f, 0.8f))
        val shade = FloatArray(n) { i -> vignette(i % lw, i / lw, lw, lh, 0.5f) }
        return finish(w, h, lw, lh, gr, gg, gb, lr, lg, lb, null, null, null, shade, haze, emptyList(), grain = 14f, seed = inp.seed + 3)
    }

    // ---- STUDY ---------------------------------------------------------------------------------------------------

    /** The x centre of pitch class [pc] on the print's keyboard (C at the left), in output pixels. */
    fun keyCentre(pc: Int, w: Int): Float {
        val kw = w * 120f / 780f; val off = w * 40f / 780f
        val white = intArrayOf(0, 2, 4, 5, 7, 9, 11).indexOf(Math.floorMod(pc, 12))
        if (white >= 0) return white * kw - off + kw / 2
        val boundary = when (Math.floorMod(pc, 12)) { 1 -> 1; 3 -> 2; 6 -> 4; 8 -> 5; else -> 6 }
        return boundary * kw - off
    }

    private fun study(w: Int, h: Int, inp: Input): Layers {
        val lw = max(8, w / 4); val lh = max(8, h / 4); val n = lw * lh
        val s = w / 780f
        val kw = w * 120f / 780f; val off = w * 40f / 780f
        fun keys(x: Float, y: Float): Float {
            if (y < h * 0.08f || y >= h * 0.68f) return 16f
            for (b in intArrayOf(1, 2, 4, 5, 6)) {
                val bx = b * kw - off
                if (abs(x - bx) < 34 * s && y < h * 0.42f + (x / w) * 40 * s) return 10f
            }
            return if (((x + off) % kw) < kw - 8 * s) 100f else 20f
        }
        // Out of focus everywhere but a band a third of the way down.
        val soft = FloatArray(n) { i -> keys((i % lw + 0.5f) * w / lw, (i / lw + 0.5f) * h / lh) }
        val blurred = FilmFrame.boxBlur3(soft, lw, lh, max(1, (14 * s / 4 * 1.4f).roundToInt()))
        val rnd = java.util.Random(inp.seed * 31 + 11)
        val lx = 0.1f + 0.2f * rnd.nextFloat()
        val haze = FilmFrame.valueNoise(lw, lh, inp.seed + 202, lw / 3f, 3)
        val lr = FloatArray(n); val lg = FloatArray(n); val lb = FloatArray(n)
        // The project's tonic catches the light.
        inp.tonicPc?.let { pc ->
            val black = Math.floorMod(pc, 12) in intArrayOf(1, 3, 6, 8, 10)
            val cx = keyCentre(pc, w) / w * lw; val cy = lh * (if (black) 0.27f else 0.4f)
            val sx = kw / w * lw * (if (black) 0.35f else 0.5f); val sy = lh * 0.2f
            for (y in 0 until lh) for (x in 0 until lw) {
                val dx = (x - cx) / sx; val dy = (y - cy) / sy; val i = y * lw + x
                val k = exp(-(dx * dx + dy * dy)) * 0.55f * (0.8f + 0.4f * haze[i]) * (1 - 0.8f * ((y - lh * 0.55f) / (lh * 0.25f)).coerceIn(0f, 1f))
                lr[i] += 255f * k; lg[i] += 205f * k; lb[i] += 140f * k
            }
        }
        val shade = FloatArray(n) { i -> vignette(i % lw, i / lw, lw, lh, 0.6f) }
        val zero = FloatArray(n)
        // The ground is the print itself, worked at full size in finish() through [extra].
        return finish(w, h, lw, lh, zero, zero, zero, lr, lg, lb, null, null, null, shade, haze, emptyList(), grain = 18f, seed = inp.seed + 5) { x, y, rgb ->
            val fy = y.toFloat()
            val focus = exp(-((fy - h * 0.3f) / (h * 0.09f)).pow(2))
            var v = keys(x.toFloat(), fy) * focus + bilinear(blurred, lw, lh, x.toFloat() / w * lw - 0.5f, fy / h * lh - 0.5f) * (1 - focus)
            val dx = (x - w * lx) / (w * 0.6f); val dy = (fy - h * 0.1f) / (h * 0.5f)
            v *= 0.35f + 0.75f * exp(-(dx * dx + dy * dy))
            v *= 1 - 0.8f * ((fy - h * 0.55f) / (h * 0.25f)).coerceIn(0f, 1f)
            rgb[0] += v + 18; rgb[1] += v * 0.86f + 10; rgb[2] += v * 0.66f + 4
        }
    }

    // ---- shared --------------------------------------------------------------------------------------------------

    /** What is very bright bleeds into its surroundings, as on film; added to the light. */
    private fun halation(
        gr: FloatArray, gg: FloatArray, gb: FloatArray, lr: FloatArray, lg: FloatArray, lb: FloatArray,
        Lr: FloatArray?, Lg: FloatArray?, Lb: FloatArray?, w: Int, h: Int, lw: Int, lh: Int, thresh: Float, radius: Float, tint: FloatArray,
    ) {
        val full = if (Lr != null && Lg != null && Lb != null) downsample(FloatArray(w * h) { i -> (Lr[i] + Lg[i] + Lb[i]) / 3f }, w, h, lw, lh) else null
        val hot = FloatArray(lw * lh) { i -> max(0f, (gr[i] + gg[i] + gb[i] + lr[i] + lg[i] + lb[i]) / 3f + (full?.get(i) ?: 0f) - thresh) }
        val hal = FilmFrame.boxBlur3(hot, lw, lh, max(1, radius.roundToInt()))
        for (i in hal.indices) { lr[i] += hal[i] * tint[0]; lg[i] += hal[i] * tint[1]; lb[i] += hal[i] * tint[2] }
    }

    private fun vignette(x: Int, y: Int, w: Int, h: Int, amount: Float): Float {
        val dx = (x - w / 2f) / (w * 0.62f); val dy = (y - h / 2f) / (h * 0.58f)
        return 1f - amount * ((sqrt(dx * dx + dy * dy) - 0.35f).coerceIn(0f, 1f)).pow(1.6f)
    }

    /**
     * Up to the output size: the ground with grain (darker areas get more, as film does), bands, dust and hairs; the
     * light from its quarter-size and full-size parts, with a little grain of its own (multiplied, so black stays black).
     */
    private fun finish(
        w: Int, h: Int, lw: Int, lh: Int,
        gr: FloatArray, gg: FloatArray, gb: FloatArray, lr: FloatArray, lg: FloatArray, lb: FloatArray,
        Lr: FloatArray?, Lg: FloatArray?, Lb: FloatArray?, shade: FloatArray, haze: FloatArray, bands: List<Band>,
        grain: Float, seed: Long, extra: ((Int, Int, FloatArray) -> Unit)? = null,
    ): Layers {
        val ground = IntArray(w * h); val light = IntArray(w * h)
        val rgb = FloatArray(3)
        var hash = (seed * -0x61c8864680b583ebL) or 1L
        for (y in 0 until h) {
            val fy = y.toFloat() / h * lh - 0.5f
            for (x in 0 until w) {
                val fx = x.toFloat() / w * lw - 0.5f
                val sh = bilinear(shade, lw, lh, fx, fy)
                rgb[0] = bilinear(gr, lw, lh, fx, fy); rgb[1] = bilinear(gg, lw, lh, fx, fy); rgb[2] = bilinear(gb, lw, lh, fx, fy)
                if (bands.isNotEmpty()) {
                    val hz = 0.4f + 0.6f * bilinear(haze, lw, lh, fx, fy)
                    for (b in bands) {
                        val ph = (y + x * b.tilt) / b.period
                        val v = BAND[((ph - floor(ph)) * 511).toInt()] * hz * b.strength
                        rgb[0] += b.tint[0] * v; rgb[1] += b.tint[1] * v; rgb[2] += b.tint[2] * v
                    }
                }
                extra?.invoke(x, y, rgb)
                hash = hash xor (hash shl 13); hash = hash xor (hash ushr 7); hash = hash xor (hash shl 17)
                val nz = ((hash and 0xFFFF).toFloat() / 65535f + ((hash ushr 16) and 0xFFFF).toFloat() / 65535f - 1f)
                val r0 = rgb[0] * sh; val g0 = rgb[1] * sh; val b0 = rgb[2] * sh
                val lum = (r0 + g0 + b0) / 765f
                val gn = nz * grain * (0.6f + 0.4f * (1f - lum.coerceIn(0f, 1f)))
                ground[y * w + x] = FilmFrame.argb(r0 + gn, g0 + gn, b0 + gn)
                val i = y * w + x
                val k = sh * (1f + nz * 0.12f)
                light[i] = FilmFrame.argb(
                    (bilinear(lr, lw, lh, fx, fy) + (Lr?.get(i) ?: 0f)) * k,
                    (bilinear(lg, lw, lh, fx, fy) + (Lg?.get(i) ?: 0f)) * k,
                    (bilinear(lb, lw, lh, fx, fy) + (Lb?.get(i) ?: 0f)) * k,
                )
            }
        }
        FilmFrame.dust(ground, w, h, seed)
        hairs(ground, w, h, seed)
        return Layers(w, h, ground, light)
    }

    /** Two or three hairs on the print, as in the mockup. */
    private fun hairs(px: IntArray, w: Int, h: Int, seed: Long) {
        val rnd = java.util.Random(seed * 3 + 99)
        repeat(2 + rnd.nextInt(2)) {
            val x0 = rnd.nextInt(w); val y0 = rnd.nextInt(h); val len = (h * (0.025f + 0.06f * rnd.nextFloat())).toInt()
            val a = rnd.nextFloat() * PI.toFloat()
            for (t in 0 until len) {
                val x = (x0 + cos(a) * t + sin(t / 9f) * 2).toInt(); val y = (y0 + sin(a) * t).toInt()
                if (x in 0 until w && y in 0 until h) {
                    val p = px[y * w + x]
                    px[y * w + x] = FilmFrame.argb(((p shr 16) and 255) * 0.45f + 120f, ((p shr 8) and 255) * 0.45f + 120f, (p and 255) * 0.45f + 120f)
                }
            }
        }
    }

    private fun bilinear(a: FloatArray, w: Int, h: Int, fx: Float, fy: Float): Float {
        val x = fx.coerceIn(0f, w - 1f); val y = fy.coerceIn(0f, h - 1f)
        val x0 = x.toInt(); val y0 = y.toInt(); val x1 = min(w - 1, x0 + 1); val y1 = min(h - 1, y0 + 1)
        val tx = x - x0; val ty = y - y0
        return (a[y0 * w + x0] * (1 - tx) + a[y0 * w + x1] * tx) * (1 - ty) + (a[y1 * w + x0] * (1 - tx) + a[y1 * w + x1] * tx) * ty
    }

    /** Box average down to the quarter-size grid (keeps the total light per area). */
    private fun downsample(src: FloatArray, w: Int, h: Int, lw: Int, lh: Int): FloatArray {
        val out = FloatArray(lw * lh); val cnt = IntArray(lw * lh)
        for (y in 0 until h) {
            val ly = min(lh - 1, y * lh / h)
            for (x in 0 until w) {
                val j = ly * lw + min(lw - 1, x * lw / w)
                out[j] += src[y * w + x]; cnt[j]++
            }
        }
        for (j in out.indices) if (cnt[j] > 0) out[j] /= cnt[j]
        return out
    }

    private fun smoothstep(a: Float, b: Float, x: Float): Float { val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }

    /** Turns a colour round the colour wheel by [turn] (0..1), keeping its saturation and brightness. */
    internal fun rotateHue(c: FloatArray, turn: Float): FloatArray {
        val r = c[0] / 255f; val g = c[1] / 255f; val b = c[2] / 255f
        val mx = maxOf(r, g, b); val mn = minOf(r, g, b); val d = mx - mn
        if (d <= 1e-6f) return c.copyOf()
        var hue = when (mx) { r -> ((g - b) / d) % 6f; g -> (b - r) / d + 2f; else -> (r - g) / d + 4f } / 6f
        if (hue < 0) hue += 1f
        return FilmFrame.hsv((hue + turn + 1f) % 1f, d / mx, mx)
    }
}
