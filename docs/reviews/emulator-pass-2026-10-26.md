# Emulator pass — 26 Oct 2026 (builds 0.9.4–0.9.7, -Pemulator)

Every screen walked in the Android emulator (Pixel 6 profile, Android 13, x86_64) by Claude, reading each screen's
accessibility tree and taking screenshots where the look matters. Not the phone: no audio judgement, no real mic,
no speed. Findings are numbered; **[bug]** broken, **[dead end]** nowhere to go, **[confusing]** works but unclear,
**[layout]** visual, **[stale]** text that no longer matches the app.

## Findings

1. **[layout]** Bottom bar: the sub-tab hints under each tab are cut off at phone width. PRACTICE's "TOOLS · EAR · EXERCISES" shows as "TOOLS · EAR ·", MAKE's runs into PRACTICE's. (Seen on the first screen.)
2. **[set aside by Christopher]** **[confusing]** Debug builds install two icons, Apophenia and Device Lab; the Device Lab opens straight into measurement screens with no way into the app.
3. **[bug]** New project sheet: the keyboard covers the whole sheet, so the name you're typing and MAKE IT are hidden (the sheet doesn't move up above the keyboard). Likely every bottom sheet with a text field (SheetHost). Also: the name field needs a tap before typing; the sheet doesn't focus it.
4. **[confusing]** Board square for a lyric shows the first line twice (as the title, then as the glimpse), and not the chord over it. A screen reader calls it "LYR window".
5. **[fixed 0.9.11: row removed]** **[confusing]** An empty Board has ● RECORD · WRITE ▾ · LIBRARY › at the bottom, the same jobs as ● REC and + NEW at the top; once one scrap is on the Board that row goes, so the two ways to add things aren't the same from one moment to the next. Its text says "Record, write lyrics, a note or a chord chart" though WRITE ▾ also has tab, drum chart, sheet music and piano roll.
6. **[bug, fixed in 0.9.6]** **Every tab and sheet-music editor crashed on opening** (+ NEW › Guitar tab, and any TAB/SHEET scrap). ScoreView's meter menu held a bare "5/8", which `TimeSignature.of` refuses ("more than one grouping"), so the screen's file failed to load. In since 0.5.3. Fixed: the menu lists 5/8 by grouping (2+3/8, 3+2/8) like 7/8; `ScreenTablesTest` now loads every UI file's top-level values and fails on any that throw (checked: it fails with the old list). The app's own crash report ("LAST TIME IT CRASHED") showed it on the next launch, as designed.
7. **[layout]** Tab editor: the duration row and the INPUT / MODE / EFFECTS / TUNING row run off the right edge (TUPLET and anything after TUNING are cut off; they scroll, but nothing shows that they do).

8. **[bug, fixed in 0.9.7]** Opening an item while another was open (+ NEW › Sheet music over an open tab) showed the **previous item's content** in the new editor, titled as the new one; an edit there would have saved the tab into the new sheet music. `collectAsState` keeps the last value until the new item's data arrives, and the editors read their first text from it. Fixed: `ScrapDetail` starts a fresh composition per item (`key(scrapId, project)`). Checked: the new sheet music now opens empty.
9. **[bug, fixed in 0.9.7]** **Every drum chart crashed the app**, new or opened. The drum chart reader's `{meter: …}` pattern had a bare `}`: desktop Java accepts it (so the unit tests passed), Android's ICU regex refuses it. Escaped; it's the only such pattern in the code (searched). In since 0.3.6. The crash screen on the next launch showed it, but only its top frames: the cause (PatternSyntaxException) is below the fold, in COPY.
10. **[dead end, fixed in 0.9.7]** EXERCISES › + ADD ▾ › TAB (or any type) closed the menu instead of listing the items: **no exercise could be added**. The family step now reopens the menu, as the other family menus do. Checked: added a tab, CLEAN ↑ went 57 → 61, ▶ CLICK set the top click to 61.
11. **[bug]** Uppercased music labels change meaning. EAR's drill lists read "M2 · M2 · M3 · M3" for m2 · M2 · m3 · M3 (and M6 · M6, M7 · M7, M9 · M9…). Reference chord pages' line of other names reads "MINOR TRIAD · CM · CM · CM" for Cm. Both run symbols through `uppercase()` (`EarView.kt:142`, `Reference.kt:242`). The drill's own answer buttons are right (m2, M2).
12. **[confusing]** Tab and sheet editors: on opening, the length bar highlights 1/4 but the selected beat is the bar's whole rest, and the first note goes in as a whole note. Shortening it leaves the bar short (number in red) instead of filling rests; Guitar Pro does the same, MuseScore fills rests.
13. **[fixed 0.9.9: notes play beside the player]** **[confusing]** Hearing a note as you enter it (fret, piano key, piano roll) loads "NOTE 0:00 / 0:01" into the bottom player, replacing what was loaded there.
14. **[fixed 0.9.8 + 0.9.10]** **[confusing]** Piano roll: the step-entry rows (lengths, INPUT / MODE / EFFECTS / EDIT ▾) stay above the roll's own toolbar (TOOL / SNAP / SCALE / EDIT ▾ / TOOLS ▾), so two EDIT ▾ are on screen. After the first note was drawn the roll scrolled an octave, so the note jumped away from the finger.
15. **[confusing]** Names: a new tab or sheet music is "Guitar tab" / "Sheet music", a new chord chart or drum chart "Untitled · 26 Oct 16:59", a text note its first line. Pickers then list identical rows: Performance check's REFERENCE ▾ showed three "Sheet music" and two "Guitar tab"; so did EXERCISES › + ADD.
16. **[confusing]** A new chord chart says "100 BPM (NOT SET)" while the click, the tab, the sheet music and the drum chart all say 96.
17. **[layout]** Drum chart: each settings row (METER, STEPS, BARS, SWING, TEMPO) has two ▾, one after the value and one at the right edge.
18. **[confusing]** Library rows have two unlabelled columns (96, —): tempo and key, with no header. The type tags are abbreviated: NOT (sheet music) reads as the word "not"; CHD, DRM, TXT. The screen reader says "NOT window", "CHD window" on the Board (as in 4).
19. **[confusing]** PRACTICE › Tools › Playback speed and Isolator say "Play a recording first" while the player holds a drum chart: they take recordings only, and don't say that's why.
20. **[confusing]** The save sheet after a 4-second emulator take filled KEY with "G Lydian · ANALYSIS" while BPM said unknown: a mode named from four seconds of the emulator's fake mic. Can't judge real input here; worth looking at on the phone.

## Checked and working
Tab: fret entry, NEXT, ▶ PLAY (4 bars at 96 = 0:10). Sheet music: piano entry, NEXT. Piano roll: draw. Chord chart: typing with the | button, the grid, ▶ PLAY CHORDS. Drum chart (after 9): grid taps, HEAR IT. Text note: titled by its first line. Board: every new item lands on it. Library: FILTER (type, 2 match), FIND, SORT, ⋯. AI: the list, Performance check's pickers. Tools: metronome start/stop (top CLICK follows), tempo trainer, tuner (mic on while open, off on leaving), drone, playback speed, isolator, practice timer. Ear: a drill, answer and explanation (D♭–E♭ = M2). Exercises (after 10): add, ladder, click. Course: Intervals › Major, minor, perfect, its ▶ example, the P4 shapes on the neck. Reference: chord families, Minor triad page (C E♭ G, P1 m3 P5, x-3-1-0-1-3). Top strip: CLICK ▾, TOOLS ▾ (the two pinned), ● REC → save sheet, MIC OFF after. Settings: themes (Paper checked). Project switcher.

## Not walked (emulator can't, or left for the phone)
Import from the phone (system picker), stacks and overdub, a recording's AI jobs (Notes → MIDI, stems, speech), backup and restore, the widget and shortcuts, Stand mode, Name a chord, Reference scale / progression / tuning / groove pages, PICK YOUR OWN in Ear.

## Fixed in 0.9.8
1, 3, 4, 7, 11, 12, 15, 16, 17, 18, 19; part of 5 (the empty Board's text) and 14 (the octave jump). Open: 2, 5's add row, 13, 14's two toolbars, 20. Details in `docs/STATUS.md` (0.9.8).
