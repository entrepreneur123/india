import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'

// public/echo-guard.js as the browser's audio thread runs it (128-sample blocks, 48 kHz as phones run it), with a
// LibriVox reading (public domain, 16 kHz, interpolated up) as the voice that plays from my speaker and comes back.
type Guard = { process(i: Float32Array[][], o: Float32Array[][]): boolean }
const RATE = 48_000
function load(): Guard {
  let Ctor: (new (o: object) => Guard) | undefined
  runInNewContext(readFileSync(new URL('../../public/echo-guard.js', import.meta.url), 'utf8'), {
    AudioWorkletProcessor: class {},
    registerProcessor: (_: string, c: new (o: object) => Guard) => (Ctor = c),
    sampleRate: RATE,
  })
  return new Ctor!({})
}
const wav = readFileSync(new URL('fixtures/speech-16k.wav', import.meta.url))
const speech16 = Float32Array.from(new Int16Array(wav.buffer.slice(wav.byteOffset + 44, wav.byteOffset + wav.length)), (v) => v / 32768)
const speech = Float32Array.from({ length: speech16.length * 3 }, (_, i) => {
  const a = speech16[Math.floor(i / 3)], b = speech16[Math.min(speech16.length - 1, Math.floor(i / 3) + 1)]
  return a + ((b - a) * (i % 3)) / 3
})
const seed = (n: number) => () => ((n = (n * 1_103_515_245 + 12_345) % 2 ** 31) / 2 ** 31 - 0.5)
/** [s] seconds of the reading, looped. */
const voice = (s: number) => Float32Array.from({ length: s * RATE }, (_, i) => speech[i % speech.length])
const silence = (s: number) => new Float32Array(s * RATE)
const concat = (...xs: Float32Array[]) => {
  const y = new Float32Array(xs.reduce((n, x) => n + x.length, 0))
  xs.reduce((at, x) => (y.set(x, at), at + x.length), 0)
  return y
}
/** What a phone's speaker does to [ref] on its way back into the mic: 150 ms late, quieter, with a short reverb. */
function echoOf(ref: Float32Array) {
  const d = 0.15 * RATE
  const y = new Float32Array(ref.length)
  for (let i = d; i < ref.length; i++) y[i] = 0.4 * ref[i - d] + 0.15 * (i - d - 1200 >= 0 ? ref[i - d - 1200] : 0)
  return y
}
function run(mic: Float32Array, ref: Float32Array) {
  const g = load()
  const y = new Float32Array(mic.length)
  for (let i = 0; i + 128 <= mic.length; i += 128) {
    const out = new Float32Array(128)
    g.process([[mic.subarray(i, i + 128)], [ref.subarray(i, i + 128)]], [[out]])
    y.set(out, i)
  }
  return y
}
const LATE = 511 // the guard's output is this many samples behind its input
const power = (x: Float32Array, a: number, b: number, shift = 0) => {
  let p = 0
  for (let i = Math.round(a * RATE); i < Math.round(b * RATE); i++) p += (x[i + shift] ?? 0) ** 2
  return p
}
const db = (a: number, b: number) => 10 * Math.log10(a / b)

test('with no voice playing, my microphone passes untouched (511 samples late)', () => {
  const hiss = seed(5)
  const mic = concat(voice(3), Float32Array.from({ length: RATE }, () => 0.01 * hiss()))
  const y = run(mic, silence(4))
  let err = 0
  for (let i = 0; i < mic.length - LATE - 1024; i++) err += (y[i + LATE] - mic[i]) ** 2
  assert.ok(db(power(mic, 0, 3.9), err) > 60, `reconstruction ${db(power(mic, 0, 3.9), err).toFixed(1)} dB`)
})

test('the voice I play comes back into the mic: at least 15 dB of it taken out, every time it plays (3 times)', () => {
  const ref = concat(voice(4), silence(1.5), voice(3), silence(1.5), voice(3))
  const noise = seed(9)
  const mic = Float32Array.from(echoOf(ref), (v) => v + 0.0005 * noise())
  const y = run(mic, ref)
  for (const [a, b] of [[2, 4], [6, 8.5], [11, 13.5]]) {
    const cut = db(power(mic, a, b), power(y, a, b, LATE))
    assert.ok(cut > 15, `${a}-${b} s: ${cut.toFixed(1)} dB`)
  }
})

test('my own voice after theirs stops is not turned down', () => {
  const ref = concat(voice(4), silence(4))
  const me = concat(silence(4.5), voice(3.5))
  const mic = Float32Array.from(echoOf(ref), (v, i) => v + 0.7 * me[i])
  const y = run(mic, ref)
  const loss = db(power(me, 5, 7.5), power(y, 5, 7.5, LATE) / 0.49)
  assert.ok(Math.abs(loss) < 1, `my voice changed by ${(-loss).toFixed(1)} dB`)
})

test('their voice stopping and my mic going fully silent (Krisp between words): the next echo is still cut', () => {
  const ref = concat(voice(4), silence(1.5), voice(3))
  const y = run(echoOf(ref), ref)
  const cut = db(power(echoOf(ref), 5.5, 6.5), power(y, 5.5, 6.5, LATE))
  assert.ok(cut > 15, `first second back: ${cut.toFixed(1)} dB`)
})

test('earbuds (their voice plays, none of it comes back): my voice over theirs passes untouched', () => {
  const ref = voice(8)
  // me: another sound from the reading (its samples out of order), not a copy of theirs
  const me = concat(silence(4), Float32Array.from(voice(4), (_, i) => speech[(i * 7 + 12_345) % speech.length]))
  const hiss = seed(3)
  const mic = Float32Array.from(me, (v) => 0.7 * v + 0.0005 * hiss())
  const y = run(mic, ref)
  let err = 0
  for (let i = Math.round(5 * RATE); i < Math.round(7.9 * RATE); i++) err += (y[i + LATE] - mic[i]) ** 2
  const snr = db(power(mic, 5, 7.9), err)
  assert.ok(snr > 40, `my voice changed: ${snr.toFixed(1)} dB of difference below it`)
})
