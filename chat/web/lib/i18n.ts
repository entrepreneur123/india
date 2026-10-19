import en from './locales/en'

export type Lang = 'en' | 'hi' | 'bn' | 'pt-BR' | 'vi'
export type TKey = keyof typeof en
/** A complete translation: TypeScript refuses a language file with a missing (or unknown) key. */
export type Dict = Record<TKey, string>
export type Vars = Record<string, string | number>
export type T = (key: TKey, vars?: Vars) => string

/** In the order shown in Settings; names are written in their own language. */
export const LANGS: { id: Lang; native: string; english: string }[] = [
  { id: 'en', native: 'English', english: 'English' },
  { id: 'hi', native: 'हिन्दी', english: 'Hindi' },
  { id: 'bn', native: 'বাংলা', english: 'Bengali' },
  { id: 'pt-BR', native: 'Português (Brasil)', english: 'Portuguese' },
  { id: 'vi', native: 'Tiếng Việt', english: 'Vietnamese' },
]

export const STORAGE_KEY = 'yaari_lang'

export function isLang(v: unknown): v is Lang {
  return LANGS.some((l) => l.id === v)
}

/** First supported language in the browser's list: bn-BD/bn-IN → bn, pt-* → pt-BR, vi → vi, hi → hi, else en. */
export function detectLang(list: readonly string[]): Lang {
  for (const tag of list) {
    const base = tag.toLowerCase().split('-')[0]
    if (base === 'bn') return 'bn'
    if (base === 'pt') return 'pt-BR'
    if (base === 'vi') return 'vi'
    if (base === 'hi') return 'hi'
    if (base === 'en') return 'en'
  }
  return 'en'
}

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

export const EN: Dict = en

// English ships with the app; the others are separate chunks, downloaded only by people who use them.
const LOADERS: Record<Exclude<Lang, 'en'>, () => Promise<Dict>> = {
  hi: () => import('./locales/hi').then((m) => m.default),
  bn: () => import('./locales/bn').then((m) => m.default),
  'pt-BR': () => import('./locales/pt-BR').then((m) => m.default),
  vi: () => import('./locales/vi').then((m) => m.default),
}

export function loadDict(lang: Lang): Promise<Dict> {
  return lang === 'en' ? Promise.resolve(EN) : LOADERS[lang]()
}

export function fill(text: string, vars?: Vars) {
  if (!vars) return text
  return text.replace(/\{(\w+)\}/g, (all, name: string) => (name in vars ? String(vars[name]) : all))
}

/** The BCP 47 tag for Intl (dates, times, numbers). English keeps the browser's region (en-IN, en-GB…). */
export function intlLocale(lang: Lang): string {
  if (lang !== 'en') return lang
  if (typeof navigator !== 'undefined') {
    for (const raw of navigator.languages ?? []) {
      if (!raw.toLowerCase().startsWith('en')) continue
      try {
        // Some systems report tags Intl rejects ("en-US@posix"): keep only valid ones.
        return Intl.getCanonicalLocales(raw.split('@')[0])[0]
      } catch {
        /* try the next one */
      }
    }
  }
  return 'en-IN'
}
