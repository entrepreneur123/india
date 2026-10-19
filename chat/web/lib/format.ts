import { BackendError } from './backend'

export function flagEmoji(iso2?: string | null) {
  if (!iso2 || iso2.length !== 2) return ''
  return String.fromCodePoint(...[...iso2.toUpperCase()].map((c) => 0x1f1e6 + c.charCodeAt(0) - 65))
}

export function timeAgo(at: number, now = Date.now()) {
  const s = Math.max(0, Math.round((now - at) / 1000))
  if (s < 45) return 'just now'
  const m = Math.round(s / 60)
  if (m < 60) return `${m}m ago`
  const h = Math.round(m / 60)
  if (h < 24) return `${h}h ago`
  const d = Math.round(h / 24)
  if (d < 7) return `${d}d ago`
  return new Date(at).toLocaleDateString(undefined, { day: 'numeric', month: 'short' })
}

export function clockTime(at: number) {
  return new Date(at).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })
}

export function compactNumber(n: number) {
  return new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 }).format(n)
}

const ERROR_COPY: Record<string, string> = {
  exit_locked: 'Wait a moment before leaving',
  slow_down: 'You’re sending too fast',
  banned: 'Your account is suspended',
  premium_required: 'This is a Yaari Plus feature',
  under_18: 'You must be 18 or older to use yaari',
  not_allowed: 'That action isn’t allowed',
  not_found: 'We couldn’t find that anymore',
  network: 'Connection problem. Check your internet and try again',
  blocked: 'You can’t interact with this person',
  too_large: 'That file is too large (25 MB max)',
  bad_media: 'That file can’t be sent',
  video_not_configured: 'Video calls aren’t available yet',
  not_signed_in: 'Please sign in again',
  sms_unavailable: 'We couldn’t send a code to this number right now. Please try again later',
}

export function errorCode(err: unknown) {
  return err instanceof BackendError ? err.code : 'network'
}

export function friendlyError(err: unknown) {
  return ERROR_COPY[errorCode(err)] ?? 'Something went wrong. Please try again'
}

export function ageFromBirthDate(value: string) {
  const born = new Date(value)
  if (Number.isNaN(born.getTime())) return null
  const now = new Date()
  let age = now.getFullYear() - born.getFullYear()
  const m = now.getMonth() - born.getMonth()
  if (m < 0 || (m === 0 && now.getDate() < born.getDate())) age--
  return age
}

export function maxBirthDate() {
  const d = new Date()
  d.setFullYear(d.getFullYear() - 18)
  return d.toISOString().slice(0, 10)
}

/** The server refuses birth dates more than 100 years ago. */
export function minBirthDate() {
  const d = new Date()
  d.setFullYear(d.getFullYear() - 100)
  return d.toISOString().slice(0, 10)
}

/** Returns the 10-digit Indian mobile number, or null. Accepts +91 / 91 / 0 prefixes, spaces and dashes. */
export function indianMobile(input: string): string | null {
  const digits = input.replace(/\D/g, '')
  const local =
    digits.length === 12 && digits.startsWith('91') ? digits.slice(2)
    : digits.length === 11 && digits.startsWith('0') ? digits.slice(1)
    : digits
  return local.length === 10 && /^[6-9]/.test(local) ? local : null
}

/**
 * The full international number in digits ("919876543210"), or null — the same rule as the Android app
 * (Validation.phone): India keeps its exact mobile rule; elsewhere a pasted "+<code>" and the trunk "0" are
 * dropped and the E.164 length limit applies (the SMS step rejects numbers that don't exist).
 */
export function phoneDigits(dial: string, input: string): string | null {
  if (dial === '91') {
    const local = indianMobile(input)
    return local ? `91${local}` : null
  }
  let digits = input.replace(/\D/g, '')
  if (input.trim().startsWith('+') && digits.startsWith(dial)) digits = digits.slice(dial.length)
  digits = digits.replace(/^0+/, '')
  const full = dial + digits
  return digits.length >= 6 && full.length <= 15 ? full : null
}
