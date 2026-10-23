'use client'

import { useId } from 'react'
import type { BadgeId } from '@/lib/looks'

// The badges worn beside an @ID (040), drawn here like the Android app's (docs/LOOKS_SPEC.md): the same shapes and
// colours, no image files. 24 × 24 units, drawn at the ID's font size; strokes stay 1 px at any size.

/** A five-point star around (12, 13): outer radius 10, inner 4.2, the first point up. */
const STAR = Array.from({ length: 10 }, (_, i) => {
  const r = i % 2 ? 4.2 : 10
  const a = (Math.PI / 5) * i - Math.PI / 2
  return `${(12 + r * Math.cos(a)).toFixed(2)},${(13 + r * Math.sin(a)).toFixed(2)}`
}).join(' ')

/** A four-point sparkle (glints on the diamond and the star). */
const sparkle = (x: number, y: number, r: number) =>
  `M${x} ${y - r}Q${x + r * 0.18} ${y - r * 0.18} ${x + r} ${y}Q${x + r * 0.18} ${y + r * 0.18} ${x} ${y + r}Q${x - r * 0.18} ${y + r * 0.18} ${x - r} ${y}Q${x - r * 0.18} ${y - r * 0.18} ${x} ${y - r}Z`

const HEART = 'M12 21.2C6.2 16.9 2.6 13.4 2.6 9.1 2.6 6.2 4.9 4 7.7 4c1.8 0 3.3.9 4.3 2.3C13 4.9 14.5 4 16.3 4c2.8 0 5.1 2.2 5.1 5.1 0 4.3-3.6 7.8-9.4 12.1z'

/** A vertical gradient (top → bottom) under its own id. */
function Vertical({ id, from, to }: { id: string; from: string; to: string }) {
  return (
    <linearGradient id={id} x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stopColor={from} />
      <stop offset="1" stopColor={to} />
    </linearGradient>
  )
}

export function LookBadge({ id, className }: { id: BadgeId; className?: string }) {
  const g = useId().replace(/:/g, '')
  return (
    <svg viewBox="0 0 24 24" className={className} aria-hidden focusable="false">
      {id === 'badge_crown' ? (
        <>
          <defs>
            <Vertical id={g} from="#FFE27A" to="#F2A900" />
          </defs>
          <path d="M3 8.5l4.6 4.8L12 4.6l4.4 8.7L21 8.5l-1.6 8.4H4.6z" fill={`url(#${g})`} stroke="#B8860B" strokeWidth="1" strokeLinejoin="round" vectorEffect="non-scaling-stroke" />
          <rect x="4.4" y="16.6" width="15.2" height="3.6" rx="1" fill={`url(#${g})`} stroke="#B8860B" strokeWidth="1" vectorEffect="non-scaling-stroke" />
          <circle cx="3" cy="8.2" r="1.7" fill="#1E88E5" />
          <circle cx="12" cy="4.2" r="1.9" fill="#E53935" />
          <circle cx="21" cy="8.2" r="1.7" fill="#1E88E5" />
        </>
      ) : id === 'badge_diamond' ? (
        <>
          <defs>
            <Vertical id={g} from="#B9F3FF" to="#2BA8E0" />
          </defs>
          {/* Table (the flat top), crown (down to the girdle) and pavilion (down to the point). */}
          <path d="M7 4h10l4.5 5.2L12 21.2 2.5 9.2z" fill={`url(#${g})`} />
          <path d="M2.5 9.2h19M7 4l2.6 5.2L12 4l2.4 5.2L17 4M9.6 9.2L12 21.2l2.4-12" fill="none" stroke="#fff" strokeOpacity=".55" strokeWidth="1" strokeLinejoin="round" vectorEffect="non-scaling-stroke" />
          <path d={sparkle(6.6, 6.4, 2.3)} fill="#fff" />
        </>
      ) : id === 'badge_flame' ? (
        <>
          <defs>
            {/* Bottom → top. */}
            <linearGradient id={g} x1="0" y1="1" x2="0" y2="0">
              <stop offset="0" stopColor="#FF3D00" />
              <stop offset="1" stopColor="#FFB300" />
            </linearGradient>
          </defs>
          <path d="M12 1.8c.6 3.4 3.1 5.2 4.7 7.6 1.3 1.9 2 3.6 2 5.6 0 4-3 7.2-6.7 7.2S5.3 19 5.3 15c0-2.6 1.2-4.8 3-6.4.1 1.6.7 2.8 1.8 3.5-.2-3.6.6-7.2 1.9-10.3z" fill={`url(#${g})`} />
          <path d="M12 11.2c.9 1.6 2.9 2.9 2.9 5.4 0 1.8-1.3 3.3-2.9 3.3s-2.9-1.5-2.9-3.3c0-2.3 2-3.6 2.9-5.4z" fill="#FFE57F" />
        </>
      ) : id === 'badge_star' ? (
        <>
          <defs>
            <Vertical id={g} from="#FFE082" to="#FFB300" />
          </defs>
          <polygon points={STAR} fill={`url(#${g})`} />
          <path d={sparkle(20.2, 3.6, 2.6)} fill="#fff" />
        </>
      ) : id === 'badge_heart' ? (
        <>
          <defs>
            <Vertical id={g} from="#FF8AB3" to="#E91E63" />
          </defs>
          <path d={HEART} fill={`url(#${g})`} />
          <path d="M5.2 10.4c-.3-2.1 1-3.9 2.9-4.1" fill="none" stroke="#fff" strokeOpacity=".4" strokeWidth="1.8" strokeLinecap="round" />
        </>
      ) : (
        <>
          <defs>
            <Vertical id={g} from="#FFF176" to="#FBC02D" />
          </defs>
          <path d="M14 1.8L4.8 13.6h6.1L9.6 22.2l9.6-12.3h-6.2z" fill={`url(#${g})`} stroke="#8D6E00" strokeWidth="1" strokeLinejoin="round" vectorEffect="non-scaling-stroke" />
        </>
      )}
    </svg>
  )
}
