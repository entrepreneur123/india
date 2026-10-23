import en from './locales/en'
import { isLang, langOfTag, type Lang } from './lang-tags'

export { LANG_IDS, detectLang, isLang, isRtl, langOfTag, pluralText, type Lang } from './lang-tags'

export type TKey = keyof typeof en
/**
 * Extra plural forms a language may add next to English's "x.one" / "x.other" (Intl.PluralRules categories:
 * Arabic has all of them, Russian and Polish "few" and "many"…). Without them "other" is used.
 */
export type PluralForms<K extends string> = K extends `${infer B}.one` ? `${B}.${'zero' | 'two' | 'few' | 'many'}` : never
/**
 * A language file. Keys it leaves out are shown in English, so a translation can be added (or lag behind new
 * English strings) without breaking anything; a key English doesn't have is refused by TypeScript.
 */
export type LocaleDict<K extends string> = Partial<Record<K | PluralForms<K>, string>>
export type Dict = LocaleDict<TKey>
/** English: every key. */
export type FullDict = Record<TKey, string>
export type Vars = Record<string, string | number>
export type T = (key: TKey, vars?: Vars) => string

/** In the order shown in Settings; names are written in their own language. */
export const LANGS: { id: Lang; native: string; english: string }[] = [
  { id: 'en', native: 'English', english: 'English' },
  { id: 'hi', native: 'हिन्दी', english: 'Hindi' },
  { id: 'bn', native: 'বাংলা', english: 'Bengali' },
  { id: 'ur', native: 'اردو', english: 'Urdu' },
  { id: 'pa', native: 'ਪੰਜਾਬੀ', english: 'Punjabi' },
  { id: 'mr', native: 'मराठी', english: 'Marathi' },
  { id: 'gu', native: 'ગુજરાતી', english: 'Gujarati' },
  { id: 'ta', native: 'தமிழ்', english: 'Tamil' },
  { id: 'te', native: 'తెలుగు', english: 'Telugu' },
  { id: 'kn', native: 'ಕನ್ನಡ', english: 'Kannada' },
  { id: 'ml', native: 'മലയാളം', english: 'Malayalam' },
  { id: 'ne', native: 'नेपाली', english: 'Nepali' },
  { id: 'pt-BR', native: 'Português (Brasil)', english: 'Portuguese (Brazil)' },
  { id: 'es', native: 'Español', english: 'Spanish' },
  { id: 'fr', native: 'Français', english: 'French' },
  { id: 'de', native: 'Deutsch', english: 'German' },
  { id: 'it', native: 'Italiano', english: 'Italian' },
  { id: 'nl', native: 'Nederlands', english: 'Dutch' },
  { id: 'pl', native: 'Polski', english: 'Polish' },
  { id: 'ro', native: 'Română', english: 'Romanian' },
  { id: 'tr', native: 'Türkçe', english: 'Turkish' },
  { id: 'ru', native: 'Русский', english: 'Russian' },
  { id: 'uk', native: 'Українська', english: 'Ukrainian' },
  { id: 'ar', native: 'العربية', english: 'Arabic' },
  { id: 'fa', native: 'فارسی', english: 'Persian' },
  { id: 'he', native: 'עברית', english: 'Hebrew' },
  { id: 'sw', native: 'Kiswahili', english: 'Swahili' },
  { id: 'vi', native: 'Tiếng Việt', english: 'Vietnamese' },
  { id: 'th', native: 'ไทย', english: 'Thai' },
  { id: 'id', native: 'Bahasa Indonesia', english: 'Indonesian' },
  { id: 'ms', native: 'Bahasa Melayu', english: 'Malay' },
  { id: 'fil', native: 'Filipino', english: 'Filipino' },
  { id: 'ja', native: '日本語', english: 'Japanese' },
  { id: 'ko', native: '한국어', english: 'Korean' },
  { id: 'zh-Hans', native: '简体中文', english: 'Chinese (Simplified)' },
  { id: 'zh-Hant', native: '繁體中文', english: 'Chinese (Traditional)' },
]

export const STORAGE_KEY = 'yaari_lang'

export function savedLang(): Lang | null {
  try {
    const v = localStorage.getItem(STORAGE_KEY)
    return isLang(v) ? v : null
  } catch {
    return null
  }
}

export function saveLang(lang: Lang) {
  try {
    localStorage.setItem(STORAGE_KEY, lang)
  } catch {
    /* private mode or storage blocked: the choice lasts for this visit */
  }
}

export const EN: FullDict = en

/** A chunk that failed to download (offline), as opposed to a language file that doesn't exist yet. */
function isNetworkError(err: unknown) {
  const e = err as { name?: string; message?: string } | null
  return e?.name === 'ChunkLoadError' || /chunk|failed to fetch|networkerror|load failed/i.test(e?.message ?? '')
}

/**
 * One set of strings in every language: English ships with the code; each other language is its own chunk,
 * downloaded only by people who use it. `load` is `(lang) => import(\`./locales/${lang}\`)` written next to the
 * files, so a new language file is picked up by the bundler with nothing to register. A language without a file
 * (or a key a file leaves out) reads in English.
 */
export interface LocaleSet<D> {
  en: D
  /** What is already here for this language (English until its file has arrived). */
  peek(lang: Lang): Partial<D> | undefined
  load(lang: Lang): Promise<Partial<D>>
}

export function localeSet<D>(english: D, load: (lang: string) => Promise<{ default?: Partial<D> }>): LocaleSet<D> {
  const ready = new Map<Lang, Partial<D>>([['en', english]])
  const pending = new Map<Lang, Promise<Partial<D>>>()
  return {
    en: english,
    peek: (lang) => ready.get(lang),
    load(lang) {
      const have = ready.get(lang)
      if (have) return Promise.resolve(have)
      let p = pending.get(lang)
      if (!p) {
        p = load(lang).then(
          (mod) => {
            const dict = mod?.default ?? {}
            ready.set(lang, dict)
            return dict
          },
          (err) => {
            pending.delete(lang)
            if (isNetworkError(err)) throw err // offline: try again next time
            ready.set(lang, {}) // no file for this language yet: English
            return {}
          },
        )
        pending.set(lang, p)
      }
      return p
    },
  }
}

export const APP_TEXT = localeSet<FullDict>(en, (lang) => import(`./locales/${lang}`))

export function loadDict(lang: Lang): Promise<Dict> {
  return APP_TEXT.load(lang)
}

export function fill(text: string, vars?: Vars) {
  if (!vars) return text
  return text.replace(/\{(\w+)\}/g, (all, name: string) => (name in vars ? String(vars[name]) : all))
}

/**
 * The BCP 47 tag for Intl (dates, times, numbers): the browser's own tag when it is the same language (en-GB,
 * es-MX, ar-EG keep their region's formats), else the UI language itself.
 */
export function intlLocale(lang: Lang, browser: readonly string[] = typeof navigator !== 'undefined' ? navigator.languages ?? [] : []): string {
  for (const raw of browser) {
    if (langOfTag(raw) !== lang) continue
    try {
      // Some systems report tags Intl rejects ("en-US@posix"): keep only valid ones.
      return Intl.getCanonicalLocales(raw.split('@')[0])[0]
    } catch {
      /* try the next one */
    }
  }
  return lang
}
