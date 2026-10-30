# Chord tracking on acoustic guitar — 29 Oct 2026

Christopher: “Don't just add notes so it makes a chord. Start with 1.” Scope: combine
the recording's spectral evidence with notes already detected by Notes → MIDI.
No chord completion, note insertion, pitch correction or change to the stored transcription.

## Data and scoring

- [GuitarSet v1.1.0](https://zenodo.org/records/3371780), Xi et al., ISMIR 2018,
  CC BY 4.0: all 180 acoustic-guitar comping recordings, mono microphone audio.
- Players 00–02 (90 takes) for tuning; players 03–05 (90) for comparison on other
  players. The comparison results were inspected when choosing the compromise;
  this is not a fresh, untouched final test set.
- Basic Pitch 0.4 model output, decoded at the 0.9.21 settings: onset 0.6,
  frame 0.35, minimum length 8 frames. Beat This! small0 for beat times.
- Time-weighted [mir_eval chord](https://github.com/mir-evaluation/mir_eval/blob/main/mir_eval/chord.py)
  majmin, root and thirds against the first (`instructed`) chord annotation.
  Slash basses are ignored by the scorer. `majmin` reduces supported reference
  chords to major/minor triads; it does not measure full extended-chord accuracy.
  Instructed harmony is not ground truth for every performed voicing or omission.

## Speech gate

The old held-pitch heuristic calls **63 of 180 comping recordings speech**. This
removes key and chords and, since 0.9.19, the BPM. This reproduces one possible
cause of Christopher's missing BPM, not a confirmed diagnosis of his recordings.

Eleven speech probes (the memo fixture and Windows David/Zira voices) have at most
5 detected beats and at most 0.50 beat regularity. The 63 guitar false positives
have regularity at least 0.61. Implementation choice: bypass the held-pitch speech
heuristic with at least 8 model beats and regularity at least 0.55. This is a
limited speech sample, not a general speech/music classifier. Rhythmic speech and
short or unsteady guitar still need phone checks.

## Combined evidence

For each beat segment, accumulate only the detected notes sounding inside it,
weighted by overlap duration × activation amplitude. Blend their fundamental
pitch-class cosine similarity equally with the existing harmonic audio-template
similarity. Keep the bass evidence and all twelve existing chord qualities.

When note evidence is present, use a change cost of 0.25 rather than 0.08 and a
small ambiguity prior: 0 for major/minor, 0.03 for 7/maj7/m7, 0.10 for the other
qualities. This is an implementation tradeoff to reduce flickering and false
extensions. Sound-only segments retain the old scoring and transition cost:
applying the same prior without note evidence broke clean power-chord/inversion
regressions. No automatic transcription is started by analysis.

Earlier parameter comparison (all with the speech exemption; values majmin/root/thirds):

| Variant | Players 00–02 | Players 03–05 |
| --- | --- | --- |
| quality prior 0.10/0.03 only | .523/.794/.685 | .467/.634/.571 |
| prior 0.15/0.05 only | .626/.802/.623 | .557/.636/.557 |
| prior 0.10/0.03 + notes 0.50 | .565/.844/.746 | .535/.678/.633 |
| prior 0.10/0.03 + notes 0.80 | .561/.857/.752 | .528/.683/.634 |
| prior 0.10/0.03 + change cost .25 | .576/.813/.708 | .480/.632/.575 |
| prior 0.10/0.03 + notes .50 + change cost .25 | .605/.863/.770 | .563/.689/.648 |
| prior 0.20/0.05 + notes .50 + change cost .25 | .741/.868/.728 | .643/.677/.637 |

The strongest prior wins on instructed major/minor but loses root/third evidence
on the comparison players. Kept the moderate 0.10/0.03 compromise and restricted
it to segments with notes. Final-source results are recorded below separately.

Final-source measurement (180 takes, cached model beats; **desktop measurement**):

| Output | Players 00–02 majmin/root/thirds | Players 03–05 majmin/root/thirds |
| --- | --- | --- |
| Previous app output, including speech suppression | .178/.613/.493 | .155/.451/.379 |
| Speech exemption + sound-only control | .231/.775/.618 | .226/.613/.512 |
| Speech exemption + final combined evidence | .593/.854/.761 | .562/.690/.649 |

The existing single-line rule suppresses charts on 2/180 comping takes after
transcription; these remain counted as no-chord predictions in the final score.
Results are uneven: comparison-player majmin averages are .286 for funk and .395
for jazz, versus .595 rock, .685 bossa nova and .762 singer-songwriter. Plenty of
errors remain, especially extensions and omitted tones.

## Reproduction and regression evidence

Large inputs and outputs are local on D:, not shipped in the app:

- `D:/junkdrawer/guitarset/audio`, `annotation`: microphone WAVs and JAMS.
- `D:/junkdrawer/apo-ref/bp_out`, `gs_notes`: model output and detected notes.
- `D:/junkdrawer/apo-ref/chords`: per-beat predictions by variant.
- `chord_eval.py <variant> 03,04,05` in that directory: original JAMS/mir_eval scorer.
- `chord_eval_fast.py`: same scoring using direct JAMS JSON reads instead of full
  schema validation; reports old output, final sound-only control and final fusion.
  It also accepts double accidentals (`Fx` → `F##`) without dropping predictions.
- `ZProbeChords.kt` and `ZProbeSpeech.kt`: earlier exploratory probes (reference the
  temporary tuning API, which is now removed). `ZFinalChordProbe.kt`: final public
  `chroma`/`label` API, cached model beat boundaries and key spelling; same recorded
  notes and single-line suppression as the app. It reuses cached inference rather
  than rerunning Android inference. Restore this manual probe to core tests to
  repeat it, then remove it before normal tests. `final22-probe.log` records the run.

Regression fixture: first ten seconds of player03's `03_BN2-131-B_comp_mic.wav`;
actual detected notes and beats, with attribution beside the fixture and in NOTICE.
Instructed opening Em, then A7 at 1.832 s. Audio-only calls the opening E5; combined
evidence calls Em and retains A7. Existing note values and beat times remain equal.
Clean power, sus4, diminished and dominant seventh chords also retain their names.

**Unit-tested:** disabling the speech exemption fails the real-guitar tempo/chart
test; disconnecting transcription evidence fails the Em/A7 regression. Both
mutations restored before the final suite. No new note-generation path was added.

**Unverified on device / by ear:** S22 Ultra microphone recordings, runtime, real
speech, short/loose strumming, and advanced performed voicings. The dataset result
is not a claim of solved guitar transcription or full chord recognition.

## Follow-up: quiet notes, bass and inversions (0.9.23, 29 Oct 2026)

Christopher: “Keep improving.” Three changes, confined to chart evidence:

1. Keep the detected note activations directly, normalised by L2 norm. The audio
   spectrum still gets mean subtraction, but applying that noise-floor operation
   to already-detected notes erased quieter thirds/sevenths. Real example:
   `00_BN2-166-Ab_comp`, F#7 at 1.08–3.08 s read as F# before this change.
2. Combine the spectral bass distribution equally with the lowest detected note
   sounding at each instant. Weight by activation × sounding duration; a short
   low note does not become the bass for a whole beat. Tied unison detections use
   the strongest activation once. No note is created, filled in or rewritten.
3. With note evidence, give a candidate half the root-bass bonus when the bass is
   another of its chord tones. A bass note need not be the root. The new clean
   C/E regression exposed the old root-only vote overpowering the chord and
   naming Em, also holding Em across a following E5. Both now retain their names.
   Inversion display still uses the existing spectral bass; it was not replaced
   with a generated voicing. Sound-only scoring is unchanged.

Primary background: [Mauch and Cannam's NNLS Chroma/Chordino description](https://isophonics.net/nnls-chroma)
separates note/chroma evidence from bass evidence and describes its harmonic
transcription and normalisation stages. This update does not port NNLS Chroma.

The measurement cache uses the exact Kotlin audio features and the same model
notes/beat boundaries as 0.9.22. A Python replica first matched all 5,019 labelled
beats in the 00–02 baseline. Only those players were used for the new bass-weight
sweep (0.25/0.5/0.75/1) and chord-tone credit (0.25/0.5). Equal bass votes and
half credit were the compromise. Globally halving the bass bonus passed the clean
inversion test but weakened root recognition, so it was discarded in favour of
crediting actual chord-tone inversions. Comparison-player results were inspected;
these are repeated comparisons, not a pristine new test set.

New checks also score GuitarSet's **second, performed-note chord annotation**,
which is sheet-informed, semi-automatic with manual verification; it is not an
independent listening verdict. `mir_eval.chord.tetrads` compares root and the full
pitch-class quality, ignoring bass inversion. Unsupported detector qualities such
as add9 remain errors; matching an instructed major triad does not certify a
performed add9 or an omitted fifth. The detector still has its existing twelve
qualities.

Regression features and original detected notes from two tuning takes are in
`core/src/test/resources/chordtracking/`, attributed in README and NOTICE. No new
audio is bundled. `ChordEvidenceTest` covers a quiet F#7, a root disambiguation,
exact note boundaries, gaps, unisons and unsorted notes. The chord-quality test
also covers transcribed C/E, E5, Am and G7. All three deliberate mutations
(erase quiet notes, disable detected bass, disable inversion credit) produced
assertion failures; the original source was restored after each.

Reproduction on this PC: `D:/junkdrawer/apo-ref/chord_study.py`,
`chord_followup.py`, `chord_inversions23.py`, `chord_validate_followup.py`,
`validate_native23.py`, `ZChordFeaturesProbe.kt`, `ZFinalChord23Probe.kt`;
raw features in `chord_features/` and `chord_features_tsv/`, predictions
`chords/*.final23.txt`. The optional Kotlin extraction/measurement classes were
removed from the normal test suite after running. Android inference was not
rerun; neither the models nor audio front end changed.

Final compiled Kotlin output matched the Python candidate on **9,980/9,980
labelled beats** across all six players (two single-line-suppressed takes have
empty charts in both). The replica initially differed on 16 beats because it
gave inversion credit across segments with no detected notes; correcting that
replica guard produced exact parity. The following figures come from the Kotlin
predictions scored through the original interval-merging/mir_eval path, not the
replica's faster score tables (`native_scores23.py`, `native23-scores.txt`).

| Reference / players | Version | Major/minor | Root | Thirds | Full quality (tetrads) |
| --- | --- | --- | --- | --- | --- |
| Instructed, 00–02 | 0.9.22 | .593 | .854 | .761 | .521 |
| Instructed, 00–02 | 0.9.23 | .645 | .881 | .787 | .570 |
| Instructed, 03–05 | 0.9.22 | .562 | .690 | .649 | .407 |
| Instructed, 03–05 | 0.9.23 | .606 | .713 | .672 | .432 |
| Performed-note, 00–02 | 0.9.22 | .761 | .854 | .817 | .578 |
| Performed-note, 00–02 | 0.9.23 | .824 | .881 | .844 | .625 |
| Performed-note, 03–05 | 0.9.22 | .695 | .690 | .653 | .401 |
| Performed-note, 03–05 | 0.9.23 | .749 | .713 | .677 | .436 |

Different reference vocabularies and eligible durations make the two major/minor
scores incomparable to each other; compare versions within each row pair. The
per-style performed major/minor averages on comparison players are funk .348→.405,
jazz .412→.454, rock .729→.760, bossa .776→.797 and singer-songwriter .782→.843.
One performed-note take has no reference chords eligible for majmin and mir_eval
warns about its per-take score; the aggregate excludes ineligible durations as
before. Root and full-quality scores still include it when eligible.

**Unit-tested / build-verified:** 631 tests pass (497 core, 88 data, 19 app, 27
audio). Signed release APK and AAB, code 104; analysis version 7. APK identity,
permissions, certificate and AAB validation/signature checked. **Unverified on
device / by ear:** only an offline emulator is listed by adb; no phone connected.
This improves the chart's use of existing detections, not Basic Pitch recall.

## Follow-up: balancing pitch-class evidence (0.9.24, 29 Oct 2026)

Christopher: “Continue.” Kept one change: square-root the accumulated detected
pitch-class weights before their L2 normalisation in the chord matcher. A repeated
or louder pitch class still has more weight, but does not bury a quieter third or
seventh as readily. Zero stays zero. This changes only a derived matching feature;
the notes, activations, note count, timings, bass feature, 50/50 audio/note balance,
change costs and twelve-quality vocabulary remain unchanged. The audio spectrum
keeps its existing processing.

Measured alternatives on players 00–02, performed-note annotation:

| Trial | Major/minor | Root | Thirds | Full quality |
| --- | --- | --- | --- | --- |
| 0.9.23 | .8242 | .8811 | .8441 | .6252 |
| Note weight .75, linear pitch classes | .8330 | .8851 | .8506 | .6499 |
| Note weight .50, square-root pitch classes (kept) | .8569 | .8888 | .8577 | .6527 |
| Note weight .75, square-root pitch classes | .8569 | .8904 | .8563 | .6697 |

The last trial won on aggregate but failed the existing funk-root regression,
changing Ab to Fm. It was rejected. The kept square-root-only change preserves
the root both with and without the separate detected-bass vote; that test's old
negative control expected Fm without that vote, which is no longer a valid
expectation once the independent compression fix also corrects it. The test now
requires the correct root in both cases. Clean C/E, E5, Am, G7, suspended and
diminished checks remain unchanged and pass.

New regression uses the already-attributed `00_BN2-166-Ab_comp` features/notes:
the performed annotation is C#:min7/5 at 11.54–13.00 s. Linear weights name C#m;
compression retains C#m7. Reverting only the square root causes this assertion
and the known-funk-root assertion to fail (`mutation24.txt`).

Other experiments were **not shipped**:

- The tuning reference contains 425.46 s of performed pitch-class qualities outside
  the twelve-quality dictionary versus 2,316.65 s inside it. The largest remaining
  covered-quality errors still include plain major, dominant seventh and power
  chords (`chord_error_census24.py`).
- A wider dictionary (sixths, add9, ninths, 7sus4 and altered dominants), gated by
  detected defining tones, moved tuning full quality only .6252→.6265 at its best
  tested gate. Fifth-omission variants similarly yielded little benefit and could
  weaken major/minor recognition. No new labels or omitted-tone guesses were
  added to the app (`chord_extended24.py`, `chord_omitted24.py`).
- Removing the broad quality prior worsened results substantially; changing the
  seventh prior helped slightly but was left out to keep this update to the
  independently verified feature correction (`chord_quality24.py`).

Reproduction: `chord_balance24.py`, `chord_balance_combined24.py`,
`chord_validate24.py`, `ZFinalChord24Probe.kt`, `validate_native24.py`,
`native_scores24.py` under `D:/junkdrawer/apo-ref`. Large inputs and cached model
outputs are unchanged. Final Kotlin predictions are `chords/*.final24.txt`.
Comparison players 03–05 have been inspected repeatedly during this work; the
result is a repeated comparison, not a new blind test. Basic Pitch's missing-note
rate and stem separation are unaffected.

Final compiled Kotlin output and the replica agree on **9,980/9,980 labelled
beats**. Scoring the Kotlin predictions through the original interval-merging
path (`native24-scores.txt`) gives:

| Reference / players | Version | Major/minor | Root | Thirds | Full quality |
| --- | --- | --- | --- | --- | --- |
| Instructed, 00–02 | 0.9.23 | .645 | .881 | .787 | .570 |
| Instructed, 00–02 | 0.9.24 | .676 | .889 | .798 | .590 |
| Instructed, 03–05 | 0.9.23 | .606 | .713 | .672 | .432 |
| Instructed, 03–05 | 0.9.24 | .635 | .719 | .685 | .453 |
| Performed-note, 00–02 | 0.9.23 | .824 | .881 | .844 | .625 |
| Performed-note, 00–02 | 0.9.24 | .857 | .889 | .858 | .653 |
| Performed-note, 03–05 | 0.9.23 | .749 | .713 | .677 | .436 |
| Performed-note, 03–05 | 0.9.24 | .788 | .719 | .688 | .470 |

Same metric/reference limitations as the previous comparison. Full quality still
ignores bass inversion and is far from reliable on all performed voicings.
**Unit-tested / build-verified:** 632 tests pass (498 core, 88 data, 19 app, 27
audio), deliberate compression-off mutation fails, signed APK/AAB and debug APK
built; certificate, permissions, bundle validation and AAB signature checked.
VersionCode 105, analysis version 8. **Unverified on device / by ear:** no connected
phone, emulator offline. Previously saved chart scraps keep their written content.
