import {
  type GroupChat,
  type LookingPost,
  LOOKING_MAX,
  type GroupLine,
  type Backend,
  BackendError,
  type Badges,
  type InboxItem,
  type IncomingCall,
  MAX_PHOTOS,
  MAX_HIGHLIGHTS,
  type Highlight,
  type ExploreKind,
  type FeedItem,
  type RoomQuery,
  type VoiceRoomSummary,
  type FeedPost,
  type PostVisibility,
  type ProfileStyleInput,
  type Friend,
  type FriendActivity,
  type GiftCheck,
  type PersonRow,
  type FriendState,
  type Match,
  type MatchEvent,
  type Message,
  type NewProfile,
  type Post,
  type Profile,
  type RecentMatch,
  type ReportReason,
  type Target,
  type VideoStatus,
  type NewVoiceRoom,
  type VoiceAction,
  type VoiceEvent,
  type KycDemoOutcome,
  type LinkMeta,
  type LookWear,
  mediaKindOf,
  type MegaphoneLine,
} from './backend'
import { CONTACT_APPS, cleanContact, type ContactApp, type Contacts } from './contacts'
import { COUNTRIES } from './countries'
import { MANNER_FLOOR, MANNERS } from './trust'
import { isItem, isVip } from './looks'
import type { LookSim } from './mockLooks'
import type { TrustSim } from './mockTrust'
import type { VoiceSim } from './mockVoice'
import type { FeedSim } from './mockFeed'
import { droppedOut } from './voice-lost'
import { art } from './mock-art'
import { hashtagsOf } from './hashtags'
import { saveVolume } from './voice-volumes'

const wait = (ms: number) => new Promise<void>((r) => setTimeout(r, ms))
function countryOfPhone(digits: string | null | undefined): string | null {
  if (!digits) return null
  const hits = COUNTRIES.filter((c) => digits.startsWith(c.dial)).sort((a, b) => b.dial.length - a.dial.length)
  return hits.length && hits.filter((c) => c.dial === hits[0].dial).length === 1 ? hits[0].iso : null
}
/** Like handle_make: the plain letters of the name and four steady digits ("ananya_4821"). */
function freeHandle(name: string, seed: string) {
  let h = 0
  for (const ch of seed) h = (h * 31 + ch.charCodeAt(0)) | 0
  const base = name.toLowerCase().replace(/[^a-z0-9._]/g, '').slice(0, 14).replace(/^[._]+|[._]+$/g, '')
  return `${base.length >= 3 ? base : 'user'}_${String(Math.abs(h) % 10_000).padStart(4, '0')}`
}
const jitter = (min: number, max: number) => min + Math.random() * (max - min)
const pick = <T,>(list: T[]): T => list[Math.floor(Math.random() * list.length)]
const uid = () => Math.random().toString(36).slice(2, 10)
const HOUR = 3_600_000
const DAY = 24 * HOUR

const EXIT_LOCK_MS = 5000
/** The demo friend's call (Arjun, voice) rings this long after the app opens. */
const DEMO_FRIEND_CALL_MS = 20_000


function post(seed: string, text: string, ageMs: number, withImage = true): Post {
  return {
    id: `p_${seed}`,
    text,
    imageUrl: withImage ? art(seed) : null,
    createdAt: Date.now() - ageMs,
  }
}

const PEOPLE: Profile[] = [
  {
    id: 'u_ananya', name: 'Ananya', gender: 'female', age: 23, city: 'Mumbai', country: 'IN',
    bio: 'Chai over coffee, always. Learning Korean one drama at a time.',
    photoUrl: null,
    posts: [
      post('ananya1', 'Marine Drive at 6pm hits different #sunset #mumbai', 2 * DAY),
      post('ananya2', 'Finally finished my first pottery class', 6 * DAY),
      post('ananya3', 'Sunday market haul', 11 * DAY),
    ],
  },
  {
    id: 'u_kabir', name: 'Kabir', gender: 'male', age: 25, city: 'Delhi', country: 'IN',
    bio: 'Product designer. Weekend cricket, weekday overthinking.',
    photoUrl: null,
    posts: [post('kabir1', 'New desk setup, finally', 3 * DAY), post('kabir2', 'Old Delhi food walk', 9 * DAY)],
  },
  {
    id: 'u_sofia', name: 'Sofia', gender: 'female', age: 24, city: 'Lisbon', country: 'PT',
    bio: 'Surfing badly, cooking well. Ask me about pastel de nata.',
    photoUrl: null,
    posts: [
      post('sofia1', 'Ericeira mornings', 1 * DAY),
      post('sofia2', 'Tram 28, tourist mode on', 5 * DAY),
      post('sofia3', 'Grandma’s recipe, my kitchen', 14 * DAY),
      post('sofia4', 'Golden hour from Miradouro #sunset #lisbon', 20 * DAY),
    ],
  },
  {
    id: 'u_mateo', name: 'Mateo', gender: 'male', age: 27, city: 'Buenos Aires', country: 'AR',
    bio: 'Tango on Thursdays, football every other day.',
    photoUrl: null,
    posts: [post('mateo1', 'Asado with the crew', 4 * DAY)],
  },
  {
    id: 'u_yuki', name: 'Yuki', gender: 'female', age: 22, city: 'Osaka', country: 'JP',
    bio: 'Film photography and late-night ramen runs.',
    photoUrl: null,
    posts: [
      post('yuki1', 'Shot on Portra 400', 2 * DAY),
      post('yuki2', 'Dotonbori lights', 8 * DAY),
      post('yuki3', 'Rainy day, warm tea', 16 * DAY),
    ],
  },
  {
    id: 'u_amara', name: 'Amara', gender: 'female', age: 26, city: 'Lagos', country: 'NG',
    bio: 'Afrobeats playlist curator. Building a fintech startup.',
    photoUrl: null,
    posts: [post('amara1', 'Demo day nerves', 1 * DAY), post('amara2', 'Lekki sunset #sunset', 7 * DAY)],
  },
  {
    id: 'u_lucas', name: 'Lucas', gender: 'male', age: 24, city: 'São Paulo', country: 'BR',
    bio: 'Skater, coder, açaí enthusiast.',
    photoUrl: null,
    posts: [post('lucas1', 'Ibirapuera session', 3 * DAY)],
  },
  {
    id: 'u_leila', name: 'Leila', gender: 'female', age: 25, city: 'Istanbul', country: 'TR',
    bio: 'Two continents, one ferry ride. Bookworm.',
    photoUrl: null,
    posts: [post('leila1', 'Bosphorus by ferry', 2 * DAY), post('leila2', 'Current read', 10 * DAY, false)],
  },
  {
    id: 'u_arjun', name: 'Arjun', gender: 'male', age: 28, city: 'Bengaluru', country: 'IN',
    bio: 'Backend engineer by day, guitarist by night.',
    photoUrl: null,
    posts: [post('arjun1', 'Nandi Hills sunrise ride #travel', 5 * DAY), post('arjun2', 'Open mic tonight', 12 * DAY)],
  },
  {
    id: 'u_chloe', name: 'Chloé', gender: 'female', age: 23, city: 'Lyon', country: 'FR',
    bio: 'Architecture student. I will judge your city’s skyline.',
    photoUrl: null,
    posts: [post('chloe1', 'Vieux Lyon rooftops', 4 * DAY)],
  },
  {
    id: 'u_omar', name: 'Omar', gender: 'male', age: 26, city: 'Dubai', country: 'AE',
    bio: 'Desert drives and good playlists.',
    photoUrl: null,
    posts: [post('omar1', 'Hatta weekend', 6 * DAY)],
  },
  {
    id: 'u_riya', name: 'Riya', gender: 'other', age: 21, city: 'Pune', country: 'IN',
    bio: 'Illustrator. Cats > people (you might change my mind).',
    photoUrl: null,
    posts: [post('riya1', 'Sketchbook page 42', 1 * DAY), post('riya2', 'Monsoon in the ghats #travel #monsoon', 9 * DAY)],
  },
  // 046: someone in VIP incognito, as the server shows them to a stranger.
  { id: 'u_vip', name: 'VIP', gender: 'female', age: 0, city: '', country: null, bio: '', photoUrl: null, posts: [] },
]

// More photos for some demo people (024).
const EXTRA_PHOTOS: Record<string, number> = { u_ananya: 2, u_sofia: 3, u_yuki: 2, u_arjun: 1 }
for (const p of PEOPLE) {
  const n = EXTRA_PHOTOS[p.id]
  if (n) p.photos = Array.from({ length: n }, (_, i) => ({ id: `${p.id}_g${i}`, url: art(`${p.id}-gallery-${i}`) }))
}

// Verified demo people (035); their manners are in lib/mockTrust.ts.
const VERIFIED = new Set(['u_ananya', 'u_kabir', 'u_sofia', 'u_yuki', 'u_amara', 'u_leila', 'u_arjun', 'u_chloe', 'u_riya'])
for (const p of PEOPLE) p.verified = VERIFIED.has(p.id)

/**
 * Demo switches in the address (to try every state): ?demo_required=1 (verification required), ?demo_required=later
 * (switched on while the app runs: my_trust still says no, the server already refuses), ?demo_kyc=off (no
 * identity provider yet, like the live server today), ?demo_manner=12 (my manner score, with enough ratings).
 */
function demoFlags() {
  const q = typeof location === 'undefined' ? new URLSearchParams() : new URLSearchParams(location.search)
  const manner = Number(q.get('demo_manner'))
  const req = q.get('demo_required')
  return { required: req === '1', gated: req === '1' || req === 'later', kycOff: q.get('demo_kyc') === 'off', manner: q.has('demo_manner') && Number.isFinite(manner) ? manner : null }
}

const GREETINGS = [
  'hey! where are you from?',
  'hiii 👋 how’s your day going?',
  'hello from the other side of the world',
  'hey there! first time on yaari?',
]

const REPLIES: { match: RegExp; lines: string[] }[] = [
  { match: /\b(hi|hey|hello|hii+|namaste)\b/i, lines: ['hey hey! what are you up to?', 'hi! nice to meet you'] },
  { match: /where|from|city|country/i, lines: ['I’m from {city}! you?', 'born and raised in {city}. ever been?'] },
  { match: /\?$/, lines: ['hmm good question 😄', 'honestly? depends on the day', 'yes! and you?'] },
  { match: /music|song|playlist/i, lines: ['send me your top song right now', 'I’ve had the same song on repeat all week'] },
  { match: /food|eat|chai|coffee/i, lines: ['now I’m hungry, thanks', 'chai > coffee, fight me'] },
]
const FALLBACK = [
  'haha that’s fair',
  'tell me more',
  'no way, same!',
  'that’s actually really cool',
  'okay I like your vibe',
  'what do you do for fun?',
  'lol you’re funny',
]

interface MatchState {
  id: string
  partner: Profile
  startedAt: number
  messages: Message[]
  listeners: Set<(e: MatchEvent) => void>
  video: VideoStatus
  /** The call asked for (or on) is a video call; false: voice only. */
  callVideo: boolean
  kind: 'random' | 'thread'
  /** Over (left, or a thread): 040, only friends write on. */
  ended: boolean
  myCount: number
  greeted: boolean
  sentMedia: boolean
  timers: ReturnType<typeof setTimeout>[]
}

export class MockBackend implements Backend {
  private signedIn = false
  private pendingPhone: string | null = null
  private me: Profile | null = null
  private searchPolls = 0
  private searchTarget: string | null = null
  private matches = new Map<string, MatchState>()
  private recentList: RecentMatch[] = []
  /** 52: "looking for" lines, and the threads they opened (those stay open, friends or not). */
  private looking: LookingPost[] | null = null
  private lookingThreads = new Set<string>()
  /** People taken off my recents, and when (048): a later meeting brings them back. */
  private recentHidden = new Map<string, number>()
  private friendMap = new Map<string, FriendState>()
  private threadByUser = new Map<string, string>()
  private msgSeq = 1
  /** Voice rooms are simulated in their own chunk, fetched the first time they are used. */
  private voice_: Promise<VoiceSim> | null = null
  private voiceSim: VoiceSim | null = null
  private inboxItems: InboxItem[] = []
  private unread = new Map<string, number>()
  private inboxListeners = new Set<() => void>()
  /** Posts, likes, comments and profile styles are simulated in their own chunk too (lib/mockFeed.ts). */
  private feed_: Promise<FeedSim> | null = null
  private feedSim: FeedSim | null = null
  // identity and manners (035)
  private flags = demoFlags()
  private trust_: Promise<TrustSim> | null = null
  // IDs and looks (040)
  private looks_: Promise<LookSim> | null = null

  constructor() {
    this.friendMap.set('u_sofia', 'friends')
    this.friendMap.set('u_arjun', 'friends')
    this.friendMap.set('u_yuki', 'friends')
    this.friendMap.set('u_riya', 'friends')
    this.friendMap.set('u_omar', 'friends')
    this.friendMap.set('u_amara', 'incoming')
    this.friendMap.set('u_lucas', 'incoming')
    this.friendMap.set('u_chloe', 'requested')

    const seedRecents: [string, number, string][] = [
      ['u_leila', 3 * HOUR, 'have a great night!'],
      ['u_mateo', 20 * HOUR, 'jajaja Messi para siempre 🐐'],
      ['u_kabir', 2 * DAY, 'catch you later'],
      ['u_vip', 3 * DAY, ':st:namaste/1:'],
    ]
    for (const [pid, ago, last] of seedRecents) {
      const partner = this.person(pid)!
      const id = `m_${uid()}`
      const at = Date.now() - ago
      // Past random chats (083): over, their lines gone for both of us (kept here as the server keeps them).
      const m = this.newMatchState(id, partner, 'random', at, [
        { id: this.msgSeq++, fromMe: false, text: pick(GREETINGS), at: at },
        { id: this.msgSeq++, fromMe: true, text: 'hey! nice to meet you', at: at + 20_000 },
        { id: this.msgSeq++, fromMe: false, text: last, at: at + 60_000 },
      ])
      m.ended = true
      this.matches.set(id, m)
      this.recentList.push({ matchId: id, partner, metAt: at, lastText: last })
    }

    // A friend's thread with two unread messages, and an inbox to show (023).
    const sofia = this.person('u_sofia')!
    const sofiaAt = Date.now() - 12 * 60_000
    this.matches.set('t_sofia', this.newMatchState('t_sofia', sofia, 'thread', sofiaAt - DAY, [
      { id: this.msgSeq++, fromMe: true, text: 'that sunrise photo was unreal', at: sofiaAt - DAY },
      { id: this.msgSeq++, fromMe: false, text: 'né?! acordei às 5 só pra isso kkkk', at: sofiaAt - 60_000 },
      { id: this.msgSeq++, fromMe: false, text: 'are you coming to the music room tonight?', at: sofiaAt },
    ]))
    this.threadByUser.set('u_sofia', 't_sofia')
    this.unread.set('t_sofia', 2)
    const item = (id: number, kind: InboxItem['kind'], actor: string, ago: number, extra: Partial<InboxItem> = {}): InboxItem => ({
      id, kind, actor: this.person(actor)!, at: Date.now() - ago, read: false, count: 1, matchId: null, roomId: null,
      roomTitle: null, roomOpen: false, pending: false, post: null, ...extra,
    })
    this.inboxItems = [
      item(6, 'gift', 'u_sofia', 2 * 60_000, { item: 'bubble_peach' }),
      item(5, 'room_invite', 'u_arjun', 4 * 60_000, { roomId: 'v-chai', roomTitle: 'Chai pe charcha', roomOpen: true }),
      item(4, 'message', 'u_sofia', 12 * 60_000, { matchId: 't_sofia', count: 2 }),
      item(3, 'friend_request', 'u_amara', HOUR, { pending: true }),
      item(2, 'friend_request', 'u_lucas', 3 * HOUR, { pending: true, read: true }),
      item(1, 'friend_accept', 'u_yuki', DAY, { read: true }),
    ]
  }

  private inboxChanged() {
    for (const l of this.inboxListeners) l()
  }

  private person(id: string) {
    return PEOPLE.find((p) => p.id === id) ?? null
  }

  private newMatchState(
    id: string,
    partner: Profile,
    kind: MatchState['kind'],
    startedAt = Date.now(),
    messages: Message[] = [],
  ): MatchState {
    return {
      id, partner, startedAt, messages, kind, ended: kind === 'thread',
      listeners: new Set(), video: 'none', callVideo: true, myCount: 0,
      greeted: kind === 'thread', sentMedia: kind === 'thread', timers: [],
    }
  }

  private requireMe() {
    if (!this.signedIn) throw new BackendError('not_allowed')
  }

  /** Like require_verified: when verification is required, unverified people can only look around. */
  private gate() {
    if (this.flags.gated && !this.me?.verified) throw new BackendError('verify_required')
  }

  private emit(m: MatchState, e: MatchEvent) {
    for (const l of m.listeners) l(e)
  }

  private schedule(m: MatchState, ms: number, fn: () => void) {
    m.timers.push(setTimeout(fn, ms))
  }

  private partnerSays(m: MatchState, text: string | null, media?: Message['media'], delay = jitter(900, 1800)) {
    this.schedule(m, 350, () => {
      const mine = m.messages.filter((x) => x.fromMe)
      if (mine.length) this.emit(m, { type: 'read', upto: mine[mine.length - 1].id })
      this.emit(m, { type: 'typing', on: true })
    })
    this.schedule(m, delay, () => {
      const msg: Message = { id: this.msgSeq++, fromMe: false, text, media: media ?? null, at: Date.now() }
      m.messages.push(msg)
      this.emit(m, { type: 'typing', on: false })
      this.emit(m, { type: 'message', message: msg })
      this.touchRecent(m, text ?? (media ? 'Sent a photo' : null))
    })
  }

  private touchRecent(m: MatchState, lastText: string | null) {
    const existing = this.recentList.find((r) => r.matchId === m.id)
    if (existing) existing.lastText = lastText ?? existing.lastText
    else if (m.kind === 'random') this.recentList.unshift({ matchId: m.id, partner: m.partner, metAt: m.startedAt, lastText })
  }

  private replyFor(text: string, partner: Profile) {
    const rule = REPLIES.find((r) => r.match.test(text.trim()))
    return pick(rule ? rule.lines : FALLBACK).replace('{city}', partner.city)
  }

  /* ---------- auth ---------- */

  async sendOtp(phone: string) {
    await wait(900)
    if (!/^\d{8,15}$/.test(phone)) throw new BackendError('not_allowed')
    this.pendingPhone = phone
  }

  async verifyOtp(phone: string, code: string) {
    await wait(800)
    if (phone !== this.pendingPhone) return false
    if (!/^\d{6}$/.test(code) || code === '000000') return false
    this.signedIn = true
    return true
  }

  async isSignedIn() {
    await wait(250)
    return this.signedIn
  }

  async myProfile() {
    await wait(300)
    if (!this.me) return null
    // My posts with their current counts, once the feed's chunk is here.
    return { ...this.me, posts: this.feedSim ? this.feedSim.postsOfNow(this.me.id) : [...this.me.posts] }
  }

  async createProfile(p: NewProfile) {
    this.requireMe()
    await wait(900)
    const born = new Date(p.birthDate)
    const now = new Date()
    let age = now.getFullYear() - born.getFullYear()
    const m = now.getMonth() - born.getMonth()
    if (m < 0 || (m === 0 && now.getDate() < born.getDate())) age--
    if (Number.isNaN(age) || age < 18) throw new BackendError('under_18')
    if (!(await import('./handle')).namePlain(p.name)) throw new BackendError('name_invalid')
    this.me = {
      id: 'u_me',
      name: p.name.trim(),
      gender: p.gender,
      age,
      city: p.city.trim(),
      bio: p.bio.trim(),
      photoUrl: p.photo ? URL.createObjectURL(p.photo) : null,
      photoPath: p.photo ? 'me_main' : null,
      // The country of the number's calling code (the longest that matches; +1 numbers stay without a flag).
      country: countryOfPhone(this.pendingPhone),
      posts: [],
      photos: [],
      dmEveryone: true, // 097: anyone may message me unless I turn it off
      handle: freeHandle(p.name, 'u_me'),
    }
    return { ...this.me }
  }

  async signOut() {
    await wait(400)
    this.signedIn = false
  }

  async deleteAccount() {
    await wait(900)
    this.signedIn = false
    this.me = null
  }

  /* ---------- random matching ---------- */

  async findMatch(target: Target, _lang?: string, countries: readonly string[] = []) {
    this.requireMe()
    this.gate()
    if (MANNERS && (this.flags.manner ?? 50) < MANNER_FLOOR) throw new BackendError('manner_low')
    if (target !== 'any' && !(this.passUntil > Date.now())) throw new BackendError('premium_required')
    await wait(jitter(300, 700))
    const key = `${target}:${countries.join()}`
    if (this.searchTarget !== key) {
      this.searchTarget = key
      this.searchPolls = 0
    }
    this.searchPolls++
    if (this.searchPolls < 2 + Math.floor(Math.random() * 2)) return null
    this.searchPolls = 0
    this.searchTarget = null
    const pool = PEOPLE.filter((p) => (target === 'any' || p.gender === target) && (!countries.length || countries.includes(p.country ?? '')))
    if (!pool.length) return null // nobody from those countries in the demo: keep searching
    const found = pick(pool)
    // Like 070's match_card: a stranger's card keeps only the gender and the country.
    const partner: Profile = found
    const id = `m_${uid()}`
    this.matches.set(id, this.newMatchState(id, partner, 'random'))
    return { id, partner } satisfies Match
  }

  async myPhone() {
    return this.pendingPhone ? `+${this.pendingPhone}` : null
  }

  abandon(matchId: string) {
    setTimeout(() => void this.leave(matchId).catch(() => undefined), EXIT_LOCK_MS + 500)
  }

  matchSignals() {
    return () => {}
  }

  async cancelSearch() {
    await wait(150)
    this.searchPolls = 0
    this.searchTarget = null
  }

  subscribe(matchId: string, on: (e: MatchEvent) => void) {
    const m = this.matches.get(matchId)
    if (!m) return () => {}
    m.listeners.add(on)
    if (m.ended) queueMicrotask(() => on({ type: 'ended' }))
    if (m.video !== 'none') queueMicrotask(() => on({ type: 'video', status: m.video, camera: m.callVideo }))
    if (!m.greeted) {
      m.greeted = true
      this.partnerSays(m, pick(GREETINGS), null, jitter(1800, 2600))
    }
    return () => {
      m.listeners.delete(on)
    }
  }

  async history(matchId: string) {
    await wait(350)
    const m = this.matches.get(matchId)
    if (!m) throw new BackendError('not_found')
    // A random chat is gone for both once it ends (083: kept on the server for reports only).
    return m.kind === 'random' && m.ended ? [] : [...m.messages]
  }

  sendTyping() {}

  async markRead(matchId: string) {
    if (!this.unread.delete(matchId)) return
    for (const i of this.inboxItems) if (i.kind === 'message' && i.matchId === matchId) i.read = true
    this.inboxChanged()
  }

  async flagNudity() {}

  async send(matchId: string, text: string | null, media: File | null, clientId: string) {
    const m = this.matches.get(matchId)
    if (!m) throw new BackendError('not_found')
    await wait(jitter(120, 260))
    // As the server: offline it can't arrive; sent again under the same name it is stored once (091).
    if (!navigator.onLine) throw new BackendError('network')
    const sent = m.messages.find((x) => x.clientId === clientId)
    if (sent) return sent
    if (m.ended && this.friendMap.get(m.partner.id) !== 'friends' && !this.lookingThreads.has(m.id)) throw new BackendError('chat_closed')
    const msg: Message = {
      id: this.msgSeq++,
      fromMe: true,
      text: text?.trim() || null,
      media: media ? { url: URL.createObjectURL(media), kind: mediaKindOf(media.type) } : null,
      at: Date.now(),
      clientId,
    }
    m.messages.push(msg)
    m.myCount++
    this.touchRecent(m, msg.text ?? 'Sent media')

    if (m.myCount === 2 && !m.sentMedia) {
      m.sentMedia = true
      this.partnerSays(m, 'this is the view from my window rn', {
        url: art(`view-${m.partner.id}`, 800, 1000),
        kind: 'image',
      })
    } else {
      this.partnerSays(m, this.replyFor(msg.text ?? '', m.partner))
    }
    return msg
  }

  async leave(matchId: string) {
    const m = this.matches.get(matchId)
    if (!m) throw new BackendError('not_found')
    if (m.kind === 'random' && Date.now() - m.startedAt < EXIT_LOCK_MS) throw new BackendError('exit_locked')
    await wait(200)
    for (const t of m.timers) clearTimeout(t)
    m.timers = []
    m.listeners.clear()
    m.ended = true
  }

  async report(_partnerId: string, _reason: ReportReason, _matchId: string | null) {
    await wait(500)
  }

  async block(userId: string) {
    await wait(300)
    // As block_user (084): 10 a day of people who aren't friends.
    const day = [...this.blocked.values()].filter((at) => at > Date.now() - 86_400_000).length
    if (this.friendMap.get(userId) !== 'friends' && !this.blocked.has(userId) && day >= 10) throw new BackendError('block_limit')
    this.blocked.set(userId, Date.now())
    this.unfriend(userId)
  }

  /** Like the server: no longer friends (unfriended, blocked) ends contact sharing both ways. */
  private unfriend(userId: string) {
    this.friendMap.delete(userId)
    this.sharedWith.delete(userId)
    this.theirContacts.delete(userId)
  }

  /** Who I blocked, and when (newest first in blocks()). */
  private blocked = new Map<string, number>()

  private personRow(p: Profile): PersonRow {
    return { id: p.id, handle: freeHandle(p.name, p.id), name: p.name, photoUrl: p.photoUrl ?? null }
  }

  async searchHandles(q: string) {
    await wait(250)
    const low = q.trim().replace(/^@/, '').toLowerCase()
    if (!low) return []
    return PEOPLE.filter((p) => !this.blocked.has(p.id) && !isVip(p)).map((p) => this.personRow(p))
      .filter((r) => r.handle.startsWith(low)).sort((a, b) => (a.handle < b.handle ? -1 : 1)).slice(0, 20)
  }

  async blocks() {
    await wait(250)
    return [...this.blocked].sort((a, b) => b[1] - a[1]).flatMap(([id]) => {
      const p = this.person(id)
      return p ? [this.personRow(p)] : []
    })
  }

  async unblock(userId: string) {
    await wait(250)
    this.blocked.delete(userId)
  }

  // ---- messenger contacts (044): mine, and what demo friends show me (Sofia does, the others don't yet) ----
  private myContactCard: Contacts = {}
  private sharedWith = new Set<string>()
  private theirContacts = new Map<string, Contacts>([['u_sofia', { whatsapp: '+5511987654321', instagram: 'sofia.lima', discord: 'sofi_l' }]])

  async setContacts(contacts: Contacts) {
    await wait(300)
    const out: Contacts = {}
    for (const [k, v] of Object.entries(contacts)) {
      const app = k as ContactApp
      if (!CONTACT_APPS.includes(app)) throw new BackendError('bad_request')
      const clean = cleanContact(app, v ?? '')
      if (clean === null) throw new BackendError('contact_invalid', app)
      if (clean) out[app] = clean
    }
    this.myContactCard = out
    return { ...out }
  }

  async myContacts() {
    await wait(250)
    return { contacts: { ...this.myContactCard }, sharedWith: [...this.sharedWith] }
  }

  async shareContacts(userId: string, on: boolean) {
    await wait(300)
    if (!on) this.sharedWith.delete(userId)
    else if (this.friendMap.get(userId) !== 'friends') throw new BackendError('not_friends')
    else this.sharedWith.add(userId)
  }

  async contactsOf(userId: string) {
    await wait(250)
    if (this.friendMap.get(userId) !== 'friends') return null
    return { contacts: this.theirContacts.get(userId) ?? null, mineShared: this.sharedWith.has(userId) }
  }

  private setCall(m: MatchState, status: VideoStatus) {
    m.video = status
    this.emit(m, { type: 'video', status, camera: m.callVideo })
    // Like the server (053): any change reads the call's line.
    const ringing = this.inboxItems.filter((i) => i.kind === 'call' && i.matchId === m.id && !i.read)
    for (const i of ringing) i.read = true
    if (ringing.length) this.inboxChanged()
  }

  /** The demo: a friend calls me (voice) once, a little after the app opens; it rings out after 30 s. */
  private async friendCalls() {
    const m = this.matches.get(await this.friendThread('u_arjun'))
    if (!m || m.video !== 'none') return
    m.callVideo = false
    this.setCall(m, 'requested_by_partner')
    this.inboxItems.unshift({
      id: Math.max(0, ...this.inboxItems.map((i) => i.id)) + 1, kind: 'call', actor: m.partner, at: Date.now(), read: false, count: 1,
      matchId: m.id, roomId: null, roomTitle: null, roomOpen: false, pending: false, post: null,
    })
    this.inboxChanged()
    this.schedule(m, 30_000, () => m.video === 'requested_by_partner' && this.setCall(m, 'none'))
  }

  async incomingCall(): Promise<IncomingCall | null> {
    const item = this.inboxItems.find((i) => i.kind === 'call' && !i.read && i.matchId)
    const m = item && this.matches.get(item.matchId!)
    return m && m.video === 'requested_by_partner' ? { matchId: m.id, caller: m.partner, video: m.callVideo } : null
  }

  /** Like the server's call_open (052): an open chat, or a past one between friends. */
  private callable(matchId: string) {
    const m = this.matches.get(matchId)
    if (!m) throw new BackendError('not_found')
    // 070: calls only between friends, in their thread (never in a random chat).
    if (m.kind === 'random' || this.friendMap.get(m.partner.id) !== 'friends') throw new BackendError('not_allowed')
    return m
  }

  async requestCall(matchId: string, video: boolean) {
    const m = this.callable(matchId)
    // Like request_call (067): a friends' thread calls only someone online.
    if (m.ended && !this.presence[m.partner.id]) throw new BackendError('offline')
    await wait(300)
    m.callVideo = video
    this.setCall(m, 'requested_by_me')
    this.schedule(m, 2400, () => this.setCall(m, 'accepted'))
  }

  async answerVideo(matchId: string, accept: boolean) {
    const m = this.callable(matchId)
    await wait(300)
    this.setCall(m, accept ? 'accepted' : 'declined')
    if (!accept) m.video = 'none'
  }

  async endVideo(matchId: string) {
    const m = this.matches.get(matchId)
    if (!m) return
    await wait(200)
    this.setCall(m, 'none')
  }

  async videoToken(_matchId: string) {
    return null // the demo has no video server; the call screen shows a preview
  }

  async callSignal() {
    return null
  }

  /* ---------- social ---------- */

  async recents() {
    await wait(600)
    // The person only: no last line from a random chat (083).
    return this.recentList
      .filter((r) => (this.recentHidden.get(r.partner.id) ?? 0) <= r.metAt)
      .map((r) => (this.matches.get(r.matchId)?.kind === 'thread' ? r : { ...r, lastText: null }))
      .sort((a, b) => b.metAt - a.metAt)
      .slice(0, 50)
  }

  async hideRecents(userIds: string[]) {
    if (userIds.length > 100) throw new BackendError('bad_request')
    await wait(300)
    for (const id of userIds) this.recentHidden.set(id, Date.now())
  }

  async profile(userId: string) {
    await wait(450)
    if (this.me && userId === this.me.id) return { ...this.me }
    const p = this.person(userId) ?? this.voiceSim?.person(userId) ?? null
    if (!p) return null
    const sim = await this.feedChunk()
    return { ...p, posts: this.person(userId) ? sim.postsOfNow(userId) : p.posts, style: sim.style(userId) }
  }

  async addPost(text: string, image: File | null, visibility: PostVisibility = 'friends'): Promise<FeedPost> {
    this.requireMe()
    this.gate()
    if (!this.me) throw new BackendError('not_found')
    const sim = await this.feedChunk()
    if (this.me.posts.filter((x) => Date.now() - x.createdAt < DAY).length >= 20) throw new BackendError('slow_down')
    await wait(700)
    const p: Post = {
      id: `p_${uid()}`,
      text: text.trim(),
      imageUrl: image ? URL.createObjectURL(image) : null,
      createdAt: Date.now(),
    }
    const full = await sim.add(p, visibility)
    this.me.posts = [full, ...this.me.posts]
    return full
  }

  async deletePost(postId: string) {
    if (!this.me) throw new BackendError('not_found')
    await (await this.feedChunk()).remove(postId)
    this.me.posts = this.me.posts.filter((p) => p.id !== postId)
  }

  // ---- posts, likes, comments ----

  private feedChunk() {
    this.feed_ ??= import('./mockFeed').then(
      (m) =>
        (this.feedSim = new m.FeedSim({
          me: () => this.me,
          person: (id) => this.person(id),
          people: PEOPLE,
          close: (id) => this.friendMap.get(id) === 'friends' || this.threadByUser.has(id) || this.recentList.some((r) => r.partner.id === id),
          inbox: () => this.inboxItems,
          inboxChanged: () => this.inboxChanged(),
        })),
    )
    return this.feed_
  }

  async setProfileStyle(style: ProfileStyleInput) {
    if (!this.me) throw new BackendError('not_found')
    this.me = { ...this.me, style: await (await this.feedChunk()).setStyle(style) }
    return (await this.myProfile())!
  }

  async postsOf(userId: string, before?: string) {
    return (await this.feedChunk()).postsOf(userId, before)
  }

  async postsByTag(tag: string, before?: string) {
    return (await this.feedChunk()).postsByTag(tag, before)
  }

  async post(postId: string) {
    return (await this.feedChunk()).post(postId)
  }

  async likePost(postId: string, on: boolean) {
    return (await this.feedChunk()).like(postId, on)
  }

  async postLikers(postId: string) {
    await wait(200)
    return (await this.feedChunk()).likerIds(postId).flatMap((id) => {
      const p = id === this.me?.id ? this.me : this.person(id)
      return p && !this.blocked.has(id) ? [this.personRow(p)] : []
    })
  }

  async comments(postId: string, after?: number) {
    return (await this.feedChunk()).comments(postId, after)
  }

  async addComment(postId: string, text: string, replyTo?: number | null) {
    this.gate()
    return (await this.feedChunk()).addComment(postId, text, replyTo ?? null)
  }

  async removeComment(commentId: number) {
    return (await this.feedChunk()).removeComment(commentId)
  }

  async reportPost(postId: string, _reason: ReportReason) {
    return (await this.feedChunk()).report(postId, null)
  }

  async reportComment(commentId: number, _reason: ReportReason) {
    return (await this.feedChunk()).report(null, commentId)
  }

  async updateProfile(name: string, city: string, bio: string, photo: File | null) {
    if (!this.me) throw new BackendError('not_found')
    await wait(700)
    if (!(await import('./handle')).namePlain(name)) throw new BackendError('name_invalid')
    this.me = {
      ...this.me,
      name: name.trim(),
      city: city.trim(),
      bio: bio.trim(),
      photoUrl: photo ? URL.createObjectURL(photo) : this.me.photoUrl,
      photoPath: photo ? 'me_main' : this.me.photoPath,
    }
    return { ...this.me }
  }

  private likedIds = new Set<string>()

  async explorePeople(kind: ExploreKind, country?: string | null) {
    await wait(300)
    const c = country ?? this.me?.country ?? null
    let list = PEOPLE.filter((p) => !this.blocked.has(p.id) && !isVip(p) && (kind === 'liked' ? this.likedIds.has(p.id) : !c || p.country === c))
    // 087: no friends or pending requests among the recommendations.
    if (kind === 'recommend') list = list.filter((p) => (this.friendMap.get(p.id) ?? 'none') === 'none').sort(() => Math.random() - 0.5)
    if (kind === 'online') list = list.filter((_, i) => i % 2 === 0)
    // Rankings show me too (078).
    if ((kind === 'likes' || kind === 'friends') && this.me && (!c || this.me.country === c)) list = [...list, this.me]
    // Rankings: a steady made-up count per person, highest first.
    const count = (id: string) => (kind === 'likes' || kind === 'friends' ? [...id].reduce((n, ch) => n + ch.charCodeAt(0), 0) % 97 : null)
    return list
      .map((p) => ({ ...this.personRow(p), age: p.age, country: p.country ?? null, count: count(p.id) }))
      .sort((a, b) => (b.count ?? 0) - (a.count ?? 0))
      .slice(0, 50)
  }

  async exploreFeed(): Promise<FeedItem[]> {
    await wait(300)
    const feed = await this.feedChunk()
    return [...this.friendMap]
      .filter(([, s]) => s === 'friends')
      .flatMap(([id], i) => {
        const p = this.person(id)
        // A friend's latest post in full (081); a friend without one shows their photo, as before.
        const post = feed.postsOfNow(id)[0]
        if (p && post) return [{ kind: 'post' as const, id: post.id, user: this.personRow(p), text: post.text, photos: post.imageUrl ? [post.imageUrl] : [], at: new Date(post.createdAt).toISOString(), post }]
        return p?.photoUrl ? [{ kind: 'post' as const, id: `f_${id}`, user: this.personRow(p), text: p.bio, photos: [p.photoUrl], at: new Date(Date.now() - i * 3_600_000).toISOString() }] : []
      })
  }

  async profileSocial(userId: string) {
    await wait(150)
    // As profile_social (066): someone in VIP takes messages from friends only.
    const vip = (p: Profile | null) => !!p && isVip(p) && this.friendMap.get(p.id) !== 'friends'
    return { likes: this.peopleOf(userId, 'likers').length, friends: this.peopleOf(userId, 'friends').length, liked: this.likedIds.has(userId), canMessage: userId !== this.me?.id && !vip(this.person(userId)) }
  }

  /** As profile_people (098); profileSocial counts the same lists. */
  private peopleOf(userId: string, kind: 'friends' | 'likers' | 'liked'): Profile[] {
    const others = PEOPLE.filter((p) => p.id !== userId && !this.blocked.has(p.id) && !isVip(p)) // VIP stays out, as 098
    const start = [...userId].reduce((h, c) => h + c.charCodeAt(0), 0) % others.length
    const ring = [...others.slice(start), ...others.slice(0, start)]
    const mine = kind === 'likers' && this.me && this.likedIds.has(userId) ? [this.me] : []
    return [...mine, ...(kind === 'friends' ? ring.slice(0, 8) : kind === 'likers' ? ring.slice(2, 12) : ring.slice(6, 11))]
  }

  async profilePeople(userId: string, kind: 'friends' | 'likers' | 'liked') {
    await wait(200)
    return this.peopleOf(userId, kind).map((p) => this.personRow(p))
  }

  async likeProfile(userId: string, on: boolean) {
    await wait(150)
    if (on) this.likedIds.add(userId)
    else this.likedIds.delete(userId)
  }

  async setDmEveryone(on: boolean) {
    if (!this.me) throw new BackendError('not_found')
    await wait(200)
    this.me = { ...this.me, dmEveryone: on }
    return { ...this.me }
  }

  async setHideOnline(on: boolean) {
    if (!this.me) throw new BackendError('not_found')
    await wait(200)
    this.me = { ...this.me, hideOnline: on }
    return { ...this.me }
  }

  async friends() {
    await wait(550)
    const list: Friend[] = []
    for (const [id, state] of this.friendMap) {
      const profile = this.person(id) ?? this.voiceSim?.person(id)
      const threadId = this.threadByUser.get(id) ?? null
      const l = threadId ? this.matches.get(threadId)?.messages.at(-1) : undefined
      const last = l ? { text: l.text ?? null, kind: l.media?.kind ?? null, mine: l.fromMe, at: l.at } : null
      if (profile && state !== 'none') list.push({ profile, state, threadId, last })
    }
    // As the server (103): the newest conversation first.
    return list.sort((a, b) => (b.last?.at ?? 0) - (a.last?.at ?? 0))
  }

  /** What the demo friends are doing (the rest are offline). */
  private presence: Record<string, FriendActivity['presence']> = { u_sofia: 'online', u_arjun: 'room', u_riya: 'matching', u_omar: 'chat' }

  async friendActivity(): Promise<FriendActivity[]> {
    const feed = await this.feedChunk()
    const presence = this.presence
    const changed: Record<string, number> = { u_riya: 20 * 60_000, u_arjun: 2 * HOUR, u_sofia: 5 * HOUR, u_yuki: 2 * DAY }
    return [...this.friendMap].flatMap(([id, state]) => {
      if (state !== 'friends') return []
      const status = feed.style(id).status || null
      return [{ id, presence: presence[id] ?? 'offline', status, statusAt: status && changed[id] ? Date.now() - changed[id] : null }]
    })
  }

  /** Who has what among the demo friends (what they wear, and gifts sent here). */
  private gifted = new Set<string>()

  async giftCheck(friendId: string, itemId: string): Promise<GiftCheck> {
    await wait(250)
    if (!isItem(itemId)) return 'bad_item'
    if (this.friendMap.get(friendId) !== 'friends') return 'not_friends'
    return this.gifted.has(`${friendId}:${itemId}`) || (await this.lookSim()).wears(friendId, itemId) ? 'owned' : 'ok'
  }

  async giftLook(friendId: string, itemId: string) {
    const check = await this.giftCheck(friendId, itemId)
    if (check !== 'ok') throw new BackendError('not_allowed')
    await wait(500)
    this.gifted.add(`${friendId}:${itemId}`)
  }

  async requestFriend(userId: string) {
    await wait(400)
    this.gate()
    const current = this.friendMap.get(userId) ?? 'none'
    const next: FriendState = current === 'incoming' ? 'friends' : current === 'friends' ? 'friends' : 'requested'
    this.friendMap.set(userId, next)
    return next
  }

  async answerFriend(userId: string, accept: boolean) {
    await wait(400)
    if (accept) this.friendMap.set(userId, 'friends')
    else this.friendMap.delete(userId)
    for (const i of this.inboxItems) {
      if (i.kind === 'friend_request' && i.actor.id === userId) Object.assign(i, { pending: false, read: true })
    }
    this.inboxChanged()
  }

  async removeFriend(userId: string) {
    await wait(400)
    this.unfriend(userId)
  }

  private lookingNow() {
    const at = Date.now()
    this.looking ??= [
      ['u_arjun', 'Anyone up for BGMI tonight? Need 2 more for a squad', 20],
      ['u_riya', 'Looking for someone to practise Spanish with 🇪🇸', 75],
      ['u_ananya', 'Who wants to watch the match in the cricket room at 7?', 140],
    ].flatMap(([id, text, min]) => {
      const author = this.person(id as string)
      return author ? [{ id: Math.round(at / 1000) - (min as number), text: text as string, at: at - (min as number) * 60_000, expiresAt: at + 86_400_000, mine: false, author }] : []
    })
    return this.looking.filter((p) => p.expiresAt > at)
  }

  async lookingFor(country?: string | null) {
    await wait(300)
    const c = country || this.me?.country
    return this.lookingNow().filter((p) => p.author.country === c).sort((a, b) => b.id - a.id)
  }

  async postLooking(text: string) {
    await wait(300)
    const body = text.trim()
    if (!this.me || body.length < 2 || body.length > LOOKING_MAX) throw new BackendError('bad_request')
    if (this.lookingNow().filter((p) => p.mine).length >= 3) throw new BackendError('slow_down')
    this.looking!.push({ id: Math.round(Date.now() / 1000), text: body, at: Date.now(), expiresAt: Date.now() + 86_400_000, mine: true, author: this.me })
  }

  async hideLooking(id: number) {
    await wait(200)
    this.looking = this.lookingNow().filter((p) => !(p.id === id && p.mine))
  }

  async replyLooking(id: number) {
    const p = this.lookingNow().find((x) => x.id === id)
    if (!p) throw new BackendError('not_found')
    const thread = await this.friendThread(p.author.id)
    this.lookingThreads.add(thread)
    return thread
  }

  async friendThread(userId: string) {
    await wait(300)
    // As friend_thread (083): the pair's own thread, never a random chat they had.
    const existing = this.threadByUser.get(userId)
    if (existing) return existing
    const partner = this.person(userId)
    if (!partner) throw new BackendError('not_found')
    const id = `t_${uid()}`
    this.matches.set(id, this.newMatchState(id, partner, 'thread'))
    this.threadByUser.set(userId, id)
    return id
  }

  // ---- voice rooms ----

  private voice() {
    this.voice_ ??= import('./mockVoice').then((m) => (this.voiceSim = new m.VoiceSim(() => this.me)))
    return this.voice_
  }

  async voiceRooms(query: RoomQuery = {}) {
    const text = query.text?.trim().toLocaleLowerCase() ?? ''
    const tag = text.startsWith('#') ? hashtagsOf(text)[0] ?? '' : null
    const countries = query.countries ?? []
    const mine = (r: VoiceRoomSummary) => Number(r.host.country === this.me?.country)
    // As voice_list5 (072): mine first, then the chosen order, then my country's rooms (a stable sort keeps the rest).
    // The demo has no chat counts: people on the stage stand in for "busy".
    const score = (r: VoiceRoomSummary) =>
      query.sort === 'popular' ? r.members + 3 * r.speakers.length
      : query.sort === 'relevant'
        ? 2 * mine(r) + 3 * Number(!!text && r.title.toLocaleLowerCase().includes(text))
        : 0
    return (await (await this.voice()).list())
      .filter(
        (r) =>
          (!text || (tag !== null ? r.tags.includes(tag) : r.title.toLocaleLowerCase().includes(text) || r.tags.includes(text))) &&
          (!query.lang || r.lang === query.lang) &&
          (!countries.length || countries.includes(r.host.country ?? '')) &&
          (query.locked !== false || (r.refusal !== 'room_password' && r.refusal !== 'room_private')),
      )
      .sort((a, b) => Number(b.mine) - Number(a.mine) || score(b) - score(a) || mine(b) - mine(a))
  }

  async setRoomExtras(roomId: string, extras: { cover?: File | null; tags: string[] }) {
    return (await this.voice()).extras(roomId, extras)
  }

  async inviteToRoom(roomId: string, userId: string) {
    await wait(350)
    if (this.friendMap.get(userId) !== 'friends') throw new BackendError('not_allowed')
    if (!(await this.voice()).liveRooms().some((r) => r.id === roomId)) throw new BackendError('room_closed')
  }

  async joinInvite(inboxId: number) {
    await wait(250)
    const i = this.inboxItems.find((x) => x.id === inboxId && x.kind === 'room_invite')
    if (!i?.roomId) throw new BackendError('not_found')
    i.read = true
    this.inboxChanged()
    ;(await this.voice()).admit(i.roomId)
    return i.roomId
  }

  async onlineCount() {
    await wait(200)
    // A believable, slowly moving number for the demo.
    return 2400 + Math.round(60 * Math.sin(Date.now() / 600_000))
  }

  // ---- inbox and badges ----

  async inbox(before?: number) {
    await wait(400)
    const sim = this.voiceSim
    if (sim) {
      const live = new Set(sim.liveRooms().map((r) => r.id))
      for (const i of this.inboxItems) if (i.roomId) i.roomOpen = live.has(i.roomId)
    }
    return this.inboxItems.filter((i) => before === undefined || i.id < before).map((i) => ({ ...i }))
  }

  async markInboxRead(ids?: number[]) {
    await wait(150)
    for (const i of this.inboxItems) if (!ids || ids.includes(i.id)) i.read = true
    this.inboxChanged()
  }

  async badges(): Promise<Badges> {
    await wait(150)
    return {
      inbox: this.inboxItems.filter((i) => !i.read).length,
      friends: [...this.friendMap.values()].filter((s) => s === 'incoming').length,
      // As my_badges (083): no unread count from a past random chat.
      chats: Object.fromEntries([...this.unread].filter(([id]) => this.matches.get(id)?.kind === 'thread' || !this.recentList.some((r) => r.matchId === id))),
    }
  }

  presencePing() {}
  friendSignals() {
    return () => undefined
  }

  // ---- megaphone (100, 101): as the server, in memory; two lines from earlier today, and a demo person shouts 20 s in ----
  private megaphoneN = 0
  private megaphoneAt = 0
  private megaphoneListeners = new Set<(m: MegaphoneLine) => void>()
  private megaphoneLog: MegaphoneLine[] | null = null

  private megaphoneOut(line: MegaphoneLine) {
    this.megaphoneLog?.unshift(line)
    this.megaphoneListeners.forEach((f) => f(line))
  }

  async megaphoneCount() {
    await wait(150)
    return this.megaphoneN
  }

  async buyMegaphone() {
    await wait(400)
    this.megaphoneN++
  }

  async sendMegaphone(text: string) {
    await wait(300)
    const body = text.trim()
    if (!body || [...body].length > 80 || !this.me) throw new BackendError('bad_request')
    if (Date.now() - this.megaphoneAt < 30_000) throw new BackendError('slow_down')
    if (this.megaphoneN < 1) throw new BackendError('no_megaphone')
    this.megaphoneN--
    this.megaphoneAt = Date.now()
    this.megaphoneOut({ id: Date.now(), user: this.personRow(this.me), text: body, at: Date.now() })
  }

  async megaphoneHistory() {
    await wait(200)
    return (this.megaphoneLog ?? []).filter((l) => !this.blocked.has(l.user.id))
  }

  megaphones(on: (m: MegaphoneLine) => void) {
    this.megaphoneListeners.add(on)
    if (!this.megaphoneLog) {
      const line = (id: number, who: string, text: string, ago: number) => {
        const p = this.person(who)
        return p ? [{ id, user: this.personRow(p), text, at: Date.now() - ago }] : []
      }
      this.megaphoneLog = [
        ...line(2, 'u_kabir', 'Anyone up for cricket talk? IPL fans, DM me 🏏', 25 * 60_000),
        ...line(3, 'u_ananya', 'Looking for people who love old Bollywood songs ✨', 3 * 3600_000),
      ]
      setTimeout(() => line(1, 'u_sofia', 'Music room at 9 tonight, everyone come! 🎶', 0).forEach((l) => this.megaphoneOut(l)), 20_000)
    }
    return () => void this.megaphoneListeners.delete(on)
  }

  private friendCallArmed = false
  inboxSignals(on: () => void) {
    if (!this.friendCallArmed && this.signedIn) {
      this.friendCallArmed = true
      setTimeout(() => void this.friendCalls(), DEMO_FRIEND_CALL_MS)
    }
    this.inboxListeners.add(on)
    return () => void this.inboxListeners.delete(on)
  }

  // ---- profile extras ----

  async addProfilePhoto(photo: File) {
    if (!this.me) throw new BackendError('not_found')
    const photos = this.me.photos ?? []
    if (photos.length >= MAX_PHOTOS) throw new BackendError('not_allowed')
    await wait(600)
    this.me = { ...this.me, photos: [...photos, { id: `me_${uid()}`, url: URL.createObjectURL(photo) }] }
    return { ...this.me }
  }

  private myHighlights: Highlight[] = []

  async highlights(userId: string) {
    await wait(200)
    return userId === this.me?.id ? this.myHighlights.map((h) => ({ ...h })) : []
  }

  async saveHighlight(h: { id: string | null; title: string; keep: string[]; add: File[] }) {
    await wait(500)
    const old = this.myHighlights.find((x) => x.id === h.id)
    if (h.id !== null && !old) throw new BackendError('not_found')
    if (h.id === null && this.myHighlights.length >= MAX_HIGHLIGHTS) throw new BackendError('not_allowed')
    const photos = [...(old?.photos ?? []).filter((p) => h.keep.includes(p.id)), ...h.add.map((f) => ({ id: `hl_${uid()}`, url: URL.createObjectURL(f) }))]
    const next = { id: h.id ?? `hl_${uid()}`, title: h.title.trim(), photos }
    this.myHighlights = old ? this.myHighlights.map((x) => (x === old ? next : x)) : [...this.myHighlights, next]
  }

  async deleteHighlight(id: string) {
    await wait(200)
    this.myHighlights = this.myHighlights.filter((x) => x.id !== id)
  }

  async removeProfilePhoto(id: string) {
    if (!this.me) throw new BackendError('not_found')
    await wait(300)
    this.me = { ...this.me, photos: (this.me.photos ?? []).filter((p) => p.id !== id) }
    return { ...this.me }
  }

  async createVoiceRoom(r: NewVoiceRoom) {
    this.gate()
    return (await this.voice()).create(r)
  }

  async joinVoice(roomId: string) {
    this.gate()
    return (await this.voice()).join(roomId)
  }

  async leaveVoice(roomId: string) {
    return (await this.voice()).leave(roomId)
  }

  async endVoice(roomId: string) {
    return (await this.voice()).endRoom(roomId)
  }

  async voiceLines(roomId: string) {
    return (await this.voice()).lines(roomId)
  }

  subscribeVoice(roomId: string, on: (e: VoiceEvent) => void) {
    let stop: (() => void) | null = null
    let closed = false
    void this.voice().then((sim) => {
      if (!closed) stop = sim.subscribe(roomId, on)
    })
    // My own connection, as the real backend shows it: the browser going offline, and back (the 13 s rule).
    let offAt = 0
    const offline = () => {
      offAt = Date.now()
      on({ type: 'connection', lost: true })
    }
    const online = () => {
      if (!offAt) return
      const gone = droppedOut(offAt, Date.now())
      offAt = 0
      if (!gone) return on({ type: 'connection', lost: false })
      on({ type: 'dropped' }) // the room screen closes and leaves
    }
    window.addEventListener('offline', offline)
    window.addEventListener('online', online)
    return () => {
      closed = true
      stop?.()
      window.removeEventListener('offline', offline)
      window.removeEventListener('online', online)
    }
  }

  async sendVoice(roomId: string, text: string, replyTo?: number | null) {
    return (await this.voice()).send(roomId, text, replyTo ?? null)
  }

  async joinVoiceCode(code: string) {
    return (await this.voice()).joinCode(code)
  }

  async updateVoiceRoom(roomId: string, patch: { title: string; lang?: string }) {
    return (await this.voice()).settings(roomId, patch)
  }

  async unlockRoom(roomId: string, password: string) {
    return (await this.voice()).unlock(roomId, password)
  }

  async setRoomPassword(roomId: string, password: string | null) {
    return (await this.voice()).setPassword(roomId, password)
  }

  async setRoomNotice(roomId: string, text: string) {
    return (await this.voice()).notice(roomId, text)
  }

  // ---- 48 group chats (069): one demo group to start with; someone answers a moment after I write ----
  private groupList: { id: string; title: string; members: string[]; lines: GroupLine[]; read: number }[] | null = null
  private groupWatchers = new Set<(groupId: string, line: GroupLine) => void>()
  private groupSeq = 100

  private demoGroups() {
    if (!this.groupList) {
      const me = this.me?.id ?? 'me'
      const t0 = Date.now() - 3 * HOUR
      const line = (senderId: string | null, kind: GroupLine['kind'], body: string, ago: number): GroupLine => ({ id: ++this.groupSeq, senderId, kind, body, at: t0 + ago })
      this.groupList = [{
        id: 'g_weekend', title: 'Weekend plans', members: [me, 'u_sofia', 'u_arjun', 'u_riya'], read: 0,
        lines: [
          line('u_arjun', 'created', 'Sofia, Riya, Lisa', 0),
          line('u_arjun', 'text', 'Movie on Saturday?', 60_000),
          line('u_sofia', 'text', 'Yes!! which one', 120_000),
          line('u_riya', 'text', 'Anything but horror pls', 180_000),
        ],
      }]
    }
    return this.groupList
  }

  private groupOf(groupId: string) {
    const g = this.demoGroups().find((x) => x.id === groupId)
    if (!g || !g.members.includes(this.me?.id ?? 'me')) throw new BackendError('not_allowed')
    return g
  }

  private groupPost(g: { id: string; lines: GroupLine[] }, line: Omit<GroupLine, 'id' | 'at'>) {
    const full = { ...line, id: ++this.groupSeq, at: Date.now() }
    g.lines.push(full)
    for (const f of this.groupWatchers) f(g.id, full)
    return full
  }

  async groups(): Promise<GroupChat[]> {
    await wait(250)
    const me = this.me?.id ?? 'me'
    return this.demoGroups()
      .filter((g) => g.members.includes(me))
      .map((g) => {
        const last = g.lines[g.lines.length - 1]
        return {
          id: g.id, title: g.title, members: g.members.flatMap((id) => (id === me ? (this.me ? [this.me] : []) : this.person(id) ?? [])),
          last: last ? { kind: last.kind, body: last.body, senderId: last.senderId } : null, lastAt: last?.at ?? 0,
          unread: g.lines.filter((l) => l.id > g.read && l.senderId !== me).length,
        }
      })
      .sort((a, b) => b.lastAt - a.lastAt)
  }

  // 49: a party search in the demo — the others are "in" a moment after the sheet opens, and a few polls of a search
  // later another party of demo people turns up.
  private parties = new Map<string, { opened: number; polls: number; matched: string | null; searching: boolean }>()
  private party(groupId: string) {
    let p = this.parties.get(groupId)
    if (!p || (p.matched && !this.demoGroups().some((g) => g.id === p!.matched))) this.parties.set(groupId, (p = { opened: Date.now(), polls: 0, matched: null, searching: false }))
    return p
  }

  async partyState(groupId: string, ready: boolean) {
    await wait(150)
    const g = this.groupOf(groupId)
    const p = this.party(groupId)
    const me = this.me?.id ?? 'me'
    const others = Date.now() - p.opened > 1500 ? g.members.filter((id) => id !== me).slice(0, 2) : []
    return { ready: [...(ready ? [me] : []), ...others], searching: p.searching && !p.matched, matched: p.matched }
  }

  async partySearch(groupId: string, _everywhere: boolean) {
    await wait(300)
    const g = this.groupOf(groupId)
    const p = this.party(groupId)
    if (p.matched) return p.matched
    if ((await this.partyState(groupId, true)).ready.length < 2) throw new BackendError('party_small')
    p.searching = true
    if (++p.polls < 3) return null
    const me = this.me?.id ?? 'me'
    const mine = [me, ...g.members.filter((id) => id !== me).slice(0, 2)]
    const theirs = PEOPLE.filter((x) => !mine.includes(x.id)).slice(0, 3).map((x) => x.id)
    const party = { id: `g_${++this.groupSeq}`, title: `Night owls × ${g.title}`.slice(0, 40), members: [...mine, ...theirs], lines: [] as GroupLine[], read: 0 }
    this.demoGroups().unshift(party)
    this.groupPost(party, { senderId: me, kind: 'created', body: [...mine, ...theirs].filter((id) => id !== me).map((id) => this.person(id)?.name ?? '').join(', ') })
    setTimeout(() => this.demoGroups().includes(party) && this.groupPost(party, { senderId: theirs[0], kind: 'text', body: 'hiii everyone 👋 where are you all from?' }), 2500)
    p.matched = party.id
    p.searching = false
    return party.id
  }

  async partyCancel(groupId: string) {
    await wait(150)
    this.party(groupId).searching = false
    this.party(groupId).polls = 0
  }

  async createGroup(title: string, memberIds: string[]) {
    await wait(300)
    const me = this.me?.id ?? 'me'
    const t = title.trim()
    if (!t || t.length > 40 || !memberIds.length) throw new BackendError('bad_request')
    const ok = memberIds.filter((id) => this.friendMap.get(id) === 'friends')
    if (!ok.length) throw new BackendError('not_friends')
    const g = { id: `g_${++this.groupSeq}`, title: t, members: [me, ...ok], lines: [] as GroupLine[], read: 0 }
    this.demoGroups().unshift(g)
    this.groupPost(g, { senderId: me, kind: 'created', body: ok.map((id) => this.person(id)?.name ?? '').join(', ') })
    return g.id
  }

  async groupHistory(groupId: string, before?: number) {
    await wait(200)
    const lines = this.groupOf(groupId).lines.filter((l) => before === undefined || l.id < before)
    return lines.slice(-50)
  }

  async sendGroup(groupId: string, text: string, clientId: string) {
    const g = this.groupOf(groupId)
    const body = text.trim()
    if (!body || body.length > 2000) throw new BackendError('bad_request')
    await wait(150)
    // As the server: offline it can't arrive; sent again under the same name it is stored once (092).
    if (!navigator.onLine) throw new BackendError('network')
    const sent = g.lines.find((l) => l.clientId === clientId)
    if (sent) return sent
    const line = this.groupPost(g, { senderId: this.me?.id ?? 'me', kind: 'text', body, clientId })
    const others = g.members.filter((id) => id !== this.me?.id)
    if (others.length) {
      setTimeout(() => {
        if (this.demoGroups().includes(g)) this.groupPost(g, { senderId: others[g.lines.length % others.length], kind: 'text', body: ['haha 😂', 'true!', 'I’m in', 'wait what', 'sounds good 👍'][g.lines.length % 5] })
      }, 2200)
    }
    return line
  }

  async readGroup(groupId: string, upto: number) {
    const g = this.groupOf(groupId)
    g.read = Math.max(g.read, upto)
  }

  async addToGroup(groupId: string, memberIds: string[]) {
    const g = this.groupOf(groupId)
    await wait(250)
    const add = memberIds.filter((id) => this.friendMap.get(id) === 'friends' && !g.members.includes(id))
    if (!add.length) return
    if (g.members.length + add.length > 50) throw new BackendError('room_full')
    g.members.push(...add)
    this.groupPost(g, { senderId: this.me?.id ?? 'me', kind: 'added', body: add.map((id) => this.person(id)?.name ?? '').join(', ') })
  }

  async renameGroup(groupId: string, title: string) {
    const g = this.groupOf(groupId)
    const t = title.trim()
    if (!t || t.length > 40) throw new BackendError('bad_request')
    await wait(200)
    if (t === g.title) return
    g.title = t
    this.groupPost(g, { senderId: this.me?.id ?? 'me', kind: 'renamed', body: t })
  }

  async leaveGroup(groupId: string) {
    const g = this.groupOf(groupId)
    await wait(200)
    g.members = g.members.filter((id) => id !== this.me?.id)
    if (!g.members.length) this.groupList = this.demoGroups().filter((x) => x !== g)
    else this.groupPost(g, { senderId: this.me?.id ?? 'me', kind: 'left', body: this.me?.name ?? '' })
  }

  watchGroups(groupIds: string[], onLine: (groupId: string, line: GroupLine) => void) {
    const f = (groupId: string, line: GroupLine) => groupIds.includes(groupId) && onLine(groupId, line)
    this.groupWatchers.add(f)
    return () => void this.groupWatchers.delete(f)
  }

  async setRoomTheme(roomId: string, itemId: string | null) {
    if (itemId && !(await (await this.lookSim()).shop()).owned.includes(itemId as never)) throw new BackendError('not_owned')
    return (await this.voice()).theme(roomId, itemId)
  }

  async sendVoicePhoto(roomId: string, photo: File) {
    if (!photo.type.startsWith('image/')) throw new BackendError('bad_media')
    return (await this.voice()).photo(roomId, URL.createObjectURL(photo))
  }

  async voicePollCreate(roomId: string, question: string, options: string[]) {
    return (await this.voice()).pollCreate(roomId, question, options)
  }

  async voicePollVote(pollId: number, choice: number) {
    return (await this.voice()).pollVote(pollId, choice)
  }

  async voicePollClose(pollId: number) {
    return (await this.voice()).pollClose(pollId)
  }

  async voicePolls(ids: number[]) {
    return (await this.voice()).polls(ids)
  }

  async voiceAction(roomId: string, action: VoiceAction) {
    return (await this.voice()).act(roomId, action)
  }

  /** The demo's people don't read typing signals. */
  voiceTyping(_roomId: string, _name: string) {}

  /** The demo reaches no other site: a card from the address alone (shown with "Open in browser"). */
  async linkMeta(url: string): Promise<LinkMeta> {
    await wait(300)
    let host = url
    try {
      host = new URL(url).hostname.replace(/^www\./, '')
    } catch {
      throw new BackendError('bad_request')
    }
    return { url, title: null, description: null, image: null, site: host, frameable: false }
  }

  async reportVoiceRoom(_roomId: string, _reason: ReportReason) {
    await wait(500)
  }

  /** The demo has no real audio, so nothing is ever blocked. */
  async voiceStartAudio(_roomId: string) {}

  async voiceSound(_roomId: string, _on: boolean) {}

  async hideVoiceLine(roomId: string, lineId: number) {
    return (await this.voice()).hideLine(roomId, lineId)
  }

  async clearVoiceChat(roomId: string) {
    return (await this.voice()).clearChat(roomId)
  }

  voiceVolume(_roomId: string, userId: string, volume: number) {
    saveVolume(userId, volume)
  }

  // ---- identity and manners (simulated in their own chunk, lib/mockTrust.ts) ----

  private trustSim() {
    this.trust_ ??= import('./mockTrust').then(
      (m) =>
        new m.TrustSim({
          flags: this.flags,
          me: () => this.me,
          verified: (id) => VERIFIED.has(id),
          setMe: (p) => (this.me = p),
          match: (id) => {
            const m = this.matches.get(id)
            return m ? { kind: m.kind, partnerId: m.partner.id, messages: m.messages } : null
          },
        }),
    )
    return this.trust_
  }

  async myTrust() {
    return (await this.trustSim()).myTrust()
  }

  async trust(userIds: string[]) {
    return (await this.trustSim()).trust(userIds)
  }

  async kycStart(_returnUrl: string) {
    this.requireMe()
    return (await this.trustSim()).kycStart()
  }

  async kycDemo(outcome: KycDemoOutcome) {
    return (await this.trustSim()).kycDemo(outcome)
  }

  async kycCheck() {
    return (await this.trustSim()).kycCheck()
  }

  async rateChat(matchId: string, good: boolean, tags: string[]) {
    return (await this.trustSim()).rateChat(matchId, good, tags)
  }

  // ---- IDs and looks (simulated in their own chunk, lib/mockLooks.ts) ----

  private lookSim() {
    this.looks_ ??= import('./mockLooks').then(
      (m) =>
        new m.LookSim({
          me: () => this.me,
          setMe: (p) => (this.me = p),
          handle: (id) => {
            const p = this.person(id) ?? this.voiceSim?.person(id)
            return p && !isVip(p) ? freeHandle(p.name, id) : null
          },
        }),
    )
    return this.looks_
  }

  async handleCheck(handle: string) {
    return (await this.lookSim()).handleCheck(handle)
  }

  async setHandle(handle: string) {
    return (await this.lookSim()).setHandle(handle)
  }

  async shop() {
    return (await this.lookSim()).shop()
  }

  async equipLook(wear: LookWear) {
    return (await this.lookSim()).equipLook(wear)
  }

  async setWall(itemId: string | null) {
    return (await this.lookSim()).setWall(itemId)
  }

  async setCycle(paths: string[] | null) {
    return (await this.lookSim()).setCycle(paths)
  }

  async setWorn(kind: 'bubble', itemId: string | null) {
    return (await this.lookSim()).setWorn(kind, itemId)
  }

  async setAnon(on: boolean) {
    return (await this.lookSim()).setAnon(on)
  }

  async looks(userIds: string[]) {
    return (await this.lookSim()).looks(userIds)
  }

  async buyLook(itemId: string) {
    return (await this.lookSim()).buy(itemId)
  }

  /** The demo's gender pass (none at first; ?demo_pass gives one). */
  private passUntil = typeof location !== 'undefined' && new URLSearchParams(location.search).has('demo_pass') ? Date.now() + DAY : 0

  async genderPass() {
    await wait(150)
    return this.passUntil > Date.now() ? this.passUntil : null
  }

  async buyGenderPass() {
    await wait(600)
    this.passUntil = Math.max(Date.now(), this.passUntil) + DAY
  }
}
