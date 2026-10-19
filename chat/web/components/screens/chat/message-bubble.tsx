'use client'

import * as m from 'motion/react-m'
import { useState } from 'react'
import { Eye } from 'lucide-react'
import type { Message } from '@/lib/backend'
import { useI18n } from '@/components/i18n'
import { cn } from '@/lib/utils'

export function MessageBubble({ message, grouped }: { message: Message; grouped: boolean }) {
  const { clock } = useI18n()
  const mine = message.fromMe
  return (
    <m.li
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ type: 'tween', duration: 0.15, ease: [0.2, 0, 0, 1] }}
      className={cn('flex max-w-[78%] flex-col gap-1', mine ? 'self-end items-end' : 'self-start items-start', grouped ? 'mt-0.5' : 'mt-3')}
    >
      {message.media ? <MediaBubble media={message.media} mine={mine} /> : null}
      {message.text ? (
        // User text is always rendered as text (React escapes it), never as HTML.
        <div
          className={cn(
            'rounded-2xl px-4 py-2.5 text-[15px] leading-relaxed break-words whitespace-pre-wrap',
            mine ? 'rounded-br-md bg-primary text-primary-foreground' : 'rounded-bl-md bg-muted text-foreground',
          )}
        >
          {message.text}
        </div>
      ) : null}
      {!grouped ? null : <span className="sr-only">{clock(message.at)}</span>}
    </m.li>
  )
}

function MediaBubble({ media, mine }: { media: NonNullable<Message['media']>; mine: boolean }) {
  const { t } = useI18n()
  const [revealed, setRevealed] = useState(mine)
  return (
    <div className="relative overflow-hidden rounded-2xl bg-muted">
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
      className="mt-3 flex items-center gap-1.5 self-start rounded-2xl rounded-bl-md bg-muted px-4 py-3.5"
      aria-live="polite"
    >
      <span className="sr-only">{t('bubble.typing', { name })}</span>
      {[0, 1, 2].map((i) => (
        <span
          key={i}
          className="size-2 animate-pulse rounded-full bg-muted-foreground motion-reduce:animate-none"
          style={{ animationDelay: `${i * 0.2}s` }}
          aria-hidden
        />
      ))}
    </m.li>
  )
}
