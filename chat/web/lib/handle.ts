// IDs ("handles": handle_valid of 055) and plain names (name_plain): the server's rules, mirrored for instant feedback
// while typing. The server stays the judge (handle_check, set_handle, the name trigger); keep these in step with the
// migrations. No imports, so the unit tests run it directly (node --test).

export const HANDLE_MIN = 2
export const HANDLE_MAX = 30

/** What handle_check answers ('mine': it already is my ID). */
export type HandleCheck = 'ok' | 'mine' | 'invalid' | 'reserved' | 'taken'

/** The spelling the server stores (set_handle, handle_check): trimmed, lower case. */
export const handleSpelling = (raw: string) => raw.trim().toLowerCase()

const within = (c: number, ranges: [number, number][]) => ranges.some(([a, b]) => c >= a && c <= b)

/** The few IPA letters real alphabets use (Hausa, Yoruba, Azerbaijani…), allowed in names. */
const IPA_LETTERS = new Set([595, 596, 599, 601, 603])

/**
 * handle_valid() (055, Instagram's rule): 2–30 of English letters a–z, digits, "." and "_", at least one letter or
 * digit, no "." first, last or twice in a row. Given the stored spelling (handleSpelling).
 */
export const handleValid = (p: string) => /^[a-z0-9._]{2,30}$/.test(p) && /[a-z0-9]/.test(p) && !/^\.|\.$|\.\./.test(p)

const RESERVED = new Set(['support', 'staff', 'help', 'team', 'system', 'security', 'root', 'null', 'undefined'])

/** handle_reserved(): staff look-alikes nobody may take. */
export function handleReserved(p: string): boolean {
  const low = p.toLowerCase()
  return /^(yaari|admin|official|moderator)/.test(low) || RESERVED.has(low)
}

/**
 * What can be said without asking the server: 'invalid', 'reserved' or null (fine as far as this device knows).
 * `mine`: my current ID (a new spelling of it is never refused as reserved, like set_handle).
 */
export function handleProblem(raw: string, mine?: string | null): 'invalid' | 'reserved' | null {
  const h = handleSpelling(raw)
  if (!handleValid(h)) return 'invalid'
  if (handleReserved(h) && h.toLowerCase() !== mine?.toLowerCase()) return 'reserved'
  return null
}

/**
 * An ID to offer from a name: its English letters and digits in lower case, words joined with "_", cut to fit; empty
 * when nothing usable is left (a name in another script: the server's free ID stays). `digits` (e.g. "482") is added
 * after "_" to try another one.
 */
export function suggestHandle(name: string, digits = ''): string {
  const tail = digits ? `_${digits}` : ''
  const words = name.toLowerCase().split(/\s+/).map((w) => w.replace(/[^a-z0-9]/g, '')).filter(Boolean)
  const base = words.join('_').slice(0, HANDLE_MAX - tail.length).replace(/_$/, '')
  const out = base + tail
  return base && handleValid(out) && !handleReserved(out) ? out : ''
}

/** Fancy-font letters (𝓛𝓲𝓼𝓪, Ｌｉｓａ, ʟɪsᴀ, ⓛⓘⓢⓐ, ℒ, Cherokee and Canadian look-alikes…), name_plain()'s list. */
const FANCY: [number, number][] = [
  [119808, 120831], // Mathematical Alphanumeric Symbols
  [65281, 65376], // fullwidth
  [7424, 7615], // small capitals, modifier letters
  [8304, 8351], // superscripts, subscripts
  [8448, 8527], // letterlike
  [9312, 9471], // circled / parenthesised
  [127232, 127461], // enclosed alphanumerics (not the flag letters after them)
  [42784, 43007], // Latin Extended-D
  [5024, 5119], [43888, 43967], // Cherokee
  [5120, 5759], // Canadian syllabics
]
/** Combining marks: one after a letter is fine (accents), a second in a row is a stack (zalgo). */
const MARKS: [number, number][] = [[768, 879], [6832, 6911], [7616, 7679], [8400, 8447], [65056, 65071]]

/**
 * name_plain(): emoji are fine, the fancy-font letters and stacked accents are not. The server checks the NFC form
 * of the name, so this does too.
 */
export function namePlain(name: string): boolean {
  let marks = 0
  for (const ch of name.normalize('NFC')) {
    const c = ch.codePointAt(0)!
    if (within(c, FANCY) || (c >= 592 && c <= 767 && !IPA_LETTERS.has(c))) return false
    if (within(c, MARKS)) {
      if (++marks > 1) return false
    } else {
      marks = 0
    }
  }
  return true
}

/**
 * name_clean() (111) for a name: letters of any language with their marks, digits, single spaces and . , _ - ' ’
 * (O'Neil, Abd-Allah, D’Souza); no emoji, symbols (★ ꧁ ♥) or blank fillers (ㅤ), and namePlain's fancy fonts (the owner,
 * 2026-10-31: no fancy fonts or special characters, as Instagram's IDs). Checked as saved: trimmed, spaces collapsed.
 * The server, without Unicode classes in its regexes, blocks the symbol ranges instead; this is the stricter side.
 */
export function nameClean(name: string): boolean {
  const n = name.normalize('NFC').trim().replace(/\s+/g, ' ')
  return /^[\p{L}\p{M}\p{Nd}._,'’\- \u200C\u200D]*$/u.test(n) && !/[\u115F\u1160\u3164\uFFA0]/.test(n) && namePlain(n)
}
