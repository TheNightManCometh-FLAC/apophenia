---
name: apophenia-working-rules
description: Use at the start of every Apophenia work session and before planning a feature, acting on an audit or review, troubleshooting, finishing a milestone, or preparing a release. Holds Christopher's process rules, evidence labelling, the status/walkthrough routine and the release channels.
---

# Apophenia working rules

## Before building anything
0. Use what earlier projects already learned: load `anthropic-skills:android-apk-builder-plus` (lessons from his earlier Android apps; its no-Gradle toolchain doesn't apply here) and read `docs/reference/app-building-export-independent-video.md`. For DSP and real-time audio, the user-level audio-* and vst3-threading-* skills; for worker→audio handoff, `C:\Users\Wi-Fi\sunsine\.claude\skills\rt-worker-generation-handoff\SKILL.md`.
1. Re-read the relevant section of `docs/plan/apophenia-official-plan.md` and `docs/STATUS.md`.
2. If the feature has a real-world equivalent, look at what real products do (and primary sources/specs) before designing. Don't guess API names, parameters or file formats — look them up.
3. If a user-facing decision isn't in the plan, **ask Christopher**. Don't fill gaps with your own product decisions; earlier planning passes did that and he rejected the results. Implementation choices are yours — state them.
4. Prefer simple, universal approaches over clever ones.

## While building
- Reason through concrete steps; be specific (named libraries, versions, the S22 Ultra (SM-S908U) on Android 16).
- Keep a running "don't know yet" list in `docs/STATUS.md` and tell him when research is needed; he can hand over research files.
- Verify any external audit or review claim in the source before acting; push back with reasons on wrong or low-value items.
- Big features go in new classes/modules, not rewrites.
- Never rebuild views inside a touch dispatch; post and guard re-entrancy.
- Data formats: version every stored format; older builds refuse newer data rather than silently dropping fields; migrations tested.

## Evidence labels (use them in every report)
**source-only** · **unit-tested** · **build-verified** · **device-verified** (installed and run on the S22 Ultra) · **ear-verified** (audio judged by listening). Never blur them. Nothing is "done" until walked through on the phone.

## End of every milestone
- `docs/walkthroughs/Mx.md`: exact steps for Christopher to try on the phone, with headphones and speaker, plus interruption and forced-kill cases.
- `docs/STATUS.md`: what shipped, tests and counts, Device Lab measurements (device, date), known limits, and an **unverified on device** list.
- Bump versionCode/versionName for anything installable.

## Releases (M7 and after)
- Every update ships to **Google Play (AAB), GitHub release (APK), F-Droid (built from source by F-Droid)** in one pass. Christopher uploads to Play and creates GitHub releases/tags; Claude prepares artifacts, changelog and notes.
- `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (≤500 chars) feeds all channels.
- Keep signing keys stable and **never commit them**. Play, GitHub and F-Droid builds are signed differently; cross-channel updates aren't possible (expected).
- Licence notices for every bundled third-party piece: Oboe (Apache-2.0), Rubber Band (GPL-2.0-or-later) or Signalsmith Stretch (MIT), TensorFlow Lite / ONNX Runtime / sherpa-onnx (Apache-2.0 / MIT), Basic Pitch (Apache-2.0), Spleeter (MIT), Moonshine English (MIT), Bravura (SIL OFL), Iowa MIS piano (public domain), chord fixtures (CC0 / CC BY 4.0 attribution), any Open Music Theory text adapted (CC BY-SA 4.0 → GPL-3.0, attribution + modification notice).
- Release history and conventions from Independent Video (AAB checks, F-Droid recipe, fastlane layout): `docs/reference/app-building-export-independent-video.md`. Its custom no-Gradle toolchain does **not** apply to Apophenia.

## Delivering files to Christopher
Send every installable build straight away with SendUserFile (display attach): he downloads it in the Claude app on his phone and installs it. Over 30 MiB, zip it first (`"C:\Program Files\7-Zip\7z.exe" a -tzip -mx=9`); don't rebuild to shrink it. Keep a copy in `dist/` (gitignored). adb installs are optional, never the gate: his USB link to this PC is unreliable.
Don't build smoke tests, probe builds or demo screens for plumbing he has already shipped in other apps; go straight to the real feature.
