'use client'

import { motion } from 'motion/react'
import { useEffect, useState } from 'react'
import { X } from 'lucide-react'
import { cn } from '@/lib/utils'

const R = 20
const C = 2 * Math.PI * R

export function ExitButton({
  startedAt,
  lockMs,
  onExit,
  onLocked,
  busy,
}: {
  startedAt: number
  lockMs: number
  onExit: () => void
  onLocked: () => void
  busy?: boolean
}) {
  const remaining = () => Math.max(0, lockMs - (Date.now() - startedAt))
  const [left, setLeft] = useState(remaining)
  const locked = left > 0

  useEffect(() => {
    if (!locked) return
    let raf = 0
    const tick = () => {
      const r = remaining()
      setLeft(r)
      if (r > 0) raf = requestAnimationFrame(tick)
    }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [startedAt, lockMs])

  const progress = lockMs > 0 ? 1 - left / lockMs : 1

  return (
    <motion.button
      type="button"
      onClick={locked ? onLocked : onExit}
      disabled={busy}
      whileTap={{ scale: 0.9 }}
      aria-label={locked ? `Leave chat (available in ${Math.ceil(left / 1000)} seconds)` : 'Leave chat'}
      className={cn(
        'relative grid size-11 shrink-0 place-items-center rounded-full transition-colors',
        locked ? 'text-muted-foreground' : 'bg-muted text-foreground hover:bg-destructive hover:text-primary-foreground',
      )}
    >
      <svg viewBox="0 0 44 44" className="absolute inset-0 -rotate-90" aria-hidden>
        <circle cx="22" cy="22" r={R} fill="none" className="stroke-border" strokeWidth="2.5" />
        {locked ? (
          <circle
            cx="22"
            cy="22"
            r={R}
            fill="none"
            stroke="url(#exit-ring)"
            strokeWidth="2.5"
            strokeLinecap="round"
            strokeDasharray={C}
            strokeDashoffset={C * (1 - progress)}
          />
        ) : null}
        <defs>
          <linearGradient id="exit-ring" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#FF3D68" />
            <stop offset="100%" stopColor="#FF7A45" />
          </linearGradient>
        </defs>
      </svg>
      {locked && left > 400 ? (
        <span className="relative text-xs font-bold tabular-nums">{Math.ceil(left / 1000)}</span>
      ) : (
        <X className="relative size-5" aria-hidden />
      )}
    </motion.button>
  )
}
