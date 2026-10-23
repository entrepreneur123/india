import test from 'node:test'
import assert from 'node:assert/strict'
import { framable, hostOf, previewLink, safeUrl, shareable, splitLinks } from '../links.ts'
import { nextExpiry, typingIds, TYPING_TTL_MS, type TypingSeen } from '../typing.ts'

test('links are found in text and the parts join back into it', () => {
  const text = 'look at https://example.com/a?b=1 and http://x.in now'
  const parts = splitLinks(text)
  assert.deepEqual(parts.map((p) => p.kind), ['text', 'link', 'text', 'link', 'text'])
  assert.equal(parts.map((p) => p.text).join(''), text)
  assert.equal(parts[1].kind === 'link' && parts[1].url, 'https://example.com/a?b=1')
})

test('sentence punctuation and unmatched brackets stay outside the link', () => {
  const end = splitLinks('see https://example.com/page.')
  assert.equal(end[1].text, 'https://example.com/page')
  assert.equal(end[2].text, '.')
  const wrapped = splitLinks('(https://example.com/x)')
  assert.equal(wrapped[1].text, 'https://example.com/x')
  const wiki = splitLinks('https://en.wikipedia.org/wiki/Delhi_(city)!')
  assert.equal(wiki[0].text, 'https://en.wikipedia.org/wiki/Delhi_(city)')
})

test('only http(s) becomes a link', () => {
  for (const text of ['javascript:alert(1)', 'data:text/html,hi', 'ftp://x.in/a', 'mailto:a@b.in', 'just text']) {
    assert.ok(splitLinks(text).every((p) => p.kind === 'text'), text)
  }
  assert.equal(safeUrl('https://user:pw@example.com/'), null)
  assert.equal(safeUrl('https://example.com/<b>'), 'https://example.com/%3Cb%3E')
})

test('YouTube links get no preview card; the first other link does', () => {
  assert.equal(previewLink('https://youtu.be/dQw4w9WgXcQ'), null)
  assert.equal(previewLink('https://www.youtube.com/watch?v=dQw4w9WgXcQ then https://news.example.org/a'), 'https://news.example.org/a')
  assert.equal(hostOf('https://www.example.com/a'), 'example.com')
})

test('only https links up to 500 characters can be shown to a room', () => {
  assert.ok(shareable('https://example.com/a'))
  assert.ok(!shareable('http://example.com/a'))
  assert.ok(!shareable(`https://example.com/${'a'.repeat(500)}`))
})

test('typing: never me, only people present, only fresh signals, oldest first', () => {
  const now = 100_000
  const seen: TypingSeen = new Map([
    ['me', { name: 'Me', at: now - 100 }],
    ['a', { name: 'Asha', at: now - 1000 }],
    ['b', { name: 'Ravi', at: now - 200 }],
    ['gone', { name: 'Left', at: now - 100 }],
    ['old', { name: 'Old', at: now - TYPING_TTL_MS - 1 }],
  ])
  assert.deepEqual(typingIds(seen, now, new Set(['me', 'a', 'b', 'old']), 'me'), ['a', 'b'])
  assert.equal(nextExpiry(seen, now), now - 1000 + TYPING_TTL_MS)
  assert.equal(nextExpiry(new Map(), now), null)
})

test('only a page that allows frames, and is not this app, opens in the viewer', () => {
  const here = 'https://yaari.app'
  assert.equal(framable({ url: 'https://en.wikipedia.org/wiki/Delhi', frameable: true }, here), true)
  assert.equal(framable({ url: 'https://instagram.com/p/1', frameable: false }, here), false)
  assert.equal(framable({ url: 'https://yaari.app/r/abc', frameable: true }, here), false)
  assert.equal(framable({ url: 'not a url', frameable: true }, here), false)
  assert.equal(framable(null, here), false)
})
