'use client'

import * as m from 'motion/react-m'
import { useState, type CSSProperties, type ReactNode } from 'react'
import { ChevronRight, Copy, Eye } from 'lucide-react'
import type { Message } from '@/lib/backend'
import { savedFlag } from '@/lib/flags'
import { useI18n } from '@/components/i18n'
import { Modal } from '@/components/ui/modal'
import { useToast } from '@/components/ui/toast'
import { PhotoViewer } from '@/components/ui/photo-viewer'
import { bubbleOf } from '@/lib/looks'
import { cn } from '@/lib/utils'
import { LinkPreview, LinkText } from './link-viewer'
import { MessageMenu, useMenuGesture, type MenuAction } from './message-menu'

/** A line's Copy action (the menu of a chat bubble or a room line). */
export function useLineActions(text: string | null | undefined): MenuAction[] {
  const { t } = useI18n()
  const toast = useToast()
  if (!text || typeof navigator === 'undefined' || !navigator.clipboard) return []
  return [
    {
      icon: <Copy className="size-4.5" aria-hidden />,
      label: t('tr.copy'),
      onClick: () => void navigator.clipboard.writeText(text).then(() => toast.show(t('tr.copied'), 'success'), () => undefined),
    },
  ]
}

/**
 * 9 (the owner: "chats look like a cheap chat app"): bubbles as in today's messengers (Instagram, iMessage,
 * Telegram) — soft fills on a plain page, no tails or shadows; a run of lines from one person sits close together with
 * tighter inner corners and their photo beside its last line; the time only where the talk pauses (each line's time
 * on hover), "Read" / "Sent" under my latest line only. Long press / right click opens the line's menu.
 */
export function MessageBubble({ message, first, last, time, status, skin, avatar }: {
  message: Message
  /** First / last line of a run (one sender, minutes apart). */
  first: boolean
  last: boolean
  /** The time under this line (the talk pauses after it). */
  time: boolean
  /** Under my latest line. */
  status?: 'read' | 'sent' | null
  skin?: string | null
  /** Theirs: the face beside the run's last line (a gap keeps the others in line). */
  avatar?: ReactNode
}) {
  const { t, clock } = useI18n()
  const mine = message.fromMe
  const actions = useLineActions(message.text)
  const [menu, setMenu] = useState(false)
  const gesture = useMenuGesture(actions.length ? () => setMenu(true) : null)
  return (
    <m.li
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ type: 'tween', duration: 0.15, ease: [0.2, 0, 0, 1] }}
      className={cn(
        'relative flex flex-col pointer-coarse:select-none pointer-coarse:[-webkit-touch-callout:none]',
        mine ? 'items-end' : 'items-start',
        first ? 'mt-3' : 'mt-0.5',
      )}
      {...gesture}
    >
      <div className={cn('flex max-w-[82%] items-end gap-2 md:max-w-[68%]', mine && 'flex-row-reverse')}>
        {mine ? null : <span className="w-7 shrink-0">{last ? avatar : null}</span>}
        <Bubble mine={mine} first={first} last={last} skin={skin} title={clock(message.at)} className={message.media ? 'overflow-hidden' : 'px-3.5 py-2'}>
          {message.media ? <MediaBubble media={message.media} mine={mine} meta={null} /> : null}
          {message.text ? (
            // User text is always rendered as text (React escapes it), never as HTML; its links are plain <a> links.
            <>
              <LinkPreview text={message.text} className={cn('mb-1.5 w-72 max-w-full', message.media && 'mx-1.5 mt-1.5')} />
              <p dir="auto" className={cn('text-start text-[15px] leading-[1.4] break-words whitespace-pre-wrap', message.media && 'px-3.5 py-2')}>
                <LinkText text={fold(message.text)} />
              </p>
              <SeeAll text={message.text} className={cn(message.media && 'mx-3.5 mb-2 w-auto')} />
            </>
          ) : null}
        </Bubble>
      </div>
      {time || status ? (
        <span className={cn('mt-1 flex items-center gap-1 px-1 text-[11px] leading-none text-muted-foreground tabular-nums', !mine && 'ps-10')}>
          {time ? clock(message.at) : null}
          {time && status ? <span aria-hidden>·</span> : null}
          {status ? <span className={cn(status === 'read' && 'font-semibold text-primary-text')}>{t(status === 'read' ? 'bubble.read' : 'bubble.sent')}</span> : null}
        </span>
      ) : null}
      {menu ? <MessageMenu actions={actions} label={t('tr.actions')} side={mine ? 'end' : 'start'} onClose={() => setMenu(false)} /> : null}
    </m.li>
  )
}

/** Over 500 characters a line is folded (22): the bubble shows the start, "See all" opens the whole text. */
const FOLD = 500
export const fold = (text: string) => (text.length > FOLD ? `${Array.from(text).slice(0, FOLD).join('')}…` : text)

/** The "See all" link under a folded line, and the sheet with the whole text (its links still work). */
export function SeeAll({ text, className }: { text: string; className?: string }) {
  const { t } = useI18n()
  const [open, setOpen] = useState(false)
  if (text.length <= FOLD) return null
  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className={cn('mt-1.5 flex w-full items-center justify-between gap-2 border-t border-current/15 pt-1.5 text-[13px] font-semibold', className)}
      >
        {t('chat.seeAll')}
        <ChevronRight className="size-4 rtl:-scale-x-100" aria-hidden />
      </button>
      <Modal open={open} onClose={() => setOpen(false)} variant="sheet" title={t('chat.seeAll')} className="md:max-w-lg">
        <p dir="auto" className="max-h-[65dvh] overflow-y-auto pb-2 text-start text-[15px] leading-relaxed break-words whitespace-pre-wrap select-text scrollbar-thin">
          <LinkText text={text} />
        </p>
      </Modal>
    </>
  )
}

/**
 * Shared bubble shell (chats, group chats, rooms, the Looks preview): a soft rounded fill; a run of lines keeps its
 * outer corners round and tightens the inner ones (`first` / `last` of the run). `skin`: the sender's chat bubble
 * (062), drawn over the plain colours, its text and time in its own colours.
 */
export function Bubble({ mine, first = true, last = true, skin, title, className, children }: {
  mine: boolean
  first?: boolean
  last?: boolean
  skin?: string | null
  title?: string
  className?: string
  children: ReactNode
}) {
  const d = bubbleOf(skin)
  const style = d
    ? ({ backgroundImage: d.bg, boxShadow: d.shadow, '--bubble-out': d.top, '--bubble-in': d.top, '--bubble-text': d.text, '--bubble-meta': d.meta } as CSSProperties)
    : undefined
  return (
    <div
      style={style}
      title={title}
      className={cn(
        'relative min-w-0 rounded-[20px] text-bubble-text',
        mine ? 'bg-bubble-out' : 'bg-bubble-in',
        !first && (mine ? 'rounded-se-md' : 'rounded-ss-md'),
        !last && (mine ? 'rounded-ee-md' : 'rounded-es-md'),
        d?.motion,
        className,
      )}
    >
      {children}
    </div>
  )
}

/** Invisible copy of the time that reserves room on the last line, so the real one never covers the text. */
export function MetaSpacer({ children }: { children: ReactNode }) {
  return (
    <span aria-hidden className="invisible ms-2 inline-flex items-center gap-0.5 align-bottom text-[11px] leading-none">
      {children}
    </span>
  )
}

/** A photo or video; someone else's is blurred until tapped only when I chose that in Settings (off by default). */
export function MediaBubble({ media, mine, meta }: { media: NonNullable<Message['media']>; mine: boolean; meta: ReactNode | null }) {
  const { t } = useI18n()
  const [revealed, setRevealed] = useState(() => mine || !savedFlag('yaari_blur_media'))
  const [large, setLarge] = useState<number | null>(null)
  return (
    <div className="relative overflow-hidden rounded-[16px] bg-black/5">
      {media.kind === 'video' ? (
        <video
          src={media.url}
          controls={revealed}
          playsInline
          preload="metadata"
          className={cn('block max-h-80 w-64 object-cover transition-[filter] duration-300', !revealed && 'scale-110 blur-2xl')}
        />
      ) : (
        <>
          {/* 39: once shown, a tap opens it large (zoom, save). */}
          <img
            src={media.url}
            alt={revealed ? t('bubble.photo') : t('bubble.hidden')}
            crossOrigin="anonymous"
            decoding="async"
            onClick={revealed ? () => setLarge(0) : undefined}
            className={cn('block max-h-80 w-64 object-cover transition-[filter,transform] duration-300', revealed ? 'cursor-zoom-in' : 'scale-110 blur-2xl')}
          />
          <PhotoViewer photos={[media.url]} index={large} onIndex={setLarge} label={t('bubble.photo')} />
        </>
      )}
      {!revealed ? (
        <button
          type="button"
          onClick={() => setRevealed(true)}
          className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-black/40 text-white"
        >
          <span className="grid size-11 place-items-center rounded-full bg-white/20">
            <Eye className="size-5" aria-hidden />
          </span>
          <span className="text-sm font-semibold">{t('bubble.tap')}</span>
          <span className="text-xs text-white/85">{media.kind === 'video' ? t('bubble.sensitiveVideo') : t('bubble.sensitivePhoto')}</span>
        </button>
      ) : null}
      {meta && revealed && media.kind === 'image' ? (
        <span className="pointer-events-none absolute bottom-1 inset-e-1.5 flex items-center gap-0.5 rounded-full bg-black/35 px-1.5 py-0.5 text-[11px] leading-none text-white">
          {meta}
        </span>
      ) : null}
    </div>
  )
}

/** Three dots rising one after another; still (dimmed) under reduced motion. */
export function TypingDots({ className }: { className?: string }) {
  return (
    <span className={cn('inline-flex items-center gap-1', className)} aria-hidden>
      {[0, 1, 2].map((i) => (
        <span
          key={i}
          className="size-[7px] animate-typing-dot rounded-full bg-bubble-meta motion-reduce:animate-none motion-reduce:opacity-60"
          style={{ animationDelay: `${i * 0.16}s` }}
        />
      ))}
    </span>
  )
}

export function TypingBubble({ name }: { name: string }) {
  const { t } = useI18n()
  return (
    <m.li
      initial={{ opacity: 0, y: 6, scale: 0.96 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0, scale: 0.96 }}
      transition={{ type: 'tween', duration: 0.15, ease: [0.2, 0, 0, 1] }}
      className="ms-9 mt-3 origin-bottom-left self-start rtl:origin-bottom-right"
      aria-live="polite"
    >
      <Bubble mine={false} className="flex h-9 items-center px-3.5">
        <span className="sr-only">{t('bubble.typing', { name })}</span>
        <TypingDots />
      </Bubble>
    </m.li>
  )
}
