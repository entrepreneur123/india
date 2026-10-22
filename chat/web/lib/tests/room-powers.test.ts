import test from 'node:test'
import assert from 'node:assert/strict'
import type { VoiceMember, VoiceRole } from '../backend'
import { canManage, isMod } from '../../components/screens/voice/voice-text.ts'

const member = (id: string, role: VoiceRole): VoiceMember =>
  ({ profile: { id, name: id }, role, seat: null, muted: true, hand: false }) as unknown as VoiceMember

test('036: the host moves and removes anyone but themself', () => {
  const host = member('h', 'host')
  for (const role of ['cohost', 'speaker', 'listener'] as const) assert.ok(canManage(host, member('x', role)), role)
  assert.ok(!canManage(host, host))
})

test('036: a co-host moves and removes listeners and speakers only', () => {
  const co = member('c', 'cohost')
  assert.ok(canManage(co, member('l', 'listener')))
  assert.ok(canManage(co, member('s', 'speaker')))
  assert.ok(!canManage(co, member('h', 'host')))
  assert.ok(!canManage(co, member('c2', 'cohost')))
  assert.ok(!canManage(co, co))
})

test('listeners and speakers manage nobody; host and co-hosts run the room', () => {
  assert.ok(!canManage(member('s', 'speaker'), member('l', 'listener')))
  assert.ok(!canManage(null, member('l', 'listener')))
  assert.deepEqual((['host', 'cohost', 'speaker', 'listener'] as const).map((r) => isMod(member('x', r))), [true, true, false, false])
})
