package com.betterappmafia.apophenia.core.tools

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.instrument.Tunings
import com.betterappmafia.apophenia.core.pitch.OctaveLabel
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.pitch.SpelledPitch
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.sin

class MetronomeTest {
    private fun at(p: BarProgram) = p.events.map { it.at }

    @Test fun `four four plays four beats with the first one strong`() {
        val p = MetronomeSettings().bar()
        assertEquals(4.0, p.units, 0.0)
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0), at(p))
        assertEquals(listOf(ClickSound.STRONG, ClickSound.NORMAL, ClickSound.NORMAL, ClickSound.NORMAL), p.events.map { it.sound })
    }

    @Test fun `six eight counts two dotted beats and switching to it subdivides in threes`() {
        val s = MetronomeSettings().withMeter(TimeSignature.of(6, 8))
        assertEquals(3, s.subdivision)
        val p = s.bar()
        assertEquals(2.0, p.units, 0.0)
        listOf(0.0, 1.0 / 3, 2.0 / 3, 1.0, 4.0 / 3, 5.0 / 3).zip(at(p)).forEach { (e, x) -> assertEquals(e, x, 1e-9) }
        assertEquals(listOf(ClickSound.STRONG, ClickSound.SUB, ClickSound.SUB, ClickSound.NORMAL, ClickSound.SUB, ClickSound.SUB), p.events.map { it.sound })
    }

    @Test fun `seven eight as two two three counts eighths with beats on one three and five`() {
        val p = MetronomeSettings(meter = TimeSignature.parse("2+2+3/8")).bar()
        assertEquals(7.0, p.units, 0.0)
        assertEquals(listOf(0.0, 2.0, 4.0), at(p))
    }

    @Test fun `triplet swing puts the off eighth two thirds through the beat`() {
        val p = MetronomeSettings(subdivision = 2, swing = 2.0 / 3).bar()
        assertEquals(2.0 / 3, p.events[1].at, 1e-9)
        assertEquals(ClickSound.SUB, p.events[1].sound)
    }

    @Test fun `hard swung sixteenths delay the second and fourth of each beat`() {
        val p = MetronomeSettings(subdivision = 4, swing = 0.75).bar()
        listOf(0.0, 0.375, 0.5, 0.875).forEachIndexed { i, e -> assertEquals(e, p.events[i].at, 1e-9) }
    }

    @Test fun `quintuplets and septuplets divide the beat evenly`() {
        val five = MetronomeSettings(subdivision = 5).bar().events.filter { it.beat == 0 }.map { it.at }
        listOf(0.0, 0.2, 0.4, 0.6, 0.8).zip(five).forEach { (e, x) -> assertEquals(e, x, 1e-9) }
        assertEquals(28, MetronomeSettings(subdivision = 7).bar().events.size)
    }

    @Test fun `three against four spreads three poly clicks across the bar`() {
        val poly = MetronomeSettings(poly = 3).bar().events.filter { it.sound == ClickSound.POLY }.map { it.at }
        listOf(0.0, 4.0 / 3, 8.0 / 3).zip(poly).forEach { (e, x) -> assertEquals(e, x, 1e-9) }
    }

    @Test fun `a silent beat is counted but not played, and accents cycle`() {
        var s = MetronomeSettings()
        s = s.cycleAccent(2).cycleAccent(2)   // NORMAL -> SILENT -> STRONG
        assertEquals(Accent.STRONG, s.accentOf(2))
        s = s.cycleAccent(1)   // NORMAL -> SILENT
        assertEquals(Accent.SILENT, s.accentOf(1))
        assertEquals(listOf(0.0, 2.0, 3.0), at(s.bar()))
        assertEquals(4, s.countInBar().events.size)
    }

    @Test fun `the trainer adds five BPM every four bars and stops at its limit`() {
        val s = MetronomeSettings(bpm = 100.0, trainer = TempoTrainer(5, 4, 120))
        assertEquals(100.0, s.bpmAtBar(-1), 0.0)
        assertEquals(100.0, s.bpmAtBar(3), 0.0)
        assertEquals(105.0, s.bpmAtBar(4), 0.0)
        assertEquals(120.0, s.bpmAtBar(16), 0.0)
        assertEquals(120.0, s.bpmAtBar(400), 0.0)
        assertEquals(80.0, MetronomeSettings(bpm = 100.0, trainer = TempoTrainer(-10, 1, 80)).bpmAtBar(9), 0.0)
    }

    @Test fun `two bars on one bar off mutes every third bar but never the count-in`() {
        val s = MetronomeSettings(silent = SilentBars(2, 1))
        assertEquals(listOf(true, true, true, false, true, true, false), (-1L..5L).map { s.audible(it) })
    }

    @Test fun `tap tempo takes the median gap and forgets taps after a pause`() {
        assertNull(MetronomeSettings.tapBpm(listOf(1000)))
        assertEquals(120.0, MetronomeSettings.tapBpm(listOf(0, 500, 1000, 1500))!!, 1e-9)
        assertEquals(120.0, MetronomeSettings.tapBpm(listOf(0, 500, 1000, 1600, 2000))!!, 1e-9)
        assertEquals(60.0, MetronomeSettings.tapBpm(listOf(0, 300, 5000, 6000, 7000))!!, 1e-9)
    }

    @Test fun `metronome settings survive being saved and read back`() {
        val s = MetronomeSettings(
            bpm = 132.5, meter = TimeSignature.parse("2+2+3/8"), subdivision = 2, swing = 0.6, poly = 3, countInBars = 1,
            trainer = TempoTrainer(4, 2, 160), silent = SilentBars(3, 1),
        ).cycleAccent(1)
        assertEquals(s, MetronomeSettings.decode(s.encode()))
        assertEquals(MetronomeSettings(), MetronomeSettings.decode(MetronomeSettings().encode())!!.copy(accents = emptyList()))
        assertNull(MetronomeSettings.decode("2|x"))
    }

    @Test fun `a click grid survives the trip through its JSON`() {
        val g = ClickGrid(48000, 92.0, "2+2+3/8", listOf(-31304, 0, 31304, 62609), setOf(1), setOf(0), true)
        assertEquals(g, ClickGrid.fromJson(g.toJson()))
        assertNull(ClickGrid.fromJson(g.toJson().replace("\"v\":1", "\"v\":2")))
        assertNull(ClickGrid.fromJson("not json"))
    }
}

class TunerTest {
    private fun sine(hz: Double, n: Int = 4096, rate: Int = 48000, amp: Double = 0.5) =
        FloatArray(n) { (amp * sin(2 * PI * hz * it / rate)).toFloat() }

    private fun saw(hz: Double, n: Int = 4096, rate: Int = 48000) = FloatArray(n) { i ->
        var v = 0.0
        for (h in 1..20) if (hz * h < rate / 2) v += sin(2 * PI * hz * h * i / rate) / h
        (0.3 * v).toFloat()
    }

    private fun cents(a: Double, b: Double) = 1200 * ln(a / b) / ln(2.0)

    @Test fun `YIN finds a low E on a bass to within a cent`() {
        val e = Yin.estimate(sine(41.2034), 48000, TunerRange.of(Tunings.BASS_STANDARD).minHz, TunerRange.of(Tunings.BASS_STANDARD).maxHz)
        assertNotNull(e)
        assertEquals(0.0, cents(e!!.hz, 41.2034), 1.0)
    }

    @Test fun `YIN finds drop D's low D and a bright sawtooth A without octave errors`() {
        val r = TunerRange.of(Tunings.BASS_DROP_D)
        assertEquals(0.0, cents(Yin.estimate(saw(36.708), 48000, r.minHz, r.maxHz)!!.hz, 36.708), 1.0)
        assertEquals(0.0, cents(Yin.estimate(saw(110.0), 48000, TunerRange.ANY.minHz, TunerRange.ANY.maxHz)!!.hz, 110.0), 1.0)
        assertEquals(0.0, cents(Yin.estimate(sine(1318.51), 48000, TunerRange.ANY.minHz, TunerRange.ANY.maxHz)!!.hz, 1318.51), 1.0)
    }

    @Test fun `YIN stays on pitch under noise and says nothing for silence`() {
        val rnd = java.util.Random(7)
        val x = sine(196.0).map { (it + 0.05 * rnd.nextGaussian()).toFloat() }.toFloatArray()
        assertEquals(0.0, cents(Yin.estimate(x, 48000, 27.0, 2100.0)!!.hz, 196.0), 2.0)
        assertNull(Yin.estimate(FloatArray(4096), 48000, 27.0, 2100.0))
    }

    @Test fun `just and meantone thirds sit fourteen cents flat and the Pythagorean third eight sharp`() {
        assertEquals(-13.686, Temperament.JUST.offset(4), 1e-3)
        assertEquals(1.955, Temperament.JUST.offset(7), 1e-3)
        assertEquals(7.820, Temperament.PYTHAGOREAN.offset(4), 1e-3)
        assertEquals(-13.686, Temperament.MEANTONE.offset(4), 1e-3)
        assertEquals(-3.422, Temperament.MEANTONE.offset(7), 1e-3)
        assertEquals(0.0, Temperament.EQUAL.offset(4), 0.0)
    }

    @Test fun `A440 reads as A4 dead on and 445 Hz as nearly twenty cents sharp`() {
        val s = TunerSetup()
        assertEquals(SpelledPitch.parse("A4"), s.read(440.0).pitch)
        assertEquals(0.0, s.read(440.0).cents, 1e-9)
        assertEquals(19.56, s.read(445.0).cents, 0.01)
        assertEquals(SpelledPitch.parse("A4"), TunerSetup(a4 = 442.0).read(442.0).pitch)
    }

    @Test fun `in just intonation on C an E tuned five to four over C reads as in tune`() {
        val s = TunerSetup(temperament = Temperament.JUST, tonic = PitchName.parse("C"))
        val pure = 261.6256 * 5 / 4
        val r = s.read(pure)
        assertEquals(SpelledPitch.parse("E4"), r.pitch)
        assertEquals(0.0, r.cents, 0.05)
    }

    @Test fun `notes are spelled in the key, outside notes follow its sharps or flats`() {
        val eb = TunerSetup(key = KeyMode(PitchName.parse("Eb"), "major"))
        assertEquals("Eb4", eb.read(311.127).pitch.toString())
        assertEquals("Db4", eb.read(277.183).pitch.toString())
        val e = TunerSetup(key = KeyMode(PitchName.parse("E"), "major"))
        assertEquals("G#3", e.read(207.652).pitch.toString())
        assertEquals("A#3", e.read(233.082).pitch.toString())
        assertEquals("Bb3", TunerSetup().read(233.082).pitch.toString())
        assertEquals("C3", TunerSetup(octaveLabel = OctaveLabel.MIDDLE_C_IS_C3).let { it.label(it.read(261.626).pitch) })
    }

    @Test fun `a tuning narrows the search to two semitones under its lowest string`() {
        val r = TunerRange.of(Tunings.BASS_STANDARD)
        assertEquals(36.708 * 0.97, r.minHz, 0.01)   // D1
        assertEquals(392.0 * 1.03, r.maxHz, 0.1)   // G4, two octaves over the G string
        assertFalse(TunerRange.of(Tunings.GUITAR_STANDARD).minHz < 70.0)
    }

    @Test fun `a D drone with its fifth and a major third is tuned pure over the root`() {
        val d = DroneSettings(third = DroneThird.MAJOR)
        val f = d.frequencies(TunerSetup())
        assertEquals(73.416, f[0], 0.001)
        assertEquals(73.416 * 1.5, f[1], 0.002)
        assertEquals(73.416 * 1.25, f[2], 0.002)
        assertEquals("D + 5TH + M3", d.short)
    }
}

class RudimentTest {
    @Test fun `the PAS list has all forty rudiments in order`() {
        assertEquals((1..40).toList(), Rudiment.PAS.map { it.number })
        assertEquals("Single Paradiddle", Rudiment.PAS[15].name)
        assertEquals("Triple Ratamacue", Rudiment.PAS[39].name)
    }

    @Test fun `a paradiddle is R L R R then L R L L with the first of each accented`() {
        val p = Rudiment.PAS.first { it.name == "Single Paradiddle" }
        assertEquals("R L R R L R L L", p.strokes.joinToString(" ") { it.hand.toString() })
        assertEquals(listOf(0, 4), p.strokes.indices.filter { p.strokes[it].accent })
        assertEquals(2.0, p.beats, 0.0)
    }

    @Test fun `flams and drags take their grace notes from the other hand, except the dragadiddle's bounce`() {
        Rudiment.PAS.filter { it.number != 35 }.flatMap { it.strokes }.filter { it.grace.isNotEmpty() }.forEach { s ->
            assertTrue("$s", s.grace.all { it.uppercaseChar() != s.hand })
        }
    }

    @Test fun `the lit stroke follows the click and wraps around`() {
        val p = Rudiment.PAS.first { it.name == "Single Paradiddle" }
        assertEquals(0, p.slotAt(0.0))
        assertEquals(5, p.slotAt(1.3))
        assertEquals(1, p.slotAt(2.25))
    }

    @Test fun `strokes read and print the same`() {
        listOf(">lR", "llR", "R~", "-", "L").forEach { assertEquals(it, Stroke.parse(it).toString()) }
    }
}

class RudimentSoundTest {
    private fun energy(x: FloatArray, from: Int, n: Int) = (from until minOf(from + n, x.size)).sumOf { (x[it] * x[it]).toDouble() }

    @Test fun `a paradiddle at 60 plays a stroke every quarter second, accents louder`() {
        val p = Rudiment.PAS.first { it.name == "Single Paradiddle" }
        val rate = 48000
        val x = p.render(60.0, rate, 1)
        val lead = (Rudiment.LEAD_IN * rate).toInt()
        assertEquals(12000.0, p.slotFrames(60.0, rate), 0.0)
        assertEquals(lead + 8 * 12000 + (0.3 * rate).toInt(), x.size)
        assertTrue(energy(x, lead - 200, 200) < 1e-9)                       // silent before the first stroke
        val accented = energy(x, lead, 2000)
        val plain = energy(x, lead + 12000, 2000)
        assertTrue(plain > 0)
        assertTrue(accented > 3 * plain)
    }

    @Test fun `a flam's grace note sounds just before its stroke`() {
        val f = Rudiment.PAS.first { it.name == "Flam" }
        val x = f.render(60.0, 48000, 1)
        val lead = (Rudiment.LEAD_IN * 48000).toInt()
        assertTrue(energy(x, lead - 1440, 1000) > 0)                         // 30 ms before
    }
}

class GrooveTest {
    @Test fun `every common groove is a whole number of bars on every drum`() {
        assertTrue(Groove.COMMON.size >= 15)
        assertEquals(Groove.COMMON.size, Groove.COMMON.map { it.id }.toSet().size)
        Groove.COMMON.forEach { g -> g.grid.rows.values.forEach { assertEquals(g.name, g.grid.steps, it.size) } }
    }

    @Test fun `the rock beat reads as drum tab with the backbeat on 2 and 4`() {
        val g = Groove.COMMON.first { it.id == "rock8" }
        assertEquals("----X-------X---", g.grid.tab(DrumVoice.SNARE))
        assertEquals("x-------x-------", g.grid.tab(DrumVoice.KICK))
    }

    @Test fun `the jazz ride plays ding ding-a on every two beats`() {
        val g = Groove.COMMON.first { it.id == "swing" }
        val ride = g.grid.rows.getValue(DrumVoice.RIDE)
        assertEquals(listOf(0, 3, 5, 6, 9, 11), ride.indices.filter { ride[it] != null })
    }

    @Test fun `swing pushes the off sixteenth late and the lit step follows it`() {
        val g = Groove.COMMON.first { it.id == "boombap" }
        assertEquals(0.25 * 2 * 0.58, g.stepBeats(1), 1e-9)
        assertEquals(0, g.stepAt(0.1))
        assertEquals(1, g.stepAt(0.3))
        assertEquals(4, g.stepAt(1.0))
        assertEquals(0, g.stepAt(4.0))
    }

    @Test fun `a groove renders a bar per pass and a kick lands on the downbeat`() {
        val g = Groove.COMMON.first { it.id == "rock8" }
        val rate = 48000
        val x = g.render(120.0, rate, 2)
        val lead = (DrumKit.LEAD_IN * rate).toInt()
        assertEquals(lead + 2 * 4 * 24000 + rate, x.size)
        assertTrue((lead - 100 until lead).all { x[it] == 0f })
        assertTrue((lead until lead + 2000).any { kotlin.math.abs(x[it]) > 0.1f })
    }

    @Test fun `bad tab marks and wrong lengths are refused`() {
        assertTrue(runCatching { DrumGrid.of("4/4", 4, 1, DrumVoice.KICK to "x--q") }.isFailure)
        assertTrue(runCatching { DrumGrid.of("4/4", 4, 1, DrumVoice.KICK to "x---") }.isFailure)
    }
}

class IsolatorTest {
    private fun tone(rate: Int, n: Int, vararg partials: Pair<Double, Double>) = FloatArray(n) { i ->
        partials.sumOf { (hz, a) -> a * kotlin.math.sin(2 * Math.PI * hz * i / rate) }.toFloat()
    }

    private fun hz(midi: Int) = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)

    @Test fun `the FFT puts a 375 Hz sine in bin 128 of 16384 at 48 kHz`() {
        val m = Fft.magnitudes(tone(48000, 16384, 48000.0 * 128 / 16384 to 1.0))
        assertEquals(128, m.indices.maxBy { m[it] })
    }

    @Test fun `a bass E1 with a weak fundamental is found as E1, not E2`() {
        val f = hz(28)
        val x = tone(48000, 16384, f to 0.2, 2 * f to 1.0, 3 * f to 0.7, 4 * f to 0.4, 5 * f to 0.3)
        val top = NoteFinder.strongest(NoteFinder.strengths(x, 48000))
        assertEquals(28, top.first().midi)
    }

    @Test fun `an A major triad shows A, C sharp and E among the strongest notes`() {
        val x = tone(48000, 16384, hz(57) to 1.0, hz(61) to 0.8, hz(64) to 0.8)
        val top = NoteFinder.strongest(NoteFinder.strengths(x, 48000)).map { it.midi }
        assertTrue(top.toString(), top.containsAll(listOf(57, 61, 64)))
    }

    @Test fun `silence finds nothing`() {
        assertTrue(NoteFinder.strongest(NoteFinder.strengths(FloatArray(16384), 48000)).isEmpty())
    }

    @Test fun `isolator settings round-trip and refuse a low cut over the high cut`() {
        IsolatorSettings.PRESETS.forEach { (_, s) -> assertEquals(s, IsolatorSettings.decode(s.encode())) }
        assertTrue(runCatching { IsolatorSettings(lowCut = 500, highCut = 200) }.isFailure)
        assertTrue(IsolatorSettings().isNeutral)
    }
}

class DrumChartTest {
    private val rock = """
        {time: 4/4}
        {steps: 4}
        HH |x-x-x-x-x-x-x-x-|
        SD |----X-------X---|
        BD |x-------x-------|
    """.trimIndent()

    @Test fun `a chart reads back exactly as it was written`() {
        val c = DrumChart.parse(rock)
        assertEquals(emptyList<String>(), c.problems)
        assertEquals(1, c.grid.bars)
        assertEquals("----X-------X---", c.grid.tab(DrumVoice.SNARE))
        assertEquals(rock + "\n", c.text())
        assertEquals(c, DrumChart.parse(c.text()))
    }

    @Test fun `a drum written again starts the next line of music, and a drum missing from it rests`() {
        val c = DrumChart.parse("HH |x-x-x-x-|\nBD |x---x---|\nHH |xxxxxxxx|")
        assertEquals(2, c.grid.stepsPerBeat)            // worked out from the first bar: 8 cells over 4 beats
        assertEquals(2, c.grid.bars)
        assertEquals("x-x-x-x-|xxxxxxxx", c.grid.tab(DrumVoice.HAT))
        assertEquals("x---x---|--------", c.grid.tab(DrumVoice.KICK))
    }

    @Test fun `what can't be read is listed and the rest of the chart still plays`() {
        val c = DrumChart.parse("{time: 4/4}\nZZ |x---|\nSD |x?--|\nwhatever")
        assertEquals(3, c.problems.size)
        assertTrue(c.problems[0].startsWith("Line 2: ZZ isn't a drum"))
        assertEquals("x---", c.grid.tab(DrumVoice.SNARE))   // '?' read as a rest; 4 cells = one step a beat
        assertEquals(1, c.grid.stepsPerBeat)
    }

    @Test fun `swing and a compound meter survive the round trip`() {
        val c = DrumChart(DrumChart.parse("{time: 12/8}\n{steps: 3}\n{swing: 58}\nHH |xxxxxxxxxxxx|").grid, 0.58)
        assertEquals("12/8", c.grid.meter.toString())
        assertEquals(4, c.grid.meter.beatsPerBar)
        assertEquals(c, DrumChart.parse(c.text()))
        assertTrue("{swing: 58}" in c.text())
    }

    @Test fun `tapping a cell goes rest, hit, accent, ghost, and open only on the hi-hat`() {
        var g = DrumChart.starter().grid
        val marks = (1..5).map { g = g.cycled(DrumVoice.HAT, 3); g.rows.getValue(DrumVoice.HAT)[3] }
        assertEquals(listOf(DrumHit.HIT, DrumHit.ACCENT, DrumHit.GHOST, DrumHit.OPEN, null), marks)
        var s = DrumChart.starter().grid
        val snare = (1..4).map { s = s.cycled(DrumVoice.SNARE, 0); s.rows.getValue(DrumVoice.SNARE)[0] }
        assertEquals(listOf(DrumHit.HIT, DrumHit.ACCENT, DrumHit.GHOST, null), snare)
    }

    @Test fun `going from sixteenths to eighths keeps the eighths and drops the sixteenths between`() {
        val g = DrumChart.parse("HH |xxxxxxxxxxxxxxxx|").grid.resized(stepsPerBeat = 2)
        assertEquals("xxxxxxxx", g.tab(DrumVoice.HAT))
        val back = g.resized(stepsPerBeat = 4)
        assertEquals("x-x-x-x-x-x-x-x-", back.tab(DrumVoice.HAT))
        // Triplets and eighths share only the beat itself.
        assertEquals("x--x--x--x--", DrumChart.parse("HH |x-x-x-x-|").grid.resized(stepsPerBeat = 3).tab(DrumVoice.HAT))
    }

    @Test fun `a shorter bar or fewer bars drops what no longer fits and more bars add rests`() {
        val g = DrumChart.parse(rock).grid
        assertEquals("----X-------", g.resized(meter = TimeSignature.parse("3/4")).tab(DrumVoice.SNARE))
        assertEquals("----X-------X---|----------------", g.resized(bars = 2).tab(DrumVoice.SNARE))
        assertEquals(1, g.resized(bars = 2).resized(bars = 1).bars)
    }

    @Test fun `the starter chart is an empty bar of hat, snare and kick in sixteenths`() {
        val c = DrumChart.starter()
        assertEquals(listOf(DrumVoice.HAT, DrumVoice.SNARE, DrumVoice.KICK), DrumVoice.entries.filter { it in c.grid.rows })
        assertEquals(16, c.grid.steps)
        assertEquals(c, DrumChart.parse(c.text()))
        assertEquals(c.grid.bars, DrumChart.parse("").grid.bars)
    }

    @Test fun `a long chart is written a few bars to a line and reads back whole`() {
        val c = DrumChart(DrumChart.parse(rock).grid.resized(bars = 5))
        assertEquals(3, c.text().split("\n\n").size)   // 2 bars of sixteenths to a line: 2 + 2 + 1
        assertEquals(c, DrumChart.parse(c.text()))
    }

    @Test fun `a chart plays like a groove, kick on the downbeat`() {
        val rate = 48000
        val pcm = DrumChart.parse(rock).groove.render(120.0, rate, passes = 1)
        val lead = (DrumKit.LEAD_IN * rate).toInt()
        assertTrue(pcm.copyOfRange(0, lead - 10).all { it == 0f })
        assertTrue(pcm.copyOfRange(lead, lead + 200).any { kotlin.math.abs(it) > 0.05f })
    }
}
