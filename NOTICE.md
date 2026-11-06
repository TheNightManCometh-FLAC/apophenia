# Apophenia — notices

Apophenia is free software under the GNU General Public License, version 3 or later (see `LICENSE`).
App id `com.betterappmafia.apophenia`, by Better App Mafia.

The APK bundles the following. Each licence text ships in the app at `app/src/main/assets/licenses/` and is
shown in the app under Library ⋯ › Licences and privacy.

Optional Guitar transcription: Xavier Riley's `guitar-gaps.pth`, model repository
`xavriley/midi-transcription-models` revision `b7bec65a2b860aca72856b0feef58b5df407b777`
(MIT declaration in model card), converted to compact ONNX plus mel matrix.
Reference algorithm: Riley's hf_midi_transcription (MIT declaration in README) and
Qiuqiang Kong's piano-transcription-inference (MIT classifier in PyPI/setup.py).
Conversion and Kotlin inference modifications are described in
`app/src/main/assets/licenses/guitar-gaps-NOTICE.txt`, which is also shown in-app.
The test-only `guitarpitch/` reference derives from the GuitarSet recording
attributed below and carries the same CC BY 4.0 attribution.

| Piece | Used for | Licence | Copyright | Text |
|---|---|---|---|---|
| Oboe 1.11.0 | audio engine | Apache-2.0 | The Android Open Source Project | apache-2.0.txt |
| Signalsmith Stretch (vendored in `audio/src/main/cpp/third_party/`) | time-stretch, pitch-shift | MIT | Geraint Luff / Signalsmith Audio Ltd. | signalsmith-stretch-LICENSE.txt |
| Signalsmith Linear (vendored) | FFT for Stretch | MIT | Signalsmith Audio | signalsmith-linear-LICENSE.txt |
| LiteRT 2.3.0 | runs Basic Pitch | Apache-2.0 + third-party notices | Google LLC | litert-LICENSE.txt, litert-THIRD_PARTY_NOTICE.txt |
| ONNX Runtime 1.31.0 | runs Spleeter, Moonshine, Beat This!, Guitar GAPS, Demucs | MIT + third-party notices | Microsoft Corporation | onnxruntime-LICENSE.txt, onnxruntime-ThirdPartyNotices.txt |
| Basic Pitch (ICASSP 2022) model | notes from audio | Apache-2.0 | Spotify AB | basic-pitch-LICENSE.txt |
| Spleeter 4-stems, ONNX conversion | stem separation | MIT (Spleeter), Apache-2.0 (conversion) | Deezer SA | spleeter-LICENSE.txt, spleeter-4stems-onnx-README.md |
| Moonshine base | speech to text | MIT | Moonshine AI | moonshine-LICENSE.txt |
| Beat This! small (`small0`), exported to ONNX | beats, downbeats, tempo | MIT (code and weights); some training data is copyrighted, as its README notes | Institute of Computational Perception, JKU Linz | beat-this-LICENSE.txt |
| Guitar GAPS, converted to ONNX | optional solo-guitar notes | MIT (see the paragraph above) | Xavier Riley and contributors; Qiuqiang Kong | guitar-gaps-NOTICE.txt |
| Demucs 4.0.1 (how its model is run, ported to Kotlin) | optional HT-Demucs stems; the model file is not bundled, the user adds it | MIT | Meta Platforms, Inc. and affiliates | demucs-LICENSE.txt |
| Bravura | music font | SIL OFL 1.1 | Steinberg Media Technologies GmbH | Bravura-OFL.txt |
| Noto Music | music symbols in text | SIL OFL 1.1 | The Noto Project Authors | NotoMusic-OFL.txt |
| JetBrains Mono | typeface | SIL OFL 1.1 | The JetBrains Mono Project Authors | JetBrainsMono-OFL.txt |
| Cormorant Garamond (italic) | titles in the Film theme | SIL OFL 1.1 | The Cormorant Project Authors | CormorantGaramond-OFL.txt |
| Jost | labels in the Film theme | SIL OFL 1.1 | The Jost Project Authors | Jost-OFL.txt |
| University of Iowa MIS piano (30 notes, processed) | piano sound | free use without restrictions | University of Iowa Electronic Music Studios | iowa-piano-NOTICE.txt |
| Android Jetpack (Compose, Room, Lifecycle, Activity, Core), Kotlin | interface, database, language | Apache-2.0 | The Android Open Source Project; JetBrains s.r.o. | apache-2.0.txt |

The Course's text is written for Apophenia; no Open Music Theory text has been adapted so far. If any is, it is
CC BY-SA 4.0 and gets its attribution and modification notice here.

Test-only fixture: the first ten seconds of GuitarSet `03_BN2-131-B_comp_mic.wav`,
with derived Basic Pitch notes and Beat This! beats, are in `core/src/test/resources/chordtracking/`.
GuitarSet © Qingyang Xi, Rachel M. Bittner, Johan Pauwels, Xuzhou Ye and Juan Pablo Bello,
2018, [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/),
[dataset v1.1.0](https://zenodo.org/records/3371780). The audio is trimmed and converted
to PCM16 for this regression fixture. These test resources are not bundled in the APK.
The same test folder also contains numerical audio feature extracts and detected notes
from `00_BN2-166-Ab_comp_mic.wav` and `00_Funk3-112-C#_comp_mic.wav`, under the same
GuitarSet attribution and CC BY 4.0 licence. These regression tests modify no source notes.
The harmonic-fit regression uses a numerical spectral extract from
`00_BN1-129-Eb_comp_mic.wav`, with an independently computed NNLS result, under the
same attribution and licence. It is also test-only.
