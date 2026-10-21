import { BackendError } from './backend'
import type { TKey } from './i18n'

export function flagEmoji(iso2?: string | null) {
  if (!iso2 || iso2.length !== 2) return ''
  return String.fromCodePoint(...[...iso2.toUpperCase()].map((c) => 0x1f1e6 + c.charCodeAt(0) - 65))
}

const rtf = new Map<string, Intl.RelativeTimeFormat>()

/** "5 min ago", "vor 3 Std." … in the given locale; older than a week shows the date. */
export function timeAgo(at: number, locale: string, justNow: string, now = Date.now()) {
  const s = Math.max(0, Math.round((now - at) / 1000))
  if (s < 45) return justNow
  let f = rtf.get(locale)
  if (!f) {
    f = new Intl.RelativeTimeFormat(locale, { numeric: 'always', style: 'short' })
    rtf.set(locale, f)
  }
  const m = Math.round(s / 60)
  if (m < 60) return f.format(-m, 'minute')
  const h = Math.round(m / 60)
  if (h < 24) return f.format(-h, 'hour')
  const d = Math.round(h / 24)
  if (d < 7) return f.format(-d, 'day')
  return new Date(at).toLocaleDateString(locale, { day: 'numeric', month: 'short' })
}

export function clockTime(at: number, locale: string) {
  return new Date(at).toLocaleTimeString(locale, { hour: 'numeric', minute: '2-digit' })
}

export function compactNumber(n: number, locale: string) {
  return new Intl.NumberFormat(locale, { notation: 'compact', maximumFractionDigits: 1 }).format(n)
}

const ERROR_CODES = new Set([
  'exit_locked', 'slow_down', 'banned', 'premium_required', 'under_18', 'not_allowed', 'not_found', 'network',
  'blocked', 'too_large', 'bad_media', 'video_not_configured', 'not_signed_in', 'room_closed', 'room_gender', 'nude_photo',
  'room_age', 'room_full', 'room_title', 'room_size', 'sms_unavailable', 'voice_removed', 'mic_denied', 'queue_full',
  'poll_closed', 'bad_request',
])

export function errorCode(err: unknown) {
  return err instanceof BackendError ? err.code : 'network'
}

/** The dictionary key for an error's message ("error.exit_locked", or "error.generic" for anything unknown). */
export function errorKey(err: unknown): TKey {
  const code = errorCode(err)
  return (ERROR_CODES.has(code) ? `error.${code}` : 'error.generic') as TKey
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
