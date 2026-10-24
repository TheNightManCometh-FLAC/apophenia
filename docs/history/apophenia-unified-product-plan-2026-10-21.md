# APOPHENIA — Unified Product Vision, Design Audit & Master Plan

**Version:** Design synthesis 1.0 — 21 October 2026  
**Status:** **PRE-PLANNING / PRODUCT DESIGN ONLY.** Nothing described here is asserted to be built, integrated, device-tested, or finished. The source/research ZIP is a set of explorations, not a completed app or a binding technical foundation.  
**Sources reconciled:** `practice-app-design.md`, `music-core-and-theory-design.md`, both plan audits, both research reports, `app-building-export.md`, the P0 research archive, and especially `apophenia-architecture-decisions-2026-10-21(1).md`.  
**Precedence:** The October 21 user decisions govern wherever old plans disagree. This document is the proposed single source of truth for future planning; it is not an authorization to start coding.

---

## 1. What we are actually making

**Apophenia is an offline-first musical idea environment for capturing rough material, finding relationships within it, combining it without commitment, and developing it into compositions.** It also provides the instrumental, ear-training, and theory tools to investigate and improve that material without disrupting the creative session.

The app is not principally a metronome, a notation package, a sample browser, a DAW, an AI songwriter, or a practice tracker. Those are possible capabilities inside an idea-to-music workflow; none is the central organizing principle.

### The primary cycle

**Capture → Keep → Find → Compare → Combine → Experiment → Develop → Export**, with the ability to stop, branch, or return to any stage. A recording need not be rhythmic. A lyric need not be assigned a melody. An idea can belong nowhere, sit in multiple groups, be used by multiple independent projects, or never become a song.

### Non-negotiable product qualities

1. **Capture now, decide later.** Never lose a performance or force project assignment before recording. A dismissed placement sheet does not delete the result.
2. **Fragments precede songs.** Scratch material remains valuable independently of a song or category.
3. **One connected environment.** The same fragment can be found, understood, auditioned, copied, and creatively developed without re-importing across modules.
4. **Project ownership is real.** Once copied to a project, its editable version is independent of the Library source and all other projects.
5. **Flexible musical time.** Loops, one-shots, free-time phrases, changing meter, and non-tonal material all work; no beat-grid or key forced on everything.
6. **Helpful, not presumptuous.** Inferred tempos, keys, modes, chord names, lyric matches, and transcriptions are suggestions, not facts; uncertainty and provenance are visible.
7. **Depth without obstruction.** Advanced theory is represented accurately, but capturing a riff does not involve a theory questionnaire.
8. **Offline, private, recoverable.** Local-first operation, explicit microphone Listening, user-owned data, and backup/restore.
9. **Creative freedom beats feature count.** Any feature that adds menus without shortening a real workflow should be simplified, contextualized, or deferred.

### Explicit non-goals

- Competing with FL Studio or other full DAWs in high-end multitrack production, mixing, automation, mastering, or plugin hosting.
- Automatically composing songs or turning every fragment into a conventional verse/chorus arrangement.
- Automatic retuning/time-stretching of every clip just to manufacture compatibility.
- Requiring online accounts, a proprietary sample marketplace, or cloud inference for the core experience.
- Presenting a new main tab for every capability (Stacks, Connections, Transcription, Lyrics, Exercises).
- Treating every possible advanced-theory feature as an initial shipping requirement.

---

## 2. Experience architecture: five places, two persistent abilities

**Exactly five top-level modes:**

| Mode | What it is | What it is *not* |
|---|---|---|
| **Board** | The visual, freeform development surface for one selected **Project** | A folder browser, suggestion gallery, or mandatory linear DAW arrangement |
| **Library** | The single searchable, mixed-format home of source fragments and Groups | Separate audio/lyric/score silos or a page dominated by filters |
| **Tools** | Metronome, tuner, drone, independent recorder, learn-by-ear functions, useful standalone utilities | Another arrangement workspace |
| **Pitch Trainer** | Ear and pitch training, from direct pitch matching through advanced musical hearing | A compulsory gamified beginner course |
| **Theory** | Accurate, interactive, searchable musical reference applied to the user's own material | A disconnected pile of textbook articles |

**Persistent across all five:**

- **Collapsed player/session strip**, expanding to a multi-item player, auditioning and overdubbing workspace. It displays the active session's name/context, play state, time, and loop/record state. The selected Board and active session are independent.
- **Global quick capture** for audio, lyrics, or a note, even if unrelated to what is currently on screen or playing. It returns to the previous state after saving.

**Important real-estate correction:** The previous persistent **tool rail** and the newer persistent **player** cannot both consume a large bottom portion of a phone screen. The player is the persistent bottom surface; a compact active-tool indicator or quick action may appear within/adjacent to it. Full tuner/metronome controls live in Tools, in a temporary sheet, or in a deliberate stand-mode expansion. Five-mode navigation stays legible and separate from the player.

### Launch behavior

- With an existing selected project, launch to the last Board view with project switching, quick capture, and active session clearly accessible.
- Without a project, present a useful Board launch state: **New Project**, **Open Project**, **Browse Library**, **Record Idea**, **Write Note**. Do not create a fake default project or force everything into an Inbox dashboard.
- Do not auto-start playback or microphone Listening on launch.

### Phone interaction conventions

- Board pans/zooms; cards are positioned intentionally; drag handles or a deliberate move mode prevent accidental rearrangement while scrolling or reading.
- Tap previews/opens; long-press selects; overflow contains secondary actions. Destructive actions offer recovery.
- Record/play/tap-tempo controls have large instrument-in-hand targets; support big readouts/stand mode when needed.
- Pinned lyrics stay readable during playback/recording; overlays do not hide recorder state or the ability to stop.
- Background operations never obscure active recording or silently change the selected project/session.
- Restore tab, search, filter, Board pan/zoom, selected card, and active-session context when navigating.

---

## 3. The Library: the real center of accumulated creative material

**One list containing all types of fragments together:** audio, spoken ideas, lyrics, notes, chord progressions, tablature, MIDI, standard notation, and later supported formats. Rows look recognizably different by type but share the same searchable space.

**Default layout:** Search at top; small **Filter** and **Sort** actions; optional compact scope (`All / Unassigned / Recent / Used in projects`); results list occupying most of the screen; tags on the **same row** as the fragment where width permits (overflow indicator if not); persistent player and navigation below.

**A fragment row** supports immediate preview/read, opening the appropriate editor, selection, musical metadata, Group membership, independent Copy to Project, Add to Active Session for playable items, and recovery-safe delete. Playing a preview does **not** automatically add it to a player arrangement.

### Two independent classification systems

- **Groups** are user-named Library collections (`Possible choruses`, `Love songs`, `Dark riffs`). One source may be in many Groups; deleting a Group does not delete any fragments. A Group has **no Board**.
- **Musical attributes** are typed properties (`D Dorian`, `92 BPM`, `Bass`, `Drop D`, `7/8`, `free-time`, `loop`, `one-shot`). They are displayed as familiar editable chips, never treated as flat hashtags. User descriptive keywords may coexist.

**Filter semantics:** OR among selected values of one property; AND between different properties. Tempo can use a precise value, range, or explicit compatible half/double relationships. Uncertain or absent metadata remains distinguishable from confirmed metadata. Search supports names, text/lyrics, recognized transcripts (where available), group names, and applicable tags.

**Creation versus organization:** A new recording is immediately safe, searchable and marked unreviewed if desired, even before it is assigned to a Group or Project. `Unassigned` is not a dumping ground that disappears from All search.

**Old plan correction:** Do not retain the original Workshop/folder model or linked draft/song references as the primary organizational model. The Library is where source fragments and Groups live; Projects and their Boards are separate editable domains.

---

## 4. The Board: a project is a space for developing ideas

Each Project owns **exactly one Board**: a scrollable/zoomable canvas containing project-owned fragments of **all types**. It is deliberately more like a creative musical storyboard than a multitrack DAW.

### Board cards

- **Audio:** title, take identity, small waveform and audition affordance, annotated markers.
- **Lyrics:** readable, directly editable words; optional chords above words; pin the card for singing.
- **Text:** free notes, questions, imagery, reminders or possible directions.
- **Chords:** meaningful chord sequences/harmonic notes, not just images of chord labels.
- **Tab:** compact preview, full tab editor on opening.
- **MIDI:** pitch/rhythm pattern preview and editing entry.
- **Notation:** a staff preview, opening the same underlying musical-note editor in a different representation when appropriate.

Cards can be moved, resized, clustered, labeled, pinned and optionally grouped into song sections. Section names are **optional**; a Project with only a few sketches is valid. A Board does not need to have bars, tempo, meter or a song structure before it is useful. A later arrangement/lead-sheet view may be generated from the material the user has chosen to structure.

### Ownership rule

`Copy to Project` creates a distinct ProjectFragment with its own ID, editable content, revision history, tags, annotations and media edits. A source copied to two projects has three independently editable versions: Library original, Project A, Project B. **Provenance is informational, not live synchronization.** Group membership remains on Library source fragments by default.

Audio originals remain immutable; non-destructive changes create new project-specific edits/derivatives. Initially favor explicit separate project media where needed to guarantee independence; content-addressed deduplication could reduce storage later if it cannot create shared mutable state. Any copies of a fragment within the same project should have clearly defined independent-or-reuse behavior rather than silent aliasing.

### Board versus player

The Board answers **what ideas belong to this project and how am I thinking about them?** The player answers **what am I hearing together right now?** Board coordinates and section clusters do not implicitly prescribe playback sequence or mix. Tapping an audio card can preview it without changing the session; **Add to Session** is explicit.

---

## 5. The persistent player: musical combinations without a full DAW

This absorbs the useful parts of earlier **Stacks**, loop player and overdub plans. A separate Stacks tab is unnecessary. Saved combinations are **Sessions** available from the player and, where applicable, attached to a Project.

### Three kinds of audio material

1. **Loop:** repeat a marked region, with explicit loop boundaries and an optional beat map.
2. **One-shot:** play on a chosen trigger/start offset; do not automatically repeat it.
3. **Free-time recording:** play at its natural timing; can be aligned by audible landmarks, with optional manual markers; no invented grid.

A session can combine all three. The session's tempo/key is an **optional aid**, not a condition of entry. Tempo sync, pitch conversion and quantization must be explicit, bypassable and reversible; show source tempo/key, suggested alternatives, applied shifts, and risk of audible artifacts.

### Player controls

- Playback / pause / stop, transport, loop, cycle region, count-in, section marks.
- Multiple clips/tracks with their own gain, mute, solo, offset and trim ranges.
- Alignment by ear, numerical offsets and optional snap to meaningful beat grid.
- Record vocals or an instrument over an existing combination using headphones; capture as an **independent microphone take**, never silently flattening backing into the recording.
- Keep and compare multiple takes; designate preferred take; do not delete other originals by switching.
- Save/reopen/duplicate a Session, or discard an explicitly temporary combination after confirmation.
- Export a mix and original/processed stems when those operations are supported.

**Important nuance:** This is a persistent *session configuration*, not an always-running recorder. Switching from Board to Theory or Tools must not change selected tracks or session ownership. Switching Projects must show `Session: Project A` even when `Board: Project B`, and offer an explicit switch/replace/save-as choice. A recorded take must never accidentally be placed in the newly viewed project.

### Audio correctness gates

- Frame-accurate capture and playback positions; audible latency must be measured and compensated when overdubbing.
- Handle mismatched sample rates, clip lengths, headphones and speaker bleed, gain/clipping, route changes, incoming calls and app interruption.
- One logical mixer/output and controlled microphone ownership; no simultaneous independent mic consumers fighting for resources.
- Save raw capture first. Analysis, pitch correction and render caches operate on derivatives.
- If reliable tracking fails, preserve audio and offer manual alignment instead of claiming successful synchronization.

---

## 6. Quick capture: the first workflow to design thoroughly

### User story A: unrelated idea during another project

1. User is reading lyric cards on Project A while a Session is paused or playing.
2. Tap the global capture affordance; choose **Record / Lyrics / Text** (or direct record shortcut).
3. Begin audio recording promptly with a visible red state and elapsed time; no destination questionnaire. A metronome can be used if already intentionally enabled, without assigning the idea to the current Project.
4. Stop. Write/close the audio safely, create a Library source with its own ID and timestamp.
5. **Then** offer `Leave in Library`, `Add to Group`, `Copy to current Project`, `Copy to another Project`, and, for an overdub, `Add as take to active Session` where appropriate.
6. If user dismisses placement, the fragment stays in Library, not lost. Return to exactly the prior Board/player/lyric view.

**Avoid hidden coupling:** The fact that a Board is visible does not mean a quick capture is assigned to it. User-initiated **record-over-session** remembers session timing and a suggested destination, but still clearly asks after capture.

### Microphone state machine

- **Off:** no microphone use and no pre-roll. Opening the app, player, or metronome does not change it.
- **Listening:** explicitly enabled user state; mic can buffer a short bounded pre-roll without writing permanent audio until capture. Clear indicator and direct Off control.
- **Recording:** active persisted take capture; visible timer and stop, with app-level safeguards against lost files.
- **Tuner mic use:** explicit mic access while active; does **not** automatically enable pre-roll Listening.

A widget/app shortcut may provide fast entry, but must follow current Android microphone/background restrictions and preserve user initiation. No magical guaranteed hardware quick-record from arbitrary background state.

### Capture durability

Create provisional recording record and temp media; append safely; finalize atomically with content metadata. On interruption/crash/power loss, detect recoverable partial takes on next launch. Never tie recorded bytes to a dismissible placement dialog. Preserve creation time, sample format, mic route and optional session offset for later reconstruction.

---

## 7. Discovery: make the app's name meaningful

**Connections is a contextual capability** available when viewing an item, searching the Library, filling a player layer, or exploring a Board. It should illuminate possibilities rather than generate a gallery that becomes the main activity.

### Candidate connections, progressively enabled

**Immediately reliable from explicit data:** shared Group or descriptive tags; compatible instrumentation; exact or nearby manual tempos; saved chord progressions; recorded-in-same-session proximity; the same Library source used in different projects.

**Analysis-assisted with qualification:** half/double tempo relationships; key/mode possibilities and transposition distance; note/interval contours; rhythmic similarity; repeated chord progressions in other keys. These depend on sufficient evidence or readable structured musical content.

**Lyric/text relationships:** shared words, phrase motifs, rhymes and near-rhymes, syllable count and stress compared with a melody's structure. Prosodic matches should be suggestions to *try*, not claims of lyrical perfection. Meaning/mood analysis is exploratory, optional and not a disguised promise of offline semantic AI.

### Contextual presentation

- On a Library item: `Could work with...` and a short, explicit reason.
- On an empty/selected player layer: `Try these from your Library`, audition without replacing the current combination.
- On a Board: small optional discovery affordance, **never** auto-inserting cards.
- On Theory pages: `Found in your music` deep-links to actual structured fragments where the concept is detected reliably.

A match must say *why* (`91 vs 94 BPM`, `same progression transposed`, `matching stress pattern`) and permit audition; it must not silently transpose, rewrite or classify material. Favor useful surprises alongside strong compatibility: unrelated sources might make a musically interesting juxtaposition even with different tempos.

---

## 8. Musical intelligence and honest attribution

Audio metadata and transcription are extremely useful, but also the easiest features to overpromise.

### Per-take metadata

- **Instrument:** primarily chosen by user (Voice, Bass, Guitar, Piano, other/mixed). No unverified instrument-identification claim.
- **Tempo:** grid used by metronome while recording is *context*, not proof the musician performed at exactly that tempo. Onset-based analysis may estimate actual pulse, display half/double candidates, or say `free time` / `unknown`.
- **Key and mode:** use pitch-class content and evidence for tonic; modal identity can remain ambiguous, especially with short phrases. Store ranked candidates, provenance, confidence and manual corrections. The app should not turn D Dorian and C major into interchangeable metadata.
- **Meter/tuning/role:** only when entered, imported, or supported by reliable evidence.

### Recording-to-written-music

- Monophonic recordings: **candidate** pitch sequence with timing; user edits → MIDI and, with tuning/fingering choice, tab/notation. Preserve original recording.
- Chordal recordings: possible chord-name timeline with confidence, not a falsely precise note-for-note polyphonic score.
- Complex polyphonic transcription: independent feasibility research, possibly an on-device model if genuinely practical; optional rather than foundational.
- Speech ideas → transcript: use on-device recognition only where available and accurate enough; speech capture must still work without it.

All detections are cached derived data, reproducible where practical, reversible and manually overrideable. Analysis must not block capture or make an item unusable on failure.

---

## 9. Tools, Pitch Trainer and Theory: connected but independently useful

### Tools

**Core set:** metronome (tap/typed BPM, additive/compound meter, accents, subdivisions, swing, count-in and evolving advanced options); tuner (chromatic, cent/temperament and reference pitch, low-bass testing); drone/reference tone; standalone recorder using the same saved-fragment path; learn-by-ear functions such as speed/pitch adjustment and A/B looping.

**Later refinement:** advanced polyrhythms, silent-bar trainer, haptic cues, detailed isolation/tone focus and note finder, USB interface selection, MIDI/foot pedals, lock-screen transport, practice timer and automatic activity history. Useful but not allowed to delay the first creation workflow. Avoid reintroducing the unwanted `string tuner mode` or mandatory structured practice session builder.

### Pitch Trainer

A distinct top-level mode, not a hidden submenu. Starts with useful **real pitch matching**, held-note accuracy, intervals and functional hearing; grows into mode identification, chord qualities, inversions, progressions, rhythmic dictation and drills taken from the user's own material. Use actual musical sounds (piano/reference tones), configurable tuning and instrument context, level descriptors rather than feature locks, and progress metrics only when useful. Curated exercises can be added later; do not invent a giant finished exercise library now.

### Theory

A deep navigable reference using the **same music objects** as user editing and Pitch Trainer: written musical structure, intervals, scales/modes, chords, harmony/function, rhythm/meter, voice leading, fretboard, alternative temperaments, acoustics, and advanced topics such as augmented-sixth harmonies, set theory and jazz concepts. Each page should make theory tangible: definition → structure → piano/fretboard/staff → hear/compare → musical contexts → where in your material it appears → known naming disputes.

**Technical commitment retained from research:** musical pitch must preserve letter spelling and accidentals, not just MIDI note numbers; intervals preserve diatonic size and semitone count; meter/rhythm supports precise rational durations and advanced tuplets; chord symbols are parsed, stored structurally, and rendered per dialect; instrument models include actual tuning, capo, transposition and string count. Advanced data *representation* is designed early even where feature-specific UI is deferred.

The reference reports on MusicXML 4.0 and advanced theory are input data for specifications/tests, **not a mandate to implement hundreds of concepts before the first recording works**.

---

## 10. Data boundaries and technical direction

### Conceptual entities

| Entity | Responsibility |
|---|---|
| `LibraryFragment` | Saved source fragment and content type; never requires group/project membership |
| `Group` / `GroupMembership` | Many-to-many Library grouping, no Board |
| `MusicalAttributes` | Typed musical facets, with source/user/detection provenance and uncertainty |
| `Project` | Named workspace with one Board and independent owned material |
| `ProjectFragment` | Editable project-owned copy; source ID is provenance only |
| `BoardCard` | Position/size/visual relationship to a ProjectFragment; not playback truth |
| `Session` / `SessionTrack` | Saved or temporary playing combination; track source + settings, independent of viewed Board |
| `RecordingTake` / `MediaAsset` / `DerivedMedia` | Preserved audio original and linked separate processing/transcription revisions |
| `Association` | Explicit relationships among ideas, takes, written transcriptions, related scraps and sections, without merging them silently |
| `EditRevision` / `TrashRecord` | Undo/recovery for user content, projects and placement operations |

An **idea can contain intentionally associated related material** (riff audio and tab transcript, alternate vocal takes), but that is an explicit relationship, not a reason to hide the constituent Library rows or collapse unrelated fragments automatically. This resolves the old `idea with parts` versus new `mixed-format fragments` conflict.

### Storage and boundaries

- SQLite (with text indexes) for durable searchable models and transactional placement; media files for audio. Implement migrations and backup/restore from the beginning of real development.
- Stable IDs; temporary state vs saved state clearly distinguished; explicit revision and media origin.
- Audio files immutable after capture; project transformations are non-destructive edits; never shared mutable state between Project copies.
- User-selected export and backup folders using Android-supported document access. Backup integrity includes all media referenced by the database, not just SQLite.
- Sessions refer to stable fragment/take snapshots; playback settings are not destructive edits on Library originals.
- One common music-domain core used by Tools, Pitch Trainer, Theory, Board, transposition and formats.
- One controlled audio input/output subsystem; bounded real-time work; costly analysis done off audio callbacks; cache derived renditions without changing originals.

### Technology choice must not be inherited uncritically

The `app-building-export.md` document describes an **earlier constrained APK build route** (Java-only, old API stubs, no AndroidX/NDK). It is valuable build-history context, not an artistic or product requirement. **Before selecting a stack, compare modern Android SDK + Kotlin/Compose/custom drawing, native audio/Oboe where justified, and a Java/AudioTrack fallback against the actual available build environment.** Android's own guidance favors Oboe for lowest latency; if building it is impractical, test a simpler engine and record the tradeoff. Do not design away reliable overdubbing or modern APIs solely to fit an inherited shortcut. Conversely, do not add a native DSP toolchain unless latency or a proven requirement needs it.

**Android constraint:** microphone foreground services and background starts have specific restrictions. Widget/tile shortcuts may offer user-initiated entry, but their behavior must be designed and verified on the target phone; do not promise silent/background capture. (See Android Developers, foreground service background-start restrictions.)

---

## 11. Integration workflows that should define the screens

### Flow 1 — Start without a project

Launch → quick record a free-time riff → stop → source appears in Library (even after dismissing placement) → give it a title and Guitar tag → optionally Group `Riffs` → later find it via search without knowing the Group.

### Flow 2 — Find unrelated material for a song

Open Project A Board → go to Library → search/filter `Love songs`, find lyric row → independent Copy to Project A → filter `Bass, ~90 BPM`, audition several recordings → copy selected audio to Project A and add chosen takes to Session A → return to Board with prior position intact.

### Flow 3 — Explore without committing

Expand Session → combine a looping guitar passage, an independent vocal one-shot and an unmetered sound → adjust offsets and mix → try, but reject, a tempo/pitch suggestion → save a named session or return without modifying the originals.

### Flow 4 — Sing to an existing lyric

Project A Board → pin lyric card → expanded session playing in headphones → record vocal overdub → stop, safely save → compare against earlier take → explicitly place as a take in Session A / independent project fragment → keep lyric visible throughout.

### Flow 5 — Creative independence

Library source copied to A and B → rewrite lyrics in A → edit tab/trim an audio derivative and retag in B → verify neither change affects the other or the Library source; deleting A preserves B and original.

### Flow 6 — Return to creation from learning

Identify an unusual chord in Project A → contextual Theory page shows its spelling, hearing example, possible voicings and use in user's own project → back returns to the specific Board card/session without losing transport or selection.

### Flow 7 — Interruptions and recovery

Record while metronome/player operate → lock screen or change app → resume; if interrupted, partial audio remains recoverable → reboot/relaunch restores Library, active Session setup **stopped**, Board layout, Groups and tags.

These flows are **design acceptance scenarios**, not claimed test results.

---

## 12. A realistic future development order — not work already begun

The phases are ordered around **completed user experiences**, not isolated impressive modules. Each phase would have its own focused UX review and, when implementation begins, a device walkthrough. Later phases must not erase earlier user decisions.

| Future phase | Output / gate | Why it comes here |
|---|---|---|
| **D0 — Product design** | Final navigation map, screen states, wireframes and clickable phone layouts for all five modes, Library list, Board canvas, player collapsed/expanded, lyric+overdub, capture and placement. Finalize the data ownership/interaction contracts and accessibility. | The prior audits repeatedly redesigned navigation after deciding implementation. **No feature coding needed.** |
| **R0 — Technical feasibility research** | Target phone/audio-routing study, background microphone rules, practical build-stack comparison, UI canvas performance expectations, low-bass tuning and capture/overdub latency plan, transcription/analysis risk assessment; only throwaway tests if approved later. | Identify genuine platform blockers before committing to a framework. This is research, not a claim of app progress. |
| **I1 — First complete creative vertical slice** | Create/edit/save source text & audio; unified Library search, Groups/tags, named Project with one Board, independent Copy to Project, quick capture/post-record destination, recovery+backup, mini-player capable of auditioning one item. | Proves identity, organization, ownership and capture together instead of shipping isolated tools. |
| **I2 — Hear ideas together** | Persistent multi-item Sessions with loops/one-shots/free time, offsets, gain/mute/solo, save/reopen, headphones overdub, take comparison, latency handling, pinned lyrics. | Delivers the central act of combining scraps. Must work before sophisticated analysis is marketed. |
| **I3 — Find relationships** | Better typed metadata/filtering and manual corrections; useful first-layer Connections in Library/player/Board; recording-context tempo and optional qualified analysis. Add stable metronome, tuner and drone, all integrated with mic/session control. | Improves discovery using reliable evidence and gives useful daily tools. |
| **I4 — Write and understand** | Structured chords, lyric markers, basic playable chord charts, note/rhythm engine, piano/reference sound, foundational Theory pages and Pitch Trainer exercises; initial MIDI and basic export paths. | One shared theoretical model begins paying off across creation and learning. |
| **I5 — Written music and conversion** | Tab/notation on a shared note representation, flexible tunings, editable mono pitch-to-MIDI candidate, MusicXML/ChordPro export, deeper integration with audio-take timing. | Complex editing rests on stable capture and correct musical structure. |
| **I6 — Depth and polish** | Broader Theory and Trainer curriculum, rhythmic/chord analysis, lyric prosody/rhyme Connections, advanced playback/learn-by-ear/isolation, improved export package, speech transcription if robust, external capture shortcuts, optional pedals/USB. | Adds breadth without making early workflows contingent on risky algorithms. |
| **I7 — Release readiness** | Thorough backup/restore/import/export compatibility, permission/privacy audit, Android process-death and device routing tests, accessibility, performance, release/signing/licensing, store channel packaging. | Release only when originals are safe and ownership/recording flows are trustworthy. |

**Overlap is allowed** when testing shows a dependency; e.g. foundational pitch and rhythm objects belong in I1–I2 schema design, not postponed until I4. The phases are not promises of build dates.

### Minimum coherent first release

A release that calls itself *Apophenia* should already allow **capture → mixed Library → search/group/tag → independent project Board → multi-clip audition → basic overdub → save/recover/export**, plus usable basic Tools, an initial Pitch Trainer, and genuinely interactive initial Theory content. Empty tabs or screenshot-only features are not acceptable placeholders. Later advanced algorithms may be marked experimental; do not claim polished transcription, key detection or isolation before accuracy tests.

---

## 13. What the prior plans got wrong — exact disposition

| Previously proposed | Decision now | Reason |
|---|---|---|
| Workshop as primary tab, folder-first navigation | **Replace** with mixed-format Library + Groups and per-project Boards | Matches user mental model |
| Draft/song fragments linked live to source | **Replace** with independent ProjectFragment copies | Project editing must be isolated |
| Auto-save takes into open draft/section | **Replace** with save-first, ask destination afterward | No wrong-project capture or lost take |
| Automatic pre-roll from tuner/metronome/player | **Reject**; require explicit Listening | Correct consent/microphone state |
| Separate Stacks editor and Stacks Library | **Merge** into persistent player Sessions | One creative combination surface |
| Separate Connections main tab/gallery | **Reject**; contextual discovery only | Suggestions serve creative workflow |
| Only bar-quantized 1/2/4/8-bar layers | **Reject** as universal rule | Free time and one-shots must work |
| Lyrics selected inside stack/player | **Replace** with Board lyric cards that pin during recording | Writing context belongs to project |
| Practice-session builder and mandatory ratings | **Remove** as required core; optional light log/curated exercises later | User rejected structured sessions |
| Tuner string-mode UI | **Remove** | User rejected it |
| Fixed 8-layer limit and auto-conform by key/tempo | **Do not hardcode into vision**; test performance limits, preserve original timing | Avoid false musical assumptions |
| All theory encyclopedia/notation work before usable composition | **Re-sequence** without sacrificing advanced core correctness | User benefit and integration first |
| Constrained legacy Java-only APK method is mandatory architecture | **Reassess** before coding | Toolchain limitations are not user requirements |
| Polyphonic note-by-note, universal instrument detection, perfect key/mode labels | **Research/experimental only** | Honest DSP limitations |
| No backup until polish | **Reject**; backup and recovery are foundational | Creative originals are irreplaceable |

---

## 14. Design ambiguities worth settling before implementation

These are **proposed defaults**, not a request to answer immediately:

1. **What happens when a user duplicates a card within one Project?** Default: create a second independent ProjectFragment rather than a linked editable alias, or make `Duplicate card only` a distinct explicit action.
2. **Can session tracks draw directly from the Library?** Default: yes, as an audition/temporary immutable source snapshot; adding to a Project always creates an independent project copy. A saved Project session should explicitly resolve which ownership it uses.
3. **What happens to Groups when content is copied?** Default: Library Group memberships stay with Library originals. Project copies initially inherit descriptive musical attributes, not active Group membership.
4. **Where do takes recorded over a Session belong?** Default: durable Library take immediately, associated with Session timing, then explicit destination/commit to the active Session or Project.
5. **Who owns provisional detected tags?** Default: detection is an optional candidate layer; user-confirmed tags are authoritative within that fragment identity. No auto-propagation across independent copies.
6. **Can multiple Sessions exist?** Default: save multiple; only one active at a time. Sessions have temporary or named identities and optional Project ownership.
7. **What happens after app relaunch?** Default: restore Board and session configuration but leave playback stopped and mic Off; never auto-resume a recording or open a microphone silently.
8. **How much does the Board constrain form?** Default: no forced grid, section markers optional, arranger view later and opt-in.
9. **Does the initial launch require a heavy ML runtime?** Default: no; core works fully without ML; add offline inference only after accuracy/performance/size analysis.
10. **Framework/audio stack?** Compare options once the actual developer build environment is settled. Do not let the old constrained-build notes prematurely freeze architecture.

---

## 15. Planning definition of done

Before writing production code, the product plan is *ready* only when it has:

- A single agreed vocabulary: Library fragments, Groups, Projects, Boards, Sessions and Takes with no contradictory semantics.
- Wireframes and interaction maps for the seven real-world flows in §11, including transitions, cancel/back, interruptions, small-phone screen use and accessibility.
- A data ownership/copy/undo/recovery matrix, including duplicate within same Project and temporary Sessions.
- A fixed microphone state machine and recording persistence contract.
- A measured feasibility/risk list for timing, key/mode identification, transcription, free-time audio layering and Android microphone restrictions.
- A small, credible first-release feature set whose five tabs are useful and whose differentiator works end to end.
- Clear division between **decided requirements**, **default proposals**, **research hypotheses**, and **future optional features**.

**The key test of the plan:** If a musician has a two-line lyric, an unmetered whispered melody, and a guitar recording from six months ago, Apophenia should help find them, hear them together, try a vocal over them, and build a song without forcing any of those things to stop being independent creative ideas.

---

## Source and verification notes

- Local user-provided planning documents govern the product decisions; October 21 architecture decisions take precedence over earlier audits and tool-focused designs.
- Android's [foreground-service background-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) affect the design of one-tap capture and recorder services.
- Android's [audio latency guidance](https://source.android.com/docs/core/audio/latency/app) and the [Oboe FAQ](https://github.com/google/oboe/blob/main/docs/FAQ.md) inform feasibility tradeoffs, not a final technology decision.
- No claim here constitutes source-build verification, real-device testing, or product implementation.
