// What this browser remembers about identity checks and ratings (035). Storage can be blocked: then nothing is
// remembered and the app asks again next time, never more often than once a visit.

const OFF_KEY = 'yaari_kyc_off'
const ASKED_KEY = 'yaari_verify_asked'
const SKIPPED_KEY = 'yaari_rate_skipped'
/** "Identity check is coming soon" is believed this long before asking the server again. */
const OFF_FOR_MS = 12 * 3_600_000

function get(k: string) {
  try {
    return localStorage.getItem(k)
  } catch {
    return null
  }
}
function set(k: string, v: string | null) {
  try {
    if (v === null) localStorage.removeItem(k)
    else localStorage.setItem(k, v)
  } catch {
    /* blocked: lasts for this visit */
  }
}

export const kycOffCached = () => Date.now() - Number(get(OFF_KEY) ?? 0) < OFF_FOR_MS
export const markKycOff = (off: boolean) => set(OFF_KEY, off ? String(Date.now()) : null)

export { requiredCached, rememberRequired } from './required'

/** The verification sheet after sign-up shows once per browser (every start while verification is required). */
export const verifyAsked = () => get(ASKED_KEY) !== null
export const markVerifyAsked = () => set(ASKED_KEY, '1')

/** Chats whose rating was skipped (the newest 50). */
export function skippedRatings(): string[] {
  try {
    const v = JSON.parse(get(SKIPPED_KEY) ?? '[]')
    return Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string') : []
  } catch {
    return []
  }
}
export const skipRating = (matchId: string) => set(SKIPPED_KEY, JSON.stringify([...skippedRatings().filter((x) => x !== matchId), matchId].slice(-50)))
