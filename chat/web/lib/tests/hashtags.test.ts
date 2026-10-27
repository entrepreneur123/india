import test from 'node:test'
import assert from 'node:assert/strict'
import { hashtagsOf, roomTags, splitTags } from '../hashtags.ts'

test('hashtagsOf matches the server (073)', () => {
  // The same input as the live check of 073: order kept, a 40-letter word dropped, "_" kept, any script.
  assert.deepEqual(hashtagsOf(`#여행 #Music #bad tag! #${'x'.repeat(40)} #music #a #b, #c.#d_e #नमस्ते`), ['여행', 'music', 'bad', 'a', 'b', 'c', 'd_e', 'नमस्ते'])
  assert.deepEqual(hashtagsOf('no tags # here'), [])
  assert.equal(hashtagsOf(Array.from({ length: 12 }, (_, i) => `#t${i}`).join(' ')).length, 10)
})

test('roomTags caps at 5 and takes words with or without #', () => {
  assert.deepEqual(roomTags(['#여행', 'Music', 'music', 'a', 'b', 'c', 'd']), ['여행', 'music', 'a', 'b', 'c'])
})

test('splitTags keeps the text and marks the tags', () => {
  assert.deepEqual(splitTags('Sunset #Travel!'), [{ text: 'Sunset ', tag: null }, { text: '#Travel', tag: 'travel' }, { text: '!', tag: null }])
})
