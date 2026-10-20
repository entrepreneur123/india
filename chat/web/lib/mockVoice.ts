// Demo voice rooms: a few live rooms with people talking (audio levels), chat, reactions, raised hands and a
// shared YouTube track. Loaded by the demo backend the first time a voice room is listed or opened.
import {
  BackendError,
  type NewVoiceRoom,
  type Profile,
  type RoomCategory,
  type SpeakMode,
  VOICE_SEATS,
  type VoiceAction,
  type VoiceEvent,
  type VoiceLine,
  type VoiceMember,
  type VoiceQueueItem,
  type VoiceReaction,
  type VoiceRoom,
  type VoiceRoomSummary,
  type VoiceSystemEvent,
  type VoiceTrack,
} from './backend'

const wait = (ms: number) => new Promise<void>((r) => setTimeout(r, ms))
const jitter = (min: number, max: number) => min + Math.random() * (max - min)
const pick = <T,>(list: T[]): T => list[Math.floor(Math.random() * list.length)]
const uid = () => Math.random().toString(36).slice(2, 10)

const LEVEL_MS = 150

const NAMES = [
  'Priya', 'Rohan', 'Sneha', 'Vikram', 'Meera', 'Aditya', 'Kavya', 'Ishaan', 'Nisha', 'Rahul', 'Pooja', 'Siddharth',
  'Farhan', 'Zoya', 'Dev', 'Tanvi', 'Aarav', 'Diya', 'Kunal', 'Ritika', 'Manish', 'Shreya', 'Harsh', 'Anjali', 'Yash',
  'Simran', 'Varun', 'Neha', 'Aman', 'Ira', 'Karthik', 'Lakshmi', 'Arnab', 'Moumita', 'Gurpreet', 'Fatima', 'Joel',
  'Tenzin', 'Sana', 'Abhishek',
]
const CITIES = [
  'Mumbai', 'Delhi', 'Bengaluru', 'Pune', 'Kolkata', 'Jaipur', 'Lucknow', 'Hyderabad', 'Chennai', 'Indore', 'Patna',
  'Ahmedabad', 'Chandigarh', 'Kochi', 'Guwahati', 'Bhopal',
]
const BIOS = [
  'Chai, cricket and old Hindi songs.',
  'Engineer by day, bathroom singer by night.',
  'Here to practise English and make friends.',
  'Night owl. Arijit fan forever.',
  'Preparing for exams, one lo-fi playlist at a time.',
]

let personSeq = 0
/** A new demo person: the given name, or the next one from the list (with an initial once names repeat). */
function person(given?: string): Profile {
  const n = personSeq++
  const first = given ?? NAMES[n % NAMES.length]
  return {
    id: `v_${first.toLowerCase()}_${n}`,
    name: given || n < NAMES.length ? first : `${first} ${'ABCDGKMPRS'[n % 10]}.`,
    gender: n % 2 ? 'male' : 'female',
    age: 19 + (n * 7) % 14,
    city: CITIES[n % CITIES.length],
    country: 'IN',
    bio: BIOS[n % BIOS.length],
    photoUrl: null,
    posts: [],
  }
}

/** Real videos, so the player works in the demo; the titles stand in for what the server would store. */
const TRACKS: Record<string, { title: string; seconds: number }> = {
  BddP6PYo2gs: { title: 'Kesariya – Brahmāstra · Arijit Singh', seconds: 268 },
  Umqb9KENgmk: { title: 'Tum Hi Ho – Aashiqui 2 · Arijit Singh', seconds: 262 },
  '284Ov7ysmfA': { title: 'Channa Mereya – ADHM · Arijit Singh', seconds: 289 },
  VAdGW7QDJiU: { title: 'Chaleya – Jawan · Arijit Singh, Shilpa Rao', seconds: 200 },
  g0eO74UmRBs: { title: 'Kal Ho Naa Ho – Title Track · Sonu Nigam', seconds: 322 },
  lTRiuFIWV54: { title: '1 A.M Study Session · lofi hip hop', seconds: 0 }, // an hour long: the demo never moves on
}

const LINES: Partial<Record<RoomCategory, string[]>> & { any: string[] } = {
  music: [
    'Arijit ka koi bhi gaana, bas 🔥', 'next Kal Ho Naa Ho please 🙏', 'volume ekdum perfect hai', 'who added this? legend',
    'goosebumps every single time', 'late night + Arijit = therapy', 'Priya di please play Tum Hi Ho', 'this chorus 😭❤️',
  ],
  language: [
    'How do you say “I’m bored” in Hindi?', 'main thoda thoda English bolta hoon', 'is it “in the bus” or “on the bus”?',
    'practice makes perfect 💪', 'can someone correct my sentence?', 'मुझे English बोलने में डर लगता है', 'Meera ma’am explains so well',
  ],
  sports: [
    'Bumrah ka over lethal tha', 'Kohli 100 today, mark my words', 'Head ka wicket chahiye jaldi', 'DRS was clearly out!',
    'IND will chase this easily', 'what a catch by Jadeja 🔥', 'Gill looking in form today',
  ],
  study: [
    '45 min focus, 10 min break?', 'UPSC prep day 112 ✍️', 'anyone doing NEET this year?', 'this beat is so calm',
    'back after chai ☕', 'Pomodoro #3 done', 'library vibes at home',
  ],
  gaming: [
    'bas ek six chahiye yaar', 'green ko maaro!', 'Arnab bhai ka luck dekho', 'who is winning?', 'next game mujhe bhi lo',
    'safe square pe hai, bach gaya', 'yellow is so close to home', 'that capture was brutal',
  ],
  any: ['hello everyone 👋', 'nice room yaar', 'hi from {city}!', 'first time in a voice room 😄', 'mic quality is great'],
}

const REACTIONS: VoiceReaction[] = ['heart', 'laugh', 'fire', 'clap', 'wow']

interface SimRoom {
  room: Omit<VoiceRoom, 'meId'>
  lines: VoiceLine[]
  subs: Set<(e: VoiceEvent) => void>
  timers: ReturnType<typeof setTimeout>[]
  talkers: Set<string>
  levels: Record<string, number>
  ended: boolean
  mine: boolean
  /** Fake people who raised a hand while I host (so the queue doesn't grow forever). */
  handsRaised: number
}

export class VoiceSim {
  private rooms = new Map<string, SimRoom>()
  private lineSeq = 1
  private sendTimes: number[] = []

  constructor(private me: () => Profile | null) {
    this.seed('v-bolly', 'Late night Bollywood songs', 'music', 'approval', 'Priya', 5, 34, {
      playing: 'BddP6PYo2gs', ago: 74_000, queue: ['Umqb9KENgmk', '284Ov7ysmfA', 'VAdGW7QDJiU'],
    })
    this.seed('v-cricket', 'Cricket talk: IND vs AUS', 'sports', 'approval', 'Vikram', 7, 58)
    this.seed('v-english', 'Hindi–English practice', 'language', 'open', 'Meera', 4, 21)
    this.seed('v-lofi', 'Lo-fi study together', 'study', 'open', 'Kavya', 3, 17, { playing: 'lTRiuFIWV54', ago: 480_000, queue: [] })
    // A Ludo game is on here (mockLudo.ts), to watch.
    this.seed('v-ludo', 'Ludo night: come watch', 'gaming', 'open', 'Arnab', 4, 12)
  }

  /** Who is in a live room, for the demo Ludo game. */
  peek(roomId: string): { members: VoiceMember[]; hostId: string; mine: boolean } | null {
    const r = this.rooms.get(roomId)
    return r && !r.ended ? { members: r.room.members, hostId: r.room.hostId, mine: r.mine } : null
  }

  /** Profiles of demo people, for the profile sheet. */
  person(id: string): Profile | null {
    for (const r of this.rooms.values()) {
      const m = r.room.members.find((x) => x.profile.id === id)
      if (m) return m.profile
    }
    return null
  }

  private seed(
    id: string,
    title: string,
    category: RoomCategory,
    mode: SpeakMode,
    hostName: string,
    speakers: number,
    listeners: number,
    music?: { playing: string; ago: number; queue: string[] },
  ) {
    const people = [person(hostName), ...Array.from({ length: speakers + listeners - 1 }, () => person())]
    const members: VoiceMember[] = people.map((profile, i) => ({
      profile,
      role: i === 0 ? 'host' : i === 1 && speakers > 2 ? 'cohost' : i < speakers ? 'speaker' : 'listener',
      seat: i < speakers ? i : null,
      // A few speakers sit muted, as in any real room.
      muted: i >= speakers ? true : i > 1 && i % 3 === 0,
      hand: i >= speakers && i % 11 === 4,
    }))
    const host = people[0]
    const room: SimRoom['room'] = {
      id, title, category, mode, hostId: host.id, members,
      seats: Array.from({ length: VOICE_SEATS }, (_, index) => ({ index, locked: index === 8 && mode === 'approval' })),
      track: null,
      queue: [],
    }
    if (music) {
      room.track = this.track(music.playing, host.name, Date.now() - music.ago)
      room.queue = music.queue.map((v, i) => ({ id: uid(), videoId: v, title: TRACKS[v]?.title ?? '', addedBy: people[i + 1].name, pending: false }))
    }
    const lines: VoiceLine[] = []
    const now = Date.now()
    for (let i = 0; i < 7; i++) {
      const sender = pick(people)
      const at = now - (7 - i) * 45_000
      if (i === 2) lines.push({ id: this.lineSeq++, kind: 'system', event: 'joined', name: pick(people).name, at })
      lines.push({ id: this.lineSeq++, kind: 'text', sender, fromMe: false, text: this.chatLine(category, sender), at })
    }
    this.rooms.set(id, { room, lines, subs: new Set(), timers: [], talkers: new Set(), levels: {}, ended: false, mine: false, handsRaised: 0 })
  }

  private track(videoId: string, addedBy: string, startedAt = Date.now()): VoiceTrack {
    return { id: uid(), videoId, title: TRACKS[videoId]?.title ?? '', addedBy, startedAt, pausedAt: null }
  }

  private chatLine(category: RoomCategory, sender: Profile) {
    const list = Math.random() < 0.8 ? (LINES[category] ?? LINES.any) : LINES.any
    return pick(list).replace('{city}', sender.city)
  }

  private get(roomId: string) {
    const r = this.rooms.get(roomId)
    if (!r || r.ended) throw new BackendError('room_closed')
    return r
  }

  private meMember(r: SimRoom) {
    const me = this.me()
    return me ? r.room.members.find((m) => m.profile.id === me.id) ?? null : null
  }

  private snapshot(r: SimRoom): VoiceRoom {
    return structuredClone({ ...r.room, meId: this.me()?.id ?? '' })
  }

  private emit(r: SimRoom, e: VoiceEvent) {
    for (const on of r.subs) on(e)
  }

  private changed(r: SimRoom) {
    this.emit(r, { type: 'room', room: this.snapshot(r) })
  }

  private system(r: SimRoom, event: VoiceSystemEvent, name: string, extra?: { seat?: number; title?: string }) {
    const line: VoiceLine = { id: this.lineSeq++, kind: 'system', event, name, at: Date.now(), ...extra }
    r.lines.push(line)
    this.emit(r, { type: 'line', line })
  }

  private freeSeat(r: SimRoom, preferred?: number) {
    const taken = new Set(r.room.members.map((m) => m.seat))
    const ok = (i: number) => i > 0 && i < VOICE_SEATS && !taken.has(i) && !r.room.seats[i].locked
    if (preferred !== undefined && ok(preferred)) return preferred
    for (let i = 1; i < VOICE_SEATS; i++) if (ok(i)) return i
    return null
  }

  private seatMember(r: SimRoom, m: VoiceMember, seat: number) {
    m.seat = seat
    m.hand = false
    m.muted = true // everyone comes up muted and unmutes when ready (Clubhouse)
    if (m.role === 'listener') m.role = 'speaker'
    this.system(r, 'seat', m.profile.name, { seat })
  }

  async list(): Promise<VoiceRoomSummary[]> {
    await wait(450)
    const me = this.me()
    return [...this.rooms.values()]
      .filter((r) => !r.ended)
      .sort((a, b) => Number(b.mine) - Number(a.mine))
      .map(({ room, mine }) => {
        const host = room.members.find((m) => m.profile.id === room.hostId)!.profile
        const stage = room.members.filter((m) => m.seat !== null).sort((a, b) => a.seat! - b.seat!)
        return {
          id: room.id,
          title: room.title,
          category: room.category,
          host,
          speakers: stage.slice(0, 4).map((m) => m.profile),
          listeners: room.members.filter((m) => m.seat === null && m.profile.id !== me?.id).length,
          nowPlaying: room.track ? room.track.title || null : null,
          mine,
        }
      })
  }

  async create(n: NewVoiceRoom) {
    await wait(500)
    const me = this.me()
    if (!me) throw new BackendError('not_allowed')
    const title = n.title.trim()
    if (title.length < 2 || title.length > 40) throw new BackendError('room_title')
    for (const r of this.rooms.values()) if (r.mine) this.end(r) // one live room per host
    const id = `v-${uid()}`
    // A couple of people are already waiting to listen, so the host isn't alone on the first screenshot.
    const early = Array.from({ length: 3 }, () => person())
    const room: SimRoom['room'] = {
      id, title, category: n.category, mode: n.mode, hostId: me.id,
      members: [
        { profile: me, role: 'host', seat: 0, muted: false, hand: false },
        ...early.map((profile): VoiceMember => ({ profile, role: 'listener', seat: null, muted: true, hand: false })),
      ],
      seats: Array.from({ length: VOICE_SEATS }, (_, index) => ({ index, locked: false })),
      track: null,
      queue: [],
    }
    this.rooms.set(id, { room, lines: [], subs: new Set(), timers: [], talkers: new Set(), levels: {}, ended: false, mine: true, handsRaised: 0 })
    return id
  }

  async join(roomId: string) {
    await wait(350)
    const r = this.get(roomId)
    const me = this.me()
    if (!me) throw new BackendError('not_allowed')
    if (!this.meMember(r)) {
      r.room.members.push({ profile: me, role: 'listener', seat: null, muted: true, hand: false })
      this.system(r, 'joined', me.name)
    }
    return this.snapshot(r)
  }

  async leave(roomId: string) {
    const r = this.rooms.get(roomId)
    const me = this.me()
    if (!r || !me || r.ended) return
    await wait(100)
    if (r.mine) return // my own room stays live until I end it
    r.room.members = r.room.members.filter((m) => m.profile.id !== me.id)
    this.changed(r)
  }

  async endRoom(roomId: string) {
    const r = this.get(roomId)
    if (!r.mine) throw new BackendError('not_allowed')
    await wait(300)
    this.end(r)
  }

  private end(r: SimRoom) {
    r.ended = true
    this.emit(r, { type: 'ended' })
    this.stop(r)
    r.subs.clear()
  }

  async lines(roomId: string) {
    await wait(300)
    return [...this.get(roomId).lines]
  }

  async send(roomId: string, text: string): Promise<VoiceLine> {
    const r = this.get(roomId)
    const me = this.me()
    if (!me) throw new BackendError('not_allowed')
    const now = Date.now()
    this.sendTimes = this.sendTimes.filter((t) => now - t < 5000)
    if (this.sendTimes.length >= 5) throw new BackendError('slow_down')
    this.sendTimes.push(now)
    await wait(120)
    const line: VoiceLine = { id: this.lineSeq++, kind: 'text', sender: me, fromMe: true, text: text.trim(), at: now }
    r.lines.push(line)
    for (const on of r.subs) on({ type: 'line', line: { ...line } })
    return line
  }

  subscribe(roomId: string, on: (e: VoiceEvent) => void) {
    const r = this.rooms.get(roomId)
    if (!r || r.ended) return () => {}
    r.subs.add(on)
    if (r.subs.size === 1) this.start(r)
    return () => {
      r.subs.delete(on)
      if (r.subs.size === 0) this.stop(r)
    }
  }

  // ---- the simulation: runs only while someone is subscribed; every timer is cleared on the way out ----

  private every(r: SimRoom, min: number, max: number, fn: () => void) {
    const loop = () => {
      const id = setTimeout(() => {
        r.timers.splice(r.timers.indexOf(id), 1)
        if (r.ended || r.subs.size === 0) return
        fn()
        loop()
      }, jitter(min, max))
      r.timers.push(id)
    }
    loop()
  }

  private later(r: SimRoom, ms: number, fn: () => void) {
    const id = setTimeout(() => {
      r.timers.splice(r.timers.indexOf(id), 1)
      if (!r.ended && r.subs.size > 0) fn()
    }, ms)
    r.timers.push(id)
  }

  private stop(r: SimRoom) {
    for (const id of r.timers) clearTimeout(id)
    r.timers = []
    r.talkers.clear()
    r.levels = {}
  }

  private others(r: SimRoom) {
    const me = this.me()
    return r.room.members.filter((m) => m.profile.id !== me?.id)
  }

  private start(r: SimRoom) {
    // Who is talking: one or two unmuted people on the stage, changing every few seconds.
    const rotate = () => {
      const stage = this.others(r).filter((m) => m.seat !== null && !m.muted)
      r.talkers = new Set(
        stage.sort(() => Math.random() - 0.5).slice(0, Math.random() < 0.3 ? 2 : 1).map((m) => m.profile.id),
      )
      if (Math.random() < 0.15) r.talkers.clear() // a pause in the conversation
    }
    rotate()
    this.every(r, 1800, 4500, rotate)
    this.every(r, LEVEL_MS, LEVEL_MS, () => {
      const levels: Record<string, number> = {}
      for (const id of r.talkers) {
        // Speech is bursty: mostly mid-level with syllable peaks and short gaps.
        const prev = r.levels[id] ?? 0
        const next = Math.random() < 0.18 ? 0 : Math.min(1, Math.max(0.15, prev * 0.4 + jitter(0.25, 1) * 0.6))
        if (next > 0) levels[id] = Math.round(next * 100) / 100
      }
      r.levels = levels
      this.emit(r, { type: 'levels', levels })
    })
    this.every(r, 3500, 7500, () => {
      const sender = pick(this.others(r)).profile
      const line: VoiceLine = {
        id: this.lineSeq++, kind: 'text', sender, fromMe: false, text: this.chatLine(r.room.category, sender), at: Date.now(),
      }
      r.lines.push(line)
      this.emit(r, { type: 'line', line })
    })
    this.every(r, 2500, 6000, () => {
      const from = Math.random() < 0.6 ? this.others(r).filter((m) => m.seat !== null) : this.others(r)
      if (from.length) this.emit(r, { type: 'reaction', userId: pick(from).profile.id, kind: pick(REACTIONS) })
    })
    // People come and go.
    this.every(r, 9000, 16000, () => {
      const audience = this.others(r).filter((m) => m.seat === null)
      if (Math.random() < 0.25 && audience.length > 4) {
        const gone = pick(audience)
        r.room.members = r.room.members.filter((m) => m !== gone)
        this.system(r, 'left', gone.profile.name)
      } else {
        const p = person()
        r.room.members.push({ profile: p, role: 'listener', seat: null, muted: true, hand: false })
        this.system(r, 'joined', p.name)
      }
      this.changed(r)
    })
    // Open rooms: now and then someone takes a free seat.
    if (r.room.mode === 'open' && !r.mine) {
      this.every(r, 14000, 24000, () => {
        const seat = this.freeSeat(r)
        const audience = this.others(r).filter((m) => m.seat === null)
        if (seat === null || !audience.length) return
        const m = pick(audience)
        this.seatMember(r, m, seat)
        m.muted = false
        this.changed(r)
      })
    }
    // Hosting: hands go up in the audience, so the requests queue has something in it.
    if (r.mine) {
      const raise = () => {
        const audience = this.others(r).filter((m) => m.seat === null && !m.hand)
        if (!audience.length || r.handsRaised >= 4) return
        pick(audience).hand = true
        r.handsRaised++
        this.changed(r)
      }
      this.later(r, 4000, raise)
      this.later(r, 6500, raise)
      this.every(r, 18000, 30000, raise)
    }
    // A host that isn't me plays the queue on: the next track starts when this one would have ended.
    if (!r.mine) {
      this.every(r, 4000, 4000, () => {
        const t = r.room.track
        const seconds = t ? TRACKS[t.videoId]?.seconds : 0
        if (t && seconds && !t.pausedAt && Date.now() - t.startedAt > seconds * 1000) this.skip(r)
      })
    }
  }

  private skip(r: SimRoom) {
    const i = r.room.queue.findIndex((q) => !q.pending)
    if (i < 0) {
      r.room.track = null
    } else {
      const [next] = r.room.queue.splice(i, 1)
      r.room.track = { ...this.track(next.videoId, next.addedBy), title: next.title }
      this.system(r, 'track', next.addedBy, { title: next.title })
    }
    this.changed(r)
  }

  // ---- actions ----

  async act(roomId: string, a: VoiceAction): Promise<void> {
    const r = this.get(roomId)
    const me = this.meMember(r)
    if (!me) throw new BackendError('not_allowed')
    await wait(a.type === 'react' ? 0 : 150)
    const isHost = me.role === 'host'
    const isMod = isHost || me.role === 'cohost'
    const target = 'userId' in a ? r.room.members.find((m) => m.profile.id === a.userId) : undefined
    const need = (ok: boolean) => {
      if (!ok) throw new BackendError('not_allowed')
    }

    switch (a.type) {
      case 'hand': {
        need(me.seat === null)
        me.hand = a.up
        // Someone else's room: the host brings me up after a few seconds.
        if (a.up && !r.mine) {
          this.later(r, jitter(3500, 6000), () => {
            const seat = this.freeSeat(r)
            if (!me.hand || me.seat !== null || seat === null) return
            this.seatMember(r, me, seat)
            this.changed(r)
          })
        }
        break
      }
      case 'mic':
        need(me.seat !== null)
        me.muted = !a.on
        break
      case 'seat': {
        if (me.seat !== null) {
          need(!isHost && this.freeSeat(r, a.seat) === a.seat)
          me.seat = a.seat // moving to another seat on the stage
        } else if (r.room.mode === 'open' || isMod) {
          need(this.freeSeat(r, a.seat) === a.seat)
          this.seatMember(r, me, a.seat)
        } else {
          return this.act(roomId, { type: 'hand', up: true })
        }
        break
      }
      case 'unseat':
        need(me.seat !== null && !isHost)
        me.seat = null
        if (me.role === 'speaker') me.role = 'listener'
        this.system(r, 'audience', me.profile.name)
        break
      case 'react':
        this.emit(r, { type: 'reaction', userId: me.profile.id, kind: a.kind })
        return
      case 'invite': {
        need(isMod && !!target && target.seat === null)
        const seat = this.freeSeat(r, a.seat)
        need(seat !== null)
        // The invited person says yes a moment later.
        this.later(r, 1200, () => {
          const s = this.freeSeat(r, a.seat)
          if (target!.seat !== null || s === null || !r.room.members.includes(target!)) return
          this.seatMember(r, target!, s)
          this.later(r, 1500, () => {
            target!.muted = false
            this.changed(r)
          })
          this.changed(r)
        })
        break
      }
      case 'answer': {
        need(isMod && !!target)
        if (a.accept) {
          const seat = this.freeSeat(r)
          need(seat !== null)
          this.seatMember(r, target!, seat!)
          this.later(r, 1500, () => {
            target!.muted = false
            this.changed(r)
          })
        } else {
          target!.hand = false
        }
        break
      }
      case 'toAudience':
        need(isMod && !!target && target.seat !== null && target.role !== 'host')
        target!.seat = null
        if (target!.role === 'speaker') target!.role = 'listener'
        this.system(r, 'audience', target!.profile.name)
        break
      case 'mute':
        need(isMod && !!target && target.role !== 'host')
        target!.muted = true
        break
      case 'lock':
        need(isMod && a.seat > 0 && a.seat < VOICE_SEATS && !r.room.members.some((m) => m.seat === a.seat))
        r.room.seats[a.seat].locked = a.locked
        break
      case 'cohost':
        need(isHost && !!target && target.role !== 'host')
        target!.role = a.on ? 'cohost' : target!.seat === null ? 'listener' : 'speaker'
        if (a.on) this.system(r, 'cohost', target!.profile.name)
        break
      case 'kick':
        need(isHost && !!target && target.role !== 'host')
        r.room.members = r.room.members.filter((m) => m !== target)
        this.system(r, 'removed', target!.profile.name)
        break
      case 'addTrack': {
        need(isHost)
        const title = TRACKS[a.videoId]?.title ?? ''
        if (!r.room.track) {
          r.room.track = this.track(a.videoId, me.profile.name)
          this.system(r, 'track', me.profile.name, { title })
        } else {
          r.room.queue.push({ id: uid(), videoId: a.videoId, title, addedBy: me.profile.name, pending: false })
        }
        break
      }
      case 'suggest': {
        const item: VoiceQueueItem = { id: uid(), videoId: a.videoId, title: TRACKS[a.videoId]?.title ?? '', addedBy: me.profile.name, pending: true }
        r.room.queue.push(item)
        // The demo host likes every suggestion.
        this.later(r, 3000, () => {
          item.pending = false
          this.changed(r)
        })
        break
      }
      case 'approve': {
        need(isHost)
        const i = r.room.queue.findIndex((q) => q.id === a.itemId)
        need(i >= 0)
        if (a.accept) r.room.queue[i].pending = false
        else r.room.queue.splice(i, 1)
        break
      }
      case 'play': {
        need(isHost)
        const i = r.room.queue.findIndex((q) => q.id === a.itemId)
        need(i >= 0)
        const [item] = r.room.queue.splice(i, 1)
        r.room.track = { ...this.track(item.videoId, item.addedBy), title: item.title }
        this.system(r, 'track', me.profile.name, { title: item.title })
        break
      }
      case 'removeItem':
        need(isHost)
        r.room.queue = r.room.queue.filter((q) => q.id !== a.itemId)
        break
      case 'pause':
        need(isHost && !!r.room.track && !r.room.track.pausedAt)
        r.room.track!.pausedAt = Date.now()
        break
      case 'resume': {
        need(isHost && !!r.room.track?.pausedAt)
        const t = r.room.track!
        t.startedAt += Date.now() - t.pausedAt!
        t.pausedAt = null
        break
      }
      case 'skip':
        need(isHost)
        if (a.trackId && r.room.track?.id !== a.trackId) return // that track already moved on
        this.skip(r)
        return
    }
    this.changed(r)
  }
}
