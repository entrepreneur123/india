'use client'

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  useSyncExternalStore,
  type ReactNode,
} from 'react'
import { Languages, LoaderCircle } from 'lucide-react'
import { useBackend } from '@/lib/backend-context'
import {
  cacheKey,
  defaultTarget,
  isTarget,
  shouldAutoTranslate,
  TranslateStore,
  type Entry,
  type Target,
  type TranslateKind,
} from '@/lib/translate'
import { useI18n } from '@/components/i18n'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'

const SETTINGS_KEY = 'yaari_translate'
/** When the server last said it can't translate yet (503): auto-translate stays off for a while, quietly. */
const OFF_KEY = 'yaari_translate_off'
const OFF_FOR_MS = 6 * 60 * 60 * 1000

type Saved = { auto: boolean; target: Target | null }

function readSaved(): Saved {
  try {
    const raw = JSON.parse(localStorage.getItem(SETTINGS_KEY) ?? 'null') as Partial<Saved> | null
    return { auto: raw?.auto !== false, target: isTarget(raw?.target) ? raw!.target! : null }
  } catch {
    return { auto: true, target: null }
  }
}

function writeSaved(s: Saved) {
  try {
    localStorage.setItem(SETTINGS_KEY, JSON.stringify(s))
  } catch {
    /* storage blocked: the choice lasts for this visit */
  }
}

function offRecently() {
  try {
    return Date.now() - Number(localStorage.getItem(OFF_KEY) ?? 0) < OFF_FOR_MS
  } catch {
    return false
  }
}

function markOff(off: boolean) {
  try {
    if (off) localStorage.setItem(OFF_KEY, String(Date.now()))
    else localStorage.removeItem(OFF_KEY)
  } catch {
    /* nothing to remember it in */
  }
}

export interface TranslateApi {
  /** False once the server said translation isn't set up yet (503): every translate control is hidden. */
  available: boolean
  /** The "Translate chats automatically" setting. */
  autoSetting: boolean
  setAuto: (on: boolean) => void
  /** Whether lines are translated by themselves right now (the setting, and the server is up, and today's limit). */
  auto: boolean
  /** The language lines are translated into. */
  target: Target
  /** The chosen one, or null = the app's language. */
  targetSetting: Target | null
  appTarget: Target
  setTarget: (t: Target | null) => void
  store: TranslateStore
  request: (kind: TranslateKind, id: number, opts: { manual: boolean; wanted?: () => boolean }) => void
  /** Calls `fn` whenever `el` comes into or leaves the screen (one observer for every line). */
  watch: (el: Element, fn: (shown: boolean) => void) => () => void
}

const Ctx = createContext<TranslateApi | null>(null)

/** Chat translation for the signed-in app: settings, the shared cache and queue, the one-time notices. */
export function TranslateProvider({ children }: { children: ReactNode }) {
  const backend = useBackend()
  const toast = useToast()
  const { t, lang } = useI18n()
  const [saved, setSaved] = useState<Saved>(() => readSaved())
  const [available, setAvailable] = useState(() => !offRecently())
  const [limited, setLimited] = useState(false)
  const told = useRef(new Set<string>())
  // The store outlives renders; what it reports goes through refs so it never acts on an old render's values.
  const say = useRef({ toast, t })
  say.current = { toast, t }

  const [store] = useState(
    () =>
      new TranslateStore((code, manual) => {
        const { toast, t } = say.current
        const once = (key: string, text: string, tone: 'info' | 'error' = 'info') => {
          if (told.current.has(key)) return
          told.current.add(key)
          toast.show(text, tone)
        }
        if (code === 'translate_unavailable') {
          store.dropQueued()
          markOff(true)
          setAvailable(false)
          once('off', t('tr.unavailable'))
        } else if (code === 'slow_down') {
          store.dropQueued()
          setLimited(true)
          once('limit', t('tr.limit'))
        } else if (manual) {
          if (code === 'cannot_translate') toast.show(t('tr.cannot'), 'error')
          else if (code === 'too_long') toast.show(t('tr.tooLong'), 'error')
          else toast.show(t('tr.failed'), 'error')
        }
      }),
  )

  const appTarget = useMemo(() => defaultTarget(lang, typeof navigator === 'undefined' ? [] : navigator.languages ?? []), [lang])
  const target = saved.target ?? appTarget
  const auto = saved.auto && available && !limited

  useEffect(() => {
    if (!auto) store.dropQueued()
  }, [auto, store])

  const targetRef = useRef(target)
  targetRef.current = target
  const request = useCallback<TranslateApi['request']>(
    (kind, id, opts) => {
      const to = targetRef.current
      store.request(cacheKey(kind, id, to), () => backend.translate(kind, id, to), opts)
    },
    [backend, store],
  )

  // One IntersectionObserver for every line on screen.
  const observer = useRef<{ io: IntersectionObserver; fns: Map<Element, (shown: boolean) => void> } | null>(null)
  const watch = useCallback<TranslateApi['watch']>((el, fn) => {
    if (typeof IntersectionObserver === 'undefined') {
      fn(true)
      return () => undefined
    }
    if (!observer.current) {
      const fns = new Map<Element, (shown: boolean) => void>()
      const io = new IntersectionObserver((entries) => {
        for (const e of entries) fns.get(e.target)?.(e.isIntersecting)
      })
      observer.current = { io, fns }
    }
    const { io, fns } = observer.current
    fns.set(el, fn)
    io.observe(el)
    return () => {
      fns.delete(el)
      io.unobserve(el)
    }
  }, [])
  useEffect(() => () => observer.current?.io.disconnect(), [])

  const value = useMemo<TranslateApi>(
    () => ({
      available,
      autoSetting: saved.auto,
      setAuto: (on) => {
        const next = { ...saved, auto: on }
        writeSaved(next)
        setSaved(next)
      },
      auto,
      target,
      targetSetting: saved.target,
      appTarget,
      setTarget: (to) => {
        const next = { ...saved, target: to }
        writeSaved(next)
        setSaved(next)
      },
      store,
      request,
      watch,
    }),
    [available, saved, auto, target, appTarget, store, request, watch],
  )
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useTranslate(): TranslateApi | null {
  return useContext(Ctx)
}

const NONE = () => () => undefined

/** Sent (bubbling) from a line whose height changed because of its translation; see useFollowLines. */
export const LINE_GROW = 'yaari:linegrow'

/**
 * For a chat list that sticks to its newest line: when a line's translation changes its height, scroll down again
 * if the reader was at the bottom (`atBottom`).
 */
export function useFollowLines(list: React.RefObject<HTMLElement | null>, atBottom: React.RefObject<boolean>) {
  useEffect(() => {
    const el = list.current
    if (!el) return
    const grow = () => {
      if (atBottom.current) el.scrollTop = el.scrollHeight
    }
    el.addEventListener(LINE_GROW, grow)
    return () => el.removeEventListener(LINE_GROW, grow)
  }, [list, atBottom])
}

export interface LineTranslation {
  /** This line can be translated (someone else's saved text, and the server is up). */
  can: boolean
  /** It looks like another language than mine. */
  foreign: boolean
  entry: Entry | undefined
  /** The translation is what's shown now. */
  showing: boolean
  /** A translation exists and the original is shown (the reader chose it). */
  hidden: boolean
  target: Target
  translate: () => void
  showOriginal: () => void
  showTranslation: () => void
  /** Put on the line's element: automatic translation waits until it is on screen. */
  ref: (el: Element | null) => void
}

/**
 * One chat line's translation. Lines that look foreign are translated by themselves once they are on screen (when
 * the setting is on); any other line when the reader asks. Nothing is sent again for a line already asked for.
 */
export function useLineTranslation(kind: TranslateKind, id: number, text: string | null | undefined, mine: boolean): LineTranslation {
  const api = useTranslate()
  const target = api?.target ?? 'en'
  const key = cacheKey(kind, id, target)
  const store = api?.store
  const subscribe = useCallback((fn: () => void) => (store ? store.subscribe(fn) : NONE()), [store])
  const entry = useSyncExternalStore(subscribe, () => store?.get(key), () => undefined)
  const can = !!api?.available && !mine && !!text && text.trim().length > 0 && Number.isSafeInteger(id) && id > 0
  const foreign = useMemo(() => can && shouldAutoTranslate(text, target), [can, text, target])
  const [asked, setAsked] = useState(false)
  const [original, setOriginal] = useState(false)

  // On screen? (only matters for automatic translation)
  const [el, setEl] = useState<Element | null>(null)
  const [shown, setShown] = useState(false)
  const shownRef = useRef(false)
  const watch = api?.watch
  const autoWanted = !!api?.auto && foreign
  useEffect(() => {
    if (!el || !watch || !autoWanted) return
    return watch(el, (on) => {
      shownRef.current = on
      setShown(on)
    })
  }, [el, watch, autoWanted])

  const request = api?.request
  const empty = !entry
  useEffect(() => {
    if (autoWanted && shown && empty && request) request(kind, id, { manual: false, wanted: () => shownRef.current })
  }, [autoWanted, shown, empty, request, kind, id])

  // A tap that comes back "already in your language" changes nothing on screen: say so (once per tap).
  const toast = useToast()
  const { t } = useI18n()
  const tellSame = useRef(false)
  useEffect(() => {
    if (!tellSame.current || entry?.status === 'loading' || !entry) return
    tellSame.current = false
    if (entry.status === 'done' && entry.same) toast.show(t('tr.same'))
  }, [entry, toast, t])

  const done = entry?.status === 'done' && !entry.same
  const wants = asked || autoWanted
  const showing = done && wants && !original
  const status = entry?.status
  // The line changes height (a translation, "Translating…"): a chat following its newest line keeps following it.
  useLayoutEffect(() => {
    if (el && status) el.dispatchEvent(new Event(LINE_GROW, { bubbles: true }))
  }, [el, status, showing])

  return {
    can,
    foreign,
    entry,
    showing,
    hidden: done && wants && original,
    target,
    translate: () => {
      if (!can || !request) return
      setAsked(true)
      setOriginal(false)
      if (entry?.status === 'done') {
        if (entry.same) toast.show(t('tr.same'))
        return
      }
      tellSame.current = true
      request(kind, id, { manual: true })
    },
    showOriginal: () => setOriginal(true),
    showTranslation: () => {
      setAsked(true)
      setOriginal(false)
    },
    ref: setEl,
  }
}

/**
 * Under a line: "Translated · Show original" while the translation shows, "Show translation" after, "Translating…"
 * while it is on its way, and a small "Translate" for a foreign-looking line nobody has translated yet.
 */
export function TranslateFooter({ tr, className }: { tr: LineTranslation; className?: string }) {
  const { t } = useI18n()
  if (!tr.can) return null
  const base = cn('mt-0.5 flex items-center gap-1 text-[11.5px] leading-4 text-bubble-meta', className)
  if (tr.showing)
    return (
      <span className={base}>
        <Languages className="size-3 shrink-0" aria-hidden />
        <span>{t('tr.translated')}</span>
        <span aria-hidden>·</span>
        <button type="button" onClick={tr.showOriginal} className="font-semibold hover:underline">
          {t('tr.showOriginal')}
        </button>
      </span>
    )
  if (tr.hidden)
    return (
      <span className={base}>
        <Languages className="size-3 shrink-0" aria-hidden />
        <button type="button" onClick={tr.showTranslation} className="font-semibold hover:underline">
          {t('tr.showTranslation')}
        </button>
      </span>
    )
  if (tr.entry?.status === 'loading')
    return (
      <span className={base}>
        <LoaderCircle className="size-3 shrink-0 animate-spin motion-reduce:animate-none" aria-hidden />
        <span>{t('tr.translating')}</span>
      </span>
    )
  // Already in my language: nothing to offer.
  if (tr.entry?.status === 'done') return null
  if (!tr.foreign) return null
  return (
    <span className={base}>
      <button type="button" onClick={tr.translate} className="inline-flex items-center gap-1 font-semibold hover:underline">
        <Languages className="size-3 shrink-0" aria-hidden />
        {t('tr.translate')}
      </button>
    </span>
  )
}

/** The words to show for a line: its translation while that is chosen, else what was written. */
export function shownText(tr: LineTranslation, text: string) {
  return tr.showing && tr.entry?.status === 'done' ? tr.entry.text : text
}
