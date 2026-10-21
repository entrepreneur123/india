import { useEffect, useState } from 'react'
import { useI18n } from '@/components/i18n'
import { fill, type Lang, type Vars } from '@/lib/i18n'
import en from './locales/feed/en'

// Strings of the feed, posts, comments and profile style (033). Like the room chat's, they come with the screens
// that use them (feed, profile, post), so neither the first screen nor the shell grows.
export type FeedKey = keyof typeof en
export type FeedDict = Record<FeedKey, string>
export type FeedT = (key: FeedKey, vars?: Vars) => string

const LOADERS: Record<Exclude<Lang, 'en'>, () => Promise<FeedDict>> = {
  hi: () => import('./locales/feed/hi').then((m) => m.default),
  bn: () => import('./locales/feed/bn').then((m) => m.default),
  'pt-BR': () => import('./locales/feed/pt-BR').then((m) => m.default),
  vi: () => import('./locales/feed/vi').then((m) => m.default),
}
const loaded: Partial<Record<Lang, FeedDict>> = { en }

/** These strings in the app's language (English until another language's file has arrived). */
export function useFeedT(): { ft: FeedT; plural: (n: number, one: FeedKey, other: FeedKey) => string } {
  const { lang, locale, num } = useI18n()
  const [dict, setDict] = useState<FeedDict>(() => loaded[lang] ?? en)
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
  const ft: FeedT = (key, vars) => fill(dict[key] ?? en[key], vars)
  const rules = new Intl.PluralRules(locale)
  return { ft, plural: (n, one, other) => ft(rules.select(n) === 'one' ? one : other, { n: num(n) }) }
}
