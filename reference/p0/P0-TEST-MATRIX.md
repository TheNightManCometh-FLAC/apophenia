# Apophenia — P0 acceptance checklist (phone test; not yet performed)

Test device: Samsung Galaxy S22 Ultra (`SM-S908U`), Android 16 — confirmed over adb on 21 Oct 2026 (earlier notes said S23 Ultra). The repo is **not yet an installable APK** in this workspace because there is no Android SDK / Gradle build environment here.

**When running tests, use the supplied P0 Probe app only**: no upload, no analytics, no internet permission. Mic recording on test screens is short-lived and never saved. The user manually presses each test button.

## P0 gates

- [ ] Android Studio imports Gradle project, syncs AGP 8.13.0, and builds a `debug` APK with targetSdk 36 and minSdk 26.
- [ ] APK installs on Galaxy, launches and shows P0 buttons.
- [ ] Test 1 displays device, output sample rate and frames/buffer.
- [ ] Test 2 RECORD_AUDIO permission appears only when requested; denial does not crash the app.
- [ ] Tests 3–5 each try 8 seconds of mono 48 kHz from UNPROCESSED, VOICE_RECOGNITION and MIC; record which initialize and RMS, and whether the audio is compressed/filtered audibly (actual quality inspection requires a separate recording later).
- [ ] Test 6 runs mic and output concurrently; confirm no initialization failure. The 440-Hz tone is quiet, but acoustic bleed may still occur. Use headphones if loudspeaker feedback occurs.
- [ ] Test 7 `System.loadLibrary("apophenia_probe")` succeeds and logs `Loaded ...`; failure means bundled ARM64 `.so` handling needs correction. **Does not prove ML feasibility.**
- [ ] Test 8 renders U+1D12A; inspect glyph visually. If a missing glyph, render from an OFL font later, not a text fallback guess.
- [ ] Test 9 availability; 9b speaks one phrase into real on-device SpeechRecognizer (if installed). Test only proves that phone's configured service, not `EXTRA_AUDIO_SOURCE` injection.
- [ ] Share probe log using native `ACTION_SEND` and attach it to the next project iteration; include model/API and exact success/fail details.

## Analysis input fixtures for P2/P3 benchmark

Record `bass_Dstd_92_click.wav`, `bass_Dstd_92_noclick.wav`, `bass_dropA_low.wav`, `guitar_strum_C_G_Am_F.wav`, `voice_dorian_short.wav`, `voice_free_time.wav` (or equivalent originals). Use only recordings you control. For each, record actual tempo/time-signature and expected played notes, when known. Save recordings **locally** and do not share recordings without consent.

| Category | Baseline test | Failure to detect |
|---|---|---|
| Tempo | 5 / 10 / 20 / 60 sec, 50–200 BPM, half/double traps, clicks/no clicks | returns implausibly confident 92 vs 184, even during free time |
| Mode | D Dorian, C Ionian (identical notes), one-note riff, chromatic passing notes | falsely asserts mode when tonic unsupported |
| Bass | A0 27.5 Hz, E1 41.2 Hz, different pluck attacks; quiet room and phone noise | octave jumps, false fundamentals, dropouts |
| Melody | separate same-note attacks, slides, held vibrato, bends | merges note reattacks or chops pitch wobble into notes |
| Chords | clean 3-note triads, 7ths, omitted fifth, partial strum, inversions | labels unsupported extensions or claims exact constituent notes |
| Time-stretch | 92→96 (+4.35%), 92→110 (+19.56%), -2 and +2 semitones | loop seam clicks, wrong duration or bass smearing |
| Lyrics | strong/weak stress, rhyme `light/night`, name not in dictionary, melisma | false perfect rhyme, no OOV state, alignment impossible |

**Expected:** qualitative listening scores and a small ground-truth set first; do not publish accuracy percentages until sufficiently diverse real data exists.

## Build and supply-chain notes

- `gradlew` not included: open the project with Android Studio or install compatible Gradle 8.13 (or add the wrapper in an SDK-enabled environment). The wrapper jar is not faked here.
- The P0 ELF marker was built without an NDK sysroot, as a dependency-free ARM64 shared object. Use `file`/`readelf` to inspect it. Verify on device before assuming Android dynamic-loader compatibility.
- No third-party models, full CMUdict data, SoundTouch/Rubber Band libraries, or sample banks are bundled yet; inclusion is a later explicit step with license audit.
- **Signing keys:** debug install only. Keep future Play signing/upload keys outside source zip. Do not upload this experimental application to Play before confirming app id.
