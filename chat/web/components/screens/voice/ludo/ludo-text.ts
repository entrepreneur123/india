import { useEffect, useState } from 'react'
import { useI18n } from '@/components/i18n'
import { fill, type Lang, type Vars } from '@/lib/i18n'
import { COLOURS } from '@/lib/ludo'
import en from './locales/en'

export type LudoKey = keyof typeof en
export type LudoDict = Record<LudoKey, string>
export type LudoT = (key: LudoKey, vars?: Vars) => string

const LOADERS: Record<Exclude<Lang, 'en'>, () => Promise<LudoDict>> = {
  hi: () => import('./locales/hi').then((m) => m.default),
  bn: () => import('./locales/bn').then((m) => m.default),
  'pt-BR': () => import('./locales/pt-BR').then((m) => m.default),
  vi: () => import('./locales/vi').then((m) => m.default),
}
const loaded: Partial<Record<Lang, LudoDict>> = { en }

/** Ludo's strings in the app's language (English until another language's file has arrived). */
export function useLudoT(): { lt: LudoT; colour: (seat: number, inSentence?: boolean) => string } {
  const { lang, locale } = useI18n()
  const [dict, setDict] = useState<LudoDict>(() => loaded[lang] ?? en)
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
  const lt: LudoT = (key, vars) => fill(dict[key] ?? en[key], vars)
  // In the middle of a sentence a colour is written in lower case ("a green token").
  const colour = (seat: number, inSentence = false) => {
    const word = lt(COLOURS[seat])
    return inSentence ? word.toLocaleLowerCase(locale) : word
  }
  return { lt, colour }
}
