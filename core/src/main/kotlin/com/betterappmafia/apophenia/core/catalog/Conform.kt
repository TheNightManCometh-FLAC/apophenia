package com.betterappmafia.apophenia.core.catalog

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * How much a stack layer is stretched and shifted to fit the stack (plan §5.3: loop and one-shot
 * layers conform automatically, amounts shown, one tap turns it off; free-time layers never; a layer
 * with unknown key isn't pitch-shifted).
 *
 * [lengthFactor] multiplies the layer's length (above 1 = slower); [semitones] shifts its pitch.
 */
data class Conform(val lengthFactor: Double, val semitones: Int, val fromBpm: Double?, val toBpm: Double?, val fold: Int) {
    val isIdentity: Boolean get() = abs(lengthFactor - 1.0) < 1e-6 && semitones == 0

    /** "+2 st · 92→96 BPM", "−1 st", "180→96 BPM (½×)", or "" when nothing changes. */
    val label: String
        get() = listOfNotNull(
            semitones.takeIf { it != 0 }?.let { (if (it > 0) "+" else "−") + abs(it) + " st" },
            if (fromBpm != null && toBpm != null && abs(lengthFactor - 1.0) >= 1e-6)
                "${fmt(fromBpm)}→${fmt(toBpm)} BPM" + when (fold) { 1 -> " (½×)"; -1 -> " (2×)"; else -> "" } else null,
        ).joinToString(" · ")

    companion object {
        val NONE = Conform(1.0, 0, null, null, 0)

        private fun fmt(b: Double) = if (abs(b - b.roundToInt()) < 0.05) b.roundToInt().toString() else "%.1f".format(b)

        /**
         * [Claude] Tempo: the layer's BPM (the middle of a range) against the stack's, taking whichever
         * of straight, half-time or double-time needs the least stretch (a 184 BPM riff in a 92 BPM
         * stack is double-time, not twice as slow). Key: the layer's home note moved to the stack's by
         * the shortest way (a tritone goes down); the mode isn't compared, so A minor into C major
         * moves up 3. Unknown or free-time values leave that part alone.
         */
        fun of(layerBpmLow: Double?, layerBpmHigh: Double?, layerFreeTime: Boolean, layerKey: KeyMode?, stackBpm: Double?, stackKey: KeyMode?): Conform {
            val layerBpm = layerBpmLow?.let { lo -> (lo + (layerBpmHigh ?: lo)) / 2 }
            var factor = 1.0
            var fold = 0
            if (!layerFreeTime && layerBpm != null && layerBpm > 0 && stackBpm != null && stackBpm > 0) {
                val candidates = listOf(0 to layerBpm, 1 to layerBpm / 2, -1 to layerBpm * 2)
                val (f, effective) = candidates.minBy { (_, b) -> abs(ln(b / stackBpm)) }
                fold = f
                factor = effective / stackBpm
            }
            var st = 0
            if (layerKey != null && stackKey != null) {
                st = Math.floorMod(stackKey.tonic.pitchClass - layerKey.tonic.pitchClass, 12)
                if (st >= 6) st -= 12
            }
            val tempoChanged = abs(factor - 1.0) >= 1e-6
            return Conform(factor, st, if (tempoChanged) layerBpm else null, if (tempoChanged) stackBpm else null, if (tempoChanged) fold else 0)
        }
    }
}
