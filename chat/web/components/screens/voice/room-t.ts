import { useI18n, useLocaleSet } from '@/components/i18n'
import { fill, localeSet, pluralText, type LocaleDict, type Vars } from '@/lib/i18n'
import en from './locales/en'

export type RoomKey = keyof typeof en
/** A language file: keys it leaves out are shown in English. */
export type RoomDict = LocaleDict<RoomKey>
export type RoomT = (key: RoomKey, vars?: Vars) => string

// A language's file is found by the bundler from this pattern: adding `./locales/<lang>.ts` is all it takes.
const TEXT = localeSet<RoomDict>(en, (lang) => import(`./locales/${lang}`))

/** The room chat's strings (replies, mentions, polls, the player strip) in the app's language (English until it arrives). */
export function useRoomT(): { rt: RoomT; votes: (n: number) => string } {
  const { locale, num } = useI18n()
  const dict = useLocaleSet(TEXT)
  const rt: RoomT = (key, vars) => fill(dict[key] ?? en[key], vars)
  const rules = new Intl.PluralRules(locale)
  return { rt, votes: (n) => fill(pluralText(dict, en, rules.select(n), 'votes.one', 'votes.other'), { n: num(n) }) }
}
