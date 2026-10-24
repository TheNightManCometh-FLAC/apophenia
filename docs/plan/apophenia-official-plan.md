# APOPHENIA — Official Plan (v1.0, 21 October 2026)

**This is the single source of truth.** It replaces `practice-app-design.md`, `music-core-and-theory-design.md` (except its data-model detail, referenced in §6), `plan-audit-1.md`, `plan-audit-2.md`, `apophenia-independent-audit-3.md`, `apophenia-unified-product-plan-2026-10-21.md`, `apophenia-architecture-decisions-2026-10-21.md` and `apophenia-vision-check.md`. Those stay in the project as history and reference.

**How decisions are marked:**
- Everything not otherwise marked is **Christopher's decision**.
- **[Claude]** = an implementation choice made by Claude where Christopher left it open or it's purely technical. Any of these can be overruled.
- **[measure]** = must be measured on the phone before the feature is considered done.

Nothing in this plan is built yet.

---

## 1. What Apophenia is

**An offline Android app where rough musical scraps get captured, kept together, and worked through.** Recordings, lyrics, text, chords, tab, MIDI and sheet music all live in one place, without deciding what they're for first.

**The app doesn't make connections; the musician does, in their head.** Apophenia's job is to put the material in front of you — searchable, playable, readable, side by side — and give you the tools to work with it. It doesn't suggest, rank or match ideas.

**It's the rough side of music.** The song isn't finished in the app; finishing happens in FL Studio or a real session. Apophenia makes sure ideas survive, get understood and combined, and leave in a form a DAW can use.

**Full depth, not for beginners.** Theory, pitch training, notation and chord symbols are complete and correct at the advanced end; nothing is simplified or locked behind levels.

**Identity:** Apophenia, by Better App Mafia. App id `com.betterappmafia.apophenia` (permanent after the first Play upload). Free and open source, GPL-3.0. Channels: GitHub, Google Play, F-Droid. Offline: no internet permission. Website apophenia.betterappmafia.com (apophenia.audio was chosen earlier and can still point to it).

**Not:** a DAW, an AI songwriter, a suggestion engine, a sample store, a beginner course, a practice-session planner, or an engraving program.

---

## 2. The app's places

### 2.1 Five modes (bottom navigation)

*Christopher, 21 Oct 2026: Pitch Trainer and Theory are merged into one **Learn** mode. Later the same day: **AI** is its own fifth mode, not a section inside Tools (§10.6).*

| Mode | What it is |
|---|---|
| **Board** | The selected project's working space: windows onto its scraps, each opening its own editor (§4.3) |
| **Library** | Every scrap in one searchable list, mixed formats, tags on each row, Groups, filter and sort (§3) |
| **Tools** | Metronome, tuner, drone, recorder, playback speed, isolator, practice log (§7); metronome and tool shortcuts also live in the top bar |
| **Learn** | One mode with three parts: **Course** (§8: learn theory from the first two notes onward, hearing every example), **Ear** (§9: ear and pitch training, Listen / Practice / Test), **Reference** (§8: the full-depth lookup, on your tuning and found in your material). Each lesson ends in an ear check that leads into the matching Ear drill |
| **AI** | Every on-phone model in one place (§10.6): stem separation, notes → MIDI, sheet music and tab from audio, chords, tempo and key, speech → text, Performance check, plus the job list. Each recording's AI ▾ menu runs the same tools on that recording |

### 2.2 Always available

- **Persistent player** at the bottom, above the navigation: collapsed it shows what's loaded and transport; expanded it's the stack/session workspace (§5). It keeps playing and keeps its contents across all five modes.
- **Quick capture** from every screen: record audio, write lyrics or a note. Returns you exactly where you were.
- **Outside the app:** home-screen widget (record / write) and app shortcuts (long-press the icon: Record, Lyrics, Tuner, Metronome). They open straight into capture and work within Android's microphone rules (§10.4).

### 2.3 Launch

Opens to the last Board you used, with the player stopped and the mic Off. With no project yet, the Board shows **New project / Open project / Record / Write / Library**. No fake default project.

### 2.4 Look and feel

- Dark by default, light option. Large instrument-in-hand targets for record, play, stop and tap tempo. Stand mode (big readouts, landscape) for the Board and tools. One-thumb reach for the main actions. Accessibility: scalable text, screen-reader labels, colour never the only signal, 48 dp targets.
- **The visual style isn't designed yet.** Before any screen is built, Christopher approves clickable mockups of the core screens (M0). If he has reference apps or images he likes or hates, they go into that step.
- *Christopher, 21 Oct 2026, after the first three mockups:* direction **C · Console** (monospace, grid, hardware-panel feel) is the favourite so far, but its bright white and orange are harsh on the eyes; soften them.
- *Christopher, 21 Oct 2026:* **the bar at the top always holds metronome control and shortcuts/controllers for the tools**, on every screen. Agreed layout:
  - Click on/off with a beat light, − / BPM / +, and a visible Tap key.
  - Two slots the musician chooses in Tools (default Tuner and Drone).
  - **A quick recorder (● REC)**, which records to the Library. The stack's own record-over stays in the player.
  - The mic chip above it shows the mic's real state (Off, Recording, or on for the tuner).
- *Christopher, 21 Oct 2026 (round 3):*
  - **Use dropdown menus instead of rows of buttons.**
  - Never show three record buttons at once. The lyrics view has no record-over button.
  - **The stack's record button records the stack directly**, not the phone's speaker through the mic. *Confirmed by Christopher, 21 Oct 2026:* it is a bounce of the stack's own output with the mic off. Playing or singing a new part over the stack is the top ● REC while the stack plays.
  - **Fingering charts use dots** on strings and frets, like every real chart, never filled squares.
  - **"Slow-down player" is renamed "Playback speed"**: it's a variation of playback speed.

---

## 3. The Library

### 3.1 What's in it
Every recording, lyric, text note, chord chart, tab, MIDI file and sheet-music fragment, in **one list**. Rows look different by type (icon, preview) but sit together.

*Christopher, 22 Oct 2026: "You also need to be able to add files from your phone as fragments."* Importing is moved up from M7: audio files (any format Android decodes) become recordings, text and ChordPro files become text and lyric scraps, all tagged "imported" and kept in the Library (or a project when imported from its Board). [Claude] Audio is converted to the app's 24-bit 48 kHz WAV; a stereo song stays stereo (from 0.3.4, for the isolator's stereo position), mono stays mono. The original file is not kept.

*Christopher, 22 Oct 2026: "Also drummer charts as a fragment."* A **drum chart** is its own scrap format, in the Library and on the Board like the others. [Claude] It's a grid per bar, one row per drum (hi-hat, snare, kick, toms, cymbals), written and shown as drum tab (`HH|x-x-x-x-|`), with the metronome's meter and tempo; notation view of the same chart arrives with the notation editor (M5).

### 3.2 Layout
Search field at the top; small **Filter** and **Sort** buttons; the results list fills the screen; the player and navigation stay below. Opening Filter shows a temporary sheet; closing it returns to the same list with a short "filters on" note.

### 3.3 A row
Type icon · title (or first line) · **tags on the same row** (overflow shows +N) · play/preview without leaving the list · overflow menu. Badges: Groups, "in Love Song A, B" for projects using it. Tap to open; long-press to multi-select.

**Actions:** open, preview, edit tags, add to Group, add to project, add to the player, share/export, delete (to trash).

### 3.4 Tags (your own words; only BPM and key are built in)
*Revised by Christopher, 21 Oct 2026; this replaces the earlier typed-tag list.*
- **Tags are your own words**, as many per scrap as you like. What the thing is and what it's played on are tags like any other: "lyrics", "bass". One lyric can be tagged both "love song" and "political song". The app doesn't build a fixed set of tag types. **Tags are reusable**: once made, a tag is picked from a list instead of typed again.
- Only two are built in, because they have values the app reads:
  - **BPM**: a number, a range, or free time/unknown; half/double-time aware in filters.
  - **Key and mode**: e.g. D Dorian; C major and A minor kept distinct; unknown/non-tonal allowed.
- BPM and key show where they came from: **entered by you**, **from the metronome while recording**, or **estimated by analysis**. Your entries always win and are never overwritten by re-analysis.
- **Tags belong to the scrap, not to a project.** Using a scrap in a project never replaces, copies or moves its tags. Scraps are fragments that might fit in several places, and the workflow depends on things existing in more than one place.

*Christopher, 21 Oct 2026:* the database, search and organisation are core and must not be overlooked, but they mustn't take over the app either. They are usable throughout it, not its whole focus.

### 3.5 Groups
User-named collections ("Love songs", "Dark riffs", "Possible choruses"). A scrap can be in many Groups. Deleting a Group never deletes its scraps. **When a scrap is used in a project, you're offered one-tap removal from its other Groups.** Each Group has an "unused only" filter.

### 3.6 Filter and sort
Filter by Group, project use, tags, key/mode, BPM. OR within one property, AND across properties. Sort by recently captured, recently edited, name, tempo (items without a tempo grouped at the end, never given a fake value).

### 3.7 Takes
Several takes of one thing share its name plus a letter: **Verse riff A, B, C…** (**Take A, B, C** if unnamed). Star the ones you want, delete the rest; deletes go to trash for 30 days.

### 3.8 Trash and recovery
Everything deleted (scraps, takes, projects, Groups) goes to a 30-day trash with restore. Undo bar after every delete.

---

## 4. Projects and the Board

### 4.1 Projects
A project is a named working space with one Board and one or more saved stacks/sessions. Switch projects from the Board header without leaving the Board.

*Christopher, 21 Oct 2026:*
- A project is how you view just the things you're considering for one song. It decides what shows up on its Board.
- It is also the container for what you write specifically for it that ties several layers together: its stacks and what results from building on them.
- Putting a scrap in a project doesn't take it out of the Library or out of other projects. The same scrap can sit in several projects at once.

### 4.2 Using scraps in a project (no duplicates)
**A recording is never edited.** Adding one to a project stores a **reference plus that project's settings**: trim, loop points, gain, fades, tempo/pitch conform, offset. (Tags stay with the scrap everywhere, §3.4.) Changing these in one project changes nothing anywhere else, and the audio isn't copied. Lyrics, text, chords, tab, MIDI and notation are tiny; a project's version of one is its own copy, edited independently.

### 4.3 The Board — a working sheet

> **Revised by Christopher, 21 Oct 2026 (replaces the "one scrolling sheet of full blocks" layout below where they conflict):**
> - The Board is **not blocks you scroll down**. You're looking at several pieces you don't yet know will fit together, not one lyric or one tab. Each scrap on the Board is a **small window into what it is**: title and tags visible, a glimpse of the content.
> - **Tap a window to open it full size.** Each type has **its own editor**: lyrics, tab, chords, notation, recording, text.
> - **Lyrics open full screen and auto-scroll** when long, at a speed you adjust.
> - **Workflow:** add the recordings you want to a stack; look over lyrics or tabs and record more parts while the stack plays.
> - **A recording's full view** holds everything related to it: stems, MIDI, sheet music, tags, other metadata, and speech-to-text.
> - *Christopher, 21 Oct 2026:* windows are **arranged freely** by the musician.
> - Open questions for Christopher are listed in `docs/STATUS.md`.

**What it's for:** sitting with your instrument and working on one song: read it, play along to it, record over it, edit it in place.

**One vertical scrolling sheet** of blocks in the order you put them:
- **Lyrics**, with chords above the words where placed.
- **Chord charts** (bars and chords).
- **Tab** for the project's instrument and tuning, with the note row (§6.4) under it.
- **Sheet music** as a continuous strip of systems.
- **Recordings** as inline players with waveforms.
- **Text** notes.
- Optional **section headings**.

**The Board is not the song structure.** Block order is just how you've laid things out. Song structure is worked out with the tools however you like: written in tab or sheet music, by recording sections and arranging them in the player, or not at all.

### 4.4 Playing along on the Board
- Choose what plays: a recording, a stack, or the chord chart / tab / notation played on piano.
- **The sheet scrolls with playback.** Tab and notation follow a cursor through the music. Lyrics and chords follow a recording at synced points; you sync by tapping each line as it goes by while listening, once, then nudge if needed. Unsynced blocks can scroll at a steady speed you set, or stay put.
- Loop any part; record over the playback without leaving the sheet; the lyric you're singing stays visible.
- **Stand mode:** large type, landscape, scroll locked to playback, one-thumb controls.

### 4.5 Editing on the Board
Every block is edited where it sits: type lyrics, enter tab frets, enter notes on the staff, type chords. Blocks can be added, reordered, collapsed or hidden while playing. Undo for every edit.

---

## 5. The player and stacks

### 5.1 What it does
Play several recordings together, try combinations, and record over them — like Splice Stacks, but with your own recordings and offline.

### 5.2 Layers
- Any number of layers **[Claude: no fixed limit; the practical limit is found by measurement] [measure]**.
- Each layer is a recording (or a section of one) in one of three modes: **loop** (repeats between loop points), **one-shot** (plays once from a chosen point), **free time** (plays at its own natural timing, never forced onto a grid).
- Per layer: gain, mute (tap), solo (long-press), delete (swipe), trim, offset/nudge, loop points.
- **Adding a layer:** opens the Library filtered for audio, with quick filters for the stack's key, mode, tempo and instrument. No suggestions.

### 5.3 Tempo and key (conform)
- A stack has a tempo and key (from its first layer, editable).
- Loop and one-shot layers **conform automatically** to the stack's tempo and key, showing the amounts ("+2 st, 92→96 BPM"); one tap turns conform off for a layer. Free-time layers are never conformed. Layers with unknown key aren't pitch-shifted until you set the key.
- Conformed audio is rendered in the background and cached; the original file is never touched.

### 5.4 Recording over a stack
- Record a new take over the playing stack (headphones recommended; the record screen notes when the speaker will leak into the take).
- **In a project's stack, the take goes to that project immediately**, as a new layer, and shows in Library search unless you switch that off on the save sheet.
- Takes are lettered and starred as in §3.7; compare takes by switching them in place.
- Latency between what you hear and what's recorded is measured once per output route (wired, Bluetooth, speaker) with a calibration step, and compensated so overdubs line up **[measure]**.

### 5.5 Saving and leaving
Stacks save with the project (named, duplicable) or as temporary stacks. Switching project while a stack is loaded shows which project the stack belongs to and asks before replacing it. Export: mix, stems (conformed or originals), and MIDI for note layers (§11).

---

## 6. The music core (shared by everything)

One pure Kotlin module used by Board, Library tags, player, tools, transcription, Theory, Pitch Trainer and exports. It has no Android dependencies and its own test suite. Full data detail is in `music-core-and-theory-design.md` §1–5 and the MusicXML research; the essentials:

### 6.1 Pitch and intervals
- **Spelled pitch** = letter + accidental (double flat to double sharp) + octave. B♭ and A♯ are different things; MIDI number and frequency are derived.
- **Interval** = letter distance + semitones, so A4 and d5 (the two tritones) stay distinct.
- Spelling rule: each degree gets its own letter (F major has B♭; Cdim7 has B𝄫; E7♯9 has F𝄪).

### 6.2 Chords and symbols
- A chord is stored as **structure**, mirroring MusicXML 4.0: spelled root (or Roman numeral), one of the 33 MusicXML chord kinds, ordered added/altered/omitted degrees, spelled bass, and the text you typed.
- **Display styles:** pop, jazz, classical/analysis, plain text. Real typesetting: superscript extensions, stacked alterations, slash bass, polychords with a horizontal line, Roman numerals with stacked figures. Glyphs from the Bravura music fonts (SIL OFL).
- **Parser** accepts how people actually write chords, with dialect settings: pop, jazz/iReal Pro, German (H = B♮, B = B♭), Do-Re-Mi names. Ambiguous symbols (C2, C°, C7+, Cmaj, C11) follow the dialect and show the alternative.
- **Transposition** re-spells correctly for the target key.

### 6.3 Rhythm
Exact rational durations, dots, ties, **any tuplet ratio including nested**, additive and compound meters (7/8 = 2+2+3, 6/8 as two beats), tempo with its beat unit, swing ratio.

### 6.4 Instruments, tuning and tab
- An instrument is **string count + tuning (a note per string) + capo**, plus fret count for drawing. Presets: guitar standard, drop D, D standard, DADGAD, open G, open D, 7-string; bass E standard, D standard, drop D, drop A, 5-string, 6-string; ukulele. Any custom tuning.
- **Every tab note stores string, fret and pitch together.**
- **Note row** under tab: note names, scale degrees in the key, or intervals from the chord root. Tapping a fret number lights the same note on the staff, piano and fretboard (with its other positions on the neck shown) and plays it.
- Written vs sounding pitch per instrument (guitar and bass sound an octave below written).
- Changing a part's tuning or capo asks: **keep the notes** (re-finger) or **keep the fingering** (pitches move).

### 6.5 Storage of musical data
All musical text uses plain ASCII tokens (`b # bb x`) in storage; the real symbols are only drawn on screen. This avoids the two-character encoding of 𝄪/𝄫 breaking text handling.

---

## 7. Tools

| Tool | Scope |
|---|---|
| **Metronome** | Tap / drag / type BPM (20–400); additive and compound meters; accents per beat; subdivisions incl. 5 and 7; swing; polyrhythm layer; count-in; tempo trainer (+N BPM every M bars); silent bars; visual beat; sample-accurate timing. Its beat grid is saved with any take recorded while it runs |
| **Tuner** | Chromatic; cents; A4 reference; temperaments (equal, just, Pythagorean, meantone) relative to a tonic; note names spelled in the current key; octave label setting (C4 default, MIDI number shown); search range limited to the selected tuning internally (no string mode) **[measure: drop-A bass on the phone mic]** |
| **Drone** | Any root, octave, fifth, third (major/minor/off); piano or sustained tone |
| **Recorder** | Same save path as quick capture; markers while recording; input level and clipping warning |
| **Playback speed** | Import any audio file; speed 0.25–2× without pitch change; pitch ±24 semitones and ±100 cents; A/B loops; marks; step-up loops (speed rises each pass); count-in per loop |
| **Isolator** | Adjustable, dialled in by you: stereo position (keep/remove what's panned where), tone ⇄ hit (harmonic/percussive balance), band (low/high cut); presets as starting points; bypass A/B; note finder (spectrum with piano and fretboard showing the strongest notes) |
| **Stem separation** | Spleeter 4-stem (vocals, drums, bass, other) or 5-stem (+ piano) on a take or imported song, as a background job; stems saved as their own items linked to the source, **in stereo** (*Christopher, 22 Oct 2026: "yes, stereo"*) **[measure]** |
| **Practice log** | A simple timer you can attach to a scrap, project or exercise; optional note; history. No structured session builder |
| **Exercises** | A library of curated exercises; **contents chosen by Christopher at the end** |
| **Mic control** | Mic state always visible: **Off / Recording** (and the tuner's mic while its screen is open). **No pre-roll and no Listening state** (*Christopher, 22 Oct 2026, asked how long the pre-roll should be: "none then"*): the mic opens only for ● REC and the tuner. In-app permission control, including revoking the permission from inside the app (it takes effect when the app closes, per Android) |

---

## 8. Theory — full depth

> **Revised by Christopher, 21 Oct 2026:**
> - **Theory is for actually learning**, not only looking things up. *"Knowledge is for the people. Anyone should be able to learn music theory for free and learn it right."*
> - Layout, coverage and order are informed by **Rick Beato's courses**. Claude studies them and summarises them; no copying of his material.
> - Lessons carry **sound file references** (hear every example).
> - **Self-testing mode:** hear chords, modes, notes and intervals and identify them.
> - A **real reference tool**, strong enough to be an app (or several) on its own.
> - *Christopher, 21 Oct 2026:* the course is **not a literal prescribed course**. Nothing is locked; everything is open freely. It really explains and demonstrates the fundamentals. **Levels are only a suggested complexity gauge.** Its organisation follows the *design* of Rick Beato's courses (not his content).

**Scope:** everything, beginner to advanced, all browsable: pitch and tuning systems; all intervals incl. compound and the tritone in all its roles; every scale and mode (major, melodic and harmonic minor modes, symmetric scales, bebop, harmonic major, world scales as labelled approximations); keys and spelling; every chord type in the catalog incl. altered dominants, polychords, quartal and cluster voicings; diatonic and chromatic harmony (secondary dominants, modal mixture, Neapolitan, augmented sixths, common-tone diminished, omnibus); jazz harmony (chord-scale theory, guide tones, tritone subs, upper structures, Barry Harris 6th-diminished, Coltrane changes, negative harmony with its correct definitions); contemporary and post-tonal (set theory, Messiaen modes, neo-Riemannian); rhythm and meter; melody and counterpoint; form; notation and tab; acoustics and the harmonic series.

**Every page:** definition → structure (formula, spelling) → interactive view on piano, staff and **your current tuning's fretboard** (change the root and everything regenerates) → hear it, with A/B comparisons → where it occurs → **where it appears in your own material** → naming disputes between sources.

**Generated where possible:** every interval, scale, mode, chord and named progression gets a page built from the catalog, with hand-written commentary; concept pages are hand-written. Text from Open Music Theory (CC BY-SA 4.0) may be adapted with attribution (one-way compatible with GPL-3.0).

**Ships complete.** Theory is built after the creation workflow works (M6), but the release includes the full scope.

**Drums in the Reference.** *Christopher, 22 Oct 2026: "Patterns for drummers as well, paradiddles and all that"; then: "I didn't mean for the click. I meant as reference material so drummers are included."* The 40 standard rudiments (Percussive Arts Society list: rolls, diddles, flams, drags) are Reference pages: sticking (R L R R L R L L) with accents and grace notes, and **hear it** at a tempo you choose, the stroke being played lit. *Then: "Add common grooves to the drum Reference too."* Common grooves (rock, half-time, punk, Motown, funk with ghost notes, shuffle, 12/8 blues, jazz swing, disco, boom bap, trap, reggae one drop, country train beat, 6/8 bell) as drum tab, each with **hear it**. [Claude] Generic textbook versions of each style, not transcriptions of records. Built ahead of M6, because it needs nothing else from the Theory work. More groove styles come later (*Christopher, 22 Oct 2026: "grooves later"*). The rudiment stickings and roll rhythms wait for a check by a trained drummer; Christopher: "Keep those on hold, I'm not a trained drummer."

---

## 9. Pitch Trainer — full scope

On real piano sound; hear-again, compare and replay-answer everywhere; mixed practice by default; answer on your own fretboard or the piano as well as by name.

- **Pitch:** note naming with/without reference; octave placement; pitch matching by voice or instrument (graded by the tuner); fine pitch discrimination in cents.
- **Intervals:** all simple and compound, melodic up/down and harmonic, in key context; singing intervals.
- **Scale degrees:** functional hearing after a cadence or over a drone; chromatic degrees.
- **Scales and modes:** identify over a drone (all modes of major, melodic and harmonic minor, symmetric scales); characteristic-note drills.
- **Chords:** triads, sevenths, sixths, extended, altered, sus, add; inversions and bass note; voicing spread.
- **Harmony:** Roman numerals in major and minor; cadences; secondary dominants, borrowed chords, Neapolitan, augmented sixths, tritone substitutions; modulation targets.
- **Melody and bass:** melodic dictation; bass-line dictation; chord tones vs passing tones.
- **Rhythm:** dictation incl. tuplets, odd and additive meters, swing; tap-back accuracy; tempo estimation.
- **Tuning and timbre:** equal vs just intonation, beating, harmonic partials.
- **Your own music:** chords of your own progressions, phrases from your own takes, key/mode of your own riffs, tab-it-by-ear from your recordings.
- Extra weight on commonly confused intervals (m6/M6, m7/M7, tritone).

---

## 10. Recording, analysis and transcription

### 10.1 Saving recordings
| Recorded from | Goes to |
|---|---|
| Quick capture, widget, shortcut, Tools recorder | Library |
| A project's Board or stack | That project (also in Library search by default) |
| A temporary stack | Library |

Audio is written to disk as it records (crash-safe; partial takes recovered on next launch). After Stop, a **save sheet** with nothing required: title, tags (the instrument tag pre-filled), BPM/key/mode as analysis arrives, take letter, flags (show in Library — on by default for project recordings; add as a stack layer; Groups). Closing the sheet keeps everything.

### 10.2 Take analysis
- **Tempo:** the metronome's grid is saved when it ran (labelled "click"); a performed-tempo estimate is computed separately, with half/double alternatives, or "free time".
- **Key and mode:** ranked candidates from the notes and an estimated home note, with the reason shown; stays "uncertain" when a riff never settles. Your correction wins.
- **Instrument:** picked by you.
- Runs in the background, never blocks playback or saving.

### 10.3 Transcription: recordings into MIDI, tab and sheet music
- **Polyphonic notes:** Spotify **Basic Pitch** (Apache-2.0, 0.84 MB TFLite model; MIDI notes 21–108) via TensorFlow Lite on the phone.
- **Single lines:** pitch tracking + onset detection in the app's own code (fast first pass and fallback).
- **Chord names:** per-beat chord recognition → chord chart.
- **Outputs from the same notes:** MIDI; tab in the take's tuning (string/fret chosen by the fingering chooser, every position changeable); sheet music spelled in the take's key at the instrument's written pitch; chord chart. **Tab is for fretted parts only:** a sung line gets MIDI, sheet music and a chord chart, not tab (*Christopher, 22 Oct 2026: "singing isn't really expressed by tabs"*).
- **Quantising is a separate, reversible step**; the unquantised notes are visible against the waveform.
- **Correction screen:** waveform and notes together, play original vs transcription, fix pitch, timing and string.
- The transcription is its own item, time-linked to the take: tap a note to hear that moment. The recording is never changed.
- Honest limits shown in the app: bends/slides/vibrato become wobble to clean up; dense mixes much less accurate than one instrument; low bass on the phone mic can come out an octave off.

### 10.4 Speech to text ("thinking out loud")
- **Moonshine** English model (MIT) on the app's ONNX Runtime (one runtime with the stems; sherpa-onnx would bring a second copy) **[Claude, 22 Oct 2026: Base int8 (63 MB), the only Moonshine format with a documented interface; Small Streaming (~123 MB) only if Base reads Christopher's memos badly — `docs/research/speech-model-2026-10-22.md`] [measure]**.
- The voice memo is always kept; the transcript is time-stamped against it and editable without touching the audio.

### 10.5 Notation entry (when writing rather than transcribing)
Pick a duration and tap the staff (drag to adjust pitch, hearing it); tap a piano key or fret on a panel; play a note on your instrument and the pitch tracker places it; tap a rhythm then fill pitches; MIDI keyboard; S Pen hover preview. Automatic ties across barlines, beaming by beat, rests filling bars; undo, multi-select, transpose, copy/paste. Rendered with Bravura; readable rather than engraving-quality; MusicXML for anything beyond.

### 10.5a A real sheet-music writer and editor
*Christopher, 21 Oct 2026:* Apophenia needs **the full notation of a real sheet-music writer and editor**, informed by:
- the PC notation programs (Sibelius, Dorico, MuseScore; Finale is discontinued);
- **FL Studio's piano roll menus**;
- tablature software (Guitar Pro, TuxGuitar).

The feature inventory and Claude's proposal are in `docs/research/notation-pianoroll-tab-editors-2026-10-21.md`: one note model with notation, tab and piano-roll views, palette-style menus, Guitar Pro's effect set, and FL-style piano-roll tools. Generators (riff machine, chord-progression generator, randomize) are left out under the no-suggestions rule; *Christopher confirmed, 21 Oct 2026: leave them out.* His C4 mockup review the same day: "that's good".

### 10.6 AI tools get their own visible section, and Performance check
*Christopher, 21 Oct 2026:*
- The AI tools (stems, MIDI, sheet music from audio, chords, tempo/key, speech-to-text) need **their own visible section**, not a "job / stems / 4" tile.
- **Sheet music** (writing and viewing), **MIDI**, and **piano** (keyboard, piano fingerings) must be present in the design.
- New: **Performance check.** Get the MIDI of a song, record yourself singing (or playing guitar or bass) it, and see how closely you matched it. It points out the sections you got wrong, and why/how.

**Performance check, what the planned models can actually do [Claude, from §10.3; accuracy is [measure]]:**
- **Reference** (what you're measured against): your own MIDI or notation, a transcription of one of your takes, or an imported song → Spleeter stem → Basic Pitch MIDI. A reference made by transcription carries its own errors, so it is shown and editable before the check runs.
- **Your take:** the same pitch tracking (§10.3) gives a continuous pitch curve plus note onsets, aligned to the reference by dynamic time warping, so a slightly different tempo doesn't count as wrong.
- **What it reports, per note and per section:**
  - cents sharp or flat;
  - wrong note (and by what interval);
  - missed or extra notes;
  - early or late by milliseconds;
  - **why** where the curve shows it: scooping up into a note, drifting flat on a held note, vibrato centred off pitch, rushing a phrase, rhythm slipping on syncopation.
  - Each problem plays yours against the reference.
- **By instrument:**
  - **Monophonic lines** (voice, bass, single-note guitar, any melody instrument): the strongest case, from the pitch tracker.
  - **Chords** (guitar, piano): notes present or missing per chord, from Basic Pitch, at lower confidence.
  - **Drums and percussion:** timing only, from onsets.
  - So it works on **any instrument** for timing, and on any instrument that plays one note at a time for pitch.


## 11. Getting things out

- **FL Studio package** per project or stack: a MIDI file (tempo map, time signatures, markers, chord and note tracks), takes as WAV with their positions (originals and/or conformed stems), and a plain-text manifest **[measure: how FL Studio imports markers and meter changes]**.
- **MusicXML 4.0** (notes, chord symbols, tab, tuplets), **MIDI**, **ChordPro**, plain-text tab, WAV.
- **Backup and restore** of everything (database and audio) to a folder you choose, manual and scheduled; restore tested on a clean install.
- Import: audio files, MIDI, MusicXML, text/ChordPro.

---

## 12. Technical architecture

**[Claude, confirmed by Christopher]**
- **Kotlin**, **Jetpack Compose** for screens; custom drawing for the Board sheet, waveforms, tab, notation and fretboard.
- **Audio engine in C++ on Oboe**: one output mixer (player layers, metronome, drone, piano), one controlled microphone owner (recorder, tuner, speech, pitch-tracking entry); real-time-safe callbacks; analysis and rendering on worker threads.
- **Time-stretch/pitch-shift** for conform and playback speed: **Signalsmith Stretch (MIT)**. *Christopher, 22 Oct 2026: "Signalsmith. This is for getting the structure, not perfect quality recordings from your phone."* (Rubber Band, GPL-2.0-or-later, was the other candidate; swappable behind one interface if it ever sounds wrong.)
- **ML runtimes:** TensorFlow Lite for Basic Pitch; ONNX Runtime/sherpa-onnx for Moonshine; Spleeter converted to TFLite or ONNX **[measure]**. All behind interfaces so the app works if a model fails to load.
- **Music core:** pure Kotlin/JVM module + tests (§6).
- **Storage:** SQLite via Room (Library items, takes, tags, BPM and key with provenance, Groups, projects, project references + settings, Board blocks and sync points, stacks/layers, transcriptions, stems, practice log, settings); audio files written once and never modified; derived renders in an evictable cache; versioned migrations from day one.
- **Piano sound:** University of Iowa Steinway samples (public domain), one dynamic now, built to add a second later.
- **Fonts:** Bravura and Bravura Text (SIL OFL), unmodified.
- **Permissions:** RECORD_AUDIO only (plus the foreground-service types needed to keep recording with the screen off); no internet.

**Where work happens:** builds and tests run on Christopher's computer (Android Studio, SDK, NDK). If the chat is linked to that computer through the Claude desktop app, Claude runs Gradle builds, unit tests and installs there; otherwise Claude writes the code and Christopher builds. Claude's cloud workspace can't run Gradle (Google's servers are blocked there), but can run the pure-Kotlin core tests.

**Source:** a new GPL-3.0 GitHub repository for Apophenia; the Claude GitHub app installed on it so sessions can push. Signing keys never committed.

---

## 13. Build order

Each milestone ends with an **on-phone walkthrough on the S22 Ultra** (SM-S908U, Android 16) and its tests passing. Later milestones never undo earlier decisions.

| Milestone | Builds | Done when |
|---|---|---|
| **M0 — Foundation and mockups** | Repo, Gradle project (app, core, audio-engine native module), Room schema v1, music core types with desktop tests; **Device Lab** screen (a hidden screen in debug builds) with the measurements in §14; clickable mockups of every main screen for approval | Christopher approves the mockups; Device Lab runs on the S22 Ultra and its first measurements are recorded |
| **M1 — Capture and Library** | Oboe engine (record/play), mic states and controls, quick capture, widget, app shortcuts, save sheet, takes A/B/C with star/delete, Library list/search/filter/sort/tags/Groups, trash, backup/restore, crash recovery | Record from the widget with the app closed, lock the phone, come back, find the take by search, restore it from a backup on a wiped install |
| **M2 — Projects, Board and player** | Projects; Board sheet with lyrics, text, chords and recording blocks; persistent player with loop/one-shot/free-time layers; conform with the chosen stretcher; overdub with latency calibration; project recordings; lyric line sync and sheet scrolling | Build a stack of three of your recordings in different tempos, sing over it while reading a lyric on the Board, keep takes B and D |
| **M3 — Tools** | Metronome (full), tuner, drone, recorder, playback speed, isolator with note finder, practice log | Learn a bass line from an imported song at 0.6× with the isolator and loop, record your version with the click |
| **M4 — Analysis and ML** | Tempo/key/mode analysis; Basic Pitch transcription + single-line tracker + chord recognition; correction screen; MIDI output; Spleeter stems; Moonshine speech-to-text | Record a riff, get editable MIDI and chords, separate a song into stems, dictate a lyric idea and get a time-stamped transcript — each meeting its §14 target |
| **M5 — Written music** | Note model; tab editor with note row and linked highlighting; notation editor with all entry methods; transcription → tab/notation; piano sampler; chart/tab/notation playback; Board cursor scrolling | Transcribe a take, correct it in tab, see it as sheet music, play along as the Board scrolls |
| **M6 — Theory and Pitch Trainer** | Catalogs, generated and written Theory pages, full Pitch Trainer, "in your material" links | Every catalog chord and scale spells correctly on all 35 roots; every Theory page has its interactive view and audio; every Pitch Trainer area works |
| **M7 — Exports and release** | FL Studio package, MusicXML/MIDI/ChordPro/tab export, imports; Exercises framework filled with Christopher's chosen content; privacy and licence notices; Play AAB, GitHub release, F-Droid recipe | Import an exported project into FL Studio with everything in place; clean-install restore; all three channels build |

---

## 14. Testing plan

### 14.1 Automated (every build)
- **Music core:** every chord quality and scale on all 35 roots (no triple accidentals; known traps like Cdim7 B𝄫, E7♯9 F𝄪); interval tables; chord parser round trips in every display style and dialect; real-world chord fixtures from the McGill Billboard dataset (CC0) and the CC BY parts of ChoCo; MusicXML round trips (incl. tab string/line numbering and nested tuplets); rhythm arithmetic; voicing generator; tab note storage; transposition.
- **Storage:** migrations, crash between file write and database insert, backup/restore integrity, trash expiry, per-project settings isolation (changing a project's trim never affects another project or the Library).
- **Theory pages:** every link and example resolves.

### 14.2 Device Lab measurements on the S22 Ultra (from M0, repeated at each milestone)

| Measurement | Target **[Claude, proposed — Christopher can change]** |
|---|---|
| Round-trip audio latency per route (wired, Bluetooth, speaker) and overdub alignment after calibration | Takes line up within ±5 ms after calibration |
| Microphone sources (unprocessed, voice recognition, default): noise, gain, low-frequency response | Pick the cleanest; document |
| Tuner on drop-A and D-standard bass, phone mic | Correct note and octave on every open string |
| Simultaneous playback + recording + metronome | No dropouts over 10 minutes |
| Stack playback | Number of conformed layers before dropouts; record the limit |
| Stretcher quality and speed (Rubber Band vs Signalsmith) at ±5%, ±20%, ±2 semitones | Choose the better; no clicks at loop seams |
| **Basic Pitch** speed and accuracy | 1 minute of audio transcribed in under 10 s; note accuracy measured against hand-annotated takes (bass riff, guitar chords, sung melody) and reported, not assumed |
| **Spleeter** 4-stem on a 3-minute song | Finishes in the background without Android killing it; time and memory recorded; under 5 minutes |
| **Moonshine** speech | Faster than real time; word error rate on your own voice memos (quiet room, over an instrument) measured against your corrected transcripts |
| Widget/shortcut capture with the app closed and the phone locked | Starts recording within Android's rules on Android 16 |
| Battery and heat during a 30-minute session | Recorded |
| Glyph rendering (𝄪, 𝄫, Bravura) | Correct on screen |

### 14.3 Test recordings Christopher provides
Your own recordings, kept locally: bass in D standard with and without click (~92 BPM); drop-A low notes; a guitar strum (e.g. C–G–Am–F); a short Dorian vocal phrase; a free-time vocal; a few spoken voice memos (one over an instrument); one full song for stems. For each, write down what's actually played/said; that's the answer key for accuracy.

### 14.4 Walkthroughs
Each milestone's "done when" is walked through on the phone with headphones and with the speaker, then repeated after an interruption (call, lock, app switch) and after a forced app kill.

---

## 15. Assets and what Claude needs

| Item | Status |
|---|---|
| Iowa MIS Steinway piano samples | Download on Christopher's computer (public domain); Claude prepares the trimmed set |
| Bravura / Bravura Text fonts | From Steinberg's GitHub, unmodified, with OFL text |
| Basic Pitch TFLite, Moonshine English, Spleeter weights | Downloaded during M4 and **bundled in the APK** (*Christopher, 22 Oct 2026, offered importing model files instead: "Bundle."*), so the APK grows to about 130 MB with stems and more with speech; licences recorded in the app's notices. Spleeter: Deezer 4stems converted to ONNX (`docs/research/stems-models-2026-10-22.md`) |
| Test recordings and answer keys | §14.3, from Christopher |
| GitHub repo + Claude GitHub app | Christopher creates/installs before M0 |
| Desktop app link to his computer | So Claude can build and install directly |

---

## 16. Not in this plan (decided)

No app-made connections or suggestions. No structured practice sessions. No tuner string mode. No "new/unreviewed" marker. No automatic instrument detection. No cloud, accounts or internet. USB audio interfaces, Bluetooth pedals and volume-key controls: later polish, after release. Second piano dynamic: later, by Christopher's choice.
