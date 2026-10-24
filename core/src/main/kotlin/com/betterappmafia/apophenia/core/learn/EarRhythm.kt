package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.rhythm.Duration
import com.betterappmafia.apophenia.core.rhythm.Rational
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import com.betterappmafia.apophenia.core.tools.DrumHit
import com.betterappmafia.apophenia.core.tools.DrumVoice
import kotlin.math.abs
import kotlin.random.Random

/**
 * Rhythm for the Ear (plan §9 "Rhythm"): dictation (hear one bar, pick it from three written bars) in simple meters,
 * with triplets and other tuplets, and in odd and additive meters; straight or swung; and tapping a bar back, scored
 * in milliseconds. [Claude] Bars are built from common one-beat (or one-group) cells, not taken from songs.
 */
data class RNote(val d: Duration, val rest: Boolean = false)

/** A drum hit at [at] seconds. */
data class DrumAt(val at: Double, val voice: DrumVoice, val hit: DrumHit)

/** A tap-back result: [ok] when every note was tapped within [EarRhythm.TAP_WINDOW_MS] of where it falls once your own constant delay is removed. */
data class TapResult(val ok: Boolean, val offsetMs: Int, val errorsMs: List<Int>, val expected: Int, val tapped: Int)

object EarRhythm {
    const val TAP_WINDOW_MS = 60
    const val OPTIONS = 3

    private val q = Duration.QUARTER
    private val e = Duration.EIGHTH
    private val s = Duration.SIXTEENTH
    private fun n(d: Duration) = RNote(d)
    private fun r(d: Duration) = RNote(d, rest = true)

    /** One-quarter-beat cells. */
    private val SIMPLE = listOf(
        listOf(n(q)), listOf(n(e), n(e)), listOf(r(e), n(e)), listOf(n(e.dotted()), n(s)), listOf(n(s), n(s), n(s), n(s)),
        listOf(n(e), n(s), n(s)), listOf(n(s), n(s), n(e)), listOf(r(q)),
    )
    /** Cells a tapper can manage at 80 BPM: quarters, eighths and rests. */
    private val TAPPABLE = listOf(listOf(n(q)), listOf(n(e), n(e)), listOf(r(e), n(e)), listOf(n(q)), listOf(r(q)))
    private val TUPLETS = listOf(
        List(3) { n(e.inTuplet(3, 2)) }, listOf(n(q.inTuplet(3, 2)), n(e.inTuplet(3, 2))), listOf(r(e.inTuplet(3, 2)), n(e.inTuplet(3, 2)), n(e.inTuplet(3, 2))),
        List(5) { n(s.inTuplet(5, 4)) }, List(6) { n(s.inTuplet(6, 4)) },
    )
    private val GROUP2 = listOf(listOf(n(q)), listOf(n(e), n(e)), listOf(n(s), n(s), n(e)), listOf(n(e), n(s), n(s)), listOf(r(e), n(e)))
    private val GROUP3 = listOf(listOf(n(q.dotted())), listOf(n(q), n(e)), listOf(n(e), n(q)), listOf(n(e), n(e), n(e)), listOf(r(e), n(e), n(e)), listOf(n(e.dotted()), n(s), n(e)))

    val drills: List<EarDrill> = listOf(
        EarDrill("rh-simple", "Rhythm", "Rhythm · simple meters", EarKind.RHYTHM, listOf("0", "1", "2"), "HEAR A BAR, PICK IT · 2/4 3/4 4/4"),
        EarDrill("rh-tuplets", "Rhythm", "Rhythm · triplets and tuplets", EarKind.RHYTHM, listOf("0", "1", "2"), "TRIPLETS, QUINTUPLETS, SEXTUPLETS"),
        EarDrill("rh-odd", "Rhythm", "Rhythm · odd and additive meters", EarKind.RHYTHM, listOf("0", "1", "2"), "5/8 · 7/8 · 9/8"),
        EarDrill("rh-swing", "Rhythm", "Straight or swung", EarKind.SWING, listOf("straight", "swung"), "EIGHTHS PLAYED EVEN, OR LONG-SHORT"),
        EarDrill("rh-tap", "Rhythm", "Tap it back", EarKind.TAPBACK, listOf("tap"), "HEAR A BAR, TAP IT IN THE NEXT"),
    )

    /** The meter a drill's question is in. */
    fun meter(drill: EarDrill, random: Random): TimeSignature = when (drill.id) {
        "rh-odd" -> listOf(TimeSignature(listOf(3, 2), 8), TimeSignature(listOf(2, 3), 8), TimeSignature(listOf(2, 2, 3), 8), TimeSignature(listOf(3, 2, 2), 8), TimeSignature(listOf(2, 2, 2, 3), 8))[random.nextInt(5)]
        "rh-simple" -> listOf(TimeSignature.of(2, 4), TimeSignature.of(3, 4), TimeSignature.of(4, 4), TimeSignature.of(4, 4))[random.nextInt(4)]
        else -> TimeSignature.of(4, 4)
    }

    /** One bar: a cell per beat (per group of eighths in an odd meter); a tuplet bar has at least one tuplet beat. */
    fun bar(drill: EarDrill, meter: TimeSignature, random: Random): List<RNote> {
        val out = ArrayList<RNote>()
        if (meter.denominator == 8) for (g in meter.groups) out += (if (g == 3) GROUP3 else GROUP2).let { it[random.nextInt(it.size)] }
        else {
            val tupletBeat = random.nextInt(meter.groups.size)
            for (beat in meter.groups.indices) out += when {
                drill.id == "rh-tuplets" && (beat == tupletBeat || random.nextInt(3) == 0) -> TUPLETS[random.nextInt(TUPLETS.size)]
                drill.kind == EarKind.TAPBACK -> TAPPABLE[random.nextInt(TAPPABLE.size)]
                else -> SIMPLE[random.nextInt(SIMPLE.size)]
            }
        }
        // A bar that's all rests can't be heard; start it with a note.
        if (out.all { it.rest }) out[0] = out[0].copy(rest = false)
        return out
    }

    /** Where each note (not rest) starts, as a fraction of a whole note from the barline. */
    fun onsets(bar: List<RNote>): List<Rational> {
        var at = Rational.ZERO
        val out = ArrayList<Rational>()
        for (x in bar) { if (!x.rest) out += at; at += x.d.length }
        return out
    }

    /** The question's options: the right bar at [answer]'s index and distinct others in the same meter. */
    fun options(drill: EarDrill, meter: TimeSignature, answer: Int, random: Random): List<List<RNote>> {
        val right = bar(drill, meter, random)
        val all = arrayListOf(right)
        var tries = 0
        while (all.size < OPTIONS && tries++ < 200) {
            val other = bar(drill, meter, random)
            if (all.none { onsets(it) == onsets(other) && it.map { x -> x.d } == other.map { x -> x.d } }) all += other
        }
        while (all.size < OPTIONS) all += right
        val others = all.drop(1)
        return (0 until OPTIONS).map { i -> if (i == answer) right else others[if (i < answer) i else i - 1] }
    }

    fun secondsPerWhole(bpm: Int) = 4 * 60.0 / bpm

    /** A bar on the snare with the beats on the hi-hat, starting at [start] seconds. [swing] delays offbeat eighths to 2/3 of the beat. */
    fun drums(bar: List<RNote>, meter: TimeSignature, bpm: Int, start: Double, swing: Boolean = false, hats: Boolean = true): List<DrumAt> {
        val w = secondsPerWhole(bpm)
        val out = ArrayList<DrumAt>()
        if (hats) {
            var b = Rational.ZERO
            meter.groups.forEachIndexed { i, g -> out += DrumAt(start + b.toDouble() * w, DrumVoice.HAT, if (i == 0) DrumHit.ACCENT else DrumHit.HIT); b += Rational.of(g, meter.denominator) }
        }
        for (o in onsets(bar)) {
            val beat = Rational.of(1, 4)
            val inBeat = o.toDouble() % beat.toDouble()
            val t = if (swing && abs(inBeat - 0.125) < 1e-9) o.toDouble() - 0.125 + 0.25 * 2 / 3 else o.toDouble()
            out += DrumAt(start + t * w, DrumVoice.SNARE, DrumHit.HIT)
        }
        return out
    }

    /** A count-in bar of clicks on the beats. */
    fun countIn(meter: TimeSignature, bpm: Int): List<DrumAt> {
        val w = secondsPerWhole(bpm)
        var b = Rational.ZERO
        return meter.groups.mapIndexed { i, g -> DrumAt(b.toDouble() * w, DrumVoice.CROSS_STICK, if (i == 0) DrumHit.ACCENT else DrumHit.HIT).also { b += Rational.of(g, meter.denominator) } }
    }

    fun barSeconds(meter: TimeSignature, bpm: Int) = Rational.of(meter.numerator, meter.denominator).toDouble() * secondsPerWhole(bpm)

    /** An even bar of eighths with a few rests, for straight-or-swung. */
    fun swingBar(random: Random): List<RNote> = (0 until 8).map { i -> if (i in 2..6 && random.nextInt(5) == 0) r(e) else n(e) }

    /**
     * Tap-back: pairs taps with notes in order. The mean difference is your constant delay (the phone's output and touch
     * latency plus your own anticipation) and is taken out; each note's error is what's left. [Claude]
     */
    fun grade(expected: List<Double>, taps: List<Double>): TapResult {
        if (taps.size != expected.size || taps.isEmpty()) return TapResult(false, 0, emptyList(), expected.size, taps.size)
        val diffs = expected.indices.map { (taps[it] - expected[it]) * 1000 }
        val offset = diffs.average()
        val errors = diffs.map { kotlin.math.round(it - offset).toInt() }
        return TapResult(errors.all { abs(it) <= TAP_WINDOW_MS }, kotlin.math.round(offset).toInt(), errors, expected.size, taps.size)
    }
}

/** The Ear's rhythm questions: built, played, named and explained. */
object EarRhythmQuestions {
    fun bpm(drill: EarDrill) = when (drill.id) { "rh-tuplets" -> 72; "rh-odd" -> 100; "rh-swing" -> 100; else -> 80 }

    fun question(drill: EarDrill, answer: String, full: String, root: com.betterappmafia.apophenia.core.pitch.PitchName, random: Random): EarQuestion {
        val bpm = bpm(drill)
        return when (drill.kind) {
            EarKind.RHYTHM -> {
                val meter = EarRhythm.meter(drill, random)
                EarQuestion(drill, full, emptyList(), root, EarDirection.UP, bpm = bpm, rhythm = EarRhythm.options(drill, meter, answer.toInt(), random), meter = meter)
            }
            EarKind.SWING -> EarQuestion(drill, full, emptyList(), root, EarDirection.UP, bpm = bpm, rhythm = listOf(EarRhythm.swingBar(random)), meter = TimeSignature.of(4, 4))
            else -> {
                val meter = TimeSignature.of(4, 4)
                EarQuestion(drill, full, emptyList(), root, EarDirection.UP, bpm = bpm, rhythm = listOf(EarRhythm.bar(drill, meter, random)), meter = meter)
            }
        }
    }

    fun audio(q: EarQuestion): EarAudio {
        val (src, a) = Ear.source(q.drill, q.answer)
        val meter = q.meter!!
        val bar = EarRhythm.barSeconds(meter, q.bpm)
        val count = EarRhythm.countIn(meter, q.bpm)
        return when (src.kind) {
            EarKind.RHYTHM -> EarAudio(emptyList(), drums = count + EarRhythm.drums(q.rhythm[a.toInt()], meter, q.bpm, bar))
            EarKind.SWING -> EarAudio(emptyList(), drums = count + (1..2).flatMap { EarRhythm.drums(q.rhythm[0], meter, q.bpm, bar * it, swing = a == "swung") })
            else -> EarAudio(emptyList(), drums = count + EarRhythm.drums(q.rhythm[0], meter, q.bpm, bar) + EarRhythm.drums(emptyList(), meter, q.bpm, 2 * bar))
        }
    }

    /** Tap-back: where each note should be tapped, in seconds from the start of the audio (the third bar). */
    fun expected(q: EarQuestion): List<Double> {
        val bar = EarRhythm.barSeconds(q.meter!!, q.bpm)
        return EarRhythm.onsets(q.rhythm[0]).map { 2 * bar + it.toDouble() * EarRhythm.secondsPerWhole(q.bpm) }
    }

    fun label(drill: EarDrill, answer: String) = when (drill.kind) {
        EarKind.RHYTHM -> "ABC"[answer.toInt()].toString()
        EarKind.SWING -> answer.replaceFirstChar(Char::uppercase)
        else -> "Tap"
    }

    fun explain(q: EarQuestion): String {
        val (src, a) = Ear.source(q.drill, q.answer)
        return when (src.kind) {
            EarKind.RHYTHM -> "Bar ${label(src, a)}, in ${q.meter} at ${q.bpm} BPM (a quarter note), after a bar of clicks."
            EarKind.SWING -> if (a == "swung") "Swung: each offbeat eighth lands two thirds of the way through the beat (2:1)." else "Straight: the eighths are even."
            else -> "Tap the bar back in the third bar, after it plays."
        }
    }
}
