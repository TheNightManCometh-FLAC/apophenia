"""Regenerate the test-only golden from the author's frontend and decoder.
Run with the same pinned desktop dependencies/PYTHONPATH as export.py.
"""
import argparse
import gzip
from pathlib import Path
import numpy as np
import torch
import librosa
import onnxruntime as ort
from torchlibrosa.stft import Spectrogram, LogmelFilterBank
from piano_transcription_inference.utilities import RegressionPostProcessor

repo = Path(__file__).resolve().parents[2]
p = argparse.ArgumentParser()
p.add_argument("--audio", type=Path, default=repo / "core/src/test/resources/chordtracking/guitar.wav")
p.add_argument("--assets", type=Path, default=repo / "app/src/main/assets/models/guitar-gaps")
p.add_argument("--output", type=Path, default=repo / "core/src/test/resources/guitarpitch")
args = p.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
torch.set_num_threads(2)
x, _ = librosa.load(args.audio, sr=16000, mono=True)
x = x[:160000]
x = np.pad(x, (0, 160000 - len(x))).astype(np.float32)
spect = Spectrogram(n_fft=2048, hop_length=160, win_length=2048, window="hann", center=True, pad_mode="reflect", freeze_parameters=True)
mel = LogmelFilterBank(sr=16000, n_fft=2048, n_mels=229, fmin=30, fmax=8000, ref=1., amin=1e-10, top_db=None, freeze_parameters=True)
assert np.array_equal(mel.melW.detach().numpy().ravel(), np.fromfile(args.assets / "mel-filter.f32", dtype="<f4"))
with torch.no_grad():
    s = mel(spect(torch.from_numpy(x[None, :]))).numpy()
options = ort.SessionOptions()
options.intra_op_num_threads = 2
options.inter_op_num_threads = 1
session = ort.InferenceSession(str(args.assets / "guitar-mel-compact.onnx"), options, providers=["CPUExecutionProvider"])
names = ["reg_onset_output", "reg_offset_output", "frame_output", "velocity_output"]
y = session.run(names, {"mel": s})
post = RegressionPostProcessor(100, 88, .3, .3, .1, .2)
events, _ = post.output_dict_to_midi_events({k: v[0].copy() for k, v in zip(names, y)})
with gzip.open(args.output / "reference.f32.gz", "wb") as f:
    for a in [x, s, *y]:
        f.write(np.asarray(a, dtype="<f4").tobytes())
events.sort(key=lambda e: (e["onset_time"], e["midi_note"], e["offset_time"]))
(args.output / "notes.tsv").write_text("".join(f"{e['onset_time']}\t{e['offset_time']}\t{e['midi_note']}\t{e['velocity']}\n" for e in events), encoding="utf-8")
print(f"Guitar reference: {len(events)} notes")
