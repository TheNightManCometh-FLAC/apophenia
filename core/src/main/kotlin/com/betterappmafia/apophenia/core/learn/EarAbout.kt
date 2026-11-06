package com.betterappmafia.apophenia.core.learn

/**
 * What each Ear drill is (Christopher, 31 Oct 2026: "a description of what each ear training is"): [what] says what
 * you'll hear and what you answer, [listen] how to tell the answers apart. Focused interval drills (melodic or harmonic)
 * use their base drill's text plus a line on the presentation; mixed and hand-picked sets describe themselves.
 */
object EarAbout {
    data class About(val what: String, val listen: String)

    fun of(drill: EarDrill): About {
        if (drill.id.startsWith("focus:")) {
            val base = Ear.drill(drill.id.substringAfterLast(':'))?.let { of(it) } ?: GENERIC
            val how = when (drill.direction) {
                EarDirection.TOGETHER -> "Harmonic: both notes sound at once."
                EarDirection.UP -> "Melodic: the lower note first, then the upper."
                EarDirection.DOWN -> "Melodic: the upper note first, then the lower."
                EarDirection.MELODIC -> "Melodic: one note, then the other, going up or down at random."
                else -> "Every presentation, mixed: up, down and together."
            }
            return About("$how ${base.what}", base.listen)
        }
        if (drill.id.startsWith("mixed")) return About(
            "Questions drawn at random from every drill in ${if (drill.chapter == "Mixed") "every chapter" else "the ${drill.chapter} chapter"}, " +
                "so you can't lean on knowing which drill you're in. The answers offered are the ones of the drill each question comes from.",
            "Treat each question fresh: first decide what kind of thing you're hearing (an interval, a chord, a phrase), then which one.")
        if (drill.id.startsWith("pick:")) return About(
            "Only the answers you picked, mixed together. Use it to drill the two or three things you keep confusing.",
            "Play the answers you confuse side by side first (after an answer, tap the others to hear them on the same root).")
        return ABOUT[drill.id] ?: GENERIC
    }

    private val GENERIC = About("Hear a short example, then pick what it was from the answers below.", "After you answer, tap the other answers to hear them on the same note and compare.")

    private fun a(what: String, listen: String) = About(what, listen)

    private val ABOUT: Map<String, About> = mapOf(
        // ------------------------------------------------------------------------------------------------ intervals
        "iv-seconds" to a("Two notes a second apart: either a minor second (1 half step, like E–F) or a major second (2 half steps, like C–D). Pick which.",
            "A minor second rubs and sounds tight; together it's a sharp clash. A major second is the first two notes of a scale, \"do–re\", and together it buzzes gently rather than clashes."),
        "iv-thirds" to a("Two notes a third apart: minor (3 half steps) or major (4). Pick which.",
            "The major third is the bottom of a major chord, bright and open. The minor third is the bottom of a minor chord, darker. Sing a major chord's first two notes against what you hear."),
        "iv-perfect" to a("A perfect fourth (5 half steps) or a perfect fifth (7). Pick which.",
            "The fifth is stable and hollow, like a power chord; the fourth sounds like it wants to fall or settle (\"Here Comes the Bride\" starts with one). Upside down, a fourth becomes a fifth: if you hear the lower note as home, it's a fifth."),
        "iv-sixths" to a("A minor sixth (8 half steps) or a major sixth (9). Pick which.",
            "The major sixth is sweet and wide (the start of \"My Bonnie\"). The minor sixth is darker and leans back down a half step. Flip them in your head: a minor sixth is a major third upside down."),
        "iv-sevenths" to a("A minor seventh (10 half steps) or a major seventh (11). Pick which.",
            "The major seventh is a half step short of the octave and pulls hard toward it. The minor seventh is a whole step short, the bluesy 7th of a dominant chord, and feels relaxed by comparison."),
        "iv-unison-octave" to a("The same note twice (unison), or a note and the one 12 half steps above (octave). Pick which.",
            "An octave sounds like the same note in a different register: the two blend almost into one, but you can hear the height change. A unison doesn't move at all."),
        "iv-2-3" to a("Any second or third: minor second, major second, minor third or major third. Pick which.",
            "First decide step (second) or skip (third): a second sounds like moving to the next scale note, a third skips one. Then decide bright or dark."),
        "iv-4-5" to a("A perfect fourth, the tritone (6 half steps, A4) or a perfect fifth. Pick which.",
            "The tritone is the odd one out: unstable, neither open nor sweet, and it wants to resolve. Between fourth and fifth, the fifth is the more settled."),
        "iv-6-7" to a("Minor or major sixth, minor or major seventh: the four intervals most often confused. Pick which.",
            "Measure from the octave: a major seventh is a half step short of it, a minor seventh a whole step short, a major sixth a step and a half short. Sixths sound consonant; sevenths sound like they need to move."),
        "iv-simple" to a("Any interval from unison to octave. Pick which.",
            "Narrow it down in stages: is it small (seconds, thirds), middle (fourth, tritone, fifth) or large (sixths, sevenths, octave)? Then use the pair tricks from the smaller drills."),
        "iv-compound" to a("Intervals wider than an octave, from a minor ninth to two octaves. Pick which.",
            "Collapse the upper note down an octave in your head and name the simple interval (a tenth is a third plus an octave). Then add 7 to the number: a third becomes a tenth, a sixth a thirteenth."),
        "iv-sing" to a("You hear one note and the name of an interval; sing the note that interval above it. The microphone checks your pitch.",
            "Hear the target in your head before you sing: use a song that starts with that interval, or walk up the scale silently to it. Sing on \"ah\" and hold it steady."),
        // ------------------------------------------------------------------------------------------------ chords
        "ch-triads" to a("One three-note chord: major, minor, diminished or augmented. Pick which.",
            "Major is bright and stable; minor darker but still stable. Diminished sounds tense and small (both thirds minor); augmented sounds unresolved and dreamy (both thirds major). Use SOUND ▾ to hear them broken up."),
        "ch-sus" to a("A major chord, or a sus2 or sus4 chord, where the 3rd is replaced by the 2nd or the 4th. Pick which.",
            "Major has a 3rd and sounds complete. Sus4 sounds like it wants to fall back to the 3rd. Sus2 sounds open and airy, with a close rub between the root and 2nd at the bottom."),
        "ch-7-basic" to a("A major seventh, minor seventh or dominant seventh chord. Pick which.",
            "Major 7th: dreamy, with a soft clash between the 7th and the octave. Minor 7th: mellow and dark. Dominant 7th: bluesy and restless, it sounds like it wants to go somewhere."),
        "ch-7-all" to a("Any of eight seventh chords: maj7, 7, m7, minor-major 7, half-diminished, diminished 7, 7♯5 and maj7♯5. Pick which.",
            "First hear the triad underneath (major, minor, diminished, augmented), then the 7th: a half step under the octave (major 7th), a whole step under (minor 7th), or a step and a half under (diminished 7th)."),
        "ch-6-add" to a("A sixth chord (6, m6, 6/9) or a chord with an added note (add9, m add9). Pick which.",
            "6 chords are sweet and settled, with no 7th. Add9 chords sparkle: the 9th sits a step above the root, close to the 3rd. 6/9 has both the 6th and the 9th and sounds wide and jazzy."),
        "ch-ext" to a("Ninth, eleventh and thirteenth chords, and altered dominants (♭9, ♯9, alt). Pick which.",
            "Hear the 7th chord first (dominant, major or minor), then listen to the top: a 9th is a step above the root, a 13th a step above the 5th. ♭9 and ♯9 make a dominant sound darker and grittier; alt is the darkest of all."),
        "ch-inv" to a("A major or minor triad with its root, 3rd or 5th in the bass. Pick which inversion.",
            "Listen only to the lowest note. Root position sounds grounded; first inversion (3rd in the bass) lighter and less stable; second inversion (5th in the bass) unsettled, like it wants to move."),
        "ch-inv7" to a("A dominant or minor seventh chord with its root, 3rd, 5th or 7th in the bass. Pick which.",
            "Sing the bass note, then find it in the chord. The 7th in the bass (third inversion) is the most unstable and wants to fall a step."),
        "ch-voicing" to a("A seventh chord voiced four ways, root in the bass: close, drop 2, drop 3, or spread. Pick which.",
            "Close voicing is tight, all four notes within an octave. Drop 2 opens it a little; drop 3 leaves a wide gap between the bass and the rest; spread sounds wide and orchestral throughout."),
        // ------------------------------------------------------------------------------------------------ pitch
        "pitch-ref" to a("You hear an A (440 Hz) first, then another note. Name the second note.",
            "Use the A as a ruler: count the interval from it, or sing up or down the scale from A to the note."),
        "pitch-abs" to a("One note with no reference note before it. Name the note from memory alone.",
            "This is absolute pitch, which few people have; practise it to strengthen your memory for a few anchor notes (your lowest string, a tuning A) rather than expecting to get them all."),
        "pitch-octave" to a("One note somewhere from C2 to B6. Name its octave (C4 is middle C).",
            "Compare it with your own voice and instruments: a guitar's low E is E2, middle C sits in the middle of most voices, and the top of a guitar neck is around C6."),
        "pitch-cents-25" to a("Two notes, the second slightly higher or lower than the first (25 cents, a quarter of a half step). Pick up or down.",
            "Hold the first note in your head and feel which way the second leans. At 25 cents the difference is clear once you're listening for it."),
        "pitch-cents-10" to a("Two notes, the second 10 cents higher or lower. Pick up or down.",
            "Ten cents is about as close as a careful player tunes by ear. Listen to the very start of the second note."),
        "pitch-cents-5" to a("Two notes, the second 5 cents higher or lower. Pick up or down.",
            "Five cents is near the limit of most people's hearing on separate notes. Don't strain; trust your first impression."),
        "pitch-match" to a("You hear a note; sing or play it back. The tuner grades how close you got.",
            "Hum it quietly first, then sing it out and hold it. Watch the needle settle; aim for steady rather than perfect."),
        // ------------------------------------------------------------------------------------------------ scale degrees
        "deg-major" to a("A short cadence sets a major key (or a drone holds the tonic, under SOUND ▾), then one note plays. Name its scale degree, 1 to 7.",
            "Hear every note in relation to home. 1 is rest; 5 is stable but open; 3 is sweet; 7 pulls up to 1; 4 leans down to 3; 2 and 6 sound unfinished. Sing from the note down or up to 1 and count the steps."),
        "deg-minor" to a("A cadence sets a minor key, then one note plays. Name its degree in natural minor (with ♭3, ♭6, ♭7), or the raised 7.",
            "♭3 is the sound of minor; ♭6 leans heavily down to 5; ♭7 is a whole step under the tonic and doesn't pull; the raised 7 is a half step under and pulls hard."),
        "deg-chromatic" to a("A cadence sets a major key, then any of the twelve notes plays. Name its degree, including the notes outside the key.",
            "Find the nearest scale degree first, then decide whether the note is that degree or a half step off it. ♯4 pulls up to 5, ♭2 down to 1, ♭7 sounds bluesy, ♭3 sounds minor."),
        // ------------------------------------------------------------------------------------------------ modes
        "mode-major" to a("A low drone holds the tonic while a scale runs up and down over it. Name the mode: Ionian (major), Dorian, Phrygian, Lydian, Mixolydian, Aeolian or Locrian.",
            "First major or minor (listen for the 3rd). Major: Lydian has a raised 4th (dreamy), Mixolydian a flat 7th (bluesy), Ionian neither. Minor: Dorian has a natural 6th (brighter), Phrygian a flat 2nd (dark, Spanish), Locrian a flat 5th too, Aeolian is plain minor."),
        "mode-melodic" to a("Over a drone, a mode of melodic minor: melodic minor, Dorian ♭2, Lydian augmented, Lydian dominant, Mixolydian ♭6, Locrian ♮2 or altered. Name it.",
            "Melodic minor is a minor scale with a major 6th and 7th. Listen for the 3rd first, then the notes that differ from the nearest major mode: Lydian dominant is Lydian with a ♭7, the altered scale has almost everything flattened."),
        "mode-harmonic" to a("Over a drone, a mode of harmonic minor (including Phrygian dominant). Name it.",
            "Every mode of harmonic minor has the scale's three-half-step leap somewhere. Find where it falls relative to the drone: between ♭2 and 3 is Phrygian dominant, between ♭6 and 7 is harmonic minor itself."),
        "mode-symmetric" to a("Over a drone, a symmetric scale: whole-tone, half-whole diminished, whole-half diminished, or augmented. Name it.",
            "Whole-tone has no half steps at all and floats. The diminished scales alternate half and whole steps; half-whole starts with the half step. The augmented scale alternates half steps and minor thirds."),
        "mode-character" to a("Over a drone, a short phrase built around the one note that defines a mode. Name the mode.",
            "Listen for the note that differs from plain major or minor: the raised 4th (Lydian), flat 7th (Mixolydian), natural 6th in minor (Dorian), flat 2nd (Phrygian), flat 5th (Locrian), flat 6th (Aeolian)."),
        // ------------------------------------------------------------------------------------------------ dictation
        "dc-mel-3-step" to a("A three-note melody in a major key, moving only by step. Enter the notes.",
            "Find the first note against the key, then each next note is just one scale step up or down. Sing it back before you answer."),
        "dc-mel-3-leap" to a("A three-note melody in a major key with at least one leap of a third or more. Enter the notes.",
            "Decide for each move: step, or skip? Leaps usually land on notes of the tonic chord (1, 3, 5)."),
        "dc-mel-4" to a("A four-note melody in a major key; the first note is given. Enter the rest.",
            "Sing the whole melody back once, then work out one note at a time, always relative to the last."),
        "dc-mel-6" to a("A six-note melody in a major key. Enter the notes.",
            "Hear it in two halves of three. Hum the shape (up, down, same) before naming notes."),
        "dc-mel-minor" to a("A five-note melody in a natural minor key. Enter the notes.",
            "The same method as major, but home is the minor tonic; ♭3 and ♭6 are the colour notes to listen for."),
        "dc-mel-mixed" to a("A five-note melody; the chord before it tells you whether the key is major or minor. Enter the notes.",
            "Decide the key from the chord first, then dictate as usual."),
        "dc-mel-chromatic" to a("A melody in a major key with one note outside the key, a half step from the note after it. Enter the notes.",
            "The outside note almost always leads by half step into a scale note: ♯4 into 5, ♯1 into 2, ♭7 down to 6. Find the note it resolves to first."),
        "dc-bass" to a("Four chords in a major key; write down the bass line (the first note is given).",
            "Ignore the upper notes and hum only the lowest one. Bass lines move mostly by step or by fourth and fifth (1 to 4, 5 to 1)."),
        "dc-bass-minor" to a("Four chords in a minor key; write down the bass line.",
            "As in major, follow only the bottom; listen for ♭6 and ♭7, which show up often in minor bass lines."),
        "dc-passing" to a("A chord sounds, then a short line over it. One of the notes isn't in the chord (a passing tone). Pick which note.",
            "Chord tones sound at rest against the chord; the passing tone sounds like it's on its way somewhere, usually between two chord tones a third apart."),
        "dc-tempo" to a("A short groove plays. Name its tempo in BPM.",
            "Tap along and compare with tempos you know: 60 is one beat a second, 120 is two. Most pop sits between 90 and 130."),
        // ------------------------------------------------------------------------------------------------ harmony
        "hm-major" to a("A cadence (I–IV–V–I) sets a major key, then one chord plays. Name it by its numeral: I, ii, iii, IV, V, vi or vii°.",
            "Listen to the bass and to the job the chord does. I is home; V wants to return home; IV sounds like leaving; vi is a darker home; ii leads toward V; iii is ambiguous; vii° is tense and unstable. The Theory basics page \"Chords of a major key\" plays each one."),
        "hm-minor" to a("A cadence sets a minor key, then one chord plays. Name its numeral: i, ii°, III, iv, v, V, VI or VII.",
            "i is home; V (with the raised 7th) pulls hard home while v doesn't; VI and VII are the big major chords on ♭6 and ♭7; III is the relative major."),
        "hm-cadence" to a("A short phrase ends one of four ways. Name the cadence: authentic (V–I), plagal (IV–I), half (ending on V) or deceptive (V–vi).",
            "Listen only to the last two chords. Does it end at home? Strongly (authentic) or gently (plagal)? Does it stop on a chord that wants to go on (half)? Or does it set up home and land somewhere else (deceptive)?"),
        "hm-secondary" to a("A major key, then a secondary dominant and the chord it points at. Name the target: ii, iii, IV, V or vi.",
            "The secondary dominant is a borrowed V7; the chord after it is its target. Name the target chord the way you would in \"Chords in a major key\", then the dominant before it is V7 of that chord."),
        "hm-borrowed" to a("I, a chord borrowed from the parallel minor, then I again. Name the borrowed chord: iv, ♭III, ♭VI, ♭VII or ii°.",
            "All of them sound darker than the key. Follow the bass: up a 4th (iv), up a minor 3rd (♭III), down a major 3rd (♭VI), down a whole step (♭VII), up a whole step (ii°)."),
        "hm-predominant" to a("In a minor key: i, a predominant chord, V, i. Name the predominant: iv, the Neapolitan (N⁶) or an Italian, French or German sixth.",
            "iv is plain. N⁶ has the dark ♭2 on top. The augmented sixths have the ♭6 in the bass moving down a half step to V's root; Italian is three notes, French has a whole-tone shimmer, German sounds like a dominant seventh."),
        "hm-subs" to a("ii7, a dominant, Imaj7. Name the dominant: the plain V7, the tritone substitute ♭II7, or the backdoor ♭VII7.",
            "Follow the bass into the last chord: down a fifth from V7, down a half step from ♭II7, up a whole step from ♭VII7."),
        "hm-modulation" to a("A phrase in a major key moves to a new key. Name where it goes: to V, IV, vi, ii, iii, or the parallel minor i.",
            "Sing the new home note at the end and compare it with the old one: a fifth up (V), a fourth up (IV), down a minor third and minor (vi), and so on."),
        "hm-prog-major" to a("A whole four-chord progression in a major key, no cadence first. Name the progression from six common ones: I–IV–V–I, I–V–vi–IV, I–vi–IV–V, vi–IV–I–V, I–IV–vi–V, or ii–V–I.",
            "Start with the first chord: home (I), minor (vi or ii)? Then follow the bass: where does the minor chord fall, second, third or never? I–V–vi–IV and I–vi–IV–V both use the same four chords; listen for whether the minor vi comes early or late. The Theory basics pages \"Chords of a major key\" and \"Function\" play these progressions with the chords named."),
        "hm-prog-minor" to a("A whole progression in a minor key. Name it from six common ones, such as i–iv–V–i, i–VI–III–VII and i–VII–VI–VII.",
            "Listen for the major V (the raised 7th, classical) against VII (rock). VI and VII are the big major chords a step apart; III is the relative major."),
        "hm-prog-sevenths" to a("A progression of seventh chords: the ii–V–I, the I–vi–ii–V turnaround, iii–vi–ii–V, and others. Name it.",
            "Hear where the V7 is: it's the most restless chord, and the one before it is usually ii. Turnarounds end on V7 instead of resolving."),
        "hm-modal" to a("A short vamp sets a mode (its tonic chord against the chord with its characteristic note). Name the mode.",
            "Major or minor tonic first. Then the second chord: II major (Lydian), ♭VII (Mixolydian), IV major in minor (Dorian), ♭II (Phrygian), ♭VI–♭VII (Aeolian), a diminished tonic (Locrian)."),
        "hm-upper" to a("A dominant seventh with a triad above it (an upper-structure triad). Name the triad by its root above the bass: II, ♭III, ♭V, VI, ♭VI or ♭II.",
            "Ignore the bass chord and sing the top three notes as a chord; then hear how far its root is above the bass. Each gives a different set of tensions: II is bright (9, ♯11, 13), ♭VI and ♭III are dark (♯9, ♭13)."),
        "hm-polychord" to a("A major triad with another major triad above it. Name the upper one by its distance from the lower.",
            "Listen to the top chord alone, find its root, then measure down to the bottom chord's root."),
        "hm-reharm" to a("I–IV–V7–I, then the same phrase with one chord changed. Name the replacement.",
            "Compare the two versions chord by chord and find the one that's different; then hear what it is: ii for IV, iv (darker) for IV, II7 for IV, a tritone sub or backdoor for V7, vi for the last I."),
        "sc-modal-voicing" to a("One chord, voiced to spell a mode: its 3rd and 7th, the 9th and the mode's characteristic note. Name the mode.",
            "The 3rd says major or minor. Then listen for the colour note at the top: ♯11 (Lydian), 13 with a ♭7 (Mixolydian or Dorian), ♭9 (Phrygian, Locrian), ♭13 (Aeolian)."),
        // ------------------------------------------------------------------------------------------------ tuning and timbre
        "tt-just" to a("A major chord in plain sine-like tones, tuned either in equal temperament (as a piano is) or justly (pure ratios 4:5:6). Pick which.",
            "The just chord sits perfectly still. The equal one has a faint, slow shimmer or roughness, mostly from the 3rd being slightly wide."),
        "tt-beats" to a("Two tones almost in unison. Do they beat (pulse) not at all, slowly, or fast?",
            "Listen for the volume wobbling: two pulses a second is slow, five is fast. No wobble means they're the same frequency."),
        "tt-partials" to a("A low note, then one of its harmonics. Name which harmonic (2nd to 8th).",
            "Name the interval from the low note: an octave (2nd), an octave and a fifth (3rd), two octaves (4th), two octaves and a major third (5th), and so on; the 7th sounds like a flat minor seventh."),
        // ------------------------------------------------------------------------------------------------ rhythm
        "rh-simple" to a("One bar of rhythm in 2/4, 3/4 or 4/4 after a count-in. Pick the written bar that matches.",
            "Count the beats out loud during the count-in, then hear where the notes fall against them: on the beat, between beats, held across."),
        "rh-tuplets" to a("One bar with triplets, quintuplets or sextuplets. Pick the written bar that matches.",
            "Count how many notes fit evenly into the beat: three is a triplet's lilt, five is a smooth uneven roll, six is twice as fast as three."),
        "rh-odd" to a("One bar in 5/8, 7/8 or 9/8 after a count-in. Pick the written bar.",
            "Group the eighth notes into 2s and 3s (7/8 might be 2+2+3); the accent at the start of each group tells you the grouping."),
        "rh-swing" to a("A line of eighth notes, played straight (even) or swung (long-short). Pick which.",
            "Straight eighths tick evenly like a clock. Swung eighths lilt: the first of each pair is longer, the second shorter and later."),
        "rh-tap" to a("Hear a bar of rhythm, then tap it back on the screen during the next bar.",
            "Count the beats while it plays, then tap exactly where the notes fell. Keep your counting going through the gap."),
        "rh-poly" to a("Two pulses at once, high against low: 3 against 2, or 4 against 3. Pick which.",
            "Count the low pulse, then count the high pulse in the same span. 3:2 has a lopsided swing; 4:3 is busier and more even."),
        "rh-poly-2" to a("Two pulses at once, the low one playing 2: the high one plays 3, 5 or 7. Pick which.",
            "Count the high clicks between the low ones and the moment they meet again."),
        "rh-poly-3" to a("Two pulses at once, the low one playing 3: the high one plays 2, 4, 5 or 7. Pick which.",
            "Fix on the low 3 and count the high clicks across one cycle."),
        "rh-poly-4" to a("Two pulses at once, the low one playing 4: the high one plays 3, 5 or 7. Pick which.",
            "Fix on the low 4 and count the high clicks across one cycle."),
        "rh-poly-next" to a("Polyrhythms whose numbers are one apart: 3:2, 4:3, 5:4, 6:5, 7:6. Pick which.",
            "These are the hardest to tell apart; count the high pulse across one full cycle, from the shared downbeat to the next."),
        "rh-poly-more" to a("Two pulses at once, high against low, from any of twelve polyrhythms (2 to 7 against 2, 3 or 4). Pick which.",
            "Find the low pulse first and count it; then count the high pulse in the same cycle."),
        // ------------------------------------------------------------------------------------------------ triads
        "tr-majmin" to a("A triad in root position: major or minor. Pick which.",
            "Major is bright, minor darker. Listen to the middle note: high (major third above the root) or low (minor third)."),
        "tr-dimaug" to a("A triad in root position: diminished or augmented. Pick which.",
            "Diminished is small and tense, both thirds minor; augmented is wide and unresolved, both thirds major."),
        "tr-inv-maj" to a("A major triad with its root, 3rd or 5th in the bass. Pick which.",
            "Listen to the bass: root position is grounded, first inversion lighter, second inversion unsettled."),
        "tr-inv-min" to a("A minor triad with its root, 3rd or 5th in the bass. Pick which.",
            "As with major: follow only the lowest note and hear how stable the chord sounds above it."),
        "tr-inv-dim" to a("A diminished triad with its root, 3rd or 5th in the bass. Pick which.",
            "Diminished chords are unstable in every position; listen for which note is lowest by singing it."),
        "tr-inv-all" to a("Major, minor or diminished, in any inversion. Name both the quality and the bass.",
            "Decide the quality first (bright, dark or tense), then sing the bass and find it in the chord."),
        "tr-sus2-phryg" to a("A chord with no 3rd: sus2 (1 2 5) or the Phrygian triad (1 ♭2 5). Pick which.",
            "Sus2 sounds open, with a gentle whole-step rub. The Phrygian triad has a half-step clash at the bottom and sounds dark and Spanish."),
        "tr-sus4-lyd" to a("Sus4 (1 4 5) or the Lydian triad (1 ♯4 5). Pick which.",
            "Sus4 wants to fall to a 3rd. The Lydian triad's ♯4 is a tritone above the root and sounds bright and floating."),
        "tr-sus4b5-locr" to a("Sus4♭5 (1 4 ♭5) or the Locrian triad (1 ♭2 ♭5). Pick which.",
            "Both have the ♭5. Listen for the second note: a fourth above the root (sus4♭5) or a half step above (Locrian), which clashes."),
        "tr-colour" to a("Six chords with no 3rd: sus2, sus4, Phrygian, Lydian, sus4♭5 and Locrian. Name which.",
            "Listen for what sits where the 3rd would be: a 2nd, ♭2, 4th or ♯4, then whether the top is a perfect or flat 5th."),
        "tr-all" to a("Any of the ten triad qualities, in root position. Name which.",
            "Sort first: has a 3rd (major, minor, diminished, augmented) or not (the sus and modal triads)? Then use the smaller drills' tricks."),
        // ------------------------------------------------------------------------------------------------ your music
        "own-chords" to a("A chord from your own charts, lyrics sheets and takes, played on the piano. Name its quality.",
            "These are your own progressions, so the context is familiar; listen to the chord alone first, then imagine where it sat in your song."),
        "own-keys" to a("A clip from one of your recordings. Name its key.",
            "Hum the note the music feels most at rest on; that's the tonic. Then decide major or minor."),
        "own-phrases" to a("A short phrase from one of your takes. Play it back on your instrument, or tab it by ear.",
            "Loop it, hum it, then find the first note on your instrument; work out the rest relative to it."),
    )
}
