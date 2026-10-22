import { useLocaleSet } from '@/components/i18n'
import { fill, localeSet, type LocaleDict, type Vars } from '@/lib/i18n'
import en from './locales/en'

// Strings of IDs and looks (040) and of what came with them (the room's People sheet and layout, the friends bar of an
// ended chat). Like the feed's, they come with the screens that use them, so the first screen doesn't grow.
export type LooksKey = keyof typeof en
/** A language file: keys it leaves out are shown in English. */
export type LooksDict = LocaleDict<LooksKey>
export type LooksT = (key: LooksKey, vars?: Vars) => string

// A language's file is found by the bundler from this pattern: adding `./locales/<lang>.ts` is all it takes.
const TEXT = localeSet<LooksDict>(en, (lang) => import(`./locales/${lang}`))

/** These strings in the app's language (English until another language's file has arrived). */
export function useLooksT(): { lt: LooksT } {
  const dict = useLocaleSet(TEXT)
  return { lt: (key, vars) => fill(dict[key] ?? en[key], vars) }
}
