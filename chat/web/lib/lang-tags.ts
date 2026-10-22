// Pure language-tag helpers (no imports), shared by lib/i18n.ts and lib/translate.ts and unit-tested directly.

/** The app's UI languages (BCP 47). A language works as soon as its file `lib/locales/<id>.ts` exists. */
export const LANG_IDS = [
  'en', 'hi', 'bn', 'ur', 'pa', 'mr', 'gu', 'ta', 'te', 'kn', 'ml', 'ne',
  'pt-BR', 'es', 'fr', 'de', 'it', 'nl', 'pl', 'ro', 'tr', 'ru', 'uk',
  'ar', 'fa', 'he', 'sw',
  'vi', 'th', 'id', 'ms', 'fil', 'ja', 'ko', 'zh-Hans', 'zh-Hant',
] as const
export type Lang = (typeof LANG_IDS)[number]

/** Written right to left: <html dir="rtl"> and the layout mirrors. */
const RTL = new Set<Lang>(['ar', 'ur', 'fa', 'he'])
export const isRtl = (lang: Lang) => RTL.has(lang)

export function isLang(v: unknown): v is Lang {
  return typeof v === 'string' && (LANG_IDS as readonly string[]).includes(v)
}


/** Chinese written with traditional characters: Taiwan, Hong Kong, Macau, or an explicit Hant script. */
export function isTraditionalChinese(tag: string) {
  const parts = tag.split(/[-_]/).map((p) => p.toLowerCase())
  if (parts.includes('hant')) return true
  if (parts.includes('hans')) return false
  return parts.some((p) => p === 'tw' || p === 'hk' || p === 'mo')
}

/** Old or alternative codes browsers still report. */
const ALIASES: Record<string, Lang> = { iw: 'he', in: 'id', tl: 'fil', pt: 'pt-BR', zsm: 'ms', pes: 'fa', prs: 'fa', swh: 'sw' }

/** The UI language for one browser tag ("pt-PT" → pt-BR, "zh-TW" → zh-Hant, "es-MX" → es), or null. */
export function langOfTag(tag: string): Lang | null {
  const base = tag.toLowerCase().split(/[-_]/)[0]
  if (!base) return null
  if (base === 'zh') return isTraditionalChinese(tag) ? 'zh-Hant' : 'zh-Hans'
  const alias = ALIASES[base]
  if (alias) return alias
  return isLang(base) ? base : null
}

/** The first browser language the app has; English when none. */
export function detectLang(list: readonly string[]): Lang {
  for (const tag of list) {
    const lang = langOfTag(tag)
    if (lang) return lang
  }
  return 'en'
}

/**
 * The words for a count: the language's own form for its plural category (`rules.select(n)`), falling back to
 * "other", then to English. `one` and `other` are the "x.one" / "x.other" keys.
 */
export function pluralText(dict: Partial<Record<string, string>>, english: Record<string, string>, category: string, one: string, other: string): string {
  if (category === 'one') return dict[one] ?? english[one]
  const base = one.endsWith('.one') ? one.slice(0, -4) : null
  const own = base && category !== 'other' ? dict[`${base}.${category}`] : undefined
  return own ?? dict[other] ?? english[other]
}
