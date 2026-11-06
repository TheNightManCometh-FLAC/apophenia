package com.betterappmafia.apophenia.core.tools

import com.betterappmafia.apophenia.core.catalog.Chords
import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.catalog.Scales
import com.betterappmafia.apophenia.core.degree.DegreeToken
import com.betterappmafia.apophenia.core.instrument.Instrument
import com.betterappmafia.apophenia.core.instrument.Tunings
import com.betterappmafia.apophenia.core.interval.plus
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.pitch.SpelledPitch
import com.betterappmafia.apophenia.core.rhythm.Duration
import com.betterappmafia.apophenia.core.rhythm.Tempo
import com.betterappmafia.apophenia.core.score.Bar
import com.betterappmafia.apophenia.core.score.Clef
import com.betterappmafia.apophenia.core.score.Event
import com.betterappmafia.apophenia.core.score.Note
import com.betterappmafia.apophenia.core.score.Part
import com.betterappmafia.apophenia.core.score.Score

/** Who an exercise is for. */
enum class ExerciseFor(val label: String) {
    DRUMS("Drums"), GUITAR("Guitar"), BASS("Bass"), PIANO("Piano"), VOICE("Voice"), ANY("Every instrument")
}

/**
 * One curated exercise (plan §7 Exercises; Christopher, 2 Nov 2026: "all of the instruments if possible. The restriction
 * is finding quality exercises. So vet them very carefully"). Only standard, public material: the PAS rudiments and the
 * scale, arpeggio, position and vocalise patterns every method teaches. Nothing is typed in by hand: every note is spelled
 * by the app's spelling engine from the pattern's degrees, every tab note is the fretboard's own pitch at that string and
 * fret, and ExercisesTest checks each one in every key.
 *
 * [inKeys]: it moves to any key ([roots]). [source] says where the pattern comes from. No tempo is preset: the
 * tempo is yours to set (Christopher: "The tempo raising should be on them to decide").
 */
data class CuratedExercise(
    val id: String,
    val forWhom: ExerciseFor,
    val family: String,
    val name: String,
    val what: String,
    val source: String,
    val inKeys: Boolean = false,
    val minor: Boolean = false,
    /** The drum exercises: a PAS rudiment. */
    val rudiment: Rudiment? = null,
    private val build: ((PitchName) -> Score)? = null,
) {
    /** The music in [root] (ignored when not [inKeys]); null for a rudiment. */
    fun score(root: PitchName = defaultRoot): Score? = build?.invoke(if (inKeys) root else defaultRoot)
    val defaultRoot: PitchName get() = if (minor) PitchName.parse("A") else PitchName.parse("C")
    val roots: List<PitchName> get() = if (minor) Exercises.MINOR_ROOTS else Exercises.MAJOR_ROOTS
}

object Exercises {
    /** Major keys round the circle of fifths, as their key signatures spell them. */
    val MAJOR_ROOTS = listOf("C", "G", "D", "A", "E", "B", "F#", "Db", "Ab", "Eb", "Bb", "F").map(PitchName::parse)
    /** Minor keys round the circle, with the usual key-signature spellings. */
    val MINOR_ROOTS = listOf("A", "E", "B", "F#", "C#", "G#", "Eb", "Bb", "F", "C", "G", "D").map(PitchName::parse)

    val GUITAR = Instrument(Tunings.GUITAR_STANDARD)
    val BASS = Instrument(Tunings.BASS_STANDARD)

    private val e = Duration.EIGHTH
    private val q = Duration.QUARTER
    private fun d(s: String) = s.trim().split(Regex("\\s+")).map(DegreeToken::parse)

    /** [degrees] above [root] in octave [octave], each spelled from its degree. */
    fun spell(root: PitchName, octave: Int, degrees: List<DegreeToken>): List<SpelledPitch> = degrees.map { root.at(octave) + it.interval }

    /** Notes into 4/4 bars of [value]; the last bar is filled out with rests of the same value. */
    private fun bars(events: List<Event>, value: Duration = e): List<Bar> {
        val perBar = if (value == q) 4 else 8
        return events.chunked(perBar).map { chunk -> Bar(listOf(chunk + List(perBar - chunk.size) { Event(value) })) }
    }

    private fun staff(name: String, clef: Clef, notes: List<Note>, value: Duration = e, key: KeyMode? = null) =
        Score(key = key, tempo = Tempo(60.0), parts = listOf(Part(name, clef, bars = bars(notes.map { Event(value, listOf(it)) }, value))))

    private fun fretted(name: String, inst: Instrument, clef: Clef, notes: List<Note>, key: KeyMode? = null) =
        Score(key = key, tempo = Tempo(60.0), parts = listOf(Part(name, clef, inst, bars(notes.map { Event(e, listOf(it)) }))))

    // ------------------------------------------------------------------------------------------- scales and arpeggios

    private val UP8 = d("1 2 3 4 5 6 7 8")

    /** A seven-note scale's degrees one octave up, then back down; [down] replaces the way down (melodic minor). */
    private fun upDown(up: List<DegreeToken>, down: List<DegreeToken> = up) = up + down.reversed().drop(1)

    private fun scaleDegrees(id: String) = Scales.byId(id).degrees.let { it + DegreeToken(8) }

    /** Up through [degrees] and back down, written in [root]'s key on the treble staff. */
    private fun scaleScore(root: PitchName, degrees: List<DegreeToken>, scaleId: String, fingers: List<String> = emptyList()) =
        spell(root, 4, degrees).mapIndexed { i, p -> Note(p, finger = fingers.getOrElse(i) { "" }) }
            .let { staff("", Clef.TREBLE, it, key = KeyMode(root, scaleId)) }

    /** A chord's arpeggio: up through its tones to the octave and back down. */
    private fun arpeggio(chordId: String): List<DegreeToken> {
        val tones = Chords.byId(chordId).degrees + DegreeToken(8)
        return tones + tones.reversed().drop(1)
    }

    /**
     * A major scale in thirds from [root]: 1 3, 2 4 … 7 9; then 8 6, 7 5 … 2 and the 7th below, 1 (pairs of
     * degrees a third apart, up and back).
     */
    fun thirds(root: PitchName): List<SpelledPitch> {
        // Index 0 is the 7th below the root, 1–7 the scale, 8 the octave, 9 the 9th.
        val ladder = spell(root, 3, d("7")) + spell(root, 4, d("1 2 3 4 5 6 7 8 9"))
        val up = (1..7).flatMap { listOf(it, it + 2) }
        val down = (8 downTo 2).flatMap { listOf(it, it - 2) } + 1
        return (up + down).map { ladder[it] }
    }

    // --------------------------------------------------------------------------------------------- fretted positions

    /**
     * [count] notes of a scale up from [start] on [inst]'s lowest string, [perString] to a string, highest string last.
     * Returns null if a note would fall below the nut or past the last fret.
     */
    fun perString(inst: Instrument, scale: List<SpelledPitch>, perString: Int): List<Note>? {
        val strings = inst.tuning.stringCount
        val out = ArrayList<Note>()
        var i = 0
        for (s in strings downTo 1) repeat(perString) {
            val p = scale.getOrNull(i++) ?: return null
            val fret = p.midi - inst.midiAt(s, 0)
            if (fret < 0 || fret > inst.frets) return null
            out += Note(p, string = s, fret = fret)
        }
        return out
    }

    /**
     * Pitches of the scale with pitch classes [pcs] spelled as [names], from [first] upward ([count] notes).
     */
    fun ascending(first: SpelledPitch, names: List<PitchName>, count: Int): List<SpelledPitch> {
        val out = ArrayList<SpelledPitch>()
        var midi = first.midi
        var p = first
        while (out.size < count) {
            out += p
            val next = (1..12).asSequence().map { midi + it }.first { m -> names.any { it.pitchClass == Math.floorMod(m, 12) } }
            val n = names.first { it.pitchClass == Math.floorMod(next, 12) }
            p = n.at(Math.floorDiv(next - n.letter.natural - n.alter, 12) - 1)
            midi = next
        }
        return out
    }

    /** The lowest fret from 1 to 12 on [inst]'s lowest string sounding [name]. */
    private fun lowFret(inst: Instrument, name: PitchName) = (1..12).first { Math.floorMod(inst.midiAt(inst.tuning.stringCount, it), 12) == name.pitchClass }

    private fun pitchAt(inst: Instrument, string: Int, fret: Int, name: PitchName) = inst.tabNote(string, fret, name).pitch

    /**
     * Box [box] (0–4) of a pentatonic in [root]: it starts on the scale's ([box]+1)th note on the low E string and takes
     * two notes a string, the five standard boxes.
     */
    fun pentatonicBox(root: PitchName, minor: Boolean, box: Int): List<Note> {
        val names = scaleNames(root, if (minor) "minor-pentatonic" else "major-pentatonic")
        val start = names[box]
        val first = pitchAt(GUITAR, 6, lowFret(GUITAR, start), start)
        return perString(GUITAR, ascending(first, names, 12), 2)!!
    }

    /** Pattern [from] (0–6) of a major scale three notes a string, starting on its ([from]+1)th degree on the low E. */
    fun threePerString(root: PitchName, from: Int): List<Note> {
        val names = scaleNames(root, "major")
        val start = names[from]
        val first = pitchAt(GUITAR, 6, lowFret(GUITAR, start), start)
        return perString(GUITAR, ascending(first, names, 18), 3)!!
    }

    fun scaleNames(root: PitchName, scaleId: String): List<PitchName> = Scales.byId(scaleId).degrees.map { (root.at(4) + it.interval).name }

    /** Up and back down: the notes reversed after the top, the top played once. */
    private fun andBack(notes: List<Note>) = notes + notes.reversed().drop(1)

    /**
     * The chromatic "spider" warm-up: one finger a fret over four frets from fret 1, the fingers in [order] (e.g.
     * "1324"), low E string to high E and back. Each note's finger is its fret.
     */
    fun spider(order: String): List<Note> {
        val fingers = order.map { it.digitToInt() }
        val up = (6 downTo 1).flatMap { s -> fingers.map { f -> GUITAR.tabNote(s, f).let { Note(it.pitch, string = s, fret = f, finger = "$f") } } }
        val down = (1..6).flatMap { s -> fingers.reversed().map { f -> GUITAR.tabNote(s, f).let { Note(it.pitch, string = s, fret = f, finger = "$f") } } }
        return up + down
    }

    /**
     * Bass in one position: the notes from [root] on the E string, each on the lowest string where it lies within the
     * position (one fret behind the root's fret to two above it, the reach of Simandl's 1-2-4 fingering).
     */
    fun bassPosition(root: PitchName, degrees: List<DegreeToken>, behind: Int = 1, ahead: Int = 2): List<Note> {
        val f = lowFret(BASS, root)
        val base = pitchAt(BASS, 4, f, root)
        return degrees.map { dg ->
            val p = base + dg.interval
            // The window is four frets wide and the strings a fourth apart, so exactly one string holds each note.
            val string = (4 downTo 1).firstOrNull { s -> p.midi - BASS.midiAt(s, 0) in f - behind..f + ahead }
                ?: throw IllegalArgumentException("$p is out of the position at fret $f")
            Note(p, string = string, fret = p.midi - BASS.midiAt(string, 0))
        }
    }

    // ---------------------------------------------------------------------------------------------------- piano

    /** Standard one-octave right-hand scale fingering, up then down, for the keys it holds in (C G D A E: 1231234-5; F: 1234 1234). */
    fun pianoScaleFingers(root: PitchName): List<String>? {
        val up = when (root.toString()) {
            "C", "G", "D", "A", "E" -> "12312345"
            "F" -> "12341234"
            else -> return null
        }.map { "$it" }
        return up + up.reversed().drop(1)
    }

    /** Standard right-hand chromatic fingering by pitch: 3 on black keys, 1 on white, 2 on F and on the C at the top. */
    fun chromaticFinger(p: SpelledPitch, top: Boolean): String = when {
        p.pitchClass in setOf(1, 3, 6, 8, 10) -> "3"
        p.pitchClass == 5 -> "2"
        p.pitchClass == 0 && top -> "2"
        else -> "1"
    }

    /** The chromatic scale from C4: sharps going up, flats coming down. */
    fun chromatic(): List<Note> {
        val up = listOf("C4", "C#4", "D4", "D#4", "E4", "F4", "F#4", "G4", "G#4", "A4", "A#4", "B4", "C5").map(SpelledPitch::parse)
        val down = listOf("B4", "Bb4", "A4", "Ab4", "G4", "Gb4", "F4", "E4", "Eb4", "D4", "Db4", "C4").map(SpelledPitch::parse)
        return (up + down).mapIndexed { i, p -> Note(p, finger = chromaticFinger(p, i == up.size - 1)) }
    }

    /** C major in contrary motion from middle C: both hands 1231234-5 out and back, the left hand going down. */
    fun contrary(): Score {
        val c = PitchName.parse("C")
        val fingers = "12312345".map { "$it" }.let { it + it.reversed().drop(1) }
        val rh = spell(c, 4, upDown(UP8)).mapIndexed { i, p -> Note(p, finger = fingers[i]) }
        // The left hand: middle C down to the C below and back, the same notes as the right hand's octave above it.
        val lhDown = spell(c, 3, UP8).reversed()
        val lh = (lhDown + lhDown.reversed().drop(1)).mapIndexed { i, p -> Note(p, finger = fingers[i]) }
        val key = KeyMode(c, "major")
        return Score(key = key, tempo = Tempo(60.0), parts = listOf(
            Part("Right hand", Clef.TREBLE, bars = bars(rh.map { Event(e, listOf(it)) })),
            Part("Left hand", Clef.BASS, bars = bars(lh.map { Event(e, listOf(it)) })),
        ))
    }

    // ---------------------------------------------------------------------------------------------------- catalogue

    private const val PATTERN = "A standard pattern taught in every method; the notes are spelled by the app in each key."

    private fun major(id: String, name: String, what: String, degrees: List<DegreeToken>, forWhom: ExerciseFor = ExerciseFor.ANY, family: String = "Scales", source: String = PATTERN) =
        CuratedExercise(id, forWhom, family, name, what, source, inKeys = true) { r -> scaleScore(r, degrees, "major") }

    private fun minorScale(id: String, name: String, what: String, scaleId: String, up: List<DegreeToken>, down: List<DegreeToken> = up) =
        CuratedExercise(id, ExerciseFor.ANY, "Scales", name, what, PATTERN, inKeys = true, minor = true) { r -> scaleScore(r, upDown(up, down), scaleId) }

    private fun arp(id: String, name: String, chordId: String, minor: Boolean = false) =
        CuratedExercise(id, ExerciseFor.ANY, "Arpeggios", name, "The chord's notes one at a time, root to octave and back: ${Chords.byId(chordId).degrees.joinToString(" ")} 8.", PATTERN, inKeys = true, minor = minor) { r ->
            scaleScore(r, arpeggio(chordId), if (minor) "aeolian" else "major")
        }

    val all: List<CuratedExercise> by lazy {
        buildList {
            // Drums: the 40 PAS International Drum Rudiments (Christopher, 2 Nov 2026: rudiments in both Reference and Exercises).
            Rudiment.PAS.forEach { r ->
                add(CuratedExercise("pas-${r.number}", ExerciseFor.DRUMS, r.family, r.name, "PAS rudiment #${r.number}: ${r.strokes.joinToString(" ")}.",
                    "Percussive Arts Society, 40 International Drum Rudiments (1984).", rudiment = r))
            }

            // Every instrument: scales and arpeggios in all keys.
            add(major("major-scale", "Major scale", "One octave up and down.", upDown(UP8)))
            add(minorScale("natural-minor", "Natural minor scale", "One octave up and down.", "aeolian", scaleDegrees("aeolian")))
            add(minorScale("harmonic-minor", "Harmonic minor scale", "One octave up and down; the raised 7th both ways.", "harmonic-minor", scaleDegrees("harmonic-minor")))
            add(minorScale("melodic-minor", "Melodic minor scale", "Raised 6th and 7th going up, natural minor coming down, as it's taught classically.", "melodic-minor", scaleDegrees("melodic-minor"), scaleDegrees("aeolian")))
            add(major("major-pentatonic", "Major pentatonic", "1 2 3 5 6 up to the octave and back.", upDown(d("1 2 3 5 6 8"))))
            add(CuratedExercise("minor-pentatonic", ExerciseFor.ANY, "Scales", "Minor pentatonic", "1 ♭3 4 5 ♭7 up to the octave and back.", PATTERN, inKeys = true, minor = true) { r ->
                scaleScore(r, upDown(d("1 b3 4 5 b7 8")), "aeolian")
            })
            add(CuratedExercise("major-thirds", ExerciseFor.ANY, "Scales", "Major scale in thirds", "Up in pairs a third apart (1 3, 2 4, 3 5 …), then down the same way.", PATTERN, inKeys = true) { r ->
                staff("", Clef.TREBLE, thirds(r).map { Note(it) }, key = KeyMode(r, "major"))
            })
            add(arp("arp-major", "Major triad arpeggio", "maj"))
            add(arp("arp-minor", "Minor triad arpeggio", "m", minor = true))
            add(arp("arp-dom7", "Dominant 7th arpeggio", "7"))
            add(arp("arp-maj7", "Major 7th arpeggio", "maj7"))
            add(arp("arp-m7", "Minor 7th arpeggio", "m7", minor = true))
            add(arp("arp-m7b5", "Half-diminished arpeggio", "m7b5", minor = true))
            add(arp("arp-dim7", "Diminished 7th arpeggio", "dim7", minor = true))

            // Guitar.
            listOf("1234", "1324", "1243", "1423").forEach { o ->
                add(CuratedExercise("spider-$o", ExerciseFor.GUITAR, "Chromatic warm-ups", "Chromatic ${o.toList().joinToString("-")}",
                    "One finger a fret over frets 1 to 4, fingers in the order ${o.toList().joinToString("-")}, low E to high E and back. Alternate pick (down, up). Move it up a fret each time round once it's even.",
                    "The four-finger chromatic permutation exercise, a standard guitar warm-up.") {
                    fretted("Guitar", GUITAR, Clef.TREBLE_8VB, spider(o))
                })
            }
            for (box in 0..4) add(CuratedExercise("pent-box-${box + 1}", ExerciseFor.GUITAR, "Pentatonic boxes", "Minor pentatonic, box ${box + 1}",
                "Two notes a string, low E to high E and back; box ${box + 1} starts on the scale's ${listOf("root", "♭3", "4th", "5th", "♭7")[box]} on the low E.",
                "The five standard pentatonic positions, worked out on the fretboard in each key.", inKeys = true, minor = true) { r ->
                fretted("Guitar", GUITAR, Clef.TREBLE_8VB, andBack(pentatonicBox(r, true, box)), KeyMode(r, "aeolian"))
            })
            for (from in 0..6) add(CuratedExercise("3nps-${from + 1}", ExerciseFor.GUITAR, "Three notes a string", "Major scale, 3 a string, pattern ${from + 1}",
                "Three notes a string from the ${listOf("1st", "2nd", "3rd", "4th", "5th", "6th", "7th")[from]} degree on the low E, up and back: for alternate picking and legato.",
                "The seven three-notes-per-string major-scale patterns, worked out on the fretboard in each key.", inKeys = true) { r ->
                fretted("Guitar", GUITAR, Clef.TREBLE_8VB, andBack(threePerString(r, from)), KeyMode(r, "major"))
            })

            // Bass.
            add(CuratedExercise("bass-chromatic", ExerciseFor.BASS, "Warm-ups", "Chromatic in first position",
                "Frets 1, 2, 3 on each string with fingers 1, 2, 4 (Simandl), E to G and back.", "Simandl fingering, the standard double-bass and electric-bass system.") {
                fretted("Bass", BASS, Clef.BASS, andBack((4 downTo 1).flatMap { s -> (1..3).map { f -> BASS.tabNote(s, f).let { Note(it.pitch, string = s, fret = f, finger = if (f == 3) "4" else "$f") } } }))
            })
            add(CuratedExercise("bass-major", ExerciseFor.BASS, "Scales", "Major scale in one position", "Root on the E string, one octave up and down without shifting.", PATTERN, inKeys = true) { r ->
                fretted("Bass", BASS, Clef.BASS, bassPosition(r, upDown(UP8)), KeyMode(r, "major"))
            })
            add(CuratedExercise("bass-arp-major", ExerciseFor.BASS, "Arpeggios", "Major arpeggio in one position", "1 3 5 8 and back, root on the E string.", PATTERN, inKeys = true) { r ->
                fretted("Bass", BASS, Clef.BASS, bassPosition(r, arpeggio("maj")), KeyMode(r, "major"))
            })
            add(CuratedExercise("bass-arp-minor", ExerciseFor.BASS, "Arpeggios", "Minor arpeggio in one position", "1 ♭3 5 8 and back, root on the E string with the 1st finger, the ♭3 with the 4th.", PATTERN, inKeys = true, minor = true) { r ->
                fretted("Bass", BASS, Clef.BASS, bassPosition(r, arpeggio("m"), behind = 0, ahead = 3), KeyMode(r, "aeolian"))
            })
            add(CuratedExercise("bass-root-fifth", ExerciseFor.BASS, "Grooves", "Root, fifth, octave", "The root, the fifth above it a string up, the octave two strings up, and back: the shape under most bass lines.", PATTERN, inKeys = true) { r ->
                fretted("Bass", BASS, Clef.BASS, bassPosition(r, d("1 5 8 5")), KeyMode(r, "major"))
            })

            // Piano.
            add(CuratedExercise("piano-five-finger", ExerciseFor.PIANO, "Five-finger patterns", "Five-finger pattern", "1 2 3 4 5 4 3 2 1 with fingers 1 to 5, right hand; then the next key.", PATTERN, inKeys = true) { r ->
                spell(r, 4, d("1 2 3 4 5 4 3 2 1")).mapIndexed { i, p -> Note(p, finger = "123454321"[i].toString()) }.let { staff("Right hand", Clef.TREBLE, it, key = KeyMode(r, "major")) }
            })
            add(CuratedExercise("piano-major-scale", ExerciseFor.PIANO, "Scales", "Major scale, right hand", "One octave up and down, thumb under on the way up. Fingering shown for C, G, D, A, E and F, where it's the standard one.", "Standard piano scale fingering (C, G, D, A, E: 1 2 3 1 2 3 4 5; F: 1 2 3 4 1 2 3 4).", inKeys = true) { r ->
                scaleScore(r, upDown(UP8), "major", pianoScaleFingers(r).orEmpty())
            })
            add(CuratedExercise("piano-contrary", ExerciseFor.PIANO, "Scales", "C major in contrary motion", "Both thumbs on middle C, the hands moving apart and back with the same fingering.", "A standard piano scale exercise; fingering 1 2 3 1 2 3 4 5 in both hands.") { contrary() })
            add(CuratedExercise("piano-chromatic", ExerciseFor.PIANO, "Scales", "Chromatic scale, right hand", "3 on every black key, 1 on the white keys, 2 on F and on the top C.", "The standard right-hand chromatic fingering.") {
                staff("Right hand", Clef.TREBLE, chromatic())
            })
            add(CuratedExercise("piano-arpeggio", ExerciseFor.PIANO, "Arpeggios", "Triad arpeggio, right hand", "1 3 5 8 with fingers 1 2 3 5, and back.", "The standard one-octave right-hand fingering for a root-position triad on white keys (C, F, G).", inKeys = true) { r ->
                val fingers = if (r.toString() in setOf("C", "F", "G")) listOf("1", "2", "3", "5", "3", "2", "1") else emptyList()
                scaleScore(r, arpeggio("maj"), "major", fingers)
            })

            // Voice (written in treble clef; lower voices sing it an octave down).
            add(CuratedExercise("voice-five", ExerciseFor.VOICE, "Vocalises", "Five-tone scale", "1 2 3 4 5 4 3 2 1 on one breath, on \"ah\" or a lip trill; then up a half step and again.", "A standard vocal warm-up (vocalise).", inKeys = true) { r ->
                scaleScore(r, d("1 2 3 4 5 4 3 2 1"), "major")
            })
            add(CuratedExercise("voice-arpeggio", ExerciseFor.VOICE, "Vocalises", "Arpeggio 1 3 5 8 5 3 1", "The major arpeggio up to the octave and down, on one vowel; then up a half step.", "A standard vocal warm-up (vocalise).", inKeys = true) { r ->
                scaleScore(r, arpeggio("maj"), "major")
            })
            add(CuratedExercise("voice-nine", ExerciseFor.VOICE, "Vocalises", "Nine-tone scale", "1 up to 9 and back down on one breath; then up a half step.", "A standard vocal warm-up (vocalise).", inKeys = true) { r ->
                scaleScore(r, (1..9).map { DegreeToken(it) }.let { it + it.reversed().drop(1) }, "major")
            })
        }
    }

    fun byId(id: String): CuratedExercise? = all.firstOrNull { it.id == id }
}
