'use client'

import { useId } from 'react'
import type { BadgeId } from '@/lib/looks'
import { cn } from '@/lib/utils'

// The badges worn beside an @ID (040), redrawn 2026-10-28 (the owner: "childish, AI-looking"): one family of insignia
// instead of emoji-like pictures, as rank emblems and Discord's profile badges are. Each is a hexagonal pin: a metal
// rim, a dark enamel face and a clean symbol, with a band of light sweeping across now and then. 24 × 24 units, drawn
// at the ID's font size; no image files.

const hex = (r: number) =>
  Array.from({ length: 6 }, (_, i) => {
    const a = (Math.PI / 3) * i - Math.PI / 2
    return `${(12 + r * Math.cos(a)).toFixed(2)},${(12 + r * Math.sin(a)).toFixed(2)}`
  }).join(' ')
const star = (cx: number, cy: number, R: number, r: number) =>
  Array.from({ length: 10 }, (_, i) => {
    const d = i % 2 ? r : R
    const a = (Math.PI / 5) * i - Math.PI / 2
    return `${(cx + d * Math.cos(a)).toFixed(2)},${(cy + d * Math.sin(a)).toFixed(2)}`
  }).join(' ')
const RIM = hex(10.6)
const FACE = hex(8.7)

/** Rim metals, top → bottom. */
const METAL = {
  gold: ['#FFF4C2', '#E8B84A', '#9A6B12'],
  silver: ['#FFFFFF', '#C3CAD5', '#6E7787'],
  copper: ['#FFD9BF', '#D9824F', '#7E3A17'],
  rose: ['#FFE6E1', '#E7A59A', '#9E5A50'],
  graphite: ['#C9CED6', '#6B7280', '#252A33'],
} as const

interface Design {
  rim: keyof typeof METAL
  /** The enamel face and the symbol, top → bottom. */
  face: [string, string]
  glyph: [string, string]
  symbol: React.ReactNode
}

const DESIGNS: Record<BadgeId, Design> = {
  badge_crown: {
    rim: 'gold', face: ['#3B2606', '#140C02'], glyph: ['#FFF1B8', '#F2C14E'],
    symbol: (
      <>
        <path d="M6.6 15.6 7.3 9.3l2.9 2.6L12 7.4l1.8 4.5 2.9-2.6.7 6.3z" />
        <rect x="6.6" y="16.4" width="10.8" height="1.6" rx=".5" />
      </>
    ),
  },
  badge_diamond: {
    rim: 'silver', face: ['#0E3160', '#05142B'], glyph: ['#FFFFFF', '#9FE3FF'],
    symbol: (
      <>
        <path d="M8.6 7.6h6.8l2.6 3.2L12 18.4 6 10.8z" />
        {/* The facets: table, crown and pavilion lines. */}
        <path d="M6 10.8h12M8.6 7.6l1.7 3.2L12 7.6l1.7 3.2 1.7-3.2M10.3 10.8 12 18.4l1.7-7.6" fill="none" stroke="#0E3160" strokeOpacity=".45" strokeWidth=".6" strokeLinejoin="round" />
      </>
    ),
  },
  badge_flame: {
    rim: 'copper', face: ['#45100A', '#1C0503'], glyph: ['#FFE08A', '#FF6A1A'],
    symbol: <path d="M12 6.2c.4 2.2 2 3.4 3 5 .8 1.2 1.2 2.3 1.2 3.6 0 2.5-1.9 4.4-4.2 4.4s-4.2-1.9-4.2-4.4c0-1.7.8-3.1 1.9-4.1.1 1 .5 1.8 1.2 2.2-.1-2.3.3-4.6 1.1-6.7z" />,
  },
  badge_star: {
    rim: 'silver', face: ['#25215E', '#0E0B2B'], glyph: ['#FFF4C2', '#F2C14E'],
    symbol: <polygon points={star(12, 12.6, 6, 2.6)} />,
  },
  badge_heart: {
    rim: 'rose', face: ['#4F0A25', '#220410'], glyph: ['#FFC2D6', '#FF4D8D'],
    symbol: <path d="M12 18.2c-3.6-2.7-5.6-4.7-5.6-7.2 0-1.7 1.3-3 3-3 1.1 0 2 .6 2.6 1.4.6-.8 1.5-1.4 2.6-1.4 1.7 0 3 1.3 3 3 0 2.5-2 4.5-5.6 7.2z" />,
  },
  badge_bolt: {
    rim: 'graphite', face: ['#1A1F2B', '#07090D'], glyph: ['#FFF7A8', '#FACC15'],
    symbol: <path d="M13.4 5.8 7.8 13h3.6l-1 5.2 5.8-7.4h-3.7z" />,
  },
  badge_moon: {
    rim: 'silver', face: ['#152042', '#060B1C'], glyph: ['#FFFFFF', '#C9D4FF'],
    symbol: (
      <>
        <path d="M14.6 17.6A6 6 0 1 1 13.1 6.3a4.7 4.7 0 0 0 1.5 11.3z" />
        <polygon points={star(16.2, 8.6, 1.7, 0.7)} />
      </>
    ),
  },
  badge_lotus: {
    rim: 'gold', face: ['#3E0A4F', '#190322'], glyph: ['#FFD6EC', '#F062A8'],
    symbol: (
      <>
        <path d="M12 6.6c1.3 1.4 2 3.2 2 5.1s-.7 3.7-2 5.1c-1.3-1.4-2-3.2-2-5.1s.7-3.7 2-5.1z" />
        <path d="M6.4 10.4c2.2.2 4 1.4 5 3.2.5 1 .6 2.1.6 3.2-2.3-.1-4.1-1.3-5-3.2-.5-1-.7-2.1-.6-3.2zM17.6 10.4c-2.2.2-4 1.4-5 3.2-.5 1-.6 2.1-.6 3.2 2.3-.1 4.1-1.3 5-3.2.5-1 .7-2.1.6-3.2z" fillOpacity=".85" />
      </>
    ),
  },
}

const Vertical = ({ id, stops }: { id: string; stops: readonly string[] }) => (
  <linearGradient id={id} x1="0" y1="0" x2="0" y2="1">
    {stops.map((c, i) => (
      <stop key={i} offset={i / (stops.length - 1)} stopColor={c} />
    ))}
  </linearGradient>
)

export function LookBadge({ id, className }: { id: BadgeId; className?: string }) {
  const g = useId().replace(/:/g, '')
  const d = DESIGNS[id]
  return (
    <svg viewBox="0 0 24 24" className={cn('overflow-visible drop-shadow-[0_1px_1px_rgb(0_0_0/0.25)]', className)} aria-hidden focusable="false">
      <defs>
        <Vertical id={`${g}r`} stops={METAL[d.rim]} />
        <Vertical id={`${g}f`} stops={d.face} />
        <Vertical id={`${g}g`} stops={d.glyph} />
        <clipPath id={`${g}c`}>
          <polygon points={RIM} />
        </clipPath>
        <linearGradient id={`${g}s`} x1="0" y1="0" x2="1" y2="0">
          <stop offset="0" stopColor="#fff" stopOpacity="0" />
          <stop offset="0.5" stopColor="#fff" stopOpacity="0.5" />
          <stop offset="1" stopColor="#fff" stopOpacity="0" />
        </linearGradient>
      </defs>
      {/* The rounded corners are the strokes' round joins. */}
      <polygon points={RIM} fill={`url(#${g}r)`} stroke={`url(#${g}r)`} strokeWidth="1.6" strokeLinejoin="round" />
      <polygon points={FACE} fill={`url(#${g}f)`} stroke={`url(#${g}f)`} strokeWidth="1" strokeLinejoin="round" />
      <polygon points={FACE} fill="none" stroke="#000" strokeOpacity=".35" strokeWidth=".5" strokeLinejoin="round" />
      {/* The enamel's top catches a little light. */}
      <path d="M4.6 7.6 12 3.3l7.4 4.3V11C15 9.6 9 9.6 4.6 11z" fill="#fff" fillOpacity=".07" />
      <g fill={`url(#${g}g)`}>{d.symbol}</g>
      <g clipPath={`url(#${g}c)`}>
        <rect className="look-sheen" x="2" y="-2" width="8" height="28" fill={`url(#${g}s)`} />
      </g>
    </svg>
  )
}
