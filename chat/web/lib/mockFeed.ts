// The demo's posts, likes, comments and profile styles (033), in their own chunk: fetched the first time the demo
// needs them (the feed, a profile), so the demo's first screen doesn't grow.
import {
  BackendError,
  type InboxItem,
  type FeedPost,
  type FeedTab,
  type Post,
  type PostAuthor,
  type PostComment,
  type PostVisibility,
  type Profile,
  type ProfileStyle,
  type ProfileStyleInput,
} from './backend'
import { FEED_PAGE, COMMENTS_PAGE } from './feed'
import { EMPTY_STYLE, styleInput } from './profile-style'

const wait = (ms: number) => new Promise<void>((r) => setTimeout(r, ms))
const MIN = 60_000

function hash(seed: string) {
  let n = 0
  for (const ch of seed) n = (n * 31 + ch.charCodeAt(0)) | 0
  return Math.abs(n)
}

/** The demo people's covers are their accent's gradient; the rest is written here. */
const STYLES: Record<string, Omit<ProfileStyle, 'cover'>> = {
  u_ananya: {
    accent: 'sunset', status: 'Chai, K-dramas and long walks by the sea', languages: ['hi', 'mr', 'en'],
    prompts: [{ q: 'weekend', a: 'Marine Drive at sunset, then vada pav with my cousins' }, { q: 'song', a: 'Kesariya, still, after all this time' }],
  },
  u_kabir: {
    accent: 'ocean', status: 'Designing things people actually use', languages: ['hi', 'pa', 'en'],
    prompts: [{ q: 'talk', a: 'you have opinions about fonts' }, { q: 'goal', a: 'Finally learn to bowl a proper leg spin' }],
  },
  u_sofia: {
    accent: 'mint', status: 'Somewhere between the sea and the kitchen', languages: ['pt', 'en'],
    prompts: [{ q: 'food', a: 'My grandmother’s bacalhau. Nothing comes close.' }, { q: 'travel', a: 'Kerala backwaters, this winter' }, { q: 'laugh', a: 'Me, trying to surf' }],
  },
  u_yuki: {
    accent: 'violet', status: 'Shooting film, one roll at a time', languages: ['en'],
    prompts: [{ q: 'secret', a: 'I have never once finished a bowl of ramen' }],
  },
  u_arjun: {
    accent: 'slate', status: 'Debugging by day, open mics by night', languages: ['kn', 'ta', 'en', 'hi'],
    prompts: [{ q: 'song', a: 'Anything by Prateek Kuhad on loop' }, { q: 'weekend', a: 'Sunrise ride to Nandi Hills' }],
  },
  u_amara: { accent: 'gold', status: 'Building something big in Lagos', languages: ['en'], prompts: [{ q: 'goal', a: 'Ship our app to a million people' }] },
  u_riya: { accent: 'berry', status: 'Drawing cats, mostly', languages: ['mr', 'hi', 'en'], prompts: [{ q: 'laugh', a: 'My cat judging my sketches' }] },
  u_lucas: { accent: 'rose', status: 'Skate, code, açaí, repeat', languages: ['pt', 'en'], prompts: [] },
  u_leila: { accent: null, status: 'Reading on the ferry', languages: ['en'], prompts: [{ q: 'food', a: 'Simit and tea on the Bosphorus' }] },
}

/** Which demo posts anyone can see (Discover); the rest are friends-only. */
const PUBLIC_POSTS = new Set([
  'p_ananya1', 'p_ananya3', 'p_kabir1', 'p_sofia1', 'p_sofia4', 'p_mateo1', 'p_yuki2', 'p_amara1', 'p_amara2',
  'p_lucas1', 'p_leila1', 'p_arjun2', 'p_chloe1', 'p_omar1', 'p_riya1', 'p_riya2',
])

const COMMENT_LINES = [
  'this is gorgeous', 'take me with you next time', 'okay this is my new wallpaper', 'the colours!!', 'need the location pls',
  'how is this real', 'miss this place', 'so jealous right now', 'love this', 'big mood',
]
const REPLY_LINES = ['haha right?', 'you have to come!', 'thank you!!', 'next time for sure', 'it was even better in person']

interface SimComment extends Omit<PostComment, 'mine' | 'canRemove'> {
  removed: boolean
}
interface SimPost {
  post: Post
  authorId: string
  visibility: PostVisibility
  likers: Set<string>
  comments: SimComment[]
}

export interface FeedHost {
  me: () => Profile | null
  person: (id: string) => Profile | null
  people: Profile[]
  /** Friends' ids. */
  friends: () => string[]
  /** Friends and people I chatted with (who see my friends-only posts, and whose I see). */
  close: (id: string) => boolean
  /** The demo's inbox lines (likes and comments on my posts are added here) and its change signal. */
  inbox: () => InboxItem[]
  inboxChanged: () => void
}

export class FeedSim {
  private posts: SimPost[] = []
  private seq = 1000
  private styles = new Map<string, ProfileStyle>()
  private timers: ReturnType<typeof setTimeout>[] = []

  constructor(private host: FeedHost) {
    for (const [id, s] of Object.entries(STYLES)) this.styles.set(id, { ...s, cover: null })
    const ids = host.people.map((p) => p.id)
    for (const person of host.people) {
      for (const p of person.posts) {
        const h = hash(p.id)
        const likers = new Set(ids.filter((id, i) => id !== person.id && (h >> i) % 3 === 0))
        const comments: SimComment[] = []
        const n = h % 4
        for (let i = 0; i < n; i++) {
          const by = ids[(h + i * 5) % ids.length]
          if (by === person.id) continue
          const at = p.createdAt + (i + 1) * 37 * MIN
          comments.push(this.simComment(p.id, by, COMMENT_LINES[(h + i) % COMMENT_LINES.length], at, null))
          if (i === 0 && h % 2 === 0) {
            const first = comments[comments.length - 1]
            comments.push(this.simComment(p.id, person.id, REPLY_LINES[h % REPLY_LINES.length], at + 9 * MIN, first))
          }
        }
        this.posts.push({ post: { ...p }, authorId: person.id, visibility: PUBLIC_POSTS.has(p.id) ? 'public' : 'friends', likers, comments })
      }
    }
    this.posts.sort((a, b) => b.post.createdAt - a.post.createdAt)
  }

  /** A like or comment on my post: one inbox line per post and kind until read (n = how many). */
  private notify(kind: 'post_like' | 'post_comment', actorId: string, post: InboxItem['post']) {
    const actor = this.host.person(actorId)
    const items = this.host.inbox()
    if (!actor || !post) return
    const open = items.find((i) => i.kind === kind && i.post?.id === post.id && !i.read)
    if (open) Object.assign(open, { count: open.count + 1, actor, at: Date.now() })
    else {
      items.push({
        id: Math.max(0, ...items.map((i) => i.id)) + 1, kind, actor, at: Date.now(), read: false, count: 1, matchId: null,
        roomId: null, roomTitle: null, roomOpen: false, pending: false, post,
      })
    }
    items.sort((a, b) => b.at - a.at)
    this.host.inboxChanged()
  }

  private simComment(postId: string, by: string, text: string, at: number, replyTo: SimComment | null): SimComment {
    return {
      id: this.seq++, postId, text, at, replyTo: replyTo?.id ?? null, replyName: replyTo ? replyTo.author.name : null,
      author: this.author(by)!, removed: false,
    }
  }

  private get meId() {
    return this.host.me()?.id ?? 'u_me'
  }

  private profileOf(id: string): Profile | null {
    const me = this.host.me()
    return me && id === me.id ? me : this.host.person(id)
  }

  private author(id: string): PostAuthor | null {
    const p = this.profileOf(id)
    if (!p) return null
    return {
      id: p.id, name: p.name, gender: p.gender, age: p.age, city: p.city || null, country: p.country ?? null, photoUrl: p.photoUrl ?? null,
      accent: this.style(id).accent, verified: !!p.verified,
    }
  }

  private visible(s: SimPost) {
    return s.authorId === this.meId || s.visibility === 'public' || this.host.close(s.authorId)
  }

  private view(s: SimPost): FeedPost | null {
    const author = this.author(s.authorId)
    if (!author) return null
    return {
      ...s.post, cursor: new Date(s.post.createdAt).toISOString(), visibility: s.visibility, likes: s.likers.size,
      comments: s.comments.filter((c) => !c.removed).length, liked: s.likers.has(this.meId), author,
    }
  }

  private page(list: SimPost[], before?: string, limit = FEED_PAGE) {
    const until = before ? Date.parse(before) : Infinity
    return list.filter((s) => s.post.createdAt < until).slice(0, limit).flatMap((s) => this.view(s) ?? [])
  }

  private find(postId: string) {
    const s = this.posts.find((x) => x.post.id === postId)
    if (!s || !this.visible(s)) throw new BackendError('not_found')
    return s
  }

  style(userId: string): ProfileStyle {
    if (userId === this.meId) return this.host.me()?.style ?? EMPTY_STYLE
    return this.styles.get(userId) ?? EMPTY_STYLE
  }

  async setStyle(input: ProfileStyleInput, cover: File | null | undefined): Promise<ProfileStyle> {
    await wait(500)
    const { input: clean, problem } = styleInput(input)
    if (problem) throw new BackendError('bad_request')
    const old = this.host.me()?.style?.cover ?? null
    const coverPhoto = cover
      ? { id: `u_me/cover/${this.seq++}.jpg`, url: URL.createObjectURL(cover) }
      : clean.coverId && old?.id === clean.coverId ? old : null
    return { cover: coverPhoto, accent: clean.accent, status: clean.status, languages: clean.languages, prompts: clean.prompts }
  }

  async feed(tab: FeedTab, before?: string) {
    await wait(450)
    const friends = new Set(this.host.friends())
    const list = tab === 'friends'
      ? this.posts.filter((s) => s.authorId === this.meId || friends.has(s.authorId))
      : this.posts.filter((s) => s.visibility === 'public')
    return this.page(list, before)
  }

  /** A person's posts I may see (sync: the demo's profiles carry them). */
  postsOfNow(userId: string): FeedPost[] {
    return this.page(this.posts.filter((s) => s.authorId === userId && this.visible(s)), undefined, 50)
  }

  async postsOf(userId: string, before?: string) {
    await wait(350)
    return this.page(this.posts.filter((s) => s.authorId === userId && this.visible(s)), before, 50)
  }

  async post(postId: string) {
    await wait(250)
    const s = this.posts.find((x) => x.post.id === postId)
    return s && this.visible(s) ? this.view(s) : null
  }

  async add(post: Post, visibility: PostVisibility): Promise<FeedPost> {
    const s: SimPost = { post, authorId: this.meId, visibility, likers: new Set(), comments: [] }
    this.posts.unshift(s)
    // The demo's friends notice: two likes, then a comment (each arrives in Notifications).
    const brief = { id: post.id, text: post.text.slice(0, 80), imageUrl: post.imageUrl ?? null }
    const later = (ms: number, fn: () => void) => this.timers.push(setTimeout(() => this.posts.includes(s) && fn(), ms))
    later(3500, () => {
      s.likers.add('u_sofia')
      this.notify('post_like', 'u_sofia', brief)
    })
    later(6000, () => {
      s.likers.add('u_arjun')
      this.notify('post_like', 'u_arjun', brief)
    })
    later(8500, () => {
      s.comments.push(this.simComment(post.id, 'u_yuki', post.imageUrl ? 'okay this photo is everything' : 'so true!!', Date.now(), null))
      this.notify('post_comment', 'u_yuki', brief)
    })
    return this.view(s)!
  }

  async remove(postId: string) {
    await wait(300)
    const i = this.posts.findIndex((x) => x.post.id === postId && x.authorId === this.meId)
    if (i < 0) throw new BackendError('not_found')
    this.posts.splice(i, 1)
  }

  async like(postId: string, on: boolean) {
    await wait(220)
    const s = this.find(postId)
    if (on) s.likers.add(this.meId)
    else s.likers.delete(this.meId)
    return { likes: s.likers.size, liked: on }
  }

  private commentView(s: SimPost, c: SimComment): PostComment {
    const { removed: _removed, ...rest } = c
    return { ...rest, mine: c.author.id === this.meId, canRemove: c.author.id === this.meId || s.authorId === this.meId }
  }

  async comments(postId: string, after?: number) {
    await wait(300)
    const s = this.find(postId)
    return s.comments.filter((c) => !c.removed && (after === undefined || c.id > after)).slice(0, COMMENTS_PAGE).map((c) => this.commentView(s, c))
  }

  private recentComments: number[] = []

  async addComment(postId: string, text: string, replyTo: number | null) {
    await wait(260)
    const body = text.trim()
    if ([...body].length < 1 || [...body].length > 300) throw new BackendError('bad_request')
    const now = Date.now()
    this.recentComments = this.recentComments.filter((t) => now - t < MIN)
    if (this.recentComments.length >= 10) throw new BackendError('slow_down')
    this.recentComments.push(now)
    const s = this.find(postId)
    const to = replyTo === null ? null : s.comments.find((c) => c.id === replyTo && !c.removed) ?? null
    const c = this.simComment(postId, this.meId, body, now, to)
    s.comments.push(c)
    // Someone else's post: its author answers after a moment.
    if (s.authorId !== this.meId && !to) {
      this.timers.push(setTimeout(() => {
        if (!c.removed) s.comments.push(this.simComment(postId, s.authorId, REPLY_LINES[hash(body) % REPLY_LINES.length], Date.now(), c))
      }, 2500))
    }
    return this.commentView(s, c)
  }

  async removeComment(id: number) {
    await wait(200)
    for (const s of this.posts) {
      const c = s.comments.find((x) => x.id === id && !x.removed)
      if (!c) continue
      if (c.author.id !== this.meId && s.authorId !== this.meId) break
      c.removed = true
      return
    }
    throw new BackendError('not_found')
  }

  async report(postId: string | null, commentId: number | null) {
    await wait(400)
    if (postId) {
      const s = this.find(postId)
      if (s.authorId === this.meId) throw new BackendError('not_found')
    }
    if (commentId !== null && !this.posts.some((s) => s.comments.some((c) => c.id === commentId && !c.removed && c.author.id !== this.meId))) {
      throw new BackendError('not_found')
    }
  }
}
