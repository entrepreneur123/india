'use client'

import { createContext, Fragment, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import {
  detectLang,
  EN,
  fill,
  intlLocale,
  loadDict,
  saveLang,
  savedLang,
  type Dict,
  type Lang,
  type T,
  type TKey,
} from '@/lib/i18n'
import { clockTime, compactNumber, errorKey, timeAgo } from '@/lib/format'

export interface I18n {
  lang: Lang
  /** BCP 47 tag for Intl. */
  locale: string
  setLang: (lang: Lang) => void
  t: T
  /** Picks the ".one" or ".other" form for this count, with {n} filled in. */
  plural: (n: number, one: TKey, other: TKey) => string
  timeAgo: (at: number) => string
  clock: (at: number) => string
  num: (n: number) => string
  errorText: (err: unknown) => string
}

const I18nContext = createContext<I18n | null>(null)

function browserLang(): Lang {
  const list = navigator.languages?.length ? navigator.languages : [navigator.language]
  return detectLang(list)
}

/**
 * Language for the whole app: the saved choice, else the browser's languages on first visit. Until the
 * dictionary is ready (non-English ones are separate downloads) `fallback` is shown.
 */
export function I18nProvider({ children, fallback }: { children: ReactNode; fallback: ReactNode }) {
  const [state, setState] = useState<{ lang: Lang; dict: Dict } | null>(null)

  useEffect(() => {
    let alive = true
    const lang = savedLang() ?? browserLang()
    loadDict(lang).then(
      (dict) => alive && setState({ lang, dict }),
      () => alive && setState({ lang: 'en', dict: EN }), // offline while fetching the language file
    )
    return () => {
      alive = false
    }
  }, [])

  const lang = state?.lang
  useEffect(() => {
    if (lang) document.documentElement.lang = lang
  }, [lang])

  const setLang = useCallback((next: Lang) => {
    saveLang(next)
    loadDict(next).then(
      (dict) => setState({ lang: next, dict }),
      () => undefined,
    )
  }, [])

  const value = useMemo<I18n | null>(() => {
    if (!state) return null
    const { dict } = state
    const locale = intlLocale(state.lang)
    const t: T = (key, vars) => fill(dict[key] ?? EN[key], vars)
    const rules = new Intl.PluralRules(locale)
    return {
      lang: state.lang,
      locale,
      setLang,
      t,
      plural: (n, one, other) => t(rules.select(n) === 'one' ? one : other, { n: compactNumber(n, locale) }),
      timeAgo: (at) => timeAgo(at, locale, t('time.now')),
      clock: (at) => clockTime(at, locale),
      num: (n) => compactNumber(n, locale),
      errorText: (err) => t(errorKey(err)),
    }
  }, [state, setLang])

  if (!value) return <>{fallback}</>
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>
}

export function useI18n(): I18n {
  const ctx = useContext(I18nContext)
  if (!ctx) throw new Error('useI18n must be used inside <I18nProvider>')
  return ctx
}

/**
 * Renders a translated string with markup: `[words]` go through `mark`, `{name}` placeholders are replaced by
 * React nodes. Text stays text (never HTML).
 */
export function Rich({
  text,
  mark,
  nodes,
}: {
  text: string
  /** Renders each [bracketed] part; `index` counts them from 0 in the order they appear. */
  mark?: (inner: string, index: number) => ReactNode
  nodes?: Record<string, ReactNode>
}) {
  const parts = text.split(/(\[[^\]]+\]|\{\w+\})/g).filter(Boolean)
  let marked = 0
  return (
    <>
      {parts.map((p, i) => {
        if (p.startsWith('[') && p.endsWith(']')) return <Fragment key={i}>{mark ? mark(p.slice(1, -1), marked++) : p.slice(1, -1)}</Fragment>
        const name = /^\{(\w+)\}$/.exec(p)?.[1]
        if (name && nodes && name in nodes) return <Fragment key={i}>{nodes[name]}</Fragment>
        return <Fragment key={i}>{p}</Fragment>
      })}
    </>
  )
}
