package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.catalog.Chords
import com.betterappmafia.apophenia.core.learn.CourseIntervals.shown
import com.betterappmafia.apophenia.core.pitch.PitchName

/*
 * Course · Chords: triads and their four qualities, inversions, seventh chords, sus and add, sixths, extensions and
 * what's left out, altered dominants, slash chords and polychords, quartal and cluster voicings, upper structures.
 * Text written for Apophenia; every spelling in the tables comes from ChordPages.
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

    // ------------------------------------------------------------------------------------------------- triads

    private val build = CoursePage(
        "triad-build", "Building a triad", 1,
        "A triad is three notes stacked in thirds: a root, the third above it, and the fifth above that. Take every other " +
            "note of a scale and you get one: C E G from C major. The two thirds can each be major (4 half steps) or minor " +
            "(3), and the combination decides the chord. A major third under a minor third is a major triad, C E G. A minor " +
            "third under a major third is a minor triad, C E♭ G. Both have a perfect fifth between root and top. On a " +
            "guitar the common open and barre shapes are these three notes doubled in different octaves; a power chord " +
            "(C5) is just the root and fifth, without the third that makes a chord major or minor.",
        see = CourseSee.Table("TRIADS ON C", ROW_HEADER, listOf("maj", "m", "5").map { row(it) }),
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
        "With two kinds of third there are four ways to stack them. Major (M3 + m3) and minor (m3 + M3) have a perfect " +
            "fifth. Two minor thirds make a diminished triad, C E♭ G♭, whose fifth is diminished: tense, it sits on the 7th " +
            "degree of a major key. Two major thirds make an augmented triad, C E G♯, whose fifth is augmented: it splits " +
            "the octave into three equal parts, so C+, E+ and G♯+ are the same keys. Spelling follows the letters, every " +
            "other letter from the root: G♯ major is G♯ B♯ D♯, with a B♯, because the third of G must be some kind of B.",
        see = CourseSee.Table("THE FOUR ON C", ROW_HEADER, listOf("maj", "m", "dim", "aug").map { row(it) } + listOf(row("maj", "G#"))),
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
        "A chord with its root at the bottom is in root position. Put the third in the bass and it's in first inversion; " +
            "the fifth, second inversion; a seventh chord's seventh, third inversion. The upper notes can be in any order " +
            "and any octave; only the bass decides. Chord symbols write an inversion as a slash chord, C/E or C/G. Classical " +
            "figured bass numbers them by the intervals above the bass: 6 for a first-inversion triad and 6/4 for a second, " +
            "6/5, 4/3 and 4/2 for the three inversions of a seventh chord. Inversions let a bass line move by step (C, C/B, " +
            "Am, Am/G) and soften a chord's weight: first inversion is lighter than root position, and a second-inversion " +
            "triad usually only passes through.",
        see = CourseSee.Table("C MAJOR AND G7", listOf("", "BASS", "FIGURES"), listOf("maj" to "C", "7" to "G").flatMap { (id, r) ->
            ChordPages.page(Chords.byId(id), p(r)).inversions.map { inv -> listOf(accidentals(inv.symbol), shown(inv.bass), inv.figures) }
        }),
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
        "Add one more third on top of a triad and you have a seventh chord. Five matter most. A major triad with a major " +
            "seventh is a major seventh, Cmaj7 (C E G B): settled and soft. A major triad with a minor seventh is a " +
            "dominant seventh, C7 (C E G B♭): the V7 of a key, with the tritone that pulls home, and the home chord of the " +
            "blues. A minor triad with a minor seventh is a minor seventh, Cm7 (C E♭ G B♭). A diminished triad with a minor " +
            "seventh is half-diminished, Cø7 or Cm7♭5 (C E♭ G♭ B♭). A diminished triad with a diminished seventh is a " +
            "diminished seventh, C°7 (C E♭ G♭ B𝄫): all minor thirds, so it repeats every three half steps and four roots " +
            "share its keys.",
        see = CourseSee.Table("THE FIVE ON C", ROW_HEADER, MAIN_SEVENTHS.map { row(it) }),
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
        "The other combinations of triad and seventh have their own sounds. A minor triad with a major seventh, Cm(maj7), is " +
            "the dark, suspenseful chord of a lot of film music and of a descending minor line (Cm, Cm(maj7), Cm7). An " +
            "augmented triad with a major seventh, Cmaj7♯5, is the tonic of Lydian augmented; with a minor seventh, C7♯5, " +
            "it's a dominant with a raised fifth. C7♭5 lowers the fifth instead, and its notes repeat at the tritone: C7♭5 " +
            "and G♭7♭5 share their keys. And C7sus4 replaces the third with the fourth, a dominant that doesn't commit, " +
            "common in modal jazz and as a V that resolves without the usual pull.",
        see = CourseSee.Table("ON C", ROW_HEADER, MORE_SEVENTHS.map { row(it) }),
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
        "A suspended chord replaces the third with a second or a fourth: Csus2 is C D G, Csus4 is C F G. With no third " +
            "it's neither major nor minor, which gives it an open, unresolved sound. The name comes from counterpoint, " +
            "where a note held over from the previous chord is \"suspended\" a step above the third and then falls to it; " +
            "in pop and rock a sus chord often resolves the same way, Csus4 to C, or simply stays as a colour. Csus2 and " +
            "Gsus4 are the same keys (C D G against G C D), so the name depends on which note you hear as the root.",
        see = CourseSee.Table("ON C", ROW_HEADER, listOf("sus2", "sus4", "7sus4").map { row(it) } + listOf(row("sus4", "G"))),
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
        "An added-note chord keeps its triad and adds one note without the stack of thirds in between. Cadd9 is C E G D: " +
            "the 9th with no 7th, which is what separates it from C9 (with B♭) and Cmaj9 (with B). Cadd11 adds the F the " +
            "same way. Some writers put the added note inside the triad and call it add2 or add4 (C D E G, C E F G); the " +
            "notes are the same, the voicing differs. Charts disagree about C2: in pop it usually means Cadd9, in jazz and " +
            "on iReal Pro it means Csus2, so the third may or may not be there. When it matters, Apophenia's chord reader " +
            "lists both readings.",
        see = CourseSee.Table("ON C", ROW_HEADER, listOf("add9", "madd9", "add11", "add2", "9", "maj9").map { row(it) }),
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
        "A sixth chord adds the major sixth to a triad: C6 is C E G A, Cm6 is C E♭ G A (in Cm6 the 6th is still major). " +
            "C6 has the same notes as Am7, and Cm6 the same as Aø7: which one you hear depends on the bass and the context. " +
            "Sixth chords are a softer alternative to the major seventh as a tonic, the sound of swing-era endings, and C6/9 " +
            "(C E G A D) adds the 9th as well, with no 7th, so it's never a slash chord. Barry Harris built a whole " +
            "approach to harmony on the major sixth chord and the diminished seventh a half step below its root.",
        see = CourseSee.Table("ON C", ROW_HEADER, listOf("6", "m6", "69").map { row(it) } + listOf(row("m7", "A"), row("m7b5", "A"))),
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
        "Keep stacking thirds past the seventh and you reach the 9th, the 11th and the 13th: the 2nd, 4th and 6th an " +
            "octave up. After the 13th the stack lands on the root again. A chord symbol names only its highest extension " +
            "and implies the rest: a 9 implies the 7th, an 11 the 7th and 9th, a 13 the 7th, 9th and 11th. The exceptions " +
            "are chords that say otherwise: 6, 6/9, add and sus chords have no 7th. C9 is C E G B♭ D, Cmaj9 C E G B D, " +
            "Cm9 C E♭ G B♭ D. The quality of the 7th comes from the symbol the same way it does for seventh chords: plain " +
            "9 means a dominant, maj9 a major seventh underneath.",
        see = CourseSee.Table("EXTENDED CHORDS ON C", ROW_HEADER, EXTENDED.map { row(it) }),
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
        "A full thirteenth chord would be seven notes, every note of the scale at once, and almost nobody plays it that " +
            "way. Notes are left out in a fixed order of importance. The 5th goes first: it adds little colour. Then the " +
            "root, which the bass player usually has (a voicing without it is rootless). The 3rd and the 7th stay: they " +
            "say whether the chord is major, minor or dominant. The 11 chord is the exception, dropping the 3rd because the " +
            "11th clashes with it; C11 sounds close to C9sus4, or B♭/C. Over a major 3rd the natural 11th itself is avoided " +
            "(it rubs a half step above the 3rd), so C13 is played without the 11th and a major chord takes a ♯11 instead, " +
            "Cmaj7♯11. A compact C13 on guitar or piano is just C E B♭ A: root, 3rd, 7th, 13th.",
        see = CourseSee.Table("WHAT EACH LEAVES OUT FIRST", listOf("", "FORMULA", "OMIT FIRST"), EXTENDED.map { id ->
            listOf(symbol(id, "C"), formula(id), Chords.byId(id).omitOrder.joinToString(" ") { accidentals(it.toString()) })
        }),
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
        "A dominant seventh can carry tensions a half step off the natural ones: the ♭9 and ♯9, the ♯11, the ♭13. They " +
            "sharpen the pull toward the next chord, especially a minor one, and they come from the scales a dominant " +
            "borrows: half-whole diminished (♭9 ♯9 ♯11 13), Phrygian dominant (♭9 ♭13), the altered scale (all four). In " +
            "a symbol the 7 is always written when an extension is altered, and the alteration uses ♭ and ♯ rather than − " +
            "and +: C7♭9, C7♯9, not C♭9. Spelling stays in thirds: the ♯9 of C7 is D♯, not E♭, and the ♯9 of E7, the " +
            "chord in the riff of many a blues-rock song, is F𝄪. C7alt (or Calt) means some selection of ♭9 ♯9 ♯11 ♭13 " +
            "with no natural 5, 9 or 13; the player chooses which.",
        see = CourseSee.Table("ALTERED DOMINANTS ON C", ROW_HEADER, ALTERED.map { row(it) } + listOf(row("7#9", "E"))),
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
        "A slash chord names a chord and then the bass note under it: D/F♯ is a D chord with F♯ in the bass. When the bass " +
            "is one of the chord's own notes it's just an inversion. When it isn't, the two together make a new chord, and " +
            "the slash is often the clearest way to write it. F/G is F A C over G: G with a 7th, 9th and 11th and no 3rd, a " +
            "G9sus4 or G11 that gospel and pop use as a soft V. D/C is D F♯ A over C: a D7 in third inversion, or a C " +
            "Lydian colour (9, ♯11, 13). B♭/C is C9sus4 again in another key. The rule for reading them: play the chord in " +
            "your right hand or on the top strings and the note after the slash in the bass, and let the ear name the sum.",
        see = CourseSee.Table("WHAT THE SUM IS", listOf("SLASH", "NOTES", "HEARD AS"), listOf(
            listOf("C/E", "E · C E G", "C, first inversion"),
            listOf("F/G", "G · F A C", "G9sus4 (G11)"),
            listOf("D/C", "C · D F♯ A", "D7, third inversion · C Lydian"),
            listOf("B♭/C", "C · B♭ D F", "C9sus4"),
        )),
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
        "A polychord is two chords at once, one stacked over the other, written with a horizontal line: D over C, the D " +
            "triad on top and the C triad below. It isn't the same as a slash chord, where only a single bass note goes " +
            "under. D over C gives C E G with D F♯ A: a C major chord with its 9th, ♯11 and 13, the Lydian sound in one " +
            "grip. Hearing a polychord as two separate chords is part of the effect; Stravinsky's Petrushka chord, C major " +
            "against F♯ major, is the famous clash. In jazz the idea becomes the upper-structure triad, a triad played over " +
            "a dominant's 3rd and 7th.",
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
        "Chords don't have to be stacked in thirds. Stack perfect fourths and you get quartal harmony: D G C F, open and " +
            "ambiguous, without the major-or-minor certainty of a triad. The best-known quartal voicing, often called the " +
            "So What chord after its use on that Miles Davis track, is three fourths with a major third on top: E A D G B. " +
            "Moved up and down the notes of one mode, such voicings make modal jazz's floating sound, and McCoy Tyner built " +
            "much of his piano style on them. Stack seconds instead and you have a cluster: C D E or wider, from soft colour " +
            "in film scoring to the forearm clusters of Henry Cowell.",
        see = CourseSee.Table("VOICINGS", listOf("", "NOTES", "INTERVALS"), listOf(
            listOf("Quartal", "D G C F", "P4 P4 P4"),
            listOf("So What", "E A D G B", "P4 P4 P4 M3"),
            listOf("Cluster", "C D E", "M2 M2"),
            listOf("Tertian", "C E G B", "M3 m3 M3"),
        )),
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

    private val upper = CoursePage(
        "upper", "Upper structures", 5,
        "Play a dominant's shell, the root, 3rd and 7th (C E B♭ for C7), with the left hand and a plain major triad with " +
            "the right, and the triad's three notes become tensions. A D major triad over C7 gives the 9, ♯11 and 13: a " +
            "bright C13♯11. E♭ major gives ♯9, 5 and ♭7. F♯ major gives ♯11, ♭7 and ♭9. A♭ major gives ♭13, root and ♯9, " +
            "an altered sound. A major gives 13, ♭9 and 3, the sound of C13♭9. Pianists name these by the triad's root as a " +
            "Roman numeral from the dominant's root: II, ♭III, ♯IV, ♭VI, VI. One triad shape, five colours of dominant: " +
            "it's a quick way to find altered voicings without thinking about each tension.",
        see = CourseSee.Table("TRIADS OVER C7", listOf("", "TRIAD", "TENSIONS"), UPPER.map { (n, s) ->
            listOf(n, shown(com.betterappmafia.apophenia.core.pitch.PitchClass(s).defaultSpelling().let { if (s == 6) p("F#") else it }), upperTensions(s).joinToString(" "))
        }),
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
