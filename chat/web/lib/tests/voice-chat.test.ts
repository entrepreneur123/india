import test from 'node:test'
import assert from 'node:assert/strict'
import type { Profile } from '../backend'
import { insertMention, mentionMatches, mentionQuery, mentionRanges, mentions, parseReply, replyOf, type Quotable } from '../voice-chat.ts'

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
