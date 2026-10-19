'use client'

import { motion } from 'motion/react'
import { useState } from 'react'
import { Eye } from 'lucide-react'
import type { Message } from '@/lib/backend'
import { clockTime } from '@/lib/format'
import { cn } from '@/lib/utils'

export function MessageBubble({ message, grouped }: { message: Message; grouped: boolean }) {
  const mine = message.fromMe
  return (
    <motion.li
      layout="position"
      initial={{ opacity: 0, y: 14, scale: 0.86 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      transition={{ type: 'spring', stiffness: 460, damping: 28 }}
      style={{ transformOrigin: mine ? 'bottom right' : 'bottom left' }}
      className={cn('flex max-w-[78%] flex-col gap-1', mine ? 'self-end items-end' : 'self-start items-start', grouped ? 'mt-0.5' : 'mt-3')}
    >
      {message.media ? <MediaBubble media={message.media} mine={mine} /> : null}
      {message.text ? (
        <div
          className={cn(
            'rounded-3xl px-4 py-2.5 text-[15px] leading-relaxed break-words whitespace-pre-wrap',
            mine
              ? 'rounded-br-lg bg-brand text-primary-foreground'
              : 'rounded-bl-lg bg-card text-card-foreground ring-1 ring-border',
          )}
        >
          {message.text}
        </div>
      ) : null}
      {!grouped ? null : <span className="sr-only">{clockTime(message.at)}</span>}
    </motion.li>
  )
}

function MediaBubble({ media, mine }: { media: NonNullable<Message['media']>; mine: boolean }) {
  const [revealed, setRevealed] = useState(mine)
  return (
    <div className="relative overflow-hidden rounded-3xl bg-muted ring-1 ring-border">
      {media.kind === 'video' ? (
        <video
          src={media.url}
          controls={revealed}
          playsInline
          className={cn('block max-h-80 w-64 object-cover transition-[filter] duration-500', !revealed && 'scale-110 blur-2xl')}
        />
      ) : (
        <img
          src={media.url}
          alt={revealed ? 'Shared photo' : 'Hidden photo'}
          crossOrigin="anonymous"
          className={cn('block max-h-80 w-64 object-cover transition-[filter,transform] duration-500', !revealed && 'scale-110 blur-2xl')}
        />
      )}
      {!revealed ? (
        <button
          type="button"
          onClick={() => setRevealed(true)}
          className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-ink/30 text-ink-foreground"
        >
          <span className="grid size-11 place-items-center rounded-full bg-ink-foreground/15 ring-1 ring-ink-foreground/25 backdrop-blur-md">
            <Eye className="size-5" aria-hidden />
          </span>
          <span className="text-sm font-semibold">Tap to view</span>
          <span className="text-xs text-ink-foreground/70">{media.kind === 'video' ? 'Video' : 'Photo'} may be sensitive</span>
        </button>
      ) : null}
    </div>
  )
}

export function TypingBubble({ name }: { name: string }) {
  return (
    <motion.li
      initial={{ opacity: 0, y: 8, scale: 0.9 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0, scale: 0.9 }}
      className="mt-3 flex items-center gap-1.5 self-start rounded-3xl rounded-bl-lg bg-card px-4 py-3.5 ring-1 ring-border"
      aria-live="polite"
    >
      <span className="sr-only">{name} is typing</span>
      {[0, 1, 2].map((i) => (
        <motion.span
          key={i}
          className="size-2 rounded-full bg-muted-foreground"
          animate={{ y: [0, -5, 0], opacity: [0.5, 1, 0.5] }}
          transition={{ duration: 0.9, repeat: Infinity, delay: i * 0.15, ease: 'easeInOut' }}
          aria-hidden
        />
      ))}
    </motion.li>
  )
}
