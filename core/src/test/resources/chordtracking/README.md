First 10 seconds of GuitarSet 03_BN2-131-B_comp_mic.wav (held-out player 03).
GuitarSet: Xi, Bittner, Pauwels, Ye, Bello, ISMIR 2018; CC BY 4.0. https://zenodo.org/records/3371780
notes.tsv: Basic Pitch detected notes at 0.6/0.35/8; beats.txt: Beat This small0 beats. No notes added.
Used to guard rhythmic strumming being classified as speech, and blending heard notes with audio.

0.9.23 regression features (derived data, same GuitarSet authors and CC BY 4.0 licence):
- 00_BN2-166-Ab_comp.segments.tsv: actual audio chroma/bass/energy at the detected beat boundaries.
  Matching .notes.tsv: Basic Pitch output. Quiet seventh was lost from F#7 at 1.08–3.08 s.
- 00_Funk3-112-C#_comp.segments.tsv and .notes.tsv: same features from the funk take.
  Instructed G# major at 19.2857–21.4286 s; performed annotation is G#:maj(2,*5)/2.
  The test checks root/major quality only, with the label spelled Ab by the default context.
These are numerical feature extracts, not additional bundled audio. No detected notes were added.

0.9.24 reuses 00_BN2-166-Ab_comp for C#:min7/5 around 11.54–13.00 s:
linear pitch-class weights lost the seventh, while square-root compression retains it.
The existing funk-root fixture now names the correct root even without the separate bass vote.

0.9.25: harmonic-spectrum.tsv is the mean magnitude spectrum of frames 100–109
from 00_BN1-129-Eb_comp_mic.wav, linearly resampled to 24 kHz, periodic Hann 4096,
hop 1024; bins 7–426 (40–2500 Hz). harmonic-expected.tsv is independent SciPy
NNLS fitted pitch-class evidence using the documented harmonic dictionary. Same
GuitarSet authors and CC BY 4.0 licence; numerical extracts only, no notes added.
The existing 03_BN2 opening's performed annotation is Em7 (sheet instruction Em);
the integration test now checks that performed seventh instead of the sheet's triad.
