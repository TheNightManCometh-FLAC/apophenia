# Design enhancement plan (draft, 30 Oct 2026)

Christopher: "It looks very flat right now and nothing stands out from anything else. Have different colors for the
different sections and come up with some more style ideas to enhance it but actually look/function better not just
change it to look more flashy or cute."

Nothing here is built yet. The open questions at the end are his to answer.

## Decisions so far (31 Oct 2026)

All the directions are on the design canvas (claude.ai/artifact/V9jLDiibgJAZq56q1pRR9Z; sources in `docs/mockups/directions/`). The decisions below supersede the three directions under "How it gets built".

**1. Direction: "Film still"** (`Film.dc.html`, board 17).
- He reacted: "Now that's what I'm talking about."
- How he got there:
  - He liked White cube, but called it "very teenage engineering".
  - He called the psychedelic tints "business" and the zine version "teenage magazine".
  - He then sent 16 album covers as the reference for style, not for literal use.

**2. Every screen is a full-bleed painted frame:** haze, light, halation, film grain, dust and vignette.

**3. The text is set like film:**
- title cards: spaced caps over an italic serif title;
- two-column credit blocks for facts;
- subtitles for the chords and the Ear question;
- an end-credits roll for Ear's answers.

**4. Backgrounds are unique and abstract, generated from the material's own data.**
- He chose this over scenes he picks.
- Inputs: key sets the palette, tempo sets the rhythm of the light, and the takes' loudness sets the light trails.
- There are no figurative scenes, and the app never interprets lyrics or content (the "no connections" rule).
- Every background is deterministic from a seed plus the data, so it can be regenerated identically any time.

**5. Battery and CPU rules (non-negotiable):**
- Every effect is baked into a bitmap once, on a worker thread, when a project or fragment is created or changes.
- Bitmaps are kept in an evictable cache, like renders.
- Drawing a background is a plain bitmap draw: no live blur (`RenderEffect`), no running shaders, no animated grain. Only the playhead and the text change.
- Haze, glow and blur layers are painted at quarter resolution with box-blur passes, so the cost doesn't grow with the radius. Grain is added at full size.
- Only the visible screen and its neighbours stay in memory.
- Dark frames also save power on the OLED screen.

**6. Cost:**
- **Measured, prototype on the PC:**
  - About 220–260 KB per 780×1688 JPEG.
  - 4.2 s for all four in unoptimised Python with per-pixel loops.
- **Estimated, not measured** (`[measure]` on the S22 Ultra):
  - 50–200 ms per background in Kotlin.
  - About 10 MB for a full-screen bitmap at 1080×2316; 16-bit RGB_565 halves that, and the grain hides the banding.

## Why it looks flat (read from the code, `ui/theme/Console.kt`)

1. **One accent does every job.**
   - `C.acc` (copper in Console) is used in 156 places: the selected tab, primary buttons, links, highlighted values, "on" chips and progress.
   - When everything important is the same colour, nothing is more important than anything else.
2. **The surfaces are nearly the same colour.**
   - Background `#121314`, panel `#18191B` and panel2 `#1F2023` sit about 2–3% apart in lightness.
   - On a phone at arm's length they read as one flat sheet, so 1 px divider lines do all the grouping.
3. **One typeface at one size range.**
   - Everything is JetBrains Mono at 9–14 sp.
   - Titles differ from body text only by weight and capitals, so a screen has no first thing to look at.
4. **The labels are hard to read.**
   - The uppercase labels ("BPM · KEY") are 9 sp in the dim colour.
5. **No section identity.**
   - MAKE, PRACTICE and STUDY look identical, so you can't tell where you are without reading the tab bar.

## What changes, and what each change is for

Each item has a job. Nothing is decoration for its own sake.

**1. A colour per section: MAKE, PRACTICE, STUDY.**
- The section's hue marks:
  - its bottom tab;
  - the sub-tab indicator;
  - a thin band under the top strip;
  - that section's primary buttons.
- **Job:** you know where you are at a glance, and a primary action stands out from everything else on screen.
- [Claude] Hues for Console: MAKE warm amber, PRACTICE green-teal, STUDY blue-violet.
- Every theme gets its own three hues, tuned to its background.
- **Reserved colours:**
  - Red stays reserved for recording.
  - A separate "selected" colour stays the same in every section, so a selected item never changes meaning.

**2. Real surface levels.**
- Four tonal steps (background, section, card, raised), each visibly lighter than the one below. This is the same idea as Material 3's surface-container roles ([Material blog: tone-based surfaces](https://material.io/blog/tone-based-surface-color-m3)).
- **Placement:**
  - The player and the top strip sit on the highest level.
  - Grouped settings and lists sit in cards instead of between hairlines.
- **Job:** groups read as groups without lines everywhere, and the always-there controls separate from the content.

**3. A type scale with two faces.**
- **Body, titles and labels:** a readable sans, OFL-licensed. Candidates are Inter and IBM Plex Sans.
- **Values stay in JetBrains Mono,** so digits line up: BPM, key, times, tab, chord symbols, scores.
- **Five sizes:** 11 / 13 / 15 / 20 / 28 sp. Labels go up to 11 sp at full contrast.
- **Job:** each screen gets a clear headline, then content, then detail, and small text gets easier to read.

**4. Displays as heroes.**
- The thing a screen is about gets the space and the contrast:
  - the waveform on a recording;
  - the question and answer on an Ear run;
  - the needle and note on the tuner;
  - the chart on a chord chart.
- Controls step back around it.
- **Job:** the eye lands on the content first.

**5. Clearer states.**
- An "on" chip is filled and an "off" chip is outlined, not just a colour change.
- Pressed and disabled states are visible.
- A running AI job shows a progress bar in the section's colour.
- **Job:** you can tell what's active without reading.

**6. Icons next to words.**
- Material Symbols (Apache-2.0) on the bottom tabs and common actions (record, play, more, add), keeping the text labels.
- **Job:** faster recognition. The words stay for precision.

**7. Spacing and touch.**
- An 8 dp grid.
- Every tappable thing at least 48 dp tall, as Android's accessibility guidance asks.
- **Job:** fewer missed taps, and less cramming.

**8. Kind colours on the Board and Library** (only if you want them; see the questions).
- Each fragment kind gets a colour on its kind tag: recording, lyrics, chords, tab, sheet music, drum chart, text.
- **Job:** a Board of loose squares becomes scannable by kind.

**What I'm not doing:**
- gradients, glows, shadows for their own sake, or rounded "cute" shapes;
- animation beyond the existing state changes;
- anything that lowers the information density you already have.

## How it gets built

**1. References first.**
- Capture screens from shipping apps that solve "sections and hierarchy on a dark phone UI":
  - Ableton Note
  - Moises
  - MuseScore
  - Yousician
  - Functional Ear Trainer
  - BandLab
  - TonalEnergy
- Write down what each does for section identity, surfaces and type, with screenshots in `docs/research/`.

**2. Three materially different directions, built for real in the app,** not swatches. All three include the section colours; what differs is the design language:
- **A, "Sections":** today's layout, plus section hues, real surface levels and the new type scale. The smallest change.
- **B, "Cards":** content grouped into raised cards, hero displays, sans body text, and more space between groups.
- **C, "Instrument panel":** dense like hardware. The section colour sits as a top band and LED-style indicators. Mono stays the main face, with a larger size scale.
- Each direction is applied to the same four screens: the Board, a recording, an Ear run, and the tuner.
- Each is screenshotted on the emulator and sent to you to pick. A debug-only switch flips between them, so you can also try them on the phone.

**3. Roll out your pick** to every screen and all six themes.
- A unit test checks contrast for every theme: text against its surface at least 4.5:1, and UI marks at least 3:1 (WCAG 2.2).
- A theme that fails the test doesn't build.

**4. Phone walkthrough** (`docs/walkthroughs/`) and a STATUS entry, as usual.

## Questions for Christopher

1. **Section hues:** OK with amber (MAKE), teal (PRACTICE) and violet (STUDY) for the default theme, or do you have colours in mind?
2. **Kind colours on Board and Library tags:** yes or no?
3. **A sans for body text:** is changing the typeface OK, with mono kept for numbers, tab and chords? Or should everything stay mono?
4. **Do you have any apps** whose look you like (or hate) that I should add to the references?
