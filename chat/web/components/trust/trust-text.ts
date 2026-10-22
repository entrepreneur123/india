import { useEffect, useState } from 'react'
import { useI18n } from '@/components/i18n'
import { fill, type Lang, type Vars } from '@/lib/i18n'
import en from './locales/en'

// Strings of identity verification, badges' facts, manners and chat ratings (035). Like the feed's, they come with
// the screens that use them (the verification and rating sheets, Me, profiles), so the first screen doesn't grow.
export type TrustKey = keyof typeof en
export type TrustDict = Record<TrustKey, string>
export type TrustT = (key: TrustKey, vars?: Vars) => string

const LOADERS: Record<Exclude<Lang, 'en'>, () => Promise<TrustDict>> = {
  hi: () => import('./locales/hi').then((m) => m.default),
  bn: () => import('./locales/bn').then((m) => m.default),
  'pt-BR': () => import('./locales/pt-BR').then((m) => m.default),
  vi: () => import('./locales/vi').then((m) => m.default),
}
const loaded: Partial<Record<Lang, TrustDict>> = { en }

/** These strings in the app's language (English until another language's file has arrived). */
export function useTrustT(): { tt: TrustT; plural: (n: number, one: TrustKey, other: TrustKey) => string } {
  const { lang, locale, num } = useI18n()
  const [dict, setDict] = useState<TrustDict>(() => loaded[lang] ?? en)
  useEffect(() => {
    if (lang === 'en') return setDict(en)
    let alive = true
    LOADERS[lang]().then(
      (d) => {
        loaded[lang] = d
        if (alive) setDict(d)
      },
      () => undefined,
    )
    return () => {
      alive = false
    }
  }, [lang])
  const tt: TrustT = (key, vars) => fill(dict[key] ?? en[key], vars)
  const rules = new Intl.PluralRules(locale)
  return { tt, plural: (n, one, other) => tt(rules.select(n) === 'one' ? one : other, { n: num(n) }) }
}
