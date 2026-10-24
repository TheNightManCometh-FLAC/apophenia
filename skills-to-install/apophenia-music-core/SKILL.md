---
name: apophenia-music-core
description: Use whenever writing or changing Apophenia code that touches pitches, note names, intervals, scales, modes, keys, chords, chord symbols (parsing or display), Roman numerals, tab, tunings, rhythm/durations/tuplets/meters, transposition, MusicXML/MIDI/ChordPro import or export, or any Learn content (course, ear training, reference). Holds the spelling rules, data shapes and known traps.
---

# Apophenia music core

The `:core` module is pure Kotlin/JVM (no Android imports) and is shared by every feature. Correct spelling and structure here is what makes the app trustworthy at the advanced end. Full detail: `docs/reference/music-core-and-theory-design.md` §1–5, `docs/research/music-theory-for-a-theory-engine.md`, `docs/research/music-core-reference-musicxml-advanced-theory-chord-corpus.md`.

## Pitch and interval
- **SpelledPitch** = letter (C..B = 0..6) + alter (−2..+2) + octave. The octave belongs to the letter: B♯3 = MIDI 60, C♭4 = MIDI 59. Derived: `pc = (natural[letter] + alter) mod 12`, `midi = 12*(octave+1) + natural[letter] + alter`, natural = {0,2,4,5,7,9,11}. Never store a bare MIDI number where spelling exists.
- **PitchClass** (0–11) only where spelling genuinely doesn't exist (detector output, raw MIDI in, set theory). Converting to SpelledPitch always requires a context (key, chord, or the default table D♭ E♭ F♯ A♭ B♭).
- **Interval** = (generic number counted inclusively by letters, semitones). Quality derived: perfect class (1,4,5,8,11,12): −1 dim, 0 P, +1 aug; major/minor class (2,3,6,7,9,10,13): −2 dim, −1 m, 0 M, +1 aug. A4 (4,6) ≠ d5 (5,6).
- Frequency = A4ref × 2^((midi−69)/12) × temperament cents offset. A4 ref is a setting (default 440). Octave label setting: C4 default, C3 option, always show MIDI number.

## Spelling rule (the core of correctness)
For degree token d (number n, semitones s) above a spelled root: letter = root.letter + (n−1) mod 7; alter = whatever makes pc == (root.pc + s) mod 12. Never output triple accidentals: respell the root (G♯ major → A♭) unless context forces it (G♯ as V of C♯ minor), then allow doubles.
Traps that must be unit-tested: F major has B♭; C°7 = C E♭ G♭ **B𝄫**; E7♯9 = E G♯ B D **F𝄪**; C7♯9 has **D♯** not E♭; G♯ major has **B♯**; Ger+6 in C = A♭ C E♭ **F♯**; tritone sub of G7 = D♭ F A♭ **C♭**; altered scale has a scale spelling (G♯ A B C D E F♯) and a chord-tone spelling (C D♭ D♯ E F♯ G♯ B♭).

## Degree tokens
`1 b2 2 #2 b3 3 4 #4 b5 5 #5 b6 6 bb7 b7 7 b9 9 #9 11 #11 b13 13`; grammar = optional accidental (`bb b n # x`) + number 1–15. Each maps to exactly one Interval (`bb7`=(7,9), `#11`=(11,18), `b13`=(13,20)). Scales and chords in the catalogs are lists of these.

## Chords — stored as structure, mirroring MusicXML 4.0 `<harmony>`
```
root: SpelledPitch (no octave) | numeral (1–7 + alter + key)
kind: one of the 33 MusicXML kind values (major, minor, augmented, diminished, dominant, major-seventh, minor-seventh, diminished-seventh, augmented-seventh, half-diminished, major-minor, major-sixth, minor-sixth, dominant-ninth, major-ninth, minor-ninth, dominant-11th, major-11th, minor-11th, dominant-13th, major-13th, minor-13th, suspended-second, suspended-fourth, Neapolitan, Italian, French, German, pedal, power, Tristan, other, none)
degrees: ordered [{value, alter, type: add|alter|subtract}]
bass: SpelledPitch? ; poly: second chord? (polychord, horizontal line)
quality: catalog id (alt, 7sus4, … consistent with kind+degrees via one tested table)
typed: the exact text the user typed ; voicing: optional concrete pitches or frets
```
- **Degree arithmetic (spec):** for `alter`/`subtract`, alter is relative to the degree already in the kind; for `add`, alter is relative to a **dominant** chord. Adding maj7 = +1, ♭7 = 0, ♭9 = −1, ♯11 = +1. Getting this wrong silently changes chords on export.
- Implied stack: 9 ⇒ 7; 11 ⇒ 7,9; 13 ⇒ 7,9,11; except 6, 6/9, add, sus (no 7th). 13 chords omit the natural 11 over a major 3rd. Omit the 5th first, then the root (rootless); never the guide tones (3, 7).
- N.C. = kind `none`. On import trust kind+degrees for meaning, `kind@text` only for display.

## Symbols: store structure, render symbols, parse text
- **Storage is ASCII only** (`bb b n # x`, quality ids). Never store ♭♯𝄫𝄪. 𝄪 (U+1D12A) and 𝄫 (U+1D12B) are surrogate pairs in Kotlin/Java strings: `length`, `get(i)`, `substring` break on them. Only the renderer touches them, with code-point APIs.
- **Display styles:** pop (m, maj7, m7♭5, dim7, aug), jazz (−, Δ, ø, °, +), classical/analysis, ASCII (exports). Fixed rules: always write the 7 when an extension is altered; ♭/♯ not −/+ for alterations; `7sus4` not `sus7`; `add9` has no 7th; simplest correct symbol wins.
- Typesetting: superscript extensions, stacked or parenthesised alterations, diagonal slash = bass note, horizontal line = polychord; Roman numerals with stacked figures (V⁶₅), N⁶, It⁺⁶/Fr⁺⁶/Ger⁺⁶. Glyphs: Bravura (staff) and Bravura Text (inline), SIL OFL, unmodified.
- **Parser** returns ranked candidates plus an `ambiguous` list. Pipeline: tokenize chart noise (`|`, `%`, `x2`, `|: :|`, `N.C.`/`NC`/`n`, parenthesised alternates, lyrics) → root + accidental → quality by longest match → extensions/alterations (`(b9,#11)`, `(b9 #11)`, `b9#11`, `+9`/`-9` = ♯9/♭9 in alteration position) → omissions (`no3`, `omit3`) → `/bass`. `C6/9`/`C69` is never a slash chord. Dialects: Pop (C2 = add9, C° = dim triad), Jazz/iReal (official iReal quality list; C2 = sus2; lone C° = dim7; `9sus`, `13sus`), German (H = B♮, B = B♭, Fis/Cis/Es/As; never inferred from a bare "B"), Romance solfège (Do Re Mi Fa Sol La Si). Ambiguous by default: C2, C°, C7+ (7♯5 usually, maj7 in some Latin/European charts), Cmaj, C11 (often 9sus4), lowercase roots. Unparseable text is kept and flagged.
- Transposition moves root and bass by an interval and re-spells for the target key (C→E♭: D7 → F7, not E♯7).

## Rhythm
Durations are exact rationals with dots and a tuplet stack (any n:m, nested). Ticks: 960 per quarter. Time signature = additive groups + denominator (`[[2,2,3],8]` for 7/8, `[[3,3],8]` for 6/8 = 2 beats); tempo = BPM + beat unit; swing ratio is a playback parameter.

## Tab and tuning
- Instrument = string count + tuning (spelled pitch per string, in **string order**, not pitch order — ukulele is re-entrant) + capo + fret count; written-pitch transposition per instrument (guitar/bass written an octave above sounding).
- Every tab note stores **string, fret and pitch together**. Pitch = open string + capo + fret, spelled in context.
- Retuning a part asks: keep notes (re-finger) or keep fingering (pitches move).
- MusicXML: `<staff-tuning line>` counts from the **lowest** string; `<technical><string>` counts from the **highest**. Opposite directions — test it. No palm-mute element (use `<other-technical>`). Accept tab paired as one part with an alternate staff or as two parts. Guitar/bass sounding pitch via `<transpose><octave-change>-1` **or** an octave treble clef, never both. Nested tuplets: cumulative `<time-modification>` plus `<tuplet-actual>/<tuplet-normal>`. Additive meters: `<beats>2+2+3</beats>`. Emit both `<tie>` and `<tied>`.

## Theory content corrections (don't repeat popular errors)
Negative harmony in C maps n → 7 − n; G7 → **Fm6 = Dø7** (not Dm6); Levy never used the term. Barry Harris 6th-dim extra note: A♭ when part of the °7, G♯ as a passing tone. CT°7 keeps the decorated chord's root (on I in C: C D♯ F♯ A). Messiaen modes and harmonic-major modes: store as pitch-class sets with alias names, no single canonical spelling.

## Test fixtures
Every catalog chord and scale on all 35 roots; McGill Billboard (CC0) and ChoCo CC BY 4.0 subsets (attribute in NOTICE; exclude its Chordify, Mozart Piano Sonata and JAAH subsets, which are NC-SA); synthetic fixtures from the iReal Pro quality list. Chordonomicon and other scrapes: local statistics only, never committed.
