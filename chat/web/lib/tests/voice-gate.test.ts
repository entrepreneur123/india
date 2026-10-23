import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'

// public/voice-gate.js as the browser's audio thread runs it: 128-sample blocks at 48 kHz.
type Gate = { process(i: Float32Array[][], o: Float32Array[][]): boolean }
function load(): Gate {
  let Ctor: (new () => Gate) | undefined
  runInNewContext(readFileSync(new URL('../../public/voice-gate.js', import.meta.url), 'utf8'), {
    AudioWorkletProcessor: class {},
    registerProcessor: (_: string, c: new () => Gate) => (Ctor = c),
    sampleRate: 48_000,
  })
  return new Ctor!()
}
const tone = (rms: number) => Float32Array.from({ length: 128 }, (_, i) => Math.sin((2 * Math.PI * 300 * i) / 48_000) * rms * Math.SQRT2)
const level = (x: Float32Array) => Math.sqrt(x.reduce((s, v) => s + v * v, 0) / x.length)
const run = (g: Gate, rms: number) => {
  const out = new Float32Array(128)
  g.process([[tone(rms)]], [[out]])
  return level(out)
}

test('a quiet room (-50 dBFS) never goes out', () => {
  const g = load()
  for (let i = 0; i < 750; i++) assert.equal(run(g, 0.003), 0) // 2 s
})

test('speech goes out at once; 400 ms of quiet after it closes the gate', () => {
  const g = load()
  run(g, 0.1) // -20 dBFS: the 5 ms ramp starts
  for (let i = 0; i < 10; i++) run(g, 0.1)
  assert.ok(Math.abs(run(g, 0.1) - level(tone(0.1))) < 1e-6, 'unchanged once the ramp is over')
  const blocks = 0.4 * 48_000 / 128 // 150
  let open = 0
  for (let i = 0; i < 160; i++) if (run(g, 0.003) > 0) open++
  assert.ok(open >= blocks - 1 && open <= blocks + 3, String(open)) // open through the pause, then closed (and ramped down)
  assert.equal(run(g, 0.003), 0)
  assert.ok(run(g, 0.1) > 0, 'talking again opens it at once')
})
