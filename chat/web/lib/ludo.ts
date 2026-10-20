// Ludo rules and board geometry, the same as the server's (chat/server/migrations/018_ludo.sql): the screens use
// them for legal-token hints and the hop-by-hop paths, the demo backend to play whole games.
import type { LudoEvent, LudoGame } from './backend'

export const COLOURS = ['red', 'green', 'yellow', 'blue'] as const
export type LudoColour = (typeof COLOURS)[number]

export const HOME = 56
export const TURN_MS = 15_000

/** The shared track square of a token at position `p` of `seat`, or null in the base, home column or home. */
export const square = (seat: number, p: number) => (p >= 0 && p <= 50 ? (seat * 13 + p) % 52 : null)
/** The 4 start squares and the 4 stars 8 squares on. */
export const isSafe = (sq: number) => sq % 13 === 0 || sq % 13 === 8

// ---- geometry: a 15 x 15 grid, red's base bottom-left, play goes clockwise ----

type Pt = [number, number]

/** The 52 track cells (column, row), from red's start square. */
export const TRACK: Pt[] = (() => {
  const t: Pt[] = []
  const run = (from: Pt, dx: number, dy: number, n: number) => {
    for (let i = 0; i < n; i++) t.push([from[0] + dx * i, from[1] + dy * i])
  }
  run([6, 13], 0, -1, 5)
  run([5, 8], -1, 0, 6)
  run([0, 7], 0, 0, 1)
  run([0, 6], 1, 0, 6)
  run([6, 5], 0, -1, 6)
  run([7, 0], 0, 0, 1)
  run([8, 0], 0, 1, 6)
  run([9, 6], 1, 0, 6)
  run([14, 7], 0, 0, 1)
  run([14, 8], -1, 0, 6)
  run([8, 9], 0, 1, 6)
  run([7, 14], 0, 0, 1)
  run([6, 14], 0, 0, 1)
  return t
})()

/** A quarter turn clockwise around the board's centre, in board units (a cell is 1). */
const turn = ([x, y]: Pt, times: number): Pt => {
  let p: Pt = [x, y]
  for (let i = 0; i < times; i++) p = [15 - p[1], p[0]]
  return p
}
const centre = ([c, r]: Pt): Pt => [c + 0.5, r + 0.5]

/** Red's home column (rows 13 to 9 of the middle column), the other colours turned. */
export const homeColumn = (seat: number): Pt[] => [0, 1, 2, 3, 4].map((i) => [7, 13 - i] as Pt).map((c) => turn(centre(c), seat).map((v) => v - 0.5) as Pt)

/** Where token `k` waits in the base: four wells in red's corner, turned for the others. */
export const BASE_WELLS: Pt[] = [
  [2, 11],
  [4, 11],
  [2, 13],
  [4, 13],
]

/** The centre of a token's spot, in board units. */
export function spot(seat: number, p: number, k: number): Pt {
  if (p < 0) return turn(BASE_WELLS[k], seat)
  if (p <= 50) return centre(TRACK[(seat * 13 + p) % 52])
  if (p < HOME) return centre(homeColumn(seat)[p - 51])
  // Home: the colour's triangle in the centre, the tokens side by side.
  return turn([6.9 + (k % 2) * 1.2, 8.15 + Math.floor(k / 2) * 0.55], seat)
}

/** Every spot a move passes through, the last one where it lands (hop by hop). */
export function path(seat: number, k: number, from: number, to: number): Pt[] {
  if (from < 0) return [spot(seat, 0, k)]
  const out: Pt[] = []
  for (let p = from + 1; p <= to; p++) out.push(spot(seat, p, k))
  return out
}

// ---- rules ----

export function legal(g: Pick<LudoGame, 'tokens' | 'turn'>, d: number): number[] {
  if (g.turn === null) return []
  const r: number[] = []
  for (let k = 0; k < 4; k++) {
    const p = g.tokens[g.turn * 4 + k]
    if ((p === -1 && d === 6) || (p >= 0 && p + d <= HOME)) r.push(k)
  }
  return r
}

/** Legal tokens that are all the same move (any token out of the base): played without asking. */
export function onlyMove(g: Pick<LudoGame, 'tokens' | 'turn'>, d: number): number | null {
  const l = legal(g, d)
  if (!l.length || g.turn === null) return null
  return new Set(l.map((k) => g.tokens[g.turn! * 4 + k])).size === 1 ? l[0] : null
}

export const activeSeats = (g: Pick<LudoGame, 'players' | 'out'>) => [0, 1, 2, 3].filter((s) => g.players[s] !== null && !g.out[s])

/**
 * The game engine for the demo backend, the server's rules step for step. It works on a copy: `next` collects
 * the events, `sixes` counts sixes in the current turn.
 */
export class LudoEngine {
  events: LudoEvent[] = []
  constructor(public g: LudoGame & { sixes: number; misses: number[] }) {}

  private nextTurn() {
    const g = this.g
    for (let i = 1; i <= 4; i++) {
      const s = ((g.turn ?? 3) + i) % 4
      if (g.players[s] !== null && !g.out[s]) {
        g.turn = s
        break
      }
    }
    g.dice = null
    g.sixes = 0
    g.deadline = Date.now() + TURN_MS
  }

  private finish(winner: number | null) {
    const g = this.g
    g.status = 'finished'
    g.winner = winner
    g.dice = null
    g.deadline = null
    g.endedAt = Date.now()
    if (winner !== null) this.events.push({ k: 'win', s: winner })
  }

  start() {
    this.g.status = 'playing'
    this.g.turn = 3
    this.nextTurn()
    this.events.push({ k: 'start', s: this.g.turn! })
  }

  forfeit(seat: number, why: 'left' | 'timeouts' | 'quit') {
    const g = this.g
    if (g.status === 'lobby') {
      g.players[seat] = null
      g.names[seat] = null
      return
    }
    if (g.status !== 'playing' || g.players[seat] === null || g.out[seat]) return
    g.out[seat] = true
    for (let k = 0; k < 4; k++) g.tokens[seat * 4 + k] = -1
    this.events.push({ k: 'out', s: seat, why })
    const left = activeSeats(g)
    if (left.length < 2) return this.finish(left[0] ?? null)
    if (g.turn === seat) this.nextTurn()
  }

  move(k: number) {
    const g = this.g
    const seat = g.turn!
    const d = g.dice!
    const f = g.tokens[seat * 4 + k]
    const to = f === -1 ? 0 : f + d
    g.tokens[seat * 4 + k] = to
    const cap: [number, number, number][] = []
    const sq = square(seat, to)
    if (sq !== null && !isSafe(sq)) {
      for (let s = 0; s < 4; s++) {
        if (s === seat || g.players[s] === null || g.out[s]) continue
        for (let j = 0; j < 4; j++) {
          const p = g.tokens[s * 4 + j]
          if (square(s, p) === sq) {
            cap.push([s, j, p])
            g.tokens[s * 4 + j] = -1
          }
        }
      }
    }
    this.events.push({ k: 'move', s: seat, t: k, f, to, cap })
    g.dice = null
    if ([0, 1, 2, 3].every((j) => g.tokens[seat * 4 + j] === HOME)) return this.finish(seat)
    if (d === 6 || cap.length || to === HOME) {
      if (d !== 6) g.sixes = 0
      g.deadline = Date.now() + TURN_MS
      return
    }
    this.nextTurn()
  }

  roll(d: number, auto: boolean) {
    const g = this.g
    g.roll = d
    this.events.push({ k: 'roll', s: g.turn!, d })
    g.sixes = d === 6 ? g.sixes + 1 : 0
    if (g.sixes >= 3) {
      this.events.push({ k: 'triple', s: g.turn! })
      return this.nextTurn()
    }
    const l = legal(g, d)
    if (!l.length) {
      this.events.push({ k: 'pass', s: g.turn! })
      return this.nextTurn()
    }
    g.dice = d
    const only = onlyMove(g, d)
    if (auto || only !== null) return this.move(only ?? l[0])
    g.deadline = Date.now() + TURN_MS
  }

  timeout() {
    const g = this.g
    const s = g.turn!
    this.events.push({ k: 'timeout', s })
    g.misses[s] += 1
    if (g.misses[s] >= 3) return this.forfeit(s, 'timeouts')
    if (g.dice === null) return this.roll(1 + Math.floor(Math.random() * 6), true)
    this.move(legal(g, g.dice)[0])
  }
}
