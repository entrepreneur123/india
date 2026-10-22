'use client'

import { createContext, useContext, useEffect, useState, type MouseEvent, type ReactNode } from 'react'
import { ExternalLink, LoaderCircle, RotateCw, X } from 'lucide-react'
import { BackendError, type Backend, type LinkMeta } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { hostOf, isYouTube, previewLink, splitLinks } from '@/lib/links'
import { useI18n } from '@/components/i18n'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { TopicIcon } from '@/components/ui/topic-icons'
import { cn } from '@/lib/utils'

// Links in chat (room chat, threads, random chats): shown as links (never as HTML), a preview card under the line,
// and a viewer beside the chat: the page itself when the site allows being framed, else its card and "Open in
// browser". Previews come from the link-meta edge function, once per address while the app is open.

const MAX_CACHED = 200
const cache = new Map<string, Promise<LinkMeta | null>>()

function metaFor(backend: Backend, url: string): Promise<LinkMeta | null> {
  const hit = cache.get(url)
  if (hit) return hit
  // link-meta reads https pages only: an http link gets its card from the address alone, without asking.
  const p = !url.startsWith('https://')
    ? Promise.resolve(null)
    : backend.linkMeta(url).catch((err: unknown) => {
        // A refused address stays refused; anything else (offline, a slow site) is asked again next time.
        if (!(err instanceof BackendError && err.code === 'bad_request')) cache.delete(url)
        return null
      })
  cache.set(url, p)
  if (cache.size > MAX_CACHED) cache.delete(cache.keys().next().value!)
  return p
}

/** The link's preview: `meta` null while it loads, or when there is none (`failed`). */
export function useLinkMeta(url: string | null) {
  const backend = useBackend()
  const [state, setState] = useState<{ url: string | null; meta: LinkMeta | null; done: boolean }>({ url: null, meta: null, done: false })
  useEffect(() => {
    if (!url) return
    let alive = true
    setState((s) => (s.url === url ? s : { url, meta: null, done: false }))
    void metaFor(backend, url).then((meta) => alive && setState({ url, meta, done: true }))
    return () => {
      alive = false
    }
  }, [backend, url])
  const current = state.url === url
  return { meta: current ? state.meta : null, loading: !!url && !(current && state.done), failed: current && state.done && !state.meta }
}

/** Opens a link in the viewer beside the chat (absent: links open in a new tab). */
const OpenContext = createContext<((url: string) => void) | null>(null)
export const LinkOpenProvider = OpenContext.Provider

const LINK_REL = 'noopener noreferrer nofollow'

/** A plain click opens the viewer; a middle click or a modifier key keeps the browser's own new tab. */
function useOpener() {
  const open = useContext(OpenContext)
  return (url: string) => (e: MouseEvent<HTMLAnchorElement>) => {
    if (!open || isYouTube(url) || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return
    e.preventDefault()
    open(url)
  }
}

/**
 * The text with its http(s) addresses as links. `decorate` styles the plain parts (the room's @mentions); it gets
 * each part's offset in the whole text.
 */
export function LinkText({ text, decorate }: { text: string; decorate?: (part: string, offset: number) => ReactNode }) {
  const click = useOpener()
  let at = 0
  return (
    <>
      {splitLinks(text).map((p, i) => {
        const offset = at
        at += p.text.length
        if (p.kind === 'text') return <span key={i}>{decorate ? decorate(p.text, offset) : p.text}</span>
        return (
          <a
            key={i}
            href={p.url}
            target="_blank"
            rel={LINK_REL}
            onClick={click(p.url)}
            className="font-medium break-all underline decoration-current/50 underline-offset-2 transition-colors duration-150 hover:decoration-current"
          >
            {p.text}
          </a>
        )
      })}
    </>
  )
}

/** The preview of the first link in a line (none for YouTube links, or a line without links). */
export function LinkPreview({ text, className }: { text: string; className?: string }) {
  const url = previewLink(text)
  return url ? <LinkCard url={url} className={className} /> : null
}

/** A compact card: the page's picture, title and site. While it loads, the address stands in. */
export function LinkCard({ url, className }: { url: string; className?: string }) {
  const { t } = useI18n()
  const { meta, loading } = useLinkMeta(url)
  const click = useOpener()
  const [imgOk, setImgOk] = useState(true)
  const site = meta?.site || hostOf(url)
  const title = meta?.title || hostOf(url)
  return (
    <a
      href={url}
      target="_blank"
      rel={LINK_REL}
      onClick={click(url)}
      aria-label={t('link.preview', { site, title })}
      className={cn(
        'mt-1 flex min-w-0 items-stretch overflow-hidden rounded-md bg-black/5 text-left transition-colors duration-150 hover:bg-black/10 night:bg-white/8 night:hover:bg-white/12',
        className,
      )}
    >
      <span className="relative grid w-16 shrink-0 place-items-center bg-black/5 night:bg-white/5">
        {meta?.image && imgOk ? (
          <img src={meta.image} alt="" loading="lazy" decoding="async" referrerPolicy="no-referrer" onError={() => setImgOk(false)} className="absolute inset-0 size-full object-cover" />
        ) : loading ? (
          <LoaderCircle className="size-4 animate-spin text-bubble-meta" aria-hidden />
        ) : (
          <TopicIcon topic="globe" size={26} />
        )}
      </span>
      <span className="min-w-0 flex-1 px-2.5 py-1.5">
        <span className="block truncate text-[11.5px] leading-4 text-bubble-meta">{site}</span>
        <span className="line-clamp-2 text-[13px] leading-[1.3] font-semibold break-words">{title}</span>
        {meta?.description ? <span className="mt-0.5 line-clamp-1 text-[12px] leading-4 text-bubble-meta">{meta.description}</span> : null}
      </span>
    </a>
  )
}

/**
 * The page beside the chat. Shown in a frame when the site allows it (sandboxed, no referrer); otherwise its card,
 * a word on why, and "Open in browser". `label` sits over the title ("Shared by Asha"); `actions` go in the header
 * (the room's "Show to everyone"); `footer` under the page (a note for the room).
 */
export function LinkPanel({
  url,
  onClose,
  closeLabel,
  label,
  actions,
  footer,
  className,
}: {
  url: string
  onClose: () => void
  /** The close button's label (default: "Close page"). */
  closeLabel?: string
  label?: ReactNode
  actions?: ReactNode
  footer?: ReactNode
  className?: string
}) {
  const { t } = useI18n()
  const { meta, loading } = useLinkMeta(url)
  const [reload, setReload] = useState(0)
  const [imgOk, setImgOk] = useState(true)
  useEffect(() => setImgOk(true), [url])
  // The frame shows the page the link led to when that is still https (an http page would be blocked here).
  const src = meta?.url.startsWith('https://') ? meta.url : url
  const title = meta?.title || hostOf(url)
  const site = meta?.site || hostOf(url)
  // This app's own Content-Security-Policy (frame-src) may not allow the site: the frame would stay blank, so the
  // card and "Open in browser" show instead.
  const [cspBlocked, setCspBlocked] = useState(false)
  useEffect(() => {
    setCspBlocked(false)
    let origin = ''
    try {
      origin = new URL(src).origin
    } catch {
      return
    }
    const onViolation = (e: SecurityPolicyViolationEvent) => {
      const directive = e.effectiveDirective || e.violatedDirective
      if ((directive === 'frame-src' || directive === 'child-src' || directive === 'default-src') && e.blockedURI.startsWith(origin)) setCspBlocked(true)
    }
    document.addEventListener('securitypolicyviolation', onViolation)
    return () => document.removeEventListener('securitypolicyviolation', onViolation)
  }, [src])
  // Never this app itself in a frame (the sandbox would not hold a page of the same origin).
  const sameOrigin = (() => {
    try {
      return new URL(src).origin === location.origin
    } catch {
      return true
    }
  })()
  const framed = !!meta?.frameable && !cspBlocked && !sameOrigin

  return (
    <section aria-label={t('link.viewer')} className={cn('flex min-h-0 flex-col bg-card text-card-foreground', className)}>
      <header className="flex items-center gap-1 border-b border-border py-2 pr-2 pl-4">
        <span className="min-w-0 flex-1">
          {label ? <span className="block truncate text-[11px] font-semibold text-primary-text">{label}</span> : null}
          <span className="block truncate text-sm leading-tight font-semibold">{title}</span>
          <span className="block truncate text-xs text-muted-foreground" dir="ltr">
            {site}
          </span>
        </span>
        {actions}
        {framed ? (
          <Button size="icon-sm" variant="ghost" onClick={() => setReload((n) => n + 1)} aria-label={t('link.reload')} title={t('link.reload')}>
            <RotateCw className="size-4" aria-hidden />
          </Button>
        ) : null}
        <a
          href={url}
          target="_blank"
          rel={LINK_REL}
          aria-label={t('link.openBrowser')}
          title={t('link.openBrowser')}
          className="grid size-9 shrink-0 place-items-center rounded-full text-muted-foreground transition-colors duration-150 hover:bg-muted hover:text-foreground"
        >
          <ExternalLink className="size-4.5" aria-hidden />
        </a>
        <Button size="icon-sm" variant="ghost" onClick={onClose} aria-label={closeLabel ?? t('link.close')} title={closeLabel ?? t('link.close')}>
          <X className="size-5" aria-hidden />
        </Button>
      </header>
      <div className="relative min-h-0 flex-1 bg-background">
        {loading ? (
          <p className="absolute inset-0 flex items-center justify-center gap-2 text-sm text-muted-foreground" role="status">
            <LoaderCircle className="size-4 animate-spin" aria-hidden /> {t('link.loading')}
          </p>
        ) : framed ? (
          <iframe
            key={`${src}#${reload}`}
            src={src}
            title={title}
            sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-popups-to-escape-sandbox"
            referrerPolicy="no-referrer"
            loading="lazy"
            className="absolute inset-0 size-full border-0 bg-white"
          />
        ) : (
          <div className="flex h-full flex-col items-center justify-center gap-4 overflow-y-auto p-6 text-center">
            <div className="w-full max-w-sm overflow-hidden rounded-2xl bg-muted/60 text-left ring-1 ring-border">
              {meta?.image && imgOk ? (
                <img src={meta.image} alt="" referrerPolicy="no-referrer" onError={() => setImgOk(false)} className="aspect-[1.91/1] w-full object-cover" />
              ) : (
                <span className="grid aspect-[2.4/1] w-full place-items-center bg-muted">
                  <TopicIcon topic="globe" size={44} />
                </span>
              )}
              <span className="block px-4 py-3">
                <span className="block truncate text-xs text-muted-foreground" dir="ltr">
                  {site}
                </span>
                <span className="mt-0.5 line-clamp-2 block text-[15px] leading-snug font-semibold">{title}</span>
                {meta?.description ? <span className="mt-1 line-clamp-3 block text-[13px] leading-snug text-muted-foreground">{meta.description}</span> : null}
              </span>
            </div>
            <p className="max-w-sm text-sm leading-relaxed text-muted-foreground">{t('link.noFrame')}</p>
            <a
              href={url}
              target="_blank"
              rel={LINK_REL}
              className="inline-flex h-11 items-center gap-2 rounded-full bg-primary px-5 text-sm font-semibold text-primary-foreground transition-colors duration-150 hover:bg-primary-hover"
            >
              <ExternalLink className="size-4.5" aria-hidden /> {t('link.openBrowser')}
            </a>
          </div>
        )}
      </div>
      {footer}
    </section>
  )
}

/** Phones: the viewer as a tall sheet over the chat. */
export function LinkSheet({ open, onClose, night, children }: { open: boolean; onClose: () => void; night?: boolean; children: ReactNode }) {
  return (
    <Modal open={open} onClose={onClose} variant="sheet" hideHeader night={night} className="h-[90dvh] md:h-[85dvh] md:max-w-2xl">
      <div className="flex h-full min-h-0 flex-col [&>section]:flex-1">{children}</div>
    </Modal>
  )
}

/** Wide screens (1024 px and up): the viewer is a column beside the chat; phones get the sheet. */
export function useWideScreen(query = '(min-width: 1024px)') {
  const [wide, setWide] = useState(false)
  useEffect(() => {
    const mq = window.matchMedia(query)
    const update = () => setWide(mq.matches)
    update()
    mq.addEventListener('change', update)
    return () => mq.removeEventListener('change', update)
  }, [query])
  return wide
}
