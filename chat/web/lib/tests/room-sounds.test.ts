import test from 'node:test'
import assert from 'node:assert/strict'
import { chimeWav, crowdChange, JOIN, LEAVE } from '../room-sounds.ts'

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

test('crowd: someone else in or out of the room, anyone on or off the stage; my own coming in rings nothing here', () => {
  const c = (all: string[], stage: string[] = []) => ({ all, stage })
  assert.equal(crowdChange(c(['me', 'a']), c(['me', 'a', 'b']), 'me'), 'join')
  assert.equal(crowdChange(c(['me', 'a', 'b']), c(['me', 'b']), 'me'), 'leave')
  assert.equal(crowdChange(c(['a']), c(['a', 'me']), 'me'), null)
  assert.equal(crowdChange(c(['me', 'a']), c(['a', 'me']), 'me'), null)
  assert.equal(crowdChange(c(['me', 'a']), c(['me', 'a'], ['a']), 'me'), 'join')
  assert.equal(crowdChange(c(['me', 'a']), c(['me', 'a'], ['me']), 'me'), 'join')
  assert.equal(crowdChange(c(['me', 'a'], ['me', 'a']), c(['me', 'a'], ['a']), 'me'), 'leave')
  assert.equal(crowdChange(c(['me', 'a'], ['a']), c(['me'], []), 'me'), 'leave')
})

test('in is high, out is low: every note of the join chime is above every note of the leave chime', () => {
  assert.ok(Math.min(...JOIN) > 2 * Math.max(...LEAVE)) // more than an octave apart
  assert.ok(JOIN[1] > JOIN[0] && LEAVE[1] < LEAVE[0])
})
