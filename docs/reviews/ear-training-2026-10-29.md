# Focused Ear practice and tests — 29 Oct 2026

Christopher requested more sections to build up to mixed questions, separate test
mode (25 questions by default, adjustable), best percentage plus question count,
practice longest streak, drill-to-lesson shortcuts, and piano alongside fretboard
learning examples. This supersedes the earlier practice-only choice.

## Research and source check

[Teoria's interval exercise](https://www.teoria.com/en/exercises/ie.php) exposes
melodic, harmonic, combined, ascending and descending choices; its
[exercise help](https://www.teoria.com/en/help/exercises/kne.php) documents stopping
after a chosen number of exercises.
[musictheory.net](https://www.musictheory.net/exercises) offers an interval ear
exercise and customizer. These support making presentation and interval selection
explicit; Apophenia keeps its existing Console layout and unrestricted topics.

Source confirmed: `EarView` hardcoded PRACTICE, with direction under SOUND;
`EarResultDao.bests()` already ranks test fractions with longer tests winning ties.
Existing Room schema15 already holds mode, correct, total and best streak. No
schema change/migration is needed. The current high-score streak query included
tests, so it now filters to practice. Historical records remain intact.

## Implementation

- Four visible interval presentation groups. New minor/major seconds, thirds,
  sixths, sevenths, fourth/fifth and unison/octave comparisons precede broader
  simple/compound sets. Simple includes unison; compounds extend through the
  double octave. All are available immediately. Custom picks retain the selected
  presentation, and focused presentations have independent drill/score IDs.
- Practice remains open-ended. Its current database session is updated as answers
  arrive, instead of inserting an attempt for each answer. A mutex and increasing
  answer-count guard prevent duplicate/stale writes. Tests persist only when the
  selected number of first answers has been reached; no extra answer can grow a
  completed test. The chosen question count is remembered after starting a test.
  Test sounds stay fixed during the run. Practice's missed-answer weighting is not
  used to adapt a test's later questions.
- High scores show best test percentage with correct/total, and a separate longest
  practice streak. Comparison uses unrounded fractions; percentages are displayed
  to one decimal. A shorter 100% test may beat a longer lower-percentage test, as
  requested; the denominator is always displayed. Equal percentages prefer more
  questions, using the existing query.
- `EarLearning` maps every kind/source to existing related Course pages, including
  your-material drills. Mixed and custom drills resolve source topics. LEARN links
  are on drill rows and the current question, with its root carried into the
  example. AppModel retains the active run while a lesson is open; ‹ EAR and Android
  Back resume it. The normal tab navigation still closes the open drill.
- Shared Reference/Course examples show both lit piano keys and fretboard; staff
  remains available. Pitched score examples without a TRY reference show their
  notes on both, labelled as notes used in the example. Percussion stays on its
  appropriate rhythm notation. Piano answering now permits the given pitch for a
  unison/tonic degree, and its visible range includes compound targets.
  Interval recognition registers are kept inside E2–E5 so a standard-guitar answer
  can be entered on the existing twelve-fret input; sung intervals keep their
  previous register. Alternative tunings can have different ranges.

## Verification

Targeted core, actual Room/SQLite and compilation checks pass. Tests cover fixed
directions even when global sound settings disagree; mixed-direction coverage;
custom ID restoration; default25 and selected1/7/50/100; exact completion and no
partial record; practice streak reset/best; every lesson target's existence and
specific cadence/interval/swing destinations. Room checks compare24/25 against
45/50, tie-break48/50, exclude100/100 practice from test scores and exclude test
streaks from practice, and update one practice row.

Five mutation controls were observed failing: ignore a focused direction; stop a
test one question late; link Cadences to an unrelated but real lesson; rank tests
by raw correct count; include test streaks in practice. Each source was restored
before the final suite/build. Logs `D:/junkdrawer/apo-ref/ear27-mutations.txt` and
`ear27-mutation-*.log`. A further regression checks correct/wrong/empty unison
answers and complete compound coverage/range through P15.

An app-state regression also covers consecutive equal musical questions: each
new attempt gets a distinct audio/input key, while its score continues. The
completed test cannot start an extra attempt. Removing the key increment caused
the intended failure, then was restored. A tuning change during a lesson clears
and resizes the active fret input before returning. Custom pick names include
their fixed presentation so score cards identify ascending/descending/harmonic.

**Unit-tested / build-verified:** 649 tests (511 core, 90 data, 21 app, 27 audio),
arm64 debug APK and signed release APK/AAB. Signatures/certificate, no-network
permissions and bundle validation passed. Local signed .27 files in
`dist/release`, not published. Final build log `D:/junkdrawer/apo-ref/ear27-final.log`.
No phone connected. An emulator cold-boot attempt encountered a stale instance;
its stalled process from this chat was stopped and a read-only software-rendered
boot attempted. Do not infer that a UI walk occurred from a successful build.
