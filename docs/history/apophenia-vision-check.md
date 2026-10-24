> **SUPERSEDED (Oct 21 2026)** by `apophenia-official-plan.md`, which incorporates everything here.

# Apophenia — vision check (v4, Oct 21 2026)

**Purpose:** state the concept back in Christopher's terms, separate what *he* decided from what other planning passes decided for him, and record his corrections. Where this doc and the Oct 21 unified plan or architecture decisions differ, **this doc reflects his latest words.** **[confirm]** = my reading of his answer, waiting on a yes.

v2: his answers on the Board, Connections, stacks, Pitch Trainer, code stack and transcription.
v3: the song isn't made in the app (Board is not the song structure); duplicates simplified to per-project settings; takes labelled A, B, C with star/delete; research on on-device stems and speech (§8).
v4: Library filtering for stack layers, no "new" marker, save-sheet flag, click bleed, app id `com.betterappmafia.apophenia` (§10).

---

## 1. The concept

**Apophenia is a place to dump rough musical scraps and work through them.** Recordings, lyrics, lines of text, chord ideas, riffs in tab, MIDI and sheet music all land in one place without deciding what they're for.

**The app doesn't make connections; the musician does, in their head.** The app's job is to put the material in front of you — searchable, playable, readable, side by side — so you can see and hear what goes together. It doesn't suggest, rank or match ideas for you. (This replaces the "Connections" feature in every earlier plan; §3.)

Around that sit the tools you'd otherwise leave the app for: metronome, tuner, drone, recorder, a slow-down player for learning by ear, an adjustable isolator, transcription of your recordings into MIDI/tab/sheet music, pitch and ear training on real piano sound, and a theory reference **in full depth**, not a beginner course.

It's the **rough side** of music: finishing happens later in FL Studio or a real session.

---

## 2. The five places

| Mode | What it is |
|---|---|
| **Board** | A project's working sheet — everything you need to play and write that song, in one scrolling view (§4) |
| **Library** | Every scrap in one searchable list, mixed formats, tags on the row, Groups, compact filter |
| **Tools** | Metronome, tuner, drone, recorder, slow-down player, isolator |
| **Pitch Trainer** | Ear and pitch training, full depth (§7) |
| **Theory** | Full-depth interactive reference, applied to your tuning and your material |

Plus a persistent bottom player (stacks/sessions) and global quick capture, with widgets and app shortcuts.

---

## 3. Corrections to the unified plan (his answers)

| Plan said | He said | Now |
|---|---|---|
| Board = freeform canvas of movable cards | No. The Board has everything you need: lyrics, chords/tab/sheet music, and the recordings to play along to; sheet music should scroll. The song isn't made in the app | Board redesigned (§4); not the song structure |
| Connections = a contextual app capability suggesting matches | The app doesn't make connections; the user does, in their head | **Removed** as an app feature. What stays is what helps *you* find things: tags (key, mode, tempo, instrument), Groups, search and filters |
| Stack layer suggestions ranked by compatibility | (follows from the above) | Replaced by filtering your Library by key/tempo/instrument when adding a layer **[confirm]** |
| No automatic conform in stacks (non-goal) | Yes to stacks conforming | Layers conform to the stack's tempo and key automatically, reversible, amounts shown; free-time and one-shot clips untouched |
| Prompt for a destination after every recording | Recordings go to the Library; in a project's stack they go to the project; you flag it when you save | Save sheet (§5) |
| Advanced theory not an initial requirement | Full depth, still | Full-depth Theory and Pitch Trainer are release requirements |
| Copy to Project duplicates files | Not an issue: the file is never edited; changes are per-project settings | Reference + per-project settings (§6) |
| Widgets in the last phase | Wanted | Ship with quick capture |
| Pitch Trainer starts with singing into the tuner | Hearing-based is a start; do more | Full scope (§7) |
| Transcription "optional, experimental" | (not covered in my v1 either) | Full section (§8) |
| Build stack "to be compared" | Confirmed | Kotlin + Compose, C++/Oboe audio, Room/SQLite (§9) |

Still standing from earlier, not re-opened: groups where a scrap can be in many and is removed from the others once used; take tags for tempo, key **and mode**, instrument; draft playback; one piano dynamic for now; adjustable isolator; in-app mic control, Listening only when switched on; instruments as strings + tuning + capo; tab stores string, fret and pitch, with notes shown alongside; no tuner string mode; no structured practice sessions, curated exercises at the end; practice log/timer kept simple; USB/pedals as later polish.

---

## 4. The Board — a project's working sheet

**What it's for:** sitting with your instrument and working on one song. Everything for that song is on one page: you read it, play along to it, record over it, and edit it in place.

**Layout: one vertical, scrolling sheet** (not a canvas), made of blocks in the order you put them:
- **Lyrics**, with chords above the words where you've placed them.
- **Chord charts** (bars and chords).
- **Tab** for the project's instrument and tuning, with the note row under it.
- **Sheet music**, laid out as a continuous strip of systems that scrolls.
- **Recordings**: inline players with waveform; any recording on the sheet can be the playback source.
- **Text**: notes, ideas, instructions to yourself.
- Optional **section headings** (verse, chorus, anything) to divide the sheet; a project with no sections is fine.

**Playing along:**
- Pick what plays: one recording, the project's stack, or the chord chart/notation played on piano (draft playback).
- **The sheet scrolls with playback.** Notation and tab follow a cursor through the music. Lyrics and chords follow the recording at the points you've synced them; syncing is done by tapping each line as it goes by while listening, once, and can be nudged. Unsynced content can scroll at a steady speed you set (teleprompter style), or stay put.
- Loop any section while reading it; record a new take over the playback without leaving the sheet; the lyric you're singing stays on screen.
- **Stand mode**: large type, landscape support, scroll locked to playback, controls reachable with one thumb.

**Editing:** every block is edited where it sits (tap lyrics to type, tap tab to enter frets, tap notation to enter notes). Blocks can be reordered, collapsed, or hidden while playing.

**The Board is not the song.** The song isn't actually made in the app. Block order is just how you've laid out your material, not a song structure. Song structure gets worked out with the tools, in whatever way suits the person:
- written out in **tab or sheet music**,
- or by **recording sections and arranging them** in the player/stack,
- or not at all.

There's no one way; these are the tools.

---

## 5. Saving recordings

| Where you record | Default destination |
|---|---|
| Quick capture, widget, shortcut, Tools recorder | Library |
| In a project's stack or on its Board | That project |
| Temporary stack, no project | Library |

**The save sheet** (one screen after Stop, nothing required; tapping Save or leaving keeps the defaults):
- Title (optional).
- Instrument (pre-filled from the last one).
- Tempo, key and mode shown as they arrive from analysis, editable.
- **Flags** set at this moment: for a project recording, **also show it in the Library**; mark it as a stack layer; add to Groups.
- Recordings are written to disk before the sheet appears; closing the sheet never loses anything.

**[confirm]** "You flag them initially when you go to save the recording" — I read this as: whether a project recording also shows in the Library is a flag on the save sheet. Right?

---

## 6. Duplicates and takes

**Duplicates aren't an issue, because nothing edits the file.** A recording is never permanently changed. When it's used in a project, the project holds a reference to it plus **that project's settings** (trim, loop points, gain, fades, tempo/pitch conform, offsets, tags). Changing those in one project changes nothing anywhere else, and no second copy of the audio is ever made.
- Lyrics, text, chords, tab, MIDI and notation are tiny; a project's edited version of one is simply its own copy.
- The Library shows each recording once, with a badge for the projects using it.

**Takes.** Recording several takes of something gives them the name you gave it plus a letter: **Verse riff A, B, C…** (or just **Take A, B, C** if unnamed). Star the ones you want, delete the ones you don't. Deleted takes go to the trash first, so a wrong tap can be undone.

## 7. Pitch Trainer — full scope

All on real piano sound (and your own recordings where noted), every exercise with hear-again, compare and replay-the-answer; levels as labels, nothing locked.

| Area | Exercises |
|---|---|
| **Pitch** | Note naming with and without a reference (relative and absolute pitch); octave placement; pitch matching by singing or playing (graded by the tuner); fine pitch: which is sharper, by how many cents |
| **Intervals** | All simple and compound, melodic up/down and harmonic; in context of a key; interval singing |
| **Scale degrees** | Functional hearing after a cadence or over a drone; solfège or numbers; chromatic degrees |
| **Scales and modes** | Identify scale/mode over a drone (all modes of major, melodic and harmonic minor, symmetric scales); characteristic-note drills (Dorian 6, Lydian ♯4, Phrygian ♭2…) |
| **Chords** | Triads, sevenths, sixths, extended and altered dominants, sus, add; inversions and bass note; open vs close voicing |
| **Progressions and harmony** | Roman numerals in major and minor; cadence types; secondary dominants, borrowed chords, Neapolitan, augmented sixths, tritone subs; modulation targets |
| **Melody and bass** | Melodic dictation; **bass-line dictation**; chord tones vs passing tones |
| **Rhythm** | Rhythmic dictation incl. tuplets, odd and additive meters, swing; tap-back accuracy; tempo estimation |
| **Tuning and timbre** | Temperament comparisons (equal vs just), beating, harmonic series partials |
| **Your own music** | Drills built from your Board: name the chords of your own progression, transcribe a phrase from your own take, identify the key/mode of your own riff, tab-it-by-ear from a recording |
| **Instrument link** | Answer on the fretboard of your own tuning or the piano, not only by name |

Mixed (interleaved) practice by default; extra focus on the intervals people confuse most (m6/M6, m7/M7, tritone). Curated exercise sets are yours to choose at the end.

---

## 8. Transcription — recordings into MIDI, tab and sheet music

**What it does:** turns a recording into editable notes, shown as MIDI, tab (for the take's instrument and tuning) and sheet music, next to the original recording on the Board and in the Library. The original audio is never changed; the transcription is its own version, time-linked to the take, so tapping a note plays that moment of the recording.

**Why this is now much more capable than earlier plans assumed:** the old toolchain had no native code, which ruled out neural models. With Android Studio and the NDK, an on-device model is possible:
- **Polyphonic notes:** Spotify's **Basic Pitch** (code and model under Apache 2.0; it ships TensorFlow Lite/ONNX versions) transcribes polyphonic instrument audio — chords, fingerstyle, piano parts — into notes with pitch bends. Run on-device through TensorFlow Lite or ONNX Runtime as a background job. **[research]** speed and accuracy on the S23 with phone-mic takes; model size in the APK.
- **Single lines** (bass, riffs, sung melody): pitch tracking (YIN/pYIN-style) plus onset detection in the app's own code, as a fast first pass and a fallback.
- **Chord names:** chroma-based chord recognition per beat, giving a chord chart even when individual notes are unclear.

**Pipeline:**
1. Recording → note events (pitch, start, end, loudness, confidence), with the take's instrument and tuning.
2. Timing: the metronome's grid if one ran, otherwise the estimated tempo; **quantising is a separate, reversible step** — you can see the unquantised notes against the waveform.
3. Outputs from the same notes:
   - **MIDI** (export or reuse in stacks/draft playback),
   - **tab**: string and fret chosen by the fingering chooser for your tuning, every position changeable,
   - **sheet music**: spelled in the take's key, written at the instrument's written pitch,
   - **chord chart** from the chord pass.
4. **Correction screen**: waveform and notes together, play original vs transcription, fix pitch, timing and string; corrections are kept.

**Honest limits:** bends, slides and vibrato come out as pitch wobble to clean up; dense mixes are much less accurate than a single instrument; phone-mic low bass may be read an octave off. Confidence is shown per note, and nothing is presented as exact.

**Transcription model, researched:** Basic Pitch's TensorFlow Lite version is **0.84 MB** and Apache-2.0; it takes 2-second windows of mono audio at 22,050 Hz and outputs note, onset and pitch-contour data for MIDI notes 21–108 (piano range, which covers drop-A bass). A published benchmark measured about 4.4 ms per 2-second window on a Pixel 8a's GPU, so a minute of audio is a fraction of a second there; the S23 still needs its own measurement. The project notes it works best on one instrument at a time.

### Real stem separation (researched, approved to pursue)

| Model | Licence of the trained weights | Fit |
|---|---|---|
| **Spleeter** (Deezer) | Code **and** pretrained models MIT; trained on Deezer's internal data | **Usable.** 2, 4 (vocals/drums/bass/other) or 5 stems (+ piano). Older (2019) and lower quality than newer models, but legally clean |
| **Demucs / HT-Demucs** (Meta) | Code MIT, but the author has said the weights are for research use; a newer Hugging Face upload is tagged MIT and the question is unanswered | Best quality, **not usable** until the weights' licence is clear |
| **Open-Unmix** | UMX-L weights CC BY-NC-SA (non-commercial); the MUSDB-trained ones' weight licence is unstated | **Not usable** in a GPL app as things stand |

**Plan:** Spleeter 4-stem (or 5-stem), converted to TensorFlow Lite or ONNX, run as a background job on a take or imported song, results saved as separate stems linked to the original; the adjustable isolator stays for quick, live use. **[verify]** run time and memory on the S23: the only phone figure found is an old hobby port taking 3–5 minutes (track length not given), so this must be measured. If Demucs's weights are ever clearly licensed, they'd be a drop-in quality upgrade.

### Offline speech-to-text (researched, approved to pursue)

| Option | Licence | Size | Notes |
|---|---|---|---|
| **Moonshine** (English models) | MIT, any use | Tiny ~26 MB, Base ~58 MB, Small streaming ~123 MB | Published error rates: Small streaming 7.84% vs Whisper Small 8.59%; has an Android library and streaming models. Non-English Moonshine models use a separate community licence |
| **Whisper** via whisper.cpp | MIT (code and weights) | base.en quantised ~57 MB | A published Android measurement: Pixel 6a, base.en, ~0.6× real time on CPU, ~0.25× with the GPU path |
| **NVIDIA Parakeet-TDT 0.6B** (via sherpa-onnx) | CC BY 4.0 (attribution) | Large | Higher accuracy, much bigger download |
| **sherpa-onnx** (runtime) | Apache-2.0 | — | Runs Moonshine, Whisper and Parakeet on Android, plus voice-activity detection |

**Plan:** Moonshine (English, MIT) as the default model — small enough to bundle, streaming, permissive — run so that the transcript is time-stamped against the voice memo, which is always kept. **[verify]** accuracy on mumbled lyric ideas and speech over an instrument, which no published benchmark covers.

---

## 9. Code stack (confirmed)

- **Kotlin**, **Jetpack Compose** for screens, custom drawing for the Board sheet, waveforms, tab and notation.
- **C++ audio engine on Oboe** (low-latency recording and playback for overdubs, stacks and the metronome).
- **Music core** (spelled pitch, chords, parser, rhythm, analysis) as a pure Kotlin/JVM module with its own tests.
- **Room/SQLite** for everything; audio files stored once and never modified (§6).
- **TensorFlow Lite or ONNX Runtime** for transcription models (§8), behind an interface so the app works without them.
- Builds run on his computer (Android Studio, SDK, NDK). If this chat is linked to that computer through the Claude desktop app, I can run Gradle builds and tests there; otherwise I write the code and he builds.

---

## 10. Settled Oct 21 (round 4)

| Item | Decision |
|---|---|
| Adding layers to a stack | Filter the Library (key, mode, tempo, instrument, Groups, search); no suggestions |
| "New / unreviewed" marker | None |
| Save-sheet flag | A recording made in a project shows in Library search by default; the save sheet has a toggle to keep it project-only (my call, he left it to me) |
| Click bleed | My call: the click keeps playing while recording. With headphones it can't reach the mic. On the phone speaker it will be audible in the take, so the record screen shows a small "click will be in the take" note with a one-tap **mute click while recording** option. Either way the click's beat grid is saved with the take |
| App id | `com.betterappmafia.apophenia` — the reverse of apophenia.betterappmafia.com, matching Independent Video's `com.betterappmafia.independentvideo`. Permanent after the first Play upload |

## 11. Still open

1. On-device measurements for Basic Pitch, Spleeter and Moonshine on the S23 (§8).
2. Mic default at launch: Off, per the approved architecture decisions, unless he says otherwise.
