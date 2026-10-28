"""fp16 storage, fp32 compute: each fp16 weight gets a Cast to float; every activation becomes float; the graph's own
input/output Casts become Identity. The arithmetic is then fp32 on every CPU (ARM64 ORT has native fp16 kernels)."""
import sys, onnx
from onnx import helper, TensorProto
src, dst = sys.argv[1:3]
m = onnx.load(src); g = m.graph
new_nodes = []
for init in g.initializer:
    if init.data_type == TensorProto.FLOAT16:
        name = init.name; init.name = name + "__f16"
        new_nodes.append(helper.make_node("Cast", [init.name], [name], to=TensorProto.FLOAT, name=name + "__cast"))
for n in g.node:
    if n.op_type == "Cast":
        to = next(a for a in n.attribute if a.name == "to")
        if to.i == TensorProto.FLOAT16: to.i = TensorProto.FLOAT
for vi in list(g.value_info) + list(g.input) + list(g.output):
    if vi.type.tensor_type.elem_type == TensorProto.FLOAT16: vi.type.tensor_type.elem_type = TensorProto.FLOAT
nodes = new_nodes + list(g.node)
del g.node[:]; g.node.extend(nodes)
onnx.checker.check_model(m)
onnx.save(m, dst)
print(dst)
