import test from 'node:test'
import assert from 'node:assert/strict'
import type { VoicePoll } from '../backend'
import { checkPoll, mergePoll, parsePoll, pollPercents, pollsReducer, repeatedOptions, voteLocally } from '../voice-polls.ts'

const poll = (patch: Partial<VoicePoll> = {}): VoicePoll => ({
  id: 7, question: 'Next song?', options: ['Kesariya', 'Tum Hi Ho', 'Chaleya'], counts: [2, 1, 0], total: 3,
  closed: false, by: 'u1', mine: null, ...patch,
})

test('checkPoll trims, drops empty rows and finds what is wrong', () => {
  assert.deepEqual(checkPoll('  Tea? ', [' Yes ', '', 'No ']), { question: 'Tea?', options: ['Yes', 'No'], problem: null })
  assert.equal(checkPoll('', ['a', 'b']).problem, 'question')
  assert.equal(checkPoll('x'.repeat(121), ['a', 'b']).problem, 'question')
  assert.equal(checkPoll('q', ['a', '  ']).problem, 'options')
  assert.equal(checkPoll('q', ['a', 'b', 'c', 'd', 'e']).problem, 'options')
  assert.equal(checkPoll('q', ['a', 'b'.repeat(41)]).problem, 'long')
  assert.equal(checkPoll('q', ['Chai', 'CHAI ']).problem, 'same')
  assert.deepEqual([...repeatedOptions(['Chai', 'coffee', ' chai', ''])], [2])
})

test('a live update keeps my vote; my own reply sets it', () => {
  const mine = poll({ mine: 1 })
  const fromRoom = poll({ counts: [3, 1, 0], total: 4, mine: null })
  assert.equal(mergePoll(mine, fromRoom, false).mine, 1)
  assert.deepEqual(mergePoll(mine, fromRoom, false).counts, [3, 1, 0])
  assert.equal(mergePoll(mine, poll({ mine: null }), true).mine, null)
  // Closed stays closed, whatever arrives later.
  assert.equal(mergePoll(poll({ closed: true }), poll({ closed: false }), true).closed, true)
})

test('voting at once: a first vote adds one, a change moves one', () => {
  const first = voteLocally(poll(), 2)
  assert.deepEqual([first.counts, first.total, first.mine], [[2, 1, 1], 4, 2])
  const moved = voteLocally(first, 0)
  assert.deepEqual([moved.counts, moved.total, moved.mine], [[3, 1, 0], 4, 0])
  assert.equal(voteLocally(moved, 0), moved) // the same choice again: nothing
  assert.equal(voteLocally(poll({ closed: true }), 1).mine, null)
  assert.equal(voteLocally(poll(), 5).mine, null)
})

test('the reducer: room updates, my vote, restore, junk ignored', () => {
  let s = pollsReducer({}, { type: 'mine', poll: poll({ mine: 0 }) })
  s = pollsReducer(s, { type: 'room', poll: poll({ counts: [5, 1, 0], total: 6 }) })
  assert.deepEqual([s[7].counts, s[7].mine], [[5, 1, 0], 0])
  const before = s[7]
  s = pollsReducer(s, { type: 'vote', id: 7, choice: 1 })
  assert.deepEqual([s[7].counts, s[7].mine], [[4, 2, 0], 1])
  s = pollsReducer(s, { type: 'restore', poll: before })
  assert.equal(s[7].mine, 0)
  assert.equal(pollsReducer(s, { type: 'vote', id: 99, choice: 0 }), s)
  assert.equal(pollsReducer(s, { type: 'room', poll: { ...poll(), options: ['only one'] } }), s)
})

test('percentages add up to 100', () => {
  assert.deepEqual(pollPercents([1, 1, 1], 3), [34, 33, 33])
  assert.deepEqual(pollPercents([0, 0], 0), [0, 0])
  assert.deepEqual(pollPercents([2, 1, 0], 3), [67, 33, 0])
  assert.equal(pollPercents([3, 5, 7, 11], 26).reduce((a, b) => a + b), 100)
})

test('parsePoll reads the server json and refuses anything else', () => {
  const p = parsePoll({ id: 3, question: 'Q', options: ['a', 'b'], counts: [1, '2'], total: 3, closed: false, by: 'u', mine: 1 })
  assert.deepEqual(p, { id: 3, question: 'Q', options: ['a', 'b'], counts: [1, 2], total: 3, closed: false, by: 'u', mine: 1 })
  assert.equal(parsePoll({ id: 3, question: 'Q', options: ['a', 'b'], counts: [], total: 0, mine: 9 })?.mine, null)
  assert.equal(parsePoll({ id: 'x', question: 'Q', options: ['a', 'b'] }), null)
  assert.equal(parsePoll({ id: 1, question: 'Q', options: ['a'] }), null)
  assert.equal(parsePoll(null), null)
})
