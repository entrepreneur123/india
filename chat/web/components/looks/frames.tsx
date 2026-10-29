'use client'

import { useId } from 'react'
import { Avatar } from '@/components/ui/avatar'
import type { FrameId } from '@/lib/looks'
import { cn } from '@/lib/utils'
import { UserAvatar } from './user-avatar'

// Profile frames (040, redrawn 2026-10-26): a turning conic ring with a travelling glint and a soft glow around the
// profile photo, and each frame's own decorations (snowflakes, flames, faceted gems, drifting sand, a neon orbit,
// petals). The card wash and particles went with the card (Instagram layout, 2026-10-29).

interface FrameDesign {
  /** The ring's colours, turning round (a conic gradient; the first colour closes the circle). */
  ring: string[]
}

const FRAME_DESIGNS: Record<FrameId, FrameDesign> = {
  frame_frost: { ring: ['#FFFFFF', '#BFEAFF', '#5CB8FF', '#2F7DE1', '#A9E2FF', '#FFFFFF', '#7CC8FF'] },
  frame_fire: { ring: ['#FFE66D', '#FF9A1F', '#FF4D00', '#C10000', '#FF6A00', '#FFD23F', '#FF3D00'] },
  frame_jewel: { ring: ['#FFF6CF', '#E8C252', '#9C7414', '#FFE9A1', '#C9971C', '#FFF3C4', '#8A6510'] },
  frame_sand: { ring: ['#FBE7C2', '#D9AE6C', '#9A6635', '#E9C792', '#7A4B24', '#F2D5A0'] },
  frame_neon: { ring: ['#00F0FF', '#7A5CFF', '#FF00E5', '#FF4FA3', '#00F0FF', '#00FFA3'] },
  frame_sakura: { ring: ['#FFE7EF', '#FFB3CC', '#FF7AA8', '#FFD6E4', '#FF92B8', '#FFF0F5'] },
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
