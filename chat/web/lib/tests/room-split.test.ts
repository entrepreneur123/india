import test from 'node:test'
import assert from 'node:assert/strict'
import { clampShare, clampWidth, nearestSnap, readShare, readWidth, splitKey, stepSnap, wideDefault } from '../room-split.ts'

test('phones: a drag ends on the nearest snap point (chat only, 30 %, 50 %, 70 %)', () => {
  assert.equal(nearestSnap(0.1), 0)
  assert.equal(nearestSnap(0.2), 0.3)
  assert.equal(nearestSnap(0.44), 0.5)
  assert.equal(nearestSnap(0.69), 0.7)
  assert.equal(nearestSnap(0.95), 0.7)
  assert.equal(clampShare(0.9), 0.7)
  assert.equal(clampShare(-0.2), 0)
})

test('phones: the keyboard steps from snap to snap, also from a share between them', () => {
  assert.equal(stepSnap(0.3, 1), 0.5)
  assert.equal(stepSnap(0.3, -1), 0)
  assert.equal(stepSnap(0.55, 1), 0.7) // a share between snaps
  assert.equal(stepSnap(0.55, -1), 0.5)
  assert.equal(stepSnap(0.7, 1), 0.7)
  assert.equal(stepSnap(0, -1), 0)
})

test('wide screens: the chat column is 300 px up to 60 % of the width and starts at half', () => {
  assert.equal(clampWidth(100, 1200), 300)
  assert.equal(clampWidth(900, 1200), 720)
  assert.equal(clampWidth(500, 400), 300) // a narrow room still keeps the minimum
  assert.equal(wideDefault(1200), 600)
  assert.equal(wideDefault(400), 300)
})

test('stored choices: per device (the keys of before 28), anything odd falls back to the default', () => {
  assert.equal(splitKey('phone'), 'yaari_room_split_talk_phone')
  assert.equal(splitKey('wide'), 'yaari_room_split_talk_wide')
  assert.equal(readShare('0.5'), 0.5)
  assert.equal(readShare('0'), 0)
  assert.equal(readShare('0.9'), null)
  assert.equal(readShare('abc'), null)
  assert.equal(readShare(null), null)
  assert.equal(readWidth('420'), 420)
  assert.equal(readWidth('12'), null)
})
