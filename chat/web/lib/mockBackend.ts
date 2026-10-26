import {
  type Backend,
  BackendError,
  type Badges,
  type InboxItem,
  type IncomingCall,
  type Interest,
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
} from './backend'
import { CONTACT_APPS, cleanContact, type ContactApp, type Contacts } from './contacts'
import { COUNTRIES } from './countries'
import { MANNER_FLOOR, MANNERS } from './trust'
import { isVip } from './looks'
import type { LookSim } from './mockLooks'
import type { TrustSim } from './mockTrust'
import type { VoiceSim } from './mockVoice'
import type { FeedSim } from './mockFeed'
import { droppedOut } from './voice-lost'

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


// Demo pictures are drawn here as small SVGs (no third-party image hosts; works offline and under the CSP).
const ART_COLORS = [
  ['#fde2e4', '#c8174a'], ['#e3f0ff', '#1a73e8'], ['#e6f4ea', '#188038'], ['#f3e8fd', '#8430ce'],
  ['#fef7e0', '#b06000'], ['#e4f7f8', '#007b83'],
]
function art(seed: string, w = 720, h = 720) {
  let n = 0
  for (const ch of seed) n = (n * 31 + ch.charCodeAt(0)) | 0
  const [bg, fg] = ART_COLORS[Math.abs(n) % ART_COLORS.length]
  const sun = 20 + (Math.abs(n >> 3) % 60)
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}"><rect width="${w}" height="${h}" fill="${bg}"/>` +
    `<circle cx="${(sun / 100) * w}" cy="${h * 0.3}" r="${w * 0.09}" fill="${fg}" opacity=".35"/>` +
    `<path d="M0 ${h * 0.75} L${w * 0.3} ${h * 0.5} L${w * 0.55} ${h * 0.7} L${w * 0.75} ${h * 0.55} L${w} ${h * 0.72} V${h} H0Z" fill="${fg}" opacity=".55"/>` +
    `<path d="M0 ${h * 0.88} L${w * 0.4} ${h * 0.68} L${w} ${h * 0.9} V${h} H0Z" fill="${fg}"/></svg>`
  return `data:image/svg+xml,${encodeURIComponent(svg)}`
}

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
      post('ananya1', 'Marine Drive at 6pm hits different', 2 * DAY),
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
      post('sofia4', 'Golden hour from Miradouro', 20 * DAY),
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
    posts: [post('amara1', 'Demo day nerves', 1 * DAY), post('amara2', 'Lekki sunset', 7 * DAY)],
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
    posts: [post('arjun1', 'Nandi Hills sunrise ride', 5 * DAY), post('arjun2', 'Open mic tonight', 12 * DAY)],
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
    posts: [post('riya1', 'Sketchbook page 42', 1 * DAY), post('riya2', 'Monsoon in the ghats', 9 * DAY)],
  },
  // 046: someone in VIP incognito, as the server shows them to a stranger.
  { id: 'u_vip', name: 'VIP', gender: 'female', age: 0, city: '', country: null, bio: '', photoUrl: null, posts: [] },
]

// Interests and a second photo for some demo people (024).
const EXTRAS: Record<string, { interests: Interest[]; photos?: number }> = {
  u_ananya: { interests: ['movies', 'food', 'travel'], photos: 2 },
  u_kabir: { interests: ['cricket', 'tech', 'art'] },
  u_sofia: { interests: ['travel', 'food', 'fitness', 'music'], photos: 3 },
  u_mateo: { interests: ['football', 'dance', 'music'] },
  u_yuki: { interests: ['photography', 'anime', 'food'], photos: 2 },
  u_amara: { interests: ['music', 'business', 'tech'] },
  u_lucas: { interests: ['tech', 'fitness', 'music'] },
  u_leila: { interests: ['books', 'travel', 'photography'] },
  u_arjun: { interests: ['music', 'tech', 'cricket'], photos: 1 },
  u_chloe: { interests: ['art', 'travel', 'fashion'] },
}
for (const p of PEOPLE) {
  const x = EXTRAS[p.id]
  if (!x) continue
  p.interests = x.interests
  p.photos = Array.from({ length: x.photos ?? 0 }, (_, i) => ({ id: `${p.id}_g${i}`, url: art(`${p.id}-gallery-${i}`) }))
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
  askedVideo: boolean
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
      this.matches.set(id, this.newMatchState(id, partner, 'thread', at, [
        { id: this.msgSeq++, fromMe: false, text: pick(GREETINGS), at: at },
        { id: this.msgSeq++, fromMe: true, text: 'hey! nice to meet you', at: at + 20_000 },
        { id: this.msgSeq++, fromMe: false, text: last, at: at + 60_000 },
      ]))
      this.recentList.push({ matchId: id, partner, metAt: at, lastText: last })
    }
    this.unread.set(this.recentList[0].matchId, 1) // Leila's last message is unread

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
      greeted: kind === 'thread', sentMedia: kind === 'thread', askedVideo: kind === 'thread', timers: [],
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
      interests: [],
      photos: [],
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
    const partner = pick(pool)
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
    return [...m.messages]
  }

  sendTyping() {}

  async markRead(matchId: string) {
    if (!this.unread.delete(matchId)) return
    for (const i of this.inboxItems) if (i.kind === 'message' && i.matchId === matchId) i.read = true
    this.inboxChanged()
  }

  async flagNudity() {}

  async send(matchId: string, text: string | null, media: File | null) {
    const m = this.matches.get(matchId)
    if (!m) throw new BackendError('not_found')
    await wait(jitter(120, 260))
    if (m.ended && this.friendMap.get(m.partner.id) !== 'friends') throw new BackendError('chat_closed')
    const msg: Message = {
      id: this.msgSeq++,
      fromMe: true,
      text: text?.trim() || null,
      media: media ? { url: URL.createObjectURL(media), kind: media.type.startsWith('video') ? 'video' : 'image' } : null,
      at: Date.now(),
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
    } else if (m.myCount === 3 && !m.askedVideo && m.video === 'none') {
      m.askedVideo = true
      this.partnerSays(m, 'can I call you? voice is fine')
      this.schedule(m, 3200, () => {
        if (m.video !== 'none') return
        m.callVideo = false
        this.setCall(m, 'requested_by_partner')
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
    if (m.ended && this.friendMap.get(m.partner.id) !== 'friends') throw new BackendError('not_allowed')
    return m
  }

  async requestCall(matchId: string, video: boolean) {
    const m = this.callable(matchId)
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

  /* ---------- social ---------- */

  async recents() {
    await wait(600)
    return this.recentList.filter((r) => (this.recentHidden.get(r.partner.id) ?? 0) <= r.metAt).sort((a, b) => b.metAt - a.metAt).slice(0, 50)
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

  async post(postId: string) {
    return (await this.feedChunk()).post(postId)
  }

  async likePost(postId: string, on: boolean) {
    return (await this.feedChunk()).like(postId, on)
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
    if (kind === 'recommend') list = [...list].sort(() => Math.random() - 0.5)
    if (kind === 'online') list = list.filter((_, i) => i % 2 === 0)
    // Rankings: a steady made-up count per person, highest first.
    const count = (id: string) => (kind === 'likes' || kind === 'friends' ? [...id].reduce((n, ch) => n + ch.charCodeAt(0), 0) % 97 : null)
    return list
      .map((p) => ({ ...this.personRow(p), age: p.age, country: p.country ?? null, count: count(p.id) }))
      .sort((a, b) => (b.count ?? 0) - (a.count ?? 0))
      .slice(0, 50)
  }

  async exploreFeed(): Promise<FeedItem[]> {
    await wait(300)
    return [...this.friendMap]
      .filter(([, s]) => s === 'friends')
      .flatMap(([id], i) => {
        const p = this.person(id)
        return p?.photoUrl ? [{ kind: 'post' as const, id: `f_${id}`, user: this.personRow(p), text: p.bio, photos: [p.photoUrl], at: new Date(Date.now() - i * 3_600_000).toISOString() }] : []
      })
  }

  async profileSocial(userId: string) {
    await wait(150)
    return { likes: (this.likedIds.has(userId) ? 1 : 0) + 12, friends: 8, liked: this.likedIds.has(userId), canMessage: userId !== this.me?.id }
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

  async friends() {
    await wait(550)
    const list: Friend[] = []
    for (const [id, state] of this.friendMap) {
      const profile = this.person(id) ?? this.voiceSim?.person(id)
      if (profile && state !== 'none') list.push({ profile, state, threadId: this.threadByUser.get(id) ?? null })
    }
    return list
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

  async friendThread(userId: string) {
    await wait(300)
    const existing = this.threadByUser.get(userId)
    if (existing) return existing
    const fromRecent = this.recentList.find((r) => r.partner.id === userId)
    if (fromRecent) {
      const m = this.matches.get(fromRecent.matchId)
      if (m) m.kind = 'thread'
      this.threadByUser.set(userId, fromRecent.matchId)
      return fromRecent.matchId
    }
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
    const countries = query.countries ?? []
    const mine = (r: VoiceRoomSummary) => Number(r.host.country === this.me?.country)
    // As voice_list4: mine first, then my country's rooms (a stable sort keeps the rest in order).
    return (await (await this.voice()).list())
      .filter(
        (r) =>
          (!text || r.title.toLocaleLowerCase().includes(text)) &&
          (!query.lang || r.lang === query.lang) &&
          (!countries.length || countries.includes(r.host.country ?? '')) &&
          (query.locked !== false || (r.refusal !== 'room_password' && r.refusal !== 'room_private')),
      )
      .sort((a, b) => Number(b.mine) - Number(a.mine) || mine(b) - mine(a))
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
      chats: Object.fromEntries(this.unread),
    }
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

  async setInterests(tags: Interest[]) {
    if (!this.me) throw new BackendError('not_found')
    await wait(400)
    this.me = { ...this.me, interests: [...new Set(tags)] }
    return { ...this.me }
  }

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

  async sendVoicePhoto(roomId: string, photo: File) {
    if (!photo.type.startsWith('image/')) throw new BackendError('bad_media')
    return (await this.voice()).photo(roomId, URL.createObjectURL(photo))
  }

  async voiceIcebreaker(roomId: string) {
    return (await this.voice()).icebreaker(roomId)
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

  async setWorn(kind: 'effect' | 'bubble', itemId: string | null) {
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
