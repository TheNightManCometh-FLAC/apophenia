# Music-Core Reference: MusicXML 4.0 Encoding, Advanced Theory Definitions, and a Real-World Chord-Symbol Corpus

**Bottom line:** Build the engine on MusicXML 4.0. Its `<harmony>` model is a root (or a 4.0 `<numeral>`), plus one of exactly 33 `kind` values, plus `<degree>` add/alter/subtract edits, plus `<bass>`. That model can represent every chord symbol you listed. Use the McGill Billboard data (CC0) and the CC BY 4.0 parts of ChoCo for bundled test fixtures. Chordonomicon (CC BY-NC 4.0 on Hugging Face) and iReal/Ultimate Guitar scrapes should stay in local development only. CC BY-SA 4.0 text from Open Music Theory can legally go into a GPLv3 app: Creative Commons declared one-way compatibility on 8 October 2015.

## TL;DR

- **MusicXML 4.0:** `<harmony>` = (`<root>` | `<numeral>` | deprecated `<function>`) → `<kind>` (required, 33 values) → `<inversion>`? → `<bass>`? → `<degree>`*. These groups can repeat for polychords and secondary functions. 4.0 added `<numeral>`, `<bass-separator>`, the `arrangement` attribute, bend `shape`, release `offset` and `double@above`. It deprecated `<function>`. Tab, tuplets, ties, beams and additive meters use well-defined elements that predate 4.0. There is **no `<palm-mute>` element**.
- **Theory:** Several popular claims need correcting before they go into app content:
  - The negative-harmony image of G7 in C is **Fm6 = Dø7** (pitches C–A♭–F–D), not "Dm6".
  - Levy never uses the term "negative harmony".
  - Barry Harris's major 6th-diminished scale is C D E F G G♯/A♭ A B (= C6 + B°7). Sources spell the added note both as ♭6 and ♯5.
  - CT°7 chords keep the root of the chord they decorate (in C: C–D♯–F♯–A before I; G–A♯–C♯–E before V).
- **Corpus and licensing:**
  - Safe to bundle: McGill Billboard (CC0, Harte-style labels) and ChoCo (CC BY 4.0 except its Chordify, Mozart Piano Sonata and JAAH subsets, which are CC BY-NC-SA 4.0). ChoCo has 20,080 JAMS files and 20,530 Harte-converted chord annotations.
  - Weimar Jazz Database is ODbL. Keep it local or attribute it carefully.
  - Chordonomicon's licensing conflicts across sources: CC BY-NC 4.0 on Hugging Face, Apache-2.0 on the GitHub code repo, CC BY 4.0 on the arXiv paper. Treat it as NC and local-only.
  - The iReal Pro published quality list is the best single spec of jazz-chart spellings.

---

# PART 1 — MusicXML 4.0

## Key Findings

- The 4.0 reference lives at `https://www.w3.org/2021/06/musicxml40/` (Community Group final report). Element pages follow the pattern `.../musicxml-reference/elements/<name>/` and data types follow `.../musicxml-reference/data-types/<name>/`. Example files are at `.../musicxml-reference/examples/`, and the schema listing is at `.../listings/musicxml.xsd/`. The `w3c.github.io/musicxml/` mirror has the same content.
- The tutorial states directly: "MusicXML 4.0 supports 33 different `<kind>` element values." [w3](https://www.w3.org/2021/06/musicxml40/tutorial/chord-symbols-and-diagrams)
- The tablature tutorial notes that `<harmonic>` and `<bend>` "have sporadic support in current MusicXML software", whereas `<hammer-on>` and `<pull-off>` "are supported in many applications". [w3](https://www.w3.org/2021/06/musicxml40/tutorial/tablature) Plan for lossy round trips on bends and harmonics.

### Spec page index

| Topic | URL |
|---|---|
| `<harmony>` | https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/harmony |
| `<kind>` | https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/kind |
| kind-value (33 values) | https://www.w3.org/2021/06/musicxml40/musicxml-reference/data-types/kind-value/ |
| `<bass>` | https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/bass |
| harmony-arrangement | https://www.w3.org/2021/06/musicxml40/musicxml-reference/data-types/harmony-arrangement |
| `<degree-alter>` / `<degree-type>` | https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/degree-alter , .../elements/degree-type |
| `<numeral>` | https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/numeral |
| `<function>` (deprecated) | https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/function |
| Chord-symbol tutorial | https://www.w3.org/2021/06/musicxml40/tutorial/chord-symbols-and-diagrams |
| Tablature tutorial | https://www.w3.org/2021/06/musicxml40/tutorial/tablature |
| `<bend>`, `<harmonic>`, `<tap>`, `<transpose>`, `<time>`, `<beam>`, `<accidental>`, `<tie>`, `<tied>`, `<slide>`, `<offset>` | https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/(name) |
| `<tuplet>`, `<tuplet-actual>`, `<tuplet-normal>`, `<time-modification>`, `<normal-type>` | https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/(name) |
| Examples: tuplet (Regular), tuplet (Nested), tuplet-dot, time-modification, TAB Clef, artificial | https://www.w3.org/2021/06/musicxml40/musicxml-reference/examples/ |
| 4.0 change list | https://www.w3.org/2021/06/musicxml40/version-history/40/ |

## 1(a) Chord symbols — `<harmony>`

**Content model (4.0, in order):**
1. One or more harmony-chord groups. Each group contains:
   - exactly one of `<root>`, `<numeral>` or `<function>` (function is deprecated)
   - `<kind>` (required)
   - `<inversion>` (optional)
   - `<bass>` (optional)
   - `<degree>` (zero or more)
2. `<frame>` (optional)
3. `<offset>` (optional)
4. `<footnote>` (optional), then `<level>` (optional)
5. `<staff>` (optional)

The spec says root vs numeral "is an either/or choice to avoid data inconsistency." [w3](https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/harmony)

**`<harmony>` attributes:**
- `arrangement`: horizontal | vertical | diagonal. New in 4.0, for stacked harmony-chords.
- `type`: explicit | implied | alternate.
- `print-frame`, `print-object` (default yes), `placement` (above | below), `system`, `id`.
- Position/font attributes. Note that `default-x` on `<harmony>` is measured "relative to the start of the entire current measure."

**`<root>`** = `<root-step>` (required, A–G) + `<root-alter>` (optional, semitones). The tutorial notes: "There is no element that corresponds to the octave element for pitch." [w3](https://www.w3.org/2021/06/musicxml40/tutorial/chord-symbols-and-diagrams)

**`<bass>`** (4.0 order) = `<bass-separator>` (optional, new in 4.0) → `<bass-step>` (required) → `<bass-alter>` (optional). Attribute `arrangement` (new in 4.0) "specifies where the bass is displayed relative to what precedes it." [w3](https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/bass) Per the 4.0 change list, `<bass-separator>` "indicates that text, rather than a line or slash, separates the bass from what precedes it." [w3](https://www.w3.org/2021/06/musicxml40/version-history/40) The spec describes `<bass>` as being for pop symbols (e.g. G/C) [w3](https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/bass) and `<inversion>` (0 = root position, 1 = first inversion, …) as being for functional harmony. [musicxml](https://usermanuals.musicxml.com/MusicXML/Content/EL-MusicXML-harmony.htm)

**`<kind>` attributes (all optional, all "implementation-dependent" by default):**
- `text`: "how the `<kind>` should be spelled", e.g. `"6"`, `"7"`, `"m7b5"`.
- `use-symbols`: major = triangle U+25B3, minor = "-", augmented = "+", diminished = "°" U+00B0, half-diminished = "ø" U+00F8.
- `stack-degrees`, `parentheses-degrees`, `bracket-degrees`.
- `halign`, `valign`, plus position/font attributes.

Two spec rules matter for a renderer:
1. "For the major-minor `<kind>`, only the minor symbol is used when use-symbols is yes. The major symbol is set using the symbol attribute in the `<degree-value>` element." [w3](https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/kind)
2. "The text attribute may use strings such as '13sus' that refer to both the kind and one or more `<degree>` elements. In this case, the corresponding `<degree>` elements should have the print-object attribute set to no." [w3](https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/kind)

**The 33 kind values (verified against the 4.0 data-type page).** Your list is exactly correct, including the capitalisation of `Neapolitan`, `Italian`, `French`, `German` and `Tristan`.

| Kind value | Spec definition |
|---|---|
| major / minor / augmented / diminished | Triads: M3+P5 / m3+P5 / M3+A5 / m3+d5 |
| dominant | major triad, minor seventh |
| major-seventh / minor-seventh | major triad + M7 / minor triad + m7 |
| diminished-seventh | diminished triad, diminished seventh |
| augmented-seventh | augmented triad, minor seventh |
| half-diminished | diminished triad, minor seventh |
| major-minor | minor triad, major seventh |
| major-sixth / minor-sixth | triad + added sixth |
| dominant-ninth / major-ninth / minor-ninth | seventh chord + major ninth |
| dominant-11th / major-11th / minor-11th | ninth chord + perfect 11th |
| dominant-13th / major-13th / minor-13th | 11th chord + major 13th |
| suspended-second / suspended-fourth | M2+P5 / P4+P5 |
| Neapolitan / Italian / French / German | functional sixths |
| pedal | "Pedal-point bass" |
| power | "Perfect fifth." |
| Tristan | "Augmented fourth, augmented sixth, augmented ninth." |
| other | "Used when the harmony is entirely composed of add elements." |
| none | Absence of chord. "the `<root-step>` or `<numeral-step>` text attribute should be set to the empty string" |

The spec also says: "The 11th and 13th values are usually used as a basis for alteration." [w3](https://www.w3.org/2021/06/musicxml40/musicxml-reference/data-types/kind-value/)

**`<degree>`** = `<degree-value>` (number; `symbol` attribute: major | minor | augmented | diminished | half-diminished) → `<degree-alter>` (semitones) → `<degree-type>` (add | alter | subtract; a `text` attribute sets the printed text, e.g. `""` or `"add"`).

**Critical arithmetic rule:** "If the `<degree-type>` value is alter or subtract, the `<degree-alter>` value is relative to the degree already in the chord based on its kind element. If the `<degree-type>` value is add, the `<degree-alter>` is relative to a dominant chord (major and perfect intervals except for a minor seventh)." [w3](https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/degree-alter) The consequences:
- Adding a ♭7 uses alter **0**.
- Adding a maj7 uses alter **+1**.
- Adding ♭9 uses −1.
- Adding ♯11 uses +1.

### Encodings for the requested symbols

```xml
<!-- C7(b9,#11) -->
<harmony>
  <root><root-step>C</root-step></root>
  <kind text="7" parentheses-degrees="yes">dominant</kind>
  <degree><degree-value>9</degree-value><degree-alter>-1</degree-alter><degree-type>add</degree-type></degree>
  <degree><degree-value>11</degree-value><degree-alter>1</degree-alter><degree-type>add</degree-type></degree>
</harmony>

<!-- C7alt: no 'alt' kind exists. Use kind@text and hidden degrees (per the '13sus' rule) -->
<harmony>
  <root><root-step>C</root-step></root>
  <kind text="7alt">dominant</kind>
  <degree print-object="no"><degree-value>9</degree-value><degree-alter>-1</degree-alter><degree-type>add</degree-type></degree>
  <degree print-object="no"><degree-value>9</degree-value><degree-alter>1</degree-alter><degree-type>add</degree-type></degree>
  <degree print-object="no"><degree-value>5</degree-value><degree-alter>1</degree-alter><degree-type>alter</degree-type></degree>
</harmony>

<!-- Cm7b5 -->
<harmony><root><root-step>C</root-step></root><kind text="m7b5">half-diminished</kind></harmony>

<!-- C6/9 -->
<harmony>
  <root><root-step>C</root-step></root>
  <kind text="6/9">major-sixth</kind>
  <degree><degree-value>9</degree-value><degree-alter>0</degree-alter><degree-type text="/">add</degree-type></degree>
</harmony>

<!-- Cadd9 (the tutorial's A(9) uses parentheses-degrees="yes" and degree-type text="") -->
<harmony>
  <root><root-step>C</root-step></root>
  <kind>major</kind>
  <degree><degree-value>9</degree-value><degree-alter>0</degree-alter><degree-type text="add">add</degree-type></degree>
</harmony>

<!-- C(no3) -->
<harmony>
  <root><root-step>C</root-step></root>
  <kind parentheses-degrees="yes">major</kind>
  <degree><degree-value>3</degree-value><degree-alter>0</degree-alter><degree-type text="no">subtract</degree-type></degree>
</harmony>

<!-- C/E -->
<harmony>
  <root><root-step>C</root-step></root><kind text="">major</kind>
  <bass><bass-step>E</bass-step></bass>
</harmony>

<!-- Polychord D over C (two harmony-chords; vertical = separated by horizontal line) -->
<harmony arrangement="vertical">
  <root><root-step>D</root-step></root><kind>major</kind>
  <root><root-step>C</root-step></root><kind>major</kind>
</harmony>

<!-- N.C. (4.0 clarification) -->
<harmony><root><root-step text="">C</root-step></root><kind text="N.C.">none</kind></harmony>

<!-- Roman numeral V7/ii in C (4.0): 'V of II' = a 5-numeral chord followed by a 2-numeral chord -->
<harmony>
  <numeral><numeral-root text="V">5</numeral-root></numeral><kind text="7">dominant</kind>
  <numeral><numeral-root text="ii">2</numeral-root></numeral><kind>minor</kind>
</harmony>
```

Three of these encodings are reasonable encoder choices, not spec mandates:
- The C6/9 `degree-type text="/"`.
- Using `subtract` for "no3".
- The exact degree set chosen for "alt".

Kind `power` is the spec-native encoding for C5.

**`<numeral>`** (new in 4.0) contains, in order:
- `<numeral-root>` (1–7, `text` attribute e.g. "V" or "5")
- `<numeral-alter>` (semitones; `location` left/right; `print-object`)
- `<numeral-key>` (`<numeral-fifths>` + `<numeral-mode>`; the mode is one of major, minor, natural minor, melodic minor, harmonic minor)

The spec says it "requires that the key be specified in the encoding, either with a `<key>` or `<numeral-key>` element." It represents both Roman numerals and Nashville numbers. [w3](https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/numeral)

**Placement.** `<harmony>` is a sibling of `<note>` inside `<measure>`, placed *before* the note it applies to. `<offset>` (in divisions) shifts it from the current position; its `sound` attribute defaults to no for compatibility. `placement` = above | below. `<staff>` selects the staff in multi-staff parts.

**Application support.** I could not verify, from primary sources in this session, a per-application matrix for how MuseScore 4, Dorico, Finale, Sibelius and Flat import and export `<degree>`, `<numeral>`, polychords or `kind@text`. Treat specific lossiness claims as untested and build a round-trip fixture suite instead. The documented MuseScore point is that its MusicXML export added `tuplet-actual`/`tuplet-normal` for nested tuplets in 3.5.0 (issue #305169), because braille translation software needed them. [musescore](https://musescore.org/en/node/305169)

Practical rule: on import, trust `kind` + `degree` for semantics and keep `kind@text` only for display. Many writers emit inconsistent text/degree pairs.

## 1(b) Tablature

**Minimal valid tab staff (from the official tutorial):**

```xml
<attributes>
  <divisions>2</divisions>
  <key print-object="no"><fifths>0</fifths><mode>major</mode></key>
  <time print-object="no"><beats>4</beats><beat-type>4</beat-type></time>
  <clef><sign>TAB</sign><line>5</line></clef>
  <staff-details>
    <staff-lines>6</staff-lines>
    <staff-tuning line="1"><tuning-step>E</tuning-step><tuning-octave>2</tuning-octave></staff-tuning>
    <staff-tuning line="2"><tuning-step>A</tuning-step><tuning-octave>2</tuning-octave></staff-tuning>
    <staff-tuning line="3"><tuning-step>D</tuning-step><tuning-octave>3</tuning-octave></staff-tuning>
    <staff-tuning line="4"><tuning-step>G</tuning-step><tuning-octave>3</tuning-octave></staff-tuning>
    <staff-tuning line="5"><tuning-step>B</tuning-step><tuning-octave>3</tuning-octave></staff-tuning>
    <staff-tuning line="6"><tuning-step>E</tuning-step><tuning-octave>4</tuning-octave></staff-tuning>
  </staff-details>
</attributes>
```

**Tuning and numbering:**
- `<staff-tuning>` contains `<tuning-step>` (required), `<tuning-alter>` (optional) and `<tuning-octave>` (required).
- Tuning `line` counts from the bottom line, so line 1 = low E.
- `<capo>` (non-negative integer) "changes the open tuning of the strings specified by staff-tuning by the specified number of half-steps." [musicxml](https://usermanuals.musicxml.com/MusicXML/Content/EL-MusicXML-staff-details.htm)
- In `<technical>`, "Frets are numbered starting at 0 for the open string. Strings are numbered starting at 1 for the highest string." [w3](https://www.w3.org/2021/06/musicxml40/tutorial/tablature) **String numbers and tuning line numbers therefore run in opposite directions.** This is a classic bug source.

**Technique elements** (children of `<notations><technical>` unless noted):

| Element | Content / attributes |
|---|---|
| `<string>`, `<fret>` | integers |
| `<hammer-on>`, `<pull-off>` | `type` start/stop (required), `number`. Text content e.g. "H"/"P". Usually paired with a `<slur>` |
| `<bend>` | `<bend-alter>` (required; semitones, 0.5 = microtone; a release has a *negative* value), then optional `<pre-bend/>` or `<release/>`, then optional `<with-bar>` ("scoop"/"dip"). Attributes: `shape` angled/curved (**new 4.0**), accelerate, beats, first-beat, last-beat. `<release offset>` is **new in 4.0**. A bend plus release = two `<bend>` elements on one note |
| `<tap>` | content "T"/"+". `hand` left/right (added in **3.1**, not 4.0) |
| `<harmonic>` | optional `<natural/>` or `<artificial/>`, then optional `<base-pitch/>`, `<touching-pitch/>` or `<sounding-pitch/>` |
| `<slide>` (in `<notations>`) | `type` start/stop, `number`, `line-type`. "continuous between the two pitches and defaults to a solid line" |
| `<glissando>` (in `<notations>`) | same attribute pattern. Wavy line typical |
| `<other-technical>` | free text plus `smufl` attribute: "technical indications not yet in the MusicXML format" |
| **Palm mute** | **No `<palm-mute>` element exists.** Encode it as `<other-technical>P.M.</other-technical>` or as a `<direction><words>P.M.</words>` with `<dashes>`. For playback, the `mute` type includes `palm` |

**Pairing notation and tab staves.** MusicXML allows two patterns:
- **One part with `<staves>2</staves>`.** The tab staff gets `<staff-details number="2">`, ideally with `<staff-type>alternate</staff-type>`. The spec's definition: "An alternate staff shares the same music as the prior staff, but displayed differently (e.g., … standard notation and tablature). It is not included in playback." [w3](https://www.w3.org/2021/06/musicxml40/listings/musicxml.xsd)
- **Two separate parts.**

I could not verify from primary sources how MuseScore, Guitar Pro and TuxGuitar choose between these on export. Your importer must accept both. Heuristic: two parts with identical rhythms, the same instrument and one TAB clef are a linked pair.

**Written vs sounding pitch (guitar/bass).** `<pitch>` is the *written* pitch. `<transpose>` (in `<attributes>`) "represents what must be added to a written pitch to get a correct sounding pitch." Its children, in order: `<diatonic>` (optional), `<chromatic>` (required), `<octave-change>` (optional), `<double>` (optional; `above` attribute is new in 4.0). [w3](https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/transpose)

There are two valid guitar encodings, and an importer must not apply both:

```xml
<!-- (A) plain treble clef, transposing -->
<clef><sign>G</sign><line>2</line></clef>
<transpose><diatonic>0</diatonic><chromatic>0</chromatic><octave-change>-1</octave-change></transpose>
<!-- written E4 on the top space sounds E3 -->

<!-- (B) octave treble clef: pitches already sit an octave lower; no transpose -->
<clef><sign>G</sign><line>2</line><clef-octave-change>-1</clef-octave-change></clef>
```

Sounding pitch = written + chromatic + 12 × octave-change. `<chromatic>` "does not include `<octave-change>` values." [w3](https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/chromatic)

## 1(c) Tuplets, durations, meter, beams, pitch

**Divisions arithmetic.** `<divisions>` = ticks per quarter note. Choose the LCM of all needed denominators. `<duration>` = ticks. For a dotted note with n dots: base × (2 − 2⁻ⁿ).

**`<time-modification>`** contains `<actual-notes>` and `<normal-notes>` (both required), then optional `<normal-type>` and `<normal-dot>`*. `normal-type` is needed when "the type associated with the number in the `<normal-notes>` element is different than the current note type (e.g., a quarter note within an eighth note triplet)." [w3](https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/normal-type) For nested tuplets the values are **cumulative**: the element "shows how the cumulative, sounding effect of tuplets … compare to the written note type." [w3](https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/time-modification)

**`<notations><tuplet>`** attributes:
- `type` start/stop (required)
- `number` ("Distinguishes nested tuplets") [w3](https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/tuplet/)
- `bracket` yes/no
- `show-number` and `show-type`: actual | both | none; show-number defaults to actual
- `line-shape`, `placement`

Children (optional): `<tuplet-actual>` and `<tuplet-normal>`, each containing `<tuplet-number>`?, `<tuplet-type>`? and `<tuplet-dot>`*. [w3](https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/tuplet-actual/) [w3](https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/tuplet-normal)

```xml
<!-- Nested: quarter-note triplet (3:2) containing an eighth triplet. divisions=9 -->
<!-- outer triplet quarter = 6 ticks; inner triplet eighth = 2 ticks (cumulative 9:4) -->
<note><pitch><step>C</step><octave>5</octave></pitch><duration>6</duration><type>quarter</type>
  <time-modification><actual-notes>3</actual-notes><normal-notes>2</normal-notes></time-modification>
  <notations><tuplet type="start" number="1" bracket="yes"/></notations></note>
<note><pitch><step>D</step><octave>5</octave></pitch><duration>2</duration><type>eighth</type>
  <time-modification><actual-notes>9</actual-notes><normal-notes>4</normal-notes><normal-type>eighth</normal-type></time-modification>
  <beam number="1">begin</beam>
  <notations><tuplet type="start" number="2" bracket="yes" show-number="actual">
    <tuplet-actual><tuplet-number>3</tuplet-number><tuplet-type>eighth</tuplet-type></tuplet-actual>
    <tuplet-normal><tuplet-number>2</tuplet-number><tuplet-type>eighth</tuplet-type></tuplet-normal>
  </tuplet></notations></note>
<!-- ...second inner eighth: duration 2, beam continue; third: duration 2, beam end, tuplet number="2" stop -->
<!-- ...final outer quarter: duration 6, tuplet number="1" stop -->
```

This nested example is my construction from the spec rules; the official "tuplet (Nested)" example page is the reference fixture to diff against. Include `tuplet-actual`/`tuplet-normal` on nested tuplets: MuseScore added them to export specifically because downstream software mis-read nested tuplets without them. [musescore](https://musescore.org/en/node/305169)

**Ties.** `<tie>` (child of `<note>`, type start | stop only) "indicates sound". `<tied>` (in `<notations>`, type start | stop | continue | let-ring) indicates notation. [w3](https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/tied) [w3](https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/tie) Emit both. `let-ring` was added in 3.1. [w3](https://www.w3.org/2021/06/musicxml40/version-history/31)

**Additive and composite meters.** Quoting the spec: "Multiple pairs of `<beats>` and `<beat-type>` elements are used for composite time signatures with multiple denominators, such as 2/4 + 3/8. A composite such as 3+2/8 requires only one `<beats>`/`<beat-type>` pair." [w3](https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/time) So 2+2+3/8 is `<beats>2+2+3</beats><beat-type>8</beat-type>`. Parse `<beats>` as a string.
- `symbol` = common | cut | single-number | note | dotted-note | normal. [w3](https://www.w3.org/2021/06/musicxml40/listings/musicxml.xsd)
- `separator` = none | horizontal | diagonal | vertical | adjacent. [w3](https://www.w3.org/2021/06/musicxml40/musicxml-reference/data-types/time-separator)
- `<interchangeable>` (with `<time-relation>`: parentheses | bracket | equals | slash | space | hyphen) covers dual signatures like 3/4 (6/8). [w3](https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/interchangeable) [w3](https://www.w3.org/2021/06/musicxml40/musicxml-reference/data-types/time-relation)

**Beams.** One `<beam>` per beam level, `number` 1–8, "starting with the eighth note beam using a value of 1". Values: `begin`, `continue`, `end`, `forward hook`, `backward hook` (the hook values contain a space). [w3](https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/beam)

**Pitch and accidentals.** `<pitch>` = `<step>` → `<alter>` (optional, semitones, decimals for microtones, e.g. 0.5 = quarter-tone sharp) → `<octave>`. The ±2 range is a convention, not a schema limit: the semitones type is a plain decimal. Accept wider values and flag them.

`<accidental>` is *display only*; `<alter>` is the pitch. Attributes: `cautionary`, `editorial`, `parentheses`, `bracket`, `size`, `smufl`. [w3](https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/accidental) accidental-value has 42 values, including quarter-flat/sharp, three-quarters-flat/sharp, arrow variants, triple-sharp/flat, Turkish slash and numbered forms, sori/koron and `other`. [w3](https://www.w3.org/2021/06/musicxml40/listings/musicxml.xsd) 4.0 only extended the `smufl` glyph pattern. [w3](https://www.w3.org/2021/06/musicxml40/version-history/40)

---

# PART 2 — Advanced theory topics

## (a) Negative harmony

**Origin and sources:**
- **Ernst Levy**, *A Theory of Harmony* (SUNY Press, 1985; ed. Siegmund Levarie). Levy calls it a "polarity" theory, in the harmonic-dualist line of Hauptmann, Oettingen and Riemann. [Wikipedia](https://en.wikipedia.org/wiki/Negative_harmony) It contrasts a *telluric* view (gravity pulls "down" to the tonic) with an *absolute* view. Per Wikipedia's summary, Levy proposed the term "telluric gravity". **The term "negative harmony" does not appear in Levy's text.** [Wikipedia](https://en.wikipedia.org/wiki/Negative_harmony) The book's sales jumped after the Collier interview: Inside Higher Ed (11 July 2017), citing SUNY Press's James Peltz, reports "300 or so in lifetime hardcover sales, 677 paperbacks and 32 ebooks" before the interview, then "773 paperback sales and 85 ebook sales" after it.
- **Steve Coleman**, "Symmetrical Movement Concept" essay at m-base.com. Coleman says he linked his idea to Levy in the 1990s and passed it to educator Barak Schmool, who taught Collier. [Wikipedia](https://en.wikipedia.org/wiki/Negative_harmony) This claim is reported via Wikipedia and is self-published.
- **Jacob Collier**, "Interview: Jacob Collier (Part 1)" by June Lee (2017). Inside Higher Ed describes it as "almost 13 minutes long, with the reference to the book coming at the 1:30 mark," with "more than 560,000 views on YouTube" by July 2017. Collier: "any chord and any key has a reflection, a polar opposite chord within that key center, based on rotating every single note around the axis of that key center."

**Mechanics (in pitch classes).** For tonic T, the axis is the midpoint of T and T+7. Map n → (2T + 7 − n) mod 12. In C (T = 0) this gives n → 7 − n, an axis between E♭ and E (equivalently between A and B♭).

**Spelling-preserving rule (for a spelled-pitch engine).** Index letters C=0 … B=6.
- New letter = (4 − letter) mod 7, giving C↔G, D↔F, E↔E, A↔B.
- New alter = −alter + c, where c = 0 for C, D, F, G and c = −1 for E, A, B.
- For other keys, transpose to C, map, and transpose back.

| C | D♭ | D | E♭ | E | F | F♯ | G | A♭ | A | B♭ | B |
|---|---|---|---|---|---|---|---|---|---|---|---|
| G | F♯ | F | E | E♭ | D | D♭ | C | B | B♭ | A | A♭ |

**Chord images in C** (computed from the rule):

| Original | Pitches → reflected | Usual name |
|---|---|---|
| C (I) | C E G → G E♭ C | **Cm** |
| G7 (V7) | G B D F → C A♭ F D | **Fm6 = Dø7** (not "Dm6") |
| G (V) | G B D → C A♭ F | Fm (iv) |
| Dm7 (ii7) | D F A C → F D B♭ G | Gm7 = B♭6 |
| Fmaj7 (IVmaj7) | F A C E → D B♭ G E♭ | E♭maj7 |
| Am (vi) | A C E → B♭ G E♭ | E♭ |
| Em (iii) | E G B → E♭ C A♭ | A♭ |

So ii–V–I becomes Gm7 (B♭6) – Fm6 (Dø7) – Cm. That is the familiar minor-plagal/backdoor colour.

**The root question.** Reflection turns every chord's root into the chord's top "generator". C major's root C maps to G, which is the *fifth* of C minor. In Levy's dualism the minor triad is generated downward from G, so the "root" is G. The popular formulation (Collier and most online tutorials) simply renames the result with ordinary bottom-up root names: "Cm", "Fm6".

**Disagreements to surface in app content:**
1. Levy's is a metaphysical/dualist theory with no "negative" terminology.
2. Coleman uses symmetric movement mainly as an improvisational/melodic principle. [academia](https://www.academia.edu/26634737/Symmetry_in_the_music_of_Thelonious_Monk_and_Steve_Coleman)
3. Collier's popular version is a reharmonization device. It often relabels Fm6 as "the negative dominant" and talks about equal "gravity" toward the tonic. [andrews](https://www.andrews.edu/services/honors/research/jeremy-ahn-honors-poster.pdf)

Implement reflection as a pure function. Treat naming (root by reflection vs conventional root) as a display option.

## (b) Barry Harris 6th-diminished system

**Sources.** Barry Harris workshops (transcribed by students; no authoritative published text by Harris himself) and Alan Kingstone, *The Barry Harris Harmonic Method for Guitar* (2006). Roni Ben-Hur's *Talk Jazz* is derived from Harris's work. [Wikipedia](https://en.wikipedia.org/wiki/Bebop_scale) I could not verify specific page references for Frank Mantooth, Howard Rees or Chris Parks in this session, so cite them only after checking the books.

**Formulas:**

| Scale | Degrees | In C | Chords that partition it |
|---|---|---|---|
| Major 6th-diminished | 1 2 3 4 5 ♭6(♯5) 6 7 | C D E F G G♯/A♭ A B | C6 (C E G A) + B°7 (B D F A♭) | [Wikipedia](https://en.wikipedia.org/wiki/Bebop_scale) [jazz-guitar-licks](https://www.jazz-guitar-licks.com/pages/guitar-scales-modes/other-scales/the-major-sixth-diminished-scale-guitar-diagrams-and-theory.html)
| Minor 6th-diminished | 1 2 ♭3 4 5 ♭6 6 7 | C D E♭ F G A♭ A B | Cm6 (C E♭ G A) + B°7 (B D F A♭) |

**Spelling disagreement:** the extra note is written as ♭6 (A♭, which matches B°7) or as ♯5 (G♯, which matches Harris's spoken "C6 to G♯ diminished"). One source spells the major version as "major scale with an added b6 or #5." [cochranemusic](https://cochranemusic.com/barry-harris-6th-dim-scale-diminished) Recommendation: store A♭ when the note is generated as part of the diminished chord, and G♯ when it functions as a melodic passing tone from G to A.

**Harmonization (alternate 6 and °7 chords up the scale):** C6 – D°7 – C6/E – F°7 – C6/G – A♭°7 – C6/A (= Am7) – B°7 – C6. [jazz-guitar-licks](https://www.jazz-guitar-licks.com/blog/lessons/major-sixth-diminished-jazz-guitar-lesson-around-the-barry-harris-concept.html) D°7, F°7, A♭°7 and B°7 are one pitch-class set in four inversions, and that set equals a rootless G7♭9. [jazzguitarlessons](https://www.jazzguitarlessons.net/blog/adding-interest-to-comping-using-the-alan-kingstone-book-the-barry-harris-harmonic-method-for-guitar)

**Extensions.** Treating the eight notes as a source of chord-scales gives:
- C6 Dm7♭5 Em7 Fmaj7 G7 A♭°7 Am7 B°7
- C6 Dm7 Em7 FmMaj7 G7♭9 A♭+ AmMaj7 Bm6

These come from Cochrane's summary of a Harris workshop (secondary source). [cochranemusic](https://cochranemusic.com/barry-harris-6th-dim-scale-diminished)

**Applications:**
- **Dominant / ii–V:** "Dm7: use F6-dim; G7: use A♭m6-dim (for altered sound)." This is secondary, citing Kingstone. [jazzguitarlessons](https://www.jazzguitarlessons.net/blog/adding-interest-to-comping-using-the-alan-kingstone-book-the-barry-harris-harmonic-method-for-guitar) A♭m6 (A♭ C♭ E♭ F) over G gives ♭9, 3, ♭13 and 7.
- **Borrowing:** replace one note of the °7 chord with a note from the 6th chord (or the scale tone above) to colour passing chords. Kingstone's "drop three borrowing the soprano from above" is an example. [jazzguitar](https://www.jazzguitar.be/forum/comping-chords-chord-progressions/22015-barry-harris-my-aha-moment-regarding-chords-comping-2.html)
- **Drop-2 movement:** voice the close-position 6/°7 alternation as drop-2 and drop-3 shapes. [jazzguitarlessons](https://www.jazzguitarlessons.net/blog/adding-interest-to-comping-using-the-alan-kingstone-book-the-barry-harris-harmonic-method-for-guitar) On guitar this is the core comping method.

## (c) The omnibus progression

**Definition (Open Music Theory v2, Bryn Hughes, CC BY-SA 4.0).** "A special type of chromatic sequence in which the bass and another voice in the texture move in contrary motion." It "prolongs dominant harmony" through a chromatic voice exchange while the other voices stay as common tones.
- **Converging:** bass rises from the root while the leading tone falls.
- **Diverging:** bass falls while the seventh rises.
- **Variant:** chord 2 can be a °7 chord, giving three common tones.
- "Moving three semitones … brings you to another root-position dominant-seventh chord/German augmented-sixth chord … four semitones … the same dominant-seventh chord … in first inversion." [pressbooks](https://viva.pressbooks.pub/openmusictheory/chapter/the-omnibus-progression/)

**Converging omnibus prolonging V7 in C** (inner voices D and F held):

| # | Bass ↑ | Soprano ↓ | Chord (spelling) |
|---|---|---|---|
| 1 | G | B | G7 (G B D F) |
| 2 | G♯ | B♭ | G♯–D–F–B♭ (enharmonic B♭7/A♭, third inversion). Variant: G♯°7 (G♯ B D F), with the soprano holding B |
| 3 | A | A | D minor 6/4 (A D F A) |
| 4 | A♯ / B♭ | A♭ | B♭7 (B♭ D F A♭) ≡ German-sixth-type sonority. Spelling with A♯ in the bass shows its resolution to B |
| 5 | B | G | G7/B (V6/5) |

**Diverging omnibus from G7** (B and D held): G7 → B minor 6/4 over F♯ (F♯ B D F♯) → G7/F (V4/2: F B D G) → E7 (E G♯ B D). From there it repeats with the new seventh.

The chord spellings above are my derivation from OMT's voice-leading rule. Chords 2 and 4 are exactly where enharmonic spelling is contested.

**Scholarly sources:**
- Victor Fell Yellin, *The Omnibus Idea* (1998) [Wikipedia](https://en.wikipedia.org/wiki/Omnibus_progression)
- Paula J. Telesco, "Enharmonicism and the Omnibus Progression in Classical-Era Music," *Music Theory Spectrum* 20/2 (1998), JSTOR 746049. Telesco treats short omnibus segments as dominant prolongation by voice exchange, and the variable enharmonic spelling of the middle chords as the analytic point. [en-academic](https://en-academic.com/dic.nsf/enwiki/980082)
- Related: "The Theory and Practice of Chromatic Wedge Progressions in Romantic Music," *Music Theory Spectrum*. [Wikipedia](https://en.wikipedia.org/wiki/Omnibus_progression)

I did not read Yellin or Telesco directly in this session. Verify page references before quoting them in app content.

## (d) Common-tone diminished seventh (CT°7)

**Definition (Open Music Theory):** "The common-tone diminished seventh chord (CTo7) and common-tone augmented sixth chord (CT+6) have the same pitches as viio7 and Ger+6 but a different function: to embellish the upcoming chord (a major triad or dominant seventh chord, typically I or V)." They "contain the root of (i.e., have a common tone with) the chord being embellished." [pressbooks](https://viva.pressbooks.pub/openmusictheory/chapter/common-tone-chords/) OMT's jazz chapter: "To create a CTo7, the root of the chord being embellished is kept as a common tone, … and all other voices move by step." [pressbooks](https://viva.pressbooks.pub/openmusictheory/chapter/jazz-embellishing-chords/)

**Spelling algorithm.** Keep the target root R. Build the °7 containing R whose other notes sit a step or half-step below or above the target's chord tones: R, ♯2, ♯4, 6 relative to R.
- **On I in C:** C–D♯–F♯–A (♯ii°7). D♯→E, F♯→G, A→G, C held.
- **On V in C:** G–A♯–C♯–E (♯vi°7 in C). A♯→B, C♯→D, E→D.
- Integrated Music Theory notes that the two most common are "most likely … spelled as ♯ii°7 and ♯vi°7 respectively, because these provide the correct voice leading." [github](https://intmus.github.io/inttheory18-19/18-chromatic-chords/c1-ex-ctdiminished.html) OMT notes that "spelling variants do happen." [pressbooks](https://viva.pressbooks.pub/openmusictheory/chapter/common-tone-chords/)

**Distinguishing it from vii°7/x:**

| Test | CT°7 | vii°7/x |
|---|---|---|
| Contains the root of the next chord? | Yes (the common tone) | No (its root is a half step below x) |
| Resolution | All voices move by neighbour motion back to the same chord | Root rises a half step to x |
| Typical context | I–CT°7–I, V–CT°7–V | tonicizes x |

**Lead-sheet practice.** Jazz charts usually write the CT°7 with the *common tone as the chord-symbol root*. OMT gives "C, Cº7, C", so the chord symbol is C°7 = C E♭ G♭ B𝄫, enharmonic with C–D♯–F♯–A. [pressbooks](https://viva.pressbooks.pub/openmusictheory/chapter/jazz-embellishing-chords/) The parser should produce the dim7 set, and an analysis layer can re-spell it as ♯ii°7. OMT says these chords "are often not written into lead sheets but improvised." [pressbooks](https://viva.pressbooks.pub/openmusictheory/chapter/jazz-embellishing-chords/)

**Textbook sources.** Kostka/Payne (*Tonal Harmony*) and Aldwell/Schachter (*Harmony and Voice Leading*) cover CT°7 in their chromatic-harmony chapters. I could not verify their chapter or page numbers in this session. Their use in ragtime, barbershop and Tin Pan Alley song is widely taught, but I did not confirm a primary source for it here.

## (e) Messiaen's modes of limited transposition

**Primary source:** Olivier Messiaen, *Technique de mon langage musical* (Paris: Leduc, 1944); English translation *The Technique of My Musical Language* (trans. John Satterfield). The definition is a scale whose transpositions repeat the same pitch-class set before all 12 are exhausted. [chromatone](https://chromatone.center/theory/scales/symmetrical/) Mathematically, it is a pc-set invariant under some non-trivial transposition mod 12. [arxiv](https://arxiv.org/pdf/1407.5757)

| Mode | Semitone pattern | Notes | Transpositions | Rotations ("modes") | Pitch content on C (one common spelling) |
|---|---|---|---|---|---|
| 1 (whole-tone) | 2 2 2 2 2 2 | 6 | 2 | 1 | C D E F♯ G♯ A♯ |
| 2 (octatonic, half-whole) | 1 2 1 2 1 2 1 2 | 8 | 3 | 2 | C D♭ E♭ E F♯ G A B♭ |
| 3 | 2 1 1 2 1 1 2 1 1 | 9 | 4 | 3 | C D E♭ E F♯ G A♭ B♭ B |
| 4 | 1 1 3 1 1 1 3 1 | 8 | 6 | 4 | C D♭ D F F♯ G A♭ B |
| 5 | 1 4 1 1 4 1 | 6 | 6 | 3 | C D♭ F F♯ G B |
| 6 | 2 2 1 1 2 2 1 1 | 8 | 6 | 4 | C D E F F♯ G♯ A♯ B |
| 7 | 1 1 1 2 1 1 1 1 2 1 | 10 | 6 | 5 | C D♭ D E♭ F F♯ G A♭ A B |

Rotation counts and patterns match standard summaries, e.g. "mode 3 … has four transpositions … and three modes; … mode 7 … six transpositions … and five modes." [Wikipedia](https://simple.wikipedia.org/wiki/Modes_of_limited_transposition) [kiddle](https://kids.kiddle.co/Mode_of_limited_transposition)

**Symmetry:** mode 1 repeats every 2 semitones, mode 2 every 3, mode 3 every 4, and modes 4–7 every 6 (the tritone). Mode 1 is the whole-tone scale; mode 2 is the octatonic/diminished scale. Messiaen favoured modes 2, 3, 4 and 6 in practice. [github](https://github.com/solyarisoftware/Olivier-Messiaen-Modes)

**Spelling.** There is no single correct spelling. These are symmetric sets, and Messiaen's examples and secondary sources mix sharps and flats. Store the modes as pitch-class sets plus a display-spelling policy, for example "flats descending / sharps ascending" or "minimise letter repeats". Do not encode one spelling as canonical in tests.

## Harmonic-major mode names (quick check)

The modes of C harmonic major (C D E F G A♭ B), derived and checked interval by interval:

| Mode | Notes | Most common names (alternatives seen) |
|---|---|---|
| 1 | C D E F G A♭ B | Harmonic major (Ionian ♭6) |
| 2 | D E F G A♭ B C | Dorian ♭5 (Locrian ♮2 ♮6) |
| 3 | E F G A♭ B C D | Phrygian ♭4 (Superphrygian; Altered ♮5) |
| 4 | F G A♭ B C D E | Lydian ♭3 (Melodic minor ♯4; Lydian diminished) |
| 5 | G A♭ B C D E F | Mixolydian ♭2 |
| 6 | A♭ B C D E F G | Lydian augmented ♯2 (Lydian ♯2 ♯5) |
| 7 | B C D E F G A♭ | Locrian ♭♭7 (Locrian diminished) |

The interval derivations above are exact. I did not get to fetch primary sources documenting which author uses which alternative name. Show the primary name plus "also called" aliases in app content, and do not assert any one name as standard.

## CC BY-SA 4.0 → GPLv3 compatibility

- **Official:** Creative Commons' Compatible Licenses page says "The GNU General Public License version 3 was declared a 'BY-SA–Compatible License' for version 4.0 on 8 October 2015." It adds: "compatibility with the GPLv3 is one-way only, which means you may license your contributions to adaptations of BY-SA 4.0 materials under GPLv3, but you may not license your contributions to adaptations of GPLv3 projects under BY-SA 4.0. Other special considerations apply." [creativecommons](https://creativecommons.org/share-your-work/licensing-considerations/compatible-licenses/)
- CC's announcement says a reuser of the combined work "need only look to the conditions of GPLv3 to satisfy the attribution and ShareAlike conditions of BY-SA 4.0." [creativecommons](https://creativecommons.org/2015/10/08/cc-by-sa-4-0-now-one-way-compatible-with-gplv3/) The FSF's blog post "Creative Commons BY-SA 4.0 declared one-way compatible with GNU GPL version 3" confirms that "this compatibility is one-way only."
- **What this means for the app (GPL-3.0-or-later):**
  1. You may adapt Open Music Theory v2 text. OMT's copyright page states CC BY-SA 4.0 "except where otherwise noted", so check each figure and embedded example. [pressbooks](https://viva.pressbooks.pub/openmusictheory/chapter/the-omnibus-progression/)
  2. Release the adapted text under GPLv3.
  3. Keep the OMT attribution and a modification notice, as BY-SA 4.0 requires of adapters.
  4. The declaration names GPLv3. The FSF "urges individuals to take advantage of Section 14 of the GNU GPLv3," naming CC as proxy "so that if and when Creative Commons determines that a future version of the GPL is a compatible license, the adapted and combined work could be used under that later version"; no later version has been declared yet. Label adapted content "GPL-3.0" or "GPL-3.0-or-later (CC proxy)" after legal review.
- CC BY 4.0 data, such as most of ChoCo, is GPLv3-compatible: the GNU Project's license list says CC BY 4.0 "is compatible with all versions of the GNU GPL; however, like all CC licenses, it should not be used on software."

---

# PART 3 — Chord-symbol corpora and the spelling variants a parser must handle

## Dataset table

| Dataset | Size | Label syntax | License | Bundle in GPL test suite? | Where |
|---|---|---|---|---|---|
| **Harte syntax** (Harte, Sandler, Abdallah, Gómez, ISMIR 2005) | spec | `root ":" shorthand ["(" degrees ")"] ["/" bass-degree]`. Shorthands maj min dim aug maj7 min7 7 dim7 hdim7 minmaj7 maj6 min6 9 maj9 min9 sus4. `*3` omits a degree. `N` = no chord. C ≡ C:maj ≡ C:(3,5) | paper | n/a (syntax) | ismir2005.ismir.net/proceedings/1080.pdf | [ismir](https://ismir2005.ismir.net/proceedings/1080.pdf)
| **McGill Billboard 2.0** (Burgoyne, Wild, Fujinaga, ISMIR 2011) | 890 chart slots comprising 740 distinct songs (DDMAL official project page) | Harte-style with extras, e.g. `D#:hdim7/b5`, `F#:9(*3,11)`, `1(11)`, `5(b7)`, `sus4(b7,9)`, `maj6(9)`, bar-grid lines `\| B \| B:aug \|` | **CC0** (official page, quoted via mirror) | **Yes** | ddmal.ca/research/The_McGill_Billboard_Project_(Chord_Analysis_Dataset)/ |
| **ChoCo** (de Berardinis et al., *Scientific Data* 10:641, 2023) | 20,080 JAMS files; 20,530 Harte chord annotations; 18 sources | Originals (Leadsheet, Harte, Roman, ABC) plus Harte conversions, e.g. `F:min/5`, `C#:maj7/3`, `C#/3`, `N` | **CC BY 4.0**, except Chordify, Mozart Piano Sonata and JAAH subsets (**CC BY-NC-SA 4.0**) | Yes for CC BY subsets (attribute). **No** for NC-SA subsets | github.com/smashub/choco ; Zenodo DOI 10.5281/zenodo.7706751 | [github](https://github.com/smashub/choco)
| ChoCo subsets of note | Isophonics 300; Billboard 890; Real Book 2,486; iReal Pro 2,000+; Wikifonia 6,500+; Band-in-a-Box 5,000+; Weimar 456; Rock Corpus 200 (Roman .har); When in Rome 450 (RomanText); Nottingham 1,000+ (ABC); JAAH 113; RWC-Pop 100; USPop 195 | as above | per ChoCo | [github](https://github.com/smashub/choco) iReal-, Wikifonia- and BiaB-derived charts were user-made. ChoCo's CC BY claim rests on chord sequences being uncopyrightable, so keep a legal note | same |
| **Weimar Jazz Database** v2.1 | "Weimar Jazz Database v2.1 (DB version 2.2) with 456 solo transcriptions" (Jazzomat download page), with chords and beats; 456 solos from 78 performers (Frieler) | lead-sheet style chord symbols, in "a single SQLITE3 database file" | **ODbL** | Small extracts only, with ODbL notice. Simplest is local-only | jazzomat.hfm-weimar.de/download |
| **Chordonomicon** (Kantarelis et al., arXiv 2410.22046, 2024) | 666,000+ songs (HF: 679,807 rows, 264 MB); 51,994,634 chords, 1,464,392 inverted | chords normalized by the authors, with structural part tags (verse, chorus…) | **Conflict:** HF dataset card `cc-by-nc-4.0`; GitHub code Apache-2.0; arXiv paper CC BY 4.0. Source is scraped Ultimate Guitar | **No** (NC; scraped). Local analysis only | huggingface.co/datasets/ailsntua/Chordonomicon | [github](https://github.com/spyroskantarelis/chordonomicon) [huggingface](https://huggingface.co/datasets/ailsntua/Chordonomicon) [tiptreesystems](https://lacuna.tiptreesystems.com/work/chordonomicon-a-dataset-of-666-000-songs-and-their-chord-progressions/wrk_b233cea1c4714e334301bcf6c769b5f0)
| **iReal Pro** protocol and public playlists | thousands of user charts on the forums | `C^7`, `C-7`, `Ch7`, `Co7`, `C7alt`, `C-^7`, `n` (N.C.), `x`/`r` repeats, `(Db^7/F)` alternate chords | protocol is public. [irealpro](https://www.irealpro.com/ireal-pro-file-format) Playlists are user content (unclear) | Use the **quality list** (facts) for tests. Do not ship playlists | irealpro.com/ireal-pro-file-format |
| JAMS `chord` / `chord_harte` namespaces | spec | `chord` adds sus2, 1, 5, aug7, 11, maj11, min11, 13, maj13, min13. Sharps and flats cannot be mixed | ISC (jams library) | n/a | jams.readthedocs.io | [readthedocs](https://jams.readthedocs.io/en/stable/namespaces/chord.html)
| Rock Corpus (de Clercq & Temperley 2011) | 200 songs | Roman numerals (.har) | not verified | Check before bundling | (via ChoCo) | [github](https://github.com/smashub/choco)
| Hooktheory TheoryTab; MuseScore.com scores; Ultimate Guitar | very large | free text / Roman | site terms or per-score licenses, not verified here | **Local only** | — |

**Quantitative hint:** in Chordonomicon, about 2.8% of chord tokens are slash/inverted chords (1,464,392 of 51,994,634). [tiptreesystems](https://lacuna.tiptreesystems.com/work/chordonomicon-a-dataset-of-666-000-songs-and-their-chord-progressions/wrk_b233cea1c4714e334301bcf6c769b5f0) Slash handling is not an edge case.

## The iReal Pro quality vocabulary (verbatim, official)

`5, 2, add9, +, o, h, sus, ^, -, ^7, -7, 7, 7sus, h7, o7, ^9, ^13, 6, 69, ^7#11, ^9#11, ^7#5, -6, -69, -^7, -^9, -9, -11, -7b5, h9, -b6, -#5, 9, 7b9, 7#9, 7#11, 7b5, 7#5, 9#11, 9b5, 9#5, 7b13, 7#9#5, 7#9b5, 7#9#11, 7b9#11, 7b9b5, 7b9#5, 7b9#9, 7b9b13, 7alt, 13, 13#11, 13b9, 13#9, 7b9sus, 7susadd3, 9sus, 13sus, 7b13sus, 11, min13, min^11, min^13, maj13#11, maj7b5, maj7#9, min7b6, min9b6, maj(add4), min(add4), 7(add13)` [irealpro](https://www.irealpro.com/ireal-pro-file-format)

- Roots: `C, C#, Db, D, D#, Eb, E, F, F#, Gb, G, G#, Ab, A, A#, Bb, B`. [irealpro](https://www.irealpro.com/ireal-pro-file-format)
- The iReal help centre gives the equivalences: `^`/∆ = maj7; `o` = dim; `ø`/`h` = min7♭5; `add2` = add9; `+` = aug = ♯5; `7+` = 7♯5; `sus4` = sus; **`sus2` = `2`**; `alt` = 7alt; `7susb9` = 7b9sus. [helpshift](https://technimo.helpshift.com/hc/en/3-ireal-pro/faq/88-chord-symbols-used-in-ireal-pro/)
- iReal writes sus extensions *before* "sus" (`9sus`, `13sus`). [github](https://github.com/AntoineBalaine/abcls/pull/17)

## Spelling-variant table (parser requirements and canonical mappings)

| Category | Variants seen | Canonical (Harte / MusicXML kind) | Notes / ambiguity |
|---|---|---|---|
| Major triad | C, CM, Cmaj, CΔ (rare as a triad) | C:maj / major | "Cmaj" sometimes means Cmaj7 in sloppy charts; default to triad |
| Minor | Cm, Cmin, C-, Cmi, c (lowercase) | C:min / minor | lowercase root = minor only in Roman-numeral or Nashville contexts. In letter charts, lowercase is usually a typo for major. Flag it |
| Maj7 | Cmaj7, CM7, CΔ7, CΔ, C^7, C^, C7M, Cma7, Cj7 | C:maj7 / major-seventh | `C7M` is common in Brazilian and French charts |
| Dominant | C7, Cdom7 | C:7 / dominant | |
| Minor 7 | Cm7, Cmin7, C-7, Cmi7 | C:min7 / minor-seventh | |
| Half-dim | Cø, Cø7, Cm7b5, Cm7-5, C-7b5, Ch, Ch7, Cmi7(♭5) | C:hdim7 / half-diminished | "-5" means ♭5, not "minus" |
| Dim triad vs dim7 | C°, Co, Cdim, Cdim7, C°7, Co7 | C:dim / diminished; C:dim7 / diminished-seventh | **Ambiguous:** many jazz charts write `C°` meaning °7. iReal: `o` = triad, `o7` = dim7. Use a configurable default; jazz mode → dim7 |
| Augmented | C+, Caug, C(#5), C+5 | C:aug / augmented | |
| Aug 7 | C+7, C7+, C7#5, C7(+5), Caug7 | C:aug7 (JAMS) / augmented-seventh | **`C7+` is ambiguous:** iReal = 7♯5, but some Latin and European charts use 7+ for maj7. Default 7♯5 and flag |
| Min-maj7 | Cm(maj7), CmM7, Cm^7, C-^7, C-Δ7, CmMaj7, Cm#7, Cmi(ma7) | C:minmaj7 / major-minor | `Cm#7` is a rare amateur spelling |
| Sixths | C6, Cmaj6, CM6; Cm6, C-6 | maj6 / min6 | |
| 6/9 | C6/9, C69, C6(9), C6add9 | C:maj6(9) / major-sixth + add 9 | `C6/9` must **not** parse as slash-bass 9 |
| add9 | Cadd9, C(add9), Cadd2, C(9), C2 | C:maj(9) / major + add 9 | MusicXML's own tutorial encodes "A(9)" as major + add 9. **`C2` is ambiguous:** iReal = sus2. Worship and pop charts often mean add2 (3rd kept). Default sus2 in iReal mode, add9 in pop mode, and flag |
| sus | Csus, Csus4, C4; Csus2, C2; C7sus, C7sus4, C9sus, C11 (sometimes = 9sus4) | sus4 / suspended-fourth; sus2 / suspended-second | Bare "sus" = sus4. `C11` used for 9sus4 is a common practical ambiguity |
| Power | C5, C(no3) | C:5 (JAMS) / power | `C(no3)` keeps the 5th; it equals power only if no other tones are present |
| Omissions | (no3), (no 3rd), no5, omit3 | Harte `*3` / degree subtract | |
| Extensions and alterations | C9, C7(9), C7b9, C7-9, C7(♭9,♯11), C7+9, C7#9, C13, C7alt, C7♯9♯5 | 7 + degrees / dominant + degree | "+9" and "-9" mean ♯9/♭9. Parenthesis and comma variants: `(b9,#11)`, `(b9 #11)`, `b9#11`, stacked |
| Slash and inversions | C/E, Am/G#, C/G, D/F♯ | Harte `C:maj/3` (bass as degree) / `<bass>` | Harte encodes the bass as an interval from the root. Convert by spelled interval, e.g. Am/G♯ → `A:min/7` |
| Polychords | D/C (stacked), D\|C, D over C | MusicXML 2 harmony-chords, arrangement=vertical | A slash cannot distinguish polychord from slash chord; use the line or "over" if present |
| No chord | N.C., NC, n (iReal), N (Harte), "-" | kind none | |
| Accidentals | b, #, ♭, ♯, bb, 𝄫, x, ##, "is/es" (German) | | Accept Unicode; normalise to alter. JAMS `chord` forbids mixed `A#b` |
| German | H = B♮, B = B♭, Hm, Fis, Cis, Es, As, Ges, Des | — | In German-locale input mode, "B" means B♭. **Never auto-detect** German from "B" alone; use locale or the presence of H |
| Romance solfège | Do Re Mi Fa Sol La Si (Do#, Reb, Sim, Lam7) | — | "Si" = B; "Sol" vs "So". Fixed-do |
| Japanese | Letter names in chord symbols. Traditional key names ハ ニ ホ ヘ ト イ ロ (C D E F G A B) | — | Mostly an i18n display concern |
| Chart noise | `\|`, `%`, `x`, `/` slashes, `(…)` alternates, `*A`, `N1`, repeat signs, `|: :|`, "x2", lyrics | — | Tokenize before chord parsing. iReal uses `x`/`r` for repeats and `p` for slashes |

Recommended parser architecture:
1. Locale and dialect pre-pass (English, German, solfège; jazz/iReal vs pop defaults for `°`, `2`, `7+`).
2. Root + accidental.
3. Quality token by longest match against the table above.
4. Extensions/alterations list.
5. Omissions.
6. `/bass`.
7. Emit a structure that maps 1:1 to MusicXML kind + degrees and to Harte.
8. Carry an `ambiguous` flag with alternative readings for C2, C°, C7+, Cmaj, C11 and lowercase roots.

## Recommendations

1. **Data model:** mirror MusicXML: root (spelled) | numeral, kind (33-enum), ordered degree edits with add-relative-to-dominant arithmetic, bass (spelled), and display text. Round-trip `kind@text`, but do not trust it for semantics.
2. **Fixtures:**
   - Bundle McGill Billboard (CC0) and ChoCo CC BY 4.0 subsets, with ChoCo attribution in NOTICE.
   - Generate synthetic fixtures from the iReal quality list (facts, not content).
   - Keep Chordonomicon, Weimar (ODbL), Hooktheory and UG local, and only commit *derived spelling-frequency statistics* after legal review.
3. **Tab:** support both the one-part/two-staves and two-part layouts. Reconcile string numbering (1 = highest) with tuning lines (1 = lowest). Map palm mute to `<other-technical>` or `<words>`.
4. **Theory content:** fix the G7 → Fm6/Dø7 error. Present Levy, Coleman and Collier as three distinct framings. Store Messiaen modes and harmonic-major modes as pc-sets with alias lists. Adapted OMT text can ship under GPLv3 with attribution.

## Caveats

- I could not verify these from primary sources in this session:
  - per-application MusicXML import/export behaviour (MuseScore 4, Dorico, Finale, Sibelius, Flat, Guitar Pro, TuxGuitar), apart from the MuseScore nested-tuplet fix
  - page references in Levy, Yellin, Telesco, Kostka/Payne and Aldwell/Schachter
  - the licenses of Isophonics, Rock Corpus, Hooktheory and MuseScore.com
  - author-by-author naming of the harmonic-major modes
- The Chordonomicon license conflict is real (CC BY-NC 4.0 vs Apache-2.0 vs CC BY 4.0 across three official surfaces). Use the most restrictive one.
- ChoCo's CC BY relicensing of iReal-, Wikifonia- and BiaB-derived charts depends on chord sequences being uncopyrightable. That is a reasonable but untested legal position.
- Regional readings of `C7+`, `C2` and `C°` come from practitioner convention, not a published standard. Keep them configurable.