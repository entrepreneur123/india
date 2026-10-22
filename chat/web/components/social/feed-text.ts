import { useI18n, useLocaleSet } from '@/components/i18n'
import { fill, localeSet, pluralText, type LocaleDict, type Vars } from '@/lib/i18n'
import en from './locales/feed/en'

// Strings of posts, comments and profile details (033). Like the room chat's, they come with the screens that use
// them (profile, post), so neither the first screen nor the shell grows.
export type FeedKey = keyof typeof en
/** A language file: keys it leaves out are shown in English. */
export type FeedDict = LocaleDict<FeedKey>
export type FeedT = (key: FeedKey, vars?: Vars) => string

// A language's file is found by the bundler from this pattern: adding `./locales/feed/<lang>.ts` is all it takes.
const TEXT = localeSet<FeedDict>(en, (lang) => import(`./locales/feed/${lang}`))

/** These strings in the app's language (English until another language's file has arrived). */
export function useFeedT(): { ft: FeedT; plural: (n: number, one: FeedKey, other: FeedKey) => string } {
  const { locale, num } = useI18n()
  const dict = useLocaleSet(TEXT)
  const ft: FeedT = (key, vars) => fill(dict[key] ?? en[key], vars)
  const rules = new Intl.PluralRules(locale)
  return { ft, plural: (n, one, other) => fill(pluralText(dict, en, rules.select(n), one, other), { n: num(n) }) }
}
