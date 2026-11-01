# Writes public/silero-vad.bin, the 16 kHz half of Silero VAD (MIT, github.com/snakers4/silero-vad) as float16
# little-endian (probabilities within 0.002 of float32's, measured), in the order public/voice-gate.js reads it. Run once by hand (pip install onnx numpy):
#   python scripts/silero-weights.py silero_vad_v6.onnx public/silero-vad.bin
# (the .onnx as @ricky0123/vad-web 0.0.31 ships it in dist/).
import sys, onnx, numpy as np
from onnx import numpy_helper

g = next(a.g for a in onnx.load(sys.argv[1]).graph.node[2].attribute if a.name == 'then_branch')  # sr == 16000
t = {n.output[0].split('__')[-1]: numpy_helper.to_array(n.attribute[0].t) for n in g.node if n.op_type == 'Constant'}
names = [f'encoder.{i}.reparam_conv.{p}' for i in range(4) for p in ('weight', 'bias')]
names += [f'decoder.rnn.{p}' for p in ('weight_ih', 'weight_hh', 'bias_ih', 'bias_hh')] + ['decoder.decoder.2.weight', 'decoder.decoder.2.bias']
w = np.concatenate([t[n].ravel() for n in names]).astype('<f2')
assert np.isfinite(w).all()
w.tofile(sys.argv[2])
print(sys.argv[2], w.size, 'floats', [t[n].shape for n in names])
