import test from 'node:test'
import assert from 'node:assert/strict'
import { planLive } from '../live-session.ts'

const room = { id: 'v-1', title: 'Chai pe charcha' }

test('starting a random chat from a room leaves the room', () => {
  assert.deepEqual(planLive({ room, random: { phase: 'idle' } }, { kind: 'random' }), { do: 'leaveRoom', title: 'Chai pe charcha' })
  assert.deepEqual(planLive({ room: null, random: { phase: 'idle' } }, { kind: 'random' }), { do: 'go' })
})

test('joining a room from a random chat ends it; a search just stops', () => {
  assert.deepEqual(planLive({ room: null, random: { phase: 'chat', partner: 'Ananya' } }, { kind: 'room', roomId: 'v-2' }), {
    do: 'endChat',
    partner: 'Ananya',
  })
  assert.deepEqual(planLive({ room: null, random: { phase: 'searching' } }, { kind: 'room', roomId: 'v-2' }), { do: 'stopSearch' })
  assert.deepEqual(planLive({ room: null, random: { phase: 'idle' } }, { kind: 'room', roomId: 'v-2' }), { do: 'go' })
})

test('another room takes the place of the one I am in', () => {
  assert.deepEqual(planLive({ room, random: { phase: 'idle' } }, { kind: 'room', roomId: 'v-2' }), { do: 'go' })
  assert.deepEqual(planLive({ room, random: { phase: 'idle' } }, { kind: 'room', roomId: 'v-1' }), { do: 'go' })
})
