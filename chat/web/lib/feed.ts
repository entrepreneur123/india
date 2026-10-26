// Posts, likes and comments (033_profiles_feed.sql): reading the server's JSON, my own likes on screen and the
// comment thread order. No imports but types, so the unit tests run it directly (node --test).
import type { Accent, FeedPost, Gender, InboxKind, PostAuthor, PostComment, PostVisibility } from './backend'

export const POST_MAX = 500
export const COMMENT_MAX = 300
export const COMMENTS_PAGE = 50

const ACCENT_IDS = new Set(['rose', 'violet', 'ocean', 'mint', 'sunset', 'gold', 'berry', 'slate'])
const INBOX_KINDS = new Set<string>(['message', 'friend_request', 'friend_accept', 'room_invite', 'post_like', 'post_comment', 'call', 'gift'])

/** Postgres time text ("2026-10-19 12:41:01.00537+00") as ms; older Safari needs strict ISO first. */
export function parseTime(ts: string): number {
  return Date.parse(
    ts
      .replace(' ', 'T')
      .replace(/(\.\d{3})\d+/, '$1')
      .replace(/([+-]\d{2})$/, '$1:00'),
  )
}

type Json = Record<string, unknown>
const str = (v: unknown): string | null => (typeof v === 'string' ? v : null)
const num = (v: unknown): number => (typeof v === 'number' && Number.isFinite(v) ? v : 0)
const isObj = (v: unknown): v is Json => !!v && typeof v === 'object' && !Array.isArray(v)
const gender = (g: unknown): Gender => (g === 'male' || g === 'female' ? g : 'other')

/** Signs a stored file's path to a URL (null: none, or not signed). */
export type Signer = (path: string | null) => string | null

export function parseAuthor(raw: unknown, sign: Signer): PostAuthor | null {
  if (!isObj(raw) || !str(raw.id)) return null
  return {
    id: raw.id as string,
    name: str(raw.name) ?? '',
    gender: gender(raw.gender),
    age: num(raw.age),
    city: str(raw.city),
    country: str(raw.country),
    photoUrl: sign(str(raw.photo_path)),
    accent: ACCENT_IDS.has(raw.accent as string) ? (raw.accent as Accent) : null,
    verified: raw.verified === true,
  }
}

/** One post of feed / posts_of / post_get; null when it isn't one (or has no author any more). */
export function parsePost(raw: unknown, sign: Signer): FeedPost | null {
  if (!isObj(raw) || !str(raw.id) || !str(raw.created_at)) return null
  const author = parseAuthor(raw.author, sign)
  if (!author) return null
  const at = parseTime(raw.created_at as string)
  if (Number.isNaN(at)) return null
  return {
    id: raw.id as string,
    text: str(raw.body) ?? '',
    imageUrl: sign(str(raw.image_path)),
    createdAt: at,
    cursor: raw.created_at as string,
    visibility: (raw.visibility === 'public' ? 'public' : 'friends') as PostVisibility,
    likes: Math.max(0, num(raw.likes)),
    comments: Math.max(0, num(raw.comments)),
    liked: raw.liked === true,
    author,
  }
}

export function parseComment(raw: unknown, sign: Signer): PostComment | null {
  if (!isObj(raw) || typeof raw.id !== 'number' || !str(raw.created_at)) return null
  const author = parseAuthor(raw.author, sign)
  if (!author) return null
  return {
    id: raw.id,
    postId: str(raw.post_id) ?? '',
    text: str(raw.body) ?? '',
    at: parseTime(raw.created_at as string),
    replyTo: typeof raw.reply_to === 'number' ? raw.reply_to : null,
    replyName: str(raw.reply_name),
    mine: raw.mine === true,
    canRemove: raw.can_remove === true,
    author,
  }
}

/** The photo paths in a list of posts or comments (to sign them in one request). */
export function mediaPaths(rows: unknown[]): string[] {
  const out: string[] = []
  for (const r of rows) {
    if (!isObj(r)) continue
    if (typeof r.image_path === 'string') out.push(r.image_path)
    if (isObj(r.author) && typeof r.author.photo_path === 'string') out.push(r.author.photo_path)
  }
  return out
}

/** An inbox line's kind, or null for one this app doesn't know (a newer server): such lines are skipped. */
export function inboxKind(kind: unknown): InboxKind | null {
  return typeof kind === 'string' && INBOX_KINDS.has(kind) ? (kind as InboxKind) : null
}

/** My like on screen at once (the server's answer then sets the real count). */
export function likeLocally<T extends { likes: number; liked: boolean }>(post: T, on: boolean): T {
  if (post.liked === on) return post
  return { ...post, liked: on, likes: Math.max(0, post.likes + (on ? 1 : -1)) }
}

/**
 * The thread order: each comment, then the replies to it (and replies to those) oldest first. A reply whose
 * comment is gone (removed, or not loaded) stays where it is in time. `depth`: 0 for a comment, 1 for a reply.
 */
export function threadComments(list: PostComment[]): { comment: PostComment; depth: 0 | 1 }[] {
  const byId = new Map(list.map((c) => [c.id, c]))
  const rootOf = (c: PostComment): number => {
    let cur = c
    const seen = new Set<number>()
    while (cur.replyTo !== null && byId.has(cur.replyTo) && !seen.has(cur.id)) {
      seen.add(cur.id)
      cur = byId.get(cur.replyTo)!
    }
    return cur.id
  }
  const replies = new Map<number, PostComment[]>()
  const roots: PostComment[] = []
  for (const c of list) {
    const root = rootOf(c)
    if (root === c.id) roots.push(c)
    else replies.set(root, [...(replies.get(root) ?? []), c])
  }
  return roots.flatMap((r) => [{ comment: r, depth: 0 as const }, ...(replies.get(r.id) ?? []).map((c) => ({ comment: c, depth: 1 as const }))])
}

/** Which inbox line a like gets: just one person, or "and n others" (n = everyone else). */
export function likeLine(n: number): { key: 'single' | 'one' | 'other'; others: number } {
  const others = Math.max(0, n - 1)
  return { key: others === 0 ? 'single' : others === 1 ? 'one' : 'other', others }
}
