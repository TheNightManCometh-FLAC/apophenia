package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.instrument.Instrument
import com.betterappmafia.apophenia.core.instrument.Tuning
import com.betterappmafia.apophenia.core.instrument.Tunings
import com.betterappmafia.apophenia.core.interval.Interval
import com.betterappmafia.apophenia.core.interval.minus
import com.betterappmafia.apophenia.core.pitch.PitchClass
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.pitch.SpelledPitch
import com.betterappmafia.apophenia.core.rhythm.Duration
import com.betterappmafia.apophenia.core.score.Bar
import com.betterappmafia.apophenia.core.score.Clef
import com.betterappmafia.apophenia.core.score.Event
import com.betterappmafia.apophenia.core.score.Note
import com.betterappmafia.apophenia.core.score.Part
import com.betterappmafia.apophenia.core.score.Score

/*
 * Course · Instruments: the piano keyboard, the guitar and bass necks, tunings and capos, CAGED and barre shapes,
 * scale positions, drop 2 and drop 3, piano voicings (shells, rootless, spread) and the voice. Text written for
 * Apophenia; every fret, voicing and range in it is computed from the app's own tunings and checked by tests.
 */
internal object CourseInstruments : CourseKit() {
    private val q = Duration.QUARTER
    private val h = Duration.HALF
    private val w = Duration.WHOLE
    private val e = Duration.EIGHTH
    internal val GUITAR = Instrument(Tunings.GUITAR_STANDARD)
    internal val BASS = Instrument(Tunings.BASS_STANDARD)

    /** A MIDI note spelled from the default table (D♭ E♭ F♯ A♭ B♭). */
    internal fun spell(m: Int): SpelledPitch {
        val n = PitchClass(m).defaultSpelling()
        return n.at(Math.floorDiv(m - n.letter.natural - n.alter, 12) - 1)
    }
    private fun shown(p: SpelledPitch) = CourseIntervals.shown(p.name) + p.octave
    private fun shown(m: Int) = shown(spell(m))
    private fun signed(n: Int) = if (n > 0) "+$n" else if (n < 0) "−${-n}" else "0"
    internal val MAJOR_NAMES = listOf("C", "D♭", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B")

    private fun fretted(inst: Instrument, string: Int, fret: Int) = inst.tabNote(string, fret).let { Note(it.pitch, string = it.string, fret = it.fret) }
    /** A tab run: (string, fret) pairs as notes of [d], four beats to the bar, the last bar filled with rests. */
    internal fun run(inst: Instrument, clef: Clef, notes: List<Pair<Int, Int>>, d: Duration = e, perBar: Int = 8): Score {
        val events = notes.map { (s, f) -> Event(d, listOf(fretted(inst, s, f))) }
        val bars = events.chunked(perBar).map { b -> Bar(listOf(b + List(perBar - b.size) { Event(d) })) }
        return Score(parts = listOf(Part("", clef, inst, bars)))
    }
    /** Chords on one fretted part, one to a bar: each chord a list of (string, fret). */
    internal fun chords(inst: Instrument, clef: Clef, list: List<List<Pair<Int, Int>>>) =
        Score(parts = listOf(Part("", clef, inst, list.map { c -> Bar(listOf(listOf(Event(w, c.map { (s, f) -> fretted(inst, s, f) })))) })))
    /**
     * Piano chords on the grand staff, one to a bar: the lowest [left] notes of each on the bass staff (the left hand),
     * or without [left], notes from middle C up on the treble staff and the rest on the bass.
     */
    internal fun grand(list: List<List<String>>, left: Int? = null): Score {
        val ps = list.map { c -> c.map(SpelledPitch::parse).sortedBy { it.midi } }
        fun side(up: Boolean) = ps.map { c -> c.filterIndexed { i, p -> (if (left != null) i >= left else p.midi >= 60) == up }.let { n -> Bar(listOf(listOf(if (n.isEmpty()) Event(w) else Event(w, n.map { Note(it) })))) } }
        return Score(parts = listOf(Part("", Clef.TREBLE, bars = side(true)), Part("", Clef.BASS, bars = side(false))))
    }
    internal fun midis(c: List<String>) = c.map { SpelledPitch.parse(it).midi }
    private fun strum(midis: List<Int>, at: Double = 0.0) = midis.mapIndexed { i, m -> note(at + i * 0.05, 2.2 - i * 0.05, m) }
    private fun chordsInTurn(list: List<List<Int>>, step: Double = 2.0) = piano(list.flatMapIndexed { i, c -> RefSound.block(c, at = i * step, length = step * 0.95) })

    private fun one(m: Int) = Play(listOf(listOf(m)), 1.2)
    private fun tabSee(caption: String, sc: Score) = CourseSee.Score(caption, sc, listOf("GUITAR"))
    /** Frets 0..[max] across, one row per (label, lit frets), each row playing [play]. */
    private fun fretGrid(caption: String, max: Int, rows: List<Triple<String, Set<Int>, Play?>>): CourseSee.Grid {
        val cols = (0..max).map { "$it" }
        return CourseSee.Grid(caption, cols, rows.map { (l, lit, pl) -> row(l, cols, lit, pl) })
    }
    /** The five open shapes, frets for strings 6 to 1 (null: not played). */
    internal val OPEN_SHAPES = listOf(
        "C" to listOf(null, 3, 2, 0, 1, 0), "A" to listOf(null, 0, 2, 2, 2, 0), "G" to listOf(3, 2, 0, 0, 0, 3),
        "E" to listOf(0, 2, 2, 1, 0, 0), "D" to listOf(null, null, 0, 2, 3, 2),
    )
    private fun shapeMidis(frets: List<Int?>, capo: Int = 0) = shapeNotes(frets).map { (s, f) -> GUITAR.midiAt(s, f + capo) }

    // ------------------------------------------------------------------------------------------------- piano

    internal val KEYS = 21..108
    private val WHITE = setOf(0, 2, 4, 5, 7, 9, 11)
    internal fun whiteKeys() = KEYS.count { Math.floorMod(it, 12) in WHITE }

    private val keyboard = CoursePage(
        "keyboard", "The piano keyboard", 1,
        "The keyboard repeats one pattern twelve keys wide. Learn the pattern and every key names itself.",
        steps = listOf(
            say("Seven white keys and five black ones, the black keys in a group of two and a group of three. C is the white key " +
                "just left of the two, F just left of the three; from C the white keys run C D E F G A B.",
                keys("FIND C AND F BY THE BLACK KEYS", "C4 F4", listOf("C", "F"), faint = "C#4 D#4 F#4 G#4 A#4",
                    play = Play(listOf(listOf(60), listOf(65)), 0.8))),
            say("Each key, white or black, is a half step from its neighbour; where two white keys touch with no black key between " +
                "(E–F and B–C) they're a half step apart too. The black keys take the sharps and flats, each with two names. Tap " +
                "down the octave.",
                table("ONE OCTAVE OF KEYS", listOf("KEY", "COLOUR", "NAMES"), (60..71).map { m ->
                    val white = Math.floorMod(m, 12) in WHITE
                    val names = if (white) shown(spell(m).name) else "${shown(spell(m - 1).name)}♯ · ${shown(spell(m + 1).name)}♭"
                    listOf("${m - 59}", if (white) "white" else "black", names)
                }, (60..71).map(::one))),
            say("Played alone, the black keys are a pentatonic scale, F♯ G♯ A♯ C♯ D♯: which is why anything you play on them " +
                "alone sounds tuneful.",
                keysLine("THE BLACK KEYS · A PENTATONIC", "F#4 G#4 A#4 C#5 D#5 F#5", step = 0.4)),
            say("A full piano has ${KEYS.count()} keys, ${shown(KEYS.first)} to ${shown(KEYS.last)}, ${whiteKeys()} white and " +
                "${KEYS.count() - whiteKeys()} black. Middle C, C4, is the fourth C from the bottom.",
                keys("COUNTING C'S UP TO MIDDLE C", "C1 C2 C3 C4", listOf("C1", "C2", "C3", "C4"), play = Play(mids("C1 C2 C3 C4").map { listOf(it) }, 0.7))),
        ),
        inShort = listOf(
            "Twelve keys repeat: 7 white, 5 black in twos and threes.",
            "C left of the two black keys, F left of the three.",
            "Neighbouring keys are a half step apart; middle C is the fourth C.",
        ),
        hear = listOf(
            sound("WHITE KEYS FROM C") { piano(seq(listOf(60, 62, 64, 65, 67, 69, 71, 72), step = 0.4)) },
            sound("BLACK KEYS · A PENTATONIC SCALE") { piano(seq(listOf(66, 68, 70, 73, 75, 78), step = 0.4)) },
        ),
        tryIt = CourseTry("A SCALE ON THE KEYS · PICK THE PIANO", RefKind.SCALE, "major", "C"),
        related = listOf(page("names-octaves", "Octave numbers"), page("names-enharmonic", "One key, two names")),
    )
    private fun shown(n: PitchName) = CourseIntervals.shown(n)

    // ------------------------------------------------------------------------------------------------- guitar neck

    /** Frets up from string [s] to the same note an octave up, two strings higher. */
    internal fun octaveShape(inst: Instrument, s: Int) = inst.tuning.open(s).midi + 12 - inst.tuning.open(s - 2).midi
    /** Every (string, fret) where [midi] sounds, up to [maxFret]. */
    internal fun places(inst: Instrument, midi: Int, maxFret: Int = 15) =
        (inst.tuning.stringCount downTo 1).mapNotNull { s -> (midi - inst.tuning.open(s).midi).takeIf { it in 0..maxFret }?.let { s to it } }

    private val guitarNeck = CoursePage(
        "guitar-neck", "Strings and frets", 1,
        "A guitar neck is a grid: six strings across, frets along. Two facts about it explain nearly everything else.",
        steps = listOf(
            say("The strings are numbered from the thinnest: string 1 is the high E, string 6 the low E. Standard tuning, E A D G B E " +
                "from the bottom, goes up in perfect fourths, except G to B, a major third. Tap a string.",
                table("STANDARD TUNING", listOf("STRING", "OPEN", "MIDI", "UP TO NEXT"), (6 downTo 1).map { s ->
                    val open = GUITAR.tuning.open(s)
                    val next = if (s > 1) Interval.between(open, GUITAR.tuning.open(s - 1)).toString() else "—"
                    listOf("$s", shown(open), "${open.midi}", next)
                }, (6 downTo 1).map { one(GUITAR.tuning.open(it).midi) })),
            say("Each fret raises a string by a half step, so twelve frets is an octave: the 12th fret is the open string's note " +
                "again, which is why the dots double there. Each row plays the open string, then its 12th fret.",
                fretGrid("OPEN AND 12TH FRET, EVERY STRING", 12, (6 downTo 1).map { s ->
                    Triple("$s · ${shown(GUITAR.tuning.open(s))}", setOf(0, 12), Play(listOf(listOf(GUITAR.midiAt(s, 0)), listOf(GUITAR.midiAt(s, 12))), 0.8))
                })),
            say("Because the strings overlap, most notes can be played in more than one place. Middle C sounds on four strings below " +
                "the 15th fret: the same pitch, four different colours.",
                fretGrid("MIDDLE C · EVERY PLACE BELOW THE 15TH FRET", 15, places(GUITAR, 60).map { (s, f) -> Triple("string $s", setOf(f), one(60)) })),
            say("Guitar music is written an octave higher than it sounds, so that middle C is written on the third space of the " +
                "treble staff (the clef with a small 8 under it says so).",
                tabSee("MIDDLE C, STRING 2 FRET 1", chords(GUITAR, Clef.TREBLE_8VB, listOf(listOf(2 to 1))))),
        ),
        inShort = listOf(
            "Strings 6–1: E A D G B E, fourths except G–B (a major third).",
            "One fret = a half step; 12 frets = an octave.",
            "Most notes sit in several places; guitar is written an octave up.",
        ),
        hear = listOf(
            sound("THE OPEN STRINGS, LOW TO HIGH") { piano(seq((6 downTo 1).map { GUITAR.tuning.open(it).midi }, step = 0.5)) },
            sound("MIDDLE C ON FOUR STRINGS") { piano(seq(places(GUITAR, 60).map { 60 }, step = 0.6)) },
        ),
        tryIt = CourseTry("A SCALE ON YOUR NECK · EVERY PLACE ITS NOTES SIT", RefKind.SCALE, "major", "C"),
        related = listOf(page("guitar-octaves", "Octave shapes"), page("tab", "Reading tab")),
    )

    private val octaveRun = run(GUITAR, Clef.TREBLE_8VB, listOf(5 to 3, 3 to 5, 1 to 8), d = h, perBar = 2)

    private val guitarOctaves = CoursePage(
        "guitar-octaves", "Octave shapes", 1,
        "The same note an octave up is always two strings higher and a few frets along. Two shapes cover the whole neck.",
        steps = listOf(
            say("From the 6th or 5th string it's two strings up and two frets up. From the 4th or 3rd string it's three frets up, " +
                "because the octave has to cross the B string, tuned a half step lower than the fourths pattern would have it.",
                tabSee("THE TWO SHAPES · FROM STRING 6, FROM STRING 4", chords(GUITAR, Clef.TREBLE_8VB, listOf(listOf(6 to 3, 4 to 5), listOf(4 to 2, 2 to 5))))),
            say("Every pair of strings, and every place one note sits. Tap a row to hear the octave.",
                table("OCTAVE SHAPES AND THE PLACES OF ONE NOTE", listOf("FROM", "TO", "FRETS"),
                    (6 downTo 3).map { s -> listOf("string $s", "string ${s - 2}", signed(octaveShape(GUITAR, s))) } +
                        listOf(listOf("C4", "every place", places(GUITAR, 60).joinToString(" · ") { (s, f) -> "$s/$f" })),
                    (6 downTo 3).map { s -> Play(listOf(listOf(GUITAR.midiAt(s, 3)), listOf(GUITAR.midiAt(s, 3) + 12)), 0.7) } + listOf(Play(places(GUITAR, 60).map { listOf(60) }, 0.6)))),
            say("Chained, they climb the neck: C3, C4, C5 as 5th string 3rd fret, 3rd string 5th fret, 1st string 8th fret. With the " +
                "octave shapes and the notes on the two lowest strings, any note on the neck can be found in a moment.",
                tabSee("C3 · C4 · C5", octaveRun)),
        ),
        inShort = listOf(
            "Strings 6 and 5: octave = two strings up, two frets up.",
            "Strings 4 and 3: two strings up, three frets up (the B string).",
            "Learn the low two strings and the shapes find everything else.",
        ),
        hear = listOf(sound("C3 · C4 · C5") { piano(seq(listOf(48, 60, 72), step = 0.7, len = 0.65)) }),
        related = listOf(page("guitar-neck", "Strings and frets"), page("names-octaves", "Octave numbers")),
    )

    // ------------------------------------------------------------------------------------------------- bass neck

    internal val BASSES = listOf(Tunings.BASS_STANDARD, Tunings.BASS_5_STRING, Tunings.BASS_6_STRING)
    internal val bassLine = run(BASS, Clef.BASS, listOf(4 to 5, 3 to 7, 2 to 7, 3 to 7, 3 to 5, 2 to 7, 1 to 7, 2 to 7), d = q, perBar = 4)

    private val bassNeck = CoursePage(
        "bass-neck", "The bass neck", 1,
        "The bass neck is the guitar's lower strings an octave down, tuned evenly, so every shape works everywhere.",
        steps = listOf(
            say("A four-string bass is tuned E A D G, the same notes as the guitar's four lowest strings an octave lower, all in " +
                "fourths, so every shape is the same on every string pair. A five-string adds a low B a fourth below the E; a " +
                "six-string adds a high C above the G as well.",
                table("BASS TUNINGS", listOf("", "STRINGS, LOW TO HIGH"), BASSES.map { t -> listOf(t.name, t.strings.joinToString(" ") { shown(it) }) },
                    BASSES.map { t -> Play(t.strings.map { listOf(it.midi) }, 0.5) })),
            say("Bass is written in bass clef an octave higher than it sounds, which keeps the low E on a ledger line below the " +
                "staff instead of five.",
                CourseSee.Score("THE OPEN LOW E", chords(BASS, Clef.BASS, listOf(listOf(4 to 0))), listOf("BASS"))),
            say("The most used shape on the instrument: root, fifth two frets up on the next string, octave two strings up at the " +
                "same fret as the fifth. Here it is on A, then on D.",
                CourseSee.Score("ROOT · FIFTH · OCTAVE ON A, THEN ON D", bassLine, listOf("BASS"))),
        ),
        inShort = listOf(
            "Four-string E A D G, all fourths; five-string adds low B, six-string high C.",
            "Written in bass clef an octave above how it sounds.",
            "Root, fifth (+2 frets, next string), octave (two strings up).",
        ),
        hear = listOf(sound("PLAY THE LINE") { CourseReading.played(bassLine) }),
        related = listOf(page("guitar-neck", "Strings and frets"), ref(RefKind.INTERVAL, "P5", "Perfect fifth")),
    )

    // ------------------------------------------------------------------------------------------------- tunings and capos

    internal val GUITAR_TUNINGS = Tunings.all.filter { it.id.startsWith("guitar") && it.stringCount == 6 }
    /** (string number, semitones) for each string a tuning moves from standard. */
    internal fun changes(t: Tuning) = (6 downTo 1).mapNotNull { s ->
        (t.open(s).midi - Tunings.GUITAR_STANDARD.open(s).midi).takeIf { it != 0 }?.let { s to it }
    }
    private fun openMidis(t: Tuning) = t.strings.map { it.midi }

    private val tuningsPage = CoursePage(
        "tunings-alt", "Alternate tunings", 2,
        "Retuning changes what the open strings give you, and every shape you know lands somewhere new.",
        steps = listOf(
            say("Drop D lowers the 6th string a whole step. The bottom three strings then make a D power chord with one finger, and " +
                "the low end goes down to D.",
                keys("DROP D · THE BOTTOM THREE OPEN STRINGS", "D2 A2 D3", listOf("6 · D", "5 · A", "4 · D"))),
            say("D standard drops every string a whole step: the same shapes, a step lower. Open tunings make the open strings a " +
                "chord: open G sounds G major, open D sounds D major, and a finger across one fret plays the chord a half step up. " +
                "DADGAD is neither major nor minor, D and A with a G, and rings with suspended colour. Tap a tuning.",
                table("SIX-STRING TUNINGS", listOf("TUNING", "STRINGS 6–1", "CHANGED"), GUITAR_TUNINGS.map { t ->
                    listOf(t.name, t.strings.joinToString(" ") { CourseIntervals.shown(it.name) }, changes(t).joinToString(" · ") { (s, d) -> "$s: ${signed(d)}" }.ifEmpty { "—" })
                }, GUITAR_TUNINGS.map { t -> Play(listOf(openMidis(t)), 2.0, spread = true) })),
            say("Every shape you know moves on the strings that changed, so new tunings sound new partly because the hands can't " +
                "fall into habits."),
        ),
        inShort = listOf(
            "Drop D: 6th string down a whole step.",
            "Open tunings sound a chord on the open strings; DADGAD a suspended one.",
            "Shapes move on the retuned strings.",
        ),
        hear = GUITAR_TUNINGS.map { t -> sound(t.name.uppercase() + " · OPEN STRINGS") { piano(strum(openMidis(t))) } },
        check = CourseCheck("Which tuning are the open strings in?", GUITAR_TUNINGS.map { t ->
            option(t.name, "${t.name}: ${t.strings.joinToString(" ") { CourseIntervals.shown(it.name) }}.") { _, _ -> piano(strum(openMidis(t))) }
        }),
        related = listOf(page("capo", "What a capo does"), page("guitar-neck", "Strings and frets")),
    )

    internal val SHAPE_ROOTS = listOf("C" to 0, "A" to 9, "G" to 7, "E" to 4, "D" to 2)
    internal fun capoSounds(shapeRoot: Int, capo: Int) = MAJOR_NAMES[Math.floorMod(shapeRoot + capo, 12)]

    private val capo = CoursePage(
        "capo", "What a capo does", 2,
        "A capo clamps every string at one fret, making a new nut: everything sounds that many half steps higher while the " +
            "hands play the same open shapes.",
        steps = listOf(
            say("Each open shape sounds a different chord depending on where the capo is. Tap a row to hear the five shapes at that " +
                "capo, C A G E D.",
                table("WHAT EACH OPEN SHAPE SOUNDS", listOf("CAPO") + SHAPE_ROOTS.map { "${it.first} SHAPE" }, (0..7).map { c ->
                    listOf(if (c == 0) "none" else "$c") + SHAPE_ROOTS.map { capoSounds(it.second, c) }
                }, (0..7).map { c -> Play(OPEN_SHAPES.map { shapeMidis(it.second, c) }, 1.2) })),
            say("Its two uses are moving a song into a singer's key without learning new chords, and getting the ring of open " +
                "strings in keys like E♭ or B♭ that have few of them. A G shape with the capo at the 3rd fret sounds B♭; at the " +
                "2nd, A."),
            say("Players name chords by the shape (\"play G, capo 3\"), so a chart should say which it means. Tab with a capo counts " +
                "frets from the capo, so a capo-3 G shape is still written 3 2 0 0 0 3."),
        ),
        inShort = listOf(
            "Capo = a movable nut; shapes stay, pitch rises a half step per fret.",
            "Use it for a singer's key or for open strings in flat keys.",
            "Say whether a chord name means the shape or the sound.",
        ),
        hear = listOf(
            sound("G SHAPE, NO CAPO") { piano(strum(listOf(43, 47, 50, 55, 59, 67))) },
            sound("G SHAPE, CAPO 3 · B♭") { piano(strum(listOf(43, 47, 50, 55, 59, 67).map { it + 3 })) },
        ),
        related = listOf(page("tunings-alt", "Alternate tunings"), page("caged", "CAGED")),
    )

    // ------------------------------------------------------------------------------------------------- CAGED

    /** C major in the five shapes up the neck: name and frets for strings 6 to 1 (null: not played). */
    internal val CAGED = listOf(
        "C" to listOf(null, 3, 2, 0, 1, 0),
        "A" to listOf(null, 3, 5, 5, 5, 3),
        "G" to listOf(8, 7, 5, 5, 5, 8),
        "E" to listOf(8, 10, 10, 9, 8, 8),
        "D" to listOf(null, null, 10, 12, 13, 12),
    )
    internal fun shapeNotes(frets: List<Int?>) = frets.mapIndexedNotNull { i, f -> f?.let { (6 - i) to it } }
    internal val cagedScore = chords(GUITAR, Clef.TREBLE_8VB, CAGED.map { shapeNotes(it.second) })

    private val caged = CoursePage(
        "caged", "CAGED", 2,
        "Five open chords, C, A, G, E and D, are the five ways one major chord can lie under the hand. Together they map " +
            "the whole neck.",
        steps = listOf(
            say("Here are the five shapes as you first learn them, open at the nut: C, A, G, E and D major.",
                tabSee("THE FIVE OPEN SHAPES · C A G E D", chords(GUITAR, Clef.TREBLE_8VB, OPEN_SHAPES.map { shapeNotes(it.second) }))),
            say("Move them up the neck, a finger or barre taking the nut's place, and they link in that order for any chord. For C: " +
                "the open C shape, then the A shape at the 3rd fret, the G shape around the 5th, the E shape at the 8th, the D " +
                "shape at the 10th, and the C shape again an octave up at the 12th.",
                CourseSee.Score("C MAJOR IN THE FIVE SHAPES · C A G E D", cagedScore, listOf("GUITAR"))),
            say("Each shape has its root in a known place, and the scale and arpeggio patterns sit around the same shapes. That's the " +
                "system's real use: knowing which chord tone is under each finger anywhere on the neck.",
                table("WHERE EACH SHAPE KEEPS ITS ROOT", listOf("SHAPE", "ROOT ON STRINGS"), listOf(
                    listOf("C", "5 and 2"), listOf("A", "5 and 3"), listOf("G", "6, 3 and 1"), listOf("E", "6, 4 and 1"), listOf("D", "4 and 2"),
                ))),
        ),
        inShort = listOf(
            "C A G E D: five shapes of one major chord.",
            "They link up the neck in that order, then repeat at the 12th fret.",
            "Know where each shape's root is.",
        ),
        hear = listOf(sound("THE FIVE SHAPES IN TURN") { piano(CAGED.flatMapIndexed { i, (_, f) -> strum(shapeNotes(f).map { (s, fr) -> GUITAR.midiAt(s, fr) }, at = i * 2.2) }) }),
        tryIt = CourseTry("A CHORD'S SHAPES ON YOUR NECK", RefKind.CHORD, "maj", "C"),
        related = listOf(page("barre", "Barre chords"), page("pent-positions", "Pentatonic positions")),
    )

    private val barre = CoursePage(
        "barre", "Barre chords", 2,
        "The E and A shapes are the two that move most easily: a finger barres the fret and the shape sits above it.",
        steps = listOf(
            say("G as an E-shape barre at the 3rd fret, then C as an A-shape barre at the 3rd fret. Same fret, different string for " +
                "the root.",
                tabSee("G (E SHAPE) · C (A SHAPE), BOTH AT THE 3RD", chords(GUITAR, Clef.TREBLE_8VB, listOf(
                    listOf(6 to 3, 5 to 5, 4 to 5, 3 to 4, 2 to 3, 1 to 3), listOf(5 to 3, 4 to 5, 3 to 5, 2 to 5, 1 to 3))))),
            say("An E-shape barre has its root on the 6th string, an A-shape barre on the 5th, so naming the notes on those two " +
                "strings names every barre chord. Tap a fret to hear both roots.",
                table("THE ROOT UNDER THE BARRE", listOf("FRET", "E SHAPE (6TH)", "A SHAPE (5TH)"), (0..12).map { f ->
                    listOf("$f", MAJOR_NAMES[Math.floorMod(GUITAR.midiAt(6, f), 12)], MAJOR_NAMES[Math.floorMod(GUITAR.midiAt(5, f), 12)])
                }, (0..12).map { f -> Play(listOf(listOf(GUITAR.midiAt(6, f)), listOf(GUITAR.midiAt(5, f))), 0.6) })),
            say("The minor versions are the Em and Am shapes: the 3rd drops one fret in each. Between them the two shapes give two " +
                "places for every chord within five frets, which is how most rhythm parts get played.",
                tabSee("Gm (Em SHAPE) · Cm (Am SHAPE)", chords(GUITAR, Clef.TREBLE_8VB, listOf(
                    listOf(6 to 3, 5 to 5, 4 to 5, 3 to 3, 2 to 3, 1 to 3), listOf(5 to 3, 4 to 5, 3 to 5, 2 to 4, 1 to 3))))),
        ),
        inShort = listOf(
            "E shape: root on the 6th string. A shape: root on the 5th.",
            "Minor: the 3rd drops a fret.",
            "Two shapes, two places for every chord.",
        ),
        hear = listOf(
            sound("G · E SHAPE AT THE 3RD") { piano(strum(listOf(3, 5, 5, 4, 3, 3).mapIndexed { i, f -> GUITAR.midiAt(6 - i, f) })) },
            sound("C · A SHAPE AT THE 3RD") { piano(strum(listOf(3, 5, 5, 5, 3).mapIndexed { i, f -> GUITAR.midiAt(5 - i, f) })) },
        ),
        tryIt = CourseTry("MAJOR AND MINOR SHAPES", RefKind.CHORD, "m", "A"),
        related = listOf(page("caged", "CAGED")),
    )

    // ------------------------------------------------------------------------------------------------- scale positions

    /**
     * A scale position: from [start] on the lowest string, [perString] scale notes on each string in turn, low to high.
     * [pcs] are the scale's pitch classes. Returns (string, fret) pairs.
     */
    internal fun position(inst: Instrument, pcs: Set<Int>, start: Int, perString: Int): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        var m = start
        for (s in inst.tuning.stringCount downTo 1) repeat(perString) {
            out += s to (m - inst.tuning.open(s).midi)
            m++
            while (Math.floorMod(m, 12) !in pcs) m++
        }
        return out
    }
    internal val A_MINOR_PENT = setOf(9, 0, 2, 4, 7)
    internal val G_MAJOR = setOf(7, 9, 11, 0, 2, 4, 6)
    /** The five pentatonic positions in A, each starting on a scale note on the 6th string from the 5th fret. */
    internal val PENT = listOf(5, 8, 10, 12, 15).map { f -> position(GUITAR, A_MINOR_PENT, GUITAR.midiAt(6, f), 2) }
    internal val THREE_NPS = listOf(3, 5, 7, 8, 10, 12, 14).map { f -> position(GUITAR, G_MAJOR, GUITAR.midiAt(6, f), 3) }
    internal fun threeSpan() = THREE_NPS.maxOf { p -> p.maxOf { it.second } - p.minOf { it.second } }
    private fun byString(p: List<Pair<Int, Int>>) = p.groupBy({ it.first }, { it.second }).entries.joinToString(" · ") { (_, f) -> f.joinToString(" ") }

    private val pentPositions = CoursePage(
        "pent-positions", "Pentatonic positions", 3,
        "A position is a box of frets the hand covers without moving. Scales are learnt as positions so the hand knows the " +
            "whole neck, not one line.",
        steps = listOf(
            say("Box 1 of A minor pentatonic starts on the 5th fret, two notes on each string: the one everybody learns first.",
                tabSee("A MINOR PENTATONIC · BOX 1", run(GUITAR, Clef.TREBLE_8VB, PENT[0]))),
            say("There are five boxes and they join end to end: the top note of each string in one box is the bottom of the next. " +
                "The fifth box ends at the 17th fret, where box 1 starts again an octave up. Tap a box to hear it.",
                table("A MINOR PENTATONIC · FRETS ON STRINGS 6 TO 1", listOf("BOX", "STARTS", "FRETS"), PENT.mapIndexed { i, p ->
                    listOf("${i + 1}", "${shown(spell(GUITAR.midiAt(p[0].first, p[0].second)).name)} · fret ${p[0].second}", byString(p))
                }, PENT.map { p -> Play(p.map { (s, f) -> listOf(GUITAR.midiAt(s, f)) }, 0.25) })),
            say("The same five boxes are the relative major pentatonic, C major here; only the note you treat as home changes. Each " +
                "box also sits over one of the CAGED shapes, which is how players see chord tones inside a scale.",
                tabSee("BOX 2", run(GUITAR, Clef.TREBLE_8VB, PENT[1]))),
        ),
        inShort = listOf(
            "Five boxes, two notes per string, joined end to end.",
            "A minor pentatonic box 1 starts at the 5th fret.",
            "Same boxes = C major pentatonic; each sits over a CAGED shape.",
        ),
        hear = listOf(sound("BOX 1 UP") { piano(seq(PENT[0].map { (s, f) -> GUITAR.midiAt(s, f) }, step = 0.3, len = 0.28)) }),
        tryIt = CourseTry("THE MINOR PENTATONIC ON YOUR NECK", RefKind.SCALE, "minor-pentatonic", "A"),
        related = listOf(page("three-nps", "Three notes per string"), page("caged", "CAGED"), ref(RefKind.SCALE, "minor-pentatonic", "Minor pentatonic", "A")),
    )

    private val threeScore = run(GUITAR, Clef.TREBLE_8VB, THREE_NPS[0])

    private val threeNps = CoursePage(
        "three-nps", "Three notes per string", 3,
        "Seven-note scales are often fingered three notes to a string, which gives every string the same picking: three " +
            "notes, change string.",
        steps = listOf(
            say("G major from G on the 6th string, 3rd fret, up eighteen notes to C on the 1st string.",
                CourseSee.Score("G MAJOR, THREE NOTES PER STRING", threeScore, listOf("GUITAR"))),
            say("That makes seven positions, one starting on each degree. The stretch is wider than a pentatonic box: from the lowest " +
                "fret to the highest is as much as ${threeSpan()} frets in some positions, covered by stretching or a small shift.",
                table("SEVEN POSITIONS OF G MAJOR", listOf("POSITION", "STARTS", "SPAN"), THREE_NPS.mapIndexed { i, p ->
                    listOf("${i + 1}", "${shown(spell(GUITAR.midiAt(p[0].first, p[0].second)).name)} · fret ${p[0].second}", "${p.maxOf { it.second } - p.minOf { it.second } + 1} frets")
                }, THREE_NPS.map { p -> Play(p.map { (s, f) -> listOf(GUITAR.midiAt(s, f)) }, 0.2) })),
            say("The patterns repeat in a way the pentatonic boxes don't, and they run straight into fast legato and alternate-picked " +
                "lines."),
        ),
        inShort = listOf(
            "Three notes per string: seven positions, even picking.",
            "Wider stretches than the boxes.",
            "Built for speed: legato and alternate picking.",
        ),
        hear = listOf(sound("PLAY THE RUN") { CourseReading.played(threeScore) }),
        tryIt = CourseTry("THE MAJOR SCALE ON YOUR NECK", RefKind.SCALE, "major", "G"),
        related = listOf(page("pent-positions", "Pentatonic positions")),
    )

    // ------------------------------------------------------------------------------------------------- drop 2 and drop 3

    /** A close voicing with its [k]th voice from the top dropped an octave, low to high. */
    internal fun drop(close: List<Int>, k: Int): List<Int> {
        val s = close.sorted()
        return s.mapIndexed { i, m -> if (i == s.size - k) m - 12 else m }.sorted()
    }
    /** The four close inversions of Cmaj7 from C4. */
    internal val CLOSE = listOf(listOf(60, 64, 67, 71), listOf(64, 67, 71, 72), listOf(67, 71, 72, 76), listOf(71, 72, 76, 79))
    /** Frets for a voicing on the given strings, moved by octaves into reach (lowest fret 0 to 11). */
    internal fun frets(strings: List<Int>, v: List<Int>): List<Int> {
        var f = strings.zip(v) { s, m -> m - GUITAR.tuning.open(s).midi }
        while (f.min() < 0) f = f.map { it + 12 }
        while (f.min() >= 12) f = f.map { it - 12 }
        return f
    }
    internal val D2_STRINGS = listOf(4, 3, 2, 1)
    internal val D3_STRINGS = listOf(6, 4, 3, 2)
    private fun names(v: List<Int>) = v.joinToString(" ") { shown(spell(it).name) }

    private val dropPage = CoursePage(
        "drop", "Drop 2 and drop 3", 4,
        "A close voicing, every note as near as it can be, is easy on the piano and mostly impossible on the guitar. Drop " +
            "voicings fix that.",
        steps = listOf(
            say("Cmaj7 close: C E G B, all within an octave. On adjacent guitar strings that needs a stretch no hand makes.",
                keys("CMAJ7 · CLOSE", "C4 E4 G4 B4", listOf("1", "3", "5", "7"))),
            say("Take the second voice from the top (G) down an octave and you get drop 2: G C E B, which lies on four adjacent " +
                "strings.",
                keys("DROP 2 · THE G GOES DOWN AN OCTAVE", "G3 C4 E4 B4", listOf("5", "1", "3", "7"))),
            say("Drop the third voice from the top (E) instead and you get drop 3: E C G B, wider, with the bass on the 6th or 5th " +
                "string and a string skipped above it.",
                keys("DROP 3 · THE E GOES DOWN AN OCTAVE", "E3 C4 G4 B4", listOf("3", "1", "5", "7"))),
            say("Each inversion of the close chord gives its own drop voicing, so a seventh chord has four of each, a set that moves " +
                "up the neck with the bass note changing. Jazz guitar comping is built on them, and big-band saxophone writing " +
                "too. Each row plays close, drop 2, drop 3.",
                table("CMAJ7 · DROP 2 ON STRINGS 4–1, DROP 3 ON 6 4 3 2", listOf("CLOSE", "DROP 2", "FRETS", "DROP 3", "FRETS"), CLOSE.map { c ->
                    val d2 = drop(c, 2); val d3 = drop(c, 3)
                    listOf(names(c), names(d2), frets(D2_STRINGS, d2).joinToString(" "), names(d3), frets(D3_STRINGS, d3).joinToString(" "))
                }, CLOSE.map { c -> Play(listOf(c, drop(c, 2), drop(c, 3)), 1.4) })),
        ),
        inShort = listOf(
            "Drop 2: second voice from the top down an octave; four adjacent strings.",
            "Drop 3: third voice from the top down; a string skipped.",
            "Four inversions give four of each.",
        ),
        hear = listOf(
            sound("CLOSE · DROP 2 · DROP 3") { r -> chordsInTurn(listOf(0, 2, 3).map { k -> val c = listOf(r, r + 4, r + 7, r + 11); if (k == 0) c else drop(c, k) }) },
        ),
        check = CourseCheck("Close, drop 2 or drop 3?", listOf(0 to "Close", 2 to "Drop 2", 3 to "Drop 3").map { (k, n) ->
            option(n, if (k == 0) "Close: all four notes within an octave." else "$n: the ${if (k == 2) "second" else "third"} voice from the top dropped an octave.") { r, _ ->
                val c = listOf(r, r + 4, r + 7, r + 11)
                piano(RefSound.block(if (k == 0) c else drop(c, k), length = 2.4))
            }
        }, roots = 55..64),
        tryIt = CourseTry("A MAJOR SEVENTH'S SHAPES ON YOUR NECK", RefKind.CHORD, "maj7", "C"),
        related = listOf(page("sevenths", "Seventh chords"), ear("ch-7-all", "seventh chords")),
    )

    // ------------------------------------------------------------------------------------------------- piano voicings

    internal val SHELLS = listOf(listOf("D3", "C4"), listOf("G3", "B3"), listOf("C3", "B3"))
    internal val ROOTLESS = listOf(listOf("F3", "A3", "C4", "E4"), listOf("F3", "A3", "B3", "E4"), listOf("E3", "G3", "B3", "D4"))
    internal val SPREAD = listOf(listOf("D2", "C3", "F3", "A3", "E4"), listOf("G2", "F3", "B3", "E4", "A4"), listOf("C2", "B2", "E3", "G3", "D4"))
    internal val II_V_I = listOf(2, 7, 0)

    private val shells = CoursePage(
        "shells", "Shells", 4,
        "A shell voicing is the root with just one guide tone. It's the left hand's minimum that still says what the chord is.",
        steps = listOf(
            say("Root and seventh, or root and third, played low. The left hand leaves the middle to the right hand or the band.",
                keys("DM7 AS A SHELL · ROOT AND 7TH", "D3 C4", listOf("root", "7th"))),
            say("Alternating the two keeps the voices moving by step through a ii–V–I: Dm7 as root and seventh (D, C), G7 as root " +
                "and third (G, B), Cmaj7 as root and seventh (C, B). The upper voice goes C, B, B while the roots move round the " +
                "circle.",
                CourseSee.Score("ii–V–I IN C · DM7 G7 CMAJ7 AS SHELLS", grand(SHELLS), listOf("", ""))),
            say("Bud Powell's left hand is the classic sound. Add the missing guide tone on top and you have a three-note shell, " +
                "1–7–3 or 1–3–7.",
                CourseSee.Score("THREE-NOTE SHELLS · 1–7–3, 1–3–7, 1–7–3", grand(listOf(listOf("D3", "C4", "F4"), listOf("G3", "B3", "F4"), listOf("C3", "B3", "E4"))), listOf("", ""))),
        ),
        inShort = listOf(
            "Shell = root + 3rd or root + 7th.",
            "Alternate them through ii–V–I so the top voice steps.",
            "Add the other guide tone for a three-note shell.",
        ),
        hear = listOf(sound("THE SHELLS") { chordsInTurn(SHELLS.map(::midis)) }),
        check = CourseCheck("Root and third, or root and seventh?", listOf(4 to "1–3", 10 to "1–7").map { (i, n) ->
            option(n, if (i == 4) "Root and major third." else "Root and minor seventh.") { r, _ -> piano(RefSound.block(listOf(r, r + i), length = 2.0)) }
        }, roots = 45..55),
        related = listOf(page("rootless", "Rootless voicings"), page("jazz-251", "ii–V–I")),
    )

    private val rootless = CoursePage(
        "rootless", "Rootless voicings", 4,
        "Leave the root to the bass player and the left hand can carry the colour instead.",
        steps = listOf(
            say("Four notes: the third and seventh with a fifth, ninth or thirteenth. Dm9 without its root is F A C E.",
                keys("DM9 WITHOUT ITS ROOT", "F3 A3 C4 E4", listOf("3", "5", "7", "9"))),
            say("In one form the voicing starts on the third (3 5 7 9), in the other on the seventh (7 9 3 13 for a dominant). " +
                "Alternating them moves every voice by a step or not at all through a ii–V–I: Dm9 as F A C E, G13 as F A B E, " +
                "Cmaj9 as E G B D.",
                CourseSee.Score("ii–V–I IN C · DM9 G13 CMAJ9 WITHOUT ROOTS", grand(ROOTLESS), listOf("", ""))),
            say("These are the voicings heard on Bill Evans's and Wynton Kelly's records from the late 1950s. They sit around middle " +
                "C, low enough to be full and high enough not to muddy, with the bass player's D, G and C underneath.",
                table("WITH THE BASS UNDERNEATH", listOf("", "BASS", "LEFT HAND"), listOf("Dm9", "G13", "Cmaj9").mapIndexed { i, n ->
                    listOf(n, MAJOR_NAMES[II_V_I[i]], ROOTLESS[i].joinToString(" ") { CourseIntervals.shown(SpelledPitch.parse(it).name) })
                }, ROOTLESS.mapIndexed { i, c -> Play(listOf(listOf(36 + II_V_I[i]) + midis(c)), 1.8) })),
        ),
        inShort = listOf(
            "Rootless = 3 and 7 plus colour tones; the bass has the root.",
            "Two forms (from the 3rd, from the 7th) alternate through ii–V–I.",
            "Keep them around middle C.",
        ),
        hear = listOf(
            sound("THE ROOTLESS VOICINGS") { chordsInTurn(ROOTLESS.map(::midis)) },
            sound("WITH THE BASS NOTES") { chordsInTurn(ROOTLESS.mapIndexed { i, c -> listOf(36 + II_V_I[i]) + midis(c) }) },
        ),
        related = listOf(page("shells", "Shells"), page("spread", "Spread voicings"), page("extensions", "Extensions")),
    )

    private val spread = CoursePage(
        "spread", "Spread voicings", 4,
        "A spread voicing uses both hands across the whole middle of the keyboard: the big solo-piano sound.",
        steps = listOf(
            say("The left hand plays the root low with the seventh above it (or a tenth); the right hand the third, a fifth or " +
                "tension, and the top note.",
                keys("DM9 SPREAD", "D2 C3 F3 A3 E4", listOf("1", "♭7", "♭3", "5", "9"))),
            say("Through a ii–V–I: Dm9 with D and C in the left hand and F A E in the right; G13 with G and F, then B E A; Cmaj9 " +
                "with C and B, then E G D.",
                CourseSee.Score("ii–V–I IN C, SPREAD ACROSS BOTH HANDS", grand(SPREAD, left = 2), listOf("", ""))),
            say("It's open at the bottom, where close intervals turn to mud, and close at the top, where the ear hears colour. " +
                "Compare the same five notes packed low with the spread version.",
                table("LOW AND CLOSE, OR SPREAD", listOf("", "NOTES"), listOf(listOf("Close, low", "D2 F2 A2 C3 E3"), listOf("Spread", "D2 C3 F3 A3 E4")),
                    listOf(Play(listOf(mids("D2 F2 A2 C3 E3")), 2.0), Play(listOf(mids("D2 C3 F3 A3 E4")), 2.0)))),
        ),
        inShort = listOf(
            "Left hand: root + 7th (or 10th). Right hand: 3rd, tension, top note.",
            "Wide at the bottom, close at the top.",
            "Low close voicings muddy; spread ones ring.",
        ),
        hear = listOf(sound("THE SPREAD VOICINGS") { chordsInTurn(SPREAD.map(::midis), step = 2.5) }),
        related = listOf(page("rootless", "Rootless voicings"), page("extensions", "Extensions")),
    )

    // ------------------------------------------------------------------------------------------------- the voice

    /** Usual part-writing ranges (textbooks differ by a step or so), low and high. */
    internal val SATB = listOf(
        Triple("Soprano", Clef.TREBLE, "C4" to "G5"),
        Triple("Alto", Clef.TREBLE, "G3" to "D5"),
        Triple("Tenor", Clef.TREBLE_8VB, "C3" to "G4"),
        Triple("Bass", Clef.BASS, "E2" to "C4"),
    )
    private val rangesScore = Score(parts = SATB.map { (n, c, r) -> Part(n, c, bars = listOf(Bar(listOf(listOf(Event(h, listOf(Note(SpelledPitch.parse(r.first)))), Event(h, listOf(Note(SpelledPitch.parse(r.second))))))))) })

    private val voiceRanges = CoursePage(
        "voice-ranges", "Ranges and registers", 2,
        "Four-part writing names four voices, and their usual ranges are a good map of what voices can comfortably do.",
        steps = listOf(
            say("Soprano and alto are high and low women's or boys' voices; tenor and bass high and low men's. Tap a voice for its " +
                "lowest and highest note.",
                table("SATB RANGES", listOf("", "LOW", "HIGH"), SATB.map { (n, _, r) -> listOf(n, r.first, r.second) },
                    SATB.map { (_, _, r) -> Play(listOf(listOf(SpelledPitch.parse(r.first).midi), listOf(SpelledPitch.parse(r.second).midi)), 0.9) })),
            say("These are the usual comfortable ranges for part-writing; trained singers go further and textbooks differ by a step " +
                "or so. Tenor parts are written in treble clef an octave above how they sound: the small 8 under the clef.",
                CourseSee.Score("SATB RANGES, LOWEST AND HIGHEST NOTE", rangesScore, SATB.map { it.first.uppercase() })),
            say("Within a range a voice changes register, chest voice low and head voice high, and the crossing point, the " +
                "passaggio, is where an untrained voice cracks or strains. A song sits best where the voice is strong for what " +
                "the song asks: a belted chorus wants a different key from a whispered verse, even for the same singer."),
        ),
        inShort = listOf(
            "SATB: soprano, alto, tenor, bass.",
            "Tenor is written an octave above how it sounds.",
            "Registers: chest low, head high, the passaggio between.",
        ),
        hear = SATB.map { (n, _, r) -> sound(n.uppercase()) { piano(seq(listOf(SpelledPitch.parse(r.first).midi, SpelledPitch.parse(r.second).midi), step = 0.9, len = 0.85)) } },
        related = listOf(page("voice-transpose", "Transposing for a singer"), ear("pitch-match", "match a pitch")),
    )

    /** Shifts in semitones (−12..12) that put a song's [low]–[high] inside a singer's [from]–[to]. */
    internal fun fits(low: Int, high: Int, from: Int, to: Int) = (-12..12).filter { low + it >= from && high + it <= to }
    internal val SONG = "D4" to "G5"
    internal val SINGER = "G3" to "D5"
    /** How each fitting shift is spelled: down a P4, an A4 or a P5. */
    internal val SHIFTS = mapOf(-5 to Interval(4, 5), -6 to Interval(4, 6), -7 to Interval(5, 7))

    private val fitting = fits(SpelledPitch.parse(SONG.first).midi, SpelledPitch.parse(SONG.second).midi, SpelledPitch.parse(SINGER.first).midi, SpelledPitch.parse(SINGER.second).midi).reversed()

    private val voiceTranspose = CoursePage(
        "voice-transpose", "Transposing for a singer", 2,
        "To find a singer's key, compare two ranges: the song's and the singer's.",
        steps = listOf(
            say("Find the song's lowest and highest notes, then the singer's comfortable lowest and highest. This song runs from D4 " +
                "up to G5; the singer is comfortable from G3 to D5.",
                keys("THE SONG D4–G5 · THE SINGER G3–D5", "G3 D4 D5 G5", listOf("singer low", "song low", "singer high", "song high"),
                    play = Play(mids("G3 D4 D5 G5").map { listOf(it) }, 0.8))),
            say("As written the top is five half steps too high. Move the song down until it fits inside: a fourth (D major), an " +
                "augmented fourth (D♭) or a fifth (C) all work. Tap a row to hear the melody's shape in that key.",
                table("A SONG FROM D4 TO G5 · A SINGER FROM G3 TO D5", listOf("SHIFT", "KEY", "RANGE"),
                    listOf(listOf("as written", "G", "D4–G5 · too high")) + fitting.map { s ->
                        val iv = SHIFTS.getValue(s)
                        listOf(signed(s), CourseIntervals.shown(PitchName.parse("G") - iv), "${shown(SpelledPitch.parse(SONG.first) - iv)}–${shown(SpelledPitch.parse(SONG.second) - iv)}")
                    }, (listOf(0) + fitting).map { s -> Play(listOf(62, 67, 71, 74, 79).map { listOf(it + s) }, 0.45) })),
            say("A melody's range matters more than its key: most songs span an octave and a bit, and one or two notes at the edge " +
                "decide it. Men and women singing the same song an octave apart can usually share the key; when they can't, the " +
                "duet key is a compromise."),
        ),
        inShort = listOf(
            "Compare the song's range with the singer's.",
            "Shift until the song fits inside.",
            "The edge notes decide; the key follows.",
        ),
        hear = listOf(
            sound("THE MELODY'S SHAPE IN G") { piano(seq(listOf(62, 67, 71, 74, 79), step = 0.5)) },
            sound("DOWN A FOURTH, IN D") { piano(seq(listOf(62, 67, 71, 74, 79).map { it - 5 }, step = 0.5)) },
        ),
        related = listOf(page("voice-ranges", "Ranges and registers"), page("capo", "What a capo does")),
    )

    val topics = listOf(
        CourseTopic("piano", "The piano keyboard", "the pattern of twos and threes", 1, listOf(keyboard)),
        CourseTopic("guitar", "The guitar neck", "strings, frets, the octave shapes", 1, listOf(guitarNeck, guitarOctaves)),
        CourseTopic("bass", "The bass neck", "four, five and six strings", 1, listOf(bassNeck)),
        CourseTopic("tunings", "Tunings and capos", "drop D, open tunings, what a capo does", 2, listOf(tuningsPage, capo)),
        CourseTopic("caged", "Chord shapes and CAGED", "five shapes up the neck", 2, listOf(caged, barre)),
        CourseTopic("positions", "Scale positions", "five shapes, three notes per string", 3, listOf(pentPositions, threeNps)),
        CourseTopic("drop", "Drop 2 and drop 3", "seventh chords across the neck", 4, listOf(dropPage)),
        CourseTopic("shells", "Piano voicings", "shells, rootless, spread", 4, listOf(shells, rootless, spread)),
        CourseTopic("voice", "The voice", "ranges, registers, transposing for a singer", 2, listOf(voiceRanges, voiceTranspose)),
    )
}
