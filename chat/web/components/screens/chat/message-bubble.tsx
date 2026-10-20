'use client'

import * as m from 'motion/react-m'
import { useState, type ReactNode } from 'react'
import { Check, CheckCheck, Eye } from 'lucide-react'
import type { Message } from '@/lib/backend'
import { useI18n } from '@/components/i18n'
import { cn } from '@/lib/utils'

/**
 * Chat bubbles in the style people in India use every day: green (mine) / white (theirs) on a warm wallpaper,
 * a tail on the first bubble of a run, and the time with read ticks tucked into the bubble's corner.
 */
export function MessageBubble({ message, grouped, read }: { message: Message; grouped: boolean; read?: boolean }) {
  const { t, clock } = useI18n()
  const mine = message.fromMe
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
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ type: 'tween', duration: 0.15, ease: [0.2, 0, 0, 1] }}
      className={cn('flex max-w-[82%] md:max-w-[65%]', mine ? 'self-end' : 'self-start', grouped ? 'mt-0.5' : 'mt-2')}
    >
      <Bubble mine={mine} tail={!grouped} className={message.media ? 'p-1' : 'px-2.5 pb-1.5 pt-1.5'}>
        {message.media ? (
          <MediaBubble media={message.media} mine={mine} meta={message.text ? null : meta} />
        ) : null}
        {message.text ? (
          // User text is always rendered as text (React escapes it), never as HTML.
          <p className={cn('relative text-[15px] leading-[1.35] break-words whitespace-pre-wrap', message.media && 'px-1.5 pb-0.5 pt-1')}>
            {message.text}
            <MetaSpacer>{meta}</MetaSpacer>
            <span className="absolute -bottom-0.5 right-0 flex items-center gap-0.5 text-[11px] leading-none text-bubble-meta">{meta}</span>
          </p>
        ) : null}
      </Bubble>
    </m.li>
  )
}

/** Shared bubble shell (random chats and rooms). */
export function Bubble({ mine, tail, className, children }: { mine: boolean; tail: boolean; className?: string; children: ReactNode }) {
  return (
    <div
      className={cn(
        'relative min-w-0 rounded-lg text-bubble-text shadow-[0_1px_0.5px_rgb(11_20_26/0.13)]',
        mine ? 'bg-bubble-out' : 'bg-bubble-in',
        tail && (mine ? 'rounded-tr-none' : 'rounded-tl-none'),
        className,
      )}
    >
      {tail ? (
        <svg
          viewBox="0 0 8 13"
          aria-hidden
          className={cn('absolute top-0 h-[13px] w-2', mine ? '-right-2 text-bubble-out' : '-left-2 text-bubble-in')}
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
    <span aria-hidden className="invisible ml-2 inline-flex items-center gap-0.5 align-bottom text-[11px] leading-none">
      {children}
    </span>
  )
}

function MediaBubble({ media, mine, meta }: { media: NonNullable<Message['media']>; mine: boolean; meta: ReactNode | null }) {
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
        <span className="pointer-events-none absolute bottom-1 right-1.5 flex items-center gap-0.5 rounded-full bg-black/35 px-1.5 py-0.5 text-[11px] leading-none text-white">
          {meta}
        </span>
      ) : null}
    </div>
  )
}

export function TypingBubble({ name }: { name: string }) {
  const { t } = useI18n()
  return (
    <m.li
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.15 }}
      className="mt-2 self-start"
      aria-live="polite"
    >
      <Bubble mine={false} tail className="flex items-center gap-1.5 px-4 py-3">
        <span className="sr-only">{t('bubble.typing', { name })}</span>
        {[0, 1, 2].map((i) => (
          <span
            key={i}
            className="size-2 animate-pulse rounded-full bg-bubble-meta motion-reduce:animate-none"
            style={{ animationDelay: `${i * 0.2}s` }}
            aria-hidden
          />
        ))}
      </Bubble>
    </m.li>
  )
}
