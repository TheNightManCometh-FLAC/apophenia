package com.betterappmafia.apophenia.core.analysis

import com.betterappmafia.apophenia.core.analysis.PerformanceCheck.Issue
import com.betterappmafia.apophenia.core.analysis.PerformanceCheck.RefNote
import com.betterappmafia.apophenia.core.analysis.PerformanceCheck.Why
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerformanceCheckTest {
    // "Mary had a little lamb" opening at 120 BPM, a held E, a rest, then G and a long C.
    private val tune = listOf(
        RefNote(0.0, 0.5, 64), RefNote(0.5, 1.0, 62), RefNote(1.0, 1.5, 60), RefNote(1.5, 2.0, 62),
        RefNote(2.0, 2.5, 64), RefNote(2.5, 3.0, 64), RefNote(3.0, 4.0, 64),
        RefNote(5.0, 5.5, 67), RefNote(5.5, 6.5, 72),
    )

    /**
     * A sung take of [tune]: [stretch] slower or faster, starting [offset] s in, [shift] semitones, a short breath
     * before each note, small random wobble. [bend] adds semitones to note k at t seconds into it; [missing] notes are
     * silent; [late] moves a note's start later by seconds (its end stays).
     */
    private fun sing(
        stretch: Double = 1.0, offset: Double = 0.2, shift: Int = 0,
        bend: (Int, Double) -> Double = { _, _ -> 0.0 }, missing: Set<Int> = emptySet(), late: Map<Int, Double> = emptyMap(),
        extra: Triple<Double, Double, Int>? = null,
    ): PitchCurve {
        val hop = 0.02
        val end = offset + tune.last().end * stretch + 0.5
        val r = Random(5)
        val out = DoubleArray((end / hop).toInt()) { Double.NaN }
        for (f in out.indices) {
            val t = f * hop
            for ((k, n) in tune.withIndex()) {
                if (k in missing) continue
                val s = offset + n.start * stretch + (late[k] ?: 0.0) + 0.03
                val e = offset + n.end * stretch - 0.04
                if (t in s..e) out[f] = n.midi + shift + bend(k, t - s) + (r.nextDouble() - 0.5) * 0.08
            }
            extra?.let { (s, e, m) -> if (t in s..e) out[f] = m.toDouble() }
        }
        return PitchCurve(hop, 0.0, out)
    }

    @Test fun a_clean_take_scores_100_with_nothing_to_report() {
        val r = PerformanceCheck.check(tune, sing())
        assertEquals(100, r.score, r.notes.filter { !it.good }.toString())
        assertEquals(0, r.octaveShift)
        assertTrue(r.extra.isEmpty())
    }

    @Test fun a_slower_later_take_is_not_wrong_and_an_octave_down_is_found() {
        for ((name, take) in listOf("12% slower, 0.9 s late" to sing(stretch = 1.12, offset = 0.9), "8% faster" to sing(stretch = 0.92), "an octave down" to sing(shift = -12))) {
            val r = PerformanceCheck.check(tune, take)
            assertEquals(100, r.score, "$name: " + r.notes.filter { !it.good }.joinToString { "${it.ref} ${it.issues} ${it.cents} ${it.timingMs}" })
            assertEquals(if (name == "an octave down") -12 else 0, r.octaveShift, name)
        }
    }

    @Test fun sharp_and_wrong_notes_are_named_with_how_far_off() {
        val r = PerformanceCheck.check(tune, sing(bend = { k, _ -> when (k) { 1 -> 0.4; 2 -> -2.0; else -> 0.0 } }))
        val d = r.notes[1]
        assertEquals(setOf(Issue.SHARP), d.issues)
        assertTrue(abs(d.cents!! - 40) <= 6, "cents ${d.cents}")
        assertEquals(setOf(Issue.WRONG_NOTE), r.notes[2].issues)
        assertEquals(-2, r.notes[2].wrongBy)
        assertTrue(r.notes.filterIndexed { k, _ -> k !in setOf(1, 2) }.all { it.good }, r.notes.toString())
        assertEquals(78, r.score)
    }

    @Test fun a_missed_note_and_a_late_one_are_reported() {
        val r = PerformanceCheck.check(tune, sing(missing = setOf(3), late = mapOf(7 to 0.16)))
        assertEquals(setOf(Issue.MISSED), r.notes[3].issues)
        assertTrue(Issue.LATE in r.notes[7].issues, r.notes[7].toString())
        assertTrue(r.notes[7].timingMs!! in 110..190, "ms ${r.notes[7].timingMs}")
        assertTrue(r.notes.filterIndexed { k, _ -> k != 3 && k != 7 }.all { it.good }, r.notes.toString())
    }

    @Test fun the_why_scoop_drift_and_off_centre_vibrato() {
        val r = PerformanceCheck.check(tune, sing(bend = { k, t ->
            when (k) {
                6 -> if (t < 0.1) -1.0 else 0.0                       // scooped into the held E
                8 -> -0.6 * t                                        // the long C sinks
                else -> 0.0
            }
        }))
        assertTrue(Why.SCOOP in r.notes[6].why, r.notes[6].toString())
        assertTrue(Why.DRIFT_FLAT in r.notes[8].why, r.notes[8].toString())
        val vib = PerformanceCheck.check(tune, sing(bend = { k, t -> if (k == 6) 0.4 + 0.3 * sin(2 * Math.PI * 5.5 * t) else 0.0 }))
        assertTrue(Why.VIBRATO_OFF in vib.notes[6].why, vib.notes[6].toString())
        assertTrue(Issue.SHARP in vib.notes[6].issues)
    }

    @Test fun rushing_shows_across_a_run_of_notes() {
        // Notes 1–4 each come in further ahead than the one before.
        val r = PerformanceCheck.check(tune, sing(late = mapOf(1 to -0.06, 2 to -0.12, 3 to -0.19, 4 to -0.26)))
        assertTrue((2..4).all { Why.RUSHING in r.notes[it].why }, r.notes.map { it.timingMs to it.why }.toString())
    }

    @Test fun something_sung_in_the_rest_is_an_extra_note() {
        val r = PerformanceCheck.check(tune, sing(extra = Triple(4.45, 4.85, 65)))
        assertEquals(listOf(65), r.extra.map { it.midi }, r.notes.drop(5).joinToString(" | ") { "${it.ref.midi}@${it.ref.start} ${it.takeStart} ${it.takeEnd} ${it.issues} ${it.cents} ${it.wrongBy}" })
    }

    @Test fun a_chord_reference_is_followed_on_its_top_line() {
        val chords = listOf(RefNote(0.0, 1.0, 60), RefNote(0.0, 1.0, 64), RefNote(0.0, 1.0, 67), RefNote(1.0, 2.0, 62), RefNote(1.0, 2.0, 65))
        assertEquals(listOf(67, 65), PerformanceCheck.melody(chords).map { it.midi })
    }
}
