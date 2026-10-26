package com.betterappmafia.apophenia.core.analysis

import kotlin.math.abs

/**
 * Overdub latency by playing along (Christopher, 26 Oct 2026: Bluetooth headphones are the standard; you hear the
 * instrument in the room, the recording is shifted to line up). Clicks play in the headphones and you clap (or tap the
 * phone) with them; the phone's mic hears the claps. How late each clap lands after its click, in the frames a layer
 * recorded over a stack is placed by, is the delay out to your ears plus the delay back in through the mic: exactly
 * what a layer must be moved earlier by. Your own clapping error averages out over the claps.
 */
object PlayAlong {
    /** The clicks, the claps found, and the lead-in clicks not used (you find the beat first). [Claude] */
    const val CLICKS = 16
    const val LEAD_IN = 3
    const val BPM = 80.0

    /** A clap may come this long before its click (you anticipate) or after it (the round trip): within one beat at [BPM]. */
    private const val EARLY = 0.15
    private const val LATE = 0.6
    /** After the first estimate, a clap counts if it's this close to click + delay. */
    private const val CLOSE = 0.12

    /** At least this many claps must be found, and half the middle spread of them under [STEADY_MS], to trust it. [Claude] */
    const val NEEDED = 6
    const val STEADY_MS = 30.0

    /** [ms] the delay (median), [spreadMs] half the spread of the middle half of the claps, [used] claps matched. */
    data class Result(val ms: Double?, val spreadMs: Double?, val used: Int, val problem: String?)

    /** [clicks] and [claps] in seconds of the same recording. */
    fun measure(clicks: List<Double>, claps: List<Double>): Result {
        val heard = clicks.sorted().drop(LEAD_IN)
        val c = claps.sorted()
        // First pass: each click's first clap in its window.
        val first = heard.mapNotNull { t -> c.firstOrNull { it >= t - EARLY && it < t + LATE }?.minus(t) }
        if (first.size < NEEDED) return Result(null, null, first.size, "Only ${first.size} claps were heard. Clap louder, on every click, with the earbuds in.")
        val guess = median(first)
        // Second pass: the clap nearest click + delay, each clap used once.
        val used = HashSet<Int>()
        val lags = heard.mapNotNull { t ->
            val k = c.indices.filter { it !in used && abs(c[it] - (t + guess)) < CLOSE }.minByOrNull { abs(c[it] - (t + guess)) } ?: return@mapNotNull null
            used += k
            c[k] - t
        }
        if (lags.size < NEEDED) return Result(null, null, lags.size, "Only ${lags.size} claps landed in time with the clicks. Try again, clapping on every click.")
        val ms = median(lags) * 1000
        val sorted = lags.sorted()
        val spread = (sorted[sorted.size * 3 / 4] - sorted[sorted.size / 4]) * 1000 / 2
        if (spread > STEADY_MS) return Result(ms, spread, lags.size, "The claps were uneven (±%.0f ms). Try again, as steadily as you can.".format(java.util.Locale.ROOT, spread))
        return Result(ms, spread, lags.size, null)
    }

    private fun median(v: List<Double>) = v.sorted().let { it[it.size / 2] }
}
