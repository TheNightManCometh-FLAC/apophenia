package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.catalog.Scales
import com.betterappmafia.apophenia.core.interval.Interval
import com.betterappmafia.apophenia.core.pitch.PitchName

/*
 * Course · Intervals: counting them, melodic and harmonic, quality (perfect, major, minor, augmented, diminished),
 * inversion, compound intervals, consonance and resolution, the tritone. Text written for Apophenia; every spelling in
 * the tables comes from the music core.
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

    // ------------------------------------------------------------------------------------------------- numbers

    private val count = CoursePage(
        "iv-count", "Counting an interval", 1,
        "An interval is the distance between two notes. Its number comes from counting letter names, both ends " +
            "included: C up to E is C D E, three letters, so a third; C up to G is a fifth. The same note twice is a " +
            "unison, and the same letter an octave up is an octave, the eighth. The number counts letters, not keys: C to " +
            "E and C to E♭ are both thirds, and C to D♯ is a second even though D♯ is the same key as E♭. That's how an " +
            "interval's name says how it's written as well as how far it reaches. The number alone isn't the exact size; " +
            "the quality (perfect, major, minor) does that, later in this chapter.",
        see = CourseSee.Table("COUNTING UP FROM C", listOf("", "NUMBER", "C UP TO"),
            ScalePages.page(Scales.byId("major"), C).notes.let { it + it.first() }.mapIndexed { n, p ->
                listOf("${n + 1}", listOf("unison", "second", "third", "fourth", "fifth", "sixth", "seventh", "octave")[n], shown(p))
            }),
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
        "Two notes one after the other make a melodic interval; two notes together make a harmonic interval. The name " +
            "is the same either way. A melodic interval has a direction too: C up to G is a fifth, and so is G down to C. " +
            "Melodies move mostly by small melodic intervals, with the odd leap; chords stack harmonic intervals, mostly " +
            "thirds. Ear training usually starts with melodic intervals going up, then going down, then harmonic ones, " +
            "which many people find hardest because the two notes fuse into one sound.",
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

    private val perfect = CoursePage(
        "iv-perfect", "Perfect intervals", 2,
        "Quality gives the exact size. Unisons, fourths, fifths and octaves are called perfect: from the root of any " +
            "major scale up to its 4th, 5th and octave are a perfect fourth (5 half steps), a perfect fifth (7) and a " +
            "perfect octave (12). Make a perfect interval one half step wider and it's augmented; one narrower and it's " +
            "diminished. C to F♯ is an augmented fourth and C to G♭ a diminished fifth: the same key, six half steps " +
            "either way, written differently. Inside C major one fourth isn't perfect: F up to B is an augmented fourth, " +
            "and B up to F a diminished fifth. Every other fourth and fifth in the scale is perfect.",
        see = CourseSee.Table("PERFECT AND ITS NEIGHBOURS", ON_C_HEADER, listOf("P1", "A1", "P4", "A4", "d5", "P5", "A5", "P8").map(::onC)),
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
        "Seconds, thirds, sixths and sevenths come in two everyday sizes. From the root of a major scale up to its 2nd, " +
            "3rd, 6th and 7th are all major: 2, 4, 9 and 11 half steps. One half step narrower makes each minor: 1, 3, 8 " +
            "and 10. So C to E is a major third and C to E♭ a minor third, C to A a major sixth and C to A♭ a minor sixth. " +
            "Wider than major is augmented (C to D♯, an augmented second); narrower than minor is diminished (C to B𝄫, " +
            "a diminished seventh). An augmented second is the same keys as a minor third, and a diminished seventh the " +
            "same keys as a major sixth: the name follows the letters.",
        see = CourseSee.Table("MAJOR AND MINOR ON C", ON_C_HEADER, listOf("m2", "M2", "m3", "M3", "m6", "M6", "m7", "M7").map(::onC)),
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
        "Put quality and number together and every interval within the octave has a two-part name. The short forms are P " +
            "for perfect, M major, m minor, A augmented and d diminished: M3 is a major third, d5 a diminished fifth. The " +
            "table lists them by size. Several sizes have two or more names: six half steps is an augmented fourth or a " +
            "diminished fifth, three is a minor third or an augmented second. The everyday ones, the ones the ear drills " +
            "use, are m2 M2 m3 M3 P4 A4 P5 m6 M6 m7 M7 P8; the rest exist so that every note can be spelled with the right " +
            "letter.",
        see = CourseSee.Table("EVERY SIMPLE INTERVAL ON C", ON_C_HEADER, SIMPLE.map(::onC)),
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
        "Move the lower note up an octave, or the upper one down, and you invert the interval: C up to E, a major third, " +
            "becomes E up to C, a minor sixth. The numbers always add up to nine (3 and 6, 2 and 7, 4 and 5) and the half " +
            "steps to twelve (4 and 8). Major turns into minor and minor into major, augmented into diminished and back, " +
            "and perfect stays perfect. It's a quick way to size up a wide interval: a major seventh is a minor second " +
            "short of the octave. An interval and its inversion share a family sound: fifths and fourths are open, thirds " +
            "and sixths sweet, seconds and sevenths sharp.",
        see = CourseSee.Table("INVERSIONS", listOf("", "HALF STEPS", "INVERTS TO", "HALF STEPS"), INVERTED.map { id ->
            val i = Interval.parse(id)
            val inv = IntervalPages.page(i, C).inversion
            listOf(id, "${i.semitones}", inv.toString(), "${inv.semitones}")
        }),
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
        "Intervals wider than an octave are compound. Take seven off the number to find the simple interval inside: a " +
            "ninth is an octave and a second, a tenth an octave and a third, an eleventh an octave and a fourth, a twelfth " +
            "an octave and a fifth, a thirteenth an octave and a sixth. The quality carries over, so C up to the D past " +
            "the next C is a major ninth, 14 half steps. Chord symbols count this way: the 9, 11 and 13 in C9, C11 and C13 " +
            "are the 2nd, 4th and 6th an octave up, named high because they sit above the 7th in a stack of thirds. A " +
            "compound interval sounds more open than the simple one inside it; a ninth rubs far less than a second.",
        see = CourseSee.Table("COMPOUND INTERVALS ON C", listOf("", "NAME", "HALF STEPS", "SIMPLE"), COMPOUND.map { id ->
            val i = Interval.parse(id)
            listOf(id, Reference.intervalName(i), "${i.semitones}", if (i.number == 15) "P8 + P8" else "${i.simple} + P8")
        }),
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
        "Some intervals sound settled and some sound as if they want to move. Classical theory sorts them: the unison, " +
            "the octave and the fifth are perfect consonances; thirds and sixths are imperfect consonances; seconds, " +
            "sevenths and the tritone are dissonances. The fourth is the odd one: consonant between upper voices, treated " +
            "as a dissonance against the bass. The settled ones are those with simple ratios in the harmonic series (2:1, " +
            "3:2, 5:4), whose partials line up. How much tension a style hears in them varies, though. In the blues a " +
            "dominant seventh chord is home; in jazz a major seventh or an added ninth is a resting sound; in a lot of " +
            "rock the fifth alone, the power chord, is the whole chord. Dissonance isn't a fault. It's what makes music move.",
        see = CourseSee.Table("THE CLASSICAL SORTING", listOf("", "INTERVALS"), listOf(
            listOf("Perfect consonance", "P1 P5 P8"),
            listOf("Imperfect consonance", "m3 M3 m6 M6"),
            listOf("Dissonance", "m2 M2 A4 d5 m7 M7"),
            listOf("Depends", "P4: consonant above, dissonant over the bass"),
        )),
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
        "A dissonance usually moves to a consonance, and that move is called resolution. The standard example is the " +
            "tritone in a dominant seventh: in G7 the B and the F are a diminished fifth apart, and they resolve inward by " +
            "half steps, B up to C and F down to E, landing on the major third C–E of the C chord. Turn it over, F below B, " +
            "and it's an augmented fourth that resolves outward, to E and C, a minor sixth. A suspension makes tension by " +
            "delay: hold the 4th over a chord (Csus4 is C F G) and let it fall to the 3rd (C E G). Left unresolved, the pull " +
            "is part of the sound: ending on the dissonance sounds open, like a question.",
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
        "Six half steps, three whole steps, exactly half an octave: the tritone. Spelled with letters four apart it's an " +
            "augmented fourth (C–F♯); five apart, a diminished fifth (C–G♭). Because it's half the octave it turns into " +
            "itself when inverted: C up to F♯ is an A4, F♯ up to C a d5, both six half steps. For the same reason the " +
            "twelve keys hold only six different tritones. A major scale has exactly one, between its 4th and 7th degrees " +
            "(F and B in C major), and that one tritone does a lot to tell the ear which key it's in. Renaissance singers " +
            "steered around it in melodies, and it picked up the nickname diabolus in musica.",
        see = CourseSee.Table("THE SIX TRITONES", listOf("LOWER", "UPPER", "AS WRITTEN"), TRITONES.map { (a, b, i) ->
            listOf(shown(a), shown(b), "$i · ${Reference.intervalName(i)}")
        }),
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
        "The dominant seventh is built around a tritone: in G7 (G B D F) the 3rd and the 7th, B and F, are a diminished " +
            "fifth apart, and their move to C and E is most of the pull of V7 to I. D♭7 is D♭ F A♭ C♭, and its 3rd and " +
            "7th are F and C♭, the same two keys as G7's B and F. That's why D♭7 can stand in for G7 on the way to C, the " +
            "tritone substitution: the root moves, the tritone stays. Other chords hold it too. The half-diminished " +
            "seventh has one between root and 5th (Bø7 is B D F A); the diminished seventh has two (C°7 is C E♭ G♭ B𝄫: " +
            "C–G♭ and E♭–B𝄫), which is why it can lead to four different chords. In scales it marks Lydian (the ♯4 " +
            "against the root) and Locrian (the ♭5).",
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
