package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.catalog.Chords
import com.betterappmafia.apophenia.core.catalog.Scales
import com.betterappmafia.apophenia.core.learn.Acoustics.cents
import com.betterappmafia.apophenia.core.learn.Acoustics.hz
import com.betterappmafia.apophenia.core.pitch.PitchName
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CourseTest {
    @Test
    fun `the course has the nine categories of the mockup, sound and pitch written first`() {
        assertEquals(listOf("sound", "rhythm", "intervals", "scales", "chords", "harmony", "melody", "reading", "instruments"), Course.categories.map { it.id })
        assertEquals(listOf(6, 8, 5, 9, 10, 9, 7, 8, 9), Course.categories.map { it.topics.size })
        val sound = Course.category("sound")!!
        assertEquals("●–●●●", sound.range)
        assertTrue(sound.topics.all { it.pages.isNotEmpty() })
        assertEquals(123, Course.pages.size)
        assertTrue(Course.category("reading")!!.topics.all { it.pages.isNotEmpty() })
        assertEquals("●–●●●", Course.category("reading")!!.range)
        assertTrue(Course.category("melody")!!.topics.all { it.pages.isNotEmpty() })
        assertEquals("●–●●●●", Course.category("melody")!!.range)
        assertTrue(Course.category("rhythm")!!.topics.all { it.pages.isNotEmpty() })
        assertEquals("●–●●●●●", Course.category("rhythm")!!.range)
        assertTrue(Course.category("harmony")!!.topics.all { it.pages.isNotEmpty() })
        assertEquals("●●–●●●●●", Course.category("harmony")!!.range)
        assertTrue(Course.category("chords")!!.topics.all { it.pages.isNotEmpty() })
        assertEquals("●–●●●●●", Course.category("chords")!!.range)
        assertTrue(Course.category("scales")!!.topics.all { it.pages.isNotEmpty() })
        assertEquals("●–●●●●", Course.category("scales")!!.range)
        assertTrue(Course.category("intervals")!!.topics.all { it.pages.isNotEmpty() })
        assertEquals("●–●●●", Course.category("intervals")!!.range)
        assertEquals(Course.pages.size, Course.pages.map { it.id }.distinct().size, "page ids are unique")
        assertEquals("●–●●●●", Course.category("instruments")!!.range)
        assertTrue(Course.categories.all { c -> c.topics.all { it.pages.isNotEmpty() } }, "every topic in every category is written")
    }

    @Test
    fun `every link, try and your-material lookup points at something that exists`() {
        for (p in Course.pages) {
            p.tryIt?.let { assertNotNull(Reference.item(it.kind, it.id), "${p.id} try ${it.id}"); PitchName.parse(it.root) }
            p.yours?.let { (k, id) -> assertNotNull(Reference.item(k, id), "${p.id} yours $id") }
            for (l in p.related) when (val t = l.target) {
                is CourseTarget.Ref -> { assertNotNull(Reference.item(t.kind, t.id), "${p.id} → ${t.id}"); PitchName.parse(t.root) }
                is CourseTarget.Ear -> assertNotNull(Ear.drill(t.drill), "${p.id} → ear ${t.drill}")
                is CourseTarget.Page -> assertNotNull(Course.page(t.id), "${p.id} → page ${t.id}")
            }
            assertTrue(p.level in 1..5)
            assertTrue(p.explain.length > 200, "${p.id} explains")
        }
    }

    @Test
    fun `every sound and every check answer makes audio, from every root it can start on`() {
        val r = Random(3)
        for (p in Course.pages) {
            p.hear.forEach { s -> listOf(48, 60, 67).forEach { root -> val a = s.make(root, r); assertTrue(a.piano.isNotEmpty() || a.tones.isNotEmpty() || a.drums.isNotEmpty(), "${p.id} ${s.label}") } }
            p.check?.let { c ->
                assertTrue(c.options.size >= 2)
                for (root in c.roots) c.options.forEach { o -> val a = o.make(root, r); assertTrue(a.piano.isNotEmpty() || a.tones.isNotEmpty() || a.drums.isNotEmpty(), "${p.id} ${o.label}") }
            }
        }
    }

    @Test
    fun `the checks play what their answers say`() {
        fun gaps(id: String, option: Int) = (1..30).map { Course.page(id)!!.check!!.options[option].make(60, Random(it)).piano.let { n -> Math.abs(n[1].midi - n[0].midi) } }.toSet()
        assertEquals(setOf(12), gaps("pitch-octave", 0))
        assertEquals(setOf(11, 13), gaps("pitch-octave", 1))
        assertEquals(setOf(1), gaps("steps-half", 0))
        assertEquals(setOf(2), gaps("steps-half", 1))
        assertEquals(setOf(1), gaps("steps-whole", 0))
        assertEquals(setOf(2), gaps("steps-whole", 1))
        val higher = Course.page("pitch-frequency")!!.check!!.options
        (1..20).forEach { s -> higher[0].make(60, Random(s)).piano.let { assertTrue(it[1].midi > it[0].midi) }; higher[1].make(60, Random(s)).piano.let { assertTrue(it[1].midi < it[0].midi) } }
        val sharp = Course.page("tuning-cents")!!.check!!.options
        sharp[0].make(69, Random(1)).tones.let { assertEquals(20.0, cents(it[1].hz / it[0].hz), 1e-9) }
        sharp[1].make(69, Random(1)).tones.let { assertEquals(-20.0, cents(it[1].hz / it[0].hz), 1e-9) }
        val third = Course.page("tuning-equal")!!.check!!.options
        third[0].make(60, Random(1)).tones.let { assertEquals(400.0, cents(it[1].hz / it[0].hz), 1e-9) }
        third[1].make(60, Random(1)).tones.let { assertEquals(1.25, it[1].hz / it[0].hz, 1e-12) }
        val harmonic = Course.page("timbre-harmonics")!!.check!!.options
        listOf(2, 3, 4, 5).forEachIndexed { i, n -> harmonic[i].make(40, Random(1)).tones.let { assertEquals(n.toDouble(), it[1].hz / it[0].hz, 1e-12) } }
    }

    @Test
    fun `the frequencies quoted are right`() {
        assertEquals(440.0, hz(69), 1e-12)
        assertEquals("261.63", Acoustics.hzText(hz(60)))
        assertEquals("27.50", Acoustics.hzText(hz(21)))
        assertEquals("4186.01", Acoustics.hzText(hz(108)))
        assertEquals("41.20", Acoustics.hzText(hz(28)))
        assertEquals("82.41", Acoustics.hzText(hz(40)))
        assertEquals("329.63", Acoustics.hzText(hz(64)))
        assertEquals(listOf("55", "110", "220", "440", "880", "1760"), (1..6).map { Acoustics.hzText(hz(21 + 12 * it)) })
        assertEquals(23.45, cents(446.0 / 440), 0.01)
        assertEquals(-101.27, cents(415.0 / 440), 0.01)
        assertEquals(-31.77, cents(432.0 / 440), 0.01)
        assertEquals(1.05946, Math.pow(2.0, 1.0 / 12), 1e-5)
    }

    @Test
    fun `the harmonic series over C2 is spelled and tuned as the page says`() {
        val names = (1..8).map { CourseSoundPitch.nameOf(36 + Acoustics.harmonic(it).first) }
        assertEquals(listOf("C2", "C3", "G3", "C4", "E4", "G4", "B♭4", "C5"), names)
        assertEquals(-13.69, Acoustics.harmonic(5).second, 0.01)
        assertEquals(1.96, Acoustics.harmonic(6).second, 0.01)
        assertEquals(-31.17, Acoustics.harmonic(7).second, 0.01)
        assertEquals(23.46, Acoustics.pythagoreanComma, 0.01)
        assertEquals(1.96, cents(1.5) - 700, 0.01, "the equal fifth is 1.96 cents narrow")
        assertEquals(13.69, 400 - cents(1.25), 0.01, "the equal major third is 13.69 cents wide")
        assertEquals("−31.17", Acoustics.centsText(Acoustics.harmonic(7).second))
    }

    @Test
    fun `the guitar harmonics agree with the tuning page`() {
        // 5th-fret harmonic of low E = 4th harmonic = E4; 7th-fret harmonic of A = 3rd harmonic = 330 Hz.
        assertEquals(hz(64), 4 * hz(40), 1e-9)
        assertEquals(330.0, 3 * hz(45), 1e-9)
        // Tuning A so its 3rd harmonic matches E's 4th leaves A 1.96 cents flat.
        assertEquals(-1.96, cents(4 * hz(40) / 3 / hz(45)), 0.01)
        // 12th, 7th, 5th fret harmonics of A2 (110 Hz): 220, 330, 440.
        assertEquals(110.0, hz(45), 1e-9)
    }

    @Test
    fun `the spellings in note names are right`() {
        fun pc(s: String) = PitchName.parse(s).pitchClass
        assertEquals(0, pc("B#")); assertEquals(5, pc("E#")); assertEquals(11, pc("Cb")); assertEquals(4, pc("Fb"))
        assertEquals(7, pc("Fx")); assertEquals(9, pc("Bbb"))
        assertEquals(60, PitchName.parse("B#").at(3).midi, "B♯3 is the key of C4")
        assertEquals(59, PitchName.parse("Cb").at(4).midi, "C♭4 is the key of B3")
        assertEquals("D E F# G A B C#", ScalePages.page(Scales.byId("major"), PitchName.parse("D")).notes.joinToString(" "))
        assertEquals("C Eb Gb Bbb", ChordPages.page(Chords.byId("dim7"), PitchName.parse("C")).notes.joinToString(" "))
        assertTrue(PitchName.parse("Fx") in ChordPages.page(Chords.byId("7#9"), PitchName.parse("E")).notes)
        assertEquals(listOf(2, 2, 1, 2, 2, 2, 1), Scales.byId("major").semitones.let { s -> (s + 12).zipWithNext { a, b -> b - a } }, "W W H W W W H")
        assertEquals(6, Scales.byId("whole-tone").semitones.size)
    }

    @Test
    fun `level shows a guide, never a lock`() {
        val topics = Course.categories.flatMap { it.topics }
        assertEquals(topics, Course.shown(topics, 0))
        assertTrue(Course.shown(topics, 2).all { it.level == 2 })
        assertTrue(Course.shown(topics, 4).all { it.level >= 4 })
        assertEquals("●●○○○", Course.gauge(2))
        assertEquals("tuning-equal", Course.next("tuning-cents")!!.id)
        assertEquals(null, Course.previous(Course.pages.first().id))
    }
}
