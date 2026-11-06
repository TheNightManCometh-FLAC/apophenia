package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.catalog.Chords
import com.betterappmafia.apophenia.core.learn.CourseIntervals.shown
import com.betterappmafia.apophenia.core.pitch.PitchName

/*
 * Course · Chords: triads and their four qualities, inversions, seventh chords, sus and add, sixths, extensions and
 * what's left out, altered dominants, slash chords and polychords, quartal and cluster voicings, upper structures.
 * Each page is walked through in steps with the chord drawn on the piano or the staff, its ▶ playing what's drawn.
 * Text written for Apophenia; every spelling comes from ChordPages.
 */
internal object CourseChords : CourseKit() {
    private fun p(s: String) = PitchName.parse(s)
    private fun semis(id: String) = Chords.byId(id).semitones
    /** [id] as a block chord from [r], or its notes one by one when [arp]. */
    private fun chord(r: Int, id: String, at: Double = 0.0, len: Double = 1.8) = RefSound.block(semis(id).map { r + it }, at = at, length = len)
    private fun hearChord(r: Int, id: String) = piano(RefSound.chord(semis(id).map { r + it }))
    internal fun notes(id: String, root: String) = ChordPages.page(Chords.byId(id), p(root)).notes.joinToString(" ") { shown(it) }
    internal fun symbol(id: String, root: String) = accidentals(ChordPages.page(Chords.byId(id), p(root)).symbols.first()).replace("#", "♯").let { s ->
        // The root's own accidental: C# → C♯, Bb → B♭ (accidentals() only converts before a digit).
        val r = p(root); shown(r) + s.drop(r.toString().length)
    }
    private fun formula(id: String) = Chords.byId(id).degrees.joinToString(" ") { accidentals(it.toString()) }
    private fun row(id: String, root: String = "C") = listOf(symbol(id, root), formula(id), notes(id, root))
    private val ROW_HEADER = listOf("", "FORMULA", "NOTES")
    private fun compare(r: Int, vararg ids: String) = piano(ids.flatMapIndexed { i, id -> chord(r, id, at = i * 2.2) })

    /** [id] on [root] as MIDI, the root between C4 and B4 (or from [low]). */
    private fun cm(id: String, root: String = "C", low: Int = 60) = RefSound.midis(p(root).pitchClass, semis(id), low)
    /** The chord lit on the piano, each key named, ▶ playing it broken then together. */
    private fun chordKeys(caption: String, id: String, root: String = "C", labels: List<String>? = null): CourseSee.Keys {
        val names = ChordPages.page(Chords.byId(id), p(root)).notes.map { shown(it) }
        val ms = cm(id, root)
        return CourseSee.Keys(caption, ms.mapIndexed { i, m -> Key(m, labels?.getOrNull(i) ?: names.getOrElse(i) { "" }) }, Play(listOf(ms), 1.6, spread = true))
    }
    private fun rowPlay(id: String, root: String = "C") = Play(listOf(cm(id, root)), 1.6, spread = true)
    private fun chordTable(caption: String, ids: List<Pair<String, String>>) = table(caption, ROW_HEADER, ids.map { (id, r) -> row(id, r) }, ids.map { (id, r) -> rowPlay(id, r) })

    // ------------------------------------------------------------------------------------------------- triads

    private val build = CoursePage(
        "triad-build", "Building a triad", 1,
        "A triad is the basic chord: three notes stacked in thirds. Every other chord on the following pages is a triad with " +
            "something changed or added, so it's worth seeing exactly how one is put together.",
        steps = listOf(
            say("Take the C major scale and play every other note, starting on C: C, skip D, E, skip F, G. Those three notes are " +
                "the C major triad. The bottom note is the root (it names the chord), the middle one is the 3rd, the top one " +
                "is the 5th, named for how far each is above the root.",
                keys("EVERY OTHER NOTE FROM C", "C4 E4 G4", listOf("R", "3", "5"), faint = "D4 F4 A4 B4")),
            say("Each gap is a third, but thirds come in two sizes. Count the keys, black ones included: C to E is 4 half steps, " +
                "a major third. E to G is 3, a minor third. Major third on the bottom, minor on top: that's what makes a triad " +
                "major.",
                table("THE TWO THIRDS OF C MAJOR", listOf("", "FROM", "HALF STEPS", "SIZE"), listOf(listOf("lower", "C to E", "4", "major 3rd"), listOf("upper", "E to G", "3", "minor 3rd"), listOf("outer", "C to G", "7", "perfect 5th")),
                    listOf(Play(listOf(mids("C4 E4")), 1.4, true), Play(listOf(mids("E4 G4")), 1.4, true), Play(listOf(mids("C4 G4")), 1.4, true)))),
            say("Lower the middle note by one half step, E to E♭, and the order flips: minor third on the bottom, major on top. " +
                "That's C minor. The outer notes, C and G, haven't moved, so it's still a perfect fifth from root to top. One " +
                "note, a half step, is the whole difference between major and minor. Listen for it.",
                keys("C MINOR · THE 3RD LOWERED", "C4 Eb4 G4", listOf("R", "♭3", "5"), play = Play(listOf(mids("C4 E4 G4"), mids("C4 Eb4 G4")), 1.6))),
            say("Take the 3rd out completely and you have a power chord, C5: just root and fifth. With no 3rd it's neither major " +
                "nor minor, which is why it works under distorted guitar, where a 3rd turns muddy. And a guitar's open and barre " +
                "chords are these same three notes, doubled in different octaves across the strings.",
                chordTable("TRIADS ON C", listOf("maj" to "C", "m" to "C", "5" to "C"))),
        ),
        inShort = listOf(
            "A triad is root, 3rd and 5th: every other note of a scale.",
            "Major: major 3rd (4 half steps) on the bottom. Minor: minor 3rd (3) on the bottom.",
            "Major and minor differ by one note, the 3rd, a half step apart.",
            "A power chord (5) is root and fifth only.",
        ),
        hear = listOf(
            sound("MAJOR · MINOR") { r -> compare(r, "maj", "m") },
            sound("THE NOTES ONE BY ONE") { r -> piano(seq(listOf(r, r + 4, r + 7), step = 0.6, len = 1.6) + chord(r, "maj", at = 2.0)) },
            sound("POWER CHORD") { r -> piano(chord(r - 12, "5") + chord(r, "5", at = 0.0)) },
        ),
        tryIt = CourseTry("A MAJOR TRIAD · SHAPES AND EVERY NOTE ON THE NECK", RefKind.CHORD, "maj", "C"),
        check = CourseCheck("Major or minor?", listOf(
            option("Major", "Major: the major third is at the bottom.") { r, _ -> hearChord(r, "maj") },
            option("Minor", "Minor: the minor third is at the bottom.") { r, _ -> hearChord(r, "m") },
        )),
        related = listOf(ref(RefKind.CHORD, "maj", "C major"), ref(RefKind.CHORD, "m", "C minor"), ear("ch-triads", "major · minor · dim · aug")),
        yours = RefKind.CHORD to "maj",
    )

    private val four = CoursePage(
        "triad-four", "The four triads", 1,
        "With two sizes of third and two thirds to stack, there are exactly four combinations. You've met major and minor; " +
            "the other two are rarer and much more tense, and knowing how they're built explains why.",
        steps = listOf(
            say("Here are all four on C. Read each row's formula: major is 1 3 5, minor 1 ♭3 5. Diminished lowers the 5th as " +
                "well: two minor thirds, C E♭ G♭. Augmented raises the 5th instead: two major thirds, C E G♯. Tap each row.",
                chordTable("THE FOUR ON C", listOf("maj" to "C", "m" to "C", "dim" to "C", "aug" to "C"))),
            say("Diminished: two minor thirds squeeze the top note down, so the outer interval is a tritone (6 half steps) instead " +
                "of a perfect fifth. That's what makes it tense and unstable. In a major key it lives on the 7th degree (B D F " +
                "in C), right next to home, which is why it pulls so hard toward the tonic.",
                chordKeys("C DIMINISHED · C E♭ G♭", "dim", labels = listOf("R", "♭3", "♭5"))),
            say("Augmented: two major thirds push the top note up a half step. Its notes split the octave into three equal parts " +
                "(4 + 4 + 4 half steps), so C augmented, E augmented and G♯ augmented use the same three keys. With no " +
                "obvious root it sounds suspended in the air, a sound used for dream sequences and for passing between chords.",
                chordKeys("C AUGMENTED · C E G♯", "aug", labels = listOf("R", "3", "♯5"))),
            say("Spelling follows the letters, not the keys: every other letter from the root. G♯ major must be G♯, some kind of " +
                "B, some kind of D, and to get the right sizes that's G♯ B♯ D♯. B♯ is the key you'd normally call C, but in " +
                "this chord it has to be a B. This is why chord spellings sometimes look odd.",
                chordTable("SPELLING BY LETTERS", listOf("maj" to "G#", "aug" to "C", "dim" to "B"))),
        ),
        inShort = listOf(
            "Major (M3 + m3), minor (m3 + M3), diminished (m3 + m3), augmented (M3 + M3).",
            "Diminished has a tritone between its outer notes; augmented splits the octave in three.",
            "Spell chords by letters, every other letter from the root.",
        ),
        hear = listOf(sound("MAJOR · MINOR · DIMINISHED · AUGMENTED") { r -> compare(r, "maj", "m", "dim", "aug") }),
        tryIt = CourseTry("A DIMINISHED TRIAD FROM ANY ROOT", RefKind.CHORD, "dim", "B"),
        check = CourseCheck("Which triad?", listOf("maj" to "Major", "m" to "Minor", "dim" to "Diminished", "aug" to "Augmented").map { (id, n) ->
            option(n, "$n: ${formula(id)}.") { r, _ -> hearChord(r, id) }
        }),
        related = listOf(ref(RefKind.CHORD, "dim", "Diminished"), ref(RefKind.CHORD, "aug", "Augmented"), ear("ch-triads", "major · minor · dim · aug")),
    )

    // ------------------------------------------------------------------------------------------------- inversions

    private val inversions = CoursePage(
        "inversions", "Inversions", 2,
        "The notes of a chord can be stacked in any order. What changes the sound most is which note is at the bottom, and " +
            "that bottom note is what an inversion names.",
        steps = listOf(
            say("Root position: the root at the bottom, C under E and G. This is the most grounded and stable way to play the " +
                "chord.",
                staff("ROOT POSITION · C IN THE BASS", listOf("C4 E4 G4"), above = listOf("C"), below = listOf("root"), step = 1.6)),
            say("Move the C up an octave and E is now at the bottom: first inversion, written C/E. The same three notes, but the " +
                "chord sounds lighter, less planted. Then move E up too, and G is at the bottom: second inversion, C/G, which " +
                "sounds unsettled, as if it's on its way somewhere. On the staff you can see the same notes climbing over each " +
                "other.",
                staff("ROOT · FIRST · SECOND INVERSION", listOf("C4 E4 G4", "E4 G4 C5", "G4 C5 E5"), above = listOf("C", "C/E", "C/G"), below = listOf("root", "1st", "2nd"), step = 1.5)),
            say("Only the bass decides the inversion. The upper notes can be in any order and any octave: if E is the lowest note " +
                "it's first inversion, however the rest is voiced. Chord symbols write it as a slash chord, C/E. Classical music " +
                "uses figured bass instead, numbering the intervals above the bass: 6 for first inversion, 6/4 for second, and " +
                "for a seventh chord 6/5, 4/3 and 4/2. Tap a row to hear it with its bass.",
                table("C MAJOR AND G7, EVERY INVERSION", listOf("", "BASS", "FIGURES"), listOf("maj" to "C", "7" to "G").flatMap { (id, r) ->
                    ChordPages.page(Chords.byId(id), p(r)).inversions.map { inv -> listOf(accidentals(inv.symbol), shown(inv.bass), inv.figures) }
                }, listOf("maj" to "C", "7" to "G").flatMap { (id, r) ->
                    val ms = cm(id, r)
                    ms.indices.map { i -> Play(listOf(listOf(ms[i] - 24) + ms), 1.6) }
                })),
            say("Why bother? Because inversions let a bass line move by step instead of jumping. C, then C with B in the bass, " +
                "then Am, then Am with G in the bass: the bass walks down C B A G while the chords barely change. Listen to the " +
                "bass on its own.",
                chart("A BASS LINE BY STEP", cc("I", "C", "1 3 5"), cc("I", "C/B", "7|1 3 5", "B in bass"), cc("vi", "Am", "6 1 3"), cc("vi", "Am/G", "5|6 1 3", "G in bass"), step = 1.3)),
        ),
        inShort = listOf(
            "Root position: root in the bass. First inversion: 3rd. Second: 5th. Third (seventh chords): 7th.",
            "Only the lowest note matters; the rest can be voiced freely.",
            "Written as slash chords (C/E) or figured bass (6, 6/4).",
            "Inversions let the bass move by step.",
        ),
        hear = listOf(
            sound("ROOT · 1ST · 2ND INVERSION") { r -> piano(RefSound.block(listOf(r - 12, r + 4, r + 7, r + 12), length = 1.8) + RefSound.block(listOf(r - 8, r + 7, r + 12, r + 16), at = 2.2, length = 1.8) + RefSound.block(listOf(r - 5, r + 12, r + 16, r + 19), at = 4.4, length = 1.8)) },
            sound("A BASS LINE BY STEP: C · C/B · Am · Am/G") { r -> piano(listOf(listOf(0, 4, 7), listOf(-1, 4, 7), listOf(-3, 0, 4), listOf(-5, 0, 4)).flatMapIndexed { i, c -> RefSound.block(listOf(r - 12 + c[0]) + c.drop(1).map { r + it } + (r + 12), at = i * 1.6, length = 1.5) }) },
        ),
        tryIt = CourseTry("A CHORD'S SHAPES · EACH PUTS A DIFFERENT NOTE IN THE BASS", RefKind.CHORD, "maj", "C"),
        check = CourseCheck("What's in the bass: the root, the third or the fifth?", listOf(0 to "Root", 4 to "Third", 7 to "Fifth").map { (b, n) ->
            option(n, if (b == 0) "Root position." else if (b == 4) "First inversion: the third in the bass." else "Second inversion: the fifth in the bass.") { r, _ ->
                piano(RefSound.block(listOf(r - 12 + b, r + 4, r + 7, r + 12), length = 2.2))
            }
        }),
        related = listOf(ear("ch-inv", "triad inversions"), ear("ch-inv7", "seventh-chord inversions")),
    )

    // ------------------------------------------------------------------------------------------------- sevenths

    internal val MAIN_SEVENTHS = listOf("maj7", "7", "m7", "m7b5", "dim7")

    private val sevenths = CoursePage(
        "sevenths", "The five main seventh chords", 2,
        "Add one more third on top of a triad and you get a seventh chord: four notes, root, 3rd, 5th and 7th. There are " +
            "five that matter most, and each is just a triad you already know plus one of two or three possible sevenths.",
        steps = listOf(
            say("The 7th sits just under the octave. It comes in three sizes: a major 7th is a half step below the octave (B " +
                "over C), a minor 7th a whole step below (B♭), a diminished 7th a step and a half below (B𝄫, the key of A). " +
                "Listen to each against C.",
                keys("THREE SEVENTHS ABOVE C", "C4 B4", listOf("C", "B"), play = Play(listOf(mids("C4 B4"), mids("C4 Bb4"), mids("C4 A4")), 1.4))),
            say("Major triad plus major 7th: major seventh, Cmaj7 (C E G B). Soft, settled and a little dreamy: the 7th rubs " +
                "against the octave without needing to go anywhere.",
                chordKeys("Cmaj7 · 1 3 5 7", "maj7", labels = listOf("R", "3", "5", "7"))),
            say("Major triad plus minor 7th: dominant seventh, C7 (C E G B♭). Its 3rd and 7th (E and B♭) form a tritone, and " +
                "that tension wants to resolve: it's the V7 of a key, and the home chord of the blues.",
                chordKeys("C7 · 1 3 5 ♭7", "7", labels = listOf("R", "3", "5", "♭7"))),
            say("Minor triad plus minor 7th: minor seventh, Cm7 (C E♭ G B♭). Mellow and dark, the ii chord of jazz.",
                chordKeys("Cm7 · 1 ♭3 5 ♭7", "m7", labels = listOf("R", "♭3", "5", "♭7"))),
            say("Diminished triad plus minor 7th: half-diminished, Cø7 or Cm7♭5 (C E♭ G♭ B♭). Diminished triad plus " +
                "diminished 7th: fully diminished, C°7 (C E♭ G♭ B𝄫). The °7 is all minor thirds, so it repeats every three " +
                "half steps: C°7, E♭°7, G♭°7 and A°7 are the same four keys. Compare all five in the table.",
                chordTable("THE FIVE ON C", MAIN_SEVENTHS.map { it to "C" })),
        ),
        inShort = listOf(
            "maj7 = major triad + major 7th; 7 = major triad + minor 7th; m7 = minor triad + minor 7th.",
            "ø7 = diminished triad + minor 7th; °7 = diminished triad + diminished 7th.",
            "The dominant 7th holds a tritone (3rd and ♭7) that wants to resolve.",
        ),
        hear = listOf(sound("MAJ7 · 7 · m7 · ø7 · °7") { r -> compare(r, *MAIN_SEVENTHS.toTypedArray()) }),
        tryIt = CourseTry("A DOMINANT SEVENTH · ITS SHAPES ON YOUR NECK", RefKind.CHORD, "7", "G"),
        check = CourseCheck("Major seventh, dominant seventh, or minor seventh?", listOf("maj7" to "Maj7", "7" to "7", "m7" to "m7").map { (id, n) ->
            option(n, "${symbol(id, "C").removePrefix("C").ifEmpty { n }}: ${formula(id)}.") { r, _ -> hearChord(r, id) }
        }),
        related = listOf(ref(RefKind.CHORD, "maj7", "Cmaj7"), ref(RefKind.CHORD, "7", "C7"), ref(RefKind.CHORD, "m7", "Cm7"), ear("ch-7-basic", "maj7 · m7 · 7")),
        yours = RefKind.CHORD to "7",
    )

    internal val MORE_SEVENTHS = listOf("mMaj7", "maj7#5", "7#5", "7b5", "7sus4")

    private val moreSevenths = CoursePage(
        "sevenths-more", "More seventh chords", 3,
        "The five main seventh chords don't use up every combination of triad and seventh. The rest are rarer, but each has a " +
            "distinct sound and a job you'll recognise once you've heard it.",
        steps = listOf(
            say("Minor triad plus major 7th: minor-major seventh, Cm(maj7). Dark and suspenseful, the classic film-noir chord. " +
                "It most often appears in a line descending inside a minor chord: Cm, Cm(maj7), Cm7, Cm6, with one voice " +
                "sliding C, B, B♭, A. Follow the top note on the staff.",
                staff("THE LINE INSIDE Cm", listOf("Eb4 G4 C5", "Eb4 G4 B4", "Eb4 G4 Bb4", "Eb4 G4 A4"), above = listOf("Cm", "Cm(maj7)", "Cm7", "Cm6"), step = 1.2)),
            say("Augmented triad plus a 7th: Cmaj7♯5 (major 7th, the tonic sound of Lydian augmented) and C7♯5 (minor 7th, a " +
                "dominant with a raised 5th that leans toward the next chord's 3rd). Lowering the 5th instead gives C7♭5, whose " +
                "notes repeat at the tritone: C7♭5 and G♭7♭5 are the same keys.",
                chordTable("AUGMENTED AND ALTERED FIFTHS", listOf("maj7#5" to "C", "7#5" to "C", "7b5" to "C"))),
            say("C7sus4 replaces the 3rd with the 4th: C F G B♭. Without the 3rd there's no tritone, so it's a dominant that " +
                "doesn't commit. Modal jazz vamps on it, and it often resolves to the plain C7 before moving on.",
                chordKeys("C7sus4 · 1 4 5 ♭7", "7sus4", labels = listOf("R", "4", "5", "♭7"))),
        ),
        inShort = listOf(
            "m(maj7): minor triad + major 7th, the descending-line chord.",
            "maj7♯5 and 7♯5: augmented triads with sevenths; 7♭5 repeats at the tritone.",
            "7sus4: a dominant without its 3rd.",
        ),
        hear = listOf(
            sound("Cm · Cm(MAJ7) · Cm7") { r -> compare(r, "m", "mMaj7", "m7") },
            sound("C7 · C7♯5 · C7♭5 · C7SUS4") { r -> compare(r, "7", "7#5", "7b5", "7sus4") },
        ),
        tryIt = CourseTry("THE MINOR-MAJOR SEVENTH", RefKind.CHORD, "mMaj7", "C"),
        check = CourseCheck("Half-diminished, or fully diminished?", listOf(
            option("ø7", "Half-diminished: the seventh is a minor seventh.") { r, _ -> hearChord(r, "m7b5") },
            option("°7", "Diminished seventh: all minor thirds, the seventh a diminished seventh.") { r, _ -> hearChord(r, "dim7") },
        )),
        related = listOf(ref(RefKind.CHORD, "mMaj7", "Cm(maj7)"), ref(RefKind.CHORD, "7sus4", "C7sus4"), ear("ch-7-all", "every seventh chord")),
    )

    // ------------------------------------------------------------------------------------------------- sus and add

    private val sus = CoursePage(
        "sus", "Suspended chords", 2,
        "A suspended chord leaves out the 3rd, the note that makes a chord major or minor, and puts a 2nd or a 4th in its " +
            "place. The result is open and undecided, and in a lot of music it sets up the moment when the 3rd comes back.",
        steps = listOf(
            say("Csus4 is C F G: the 3rd (E) replaced by the 4th (F). F sits a half step above E, and the ear hears it as E " +
                "pushed up, waiting to fall back. Play sus4, then the major chord: the F drops to E and the chord settles.",
                staff("Csus4 → C · F FALLS TO E", listOf("C4 F4 G4", "C4 E4 G4"), above = listOf("Csus4", "C"), step = 1.6)),
            say("Csus2 is C D G: the 3rd replaced by the 2nd. It doesn't pull as strongly; it's more a colour than a tension, open " +
                "and airy, very common on guitar.",
                chordKeys("Csus2 · 1 2 5", "sus2", labels = listOf("R", "2", "5"))),
            say("The name comes from counterpoint. A note held over from the previous chord is \"suspended\" above the new chord's " +
                "3rd and then falls to it. The staff shows that: the F is held from an F chord into the C chord, rubs as a sus4, " +
                "then resolves.",
                staff("A SUSPENSION · F HELD OVER, THEN FALLING", listOf("F4 A4 C5", "F4 G4 C5", "E4 G4 C5"), above = listOf("F", "Csus4", "C"), step = 1.3)),
            say("Because there's no 3rd, the same keys can be read two ways. Csus2 (C D G) and Gsus4 (G C D) are the same three " +
                "notes; which name is right depends on which note you hear as the root, usually the bass.",
                chordTable("SAME KEYS, TWO NAMES", listOf("sus2" to "C", "sus4" to "G", "7sus4" to "C"))),
        ),
        inShort = listOf(
            "sus4 = 1 4 5, sus2 = 1 2 5: no 3rd, so neither major nor minor.",
            "sus4 usually resolves down to the 3rd; sus2 is more of a colour.",
            "Csus2 and Gsus4 share their notes; the bass decides the name.",
        ),
        hear = listOf(
            sound("SUS4 → MAJOR") { r -> compare(r, "sus4", "maj") },
            sound("SUS2 → MAJOR") { r -> compare(r, "sus2", "maj") },
        ),
        tryIt = CourseTry("A SUS4 · ITS SHAPES", RefKind.CHORD, "sus4", "D"),
        check = CourseCheck("Major, sus2 or sus4?", listOf("maj" to "Major", "sus2" to "Sus2", "sus4" to "Sus4").map { (id, n) ->
            option(n, "$n: ${formula(id)}.") { r, _ -> hearChord(r, id) }
        }),
        related = listOf(ref(RefKind.CHORD, "sus4", "Csus4"), ref(RefKind.CHORD, "sus2", "Csus2"), page("iv-resolve", "Tension and resolution"), ear("ch-sus", "major · sus2 · sus4")),
    )

    private val add = CoursePage(
        "add", "Added-note chords", 2,
        "An added-note chord keeps its triad, 3rd and all, and adds one extra note on top. The question with these is always " +
            "what isn't there: the 7th. That's what separates an add chord from an extended one.",
        steps = listOf(
            say("Cadd9 is C E G plus D, the 9th (the 2nd an octave up), with no 7th. It sparkles, especially on guitar, where " +
                "the 9th often sits right next to the 3rd.",
                keys("Cadd9 · 1 3 5 9", "C4 E4 G4 D5", listOf("R", "3", "5", "9"))),
            say("Compare it with the two ninth chords. Cmaj9 has the major 7th (B) under the 9th; C9 has the minor 7th (B♭). Same " +
                "9th on top, three different sounds, all decided by the 7th or its absence. Tap the rows.",
                chordTable("THE 9TH, WITH AND WITHOUT A 7TH", listOf("add9" to "C", "maj9" to "C", "9" to "C", "madd9" to "C"))),
            say("Some writers put the added note inside the chord instead and call it add2 or add4: C D E G, C E F G. The notes are " +
                "the same as add9 and add11; only the voicing differs. Watch out for C2: in pop charts it usually means Cadd9, in " +
                "jazz and on iReal Pro it means Csus2, with no 3rd. Apophenia's chord reader lists both readings when a chart " +
                "is ambiguous.",
                chordTable("THE SAME IDEA, OTHER NAMES", listOf("add2" to "C", "add11" to "C"))),
        ),
        inShort = listOf(
            "add9 = triad + 9th, no 7th. 9 and maj9 have a 7th underneath.",
            "add2/add4 are the same notes as add9/add11, voiced inside the chord.",
            "C2 is ambiguous: add9 in pop, sus2 in jazz.",
        ),
        hear = listOf(
            sound("C · CADD9 · CMAJ9 · C9") { r -> compare(r, "maj", "add9", "maj9", "9") },
            sound("Cm · Cm(ADD9)") { r -> compare(r, "m", "madd9") },
        ),
        tryIt = CourseTry("ADD9 · SHAPES ON YOUR NECK", RefKind.CHORD, "add9", "G"),
        check = CourseCheck("Added ninth, or major ninth?", listOf(
            option("Add9", "Add9: the 9th with no 7th.") { r, _ -> hearChord(r, "add9") },
            option("Maj9", "Maj9: the 9th on top of a major 7th.") { r, _ -> hearChord(r, "maj9") },
        )),
        related = listOf(ref(RefKind.CHORD, "add9", "Cadd9"), ear("ch-6-add", "sixths and added notes")),
    )

    // ------------------------------------------------------------------------------------------------- sixths

    private val sixths = CoursePage(
        "sixths", "Sixth chords", 2,
        "A sixth chord adds the major 6th to a triad instead of a 7th. It's the sweet, settled sound of swing-era endings, " +
            "and it hides a surprise: every sixth chord is also a seventh chord with a different root.",
        steps = listOf(
            say("C6 is C E G A: the C major triad plus A, the 6th. It's a softer tonic than Cmaj7, because the A doesn't rub " +
                "against the octave the way B does. Play them side by side.",
                keys("C6, THEN Cmaj7", "C4 E4 G4 A4", listOf("R", "3", "5", "6"), play = Play(listOf(mids("C4 E4 G4 A4"), mids("C4 E4 G4 B4")), 1.8))),
            say("Now look at C6's notes again: C E G A. Start from A instead and you have A C E G, which is Am7. Same four keys. " +
                "Which chord you hear depends on the bass: C underneath and it's C6, A underneath and it's Am7. Likewise Cm6 (C " +
                "E♭ G A) is Aø7. The 6th in Cm6 is still the major 6th.",
                chordTable("SAME KEYS, DIFFERENT ROOTS", listOf("6" to "C", "m7" to "A", "m6" to "C", "m7b5" to "A"))),
            say("C6/9 adds the 9th as well: C E G A D, still with no 7th. It's a wide, jazzy major chord. The slash in 6/9 isn't a " +
                "bass note; C6/9 is never \"C6 over 9\".",
                chordKeys("C6/9 · 1 3 5 6 9", "69", labels = listOf("R", "3", "5", "6", "9"))),
            say("Barry Harris built a whole approach to harmony on the major sixth chord and the diminished seventh a half step " +
                "below its root, alternating them under a scale (C6, B°7, C6...). That's why the sixth chord is a tonic in so " +
                "much bebop piano.",
                null),
        ),
        inShort = listOf(
            "6 = triad + major 6th; m6 = minor triad + major 6th.",
            "C6 = Am7 and Cm6 = Aø7 in notes; the bass decides.",
            "6/9 adds the 9th, never a slash chord.",
        ),
        hear = listOf(
            sound("C6 · CMAJ7") { r -> compare(r, "6", "maj7") },
            sound("C6 OVER C, THEN OVER A") { r -> piano(RefSound.block(listOf(r - 12, r + 4, r + 7, r + 9, r + 12), length = 1.8) + RefSound.block(listOf(r - 15, r + 4, r + 7, r + 9, r + 12), at = 2.2, length = 1.8)) },
        ),
        tryIt = CourseTry("THE SIXTH CHORD", RefKind.CHORD, "6", "C"),
        check = CourseCheck("A sixth chord, or a major seventh?", listOf(
            option("6", "C6: the 6th on top, sweet and settled.") { r, _ -> hearChord(r, "6") },
            option("Maj7", "Cmaj7: the 7th, a half step under the octave.") { r, _ -> hearChord(r, "maj7") },
        )),
        related = listOf(ref(RefKind.CHORD, "6", "C6"), ref(RefKind.CHORD, "m6", "Cm6"), ref(RefKind.CHORD, "69", "C6/9"), ear("ch-6-add", "sixths and added notes")),
    )

    // ------------------------------------------------------------------------------------------------- extensions

    internal val EXTENDED = listOf("9", "maj9", "m9", "11", "m11", "13", "maj13", "m13")

    private val extensions = CoursePage(
        "extensions", "Ninths, elevenths, thirteenths", 3,
        "Keep stacking thirds past the 7th and you reach the 9th, the 11th and the 13th. They're not new notes: they're the " +
            "2nd, 4th and 6th of the scale, an octave up. What makes them confusing is that a chord symbol names only the " +
            "top one and leaves the rest implied.",
        steps = listOf(
            say("Stack thirds from C through the C Mixolydian scale (the scale of C7) and keep going: C E G B♭, then D (9), F (11), " +
                "A (13). One more third would land on C again: after the 13th the stack has used every note of the scale.",
                keys("THE WHOLE STACK OF THIRDS ON C", "C3 E3 G3 Bb3 D4 F4 A4", listOf("R", "3", "5", "♭7", "9", "11", "13"))),
            say("A symbol names the highest extension and implies the ones below: C9 means C7 plus the 9th; C11 means C7 plus 9 " +
                "and 11; C13 means C7 plus 9, 11 and 13 (in practice the 11 is left out; see the next page). The exceptions are " +
                "chords that say otherwise: 6, 6/9, add and sus chords have no 7th.",
                table("WHAT EACH SYMBOL IMPLIES", listOf("SYMBOL", "MEANS"), listOf(listOf("C9", "C7 + 9"), listOf("C11", "C7 + 9 + 11"), listOf("C13", "C7 + 9 + (11) + 13"), listOf("Cadd9", "C + 9, no 7th")),
                    listOf(rowPlay("9"), rowPlay("11"), rowPlay("13"), rowPlay("add9")))),
            say("The 7th underneath comes from the symbol the same way as for seventh chords: plain 9, 11, 13 mean a dominant " +
                "(♭7); maj9, maj13 mean a major 7th; m9, m11, m13 a minor triad with a minor 7th. Compare the families in the " +
                "table.",
                chordTable("EXTENDED CHORDS ON C", EXTENDED.map { it to "C" })),
        ),
        inShort = listOf(
            "9, 11, 13 = 2, 4, 6 an octave up, stacked above the 7th.",
            "The symbol names the top extension; the 7th and lower extensions are implied.",
            "Plain number = dominant 7th; maj = major 7th; m = minor triad with ♭7.",
        ),
        hear = listOf(
            sound("C7 · C9 · C13") { r -> compare(r, "7", "9", "13") },
            sound("CMAJ7 · CMAJ9 · CMAJ13") { r -> compare(r, "maj7", "maj9", "maj13") },
        ),
        tryIt = CourseTry("A NINTH CHORD · SHAPES ON YOUR NECK", RefKind.CHORD, "9", "C"),
        check = CourseCheck("A plain seventh chord, or a ninth?", listOf(
            option("7", "C7: four notes, no 9th.") { r, _ -> hearChord(r, "7") },
            option("9", "C9: the 9th on top.") { r, _ -> hearChord(r, "9") },
        )),
        related = listOf(ref(RefKind.CHORD, "9", "C9"), ref(RefKind.CHORD, "13", "C13"), ear("ch-ext", "ninths and beyond")),
    )

    private val leftOut = CoursePage(
        "extensions-left", "What gets left out", 3,
        "A full thirteenth chord would be seven notes, the whole scale at once, and almost nobody plays it that way. Players " +
            "leave notes out, always in the same order of importance, so the chord keeps its identity with three or four " +
            "notes.",
        steps = listOf(
            say("The 5th goes first: it adds almost no colour. Then the root, because the bass player usually has it (a voicing " +
                "without the root is called rootless). The 3rd and 7th always stay: they say whether the chord is major, minor " +
                "or dominant. The table shows what each extended chord drops first.",
                table("WHAT EACH LEAVES OUT FIRST", listOf("", "FORMULA", "OMIT FIRST"), EXTENDED.map { id ->
                    listOf(symbol(id, "C"), formula(id), Chords.byId(id).omitOrder.joinToString(" ") { accidentals(it.toString()) })
                }, EXTENDED.map { rowPlay(it) })),
            say("Here's C13 boiled down: just root, 3rd, ♭7 and 13. Four notes, and it still sounds unmistakably like C13. This is " +
                "how guitarists and pianists actually play it.",
                keys("A PLAYABLE C13 · R 3 ♭7 13", "C3 E3 Bb3 A4", listOf("R", "3", "♭7", "13"))),
            say("The 11 is the exception. Over a major 3rd, the natural 11th (F over E) rubs a half step above the 3rd and muddies " +
                "it. So C13 is played without the 11th, and a major chord that wants that colour takes the ♯11 instead, " +
                "Cmaj7♯11 (F♯). The C11 chord goes the other way and drops the 3rd: it sounds close to C9sus4, or B♭/C.",
                keys("THE CLASH · E AND F TOGETHER", "C4 E4 F4", listOf("R", "3", "11"), play = Play(listOf(mids("C3 E4 G4 Bb4 F5"), mids("C3 E4 G4 B4 F#5")), 1.8))),
        ),
        inShort = listOf(
            "Leave out the 5th first, then the root; keep the 3rd and 7th.",
            "A compact 13 is root, 3, ♭7, 13.",
            "Avoid the natural 11 over a major 3rd: use ♯11, or drop the 3rd (11 chords).",
        ),
        hear = listOf(
            sound("C13 IN FULL · THEN ROOT, 3, 7, 13") { r -> piano(chord(r - 12, "13") + RefSound.block(listOf(r - 12, r - 8, r - 2, r + 9), at = 2.2, length = 1.8)) },
            sound("C11 · C9SUS4") { r -> piano(chord(r, "11") + RefSound.block(listOf(r, r + 5, r + 7, r + 10, r + 14), at = 2.2, length = 1.8)) },
        ),
        tryIt = CourseTry("THE THIRTEENTH · SHAPES LEAVE OUT THE 5TH, 9TH OR ROOT", RefKind.CHORD, "13", "G"),
        check = CourseCheck("A dominant ninth, or a thirteenth?", listOf(
            option("9", "A 9th chord: the 9th is the top colour.") { r, _ -> piano(RefSound.block(listOf(r - 12, r + 4, r + 10, r + 14), length = 2.2)) },
            option("13", "A 13th chord: the 13th, the 6th an octave up, on top.") { r, _ -> piano(RefSound.block(listOf(r - 12, r + 4, r + 10, r + 21), length = 2.2)) },
        ), roots = 48..58),
        related = listOf(ref(RefKind.CHORD, "11", "C11"), ref(RefKind.CHORD, "maj7#11", "Cmaj7♯11"), ref(RefKind.CHORD, "13", "C13")),
    )

    // ------------------------------------------------------------------------------------------------- altered

    internal val ALTERED = listOf("7b9", "7#9", "7#11", "7b13", "7alt")

    private val altered = CoursePage(
        "altered", "Altered dominants", 4,
        "A dominant seventh can carry tensions a half step away from the natural ones. They make the pull toward the next " +
            "chord sharper and darker, especially into a minor chord, and they're the sound of a lot of jazz, blues-rock and " +
            "film music.",
        steps = listOf(
            say("The natural tensions of C7 are 9 (D), 11 (F) and 13 (A). The altered ones sit a half step either side: ♭9 (D♭) " +
                "and ♯9 (D♯), ♯11 (F♯), ♭13 (A♭). The natural ones are outlined; the altered ones lit.",
                keys("ALTERED TENSIONS OF C7", "Db4 D#4 F#4 Ab4", listOf("♭9", "♯9", "♯11", "♭13"), faint = "C4 E4 Bb4 D4 F4 A4",
                    play = Play(listOf(mids("C3 E3 Bb3 Db4"), mids("C3 E3 Bb3 D#4"), mids("C3 E3 Bb3 F#4"), mids("C3 E3 Bb3 Ab4")), 1.5))),
            say("Why they work: each altered note leads by half step into a note of the next chord. G7♭9 has A♭, which falls to G " +
                "of Cm (or C); the ♭13, E♭, is the 3rd of C minor waiting to arrive. That's why altered dominants are favourites " +
                "before minor chords. Follow the top voice.",
                staff("G7♭9 → Cm", listOf("B3 F4 Ab4", "C4 Eb4 G4"), above = listOf("G7♭9", "Cm"), step = 1.5)),
            say("Symbols always include the 7 when an extension is altered, and use ♭ and ♯ rather than − and +: C7♭9, C7♯9, " +
                "never C♭9. Spelling stays in thirds, so the ♯9 of C7 is D♯, not E♭ (it's a kind of 9th, a D), and the ♯9 of " +
                "E7 is F𝄪. The table shows the main ones; tap to hear.",
                chordTable("ALTERED DOMINANTS", ALTERED.map { it to "C" } + listOf("7#9" to "E"))),
            say("C7alt (or Calt) means a dominant with some selection of ♭9, ♯9, ♯11 and ♭13, and no natural 5, 9 or 13. The " +
                "player chooses which; the altered scale (the 7th mode of melodic minor) contains all of them.",
                null),
        ),
        inShort = listOf(
            "Altered tensions: ♭9, ♯9, ♯11, ♭13, a half step from the natural 9, 11, 13.",
            "They lead by half step into the next chord, especially a minor one.",
            "Write the 7 (C7♭9) and spell in thirds (♯9 of C is D♯).",
            "alt = any mix of ♭9 ♯9 ♯11 ♭13, no natural tensions.",
        ),
        hear = listOf(
            sound("C7 · C7♭9 · C7♯9") { r -> compare(r, "7", "7b9", "7#9") },
            sound("G7♭9 → Cm") { r -> piano(chord(r - 5, "7b9") + RefSound.block(listOf(r - 12, r, r + 3, r + 7), at = 2.2, length = 2.0)) },
            sound("E7♯9") { r -> hearChord(r + 4 - 12, "7#9") },
        ),
        tryIt = CourseTry("E7♯9 · THE F𝄪 ON TOP", RefKind.CHORD, "7#9", "E"),
        check = CourseCheck("Flat nine, or sharp nine?", listOf(
            option("7♭9", "7♭9: the 9th a half step above the root, dark.") { r, _ -> hearChord(r, "7b9") },
            option("7♯9", "7♯9: the 9th a half step under the 3rd, bluesy.") { r, _ -> hearChord(r, "7#9") },
        ), roots = 45..58),
        related = listOf(ref(RefKind.CHORD, "7#9", "E7♯9", "E"), ref(RefKind.CHORD, "7alt", "C7alt"), ref(RefKind.SCALE, "altered", "The altered scale", "G"), ear("ch-ext", "ninths and beyond")),
    )

    // ------------------------------------------------------------------------------------------------- slash, poly

    private val slash = CoursePage(
        "slash", "Slash chords", 4,
        "A slash chord names a chord and then a bass note under it: D/F♯ is a D chord with F♯ in the bass. Sometimes that's " +
            "just an inversion; sometimes the bass note isn't in the chord at all, and the two together make something new.",
        steps = listOf(
            say("When the bass is one of the chord's own notes, it's an inversion. C/E is C major with its 3rd in the bass.",
                keys("C/E · AN INVERSION", "E3 C4 E4 G4", listOf("E", "C", "E", "G"))),
            say("When the bass isn't in the chord, add them up. F/G is F A C over G. Measured from G, those notes are the 7th " +
                "(F), 9th (A) and 11th (C), with no 3rd: a G9sus4, sometimes written G11. Gospel and pop use it as a soft V that " +
                "resolves to C without the bite of G7.",
                keys("F/G · F TRIAD OVER G", "G2 F3 A3 C4", listOf("G", "F", "A", "C"), play = Play(listOf(mids("G2 F3 A3 C4"), mids("C3 E3 G3 C4")), 1.8))),
            say("D/C is D F♯ A over C. Read from D it's a D7 with its 7th in the bass (third inversion); read from C it's C with a " +
                "9 (D), ♯11 (F♯) and 13 (A), a C Lydian colour. Context decides which you hear.",
                keys("D/C", "C3 D4 F#4 A4", listOf("C", "D", "F♯", "A"))),
            say("The method for any slash chord: play the chord in your right hand (or on the top strings), the bass note in the " +
                "left (or on the low string), and let your ear name the sum. The table collects the common ones.",
                table("WHAT THE SUM IS", listOf("SLASH", "NOTES", "HEARD AS"), listOf(
                    listOf("C/E", "E · C E G", "C, first inversion"),
                    listOf("F/G", "G · F A C", "G9sus4 (G11)"),
                    listOf("D/C", "C · D F♯ A", "D7, third inversion · C Lydian"),
                    listOf("B♭/C", "C · B♭ D F", "C9sus4"),
                ), listOf(mids("E3 C4 E4 G4"), mids("G2 F3 A3 C4"), mids("C3 D4 F#4 A4"), mids("C3 Bb3 D4 F4")).map { Play(listOf(it), 1.8) })),
        ),
        inShort = listOf(
            "Chord / bass note. If the bass is a chord tone, it's an inversion.",
            "If not, add it up: F/G = G9sus4, B♭/C = C9sus4, D/C = D7 over its 7th or C Lydian.",
        ),
        hear = listOf(
            sound("F/G → C") { r -> piano(RefSound.block(listOf(r - 5, r + 5, r + 9, r + 12), length = 1.8) + RefSound.block(listOf(r - 12, r + 4, r + 7, r + 12), at = 2.2, length = 2.0)) },
            sound("D/C") { r -> piano(RefSound.block(listOf(r - 12, r + 2, r + 6, r + 9), length = 2.5)) },
        ),
        tryIt = CourseTry("A NINTH SUS · WHAT F/G ADDS UP TO", RefKind.CHORD, "11", "G"),
        check = CourseCheck("The root in the bass, or another note?", listOf(
            option("Root", "The root in the bass: C.") { r, _ -> piano(RefSound.block(listOf(r - 12, r + 4, r + 7, r + 12), length = 2.2)) },
            option("Another note", "Another note: the same C chord over E, or over D.") { r, rnd -> piano(RefSound.block(listOf(r - 12 + if (rnd.nextBoolean()) 4 else 2, r + 4, r + 7, r + 12), length = 2.2)) },
        )),
        related = listOf(page("inversions", "Inversions"), ref(RefKind.CHORD, "11", "C11")),
    )

    private val poly = CoursePage(
        "polychords", "Polychords", 4,
        "A polychord is two whole chords at once, one stacked over the other, written with a horizontal line instead of a " +
            "slash. Where a slash chord puts one note under a chord, a polychord puts a whole chord under it.",
        steps = listOf(
            say("D over C: the D major triad (D F♯ A) on top, the C major triad (C E G) below. Heard as one chord it's C with " +
                "its 9th, ♯11 and 13, the bright Lydian sound in a single grip.",
                keys("D OVER C", "C3 E3 G3 D4 F#4 A4", listOf("C", "E", "G", "D", "F♯", "A"))),
            say("Part of the effect is that you can hear the two chords as separate layers. The most famous clash is Stravinsky's " +
                "Petrushka chord: C major against F♯ major, two triads a tritone apart.",
                keys("C AGAINST F♯ · THE PETRUSHKA CHORD", "C3 E3 G3 F#4 A#4 C#5", listOf("C", "E", "G", "F♯", "A♯", "C♯"))),
            say("In jazz the same idea becomes the upper-structure triad: a triad played over a dominant's 3rd and 7th instead of " +
                "over a full chord. That's the last page of this chapter.",
                null),
        ),
        inShort = listOf(
            "Two chords stacked, written with a horizontal line.",
            "D over C = C maj9♯11 13; C against F♯ = Petrushka.",
            "Over a dominant shell, it becomes an upper structure.",
        ),
        hear = listOf(
            sound("C, THEN D OVER C") { r -> piano(RefSound.block(listOf(r - 12, r - 8, r - 5), length = 1.8) + RefSound.block(listOf(r - 12, r - 8, r - 5, r + 2, r + 6, r + 9), at = 2.2, length = 2.2)) },
            sound("C AGAINST F♯") { r -> piano(RefSound.block(listOf(r - 12, r - 8, r - 5, r + 6, r + 10, r + 13), length = 2.5)) },
        ),
        tryIt = CourseTry("MAJ7♯11 · THE LYDIAN CHORD D OVER C SPELLS", RefKind.CHORD, "maj7#11", "C"),
        related = listOf(page("upper", "Upper structures"), ref(RefKind.SCALE, "lydian", "Lydian")),
    )

    // ------------------------------------------------------------------------------------------------- quartal

    private val quartal = CoursePage(
        "quartal", "Quartal and cluster voicings", 4,
        "Chords don't have to be built in thirds. Stack fourths, or seconds, and you get sounds that don't announce major or " +
            "minor at all, the open, floating harmony of modal jazz and a lot of modern film scoring.",
        steps = listOf(
            say("Stack perfect fourths: D, G, C, F. Every interval is the same, so no note stands out as the root. The chord is " +
                "open and ambiguous, neither major nor minor.",
                keys("STACKED FOURTHS · D G C F", "D4 G4 C5 F5")),
            say("The best-known quartal voicing is three fourths with a major third on top: E A D G B. It's often called the \"So " +
                "What\" chord after the Miles Davis track where Bill Evans played it. Moved up and down the notes of one mode, " +
                "these voicings make modal jazz's floating sound; McCoy Tyner built much of his piano style on them.",
                keys("THE SO WHAT CHORD · E A D G B", "E3 A3 D4 G4 B4", play = Play(listOf(mids("E3 A3 D4 G4 B4"), mids("F3 Bb3 Eb4 Ab4 C5")), 1.8))),
            say("Stack seconds instead and you have a cluster: C D E, or wider. Clusters range from a soft colour in film music to " +
                "the forearm clusters of Henry Cowell. Compare the four kinds of stacking in the table.",
                table("FOUR WAYS TO STACK", listOf("", "NOTES", "INTERVALS"), listOf(
                    listOf("Quartal", "D G C F", "P4 P4 P4"),
                    listOf("So What", "E A D G B", "P4 P4 P4 M3"),
                    listOf("Cluster", "C D E", "M2 M2"),
                    listOf("Tertian", "C E G B", "M3 m3 M3"),
                ), listOf(mids("D4 G4 C5 F5"), mids("E3 A3 D4 G4 B4"), mids("C4 D4 E4"), mids("C4 E4 G4 B4")).map { Play(listOf(it), 1.8, spread = true) })),
        ),
        inShort = listOf(
            "Quartal: stacked fourths, no clear root, neither major nor minor.",
            "So What: three fourths and a major third.",
            "Clusters: stacked seconds.",
        ),
        hear = listOf(
            sound("STACKED FOURTHS") { r -> piano(RefSound.block(listOf(r + 2, r + 7, r + 12, r + 17), length = 2.2)) },
            sound("SO WHAT · UP A STEP") { r -> piano(RefSound.block(listOf(r - 8, r - 3, r + 2, r + 7, r + 11), length = 1.8) + RefSound.block(listOf(r - 7, r - 2, r + 3, r + 8, r + 12), at = 2.2, length = 1.8)) },
            sound("A CLUSTER") { r -> piano(RefSound.block(listOf(r, r + 2, r + 4, r + 5), length = 2.2)) },
        ),
        check = CourseCheck("Stacked fourths, or stacked thirds?", listOf(
            option("Fourths", "Fourths: open, neither major nor minor.") { r, _ -> piano(RefSound.block(listOf(r, r + 5, r + 10, r + 15), length = 2.2)) },
            option("Thirds", "Thirds: a seventh chord.") { r, rnd -> piano(RefSound.block(if (rnd.nextBoolean()) listOf(r, r + 4, r + 7, r + 11) else listOf(r, r + 3, r + 7, r + 10), length = 2.2)) },
        )),
        related = listOf(ref(RefKind.SCALE, "dorian", "Dorian", "D"), page("modes-seven", "One scale, seven modes")),
    )

    // ------------------------------------------------------------------------------------------------- upper structures

    /** What a pitch class above a dominant's root is called as a tension. */
    internal val TENSION = listOf("1", "♭9", "9", "♯9", "3", "11", "♯11", "5", "♭13", "13", "♭7", "7")
    /** Upper-structure triads over C7 by their root's distance from C: name, semitones. */
    internal val UPPER = listOf("II" to 2, "♭III" to 3, "♯IV" to 6, "♭VI" to 8, "VI" to 9)
    internal fun upperTensions(semis: Int) = listOf(0, 4, 7).map { TENSION[(semis + it) % 12] }

    private fun shell(r: Int) = listOf(r - 12, r - 8, r - 2)
    private fun upperRoot(s: Int) = com.betterappmafia.apophenia.core.pitch.PitchClass(s).defaultSpelling().let { if (s == 6) p("F#") else it }

    private val upper = CoursePage(
        "upper", "Upper structures", 5,
        "An upper structure is a plain major triad played over a dominant seventh's shell. The triad's three notes become " +
            "tensions of the dominant, so one easy shape in the right hand gives you five different colours of dominant.",
        steps = listOf(
            say("Start with C7's shell in the left hand: root, 3rd and ♭7 (C E B♭). Those three notes are enough to say \"C7\". " +
                "Everything above is colour.",
                keys("THE SHELL · C E B♭", "C3 E3 Bb3", listOf("R", "3", "♭7"))),
            say("Put a D major triad on top (D F♯ A). Measured from C, D is the 9, F♯ the ♯11, A the 13: a bright C13♯11 from " +
                "a shape any guitarist or pianist already knows.",
                keys("D OVER C7 · 9 ♯11 13", "C3 E3 Bb3 D4 F#4 A4", listOf("R", "3", "♭7", "9", "♯11", "13"))),
            say("Move the triad and the tensions change. A♭ major gives ♭13, root and ♯9: dark and altered. Pianists name each by " +
                "the triad's root as a numeral from the dominant's root: II, ♭III, ♯IV, ♭VI, VI. Tap a row to hear the triad " +
                "over the shell.",
                table("TRIADS OVER C7", listOf("", "TRIAD", "TENSIONS"), UPPER.map { (n, s) -> listOf(n, shown(upperRoot(s)), upperTensions(s).joinToString(" ")) },
                    UPPER.map { (_, s) -> Play(listOf(listOf(48, 52, 58) + listOf(0, 4, 7).map { 60 + s + it }), 2.0) })),
        ),
        inShort = listOf(
            "Shell (R 3 ♭7) in the left hand, a major triad in the right.",
            "II = 9 ♯11 13 (bright); ♭VI = ♭13 1 ♯9 and ♭III = ♯9 5 ♭7 (dark).",
            "One triad shape, five dominant colours.",
        ),
        hear = UPPER.map { (n, s) -> sound("$n OVER C7") { r -> piano(RefSound.block(shell(r) + listOf(0, 4, 7).map { r + s + it }, length = 2.4)) } },
        tryIt = CourseTry("C13♯11 · THE II TRIAD OVER C7", RefKind.CHORD, "7#11", "C"),
        check = CourseCheck("Over the C7 shell: the bright II triad, or the dark ♭VI?", listOf(
            option("II", "II: D major over C7, 9 ♯11 13.") { r, _ -> piano(RefSound.block(shell(r) + listOf(0, 4, 7).map { r + 2 + it }, length = 2.4)) },
            option("♭VI", "♭VI: A♭ major over C7, ♭13 1 ♯9.") { r, _ -> piano(RefSound.block(shell(r) + listOf(0, 4, 7).map { r + 8 + it }, length = 2.4)) },
        ), roots = 55..62),
        related = listOf(page("polychords", "Polychords"), ref(RefKind.CHORD, "7alt", "C7alt"), ref(RefKind.SCALE, "half-whole", "Half-whole")),
    )

    val topics = listOf(
        CourseTopic("triads", "Triads", "major, minor, diminished, augmented", 1, listOf(build, four)),
        CourseTopic("inversions", "Inversions", "which note is in the bass", 2, listOf(inversions)),
        CourseTopic("sevenths", "Seventh chords", "maj7, m7, 7, ø7, °7 and the rest", 2, listOf(sevenths, moreSevenths)),
        CourseTopic("sus-add", "Sus and add chords", "sus2, sus4, add9", 2, listOf(sus, add)),
        CourseTopic("sixths", "Sixth chords", "6, m6, 6/9", 2, listOf(sixths)),
        CourseTopic("extensions", "Ninths, elevenths, thirteenths", "what's implied, what's left out", 3, listOf(extensions, leftOut)),
        CourseTopic("altered", "Altered dominants", "♭9, ♯9, ♯11, ♭13, alt", 4, listOf(altered)),
        CourseTopic("slash", "Slash chords and polychords", "a chord over a bass, a chord over a chord", 4, listOf(slash, poly)),
        CourseTopic("quartal", "Quartal and cluster voicings", "stacks of fourths, seconds", 4, listOf(quartal)),
        CourseTopic("upper", "Upper structures", "triads over a dominant", 5, listOf(upper)),
    )
}
