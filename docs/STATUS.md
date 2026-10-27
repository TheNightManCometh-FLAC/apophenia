# Apophenia — status

**22 Oct 2026:** M0's code is done (scaffold, core types, Room, Device Lab 0.0.2; no git yet, waiting for the repo URL); C4 approved; AI is the fifth mode. **M1 code-complete (builds 0.1.0–0.1.2) and sent, not yet run on the phone.** Waiting on: Christopher's run of 0.1.2 (walkthrough `docs/walkthroughs/M1a.md`) and of the Device Lab, the GitHub repo URL, and the open questions below.

## Build 0.9.11 (26 Oct 2026): the empty Board has no add row of its own
Christopher on finding 5: "Drop the empty Board's add row; the top strip's REC and + NEW are enough."
- An empty project Board no longer has ● RECORD · WRITE ▾ · LIBRARY ›. Its text points to + NEW at the top (which, on a Board, records or writes into the project) and to ⋯ › Add from the Library. The ⋯ menu still has Record into the project, the Write items and Add from the Library.
- The no-project screen (shown when no project is open) keeps + NEW PROJECT and its row, since it isn't a Board.
- **Tests: 605**, unchanged. Emulator-verified: a new empty project shows only the text. Not on the phone.
- Emulator-pass findings now all closed except 20 (key guess on a short take: needs a real mic on the phone) and 2 (set aside).

## Build 0.9.10 (26 Oct 2026): one toolbar in the piano roll
Christopher said to skip the Device Lab finding (he hasn't used it) and continue.
- **14** In piano-roll view, the step-entry rows (length bar; INPUT / MODE / ADD / EFFECTS / TUNING / EDIT / SCORE) and NEXT › / + CHORD are hidden. Only the roll's own toolbar shows (TOOL / SNAP / SCALE / EDIT / TOOLS / zoom), with ▶ PLAY under the roll. Before, there were two EDIT ▾ menus on screen.
- The roll's EDIT ▾ gained Undo and Redo, which share the editor's history [Claude]. SCALE without a key now says to use PIANO ROLL ▾ › Notation, then ADD ▾, because ADD ▾ isn't on the roll's toolbar.
- **Tests: 605**, unchanged. Emulator-verified: one toolbar; drawing a note, then EDIT ▾ › Undo, removes it. Not on the phone.
- Still open: 5 (the empty Board's add row) and 20 (the key guess on a short take). 2 (Device Lab icon) is set aside.

## Build 0.9.9 (26 Oct 2026): a note heard while writing stays out of the player
Christopher on finding 13: "Yes, separate."
- Tapping a fret, a piano key or a piano-roll note now sounds through a **cue voice** in the audio engine (`nativeCue`, `renderCue` in `engine.cpp`), mixed beside the player. Whatever is loaded in the bottom player stays loaded and keeps its place. Before, each note was loaded into the player as "Note", which replaced the take.
- The cue is a 2 s buffer in five pre-allocated slots. The control thread fills a slot the audio thread isn't reading, then publishes it; a new note fades the old one out over 5 ms [Claude]. Notes are rendered once per MIDI number and kept in memory (`pitchCache`, 17 MB at most) instead of as WAV files.
- Other ▶ buttons (Reference, Ear, Performance check, a score's ▶ PLAY) still load into the player, as before.
- **Tests: 605**, unchanged; the engine change has no unit test (C++ callback). Emulator-verified: with the tab loaded in the player, tapping a fret left "GUITAR TAB 0:09 / 0:10" in the player and nothing crashed. **Not ear-verified**: the emulator has no usable sound here, so whether the note is heard needs the phone.
- Still open for Christopher: 2, 5, 14 (two toolbars), 20.

## Build 0.9.8 (26 Oct 2026): emulator-pass findings fixed
Christopher said "Continue" on fixing the emulator-pass findings (`docs/reviews/emulator-pass-2026-10-26.md`). Fixed:
- **11** Music labels no longer go through `uppercase()`: Ear's drill lists read m2 · M2 · m3 · M3; a Reference chord page lists its other names as written (Cmin · Cmi · C-, not "CM · CM · CM").
- **12** Tab and sheet editors: in step mode the length bar follows the selected beat (it opened on 1/4 over a whole rest).
- **16** A new chord chart starts at the click's tempo, as drum charts and sheet music do [Claude].
- **15** New items get a name and a number when it's taken: "Chord chart", "Drum chart", "Sheet music 2", "Guitar tab 2" [Claude] (`ScrapDao.titlesLike`).
- **4** A lyric's Board square shows the chords over the words, without repeating its title line (`LyricSheet.glimpse`, tested); the screen reader says "Lyrics: <title>", not "LYR window".
- **18** The Library names its columns (BPM, KEY) and lines every row up under them; sheet music's tag is SHT, not NOT [Claude].
- **17** A row whose value already ends in ▾ or › has no second ▾ (drum chart, Performance check, Exercises, a recording's FROM).
- **1** The bottom bar's sub-tab hints fit (TOOLS · EAR · EXERCISES).
- **7** The editors' sideways rows fade at the edge they can still scroll to (`Modifier.scrollCue`).
- **3** Bottom sheets sit above the gesture bar and the keyboard (the New project sheet was hidden); name fields take the keyboard when they open.
- **14** (part) The piano roll's pitch range only grows while it's open: the first note no longer makes it jump an octave.
- **19** Playback speed and Isolator say when the player holds something that isn't a recording.
- **5** (part) The empty Board's text lists everything WRITE ▾ makes.
- Left for Christopher: 2 (Device Lab icon in debug builds), 5 (the empty Board's own add row), 13 (hearing a note takes over the player), 14's two toolbars, 20 (the key guess on a short take).
- **Tests: 605** (+1). Emulator-verified: 1, 3, 4, 11, 12, 15, 17, 18. Not on the phone.

## Build 0.9.7 (26 Oct 2026): emulator pass done; drum charts, exercises and switching items fixed
- **The emulator pass is finished** (`docs/reviews/emulator-pass-2026-10-26.md`: 20 findings, what was checked, what wasn't walked). Fixed only what broke the walk; the rest waits for Christopher.
- **Every drum chart crashed the app** (finding 9, since 0.3.6): the chart reader's `{meter: …}` pattern had a bare `}`, which desktop Java accepts and Android's ICU regex refuses. Escaped. **`RegexBracesTest`** (core) reads the app's sources and fails on any pattern with a bare brace; with the old line it fails.
- **Opening an item over another showed the old one's content** (finding 8): + NEW › Sheet music over an open tab opened with the tab in it, and an edit would have saved it there. `ScrapDetail` now starts fresh per item (`key`).
- **No exercise could be added** (finding 10): + ADD ▾'s type step closed the menu. It reopens on the list now.
- **Tests: 604** (+2). Emulator-verified: the three fixes. Not on the phone yet.

## Build 0.9.6 (26 Oct 2026): tab and sheet music open again
- **Found in the emulator pass** (`docs/reviews/emulator-pass-2026-10-26.md`, finding 6): every tab and sheet-music editor crashed on opening, since 0.5.3. The score screen's meter menu held a bare "5/8", which `TimeSignature.of` refuses (two groupings), and that list is built when the screen's file loads. Now 5/8 is listed as 2+3/8 and 3+2/8, like 7/8's two groupings.
- **`ScreenTablesTest`** (app) loads every UI file's top-level values and fails on any that throw; with the old list it fails naming this exact error, so it guards the whole class of bug, not just this list.
- **Tests: 602** (+2). The emulator pass continues (findings 1–7 so far: the cut-off bottom-bar hints, two launcher icons in debug builds, the keyboard hiding the New project sheet, the Board's repeated lyric line, two ways to add things that change, the tab editor's rows running off the edge).

## Build 0.9.5 (26 Oct 2026): playing along with headphones, Bluetooth included
- **Christopher, 26 Oct 2026:** "If you're playing along to a stack they should have headphones in. It should say that." / "Bluetooth is unfortunately the standard now." / "Then don't play the guitar's live feed through the headphones. You can hear it in person. If it's recorded right then you can shift the recording over and it'll sync up." (The app never played the mic back into the headphones; it still doesn't.)
- **The phone's own mic records** whenever headphones are the output (`AudioController.preferredInput`, Oboe `setDeviceId` on the input; `nativeSetInputDevice`). A Bluetooth headset's mic would put Android into call audio; a wired headset's in-line mic is a poor instrument mic. A USB audio interface plugged in is left to Android [Claude]. Applies to recording and the tuner.
- **Timing by playing along** (`core/analysis/PlayAlong.kt`), replacing "hold an earbud against the mic" for headphones: 16 plain clicks at 80 BPM, you clap with each from the 4th; the phone's mic records; each clap is matched to its click (first in a −150…+600 ms window, then the one nearest click + the first estimate, each clap used once); the median is the delay, refused under 6 claps or when the middle half spreads over ±30 ms [Claude, all the numbers]. The clicks are placed by frame counts (`lastGridCounted`, new in the engine), the same way a layer recorded over a stack is placed, so the measured delay is exactly what the layer is moved by. The metronome is put back afterwards and the recording deleted.
- **Per pair of headphones:** kept under the name Android gives the device (two Bluetooth pairs can differ by 100 ms); a pair not timed yet uses the last value measured on that route. The speaker keeps its automatic noise-burst test.
- **Wording:** ⚙ › Mic and overdub now starts with PLAYING ALONG ("Use headphones to play or sing along to a stack, wired or Bluetooth", and why), then TIMING (this pair: n ms / not timed yet; TIME THESE HEADPHONES). Recording over a stack on the speaker says it will be in the take and to use headphones; with headphones, that the take is lined up after. The "not calibrated" notice pointed at Tools; it now points at ⚙ › Mic and overdub.
- **Tests: 600** (+4 PlayAlongTest: 240 ms through ±20 ms uneven claps, 30 ms with claps ahead of the beat, missed claps and a stray noise left out, too few or too uneven refused). Two failures on the way were my tests' own wrong expectations (a ±10 ms jitter bound set at 8; a missed clap I counted as lead-in).
- **Emulator:** the settings screen renders (speaker path; the emulator has no headphones). **Unverified on device:** the play-along timing with real Bluetooth and wired earbuds, whether the phone's mic is really the one opened with a headset connected (`getDeviceId` isn't checked yet), and takes over a stack lining up by ear.

## Build 0.9.4 (26 Oct 2026): MusicXML import reads lines, tremolo and chord diagrams
- **Lines** (`core/score/MusicXmlRead.kt`): slurs, glissandos and trill lines (on the notes) and hairpins, 8va/8vb/15ma/15mb and pedal (as directions) are read. Each start is paired with the next stop of its kind and number in the same voice, across barlines, and stored the way the app keeps lines: on the start event, as how many events of its voice it covers. Before, all of these went on the "left out" list.
  - 8va is read from `octave-shift type="down"` (the shift is from the pitches in the notes, which stay as they sound).
  - [Claude] A line given as a direction ends on the last event that starts before its stop (where the app's own export puts the stop, at the end of the last note). A program that writes the stop at the start of the line's last note gets a line one event shorter.
  - A line that never ends, or covers a single event, is left out and listed. Dashed and bracket lines, two-note tremolos and 22ma are still listed as left out.
- **Single-note tremolo** (1–4 strokes; more are kept as four and listed). **Chord diagrams** from `<frame>` go onto the chord symbol's event: frets absolute, strings from the highest, as in the W3C frame example (an A at the 12th fret written 12 and 14).
- A trill mark that starts a wavy line is read as the trill line's sign, not as a second trill.
- **Round trips:** the 0.9.3 score with every marking, and a second with a diminuendo and slur across a barline, a glissando, a trill line and an 8vb, are exported, schema-checked and read back with their lines, marks, tremolo, graces and diagram as they were.
- **Tests: 596** counted (+2 round-trip tests in MusicXmlTest).
- **Unverified:** files from MuseScore 4, Guitar Pro 8, Dorico or Sibelius (no sample files from them in the repo); not run on the phone.

## Build 0.9.3 (26 Oct 2026): MusicXML export writes the markings
- **The gap since 0.7.0 is closed:** MusicXML export (`core/score/MusicXml.kt`) now writes everything the stored score keeps beyond notes and words. Before, it dropped articulations, ornaments, slurs, hairpins, graces, tremolo, fingering, chord diagrams and the guitar techniques.
- **Written, each checked against the MusicXML 4.0 reference pages:**
  - Every [Mark]: staccatissimo, tenuto, detached-legato (portato), stress, unstress, soft-accent, breath-mark, caesura; up-bow, down-bow, open, stopped, snap-pizzicato; upright fermatas (normal, angled, square); trill-mark, mordent, inverted-mordent (the upper mordent), turn, inverted-turn, turn slash="yes"; arpeggiate (up or down) on every note of the chord.
  - Single-note tremolo; noteheads; fingering and pluck; a circled string number as `<string>`.
  - Grace notes (slashed or not, before or after, with string and fret on the TAB staff).
  - Lines: slurs, glissandos and trill lines as numbered start/stop pairs (1–4 per voice), across bars. Hairpins as `<wedge>`, 8va/8vb/15ma/15mb as `<octave-shift>` (8va is "down" from the written pitch), pedal with a line.
  - Chord diagrams as `<frame>` inside the chord symbol's `<harmony>` (strings numbered from the highest; unplayed strings `unplayed="x"`; `<first-fret>` above the 4th fret) [Claude]. A diagram without a readable chord symbol isn't written, since `<harmony>` needs a root.
  - **Guitar techniques:**
    - Hammer-ons and pull-offs (by whether the next note is higher) and slides, as start/stop pairs on both the notation and TAB staves.
    - Accent, marcato (strong-accent), staccato.
    - Slides in and out as scoop, plop, falloff, doit.
    - Natural and artificial harmonics; tapping with the hand.
    - Tremolo picking (unmeasured tremolo); ghost notes in parentheses; dead notes as x.
    - Let ring, palm mute, fades, swell, whammy, vibrato, slap and pop as `<other-technical>` with the technique's name, MusicXML having no element for them.
    - **A bend is written as a full bend (2 semitones) [Claude]**: the stored score doesn't keep the amount.
- **The schema in the tests:** the official MusicXML 4.0 XSD (w3c/musicxml tag v4.0, W3C Community Final Specification Agreement, test resources only) now validates every Course example, the tab and road-map scores, and a score with every marking. A deliberately broken file is rejected, so the check is real. The exports from before this build were already valid.
- **Import:** reads back scoop/plop/falloff/doit, left-hand tapping, `<other-technical>` by name, slides, unmeasured tremolo and ghost-note parentheses, so an Apophenia file's guitar techniques survive a round trip. A dead note comes back as an x notehead. Lines, single-note tremolo and frames are read on import from 0.9.4.
- **Tests: 594** counted (+4 in MusicXmlTest: schema validity of every export, the full-markings score element by element, the techniques' round trip, the validator rejecting a bad file).
- **Unverified:** opening the files in MuseScore 4, Guitar Pro 8, Dorico or Sibelius. Schema-valid isn't the same as read the way we mean.

## Build 0.9.2 (26 Oct 2026): the Exercises framework
- **PRACTICE › EXERCISES**, a third sub-tab (plan §7 *Exercises*, M7 "Exercises framework filled with Christopher's chosen content"). Built in the shape proposed on 25 Oct; Christopher said Continue after seeing it (26 Oct) without asking for changes, so it stays as built until he does; the shape and defaults are still [Claude].
- **An exercise** = a piece of written music on a **tempo ladder** (`core/tools/TempoLadder.kt`): START, GOAL, STEP. **CLEAN ↑** after a run with no mistakes makes that tempo your best and goes up a step (the last step lands on the goal); **↓ STEP** goes back; a run that wasn't clean leaves the ladder where it is. Changing START begins again from it; the goal can't go below the start; 20–400 BPM, steps 1–40.
- **On the exercise:** THE MUSIC › opens it in its own editor; **▶ CLICK AT n** starts the top-bar click at the ladder's tempo, and it follows the ladder while on; **◷ TIME IT** runs the practice timer against the exercise (tap again to log it; if the timer already has time on something else, it says so instead of overwriting it); TIME LOGGED totals the practice log for that music; ⋯ restarts the ladder, forgets the best, or removes the exercise (the music stays).
- **Where exercises come from:** CURATED is empty ("come with a later update") until Christopher picks the content; the table already has a `curated` id column for them. Meanwhile **YOURS › + ADD ▾** makes any tab, sheet music, drum chart or chord chart from the Library an exercise (format first, then the piece) [Claude]. A new exercise starts at 60% of the piece's BPM (120 when it has none) with 4 BPM steps [Claude].
- **Schema v15:** table `exercise` (scrap_id unique, cascade on purge; curated unique; start/goal/step/current/best BPM; added/last times), auto-migration 14 → 15. A trashed scrap's exercise hides until it's restored.
- **Tests: 590** counted (+5: three TempoLadderTest, the 14 → 15 migration, and one scrap = one exercise, hidden in the trash, gone when purged).
- **Unverified on device:** all of it.

## Build 0.9.1 (26 Oct 2026): Performance check for chords
- **The last case of plan §10.6** ("notes present or missing per chord, from Basic Pitch, at lower confidence"). With a tab or sheet music REFERENCE, a new **YOU PLAYED ▾** picks **One line** (the pitch tracker, as before) or **Chords**; it starts on Chords when the reference has notes sounding together [Claude].
- **How** (`PerformanceCheck.chords`): the reference becomes chords (notes each starting within 50 ms of the one before, up to 200 ms in all, so a written or played strum is one chord [Claude]); the take's **Notes → MIDI transcription** becomes strums the same way. Strums are matched to chords on timing alone (the drum check's tempo and start search, now shared as `lineUp`), so a wrong chord on the beat is that chord played wrong, not a missed one.
- **Per chord:** missed (nothing near it); **notes missing** (a pitch class of the chord not played in any octave); **notes not in the chord**; early or late by over 70 ms; rushing or dragging. A chord note you played only in another octave is listed but not counted wrong [Claude] (Basic Pitch's commonest mistake is the octave, and another voicing isn't wrong). Rows read "Bar 2 · A2 E3 A3 C4 E4: no C4", then "You played …"; ▶ REFERENCE plays the chords around it; extra strums are listed.
- **A take with no transcription** can't be checked for chords: the screen says so and offers ▶ TRANSCRIBE TAKE A (the AI job), then you check again.
- **Tests: 585** counted (+3: a written strum is one chord; C, Am, F barre and G open voicings strummed 10% slower and 0.5 s in score 100; a missing C, a C♯ in an F, a B an octave up, an unplayed chord, a late chord and an extra strum are each reported as they should be).
- **Unverified on device:** all of it, and Basic Pitch itself has never run on the phone, so chord results on a real guitar or piano are [measure].

## Build 0.9.0 (25 Oct 2026): Performance check for drums
- **Drum charts are now references** (REFERENCE ▾ lists them, "TIMING ONLY"), as plan §10.6 has it for drums and percussion. Each step of the chart with anything on it is a hit, placed with the chart's swing as it plays, at its BPM; the hit is named by its drums ("Hi-hat + Kick"). Your take's attacks (the onset detector) are matched one to one after finding your own tempo (0.8× to 1.25×) and start, then judged against a line through the matches.
- **Reported:** missed hits (a ghost note you leave out isn't counted), early or late by more than **40 ms** [Claude] (tighter than the 70 for a melody), rushing or dragging over three or more hits, and extra hits. Problems show as "Bar 2 beat 1.50 · Snare: 52 ms late", with ▶ YOURS; no piano reference for drums, and no pitch graph.
- **Not told apart:** which drum you hit (an onset is an onset), so a snare played where the kick goes counts as on time. Chords are still to come.
- **Tests: 583** (+1: a two-bar rock beat with ghost notes played 8% slower and 0.7 s in scores 100; a dropped hit is missed, a hit 60 ms late is late, an extra onset is extra).
- **Unverified on device:** all of it; how well the onset detector picks up a kit (and a practice pad) through the phone mic is [measure].

## Build 0.8.9 (25 Oct 2026): Performance check
- **AI › Performance check** (plan §10.6; it was the one AI tool not built). Pick a **REFERENCE** (any tab or sheet music in the Library, one part at a time, or the transcribed notes of a recording) and **YOUR TAKE**, then CHECK.
- **How it works** (`core/analysis/PerformanceCheck.kt`): your take's pitch every 20 ms (YIN, the tuner's detector, over a range an octave and a tone either side of the reference); the reference as a single line (where notes overlap, the top one). The two are lined up by dynamic time warping on pitch class, in a band, with 2 s of silence padded round the reference, so a slower, faster or later take isn't marked wrong. An octave shift over the whole take is found and not counted. Your pitch is cut into your own runs (at breaths, jumps of more than 0.7 semitone, attacks), short runs against a note joined to it (a scoop), and the runs matched to the reference notes in order by a note-level alignment guided by the warp's times (repeated notes go one to one). A run left over is an **extra note**, a note left over is **missed**.
- **Per note:** missed; wrong note (which note you sang and how many semitones off); sharp or flat in cents (over 25); early or late in ms (over 70) against a line through your own note starts (Theil–Sen, so a few off notes don't move it). **Why:** scooping up into the note, drifting flat or sharp on a held note, vibrato centred off the pitch, rushing or dragging over three or more notes.
- **Screen:** the score (notes right out of all), a graph of the reference notes with yours over them (off notes in red), the problems grouped into sections (bars when the reference is written), each with **▶ YOURS** (that stretch of your take, looped) and **▶ REFERENCE** (the notes around it on the piano), and the extra notes.
- **AI screen:** "Sheet music and tab" said LATER though it has been in since 0.5.0; now READY. Performance check READY, tap to open.
- [Claude] the tolerances (25 cents, half a semitone, 70 ms), the costs in the note alignment, the "why" thresholds, takes up to 5 minutes. **Not yet:** chords (Basic Pitch per chord) and drums (timing only), which the plan also lists.
- **Tests: 582** (+8 PerformanceCheckTest). **Unverified on device:** all of it; the tests are synthetic pitch curves (a clean take, 12% slower and 0.9 s late, 8% faster, an octave down, sharp and wrong notes, a missed and a late note, scoop, drift, off-centre vibrato, rushing, an extra note in a rest, a chord reference's top line). Real singing and bass through the phone mic are [measure].

## Play download size measured (25 Oct 2026, on 0.8.8)
- **156.5 MB** (`bundletool get-size total` on the signed release AAB: 156,532,363–156,536,559 bytes, the same for Android 10–11 and 12–16 on arm64-v8a). Play's limit for the compressed base download is 200 MB, so it fits with 43 MB to spare; no asset pack needed yet. This closes that [measure] item (measured on this PC, which is what Play's number is).
- Biggest pieces: Moonshine base (60 MB), the four Spleeter stems (75 MB), ONNX Runtime's native library (32.6 MB), the app's dex (24.6 MB with R8 off). If the margin is ever needed, R8 on the dex is the cheap first step (needs keep rules checked on the phone).
- bundletool runs from the Gradle cache with the SDK's aapt2 (`docs/RELEASING.md` has the commands).

## Build 0.8.8 (25 Oct 2026): paint, slice and ghost notes in the piano roll
- **TOOL ▾ › Paint** (FL Studio's brush): drag across the grid and every snap cell the finger crosses gets a note, at the pitch under the finger there (SCALE snaps it to the key). The stroke shows as you drag and sounds each new note; cells that already hold that note are skipped. While painting, the grid doesn't scroll (switch to Draw to scroll).
- **TOOL ▾ › Slice** (FL's knife): tap inside a note to cut it in two at the nearest snap line.
- **Ghost notes** (FL's ghost channels): in a score with more than one part, the other parts' notes show as faint outlines behind the one you're editing, so a bass line can be written against the chords. **GHOSTS** turns them off.
- **Tests: 574** (+1: slicing a half note at beat 2 gives two quarters and does nothing at its ends; a painted stroke across four sixteenth cells adds notes only where the cells were empty, at the pitch the finger was over).
- **Unverified on device:** all of it, especially how painting feels under a finger.

## Build 0.8.7 (25 Oct 2026): arpeggiate in the piano roll
- **TOOLS ▾ › Arpeggiate: up / down / up and down / down and up** (FL Studio's arpeggiator, the part notation can write): each chord in the selection (or everywhere, with nothing selected) becomes its notes one after another, each the snap length, repeating until the chord's longest note ends. Up and down doesn't repeat the turning notes (C E G E C…). Single notes are left alone. With a triplet snap it writes triplets.
- [Claude] Strum and flam are left out: their offsets are milliseconds, which notation can't write and the roll would quantize away.
- **Tests: 573** (+1: a C major half note arpeggiated in sixteenths reads C E G C E G C E up, C E G E… up-and-down, G E C… down; a lone note after it is untouched; written back as eight sixteenths).
- **Unverified on device:** all of it.

## Build 0.8.6 (25 Oct 2026): triplet snaps and a chord stamp in the piano roll
- **SNAP ▾ › 1/8 triplet and 1/16 triplet** (FL Studio has both). Notes drawn on them are written as real triplets in the notation and tab (three eighth-note triplets on a beat come out as one bracketed group). Bars with only straight notes are written exactly as before: triplets are switched on only once a note sits off the straight grid. In compound meters the roll writes the nearest straight values.
- **TOOL ▾ › Chord stamp** (FL Studio's Stamp): pick a chord in **CHORD ▾** (family, then chord: the whole chord catalog), tap a row and the chord appears with its root there, every note the snap length, in the voice being entered. Tones are spelled from the root by their degrees, so E7♯9 gets F𝄪 and C°7 B𝄫. A root that would need a triple accidental is respelled (E♯7♯9 becomes F7♯9). It's a drawing tool, not a suggestion: nothing is proposed, you pick the chord.
- **Tests: 572** (+3: three notes on a triplet snap become an eighth-note triplet with their pitches, straight notes stay straight; the stamp spells E7♯9, C°7 and F♭7♯9, and respells E♯7♯9 as F7♯9).
- **Unverified on device:** all of it.

## Build 0.8.5 (25 Oct 2026): tenor clef key signatures
- **Tenor clef now shows its key signature** (it showed none since 0.5.2). Flats take the usual shape a step above alto's (E♭ major: B♭3 third space, E♭4 top line, A♭3 middle line). Sharps take tenor's own shape so they stay on the staff, as Dorico's documentation and standard engraving have it: F♯3 on the second line, C♯4 fourth line, then G♯ D♯ A♯ E♯ B♯ alternating down and up inside the staff.
- **Test:** for treble, bass, alto and tenor, every sharp and flat of every key up to seven lands on its own letter (F C G D A E B for sharps, the reverse for flats), and tenor's all stay on the staff.
- **Tests: 569** (+1). **Unverified on device:** how it looks (a cello or trombone part in tenor clef with a key).

## Build 0.8.4 (25 Oct 2026): pick your own Ear drills; the new icon
- **Ear › PICK YOUR OWN** (Christopher: "There's no way to practice specific intervals and so on for the rest of the mode"): every chapter (Intervals, Chords, Pitch, Scale degrees, Scales, Harmony and the others) lists, under its drills, every answer it has as a chip (m2 … M13; Major, m7, 7♯9…; root position, 1st inversion…; each degree; each mode…), grouped by the drill it first appears in. Tap the ones you want, then PRACTISE THESE n. The same sound is listed once (m3 is in two interval drills); a degree in a minor key is its own chip. Mic drills and tap-back are left out, as in mixed practice. Picks are kept per chapter while the app runs; your pick has its own high score (its id carries the picks) and shows in HIGH SCORES as "Your pick: m3 · P5". [Claude] at least two picks (one alone always has the same answer).
- **New app icon** (Christopher's painting): it fills the whole adaptive-icon layer (it's a full-bleed image, not a logo on black), so launcher masks trim the edges and the waveform sits in the centre. The Play icon and feature graphic are re-rendered from it.
- **Tests: 568** (+2: a picked drill asks only its picks and plays them right, and its id brings it back; every chapter can be picked one answer at a time with each sound listed once). **Unverified on device:** all of it, and how the icon crops under the S22 Ultra's launcher mask.

## Build 0.8.3 (25 Oct 2026): Settings and themes
- **⚙ Settings** at the right of the top bar on every screen (Christopher: "a settings gear somewhere with anything that should be as well as a themes"). Sections: **THEME**; **CHORD SYMBOLS** (show as, read typed chords as, note names); **PITCH** (A4, octave numbers); **REFERENCE** (draw pages on guitar/bass/piano/staff); **MIC AND OVERDUB** (mic permission, overdub latency, moved here from the bottom of Tools); **YOUR DATA** (back up now, backup folder, restore, trash); **ABOUT** (licences and privacy, version). The same settings stay where they act (CHORDS ▾, the tuner, SHOW ON ▾, Library ⋯); it's one setting in two places, not a copy.
- **Themes:** Console (default, C4), Paper (light, the plan's light option), Midnight, Phosphor, Plum, High contrast. Each row shows a swatch; the choice is kept; the system bar icons go dark on Paper. [Claude] the six schemes and their colours. Colours are now read from the chosen palette everywhere (`C.palette` is Compose state).
- **Contrast test** (`ThemeContrastTest`): in every theme, text, highlight, accent and record colours read at WCAG AA 4.5:1 on all three grounds, dim labels at 3:1, the kind tag on its accent at 4.5:1. It caught two: Console's record red was 4.35:1 on the ground (now #DA665A, slightly lighter than C4's #C9564B) and Paper's accent 4.47:1 (darkened).
- **Tests: 566** (+3). **Unverified on device:** all of it, and how each theme looks on the S22 Ultra's screen.

## Build 0.8.2 (25 Oct 2026): the Board as loose squares; AI under MAKE
- Christopher: "again the board should be loose squares that you can instantly understand what they are by the tags, and interact with for the full view of the fragment" and "not a list". The Board already had free windows, but new ones landed in a tidy two-column grid of short wide boxes, which read as a list (and the nav mockup drew it as one).
- **Squares:** new windows are squares (168 dp on the S22 Ultra's width, at most 200 dp so wider screens get more columns), on a staggered layout (every other column a third of a square lower), nudged a few dp each and tilted under a degree and a half, so they look laid down by hand. Windows still where an older build put them by default are laid out again as squares; ones you moved or resized stay where you put them. [Claude] the stagger, nudge and tilt.
- **What it is at a glance:** each square leads with its kind as a filled tag (RECORDING, LYRICS, NOTE, CHORDS, TAB, MIDI, SHEET MUSIC, DRUMS), then its own tags as chips, then the title, the glimpse, and BPM · key · takes. Tap opens the full view; hold and drag moves; the corner resizes (as before).
- **AI moved to MAKE** (Christopher: "ai is not study It's make"): MAKE is Board · Library · AI; STUDY is Course · Reference.
- **Tests: 563** (Board layout: squares staggered and never overlapping, wide Boards get more columns, a new square never covers a moved one, old default windows found and moved ones not, clamping).
- **Unverified on device:** all of it.

## Build 0.8.1 (25 Oct 2026): piano roll as a way to write sheet music
- Christopher: "Its an alternative mode of writing sheet music as I see a piano roll." **+ NEW › Piano roll** (also WRITE ▾ › Piano roll and Board ⋯ › Write in the piano roll) makes new sheet music and opens it straight in the piano-roll view. It is the same sheet-music scrap: VIEW ▾ switches it to notation, tab or chord chart and back.
- Plumbing: `AppModel.writeSheet(projectId, roll = true)` → `open(..., view = "roll")` → `detailView`, which ScoreView takes as its first view.
- **Tests: 561.** build-verified. **Unverified on device:** all of it.

## Build 0.8.0 (25 Oct 2026): navigation B and + NEW
- **Three bottom tabs** (Christopher picked option B from the mockup `docs/mockups/nav-b.html`): **MAKE** (Board, Library) · **PRACTICE** (Tools, Ear) · **STUDY** (Course, Reference, AI). Each tab's sub-tabs sit along the top of its screen; a tab reopens on the sub-tab you left it on. "Learn" is gone as a name; Course, Ear and Reference are sub-tabs, and a Course link to an Ear drill switches to PRACTICE › Ear with the lesson kept behind it.
- **Unchanged on every screen:** project ▾ and mic state, the player and stack at the bottom, and the strip at the top (Christopher: "At the top"), which now reads CLICK ▾ · TOOLS ▾ · ● REC · **+ NEW**.
- **+ NEW** (Christopher: "You select the type of fragment at new + and that determines if it opens a notepad or midi or tab or the recorder"): Record, Lyrics, Text note, Chord chart, Drum chart, Guitar tab, Bass tab, Sheet music, Import from the phone. Each opens its editor (or the recorder). At the top of the menu: **INTO LIBRARY** or **INTO <PROJECT> + LIBRARY** (his rule: "either just the library or project and library"). [Claude] It starts on the project when you're on its Board, on the Library everywhere else.
- [Claude] The Board keeps its own WRITE ▾ and ⋯ › Record; mic permission and overdub latency stay under PRACTICE › Tools.
- Piano roll: answered in 0.8.1 (it is another way of writing sheet music).
- **Tests: 561** (no new tests; navigation is UI only). build-verified.
- **Unverified on device:** all of it.

## Build 0.7.9 (25 Oct 2026): Ear as practice and high scores
- **Ear (Learn › Ear)** reworked to Christopher's direction: no chapters to work through, no Listen / Practice / Test modes. Two sections: **PRACTICE** (Mixed: everything, then the drill types as rows that open on their drills, and Your music) and **HIGH SCORES** (every drill you've played, by its best streak, highest first; tap one to practise it). Every drill opens straight into practice.
- **Streak counter** (Christopher: "A streak counter for how many in a row would make it feel like you're actually getting somewhere"): the drill screen shows STREAK large (right answers in a row; a wrong one resets it) and the drill's BEST beside it, which turns to NEW BEST when you pass it. The best run of each session is saved; a drill's high score is its longest run ever (`EarResultDao.streaks()`, no schema change).
- [Claude] The drill types stay as grouping rows (they aren't lessons); old test scores stay in the database but aren't shown.
- **Tests: 561** (+1: a drill's high score is its longest streak in any session, with sessions and last time).
- **Unverified on device:** all of it.

## Navigation: Christopher picked B (25 Oct 2026)
- **Three bottom tabs by intent, each with sub-tabs: MAKE (Board, Library) · PRACTICE (Tools, Ear, high scores) · STUDY (Course, Reference, AI).** Exact sub-tab placement to be mocked up.
- **Kept on every screen, unchanged:** the persistent player strip (player and stack), "Obviously leave the stack like player", plus the **metronome** and the **recorder** from Tools, and a **New +** button beside them for adding a fragment (record, lyrics, chord chart, tab, sheet music, note).

## Christopher's first phone verdict (25 Oct 2026, on 0.7.8)
- "It's well done, just needs polish and some deeper organization."
- **Navigation:** "The menu layout in general. 5 tabs for everything isn't the best way." The five-mode bottom bar is to be replaced. Next: research how real music and notebook apps organise this, then materially different options as clickable mockups for his pick (CLAUDE.md §5.6 rule), then build.
- **Ear training:** "The pitch training should drop the course approach and just have the practice and high score section to avoid being too close to Rick Beato's." Chapter chips and the Listen / Practice / Test structure go; drills to practice plus a high-score section stay. The theory Course wasn't part of this.

## M7 part 9 — build 0.7.8 (24 Oct 2026): the upload key and the icon
- **Upload key made** (Christopher, 24 Oct 2026: "Back it up onto my actual computer"): `keys/apophenia-upload.jks`, PKCS12, alias `upload`, RSA 4096, valid to 11 Mar 2054, certificate SHA-256 `C5:50:9F:E4:D7:A6:BB:A0:6C:37:FF:D4:A1:E4:42:A9:E1:F1:22:62:C9:7D:46:D2:62:8D:A5:99:28:35:F8:7C`. Random 32-character password, the same for store and key, written only to files. `keystore.properties` and `keys/` are gitignored (checked with `git check-ignore`).
- **Backed up on this PC, two drives:** `C:\Users\Wi-Fi\Documents\Apophenia upload key\` and `D:\Backups\Apophenia upload key\`, each with the .jks, password.txt, the certificate (.pem), a README, and `apophenia-upload-key.zip` holding all four (the Independent Video layout). Both copies byte-identical to the original.
- **Signed release builds:** `bundleRelease` and `assembleRelease` now sign with it: the APK verifies (apksigner, v2) and the AAB's certificate matches. Kept in `dist/release/` (Apophenia-0.7.8.aab for Play, .apk for GitHub). A release APK can't install over a debug install (different keys), so phone tests stay on the debug zips.
- **Launcher icon:** Christopher's artwork (`art/apophenia-icon-source.png`, 1254 px) as an adaptive icon: black background, the art 66 dp across the 108 dp layer, so every launcher shape (circle, squircle, square) shows all of it; PNG layers for every density, regular and round. Play listing icon `fastlane/.../images/icon.png` 512 px. `tools/store/icon.ps1` regenerates them all.
- **Tests: 560**, all passing. First release changelog moved to `changelogs/69.txt`.
- **Feature graphic** (Christopher OK'd making it from the artwork, 24 Oct 2026): `fastlane/metadata/android/en-US/images/featureGraphic.png` 1024×500, made by `tools/store/feature.ps1`. **Still needed for Play:** phone screenshots; the Play Console setup is Christopher's.
- **Unverified on device:** the icon on the S22's launcher, and everything since 0.5.2. Christopher still has not seen notation drawn (0.5.2–0.7.8).

## M7 part 8 — build 0.7.7 (24 Oct 2026): release prep, and the network permission fixed
- **Found and fixed: the installed app asked for INTERNET.** The source manifest never did, but ONNX Runtime 1.31.0's own manifest adds INTERNET and ACCESS_NETWORK_STATE, and LiteRT 2.3.0 pulled in Google Play's AI-pack delivery (play-services-basement, asset-delivery, WorkManager: RECEIVE_BOOT_COMPLETED, WAKE_LOCK). Every build since ONNX Runtime arrived had them in the merged manifest, against plan §1 and the live privacy page. Now: the two network permissions are removed at merge (`tools:node="remove"`), the Play/GMS group is excluded from LiteRT (only its unused AiPackModelProvider needs it), and **every build fails if the merged manifest asks for INTERNET, ACCESS_NETWORK_STATE or RECEIVE_BOOT_COMPLETED**. NoticesTest also checks the merged manifest. The 0.7.7 APK asks only for RECORD_AUDIO, the foreground-service permissions and Android's own receiver permission (aapt2 badging, build-verified). No Google Play Services remain in the dependency tree.
- **Release builds:** `bundleRelease` (AAB 170 MB) and `assembleRelease` (APK 208 MB) build; release lint passes (it failed on the 2019 Fragment from GMS). Signing reads a gitignored `keystore.properties`; without it the outputs are unsigned. [Claude] R8 off (see docs/RELEASING.md).
- **Store listing drafts:** `fastlane/metadata/android/en-US/` title, short description, full description (from the README), `changelogs/68.txt`. F-Droid recipe draft `tools/release/fdroid/com.betterappmafia.apophenia.yml`. The whole procedure, sizes, and the F-Droid open questions (prebuilt ONNX Runtime/LiteRT AARs, model weights) in **docs/RELEASING.md**.
- **Missing for Play (Christopher):** the release (upload) key; a launcher icon (the app has none, so Android shows its default) and the 512×512 store icon, feature graphic and screenshots.
- **Tests: 560**, all passing.
- **[measure] not done:** Basic Pitch transcription with the Play libraries excluded (LiteRT's Interpreter shouldn't need them; check on the phone); (`bundletool get-size`: measured 25 Oct 2026, 156.5 MB of 200).
- **Unverified on device:** all of it. Christopher still has not seen notation drawn (0.5.2–0.7.7).

## M7 part 7 — build 0.7.6 (24 Oct 2026): the FL Studio package
- **Stack ⋯ › Export for FL Studio** and **Board ⋯ › Export for FL Studio** (plan §11): a dropdown asks which audio goes in — *Stems and original takes*, *Stems only* or *Original takes only* (the plan's "originals and/or conformed stems") — then the save picker takes a `.zip`. The Board's entry puts the project's stack in the player first (asking before it replaces another, as everywhere).
- **In the zip** (`<Project>/`): **Stems/** each layer alone over the whole stack from its start (loops written out, conformed as the stack plays them, at the layer's gain, whatever mute/solo say), numbered in stack order, plus **Mix.wav** (the bounce); **Takes/** the original recordings unchanged, the stack's takes and every live take of the Board's other recordings; **MIDI/** each tab and sheet scrap through the app's MIDI export (tempo map, meters, key signature, rehearsal marks), each chord chart as a chord track (bass plus voicing, struck at each change and barline, sections as markers); **README.txt**, the manifest: tempo and key, how to put it together in FL Studio (tempo, stems at bar 1, File > Import > MIDI file), and per layer its mode, start in seconds and as bar/beat of 4/4 at the stack's tempo, the part of the take played, gain, muted, conform amounts, its stem and original; per MIDI file its tracks, tempos, meters, key and marks (written out in case FL Studio drops markers or meter changes — [measure]).
- **Engine:** the bounce can render one layer alone (`nativeBounceStack` onlyLayer), so the stems are the engine's own samples, sample-for-sample what the stack plays. The export waits for the stack to load and refuses while a layer is still being conformed.
- [Claude] choices: 48 kHz 24-bit mono WAV (the engine's format); the stack has no meter, so bar positions assume 4/4 and say so; the zip is written as it's made (no copy in the app); a failed export deletes the half-written file.
- **Tests: 560** (+3 FlPackageTest: a chord chart becomes a chord track with its symbols, sections, a 3/4 change, N.C. as silence, B♭ spelled in F major, and the same notes through MIDI and back; the manifest gives a layer's start as 0:02.000 (bar 2 beat 1) at 120 BPM, the take region and gain; clock, bar/beat and safe file names).
- **[measure] not done:** opening the package in FL Studio (plan §11, M7's exit test: "Import an exported project into FL Studio with everything in place"), and the size and time of a long stack's export on the S22 Ultra.
- **Unverified on device:** all of it. Christopher still has not seen notation drawn (0.5.2–0.7.6).

## M7 part 6 — build 0.7.5 (24 Oct 2026): MIDI import
- **Import now takes MIDI** (`.mid`, `.midi`, `.kar`; plan §11 "Import: … MIDI"), which was refused until now. A Standard MIDI File (format 0 or 1) becomes written music: a tab scrap when a part is a guitar or bass, else sheet music; open it in the piano roll, notation or tab view. `core/score/MidiRead.kt`, wired in `capture/Imports.kt`.
- **Read:** notes (a note-on at velocity 0 counts as a note-off; running status), the tempo map, time signatures, key signatures, track names, markers (as rehearsal marks) and program changes. One part per track, split by channel when a track uses several; channel 10 is a drum part (percussion clef).
- [Claude] choices: timing is snapped to sixteenths, or for a quarter beat whose notes sit at least twice as close to that grid, to sixteenth triplets. The bars are written the way the piano roll writes them (beat-aware durations, ties over beats and barlines, overlapping notes as tied chords in one voice). General MIDI guitar programs (25–32) get a standard guitar with frets, bass programs (33–40) a 4-string bass; anything else is notation, in bass clef when its middle note is below A3. Spelled in the first key signature (else C major). Tempo and key changes go on the bar they fall in. Velocities are kept only when they vary. Repeats are already played out in a MIDI file, so they come in as written-out bars. Not read: lyric and text events, pitch bend, controllers; SMPTE-timed files are read as 120 BPM. The notice says what was skipped.
- **Export change:** the app's MIDI now carries key signatures (major/minor; a mode goes out as its signature), so a file exported and imported keeps its spelling.
- **Piano roll:** `PianoRoll.rebuild` takes the grid division (12 lets triplets be written); the editor still uses 4.
- **Tests: 557** (+4 MidiReadTest: a two-part score with a tied note, a quarter-note triplet, a 3/4 bar with a tempo change, a marker and B♭ major reads back with the same notes, spelling, meter, tempo, key, rehearsal mark, and the bass fingered on its strings; the road map plays out its repeats; the course tab comes back as guitar tab; a hand-made format-0 file with played timing snaps to sixteenths and a triplet beat, keeps varied velocities, reads running status and velocity-0 note-offs, and puts channel 10 in a Drums part; non-MIDI and note-less files are refused).
- **[measure] not done:** MIDI from FL Studio, a DAW-humanised performance and a karaoke .kar.
- **Unverified on device:** all of it. Christopher still has not seen notation drawn (0.5.2–0.7.5).

## M7 part 5 — build 0.7.4 (24 Oct 2026): MusicXML import
- **Library (or a project) › import** now takes MusicXML: `.musicxml`, `.xml` and compressed `.mxl` (plan §11 "Import: … MusicXML"). A score with a fretted part becomes a tab scrap, any other becomes sheet music, titled from the score's title (else the file name), tagged "imported". The import notice says what a file was read without (e.g. "slurs and slides"). `core/score/MusicXmlRead.kt`, wired in `capture/Imports.kt`; the picker also offers the MusicXML MIME types.
- **Read:** notes, rests, chords, up to four voices a staff (`<backup>`/`<forward>`, gaps filled with rests, hidden in voices 2–4), dots, tuplets (nesting from the `<tuplet>` brackets checked against the cumulative `<time-modification>`), ties, graces, key with mode, time (additive 2+2+3), clefs incl. octave clefs and changes, `<transpose>` (pitches kept sounding: a guitar written an octave up comes in an octave down), tab (TAB staff `<staff-tuning>` from the lowest string, `<technical><string>/<fret>` from the highest; the tuning matched to a preset or the Reference's 45, else Custom), `<harmony>` as ASCII chord text (the file's own `kind@text` when it reads as a chord with all the listed degrees, else built from kind + degrees; an added 9/11/13 on a seventh chord written 7b9, not 7addb9), lyrics with hyphens and extenders, dynamics, words (italic = expression), D.C./D.S./Fine/To Coda words as jumps, segno/coda, rehearsal marks, tempo (metronome, else `<sound tempo>`), repeats and times, 1st/2nd endings, barline styles, articulations (staccato/accent/marcato as note effects, tenuto, staccatissimo, portato, stress, fermatas, breath, caesura), ornaments, arpeggios, noteheads, fingering, hammer-ons, harmonics, bends, taps. The DTD is never fetched.
- [Claude] choices: a part with a notation staff and a TAB staff becomes one fretted part (frets from the TAB staff); other multi-staff parts become one part per staff ("Piano (upper)", "Piano (lower)"); a score with no accidentals and no `<mode>` has no key (the app writes C major with its mode); 5/8, 7/8, 11/8 given as one number are grouped as twos then a three; frets default to 22. Timewise files are refused with a reason. Not read yet: slurs, hairpins, 8va and pedal lines, voices beyond four, microtones.
- **Export change:** expression text is now written in italics (below the staff), so it comes back as expression.
- **Tests: 553** (+7 MusicXmlReadTest: everything the app writes reads back identically — a two-part score with repeats, endings, a 7/8 bar with two voices, a hidden rest, lyrics, chord symbols, a key, meter and tempo change, segno + D.C. al Fine, a bass tab; the course tab and road map; nested triplets keep both brackets; a guitar with `<transpose>` sounds an octave down with frets, staccato, tenuto, fermata and a slashed grace; a piano's staves become two parts, voice 2's gap is a hidden rest, a 5/8 measure rest fills the bar, G7 + added ♭9 over B reads G7b9/B; a TAB-only part gets Bass drop D and a bass clef; .mxl through its container; timewise, HTML and non-XML refused; keys from fifths and mode).
- **[measure] not done:** files from MuseScore 4, Guitar Pro 8, Sibelius and Dorico (needs real exports; Christopher can send some).
- **Unverified on device:** all of it. Christopher still has not seen notation drawn (0.5.2–0.7.4).

## M7 part 4 — build 0.7.3 (24 Oct 2026): the BAM splash
- **Cold start shows the Better App Mafia mark, as Independent Video does** (Christopher, 24 Oct 2026: "the BAM splash screen like independent video"). Ported from Independent Video's `MainActivity.showSplash`, with the same asset (`assets/brand/bam_logo.png`, 512×512, feathered edges) and timing: true black, the logo at 78% of the screen's short side, fading in and growing from 94% over 520 ms, held until 1.7 s, then fading out over 380 ms to the app. A tap skips it. `ui/Splash.kt` (BamSplash), drawn over the Shell in MainActivity.
- **System splash:** Android 12+ always draws its own (the launcher icon). The new `Theme.Apophenia` (res/values and values-v31) sets the window and `windowSplashScreenBackground` to black so the system splash, the BAM screen and the app meet with no flash. [Claude] No androidx core-splashscreen library: the platform splash plus a black theme gives the same result without a new dependency.
- [Claude] Not shown when the app is opened by the Record widget or shortcut (the take starts at once and the screen must show the mic), and not after a rotation or return from the background (cold starts only, as in Independent Video).
- **Tests: 546**, all passing (no new tests: a splash is visual).
- **Unverified on device:** all of it. Christopher still has not seen notation drawn (0.5.2–0.7.3).


## Repo and website (24 Oct 2026, after build 0.7.2)
- **GitHub:** https://github.com/TheNightManCometh-FLAC/apophenia, public, GPL-3.0-or-later, described as "A 21st century musician's notebook" (Christopher, 24 Oct 2026: "Put it on github like the last one. Give it a full repo."). First commit c1b220c holds the whole project: app, core, audio, data, docs, site, reference, tools, skills-to-install. Not in git: build outputs, local.properties, dist/, keys, .claude/, .kotlin/. Line endings LF (`.gitattributes`; this repo has core.autocrlf off).
- **Website and privacy policy** (GitHub Pages from the `gh-pages` branch, made from `site/` with `git subtree split --prefix site`): https://thenightmancometh-flac.github.io/apophenia/ and **/privacy/** — the privacy policy URL for the Play listing. To update the site: edit `site/`, commit, then `git subtree split --prefix site -b gh-pages && git push -f origin gh-pages`.
- **ONNX Runtime 1.31.0 ThirdPartyNotices.txt** downloaded from the release tag (Christopher OK'd it) and added to the app's licences. Copyright holder "Better App Mafia" confirmed by Christopher.
- Contact on the site: cporter31415@gmail.com (as Independent Video's).

## M7 part 3 — build 0.7.2 (24 Oct 2026): licences and privacy
- **Library ⋯ › Licences and privacy** (plan §13 M7: "privacy and licence notices"; [Claude] placed in Library ⋯ beside backups, as the app has no Settings screen): a PRIVACY paragraph (no internet permission, no accounts/ads/analytics; mic off at launch and open only for recording, the tuner and sung/played drills; files stay on the phone; backups and exports only where you pick; all AI runs on the phone), then every bundled piece with what it does, its licence and copyright holder; tap one to read its licence text from assets (the long LiteRT and ONNX Runtime notices show their first 60 kB and say where the rest is).
- Pieces: Apophenia (GPL-3.0-or-later), Oboe 1.11.0, Signalsmith Stretch and Linear (MIT), LiteRT 2.3.0 (+ its THIRD_PARTY_NOTICE, copied from the AAR), ONNX Runtime 1.31.0 (MIT), Basic Pitch, Spleeter 4-stems (MIT; the ONNX conversion Apache-2.0), Moonshine base (MIT), Bravura, Noto Music, JetBrains Mono (OFL 1.1), Iowa MIS piano, Jetpack and Kotlin (Apache-2.0).
- **Repo:** `LICENSE` (GPL-3.0 text) and `NOTICE.md` at the root, the same table. New licence texts in assets: apophenia-GPL-3.0.txt, apache-2.0.txt (the canonical 11 357-byte text), the two Signalsmith licences, litert-THIRD_PARTY_NOTICE.txt.
- **Tests: 546** (+3 NoticesTest: every notice's licence file exists and every file in assets/licenses is listed; the manifest has no INTERNET permission, as the privacy text says; every bundled model and font has an entry).
- **Not done:** ONNX Runtime's own ThirdPartyNotices.txt isn't in the AAR; adding it means downloading it from the 1.31.0 release (ask Christopher before downloading). The "Better App Mafia" copyright line on Apophenia itself is [Claude]: confirm the holder's name. A privacy policy URL for the Play listing is still needed (Play asks for one even with no data collected).
- **Unverified on device:** all of it. Christopher still has not seen notation drawn (0.5.2–0.7.2).

## M7 part 2 — build 0.7.1 (24 Oct 2026): MIDI from scores, ChordPro, text tab
- **Tab and sheet scraps › ⋯** now also has **MIDI** and **Text tab** next to MusicXML. `core/score/ScoreMidi.kt`: Standard MIDI File format 1 at 960 ticks a quarter; a conductor track (title, every tempo and time-signature change, rehearsal marks as markers) and one track per part with its name and a General MIDI program (guitar 25, bass 33, else piano 0; percussion parts on channel 10). Bars are written in playing order, repeats and endings taken as the app plays them; tied notes are one long note. [Claude] Velocity is the note's own playback velocity, else 80; dynamics, articulations and chord symbols aren't in the MIDI yet.
- **Text tab** (`core/sheet/Exports.kt`): every fretted part, its tuning and capo, four bars a line, from the tab view's own renderer.
- **Lyrics and chord charts › ⋯ › Export ChordPro** (`.cho`, chordpro.org v6): lyrics add `{title}`, `{key}` (major and minor keys only; ChordPro has no mode names) and `{tempo}` when the text lacks them; a chord chart becomes `{start_of_grid}` blocks, one per section with its label, four bars a line, `.` for a held beat, `{time}` where the meter changes. **Text and drum charts › Export text** (`.txt`).
- **Tests: 543** (+3 ScoreMidiTest with a small MIDI reader: format 1, 90 BPM = 666 667 µs, 7/8 time signature bytes, a tied D4 joined into one half note, piano program; the road map plays bars 1 2 3 1 2 4 5 1 2; a guitar part gets program 25 and sounding E2; ChordPro grids, keys, a kept title; six string lines of text tab).
- **[measure] not done:** opening the MIDI in FL Studio (tempo and meter changes, markers; plan §11's open question), the ChordPro in a ChordPro reader (OnSong, SongbookPro).
- **Unverified on device:** all of it. Christopher still has not seen notation drawn (0.5.2–0.7.1).

## M7 part 1 — build 0.7.0 (24 Oct 2026): MusicXML 4.0 export
- **Tab and sheet scraps › ⋯ › MusicXML** saves a `.musicxml` file where you choose (plan §11: "MusicXML 4.0 (notes, chord symbols, tab, tuplets)"). `core/score/MusicXml.kt`.
- Written: notes and rests (hidden rests print-object="no"), chords, dots, tuplets (cumulative `<time-modification>` plus brackets on the outermost group), ties as both `<tie>` and `<tied>`, up to four voices (`<backup>`, `<forward>` to fill a short voice), key with mode, time (additive meters as 2+2+3), clef and mid-part key/meter/clef changes, tempo (metronome + sound tempo in quarter BPM), chord symbols as `<harmony>` (root, kind, degrees, bass, N.C.), lyrics with syllabic, dynamics, staff text, rehearsal marks, repeats, 1st/2nd endings (stop under an end repeat, discontinue otherwise), double/final/dashed barlines, segno/coda and the D.C./D.S./Fine words. Divisions are computed so every event and bar is a whole number of them.
- **Fretted parts:** sounding pitch on an octave clef (G2 or F4 with clef-octave-change −1, never a `<transpose>` as well), plus a second TAB staff with `<staff-details>`: `<staff-tuning line>` from the lowest string, `<technical><string>` from the highest, and the capo.
- **[Claude] not written yet:** articulations, ornaments, slurs and hairpins, graces, tremolo, Guitar Pro effects, fingering and fretboard diagrams (the stored score keeps them all). Import of MusicXML (plan §11) isn't built.
- **Tests: 540** (+4 MusicXmlTest, reading the output back with the JVM's XML parser: every voice of every bar adds up; the guitar part's clef, sounding pitches, staff-tuning order and string/fret numbering; ties, a triplet, 2+2+3/8, D dorian, chord symbols Dm7 / E7♯9 (♯9 as a degree) / C/E, lyric syllables; the road-map repeats and endings; every score example in the Course exports and adds up).
- **[measure] not done:** opening the file in MuseScore 4, Guitar Pro 8 and Sibelius/Dorico; Christopher's FL Studio doesn't read MusicXML (that's the FL Studio package, later in M7).
- **Unverified on device:** all of it, including the save picker. Christopher still has not seen notation drawn (0.5.2–0.7.0).

## M6 close-out (24 Oct 2026, build 0.6.21): Theory and Pitch Trainer code-complete
- **Done-when (plan §13), source and test evidence:** every catalog chord (41) and scale (55) spells on all 35 roots (CatalogTest); every Theory page has its interactive view and audio: Reference intervals, scales, chords, named progressions (27) and guitar tunings (45), and the Course's 123 pages (CourseTest: every sound and check renders on every root); every plan §9 Pitch Trainer area has drills (56, EarTest). Hand-written FUNCTION/WHERE rows on every chord and scale; NAMING rows where sources disagree; IN YOURS on chords, scales, intervals and progressions.
- **Not done-when until device-verified:** nothing in M6 has run on the phone. Walkthrough: `docs/walkthroughs/M6.md`, "Start here" first.
- **Unverified on device (all of M6):** notation drawing of every kind (staves, clefs, ledger lines, accidentals, key and time signatures, repeats and voltas, tab under notation, percussion staves) — never seen since 0.5.2; 𝄫/𝄪 in Bravura Text and in chips; the piano sound's level against the click, drone and pure tones; every sung/played drill's mic open-and-close; ANSWER ▾ on guitar and piano; the Course's KEY ▾, CHECK BY EAR, ‹ › paging and LEVEL ▾; IN YOURS speed with many scraps (it reads every scrap each time a page opens); the two-step TUNING ▾ menu; the circle of fifths' taps.
- **[Claude] gaps still open:** an Ear link from a Course page switches Learn to Ear (no way back to the page except ‹); Course check answers aren't saved to results; the circle's minor labels read "am"; SATB ranges are textbook ones, not quoted from Open Music Theory; every hand-written row and Course text is drafted for Christopher to check.
- **Questions for Christopher before more Learn work:** the Course pass (save check answers to results? the C4 mockup's "In your material 3 ›" count on lesson pages?).
- **M7 (plan §13: exports and release) needs from Christopher first:** (1) the GitHub repo URL — there's still no git history for the project; (2) the Exercises content (plan: "contents chosen by Christopher at the end"); (3) FL Studio on hand to test the FL Studio package import (done-when: "Import an exported project into FL Studio with everything in place"); (4) Play Console and signing: who holds the upload key (never committed); (5) the "Bass drop A" tuning notes (4- or 5-string), still waiting since M5.

## M6 part 22 — build 0.6.21 (24 Oct 2026): voicing-spread drill; naming disputes on chord pages
- **dist/ cleared** (Christopher: "Clear the unneeded versions"): 73 old builds deleted, only the latest three zips kept from now on. C: went from 0 bytes to ~6 GB free.
- **Ear › Chords › Voicing spread** (plan §9 Chords: "voicing spread", the one §9 item without a drill): a maj7, 7 or m7 with the root in the bass, voiced close, drop 2 (1 5 7 3), drop 3 (1 7 3 5) or spread (1 5 10 14); answered by name; ▶ YOURS plays your answer's voicing of the same chord. [Claude] the four voicings and root-in-bass for all of them, so the bass note doesn't give the answer away.
- **NAMING rows for the disputed chord symbols** (plan §8 "naming disputes between sources"): ° (triad in pop, dim7 in iReal/jazz), C+ / C7+, C2 (sus2 or add9), Cmaj on its own, half-diminished spellings, C7♯5 vs C7(♭13), 7sus4 (never sus7, 9sus/13sus), add9. Stored in ASCII like the rest of the catalog.
- **Audit of plan §9 against Ear:** every area now has drills (56). Answering on the piano or fretboard covers intervals, chords, notes, octaves, degrees, melody, bass and your phrases; the rest are by name.
- **Tests: 536**: EarTest checks the voicing drill (a root-position seventh underneath, root lowest, notes rising, ▶ YOURS keeps the same chord). The catalog's ASCII test caught my first draft of the naming notes using ♭ ♯ ° Δ ø; rewritten.
- **Unverified on device:** all of it. Christopher still has not seen notation drawn (0.5.2–0.6.21).

## M6 part 21 — build 0.6.20 (24 Oct 2026): hand-written rows on every Reference page; progression pages show their chords
- **Every catalog chord (41) and scale (55) now has its hand-written FUNCTION row** (most with WHERE), and every simple major, minor and perfect interval (plus A2, A4, d5, A5, d7 and the compound tensions ♭9 to 13) has a FUNCTION row beside the old WHERE memory aids (plan §8 "with hand-written commentary"). `core/learn/Commentary.kt`. [Claude] drafted, for Christopher to check like the rest.
- **Progression pages show the chosen chord** on SHOW ON ▾ (guitar shapes, bass, piano, staff): tap a chord in the row to select it, ▶ plays it, OPEN › goes to its chord page (plan §8's interactive view on every page).
- **Tests: 536** (+2 CommentaryTest: every chord and scale has a row and no row names a missing id; every simple interval has one; and the relations the rows state hold: suspended pentatonic = major pentatonic from its 2nd, Iwato = In from its 4th note, Hungarian minor = 4th mode of the double harmonic, Persian and the Neapolitan scales as stated, the augmented scale's two triads, Messiaen modes' note counts and transpositions (4, 6, 6, 6, 6) and tritone symmetry, G7♭5 = D♭7♭5 = the French sixth, So What = Em11).
- Caught while writing: a madd9 line placed the 9th "a half step from the minor 3rd an octave down"; it's a half step below the 3rd. Fixed.
- **Disk:** C: had 0 bytes free at the 0.6.19 zip (now ~870 MB); `dist/` holds 6 GB of old zips. Waiting on Christopher before deleting any.
- **Unverified on device:** all of it. Christopher still has not seen notation drawn (0.5.2–0.6.20).

## M6 part 20 — build 0.6.19 (24 Oct 2026): named progressions in the Reference
- **Reference › Named progressions** (plan §8 "every … named progression gets a page"). `core/learn/Progressions.kt`: 27 progressions in five families, opening on family rows (Christopher's "not a giant list"): pop and rock (I–IV–V–I, I–V–vi–IV, I–vi–IV–V, royal road); modal and minor (I–♭VII–IV–I, i–VI–VII–i, Andalusian, minor line cliché); jazz (ii–V–I major and minor, I–vi–ii–V, iii–vi–ii–V–I, tritone sub, backdoor, ragtime, Coltrane changes); blues (12-bar, quick change, minor blues); classical and cadences (V7–I, IV–I, iv–I, V7–vi, IV–V, Pachelbel, the diatonic circle in major and minor).
- A page on any of the 21 roots (ROOT ▾): each chord in your chord style over its Roman numeral (tap one to open its chord page); the bars in 4/4; ▶ PLAY IT with ▶ VS ▾ every other progression in the same key; a hand-written WHERE row; IN YOURS. A key that would need a double sharp or flat is respelled (tritone sub in F♭ → E) and says so.
- **IN YOURS for progressions:** the chords in order in your charts, lyrics, scores and analysed takes, in any key, matched on root movement and each chord's third (Em7 A7 Dmaj7 and Em A D both count as ii–V–I), repeats merged. The line cliché (one root) is matched on its exact chords.
- **[Claude]** The list and its families are mine; the WHERE text is original. Minor-key numerals follow the app's classical style (Andalusian shows i VII VI V); the ♭VII names are kept as aliases and in the text.
- **Tests: 534** (+5 ProgressionsTest: well formed, families with notes; chords spelled as written in C, A and D (Coltrane Dm7 E♭7 A♭maj7 B7 Emaj7 G7 Cmaj7, Andalusian Am G F E, Pachelbel in D, minor blues bar 9 A♭7); no double accidental on any of the 21 roots; the sound has the root in the bass and only chord tones above, and every chord has a numeral; the 12-bar is 12 bars; IN YOURS finds ii–V–I and I–V–vi–IV in a chart in D and the line cliché only on its exact chords). One failure on the way was my test's own wrong expectation (i–♭VI–♭VII–i is C A♭ B♭ C).
- **Unverified on device:** all of it. Christopher still has not seen notation drawn (0.5.2–0.6.19).

## M6 part 19 — build 0.6.18 (24 Oct 2026): guitar tunings organised by family
- Christopher, 24 Oct 2026: the tunings stay **Reference only** (tab editor and tuner keep the plan §6.4 presets), and "organize them, not just a giant list".
- **Reference › Guitar tunings** now opens on nine family rows (count, what the family has in common, the first few names): standard and lowered · drop · open major · open minor (cross-note) · modal and suspended · chord tunings · regular · other · seven and eight strings. A family opens its tunings; ‹ goes back to the families.
- **TUNING ▾** on every neck (Reference pages and the Course's TRY) is two steps for guitar: pick a family (✓ marks the one holding your current tuning), then the tuning; "‹ All families" goes back. Bass keeps its five. Note names in the menu use your note-name setting with ♯/♭.
- Tests: 529 (TuningReferenceTest also checks every family has a note and every tuning sits in one family).
- **Unverified on device:** all of it. Christopher still has not seen notation drawn (0.5.2–0.6.18).

## M6 part 18 — build 0.6.17 (24 Oct 2026): Guitar tunings in the Reference; Messiaen modes 3–7
- **Reference › Guitar tunings** (Christopher, 24 Oct 2026: "References needs a list of all guitar tunings and open tunings"). `core/learn/TuningReference.kt`: 45 tunings in nine families: standard and lowered (E standard to B standard), drop (D, D♭, C, B, A, double drop D), open major (D, E, G, A, C, B, F, Dobro G), open minor / cross-note (Dm, Em, Gm, Am, Cm), modal and suspended (DADGAD, DADEAD, CGCGCD, DGDGCD, Orkney), chord tunings (C6, D6, G6, Dmaj7, Dadd9, Dm7), regular (all fourths, major thirds, all fifths, new standard), other (Nashville high-strung), seven and eight strings. Each string is written with its octave.
- A tuning page shows each string (number, note, Hz, semitones from standard, the step up to the next string), how many strings are retuned, the chord(s) the open strings make, ▶ OPEN STRINGS with ▶ VS ▾ against every other tuning with the same string count, a note, and the neck with the open strings' notes marked. **USE FOR THE REFERENCE NECK** makes it the guitar tuning every Reference and Course neck draws on; Reference's TUNING ▾ now lists all 45. Search finds them by name or notes (DADGAD, "D A D G").
- **[Claude]** The list is the common tunings in each family of Wikipedia's "List of guitar tunings", not its 150+ one-off variants. The octaves are mine where the page doesn't give them. The tab editor's and tuner's tuning menus still list only the plan §6.4 presets: say if you want all 45 there too.
- **Messiaen modes 3–7 in the scale catalog** (plan §8), with Reference pages generated like every scale; whole-tone and half-whole also answer to "Messiaen mode 1/2". Stored with one spelling and a NAMING note that Messiaen defined them as note sets.
- **Tests: 529** (+5 TuningReferenceTest: unique ids, the app's own tunings are the same objects; every open, cross-note, modal and chord tuning rings the chord it's named for; lowered and drop tunings move exactly the strings their names say; regular tunings repeat their interval, string steps and Hz; the Messiaen sets match the Course's). CatalogTest's fixture got the five new sets; all 35 roots spell.
- **Unverified on device:** all of it. Christopher still has not seen notation drawn (0.5.2–0.6.17).

## M6 part 17 — build 0.6.16 (24 Oct 2026): Course · Instruments written; every Course topic now has pages
- **Instruments: 9 topics, 16 pages** (`core/learn/CourseInstruments.kt`, text original): The piano keyboard (88 keys A0–C8, 52 white / 36 black, computed); Strings and frets (standard tuning table with the interval to each next string); Octave shapes (+2 from strings 6/5, +3 from 4/3; every place of middle C); The bass neck (root–fifth–octave on A and D, bass clef with tab); Alternate tunings (every six-string tuning in Tunings with the strings it changes); What a capo does (what each open shape sounds at capo 0–7); CAGED (C major in the five shapes C A G E D, drawn as notation + tab); Barre chords (root names on strings 6 and 5, frets 0–12); Pentatonic positions (five A minor pentatonic boxes, frets per string, computed); Three notes per string (G major, seven positions, the run drawn with tab); Drop 2 and drop 3 (Cmaj7's four close inversions with their drop 2 on strings 4–1 and drop 3 on 6 4 3 2, frets computed); Shells, Rootless voicings, Spread voicings (ii–V–I in C on the grand staff); Ranges and registers (SATB, tenor in treble 8vb); Transposing for a singer (which shifts fit a range, computed).
- **Checks:** which tuning the open strings are in; close / drop 2 / drop 3; shell 1–3 or 1–7.
- **All nine categories are written: 123 pages.** CourseTest now asserts every topic in every category has pages; the catalog's stub helper is gone.
- **Tests: 524** (+6 CourseInstrumentsTest, +1 CourseTest assertion set): keyboard counts; octave shapes and middle C's places; the bass line's frets; each tuning's changed strings, open G/D ring their chords, the tuning check plays each tuning's open strings; all five CAGED shapes are C E G with C in the bass and climb in order; pentatonic boxes join end to end, stay in the scale and span ≤ 4 frets, box 5 ends at the 17th; three-notes-per-string positions; drop 2 = G C E B (5 5 5 7), root-bass drop 3 = C B E G (8 x 9 9 8 x), every drop voicing's frets play it and lie within 4 frets; shells, rootless and spread voicings have the tones named and the rootless voices move by step; the singer's shifts are −5, −6, −7.
- Caught: my text said three-notes-per-string positions stretch up to five frets; the position from C runs 8th–14th (six). The page now quotes the computed figure. A transposing sentence said "three semitones too high"; it's five. Related links to pages that don't exist were fixed before the build.
- **[Claude]** SATB ranges are the usual part-writing ones (S C4–G5, A G3–D5, T C3–G4, B E2–C4); textbooks differ by a step and the page says so. I couldn't open Open Music Theory's own range page (403), so these are not quoted from it.
- **Unverified on device:** all of it. Christopher still has not seen notation drawn (0.5.2–0.6.16).

## M6 part 16 — build 0.6.15 (24 Oct 2026): Course · Reading written
- **Reading: 8 topics, 10 pages** (`core/learn/CourseReading.kt`, text original): The staff and clefs (treble, bass, alto, tenor, grand staff, guitar an octave up); Lines and spaces (all four clefs, computed); Ledger lines and octave signs; How bars add up; Key and time signatures on the staff (D major in 3/4); Accidentals in the bar (courtesy accidentals); Reading tab (E minor pentatonic run and an open G chord, tab under notation); Chord charts; Nashville numbers (computed, with the chart in C and G); Repeats, endings and road maps (|: :|, 1st/2nd endings, D.C. al Fine; play order from ScoreTime.order).
- **New See: real scores.** CourseSee.Score draws each part with the app's notation view, and tab under a fretted part (TabPreview, a read-only wrapper of the tab editor's canvas); ▶ plays it through ScorePlayback.
- **Tests: 517** (+5 CourseReadingTest: each clef's lines and spaces, the clefs' own notes; every example score's bars fill the meter and play; every tab fret plays its written pitch; the chart in Nashville numbers (1 6m 4 5 1/3 4 5⁷ 1 ♭7 4/6 5 1); the road map plays 1 2 3 1 2 4 5 1 2 and the page says so). One run failed on my own miscount (13 tab notes, not 12); fixed in the test.
- Caught before the build: Nashville printed G7 as "57" (now 5⁷); the road-map text promised a table it didn't have (now quotes the computed order); segno/coda symbols removed from prose (font support unknown on the phone).
- **Unverified on device:** all of it; this chapter is the most notation-heavy yet (staves in four clefs, ledger lines, accidentals, repeats and voltas, tab). Christopher still has not seen notation drawn (0.5.2–0.6.15).

## M6 part 15 — build 0.6.14 (24 Oct 2026): Course · Melody & songs written
- **Melody & songs: 7 topics, 9 pages** (`core/learn/CourseMelody.kt`, text original): Steps, leaps and contour; Motif and phrase (repetition, sequence, inversion, question and answer); Chord tones, passing and neighbour tones; Suspensions and other non-chord tones (4–3, appoggiatura, escape, anticipation, pedal); Lines over changes (targets on downbeats, chromatic approach, enclosure, over Dm7–G7–Cmaj7 landing F, B, E); Motion between voices (contrary, oblique, similar, parallel; parallel fifths/octaves as a style rule); First-species counterpoint (Fux 1725; C D F E D C against G B A C B C); Song form (verse–chorus, 32-bar AABA); The twelve-bar blues (quick change, AAB lyric, minor and jazz blues).
- The tables are computed by the classifiers the pages describe: `classify` (chord tone, passing, neighbour, suspension, appoggiatura, escape, anticipation) and `motion`/`parallelPerfect`.
- **Tests: 512** (+5 CourseMelodyTest: every non-chord tone kind; motion types and parallel perfects; the species example keeps every rule the page states (consonances, perfect ends, no parallels, leading tone, mostly contrary); AABA = 32 and the twelve-bar layout; checks play what they say). One run failed on my own wrong expectation (the parallel-fifths example is P5 throughout); fixed in the test.
- Caught before the build: two Ear links pointed at drill ids that don't exist (now dc-mel-4, dc-passing; CourseTest would also have failed on them), and the ii–V–I line didn't land on the guide tones the text names (rewritten).
- **Unverified on device:** all of it. Christopher still has not seen notation drawn (0.5.2–0.6.14).

## M6 part 14 — build 0.6.13 (24 Oct 2026): Course · Rhythm & time written
- **Rhythm & time: 8 topics, 13 pages** (`core/learn/CourseRhythm.kt`, text original): Pulse and tempo (ms per beat table, half-time); Note values; Rests; Time signatures (strong and weak beats, C and ¢); Simple and compound (6/8 vs 3/4 accents); Dots and ties; Syncopation (ti–ta–ti, pushes, 3+3+2); Swing and feel (≈2:1, shuffle, laid back/on top); Tuplets (3:2, 5:4, 6:4, quarter triplets); Odd meters (5/4 "Take Five", 7/8 groupings, 2+2+2+3/8); Changing meters; Polyrhythm (grids 3:2, 4:3, 5:4); Metric modulation (120 → 180 / 80 / 60 / 240).
- **New See: written rhythms.** CourseSee.Rhythm draws each bar on a percussion staff (NotationCanvas) with a ▶ that plays it after a count-in on the drum kit. Checks and sounds use EarRhythm.drums/countIn; the 6/8-vs-3/4 check accents the same six eighths two ways; polyrhythms play high and low piano notes.
- **Tests: 507** (+6 CourseRhythmTest: every written bar fills its meter exactly; 6/8 = two dotted-quarter beats, 7/8 = 2+2+3, 6/8 and 3/4 same length; swung offbeats at 2/3 of the beat; polyrhythms meet only on note one; metric-modulation tempos; every rhythm check plays what it says). CourseTest now counts drum-only sounds as audio (the first run failed on that, my test's assumption).
- Caught before the build: a quintuplet bar that only added up to three beats (fixed; the new test would have caught it).
- **Unverified on device:** all of it. The rhythm staves are notation drawing: Christopher still has not seen notation drawn (0.5.2–0.6.13).

## M6 part 13 — build 0.6.12 (24 Oct 2026): Course · Harmony written
- **Harmony: 9 topics, 17 pages** (`core/learn/CourseHarmony.kt`, text original): Chords of a major key and of a minor key (tables from ScalePages with numerals from ChordDisplay CLASSICAL); Tonic, predominant, dominant; Cadences (the Ear's phrases); Secondary dominants; Chains of dominants (E7 A7 D7 G7 C, rhythm-changes bridge); Borrowed chords (iv, ♭III, ♭VI, ♭VII, ii°; Picardy third); Pivot-chord modulation; Direct and chromatic-mediant moves; The Neapolitan; Augmented sixths (It/Fr/Ger in C, Ger = A♭7 = tritone sub of V/V); ii–V–I and guide tones; Tritone substitution, backdoor and chord-scales; Coltrane changes (ii–V–I in C through A♭ and E); Negative harmony (n → 7 − n in C, G7 → Fm6 = Dø7; Riemann, Levy, Collier); Neo-Riemannian P R L (PL chain C A♭ E C); Pitch-class sets (prime forms by Rahn's method, interval vectors).
- Progressions are degree tokens voiced by EarHarmony.voice; cadences, secondary dominants, borrowed chords, augmented sixths, substitutes and modulations reuse the Ear's phrases exactly.
- **Tests: 501** (+7 CourseHarmonyTest: the diatonic chords and numerals of C major/minor/harmonic minor; every progression's spelling incl. It/Fr/Ger+6 and the Coltrane chords; N6 and Ger+6 pitch classes; negative harmony map and G7 → Fm6 = Dø7; P/R/L keep two notes and are involutions on all 24 triads; prime forms (037) (036) (048) (0258) (0158) (0358) (0369) and vectors; every harmony check plays what it says). build-verified; nothing device-verified.
- Caught before the build: the neo-Riemannian sounds put the bass on the wrong note off C (fixed), and a minor-key example (Am–G–F–E ends on a major V) was wrong for its point (now Am–G–F–G against Am–Dm–E–Am).
- **Unverified on device:** all of it. Christopher still has not seen notation drawn (0.5.2–0.6.12).

## M6 part 12 — build 0.6.11 (23 Oct 2026): Course · Chords written
- **Chords: 10 topics, 15 pages** (`core/learn/CourseChords.kt`, text original): Building a triad (power chords); The four triads (G♯ B♯ D♯); Inversions (figured bass 5/3 6 6/4, 7 6/5 4/3 4/2; slash names; a stepwise bass line); The five main seventh chords; More seventh chords (m(maj7), maj7♯5, 7♯5, 7♭5, 7sus4); Suspended chords; Added-note chords (add9 vs 9/maj9; C2 read as add9 in pop, sus2 in jazz/iReal); Sixth chords (C6 = Am7, Cm6 = Aø7; Barry Harris); Ninths, elevenths, thirteenths (implied stack); What gets left out (5th, then root; 3rd and 7th stay; the 11 chord's exception; no natural 11 over a major 3rd); Altered dominants (C7♯9 D♯, E7♯9 F𝄪, alt); Slash chords (F/G, D/C, B♭/C); Polychords (D over C, Petrushka); Quartal and cluster voicings (the So What chord); Upper structures (II ♭III ♯IV ♭VI VI over C7, tensions computed).
- Tables come from ChordPages (spellings, inversions and figures) and the catalog (formulas, omit orders).
- **Tests: 494** (+6 CourseChordsTest: every quoted spelling; every "same notes" claim (C6/Am7, Cm6/Aø7, Csus2/Gsus4, aug and °7 symmetry, 7♭5 at the tritone, D/C = D7, C11 ⊇ B♭/C); implied stacks and omit orders (guide tones kept in every chord but 11); inversion figures; upper-structure tensions; every chord check plays the chord its answer names). build-verified; nothing device-verified.
- My first run had one failing test: it was my test's mistake (the 11 chord drops its 3rd first, which the page says), fixed in the test.
- **Unverified on device:** all of it. Christopher still has not seen notation drawn (0.5.2–0.6.11).

## M6 part 11 — build 0.6.10 (23 Oct 2026): Course · Scales & keys written
- **Scales & keys: the mockup's 9 topics, 18 pages** (`core/learn/CourseScales.kt`, text original): The major scale (tetrachords); Scale degrees (names, tonic vs leading tone after a cadence); Sharps and flats in order (the two shortcuts); Every key signature (all 15, enharmonic pairs, relative minors); The circle of fifths; Close and distant keys (shared notes by steps); Natural, harmonic and melodic minor (classical vs jazz melodic); Pentatonic scales (the black keys); The blues scale (blue notes, major blues); One scale, seven modes (relative vs parallel, over a drone); Bright to dark (one note per step, characteristic notes); Modes of melodic minor (Lydian dominant, Locrian ♯2, the altered scale read as G7 tensions); Modes of harmonic minor and major (Phrygian dominant; harmonic-major names disputed); Whole-tone and diminished (2, 3, 3 and 4 transpositions); Messiaen's modes (pitch-class sets, 2 3 4 6 6 6 6); Scales from other traditions (labelled approximations).
- **Two new See drawings:** KeyStaff (the page key's scale on a treble staff with its key signature, follows KEY ▾) and the **circle of fifths** wheel (majors outside with their signature counts, relative minors inside, the key lit; tap a key to hear its scale and move TRY to it). StaffView now takes a key.
- Mode checks play over a drone (EarAudio.drone). Tables come from ScalePages, Notation.fifths and the catalog.
- **Tests: 488** (+7 CourseScalesTest: every one of the 15 signatures matches its spelled scale; both shortcuts on every key; relative minors and enharmonic pairs; the circle steps by fifths and its signature counts; shared notes 7 6 5 4 3 2 2; quoted spellings (A harmonic minor, A blues, G altered, G Lydian dominant, B Locrian ♯2, E Phrygian dominant); bright-to-dark one note per step; symmetric/Messiaen transposition counts; checks play what they say). build-verified; nothing device-verified.
- **[Claude]:** the circle's minor labels are lowercase + m ("am", "f♯m"); tapping the inner ring plays that minor and moves TRY to its relative major.
- **Unverified on device:** all of it; check the wheel's tap targets and that the key-signature staff draws (Christopher still has not seen notation drawn, 0.5.2–0.6.10).

## M6 part 10 — build 0.6.9 (23 Oct 2026): Course · Intervals written
- **Intervals: 5 topics, 11 pages** (`core/learn/CourseIntervals.kt`, text original): Counting an interval; Melodic and harmonic; Perfect intervals (the one A4/d5 in C major); Major and minor; Every simple interval (table of all the Reference's simple intervals on C); Inversion (adds to 9 and 12); Compound intervals (9 to 15, and chord-symbol 9/11/13); Consonance and dissonance (classical sorting, the fourth's double role, style differences); Tension and resolution (B–F → C–E inward, F–B → E–C outward, sus4 → 3); The tritone (six tritones, A4/d5 by spelling); The tritone in chords (G7 and D♭7 share B/C♭–F, Bø7, C°7's two tritones).
- Tables are generated from the music core (IntervalPages, Interval.between), so spellings like C–D♯ (A2), C–B𝄫 (d7) come from tested code. Checks: step/leap, up/down, P4/P5, M3/m3, m2/M3/P5/P8, M3/m6, M2/M9, consonant/dissonant, resolved/hanging, P4/tritone/P5, G7 or D♭7 into C.
- `CourseKit` (shared page-writing helpers) now under both chapters.
- **Tests: 481** (+6 CourseIntervalsTest: C major's fourths and fifths; quoted sizes and spellings; inversions; compound = simple + octave; G7/D♭7/Bø7/C°7/Csus4 notes and their tritones; every interval check plays the size its answer names). build-verified; nothing device-verified.
- **Unverified on device:** all of it. Christopher still has not seen notation drawn (0.5.2–0.6.9).

## M6 part 9 — build 0.6.8 (23 Oct 2026): the Course, with Sound & pitch written
- **Learn opens on the Course** (LEARN ▾ Course · Ear · Reference). `core/learn/Course.kt`: categories → topics → pages; each page is Explain → See (a table) → Hear (▶ buttons) → Try (a Reference interval, scale or chord drawn by SHOW ON ▾ on your guitar, bass, piano or staff, KEY ▾ any start note) → Check by ear (▶ PLAY, NEW ›, tap an answer, then each answer plays to compare; a count for this visit) → Related (Reference pages, Ear drills, other pages) → IN YOURS where it applies. ⋯ lists previous/next and the related links; ‹ › buttons at the foot. Levels ● to ●●●●● are shown as a gauge; LEVEL ▾ filters (All, ● only, ●● only, ●●● only, ●●●● and up); nothing is locked.
- **The nine categories** (C4): Sound & pitch 6 topics, Rhythm & time 8, Intervals 5, Scales & keys 9, Chords 10, Harmony 9, Melody & songs 7, Reading 8, Instruments 9 (71 topics). Unwritten topics are listed as TO BE WRITTEN. [Claude] the topic lists and levels outside Scales & keys (which is the mockup's), following plan §8's scope in Beato's order.
- **Sound & pitch written: 6 topics, 14 pages** (text original): Pitch is frequency; Octaves; The musical alphabet; Octave numbers and MIDI (incl. Ableton/Logic C3, FL Studio C5 for MIDI 60); One key, two names; The half step; The whole step; Timbre; The harmonic series; Beats; Tuning with harmonics (the 1.96 ¢ error of tuning a guitar by 5th/7th-fret harmonics); Cents; Equal temperament (table of equal vs pure intervals); Concert pitch (440, 442, 415, 432).
- **Every number in the text comes from `Acoustics`** (hz, cents, harmonic, Pythagorean comma), so the prose can't drift from the maths; formatted with Locale.ROOT.
- `Tone` gained partials and gain (defaults unchanged, so the Ear drills sound the same); EarTone skips partials above Nyquist. `ShowRef` in Reference.kt draws a Reference item for TRY.
- **Tests: 475** (+9 CourseTest: categories and counts; every link/try/yours target exists; every sound and check answer renders for every root; checks play what their answers say; quoted frequencies, cents, harmonic spellings (C2 C3 G3 C4 E4 G4 B♭4 C5), comma, ET errors, the guitar-harmonic tuning error, enharmonic spellings (B♯3 = MIDI 60, C♭4 = 59, D major, C°7 B𝄫, E7♯9 F𝄪); level filter). build-verified; nothing device-verified.
- **[Claude] gaps:** an Ear-drill link switches Learn to Ear (back returns to Ear's home; LEARN ▾ Course returns to the page); See is tables only (the circle-of-fifths wheel and other drawings come with their chapters); Check answers aren't saved to the Ear results; the JND "about 5 cents" and orchestras "around 442 Hz" are stated as typical, not exact.
- **Unverified on device:** everything above, plus sine/partial tone levels (ear-verify that ALL PARTIALS and BUILT UP don't clip), the 8 s nearly-still 329.63 + 330 Hz pair. Christopher still has not seen notation drawn (0.5.2–0.6.8).
## M6 part 8 — build 0.6.7 (23 Oct 2026): Ear on your own music; Ear complete
- **Learn › Ear › YOUR MUSIC** (`core/learn/YourEar.kt`, plan §9 "Your own music"). It reads your material each time the chip opens and lists only the drills it has enough for; for the rest it says what each needs. It never suggests anything.
  - **Chords of your progressions:** every chord symbol in your chord charts, lyric sheets, scores and analysed takes that names a catalog chord (slash chords and polychords left out). A question plays one of them on its own root; answer the quality; the answer says where it came from ("Low Tide · bar 3 · E7#9"). Needs two or more kinds of chord.
  - **The key of your recordings:** the first 12 s of a recording with a key (its starred take, else the last). ▶ YOURS plays a cadence in the key you picked. Needs recordings in two or more keys.
  - **Phrases from your takes:** single-line runs of 4–6 notes from takes turned into notes (Basic Pitch), each played from the take itself; play it back on the piano or your guitar or bass, note by note (this is the plan's "tab it by ear"). [Claude] A run: notes that don't overlap, gaps under 0.6 s, within a 12th; up to six per take.
  - Your-music drills aren't in mixed practice.
- With this, every Ear area in plan §9 has drills (55 drills, 10 chapters). The plan's "answer on your fretboard or the piano as well as by name" is there for intervals, chords, notes, octaves, degrees, melody, bass and your phrases; the rest are by name.
- Tests: YourEarTest 3 (your chords and where, phrase runs and clips, drills only when there's enough, a question's source in its explanation, a key clip and the other key as a cadence, phrase checking). **Total 466, all pass.** Zip `dist/Apophenia-0.6.7-debug.zip` (151 MiB).
- **Unverified on device:** all of it; clips assume takes are at the engine's rate (imports are resampled to it on import).

## M6 part 7 — build 0.6.6 (23 Oct 2026): Ear, rhythm; tuplet numbers in notation
- **Rhythm chapter** (`core/learn/EarRhythm.kt`):
  - **Rhythm dictation** in simple meters (2/4, 3/4, 4/4), with triplets and other tuplets (eighth triplets, quarter-eighth triplets, quintuplet and sextuplet sixteenths), and in odd and additive meters (5/8 as 3+2 and 2+3, 7/8 as 2+2+3 and 3+2+2, 9/8 as 2+2+2+3). A bar of clicks, then the bar on the snare with the beats on the hi-hat; pick it from three bars **written out on a percussion staff** (A, B, C). Listen mode: tap a bar to hear it.
  - **Straight or swung:** two bars of eighths, even or 2:1.
  - **Tap it back:** a bar of clicks, the rhythm, then a bar of clicks while you tap it on a pad. Taps are timed against the player's own position (`AudioController.playerFrame`). [Claude] The average difference is your constant delay (the phone's output and touch latency plus your own) and is shown and taken out; each note must then be within ±60 ms, with the right number of taps. Kept out of mixed practice.
  - [Claude] Bars are built from common one-beat cells (or two- and three-eighth groups in odd meters), not from songs. Tempos: 80, tuplets 72, odd meters 100 (quarter note).
- **Notation:** tuplet numbers are now drawn (bold italic over each run of tuplet notes in a beat, with a bracket when the run isn't one beam). This was missing from the score editor since 0.5.2; it shows there too.
- Fixed before release: the question audio cache key lacked the rhythm, so every rhythm question would have replayed the first one's file.
- Tests: EarTest 17 (+ 600 bars fill their meters exactly with three distinct options and the right one at its letter, every tuplet option has a tuplet; swing timing; tap-back grading with the delay removed, a late note failing, a missing tap failing; tap times in the third bar). **Total 463, all pass.** Zip `dist/Apophenia-0.6.6-debug.zip` (151 MiB). Ear has 52 drills in 9 chapters.
- **Unverified on device:** all of it, and this is the first Ear drill that draws notation (the drawing itself is unverified since 0.5.2); whether the 60 ms window feels fair on the S22.

## M6 part 6 — build 0.6.5 (23 Oct 2026): Ear, dictation
- **Dictation chapter** (`core/learn/EarDictation.kt`):
  - **Melody** (four notes, six notes, minor key): the key's tonic triad, then the phrase; play it back note by note on the piano or your guitar (ANSWER ▾), the first note given, ⌫ / CLEAR / ▶ MINE / CHECK. Each note is marked; a wrong one shows what it was. [Claude] Phrases are generated (start on 1, 3 or 5; mostly steps, leaps up to a 5th; end on the tonic); right means every pitch class in order, octave not marked.
  - **Bass line** (major, minor): four chords (I vi IV V, I IV V I, I V vi IV, I iii IV V, I ii V I, I IV vi V; in minor i iv v i, i VI III VII, i VII VI v, i iv VII III, all natural minor), the bass in the low register; play the roots back on the piano or your bass tuning.
  - **Chord tones and passing tones:** a held triad and four notes over it; which one isn't in the chord (a step between two chord tones a third apart).
  - **How fast:** two bars of a bass-and-chord groove; pick the BPM from 60, 72, 84, 96, 108, 120, 132, 144, 160, 176.
- Moved to their own build (0.6.6): rhythm dictation (tuplets, odd and additive meters, swing) and tap-back accuracy, because they need the rhythm drawn as notation and the output latency taken into account.
- Fixed before release: the audio cache key for a question didn't include its tempo or chords, so two tempo questions on the same root would have replayed the first one's file.
- Tests: EarTest 15 (melody shape over 300 phrases, scale steps in major and minor, passing tones over 300 questions, note-by-note checking, the explanation spelled in the key). **Total 461, all pass.** Zip `dist/Apophenia-0.6.5-debug.zip` (151 MiB). Ear has 47 drills in 8 chapters.
- **Unverified on device:** all of it; that the bass keyboard (E1–C4) is easy to hit on the phone.

## M6 part 5 — build 0.6.4 (23 Oct 2026): Ear, harmony and tuning
- **Harmony** (`core/learn/EarHarmony.kt`; each chord written as degree tokens above the tonic, bass first, so explanations spell every note):
  - Chords in a major key (I ii iii IV V vi vii°) and a minor key (i ii° III iv v V VI VII), after the I – IV – V – I (or i – iv – V – i) cadence.
  - Cadences: authentic (I IV V I), plagal (I vi IV I), half (I vi IV V), deceptive (I IV V vi).
  - Secondary dominants: I, then V7/ii, V7/iii, V7/IV, V7/V or V7/vi, then the chord it points at.
  - Borrowed chords from the parallel minor: I – iv / ♭III / ♭VI / ♭VII / ii° – I.
  - Neapolitan and augmented sixths in minor: i – iv / N⁶ / It⁺⁶ / Fr⁺⁶ / Ger⁺⁶ – V – i (in C: N⁶ = D♭ F A♭ over F; Ger⁺⁶ = A♭ C E♭ F♯, resolving outward to G).
  - Tritone and backdoor substitutes: ii7 – V7 / ♭II7 / ♭VII7 – Imaj7 (♭II7 in C spelled D♭ F A♭ C♭).
  - Where it modulates: a cadence in a major key, then V7 – I – IV – V7 – I in the new key: to V, IV, vi (relative minor), ii, iii, or i (parallel minor).
  - Voicing [Claude]: bass two octaves under the tonic, the other tones close around the tonic.
- **Tuning and timbre:** equal or just (a major triad in plain tones: 5:4 and 3:2 against 400 and 700 ¢) · beating (two near-unison tones: none, 2 or 5 beats a second) · which harmonic (a low note, then its 2nd–8th harmonic, then both together; the 7th explained as about 31 ¢ flat of an equal ♭7).
- **Fixed:** Ear and Reference turned every "b" into ♭ in labels and explanations, so "bass" and "Bebop" would show as "♭ass" and "♭e♭op" (0.6.0–0.6.3; never seen on the phone). Accidentals now change only directly before a number (`accidentals`, `degreeText`). Caught while adding the tuning labels ("No beats").
- **Fixed before release:** the augmented sixths first left their own bass note out of the upper voices; a test caught it.
- Tests: EarTest 12 (+ harmony spellings in C and E♭, Ger⁺⁶ voicing and its resolution to G, the deceptive cadence, just/equal/harmonic/beat frequencies, the accidental rule). **Total 458, all pass.** Zip `dist/Apophenia-0.6.4-debug.zip` (151 MiB). Ear now has 40 drills in 7 chapters.
- **Unverified on device:** all of it; whether the 5-beat and just/equal differences are clear on the S22's speaker (use headphones).

## M6 part 4 — build 0.6.3 (23 Oct 2026): Ear, pitch, scale degrees, scales, singing
- **New chapters in Learn › Ear** (`core/learn/Ear.kt`), each drill with L / P / T as before:
  - **Pitch:** name the note after an A4 reference · with no reference (absolute pitch) · which octave (C2–B6) · higher or lower by 25, 10 or 5 cents (two pure tones, since the piano can't bend by cents) · **sing or play the note** (graded by the tuner).
  - **Intervals:** **sing the interval** above the note you hear (graded by the tuner).
  - **Scale degrees:** in a major key (1–7) · in a minor key (natural minor plus the raised 7) · chromatic (all twelve). SOUND ▾ sets the context: after a cadence (I – IV – V – I, or i – iv – V – i with the major V) or over a drone of the tonic.
  - **Scales:** modes of major · modes of melodic minor · modes of harmonic minor · symmetric scales (whole tone, both octatonics, augmented), each played up and down over a drone of its tonic · **characteristic notes** (Dorian 6, Phrygian ♭2, Lydian ♯4, Mixolydian ♭7, Aeolian ♭6, Locrian ♭5 against the tonic over a drone).
- **Sung drills:** ● LISTEN opens the mic through the tuner only when tapped, shows the note heard and its cents from the target, grades the median of 0.6 s of steady pitch, and closes the mic again (gives up after 6 s). [Claude] Any octave counts; right within ±30 ¢. ▶ TARGET plays the target after you've answered. They're left out of mixed practice.
- **Answering on the instrument** now also covers naming a note (any octave), the octave (exact) and scale degrees (the tonic is given). Cents, modes, characteristic notes and inversions are by name.
- **Sound:** questions mix the piano with pure tones and a drone (`EarTone`, a few harmonics with soft edges) into one cached render.
- Tests: EarTest 8 (every drill's questions, now all 29 drills; the minor cadence's iv and major V; sung grading in any octave within 30 ¢; cents tones exactly the asked distance apart; drone under modes; the tone render stays in range). **Total 454, all pass.** Zip `dist/Apophenia-0.6.3-debug.zip` (151 MiB).
- **Unverified on device:** all of it; the mic hearing the piano's own tail if you tap LISTEN while it still sounds (wait for it to stop); how the drone sounds on the speaker.

## M6 part 3 — build 0.6.2 (23 Oct 2026): Ear, intervals and chords
Looked at first: Perfect Ear (identification, interval comparison and singing drills; ascending, descending and harmonic chosen per exercise; custom drills by interval set, direction and range), plus the C4 'ear' screen.
- **Learn › Ear:** chips MIXED (the default, plan §9: mixed practice), INTERVALS, CHORDS. Each drill row has **L · P · T** (Listen, Practice, Test) and shows its best test score and number of sessions.
  - Intervals: seconds and thirds · fourths, fifths, tritone · sixths and sevenths (the confused pairs) · every simple interval · compound (m9 to M13, A11).
  - Chords: major/minor/dim/aug · major/sus2/sus4 · maj7/m7/7 · every seventh chord (8) · sixths and added notes · ninths and beyond (9, maj9, m9, 11, 13, 7♭9, 7♯9, 7alt) · triad inversions · seventh-chord inversions.
  - Mixed: a chapter's drills together, or everything; each question's buttons are its own drill's answers.
- **Drill screen:** the mode banner, MODE ▾, ▶ PLAY (auto-plays each new question), SOUND ▾ (interval direction up, down, together or mixed; chord block, arpeggio or both; random key on, or everything on C), ANSWER ▾ (by name, on the piano, on your guitar tuning).
  - Practice: pick an answer; the right one is outlined in copper, a wrong pick in red; the answer is explained with its notes on this question's root (Dm7: D F A C); ▶ RIGHT ONE and ▶ YOURS (your wrong answer on the same root, the A/B); NEXT ›. SESSION: right / asked, streak, best streak.
  - Test: 20 questions, no hearing yours, then the score; it is kept (schema v14 `ear_result`), AGAIN starts over.
  - Listen: tap any answer to hear it on this root; ▶ NEW ROOT.
  - On the piano or guitar: the first note (an interval's first-played note, a chord's bass) is given; you play the other note (exact pitch) or the rest of the chord (any octave or voicing), ▶ MINE hears it, CHECK marks it.
- **Weighting** (`core/learn/Ear.kt`): m6, M6, m7, M7 and the tritone come up twice as often (plan §9); each miss raises that answer's chance until you get it right; the same answer twice running is less likely.
- **Storage:** schema 14 adds `ear_result` (drill, mode, right, total, best streak, time) by AutoMigration 13→14; Practice sessions are kept when you leave them, Listen keeps nothing.
- Tests: EarTest 5 (every drill's questions play the right notes, the confused-interval weighting measured at about 2×, inversions and hearing another inversion, instrument answers, explanations) + data 2 (migration 13→14, best test score per drill). **Total 451, all pass.** Zip `dist/Apophenia-0.6.2-debug.zip` (151 MiB).
- [Claude] choices: drill list and grouping above; Test = 20 questions; answers on the instrument check an interval's exact octave but a chord's pitch classes only.
- **Unverified on device:** all of it, including how quickly each question's piano audio renders (first time per question, then cached).

## M6 part 2 — build 0.6.1 (23 Oct 2026): in your material, naming a chord, written rows
- **IN YOURS on every Reference page** (`core/learn/InYours.kt`; plan §8 "where it appears in your own material"). It lists places and says where; it suggests nothing.
  - Chord pages: every chord of that quality, on any root, in chord charts (bar), lyric sheets (line), tab and sheet music (part and bar), each project's own copy of a text (named by project), and the chords the analysis found in every take (take letter and time, at each change).
  - Scale pages: scraps whose key is that scale (any root), and scores whose key is.
  - Interval pages: melodic steps of that interval, up or down, between neighbouring notes of each part's first voice in tab and sheet music.
  - Hits on the page's root come first, then OTHER ROOTS; tapping one opens the scrap (a project copy opens in that project).
  - New read-only queries, no schema change: `ScrapDao.live`, `ProjectDao.ownBodies`, `AnalysisDao.bodies`.
- **Name a chord** (Reference home → Name a chord; `ChordNamer`): tap notes on two octaves of piano (C3–C5) or frets on your guitar tuning (one per string, tap again to mute). It lists every catalog chord with exactly those notes, then forms missing a tone the catalog lets voicings leave out ("NO 5"), then slash chords over a non-root bass, as Chordopia's and Oolimo's namers do. Tap a name to open its page. ▶ PLAY THEM strums the notes. [Claude] Tapped notes are named from the default table (D♭ E♭ F♯ A♭ B♭); the result opens its page in the chord's own spelling.
- **FUNCTION and WHERE rows** (`core/learn/Commentary.kt`), [Claude] drafted for Christopher to check: 24 chords, 19 scales, and for 12 intervals the usual song memory aids (Jaws, Happy Birthday, Smoke on the Water, When the Saints, Here Comes the Bride, Maria, Twinkle Twinkle, The Entertainer, My Bonnie, Somewhere, Take On Me, Over the Rainbow; descending Für Elise, Mary Had a Little Lamb, Hey Jude, Swing Low, The Flintstones). Song examples elsewhere are kept to widely documented ones ("So What" in D Dorian, "Purple Haze" for E7♯9).
- Tests: InYoursTest 4 (charts/lyrics/project copies with where, key matches, melodic intervals up and down, chord naming: exact first, C7 no 5, Am7 also C6/A, E7♯9 from four notes, C/E). **Total 444, all pass.** Zip `dist/Apophenia-0.6.1-debug.zip` (151 MiB).
- **Unverified on device:** all of it; that IN YOURS loads quickly with many scraps (it reads every scrap and analysis each time a page opens).

## M6 part 1 — build 0.6.0 (23 Oct 2026): the Reference, generated
**M6 plan (Claude's split, like M5):** 0.6.0 Reference pages for intervals, scales and chords · 0.6.1 "in your material" on every page, naming a chord from notes or fret dots, hand-written commentary rows (function, where it occurs) · 0.6.2–0.6.4 Ear (Listen / Practice / Test; intervals and chords first, then pitch, scale degrees, scales over a drone, then harmony, dictation, rhythm, tuning and timbre, your own music) · 0.6.5 on the Course (categories → topics → pages: Explain, See, Hear, Try, Check by ear, Related), chapter by chapter · then the catalog additions plan §8 lists (named progressions, Messiaen modes, quartal and cluster voicings, polychords, concept pages).
- **LEARN ▾** picks Course, Ear or Reference (C4). [Claude] It opens on the Reference until the Course exists; Course and Ear say they arrive in later M6 builds.
- **Reference home:** FIND (a chord symbol such as E7#9 opens that chord on E; "D dorian" opens Dorian on D; "m3" the interval; anything else matches names and symbols), then five categories: Intervals (52), Scales and modes (51), Chords (41), Drum grooves, Drum rudiments.
- **Every page** (generated from the catalogs, `core/learn/Reference.kt`): ROOT ▾ (21 roots; a root that would need triple accidentals is respelled and the page says so), NOTES (each note with its interval and semitones), SHOW ON ▾ (guitar, bass, piano, staff) with TUNING ▾ (remembered: "your tuning"), HEAR with ▶ and **▶ VS ▾** (this, then any other interval / scale / chord on the same root: the A/B comparison).
  - **Intervals:** unison to double octave in every usual quality; size in semitones and cents (the tritone named), the 5-limit just ratio with its cents against equal (with the other ratios sources give for M2, A4, d5, m7), inversion, the other intervals on the same keys (A4/d5), the notes above and below the root.
  - **Scales and modes:** steps (W/H/3), "Nth mode of …" with a link, the other modes and scales with the same notes, triads and sevenths on every degree with Roman numerals in that scale (tap to open the chord), naming disputes from the catalog.
  - **Chords:** every catalog symbol on the root, formula and what voicings leave out first, inversions with figured-bass names (6, 6/4; 6/5, 4/3, 4/2) and slash symbols, chords with the same notes (C6 = Am7), scales on the root that hold every chord tone, naming notes.
  - **Guitar shapes** (`core/learn/Voicings.kt`): found by search on any tuning, as Chordopia and Fabulus's Guitar Chord Finder do, not from a chord book. ‹ › steps through them, ▶ strums one. [Claude] rules: root in the bass, every essential tone sounds, three strings or more, four fingers at most (a barre counts once), at most one muted string inside the shape, within four frets; ranked by muted strings, missing optional tones, stretch, then height. E7♯9 includes x-7-6-7-8-x; C includes x-3-2-0-1-0 in its first five.
  - **On the neck:** every place the notes fall, frets 0–15, labelled by degree, the root in copper. Piano: the keys lit and named. Staff: the notes through the score's own notation view.
- **Audio:** the Iowa piano (`RefSound`): chords block then arpeggio, scales up and back down, intervals up, down, then together; rendered once into the reference cache.
- Tests: ReferenceTest 11 (every page on all 35 roots, interval names/inversions/just ratios, Dorian ↔ C major, C major and A harmonic minor chords per degree, C6 = Am7, C°7's three twins, E7♯9 = E G♯ B D F𝄪 fits half-whole not altered, G7 inversions, search, guitar shapes, audio). **Total 440, all pass.** Zip `dist/Apophenia-0.6.0-debug.zip` (151 MiB).
- **Unverified on device:** everything above; the shape ranking feel on a real neck; the staff view at this size; that VS ▾ menus of 50 entries scroll well.
- [Claude] choices to confirm or overrule: Reference first, then Ear, then Course; Learn opening on the Reference for now; the shape rules above; "same notes" includes rotations (modes) and symmetric twins.

## M5 part 13 — build 0.5.12 (23 Oct 2026): chord display styles; M5 built
Unit-tested and build-verified; **not device-verified.** Tests 429/429 (core 310, data 81, audio 27, app 11). Schema v13, ScoreText v4.
- **Display styles** (plan §6.2; `docs/reference/music-core-and-theory-design.md` §2.2), chosen in **CHORDS ▾** (chart view and chord-chart scraps; it also holds the READ TYPED CHORDS AS and NOTE NAMES choices from 0.5.9; remembered, applies everywhere):
  - **Pop:** Cm7, Cmaj7, Cm7♭5, Cdim7, Caug, several alterations in parentheses: C7(♭9,♯11).
  - **Jazz:** C−7, CΔ7, Cø7, C°7, C+, alterations stacked.
  - **Classical / analysis:** Roman numerals in the key: the degree (lower case when the third is minor, ♭/♯ before it off the scale), the quality sign raised (° ø +, M for a major seventh), inversions from the bass as stacked figures (I⁶, I⁶₄, V⁷, V⁶₅, V⁴₃, V⁴₂), N⁶, It⁺⁶/Fr⁺⁶/Ger⁺⁶; natural minor gives VII for the subtonic (the disputed one in the reference table). Without a key it shows pop.
  - **Plain text:** ASCII, as exported.
  - Fixed rules in every style: the 7 written when an extension is altered; ♭/♯ never −/+; 7sus4, 9sus4, not sus7; add9 without a 7th.
- **Typesetting** (`core/chord/ChordDisplay` → `Typeset`; `ui/ChordType.kt`): the extension raised, alterations raised (stacked in jazz), a numeral's figures stacked, the slash bass full size, a polychord's upper chord over a horizontal line. On the notation the stacks are real columns; in the chart cells (text views) a jazz stack falls back to parentheses and figures sit as super/subscript (V⁶₅), and a polychord is the upper chord underlined over the lower.
- The typing line now says how a chord reads, how it's spelled in the key, and how it will be shown in your style.
- **Transposition goes through the parser:** root and bass move and are spelled for the new key, the quality and alterations stay as typed, 6/9 isn't a slash, N.C. stays N.C. (unreadable text still has its leading note moved, as before).
- [Claude] choices and gaps:
  - Pop parenthesises only two or more alterations (C7♭9, but C7(♭9,♯11)).
  - Accidentals are the Unicode ♭ ♯ 𝄫 𝄪 in the app's font, not Bravura Text yet ([verify] that 𝄫/𝄪 render on the S22 Ultra).
  - Roman analysis is per chord (degree, quality, inversion); applied chords (V⁷/V), modal-mixture labels and the analysis row under a chart are the next part of the reference's §2.2/§6.
  - The McGill Billboard / ChoCo fixtures still aren't bundled (they need downloading and the licence notes in research part 3 checked).
- **M5 (Written music):** everything in its row of plan §13 is now built (note model, tab editor with note row and linked highlighting, notation editor with every entry method, transcription → tab/notation, piano sampler, chart/tab/notation playback, Board cursor scrolling) — **none of it has been tried on the phone yet**; `docs/walkthroughs/M5.md` §1–13 is the walkthrough for "transcribe a take, correct it in tab, see it as sheet music, play along as the Board scrolls".
- **Unverified on device:** all of it.

## M5 part 12 — build 0.5.11 (23 Oct 2026): stand mode, looping and recording over the Board
Build-verified; **not device-verified.** Tests 425/425 (core 306, data 81, audio 27, app 11); UI wiring only. Schema v13, ScoreText v4.
- **Stand mode** (plan §4.4; C4's BOARD menu "Stand mode · LANDSCAPE"): Board ⋯ › Stand mode. The phone turns landscape, the screen stays on, and the Board's readable blocks become one sheet in large type, top to bottom in the order they sit: lyrics with chords over the words, chord charts four bars a line, tab four bars a line, the chord symbols of sheet music without tab, drum tab, text notes; recordings appear as a ● heading.
  - **Scroll locked to playback:** while something on the Board plays, the line being played is lit and kept a third of the way down (the four-bar line of a tab, score or chart; the sung line of a lyric synced to the recording playing).
  - **One-thumb controls** in a column on the right: ▶ (PLAY ALONG menu) / ■, ⟲ back to the start, ⟳ loop the line being played (⟳✓ stops looping), ● record into the project over the playback (■ stops), A+ / A− type size, ✕ leave. Back leaves too.
  - Recording from stand mode keeps the sheet up (the full-screen recording overlay isn't shown there), so the lyric you're singing stays visible; the save sheet comes up when you stop.
- **The stack in PLAY ▾**, on the Board and in stand mode, when the project has one ("The stack · IN THE PLAYER").
- [Claude] choices (no stand-mode artboard in C4; this is the plan's literal reading):
  - The layout, the four-bar line, the controls column on the right and the 26 sp starting type size.
  - Looping takes the line being played; finer loop points are in each item's own player.
  - Sheet music in stand mode shows its chord symbols only (no large-type notation yet).
  - Unsynced lyrics don't scroll at a steady speed in stand mode or on the Board; they stay put.
- **Unverified on device:** all of it, including the rotation (the activity is recreated when it turns; stand mode is kept in the app model so it survives).

## M5 part 11 — build 0.5.10 (23 Oct 2026): playing along on the Board
Build-verified; **not device-verified.** Tests 425/425 (core 306, data 81, audio 27, app 11); this build is UI wiring, no new core logic. Schema v13, ScoreText v4.
- **▶ PLAY ▾ on the Board** (plan §4.4, "choose what plays"): every recording on the Board (its starred take, else its newest), and its tab, sheet music and chord charts on the piano, listed in the order they sit (top to bottom). While something on the Board plays the button is ■ STOP; a paused one can be resumed from the menu.
- **The Board follows:** when something on it starts playing, the Board scrolls to bring that window into view. Inside the windows:
  - a tab or sheet-music window being played shows "▶ BAR n OF N" and the tab from that bar on (sheet music without tab shows its chord symbols from that bar);
  - a chord-chart window being played shows the bar it's on and the next bars;
  - a lyrics window synced to the take that's playing (the sync made in the lyrics view) shows from the line being sung, marked ▶.
- **The score screen follows playback:** ▶ PLAY scrolls the notation or tab so the system holding the cursor stays at the top.
- A chord-chart scrap's ▶ PLAY CHORDS now plays under the scrap's own id (so the Board knows what's playing, and the player bar opens it).
- [Claude] choices and gaps:
  - The Board brings the playing window into view once, when it starts; windows are small enough that the cursor stays visible inside them. Stand mode (large type, landscape, scroll locked to playback, plan §4.4; C4's BOARD menu item) isn't built yet.
  - Unsynced lyric windows don't scroll at a steady speed on the Board (the full lyrics view does); "stay put" is what they do.
  - The stack isn't in PLAY ▾ yet (it plays from its own screen); looping a part and recording over playback from the Board are the next pieces of §4.4.
- **Unverified on device:** all of it.

## M5 part 10 — build 0.5.9 (23 Oct 2026): the chord-symbol parser
Unit-tested and build-verified; **not device-verified.** Tests 425/425 (core 306, data 81, audio 27, app 11). Schema v13 and ScoreText v4, unchanged (chords are still stored exactly as typed).
- **The parser** (`core/chord/ChordParser`, plan §6.2, music-core skill, research part 3): reads chord symbols into the MusicXML structure (`Chord`: root, one of the 33 kinds, ordered add/alter/subtract degrees, bass, a polychord above). Unicode or ASCII accidentals; quality by longest match that leaves a readable rest (`C-9` is Cm9, `C7-9` is C7♭9, `Comit3` isn't diminished); extensions and alterations bare, in parentheses, with commas or spaces (`+9`/`-9` as ♯9/♭9); add/no/omit (`no 3rd` too); sus on any chord (C7♭9sus keeps the 7th and ♭9); `6/9` never a slash chord; `D over C` and `D|C` polychords; N.C., NC, n, N. C7♯5 becomes augmented-seventh, Cm7♭5 half-diminished, C7(9) a dominant ninth, and the catalog quality is recorded.
- **Dialects** (READ AS ▾, in the chord chart view and chord-chart scraps; remembered): **Pop** or **Jazz / iReal Pro** for the symbols charts disagree on, and the note names: **English**, **German** (H = B♮, B = B♭, Fis, Es, As…; Esus4 is E sus4, not E♭ "us4"), or **Do Re Mi** (Sol, Si). German is never guessed from English input (H7 doesn't read in English).
- **Ambiguous symbols** read as the dialect says and show the other reading under the typing field: C2 (add9 in pop, sus2 in jazz), C° (triad in pop, °7 in jazz), C7+ (7♯5, or maj7), Cmaj (triad, or maj7), C11 (dominant 11th, or 9sus4), a lowercase root (major, or minor).
- **Spelled in the key** (C4): a chord whose root or slash bass is a note of the key's scale is shown with the scale's spelling (A♯7 in F major shows as B♭7, D♭m7/G♭ in E major as C♯m7/F♯), on the notation, in the chart view and in chord-chart scraps (in the scrap's key). A root outside the scale keeps your spelling (D♭7 in C stays D♭7). Only the display changes; what you typed is kept.
- The typing field says how a chord reads ("Reads as …, shown as … in the key"), the other reading of an ambiguous one, or why it can't read it (kept as typed, shown red). Unreadable chords now show red in chord-chart scraps too.
- The chord-chart playback reads through the parser in your dialect, so German and Do-Re-Mi charts play; a polychord plays both chords; a 13th leaves out the 11 over its major 3rd, an 11th its 3rd.
- Tests: every common spelling per quality, edits, slashes, polychords, N.C., each ambiguity in both styles, German and solfège names, the spelling traps (C°7 B𝄫, E7♯9 F𝄪, C7♯9 D♯, G♯ B♯, D♭7 C♭), spelling in the key, and the whole **iReal Pro quality list on all 17 iReal roots in both styles** (synthetic fixtures from the published list).
- [Claude] choices:
  - READ AS ▾ sits in the chord chart view, the chord-chart scrap and nowhere else yet (there's no settings screen); it applies everywhere.
  - "Spelled in the key" respells only scale notes, and only on screen.
  - Display styles (jazz Δ/ø/°, superscripts, stacked alterations, classical) are the next piece of §6.2; chords are still drawn as typed with ♭ and ♯.
  - The McGill Billboard / ChoCo fixtures aren't bundled yet.
- **Unverified on device:** all of it.

## M5 part 9 — build 0.5.8 (23 Oct 2026): note velocity and the velocity lane
Unit-tested and build-verified; **not device-verified.** Tests 415/415 (core 296, data 81, audio 27, app 11). Schema v13 unchanged; **ScoreText v4** (older builds refuse v4 scores rather than drop velocities; v3 scores read as before, every note at the dynamics).
- **A note can carry its own velocity** (1–127, stored as `vel=` in the note's `[…]`). Without one it plays at the written dynamics, hairpins and accents, as in 0.5.7; with one it plays at exactly that.
- **The piano roll's velocity lane** (FL Studio's event editor under the roll, `VEL`): a stem at each note's start as tall as its velocity, in its voice's colour, the selection white. A filled head is a velocity set in the lane; a hollow one shows what the dynamics give it. Touch a stem and slide up or down, let go to set it; a chord's stems move together, and so does the selection when the touched stem is in it. A faint line marks mf (80). The lane scrolls with the roll. EDIT ▾ › **Velocity from the dynamics** clears the lane for the selection (or all).
- Velocity rides with the note through every roll edit (move, chop, glue, legato…) and across ties.
- [Claude] choices:
  - A lane velocity is absolute (FL's model), not an offset from the dynamics (MuseScore's other option).
  - The hollow heads read the dynamics in written order, repeats aside (a hairpin heard differently on a second pass shows its first).
  - Transcriptions turned into notation don't carry Basic Pitch's amplitudes over as velocities yet.
- **Unverified on device:** all of it.

## M5 part 8 — build 0.5.7 (23 Oct 2026): the piano
Unit-tested and build-verified; **not device-verified, not ear-verified.** Tests 412/412 (core 293, data 81, audio 27, app 11). Schema v13 and ScoreText v3, unchanged. APK 218 MB (+13 MB of piano).
- **The piano sound** (plan: University of Iowa MIS Steinway, one dynamic now, built to add a second later). The Iowa set is a Steinway model B, stereo 16-bit 44.1 kHz AIFF, one file per note at pp, mf and ff; the site says the recordings "may be downloaded and used for any projects, without restrictions" (notice in the app's licences). The app ships 30 mf notes, every minor third from B0 to C8 (G7 for Gb7, whose link on the site is broken), made mono, aligned to the hammer, level-matched (the top two octaves roll off 6 dB, as a piano does), trimmed to 7 s in the bass, 5.5 s and 4 s through the middle, 2.5 s at the top, with a 0.3 s fade: 11.9 MB of 16-bit WAV in `assets/piano/`. `tools/piano/PianoPrep.java` makes them from the downloads.
- **The sampler** (`core/sound/PianoSampler`): each note plays the nearest recording, repitched by cubic Hermite (at most 1.5 semitones). Velocity sets the level ((v/127)², the usual MIDI curve: pp about 15 dB under mf, ff about 6 dB over) and, below mf, a gentle low-pass so soft notes sound duller. Release is the damper, about 0.1 s; the top 18 keys (from F6) have no dampers and ring on. A key struck again cuts its own previous note. It takes a list of layers, so the second dynamic is a second folder of samples.
- **Scores play on it, with their markings** (`core/score/ScorePlayback`):
  - Dynamics at MuseScore 3's velocities (pp 33, p 49, mp 64, mf 80, f 96, ff 112…), holding for the whole part from where they're written; sf, sfz, fz, rf, rfz hit one beat; fp, sfp, sfpp hit and drop.
  - Hairpins move from the level at their start to the dynamic where they end, or one level (16) when none is written.
  - Accent +20, marcato +28 and three-quarter length, stress +12, soft accent +10, unstress −15, ghost notes at 45%; staccato half length, staccatissimo 30%, portato 75%, dead notes 15%.
  - Grace notes: an acciaccatura plays on the beat for 60 ms and pushes the main note later; an appoggiatura takes its written value; together never more than half the note. Grace notes after take the end of the note.
  - Trill (and trill line) in 32nds with the step above in the key; mordents, turns and inverted turns with the steps above and below; tremolo slashes repeat the note (one slash eighths, two sixteenths…); arpeggios roll the chord 30 ms a note, up or down; the sustain pedal holds every note released under it to the pedal's end.
- **The chord chart plays:** ▶ PLAY CHORDS in the score's chord chart view, and in a chord-chart scrap at the scrap's BPM (100 when it has none, as the drum chart does). Each chord is a bass note below C3 and the chord close above its root (root between G3 and F♯4), struck at each change and again at each barline it holds across; N.C. is silent. A sound-only chord reader (`core/sheet/ChordSound`) reads the root, the quality by longest match against the chord catalog, extensions, alterations, add/no/omit/sus and the slash bass; the full parser will replace it.
- **Reference notes are the piano:** tapping a key, a fret, a staff note or a piano-roll note, and a transcription's notes in the correction screen (amplitude becomes velocity).
- [Claude] choices:
  - Fermatas, breath marks and caesuras don't change the timing yet (the cursor and the sound would part company); glissandos play as their two notes.
  - The level matching, the treble roll-off and the trim lengths are by measurement of the files, not by ear; Christopher's listen decides them.
  - The drone's "piano or sustained tone" (plan Tools table) isn't done: the drone is still the engine's tone.
- **Unverified on device / by ear:** all of it — the piano's sound, levels across the range, the seams between samples a minor third apart, the damper release, the markings in playback, the chord voicings.

## M5 part 7 — build 0.5.6 (23 Oct 2026): the chord chart view
Unit-tested and build-verified; **not device-verified.** Tests 404/404 (core 285, data 81, audio 27, app 11). Schema v13 and ScoreText v3, unchanged.
- **View ▾ › Chord chart**, as the C4 mockup has it: the score's chord symbols bar by bar, two bars to a row, one slot per beat with a slash where nothing changes, the last bar closed with a heavier line; the heading gives the title, meter and key.
  - It is the same data as the chord symbols over the notation (ADD ▾ › Chord symbols): tap a beat, type a chord, SET (or Done on the keyboard, which moves on to the next beat as you type a chart). **N.C.**, **SAME AS BEFORE** (the last chord again) and **CLEAR**.
  - **Checked as you type:** a chord-symbol reader in the core (`ChordText.readable`) takes root (with ♯/♭/double), qualities by longest match (maj7, m7♭5, ø, °7, sus4, 7sus4, 6/9, alt…), extensions, alterations, add/no/omit, parenthesised lists, a slash bass, and N.C.; 6/9 is never read as a slash chord. Anything it can't read is still kept as typed (music-core rule) and shown red in the chart.
  - A chord typed on a beat in the middle of a longer note or rest splits it there (a tie for a note), so the change has a beat to sit on; the music sounds the same.
- [Claude] choices:
  - "Spelled in the key" (C4's note) isn't done: the core has no chord-symbol model yet, only the reader, so a typed root stays as typed. The full parser (MusicXML `<harmony>` structure, dialects, ambiguity) is a separate piece of work.
  - A tuplet isn't split for a chord: the symbol goes on the tuplet note sounding at that beat.
  - Chords in the chart don't play yet (the piano sampler will).
- **Unverified on device:** all of it.


## M5 part 6 — build 0.5.5 (23 Oct 2026): the piano roll
Unit-tested and build-verified; **not device-verified.** Tests 401/401 (core 282, data 81, audio 27, app 11). Schema v13 and ScoreText v3, unchanged.
- **View ▾ › Piano roll** (FL Studio's piano roll as the model, research file §FL): the same score as bars of colour, pitch up the side (keyboard with C's named), time along with bar, beat and snap lines, the key's scale rows lit, every voice in its colour (the voice being entered brighter), the playback cursor.
  - **TOOL ▾:** Draw (tap empty space: a note of the snap length in the voice being entered; drag a note to move it, its right end to lengthen it), Select (tap notes to pick several; dragging one moves them all), Delete (tap a note). Dragging empty space scrolls. A tapped or moved note sounds.
  - **SNAP ▾:** bar, beat, 1/8, 1/16 (default), 1/32. **SCALE** snaps drawn and moved pitches to the key's scale (FL's Snap to scale).
  - **EDIT ▾:** select all, deselect, duplicate (after the selection), delete, transpose ±1 and ±12, shift a snap left or right.
  - **TOOLS ▾:** quantize (starts and ends, or starts only), chop, glue, legato, flip, limit to the instrument's range (C2–C7 without one). They work on the selection, or on everything when nothing is selected.
  - ZOOM − / +.
- **How an edit becomes notation** (`core/score/PianoRoll.kt`): the roll is the score's notes as start, length and pitch in ticks, ties joined into one bar. After an edit, only the bars the changed notes touched (before or after) are rewritten: beat-aware durations with ties (as transcription does), notes overlapping in one voice as chords and ties; bars it didn't reach keep their tuplets, spelling and everything on them exactly. A note's articulations, lyrics and other beat markings go with it; bars are added when notes are drawn past the end; fretted parts are fingered again.
- [Claude] choices:
  - No velocity lane yet: notes have no velocity in the model (dynamics are the markings). Adding one is a model change for a later build.
  - Snap stops at 1/32; triplet snaps (1/8 and 1/16 triplet) came in 0.8.6.
  - Notes that overlap in one voice become chords with ties rather than being moved into another voice (MuseScore's MIDI import would split them into voices).
  - Not built from FL's list (chord stamp 0.8.6, arpeggiate 0.8.7, paint, slice and ghost notes 0.8.8): mute, strum, arpeggiate, flam, background waveform; generators (riff machine, chord progression, randomize) stay out (no suggestions).
- **Unverified on device:** all of it, especially dragging notes inside the scrolling roll and the edge-drag to lengthen.


## M5 part 5 — build 0.5.4 (23 Oct 2026): input modes and voices 2–4
Unit-tested and build-verified; **not device-verified.** Tests 392/392 (core 273, data 81, audio 27, app 11). Schema v13, unchanged; the score's stored text is now **ScoreText v3** (adds hidden rests; v1 and v2 scores read as before; older builds refuse v3).
- Checked against MuseScore 4's handbook (Alternative note input methods; Voices) before building.
- **MODE ▾** (next to INPUT ▾), MuseScore's input modes:
  - **Step**: as before.
  - **Rhythm**: each length key puts in a note of that length on the middle line and moves on; REST puts a rest. DOT and TUPLET only set what comes next, as in MuseScore (dot first, then the length).
  - **Re-pitch**: each pitch (piano, staff tap or fret) replaces the next note's pitch, keeping its rhythm, ties and markings; rests and notes tied in are passed over, and a tied note's continuation takes the new pitch too. + CHORD adds to the note instead. A fretted note is fingered again.
  - **Insert**: a pitch goes in before the selected beat at the picked length; the bar grows and turns red until it adds up; the cursor stays before the same beat. REST inserts a rest; + CHORD adds to the beat just inserted.
  - **Real-time** (to a click): START CLICK, hold piano keys; each click enters the picked length with the keys held, held on through the next click ties on, nothing held enters nothing (ADVANCE puts a rest). STOP CLICK rewrites tied runs as beat-sized notes (four tied 16ths on a beat → a quarter). The click is the app's metronome, one click per length entered (one per beat when that would pass 400 BPM); its own tempo is put back afterwards.
  - **Real-time, tapped**: the same, at your tempo: each tap of ADVANCE is a click; DONE tidies the ties.
  - The piano now tracks every finger from press to release, so several keys can be held.
- **Voices 2–4** (VOICE ▾ on the duration bar, showing V1–V4 in the voice's colour): enter in any voice (a voice starts in a bar as rests filling it; NEXT carries on in the same voice into the next bar); move a note, or the whole beat, to another voice where that voice has a beat of the same length starting at the same time (MuseScore refuses otherwise too); exchange voices 1 and 2 in a bar; hide or show a rest in voices 2–4 (voice 1 keeps its rests, as MuseScore's reference voice); clear a voice in a bar (the highest one goes away, a lower one turns back to rests).
  - **Notation:** the voices share the staff lined up in time, voices 1 and 3 stem up and 2 and 4 down whenever a second voice shows, rests moved apart (up for 1 and 3, down for 2 and 4), hidden rests shown faintly, a note a step or less from voice 1's at the same time moved aside, accidentals read across all the voices in time order, selection in the voice's colour (1 accent, 2 green, 3 blue, 4 violet). Tapping a note selects it in whichever voice it is; tapping empty staff enters in the voice being entered.
  - **Tab:** other voices' fret numbers sit where their beats fall, in the voice's colour, and can be tapped; the rhythm under the tab is voice 1's.
  - Playback plays every voice; fingering after a retune or transpose fingers each voice as its own line.
- [Claude] choices:
  - Lines (slurs, hairpins, 8va…) and lyric stepping stay on voice 1; marks, chord symbols and text work on any voice.
  - Real-time's beats are timed by the app, the click by the audio engine: both start together and are each kept to their own clock, so they shouldn't drift over a take, but this isn't measured.
  - Tapping empty staff in a bar that doesn't have the voice yet starts the voice there (as rests) and asks for a second tap.
  - Fretted notes in voices 2–4 can land on a string voice 1 is using at the same moment; nothing checks that yet.
  - Stems of voices 2–4 aren't drawn in the tab's rhythm line.
- **Unverified on device:** all of it, especially real-time entry against the click and holding several keys.


## M5 part 4 — build 0.5.3 (22 Oct 2026): everything ADD ▾ puts on a score
Unit-tested and build-verified; **not device-verified, and none of the new markings has been looked at on a screen.** Tests 381/381 (core 262, data 81, audio 27, app 11). Schema v13, unchanged; the score's stored text is now **ScoreText v2** (v1 scores read as before; a v1 build refuses a v2 score instead of half reading it).
- **Model** (`core/score/Marks.kt`, `Score.kt`): an event now carries articulation and ornament marks, a dynamic, lines (slur, hairpins, 8va/8vb/15ma/15mb, pedal, glissando, trill line; each stored as a count of beats from where it starts, so none can be left open), a chord symbol as typed with an optional fretboard diagram, staff text (expression, technique, plain), lyrics (verses 1–9, hyphen, melisma line), tremolo strokes and grace notes (before or after, slashed or not). A note carries a notehead, finger (0–4, T, piano 1–5), right-hand pluck (p i m a c) and a circled string number. A bar carries a start repeat, its closing barline (single, double, final, dashed, end repeat with a play count), volta endings, segno/coda/To Coda/Fine/D.C./D.S. (al Fine, al Coda), a rehearsal mark, and changes of clef, key, meter and tempo with tempo text.
  - Accent, marcato and staccato stay the note effects the tab already shows; ADD ▾ › Articulations sets them on the whole chord, so there's one record of each.
  - Free text is %-escaped so the stored form stays ASCII (lyrics in any language).
- **Playback follows the form:** end repeats (played their count), volta endings by pass, D.C./D.S. back once (repeats not retaken after a jump, the last ending of each volta group), al Fine stops at Fine, al Coda leaves at To Coda for the Coda. Each bar plays at its own meter and tempo; the cursor follows the passes.
- **Notation draws all of it:** articulations on the notehead side (fermatas, breaths and ornaments above), arpeggio lines with arrowheads, dynamics and expression text below, technique and staff text above, chord symbols with diagrams above, lyrics under the staff per verse with hyphens and melisma lines, fingering beside the head, the seven noteheads, ghost-note brackets, tremolo strokes on the stem, small grace notes (slashed acciaccaturas), lines that run across barlines and on to the next system (slurs on the notehead side, hairpins, 8va brackets, Ped. … ✱, trill wiggles, gliss.), every barline kind, repeat dots, voltas, segno and coda signs, jump text, boxed rehearsal marks, tempo marks (♩ = 96 with text), and clef, key and meter changes mid-system. Notes under an 8va line are drawn an octave lower, and a staff tap there enters the sounding octave.
- **ADD ▾** (all 14 categories): Accidentals; Clefs · keys · meters (from the selected bar; bar 1 changes the score's own); Articulations; Ornaments; Dynamics · hairpins; Lines (each opens a length menu: over 2–16 beats, to the end of the bar, remove); Repeats · barlines; Text · tempo · rehearsal (typed text, the next rehearsal letter offered, tempo by BPM, tempo words); Lyrics (type a syllable, **NEXT ›**, **HYPHEN –** or **HOLD ___** moves on to the next sung note, skipping rests and tied-on notes); Chord symbols · diagrams (typed, or a diagram from a fretted chord's own frets, or typed frets); Fingering; Noteheads; Tremolo; Grace notes (add, step the last one up or down in the key, remove). SELECTED lists what's on the beat.
- **Transpose** moves grace notes and chord-symbol roots and basses too (spelled in the key), and takes off chord diagrams, which no longer fit.
- [Claude] choices:
  - A meter change doesn't rebar the notes: they stay put and bars that no longer add up turn red, as an over-full bar does now.
  - Key changes don't print cancelling naturals; courtesy clefs, keys and meters at a system's end aren't drawn.
  - Lines and grace notes are on voice 1 (the only voice so far); one line of each kind starts per beat.
  - Two-note (between-notes) tremolos aren't in yet; single-note strokes are. A tab's tremolo-picking effect draws three strokes in notation.
  - Playback plays the main notes only: grace notes, ornaments, dynamics and articulations don't change the sound yet (the plan's piano sampler is still to come).
  - Grace notes on a fretted part have no string or fret yet and don't show in the tab view.
  - Chord symbols are stored as typed; there is no chord-symbol parser in the core yet, so they aren't checked or restyled.
- **Unverified on device:** all of the drawing above, the ADD ▾ menus and the typing panel, and playback through repeats.


## M5 part 3 — build 0.5.2 (22 Oct 2026): the notation view
Unit-tested and build-verified; **not device-verified, and the staff drawing hasn't been looked at on a screen yet.** Tests 368/368 (core 249, data 81, audio 27, app 11). Schema v13, unchanged.
- **Notation view** of the same score (view ▾ › Notation; sheet-music items open on it): five-line staff with clef (treble, treble 8vb for guitar, bass, alto, percussion), key signature, meter, noteheads placed from spelled pitches, accidentals as each bar needs them (an accidental lasts the bar; a tied-in note shows none), ledger lines, stems by the middle-line rule (a note on the middle line stems down), seconds in a chord on either side of the stem, flat beams within a beat, flags, dots, ties, rests, tuplet numbers, barlines with a final double bar, red bar numbers for bars that don't add up. Guitar and bass are written an octave above sounding.
  - Glyphs from **Bravura 1.482** (Steinberg, SIL OFL 1.1; `res/font/bravura.otf`, licence in `assets/licenses/Bravura-OFL.txt`), positioned with SMuFL's metrics (stem anchors and sizes from Bravura's metadata).
  - Key signatures from the key: a mode uses its parent major's (D Dorian none, F♯ Phrygian two sharps), other scales the major or natural minor of the tonic; placed in the standard pattern, a line lower in bass clef.
- **Entry** (INPUT ▾): **piano keys** (two octaves, OCT ▼ ▲), **tap on the staff** (the line or space gives the note, the key signature its sharp or flat), and the fretboard on fretted parts. A note replaces the beat, or joins it with **+ CHORD**. On a fretted part it goes on the lowest free fret; one the instrument can't reach is refused with a message. Spelled in the key.
- **ADD ▾** (C4's 14 categories): **Accidentals** works (sharp, flat, natural, double sharp, double flat on the same letter; **Respell** cycles the same sound through its spellings, C♯ → D♭ → B𝄪). The other 13 categories are listed as "next in M5".
- **New sheet music:** WRITE ▾ › Sheet music (four empty treble bars), and the MIDI tab's **MAKE TAB · SHEET ▾ › Sheet music, no tab**.
- [Claude] choices:
  - Flat beams.
  - Tenor clef key signatures: drawn from 0.8.5 (sharps in their own shape).
  - Keys past seven sharps or flats get no signature; their accidentals go in the bars.
- **Unverified on device:** the whole notation drawing (glyph sizes, positions, beams, ties) and the entry methods.


## M5 part 2 — build 0.5.1 (22 Oct 2026): the tab editor
Unit-tested and build-verified; **not device-verified.** Tests 360/360 (core 241, data 81, audio 27, app 11). Schema v13, unchanged.
- **Step entry, Guitar Pro's way** (C4 tab view): select a beat, pick a length on the **duration bar** (whole to 64th, DOT cycles none/one/two, REST clears the beat, TIE ties it on, TUPLET ▾ triplet/quintuplet/sextuplet/septuplet/duplet, VOICE ▾ voice 1 for now), tap a fret on the fretboard, **NEXT ›**.
  - A fret goes on the selected beat: it replaces that string's note or adds one (a chord); a rest becomes a note. Spelled in the key; you hear it.
  - A hollow dot still moves the selected note to another place with the same pitch.
  - NEXT › steps on; at the end of a short bar it adds a rest of the chosen length (cut to fit), at the end of the last full bar it starts a new bar.
  - A bar that doesn't add up keeps its notes and shows its number in red; SELECTED says how far off it is.
- **EFFECTS ▾:** Guitar Pro 8's set in five categories (dynamics and tone, touch, harmonics, vibrato and tremolo, bass and percussive), toggled on the selected note and drawn above the tab.
- **TUNING ▾:** every preset with the same string count and capo 0–7; then **Keep the notes** (finger them again) or **Keep the fingering** (pitches move), plan §6.4.
- **EDIT ▾:** undo/redo (200 steps), delete note, delete beat, insert a beat after, insert/delete a bar, copy a bar and paste it after another, transpose ±1 semitone or ±1 octave (respelled in the key, fingered again).
- **INPUT ▾** shows the fretboard; piano keys, sing-or-play and recording entry arrive with notation. **SCORE ⋯** holds the note row choice.
- **New tabs:** WRITE ▾ (and the Board and Library add menus) › Guitar tab or Bass tab: four empty bars in the metronome's meter and tempo, standard tuning.
- [Claude] choices:
  - Bars may be short or over while you edit, marked rather than auto-fixed.
  - A new tab is standard tuning, 4 bars; guitar 22 frets, bass 24.
  - Transposing re-fingers the whole part.
- **Unverified on device:** everything in 0.5.0 and 0.5.1.


## M5 part 1 — build 0.5.0 (22 Oct 2026): the note model, transcription → tab, the tab view
Unit-tested and build-verified; **not device-verified.** Tests 353/353 (core 234, data 81, audio 27, app 11). Schema v13, unchanged (scores are text in the TAB/SHEET items).
- **One note model** (`core/score/`, plan §6, §10.5a): score → parts (name, clef, and for fretted parts the instrument: tuning, capo, frets) → bars → up to four voices → events (rest, note or chord) with exact durations (dots, nested tuplets). Notes are spelled sounding pitches with string and fret, ties, and Guitar Pro's effect set (ghost, accents, palm mute, let ring, dead note, hammer-on/pull-off, slides, bend, whammy, tapping, harmonics, tremolo picking, vibrato, slap, pop…). Stored as versioned ASCII text (plan §6.5); a newer format is refused, not half read. Notation, tab and piano roll will all show this one model.
- **Fingering chooser:** picks a string and fret for every note over the whole passage so the hand moves as little as it has to (a riff stays in one position, open strings are free, a chord gets one string per note within a five-fret span). Every position stays changeable.
- **Transcription → tab** (plan §10.3): the MIDI tab's new **MAKE TAB ▾** (pick the tuning: every preset) puts the transcription into bars at the recording's BPM and meter from its first downbeat, spelled in its key, and fingered. It needs the recording's BPM. A new tab item opens, in the recording's project or the Library.
  - Durations are written so the beats show: a note on a beat may run to a later beat or be a dotted note; one off the beat is tied at the next beat; everything is tied over the barline; gaps become rests.
- **Tab view** (C4): tab with the rhythm under it (stems, flags, beams within a beat, dots, triplet numbers, rest glyphs), tied-on frets in brackets, bar numbers, effects above, and a **note row** (ROW ▾: note names, scale degrees in the key, or off).
  - Tap a fret number: SELECTED says the pitch (and written pitch for bass and guitar), string and fret, duration, ties, effects, its degree in the key, and every other place it can be played.
  - The **fretboard** under it shows the note filled and its other places hollow: tap a hollow dot to move the note there; tap any other fret to hear it.
  - **▶ PLAY** plays the tab on the note synth with a cursor; **EDIT ▾** has undo and redo.
  - The Board's window shows the first two bars as plain-text tab.
- [Claude] choices:
  - One voice from a transcription: a chord is the notes that start together, each lasting until the next starts.
  - A pickup before the first downbeat moves the music a whole bar on.
  - Sixteenth grid unless the transcription is quantised.
  - 24 frets for bass, 22 for guitar, 18 for ukulele.
  - The ukulele sounds as written; guitar and bass are written an octave up.
- **Not yet in M5:** entering and editing notes (durations, frets, effects menus), notation, piano roll, chord chart view, the piano sampler, Board cursor scrolling. These are the next parts.
- **Unverified on device:** all of it (tab drawing on the S22 Ultra's screen, taps, the fretboard, playback cursor).


## M4 part 6 — build 0.4.5 (22 Oct 2026): speech → text with Moonshine. M4 is code-complete
Unit-tested and build-verified; **the real model ran on this PC (desktop ONNX Runtime), not yet on the phone.** Tests 343/343 (core 224, data 81, audio 27, app 11). Schema v13 (`take_speech`).
- **Speech → text** (plan §10.4), from a recording's **AI ▾** or its new **TEXT** tab: Moonshine Base (English, MIT, int8, 63 MB, bundled) on the same ONNX Runtime as the stems, as a background job under the same foreground service.
  - The take is cut into pieces at its quietest moments (at most 20 s each; silent pieces skipped), and each piece is read into one line with the time it starts in the take.
  - TEXT tab: the lines with their times; **tap a time to hear that line**; **EDIT** fixes the words (saved as "corrected"); **MAKE ▾** › a text note or a lyric sheet from the words, in the recording's project or the Library; WRITE DOWN AGAIN warns when it would lose fixes. The take is never changed.
  - The AI mode marks Speech → text READY and lists its jobs.
- **Model choice** (`docs/research/speech-model-2026-10-22.md`): Base, not Small Streaming. The streaming models only exist as undocumented ORT pieces driven by Moonshine's own C++; Moonshine's Android library downloads models over the network and brings its own runtime; sherpa-onnx would put a second ONNX Runtime in the APK. Plan §10.4 updated. Small Streaming stays an option if Base reads your memos badly.
- **Measured on this PC:** a 6-second sentence in Windows' synthetic "Microsoft David" voice read back word for word in 0.36 s; silence reads as nothing. A synthetic voice is the easy case: real memos, mumbled ideas and speech over an instrument are the phone test (plan §14.2 WER on your own memos).
- [Claude] choices:
  - Pieces of at most 20 s, cut at the quietest 30 ms between 8 and 20 s.
  - At most 6.5 tokens per second of audio.
  - The first 10 minutes of a take.
  - One line per piece, so lines follow your pauses rather than sentences.
  - Made notes and lyric sheets get one line per spoken line, without the times.
- **APK size: 205 MB** (zip 144 MiB, `dist/Apophenia-0.4.5-debug.zip`; too big for the phone app's 30 MiB).
- **Unverified on device:** model load and speed on the S22 Ultra (target faster than real time), accuracy on your own memos (quiet room, over an instrument), editing, MAKE ▾, and everything in 0.4.4.


## M4 part 5 — build 0.4.4 (22 Oct 2026): stems with Spleeter
Unit-tested and build-verified; **the real models ran on this PC (desktop ONNX Runtime), not yet on the phone.** Tests 332/332 (core 215, data 79, audio 27, app 11). Schema v12 (`take_stem`).
- **Separate stems** (plan §10.6), from a recording's **AI ▾** or its new **STEMS** tab: Deezer's Spleeter 4 stems (vocals, drums, bass, other), in stereo, on ONNX Runtime 1.31.0 as a background job.
  - The models are bundled (the community ONNX conversion, fp16, 4 × 19.7 MB; research doc `docs/research/stems-models-2026-10-22.md`).
  - Each stem becomes **its own recording** in the Library, titled "<song> <take> · <stem>", tagged "stem", with the song's BPM and key, in the same project if the song is in one. A link table records which take it came from; the stem's INFO tab shows **FROM** with a way back. The take is never changed.
  - STEMS tab: one row per stem with **M · S ▾** (Mute, Solo, Play this stem, Open it) and **▶ PLAY** for the audible ones mixed (an evictable render in the cache).
  - A foreground service keeps AI jobs (stems and Notes → MIDI) running with the app in the background: media processing on Android 15+, data sync before. It is started only from a tap in the app.
- **Pipeline** (`core/analysis/Spleeter.kt`, ported from the model card's reference): 44.1 kHz stereo, periodic Hann, frame 4096, hop 1024, 3072 zeros in front; 1024 of 2049 bins to the net in 512-frame splits; ratio masks of squares over all four stems; masks carried above 11 kHz by their per-frame mean ("average"); masked complex spectrum; weighted overlap-add. One split (about 12 s) at a time, streaming to the stem files, so a song never sits in memory whole.
- **Measured on this PC** (desktop CPU, 16 s synthetic four-part song, `SpleeterModelTest`): 2.0 s for all four stems; the four stems add back up to the mix within 4e-8. Share of each synthetic part in its own stem: drums 0.88, bass 0.85, other 0.99. The synthetic "voice" (a formant-shaped tone) went to OTHER: a synthetic tone isn't a voice, so that says nothing about real singing.
- [Claude] choices:
  - Stems are 48 kHz 24-bit stereo WAVs like imports (44.1 kHz inside the separation).
  - The first 10 minutes of a file.
  - 4 ONNX Runtime threads.
  - Mute and solo are per screen, not saved.
  - Separating again makes four new recordings; the old ones stay until deleted.
- Not built: Spleeter 5 stems (+ piano). The plan says 4 or 5; the conversion only covers 4 stems.
- Licences added to the app's notices folder: ONNX Runtime (MIT), Spleeter (MIT; weights per the JOSS statement, deezer/spleeter#898 still open), the model card (Apache-2.0).
- **APK size: 156 MB** (the 34 MB ONNX Runtime library, 79 MB of stem models). The 98 MiB zip is over the 30 MiB the Claude phone app takes, so it is at `dist/Apophenia-0.4.4-debug.zip`.
- **Unverified on device:** model load and speed on the S22 Ultra (plan §14.2: 3 minutes of song in under 5 minutes, the job surviving the background), memory use, real separation quality, mix playback, the foreground notification.

## M4 part 4 — build 0.4.3 (22 Oct 2026): the correction screen
Build-verified; edits, quantising, the synth and the stored setting are unit-tested; **not device-verified.** Tests 320/320 (core 207, data 77, audio 25, app 11). Schema v11, unchanged.
- **Correction screen** (plan §10.3), from a recording's MIDI tab › **CORRECT ›**:
  - The take's waveform over its notes on one time line. Drag to move along; − and + zoom. Tap the waveform to play from there.
  - **Tap a note** to select it, then: ▼ ▲ a semitone, OCT ▼ ▲, ◀ ▶ move it (10 ms, or a grid step when quantising), SHORTER, LONGER, HEAR (the take from that note), DELETE. Changing a pitch drops that note's bends.
  - **Long-press an empty place** to add a note there: a quarter second, or one grid step.
  - **UNDO / REDO** (up to 200 steps).
  - **PLAY ▾:** the take, the transcription (a soft synth tone per note), or both mixed. Renders are cached and evictable.
  - **QUANTISE ▾** (off, 1/4, 1/8, 1/8 triplets, 1/16, 1/16 triplets): a setting, not an edit.
    - Starts snap to the nearest step and ends to the step after, so no note vanishes.
    - The played notes stay underneath as outlines; turning it off brings back exactly what was played.
    - The grid starts on the click's first downbeat, or the first attack (from the analysis). It needs the recording's BPM.
- Every change is saved to the transcription with an "edited" mark and the quantise setting (optional lines in its text, so older stored transcriptions still read). **The take is never touched.**
- MIDI tab: "corrected" and "quantised" shown. SAVE AS MIDI writes the quantised notes when quantising is on. TRANSCRIBE AGAIN warns that it loses the fixes.
- [Claude] choices:
  - Choosing the string comes with tab (M5), since a string only means something on the tab view.
  - New notes are a quarter second at strength 0.7.
  - The nudge is 10 ms.
  - The mixed playback is 60% notes, 80% take.

## M4 part 3 — build 0.4.2 (22 Oct 2026): finding the chords
Build-verified; unit-tested on synthetic chords; **not device-verified or tried on real recordings.** Tests 316/316 (core 203, data 77, audio 25, app 11). Schema v11, unchanged: chords are part of take analysis (version 2, so every take is analysed again once).
- **Chord finder** (`core/analysis/ChordFinder.kt`, P0 research R2):
  - A chroma per beat: linear magnitudes, 60 Hz–2 kHz, 4096-point frames.
  - Matched against **harmonic templates** for 12 roots × 12 qualities from the core catalog [Claude]: maj, m, 5, 7, maj7, m7, dim, aug, sus2, sus4, m7b5, dim7.
    - Each chord tone also predicts its overtones (harmonics 2–4 at 0.6 a step), so a D's 3rd harmonic (A) doesn't turn B♭ into B♭maj7.
    - A triad wins a tie with its seventh (0.02 per extra note).
  - **No chord (N.C.)** when nothing matches above 0.6 [Claude], or when a beat has under 2% of the loudest beat's energy.
  - **Smoothing:** Viterbi over the beats; each change of chord costs 0.08 [Claude], so a chord holds unless the sound really changes.
  - **Inversions:** the strongest note under 220 Hz names the bass when it's a chord tone other than the root (C/E). A bass note outside the chord (a pedal) is ignored [Claude].
  - Roots are spelled in the recording's key (F♯m in D, B♭ in F), ASCII as charts store them.
  - The unit tests found three real faults on the way, each fixed at its cause:
    - Overtones read as chord tones: fixed by the harmonic templates.
    - Log-compressed chroma lifted overtones and window leakage: fixed by linear magnitudes, and silence judged by energy.
    - Tests: I–IV–V–I reads C C F F G G C C and charts as `| C / F / | G / C / |`; C/E, E5, Am, G7; B♭ spelled in F; silence is N.C.; F♯m in D; free time gives one bar per chord.
- **Where the beats come from:**
  - Recorded with the click: the click's own beats and meter, from its first downbeat after the count-in.
  - Otherwise: the analysed pulse from the first attack, in 4/4 [Claude]. The bar line is a guess and the UI says so.
  - Free time: half-second steps, and the chart is one bar per chord, in order (the rhythm isn't known).
- **Recording view › INFO › CHORDS:**
  - The chart (first 8 lines) and where its beats came from.
  - **MAKE A CHORD CHART ▸** creates a chord chart scrap ("Chords · title · A") with the recording's BPM and key, where the recording lives (its project, or the Library) [Claude], and opens it to edit.
- AI ▾ › Find the chords and the AI mode's list now say it's automatic and ready.
- [measure] / unknown:
  - Accuracy on real strummed guitar, piano and full mixes from the phone mic.
  - Whether 0.6 and 0.08 are the right lines.
  - How often the guessed bar line is off by a beat or two without the click.

## M4 part 2 — build 0.4.1 (22 Oct 2026): Notes → MIDI with Basic Pitch; the AI mode
Build-verified; the pre- and post-processing is unit-tested; **the model has never run: not on the phone, and LiteRT can't run on this PC.** Tests 312/312 (core 199, data 77, audio 25, app 11). Schema v11.
- **Model:** Spotify Basic Pitch `nmp.tflite` (ICASSP 2022) from github.com/spotify/basic-pitch, Apache-2.0, Copyright 2022 Spotify AB.
  - 204,448 bytes, sha256 3db297d5…552676. Bundled as `assets/models/basic-pitch-icassp2022.tflite`, licence in `assets/licenses/`.
  - Its signature `serving_default` (input `input_2` → `note`, `onset`, `contour`) was found by searching the file's strings. That is source-only evidence.
  - The research note's 0.84 MB LiteRT build is a different export; this is the repo's own file.
- **Runtime:** LiteRT 2.3.0 (`com.google.ai.edge.litert:litert`), classic `Interpreter`, 4 threads, CPU. It adds about 6 MB of native code (arm64). Licence in `assets/licenses/`.
- **Port** (`core/analysis/BasicPitch.kt`), line by line from `inference.py` and `note_creation.py`:
  - Windows: 43,844 samples, 7,680 overlap, half an overlap of silence in front.
  - Unwrapping: drop 15 frames each end; trim.
  - Frame times, including the reference's per-window correction.
  - Inferred onsets.
  - Onset-peak notes and the melodia pass: onset 0.5, frame 0.3, at least 11 frames, energy tolerance 11.
  - Pitch bends from the contour (thirds of a semitone, Gaussian ±25 bins).
  - 9 unit tests on synthetic model outputs. Two of my own expectations were wrong and were corrected by tracing the reference, not by changing the port.
- **Audio in:** the take at 24 kHz, a 63-tap Blackman windowed-sinc low-pass at 10 kHz (tested: 1 kHz passes, 11.8 kHz < 1%), then resampled to 22,050 Hz. At most the first 5 minutes [Claude].
- **MIDI out** (`core/analysis/Transcription.kt` MidiFile), made the way the reference makes its MIDI:
  - Format 0, 480 ticks a quarter, Electric Piano.
  - Velocity is 127 × the note's activation.
  - Pitch bends (4096 a semitone) only on notes that overlap no other, then reset after the note.
  - The tempo is the recording's BPM, or 120 when it has none. Either way the notes land at their real times.
  - Unit-tested with a small reader in the test: ticks, velocities, bends, multi-byte deltas, exact track length.
- **Storage:** schema v11 `take_transcription` (take, model id, time, notes as text). It goes with the take.
- **Recording view:**
  - **AI ▾** (C4's "AI on this recording"): Notes → MIDI runs or shows its progress or ✓ DONE; Tempo and key is automatic; the rest say when they arrive.
  - **INFO / MIDI tabs:** the MIDI tab has TRANSCRIBE, progress, a piano roll (tap to play from that moment, with the playhead), the notes spelled in the key, SAVE AS MIDI ▸ (Android's save picker), TRANSCRIBE AGAIN and the plan §10.3 honest limits.
- **AI mode** (C4), replacing the placeholder:
  - ON PHONE, then the seven tools with their models, READY or LATER.
  - JOBS: running transcriptions with their percentage, failed ones with the reason, finished ones. Tap a job to open its recording.
- [Claude] choices:
  - Transcription runs only when you ask (AI ▾), not on every take: it's the heavier job.
  - Bends ride on non-overlapping notes only, as in the reference's default.
- [measure] (plan §14.2): time per 2-second window on the S22 Ultra (target: 1 minute of audio in under 10 s); accuracy against your hand-checked takes (bass riff, guitar chords, sung melody); memory for a 5-minute file.

## M4 part 1 — build 0.4.0 (22 Oct 2026): take analysis
Build-verified; unit-tested on synthetic audio; **not device-verified, and not yet run on real takes.** Tests 297/297 (core 186, data 75, audio 25, app 11). Schema v10.
- **Take analysis** (plan §10.2, `core/analysis/TakeAnalysis.kt`). No ML: the audio is reduced to 24 kHz mono, and the analysis covers at most the first 5 minutes of a file.
  - **Onsets:** spectral flux of log magnitudes (1024-point frames, 256 hop), peaks over a moving mean. Only rising energy counts as an attack (a note stopping short otherwise read as a new one).
  - **Tempo:** autocorrelation of the onset strength. The fastest clear pulse, then the pulse or its half, decided by accents on alternate pulses and nearness to 120. Half and double are shown as alternatives.
    - **Free time** when the regularity is under 30% [Claude], when the take is under 4 s, or when there are fewer than 6 attacks.
    - Unit-tested: clicks at 72, 120 and 160 within 1.5%; eighths at 92 accented on the beat read 92, with 184 as the double; scattered attacks and a 3 s take are free time.
  - **Single-line notes:** YIN every 20 ms, 30 Hz–1 kHz, over the first 2 minutes. Frames are grouped while the pitch holds and split at attacks (a repeated note) and at pitch changes; notes under 60 ms are dropped. Each note stores start, end, MIDI number, cents and confidence.
    - Unit-tested: A2 C3 D3 E3 and a bass E1 come back as those notes.
  - **Key and mode** (P0 research R1): 12 tonics × the 7 diatonic modes plus harmonic and melodic minor (those two a little behind when they fit the same notes).
    - The fit is time on each note inside versus outside the scale, plus home-note clues: the last note, the lowest, the longest and the first. With chords or a mix it uses chroma and the strongest bass note.
    - **Uncertain** when another home note scores within 0.05, or another mode on the same home note within 0.02. A pentatonic riff fits both Dorian and minor, so it says so.
    - Each candidate has its reason in words, for example "Notes A B C D E F G, all in the scale. A is ends on it, the lowest note, the longest note."
    - Tonics are spelled the way the key is written (D♭ major, F♯ minor, G♯ minor).
    - Unit-tested: an A minor line reads A minor; a pentatonic riff and a bare C-major collection are uncertain; I–IV–V–I chords read C major from the spectrum.
- **Where it runs** (`audio/Analyser.kt`): one take at a time on a background worker, newest first, after each recording or import and at launch (it catches up on older takes).
  - Results are stored per take (schema v10 `take_analysis`, text form, versioned so a newer analysis reruns).
  - The first analysis of a recording fills in its BPM and key only when nobody has set them. A take recorded with the click keeps the click's tempo. Uncertain keys aren't filled in.
- **Recording view › ANALYSIS · TAKE X:**
  - Tempo chips (pulse, half, double, FREE TIME) and the regularity.
  - Up to 4 key candidates with their reasons.
  - The notes spelled in the key.
  - ANALYSE AGAIN.
  - Tapping a tempo or key makes it yours, and analysis never changes it again.
- [measure] / don't know yet:
  - How this does on real phone-mic takes: bass, strummed guitar and voice, the plan §14.3 test recordings.
  - Analysis time on the S22 Ultra for a 3-minute import.
  - Whether the 30% regularity line is right.
- **Next in M4:** Basic Pitch (LiteRT) polyphonic transcription, chord recognition into a chord chart, the correction screen, MIDI out, Spleeter stems, Moonshine speech-to-text.

## Build 0.3.7 (22 Oct 2026): no pre-roll
Build-verified; tests 285/285 unchanged; not device-verified. *Christopher, asked how long the pre-roll should be: "none then".*
- **Removed:** pre-roll and the Listening state. The mic opens only for ● REC and while the tuner's screen is open.
  - The top-bar chip now only shows the state: MIC OFF, RECORDING, or TUNER · MIC ON. Tapping it does nothing.
  - Engine: `nativeSetListening` and `nativePrerollSeconds` are gone. A take starts at the tap.
  - Stopping a recording now keeps the mic open when the tuner is using it (before, it closed unless Listening was on).
- **Kept:** the `take.preroll_frames` column, so takes from earlier builds still show their mark. New takes write 0.
- **Updated:** plan §2 and §7 Mic control, CLAUDE.md, and the realtime-audio skill. Walkthrough M1a §6 is withdrawn.

## M3 part 7 — build 0.3.6 (22 Oct 2026): drum charts. M3 is code-complete
Build-verified; unit-tested; not device- or ear-verified. Tests 285/285 (core 177, data 73, audio 24, app 11). Schema v9, unchanged: a drum chart is a new scrap format token (`drum_chart`), not a new table.
- **Drum chart scrap** (plan §3.1). WRITE ▾ › Drum chart, on the Board or in the Library. It shows in the Library as DRM and on the Board as a window with the tab.
  - It opens on a **grid**, one bar to a line and one row per drum. Tap a cell to step through rest, x, X (accent), g (ghost) and o (open hi-hat only).
  - **Menus:** meter (4/4, 3/4, 2/4, 5/4, 6/4, 7/4, 6/8, 9/8, 12/8), steps to a beat (quarters to thirty-seconds, triplets and sextuplets), bars (1–16), swing (straight, 54, 58, 62, 67%), ADD A DRUM (any of the 11).
  - Tap a drum's name to clear or remove it.
  - **Tempo** is the scrap's BPM (the BPM sheet).
  - **▶ HEAR IT** plays it on the synthesized kit, with the playing step lit.
  - **UNDO/REDO** on the grid. **TEXT** shows the chart as drum tab to edit by hand, with keys for | - x X g o, and lists anything it can't read.
- **The text** (`core/tools/Drums.kt` DrumChart) has `{time: 4/4}`, `{steps: 4}` and `{swing: 58}` lines, then drum tab lines (`HH |x-x-x-x-|`). Writing a drum a second time, or leaving a blank line, starts the next line of music.
  - Steps to a beat are worked out from the first bar when not given.
  - Anything unreadable is listed and read as a rest. The grid's own text puts 2 bars of sixteenths to a line.
  - Unit-tested: round trips (also 12/8 with swing, and 5 bars), lines of music continuing, problems, tap cycle, resizing (sixteenths to eighths keeps the eighths; 4/4 to 3/4 drops beat 4), the starter, render.
- [Claude] choices:
  - A new chart is one empty bar of hi-hat, snare and kick in sixteenths, in the metronome's meter (when a chart takes it) and tempo, stored as BPM from the metronome.
  - A chart without a BPM plays at 100.
  - Changing steps or meter keeps a hit only where its beat and place in the beat still exist.
  - Additive meters (7/8 as 2+2+3) are left out, because a grid step has to be the same length all through the bar.
  - The notation view of a chart waits for the notation editor (M5).
- **M3 unverified on device (all of it):**
  - Click grid alignment against a recording.
  - Tuner on a drop-A bass through the phone mic.
  - Stretcher and isolator CPU on the S22 Ultra.
  - Rudiment stickings 8, 23, 27 and 30: on hold until a trained drummer can check them (Christopher, 22 Oct 2026).
  - The drum kit's sound and the lit step's timing.
  - The grid's cell size at phone width.
  - The M3 goal (plan §13): learn a bass line from an imported song at 0.6× with the isolator and a loop, and record your version with the click. Walkthrough `walkthroughs/M3.md` §1–10.


## M3 part 6 — build 0.3.5 (22 Oct 2026): practice log
Build-verified; unit-tested; not device-verified. Tests 275/275 (core 167, data 73, audio 24, app 11). Schema v9.
- **Practice timer** (Tools; pinnable in the top bar):
  - START, PAUSE, GO ON, STOP & LOG.
  - Attach the time to the recording in the player, the open project, or an exercise you type.
  - Optional note; history with ✕ (and UNDO); today and 7-day totals.
  - [Claude] Wall-clock timer saved as it changes, so closing the app does not lose it. Under 5 s is not logged.
- **Schema v9 `practice`** (started, seconds, scrap?, project?, exercise?, note). Deleting the scrap or project keeps the time (the link clears).
  - Unit-tested, including the 8→9 migration and the timer arithmetic.
- No structured session builder (plan §7). The curated Exercises come at the end, with content chosen by Christopher.

## M3 part 5 — build 0.3.4 (22 Oct 2026): isolator and note finder; imports stay stereo
Build-verified; isolator DSP host-tested (8/8, MSVC, `audio/src/test/cpp/isolator_check.cpp`); core unit-tested; **not device- or ear-verified**. Tests 270/270 Gradle (core 167, data 71, audio 24, app 8) + 8 host checks.
- **Imports keep stereo** [Claude, changes my earlier §3.1 reading]: a stereo song is written as 24-bit stereo 48 kHz (first two channels); mono stays mono. The isolator's stereo position needs it. Recordings from the mic stay mono. The stack, conform and waveforms mix stereo to mono as before.
- **Isolator** (`audio/src/main/cpp/isolator.h`), run on the player's reader thread before the stretcher:
  - STFT of 2048 points with a 512 hop and sqrt-Hann windows on both sides.
  - **Stereo position:** keep or remove what's panned around a place, with a set width, from each bin's L/R balance; soft edges.
  - **Tone ⇄ hit:** median filtering across 17 frames (tone) and 17 bins (hit), soft masks (Fitzgerald, DAFx 2010).
  - **Band:** low and high cuts with a third-of-an-octave ramp.
  - **Live:** settings apply within one hop. ISOLATING ⇄ BYPASSED reloads the take at the same place, crossfaded.
  - **Host checks:**
    - Neutral settings null against the mono mix (2.5e-6), also after a jump back.
    - The 400 Hz low cut leaves 0.01% of 100 Hz and keeps 2 kHz.
    - Keep-centre removes a hard-left sine; remove-centre removes a centred one.
    - Hit-only leaves 0.4% of a steady sine; tone-only keeps 99.8%.
  - **Presets** (starting points): bass, lead vocal (centre), remove the centre, left side, right side, drums, everything but drums, kick. [Claude] values.
- **Note finder:**
  - Every 250 ms it takes the 16384 frames (0.34 s) before the playhead, isolated when the isolator is on.
  - Each note's strength is a harmonic sum over 5 harmonics, counted only when its own fundamental is present, minus what the octave below explains.
  - It shows the strongest notes spelled in the tuner's key, a piano (E1–C7) lit by strength, and dots on the fretboard of the tuner's tuning (bass standard when "any").
  - Unit-tested: FFT bin; a bass E1 with a weak fundamental reads E1, not E2; an A major triad finds A, C♯, E; silence finds nothing.
- **[measure]:**
  - Isolator CPU on the S22 Ultra, alone and with the stretcher at 0.5×.
  - Whether the isolator plus the note finder can pick out a real bass line from a mixed song (plan §13: learn a bass line at 0.6× with the isolator and loop).
- **Not yet in M3:** practice log, drum charts.

## M3 part 4 — build 0.3.3 (22 Oct 2026): playback speed, marks, recorder markers
Build-verified; unit-tested where marked; **not device- or ear-verified**. Tests 265/265 (core 162, data 71, audio 24, app 8). Schema v8.
- **Engine player rewritten** (`engine.cpp` Player):
  - The take is decoded once into memory, shared with the stack and with the player it replaces.
  - A reader thread runs Signalsmith Stretch (streaming: `outputSeek` at the start, at seeks and at each loop pass) and pushes frames tagged with their source frame, so the position is always a place in the take.
  - At 1× and no transpose the take is copied straight through.
  - Changing a setting starts a new player at the same place; the output callback crossfades old to new over one block (linear: the same material).
  - Loop seams fade out and in over 10 ms each (different places in the take, so no overlap).
- **Playback speed** (Tools, and pinnable in the top bar), on the take in the player (▶ one in the Library, or import a song):
  - Speed 0.25–2× (menu plus ±0.05), pitch ±24 semitones, fine tune ±100 cents in 5 ¢ steps.
  - Waveform with the loop shaded and marks; tap to play from there; −5 s / ▶ / +5 s.
  - **A/B loop** (SET A, SET B at the playhead, CLEAR).
  - **Step-up loops:** +0.05 or +0.10 each pass, up to 1.00× or 1.25×; pass number and current speed shown.
  - **Count-in each pass:** 1, 2, 4 or 8 beats at the recording's BPM × speed. [Claude] Clicks synthesized into the stream, first one accented; it needs the recording's BPM.
  - **Marks:** + MARK HERE, SAVE LOOP; tap a mark to go there, tap a saved loop to loop it, ✕ to delete.
  - The player bar shows the speed when it isn't plain.
  - `audio/Playback.kt` is unit-tested: transpose factor, plain/loop, label, ranges.
- **Marks** (schema v8 `take_mark`: take, frame, end frame for loops, label): stored with the take, deleted with it. Unit-tested, including the 7→8 migration.
- **Recorder:**
  - **◆ MARK** while recording drops a marker; markers are saved on the take as "Marker 1, 2…".
  - **Clipping warning:** "CLIPPING · MOVE BACK OR PLAY SOFTER" shows for 2 s after the input peaks at ≥ 0.98.
- **[measure]**:
  - Stretcher CPU on the S22 Ultra at 0.25× and 2×.
  - How long a speed change takes to be heard.
  - Count-in timing against the loop.
- **Not yet in M3:** isolator with note finder, practice log, drum charts.

## M3 part 3 — build 0.3.2 (22 Oct 2026): common drum grooves in the Reference
Build-verified; unit-tested where marked; not device- or ear-verified. Tests 259/259 (core 162). Plan §8 updated with Christopher's ask.
- **Learn › Reference › Drums › Grooves:** 17 grooves in five styles:
  - **Rock and pop:** rock beat, rock beat with a kick on the "and" of 3, 16th-note hat, half-time, ride with a crash on 1 (2 bars), fast punk.
  - **Funk, soul and R&B:** Motown backbeat, funk with ghost notes.
  - **Shuffle, blues and jazz:** shuffle, 12/8 slow blues, jazz swing (ride "ding, ding-a ding", hat foot on 2 and 4, feathered kick).
  - **Dance and hip-hop:** disco, boom bap (swung 58%), trap (32nd hat roll).
  - **Reggae, country and Afro-Cuban:** reggae one drop, country train beat, 6/8 bembé bell.
  - [Claude] They're generic textbook versions, not transcriptions.
- **Each page:** drum tab, a row per drum and a column per step, with beat numbers and bar gaps; marks are x, X (accent), g (ghost) and o (open hat). The step being played is lit. There's a tempo menu (the style's typical tempo marked) and **▶ HEAR IT**: 8 bars on a synthesized kit, rendered once per tempo into the cache.
- **Core `tools/Drums.kt`:**
  - `DrumGrid` (drum tab, validated row lengths; meant to be the model for drum-chart scraps).
  - `Groove` (swing, step timing, render).
  - `DrumKit`: kick (falling sine), snare, closed/open/foot hat (an open hat is cut by the next hat hit), ride, crash, toms, cross-stick, bell.
  - Unit-tested: every groove a whole number of bars, rock tab, jazz ride steps, swing timing and the lit step, render length and downbeat, bad tab refused.
- **Christopher:** check the grooves read and sound right to you; say which styles are missing.

## M3 part 2 — build 0.3.1 (22 Oct 2026): drum rudiments are Reference material
Build-verified; unit-tested where marked; not device- or ear-verified. Tests 253/253 (core 156).
- *Christopher, 22 Oct 2026: "I didn't mean for the click. I meant as reference material so drummers are included. Those are still useful for hearing what they sound like."* Plan updated: the rudiments are in §8 (Reference), not §7.
- Taken off the metronome (no drum pattern row there now).
- **Learn › Reference › Drums:** the 40 PAS rudiments by family. Each page shows the sticking (accents, grace notes, buzz) and has **▶ HEAR IT** at 40–200 BPM, the stroke being played lit.
- [Claude] **Sound:** a synthesized snare (high-passed noise plus a 185 Hz body), accents at full level, others at half, flam grace 30 ms early, drag graces 30 ms apart, buzz as five bounces. Four passes are rendered once per tempo into the cache (`cache/reference/`, evictable) and played in the persistent player. Unit-tested: stroke spacing, accent level, flam timing.
- The rest of Learn (Course, Ear, the theory Reference) still arrives in M6; the screen says so.

## M3 part 1 — build 0.3.0 (22 Oct 2026): metronome, drum patterns, tuner, drone, top-bar tool slots
Build-verified; core unit-tested; **nothing here is device-verified or ear-verified**. APK 36.9 MB, sent zipped (12.6 MB), copy in `dist/`. Tests 251/251 (core 154, data 69, audio 20, app 8).
- **Christopher's new asks (22 Oct 2026), added to the plan:** drum patterns ("paradiddles and all that", plan §7 row *Drum patterns*) and drum charts as a fragment (plan §3.1). The rudiments are in this build; **drum charts are not built yet** (next: a new scrap format, drum tab grid per bar, notation view with M5).
- **Metronome (plan §7, C4 'metro'),** core `tools/Metronome.kt` + engine `renderClick`:
  - Tempo 20–400: drag sideways on the number, tap it to type, − / + / ±5, TAP (median of the last 8 taps, reset after 2 s).
  - Meters: 4/4 3/4 2/4 5/4 6/8 9/8 12/8 2+3/8 3+2/8 2+2+3/8 3+2+2/8 3+3+2/8 and Custom (any `a+b+c/d`).
  - [Claude] What the BPM counts: the beat in simple meters, the dotted beat in compound meters, the denominator note when the beats are unequal (7/8 as 2+2+3 counts eighths).
  - Accent per beat (tap a beat light: strong › accent › normal › silent); subdivision 1–9 (incl. 5 and 7); swing 50–75% on even subdivisions; polyrhythm layer (n evenly across the bar, own sound); count-in 1–2 bars; tempo trainer (±n BPM every m bars up to a limit); silent bars (play n, mute m, the count goes on); click volume.
  - Engine: the bar advances in BPM units at the current bar's tempo, so tempo changes and the trainer apply from the next frame without restarting the bar; clicks start on the first frame at or after their time (sample-accurate). Programs swap atomically, freed two callbacks later.
  - **Beat grid saved with takes** (`take.metronome_grid`, versioned JSON `ClickGrid`): every beat heard during the take in the take file's frames, downbeats and count-in flagged. Output and input `getTimestamp(CLOCK_MONOTONIC)` put each click where the mic heard it ("aligned"); if a stream reopened mid-take or timestamps fail, frames are matched by count and the grid is marked unaligned. **[measure]** alignment against a click recorded through the mic.
  - Settings saved in app_setting and restored at launch; the top bar's − / + / Tap change the same metronome.
- **Drum patterns (moved to Learn in 0.3.1):** the 40 PAS International Drum Rudiments (1984 list, PAS numbering and families) as stickings over the click, the stroke being played lit, accents (>), flams and drags (grace letters), buzz (~). [Claude] Rhythm is simplified to equal slots per click (rests lengthen notes). Stickings were typed from the PAS sheet; the mill (RRLR) and the single dragadiddle were checked against text sources online, the PAS PDF itself couldn't be read here. Checking 8 (six stroke), 23 (flamacue), 27 (pataflafla), 30 (flam drag) and the roll rhythms is on hold: Christopher isn't a trained drummer (22 Oct 2026).
- **Tuner (C4 'tuner'),** core `tools/Tuner.kt`:
  - YIN in two passes: the octave is chosen on a decimated copy, the period refined at 48 kHz (unit-tested: E1, drop-D D1, a bright 110 Hz sawtooth and 1318 Hz all within 1 cent; 196 Hz under noise within 2 cents).
  - Median of the last five readings; holds the note ~0.4 s.
  - A4 415–446; temperaments equal, just (5-limit), Pythagorean, ¼-comma meantone relative to a tonic; notes spelled in a chosen key (scale notes as the scale spells them, others by the key's sharps or flats); octave numbers C4 or C3; MIDI number and Hz shown.
  - "Listen for": any instrument, voice, or one of the 13 tunings, which only narrows the search (2 semitones under the lowest string to 2 octaves over the highest). It never names strings (no string mode).
  - Mic: opened while the tuner is on screen, no pre-roll; the chip says TUNER · MIC ON; released in the background.
  - **[measure]** drop-A / low B on the phone mic.
- **Drone:** any root and octave, fifth, major/minor third, octave above; [Claude] intervals tuned pure (3:2, 5:4, 6:5) over a root that follows the tuner's A4 and temperament; sustained tone (sine + 2nd and 3rd harmonics, 200 ms fades). The piano drone waits for the piano sampler (M5).
- **Top bar:** TOOLS ▾ between CLICK and ● REC holds the two tools pinned in Tools (default Tuner and Drone; the drone starts and stops right from the menu) plus All tools. CLICK ▾ gains "Full metronome".
- **Not yet in M3:** playback speed, isolator with note finder, practice log, recorder markers and the clipping warning, drum charts.
## M1 part 1 — build 0.1.0 (22 Oct 2026): real screens in the C4 look
Build-verified; unit-tested where marked; **nothing here is device-verified yet**. APK 35.3 MB, sent zipped (12.0 MB), copy in `dist/`. adb showed no phone.
- **Shell (C4):** project/mic bar, CLICK + ● REC bar, the screen, the persistent player bar, five modes. JetBrains Mono and Noto Music bundled (SIL OFL, licences in `app/src/main/assets/licenses/`).
  - The C4 TOOLS ▾ and the project switcher are left out until their features exist (M3, M2), so no control does nothing.
  - TOOLS, LEARN and AI say what arrives and in which milestone.
- **Engine** (`audio/src/main/cpp/engine.cpp`):
  - One output stream mixes take playback (decoded ahead by a reader thread into an SPSC ring) and a sample-accurate click, accented on beat 1 of 4.
  - One input stream (UNPROCESSED when the phone reports support, else VOICE_RECOGNITION) feeds a 30 s ring.
  - The recorder's writer thread streams it to `take-<ms>.wav.part` (24-bit mono 48 kHz), fsyncs every 5 s, and on stop fixes the header, fsyncs and renames.
  - Callbacks allocate nothing, lock nothing, log nothing.
  - Players are swapped atomically and freed two callbacks later.
- **Mic:** Off closes the stream. Listening keeps it open, so ● REC includes up to **10 s of pre-roll** [Claude: length]. The pre-roll stays in the file; the take stores where ● REC fell (schema v2 `take.preroll_frames`). Listening turns off when the app leaves the screen; recording carries on through a `microphone` foreground service.
- **Saving:** the take is written to the database the moment you stop. The save sheet (title, tags, BPM, key) only adds to it. With the click on, BPM comes in marked CLICK.
- **Recovery:** at launch, a `.part` left by a crash or force-stop is finished (sizes from the file length, torn last frame dropped) and saved as "Recovered take", tagged "recovered". A finished file the database never got is saved the same way. The 30-day trash purge runs at launch too.
- **Library:**
  - Search by title or tag. Sort by captured, edited, name or tempo (no tempo last).
  - Filter: type, tags (include/exclude, ANY/ALL), key (+ unknown), BPM range (½·2×, + free time), starred only. OR within a property, AND across (plan §3.6).
  - Rows show type, title, tags, takes/★, BPM, key and ▶.
  - The trash has RESTORE, and every delete gets UNDO.
- **A recording's full view:**
  - Waveform (tap to play from there) with the pre-roll mark. TAKE ▾ switches, stars, records the next take, or deletes.
  - Tags picked from your own (most used first) or made new. BPM: single, range, free time or unknown. Key: 21 tonics × all 50 catalog scales, spelled as picked. Your entries are marked YOURS.
- **Crash log:** an uncaught crash is written with the last 24 actions and shown with COPY on the next launch.
- **Tests:**
  - **core 107/107**, including KeyMode 7: tokens round-trip for all 21 tonics × 50 scales.
  - **data 42/42**, including the v1→v2 migration through Room's MigrationTestHelper, Library rows, take letters, and LibraryQuery 11.
  - **audio 16/16**, including Wav 6: 24-bit decoding, crashed-header length, repair keeps every audio byte, a non-WAV is refused.
- **Unverified on device:**
  - recording with the screen locked;
  - the foreground service on Android 16;
  - force-stop recovery;
  - click timing and lamp sync;
  - pre-roll;
  - playback after the output stream was closed in the background;
  - Bluetooth route changes;
  - interruptions (calls, other apps);
  - the fonts at the S22 Ultra's density;
  - how the menus look.
- **Known limits:**
  - No interruption or route-change handling yet (a disconnected stream is reopened on the next play/record).
  - Playback assumes 48 kHz files, which all of the app's own takes are.
  - "Key: non-tonal" isn't offered, only unknown.
- **Rest of M1:** widget and app shortcuts, backup/restore, multi-select and Groups UI, mic permission control inside the app, and interruption handling.

## M1 part 2 — build 0.1.1 (22 Oct 2026)
Build-verified; unit-tested where marked; **not device-verified**. APK 35.3 MB, sent zipped (12.1 MB), copy in `dist/`. Permissions: RECORD_AUDIO, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE (plus androidx.core's app-private receiver permission). No INTERNET.
- **Widget and shortcut:** a home-screen ● REC widget and a long-press **Record** shortcut. Both open the app, which starts a Library take in `onResume`, because Android 14+ only lets a microphone service start from a visible app. The foreground-service start is guarded: if Android refuses, the take still records while the app stays open. Lyrics, Tuner and Metronome shortcuts join when those screens exist.
- **Interruptions:**
  - Audio focus is taken when playback or the click starts; losing it to another app pauses, with a notice.
  - Headphones out ("becoming noisy") pauses playback and the click.
  - A route change that closes the output pauses the take where it was (play resumes it) and reopens the stream for the click.
  - If the system takes the mic mid-take (a call, another app), the take stops and is saved, with a notice. If it takes the mic while Listening, Listening turns off.
  - Not detected: a call that leaves the stream open but feeds silence.
- **Backup and restore (plan §11), no permissions:**
  - LIBRARY ⋯ → Back up now / Backup folder / Restore from a backup. The folder comes from Android's picker and is remembered.
  - A backup is one zip: a versioned manifest, a consistent database snapshot (SQLite `VACUUM INTO`), and every take stored uncompressed. A failed write deletes the half file.
  - Restore unpacks to a temp folder and checks everything before touching the phone: format, version, schema not newer than this build, every listed take present at its size, zip CRCs, and no paths outside the folder.
  - It then shows a summary (date, recordings, size, what it replaces). On RESTORE it moves the current data to `files/before-restore` (kept until the next restore), swaps the backup in, and asks you to close and reopen the app.
  - If any swap step fails, the phone's own data is put back.
  - [Claude: placement under Library ⋯ until a settings screen exists.]
- **Mic control in TOOLS:** shows the mic state and the permission. Allow; on Android 13+ revoke (`revokeSelfPermissionOnKill`, which takes effect when the app closes).
- **Tests:** **173/173**: core 107, data 42, audio 16, app 8. The app's 8 cover the backup archive: round trip byte for byte, empty Library, newer schema refused, older accepted, truncated file refused, zip-slip refused with nothing written outside, non-backup zip refused, name rules.
- **Unverified on device:**
  - widget and shortcut starting a take from a closed app;
  - restore on a wiped install (the plan's M1 "done when");
  - `VACUUM INTO` on the phone's bundled SQLite;
  - focus loss, headphone pull and a call during a take;
  - how Samsung's launcher draws the widget.
- **Still to do in M1:** multi-select (long-press plus a visible Select), Groups (make, add, filter, one-tap removal from other Groups when used in a project, which waits for M2 projects), scheduled backups (needs WorkManager; manual only now).

## M2 part 6 — build 0.2.5 (22 Oct 2026): lyric line sync; M2 code-complete
Build-verified; sync storage and line lookup unit-tested; the view is source-only. APK 36.5 MB, sent zipped (12.5 MB).
- **Sync (plan §4.4):**
  - Play a recording, open a lyric, then ⋯ → **Sync lines to the recording playing**. The take restarts from the top, and a big bar says TAP AS LINE n OF N STARTS. STOP keeps what was tapped.
  - Stored per lyric and take: schema v7 `lyric_sync`, one frame per line of words, cascading with either side.
- **Follow:** while a synced take plays, the line being sung is lit and kept a third of the way down. ⋯ has **Sync earlier / later** (0.1 s steps) and **Remove this sync**. An unsynced lyric keeps the steady auto-scroll.
- **[Claude]** Sync counts lines of words in order, so editing the lyric's line count shifts the sync. Re-sync after big edits.
- **M2 is code-complete.** Plan §13 "done when": build a stack of three recordings in different tempos, sing over it reading a lyric on the Board, keep takes B and D. Every part exists; none of it is device-verified.
- **Not in M2 yet:**
  - Stand mode (§4.4).
  - Tab/notation cursor scrolling (M5 with written music).
  - Loop/trim handles on layers.
  - Naming and duplicating stacks; stack export.
  - Chord parser and transposition (§6.2).
  - Tap-to-place chords.
- **Tests: 224/224** (core 127, data 69, audio 20, app 8).

## M2 part 5 — build 0.2.4 (22 Oct 2026): overdub latency calibration
Build-verified; the detection is the Device Lab's (unit-tested 10/10 in M0); **the whole path is source-only on the phone**, and how well it holds is [measure] (§14.2: ±5 ms after calibration). APK 36.5 MB, sent zipped (12.4 MB).
- **Tools → OVERDUB LATENCY (plan §5.4):**
  - Lists the phone speaker, wired/USB headphones and Bluetooth, with the measured round trip for each or "not measured"; the current route is marked.
  - **MEASURE ON <ROUTE>** plays five noise bursts and the mic times them. Cross-correlation, median of the bursts that clear the confidence floor. It shows the result with its spread.
  - For headphones the instruction is to hold an earbud against the phone's mic.
  - Before measuring, everything sounding stops and the engine's output closes (`nativeReleaseOutput`), so the calibration's own duplex streams never share the device with it. The mic has to be off.
- **Applied:**
  - A take recorded over the stack has its layer start moved earlier by the current route's round trip.
  - The amount is stored with the take, in the `take.latency_frames` column that has existed since schema v1 (no migration).
  - On an uncalibrated route the take is placed as before, with a notice pointing to Tools.
  - Values are kept in app_setting `latency.speaker|wired|bluetooth`.
- **[Claude] Route:** Bluetooth beats wired, which beats the speaker (Android's media routing order).
- **Not exact yet [measure]:** at ● REC the stack position and the input ring are read on the control thread, a few ms apart at most. Whether the whole chain lands within ±5 ms needs the phone.
- **Tests: 223/223**, unchanged. This piece is device plumbing over already-tested detection.

## M2 part 4 — build 0.2.3 (22 Oct 2026): conform and importing
Build-verified; conform math, resampler and WAV writer unit-tested; **the render, decoder and import paths are source-only** (MediaCodec and Signalsmith run only on the phone). APK 36.4 MB, sent zipped (12.4 MB).
- **Stretcher: Signalsmith Stretch** (Christopher, 22 Oct 2026: "Signalsmith. This is for getting the structure, not perfect quality recordings from your phone."). Vendored unmodified with its dependency Signalsmith Linear 0.6.4, both MIT, at `audio/src/main/cpp/third_party/` (README there). Plan §12 updated. **Licence notices** for both must be added to the app's notices at M7.
- **Conform (plan §5.3), `core/catalog/Conform.kt`:**
  - **[Claude] Tempo:** the layer's BPM (the middle of a range) against the stack's, taking whichever of straight, half-time or double-time needs the least stretch.
  - **[Claude] Key:** the layer's home note moved to the stack's by the shortest way, with a tritone going down. The mode isn't compared, so A minor into C major moves up 3.
  - Free-time layers, unknown tempo and unknown key change nothing.
  - The label reads like "+2 st · 92→96 BPM" or "180→96 BPM (½×)".
- **Stack tempo/key:** the header button "96 · D DOR ▾" opens a sheet. Left empty, they follow the first layer that has them, so the first layer never moves.
- **Per layer:** a line under the waveform shows CONFORMED / RENDERING… / CONFORM OFF / TOO SHORT TO STRETCH, with its amounts. Tapping it turns conform off or on (schema v6: `stack_layer.conform`, default on).
- **Renders (`audio/Renders.kt`):**
  - Made on their own worker with Signalsmith `exact()`, which compensates latency and gives an exact output length.
  - The whole take is rendered, so loop points and trim scale with it.
  - Cached in the app's cache folder: **[Claude]** 512 MB cap, oldest-used evicted, keyed by source, amounts and stretcher version.
  - Until a render is ready the layer plays as recorded; the stack swaps it in without touching the other layers.
- **Importing (plan §3.1 now says so; Christopher, 22 Oct 2026: "You also need to be able to add files from your phone as fragments"):**
  - Library ⋯ → **Import from the phone** goes to the Library; Board ⋯ → **Import from the phone** goes into that project. You can pick several files at once.
  - **Audio:** anything Android's MediaCodec decodes (MP3, AAC/M4A, FLAC, Ogg/Opus, WAV, AMR, a video's sound track). Mixed to mono, converted to 48 kHz (**[Claude]** cubic Hermite, averaged first when coming down from higher rates), written as a 24-bit take titled with the file name and tagged "imported".
  - **Text:** .txt/.md become text notes; .cho/.chopro/.chordpro/.crd/.pro become lyrics.
  - MIDI files are refused with a note: they arrive with written music (M5).
  - A single import opens straight away. An import cut off by a kill leaves only a `.wav.importing` file, deleted at the next launch.
  - **[Claude]** The original file isn't kept. Stems (M4) will want stereo, so re-import then or keep originals: decide at M4.
- **Tests: 223/223** (core 127, data 68, audio 20, app 8). New:
  - Conform: tempo, key, shortest shift, half/double-time, ranges, unknowns.
  - Resampler: 44.1k→48k pitch and duration, chunk-size independence, 96k→48k.
  - The WAV writer's output read back by `Wav.info`.
  - Migration 5→6.

## M2 part 3 — build 0.2.2 (22 Oct 2026): stacks
Build-verified; data unit-tested; **the mixer is source-only** (no host build for engine.cpp tests; ear-check on the phone when convenient). APK 36.2 MB, sent zipped (12.3 MB).
- **Schema v5:** `stack` (project_id null = the temporary stack) and `stack_layer` (scrap, chosen take or null = starred/newest, mode loop/one-shot/free, start on the stack timeline, from/to region, gain dB −60..+12, mute, solo, order). One stack per project for now ("Stack 1"); naming and duplicating (§5.5) later.
- **Engine (`engine.cpp`):**
  - **[Claude]** Layers are decoded whole into RAM as mono float, about 192 KB per second. A cache by path means a rebuild never decodes twice.
  - The stack mixes sample-accurately on its own timeline. Gain, mute and solo ramp across each block, so they don't click. Stop fades out over one block.
  - Loop seams get a **linear 10 ms crossfade**: the two sides are correlated, per the audio-crossfade-laws skill. There are three cases: tail into the pre-loop frames, head into the post-loop frames, and a whole-take loop.
  - **[Claude]** The whole stack repeats at its longest layer.
  - Layers whose file and timing are unchanged are kept across edits, so changing one layer never restarts the others.
  - Mixes are swapped atomically and retired after two callbacks, like the player.
- **The stack's ● (confirmed 21 Oct):** **[Claude]** renders one pass offline at the current gains, mutes and solos, not in real time. It's saved as "Stack 1 mix", tagged "stack mix", in the stack's project and the Library, and opens the save sheet. A notice says if the mix clipped.
- **Record over the stack (§5.4):** the top ● REC while the stack plays records a new layer. The overlay says TO STACK 1 · LOW TIDE · NEW LAYER, and warns when the stack is on the speaker and will leak into the take (no wired, USB or Bluetooth headphones). The take goes to the project and the Library and becomes a one-shot layer starting where ● REC fell, minus the pre-roll. **Not latency-compensated yet** (calibration is the next M2 piece).
- **UI:**
  - **+ STACK / IN STACK** on recording windows.
  - The stack workspace opens from the player bar. It covers the screen area only, so the top ● REC stays in reach. Each layer has its mode menu, − / dB / +, M, S, and ⋯ (start at the top, remove with UNDO); a waveform shows the region played. There's a tap-to-seek position line, ▶ PLAY / ■ STOP and ● BOUNCE, and ⋯ (add a layer, bounce, take it out of the player).
  - **Add a layer** opens the Library in pick mode filtered to recordings; your filter comes back after.
  - Library ACTIONS → **Add to the player**: the loaded stack, else the open project's stack, else the temporary stack.
  - Replacing a loaded stack with another **asks first** (§5.5).
  - The player bar shows the stack, plus a slim row for a take being previewed over it.
- **Not yet:**
  - Conform (tempo and key) needs the stretcher choice, Rubber Band (GPL) vs Signalsmith Stretch (MIT): a plan [measure] item.
  - Loop point and trim handles (the data and engine support them; there's no drag UI).
  - Swipe-to-delete and long-press-to-solo (they're buttons for now), choosing a layer's take, naming and duplicating stacks, stack export.
- **Tests: 212/212** (core 121, data 67, audio 16, app 8). New: one stack per project plus the temporary stack, layer order and take choice (chosen, starred, newest, fallback when trashed), sanitized settings, trashed recordings dropping out, the project-stack scrap query, cascade on project purge, migration 4→5.

## M2 part 2 — build 0.2.1 (22 Oct 2026): lyrics, text notes, chord charts
Build-verified, unit-tested; not device-verified (Christopher, 22 Oct: he won't test every small update; build the scaffold, sand it later). APK 35.9 MB, sent zipped (12.2 MB).
- **Storage:** ChordPro subset (chordpro.org v6) in `scrap.body`: `[Dm7]` inline chords over the following words, `{c: Verse 1}` / `{soc}`-style section headings; other directives ignored as lines. Chord charts: `| F / C / | G / Dm7 / |`, one token per beat, `/` holds, `%` repeats the bar, `{time: 6/8}` / `{time: 2+2+3/8}` sets the meter; wrong beat counts are reported, never changed. Chord names stay as typed in ASCII; shown with ♭/♯ (`core/sheet`). Schema v4 (scrap.body, project_ref.body).
- **[Claude] Project copies (plan §4.2):** a lyric/text/chart added to a project from elsewhere gets the project's own copy **when it is added** (the plan's wording; Christopher didn't pick between copy-on-add and copy-on-first-edit). One written in a project is that project's original: editing it there edits the scrap itself. The editor's header says LIBRARY, the project, or "<PROJECT>'S OWN COPY". Changing to copy-on-first-edit is one DAO function (`ProjectDao.addRefs`).
- **Write:** Board ⋯ → Write lyrics / text note / chord chart (to the project), WRITE ▾ on empty and no-project Boards, Library ⋯ → Write … (to the Library). A new one opens in its editor.
- **Editors:** lyrics view with chords over the words (monospace alignment; a chord inside a pushed word gets a hyphen), ▶ SCROLL / SPEED 1–6 ▾ (**[Claude]** 6–46 dp/s) / ⋯ (text size S–XXL, chords shown/hidden, back to top). EDIT opens the source with keys for [ CHORD ], SECTION (charts: | / % SECTION METER) and UNDO/REDO (Compose TextFieldState undo). Text notes open straight in the editor. Charts show bars two to a row, a copper outline on bars whose beat count doesn't fit. Saves as you type (0.5 s) and on leaving. ⋯ has Tags, Key, BPM, Groups, Projects, Delete.
- **Board windows** show the words, the note or the first bars; Library rows title written scraps by their first words; **Library search finds words inside lyrics and notes.**
- **Tests: 207/207** (core 121, data 62, audio 16, app 8). New: lyric parsing, chord-over-words alignment incl. pushing and hyphen, section directives, ♭/♯ display rules, chart bars/hold/repeat/meter/problems, written scraps in and added to projects, copy independence, recordings never copied, body search, migration 3→4.
- **Not yet:** transposing and checking chords (needs the chord-symbol parser, plan §6.2), lyric line sync to a recording and Board scrolling with playback (§4.4, later in M2), tap-to-place chords on words (typing `[ ]` for now).

## M2 part 1 — build 0.2.0 (22 Oct 2026): projects and the Board
Build-verified, unit-tested; not device-verified. APK 35.7 MB, sent zipped (12.2 MB). Walkthrough: `docs/walkthroughs/M2.md`.
- **Projects (plan §4.1, §2.3):** the top-left PROJECT ▾ on every screen: switch (opens its Board), New project (a name is required, no fake default), Rename, Delete (to trash with UNDO; Board and settings come back with it; restore also from Library ⋯ → Trash). Launch reopens the last Board (app_setting `last_project`). With no project open the Board offers New project / open one / ● RECORD (to the Library) / LIBRARY.
- **Recording into a project (§10.1):** Board ⋯ → Record into <project> (or ● RECORD HERE on an empty Board). The overlay says TO <PROJECT>; the take lands on the Board and in the Library. The save sheet's **GOES TO** row toggles "<project> + Library" / "<project> only" (out of Library search). The top ● REC still records to the Library (§2.4), as in C4.
- **Add to project (§3.3, §4.2):** Library ACTIONS ▾ → Add to project; a recording's full view has a **PROJECTS** row; Board ⋯ → Add from the Library opens the Library in pick mode with an ADD n button. Tap a project they're all on to take them off (UNDO restores the settings and window). References only, never copies; rows show "in Low Tide, Porch Light".
- **The Groups offer (§3.5):** after adding scraps that are in Groups, the bar offers **REMOVE FROM GROUPS** (live Groups only), itself undoable.
- **Board windows (§4.3, C4):** each scrap is a window with type, title, waveform glimpse (newest starred take, else newest; shows play progress), BPM · key · takes, tags. Tap opens it full size. **[Claude]** Hold then drag moves a window (a plain drag scrolls the Board); the corner drags to resize; the window moved last sits on top. New windows take the first free cell of a two-column grid and never cover a placed one; placement is saved per project (schema v3: board_x/y/w/h/z on project_ref).
- **Schema v3** (AutoMigration 2→3, tested with MigrationTestHelper). Taking a hidden project recording off its own Board puts it back in Library search so nothing becomes unreachable.
- **Tests: 188/188** (core 107, data 57, audio 16, app 8). New: recording into a project, show-in-Library switch, add counts only new refs and never moves a scrap out of another project, window placement leaves settings alone, glimpse take choice, switcher order and trash/restore, leaving live Groups only with undo, project names on rows, take-off unhiding, migration 2→3, grid placement and clamping.
- **Not yet:** lyrics/text/chord windows and their editors, "Write" on the empty Board (next build); stacks and + STACK (M2 part 3).

## M1 part 3 — build 0.1.2 (22 Oct 2026): multi-select and Groups
Build-verified, unit-tested; not device-verified. APK 35.4 MB, sent zipped (12.1 MB).
- **Multi-select:** long-press a Library row, or Library ⋯ → Select (a visible route, not only the long-press). Then ALL, DONE, and ACTIONS ▾: tag them all (shared tags removable), Groups, delete with one UNDO. Back ends selecting.
- **Groups (plan §3.5):** make one from a selection or from a recording's full view (GROUPS row), tap to add or remove, ✕ to delete (to trash with UNDO; scraps stay). Filter by Group (OR), and UNUSED ONLY (no live project uses it). Library rows now carry live Group ids and a live-project-use count.
- **Christopher, 22 Oct 2026:** save systems and call interruptions need no more work or testing ("Our save systems in other apps have been fine"). **Scheduled backups dropped** from M1.
- **Tests: 176/176** (core 107, data 45, audio 16, app 8). New: Group filter, unused only, and a row showing only live Groups and counting only live projects.
- **Waits for M2:** one-tap removal from a scrap's other Groups when it's used in a project.
- **M1 is code-complete** except that M2-dependent piece. Its "done when" (widget take with the phone locked, find it by search, restore on a wiped install) needs the phone.

### Open questions for Christopher (0.1.0)
- ~~**Pre-roll length**~~ **Answered 22 Oct 2026: none.** Pre-roll and Listening are removed (build 0.3.7).

## Clickable mockups (21 Oct 2026) — published, not yet reviewed by Christopher
- Canvas: https://claude.ai/artifact/KyVeuP8FZo9Hr9HCxF5TkB (private to Christopher). Three tappable phone prototypes (390×844), each covering every main screen: Board, Library + Filter sheet, Tools → Metronome and Tuner, Pitch Trainer question, Theory page (E7♯9), expanded player/stack, quick capture → recording → save sheet. Persistent player and bottom navigation on every screen; mic state always shown.
- References looked at first (Play Store screenshots, 21 Oct 2026): Moises, Songsterr, Ultimate Guitar, BandLab, MuseScore, Pro Metronome, Perfect Ear.
- Directions, which differ in how screens are built, not only in colour:
  - **A · Session** (Moises/BandLab lineage): dark, rounded cards, colour-coded scrap types with text labels, round transport, red capture button.
  - **B · Notebook** (Songsterr/MuseScore/paper lineage): the Board as a dark ruled page with margin labels, serif lyrics, no cards, text-only navigation, one vermilion accent.
  - **C · Console** (hardware/4-track lineage): all monospace, dense grid with hairlines, an LCD status strip (project/mic/tempo/key), numbered keys, one orange accent, record key always in the transport.
- Sample content is invented for the mockups (project "Low Tide", lyric lines, tags), but the music is checked: the tab's note row and Dorian degrees match the frets, and the E7♯9 voicing (x-7-6-7-8-x) is spelled E G♯ D F𝄪.
- Not decided by the mockups, and needing Christopher's call: everything visual. Layout details follow the plan; nothing in them adds a feature.
- Rendering not checked by Claude: the in-app browser isn't signed in to claude.ai.

### Round 2 (21 Oct 2026): C2 · Console, revised — on the same canvas, not yet reviewed
Built from Christopher's feedback on C, now recorded in plan §2.4, §4.3 and §8.
- **Softer palette:** warm off-white text on charcoal and a muted copper accent. Selected states are outlined, not inverted white blocks.
- **Top bar on every screen:** project, mic state, then a metronome strip (click on/off with a beat lamp at the tempo, −/BPM/+, tap) and the Tuner and Drone shortcuts.
- **Board as windows:** a two-column grid of small windows, each showing type, title, tags and a glimpse, plus a + STACK toggle on recordings. Tapping one opens that type's editor:
  - **Lyrics:** full screen, auto-scroll on/off with speed 1–6, play the stack and record over it while reading.
  - **Tab:** string selector, fret pad, durations, note row, and the selected note's other positions.
  - **Recording:** takes A/B/C, waveform, and tabs INFO (tags, BPM/key with their source, takes, usage, recording details), STEMS (mute/solo, stem → stack), MIDI (piano roll), SHEET (bass clef), TEXT (timestamped speech transcript).
- **Trainer** follows the Listen/Practice/Test pattern per lesson, with best scores.
- **Theory** has COURSE (14 units in order, progress; a lesson has text, hear-it buttons, a diagram and a Check-yourself question that leads into the Trainer) and REFERENCE (search, categories, entry with hear buttons, fretboard, "in yours", test link).
- **Research:** `docs/research/beato-courses-2026-10-21.md` covers structure and order only, nothing copied.
- **Fixed in C2:** the first three mockups labelled the bass tab "D standard", but its strings are G-D-A-D, which is drop D (D standard bass is D-G-C-F). C2 says drop D, and its bass-clef notes are the correct written pitches. The first three boards still carry the wrong label.

### Round 3 (21 Oct 2026): C2 updated
- **Top bar:** CLICK, −, BPM, +, TAP, two changeable slots (TUNE, DRONE), and **● REC** for a quick recording to the Library. The mic chip toggles Off and Listening (pre-roll). Tools has a "Top bar slots" setting.
- **Learn → Course** is a book: 14 chapters of short pages, every page open, each with a level gauge (●○○○○) and a level filter that only narrows the list.
- **A lesson page** goes Explain → See → Hear → Try (on your instrument, piano or staff, any start key) → Check by ear → Related (Reference, Ear drill, "in yours"), with previous/next page buttons.
- **Ear** has chapter chips and a scores row. **Reference** entries link back to the Course page that explains them.
- **Sources:** `docs/research/learn-sources-2026-10-21.md`. Open Music Theory (CC BY-SA 4.0) and Schmidt-Jones (CC BY) are usable. Mount (CC BY-NC) is not. Hutchinson (GFDL) and the standard textbooks are for fact-checking only.

### Round 4 (21 Oct 2026): C3 · menus, AI, notation — new artboard beside C2
- **Menus instead of rows of buttons.** The top bar is now three controls:
  - CLICK + BPM ▾ opens a menu: ±, on/off, tap, meter, count-in, full metronome.
  - TOOLS ▾ opens the tools; two can be pinned.
  - ● REC.
  Screens use ⋯ and ▾ menus for everything secondary.
- **Record buttons:** never more than two on screen. Top ● REC records the mic (a new stack layer if the stack is playing, otherwise to the Library). The stack's ● records the stack's own output directly, with no mic. The lyrics view has no record button.
- **Board:** freely arranged windows of different sizes, with a resize corner. New window types for notation and MIDI.
- **Notation editor:** treble-clef melody with lyrics. The selected note shows its function (C5 = ♭7 of Dm7). Input ▾ is piano keys, fretboard, sing/play it, tap the staff, or from a recording. Duration ▾. A two-octave on-screen piano. View ▾ switches notation, tab, piano roll or chord chart.
- **Piano:** D major with right- and left-hand fingering on a lesson page. An E7♯9 piano voicing in Reference.
- **Fingering charts use dots** on a fret grid (E7♯9, x-7-6-7-8-x).
- **AI tools** is its own section (Tools ▾ AI): stems, notes → MIDI, sheet music and tab, chords, tempo/key, speech → text, Performance check. It names each model and shows a job list. Each recording has an AI ▾ menu.
- **Performance check:** reference vs your take on one pitch graph; a score; problems with where, what and why, each with yours / reference / loop. An instrument menu states what each instrument can be checked for. The plan's new §10.6 holds the capability analysis.
- **Library filter** adds:
  - match all/any; type; tags with include *and exclude*; key with "+ unknown";
  - BPM with half/double and free-time handling; meter; has (MIDI, stems, transcript, notation); starred takes;
  - project use; group; source (recorded, imported, AI-made); date; length;
  - saved filters, and "save as…".
- **Learn:** the Course is categories (9 tiles) → topics (each with a level gauge) → pages, not one scrolling index. Reference E7♯9 now has names, intervals with semitones, guitar/piano views, function, fitting scales, substitutes, hear (including in context), where it occurs, your material, the naming rule, and a link to where it's explained.
- **Renamed:** "Slow-down player" → **Playback speed** (plan updated).

### Round 5 (21 Oct 2026): C4 · score editor — new artboard beside C3, not yet reviewed
Built from `docs/research/notation-pianoroll-tab-editors-2026-10-21.md` (plan §10.5a). The Board's notation window is now **Chorus**: one score with three parts (Voice, Piano, Bass), shown four ways from **VIEW ▾**: notation, tab, piano roll, chord chart. **PART ▾** picks the part.
- **Notation:**
  - A duration bar that is always visible: whole to 64th, dot (single/double), rest, tie, tuplet ▾ (3:2, 2:3, 5:4, 6:4, 7:4, any ratio, nested) and voice ▾ (1–4, swap).
  - A row of five menus: INPUT ▾ (piano, fretboard, sing/play, tap the staff, USB MIDI keyboard, from a recording), MODE ▾ (step, re-pitch, rhythm first, real-time, insert), ADD ▾ (14 palette categories, each a submenu: clefs/keys/meters, accidentals, articulations, ornaments, dynamics/hairpins, lines, repeats/barlines, text/tempo/rehearsal, lyrics, chord symbols/diagrams, fingering, noteheads, tremolo, grace notes), EDIT ▾ and SCORE ⋯ (setup, play, follow, text size, export).
  - Voice part: treble with lyrics, chord symbols, mp and a crescendo. Piano part: grand staff with RH and LH fingering. Bass part: bass clef (written an octave up) with the tab staff linked underneath.
  - The input panel follows INPUT: piano keys, a drop-D bass fretboard, or a short explanation for sing, staff, MIDI and recording.
- **Tab:** the bass part in drop D with Guitar Pro-style effects drawn (P.M., slide in from below, hammer-on) and rhythm stems under the tab. EFFECTS ▾ has Guitar Pro's nine categories as submenus. TUNING ▾ and a fretboard showing the selected note's other position.
- **Piano roll** (FL Studio model): TOOL ▾ (draw, paint, delete, mute, slice, select), SNAP ▾ (with snap to scale), chord STAMP ▾, SHOW ▾ (ghost notes, background waveform, scale rows, key labels), TOOLS ▾ (quantize, chop, glue, legato, arpeggiate, strum, flam, limit, flip, transpose, scale levels). Ghost notes of the piano chords, the Hum melody waveform behind, D Dorian rows lit, and a velocity lane (LANE ▾: velocity, release velocity, fine pitch).
- **Left out:** FL's Riff machine, chord-progression generator and Randomize, under the no-suggestions rule. Chord Stamp stays in because you pick the chord yourself.
- **Music checked** (pitches and staff positions computed from note names by a script, then looked at in the browser):
  - **Voice:** A4 A4 G4 F4 | F4 G4 (eighths) A4 A4 C5, chords F C | G Dm7. C5 is the ♭7 of Dm7.
  - **Piano:** RH F-A-C (1 3 5), E-G-C (1 2 5), D-G-B (1 3 5), D-F-A-C (1 2 3 5); LH octaves fingered 5–1.
  - **Bass:** F1 C2 C2 | G1 D1 D1 E1 = low D 3, A 3, A 3 | low D 5, 0, 0, 2.
- **Fixed in C4:** C3's chorus had eight notes for the nine syllables of "show me what you took" (the "you" was missing). C4 adds it, as two eighths on "show me", in the notation, the piano roll and the Performance check reference. C3 is left as it was, for comparison.
- **Where the sources live now:** `docs/mockups/project/` (all artboards and `canvas.json`) and `docs/mockups/gen/` (the C4 generator: `node build-c4.js`). Before this they existed only in a temporary folder.
- **Not checked:** how the whole C4 screen renders in the canvas, since the in-app browser isn't signed in to claude.ai. Checked: the staff drawings in a local preview, and every view × part × input combination plus every menu and submenu in a headless run (72 states, no errors).

### Answers (21 Oct 2026, after round 5)
Christopher: "that's good. 4 yes. fifth mode, leave out." Read as follows; correct me if any is wrong.
- **C4 approved.**
- **Round 4, the stack's ●: yes.** It bounces the stack's own output with the mic off (plan §5).
- **AI is a fifth mode.** Updated in plan §2.1, CLAUDE.md and C4. C4's bottom bar now reads Board · Library · Tools · Learn · AI; Tools keeps only the practice tools, and the AI ▾ menu on each recording stays. Republished as canvas version 13; the change is checked in a headless run, not yet seen rendered.
- **Generators stay out** of the piano roll (plan §10.5a).

### Still open
- ~~**Tab for non-fretted parts**~~ **Answered 22 Oct 2026: no.** "Singing isn't really expressed by tabs." A sung line gets MIDI, sheet music and a chord chart (plan §10.3).

### Open questions for Christopher (from round 2)
1. ~~Trainer and Theory~~ **Answered 21 Oct 2026: merged into one Learn mode.** Navigation is now four modes (Board, Library, Tools, Learn), and Learn has Course / Ear / Reference. Updated in plan §2.1, CLAUDE.md, the music-core skill trigger and C2.
2. **Answered 21 Oct 2026:** not a prescribed course, nothing locked, explains and demonstrates the fundamentals; levels are a suggested complexity gauge. Organised after Beato's course *design*. 3. **Answered:** Board windows are arranged freely. (Original questions follow.)
2. **Who it's for:** the plan said "full depth, not for beginners". The new Theory course starts from the first two notes. I read this as: it starts at zero and goes all the way, with levels as labels, never locks. Correct?
3. **Board windows:** free arrangement (drag to place, sizes you choose) or an automatic grid?
4. **Top bar shortcuts:** C2 has click, BPM ±, tap, tuner, drone. Anything else always there (e.g. recorder, slow-down)?
5. **Course text:** Claude drafts original lessons (plus Open Music Theory, CC BY-SA, with attribution), and the audio is generated from the app's piano samples. OK, or are there sources you want used?

## Device Lab (build 0.0.2, 21 Oct 2026) — build-verified, analysis unit-tested 10/10, not yet run on the phone
- Debug builds only: `app/src/debug` holds the activity (`lab/DeviceLabActivity.kt`) and a debug manifest that adds RECORD_AUDIO and a second launcher icon, "Apophenia Lab". Release builds have neither [Claude: a separate debug-only icon is the simplest way to reach a hidden screen].
- Native side in `:audio` (`cpp/lab_engine.cpp`, Kotlin `audio/lab/LabEngine`): Oboe 1.11 AAudio streams, LowLatency + Exclusive requested (the result reports what was granted, MMAP or not). Callbacks allocate nothing, lock nothing, log nothing.
- Tests on screen:
  1. **Round-trip latency per route:** Oboe `FullDuplexStream` (input read inside the output callback, so the two frame counters stay locked), five 1024-sample Hann-windowed noise bursts 1.2 s apart, found by cross-correlation in Kotlin. Reports each burst, median and spread, plus Oboe's timestamp-based latency estimate per stream, for comparison. Input preset UNPROCESSED when the phone reports support, else VOICE_RECOGNITION. Short input reads are counted, because they would shift the counters.
  2. **Microphone sources:** 6 s each from UNPROCESSED, VOICE_RECOGNITION, MIC (generic). Reports RMS, peak, quietest-100 ms floor, and octave bands 31.5 Hz–16 kHz (Welch, 32768-point Hann). Saves each take as a WAV to Music/Apophenia Device Lab for listening.
  3. **Play + record + click:** a quiet pad plus a sample-scheduled 120 BPM click out, with the mic going through an SPSC ring to a writer thread that writes a 16-bit WAV. Runs 1/10/30 min. Reports xruns per stream, short reads, ring overflow, max callback time, slowest disk write, and battery %, battery temperature and thermal status before and after. Stops if the app leaves the foreground, since screen-off running needs the M1 foreground service.
  4. **Glyphs:** `Paint.hasGlyph` for 𝄪 𝄫 ♯ ♭ ♮ plus an on-screen sample. Bravura comes when the fonts are bundled.
  5. Every run is appended to `files/lab/results.txt`. **Share log** sends it as plain text through the share sheet.
- Tests (`:audio`, JVM): latency detection within 2 frames through a simulated speaker/mic filter, inverted polarity, noise-only null probe stays under the confidence threshold, summary statistics; octave-band calibration (half-scale sine = −9.03 dB in its band), white-noise band sum, noise floor, FFT impulse. The 41 Hz test failed at 8192-point frames (0.9 dB leaked across the 44.5 Hz band edge), so the frame size went to 32768.
- Not in the M0 Lab (each arrives with the feature it measures): tuner on bass (M3), stack layer limit and stretcher (M2), Basic Pitch / Spleeter / Moonshine (M4), widget/shortcut capture with the phone locked (M1).
- APK: 33.9 MB debug, sent zipped (11.2 MB), copy in `dist/`. Not installed over adb (phone not connected at the time).

## Project scaffold (21 Oct 2026)
- Modules: `:app` (Compose), `:core` (pure Kotlin/JVM), `:audio` (Android library, C++17 + Oboe via CMake/prefab, `c++_shared`). Scaffold screen only shows that the three are wired together.
- SDK levels: minSdk 29, targetSdk 36, compileSdk 37 (platform `android-37.0`). ABI: arm64-v8a only for now [Claude: faster builds; add armeabi-v7a/x86_64 when release builds need them].
- Versions (newest stable on 21 Oct 2026, read from Google Maven / Maven Central metadata): Gradle 9.8.2 (wrapper, SHA-256 pinned), AGP 9.4.1 (built-in Kotlin), Kotlin 2.4.21, Compose BOM 2026.09.00, activity-compose 1.13.0, core-ktx 1.19.1, lifecycle 2.11.0, Oboe 1.11.0, JUnit 4.13.2.
- Manifest: no permissions of our own (merged manifest has only androidx.core's app-private receiver permission); `allowBackup="false"` [Claude: Android auto-backup goes to the cloud; the app's own backup is plan §11].
- Tests: `:core` 8/8 pass (SpelledPitch MIDI/pitch-class derivation incl. B#3 = 60, Cb4 = 59) — **unit-tested**. `:app:assembleDebug` — **build-verified**; installed and launched on the S22 Ultra 21 Oct 2026 — **device-verified**.
- Known build noise: Gradle prints one deprecation (`Configuration.setVisible`) from a plugin, not our scripts.

## Core music types (`:core`, 21 Oct 2026) — unit-tested, 100/100 pass
- Package `com.betterappmafia.apophenia.core`:
  - `pitch/`: Letter, Accidental (ASCII `bb b n # x`), PitchName, SpelledPitch (frequency with A4 ref + cents), PitchClass with the default table, OctaveLabel (C4/C3).
  - `interval/`: Interval (number, semitones), derived Quality, letter-based `+`/`-`.
  - `degree/`: DegreeToken.
  - `spelling/`: Spelling.spell, which respells the root when a triple accidental would be needed; `keepRoot` forbids that.
  - `catalog/`: 50 scales (28 modes generated by rotation) and 41 chord qualities with omitOrder/essential.
  - `chord/`: the 33 MusicXML kinds, DegreeEdit with the spec's add-relative-to-dominant arithmetic, Chord (root or numeral, bass, polychord, typed text), and QualityMapping between catalog quality and kind + degrees.
  - `rhythm/`: Rational, Duration (dots, nested tuplets, 960 ticks per quarter), TimeSignature as additive groups, Tempo with beat unit, Swing.
  - `instrument/`: Tuning in string order, Instrument (capo, frets, written vs sounding), TabNote, 13 presets.
  - `transpose/`: Transpose.chord, which reports theoretical respellings.
- Tests:
  - Every catalog scale and chord on all 35 roots spells with the right pitch classes and letters, and respells only when a triple accidental is unavoidable.
  - Catalog degrees are cross-checked against the research doc's semitone columns.
  - All the skill's spelling traps (Bbb in C°7, Fx in E7#9, D# in C7#9, B# in G# major, Ger+6, tritone sub, both altered spellings).
  - The research doc's MusicXML encodings (C7(b9,#11), C7alt, Cm7b5, C6/9, Cadd9, C(no3), C/E, D/C polychord, N.C.).
  - The quality↔kind table, tested both ways.
  - Nested tuplets, additive meters, and tempo beat units.
  - One deliberate-mutation check confirmed the add-relative-to-dominant tests fail when the rule is broken.
- Choices [Claude]:
  - 7alt is stored as `1 3 #5 b7 b9 #9 #11`, its chord-tone spelling.
  - The functional-sixth kinds are rooted on their lowest root-position note (Neapolitan on bII, augmented sixths on b6).
  - Irregular meters (5/8, 7/8) must be given their grouping; there is no default.
  - Harmonic-major mode names are flagged as varying between sources.

## Room schema v1 (`:data`, 21 Oct 2026) — unit-tested 25/25, build-verified
- New module `:data`: Kotlin Multiplatform (android + jvm) with Room 2.8.5, sqlite-bundled 2.7.1 and KSP 2.3.12 (all the newest stable on 21 Oct 2026).
  - Why a 4th module [Claude]: Google's Room testing guide recommends local JVM tests via Room KMP with `BundledSQLiteDriver` and advises against Robolectric. That needs a KMP module, and the M0 plan already allowed for `:data`.
  - The Android app opens the database with the same bundled SQLite.
- Tables:
  - `project`.
  - `scrap`: format token (how to open it, not how it's sorted), origin project_id, show_in_library, deleted_at. Also the only two built-in values, each with its source (user/metronome/analysis): BPM (single, range or free time) and key.
  - `take` (letter unique per scrap, starred, write-once audio path, frames + sample rate, metronome grid, latency).
  - `tag` + `scrap_tag`: your own words, made once and reused ("lyrics", "bass", "love song"). Names are unique ignoring case. A tag never belongs to a project.
  - `scrap_group` + `group_member`.
  - `project_ref`: puts a scrap on a project's Board. One scrap can be on many Boards and stays in the Library. Holds that project's trim, loop, gain, fades, conform and offset as typed columns, sanitized on read and write.
  - `app_setting`.
- Tags and projects follow Christopher's 21 Oct 2026 revision of plan §3.4 and §4.1: free reusable tags, only BPM and key built in, and scraps that live in many places.
- Rules: enums are stored as ASCII tokens, and unknown tokens are refused. Schema JSON is exported to `data/schemas/` (commit it with the code).
- Tests (JVM, in-memory, bundled SQLite):
  - Round trips; foreign keys enforced, with cascades.
  - Take letters A–Z then AA; trashed takes keep their letter.
  - Library visibility; the 30-day trash for scraps, takes, projects and Groups, plus the list of audio files to delete.
  - Tags: one lyric tagged both "love song" and "political song"; one tag reused across scraps, whatever its case; the pick list puts the most-used first and narrows as you type; filtering by any or all tags; rename; untag or delete never removes scraps.
  - BPM and key: user > metronome > analysis; ranges, free time, and refusing a reversed range or NaN.
  - Projects: making something in a project puts it on that Board and keeps it in the Library; one scrap on two Boards with its tags unchanged; taking it off one Board leaves the other.
  - Groups never own scraps; per-project settings isolation; damaged settings sanitized; schema file present.
- `:app:assembleDebug` with `:data` — build-verified. No APK was sent: nothing user-facing changed.

## Toolchain (this PC, installed 21 Oct 2026)
- JDK 21.0.11 (Eclipse Adoptium), `JAVA_HOME` set.
- Android SDK at `D:\Android\Sdk` (`ANDROID_HOME`, user scope): cmdline-tools 15859902 (SHA-256 verified), platform-tools 37.0.1, platforms;android-36 rev 2 and android-37.0 rev 2, build-tools 36.1.0, NDK 29.0.14206865 (clang 21), CMake 3.31.6. Only `android-sdk-license` accepted.
- No Android Studio IDE; builds run through the Gradle wrapper (`gradlew`). Gradle caches live in the default `%USERPROFILE%\.gradle` on C:.
- adb sees the test phone (serial R5CT60LBR0H). The USB link drops every 1–3 s on the current cable/port; reads succeed only by retrying.

## Test phone (read over adb, 21 Oct 2026 — device-verified)
- **Model SM-S908U, device `b0q`, product `b0qsqw` = Galaxy S22 Ultra (US, Snapdragon 8 Gen 1, SoC SM8450)**. Christopher confirmed 21 Oct 2026 that this is the test phone (CLAUDE.md, plan and skills updated).
- Android 16, API 36 (sdk_full 36.0), One UI 8.0 (`ro.build.version.oneui` 80000), build S908USQSAGZH3, security patch 2026-08-05; first API level 31; ABIs arm64-v8a, armeabi-v7a, armeabi.
- Features: `android.hardware.audio.low_latency`, `android.hardware.audio.pro`, `android.hardware.microphone`, `android.software.midi`.
- AAudio: `aaudio.mmap_policy` = 2 and `aaudio.mmap_exclusive_policy` = 2 (MMAP allowed when available).
- Mixer output threads at 48000 Hz; one output has a HAL frame count of 192 (4 ms) and the others 960. Bluetooth A2DP output was active at the time. These are system settings, not round-trip latency; that gets measured in the Device Lab.

## Shipped
(nothing yet)

## Device Lab measurements
(none yet — official plan §14.2)

## Unverified on device
- Everything in the Device Lab: whether Exclusive/MMAP is granted, whether the latency bursts are found on speaker, wired and Bluetooth, whether all three mic presets open, the stability run, the glyphs.
- Whether the Lab icon and the scaffold icon both show and install over 0.0.1.

## Don't know yet
- S22 Ultra speeds for Basic Pitch, Spleeter, Moonshine; Rubber Band vs Signalsmith quality.
- Phone-mic low-bass tuner accuracy (drop A, D standard).
- Widget/shortcut capture behaviour under Android 16 microphone foreground-service rules.
- How FL Studio imports MIDI markers and meter changes.
- Visual style (reference apps/images from Christopher, then mockups).
- Whether acoustic latency through Bluetooth can be measured reliably by holding an earbud to the mic (codec delay varies; the spread will tell).
