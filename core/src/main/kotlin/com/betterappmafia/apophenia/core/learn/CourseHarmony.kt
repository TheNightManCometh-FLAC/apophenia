package com.betterappmafia.apophenia.core.learn

import com.betterappmafia.apophenia.core.catalog.KeyMode
import com.betterappmafia.apophenia.core.catalog.Scales
import com.betterappmafia.apophenia.core.chord.ChordDisplay
import com.betterappmafia.apophenia.core.chord.ChordStyle
import com.betterappmafia.apophenia.core.degree.DegreeToken
import com.betterappmafia.apophenia.core.interval.plus
import com.betterappmafia.apophenia.core.learn.CourseIntervals.shown
import com.betterappmafia.apophenia.core.pitch.PitchClass
import com.betterappmafia.apophenia.core.pitch.PitchName
import com.betterappmafia.apophenia.core.sound.PlayNote

/*
 * Course · Harmony: the chords of a key and Roman numerals, function, cadences, secondary dominants, borrowed chords,
 * modulation, the Neapolitan and augmented sixths, jazz harmony (ii–V–I, guide tones, tritone subs, chord-scales), and
 * beyond the key (Coltrane changes, negative harmony, neo-Riemannian operations, pitch-class set theory). Text written
 * for Apophenia; progressions are spelled from degree tokens and voiced by EarHarmony.voice.
 */
internal object CourseHarmony : CourseKit() {
    private val C = PitchName.parse("C")
    private fun p(s: String) = PitchName.parse(s)

    /** A chord as degree tokens above the tonic; "b6|b6 1 b3 #4" puts the first part in the bass. */
    internal fun ch(t: String): EarHarmony.HChord {
        val (bass, tones) = if ("|" in t) t.substringBefore("|") to t.substringAfter("|") else t.substringBefore(" ") to t
        return EarHarmony.HChord(DegreeToken.list("$bass $tones"))
    }
    /** A progression on [tonic] (MIDI), each chord [step] seconds. */
    internal fun prog(tonic: Int, chords: List<String>, step: Double = 1.0, at: Double = 0.0): List<PlayNote> =
        chords.flatMapIndexed { i, c -> EarHarmony.voice(ch(c), tonic).map { PlayNote(at + i * step, at + i * step + step * 0.92, it, RefSound.VELOCITY) } }
    private fun play(tonic: Int, vararg chords: String, step: Double = 1.0) = piano(prog(tonic, chords.toList(), step))
    /** A chord's notes spelled on [key]'s tonic. */
    internal fun spell(t: String, key: PitchName = C) = ch(t).tokens.drop(1).distinct().joinToString(" ") { shown(key + it.interval) }
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
    private val NUM_HEADER = listOf("", "CHORD", "NOTES")

    private val majorKey = CoursePage(
        "numerals-major", "Chords of a major key", 2,
        "Build a triad on every degree of a major scale, using only the scale's notes, and you get the key's seven chords. " +
            "Their qualities always fall the same way: major on 1, 4 and 5, minor on 2, 3 and 6, diminished on 7. Roman " +
            "numerals name them by degree, so one name works in every key: upper case for major (I, IV, V), lower case for " +
            "minor (ii, iii, vi), a small circle for diminished (vii°). In C that's C, Dm, Em, F, G, Am, B°; in G it's G, " +
            "Am, Bm, C, D, Em, F♯°. Add a seventh from the scale and the qualities become Imaj7, ii7, iii7, IVmaj7, V7, vi7 " +
            "and viiø7: only V gets a dominant seventh. This is what lets musicians say \"one, four, five\" or \"a two–five\" " +
            "about any key.",
        see = CourseSee.Table("C MAJOR · TRIADS, THEN SEVENTHS", NUM_HEADER, diatonic("major", false) + diatonic("major", true)),
        hear = listOf(
            sound("I ii iii IV V vi vii° I") { r -> play(r, "1 3 5", "2 4 6", "3 5 7", "4 6 1", "5 7 2", "6 1 3", "7 2 4", "1 3 5", step = 0.9) },
            sound("THE SEVENTHS") { r -> play(r, "1 3 5 7", "2 4 6 1", "3 5 7 2", "4 6 1 3", "5 7 2 4", "6 1 3 5", "7 2 4 6", "1 3 5 7", step = 1.1) },
        ),
        tryIt = CourseTry("THE KEY'S SCALE · ITS CHORDS ARE ON THE SCALE'S PAGE", RefKind.SCALE, "major", "C"),
        check = CourseCheck("After the cadence, one chord: I, IV, V or vi?", listOf("I", "IV", "V", "vi").map { n ->
            option(n, "$n: ${mapOf("I" to "the tonic", "IV" to "the subdominant", "V" to "the dominant", "vi" to "the relative minor").getValue(n)}.") { r, _ -> ear("hm-major", n, r) }
        }),
        related = listOf(ref(RefKind.SCALE, "major", "C major"), ear("hm-major", "chords in a major key")),
    )

    private val minorKey = CoursePage(
        "numerals-minor", "Chords of a minor key", 2,
        "Natural minor's triads are i, ii°, III, iv, v, VI and VII: in A minor, Am, B°, C, Dm, Em, F and G, the same chords " +
            "as C major from a different home. In practice minor keys borrow the raised 7th of harmonic minor for their " +
            "dominant, so the v becomes V (E major, with G♯) and the VII can become vii° (G♯°): that's what gives a minor key " +
            "its strong V–i. Pop and rock often keep the natural v and VII, part of their modal, less classical sound: " +
            "Am–G–F–G against a classical Am–Dm–E–Am. The numerals follow the same rules, lower case for minor, so i is the minor " +
            "tonic and III the major chord on the ♭3.",
        see = CourseSee.Table("C NATURAL MINOR, THEN HARMONIC MINOR", NUM_HEADER, diatonic("aeolian", false) + diatonic("harmonic-minor", false)),
        hear = listOf(
            sound("i iv v i · NATURAL") { r -> play(r, "1 b3 5", "4 b6 1", "5 b7 2", "1 b3 5") },
            sound("i iv V i · THE RAISED 7TH") { r -> play(r, "1 b3 5", "4 b6 1", "5 7 2", "1 b3 5") },
        ),
        tryIt = CourseTry("HARMONIC MINOR · WHERE THE MAJOR V COMES FROM", RefKind.SCALE, "harmonic-minor", "A"),
        check = CourseCheck("Into the minor tonic: from a minor v, or a major V?", listOf(
            option("v", "The minor v: natural minor, softer.") { r, _ -> play(r, "1 b3 5", "4 b6 1", "5 b7 2", "1 b3 5") },
            option("V", "The major V: the raised 7th leads home.") { r, _ -> play(r, "1 b3 5", "4 b6 1", "5 7 2", "1 b3 5") },
        )),
        related = listOf(ear("hm-minor", "chords in a minor key"), page("minor-harmonic", "Harmonic minor")),
    )

    // ------------------------------------------------------------------------------------------------- function

    private val function = CoursePage(
        "function", "Tonic, predominant, dominant", 2,
        "The chords of a key sort into three jobs. Tonic chords are home: I, and vi and sometimes iii, which share two notes " +
            "with it. Dominant chords pull home: V and vii°, both holding the leading tone and, as sevenths, the tritone. " +
            "Predominant chords lead to the dominant: IV and ii, which share two notes with each other. Much tonal music " +
            "moves tonic → predominant → dominant → tonic, as in I–IV–V–I or I–vi–ii–V–I, and a chord can be swapped for " +
            "another with the same job: vi for I (the deceptive move), ii for IV. Function explains why a progression " +
            "feels like it's going somewhere, and why the same three-chord loop can sound settled in one order and " +
            "restless in another.",
        see = CourseSee.Table("FUNCTIONS IN C", listOf("", "CHORDS", "IN C"), listOf(
            listOf("Tonic", "I vi (iii)", "C Am (Em)"), listOf("Predominant", "IV ii", "F Dm"), listOf("Dominant", "V vii°", "G B°"),
        )),
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
        "A cadence is how a phrase ends, and four kinds cover most music. The authentic cadence goes V to I, the strongest " +
            "close; when both chords are in root position and the melody ends on the tonic it's a perfect authentic cadence. " +
            "The plagal cadence goes IV to I, softer, the \"amen\" at the end of a hymn. The half cadence stops on V, a " +
            "comma rather than a full stop, asking for more. The deceptive cadence sets up V to I and goes to vi instead, " +
            "a surprise that keeps the music going. Listen for where the phrase lands: home, away, or somewhere unexpected.",
        hear = EarHarmony.CADENCES.map { c -> sound(c.uppercase()) { r -> ear("hm-cadence", c, r) } },
        check = CourseCheck("How does the phrase end?", EarHarmony.CADENCES.map { c ->
            option(c.replaceFirstChar(Char::uppercase), mapOf("authentic" to "Authentic: V to I.", "plagal" to "Plagal: IV to I.", "half" to "Half: it stops on V.", "deceptive" to "Deceptive: V to vi.").getValue(c)) { r, _ -> ear("hm-cadence", c, r) }
        }),
        related = listOf(ear("hm-cadence", "cadences"), page("function", "Function")),
    )

    // ------------------------------------------------------------------------------------------------- secondary

    private val secondary = CoursePage(
        "secondary", "Secondary dominants", 3,
        "Any major or minor chord of a key can borrow a dominant of its own. D7 isn't in C major, but it's the V7 of G, so " +
            "in C it's written V7/V, \"five of five\", and it pushes toward G harder than any diatonic chord could. The " +
            "borrowed note is the new chord's leading tone: D7's F♯ leads up to G. V7/IV is C7, whose B♭ leads down to F's " +
            "A; V7/vi is E7, whose G♯ leads up to A. Only vii°, the diminished chord, isn't tonicised, since a diminished " +
            "chord can't act as a home. Secondary dominants are everywhere from Bach to doo-wop, and they're how a song can " +
            "colour a key without leaving it.",
        see = CourseSee.Table("SECONDARY DOMINANTS IN C", listOf("", "CHORD", "NOTES", "LEADS TO"), listOf(
            listOf("V7/ii", "A7", spell("6 #1 3 5"), "Dm"), listOf("V7/iii", "B7", spell("7 #2 #4 6"), "Em"), listOf("V7/IV", "C7", spell("1 3 5 b7"), "F"),
            listOf("V7/V", "D7", spell("2 #4 6 1"), "G"), listOf("V7/vi", "E7", spell("3 #5 7 2"), "Am"),
        )),
        hear = listOf("V7/V", "V7/vi", "V7/IV").map { s -> sound("I · $s · ITS CHORD") { r -> ear("hm-secondary", s, r) } },
        check = CourseCheck("Which chord does the dominant point at?", listOf("V7/ii" to "ii", "V7/IV" to "IV", "V7/V" to "V", "V7/vi" to "vi").map { (s, t) ->
            option(t, "$s: the dominant of $t.") { r, _ -> ear("hm-secondary", s, r) }
        }),
        related = listOf(ear("hm-secondary", "secondary dominants"), ref(RefKind.CHORD, "7", "D7", "D")),
    )

    private val chains = CoursePage(
        "secondary-chains", "Chains of dominants", 3,
        "Secondary dominants can lead into each other. E7 is the V of A, A7 the V of D, D7 the V of G, G7 the V of C: E7–" +
            "A7–D7–G7–C walks round the circle of fifths, each chord resolving into the next, which is also a dominant. " +
            "Ragtime, early jazz and a lot of standards use these chains, and the bridge of rhythm changes is a short one " +
            "(III7–VI7–II7–V7). Each 3rd falls a half step to become the next chord's 7th, and each 7th falls a half step " +
            "to the next 3rd, so the inner voices slide down chromatically while the roots jump by fifths. Replacing each " +
            "dominant with its ii–V gives the familiar backcycling of jazz.",
        see = CourseSee.Table("E7 TO C", listOf("", "NOTES", "3RD · 7TH"), listOf("E" to "3 #5 7 2", "A" to "6 #1 3 5", "D" to "2 #4 6 1", "G" to "5 7 2 4").map { (r, t) ->
            val n = ch(t).tokens.drop(1)
            listOf("${shown(p(r))}7", spell(t), shown(C + n[1].interval) + " " + shown(C + n[3].interval))
        }),
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
        "A major key can borrow chords from its parallel minor, the minor on the same tonic. In C that adds Fm (iv), E♭ " +
            "(♭III), A♭ (♭VI), B♭ (♭VII) and D° (ii°). The minor iv after IV (F then Fm) is a classic bittersweet turn; " +
            "♭VI–♭VII–I (A♭–B♭–C) is a rock and film-score cadence that arrives without any dominant; ♭VII on its own " +
            "gives a Mixolydian lean. This is modal mixture, or modal interchange: a key keeps its home but takes its " +
            "colours from the other mode. It works the other way too: a piece in minor that ends on a major tonic chord has " +
            "a Picardy third.",
        see = CourseSee.Table("BORROWED INTO C", listOf("", "CHORD", "NOTES"), listOf(
            listOf("iv", "Fm", spell("4 b6 1")), listOf("♭III", "E♭", spell("b3 5 b7")), listOf("♭VI", "A♭", spell("b6 1 b3")),
            listOf("♭VII", "B♭", spell("b7 2 4")), listOf("ii°", "D°", spell("2 4 b6")),
        )),
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
        "To modulate is to move the home to another key and stay there. The smoothest way uses a pivot chord, one that " +
            "belongs to both keys. From C to G, Am is vi in C and ii in G: play I–vi in C, treat the Am as ii of G, and go " +
            "on to D7 and G. The new key is confirmed by its own dominant and a cadence. Keys that share many chords, the " +
            "neighbours on the circle of fifths, have the most pivots; distant keys may have none. A brief move that " +
            "returns at once is a tonicisation rather than a modulation, the difference being how long the new home holds.",
        see = CourseSee.Table("C → G THROUGH Am", listOf("", "IN C", "IN G"), listOf(
            listOf("C", "I", "IV"), listOf("Am", "vi", "ii · the pivot"), listOf("D7", "—", "V7"), listOf("G", "V", "I"),
        )),
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
        "Not every key change prepares itself. A direct modulation just starts again in the new key, usually at the start " +
            "of a section: the last chorus a half step or a whole step higher, a lift pop has used for decades. A chromatic " +
            "mediant is a jump between major (or minor) chords a third apart that share only one note: C to E, C to A♭, C to " +
            "E♭ or A. It sounds like a change of light rather than a move along the key, and it's a staple of film music. " +
            "Each of those pairs shares one note (C and E share E; C and A♭ share C), and the other two notes move by half or " +
            "whole steps.",
        see = CourseSee.Table("CHROMATIC MEDIANTS OF C", listOf("", "NOTES", "SHARED WITH C"), listOf("E", "Ab", "Eb", "A").map { k ->
            val notes = ScalePages.page(Scales.byId("major"), p(k)).notes.let { listOf(it[0], it[2], it[4]) }
            listOf(shown(p(k)), notes.joinToString(" ") { shown(it) }, notes.filter { it.pitchClass in setOf(0, 4, 7) }.joinToString(" ") { shown(it) })
        }),
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
        "The Neapolitan is a major chord on the lowered 2nd degree, ♭II, used as a predominant, most often in minor. In C " +
            "minor it's D♭ F A♭, and it usually appears in first inversion with F in the bass, which is why it's written N⁶. " +
            "It takes the place of iv or ii°: F is in the bass either way, but the D♭ on top gives a dark, sudden colour " +
            "before the D♭ falls to the leading tone B (a diminished third, D♭ to B) as the chord moves to V. The name " +
            "comes from its use by 18th-century composers of the Neapolitan school; Baroque and Classical music use it " +
            "mostly in minor keys before a cadence.",
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
        "An augmented-sixth chord has the ♭6 in the bass and the ♯4 above, an augmented sixth apart (A♭ and F♯ in C), and " +
            "that interval expands outward by half steps to the octave on the 5th degree, G. It's a predominant leading to " +
            "V. Three kinds are named, by tradition after countries: the Italian adds just the tonic (A♭ C F♯), the French " +
            "adds the tonic and the 2nd (A♭ C D F♯), the German the tonic and the ♭3 (A♭ C E♭ F♯). The German sounds " +
            "exactly like an A♭7 chord, but it's spelled with F♯, not G♭, because the F♯ rises to G; that same sound is " +
            "the tritone substitute for D7, the V7 of V, which is why the two ideas meet in jazz.",
        see = CourseSee.Table("IN C", listOf("", "SPELLED", "BASS"), listOf("It⁺⁶" to "b6|b6 1 #4", "Fr⁺⁶" to "b6|b6 1 2 #4", "Ger⁺⁶" to "b6|b6 1 b3 #4").map { (n, t) ->
            listOf(n, spell(t), shown(C + ch(t).tokens.first().interval))
        }),
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
        "The ii–V–I is the basic sentence of jazz harmony: in C, Dm7–G7–Cmaj7, predominant, dominant, tonic. Standards " +
            "are largely chains of them, in the home key and in passing keys, and recognising them on a lead sheet is most " +
            "of reading jazz changes; in minor it's iiø7–V7–i (Dø7–G7–Cm). Each chord's 3rd and 7th, its guide tones, say " +
            "what it is, and through a ii–V–I they barely move: the 7th of Dm7 (C) falls a half step to the 3rd of G7 (B), " +
            "which stays as the 7th of Cmaj7; the 3rd of Dm7 (F) stays as the 7th of G7 and falls to the 3rd of Cmaj7 (E). " +
            "Two-note shells of guide tones, with the root in the bass, are the core of jazz piano and guitar comping.",
        see = CourseSee.Table("GUIDE TONES IN C", listOf("", "3RD", "7TH"), listOf(
            listOf("Dm7", shown(C + DegreeToken.parse("4").interval), shown(C + DegreeToken.parse("1").interval)),
            listOf("G7", shown(C + DegreeToken.parse("7").interval), shown(C + DegreeToken.parse("4").interval)),
            listOf("Cmaj7", shown(C + DegreeToken.parse("3").interval), shown(C + DegreeToken.parse("7").interval)),
        )),
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
        "A dominant seventh can be replaced by the dominant a tritone away: D♭7 for G7, since both hold the same 3rd and 7th " +
            "(F and B, or C♭). Dm7–D♭7–Cmaj7 gives a bass that slides down by half steps. The backdoor dominant, B♭7 to C, " +
            "comes from the parallel minor's ♭VII and resolves by a whole step. Chord-scale theory pairs each chord with a " +
            "scale for improvising and voicing: Dm7 with D Dorian, G7 with G Mixolydian, Cmaj7 with C Ionian (or Lydian, " +
            "avoiding the clash of the natural 4th over the 3rd). An altered G7 going to a minor chord takes the altered " +
            "scale; a G7♭9 the half-whole diminished. The scales come from the key and from the chord's tensions; they're " +
            "a map of choices, not a rule.",
        see = CourseSee.Table("CHORD-SCALES IN C", listOf("", "SCALE", "NOTES"), listOf(
            Triple("Dm7", "dorian", "D"), Triple("G7", "mixolydian", "G"), Triple("Cmaj7", "major", "C"), Triple("G7alt", "altered", "G"), Triple("G7♭9", "half-whole", "G"),
        ).map { (c, s, r) -> listOf(c, accidentals(Scales.byId(s).names.first()), CourseScales.notesOn(s, r)) }),
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
        "In the late 1950s John Coltrane built progressions on keys a major third apart, which split the octave into three " +
            "equal parts: B, G and E♭ in \"Giant Steps\", each tonic reached through its own dominant seventh. Applied to a " +
            "ii–V–I in C, the same idea fills the bar before Cmaj7 with a tour of all three keys: Dm7, E♭7, A♭maj7, B7, " +
            "Emaj7, G7, Cmaj7, with tonics C, A♭ and E. Each dominant is a fifth above its tonic, so the roots alternate " +
            "fifths and thirds. At a fast tempo it's one of the hardest sets of changes to improvise over, and it became a " +
            "standard test for jazz players.",
        see = CourseSee.Table("A ii–V–I IN C, COLTRANE'S WAY", listOf("", "CHORD", "TONIC OF"), listOf(
            listOf("1", "Dm7", "—"), listOf("2", "E♭7", "A♭"), listOf("3", "A♭maj7", "A♭"), listOf("4", "B7", "E"),
            listOf("5", "Emaj7", "E"), listOf("6", "G7", "C"), listOf("7", "Cmaj7", "C"),
        )),
        hear = listOf(
            sound("Dm7 · G7 · Cmaj7") { r -> play(r, "2 4 6 1", "5 7 2 4", "1 3 5 7", step = 1.2) },
            sound("THE SAME, COLTRANE'S WAY") { r -> play(r, "2 4 6 1", "b3 5 b7 b2", "b6 1 b3 5", "7 #2 #4 6", "3 #5 7 #2", "5 7 2 4", "1 3 5 7", step = 0.9) },
        ),
        related = listOf(page("jazz-251", "ii–V–I"), page("triad-four", "The augmented triad")),
    )

    /** Negative harmony in C: pitch class n becomes 7 − n (the mirror between E♭ and E, the axis of C and G). */
    internal fun negative(pc: Int) = Math.floorMod(7 - pc, 12)

    private val negativeHarmony = CoursePage(
        "negative-harmony", "Negative harmony", 5,
        "Reflect every note of a key around the point halfway between its ♭3 and 3 (between E♭ and E in C) and major turns " +
            "into minor: C E G becomes G E♭ C, a C minor chord. In C each pitch class n maps to 7 − n: C and G swap, D and " +
            "F, E and E♭, A and B♭, B and A♭, D♭ and F♯. A dominant G7 (G B D F) becomes C A♭ F D, which is Fm6, or Dø7, " +
            "the same notes: a minor iv with an added 6th standing in for V and still resolving to C, the \"negative\" " +
            "dominant. The idea of harmonic polarity goes back to Hugo Riemann and was worked out by Ernst Levy in A " +
            "Theory of Harmony; Levy never used the phrase \"negative harmony\", which Jacob Collier popularised. It's a " +
            "way to find new chords, not a description of how tonal music works.",
        see = CourseSee.Table("THE MIRROR IN C", listOf("", "NOTES", "BECOMES", "NAMED"), listOf(
            Triple("C", listOf(0, 4, 7), "Cm"), Triple("G7", listOf(7, 11, 2, 5), "Fm6 · Dø7"), Triple("F", listOf(5, 9, 0), "Gm"), Triple("Am", listOf(9, 0, 4), "E♭"),
        ).map { (n, pcs, named) ->
            fun names(l: List<Int>) = l.joinToString(" ") { pc -> shown(if (pc == 3) p("Eb") else if (pc == 8) p("Ab") else if (pc == 10) p("Bb") else PitchClass(pc).defaultSpelling()) }
            listOf(n, names(pcs), names(pcs.map(::negative)), named)
        }),
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

    private val neoRiemannian = CoursePage(
        "neo-riemannian", "Neo-Riemannian moves", 5,
        "Neo-Riemannian theory describes moves between major and minor triads that keep two notes and shift the third by " +
            "a step. P (parallel) moves the third: C major ↔ C minor. R (relative) moves the fifth of a major chord up a whole " +
            "step: C major ↔ A minor. L (leading-tone exchange) moves the root of a major chord down a half step: C major ↔ " +
            "E minor. Each is its own undo, and combinations reach further with very little voice movement: PL takes C to " +
            "A♭, a chromatic mediant in two smooth steps. Late-Romantic composers and film scores use such chains to " +
            "drift between distant chords without any key holding them together, which is what the theory was built to " +
            "explain.",
        see = CourseSee.Table("FROM C MAJOR", listOf("MOVE", "TO", "KEPT"), listOf("P" to P(Triad(0, true)), "R" to R(Triad(0, true)), "L" to L(Triad(0, true)), "PL" to L(P(Triad(0, true))), "LP" to P(L(Triad(0, true)))).map { (n, t) ->
            val name = shown(if (t.root == 8) p("Ab") else if (t.root == 3) p("Eb") else PitchClass(t.root).defaultSpelling()) + if (t.major) "" else "m"
            listOf(n, name, "${Triad(0, true).pcs.intersect(t.pcs).size} notes")
        }),
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

    private val sets = CoursePage(
        "set-theory", "Pitch-class sets", 5,
        "Post-tonal theory treats a chord or a motif as a set of pitch classes, numbered 0 to 11 from C, with spelling and " +
            "octave set aside. Two sets are the same kind if one is a transposition or an inversion of the other, and each " +
            "kind gets a prime form, its most compact arrangement starting on 0. The major and minor triads are inversions " +
            "of each other, so both have the prime form (037), which Allen Forte numbered 3-11. The interval vector counts " +
            "how many of each interval class a set holds, from half step to tritone: a triad's is 001110, one minor third, " +
            "one major third, one fourth (or fifth). Sets with the same vector share a sound even when they aren't related " +
            "by transposition or inversion. The tools work on any music, which is why they're used for Schoenberg and " +
            "Bartók and for analysing jazz voicings.",
        see = CourseSee.Table("PRIME FORMS AND VECTORS", listOf("", "PCS ON C", "PRIME", "VECTOR"), listOf(
            "Major triad" to setOf(0, 4, 7), "Minor triad" to setOf(0, 3, 7), "Diminished" to setOf(0, 3, 6), "Augmented" to setOf(0, 4, 8),
            "Dominant 7th" to setOf(0, 4, 7, 10), "Half-diminished" to setOf(0, 3, 6, 10), "Major 7th" to setOf(0, 4, 7, 11), "Minor 7th" to setOf(0, 3, 7, 10),
            "Diminished 7th" to setOf(0, 3, 6, 9), "Whole-tone" to setOf(0, 2, 4, 6, 8, 10),
        ).map { (n, s) ->
            fun t(x: Int) = when (x) { 10 -> "T"; 11 -> "E"; else -> "$x" }
            listOf(n, s.sorted().joinToString(" ") { t(it) }, "(" + prime(s).joinToString("") { t(it) } + ")", vector(s).joinToString(""))
        }),
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
