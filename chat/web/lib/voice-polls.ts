// Room polls on this device (029_room_replies_polls.sql): the rules a new poll must meet (the server checks them
// again), and how the polls on screen take in what arrives: my own replies, live updates from the room, re-reads.
// No imports but types, so the unit tests run it directly (node --test).
import type { VoicePoll } from './backend'

export const POLL_QUESTION_MAX = 120
export const POLL_OPTION_MAX = 40
export const POLL_MIN_OPTIONS = 2
export const POLL_MAX_OPTIONS = 4
/** voice_polls_get reads at most this many at once. */
export const POLLS_PER_READ = 50

export type PollProblem = 'question' | 'options' | 'long' | 'same'

/**
 * A new poll as the server will take it: trimmed, empty option rows dropped. `problem`: why it can't be sent yet
 * (no question, fewer than 2 options, an option too long, two the same ignoring case).
 */
export function checkPoll(question: string, options: string[]): { question: string; options: string[]; problem: PollProblem | null } {
  const q = question.trim()
  const opts = options.map((o) => o.trim()).filter(Boolean)
  let problem: PollProblem | null = null
  if (q.length < 1 || q.length > POLL_QUESTION_MAX) problem = 'question'
  else if (opts.length < POLL_MIN_OPTIONS || opts.length > POLL_MAX_OPTIONS) problem = 'options'
  else if (opts.some((o) => o.length > POLL_OPTION_MAX)) problem = 'long'
  else if (new Set(opts.map((o) => o.toLowerCase())).size !== opts.length) problem = 'same'
  return { question: q, options: opts, problem }
}

/** Indexes of the options that repeat an earlier one (ignoring case), to mark them in the form. */
export function repeatedOptions(options: string[]): Set<number> {
  const seen = new Set<string>()
  const out = new Set<number>()
  options.forEach((o, i) => {
    const k = o.trim().toLowerCase()
    if (!k) return
    if (seen.has(k)) out.add(i)
    seen.add(k)
  })
  return out
}

export type PollMap = Record<number, VoicePoll>

export type PollAction =
  /** From a reply to my own request (vote, close, a read): `mine` is the truth. */
  | { type: 'mine'; poll: VoicePoll }
  /** A live update from the room: counts are the truth, `mine` is not known (null): keep mine. */
  | { type: 'room'; poll: VoicePoll }
  /** My tap, shown before the server answers. */
  | { type: 'vote'; id: number; choice: number }
  /** Back to how it was (a refused vote). */
  | { type: 'restore'; poll: VoicePoll }

/** A poll never opens again once closed; anything else is the newer copy. */
export function mergePoll(prev: VoicePoll | undefined, next: VoicePoll, mineKnown: boolean): VoicePoll {
  const mine = mineKnown ? next.mine : prev?.mine ?? null
  return { ...next, mine, closed: next.closed || !!prev?.closed }
}

/** My vote counted at once: the old choice loses one, the new one gains one (a first vote adds to the total). */
export function voteLocally(p: VoicePoll, choice: number): VoicePoll {
  if (p.closed || choice < 0 || choice >= p.options.length || p.mine === choice) return p
  const counts = p.options.map((_, i) => p.counts[i] ?? 0)
  if (p.mine !== null && counts[p.mine] > 0) counts[p.mine]--
  counts[choice]++
  return { ...p, counts, total: p.mine === null ? p.total + 1 : p.total, mine: choice }
}

export function pollsReducer(state: PollMap, a: PollAction): PollMap {
  switch (a.type) {
    case 'mine':
    case 'room': {
      if (!validPoll(a.poll)) return state
      return { ...state, [a.poll.id]: mergePoll(state[a.poll.id], a.poll, a.type === 'mine') }
    }
    case 'vote': {
      const p = state[a.id]
      if (!p) return state
      const next = voteLocally(p, a.choice)
      return next === p ? state : { ...state, [a.id]: next }
    }
    case 'restore':
      return { ...state, [a.poll.id]: a.poll }
  }
}

/** Whole percentages that add up to 100 (largest remainder), 0 for every option before the first vote. */
export function pollPercents(counts: number[], total: number): number[] {
  if (total <= 0) return counts.map(() => 0)
  const raw = counts.map((c) => (c / total) * 100)
  const out = raw.map(Math.floor)
  let left = 100 - out.reduce((a, b) => a + b, 0)
  const order = raw.map((r, i) => [r - Math.floor(r), i] as const).sort((a, b) => b[0] - a[0])
  for (const [, i] of order) {
    if (left <= 0) break
    if (counts[i] > 0) {
      out[i]++
      left--
    }
  }
  return out
}

function validPoll(p: VoicePoll) {
  return Number.isFinite(p.id) && Array.isArray(p.options) && p.options.length >= 2
}

/** A poll as the server sends it (voice_poll_json): checked field by field, null when it isn't one. */
export function parsePoll(j: unknown): VoicePoll | null {
  if (!j || typeof j !== 'object') return null
  const o = j as Record<string, unknown>
  const id = Number(o.id)
  const options = Array.isArray(o.options) ? o.options.filter((x): x is string => typeof x === 'string') : []
  if (!Number.isFinite(id) || typeof o.question !== 'string' || options.length < 2) return null
  const counts = options.map((_, i) => {
    const n = Array.isArray(o.counts) ? Number(o.counts[i]) : 0
    return Number.isFinite(n) && n > 0 ? n : 0
  })
  const total = Number(o.total)
  const mine = o.mine === null || o.mine === undefined ? null : Number(o.mine)
  return {
    id,
    question: o.question,
    options,
    counts,
    total: Number.isFinite(total) && total >= 0 ? total : counts.reduce((a, b) => a + b, 0),
    closed: o.closed === true,
    by: typeof o.by === 'string' ? o.by : '',
    mine: mine !== null && Number.isInteger(mine) && mine >= 0 && mine < options.length ? mine : null,
  }
}
