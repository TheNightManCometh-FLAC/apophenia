package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.catalog.Scales
import com.betterappmafia.apophenia.core.learn.Acoustics.centsText
import com.betterappmafia.apophenia.core.learn.Acoustics.fmt
import com.betterappmafia.apophenia.core.learn.Acoustics.hz
import com.betterappmafia.apophenia.core.learn.Acoustics.hzText
import com.betterappmafia.apophenia.core.sound.PlayNote
import kotlin.math.abs
import kotlin.math.pow

/*
 * Course · Sound & pitch: what pitch is, note names and octave numbers, half and whole steps, timbre and the harmonic
 * series, beating, cents and tuning systems. Text written for Apophenia; every number quoted comes from Acoustics.
 */
internal object CourseSoundPitch : CourseKit() {

    private val SINE = listOf(1.0)
    private val BRIGHT = (1..10).map { 1.0 / it }
    private val HOLLOW = (1..10).map { if (it % 2 == 1) 1.0 / it else 0.0 }
    /** Enough harmonics for a major third's 5th and 4th harmonics to meet and beat. */
    private val SIX = listOf(1.0, 0.5, 0.35, 0.25, 0.2, 0.15)

    /** Note names on C for the tables (the harmonic series is spelled from its fundamental: the 7th is B♭). */
    private val ON_C = listOf("C", "D♭", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B")
    internal fun nameOf(midi: Int) = ON_C[Math.floorMod(midi, 12)] + (Math.floorDiv(midi, 12) - 1)

    private fun hzRow(m: Int, what: String) = listOf(nameOf(m), "$m", hzText(hz(m)), what)
    private fun one(m: Int) = Play(listOf(listOf(m)), 1.5)

    /** Frets 0–12 across, each row lighting the frets in [lit] (for harmonics and the 12th fret; drawn, not played). */
    private val FRETS = (0..12).map { "$it" }
    private fun frets(caption: String, rows: List<Pair<String, Set<Int>>>, play: List<Play?> = rows.map { null }) =
        CourseSee.Grid(caption, FRETS, rows.mapIndexed { i, (label, lit) -> row(label, FRETS, lit, play[i]) })
    private fun tones(label: String, f: () -> List<Tone>) = sound(label) { EarAudio(emptyList(), f()) }

    // ------------------------------------------------------------------------------------------------- pitch

    private val frequency = CoursePage(
        "pitch-frequency", "Pitch is frequency", 1,
        "Pitch is how high or low a note sounds. Physically it's frequency: how many times a second the air pressure " +
            "swings back and forth.",
        steps = listOf(
            say("A vibrating string pushes the air back and forth, and that pressure wave reaches your ear. The number of swings " +
                "per second is the frequency, in hertz (Hz). Faster swings sound higher. The A above middle C, the note bands and " +
                "orchestras tune to, swings ${hzText(hz(69))} times a second.",
                keys("A4 · ${hzText(hz(69))} HZ", "A4", listOf("A4"), play = Play(listOf(listOf(69)), 2.0))),
            say("Every key has its own frequency. Middle C is ${hzText(hz(60))} Hz; a piano runs from ${hzText(hz(21))} Hz at the " +
                "bottom to ${hzText(hz(108))} Hz at the top. Tap the rows: each one is a note you know from an instrument.",
                table("FREQUENCIES · A4 = 440 HZ", listOf("NOTE", "MIDI", "HZ", ""), listOf(
                    hzRow(21, "lowest piano key"), hzRow(28, "bass, low E"), hzRow(40, "guitar, low E"), hzRow(60, "middle C"),
                    hzRow(64, "guitar, high E"), hzRow(69, "the tuning A"), hzRow(108, "highest piano key"),
                ), listOf(21, 28, 40, 60, 64, 69, 108).map(::one))),
            say("Young ears hear roughly 20 Hz to 20,000 Hz, and the top end comes down with age. The notes themselves sit low in " +
                "that range: the bass's low E is ${hzText(hz(28))} Hz, the guitar's ${hzText(hz(40))}, its top string " +
                "${hzText(hz(64))}. The rest of the range is filled by the overtones that give each instrument its colour.",
                keysLine("E1 · E2 · E4: BASS, GUITAR, TOP STRING", "E1 E2 E4", listOf("E1 bass", "E2 guitar", "E4 top string"), step = 0.9)),
        ),
        inShort = listOf(
            "Pitch = frequency, in Hz: swings per second.",
            "A4 = 440 Hz is the tuning note; middle C is ${hzText(hz(60))} Hz.",
            "Hearing runs about 20 Hz to 20 kHz.",
        ),
        hear = listOf(
            sound("A 440") { piano(listOf(note(0.0, 2.0, 69))) },
            sound("A1 TO A7") { piano(seq((1..7).map { 21 + 12 * it }, step = 0.7, len = 0.65)) },
            sound("E1 · E2 · E4") { piano(seq(listOf(28, 40, 64), step = 0.9, len = 0.85)) },
        ),
        tryIt = CourseTry("EVERY A ON YOUR NECK · ONE LETTER, A DIFFERENT FREQUENCY EACH OCTAVE", RefKind.INTERVAL, "P8", "A"),
        check = CourseCheck("Is the second note higher or lower?", listOf(
            option("Higher", "Higher: the second note vibrates faster.") { r, rnd -> piano(seq(listOf(r, r + 1 + rnd.nextInt(4)), step = 0.8, len = 0.75)) },
            option("Lower", "Lower: the second note vibrates more slowly.") { r, rnd -> piano(seq(listOf(r, r - 1 - rnd.nextInt(4)), step = 0.8, len = 0.75)) },
        ), roots = 52..72),
        related = listOf(page("pitch-octave", "Next: octaves"), ear("pitch-ref", "name the note, after an A")),
    )

    private val octave = CoursePage(
        "pitch-octave", "Octaves", 1,
        "Double a frequency and you hear the same note again, higher. That distance is the octave, and it's why note names " +
            "repeat.",
        steps = listOf(
            say("A at ${hzText(hz(45))} Hz, ${hzText(hz(57))}, ${hzText(hz(69))} and ${hzText(hz(81))}: each is twice the one " +
                "before, and each is an A. Played upward they sound like one note climbing stairs, not different notes.",
                table("THE A'S", listOf("NOTE", "MIDI", "HZ", ""), (1..6).map { o -> hzRow(21 + 12 * o, if (o == 4) "the tuning A" else "") },
                    (1..6).map { one(21 + 12 * it) })),
            say("Twelve half steps take you to double the frequency, so the names repeat every twelve keys on a piano and every " +
                "twelve frets on a string. Halve the frequency and you go down an octave.",
                keysLine("C4 TO C5 · TWELVE HALF STEPS", "C4 Db4 D4 Eb4 E4 F4 F#4 G4 Ab4 A4 Bb4 B4 C5",
                    listOf("C4", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12 · C5"), step = 0.35)),
            say("Notes an octave apart blend so well they can pass for one note. When high and low voices sing a tune together " +
                "they're usually an octave apart without thinking about it. Compare an octave with a seventh, one key short: " +
                "the octave melts together, the seventh rubs.",
                keys("C4–C5, THEN C4–B4", "C4 B4 C5", play = Play(listOf(listOf(60, 72), listOf(60, 71)), 2.0))),
        ),
        inShort = listOf(
            "Octave = double the frequency; same letter.",
            "Twelve half steps, twelve keys, twelve frets.",
            "Octaves blend almost into one note.",
        ),
        hear = listOf(
            sound("A 110 · 220 · 440 · 880") { piano(seq(listOf(45, 57, 69, 81), step = 0.8, len = 0.75)) },
            sound("AN OCTAVE TOGETHER") { r -> piano(RefSound.block(listOf(r, r + 12), length = 2.5)) },
            sound("OCTAVE · THEN A SEVENTH") { r -> piano(RefSound.block(listOf(r, r + 12), length = 1.8) + RefSound.block(listOf(r, r + 11), at = 2.2, length = 1.8)) },
        ),
        tryIt = CourseTry("EVERY OCTAVE OF ANY NOTE ON YOUR NECK", RefKind.INTERVAL, "P8", "C"),
        check = CourseCheck("An octave, or not quite?", listOf(
            option("Octave", "An octave: the two notes melt into one.") { r, _ -> piano(RefSound.interval(r, r + 12)) },
            option("Not an octave", "Not an octave: a half step off, and the two notes rub instead of blending.") { r, rnd -> piano(RefSound.interval(r, r + if (rnd.nextBoolean()) 11 else 13)) },
        )),
        related = listOf(ref(RefKind.INTERVAL, "P8", "The octave"), ear("pitch-octave", "which octave")),
        yours = RefKind.INTERVAL to "P8",
    )

    // ------------------------------------------------------------------------------------------------- names

    private val letters = CoursePage(
        "names-letters", "The musical alphabet", 1,
        "Notes are named with seven letters, A B C D E F G. After G the letters start again at A, an octave up.",
        steps = listOf(
            say("On a piano the letters are the white keys. Find them by the black keys, which come in twos and threes: C is the " +
                "white key just left of each pair, F the white key just left of each three.",
                keysLine("THE WHITE KEYS FROM C", "C4 D4 E4 F4 G4 A4 B4", step = 0.45)),
            say("A black key takes its name from a neighbour. The key above C is C sharp (C♯); the same key, seen from D, is " +
                "D flat (D♭). A sharp raises a note a half step and a flat lowers it a half step.",
                keys("ONE BLACK KEY, TWO NAMES", "C4 C#4 D4", listOf("C", "C♯ / D♭", "D"), play = Play(listOf(listOf(60), listOf(61), listOf(62)), 0.6))),
            say("So each octave holds twelve different keys: seven letters and five in between. Played in a row, every key, they " +
                "make the chromatic scale.",
                keysLine("ALL TWELVE", "C4 C#4 D4 D#4 E4 F4 F#4 G4 G#4 A4 A#4 B4 C5", step = 0.35)),
        ),
        inShort = listOf(
            "Seven letters, A–G, then repeat.",
            "White keys are the letters; C sits left of the two black keys, F left of the three.",
            "♯ raises a half step, ♭ lowers one: twelve keys per octave.",
        ),
        hear = listOf(
            sound("C D E F G A B C") { piano(seq(Scales.byId("major").semitones.map { 60 + it } + 72)) },
            sound("ALL TWELVE") { piano(seq((60..72).toList(), step = 0.4, len = 0.38)) },
        ),
        tryIt = CourseTry("ALL TWELVE NOTES, FROM ANY START", RefKind.SCALE, "chromatic", "C"),
        check = CourseCheck("One note per letter, or all twelve?", listOf(
            option("Seven: one per letter", "Seven notes, one for each letter: a major scale.") { r, _ -> piano(seq(Scales.byId("major").semitones.map { r + it } + (r + 12), step = 0.4, len = 0.38)) },
            option("All twelve", "All twelve: every key in the octave, a half step at a time.") { r, _ -> piano(seq((r..r + 12).toList(), step = 0.4, len = 0.38)) },
        )),
        related = listOf(ref(RefKind.SCALE, "chromatic", "The chromatic scale"), ref(RefKind.SCALE, "major", "The major scale")),
    )

    private val octaveNumbers = CoursePage(
        "names-octaves", "Octave numbers and MIDI", 1,
        "The letters repeat every octave, so to say which C you mean you add an octave number.",
        steps = listOf(
            say("Middle C is C4. The number goes up at every C, so the C above it is C5, and the B just below middle C is B3, " +
                "not B4. This is scientific pitch notation.",
                keys("B3 · C4 · C5", "B3 C4 C5", listOf("B3", "C4 middle C", "C5"), play = Play(listOf(listOf(59), listOf(60), listOf(72)), 0.8))),
            say("MIDI numbers count every key instead: C4 is 60, A4 is 69, and each half step adds one. Tap a row.",
                table("OCTAVE NUMBERS", listOf("NOTE", "MIDI", "HZ", ""), listOf(
                    hzRow(28, "bass, low E"), hzRow(40, "guitar, low E"), hzRow(59, "just below middle C"), hzRow(60, "middle C"),
                    hzRow(69, "the tuning A"), hzRow(72, "an octave above middle C"),
                ), listOf(28, 40, 59, 60, 69, 72).map(::one))),
            say("Software doesn't all agree on the octave label. Ableton Live and Logic call MIDI note 60 C3, and FL Studio calls it " +
                "C5, so the MIDI number is the safe thing to check. The octave number also belongs to the letter, not the sound: " +
                "B♯3 is the same key as C4, and C♭4 the same key as B3.",
                table("MIDI NOTE 60 BY NAME", listOf("", "CALLS IT"), listOf(
                    listOf("Scientific pitch, this app", "C4"), listOf("Ableton Live, Logic", "C3"), listOf("FL Studio", "C5"),
                ), listOf(one(60), one(60), one(60)))),
        ),
        inShort = listOf(
            "Middle C = C4 = MIDI 60; the number changes at C.",
            "Each half step is one MIDI number; A4 = 69.",
            "DAWs differ on the label (C3, C4, C5); the MIDI number doesn't.",
        ),
        hear = listOf(
            sound("C3 · C4 · C5") { piano(seq(listOf(48, 60, 72), step = 0.8, len = 0.75)) },
            sound("B3 → C4") { piano(seq(listOf(59, 60), step = 0.8, len = 0.75)) },
        ),
        check = CourseCheck("Middle C first. Was the second C above or below it?", listOf(
            option("Above: C5", "Above: C5, an octave over middle C.") { _, _ -> piano(seq(listOf(60, 72), step = 0.9, len = 0.85)) },
            option("Below: C3", "Below: C3, an octave under middle C.") { _, _ -> piano(seq(listOf(60, 48), step = 0.9, len = 0.85)) },
        )),
        related = listOf(ear("pitch-octave", "which octave"), ear("pitch-abs", "name the note, no reference")),
    )

    private val enharmonic = CoursePage(
        "names-enharmonic", "One key, two names", 2,
        "C♯ and D♭ are the same piano key and the same fret, but in written music they aren't interchangeable. The name " +
            "follows the letter the music needs.",
        steps = listOf(
            say("A major scale uses each letter once. D major is D E F♯ G A B C♯: C♯, not D♭, because the scale already has a D " +
                "and the letter after B is C.",
                keysLine("D MAJOR · EVERY LETTER ONCE", "D4 E4 F#4 G4 A4 B4 C#5 D5")),
            say("Notes that share a key but not a name are enharmonic. White keys have other names too: B♯ is the key of C, E♯ of " +
                "F, C♭ of B and F♭ of E. They appear in keys like C♯ major (with E♯ and B♯) and C♭ major (with C♭ and F♭).",
                table("WHITE KEYS, OTHER NAMES", listOf("NAME", "PLAYED ON"), listOf(listOf("B♯", "C"), listOf("E♯", "F"), listOf("C♭", "B"), listOf("F♭", "E")),
                    listOf(60, 65, 59, 64).map(::one))),
            say("A double sharp (𝄪) raises a note two half steps and a double flat (𝄫) lowers it two, so F𝄪 is played on G and " +
                "B𝄫 on A. They turn up wherever the letters demand them. C°7 is stacked thirds, C E♭ G♭ and then a B: lowered " +
                "twice to fit, it's B𝄫. Likewise the ♯9 of E7♯9 is F𝄪.",
                keys("C°7 · THE B𝄫 IS PLAYED ON A", "C4 Eb4 Gb4 Bbb4")),
        ),
        inShort = listOf(
            "Enharmonic = same key, different name.",
            "The letter the music needs decides the name.",
            "𝄪 and 𝄫 move two half steps.",
        ),
        hear = listOf(sound("D MAJOR") { piano(RefSound.scale(RefSound.midis(2, Scales.byId("major").semitones, 60))) }),
        tryIt = CourseTry("ANY MAJOR SCALE: EVERY LETTER ONCE", RefKind.SCALE, "major", "D"),
        related = listOf(
            ref(RefKind.CHORD, "dim7", "C°7: the B𝄫"),
            ref(RefKind.CHORD, "7#9", "E7♯9: the F𝄪", "E"),
            ref(RefKind.SCALE, "major", "D major", "D"),
        ),
    )

    // ------------------------------------------------------------------------------------------------- steps

    private val half = CoursePage(
        "steps-half", "The half step", 1,
        "The half step, or semitone, is the smallest distance on a piano or a fretted neck. Every other distance is counted " +
            "in them.",
        steps = listOf(
            say("It's the very next key, black or white, or the next fret on the same string. C to C♯ is a half step, and so is " +
                "C♯ to D.",
                keys("C → C♯ → D", "C4 C#4 D4", play = Play(listOf(listOf(60), listOf(61), listOf(62)), 0.7))),
            say("Most neighbouring white keys have a black key between them, so they're two half steps apart. E–F and B–C have no " +
                "black key between, so those two are half steps.",
                keys("E–F AND B–C · NOTHING BETWEEN", "E4 F4 B4 C5", faint = "C4 D4 G4 A4",
                    play = Play(listOf(listOf(64), listOf(65), listOf(71), listOf(72)), 0.7))),
            say("Twelve half steps make an octave. On a string that's twelve frets, which is why the 12th fret sounds the open " +
                "string an octave up.",
                frets("LOW E STRING · OPEN AND 12TH FRET", listOf("low E" to setOf(0, 12)), listOf(Play(listOf(listOf(40), listOf(52)), 0.9)))),
        ),
        inShort = listOf(
            "Half step = next key = next fret.",
            "E–F and B–C are the white-key half steps.",
            "12 half steps = an octave = the 12th fret.",
        ),
        hear = listOf(
            sound("HALF STEP · UP, DOWN, TOGETHER") { r -> piano(RefSound.interval(r, r + 1)) },
            sound("E → F · B → C") { piano(seq(listOf(64, 65), step = 0.7, len = 0.65) + seq(listOf(71, 72), step = 0.7, len = 0.65).map { it.copy(start = it.start + 1.8, end = it.end + 1.8) }) },
        ),
        tryIt = CourseTry("HALF STEPS FROM ANY NOTE", RefKind.INTERVAL, "m2", "E"),
        check = CourseCheck("A half step or a whole step?", listOf(
            option("Half step", "A half step: the next key, one fret.") { r, rnd -> piano(seq(if (rnd.nextBoolean()) listOf(r, r + 1) else listOf(r + 1, r), step = 0.8, len = 0.75)) },
            option("Whole step", "A whole step: two keys, two frets.") { r, rnd -> piano(seq(if (rnd.nextBoolean()) listOf(r, r + 2) else listOf(r + 2, r), step = 0.8, len = 0.75)) },
        )),
        related = listOf(ref(RefKind.INTERVAL, "m2", "Minor second"), ear("iv-2-3", "seconds and thirds")),
        yours = RefKind.INTERVAL to "m2",
    )

    private val whole = CoursePage(
        "steps-whole", "The whole step", 1,
        "Two half steps make a whole step, or whole tone. Scales are built from whole and half steps in a fixed order.",
        steps = listOf(
            say("C to D is a whole step: two keys over, with C♯ between them. On one string it's two frets. E to F♯ is a whole " +
                "step too.",
                keys("C → D · ONE KEY SKIPPED", "C4 D4", faint = "C#4", play = Play(listOf(listOf(60), listOf(62), listOf(60, 62)), 0.8))),
            say("The major scale goes W W H W W W H (W a whole step, H a half step). Its two half steps fall between notes 3 and 4 " +
                "and between 7 and 8; on C that's E–F and B–C, exactly the white keys with nothing between. That's why C major " +
                "needs no black keys.",
                keysLine("C MAJOR · W W H W W W H", "C4 D4 E4 F4 G4 A4 B4 C5", listOf("C", "W", "W", "H", "W", "W", "W", "H"))),
            say("A scale of nothing but whole steps, the whole-tone scale, has six notes. Every step is the same size, so no note " +
                "stands out as home, and it floats.",
                keysLine("WHOLE-TONE · ALL W", "C4 D4 E4 F#4 G#4 A#4 C5")),
        ),
        inShort = listOf(
            "Whole step = two half steps = two frets.",
            "Major scale: W W H W W W H.",
            "All whole steps: the six-note whole-tone scale.",
        ),
        hear = listOf(
            sound("WHOLE STEP · UP, DOWN, TOGETHER") { r -> piano(RefSound.interval(r, r + 2)) },
            sound("W W H W W W H") { r -> piano(RefSound.scale(RefSound.midis(Math.floorMod(r, 12), Scales.byId("major").semitones, 60))) },
            sound("ALL WHOLE STEPS") { r -> piano(RefSound.scale(RefSound.midis(Math.floorMod(r, 12), Scales.byId("whole-tone").semitones, 60))) },
        ),
        tryIt = CourseTry("THE MAJOR SCALE FROM ANY KEY · W W H W W W H", RefKind.SCALE, "major", "C"),
        check = CourseCheck("All half steps, or all whole steps?", listOf(
            option("Half steps", "Half steps: a chromatic run.") { r, _ -> piano(seq((0..4).map { r + it }, step = 0.42, len = 0.4)) },
            option("Whole steps", "Whole steps: part of a whole-tone scale.") { r, _ -> piano(seq((0..4).map { r + 2 * it }, step = 0.42, len = 0.4)) },
        )),
        related = listOf(ref(RefKind.INTERVAL, "M2", "Major second"), ref(RefKind.SCALE, "whole-tone", "Whole-tone scale"), ear("iv-2-3", "seconds and thirds")),
        yours = RefKind.INTERVAL to "M2",
    )

    // ------------------------------------------------------------------------------------------------- timbre

    private val timbre = CoursePage(
        "timbre-what", "Timbre", 2,
        "Play the same A on a piano, a guitar and a flute and you hear one pitch in three colours. That colour is timbre.",
        steps = listOf(
            say("A real note isn't a single frequency. It's a stack: the fundamental, which sets the pitch, and fainter partials " +
                "above it. A sine wave is the one sound with nothing above the fundamental, and it sounds plain and hollow.",
                null, tones("SINE · 220 HZ") { listOf(Tone(0.0, 2.0, 220.0, SINE, 0.3)) }),
            say("Add partials and it brightens. All of them, each weaker than the last, gives the bright buzz of a sawtooth. Odd " +
                "partials only gives the hollow, reedy sound of a square wave. The table gives each recipe's partial strengths.",
                table("THREE RECIPES · PARTIAL STRENGTHS", listOf("", "1", "2", "3", "4", "5", "6"), listOf(
                    listOf("Sine") + SINE.let { s -> (0 until 6).map { s.getOrElse(it) { 0.0 } } }.map { fmt(2, it) },
                    listOf("All partials") + BRIGHT.take(6).map { fmt(2, it) },
                    listOf("Odd only") + HOLLOW.take(6).map { fmt(2, it) },
                )),
                tones("ALL PARTIALS") { listOf(Tone(0.0, 2.0, 220.0, BRIGHT, 0.12)) }, tones("ODD ONLY") { listOf(Tone(0.0, 2.0, 220.0, HOLLOW, 0.16)) }),
            say("How strong each partial is, and how the sound starts and dies away, is most of what tells instruments apart. A " +
                "piano's hammer gives a sharp start and a long fade. A synth's low-pass filter darkens a sound by taking the upper " +
                "partials away."),
        ),
        inShort = listOf(
            "Timbre = the colour of a sound at a given pitch.",
            "A note = fundamental + partials; their strengths shape the colour.",
            "The start and the fade matter as much as the partials.",
        ),
        hear = listOf(
            sound("SINE") { EarAudio(emptyList(), listOf(Tone(0.0, 2.0, 220.0, SINE, 0.3))) },
            sound("ALL PARTIALS") { EarAudio(emptyList(), listOf(Tone(0.0, 2.0, 220.0, BRIGHT, 0.12))) },
            sound("ODD PARTIALS") { EarAudio(emptyList(), listOf(Tone(0.0, 2.0, 220.0, HOLLOW, 0.16))) },
            sound("PIANO") { piano(listOf(note(0.0, 2.0, 57))) },
        ),
        check = CourseCheck("A pure sine, or a tone with partials?", listOf(
            option("Pure sine", "A sine: one frequency, nothing above it.") { r, _ -> EarAudio(emptyList(), listOf(Tone(0.0, 2.0, hz(r), SINE, 0.3))) },
            option("With partials", "Partials above the fundamental make it brighter and buzzier.") { r, rnd -> EarAudio(emptyList(), listOf(Tone(0.0, 2.0, hz(r), if (rnd.nextBoolean()) BRIGHT else HOLLOW, 0.13))) },
        ), roots = 45..64),
        related = listOf(page("timbre-harmonics", "Next: the harmonic series"), ear("tt-partials", "which harmonic")),
    )

    private val series = (1..8).map { n -> n to Acoustics.harmonic(n) }

    private val harmonics = CoursePage(
        "timbre-harmonics", "The harmonic series", 2,
        "In strings, winds and the voice the partials aren't random: they fall at whole-number multiples of the fundamental. " +
            "That pattern is the harmonic series, and much of harmony grows out of it.",
        steps = listOf(
            say("Twice the fundamental, three times, four times and so on. Over a low C (C2, ${hzText(hz(36))} Hz) the first eight " +
                "are ${series.joinToString(", ") { (_, h) -> nameOf(36 + h.first) }}. Each row plays the nearest piano note; the " +
                "button below plays the true harmonics.",
                table("HARMONICS OF C2", listOf("N", "NOTE", "HZ", "¢ VS PIANO"), series.map { (n, h) ->
                    listOf("$n", nameOf(36 + h.first), hzText(hz(36) * n), centsText(h.second))
                }, series.map { (_, h) -> one(36 + h.first) }),
                tones("THE TRUE HARMONICS 1 TO 8") { (1..8).map { n -> Tone((n - 1) * 0.75, (n - 1) * 0.75 + 0.7, hz(36) * n, SINE, 0.3) } }),
            say("Most land close to piano notes, but the 7th is ${fmt(0, abs(series[6].second.second))} cents flat of the piano's " +
                "B♭ and the 5th, the E, ${fmt(0, abs(series[4].second.second))} cents flat. Built up one at a time, the harmonics " +
                "fuse into a single bright C: that's what a C on a real instrument is.",
                null, tones("BUILT UP, ONE AT A TIME") { (1..8).map { n -> Tone((n - 1) * 0.7, 7.5, hz(36) * n, SINE, 0.1) } }),
            say("Neighbouring harmonics make the intervals that sound most settled: 2:1 the octave, 3:2 the fifth, 4:3 the fourth, " +
                "5:4 the major third, 6:5 the minor third. Simple ratio, settled sound.",
                table("NEIGHBOURS IN THE SERIES", listOf("HARMONICS", "RATIO", "INTERVAL", "ON C2"), listOf(
                    listOf("1–2", "2:1", "octave", "C2–C3"), listOf("2–3", "3:2", "fifth", "C3–G3"), listOf("3–4", "4:3", "fourth", "G3–C4"),
                    listOf("4–5", "5:4", "major third", "C4–E4"), listOf("5–6", "6:5", "minor third", "E4–G4"),
                ), listOf(36 to 48, 48 to 55, 55 to 60, 60 to 64, 64 to 67).map { (a, b) -> Play(listOf(listOf(a, b)), 1.6) })),
            say("On a guitar you can play them. Rest a finger lightly on a string right over the 12th fret and pluck: the 2nd " +
                "harmonic. Over the 7th fret: the 3rd. Over the 5th fret: the 4th. Those frets divide the string into halves, " +
                "thirds and quarters.",
                frets("A STRING · WHERE THE HARMONICS ARE", listOf("12th · 2nd harmonic" to setOf(12), "7th · 3rd harmonic" to setOf(7), "5th · 4th harmonic" to setOf(5))),
                tones("OPEN A · 12TH · 7TH · 5TH FRET HARMONICS") { listOf(1, 2, 3, 4).mapIndexed { i, n -> Tone(i * 1.0, i * 1.0 + 0.95, 110.0 * n, listOf(1.0, 0.15), 0.28) } }),
        ),
        inShort = listOf(
            "Harmonics = whole-number multiples of the fundamental.",
            "Over C: C C G C E G B♭ C; the 5th and 7th are flat of the piano.",
            "Neighbouring harmonics give octave, fifth, fourth, thirds. Bells, drums and stiff strings have partials that aren't whole multiples.",
        ),
        hear = listOf(
            sound("HARMONICS 1 TO 8 ON C2") { EarAudio(emptyList(), (1..8).map { n -> Tone((n - 1) * 0.75, (n - 1) * 0.75 + 0.7, hz(36) * n, SINE, 0.3) }) },
            sound("BUILT UP, ONE AT A TIME") { EarAudio(emptyList(), (1..8).map { n -> Tone((n - 1) * 0.7, 7.5, hz(36) * n, SINE, 0.1) }) },
            sound("OPEN A · 12TH · 7TH · 5TH FRET HARMONICS") { EarAudio(emptyList(), listOf(1, 2, 3, 4).mapIndexed { i, n -> Tone(i * 1.0, i * 1.0 + 0.95, 110.0 * n, listOf(1.0, 0.15), 0.28) }) },
        ),
        check = CourseCheck("A low note, then one of its harmonics. Which harmonic?", listOf(2, 3, 4, 5).map { n ->
            val (s, _) = Acoustics.harmonic(n)
            val name = mapOf(12 to "an octave", 19 to "an octave and a fifth", 24 to "two octaves", 28 to "two octaves and a third").getValue(s)
            option("${n}${if (n == 2) "nd" else if (n == 3) "rd" else "th"}", "The ${n}${if (n == 2) "nd" else if (n == 3) "rd" else "th"} harmonic: $n times the frequency, $name up.") { r, _ ->
                val f = hz(r)
                EarAudio(emptyList(), listOf(Tone(0.0, 1.2, f, SINE, 0.3), Tone(1.4, 2.6, f * n, SINE, 0.3), Tone(2.9, 5.0, f, SINE, 0.25), Tone(2.9, 5.0, f * n, SINE, 0.25)))
            }
        }, roots = 36..48),
        related = listOf(
            ref(RefKind.INTERVAL, "P5", "Perfect fifth · 3:2"), ref(RefKind.INTERVAL, "P4", "Perfect fourth · 4:3"),
            ref(RefKind.INTERVAL, "M3", "Major third · 5:4"), ear("tt-partials", "which harmonic"),
        ),
    )

    // ------------------------------------------------------------------------------------------------- beats

    private fun pair(a: Double, b: Double, len: Double = 4.0) = EarAudio(emptyList(), listOf(Tone(0.0, len, a, SINE, 0.25), Tone(0.0, len, b, SINE, 0.25)))

    private val beating = CoursePage(
        "beats-what", "Beats", 2,
        "Two steady tones close in frequency don't sound like two notes. They merge into one that swells and fades.",
        steps = listOf(
            say("The swells are called beats, and they come as many times a second as the frequencies differ. Try the HEAR buttons " +
                "below against this table.",
                table("BEATS A SECOND = THE DIFFERENCE IN HZ", listOf("TONES", "APART", "BEATS"), listOf(
                    listOf("440 + 440", "0 Hz", "none"), listOf("440 + 442", "2 Hz", "2 a second"),
                    listOf("440 + 444", "4 Hz", "4 a second"), listOf("440 + 447", "7 Hz", "7 a second"),
                ))),
            say("As the two come together the beating slows, then stops at unison. That's how instruments were tuned by ear long " +
                "before tuners, and often still are: play the two notes and turn the peg until the wobble stops.",
                null, tones("SLIDING INTO TUNE") { listOf(446.0, 443.0, 441.0, 440.0).flatMapIndexed { i, f -> listOf(Tone(i * 2.2, i * 2.2 + 2.0, 440.0, SINE, 0.25), Tone(i * 2.2, i * 2.2 + 2.0, f, SINE, 0.25)) } }),
            say("Partials beat too. When the top note of a fifth is slightly off, the low note's 3rd harmonic and the high note's " +
                "2nd are a few hertz apart, and the fifth wobbles, though its two fundamentals are far apart.",
                null, tones("FIFTH IN TUNE · THEN 2 HZ OFF") { listOf(Tone(0.0, 3.0, 220.0, SIX, 0.12), Tone(0.0, 3.0, 330.0, SIX, 0.12), Tone(3.4, 6.4, 220.0, SIX, 0.12), Tone(3.4, 6.4, 331.0, SIX, 0.12)) }),
        ),
        inShort = listOf(
            "Beats a second = the difference in Hz.",
            "No beating = in tune.",
            "Partials beat too, so out-of-tune fifths and thirds wobble.",
        ),
        hear = listOf(
            sound("440 + 440") { pair(440.0, 440.0) },
            sound("440 + 442") { pair(440.0, 442.0) },
            sound("440 + 444") { pair(440.0, 444.0) },
            sound("440 + 447") { pair(440.0, 447.0) },
        ),
        check = CourseCheck("Slow beating or fast?", listOf(
            option("Slow", "Slow: the two tones are about 1 Hz apart.") { r, _ -> pair(hz(r), hz(r) + 1.0) },
            option("Fast", "Fast: the two tones are about 6 Hz apart.") { r, _ -> pair(hz(r), hz(r) + 6.0) },
        ), roots = 55..69),
        related = listOf(page("beats-tune", "Next: tuning with harmonics"), ear("tt-beats", "beating")),
    )

    private val harmonicTuning = CoursePage(
        "beats-tune", "Tuning with harmonics", 2,
        "A common way to tune a guitar by ear uses harmonics and beats. It works, with one catch worth knowing.",
        steps = listOf(
            say("The harmonic over the 5th fret of the low E string is E4, two octaves up (4 × ${hzText(hz(40))} Hz). The harmonic " +
                "over the 7th fret of the A string is an E4 too (3 × ${hzText(hz(45))} Hz). Sound them together and listen.",
                frets("LOW E AND A · THE TWO HARMONICS", listOf("E string · 5th fret" to setOf(5), "A string · 7th fret" to setOf(7)))),
            say("If they beat, the strings disagree: turn the A until the beating stops. The HEAR buttons below go from in tune to " +
                "beating."),
            say("The catch: the A string's harmonic is a pure fifth above the A, but equal temperament's E is slightly lower. Tuned " +
                "this way the A comes out ${fmt(2, abs(Acoustics.cents(1.5) - 700))} cents flat of a tuner, and repeating it string " +
                "by string lets the error add up. A tuner, or matching a fretted note against the next open string, avoids it.",
                table("THE TWO E'S", listOf("", "HZ"), listOf(
                    listOf("Equal-tempered E4", hzText(hz(64))), listOf("A string × 3", hzText(3 * hz(45))),
                    listOf("Apart", "${fmt(2, 3 * hz(45) - hz(64))} Hz · a beat every ${fmt(1, 1 / (3 * hz(45) - hz(64)))} s"),
                ))),
        ),
        inShort = listOf(
            "5th-fret harmonic on E = 7th-fret harmonic on A = E4.",
            "Tune until the beats stop.",
            "The method leaves each string a couple of cents flat of equal temperament.",
        ),
        hear = listOf(
            sound("TWO E4s IN TUNE") { pair(hz(64), hz(64)) },
            sound("${hzText(hz(64))} + 330 · NEARLY STILL") { pair(hz(64), 330.0, 8.0) },
            sound("${hzText(hz(64))} + 332 · BEATING") { pair(hz(64), 332.0) },
        ),
        check = CourseCheck("In tune, or beating?", listOf(
            option("In tune", "In tune: one steady tone.") { r, _ -> pair(hz(r), hz(r)) },
            option("Beating", "Beating: about 3 Hz apart.") { r, _ -> pair(hz(r), hz(r) + 3.0) },
        ), roots = 60..69),
        related = listOf(page("tuning-equal", "Equal temperament"), ear("tt-beats", "beating")),
    )

    // ------------------------------------------------------------------------------------------------- tuning

    private fun cents(f: Double, c: Double) = f * 2.0.pow(c / 1200)
    private fun twoTones(a: Double, b: Double) = EarAudio(emptyList(), listOf(Tone(0.0, 1.2, a), Tone(1.5, 2.7, b)))

    private val centsPage = CoursePage(
        "tuning-cents", "Cents", 3,
        "A half step is too coarse for tuning, so smaller differences are measured in cents.",
        steps = listOf(
            say("There are 100 cents to the equal-tempered half step and 1200 to the octave. 50 cents is halfway between two keys.",
                keys("ONE HALF STEP = 100 CENTS", "C4 C#4", listOf("0 ¢", "100 ¢"), play = Play(listOf(listOf(60), listOf(61)), 0.8))),
            say("The cents between two frequencies are 1200 × log₂(f₂ ÷ f₁). Cents follow the ear rather than the hertz: 10 cents " +
                "sharp is the same amount of sharp at 80 Hz as at 800 Hz, though the gap in hertz is ten times wider.",
                table("10 CENTS SHARP", listOf("NOTE AT", "10 ¢ SHARP", "GAP"), listOf(80.0, 800.0).map { f ->
                    listOf("${hzText(f)} Hz", "${fmt(2, cents(f, 10.0))} Hz", "${fmt(2, cents(f, 10.0) - f)} Hz")
                })),
            say("An A tuned to 446 Hz is ${fmt(1, Acoustics.cents(446.0 / 440))} cents sharp of 440. With steady tones one after " +
                "the other, trained listeners can often tell differences of around 5 cents; a tuner's needle shows them directly."),
        ),
        inShort = listOf(
            "100 cents = a half step; 1200 = an octave.",
            "Cents = 1200 × log₂(f₂ ÷ f₁).",
            "About 5 cents is near the limit of a trained ear.",
        ),
        hear = listOf(
            sound("IN TUNE, THEN 25 ¢ SHARP") { twoTones(440.0, cents(440.0, 25.0)) },
            sound("IN TUNE, THEN 10 ¢ SHARP") { twoTones(440.0, cents(440.0, 10.0)) },
            sound("IN TUNE, THEN 25 ¢ FLAT") { twoTones(440.0, cents(440.0, -25.0)) },
        ),
        check = CourseCheck("Is the second tone sharp or flat of the first?", listOf(
            option("Sharp", "Sharp: 20 cents higher.") { r, _ -> twoTones(hz(r), cents(hz(r), 20.0)) },
            option("Flat", "Flat: 20 cents lower.") { r, _ -> twoTones(hz(r), cents(hz(r), -20.0)) },
        ), roots = 57..72),
        related = listOf(ear("pitch-cents-25", "higher or lower · 25 cents"), ear("pitch-cents-10", "higher or lower · 10 cents"), ear("pitch-cents-5", "higher or lower · 5 cents")),
    )

    /** Intervals against their pure ratios: (name, semitones, ratio text, ratio). */
    internal val PURE = listOf(
        listOf("P8", 12, "2:1", 2.0), listOf("P5", 7, "3:2", 1.5), listOf("P4", 5, "4:3", 4.0 / 3), listOf("M3", 4, "5:4", 1.25),
        listOf("m3", 3, "6:5", 1.2), listOf("M6", 9, "5:3", 5.0 / 3), listOf("m6", 8, "8:5", 1.6), listOf("m7", 10, "7:4", 1.75),
    )

    private fun held(a: Double, b: Double) = EarAudio(emptyList(), listOf(Tone(0.0, 3.5, a, SIX, 0.12), Tone(0.0, 3.5, b, SIX, 0.12)))

    private val equal = CoursePage(
        "tuning-equal", "Equal temperament", 3,
        "Pure intervals don't fit together into twelve keys. Every tuning system is a compromise, and equal temperament is the " +
            "one pianos, guitars and DAWs use.",
        steps = listOf(
            say("Stack twelve pure fifths (3:2) from C and you should come back to C, seven octaves up. You don't: you land " +
                "${fmt(2, Acoustics.pythagoreanComma)} cents sharp. That leftover is the Pythagorean comma, and every tuning " +
                "system is a way of sharing it out.",
                table("THE CIRCLE DOESN'T CLOSE", listOf("", "CENTS"), listOf(
                    listOf("Twelve pure fifths", fmt(2, 12 * Acoustics.cents(1.5))), listOf("Seven octaves", "8400.00"),
                    listOf("Left over", fmt(2, Acoustics.pythagoreanComma)),
                ))),
            say("Equal temperament shares it evenly. Every half step is the same ratio, the twelfth root of 2 (about " +
                "${fmt(5, 2.0.pow(1.0 / 12))}), so every key sounds alike and you can modulate anywhere. The price is that only " +
                "the octave is pure.",
                table("EQUAL AGAINST PURE", listOf("", "EQUAL ¢", "PURE", "PURE ¢", "EQUAL IS"), PURE.map { r ->
                    val pure = Acoustics.cents(r[3] as Double)
                    listOf(r[0] as String, "${(r[1] as Int) * 100}", r[2] as String, fmt(2, pure), centsText((r[1] as Int) * 100 - pure))
                })),
            say("The equal fifth is ${fmt(2, Acoustics.cents(1.5) - 700)} cents narrow of 3:2, too little to notice. The equal " +
                "major third is ${fmt(2, 400 - Acoustics.cents(1.25))} cents wide of 5:4, which you can hear as a shimmer when a " +
                "held third beats; compare the two below. Choirs, string quartets and barbershop singers often bend toward the " +
                "pure ratios; a piano can't."),
        ),
        inShort = listOf(
            "Twelve pure fifths overshoot seven octaves by the Pythagorean comma.",
            "Equal temperament: every half step = 2^(1/12); only octaves are pure.",
            "Equal fifths are nearly pure; equal thirds are audibly wide.",
        ),
        hear = listOf(
            sound("EQUAL MAJOR THIRD") { r -> held(hz(r), hz(r + 4)) },
            sound("PURE MAJOR THIRD") { r -> held(hz(r), hz(r) * 1.25) },
            sound("EQUAL FIFTH · PURE FIFTH") { r -> EarAudio(emptyList(), held(hz(r), hz(r + 7)).tones + held(hz(r), hz(r) * 1.5).tones.map { it.copy(start = it.start + 4.0, end = it.end + 4.0) }) },
        ),
        check = CourseCheck("A held major third. Equal-tempered, or pure?", listOf(
            option("Equal", "Equal: 14 cents wide, so its upper partials beat.") { r, _ -> held(hz(r), hz(r + 4)) },
            option("Pure", "Pure: 5:4, the partials line up and it sits still.") { r, _ -> held(hz(r), hz(r) * 1.25) },
        ), roots = 52..64),
        related = listOf(ear("tt-just", "equal or just"), ref(RefKind.INTERVAL, "M3", "Major third"), page("beats-what", "Beats")),
    )

    private val concert = CoursePage(
        "tuning-a440", "Concert pitch", 3,
        "Which frequency counts as A is an agreement, not a law of nature.",
        steps = listOf(
            say("A = 440 Hz is the international standard (ISO 16, 1975) and the default on tuners and in DAWs. Other A's are in " +
                "everyday use; the table gives how far each is from 440.",
                table("A4 IN USE", listOf("", "A4", "VS 440"), listOf(
                    listOf("ISO standard", "440 Hz", "0"),
                    listOf("Many orchestras", "442 Hz", "${centsText(Acoustics.cents(442.0 / 440))} ¢"),
                    listOf("Baroque ensembles", "415 Hz", "${centsText(Acoustics.cents(415.0 / 440))} ¢"),
                    listOf("Some players", "432 Hz", "${centsText(Acoustics.cents(432.0 / 440))} ¢"),
                ))),
            say("Baroque 415 is ${fmt(0, abs(Acoustics.cents(415.0 / 440)))} cents down, very nearly a half step, so a baroque A " +
                "sounds where a modern A♭ does. 432 is ${fmt(2, abs(Acoustics.cents(432.0 / 440)))} cents flat, about a third of a " +
                "half step, with no special acoustic property."),
            say("What matters is that everyone playing together agrees, and that a recording you play along to may not be at 440."),
        ),
        inShort = listOf(
            "A = 440 Hz is a standard, not physics.",
            "415 (baroque) is about a half step down; 442 is common in orchestras.",
            "Players together just need to agree.",
        ),
        hear = listOf(
            sound("A 440, THEN A 415") { twoTones(440.0, 415.0) },
            sound("A 440, THEN A 432") { twoTones(440.0, 432.0) },
            sound("A 440, THEN A 442") { twoTones(440.0, 442.0) },
        ),
        check = CourseCheck("Two A's. Same tuning, or different?", listOf(
            option("Same", "The same: both at 440 Hz.") { _, _ -> twoTones(440.0, 440.0) },
            option("Different", "Different: 440, then 432 Hz.") { _, _ -> twoTones(440.0, 432.0) },
        )),
        related = listOf(ear("pitch-ref", "name the note, after an A"), page("tuning-cents", "Cents")),
    )

    val topics = listOf(
        CourseTopic("pitch", "Pitch and frequency", "what you hear as high and low, octaves", 1, listOf(frequency, octave)),
        CourseTopic("names", "Note names", "letters, sharps and flats, octave numbers", 1, listOf(letters, octaveNumbers, enharmonic)),
        CourseTopic("steps", "Half steps and whole steps", "the distances scales are built from", 1, listOf(half, whole)),
        CourseTopic("timbre", "Timbre and the harmonic series", "why a piano isn't a flute", 2, listOf(timbre, harmonics)),
        CourseTopic("beats", "Beats", "hearing two pitches disagree", 2, listOf(beating, harmonicTuning)),
        CourseTopic("tuning", "Cents and tuning systems", "equal temperament, pure intervals, A = 440", 3, listOf(centsPage, equal, concert)),
    )
}
