# Rebuilds src/live/assets/nsfw_mobilenet_v2.tflite: the nsfwjs MobileNetV2 (224 px) the web app runs, converted to
# TensorFlow Lite with int8 (dynamic-range) weights, 2.9 MB. Same model and preprocessing (RGB / 255) as the web.
#
#   python -m venv venv && venv/bin/pip install tensorflow-cpu tf_keras    (tested with 2.21)
#   node -e '...' : write model.json + group1-shard1of1 from web/node_modules/nsfwjs/dist/models/mobilenet_v2
#                   (see web/scripts/nsfw-model.mjs), then:
#   venv/bin/python tools/nsfw_tflite.py <dir with model.json and group1-shard1of1> src/live/assets/nsfw_mobilenet_v2.tflite
#
# Checked against tfjs on generated neutral images: max probability difference 0.02, Porn + Hentai within 0.002.
import json, os, sys
import numpy as np
import tf_keras as keras
import tensorflow as tf

src, out = sys.argv[1], sys.argv[2]
m = json.load(open(os.path.join(src, 'model.json')))
blob = open(os.path.join(src, 'group1-shard1of1'), 'rb').read()

# tfjs layers format: uint8-quantized weights (value = q * scale + min), in manifest order.
weights, off = {}, 0
for w in m['weightsManifest'][0]['weights']:
    n = int(np.prod(w['shape'])) if w['shape'] else 1
    q = w.get('quantization')
    if q:
        val = np.frombuffer(blob, np.uint8, n, off).astype(np.float32) * q['scale'] + q['min']
        off += n
    else:
        val = np.frombuffer(blob, np.float32, n, off).copy()
        off += n * 4
    weights[w['name']] = val.reshape(w['shape'])
assert off == len(blob)

model = keras.models.model_from_json(json.dumps(m['modelTopology']['model_config']))
for layer in model.layers:
    if layer.weights:
        layer.set_weights([weights.pop(v.name.split(':')[0]) for v in layer.weights])
assert not weights, list(weights)[:5]

conv = tf.lite.TFLiteConverter.from_keras_model(model)
conv.optimizations = [tf.lite.Optimize.DEFAULT]
open(out, 'wb').write(conv.convert())
