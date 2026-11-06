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

    /** [id] on [root] as MIDI up to its octave, the root between [low] and [low] + 11. */
    private fun sm(id: String, root: String = "C", low: Int = 60) = RefSound.midis(p(root).pitchClass, Scales.byId(id).semitones, low).let { it + (it.first() + 12) }
    /** The scale lit on the piano, each key labelled with its degree (or [labels]), ▶ playing it up one note at a time. */
    private fun scaleKeys(caption: String, id: String, root: String = "C", labels: List<String>? = null, faint: List<Int> = emptyList()): CourseSee.Keys {
        val ms = sm(id, root)
        val ls = labels ?: (Scales.byId(id).degrees.map { accidentals(it.toString()) } + "1")
        return CourseSee.Keys(caption, ms.mapIndexed { i, m -> Key(m, ls.getOrElse(i) { "" }) } + faint.filter { it !in ms }.map { Key(it, "", faint = true) }, Play(ms.map { listOf(it) }, 0.4))
    }
    /** A scale's notes named on the keys. */
    private fun scaleNames(caption: String, id: String, root: String = "C"): CourseSee.Keys {
        val names = ScalePages.page(Scales.byId(id), p(root)).notes.map { shown(it) }
        return scaleKeys(caption, id, root, names + names.first())
    }
    private fun scalePlay(id: String, root: String = "C") = Play(sm(id, root).map { listOf(it) }, 0.32)
    /** The steps between neighbouring notes, W, H or the size in half steps. */
    private fun stepsOf(id: String) = (Scales.byId(id).semitones + 12).zipWithNext { a, b -> when (b - a) { 1 -> "H"; 2 -> "W"; 3 -> "W+H"; else -> "${b - a}" } }
    /** A scale's notes as a grid row against twelve keys from C, with its steps written in the cells it uses. */
    private val KEY_COLUMNS = listOf("C", "D♭", "D", "E♭", "E", "F", "F♯/G♭", "G", "A♭", "A", "B♭", "B", "C")
    private fun stepRow(label: String, id: String): GridRow {
        val s = Scales.byId(id).semitones + 12
        val st = stepsOf(id)
        val cells = KEY_COLUMNS.indices.map { c -> val i = s.indexOf(c); if (i < 0) "" else if (i < st.size) st[i] else "1" }
        return row(label, cells, s.toSet(), Play(s.map { listOf(60 + it) }, 0.35))
    }

    // ------------------------------------------------------------------------------------------------- major

    private val major = CoursePage(
        "scale-major", "The major scale", 1,
        "The major scale is the ruler most of Western music is measured with. It isn't a set of notes so much as a " +
            "pattern of steps, and once you see the pattern you can build it from any note.",
        steps = listOf(
            say("On the piano, C major is the seven white keys from C to C. Look where the black keys are: there's one " +
                "between most neighbouring white keys, but none between E and F, or between B and C.",
                scaleNames("C MAJOR · THE WHITE KEYS", "major")),
            say("So the steps aren't all the same size. Where a black key sits between two white keys, the step is a whole " +
                "step (two half steps); where there's none, it's a half step. Read the row: W W H W W W H. That pattern is " +
                "the major scale. Each lit cell shows the step up to the next note.",
                CourseSee.Grid("THE PATTERN · W W H W W W H", KEY_COLUMNS, listOf(stepRow("major", "major")))),
            say("Start the same pattern on G. Five steps go fine on white keys, but the 7th step needs F♯, because E to F is " +
                "only a half step and the pattern asks for a whole one there. So G major has one black key, F♯. Every major " +
                "scale sounds alike, only higher or lower; what changes from key to key is which keys are black.",
                scaleNames("G MAJOR · ONE SHARP", "major", "G")),
            say("Written, a major scale uses each letter exactly once. That's why G major has F♯ rather than G♭ (there's " +
                "already a G), and F major has B♭ rather than A♯ (there's already an A). Tap a row to hear any of these.",
                table("MAJOR SCALES", listOf("KEY", "NOTES"), listOf("C", "G", "D", "F", "Bb", "E").map { k -> listOf(shown(p(k)), notesOn("major", k)) },
                    listOf("C", "G", "D", "F", "Bb", "E").map { scalePlay("major", it) })),
            say("The pattern splits into two matching halves: W W H, a whole step, W W H. C D E F and G A B C are the same " +
                "shape a fifth apart. These four-note halves are called tetrachords, and they're why the major scale sounds so " +
                "balanced.",
                CourseSee.Grid("TWO MATCHING HALVES", KEY_COLUMNS, listOf(
                    row("lower", KEY_COLUMNS.indices.map { c -> when (c) { 0, 2 -> "W"; 4 -> "H"; 5 -> "4"; else -> "" } }, setOf(0, 2, 4, 5), Play(listOf(60, 62, 64, 65).map { listOf(it) }, 0.4)),
                    row("upper", KEY_COLUMNS.indices.map { c -> when (c) { 7, 9 -> "W"; 11 -> "H"; 12 -> "8"; else -> "" } }, setOf(7, 9, 11, 12), Play(listOf(67, 69, 71, 72).map { listOf(it) }, 0.4)),
                ))),
        ),
        inShort = listOf(
            "Major scale = W W H W W W H from any note.",
            "C major is all white keys; other keys need black keys to keep the pattern.",
            "Each letter is used once, which decides sharp or flat names.",
            "Two matching halves (tetrachords), a whole step apart.",
        ),
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
        "Every note of a key has a number and a personality. Hearing a note as \"the 3\" or \"the 7\" of a key, rather than " +
            "as a letter, is what lets you sing a melody in any key, follow a bass line, and hear where a song is going.",
        steps = listOf(
            say("Number the notes of the scale from the home note, the tonic: 1 to 7. In C major, C is 1, D is 2, E is 3 and " +
                "so on. In G major, G is 1 and the same numbers move with it. The numbers describe a note's place in the key, " +
                "not its letter.",
                scaleKeys("DEGREES IN C MAJOR", "major")),
            say("Each degree also has a traditional name that describes its role. Tap a row to hear the key's cadence and " +
                "then that degree; listen to how each one feels against home.",
                table("THE DEGREES AND THEIR NAMES", listOf("", "NAME", "IN C"), ScalePages.page(Scales.byId("major"), p("C")).notes.mapIndexed { i, n ->
                    listOf("${i + 1}", DEGREE_NAMES[i], shown(n))
                }, Scales.byId("major").semitones.map { s -> Play(listOf(mids("C3 C4 E4 G4"), mids("F3 C4 F4 A4"), mids("G3 B3 D4 G4"), mids("C3 C4 E4 G4"), listOf(60 + s)), 0.8) })),
            say("Some degrees are stable and some lean. 1, 3 and 5 (the notes of the tonic chord) feel at rest. 7, the leading " +
                "tone, sits a half step under 1 and pulls up into it. 4 sits a half step above 3 and tends to settle down onto " +
                "it. 2 and 6 sound unfinished and usually step to a neighbour. Play 7 resolving to 1, then 4 resolving to 3.",
                keys("THE TWO HALF-STEP PULLS · 7 → 1 AND 4 → 3", "B4 C5", listOf("7", "1"), faint = "E4 F4", play = Play(listOf(listOf(71), listOf(72), listOf(65), listOf(64)), 0.8))),
            say("In natural minor the 7th is a whole step under the tonic, so it doesn't lean the same way; it's called the " +
                "subtonic. Musicians mostly use the numbers (\"land on the 3\", \"go to the 5\"), and the Nashville number " +
                "system and Roman numerals build whole chords on them.",
                null),
        ),
        inShort = listOf(
            "Degrees number the notes from the tonic, 1 to 7, in any key.",
            "1, 3, 5 are stable; 7 pulls up to 1; 4 settles down to 3.",
            "Names: tonic, supertonic, mediant, subdominant, dominant, submediant, leading tone.",
        ),
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
            "the start of every line: the key signature. They always appear in a fixed order, and that order lets you read " +
            "the key at a glance.",
        steps = listOf(
            say("Why sharps pile up in a particular order: each new sharp key is a fifth above the last, and each one keeps the " +
                "previous key's sharps and adds one more. G major needs F♯; D major (a fifth up) needs F♯ and C♯; A major adds " +
                "G♯. Tap the rows and listen to each scale gain one raised note.",
                table("SHARPS ARRIVE ONE AT A TIME", listOf("KEY", "SHARPS", "NEW ONE"), listOf("G", "D", "A", "E").mapIndexed { i, k -> listOf(shown(p(k)) + " major", signature(i + 1).joinToString(" "), signature(i + 1).last()) },
                    listOf("G", "D", "A", "E").map { scalePlay("major", it) })),
            say("So sharps always come in the order F C G D A E B, and flats in the reverse, B E A D G C F. A key with two " +
                "sharps has F♯ and C♯ and no others; a key with three flats has B♭, E♭ and A♭.",
                table("THE ORDER", listOf("", "1", "2", "3", "4", "5", "6", "7"), listOf(listOf("♯") + SHARP_ORDER, listOf("♭") + FLAT_ORDER))),
            say("Two shortcuts for major keys. With sharps, the tonic is a half step above the last sharp: if the last sharp is " +
                "C♯, the key is D major. With flats, the tonic is the second-to-last flat: B♭ E♭ A♭ means E♭ major (one flat on " +
                "its own is F major). C major has none.",
                table("READING THE KEY", listOf("SIGNATURE", "RULE", "KEY"), listOf(
                    listOf("F♯ C♯", "half step above C♯", "D major"), listOf("F♯ C♯ G♯ D♯", "half step above D♯", "E major"),
                    listOf("B♭ E♭ A♭", "second-to-last flat", "E♭ major"), listOf("B♭", "one flat", "F major"),
                ), listOf("D", "E", "Eb", "F").map { scalePlay("major", it) })),
            say("Here's a signature on the staff, with the key's scale written after it. The notes need no sharps or flats of " +
                "their own: the signature covers them. Change the key under TRY and the staff follows. An accidental written in " +
                "the music changes a note for the rest of its bar only.",
                null),
        ),
        inShort = listOf(
            "Sharps in order F C G D A E B; flats B E A D G C F.",
            "Sharp keys: tonic a half step above the last sharp. Flat keys: the second-to-last flat.",
            "The signature applies to every note on every line; accidentals last one bar.",
        ),
        see = CourseSee.KeyStaff("major"),
        hear = listOf(sound("THE KEY'S SCALE") { r -> scale(r, "major") }),
        tryIt = CourseTry("PICK A KEY · THE STAFF ABOVE SHOWS ITS SIGNATURE", RefKind.SCALE, "major", "D"),
        related = listOf(page("key-all", "Every key signature"), ref(RefKind.SCALE, "major", "The major scale")),
    )

    private val allKeys = CoursePage(
        "key-all", "Every key signature", 2,
        "There are fifteen major key signatures, from seven flats to seven sharps, and each one also belongs to a minor key. " +
            "This page lays them all out and shows why a few of them are really the same key written two ways.",
        steps = listOf(
            say("Here is every signature, flats to sharps. Each row plays that major scale. Notice the three pairs at the " +
                "ends that land on the same piano keys: B major (5♯) and C♭ major (7♭), F♯ major (6♯) and G♭ major (6♭), C♯ major " +
                "(7♯) and D♭ major (5♭). They're enharmonic: same sound, different spelling. Writers choose whichever has fewer " +
                "accidentals or fits the music around it.",
                table("THE FIFTEEN SIGNATURES", listOf("MAJOR", "", "SIGNATURE", "RELATIVE MINOR"), MAJOR_KEYS.map { k ->
                    val f = Notation.fifths(KeyMode(p(k), "major"))
                    listOf(shown(p(k)), if (f == 0) "—" else if (f > 0) "$f♯" else "${-f}♭", signature(f).joinToString(" "), shown(ScalePages.page(Scales.byId("major"), p(k)).notes[5]) + " minor")
                }, MAJOR_KEYS.map { scalePlay("major", it) })),
            say("Each signature also serves a minor key: the relative minor, built on the major scale's 6th degree, three half " +
                "steps down. A minor uses C major's notes from A, so it has C major's empty signature. Play C major, then the " +
                "same white keys from A: same notes, a different home, a darker sound.",
                CourseSee.Grid("C MAJOR AND A MINOR · THE SAME KEYS", cols("C D E F G A B"), listOf(
                    scaleRow("C major", cols("C D E F G A B"), "C4 D4 E4 F4 G4 A4 B4 C5"),
                    scaleRow("A minor", cols("C D E F G A B"), "A3 B3 C4 D4 E4 F4 G4 A4"),
                ))),
            say("To find a minor key's signature, go up three half steps to its relative major: E minor → G major, one sharp; " +
                "D minor → F major, one flat. Modes take their parent's signature too, so D Dorian (C major from D) is usually " +
                "written with no sharps or flats, though some writers prefer D minor's signature with a natural on the B.",
                null),
        ),
        inShort = listOf(
            "Fifteen major signatures, 7♭ to 7♯; three pairs are enharmonic.",
            "Each serves a relative minor on its 6th degree, three half steps down.",
            "Minor key's signature = its relative major's.",
        ),
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
        "The circle of fifths is a map of all twelve keys, arranged so that neighbours are the most closely related. It " +
            "explains key signatures, which keys sound close, and the way chord roots like to move.",
        steps = listOf(
            say("Start on C and go up a perfect fifth (7 half steps): G. Up another fifth: D. Keep going, C G D A E B F♯ C♯... " +
                "after twelve fifths you're back on C (spelled B♯ by then; the circle switches to flat names halfway round). " +
                "Play the chain of fifths and hear it climb.",
                keysLine("FIFTHS UP FROM C", "C3 G3 D4 A4 E5", listOf("C", "G", "D", "A", "E"), step = 0.6)),
            say("Each step clockwise adds one sharp to the key signature, because the new key a fifth up needs exactly one note " +
                "raised (its 7th). Each step anticlockwise adds one flat. So the number of sharps or flats is just the distance " +
                "from C round the circle.",
                table("ONE STEP, ONE SHARP", listOf("STEPS", "KEY", "SIGNATURE"), (0..5).map { s -> listOf("$s", shown(p(CIRCLE_MAJOR[s])), if (s == 0) "—" else signature(s).joinToString(" ")) },
                    (0..5).map { scalePlay("major", CIRCLE_MAJOR[it]) })),
            say("Neighbours on the circle share six of their seven notes (C and G differ only by F/F♯), so a move of one step " +
                "sounds close and natural. A jump across the circle, C to F♯, shares almost nothing and sounds far.",
                chart("ONE STEP, THEN ACROSS", cc("I", "C", "1 3 5"), cc("V", "G", "5 7 2", "one step"), cc("I", "C", "1 3 5"), cc("♯IV", "F♯", "#4 #6 #1", "across"))),
            say("Read anticlockwise, it's a chain of falling fifths, the strongest way for chord roots to move: E to A to D to G " +
                "to C, each chord the V of the next. That's why so many progressions trace part of the circle. The inner ring " +
                "below shows each key's relative minor. Tap a key on the circle to hear its scale; it also sets TRY's key.",
                chart("ANTICLOCKWISE · FALLING FIFTHS", cc("", "E7", "3 #5 7 2"), cc("", "A7", "6 #1 3 5"), cc("", "D7", "2 #4 6 1"), cc("", "G7", "5 7 2 4"), cc("", "C", "1 3 5"))),
        ),
        inShort = listOf(
            "Clockwise: up a fifth, one more sharp. Anticlockwise: one more flat.",
            "Neighbours share six notes and sound close; opposite keys sound far.",
            "Chord roots love to fall round the circle: each chord the V of the next.",
        ),
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
        "How far apart two keys sound depends on how many notes they share, and the circle of fifths measures exactly " +
            "that. This page puts numbers on \"close\" and \"distant\".",
        steps = listOf(
            say("A key's closest relatives are its two neighbours on the circle plus the relative minors of all three. For C " +
                "major: G major, F major, A minor, E minor and D minor. They're exactly the keys of the other major and minor " +
                "chords in C (V, IV, vi, iii, ii), at most one sharp or flat away. Moving between them sounds smooth.",
                chart("C'S CLOSE RELATIVES, AS CHORDS OF C", cc("V", "G", "5 7 2"), cc("IV", "F", "4 6 1"), cc("vi", "Am", "6 1 3"), cc("iii", "Em", "3 5 7"), cc("ii", "Dm", "2 4 6"))),
            say("Every step further round the circle loses one shared note. Five or six steps away, two keys share only two " +
                "notes and a change between them sounds like a jolt. Tap a row to hear C major and then that key's scale.",
                table("HOW MANY NOTES TWO KEYS SHARE", listOf("STEPS", "FROM C", "SHARED"), (0..6).map { s ->
                    listOf("$s", shown(p(CIRCLE_MAJOR[s])) + (if (s in 1..5) " · " + shown(p(CIRCLE_MAJOR[12 - s])) else ""), "${shared(s)} of 7")
                }, (0..6).map { s -> Play(sm("major").map { listOf(it) } + sm("major", CIRCLE_MAJOR[s], 60).map { listOf(it) }, 0.25) })),
            say("Songs often lift the last chorus a half step or a whole step. On the circle those are big jumps: up a whole " +
                "step is two steps clockwise (C to D, two notes changed); up a half step is five steps anticlockwise (C to D♭, " +
                "five notes changed). That's why the half-step lift sounds so dramatic.",
                null),
        ),
        inShort = listOf(
            "Close keys: the two neighbours and the three relative minors.",
            "Each step round the circle loses one shared note.",
            "A half-step key change is five steps round the circle: a big jolt.",
        ),
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
        "Natural minor is the plainest minor scale, and there are two useful ways to find it: as the major scale started " +
            "from its 6th note, or as the major scale with three notes lowered. Both give the same scale; they answer " +
            "different questions.",
        steps = listOf(
            say("The relative way: play C major's white keys starting and ending on A. Same seven notes, but with A as home the " +
                "scale sounds minor. A minor is C major's relative minor.",
                scaleNames("A NATURAL MINOR · C MAJOR'S NOTES FROM A", "aeolian", "A")),
            say("The parallel way: keep the same tonic and lower three degrees of the major scale. C major's E, A and B become E♭, " +
                "A♭ and B♭. Compare the two rows: only those three columns move.",
                CourseSee.Grid("C MAJOR, THEN C MINOR", KEY_COLUMNS, listOf(stepRow("major", "major"), stepRow("minor", "aeolian")))),
            say("Its step pattern is W H W W H W W: the half steps fall between 2 and ♭3 and between 5 and ♭6. The lowered 3rd " +
                "is what makes a scale minor at all; the 6th and 7th are where the three kinds of minor differ (next pages). In " +
                "mode names, natural minor is the Aeolian mode.",
                table("C MAJOR AND C MINOR", listOf("", "FORMULA", "ON C"), listOf("major", "aeolian").map { listOf(name(it), formula(it), notesOn(it, "C")) },
                    listOf(scalePlay("major"), scalePlay("aeolian")))),
        ),
        inShort = listOf(
            "Natural minor = 1 2 ♭3 4 5 ♭6 ♭7, steps W H W W H W W.",
            "Relative: the major scale from its 6th (A minor = C major's notes).",
            "Parallel: the major scale with ♭3, ♭6, ♭7 (C minor has three flats).",
        ),
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
        "Harmonic minor exists to fix one problem in natural minor: its chord on the 5th degree doesn't pull home. Raising a " +
            "single note solves it, and creates the scale's famous exotic leap as a side effect.",
        steps = listOf(
            say("In A natural minor the 7th note is G, a whole step under A. So the chord on the 5th degree is E minor (E G B), " +
                "and nothing in it leans into A. Listen: Em to Am is a soft, sideways move.",
                chart("Em → Am · NO LEADING TONE", cc("v", "Em", "5 b7 2", key = "A"), cc("i", "Am", "1 b3 5", key = "A"))),
            say("Raise the 7th a half step, G to G♯. Now the 5th-degree chord is E major (E G♯ B), and its G♯ sits a half step " +
                "under A, pulling hard. That's the sound of a classical minor cadence.",
                chart("E → Am · G♯ LEADS HOME", cc("V", "E", "5 7 2", key = "A"), cc("i", "Am", "1 b3 5", key = "A"))),
            say("The scale with that raised 7th is harmonic minor: 1 2 ♭3 4 5 ♭6 7, named for the harmony it makes. Look at the " +
                "gap between F and G♯: three half steps, an augmented second. That leap gives the scale its exotic edge. " +
                "Classical melodies tend to step around it; metal and flamenco-flavoured music lean right into it.",
                scaleNames("A HARMONIC MINOR", "harmonic-minor", "A")),
            say("Compare it with natural minor step by step: the only change is the 7th, which makes the last two steps W+H and H " +
                "instead of W and W.",
                CourseSee.Grid("NATURAL, THEN HARMONIC MINOR ON C", KEY_COLUMNS, listOf(stepRow("natural", "aeolian"), stepRow("harmonic", "harmonic-minor")))),
        ),
        inShort = listOf(
            "Harmonic minor = natural minor with a raised 7th: 1 2 ♭3 4 5 ♭6 7.",
            "It turns v into V, so the minor key gets a strong cadence.",
            "The ♭6 to 7 leap (an augmented second) is its signature sound.",
        ),
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
        "Melodic minor smooths out harmonic minor's awkward leap for singers and melody writers, and in jazz it became a " +
            "scale family of its own.",
        steps = listOf(
            say("Raise the 6th as well as the 7th and the augmented second disappears: 1 2 ♭3 4 5 6 7. Now every step is a whole " +
                "or half step, and the top of the scale climbs smoothly into the tonic.",
                scaleNames("A MELODIC MINOR · F♯ AND G♯", "melodic-minor", "A")),
            say("Notice that it's only one note away from the major scale: the ♭3. A melodic minor is A major with C instead of C♯. " +
                "Compare the three minor scales on C, step by step.",
                CourseSee.Grid("THE THREE MINORS ON C", KEY_COLUMNS, listOf(stepRow("natural", "aeolian"), stepRow("harmonic", "harmonic-minor"), stepRow("melodic", "melodic-minor"), stepRow("major", "major")))),
            say("In the classical tradition the raised form is the way up only. Coming down, there's no leading tone to satisfy, so " +
                "the melody returns to natural minor (♭7, ♭6). Jazz uses the raised form in both directions, calls it jazz minor, " +
                "and builds seven modes on it (see \"Modes of melodic minor\").",
                keys("CLASSICAL · UP MELODIC, DOWN NATURAL", "A3 B3 C4 D4 E4 F#4 G#4 A4", listOf("1", "2", "♭3", "4", "5", "6", "7", "1"), faint = "F4 G4",
                    play = Play((mids("A3 B3 C4 D4 E4 F#4 G#4 A4") + mids("G4 F4 E4 D4 C4 B3 A3")).map { listOf(it) }, 0.3))),
        ),
        inShort = listOf(
            "Melodic minor = 1 2 ♭3 4 5 6 7: major with a ♭3.",
            "Classical: raised going up, natural coming down. Jazz: raised both ways.",
        ),
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
        "The pentatonic scale is five notes, and it's the most forgiving scale there is: play its notes in almost any order " +
            "over its key and nothing clashes. This page shows why, by building it from the major scale.",
        steps = listOf(
            say("Take C major and remove two notes: the 4th (F) and the 7th (B). Those are the two notes that sit a half step " +
                "from a neighbour (F above E, B below C). What's left, C D E G A, is the major pentatonic, 1 2 3 5 6. The " +
                "removed notes are outlined.",
                scaleKeys("C MAJOR PENTATONIC · F AND B REMOVED", "major-pentatonic", labels = listOf("C", "D", "E", "G", "A", "C"), faint = listOf(65, 71))),
            say("With F and B gone, there are no half steps left and no tritone (F–B was the key's only one). That's why nothing " +
                "in it clashes hard against the key's chords, and why it's the first soloing scale most guitarists learn.",
                CourseSee.Grid("MAJOR, THEN MAJOR PENTATONIC", KEY_COLUMNS, listOf(stepRow("major", "major"), stepRow("pentatonic", "major-pentatonic")))),
            say("Start the same five notes from A and you have A minor pentatonic, 1 ♭3 4 5 ♭7: the relative minor again. Same " +
                "notes, darker home. This is the scale behind most rock and blues guitar solos.",
                scaleNames("A MINOR PENTATONIC · THE SAME FIVE NOTES FROM A", "minor-pentatonic", "A")),
            say("The five black keys on a piano are a pentatonic scale too: G♭ major pentatonic, or E♭ minor pentatonic. Play " +
                "only black keys and everything sounds consonant.",
                keysLine("THE BLACK KEYS", "Gb4 Ab4 Bb4 Db5 Eb5", listOf("G♭", "A♭", "B♭", "D♭", "E♭"), step = 0.32)),
            say("On the guitar neck the scale falls into five overlapping box shapes, the first starting on the root on the low " +
                "E string. TRY below lights every note of the scale on your neck at once, so you can see the boxes link up.",
                null),
        ),
        inShort = listOf(
            "Major pentatonic = major scale without 4 and 7: 1 2 3 5 6.",
            "Minor pentatonic = the same notes from the 6th: 1 ♭3 4 5 ♭7.",
            "No half steps, no tritone: almost anything sounds fine over the key.",
        ),
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
        "The blues scale is the minor pentatonic with one extra note squeezed in. That note, and the way blues players bend " +
            "the notes around it, is most of what makes the blues sound like the blues.",
        steps = listOf(
            say("Start with A minor pentatonic, A C D E G, and add E♭ between D and E. That's the blues scale: 1 ♭3 4 ♭5 5 ♭7. " +
                "The ♭5 is a passing note: it's usually slid through on the way between 4 and 5, rarely held.",
                scaleKeys("A BLUES SCALE · E♭ ADDED", "minor-blues", "A", listOf("A", "C", "D", "E♭", "E", "G", "A"))),
            say("The blue notes are ♭3, ♭5 and ♭7. Singers and guitarists often place them somewhere between flat and natural, " +
                "bending up toward the major version. That's why a minor 3rd over a major chord (C over A7) is part of the " +
                "sound rather than a mistake. Listen: an A7 chord, with C sliding up to C♯ above it.",
                keys("♭3 TO 3 OVER A7", "C5 C#5", listOf("♭3", "3"), faint = "A3 E4 G4 C#4", play = Play(listOf(mids("A2 E3 G3 C#4 C5"), mids("A2 E3 G3 C#4 C#5")), 0.7))),
            say("The major blues scale works the same way from the major side: major pentatonic plus ♭3, 1 2 ♭3 3 5 6, the ♭3 " +
                "sliding up into the 3. It's the sweeter, country-blues sound. Compare the two.",
                table("THE TWO BLUES SCALES ON A", listOf("", "FORMULA", "NOTES"), listOf("minor-blues", "major-blues").map { listOf(name(it), formula(it), notesOn(it, "A")) },
                    listOf(scalePlay("minor-blues", "A"), scalePlay("major-blues", "A")))),
        ),
        inShort = listOf(
            "Blues scale = minor pentatonic + ♭5: 1 ♭3 4 ♭5 5 ♭7.",
            "Blue notes (♭3, ♭5, ♭7) are often bent toward natural.",
            "Major blues = major pentatonic + ♭3.",
        ),
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
        "A mode is a scale heard from a different home. The seven modes of the major scale use exactly the same notes, but " +
            "each one makes a different note the centre, and that changes the whole mood. There are two ways to think about " +
            "them, and you need both.",
        steps = listOf(
            say("Play C major's white keys, but from D to D, with D as home. The notes haven't changed, yet it no longer sounds " +
                "like C major: it sounds minor, with a lifted, slightly bright 6th. That's D Dorian.",
                scaleKeys("C MAJOR'S NOTES FROM D · D DORIAN", "dorian", "D", listOf("D", "E", "F", "G", "A", "B", "C", "D"))),
            say("Do the same from every white key and you get seven modes, one per degree: Ionian (from C, the major scale), " +
                "Dorian (D), Phrygian (E), Lydian (F), Mixolydian (G), Aeolian (A, natural minor) and Locrian (B). Each row " +
                "below is the same seven keys starting somewhere else. Tap one to hear it.",
                CourseSee.Grid("THE SEVEN MODES · ALL WHITE KEYS", cols("C D E F G A B"), listOf(
                    "Ionian" to "C4 D4 E4 F4 G4 A4 B4 C5", "Dorian" to "D4 E4 F4 G4 A4 B4 C5 D5", "Phrygian" to "E4 F4 G4 A4 B4 C5 D5 E5",
                    "Lydian" to "F4 G4 A4 B4 C5 D5 E5 F5", "Mixolydian" to "G3 A3 B3 C4 D4 E4 F4 G4", "Aeolian" to "A3 B3 C4 D4 E4 F4 G4 A4", "Locrian" to "B3 C4 D4 E4 F4 G4 A4 B4",
                ).map { (n, s) -> row(n, cols("C D E F G A B"), (0..6).toSet(), Play(mids(s).map { listOf(it) }, 0.32)) })),
            say("That's the relative view: each mode is the parent scale from another note. It's how you find a mode's notes. " +
                "But it hides how modes actually sound, because the home note keeps moving. So put them all on the same tonic " +
                "instead. On C, Dorian is C D E♭ F G A B♭: C minor with a natural 6th. That's the parallel view, and it's how " +
                "your ear hears them.",
                table("THE MODES ON C", listOf("", "FORMULA", "ON C", "NOTES OF"), MAJOR_MODES.map { id ->
                    val page = ScalePages.page(Scales.byId(id), p("C"))
                    listOf(name(id), formula(id), page.notes.joinToString(" ") { shown(it) }, page.parent?.let { (_, pr) -> shown(pr) + " major" } ?: "C major")
                }, MAJOR_MODES.map { scalePlay(it) })),
            say("A mode only sounds like itself when the home note is established, usually by a drone or a bass note under it. " +
                "Without that, D Dorian just sounds like C major starting on D. That's why the HEAR buttons below and the Ear's " +
                "mode drills play modes over a held low note.",
                null),
        ),
        inShort = listOf(
            "Seven modes = the major scale started from each of its degrees.",
            "Relative view (C major from D) finds the notes; parallel view (C Dorian vs C major) is how they sound.",
            "A mode needs its home note held or stressed to sound like itself.",
        ),
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
        "Put all seven modes on the same tonic and they line up from brightest to darkest, with exactly one note lowered at " +
            "each step. That ordering is the quickest way to remember them and to hear what makes each one different.",
        steps = listOf(
            say("Read down the grid: each mode is the one above with one note lowered a half step. Lydian has the most raised " +
                "notes (♯4) and is the brightest; then Ionian, Mixolydian (♭7), Dorian (♭3), Aeolian (♭6), Phrygian (♭2) and " +
                "Locrian (♭5), the darkest. Tap the rows top to bottom and listen to the light go out.",
                CourseSee.Grid("BRIGHT TO DARK ON C", KEY_COLUMNS, BRIGHT_TO_DARK.map { stepRow(name(it), it) })),
            say("Each mode's colour comes mostly from its characteristic note, the one that sets it apart from plain major or " +
                "minor. The table lists them with the note that changed from the brighter neighbour.",
                table("THE ONE NOTE THAT CHANGES", listOf("", "FORMULA", "CHANGED"), BRIGHT_TO_DARK.mapIndexed { i, id ->
                    val changed = if (i == 0) "" else Scales.byId(id).degrees.filter { it !in Scales.byId(BRIGHT_TO_DARK[i - 1]).degrees }.joinToString(" ") { accidentals(it.toString()) }
                    listOf(name(id), formula(id), changed)
                }, BRIGHT_TO_DARK.map { scalePlay(it) })),
            say("Hear each characteristic note against the tonic chord. Lydian: the ♯4 floating over a major chord. Mixolydian: " +
                "the ♭7 over major, bluesy. Dorian: the natural 6 over minor, hopeful. Phrygian: the ♭2 over minor, dark and " +
                "Spanish.",
                keys("CHARACTERISTIC NOTES OVER C", "F#4 Bb4 A4 Db5", listOf("♯4", "♭7", "6", "♭2"),
                    play = Play(listOf(mids("C3 E4 G4 F#5"), mids("C3 E4 G4 Bb4"), mids("C3 Eb4 G4 A4"), mids("C3 Eb4 G4 Db5")), 1.6))),
            say("Locrian's ♭5 makes its own tonic chord diminished (C E♭ G♭), which can't sound like a stable home. That's why " +
                "Locrian is rarely used as a key, though its colour turns up over half-diminished chords.",
                null),
        ),
        inShort = listOf(
            "Bright to dark: Lydian, Ionian, Mixolydian, Dorian, Aeolian, Phrygian, Locrian.",
            "Each step lowers exactly one note.",
            "Characteristic notes: ♯4 Lydian, ♭7 Mixolydian, 6 Dorian, ♭2 Phrygian, ♭5 Locrian.",
        ),
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
        "Melodic minor (in its jazz form, the same going up and down) is a parent scale just like major, with seven modes " +
            "of its own. Several of them are the standard sounds for particular jazz chords, so the family is worth knowing " +
            "by ear.",
        steps = listOf(
            say("Start each degree of C melodic minor (C D E♭ F G A B) and you get its seven modes. Each row below is one mode " +
                "put back on C, so you can compare their shapes. Tap a row.",
                CourseSee.Grid("THE MODES OF MELODIC MINOR, ON C", KEY_COLUMNS, MELODIC_MODES.map { stepRow(name(it), it) })),
            say("The 4th mode, Lydian dominant, is 1 2 3 ♯4 5 6 ♭7: Mixolydian with a raised 4th. It's the sound of a 7♯11 chord, " +
                "bright and a little exotic. Hear it against plain Mixolydian.",
                CourseSee.Grid("MIXOLYDIAN, THEN LYDIAN DOMINANT", KEY_COLUMNS, listOf(stepRow("Mixolydian", "mixolydian"), stepRow("Lydian dom.", "lydian-dominant")))),
            say("The 7th mode, the altered scale, holds every tension a dominant can carry. G altered is A♭ melodic minor from G: " +
                "G A♭ B♭ C♭ D♭ E♭ F. Read against G7, A♭ is the ♭9, B♭ the ♯9, C♭ (the key of B) the 3rd, D♭ the ♯11, E♭ the ♭13, " +
                "F the ♭7. Every note except the root is a guide tone or an altered tension.",
                keys("G ALTERED · AGAINST G7", "G3 Ab3 Bb3 Cb4 Db4 Eb4 F4", listOf("1", "♭9", "♯9", "3", "♯11", "♭13", "♭7"),
                    play = Play(mids("G3 Ab3 Bb3 Cb4 Db4 Eb4 F4 G4").map { listOf(it) } + listOf(mids("G2 B3 F4 Ab4 Eb5")), 0.35))),
            say("The 6th mode, Locrian ♯2, fits a half-diminished chord better than plain Locrian, because its natural 2 avoids " +
                "the clash of the ♭2. The table gives every mode's formula and notes on C; some modes go by two names.",
                table("MODES OF MELODIC MINOR ON C", listOf("", "", "FORMULA", "ON C"), modeTable(MELODIC_MODES), MELODIC_MODES.map { scalePlay(it) })),
        ),
        inShort = listOf(
            "Melodic minor's modes are a second parent family alongside major's.",
            "Lydian dominant (4th mode) = 7♯11; altered (7th) = every dominant tension; Locrian ♯2 (6th) = ø7.",
        ),
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
        "Harmonic minor has seven modes too, and every one of them carries its three-half-step leap somewhere. Where the " +
            "leap falls relative to the home note decides the mode's character.",
        steps = listOf(
            say("Each row is a mode of harmonic minor on C. Find the W+H cell in each: that's the augmented second, and it moves " +
                "to a different place in every mode.",
                CourseSee.Grid("THE MODES OF HARMONIC MINOR, ON C", KEY_COLUMNS, HARMONIC_MODES.map { stepRow(name(it), it) })),
            say("The best known is the 5th mode, Phrygian dominant: 1 ♭2 3 4 5 ♭6 ♭7. From A harmonic minor it's E F G♯ A B C D. " +
                "The leap sits between ♭2 and 3, right at the bottom, which gives the dark, Spanish sound of flamenco, the " +
                "freygish of klezmer, and a lot of metal riffs.",
                scaleNames("E PHRYGIAN DOMINANT · A HARMONIC MINOR FROM E", "phrygian-dominant", "E")),
            say("Dorian ♯4 and Lydian ♯2 turn up in Eastern European folk music and film scores. Harmonic major, the major scale " +
                "with a ♭6 (1 2 3 4 5 ♭6 7), is a related parent scale; its modes have no settled names and sources disagree on " +
                "them. The table has every harmonic-minor mode on C.",
                table("MODES OF HARMONIC MINOR ON C", listOf("", "", "FORMULA", "ON C"), modeTable(HARMONIC_MODES), HARMONIC_MODES.map { scalePlay(it) })),
        ),
        inShort = listOf(
            "Every harmonic-minor mode has the augmented second somewhere.",
            "Phrygian dominant (5th mode) = 1 ♭2 3 4 5 ♭6 ♭7: flamenco, klezmer, metal.",
            "Harmonic major = major with ♭6.",
        ),
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
        "Most scales have a mix of step sizes that points to one home note. Symmetric scales repeat the same small pattern " +
            "all the way up, so no note is more central than another. That's what gives them their floating, unresolved sound.",
        steps = listOf(
            say("The whole-tone scale is nothing but whole steps: six notes, C D E F♯ G♯ A♯. With every step the same size, it " +
                "sounds the same from any of its notes; there's no pull toward home. Only two different whole-tone scales exist " +
                "(on C and on D♭); every other start repeats one of them.",
                CourseSee.Grid("WHOLE-TONE · ALL W", KEY_COLUMNS, listOf(stepRow("whole-tone", "whole-tone")))),
            say("The diminished (octatonic) scales alternate half and whole steps: eight notes. Half-whole (starting with the half " +
                "step) fits a dominant 7♭9 chord; whole-half fits a diminished seventh chord. Only three different ones exist.",
                CourseSee.Grid("THE TWO DIMINISHED SCALES", KEY_COLUMNS, listOf(stepRow("half-whole", "half-whole"), stepRow("whole-half", "whole-half")))),
            say("The table lists the symmetric scales on C with their steps, and how many genuinely different versions exist " +
                "across the twelve keys. The fewer there are, the more symmetric the scale.",
                table("SYMMETRIC SCALES ON C", listOf("", "STEPS", "NOTES", "DIFFERENT"), listOf("whole-tone", "half-whole", "whole-half", "augmented").map { id ->
                    val page = ScalePages.page(Scales.byId(id), p("C"))
                    listOf(name(id), page.steps.joinToString(" ") { ScalePages.stepName(it) }, page.notes.joinToString(" ") { shown(it) }, "${transpositions(Scales.byId(id).semitones.toSet())}")
                }, listOf("whole-tone", "half-whole", "whole-half", "augmented").map { scalePlay(it) })),
        ),
        inShort = listOf(
            "Whole-tone: six whole steps, two versions.",
            "Diminished: half and whole steps alternating, three versions; half-whole over 7♭9, whole-half over °7.",
            "Symmetric scales have no single home note.",
        ),
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
        "The composer Olivier Messiaen collected seven scales that are symmetric in the way the previous page describes. He " +
            "called them modes of limited transposition and built much of his harmony on them.",
        steps = listOf(
            say("Limited transposition means that moving the scale up by half steps brings you back to the same notes before " +
                "you've tried all twelve. The whole-tone scale is mode 1: move it up two half steps and it's the same scale " +
                "again, so it has only two versions. Each row lights a mode's notes on C.",
                CourseSee.Grid("MESSIAEN'S SEVEN MODES ON C", (0..11).map { shown(PitchClass(it).defaultSpelling()) }, MESSIAEN.mapIndexed { i, set ->
                    row("mode ${i + 1}", (0..11).map { shown(PitchClass(it).defaultSpelling()) }, set, Play((set.sorted().map { 60 + it } + 72).map { listOf(it) }, 0.3))
                })),
            say("Mode 2 is the half-whole diminished scale (three versions). Mode 3 is nine notes, a whole step and two half steps " +
                "repeated three times (four versions). Modes 4 to 7 have six versions each. Messiaen valued them because their " +
                "harmony floats without pulling to a key. They have no single correct spelling, so notes are named by piano key.",
                table("THE SEVEN MODES ON C", listOf("MODE", "NOTES", "COUNT", "DIFFERENT"), MESSIAEN.mapIndexed { i, set ->
                    listOf("${i + 1}", set.sorted().joinToString(" ") { shown(PitchClass(it).defaultSpelling()) }, "${set.size}", "${transpositions(set)}")
                }, MESSIAEN.map { set -> Play((set.sorted().map { 60 + it } + 72).map { listOf(it) }, 0.3) })),
        ),
        inShort = listOf(
            "Modes of limited transposition repeat before all twelve transpositions are used.",
            "Mode 1 = whole-tone (2 versions), mode 2 = octatonic (3), mode 3 = nine notes (4), modes 4–7 (6 each).",
        ),
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
        steps = listOf(
            say("Here they are as twelve-tone scales on C. Tap a row to hear each one; listen for half steps placed where a major " +
                "or minor scale wouldn't put them, which is where most of their colour comes from.",
                table("ON C", listOf("", "FORMULA", "NOTES"), WORLD.map { listOf(name(it), formula(it), notesOn(it, "C")) }, WORLD.map { scalePlay(it) })),
            say("Compare the Japanese in scale with the major pentatonic it superficially resembles: both have five notes, but in " +
                "puts half steps where the pentatonic has none.",
                CourseSee.Grid("MAJOR PENTATONIC, THEN IN", KEY_COLUMNS, listOf(stepRow("pentatonic", "major-pentatonic"), stepRow("in", "in")))),
        ),
        inShort = listOf(
            "These are equal-tempered approximations; the traditions themselves tune and phrase differently.",
            "Use them as colours, and read the names as labels.",
        ),
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
