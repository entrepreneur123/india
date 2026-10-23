import test from 'node:test'
import assert from 'node:assert/strict'
import { droppedOut, nextHost, VOICE_BEAT_MS, VOICE_GONE_MS } from '../voice-lost.ts'
import type { Profile, VoiceMember, VoiceRole } from '../backend.ts'

test('a short gap resumes; past 6 s + 7 s the server has taken me out', () => {
  assert.equal(VOICE_BEAT_MS, 3_000)
  assert.equal(VOICE_GONE_MS, 13_000)
  assert.equal(droppedOut(1_000, 1_000 + VOICE_BEAT_MS), false)
  assert.equal(droppedOut(1_000, 1_000 + 12_999), false)
  assert.equal(droppedOut(1_000, 1_000 + 13_000), false)
  assert.equal(droppedOut(1_000, 1_000 + 13_001), true)
})

test('the host’s heir: the first co-host to come in who is still connected; none ends the room', () => {
  const m = (id: string, role: VoiceRole, joinedAt?: number, lost = false) =>
    ({ profile: { id } as Profile, role, seat: null, muted: true, hand: false, lost, joinedAt }) as VoiceMember
  const room = (...members: VoiceMember[]) => ({ hostId: 'h', members })
  assert.equal(nextHost(room(m('h', 'host'), m('a', 'speaker'), m('b', 'listener'))), null)
  assert.equal(nextHost(room(m('h', 'host'), m('a', 'cohost', 30), m('b', 'cohost', 10)))?.profile.id, 'b')
  assert.equal(nextHost(room(m('h', 'host'), m('a', 'cohost', 30), m('b', 'cohost', 10, true)))?.profile.id, 'a')
  assert.equal(nextHost(room(m('h', 'host'), m('a', 'cohost', 10, true))), null)
  assert.equal(nextHost(room(m('a', 'cohost'), m('b', 'cohost')))?.profile.id, 'a') // the demo: in order of arrival
})
