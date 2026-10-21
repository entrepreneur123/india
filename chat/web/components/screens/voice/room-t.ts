import { useEffect, useState } from 'react'
import { useI18n } from '@/components/i18n'
import { fill, type Lang, type Vars } from '@/lib/i18n'
import en from './locales/en'

export type RoomKey = keyof typeof en
export type RoomDict = Record<RoomKey, string>
export type RoomT = (key: RoomKey, vars?: Vars) => string

const LOADERS: Record<Exclude<Lang, 'en'>, () => Promise<RoomDict>> = {
  hi: () => import('./locales/hi').then((m) => m.default),
  bn: () => import('./locales/bn').then((m) => m.default),
  'pt-BR': () => import('./locales/pt-BR').then((m) => m.default),
  vi: () => import('./locales/vi').then((m) => m.default),
}
const loaded: Partial<Record<Lang, RoomDict>> = { en }

/** The room chat's strings (replies, mentions, polls, the player strip) in the app's language (English until it arrives). */
export function useRoomT(): { rt: RoomT; votes: (n: number) => string } {
  const { lang, locale, num } = useI18n()
  const [dict, setDict] = useState<RoomDict>(() => loaded[lang] ?? en)
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
  const rt: RoomT = (key, vars) => fill(dict[key] ?? en[key], vars)
  const rules = new Intl.PluralRules(locale)
  return { rt, votes: (n) => rt(rules.select(n) === 'one' ? 'votes.one' : 'votes.other', { n: num(n) }) }
}
