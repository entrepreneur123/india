'use client'

import * as m from 'motion/react-m'
import { useEffect, useState } from 'react'
import { X } from 'lucide-react'
import { useI18n } from '@/components/i18n'
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
  const { t } = useI18n()
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
    <m.button
      type="button"
      onClick={locked ? onLocked : onExit}
      disabled={busy}
      whileTap={{ scale: 0.94 }}
      transition={{ duration: 0.1 }}
      aria-label={locked ? t('exit.lockedIn', { s: Math.ceil(left / 1000) }) : t('exit.leave')}
      className={cn(
        'relative grid size-11 shrink-0 place-items-center rounded-full transition-colors duration-150',
        locked ? 'text-muted-foreground' : 'bg-muted text-foreground hover:bg-input/60',
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
            className="stroke-primary-text"
            strokeWidth="2.5"
            strokeLinecap="round"
            strokeDasharray={C}
            strokeDashoffset={C * (1 - progress)}
          />
        ) : null}
      </svg>
      {locked && left > 400 ? (
        <span className="relative text-xs font-bold tabular-nums">{Math.ceil(left / 1000)}</span>
      ) : (
        <X className="relative size-5" aria-hidden />
      )}
    </m.button>
  )
}
