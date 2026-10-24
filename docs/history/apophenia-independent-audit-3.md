# Apophenia — Independent product, interaction, accuracy and build-plan audit (Audit 3)

**Date:** 20 October 2026  
**Scope:** `practice-app-design.md` (v3), `music-core-and-theory-design.md` (v2), `plan-audit-1.md`, `plan-audit-2.md` (the controlling decision document), both research reports, `app-building-export.md`, and the actual P0 source/wireframe in `apophenia-p0-source-and-research(1).zip`.  
**Method:** Inspect user goals and proposed actions for each feature; trace cross-feature workflows and data dependencies; inspect rendered HTML mockup at 412 × 915; inspect P0 code, handoff and phone test matrix. I separately checked the current Android foreground-service rules in official developer documentation.  
**Evidence labels:** **Plan** = specified in documents; **P0 built** = source/prototype exists, not necessarily Android-build-verified; **Proposal** = my change; **Verify** = research/device/usability test required. The prototype's desktop-only synthetic tests are not evidence of real-audio accuracy.

## 1. Product definition: what someone is hiring Apophenia to do

**Primary job:** "I have bits of recordings, lyrics, rhythms, chords and ideas. Help me capture them without friction, make sense of them, find useful relationships, try combinations, and move the promising results toward songs without turning this into a DAW."

This is distinct from:
- a practice suite (practice tools support creation; they are not the homepage's organizing principle);
- a transcription app (transcription enriches ideas rather than creating a separate document silo);
- a theory course (theory explains or manipulates *my music*, with a comprehensive browsable reference on demand);
- a miniature multitrack workstation (Stacks is for experimenting and evaluating combinations, not final production).

**Proposal: organize the UX around six verbs:** **Capture → Understand → Connect → Try → Develop → Retrieve.** Every feature should specify which verb it serves, what it saves, and how to return to the prior context. The user's chosen product name, free/GPLv3/offline intent, phone-first character, Stacks and Connections, mode awareness, no string tuner mode, no practice-session builder, and USB/pedal deferral remain unchanged.

### Three realistic end-to-end journeys to design and test

**A. Unexpected riff:** App closed → tap widget → recording begins with clear mic state → stop → saved immediately, even with no title → see wave and one-tap playback → optional analysis arrives → add a note → file it or leave it in Inbox → later receive an intelligible connection → preview it together with a lyric → make a Stack. No naming or metadata prompt may block a successful save.

**B. A lyric, not a recording:** Open → enter or dictate an unstructured line → save → browse similar rhymes, stress patterns or shared imagery → audition fit against one of my own riffs (with honest uncertainty) → attach lyric to Stack → move result into a draft verse. Capture cannot force a metronome, pitch, instrument or song selection.

**C. Practice becomes composition:** Import a reference recording → slow/loop a section → work with click/tuner → record a new riff; recorded take preserves whichever click grid and source context actually existed → view original take linked to loop/practice source → transcribe only if useful → try with another saved fragment → place into draft → export to DAW preserving positions and original files. The app must distinguish recorded material from imported reference material throughout.

## 2. Cross-cutting flaws requiring early decisions

### 2.1 A fragment/idea/draft/stack/link is still conceptually ambiguous

**Plan:** Earlier docs call the unit a *fragment* (one type); audit 1 calls it an *idea* containing many parts; audit 2 refers to takes as fragments again; Stacks can be saved as an idea, but also converted to a draft section.

**Proposal:** Make four durable identities, and do not make their wording interchangeable:

1. **Idea** — one creative thought or unit, with original *parts* (recordings, notes, lyrics, chord map, MIDI/tab/notation) and related take versions. It can have incomplete or unknown metadata.
2. **Occurrence** — an idea or a selected time range used within a particular draft section or stack layer, with local trim, transpose, tempo alignment, role, gain, and comments. A source edit should not silently destroy a placed arrangement.
3. **Stack** — a non-destructive experiment made of occurrences; both the setup and snapshots of promising variants can be preserved.
4. **Draft** — song structure containing sections and occurrences; 'song' is a status on the same object as already decided.

A *link* is a typed relation (membership, 'derived from', used in, suggestion accepted, manually related), not just a database foreign key. Keep provenance: which transformation/version generated this result? Preserve original audio, original timing, the user correction, and the rendered/transcribed derivatives separately.

**Critical unresolved case:** A single riff used in three songs must not change all three when somebody trims, transposes, or modifies it in one song without knowing what will happen. Default: edits within a stack/draft create **local non-destructive overrides**; original parts and explicit global edits are separate actions.

### 2.2 Inbox vs scratch pad should not be calculated only from "has zero links"

**Plan:** Scratch pad = ideas with no folder or draft links. **Problem:** A newly captured idea automatically linked to a stack, current draft, or reference might immediately vanish from Scratch pad even though the person has not processed it. **Proposal:** `inboxState = unreviewed | reviewed | archived` is independent of folder/draft/stack membership. Show **New / Unsorted / Recently captured**, plus **All Ideas**; never define 'not yet reviewed' by graph degree. Support snoozing and resurfacing forgotten ideas without destroying organization.

### 2.3 Confidence and evidence need one consistent schema

**Plan:** BPM, key and mode displayed on every take; confidence mark; user correction wins. **Problem:** 'played at 92 BPM' because the click was set to 92 is *not* proof that the performance was at 92; a 6-second riff often has no defensible tonic. **Proposal:** three separate fields: `clickTempo` (observed metadata), `performedTempoEstimate` (inferred), `interpretation` (key/mode hypotheses). Display 'Click 92 BPM' separately from 'Estimated ~92 BPM'. Use four explicit states across the app: **Known from input / Estimated / User-confirmed / Unknown**. Allow no-key, no-mode, free-time, and no reliable chord without penalties. Never silently replace a user's note/key/correction when reanalyzing.

### 2.4 Connections must optimize creative usefulness, not simple similarity

**Plan:** compatible key/mode, tempo, interval/rhythm shape, Roman progression, rhyme, lexical match, shared grouping. **Risk:** most pieces will share a tempo band or a common chord progression, flooding the app with weak matches. **Proposal:** classify connections as **Fits Together**, **Shares a Pattern**, **Suggests a Transformation**, **Related by Your Organization**, **Lexical/Rhythmic Lyric Fit**. Score by evidence quality and novelty, not only matching tags. Make false positives *visible and dismissible*: `Useful / Not relevant / Hide this kind / Pin`. Support explicit manual links even where the algorithm finds none. Avoid turning 'maybe fits' into a promise about harmony or phonetics.

**Preview interaction:** tap a connection → hear source A, source B, and a safe two-layer test; show speed/pitch adjustments **before** applying. Provide `Try as Stack`, `Place in Draft`, `Pin relationship`, `Dismiss`. 'Why?' reveals detected vs corrected evidence. A list of connections with no immediate experiment is a missed opportunity.

### 2.5 Recording/privacy/persistence is a product contract, not a setting

**Plan:** mic Off / Listening / Recording; pre-roll, sometimes automatically active with metronome/player/tuner; widgets and shortcuts. **Issue:** Automatically listening when a player or metronome starts may surprise users and deplete power. **Proposal:** first-use explanation and opt-in to pre-roll; mic defaults Off unless explicitly changed. The app must display the actual hardware recording state, buffer duration (for example, 15s), recording target and input route. Closing/stopping Listening discards the ring buffer. Capture should still be one tap from non-listening mode (without pre-roll) and clearly explain why earlier sound wasn't retained. Analyze background-service/Android 14+ restrictions by entry path; a widget may trigger an allowed FGS exception, but test on the target phone rather than claiming that all background starts work.

### 2.6 Feature completeness needs horizontal slices

**Plan:** P2 includes Workshop; P3 Stacks/Connections; P5 transcription; P9 export. **Issue:** Connections based on melodic shape and lyric prosody rely on note/event extraction that arrives later; stack/draft projects cannot be tested meaningfully in FL Studio until the very end. **Proposal:** make **minimal** pitch/onset/chroma extraction and evidence indexing a P2 dependency; basic two-layer stack listening in P3; a minimal WAV+tempo-position export by P3/P4; rich transcription and MusicXML expand later. Show Connections feature badges ('metadata-only', 'note pattern ready') until stronger evidence exists.

## 3. Feature-by-feature user intent audit

Each row states the thing a person is *actually attempting*, the better interaction, and how to avoid incorrect or disappointing results. "Must not" items become tests, not merely copy in help text.

### A. Capturing and organizing ideas

| Function | Intent when opening it | Better workflow/UI | Correctness and failure requirements |
|---|---|---|---|
| **Quick capture/widget/tile** | "Get this sound or phrase before it disappears." | Open directly to a recording-ready screen; recording starts on explicit tap or clear user-configured action; no title, mode or folder questionnaire. Distinct Audio / Lyric / Note quick options; widget is fast not a mini dashboard. | Mic permission/FGS failures explain next step and do not pretend recording; save unfinished take on interruption; crash recovery for partial WAV. |
| **Recorder** | "Hear exactly what I played, then decide if it's worth keeping." | Primary big Stop, optional marker while recording, visible target and input; immediate waveform/playback, discard/rename/retake after saving; one-handed use. | File closed safely, no silent loss; indicate clipping, low signal, route changes, mic busy and out-of-storage; original PCM preserved. |
| **Pre-roll** | "I just played something good and didn't hit record." | 'Save last 15 s' appears only when mic is truly Listening; show buffer countdown and explicit privacy icon. One tap includes pre-roll plus continued recording. | Ring-buffer cut at exact frame offset; no audio while Off; honor consent and platform restrictions; no 'retroactive' sound without microphone active. |
| **Take versions** | "Compare several performances of this same riff." | Horizontal takes with short listen buttons, compare A/B, favorite, rename, add note, choose best; record next take into existing Idea. | Don't confuse 'same idea' with accidental duplicates; separate raw take from trimmed/processed version. |
| **Analysis (BPM/key/mode/instrument)** | "What is this musically, and can I use it elsewhere?" | Compact editable metadata chip, then tap opens evidence + ranked candidates; easy 'not sure/free time'; analysis never gates playback. | Click BPM vs inferred BPM distinguished; key/mode may be unknown; selected instrument is explicitly user-supplied, not detected; corrections persist and reruns are versioned. |
| **Workshop / All Ideas** | "Find the half-remembered thing and make progress." | Search prominent; filter tabs **New, All, Used, Favorites, Recently Opened**; preview audio in list; persistent last query; multi-select organize. Mixed cards use same affordances without pretending lyric-only items have BPM. | No content disappears due to folder/stack link; sort and indexing remain consistent; robust delete/undo/trash. |
| **Folders/tags** | "Keep ideas without deciding where they belong permanently." | One action 'Add to…' with recent folders and multi-select; folders are *views*, not physical folders; allow an idea in many. Offer user-created collections, don't auto-file by guessed style. | Links do not copy audio or force move; removing folder never deletes source; clear distinction between unfiled and unread. |
| **Idea detail** | "Understand/annotate this scrap and try it somewhere." | One source timeline with tabs/sections **Takes, Lyrics, Chords, Notes, Tab, Related**; one prominent play button; convert/attach without changing object. | Source and derived part timestamps align; edits to derived tab never destructively alter take; show what was manual vs generated. |
| **Text/lyric capture** | "Write a line as it came to me." | Text input opens ready to type; plain text first; optional chords, rhyme and syllable overlay; minimal keyboard disruption; draft text auto-save. | Never auto-replace phrasing based on dictionary; unknown words/pronunciations manual; preserve raw typed text. |
| **Speech to text** | "Speak my thoughts instead of writing while holding an instrument." | Start mic recording immediately and save voice note; transcribe asynchronously if available; word-level correction and click to hear original. Offline state shown. | No promised on-device recognition if unavailable; speech transcription isn't the original evidence; keep original audio; users can edit transcript without editing source. |

### B. Discovering and developing

| Function | Intent when opening it | Better workflow/UI | Correctness and failure requirements |
|---|---|---|---|
| **Connections** | "Show me something I forgot that might work with this." | Reason-filter tabs, a few high-value recommendations with tiny source previews; ranked explanation and direct **Preview Together**; thumbs up/dismiss/pin. Manual connection option. | Penalize generic coincidences; distinguish confirmed and speculative analysis; never imply the matching material actually harmonizes without audition. |
| **Lyric/rhyme/stress matching** | "Would this lyric sing naturally over this melodic phrase?" | Two-pane lyric and waveform/note-onset view; align syllables by drag/tap; compare proposed placements; highlight strong syllables on weak beats. | Allow melisma, pickup, spoken rhythmic delivery, manual stress, unknown names/dialects and near rhymes; no yes/no certainty claim. |
| **Stacks** | "What happens if I put these together?" | Stack screen *is an audition workspace*: main play/stop, bar ruler, visible source offsets, loop region, per-layer waveform/piano part, mute/solo, fader/gain and reversible controls. Start with two layers, expand up to eight. | Conform non-destructively; report source/destination BPM, pitch offset and quality; avoid auto-key shift for unknown modes or nonpitched audio; gain staging, clipping prevention, loop crossfade. |
| **Add/swap suggested layer** | "Try another idea against this without ruining the current one." | Preview candidate without permanently adding; A/B swap; lock favorite layers; snapshot version and Undo; rank by harmonic fit, role, rhythm and evidence confidence. | Matching tempo is not evidence of bar-phase alignment; allow nudging down to milliseconds; let user turn off all automatic stretching. |
| **Record over stack** | "Play a response to the loop and keep it aligned." | Clear armed track, headphones/click source, 1–2 bar count-in, input-level meter, optional monitor off; on stop offer **Keep as new layer** or **Keep as separate idea**. | Record device output latency offset/compensate via calibration or manual nudge; raw take kept; mic/click bleed warning; repeatable beat grid. |
| **Drafts/sections** | "Organize a promising collection into verse/chorus/bridge." | Sections reorder by drag; compact expandable cards show chord line, lyric line, associated riff/stack; global overview plus section focus; unstructured draft remains possible. | Each occurrence retains source link and arrangement-local transforms; changing tempo/meter or key shouldn't erase source. |
| **Chord grid and comp playback** | "Hear the harmonic skeleton, not write a finished arrangement." | Type free-form chord symbols or choose by ear; play/loop chart with simple piano accompaniment; change key/tempo in one obvious place; mute backing instrument. | Parser ambiguity visible and reversible; chart notation tied to same chord objects as exports; comp voicings don't claim to be the user's original part. |
| **Structure → DAW export** | "Take a good sketch into FL Studio without redoing alignment." | Preview manifest (sections, tempo map, note tracks, raw WAV, processed optional stems); choose **originals / conformed / both** and destination via system picker. One folder/zip plus plain-text manifest. | Preserve sample positions and tempo maps, names and versions; include original audio/units; validate imports against current FL Studio rather than assuming markers import as written. |

### C. Tools that help without interrupting

| Function | Intent when opening it | Better workflow/UI | Correctness and failure requirements |
|---|---|---|---|
| **Persistent tool rail** | "Use a tool while staying in the thing I was doing." | Always visible mini-controls for current active task + big Record; tapping active tool expands a bottom sheet; a tool never sends user to an unrelated page unless explicitly asked. Single contextual `Back to idea` path. | Exactly one authoritative transport and audio/mic state; buttons indicate whether actually active; collapse never stops capture unexpectedly. |
| **Metronome** | "Get a reliable pulse; practise/record at my tempo." | Start/stop prominent; BPM tap, step and exact input; accessible meter accent editor; advanced odd/additive/subdivisions behind an obvious secondary panel, not hidden or discarded. | Absolute sample-time scheduling; playback/readout timing calibrated; saved beat grid only when click actually ran; no assertion about player's performed BPM. |
| **Tuner** | "Find out what note I'm producing and whether I'm in tune." | Big note/cents display, input status, octave and target frequency; instrument range under the hood, **no string-mode UI** as user chose. | Silence/low confidence shows dashes; mic Off cannot display a live note; bass octave uncertainty shown; don't treat 0.1-cent formatting as 0.1-cent measurement accuracy. |
| **Drone / sound reference** | "Hear the tonal center or interval while I play." | One tap root + fifth; piano or sustained reference; volume independent of click; run while reading Theory or tab; stop from rail. | Output source mixing doesn't interfere with recorded mic path; optional headphone recommendation to avoid audible bleed. |
| **Learn-by-ear player** | "Understand this imported song phrase by phrase." | Single waveform/transport, fast A/B handles, speed/pitch top-level; isolator and note finder tools inside this flow, not competing global pages; set practice point from loop. | Imported media timebase maintained after stretch/pitch; loop region stable on source time; state if protected/unsupported files can't be read. |
| **Isolator and note finder** | "Make bass or melody easier to hear, then find notes." | Clear three-stage dial-in, **original vs processed A/B** and output gain; spectrum+fretboard as visual aid inside loop. | DSP cannot promise isolated stems; display candidate notes not definitive chord transcription; high-pass/low-pass and stereo cancellation can destroy desired signal; clipping and CPU bounded. |
| **Practice timer/log** | "Track what I practised without filling out forms." | Timer attached to an Idea, section, loop, or Exercise; optional short note or rating at end; auto-log only while practice is intentionally active. **No structured session builder.** | Don't infer 'practice' from a metronome idling for 45 minutes or from a song being open; pause/idle detection, edit/delete erroneous entries. |
| **Curated Exercises** | "Choose something useful to work on." | Browse/filter exercises selected by user later; attach target tempo and relevant theory examples; retain simple progress history. | Leave content inventory undecided as specified; don't invent exercises or lock user into a course. |
| **Ear training** | "Internalize what intervals/chords/functions sound like." | Functional cadence/drone first, selectable drills; one-tap hear again/compare/correct; quick drills from own drafts where source is playable. | Grade by known target pitch/function, not by arbitrary enharmonic label; tuner sing-back accepts noisy pitch with tolerances and correct octave handling. |

### D. Writing/understanding musical structure

| Function | Intent when opening it | Better workflow/UI | Correctness and failure requirements |
|---|---|---|---|
| **Tab editor** | "Write where I put my fingers, and connect that to the notes." | Fretboard input + visible string tuning, direct fret entry, audible note, contextual duration and technique controls; instant notation/note-name overlay; zoom and landscape. | Changing tuning/capo prompts 'keep pitches' vs 'keep fingerings'; non-reentrant assumption forbidden; saved fingering belongs to occurrence/part, not guessed every render. |
| **Transcription** | "Turn my recording into editable musical notes so I don't re-enter it." | Preview **unquantized original** against corrected notes; fix pitch, note onset and string placement in one screen; quantize as separate reversible option; select mono melody or chord labels. | No fake exact note-by-note polyphony; note events keep original times/confidence; pitch-class isn't spelling; separate bass fundamentals, onset and octave errors. |
| **Notation / sheet music** | "Write readable rhythms/pitches without wrestling tiny symbols." | Staff + keyboard/fretboard input modes; duration selected then place; multi-select/copy/transpose; linked audio highlight; standalone full-screen landscape when needed. | Bar split ties, rests, beaming/accidentals/tuplets must round-trip; readable notation is not engraving-quality; prefer MusicXML handoff for polish. |
| **Theory / Reference** | "Explain the thing I'm hearing, or show how it works on *my tuning*." | Default from active chord/riff/key; deep-link from detected term; a clear **What it is / Hear it / Try it / In my music** pattern with full reference browse and search. Advanced topics discoverable, not gated. | Store spellings and chord structures canonically; mode/functional interpretation is contextual rather than forced; include alternate names where sources disagree. |
| **MIDI I/O** | "Capture a keyboard idea or move note/chord data to another app." | Easy select input device, record into Idea, show note events/monitor, audition and edit, then export part or full draft. | MIDI note numbers don't encode spelling, microtonal tuning or full performance semantics; set pitch spelling by context, preserve original MIDI event values. |
| **Backup, restore, trash** | "Trust that my unfinished work will still be here." | Backup status visible near Workshop/Settings; explicit **Back up now**; scheduled option where platform permits; restore preview with conflicts, sizes and creation time; 30-day trash + undo. | Atomic DB snapshot and referenced audio consistency, integrity/version checks, interrupted backup recovery, no assumed access to inaccessible folders; verify restore on fresh installation. |
| **Settings/permissions** | "Set my defaults once, without troubleshooting every recording." | Top-level sections: Capture & Privacy, Instrument & Tuning, Audio Input/Output, Display, Notation, Data & Backup, Advanced Analysis. Put contextual quick settings where used. | App never silently enables mic; route/input changes logged; explain unavailable offline dependencies; version data migrations. |

## 4. Specific issues in the existing P0 HTML mockup

I inspected `res/mockups/index.html` and rendered Workshop, Idea, Stacks, Connections, and Draft at 412 × 915. This is a static mockup, not a tested Android UI.

1. **Top navigation scrolls away.** Mobile `.nav` is a horizontal row at the top; `go()` scrolls `<main>` into view, which can push the navigation offscreen. The tool rail doesn't contain Workshop/Connections navigation. **Fix:** stable bottom destination tabs for **Workshop / Discover / Stacks / Drafts / Tools**, or a consistent compact navigation bar above a separate active-tool dock. Avoid two competing bottom bars; reserve one main transport area.
2. **Tool rail isn't architecturally persistent.** The mockup positions a footer `.rail` as `position:sticky` after the content inside `.wrap`; on tall content it may only enter the viewport near the end, and consumes scrolling area. **Fix:** on Android create an actual persistent overlay/container outside screen scroll content, respecting system gesture insets, keyboard, screen rotation and recorder state.
3. **Mic Off + tuner D 0¢** is a logically impossible live reading. **Fix:** disconnected/Off/insufficient signal states (`—`), and actual input source and record status.
4. **Record button has no target.** The docs promise `REC → Scratch pad` or `REC → Current draft / section`, but the mockup says only `Capture`. **Fix:** show target beneath the recording button or as a small adjacent chip before arming; one tap can change it.
5. **Workshop isn't a workshop yet.** Four cards with examples and an 'Explore connections' button, but no search, folder browser, inbox count/triage, multi-select, previews, or used-in state. **Fix:** test actual find/file/play actions with 100+ heterogeneous ideas, not a four-card sample.
6. **Stacks UI lacks the thing users need to manipulate:** a transport, bar grid, start/trim handles, waveforms, manual alignment, per-layer gain, recording/overdub control, A/B comparisons and snapshot history. Muting is not enough to determine whether things work together.
7. **Connections aren't directly actionable.** An item can open another page, but cannot audition the match, dismiss a weak result, pin the relation, filter by type or add the pairing to a temporary Stack.
8. **Metadata 'edit' cycles through a canned list.** Sufficient for a demonstrator but wrong as a model for production. Open a compact correction sheet with exact input, ranked candidates, 'Unknown', and trace of where the estimate came from.
9. **No empty/error/loading states.** Needed: microphone denied, no ideas, take still analyzing, no confident mode, no compatible layers, draft empty, sound pack missing, backup failed, imported file unavailable.
10. **Navigation labels are not aligned across docs.** An earlier five-place scheme listed Workshop/Practice/Player/Reference & Theory/Tools; P0 mockup uses Workshop/Idea/Stacks/Connections/Draft/Theory. **Fix:** settle a task-based mobile IA: *Workshop (ideas+drafts)*, *Discover (Connections)*, *Stacks (Try)*, *Player (Learn)*, *Theory (Understand)*, with Practice/Tools integrated contextually. No top-level destination for an Idea detail.
11. **Stand mode, landscape and accessibility absent.** Need oversized tempo/tuner/play labels; 48dp-ish touch targets, semantic accessible controls, screen reader labels, high contrast not color-only, reduced motion, scaling and undo for destructive gestures.
12. **Long-running work lacks visible state.** In actual app: `Saved`, `Analyzing`, `Stretcher rendering`, `Ready`, `Failed — use original` and progress/cancel. Never freeze the Take screen awaiting analysis.

**Screen-by-screen review task before implementation:** For each screen supply (a) a fast/common-path storyboard, (b) an empty state, (c) error/unavailable state, (d) a one-handed state, (e) a long-content state, and (f) landscape/keyboard state. Prototype tap targets and transitions on the actual phone, not only in desktop HTML.

## 5. Technical/accuracy risk audit

### 5.1 Audio and timing: an explicit synchronized timeline is missing

The plan has sample-accurate metronome scheduling, but separate input/output streams do not automatically share timestamps. To support "record over Stack", pre-roll, overdub and DAW handoff, define immutable source sample frames; device audio presentation/capture timestamps where available; musical beat-map from the click; and arrangement local time. Distinguish **source seconds**, **source frames**, **beat/bar coordinates**, **rendered frames**, and **display cursor position**. Conversions need round-trip tests, including sample-rate changes, 6/8, tempo changes, count-ins and preroll. Calibrate roundtrip latency on target device or expose explicit nudge. Don't just pad every WAV to a bar if the original timing and count-in offset were never established; export a position manifest and leave source unchanged.

### 5.2 Tuning-aware pitch handling is necessary, but not sufficient

27.5 Hz bass notes require long enough observation windows and the phone mic may omit the fundamental. Test with direct/reference capture and phone mic, label octave uncertainty, and use harmonic evidence. Avoid claiming 0.1¢ accuracy just because a readout has one decimal. Set a realistic display settling time and confidence/no-signal threshold. `A4=440` reference, temperament, transposing instruments, capo, *actual sounding pitch*, detected MIDI number and final notated spelling should remain separate.

### 5.3 Analysis models can get the right data for the wrong reason

A D Dorian riff and C major pitch-class collection can be indistinguishable without tonic/phrasing context. Click beat grid only proves the metronome's setting, not adherence. Chroma may label an omitted fifth or inversion ambiguously; inversion requires bass evidence. Isolator results may contain artifacts worse than the input. Correction must be possible right where a guess is seen and affect subsequent Connections and Stacks. Store detector/library version plus evidence and user choice. Test no-signal, atonal, deliberately modulating, rubato and non-Western material, not only clean 4/4 single-note synth fixtures.

### 5.4 Theory engine must distinguish notes from interpretations

The music-core's separation of spelled pitch, chord structure, parse dialect, and rendering is excellent. Keep it. Add explicit distinction for: measured Hz/MIDI note ↔ spelling in key; played voicing ↔ possible chord symbol; chord symbol ↔ harmonic function; pitch set ↔ mode/tonal center. Do not make chord or function interpretation an immutable field of the audio. The advanced theory catalog must retain named variants/disputes and data-test parity. The design's per-pitch-class cent offsets are only a **static temperament approximation**, not a comprehensive adaptive just-intonation engine; label limitations accordingly. The full grammar can exist in core even when full engraving and every theory page land later.

### 5.5 Storage needs transactions, migration and cache policy

Plan's SQLite + audio files is appropriate. Add staged file writes, checksum/duration verification before DB commit, idempotent migration tests, orphan cleanup and recovery from crash between save and insert. Offline rendered audio is cache: version it, give it storage limits, evictable without data loss. Distinguish user data, original recordings, regenerable features, sound banks and cached stack renders in backup. Backups must be made via a consistent SQLite snapshot (WAL aware) and run a manifest/integrity check before claiming success.

### 5.6 Native libraries and device viability are not yet proven

P0 bundles a tiny ARM64 `.so` marker and a pure Java prototype; it does **not** show Rubber Band/Basic Pitch/Speech runtime integration works. `gradlew` wrapper binary is absent, the Android P0 project was not built here, and current source depends on standard Android Gradle Plugin/SDK whereas the older app-building export describes a no-Gradle/non-AndroidX custom toolchain. **Decision required:** support a reproducible standard AGP/JDK17 build as canonical, then optionally maintain a constrained offline build path. Specify ABI support, provenance, OSS notices, release signing and APK/AAB pipeline. Do not count any unsupplied dependency as implemented.

### 5.7 Android service rules need actual implementation tests

**Externally verified** in Android developer docs: Android 14+ requires proper foreground service types/permissions (microphone uses `FOREGROUND_SERVICE_MICROPHONE` and `RECORD_AUDIO`) and restricts starting microphone services from background, with context-sensitive exemptions including some user-initiated widget/notification flows. This is **not** evidence that the P0/production capture flow works on the target Samsung device. Test app-open, home-screen widget, notification, tile, lock screen, Bluetooth route switch, permission denial, mic killed and process death. P0 manifest has no production background recording service yet, as expected for a probe. Sources: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start and https://developer.android.com/about/versions/14/changes/fgs-types-required .

### 5.8 Test actual phone recordings, not only ideal synthesized examples

Current P0 tests pass 9 CoreSmoke and 15 ResearchPrototype synthetic assertions. These validate implementation fragments, *not* song/riff recognition or rendered accuracy. The P0 probe currently logs RMS but doesn't retain recordings for controlled listening; add an **opt-in diagnostic WAV export** with route/source metadata and a defined 5/10/20/60-s corpus. Pair expected notes/tempo with the files. Compare tests by device input, instrument and tuning. Treat chord/key transcription with explicit reject/unknown metrics, not only success rate. Include human audition ratings for stacks, isolated bass, vocals, transient percussion and large stretches.

## 6. Proposed development order: make useful loops early

The controlling audit 2 build order is reasonable technically but creates a product that is 'usable' at P2 yet does not show the namesake discovery workflow until P3, and ships export very late. I'd reorganize acceptance around completed user tasks:

| Gate | Build contents | End-to-end demonstration that passes before advancing |
|---|---|---|
| **P0 — interaction contract + probes** | Clarify open decisions; wireframes for Capture/Workshop/Connections/Stacks/Idea/Draft; P0 debug APK build and actual phone tests; audio recording diagnostics and timing benchmarks. | Can navigate every major screen and demonstrate capture to saving/returning *in the prototype*. Device mic/native/latency/log findings documented honestly. |
| **P1 — trustworthy capture** | Audio service/engine; recorder + optional preroll; click and tuner, mic and route state; crash-safe original WAV; capture shortcut/widget when feasible; fast inbox, playback and rename. | Record a riff in under a few taps, lock/unlock, review/save/re-record, relaunch and find the original. No lost data. |
| **P2 — understand + find** | Minimal immutable music core; SQLite schema/backup/undo; Ideas, parts, tags and occurrences; BPM/onset/chroma extractor and *uncertain* candidates; text/lyric ideas; initial search, simple connections; user corrections. | Capture five different ideas, correct an uncertain tag, search and retrieve them, show two honest evidence-based connections, back up and restore. |
| **P3 — try things together** | Two-layer Stack first; waveform and musical timeline, trim/nudge, mute/solo/gain, manual pitch/tempo settings, offline stretch cache, record over; then 8 layers and ranked suggestions. Add basic WAV+position/tempo export. | From a proposed Connection, audition two originals, align them, A/B a candidate, record a response, preserve original takes, export aligned audio. |
| **P4 — develop a song** | Draft sections, chord grid, lyric alignment, piano sample playback, Reference essentials, note/scale/catalog utilities; draft from Stack; minimal DAW package. | Convert Stack to chorus, add verse lyrics/chords, hear an arrangement, export audio/MIDI with correct placement to FL Studio. |
| **P5 — transcribe and edit** | Monophonic pitch+onset to editable notes, tab/notation-backed note model, chord-label timeline, synced waveform correction; edit-note transformations feed Connections. | Record bass riff → tentative notes → manually correct → display tab and export MIDI, with note-to-audio sync preserved. |
| **P6 — learning and detailed notation** | Player time/pitch loop tools; isolator/note finder; complete tab/notation UI, MusicXML; then deep Theory and mode catalogs with generated pages. | Import reference, learn 4-bar passage, write it, read it on own tuning; round-trip notation in a reference editor. |
| **P7 — polish and support tools** | Speech transcription if actual offline probe supports it, ear trainer and curated Exercise framework, optional MIDI-in, practice log, advanced comping/voice-leading, second sample dynamic if warranted. | Offline and failure paths work; practice/tool state doesn't distract from capturing, connecting and developing. |
| **Release gate** | Play/GitHub/F-Droid compliance, backup restore on clean install, privacy policy, licenses, accessibility, CI reproducible builds, acceptance corpus. | Test fresh install, restore, export/import, long session, low battery, denied mic, process death, low disk, slow and noisy audio on target phone. |

**Note:** All advanced theory types remain representable early. This schedule defers encyclopedic content, complex UI and supplementary utilities, **not** correct data models. It does not change previously decided user preferences (no structured practice session builder, no string-mode tuner UI, one piano dynamic initially, USB/pedals as polish).

## 7. Questions that materially change what to build

Questions are grouped by decision impact. Each comes with the default I recommend. **These are not previously answered decisions; I am not asking the user to re-decide known items.**

### Must resolve for P1/P2

**Q1. Where should Record put audio when you're looking at a specific draft or stack?**  
A. Automatically into that context; B. Always into New Ideas with an optional 'attach'; C. Ask when stopping.  
**My default:** show the explicit target and use **A**, with one-tap target switch before recording; never ask during capture. For recording over a Stack, automatically attach as a new layer with a separate saved Idea underneath. This needs confirmation because accidental routing is costly.

**Q2. When should the microphone be listening for pre-roll?**  
A. Only after you intentionally enable Listening; B. Whenever the metronome/player/tuner is on; C. Always when the app is foreground.  
**Default:** A; remember your setting and offer a clearly marked 15-second buffer. This determines privacy, power and what 'capture the previous riff' can promise.

**Q3. Is an Idea supposed to represent one musical thought with related takes/lyrics/notes, or a loose collection of any scraps?**  
**Default:** one thought with related parts; the Workshop has folders and Stacks to collect unrelated ideas. Ask for confirmation using an example: should a bass riff + lyric experiment become one Idea or remain two linked Ideas?

**Q4. When a riff is in three different songs and you edit it in one song, should the other two change?**  
A. All update; B. only that placement; C. ask per edit.  
**Default:** B for trims, positions, stretch, transposition, chord substitutions, gain; changes to the source idea need explicit propagation.

**Q5. What should qualify an Idea as 'processed' rather than new?**  
A. It gets any folder/link; B. you manually mark reviewed; C. after you listen/open it.  
**Default:** B. This matters because otherwise users can inadvertently lose uncategorized ideas from Inbox.

### Must resolve before Connections/Stacks design is frozen

**Q6. What would make a Connection interesting enough to interrupt you?** Examples: similar rhythm in different keys, lyrics that fit a phrase, same progression in a different mood, a contrasting part that fills a missing instrument role.  
**Default:** show a small ranked list, offer filters, and strongly favor auditionable, non-obvious combinations over simple tag matches. Ask user to rank those examples so relevance scoring can reflect their actual creative criteria.

**Q7. In Stacks, should it try to force loops into the same key/tempo automatically, or suggest changes but wait for approval?**  
**Default:** suggest and preview first; timing and pitch processing is reversible; never shift a layer whose key is unknown without permission.

**Q8. Are Stacks strictly repeating loops, or do you want one-shot clips and free-time passages too?**  
**Default:** loops are the initial focus; support one-shots and free-time audio *in the data model* and allow unconformed layers. Restricting every layer to 1, 2, 4 or 8 bars may eliminate interesting fragments.

**Q9. Is two-way 'preview together' enough to evaluate a suggestion, or must audition include shift controls, offsets and A/B before creating a Stack?**  
**Default:** a transient mini-stack with both original and proposed altered versions is the point of discovery; creating a saved Stack is only one further tap.

### Before finalizing visual design/exports

**Q10. What does Apophenia's home screen need to emphasize on a normal day?**  
A. Unreviewed scraps; B. projects currently being developed; C. promising new Connections.  
**Default:** A at top with B and C visible as secondary shelves. Prototype three variants; observe which helps retrieve work fastest.

**Q11. Style and density:** should it feel more like a restrained musician's notebook/field recorder, or a high-information workstation? Any 2–3 reference apps/images you specifically like or dislike?  
**Default:** restrained, dark, tactile and non-gauche; progressively reveal technical controls without hiding them permanently. Need user visual references before polishing.

**Q12. Export purpose:** do you primarily want to drop *original takes* into FL Studio to finish arranging, or do you expect each processed Stack to recreate exactly there as separate conformed stems?  
**Default:** both as selectable output, plus source tempo/time positions and one-click simple draft export. Confirm earliest required handoff.

**Q13. Imported audio's role:** Is a commercial/reference song only a Player source to practice against, or should it be an Idea searchable through Connections and usable in Stacks?  
**Default:** reference recordings remain in a distinct Library unless explicitly converted/imported; avoids clutter and confusion about original ownership.

**Q14. Is it acceptable to store a user's own manual description of mood/meaning (e.g., 'uneasy city-at-night') for search/Connections even though automatic semantic AI matching is deliberately out of scope?**  
**Default:** yes; tag and text semantics are user-authored, not guessed. This keeps the app human-driven without requiring an ML mood classifier.

**Q15. Which build route will you actually use for this project?**  
A. Standard Android Studio/Gradle/SDK36; B. existing restricted Java-only APK builder; C. both, with one canonical.  
**Default:** A, preserving a source-portable pure Java core and evaluating constrained build only as fallback. This matters before promising native DSP/ML and reproducible releases.

### Do not re-open answered decisions unless user volunteers a change

The docs already establish: **Apophenia**; domain `apophenia.audio`; free/GPLv3; offline-first; GitHub and Play then F-Droid; Stacks using own recordings; instrument selected not auto-detected; single-dynamic piano initially; no string-mode tuner; no structured practice-session builder; USB audio interface and pedals deferred; user-curated Exercises later. The final app ID (provisional `audio.apophenia`) remains genuinely unconfirmed; resolve before any Play submission, not before a local prototype.

## 8. Acceptance rubric for every feature

A feature cannot be called "done" merely because a button responds. In its phone walkthrough require:

- **Discoverability:** a novice can explain how to find the action; a repeat user can reach its common path with minimal steps.
- **Task completion:** can complete the real user intent start-to-finish, including saving/returning where applicable.
- **State transparency:** current input, processing, output and recording target are obvious, with no fake progress/measurements.
- **Correctness:** independent fixtures and, for audio, real recorded cases; known uncertainty displayed; source never silently overwritten.
- **Failure handling:** permission denied, silent input, interrupted recording, process kill, storage full, external file gone, job cancellation and unsupported formats.
- **Undo/recovery:** destructive edits reversible, accidental organization reversible, user originals recoverable, backups restorable.
- **Device ergonomics:** instrument in hands, one-thumb/stand view, small screen, keyboard open, Android insets, landscape, accessibility.
- **Integration:** output appears immediately in the right place; can move to next creative step and back, preserving context.
- **Evidence label:** source-only, desktop-tested, APK-build-verified, phone-verified or human-audio-verified, never conflated.

## 9. Most important conclusion

The plan's highest-risk mistake would be shipping **20 individually impressive tools with poor movement between them**. Its highest-value opportunity is the opposite: an app where a rough recording can become a related lyric, a two-layer experiment, a draft chorus, a playable theory example, and a clean DAW handoff *without losing the original or breaking concentration*.

Test the **transitions between functions** as first-class features: the source context, recording target, uncertainty, and returned location should survive every transition. Every screen needs a direct next action, and every transformation should be reversible. That is how Apophenia becomes more than a feature checklist.
