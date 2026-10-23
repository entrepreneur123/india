import test from 'node:test'
import assert from 'node:assert/strict'
import { chimeWav, crowdChange } from '../room-sounds.ts'

test('chime: a valid 16-bit mono WAV of the right length, never clipping', () => {
  const w = chimeWav([660, 880], 90)
  const v = new DataView(w.buffer)
  const ascii = (at: number) => String.fromCharCode(...w.slice(at, at + 4))
  assert.deepEqual([ascii(0), ascii(8), ascii(12), ascii(36)], ['RIFF', 'WAVE', 'fmt ', 'data'])
  assert.equal(v.getUint32(24, true), 22_050)
  assert.equal(v.getUint32(40, true), w.length - 44)
  assert.equal(v.getUint32(4, true), w.length - 8)
  assert.equal((w.length - 44) / 2, Math.round(22_050 * 0.09) * 2)
  let peak = 0
  for (let i = 44; i < w.length; i += 2) peak = Math.max(peak, Math.abs(v.getInt16(i, true)))
  assert.ok(peak > 1000 && peak < 32_767 * 0.25, String(peak))
})

test('crowd: someone else in or out; my own coming and going, and no change, ring nothing here', () => {
  assert.equal(crowdChange(['me', 'a'], ['me', 'a', 'b'], 'me'), 'join')
  assert.equal(crowdChange(['me', 'a', 'b'], ['me', 'b'], 'me'), 'leave')
  assert.equal(crowdChange(['a'], ['a', 'me'], 'me'), null)
  assert.equal(crowdChange(['me', 'a'], ['a', 'me'], 'me'), null)
  assert.equal(crowdChange(['me', 'a'], ['me', 'b'], 'me'), 'join')
})
