# Guitar model reference

Test-only extract of the existing `../chordtracking/guitar.wav` fixture: first ten
seconds of GuitarSet `03_BN2-131-B_comp_mic.wav`. GuitarSet © Qingyang Xi,
Rachel M. Bittner, Johan Pauwels, Xuzhou Ye and Juan Pablo Bello, 2018,
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/),
[v1.1.0](https://zenodo.org/records/3371780). Converted to mono 16 kHz float32.
These resources are not bundled in the app.

`reference.f32.gz`: little-endian float32 input (160000 samples), then reference
torchlibrosa log-mel (1001 × 229), then compact ONNX onset, offset, frame and
velocity outputs (each 1001 × 88). `notes.tsv`: the author's decoder output,
sorted by onset/pitch/end, columns start/end/MIDI/velocity. Thresholds .3/.3/.1.
Model and inference revisions are recorded in the harmonic-analysis research doc.
Produced on 29 Oct 2026; regenerate with `tools/guitar/reference.py` and the
pinned desktop dependencies in `tools/guitar/README.md`.
