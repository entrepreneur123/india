'use client'

import { AnimatePresence, useReducedMotion } from 'motion/react'
import * as m from 'motion/react-m'
import { useEffect, useState } from 'react'
import type { TKey } from '@/lib/i18n'
import { flagEmoji } from '@/lib/format'
import { useI18n } from '@/components/i18n'
import { cn } from '@/lib/utils'

const SCRIPT: { from: 'a' | 'b'; text: TKey }[] = [
  { from: 'a', text: 'hero.1' },
  { from: 'b', text: 'hero.2' },
  { from: 'a', text: 'hero.3' },
  { from: 'b', text: 'hero.4' },
  { from: 'a', text: 'hero.5' },
]

/** An example conversation that types itself out (decorative; hidden from screen readers). */
export function HeroPreview() {
  const { t } = useI18n()
  const reduce = useReducedMotion()
  const [count, setCount] = useState(reduce ? SCRIPT.length : 1)

  useEffect(() => {
    if (reduce) return
    const id = setInterval(() => {
      if (document.visibilityState === 'hidden') return
      setCount((c) => (c >= SCRIPT.length + 2 ? 1 : c + 1))
    }, 1700)
    return () => clearInterval(id)
  }, [reduce])

  const visible = SCRIPT.slice(0, Math.min(count, SCRIPT.length))
  const typing = !reduce && count < SCRIPT.length

  return (
    <div className="mx-auto w-full max-w-[360px] rounded-[28px] bg-card p-4 shadow-e2" aria-hidden>
      <div className="flex items-center gap-2.5 border-b border-border pb-3">
        <span className="grid size-8 place-items-center rounded-full bg-[#1a73e8] text-sm font-semibold text-white">S</span>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold">Sofia, 24 {flagEmoji('PT')}</p>
          <p className="flex items-center gap-1.5 text-[11px] text-muted-foreground">
            <span className="size-1.5 rounded-full bg-online" /> {t('hero.example')}
          </p>
        </div>
      </div>
      <div className="flex min-h-56 flex-col justify-end gap-1.5 pt-3">
        <AnimatePresence initial={false}>
          {visible.map((msg, i) => (
            <m.div
              key={`${i}-${msg.text}`}
              initial={{ opacity: 0, y: 6 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0 }}
              transition={{ type: 'tween', duration: 0.18, ease: [0.2, 0, 0, 1] }}
              className={cn(
                'max-w-[82%] rounded-2xl px-3 py-2 text-[13px] leading-snug',
                msg.from === 'a' ? 'self-end rounded-ee-md bg-primary text-primary-foreground' : 'self-start rounded-es-md bg-muted',
              )}
            >
              {t(msg.text)}
            </m.div>
          ))}
        </AnimatePresence>
        {typing ? (
          <div className="flex gap-1 self-start rounded-2xl rounded-es-md bg-muted px-3 py-2.5">
            {[0, 1, 2].map((d) => (
              <span key={d} className="size-1.5 animate-pulse rounded-full bg-muted-foreground" style={{ animationDelay: `${d * 0.2}s` }} />
            ))}
          </div>
        ) : null}
      </div>
    </div>
  )
}
