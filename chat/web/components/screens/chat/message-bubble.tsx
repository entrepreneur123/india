'use client'

import * as m from 'motion/react-m'
import { useState, type CSSProperties, type ReactNode } from 'react'
import { Check, CheckCheck, ChevronRight, Copy, Eye, Languages, Undo2 } from 'lucide-react'
import type { Message } from '@/lib/backend'
import { useI18n } from '@/components/i18n'
import { shownText, TranslateFooter, useLineTranslation, type LineTranslation } from '@/components/translate'
import { Modal } from '@/components/ui/modal'
import { useToast } from '@/components/ui/toast'
import { bubbleOf } from '@/lib/looks'
import { cn } from '@/lib/utils'
import { LinkPreview, LinkText } from './link-viewer'
import { MessageMenu, useMenuGesture, type MenuAction } from './message-menu'

/** A line's Translate / Show original / Copy actions (the menu of a chat bubble or a room line). */
export function useLineActions(tr: LineTranslation, text: string | null | undefined): MenuAction[] {
  const { t } = useI18n()
  const toast = useToast()
  const out: MenuAction[] = []
  if (tr.can) {
    if (tr.showing) out.push({ icon: <Undo2 className="size-4.5 rtl:-scale-x-100" aria-hidden />, label: t('tr.showOriginal'), onClick: tr.showOriginal })
    else if (tr.hidden) out.push({ icon: <Languages className="size-4.5" aria-hidden />, label: t('tr.showTranslation'), onClick: tr.showTranslation })
    else out.push({ icon: <Languages className="size-4.5" aria-hidden />, label: t('tr.translate'), onClick: tr.translate })
  }
  if (text && typeof navigator !== 'undefined' && navigator.clipboard) {
    out.push({
      icon: <Copy className="size-4.5" aria-hidden />,
      label: t('tr.copy'),
      onClick: () =>
        void navigator.clipboard.writeText(shownText(tr, text)).then(
          () => toast.show(t('tr.copied'), 'success'),
          () => undefined,
        ),
    })
  }
  return out
}

/** With a mouse: a small Translate button beside someone's bubble on hover (touch has the long-press menu). */
export function HoverTranslate({ tr, className }: { tr: LineTranslation; className?: string }) {
  const { t } = useI18n()
  if (!tr.can || tr.showing || tr.hidden || tr.entry?.status === 'loading') return null
  return (
    <button
      type="button"
      onClick={tr.translate}
      aria-label={t('tr.translate')}
      title={t('tr.translate')}
      className={cn(
        'grid size-8 shrink-0 place-items-center self-center rounded-full text-muted-foreground opacity-0 transition-opacity duration-150 group-hover:opacity-100 hover:bg-muted hover:text-foreground focus-visible:opacity-100 pointer-coarse:hidden',
        className,
      )}
    >
      <Languages className="size-4" aria-hidden />
    </button>
  )
}

/**
 * Chat bubbles in the familiar messenger style: green (mine) / white (theirs) on a warm wallpaper, a tail on the
 * first bubble of a run, and the time with read ticks tucked into the bubble's corner. Someone else's text can be
 * translated (by itself when it looks like another language, or from the long-press / right-click menu).
 */
export function MessageBubble({ message, grouped, read, skin }: { message: Message; grouped: boolean; read?: boolean; skin?: string | null }) {
  const { t, clock } = useI18n()
  const mine = message.fromMe
  const tr = useLineTranslation('chat', message.id, message.text, mine)
  const actions = useLineActions(tr, message.text)
  const [menu, setMenu] = useState(false)
  const gesture = useMenuGesture(actions.length ? () => setMenu(true) : null)
  const text = message.text ? shownText(tr, message.text) : null
  const meta = (
    <>
      {clock(message.at)}
      {mine ? (
        read ? (
          <CheckCheck className="size-4 text-tick-read" aria-label={t('bubble.read')} />
        ) : (
          <Check className="size-4" aria-label={t('bubble.sent')} />
        )
      ) : null}
    </>
  )
  return (
    <m.li
      ref={tr.ref}
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ type: 'tween', duration: 0.15, ease: [0.2, 0, 0, 1] }}
      className={cn(
        'group relative flex max-w-[82%] gap-1 md:max-w-[65%] pointer-coarse:select-none pointer-coarse:[-webkit-touch-callout:none]',
        mine ? 'self-end' : 'self-start',
        grouped ? 'mt-0.5' : 'mt-2',
      )}
      {...gesture}
    >
      <Bubble mine={mine} tail={!grouped} skin={skin} className={message.media ? 'p-1' : 'px-2.5 pb-1.5 pt-1.5'}>
        {message.media ? (
          <MediaBubble media={message.media} mine={mine} meta={message.text ? null : meta} />
        ) : null}
        {message.text && text ? (
          // User text is always rendered as text (React escapes it), never as HTML; its links are plain <a> links.
          <>
            <LinkPreview text={message.text} className={cn('mb-1 w-72 max-w-full', message.media && 'mx-1')} />
            <p
              dir="auto"
              lang={tr.showing ? tr.target : undefined}
              className={cn('relative text-start text-[15px] leading-[1.35] break-words whitespace-pre-wrap', message.media && 'px-1.5 pb-0.5 pt-1')}
            >
              <LinkText text={fold(text)} />
              <MetaSpacer>{meta}</MetaSpacer>
              <span className="absolute -bottom-0.5 inset-e-0 flex items-center gap-0.5 text-[11px] leading-none text-bubble-meta">{meta}</span>
            </p>
            <SeeAll text={text} className={cn(message.media && 'mx-1.5')} />
            <TranslateFooter tr={tr} className={cn(message.media && 'px-1.5')} />
          </>
        ) : null}
      </Bubble>
      {mine ? null : <HoverTranslate tr={tr} />}
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
 * Shared bubble shell (random chats and rooms). `skin`: the sender's chat bubble (062), drawn over the plain colours;
 * the tail takes its top colour and the text and time take its own.
 */
export function Bubble({ mine, tail, skin, className, children }: { mine: boolean; tail: boolean; skin?: string | null; className?: string; children: ReactNode }) {
  const d = bubbleOf(skin)
  const style = d
    ? ({ backgroundImage: d.bg, boxShadow: d.shadow, '--bubble-out': d.top, '--bubble-in': d.top, '--bubble-text': d.text, '--bubble-meta': d.meta } as CSSProperties)
    : undefined
  return (
    <div
      style={style}
      className={cn(
        'relative min-w-0 rounded-lg text-bubble-text shadow-[0_1px_0.5px_rgb(11_20_26/0.13)]',
        mine ? 'bg-bubble-out' : 'bg-bubble-in',
        tail && (mine ? 'rounded-se-none' : 'rounded-ss-none'),
        d?.motion,
        className,
      )}
    >
      {tail ? (
        <svg
          viewBox="0 0 8 13"
          aria-hidden
          // The tail sits on the bubble's outer top corner and mirrors with the layout (right to left).
          className={cn('absolute top-0 h-[13px] w-2 rtl:-scale-x-100', mine ? '-inset-e-2 text-bubble-out' : '-inset-s-2 text-bubble-in')}
        >
          <path
            fill="currentColor"
            d={mine ? 'M5.188 0H0v11.193l6.467-8.625C7.526 1.156 6.958 0 5.188 0z' : 'M2.812 0H8v11.193L1.533 2.568C.474 1.156 1.042 0 2.812 0z'}
          />
        </svg>
      ) : null}
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

/** A photo or video, blurred until tapped when it's someone else's (random chats and rooms). */
export function MediaBubble({ media, mine, meta }: { media: NonNullable<Message['media']>; mine: boolean; meta: ReactNode | null }) {
  const { t } = useI18n()
  const [revealed, setRevealed] = useState(mine)
  return (
    <div className="relative overflow-hidden rounded-md bg-black/5">
      {media.kind === 'video' ? (
        <video
          src={media.url}
          controls={revealed}
          playsInline
          preload="metadata"
          className={cn('block max-h-80 w-64 object-cover transition-[filter] duration-300', !revealed && 'scale-110 blur-2xl')}
        />
      ) : (
        <img
          src={media.url}
          alt={revealed ? t('bubble.photo') : t('bubble.hidden')}
          crossOrigin="anonymous"
          decoding="async"
          className={cn('block max-h-80 w-64 object-cover transition-[filter,transform] duration-300', !revealed && 'scale-110 blur-2xl')}
        />
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
      className="mt-2 origin-bottom-left self-start rtl:origin-bottom-right"
      aria-live="polite"
    >
      <Bubble mine={false} tail className="flex h-9 items-center px-3.5">
        <span className="sr-only">{t('bubble.typing', { name })}</span>
        <TypingDots />
      </Bubble>
    </m.li>
  )
}
