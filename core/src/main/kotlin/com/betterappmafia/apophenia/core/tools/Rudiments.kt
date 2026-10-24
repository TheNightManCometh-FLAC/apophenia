package com.betterappmafia.apophenia.core.tools

/**
 * One stroke of a sticking: the [hand] (R or L), grace notes before it ("l" = a flam from the left,
 * "ll" = a drag; a grace from the same hand, "rR", is a bounced double), accent, buzz. A null hand is a
 * slot where nothing is struck (the previous note lasts longer).
 */
data class Stroke(val hand: Char?, val grace: String = "", val accent: Boolean = false, val buzz: Boolean = false) {
    override fun toString() = if (hand == null) "-" else (if (accent) ">" else "") + grace + hand + (if (buzz) "~" else "")

    companion object {
        /** "R", ">R", "lR", ">llR", "R~", "-". */
        fun parse(t: String): Stroke {
            if (t == "-") return Stroke(null)
            var s = t
            val accent = s.startsWith(">").also { if (it) s = s.substring(1) }
            val buzz = s.endsWith("~").also { if (it) s = s.dropLast(1) }
            require(s.isNotEmpty() && s.last() in "RL" && s.dropLast(1).all { it in "rl" }) { "not a stroke: '$t'" }
            return Stroke(s.last(), s.dropLast(1), accent, buzz)
        }
    }
}

/**
 * A drum rudiment, Reference material for drummers (plan §8, Christopher 22 Oct 2026: "Patterns
 * for drummers as well, paradiddles and all that"). [slotsPerBeat] strokes fit one beat; the pattern
 * repeats. [Claude] Rhythm is simplified to equal slots (a rest slot "-" lengthens the note before it);
 * the sticking, accents, flams and drags follow the PAS sheet.
 */
data class Rudiment(val number: Int, val name: String, val family: String, val slotsPerBeat: Int, val strokes: List<Stroke>) {
    val beats: Double get() = strokes.size.toDouble() / slotsPerBeat

    /** The slot sounding [beatsElapsed] beats after the pattern started. */
    fun slotAt(beatsElapsed: Double): Int = Math.floorMod(kotlin.math.floor(beatsElapsed * slotsPerBeat).toLong(), strokes.size.toLong()).toInt()

    /** Frames per slot at [bpm] (a beat holds [slotsPerBeat] slots). */
    fun slotFrames(bpm: Double, rate: Int): Double = rate * 60.0 / bpm / slotsPerBeat

    /**
     * The rudiment played on a synthesized snare, [passes] times, for hearing it (plan §8 Reference).
     * Accents at full level, other strokes at half, grace notes quiet and just before their stroke
     * (a flam 30 ms, a drag's two 30 ms apart), a buzz as five fading bounces. The first stroke falls
     * at [LEAD_IN] seconds so the graces before it fit.
     */
    fun render(bpm: Double, rate: Int, passes: Int): FloatArray {
        val slot = slotFrames(bpm, rate)
        val lead = (LEAD_IN * rate).toInt()
        val total = lead + (slot * strokes.size * passes).toInt() + (0.3 * rate).toInt()
        val out = FloatArray(total)
        var seed = 0x2545F491L
        fun hit(at: Int, gain: Float) {
            val len = (0.18 * rate).toInt()
            var prev = 0.0
            for (i in 0 until len) {
                val k = at + i
                if (k !in out.indices) continue
                seed = (seed * 6364136223846793005L + 1442695040888963407L)
                val noise = ((seed ushr 33).toDouble() / (1L shl 31) - 0.5) * 2
                val t = i.toDouble() / rate
                val hp = noise - prev
                prev = noise
                val body = kotlin.math.sin(2 * Math.PI * 185.0 * t) * kotlin.math.exp(-t / 0.025)
                out[k] += (gain * (0.55 * hp * kotlin.math.exp(-t / 0.05) + 0.6 * body)).toFloat()
            }
        }
        for (p in 0 until passes) strokes.forEachIndexed { i, s ->
            if (s.hand == null) return@forEachIndexed
            val at = lead + ((p * strokes.size + i) * slot).toInt()
            val gain = if (s.accent) 1f else 0.5f
            s.grace.forEachIndexed { g, _ -> hit(at - ((s.grace.length - g) * 0.03 * rate).toInt(), 0.2f) }
            if (s.buzz) for (b in 0 until 5) hit(at + (b * slot / 5).toInt(), gain * (1f - b * 0.15f)) else hit(at, gain)
        }
        val peak = out.maxOf { kotlin.math.abs(it) }
        if (peak > 0.9f) for (i in out.indices) out[i] *= 0.9f / peak
        return out
    }

    companion object {
        private fun r(n: Int, name: String, family: String, spb: Int, s: String) =
            Rudiment(n, name, family, spb, s.trim().split(Regex("\\s+")).map(Stroke::parse))

        /** Seconds of silence before the first stroke in [render]. */
        const val LEAD_IN = 0.1

        private const val ROLL = "Roll rudiments"
        private const val DIDDLE = "Diddle rudiments"
        private const val FLAM = "Flam rudiments"
        private const val DRAG = "Drag rudiments"

        /** The 40 PAS International Drum Rudiments (1984), in the PAS order and numbering. */
        val PAS: List<Rudiment> = listOf(
            r(1, "Single Stroke Roll", ROLL, 4, "R L R L"),
            r(2, "Single Stroke Four", ROLL, 6, "R L R >L - - L R L >R - -"),
            r(3, "Single Stroke Seven", ROLL, 6, "R L R L R L >R - - - - - L R L R L R >L - - - - -"),
            r(4, "Multiple Bounce Roll", ROLL, 4, "R~ L~ R~ L~"),
            r(5, "Triple Stroke Roll", ROLL, 6, "R R R L L L"),
            r(6, "Double Stroke Open Roll", ROLL, 4, "R R L L"),
            r(7, "Five Stroke Roll", ROLL, 8, "R R L L >R - - - L L R R >L - - -"),
            r(8, "Six Stroke Roll", ROLL, 8, ">R - L L R R >L -"),
            r(9, "Seven Stroke Roll", ROLL, 8, "R R L L R R >L - L L R R L L >R -"),
            r(10, "Nine Stroke Roll", ROLL, 8, "R R L L R R L L >R - - - - - - -"),
            r(11, "Ten Stroke Roll", ROLL, 8, "R R L L R R L L >R - - - >L - - -"),
            r(12, "Eleven Stroke Roll", ROLL, 8, "R R L L R R L L R R >L - - - - -"),
            r(13, "Thirteen Stroke Roll", ROLL, 8, "R R L L R R L L R R L L >R - - -"),
            r(14, "Fifteen Stroke Roll", ROLL, 8, "R R L L R R L L R R L L R R >L -"),
            r(15, "Seventeen Stroke Roll", ROLL, 8, "R R L L R R L L R R L L R R L L >R - - - - - - -"),
            r(16, "Single Paradiddle", DIDDLE, 4, ">R L R R >L R L L"),
            r(17, "Double Paradiddle", DIDDLE, 6, ">R L R L R R >L R L R L L"),
            r(18, "Triple Paradiddle", DIDDLE, 4, ">R L R L R L R R >L R L R L R L L"),
            r(19, "Single Paradiddle-Diddle", DIDDLE, 6, ">R L R R L L"),
            r(20, "Flam", FLAM, 2, "lR rL"),
            r(21, "Flam Accent", FLAM, 6, ">lR L R >rL R L"),
            r(22, "Flam Tap", FLAM, 4, ">lR R >rL L"),
            r(23, "Flamacue", FLAM, 4, "lR >L R L lR - - -"),
            r(24, "Flam Paradiddle", FLAM, 4, ">lR L R R >rL R L L"),
            r(25, "Single Flammed Mill", FLAM, 4, ">lR R L R >rL L R L"),
            r(26, "Flam Paradiddle-Diddle", FLAM, 6, ">lR L R R L L >rL R L L R R"),
            r(27, "Pataflafla", FLAM, 4, "lR L R rL rL R L lR"),
            r(28, "Swiss Army Triplet", FLAM, 6, ">lR R L >lR R L"),
            r(29, "Inverted Flam Tap", FLAM, 4, ">lR L >rL R"),
            r(30, "Flam Drag", FLAM, 6, ">lR llR L >rL rrL R"),
            r(31, "Drag", DRAG, 4, "llR - rrL -"),
            r(32, "Single Drag Tap", DRAG, 4, "llR >L rrL >R"),
            r(33, "Double Drag Tap", DRAG, 6, "llR llR >L rrL rrL >R"),
            r(34, "Lesson 25", DRAG, 6, "llR L >R rrL R >L"),
            r(35, "Single Dragadiddle", DRAG, 4, ">rR L R R >lL R L L"),
            r(36, "Drag Paradiddle #1", DRAG, 6, ">R llR L R R - >L rrL R L L -"),
            r(37, "Drag Paradiddle #2", DRAG, 4, ">R llR llR L R R - - >L rrL rrL R L L - -"),
            r(38, "Single Ratamacue", DRAG, 4, "llR L R >L rrL R L >R"),
            r(39, "Double Ratamacue", DRAG, 6, "llR llR L R >L - rrL rrL R L >R -"),
            r(40, "Triple Ratamacue", DRAG, 4, "llR llR llR L R >L - - rrL rrL rrL R L >R - -"),
        )
    }
}
