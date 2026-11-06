package com.betterappmafia.apophenia.core.art

import kotlin.math.PI
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
 * The Film theme's painted frames (Christopher, 31 Oct 2026: "Film still", backgrounds unique per project and
 * abstract, painted from the material's own data; nothing figurative, nothing interpreted).
 *
 * - Key sets the palette: the tonic's pitch class picks the hue, a minor-third mode is darker and more saturated.
 * - Tempo spaces faint horizontal bands of light, one beat apart.
 * - Each take's loudness envelope becomes one long-exposure light trail; the starred take burns brightest.
 *
 * Deterministic: the same [Input] always gives the same pixels, so a frame can be thrown away and painted again.
 * Painted once off the main thread and cached by the app; nothing here runs per frame. The soft layers (haze, glow,
 * halation) are worked at a quarter of the output size with box blurs, whose cost doesn't depend on the radius;
 * grain and dust go on at full size.
 */
object FilmFrame {

    enum class Kind {
        /** A project's frame: palette, tempo bands, one trail per take, lower part kept for the trails. */
        PROJECT,
        /** A take's own frame: one long exposure of its loudness across the upper third, the rest dark. */
        TAKE,
        /** The PRACTICE section: a violet room, two soft columns of light (the Film mockup's Ear frame, 31 Oct 2026). */
        PRACTICE,
        /** The STUDY section: a sepia darkroom print of piano keys out of focus, light falling on the project's tonic. */
        STUDY,
    }

    class Input(
        val kind: Kind,
        /** Fixes the haze, the grain and the dust. */
        val seed: Long,
        /** 0 = C … 11 = B; null when no key is known (a neutral slate). */
        val tonicPc: Int?,
        val minor: Boolean,
        val bpm: Double?,
        /** Loudness envelopes, 0..1, any length. */
        val trails: List<FloatArray>,
        /** Index into [trails] of the starred take, or -1. */
        val starred: Int = -1,
        /** Each trail's own key as (tonic pitch class, minor), null where unknown; same order as [trails]. */
        val trailKeys: List<Pair<Int, Boolean>?> = emptyList(),
        /** Each trail's own tempo, null in free time; same order as [trails]. */
        val trailBpm: List<Double?> = emptyList(),
        /** The Board's other tempos besides [bpm] (up to two): each lays its own set of bands across the frame. */
        val tempos: List<Double> = emptyList(),
        /** The Board's second most common key, for the duotone haze; null when the Board has one key. */
        val tonic2: Int? = null,
        val minor2: Boolean = false,
    ) {
        /** For cache file names: changes whenever the picture would. */
        fun signature(): String {
            var h = 1125899906842597L
            fun mix(v: Long) { h = 31 * h + v }
            mix(kind.ordinal.toLong()); mix(seed); mix((tonicPc ?: -1).toLong()); mix(if (minor) 1 else 0)
            mix(((bpm ?: -1.0) * 100).toLong()); mix(starred.toLong()); mix(VERSION.toLong())
            trails.forEach { t -> mix(t.size.toLong()); t.forEach { mix((it * 1000).toLong()) } }
            trailKeys.forEach { mix(it?.let { (pc, m) -> pc * 2L + (if (m) 1 else 0) } ?: -1L) }
            trailBpm.forEach { mix(((it ?: -1.0) * 100).toLong()) }
            tempos.forEach { mix((it * 100).toLong()) }
            mix((tonic2 ?: -1).toLong()); mix(if (minor2) 1 else 0)
            return java.lang.Long.toHexString(h)
        }
    }

    /** Raise when the painting changes, so cached frames are painted again. */
    const val VERSION = 4

    /** Palette for a key: (deep top, base, warm light), each as linear 0..255 RGB. */
    fun palette(tonicPc: Int?, minor: Boolean): Triple<FloatArray, FloatArray, FloatArray> {
        if (tonicPc == null) {
            val deep = hsv(0.6f, 0.3f, 0.10f); val base = hsv(0.62f, 0.3f, 0.30f); val warm = hsv(0.08f, 0.55f, 1f)
            return Triple(deep, base, warm)
        }
        val hue = (Math.floorMod(tonicPc, 12) * 30f) / 360f
        val sat = if (minor) 0.55f else 0.45f
        val v = if (minor) 0.20f else 0.30f
        val base = hsv(hue, sat, v)
        val deep = hsv((hue + 0.04f) % 1f, min(1f, sat * 1.1f), v * 0.35f)
        val warm = hsv((hue + 0.42f) % 1f, 0.7f, 1f)
        return Triple(deep, base, warm)
    }

    /** Paints [w]×[h] ARGB pixels (opaque): a take's frame as one picture, a section's as its two layers added together. */
    fun paint(w: Int, h: Int, input: Input): IntArray =
        if (input.kind == Kind.TAKE) paintTake(w, h, input) else FilmRooms.paint(w, h, input).composite()

    /** A section frame in two layers, so the app can move the light over a still ground (see [FilmRooms]). */
    fun paintLayers(w: Int, h: Int, input: Input): FilmRooms.Layers = FilmRooms.paint(w, h, input)

    private fun paintTake(w: Int, h: Int, input: Input): IntArray {
        require(w > 0 && h > 0)
        val lw = max(8, w / 4); val lh = max(8, h / 4)
        val r = FloatArray(lw * lh); val g = FloatArray(lw * lh); val b = FloatArray(lw * lh)
        val (deep, base, warm) = palette(input.tonicPc, input.minor)
        val haze = valueNoise(lw, lh, input.seed, cell = lw / 3f, octaves = 4)

        // Ground: deep at the top to the key's colour at the bottom, broken up by haze.
        for (y in 0 until lh) {
            val t = y / (lh - 1f)
            for (x in 0 until lw) {
                val i = y * lw + x
                val k = 0.55f + 0.9f * haze[i]
                val dim = if (input.kind == Kind.TAKE) 0.45f else 1f
                r[i] = (deep[0] * (1 - t) + base[0] * t) * k * dim
                g[i] = (deep[1] * (1 - t) + base[1] * t) * k * dim
                b[i] = (deep[2] * (1 - t) + base[2] * t) * k * dim
            }
        }

        // Tempo: faint bands of light one beat apart (the prototype's 9000/bpm px at 1688 px tall).
        val bpm = input.bpm
        if (input.kind == Kind.PROJECT && bpm != null && bpm > 20) {
            val period = lh * (9000.0 / bpm / 1688.0)
            for (y in 0 until lh) {
                val s = ((0.5 + 0.5 * cos(2 * PI * y / period)).pow(10)).toFloat()
                for (x in 0 until lw) {
                    val i = y * lw + x
                    val k = s * (haze[i] * haze[i]) * 0.5f
                    r[i] += base[0] * k; g[i] += base[1] * k; b[i] += base[2] * k
                }
            }
        }

        // A soft glow of the key's own colour somewhere in the upper half, placed by the seed.
        run {
            val rnd = java.util.Random(input.seed * 7919)
            val gx = lw * (0.15f + 0.7f * rnd.nextFloat()); val gy = lh * (0.08f + 0.3f * rnd.nextFloat())
            val rad = lw * (0.5f + 0.3f * rnd.nextFloat())
            for (y in 0 until lh) for (x in 0 until lw) {
                val dx = (x - gx) / rad; val dy = (y - gy) / (rad * 1.4f)
                val k = exp(-(dx * dx + dy * dy)) * 0.55f * (0.6f + 0.4f * haze[y * lw + x])
                val i = y * lw + x
                r[i] += base[0] * k; g[i] += base[1] * k; b[i] += base[2] * k
            }
        }

        // Light trails: the core intensity, then glows of growing radius in the key's warm light.
        val core = FloatArray(lw * lh)
        val trails = input.trails.filter { it.isNotEmpty() }.take(if (input.kind == Kind.TAKE) 1 else 5)
        // Prototype px -> low-res px; a wide take banner is scaled to its own height instead.
        val unit = if (input.kind == Kind.TAKE && lw > lh) lh / 100f else lh / 1688f
        trails.forEachIndexed { n, env ->
            val bright = when {
                input.kind == Kind.TAKE -> 1f
                input.starred == input.trails.indexOf(env) -> 1f
                else -> 0.55f + 0.15f * (n % 2)
            }
            // A tall take frame keeps its exposure in the upper third; a wide one (a banner) through the middle.
            val y0 = if (input.kind == Kind.TAKE) lh * (if (lw > lh) 0.5f else 0.30f)
                else lh * (0.665f + (if (trails.size > 3) 0.21f / (trails.size - 1) else 0.07f) * n)
            val shift = if (input.kind == Kind.TAKE) 0f else n * 0.37f
            val wide = input.kind == Kind.TAKE && lw > lh
            val wobble = if (wide) 6f else if (input.kind == Kind.TAKE) 60f else 22f
            val maxSpread = if (wide) 9f else if (input.kind == Kind.TAKE) 70f else 22f
            for (x in 0 until lw) {
                val tt = x / (lw - 1f)
                // Mirrored rather than wrapped, so a shifted trail has no seam.
                val u = (tt + shift) % 2f
                val a = sample(env, if (u <= 1f) u else 2f - u)
                val cy = y0 + (sin(tt * (if (input.kind == Kind.TAKE) 5.2f else 4f) + n) * wobble * unit).toFloat()
                val sp = (1.5f + a * maxSpread) * unit
                val lo = max(0, (cy - sp * 4).toInt()); val hi = min(lh - 1, (cy + sp * 4).toInt())
                for (y in lo..hi) {
                    val d = (y - cy) / max(sp, 0.35f)
                    core[y * lw + x] += exp(-d * d) * (0.2f + 0.8f * a) * bright
                }
            }
        }
        val glow = floatArrayOf(1f, 5f, 22f, 60f).map { rad -> boxBlur3(core, lw, lh, max(0, (rad * unit * 2).roundToInt())) }
        val weights = floatArrayOf(0.9f, 0.6f, 0.45f, 0.3f)
        val tints = arrayOf(floatArrayOf(255f, 240f, 215f), warm, warm, floatArrayOf(base[0] * 2.2f, base[1] * 2.2f, base[2] * 2.2f))
        for (i in core.indices) {
            for (k in 0..3) {
                val v = glow[k][i] * weights[k]
                r[i] += v * tints[k][0]; g[i] += v * tints[k][1]; b[i] += v * tints[k][2]
            }
        }

        // Halation: what's very bright bleeds red into its surroundings, as on film.
        val hot = FloatArray(lw * lh) { i -> max(0f, (r[i] + g[i] + b[i]) / 3f - 170f) }
        val hal = boxBlur3(hot, lw, lh, max(1, (26 * unit * 2).roundToInt()))
        for (i in hal.indices) { r[i] += hal[i] * 1.65f; g[i] += hal[i] * 0.58f; b[i] += hal[i] * 0.3f }

        // Keep the upper part quiet for the text (projects); vignette everywhere.
        for (y in 0 until lh) for (x in 0 until lw) {
            val i = y * lw + x
            var k = 1f
            if (input.kind == Kind.PROJECT) k *= 1f - 0.35f * (1f - y / (lh * 0.55f)).coerceIn(0f, 1f)
            val dx = (x - lw / 2f) / (lw * 0.62f); val dy = (y - lh / 2f) / (lh * 0.58f)
            val d = sqrt(dx * dx + dy * dy)
            k *= 1f - 0.6f * ((d - 0.35f).coerceIn(0f, 1f)).pow(1.6f)
            r[i] *= k; g[i] *= k; b[i] *= k
        }

        // Up to full size, with grain (darker areas get more, as film does) and a little dust.
        val out = IntArray(w * h)
        val sx = (lw - 1f) / max(1, w - 1); val sy = (lh - 1f) / max(1, h - 1)
        var hash = (input.seed * 0x9E3779B97F4A7C15uL.toLong()) or 1L
        for (y in 0 until h) {
            val fy = y * sy; val y0 = fy.toInt(); val y1 = min(lh - 1, y0 + 1); val ty = fy - y0
            for (x in 0 until w) {
                val fx = x * sx; val x0 = fx.toInt(); val x1 = min(lw - 1, x0 + 1); val tx = fx - x0
                val a = y0 * lw + x0; val bb = y0 * lw + x1; val c = y1 * lw + x0; val d = y1 * lw + x1
                fun lerp(p: FloatArray) = (p[a] * (1 - tx) + p[bb] * tx) * (1 - ty) + (p[c] * (1 - tx) + p[d] * tx) * ty
                var rr = lerp(r); var gg = lerp(g); var bl = lerp(b)
                hash = hash xor (hash shl 13); hash = hash xor (hash ushr 7); hash = hash xor (hash shl 17)
                val n = ((hash and 0xFFFF).toFloat() / 65535f + ((hash ushr 16) and 0xFFFF).toFloat() / 65535f - 1f) * 16f
                val lum = (rr + gg + bl) / 765f
                val gn = n * (0.6f + 0.4f * (1f - lum.coerceIn(0f, 1f)))
                rr += gn; gg += gn; bl += gn
                out[y * w + x] = argb(rr, gg, bl)
            }
        }
        dust(out, w, h, input.seed)
        return out
    }

    internal fun dust(px: IntArray, w: Int, h: Int, seed: Long) {
        val rnd = java.util.Random(seed xor 0x5DEECE66DL)
        // Sparse and small: the frame is shown at twice its size, so one pixel of dust is already two on screen.
        val n = (w * h / 30000).coerceIn(3, 60)
        repeat(n) {
            val x = rnd.nextInt(w); val y = rnd.nextInt(h); val s = 1
            val v = if (rnd.nextInt(3) == 0) 200f else 10f
            for (yy in max(0, y - s) until min(h, y + s)) for (xx in max(0, x - s) until min(w, x + s)) {
                val p = px[yy * w + xx]
                px[yy * w + xx] = argb(((p shr 16) and 255) * 0.3f + v * 0.7f, ((p shr 8) and 255) * 0.3f + v * 0.7f, (p and 255) * 0.3f + v * 0.7f)
            }
        }
    }

    /** Linear interpolation into an envelope at t in 0..1. */
    internal fun sample(env: FloatArray, t: Float): Float {
        if (env.size == 1) return env[0].coerceIn(0f, 1f)
        val f = t.coerceIn(0f, 1f) * (env.size - 1)
        val i = floor(f).toInt().coerceAtMost(env.size - 2)
        val k = f - i
        return (env[i] * (1 - k) + env[i + 1] * k).coerceIn(0f, 1f)
    }

    /** Three box passes approximate a Gaussian; each pass is O(n) whatever the radius. */
    internal fun boxBlur3(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        if (radius <= 0) return src.copyOf()
        var a = src.copyOf(); var b = FloatArray(src.size)
        repeat(3) {
            boxH(a, b, w, h, radius); boxV(b, a, w, h, radius)
        }
        return a
    }

    private fun boxH(s: FloatArray, d: FloatArray, w: Int, h: Int, r: Int) {
        val norm = 1f / (2 * r + 1)
        for (y in 0 until h) {
            val row = y * w
            var acc = 0f
            for (k in -r..r) acc += s[row + k.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                d[row + x] = acc * norm
                acc += s[row + (x + r + 1).coerceAtMost(w - 1)] - s[row + (x - r).coerceAtLeast(0)]
            }
        }
    }

    private fun boxV(s: FloatArray, d: FloatArray, w: Int, h: Int, r: Int) {
        val norm = 1f / (2 * r + 1)
        for (x in 0 until w) {
            var acc = 0f
            for (k in -r..r) acc += s[k.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                d[y * w + x] = acc * norm
                acc += s[(y + r + 1).coerceAtMost(h - 1) * w + x] - s[(y - r).coerceAtLeast(0) * w + x]
            }
        }
    }

    /** Smooth value noise, 0..1, from a seeded lattice (bilinear, smoothstepped), summed over octaves. */
    internal fun valueNoise(w: Int, h: Int, seed: Long, cell: Float, octaves: Int): FloatArray {
        val out = FloatArray(w * h)
        var amp = 1f; var total = 0f; var c = max(2f, cell)
        for (o in 0 until octaves) {
            val s = seed * 31 + o
            for (y in 0 until h) for (x in 0 until w) {
                val fx = x / c; val fy = y / c
                val ix = floor(fx).toInt(); val iy = floor(fy).toInt()
                val tx = smooth(fx - ix); val ty = smooth(fy - iy)
                val v = (lattice(ix, iy, s) * (1 - tx) + lattice(ix + 1, iy, s) * tx) * (1 - ty) +
                    (lattice(ix, iy + 1, s) * (1 - tx) + lattice(ix + 1, iy + 1, s) * tx) * ty
                out[y * w + x] += v * amp
            }
            total += amp; amp *= 0.5f; c = max(2f, c / 2)
        }
        for (i in out.indices) out[i] /= total
        return out
    }

    private fun smooth(t: Float) = t * t * (3 - 2 * t)

    private fun lattice(x: Int, y: Int, seed: Long): Float {
        var v = seed xor (x.toLong() * 0x27D4EB2FL) xor (y.toLong() * 0x165667B1L)
        v = (v xor (v ushr 15)) * 0x2C1B3C6DL
        v = (v xor (v ushr 12)) * 0x297A2D39L
        v = v xor (v ushr 15)
        return ((v and 0xFFFFFF).toFloat() / 0xFFFFFF.toFloat())
    }

    internal fun hsv(h: Float, s: Float, v: Float): FloatArray {
        val i = floor(h * 6).toInt(); val f = h * 6 - i
        val p = v * (1 - s); val q = v * (1 - f * s); val t = v * (1 - (1 - f) * s)
        val (r, g, b) = when (Math.floorMod(i, 6)) {
            0 -> Triple(v, t, p); 1 -> Triple(q, v, p); 2 -> Triple(p, v, t)
            3 -> Triple(p, q, v); 4 -> Triple(t, p, v); else -> Triple(v, p, q)
        }
        return floatArrayOf(r * 255, g * 255, b * 255)
    }

    internal fun argb(r: Float, g: Float, b: Float): Int =
        (0xFF shl 24) or (clamp(r) shl 16) or (clamp(g) shl 8) or clamp(b)

    private fun clamp(v: Float): Int = if (v <= 0f) 0 else if (v >= 255f) 255 else v.toInt()

    /** Mean brightness 0..1 of a region (for tests and for checking text sits on a dark enough ground). */
    fun meanLuma(px: IntArray, w: Int, x0: Int, y0: Int, x1: Int, y1: Int): Double {
        var s = 0.0; var n = 0
        for (y in y0 until y1) for (x in x0 until x1) {
            val p = px[y * w + x]
            s += (0.2126 * ((p shr 16) and 255) + 0.7152 * ((p shr 8) and 255) + 0.0722 * (p and 255)) / 255.0; n++
        }
        return if (n == 0) 0.0 else s / n
    }

}
