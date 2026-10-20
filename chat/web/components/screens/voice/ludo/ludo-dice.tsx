'use client'

import { useEffect, useRef, type CSSProperties } from 'react'
import { cn } from '@/lib/utils'

/** Pips on a 3 x 3 grid (0-8), per face. */
const PIPS: Record<number, number[]> = {
  1: [4],
  2: [2, 6],
  3: [2, 4, 6],
  4: [0, 2, 6, 8],
  5: [0, 2, 4, 6, 8],
  6: [0, 2, 3, 5, 6, 8],
}
/** Where each face sits on the cube, and the turn that brings it to the front. */
const FACES: Record<number, { place: string; show: [number, number] }> = {
  1: { place: 'rotateY(0deg)', show: [0, 0] },
  2: { place: 'rotateY(90deg)', show: [0, -90] },
  6: { place: 'rotateY(180deg)', show: [0, -180] },
  5: { place: 'rotateY(-90deg)', show: [0, 90] },
  3: { place: 'rotateX(90deg)', show: [-90, 0] },
  4: { place: 'rotateX(-90deg)', show: [90, 0] },
}
const pose = (n: number, spinX = 0, spinY = 0) => `rotateX(${FACES[n].show[0] + spinX}deg) rotateY(${FACES[n].show[1] + spinY}deg)`

/**
 * A die you can almost pick up: a small 3D cube, slightly turned so two more faces catch the light. It tumbles
 * while the server rolls, then lands on the server's number. Reduced motion: the number just changes.
 */
export function Dice({
  value,
  rolling,
  size,
  colour,
  enabled,
  label,
  reduce,
  onRoll,
}: {
  value: number | null
  rolling: boolean
  size: number
  colour: string
  enabled: boolean
  label: string
  reduce: boolean
  onRoll: () => void
}) {
  const cube = useRef<HTMLDivElement>(null)
  const spin = useRef<Animation | null>(null)
  const shown = value ?? 6

  useEffect(() => {
    const el = cube.current
    if (!el || reduce) return
    if (rolling) {
      spin.current = el.animate(
        [{ transform: `${pose(shown)}` }, { transform: `${pose(shown, 360, 360)}` }],
        { duration: 420, iterations: Infinity },
      )
      return
    }
    if (spin.current) {
      spin.current.cancel()
      spin.current = null
      // Land: one more tumble that slows onto the face.
      el.animate([{ transform: pose(shown, -270, 180) }, { transform: pose(shown, 12, -8) }, { transform: pose(shown) }], {
        duration: 420,
        easing: 'cubic-bezier(.2,.75,.25,1)',
      })
    }
  }, [rolling, shown, reduce])

  const half = size / 2
  return (
    <button
      type="button"
      onClick={onRoll}
      disabled={!enabled}
      aria-label={label}
      className={cn('relative grid shrink-0 place-items-center rounded-2xl transition-transform duration-150', enabled ? 'cursor-pointer active:scale-95' : 'cursor-default')}
      style={{ width: size + 14, height: size + 14 }}
    >
      {enabled ? (
        <span
          aria-hidden
          className="absolute inset-0 rounded-2xl motion-safe:animate-pulse"
          style={{ boxShadow: `0 0 0 2px ${colour}, 0 0 18px 2px color-mix(in srgb, ${colour} 55%, transparent)` }}
        />
      ) : null}
      <span aria-hidden className="absolute bottom-0.5 h-2 rounded-full bg-black/35 blur-[3px]" style={{ width: size * 0.8 }} />
      <span aria-hidden style={{ width: size, height: size, perspective: size * 4 }}>
        <span className="block size-full" style={{ transformStyle: 'preserve-3d', transform: 'rotateX(-16deg) rotateY(-20deg)' }}>
          <span
            ref={cube}
            className={cn('relative block size-full', reduce && rolling && 'opacity-60')}
            style={{ transformStyle: 'preserve-3d', transform: pose(shown) }}
          >
            {[1, 2, 3, 4, 5, 6].map((n) => (
              <span
                key={n}
                className="absolute inset-0 grid grid-cols-3 grid-rows-3 rounded-[22%] p-[13%]"
                style={
                  {
                    transform: `${FACES[n].place} translateZ(${half}px)`,
                    backfaceVisibility: 'hidden',
                    background: 'linear-gradient(145deg, #ffffff 0%, #f1eee8 60%, #dcd7cd 100%)',
                    boxShadow: 'inset 0 0 0 1px rgb(0 0 0 / 0.06), inset 0 -2px 4px rgb(0 0 0 / 0.08)',
                  } as CSSProperties
                }
              >
                {Array.from({ length: 9 }, (_, i) => (
                  <span key={i} className="grid place-items-center">
                    {PIPS[n].includes(i) ? (
                      <span
                        className="block rounded-full"
                        style={{
                          width: size * 0.17,
                          height: size * 0.17,
                          background: n === 1 ? colour : '#26232b',
                          boxShadow: 'inset 0 1px 1.5px rgb(0 0 0 / 0.45)',
                        }}
                      />
                    ) : null}
                  </span>
                ))}
              </span>
            ))}
          </span>
        </span>
      </span>
    </button>
  )
}

/**
 * The turn clock around the player's avatar: a ring that empties over the 15 s. `deadline` is on this device's
 * clock; the ring re-starts whenever it changes.
 */
export function TurnRing({ deadline, total, size, colour }: { deadline: number; total: number; size: number; colour: string }) {
  const ring = useRef<SVGCircleElement>(null)
  const r = size / 2 - 2
  const len = 2 * Math.PI * r
  useEffect(() => {
    const el = ring.current
    if (!el) return
    const left = Math.max(0, deadline - Date.now())
    const from = len * (1 - Math.min(1, left / total))
    const a = el.animate([{ strokeDashoffset: from }, { strokeDashoffset: len }], { duration: left, fill: 'forwards', easing: 'linear' })
    return () => a.cancel()
  }, [deadline, total, len])
  return (
    <svg width={size} height={size} className="pointer-events-none absolute inset-0 -rotate-90" aria-hidden>
      <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="color-mix(in srgb, currentColor 14%, transparent)" strokeWidth={3} />
      <circle ref={ring} cx={size / 2} cy={size / 2} r={r} fill="none" stroke={colour} strokeWidth={3} strokeLinecap="round" strokeDasharray={len} />
    </svg>
  )
}
