import test from 'node:test'
import assert from 'node:assert/strict'
import type { Profile, VoiceReads } from '../backend'
import { insertMention, laterReads, mentionMatches, mentionQuery, mentionRanges, mentions, movesReads, nextReads, parseReply, reactionClock, readByOthers, replyOf, type Quotable } from '../voice-chat.ts'

const sender = { id: 'u2', name: 'Rohan B.' } as Profile

test('a reply quotes text (120 characters), a poll question, or nothing for a photo', () => {
  const text: Quotable = { id: 5, kind: 'text', sender, fromMe: false, text: 'x'.repeat(200), at: 0 }
  assert.deepEqual(replyOf(text), { id: 5, sender: 'u2', name: 'Rohan B.', kind: 'text', text: 'x'.repeat(120) })
  const photo: Quotable = { id: 6, kind: 'photo', sender, fromMe: false, url: 'u', at: 0 }
  assert.equal(replyOf(photo).text, null)
  const poll: Quotable = { id: 8, kind: 'poll', sender, fromMe: false, pollId: 1, question: 'Tea?', options: ['a', 'b'], at: 0 }
  assert.deepEqual([replyOf(poll).kind, replyOf(poll).text], ['poll', 'Tea?'])
})

test('payload.reply is read safely', () => {
  assert.deepEqual(parseReply({ reply: { id: 4, sender: 'u', name: 'A', kind: 'text', text: 'hi' } }), { id: 4, sender: 'u', name: 'A', kind: 'text', text: 'hi' })
  assert.equal(parseReply({ reply: { id: 4, kind: 'photo', text: 'ignored' } })?.text, null)
  assert.equal(parseReply({ reply: { id: 4, kind: 'video' } }), null)
  assert.equal(parseReply({}), null)
  assert.equal(parseReply(null), null)
  assert.equal(parseReply({ reply: 'nope' }), null)
})

test('mentions: "@" and my name, any case, as a whole word', () => {
  assert.ok(mentions('hey @asha what do you think?', 'Asha'))
  assert.ok(mentions('@ASHA!', 'Asha'))
  assert.ok(mentions('cc @Rohan B. please', 'Rohan B.'))
  assert.ok(!mentions('asha without the at', 'Asha'))
  assert.ok(!mentions('mail asha@asha.com', 'Asha'))
  assert.ok(!mentions('@Ashantee is someone else', 'Asha'))
  assert.ok(!mentions('@ alone', ''))
  assert.deepEqual(mentionRanges('@asha and @Asha', 'asha'), [[0, 5], [10, 15]])
})

test('typing "@" offers people; picking one puts "@Name " in', () => {
  assert.deepEqual(mentionQuery('hi @ro', 6), { query: 'ro', at: 3 })
  assert.deepEqual(mentionQuery('@', 1), { query: '', at: 0 })
  assert.equal(mentionQuery('mail a@b', 8), null)
  assert.equal(mentionQuery('no at here', 10), null)
  assert.deepEqual(insertMention('hi @ro', 6, 3, 'Rohan B.'), { text: 'hi @Rohan B. ', caret: 13 })
  assert.deepEqual(insertMention('hi @ro there', 6, 3, 'Rohan'), { text: 'hi @Rohan there', caret: 10 })
  const people = [{ name: 'Priya' }, { name: 'Rohan' }, { name: 'Aarohi' }]
  assert.deepEqual(mentionMatches(people, 'ro').map((p) => p.name), ['Rohan', 'Aarohi'])
  assert.equal(mentionMatches(people, '').length, 3)
})

test('reactions are judged on the server clock: a device clock running fast does not drop them', () => {
  const fresh = reactionClock(15_000)
  const t0 = Date.parse('2026-10-23T10:00:00Z') // far behind this machine's clock, like a computer running fast
  assert.equal(fresh(t0, true), true)
  assert.equal(fresh(t0 + 1_000, false), false) // a chat line: not a reaction, only moves the clock
  assert.equal(fresh(t0 + 20_000, true), true)
  assert.equal(fresh(t0 + 2_000, true), false) // replayed: 18 s older than the newest row
})

test('104: my line shows two ticks once someone else has read it, every time (three rounds)', () => {
  let k: VoiceReads = { hi: 0, by: null, hi2: 0 }
  const read = (who: string, v: number) => {
    const n = nextReads(k, who, v)
    if (n) k = n
    return n
  }
  let line = 0
  for (let round = 0; round < 3; round++) {
    const mine = ++line // I post
    read('me', mine) // my own reading never counts for my line
    assert.ok(mine > readByOthers(k, 'me'), `round ${round}: one tick`)
    assert.equal(movesReads(k, 'me', mine), false, 'nothing to send again')
    read('b', mine)
    assert.ok(mine <= readByOthers(k, 'me'), `round ${round}: two ticks`)
    assert.equal(readByOthers(k, 'b'), mine, 'b sees my reading of the line too')
    assert.equal(read('b', mine), null, 'reading it again moves nothing')
    const theirs = ++line // b answers, I read it
    read('me', theirs)
    assert.equal(readByOthers(k, 'b'), theirs)
  }
})

test('104: the furthest reader still sees what the others read; answers out of order keep the later marks', () => {
  let k: VoiceReads = { hi: 0, by: null, hi2: 0 }
  k = nextReads(k, 'a', 5)!
  k = nextReads(k, 'b', 3)! // behind a: only hi2 moves
  assert.deepEqual(k, { hi: 5, by: 'a', hi2: 3 })
  assert.equal(readByOthers(k, 'a'), 3)
  assert.equal(readByOthers(k, 'b'), 5)
  k = nextReads(k, 'a', 9)! // a further on alone: b's 3 stays what others read
  assert.deepEqual(k, { hi: 9, by: 'a', hi2: 3 })
  k = nextReads(k, 'b', 12)! // b passes a: a's 9 becomes the others' mark
  assert.deepEqual(k, { hi: 12, by: 'b', hi2: 9 })
  assert.equal(readByOthers(null, 'a'), 0)
  assert.equal(movesReads(null, 'a', 1), true)
  assert.equal(movesReads(k, 'b', 12), false)
  assert.equal(movesReads(k, 'a', 10), true)
  const old = { hi: 5, by: 'a', hi2: 3 }
  assert.equal(laterReads(k, old), k)
  assert.equal(laterReads(old, k), k)
  assert.equal(laterReads(null, old), old)
})
