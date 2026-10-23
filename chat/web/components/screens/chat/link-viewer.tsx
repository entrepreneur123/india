'use client'

import { createContext, useContext, useEffect, useState, type MouseEvent, type ReactNode } from 'react'
import { ExternalLink, LoaderCircle, Play, RotateCw, X } from 'lucide-react'
import { BackendError, type Backend, type LinkMeta } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { framable, hostOf, openBeside, previewLink, splitLinks } from '@/lib/links'
import { thumbnail, youTubeId } from '@/lib/youtube'
import { useI18n } from '@/components/i18n'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { TopicIcon } from '@/components/ui/topic-icons'
import { cn } from '@/lib/utils'

// Links in chat (room chat, threads, random chats): shown as links (never as HTML), a preview card under the line,
// and, for a site that allows being framed, a viewer beside the chat; any other link opens in a new browser tab.
// Previews come from the link-meta edge function, once per address while the app is open.

const MAX_CACHED = 200
const cache = new Map<string, Promise<LinkMeta | null>>()
/** Addresses whose page the viewer can show, known once their preview has loaded (a click needs the answer at once). */
const canFrame = new Set<string>()

/** The viewer can show this page (lib/links.ts framable). */
export const frames = (meta: LinkMeta | null) => !!meta?.frameable && framable(meta, location.origin) // no `location` before a preview

function metaFor(backend: Backend, url: string): Promise<LinkMeta | null> {
  const hit = cache.get(url)
  if (hit) return hit
  // link-meta reads https pages only: an http link gets its card from the address alone, without asking.
  const p = !url.startsWith('https://')
    ? Promise.resolve(null)
    : backend.linkMeta(url).then((meta) => {
        if (frames(meta)) canFrame.add(url)
        return meta
      }).catch((err: unknown) => {
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
  const meta = current ? state.meta : null
  return { meta, loading: !!url && !(current && state.done), failed: current && state.done && !state.meta, frames: frames(meta) }
}

/** Opens a link in the viewer beside the chat (absent: links open in a new tab). */
const OpenContext = createContext<((url: string) => void) | null>(null)
export const LinkOpenProvider = OpenContext.Provider

const LINK_REL = 'noopener noreferrer nofollow'

/**
 * A plain click on a page that can be framed opens the viewer; any other link, a middle click or a modifier key keeps
 * the browser's own new tab (no viewer that could only say "open it in your browser").
 */
const plain = (e: MouseEvent) => e.button === 0 && !e.metaKey && !e.ctrlKey && !e.shiftKey && !e.altKey

/** A plain click on a link to a page shown outside the app: beside it on a computer (lib/links.ts openBeside). */
export const besideClick = (url: string) => (e: MouseEvent<HTMLAnchorElement>) => {
  if (plain(e) && openBeside(url)) e.preventDefault()
}

function useOpener() {
  const open = useContext(OpenContext)
  return (url: string) => (e: MouseEvent<HTMLAnchorElement>) => {
    const video = youTubeId(url)
    // A YouTube link plays its video under it, in the same line (VideoInline); already playing, it stays as it is.
    const player = video && plain(e) ? e.currentTarget.closest('p')?.parentElement?.querySelector(`[data-video="${video}"]`) : null
    if (player) {
      e.preventDefault()
      return player.querySelector('button')?.click()
    }
    if (!open || !canFrame.has(url) || video || !plain(e)) return besideClick(url)(e)
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

/** Under a line's text: its first YouTube video, played right here; else a preview of its first link. */
export function LinkPreview({ text, className }: { text: string; className?: string }) {
  const video = splitLinks(text).map((p) => (p.kind === 'link' ? youTubeId(p.url) : null)).find(Boolean)
  if (video) return <VideoInline id={video} className={className} />
  const url = previewLink(text)
  return url ? <LinkCard url={url} className={className} /> : null
}

/**
 * A YouTube video in a chat line (youtube-nocookie.com): its picture until tapped (or its link is), then the player,
 * which plays on while the chat is open, like background music.
 */
function VideoInline({ id, className }: { id: string; className?: string }) {
  const { t } = useI18n()
  const [on, setOn] = useState(false)
  return (
    <span data-video={id} className={cn('relative block aspect-video overflow-hidden rounded-md bg-black', className)}>
      {on ? (
        <iframe
          src={`https://www.youtube-nocookie.com/embed/${id}?autoplay=1&playsinline=1&rel=0`}
          title={t('voice.ytVideo')}
          allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
          allowFullScreen
          className="absolute inset-0 size-full"
        />
      ) : (
        <button type="button" onClick={() => setOn(true)} aria-label={t('voice.ytVideo')} className="absolute inset-0 grid place-items-center">
          <img src={thumbnail(id)} alt="" loading="lazy" decoding="async" className="absolute inset-0 size-full object-cover" />
          <span className="relative grid size-12 place-items-center rounded-full bg-black/60 text-white">
            <Play className="size-6 translate-x-0.5 fill-current" aria-hidden />
          </span>
        </button>
      )}
    </span>
  )
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
        'mt-1 flex min-w-0 items-stretch overflow-hidden rounded-md bg-black/5 text-start transition-colors duration-150 hover:bg-black/10',
        className,
      )}
    >
      <span className="relative grid w-16 shrink-0 place-items-center bg-black/5">
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
 * The page beside the chat, in a frame (sandboxed, no referrer). Only framable pages open here; should the frame
 * still be refused (this app's own CSP), its card, a word on why, and "Open in browser". `label` sits over the title
 * ("Shared by Asha"); `actions` go in the header (the room's "View together"); `footer` under the framed page.
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
  const framed = frames(meta) && !cspBlocked

  return (
    <section aria-label={t('link.viewer')} className={cn('flex min-h-0 flex-col bg-card text-card-foreground', className)}>
      <header className="flex items-center gap-1 border-b border-border py-2 pe-2 ps-4">
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
          onClick={besideClick(url)}
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
            <div className="w-full max-w-sm overflow-hidden rounded-2xl bg-muted/60 text-start ring-1 ring-border">
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
              onClick={besideClick(url)}
              className="inline-flex h-11 items-center gap-2 rounded-full bg-primary px-5 text-sm font-semibold text-primary-foreground transition-colors duration-150 hover:bg-primary-hover"
            >
              <ExternalLink className="size-4.5" aria-hidden /> {t('link.openBrowser')}
            </a>
          </div>
        )}
      </div>
      {framed ? footer : null}
    </section>
  )
}

/** Phones: the viewer as a tall sheet over the chat. */
export function LinkSheet({ open, onClose, children }: { open: boolean; onClose: () => void; children: ReactNode }) {
  return (
    <Modal open={open} onClose={onClose} variant="sheet" hideHeader className="h-[90dvh] md:h-[85dvh] md:max-w-2xl">
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
