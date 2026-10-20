'use client'

import { useEffect, useId, useRef, type CSSProperties, type KeyboardEvent } from 'react'
import { HOME, spot, TRACK } from '@/lib/ludo'

/**
 * Colours of the board, muted rather than toy-bright: warm ivory in the light theme, deep slate at night (voice
 * rooms). CSS variables, so the board follows the theme it sits in. Seats: red, green, yellow, blue.
 */
export const LUDO_CSS = `.ludo{--ludo-0:#d8463e;--ludo-1:#1f9862;--ludo-2:#e2a31c;--ludo-3:#2e70d0;--ludo-frame:#ebe4d8;--ludo-tile:#fffdf8;--ludo-inner:#fffdf8;--ludo-star:rgb(60 45 20/.3);--ludo-halo:#fff;--ludo-wash:10%}
.theme-night .ludo{--ludo-0:#e0574e;--ludo-1:#2ba673;--ludo-2:#e7b03a;--ludo-3:#4a86e2;--ludo-frame:#121216;--ludo-tile:#262630;--ludo-inner:#18181e;--ludo-star:rgb(255 255 255/.32);--ludo-halo:#f4f2ee;--ludo-wash:16%}`

export const PALETTE = {
  seats: ['var(--ludo-0)', 'var(--ludo-1)', 'var(--ludo-2)', 'var(--ludo-3)'],
  frame: 'var(--ludo-frame)',
  tile: 'var(--ludo-tile)',
  inner: 'var(--ludo-inner)',
  star: 'var(--ludo-star)',
  halo: 'var(--ludo-halo)',
}
export type Palette = typeof PALETTE

const mix = (c: string, pct: number, base: string) => `color-mix(in srgb, ${c} ${pct}%, ${base})`

/** A five-point star, centred on (cx, cy). */
function starPath(cx: number, cy: number, r: number) {
  const pts: string[] = []
  for (let i = 0; i < 10; i++) {
    const a = (Math.PI / 5) * i - Math.PI / 2
    const rr = i % 2 ? r * 0.45 : r
    pts.push(`${(cx + rr * Math.cos(a)).toFixed(3)} ${(cy + rr * Math.sin(a)).toFixed(3)}`)
  }
  return `M${pts.join('L')}Z`
}

const tile = (c: number, r: number, fill: string, key?: string | number) => (
  <rect key={key} x={c + 0.05} y={r + 0.05} width={0.9} height={0.9} rx={0.14} fill={fill} />
)

/** Red's quarter of the board (base, start, home column, arrow, centre triangle); the others are it turned. */
function Quarter({ seat, p, ids, glow, reduce }: { seat: number; p: Palette; ids: string; glow: boolean; reduce: boolean }) {
  const c = p.seats[seat]
  return (
    <g transform={`rotate(${seat * 90} 7.5 7.5)`}>
      {/* Base */}
      <rect x={0.14} y={9.14} width={5.72} height={5.72} rx={0.42} fill={`url(#${ids}-base-${seat})`} />
      {glow ? (
        <rect x={0.14} y={9.14} width={5.72} height={5.72} rx={0.42} fill="none" stroke={p.halo} strokeWidth={0.08} opacity={0.7}>
          {reduce ? null : <animate attributeName="opacity" values="0.15;0.75;0.15" dur="1.6s" repeatCount="indefinite" />}
        </rect>
      ) : null}
      <rect x={0.9} y={9.9} width={4.2} height={4.2} rx={0.5} fill={p.inner} />
      <rect x={0.9} y={9.9} width={4.2} height={4.2} rx={0.5} fill="none" stroke={mix(c, 30, 'transparent')} strokeWidth={0.04} />
      {[
        [2, 11],
        [4, 11],
        [2, 13],
        [4, 13],
      ].map(([x, y]) => (
        <g key={`${x}${y}`}>
          <circle cx={x} cy={y} r={0.64} fill={mix(c, 16, p.inner)} />
          <circle cx={x} cy={y} r={0.64} fill="none" stroke={mix(c, 45, 'transparent')} strokeWidth={0.05} />
        </g>
      ))}
      {/* Start square (safe) and the home column */}
      {tile(6, 13, c)}
      <path d={starPath(6.5, 13.5, 0.3)} fill="none" stroke="#fff" strokeWidth={0.06} strokeLinejoin="round" opacity={0.9} />
      {[13, 12, 11, 10, 9].map((r) => tile(7, r, mix(c, 88, p.tile), r))}
      {/* The way in: an arrow on the square before the home column */}
      <path d="M7.22 14.72 7.5 14.36 7.78 14.72" fill="none" stroke={c} strokeWidth={0.11} strokeLinecap="round" strokeLinejoin="round" />
      {/* Star square (safe) */}
      <path d={starPath(2.5, 8.5, 0.3)} fill={p.star} />
      {/* Centre */}
      <path d="M6.04 8.96 8.96 8.96 7.5 7.5Z" fill={`url(#${ids}-tri-${seat})`} />
    </g>
  )
}

export interface TokenView {
  /** Global index seat * 4 + k. */
  idx: number
  pos: number
  label: string
  /** May be moved now (pulses, is a button). */
  movable: boolean
  /** How it got here: one square at a time, or sliding home after a capture. */
  motion?: 'hop' | 'slide'
}

export function LudoBoard({
  tokens,
  seats,
  turn,
  reduce,
  label,
  onToken,
  className,
  style,
}: {
  tokens: TokenView[]
  /** Colours in play (others are drawn without tokens). */
  seats: boolean[]
  turn: number | null
  reduce: boolean
  label: string
  onToken?: (idx: number) => void
  className?: string
  style?: CSSProperties
}) {
  const ids = useId().replace(/:/g, '')
  const p = PALETTE
  const starts = new Set([0, 13, 26, 39])

  // Tokens sharing a square stand side by side, smaller.
  const groups = new Map<string, number[]>()
  for (const t of tokens) {
    const [x, y] = spot(t.idx >> 2, t.pos, t.idx & 3)
    const key = t.pos === HOME ? `h${t.idx}` : `${x.toFixed(2)},${y.toFixed(2)}`
    groups.set(key, [...(groups.get(key) ?? []), t.idx])
  }
  const place = (t: TokenView) => {
    const [x, y] = spot(t.idx >> 2, t.pos, t.idx & 3)
    if (t.pos === HOME) return { x, y, scale: 0.62 }
    const key = `${x.toFixed(2)},${y.toFixed(2)}`
    const g = groups.get(key) ?? [t.idx]
    if (g.length === 1) return { x, y, scale: 1 }
    const i = g.indexOf(t.idx)
    const off = g.length === 2 ? [[-0.2, 0], [0.2, 0]][i] : [[-0.2, -0.2], [0.2, -0.2], [-0.2, 0.2], [0.2, 0.2]][i % 4]
    return { x: x + off[0], y: y + off[1], scale: 0.7 }
  }
  // The player to move is drawn on top, the token being moved above everything.
  const order = [...tokens].sort((a, b) => Number(a.idx >> 2 === turn) - Number(b.idx >> 2 === turn) || Number(!!a.motion) - Number(!!b.motion))

  return (
    <svg viewBox="0 0 15 15" role="group" aria-label={label} className={className} style={style}>
      <defs>
        {p.seats.map((c, s) => (
          <g key={s}>
            <linearGradient id={`${ids}-base-${s}`} x1="0" y1="0" x2="1" y2="1" gradientTransform={`rotate(${-s * 90} .5 .5)`}>
              <stop offset="0" stopColor={mix(c, 82, '#fff')} />
              <stop offset="1" stopColor={mix(c, 86, '#000')} />
            </linearGradient>
            <linearGradient id={`${ids}-tri-${s}`} x1="0.5" y1="1" x2="0.5" y2="0">
              <stop offset="0" stopColor={c} />
              <stop offset="1" stopColor={mix(c, 70, '#000')} />
            </linearGradient>
            <radialGradient id={`${ids}-tok-${s}`} cx="0.36" cy="0.3" r="0.8">
              <stop offset="0" stopColor={mix(c, 45, '#fff')} />
              <stop offset="0.45" stopColor={c} />
              <stop offset="1" stopColor={mix(c, 62, '#000')} />
            </radialGradient>
          </g>
        ))}
        <radialGradient id={`${ids}-shadow`}>
          <stop offset="0" stopColor="#000" stopOpacity="0.4" />
          <stop offset="1" stopColor="#000" stopOpacity="0" />
        </radialGradient>
      </defs>
      <rect width={15} height={15} rx={0.5} fill={p.frame} />
      {TRACK.map(([c, r], i) => (starts.has(i) ? null : tile(c, r, p.tile, i)))}
      <rect x={6.05} y={6.05} width={2.9} height={2.9} rx={0.14} fill={p.tile} />
      {[0, 1, 2, 3].map((s) => (
        <Quarter key={s} seat={s} p={p} ids={ids} glow={turn === s} reduce={reduce} />
      ))}
      <circle cx={7.5} cy={7.5} r={0.2} fill={p.tile} />
      <g>
        {order.map((t) =>
          seats[t.idx >> 2] ? (
            <Token key={t.idx} t={t} {...place(t)} ids={ids} halo={p.halo} colour={p.seats[t.idx >> 2]} reduce={reduce} onToken={onToken} />
          ) : null,
        )}
      </g>
    </svg>
  )
}

function Token({
  t,
  x,
  y,
  scale,
  ids,
  halo,
  colour,
  reduce,
  onToken,
}: {
  t: TokenView
  x: number
  y: number
  scale: number
  ids: string
  halo: string
  colour: string
  reduce: boolean
  onToken?: (idx: number) => void
}) {
  const seat = t.idx >> 2
  const body = useRef<SVGGElement>(null)
  const pos = useRef(t.pos)

  // Each square of a move is a little hop.
  useEffect(() => {
    if (pos.current === t.pos) return
    pos.current = t.pos
    if (reduce || t.motion !== 'hop') return
    body.current?.animate(
      [{ transform: 'translateY(0) scale(1)' }, { transform: 'translateY(-0.32px) scale(1.12)' }, { transform: 'translateY(0) scale(1)' }],
      { duration: 130, easing: 'ease-out' },
    )
  }, [t.pos, t.motion, reduce])

  const act = () => t.movable && onToken?.(t.idx)
  const key = (e: KeyboardEvent) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault()
      act()
    }
  }
  const move = reduce ? undefined : t.motion === 'slide' ? 'transform 420ms cubic-bezier(.3,0,.2,1)' : 'transform 120ms ease-out'

  return (
    <g
      role="button"
      tabIndex={t.movable ? 0 : -1}
      aria-label={t.label}
      aria-disabled={!t.movable}
      onClick={act}
      onKeyDown={key}
      style={{ transform: `translate(${x}px, ${y}px)`, transition: move, cursor: t.movable ? 'pointer' : undefined }}
    >
      <g style={{ transform: `scale(${scale})`, transition: reduce ? undefined : 'transform 160ms ease-out' }}>
        {t.movable ? (
          // A steady ring (it reads in a still frame and under reduced motion) and a ripple going out of it.
          <g>
            <circle r={0.47} fill={colour} fillOpacity={0.28} stroke={halo} strokeWidth={0.07} />
            {reduce ? null : (
              <circle r={0.47} fill="none" stroke={halo} strokeWidth={0.05}>
                <animate attributeName="r" values="0.47;0.66" dur="1.1s" repeatCount="indefinite" />
                <animate attributeName="opacity" values="0.9;0" dur="1.1s" repeatCount="indefinite" />
              </circle>
            )}
          </g>
        ) : null}
        <ellipse cx={0} cy={0.31} rx={0.34} ry={0.11} fill={`url(#${ids}-shadow)`} />
        <g ref={body} style={{ transformBox: 'fill-box', transformOrigin: '50% 100%' }}>
          {t.movable && !reduce ? (
            <animateTransform attributeName="transform" type="translate" values="0 0;0 -0.07;0 0" dur="0.9s" repeatCount="indefinite" />
          ) : null}
          {/* A white rim keeps the pawn readable on its own colour */}
          <g fill={halo} stroke={halo} strokeWidth={0.1} strokeLinejoin="round">
            <path d="M-.3 .25C-.3 .09-.17-.03-.11-.09H.11C.17-.03.3.09.3.25C.3.34-.3.34-.3.25Z" />
            <circle cy={-0.25} r={0.17} />
          </g>
          <path d="M-.3 .25C-.3 .09-.17-.03-.11-.09H.11C.17-.03.3.09.3.25C.3.34-.3.34-.3.25Z" fill={`url(#${ids}-tok-${seat})`} />
          <ellipse cy={-0.09} rx={0.15} ry={0.045} fill={mix(colour, 60, '#000')} opacity={0.55} />
          <circle cy={-0.25} r={0.17} fill={`url(#${ids}-tok-${seat})`} />
          <ellipse cx={-0.055} cy={-0.31} rx={0.06} ry={0.038} fill="#fff" opacity={0.75} />
          <ellipse cx={-0.13} cy={0.12} rx={0.04} ry={0.08} fill="#fff" opacity={0.28} transform="rotate(25 -0.13 0.12)" />
        </g>
        <circle r={0.5} fill="transparent" />
      </g>
    </g>
  )
}
