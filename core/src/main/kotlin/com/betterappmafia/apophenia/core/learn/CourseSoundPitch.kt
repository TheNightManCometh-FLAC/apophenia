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

    // ------------------------------------------------------------------------------------------------- pitch

    private val frequency = CoursePage(
        "pitch-frequency", "Pitch is frequency", 1,
        "A note is a pressure wave in the air repeating at a steady rate. That rate, in cycles per second or hertz (Hz), " +
            "is its frequency, and we hear frequency as pitch: faster is higher. The A above middle C is tuned to " +
            "${hzText(hz(69))} Hz in most music today, and middle C is then ${hzText(hz(60))} Hz. A piano runs from " +
            "${hzText(hz(21))} Hz at the bottom to ${hzText(hz(108))} Hz at the top. Young ears hear roughly 20 Hz to " +
            "20,000 Hz, and the top end comes down with age.",
        see = CourseSee.Table("FREQUENCIES · A4 = 440 HZ", listOf("NOTE", "MIDI", "HZ", ""), listOf(
            hzRow(21, "lowest piano key"), hzRow(28, "bass, low E"), hzRow(40, "guitar, low E"), hzRow(60, "middle C"),
            hzRow(64, "guitar, high E"), hzRow(69, "the tuning A"), hzRow(108, "highest piano key"),
        )),
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
        "Double a frequency and you hear the same note again, higher: A at ${hzText(hz(45))}, ${hzText(hz(57))}, " +
            "${hzText(hz(69))} and ${hzText(hz(81))} Hz is A every time. The distance from one to the next is an octave, " +
            "and it's why note names repeat every twelve piano keys or every twelve frets. Halve the frequency and you go " +
            "down an octave. Notes an octave apart blend so well they can pass for one note: when people with high and " +
            "low voices sing a tune together, they're usually an octave apart without thinking about it.",
        see = CourseSee.Table("THE A'S", listOf("NOTE", "MIDI", "HZ", ""), (1..6).map { o -> hzRow(21 + 12 * o, if (o == 4) "the tuning A" else "") }),
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
        "Notes are named with seven letters, A B C D E F G. After G the letters start again at A, an octave up. On a " +
            "piano the letters are the white keys. The black keys come in twos and threes: C is the white key just left " +
            "of each pair, F the white key just left of each three. A black key takes its name from a neighbour: the key " +
            "above C is C sharp (C♯), and the same key seen from D is D flat (D♭). A sharp raises a note one half step, " +
            "a flat lowers it one, so each octave holds twelve different keys: seven letters and five in between.",
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
        "To say which C you mean, add an octave number. Middle C is C4 and the C above it is C5. The number goes up at " +
            "each C, so the B just below middle C is B3. This is scientific pitch notation. MIDI numbers count every key " +
            "instead: C4 is 60, A4 is 69, and each half step adds one. Software doesn't all agree on the octave label: " +
            "Ableton Live and Logic call MIDI note 60 C3, and FL Studio calls it C5, so the MIDI number is the safe thing " +
            "to check. The octave number belongs to the letter, not to the sound: B♯3 is the same key as C4, and C♭4 is " +
            "the same key as B3.",
        see = CourseSee.Table("OCTAVE NUMBERS", listOf("NOTE", "MIDI", "HZ", ""), listOf(
            hzRow(28, "bass, low E"), hzRow(40, "guitar, low E"), hzRow(59, "just below middle C"), hzRow(60, "middle C"),
            hzRow(69, "the tuning A"), hzRow(72, "an octave above middle C"),
        )),
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
            "follows the letter the music needs. A major scale uses each letter once, so D major is D E F♯ G A B C♯: " +
            "C♯, not D♭, because the scale already has a D. Notes that share a key but not a name are enharmonic. White " +
            "keys have other names too: B♯ is the key of C, E♯ of F, C♭ of B and F♭ of E. A double sharp (𝄪) raises a " +
            "note two half steps and a double flat (𝄫) lowers it two, so F𝄪 is played on G and B𝄫 on A. They appear " +
            "wherever the letters demand them: C°7 is C E♭ G♭ B𝄫, and the ♯9 of E7♯9 is F𝄪.",
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
        "The smallest step on a piano or a fretted neck is the half step, or semitone: the very next key, black or " +
            "white, or the next fret on the same string. Most neighbouring white keys have a black key between them; " +
            "E–F and B–C don't, so those two are half steps. Twelve half steps make an octave, which is why the 12th " +
            "fret sounds the open string an octave up.",
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
        "Two half steps make a whole step, or whole tone: C to D, E to F♯, two frets on one string. Scales are built from " +
            "these steps in a fixed order. The major scale goes W W H W W W H (W a whole step, H a half step), so its two " +
            "half steps fall between the 3rd and 4th notes and between the 7th and the octave; on C that's E–F and B–C, " +
            "which is why C major needs no black keys. A scale of nothing but whole steps, the whole-tone scale, has only " +
            "six notes and no clear home.",
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
        "Play the same A on a piano, a guitar and a flute and you hear one pitch in three colours. That colour is " +
            "timbre. A real note isn't a single frequency. It's a stack: the fundamental, which sets the pitch, and " +
            "fainter partials above it. How strong each partial is, and how the sound starts and dies away, is most of " +
            "what tells instruments apart. A sine wave has no partials at all and sounds plain and hollow; add the " +
            "partials and it brightens. Odd partials alone give the hollow, reedy sound of a square wave. A synth's " +
            "low-pass filter darkens a sound by taking the upper partials away.",
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
        "In strings, winds and the voice the partials fall at whole-number multiples of the fundamental: twice it, three " +
            "times, four times and so on. That's the harmonic series. Over a low C (C2, ${hzText(hz(36))} Hz) the first " +
            "eight are ${series.joinToString(", ") { (_, h) -> nameOf(36 + h.first) }}. Most land close to piano notes, " +
            "but the 7th is ${fmt(0, abs(series[6].second.second))} cents flat of the piano's B♭ and the 5th, the " +
            "E, is ${fmt(0, abs(series[4].second.second))} cents flat. Neighbouring harmonics make the intervals " +
            "that sound most settled: the octave (2:1), the fifth (3:2), the fourth (4:3), the major third (5:4) and the " +
            "minor third (6:5). On a guitar you can play them: rest a finger lightly on a string right over the 12th " +
            "fret and pluck for the 2nd harmonic, over the 7th fret for the 3rd, over the 5th fret for the 4th. Bells, " +
            "drums and very stiff strings have partials that aren't whole-number multiples, which is part of their sound.",
        see = CourseSee.Table("HARMONICS OF C2", listOf("N", "NOTE", "HZ", "¢ VS PIANO"), series.map { (n, h) ->
            listOf("$n", nameOf(36 + h.first), hzText(hz(36) * n), centsText(h.second))
        }),
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
        "Two steady tones close in frequency merge into one that swells and fades. The swells are called beats, and they " +
            "come as many times a second as the frequencies differ: 440 Hz with 442 Hz beats twice a second, with 444 Hz " +
            "four times. As the two come together the beating slows and then stops at unison. That's how instruments were " +
            "tuned by ear long before tuners, and often still are: play the two notes and turn the peg until the wobble " +
            "stops. Partials beat too. When the top note of a fifth is slightly off, the low note's 3rd harmonic and the " +
            "high note's 2nd are a few hertz apart and the fifth wobbles, though its two fundamentals are far apart.",
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
        "A common way to tune a guitar by ear uses harmonics and beats. The harmonic over the 5th fret of the low E string " +
            "is E4, two octaves up; the harmonic over the 7th fret of the A string is an E4 too. Sound them together and " +
            "listen: if they beat, the strings disagree, so turn the A until the beating stops. There's a catch. The A " +
            "string's harmonic is three times 110 Hz, ${hzText(3 * hz(45))} Hz, a pure fifth above the A, while the " +
            "equal-tempered E4 is ${hzText(hz(64))} Hz. Tuned this way the A comes out ${fmt(2, abs(Acoustics.cents(1.5) - 700))} " +
            "cents flat of a tuner, and repeating it string by string lets the error add up. A tuner, or matching a fretted " +
            "note against the next open string, avoids it.",
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
        "Differences smaller than a half step are measured in cents: 100 cents to the equal-tempered half step, 1200 to " +
            "the octave. The cents between two frequencies are 1200 × log₂(f₂ ÷ f₁). Cents follow the ear rather than the " +
            "hertz: 10 cents sharp is the same amount of sharp at 80 Hz as at 800 Hz, though the gap in hertz is ten times " +
            "wider. An A tuned to 446 Hz is ${fmt(1, Acoustics.cents(446.0 / 440))} cents sharp of 440. With steady " +
            "tones one after the other, trained listeners can often tell differences of around 5 cents; a tuner's needle " +
            "shows them directly.",
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
        "Stack twelve pure fifths (3:2) and you land ${fmt(2, Acoustics.pythagoreanComma)} cents above seven " +
            "octaves: the circle doesn't close. That leftover is the Pythagorean comma, and every tuning system is a way " +
            "of sharing it out. Equal temperament shares it evenly. Every half step is the same ratio, the twelfth root " +
            "of 2 (about ${fmt(5, 2.0.pow(1.0 / 12))}), so every key sounds alike and you can modulate anywhere. The " +
            "price is that only the octave is pure. The equal fifth is ${fmt(2, Acoustics.cents(1.5) - 700)} cents " +
            "narrow of 3:2, too little to notice; the equal major third is ${fmt(2, 400 - Acoustics.cents(1.25))} " +
            "cents wide of 5:4, which you can hear as a shimmer when a held third beats. Choirs, string quartets and " +
            "barbershop singers often bend toward the pure ratios; a piano can't.",
        see = CourseSee.Table("EQUAL AGAINST PURE", listOf("", "EQUAL ¢", "PURE", "PURE ¢", "EQUAL IS"), PURE.map { r ->
            val pure = Acoustics.cents(r[3] as Double)
            listOf(r[0] as String, "${(r[1] as Int) * 100}", r[2] as String, fmt(2, pure), centsText((r[1] as Int) * 100 - pure))
        }),
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
        "Which frequency counts as A is an agreement, not a law of nature. A = 440 Hz is the international standard " +
            "(ISO 16, 1975) and the default on tuners and in DAWs. Many orchestras tune a little higher, around 442 Hz. " +
            "Baroque ensembles commonly use 415 Hz, ${fmt(0, abs(Acoustics.cents(415.0 / 440)))} cents lower, very " +
            "nearly a half step, so their A sounds where a modern A♭ does. Some players prefer A = 432 Hz: that's " +
            "${fmt(2, abs(Acoustics.cents(432.0 / 440)))} cents flat of 440, about a third of a half step, with no " +
            "special acoustic property. What matters is that everyone playing together agrees, and that a recording you " +
            "play along to may not be at 440.",
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
