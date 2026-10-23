import { useI18n, useLocaleSet } from '@/components/i18n'
import { fill, localeSet, pluralText, type Lang, type LocaleDict, type Vars } from '@/lib/i18n'
import type { Interest } from '@/lib/backend'
import en from './locales/en'

// Strings of the inbox, room search and invites, interests, photos and the first-run tour. They live in their own
// files (like Ludo's) so the first screen's download does not grow; only the signed-in app loads them.
export type SocialKey = keyof typeof en
/** A language file: keys it leaves out are shown in English. */
export type SocialDict = LocaleDict<SocialKey>
export type SocialT = (key: SocialKey, vars?: Vars) => string

// A language's file is found by the bundler from this pattern: adding `./locales/<lang>.ts` is all it takes.
const TEXT = localeSet<SocialDict>(en, (lang) => import(`./locales/${lang}`))

/** These strings in the app's language (English until another language's file has arrived). */
export function useSocialT(): { st: SocialT; tag: (i: Interest) => string; plural: (n: number, one: SocialKey, other: SocialKey) => string } {
  const { locale, num } = useI18n()
  const dict = useLocaleSet(TEXT)
  const st: SocialT = (key, vars) => fill(dict[key] ?? en[key], vars)
  const rules = new Intl.PluralRules(locale)
  return {
    st,
    tag: (i) => st(`tag.${i}` as SocialKey),
    plural: (n, one, other) => fill(pluralText(dict, en, rules.select(n), one, other), { n: num(n) }),
  }
}

/** Starts the download of the current language's file early (the shell calls it once). */
export function preloadSocialText(lang: Lang) {
  void TEXT.load(lang).catch(() => undefined)
}
