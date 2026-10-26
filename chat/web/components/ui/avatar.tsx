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

/** The photo effects' light: aurora drifts through greens, blues and violets; prism turns a rainbow round. */
const EFFECT_LIGHT: Record<string, { light: string; motion: string }> = {
  effect_aurora: { light: 'linear-gradient(120deg, #00FFA3, #00C2FF, #7A5CFF, #FF4FD8, #00E0FF, #00FFA3)', motion: 'look-aurora' },
  effect_prism: { light: 'conic-gradient(from var(--look-a), #FF3B3B, #FFB800, #3BFF6E, #00C2FF, #8B5CFF, #FF3BD1, #FF3B3B)', motion: 'look-prism' },
}

export interface AvatarProps {
  name: string
  src?: string | null
  /** A photo gradient (062): 2–4 photos fading one into the next every 2 seconds, shown instead of `src`. */
  srcs?: string[]
  /** A photo effect (062): light moving over the photo, with a glow of the same colours around it. */
  effect?: string | null
  size?: number
  country?: string | null
  online?: boolean
  className?: string
}

export function Avatar({ name, src, srcs, effect, size = 40, country, online, className }: AvatarProps) {
  const [failed, setFailed] = useState<string | null>(null) // the src that failed; a new photo gets a new try
  const initials = [...name.trim()]
    .slice(0, 1)
    .join('')
    .toUpperCase()
  const showImage = src && failed !== src
  const badge = Math.max(14, Math.round(size * 0.36))
  const fx = effect ? EFFECT_LIGHT[effect] : undefined
  const cycling = !!srcs && srcs.length >= 2
  const at = useCycle(cycling ? srcs.length : 0)

  return (
    <span className={cn('relative inline-flex shrink-0', className)} style={{ width: size, height: size }}>
      {fx ? (
        <span aria-hidden className={cn('pointer-events-none absolute -inset-[10%] rounded-full opacity-70', fx.motion)} style={{ backgroundImage: fx.light, filter: `blur(${Math.max(3, size * 0.1)}px)` }} />
      ) : null}
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
                'absolute inset-0 size-full bg-muted object-cover transition-opacity duration-700 ease-in-out motion-reduce:transition-none',
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
          // 046: someone in VIP incognito (no photo for strangers): the mark in the brand colour.
          <span
            className="grid size-full place-items-center bg-primary font-extrabold tracking-tight text-primary-foreground"
            style={{ fontSize: Math.max(9, size * 0.3) }}
            aria-hidden
          >
            VIP
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
        {fx ? (
          <>
            <span aria-hidden className={cn('pointer-events-none absolute inset-0 opacity-60 mix-blend-soft-light', fx.motion)} style={{ backgroundImage: fx.light }} />
            <span aria-hidden className={cn('pointer-events-none absolute inset-0 mix-blend-screen', fx.motion)} style={{ backgroundImage: fx.light, opacity: 0.22 }} />
            <span aria-hidden className="look-sheen pointer-events-none absolute inset-y-0 -left-1/4 w-1/2 bg-linear-to-r from-transparent via-white/45 to-transparent" />
          </>
        ) : null}
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
