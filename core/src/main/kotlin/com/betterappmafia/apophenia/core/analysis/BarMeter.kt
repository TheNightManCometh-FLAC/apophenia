package com.betterappmafia.apophenia.core.analysis

import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import kotlin.math.abs

/**
 * The meter of a take recorded without the click, read from the downbeats [BeatThis] found (Christopher, 30 Oct 2026:
 * "focus on that. its important"). Two questions:
 * - How many beats a bar: each downbeat is numbered by the beat it falls on, and a bar of L beats is read when most
 *   downbeats fall on the same beat of every L (6, then 4, 3, 2). Beat This puts in stray downbeats (a piano waltz
 *   in the tests got bars of 3, 3, 3, 3, 2, 1, 1, 2…); voting outlasts them, counting the beats between neighbours
 *   doesn't. Three is 3/4, four is 4/4, two is 2/2 (Beat This often counts a 4/4 bar in two half-note beats), six is
 *   6/8 counted in eighths ([Reading.step] 3: every third beat is the dotted-quarter beat).
 * - Does each beat split in three or in two ([triple]): a bar of two or four beats that split in three is 6/8 or
 *   12/8. Beat This counts a 6/8 bar as two dotted-quarter beats, the same count as a 4/4 bar in half notes, so the
 *   split is the only thing telling them apart.
 * When neither is clear, null: the chart stays in 4/4 and says the meter is a guess.
 *
 * Measured 30 Oct 2026 with this code on Beat This small0's beats (`docs/STATUS.md` 0.9.37): GuitarSet's 360 takes
 * (all 4/4), the same takes with beat 4 of every bar cut out (3/4), and 58 piano renders (the app's Iowa samples) in
 * 3/4, 4/4 and 6/8. No 4/4 take was read as 3/4.
 */
object BarMeter {
    /**
     * [perBar] beats a bar, with [agree] of [downbeats] downbeats on the same beat of the bar; [triple] as in [triple];
     * [start] the first bar line (the first beat on that beat of the bar); [step] how many found beats make one beat
     * of [meter] (3 when 6/8 was counted in eighths, else 1).
     */
    data class Reading(val meter: TimeSignature, val perBar: Int, val agree: Int, val downbeats: Int, val triple: Double, val start: Double, val step: Int = 1) {
        /** For the analysis text: "3 beats a bar, 14 of 15 downbeats agree". */
        val why: String get() = when {
            step > 1 -> "6 eighths a bar, $agree of $downbeats downbeats agree"
            else -> "$perBar beats a bar, $agree of $downbeats downbeats agree" + if (meter.isCompound) ", each beat split in three" else ""
        }

        /**
         * The beats of [meter] among [beats], from [start]. With [step] > 1, each next one is the found beat nearest
         * [step] median beats on (a missed eighth would otherwise shift every bar after it), or that spot itself
         * when no found beat is within half a beat of it.
         */
        fun beatsOf(beats: List<Double>): List<Double> {
            val from = beats.filter { it >= start - 1e-6 }
            if (step == 1 || from.size < 2) return from
            val gaps = from.zipWithNext { a, b -> b - a }.sorted()
            val g = gaps[gaps.size / 2]
            val out = mutableListOf(from.first())
            while (true) {
                val target = out.last() + step * g
                if (target > from.last() + g / 2) break
                val near = from.minBy { abs(it - target) }
                out += if (abs(near - target) < g / 2) near else target
            }
            return out
        }
    }

    /** At least this many downbeats on beats before a meter is read. [Claude] 6; 5 or 8 changed fewer than ten of the 1,138 readings. */
    const val MIN_DOWNBEATS = 6
    /**
     * Of the downbeats, at least this share on the same beat of a 4- or 3-beat bar. Measured: 0.75 read the most 3/4
     * right (45 of the 360 cut GuitarSet takes, 19 of 26 piano) with no 4/4 take read as 3/4; 0.9 read 27 and 15.
     */
    const val MIN_SHARE = 0.75
    /**
     * A 2-beat bar needs more: any downbeats land half on one beat of two by chance. Measured: at 0.75, 42 of 340 cut
     * 3/4 takes read as two; at 0.9, 12 of 360.
     */
    const val MIN_SHARE_TWO = 0.9
    /**
     * [triple] at or above this: the beats split in three. Measured: piano 6/8 counted in two or four beats 0.74–0.77;
     * piano 4/4 0.23–0.54; GuitarSet 4/4 up to 0.68 (swung jazz 0.60–0.67) except five singer-songwriter takes at
     * 0.76–0.88, strummed in triplets (their annotated notes on thirds of the beat, almost none on halves), read 12/8.
     */
    const val TRIPLE = 0.7
    /** A downbeat is on a beat when within this many seconds of it. */
    private const val ON_BEAT = 0.07

    /** The beat each downbeat falls on (index into [beats]), skipping downbeats on none. */
    fun onBeats(beats: List<Double>, downbeats: List<Double>): List<Int> = downbeats.mapNotNull { d ->
        val i = beats.indices.minByOrNull { abs(beats[it] - d) } ?: return@mapNotNull null
        i.takeIf { abs(beats[i] - d) < ON_BEAT }
    }

    /**
     * How strongly the beats split in three rather than two: onset strength at 1/3 and 2/3 of each beat against twice
     * the strength at 1/2, as a share (0.5 is neither; 1 is only threes). Each spot is the strongest frame within 4% of
     * the beat around it. Null without two usable beats.
     */
    fun triple(env: Onsets.Envelope, beats: List<Double>): Double? {
        val s = env.strength
        // Frame i's centre is (i * HOP + FRAME / 2) samples in.
        val offset = Onsets.FRAME / 2.0 / Onsets.HOP
        fun at(t: Double, w: Double): Double {
            val c = t / env.hopSeconds - offset
            val lo = maxOf(0, (c - w).toInt()); val hi = minOf(s.size - 1, (c + w).toInt() + 1)
            var m = 0.0
            for (i in lo..hi) if (s[i] > m) m = s[i].toDouble()
            return m
        }
        var three = 0.0; var two = 0.0; var n = 0
        for ((a, b) in beats.zipWithNext()) {
            val len = b - a
            if (len < 0.2 || len > 2.0) continue
            val w = maxOf(1.0, 0.04 * len / env.hopSeconds)
            three += at(a + len / 3, w) + at(a + 2 * len / 3, w)
            two += 2 * at(a + len / 2, w)
            n++
        }
        return if (n == 0 || three + two <= 0) null else three / (three + two)
    }

    fun read(found: BeatThis.Track, env: Onsets.Envelope): Reading? {
        val idx = onBeats(found.beats, found.downbeats)
        if (idx.size < MIN_DOWNBEATS) return null
        // For each bar length, the beat of the bar most downbeats fall on, and how many.
        fun vote(len: Int) = idx.groupingBy { it % len }.eachCount().maxBy { it.value }
        // Six first: a 6/8 bar counted in eighths also has its downbeats on every third beat. [Claude] Six never won
        // on the 1,138 measured takes; it catches 6/8 counted in eighths (the 6/8 piano test).
        val (len, phase) = listOf(6, 4, 3, 2).firstNotNullOfOrNull { len ->
            val (p, n) = vote(len)
            (len to p to n).takeIf { n >= (if (len == 2) MIN_SHARE_TWO else MIN_SHARE) * idx.size }
        }?.let { (lp, _) -> lp } ?: return null
        val agree = vote(len).value
        val split = triple(env, found.beats) ?: return null
        val meter = when (len) {
            6 -> TimeSignature.of(6, 8)
            3 -> TimeSignature.of(3, 4)
            4 -> if (split >= TRIPLE) TimeSignature.of(12, 8) else TimeSignature.of(4, 4)
            // [Claude] Two plain beats a bar is written 2/2: the beats are half notes (Beat This counting a 4/4 bar in
            // two), so the bars, the chart and the tab's note lengths come out right at the tempo those beats give.
            else -> if (split >= TRIPLE) TimeSignature.of(6, 8) else TimeSignature.of(2, 2)
        }
        val start = found.beats[found.beats.indices.first { it % len == phase }]
        return Reading(meter, len, agree, idx.size, split, start, if (len == 6) 3 else 1)
    }
}
