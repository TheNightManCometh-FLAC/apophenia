"""Export the explicitly chosen GAPS guitar model. Run on a desktop, never in the app.

Requires the pinned Riley inference checkout on PYTHONPATH and torch/torchlibrosa,
numpy, onnx, onnxruntime with its quantization dependencies. See README.md.
No automatic downloads and no unrestricted checkpoint unpickling.
"""
import argparse
from pathlib import Path
import hashlib
import json
import numpy as np
import torch
import onnx
from onnx import helper, numpy_helper, TensorProto
from onnxruntime.quantization import quantize_dynamic, QuantType
from piano_transcription_inference.models import Regress_onset_offset_frame_velocity_CRNN

p = argparse.ArgumentParser()
p.add_argument("checkpoint", type=Path)
p.add_argument("output", type=Path)
args = p.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
allowed = [np.core.multiarray._reconstruct, np.core.multiarray.scalar, np.ndarray, np.dtype]
allowed += [type(np.dtype(t)) for t in ("float64", "float32", "int32", "int64", "uint32", "uint64")]
with torch.serialization.safe_globals(allowed):
    checkpoint = torch.load(args.checkpoint, map_location="cpu", weights_only=True)
model = Regress_onset_offset_frame_velocity_CRNN(100, 88)
model.load_state_dict(checkpoint["model"], strict=True)
model.eval()
with torch.no_grad():
    mel = model.logmel_extractor(model.spectrogram_extractor(torch.zeros(1, 160000)))
model.logmel_extractor.melW.detach().numpy().astype("<f4").tofile(args.output / "mel-filter.f32")
model.spectrogram_extractor = torch.nn.Identity()
model.logmel_extractor = torch.nn.Identity()
names = ["reg_onset_output", "reg_offset_output", "frame_output", "velocity_output"]

class Net(torch.nn.Module):
    def __init__(self):
        super().__init__()
        self.model = model

    def forward(self, x):
        output = self.model(x)
        return tuple(output[k] for k in names)

fp32 = args.output / "guitar-mel.onnx"
int8 = args.output / "guitar-mel-int8.onnx"
torch.onnx.export(Net().eval(), (mel,), str(fp32), input_names=["mel"], output_names=names, opset_version=17, dynamo=False)
quantize_dynamic(str(fp32), str(int8), weight_type=QuantType.QInt8, op_types_to_quantize=["MatMul", "Gemm"], per_channel=True)
graph = onnx.load(int8)
gru = {name for node in graph.graph.node if node.op_type == "GRU" for name in node.input[1:4]}
casts, remove, add = [], [], []
for tensor in graph.graph.initializer:
    if tensor.name not in gru or tensor.data_type != TensorProto.FLOAT:
        continue
    name = tensor.name + "_stored_fp16"
    add.append(numpy_helper.from_array(numpy_helper.to_array(tensor).astype(np.float16), name))
    casts.append(helper.make_node("Cast", [name], [tensor.name], to=TensorProto.FLOAT))
    remove.append(tensor)
for tensor in remove:
    graph.graph.initializer.remove(tensor)
graph.graph.initializer.extend(add)
nodes = list(graph.graph.node)
del graph.graph.node[:]
graph.graph.node.extend(casts + nodes)
onnx.checker.check_model(graph)
target = args.output / "guitar-mel-compact.onnx"
onnx.save(graph, target)
manifest = {"source": "xavriley/midi-transcription-models", "revision": "b7bec65a2b860aca72856b0feef58b5df407b777",
            "checkpoint": "guitar-gaps.pth", "files": {}}
for path in (args.checkpoint, target, args.output / "mel-filter.f32"):
    manifest["files"][path.name] = {"bytes": path.stat().st_size, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
(args.output / "model-metadata.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
print(json.dumps(manifest, indent=2))
