# Apophenia

**A 21st century musician's notebook.** An offline Android app for catching rough musical ideas the moment they
happen — a hummed melody, a riff, a lyric line, a chord change — and working through them with real tools,
full-depth theory and ear training. By Better App Mafia. Free software, GPL-3.0-or-later.

No account, no internet permission, no ads, no analytics. Everything stays on your phone.

## What's in it

- **Board** — one scrolling working sheet per project: lyrics, chord charts, tab, sheet music, recordings and notes
  side by side. It isn't the song's structure; the song gets finished in your head and your DAW.
- **Library** — every scrap you've caught: recordings (takes A, B, C… starred or deleted, never edited), lyrics in
  ChordPro, chord charts, tab, MIDI, sheet music, drum charts, text. Search, filter, tags, groups, trash, backup and
  restore to a folder you choose.
- **Tools** — metronome with additive meters and the 40 PAS rudiments, chromatic tuner, drone, recorder, playback
  speed, an isolator with note finder, a practice log.
- **Learn** — a Course (nine categories, 123 lessons, beginner to advanced; nothing locked), a Reference (every
  interval, 55 scales and modes, 41 chord types, 27 named progressions and 45 guitar tunings, each on any root,
  on your fretboard, piano and staff, with sound) and Ear training (56 drills: intervals, chords, voicings, pitch,
  scale degrees, modes, harmony, dictation, rhythm, tuning, and drills built from your own music).
- **AI** — on the phone, not in a cloud: Spotify's Basic Pitch turns a recording into notes, Spleeter splits a
  song into stems, Moonshine turns a spoken lyric idea into text, and the app finds tempo, key and chords.
- **Stacks** — layer your recordings into a loop; loop and one-shot layers follow the stack's tempo and key (the
  amount shown, always reversible), free-time layers are never touched.
- **Getting things out** — MusicXML 4.0 (with tab), MIDI, ChordPro and plain-text tab, and imports from the phone.

The app doesn't make connections or suggestions. You do, in your head. It puts your material in front of you and
gives you tools.

## Status

In development, not yet released. Milestones M0–M6 are code-complete; M7 (exports and release) is under way.
`docs/STATUS.md` has the detail, including everything not yet checked on a phone.

## Building

Android Studio (or the command line) with JDK 17+, Android SDK 36/37 and the NDK.

```
./gradlew assembleDebug test :data:jvmTest
```

The debug APK lands in `app/build/outputs/apk/debug/`. Modules: `app` (Kotlin + Jetpack Compose), `core` (the
pure-Kotlin music core: spelled pitch, intervals, chords, scales, rhythm, scores, the Course and Reference),
`audio` (the C++ engine on Oboe, with Signalsmith Stretch), `data` (Room).

## Repository

| Path | What it is |
|---|---|
| `app/`, `core/`, `audio/`, `data/` | The app |
| `docs/plan/apophenia-official-plan.md` | The plan: what the app is and the build order |
| `docs/STATUS.md`, `docs/walkthroughs/` | What's built, what's tested, what to try on the phone |
| `docs/research/`, `docs/reference/` | The research and reference the plan rests on |
| `docs/history/` | Superseded plans (kept for the record; not built from) |
| `site/` | The project website and privacy policy (GitHub Pages) |
| `reference/p0/`, `tools/` | Early prototypes; build and asset tools |
| `CLAUDE.md`, `skills-to-install/` | Instructions and skills for Claude Code, which writes much of the code |

## Licence

Apophenia is licensed under the GNU General Public License v3.0 or later (`LICENSE`). It bundles Oboe, Signalsmith
Stretch, LiteRT, ONNX Runtime, the Basic Pitch, Spleeter and Moonshine models, the Bravura, Noto Music and
JetBrains Mono fonts, and University of Iowa piano samples, each under its own licence: see `NOTICE.md`, and
Library ⋯ › Licences and privacy in the app.

Privacy policy: https://thenightmancometh-flac.github.io/apophenia/privacy/
