'use client'

import { AnimatePresence } from 'motion/react'
import * as m from 'motion/react-m'
import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from 'react'
import { CircleAlert as AlertCircle, CircleCheck as CheckCircle2, Info } from 'lucide-react'
import { useI18n } from '@/components/i18n'
import { cn } from '@/lib/utils'
import { gate } from '@/lib/gate'
import { OfflineBanner } from './offline-banner'

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
  const { errorText } = useI18n()

  const show = useCallback((text: string, tone: Tone = 'info') => {
    const id = ++seq.current
    setItems((list) => [...list.filter((t) => t.text !== text), { id, text, tone }].slice(-3))
    setTimeout(() => setItems((list) => list.filter((t) => t.id !== id)), 3200)
  }, [])

  const api = useMemo<ToastApi>(() => ({ show, error: (err) => void (gate(err) || show(errorText(err), 'error')) }), [show, errorText])

  return (
    <ToastContext.Provider value={api}>
      {children}
      <OfflineBanner />
      <div
        className="pointer-events-none fixed inset-x-0 top-0 z-[60] flex flex-col items-center gap-2 p-4 pt-[max(1rem,env(safe-area-inset-top))]"
        role="status"
        aria-live="polite"
      >
        <AnimatePresence initial={false}>
          {items.map((t) => {
            const Icon = ICONS[t.tone]
            return (
              <m.div
                key={t.id}
                initial={{ opacity: 0, y: -8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0 }}
                transition={{ type: 'tween', duration: 0.18, ease: [0.2, 0, 0, 1] }}
                className="pointer-events-auto flex max-w-sm items-center gap-2.5 rounded-xl bg-[#262626] py-3 pe-5 ps-4 text-sm font-medium text-white shadow-e3"
              >
                <Icon
                  className={cn(
                    'size-4.5 shrink-0',
                    t.tone === 'error' && 'text-[#f28b82]',
                    t.tone === 'success' && 'text-[#81c995]',
                    t.tone === 'info' && 'text-white/70',
                  )}
                  aria-hidden
                />
                {t.text}
              </m.div>
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
