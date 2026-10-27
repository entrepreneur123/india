'use client'

import type { CSSProperties, ReactNode } from 'react'
import useSWR from 'swr'
import type { Backend, Look, LookWear } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { COLOR_DESIGNS, FONT_DESIGNS, isItem, NO_WEAR, type BadgeId, type ColorId, type FontId } from '@/lib/looks'
import { cn } from '@/lib/utils'
import { LookBadge } from './badges'

// Someone's @ID in their colour and font with their badge after it (040), and the hook that reads anyone's look. Like
// the trust badge (components/trust/badge.tsx): every useLook() on screen within a frame goes out as one looks() call
// (at most 100 ids), and an answer is kept for 2 minutes.

type Waiter = { id: string; resolve: (l: Look | null) => void; reject: (e: unknown) => void }
const queues = new WeakMap<Backend, { waiting: Waiter[]; timer: ReturnType<typeof setTimeout> | null }>()

/** One person's look, asked together with everyone else asked in the same moment. */
function loadLook(backend: Backend, id: string): Promise<Look | null> {
  let q = queues.get(backend)
  if (!q) queues.set(backend, (q = { waiting: [], timer: null }))
  const queue = q
  return new Promise((resolve, reject) => {
    queue.waiting.push({ id, resolve, reject })
    queue.timer ??= setTimeout(() => {
      const batch = queue.waiting.splice(0)
      queue.timer = null
      const ids = [...new Set(batch.map((w) => w.id))]
      for (let i = 0; i < ids.length; i += 100) {
        const part = new Set(ids.slice(i, i + 100))
        backend.looks([...part]).then(
          (list) => {
            const byId = new Map(list.map((l) => [l.id, l]))
            for (const w of batch) if (part.has(w.id)) w.resolve(byId.get(w.id) ?? null)
          },
          (err) => {
            for (const w of batch) if (part.has(w.id)) w.reject(err)
          },
        )
      }
    }, 16)
  })
}

/** The SWR key of someone's look: mutate it after my own look or ID changes. */
export const lookKey = (userId: string) => ['look', userId] as const

/** Someone's ID and look (null while loading, or when I may not see them). */
export function useLook(userId: string | null | undefined): Look | null {
  const backend = useBackend()
  const { data } = useSWR(userId ? lookKey(userId) : null, () => loadLook(backend, userId!), {
    dedupingInterval: 120_000,
    revalidateOnFocus: false,
    shouldRetryOnError: false,
  })
  return data ?? null
}

/** The CSS of an ID colour: one tone or a gradient through the letters. */
function colorStyle(id: ColorId): { className?: string; style: CSSProperties } {
  const d = COLOR_DESIGNS[id]
  if (typeof d === 'string') return { style: { color: d } }
  // The rainbow repeats its first colour at the end so its slide loops without a seam.
  const stops = d.slide ? [...d.stops, d.stops[0]] : d.stops
  return {
    className: cn('bg-clip-text text-transparent [-webkit-background-clip:text]', d.slide && 'look-rainbow'),
    style: { backgroundImage: `linear-gradient(90deg, ${stops.join(', ')})` },
  }
}

/**
 * Name effects on the name and @ID (the owner, 2026-10-27: the effect is seen on the text, not over the photo):
 * aurora drifts through greens, blues and violets, prism turns through the rainbow. Mid tones, readable on the white
 * card and on the shop's dark preview.
 */
const EFFECT_TEXT: Record<string, { stops: string; glow: string; motion: string }> = {
  effect_aurora: { stops: '#00A67E, #0A8FD6, #7A5CFF, #D63AB8, #0A8FD6, #00A67E', glow: 'rgb(10 143 214 / 0.45)', motion: 'look-aurora' },
  effect_prism: { stops: '#E53935, #EA7A00, #16A34A, #0284C7, #7C3AED, #DB2777, #E53935', glow: 'rgb(124 58 237 / 0.4)', motion: 'look-prism' },
}

/** A look's colour and font as CSS (null items: none). A name effect takes the colour's place. */
function lookStyle(wear: LookWear & { effect?: string | null }) {
  const fx = wear.effect ? EFFECT_TEXT[wear.effect] : undefined
  const color = fx
    ? { className: cn('bg-clip-text text-transparent [-webkit-background-clip:text]', fx.motion), style: { backgroundImage: `linear-gradient(90deg, ${fx.stops})`, filter: `drop-shadow(0 0 0.18em ${fx.glow})` } as CSSProperties }
    : isItem(wear.color) ? colorStyle(wear.color as ColorId) : null
  const font = isItem(wear.font) ? FONT_DESIGNS[wear.font as FontId] : null
  return {
    color,
    font,
    style: { ...color?.style, ...(font ? { fontFamily: `'${font.family}', var(--font-stack)`, fontSize: font.scale === 1 ? undefined : `${font.scale}em` } : null) },
  }
}

/** The @ID drawn with a look (null items: plain). Used by LookName and by the shop's previews. */
export function LookText({ handle, wear, className }: { handle: string; wear: LookWear & { effect?: string | null }; className?: string }) {
  const { color, font, style } = lookStyle(wear)
  return (
    <span className={cn('inline-flex max-w-full min-w-0 items-center gap-[2px] align-bottom', className)}>
      <bdi
        // The padding keeps script descenders and swashes inside the clipped (truncating) box.
        className={cn('-my-[0.15em] min-w-0 truncate py-[0.15em]', color ? color.className : 'text-muted-foreground')}
        style={style}
      >
        {/* The @ is how IDs are shown, not part of the ID: it keeps the app's font (some ID fonts draw it as a blob). */}
        <span style={font ? { fontFamily: 'var(--font-stack)' } : undefined}>@</span>
        {handle}
      </bdi>
      {isItem(wear.badge) ? <LookBadge id={wear.badge as BadgeId} className="h-[1em] w-auto shrink-0" /> : null}
    </span>
  )
}

/**
 * Someone's @ID with their look. `handle`: already known (my own, a profile that carries it), shown while the look
 * loads; `fallback`: what to show while nothing is known yet (e.g. the plain name), nothing by default.
 */
export function LookName({ userId, handle, fallback = null, className }: { userId: string; handle?: string; fallback?: ReactNode; className?: string }) {
  const look = useLook(userId)
  const shown = look?.handle ?? handle
  if (!shown) return <>{fallback}</>
  return <LookText handle={shown} wear={look ?? NO_WEAR} className={className} />
}

/**
 * Someone's name in their look's colour and font (the owner, 2026-10-27: others see the name first, so the look is on
 * the name as well as the @ID; the badge stays with the @ID). Plain until the look loads, and for everyone without one.
 */
export function NameText({ userId, wear, children, className }: { userId?: string; wear?: LookWear & { effect?: string | null }; children: ReactNode; className?: string }) {
  const look = useLook(wear ? null : userId) // `wear`: a look being tried on (the shop's preview)
  const { color, style } = lookStyle(wear ?? look ?? NO_WEAR)
  return (
    <bdi className={cn('-my-[0.15em] py-[0.15em]', color?.className, className)} style={style}>
      {children}
    </bdi>
  )
}

/** "Name, age" with the name in the person's look (the age is plain; none when it is hidden). */
export function PersonName({ p, className }: { p: { id: string; name: string; age: number }; className?: string }) {
  return (
    <span className={className}>
      <NameText userId={p.id}>{p.name}</NameText>
      {p.age > 0 ? `, ${p.age}` : null}
    </span>
  )
}
