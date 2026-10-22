import test from 'node:test'
import assert from 'node:assert/strict'
import type { PostComment } from '../backend'
import { inboxKind, likeLine, likeLocally, mediaPaths, parseComment, parsePost, parseTime, threadComments } from '../feed.ts'

const sign = (p: string | null) => (p ? `https://cdn/${p}` : null)

const rawPost = (patch: Record<string, unknown> = {}) => ({
  id: 'p1', body: 'Marine Drive', image_path: 'u1/posts/a.jpg', created_at: '2026-10-19 12:41:01.005372+00', visibility: 'public',
  likes: 3, comments: 1, liked: true,
  author: { id: 'u1', name: 'Ananya', gender: 'female', age: 23, city: 'Mumbai', country: 'IN', photo_path: null, accent: 'ocean' },
  ...patch,
})

test('parsePost reads the server JSON and keeps its own time text as the cursor', () => {
  const p = parsePost(rawPost(), sign)!
  assert.equal(p.text, 'Marine Drive')
  assert.equal(p.imageUrl, 'https://cdn/u1/posts/a.jpg')
  assert.equal(p.createdAt, Date.parse('2026-10-19T12:41:01.005+00:00'))
  assert.equal(p.cursor, '2026-10-19 12:41:01.005372+00')
  assert.equal(p.visibility, 'public')
  assert.deepEqual([p.likes, p.comments, p.liked], [3, 1, true])
  assert.equal(p.author.accent, 'ocean')
  assert.equal(p.author.photoUrl, null)
})

test('parsePost is safe with odd input', () => {
  assert.equal(parsePost(null, sign), null)
  assert.equal(parsePost(rawPost({ author: null }), sign), null)
  assert.equal(parsePost(rawPost({ created_at: 'nope' }), sign), null)
  const p = parsePost(rawPost({ visibility: 'secret', likes: -2, liked: 'yes', body: null, author: { id: 'u2', accent: 'neon' } }), sign)!
  assert.equal(p.visibility, 'friends')
  assert.equal(p.likes, 0)
  assert.equal(p.liked, false)
  assert.equal(p.text, '')
  assert.equal(p.author.accent, null)
  assert.equal(p.author.gender, 'other')
})

test('parseComment and mediaPaths', () => {
  const c = parseComment({ id: 9, post_id: 'p1', body: 'wow', created_at: '2026-10-19T12:00:00+00:00', reply_to: 4, reply_name: 'Kabir', mine: false, can_remove: true, author: { id: 'u3', name: 'Yuki', photo_path: 'u3/avatar/x.jpg' } }, sign)!
  assert.deepEqual([c.id, c.replyTo, c.replyName, c.mine, c.canRemove], [9, 4, 'Kabir', false, true])
  assert.equal(c.author.photoUrl, 'https://cdn/u3/avatar/x.jpg')
  assert.equal(parseComment({ id: '9' }, sign), null)
  assert.deepEqual(mediaPaths([rawPost(), { author: { photo_path: 'b' } }, null]), ['u1/posts/a.jpg', 'b'])
})

test('parseTime takes Postgres and ISO text', () => {
  assert.equal(parseTime('2026-10-19 12:41:01+00'), Date.parse('2026-10-19T12:41:01Z'))
  assert.equal(parseTime('2026-10-19T12:41:01.123456+05:30'), Date.parse('2026-10-19T12:41:01.123+05:30'))
})

test('unknown inbox kinds are skipped', () => {
  assert.equal(inboxKind('post_like'), 'post_like')
  assert.equal(inboxKind('post_comment'), 'post_comment')
  assert.equal(inboxKind('story_view'), null)
  assert.equal(inboxKind(undefined), null)
})

test('likeLocally moves the count once', () => {
  const p = { likes: 2, liked: false }
  assert.deepEqual(likeLocally(p, true), { likes: 3, liked: true })
  assert.equal(likeLocally(p, false), p)
  assert.deepEqual(likeLocally({ likes: 0, liked: true }, false), { likes: 0, liked: false })
})

const comment = (id: number, replyTo: number | null = null): PostComment => ({
  id, postId: 'p', text: String(id), at: id, replyTo, replyName: null, mine: false, canRemove: false,
  author: { id: 'u', name: 'U', gender: 'other', age: 20, city: null, country: null, photoUrl: null, accent: null },
})

test('threadComments puts replies under their comment', () => {
  const order = threadComments([comment(1), comment(2), comment(3, 1), comment(4, 3), comment(5, 99), comment(6, 2)])
  assert.deepEqual(order.map((x) => [x.comment.id, x.depth]), [[1, 0], [3, 1], [4, 1], [2, 0], [6, 1], [5, 0]])
})

test('likeLine', () => {
  assert.deepEqual(likeLine(1), { key: 'single', others: 0 })
  assert.deepEqual(likeLine(2), { key: 'one', others: 1 })
  assert.deepEqual(likeLine(5), { key: 'other', others: 4 })
  assert.deepEqual(likeLine(0), { key: 'single', others: 0 })
})
