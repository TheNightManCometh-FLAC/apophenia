# Emulator pass — 26 Oct 2026 (build 0.9.4/0.9.5, -Pemulator)

Every screen walked in the Android emulator (Pixel 6 profile, Android 13, x86_64) by Claude, reading each screen's
accessibility tree and taking screenshots where the look matters. Not the phone: no audio judgement, no real mic,
no speed. Findings are numbered; **[bug]** broken, **[dead end]** nowhere to go, **[confusing]** works but unclear,
**[layout]** visual, **[stale]** text that no longer matches the app.

## Findings

1. **[layout]** Bottom bar: the sub-tab hints under each tab are cut off at phone width. PRACTICE's "TOOLS · EAR · EXERCISES" shows as "TOOLS · EAR ·", MAKE's runs into PRACTICE's. (Seen on the first screen.)
2. **[confusing]** Debug builds install two icons, Apophenia and Device Lab; the Device Lab opens straight into measurement screens with no way into the app.
3. **[bug]** New project sheet: the keyboard covers the whole sheet, so the name you're typing and MAKE IT are hidden (the sheet doesn't move up above the keyboard). Likely every bottom sheet with a text field (SheetHost). Also: the name field needs a tap before typing; the sheet doesn't focus it.
4. **[confusing]** Board square for a lyric shows the first line twice (as the title, then as the glimpse), and not the chord over it. A screen reader calls it "LYR window".
5. **[confusing]** An empty Board has ● RECORD · WRITE ▾ · LIBRARY › at the bottom, the same jobs as ● REC and + NEW at the top; once one scrap is on the Board that row goes, so the two ways to add things aren't the same from one moment to the next. Its text says "Record, write lyrics, a note or a chord chart" though WRITE ▾ also has tab, drum chart, sheet music and piano roll.
6. **[bug, fixed in 0.9.6]** **Every tab and sheet-music editor crashed on opening** (+ NEW › Guitar tab, and any TAB/SHEET scrap). ScoreView's meter menu held a bare "5/8", which `TimeSignature.of` refuses ("more than one grouping"), so the screen's file failed to load. In since 0.5.3. Fixed: the menu lists 5/8 by grouping (2+3/8, 3+2/8) like 7/8; `ScreenTablesTest` now loads every UI file's top-level values and fails on any that throw (checked: it fails with the old list). The app's own crash report ("LAST TIME IT CRASHED") showed it on the next launch, as designed.
7. **[layout]** Tab editor: the duration row and the INPUT / MODE / EFFECTS / TUNING row run off the right edge (TUPLET and anything after TUNING are cut off; they scroll, but nothing shows that they do).

## Not walked yet (continue from here)
Tab editor entry (frets, NEXT, play), sheet music, piano roll, chord chart, drum chart, text note; Library (filters, search, rows, ⋯); AI screen and Performance check; PRACTICE › Tools (each tool), Ear (a drill, pick your own), Exercises (add, ladder); STUDY › Course (a lesson, hear it), Reference (families, a chord page); top strip menus (CLICK ▾, TOOLS ▾, ● REC); Settings themes; project switcher.
