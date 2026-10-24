# Apophenia — instructions for Claude Code

You are building **Apophenia**, an offline Android app for capturing rough musical scraps and working through them, for Christopher (Better App Mafia). Read this file fully before doing anything.

## 1. What's authoritative

1. **`docs/plan/apophenia-official-plan.md`** — the single source of truth for what the app is and the build order (M0–M7). Read it end to end before starting work. Items marked **[Claude]** were implementation choices; Christopher may overrule them. Items marked **[measure]** are not done until measured on the phone.
2. `docs/reference/music-core-and-theory-design.md` §1–5 — detailed music-core data model (spelled pitch, intervals, chord structure, symbols, rhythm). Its product/navigation parts are superseded by the plan.
3. `docs/research/` — the research the plan rests on (theory engine, MusicXML 4.0 and chord corpus, P0 analysis research, on-device models). Use it for specs and test fixtures.
4. `docs/history/` — superseded plans and audits. **Do not build from these.** Several contain decisions Christopher never made (e.g. a Connections/suggestion feature, a card-canvas Board, a destination prompt after every recording, advanced theory as optional). If anything there conflicts with the official plan, the plan wins.
5. `reference/p0/` — an earlier exploration: pure-Java prototypes (YIN pitch, tempo candidates, mode evidence, chord templates, lyric prosody), a probe activity, and a phone test matrix. Ideas to port to Kotlin, not code to keep. All their tests are synthetic.

## 2. Non-negotiables (from Christopher — do not change without asking him)

- The app **does not make connections or suggestions**. The musician does, in their head. The app puts material in front of them and gives them tools.
- **Full depth, not for beginners**: theory, pitch training, chord symbols, notation and rhythm are complete and correct at the advanced end. Levels are labels, never locks.
- Five modes: **Board, Library, Tools, Learn, AI** (Christopher merged Pitch Trainer and Theory into Learn, then made AI its own mode, both 21 Oct 2026); persistent bottom player; quick capture everywhere plus widget and app shortcuts.
- The **Board** is one scrolling working sheet per project (lyrics, chords, tab, sheet music, recordings, text). It is **not** the song structure; the song isn't finished in the app.
- **Recordings are never edited.** Projects hold a reference plus per-project settings. Takes are lettered A, B, C…; star or delete.
- Recordings go to the Library by default; recorded in a project's Board/stack → that project (also shown in Library search unless toggled off on the save sheet). No destination prompt; no "new/unreviewed" marker.
- Stacks: loop / one-shot / free-time layers; loop and one-shot layers conform automatically to stack tempo/key (reversible, amounts shown); free-time never conformed; layers added by **filtering the Library**, not suggestions.
- Mic is **Off** at launch and opens only for recording and the tuner. **No pre-roll, no Listening state** (Christopher, 22 Oct 2026).
- No tuner string mode. No structured practice sessions (simple log/timer only; curated Exercises content chosen by Christopher at the end). No automatic instrument detection. No internet permission, no accounts.
- App id **`com.betterappmafia.apophenia`** (permanent once uploaded to Play). GPL-3.0. Channels: GitHub, Google Play, F-Droid.
- Stack: **Kotlin + Jetpack Compose**, **C++ audio engine on Oboe**, pure-Kotlin **music core** module, **Room/SQLite**, **TensorFlow Lite / ONNX Runtime** for Basic Pitch, Spleeter and Moonshine.

## 3. How to work with Christopher

- **Never invent product decisions.** If something isn't in the plan and it affects what the user sees or does, ask him. Implementation details are yours; say what you chose.
- **Research real products and primary sources before designing a feature.** Look things up instead of guessing; no guessed API names, parameters or file formats.
- **Reason through concrete steps**, not broad strokes. Give honest assessments, including disagreement.
- **Be specific**: a specific instrument, model, library version or device, never the generic category. His test phone is a **Samsung Galaxy S22 Ultra (SM-S908U, US model, Snapdragon 8 Gen 1), Android 16 (API 36)**, confirmed over adb on 21 Oct 2026; if a different phone is connected, read its model with adb before reporting results.
- **Label evidence** on every claim: source-only, unit-tested, build-verified, device-verified, or ear-verified. Nothing is "done" until walked through on the phone.
- **Verify audits in source** before acting on them; push back on wrong or low-value items with the reason.
- **Keep track of what you don't know** and tell him; he supplies research files on request.
- He's an experienced bedroom producer and multi-instrumentalist (FL Studio, bass, guitar). Don't explain music basics to him.

## 4. Engineering rules

- Use the project skills (shipped in `skills-to-install/`, installed to `.claude/skills/` in step 0 below): **apophenia-music-core** (spelling, chords, symbols, MusicXML), **apophenia-realtime-audio** (Oboe engine and threading), **apophenia-working-rules** (process, evidence, releases). Deeper DSP references (STFT/phase vocoder, filters, lock-free queues, ONNX worker handoff) are in `docs/reference/account-skills/`; they're written for VST3 plugins but the DSP and real-time rules transfer.
- Pure-Kotlin core gets unit tests first (chords and scales on all 35 roots, parser round trips, MusicXML round trips, rhythm arithmetic). Assertions read as sentences.
- Storage: versioned Room migrations from day one; audio files written once and never modified; renders are evictable cache; backup/restore covers database + audio.
- Build and test with Gradle on this machine; install on the phone over ADB when it's connected. Never commit signing keys.
- Every milestone ends with a written phone walkthrough (`docs/walkthroughs/Mx.md`) and an updated status doc (`docs/STATUS.md`): what shipped, tests, measurements, and an "unverified on device" list.

## 5. Start here (M0)

0. **Install the project skills first:** copy each folder in `skills-to-install/` into `.claude/skills/` (create it), e.g. on Windows PowerShell: `New-Item -ItemType Directory -Force .claude\skills; Copy-Item -Recurse skills-to-install\* .claude\skills\`. Then confirm the three skills (apophenia-working-rules, apophenia-music-core, apophenia-realtime-audio) are available; if not, read their SKILL.md files directly.
1. Read the official plan, then the two research reports' sections on the data model and chord symbols.
2. Ask Christopher for anything M0 needs that isn't present: GitHub repo URL (GPL-3.0) and whether to init git here; reference apps/images for the visual style; confirm Android Studio, SDK 36, NDK and a JDK are installed (`sdkmanager --list_installed`, `java -version`).
3. Create the Gradle project: `:app` (Compose), `:core` (pure Kotlin/JVM), `:audio` (C++/Oboe via CMake), with `applicationId com.betterappmafia.apophenia`, **minSdk 29** (agreed with Christopher 21 Oct 2026), **targetSdk 36**, **compileSdk 37** (current AndroidX — Compose 1.12, core 1.19, lifecycle 2.11 — requires it; Christopher agreed 21 Oct 2026).
4. Room schema v1 and core music types with tests.
5. The **Device Lab** debug screen (plan §14.2).
6. **Clickable mockups** of every main screen for Christopher's approval before building real screens.
