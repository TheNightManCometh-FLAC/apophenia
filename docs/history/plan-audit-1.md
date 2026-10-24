> **SUPERSEDED (Oct 21 2026).** Product direction now comes from `apophenia-architecture-decisions-2026-10-21.md` (user-approved) and `apophenia-unified-product-plan-2026-10-21.md`. Keep this file for history and for detail not covered there; where they differ, they win.

# Plan audit 1 — does the design deliver what each piece is for? (Oct 20 2026)

Audits `practice-app-design.md` (v2.1) and `music-core-and-theory-design.md` (v1) against the goals Christopher set, using the Oct 20 research reports (theory engine; MusicXML/advanced topics/chord corpus). For each piece: the goal in his words, what the plan does, where it falls short, and the better way. Changes adopted here are folded into the two design docs (v3 / v2); this file records why.

Nothing here is built or tested yet. **[verify]** = needs a probe build, a source, or an answer from Christopher.

---

## A. The three biggest problems with the plan as it stood

1. **There is no visual design at all.** The docs describe data and features but never what a screen looks like, how you move between screens, or how it works with an instrument in your hands. "Will it look like I expect?" — I can't answer yes, because I don't know what you expect, and the plan never asked. **Fix:** clickable phone mockups of the core screens before phase 0.1 is built, and a short list of apps or styles you want it to look like (or not). See section C.
2. **Capture only works once you've opened the app and found the button.** The whole point is not losing an idea, but every capture path started inside the app. **Fix:** capture from outside the app: a home-screen widget, a Quick Settings tile, an app-shortcut ("Record idea") and a hardware trigger. See D1.
3. **Storage was per-draft JSON files, which doesn't fit the Workshop.** Fragments live in many folders and drafts at once, need "used in" lookups, full-text search over lyrics and transcripts, and the Theory section's "in your music" queries ("every ø7 you've used"). That's a database's job. **Fix:** SQLite (built into Android, no AndroidX needed) with full-text search; audio stays as files. See D2.

---

## B. Things the plan got right and keeps

- One audio engine with a tool rail that survives navigation; pre-roll; sample-accurate metronome.
- Fragments as the unit, linked not copied; scratch pad as a computed view; folders many-to-many.
- One music core under every feature: spelled pitches, letter-based spelling, structure stored and symbols rendered.
- Full depth in the data model everywhere; levels as labels.
- Instruments defined by strings + tuning + capo; piano as the one sound source.
- Phasing with a phone walkthrough at the end of each phase.

---

## C. Look and feel — what has to be decided before building

**What I need from you [verify]:**
1. Two or three apps whose look you like (and any you hate). Music apps or not.
2. Whether it should share a look with Independent Video / Afterglow (Better App Mafia family) or stand alone.
3. Portrait-first is assumed for capture and tools. Tab and notation editing work much better in **landscape**; I'd support both there.

**Design rules the plan was missing, now adopted:**
- **Instrument-in-hands use.** You're often holding a bass. Primary actions (record, metronome start/stop, tap tempo, loop on/off) are big targets in the bottom third of the screen, reachable with one thumb, and also mapped to hardware: **volume keys** (when enabled in settings, while the app is open) and **Bluetooth page-turner pedals / keyboards** (they send key events; Anytune and Transcribe+ both support pedals). A foot pedal is the only way to start a take with both hands on the instrument.
- **Glanceable from a music stand.** The rail's readouts (BPM, note + cents, REC timer) stay readable at arm's length; a "stand mode" enlarges the active tool to full screen.
- **Lock screen and notification controls** while the foreground service runs: metronome start/stop and tempo ±, record/stop, mic state. You shouldn't need to unlock to stop a click.
- **Dark theme by default**, with a light option; function colours (tonic/predominant/dominant/borrowed/secondary) chosen to be colour-blind-safe and always paired with a label, never colour alone.
- **Navigation:** five top-level places — Workshop, Practice, Player, Reference & Theory, Tools — plus the rail always visible. The rail is collapsible to a thin strip so it doesn't eat the screen on tab/notation editors.
- **Every control has the full set of options, not just the default one** (the same discipline as the plugin-interface skill, applied to phone UI): drag, tap ±, type an exact value, long-press for a menu, undo, and the last value remembered. Per-tool option lists are in section E.

---

## D. Better ways, piece by piece

### D1. Capture (goal: never lose an idea; move between tools without losing flow)
| Was | Better | Why |
|---|---|---|
| Record button inside the app only | **Home-screen widget** (big REC button + "→ Scratch pad"), **Quick Settings tile**, **app shortcut** (long-press icon → Record idea / Tuner / Metronome), **pedal or volume key** | Spit Notes' selling point is recording the instant the app opens; we go one step earlier. Tile/widget launch the app's recording screen directly. **[verify]** Android 14+ limits starting a microphone foreground service from the background; launching our activity from the tile/widget and starting the mic there is the safe route. |
| Pre-roll only while Listening | Same, plus **pre-roll is on automatically while the metronome, player or tuner is running** (you're playing), off otherwise | Keeps the privacy promise (mic chip shows Listening) while catching riffs during practice, which is when they happen. |
| A take is a single file | **Takes of the same idea are grouped as versions**, with a "best take" star | You record a riff five times; they should be one idea with five takes, not five fragments. |

### D2. The Workshop (goal: fragments in user folders, in several at once, cleaned up as they get used)
- **Fragment becomes an "idea" that can hold several parts**: takes (versions), a tab/notation part, a chord part, a lyric part, a text note. Before, a fragment had one type, so "riff recording + its tab + a note about the tone" was three fragments to keep in sync. Spit Notes and Suonote keep voice and lyrics in the same note; that's the better pattern. A single-part idea still works exactly like the old fragment.
- **Linked timing inside an idea:** a tab part can point at a take's time range, so tapping a bar plays the matching moment of the recording. That's the bridge between "what I played" and "what I wrote".
- **SQLite storage** (replacing per-draft JSON): tables for ideas, parts, folders, drafts, sections, links, practice items, sessions; full-text index over titles, lyrics, notes and transcripts; chord objects stored as structured rows so "where have I used ø7" is one query. Audio as WAV files referenced by id. Format versions per table, migrations tested on the desktop. **[verify]** Desktop tests need a JDBC SQLite driver, and Maven Central is blocked in the workspace; if it isn't on GitHub releases, the storage layer sits behind an interface and the tests run against an in-memory fake.
- **Backup is mandatory, not a nice-to-have.** No internet means no cloud; one phone failure would lose everything. Full backup (database + audio) to a folder you pick through the system picker — a folder you sync to your PC or Drive with something else — manual and on a schedule, plus restore. Android's own auto-backup has a 25 MB cap and would miss the audio.
- **Undo for organising** (removing from folders, deleting): a 10-second undo bar plus a 30-day trash. Deleting an idea is the one action that can't be cheap to get wrong.

### D3. Drafts and songs (goal: plan the song structure; understand it; hand it to the DAW)
- **Missing piece: hear the chart.** A draft with sections, bars and chords should **play back as a backing**: piano comping of the chord grid at the draft's tempo and meter, with the click, looping any section. That's iReal Pro's core idea and it's what makes a chord chart something you practise against instead of a list. Piano is the one sound, so: piano voicings from the voicing generator, simple comping patterns (block, arpeggio, Charleston/two-feel), and an optional root-note bass line you can mute when you play bass yourself.
- **Lead-sheet view**: one scrollable view per section with chords over lyrics, a tab/notation strip if there is one, and the takes linked to that section. Before, chords, lyrics, tab and takes lived in separate editors.
- **The metronome follows the open draft**: section tempos and meters (including meter changes) drive the click automatically, so practising the bridge in 7/8 doesn't mean reprogramming the metronome.
- **DAW handoff made concrete for FL Studio**: export a folder per song with a type-1 MIDI file (tempo map, time signatures, section markers, a chord track and a notes track per tab/notation part) and every take as WAV **padded to start on a bar line**, named `section_bar_take.wav`, so dropping a take in at its bar lines up without nudging. Plus ChordPro, MusicXML and a text summary. **[verify]** how FL Studio reads MIDI markers and time-signature changes on import.

### D4. Tools rail (goal: tools that keep running across the app)
- **Metronome:** add **haptic beat** (vibration) for silent practice, a spoken or tone **count-in**, and section-following from the draft (D3). Haptic timing on Samsung may lag the audio **[verify]**.
- **Tuner:** add a **string mode** that snaps to the nearest string of the current tuning (cuts octave errors on bass, the risk in section 6 of the main doc), alongside chromatic mode; strobe and needle displays; **cents-target memory** for intentionally off tunings.
- **Recorder input: support a USB audio interface.** This is the biggest quality fix in the audit. Android supports class-compliant USB audio input, and `AudioRecord.setPreferredDevice()` (API 23) picks it. A direct bass signal through an interface gives clean takes and fixes the phone-mic problem with drop A in one move. You use a Behringer Xenyx mixer; some Xenyx models (the "USB" and "Q…USB" ones) have a USB output — **which model is yours? [verify]**. Input picker on the rail: phone mic, wired headset mic, USB.
- **Monitoring through headphones** stays out of scope (latency), as before.

### D5. Learn-by-ear player (goal: learn songs by ear, slow down, isolate)
- **Note finder** (the feature Transcribe! is known for): while a loop plays, a spectrum view with a piano keyboard / fretboard overlay lights up the strongest notes, so you can see the bass note you're hunting for. FFT is already in the plan for the isolator; this reuses it. Rough on full mixes, useful on isolated bass.
- **Isolator** stays as three dial-in stages; the "Bass focus" preset feeds the note finder.
- **Pitch shift in cents** (already in the core doc) plus "match my tuning": shift a song recorded in D standard so you can play along in E standard, or the reverse.
- **Markers become practice items** in one tap (already planned), and loops are measured in bars once you tap the song's tempo.

### D6. Tab and sheet music (goal: write it the way people write, intuitively; see notes in the tab)
- **Transcribe my take to tab** (new): run the tuner's pitch detector plus onset detection over one of your own *single-instrument* takes and get a draft tab line, with positions picked by the fingering chooser and every note editable. Monophonic only, and rough, but it turns "I recorded the riff" into "I have the riff written down" without typing it. Not attempted on full mixes.
- The note model, note row, linked highlight, retune question and entry methods stay as designed.
- **MusicXML details now known** (from the research): string numbers count from the *highest* string while tuning lines count from the *lowest* — a classic bug, now a test. No palm-mute element exists (use `<other-technical>`). Bends and harmonics round-trip poorly in other apps, hammer-ons/pull-offs well.

### D7. Sounds (goal: real samples of notes and chords)
- Piano only, as decided. One addition: **two dynamics instead of one** (mf and f) so A/B comparisons and comping don't sound flat. Cost roughly doubles the 2–4 MB estimate. Your call **[verify]**.

### D8. Speech-to-text (goal: think out loud to paper, offline)
- Plan unchanged (Android's on-device recognizer fed from our stream), but the probe build should also test a fallback: if on-device recognition isn't available, record now and **transcribe the take later** when it is.
- **Open question that could change several features [verify]:** the toolchain can't *compile* native code, but it might be able to **bundle prebuilt native libraries** (`.so` files) if they can be obtained from a reachable source. That would open offline speech models (Vosk / whisper-class), and on-device ML stem separation as a slow background job. Worth one probe: can our APK build include `lib/arm64-v8a/*.so`, and can we get a library from GitHub releases. Until then the plan assumes no.

### D9. Practice log (goal: practice log and timer)
- Practice items can now point at an idea, a section, a loop, a Theory page or an ear-training drill.
- **Session builder**: a practice session is an ordered list of items with target minutes each (Modacity's playlists); the timer advances item to item, rating prompt between.
- Auto-log stays.

### D10. Ear training (goal: pitch training with real samples of keys and chords)
- Add **drills built from your own drafts**: hear a progression from one of your songs and name the chords; hear a section's melody and sing or play it back. Ear training on your own material is the link between the trainer and songwriting that the other apps don't have.

### D11. Theory and the music core (goal: real understanding of all of theory, advanced included)
Corrections and additions from the second research report, now in the core doc:
- **Chord structure mirrors MusicXML 4.0**: spelled root (or a Roman numeral), one of the 33 `kind` values, ordered degree edits (add/alter/subtract), spelled bass, display text kept separately. Note the spec's arithmetic: an *added* degree's alteration is relative to a dominant chord (so adding maj7 is +1, adding ♭9 is −1). Our own catalog ids stay for qualities MusicXML has no kind for (alt, 7sus4…), each mapped to kind + degrees.
- **Parser dialects**, chosen in settings, because real charts disagree: Pop (default), Jazz/iReal, German (H = B♮, B = B♭, "Fis"), Romance solfège (Do Re Mi…). Configurable defaults for the genuinely ambiguous symbols: `C°` (triad, or dim7 in jazz), `C2` (sus2 in iReal, add9 in much pop), `C7+` (7♯5 usually, maj7 in some Latin/European charts), `Cmaj`, `C11` (often means 9sus4), lowercase roots. German is never guessed from a bare "B".
- **Test corpus:** McGill Billboard (CC0) and the CC BY 4.0 parts of ChoCo can be bundled as fixtures with attribution; the iReal Pro published quality list becomes synthetic fixtures. Chordonomicon (non-commercial) and other scrapes stay local, for statistics only. About 2.8% of real chord tokens are slash chords, so slash handling is a main path, not an edge case.
- **Theory content corrections:** the negative-harmony image of G7 in C is **Fm6 = Dø7**, not "Dm6"; Levy never used the term "negative harmony"; Barry Harris's 6th-diminished extra note is spelled both ♭6 and ♯5 depending on function; the common-tone °7 keeps the decorated chord's root (C–D♯–F♯–A on I in C); Messiaen modes are stored as pitch-class sets with a display-spelling policy, not one canonical spelling; harmonic-major modes get a primary name plus aliases.
- **Licence resolved:** CC BY-SA 4.0 text (Open Music Theory) can go into the GPLv3 app (Creative Commons' one-way compatibility declaration, 8 Oct 2015), with attribution and a modification notice, after checking each figure's own licence.

---

## E. Options checklist per tool (what "all the options it should have" means)

| Tool | Must have |
|---|---|
| Metronome | BPM 20–400 by drag, ±1/±5, type, tap; meter incl. additive; accents per beat (off/normal/accent/strong); subdivisions incl. 5 and 7; swing %; polyrhythm layer; sound choice; volume; count-in bars; trainer (+N BPM every M bars, up to a limit); silent bars; haptic; visual flash; section-following; presets |
| Tuner | Chromatic / string mode; A4 reference; temperament + tonic; needle / strobe; note names in key spelling; octave label setting; input device; transposition display for capo; sensitivity/noise gate |
| Drone | Root, octave, fifth on/off, third major/minor/off, volume, timbre (piano / sustained) |
| Recorder | Input device; 16/24-bit; mono/stereo; pre-roll length; click in take on/off; count-in; markers while recording; trim; rename; versions; "transcribe to tab" |
| Player | Speed 0.25–2× (finer: 0.05× steps); pitch ±24 st and ±100 cents; A/B loop; marks; step-up loops; count-in per loop; isolator 3 stages + bypass; note finder; waveform zoom; pedal control |
| Chord grid | Bars per section, chord per beat, typed or picked, symbol style, transpose, Roman row, function colours, play chart, comping style |
| Tab / notation | All entry methods; landscape; zoom; note row modes; techniques; playback with cursor, tempo, loop; undo/redo; selection, copy/paste, transpose; retune question; export |
| Practice | Items, sessions, per-item target and timer, rating + note, tempo history chart, streaks, totals, auto-log |
| Theory / Reference | Root picker, tuning/capo, views (piano/staff/fret), audio modes (block/arp/compare), symbol style, spelling toggle, search, "in your music" |

---

## F. Revised build order

| Phase | Delivers | Change vs before |
|---|---|---|
| **P0 Probe + mockups** | Throwaway probe build: mic sources, USB input, latency and haptic timing, simultaneous in/out, on-device speech, `revokeSelfPermissionOnKill`, U+1D12A rendering, bundling a prebuilt `.so`. Clickable mockups of rail, Workshop, idea view, draft lead-sheet, tuner, metronome, player | New. Decisions that change the design get made before code is committed to them |
| **P1 Engine + rail + capture** | Audio engine, metronome, tuner (string + chromatic), drone, recorder with pre-roll and input picker, mic chip, widget / tile / shortcut / pedal capture, scratch pad | Capture-from-outside and USB input added |
| **P2 Core + Workshop + backup** | Music core with tests; SQLite store; ideas with parts and versions; folders; drafts/songs; lead-sheet view; chord grid with parser dialects; lyrics; backup/restore; trash | Storage changed; ideas-with-parts; backup moved up |
| **P3 Piano + Reference + Theory + chart playback + practice log** | Piano sampler, voicing generator, Reference, Theory pages, play-the-chart comping, metronome following the draft, practice items and sessions | Chart playback and the practice log moved up |
| **P4 Tab** | Note model, tab editor, note row, linked take timing, transcribe-take-to-tab | Transcription added |
| **P5 Player** | Slow-down, pitch/cents, loops, isolator, note finder | Note finder added |
| **P6 Sheet music** | As designed | — |
| **P7 Speech-to-text** | As designed, informed by P0 | — |
| **P8 Ear training + MIDI + exports** | Ear training incl. drills from your drafts; MIDI in; the FL Studio export package; MusicXML | Export package made concrete |

---

## G. Questions for Christopher

1. Look: two or three apps you want it to feel like (or not); share the Better App Mafia look or not?
2. Which Behringer Xenyx model is your mixer (does it have USB)? Any other audio interface you'd plug into the phone?
3. Do you have, or would you use, a Bluetooth page-turner pedal?
4. Piano at one dynamic or two (size vs realism)?
5. Is bundling a prebuilt native library acceptable to you, if the probe shows it's possible (licence permitting)? It's the route to offline speech models and real stems.
6. Still open from before: app name and id; channels/licence; click bleed default; drafts and songs one list or two; folders flat or nested; mic default at launch.
