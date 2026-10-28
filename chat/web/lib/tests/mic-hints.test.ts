import test from 'node:test'
import assert from 'node:assert/strict'
import { rms, silenceDetector, talkDetector, TALK_WINDOW_MS } from '../mic-hints.ts'

const feed = (d: (v: number, t: number) => unknown, levels: number[], step = 50) => levels.map((v, i) => d(v, i * step))

test('talking while muted: a loud start and a loud window is speech, said once per muted spell', () => {
  const d = talkDetector()
  const speech = Array.from({ length: TALK_WINDOW_MS / 50 + 2 }, () => 0.1)
  assert.equal(feed(d, speech).filter(Boolean).length, 1)
  assert.equal(feed(d, speech).filter(Boolean).length, 0) // the same spell: never again
})

test('talking while muted: a single knock (loud start, quiet window) or quiet room is not speech', () => {
  const knock = talkDetector()
  assert.equal(feed(knock, [0.2, ...Array(20).fill(0.005)]).filter(Boolean).length, 0)
  const quiet = talkDetector()
  assert.equal(feed(quiet, Array(60).fill(0.02)).filter(Boolean).length, 0)
  // ...and after a knock, real speech is still caught (3 tries)
  for (let i = 0; i < 3; i++) {
    const d = talkDetector()
    assert.equal(feed(d, [0.2, ...Array(20).fill(0.004), ...Array(20).fill(0.1)]).filter(Boolean).length, 1)
  }
})

test('no signal: 4 s of exact zeros says silent once; any sound clears it; a new mic says it again', () => {
  const d = silenceDetector()
  const out = feed(d, Array(6).fill(0), 1000)
  assert.deepEqual(out, [null, null, null, null, 'silent', null])
  assert.equal(d(0.01, 7000), 'sound')
  assert.equal(feed(d, Array(8).fill(0), 1000).filter((x) => x === 'silent').length, 0)
  const fresh = silenceDetector()
  assert.equal(feed(fresh, Array(5).fill(0), 1000).at(-1), 'silent')
  // a quiet room is not silence: tiny levels keep resetting it
  const room = silenceDetector()
  assert.equal(feed(room, Array(10).fill(0.0001), 1000).includes('silent'), false)
})

test('rms', () => {
  assert.equal(rms(new Float32Array([0, 0, 0, 0])), 0)
  assert.ok(Math.abs(rms(new Float32Array([0.5, -0.5, 0.5, -0.5])) - 0.5) < 1e-9)
})
