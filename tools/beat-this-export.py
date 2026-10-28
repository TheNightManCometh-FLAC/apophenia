import sys, os, torch, numpy as np, onnxruntime as ort
os.environ["TORCH_HOME"] = "D:/junkdrawer/torch-home"
from beat_this.inference import load_model
m = load_model("small0", "cpu")
print("params", sum(p.numel() for p in m.parameters()))
class Wrap(torch.nn.Module):
    def __init__(s, m): super().__init__(); s.m = m
    def forward(s, x):
        o = s.m(x); return o["beat"], o["downbeat"]
w = Wrap(m).eval()
x = torch.randn(1, 1500, 128).abs()
torch.onnx.export(w, (x,), "bt/beat_this_small0.onnx", input_names=["spect"], output_names=["beat", "downbeat"],
                  dynamic_axes={"spect": {1: "frames"}, "beat": {1: "frames"}, "downbeat": {1: "frames"}}, opset_version=17, dynamo=False)
s = ort.InferenceSession("bt/beat_this_small0.onnx", providers=["CPUExecutionProvider"])
for T in (1500, 700):
    x = torch.randn(1, T, 128).abs() * 3
    with torch.inference_mode(): b, d = w(x)
    ob, od = s.run(None, {"spect": x.numpy()})
    print(T, "max diff beat %.2e downbeat %.2e" % (np.abs(ob - b.numpy()).max(), np.abs(od - d.numpy()).max()))
print(os.path.getsize("bt/beat_this_small0.onnx"))
