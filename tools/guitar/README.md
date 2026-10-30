# Guitar model conversion

Bundled input: `guitar-gaps.pth` from
[xavriley/midi-transcription-models](https://huggingface.co/xavriley/midi-transcription-models/tree/b7bec65a2b860aca72856b0feef58b5df407b777).
Reference inference checkout: `xavriley/piano_transcription_inference` at
`7568dc7f78b625e40cf9776e2806d164006610e3`, on `PYTHONPATH`.
Its frontend needs torchlibrosa 0.1.0. The measured export used CPU PyTorch 2.14,
ONNX 1.23 and ONNX Runtime 1.31.1, NumPy 1.26.4; protobuf 6.31.1 and
ml_dtypes 0.6.0 for ONNX quantisation. The app uses its existing ORT 1.31.0.

```
python tools/guitar/export.py path/to/guitar-gaps.pth path/to/output
```

Copy only `guitar-mel-compact.onnx`, `mel-filter.f32` and `model-metadata.json`
into `app/src/main/assets/models/guitar-gaps/`. Retain the licence notice.
Intermediate full and int8 models are desktop-only; the compact model keeps
GRU arithmetic in FP32. Rerun `GuitarPitchTest` and recorded-guitar benchmarks
after changing conversion, frontend, joining, thresholds or decoder.

The model consumes `[1,1,1001,229]` log-mel float32. The four named outputs are
`[1,1001,88]`. Ten-second 16 kHz windows overlap by five seconds; joining keeps
0–7.5 seconds of the first, 2.5–7.5 of middle windows, and 2.5–10 of the last.
It uses published .3/.3/.1 onset/offset/frame thresholds. MIDI starts at 21.
No pedal or continuous pitch-bend outputs are provided by this checkpoint.

Model conversion is optional developer tooling. Building/installing/running
Apophenia requires no Python, model download, account or network permission.
