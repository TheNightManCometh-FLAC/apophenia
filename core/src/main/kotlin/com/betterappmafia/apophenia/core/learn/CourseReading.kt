package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.instrument.Instrument
import com.betterappmafia.apophenia.core.instrument.Tunings
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.pitch.SpelledPitch
import com.betterappmafia.apophenia.core.rhythm.Duration
import com.betterappmafia.apophenia.core.rhythm.TimeSignature
import com.betterappmafia.apophenia.core.score.Bar
import com.betterappmafia.apophenia.core.score.Barline
import com.betterappmafia.apophenia.core.score.Clef
import com.betterappmafia.apophenia.core.score.Event
import com.betterappmafia.apophenia.core.score.Jump
import com.betterappmafia.apophenia.core.score.Note
import com.betterappmafia.apophenia.core.score.Part
import com.betterappmafia.apophenia.core.score.Score
import com.betterappmafia.apophenia.core.score.ScorePlayback
import com.betterappmafia.apophenia.core.score.ScoreTime

/*
 * Course · Reading: the staff and clefs, lines and spaces, ledger lines and octave signs, how bars add up, key and
 * time signatures on the page, accidentals in the bar, tab, chord charts and Nashville numbers, repeats and road
 * maps. Text written for Apophenia; the examples are real Scores drawn by the app's own notation and tab views.
 */
internal object CourseReading : CourseKit() {
    private val q = Duration.QUARTER
    private val h = Duration.HALF
    private val w = Duration.WHOLE
    private val e = Duration.EIGHTH
    private fun ev(d: Duration, vararg p: String) = Event(d, p.map { Note(SpelledPitch.parse(it)) })
    private fun rest(d: Duration) = Event(d)
    private fun part(clef: Clef, vararg bars: List<Event>) = Part("", clef, bars = bars.map { Bar(listOf(it)) })
    private fun score(vararg parts: Part, key: KeyMode? = null, meter: TimeSignature = TimeSignature.of(4, 4)) = Score(key = key, meter = meter, parts = parts.toList())
    /** A score played on the piano, every part together. */
    internal fun played(s: Score) = EarAudio(s.parts.flatMap { ScorePlayback.notes(s, it) })

    // ------------------------------------------------------------------------------------------------- staff and clefs

    /** The pitch on each line of a five-line staff, bottom to top, from the bottom line's pitch. */
    internal fun lines(bottom: String): List<SpelledPitch> {
        val b = SpelledPitch.parse(bottom)
        val pos = b.octave * 7 + b.letter.ordinal
        return (0 until 5).map { SpelledPitch.fromLetterPosition(pos + 2 * it, 0) }
    }
    internal fun spaces(bottom: String) = lines(bottom).zipWithNext().map { (a, _) -> val p = a.octave * 7 + a.letter.ordinal; SpelledPitch.fromLetterPosition(p + 1, 0) }
    internal val BOTTOM_LINE = mapOf("Treble" to "E4", "Bass" to "G2", "Alto" to "F3", "Tenor" to "D3")

    private val middleC = score(part(Clef.TREBLE, listOf(ev(w, "C4"))), part(Clef.BASS, listOf(ev(w, "C4"))), part(Clef.ALTO, listOf(ev(w, "C4"))))

    private fun one(caption: String, sc: Score, vararg labels: String) = CourseSee.Score(caption, sc, if (labels.isEmpty()) sc.parts.map { "" } else labels.toList())
    private fun tied(d: Duration, p: String) = Event(d, listOf(Note(SpelledPitch.parse(p), tie = true)))
    private val ladder = score(part(Clef.TREBLE, listOf(ev(q, "E4"), ev(q, "F4"), ev(q, "G4"), ev(q, "A4")), listOf(ev(q, "B4"), ev(q, "C5"), ev(q, "D5"), ev(q, "E5"))))
    private val clefNotes = score(part(Clef.TREBLE, listOf(ev(w, "G4"))), part(Clef.BASS, listOf(ev(w, "F3"))))
    private val grand = score(part(Clef.TREBLE, listOf(ev(w, "E4", "G4", "C5"))), part(Clef.BASS, listOf(ev(w, "C3", "G3"))))

    private val staff = CoursePage(
        "staff", "The staff and clefs", 1,
        "Written music sits on a staff, and a clef at its start says which note each line and space stands for.",
        steps = listOf(
            say("The staff is five lines and four spaces; higher on the page means higher in pitch. Each step from a line to the " +
                "next space is one letter up. On its own, though, the staff is just a ladder.",
                one("A STEP UP THE LADDER, LINE · SPACE · LINE", ladder)),
            say("A clef fixes which rung is which note. The treble clef, a G clef, curls round the second line from the bottom: " +
                "that line is the G above middle C. The bass clef, an F clef, puts its two dots either side of the fourth line: " +
                "the F below middle C.",
                one("EACH CLEF'S OWN NOTE · G4 IN TREBLE, F3 IN BASS", clefNotes, "TREBLE", "BASS")),
            say("C clefs mark middle C wherever they sit. On the middle line it's the alto clef, used by the viola; on the fourth " +
                "line, the tenor clef of the cello's and trombone's high range. Here's one middle C written in three clefs.",
                CourseSee.Score("MIDDLE C IN TREBLE, BASS AND ALTO CLEF", middleC, listOf("TREBLE", "BASS", "ALTO"))),
            say("Piano music uses the grand staff, treble over bass, with middle C on a short ledger line between them. Guitar " +
                "music is written in treble clef an octave above how it sounds.",
                one("THE GRAND STAFF · A C CHORD, BOTH HANDS", grand, "RIGHT HAND", "LEFT HAND")),
        ),
        inShort = listOf(
            "Five lines, four spaces; up the page = up in pitch.",
            "Treble clef marks G4, bass clef F3, C clefs middle C.",
            "Piano: the grand staff. Guitar: treble clef, an octave above how it sounds.",
        ),
        hear = listOf(sound("MIDDLE C") { piano(listOf(note(0.0, 2.0, 60))) }),
        related = listOf(page("notes-lines", "Lines and spaces"), page("names-octaves", "Octave numbers")),
    )

    private val trebleLinesSpaces = score(part(Clef.TREBLE,
        listOf(ev(q, "E4"), ev(q, "G4"), ev(q, "B4"), ev(q, "D5")), listOf(ev(h, "F5"), rest(h)),
        listOf(ev(q, "F4"), ev(q, "A4"), ev(q, "C5"), ev(q, "E5"))))
    private val bassLines = score(part(Clef.BASS, listOf(ev(q, "G2"), ev(q, "B2"), ev(q, "D3"), ev(q, "F3")), listOf(ev(w, "A3"))))
    private val shapes = score(part(Clef.TREBLE, listOf(ev(q, "E4"), ev(q, "F4"), ev(q, "G4"), ev(q, "B4")), listOf(ev(w, "E4", "G4", "B4"))))

    private val linesPage = CoursePage(
        "notes-lines", "Lines and spaces", 1,
        "Each line and each space is a letter, and the letters run up the alphabet one step at a time: line, space, line, " +
            "space.",
        steps = listOf(
            say("In treble clef the lines from the bottom are E G B D F and the spaces F A C E. A note on a line has the line through " +
                "its middle; a note in a space sits between two lines.",
                one("TREBLE · THE LINES, THEN THE SPACES", trebleLinesSpaces)),
            say("In bass clef the lines are G B D F A and the spaces A C E G. People learn them with sentences (\"Every Good Boy " +
                "Deserves Fun\") or by landmarks: the clef's own note, middle C, the top and bottom lines, counting from there.",
                one("BASS · THE LINES", bassLines, "BASS")),
            say("All four clefs, from the app's own staff arithmetic. Tap a row to hear its lines, then its spaces.",
                table("THE FIVE LINES AND FOUR SPACES", listOf("CLEF", "LINES", "SPACES"), BOTTOM_LINE.map { (c, b) ->
                    listOf(c, lines(b).joinToString(" ") { it.toString() }, spaces(b).joinToString(" ") { it.toString() })
                }, BOTTOM_LINE.values.map { b -> Play((lines(b) + spaces(b)).map { listOf(it.midi) }, 0.35) })),
            say("Reading gets fast by recognising shapes rather than naming every note: a step (line to space), a skip (line to " +
                "line), a chord of stacked thirds (three lines in a row, or three spaces).",
                one("SHAPES · STEP, STEP, SKIP · STACKED THIRDS", shapes)),
        ),
        inShort = listOf(
            "Treble lines E G B D F, spaces F A C E.",
            "Bass lines G B D F A, spaces A C E G.",
            "Read shapes (steps, skips, stacks), not single notes.",
        ),
        hear = listOf(sound("TREBLE LINES · E G B D F") { piano(seq(lines("E4").map { it.midi }, step = 0.5)) }),
        related = listOf(page("ledger", "Ledger lines and octave signs"), page("staff", "The staff and clefs")),
    )

    private val ledgerScore = score(part(Clef.TREBLE, listOf(ev(q, "C4"), ev(q, "A3"), ev(q, "A5"), ev(q, "C6"))))

    private val lowTwoWays = score(part(Clef.TREBLE, listOf(ev(q, "A3"), ev(q, "B3"), ev(q, "C4"), ev(q, "D4"))), part(Clef.BASS, listOf(ev(q, "A3"), ev(q, "B3"), ev(q, "C4"), ev(q, "D4"))))

    private val ledger = CoursePage(
        "ledger", "Ledger lines and octave signs", 1,
        "Notes above or below the staff get short ledger lines of their own, continuing the pattern of lines and spaces.",
        steps = listOf(
            say("Middle C sits on one ledger line below the treble staff, A3 on two; A5 on one above it and C6 on two. Count them " +
                "the same way as staff lines: line, space, line.",
                CourseSee.Score("LEDGER LINES IN TREBLE CLEF · C4 A3 A5 C6", ledgerScore, listOf(""))),
            say("More than three or four ledger lines get hard to read, so music that stays very high or low uses an octave sign: " +
                "8va above the staff means play an octave higher than written, 8vb (or 8va bassa) below it an octave lower, 15ma " +
                "two octaves, ending with a dashed line and a hook."),
            say("Changing clef does the same job in a longer passage, which is why cellists switch to tenor clef and pianists move " +
                "a hand's staff into the other clef. The same four low notes: on ledger lines in treble, inside the staff in bass.",
                one("A3 TO D4 · TREBLE, THEN BASS", lowTwoWays, "TREBLE", "BASS")),
        ),
        inShort = listOf(
            "Ledger lines extend the staff up and down.",
            "8va / 8vb / 15ma shift the written notes by octaves.",
            "A clef change does the same for long passages.",
        ),
        hear = listOf(sound("C4 · A3 · A5 · C6") { played(ledgerScore) }),
        related = listOf(page("notes-lines", "Lines and spaces")),
    )

    // ------------------------------------------------------------------------------------------------- rhythm, signatures, accidentals

    private val barsScore = score(part(Clef.TREBLE,
        listOf(ev(q, "E4"), ev(e, "F4"), ev(e, "G4"), ev(h, "A4")),
        listOf(ev(q.dotted(), "G4"), ev(e, "F4"), rest(q), ev(q, "E4")),
        listOf(ev(e, "D4"), ev(e, "E4"), ev(e, "F4"), ev(e, "G4"), ev(h, "C4")),
    ))

    private val eighthsScore = score(part(Clef.TREBLE, List(8) { i -> ev(e, listOf("C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5")[i]) }))
    private val tieScore = score(part(Clef.TREBLE, listOf(ev(h, "C4"), ev(q, "E4"), tied(q, "G4")), listOf(ev(h, "G4"), rest(q), ev(q, "E4"))))

    private val barsPage = CoursePage(
        "rhythm-page", "How bars add up", 2,
        "A bar's notes and rests always add up to the time signature: four quarter notes' worth in 4/4, however they're " +
            "divided.",
        steps = listOf(
            say("Three bars of 4/4. Count each one: a quarter, two eighths and a half; a dotted quarter, an eighth, a quarter rest " +
                "and a quarter; four eighths and a half. Every bar comes to four beats.",
                CourseSee.Score("THREE BARS OF 4/4", barsScore, listOf(""))),
            say("Eighths and sixteenths are beamed in groups that show the beats, so in 4/4 you'll see eighths joined in twos or " +
                "fours, never across the middle of the bar. The beams and the spacing do most of the work: the eye counts beats " +
                "rather than adding values.",
                one("EIGHTHS · BEAMED BY THE BEAT", eighthsScore)),
            say("A note that would cross the bar line is split and tied instead: here the G starts on beat 4 and holds into the " +
                "next bar. Rests fill the gaps, written to show the beat too.",
                one("A NOTE TIED OVER THE BAR LINE", tieScore)),
        ),
        inShort = listOf(
            "Notes + rests = the time signature, every bar.",
            "Beams group by the beat.",
            "Ties carry a note across the bar line.",
        ),
        hear = listOf(sound("PLAY THE THREE BARS") { played(barsScore) }),
        related = listOf(page("values", "Note values"), page("dots-ties", "Dots and ties")),
    )

    private val signatureScore = Score(key = KeyMode(PitchName.parse("D"), "major"), meter = TimeSignature.of(3, 4), parts = listOf(part(Clef.TREBLE,
        listOf(ev(q, "D4"), ev(q, "F#4"), ev(q, "A4")), listOf(ev(q, "B4"), ev(q, "A4"), ev(q, "F#4")), listOf(ev(h.dotted(), "D4")),
    )))

    private val everyF = Score(key = KeyMode(PitchName.parse("D"), "major"), meter = TimeSignature.of(3, 4), parts = listOf(part(Clef.TREBLE,
        listOf(ev(q, "F#4"), ev(q, "A4"), ev(q, "F#5")), listOf(ev(h.dotted(), "D5")))))

    private val signaturesPage = CoursePage(
        "signatures-page", "Key and time signatures on the staff", 2,
        "At the start of a piece the staff reads, left to right: the clef, the key signature, the time signature.",
        steps = listOf(
            say("Here: treble clef, then D major's two sharps (F♯ and C♯), then 3/4. With the sharps in the signature, the bars " +
                "need no accidentals.",
                CourseSee.Score("D MAJOR IN 3/4", signatureScore, listOf(""))),
            say("A key signature applies to every octave and lasts the whole piece: both F's here are F♯, though neither has a sign. " +
                "It's repeated at the start of every line; the time signature is written once, and again only where it changes.",
                one("EVERY F IS F♯ · BOTH OCTAVES", everyF)),
            say("When the key changes mid-piece the new signature appears at a double bar line, often with naturals cancelling the " +
                "old sharps or flats."),
        ),
        inShort = listOf(
            "Order: clef, key signature, time signature.",
            "The key signature covers every octave, every line.",
            "Changes come at a double bar, with naturals cancelling the old key.",
        ),
        hear = listOf(sound("PLAY IT") { played(signatureScore) }),
        related = listOf(page("key-signatures", "Sharps and flats in order"), page("meter-signatures", "Time signatures")),
    )

    private val accidentalScore = score(part(Clef.TREBLE,
        listOf(ev(q, "F#4"), ev(q, "G4"), ev(q, "F#4"), ev(q, "E4")),
        listOf(ev(q, "F4"), ev(q, "G4"), ev(q, "Bb4"), ev(q, "B4")),
    ))

    private val oneBar = score(part(Clef.TREBLE, listOf(ev(q, "F#4"), ev(q, "G4"), ev(q, "F#4"), ev(q, "E4"))))
    private val naturalScore = score(part(Clef.TREBLE, listOf(ev(q, "Bb4"), ev(q, "A4"), ev(q, "B4"), ev(q, "C5"))))

    private val accidentals = CoursePage(
        "accidentals", "Accidentals in the bar", 2,
        "A sharp, flat or natural written before a note is an accidental. It lasts until the end of its bar, and only for " +
            "that note at that octave.",
        steps = listOf(
            say("In this bar the first F has a sharp. The second F, later in the same bar, is still F♯: it needs no sign.",
                one("ONE BAR · THE SHARP CARRIES", oneBar)),
            say("In the next bar the F is back to whatever the key signature says, here plain F. Engravers often add a courtesy " +
                "(cautionary) accidental anyway, sometimes in brackets, to save the reader a doubt.",
                CourseSee.Score("TWO BARS IN C", accidentalScore, listOf(""))),
            say("A natural sign cancels a sharp or flat, from the key signature or from earlier in the bar: B♭, then B♮. Double " +
                "sharps (𝄪) and double flats (𝄫) work the same way, and a single sharp after a double sharp is written in full.",
                one("B♭, THEN B♮, IN ONE BAR", naturalScore)),
        ),
        inShort = listOf(
            "An accidental lasts to the bar line, for that note at that octave.",
            "The next bar goes back to the key signature.",
            "A natural cancels; courtesy accidentals just remind.",
        ),
        hear = listOf(sound("PLAY IT") { played(accidentalScore) }),
        related = listOf(page("names-enharmonic", "One key, two names")),
    )

    // ------------------------------------------------------------------------------------------------- tab

    private fun tabNote(string: Int, fret: Int, pitch: String) = Note(SpelledPitch.parse(pitch), string = string, fret = fret)
    private fun tabEv(d: Duration, vararg n: Note) = Event(d, n.toList())
    internal val tabScore = Score(parts = listOf(Part("Guitar", Clef.TREBLE_8VB, Instrument(Tunings.GUITAR_STANDARD), listOf(
        Bar(listOf(listOf(tabEv(e, tabNote(6, 0, "E2")), tabEv(e, tabNote(6, 3, "G2")), tabEv(e, tabNote(5, 0, "A2")), tabEv(e, tabNote(5, 2, "B2")),
            tabEv(e, tabNote(4, 0, "D3")), tabEv(e, tabNote(4, 2, "E3")), tabEv(q, tabNote(3, 0, "G3"))))),
        Bar(listOf(listOf(tabEv(w, tabNote(6, 3, "G2"), tabNote(5, 2, "B2"), tabNote(4, 0, "D3"), tabNote(3, 0, "G3"), tabNote(2, 0, "B3"), tabNote(1, 3, "G4"))))),
    ))))

    private val tabRun = tabScore.copy(parts = tabScore.parts.map { it.copy(bars = it.bars.take(1)) })
    private val tabChord = tabScore.copy(parts = tabScore.parts.map { it.copy(bars = it.bars.drop(1)) })

    private val tab = CoursePage(
        "tab", "Reading tab", 1,
        "Tablature shows where to play rather than which note. It's quick to read for anything fretted.",
        steps = listOf(
            say("Each line is a string, with the highest-sounding string (the thin high E on a guitar) at the top, the way you'd " +
                "see the neck looking down at it. Each number is a fret, 0 for an open string. Numbers in a row are played one " +
                "after the other: this run starts on the open low E, then its 3rd fret.",
                CourseSee.Score("AN E MINOR PENTATONIC RUN", tabRun, listOf("GUITAR"))),
            say("Numbers stacked in a column are played together: a chord. This is an open G: 3rd fret on the low E, 2nd on the A, " +
                "three open strings, 3rd fret on the high E.",
                CourseSee.Score("AN OPEN G CHORD", tabChord, listOf("GUITAR"))),
            say("Letters and symbols mark techniques. Plain tab often leaves out rhythm, so you need the recording; tab printed under " +
                "standard notation, as in these examples, gives both. Bass tab is the same with four (or five) lines.",
                table("TAB MARKS", listOf("MARK", "MEANS"), listOf(
                    listOf("h", "hammer-on"), listOf("p", "pull-off"), listOf("/  \\", "slide up, slide down"), listOf("b", "bend"),
                    listOf("~", "vibrato"), listOf("x", "muted note"),
                ))),
        ),
        inShort = listOf(
            "Lines = strings, high string on top; numbers = frets.",
            "A column is a chord; a row is one note after another.",
            "Rhythm comes from the notation above the tab, or the recording.",
        ),
        hear = listOf(sound("PLAY THE TAB") { played(tabScore) }),
        related = listOf(page("names-octaves", "Octave numbers"), ref(RefKind.SCALE, "minor-pentatonic", "E minor pentatonic", "E")),
    )

    // ------------------------------------------------------------------------------------------------- charts and numbers

    private val DEGREES = mapOf(0 to "1", 1 to "♭2", 2 to "2", 3 to "♭3", 4 to "3", 5 to "4", 6 to "♭5", 7 to "5", 8 to "♭6", 9 to "6", 10 to "♭7", 11 to "7")
    /** A chord ("Am", "F/A", "Bb") as a Nashville number in [key] (major). */
    internal fun nashville(chord: String, key: String): String {
        val k = PitchName.parse(key).pitchClass
        val main = chord.substringBefore("/")
        val rootLen = if (main.length > 1 && main[1] in "#b") 2 else 1
        val root = PitchName.parse(main.take(rootLen)).pitchClass
        val quality = main.drop(rootLen).replace("7", "⁷")
        val bass = chord.substringAfter("/", "").takeIf { it.isNotEmpty() }?.let { DEGREES.getValue(Math.floorMod(PitchName.parse(it).pitchClass - k, 12)) }
        return DEGREES.getValue(Math.floorMod(root - k, 12)) + quality + (bass?.let { "/$it" } ?: "")
    }
    internal val CHART = listOf("C", "Am", "F", "G", "C/E", "F", "G7", "C", "Bb", "F/A", "G", "C")

    private val CHART_TOKENS = mapOf("C" to "1 3 5", "Am" to "6 1 3", "F" to "4 6 1", "G" to "5 7 2", "C/E" to "3|1 3 5", "G7" to "5 7 2 4", "Bb" to "b7 2 4", "F/A" to "6|4 6 1")
    private fun chartChord(c: String, i: Int) = cc(nashville(c, "C"), c.replace("b", "♭"), CHART_TOKENS.getValue(c), "bar ${i + 1}")
    private val leadSheet = staff("A LEAD SHEET · MELODY, SYMBOLS ABOVE", listOf("E4", "G4", "C5", "G4", "F4", "A4", "C5", "A4"),
        above = listOf("C", "", "", "", "F", "", "", ""), perBar = 4, step = 0.45).let { st ->
        st.copy(play = Play(listOf("E4", "G4", "C5", "G4", "F4", "A4", "C5", "A4").mapIndexed { i, n -> (if (i < 4) voiced("1 3 5", 48) else voiced("4 6 1", 48)) + mids(n) }, 0.45))
    }

    private val charts = CoursePage(
        "charts", "Chord charts", 2,
        "A chord chart gives the chords and the bars and leaves the rest to the player. It's the fastest way to write down " +
            "a song's harmony.",
        steps = listOf(
            say("One chord per bar is written once. Here are twelve bars in C, one chord each; tap a bar to hear it, ▶ to play " +
                "the chart.",
                chart("TWELVE BARS IN C", *CHART.mapIndexed { i, c -> chartChord(c, i) }.toTypedArray(), step = 1.0)),
            say("Two chords in a bar split it, and slashes (/) stand for beats when the split matters. A % repeats the previous " +
                "bar. A push, a chord that arrives an eighth before the bar, is marked with an accent or a tie over the bar line.",
                table("CHART MARKS", listOf("WRITTEN", "MEANS"), listOf(
                    listOf("C / / /", "C for four beats"), listOf("C / F /", "C for two beats, F for two"),
                    listOf("%", "the previous bar again"), listOf("^ or >", "a push: arrives early"),
                ), listOf(Play(List(4) { voiced("1 3 5") }, 0.6), Play(List(2) { voiced("1 3 5") } + List(2) { voiced("4 6 1") }, 0.6), null, null))),
            say("A lead sheet adds the melody on a staff with the chord symbols above it: the form jazz standards and most songbooks " +
                "use. Apophenia's chord charts read this way, with symbols typed in any dialect and stored as the chords themselves.",
                leadSheet),
        ),
        inShort = listOf(
            "Chart = chords and bars; the player fills in the rest.",
            "Slashes for beats, % for repeat, ^ for a push.",
            "Lead sheet = melody + symbols.",
        ),
        hear = listOf(sound("PLAY THE CHART") { r -> piano(CourseHarmony.prog(r, listOf("1 3 5", "6 1 3", "4 6 1", "5 7 2", "3|1 3 5", "4 6 1", "5 7 2 4", "1 3 5", "b7 2 4", "6|4 6 1", "5 7 2", "1 3 5"), 1.0)) }),
        related = listOf(page("nashville", "Nashville numbers"), page("form", "Song form")),
    )

    private val nashvillePage = CoursePage(
        "nashville", "Nashville numbers", 2,
        "The Nashville number system writes chords as scale degrees of the key instead of letters, so one chart works in " +
            "every key.",
        steps = listOf(
            say("In C, C is 1, Am is 6m (often written 6−), F is 4 and G is 5.",
                chart("1 6m 4 5 IN C", cc("1", "C", "1 3 5"), cc("6m", "Am", "6 1 3"), cc("4", "F", "4 6 1"), cc("5", "G", "5 7 2"))),
            say("A band reading numbers can play the song in any key the singer calls without rewriting the chart. The same numbers " +
                "in G:",
                chart("1 6m 4 5 IN G", cc("1", "G", "1 3 5", key = "G"), cc("6m", "Em", "6 1 3", key = "G"), cc("4", "C", "4 6 1", key = "G"), cc("5", "D", "5 7 2", key = "G"))),
            say("Chords outside the key get an accidental (B♭ in C is ♭7), inversions a slash (C/E is 1/3), and session charts add " +
                "their own marks, a diamond for a chord left to ring, a ^ for a push. It's the same idea as Roman numerals with " +
                "plainer typography, and it grew up in the Nashville studios of the 1950s and '60s.",
                table("THE TWELVE-BAR CHART IN NUMBERS", listOf("BARS", "IN C", "NUMBERS", "IN G"), CHART.chunked(4).mapIndexed { i, c ->
                    val g = mapOf("C" to "G", "Am" to "Em", "F" to "C", "G" to "D", "C/E" to "G/B", "G7" to "D7", "Bb" to "F", "F/A" to "C/E")
                    listOf("${i * 4 + 1}–${i * 4 + 4}", c.joinToString(" ").replace("b", "♭"), c.joinToString(" ") { nashville(it, "C") }, c.joinToString(" ") { g.getValue(it) })
                })),
        ),
        inShort = listOf(
            "Numbers = scale degrees of the key: 1 4 5 6m.",
            "Change key without rewriting the chart.",
            "♭7 for out-of-key chords, 1/3 for inversions.",
        ),
        hear = listOf(
            sound("1 6m 4 5 IN C") { r -> piano(CourseHarmony.prog(r, listOf("1 3 5", "6 1 3", "4 6 1", "5 7 2"), 1.0)) },
            sound("THE SAME NUMBERS IN G") { r -> piano(CourseHarmony.prog(r + 7, listOf("1 3 5", "6 1 3", "4 6 1", "5 7 2"), 1.0)) },
        ),
        related = listOf(page("numerals-major", "Roman numerals"), page("charts", "Chord charts")),
    )

    // ------------------------------------------------------------------------------------------------- road maps

    internal val roadmap = Score(parts = listOf(part(Clef.TREBLE).copy(bars = listOf(
        Bar(listOf(listOf(ev(w, "C4"))), repeatStart = true),
        Bar(listOf(listOf(ev(w, "D4"))), jumps = setOf(Jump.FINE)),
        Bar(listOf(listOf(ev(w, "E4"))), volta = listOf(1), end = Barline.REPEAT),
        Bar(listOf(listOf(ev(w, "F4"))), volta = listOf(2)),
        Bar(listOf(listOf(ev(w, "G4"))), jumps = setOf(Jump.DC_AL_FINE), end = Barline.FINAL),
    ))))
    internal fun playOrder() = ScoreTime.order(roadmap.parts.first()).map { it + 1 }

    private val repeatOnly = Score(parts = listOf(part(Clef.TREBLE).copy(bars = listOf(
        Bar(listOf(listOf(ev(w, "C4"))), repeatStart = true), Bar(listOf(listOf(ev(w, "D4"))), end = Barline.REPEAT), Bar(listOf(listOf(ev(w, "E4"))), end = Barline.FINAL),
    ))))
    private val endings = Score(parts = listOf(part(Clef.TREBLE).copy(bars = listOf(
        Bar(listOf(listOf(ev(w, "C4"))), repeatStart = true), Bar(listOf(listOf(ev(w, "D4")))),
        Bar(listOf(listOf(ev(w, "E4"))), volta = listOf(1), end = Barline.REPEAT), Bar(listOf(listOf(ev(w, "F4"))), volta = listOf(2), end = Barline.FINAL),
    ))))

    private val roadmapPage = CoursePage(
        "roadmap", "Repeats, endings and road maps", 3,
        "Written music saves space by sending you back. A few signs and words tell you the order to play the bars in.",
        steps = listOf(
            say("Repeat signs, a thick and thin bar line with two dots, enclose a passage to play twice. This plays C D, C D, then E.",
                one("A REPEAT · |: 1 2 :| 3", repeatOnly)),
            say("First and second endings, brackets marked 1. and 2., replace the last bars on each time through: C D E the first " +
                "time, C D F the second.",
                one("TWO ENDINGS · |: 1 2 [1. 3 :|] [2. 4]", endings)),
            say("Words do the rest. D.C. (da capo) means go back to the start, D.S. (dal segno) back to the sign. On the way back " +
                "after a D.C. or D.S., repeats are usually not taken again.",
                table("ROAD-MAP WORDS", listOf("", "MEANS"), listOf(
                    listOf("D.C.", "from the start"), listOf("D.S.", "from the sign (𝄋)"), listOf("al Fine", "then stop at Fine"),
                    listOf("al Coda", "then at To Coda jump to the Coda (𝄌)"),
                ))),
            say("Put together: five bars, a repeat with two endings and a D.C. al Fine. It plays bars ${playOrder().joinToString(" ")}, " +
                "worked out by the same code that plays scores in Apophenia.",
                CourseSee.Score("FIVE BARS · |: 1 2 [1. 3 :|] [2. 4] 5 D.C. AL FINE (FINE AFTER BAR 2)", roadmap, listOf(""))),
        ),
        inShort = listOf(
            "|: :| = play twice; 1. and 2. endings swap the last bars.",
            "D.C. = from the start, D.S. = from the sign; al Fine / al Coda say where to stop or jump.",
            "Repeats usually aren't taken again after a D.C. or D.S.",
        ),
        hear = listOf(sound("PLAY IT IN ORDER") { played(roadmap) }),
        check = null,
        related = listOf(page("form", "Song form")),
    )

    val topics = listOf(
        CourseTopic("staff", "The staff and clefs", "treble, bass, alto, the grand staff", 1, listOf(staff)),
        CourseTopic("notes-staff", "Notes on the staff", "lines, spaces, ledger lines", 1, listOf(linesPage, ledger)),
        CourseTopic("rhythm-page", "Rhythm on the page", "beams, rests, how bars add up", 2, listOf(barsPage)),
        CourseTopic("signatures-page", "Key and time signatures on the page", "where they sit, what they cancel", 2, listOf(signaturesPage)),
        CourseTopic("accidentals", "Accidentals in the bar", "how long a sharp lasts, courtesy accidentals", 2, listOf(accidentals)),
        CourseTopic("tab", "Tab", "strings, frets, techniques, rhythm in tab", 1, listOf(tab)),
        CourseTopic("charts", "Chord charts and the Nashville numbers", "slashes, pushes, numbers instead of letters", 2, listOf(charts, nashvillePage)),
        CourseTopic("roadmap", "Repeats, endings and road maps", "voltas, D.S., coda, fine", 3, listOf(roadmapPage)),
    )
}
