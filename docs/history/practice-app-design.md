> **SUPERSEDED (Oct 21 2026).** Product direction now comes from `apophenia-architecture-decisions-2026-10-21.md` (user-approved) and `apophenia-unified-product-plan-2026-10-21.md`. Keep this file for history and for detail not covered there; where they differ, they win.

# Apophenia (working title was Practice App) — research and design (v3.1, Oct 20 2026)

v1 changes: instruments defined by string count + tuning + capo; scratch pad and drafts alongside songs; full sheet-music section with touch entry design.
v2 changes: adjustable isolator instead of fixed stem presets; piano is the only sample set; in-app microphone control and status; the **Workshop** (fragments in user folders, many-to-many); tab stores string+fret+pitch as one fact, with note names shown alongside tab; **Theory** reference section; section 8 lists what is still unknown and needs research.
v2.1: Theory and the shared music core moved to `music-core-and-theory-design.md`; notation data model takes any tuplet.
v3: changes from `plan-audit-1.md` — visual design and mockups before code; capture from outside the app (widget, tile, shortcut, pedal, volume keys); USB audio input; ideas with several parts and take versions; SQLite storage; mandatory backup; chart playback; note finder; take-to-tab transcription; FL Studio export package; revised build order. **Where this doc and the audit differ, the audit wins.**
v3.1: the app is **Apophenia**. `plan-audit-2.md` records Christopher's decisions on audit 1 (Stacks, Connections, take analysis with key/mode, transcription moved up, practice sessions replaced by curated Exercises, hardware to backlog, no tuner string mode) and the newest build order. **Order of authority: audit 2 > audit 1 > this doc.**

Working name: **Practice App** (Christopher to pick a real name and app id).
Scope he set: the *rough* side of music. Capture, understand, plan and practice; finished editing and recording happen later in FL Studio or a real session. The whole point is moving between tools without losing flow.

Status of every claim below: **research only**. Nothing is built, build-verified or device-verified yet.

---

## 1. What real products do (research first)

| Product | What it gets right | What we take |
|---|---|---|
| **TonalEnergy** (iOS/Android, $5.99) | Tuner, metronome, drone, piano keyboard, analysis, recorder in one; the target tuner is visible on every main page; tuner range C0–C8; custom temperaments; recordings with loop and timestretch. | Tuner always reachable from any screen. Wide low range matters for bass. Drone/pitch reference. |
| **Modacity** (iOS) | Practice items in lists, a timer per item, notes per item, a forced 1–5 rating after each item ("deliberate practice mode"), record and listen back at half speed; metronome and drone stay available while using any feature. | Practice items + per-item timer + rating + note. Tools that keep running across screens. |
| **Practice Pro** | 2–8 widgets per page all running at once, record while metronome and tuner run, tap tempo, auto tempo increase. | Simultaneous tools on one audio engine; tempo trainer. |
| **Practice Studio** | Clicks scheduled on an absolute sample timeline (no drift); every session auto-logged with pieces, tempos, duration; tempo-over-time charts; fully on-device. | Sample-accurate metronome; auto-log from what tools were actually used; tempo history. |
| **Practice Suite** (F-Droid, AGPL) | Metronome, tuner, recorder, log, stats — the open-source baseline. | Proof the basic set is common; we must beat it on flow, not feature count. |
| **Anytune / Transcribe+** | Slow down without pitch change (Anytune to 0.05×), pitch ±24 semitones, marks and loops, step-up loop trainer (speed rises each loop), count-in before each loop, foot-pedal/keyboard control, stem isolation (cloud/ML). | The learn-by-ear player: marks, A/B loops, step-up loops, count-in, pitch shift (so he can play a D-standard song on an E-standard bass without retuning). |
| **Suonote / Spit Notes / Song Cage / Songcraft** | The songwriting pattern: one project holds sections (verse/chorus/bridge), chords on a grid, lyrics inside each section, takes recorded into the same note; 1-tap recording on open; rhyme suggestions; MIDI and chord-chart export. | **Song = the container.** Takes, lyrics, chords, tab and notes live in the section they belong to. 1-tap capture. Rhymes. MIDI + ChordPro export for the DAW handoff. |
| **Functional Ear Trainer / Toned Ear / Sonofield** | The method most recommended: play a cadence (or a drone) to set the key, then identify the scale degree of a note; also intervals, chord quality, progressions, melodic dictation. | Functional ear training as the core mode, using real sampled sounds. |

**The gap none of them fill:** the songwriting apps don't have real practice tools, and the practice apps don't hold songs. Nobody connects "I'm learning this bass line by ear" → "I found a riff while practicing" → "it's now the chorus of a song with lyrics and a voice memo". That connection is the product.

---

## 2. Design principles

1. **Capture beats organization.** A record button reachable from every screen, one tap. It records into exactly one of two places: the open draft/song, or the **scratch pad** when nothing is open. Triage happens later.
2. **Tools are a rail, not destinations.** Metronome, tuner, drone and recorder live on a persistent bottom rail with live mini-readouts (BPM, note + cents, REC timer). They keep running while you move between screens. Opening a tool full-screen is optional.
3. **One audio engine.** Every sound source (click, drone, samples, player) mixes into one output stream; every input consumer (tuner, recorder, speech-to-text) reads one input stream. This is what makes "record while the metronome and tuner run" possible instead of tools fighting over the mic.
4. **Fragments are the unit; songs are optional.** Most of what gets captured never becomes a song, and shouldn't have to pretend to be one. It all lives in the **Workshop** (see 3.16):
   - **Fragments** — a take, a lyric line, a chord progression, a riff in tab or notation, a text note.
   - **Folders** — user-made groups, by whatever logic the user wants: a feeling, a genre, "maybe for the slow one". A fragment can be in any number of folders at once.
   - **Scratch pad** — fragments not in any folder or draft yet. Where capture lands.
   - **Drafts** — partial songs. Sections are optional; a draft can be three fragments and a title.
   - **Songs** — drafts with a settled structure. A song is a draft with its status changed, not a copy, so promoting loses nothing.
   Fragments are *linked*, never copied: the same riff take can sit in three folders and a draft and stay one file. A practice item can point at any fragment, section, or a loop in an imported track.
5. **Export, don't edit.** The DAW handoff is files: WAV takes, MIDI, ChordPro text, tab text. No waveform editing beyond trimming a take.
6. **An instrument is its string setup, not its model.** For scales, tab, chords and capo, the full spec is: **string count + tuning (a note per string) + capo fret**, plus fret count only to limit how far the fretboard is drawn (default 22 for guitar, 20 for bass, editable). "6-string, standard, capo 3" tells the app everything it needs.
   - Presets: guitar 6-string standard, drop D, D standard, DADGAD, open G, open D; 7-string standard; bass 4-string E standard, D standard, drop D, drop A (low string A0 = 27.5 Hz); bass 5-string BEADG; ukulele GCEA. Users add any custom tuning.
   - Capo shifts the sounding pitch; chord shapes and tab are shown relative to the capo (capo 3 + G shape = sounds B♭), with a toggle to show sounding names.
   - The lowest string also sets the tuner's analysis window (3.2).
   - Sound is always the piano sample set (3.9), whatever instrument is selected.

---

## 3. Feature-by-feature feasibility under our toolchain

Toolchain limits (from the export): Java only, no lambdas, no AndroidX, no NDK (so no TFLite/ONNX/FFmpeg), API 30 stubs with newer APIs through reflection, no emulator, single dex.

### 3.1 Metronome — feasible
- `AudioTrack` in streaming mode on a dedicated high-priority thread; clicks are mixed into the buffer at exact sample positions computed from BPM, so there's no drift (the Practice Studio approach) and no `Handler.postDelayed` timing.
- Meter, subdivisions, accents, swing, tap tempo, count-in, step-up trainer (+N BPM every M bars), silent-bar gaps (play 2 bars, mute 2) for internal time.
- Visual beat on the rail, because audio latency on Samsung is tens of ms; the visual flash is scheduled against `AudioTrack.getTimestamp()` so it lines up with what you hear.

### 3.2 Tuner — feasible, with a bass-specific constraint
- `AudioRecord` at 48 kHz mono → pitch detection with YIN or McLeod (MPM) in pure Java.
- **Drop A on bass is A0 = 27.5 Hz.** A period detector needs at least ~2 periods in the window: 2 / 27.5 Hz ≈ 73 ms → a 4096-sample window at 48 kHz (85 ms). D standard's low D1 is 36.7 Hz. A default guitar-tuner window (~1024–2048) would fail on his drop-A bass, so the window size follows the selected instrument's lowest string.
- Strobe-style or needle view, ±cents, A4 reference adjustable, per-string target list from the instrument preset.

### 3.3 Drone / reference tone — feasible
- Sustained sampled tone or simple synth on the engine; any root, optional fifth. Drives the functional ear trainer too.

### 3.4 Recorder / takes — feasible; costs the first permission
- `AudioRecord` → WAV (16-bit or float) in app storage. Needs **`RECORD_AUDIO`**, the first runtime permission any of these apps would request. There's no way to do a recorder, tuner or speech-to-text without it.
- Takes go to the open draft/song (section in view) or the scratch pad (rule in 3.16), with a timestamp and optional markers ("good bit at 0:42").
- Record while the metronome plays: by default the click goes to headphones only; with the speaker, the click bleeds into the take (Android has no reliable acoustic echo cancellation for music). Offer "click in take / not in take" as honest options.
- Pre-roll buffer (keep the last 10–20 s of input in a ring buffer while the tuner is listening) so pressing record after you played the riff still catches it. This is the single most flow-preserving feature in the list and it's cheap because the input stream already exists.

### 3.4a Microphone control and status — in the app, not buried in Settings
- **Mic state chip on the rail**, always visible, three states: **Off** (mic closed, nothing captured), **Listening** (open for tuner and pre-roll; nothing saved unless you press record), **Recording**. Tap to switch Off ↔ Listening. Android 12+ also shows its own green dot whenever the mic is open, and ours must match it.
- **Mic Off is a hard gate in the engine**: the `AudioRecord` is stopped and released, not muted. Tuner, pre-roll, play-it-in and speech-to-text all show "mic off — tap to enable" instead of silently failing.
- **Pre-roll is the trade-off, stated plainly:** catching the riff you already played requires the mic to be listening. A setting picks the default on launch (Off or Listening); pre-roll only exists while Listening.
- **Granting:** the system permission is asked the first time a mic feature is used, with one sentence on why, never at first launch.
- **Un-allowing the OS permission from inside the app:** an app can't revoke its own permission instantly. Android 13+ has `revokeSelfPermissionOnKill()` (through reflection; API 30 stubs): the permission is removed when the app process ends. So "Revoke microphone permission" = call it, say the app will close, then close. Older Android, or as a fallback: open the app's system settings page directly. **Unverified on device**; see section 8.

### 3.5 Learn-by-ear player — feasible
- Import any audio file with `ACTION_OPEN_DOCUMENT` (no storage permission); decode with `MediaExtractor` + `MediaCodec` (API 16+, in our stubs) to PCM.
- Time-stretch without pitch change: WSOLA (simpler, good on speech/monophonic) or a phase vocoder (better on full mixes). Pitch shift = stretch + resample. Pure Java on the S23's CPU should handle real-time stereo; **unverified on device**.
- Waveform overview, marks, A/B loop, step-up loop (Anytune/Transcribe+ pattern), count-in before each loop, loop → "save as practice item".

### 3.6 Isolator (instead of stems) — adjustable, the user dials it in
- Real stem separation (Demucs, Spleeter, Open-Unmix) is a neural network: no NDK, so no inference runtime, and no cloud (privacy-first). So no ML stems. What we build is an **adjustable isolator**: three DSP stages, each a continuous control, with presets as starting points only.

| Control | What it does | Range |
|---|---|---|
| **Stereo position** | Mid/side mask: keep or remove what's panned to a chosen position, with a width. Centre = usually lead vocal, often bass and kick. | Position L–C–R, width narrow–wide, amount −100% (remove) to +100% (isolate) |
| **Tone ⇄ hit** | Harmonic/percussive balance (FitzGerald 2010 median filtering, soft masks). Left keeps sustained pitched parts; right keeps drums and attacks. | −100% … 0 (off) … +100%; plus a "strictness" control (mask exponent) |
| **Band** | Band-pass: low cut and high cut with slope, plus a boost of what's inside. | 20 Hz–20 kHz each; 12/24 dB/oct |

- Presets (just slider positions): **Remove vocal** (centre −100%, narrow), **Bass focus** (tone −70%, band 30–300 Hz, boost), **Drums** (hit +80%), **Guitar-ish** (sides +, band 200 Hz–5 kHz). Moving any slider leaves the preset.
- **Why it can be live while you drag:** the expensive part of HPSS is the two median-filtered spectrograms, and they don't depend on the slider settings. Compute them once for the loop region (the thing you're practising), cache them; dragging a slider then only recomputes masks and the inverse FFT. Mid/side and band are cheap per sample.
- **Memory limit, real numbers:** a 4-minute track at 48 kHz, 4096-point FFT, hop 1024 ≈ 11,250 frames × 2,049 bins. Two cached spectrograms as 16-bit ≈ 92 MB — too much to hold for a whole song. So: live isolator on the loop region (30 s ≈ 12 MB), and "render whole track" as a background job to a file.
- The UI says plainly it's a filter, not a stem; a bypass button A/Bs it instantly. Ships after the player works.

### 3.7 Speech-to-text for "thinking out loud" — feasible on paper, needs device testing
- Android's own `SpeechRecognizer.createOnDeviceSpeechRecognizer()` (API 31) runs in Google's speech service on the phone; our app keeps zero internet permission. Called through reflection (our stubs are API 30). Check `isOnDeviceRecognitionAvailable()` first.
- Google's docs say the API isn't meant for continuous recognition, so long thinking-out-loud needs **segmented sessions** (`EXTRA_SEGMENTED_SESSION`, API 33) or restarting sessions on each end-of-speech.
- **Key trick:** `EXTRA_AUDIO_SOURCE` (API 33) lets us hand the recognizer our own audio stream. That means one mic capture feeds the recorder *and* the transcriber, so the audio take and the transcript stay together with timestamps, and the two don't fight over the mic.
- Risks: on-device model must be downloaded on the phone; accuracy on mumbled lyric ideas will be mediocre; Samsung may route to a different recognizer. All **unverified on device**. Fallback if it fails: record-only, transcribe later from the take.

### 3.8 Reference: chords, scales, keys — feasible
- Generated, not hand-entered: chord formulas (intervals) × instrument tuning → voicing finder (playable within a 4–5 fret span, root position / inversions, bass-friendly root-fifth-octave shapes). Scales and modes as fretboard and keyboard overlays; circle of fifths; diatonic chords of a key; "what key fits these chords".
- Tap any chord/scale to hear it with real samples (3.9).

### 3.9 Sounds — piano only
- **University of Iowa Musical Instrument Samples, Steinway piano**: public domain ("without any restrictions"), recorded note by note at three dynamics (pp/mf/ff), stereo, in a small teaching studio. Fine for GPL, Play, anything.
- Every note, chord, scale, interval, ear-training question and notation playback uses this one piano, whatever instrument is selected. The piano's lowest key is A0 (27.5 Hz), the same note as a drop-A bass's low string, so it covers every preset tuning.
- Size plan: one dynamic (mf), one sample every minor third (~30 samples), each repitched ±1 semitone to cover neighbours, trimmed to ~2–3 s with a fade, encoded Ogg Vorbis (decodes through MediaCodec) → roughly 2–4 MB. Chords = several voices summed on the engine with a short stagger option (strum).
- Known issue reported by users of the set: clicks on some ff samples; we use mf and check each file (section 8).

### 3.10 Lyrics — feasible
- Lyric editor per section, chord markers inline (ChordPro `[G]` style), syllable count per line, offline rhyme lookup from the CMU Pronouncing Dictionary (license to verify before bundling; ~3–4 MB text, can be compacted).

### 3.11 Song structure — feasible
- Section list (intro/verse/pre/chorus/bridge/outro, custom), bars per section, chord grid per section, arrangement order with repeats, tempo/key/time signature per song.
- Export: **ChordPro** (open text standard, current v6, sections via `start_of_/end_of_` directives, tab blocks via `{sot}`/`{eot}`), **MIDI file** of the chord progression at song tempo (drag into FL Studio), WAV takes, all zipped per song.

### 3.12 Tab and sheet music — both feasible, as two views of one note model

**What real notation apps do for entry (research):**
- **Flat** (Android/iOS/web): pick a duration from a toolbar, tap the staff where the note goes; or enter through a touch piano, guitar fretboard or drum pads; MIDI keyboard input; tab and staff in one editor; exports MusicXML, MIDI, PDF.
- **Dorico for iPad**: on-screen multi-touch piano, fretboard and drum pads; MIDI keyboard in step time or real time; a piano-roll Key Editor shown alongside the score; a touch toolbar for nudging notes up/down or by a 16th.
- **StaffPad**: write by hand with an active pen; the bar is recognised and typeset (in Bravura) when the pen leaves it. It uses its own recognition engine — that's machine learning, which our toolchain can't run (no NDK/inference runtime) and a hand-rolled recogniser would be error-prone. **So no handwriting recognition.** The S Pen is still useful as a precise pointer (below).

**One note model, three views.** Each note stores **string + fret and pitch together, as one fact**, plus start (ticks), duration, voice (1–2) and tie. Tab, staff and piano roll are views of the same list. Pitch = open-string pitch of that string in the tuning + capo + fret; that's exact, never estimated, and it's stored, not recomputed on display.
- Enter on tab → string, fret and pitch are all known at once.
- Enter by pitch (staff tap, piano key, play-it-in, MIDI) → the pitch is known, but the same pitch usually exists on 2–4 strings (A2 is the open A string *and* the 5th fret of the low E), so a position has to be *chosen*, once, at entry: the app proposes one (nearest to the previous note's hand position), shows it on the fretboard panel, and a tap moves it to another string. After that it's stored like a tab entry. Entering straight on the fretboard panel avoids the choice entirely.
- **Changing the tuning or capo of a draft** asks one question, because both answers are valid: *keep the notes* (same pitches, positions re-chosen — "I tuned down to D standard, show me how to play this now") or *keep the fingering* (same frets, pitches move — "the riff is the shape; hear it in the new tuning").

**Seeing the notes inside the tab (the learning link):**
- **Note row** under each tab line: the pitch name of every fret number (E, G, A, B♭…), aligned to it. Toggle: names, scale degrees relative to the key (1, ♭3, 4, ♭5, 5), or intervals from the chord root (R, m3, 5).
- **Notes-on-tab mode:** replace the fret numbers with note names on the strings, as a drill ("which fret is this note?" hides one or the other).
- **Linked highlight:** tap a fret number → the same note lights on the staff, the piano, and the fretboard panel (with every other place that pitch exists on the neck dimmed in), and sounds.
- During playback the cursor highlights the tab number, its note name and the staff note together, so the hand shape and the sound are seen as one thing.

**Entry methods, in build order:**
1. **Duration palette + tap the staff** (Flat). Pick ♩, tap a line/space; the cursor advances. Drag up/down before lifting to adjust pitch, with the note name shown and the note sounding as you drag. Key signature applies accidentals automatically; ♯/♭/♮ buttons override.
2. **Instrument panel at the bottom** (Dorico/Flat): piano keys, or the fretboard of the current tuning and capo. Tap a key or fret → note at the cursor with the selected duration. Tap two keys at once → a chord.
3. **Play it in from your instrument.** Our own addition, because the tuner's pitch detector is already running: play a note on the bass, it lands at the cursor (step entry — rhythm from the palette). Monophonic only; chords from audio aren't reliable.
4. **Tap the rhythm, then the pitches.** For people who think rhythm-first: tap a pad against the metronome, the app quantises to the chosen grid (8ths/16ths/triplets) and lays down rhythm-only notes; then step through them assigning pitches by any method above.
5. **MIDI keyboard** step time and real time (android.media.midi, 3.13), quantised to the grid.
6. **S Pen:** hover shows a ghost notehead at the pitch under the tip (S Pen hover arrives as `ACTION_HOVER_MOVE`), so placement is exact before touching. Unverified on device.

**Behaviour that makes it feel intuitive, not just possible:**
- A note longer than the space left in the bar is **split and tied across the barline automatically**; incomplete bars are filled with rests, which are replaced as you write.
- Eighths and shorter are **beamed by beat automatically**; you never draw beams.
- Undo for every edit; long-press a note for a menu (duration, accidental, tie, delete, voice); select a range with two taps, then transpose/copy/paste.
- Playback with a moving cursor through the engine's sample player, at any tempo, loopable — the fastest way to hear a wrong note on a phone screen.
- Lyrics under notes come from the section's lyric text, one syllable per note (tap to advance); chord symbols above the staff come from the same chord data as the chord grid.

**Rendering:** Canvas, glyphs from **Bravura** (SMuFL reference font, SIL Open Font License: may be bundled with free or commercial software; can't be sold alone; a modified/subset font can't keep the name "Bravura"). Bundle the unmodified .otf (~500 KB) rather than subsetting, to avoid the renaming rule. Layout is rough on purpose: bars flow left to right and wrap; spacing roughly proportional to duration. Good enough to read and play from, not to publish.

**Scope limit (honest):** one instrument per staff, up to two voices, chords, ties, dots, key/time signature changes (incl. additive meters), simple articulations. The data model stores any tuplet ratio, nested; rendering starts with triplets and quintuplets and draws other ratios as bracket + number. No multi-part scores, no engraving polish. Anyone needing more exports **MusicXML** (opens in MuseScore, Dorico, Flat) or MIDI and finishes there — consistent with the rough-side scope.

**Tab output:** plain-text tab and ChordPro `{sot}`/`{eot}` blocks, plus MusicXML (which carries tab).

### 3.13 MIDI — feasible
- `android.media.midi` (API 23, in our stubs) for USB/Bluetooth MIDI in: play ideas on a keyboard, capture as MIDI into the song, drive the tuner-free "what chord is this?" helper. (His microKORG has 5-pin DIN MIDI, so it would need a USB-MIDI interface.)
- MIDI file export written by hand in Java (format 0/1, simple).

### 3.14 Practice log — feasible
- Practice items (a technique, a song section, a loop in an imported track, an ear-training mode). Per-item timer, target tempo, achieved tempo, 1–5 rating and a note after each item (Modacity's deliberate-practice prompt).
- Auto-log from the engine: if the metronome ran at 92 BPM for 6 minutes on the open item, that's logged without typing. Tempo-over-time chart per item. Session history, streaks, totals by item.

### 3.15 Ear training — feasible
- Functional mode first: cadence or drone sets the key, identify the scale degree; then intervals, chord qualities, progressions, short melodic dictation. Real samples from 3.9; optionally sing-back graded by the tuner's pitch detector (Sonofield's voice mode).

### 3.16 The Workshop — fragments, folders, drafts, songs (data model)
- **Fragment** (the unit): id, type (take | lyric | chords | tab/notation | text), created time, optional title, content. Takes carry the WAV file id, markers and an optional transcript; tab/notation fragments carry the note list from 3.12.
- **Folder**: id, name, colour/icon, user order. Holds fragment *links*. Nestable one level (e.g. "Feelings ▸ Dread")? — open decision 5.
- **Draft**: id, title, status (`draft` | `song`), key/tempo/time signature (all optional), and either a flat list of fragment links or sections that each hold fragment links. A draft with no sections is valid.
- **Links are many-to-many.** A fragment can be in any number of folders and drafts at once. Deleting a folder or draft deletes links, never fragments.
- **Scratch pad** is a computed view, not an object: fragments with no links at all. So filing a fragment just adds a link, and a fragment whose last link is removed falls back to the scratch pad rather than vanishing.
- **"Used" tracking, for the "it fit in several folders but only got used in one" case:**
  - Each fragment shows badges for where it's linked, and a separate **Used in** mark when it's in a draft or song ("Used in: Neon Teeth · Chorus").
  - When you place a fragment into a draft, a one-line sheet offers: *Also remove from: ☐ Dread ☐ Slow ones ☐ 6/8 ideas* — nothing is ticked by default, because sometimes you want to keep it filed. One tap cleans up.
  - Each folder can filter "unused only", so a folder of candidate riffs shrinks as they get used, without anything being deleted.
- **Sorting and finding:** sort by date, title, type, last used; filter by type (only takes, only lyrics), by key, by tempo range; search titles, text, lyric content and transcripts.
- **Recording target rule:** if a draft or song is open, the take becomes a fragment linked to it (the section in view, if it has sections); otherwise it lands in the scratch pad. The rail's record button says which ("REC → Scratch pad" / "REC → Neon Teeth · Chorus").
- **Triage gestures:** long-press a fragment → multi-select → "add to folder…" / "add to draft…" / "new draft from these". Drag onto a folder in a side strip.

### 3.17 Theory — see `music-core-and-theory-design.md`
- The Theory section, and the shared music core every feature uses (spelled pitches, intervals, chord structures, symbol storage/rendering/parsing, rhythm with any tuplet and additive meters), are designed in that companion doc. Full depth everywhere: levels are labels, never gates.

---

## 4. Architecture

- **Single Activity, custom Views** (no AndroidX). Screens: Workshop (scratch pad, folders, drafts & songs), Draft/song editor (sections), Notation/tab editor, Practice, Player, Reference, Theory, Ear, Settings. Persistent **tool rail** view at the bottom, outside the screen container, so it survives navigation.
- **AudioEngine** (one class, owns both streams):
  - Output thread: one `AudioTrack` stream; sources implement `render(float[] buf, int frames)` — Metronome, Drone, SamplePlayer, TrackPlayer. Mixer sums them with per-source gain.
  - Input thread: one `AudioRecord` stream into a ring buffer; consumers read from it — Tuner, Recorder (incl. pre-roll), Transcriber feed.
  - Audio threads don't allocate or lock; UI talks to them through atomics/lock-free queues (same rules as the threading skill, adapted from VST3 to Android).
- **Foreground service** (`mediaPlayback|microphone` types) while recording or the metronome runs with the screen off — so a take isn't killed when the screen locks. Android 14+ requires the right foreground-service type and permission declarations; to verify on the S23 (Android 16).
- **Data**: SQLite database (Android built-in) for ideas, parts, folders, drafts, links and practice data, with full-text search; audio as WAV files referenced by id; backup/restore of both to a user-picked folder. Export/import through the system file picker; no storage permissions. (Was per-song JSON; changed in `plan-audit-1.md`.)
- **Tests**: desktop JVM suites for pure code — pitch detector against synthesized tones (including 27.5 Hz), metronome sample positions, voicing finder, ChordPro/MIDI writers, time-stretch length/pitch accuracy, HPSS on synthetic mixes. Audio I/O, latency and speech recognition stay in the phone walkthrough.

---

## 5. Build order (each phase ends with a phone walkthrough)

**Superseded by the revised order in `plan-audit-1.md` section F (P0 probe + mockups through P8).** The table below is kept for history.

| Phase | Delivers | Why this order |
|---|---|---|
| **0.1 Rail + engine** | AudioEngine, metronome (sample-accurate), tuner (tuning-aware window), recorder with pre-roll, mic chip (Off/Listening/Recording) and permission controls, scratch pad of takes and text notes. | Everything else rides on the engine and the rail; if switching tools isn't smooth here, the app's premise fails. |
| **0.2 Workshop** | Fragments, folders (many-to-many), drafts with optional sections, promote to song, used-in tracking and the remove-from-folders sheet, recording target rule, lyrics with inline chords, chord grid, search/sort. ChordPro + WAV export. | Gives captured fragments somewhere to go without forcing song structure. |
| **0.3 Reference + Theory + piano** | Theory engine, Iowa piano sample player, chords/scales/keys on fretboards for any tuning and capo, the Theory section pages. | Theory and Reference share one engine and one sound source. |
| **0.4 Practice log** | Items, per-item timer, ratings, auto-log from tools, tempo charts, step-up trainer. | Uses rail data from 0.1. |
| **0.5 Player** | Import, loops/marks, slow-down, pitch shift; then the adjustable isolator (stereo position, tone ⇄ hit, band) on the loop region, whole-track render in background. | Heaviest DSP; isolated so it can't destabilize earlier phases. |
| **0.6 Speech-to-text** | On-device recognizer fed from our input stream; transcript tied to take timestamps. | Highest device risk; probe it early with a throwaway test build, ship it here. |
| **0.7 Tab** | Note model (string+fret+pitch), tab editor for any tuning/capo, note row (names / degrees / intervals), linked highlight, retune question, text/ChordPro tab export, playback. | The note model is shared with notation; tab is the simpler view to prove it on. |
| **0.8 Sheet music** | Staff view on the same notes, Bravura rendering, entry methods 1–4 (palette+tap, instrument panel, play-it-in, tap rhythm), auto ties/beams, MusicXML + MIDI export. | Biggest UI piece; needs the note model and sample player working first. |
| **0.9 Ear + MIDI** | Functional ear trainer, MIDI keyboard in (also notation entry method 5). | Uses everything above. |

Recommendation: do a **probe build** right after 0.1 that only tests on-device speech recognition and audio latency on the S23, because those two decide whether 3.7 and the visual metronome sync work as designed.

---

## 6. Honest risks

- **Scope.** "Best all-in-one" apps usually die of breadth. The differentiator is flow between tools, so 0.1's rail and pre-roll matter more than any later feature. Each phase should be usable on its own.
- **No true stems.** The isolator is rough by nature; adjustable sliders make it more useful, not cleaner. If true stems become essential, it requires a toolchain change (NDK + an inference runtime), not more Java.
- **Speech recognition** depends on Google's service on the phone, not on our code.
- **Phone mic vs. low bass.** Phone mics are tuned for voice and typically roll off well above 27.5 Hz, so on a drop-A bass the tuner may be hearing mostly harmonics. Period detectors can still find the pitch from harmonics, but octave errors get likelier. Needs testing on the S23 with the real bass (section 8).
- **Latency.** Monitoring yourself through the phone isn't a goal; record, then listen back.

---

## 7. Open decisions (Christopher's)

1. App name and app id (and studio: Better App Mafia?).
2. Channels and license: Play + GitHub + F-Droid like Independent Video (GPL-3.0-or-later)? This affects which sample/soundfont/rhyme-data licenses are allowed.
3. Speaker click bleed default: click excluded from takes (headphones) vs. included.
4. Whether drafts and songs are one list with a status filter (recommended: promoting is just a status change) or two separate screens.
5. Folders: flat, or one level of nesting ("Feelings ▸ Dread")?
6. Mic default on launch: Off (most private) or Listening (pre-roll works immediately)?

---

## 8. What I don't know yet (research needed before or during the build)

Each item says what's unknown, why it matters, and what would settle it: a research file from Christopher, a test on the S23, or something I can check myself in the workspace.

**Device behaviour (settled only by a test build on the S23):**
1. **Which `AudioRecord` source gives a clean signal on the S23.** `VOICE_RECOGNITION`, `UNPROCESSED` (API 24, `PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED`) or `MIC` differ in gain control and noise suppression, which can wreck tuner readings and recordings. Probe build logs all three.
2. **Mic low-frequency response** with the real bass in drop A and D standard: does YIN/MPM lock onto A0/D1 or jump an octave? Probe build records test clips I can analyse.
3. **Output latency** (`AudioTrack` low-latency mode, API 26) and whether the visual beat lines up — decides the visual metronome design.
4. **Output and input streams open at once** in one app on Samsung's audio stack, without glitches.
5. **On-device speech recognition:** whether `isOnDeviceRecognitionAvailable()` is true on his S23, which recognizer Samsung routes to, whether the on-device model must be downloaded, and whether `EXTRA_AUDIO_SOURCE` (feeding our own stream) and segmented sessions actually work with that implementation.
6. **`revokeSelfPermissionOnKill()`** behaviour on Android 16 One UI, and the foreground-service rules for a microphone service on Android 14–16 (can it continue with the screen off; what starting from the background is refused).
7. **Java DSP speed:** real-time time-stretch on a full stereo mix, and HPSS median filtering for a 30 s loop — how long it takes on the S23.

**Research files that would help (things I'd otherwise be guessing at):**
8. **Notation engraving rules** for the subset we draw: beaming groups per time signature (4/4, 3/4, 6/8, 12/8, odd meters), how rests are grouped and split, tie-vs-dot choice, stem direction, accidental persistence within a bar. Source: Elaine Gould, *Behind Bars* or an equivalent summary. Without it, notation will be readable but break conventions a reader notices.
9. **Tab fingering choice** (picking string/fret for a pitch entered from the staff): published methods (dynamic-programming fingering, e.g. Sayegh 1989 and later work) and what real tab editors (Guitar Pro, TuxGuitar, MuseScore) do. Mine would be "nearest to previous hand position", which is reasonable but not researched.
10. **Theory curriculum and conventions:** covered by the Oct 20 research report; what's still open is listed in section 11 of `music-core-and-theory-design.md`.
11. **Ear-training method details:** how Functional Ear Trainer / Alain Benbassat sequence levels (which degrees first, when to add chromatics, cadence choice), so our progression isn't invented.
12. **Time-stretch algorithm choice for full mixes in pure Java:** WSOLA vs phase vocoder with phase locking vs other — quality comparisons and implementation notes.
13. **HPSS soft-mask parameters** that work well for practice (filter lengths, margin, mask exponent) — FitzGerald 2010 and Driedger et al. 2014 give starting points; real-world tuning notes would help.

**Assets and formats I need to fetch or have handed to me:**
14. **Iowa MIS piano files.** The University of Iowa site is almost certainly outside the workspace's network allowlist, so I likely can't download them. Need: the mf piano files attached (or the whole piano set zipped), plus the exact file naming and note range, and I check each for clicks.
15. **Bravura.otf** — on Steinberg's GitHub (steinbergmedia/bravura); GitHub release downloads are usually reachable from the workspace. I'll check; if blocked, need it attached.
16. **CMU Pronouncing Dictionary** (rhymes) — on GitHub; need its current license text confirmed before bundling.
17. **MusicXML** — exact elements for tab (`<technical><string>`/`<fret>`), and which subset MuseScore, Dorico and Flat import cleanly. I know the outline; a spec excerpt or test files from those apps would settle it.
18. **Play policy for an app holding RECORD_AUDIO** with no internet: Data Safety form answers and any declaration needed for a microphone foreground service.

**Christopher's own decisions** are in section 7.

## Sources
- TonalEnergy App Store listing; Colin Dorman's TonalEnergy review
- Modacity App Store listing; Double Bass Blog and Clarinet Chronicles on Modacity
- Practice Pro, Practice Studio, Practice Suite (IzzyOnDroid) listings
- Anytune, Transcribe+ listings
- Suonote, Spit Notes, Song Cage, Songcraft/Hookpad overviews
- Functional Ear Trainer / Toned Ear / Sonofield descriptions
- Android SpeechRecognizer reference; RecognizerIntent API 33 diff (EXTRA_SEGMENTED_SESSION, EXTRA_AUDIO_SOURCE)
- FitzGerald 2010, "Harmonic/Percussive Separation using Median Filtering" (DAFx)
- University of Iowa MIS (public domain statement)
- ChordPro format (chordpro.org, Wikipedia)
- Flat (Google Play/App Store listings, Flat blog: phone note entry, Flat vs StaffPad)
- Dorico for iPad (Steinberg announcement, Scoring Notes review)
- StaffPad (Scoring Notes, Sound On Sound reviews)
- SMuFL / Bravura font licence (smufl.org, Steinberg blog)
