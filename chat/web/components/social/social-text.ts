import { useEffect, useState } from 'react'
import { useI18n } from '@/components/i18n'
import { fill, type Lang, type Vars } from '@/lib/i18n'
import type { Interest } from '@/lib/backend'
import en from './locales/en'

// Strings of the inbox, room search and invites, interests, photos and the first-run tour. They live in their own
// files (like Ludo's) so the first screen's download does not grow; only the signed-in app loads them.
export type SocialKey = keyof typeof en
export type SocialDict = Record<SocialKey, string>
export type SocialT = (key: SocialKey, vars?: Vars) => string

const LOADERS: Record<Exclude<Lang, 'en'>, () => Promise<SocialDict>> = {
  hi: () => import('./locales/hi').then((m) => m.default),
  bn: () => import('./locales/bn').then((m) => m.default),
  'pt-BR': () => import('./locales/pt-BR').then((m) => m.default),
  vi: () => import('./locales/vi').then((m) => m.default),
}
const loaded: Partial<Record<Lang, SocialDict>> = { en }

/** These strings in the app's language (English until another language's file has arrived). */
export function useSocialT(): { st: SocialT; tag: (i: Interest) => string; plural: (n: number, one: SocialKey, other: SocialKey) => string } {
  const { lang, locale, num } = useI18n()
  const [dict, setDict] = useState<SocialDict>(() => loaded[lang] ?? en)
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
  const st: SocialT = (key, vars) => fill(dict[key] ?? en[key], vars)
  const rules = new Intl.PluralRules(locale)
  return {
    st,
    tag: (i) => st(`tag.${i}` as SocialKey),
    plural: (n, one, other) => st(rules.select(n) === 'one' ? one : other, { n: num(n) }),
  }
}

/** Starts the download of the current language's file early (the shell calls it once). */
export function preloadSocialText(lang: Lang) {
  if (lang !== 'en' && !loaded[lang]) void LOADERS[lang]().then((d) => (loaded[lang] = d), () => undefined)
}
