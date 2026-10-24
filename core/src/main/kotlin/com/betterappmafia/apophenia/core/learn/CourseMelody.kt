package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.sound.PlayNote
import kotlin.math.abs
import kotlin.math.sign

/*
 * Course · Melody & songs: steps, leaps and contour; motif and phrase; chord tones and non-chord tones; lines over
 * changes; motion between voices and first-species counterpoint; song form; the blues form. Text written for
 * Apophenia; the classifiers the pages describe (non-chord tones, motion between two voices) are functions here, tested.
 */
internal object CourseMelody : CourseKit() {
    /** A melody of MIDI notes, one every [step] seconds. */
    private fun line(notes: List<Int>, step: Double = 0.45, at: Double = 0.0) = notes.mapIndexed { i, m -> PlayNote(at + i * step, at + i * step + step * 0.95, m, RefSound.VELOCITY) }
    private fun lineOver(r: Int, notes: List<Int>, chords: List<String>, perChord: Int, step: Double = 0.45) =
        piano(CourseHarmony.prog(r, chords, step * perChord) + line(notes.map { r + it }, step))

    // ------------------------------------------------------------------------------------------------- contour

    private val contour = CoursePage(
        "contour", "Steps, leaps and contour", 1,
        "A melody moves by steps (to the next note of the scale, a second away) and by leaps (a third or more). Most " +
            "memorable tunes are mostly steps with a few leaps, and a leap is often followed by a step back the other way, " +
            "filling the gap it opened. The overall shape is the contour: an arch that rises to a high point and settles, " +
            "a descent from a peak, a wave around one note. The highest note usually comes once, near the climax of the " +
            "phrase, which is why it lands. A melody's range, lowest to highest note, decides who can sing it: an octave " +
            "and a bit suits most voices.",
        hear = listOf(
            sound("MOSTLY STEPS · AN ARCH") { r -> piano(line(listOf(0, 2, 4, 5, 7, 9, 7, 5, 4, 2, 0).map { r + it })) },
            sound("A LEAP, THEN STEPS BACK") { r -> piano(line(listOf(0, 9, 7, 5, 4, 2, 0).map { r + it })) },
            sound("MOSTLY LEAPS") { r -> piano(line(listOf(0, 7, 4, 12, 5, 9, 0).map { r + it })) },
        ),
        check = CourseCheck("Mostly steps, or mostly leaps?", listOf(
            option("Steps", "Mostly steps: each note to the next one in the scale.") { r, rnd -> piano(line((if (rnd.nextBoolean()) listOf(0, 2, 4, 5, 4, 2, 4, 2, 0) else listOf(7, 5, 4, 2, 4, 5, 4, 2, 0)).map { r + it })) },
            option("Leaps", "Mostly leaps: thirds, fourths and wider.") { r, rnd -> piano(line((if (rnd.nextBoolean()) listOf(0, 4, 7, 12, 7, 4, 9, 5, 0) else listOf(0, 7, 4, 9, 5, 12, 7, 0)).map { r + it })) },
        )),
        tryIt = CourseTry("THE SCALE A MELODY STEPS THROUGH", RefKind.SCALE, "major", "C"),
        related = listOf(page("motif", "Motif and phrase"), ear("dc-mel-4", "melody · four notes")),
    )

    // ------------------------------------------------------------------------------------------------- motif

    private val MOTIF = listOf(0, 2, 4, 0)

    private val motif = CoursePage(
        "motif", "Motif and phrase", 2,
        "A motif is a short, recognisable idea, a few notes with a shape and a rhythm, and much music is built by doing " +
            "things to one. Repeat it exactly. Repeat it as a sequence, the same shape starting on another note. Turn it " +
            "upside down (inversion), stretch its rhythm, or keep its rhythm and change its notes. A phrase is a complete " +
            "musical sentence, often four bars, ending at a cadence; two phrases often pair as a question and an answer " +
            "(antecedent and consequent), the first ending open on V, the second closing on I. Listen to almost any chorus " +
            "and you'll hear one motif carried through repetition, sequence and a final change that ends it.",
        see = CourseSee.Table("ONE MOTIF, FOUR WAYS (IN C)", listOf("", "NOTES"), listOf(
            listOf("Original", "C D E C"), listOf("Repeated", "C D E C"), listOf("Sequence, up a step", "D E F D"), listOf("Inverted", "C B A C"),
        )),
        hear = listOf(
            sound("MOTIF · SEQUENCE · SEQUENCE") { r -> piano(line((MOTIF + listOf(2, 4, 5, 2) + listOf(4, 5, 7, 4)).map { r + it })) },
            sound("MOTIF · INVERTED") { r -> piano(line((MOTIF + listOf(0, -1, -3, 0)).map { r + it })) },
            sound("QUESTION · ANSWER") { r -> piano(line(listOf(0, 2, 4, 5, 7, 5, 4, 2).map { r + it }) + line(listOf(4, 5, 4, 2, 0, -1, 0, 0).map { r + it }, at = 4.0)) },
        ),
        check = CourseCheck("The motif twice: repeated exactly, or as a sequence?", listOf(
            option("Repeated", "Repeated: the same notes again.") { r, _ -> piano(line((MOTIF + MOTIF).map { r + it })) },
            option("Sequence", "A sequence: the same shape a step higher.") { r, _ -> piano(line((MOTIF + listOf(2, 4, 5, 2)).map { r + it })) },
        )),
        related = listOf(page("contour", "Contour"), page("cadences", "Cadences")),
    )

    // ------------------------------------------------------------------------------------------------- non-chord tones

    /**
     * What a melody note is against the chord under it ([chord] pitch classes), from the notes before and after:
     * a chord tone, or the classic non-chord tones by how they're approached and left.
     */
    internal fun classify(prev: Int, note: Int, next: Int, chord: Set<Int>): String {
        if (Math.floorMod(note, 12) in chord) return "chord tone"
        val into = note - prev
        val out = next - note
        val stepIn = abs(into) in 1..2
        val stepOut = abs(out) in 1..2
        return when {
            into == 0 && stepOut && out < 0 -> "suspension"
            out == 0 -> "anticipation"
            stepIn && stepOut && sign(into.toDouble()) == sign(out.toDouble()) -> "passing tone"
            stepIn && stepOut -> "neighbour tone"
            !stepIn && stepOut -> "appoggiatura"
            stepIn && !stepOut -> "escape tone"
            else -> "unprepared"
        }
    }

    private val passing = CoursePage(
        "nct-passing", "Chord tones, passing and neighbour tones", 2,
        "Against a chord, every melody note is either a chord tone or not. Chord tones sound stable; the others are " +
            "non-chord tones, and they're named by how they're approached and left. A passing tone fills a gap by step: " +
            "over C, E–F–G passes through F. A neighbour tone steps away and comes back: E–F–E (an upper neighbour) or " +
            "E–D–E (a lower one). Both usually fall on weak beats and pass quickly, so the ear hears the chord tones as the " +
            "melody's skeleton and the rest as motion between them. Sing a melody's chord tones alone and you'll usually " +
            "still recognise it.",
        see = CourseSee.Table("OVER A C CHORD", listOf("LINE", "MIDDLE NOTE", "IS"), listOf(
            Triple("E F G", listOf(64, 65, 67), ""), Triple("G F E", listOf(67, 65, 64), ""), Triple("E F E", listOf(64, 65, 64), ""),
            Triple("E D E", listOf(64, 62, 64), ""), Triple("C E G", listOf(60, 64, 67), ""),
        ).map { (t, n, _) -> listOf(t, t.split(" ")[1], classify(n[0], n[1], n[2], setOf(0, 4, 7))) }),
        hear = listOf(
            sound("C, THEN E F G · PASSING") { r -> lineOver(r, listOf(4, 5, 7, 7), listOf("1 3 5"), 4) },
            sound("C, THEN E F E · NEIGHBOUR") { r -> lineOver(r, listOf(4, 5, 4, 4), listOf("1 3 5"), 4) },
        ),
        check = CourseCheck("Over the chord: a passing tone, or a neighbour tone?", listOf(
            option("Passing", "Passing: E F G, through the gap.") { r, rnd -> lineOver(r, if (rnd.nextBoolean()) listOf(4, 5, 7, 7) else listOf(7, 5, 4, 4), listOf("1 3 5"), 4) },
            option("Neighbour", "Neighbour: E F E, out and back.") { r, rnd -> lineOver(r, if (rnd.nextBoolean()) listOf(4, 5, 4, 4) else listOf(7, 9, 7, 7), listOf("1 3 5"), 4) },
        )),
        related = listOf(ear("dc-passing", "chord tones and passing tones"), page("nct-other", "Suspensions and other non-chord tones")),
    )

    private val others = CoursePage(
        "nct-other", "Suspensions and other non-chord tones", 3,
        "The rest of the family. A suspension holds a note over from the previous chord into one where it clashes, then " +
            "resolves it down by step: hold C over a G chord and let it fall to B, the 4–3 suspension (others are 7–6 and " +
            "9–8). An appoggiatura arrives by leap on a strong beat and resolves by step, a leaning, expressive sigh. An " +
            "escape tone steps away from a chord tone and leaps off to the next. An anticipation arrives a little early: " +
            "the next chord's note, sounded before its chord comes. A pedal is a note held, usually in the bass, while the " +
            "chords above it change and clash with it before coming home. Suspensions and appoggiaturas fall on the beat, " +
            "which is why they're felt more than passing tones.",
        see = CourseSee.Table("IN C", listOf("LINE", "OVER", "MIDDLE NOTE IS"), listOf(
            Triple("C C B", "C, then G", listOf(72, 72, 71) to setOf(7, 11, 2)), Triple("G D C", "C", listOf(67, 74, 72) to setOf(0, 4, 7)),
            Triple("E F A", "C", listOf(64, 65, 69) to setOf(0, 4, 7)), Triple("D C C", "G, then C", listOf(62, 60, 60) to setOf(7, 11, 2)),
        ).map { (t, over, nc) -> listOf(t, over, classify(nc.first[0], nc.first[1], nc.first[2], nc.second)) }),
        hear = listOf(
            sound("4–3 SUSPENSION") { r -> piano(CourseHarmony.prog(r, listOf("1 3 5", "5 7 2", "5 7 2", "1 3 5"), 1.0) + line(listOf(12, 12, 11, 12).map { r + it }, step = 1.0)) },
            sound("APPOGGIATURA") { r -> piano(CourseHarmony.prog(r, listOf("1 3 5"), 2.4) + line(listOf(7, 14, 12).map { r + it }, step = 0.8)) },
            sound("PEDAL · C UNDER C F/C G/C C") { r -> piano(listOf(listOf(4, 7, 12), listOf(5, 9, 12), listOf(2, 7, 11), listOf(4, 7, 12)).flatMapIndexed { i, c -> RefSound.block(c.map { r + it }, at = i * 1.2, length = 1.15) } + note(0.0, 4.8, r - 24)) },
        ),
        check = CourseCheck("Over the G chord: does the C fall to B (a suspension), or was B there all along?", listOf(
            option("Suspension", "A suspension: C held over, then falling to B.") { r, _ -> piano(CourseHarmony.prog(r, listOf("1 3 5", "5 7 2", "5 7 2", "1 3 5"), 1.0) + line(listOf(12, 12, 11, 12).map { r + it }, step = 1.0)) },
            option("Chord tone", "No suspension: B arrives with its chord.") { r, _ -> piano(CourseHarmony.prog(r, listOf("1 3 5", "5 7 2", "5 7 2", "1 3 5"), 1.0) + line(listOf(12, 11, 11, 12).map { r + it }, step = 1.0)) },
        )),
        related = listOf(page("sus", "Suspended chords"), page("nct-passing", "Passing and neighbour tones")),
    )

    // ------------------------------------------------------------------------------------------------- lines over changes

    private val changes = CoursePage(
        "changes", "Lines over changes", 3,
        "Improvising or writing a line over a progression, the strong beats are where it's heard against the harmony, so " +
            "landing chord tones there, especially the 3rds and 7ths, makes the line spell the changes on its own. Between " +
            "targets anything can happen, and three approaches are especially useful. A chromatic approach comes from a " +
            "half step below (D♯ to E). A scale approach comes by step from above or below. An enclosure surrounds the " +
            "target first, a step above then a half step below (F, D♯, E) or the other way round, the bebop signature. " +
            "Over Dm7–G7–Cmaj7, a line that lands F, then B, then E on the downbeats follows the guide tones and sounds " +
            "\"inside\" however much it wanders between them.",
        hear = listOf(
            sound("TARGETING 3RDS OVER ii–V–I") { r -> lineOver(r, listOf(5, 4, 2, 0, -1, 2, 5, 7, 4, 7, 11, 12), listOf("2 4 6 1", "5 7 2 4", "1 3 5 7"), 4) },
            sound("AN ENCLOSURE · F D♯ E") { r -> lineOver(r, listOf(5, 3, 4, 4), listOf("1 3 5 7"), 4, step = 0.5) },
        ),
        check = CourseCheck("Into E over Cmaj7: a chromatic approach (D♯ E), or an enclosure (F D♯ E)?", listOf(
            option("Approach", "A chromatic approach: a half step below.") { r, _ -> lineOver(r, listOf(7, 3, 4, 4), listOf("1 3 5 7"), 4, step = 0.5) },
            option("Enclosure", "An enclosure: above, then below.") { r, _ -> lineOver(r, listOf(5, 3, 4, 4), listOf("1 3 5 7"), 4, step = 0.5) },
        )),
        related = listOf(page("jazz-251", "ii–V–I and guide tones"), ref(RefKind.SCALE, "bebop-dominant", "Bebop dominant", "G")),
    )

    // ------------------------------------------------------------------------------------------------- counterpoint

    /** How two voices move from ([a1] upper, [b1] lower) to ([a2], [b2]). */
    internal fun motion(a1: Int, b1: Int, a2: Int, b2: Int): String {
        val da = a2 - a1; val db = b2 - b1
        return when {
            da == 0 && db == 0 -> "static"
            da == 0 || db == 0 -> "oblique"
            sign(da.toDouble()) != sign(db.toDouble()) -> "contrary"
            a2 - b2 == a1 - b1 -> "parallel"
            else -> "similar"
        }
    }
    /** Parallel or consecutive perfect fifths or octaves (or unisons) between two voices moving the same way. */
    internal fun parallelPerfect(a1: Int, b1: Int, a2: Int, b2: Int): Boolean {
        val i1 = Math.floorMod(a1 - b1, 12); val i2 = Math.floorMod(a2 - b2, 12)
        val m = motion(a1, b1, a2, b2)
        return (m == "parallel" || m == "similar") && i1 == i2 && i1 in setOf(0, 7)
    }

    internal val CANTUS = listOf(60, 62, 65, 64, 62, 60)
    internal val COUNTER = listOf(67, 71, 69, 72, 71, 72)
    internal val CONSONANT = setOf(0, 3, 4, 7, 8, 9)

    private val motionPage = CoursePage(
        "counterpoint-motion", "Motion between voices", 4,
        "When two lines move together, they move in one of four ways. In contrary motion they go opposite ways; in " +
            "oblique motion one holds while the other moves; in similar motion both go the same way by different amounts; " +
            "in parallel motion both go the same way by the same interval. Contrary and oblique motion keep the lines " +
            "independent. Parallel thirds and sixths are the sweet sound of a harmony vocal. Parallel fifths and octaves " +
            "are what common-practice voice leading avoids: the two voices fuse into one and the texture thins, which is " +
            "exactly why rock power chords and medieval organum use them on purpose. The rule belongs to a style, not to " +
            "music in general.",
        see = CourseSee.Table("FROM C UNDER G", listOf("TO", "MOTION", "PARALLEL 5THS/8VES"), listOf(
            Triple("D under A", 69, 62), Triple("E under A", 69, 64), Triple("B under G", 67, 59), Triple("D under F", 65, 62), Triple("A under C", 72, 57),
        ).map { (t, a, b) -> listOf(t, motion(67, 60, a, b), if (parallelPerfect(67, 60, a, b)) "yes" else "no") }),
        hear = listOf(
            sound("PARALLEL THIRDS") { r -> piano(line(listOf(4, 5, 7, 9, 7, 5, 4).map { r + it }) + line(listOf(0, 2, 4, 5, 4, 2, 0).map { r + it })) },
            sound("PARALLEL FIFTHS") { r -> piano(line(listOf(7, 9, 11, 12, 11, 9, 7).map { r + it }) + line(listOf(0, 2, 4, 5, 4, 2, 0).map { r + it })) },
            sound("CONTRARY MOTION") { r -> piano(line(listOf(12, 11, 9, 7, 9, 11, 12).map { r + it }) + line(listOf(0, 2, 4, 5, 4, 2, 0).map { r + it })) },
        ),
        check = CourseCheck("Two voices: parallel thirds, or parallel fifths?", listOf(
            option("Thirds", "Parallel thirds: sweet, two lines in harmony.") { r, _ -> piano(line(listOf(4, 5, 7, 9, 7).map { r + it }) + line(listOf(0, 2, 4, 5, 4).map { r + it })) },
            option("Fifths", "Parallel fifths: hollow, the two lines fused.") { r, _ -> piano(line(listOf(7, 9, 11, 12, 11).map { r + it }) + line(listOf(0, 2, 4, 5, 4).map { r + it })) },
        )),
        related = listOf(page("species", "First-species counterpoint"), page("triad-build", "Power chords")),
    )

    private val species = CoursePage(
        "species", "First-species counterpoint", 4,
        "Species counterpoint is the classic way of learning to write independent lines, set out by Johann Joseph Fux in " +
            "Gradus ad Parnassum (1725). In first species a new line is written note against note over a given melody, the " +
            "cantus firmus, using only consonances: unisons, octaves, fifths, thirds and sixths. It starts and ends on a " +
            "perfect consonance, prefers contrary motion, never moves into a fifth or octave by parallel motion, and " +
            "closes with the upper voice stepping up from the leading tone to the tonic. The example here, over C D F E D C, " +
            "is G B A C B C: a fifth to start, three sixths and a third between, an octave to end. Later species add two, four and " +
            "syncopated notes against each one.",
        see = CourseSee.Table("THE EXAMPLE", listOf("CANTUS", "COUNTER", "INTERVAL", "MOTION IN"), CANTUS.indices.map { i ->
            val names = listOf("C", "D♭", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B")
            val iv = mapOf(0 to "P8", 3 to "m3", 4 to "M3", 7 to "P5", 8 to "m6", 9 to "M6").getValue(Math.floorMod(COUNTER[i] - CANTUS[i], 12))
            listOf(names[CANTUS[i] % 12], names[COUNTER[i] % 12], iv, if (i == 0) "—" else motion(COUNTER[i - 1], CANTUS[i - 1], COUNTER[i], CANTUS[i]))
        }),
        hear = listOf(
            sound("THE CANTUS FIRMUS") { r -> piano(line(CANTUS.map { it - 60 + r }, step = 0.8)) },
            sound("BOTH VOICES") { r -> piano(line(CANTUS.map { it - 60 + r }, step = 0.8) + line(COUNTER.map { it - 60 + r }, step = 0.8)) },
        ),
        check = CourseCheck("Two voices: good counterpoint, or with parallel fifths?", listOf(
            option("Good", "Good: consonances, mostly contrary motion.") { r, _ -> piano(line(CANTUS.map { it - 60 + r }, step = 0.7) + line(COUNTER.map { it - 60 + r }, step = 0.7)) },
            option("Parallel fifths", "Parallel fifths: the upper voice shadows the lower a fifth up.") { r, _ -> piano(line(CANTUS.map { it - 60 + r }, step = 0.7) + line(CANTUS.map { it - 60 + r + 7 }, step = 0.7)) },
        )),
        related = listOf(page("counterpoint-motion", "Motion between voices"), page("iv-consonance", "Consonance and dissonance")),
    )

    // ------------------------------------------------------------------------------------------------- form

    internal val AABA = listOf("A" to 8, "A" to 8, "B" to 8, "A" to 8)

    private val form = CoursePage(
        "form", "Song form", 1,
        "Songs are built from sections, labelled by letter or by name. A verse carries the story and changes its words " +
            "each time; a chorus carries the title and returns with the same words; a pre-chorus builds from one to the " +
            "other; a bridge (or middle eight) brings something new, often new chords, before the last chorus; an intro " +
            "and outro open and close. Verse–chorus form, roughly intro, verse, pre-chorus, chorus, verse, pre-chorus, " +
            "chorus, bridge, chorus, outro, runs most pop and rock. The 32-bar AABA form of Tin Pan Alley and jazz standards " +
            "has four 8-bar sections, the third (B) the bridge. Letters mark sameness: two sections with the same letter " +
            "share their music.",
        see = CourseSee.Table("TWO COMMON FORMS", listOf("FORM", "SECTIONS"), listOf(
            listOf("AABA (32 bars)", AABA.joinToString(" ") { (l, b) -> "$l$b" } + " = ${AABA.sumOf { it.second }}"),
            listOf("Verse–chorus", "Intro · Verse · Pre · Chorus · Verse · Pre · Chorus · Bridge · Chorus · Outro"),
            listOf("12-bar blues", "AAB lyric over twelve bars, repeated"),
        )),
        related = listOf(page("blues-form", "The twelve-bar blues"), page("motif", "Motif and phrase")),
    )

    internal val TWELVE_BAR = listOf("I7", "I7", "I7", "I7", "IV7", "IV7", "I7", "I7", "V7", "IV7", "I7", "V7")
    internal val QUICK_CHANGE = TWELVE_BAR.toMutableList().also { it[1] = "IV7" }
    private val BLUES_TOKENS = mapOf("I7" to "1 3 5 b7", "IV7" to "4 6 1 b3", "V7" to "5 7 2 4")
    private fun blues(r: Int, bars: List<String>, step: Double = 0.7) = piano(CourseHarmony.prog(r, bars.map { BLUES_TOKENS.getValue(it) }, step))

    private val bluesForm = CoursePage(
        "blues-form", "The twelve-bar blues", 2,
        "The twelve-bar blues is three four-bar lines: four bars of I, two of IV and two of I, then V, IV, I and a " +
            "turnaround on V back to the top. Every chord is usually a dominant seventh, which is one of the things that " +
            "makes it the blues: in A, A7 D7 E7. The quick change moves to IV in bar 2 and back. The lyric often follows the " +
            "lines as AAB: a line, the same line again, and an answer. Minor blues uses i, iv and v (or V), often with " +
            "♭VI7 to V7 in bars 9 and 10. Jazz blues fills the same twelve bars with ii–V's and passing chords, but the " +
            "outline, I to IV to I to V and home, stays recognisable under all of it.",
        see = CourseSee.Table("TWELVE BARS IN A", listOf("BARS", "CHORDS"), listOf(0, 4, 8).map { s ->
            val names = mapOf("I7" to "A7", "IV7" to "D7", "V7" to "E7")
            listOf("${s + 1}–${s + 4}", TWELVE_BAR.subList(s, s + 4).joinToString(" · ") { "${it} ${names.getValue(it)}" })
        }),
        hear = listOf(
            sound("TWELVE BARS") { r -> blues(r, TWELVE_BAR) },
            sound("WITH THE QUICK CHANGE") { r -> blues(r, QUICK_CHANGE) },
        ),
        check = CourseCheck("Bar 2: does it stay on I, or make the quick change to IV?", listOf(
            option("Stays on I", "It stays: four bars of I.") { r, _ -> blues(r, TWELVE_BAR.take(5)) },
            option("Quick change", "The quick change: IV in bar 2.") { r, _ -> blues(r, QUICK_CHANGE.take(5)) },
        )),
        tryIt = CourseTry("THE BLUES SCALE OVER IT", RefKind.SCALE, "minor-blues", "A"),
        related = listOf(page("blues", "The blues scale"), ref(RefKind.CHORD, "7", "A7", "A")),
    )

    val topics = listOf(
        CourseTopic("contour", "Melody: steps, leaps, contour", "the shape of a line", 1, listOf(contour)),
        CourseTopic("motif", "Motif and phrase", "small ideas, and how they grow", 2, listOf(motif)),
        CourseTopic("nct", "Chord tones and non-chord tones", "passing, neighbour, suspension, appoggiatura", 2, listOf(passing, others)),
        CourseTopic("changes", "Lines over changes", "targeting chord tones, approach notes", 3, listOf(changes)),
        CourseTopic("counterpoint", "Counterpoint", "two lines at once, species, voice leading", 4, listOf(motionPage, species)),
        CourseTopic("form", "Song form", "verse, chorus, bridge, pre-chorus", 1, listOf(form)),
        CourseTopic("blues", "The blues form", "twelve bars and its variants", 2, listOf(bluesForm)),
    )
}
