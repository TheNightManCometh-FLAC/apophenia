package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.catalog.Scales
import com.betterappmafia.apophenia.core.interval.Interval
import com.betterappmafia.apophenia.core.pitch.PitchName

/*
 * Course · Intervals: counting them, melodic and harmonic, quality (perfect, major, minor, augmented, diminished),
 * inversion, compound intervals, consonance and resolution, the tritone. Text written for Apophenia; every spelling in
 * the tables comes from the music core. Every picture is drawn on C and its ▶ plays exactly what it shows.
 */
internal object CourseIntervals : CourseKit() {
    private val C = PitchName.parse("C")

    /** Display name of a note: ♯ ♭ 𝄪 𝄫 for the core's ASCII. */
    internal fun shown(p: PitchName) = p.toString().replace("bb", "𝄫").replace("b", "♭").replace("#", "♯").replace("x", "𝄪")

    /** A table row for an interval on C: symbol, name, half steps, the note above C. */
    private fun onC(id: String): List<String> {
        val i = Interval.parse(id)
        val p = IntervalPages.page(i, C)
        return listOf(id, p.name, "${i.semitones}", p.above?.let(::shown) ?: "")
    }

    private val ON_C_HEADER = listOf("", "NAME", "HALF STEPS", "C UP TO")

    /** An interval [s] half steps above [low]: the low note, the high note, then both together. */
    private fun ivPlay(s: Int, low: Int = 60) = Play(listOf(listOf(low), listOf(low + s), listOf(low, low + s)), 0.8)
    private fun onCPlay(id: String) = ivPlay(Interval.parse(id).semitones)

    /**
     * Note [columns] with octaves ("B3 C4 D4"), each row ([label] to the notes it holds) lighting its notes and playing
     * them together; ▶ plays the rows in order, so a move from one interval to the next is seen and heard.
     */
    private fun pairGrid(caption: String, columns: String, rows: List<Pair<String, String>>): CourseSee.Grid {
        val cs = columns.split(' ').filter { it.isNotBlank() }
        val names = cs.map { pname(it) }
        val rs = rows.map { (label, notes) ->
            val ns = notes.split(' ').filter { it.isNotBlank() }
            row(label, names, cs.indices.filter { cs[it] in ns }.toSet(), Play(listOf(mids(notes)), 1.4, spread = true))
        }
        return CourseSee.Grid(caption, names, rs, Play(rows.map { mids(it.second) }, 1.6))
    }

    // ------------------------------------------------------------------------------------------------- numbers

    private val count = CoursePage(
        "iv-count", "Counting an interval", 1,
        "An interval is the distance between two notes. Its full name has two parts, a number and a quality (a major " +
            "third, a perfect fifth). This page is the number, which you get by counting.",
        steps = listOf(
            say("Count the letter names from the lower note to the upper one, and count both ends. C up to E is C, D, E: three " +
                "letters, so a third. The keys in between don't matter here, only the letters.",
                keys("C UP TO E · THREE LETTERS", "C4 D4 E4", listOf("1 · C", "2 · D", "3 · E"),
                    play = Play(listOf(listOf(60), listOf(62), listOf(64), listOf(60, 64)), 0.7))),
            say("Counting up the C major scale gives every number. The same note twice is a unison (1); C to D a second, C to F a " +
                "fourth, C to G a fifth, and the same letter eight letters up is the octave. Tap a row to hear C and the other " +
                "note, one then the other, then together.",
                table("COUNTING UP FROM C", listOf("", "NUMBER", "C UP TO"),
                    ScalePages.page(Scales.byId("major"), C).notes.let { it + it.first() }.mapIndexed { n, p ->
                        listOf("${n + 1}", listOf("unison", "second", "third", "fourth", "fifth", "sixth", "seventh", "octave")[n], shown(p))
                    },
                    (Scales.byId("major").semitones + 12).map { s -> if (s == 0) Play(listOf(listOf(60), listOf(60)), 0.8) else ivPlay(s) })),
            say("The number counts letters, not keys. C to E and C to E♭ are both thirds, three letters each, though E♭ is a key " +
                "lower. And C to D♯ is a second, even though D♯ is the same key as E♭. So an interval's name says how it's " +
                "written, not only how far it reaches; the quality, later in this chapter, pins down the exact size.",
                table("LETTERS, NOT KEYS", listOf("", "LETTERS", "NUMBER", "HALF STEPS"), listOf(
                    listOf("C–E", "C D E", "third", "4"),
                    listOf("C–E♭", "C D E♭", "third", "3"),
                    listOf("C–D♯", "C D♯", "second", "3"),
                ), listOf(ivPlay(4), ivPlay(3), ivPlay(3)))),
        ),
        inShort = listOf(
            "The number = letter names from the lower note to the upper, both ends counted.",
            "Same note = unison (1); same letter eight up = octave (8).",
            "Letters, not keys: C–E♭ is a third, C–D♯ a second.",
        ),
        hear = listOf(sound("UNISON TO OCTAVE, ON THE KEY") { r ->
            piano((Scales.byId("major").semitones + 12).flatMapIndexed { i, s -> together(r, s, at = i * 1.3).map { it.copy(end = it.start + 1.2) } })
        }),
        tryIt = CourseTry("COUNT THE LETTERS FROM THE ROOT · EACH NUMBER ON YOUR NECK", RefKind.SCALE, "major", "C"),
        check = CourseCheck("A step or a leap?", listOf(
            option("Step", "A step: a second, to the next letter.") { r, rnd -> val s = 1 + rnd.nextInt(2); piano(seq(listOf(r, if (rnd.nextBoolean()) r + s else r - s), step = 0.8, len = 0.75)) },
            option("Leap", "A leap: a third or wider, skipping at least one letter.") { r, rnd -> val s = 3 + rnd.nextInt(5); piano(seq(listOf(r, if (rnd.nextBoolean()) r + s else r - s), step = 0.8, len = 0.75)) },
        )),
        related = listOf(ref(RefKind.INTERVAL, "M3", "Major third"), ear("iv-2-3", "seconds and thirds")),
    )

    private val melodic = CoursePage(
        "iv-melodic", "Melodic and harmonic", 1,
        "Two notes can come one after the other or both at once. Either way they make an interval, with the same name; " +
            "what changes is how you hear it.",
        steps = listOf(
            say("One after the other is a melodic interval, and it has a direction. C up to G is a fifth; G back down to C is " +
                "a fifth too. Up or down doesn't change the name, only the movement.",
                keys("C UP TO G, THEN BACK DOWN", "C4 G4", play = Play(listOf(listOf(60), listOf(67), listOf(67), listOf(60)), 0.7))),
            say("Both at once is a harmonic interval. The two notes fuse into one sound, which is why harmonic intervals are " +
                "harder to name by ear: you hear a colour rather than a move. Try to hear the two notes inside it.",
                keys("C AND G TOGETHER", "C4 G4", play = Play(listOf(listOf(60, 67)), 2.0, spread = true))),
            say("Melodies move mostly by small melodic intervals, seconds, with the odd leap. This line steps up from C to G and " +
                "then leaps a fourth to the top C.",
                keysLine("STEPS, THEN ONE LEAP", "C4 D4 E4 F4 G4 C5", step = 0.5)),
            say("Chords are harmonic intervals stacked up, mostly thirds. C major is C–E (a third) with E–G (another third) on top, " +
                "which makes C–G a fifth.",
                keys("C E G · TWO THIRDS STACKED", "C4 E4 G4", listOf("C", "E · 3rd", "G · 5th"))),
        ),
        inShort = listOf(
            "Melodic = one after the other, with a direction; harmonic = together.",
            "The name is the same either way.",
            "Ear training usually goes melodic up, then melodic down, then harmonic.",
        ),
        hear = listOf(
            sound("A FIFTH · UP, DOWN, TOGETHER") { r -> piano(RefSound.interval(r, r + 7)) },
            sound("A THIRD · UP, DOWN, TOGETHER") { r -> piano(RefSound.interval(r, r + 4)) },
        ),
        tryIt = CourseTry("A FIFTH FROM ANY NOTE", RefKind.INTERVAL, "P5", "C"),
        check = CourseCheck("Did the second note go up or down?", listOf(
            option("Up", "Up.") { r, rnd -> piano(seq(listOf(r, r + 1 + rnd.nextInt(12)), step = 0.8, len = 0.75)) },
            option("Down", "Down.") { r, rnd -> piano(seq(listOf(r, r - 1 - rnd.nextInt(12)), step = 0.8, len = 0.75)) },
        ), roots = 55..70),
        related = listOf(ref(RefKind.INTERVAL, "P5", "Perfect fifth"), ear("iv-simple", "every simple interval")),
    )

    // ------------------------------------------------------------------------------------------------- quality

    /** Every fourth in C major, F–B the one that isn't perfect. */
    private val FOURTHS = "C4 D4 E4 F4 G4 A4 B4 C5 D5 E5 F5".split(' ').let { n ->
        pairGrid("EVERY FOURTH IN C MAJOR", n.joinToString(" "), (0..6).map { d ->
            val s = sp(n[d + 3]).midi - sp(n[d]).midi
            "${pname(n[d])}–${pname(n[d + 3])} · ${if (s == 5) "P4" else "A4"}" to "${n[d]} ${n[d + 3]}"
        })
    }

    private val perfect = CoursePage(
        "iv-perfect", "Perfect intervals", 2,
        "The number counts letters; the quality says exactly how many half steps. Unisons, fourths, fifths and octaves have " +
            "one everyday size, called perfect.",
        steps = listOf(
            say("From the root of any major scale up to its 4th, 5th and octave are a perfect fourth (5 half steps), a perfect " +
                "fifth (7) and a perfect octave (12). In C that's C–F, C–G and C–C. They sound open and hollow, with no " +
                "major or minor colour.",
                keys("C MAJOR · P4, P5, P8 FROM C", "C4 F4 G4 C5", listOf("C", "P4", "P5", "P8"), faint = "D4 E4 A4 B4",
                    play = Play(listOf(listOf(60, 65), listOf(60, 67), listOf(60, 72)), 1.4))),
            say("One half step wider than perfect is augmented, one narrower diminished. C to F♯ is an augmented fourth; C to G♭ " +
                "a diminished fifth. They're the same key, six half steps, but F♯ is four letters up from C and G♭ five.",
                table("PERFECT AND ITS NEIGHBOURS", ON_C_HEADER, listOf("P1", "A1", "P4", "A4", "d5", "P5", "A5", "P8").map(::onC),
                    listOf("P1", "A1", "P4", "A4", "d5", "P5", "A5", "P8").map(::onCPlay))),
            say("Inside C major, one fourth isn't perfect. Every fourth in the scale is five half steps except F up to B, which " +
                "spans three whole steps, six half steps: an augmented fourth. Tap down the rows and listen for the odd one.",
                FOURTHS),
        ),
        inShort = listOf(
            "Perfect: P1, P4 (5 half steps), P5 (7), P8 (12).",
            "One wider = augmented, one narrower = diminished; A4 and d5 are both six half steps.",
            "In a major scale every fourth is perfect except 4 up to 7.",
        ),
        hear = listOf(
            sound("P4 · P5 · P8") { r -> piano(together(r, 5) + together(r, 7, at = 2.1) + together(r, 12, at = 4.2)) },
            sound("4TH TO 7TH: THE AUGMENTED FOURTH") { r -> melodicThenHarmonic(r + 5, 6) },
        ),
        tryIt = CourseTry("A PERFECT FOURTH FROM ANY NOTE", RefKind.INTERVAL, "P4", "C"),
        check = CourseCheck("A perfect fourth or a perfect fifth?", listOf(
            option("P4", "A perfect fourth: 5 half steps.") { r, _ -> melodicThenHarmonic(r, 5) },
            option("P5", "A perfect fifth: 7 half steps.") { r, _ -> melodicThenHarmonic(r, 7) },
        )),
        related = listOf(ref(RefKind.INTERVAL, "P4", "Perfect fourth"), ref(RefKind.INTERVAL, "P5", "Perfect fifth"), ear("iv-4-5", "fourths, fifths, tritone")),
    )

    private val majorMinor = CoursePage(
        "iv-major-minor", "Major and minor", 2,
        "Seconds, thirds, sixths and sevenths come in two everyday sizes, major and minor, a half step apart. The letters " +
            "stay the same; only the size changes.",
        steps = listOf(
            say("From the root of a major scale up to its 2nd, 3rd, 6th and 7th are all major intervals: 2, 4, 9 and 11 half " +
                "steps. In C they're D, E, A and B.",
                keys("C MAJOR · M2 M3 M6 M7 FROM C", "C4 D4 E4 A4 B4", listOf("C", "M2", "M3", "M6", "M7"), faint = "F4 G4 C5",
                    play = Play(listOf(listOf(60, 62), listOf(60, 64), listOf(60, 69), listOf(60, 71)), 1.4))),
            say("Lower the top note a half step and each becomes minor: 1, 3, 8 and 10 half steps. C–E is a major third and " +
                "C–E♭ a minor third, both still C to an E of some kind. Tap down the table in pairs.",
                table("MAJOR AND MINOR ON C", ON_C_HEADER, listOf("m2", "M2", "m3", "M3", "m6", "M6", "m7", "M7").map(::onC),
                    listOf("m2", "M2", "m3", "M3", "m6", "M6", "m7", "M7").map(::onCPlay))),
            say("Thirds are where you'll hear the difference most. A major third is the bottom of a major chord, a minor third the " +
                "bottom of a minor one: one key's difference changes bright to dark.",
                keys("C–E, THEN C–E♭", "C4 Eb4 E4", listOf("C", "m3", "M3"), play = Play(listOf(listOf(60, 64), listOf(60, 63)), 1.6))),
            say("Wider than major is augmented, narrower than minor diminished. C to D♯ is an augmented second, the same keys as " +
                "a minor third; C to B𝄫 is a diminished seventh, the same keys as a major sixth. The name follows the letters, " +
                "so the right one depends on how the music spells the note.",
                table("SAME KEYS, DIFFERENT NAMES", ON_C_HEADER, listOf("m3", "A2", "M6", "d7").map(::onC), listOf("m3", "A2", "M6", "d7").map(::onCPlay))),
        ),
        inShort = listOf(
            "Major: M2 2, M3 4, M6 9, M7 11 half steps (a major scale from its root).",
            "Minor is a half step smaller: m2 1, m3 3, m6 8, m7 10.",
            "Augmented and diminished go one further; the letters decide the name.",
        ),
        hear = listOf(
            sound("MAJOR THIRD · MINOR THIRD") { r -> piano(together(r, 4) + together(r, 3, at = 2.1)) },
            sound("MAJOR SIXTH · MINOR SIXTH") { r -> piano(together(r, 9) + together(r, 8, at = 2.1)) },
            sound("MAJOR SEVENTH · MINOR SEVENTH") { r -> piano(together(r, 11) + together(r, 10, at = 2.1)) },
        ),
        tryIt = CourseTry("A MINOR THIRD FROM ANY NOTE", RefKind.INTERVAL, "m3", "C"),
        check = CourseCheck("A major third or a minor third?", listOf(
            option("M3", "A major third: 4 half steps, bright.") { r, _ -> melodicThenHarmonic(r, 4) },
            option("m3", "A minor third: 3 half steps, darker.") { r, _ -> melodicThenHarmonic(r, 3) },
        )),
        related = listOf(ref(RefKind.INTERVAL, "M3", "Major third"), ref(RefKind.INTERVAL, "m3", "Minor third"), ear("iv-2-3", "seconds and thirds"), ear("iv-6-7", "sixths and sevenths")),
    )

    /** Every simple interval the Reference has, by size. */
    internal val SIMPLE = Reference.intervals.filter { !it.isCompound }.sortedWith(compareBy({ it.semitones }, { it.number })).map { it.toString() }

    private val every = CoursePage(
        "iv-all", "Every simple interval", 2,
        "Put the quality and the number together and every interval within the octave has a two-part name. The short forms: " +
            "P perfect, M major, m minor, A augmented, d diminished. M3 is a major third, d5 a diminished fifth.",
        steps = listOf(
            say("Twelve everyday intervals cover every size from one half step to twelve, one per key above C. These are the ones " +
                "the ear drills use. Play them small to large and listen to the colour change: rubbing, sweet, open, tense, " +
                "sweet again, rubbing, then the octave.",
                keys("ONE PER KEY ABOVE C", "C4 Db4 D4 Eb4 E4 F4 F#4 G4 Ab4 A4 Bb4 B4 C5",
                    listOf("1", "m2", "M2", "m3", "M3", "P4", "A4", "P5", "m6", "M6", "m7", "M7", "P8"),
                    play = Play((1..12).map { listOf(60, 60 + it) }, 1.1))),
            say("Several sizes have more than one name: six half steps is A4 or d5, three is m3 or A2, eight is m6 or A5. The " +
                "extra names exist so every note can be spelled with its right letter. The full list, by size:",
                table("EVERY SIMPLE INTERVAL ON C", ON_C_HEADER, SIMPLE.map(::onC), SIMPLE.map(::onCPlay))),
        ),
        inShort = listOf(
            "Name = quality + number: P M m A d, then 1–8.",
            "The everyday twelve: m2 M2 m3 M3 P4 A4 P5 m6 M6 m7 M7 P8.",
            "Same size, different name when the letters differ (A4 = d5 on the keys).",
        ),
        hear = listOf(sound("m2 TO P8, SMALL TO LARGE") { r -> piano((1..12).flatMap { s -> together(r, s, at = (s - 1) * 1.3).map { it.copy(end = it.start + 1.2) } }) }),
        tryIt = CourseTry("A TRITONE FROM ANY NOTE", RefKind.INTERVAL, "A4", "C"),
        check = CourseCheck("Which interval?", listOf(1 to "m2", 4 to "M3", 7 to "P5", 12 to "P8").map { (s, n) ->
            option(n, "${Reference.intervalName(Interval.parse(n)).replaceFirstChar(Char::uppercase)}: $s half steps.") { r, _ -> melodicThenHarmonic(r, s) }
        }),
        related = listOf(ear("iv-simple", "every simple interval")),
    )

    // ------------------------------------------------------------------------------------------------- inversion

    internal val INVERTED = listOf("m2", "M2", "m3", "M3", "P4", "A4", "d5", "P5", "m6", "M6", "m7", "M7")

    private val invert = CoursePage(
        "iv-invert", "Inversion", 2,
        "Turn an interval upside down, by moving its lower note up an octave, and you get its inversion. An interval and its " +
            "inversion always fill exactly one octave between them.",
        steps = listOf(
            say("C up to E is a major third. Move the C up an octave and you have E up to C, a minor sixth. Put them end to end and " +
                "they span C to C: 4 half steps plus 8 makes 12.",
                keys("C–E, THEN E–C", "C4 E4 C5", listOf("C", "E", "C"), play = Play(listOf(listOf(60, 64), listOf(64, 72)), 1.6))),
            say("That gives two rules. The numbers add up to nine (3 + 6, 2 + 7, 4 + 5) and the half steps to twelve. Major turns " +
                "into minor and minor into major, augmented into diminished and back, and perfect stays perfect. Each row plays " +
                "the interval on C, then its inversion.",
                table("INVERSIONS", listOf("", "HALF STEPS", "INVERTS TO", "HALF STEPS"), INVERTED.map { id ->
                    val i = Interval.parse(id)
                    val inv = IntervalPages.page(i, C).inversion
                    listOf(id, "${i.semitones}", inv.toString(), "${inv.semitones}")
                }, INVERTED.map { id -> Interval.parse(id).semitones.let { s -> Play(listOf(listOf(60, 60 + s), listOf(60 + s, 72)), 1.4) } })),
            say("It's a shortcut for wide intervals: a major seventh is a minor second short of the octave, so find the octave and " +
                "come down a half step. An interval and its inversion also sound alike: fourths and fifths open, thirds and " +
                "sixths sweet, seconds and sevenths sharp.",
                keys("M7 = AN OCTAVE LESS A HALF STEP", "C4 B4 C5", listOf("C", "M7", "P8"), play = Play(listOf(listOf(60, 72), listOf(60, 71)), 1.6))),
        ),
        inShort = listOf(
            "Invert by moving the bottom note up an octave.",
            "Numbers add to 9, half steps to 12.",
            "M ↔ m, A ↔ d, P stays P.",
        ),
        hear = listOf(
            sound("M3, THEN ITS INVERSION m6") { r -> piano(together(r, 4) + RefSound.block(listOf(r + 4, r + 12), at = 2.1, length = 1.8)) },
            sound("P5, THEN ITS INVERSION P4") { r -> piano(together(r, 7) + RefSound.block(listOf(r + 7, r + 12), at = 2.1, length = 1.8)) },
        ),
        tryIt = CourseTry("A MINOR SIXTH FROM ANY NOTE", RefKind.INTERVAL, "m6", "C"),
        check = CourseCheck("A major third or a minor sixth?", listOf(
            option("M3", "A major third: 4 half steps.") { r, _ -> melodicThenHarmonic(r, 4) },
            option("m6", "A minor sixth: 8 half steps, the major third turned over.") { r, _ -> melodicThenHarmonic(r, 8) },
        )),
        related = listOf(ref(RefKind.INTERVAL, "m6", "Minor sixth"), ear("iv-6-7", "sixths and sevenths")),
    )

    internal val COMPOUND = listOf("m9", "M9", "m10", "M10", "P11", "A11", "P12", "m13", "M13", "P15")

    private val compound = CoursePage(
        "iv-compound", "Compound intervals", 2,
        "Intervals wider than an octave are compound: an octave plus a simple interval. The quality carries over from the " +
            "simple interval inside.",
        steps = listOf(
            say("Take seven off the number to find the simple interval inside. C up to the D past the next C is a ninth, 9 − 7 = 2, " +
                "so an octave plus a second; major, so 12 + 2 = 14 half steps. Hear the major second, then the same two letters " +
                "an octave further apart.",
                keys("M2, THEN M9", "C4 D4 C5 D5", listOf("C", "M2", "P8", "M9"), play = Play(listOf(listOf(60, 62), listOf(60, 74)), 1.8))),
            say("The ones you'll meet: ninths (octave + second), tenths (+ third), elevenths (+ fourth), twelfths (+ fifth), " +
                "thirteenths (+ sixth), and the double octave.",
                table("COMPOUND INTERVALS ON C", listOf("", "NAME", "HALF STEPS", "SIMPLE"), COMPOUND.map { id ->
                    val i = Interval.parse(id)
                    listOf(id, Reference.intervalName(i), "${i.semitones}", if (i.number == 15) "P8 + P8" else "${i.simple} + P8")
                }, COMPOUND.map(::onCPlay))),
            say("Chord symbols count this way. Stack thirds above C: C E G B♭ gets you to the 7th, and carrying on gives D, F and A. " +
                "Those are the 9, 11 and 13 of C9, C11 and C13: the 2nd, 4th and 6th, named high because they sit above the 7th.",
                keysLine("C13 BUILT IN THIRDS", "C3 E3 G3 Bb3 D4 F4 A4", listOf("1", "3", "5", "♭7", "9", "11", "13"))),
        ),
        inShort = listOf(
            "Compound = octave + simple: 9 = 2, 10 = 3, 11 = 4, 12 = 5, 13 = 6, add 12 half steps.",
            "A ninth rubs far less than a second: the notes are further apart.",
            "Chord extensions 9, 11, 13 are compound intervals over the root.",
        ),
        hear = listOf(
            sound("M2, THEN M9") { r -> piano(together(r, 2) + together(r, 14, at = 2.1)) },
            sound("M3, THEN M10") { r -> piano(together(r, 4) + together(r, 16, at = 2.1)) },
        ),
        tryIt = CourseTry("A MAJOR NINTH FROM ANY NOTE", RefKind.INTERVAL, "M9", "C"),
        check = CourseCheck("A second or a ninth?", listOf(
            option("M2", "A major second: 2 half steps, close and rubbing.") { r, _ -> melodicThenHarmonic(r, 2) },
            option("M9", "A major ninth: 14 half steps, the second an octave up.") { r, _ -> melodicThenHarmonic(r, 14) },
        ), roots = 48..60),
        related = listOf(ref(RefKind.INTERVAL, "M9", "Major ninth"), ref(RefKind.CHORD, "9", "C9"), ear("iv-compound", "compound intervals")),
    )

    // ------------------------------------------------------------------------------------------------- consonance

    internal val CONSONANT = listOf(3, 4, 7, 8, 9, 12)
    internal val DISSONANT = listOf(1, 2, 6, 10, 11)

    private val consonance = CoursePage(
        "iv-consonance", "Consonance and dissonance", 2,
        "Some intervals sound settled and some sound as if they want to move on. The settled ones are consonant, the restless " +
            "ones dissonant, and music runs on the contrast.",
        steps = listOf(
            say("Classical theory sorts them into groups. The fourth is the odd one: between upper voices it's consonant (the E–A " +
                "over a C bass, first sound in the last row), but against the bass it wants to move (C–F, the second).",
                table("THE CLASSICAL SORTING", listOf("", "INTERVALS"), listOf(
                    listOf("Perfect consonance", "P1 P5 P8"),
                    listOf("Imperfect consonance", "m3 M3 m6 M6"),
                    listOf("Dissonance", "m2 M2 A4 d5 m7 M7"),
                    listOf("Depends", "P4: consonant above, dissonant over the bass"),
                ), listOf(
                    Play(listOf(listOf(60), listOf(60, 67), listOf(60, 72)), 1.4),
                    Play(listOf(3, 4, 8, 9).map { listOf(60, 60 + it) }, 1.4),
                    Play(listOf(1, 2, 6, 6, 10, 11).map { listOf(60, 60 + it) }, 1.4),
                    Play(listOf(listOf(48, 64, 69), listOf(60, 65)), 1.8),
                ))),
            say("Why: the settled intervals have simple frequency ratios, 2:1 for the octave, 3:2 for the fifth, 5:4 for the major " +
                "third, so the overtones of the two notes line up. A minor second's overtones land close together and beat " +
                "against each other, and you hear that as roughness.",
                keys("P5, THEN m2", "C4 Db4 G4", listOf("C", "m2", "P5"), play = Play(listOf(listOf(60, 67), listOf(60, 61)), 2.0))),
            say("How much tension a style hears varies. In the blues a dominant seventh is home; in jazz a major seventh or an " +
                "added ninth is a resting chord; in a lot of rock the fifth alone, the power chord, is the whole chord. Dissonance " +
                "isn't a fault. It's what makes music move.",
                table("RESTING SOUNDS BY STYLE", listOf("", "CHORD", "NOTES"), listOf(
                    listOf("Blues", "C7", "C E G B♭"),
                    listOf("Jazz", "Cmaj9", "C E G B D"),
                    listOf("Rock", "C5", "C G C"),
                ), listOf("C3 E3 G3 Bb3", "C3 E3 G3 B3 D4", "C3 G3 C4").map { Play(listOf(mids(it)), 2.0, spread = true) })),
        ),
        inShort = listOf(
            "Perfect consonances P1 P5 P8; imperfect m3 M3 m6 M6; dissonances seconds, sevenths, tritone.",
            "The fourth is consonant above, dissonant against the bass.",
            "Simple ratios sound settled; style decides how much tension counts as rest.",
        ),
        hear = listOf(
            sound("CONSONANT · P8 P5 M3 M6") { r -> piano(listOf(12, 7, 4, 9).flatMapIndexed { i, s -> together(r, s, at = i * 2.0) }) },
            sound("DISSONANT · m2 M7 A4 M2") { r -> piano(listOf(1, 11, 6, 2).flatMapIndexed { i, s -> together(r, s, at = i * 2.0) }) },
        ),
        check = CourseCheck("Consonant or dissonant?", listOf(
            option("Consonant", "Consonant: an octave, fifth, third or sixth.") { r, rnd -> piano(together(r, CONSONANT[rnd.nextInt(CONSONANT.size)])) },
            option("Dissonant", "Dissonant: a second, seventh or tritone.") { r, rnd -> piano(together(r, DISSONANT[rnd.nextInt(DISSONANT.size)])) },
        )),
        related = listOf(page("timbre-harmonics", "The harmonic series"), ref(RefKind.INTERVAL, "P5", "Perfect fifth")),
    )

    private val resolve = CoursePage(
        "iv-resolve", "Tension and resolution", 2,
        "A dissonance usually moves to a consonance, and that move is called resolution. Most of the pull in tonal music " +
            "comes from a few small moves like these.",
        steps = listOf(
            say("The standard example is the tritone inside G7. Its B and F are a diminished fifth apart. They resolve inward by " +
                "half steps, B up to C and F down to E, and land on the major third C–E. Watch the lit cells close in.",
                pairGrid("THE DIMINISHED FIFTH CLOSES IN", "B3 C4 D4 E4 F4", listOf("B–F · d5" to "B3 F4", "C–E · M3" to "C4 E4"))),
            say("Turn it over, F below B, and it's an augmented fourth. That one resolves outward: F down to E, B up to C, a minor " +
                "sixth.",
                pairGrid("THE AUGMENTED FOURTH OPENS OUT", "E4 F4 G4 A4 B4 C5", listOf("F–B · A4" to "F4 B4", "E–C · m6" to "E4 C5"))),
            say("A suspension makes tension by delay. Hold the 4th over a chord (Csus4 is C F G) and let it fall a half step to the " +
                "3rd (C E G). The F rubbing against G and pulling at the bass is the tension; E is the release.",
                pairGrid("Csus4 → C · THE 4TH FALLS TO THE 3RD", "C4 D4 E4 F4 G4", listOf("Csus4" to "C4 F4 G4", "C" to "C4 E4 G4"))),
            say("Left unresolved, the pull becomes part of the sound: end on the dissonance and it sounds open, like a question. " +
                "Here's the tritone's resolution played backwards, from the third to the tritone.",
                pairGrid("BACKWARDS · LEFT HANGING", "B3 C4 D4 E4 F4", listOf("C–E · M3" to "C4 E4", "B–F · d5" to "B3 F4"))),
        ),
        inShort = listOf(
            "Resolution = a dissonance moving to a consonance, usually by step.",
            "d5 resolves inward to a third; A4 outward to a sixth.",
            "A suspension delays the note it resolves to.",
        ),
        hear = listOf(
            sound("B–F → C–E") { r -> piano(RefSound.block(listOf(r - 1, r + 5), length = 1.5) + RefSound.block(listOf(r, r + 4), at = 1.7, length = 2.0)) },
            sound("F–B → E–C") { r -> piano(RefSound.block(listOf(r + 5, r + 11), length = 1.5) + RefSound.block(listOf(r + 4, r + 12), at = 1.7, length = 2.0)) },
            sound("Csus4 → C") { r -> piano(RefSound.block(listOf(r, r + 5, r + 7), length = 1.5) + RefSound.block(listOf(r, r + 4, r + 7), at = 1.7, length = 2.0)) },
        ),
        tryIt = CourseTry("THE DOMINANT SEVENTH · ITS 3RD AND 7TH ARE THE TRITONE", RefKind.CHORD, "7", "G"),
        check = CourseCheck("Did it resolve, or was it left hanging?", listOf(
            option("Resolved", "Resolved: the tritone B–F moved to the third C–E.") { r, _ -> piano(RefSound.block(listOf(r - 1, r + 5), length = 1.5) + RefSound.block(listOf(r, r + 4), at = 1.7, length = 2.0)) },
            option("Hanging", "Left hanging: it went from the third C–E to the tritone B–F.") { r, _ -> piano(RefSound.block(listOf(r, r + 4), length = 1.5) + RefSound.block(listOf(r - 1, r + 5), at = 1.7, length = 2.0)) },
        )),
        related = listOf(ref(RefKind.CHORD, "7", "G7", "G"), ref(RefKind.CHORD, "sus4", "Csus4"), ear("hm-cadence", "cadences")),
    )

    // ------------------------------------------------------------------------------------------------- tritone

    /** The six tritones, by their lower note in the default spelling. */
    internal val TRITONES = (0..5).map { pc ->
        val low = com.betterappmafia.apophenia.core.pitch.PitchClass(pc).defaultSpelling()
        val up = com.betterappmafia.apophenia.core.pitch.PitchClass(pc + 6).defaultSpelling()
        Triple(low, up, Interval.between(low, up))
    }

    private val tritone = CoursePage(
        "iv-tritone-what", "The tritone", 3,
        "Six half steps, exactly half an octave: the tritone, named for the three whole steps it spans. It's the most " +
            "restless interval there is, and a lot of harmony is built around getting it to move.",
        steps = listOf(
            say("Count it in whole steps from C: D, E, F♯. Three whole steps. Spelled with letters four apart it's an augmented " +
                "fourth (C–F♯); five apart, a diminished fifth (C–G♭). Same key, different letter.",
                keys("C TO F♯ · THREE WHOLE STEPS", "C4 D4 E4 F#4", listOf("C", "+1", "+2", "+3 · F♯"),
                    play = Play(listOf(listOf(60), listOf(62), listOf(64), listOf(66), listOf(60, 66)), 0.7))),
            say("Because it's half the octave, it inverts into itself: C up to F♯ is six half steps and F♯ up to C is six as well. " +
                "For the same reason the twelve keys hold only six different tritones; each one's top note starts another's.",
                table("THE SIX TRITONES", listOf("LOWER", "UPPER", "AS WRITTEN"), TRITONES.map { (a, b, i) ->
                    listOf(shown(a), shown(b), "$i · ${Reference.intervalName(i)}")
                }, (0..5).map { ivPlay(6, 60 + it) })),
            say("A major scale has exactly one tritone, between its 4th and 7th degrees: F and B in C major. That one interval does " +
                "a lot to tell the ear which key it's in, and it's the tritone inside the scale's dominant seventh, G7.",
                keys("C MAJOR · ITS ONE TRITONE, F–B", "F4 B4", listOf("F · 4", "B · 7"), faint = "C4 D4 E4 G4 A4 C5",
                    play = Play(listOf(listOf(65), listOf(71), listOf(65, 71)), 0.8))),
        ),
        inShort = listOf(
            "Tritone = 6 half steps = A4 or d5, half an octave.",
            "It inverts into itself; there are only six.",
            "A major scale has one, from 4 to 7. Renaissance singers called it diabolus in musica and avoided it in melodies.",
        ),
        hear = listOf(
            sound("TRITONE · UP, DOWN, TOGETHER") { r -> piano(RefSound.interval(r, r + 6)) },
            sound("TRITONE · THEN P5") { r -> piano(together(r, 6) + together(r, 7, at = 2.1)) },
        ),
        tryIt = CourseTry("A TRITONE FROM ANY NOTE", RefKind.INTERVAL, "A4", "C"),
        check = CourseCheck("A tritone, a fourth or a fifth?", listOf(
            option("P4", "A perfect fourth: 5 half steps.") { r, _ -> melodicThenHarmonic(r, 5) },
            option("Tritone", "The tritone: 6 half steps, half an octave.") { r, _ -> melodicThenHarmonic(r, 6) },
            option("P5", "A perfect fifth: 7 half steps.") { r, _ -> melodicThenHarmonic(r, 7) },
        )),
        related = listOf(ref(RefKind.INTERVAL, "A4", "Augmented fourth"), ref(RefKind.INTERVAL, "d5", "Diminished fifth"), ear("iv-4-5", "fourths, fifths, tritone")),
        yours = RefKind.INTERVAL to "A4",
    )

    /** G7 (bass G2, B3, F4) or D♭7 (bass D♭3, same B/C♭ and F) going to C (C3, C4, E4), with C at [r]. */
    internal fun toC(r: Int, bass: Int) = piano(RefSound.block(listOf(bass, r - 1, r + 5), length = 1.6) + RefSound.block(listOf(r - 12, r, r + 4), at = 1.8, length = 2.2))

    private val tritoneChords = CoursePage(
        "iv-tritone-chords", "The tritone in chords", 3,
        "The tritone is the active ingredient in dominant chords. Once you see where it sits, tritone substitution and the " +
            "uses of diminished chords stop being tricks and become obvious.",
        steps = listOf(
            say("In G7 (G B D F) the 3rd and the 7th, B and F, are a diminished fifth apart. Going to C they squeeze in to C and E. " +
                "That move is most of the pull of V7 to I; the root falling G to C does the rest.",
                chart("V7 → I", cc("V7", "G7", "5 7 2 4", "tritone B–F"), cc("I", "C", "1 3 5", "lands on C–E"), step = 1.6)),
            say("D♭7 is D♭ F A♭ C♭. Its 3rd and 7th are F and C♭, the same two keys as G7's B and F. So D♭7 can stand in for G7 " +
                "on the way to C: the root moves a tritone, the tritone itself stays. That's the tritone substitution.",
                chart("G7 → C, THEN D♭7 → C", cc("V7", "G7", "5 7 2 4", "B and F"), cc("I", "C", "1 3 5"),
                    cc("♭II7", "D♭7", "b2 4 b6 b1", "F and C♭"), cc("I", "C", "1 3 5"), step = 1.4)),
            say("Other chords hold it too. The half-diminished seventh has one between its root and 5th (Bø7: B–F). The " +
                "diminished seventh has two (C°7: C–G♭ and E♭–B𝄫), which is why it can lead to four different chords.",
                table("WHERE THE TRITONES ARE", listOf("", "NOTES", "TRITONES"), listOf(
                    listOf("G7", "G B D F", "B–F"),
                    listOf("Bø7", "B D F A", "B–F"),
                    listOf("C°7", "C E♭ G♭ B𝄫", "C–G♭, E♭–B𝄫"),
                ), listOf("G3 B3 D4 F4", "B3 D4 F4 A4", "C4 Eb4 Gb4 Bbb4").map { Play(listOf(mids(it)), 1.8, spread = true) })),
        ),
        inShort = listOf(
            "V7's 3rd and 7th form the tritone; they resolve to I's root and 3rd.",
            "D♭7 shares G7's tritone, so it can replace it: the tritone sub.",
            "ø7 has one tritone, °7 two. In scales it marks Lydian (♯4) and Locrian (♭5).",
        ),
        hear = listOf(
            sound("G7 → C") { r -> toC(r, r - 17) },
            sound("D♭7 → C") { r -> toC(r, r - 11) },
            sound("THE TRITONE ALONE → C–E") { r -> piano(RefSound.block(listOf(r - 1, r + 5), length = 1.5) + RefSound.block(listOf(r, r + 4), at = 1.7, length = 2.0)) },
        ),
        tryIt = CourseTry("THE DOMINANT SEVENTH · ITS 3RD AND 7TH ARE THE TRITONE", RefKind.CHORD, "7", "G"),
        check = CourseCheck("Into C: from G7, or from D♭7?", listOf(
            option("G7", "G7: the bass falls a fifth, G to C.") { r, _ -> toC(r, r - 17) },
            option("D♭7", "D♭7, the tritone sub: the bass slides down a half step, D♭ to C; the top two notes are the same.") { r, _ -> toC(r, r - 11) },
        ), roots = 52..64),
        related = listOf(
            ref(RefKind.CHORD, "7", "G7", "G"), ref(RefKind.CHORD, "7", "D♭7", "Db"),
            ref(RefKind.CHORD, "m7b5", "Bø7", "B"), ref(RefKind.CHORD, "dim7", "C°7"), ear("hm-subs", "tritone and backdoor substitutes"),
        ),
        yours = RefKind.CHORD to "7",
    )

    val topics = listOf(
        CourseTopic("iv-names", "Interval numbers", "counting letters, melodic and harmonic", 1, listOf(count, melodic)),
        CourseTopic("iv-quality", "Major, minor, perfect", "quality from the size in half steps", 2, listOf(perfect, majorMinor, every)),
        CourseTopic("iv-inversion", "Inversion and compound intervals", "turning them over, and past the octave", 2, listOf(invert, compound)),
        CourseTopic("iv-consonance", "Consonance and dissonance", "stable, tense, and resolving", 2, listOf(consonance, resolve)),
        CourseTopic("iv-tritone", "The tritone", "A4 and d5, in dominants, subs and diminished chords", 3, listOf(tritone, tritoneChords)),
    )
}
