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

    /** Kick on 1 and 3, snare on 2 and 4, eighths on the hat, for [bars] bars at [bpm]. */
    private fun backbeat(bpm: Int, bars: Int = 2) = EarAudio(emptyList(), drums = (0 until 4 * bars).flatMap { i ->
        val beat = 60.0 / bpm
        listOf(DrumAt(i * beat, DrumVoice.HAT, DrumHit.HIT), DrumAt(i * beat + beat / 2, DrumVoice.HAT, DrumHit.HIT),
            DrumAt(i * beat, if (i % 2 == 0) DrumVoice.KICK else DrumVoice.SNARE, DrumHit.ACCENT))
    })

    /** [a] high against [b] low on a grid of a × b steps: the row for each, played on its own, and ▶ plays both for [cycles] cycles of 2 s. */
    private fun polyGrid(a: Int, b: Int, cycles: Int = 3): CourseSee.Grid {
        val (ta, tb) = poly(a, b)
        val cols = (0 until a * b).map { "$it" }
        val step = 2.0 / (a * b)
        fun only(on: List<Int>, m: Int) = Play((0 until cycles).flatMap { (0 until a * b).map { i -> if (i in on) listOf(m) else emptyList() } }, step)
        val both = Play((0 until cycles).flatMap { (0 until a * b).map { i -> listOfNotNull(if (i in ta) 79 else null, if (i in tb) 48 else null) } }, step)
        return CourseSee.Grid("$a AGAINST $b · ${a * b} STEPS", cols, listOf(
            GridRow("$a · high", cols, ta.toSet(), only(ta, 79)), GridRow("$b · low", cols, tb.toSet(), only(tb, 48)),
        ), both)
    }

    // ------------------------------------------------------------------------------------------------- pulse

    private val pulse = CoursePage(
        "pulse", "Pulse and tempo", 1,
        "Under most music runs a steady pulse, the beat you tap your foot to. Its speed is the tempo.",
        steps = listOf(
            say("Tempo is counted in beats per minute (BPM). At 60 BPM each beat lasts exactly a second; at 120, half a second. " +
                "Doubling the BPM halves every beat, and the HEAR buttons below play 60, 90 and 120.",
                table("HOW LONG A BEAT LASTS", listOf("BPM", "ONE BEAT", "A BAR OF 4"), listOf(60, 80, 90, 120, 140, 174).map { b ->
                    listOf("$b", "${Acoustics.fmt(0, 60000.0 / b)} ms", "${Acoustics.fmt(2, 240.0 / b)} s")
                })),
            say("Classical scores name tempos in Italian words, roughly from slow to fast. Sources give different BPM ranges for " +
                "each, so a BPM is the only exact marking.",
                table("TEMPO WORDS, SLOW TO FAST", listOf("", "MEANS", "PACE"), listOf(
                    listOf("Largo", "broad", "very slow"), listOf("Adagio", "at ease", "slow"), listOf("Andante", "walking", "walking pace"),
                    listOf("Moderato", "moderate", "medium"), listOf("Allegro", "cheerful", "fast"), listOf("Presto", "quick", "very fast"),
                ))),
            say("A beat can also be felt at half or double speed. A drummer playing half-time moves the snare from 2 and 4 to 3, " +
                "and the same tempo suddenly feels twice as slow. Hear the normal groove here, then the half-time one below.",
                null, sound("NORMAL TIME · SNARE ON 2 AND 4") { backbeat(90) }),
        ),
        inShort = listOf(
            "Tempo = beats per minute; 60 BPM = one beat a second.",
            "Italian tempo words are rough; BPM is exact.",
            "Half-time and double-time change the feel without changing the BPM.",
        ),
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
        "Written rhythm measures each note against the beat. Learn one value and the rest follow, because each is half or " +
            "double the next.",
        steps = listOf(
            say("In 4/4 a quarter note is one beat. A half note is two, a whole note four; an eighth is half a beat and a " +
                "sixteenth a quarter of one. Each row fills one bar; tap ▶ on a row to hear it over the beat.",
                CourseSee.Rhythm("ONE BAR OF 4/4, FIVE WAYS", FOUR, 80, listOf(
                    row("WHOLE", listOf(n(w))), row("HALVES", List(2) { n(h) }), row("QUARTERS", List(4) { n(q) }),
                    row("EIGHTHS", List(8) { n(e) }), row("SIXTEENTHS", List(16) { n(s) }),
                ))),
            say("Eighths and shorter have flags. When several fall in one beat their flags join into beams, so the beat stays " +
                "visible: two eighths under a beam are one beat, four sixteenths under a beam another, an eighth and two " +
                "sixteenths a third.",
                CourseSee.Rhythm("BEAMED BY THE BEAT", FOUR, 80, listOf(
                    row("MIXED", listOf(n(e), n(e), n(s), n(s), n(s), n(s), n(q), n(e), n(s), n(s))),
                ))),
            say("The values are relative. Only the tempo turns them into seconds: at 60 BPM a quarter lasts a second and an eighth " +
                "half a second; at 120 everything takes half as long.",
                table("BEATS AND SECONDS", listOf("", "BEATS", "AT 60", "AT 120"), listOf(
                    listOf("Whole", "4", "4 s", "2 s"), listOf("Half", "2", "2 s", "1 s"), listOf("Quarter", "1", "1 s", "0.5 s"),
                    listOf("Eighth", "½", "0.5 s", "0.25 s"), listOf("Sixteenth", "¼", "0.25 s", "0.125 s"),
                ))),
        ),
        inShort = listOf(
            "Whole 4, half 2, quarter 1, eighth ½, sixteenth ¼ (beats in 4/4).",
            "Beams group short notes by the beat.",
            "The tempo turns beats into seconds.",
        ),
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
        "Every note value has a rest of the same length, and silence counts exactly as much as sound.",
        steps = listOf(
            say("A bar of 4/4 always adds up to four beats, notes and rests together. Here each note is followed by a rest of its " +
                "own length.",
                CourseSee.Rhythm("A NOTE, THEN ITS REST", FOUR, 80, listOf(
                    row("HALVES", listOf(n(h), r(h))), row("QUARTERS", listOf(n(q), r(q), n(q), r(q))),
                    row("EIGHTHS", List(4) { listOf(n(e), r(e)) }.flatten()),
                ))),
            say("Rests are written to show the beat, as beams are. Put the rest first and only the \"and\" is left: an eighth rest " +
                "then an eighth note, every beat, is the off-beat chop of reggae skanks and funk guitar.",
                CourseSee.Rhythm("ON THE BEAT, THEN OFF IT", FOUR, 80, listOf(
                    row("ON THE BEAT", listOf(n(q), r(q), n(q), r(q))), row("OFF THE BEAT", List(4) { listOf(r(e), n(e)) }.flatten()),
                ))),
            say("Reading rests well is most of reading rhythm, because the notes after them are where the groove sits. Count " +
                "\"1 and 2 and 3 and 4 and\" through this bar and clap only the notes. A whole rest means silent for the whole bar, " +
                "in any meter.",
                CourseSee.Rhythm("COUNT IT, CLAP THE NOTES", FOUR, 80, listOf(row("MIXED", listOf(n(e), n(e), r(q), n(e.dotted()), n(s), r(q)))))),
        ),
        inShort = listOf(
            "A rest lasts as long as the note value it matches.",
            "Notes and rests together fill the bar.",
            "Rest-then-note puts the sound on the off-beat.",
        ),
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
        "Beats group into bars, and the time signature at the start of the music says how.",
        steps = listOf(
            say("The top number counts what's in a bar; the bottom names the note being counted (4 a quarter, 8 an eighth, 2 a " +
                "half). 4/4 is four quarter notes, 3/4 three, 2/4 two.",
                CourseSee.Rhythm("ONE BAR EACH", FOUR, 80, listOf(row("4/4", List(4) { n(q) })),
                    extra = listOf(Triple("3/4", THREE, List(3) { n(q) }), Triple("2/4", TimeSignature.of(2, 4), List(2) { n(q) })))),
            say("The grouping makes some beats strong. In 4/4 beat 1 is strongest, 3 next, 2 and 4 weak, which is exactly where " +
                "rock and soul put the snare, against the grain. In 3/4 the 1 is strong and 2 and 3 weak: the lilt of a waltz.",
                table("STRONG AND WEAK BEATS", listOf("", "1", "2", "3", "4"), listOf(
                    listOf("4/4", "STRONG", "weak", "medium", "weak"), listOf("3/4", "STRONG", "weak", "weak", ""),
                )),
                sound("THE BACKBEAT · SNARE ON THE WEAK BEATS") { backbeat(100) }),
            say("4/4 is so common it has its own sign, C. 2/2, cut time (¢), counts two half notes to a bar: the same length as " +
                "4/4, felt in two, like a march or a fast polka.",
                CourseSee.Rhythm("CUT TIME · TWO BEATS A BAR", TimeSignature.of(2, 2), 100, listOf(row("2/2", List(2) { n(h) }), row("2/2", List(4) { n(q) })))),
        ),
        inShort = listOf(
            "Top number: how many; bottom: of what note.",
            "4/4: 1 strong, 3 medium, 2 and 4 weak. 3/4: 1 strong.",
            "C = 4/4, ¢ = 2/2.",
        ),
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
        "Meters differ in how each beat divides: in two (simple) or in three (compound).",
        steps = listOf(
            say("In simple meters each beat divides in two: 3/4 is three beats, each two eighths. In compound meters each beat " +
                "divides in three, and the signature counts the divisions: 6/8 is two beats, each a dotted quarter of three " +
                "eighths. Look at the beaming: threes in 6/8, twos in 3/4.",
                CourseSee.Rhythm("SIX EIGHTHS TWO WAYS", SIX_EIGHT, 60, listOf(row("6/8", List(6) { n(e) })), extra = listOf(Triple("3/4", THREE, List(6) { n(e) })))),
            say("So the two hold the same six eighths and sound different. Say them aloud with the capitals loud.",
                table("WHERE THE ACCENTS FALL", listOf("", "1", "2", "3", "4", "5", "6"), listOf(
                    listOf("6/8", "ONE", "two", "three", "FOUR", "five", "six"), listOf("3/4", "ONE", "two", "THREE", "four", "FIVE", "six"),
                ))),
            say("9/8 is three such beats and 12/8 four. 12/8 is the rolling feel of slow blues and doo-wop ballads; 6/8 the jig " +
                "and many folk songs.",
                CourseSee.Rhythm("THREE AND FOUR COMPOUND BEATS", TimeSignature(listOf(3, 3, 3), 8), 60, listOf(row("9/8", List(9) { n(e) })),
                    extra = listOf(Triple("12/8", TimeSignature(listOf(3, 3, 3, 3), 8), List(12) { n(e) })))),
        ),
        inShort = listOf(
            "Simple: beats split in two. Compound: in three.",
            "6/8 = two beats of three eighths; 3/4 = three beats of two.",
            "9/8 = three compound beats, 12/8 = four.",
        ),
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
        "Dots and ties make lengths that the plain values can't, and both are everywhere in real music.",
        steps = listOf(
            say("A dot after a note adds half its value. A dotted half is 2 + 1 = three beats; a dotted quarter 1 + ½ = a beat and " +
                "a half (three eighths); a dotted eighth three sixteenths. A second dot adds half as much again.",
                table("WHAT A DOT ADDS", listOf("NOTE", "PLAIN", "DOTTED", "DOUBLE-DOTTED"), listOf(
                    listOf("Half", "2", "3", "3½"), listOf("Quarter", "1", "1½", "1¾"), listOf("Eighth", "½", "¾", "⅞"),
                ))),
            say("The dotted quarter followed by an eighth (ta–a–ti) is one of the most common figures in music; the dotted eighth " +
                "and sixteenth is its quicker, jumpier cousin.",
                CourseSee.Rhythm("DOTTED FIGURES IN 4/4", FOUR, 80, listOf(
                    row("DOTTED HALF", listOf(n(h.dotted()), n(q))), row("DOTTED QUARTER", listOf(n(q.dotted()), n(e), n(q.dotted()), n(e))),
                    row("DOTTED EIGHTH", List(4) { listOf(n(e.dotted()), n(s)) }.flatten()),
                ))),
            say("A tie is a curve joining two notes of the same pitch into one sound that lasts both values. It's what you use " +
                "when a note crosses a bar line, which no single note can, or when writing it as one note would hide where the " +
                "beat is: a dotted quarter starting on the \"and\" of 1 is written as an eighth tied to a quarter."),
        ),
        inShort = listOf(
            "A dot adds half the note's value; a second dot adds a quarter.",
            "Dotted quarter + eighth, dotted eighth + sixteenth: the common long–short figures.",
            "A tie joins two notes into one sound, across bar lines and beats.",
        ),
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
            "holds across the next one.",
        steps = listOf(
            say("Start from notes on the beat, then compare eighth–quarter–eighth (ti–ta–ti). The long note now starts on the " +
                "\"and\" of 1 and holds through beat 2, so nothing sounds on 2 at all.",
                CourseSee.Rhythm("ON THE BEAT, THEN TI–TA–TI", FOUR, 90, listOf(
                    row("ON THE BEAT", List(4) { n(q) }), row("TI–TA–TI", listOf(n(e), n(q), n(e), n(e), n(q), n(e))),
                ))),
            say("Group eight eighths as 3 + 3 + 2 and you get the pattern often called tresillo, Afro-Cuban in origin and heard " +
                "across Latin music, reggaeton and countless pop beats. Only its first note is on a beat.",
                CourseSee.Rhythm("EIGHT EIGHTHS, THEN 3 + 3 + 2", FOUR, 90, listOf(
                    row("EIGHTHS", List(8) { n(e) }), row("3 + 3 + 2", listOf(n(q.dotted()), n(q.dotted()), n(q))),
                ))),
            say("A note pushed an eighth early, onto the \"and\" of 4 and held over the bar line, is an anticipation or push: the " +
                "classic pop and funk lift into a new bar. The pulse doesn't change; the accents fight it, and that tension is " +
                "what makes music feel like it moves.",
                CourseSee.Rhythm("OFF-BEATS AND A PUSH", FOUR, 90, listOf(row("OFF-BEATS", listOf(r(e), n(e), r(e), n(e), n(e), n(q), n(e)))))),
        ),
        inShort = listOf(
            "Syncopation = accents off the beat, against the meter.",
            "Ti–ta–ti and 3 + 3 + 2 are the everyday figures.",
            "A push lands a bar's first note an eighth early.",
        ),
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
        "Swing plays pairs of eighths long–short instead of even. It's written one way and played another.",
        steps = listOf(
            say("At a medium tempo the first of each pair takes about two thirds of the beat and the second one third: the triplet " +
                "feel of jazz. The top row is how swing is usually written, plain eighths with \"swing\" at the top; the bottom " +
                "row spells out what's played, a quarter-and-eighth triplet on every beat.",
                CourseSee.Rhythm("WRITTEN, AND WHAT'S PLAYED", FOUR, 100, listOf(
                    row("WRITTEN", List(8) { n(e) }), row("PLAYED", List(4) { listOf(n(t3(q)), n(t3(e))) }.flatten()),
                ))),
            say("The ratio isn't fixed. Players swing harder at slow tempos and nearly straight at fast ones, and the HEAR buttons " +
                "below compare straight with swung.",
                null, sound("SWUNG, SLOW") { bar(List(8) { n(e) }, bpm = 70, swing = true) }),
            say("A shuffle is swung eighths with a heavy, bouncing triplet pulse: the blues and boogie feel. Feel also means where " +
                "you sit on the beat: laid back, a hair behind it, or on top, a hair ahead. Neither is written down; both are " +
                "learnt by ear."),
        ),
        inShort = listOf(
            "Swing = long–short eighths, about 2:1 at medium tempos.",
            "Written as eighths, played as triplets.",
            "Behind or ahead of the beat is feel, not notation.",
        ),
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
        "A tuplet fits a number of notes into the time normally taken by a different number.",
        steps = listOf(
            say("A triplet is three in the time of two: three triplet eighths fill one beat, where two plain eighths would. It's " +
                "written with a 3 over the group (and a bracket if the notes aren't beamed together), and as a ratio, 3:2.",
                CourseSee.Rhythm("TWO A BEAT, THEN THREE", FOUR, 72, listOf(row("EIGHTHS", List(8) { n(e) }), row("TRIPLETS", List(12) { n(t3(e)) })))),
            say("Quarter-note triplets stretch three notes across two beats, so only the first one lands on a beat. They pull " +
                "against the pulse harder than eighth triplets do.",
                CourseSee.Rhythm("THREE ACROSS TWO BEATS", FOUR, 72, listOf(row("QUARTER TRIPLETS", listOf(n(t3(q)), n(t3(q)), n(t3(q)), n(h)))))),
            say("Quintuplets fit five sixteenths into a beat (5:4), sextuplets six (6:4). Triplets are the bridge between simple and " +
                "compound time: a bar of 4/4 in triplet eighths sounds just like a bar of 12/8. Tuplets can nest, a triplet inside " +
                "a triplet, and Apophenia's scores write any ratio.",
                CourseSee.Rhythm("FIVE IN A BEAT · AND 12/8", FOUR, 72, listOf(row("QUINTUPLETS", List(5) { n(s.inTuplet(5, 4)) } + listOf(n(q), n(h)))),
                    extra = listOf(Triple("12/8", TimeSignature(listOf(3, 3, 3, 3), 8), List(12) { n(e) })))),
        ),
        inShort = listOf(
            "Triplet = 3 in the time of 2 (3:2).",
            "Quintuplet 5:4, sextuplet 6:4.",
            "4/4 in triplets ≈ 12/8.",
        ),
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
            "uneven groups of twos and threes.",
        steps = listOf(
            say("5/4 is felt as 3 + 2 or 2 + 3. It's the meter of the Dave Brubeck Quartet's \"Take Five\".",
                CourseSee.Rhythm("FIVE BEATS", FIVE_FOUR, 100, listOf(row("5/4", List(5) { n(q) })))),
            say("7/8 can be 2 + 2 + 3, 3 + 2 + 2 or 2 + 3 + 2, each with its own lilt. The beaming shows the grouping, and sometimes " +
                "the signature says it outright (2+2+3/8).",
                CourseSee.Rhythm("SEVEN EIGHTHS, THREE WAYS", SEVEN_EIGHT, 100, listOf(row("2+2+3/8", List(7) { n(e) })), extra = listOf(
                    Triple("3+2+2/8", TimeSignature(listOf(3, 2, 2), 8), List(7) { n(e) }), Triple("2+3+2/8", TimeSignature(listOf(2, 3, 2), 8), List(7) { n(e) }),
                ))),
            say("9/8 can be three threes (compound) or 2 + 2 + 2 + 3, the second common in Balkan and Turkish music. Count it at " +
                "first (\"one-two, one-two, one-two, one-two-three\"); after that it's a groove like any other.",
                CourseSee.Rhythm("NINE EIGHTHS, TWO WAYS", TimeSignature(listOf(3, 3, 3), 8), 100, listOf(row("9/8", List(9) { n(e) })), extra = listOf(
                    Triple("2+2+2+3/8", TimeSignature(listOf(2, 2, 2, 3), 8), List(9) { n(e) }),
                ))),
        ),
        inShort = listOf(
            "Odd meters are felt as uneven groups of 2s and 3s.",
            "5/4 = 3+2 or 2+3; 7/8 = 2+2+3, 3+2+2 or 2+3+2.",
            "Beaming (or the signature) shows the grouping.",
        ),
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
        "Nothing says a piece must stay in one meter. Each change is marked with a new time signature at the bar where it " +
            "happens.",
        steps = listOf(
            say("A bar of 2/4 dropped into 4/4 makes a phrase arrive early. In pop that's the usual case: a single short or long " +
                "bar to fit the words. Here's a phrase that changes meter, bar by bar.",
                CourseSee.Rhythm("A PHRASE THAT CHANGES METER", FOUR, 100, listOf(row("4/4", List(4) { n(q) })), extra = listOf(
                    Triple("2/4", TimeSignature.of(2, 4), List(2) { n(q) }), Triple("3/4", THREE, List(3) { n(q) }), Triple("7/8", SEVEN_EIGHT, List(7) { n(e) }),
                ))),
            say("Alternating 3/4 and 2/4 gives a five-beat cycle that feels like neither.",
                CourseSee.Rhythm("3/4 + 2/4", THREE, 100, listOf(row("3/4", List(3) { n(q) })), extra = listOf(
                    Triple("2/4", TimeSignature.of(2, 4), List(2) { n(q) }), Triple("3/4", THREE, List(3) { n(q) }), Triple("2/4", TimeSignature.of(2, 4), List(2) { n(q) }),
                ))),
            say("Stravinsky's The Rite of Spring and much progressive rock change meter almost every bar. The skill is keeping the " +
                "smallest note value steady through every change, so the eighth stays an eighth while the bar around it grows " +
                "and shrinks."),
        ),
        inShort = listOf(
            "A new time signature marks each change.",
            "Short or long bars fit phrases and words.",
            "Keep the smallest value steady across the changes.",
        ),
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
        "A polyrhythm is two even pulses at once that don't divide into each other: three against two, four against three.",
        steps = listOf(
            say("In 3 against 2, one part plays three evenly spaced notes while the other plays two in the same span. Split the span " +
                "into six steps: the threes fall on 0, 2 and 4, the twos on 0 and 3. They meet on the first step and nowhere else. " +
                "Tap each row alone, then ▶ for both.",
                polyGrid(3, 2)),
            say("4 against 3 needs a grid of twelve: fours on 0, 3, 6 and 9, threes on 0, 4 and 8.",
                polyGrid(4, 3)),
            say("Either pulse can be heard as the beat, and switching between them is part of the effect. Polyrhythms are the " +
                "ground of much West African drumming and Afro-Cuban music, where several layers run against a shared cycle, and " +
                "in Western music they turn up from Chopin's piano writing to modern metal. The grid for any pair is a × b steps.",
                table("WHERE THE NOTES FALL", listOf("", "GRID", "HIGH", "LOW"), listOf(3 to 2, 4 to 3, 5 to 4).map { (a, b) ->
                    val (ta, tb) = poly(a, b)
                    listOf("$a : $b", "${a * b} steps", ta.joinToString(" "), tb.joinToString(" "))
                }, listOf(3 to 2, 4 to 3, 5 to 4).map { (a, b) -> polyGrid(a, b, 2).play })),
        ),
        inShort = listOf(
            "Polyrhythm = two even pulses that don't divide into each other.",
            "a against b: a grid of a × b steps; they meet only on step 0.",
            "Either layer can be the beat.",
        ),
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
        "Metric modulation changes tempo by declaring that a note value in the old tempo equals a different value in the " +
            "new one. The players hear the link, so the new tempo arrives exactly.",
        steps = listOf(
            say("At 120 BPM a triplet eighth lasts a third of a beat. Declare that it's now a plain eighth, half a beat, and the beat " +
                "must get shorter by the same ratio: the new tempo is " +
                "${Acoustics.fmt(0, modulate(120.0, Rational.of(1, 3), Rational.of(1, 2)))} BPM. If instead the old dotted quarter " +
                "becomes the new quarter, the tempo drops to ${Acoustics.fmt(0, modulate(120.0, Rational.of(3, 2), Rational.ONE))}.",
                table("FROM 120 BPM", listOf("OLD", "BECOMES", "NEW BPM"), listOf(
                    Triple("triplet eighth", "eighth", modulate(120.0, Rational.of(1, 3), Rational.of(1, 2))),
                    Triple("dotted quarter", "quarter", modulate(120.0, Rational.of(3, 2), Rational.ONE)),
                    Triple("half", "quarter", modulate(120.0, Rational.of(2), Rational.ONE)),
                    Triple("eighth", "quarter", modulate(120.0, Rational.of(1, 2), Rational.ONE)),
                    Triple("triplet quarter", "quarter", modulate(120.0, Rational.of(2, 3), Rational.ONE)),
                ).map { (a, b, t) -> listOf(a, b, Acoustics.fmt(0, t)) })),
            say("The rule: new BPM = old BPM × (the old note's length in beats ÷ the new note's). It's written over the change as " +
                "\"♪³ = ♪\" or \"♩. = ♩\"."),
            say("Elliott Carter made it a central technique. Rock and jazz use the simplest case, half-time and double-time, all " +
                "the time: \"half = quarter\" halves the tempo, \"eighth = quarter\" doubles it."),
        ),
        inShort = listOf(
            "Metric modulation: an old note value becomes a new one, and the tempo follows.",
            "New BPM = old × (old length ÷ new length).",
            "Half-time and double-time are the everyday case.",
        ),
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
