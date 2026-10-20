// Demo Ludo: the server's rules (lib/ludo.ts) played in the browser. Bots in the room join my games and play legal
// moves at a human pace; the "Ludo night" room always has a game on to watch. Loaded with the first Ludo call.
import { BackendError, type LudoAction, type LudoGame, type Profile } from './backend'
import { activeSeats, HOME, isSafe, legal, LudoEngine, square } from './ludo'
import type { VoiceSim } from './mockVoice'

type Full = LudoGame & { sixes: number; misses: number[]; openedBy: string }

const wait = (ms: number) => new Promise<void>((r) => setTimeout(r, ms))
const jitter = (min: number, max: number) => min + Math.random() * (max - min)
const uid = () => Math.random().toString(36).slice(2, 10)
const die = () => 1 + Math.floor(Math.random() * 6)

/** The game the "Ludo night" room is in the middle of when someone walks in. */
const MID_GAME = [14, 30, -1, -1, 5, -1, -1, -1, 56, 56, 52, 50, 11, 33, -1, -1]

interface Table {
  game: Full | null
  subs: Set<(g: LudoGame) => void>
  timers: Set<ReturnType<typeof setTimeout>>
  /** Bots about to take a colour in my lobby. */
  joiners: Profile[]
}

export class LudoSim {
  private tables = new Map<string, Table>()

  constructor(private voice: VoiceSim, private me: () => Profile | null) {}

  private table(roomId: string) {
    let t = this.tables.get(roomId)
    if (!t) {
      t = { game: null, subs: new Set(), timers: new Set(), joiners: [] }
      this.tables.set(roomId, t)
      if (roomId === 'v-ludo') this.botGame(roomId, t, MID_GAME)
    }
    return t
  }

  private fresh(roomId: string, openedBy: string): Full {
    return {
      id: `g-${uid()}`, roomId, status: 'lobby', players: [null, null, null, null], names: [null, null, null, null],
      out: [false, false, false, false], tokens: Array(16).fill(-1), turn: null, dice: null, roll: null, deadline: null,
      winner: null, events: [], version: 0, endedAt: null, sixes: 0, misses: [0, 0, 0, 0], openedBy,
    }
  }

  /** Four people on the stage of the room playing each other. */
  private botGame(roomId: string, t: Table, tokens?: number[]) {
    const room = this.voice.peek(roomId)
    if (!room) return
    const g = this.fresh(roomId, room.hostId)
    room.members.filter((m) => m.seat !== null).slice(0, 4).forEach((m, s) => {
      g.players[s] = m.profile.id
      g.names[s] = m.profile.name
    })
    const e = new LudoEngine(g)
    e.start()
    if (tokens) {
      g.tokens = [...tokens]
      g.turn = 2
    }
    g.events = []
    t.game = g
  }

  private snapshot(g: Full): LudoGame {
    return structuredClone(g)
  }

  private publish(t: Table, g: Full, events: LudoGame['events']) {
    g.events = events
    g.version++
    t.game = g
    const snap = this.snapshot(g)
    for (const on of t.subs) on(snap)
    this.schedule(t)
  }

  private later(t: Table, ms: number, fn: () => void) {
    const id = setTimeout(() => {
      t.timers.delete(id)
      if (t.subs.size) fn()
    }, ms)
    t.timers.add(id)
  }

  private clear(t: Table) {
    for (const id of t.timers) clearTimeout(id)
    t.timers.clear()
  }

  private isBot(id: string | null) {
    return !!id && id !== this.me()?.id
  }

  /** What happens next on its own: a bot's roll or move, the clock running out, a bot-only game starting over. */
  private schedule(t: Table) {
    this.clear(t)
    const g = t.game
    if (!g || !t.subs.size) return
    if (g.status === 'finished' && g.roomId === 'v-ludo') {
      this.later(t, 9000, () => {
        this.botGame(g.roomId, t)
        const n = t.game!
        n.version = g.version
        this.publish(t, n, [{ k: 'start', s: n.turn! }])
      })
      return
    }
    if (g.status === 'lobby' && t.joiners.length) {
      this.later(t, 900, () => {
        const p = t.joiners.shift()!
        const s = g.players.indexOf(null)
        if (s < 0 || g.players.includes(p.id) || !this.voice.peek(g.roomId)?.members.some((m) => m.profile.id === p.id)) return this.schedule(t)
        const n = structuredClone(g)
        ;[n.players[s], n.names[s]] = [p.id, p.name]
        this.publish(t, n, [])
      })
      return
    }
    if (g.status !== 'playing' || g.turn === null) return
    const who = g.players[g.turn]
    if (this.isBot(who)) {
      // Rolling takes a moment's thought; picking a token a little less.
      this.later(t, g.dice === null ? jitter(900, 1500) : jitter(700, 1100), () => this.step(t, (e) => {
        if (e.g.dice === null) e.roll(die(), false)
        else e.move(this.choose(e.g))
      }))
    } else if (g.deadline) {
      this.later(t, Math.max(0, g.deadline - Date.now()) + 400, () => this.step(t, (e) => e.timeout()))
    }
  }

  private step(t: Table, fn: (e: LudoEngine) => void) {
    if (!t.game) return
    const g = structuredClone(t.game)
    const e = new LudoEngine(g)
    fn(e)
    this.publish(t, g, e.events)
  }

  /** A bot's pick: a capture, then home, then out of the base, then a safe square, else the token furthest on. */
  private choose(g: LudoGame): number {
    const s = g.turn!
    const d = g.dice!
    const score = (k: number) => {
      const from = g.tokens[s * 4 + k]
      const to = from === -1 ? 0 : from + d
      const sq = square(s, to)
      let v = to / 10
      if (sq !== null && !isSafe(sq)) {
        for (let o = 0; o < 4; o++) {
          if (o === s || g.players[o] === null || g.out[o]) continue
          for (let j = 0; j < 4; j++) if (square(o, g.tokens[o * 4 + j]) === sq) v += 20
        }
      }
      if (to === HOME) v += 12
      if (from === -1) v += 8
      if (sq !== null && isSafe(sq)) v += 3
      return v + Math.random()
    }
    return legal(g, d).sort((a, b) => score(b) - score(a))[0]
  }

  // ---- the Backend methods ----

  async game(roomId: string): Promise<LudoGame | null> {
    await wait(200)
    const g = this.table(roomId).game
    return g && (g.status !== 'cancelled' && (g.status !== 'finished' || Date.now() - (g.endedAt ?? 0) < 300_000)) ? this.snapshot(g) : null
  }

  subscribe(roomId: string, on: (g: LudoGame) => void) {
    const t = this.table(roomId)
    t.subs.add(on)
    this.schedule(t)
    return () => {
      t.subs.delete(on)
      if (!t.subs.size) this.clear(t)
      // Leaving the room is a forfeit, as on the server.
      const me = this.me()?.id
      const g = t.game
      if (me && g && (g.status === 'lobby' || g.status === 'playing') && g.players.includes(me) && !this.voice.peek(roomId)?.mine) {
        this.step(t, (e) => e.forfeit(g.players.indexOf(me), 'left'))
      }
    }
  }

  async act(roomId: string, gameId: string | null, a: LudoAction): Promise<LudoGame | null> {
    const me = this.me()
    const room = this.voice.peek(roomId)
    if (!me || !room) throw new BackendError('not_allowed')
    const member = room.members.find((m) => m.profile.id === me.id)
    if (!member) throw new BackendError('not_allowed')
    const mod = member.role === 'host' || member.role === 'cohost'
    const t = this.table(roomId)
    await wait(a.type === 'roll' ? 250 : 150)
    let g = t.game
    const need = (ok: boolean, code = 'not_allowed') => {
      if (!ok) throw new BackendError(code)
    }

    if (a.type === 'open') {
      need(mod)
      if (g && (g.status === 'lobby' || g.status === 'playing')) return this.snapshot(g)
      const n = this.fresh(roomId, me.id)
      // Play again: whoever played keeps their colour.
      if (g?.status === 'finished') {
        for (let s = 0; s < 4; s++) if (room.members.some((m) => m.profile.id === g!.players[s])) {
          n.players[s] = g.players[s]
          n.names[s] = g.names[s]
        }
      }
      if (!n.players.includes(me.id)) {
        const s = n.players.indexOf(null)
        if (s >= 0) [n.players[s], n.names[s]] = [me.id, me.name]
      }
      n.version = (g?.version ?? 0) + 1
      t.game = n
      t.joiners = []
      this.publish(t, n, [])
      if (room.mine) this.botsJoin(t)
      return this.snapshot(n)
    }

    need(!!g && g.id === gameId, 'not_found')
    g = structuredClone(g!)
    const e = new LudoEngine(g)
    const seat = g.players.indexOf(me.id)
    switch (a.type) {
      case 'join': {
        need(g.status === 'lobby')
        const s = a.seat ?? g.players.indexOf(null)
        need(s >= 0, 'game_full')
        if (seat === s) return this.snapshot(g)
        need(g.players[s] === null, 'seat_taken')
        if (seat >= 0) [g.players[seat], g.names[seat]] = [null, null]
        ;[g.players[s], g.names[s]] = [me.id, me.name]
        break
      }
      case 'leave':
        if (seat >= 0) e.forfeit(seat, 'quit')
        break
      case 'start':
        need(mod && g.status === 'lobby')
        need(activeSeats(g).length >= 2, 'need_players')
        e.start()
        break
      case 'close':
        need(mod)
        g.status = 'cancelled'
        g.deadline = null
        g.endedAt = Date.now()
        break
      case 'roll':
      case 'move': {
        need(a.version === t.game!.version, 'stale')
        need(g.status === 'playing' && seat >= 0 && !g.out[seat])
        need(g.turn === seat, 'not_your_turn')
        g.misses[seat] = 0
        if (a.type === 'roll') {
          need(g.dice === null, 'stale')
          e.roll(die(), false)
        } else {
          need(g.dice !== null && legal(g, g.dice).includes(a.token))
          e.move(a.token)
        }
        break
      }
      case 'timeout':
        if (a.version !== t.game!.version || g.status !== 'playing' || (g.deadline ?? Infinity) > Date.now()) return this.snapshot(t.game!)
        e.timeout()
        break
    }
    this.publish(t, g, e.events)
    return this.snapshot(g)
  }

  /** In my room, people on the stage (then the audience) take the free colours, one after another. */
  private botsJoin(t: Table) {
    const room = this.voice.peek(t.game!.roomId)
    if (!room) return
    const me = this.me()?.id
    const people = [...room.members.filter((m) => m.seat !== null), ...room.members.filter((m) => m.seat === null)]
    t.joiners = people.filter((m) => m.profile.id !== me).slice(0, 3).map((m) => m.profile)
    this.schedule(t)
  }
}
