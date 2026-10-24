# App-building knowledge export (from the "Independent Video" project, Oct 20 2026)

Upload this file to the new project as a doc. It collects everything about building apps that lives only in this project: memory, project docs and past chats. Feature history for each app stays in that app's status doc (list at the end).

---

## 0. What moves to a new project on its own, and what doesn't

**Moves by itself (account-level):**
- The skills `android-apk-builder` (toolchain, scripts, template, debug key), `android-apk-builder-plus` (the living lessons file, ~1,000 lines) and `android-music-visualizer`.
- General memory outside Projects (profile, preferences).

**Does NOT move:**
- This project's docs (`emulsion-status.md`, `independent-video-status.md`, `afterglow-status.md`, the 3.12 gap notes, the implementation plan).
- This project's memory (Android Lab notes, Independent Video facts, BAM, the research-first rule).
- Past-chat search. It is scoped to the project, so a new project can't search these chats.
- Workspace files. Every session starts with an empty machine: `~/.apklab-tools`, `~/tools/glcheck.py`, test shims and any unpushed source are gone.

So the new project needs: this file, the status docs of any app being continued, and the GitHub repo for Independent Video (its source of truth).

---

## 1. Standing instructions from Christopher

- **Research real products first, then design.** Before designing any feature, look at what real products in that space actually do (e.g. MilkDrop, cava, Resolume for visualizers; LumaFusion, CapCut, Resolve for editing). He wants this for every project of any kind.
- **Simple, universal approaches beat clever ones.** Example: music sync must work on any track, so no stems, drum-only detection or chord detection.
- **Keep the APK builder skill updated** as better practices and more complex apps come up. The plugin skill can't be edited; updates go to `android-apk-builder-plus` through `propose_skills`. Read its current on-disk SKILL.md first, because it may already hold more than the last proposal remembered.
- **Say what was verified where.** Mark each claim device-verified, build-verified, desktop-tested or glcheck-tested. There's no emulator; anything not run on the phone is "unverified on device".
- **Verify external audits in source before acting.** Confirm every claimed defect in the code, push back on infeasible or low-value items, and give the reason.
- **Every app update ships to all three channels** (Google Play, GitHub release, F-Droid) in one pass. See section 6.
- **Keep signing keys stable** so updates install over the old app without losing data.
- **Deliver files he can open.** Downloads from the chat file card didn't work for him; writing to a connected folder on his computer did. When the chat isn't linked to his computer, attach with `SendUserFile` (`display: "attach"`), and also save docs to the project.
- **Each release:** bump versionCode and versionName, update the project status doc, ship the source zip without `build/` or `keys/`, and end the notes with a phone walkthrough (`docs/IMPLEMENTATION_x.y.md`).

---

## 2. The test phone

- Samsung Galaxy S23 Ultra, Android 16 / API 36 (confirmed from a real crash log), Adreno 740 GPU.
- Has an S Pen (pressure paint ranks higher because of it) and Expert RAW, which shoots DNG (16-bit import ranks higher).
- An app targeting below API 36 shows an "older version of Android" warning; target 36 with edge-to-edge insets.

---

## 3. The workspace and toolchain

**Why the custom toolchain:** the cloud workspace blocks Google's servers (dl.google.com, maven.google.com) and Maven Central, so the SDK and Gradle can't be installed. The toolchain was assembled from reachable sources:
- `android.jar` API 30 from `raw.githubusercontent.com/Sable/android-platforms/master/android-30/android.jar`. It's a research mirror, so the SHA-256 is pinned (`toolchain/android.jar.sha256`) and checked on every download.
- aapt2 from npm `aaptjs3@2.0.2`; signing from npm `apk_sign_ts@1.0.1` (v1/v2/v3).
- dx 1.7 from the **dex2jar 2.0** GitHub release (pxb1988/dex2jar, asset `dex-tools-2.0.zip`). Not the ThexXTURBOXx 2.4 fork.
- Dead end: npm `d8-termux`. Its d8.jar is pre-dexed for Android and won't run on a JVM.
- `github.com/<owner>/<repo>/releases/download/...` and raw.githubusercontent.com are reachable; api.github.com isn't for unattached repos.
- bundletool comes from `github.com/google/bundletool/releases/download/1.18.1/bundletool-all-1.18.1.jar` into `~/.apklab-tools/`. `jarsigner` and `keytool` come with the JDK.

**Hard limits:** Java only, no lambdas (dx reads Java 6 class files), no AndroidX / Compose / Kotlin / Material, single dex (64K methods), API 30 stubs (anything newer through reflection), no NDK (so no TFLite, FFmpeg or on-device ML), no emulator.

**Build:**
- Find the plugin: `SKILL=$(dirname "$(find / -path '*android-apk-builder/SKILL.md' -not -path '*-plus*' | head -1)")`.
- Run `bash "$SKILL/scripts/build.sh" <project-dir> <out.apk>` with a 600–900 s timeout; run large builds in the background.
- Shaders go in `assets/shaders/`; the script passes `-A assets`.
- **Check the APK size, not the filtered log.** An 87 KB APK meant Java compilation failed silently.
- Confirm with `~/.apklab-tools/aapt2 dump badging <apk>` (package, versionCode, label, targetSdk) and `aapt2 dump permissions <apk>`, and report the permission list (it has been zero for every app).
- `$HOME` is `/root` while the working directory is `/home/claude`. Use absolute paths; a Write to the wrong one creates a second tree the build never sees.

**Taking over a project built elsewhere with the real SDK** (done for Emulsion 3.13):
1. Convert its .jks: `keytool -importkeystore` to PKCS12, then `openssl pkey` / `openssl x509` to the PEM pair the signer takes in `keys/`.
2. Stage a copy with the right manifest instead of editing in place.
3. Build the untouched baseline first and check badging and signer fingerprint (it must install over the old build), and run the project's own tests, before changing code.

---

## 4. Testing without a phone

- **glcheck.py:** headless GLES2 shader compile and render through Mesa surfaceless EGL. It's embedded at the end of the plus skill's SKILL.md; extract the last python block to `~/tools/glcheck.py` in each new workspace.
  - Usage: `python3 glcheck.py shader.frag out.png W H --prepend=common.glsl --tex=uName:img.png[:fbo] uName=v1,v2`; arrays as `"uT[0]=0.3,0.6,1"`.
  - It catches GLSL ES 1.00 errors desktop GL accepts (`float x = 1;`, missing precision) and `exp()` overflow to NaN/black.
  - Always render a contact sheet with PIL and look at it; compiling isn't enough. Sheets have caught near-invisible sepia, an off-centre Mandelbrot, upside-down remaps and Spin transition corners.
- **glchain.py (not in the skill yet):** built for Afterglow on top of glcheck for feedback shaders that read their previous frame. `--prev=seed.png` seeds frame 1 and `--frames=N` ping-pongs N frames in one GL context. It caught Fractal being ~3× brighter at 120 Hz than 60 Hz. Rebuild it from that description if needed.
- **Desktop Java tests:** pure model code is tested on the desktop JVM against `~/.apklab-tools/android.jar`.
  - android.jar's `org.json` is stubs that throw, so a hand-written shim (`tests/shim/org/json/`: JSONObject, JSONArray, JSONException with Android's semantics, e.g. `optDouble` on a missing key returns NaN) goes ahead of android.jar on the classpath. The shim can't parse arbitrary text, so file-I/O paths stay in the phone walkthrough.
  - Projects keep tests in their own `tests/` folder with `bash tests/run.sh <Suite>`. The old `~/tests` workspace copies were lost once, which is why.
  - Some suites take their own arguments (Emulsion's MaskStackTest takes an output folder); don't pass the same arguments to every suite.
  - Assertions are written as sentences so failures read clearly.
- **Java twins of shaders:** any effect that samples other pixels needs a pure-Java copy of the GLSL math for planning export tiles, tested for numeric parity (whole image vs 4 stitched tiles; mask stack ≤ 1/255).
- **Icons:** new SVG-path icons are rendered in Playwright/Chromium and the sheet is read. **IconRefsTest** scans the whole source for icon names and fails on any missing one, after a missing `next` icon crashed 3.16.
- **Build-time thumbnails** (looks, transitions, scenes) are rendered through the real shaders by tools in the repo (`tests/lookpipe.py`, Afterglow `tools/lookrender.py`).

---

## 5. Signing keys and app ids

| App | App id | Key |
|---|---|---|
| Independent Video (Play/GitHub) | `com.betterappmafia.independentvideo` | Upload key `independent-video-upload.jks`, alias `upload`, RSA 4096, to 2056, SHA-256 9A:FC:A2:98…62:5D:EE. Christopher keeps it as `independent-video-upload-key.zip` (folder `iv-keys/`: .jks, password.txt, PEM pair, README) and attaches it when needed. Never commit it. |
| Independent Video preview builds | same Java package | Standard Android debug key (the old preview key is no longer used or published). |
| Emulsion (old line) | `com.christopher.strata` / test app `com.christopher.strata.preview` | `signing/preview-development.jks`, alias `emulsion-preview`, passwords `android`, cert SHA-256 23:39:D6…02:D9. |
| Afterglow | `com.christopher.afterglow` | the shared debug key from the plugin |
| Inside, Past Life, APK Lab | `com.christopher.apklab` etc. | the shared debug key |

- The Java package of Independent Video is still `com.christopher.strata`. The activity is named by its full class, there's no R usage, and brand images live in `assets/brand`, so the app id changed without renaming packages.
- Play, GitHub and F-Droid builds are signed with different keys, so a phone can't update from one channel to another. That's expected.

---

## 6. Release pipeline (Independent Video)

**Source of truth:** `github.com/TheNightManCometh-FLAC/independent-video` (public, GPL-3.0-or-later). The app is in `app/`; the website (GitHub Pages, privacy policy at `/privacy/`) is at the root. A new session clones it.

**What this session's GitHub access can and can't do:** git clone, commit and push to main work. Creating tags or releases and changing repo settings or topics are refused, so Christopher does those. If `add_repo` reports no access, the Claude GitHub App isn't installed on the repo (signing in to claude.ai/code isn't enough; install it at github.com/apps/claude/installations/new), not a wrong name.

**Per update** (also in `app/tools/release/RELEASING.md`):
1. Set up the toolchain (`~/.apklab-tools`), unzip the key zip, keep its path for `KEYS=`.
2. Bump `versionCode` and `versionName` in `app/AndroidManifest.xml`. **Next versionCode: 3.** `app/tools/release/released.txt` records what's been packaged.
3. Add `# Independent Video <versionName>` at the top of `app/CHANGELOG.md`.
4. Write `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (500 characters max; all three channels use it).
5. `KEYS=<dir>/iv-keys bash app/tools/release/release.sh`. It runs every test suite, builds the Play AAB (no Ko-fi) and the GitHub APK (with Ko-fi), checks key, version and Ko-fi placement, and writes RELEASE-NOTES.md.
6. Commit and push main; send the .aab, .apk and notes.
7. Christopher uploads the .aab to Play and creates the GitHub release with the .apk, which creates a tag equal to the exact versionName. F-Droid builds from that tag once the app is included.

**How the AAB is built without Gradle** (`tools/release/build-aab.sh play|kofi`), because Play requires bundles, not APKs, for new apps:
1. Build the APK with the normal build.sh; it leaves `build/res.zip` and `build/classes.dex`.
2. Relink the same compiled resources as protobuf: `aapt2 link --proto-format -I android.jar --manifest AndroidManifest.xml -A assets -o proto.apk res.zip`.
3. Lay out a module: unzip proto.apk, move `AndroidManifest.xml` into `manifest/`, copy `classes.dex` into `dex/`, keep `res/`, `assets/`, `resources.pb`; zip it as `base.zip`.
4. `java -jar bundletool.jar build-bundle --modules=base.zip --output=X.aab`.
5. Sign with jarsigner (not apksigner): `jarsigner -keystore KS -storepass PW -keypass PW -sigalg SHA256withRSA -digestalg SHA-256 X.aab upload`, then `jarsigner -verify`.
6. Check: `bundletool validate --bundle=X.aab`; `bundletool build-apks --mode=universal` with the key, unzip, and `aapt2 dump badging universal.apk` for id, label, targetSdk and permissions.

**Ko-fi and Play policy:** `assets/brand/donate.txt` (the Ko-fi URL) turns on the Ko-fi pill and menu row. The Play build leaves the file out. Christopher questioned this and found the peer-to-peer tip exception; the evidence weighed was Play's Payments policy §3–4, AnkiDroid told to remove a donation link (Sept 2026), Language Transfer (2021) and the US external-links program (since Oct 2025). The Ko-fi link is on the hosted privacy page either way. Re-check the policy before changing this.

**F-Droid:**
- `bash build.sh fdroid` builds the unsigned release APK (`build/fdroid/IndependentVideo-unsigned.apk`, Ko-fi included) with the real SDK's aapt/d8, using `ANDROID_JAR` and `ANDROID_BUILD_TOOLS`. It was only flow-tested here with stand-ins; F-Droid's pipeline will be its first real run.
- Recipe draft: `app/tools/release/fdroid/com.betterappmafia.independentvideo.yml` (build 1.0 from commit 9dd8e54, because the `1.0` tag predates fdroid mode; `subdir: app`; `sdkmanager 'platforms;android-35' 'build-tools;35.0.0'`; `AutoUpdateMode: Version`, `UpdateCheckMode: Tags`). The `$$SDK$$` usage isn't verified against the F-Droid docs.
- F-Droid doesn't take uploaded APKs; it builds from source. Submitting: GitLab account → fork `fdroid/fdroiddata` → add the yml under `metadata/` on a new branch → merge request with the template checklist and one honest line that most code was written with Claude. F-Droid has no adopted AI rule; the draft interim policy (admin#699) neither endorses nor bans AI tools.
- Fastlane listing is in the repo root (title, descriptions, icon, feature graphic, changelogs). No screenshots yet; the old ones showed an outdated home screen. `tools/store/frame.py` frames raw S23U screenshots to 1080×1920 / 1920×1080.

**Play closed test:** testers join the Google Group `groups.google.com/g/independent-video`, then opt in at `https://play.google.com/apps/testing/com.betterappmafia.independentvideo`. Play needs 12 testers for 14 days before production. He's recruiting on subreddits and through tester swaps; he writes the post bodies himself because some subreddits ban AI-written posts, and general subreddits mostly yielded other developers rather than people who want an editor.

**Identity:** studio Better App Mafia (BAM); contact cporter31415@gmail.com (crash report, ⋮ menu, listing); Ko-fi https://ko-fi.com/betterappmafia. Privacy-first: no internet permission, no account.

---

## 7. Engineering conventions that held up

- **Format versions on demand.** A file bumps its format version only when it uses a new feature; older apps refuse newer files instead of silently dropping data; every new JSON field gets a sanitize rule; old files must open and look exactly as before.
- **Keep the app id stable across renames** so updates don't orphan projects (Strata → Emulsion kept `com.christopher.strata`). Independent Video deliberately took a new id for Play.
- **Never rebuild a view inside a touch dispatch.** The first device crash (null child in `ViewGroup.hasChildWithZ`, Emulsion 2.4.1) came from rebuilding a panel during a touch event. Post the rebuild and guard re-entrancy.
- **Big features go in new classes, not a rewrite** (agreed after the external code review).
- **Status doc per app**, updated every release: what shipped, research sources, tests, limits, and an "unverified on device" list. The walkthrough doc lists exactly what to try on the phone.
- **Audio-reactive work** goes in the separate `android-music-visualizer` skill so the plus skill doesn't grow for every project.
- **Recovering context:** when a chat summary is thin, `conversation_search` with `within_conversation_id` and distinctive terms finds the exact state.

---

## 8. Apps so far

- **Independent Video** (formerly Emulsion, Strata): layered photo editor plus video editor, plain Java + GLES 3. Latest: 1.0 (versionCode 2), 28 suites / 1,137 checks. Doc: `independent-video-status.md`; feature history 2.x–3.16 in `emulsion-status.md`; plan in `emulsion-implementation-plan.md` (3.15 animation, 3.16 compositing, 3.17 audio were next; check the repo's CHANGELOG for what landed).
- **Afterglow** (`com.christopher.afterglow`): music visualizer, capture of other apps (AudioPlaybackCapture in a mediaProjection foreground service) or own files; 2.0 (versionCode 5) with video export. Doc: `afterglow-status.md`.
- **Inside**: fake-phone art app turned horror game. 2.0-dark shipped; he found it repetitive and plotless, and a full three-act design ("Ro", sister Mara, the Tenant, three endings) was written but the rebuild wasn't finished.
- **Past Life**: Matrix-rain button → portal or crystal ball → one of three shader visions; resets on every open, Back/Home close it, TV-off exit. 1.0 delivered.
- **APK Lab** (`com.christopher.apklab`): the first tap-counter that proved the toolchain; now the skill's template.

---

## 9. Open decisions (Christopher's)

- Voiceover recording, which needs RECORD_AUDIO, the first permission any of the apps would ask for.
- 2.39:1 letterbox as a project option.
- Whether to supply his own looks as the built-in set.

---

## 10. Lessons not yet in the plus skill

Its learnings log stops at Emulsion 3.13. To fold in with `propose_skills`:
- The AAB pipeline (section 6), jarsigner for bundles, bundletool's source URL, and the universal-APK check.
- One release script for three channels with key, version and per-channel content checks; F-Droid build mode and recipe.
- GitHub: what a session can push and what Christopher must do (tags, releases, settings).
- glchain.py for feedback shaders.
- IconRefsTest: scan every icon reference.
- Changing the application id without renaming the Java package (full-class activity name, no R usage, assets for brand images).
- 3.14's GL group mix on video grades, build-time transition previews, and 160 px live thumbnails.

## 11. Files to bring into the new project

- This file.
- `independent-video-status.md` (always, if continuing that app).
- `emulsion-status.md`, `emulsion-implementation-plan.md`, `emulsion-3.12-workflow-gaps.md` (only if continuing the editor's feature work).
- `afterglow-status.md` (only if continuing Afterglow).
- Per session: `independent-video-upload-key.zip` for release builds.
