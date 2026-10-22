import { useI18n, useLocaleSet } from '@/components/i18n'
import { fill, localeSet, pluralText, type LocaleDict, type Vars } from '@/lib/i18n'
import en from './locales/en'

// Strings of identity verification, badges' facts, manners and chat ratings (035). Like the feed's, they come with
// the screens that use them (the verification and rating sheets, Me, profiles), so the first screen doesn't grow.
export type TrustKey = keyof typeof en
/** A language file: keys it leaves out are shown in English. */
export type TrustDict = LocaleDict<TrustKey>
export type TrustT = (key: TrustKey, vars?: Vars) => string

// A language's file is found by the bundler from this pattern: adding `./locales/<lang>.ts` is all it takes.
const TEXT = localeSet<TrustDict>(en, (lang) => import(`./locales/${lang}`))

/** These strings in the app's language (English until another language's file has arrived). */
export function useTrustT(): { tt: TrustT; plural: (n: number, one: TrustKey, other: TrustKey) => string } {
  const { locale, num } = useI18n()
  const dict = useLocaleSet(TEXT)
  const tt: TrustT = (key, vars) => fill(dict[key] ?? en[key], vars)
  const rules = new Intl.PluralRules(locale)
  return { tt, plural: (n, one, other) => fill(pluralText(dict, en, rules.select(n), one, other), { n: num(n) }) }
}
