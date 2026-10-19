'use client'

import { AnimatePresence, motion } from 'motion/react'
import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from 'react'
import { CircleAlert as AlertCircle, CircleCheck as CheckCircle2, Info } from 'lucide-react'
import { friendlyError } from '@/lib/format'
import { cn } from '@/lib/utils'

type Tone = 'info' | 'success' | 'error'
interface ToastItem {
  id: number
  text: string
  tone: Tone
}

interface ToastApi {
  show: (text: string, tone?: Tone) => void
  error: (err: unknown) => void
}

const ToastContext = createContext<ToastApi | null>(null)

const ICONS = { info: Info, success: CheckCircle2, error: AlertCircle }

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([])
  const seq = useRef(0)

  const show = useCallback((text: string, tone: Tone = 'info') => {
    const id = ++seq.current
    setItems((list) => [...list.filter((t) => t.text !== text), { id, text, tone }].slice(-3))
    setTimeout(() => setItems((list) => list.filter((t) => t.id !== id)), 3200)
  }, [])

  const api = useMemo<ToastApi>(() => ({ show, error: (err) => show(friendlyError(err), 'error') }), [show])

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div
        className="pointer-events-none fixed inset-x-0 top-0 z-[60] flex flex-col items-center gap-2 p-4 pt-[max(1rem,env(safe-area-inset-top))]"
        role="status"
        aria-live="polite"
      >
        <AnimatePresence initial={false}>
          {items.map((t) => {
            const Icon = ICONS[t.tone]
            return (
              <motion.div
                key={t.id}
                layout
                initial={{ opacity: 0, y: -16, scale: 0.95 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: -10, scale: 0.97 }}
                transition={{ type: 'spring', stiffness: 460, damping: 32 }}
                className="pointer-events-auto flex max-w-sm items-center gap-2.5 rounded-full bg-ink py-2.5 pr-5 pl-3.5 text-sm font-medium text-ink-foreground shadow-[0_12px_40px_-12px_rgb(0_0_0/0.5)] ring-1 ring-ink-foreground/10"
              >
                <Icon
                  className={cn(
                    'size-4.5 shrink-0',
                    t.tone === 'error' && 'text-primary',
                    t.tone === 'success' && 'text-online',
                    t.tone === 'info' && 'text-ink-foreground/70',
                  )}
                  aria-hidden
                />
                {t.text}
              </motion.div>
            )
          })}
        </AnimatePresence>
      </div>
    </ToastContext.Provider>
  )
}

export function useToast() {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be used inside <ToastProvider>')
  return ctx
}
