import test from 'node:test'
import assert from 'node:assert/strict'
import { hasThumb, registerThumb, thumbOf, thumbPath } from '../thumbs.ts'

test('profile and post photos have a small copy beside them; nothing else does', () => {
  assert.equal(hasThumb('u1/avatar/a.jpg'), true)
  assert.equal(thumbPath('u1/avatar/a.jpg'), 'u1/avatar/a_s.jpg')
  assert.equal(hasThumb('u1/posts/a.jpg'), true)
  for (const p of ['u1/avatar/a_s.jpg', 'u1/posts/a_s.jpg', 'u1/chat/a.jpg', 'u1/rooms/a.jpg', 'u1/avatar/a.mp4', 'u1/cover/a.jpg']) assert.equal(hasThumb(p), false, p)
  assert.equal(thumbOf('https://x/full'), undefined)
  registerThumb('https://x/full', 'https://x/small')
  assert.equal(thumbOf('https://x/full'), 'https://x/small')
  assert.equal(thumbOf(null), undefined)
})
