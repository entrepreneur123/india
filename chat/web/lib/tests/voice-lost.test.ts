import test from 'node:test'
import assert from 'node:assert/strict'
import { beatsFast, droppedOut, nextHost, VOICE_BEAT_MS, VOICE_LISTENER_BEAT_MS } from '../voice-lost.ts'
import type { Profile, VoiceMember, VoiceRole } from '../backend.ts'

const m = (id: string, role: VoiceRole, joinedAt?: number, lost = false, seat: number | null = null) =>
  ({ profile: { id } as Profile, role, seat, muted: true, hand: false, lost, joinedAt }) as VoiceMember

test('a short gap resumes; past 6 s + 7 s the server has taken me out', () => {
  assert.equal(VOICE_BEAT_MS, 3_000)
  assert.equal(droppedOut(1_000, 1_000 + VOICE_BEAT_MS), false)
  assert.equal(droppedOut(1_000, 1_000 + 12_999), false)
  assert.equal(droppedOut(1_000, 1_000 + 13_000), false)
  assert.equal(droppedOut(1_000, 1_000 + 13_001), true)
})

test('a listener beats every 20 s and is out only past the 90 s rule (85 s here, for the clocks)', () => {
  assert.equal(VOICE_LISTENER_BEAT_MS, 20_000)
  for (let i = 1; i <= 3; i++) assert.equal(droppedOut(1_000, 1_000 + i * VOICE_LISTENER_BEAT_MS, false), false)
  assert.equal(droppedOut(1_000, 1_000 + 85_000, false), false)
  assert.equal(droppedOut(1_000, 1_000 + 85_001, false), true)
})

test('fast beats: a seat, the host, a co-host, or not in the room yet; a listener beats slowly', () => {
  const r = { members: [m('h', 'host', 1, false, 0), m('s', 'speaker', 2, false, 3), m('c', 'cohost'), m('l', 'listener')] }
  assert.deepEqual(['h', 's', 'c', 'l', 'x'].map((id) => beatsFast(r, id)), [true, true, true, false, true])
  assert.equal(beatsFast(null, 'l'), true)
  // Leaving the stage or taking a seat switches at once (the next beat).
  assert.equal(beatsFast({ members: [m('l', 'listener', 1, false, 2)] }, 'l'), true)
  assert.equal(beatsFast({ members: [m('s', 'listener')] }, 's'), false)
})

test('the host’s heir: the first co-host to come in who is still connected; none ends the room', () => {
  const room = (...members: VoiceMember[]) => ({ hostId: 'h', members })
  assert.equal(nextHost(room(m('h', 'host'), m('a', 'speaker'), m('b', 'listener'))), null)
  assert.equal(nextHost(room(m('h', 'host'), m('a', 'cohost', 30), m('b', 'cohost', 10)))?.profile.id, 'b')
  assert.equal(nextHost(room(m('h', 'host'), m('a', 'cohost', 30), m('b', 'cohost', 10, true)))?.profile.id, 'a')
  assert.equal(nextHost(room(m('h', 'host'), m('a', 'cohost', 10, true))), null)
  assert.equal(nextHost(room(m('a', 'cohost'), m('b', 'cohost')))?.profile.id, 'a') // the demo: in order of arrival
})
