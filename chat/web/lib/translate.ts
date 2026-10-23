// Chat translation (edge function `translate`, migration 038): which language to translate into, which incoming
// lines look like they need it, and a small cache + queue so a busy chat never floods the server.
import type { Lang } from './i18n'
import { isTraditionalChinese } from './lang-tags.ts'

/** The languages the server translates into (its LANGS). */
export const TARGETS = [
  'en', 'hi', 'bn', 'ur', 'pa', 'mr', 'gu', 'ta', 'te', 'kn', 'ml', 'or', 'as', 'ne', 'si',
  'pt-BR', 'pt', 'es', 'es-419', 'fr', 'de', 'it', 'nl', 'pl', 'ro', 'cs', 'sk', 'hu', 'el', 'sv', 'da', 'no', 'fi',
  'uk', 'ru', 'bg', 'sr', 'hr', 'tr', 'ar', 'fa', 'he', 'sw', 'am', 'ha', 'yo', 'zu',
  'vi', 'th', 'id', 'ms', 'fil', 'my', 'km', 'lo', 'ja', 'ko', 'zh-Hans', 'zh-Hant', 'kk', 'uz', 'az', 'ka', 'hy',
] as const
export type Target = (typeof TARGETS)[number]

export const isTarget = (v: unknown): v is Target => typeof v === 'string' && (TARGETS as readonly string[]).includes(v)

/** Each target named in its own language, for the "Translate to" list. */
export const TARGET_NAMES: Record<Target, string> = {
  en: 'English', hi: 'हिन्दी', bn: 'বাংলা', ur: 'اردو', pa: 'ਪੰਜਾਬੀ', mr: 'मराठी', gu: 'ગુજરાતી', ta: 'தமிழ்',
  te: 'తెలుగు', kn: 'ಕನ್ನಡ', ml: 'മലയാളം', or: 'ଓଡ଼ିଆ', as: 'অসমীয়া', ne: 'नेपाली', si: 'සිංහල',
  'pt-BR': 'Português (Brasil)', pt: 'Português (Portugal)', es: 'Español (España)', 'es-419': 'Español (Latinoamérica)',
  fr: 'Français', de: 'Deutsch', it: 'Italiano', nl: 'Nederlands', pl: 'Polski', ro: 'Română', cs: 'Čeština',
  sk: 'Slovenčina', hu: 'Magyar', el: 'Ελληνικά', sv: 'Svenska', da: 'Dansk', no: 'Norsk', fi: 'Suomi',
  uk: 'Українська', ru: 'Русский', bg: 'Български', sr: 'Српски', hr: 'Hrvatski', tr: 'Türkçe', ar: 'العربية',
  fa: 'فارسی', he: 'עברית', sw: 'Kiswahili', am: 'አማርኛ', ha: 'Hausa', yo: 'Yorùbá', zu: 'isiZulu',
  vi: 'Tiếng Việt', th: 'ไทย', id: 'Bahasa Indonesia', ms: 'Bahasa Melayu', fil: 'Filipino', my: 'မြန်မာ',
  km: 'ខ្មែរ', lo: 'ລາວ', ja: '日本語', ko: '한국어', 'zh-Hans': '简体中文', 'zh-Hant': '繁體中文', kk: 'Қазақ',
  uz: 'Oʻzbek', az: 'Azərbaycan', ka: 'ქართული', hy: 'Հայերեն',
}

/** Regions that write Latin American Spanish (UN M.49 "419" and its countries). */
const LATAM = new Set(['419', 'mx', 'ar', 'co', 'cl', 'pe', 've', 'ec', 'gt', 'cu', 'bo', 'do', 'hn', 'py', 'sv', 'ni', 'cr', 'pa', 'uy', 'pr', 'us'])
/** Portuguese outside Brazil. */
const PT_EU = new Set(['pt', 'ao', 'mz', 'cv', 'gw', 'st', 'tl', 'mo'])
const BASE_ALIASES: Record<string, Target> = { iw: 'he', in: 'id', tl: 'fil', nb: 'no', nn: 'no', zsm: 'ms', pes: 'fa', prs: 'fa', swh: 'sw', sh: 'hr', bs: 'hr' }

/** The target code for a BCP 47 tag ("zh-TW" → zh-Hant, "pt" → pt-BR, "es-MX" → es-419, "nb" → no), or null. */
export function targetOfTag(tag: string): Target | null {
  const parts = tag.toLowerCase().split(/[-_]/)
  const base = parts[0]
  const region = parts.slice(1).find((p) => p.length === 2 || /^\d{3}$/.test(p))
  if (base === 'zh') return isTraditionalChinese(tag) ? 'zh-Hant' : 'zh-Hans'
  if (base === 'pt') return region && PT_EU.has(region) ? 'pt' : 'pt-BR'
  if (base === 'es') return region && LATAM.has(region) ? 'es-419' : 'es'
  if (isTarget(tag)) return tag
  if (BASE_ALIASES[base]) return BASE_ALIASES[base]
  return isTarget(base) ? base : null
}

/**
 * The default "Translate to": the app's language, refined by the browser's own tag for the same language
 * (Spanish UI on an es-MX phone → es-419; Chinese UI follows its script).
 */
export function defaultTarget(lang: Lang, browser: readonly string[] = []): Target {
  if (lang === 'es') {
    const tag = browser.find((b) => b.toLowerCase().split(/[-_]/)[0] === 'es')
    if (tag) return targetOfTag(tag) ?? 'es'
  }
  return targetOfTag(lang) ?? 'en'
}

// ---- "does this line need translating?" ----

type Script =
  | 'latin' | 'cyrillic' | 'greek' | 'arabic' | 'hebrew' | 'devanagari' | 'bengali' | 'gurmukhi' | 'gujarati' | 'odia'
  | 'tamil' | 'telugu' | 'kannada' | 'malayalam' | 'sinhala' | 'thai' | 'lao' | 'myanmar' | 'khmer' | 'georgian'
  | 'armenian' | 'ethiopic' | 'hangul' | 'kana' | 'han'

const RANGES: [number, number, Script][] = [
  [0x0041, 0x024f, 'latin'], [0x1e00, 0x1eff, 'latin'],
  [0x0370, 0x03ff, 'greek'], [0x1f00, 0x1fff, 'greek'],
  [0x0400, 0x052f, 'cyrillic'],
  [0x0530, 0x058f, 'armenian'],
  [0x0590, 0x05ff, 'hebrew'],
  [0x0600, 0x06ff, 'arabic'], [0x0750, 0x077f, 'arabic'], [0x08a0, 0x08ff, 'arabic'], [0xfb50, 0xfdff, 'arabic'], [0xfe70, 0xfeff, 'arabic'],
  [0x0900, 0x097f, 'devanagari'],
  [0x0980, 0x09ff, 'bengali'],
  [0x0a00, 0x0a7f, 'gurmukhi'],
  [0x0a80, 0x0aff, 'gujarati'],
  [0x0b00, 0x0b7f, 'odia'],
  [0x0b80, 0x0bff, 'tamil'],
  [0x0c00, 0x0c7f, 'telugu'],
  [0x0c80, 0x0cff, 'kannada'],
  [0x0d00, 0x0d7f, 'malayalam'],
  [0x0d80, 0x0dff, 'sinhala'],
  [0x0e00, 0x0e7f, 'thai'],
  [0x0e80, 0x0eff, 'lao'],
  [0x1000, 0x109f, 'myanmar'],
  [0x10a0, 0x10ff, 'georgian'],
  [0x1100, 0x11ff, 'hangul'], [0x3130, 0x318f, 'hangul'], [0xac00, 0xd7af, 'hangul'],
  [0x1200, 0x139f, 'ethiopic'],
  [0x1780, 0x17ff, 'khmer'],
  [0x3040, 0x30ff, 'kana'], [0x31f0, 0x31ff, 'kana'], [0xff66, 0xff9f, 'kana'],
  [0x3400, 0x4dbf, 'han'], [0x4e00, 0x9fff, 'han'], [0xf900, 0xfaff, 'han'],
]

function scriptOf(cp: number): Script | null {
  // Latin letters only (not digits or punctuation in the same block).
  if (cp < 0x80) return (cp >= 0x41 && cp <= 0x5a) || (cp >= 0x61 && cp <= 0x7a) ? 'latin' : null
  if (cp >= 0x00c0 && cp <= 0x024f && cp !== 0xd7 && cp !== 0xf7) return 'latin'
  for (const [a, b, s] of RANGES) if (cp >= a && cp <= b && a > 0x7f) return s
  return null
}

/** The script each target is normally written in. */
const SCRIPT_OF: Record<Target, Script> = {
  en: 'latin', hi: 'devanagari', bn: 'bengali', ur: 'arabic', pa: 'gurmukhi', mr: 'devanagari', gu: 'gujarati',
  ta: 'tamil', te: 'telugu', kn: 'kannada', ml: 'malayalam', or: 'odia', as: 'bengali', ne: 'devanagari', si: 'sinhala',
  'pt-BR': 'latin', pt: 'latin', es: 'latin', 'es-419': 'latin', fr: 'latin', de: 'latin', it: 'latin', nl: 'latin',
  pl: 'latin', ro: 'latin', cs: 'latin', sk: 'latin', hu: 'latin', el: 'greek', sv: 'latin', da: 'latin', no: 'latin',
  fi: 'latin', uk: 'cyrillic', ru: 'cyrillic', bg: 'cyrillic', sr: 'cyrillic', hr: 'latin', tr: 'latin', ar: 'arabic',
  fa: 'arabic', he: 'hebrew', sw: 'latin', am: 'ethiopic', ha: 'latin', yo: 'latin', zu: 'latin', vi: 'latin',
  th: 'thai', id: 'latin', ms: 'latin', fil: 'latin', my: 'myanmar', km: 'khmer', lo: 'lao', ja: 'kana', ko: 'hangul',
  'zh-Hans': 'han', 'zh-Hant': 'han', kk: 'cyrillic', uz: 'latin', az: 'latin', ka: 'georgian', hy: 'armenian',
}

/**
 * Common short words of languages written in Latin letters, to tell them apart cheaply ("hola que tal" is not
 * English). `hinglish` is Hindi/Urdu written in Latin letters, as a lot of people text.
 */
const WORDS: Record<string, string[]> = {
  en: ['the', 'and', 'you', 'are', 'is', 'what', 'how', 'where', 'from', 'this', 'that', 'with', 'have', 'not', 'yes', 'my', 'your', 'me', 'it', 'do', 'to', 'of', 'in', 'hi', 'hello', 'good', 'nice', 'thanks', 'lol', 'im', "i'm", 'i', 'am', 'so', 'for', 'just', 'like', 'can', 'here'],
  es: ['que', 'de', 'el', 'la', 'los', 'las', 'y', 'es', 'por', 'para', 'como', 'qué', 'cómo', 'estás', 'estas', 'hola', 'bien', 'gracias', 'muy', 'pero', 'yo', 'tú', 'eres', 'soy', 'dónde', 'donde', 'también', 'mucho', 'buenas', 'tienes', 'jaja', 'jajaja', 'jajajaja', 'siempre', 'aquí', 'ahora', 'vamos', 'nada', 'todo', 'eso', 'esto', 'con', 'una', 'un', 'más', 'hoy', 'amigo', 'amiga', 'claro', 'vale', 'genial', 'oye', 'pues', 'tengo'],
  pt: ['que', 'de', 'o', 'a', 'os', 'e', 'é', 'você', 'voce', 'vc', 'não', 'nao', 'tudo', 'bem', 'obrigado', 'obrigada', 'oi', 'olá', 'eu', 'muito', 'mas', 'onde', 'tá', 'ta', 'sou', 'meu', 'também', 'kkk', 'kkkk', 'kkkkk', 'né', 'pra', 'isso', 'só', 'às', 'tô', 'aqui', 'agora', 'vamos', 'legal', 'gente', 'ele', 'ela', 'nós', 'com', 'uma', 'um', 'mais', 'hoje', 'tchau', 'beleza', 'cara', 'mano'],
  fr: ['le', 'la', 'les', 'et', 'est', 'je', 'tu', 'vous', 'pas', 'que', 'qui', 'bonjour', 'salut', 'merci', 'oui', 'non', 'avec', 'pour', 'mais', 'très', 'suis', 'es', 'ça', 'ca', 'où', 'comment', 'c\'est', 'j\'ai', 'mdr', 'trop', 'bien', 'aussi', 'une', 'des', 'du', 'au', 'mon', 'ma', 'ton', 'ta', 'quoi', 'aujourd\'hui', 'ici'],
  de: ['der', 'die', 'das', 'und', 'ist', 'ich', 'du', 'nicht', 'ja', 'nein', 'wie', 'was', 'wo', 'hallo', 'danke', 'gut', 'sehr', 'aber', 'bin', 'bist', 'mit', 'auch', 'ein', 'eine', 'woher', 'geht', 'dir', 'mir', 'heute', 'jetzt', 'hier', 'schon', 'noch', 'mal', 'doch', 'echt', 'genau'],
  it: ['il', 'la', 'che', 'e', 'di', 'è', 'non', 'sono', 'sei', 'ciao', 'grazie', 'come', 'stai', 'bene', 'anche', 'molto', 'dove', 'io', 'tu', 'però', 'perché', 'ragazzi', 'oggi', 'adesso', 'qui', 'cosa', 'tutto', 'buongiorno', 'buonasera', 'una', 'gli'],
  nl: ['de', 'het', 'een', 'en', 'is', 'ik', 'je', 'jij', 'niet', 'wat', 'hoe', 'waar', 'hoi', 'dank', 'goed', 'ook', 'maar', 'ben', 'bent', 'met'],
  id: ['yang', 'dan', 'di', 'ini', 'itu', 'aku', 'saya', 'kamu', 'tidak', 'nggak', 'gak', 'apa', 'ada', 'dari', 'mana', 'juga', 'sudah', 'udah', 'belum', 'terima', 'kasih', 'bisa', 'lagi', 'dong', 'sih', 'kok', 'banget', 'aja'],
  tr: ['ve', 'bir', 'bu', 'ne', 'nasıl', 'nasilsin', 'nasılsın', 'merhaba', 'teşekkürler', 'evet', 'hayır', 'değil', 'ben', 'sen', 'için', 'çok', 'güzel', 'nerede', 'nerelisin', 'iyi'],
  pl: ['nie', 'jest', 'to', 'się', 'jak', 'co', 'tak', 'cześć', 'dzięki', 'dobrze', 'ja', 'ty', 'jesteś', 'gdzie', 'bardzo', 'ale', 'też'],
  ro: ['și', 'este', 'nu', 'ce', 'ești', 'eu', 'tu', 'bună', 'mulțumesc', 'foarte', 'unde', 'dar', 'sunt'],
  sw: ['na', 'ya', 'wa', 'ni', 'kwa', 'habari', 'asante', 'sana', 'mimi', 'wewe', 'hapana', 'ndiyo', 'nini', 'gani', 'wapi', 'poa', 'mambo', 'sawa'],
  fil: ['ang', 'ng', 'mga', 'sa', 'ako', 'ikaw', 'ka', 'ko', 'mo', 'hindi', 'oo', 'po', 'salamat', 'kumusta', 'kamusta', 'ano', 'saan', 'naman', 'lang', 'talaga', 'ba', 'na'],
  vi: ['không', 'là', 'của', 'và', 'có', 'tôi', 'bạn', 'được', 'này', 'cảm', 'ơn', 'chào', 'đâu', 'gì', 'rất', 'em', 'anh'],
  hinglish: ['hai', 'hain', 'kya', 'nahi', 'nahin', 'kaise', 'kaisa', 'kaisi', 'ho', 'mera', 'meri', 'tum', 'tera', 'teri', 'aap', 'bhai', 'yaar', 'acha', 'accha', 'haan', 'kahan', 'kaha', 'hum', 'mujhe', 'tujhe', 'kuch', 'bahut', 'bohot', 'matlab', 'chal', 'kar', 'raha', 'rahi', 'hu', 'hoon', 'nhi', 'kyu', 'kyun', 'theek', 'thik', 'ka', 'ki', 'ke', 'koi', 'bhi', 'bas', 'ko', 'tha', 'thi', 'abhi', 'sab', 'ekdum', 'aaj', 'yeh', 'woh', 'toh', 'kyunki', 'lekin', 'pyaar', 'dost', 'gaana', 'achha', 'mast', 'chalo'],
}

/** Which word list a Latin-script target's speakers use. */
const WORD_LANG: Partial<Record<Target, string>> = {
  en: 'en', es: 'es', 'es-419': 'es', 'pt-BR': 'pt', pt: 'pt', fr: 'fr', de: 'de', it: 'it', nl: 'nl', id: 'id', ms: 'id',
  tr: 'tr', pl: 'pl', ro: 'ro', sw: 'sw', fil: 'fil', vi: 'vi',
}

/** Vietnamese letters no other language here uses (â, ê, ô are Portuguese and French too). */
const VI_MARKS = /[ăđơưạảấầẩẫậắằẳẵặẹẻẽếềểễệỉịọỏốồổỗộớờởỡợụủứừửữựỳỵỷỹ]/i

const strip = (text: string) =>
  text
    .replace(/https?:\/\/\S+|www\.\S+/gi, ' ') // links
    .replace(/[@#][\p{L}\p{N}_.]+/gu, ' ') // @mentions, #tags
    .replace(/\S+@\S+\.\S+/g, ' ') // e-mail addresses

/** The words (lower case) of the Latin part of a line. */
const latinWords = (text: string) => text.toLowerCase().match(/[a-zà-ɏḀ-ỿ']+/g) ?? []

/** The best-matching word list for a Latin-script line, or null when nothing stands out. */
export function guessLatin(text: string): string | null {
  const words = latinWords(text)
  if (!words.length) return null
  if (VI_MARKS.test(text)) return 'vi'
  let best: string | null = null
  let bestHits = 0
  let second = 0
  for (const [lang, list] of Object.entries(WORDS)) {
    let hits = 0
    for (const w of words) if (list.includes(w)) hits++
    if (hits > bestHits) {
      second = bestHits
      bestHits = hits
      best = lang
    } else if (hits > second) second = hits
  }
  // A clear winner only: "no" or "a" alone says nothing.
  return bestHits >= 2 && bestHits > second ? best : bestHits === 1 && words.length <= 2 && second === 0 ? best : null
}

/**
 * Whether an incoming line should be translated into `target` without being asked: it has real words (not only
 * emoji, links, numbers or names), and they are mostly in another script than the target's, or a Latin-script line
 * clearly in another language. A line we can't tell is left alone (the reader can still tap Translate).
 */
export function shouldAutoTranslate(text: string | null | undefined, target: Target): boolean {
  if (!text) return false
  const clean = strip(text)
  const counts = new Map<Script, number>()
  let letters = 0
  for (const ch of clean) {
    const s = scriptOf(ch.codePointAt(0)!)
    if (!s) continue
    letters++
    counts.set(s, (counts.get(s) ?? 0) + 1)
  }
  if (letters < 2) return false // emoji, numbers, "k", "?"
  const mine = SCRIPT_OF[target]
  // Japanese is written with kana and kanji together; Chinese with Han only.
  const own = (counts.get(mine) ?? 0) + (target === 'ja' ? counts.get('han') ?? 0 : 0)
  if (own / letters < 0.5) {
    // Hindi and Urdu readers read Hindi typed in Latin letters ("kya haal hai") as it is.
    if ((target === 'hi' || target === 'ur') && (counts.get('latin') ?? 0) / letters >= 0.5 && guessLatin(clean) === 'hinglish') return false
    return true
  }
  // Mostly the target's own script: look closer only where several languages share it.
  if (mine === 'latin') {
    const latin = latinWords(clean)
    if (latin.length === 1 && latin[0].length <= 3) return false // "ok", "ya", "hmm"
    const guess = guessLatin(clean)
    const ours = WORD_LANG[target]
    if (guess !== null) return guess !== ours
    // English readers: accented words and not one common English word ("acordei às cinco") is another language.
    if (target === 'en') {
      const accented = latin.filter((w) => /[à-ɏḀ-ỿ]/.test(w)).length
      return latin.length >= 2 && accented >= 1 && !latin.some((w) => WORDS.en.includes(w))
    }
    return false
  }
  if (mine === 'han' && target !== 'ja') return (counts.get('kana') ?? 0) > 0 // Japanese among Chinese characters
  if (target === 'ar') return /[پچژگکیےٹڈڑںھ]/.test(clean) // Persian or Urdu letters
  return false
}

// ---- cache and queue ----

export type TranslateKind = 'chat' | 'room'
export type TranslateResult = { text: string; same: boolean }
export type Entry =
  | { status: 'loading' }
  | { status: 'done'; text: string; same: boolean }
  | { status: 'error'; code: string }

export const MAX_IN_FLIGHT = 4

export const cacheKey = (kind: TranslateKind, id: number, target: Target) => `${kind}:${id}:${target}`

/**
 * Remembers every answer for the visit, keyed by (kind, id, target), and sends at most MAX_IN_FLIGHT requests at a
 * time; asking again for something loading or known sends nothing. Lines waiting for a slot that were asked for
 * automatically are skipped once `wanted()` says they are no longer on screen. Asked-for lines go first.
 */
export class TranslateStore {
  private entries = new Map<string, Entry>()
  private waiting: { key: string; run: () => Promise<TranslateResult>; wanted: () => boolean; manual: boolean }[] = []
  private inFlight = 0
  private listeners = new Set<() => void>()

  private onError?: (code: string, manual: boolean) => void

  constructor(onError?: (code: string, manual: boolean) => void) {
    this.onError = onError
  }

  get(key: string): Entry | undefined {
    return this.entries.get(key)
  }

  subscribe(fn: () => void) {
    this.listeners.add(fn)
    return () => void this.listeners.delete(fn)
  }

  get pending() {
    return this.inFlight + this.waiting.length
  }

  private emit() {
    for (const fn of this.listeners) fn()
  }

  private set(key: string, entry: Entry | undefined) {
    if (entry) this.entries.set(key, entry)
    else this.entries.delete(key)
    this.emit()
  }

  /** Asks for a translation; `manual` = the reader tapped Translate (failures then retry and are reported). */
  request(key: string, run: () => Promise<TranslateResult>, opts: { manual: boolean; wanted?: () => boolean }) {
    const queued = this.waiting.find((w) => w.key === key)
    if (queued) {
      if (opts.manual && !queued.manual) {
        queued.manual = true
        // Moves ahead of the automatic ones.
        this.waiting = [queued, ...this.waiting.filter((w) => w !== queued)]
      }
      return
    }
    const have = this.entries.get(key)
    if (have && (have.status !== 'error' || !opts.manual)) return
    const job = { key, run, wanted: opts.wanted ?? (() => true), manual: opts.manual }
    if (opts.manual) this.waiting.unshift(job)
    else this.waiting.push(job)
    this.set(key, { status: 'loading' })
    this.pump()
  }

  /** Forgets queued automatic requests (auto-translate was turned off, the server is unavailable). */
  dropQueued() {
    const dropped = this.waiting.filter((w) => !w.manual)
    if (!dropped.length) return
    this.waiting = this.waiting.filter((w) => w.manual)
    for (const w of dropped) this.entries.delete(w.key)
    this.emit()
  }

  private pump() {
    while (this.inFlight < MAX_IN_FLIGHT && this.waiting.length) {
      const job = this.waiting.shift()!
      if (!job.manual && !job.wanted()) {
        // Scrolled away while waiting: asked again if it comes back into view.
        this.set(job.key, undefined)
        continue
      }
      this.inFlight++
      job
        .run()
        .then(
          (r) => this.set(job.key, { status: 'done', text: r.text, same: r.same }),
          (err: unknown) => {
            const code = (err as { code?: string } | null)?.code ?? 'network'
            this.set(job.key, { status: 'error', code })
            this.onError?.(code, job.manual)
          },
        )
        .finally(() => {
          this.inFlight--
          this.pump()
        })
    }
  }
}
