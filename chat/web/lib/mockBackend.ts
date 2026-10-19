import {
  type Backend,
  BackendError,
  type Entitlements,
  type Friend,
  type FriendState,
  type Match,
  type MatchEvent,
  type Message,
  type NewProfile,
  type Post,
  type Profile,
  type RecentMatch,
  type ReportReason,
  type Room,
  type RoomMessage,
  type Target,
  type VideoStatus,
} from './backend'

const wait = (ms: number) => new Promise<void>((r) => setTimeout(r, ms))
const jitter = (min: number, max: number) => min + Math.random() * (max - min)
const pick = <T,>(list: T[]): T => list[Math.floor(Math.random() * list.length)]
const uid = () => Math.random().toString(36).slice(2, 10)
const HOUR = 3_600_000
const DAY = 24 * HOUR

const FREE_EXIT_LOCK_MS = 5000
const PLUS_EXIT_LOCK_MS = 200

function post(seed: string, text: string, ageMs: number, withImage = true): Post {
  return {
    id: `p_${seed}`,
    text,
    imageUrl: withImage ? `https://picsum.photos/seed/yaari-${seed}/720/720` : null,
    createdAt: Date.now() - ageMs,
  }
}

const PEOPLE: Profile[] = [
  {
    id: 'u_ananya', name: 'Ananya', gender: 'female', age: 23, city: 'Mumbai', country: 'IN',
    bio: 'Chai over coffee, always. Learning Korean one drama at a time.',
    photoUrl: 'https://randomuser.me/api/portraits/women/44.jpg',
    posts: [
      post('ananya1', 'Marine Drive at 6pm hits different', 2 * DAY),
      post('ananya2', 'Finally finished my first pottery class', 6 * DAY),
      post('ananya3', 'Sunday market haul', 11 * DAY),
    ],
  },
  {
    id: 'u_kabir', name: 'Kabir', gender: 'male', age: 25, city: 'Delhi', country: 'IN',
    bio: 'Product designer. Weekend cricket, weekday overthinking.',
    photoUrl: 'https://randomuser.me/api/portraits/men/32.jpg',
    posts: [post('kabir1', 'New desk setup, finally', 3 * DAY), post('kabir2', 'Old Delhi food walk', 9 * DAY)],
  },
  {
    id: 'u_sofia', name: 'Sofia', gender: 'female', age: 24, city: 'Lisbon', country: 'PT',
    bio: 'Surfing badly, cooking well. Ask me about pastel de nata.',
    photoUrl: 'https://randomuser.me/api/portraits/women/68.jpg',
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
    photoUrl: 'https://randomuser.me/api/portraits/men/75.jpg',
    posts: [post('mateo1', 'Asado with the crew', 4 * DAY)],
  },
  {
    id: 'u_yuki', name: 'Yuki', gender: 'female', age: 22, city: 'Osaka', country: 'JP',
    bio: 'Film photography and late-night ramen runs.',
    photoUrl: 'https://randomuser.me/api/portraits/women/90.jpg',
    posts: [
      post('yuki1', 'Shot on Portra 400', 2 * DAY),
      post('yuki2', 'Dotonbori lights', 8 * DAY),
      post('yuki3', 'Rainy day, warm tea', 16 * DAY),
    ],
  },
  {
    id: 'u_amara', name: 'Amara', gender: 'female', age: 26, city: 'Lagos', country: 'NG',
    bio: 'Afrobeats playlist curator. Building a fintech startup.',
    photoUrl: 'https://randomuser.me/api/portraits/women/21.jpg',
    posts: [post('amara1', 'Demo day nerves', 1 * DAY), post('amara2', 'Lekki sunset', 7 * DAY)],
  },
  {
    id: 'u_lucas', name: 'Lucas', gender: 'male', age: 24, city: 'São Paulo', country: 'BR',
    bio: 'Skater, coder, açaí enthusiast.',
    photoUrl: 'https://randomuser.me/api/portraits/men/46.jpg',
    posts: [post('lucas1', 'Ibirapuera session', 3 * DAY)],
  },
  {
    id: 'u_leila', name: 'Leila', gender: 'female', age: 25, city: 'Istanbul', country: 'TR',
    bio: 'Two continents, one ferry ride. Bookworm.',
    photoUrl: 'https://randomuser.me/api/portraits/women/12.jpg',
    posts: [post('leila1', 'Bosphorus by ferry', 2 * DAY), post('leila2', 'Current read', 10 * DAY, false)],
  },
  {
    id: 'u_arjun', name: 'Arjun', gender: 'male', age: 28, city: 'Bengaluru', country: 'IN',
    bio: 'Backend engineer by day, guitarist by night.',
    photoUrl: 'https://randomuser.me/api/portraits/men/22.jpg',
    posts: [post('arjun1', 'Nandi Hills sunrise ride', 5 * DAY), post('arjun2', 'Open mic tonight', 12 * DAY)],
  },
  {
    id: 'u_chloe', name: 'Chloé', gender: 'female', age: 23, city: 'Lyon', country: 'FR',
    bio: 'Architecture student. I will judge your city’s skyline.',
    photoUrl: 'https://randomuser.me/api/portraits/women/33.jpg',
    posts: [post('chloe1', 'Vieux Lyon rooftops', 4 * DAY)],
  },
  {
    id: 'u_omar', name: 'Omar', gender: 'male', age: 26, city: 'Dubai', country: 'AE',
    bio: 'Desert drives and good playlists.',
    photoUrl: 'https://randomuser.me/api/portraits/men/61.jpg',
    posts: [post('omar1', 'Hatta weekend', 6 * DAY)],
  },
  {
    id: 'u_riya', name: 'Riya', gender: 'other', age: 21, city: 'Pune', country: 'IN',
    bio: 'Illustrator. Cats > people (you might change my mind).',
    photoUrl: 'https://randomuser.me/api/portraits/women/58.jpg',
    posts: [post('riya1', 'Sketchbook page 42', 1 * DAY), post('riya2', 'Monsoon in the ghats', 9 * DAY)],
  },
]

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

const ROOM_LINES = [
  'good morning from {city}!',
  'anyone here into anime?',
  'what’s everyone listening to?',
  'first time here, hi all 👋',
  'it’s 2am here and I can’t sleep',
  'who wants to play skribbl later?',
  'the weather in {city} is wild today',
  'recommend me a show to binge',
  'hello hello',
  'this room is so chill',
]

interface MatchState {
  id: string
  partner: Profile
  startedAt: number
  messages: Message[]
  listeners: Set<(e: MatchEvent) => void>
  video: VideoStatus
  kind: 'random' | 'thread'
  myCount: number
  greeted: boolean
  sentMedia: boolean
  askedVideo: boolean
  timers: ReturnType<typeof setTimeout>[]
}

interface RoomState {
  room: Room
  messages: RoomMessage[]
  listeners: Set<(m: RoomMessage) => void>
  ticker: ReturnType<typeof setInterval> | null
}

export class MockBackend implements Backend {
  private signedIn = false
  private pendingPhone: string | null = null
  private me: Profile | null = null
  private ent: Entitlements = { premium: false, genderPass: false }
  private searchPolls = 0
  private searchTarget: Target | null = null
  private matches = new Map<string, MatchState>()
  private recentList: RecentMatch[] = []
  private friendMap = new Map<string, FriendState>()
  private threadByUser = new Map<string, string>()
  private rooms_ = new Map<string, RoomState>()
  private msgSeq = 1
  private roomSendTimes: number[] = []

  constructor() {
    this.friendMap.set('u_sofia', 'friends')
    this.friendMap.set('u_arjun', 'friends')
    this.friendMap.set('u_yuki', 'friends')
    this.friendMap.set('u_amara', 'incoming')
    this.friendMap.set('u_lucas', 'incoming')
    this.friendMap.set('u_chloe', 'requested')

    const seedRecents: [string, number, string][] = [
      ['u_leila', 3 * HOUR, 'have a great night!'],
      ['u_mateo', 20 * HOUR, 'haha Messi forever'],
      ['u_kabir', 2 * DAY, 'catch you later'],
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

    const roomDefs: Room[] = [
      { id: 'lounge', title: 'Global Lounge', emoji: '🌍', online: 166 },
      { id: 'language', title: 'Language Exchange', emoji: '🗣️', online: 60 },
      { id: 'music', title: 'Music', emoji: '🎧', online: 9 },
      { id: 'movies', title: 'Movies & Series', emoji: '🎬', online: 55 },
      { id: 'gaming', title: 'Gaming', emoji: '🎮', online: 23 },
      { id: 'sports', title: 'Sports', emoji: '⚽', online: 19 },
      { id: 'night', title: 'Late Night Talks', emoji: '🌙', online: 32 },
      { id: 'memes', title: 'Memes', emoji: '😂', online: 14 },
      { id: 'study', title: 'Study & Campus', emoji: '🎓', online: 11 },
      { id: 'travel', title: 'Travel', emoji: '✈️', online: 8 },
    ]
    for (const room of roomDefs) {
      const msgs: RoomMessage[] = Array.from({ length: 6 }, (_, i) => {
        const sender = pick(PEOPLE)
        return {
          id: this.msgSeq++,
          sender,
          fromMe: false,
          text: pick(ROOM_LINES).replace('{city}', sender.city),
          at: Date.now() - (6 - i) * 90_000,
        }
      })
      this.rooms_.set(room.id, { room, messages: msgs, listeners: new Set(), ticker: null })
    }
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
      id, partner, startedAt, messages, kind,
      listeners: new Set(), video: 'none', myCount: 0,
      greeted: kind === 'thread', sentMedia: kind === 'thread', askedVideo: kind === 'thread', timers: [],
    }
  }

  private requireMe() {
    if (!this.signedIn) throw new BackendError('not_allowed')
  }

  private emit(m: MatchState, e: MatchEvent) {
    for (const l of m.listeners) l(e)
  }

  private schedule(m: MatchState, ms: number, fn: () => void) {
    m.timers.push(setTimeout(fn, ms))
  }

  private partnerSays(m: MatchState, text: string | null, media?: Message['media'], delay = jitter(900, 1800)) {
    this.schedule(m, 350, () => this.emit(m, { type: 'typing', on: true }))
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
    return this.me ? { ...this.me, posts: [...this.me.posts] } : null
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
    this.me = {
      id: 'u_me',
      name: p.name.trim(),
      gender: p.gender,
      age,
      city: p.city.trim(),
      bio: p.bio.trim(),
      photoUrl: p.photo ? URL.createObjectURL(p.photo) : null,
      country: this.pendingPhone?.startsWith('91') ? 'IN' : null,
      posts: [],
    }
    return { ...this.me }
  }

  async entitlements() {
    await wait(200)
    return { ...this.ent }
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

  async findMatch(target: Target) {
    this.requireMe()
    if (target !== 'any' && !this.ent.premium && !this.ent.genderPass) throw new BackendError('premium_required')
    await wait(jitter(300, 700))
    if (this.searchTarget !== target) {
      this.searchTarget = target
      this.searchPolls = 0
    }
    this.searchPolls++
    if (this.searchPolls < 2 + Math.floor(Math.random() * 2)) return null
    this.searchPolls = 0
    this.searchTarget = null
    const pool = PEOPLE.filter((p) => target === 'any' || p.gender === target)
    const partner = pick(pool)
    const id = `m_${uid()}`
    this.matches.set(id, this.newMatchState(id, partner, 'random'))
    return { id, partner } satisfies Match
  }

  async myPhone() {
    return this.pendingPhone ? `+${this.pendingPhone}` : null
  }

  abandon(matchId: string) {
    setTimeout(() => void this.leave(matchId).catch(() => undefined), FREE_EXIT_LOCK_MS + 500)
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

  async send(matchId: string, text: string | null, media: File | null) {
    const m = this.matches.get(matchId)
    if (!m) throw new BackendError('not_found')
    await wait(jitter(120, 260))
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
        url: `https://picsum.photos/seed/yaari-view-${m.partner.id}/800/1000`,
        kind: 'image',
      })
    } else if (m.myCount === 3 && !m.askedVideo && m.video === 'none') {
      m.askedVideo = true
      this.partnerSays(m, 'wanna switch to video?')
      this.schedule(m, 3200, () => {
        if (m.video !== 'none') return
        m.video = 'requested_by_partner'
        this.emit(m, { type: 'video', status: m.video })
      })
    } else {
      this.partnerSays(m, this.replyFor(msg.text ?? '', m.partner))
    }
    return msg
  }

  async leave(matchId: string) {
    const m = this.matches.get(matchId)
    if (!m) throw new BackendError('not_found')
    const lock = this.ent.premium ? PLUS_EXIT_LOCK_MS : FREE_EXIT_LOCK_MS
    if (m.kind === 'random' && Date.now() - m.startedAt < lock) throw new BackendError('exit_locked')
    await wait(200)
    for (const t of m.timers) clearTimeout(t)
    m.timers = []
    m.listeners.clear()
  }

  async report(_partnerId: string, _reason: ReportReason, _matchId: string | null) {
    await wait(500)
  }

  async requestVideo(matchId: string) {
    const m = this.matches.get(matchId)
    if (!m) throw new BackendError('not_found')
    if (!this.ent.premium) throw new BackendError('premium_required')
    await wait(300)
    m.video = 'requested_by_me'
    this.emit(m, { type: 'video', status: m.video })
    this.schedule(m, 2400, () => {
      m.video = 'accepted'
      this.emit(m, { type: 'video', status: m.video })
    })
  }

  async answerVideo(matchId: string, accept: boolean) {
    const m = this.matches.get(matchId)
    if (!m) throw new BackendError('not_found')
    await wait(300)
    m.video = accept ? 'accepted' : 'declined'
    this.emit(m, { type: 'video', status: m.video })
    if (!accept) m.video = 'none'
  }

  async endVideo(matchId: string) {
    const m = this.matches.get(matchId)
    if (!m) return
    await wait(200)
    m.video = 'none'
    this.emit(m, { type: 'video', status: 'none' })
  }

  async videoToken(_matchId: string) {
    return null // the demo has no video server; the call screen shows a preview
  }

  /* ---------- social ---------- */

  async recents() {
    await wait(600)
    return [...this.recentList].sort((a, b) => b.metAt - a.metAt)
  }

  async profile(userId: string) {
    await wait(450)
    if (this.me && userId === this.me.id) return { ...this.me }
    return this.person(userId)
  }

  async addPost(text: string, image: File | null) {
    this.requireMe()
    if (!this.me) throw new BackendError('not_found')
    await wait(700)
    const p: Post = {
      id: `p_${uid()}`,
      text: text.trim(),
      imageUrl: image ? URL.createObjectURL(image) : null,
      createdAt: Date.now(),
    }
    this.me.posts = [p, ...this.me.posts]
    return p
  }

  async updateProfile(name: string, city: string, bio: string, photo: File | null) {
    if (!this.me) throw new BackendError('not_found')
    await wait(700)
    this.me = {
      ...this.me,
      name: name.trim(),
      city: city.trim(),
      bio: bio.trim(),
      photoUrl: photo ? URL.createObjectURL(photo) : this.me.photoUrl,
    }
    return { ...this.me }
  }

  async friends() {
    await wait(550)
    const list: Friend[] = []
    for (const [id, state] of this.friendMap) {
      const profile = this.person(id)
      if (profile && state !== 'none') list.push({ profile, state, threadId: this.threadByUser.get(id) ?? null })
    }
    return list
  }

  async requestFriend(userId: string) {
    await wait(400)
    const current = this.friendMap.get(userId) ?? 'none'
    const next: FriendState = current === 'incoming' ? 'friends' : current === 'friends' ? 'friends' : 'requested'
    this.friendMap.set(userId, next)
    return next
  }

  async answerFriend(userId: string, accept: boolean) {
    await wait(400)
    if (accept) this.friendMap.set(userId, 'friends')
    else this.friendMap.delete(userId)
  }

  async removeFriend(userId: string) {
    await wait(400)
    this.friendMap.delete(userId)
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

  /* ---------- rooms ---------- */

  async rooms() {
    await wait(600)
    return [...this.rooms_.values()].map((r) => ({
      ...r.room,
      online: Math.max(0, r.room.online + Math.floor(jitter(-4, 4))),
    }))
  }

  async joinRoom(roomId: string) {
    const r = this.rooms_.get(roomId)
    if (!r) throw new BackendError('not_found')
    await wait(200)
  }

  async leaveRoom(roomId: string) {
    const r = this.rooms_.get(roomId)
    if (!r) return
    await wait(100)
  }

  async roomHistory(roomId: string) {
    await wait(500)
    const r = this.rooms_.get(roomId)
    if (!r) throw new BackendError('not_found')
    return [...r.messages]
  }

  subscribeRoom(roomId: string, on: (m: RoomMessage) => void) {
    const r = this.rooms_.get(roomId)
    if (!r) return () => {}
    r.listeners.add(on)
    if (!r.ticker) {
      r.ticker = setInterval(() => {
        const sender = pick(PEOPLE)
        const msg: RoomMessage = {
          id: this.msgSeq++,
          sender,
          fromMe: false,
          text: pick(ROOM_LINES).replace('{city}', sender.city),
          at: Date.now(),
        }
        r.messages.push(msg)
        for (const l of r.listeners) l(msg)
      }, jitter(3500, 6000))
    }
    return () => {
      r.listeners.delete(on)
      if (r.listeners.size === 0 && r.ticker) {
        clearInterval(r.ticker)
        r.ticker = null
      }
    }
  }

  async sendRoom(roomId: string, text: string) {
    const r = this.rooms_.get(roomId)
    if (!r || !this.me) throw new BackendError('not_found')
    const now = Date.now()
    this.roomSendTimes = this.roomSendTimes.filter((t) => now - t < 5000)
    if (this.roomSendTimes.length >= 5) throw new BackendError('slow_down')
    this.roomSendTimes.push(now)
    await wait(150)
    const msg: RoomMessage = { id: this.msgSeq++, sender: this.me, fromMe: true, text: text.trim(), at: now }
    r.messages.push(msg)
    return msg
  }
}
