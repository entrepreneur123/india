'use client'

import { useEffect, useId, useRef, type ReactNode } from 'react'
import { Avatar } from '@/components/ui/avatar'
import type { FrameId } from '@/lib/looks'
import { cn } from '@/lib/utils'
import { UserAvatar } from './user-avatar'

// Profile frames (040, redrawn 2026-10-26): a turning conic ring with a travelling glint and a soft glow, around the
// profile card and the big photo; around the photo each frame adds its own decorations (snowflakes, flames, faceted
// gems, drifting sand, a neon orbit, petals); the card gets a wash at the top and light particles behind its content
// (≤ 30 particles at ≤ 30 fps, paused off screen and in a background tab). Reduced motion stills them.

interface FrameDesign {
  /** The ring's colours, turning round (a conic gradient; the first colour closes the circle). */
  ring: string[]
  /** The wash at the top of the card. */
  wash: string
}

const FRAME_DESIGNS: Record<FrameId, FrameDesign> = {
  frame_frost: { ring: ['#FFFFFF', '#BFEAFF', '#5CB8FF', '#2F7DE1', '#A9E2FF', '#FFFFFF', '#7CC8FF'], wash: '#DFF4FF' },
  frame_fire: { ring: ['#FFE66D', '#FF9A1F', '#FF4D00', '#C10000', '#FF6A00', '#FFD23F', '#FF3D00'], wash: '#FFF1E6' },
  frame_jewel: { ring: ['#FFF6CF', '#E8C252', '#9C7414', '#FFE9A1', '#C9971C', '#FFF3C4', '#8A6510'], wash: '#FFF8E1' },
  frame_sand: { ring: ['#FBE7C2', '#D9AE6C', '#9A6635', '#E9C792', '#7A4B24', '#F2D5A0'], wash: '#FFF6E6' },
  frame_neon: { ring: ['#00F0FF', '#7A5CFF', '#FF00E5', '#FF4FA3', '#00F0FF', '#00FFA3'], wash: '#E9FDFF' },
  frame_sakura: { ring: ['#FFE7EF', '#FFB3CC', '#FF7AA8', '#FFD6E4', '#FF92B8', '#FFF0F5'], wash: '#FFF0F5' },
}

const conic = (d: FrameDesign) => `conic-gradient(from var(--look-a), ${d.ring.join(', ')}, ${d.ring[0]})`
/** A light glint travelling round the ring the other way. */
const GLINT = 'conic-gradient(from var(--look-a), transparent 0deg 290deg, rgb(255 255 255 / 0.95) 325deg, transparent 360deg)'

/** The turning ring with its glint (under the content), and a soft glow of the same colours behind it. */
function Ring({ frame, glow }: { frame: FrameId; glow: number }) {
  const d = FRAME_DESIGNS[frame]
  return (
    <>
      <span
        aria-hidden
        className="look-turn pointer-events-none absolute -inset-1 rounded-[inherit]"
        style={{ backgroundImage: conic(d), filter: `blur(${glow}px)`, opacity: frame === 'frame_neon' ? 0.95 : 0.6 }}
      />
      <span aria-hidden className="look-turn pointer-events-none absolute inset-0 rounded-[inherit]" style={{ backgroundImage: conic(d) }} />
      <span aria-hidden className="look-turn-fast pointer-events-none absolute inset-0 rounded-[inherit]" style={{ backgroundImage: GLINT }} />
    </>
  )
}

/**
 * A profile card in a frame: the ring, the wash and the particles behind `children`. Without a frame, a plain card.
 * `still`: the particles don't move (small previews).
 */
export function FrameCard({ frame, still, className, children }: { frame: FrameId | null; still?: boolean; className?: string; children: ReactNode }) {
  const d = frame ? FRAME_DESIGNS[frame] : null
  return (
    <div className={cn('relative rounded-[28px]', d ? 'p-[3px]' : 'ring-1 ring-border', className)}>
      {frame ? <Ring frame={frame} glow={10} /> : null}
      <div className={cn('relative isolate h-full overflow-hidden bg-card', d ? 'rounded-[25px]' : 'rounded-[28px]')}>
        {frame && d ? (
          <>
            <span
              aria-hidden
              className="pointer-events-none absolute inset-0"
              style={{ backgroundImage: `linear-gradient(to bottom, ${d.wash}, transparent 60%)` }}
            />
            <FrameParticles frame={frame} still={still} />
          </>
        ) : null}
        <div className="relative">{children}</div>
      </div>
    </div>
  )
}

/**
 * The big photo in its frame: a glow, the turning ring with its glint, a thin gap in the card's colour, the photo, and
 * the frame's own decorations around it (snowflakes, flames, gems, drifting sand, a neon orbit, petals). The frame
 * takes the same room as before; its glow and decorations spill over the edge. Plain without a frame.
 */
export function FrameAvatar({ frame, userId, name, src, size, className }: { frame: FrameId | null; userId?: string; name: string; src?: string | null; size: number; className?: string }) {
  const ring = frame ? Math.max(3, Math.round(size * 0.05)) : 0
  const gap = frame ? Math.max(2, Math.round(size * 0.025)) : 3
  const outer = size + 2 * (ring + gap)
  return (
    <span className={cn('relative inline-grid shrink-0 place-items-center rounded-full', !frame && 'shadow-e2', className)} style={{ width: outer, height: outer }}>
      {frame ? <Ring frame={frame} glow={Math.max(4, size * 0.07)} /> : null}
      <span className="relative grid place-items-center rounded-full bg-card" style={{ padding: gap, margin: ring }}>
        {userId ? <UserAvatar userId={userId} name={name} src={src} size={size} /> : <Avatar name={name} src={src} size={size} />}
      </span>
      {frame === 'frame_fire' ? <FireRing size={size} outer={outer} /> : frame === 'frame_jewel' ? <DiamondHalo size={size} outer={outer} /> : frame ? <Decor frame={frame} ring={ring} outer={outer} /> : null}
    </span>
  )
}

/** Points round a circle of radius `r` (viewBox units) around (50, 50), the first at `start` degrees. */
const around = (n: number, r: number, start = -90) =>
  Array.from({ length: n }, (_, i) => {
    const deg = start + (360 / n) * i
    const a = (deg * Math.PI) / 180
    return { x: 50 + r * Math.cos(a), y: 50 + r * Math.sin(a), deg, i }
  })
const delay = (i: number, step: number) => ({ animationDelay: `${-i * step}s` })

/** The decorations, drawn in a 100 × 100 box that reaches 16 % past the frame on every side. */
function Decor({ frame, ring, outer }: { frame: FrameId; ring: number; outer: number }) {
  const g = useId().replace(/:/g, '')
  const spill = outer * 0.16
  const box = outer + 2 * spill
  const r = ((outer / 2 - ring / 2) / box) * 100 // the ring's middle line
  const unit = 100 / box // one CSS pixel in viewBox units
  const s = Math.max(outer * 0.07, 3) * unit // a decoration's size, ~7 % of the frame
  return (
    <svg aria-hidden viewBox="0 0 100 100" className="pointer-events-none absolute overflow-visible" style={{ inset: -spill }}>
      <defs>
        <radialGradient id={`${g}p`} cx="0.4" cy="0.35" r="0.8">
          <stop offset="0" stopColor="#FFF5F8" />
          <stop offset="1" stopColor="#FF8DB5" />
        </radialGradient>
        <filter id={`${g}b`} x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur stdDeviation={s * 0.12} />
        </filter>
      </defs>
      {frame === 'frame_frost'
        ? around(6, r, -75).map(({ x, y, i }) => (
            <g key={i} transform={`translate(${x} ${y})`}>
              <g className="look-twinkle" style={{ ...delay(i, 0.4), transformBox: 'fill-box', transformOrigin: 'center' }}>
                {[0, 60, 120].map((rot) => (
                  <path key={rot} d={`M0 ${-s} V${s} M${-s * 0.3} ${-s * 0.7} L0 ${-s * 0.45} L${s * 0.3} ${-s * 0.7} M${-s * 0.3} ${s * 0.7} L0 ${s * 0.45} L${s * 0.3} ${s * 0.7}`}
                    transform={`rotate(${rot})`} stroke="#fff" strokeWidth={s * 0.16} strokeLinecap="round" fill="none" style={{ filter: 'drop-shadow(0 0 1.5px #5CB8FF)' }} />
                ))}
              </g>
            </g>
          ))
        : frame === 'frame_sand'
              ? (
                  <g className="look-spin" style={{ transformBox: 'view-box', transformOrigin: '50% 50%' }}>
                    {around(18, r, -90).map(({ i, deg }) => {
                      const rr = r + ((i * 37) % 7) * unit * outer * 0.012 - outer * 0.03 * unit
                      const a = ((deg + (i % 3) * 7) * Math.PI) / 180
                      return <circle key={i} cx={50 + rr * Math.cos(a)} cy={50 + rr * Math.sin(a)} r={s * (0.12 + (i % 4) * 0.06)} fill={['#F2D5A0', '#C99A5B', '#8B5E34'][i % 3]} opacity={0.5 + (i % 3) * 0.2} />
                    })}
                  </g>
                )
              : frame === 'frame_neon'
                ? (
                    <g className="look-spin" style={{ transformBox: 'view-box', transformOrigin: '50% 50%', animationDuration: '6s' }}>
                      <circle cx="50" cy="50" r={r + s * 0.65} fill="none" stroke="#00F0FF" strokeWidth={s * 0.14} strokeDasharray={`${s * 1.6} ${s * 2.4}`} strokeLinecap="round" filter={`url(#${g}b)`} />
                      <circle cx="50" cy="50" r={r + s * 0.65} fill="none" stroke="#FF00E5" strokeWidth={s * 0.1} strokeDasharray={`${s * 0.8} ${s * 3.2}`} strokeDashoffset={s * 2} strokeLinecap="round" />
                    </g>
                  )
                : around(6, r + s * 0.2, -60).map(({ x, y, deg, i }) => (
                    <g key={i} transform={`translate(${x} ${y}) rotate(${deg + 90})`}>
                      <path className="look-sway" style={delay(i, 0.6)} fill={`url(#${g}p)`} stroke="#FF6FA3" strokeWidth={s * 0.05}
                        d={`M0 ${s * 0.6} C${-s * 0.75} ${s * 0.1} ${-s * 0.55} ${-s * 0.75} ${-s * 0.1} ${-s * 0.7} L0 ${-s * 0.45} L${s * 0.1} ${-s * 0.7} C${s * 0.55} ${-s * 0.75} ${s * 0.75} ${s * 0.1} 0 ${s * 0.6}Z`} />
                    </g>
                  ))}
    </svg>
  )
}

/**
 * Blaze (redrawn 2026-10-28, the owner: "the fire is low quality"): real-looking fire instead of cartoon tongues. A
 * glow from white-hot at the photo's edge to dark red is torn into flames by moving noise (an SVG turbulence that
 * rises through a displacement map), so the flames flicker upwards like a fire's.
 */
function FireRing({ size, outer }: { size: number; outer: number }) {
  const g = useId().replace(/:/g, '')
  const pad = size * 0.22
  const box = size + 2 * pad
  const c = box / 2
  const r = size / 2 + size * 0.04
  const at = (d: number) => (r + size * d) / c
  return (
    <svg aria-hidden width={box} height={box} viewBox={`0 0 ${box} ${box}`} className="pointer-events-none absolute overflow-visible" style={{ inset: (outer - box) / 2 }}>
      <defs>
        <radialGradient id={`${g}g`}>
          {/* Clear over the photo; white-hot at its edge. */}
          <stop offset={at(-0.05)} stopColor="#FFF3B0" stopOpacity="0" />
          <stop offset={at(-0.02)} stopColor="#FFF3B0" />
          <stop offset={at(0.03)} stopColor="#FFB020" />
          <stop offset={at(0.08)} stopColor="#FF5A00" stopOpacity=".95" />
          <stop offset={at(0.14)} stopColor="#D42000" stopOpacity=".55" />
          <stop offset={at(0.19)} stopColor="#8A0E00" stopOpacity="0" />
        </radialGradient>
        {/* One tile of noise the size of the box, repeated downwards and moved up by exactly one tile per loop: no seam. */}
        <filter id={`${g}f`} x="-10%" y="-10%" width="120%" height="220%">
          <feTurbulence x="0" y="0" width={box} height={box} type="fractalNoise" baseFrequency={`${(18 / size).toFixed(3)} ${(10 / size).toFixed(3)}`} numOctaves={2} seed={3} stitchTiles="stitch" />
          <feTile result="t" />
          <feOffset in="t" dy="0" result="n">
            <animate attributeName="dy" from="0" to={-box} dur="2.4s" repeatCount="indefinite" />
          </feOffset>
          <feDisplacementMap in="SourceGraphic" in2="n" scale={size * 0.16} xChannelSelector="R" yChannelSelector="G" />
        </filter>
      </defs>
      <circle cx={c} cy={c} r={c} fill={`url(#${g}g)`} filter={`url(#${g}f)`} />
      <circle cx={c} cy={c} r={r} fill="none" stroke="#FFD27A" strokeWidth={size * 0.02} opacity=".9" />
    </svg>
  )
}

/**
 * Jewels (redrawn 2026-10-28): a halo of round brilliants on a gold band, as in a halo ring; each stone has its cut
 * lines and a highlight, and a glint flashes on a few of them in turn.
 */
function DiamondHalo({ size, outer }: { size: number; outer: number }) {
  const g = useId().replace(/:/g, '')
  const pad = size * 0.12
  const box = size + 2 * pad
  const c = box / 2
  const r = size / 2 + size * 0.055
  const n = size > 60 ? 30 : 18
  const gs = ((2 * Math.PI * r) / n) * 0.4
  const at = (i: number) => {
    const a = (2 * Math.PI * i) / n - Math.PI / 2
    return { x: c + r * Math.cos(a), y: c + r * Math.sin(a) }
  }
  const k = gs * 2.2
  return (
    <svg aria-hidden width={box} height={box} viewBox={`0 0 ${box} ${box}`} className="pointer-events-none absolute overflow-visible" style={{ inset: (outer - box) / 2 }}>
      <defs>
        <linearGradient id={`${g}b`} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#FFF1C1" />
          <stop offset=".45" stopColor="#D4A93F" />
          <stop offset=".7" stopColor="#8F6A16" />
          <stop offset="1" stopColor="#F2D27E" />
        </linearGradient>
        <radialGradient id={`${g}d`} cx="40%" cy="35%" r="75%">
          <stop offset="0" stopColor="#FFFFFF" />
          <stop offset=".35" stopColor="#DDEBFA" />
          <stop offset=".7" stopColor="#8FA9C6" />
          <stop offset="1" stopColor="#F4F8FF" />
        </radialGradient>
      </defs>
      <circle cx={c} cy={c} r={r} fill="none" stroke={`url(#${g}b)`} strokeWidth={gs * 2.7} />
      <circle cx={c} cy={c} r={r - gs * 1.45} fill="none" stroke="#7A5A10" strokeOpacity=".5" strokeWidth={Math.max(0.6, size * 0.006)} />
      {Array.from({ length: n }, (_, i) => {
        const { x, y } = at(i)
        return (
          <g key={i} transform={`translate(${x} ${y})`}>
            <circle r={gs} fill={`url(#${g}d)`} stroke="#B8902E" strokeWidth={gs * 0.28} />
            <path d={`M${-gs * 0.55} 0H${gs * 0.55}M0 ${-gs * 0.55}V${gs * 0.55}M${-gs * 0.38} ${-gs * 0.38}L${gs * 0.38} ${gs * 0.38}M${-gs * 0.38} ${gs * 0.38}L${gs * 0.38} ${-gs * 0.38}`} stroke="#fff" strokeOpacity=".55" strokeWidth={gs * 0.12} />
            <circle cx={-gs * 0.3} cy={-gs * 0.3} r={gs * 0.22} fill="#fff" />
          </g>
        )
      })}
      {[3, 11, 19, 26].filter((i) => i < n).map((i, j) => {
        const { x, y } = at(i)
        return (
          <g key={i} transform={`translate(${x - gs * 0.4} ${y - gs * 0.4})`}>
            <path className="look-twinkle" style={{ ...delay(j, 0.6), transformBox: 'fill-box', transformOrigin: 'center' }} fill="#fff"
              d={`M0 ${-k}Q${k * 0.15} ${-k * 0.15} ${k} 0Q${k * 0.15} ${k * 0.15} 0 ${k}Q${-k * 0.15} ${k * 0.15} ${-k} 0Q${-k * 0.15} ${-k * 0.15} 0 ${-k}Z`} />
          </g>
        )
      })}
    </svg>
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
