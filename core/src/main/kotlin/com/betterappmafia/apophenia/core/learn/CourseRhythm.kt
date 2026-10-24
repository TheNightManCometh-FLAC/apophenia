package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.rhythm.Duration
import com.betterappmafia.apophenia.core.rhythm.Rational
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import com.betterappmafia.apophenia.core.sound.PlayNote
import com.betterappmafia.apophenia.core.tools.DrumHit
import com.betterappmafia.apophenia.core.tools.DrumVoice

/*
 * Course · Rhythm & time: pulse and tempo, note values and rests, meter (simple and compound), dots, ties and
 * syncopation, swing and feel, tuplets, odd and changing meters, polyrhythm and metric modulation. Text written for
 * Apophenia; bars are RNote lists drawn on a percussion staff and played on the drum kit by EarRhythm.drums.
 */
internal object CourseRhythm : CourseKit() {
    private val w = Duration.WHOLE
    private val h = Duration.HALF
    private val q = Duration.QUARTER
    private val e = Duration.EIGHTH
    private val s = Duration.SIXTEENTH
    private fun n(d: Duration) = RNote(d)
    private fun r(d: Duration) = RNote(d, rest = true)
    private fun t3(d: Duration) = d.inTuplet(3, 2)

    internal val FOUR = TimeSignature.of(4, 4)
    internal val THREE = TimeSignature.of(3, 4)
    internal val SIX_EIGHT = TimeSignature(listOf(3, 3), 8)
    internal val SEVEN_EIGHT = TimeSignature(listOf(2, 2, 3), 8)
    internal val FIVE_FOUR = TimeSignature.of(5, 4)

    /** A count-in bar of clicks, then the bar on the snare with the beats on the hi-hat. */
    internal fun bar(notes: List<RNote>, meter: TimeSignature = FOUR, bpm: Int = 80, swing: Boolean = false, count: Boolean = true): EarAudio {
        val start = if (count) EarRhythm.barSeconds(meter, bpm) else 0.0
        return EarAudio(emptyList(), drums = (if (count) EarRhythm.countIn(meter, bpm) else emptyList()) + EarRhythm.drums(notes, meter, bpm, start, swing))
    }
    /** Clicks on every beat for [beats] beats. */
    internal fun clicks(bpm: Int, beats: Int, at: Double = 0.0, accentEvery: Int = 4) = (0 until beats).map { i ->
        DrumAt(at + i * 60.0 / bpm, DrumVoice.CROSS_STICK, if (i % accentEvery == 0) DrumHit.ACCENT else DrumHit.HIT)
    }
    /** Six eighths at [bpm] (eighths per minute ÷ 2), accented as 6/8 (1 and 4) or as 3/4 (1, 3, 5). */
    internal fun sixEighths(compound: Boolean, bpm: Int = 100) = EarAudio(emptyList(), drums = (0 until 12).map { i ->
        val accent = if (compound) i % 3 == 0 else i % 2 == 0
        DrumAt(i * 30.0 / bpm, if (i % 6 == 0) DrumVoice.KICK else DrumVoice.SNARE, if (accent) DrumHit.ACCENT else DrumHit.GHOST)
    })

    private fun row(label: String, notes: List<RNote>) = label to notes

    // ------------------------------------------------------------------------------------------------- pulse

    private val pulse = CoursePage(
        "pulse", "Pulse and tempo", 1,
        "Under most music runs a steady pulse, the beat you tap your foot to. Its speed is the tempo, counted in beats per " +
            "minute (BPM): at 60 BPM each beat lasts one second, at 120 half a second. Doubling the BPM halves every beat. " +
            "Classical scores name tempos in Italian, roughly from slow to fast: largo, adagio, andante (a walking pace), " +
            "moderato, allegro, presto, and the ranges given for each vary from source to source, so a BPM is the only " +
            "exact marking. A beat can also be felt at half or double speed: a drummer playing half-time puts the snare on " +
            "3 instead of 2 and 4, and the same tempo suddenly feels twice as slow.",
        see = CourseSee.Table("HOW LONG A BEAT LASTS", listOf("BPM", "ONE BEAT", "A BAR OF 4"), listOf(60, 80, 90, 120, 140, 174).map { b ->
            listOf("$b", "${Acoustics.fmt(0, 60000.0 / b)} ms", "${Acoustics.fmt(2, 240.0 / b)} s")
        }),
        hear = listOf(60, 90, 120).map { b -> sound("$b BPM") { EarAudio(emptyList(), drums = clicks(b, 8)) } } +
            sound("THE SAME GROOVE, HALF-TIME") {
                val beat = 60.0 / 90
                EarAudio(emptyList(), drums = (0 until 8).flatMap { i -> listOfNotNull(
                    DrumAt(i * beat, DrumVoice.HAT, DrumHit.HIT), DrumAt(i * beat + beat / 2, DrumVoice.HAT, DrumHit.HIT),
                    if (i % 4 == 0) DrumAt(i * beat, DrumVoice.KICK, DrumHit.ACCENT) else null,
                    if (i % 4 == 2) DrumAt(i * beat, DrumVoice.SNARE, DrumHit.ACCENT) else null,
                ) })
            },
        check = CourseCheck("Two runs of clicks. Is the second faster or slower?", listOf(
            option("Faster", "Faster: the second run is about a quarter quicker.") { _, rnd -> val b = 70 + rnd.nextInt(30); EarAudio(emptyList(), drums = clicks(b, 4) + clicks(b * 5 / 4, 4, at = 4 * 60.0 / b + 0.6)) },
            option("Slower", "Slower: the second run is about a fifth slower.") { _, rnd -> val b = 80 + rnd.nextInt(30); EarAudio(emptyList(), drums = clicks(b, 4) + clicks(b * 4 / 5, 4, at = 4 * 60.0 / b + 0.6)) },
        )),
        related = listOf(page("values", "Note values")),
    )

    // ------------------------------------------------------------------------------------------------- values

    private val values = CoursePage(
        "values", "Note values", 1,
        "Written rhythm measures each note against the beat. In 4/4 a whole note lasts four beats, a half note two, a " +
            "quarter note one, an eighth note half a beat and a sixteenth a quarter of a beat: each value is half the one " +
            "before. Eighths and shorter have flags, and when several fall in one beat their flags join into beams, so the " +
            "beat stays visible: four sixteenths under one beam are one beat's worth. At 60 BPM a quarter note lasts a " +
            "second and an eighth half a second; at 120 everything takes half as long. The values are relative; only the " +
            "tempo turns them into seconds.",
        see = CourseSee.Rhythm("ONE BAR OF 4/4, FIVE WAYS", FOUR, 80, listOf(
            row("WHOLE", listOf(n(w))), row("HALVES", List(2) { n(h) }), row("QUARTERS", List(4) { n(q) }),
            row("EIGHTHS", List(8) { n(e) }), row("SIXTEENTHS", List(16) { n(s) }),
        )),
        hear = listOf(sound("QUARTERS · EIGHTHS · SIXTEENTHS") {
            EarAudio(emptyList(), drums = EarRhythm.countIn(FOUR, 80) + listOf(List(4) { n(q) }, List(8) { n(e) }, List(16) { n(s) }).flatMapIndexed { i, b -> EarRhythm.drums(b, FOUR, 80, (i + 1) * 3.0) })
        }),
        check = CourseCheck("Over the beat: quarters, eighths or sixteenths?", listOf(
            option("Quarters", "Quarters: one note a beat.") { _, _ -> bar(List(4) { n(q) }) },
            option("Eighths", "Eighths: two a beat.") { _, _ -> bar(List(8) { n(e) }) },
            option("Sixteenths", "Sixteenths: four a beat.") { _, _ -> bar(List(16) { n(s) }) },
        )),
        related = listOf(page("rests", "Rests"), ear("rh-simple", "rhythm · simple meters")),
    )

    private val rests = CoursePage(
        "rests", "Rests", 1,
        "Every note value has a rest of the same length, and the silence counts exactly as much as a note. A bar of 4/4 " +
            "always adds up to four beats of notes and rests together. Rests are written to show the beat, just as beams " +
            "are: in 4/4 a half rest sits on beats 1–2 or 3–4, not across the middle, and an eighth rest before an eighth " +
            "note (the \"and\" alone) is the off-beat figure of reggae skanks and funk guitar. A whole rest also means " +
            "\"silent for the whole bar\" in any meter. Reading rests well is most of reading rhythm, because the notes " +
            "that follow them are where the groove sits.",
        see = CourseSee.Rhythm("NOTES AND RESTS IN 4/4", FOUR, 80, listOf(
            row("ON THE BEAT", listOf(n(q), r(q), n(q), r(q))), row("OFF THE BEAT", List(4) { listOf(r(e), n(e)) }.flatten()),
            row("MIXED", listOf(n(e), n(e), r(q), n(e.dotted()), n(s), r(q))),
        )),
        hear = listOf(
            sound("ON THE BEAT") { bar(listOf(n(q), r(q), n(q), r(q))) },
            sound("OFF THE BEAT") { bar(List(4) { listOf(r(e), n(e)) }.flatten()) },
        ),
        check = CourseCheck("On the beats, or between them?", listOf(
            option("On", "On the beats: quarters with rests.") { _, _ -> bar(listOf(n(q), r(q), n(q), n(q))) },
            option("Between", "Between: each note after an eighth rest.") { _, _ -> bar(List(4) { listOf(r(e), n(e)) }.flatten()) },
        )),
        related = listOf(ear("rh-tap", "tap it back")),
    )

    // ------------------------------------------------------------------------------------------------- meter

    private val signatures = CoursePage(
        "meter-signatures", "Time signatures", 2,
        "Beats group into bars, and the time signature says how. The top number counts what's in a bar and the bottom names " +
            "the note value being counted: 4/4 is four quarter notes, 3/4 three quarters, 2/4 two. The grouping makes some " +
            "beats strong: in 4/4 beat 1 is strongest, 3 next, 2 and 4 weak (which is exactly where rock and soul put the " +
            "snare, against the grain); in 3/4 the 1 is strong and 2 and 3 weak, the lilt of a waltz. 4/4 is so common it " +
            "has its own sign, C, and 2/2 (cut time, ¢) counts two half notes to a bar, the feel of a march or a fast polka.",
        see = CourseSee.Rhythm("ONE BAR EACH", FOUR, 80, listOf(
            row("4/4", List(4) { n(q) }),
        ), extra = listOf(Triple("3/4", THREE, List(3) { n(q) }), Triple("2/4", TimeSignature.of(2, 4), List(2) { n(q) }))),
        hear = listOf(
            sound("IN FOUR") { EarAudio(emptyList(), drums = clicks(100, 12, accentEvery = 4)) },
            sound("IN THREE") { EarAudio(emptyList(), drums = clicks(100, 12, accentEvery = 3)) },
        ),
        check = CourseCheck("In three, or in four?", listOf(
            option("Three", "In three: the accent every third beat.") { _, rnd -> EarAudio(emptyList(), drums = clicks(90 + rnd.nextInt(30), 12, accentEvery = 3)) },
            option("Four", "In four: the accent every fourth beat.") { _, rnd -> EarAudio(emptyList(), drums = clicks(90 + rnd.nextInt(30), 12, accentEvery = 4)) },
        )),
        related = listOf(page("meter-compound", "Simple and compound")),
    )

    private val compound = CoursePage(
        "meter-compound", "Simple and compound", 2,
        "In simple meters each beat divides in two: 3/4 is three beats of two eighths. In compound meters each beat divides " +
            "in three, and the signature counts the divisions: 6/8 is two beats, each a dotted quarter of three eighths; " +
            "9/8 is three such beats and 12/8 four. So 6/8 and 3/4 hold the same six eighths and sound different: 6/8 " +
            "accents 1 and 4 (ONE two three FOUR five six), 3/4 accents 1, 3 and 5. 12/8 is the rolling feel of slow blues " +
            "and doo-wop ballads; 6/8 the jig and many folk songs. In compound time the beaming shows the beats: eighths " +
            "beamed in threes.",
        see = CourseSee.Rhythm("SIX EIGHTHS TWO WAYS", SIX_EIGHT, 60, listOf(row("6/8", List(6) { n(e) })), extra = listOf(Triple("3/4", THREE, List(6) { n(e) }))),
        hear = listOf(
            sound("6/8 · IN TWO") { sixEighths(true) },
            sound("3/4 · IN THREE") { sixEighths(false) },
        ),
        check = CourseCheck("Six eighths: 6/8, or 3/4?", listOf(
            option("6/8", "6/8: two groups of three.") { _, _ -> sixEighths(true) },
            option("3/4", "3/4: three groups of two.") { _, _ -> sixEighths(false) },
        )),
        related = listOf(page("tuplets", "Tuplets"), ear("rh-simple", "rhythm · simple meters")),
    )

    // ------------------------------------------------------------------------------------------------- dots, ties, syncopation

    private val dots = CoursePage(
        "dots-ties", "Dots and ties", 2,
        "A dot after a note adds half its value: a dotted half is three beats, a dotted quarter a beat and a half (three " +
            "eighths), a dotted eighth three sixteenths. A second dot adds half as much again. A tie joins two notes of the " +
            "same pitch into one sound lasting both values, and it's what you use when a note crosses a bar line or when " +
            "writing it as one note would hide where the beat is. The dotted quarter followed by an eighth (ta–a–ti) is one " +
            "of the most common figures in music, the dotted eighth and sixteenth its quicker, jumpier cousin.",
        see = CourseSee.Rhythm("DOTTED FIGURES IN 4/4", FOUR, 80, listOf(
            row("DOTTED HALF", listOf(n(h.dotted()), n(q))), row("DOTTED QUARTER", listOf(n(q.dotted()), n(e), n(q.dotted()), n(e))),
            row("DOTTED EIGHTH", List(4) { listOf(n(e.dotted()), n(s)) }.flatten()),
        )),
        hear = listOf(
            sound("DOTTED QUARTER + EIGHTH") { bar(listOf(n(q.dotted()), n(e), n(q.dotted()), n(e))) },
            sound("DOTTED EIGHTH + SIXTEENTH") { bar(List(4) { listOf(n(e.dotted()), n(s)) }.flatten()) },
        ),
        check = CourseCheck("Even eighths, or dotted eighth and sixteenth?", listOf(
            option("Even", "Even eighths.") { _, _ -> bar(List(8) { n(e) }) },
            option("Dotted", "Dotted: long-short, three sixteenths and one.") { _, _ -> bar(List(4) { listOf(n(e.dotted()), n(s)) }.flatten()) },
        )),
        related = listOf(page("syncopation", "Syncopation")),
    )

    private val syncopation = CoursePage(
        "syncopation", "Syncopation", 2,
        "Syncopation puts the weight where the meter doesn't: on an off-beat, or on a note that starts between beats and " +
            "holds across the next one. Eighth–quarter–eighth (ti–ta–ti) lands the long note on the \"and\" of 1. A note " +
            "pushed an eighth early onto the \"and\" of 4 and tied over the bar line, an anticipation or push, is the " +
            "classic pop and funk lift into a new bar. Grouping eight eighths as 3 + 3 + 2 gives the pattern often called " +
            "tresillo, Afro-Cuban in origin and heard across Latin music, reggaeton and countless pop beats. The pulse doesn't change; the " +
            "accents fight it, and that tension is what makes music feel like it moves.",
        see = CourseSee.Rhythm("SYNCOPATED FIGURES", FOUR, 90, listOf(
            row("TI–TA–TI", listOf(n(e), n(q), n(e), n(e), n(q), n(e))),
            row("3 + 3 + 2", listOf(n(q.dotted()), n(q.dotted()), n(q))),
            row("OFF-BEATS", listOf(r(e), n(e), r(e), n(e), n(e), n(q), n(e))),
        )),
        hear = listOf(
            sound("ON THE BEAT") { bar(List(4) { n(q) }, bpm = 90) },
            sound("3 + 3 + 2") { bar(listOf(n(q.dotted()), n(q.dotted()), n(q)), bpm = 90) },
            sound("TI–TA–TI") { bar(listOf(n(e), n(q), n(e), n(e), n(q), n(e)), bpm = 90) },
        ),
        check = CourseCheck("Straight on the beats, or syncopated?", listOf(
            option("On the beats", "On the beats: every note with the hi-hat.") { _, _ -> bar(listOf(n(q), n(q), n(e), n(e), n(q)), bpm = 90) },
            option("Syncopated", "Syncopated: long notes start between beats.") { _, rnd -> bar(if (rnd.nextBoolean()) listOf(n(q.dotted()), n(q.dotted()), n(q)) else listOf(n(e), n(q), n(e), n(e), n(q), n(e)), bpm = 90) },
        )),
        related = listOf(page("swing", "Swing and feel"), ear("rh-simple", "rhythm · simple meters")),
    )

    // ------------------------------------------------------------------------------------------------- swing

    /** Where a swung offbeat eighth falls, as a fraction of the beat (EarRhythm.drums swings at 2:1). */
    internal const val SWING = 2.0 / 3

    private val swing = CoursePage(
        "swing", "Swing and feel", 2,
        "Swing plays pairs of eighths long–short instead of even. At a medium tempo the first of each pair takes about two " +
            "thirds of the beat and the second one third, the triplet feel of jazz, written either as plain eighths with " +
            "\"swing\" at the top or as a quarter-and-eighth triplet. The ratio isn't fixed: players swing harder at slow " +
            "tempos and nearly straight at fast ones. A shuffle is swung eighths with a heavy, bouncing triplet pulse, the " +
            "blues and boogie feel. Feel also means where you sit on the beat: laid back, a hair behind it, or on top, a " +
            "hair ahead. Neither is written down; both are learnt by ear.",
        hear = listOf(
            sound("STRAIGHT") { bar(List(8) { n(e) }, bpm = 100) },
            sound("SWUNG") { bar(List(8) { n(e) }, bpm = 100, swing = true) },
        ),
        check = CourseCheck("Straight or swung?", listOf(
            option("Straight", "Straight: the eighths are even.") { _, rnd -> bar(EarRhythm.swingBar(rnd), bpm = 100) },
            option("Swung", "Swung: long–short, about 2:1.") { _, rnd -> bar(EarRhythm.swingBar(rnd), bpm = 100, swing = true) },
        )),
        related = listOf(ear("rh-swing", "straight or swung"), page("tuplets", "Tuplets")),
    )

    // ------------------------------------------------------------------------------------------------- tuplets

    private val tuplets = CoursePage(
        "tuplets", "Tuplets", 3,
        "A tuplet fits a number of notes into the time normally taken by a different number. A triplet is three in the time " +
            "of two: three triplet eighths fill one beat, where two plain eighths would. It's written with a 3 over the " +
            "group (and a bracket if the notes aren't beamed together), and as a ratio 3:2. Quintuplets fit five sixteenths " +
            "into a beat (5:4), sextuplets six (6:4). Triplets are the bridge between simple and compound time: a bar of " +
            "4/4 in triplet eighths sounds like 12/8. Tuplets can nest, a triplet inside a triplet, and Apophenia's scores " +
            "write any ratio.",
        see = CourseSee.Rhythm("TUPLETS IN 4/4", FOUR, 72, listOf(
            row("EIGHTHS", List(8) { n(e) }), row("TRIPLETS", List(12) { n(t3(e)) }),
            row("QUARTER TRIPLETS", listOf(n(t3(q)), n(t3(q)), n(t3(q)), n(h))),
            row("QUINTUPLETS", List(5) { n(s.inTuplet(5, 4)) } + listOf(n(q), n(h))),
        )),
        hear = listOf(
            sound("EIGHTHS, THEN TRIPLETS") { EarAudio(emptyList(), drums = EarRhythm.countIn(FOUR, 72) + EarRhythm.drums(List(8) { n(e) }, FOUR, 72, 60.0 / 72 * 4) + EarRhythm.drums(List(12) { n(t3(e)) }, FOUR, 72, 60.0 / 72 * 8)) },
            sound("QUARTER-NOTE TRIPLETS") { bar(listOf(n(t3(q)), n(t3(q)), n(t3(q)), n(t3(q)), n(t3(q)), n(t3(q))), bpm = 72) },
        ),
        check = CourseCheck("Two a beat, or three?", listOf(
            option("Eighths", "Eighths: two a beat.") { _, _ -> bar(List(8) { n(e) }, bpm = 72) },
            option("Triplets", "Triplets: three a beat.") { _, _ -> bar(List(12) { n(t3(e)) }, bpm = 72) },
        )),
        related = listOf(ear("rh-tuplets", "triplets and tuplets"), page("poly", "Polyrhythm")),
    )

    // ------------------------------------------------------------------------------------------------- odd meters

    private val odd = CoursePage(
        "odd", "Odd meters", 4,
        "Meters with five, seven, eleven or thirteen in a bar can't split evenly into twos or threes, so they're felt as " +
            "uneven groups. 5/4 is felt as 3 + 2 or 2 + 3, the meter of the Dave Brubeck Quartet's \"Take Five\"; 7/8 as 2 + 2 " +
            "+ 3, 3 + 2 + 2 or 2 + 3 + 2, each with its own lilt; 9/8 can be three threes or 2 + 2 + 2 + 3, the second " +
            "common in Balkan and Turkish music. The grouping is shown by beaming and sometimes in the signature itself " +
            "(2+2+3/8). Counting helps at first (\"one-two, one-two, one-two-three\"); after that it's a groove like any " +
            "other.",
        see = CourseSee.Rhythm("ODD METERS BY GROUP", SEVEN_EIGHT, 100, listOf(row("2+2+3/8", List(7) { n(e) })), extra = listOf(
            Triple("3+2+2/8", TimeSignature(listOf(3, 2, 2), 8), List(7) { n(e) }), Triple("5/4", FIVE_FOUR, List(5) { n(q) }),
            Triple("2+2+2+3/8", TimeSignature(listOf(2, 2, 2, 3), 8), List(9) { n(e) }),
        )),
        hear = listOf(
            sound("7/8 AS 2 + 2 + 3") { bar(List(7) { n(e) }, SEVEN_EIGHT, bpm = 100) },
            sound("7/8 AS 3 + 2 + 2") { bar(List(7) { n(e) }, TimeSignature(listOf(3, 2, 2), 8), bpm = 100) },
            sound("5/4") { bar(listOf(n(q), n(q), n(q), n(q), n(q)), FIVE_FOUR, bpm = 100) },
        ),
        check = CourseCheck("Seven eighths: 2 + 2 + 3, or 3 + 2 + 2?", listOf(
            option("2+2+3", "2 + 2 + 3: the long group last.") { _, _ -> bar(List(7) { n(e) }, SEVEN_EIGHT, bpm = 100) },
            option("3+2+2", "3 + 2 + 2: the long group first.") { _, _ -> bar(List(7) { n(e) }, TimeSignature(listOf(3, 2, 2), 8), bpm = 100) },
        )),
        related = listOf(ear("rh-odd", "odd and additive meters"), page("changing", "Changing meters")),
    )

    private val changing = CoursePage(
        "changing", "Changing meters", 4,
        "Nothing says a piece must stay in one meter. A bar of 2/4 dropped into 4/4 can make a phrase arrive early; " +
            "alternating 3/4 and 2/4 gives a five-beat cycle that feels like neither; Stravinsky's The Rite of Spring and " +
            "much progressive rock change meter almost every bar. In pop the usual case is a single bar that's short or long " +
            "to fit the words. Written music marks each change with a new time signature at the bar where it happens. The " +
            "skill is keeping the smallest note value steady through every change, so the eighth stays an eighth while the " +
            "bar around it grows and shrinks.",
        see = CourseSee.Rhythm("A PHRASE THAT CHANGES METER", FOUR, 100, listOf(row("4/4", List(4) { n(q) })), extra = listOf(
            Triple("2/4", TimeSignature.of(2, 4), List(2) { n(q) }), Triple("3/4", THREE, List(3) { n(q) }), Triple("7/8", SEVEN_EIGHT, List(7) { n(e) }),
        )),
        hear = listOf(sound("4/4 · 2/4 · 3/4 · 4/4") {
            val bpm = 100
            var at = 0.0
            val drums = ArrayList<DrumAt>()
            for (m in listOf(FOUR, TimeSignature.of(2, 4), THREE, FOUR)) { drums += EarRhythm.drums(List(m.numerator) { n(q) }, m, bpm, at, hats = true); at += EarRhythm.barSeconds(m, bpm) }
            EarAudio(emptyList(), drums = drums)
        }),
        related = listOf(page("odd", "Odd meters")),
    )

    // ------------------------------------------------------------------------------------------------- polyrhythm, metric modulation

    /** Onsets of [a] against [b] across one cycle, on a grid of a × b steps. */
    internal fun poly(a: Int, b: Int): Pair<List<Int>, List<Int>> = (0 until a).map { it * b } to (0 until b).map { it * a }

    private fun polyAudio(a: Int, b: Int, seconds: Double = 2.0, cycles: Int = 3): EarAudio {
        val (ta, tb) = poly(a, b)
        val step = seconds / (a * b)
        val notes = (0 until cycles).flatMap { c -> ta.map { PlayNote(c * seconds + it * step, c * seconds + it * step + 0.15, 79, RefSound.VELOCITY) } + tb.map { PlayNote(c * seconds + it * step, c * seconds + it * step + 0.2, 48, RefSound.VELOCITY) } }
        return EarAudio(notes)
    }

    private val poly = CoursePage(
        "poly", "Polyrhythm", 5,
        "A polyrhythm is two even pulses at once that don't divide into each other: three against two, four against three. " +
            "In 3 against 2, one part plays three evenly spaced notes while the other plays two in the same span; they meet " +
            "on the first note and nowhere else. On a grid of six steps the threes fall on 0, 2 and 4, the twos on 0 and 3. " +
            "4 against 3 needs a grid of twelve: fours on 0, 3, 6 and 9, threes on 0, 4 and 8. Polyrhythms are the ground of " +
            "much West African drumming and Afro-Cuban music, where several layers run against a shared cycle, and in " +
            "Western music they turn up from Chopin's piano writing to modern metal. Either pulse can be heard as the beat, " +
            "and switching between them is part of the effect.",
        see = CourseSee.Table("WHERE THE NOTES FALL", listOf("", "GRID", "HIGH", "LOW"), listOf(3 to 2, 4 to 3, 5 to 4).map { (a, b) ->
            val (ta, tb) = poly(a, b)
            listOf("$a : $b", "${a * b} steps", ta.joinToString(" "), tb.joinToString(" "))
        }),
        hear = listOf(
            sound("3 AGAINST 2") { polyAudio(3, 2) },
            sound("4 AGAINST 3") { polyAudio(4, 3) },
        ),
        check = CourseCheck("High notes against low: 3 against 2, or 4 against 3?", listOf(
            option("3 : 2", "3 against 2: three high in the time of two low.") { _, _ -> polyAudio(3, 2) },
            option("4 : 3", "4 against 3: four high in the time of three low.") { _, _ -> polyAudio(4, 3) },
        )),
        related = listOf(page("tuplets", "Tuplets"), page("metric-modulation", "Metric modulation")),
    )

    /** The new tempo when a note worth [old] beats in the old tempo becomes worth [new] beats. */
    internal fun modulate(bpm: Double, old: Rational, new: Rational) = bpm * (new / old).toDouble()

    private val metric = CoursePage(
        "metric-modulation", "Metric modulation", 5,
        "Metric modulation changes tempo by making a note value in the old tempo equal a different value in the new. At " +
            "120 BPM a triplet eighth lasts a third of a beat; if it becomes the new plain eighth, the new beat is " +
            "${Acoustics.fmt(0, modulate(120.0, Rational.of(1, 3), Rational.of(1, 2)))} BPM. If instead the old dotted quarter " +
            "becomes the new quarter, the tempo drops to ${Acoustics.fmt(0, modulate(120.0, Rational.of(3, 2), Rational.ONE))}. " +
            "Written as \"♪³ = ♪\" or \"♩. = ♩\" over the change, it lets a piece shift speed exactly, with a pulse the " +
            "players can hear across the join. Elliott Carter made it a central technique; rock and jazz use the simplest " +
            "case, half-time and double-time, all the time.",
        see = CourseSee.Table("FROM 120 BPM", listOf("OLD", "BECOMES", "NEW BPM"), listOf(
            Triple("triplet eighth", "eighth", modulate(120.0, Rational.of(1, 3), Rational.of(1, 2))),
            Triple("dotted quarter", "quarter", modulate(120.0, Rational.of(3, 2), Rational.ONE)),
            Triple("half", "quarter", modulate(120.0, Rational.of(2), Rational.ONE)),
            Triple("eighth", "quarter", modulate(120.0, Rational.of(1, 2), Rational.ONE)),
            Triple("triplet quarter", "quarter", modulate(120.0, Rational.of(2, 3), Rational.ONE)),
        ).map { (a, b, t) -> listOf(a, b, Acoustics.fmt(0, t)) }),
        hear = listOf(sound("120 → 180 · THE TRIPLET EIGHTH BECOMES THE EIGHTH") {
            val old = EarRhythm.drums(List(12) { n(t3(e)) }, FOUR, 120, 0.0)
            EarAudio(emptyList(), drums = old + EarRhythm.drums(List(16) { n(e) }, FOUR, 180, 2.0) )
        }),
        related = listOf(page("poly", "Polyrhythm"), page("pulse", "Pulse and tempo")),
    )

    val topics = listOf(
        CourseTopic("pulse", "Pulse, tempo and BPM", "the beat you tap your foot to", 1, listOf(pulse)),
        CourseTopic("values", "Note values and rests", "whole to sixteenth, and silences", 1, listOf(values, rests)),
        CourseTopic("meter", "Meter and time signatures", "simple and compound, strong and weak beats", 2, listOf(signatures, compound)),
        CourseTopic("ties", "Dots, ties and syncopation", "lengths across the beat", 2, listOf(dots, syncopation)),
        CourseTopic("swing", "Swing and feel", "straight, swung, shuffled, laid back", 2, listOf(swing)),
        CourseTopic("tuplets", "Tuplets", "triplets, quintuplets, nested groups", 3, listOf(tuplets)),
        CourseTopic("odd", "Odd and mixed meters", "5/4, 7/8, 2+2+3, changing bars", 4, listOf(odd, changing)),
        CourseTopic("poly", "Polyrhythm and metric modulation", "3 against 2, 4 against 3, tempo by ratio", 5, listOf(poly, metric)),
    )

}
