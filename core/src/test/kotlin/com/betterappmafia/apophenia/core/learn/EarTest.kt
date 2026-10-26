package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.catalog.Chords
import com.betterappmafia.apophenia.core.interval.Interval
import com.betterappmafia.apophenia.core.pitch.PitchName
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EarTest {
    @Test
    fun `every drill's answers exist in the catalogs and every question plays the right notes`() {
        val r = Random(7)
        for (d in Ear.drills + Ear.chapters.map(Ear::mixed) + Ear.mixed(null)) repeat(60) {
            val q = Ear.question(d, EarSettings(), r)
            val (src, a) = Ear.source(d, q.answer)
            when (src.kind) {
                EarKind.INTERVAL -> assertEquals(Interval.parse(a).semitones, q.midis[1] - q.midis[0])
                EarKind.CHORD -> assertEquals(Chords.byId(a).semitones, q.midis.map { it - q.midis[0] })
                EarKind.INVERSION -> assertTrue(q.midis.zipWithNext().all { (x, y) -> y > x }, "inversion stays in close position, rising")
                EarKind.VOICING -> {
                    val c = Ear.close(q.midis, a)
                    assertTrue(c.map { it - c[0] } in listOf("maj7", "7", "m7").map { Chords.byId(it).semitones }, "a root-position seventh underneath")
                    assertEquals(q.midis.min(), q.midis[0], "the root is the lowest note"); assertEquals(q.midis.sorted(), q.midis, "listed from the bass up")
                    assertEquals(Ear.voicing(c, a), q.midis)
                    for (o in d.answers.map { Ear.source(d, it).second }.distinct()) if (o in Ear.VOICINGS) assertEquals(c, Ear.close(Ear.asAnswer(q, Ear.source(d, q.answer).first.let { _ -> d.answers.first { Ear.source(d, it).second == o } }).midis, o), "comparing keeps the same chord")
                }
                EarKind.SING -> assertEquals(Interval.parse(a).semitones, q.midis[1] - q.midis[0])
                EarKind.NOTE, EarKind.MATCH -> assertEquals(a.toInt(), q.midis[0] % 12)
                EarKind.OCTAVE -> assertEquals(a.toInt(), q.midis[0] / 12 - 1)
                EarKind.CENTS -> assertEquals(if (a == "up") src.cents else -src.cents, q.cents)
                EarKind.DEGREE -> assertEquals(Math.floorMod(com.betterappmafia.apophenia.core.degree.DegreeToken.parse(a).interval.semitones, 12), q.midis[0] - q.tonic!!)
                EarKind.MODE -> assertEquals(com.betterappmafia.apophenia.core.catalog.Scales.byId(a).semitones + 12, q.midis.map { it - q.tonic!! })
                EarKind.CHARACTER -> assertTrue(q.midis.toSet().size == (if (a == "locrian") 3 else 4), "Locrian's characteristic b5 is also its 5th")
                EarKind.HARMONY -> assertTrue(q.midis.isNotEmpty() && q.tonic != null)
                EarKind.TUNING, EarKind.BEATS, EarKind.PARTIAL -> assertEquals(1, q.midis.size)
                EarKind.MELODY -> assertEquals(a.toInt(), q.midis.size)
                EarKind.BASS -> assertEquals(q.chords.map { it.first() % 12 }, q.midis.map { it % 12 })
                EarKind.CHORDTONE -> assertFalse(q.midis[a.toInt() - 1] % 12 in q.chords.first().map { it % 12 })
                EarKind.TEMPO -> assertEquals(a.toInt(), q.bpm)
                EarKind.RHYTHM -> assertEquals(EarRhythm.OPTIONS, q.rhythm.size)
                EarKind.SWING, EarKind.TAPBACK -> assertEquals(1, q.rhythm.size)
                EarKind.OWN_CHORD, EarKind.OWN_KEY, EarKind.OWN_PHRASE -> error("your-music drills aren't in the fixed list")
            }
            assertTrue(Ear.sound(q, EarSettings()).seconds > 1.5)
            assertTrue(Ear.label(d, q.answer).isNotBlank())
            assertTrue(Ear.explain(q) { it.toString() }.isNotBlank())
        }
    }

    @Test
    fun `the commonly confused intervals come up about twice as often, and a miss comes up more`() {
        val d = Ear.drill("iv-simple")!!
        val r = Random(1)
        val counts = (1..12000).map { Ear.question(d, EarSettings(), r).answer }.groupingBy { it }.eachCount()
        val ratio = counts.getValue("m6").toDouble() / counts.getValue("P4")
        assertTrue(ratio in 1.6..2.4, "m6 against P4 came up $ratio times as often")
        assertEquals(3.0, Ear.weight("P4", mapOf("P4" to 2)))
    }

    @Test
    fun `an inversion puts the asked chord tone in the bass and hearing another answer keeps the root`() {
        assertEquals(listOf(64, 67, 72), Ear.inversion(listOf(60, 64, 67), 1))
        assertEquals(listOf(67, 72, 76), Ear.inversion(listOf(60, 64, 67), 2))
        val q = EarQuestion(Ear.drill("ch-inv")!!, "1", listOf(64, 67, 72), PitchName.parse("C"), EarDirection.UP)
        assertEquals(listOf(67, 72, 76), Ear.asAnswer(q, "2").midis)
        assertEquals(listOf(60, 64, 67), Ear.asAnswer(q, "0").midis)
    }

    @Test
    fun `answers on an instrument check the exact second note of an interval and the pitch classes of a chord`() {
        val up = EarQuestion(Ear.drill("iv-2-3")!!, "m3", listOf(60, 63), PitchName.parse("C"), EarDirection.UP)
        assertTrue(Ear.check(up, listOf(63)))
        assertFalse(Ear.check(up, listOf(75)))
        val down = up.copy(direction = EarDirection.DOWN)
        assertEquals(63, down.given())
        assertTrue(Ear.check(down, listOf(60)))
        val m7 = EarQuestion(Ear.drill("ch-7-basic")!!, "m7", listOf(50, 53, 57, 60), PitchName.parse("D"), EarDirection.UP)
        assertTrue(Ear.check(m7, listOf(50, 65, 69, 72)))
        assertFalse(Ear.check(m7, listOf(50, 54, 57, 60)))
    }

    @Test
    fun `the explanation spells the notes on the question's root`() {
        val q = EarQuestion(Ear.drill("ch-7-basic")!!, "m7", listOf(50, 53, 57, 60), PitchName.parse("D"), EarDirection.UP)
        assertTrue(Ear.explain(q) { it.toString() }.startsWith("Dm7: D F A C."))
        val iv = EarQuestion(Ear.drill("iv-2-3")!!, "m3", listOf(61, 64), PitchName.parse("C#"), EarDirection.DOWN)
        assertTrue(Ear.explain(iv) { it.toString() }.contains("C# up to E"))
    }

    @Test
    fun `the cadence sets the key with I IV V I, its V a major chord with the leading tone even in minor`() {
        val c = Ear.cadence(60, minor = true)
        val v = c.filter { it.start == 1.5 }.map { it.midi % 12 }.toSet()
        assertEquals(setOf(11, 2, 7), v)
        val iv = c.filter { it.start == 0.75 }.map { it.midi % 12 }.toSet()
        assertEquals(setOf(5, 8, 0), iv)
    }

    @Test
    fun `a sung note counts in any octave within 30 cents of its target`() {
        val q = EarQuestion(Ear.drill("pitch-match")!!, "9", listOf(57), PitchName.parse("A"), EarDirection.UP)
        assertEquals(true to 0, Ear.grade(q, 220.0))
        assertTrue(Ear.grade(q, 440.0).first)
        assertEquals(false, Ear.grade(q, 233.08).first)
        val sing = EarQuestion(Ear.drill("iv-sing")!!, "M3", listOf(57, 61), PitchName.parse("A"), EarDirection.UP)
        assertTrue(Ear.grade(sing, 277.18).first)
        assertEquals(false, Ear.grade(sing, 261.63).first)
    }

    @Test
    fun `cents questions play two pure tones the asked distance apart, and a drone sounds under modes`() {
        val q = Ear.question(Ear.drill("pitch-cents-10")!!, EarSettings(), Random(3))
        val t = Ear.sound(q, EarSettings()).tones
        assertEquals(q.cents.toDouble(), 1200 * kotlin.math.ln(t[1].hz / t[0].hz) / kotlin.math.ln(2.0), 1e-9)
        val m = Ear.question(Ear.drill("mode-major")!!, EarSettings(), Random(3))
        assertEquals(m.tonic!! - 24, Ear.sound(m, EarSettings()).drone)
        val pcm = FloatArray(48000)
        EarTone.render(Ear.sound(q, EarSettings()), 48000, pcm)
        assertTrue(pcm.any { it != 0f } && pcm.all { kotlin.math.abs(it) < 1f })
    }

    private fun harmony(drill: String, answer: String, tonic: String = "C") =
        EarHarmony.explain(Ear.drill(drill)!!, answer, PitchName.parse(tonic)) { it.toString() }

    @Test
    fun `harmony phrases spell every chord in the key`() {
        assertTrue(harmony("hm-predominant", "Ger+6").contains("Ger⁺⁶ (Ab C Eb F#) → V (G B D)"), harmony("hm-predominant", "Ger+6"))
        assertTrue(harmony("hm-predominant", "N6").contains("N⁶ (Db F Ab)"))
        assertTrue(harmony("hm-secondary", "V7/V").contains("V7/V (D F# A C) → V (G B D)"))
        assertTrue(harmony("hm-secondary", "V7/ii", "Eb").contains("V7/ii (C E G Bb) → ii (F Ab C)"))
        assertTrue(harmony("hm-subs", "bII7").contains("♭II7 (Db F Ab Cb)"))
        assertTrue(harmony("hm-modulation", "vi").contains("the new key is A minor"))
        assertTrue(harmony("hm-modulation", "IV", "D").contains("the new key is G major"))
        assertTrue(harmony("hm-borrowed", "bVI").contains("♭VI (Ab C Eb)"))
    }

    @Test
    fun `chords are voiced with the bass two octaves under the tonic and an augmented sixth resolves outward to the dominant`() {
        val ger = EarHarmony.phrase(Ear.drill("hm-predominant")!!, "Ger+6")
        val voiced = EarHarmony.voice(ger[1], 60)
        assertEquals(44, voiced.first())          // A♭2 in the bass
        assertEquals(setOf(0, 3, 6, 8), voiced.drop(1).map { it % 12 }.toSet())
        assertEquals(43, EarHarmony.voice(ger[2], 60).first())  // to G2
        val cadence = EarHarmony.phrase(Ear.drill("hm-cadence")!!, "deceptive").map { it.label }
        assertEquals(listOf("I", "IV", "V", "vi"), cadence)
    }

    @Test
    fun `just triads use 5 to 4 and 3 to 2, equal ones twelve-tone steps, and harmonics are whole multiples`() {
        val just = EarHarmony.tones(Ear.drill("tt-just")!!, "just", 48).map { it.hz }
        assertEquals(1.25, just[1] / just[0], 1e-12)
        assertEquals(1.5, just[2] / just[0], 1e-12)
        val equal = EarHarmony.tones(Ear.drill("tt-just")!!, "equal", 48).map { it.hz }
        assertEquals(Math.pow(2.0, 4 / 12.0), equal[1] / equal[0], 1e-12)
        val h = EarHarmony.tones(Ear.drill("tt-partials")!!, "7", 36)
        assertEquals(7.0, h[1].hz / h[0].hz, 1e-12)
        val b = EarHarmony.tones(Ear.drill("tt-beats")!!, "5", 57)
        assertEquals(5.0, b[1].hz - b[0].hz, 1e-9)
    }

    @Test
    fun `accidentals change only before a number, so words stay words`() {
        assertEquals("m7♭5", accidentals("m7b5"))
        assertEquals("𝄫7 ♯11", accidentals("bb7 #11"))
        assertEquals("Bebop dominant · the root is in the bass", accidentals("Bebop dominant · the root is in the bass"))
    }

    @Test
    fun `dictation melodies start on 1, 3 or 5, move by a 5th at most and end on the tonic`() {
        val r = Random(11)
        repeat(300) {
            val m = EarDictation.melody(6, r)
            assertEquals(6, m.size)
            assertTrue(m.first() in setOf(0, 2, 4))
            assertTrue(m.last() == 0 || m.last() == 7)
            assertTrue(m.dropLast(1).zipWithNext().all { (x, y) -> kotlin.math.abs(x - y) in 1..4 })
        }
        assertEquals(listOf(60, 62, 64, 65, 67, 69, 71, 72), (0..7).map { EarDictation.step(60, it, false) })
        assertEquals(listOf(56, 58, 60), (-2..0).map { EarDictation.step(60, it, true) })  // C minor: A♭ B♭ under C
    }

    @Test
    fun `the passing tone sits a step between two chord tones and every other note is in the chord`() {
        val r = Random(5)
        repeat(300) {
            val pos = 2 + r.nextInt(2)
            val root = r.nextInt(7)
            val notes = EarDictation.passing(60, root, pos, false, r)
            val chord = EarDictation.triad(60, root, false).map { it % 12 }.toSet()
            notes.forEachIndexed { i, m -> assertEquals(i != pos - 1, m % 12 in chord, "note ${i + 1} of $notes over $chord") }
            val p = notes[pos - 1]
            assertTrue(kotlin.math.abs(p - notes[pos - 2]) in 1..2 && kotlin.math.abs(p - notes[pos]) in 1..2)
        }
    }

    @Test
    fun `a played-back line counts note by note by pitch class`() {
        val q = EarQuestion(Ear.drill("dc-mel-4")!!, "4", listOf(64, 62, 60, 60), PitchName.parse("C"), EarDirection.UP, tonic = 60)
        assertTrue(Ear.check(q, listOf(64, 62, 60, 72)))
        assertFalse(Ear.check(q, listOf(64, 62, 62, 60)))
        assertEquals(listOf(true, true, false, true), EarDictation.compare(q, listOf(64, 62, 62, 60)))
        assertTrue(Ear.explain(q) { it.toString() }.startsWith("C major: E D C C"))
    }

    @Test
    fun `every rhythm bar fills its meter exactly, and the three options are different bars with the right one where the answer says`() {
        val r = Random(9)
        for (id in listOf("rh-simple", "rh-tuplets", "rh-odd")) repeat(200) {
            val d = Ear.drill(id)!!
            val meter = EarRhythm.meter(d, r)
            val bars = EarRhythm.options(d, meter, it % 3, r)
            for (b in bars) assertEquals(com.betterappmafia.apophenia.core.rhythm.Rational.of(meter.numerator, meter.denominator), b.fold(com.betterappmafia.apophenia.core.rhythm.Rational.ZERO) { acc, n -> acc + n.d.length }, "$id $meter $b")
            assertEquals(3, bars.map { b -> EarRhythm.onsets(b) to b.map { n -> n.d } }.toSet().size)
            if (id == "rh-tuplets") assertTrue(bars.all { b -> b.any { n -> n.d.tuplets.isNotEmpty() } })
        }
    }

    @Test
    fun `swung offbeats land two thirds through the beat and tap-back removes your constant delay`() {
        val bar = List(8) { RNote(com.betterappmafia.apophenia.core.rhythm.Duration.EIGHTH) }
        val snares = EarRhythm.drums(bar, com.betterappmafia.apophenia.core.rhythm.TimeSignature.of(4, 4), 60, 0.0, swing = true, hats = false).map { it.at }
        listOf(0.0, 2.0 / 3, 1.0, 1 + 2.0 / 3).zip(snares.take(4)).forEach { (want, got) -> assertEquals(want, got, 1e-9) }
        val ok = EarRhythm.grade(listOf(1.0, 1.5, 2.0), listOf(1.12, 1.63, 2.11))
        assertTrue(ok.ok)
        assertEquals(120, ok.offsetMs)
        val off = EarRhythm.grade(listOf(1.0, 1.5, 2.0), listOf(1.1, 1.75, 2.1))
        assertFalse(off.ok)
        assertFalse(EarRhythm.grade(listOf(1.0, 1.5), listOf(1.0)).ok)
        val q = Ear.question(Ear.drill("rh-tap")!!, EarSettings(), Random(2))
        val first = EarRhythmQuestions.expected(q).first()
        assertTrue(first >= 2 * EarRhythm.barSeconds(q.meter!!, q.bpm))
    }

    @Test
    fun `a picked drill asks only the intervals picked, plays them right, and its id brings it back`() {
        val d = Ear.pick(listOf("iv-2-3|m3", "iv-4-5|P5"))
        assertEquals("Your pick: m3 · P5", d.name)
        assertEquals(d, Ear.drill(d.id))
        val r = Random(3)
        val asked = (1..200).map { Ear.question(d, EarSettings(), r) }
        assertEquals(setOf("iv-2-3|m3", "iv-4-5|P5"), asked.map { it.answer }.toSet())
        for (q in asked) assertEquals(if (q.answer.endsWith("m3")) 3 else 7, q.midis[1] - q.midis[0])
    }

    @Test
    fun `every chapter can be picked from one answer at a time, each sound listed once`() {
        for (c in Ear.chapters) {
            val groups = Ear.pickable(c)
            val all = groups.flatMap { it.second }
            assertTrue(all.isNotEmpty(), "$c has something to pick")
            assertEquals(all.size, all.toSet().size)
            for (a in all) { val q = Ear.question(Ear.pick(listOf(a)), EarSettings(), Random(1)); assertEquals(a, q.answer) }
        }
        val intervals = Ear.pickable("Intervals").flatMap { it.second }.map { it.substringAfter('|') }
        assertEquals(1, intervals.count { it == "m3" }, "m3 is in two interval drills but listed once")
        assertTrue("M13" in intervals)
        val degrees = Ear.pickable("Scale degrees").flatMap { it.second }
        assertTrue("deg-major|1" in degrees && "deg-minor|1" in degrees, "1 in a minor key is its own answer: the key is minor")
        assertFalse(Ear.pickable("Intervals").any { (d, _) -> d.sung }, "no mic drills")
    }
}
