import test from 'node:test'
import assert from 'node:assert/strict'
import { droppedOut, VOICE_BEAT_MS, VOICE_GONE_MS } from '../voice-lost.ts'

test('a short gap resumes; past 6 s + 7 s the server has taken me out', () => {
  assert.equal(VOICE_BEAT_MS, 3_000)
  assert.equal(VOICE_GONE_MS, 13_000)
  assert.equal(droppedOut(1_000, 1_000 + VOICE_BEAT_MS), false)
  assert.equal(droppedOut(1_000, 1_000 + 12_999), false)
  assert.equal(droppedOut(1_000, 1_000 + 13_000), false)
  assert.equal(droppedOut(1_000, 1_000 + 13_001), true)
})
