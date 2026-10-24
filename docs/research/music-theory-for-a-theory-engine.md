# Music Theory, Mapped for a Theory Engine: Taxonomy, Formulas, Spelling Rules, Curriculum and Resource Survey

Almost all of music theory reduces to a few computable objects:
- a **pitch** (letter + accidental + octave);
- an **interval** (letter-distance plus semitone count);
- **scales, modes and chords**, which are lists of intervals tagged with degree numbers.

If you build the engine on letter-aware intervals rather than raw semitones, it can generate nearly every Theory page with correct spelling, on piano, staff, any-tuning fretboard and audio. Everything else (function, voice leading, rhythm, form, notation, acoustics, ear training) sits on that core, in the dependency order in Section 2.

## TL;DR

- **Spell from intervals, not semitones.** Store every interval as (generic size, semitones) and every chord or scale tone as a degree label (♭3, ♯11, 𝄫7). Then the rule "each degree gets its own letter" spells correctly by itself: F major has B♭, not A♯; Cdim7 has B𝄫; E7♯9 has F𝄪. Engines that only count semitones get these wrong.
- **Learning order.** Pitch and rhythm basics, then the major scale, intervals, keys, triads, diatonic harmony and function, sevenths and progressions, modes, chromatic harmony, and finally jazz/contemporary topics. Fretboard and ear training run alongside throughout. Peer-reviewed studies favour sound-before-symbol and interleaved practice. The m6, m7 and tritone are the hardest intervals for novices.
- **What you can legally adapt.** Open Music Theory (CC BY-SA 4.0) and musictheory.net's lesson text and images (CC BY 4.0) can be adapted with attribution. Teoria (CC BY-NC-ND), Hooktheory, Levine, Kostka-Payne, Aldwell & Schachter and *Behind Bars* are reference-only. None of them generates theory for the user's own tuning, capo, string count or songs, and that is the app's gap to fill.

## Key Findings

- **Chord-symbol defaults.** Open Music Theory (OMT) states them as: "Triads are assumed to be major. Sevenths added to the triad are assumed to be minor. All other extensions and added tones are assumed to be major/perfect." [pressbooks](https://viva.pressbooks.pub/openmusictheory/chapter/chord-symbols/) These are the parser and generator defaults.
- **No single symbol standard.** OMT says chord symbols were "never completely standardized." [pressbooks](https://viva.pressbooks.pub/openmusictheory/chapter/chord-symbols/) The closest thing to a standard is Brandt & Roemer's out-of-print *Standardized Chord Symbol Notation* (1976). Its rules: always write the 7 when the 9 is altered, use ♭/♯ rather than −/+, and "the best chord symbol is the simplest one." [openlibrary](https://openlibrary.org/books/OL13591622M/Standardized_chord_symbol_notation) [popschoolmaastricht](https://www.popschoolmaastricht.nl/pdf/RoemerBrandt-StandardizedChordSymbolNotation.pdf)
- **MIDI octave naming genuinely conflicts.** The MIDI spec only fixes middle C as note 60. Scientific pitch notation calls it C4; many DAWs label it C3. [midi](https://midi.org/community/midi-specifications/midi-octave-and-note-numbering-standard) [homestudio](https://homestudio.app/resources/midi-middle-c/) Default to C4 and always show the MIDI number.
- **Bass pitch detection on phones is an acoustics problem, not a bug.** Electric bass and guitar often carry more energy in the 2nd and 3rd harmonics than in the fundamental, and pitch trackers are known to make "octave errors." [Wikipedia](https://en.wikipedia.org/wiki/Pitch_detection_algorithm) [cycfi](https://www.cycfi.com/2017/10/fast-and-efficient-pitch-detection/) Modified-autocorrelation estimators such as YIN (de Cheveigné & Kawahara, 2002) are designed to reduce them.
- **Licensing is uneven.** musictheory.net's Legal page says "All text and still images used in the lessons are available under the Creative Commons Attribution 4.0 International license," with a visible "by musictheory.net" link required online. It also says "the musictheory.net source code… is not available under a Creative Commons license" and that "Theory Lessons and Tenuto are not available under a Creative Commons license." The lesson data/JSON files and audio are excluded too. OMT2's Pressbooks record says it "is licensed under a Creative Commons Attribution-ShareAlike 4.0 International License, except where otherwise noted." Teoria is listed as CC BY-NC-ND 4.0. [wares](https://www.wares.gg/apps/teoria)

---

## 1. Core data model

**Pitch** = letter {C D E F G A B} + accidental {−2…+2} (𝄫 ♭ ♮ ♯ 𝄪) + octave.
- Natural pitch classes: C0 D2 E4 F5 G7 A9 B11.
- pc = (natural + accidental) mod 12.
- MIDI = 12×(octave+1) + natural + accidental.
- The octave number belongs to the *letter*, not the sounding key. So B♯3 = MIDI 60 (same key as C4), and C♭4 = MIDI 59.

**Frequency (12-TET).** f = 440 × 2^((MIDI−69)/12).
- A4 = 69 = 440 Hz; C4 ≈ 261.63 Hz; guitar E2 (40) ≈ 82.41 Hz; bass E1 (28) ≈ 41.20 Hz; 5-string B0 (23) ≈ 30.87 Hz.
- Cents = 1200·log2(f2/f1). One semitone = 100¢.

**Interval** = (generic size n counted inclusively by letters, semitones s).
- Reference semitones: 1→0, 2→2, 3→4, 4→5, 5→7, 6→9, 7→11, 8→12. Compound = +12 per octave (9→14, 11→17, 13→21).
- Perfect class (1 4 5 8 11 12): −1 = diminished, 0 = perfect, +1 = augmented.
- Major/minor class (2 3 6 7 9 10 13): −2 = diminished, −1 = minor, 0 = major, +1 = augmented.
- Doubly augmented/diminished intervals exist but are rare.

**Spelling rule** (degree *d* above a root):
1. Letter = root letter + (d−1) mod 7.
2. Accidental = whatever makes the pitch class match (root pc + semitones) mod 12.
3. If that would need a triple accidental, respell the root enharmonically or flag the chord as theoretical.

So 9 shares the letter of 2, 11 shares 4, and 13 shares 6. C7♯9 therefore contains D♯, not E♭.

**Degree labels** such as `1 ♭3 ♯4 𝄫7 9 ♯11 ♭13` each map to (n, s). For example, ♯11 = (11, 18) and 𝄫7 = (7, 9).

---

## 2. Curriculum and prerequisite map

Levels: B = beginner, I = intermediate, A = advanced. Each page should define terms only by using pages earlier in this list.

| # | Topic | Level | Prerequisites |
|---|---|---|---|
| F1 | Musical alphabet; piano layout | B | — |
| F2 | Half/whole steps; accidentals; enharmonics | B | F1 |
| F3 | Octaves; scientific pitch; MIDI; A4=440 | B | F1 |
| F4 | Pulse, note/rest values, bars, time signatures | B | — |
| F5 | Staff, clefs, ledger lines; tab | B | F1, F3 |
| F6 | Fretboard in *your* tuning/capo | B | F2, F3 |
| S1 | Major scale | B | F2 |
| S2 | Intervals (generic/specific, inversion, consonance) | B | S1 |
| S3 | Key signatures; circle of fifths | B | S1 |
| S4 | Three minor scales; relative/parallel | B | S1, S3 |
| S5 | Pentatonic and blues scales | B | S2, S4 |
| C1 | Triads, sus, power chords | B | S2 |
| C2 | Diatonic triads; Roman numerals | B/I | C1, S3 |
| C3 | Inversions; slash chords; figured bass | I | C1 |
| C4 | Seventh chords | I | C1 |
| C5 | Function; cadences; common progressions | I | C2 |
| R2 | Compound/irregular meter; syncopation; swing; tuplets | I | F4 |
| M1 | Modes of major | I | S1, S2, C2 |
| N1 | Melody; non-chord tones; phrases | I | C2 |
| C6 | 6/add/sus/extended/altered chords; symbol conventions | I | C4 |
| H1 | Secondary dominants; tonicization; modulation | I | C4, C5 |
| H2 | Modal mixture; rock/pop harmony | I | C5, M1 |
| V1 | Voice leading; voicings | I/A | C3, C4 |
| FM | Form and song structure | B→I | C5 |
| A1 | Harmonic series; tuning systems; timbre | I/A | F3, S2 |
| M2 | Melodic/harmonic-minor modes; symmetric and bebop scales | A | M1 |
| J1 | Jazz: chord-scale, guide tones, subs, reharm, upper structures | A | C6, M1, H1 |
| H3 | Neapolitan; augmented sixths; chromatic mediants | A | H1, H2 |
| X1 | Quartal; non-functional harmony; set theory; microtonality | A | S2, C6 |
| E* | Ear training (degrees, solfège, functional hearing) | B→A | runs alongside all |

---

## 3. Pitch fundamentals (B)

**Alphabet and steps.**
- The musical alphabet is A–G, repeating.
- There is no black key between E–F or B–C. These natural half steps are the root of most spelling confusion.
- Half step = 1 key/fret. Whole step = 2.

**Accidentals.**
- ♯ raises a note 1 semitone, ♭ lowers it 1, ♮ cancels, 𝄪 raises 2, 𝄫 lowers 2. [Wikipedia](https://simple.wikipedia.org/wiki/Flat_%28music%29)
- E♯, B♯, F♭ and C♭ are correct spellings in some contexts.
- *Misconception:* "sharps and flats are the black keys." C♭ is the white key B. [Wikipedia](https://simple.wikipedia.org/wiki/Flat_%28music%29)

**Enharmonic equivalence.** F♯ = G♭ and B♯ = C sound the same but are different theoretical objects. [Wikipedia](https://simple.wikipedia.org/wiki/Flat_%28music%29) Context decides the spelling (Section 20).

**Octave naming.**
- Scientific pitch: middle C = C4. [Wikipedia](https://en.wikipedia.org/wiki/Octave) An 88-key piano runs A0–C8 (MIDI 21–108).
- MIDI only fixes middle C as note 60 (and General MIDI tunes note 69 to 440 Hz). Software labels it C3 (often listed: Ableton, Cubase, Logic, Studio One) or C4 (Korg, Roland, scientific). [homestudio](https://homestudio.app/resources/midi-middle-c/)
- Sources disagree about Yamaha, so display the MIDI number. [midi](https://midi.org/community/midi-specifications/midi-octave-and-note-numbering-standard) [homestudio](https://homestudio.app/resources/midi-middle-c/)

**Reference pitch.** A4 = 440 Hz is standard, but Lerch (ISMIR 2006) reports that "the Chicago Symphony Orchestra and the New York Philharmonic tune at 442 Hz, while the Berlin and Vienna Philharmonic orchestras have a tuning frequency of 443 Hz." Make it a setting.

**Tuning systems (A).**
- **12-TET:** 12 equal 100¢ semitones, so all keys are equally usable.
- **Just intonation:** small-integer frequency ratios.
  - P5 3:2 (701.96¢ vs 700)
  - M3 5:4 (386.31¢; the ET third is ~14¢ wide)
  - m3 6:5 (315.64¢)
  - P4 4:3 (498.04¢)
  - M6 5:3 (884.36¢)
  - M7 15:8 (1088.27¢)
- **Pythagorean:** stacked pure fifths. Twelve of them overshoot seven octaves by ~23.46¢ (the Pythagorean comma).
- Meantone and well temperaments are overview-level only.

---

## 4. Intervals (B → I)

| Semitones | Name | Enharmonic alt. | Inversion | Common-practice class |
|---|---|---|---|---|
| 0 | P1 | d2 | P8 | perfect consonance |
| 1 | m2 | A1 | M7 | sharp dissonance |
| 2 | M2 | d3 | m7 | mild dissonance |
| 3 | m3 | A2 | M6 | imperfect consonance |
| 4 | M3 | d4 | m6 | imperfect consonance |
| 5 | P4 | A3 | P5 | consonant, but dissonant above the bass |
| 6 | A4 / d5 (tritone) | — | d5↔A4 | dissonance |
| 7 | P5 | d6 | P4 | perfect consonance |
| 8 | m6 | A5 | M3 | imperfect consonance |
| 9 | M6 | d7 | m3 | imperfect consonance |
| 10 | m7 | A6 | M2 | mild dissonance |
| 11 | M7 | d8 | m2 | sharp dissonance |
| 12 | P8 | A7, d9 | P1 | perfect consonance |
| 13 | m9 (♭9) | A8 | — | dissonance |
| 14 | M9 | d10 | — | mild dissonance |
| 15 | m10 | A9 (♯9) | — | consonance |
| 16 | M10 | d11 | — | consonance |
| 17 | P11 | A10 | — | as P4 |
| 18 | A11 (♯11) | d12 | — | dissonance |
| 19 | P12 | d13 | — | consonance |
| 20 | m13 (♭13) | A12 (♯5) | — | dissonance in chords |
| 21 | M13 | — | — | colour tone |
| 24 | P15 | — | — | perfect consonance |

**Inversion.** Generic sizes sum to 9 and semitones sum to 12. Major ↔ minor, augmented ↔ diminished, perfect stays perfect. Example: C–A (M6) inverts to A–C (m3).

**Compound intervals.** Compound = simple + 7. Jazz uses 9/11/13 when the tone sits above a 7th.

**Why spelling matters.**
- C–F♯ (A4) tends to expand outward, while C–G♭ (d5) contracts inward.
- A♭–B in harmonic minor is an A2, not an m3. An engine that labels 3 semitones "m3" will mislabel harmonic minor.

**Consonance is style-dependent.** Common-practice counterpoint treats the P4 above the bass as dissonant; rock power chords and quartal jazz treat it as stable. Present this as "how this style hears it."

---

## 5. Scales (B → A)

Semitones are counted from the root. In step patterns, W = 2, H = 1, A2 = 3. Examples are on C unless noted.

| Scale | Semitones | Steps | Degrees | Example | Notes |
|---|---|---|---|---|---|
| Major (Ionian) | 0 2 4 5 7 9 11 | WWHWWWH | 1 2 3 4 5 6 7 | C D E F G A B | B |
| Natural minor | 0 2 3 5 7 8 10 | WHWWHWW | 1 2 ♭3 4 5 ♭6 ♭7 | A B C D E F G | B |
| Harmonic minor | 0 2 3 5 7 8 11 | WHWWH A2 H | 1 2 ♭3 4 5 ♭6 7 | A B C D E F G♯ | raised 7 = leading tone |
| Melodic minor (asc./jazz) | 0 2 3 5 7 9 11 | WHWWWWH | 1 2 ♭3 4 5 6 7 | A B C D E F♯ G♯ | classical descent = natural minor |
| Major pentatonic | 0 2 4 7 9 | W W m3 W m3 | 1 2 3 5 6 | C D E G A | B |
| Minor pentatonic | 0 3 5 7 10 | m3 W W m3 W | 1 ♭3 4 5 ♭7 | A C D E G | B |
| Minor blues | 0 3 5 6 7 10 | — | 1 ♭3 4 ♭5 5 ♭7 | A C D E♭ E G | added note spelled ♭5 or ♯4; sources vary |
| Major blues | 0 2 3 4 7 9 | — | 1 2 ♭3 3 5 6 | C D E♭ E G A | — |
| Chromatic | 0–11 | all H | — | sharps up, flats down | convention |
| Whole tone | 0 2 4 6 8 10 | W×6 | 1 2 3 ♯4 ♯5 ♭7 | C D E F♯ G♯ B♭ | skips a letter |
| Half-whole octatonic | 0 1 3 4 6 7 9 10 | HWHW… | 1 ♭9 ♯9 3 ♯11 5 13 ♭7 | C D♭ D♯ E F♯ G A B♭ | fits 7♭9 |
| Whole-half octatonic | 0 2 3 5 6 8 9 11 | WHWH… | 1 2 ♭3 4 ♭5 ♭6 𝄫7 7 | C D E♭ F G♭ A♭ B𝄫 B | fits °7 |
| Bebop dominant | 0 2 4 5 7 9 10 11 | Mixolydian+♮7 | — | C D E F G A B♭ B | A |
| Bebop major | 0 2 4 5 7 8 9 11 | major+♯5 | — | C D E F G G♯ A B | A |
| Bebop minor | 0 2 3 4 5 7 9 10 *or* 0 2 3 5 7 9 10 11 | Dorian+♮3 or +♮7 | — | — | **sources disagree**; store both |
| Harmonic major | 0 2 4 5 7 8 11 | — | 1 2 3 4 5 ♭6 7 | C D E F G A♭ B | A |
| Double harmonic major | 0 1 4 5 7 8 11 | H A2 H W H A2 H | 1 ♭2 3 4 5 ♭6 7 | C D♭ E F G A♭ B | "Byzantine/Arabic" |
| Hungarian minor | 0 2 3 6 7 8 11 | W H A2 H H A2 H | 1 2 ♭3 ♯4 5 ♭6 7 | C D E♭ F♯ G A♭ B | A |
| Phrygian dominant | 0 1 4 5 7 8 10 | H A2 H W H W W | 1 ♭2 3 4 5 ♭6 ♭7 | E F G♯ A B C D | flamenco, metal |
| Hungarian major | 0 3 4 6 7 9 10 | — | 1 ♯2 3 ♯4 5 6 ♭7 | C D♯ E F♯ G A B♭ | A |
| Neapolitan minor / major | 0 1 3 5 7 8 11 / 0 1 3 5 7 9 11 | — | 1 ♭2 ♭3 4 5 ♭6(6) 7 | C D♭ E♭ F G A♭(A) B | A |
| Persian | 0 1 4 5 6 8 11 | — | 1 ♭2 3 4 ♭5 ♭6 7 | C D♭ E F G♭ A♭ B | A |
| Augmented (hexatonic) | 0 3 4 7 8 11 | m3 H… | 1 ♯2 3 5 ♯5 7 | C D♯ E G G♯ B | A |
| Suspended pentatonic | 0 2 5 7 10 | — | 1 2 4 5 ♭7 | C D F G B♭ | I |
| Hirajōshi | 0 2 3 7 8 (one version) | — | 1 2 ♭3 5 ♭6 | C D E♭ G A♭ | **versions differ** |
| In / Iwato | 0 1 5 7 8 / 0 1 5 6 10 | — | — | C D♭ F G A♭ / C D♭ F G♭ B♭ | A |
| Ukrainian Dorian | 0 2 3 6 7 9 10 | — | 1 2 ♭3 ♯4 5 6 ♭7 | D E F G♯ A B C | A |

**Spelling.** Seven-note scales use one letter per degree, so their spelling is unique. Scales with 5, 6 or 8 notes need the degree labels above.

**Non-Western systems.** Maqam, raga and gamelan use intervals and melodic rules beyond 12-TET. Label the 12-TET versions as approximations.

---

## 6. Modes (I → A)

**Modes of major** (shown as parallel formulas)

| Mode | Semitones | Degrees | Characteristic | White keys | Chord / sound |
|---|---|---|---|---|---|
| Ionian | 0 2 4 5 7 9 11 | 1 2 3 4 5 6 7 | — | C–C | maj7; home major |
| Dorian | 0 2 3 5 7 9 10 | 1 2 ♭3 4 5 6 ♭7 | ♮6 | D–D | m7/m6; funk, soul vamps |
| Phrygian | 0 1 3 5 7 8 10 | 1 ♭2 ♭3 4 5 ♭6 ♭7 | ♭2 | E–E | metal, flamenco colour |
| Lydian | 0 2 4 6 7 9 11 | 1 2 3 ♯4 5 6 7 | ♯4 | F–F | maj7♯11; film-score major |
| Mixolydian | 0 2 4 5 7 9 10 | 1 2 3 4 5 6 ♭7 | ♭7 | G–G | dom7; rock I–♭VII–IV |
| Aeolian | 0 2 3 5 7 8 10 | 1 2 ♭3 4 5 ♭6 ♭7 | ♭6 | A–A | natural/pop minor |
| Locrian | 0 1 3 5 6 8 10 | 1 ♭2 ♭3 4 ♭5 ♭6 ♭7 | ♭5 | B–B | m7♭5; rarely a tonic |

**Modes of melodic minor**

| # | Name | Semitones | Chord |
|---|---|---|---|
| 1 | Melodic (jazz) minor | 0 2 3 5 7 9 11 | m(maj7), m6 |
| 2 | Dorian ♭2 | 0 1 3 5 7 9 10 | 7sus4♭9 |
| 3 | Lydian augmented | 0 2 4 6 8 9 11 | maj7♯5 |
| 4 | Lydian dominant | 0 2 4 6 7 9 10 | 7♯11 |
| 5 | Mixolydian ♭6 | 0 2 4 5 7 8 10 | 7♭13 |
| 6 | Locrian ♮2 | 0 2 3 5 6 8 10 | m7♭5 |
| 7 | Altered (Super Locrian) | 0 1 3 4 6 8 10 | 7alt |

The altered scale has two spellings:
- **As a scale**, one letter per degree: G♯ A B C D E F♯.
- **As a chord**, using chord tones: C D♭ D♯ E F♯ G♯ B♭ = 1 ♭9 ♯9 3 ♯11 ♭13 ♭7.

Store both.

**Modes of harmonic minor**

| # | Name | Semitones | Chord |
|---|---|---|---|
| 1 | Harmonic minor | 0 2 3 5 7 8 11 | m(maj7) |
| 2 | Locrian ♮6 | 0 1 3 5 6 9 10 | m7♭5 |
| 3 | Ionian ♯5 | 0 2 4 5 8 9 11 | maj7♯5 |
| 4 | Dorian ♯4 | 0 2 3 6 7 9 10 | m7♯11 |
| 5 | Phrygian dominant | 0 1 4 5 7 8 10 | 7♭9♭13 |
| 6 | Lydian ♯2 | 0 3 4 6 7 9 11 | maj7♯11 |
| 7 | Ultralocrian | 0 1 3 4 6 8 9 | °7 |

**Misconception.** "Dorian is C major starting on D" is only the *relative* view. Over a D drone, D Dorian is a minor scale with a raised 6th. Teach the parallel view and back it with drone audio.

---

## 7. Keys (B → I)

**Order of accidentals.** Sharps F C G D A E B; flats B E A D G C F.

**Major keys.**
- Sharp keys: C0, G1, D2, A3, E4, B5, F♯6, C♯7.
- Flat keys: F1, B♭2, E♭3, A♭4, D♭5, G♭6, C♭7.
- Enharmonic pairs: B/C♭, F♯/G♭, C♯/D♭.

**Minor keys.** The relative minor is a m3 below the major tonic and shares its signature.
- Sharp keys: a0, e1, b2, f♯3, c♯4, g♯5, d♯6, a♯7.
- Flat keys: d1, g2, c3, f4, b♭5, e♭6, a♭7.

**Relative vs parallel keys.** Relative keys share a signature (C major / A minor). Parallel keys share a tonic (C major / C minor). Modal mixture borrows from the parallel key.

**Circle of fifths.** Each step clockwise goes up a P5 and adds one sharp. Neighbouring keys differ by one note, so they are closely related.

**Theoretical keys.** Keys like G♯ major (with F𝄪) or F♭ major (with B𝄫) normally default to their enharmonic equivalent. [Wikipedia](https://en.wikipedia.org/wiki/Sharp_%28music%29) The engine must still be able to spell them inside chromatic contexts, e.g. G♯ major as V of C♯ minor.

---

## 8. Chords (B → A)

### 8.1 Chord formula table

Root = C.

| Chord | Symbols (most common first) | Degrees | Semitones | On C | Essential / omittable |
|---|---|---|---|---|---|
| Major | C, CM, Cmaj | 1 3 5 | 0 4 7 | C E G | 5 omittable |
| Minor | Cm, Cmin, Cmi, C− | 1 ♭3 5 | 0 3 7 | C E♭ G | 1, ♭3 |
| Diminished | Cdim, C°, Cm(♭5) | 1 ♭3 ♭5 | 0 3 6 | C E♭ G♭ | all |
| Augmented | Caug, C+ | 1 3 ♯5 | 0 4 8 | C E G♯ | all |
| Sus2 | Csus2 | 1 2 5 | 0 2 7 | C D G | no 3rd |
| Sus4 | Csus4, Csus | 1 4 5 | 0 5 7 | C F G | no 3rd |
| Power | C5 | 1 5 (8) | 0 7 (12) | C G | neither major nor minor |
| 6 | C6 | 1 3 5 6 | 0 4 7 9 | C E G A | 5 omittable |
| m6 | Cm6 | 1 ♭3 5 6 | 0 3 7 9 | C E♭ G A | 6 is **major** |
| 6/9 | C6/9 | 1 3 5 6 9 | 0 4 7 9 14 | C E G A D | no 7th |
| m6/9 | Cm6/9 | 1 ♭3 5 6 9 | 0 3 7 9 14 | C E♭ G A D | — |
| maj7 | Cmaj7, CM7, CΔ7, CΔ | 1 3 5 7 | 0 4 7 11 | C E G B | 5 omittable |
| Dominant 7 | C7 | 1 3 5 ♭7 | 0 4 7 10 | C E G B♭ | 3 + ♭7 (tritone) essential |
| m7 | Cm7, C−7 | 1 ♭3 5 ♭7 | 0 3 7 10 | C E♭ G B♭ | 5 omittable |
| m(maj7) | Cm(maj7), CmM7, C−Δ7 | 1 ♭3 5 7 | 0 3 7 11 | C E♭ G B | — |
| Half-dim | Cm7♭5, Cø7, Cø | 1 ♭3 ♭5 ♭7 | 0 3 6 10 | C E♭ G♭ B♭ | ♭5 essential |
| Dim7 | C°7, Cdim7 | 1 ♭3 ♭5 𝄫7 | 0 3 6 9 | C E♭ G♭ B𝄫 | symmetric |
| Aug7 | C7♯5, C+7 | 1 3 ♯5 ♭7 | 0 4 8 10 | C E G♯ B♭ | — |
| Aug maj7 | Cmaj7♯5, C+maj7 | 1 3 ♯5 7 | 0 4 8 11 | C E G♯ B | — |
| 7♭5 | C7♭5 | 1 3 ♭5 ♭7 | 0 4 6 10 | C E G♭ B♭ | tritone-symmetric |
| 7sus4 | C7sus4, C7sus | 1 4 5 ♭7 | 0 5 7 10 | C F G B♭ | no 3rd |
| 9 | C9 | 1 3 5 ♭7 9 | 0 4 7 10 14 | C E G B♭ D | 5 omittable |
| maj9 | Cmaj9 | 1 3 5 7 9 | 0 4 7 11 14 | C E G B D | 5 omittable |
| m9 | Cm9 | 1 ♭3 5 ♭7 9 | 0 3 7 10 14 | C E♭ G B♭ D | 5 omittable |
| m(maj9) | Cm(maj9) | 1 ♭3 5 7 9 | 0 3 7 11 14 | C E♭ G B D | — |
| 11 | C11 | 1 (3) 5 ♭7 9 11 | 0 (4) 7 10 14 17 | C G B♭ D F | **3rd normally omitted**; ≈ C9sus4 ≈ B♭/C |
| m11 | Cm11 | 1 ♭3 5 ♭7 9 11 | 0 3 7 10 14 17 | C E♭ G B♭ D F | 5, 9 omittable |
| maj7♯11 | Cmaj7♯11 | 1 3 5 7 (9) ♯11 | 0 4 7 11 (14) 18 | C E G B F♯ | natural 11 avoided |
| 13 | C13 | 1 3 5 ♭7 9 13 | 0 4 7 10 14 21 | C E G B♭ D A | **11 omitted**; core 1 3 ♭7 13 |
| maj13 | Cmaj13 | 1 3 5 7 9 13 | 0 4 7 11 14 21 | C E G B D A | 11 omitted |
| m13 | Cm13 | 1 ♭3 5 ♭7 9 11 13 | 0 3 7 10 14 17 21 | C E♭ G B♭ D F A | 5 omittable |
| 7♭9 | C7♭9 | 1 3 5 ♭7 ♭9 | 0 4 7 10 13 | C E G B♭ D♭ | — |
| 7♯9 | C7♯9 | 1 3 5 ♭7 ♯9 | 0 4 7 10 15 | C E G B♭ D♯ | E7♯9 = E G♯ B D F𝄪 |
| 7♯11 | C7♯11 | 1 3 5 ♭7 ♯11 | 0 4 7 10 18 | C E G B♭ F♯ | Lydian dominant |
| 7♭13 | C7♭13 | 1 3 ♭7 ♭13 | 0 4 10 20 | C E B♭ A♭ | 5 usually omitted |
| 7alt | C7alt | 1 3 ♭7 + ♭9/♯9/♯11/♭13 | e.g. 0 4 10 15 20 | C E B♭ D♯ A♭ | no natural 5/9/13 |
| add9 | Cadd9 | 1 3 5 9 | 0 4 7 14 | C E G D | **no 7th** |
| m(add9) | Cm(add9) | 1 ♭3 5 9 | 0 3 7 14 | C E♭ G D | — |
| add11 | Cadd11 | 1 3 5 11 | 0 4 7 17 | C E G F | keeps the 3rd |
| add2 / add4 | Cadd2 / Cadd4 | 1 2 3 5 / 1 3 4 5 | 0 2 4 7 / 0 4 5 7 | C D E G / C E F G | added note voiced inside the triad |
| Omissions | C(no3), C(no5) | formula minus tone | — | — | — |
| Slash | C/E, C/F♯ | chord over a bass note | — | E + C E G | bass may be a non-chord tone |
| Polychord | D over C (horizontal line) | two triads stacked | — | D F♯ A over C E G | horizontal line = polychord; diagonal slash = bass note (Brandt & Roemer) |
| Quartal | no standard symbol | stacked P4s | 0 5 10 (15) | C F B♭ (E♭) | — |
| "So What" | e.g. Em11 voicing | P4 P4 P4 M3 | 0 5 10 15 19 | E A D G B | modal jazz |
| Quintal | C5add9 | stacked P5s | 0 7 14 | C G D | ambiguous quality |
| Cluster | no standard symbol | adjacent 2nds | 0 1 2 / 0 2 4 | C D♭ D | usually written out |

**Engine rules.**
- A chord symbol implies the stacked thirds below its highest extension: 9 ⇒ 7; 11 ⇒ 7, 9; 13 ⇒ 7, 9, 11. [pressbooks](https://viva.pressbooks.pub/openmusictheory/chapter/chord-symbols/) The exceptions are 6, 6/9, add and sus chords, which imply no 7th.
- The 3rd and 7th (guide tones) define the quality.
- The 5th is omitted first. The root is dropped in rootless voicings.
- The natural 11 over a major 3rd clashes (a ♭9 above the 3rd), so 13 chords omit the 11.
- OMT advises using ♭/♯ or −/+ consistently. Brandt & Roemer reject "−" for alterations because it also means minor. [pressbooks](https://viva.pressbooks.pub/openmusictheory/chapter/chord-symbols/) [popschoolmaastricht](https://www.popschoolmaastricht.nl/pdf/RoemerBrandt-StandardizedChordSymbolNotation.pdf)

### 8.2 Symbol conventions where sources disagree

| Item | Options | Most common modern usage | App default |
|---|---|---|---|
| Minor | m, min, mi, − | m (pop); − (jazz) | m |
| Major 7 | maj7, M7, Δ7, ma7 | maj7 (pop); Δ (jazz) | maj7, with a Δ toggle. Puget Sound's text says Δ is "used primarily in jazz. Popular music typically uses 'maj' instead." [pugetsound](https://musictheory.pugetsound.edu/mt21c/JazzChordBasics.html) |
| Half-diminished | m7♭5, ø | m7♭5 (pop/guitar), ø (jazz) | show both |
| Dim7 | dim7, °7 | either | always include the 7 |
| Sus with 7 | C7sus4 vs Csus7 | C7sus4 | C7sus4 (OMT: Csus7 "might look too much like Csus2") [pressbooks](https://viva.pressbooks.pub/openmusictheory/chapter/chord-symbols/) |
| Add | add9 vs add2 | add9 | add2/add4 inside the triad; add9/add11 above it |
| Altered 9 | 7♭9, 7(♭9), 7-9 | parentheses | always write the 7 |
| ♭VII in minor | VII vs ♭VII | classical VII; pop/Hooktheory ♭VII | major-referenced (♭VII) for pop pages |

### 8.3 Naming an arbitrary note set

1. Reduce the notes to pitch classes, keeping the bass note and any spelled letters.
2. Test each pitch class as a candidate root against the template library. Allow an omitted 5th, and an omitted root for rootless voicings.
3. Score each candidate:
   - plus: bass = root; complete triad/7th core; diatonic to the key;
   - minus: each alteration, extension, add or no qualifier.

   Prefer the simplest symbol (Brandt & Roemer).
4. If the winning root is not in the bass, output a slash chord.
5. **Always show the runner-up names.** Common ambiguous sets:
   - C6 = Am7/C
   - Cm6 = Aø7/C
   - Csus2 = Gsus4/C
   - C°7 = E♭°7 = G♭°7 = A°7
   - C+ = E+ = G♯+
   - C7♭5 ≈ G♭7♭5
   - C/D ≈ D9sus4

   OMT notes that "C/A♭" is often preferred to "A♭ma7(♯5)" for readability. [pressbooks](https://viva.pressbooks.pub/openmusictheory/chapter/chord-symbols/)

---

## 9. Inversions and voicings (I → A)

**Inversions.**
- Root position: 5/3, usually left blank.
- 1st inversion: 6.
- 2nd inversion: 6/4.
- Seventh-chord figures: 7, 6/5, 4/3, 4/2. Accidentals in figures alter the interval above the bass.

**Voicing types.**
- **Close voicing:** upper voices within an octave. **Open voicing:** spread wider.
- **Drop-2 / drop-3:** take the 2nd- or 3rd-highest voice of a close voicing down an octave. These are the standard guitar 7th-chord families.
- **Shell voicing:** 1–3–7.
- **Rootless voicings** (Bill Evans style): 3-5-7-9 and 7-9-3-5.

**CAGED.** The five open shapes (C, A, G, E, D) become movable barre shapes linking up the neck. This works only in standard 6-string tuning, so generate shapes from the actual tuning instead (Section 19).

**Bass.** Root–fifth–octave (0, 7, 12), arpeggios (1-3-5-7), chromatic approach notes and walking lines. In 4ths tuning these shapes are identical on every string set.

---

## 10. Harmony and function (I → A)

| Key | I | II | III | IV | V | VI | VII |
|---|---|---|---|---|---|---|---|
| Major triads | I | ii | iii | IV | V | vi | vii° |
| Major 7ths (C) | Cmaj7 | Dm7 | Em7 | Fmaj7 | G7 | Am7 | Bm7♭5 |
| Natural minor triads | i | ii° | III | iv | v | VI | VII |
| Natural minor 7ths (A) | Am7 | Bm7♭5 | Cmaj7 | Dm7 | Em7 | Fmaj7 | G7 |
| Harmonic minor triads | i | ii° | III+ | iv | V | VI | vii° |
| Harmonic minor 7ths (A) | Am(maj7) | Bm7♭5 | Cmaj7♯5 | Dm7 | E7 | Fmaj7 | G♯°7 |
| Melodic minor triads | i | ii | III+ | IV | V | vi° | vii° |
| Melodic minor 7ths (A) | Am(maj7) | Bm7 | Cmaj7♯5 | D7 | E7 | F♯m7♭5 | G♯m7♭5 |

**Minor keys mix all three forms.** V and vii°7 come from harmonic minor; iv and VI come from natural minor.

**Roman numerals.** Upper case = major, lower case = minor; °, + and ø mark quality; figures show inversion (V6, ii6/5). OMT stresses that chord symbols are *absolute* and Roman numerals are *relative*, so show both. [libretexts](https://human.libretexts.org/Bookshelves/Music/Music_Theory/Open_Music_Theory_2e_%28Gotham_et_al.%29/06:_Jazz/6.02:_Chord_Symbols)

**Function.**
- Tonic (I, vi, iii): stable.
- Predominant (IV, ii, ♭VI, N6, +6): moves away.
- Dominant (V, V7, vii°): tension driven by the leading tone and the 3–♭7 tritone (B–F in C).

**Cadences** (in C):

| Cadence | Progression | Example in C |
|---|---|---|
| PAC (perfect authentic) | V–I, both root position, tonic in melody | G7–C |
| IAC (imperfect authentic) | V–I with an inversion or the melody on 3/5; or vii°–I | G–C/E |
| Half | ends on V | …–G |
| Plagal | IV–I | F–C |
| Minor plagal | iv–I | Fm–C |
| Deceptive | V–vi | G–Am |
| Phrygian half (minor) | iv6–V | in A minor: Dm/F–E |
| Backdoor | (iv7)–♭VII7–I | Fm7–B♭7–C |

**Secondary dominants.**
- V(7) of a target sits a P5 above it; vii°7 of a target sits a half step below it.
- In C: V/V = D7 (D F♯ A C); V/ii = A7; V/vi = E7; V/IV = C7; vii°7/V = F♯ A C E♭.
- A brief emphasis is **tonicization**. A cadence in the new key is **modulation**.

**Modulation types.**
- **Pivot chord:** e.g. Am = vi in C = ii in G.
- **Direct:** e.g. a pop "truck-driver" lift up a half or whole step.
- **Common-tone:** a held note bridges the keys.
- **Enharmonic:** a °7 or Ger+6 is reinterpreted.

**Modal mixture in C.** iv (Fm), ♭VI (A♭), ♭VII (B♭), ♭III (E♭), ii°/iiø7. A Picardy third ends a minor piece on a major I.

**Neapolitan (♭II, usually N6).** In C minor: D♭ F A♭ with F in the bass.

**Augmented sixths** (♭6 in the bass, ♯4 above, expanding to an octave on 5; in C):
- Italian: A♭ C F♯.
- French: A♭ C D F♯.
- German: A♭ C E♭ F♯. It sounds like A♭7 but is spelled with F♯. This is the classical ancestor of the tritone sub.

**Tritone substitution.** Replace V7 with ♭II7: G7 → D♭7 (D♭ F A♭ C♭). Both share the B/C♭–F tritone. Spell C♭ and offer B as a display option.

**ii–V–I.**
- Major: Dm7–G7–Cmaj7.
- Minor: Dm7♭5–G7♭9–Cm(maj7)/Cm6.

**Chromatic mediants.** Roots a 3rd apart with the same quality (C→E, C→A♭, C→A, C→E♭). Common in film; modelled by neo-Riemannian P/L/R.

**Pedal point.** A held note, usually in the bass, under changing chords.

---

## 11. Common progressions

| Name | Numerals | In C (or A minor) | Typical genre |
|---|---|---|---|
| Axis / pop | I–V–vi–IV | C–G–Am–F | pop, rock |
| '50s | I–vi–IV–V | C–Am–F–G | doo-wop |
| ii–V–I | ii7–V7–Imaj7 | Dm7–G7–Cmaj7 | jazz, R&B |
| Turnaround | I–vi–ii–V | C–Am–Dm–G | standards |
| Circle | iii–vi–ii–V–I | Em–Am–Dm–G–C | jazz, classical |
| 12-bar blues | I7×4 / IV7 IV7 I7 I7 / V7 IV7 I7 V7 | C7…F7…G7 F7 C7 G7 | blues, rock'n'roll |
| Quick-change | IV7 in bar 2 | C7 F7 C7 C7… | blues |
| Minor blues | i7×4 / iv7 iv7 i7 i7 / ♭VI7 V7 i7 i7 | Am7…Dm7…F7 E7 Am7 | jazz/blues |
| Andalusian | i–♭VII–♭VI–V | Am–G–F–E | flamenco, rock |
| Mixolydian rock | I–♭VII–IV–I | C–B♭–F–C | classic rock |
| Minor plagal | IV–iv–I | F–Fm–C | ballads |
| Backdoor | iv7–♭VII7–I | Fm7–B♭7–Cmaj7 | jazz, neo-soul |
| Pachelbel | I–V–vi–iii–IV–I–IV–V | C–G–Am–Em–F–C–F–G | pop |
| Royal road | IVmaj7–V7–iii7–vi | Fmaj7–G7–Em7–Am | J-pop |
| Minor loops | i–♭VI; i–♭VI–♭III–♭VII | Am–F; Am–F–C–G | hip-hop, trap, EDM |
| Phrygian vamp | i–♭II | Em–F | metal, trap |
| Coltrane changes | key centres a major 3rd apart | B–D7–G–B♭7–E♭ | advanced jazz |

Genre labels mark where a progression is idiomatic, not where it is exclusive. Hooktheory's public Trends API exposes chord-to-chord probabilities from its crowd-sourced TheoryTab database. [hooktheory](https://www.hooktheory.com/api/trends/docs) [hooktheory](https://www.hooktheory.com/theorytab)

---

## 12. Melody, non-chord tones, voice leading (I → A)

**Non-chord tones** (examples over a C chord):
- **Passing:** step–step, same direction (E–F–G).
- **Neighbour:** step away and back (E–F–E).
- **Suspension:** prepared, held, then resolves down (4–3, 7–6, 9–8; 2–3 in the bass).
- **Retardation:** held, then resolves up.
- **Appoggiatura:** leap in, step out, usually accented.
- **Escape tone:** step in, leap out the opposite way.
- **Anticipation:** the next chord's tone arrives early.
- **Pedal:** sustained against the changing chords.
- **Double neighbour:** C–D–B–C.

**Tendencies in major.** 7→1, 4→3, 6→5. Degrees 1, 3 and 5 are stable. Hooktheory teaches this as "stable vs. unstable scale degrees." [hooktheory](https://www.hooktheory.com/books/one) [musical-u](https://www.musical-u.com/learn/review-hooktheory-music-theory-book/)

**Common-practice SATB rules.**
- Avoid parallel P5/P8 between any voices, and hidden 5ths/8ves in the outer voices.
- The leading tone goes up; a chordal 7th goes down by step.
- Don't double the leading tone.
- Keep adjacent upper voices within an octave.
- Keep common tones; otherwise move by the smallest step.

These are style rules: rock power-chord parallel fifths are idiomatic, so label the rules by style.

**Species counterpoint** (Fux, 1725). First species is 1:1, second 2:1, third 4:1, fourth uses suspensions, fifth is florid.

**Phrase structure.**
- Motif: a short recurring idea.
- Phrase: ends with a cadence.
- Period: antecedent (ends on HC) + consequent (ends on PAC).
- Sentence: 2+2+4 bars.
- Sequence: a pattern repeated at other pitch levels.

---

## 13. Rhythm and meter (B → A)

**Durations.**
- In 4/4: whole 4, half 2, quarter 1, eighth ½, sixteenth ¼, 32nd ⅛.
- A dot multiplies the value by 1.5; a double dot by 1.75.
- A tie joins two notes of the same pitch. A slur covers different pitches and means legato.
- Tuplets are stored as n:m ratios (triplet 3:2, quintuplet 5:4, duplet 2:3).

**Meter.**
- **Simple meters** divide the beat in 2: 2/4, 3/4, 4/4, 2/2.
- **Compound meters** divide a dotted beat in 3: 6/8 is 2 beats, 9/8 is 3, 12/8 is 4. *Misconception:* 6/8 is not six beats.
- **Irregular meters** are additive, e.g. 7/8 = 2+2+3.
- **Beat hierarchy:** downbeat > strong beats > weak beats > subdivisions. Syncopation accents against this hierarchy.

**Feel and layering.**
- **Swing:** long-short eighths, roughly a 2:1 triplet feel. The ratio varies by tempo and style, so store it as a playback parameter.
- **Shuffle:** triplet-based swung blues/rock.
- **Polyrhythm:** simultaneous divisions (3:2, 4:3).
- **Polymeter:** cycles of different lengths (a 3-beat pattern over 4/4).
- **Hemiola:** 3×2 against 2×3.
- **Metric modulation:** an old note value becomes a new one.

**Tempo.** Italian terms have only approximate BPM ranges, and published ranges differ:
- Largo ~40–60
- Adagio ~66–76
- Andante ~76–108
- Moderato ~108–120
- Allegro ~120–168
- Presto ~168–200

Typical genre tempos, also approximate:
- house ~120–128
- drum & bass ~170–175
- hip-hop ~80–100
- trap ~130–150 with a half-time feel

**Groove vocabulary.** Backbeat, half-/double-time, ghost notes, pocket/micro-timing, son clave 3-2/2-3, trap hi-hat rolls, four-on-the-floor.

---

## 14. Notation and tablature (B → I)

**Clefs.**
- Treble (G) clef curls around G4. Bass (F) clef dots surround F3.
- Alto C clef puts C4 on the middle line; tenor C clef puts it on the 4th line.
- A percussion clef has no pitch meaning; a TAB clef marks tablature.
- **Guitar is written an octave above sounding pitch** (often with an 8 under the clef), and so is bass guitar. Show written pitch on the staff but use sounding pitch for audio and MIDI.

**Accidentals.** An accidental lasts for the rest of the bar on that staff position. It carries across a barline only on a tied note. Courtesy accidentals remind the reader after the barline. Elaine Gould's *Behind Bars* (Faber 2011; *General Conventions* 2023) is the engraving authority. [Wikipedia](https://en.wikipedia.org/wiki/Behind_Bars_%28book%29) [fabermusic](https://www.fabermusic.com/news/new-behind-bars-general-conventions-published-22062023) Practice varies on whether an accidental affects other octaves, so print explicit accidentals.

**Beaming.** Beam to show beats. In 4/4, avoid beaming across the middle of the bar where it hides beat 3. In compound meters, beam in threes. In irregular meters, beam by the additive groups.

**Navigation and expression.**
- Repeats, 1st/2nd endings, D.C., D.S. (𝄋), al Fine, al Coda (𝄌).
- Dynamics ppp–fff, sfz, fp, hairpins.
- Articulations: staccato, tenuto, accent, marcato, fermata.

**Chord symbols and lyrics.** Chord symbols go above the staff, aligned to the beat of the change. Lyrics go below, with hyphens between syllables and extender lines for melismas.

**Tab.**
- The top line is the highest-pitched string. Numbers are frets; 0 = open. Rhythm is optional.
- Technique symbols: h, p, / \, b (½ = semitone, "full" = tone), r, pb, ~, P.M., x, N.H./<12>, A.H., P.H., T (tap).
- Symbols vary by publisher, so store techniques semantically and render them per house style.

**Notation ↔ tab.** Staff notation gives pitch and rhythm; tab gives position. One pitch can sit at several frets, so compute every position and choose one with a fingering-cost model.

---

## 15. Form and song structure (B → I)

**Popular forms.**
- Sections: intro, verse, pre-chorus, chorus, post-chorus, bridge/middle 8, breakdown, outro. The hook can sit anywhere.
- Strophic (AAA).
- 32-bar AABA (jazz standards).
- Verse–chorus(–bridge).
- 12-bar blues, often with AAB lyrics.

**Classical forms.** Binary, rounded binary, ternary, rondo, sonata (exposition–development–recapitulation), theme and variations.

**Typical section lengths.** Pop phrases run in 4/8 bars. Hip-hop verses are often 16 bars with 8-bar hooks. EDM builds in 8/16/32-bar blocks (build–drop–breakdown). These are norms, not rules.

---

## 16. Texture, timbre, acoustics (I → A)

**Texture.** Monophony, homophony, polyphony, heterophony. Arrangement basics: register, doubling, wide spacing low and close spacing high, avoiding masking.

**Timbre** comes from the relative strengths of the partials and how they change over time (attack, decay).

**Harmonic series.** Partial n = n × f₀ and lies 1200·log2(n)¢ above the fundamental. On C2:
- 1–6: C2 C3 G3 C4 E4 G4
- 7: B♭4 (−31¢)
- 8–10: C5 D5 E5
- 11: ≈F♯5 (−49¢)
- 12: G5
- 13: ≈A5 (−59¢)
- 14–16: B♭5 B5 C6

The 3rd partial is a pure P12 (1901.96¢). The 5th is a just M3 two octaves up (~14¢ below ET). The first five partials outline a major triad, [michaelbach](https://michaelbach.de/ot/aud-missFund/) which is why small-integer ratios sound consonant.

**Why phones misread bass.**
- The fundamentals are very low (E1 41 Hz, B0 31 Hz), and small phone mics respond weakly there.
- Pickups often deliver more 2nd/3rd harmonic than fundamental. One developer notes the electric guitar signal "contains higher levels of the second (and even the third) harmonic than the fundamental." [cycfi](https://www.cycfi.com/2017/10/fast-and-efficient-pitch-detection/)
- Pitch detectors therefore make "octave errors" and report a loud overtone, while listeners still hear the low pitch (the "missing fundamental"). [Wikipedia](https://en.wikipedia.org/wiki/Pitch_detection_algorithm) [Wikipedia](https://en.wikipedia.org/wiki/Missing_fundamental)
- Fixes: use an estimator like YIN (de Cheveigné & Kawahara, *J. Acoust. Soc. Am.* 111(4), 2002). It is "based on the well-known autocorrelation method with a number of modifications that combine to prevent errors," with "error rates about three times lower than the best competing methods." Also constrain candidates to the selected tuning's range.

---

## 17. Ear-training concepts (B → A)

**Scale degrees and names.** Tonic, supertonic, mediant, subdominant, dominant, submediant, leading tone (raised 7) / subtonic (♭7).

**Solfège.**
- Movable do: do re mi fa sol la ti. Raised forms: di ri fi si li. Lowered forms: ra me se le te.
- Fixed do (do = C) is common in Romance-language countries.

**Minor keys.** Do-based minor (do re me fa sol le te) and la-based minor (la ti do re mi fa sol) both have advocates. Steve Larson's analysis in *Indiana Theory Review* compares them without a clean winner. [core](https://core.ac.uk/download/pdf/213813379.pdf) [fiveable](https://fiveable.me/ap-music-theory/key-terms/solfege) Default to scale-degree numbers and offer either solfège system as a setting.

**Functional hearing** means hearing each note or chord relative to the tonic. It is the core skill for learning songs by ear.

---

## 18. Jazz and contemporary concepts (A)

**Chord-scale theory.** Levine's *Jazz Theory Book* (Sher, 1995) organizes it as major, melodic-minor, diminished and whole-tone "scale harmony." [Wikipedia](https://en.wikipedia.org/wiki/The_Jazz_Theory_Book) [img-music](https://www.img-music.com/links/pdf/FA_0001096164_13004.pdf)

| Chord (C major) | Scale | Avoid note (half step above a chord tone) |
|---|---|---|
| Cmaj7 | Ionian / Lydian | F (Ionian); none (Lydian) |
| Dm7 | Dorian | B (13)? **Sources disagree**: Levine treats Dorian as having none |
| Em7 | Phrygian | F, C |
| G7 | Mixolydian | C (11) |
| Am7 | Aeolian | F (♭13) |
| Bm7♭5 | Locrian / Locrian ♮2 | C (♭9) in Locrian |
| G7alt / G7♭9 / G7♯11 | Altered / half-whole / Lydian dominant | — |

OMT's chord-scale chapter lists the theory's limitations. [pressbooks](https://viva.pressbooks.pub/openmusictheorycopy/part/jazz/) Present it as one improvisation lens among several, alongside guide-tone lines, arpeggios and blues vocabulary.

**Guide tones.** The 3rd and 7th move by step or hold through ii–V–I: Dm7 (F, C) → G7 (F, B) → Cmaj7 (E, B).

**Reharmonization tools.** Tritone sub, the related ii, secondary dominants, diminished passing chords, backdoor ♭VII7, chromatic ii–Vs, Coltrane cycles, modal interchange.

**Upper-structure triads over a C7 shell.**
- D/C7 = 9 ♯11 13
- A/C7 = 13 ♭9 3
- A♭/C7 = ♭13 1 ♯9
- E♭/C7 = ♯9 5 ♭7
- G♭/C7 = ♭5 ♭7 ♭9

**Modern harmony.**
- **Quartal harmony:** e.g. "So What" voicings; ambiguous quality suits modal jazz and neo-soul.
- **Non-functional harmony:** planing, chromatic mediants, modal vamps, hip-hop/ambient loops.
- **Pop modal interchange:** the ♭VI–♭VII–I "Mario cadence."

**Set theory (overview).**
- Pitch classes 0–11 (t = 10, e = 11); interval classes 0–6; Tn and In.
- Normal and prime form: major and minor triads are both set class 3-11, prime form [0,3,7].
- Interval vector: the major triad is ⟨001110⟩.
- Also: twelve-tone rows and neo-Riemannian P/L/R. All of this maps directly onto the engine's pitch-class arithmetic.

**Microtonality (overview).** 24-TET, 19/31-TET, just lattices, maqam intervals, blue notes between m3 and M3, string bends. The app is 12-TET, so state that, and show bends as cents.

---

## 19. Fretted-instrument theory (B → I)

**Fret maths.**
- Sounding MIDI = open-string MIDI + capo + fret.
- Interval between two fretted notes = (open_j + fret_j) − (open_i + fret_i).
- Spelling always comes from the theory layer, never the fretboard.

| Tuning | Notes (low→high) | MIDI | Adjacent intervals |
|---|---|---|---|
| Guitar standard | E2 A2 D3 G3 B3 E4 | 40 45 50 55 59 64 | 5 5 5 **4** 5 |
| Drop D | D2 A2 D3 G3 B3 E4 | 38 45 50 55 59 64 | 7 5 5 4 5 |
| D standard | D2 G2 C3 F3 A3 D4 | 38 43 48 53 57 62 | 5 5 5 4 5 |
| 7-string | B1 E2 A2 D3 G3 B3 E4 | 35 40 45 50 55 59 64 | 5 5 5 5 4 5 |
| Bass E standard | E1 A1 D2 G2 | 28 33 38 43 | 5 5 5 |
| Bass D standard | D1 G1 C2 F2 | 26 31 36 41 | 5 5 5 |
| Bass drop D | D1 A1 D2 G2 | 26 33 38 43 | 7 5 5 |
| Bass 5-string | B0 E1 A1 D2 G2 | 23 28 33 38 43 | 5 5 5 5 |
| Bass 6-string | B0 E1 A1 D2 G2 C3 | 23 28 33 38 43 48 | 5 5 5 5 5 |
| Ukulele (re-entrant) | G4 C4 E4 A4 | 67 60 64 69 | −7 4 5 |
| Ukulele low-G | G3 C4 E4 A4 | 55 60 64 69 | 5 4 5 |

"Drop A" is ambiguous. It may mean a 7-string tuning (A1 E2 A2 D3 G3 B3 E4) or a 6-string one (A1 E2 A2 D3 F♯3 B3). Treat tunings as editable data and display the resolved notes.

**The B-string offset.** Guitar standard tuning is all P4s except G→B (a M3). Any shape that crosses from G to B shifts up one fret. Octave shapes differ for the same reason:
- E→D and A→G strings: +2 frets.
- D→B and G→E strings: +3 frets.

**Bass in 4ths is symmetric.** Every shape is identical on every string pair, so one shape transposes anywhere. Ukulele's re-entrant G means the engine must sort notes by pitch, not by string index.

**Capo.** A capo at fret n raises everything n semitones. Show "shape G, sounds A" (capo 2). Transposing means adding k semitones and then respelling for the new key.

**Landmarks.**
- Fret 12 = octave; patterns repeat every 12 frets.
- Fret 5 on one string = the next open string. The exception is fret 4 on the G string, which gives the open B.

---

## 20. Spelling and naming conventions the app must get right

1. **One letter per degree.** F major = F G A B♭ C D E; A♯ would repeat A and skip B.
2. **Chords spell from their degrees:**
   - C°7 = C E♭ G♭ **B𝄫**
   - G♯ = G♯ **B♯** D♯
   - F♭ = F♭ A♭ C♭
   - E♭°7 = E♭ G♭ B𝄫 D𝄫
   - E7♯9 = E G♯ B D **F𝄪**

   Offer a "chart spelling" toggle (F𝄪 → G), but default to correct spelling.
3. **Default root names with no key context:** D♭, E♭, F♯/G♭, A♭, B♭. With a key context, use the diatonic spelling.
4. **Chromatic tones by function:**
   - V/V in C contains F♯, not G♭.
   - vii°7/V = F♯ A C E♭.
   - ♭VI = A♭ C E♭.
   - Chromatic passing tones: sharps going up, flats going down.
5. **Never output triple accidentals.** Respell the root instead.
6. **Unicode:**
   - ♭ U+266D, ♮ U+266E, ♯ U+266F [iloveunicode](https://iloveunicode.com/symbols/music)
   - 𝄪 U+1D12A, 𝄫 U+1D12B; quarter-tone 𝄲/𝄳 U+1D132/U+1D133 [Wikipedia](https://en.wikipedia.org/wiki/Flat_%28music%29) [Wikipedia](https://en.wikipedia.org/wiki/Sharp_%28music%29) [symbl](https://symbl.cc/en/unicode/blocks/musical-symbols/)
   - 𝄞 U+1D11E, 𝄢 U+1D122, 𝄋 U+1D10B, 𝄌 U+1D10C [iloveunicode](https://iloveunicode.com/symbols/music)
   - Δ U+0394, ø U+00F8, ° U+00B0

   The U+1Dxxx characters need a bundled font (e.g. Bravura/SMuFL or Noto Music). ASCII fallbacks: b, #, bb, x. [Wikipedia](https://en.wikipedia.org/wiki/Flat_%28music%29) [Wikipedia](https://en.wikipedia.org/wiki/Sharp_%28music%29) [graphemica](https://graphemica.com/blocks/musical-symbols)
7. **Octave label setting:** C4 by default, C3 optional, MIDI number always shown.
8. **Flagged disagreements:** symbol styles, ♭VII vs VII, do- vs la-based minor, bebop minor, the ii7 avoid note, Hirajōshi, accidental scope across octaves, tempo ranges, the Yamaha octave label, "drop A."

---

## 21. Existing resources and licensing

| Resource | Structure / strengths | Licence / reuse |
|---|---|---|
| **Open Music Theory v2** (Gotham, Gullings, Hamm, Hughes, Jarvis, Lavengood, Peterson) | Full undergraduate sequence: fundamentals, counterpoint, form, diatonic/chromatic harmony, jazz (chord symbols, voicings, ii–V–I, substitutions, chord-scale, blues), pop (rhythm, drumbeats, form), 20th-century, orchestration. Embedded audio, playable scores, workbook [pressbooks](https://viva.pressbooks.pub/openmusictheory/) [archive](https://archive.org/details/open-music-theory) | **CC BY-SA 4.0**: adapt with attribution; derivatives must share alike; check per-media credits [pressbooks](https://viva.pressbooks.pub/openmusictheory/) [pressbooks](https://viva.pressbooks.pub/openmusictheory/) |
| **musictheory.net** (Ricci Adams) | Linear lessons, staff → Neapolitan; customizable exercises including **fretboard** identification and ear training [8notes](https://www.8notes.com/school/theory/musictheory/files/about.html) [musictheory](https://www.musictheory.net/exercises) [apple](https://apps.apple.com/us/app/theory-lessons/id493157418) | **Lesson text and still images CC BY [musictheory](https://www.musictheory.net/legal) 4.0** (attribute "by musictheory.net" with a link). Code, player, data files, apps and audio are not CC |
| **Tenuto** | 24 exercises + 6 calculators; standard-tuning fretboard drills | proprietary, paid, iOS only |
| **Teoria** (J. Rodríguez Alvira) | Tutorials, reference, analyses, strongest free dictation/ear training [wares](https://www.wares.gg/apps/teoria) | listed as **CC BY-NC-ND 4.0**: reference only [wares](https://www.wares.gg/apps/teoria) |
| **Hooktheory I/II**, Hookpad, TheoryTab | Pop-first, function-first relative notation. Book I: I/IV/V/vi, melody stability. Book II: sevenths, secondary chords, add9/sus, minor, modes and mixture. [musical-u](https://www.musical-u.com/learn/review-hooktheory-music-theory-book/) [hooktheory](https://www.hooktheory.com/books/two) Crowd-sourced database: TheoryTab's search page reads "Search 81,536 songs," and the Trends page cites "75,000+ songs" | proprietary; public Trends API (check its terms) |
| **Levine, *The Jazz Theory Book*** (Sher, 1995, 522 pp.) | Chord/scale theory, slash chords, bebop/pentatonic, blues, rhythm changes, reharmonization, Coltrane changes [shermusic](https://www.shermusic.com/1883217040.php) [Wikipedia](https://en.wikipedia.org/wiki/The_Jazz_Theory_Book) | copyrighted; facts are usable, text is not |
| **Aldwell & Schachter; Kostka & Payne** | Classical harmony and voice leading | copyrighted; reference only |
| **Gould, *Behind Bars*** | Engraving conventions | copyrighted; authority for rendering |
| **Brandt & Roemer (1976)** | Chord-symbol standardization | copyrighted, out of print |
| **Yousician** | Gamified, mic-based. Guitar "Knowledge" theme (5 levels: alphabet, fretboard, keys & octaves) plus ear trainers | proprietary subscription |
| **Fender Play** | Video courses. Theory is light: notes on strings, fretboard theory, reading tab/rhythm. Reviewers note little on chord construction or scales beyond major/pentatonic | proprietary subscription |

**Gaps the app can fill.**
- Tuning-, capo- and string-count-aware generation with correct spelling.
- Shapes for non-standard tunings, where CAGED breaks.
- Theory applied to the user's *own* songs.
- Pop, hip-hop, jazz and classical in one reference.
- Bass-first content.
- An A/B listening comparison on every page.

---

## 22. Pedagogy and evidence

**Beginner order.** F1–F6 → S1 → S2 → S3/S4 → C1 → C2 → C5 → S5 → C3/C4 → M1 → R2/N1 → FM. Fretboard and ear training start on day one.

**Stumbling points and fixes.**
- E–F/B–C half steps: show keyboard and fretboard side by side.
- Letter count vs semitone count: always show both numbers.
- "Why B♭ not A♯": animate the letter-per-degree rule.
- Relative vs parallel modes.
- The three minor forms.
- Roman-numeral case, and ♭VII.
- 6/8 vs 3/4.
- 9 vs add9; sus vs add.
- Accidentals lasting through the bar.
- Guitar sounding an octave lower than written.
- The B-string offset.

**Research evidence.**
- **Sound before symbol.**
  - Grutzmacher (1987, *Journal of Research in Music Education*): beginners trained on tonal patterns scored significantly higher on aural tonality identification (p < .001) and melodic sight-reading (p < .0001), with no difference in reading recognition. These statistics are reported secondhand from the abstract.
  - MacKnight (1975, *JRME*): tonal-pattern instruction was "superior to note identification teaching techniques," especially for lower-ability students.
  - Haston (2010, *Contributions to Music Education*, n = 20): an aural emphasis "does not hamper… performance skills, and may aid them."
  - Caveat: all three studies concern beginning instrumentalists' reading. None controls for teaching theory concepts.
- **Interleaving.** Wong, Chen & Lim (2021, *Psychology of Music*): without reference songs, "interleaving produced superior musical interval identification than blocking." With songs and singing, the two were comparable. "Most participants were unaware of the benefits of interleaving, and misjudged blocking to be more effective." So mix interval types in drills.
- **App ear training.** Kariuki & Ross (2017; an ERIC conference paper, not peer-reviewed; n = 20, five sessions) found a computerized group scored significantly higher on 11 intervals and 3 chord qualities, with a reported effect size of 1.62. A 2026 *Frontiers in Psychology* scoping review of 35 studies gives no pooled effect. Treat the benefit as promising, not proven.
- **Transfer.** Stambaugh & Nichols (2020, *JRME*): interval identification correlated with pitch-error detection (r = .75), beyond what coursework explained.
- **Hard intervals.**
  - Killam, Lorton & Schubert (1975, *Journal of Music Theory*, reported secondhand): the m6 was least accurate (~55%) and the tritone ~72%. Most errors were confusions with intervals a semitone away, and harmonic intervals were harder than melodic.
  - Samplaski (2004/2005): "the different types of 2nds, 3rds, 6ths, and 7ths cluster strongly together," and the tritone is "the most volatile."
  - Build m6/M6, m7/M7 and tritone comparison drills.
  - The specific claim that the tritone is confused with P4/P5 lacks a direct empirical source.
- **No empirical error studies were found for key signatures or Roman numerals.**

---

## Recommendations

1. **Build the Section 1 model first.** Unit-test every Section 8.1 chord on all 35 roots (7 letters × 5 accidentals, rejecting triple accidentals) and every scale on all 15 key-signature tonics.
2. **Ship the Section 2 prerequisites as page metadata.** This enforces "defined on earlier pages" mechanically.
3. **Defaults:** m / maj7 / m7♭5 / dim7 / aug / sus4 with a jazz-symbol toggle; C4 = middle C plus the MIDI number; scale-degree numbers with optional solfège; correct spelling with a chart-spelling toggle.
4. **Treat tunings as data.** Generate all shapes algorithmically. Teach the B-string offset, bass symmetry, and capo shape-vs-sound explicitly.
5. **Put an A/B listening comparison on every page,** with interleaved quizzes.
6. **Licensing:** adapt musictheory.net lesson text (CC BY 4.0) and OMT2 (CC BY-SA 4.0, kept in a separately licensed bundle). Write original prose for everything else, and have counsel confirm the share-alike scope.
7. **Add a bass-pitch explainer page** (harmonic series → octave errors) and range-limit the detector by tuning.

## Caveats

- Many "rules" here are conventions, and disagreements are flagged inline.
- The Killam percentages, Grutzmacher statistics and the Kariuki & Ross effect size are secondhand; check them against the originals before quoting.
- Hooktheory's own pages give different database sizes (TheoryTab: "Search 81,536 songs"; Trends: "75,000+ songs").
- BPM, phrase-length and genre labels describe tendencies.
- Harmonic-series cent values are computed against 12-TET.
- Licences change (musictheory.net moved from CC BY-NC-SA 2.0 in 2005 to CC BY 4.0 for lessons), so recheck before publication.