'use client'

import { useEffect, useRef, type CSSProperties, type ReactNode } from 'react'
import { Avatar } from '@/components/ui/avatar'
import type { FrameId } from '@/lib/looks'
import { cn } from '@/lib/utils'

// Profile frames (040): they replace the free cover photo and colour. A frame draws (a) a 3 px gradient ring around the
// profile card, (b) a 3 px ring around the big photo, (c) a soft wash at the top of the card and (d) a light particle
// animation behind the card's content (≤ 30 particles at ≤ 30 fps, paused off screen and in a background tab; still
// with reduced motion). The same designs as the Android app (docs/LOOKS_SPEC.md).

interface FrameDesign {
  /** The ring's gradient (diagonal across the card, around the photo). */
  ring: string[]
  /** The wash at the top of the card, on light and on night screens. */
  wash: [light: string, dark: string]
  /** Round gems on the ring (jewels). */
  gems?: string[]
}

const FRAME_DESIGNS: Record<FrameId, FrameDesign> = {
  frame_frost: { ring: ['#E3F6FF', '#8FD3FF', '#4AA8FF'], wash: ['#DFF4FF', '#0B2A44'] },
  frame_fire: { ring: ['#FFD54A', '#FF6A00', '#D50000'], wash: ['#FFF1E6', '#2A0D05'] },
  frame_jewel: { ring: ['#FFE8A3', '#D4A017', '#FFE8A3'], wash: ['#FFF8E1', '#2A2208'], gems: ['#E0115F', '#0F52BA', '#50C878'] },
  frame_sand: { ring: ['#F4D9A6', '#C99A5B', '#8B5E34'], wash: ['#FFF6E6', '#2B1E0E'] },
  frame_neon: { ring: ['#00F0FF', '#FF00E5'], wash: ['#E9FDFF', '#100A24'] },
  frame_sakura: { ring: ['#FFD1E1', '#FF8AB3'], wash: ['#FFF0F5', '#2A1220'] },
}

const ringImage = (d: FrameDesign) => `linear-gradient(135deg, ${d.ring.join(', ')})`
/** The fire ring flickers (opacity 0.8–1, 0.6 s); the neon glows (blur 8 px, pulsing over 2 s). In app/globals.css. */
const ringMotion = (frame: FrameId) => (frame === 'frame_fire' ? 'look-flicker' : undefined)

/** The ring itself, under the content (and the neon's glow under the ring). */
function Ring({ frame }: { frame: FrameId }) {
  const d = FRAME_DESIGNS[frame]
  return (
    <>
      {frame === 'frame_neon' ? (
        <span aria-hidden className="look-glow pointer-events-none absolute inset-0 rounded-[inherit] blur-[8px]" style={{ backgroundImage: ringImage(d) }} />
      ) : null}
      <span aria-hidden className={cn('pointer-events-none absolute inset-0 rounded-[inherit]', ringMotion(frame))} style={{ backgroundImage: ringImage(d) }} />
    </>
  )
}

/** A gem: a round stone with a highlight, set in a thin gold rim. */
function Gem({ color, style }: { color: string; style: CSSProperties }) {
  return (
    <span
      aria-hidden
      className="pointer-events-none absolute z-10 size-2 -translate-x-1/2 -translate-y-1/2 rounded-full ring-1 ring-[#B8860B]"
      style={{ ...style, background: `radial-gradient(circle at 35% 30%, #fff 0 14%, ${color} 46%, color-mix(in srgb, ${color} 70%, #000))` }}
    />
  )
}

/** Where the 8 gems sit on a card's ring (on its middle line, 1.5 px in). */
const CARD_GEMS: CSSProperties[] = [
  { left: '25%', top: 1.5 }, { left: '75%', top: 1.5 }, { left: 'calc(100% - 1.5px)', top: '35%' }, { left: 'calc(100% - 1.5px)', top: '70%' },
  { left: '75%', top: 'calc(100% - 1.5px)' }, { left: '25%', top: 'calc(100% - 1.5px)' }, { left: 1.5, top: '70%' }, { left: 1.5, top: '35%' },
]

/**
 * A profile card in a frame: the ring, the wash and the particles behind `children`. Without a frame, a plain card.
 * `still`: the particles don't move (small previews).
 */
export function FrameCard({ frame, still, className, children }: { frame: FrameId | null; still?: boolean; className?: string; children: ReactNode }) {
  const d = frame ? FRAME_DESIGNS[frame] : null
  return (
    <div className={cn('relative rounded-[28px]', d ? 'p-[3px]' : 'ring-1 ring-border', className)}>
      {frame ? <Ring frame={frame} /> : null}
      <div className={cn('relative isolate h-full overflow-hidden bg-card', d ? 'rounded-[25px]' : 'rounded-[28px]')}>
        {frame && d ? (
          <>
            <span
              aria-hidden
              className="pointer-events-none absolute inset-0 [--w:var(--fw)] night:[--w:var(--fw-n)]"
              style={{ '--fw': d.wash[0], '--fw-n': d.wash[1], backgroundImage: 'linear-gradient(to bottom, var(--w), transparent 60%)' } as CSSProperties}
            />
            <FrameParticles frame={frame} still={still} />
          </>
        ) : null}
        <div className="relative">{children}</div>
      </div>
      {d?.gems ? CARD_GEMS.map((style, i) => <Gem key={i} color={d.gems![i % d.gems!.length]} style={style} />) : null}
    </div>
  )
}

/** The big photo in the frame's ring (3 px, then a thin gap in the card's colour), or plain without a frame. */
export function FrameAvatar({ frame, name, src, size, className }: { frame: FrameId | null; name: string; src?: string | null; size: number; className?: string }) {
  const d = frame ? FRAME_DESIGNS[frame] : null
  const outer = size + 2 * (3 + (d ? 3 : 0))
  return (
    <span className={cn('relative inline-grid shrink-0 place-items-center rounded-full', !d && 'shadow-e2', className)} style={{ width: outer, height: outer, padding: d ? 3 : 0 }}>
      {frame ? <Ring frame={frame} /> : null}
      <span className="relative grid place-items-center rounded-full bg-card p-[3px]">
        <Avatar name={name} src={src} size={size} />
      </span>
      {d?.gems
        ? Array.from({ length: 8 }, (_, i) => {
            const a = (Math.PI / 4) * i - Math.PI / 2
            const r = outer / 2 - 1.5
            return <Gem key={i} color={d.gems![i % d.gems!.length]} style={{ left: outer / 2 + r * Math.cos(a), top: outer / 2 + r * Math.sin(a) }} />
          })
        : null}
    </span>
  )
}

// ---- particles ------------------------------------------------------------------------------------------------------

/** One particle: position (CSS px), size, speed, spin, a phase for its sway or twinkle, and a colour. */
interface P {
  x: number
  y: number
  s: number
  vx: number
  vy: number
  r: number
  vr: number
  ph: number
  c: string
  /** Sand: a faint streak instead of a grain. */
  streak?: boolean
}

interface Kind {
  make: (w: number, h: number) => P[]
  step: (p: P, dt: number, t: number, w: number, h: number) => void
  draw: (ctx: CanvasRenderingContext2D, p: P, t: number, h: number) => void
}

const rand = (a: number, b: number) => a + Math.random() * (b - a)
const pick = <T,>(list: T[]) => list[Math.floor(Math.random() * list.length)]
const many = (n: number, one: () => P) => Array.from({ length: n }, one)
const base = (w: number, h: number, s: number, c: string): P => ({ x: rand(0, w), y: rand(0, h), s, vx: 0, vy: 0, r: rand(0, Math.PI * 2), vr: 0, ph: rand(0, Math.PI * 2), c })

/** A falling particle that left at the bottom comes back in at the top. */
const fall = (p: P, w: number, h: number) => {
  if (p.y > h + p.s * 2) Object.assign(p, { y: -p.s * 2, x: rand(0, w) })
}

/** A four-point sparkle centred on (x, y). */
function sparklePath(ctx: CanvasRenderingContext2D, x: number, y: number, r: number) {
  const k = r * 0.2
  ctx.beginPath()
  ctx.moveTo(x, y - r)
  ctx.quadraticCurveTo(x + k, y - k, x + r, y)
  ctx.quadraticCurveTo(x + k, y + k, x, y + r)
  ctx.quadraticCurveTo(x - k, y + k, x - r, y)
  ctx.quadraticCurveTo(x - k, y - k, x, y - r)
}

const KINDS: Partial<Record<FrameId, Kind>> = {
  // 18 six-arm snowflakes, 4–9 px, white with a light blue edge, falling slowly with a slight sway.
  frame_frost: {
    make: (w, h) => many(18, () => ({ ...base(w, h, rand(4, 9), '#fff'), vy: rand(10, 22), vr: rand(-0.6, 0.6) })),
    step: (p, dt, t, w, h) => {
      p.y += p.vy * dt
      p.x += Math.sin(t * 0.9 + p.ph) * 10 * dt
      p.r += p.vr * dt
      fall(p, w, h)
    },
    draw: (ctx, p) => {
      ctx.save()
      ctx.translate(p.x, p.y)
      ctx.rotate(p.r)
      ctx.lineCap = 'round'
      ctx.beginPath()
      for (let i = 0; i < 3; i++) {
        const a = (Math.PI / 3) * i
        const dx = Math.cos(a) * (p.s / 2)
        const dy = Math.sin(a) * (p.s / 2)
        ctx.moveTo(-dx, -dy)
        ctx.lineTo(dx, dy)
      }
      ctx.strokeStyle = '#8FD3FF'
      ctx.lineWidth = Math.max(1.6, p.s / 4)
      ctx.stroke()
      ctx.strokeStyle = 'rgba(255,255,255,0.9)'
      ctx.lineWidth = Math.max(0.8, p.s / 8)
      ctx.stroke()
      ctx.restore()
    },
  },
  // 22 embers, 2–4 px, amber and orange, rising and fading.
  frame_fire: {
    make: (w, h) => many(22, () => ({ ...base(w, h, rand(2, 4), pick(['#FFB300', '#FF6A00'])), vy: -rand(16, 34) })),
    step: (p, dt, t, w, h) => {
      p.y += p.vy * dt
      p.x += Math.sin(t * 2.4 + p.ph) * 8 * dt
      if (p.y < -p.s) Object.assign(p, { y: h + p.s, x: rand(0, w) })
    },
    draw: (ctx, p, _t, h) => {
      ctx.globalAlpha = Math.max(0, Math.min(1, 0.1 + p.y / h))
      ctx.fillStyle = p.c
      ctx.beginPath()
      ctx.arc(p.x, p.y, p.s / 2, 0, Math.PI * 2)
      ctx.fill()
      ctx.globalAlpha = 1
    },
  },
  // 12 four-point sparkles twinkling in place.
  frame_jewel: {
    make: (w, h) => many(12, () => base(w, h, rand(4, 8), '#E8B923')),
    step: () => undefined,
    draw: (ctx, p, t) => {
      ctx.globalAlpha = 0.2 + 0.8 * (0.5 + 0.5 * Math.sin(t * 2.2 + p.ph))
      ctx.fillStyle = p.c
      sparklePath(ctx, p.x, p.y, p.s)
      ctx.fill()
      ctx.fillStyle = '#fff'
      sparklePath(ctx, p.x, p.y, p.s * 0.45)
      ctx.fill()
      ctx.globalAlpha = 1
    },
  },
  // 30 sand grains, 1–3 px, blowing left → right on a wave, and 3 faint streaks among them.
  frame_sand: {
    make: (w, h) => [
      ...many(27, () => ({ ...base(w, h, rand(1, 3), pick(['#C99A5B', '#E6C48F'])), vx: rand(30, 60) })),
      ...many(3, () => ({ ...base(w, h, rand(40, 80), '#C99A5B'), vx: rand(80, 120), streak: true })),
    ],
    step: (p, dt, t, w) => {
      p.x += p.vx * dt
      p.y += Math.sin(t * 1.6 + p.ph + p.x * 0.02) * 10 * dt
      if (p.x > w + p.s) Object.assign(p, { x: -p.s })
    },
    draw: (ctx, p) => {
      if (p.streak) {
        ctx.globalAlpha = 0.18
        ctx.strokeStyle = p.c
        ctx.lineWidth = 1
        ctx.beginPath()
        ctx.moveTo(p.x - p.s, p.y)
        ctx.lineTo(p.x, p.y)
        ctx.stroke()
        ctx.globalAlpha = 1
        return
      }
      ctx.fillStyle = p.c
      ctx.beginPath()
      ctx.arc(p.x, p.y, p.s / 2, 0, Math.PI * 2)
      ctx.fill()
    },
  },
  // 14 petals (a teardrop, no lobes), falling with a slow turn.
  frame_sakura: {
    make: (w, h) => many(14, () => ({ ...base(w, h, rand(5, 9), '#FFB7CF'), vy: rand(14, 26), vr: rand(-1.2, 1.2) })),
    step: (p, dt, t, w, h) => {
      p.y += p.vy * dt
      p.x += Math.sin(t * 1.1 + p.ph) * 14 * dt
      p.r += p.vr * dt
      fall(p, w, h)
    },
    draw: (ctx, p) => {
      ctx.save()
      ctx.translate(p.x, p.y)
      ctx.rotate(p.r)
      ctx.globalAlpha = 0.9
      ctx.fillStyle = p.c
      ctx.beginPath()
      ctx.moveTo(0, -p.s / 2)
      ctx.quadraticCurveTo(p.s * 0.45, p.s * 0.05, 0, p.s / 2)
      ctx.quadraticCurveTo(-p.s * 0.45, p.s * 0.05, 0, -p.s / 2)
      ctx.fill()
      ctx.restore()
    },
  },
}

const FRAME_MS = 1000 / 30

/** The frame's particles on a canvas filling the card. The neon has none (its ring glows instead). */
function FrameParticles({ frame, still }: { frame: FrameId; still?: boolean }) {
  const ref = useRef<HTMLCanvasElement>(null)
  const kind = KINDS[frame]

  useEffect(() => {
    const canvas = ref.current
    const ctx = canvas?.getContext('2d')
    if (!canvas || !ctx || !kind) return
    const fixed = still || matchMedia('(prefers-reduced-motion: reduce)').matches
    let w = 0
    let h = 0
    let parts: P[] = []
    let t = 0
    let last = 0
    let raf = 0
    let onScreen = false

    const draw = () => {
      ctx.clearRect(0, 0, w, h)
      for (const p of parts) kind.draw(ctx, p, t, h)
    }
    const frameTick = (now: number) => {
      raf = requestAnimationFrame(frameTick)
      if (last && now - last < FRAME_MS - 1) return // at most 30 frames a second
      const dt = last ? Math.min(0.1, (now - last) / 1000) : 0
      last = now
      t += dt
      for (const p of parts) kind.step(p, dt, t, w, h)
      draw()
    }
    const run = () => {
      cancelAnimationFrame(raf)
      raf = 0
      last = 0
      if (!fixed && onScreen && document.visibilityState === 'visible') raf = requestAnimationFrame(frameTick)
    }
    const fit = () => {
      const box = canvas.getBoundingClientRect()
      if (!box.width || !box.height) return
      const dpr = Math.min(2, window.devicePixelRatio || 1)
      w = box.width
      h = box.height
      canvas.width = Math.round(w * dpr)
      canvas.height = Math.round(h * dpr)
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
      if (!parts.length) parts = kind.make(w, h)
      draw()
    }

    const resize = new ResizeObserver(fit)
    resize.observe(canvas)
    const seen = new IntersectionObserver(([e]) => {
      onScreen = e.isIntersecting
      run()
    })
    seen.observe(canvas)
    document.addEventListener('visibilitychange', run)
    fit()
    return () => {
      cancelAnimationFrame(raf)
      resize.disconnect()
      seen.disconnect()
      document.removeEventListener('visibilitychange', run)
    }
  }, [kind, still])

  if (!kind) return null
  return <canvas ref={ref} aria-hidden className="pointer-events-none absolute inset-0 size-full" />
}
