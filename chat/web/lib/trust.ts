// Real identities and manners (035_identity_manners.sql): reading trust() / my_trust() and the kyc function's
// answers, the manner levels shown, which rating tags may be sent, and when to ask for a rating. No imports but
// types, so the unit tests run it directly (node --test).
import type { KycResult, MyTrust, Trust } from './backend'

/**
 * Manners are off for now (043: the server no longer closes random chat for a low score). Scores, ratings and the
 * manner_low sheet stay in the code but are neither asked for nor shown; set to true to bring them back.
 */
export const MANNERS = false

/** Words a good rating can carry (the only ones ever shown to others). Same lists as rate_chat. */
export const GOOD_TAGS = ['kind', 'funny', 'respectful', 'good_listener', 'interesting'] as const
/** Words a bad rating can carry (never shown to anyone). */
export const BAD_TAGS = ['rude', 'sexual', 'spam', 'fake', 'creepy'] as const
export type GoodTag = (typeof GOOD_TAGS)[number]
export type BadTag = (typeof BAD_TAGS)[number]
export type RatingTag = GoodTag | BadTag
export const MAX_RATING_TAGS = 3
/** The score is shown once this many people have rated someone ("New here" before). */
export const MIN_RATINGS_SHOWN = 3
/** Below this random chat was closed (find_match2: 'manner_low'; off since 043). */
export const MANNER_FLOOR = 20
/** Everyone starts here. */
export const MANNER_START = 50

export type MannerLevel = 'needs_work' | 'low' | 'good' | 'great' | 'excellent'

/** <20 Needs work, 20–39 Low, 40–69 Good, 70–89 Great, 90+ Excellent. */
export function mannerLevel(score: number): MannerLevel {
  if (!(score >= MANNER_FLOOR)) return 'needs_work'
  if (score < 40) return 'low'
  if (score < 70) return 'good'
  if (score < 90) return 'great'
  return 'excellent'
}

/** Whether the score means something yet (enough ratings), so it is shown. */
export const mannerShown = (ratings: number) => ratings >= MIN_RATINGS_SHOWN

/** Each level's colour (the gauge, the label). Picked to read on white. */
export const LEVEL_COLORS: Record<MannerLevel, string> = {
  needs_work: '#e5484d',
  low: '#f2711c',
  good: '#e0a100',
  great: '#22a35a',
  excellent: '#0e9f9a',
}

/** The tags a rating may carry: the chosen side's only, no repeats, at most 3. Null when fine, else why not. */
export function checkRating(good: boolean, tags: readonly string[]): 'bad_request' | null {
  const allowed: readonly string[] = good ? GOOD_TAGS : BAD_TAGS
  if (new Set(tags).size !== tags.length) return 'bad_request'
  if (tags.length > MAX_RATING_TAGS) return 'bad_request'
  if (tags.some((t) => !allowed.includes(t))) return 'bad_request'
  return null
}

/** Toggles a tag in a choice of at most MAX_RATING_TAGS (a fourth one is ignored). */
export function toggleTag<T extends string>(chosen: readonly T[], tag: T): T[] {
  if (chosen.includes(tag)) return chosen.filter((t) => t !== tag)
  return chosen.length >= MAX_RATING_TAGS ? [...chosen] : [...chosen, tag]
}

const KYC_RESULTS = new Set<string>(['verified', 'pending', 'declined', 'unreadable', 'minor', 'duplicate', 'none'])

/** The kyc function's `result` (or my_trust's kyc.result); anything unknown reads as 'none'. */
export function parseKycResult(v: unknown): KycResult {
  return typeof v === 'string' && KYC_RESULTS.has(v) ? (v as KycResult) : 'none'
}

/** What the verification screen shows. */
export type VerifyState =
  | 'unverified'
  | 'opening'
  | 'pending'
  | 'verified'
  | 'declined'
  | 'unreadable'
  | 'duplicate'
  | 'minor'
  | 'unavailable'

export function verifyState(s: { verified: boolean; result: KycResult | null; unavailable: boolean; opening: boolean }): VerifyState {
  if (s.verified || s.result === 'verified') return 'verified'
  if (s.unavailable) return 'unavailable'
  if (s.opening) return 'opening'
  if (!s.result || s.result === 'none') return 'unverified'
  return s.result
}

/** States the person can start a (new) check from. */
export const canStart = (s: VerifyState) => s === 'unverified' || s === 'declined' || s === 'unreadable'

type Json = Record<string, unknown>
const isObj = (v: unknown): v is Json => !!v && typeof v === 'object' && !Array.isArray(v)
const num = (v: unknown, d = 0) => (typeof v === 'number' && Number.isFinite(v) ? v : d)
const GOOD = new Set<string>(GOOD_TAGS)

/** One trust_json: verified, manner 0-100, ratings and the top good tags (bad ones are dropped if ever sent). */
export function parseTrust(raw: unknown): Trust | null {
  if (!isObj(raw) || typeof raw.id !== 'string') return null
  const tags = Array.isArray(raw.tags) ? raw.tags : []
  return {
    id: raw.id,
    verified: raw.verified === true,
    manner: Math.min(100, Math.max(0, Math.round(num(raw.manner, MANNER_START)))),
    ratings: Math.max(0, num(raw.ratings)),
    tags: tags.flatMap((t) =>
      isObj(t) && typeof t.tag === 'string' && GOOD.has(t.tag) && num(t.n) > 0 ? [{ tag: t.tag as GoodTag, n: num(t.n) }] : [],
    ),
  }
}

export function parseTrustList(raw: unknown): Trust[] {
  return (Array.isArray(raw) ? raw : []).flatMap((r) => parseTrust(r) ?? [])
}

/** my_trust(): my trust plus whether verification is required, my last check and the chats I rated today. */
export function parseMyTrust(raw: unknown, parseTime: (ts: string) => number): MyTrust | null {
  const base = parseTrust(raw)
  if (!base || !isObj(raw)) return null
  const k = isObj(raw.kyc) ? raw.kyc : null
  const at = k && typeof k.at === 'string' ? parseTime(k.at) : NaN
  return {
    ...base,
    required: raw.required === true,
    kyc: k
      ? {
          status: typeof k.status === 'string' ? k.status : '',
          result: k.result == null ? null : parseKycResult(k.result),
          at: Number.isNaN(at) ? 0 : at,
        }
      : null,
    rated: (Array.isArray(raw.rated) ? raw.rated : []).filter((x): x is string => typeof x === 'string'),
  }
}

/**
 * Whether to ask for a rating once a random chat ended: both wrote at least one line (the server's rule), it isn't
 * rated yet (here or, per my_trust, on another device) and the person didn't skip it.
 */
export function shouldAskRating(c: { matchId: string; bothWrote: boolean; rated: Iterable<string>; skipped: Iterable<string> }) {
  if (!c.bothWrote) return false
  for (const id of c.rated) if (id === c.matchId) return false
  for (const id of c.skipped) if (id === c.matchId) return false
  return true
}

