'use client'

import { useState, useSyncExternalStore } from 'react'
import { cn } from '@/lib/utils'
import { flagEmoji } from '@/lib/format'
import { isVip } from '@/lib/looks'

// One 2-second clock for every photo gradient on screen (062): they change together and only one timer runs, only
// while one is shown.
const ticks = new Set<() => void>()
let tick = 0
let timer: ReturnType<typeof setInterval> | null = null
const subscribe = (f: () => void) => {
  ticks.add(f)
  timer ??= setInterval(() => {
    tick++
    ticks.forEach((g) => g())
  }, 2000)
  return () => {
    ticks.delete(f)
    if (!ticks.size && timer) {
      clearInterval(timer)
      timer = null
    }
  }
}
const noTick = () => () => undefined
/** Which of `n` gradient photos shows now (0 when there is nothing to cycle). */
export const useCycle = (n: number) => useSyncExternalStore(n >= 2 ? subscribe : noTick, () => (n >= 2 ? tick % n : 0), () => 0)

export interface AvatarProps {
  name: string
  src?: string | null
  /** A photo gradient (062): 2–5 photos fading one into the next every 2 seconds, shown instead of `src`. */
  srcs?: string[]
  size?: number
  country?: string | null
  online?: boolean
  className?: string
}

export function Avatar({ name, src, srcs, size = 40, country, online, className }: AvatarProps) {
  const [failed, setFailed] = useState<string | null>(null) // the src that failed; a new photo gets a new try
  const initials = [...name.trim()]
    .slice(0, 1)
    .join('')
    .toUpperCase()
  const showImage = src && failed !== src
  const badge = Math.max(14, Math.round(size * 0.36))
  const cycling = !!srcs && srcs.length >= 2
  const at = useCycle(cycling ? srcs.length : 0)

  return (
    <span className={cn('relative inline-flex shrink-0', className)} style={{ width: size, height: size }}>
      <span className="relative block size-full overflow-hidden rounded-full bg-border">
        {cycling ? (
          srcs.map((u, i) => (
            <img
              key={u}
              src={u}
              alt=""
              width={size}
              height={size}
              className={cn(
                // A long cross-fade (1.5 s of every 2 s): the photos melt into each other, a gradient that never stops.
                'absolute inset-0 size-full bg-muted object-cover transition-opacity duration-1500 ease-in-out motion-reduce:transition-none',
                i === at ? 'opacity-100' : 'opacity-0',
              )}
              decoding="async"
              draggable={false}
            />
          ))
        ) : showImage ? (
          <img
            src={src}
            alt=""
            width={size}
            height={size}
            className="size-full bg-muted object-cover"
            loading="lazy"
            decoding="async"
            draggable={false}
            onError={() => setFailed(src)}
          />
        ) : isVip({ name, photoUrl: src }) ? (
          // 046 + 35: someone in VIP incognito (no photo for strangers): black, a gold serif mark, light passing over.
          <span className="vip-face relative grid size-full place-items-center overflow-hidden" aria-hidden>
            <span className="vip-gold font-bold italic tracking-wide" style={{ fontSize: Math.max(9, size * 0.32) }}>
              VIP
            </span>
            <span className="look-sheen pointer-events-none absolute inset-y-0 -left-1/4 w-1/2 bg-linear-to-r from-transparent via-white/20 to-transparent" />
          </span>
        ) : (
          <span
            className="grid size-full place-items-center font-semibold text-foreground"
            style={{ fontSize: Math.max(11, size * 0.42) }}
            aria-hidden
          >
            {initials}
          </span>
        )}
      </span>
      {country ? (
        <span
          className="absolute -inset-e-0.5 -bottom-0.5 grid place-items-center rounded-full bg-card leading-none ring-2 ring-card"
          style={{ width: badge, height: badge, fontSize: badge * 0.72 }}
          aria-hidden
        >
          {flagEmoji(country)}
        </span>
      ) : null}
      {online ? (
        <span className="absolute top-0 inset-e-0 flex size-3" aria-hidden>
          <span className="relative inline-flex size-3 rounded-full bg-online ring-2 ring-card" />
        </span>
      ) : null}
      <span className="sr-only">{name}</span>
    </span>
  )
}
