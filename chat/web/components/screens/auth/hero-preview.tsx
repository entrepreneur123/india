'use client'

import { AnimatePresence, motion, useReducedMotion } from 'motion/react'
import { useEffect, useState } from 'react'
import { SHOWCASE_FLAGS } from '@/lib/countries'
import { flagEmoji } from '@/lib/format'
import { cn } from '@/lib/utils'

const SCRIPT: { from: 'a' | 'b'; text: string }[] = [
  { from: 'a', text: 'hiii 👋 where are you from?' },
  { from: 'b', text: 'Lisbon! and you?' },
  { from: 'a', text: 'Mumbai. it’s 2am here lol' },
  { from: 'b', text: 'night owl club 🦉' },
  { from: 'a', text: 'add me? let’s keep talking' },
]

export function HeroPreview() {
  const reduce = useReducedMotion()
  const [count, setCount] = useState(reduce ? SCRIPT.length : 1)

  useEffect(() => {
    if (reduce) return
    const id = setInterval(() => setCount((c) => (c >= SCRIPT.length + 2 ? 1 : c + 1)), 1700)
    return () => clearInterval(id)
  }, [reduce])

  const visible = SCRIPT.slice(0, Math.min(count, SCRIPT.length))
  const typing = !reduce && count < SCRIPT.length

  return (
    <div className="relative mx-auto aspect-square w-full max-w-[440px]" aria-hidden>
      <div className="absolute inset-0 animate-spin-slow [animation-duration:60s]">
        {SHOWCASE_FLAGS.slice(0, 10).map((iso, i, arr) => {
          const angle = (i / arr.length) * Math.PI * 2
          const x = 50 + Math.cos(angle) * 47
          const y = 50 + Math.sin(angle) * 47
          return (
            <span
              key={iso}
              className="absolute grid size-11 -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full bg-ink-foreground/8 text-xl ring-1 ring-ink-foreground/10 backdrop-blur-md"
              style={{ left: `${x}%`, top: `${y}%` }}
            >
              <span className="animate-spin-slow [animation-direction:reverse] [animation-duration:60s]">
                {flagEmoji(iso)}
              </span>
            </span>
          )
        })}
      </div>
      <div className="absolute inset-[18%] rounded-full ring-1 ring-ink-foreground/8" />
      <div className="absolute inset-[30%] rounded-full ring-1 ring-ink-foreground/6" />

      <div className="absolute inset-x-[17%] top-1/2 flex -translate-y-1/2 flex-col gap-2 rounded-[28px] bg-ink/70 p-4 shadow-[0_30px_80px_-30px_rgb(255_61_104/0.55)] ring-1 ring-ink-foreground/10 backdrop-blur-xl">
        <div className="flex items-center gap-2.5 border-b border-ink-foreground/8 pb-3">
          <img
            src="https://randomuser.me/api/portraits/women/68.jpg"
            alt=""
            className="size-8 rounded-full object-cover"
          />
          <div className="min-w-0 flex-1">
            <p className="text-sm font-semibold text-ink-foreground">Sofia, 24 {flagEmoji('PT')}</p>
            <p className="flex items-center gap-1.5 text-[11px] text-ink-foreground/60">
              <span className="size-1.5 rounded-full bg-online" /> Matched just now
            </p>
          </div>
        </div>
        <div className="flex min-h-48 flex-col justify-end gap-1.5">
          <AnimatePresence initial={false}>
            {visible.map((m, i) => (
              <motion.div
                key={`${i}-${m.text}`}
                layout
                initial={{ opacity: 0, y: 12, scale: 0.85 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, scale: 0.9 }}
                transition={{ type: 'spring', stiffness: 420, damping: 26 }}
                className={cn(
                  'max-w-[82%] rounded-2xl px-3 py-2 text-[13px] leading-snug',
                  m.from === 'a'
                    ? 'self-end rounded-br-md bg-brand text-primary-foreground'
                    : 'self-start rounded-bl-md bg-ink-foreground/10 text-ink-foreground',
                )}
              >
                {m.text}
              </motion.div>
            ))}
          </AnimatePresence>
          {typing ? (
            <div className="flex gap-1 self-start rounded-2xl rounded-bl-md bg-ink-foreground/10 px-3 py-2.5">
              {[0, 1, 2].map((d) => (
                <motion.span
                  key={d}
                  className="size-1.5 rounded-full bg-ink-foreground/70"
                  animate={{ y: [0, -4, 0] }}
                  transition={{ duration: 0.9, repeat: Infinity, delay: d * 0.15 }}
                />
              ))}
            </div>
          ) : null}
        </div>
      </div>
    </div>
  )
}
