> **Status (Oct 21 2026):** the music-core *data model* here (spelled pitch, intervals, chord structure mirroring MusicXML 4.0, symbol storage/rendering/parsing, rhythm) remains the technical reference. Product scope, navigation and sequencing now come from `apophenia-architecture-decisions-2026-10-21.md` and `apophenia-unified-product-plan-2026-10-21.md`; references here to the Workshop, drafts and Stacks map to Library, Project Boards and player Sessions.

# Music core and Theory section — data and design (v2, Oct 20 2026)

v2: chord structure mirrors MusicXML 4.0; parser dialects; SQLite storage; bundled test corpus; theory corrections and sources from the second research report; see `plan-audit-1.md` for the reasoning.

Companion to `practice-app-design.md` (v2). Replaces its section 3.17 and sets the depth for every other feature.

Status: **design only**. Nothing is built or tested. Facts about theory come from the research report "Music Theory for a Theory Engine" (Oct 20 2026), Open Music Theory v2, musictheory.net and Brandt & Roemer's *Standardized Chord Symbol Notation* as cited there. Places where sources disagree are marked **[disputed]**. Things I still need to verify are marked **[verify]** and collected in section 11.

---

## 0. Two rules that apply to the whole app

1. **Full depth everywhere; levels are labels, never gates.** The app is not a beginner course. Every feature handles the advanced case in its data model from day one: double flats and sharps, altered and polychords, compound and odd meters, any tuplet, any tuning, written vs sounding pitch. A level badge (Fundamental / Intermediate / Advanced) only helps orientation and linking. Nothing is hidden or locked behind it.
2. **One music core under every feature.** The tuner, metronome, Workshop chords, lyric chord markers, tab, notation, Reference, Theory, ear training, player, transposition and every export all use the same pitch, interval, chord and rhythm objects. A chord typed into a lyric line, a chord on the Reference fretboard and a chord on a Theory page are the same object, spelled and named by the same code. That's what keeps spelling, naming and symbols consistent, and it's why the core is designed once, here, before any feature is built on it.

---

## 1. Core types (the music core)

Plain Java value classes, immutable, no Android dependencies, so the whole core is tested on the desktop JVM.

### 1.1 Spelled pitch — never a bare number
| Field | Type | Range | Why |
|---|---|---|---|
| `letter` | byte | 0–6 = C D E F G A B | Spelling is letter-based; B♭ and A♯ are different objects |
| `alter` | byte | −2…+2 (𝄫 ♭ ♮ ♯ 𝄪) | Triple accidentals are never produced (rule in 2.4) |
| `octave` | byte | −1…9 | The octave belongs to the **letter**: B♯3 is the same key as C4, C♭4 is the same key as B3 |

Derived, never stored separately: `pc = (natural[letter] + alter) mod 12`, `midi = 12×(octave+1) + natural[letter] + alter`, with `natural = {0,2,4,5,7,9,11}`.

**Pitch class** is a separate type (an int 0–11) used only where spelling genuinely doesn't exist: set theory, MIDI input before it's spelled, the pitch detector's output. Converting a pitch class to a spelled pitch always needs a context (key, chord or "default" table), so the API makes that step explicit.

### 1.2 Interval
| Field | Type | Meaning |
|---|---|---|
| `number` | int | Generic size counted inclusively by letters: 1 unison, 3 third, 9 ninth… (compound allowed) |
| `semitones` | int | Exact size |
| `direction` | sign | Up or down, only where it matters (melodic intervals) |

Quality is **derived**: reference semitones for the simple number (1→0, 2→2, 3→4, 4→5, 5→7, 6→9, 7→11, +12 per octave). Perfect class (1 4 5 and their compounds): −1 dim, 0 perfect, +1 aug. Major/minor class (2 3 6 7 …): −2 dim, −1 minor, 0 major, +1 aug. ±2 beyond that = doubly augmented/diminished (rare, but representable).

So the tritone is two different objects: A4 = (4, 6) and d5 = (5, 6). They sound the same and resolve in opposite directions; the engine keeps them apart.

### 1.3 Degree token
The vocabulary every scale and chord is written in: `1 b2 2 #2 b3 3 4 #4 b5 5 #5 b6 6 bb7 b7 7 b9 9 #9 11 #11 b13 13` and so on. Grammar: optional accidental (`bb b n # x`) + number 1–15. Each token maps to exactly one Interval: `bb7` = (7, 9), `#11` = (11, 18), `b13` = (13, 20). A scale or chord is a list of degree tokens; spelling follows mechanically (2.4).

### 1.4 Duration and rhythm
| Type | Representation | Why |
|---|---|---|
| Duration | Rational (numerator/denominator of a whole note), plus dot count 0–3, plus a tuplet stack | Floats can't represent a triplet eighth exactly; ties and bar-splitting need exact arithmetic |
| Tuplet | `n:m` with a unit (e.g. 3:2 eighths, 5:4 sixteenths, 7:4, 2:3 duplets) and nesting allowed | Not just triplets; nested tuplets exist in real music |
| Time signature | List of additive groups + denominator: `[[3],8]` for 3/8, `[[2,2,3],8]` for 7/8 = 2+2+3, `[[3,3],8]` for 6/8 | Beaming, the metronome's accents and the beat count all come from the groups, so 6/8 is two beats and 7/8 can be 2+2+3 or 3+2+2 |
| Tempo | BPM + the beat unit it refers to (♩, ♩., 𝅗𝅥) | 6/8 at ♩.=60 and ♩=90 are the same speed |
| Feel | Swing ratio (float, e.g. 0.5 = straight, ≈0.67 = triplet swing) and which subdivision it applies to | Swing varies by tempo and style; it's a playback parameter, not notation |
| Position | Ticks at 960 per quarter note | Divisible by 2, 3, 4, 5, 6, 8, 10, 12, 15, 16 and more, so common tuplets land on whole ticks; anything else uses the exact rational |

### 1.5 Tuning, instrument, reference
- **Tuning** = list of spelled open-string pitches, low to high by string order (not by pitch, because ukulele is re-entrant), plus capo fret, plus fret count. Every preset in the main doc is data; "drop A" is shown with its resolved notes because the name is ambiguous **[disputed]**.
- **Written vs sounding:** each instrument profile carries a written-pitch transposition (guitar and bass are written an octave above sounding; piano 0). Notation shows written pitch; audio, MIDI and the tuner use sounding pitch.
- **Reference and temperament:** A4 frequency (default 440, editable), and a temperament (12-TET default; just intonation, Pythagorean and meantone stored as cent offsets per pitch class relative to a chosen tonic). Frequency = reference × 2^((midi−69)/12) × cents offset.

---

## 2. Symbols: storage, rendering and input are three separate problems

This is where most music apps get messy. The rule is: **store structure, render symbols, parse text into structure.** A chord symbol string is never the stored truth.

### 2.1 Storage: canonical ASCII tokens, never Unicode, never display strings
- Accidentals stored as `bb b n # x`; qualities and alterations as fixed ids (`maj7`, `dim7`, `alt`, `b9`). Data files contain no ♭/♯/𝄫/𝄪 characters at all.
- Why: (a) `𝄪` U+1D12A and `𝄫` U+1D12B are outside the Basic Multilingual Plane, so in Java each is a **surrogate pair** (two `char`s). `String.length()`, `charAt()` and substring cuts break on them. Keeping them out of stored data removes that class of bug; only the renderer handles them, with code-point APIs. (b) Search, comparison and sorting become plain string operations. (c) Exports that need ASCII (ChordPro, filenames, MIDI text events) get it for free.

### 2.2 Rendering: style profiles + real typesetting
A chord or Roman numeral is rendered by a **style profile** the user picks once (and can override per draft):

| Element | Pop (default) | Jazz | Classical / analysis | ASCII (exports) |
|---|---|---|---|---|
| minor | m | − | (lower-case numeral) | m |
| major 7 | maj7 | Δ7 / Δ | M7 | maj7 |
| half-diminished | m7♭5 | ø7 / ø | ø7 | m7b5 |
| diminished 7 | dim7 | °7 | °7 | dim7 |
| augmented | aug | + | + | aug |
| ♭VII in minor | ♭VII | ♭VII | VII **[disputed]** | bVII |
| alterations | C7(♭9,♯11) | C7♭9♯11 stacked | figured bass | C7(b9,#11) |

Fixed rules from Brandt & Roemer / OMT regardless of style: always write the 7 when an extension is altered; use ♭/♯ (never −/+) for alterations, because − also means minor; `7sus4`, not `sus7`; `add9` keeps no 7th; the simplest correct symbol wins.

**Typesetting** (custom `Canvas` text layout, not plain `TextView` strings):
- Chord symbol = root at full size, quality at full size, extension as superscript, multiple alterations as a **vertical stack** or in parentheses (style setting), slash bass at full size after a diagonal slash, polychord as two chords over a **horizontal** line (horizontal line = polychord, diagonal slash = bass note; they mean different things).
- Roman numeral = numeral + quality sign as superscript + inversion figures **stacked** (V⁶₅, ii⁴₃), accidentals inside figures, applied chords as `V⁷/V`, Neapolitan as N⁶, augmented sixths as It⁺⁶ / Fr⁺⁶ / Ger⁺⁶.
- Glyphs: **Bravura** for notation on the staff (SMuFL private-use code points, e.g. the accidental glyphs), **Bravura Text** for symbols inline in text (chord names, lyric chord markers, Theory prose). Both SIL OFL, bundled unmodified. Android's system fonts can't be relied on for U+1D12A/B **[verify on the S22 Ultra]**.
- One layout class does all of this, so a chord looks identical in the lyric editor, the chord grid, the Reference, a Theory page and the notation's chord row.

### 2.3 Input: a tolerant parser that returns candidates
Users type chords in lyrics (`[F#m7b5]`), the chord grid and search. The parser accepts every common spelling: `Cmaj7 CM7 CΔ7 CΔ Cma7`, `Cm7 C-7 Cmin7 Cmi7`, `Cm7b5 Cø Cø7 Cm7(b5)`, `C°7 Cdim7 Co7`, `C7(b9,#11) C7b9#11 C7-9` (− read as ♭ in an alteration position only), `C/E`, `C5`, `Csus Csus4`, `C6/9 C69`, `Bb B♭ A# A♯`, `F##`, `Ebb`.
- Output is a **ranked list of parses**, not one answer, because some text is genuinely ambiguous: `C2` (sus2 or add9 in pop usage **[disputed]**), `C-` (minor), `Cmaj` (major triad), `CM` (major), `C7+` (aug7 in some books). The top parse is used; ambiguity shows a small "did you mean" chip once.
- Unparseable text is kept as free text and flagged, never silently dropped.
- **Dialects** (setting, per user and overridable per draft), because real charts disagree:
  - **Pop** (default): `C2` = add9, `C°` = dim triad, `C7+` = 7♯5 (flagged), `Cmaj` = major triad (flagged).
  - **Jazz / iReal**: iReal Pro's published quality list (`^ - h o + sus 7alt 69 -^7 …`), `C2` = sus2, `C°` written alone in a jazz chart = dim7, sus extensions before "sus" (`9sus`, `13sus`).
  - **German**: H = B♮, B = B♭, `Fis Cis Es As Ges Des`, `Hm`. Never inferred from a bare "B"; only by setting or when an H appears.
  - **Romance solfège**: Do Re Mi Fa Sol La Si (fixed do), e.g. `Sim`, `Lam7`, `Reb`.
- **Pipeline:** tokenize the chart line first (bar lines `|`, repeats `%` `x2` `|: :|`, `N.C.`/`NC`/`n`, parenthesised alternates, lyrics), then root + accidental, quality by longest match, extensions/alterations (`(b9,#11)`, `(b9 #11)`, `b9#11`, `+9`/`-9` = ♯9/♭9), omissions (`no3`, `omit3`), `/bass`, emitting the structure in §4 plus an `ambiguous` list for `C2 C° C7+ Cmaj C11`, lowercase roots.
- `C6/9` and `C69` must never be read as a slash chord with bass 9.
- Search normalises everything to the ASCII tokens, so typing `Bb` finds `B♭`.

### 2.4 Spelling algorithm (the core of correctness)
For degree token *d* (number *n*, semitones *s*) above a spelled root:
1. Letter = root letter + (n − 1) mod 7.
2. Alter = whatever makes the pitch class equal (root pc + s) mod 12, expressed as −2…+2.
3. If that needs a triple accidental, the object is "theoretical" on that root: the engine respells the root enharmonically (G♯ major → A♭ major) unless the context forces it (G♯ major as V of C♯ minor), in which case it keeps the spelling and allows the double accidentals.

That one rule gives F major its B♭, Cdim7 its B𝄫, E7♯9 its F𝄪 and C7♯9 its D♯ (not E♭). A "chart spelling" toggle may simplify F𝄪 → G for reading, but the stored object never changes.

**Root spelling without a key** (a bare chord in the scratch pad): default roots C D♭ D E♭ E F F♯ G A♭ A B♭ B. **With a key** (draft key set): diatonic spelling first, then functional spelling for chromatic chords (V/V in C is D7 with F♯; ♭VI is A♭; vii°7/V is F♯ A C E♭).

---

## 3. Catalog data (assets, versioned JSON)

Shipped in `assets/theory/`, each file with a `formatVersion`. Validated by a desktop tool at build time (section 9). Sizes are small (hundreds of KB total).

### 3.1 `scales.json` — one entry per scale or mode
```json
{ "id": "dorian",
  "names": [{"text":"Dorian","primary":true},{"text":"Dorian mode"}],
  "degrees": ["1","2","b3","4","5","6","b7"],
  "family": "major", "modeOf": "major", "modeIndex": 2,
  "characteristic": ["6"],
  "chordFit": ["m7","m6","m9","m13"],
  "spellingKind": "heptatonic",
  "level": "I", "tags": ["modal","funk","jazz"],
  "variants": null, "disputed": null,
  "pageNotes": "theory/scales/dorian.page" }
```
- `spellingKind`: `heptatonic` (one letter per degree, unique spelling), or `degreeLabelled` for 5-, 6-, 8-note scales whose spelling comes from the degree tokens (whole tone, octatonic, blues, bebop).
- `variants` holds versions sources disagree on (bebop minor: Dorian+♮3 vs Dorian+♮7; Hirajōshi versions), each with its own degrees and a source note, so neither is silently picked.
- Contents: all modes of major, melodic minor and harmonic minor (21), the three minors, pentatonics, blues (minor and major), whole tone, both octatonics, chromatic, bebop family, harmonic major and its modes **[verify names]**, double harmonic, Hungarian minor and major, Phrygian dominant, Neapolitan major and minor, Persian, augmented hexatonic, suspended pentatonic, Japanese scales (with variants), Ukrainian Dorian; non-12-TET systems (maqam, raga) only as labelled approximations.

### 3.2 `chords.json` — one entry per chord quality (roots are applied at runtime)
```json
{ "id": "7b9",
  "degrees": ["1","3","5","b7","b9"],
  "essential": ["3","b7","b9"],
  "omitOrder": ["5","1"],
  "implies": [], "impliedSeventh": true,
  "family": "dominant-altered",
  "symbol": {"ext":"7","alts":["b9"],"base":"dom"},
  "scaleFit": ["half-whole","phrygian-dominant","altered"],
  "level": "A", "tags": ["jazz","minor-ii-V"],
  "enharmonicTwins": [], "pageNotes": "theory/chords/7b9.page" }
```
- `symbol` is the **structure** the renderer and parser share (quality base, extension, ordered alterations, adds, omits, sus), never a string.
- `essential` and `omitOrder` drive the voicing generator: drop the 5th first, then the root (rootless), never the guide tones.
- `enharmonicTwins` records the ambiguous identities the naming engine must offer as alternatives: C6 = Am7/C, Cm6 = Aø7/C, Csus2 = Gsus4/C, C°7 = E♭°7 = G♭°7 = A°7, C+ = E+ = G♯+, C7♭5 ≈ G♭7♭5, C/D ≈ D9sus4.
- Contents: every row of the research report's chord table (triads, sus, power, 6, m6, 6/9, m6/9, all sevenths including mMaj7, ø7, °7, aug7, augMaj7, 7♭5, 7sus4; 9/11/13 in major, minor and dominant forms; maj7♯11; 7♭9, 7♯9, 7♯11, 7♭13, 7alt; add9, madd9, add11, add2/add4; no3/no5) plus combinations generated from the grammar (13♭9, 7♯9♭13, m11♭5 …) rather than listed one by one. Slash chords, polychords, quartal, quintal and cluster voicings are structures, not catalog rows.

### 3.3 Other catalogs
- `progressions.json`: Roman-numeral sequences with names, genre tags and variants (axis I–V–vi–IV, '50s, ii–V–I major and minor, turnarounds, circle, 12-bar blues and quick-change and minor blues, Andalusian, Mixolydian rock, minor plagal, backdoor, Pachelbel, royal road, minor loops, Phrygian vamp, Coltrane changes, omnibus **[verify]**).
- `tunings.json`: the presets, with resolved notes.
- `temperaments.json`: cent offsets per pitch class.
- `meters.json` and `grooves.json`: named additive meters and rhythm patterns for the metronome (son clave 3-2/2-3, four-on-the-floor, backbeat, half-time, common odd-meter groupings).
- `techniques.json`: tab techniques as semantic ids (bend with target in cents, release, pre-bend, slide in/out, hammer, pull, tap, vibrato, palm mute, dead note, natural/artificial/pinch harmonic, let ring), each with house-style render options.

---

## 4. What a chord stores when the user writes one

The structure mirrors MusicXML 4.0's `<harmony>` so import/export is a direct mapping, not a translation:
```json
{ "root": {"l":4,"a":0},            // spelled root, no octave (or "numeral" instead, below)
  "kind": "dominant",               // one of MusicXML's 33 kind values
  "degrees": [ {"v":9,"alter":-1,"type":"add"}, {"v":11,"alter":1,"type":"add"} ],
  "bass": {"l":6,"a":-1},           // spelled bass, optional
  "poly": null,                     // second chord for a polychord (arrangement vertical)
  "quality": "7",                   // our catalog id (covers alt, 7sus4, etc. that MusicXML spells via kind+degrees)
  "typed": "G7(b9,#11)/Bb",         // exactly what the user typed
  "voicing": null }                 // optional concrete pitches or frets
```
- **Degree arithmetic follows the spec:** for `alter`/`subtract`, the alteration is relative to the degree already in the chord's kind; for `add`, it's relative to a *dominant* chord (so adding a maj7 is +1, adding ♭9 is −1, adding ♯11 is +1). Getting this wrong silently changes chords on export.
- `quality` and `kind`+`degrees` are kept consistent by one mapping table, tested both ways. On import, `kind`+`degrees` are trusted for meaning and `kind@text` only for display, because many writers emit text that doesn't match the degrees.
- **Roman numerals** use the MusicXML 4.0 `<numeral>` shape (numeral 1–7, alter, key) for analysis rows; "V7/ii" is two stacked harmony groups, as the spec encodes it.
- **N.C.** is kind `none`.
- **Transposition** moves root and bass by an interval and re-spells against the target key (C→E♭: D7 becomes F7, not E♯7), reporting any chord that became theoretical.

## 5. Engine functions (all pure, all tested)

1. **Spell** a scale, chord or interval on any of the 35 roots (7 letters × 5 accidentals).
2. **Name notes**: given notes (with bass), rank candidate chords. Score: bass is root, complete triad/seventh core, diatonic to the key; minus points per alteration/add/omission; simplest symbol wins; slash chord when the root isn't in the bass; always return runners-up (research report 8.3). Used by MIDI input, the fretboard "what is this shape?" tool and play-it-in.
3. **Roman analysis** of a chord sequence against the draft key: diatonic numerals, secondary dominants and leading-tone chords (V/x, vii°7/x), modal mixture, ♭II/N6, augmented sixths (recognised by spelling, ♭6 + ♯4, not by sound, since Ger+6 and a dominant 7 are the same keys), tritone subs, chromatic mediants; unanalysable chords are marked as such rather than forced.
4. **Key suggestion** from a chord list (ranked, with the borrowed chords explained).
5. **Chord-scale suggestions** per chord (catalog `scaleFit`), with avoid notes shown **[disputed for Dorian 13]**.
6. **Voicing generator** for any tuning and capo: all playable voicings within a fret span, honouring `essential`/`omitOrder`, with families (root position, inversions, drop-2, drop-3, shells, rootless, triads on string sets, root–fifth–octave and arpeggio shapes for bass). This replaces CAGED, which only exists in 6-string standard.
7. **Fingering chooser** for pitches entered from staff, piano or MIDI: all positions per pitch, then the lowest-cost path (hand position changes, stretch, string changes); user overrides stick **[method to research]**.
8. **Voice-leading view**: guide-tone lines (3rds and 7ths) through a progression, common tones, and style-labelled rule checks (parallel 5ths flagged only when the style is set to common-practice).
9. **Set-class tools**: normal form, prime form, interval vector, Tn/In, for the post-tonal pages and as an advanced lens on any chord.
10. **Harmonic series and temperament math** for the acoustics pages and the tuner.

---

## 6. The Theory section — plan

### 6.1 Organisation: a reference with hubs, not a course
The research's curriculum order becomes **prerequisite links**, not a path. Fourteen hubs, each a browsable index:

1. Pitch, tuning and acoustics
2. Intervals
3. Scales and modes
4. Keys
5. Chords (with chord symbol grammar)
6. Diatonic harmony and function
7. Chromatic harmony
8. Jazz harmony
9. Contemporary, modal and post-tonal
10. Rhythm, meter and groove
11. Melody and counterpoint
12. Form
13. Notation and tab
14. The fretboard (tuning-aware theory)

Search reaches any page directly; every term in any page links to its own page; a "comes before this" strip shows prerequisites for anyone who wants them.

### 6.2 Two kinds of page
- **Generated item pages** — one per catalog entry (every interval incl. compound, every scale and mode, every chord quality, every named progression, every tuning). Template + a short hand-written `pageNotes` file (sound, use, history, misconceptions). This is how hundreds of pages stay correct: the formula, spellings, diagrams and audio are generated from the catalog; only the commentary is written.
- **Concept pages** — hand-written, for ideas that aren't a single object: the tritone, enharmonic spelling, function, cadences, modulation, voice leading, chord-scale theory, upper structures, reharmonisation, set theory, the harmonic series and why phones mis-hear bass, written vs sounding pitch, the B-string offset, meter vs rhythm, swing, etc.

### 6.3 Every page, same parts (all depths)
1. **Definition** — exact, terse, using only linked terms.
2. **Structure** — degree tokens, semitones, interval stack, and the spelled example; for chords also essential vs omittable tones and every symbol variant across styles.
3. **Interactive view** — piano, staff (correct accidentals, double accidentals included), and fretboard of the current tuning and capo; change root and everything regenerates, including correct spelling on awkward roots (show G♯ major's B♯, or the respelling to A♭).
4. **Hear it** — the object, its notes one by one, and **A/B comparisons** chosen per page (dim vs half-dim vs dim7; ♯11 vs natural 11 over a major 3rd; Dorian vs Aeolian over the same drone; German sixth vs the dominant 7th it sounds like, then each resolving).
5. **In context** — where it occurs: in which scales, which keys' diatonic sets, which progressions; for chords, the chord-scale fit and the voicings for this tuning.
6. **In your music** — every place it appears in the user's Workshop (fragments, drafts, songs), found by querying the core objects, e.g. "you've used ø7 in 3 drafts".
7. **Disputes** — where sources or styles disagree, stated plainly with each option.

### 6.4 Coverage — the full advanced scope
Everything in the research taxonomy (sections 3–19 of the report), including at the advanced end:
- **Tritone** as its own concept page: A4 vs d5 spelling and resolution, the tritone inside dominant 7ths (3 and ♭7), its symmetry (it inverts to itself), tritone substitution, the German sixth as its classical ancestor, the °7 chord as two interlocked tritones, the octatonic scale's tritone symmetry, the Locrian ♭5, its history as a forbidden interval.
- Chromatic harmony: secondary dominants and leading-tone chords, tonicisation vs modulation (pivot, direct, common-tone, enharmonic via °7 and Ger+6), modal mixture, Neapolitan, Italian/French/German sixths, chromatic mediants, neo-Riemannian P/L/R and the Tonnetz, pedal points.
- Jazz: chord-scale theory and its critiques, avoid notes, guide tones, tritone subs and related ii, backdoor, diminished passing chords, upper-structure triads, rootless and drop voicings, quartal "So What" voicings, Coltrane changes, rhythm changes, reharmonisation, polychords.
- Contemporary and post-tonal: non-functional harmony, planing, modal vamps, pop modal interchange (♭VI–♭VII–I), quartal/quintal/cluster harmony, set theory, twelve-tone rows, polytonality, microtonality overview, just intonation lattices.
- Rhythm: tuplets of any ratio and nesting, additive and irregular meters, polyrhythm vs polymeter, hemiola, metric modulation, swing ratios, groove vocabulary (backbeat, half-time, ghost notes, clave, four-on-the-floor, trap hi-hat subdivisions).
- **Advanced topics now sourced** (second research report):
  - **Negative harmony:** reflection around the axis between the tonic and fifth (in C, between E♭ and E). Pitch-class map n → (2T + 7 − n) mod 12; spelling-preserving letter rule (letter → (4 − letter) mod 7 with the per-letter alter correction) so reflections come out correctly spelled. In C: C → Cm, G7 → **Fm6 = Dø7** (not "Dm6"), Dm7 → Gm7 = B♭6, Fmaj7 → E♭maj7. Three framings kept distinct: Ernst Levy's dualist polarity theory (*A Theory of Harmony*, 1985 — he never says "negative harmony"), Steve Coleman's symmetrical movement, Jacob Collier's popular reharmonisation device (2017 June Lee interview). Naming the result by reflected root vs conventional root is a display option.
  - **Barry Harris 6th-diminished:** major 6th-dim scale C D E F G G♯/A♭ A B = C6 + B°7; minor version with E♭. Harmonised by alternating 6 and °7 chords (C6 – D°7 – C6/E – F°7 – C6/G – A♭°7 – Am7 – B°7); the °7 set equals a rootless G7♭9; borrowing and drop-2 movement. The extra note is spelled A♭ when it belongs to the °7 chord and G♯ as a passing tone from G to A. Sources: Kingstone, *The Barry Harris Harmonic Method for Guitar* (2006); workshop transcriptions (secondary).
  - **Omnibus progression:** chromatic contrary motion (bass and another voice) prolonging V7, converging or diverging, with the middle chords' enharmonic spelling the contested point. Sources: Open Music Theory v2 (Hughes, CC BY-SA 4.0); Yellin, *The Omnibus Idea* (1998); Telesco, *Music Theory Spectrum* 20/2 (1998).
  - **Common-tone °7:** keeps the decorated chord's root; on I in C: C–D♯–F♯–A (♯ii°7), on V: G–A♯–C♯–E (♯vi°7); distinguished from vii°7/x by containing the next chord's root and resolving by neighbour motion. Lead sheets often write it with the common tone as the symbol root (C°7), so the analysis layer re-spells it. Source: Open Music Theory v2.
  - **Messiaen's modes of limited transposition** (*Technique de mon langage musical*, 1944): seven modes stored as pitch-class sets with transposition counts (2, 3, 4, 6, 6, 6, 6) and a display-spelling policy; no single canonical spelling.
  - **Harmonic-major modes:** primary names plus "also called" aliases (Dorian ♭5, Phrygian ♭4, Lydian ♭3, Mixolydian ♭2, Lydian augmented ♯2, Locrian 𝄫7); no name asserted as standard.
  - Still unsourced, written only after checking: Lydian Chromatic Concept (overview), classical dominant 9ths, "Hindu scale" naming.

### 6.5 Page format and authoring
- One text file per page under `assets/theory/pages/`, front-matter (id, title, hub, level, prerequisites, related) + body in a small markup with directives:
  - `[[tritone]]` — link to a page by id.
  - `{{chord q=dim7 root=C views=piano,staff,fret audio=block,arp}}` — a generated example.
  - `{{compare chord:dim chord:m7b5 chord:dim7 root=B}}` — an A/B player.
  - `{{scale id=dorian drone=true}}`, `{{progression id=backdoor key=C}}`, `{{table src=chords filter=family:dominant-altered}}`.
  - `{{dispute}}…{{/dispute}}` and `{{yourmusic q=m7b5}}`.
- Prose and accidentals written in ASCII tokens (`Bb`, `C#`, `bb7`) and rendered with the real glyphs, same as stored data.
- **Prose source and licence:** written originally by me and checked against the references. Adapting is allowed: Open Music Theory v2 (CC BY-SA 4.0) can be adapted into the GPLv3 app under Creative Commons' one-way compatibility declaration of 8 October 2015, with attribution and a modification notice, after checking each figure's own licence; musictheory.net lesson text is CC BY 4.0 (attribute "by musictheory.net" with a link). Teoria (NC-ND), Hooktheory, Levine, Kostka-Payne, Aldwell & Schachter and *Behind Bars* are used as references for facts only.

---

## 7. The same depth in every other feature

| Feature | What full depth means there | Core pieces it uses |
|---|---|---|
| **Tuner** | Cents to 0.1, A4 reference setting, temperament option (show the just/Pythagorean target relative to a chosen tonic), spelled note names in the draft's key (shows B♭ in F, A♯ in B), octave label setting (C4 default, C3 option, MIDI number always shown **[disputed convention]**), detector range limited to the tuning's range to cut octave errors | Spelled pitch, tuning, temperament |
| **Metronome** | Additive meters with group accents (7/8 = 2+2+3), compound meters counted in dotted beats, any subdivision incl. 5 and 7, polyrhythm layers (3:2, 4:3, 5:4), swing ratio, metric modulation between sections, beat-unit-aware BPM, grooves from `grooves.json` | Duration, time signature, tempo, feel |
| **Workshop chords, chord grid, lyric markers** | Full symbol grammar in and out, style profile per user/draft, ambiguity chips, Roman numerals under the chord grid when a key is set, functional colouring (tonic/predominant/dominant/borrowed/secondary), transposition with correct re-spelling | Chord structure (§4), parser, renderer, Roman analysis |
| **Tab** | Techniques stored semantically (bends in cents incl. quarter-tone bends, harmonics with node fret, slides, taps, mutes), any string count, re-entrant tunings, capo; note row with names, degrees or intervals spelled correctly | Spelled pitch, tuning, techniques |
| **Sheet music** | Double accidentals, courtesy accidentals, written vs sounding pitch per instrument, additive-meter beaming, **any tuplet ratio incl. nesting in the data model** (rendering starts with triplets and quintuplets; more ratios render as bracket + number), chord symbols with stacked alterations, Roman-numeral analysis row with stacked figures | Duration, tuplets, symbols, Bravura |
| **Reference** | Every catalog scale/chord on the fretboard of any tuning, all voicing families, chord-scale fit, interval maps from any note | Catalog, voicing generator |
| **Ear training** | Every simple and compound interval melodic and harmonic, all chord qualities incl. extended and altered, inversions, modes over a drone, progressions with secondary dominants and borrowed chords, cadence types, rhythmic dictation in odd meters; interleaved by default (the research found interleaving beats blocked practice for interval ID); extra weighting for the intervals people confuse most (m6/M6, m7/M7, tritone) | All catalogs, sample player |
| **Player** | Pitch shift in cents, not just semitones, for songs tuned off A440; loop lengths in bars of any meter once a tempo map is set | Tempo, meter, cents |
| **MIDI in/out** | MIDI loses spelling, so incoming notes are spelled from the draft's key and the current chord; MIDI export keeps sounding pitch; time signatures with additive groups are written as their sum | Pitch class → spelled pitch with context |
| **MusicXML 4.0** | Spelling via `<step>`/`<alter>` (accept wider/decimal alters, flag them); `<harmony>` = root or `<numeral>` + kind + degrees + bass, polychords as stacked groups; tab via TAB clef, `<staff-details>` tuning (line 1 = **lowest** string) and `<technical><string>/<fret>` (string 1 = **highest**) — opposite numbering, tested; techniques hammer-on/pull-off/bend/tap/harmonic/slide, palm mute as `<other-technical>` (no element exists); tab paired with notation as one part with an alternate staff or as two parts (import both); guitar/bass sounding pitch via `<transpose>` octave-change −1 **or** an octave treble clef, never both; tuplets via cumulative `<time-modification>` plus `<tuplet>` with `tuplet-actual`/`tuplet-normal` on nested ones; additive meters as `<beats>2+2+3</beats>`; both `<tie>` and `<tied>` | Everything |
| **ChordPro** | ASCII chord names from the ASCII style profile; ChordPro has no stacked alterations, so `C7(b9,#11)` | Renderer, ASCII profile |

**Revision to the main design doc:** its notation scope said "no tuplets beyond triplets". That's replaced by the row above: the data model stores any tuplet; the renderer starts simpler.

---

## 8. Storage summary

| Data | Where | Format |
|---|---|---|
| Catalogs (scales, chords, progressions, tunings, temperaments, meters, grooves, techniques) | APK `assets/theory/` | JSON, `formatVersion`, ASCII tokens only |
| Theory pages | APK `assets/theory/pages/` | Text with front-matter and directives, compiled to one index at build time |
| User data: ideas, parts, versions, folders, drafts, sections, links, practice items, sessions | App database | **SQLite** (Android built-in), one table per object type, chord/note/rhythm objects as structured columns or JSON columns in ASCII tokens, full-text index over titles, lyrics, notes and transcripts, versioned migrations |
| Audio takes | App files | WAV, referenced by id from the database |
| Backups | A folder the user picks (system picker) | Zip of database + audio, manual and scheduled; restore tested |
| User settings (style profile, parser dialect, octave label, temperament, A4, solfège system) | App settings | Small JSON |
| Fonts | APK assets | Bravura.otf + Bravura Text.otf, unmodified, OFL text included |

## 9. Tests (desktop JVM, before any UI)

1. **Spelling sweep:** every chord quality and every scale on all 35 roots; no triple accidentals; theoretical cases respelled or flagged; fixture checks for the known traps (F major B♭, C°7 B𝄫, E7♯9 F𝄪, C7♯9 D♯, G♯ major B♯, Ger+6 in C spelled A♭ C E♭ F♯, tritone sub D♭7 with C♭).
2. **Interval quality table:** every (number, semitones) pair to quality and back; inversion sums (numbers to 9, semitones to 12).
3. **Parser round trip:** render → parse returns the same structure for every catalog chord in every style and dialect; ambiguous inputs return their known alternatives.
3a. **Real-world corpus:** McGill Billboard (CC0, Harte-style labels) and the CC BY 4.0 subsets of ChoCo (attributed in NOTICE; its Chordify, Mozart Piano Sonata and JAAH subsets are NC-SA and excluded) bundled as fixtures; Harte labels converted to our structure and back; synthetic fixtures generated from iReal Pro's published quality list. Chordonomicon (CC BY-NC on its dataset card) and other scrapes used locally for spelling-frequency statistics only, never committed.
3b. **MusicXML round trip:** every catalog chord → `<harmony>` → back, including the add-relative-to-dominant arithmetic; tab string/line numbering; nested tuplets diffed against the spec's official nested-tuplet example.
4. **Naming:** the enharmonic-twin list resolves with the expected ranking and runners-up.
5. **Roman analysis:** fixtures for secondary dominants, mixture, N6, the three augmented sixths, tritone subs, backdoor.
6. **Rhythm arithmetic:** tuplets, nested tuplets, dots, ties across barlines, additive meters sum correctly; 960-tick grid exact for the common ratios.
7. **Voicing generator:** every output is playable within the span, contains the essential tones, and spells correctly; bass shapes identical across string pairs in all-fourths tunings; the B-string offset appears in guitar standard.
8. **Page validator** (fails the build like Independent Video's IconRefsTest): every `[[link]]` resolves, every directive references a real catalog id, prerequisites form no cycles, every catalog item has a page.
9. **Code-point safety:** a test that renders and measures strings containing 𝄪/𝄫 with code-point iteration, so a stray `charAt` can't slip in.

---

## 10. Build placement

The music core (§1–5, catalogs, tests) moves to the **start of phase 0.2**, before the Workshop chord grid, because the chord grid, lyric markers and transposition all depend on it. The Theory section pages ship in 0.3 with the Reference, as planned. Ear training's advanced levels come with 0.9.

---

## 11. Still unknown / to verify

Resolved by the second research report: MusicXML details (§4, §7), sources for negative harmony, Barry Harris, omnibus, CT°7 and Messiaen (§6.4), harmonic-major names (§6.4, as aliases), CC BY-SA → GPLv3 (§6.5), a test corpus (§9).

Still open:
1. Whether Android 16 on the S22 Ultra renders U+1D12A/U+1D12B in system fonts (probe P0).
2. How MuseScore 4, Dorico, Flat, Guitar Pro and TuxGuitar actually import/export `<degree>`, `<numeral>`, polychords, bends and harmonics — settled by a round-trip fixture suite with files exported from those apps.
3. Fingering-cost method for the tab chooser (Sayegh 1989 and later work; what Guitar Pro / TuxGuitar / MuseScore do).
4. Engraving rules for the notation subset (beaming per additive meter, rest grouping, tuplet brackets): *Behind Bars*.
5. Page references in Levy, Yellin, Telesco, Kostka-Payne and Aldwell & Schachter before any are cited in app text.
6. Licences of Isophonics, the Rock Corpus and Weimar (ODbL) before any of their data goes into the repo.
7. Secondhand statistics in the first report's pedagogy section (Killam et al., Grutzmacher).
