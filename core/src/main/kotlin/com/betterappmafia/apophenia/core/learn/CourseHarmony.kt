package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.catalog.Scales
import com.betterappmafia.apophenia.core.chord.ChordDisplay
import com.betterappmafia.apophenia.core.chord.ChordStyle
import com.betterappmafia.apophenia.core.interval.plus
import com.betterappmafia.apophenia.core.learn.CourseIntervals.shown
import com.betterappmafia.apophenia.core.pitch.PitchClass
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.sound.PlayNote

/*
 * Course · Harmony: the chords of a key and Roman numerals, function, cadences, secondary dominants, borrowed chords,
 * modulation, the Neapolitan and augmented sixths, jazz harmony (ii–V–I, guide tones, tritone subs, chord-scales), and
 * beyond the key (Coltrane changes, negative harmony, neo-Riemannian operations, pitch-class set theory). Each page is
 * walked through in steps; every picture is drawn in a fixed key (mostly C) and its ▶ plays exactly what's drawn. The
 * HEAR buttons at the end play the same ideas in the key chosen under TRY. Text written for Apophenia (Christopher,
 * 31 Oct 2026: "really teach it, not just give the facts").
 */
internal object CourseHarmony : CourseKit() {
    private val C = PitchName.parse("C")
    private fun p(s: String) = PitchName.parse(s)

    /** A progression on [tonic] (MIDI), each chord [step] seconds. */
    internal fun prog(tonic: Int, chords: List<String>, step: Double = 1.0, at: Double = 0.0): List<PlayNote> =
        chords.flatMapIndexed { i, c -> EarHarmony.voice(ch(c), tonic).map { PlayNote(at + i * step, at + i * step + step * 0.92, it, RefSound.VELOCITY) } }
    private fun play(tonic: Int, vararg chords: String, step: Double = 1.0) = piano(prog(tonic, chords.toList(), step))
    private fun ear(id: String, answer: String, r: Int) = piano(EarHarmony.sound(Ear.drill(id)!!, answer, r))

    // ------------------------------------------------------------------------------------------------- numerals

    /** The diatonic chords of [scaleId] on C: numeral, symbol, notes. */
    internal fun diatonic(scaleId: String, sevenths: Boolean): List<List<String>> {
        val key = KeyMode(C, scaleId)
        val page = ScalePages.page(Scales.byId(scaleId), C)
        return (if (sevenths) page.sevenths else page.triads).map { dc ->
            val sym = dc.symbol
            listOf(sym?.let { ChordDisplay.of(it, ChordStyle.CLASSICAL, key)?.text } ?: "${dc.degree}", sym?.let { accidentals(it).replace("b", "♭").replace("#", "♯").let { s -> shown(dc.root) + s.drop(dc.root.toString().length) } } ?: "", dc.notes.joinToString(" ") { shown(it) })
        }
    }

    private val MAJOR_TRIADS = listOf("I" to "1 3 5", "ii" to "2 4 6", "iii" to "3 5 7", "IV" to "4 6 1", "V" to "5 7 2", "vi" to "6 1 3", "vii°" to "7 2 4")
    private val MAJOR_SEVENTHS = listOf("Imaj7" to "1 3 5 7", "ii7" to "2 4 6 1", "iii7" to "3 5 7 2", "IVmaj7" to "4 6 1 3", "V7" to "5 7 2 4", "vi7" to "6 1 3 5", "viiø7" to "7 2 4 6")
    private val SYM_C = listOf("C", "Dm", "Em", "F", "G", "Am", "B°")
    private val SYM_G = listOf("G", "Am", "Bm", "C", "D", "Em", "F♯°")
    private val SYM_C7 = listOf("Cmaj7", "Dm7", "Em7", "Fmaj7", "G7", "Am7", "Bø7")

    private val majorKey = CoursePage(
        "numerals-major", "Chords of a major key", 2,
        "A key isn't only a scale. It's a family of seven chords, all made from the scale's seven notes, and once you know " +
            "how that family is built you can name any chord in a song by where it sits in the key. This page builds the " +
            "family from nothing, one step at a time.",
        steps = listOf(
            say("Start with the scale. C major is the seven white keys from C: C D E F G A B. Number them 1 to 7 from the " +
                "home note. Those numbers are scale degrees, and everything below is built from them.",
                keysLine("C MAJOR · DEGREES 1 TO 7", "C4 D4 E4 F4 G4 A4 B4 C5", listOf("1", "2", "3", "4", "5", "6", "7", "1"))),
            say("To build a chord on a note, take that note, skip the next scale note, take the one after, skip again, take the " +
                "next. Every other note. On C that gives C, E and G: the C major chord. Musicians call this stacking thirds, " +
                "because each jump (C up to E, E up to G) spans three letter names. The skipped notes are drawn outlined.",
                keys("ONE CHORD · EVERY OTHER NOTE FROM C", "C4 E4 G4", listOf("C", "E", "G"), faint = "D4 F4 A4 B4")),
            say("Now do exactly the same thing starting on every degree. Each row below is one chord: three lit notes, every " +
                "other note of the scale, written out twice so the chords near the top have room. No note outside C major " +
                "ever appears. That's what makes these seven chords belong together. Tap a row to hear its chord.",
                stackGrid("STACKED ON EVERY DEGREE OF C MAJOR", listOf("C", "D", "E", "F", "G", "A", "B"), MAJOR_TRIADS.map { it.first }, 3)),
            say("Why aren't they all major? Because the scale's steps aren't all the same size, so the thirds aren't either. " +
                "A third spanning 4 half steps is a major third; one spanning 3 is a minor third. A major chord has the major " +
                "third on the bottom; a minor chord has the minor third on the bottom. B has a minor third on top of a minor " +
                "third, so its outer notes are only 6 half steps apart instead of 7, and that squeezed chord is diminished. " +
                "Count it on the keyboard and you get this table. Tap a row to hear it.",
                table("COUNTING THE HALF STEPS", listOf("", "LOW 3RD", "UPPER 3RD", "OUTER", "SO IT'S"), listOf(
                    listOf("C", "C–E 4", "E–G 3", "7", "major"), listOf("Dm", "D–F 3", "F–A 4", "7", "minor"), listOf("Em", "E–G 3", "G–B 4", "7", "minor"),
                    listOf("F", "F–A 4", "A–C 3", "7", "major"), listOf("G", "G–B 4", "B–D 3", "7", "major"), listOf("Am", "A–C 3", "C–E 4", "7", "minor"),
                    listOf("B°", "B–D 3", "D–F 3", "6", "diminished"),
                ), MAJOR_TRIADS.map { Play(listOf(voiced(it.second)), 1.4, spread = true) })),
            say("Roman numerals name each chord by the degree it's built on, and their case shows its quality: upper case for " +
                "major (I, IV, V), lower case for minor (ii, iii, vi), and a small circle for diminished (vii°). The chart " +
                "plays exactly these seven chords in order; tap any one to hear it alone.",
                chart("THE SEVEN CHORDS OF C MAJOR", *MAJOR_TRIADS.mapIndexed { i, (n, t) -> cc(n, SYM_C[i], t, listOf("home", "", "", "", "pulls home", "", "tense")[i]) }.toTypedArray())),
            say("The numbers are the point. Move to G major and every chord moves with the key, but the pattern of qualities " +
                "stays exactly the same: major, minor, minor, major, major, minor, diminished. So \"a one–four–five in G\" " +
                "means G, C and D, and any band can follow it in any key without reading a single chord name.",
                chart("THE SAME SEVEN IN G MAJOR", *MAJOR_TRIADS.mapIndexed { i, (n, t) -> cc(n, SYM_G[i], t, key = "G") }.toTypedArray())),
            say("Stack one more third and every chord gets a seventh. The qualities still fall the same way in every major key: " +
                "I and IV become major sevenths (bright, settled), ii, iii and vi minor sevenths (soft), V alone becomes a " +
                "dominant seventh (major chord, minor 7th: the most restless sound in the key), and vii becomes half-" +
                "diminished (ø7). Tap the rows to compare.",
                stackGrid("ONE MORE THIRD · THE SEVENTH CHORDS", listOf("C", "D", "E", "F", "G", "A", "B"), MAJOR_SEVENTHS.map { it.first }, 4)),
            say("Here they are as a chart, with the symbols you'd see on a lead sheet. The V7 is the one to listen for: it's " +
                "the only chord in the key with the tritone (B and F) inside a major chord, and that tension is what makes it " +
                "want to go to I.",
                chart("THE SEVENTH CHORDS OF C MAJOR", *MAJOR_SEVENTHS.mapIndexed { i, (n, t) -> cc(n, SYM_C7[i], t, if (i == 4) "the only dominant" else "") }.toTypedArray())),
            say("Songs don't walk up the scale like the charts above. They pick a few of the seven chords and move between them. " +
                "These two orderings of the same four chords run through decades of pop. Play them, then find each chord in " +
                "the chart of C major above.",
                chart("FOUR CHORDS, TWO ORDERS", cc("I", "C", "1 3 5"), cc("V", "G", "5 7 2"), cc("vi", "Am", "6 1 3"), cc("IV", "F", "4 6 1"),
                    cc("I", "C", "1 3 5"), cc("vi", "Am", "6 1 3"), cc("IV", "F", "4 6 1"), cc("V", "G", "5 7 2"))),
        ),
        inShort = listOf(
            "Build a chord on each scale degree by taking every other note: seven chords, no outside notes.",
            "In every major key: major on I, IV, V; minor on ii, iii, vi; diminished on vii°.",
            "With sevenths: Imaj7, ii7, iii7, IVmaj7, V7, vi7, viiø7; only V is a dominant seventh.",
            "Roman numerals count from the tonic, so one progression works in every key.",
        ),
        hear = listOf(
            sound("I ii iii IV V vi vii° I") { r -> play(r, "1 3 5", "2 4 6", "3 5 7", "4 6 1", "5 7 2", "6 1 3", "7 2 4", "1 3 5", step = 0.9) },
            sound("THE SEVENTHS") { r -> play(r, "1 3 5 7", "2 4 6 1", "3 5 7 2", "4 6 1 3", "5 7 2 4", "6 1 3 5", "7 2 4 6", "1 3 5 7", step = 1.1) },
            sound("I V vi IV") { r -> play(r, "1 3 5", "5 7 2", "6 1 3", "4 6 1", step = 1.0) },
        ),
        tryIt = CourseTry("THE KEY'S SCALE · ITS CHORDS ARE ON THE SCALE'S PAGE", RefKind.SCALE, "major", "C"),
        check = CourseCheck("After the cadence, one chord: I, IV, V or vi?", listOf("I", "IV", "V", "vi").map { n ->
            option(n, "$n: ${mapOf("I" to "the tonic", "IV" to "the subdominant", "V" to "the dominant", "vi" to "the relative minor").getValue(n)}.") { r, _ -> ear("hm-major", n, r) }
        }),
        related = listOf(ref(RefKind.SCALE, "major", "C major"), ear("hm-major", "chords in a major key"), page("function", "What each chord does")),
    )

    private val MINOR_TRIADS = listOf("i" to "1 b3 5", "ii°" to "2 4 b6", "III" to "b3 5 b7", "iv" to "4 b6 1", "v" to "5 b7 2", "VI" to "b6 1 b3", "VII" to "b7 2 4")

    private val minorKey = CoursePage(
        "numerals-minor", "Chords of a minor key", 2,
        "A minor key builds its chords exactly the way a major key does, every other note of its scale. The surprise is " +
            "that minor keys almost never use all of those chords as they come: one note gets raised, and that single change " +
            "is behind the sound of nearly every classical minor cadence.",
        steps = listOf(
            say("A natural minor scale is the major scale started from its 6th degree. A minor uses the same seven white keys as " +
                "C major, just with A as home: A B C D E F G. Same notes, different centre of gravity.",
                keysLine("A NATURAL MINOR", "A3 B3 C4 D4 E4 F4 G4 A4", listOf("1", "2", "♭3", "4", "5", "♭6", "♭7", "1"))),
            say("Stack every other note on each degree, exactly as in major. The chords come out as i, ii°, III, iv, v, VI and " +
                "VII: Am, B°, C, Dm, Em, F and G, the same seven chords as C major, numbered from A.",
                stackGrid("STACKED ON A NATURAL MINOR", listOf("A", "B", "C", "D", "E", "F", "G"), MINOR_TRIADS.map { it.first }, 3, octave = 3)),
            say("Here they are as a chart. Lower case still means minor and upper case major. III is the major chord on the " +
                "flat 3rd (C, the relative major), VI and VII are major chords a step apart, the pair that so much rock and film " +
                "music leans on.",
                chart("THE CHORDS OF A NATURAL MINOR", *MINOR_TRIADS.mapIndexed { i, (n, t) -> cc(n, listOf("Am", "B°", "C", "Dm", "Em", "F", "G")[i], t, listOf("home", "", "relative major", "", "weak pull", "", "")[i], key = "A") }.toTypedArray())),
            say("The weak spot is v. In a major key, V's middle note is the leading tone, a half step below home (B under C), and " +
                "that half step is what pulls the ear back to the tonic. In natural minor, v is E minor, and its middle note G " +
                "sits a whole step below A. Nothing leans on home. Compare the two: E G B, then E G♯ B.",
                keys("Em AND E · THE ONE NOTE THAT CHANGES", "E4 G4 B4", listOf("E", "G", "B"), faint = "A4", play = Play(listOf(mids("E4 G4 B4"), mids("E4 G#4 B4"), mids("E4 A4 C5")), 1.3))),
            say("So classical minor raises the 7th degree a half step, G to G♯, only where the dominant needs it. The scale with " +
                "that raised 7th is harmonic minor. The gap from F up to G♯ is three half steps, which gives the scale its " +
                "exotic-sounding leap.",
                keysLine("A HARMONIC MINOR · G♯ IS THE RAISED 7TH", "A3 B3 C4 D4 E4 F4 G#4 A4", listOf("1", "2", "♭3", "4", "5", "♭6", "7", "1"))),
            say("The raised 7th turns v into a major V (E G♯ B) and VII into vii° (G♯ B D), both of which now lead home. Play " +
                "the same four-chord phrase both ways and listen to the third chord: soft with Em, insistent with E.",
                chart("i iv v i · THEN i iv V i", cc("i", "Am", "1 b3 5", key = "A"), cc("iv", "Dm", "4 b6 1", key = "A"), cc("v", "Em", "5 b7 2", "natural", "A"), cc("i", "Am", "1 b3 5", key = "A"),
                    cc("i", "Am", "1 b3 5", key = "A"), cc("iv", "Dm", "4 b6 1", key = "A"), cc("V", "E", "5 7 2", "raised 7th", "A"), cc("i", "Am", "1 b3 5", key = "A"))),
            say("Which you hear depends on the style. Classical and much jazz use the major V. Pop, rock and folk often keep the " +
                "natural v and especially VII, which gives a modal, less churchy sound: Am–G–F–G rocks back and forth without " +
                "ever cadencing. Neither is more correct; they're different dialects of minor.",
                chart("A ROCK MINOR LOOP", cc("i", "Am", "1 b3 5", key = "A"), cc("VII", "G", "b7 2 4", key = "A"), cc("VI", "F", "b6 1 b3", key = "A"), cc("VII", "G", "b7 2 4", key = "A"))),
        ),
        inShort = listOf(
            "Natural minor's chords: i ii° III iv v VI VII, the relative major's chords from a new home.",
            "Raise the 7th (harmonic minor) and v becomes V, VII becomes vii°: both pull to i.",
            "Classical minor uses V; rock and folk minor often keep v and VII.",
        ),
        hear = listOf(
            sound("i iv v i · NATURAL") { r -> play(r, "1 b3 5", "4 b6 1", "5 b7 2", "1 b3 5") },
            sound("i iv V i · THE RAISED 7TH") { r -> play(r, "1 b3 5", "4 b6 1", "5 7 2", "1 b3 5") },
            sound("i VII VI VII") { r -> play(r, "1 b3 5", "b7 2 4", "b6 1 b3", "b7 2 4") },
        ),
        tryIt = CourseTry("HARMONIC MINOR · WHERE THE MAJOR V COMES FROM", RefKind.SCALE, "harmonic-minor", "A"),
        check = CourseCheck("Into the minor tonic: from a minor v, or a major V?", listOf(
            option("v", "The minor v: natural minor, softer.") { r, _ -> play(r, "1 b3 5", "4 b6 1", "5 b7 2", "1 b3 5") },
            option("V", "The major V: the raised 7th leads home.") { r, _ -> play(r, "1 b3 5", "4 b6 1", "5 7 2", "1 b3 5") },
        )),
        related = listOf(ear("hm-minor", "chords in a minor key"), page("minor-harmonic", "Harmonic minor"), page("numerals-major", "Chords of a major key")),
    )

    // ------------------------------------------------------------------------------------------------- function

    private val function = CoursePage(
        "function", "Tonic, predominant, dominant", 2,
        "Seven chords would be a lot to keep track of if each had its own personality. In practice they sort into three " +
            "jobs, and a progression is mostly a trip between those jobs: home, getting ready to leave, and wanting to come " +
            "back. Once you hear the jobs, you can predict where a progression is going.",
        steps = listOf(
            say("Tonic means home. I is the purest home chord. vi and iii can stand in for it because each shares two of I's " +
                "three notes: look down the columns and you'll see C, E and G turning up again and again. Tap the rows: vi " +
                "sounds like a darker home, iii like home seen from a slight angle.",
                noteGrid("TONIC CHORDS SHARE NOTES WITH I", "C D E F G A B", listOf("I" to "1 3 5", "vi" to "6 1 3", "iii" to "3 5 7"))),
            say("Dominant means pulling home. V and vii° both contain B, the leading tone, a half step under C. Add the 7th to V " +
                "and it also holds F. B and F together are a tritone, the most unstable interval in the key, and they resolve " +
                "by half steps: B up to C, F down to E. Those two half-step moves are the whole force of V7–I.",
                noteGrid("DOMINANT CHORDS HOLD THE LEADING TONE", "C D E F G A B", listOf("V7" to "5 7 2 4", "vii°" to "7 2 4", "I" to "1 3 5"))),
            say("Here's that resolution on the staff. Follow each line of notes from the first chord to the second: B rises to " +
                "C, F falls to E, G stays put. Nothing jumps; the tension just melts.",
                staff("G7 → C · THE TRITONE RESOLVES", listOf("B3 F4 G4", "C4 E4 G4"), above = listOf("G7", "C"), below = listOf("V7", "I"), step = 1.4)),
            say("Predominant means getting ready to go to the dominant. IV and ii share F and A, and both move naturally to V: " +
                "IV's root rises a step to V's, ii's root falls a fifth. ii is the stronger of the two, which is why ii–V is " +
                "everywhere in jazz.",
                noteGrid("PREDOMINANT CHORDS SHARE F AND A", "C D E F G A B", listOf("IV" to "4 6 1", "ii" to "2 4 6", "V" to "5 7 2"))),
            say("Put the jobs in order and you get the basic shape of tonal music: tonic, predominant, dominant, tonic. Read " +
                "the jobs under each chord, then play it. Both charts are the same journey; the second takes the long way, " +
                "using the substitutes.",
                chart("HOME · AWAY · PULL · HOME", cc("I", "C", "1 3 5", "tonic"), cc("IV", "F", "4 6 1", "predominant"), cc("V", "G", "5 7 2", "dominant"), cc("I", "C", "1 3 5", "tonic"),
                    cc("I", "C", "1 3 5", "tonic"), cc("vi", "Am", "6 1 3", "tonic"), cc("ii", "Dm", "2 4 6", "predominant"), cc("V7", "G7", "5 7 2 4", "dominant"), cc("I", "C", "1 3 5", "tonic"))),
            say("Chords with the same job can swap. The most famous swap is the deceptive move: V sets up I, and vi arrives " +
                "instead. vi is a tonic substitute, so it still feels like a landing, but on the wrong foot, and the song has to " +
                "keep going.",
                chart("THE DECEPTIVE SWAP", cc("I", "C", "1 3 5", "tonic"), cc("IV", "F", "4 6 1", "predominant"), cc("V", "G", "5 7 2", "dominant"), cc("vi", "Am", "6 1 3", "tonic stand-in"))),
            say("Rock often runs the jobs backwards: V–IV–I (G–F–C), dominant to predominant to tonic. It still lands home, but " +
                "the pull is gone, which is part of why it sounds loose and open rather than classical. Function explains the " +
                "difference even when the chords are identical.",
                chart("BACKWARDS · V IV I", cc("V", "G", "5 7 2", "dominant"), cc("IV", "F", "4 6 1", "predominant"), cc("I", "C", "1 3 5", "tonic"))),
        ),
        inShort = listOf(
            "Tonic (home): I, and vi or iii in its place.",
            "Predominant (leaving): IV and ii.",
            "Dominant (pulling home): V and vii°, held together by the leading tone and the tritone.",
            "Tonic → predominant → dominant → tonic is the basic sentence; chords with the same job can swap.",
        ),
        hear = listOf(
            sound("T → PD → D → T · I IV V I") { r -> play(r, "1 3 5", "4 6 1", "5 7 2", "1 3 5") },
            sound("I vi ii V I") { r -> play(r, "1 3 5", "6 1 3", "2 4 6", "5 7 2 4", "1 3 5") },
        ),
        check = CourseCheck("Before the last I: a predominant (IV) or a dominant (V)?", listOf(
            option("IV", "IV, a predominant: a gentler arrival.") { r, _ -> play(r, "1 3 5", "6 1 3", "4 6 1", "1 3 5") },
            option("V", "V, the dominant: a strong pull home.") { r, _ -> play(r, "1 3 5", "6 1 3", "5 7 2", "1 3 5") },
        )),
        related = listOf(page("cadences", "Cadences"), ear("hm-major", "chords in a major key")),
    )

    private val cadences = CoursePage(
        "cadences", "Cadences", 2,
        "A cadence is the end of a phrase, the musical punctuation mark. Most phrases in tonal music end in one of four " +
            "ways, and each one is just two chords. Learn the four and you'll hear where every phrase in a song lands: a " +
            "full stop, a softer full stop, a comma, or a surprise.",
        steps = listOf(
            say("The authentic cadence goes V to I: dominant to tonic, the strongest close there is. When both chords have their " +
                "root in the bass and the top note lands on the tonic, it's called a perfect authentic cadence, the most final " +
                "ending possible. Watch the top line on the staff: C, C, B, C. It ends on the home note.",
                staff("PERFECT AUTHENTIC · THE TOP NOTE ENDS ON C", listOf("E4 G4 C5", "F4 A4 C5", "D4 G4 B4", "E4 G4 C5"), above = listOf("C", "F", "G", "C"), below = listOf("I", "IV", "V", "I"), step = 1.1)),
            say("Keep the same chords but end with E on top instead of C, and the close is a little less final: an imperfect " +
                "authentic cadence. The chords haven't changed, only the melody's last note. Compare the two.",
                staff("IMPERFECT AUTHENTIC · THE TOP NOTE ENDS ON E", listOf("G4 C5 E5", "A4 C5 F5", "G4 B4 D5", "G4 C5 E5"), above = listOf("C", "F", "G", "C"), below = listOf("I", "IV", "V", "I"), step = 1.1)),
            say("The plagal cadence goes IV to I. There's no leading tone pulling up, so the arrival is gentle: the \"amen\" at " +
                "the end of a hymn. It often comes after an authentic cadence has already closed the piece, as an afterword.",
                chart("PLAGAL · IV → I", cc("I", "C", "1 3 5"), cc("IV", "F", "4 6 1", "predominant"), cc("I", "C", "1 3 5", "home, gently"))),
            say("The half cadence stops on V. The phrase ends, but on the chord that most wants to move, so it sounds like a " +
                "comma or a question. Songs often put one at the end of a verse to throw you into the chorus.",
                chart("HALF · STOP ON V", cc("I", "C", "1 3 5"), cc("vi", "Am", "6 1 3"), cc("ii", "Dm", "2 4 6", "predominant"), cc("V", "G", "5 7 2", "stops here"))),
            say("The deceptive cadence sets up V to I, then goes to vi. Your ear expects home, gets a near relative of home, and " +
                "the phrase has to continue. It's the classic way to extend a song's last phrase before the real ending.",
                chart("DECEPTIVE · V → vi", cc("I", "C", "1 3 5"), cc("IV", "F", "4 6 1"), cc("V", "G", "5 7 2", "sets up I"), cc("vi", "Am", "6 1 3", "surprise"))),
            say("In a minor key there's one more to know: the Phrygian half cadence, iv in first inversion to V (in A minor, " +
                "Dm with F in the bass to E). The bass falls by a half step, F to E, which gives it its old, dark sound. It's " +
                "the cadence at the end of many Baroque slow movements.",
                staff("PHRYGIAN HALF CADENCE IN A MINOR", listOf("F4 A4 D5", "E4 G#4 B4"), above = listOf("Dm/F", "E"), below = listOf("iv⁶", "V"), step = 1.6)),
        ),
        inShort = listOf(
            "Authentic: V → I, the full stop (perfect when roots are in the bass and the melody ends on the tonic).",
            "Plagal: IV → I, the amen.",
            "Half: ends on V, a comma.",
            "Deceptive: V → vi, the surprise that keeps the phrase going.",
        ),
        hear = EarHarmony.CADENCES.map { c -> sound(c.uppercase()) { r -> ear("hm-cadence", c, r) } },
        check = CourseCheck("How does the phrase end?", EarHarmony.CADENCES.map { c ->
            option(c.replaceFirstChar(Char::uppercase), mapOf("authentic" to "Authentic: V to I.", "plagal" to "Plagal: IV to I.", "half" to "Half: it stops on V.", "deceptive" to "Deceptive: V to vi.").getValue(c)) { r, _ -> ear("hm-cadence", c, r) }
        }),
        related = listOf(ear("hm-cadence", "cadences"), page("function", "Function")),
    )

    // ------------------------------------------------------------------------------------------------- secondary

    private val SECONDARIES = listOf(
        Triple("V7/ii", "A7" to "6 #1 3 5", "Dm" to "2 4 6"), Triple("V7/iii", "B7" to "7 #2 #4 6", "Em" to "3 5 7"),
        Triple("V7/IV", "C7" to "1 3 5 b7", "F" to "4 6 1"), Triple("V7/V", "D7" to "2 #4 6 1", "G" to "5 7 2"), Triple("V7/vi", "E7" to "3 #5 7 2", "Am" to "6 1 3"),
    )

    private val secondary = CoursePage(
        "secondary", "Secondary dominants", 3,
        "Only one chord in a major key is a true dominant: V, pulling to I. A secondary dominant borrows that pull for any " +
            "other chord, for a moment making it feel like home. It's how songs get chromatic notes and extra momentum " +
            "without changing key.",
        steps = listOf(
            say("Start with what makes V work. In C, G7 (G B D F) contains B, which sits a half step under C and wants to rise " +
                "to it. No other chord in C major has a note a half step under its target's root. Dm, F, Am: nothing points " +
                "at them.",
                keys("G7 · B IS A HALF STEP UNDER C", "G3 B3 D4 F4", listOf("G", "B", "D", "F"), faint = "C4")),
            say("Now pretend G is home for a moment. G's own dominant is D7: D F♯ A C. The F♯ isn't in C major. It's G's leading " +
                "tone, a half step under G, and it's the borrowed note that makes the chord point at G. Written in C, D7 is " +
                "V7/V, \"five of five\".",
                keys("D7 · F♯ IS A HALF STEP UNDER G", "D4 F#4 A4 C5", listOf("D", "F♯", "A", "C"), faint = "G4")),
            say("Follow the lines on the staff as D7 moves to G: F♯ rises to G, C falls to B, D stays, A falls to G. It's the " +
                "same resolution as G7 to C, moved up a fifth.",
                staff("D7 → G · V7/V RESOLVING", listOf("A3 C4 D4 F#4", "G3 B3 D4 G4"), above = listOf("D7", "G"), below = listOf("V7/V", "V"), step = 1.4)),
            say("Every major or minor chord of the key can have its own dominant. Each row plays the dominant, then the chord it " +
                "points at. Notice the chromatic note in each one: it's always the target's leading tone (C♯ to D, D♯ to E, G♯ " +
                "to A), except in C7, whose B♭ falls to A instead.",
                table("THE SECONDARY DOMINANTS OF C", listOf("", "CHORD", "NOTES", "LEADS TO"), SECONDARIES.map { (n, d, t) -> listOf(n, d.first, spell(d.second), t.first) },
                    SECONDARIES.map { (_, d, t) -> Play(listOf(voiced(d.second), voiced(t.second)), 1.2) })),
            say("How to spot one in a song: a major or dominant-seventh chord where the key expects a minor one (A7 where Am " +
                "would be, E7 where Em would be), followed by a chord a fifth lower. This progression uses two: A7 points at " +
                "Dm, and G7 is the ordinary V.",
                chart("A SECONDARY DOMINANT IN A PROGRESSION", cc("I", "C", "1 3 5"), cc("V7/ii", "A7", "6 #1 3 5", "points at Dm"), cc("ii", "Dm", "2 4 6"), cc("V7", "G7", "5 7 2 4"), cc("I", "C", "1 3 5"))),
            say("vii° is never tonicised, because a diminished chord can't sound like a home, even briefly. The dominant can also " +
                "be replaced by its own leading-tone chord: F♯°7 points at G as strongly as D7 does, with the F♯ in the bass. " +
                "That's a secondary leading-tone chord, written vii°7/V.",
                chart("THE OTHER WAY TO POINT AT G", cc("I", "C", "1 3 5"), cc("vii°7/V", "F♯°7", "#4 6 1 b3", "leading-tone chord"), cc("V", "G", "5 7 2"), cc("I", "C", "1 3 5"))),
        ),
        inShort = listOf(
            "A secondary dominant is the V7 of a chord other than I: V7/V, V7/vi, and so on.",
            "Its outside note is usually the target's leading tone, a half step under the target's root.",
            "Spot it as a major or dominant-7 chord where the key expects minor, resolving down a fifth.",
            "vii° is never tonicised; vii°7/x is the leading-tone version of the same idea.",
        ),
        hear = listOf("V7/V", "V7/vi", "V7/IV").map { s -> sound("I · $s · ITS CHORD") { r -> ear("hm-secondary", s, r) } },
        check = CourseCheck("Which chord does the dominant point at?", listOf("V7/ii" to "ii", "V7/IV" to "IV", "V7/V" to "V", "V7/vi" to "vi").map { (s, t) ->
            option(t, "$s: the dominant of $t.") { r, _ -> ear("hm-secondary", s, r) }
        }),
        related = listOf(ear("hm-secondary", "secondary dominants"), ref(RefKind.CHORD, "7", "D7", "D"), page("secondary-chains", "Chains of dominants")),
    )

    private val chains = CoursePage(
        "secondary-chains", "Chains of dominants", 3,
        "If any chord can have a dominant, a dominant can have a dominant too. String them together and each chord resolves " +
            "into the next, which is itself a dominant, so the music keeps falling forward until it reaches home.",
        steps = listOf(
            say("E7 is the V of A. A7 is the V of D. D7 is the V of G. G7 is the V of C. Play them in a row and the roots walk " +
                "round the circle of fifths, every chord a dominant pointing at the next.",
                chart("E7 → A7 → D7 → G7 → C", cc("V7/vi", "E7", "3 #5 7 2", "V of A"), cc("V7/ii", "A7", "6 #1 3 5", "V of D"), cc("V7/V", "D7", "2 #4 6 1", "V of G"), cc("V7", "G7", "5 7 2 4", "V of C"), cc("I", "C", "1 3 5", "home"))),
            say("The magic is in the inner voices. Each chord's 3rd falls a half step to become the next chord's 7th, and each " +
                "7th falls a half step to become the next 3rd. So while the roots jump by fifths, two voices slide down the " +
                "chromatic scale. Read along each row.",
                CourseSee.Grid("THE 3RDS AND 7THS SLIDE DOWN", cols("E A D G C").map { it + if (it == "C") "" else "7" }, listOf(
                    row("upper", cols("G# G F# F E"), setOf(0, 1, 2, 3, 4), Play(mids("G#4 G4 F#4 F4 E4").map { listOf(it) }, 0.6)),
                    row("lower", cols("D C# C B C"), setOf(0, 1, 2, 3, 4), Play(mids("D4 C#4 C4 B3 C4").map { listOf(it) }, 0.6)),
                ))),
            say("Here's the same thing on the staff with just those two voices, the shell of each chord. Played with the roots " +
                "underneath, it's the whole chain; played alone, you can hear the two lines sliding.",
                staff("THE TWO SLIDING VOICES", listOf("D4 G#4", "C#4 G4", "C4 F#4", "B3 F4", "C4 E4"), above = listOf("E7", "A7", "D7", "G7", "C"), step = 1.0)),
            say("Chains like this are all over ragtime, early jazz and standards. The bridge of rhythm changes (the \"I Got " +
                "Rhythm\" form) is a four-chord chain: III7–VI7–II7–V7, in B♭ that's D7 G7 C7 F7, landing back on B♭.",
                chart("A RHYTHM-CHANGES BRIDGE IN B♭", cc("III7", "D7", "3 #5 7 2", key = "Bb"), cc("VI7", "G7", "6 #1 3 5", key = "Bb"), cc("II7", "C7", "2 #4 6 1", key = "Bb"), cc("V7", "F7", "5 7 2 4", key = "Bb"), cc("I", "B♭", "1 3 5", key = "Bb"))),
            say("Jazz players often put the ii of each dominant in front of it, so every dominant gets its own ii–V: Bm7 E7, " +
                "Em7 A7, Am7 D7, Dm7 G7. That's called backcycling, and it's why long chains of ii–Vs feel natural.",
                chart("THE CHAIN AS ii–Vs", cc("ii/vi", "Bm7", "7 2 #4 6"), cc("V7/vi", "E7", "3 #5 7 2"), cc("ii/ii", "Em7", "3 5 7 2"), cc("V7/ii", "A7", "6 #1 3 5"),
                    cc("ii/V", "Am7", "6 1 3 5"), cc("V7/V", "D7", "2 #4 6 1"), cc("ii", "Dm7", "2 4 6 1"), cc("V7", "G7", "5 7 2 4"), cc("I", "C", "1 3 5"), step = 0.8)),
        ),
        inShort = listOf(
            "Dominants can chain: each one resolves to the next, roots moving round the circle of fifths.",
            "3rd → next 7th, 7th → next 3rd: two voices slide down by half steps.",
            "Put a ii in front of each and you get a chain of ii–Vs.",
        ),
        hear = listOf(sound("E7 · A7 · D7 · G7 · C") { r -> play(r, "3 #5 7 2", "6 #1 3 5", "2 #4 6 1", "5 7 2 4", "1 3 5") }),
        check = CourseCheck("A chain of dominants, or the plain I vi ii V?", listOf(
            option("Dominants", "The chain: every chord a dominant seventh, E7 A7 D7 G7.") { r, _ -> play(r, "3 #5 7 2", "6 #1 3 5", "2 #4 6 1", "5 7 2 4", "1 3 5") },
            option("Diatonic", "The plain diatonic version: Em Am Dm G7.") { r, _ -> play(r, "3 5 7 2", "6 1 3 5", "2 4 6 1", "5 7 2 4", "1 3 5") },
        )),
        related = listOf(page("circle-fifths", "The circle of fifths"), page("jazz-251", "ii–V–I")),
    )

    // ------------------------------------------------------------------------------------------------- mixture

    private val mixture = CoursePage(
        "mixture", "Borrowed chords", 3,
        "A major key can borrow chords from the minor key on the same tonic, its parallel minor. The home stays where it is, " +
            "but for a chord or two the colour turns darker. A lot of the bittersweet sound in pop, rock and film music is " +
            "exactly this.",
        steps = listOf(
            say("C major and C minor share their tonic and four other notes. Three notes differ: C minor has E♭, A♭ and B♭ where " +
                "C major has E, A and B. Compare the two rows: those three columns are the only ones that move.",
                CourseSee.Grid("C MAJOR AND C MINOR, NOTE BY NOTE", cols("C D Eb E F G Ab A Bb B"), listOf(
                    scaleRow("C major", cols("C D Eb E F G Ab A Bb B"), "C4 D4 E4 F4 G4 A4 B4 C5"),
                    scaleRow("C minor", cols("C D Eb E F G Ab A Bb B"), "C4 D4 Eb4 F4 G4 Ab4 Bb4 C5"),
                ))),
            say("Any chord of C minor that uses those notes can be borrowed into C major. These are the common ones. Tap each and " +
                "listen for the darker note inside it.",
                chart("BORROWED FROM C MINOR", cc("iv", "Fm", "4 b6 1", "A♭"), cc("♭III", "E♭", "b3 5 b7", "E♭ B♭"), cc("♭VI", "A♭", "b6 1 b3", "A♭ E♭"), cc("♭VII", "B♭", "b7 2 4", "B♭"), cc("ii°", "D°", "2 4 b6", "A♭"))),
            say("The minor iv is the classic. Play F, then Fm, then C: the only note that changes is A falling to A♭, which then " +
                "falls again to G in the C chord. A half-step sigh, two times over. Follow the middle line on the staff.",
                staff("IV → iv → I · A, A♭, G", listOf("F4 A4 C5", "F4 Ab4 C5", "E4 G4 C5"), above = listOf("F", "Fm", "C"), below = listOf("IV", "iv", "I"), step = 1.4)),
            say("♭VI–♭VII–I arrives home without any dominant at all: two major chords a whole step apart, climbing into the " +
                "tonic. It's a rock and film-score ending, triumphant rather than resolved.",
                chart("♭VI → ♭VII → I", cc("I", "C", "1 3 5"), cc("♭VI", "A♭", "b6 1 b3", "borrowed"), cc("♭VII", "B♭", "b7 2 4", "borrowed"), cc("I", "C", "1 3 5", "home"))),
            say("♭VII on its own, going straight back to I, gives a Mixolydian lean: the B♭ replaces the leading tone, so the " +
                "music sounds bluesy and unhurried rather than classical.",
                chart("I ♭VII IV I", cc("I", "C", "1 3 5"), cc("♭VII", "B♭", "b7 2 4", "borrowed"), cc("IV", "F", "4 6 1"), cc("I", "C", "1 3 5"))),
            say("It works the other way too. A piece in minor that ends on a major tonic chord has a Picardy third: the 3rd of the " +
                "last chord raised, E♭ to E in C minor. After a whole minor phrase, the last chord opens up like light.",
                chart("i iv V I · A PICARDY THIRD", cc("i", "Cm", "1 b3 5"), cc("iv", "Fm", "4 b6 1"), cc("V", "G", "5 7 2"), cc("I", "C", "1 3 5", "major at the end"))),
        ),
        inShort = listOf(
            "The parallel minor shares the tonic; it differs by ♭3, ♭6 and ♭7.",
            "Common borrowings into major: iv, ♭III, ♭VI, ♭VII, ii°.",
            "IV–iv–I sighs by half steps; ♭VI–♭VII–I lands without a dominant.",
            "Minor ending on a major tonic chord: a Picardy third.",
        ),
        hear = listOf(
            sound("IV · iv · I") { r -> play(r, "1 3 5", "4 6 1", "4 b6 1", "1 3 5") },
            sound("♭VI · ♭VII · I") { r -> play(r, "1 3 5", "b6 1 b3", "b7 2 4", "1 3 5") },
            sound("i iv V I · PICARDY THIRD") { r -> play(r, "1 b3 5", "4 b6 1", "5 7 2", "1 3 5") },
        ),
        check = CourseCheck("I, a borrowed chord, I. Which one?", listOf("iv", "bVI", "bVII").map { b ->
            option(EarHarmony.label(Ear.drill("hm-borrowed")!!, b), "${EarHarmony.label(Ear.drill("hm-borrowed")!!, b)}, from the parallel minor.") { r, _ -> ear("hm-borrowed", b, r) }
        }),
        related = listOf(ear("hm-borrowed", "borrowed chords"), page("minor-natural", "Natural minor")),
    )

    // ------------------------------------------------------------------------------------------------- modulation

    private val pivot = CoursePage(
        "modulation-pivot", "Changing key: pivot chords", 3,
        "To modulate is to move home: after a while in C, the music settles in G and stays there. The smoothest way to do it " +
            "is through a chord that belongs to both keys, so the listener crosses the border without noticing the step.",
        steps = listOf(
            say("C major and G major are neighbours: their scales differ by one note (F in C, F♯ in G). So they share four " +
                "chords. Each row below is one shared chord, with its numeral in each key. Tap one to hear it.",
                table("CHORDS THAT BELONG TO C AND G", listOf("", "IN C", "IN G"), listOf(listOf("C", "I", "IV"), listOf("Em", "iii", "vi"), listOf("G", "V", "I"), listOf("Am", "vi", "ii")),
                    listOf("1 3 5", "3 5 7", "5 7 2", "6 1 3").map { Play(listOf(voiced(it)), 1.4, spread = true) })),
            say("Pick one of those as the pivot. Am is vi in C, but if the next chord is D7, the ear re-reads Am as ii in G, and " +
                "D7–G confirms it. Read the tags: the second chord is where the key quietly changes hands.",
                chart("C → G THROUGH Am", cc("I", "C", "1 3 5", "in C"), cc("vi = ii", "Am", "6 1 3", "the pivot"), cc("V7", "D7", "2 #4 6 1", "in G"), cc("I", "G", "5 7 2", "new home"))),
            say("A new key needs confirming: its own dominant (D7, with the F♯ that C major doesn't have) and a cadence, then " +
                "staying long enough to feel at home. If the music goes straight back to C, it hasn't modulated; it has only " +
                "tonicised G, the way a secondary dominant does. The difference is how long the new home lasts.",
                chart("ONLY A TONICISATION · BACK TO C AT ONCE", cc("I", "C", "1 3 5"), cc("V7/V", "D7", "2 #4 6 1"), cc("V", "G", "5 7 2", "brief"), cc("V7", "G7", "5 7 2 4"), cc("I", "C", "1 3 5", "home again"))),
            say("Keys next to each other on the circle of fifths share the most chords, so they modulate most easily: from C, " +
                "that's G and F and their relative minors, plus A minor itself. Keys far apart on the circle may share no " +
                "chords at all, and need a different route (see the next page).",
                table("C'S CLOSE KEYS", listOf("", "DIFFERS FROM C BY"), listOf(listOf("A minor", "nothing (relative)"), listOf("G major", "F♯"), listOf("E minor", "F♯"), listOf("F major", "B♭"), listOf("D minor", "B♭")),
                    listOf("6 1 3", "5 7 2", "3 5 7", "4 6 1", "2 4 6").map { Play(listOf(voiced(it)), 1.4, spread = true) })),
        ),
        inShort = listOf(
            "A pivot chord belongs to both keys and gets re-read in the new one.",
            "The new key is confirmed by its own dominant and a cadence, and by staying.",
            "A brief visit is a tonicisation; a stay is a modulation.",
            "Neighbours on the circle of fifths share the most pivots.",
        ),
        hear = listOf(
            sound("C · Am · D7 · G") { r -> play(r, "1 3 5", "6 1 3", "2 #4 6 1", "5 7 2", step = 1.1) },
            sound("TO THE RELATIVE MINOR") { r -> ear("hm-modulation", "vi", r) },
        ),
        check = CourseCheck("From a major key: where does it go?", listOf("V", "IV", "vi").map { t ->
            option(EarHarmony.label(Ear.drill("hm-modulation")!!, t), EarHarmony.label(Ear.drill("hm-modulation")!!, t) + ".") { r, _ -> ear("hm-modulation", t, r) }
        }),
        related = listOf(ear("hm-modulation", "where it modulates"), page("circle-neighbours", "Close and distant keys")),
    )

    private val direct = CoursePage(
        "modulation-direct", "Direct and chromatic-mediant moves", 3,
        "Not every key change sneaks in through a pivot. Some just happen: the music stops in one key and starts in another. " +
            "This page covers the two most common abrupt moves, the lift into a final chorus and the chromatic mediant.",
        steps = listOf(
            say("A direct modulation starts again in the new key, usually at the top of a section. The most familiar is the last " +
                "chorus a half or whole step higher. The chart plays a chorus ending in C, then the same chords a whole step " +
                "up in D: no preparation, just a lift.",
                chart("CHORUS IN C, LAST CHORUS IN D", cc("I", "C", "1 3 5"), cc("IV", "F", "4 6 1"), cc("V7", "G7", "5 7 2 4"),
                    cc("I", "D", "1 3 5", "new key", "D"), cc("IV", "G", "4 6 1", key = "D"), cc("V", "A", "5 7 2", key = "D"), cc("I", "D", "1 3 5", key = "D"), step = 0.9)),
            say("A chromatic mediant is a jump between two major chords (or two minor chords) whose roots are a third apart, and " +
                "which share just one note. From C: E, A♭, A and E♭. It doesn't feel like moving along the key; it feels like the " +
                "lighting changed. Tap the rows to hear C then each one.",
                table("THE CHROMATIC MEDIANTS OF C", listOf("", "NOTES", "SHARED WITH C"), listOf("E", "Ab", "Eb", "A").map { k ->
                    val notes = ScalePages.page(Scales.byId("major"), p(k)).notes.let { listOf(it[0], it[2], it[4]) }
                    listOf(shown(p(k)), notes.joinToString(" ") { shown(it) }, notes.filter { it.pitchClass in setOf(0, 4, 7) }.joinToString(" ") { shown(it) })
                }, listOf("3 #5 7", "b6 1 b3", "b3 5 b7", "6 #1 3").map { Play(listOf(voiced("1 3 5"), voiced(it)), 1.4) })),
            say("Look at the voices. C to E: E stays, C slides down to B, G slides up to G♯. C to A♭: C stays, E slides down to E♭, " +
                "G slides up to A♭. One note holds and the other two move by half steps, which is why such a distant chord " +
                "still sounds smooth.",
                staff("C → E, THEN C → A♭", listOf("C4 E4 G4", "B3 E4 G#4", "C4 E4 G4", "C4 Eb4 Ab4"), above = listOf("C", "E", "C", "A♭"), perBar = 2, step = 1.3)),
            say("Compare a close move. C to G shares G and moves the other two by step: smooth, but it stays inside the key's " +
                "world. C to E shares only E, and the G♯ is outside C major entirely. That outside note is the colour shift.",
                chart("CLOSE, THEN CHROMATIC", cc("I", "C", "1 3 5"), cc("V", "G", "5 7 2", "2 notes shared"), cc("I", "C", "1 3 5"), cc("III", "E", "3 #5 7", "1 note shared"))),
        ),
        inShort = listOf(
            "A direct modulation just restarts in the new key, often a step up for the last chorus.",
            "Chromatic mediants: same-quality chords a third apart sharing one note (C → E, A♭, A, E♭).",
            "One note holds, two move by half step: distant but smooth.",
        ),
        hear = listOf(
            sound("C → E · C → A♭") { r -> piano(prog(r, listOf("1 3 5", "3 #5 7"), 1.4) + prog(r, listOf("1 3 5", "b6 1 b3"), 1.4, at = 3.2)) },
            sound("A LAST CHORUS UP A WHOLE STEP") { r -> piano(prog(r, listOf("1 3 5", "4 6 1", "5 7 2 4"), 0.9) + prog(r + 2, listOf("1 3 5", "4 6 1", "5 7 2", "1 3 5"), 0.9, at = 2.9)) },
        ),
        check = CourseCheck("C, then: a close move (to G), or a chromatic mediant (to E)?", listOf(
            option("To G", "To G: one step round the circle, two notes shared.") { r, _ -> piano(prog(r, listOf("1 3 5", "5 7 2"), 1.4)) },
            option("To E", "To E: a chromatic mediant, one note shared.") { r, _ -> piano(prog(r, listOf("1 3 5", "3 #5 7"), 1.4)) },
        )),
        related = listOf(page("modulation-pivot", "Pivot chords"), page("neo-riemannian", "Neo-Riemannian moves")),
    )

    // ------------------------------------------------------------------------------------------------- chromatic predominants

    private val neapolitan = CoursePage(
        "neapolitan", "The Neapolitan", 4,
        "The Neapolitan is a major chord on the lowered 2nd degree, used just before the dominant, mostly in minor keys. It " +
            "replaces the ordinary predominant with something darker and more dramatic, and it does it by changing a single " +
            "note.",
        steps = listOf(
            say("In C minor, the 2nd degree is D. Lower it to D♭ and build a major chord there: D♭ F A♭. Two of its notes, F and " +
                "A♭, are already in C minor; the D♭ is the new, dark note. The rest of the C minor scale is outlined for context.",
                keys("D♭ MAJOR INSIDE C MINOR", "Db4 F4 Ab4", listOf("D♭", "F", "A♭"), faint = "C4 D4 Eb4 G4 Bb4")),
            say("It's usually played with F in the bass (first inversion), which is why it's written N⁶. Put it next to the " +
                "ordinary predominant, iv (Fm: F A♭ C). Same bass, same F and A♭; only the top note moves, C up to D♭. That's " +
                "the whole trick: one note, a half step, and a completely different colour.",
                staff("iv, THEN N⁶ · ONLY THE TOP NOTE MOVES", listOf("F4 Ab4 C5", "F4 Ab4 Db5"), above = listOf("Fm", "D♭/F"), below = listOf("iv", "N⁶"), step = 1.5)),
            say("Then it goes to V. The D♭ falls to B, the leading tone: a diminished third, an unusual melodic interval that's " +
                "part of the chord's character (often C passes in between). The F and A♭ fall to D and G.",
                staff("N⁶ → V → i", listOf("F4 Ab4 Db5", "D4 G4 B4", "Eb4 G4 C5"), above = listOf("D♭/F", "G", "Cm"), below = listOf("N⁶", "V", "i"), step = 1.4)),
            say("The whole cadence as chords with their basses: i, N⁶, V, i. Play it, then play the plain version with iv on " +
                "the HEAR buttons below, and listen to the second chord.",
                chart("i · N⁶ · V · i IN C MINOR", cc("i", "Cm", "1 b3 5"), cc("N⁶", "D♭/F", "4|b2 4 b6", "♭II, F in bass"), cc("V", "G", "5 7 2"), cc("i", "Cm", "1 b3 5"))),
            say("The name comes from the 18th-century composers of the Neapolitan school, who used it often, though it's older. " +
                "You'll meet it in Baroque and Classical minor-key music, and its D♭ colour turns up in film scores and metal " +
                "riffs as the Phrygian ♭2.",
                null),
        ),
        inShort = listOf(
            "N⁶ is a major chord on ♭2, usually with the 4th degree in the bass.",
            "From iv, only one note changes: 5 up to ♭2 (C to D♭ in C minor).",
            "It goes to V; the ♭2 falls to the leading tone.",
        ),
        hear = listOf(
            sound("i · iv · V · i") { r -> ear("hm-predominant", "iv", r) },
            sound("i · N⁶ · V · i") { r -> ear("hm-predominant", "N6", r) },
        ),
        check = CourseCheck("Before the V: iv, or the Neapolitan?", listOf(
            option("iv", "iv: the plain predominant.") { r, _ -> ear("hm-predominant", "iv", r) },
            option("N⁶", "N⁶: ♭II in first inversion, the ♭2 on top.") { r, _ -> ear("hm-predominant", "N6", r) },
        )),
        related = listOf(ear("hm-predominant", "Neapolitan and augmented sixths"), page("aug-sixths", "Augmented sixths")),
    )

    private val augSixths = CoursePage(
        "aug-sixths", "Augmented sixths", 4,
        "Augmented-sixth chords are built around one interval that pushes outward in both directions at once. They're " +
            "predominants, leading to V, and they come in three national flavours. One of them turns out to be the same sound " +
            "as a jazz tritone substitute.",
        steps = listOf(
            say("In C, put A♭ (the lowered 6th) in the bass and F♯ (the raised 4th) above it. That interval is an augmented " +
                "sixth. Each note is a half step from G, A♭ above it and F♯ below it, so they expand outward to an octave of G. " +
                "Follow the two notes on the staff.",
                staff("A♭ AND F♯ OPEN OUT TO G", listOf("Ab3 F#4", "G3 G4"), below = listOf("+6", "8ve"), clef = com.betterappmafia.apophenia.core.score.Clef.TREBLE, step = 1.5)),
            say("Add notes in between and you get the three types. Italian: just the tonic, A♭ C F♯. French: tonic and 2nd, A♭ C " +
                "D F♯, which has a whole-tone shimmer. German: tonic and ♭3, A♭ C E♭ F♯. Tap each row to hear the chord resolve " +
                "to V.",
                table("THE THREE AUGMENTED SIXTHS IN C", listOf("", "SPELLED", "BASS"), listOf("It⁺⁶" to "b6|b6 1 #4", "Fr⁺⁶" to "b6|b6 1 2 #4", "Ger⁺⁶" to "b6|b6 1 b3 #4").map { (n, t) ->
                    listOf(n, spell(t), shown(C + ch(t).tokens.first().interval))
                }, listOf("b6|b6 1 #4", "b6|b6 1 2 #4", "b6|b6 1 b3 #4").map { Play(listOf(voiced(it), voiced("5 7 2")), 1.4) })),
            say("The Italian resolves straight to V: A♭ falls to G, F♯ rises to G, C falls to B.",
                staff("It⁺⁶ → V", listOf("Ab3 C4 F#4", "G3 B3 G4"), below = listOf("It⁺⁶", "V"), step = 1.5)),
            say("The German usually goes through a tonic chord with G in the bass (i⁶₄, Cm/G) before V, because going straight to " +
                "V would give parallel fifths (A♭–E♭ to G–D), which the classical style avoids. That middle chord is a cadential " +
                "six-four: it sounds like the tonic but acts as a decoration of V.",
                chart("i · Ger⁺⁶ · i⁶₄ · V · i", cc("i", "Cm", "1 b3 5"), cc("Ger⁺⁶", "", "b6|b6 1 b3 #4", "A♭ in bass"), cc("i⁶₄", "Cm/G", "5|1 b3 5", "G in bass"), cc("V", "G", "5 7 2"), cc("i", "Cm", "1 b3 5"))),
            say("Play the German alone and you'll hear an A♭7 chord: A♭ C E♭ G♭. The notes are the same keys on the piano; only " +
                "the spelling differs (F♯, because it rises to G). And A♭7 is the tritone substitute for D7, the V7 of V. Two " +
                "traditions, one sound: classical calls it a German sixth, jazz calls it ♭VI7 or a tritone sub.",
                keys("Ger⁺⁶ · SOUNDS LIKE A♭7", "Ab3 C4 Eb4 F#4", listOf("A♭", "C", "E♭", "F♯"))),
        ),
        inShort = listOf(
            "♭6 in the bass, ♯4 above: an augmented sixth that opens out to the octave on 5.",
            "Italian (♭6 1 ♯4), French (+2), German (+♭3).",
            "The German sounds like a dominant seventh on ♭6, the tritone sub of V7/V.",
        ),
        hear = listOf("It+6", "Fr+6", "Ger+6").map { a -> sound("i · ${EarHarmony.label(Ear.drill("hm-predominant")!!, a)} · V · i") { r -> ear("hm-predominant", a, r) } },
        check = CourseCheck("Italian, French or German?", listOf("It+6", "Fr+6", "Ger+6").map { a ->
            val l = EarHarmony.label(Ear.drill("hm-predominant")!!, a)
            option(l, "$l: ${mapOf("It+6" to "three notes, the plainest", "Fr+6" to "the 2nd adds a whole-tone colour", "Ger+6" to "the ♭3 makes it sound like a dominant seventh").getValue(a)}.") { r, _ -> ear("hm-predominant", a, r) }
        }),
        related = listOf(ear("hm-predominant", "Neapolitan and augmented sixths"), page("jazz-subs", "Tritone substitution")),
    )

    // ------------------------------------------------------------------------------------------------- jazz

    private val twoFiveOne = CoursePage(
        "jazz-251", "ii–V–I and guide tones", 4,
        "If tonic–predominant–dominant–tonic is the sentence of tonal music, ii–V–I is the sentence of jazz. Standards are " +
            "built largely from ii–Vs in the home key and in passing keys, and the way to play through them smoothly is to " +
            "follow just two notes of each chord.",
        steps = listOf(
            say("In C, the ii–V–I is Dm7–G7–Cmaj7: predominant, dominant, tonic, each a seventh chord. Tap each chord, then play " +
                "the three in order.",
                chart("ii–V–I IN C", cc("ii7", "Dm7", "2 4 6 1", "predominant"), cc("V7", "G7", "5 7 2 4", "dominant"), cc("Imaj7", "Cmaj7", "1 3 5 7", "tonic"), step = 1.4)),
            say("The 3rd and 7th of a chord decide what it is: major or minor, dominant or major seventh. They're called the guide " +
                "tones. Through a ii–V–I they hardly move. Read the two rows: the upper voice holds F then falls to E; the lower " +
                "voice falls from C to B and holds.",
                CourseSee.Grid("THE GUIDE TONES", listOf("Dm7", "G7", "Cmaj7"), listOf(
                    row("upper", listOf("F 3rd", "F 7th", "E 3rd"), setOf(0, 1, 2), Play(mids("F4 F4 E4").map { listOf(it) }, 0.9)),
                    row("lower", listOf("C 7th", "B 3rd", "B 7th"), setOf(0, 1, 2), Play(mids("C4 B3 B3").map { listOf(it) }, 0.9)),
                ))),
            say("Play just the guide tones over each root and you have the shell voicings of jazz piano and guitar. Two notes " +
                "and a bass are enough to say the whole progression. The staff shows the shells; ▶ plays them with their roots.",
                staff("SHELLS: ROOT, THEN 3RD AND 7TH", listOf("C4 F4", "B3 F4", "B3 E4"), above = listOf("Dm7", "G7", "Cmaj7"), step = 1.4)
                    .copy(play = Play(listOf(mids("D3 C4 F4"), mids("G2 B3 F4"), mids("C3 B3 E4")), 1.4))),
            say("In minor, the ii is half-diminished and the I is minor: Dø7–G7–Cm. The G7 often picks up a ♭9 (A♭) from the minor " +
                "key on its way.",
                chart("MINOR ii–V–i IN C", cc("iiø7", "Dø7", "2 4 b6 1", "predominant"), cc("V7", "G7", "5 7 2 4", "dominant"), cc("i", "Cm", "1 b3 5", "tonic"), step = 1.4)),
            say("Standards chain ii–Vs. iii7–vi7–ii7–V7–I is two ii–Vs back to back (Em7–A7 aims at D, Dm7–G7 at C), and a ii–V " +
                "can point at a new key for a bar: Gm7–C7 is the ii–V of F. Spotting the pairs is most of reading jazz changes.",
                chart("ii–Vs IN PASSING", cc("Imaj7", "Cmaj7", "1 3 5 7"), cc("ii/IV", "Gm7", "5 b7 2 4", "ii of F"), cc("V7/IV", "C7", "1 3 5 b7", "V of F"), cc("IVmaj7", "Fmaj7", "4 6 1 3", "lands"),
                    cc("ii7", "Dm7", "2 4 6 1"), cc("V7", "G7", "5 7 2 4"), cc("Imaj7", "Cmaj7", "1 3 5 7"), step = 1.0)),
        ),
        inShort = listOf(
            "ii7–V7–Imaj7 (major) and iiø7–V7–i (minor) are the core of jazz harmony.",
            "Guide tones (3rds and 7ths) move by half step or hold through a ii–V–I.",
            "Shells (root + 3rd + 7th) are enough to play the changes.",
            "Read lead sheets by finding the ii–V pairs and where each one points.",
        ),
        hear = listOf(
            sound("Dm7 · G7 · Cmaj7") { r -> play(r, "2 4 6 1", "5 7 2 4", "1 3 5 7", step = 1.4) },
            sound("GUIDE TONES ONLY") { r -> piano(listOf(listOf(-22, 5, 12), listOf(-17, 5, 11), listOf(-24, 4, 11)).flatMapIndexed { i, c -> RefSound.block(c.map { r + it }, at = i * 1.4, length = 1.3) }) },
            sound("MINOR · Dø7 · G7 · Cm") { r -> play(r, "2 4 b6 1", "5 7 2 4", "1 b3 5", step = 1.4) },
        ),
        check = CourseCheck("A major ii–V–I, or a minor one?", listOf(
            option("Major", "Major: Dm7 G7 Cmaj7.") { r, _ -> play(r, "2 4 6 1", "5 7 2 4", "1 3 5 7", step = 1.2) },
            option("Minor", "Minor: Dø7 G7 Cm.") { r, _ -> play(r, "2 4 b6 1", "5 7 2 4", "1 b3 5", step = 1.2) },
        )),
        related = listOf(ref(RefKind.CHORD, "m7", "Dm7", "D"), ref(RefKind.CHORD, "m7b5", "Dø7", "D"), page("secondary-chains", "Chains of dominants")),
    )

    private val subs = CoursePage(
        "jazz-subs", "Tritone substitution and chord-scales", 4,
        "Once you know what makes a dominant work, you can swap it for another chord that does the same job. This page " +
            "covers the two classic swaps, the tritone substitute and the backdoor, and then the scales players use over each " +
            "chord.",
        steps = listOf(
            say("G7's power is the tritone between B and F. Now look at D♭7: D♭ F A♭ C♭. C♭ is the same key as B. So D♭7 holds " +
                "the same tritone, F and B/C♭, just the other way up. Same engine, different root.",
                keys("G7 · THE TRITONE B–F", "G3 B3 D4 F4", listOf("G", "B", "D", "F"))),
            say("D♭7, with the same two notes lit.",
                keys("D♭7 · THE SAME TRITONE, F–C♭", "Db4 F4 Ab4 B4", listOf("D♭", "F", "A♭", "C♭"))),
            say("Swap it into a ii–V–I and the bass slides down by half steps, D, D♭, C, instead of jumping. That's the tritone " +
                "substitution, ♭II7 for V7.",
                chart("Dm7 · D♭7 · Cmaj7", cc("ii7", "Dm7", "2 4 6 1"), cc("♭II7", "D♭7", "b2 4 b6 b1", "tritone sub"), cc("Imaj7", "Cmaj7", "1 3 5 7"), step = 1.4)),
            say("The backdoor dominant comes from the parallel minor: B♭7, ♭VII7, resolving up a whole step to C. Its A♭ falls a " +
                "half step to G and its F to E, while B♭ rises to C. With no leading tone the pull is softer and bluesier than " +
                "G7's. Often it's preceded by Fm7, making a " +
                "backdoor ii–V.",
                chart("THE BACKDOOR · Fm7 B♭7 Cmaj7", cc("iv7", "Fm7", "4 b6 1 b3", "borrowed"), cc("♭VII7", "B♭7", "b7 2 4 b6", "backdoor"), cc("Imaj7", "Cmaj7", "1 3 5 7"), step = 1.4)),
            say("Chord-scale theory pairs each chord with a scale whose notes fit it, as a map for improvising and voicing. In a " +
                "plain ii–V–I in C, every chord takes the C major scale from its own root: D Dorian, G Mixolydian, C Ionian. " +
                "Altered dominants take other scales. Tap a row to hear the scale.",
                table("CHORD-SCALES IN C", listOf("", "SCALE", "NOTES"), listOf(
                    Triple("Dm7", "dorian", "D"), Triple("G7", "mixolydian", "G"), Triple("Cmaj7", "major", "C"), Triple("G7alt", "altered", "G"), Triple("G7♭9", "half-whole", "G"),
                ).map { (c, s, r) -> listOf(c, accidentals(Scales.byId(s).names.first()), CourseScales.notesOn(s, r)) },
                    listOf("dorian" to "D", "mixolydian" to "G", "major" to "C", "altered" to "G", "half-whole" to "G").map { (s, r) ->
                        val ms = RefSound.midis(p(r).pitchClass, Scales.byId(s).semitones, 55)
                        Play((ms + (ms.first() + 12)).map { listOf(it) }, 0.32)
                    })),
            say("One note per scale needs care: the avoid note. Over Cmaj7, F sits a half step above E, the chord's 3rd, and held " +
                "against it, it clashes. That's why players often use Lydian (F♯ instead of F) over a major seventh. Listen to " +
                "the chord with F, then with F♯.",
                keys("Cmaj7 WITH F, THEN WITH F♯", "C4 E4 F4 G4 B4", listOf("C", "E", "F", "G", "B"), play = Play(listOf(mids("C3 E4 G4 B4 F5"), mids("C3 E4 G4 B4 F#5")), 1.8))),
        ),
        inShort = listOf(
            "A tritone substitute (♭II7 for V7) shares the dominant's 3rd and 7th.",
            "The backdoor dominant (♭VII7, often after iv7) resolves up a whole step.",
            "Chord-scales: a map of notes that fit each chord, not a rule.",
            "Watch the avoid note: the natural 4 over a major 3rd.",
        ),
        hear = listOf("V7", "bII7", "bVII7").map { s -> sound(EarHarmony.label(Ear.drill("hm-subs")!!, s).uppercase()) { r -> ear("hm-subs", s, r) } },
        check = CourseCheck("ii7, a dominant, Imaj7. Which dominant?", listOf("V7", "bII7", "bVII7").map { s ->
            val l = EarHarmony.label(Ear.drill("hm-subs")!!, s)
            option(l, "$l.") { r, _ -> ear("hm-subs", s, r) }
        }),
        related = listOf(ref(RefKind.CHORD, "7", "D♭7", "Db"), ref(RefKind.SCALE, "altered", "G altered", "G"), ear("hm-subs", "tritone and backdoor substitutes"), page("iv-tritone-chords", "The tritone in chords")),
    )

    // ------------------------------------------------------------------------------------------------- beyond

    private val coltrane = CoursePage(
        "coltrane", "Coltrane changes", 5,
        "In the late 1950s John Coltrane built progressions on three keys a major third apart, which divides the octave into " +
            "three equal parts. The result moves through distant keys so fast that it became the standard test of a jazz " +
            "improviser.",
        steps = listOf(
            say("Start with three tonics a major third apart: C, E and A♭. Each " +
                "step is four half steps, and three steps bring you back to C. No key is more central than the others.",
                keys("THREE TONICS · C, A♭, E", "C4 E4 Ab4", listOf("C", "E", "A♭"))),
            say("Coltrane reaches each tonic through its own dominant. Applied to the bar before Cmaj7 in a ii–V–I, the tour goes " +
                "Dm7, E♭7 to A♭maj7, B7 to Emaj7, G7 to Cmaj7. Read the tags: every dominant is immediately followed by its " +
                "tonic.",
                chart("A ii–V–I IN C, COLTRANE'S WAY", cc("ii7", "Dm7", "2 4 6 1", "start"), cc("V7/♭VI", "E♭7", "b3 5 b7 b2", "V of A♭"), cc("♭VImaj7", "A♭maj7", "b6 1 b3 5", "tonic A♭"),
                    cc("V7/III", "B7", "7 #2 #4 6", "V of E"), cc("IIImaj7", "Emaj7", "3 #5 7 #2", "tonic E"), cc("V7", "G7", "5 7 2 4", "V of C"), cc("Imaj7", "Cmaj7", "1 3 5 7", "tonic C"), step = 0.9)),
            say("Play the plain ii–V–I on the HEAR buttons, then this chart. Same start, same destination, but the middle visits " +
                "two distant keys. \"Giant Steps\" uses the same idea with tonics B, G and E♭, and \"Countdown\" applies it to " +
                "a standard's changes.",
                null),
        ),
        inShort = listOf(
            "Three tonics a major third apart split the octave evenly.",
            "Each tonic is reached through its own dominant.",
            "It fits inside a ii–V–I, replacing the plain V with a tour of three keys.",
        ),
        hear = listOf(
            sound("Dm7 · G7 · Cmaj7") { r -> play(r, "2 4 6 1", "5 7 2 4", "1 3 5 7", step = 1.2) },
            sound("THE SAME, COLTRANE'S WAY") { r -> play(r, "2 4 6 1", "b3 5 b7 b2", "b6 1 b3 5", "7 #2 #4 6", "3 #5 7 #2", "5 7 2 4", "1 3 5 7", step = 0.9) },
        ),
        related = listOf(page("jazz-251", "ii–V–I"), page("triad-four", "The augmented triad")),
    )

    /** Negative harmony in C: pitch class n becomes 7 − n (the mirror between E♭ and E, the axis of C and G). */
    internal fun negative(pc: Int) = Math.floorMod(7 - pc, 12)

    private fun pcName(pc: Int) = shown(if (pc == 3) p("Eb") else if (pc == 8) p("Ab") else if (pc == 10) p("Bb") else PitchClass(pc).defaultSpelling())

    private val negativeHarmony = CoursePage(
        "negative-harmony", "Negative harmony", 5,
        "Negative harmony reflects a key in a mirror. Every note gets a partner on the other side of an axis, major turns " +
            "into minor, and familiar chords turn into unexpected ones that still lead to the same home. It's a way to find " +
            "new chords, not a description of how tonal music works.",
        steps = listOf(
            say("The mirror in C sits halfway between E♭ and E (and, on the other side of the octave, between A and B♭). Each " +
                "note reflects to the note the same distance on the other side: C and G swap, D and F, E and E♭, A and B♭, B and " +
                "A♭, D♭ and F♯. In numbers from C, pitch class n becomes 7 − n. Tap a row to hear a note and its mirror.",
                table("THE MIRROR PAIRS IN C", listOf("", "BECOMES"), listOf(0, 2, 4, 9, 11, 1).map { listOf(pcName(it), pcName(negative(it))) },
                    listOf(0, 2, 4, 9, 11, 1).map { Play(listOf(listOf(60 + it), listOf(60 + negative(it))), 0.8) })),
            say("Reflect the C major chord, C E G, and you get G E♭ C: C minor. The root and fifth swap places, and the major " +
                "third becomes a minor third. Major and minor are mirror images.",
                chart("C → ITS MIRROR", cc("I", "C", "1 3 5"), cc("i", "Cm", "1 b3 5", "the mirror"))),
            say("Now reflect the dominant. G7 (G B D F) becomes C A♭ F D: an Fm6 chord, which is the same set of notes as Dø7. It " +
                "still resolves to C: its A♭ falls a half step to G and its F a half step to E, the same kind of pull as G7's, from " +
                "the other side. This is the " +
                "\"negative\" dominant: a minor iv with an added 6th, standing in for V.",
                chart("G7 → C, THEN Fm6 → C", cc("V7", "G7", "5 7 2 4", "dominant"), cc("I", "C", "1 3 5"), cc("iv6", "Fm6", "4 b6 1 2", "its mirror"), cc("I", "C", "1 3 5"))),
            say("Every chord has a mirror. The table shows some in C; tap a row to hear the chord, then its reflection.",
                table("CHORDS AND THEIR MIRRORS IN C", listOf("", "NOTES", "BECOMES", "NAMED"), listOf(
                    Triple("C", listOf(0, 4, 7), "Cm"), Triple("G7", listOf(7, 11, 2, 5), "Fm6 · Dø7"), Triple("F", listOf(5, 9, 0), "Gm"), Triple("Am", listOf(9, 0, 4), "E♭"),
                ).map { (n, pcs, named) -> listOf(n, pcs.joinToString(" ") { pcName(it) }, pcs.map(::negative).joinToString(" ") { pcName(it) }, named) },
                    listOf(listOf(0, 4, 7), listOf(7, 11, 2, 5), listOf(5, 9, 0), listOf(9, 0, 4)).map { pcs -> Play(listOf(pcs.map { 60 + it }, pcs.map { 60 + negative(it) }), 1.4) })),
            say("Where the idea comes from: harmonic polarity goes back to Hugo Riemann, and Ernst Levy worked it out in A Theory " +
                "of Harmony. Levy never used the phrase \"negative harmony\"; Jacob Collier popularised it. Treat it as a tool " +
                "for finding chords that share a progression's pull, not as a law.",
                null),
        ),
        inShort = listOf(
            "In C, the mirror is between E♭ and E: n → 7 − n.",
            "Major chords reflect into minor ones.",
            "G7 reflects to Fm6 (= Dø7), a negative dominant that still resolves to C.",
        ),
        hear = listOf(
            sound("G7 → C") { r -> play(r, "5 7 2 4", "1 3 5") },
            sound("ITS MIRROR · Fm6 → C") { r -> play(r, "4 b6 1 2", "1 3 5") },
            sound("C, THEN ITS MIRROR Cm") { r -> play(r, "1 3 5", "1 b3 5", step = 1.4) },
        ),
        check = CourseCheck("Into C: from G7, or from its mirror, Fm6?", listOf(
            option("G7", "G7: the dominant.") { r, _ -> play(r, "5 7 2 4", "1 3 5") },
            option("Fm6", "Fm6: the negative dominant, a minor iv with an added 6th.") { r, _ -> play(r, "4 b6 1 2", "1 3 5") },
        )),
        related = listOf(ref(RefKind.CHORD, "m6", "Fm6", "F"), ref(RefKind.CHORD, "m7b5", "Dø7", "D"), page("mixture", "Borrowed chords")),
    )

    /** A major or minor triad by root pitch class. */
    internal data class Triad(val root: Int, val major: Boolean) {
        val pcs: Set<Int> get() = listOf(0, if (major) 4 else 3, 7).map { (root + it) % 12 }.toSet()
    }
    /** Parallel: same root, the third moves. Relative: C ↔ Am. Leading-tone exchange: C ↔ Em. */
    internal fun P(t: Triad) = Triad(t.root, !t.major)
    internal fun R(t: Triad) = if (t.major) Triad((t.root + 9) % 12, false) else Triad((t.root + 3) % 12, true)
    internal fun L(t: Triad) = if (t.major) Triad((t.root + 4) % 12, false) else Triad((t.root + 8) % 12, true)

    private fun triadAudio(r: Int, list: List<Triad>) = piano(list.flatMapIndexed { i, t ->
        RefSound.block(t.pcs.map { pc -> (r - 5) + Math.floorMod(pc - (r - 5), 12) }.sorted() + (r - 24 + Math.floorMod(t.root - r, 12)), at = i * 1.3, length = 1.2)
    })
    private fun triadName(t: Triad) = shown(if (t.root == 8) p("Ab") else if (t.root == 3) p("Eb") else PitchClass(t.root).defaultSpelling()) + if (t.major) "" else "m"

    private val neoRiemannian = CoursePage(
        "neo-riemannian", "Neo-Riemannian moves", 5,
        "Neo-Riemannian theory describes three tiny moves between major and minor triads. Each keeps two notes and nudges " +
            "the third by a step, and chaining them lets music drift between chords that no key would put together. It was " +
            "built to explain late-Romantic harmony, and it explains a lot of film music too.",
        steps = listOf(
            say("P, parallel: keep the root and fifth, move the third. C major becomes C minor (E falls to E♭). Watch the middle " +
                "note on the staff.",
                staff("P · C → Cm", listOf("C4 E4 G4", "C4 Eb4 G4"), above = listOf("C", "Cm"), step = 1.4)),
            say("R, relative: from a major chord, move the fifth up a whole step. C major becomes A minor (G rises to A). C and E " +
                "stay.",
                staff("R · C → Am", listOf("C4 E4 G4", "C4 E4 A4"), above = listOf("C", "Am"), step = 1.4)),
            say("L, leading-tone exchange: from a major chord, move the root down a half step. C major becomes E minor (C falls to " +
                "B). E and G stay.",
                staff("L · C → Em", listOf("C4 E4 G4", "B3 E4 G4"), above = listOf("C", "Em"), step = 1.4)),
            say("Each move undoes itself (P of C minor is C major again), and combining them reaches further while still moving " +
                "only one note at a time. Alternate P and L from C and you get C, Cm, A♭, A♭m, E, Em and back to C: three major " +
                "chords a major third apart, all connected by single half or whole steps.",
                chart("A PL CHAIN FROM C", *generateSequence(Triad(0, true) to 0) { (t, i) -> (if (i % 2 == 0) P(t) else L(t)) to i + 1 }.take(7).map { it.first }.toList().mapIndexed { i, t ->
                    ChartChord(if (i == 0) "start" else if (i % 2 == 1) "P" else "L", triadName(t), t.pcs.sorted().joinToString(" ") { pcName(it) }, t.pcs.map { pc -> 55 + Math.floorMod(pc - 55, 12) }.sorted() + (36 + t.root))
                }.toTypedArray(), step = 1.1)),
            say("The table lists the single moves and two-step combinations from C major. Every result keeps at least one note of " +
                "C; the single moves keep two.",
                table("FROM C MAJOR", listOf("MOVE", "TO", "KEPT"), listOf("P" to P(Triad(0, true)), "R" to R(Triad(0, true)), "L" to L(Triad(0, true)), "PL" to L(P(Triad(0, true))), "LP" to P(L(Triad(0, true)))).map { (n, t) ->
                    listOf(n, triadName(t), "${Triad(0, true).pcs.intersect(t.pcs).size} notes")
                }, listOf(P(Triad(0, true)), R(Triad(0, true)), L(Triad(0, true)), L(P(Triad(0, true))), P(L(Triad(0, true)))).map { t ->
                    Play(listOf(listOf(36, 60, 64, 67), listOf(36 + t.root) + t.pcs.map { pc -> 55 + Math.floorMod(pc - 55, 12) }.sorted()), 1.3)
                })),
        ),
        inShort = listOf(
            "P moves the third (C ↔ Cm), R moves the fifth up a whole step (C ↔ Am), L moves the root down a half step (C ↔ Em).",
            "Each keeps two notes and undoes itself.",
            "Chains like PL travel between distant chords one small step at a time.",
        ),
        hear = listOf(
            sound("C · P · R · L") { r -> triadAudio(r, listOf(Triad(0, true), P(Triad(0, true)), Triad(0, true), R(Triad(0, true)), Triad(0, true), L(Triad(0, true))).map { it.copy(root = (it.root + r) % 12) }) },
            sound("A PL CHAIN · C A♭ E C") { r -> triadAudio(r, generateSequence(Triad(0, true)) { L(P(it)) }.take(4).toList().map { it.copy(root = (it.root + r) % 12) }) },
        ),
        check = CourseCheck("From C major: P, R or L?", listOf("P" to ::P, "R" to ::R, "L" to ::L).map { (n, f) ->
            option(n, mapOf("P" to "P: C to C minor.", "R" to "R: C to A minor.", "L" to "L: C to E minor.").getValue(n)) { r, _ -> triadAudio(r, listOf(Triad(r % 12, true), f(Triad(r % 12, true)))) }
        }),
        related = listOf(page("modulation-direct", "Chromatic mediants"), ref(RefKind.CHORD, "maj", "C major")),
    )

    /** Normal form (most compact rotation, ties broken by the smallest intervals from the bottom, after Rahn). */
    internal fun normal(pcs: Set<Int>): List<Int> {
        val s = pcs.map { Math.floorMod(it, 12) }.sorted()
        val rotations = s.indices.map { i -> (0 until s.size).map { k -> s[(i + k) % s.size] + if (i + k >= s.size) 12 else 0 } }
        return rotations.minWithOrNull(Comparator { a, b ->
            for (k in a.size - 1 downTo 1) { val d = (a[k] - a[0]) - (b[k] - b[0]); if (d != 0) return@Comparator d }
            0
        })!!.map { it % 12 }
    }
    /** Prime form: the more compact of the normal forms of the set and its inversion, transposed to start on 0. */
    internal fun prime(pcs: Set<Int>): List<Int> {
        fun zeroed(l: List<Int>) = l.map { Math.floorMod(it - l[0], 12) }
        val a = zeroed(normal(pcs)); val b = zeroed(normal(pcs.map { Math.floorMod(-it, 12) }.toSet()))
        for (k in a.indices.reversed()) if (a[k] != b[k]) return if (a[k] < b[k]) a else b
        return a
    }
    /** How many of each interval class (1–6) the set holds. */
    internal fun vector(pcs: Set<Int>): List<Int> {
        val l = pcs.toList(); val v = IntArray(6)
        for (i in l.indices) for (j in i + 1 until l.size) { val d = Math.floorMod(l[j] - l[i], 12); v[minOf(d, 12 - d) - 1]++ }
        return v.toList()
    }

    private fun t(x: Int) = when (x) { 10 -> "T"; 11 -> "E"; else -> "$x" }
    private val PC_COLUMNS = (0..11).map(::t)
    private fun pcRow(label: String, pcs: Set<Int>) = row(label, PC_COLUMNS, pcs, Play(listOf(pcs.sorted().map { 60 + it }), 1.4, spread = true))

    private val SETS = listOf(
        "Major triad" to setOf(0, 4, 7), "Minor triad" to setOf(0, 3, 7), "Diminished" to setOf(0, 3, 6), "Augmented" to setOf(0, 4, 8),
        "Dominant 7th" to setOf(0, 4, 7, 10), "Half-diminished" to setOf(0, 3, 6, 10), "Major 7th" to setOf(0, 4, 7, 11), "Minor 7th" to setOf(0, 3, 7, 10),
        "Diminished 7th" to setOf(0, 3, 6, 9), "Whole-tone" to setOf(0, 2, 4, 6, 8, 10),
    )

    private val sets = CoursePage(
        "set-theory", "Pitch-class sets", 5,
        "Post-tonal theory needs a way to compare chords and motifs that don't belong to any key. It does it by turning notes " +
            "into numbers and asking which collections have the same shape. The tools are simple once you see them on the " +
            "clock face of the twelve pitch classes.",
        steps = listOf(
            say("Number the twelve pitch classes from C = 0 to B = 11, ignoring spelling and octave: C♯ and D♭ are both 1. Ten and " +
                "eleven are written T and E so every number is one character. A C major triad is {0, 4, 7}.",
                CourseSee.Grid("THE TWELVE PITCH CLASSES", PC_COLUMNS, listOf(
                    row("names", cols("C Db D Eb E F Gb G Ab A Bb B"), (0..11).toSet(), Play((60..71).map { listOf(it) }, 0.3)),
                    pcRow("C major", setOf(0, 4, 7)),
                ))),
            say("Transpose the chord and every number shifts by the same amount: D major is {2, 6, 9}, add 2 to each. On the grid " +
                "it's the same pattern of gaps slid to the right. Sets related this way are the same kind of set.",
                CourseSee.Grid("TRANSPOSITION SLIDES THE SHAPE", PC_COLUMNS, listOf(pcRow("C major", setOf(0, 4, 7)), pcRow("D major", setOf(2, 6, 9)), pcRow("E major", setOf(4, 8, 11))))),
            say("Inversion flips the gaps upside down. A major triad's gaps from the bottom are 4 then 3; flip them and you get 3 " +
                "then 4: a minor triad. So in set theory major and minor triads are one kind of set, and both share the prime " +
                "form (037), the most compact arrangement starting on 0. Allen Forte's catalogue numbers it 3-11.",
                CourseSee.Grid("INVERSION FLIPS THE GAPS", PC_COLUMNS, listOf(pcRow("C major", setOf(0, 4, 7)), pcRow("C minor", setOf(0, 3, 7)), pcRow("(037)", setOf(0, 3, 7))))),
            say("The interval vector counts how many of each interval class a set contains, from 1 (half step) to 6 (tritone). " +
                "A major triad has one minor third (E–G), one major third (C–E) and one perfect fourth or fifth (C–G): 001110. " +
                "Sets with the same vector share a kind of sound even when they aren't transpositions or inversions of each " +
                "other. Tap a row to hear the set on C.",
                table("PRIME FORMS AND VECTORS", listOf("", "PCS ON C", "PRIME", "VECTOR"), SETS.map { (n, s) ->
                    listOf(n, s.sorted().joinToString(" ") { t(it) }, "(" + prime(s).joinToString("") { t(it) } + ")", vector(s).joinToString(""))
                }, SETS.map { (_, s) -> Play(listOf(s.sorted().map { 60 + it }), 1.4, spread = true) })),
            say("Two examples worth hearing: the dominant seventh and the half-diminished seventh are inversions of each other, so " +
                "both are (0258). The tools work on any music, which is why they're used for Schoenberg and Bartók and also for " +
                "comparing jazz voicings.",
                null),
        ),
        inShort = listOf(
            "Pitch classes are numbered 0–11 from C; spelling and octave are set aside.",
            "Transposed or inverted sets are the same kind and share a prime form: major and minor triads are both (037).",
            "The interval vector counts interval classes 1–6: a triad is 001110.",
        ),
        hear = listOf(
            sound("MAJOR · MINOR · BOTH (037)") { r -> piano(RefSound.block(listOf(r, r + 4, r + 7), length = 1.6) + RefSound.block(listOf(r, r + 3, r + 7), at = 2.0, length = 1.6)) },
            sound("DOMINANT 7 · HALF-DIMINISHED · BOTH (0258)") { r -> piano(RefSound.block(listOf(r, r + 4, r + 7, r + 10), length = 1.6) + RefSound.block(listOf(r, r + 3, r + 6, r + 10), at = 2.0, length = 1.6)) },
        ),
        related = listOf(page("messiaen", "Messiaen's modes"), page("triad-four", "The four triads")),
    )
    val topics = listOf(
        CourseTopic("numerals", "Chords of a key, Roman numerals", "I to vii°, in major and minor", 2, listOf(majorKey, minorKey)),
        CourseTopic("function", "Function", "tonic, predominant, dominant", 2, listOf(function)),
        CourseTopic("cadences", "Cadences", "authentic, plagal, half, deceptive", 2, listOf(cadences)),
        CourseTopic("secondary", "Secondary dominants", "V/V and the chords that point", 3, listOf(secondary, chains)),
        CourseTopic("mixture", "Borrowed chords", "modal mixture: ♭VI, ♭VII, iv in major", 3, listOf(mixture)),
        CourseTopic("modulation", "Modulation", "changing key: pivot, direct, by mediant", 3, listOf(pivot, direct)),
        CourseTopic("chromatic", "Neapolitan and augmented sixths", "N⁶, It⁺⁶, Fr⁺⁶, Ger⁺⁶", 4, listOf(neapolitan, augSixths)),
        CourseTopic("jazz", "Jazz harmony", "ii–V–I, guide tones, tritone subs, chord-scales", 4, listOf(twoFiveOne, subs)),
        CourseTopic("beyond", "Beyond the key", "Coltrane changes, negative harmony, neo-Riemannian, set theory", 5, listOf(coltrane, negativeHarmony, neoRiemannian, sets)),
    )
}
