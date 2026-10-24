package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.catalog.Scales
import com.betterappmafia.apophenia.core.learn.CourseIntervals.shown
import com.betterappmafia.apophenia.core.pitch.PitchClass
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.score.Notation

/*
 * Course · Scales & keys (the mockup's nine topics): the major scale and its degrees, key signatures, the circle of
 * fifths, the three minor scales, pentatonic and blues, the modes of major, melodic minor and harmonic minor/major,
 * symmetric scales and Messiaen's modes, and scales from other traditions as labelled approximations. Text written for
 * Apophenia; every spelling in the tables comes from ScalePages and every signature from Notation.fifths.
 */
internal object CourseScales : CourseKit() {
    private fun p(s: String) = PitchName.parse(s)
    private fun mids(r: Int, id: String) = Scales.byId(id).semitones.map { r + it }
    /** A scale up to its octave and back, from [r]. */
    private fun scale(r: Int, id: String) = piano(RefSound.scale(mids(r, id)))
    /** A scale up only, quickly. */
    private fun up(r: Int, id: String, at: Double = 0.0) = seq(mids(r, id) + (r + 12), step = 0.3, len = 0.32).map { it.copy(start = it.start + at, end = it.end + at) }
    internal fun formula(id: String) = Scales.byId(id).degrees.joinToString(" ") { accidentals(it.toString()) }
    internal fun notesOn(id: String, root: String) = ScalePages.page(Scales.byId(id), p(root)).notes.joinToString(" ") { shown(it) }
    private fun name(id: String) = accidentals(Scales.byId(id).names.first())
    private fun triad(r: Int, third: Int = 4, at: Double = 0.0, len: Double = 1.4) = RefSound.block(listOf(r, r + third, r + 7), at = at, length = len)

    // ------------------------------------------------------------------------------------------------- major

    private val major = CoursePage(
        "scale-major", "The major scale", 1,
        "Start on any note and climb whole, whole, half, whole, whole, whole, half (W W H W W W H) and you've played a " +
            "major scale. On C it's all white keys, C D E F G A B C. Every major scale has the same pattern, so every one " +
            "sounds alike, only higher or lower; what changes from key to key is which keys are black. Written, a major " +
            "scale uses each letter exactly once, which is why G major has F♯ rather than G♭ and F major has B♭ rather " +
            "than A♯. The pattern splits into two matching halves, W W H and W W H, joined by a whole step: C D E F and " +
            "G A B C, two four-note groups called tetrachords.",
        see = CourseSee.Table("MAJOR SCALES", listOf("KEY", "NOTES"), listOf("C", "G", "D", "F", "Bb", "E").map { k -> listOf(shown(p(k)), notesOn("major", k)) }),
        hear = listOf(
            sound("THE MAJOR SCALE") { r -> scale(r, "major") },
            sound("THE TWO HALVES") { r -> piano(seq(listOf(r, r + 2, r + 4, r + 5), step = 0.45, len = 0.42) + seq(listOf(r + 7, r + 9, r + 11, r + 12), step = 0.45, len = 0.42).map { it.copy(start = it.start + 2.2, end = it.end + 2.2) }) },
        ),
        tryIt = CourseTry("THE MAJOR SCALE FROM ANY KEY", RefKind.SCALE, "major", "C"),
        check = CourseCheck("A major scale, or one note off?", listOf(
            option("Major", "A major scale: W W H W W W H.") { r, _ -> piano(up(r, "major")) },
            option("One note off", "Not quite: one note was moved a half step.") { r, rnd ->
                val s = Scales.byId("major").semitones.toMutableList()
                val (at, by) = listOf(2 to -1, 3 to 1, 5 to -1, 6 to -1)[rnd.nextInt(4)]
                s[at] += by
                piano(seq(s.map { r + it } + (r + 12), step = 0.3, len = 0.32))
            },
        )),
        related = listOf(ref(RefKind.SCALE, "major", "The major scale"), ear("deg-major", "degrees in a major key")),
    )

    internal val DEGREE_NAMES = listOf("tonic", "supertonic", "mediant", "subdominant", "dominant", "submediant", "leading tone")

    private val degrees = CoursePage(
        "scale-degrees", "Scale degrees", 1,
        "Each note of a scale has a number, its degree, counted up from the tonic, and a traditional name: 1 tonic, 2 " +
            "supertonic, 3 mediant, 4 subdominant, 5 dominant, 6 submediant, 7 leading tone. The names describe roles. " +
            "The tonic is home. The dominant, a fifth up, is the strongest pull away from it and back. The leading tone sits " +
            "a half step below the tonic and leans hard into it, and the 4th tends to settle down onto the 3rd. In natural " +
            "minor, where the 7th is a whole step below the tonic, it's called the subtonic instead. Musicians mostly use " +
            "the numbers (\"land on the 3\", \"the 5 of the key\"); the Nashville numbers and Roman numerals build chords on them.",
        see = CourseSee.Table("DEGREES IN C MAJOR", listOf("", "NAME", "IN C"), ScalePages.page(Scales.byId("major"), p("C")).notes.mapIndexed { i, n ->
            listOf("${i + 1}", DEGREE_NAMES[i], shown(n))
        }),
        hear = listOf(
            sound("A CADENCE, THEN 7 → 1") { r -> piano(Ear.cadence(r, false) + seq(listOf(r + 11, r + 12), step = 0.8, len = 0.75).map { it.copy(start = it.start + 3.3, end = it.end + 3.3) }) },
            sound("A CADENCE, THEN 4 → 3") { r -> piano(Ear.cadence(r, false) + seq(listOf(r + 5, r + 4), step = 0.8, len = 0.75).map { it.copy(start = it.start + 3.3, end = it.end + 3.3) }) },
        ),
        tryIt = CourseTry("EVERY DEGREE OF THE KEY ON YOUR NECK", RefKind.SCALE, "major", "C"),
        check = CourseCheck("After the cadence: the tonic, or the leading tone?", listOf(
            option("Tonic", "The tonic: home, it stays put.") { r, _ -> piano(Ear.cadence(r, false) + note(3.3, 1.6, r + 12)) },
            option("Leading tone", "The leading tone: a half step under home, it wants to move up.") { r, _ -> piano(Ear.cadence(r, false) + note(3.3, 1.6, r + 11)) },
        )),
        related = listOf(ear("deg-major", "degrees in a major key"), ear("deg-minor", "degrees in a minor key")),
    )

    // ------------------------------------------------------------------------------------------------- signatures

    internal val SHARP_ORDER = listOf("F", "C", "G", "D", "A", "E", "B")
    internal val FLAT_ORDER = SHARP_ORDER.reversed()
    /** The fifteen major keys, seven flats to seven sharps. */
    internal val MAJOR_KEYS = listOf("Cb", "Gb", "Db", "Ab", "Eb", "Bb", "F", "C", "G", "D", "A", "E", "B", "F#", "C#")
    internal fun signature(fifths: Int) = if (fifths >= 0) SHARP_ORDER.take(fifths).map { "$it♯" } else FLAT_ORDER.take(-fifths).map { "$it♭" }

    private val signatures = CoursePage(
        "key-signatures", "Sharps and flats in order", 2,
        "Writing F♯ every time in G major would be tiresome, so the sharps or flats a key always uses are written once, at " +
            "the start of every line: the key signature. Sharps always come in the same order, F C G D A E B, and flats in " +
            "the reverse, B E A D G C F. A key with two sharps has F♯ and C♯ and no others; a key with three flats has B♭, " +
            "E♭ and A♭. Two shortcuts for major keys. With sharps, the tonic is a half step above the last sharp: the last " +
            "sharp is C♯, so it's D major. With flats, the tonic is the second-to-last flat: B♭ E♭ A♭ is E♭ major (and one " +
            "flat, on its own, is F major). C major has none. An accidental in the music then changes a note for the rest " +
            "of its bar only.",
        see = CourseSee.KeyStaff("major"),
        hear = listOf(sound("THE KEY'S SCALE") { r -> scale(r, "major") }),
        tryIt = CourseTry("PICK A KEY · THE STAFF ABOVE SHOWS ITS SIGNATURE", RefKind.SCALE, "major", "D"),
        related = listOf(page("key-all", "Every key signature"), ref(RefKind.SCALE, "major", "The major scale")),
    )

    private val allKeys = CoursePage(
        "key-all", "Every key signature", 2,
        "There are fifteen major key signatures: none, one to seven sharps, one to seven flats. Three pairs sound the same " +
            "and are written differently: B major (five sharps) and C♭ major (seven flats), F♯ major (six sharps) and G♭ " +
            "major (six flats), C♯ major (seven sharps) and D♭ major (five flats). Each signature also serves a minor key, " +
            "its relative minor, built on the 6th degree, three half steps down: A minor shares C major's empty signature, " +
            "E minor G major's one sharp. Modes take their parent's signature, so D Dorian is usually written with no " +
            "sharps or flats, though some writers prefer the signature of D minor plus a natural.",
        see = CourseSee.Table("THE FIFTEEN SIGNATURES", listOf("MAJOR", "", "SIGNATURE", "RELATIVE MINOR"), MAJOR_KEYS.map { k ->
            val f = Notation.fifths(KeyMode(p(k), "major"))
            listOf(shown(p(k)), if (f == 0) "—" else if (f > 0) "$f♯" else "${-f}♭", signature(f).joinToString(" "), shown(ScalePages.page(Scales.byId("major"), p(k)).notes[5]) + " minor")
        }),
        hear = listOf(sound("C MAJOR, THEN A MINOR · THE SAME NOTES") { r -> piano(up(r, "major") + up(r - 3, "aeolian", at = 3.0)) }),
        tryIt = CourseTry("A RELATIVE MINOR · THE SAME NOTES FROM THE 6TH", RefKind.SCALE, "aeolian", "A"),
        check = CourseCheck("The same notes, two homes: the major, or its relative minor?", listOf(
            option("Major", "The major: it starts and ends on the tonic, bright.") { r, _ -> piano(up(r, "major")) },
            option("Relative minor", "The relative minor: the same notes from the 6th, dark.") { r, _ -> piano(up(r - 3, "aeolian")) },
        )),
        related = listOf(page("circle-fifths", "The circle of fifths"), ref(RefKind.SCALE, "aeolian", "Natural minor", "A")),
    )

    // ------------------------------------------------------------------------------------------------- circle

    /** Clockwise from the top: the major keys and their relative minors. */
    internal val CIRCLE_MAJOR = listOf("C", "G", "D", "A", "E", "B", "F#", "Db", "Ab", "Eb", "Bb", "F")
    internal val CIRCLE_MINOR = listOf("A", "E", "B", "F#", "C#", "G#", "Eb", "Bb", "F", "C", "G", "D")

    private val circle = CoursePage(
        "circle-fifths", "The circle of fifths", 2,
        "Arrange the twelve keys so each is a perfect fifth above the last and they close into a circle: C G D A E B F♯ " +
            "D♭ A♭ E♭ B♭ F, and back to C. Each step clockwise adds a sharp to the key signature or takes away a flat; each " +
            "step anticlockwise adds a flat. At the bottom the two sides meet, where F♯ major (six sharps) and G♭ major " +
            "(six flats) are the same keys. Neighbours share six of their seven notes, so one step round the circle sounds " +
            "close and a jump across it sounds far. Read anticlockwise it's a chain of falling fifths, the way roots often " +
            "move in a progression: E A D G C, each chord the V of the next. The inner ring is each key's relative minor.",
        see = CourseSee.Circle,
        hear = listOf(
            sound("C → G · ONE STEP") { r -> piano(triad(r) + triad(r + 7, at = 1.6)) },
            sound("C → F · ONE STEP BACK") { r -> piano(triad(r) + triad(r + 5, at = 1.6)) },
            sound("C → F♯ · ACROSS") { r -> piano(triad(r) + triad(r + 6, at = 1.6)) },
        ),
        tryIt = CourseTry("D MAJOR, ONE STEP CLOCKWISE FROM G", RefKind.SCALE, "major", "D"),
        check = CourseCheck("The chord moves one step. Up a fifth, or down a fifth?", listOf(
            option("↑ Fifth", "Up a fifth: one step clockwise, I to V.") { r, _ -> piano(triad(r) + triad(r + 7, at = 1.6)) },
            option("↓ Fifth", "Down a fifth: one step anticlockwise, I to IV.") { r, _ -> piano(triad(r) + triad(r - 7, at = 1.6)) },
        ), roots = 55..64),
        related = listOf(page("circle-neighbours", "Close and distant keys"), page("key-all", "Every key signature")),
    )

    /** Notes two major keys [steps] apart round the circle share. */
    internal fun shared(steps: Int): Int {
        val a = Scales.byId("major").semitones.toSet()
        val b = Scales.byId("major").semitones.map { Math.floorMod(it + 7 * steps, 12) }.toSet()
        return a.intersect(b).size
    }

    private val neighbours = CoursePage(
        "circle-neighbours", "Close and distant keys", 2,
        "A key's closest relatives are its two neighbours on the circle and the relative minors of all three: " +
            "for C major that's G major, F major, A minor, E minor and D minor, the keys of the other major " +
            "and minor chords in C (ii, iii, IV, V and vi). These are the closely related keys, at most one sharp or flat " +
            "away, and moving between them sounds smooth. Every step further round the circle loses a shared note until, " +
            "five or six steps away, two keys share only two notes and a change between them sounds like a jolt. Songs " +
            "often step up a half step or a whole step for a last chorus, a jump of five or two steps round the circle.",
        see = CourseSee.Table("HOW MANY NOTES TWO KEYS SHARE", listOf("STEPS", "FROM C", "SHARED"), (0..6).map { s ->
            listOf("$s", shown(p(CIRCLE_MAJOR[s])) + (if (s in 1..5) " · " + shown(p(CIRCLE_MAJOR[12 - s])) else ""), "${shared(s)} of 7")
        }),
        hear = listOf(
            sound("C, THEN G · ONE STEP") { r -> piano(up(r, "major") + up(r + 7, "major", at = 3.0)) },
            sound("C, THEN F♯ · SIX STEPS") { r -> piano(up(r, "major") + up(r + 6, "major", at = 3.0)) },
        ),
        check = CourseCheck("Two scales. Is the second a neighbour, or across the circle?", listOf(
            option("Neighbour", "A neighbour: one note changed.") { r, rnd -> piano(up(r, "major") + up(r + if (rnd.nextBoolean()) 7 else 5, "major", at = 3.0)) },
            option("Across", "Across the circle: only two notes in common.") { r, rnd -> piano(up(r, "major") + up(r + if (rnd.nextBoolean()) 6 else 1, "major", at = 3.0)) },
        ), roots = 52..62),
        related = listOf(ear("hm-modulation", "where it modulates"), page("circle-fifths", "The circle of fifths")),
    )

    // ------------------------------------------------------------------------------------------------- minor

    private val naturalMinor = CoursePage(
        "minor-natural", "Natural minor", 2,
        "The natural minor scale is 1 2 ♭3 4 5 ♭6 ♭7, stepping W H W W H W W. It has the same notes as the major scale " +
            "three half steps up, its relative major: A minor is C major's notes with A as home. On the same tonic, the " +
            "parallel minor lowers the 3rd, 6th and 7th of the major: C minor is C D E♭ F G A♭ B♭, three flats. It's also " +
            "the Aeolian mode. The lowered 3rd is what makes a scale minor at all; the 6th and 7th are where the three " +
            "minor scales differ.",
        see = CourseSee.Table("C MAJOR AND C MINOR", listOf("", "FORMULA", "ON C"), listOf("major", "aeolian").map { listOf(name(it), formula(it), notesOn(it, "C")) }),
        hear = listOf(
            sound("MAJOR, THEN MINOR · SAME TONIC") { r -> piano(up(r, "major") + up(r, "aeolian", at = 3.0)) },
            sound("NATURAL MINOR") { r -> scale(r, "aeolian") },
        ),
        tryIt = CourseTry("NATURAL MINOR FROM ANY KEY", RefKind.SCALE, "aeolian", "A"),
        check = CourseCheck("Major scale or minor scale?", listOf(
            option("Major", "Major: the 3rd is four half steps up.") { r, _ -> piano(up(r, "major")) },
            option("Minor", "Minor: the 3rd is three half steps up.") { r, _ -> piano(up(r, "aeolian")) },
        )),
        related = listOf(ref(RefKind.SCALE, "aeolian", "Natural minor", "A"), ear("deg-minor", "degrees in a minor key")),
    )

    private val harmonicMinor = CoursePage(
        "minor-harmonic", "Harmonic minor", 2,
        "In natural minor the 7th sits a whole step below the tonic, so the chord on the 5th degree is minor (E minor in A " +
            "minor) and doesn't pull home strongly. Raising the 7th fixes that: G becomes G♯, the chord on 5 becomes E major " +
            "(E7 with its seventh), and G♯ leads up into A. That scale, 1 2 ♭3 4 5 ♭6 7, is harmonic minor, named for the " +
            "harmony it makes. It leaves a gap of an augmented second between the ♭6 and the 7 (F up to G♯, three half " +
            "steps), which gives the scale its exotic edge. Classical melodies tend to step around that gap; a lot of " +
            "metal and flamenco-flavoured music leans into it.",
        hear = listOf(
            sound("HARMONIC MINOR") { r -> scale(r, "harmonic-minor") },
            sound("Em → Am, THEN E → Am") { r -> piano(triad(r - 5, 3) + triad(r, 3, at = 1.6) + triad(r - 5, 4, at = 3.6) + triad(r, 3, at = 5.2)) },
        ),
        tryIt = CourseTry("HARMONIC MINOR FROM ANY KEY", RefKind.SCALE, "harmonic-minor", "A"),
        check = CourseCheck("Natural minor or harmonic minor?", listOf(
            option("Natural", "Natural minor: a whole step from 7 to the top.") { r, _ -> piano(up(r, "aeolian")) },
            option("Harmonic", "Harmonic minor: the raised 7th, and the wide step before it.") { r, _ -> piano(up(r, "harmonic-minor")) },
        )),
        related = listOf(ref(RefKind.SCALE, "harmonic-minor", "Harmonic minor", "A"), ref(RefKind.INTERVAL, "A2", "Augmented second")),
    )

    private val melodicMinor = CoursePage(
        "minor-melodic", "Melodic minor", 2,
        "Melodic minor raises the 6th as well as the 7th, closing the augmented second: 1 2 ♭3 4 5 6 7. In the classical " +
            "tradition that's the way up, and on the way down a melody goes back to natural minor (♭7, ♭6), since there's " +
            "no leading tone to satisfy. Jazz uses the raised form both ways, calls it jazz minor, and treats it as a parent " +
            "scale with seven modes of its own. Its only difference from the major scale is the ♭3.",
        hear = listOf(
            sound("CLASSICAL · UP MELODIC, DOWN NATURAL") { r -> piano(seq(mids(r, "melodic-minor") + (r + 12) + mids(r, "aeolian").reversed(), step = 0.3, len = 0.32)) },
            sound("JAZZ MINOR · UP AND DOWN") { r -> scale(r, "melodic-minor") },
        ),
        tryIt = CourseTry("MELODIC MINOR FROM ANY KEY", RefKind.SCALE, "melodic-minor", "A"),
        check = CourseCheck("Which minor, going up?", listOf(
            option("Natural", "Natural minor: ♭6 and ♭7.") { r, _ -> piano(up(r, "aeolian")) },
            option("Harmonic", "Harmonic minor: ♭6 and 7, the gap between them.") { r, _ -> piano(up(r, "harmonic-minor")) },
            option("Melodic", "Melodic minor: 6 and 7, like major with a ♭3.") { r, _ -> piano(up(r, "melodic-minor")) },
        )),
        related = listOf(ref(RefKind.SCALE, "melodic-minor", "Melodic minor", "A"), page("modes-melodic", "Modes of melodic minor")),
    )

    // ------------------------------------------------------------------------------------------------- pentatonic

    internal val BLACK_KEYS = listOf(66, 68, 70, 73, 75)

    private val pentatonic = CoursePage(
        "penta", "Pentatonic scales", 2,
        "Leave the 4th and 7th out of a major scale and you have the major pentatonic, 1 2 3 5 6: five notes, no half " +
            "steps, no tritone. Start it from its 6th and you have the minor pentatonic, 1 ♭3 4 5 ♭7, with the same notes: " +
            "A minor pentatonic is C major pentatonic from A. The five black keys are one, G♭ major or E♭ minor pentatonic. " +
            "With no half steps nothing in it clashes hard, so almost any order of its notes sounds fine over its key, which " +
            "is why it's the first soloing scale for most guitarists. Across the neck it falls into five overlapping shapes, " +
            "the first starting on the root on the low E string.",
        see = CourseSee.Table("PENTATONIC ON C AND A", listOf("", "FORMULA", "NOTES"), listOf(
            listOf(name("major-pentatonic") + " · C", formula("major-pentatonic"), notesOn("major-pentatonic", "C")),
            listOf(name("minor-pentatonic") + " · A", formula("minor-pentatonic"), notesOn("minor-pentatonic", "A")),
        )),
        hear = listOf(
            sound("MAJOR PENTATONIC") { r -> scale(r, "major-pentatonic") },
            sound("MINOR PENTATONIC") { r -> scale(r, "minor-pentatonic") },
            sound("THE BLACK KEYS") { piano(seq(BLACK_KEYS + 78, step = 0.32, len = 0.3)) },
        ),
        tryIt = CourseTry("ALL FIVE SHAPES AT ONCE · EVERY NOTE OF THE SCALE ON YOUR NECK", RefKind.SCALE, "minor-pentatonic", "A"),
        check = CourseCheck("Five notes or seven?", listOf(
            option("Pentatonic", "Pentatonic: five notes, no half steps.") { r, _ -> piano(up(r, "major-pentatonic")) },
            option("Major scale", "The full major scale: seven notes.") { r, _ -> piano(up(r, "major")) },
        )),
        related = listOf(ref(RefKind.SCALE, "minor-pentatonic", "Minor pentatonic", "A"), ref(RefKind.SCALE, "major-pentatonic", "Major pentatonic")),
        yours = RefKind.SCALE to "minor-pentatonic",
    )

    private val blues = CoursePage(
        "blues", "The blues scale", 2,
        "Add a ♭5 to the minor pentatonic and you have the blues scale, 1 ♭3 4 ♭5 5 ♭7: in A, A C D E♭ E G. The ♭5 is a " +
            "passing note, usually slid through on the way to or from the 5th. In the blues the blue notes, ♭3, ♭5 and ♭7, " +
            "are often sung or bent somewhere between flat and natural, which is why a minor third over a major chord (the " +
            "C over an A7) is part of the sound rather than a clash. The major blues scale adds a ♭3 to the major " +
            "pentatonic instead, 1 2 ♭3 3 5 6, with the ♭3 sliding up into the 3.",
        see = CourseSee.Table("THE TWO BLUES SCALES ON A", listOf("", "FORMULA", "NOTES"), listOf("minor-blues", "major-blues").map { listOf(name(it), formula(it), notesOn(it, "A")) }),
        hear = listOf(
            sound("BLUES SCALE") { r -> scale(r, "minor-blues") },
            sound("MAJOR BLUES") { r -> scale(r, "major-blues") },
            sound("♭3 TO 3 OVER A MAJOR CHORD") { r -> piano(RefSound.block(listOf(r - 12, r - 5, r - 2, r + 4), length = 3.0) + seq(listOf(r + 3, r + 4), step = 0.5, len = 0.5).map { it.copy(start = it.start + 0.6, end = it.end + 0.6) }) },
        ),
        tryIt = CourseTry("THE BLUES SCALE ON YOUR NECK", RefKind.SCALE, "minor-blues", "A"),
        check = CourseCheck("Minor pentatonic, or the blues scale?", listOf(
            option("Pentatonic", "Minor pentatonic: five notes.") { r, _ -> piano(up(r, "minor-pentatonic")) },
            option("Blues", "The blues scale: the ♭5 slips in between 4 and 5.") { r, _ -> piano(up(r, "minor-blues")) },
        )),
        related = listOf(ref(RefKind.SCALE, "minor-blues", "Blues scale", "A"), ref(RefKind.CHORD, "7", "A7", "A")),
        yours = RefKind.SCALE to "minor-blues",
    )

    // ------------------------------------------------------------------------------------------------- modes

    internal val MAJOR_MODES = listOf("major", "dorian", "phrygian", "lydian", "mixolydian", "aeolian", "locrian")
    internal val BRIGHT_TO_DARK = listOf("lydian", "major", "mixolydian", "dorian", "aeolian", "phrygian", "locrian")

    private fun overDrone(r: Int, id: String) = EarAudio(up(r, id) + seq(mids(r, id).reversed(), step = 0.3, len = 0.32).map { it.copy(start = it.start + 2.4, end = it.end + 2.4) }, drone = r - 12)

    private val modes = CoursePage(
        "modes-seven", "One scale, seven modes", 3,
        "Play C major's notes from D to D and the home changes: the same seven notes centred on D make D Dorian. " +
            "Starting from each degree in turn gives seven modes: Ionian (the major scale itself), Dorian, Phrygian, " +
            "Lydian, Mixolydian, Aeolian (natural minor) and Locrian. There are two ways to think of them. As rotations of " +
            "a parent, D Dorian is C major from D: the relative view, handy for finding the notes. As changes to major or " +
            "minor on the same tonic, D Dorian is D minor with a raised 6th: the parallel view, which is how they sound. " +
            "Over a held D the colour changes from mode to mode while home stays put.",
        see = CourseSee.Table("THE MODES ON C", listOf("", "FORMULA", "ON C", "NOTES OF"), MAJOR_MODES.map { id ->
            val page = ScalePages.page(Scales.byId(id), p("C"))
            listOf(name(id), formula(id), page.notes.joinToString(" ") { shown(it) }, page.parent?.let { (_, pr) -> shown(pr) + " major" } ?: "C major")
        }),
        hear = listOf(
            sound("C MAJOR'S NOTES FROM D") { r -> piano(up(r + 2, "dorian")) },
            sound("DORIAN OVER A DRONE") { r -> overDrone(r, "dorian") },
            sound("AEOLIAN OVER A DRONE") { r -> overDrone(r, "aeolian") },
        ),
        tryIt = CourseTry("D DORIAN · C MAJOR'S NOTES WITH D AS HOME", RefKind.SCALE, "dorian", "D"),
        check = CourseCheck("Over the drone: Dorian or Aeolian?", listOf(
            option("Dorian", "Dorian: minor with a natural 6th.") { r, _ -> overDrone(r, "dorian") },
            option("Aeolian", "Aeolian: natural minor, the ♭6.") { r, _ -> overDrone(r, "aeolian") },
        ), roots = 50..62),
        related = listOf(ref(RefKind.SCALE, "dorian", "D Dorian", "D"), ear("mode-major", "modes of major")),
    )

    private val bright = CoursePage(
        "modes-bright", "Bright to dark", 3,
        "Line the modes up on one tonic, ordered by how bright they sound, and each step to the next changes a single " +
            "note: Lydian (♯4), Ionian, Mixolydian (♭7), Dorian (♭3 ♭7), Aeolian (♭3 ♭6 ♭7), Phrygian (♭2 ♭3 ♭6 ♭7), " +
            "Locrian (♭2 ♭3 ♭5 ♭6 ♭7). Lydian is the brightest and Locrian the darkest. Much of each mode's colour comes " +
            "from its characteristic note, the one that sets it apart from plain major or minor: Lydian's ♯4, " +
            "Mixolydian's ♭7, Dorian's natural 6, Phrygian's ♭2. Locrian's ♭5 makes its own tonic chord diminished, so " +
            "it's rarely used as a home.",
        see = CourseSee.Table("BRIGHT TO DARK", listOf("", "FORMULA", "CHANGED"), BRIGHT_TO_DARK.mapIndexed { i, id ->
            val changed = if (i == 0) "" else Scales.byId(id).degrees.filter { it !in Scales.byId(BRIGHT_TO_DARK[i - 1]).degrees }.joinToString(" ") { accidentals(it.toString()) }
            listOf(name(id), formula(id), changed)
        }),
        hear = listOf(sound("ALL SEVEN ON ONE NOTE, BRIGHT TO DARK") { r -> EarAudio(BRIGHT_TO_DARK.flatMapIndexed { i, id -> up(r, id, at = i * 2.9) }, drone = r - 12) }),
        tryIt = CourseTry("LYDIAN · THE BRIGHTEST", RefKind.SCALE, "lydian", "F"),
        check = CourseCheck("Over the drone: Lydian, Ionian or Mixolydian?", listOf(
            option("Lydian", "Lydian: the raised 4th.") { r, _ -> overDrone(r, "lydian") },
            option("Ionian", "Ionian: the major scale.") { r, _ -> overDrone(r, "major") },
            option("Mixolydian", "Mixolydian: the ♭7.") { r, _ -> overDrone(r, "mixolydian") },
        ), roots = 50..62),
        related = listOf(ear("mode-character", "characteristic notes"), ear("mode-major", "modes of major")),
    )

    // ------------------------------------------------------------------------------------------------- minor modes

    internal val MELODIC_MODES = Scales.all.filter { it.id == "melodic-minor" || it.modeOf == "melodic-minor" }.map { it.id }
    internal val HARMONIC_MODES = Scales.all.filter { it.id == "harmonic-minor" || it.modeOf == "harmonic-minor" }.map { it.id }

    private fun modeTable(ids: List<String>) = ids.mapIndexed { i, id -> listOf("${i + 1}", name(id), formula(id), notesOn(id, "C")) }

    private val melodicModes = CoursePage(
        "modes-melodic", "Modes of melodic minor", 4,
        "Melodic minor (the jazz form, the same both ways) has seven modes, and jazz uses most of them over particular " +
            "chords. Lydian dominant, its 4th mode, is 1 2 3 ♯4 5 6 ♭7: a dominant with a raised 11th, the sound of a 7♯11 " +
            "chord. Locrian ♯2, the 6th mode, fits a half-diminished m7♭5. The 7th mode, the altered scale, holds every " +
            "tension a dominant can carry: G altered is A♭ melodic minor from G, G A♭ B♭ C♭ D♭ E♭ F, and read against a " +
            "G7 its A♭ is the ♭9, B♭ the ♯9, C♭ the 3rd (B), D♭ the ♯11 and E♭ the ♭13. The names in the table are the " +
            "common ones; some modes go by two.",
        see = CourseSee.Table("MODES OF MELODIC MINOR ON C", listOf("", "", "FORMULA", "ON C"), modeTable(MELODIC_MODES)),
        hear = listOf(
            sound("LYDIAN DOMINANT OVER A DRONE") { r -> overDrone(r, "lydian-dominant") },
            sound("G7, THEN THE ALTERED SCALE") { r -> piano(RefSound.block(listOf(r - 12, r - 8, r - 5, r - 2), length = 1.4) + up(r, "altered", at = 1.6)) },
        ),
        tryIt = CourseTry("THE ALTERED SCALE · EVERY TENSION OF A DOMINANT", RefKind.SCALE, "altered", "G"),
        check = CourseCheck("Over the drone: Lydian dominant, or Mixolydian?", listOf(
            option("Lydian dominant", "Lydian dominant: the ♯4 with the ♭7.") { r, _ -> overDrone(r, "lydian-dominant") },
            option("Mixolydian", "Mixolydian: the natural 4.") { r, _ -> overDrone(r, "mixolydian") },
        ), roots = 50..62),
        related = listOf(ref(RefKind.SCALE, "lydian-dominant", "G Lydian dominant", "G"), ref(RefKind.CHORD, "7alt", "G7alt", "G"), ear("mode-melodic", "modes of melodic minor")),
    )

    private val harmonicModes = CoursePage(
        "modes-harmonic", "Modes of harmonic minor and major", 4,
        "Harmonic minor's modes all carry its augmented second somewhere. The best known is the 5th, Phrygian dominant, " +
            "1 ♭2 3 4 5 ♭6 ♭7: from A harmonic minor it's E F G♯ A B C D, heard in flamenco, in klezmer (where it's called " +
            "freygish) and in a lot of metal. Dorian ♯4 and Lydian ♯2 turn up in Eastern European folk music and in film " +
            "scores. Harmonic major is the major scale with a ♭6, 1 2 3 4 5 ♭6 7; its modes have no settled names, and " +
            "sources disagree on them.",
        see = CourseSee.Table("MODES OF HARMONIC MINOR ON C", listOf("", "", "FORMULA", "ON C"), modeTable(HARMONIC_MODES)),
        hear = listOf(
            sound("PHRYGIAN DOMINANT OVER A DRONE") { r -> overDrone(r, "phrygian-dominant") },
            sound("HARMONIC MAJOR") { r -> scale(r, "harmonic-major") },
        ),
        tryIt = CourseTry("E PHRYGIAN DOMINANT · A HARMONIC MINOR FROM E", RefKind.SCALE, "phrygian-dominant", "E"),
        check = CourseCheck("Over the drone: Phrygian, or Phrygian dominant?", listOf(
            option("Phrygian", "Phrygian: ♭2 with a minor 3rd.") { r, _ -> overDrone(r, "phrygian") },
            option("Phrygian dominant", "Phrygian dominant: ♭2 with a major 3rd.") { r, _ -> overDrone(r, "phrygian-dominant") },
        ), roots = 50..62),
        related = listOf(ref(RefKind.SCALE, "phrygian-dominant", "Phrygian dominant", "E"), ref(RefKind.SCALE, "harmonic-major", "Harmonic major"), ear("mode-harmonic", "modes of harmonic minor")),
    )

    // ------------------------------------------------------------------------------------------------- symmetric

    /** How many different pitch-class sets a scale makes across the twelve transpositions. */
    internal fun transpositions(pcs: Set<Int>) = (0..11).map { t -> pcs.map { Math.floorMod(it + t, 12) }.toSet() }.distinct().size

    private val symmetric = CoursePage(
        "symmetric", "Whole-tone and diminished", 4,
        "Some scales repeat inside the octave. The whole-tone scale is six whole steps; only two different ones exist " +
            "(on C and on D♭), and every chord inside it is augmented or a dominant with a raised or lowered 5th. The " +
            "diminished, or octatonic, scales alternate half and whole steps, eight notes, and only three different ones " +
            "exist. Starting with a half step (half-whole) fits a dominant 7♭9 chord; starting with a whole step " +
            "(whole-half) fits a diminished seventh chord. The augmented scale alternates minor thirds and half steps and " +
            "comes in four. Because they repeat, none of them has a single home note: any of their repeating points sounds " +
            "as good a tonic as another.",
        see = CourseSee.Table("SYMMETRIC SCALES ON C", listOf("", "STEPS", "NOTES", "DIFFERENT"), listOf("whole-tone", "half-whole", "whole-half", "augmented").map { id ->
            val page = ScalePages.page(Scales.byId(id), p("C"))
            listOf(name(id), page.steps.joinToString(" ") { ScalePages.stepName(it) }, page.notes.joinToString(" ") { shown(it) }, "${transpositions(Scales.byId(id).semitones.toSet())}")
        }),
        hear = listOf(
            sound("WHOLE-TONE") { r -> scale(r, "whole-tone") },
            sound("HALF-WHOLE") { r -> scale(r, "half-whole") },
            sound("WHOLE-HALF") { r -> scale(r, "whole-half") },
        ),
        tryIt = CourseTry("HALF-WHOLE · OVER A DOMINANT 7♭9", RefKind.SCALE, "half-whole", "G"),
        check = CourseCheck("Whole-tone, or diminished?", listOf(
            option("Whole-tone", "Whole-tone: six notes, all whole steps.") { r, _ -> piano(up(r, "whole-tone")) },
            option("Diminished", "Diminished: eight notes, half and whole steps in turn.") { r, rnd -> piano(up(r, if (rnd.nextBoolean()) "half-whole" else "whole-half")) },
        )),
        related = listOf(ref(RefKind.SCALE, "whole-tone", "Whole-tone"), ref(RefKind.CHORD, "7b9", "G7♭9", "G"), ref(RefKind.CHORD, "dim7", "C°7"), ear("mode-symmetric", "symmetric scales")),
    )

    /** Messiaen's seven modes of limited transposition, as pitch-class sets on C (no single spelling). */
    internal val MESSIAEN = listOf(
        setOf(0, 2, 4, 6, 8, 10), setOf(0, 1, 3, 4, 6, 7, 9, 10), setOf(0, 2, 3, 4, 6, 7, 8, 10, 11), setOf(0, 1, 2, 5, 6, 7, 8, 11),
        setOf(0, 1, 5, 6, 7, 11), setOf(0, 2, 4, 5, 6, 8, 10, 11), setOf(0, 1, 2, 3, 5, 6, 7, 8, 9, 11),
    )

    private val messiaen = CoursePage(
        "messiaen", "Messiaen's modes", 4,
        "The composer Olivier Messiaen collected seven \"modes of limited transposition\": scales that land back on " +
            "themselves after fewer than twelve transpositions. Mode 1 is the whole-tone scale (two transpositions), mode 2 " +
            "the half-whole octatonic (three), mode 3 a nine-note scale of a whole step and two half steps repeated three " +
            "times (four); modes 4 to 7 come in six transpositions each. He valued them for a harmony that floats without " +
            "pulling to a key. They have no single correct spelling, so the table names their notes by piano key.",
        see = CourseSee.Table("THE SEVEN MODES ON C", listOf("MODE", "NOTES", "COUNT", "DIFFERENT"), MESSIAEN.mapIndexed { i, s ->
            listOf("${i + 1}", s.sorted().joinToString(" ") { shown(PitchClass(it).defaultSpelling()) }, "${s.size}", "${transpositions(s)}")
        }),
        hear = MESSIAEN.mapIndexed { i, s -> sound("MODE ${i + 1}") { r -> piano(seq(s.sorted().map { r + it } + (r + 12), step = 0.3, len = 0.32)) } },
        related = listOf(page("symmetric", "Whole-tone and diminished")),
    )

    // ------------------------------------------------------------------------------------------------- world

    internal val WORLD = Scales.all.filter { it.family == "other" || it.family == "japanese" }.map { it.id }

    private val world = CoursePage(
        "world", "Scales from other traditions", 4,
        "Many traditions don't divide the octave into twelve equal half steps, so any piano-key version of their scales is " +
            "an approximation. Arabic maqam uses steps between a half and a whole step, written as quarter tones though " +
            "they vary from region to region. An Indian raga is defined as much by its characteristic phrases and ornaments " +
            "as by its notes. Gamelan tunings differ from one ensemble to the next. The scales listed here under such names " +
            "are the twelve-tone versions in common use in Western music: the Japanese hirajoshi, in and iwato, pentatonic " +
            "scales built around half steps; the double harmonic, close to the maqam called hijaz kar; Hungarian minor; " +
            "the Persian scale. Use them as colours, and read the names as labels, not as the traditions themselves.",
        see = CourseSee.Table("ON C", listOf("", "FORMULA", "NOTES"), WORLD.map { listOf(name(it), formula(it), notesOn(it, "C")) }),
        hear = listOf(
            sound("HIRAJOSHI") { r -> scale(r, "hirajoshi") },
            sound("IN") { r -> scale(r, "in") },
            sound("DOUBLE HARMONIC") { r -> scale(r, "double-harmonic") },
        ),
        tryIt = CourseTry("HIRAJOSHI ON YOUR NECK", RefKind.SCALE, "hirajoshi", "A"),
        related = listOf(ref(RefKind.SCALE, "double-harmonic", "Double harmonic"), ref(RefKind.SCALE, "hungarian-minor", "Hungarian minor")),
    )

    val topics = listOf(
        CourseTopic("major", "The major scale", "steps, degrees, on piano and fretboard", 1, listOf(major, degrees)),
        CourseTopic("signatures", "Key signatures", "reading sharps and flats", 2, listOf(signatures, allKeys)),
        CourseTopic("circle", "The circle of fifths", "keys as neighbours", 2, listOf(circle, neighbours)),
        CourseTopic("minor", "Minor scales", "natural, harmonic, melodic", 2, listOf(naturalMinor, harmonicMinor, melodicMinor)),
        CourseTopic("penta", "Pentatonic and blues", "five notes, the blue notes", 2, listOf(pentatonic, blues)),
        CourseTopic("modes", "Modes of the major scale", "one scale, seven homes", 3, listOf(modes, bright)),
        CourseTopic("minor-modes", "Modes of melodic and harmonic minor", "and harmonic major", 4, listOf(melodicModes, harmonicModes)),
        CourseTopic("symmetric", "Symmetric scales", "whole-tone, diminished, Messiaen", 4, listOf(symmetric, messiaen)),
        CourseTopic("world", "Scales from other traditions", "labelled approximations", 4, listOf(world)),
    )

}
