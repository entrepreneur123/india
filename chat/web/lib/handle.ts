// IDs ("handles", 040_handles_looks.sql) and plain names: the server's rules, mirrored for instant feedback while
// typing. The server stays the judge (handle_check, set_handle, the name trigger). The code-point ranges below are
// copied from handle_valid() and name_plain(); keep them in step with the migration. No imports, so the unit tests
// run it directly (node --test).

export const HANDLE_MIN = 2
export const HANDLE_MAX = 24

/** What handle_check answers ('mine': it already is my ID). */
export type HandleCheck = 'ok' | 'mine' | 'invalid' | 'reserved' | 'taken'

/** The spelling the server stores: trimmed, Unicode NFC. */
export const handleSpelling = (raw: string) => raw.trim().normalize('NFC')

/** ASCII specials an ID may have: no space " ' ` < > \ and no @ (the @ is how IDs are shown). */
const SPECIALS = new Set([33, 35, 36, 37, 38, 40, 41, 42, 43, 44, 45, 46, 47, 58, 59, 61, 63, 91, 93, 94, 95, 123, 124, 125, 126])
/** The few IPA letters real alphabets use (Hausa, Yoruba, Azerbaijani…). */
const IPA_LETTERS = new Set([595, 596, 599, 601, 603])

const within = (c: number, ranges: [number, number][]) => ranges.some(([a, b]) => c >= a && c <= b)

/** Letters of other real scripts (handle_valid's allow-list): none of them is Latin, Greek or Cyrillic. */
const OTHER_SCRIPTS: [number, number][] = [
  [1329, 1366], [1377, 1415], // Armenian
  [1488, 1514], // Hebrew
  [1568, 1610], [1632, 1641], [1649, 1747], [1776, 1785], // Arabic, Urdu, Persian
  [2304, 3583], // Devanagari .. Sinhala
  [3584, 3839], // Thai, Lao
  [4096, 4255], // Myanmar
  [4304, 4351], // Georgian
  [4608, 4954], [4969, 4988], // Ethiopic
  [6016, 6109], [6112, 6121], // Khmer
  [12353, 12438], [12445, 12447], // Hiragana
  [12449, 12538], [12540, 12543], // Katakana
  [12593, 12686], // Hangul jamo
  [13312, 19903], [19968, 40959], // CJK
  [44032, 55203], // Hangul
]

/**
 * handle_valid(): 2–24 characters (code points) of the input as given (already NFC), letters of real scripts, ASCII
 * digits and specials, at least one letter or digit, and at most one of Latin / Greek / Cyrillic.
 */
export function handleValid(p: string): boolean {
  if (p !== p.normalize('NFC')) return false
  const chars = [...p]
  if (chars.length < HANDLE_MIN || chars.length > HANDLE_MAX) return false
  let word = false
  let latin = false
  let greek = false
  let cyr = false
  for (const ch of chars) {
    const c = ch.codePointAt(0)!
    if (c >= 48 && c <= 57) word = true
    else if ((c >= 65 && c <= 90) || (c >= 97 && c <= 122)) word = latin = true
    else if (SPECIALS.has(c)) continue
    else if ((c >= 192 && c <= 591 && c !== 215 && c !== 247) || (c >= 7680 && c <= 7935) || IPA_LETTERS.has(c)) word = latin = true
    else if (c >= 902 && c <= 974 && c !== 903) word = greek = true
    else if (c >= 1024 && c <= 1279) word = cyr = true
    else if (within(c, OTHER_SCRIPTS)) word = true
    else return false
  }
  return word && Number(latin) + Number(greek) + Number(cyr) <= 1
}

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
 * An ID to offer from a name: its letters and digits in lower case, words joined with "_", cut to fit; empty when
 * nothing usable is left (a name of emoji). `digits` (e.g. "482") is added after "_" to try another one.
 */
export function suggestHandle(name: string, digits = ''): string {
  const tail = digits ? `_${digits}` : ''
  const room = HANDLE_MAX - tail.length
  // A letter or digit of an allowed script (the "0" only pads it to the two-character minimum).
  const wordChar = (ch: string) => !SPECIALS.has(ch.codePointAt(0)!) && handleValid(`${ch}0`)
  const words = name
    .normalize('NFC')
    .toLowerCase()
    .split(/\s+/)
    .map((w) => [...w].filter(wordChar).join(''))
    .filter(Boolean)
  // Whole words while they fit, and one alphabet of Latin / Greek / Cyrillic at most (words that would mix are left out).
  let base = ''
  for (const w of words) {
    const next = base ? `${base}_${w}` : w
    if ([...next].length <= room && handleValid([...next].length < HANDLE_MIN ? `${next}0` : next)) base = next
  }
  if (!base && words.length) base = [...words[0]].slice(0, room).join('')
  const out = base + tail
  return handleValid(out) && !handleReserved(out) ? out : ''
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
