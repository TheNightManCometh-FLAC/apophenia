# Apophenia — Authoritative Organization, Board, Library & Player Decisions

**Status:** User-approved product direction, October 21, 2026.  
**Purpose:** Apply the October 21 user clarifications to the architecture, navigation, object ownership, filters, recording, and development sequence.  
**Precedence:** Supersedes conflicting proposals in `plan-audit-1.md`, `plan-audit-2.md`, and `apophenia-independent-audit-3.md`. Does not override unrelated confirmed music theory, privacy, offline, or technical correctness requirements.  
**Implementation status:** Design specification only; no claim that these features are already coded or tested.

## 1. Non-negotiable user decisions

1. **Five top-level modes:** **Board / Library / Tools / Pitch Trainer / Theory.** `Connections` is not a primary tab. A separate `Fragments` tab inside Board is unnecessary.
2. **Only projects have boards.** A Board is the complete visual songwriting surface for one selected project; it holds *all types* of project fragments: audio recordings, lyrics, text, chords, tablature, MIDI, sheet music, and future supported formats. Not a folder browser, AI suggestion gallery, or conventional forced DAW timeline.
3. **Groups are Library organization/filtering, not boards.** A group is a user-named collection such as `Love songs`, `Possible choruses`, or `Unfinished riffs`. Group membership does not imply project ownership or create a board.
4. **Tags are musical descriptive properties**, including **key, BPM/tempo, and instrument type** (optionally meter, mode, tuning, etc.). Show as consistent editable chips/facets rather than only unstructured hashtags. The user may also organize with named groups. A group like `Love songs` and a property like `90 BPM` must not be conflated.
5. **Library is one unified, mixed-format fragment results list.** Fragments themselves are the main content: audio recordings, lyrics, text, chord notes, tablature, MIDI and sheet music all appear together as individually recognizable rows, each with relevant musical tags on the **same row**. A conventional search box and discreet Filter/Sort actions narrow these rows; filtering does **not** replace or occupy the whole Library page. Browse, search, group, tag, multi-select and copy material to project boards; keep an `Unassigned` option without hiding used items from Library-wide search.
6. **Project fragments are independent copies.** If the same source is placed in two projects, modifying the version in project A must not change project B or the Library source. This covers lyrics, tab, MIDI, audio edits/derivatives, tags, and board annotations. Avoid linking editable underlying files across project identities.
7. **A persistent bottom player/session follows the user between all five modes.** Its collapsed state includes transport and identifies the active combination; tapping it expands full controls for playing multiple audio items together, sync/alignment, trim/loop, gain, mute/solo, and vocal/instrument overdub and take comparison. The player is not a replacement Board and not a separate navigation mode.
8. **Lyrics are on the Board, not in a player lyric picker.** Open a lyric card and read it while a session plays and a new take is recorded. Keep the opened lyric readable with an overlay/pinned view as needed.
9. **Quick capture is always available**, including notes or recordings unrelated to the current song. New audio should be saved safely before asking where to put it. **Ask after recording** for destination, not before and not automatically into current project. Preserve current Board and player selections when the capture overlay closes.
10. **Pre-roll requires explicit Listening.** Mic Off is Off. Starting player, metronome or tuner must not silently activate pre-roll; the tuner can ask for mic access when engaged without enabling a pre-roll cache.
11. **Player supports repeating loops, one-shots and free-time clips.** Never require every sound to conform to a common beat grid. Automatic key/tempo adjustments must remain reversible and transparent; don't falsely force uncertain tags into a compatible loop.
12. **Discovery/Connections remains a capability**, for fitting materials, surprising musical patterns, and lyrical/conceptual relationships, but integrates contextually into Library, Board and the expanded player rather than becoming primary navigation.
13. **The application opens into an integrated creative dashboard / current Board context**, with immediate access to creating notes, tools and project switching; it should not be an inbox-only homepage or grid of full-screen launch destinations. Where no project is selected, show a useful empty/launcher state without creating a fake project.

## 2. Conceptual map

```
TOP-LEVEL MODES (persistent tabs)

  BOARD         LIBRARY         TOOLS         PITCH TRAINER        THEORY
  selected      all sources     metronome     training tools        interactive
  project       and filters     tuner         exercises             reference
  visual        groups         standalone
  fragments     metadata       recorder

PERSISTENT BOTTOM LAYER ON EVERY MODE
  Collapsed mini-player → tap to expand active session
  Loop/one-shot combination, independent track controls, record over backing

GLOBAL QUICK CAPTURE ON EVERY MODE
  Audio / lyrics / note, saved before assignment; return to same context
```

Use a responsive phone layout: on a small screen the Board pans/zooms, an anchored player leaves space for the cards, a lyric card opens in a readable sheet that can stay visible while recording. Minimize modal chaining and maintain contextual back behavior.

## 3. Durable data identities, permissions and copy behavior

**Recommended conceptual objects:**

- `LibraryFragment`: a saved original creative fragment or note, type-specific editable content and recording takes; may be unassigned, in groups, and searchable by musical properties. An idea may have related constituent parts, but don't silently merge unrelated scraps into one identity.
- `Group`: named Library collection, many-to-many membership with Library fragments; **no Board**. Removing an item from a group does not delete it, and deleting a group does not delete its members.
- `MusicalAttributes` / indexed tag facets: `key`, `mode`, `tempo`, `meter`, `instrumentTypes[]`, optionally `tuning`, with unknown, user-entered, and estimated values differentiated. BPM numeric with units and estimate provenance, not a string-only hashtag.
- `Project`: named songwriting workspace with exactly one visual Board. A project can own any number of fragments and saved playing configurations.
- `ProjectFragment`: distinct identity and independently editable content copied from Library fragment or from another project. `sourceFragmentId` is **provenance only**, not a live synchronization relationship. Use project-local versions and an independently writable audio path/output when content changes. Copy the audio file on assignment when strict physical separation is required (safer default); immutable source files and storage optimizations may exist internally only if they never allow shared modifications and preserve independent restore/export semantics.
- `BoardCard`: presentation of a `ProjectFragment`, with x/y/size/z-order, optional freeform visual groupings, note placement, collapse, pin, custom labels; editing the card arrangement should not alter the musical source without an explicit editing action.
- `ActiveSession`: independent of currently viewed tab and, initially, bound explicitly to a selected project or marked temporary. Contains selected playable items, track timing/offsets, processing, loop mode, transport, selected take and record state. Never equate the session with the Board layout.
- `SessionTrack`: reference to a specific project-owned fragment snapshot or other chosen recording in the player, plus local playback settings. Editing a track mix/trim/offset shouldn't silently edit original source media.
- `RecordingTake`: immutable captured original plus optional edited/derived takes and transcription versions, tied to an active session when applicable; retain capture offset, sample rate, count-in, latency information, and provenance.
- `InboxState`: optional unreviewed/reviewed state independent of group tags or project membership; new items should remain discoverable even after assigning a group.

**Copy contract:**

1. `Add to project` from Library offers `Create independent project copy` as the clear expected behavior.
2. Source identity and original data remain intact. Every project owns a different `ProjectFragment` ID and edit history, even when made from the same Library original.
3. Two projects do not share mutable file paths, mutable content rows or revision histories. A new edit in A is invisible in B. Exported stems and MIDI are project-specific.
4. Imported existing fragment snapshots may carry initial musical tags and group-derived context, but changes in either place do not propagate implicitly.
5. Deleting a project uses trash/recovery and does not delete a Library original or a separate project's copies.
6. `Add to group` adds membership to Library material; it is **not** project duplication.
7. If a user deliberately wants to apply a change to multiple projects, require a separate explicit multi-project operation; no hidden sync.

## 4. Library workflow: standard search over a mixed-format fragment list

**User-corrected interaction requirement:** The Library itself should resemble a well-designed ordinary searchable list, not a full-page faceted filter builder, an AI recommendations gallery, a folder-only manager, or multiple permanent columns separating lyric files from recordings. **The fragment rows are the primary visible content.** A search field is at the top, with a compact **Filter** control (and optional Sort control). Opening Filter invokes a temporary panel/sheet; applying it returns to the *same results list*. A short indication of currently applied conditions may appear near the search field; it must not displace the fragments.

### 4.1 Layout and row contract

On a phone, the core screen is conceptually:

```text
LIBRARY                                  [ + ]
[ Search fragments, lyrics, tags ... ] [Filter] [Sort]
[All ▼]                         136 fragments

♪  Guitar bridge take.wav        [92 BPM] [Am] [Guitar]  ⋯
≡  Verse idea 7                 [Love songs]             ⋯
▤  Alternate chorus tab         [Guitar] [Drop D]        ⋯
♬  Melody sketch.mid            [90 BPM] [C major]       ⋯
♮  Chorus notation              [3/4] [Piano]           ⋯
▶  Vocal test 02.wav            [Voice] [88 BPM]        ⋯

[ persistent collapsed active-session player ]
[Board] [Library] [Tools] [Pitch Trainer] [Theory]
```

This is a **behavioral wireframe**, not exact typography or a requirement to show every tag at once on a narrow phone. Every row shows a **type icon**, meaningful filename/title or first line, a compact set of applicable **inline tags/properties on the same row**, and a direct overflow/context action. For audio, a preview/play affordance should be accessible without navigating away; playback here does not silently add it to the current Stack/session. For lyric/text, tap opens readable content; for MIDI, tab and notation, tap opens the appropriate preview/editor. A row may expand for additional details without turning the base Library into a card grid.

**Narrow-width treatment:** keep the item name and inline tags in one horizontally aligned row; favor short labels and horizontally scrollable/tag-overflow affordance (e.g., `+2`) over wrapping every tag onto separate lines. Tapping the row or overflow reveals the full metadata. Avoid requiring side-scrolling of the *entire page*. Never invent tempo/key/instrument labels for content where they are unknown or inapplicable.

**Typical immediate actions:** tap row to open, preview playable content, long-press or select to multi-select, `Add to Group`, edit musical tags/properties, `Copy to Project`, `Add to Active Session` for playable media, share/export/delete with recovery. These are not separate global filter destinations. Preserve list position and query when returning from details, editing tags, assigning a group, or opening a project.

### 4.2 Filters and search behavior

- Search is always ready at the top of Library. It searches titles, lyrics/text content, filenames, named groups and indexed musical properties as relevant.
- A compact **Filter** button reveals a temporary bottom sheet/popover for **Group**, **Project usage**, **Type** (Audio, Lyrics, Tab, MIDI, Notation, Text, etc.), **Key/Mode**, **BPM or BPM range**, **Instrument type**, and other warranted properties. Keep the existing results visible when the sheet is closed, never turn filters into a dedicated full-page default experience.
- Filters combine across types, not across separate permanent subpanels. `Type=Audio AND Instrument=Guitar AND BPM≈90` yields matching recordings; a different query/group/type selection can bring back lyrics, MIDI and tabs into the same list. `All types` is the default. Group is a searchable filter, not the Board and not a hidden container.
- Default combination: **OR within a facet** (Bass or Guitar), **AND across facets** (Instrument + Tempo + Group), with a clear `Clear filters` action and match count. Preserving filters while switching tabs is useful; do not apply old filters invisibly without showing an indicator.
- Sorting is a compact control (e.g., Recently captured, Recently edited, Name, Tempo where applicable). A tempo sort cannot silently assign numeric order to lyrics or unknown-tempo items; keep such items visibly under a clear rule.
- `All`, `Unassigned`, `Recent`, and potentially `Used in projects` can be preset scopes in one small selector rather than competing screen-width navigation bars or separate pages.
- User can select multiple rows and assign groups, set applicable tags, or copy independently to a project. Group membership never copies the item; project insertion always produces a project-owned editable instance.

### 4.3 Typed musical attributes and credible metadata

Attributes are internally typed/indexed even though they display as small convenient tag chips. A `90 BPM` chip corresponds to numeric tempo metadata; `A minor` is a tonal key interpretation; `Guitar` is an instrument class. The UI may also display named group membership as a chip, but a group (`Love songs`) is a different data relation from a musical attribute (`90 BPM`).

- **Key/Mode:** A minor, C major, D Dorian, unknown / nontonal. Treat C major and A minor as distinguishable despite equal pitch-class sets. Use explicit estimated/confirmed indications when opening details or editing an uncertain attribute.
- **Tempo:** exact `90 BPM`, range around 90, half/double-time compatible, or free time/unknown. Metronome/capture setting is not proof of performed tempo.
- **Instrument:** Voice, Bass, Electric/Acoustic Guitar, Piano, Drums, Mixed, Other; multiple per recording when appropriate.
- **Optional relevant properties:** Meter, tuning, capo, mode, loop/one-shot/free time, and user-defined descriptive keywords where they actually help discovery.
- Non-audio material can have relevant tags: a MIDI melody may have key, tempo and instrument; tab may have tuning and guitar; notation may have meter/key; a lyric fragment may have a named group but no proven musical tempo or key. Avoid blanks or invented labels merely for visual consistency.
- Attribute editing must be manual-friendly, and automated inference must carry provenance, confidence, and `unknown` states. Changing Library source tags never silently updates independent project copies or remixes their audio.

### 4.4 Library vs Board vs Player

**Library:** one mixed-format searchable list of fragments, inline tags, search/filter/sort, organizing/copying. **Project Board:** visually arranged independent project copies of any of those fragment formats. **Player:** persistent bottom session for auditioning combinations and recording takes; a selected Library audio fragment is added to this session only by deliberate action. Navigation preserves selection, filters, active session and record safety.

## 5. The Board is a creative storyboard, not a file manager

- A selected project displays all its fragment cards in one arrangable field: audio cards have play and waveform preview, lyric cards have readable text, MIDI has compact piano-roll/pattern, tab shows frets/strings, notation shows a staff, chords show progression. Cards can expand and be edited in appropriate context.
- Cards can be freely positioned and visually grouped into song parts without forcing a finished verse/chorus structure, rigid bar grid, or linear timeline. Optional section markers can help later.
- Project picker remains accessible without leaving the Board mode. Board loads with its last scroll/zoom/selection; returning from Library/Tools/Trainer/Theory preserves state.
- The Board is where a user selects a lyric to read while singing. It stays accessible above or alongside the expanded player, with clear way to pin, collapse or scroll the lyric during a take.
- Project-specific copies may be created from Library filtered selections (e.g. voice lyrics from `Love songs` and 90 BPM guitar files); audio fragments must be as first-class as written formats.
- Connections suggestions may appear contextually, unobtrusively, as relevant candidate fragments; selecting a suggestion previews it or copies it to the project only on deliberate action.

## 6. Persistent player and recording workflow

**Collapsed player:** visible across Board, Library, Tools, Pitch Trainer, Theory; active combination name, play/pause, loop status, elapsed time, record state, expand affordance. Must respect navigation bars and overlays.

**Expanded player:** layers/tracks for original audio loops, one-shots and free-time clips, current playback positions, sync-offset/nudge, trim, A/B loop, volume, mute/solo, loop mode, count-in, playback speed/tempo and optional reversible pitch-conform. New recording take captures microphone over the active headphone playback, with backing audio not recorded into the mic take when headphones work as intended. Optionally compare, discard/keep, add take to current track/session, and use reliable monophonic transcription to create editable MIDI/notation/tab without changing original WAV.

**State:** session persists during navigation and note creation; switching project does not silently replace it. On switching projects with an active session, keep the audio combination and show which project/session it belongs to; offer explicit `Switch session` or `Replace` to avoid accidental project mixing.

**Record destination:** recording is immediately and transactionally saved as a new take, then **ask after stopping** where it should go: Library inbox, a named Group, current Project, another Project, or into the active session as a take. If adding to a project, make a project-owned independent copy per the copy contract. Cancellation of placement leaves a recoverable inbox item; never delete a recording because a prompt was dismissed.

**Quick capture:** from any tab, a small overlay for audio/lyrics/notes that can be unrelated to the playing project. On completion restores previous Board position, search filters, lyric card, selected tracks and any inactive player state. When quick capture needs microphone while another feature uses it, show clear capture/playback choices; avoid concurrent mic users and preserve the underlying session. Listening/pre-roll is only enabled explicitly, not when navigation or player starts.

**Timing validation:** capture/input vs output latency, sample frames vs beat grid, sample-rate conversion, loop offsets, clipping, click/backing leakage, Android mic ownership, audio routing, app background, permission failure, process death. Don't promise overdub alignment merely because click BPM is known.

## 7. Core end-to-end acceptance scenario

1. Open Apophenia on a phone; select Project `Love Song A` from Board, with guitar, MIDI, tab and lyrics already visible as independent cards.
2. Enter the Library's unified fragment list (audio, lyrics, MIDI, tab, notation interspersed). Use the search field and compact Filter sheet to find two lyric fragments in Group `Love songs`; close the sheet to see **fragment rows with tags inline**, then change the selected facets to find audio near 90 BPM, Guitar or Bass, with optional compatible key. The result layout does not turn into a filter page or a separate category-only UI.
3. Copy selected Library fragments into Project `Love Song A`. They appear as separate Board cards and are no longer linked for mutable editing to the sources or another project's copies.
4. Audition selected audio recordings in the persistent bottom player, align them, loop appropriately, and mix; one-shot and unmetered/free-time cases remain playable without false conformity.
5. Open a lyric card on Board, keep it readable, record vocals over the active playback using headphones, and listen back. Captured original and per-take timing survive saving and navigation.
6. After stop, choose where the recorded take belongs; add it to the active session and/or project, keep the original recoverable in Library. Compare multiple takes. If pitch-to-MIDI produces a credible note sequence, show a separately editable candidate; if it cannot, don't fabricate notes.
7. Enter Theory or Tools while the session remains available at bottom, then return to Board. Audio session, project Board layout, lyric view and working selections remain intact except for explicitly changed playback controls.
8. During this process capture an unrelated riff or line through global quick capture. Save safely; choose Library/Group or another project afterward, resume previous work immediately.
9. Copy the same original riff into Project `Love Song B`. Edit, trim and retag it there; confirm zero changes to `Love Song A` and the Library source.
10. Save/close/relaunch, verify recovery of projects, board layouts, independent fragment content, last active session (as appropriate), tags and groups. Export separate editable project-owned audio and MIDI without overwriting originals.

## 8. Changes to the old roadmap

- **P0 UI/design gate:** wireframe and device-test the actual five modes, integrated Board with audio, Library facets/groups, collapsed/expanded persistent player, lyric card + recording, global capture overlay. Remove mockup tabs `Fragments` and `Connections` nested inside Board. Test small-phone geometry and interruptions.
- **P1 capture and transport:** capture, standalone recorder, tuner, metronome, mic Listening semantics, crash-safe saving, player shell and audio-session ownership. Player remains available before its advanced editing exists.
- **P2 foundations:** SQLite schema for LibraryFragment, Group, typed MusicalAttributes, Project, ProjectFragment, BoardCard and player/session entities; independent copy operations and rollback; filter/search/organize, project Board including all kinds of cards; backups and migrations.
- **P3 actual creation loop:** multi-audio player, clips of differing timing models, loop/trim/offset, record-over-headphones, latency calibration, lyric-open/record flow, take destination and comparison; basic mix/stem/MIDI export. Bring forward initial transcription feasibility where possible, but don't gate recording on it.
- **P4+ development tools:** more capable transcription/editors, piano and written music playback, theory integration, pitch trainer, advanced conversion and export. Exact sequencing can adapt to working prototypes; navigation and storage contracts must be early.

## 9. Genuine remaining design ambiguities (defaults recommended; not blockers for this decision)

1. **Group membership on a project copy:** recommended: group membership belongs to Library originals; project copies inherit descriptive tags at copy time, while the Project owns arrangement/annotations. A Library search can show copies under `Used in projects`, but they don't silently remain editable members of the original group. Confirm only if a group must actively organize project copies too.
2. **Multiple sources per musical tag:** recommended: one primary tonic/mode candidate plus alternate suggestions; multiple instruments supported per recording; unknown remains explicit. Don't turn detected key guesses into permanent unquestionable labels.
3. **Quick capture during an active overdub:** recommended: preserve session, prompt to pause the current recording before acquiring the mic; don't ever start two microphone capture processes silently. Background playback may continue if explicitly chosen.
4. **Session continuity after relaunch or project switch:** recommended: persist session setup and settings, resume playback stopped (not auto-playing), with explicit context shown so a take can't accidentally be assigned to the wrong project.
5. **Project-copy storage cost:** default to separate mutable project audio files (safe and simple); copies consume space. Later optimize storage only if immutability guarantees, provenance, backup integrity and export isolation are proven.

## 10. Definition of done

A prototype does not pass merely because tabs and cards render. It passes when musicians can perform the ten-step acceptance scenario on a real phone with headphones, no lost recordings, no hidden mic activation, **one searchable mixed-format Library results list with compact filters and inline row tags**, correctly isolated project copies, visible uncertainty in tag estimates, and a player that genuinely survives navigation.
