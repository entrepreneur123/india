import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'

// public/voice-gate.js as the browser's audio thread runs it (128-sample blocks), here at 16 kHz with the real model.
// The speech is 1.5 s of a LibriVox reading (public domain), 16-bit mono.
type Gate = { process(i: Float32Array[][], o: Float32Array[][]): boolean }
const RATE = 16_000
const weights = readFileSync(new URL('../../public/silero-vad.bin', import.meta.url))
function load(): Gate {
  let Ctor: (new (o: object) => Gate) | undefined
  runInNewContext(readFileSync(new URL('../../public/voice-gate.js', import.meta.url), 'utf8'), {
    AudioWorkletProcessor: class {},
    registerProcessor: (_: string, c: new (o: object) => Gate) => (Ctor = c),
    sampleRate: RATE,
  })
  return new Ctor!({ processorOptions: { weights: weights.buffer.slice(weights.byteOffset, weights.byteOffset + weights.length) } })
}
const wav = readFileSync(new URL('fixtures/speech-16k.wav', import.meta.url))
const speech = Float32Array.from(new Int16Array(wav.buffer.slice(wav.byteOffset + 44, wav.byteOffset + wav.length)), (v) => v / 32768)
const seed = (n: number) => () => ((n = (n * 1_103_515_245 + 12_345) % 2 ** 31) / 2 ** 31 - 0.5)
const hiss = seed(3)
/** A quiet room: -51 dBFS of noise, so a gate left open would show. */
const room = (s: number) => Float32Array.from({ length: s * RATE }, () => 0.01 * hiss())
/** Every output sample of [x] through a new gate. */
function run(x: Float32Array) {
  const g = load()
  const y = new Float32Array(x.length)
  for (let i = 0; i + 128 <= x.length; i += 128) {
    const out = new Float32Array(128)
    g.process([[x.subarray(i, i + 128)]], [[out]])
    y.set(out, i)
  }
  return y
}
const concat = (...xs: Float32Array[]) => {
  const y = new Float32Array(xs.reduce((n, x) => n + x.length, 0))
  xs.reduce((at, x) => (y.set(x, at), at + x.length), 0)
  return y
}
test('loud sounds that are not a voice never go out: a -20 dBFS tone, noise and clicks', () => {
  const tone = Float32Array.from(room(3), (_, i) => 0.14 * Math.sin((2 * Math.PI * 440 * i) / RATE))
  const rnd = seed(7)
  const noise = Float32Array.from(room(3), () => 0.35 * rnd())
  const clicks = Float32Array.from(room(3), (_, i) => (i % 2_400 < 40 ? 0.5 * rnd() : 0)) // a key every 150 ms
  for (const x of [tone, noise, clicks]) assert.equal(Math.max(...run(x).map(Math.abs)), 0)
})

test('speech goes out from its start, untouched, and the gate closes after it; again the second time', () => {
  const x = concat(room(1), speech, room(1.5), speech, room(2))
  const y = run(x)
  const lead = Math.round(0.064 * RATE)
  const start = RATE + speech.findIndex((v) => Math.abs(v) > 0.01) // the first syllable
  for (const s of [start, start + speech.length + 1.5 * RATE]) {
    const opened = y.findIndex((v, i) => i >= s - lead && v !== 0) - lead - s
    assert.ok(opened <= 0.02 * RATE, `opened ${(opened / RATE) * 1000} ms after the voice began`)
  }
  const mid = start + Math.round(0.5 * RATE)
  for (let i = mid; i < mid + 1_000; i++) assert.equal(y[i + lead], x[i]) // open: the sound itself, LEAD late
  assert.equal(Math.max(...y.subarray(RATE + speech.length + RATE, 2.5 * RATE + speech.length).map(Math.abs)), 0, 'closed 1 s after the voice')
  assert.equal(Math.max(...y.subarray(y.length - RATE).map(Math.abs)), 0, 'closed again after the second time')
})
