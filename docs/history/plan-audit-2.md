> **SUPERSEDED (Oct 21 2026).** Product direction now comes from `apophenia-architecture-decisions-2026-10-21.md` (user-approved) and `apophenia-unified-product-plan-2026-10-21.md`. Keep this file for history and for detail not covered there; where they differ, they win.

# Plan audit 2 — Christopher's decisions on audit 1, and what they change (Oct 20 2026)

Follows `plan-audit-1.md`. **Where this file differs from audit 1 or the design docs, this file wins.** Nothing is built or tested yet; **[research]** marks what needs a research pass before it's designed in detail.

The app: **Apophenia** (domain apophenia.audio; free and open source, GPL-3.0; GitHub and Play Store, F-Droid to follow). The name states the goal this audit puts at the centre: **capture rough scraps, put them together, and see patterns between them** — lyrics, recordings, chords, riffs.

---

## 1. Decisions

| Item | Decision | Effect on the plan |
|---|---|---|
| Widgets | Yes | Home-screen widget, Quick Settings tile and app shortcuts stay in P1 |
| USB interfaces, pedals, volume keys | Polish, not the point | Moved to a **later backlog**; not in P1. Phone mic is the input |
| Purpose | Capture and work through rough ideas; put scraps together; see patterns between lyrics and other things | **Stacks** (§3) and **Connections** (§4) become core features, ahead of the practice tools |
| Take analysis | Every recording shows the tempo it was played at, its key **and mode**, and the instrument | New analysis step on every take (§2) |
| Transcription | Tab, MIDI or sheet music from recordings would be a huge benefit | Moved up and widened from audit 1's "take to tab" (§5) |
| Draft playback | Yes | Stays (piano comping + click) |
| Piano dynamics | Decide later | One dynamic for now; the sampler is built so a second layer can be added |
| Modes | Phrygian, Dorian etc. throughout | Modes in analysis, matching, Reference and Theory (§2, §3) |
| Tuner string mode | No | Dropped. The detector still limits its search to the selected tuning's range internally (no UI), which is the cheaper defence against octave errors |
| Practice sessions | No structured sessions; **curated exercises** chosen by Christopher at the end | Session builder removed. An **Exercises** library is reserved (data model and screen), content decided later. Practice log, timer and auto-log stay |
| Stacks like Splice, with your own recordings | Yes | New feature (§3) |

---

## 2. Take analysis — tempo, key and mode, instrument

Shown on every take as one line, e.g. **92 BPM · D Dorian · Bass (D standard)**, each part tappable to correct. Corrections are kept and used for matching; detected values show a confidence mark.

| Field | How it's found | Honest limits |
|---|---|---|
| **Tempo** | 1. If the metronome was running during the take, its tempo and beat grid are recorded exactly — no guessing. This is the simple universal case and covers most practice takes. 2. Otherwise: onset-strength envelope → tempo estimate with half/double candidates, plus a "free time" result when there's no steady pulse. | Detection on short or loose takes picks the wrong half/double often; the candidates are one tap away. **[research]** method and accuracy on 5–60 s single-instrument takes |
| **Key and mode** | Pitch-class profile of the take (from the transcribed notes on single-note takes, from a chroma spectrum on chordal takes) + a **tonic estimate** (longest/most repeated/lowest/final notes) → ranked candidates across the modes of major, harmonic minor and melodic minor, with the evidence shown: "tonic D; uses B♮ and C♮ → D Dorian (2nd: D Aeolian if the B is a passing note)". | Relative modes share their notes (D Dorian = C major's notes), so the mode depends entirely on the tonic guess; short riffs that never settle on a tonic stay ambiguous and say so. **[research]** key/mode-finding methods beyond major/minor profiles |
| **Instrument** | Picked, not detected: the rail carries the current instrument (string count + tuning + capo, or Voice, Keys, Percussion, Other) and every take is tagged with it. One tap changes it before or after recording. | Recognising instruments from audio is machine learning; not attempted |

Analysis runs in the background after each take and never delays capture.

---

## 3. Stacks — layering your own recordings

**What Splice Stacks does (researched):** up to eight layers, each one loop; compatible loops suggested by key, tempo and style; tap to mute, long-press to solo, swipe to delete; volume per layer; tempo adjustable (mobile: ±20%); undo; save to a Stacks library; export a mix, the original files, or stems with the pitch/tempo changes applied. Its suggestions are AI picks from a 1.4-million-loop catalog and need the internet.

**Ours, from your own material, offline:**
- **A stack** has a key/mode and tempo of its own (set from the first layer, editable) and up to **8 layers**. A layer is:
  - a **loop region of a take** (1, 2, 4 or 8 bars, cut on the take's beat grid, start nudgeable),
  - a **chord part** or **tab/MIDI part**, played by the piano (once the piano exists, P4),
  - a **lyric part** — not audio: the lines scroll against the loop so you can read a lyric over a riff and see if it fits.
- **Conforming:** each audio layer is time-stretched to the stack tempo and pitch-shifted to the stack key, using its detected (or corrected) tempo and key. Shift amounts are shown on the layer ("+2 st, 92→96 BPM"); large shifts sound worse and the layer says so. Conformed audio is **rendered once in the background and cached**, so playback is a cheap mix even with 8 layers; changing the stack's tempo or key re-renders.
- **Controls** (same gestures as Splice so they're learnable): tap mute, long-press solo, swipe delete, volume per layer, stack tempo and key, **swap** a layer for the next suggestion, **lock** layers so swaps leave them, undo/redo, save, duplicate.
- **Suggestions without AI — rules you can read:** your ideas are ranked against the stack by key/mode compatibility (same key → relative/parallel mode → a fifth away), tempo (same, within ±8%, or half/double), instrument role (prefer one not already in the stack), and your folders/tags. Each suggestion says why: "same key (D Dorian), 90 vs 92 BPM, bass — stack has none".
- **Record over the stack:** a new take recorded while the stack loops is saved as a fragment, already linked to the stack and tagged with its tempo and key.
- **Where stacks go:** save as an idea in the Workshop; turn a stack into a draft section (layers become the section's parts); export stems (conformed WAVs), a mix, and MIDI for the note/chord layers — to FL Studio like the rest of the export package.

**Dependency:** stacks need the time-stretch and pitch-shift engine, which audit 1 placed in the player phase. It moves earlier (P3) and the player reuses it.

---

## 4. Connections — seeing patterns between scraps

The "apophenia" part: every idea has a **Connections** panel, and the Workshop has a Connections view across everything. Each connection states its reason, and all of them are computed offline from data the app already has.

| Connection | Found by |
|---|---|
| Same or compatible key/mode | Take analysis, chord parts, tab parts |
| Same or related tempo (incl. half/double) | Take analysis |
| Same progression, any key | Chord parts compared as Roman numerals (I–♭VII–IV in A = in E) |
| Melodic or rhythmic shape | Interval and rhythm sequences of transcribed or written parts, transposition-free |
| **Lyric fits a riff** | Syllable count and stress pattern of a lyric line vs the note count and accents of a phrase in a take or tab part (needs the pronouncing dictionary) |
| Rhymes and sound | End-rhymes and internal rhymes across lyric fragments (pronouncing dictionary) |
| Shared words and images | Repeated words/stems across lyrics, notes and transcripts |
| Your own grouping | Shared folders, tags, same day/session |

Not attempted: "these feel similar" by meaning or mood — that's machine learning. Feelings come from your folders.

**[research]** the pronouncing dictionary's licence (CMU dict) and how syllable/stress matching is done in existing lyric tools.

---

## 5. Transcription — tab, MIDI and notation from recordings

| Recording type | Result | Method | Limits |
|---|---|---|---|
| Single notes (bass line, riff, sung melody, lead line) | Notes with rhythm → **tab** (positions from the fingering chooser for the take's instrument/tuning), **MIDI**, **notation** | Pitch tracking (the tuner's detector) + onset detection, quantised to the take's beat grid | Rough; everything editable. Slides, bends and vibrato become pitch wobble to clean up |
| Strummed or block chords (guitar, piano) | **Chord sequence** with timing → chord part (and Roman numerals if the key is known) | Chroma per beat matched against chord templates from the catalog | Chord *names*, not individual notes; extensions and inversions often missed |
| Polyphonic note-by-note (fingerstyle, piano parts) | Not feasible as DSP | Needs a neural model (e.g. Basic Pitch, Apache 2.0) | Possible only if the P0 probe shows a prebuilt native library can be bundled |

The transcription becomes a new part **inside the same idea**, time-linked to the take, so tapping a note plays that moment of the recording. It also feeds take analysis (better key/mode) and Connections (shapes).

**[research]** monophonic transcription methods and parameters for short phone-mic takes (pitch tracking, onset detection, quantisation), and chroma chord recognition accuracy.

---

## 6. Revised build order (replaces audit 1 §F)

| Phase | Delivers |
|---|---|
| **P0 Probe + mockups** | Probe build: mic sources, latency, simultaneous in/out, on-device speech, `revokeSelfPermissionOnKill`, U+1D12A rendering, bundling a prebuilt `.so`. Clickable mockups: rail, Workshop, idea view with take analysis, Stack, Connections, draft lead-sheet |
| **P1 Engine + rail + capture** | Audio engine, metronome (records its grid into takes), tuner, drone, recorder with pre-roll, instrument chip, mic chip, widget / tile / shortcuts, scratch pad |
| **P2 Core + Workshop + analysis** | Music core with tests, SQLite store, ideas with parts and versions, folders, drafts/songs, chord grid with parser dialects, lyrics, backup/restore, trash; **take analysis** (tempo, key/mode, instrument) |
| **P3 Stacks + Connections** | Time-stretch/pitch-shift engine, conform cache, stack editor, rule-based suggestions, record over stack, stack → idea/draft; Connections panel and view |
| **P4 Piano + Reference + Theory + draft playback** | Piano sampler (one dynamic, expandable), voicing generator, Reference, Theory pages incl. all modes, chart playback, note/chord layers in stacks |
| **P5 Transcription + tab** | Mono transcription → tab/MIDI, chord recognition, tab editor, note row, linked take timing |
| **P6 Player** | Slow-down, pitch/cents, loops, isolator, note finder (reuses the P3 engine) |
| **P7 Sheet music** | As designed, plus transcription → notation |
| **P8 Speech-to-text** | As designed, informed by P0 |
| **P9 Ear training, MIDI in, exports, Exercises** | Ear training incl. drills from your drafts; MIDI in; FL Studio export package incl. stack stems; the curated Exercises library with your content |
| **Backlog** | USB audio input, Bluetooth pedals, volume-key controls, second piano dynamic |

---

## 7. Open questions

1. **App id.** For apophenia.audio the conventional reverse-domain id is `audio.apophenia` (or `audio.apophenia.app`). It can't change after the first Play upload. Which one?
2. Look: two or three apps you want it to feel like or not; share the Better App Mafia look?
3. Still open from before: click bleed default; drafts and songs one list or two; folders flat or nested; mic default at launch; whether bundling a prebuilt native library is acceptable if the probe shows it works.

**Research to run next** (one pass, before P2/P3 detail): tempo detection and key/**mode** estimation on short single-instrument phone takes; monophonic transcription (pitch tracking, onset detection, quantisation) and chroma chord recognition; loop-friendly time-stretch at small ratios; CMU pronouncing dictionary licence and syllable/stress matching for lyric-to-melody fit.

Sources: [Splice blog — start with your own loop](https://splice.com/blog/?p=32455); [Splice support — mobile Create FAQ](https://support.splice.com/en/articles/9034571-splice-mobile-create-faq).
