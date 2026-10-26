# Releasing Apophenia

Three channels from one commit (working rules, "Releases"): **Google Play** (AAB, signed with the upload key), **GitHub
release** (APK, signed with the same key), **F-Droid** (built and signed by F-Droid from the tag). Play, GitHub and
F-Droid installs can't update each other (different signatures): expected.

## Signing
- The release key is read from `keystore.properties` at the repo root, which git ignores:
  ```
  storeFile=keys/apophenia-upload.jks
  storePassword=...
  keyAlias=upload
  keyPassword=...
  ```
  `keys/`, `*.jks` and `keystore.properties` are all gitignored. Never commit any of them.
- Without the file, `bundleRelease` and `assembleRelease` build **unsigned** outputs (what F-Droid wants; Play and GitHub need it signed).
- **The key (made 24 Oct 2026):** `keys/apophenia-upload.jks`, alias `upload`, certificate SHA-256 C5:50:9F:E4:D7:A6:BB:A0:6C:37:FF:D4:A1:E4:42:A9:E1:F1:22:62:C9:7D:46:D2:62:8D:A5:99:28:35:F8:7C. Backups with its password and README: `C:\Users\Wi-Fi\Documents\Apophenia upload key\` and `D:\Backups\Apophenia upload key\`. Play App Signing holds the app-signing key; this is the upload key (Play can reset a lost upload key; GitHub installs can only be updated with this one).
- Signed outputs are copied to `dist/release/` (gitignored).

## Per release
1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Write `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (500 characters at most; all three channels use it).
3. `./gradlew.bat assembleDebug test :data:jvmTest bundleRelease assembleRelease`. Every build also checks the **merged**
   manifest (libraries included) for INTERNET, ACCESS_NETWORK_STATE and RECEIVE_BOOT_COMPLETED and fails if it finds one
   (`checkNoNetwork` block at the end of `app/build.gradle.kts`).
4. Check the outputs: `aapt2 dump badging app/build/outputs/apk/release/app-release.apk` (id, versionCode, targetSdk 36,
   only RECORD_AUDIO and the foreground-service permissions, arm64-v8a). AAB: `app/build/outputs/bundle/release/app-release.aab`.
5. Commit and push. Christopher uploads the AAB to Play and creates the GitHub release with the APK; the tag is `v<versionName>`.
6. F-Droid builds from the tag once the app is in fdroiddata (`tools/release/fdroid/com.betterappmafia.apophenia.yml` is the draft).

## Size
0.7.7: AAB 170 MB, release APK 208 MB, arm64-v8a only. Most of it is the models (140 MB: Spleeter 4-stems, Moonshine base,
Basic Pitch) and ONNX Runtime's native library (34 MB). Play's limit is 200 MB for the compressed base download
([measure] with `bundletool get-size total` once bundletool is on this machine). Past it, the models move to an
install-time asset pack (Play Asset Delivery). [Claude] R8 is off: the code is a small part of the size, and the JNI
bridge, Room and the ONNX/LiteRT runtimes would each need keep rules checked on the phone.

## What the dependencies bring (checked 24 Oct 2026)
- **ONNX Runtime 1.31.0** (`onnxruntime-android`) asks for INTERNET and ACCESS_NETWORK_STATE in its own manifest; the app's
  manifest removes both (`tools:node="remove"`). Builds before 0.7.7 had them in the merged manifest.
- **LiteRT 2.3.0** depends on Google Play's AI-pack delivery (`com.google.android.play:ai-delivery`, `asset-delivery`,
  `play-services-basement`, `play-services-tasks`, WorkManager). Only its `AiPackModelProvider` uses them and the app never
  calls it, so `app/build.gradle.kts` excludes `com.google.android.play` and `com.google.android.gms`. That also removes
  WorkManager's boot and wake-lock permissions and the 2019 Fragment that failed release lint. **[measure]** Basic Pitch
  transcription on the phone with this build (it's the only LiteRT user).

## F-Droid: open questions before submitting
- ONNX Runtime and LiteRT arrive as prebuilt AARs with native libraries (Maven Central and Google Maven). Check F-Droid's
  current inclusion policy and scanner on prebuilt FOSS native libraries; building ONNX Runtime from source in the recipe
  is the fallback.
- The model files in `app/src/main/assets/models` are binary weights under free licences (Apache-2.0 / MIT, see NOTICE.md).
  Check F-Droid's policy on bundled model weights, and whether they want a script that fetches or converts them instead.
- No Google Play Services remain in the dependency tree (`./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep -i gms` is empty).
- Submitting (as for Independent Video): GitLab account → fork `fdroid/fdroiddata` → add the yml under `metadata/` → merge
  request with the template checklist and one honest line that most of the code was written with Claude.

## Store listing
`fastlane/metadata/android/en-US/`: title, short description (74 of 80), full description (draft from the README), changelogs.
Icon: `images/icon.png` 512×512 from Christopher's painting (new 25 Oct 2026, full bleed) (`tools/store/icon.ps1` makes it and the launcher layers). Feature graphic: `images/featureGraphic.png` 1024×500 (`tools/store/feature.ps1`: the artwork right, name, tagline and "offline · no account · no ads" in JetBrains Mono and the app's colours). **Missing:** phone screenshots.
