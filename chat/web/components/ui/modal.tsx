'use client'

import { AnimatePresence, motion } from 'motion/react'
import { createContext, useContext, useEffect, useId, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { X } from 'lucide-react'
import { cn } from '@/lib/utils'

interface ModalProps {
  open: boolean
  onClose: () => void
  title?: ReactNode
  description?: ReactNode
  children: ReactNode
  variant?: 'dialog' | 'sheet'
  className?: string
  hideHeader?: boolean
  dismissible?: boolean
  night?: boolean
}

export const NightContext = createContext(false)

/** Open modals, newest last: Esc and the focus trap belong to the top one only (a post opened from a profile). */
const stack: symbol[] = []

const FOCUSABLE = 'button:not([disabled]), [href], input:not([disabled]), select, textarea, [tabindex]:not([tabindex="-1"])'

export function Modal({
  open,
  onClose,
  title,
  description,
  children,
  variant = 'dialog',
  className,
  hideHeader,
  dismissible = true,
  night,
}: ModalProps) {
  const inheritedNight = useContext(NightContext)
  const isNight = night ?? inheritedNight
  const titleId = useId()
  const descId = useId()
  const panelRef = useRef<HTMLDivElement>(null)
  const onCloseRef = useRef(onClose)
  onCloseRef.current = onClose

  useEffect(() => {
    if (!open) return
    const me = Symbol('modal')
    stack.push(me)
    const previouslyFocused = document.activeElement as HTMLElement | null
    const raf = requestAnimationFrame(() => {
      const panel = panelRef.current
      const target = panel?.querySelector<HTMLElement>('[data-autofocus]') ?? panel?.querySelector<HTMLElement>(FOCUSABLE)
      ;(target ?? panel)?.focus()
    })
    const onKey = (e: KeyboardEvent) => {
      if (stack[stack.length - 1] !== me) return
      if (e.key === 'Escape' && dismissible) {
        e.stopPropagation()
        onCloseRef.current()
      }
      if (e.key === 'Tab' && panelRef.current) {
        const nodes = [...panelRef.current.querySelectorAll<HTMLElement>(FOCUSABLE)]
        if (nodes.length === 0) return
        const first = nodes[0]
        const last = nodes[nodes.length - 1]
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault()
          last.focus()
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault()
          first.focus()
        }
      }
    }
    document.addEventListener('keydown', onKey)
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      stack.splice(stack.indexOf(me), 1)
      cancelAnimationFrame(raf)
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = overflow
      previouslyFocused?.focus?.()
    }
  }, [open, dismissible])

  if (typeof document === 'undefined') return null

  const isSheet = variant === 'sheet'

  return createPortal(
    <AnimatePresence>
      {open ? (
        <div
          className={cn(
            'fixed inset-0 z-50 flex justify-center',
            isSheet ? 'items-end md:items-center' : 'items-center p-4',
            isNight && 'theme-night',
          )}
        >
          <motion.div
            className="absolute inset-0 bg-overlay backdrop-blur-sm"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
            onClick={dismissible ? onClose : undefined}
            aria-hidden
          />
          <motion.div
            ref={panelRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby={title ? titleId : undefined}
            aria-describedby={description ? descId : undefined}
            tabIndex={-1}
            initial={isSheet ? { y: '100%', opacity: 0.6 } : { opacity: 0, scale: 0.94, y: 12 }}
            animate={isSheet ? { y: 0, opacity: 1 } : { opacity: 1, scale: 1, y: 0 }}
            exit={isSheet ? { y: '100%', opacity: 0.6 } : { opacity: 0, scale: 0.96, y: 8 }}
            transition={{ type: 'spring', stiffness: 380, damping: 34 }}
            className={cn(
              'relative z-10 flex max-h-[92dvh] w-full flex-col overflow-hidden bg-card text-card-foreground shadow-2xl outline-none',
              isSheet
                ? 'rounded-t-[28px] pb-[env(safe-area-inset-bottom)] md:max-w-lg md:rounded-[28px]'
                : 'max-w-md rounded-[28px]',
              className,
            )}
          >
            {isSheet ? (
              <div className="mx-auto mt-2.5 h-1 w-10 shrink-0 rounded-full bg-input md:hidden" aria-hidden />
            ) : null}
            {hideHeader ? null : (
              <div className="flex items-start justify-between gap-4 px-6 pt-5">
                <div className="flex min-w-0 flex-col gap-1">
                  {title ? (
                    <h2 id={titleId} className="text-lg font-bold tracking-tight text-balance">
                      {title}
                    </h2>
                  ) : null}
                  {description ? (
                    <p id={descId} className="text-sm leading-relaxed text-muted-foreground text-pretty">
                      {description}
                    </p>
                  ) : null}
                </div>
                {dismissible ? (
                  <button
                    type="button"
                    onClick={onClose}
                    className="-mr-2 grid size-9 shrink-0 place-items-center rounded-full text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
                    aria-label="Close"
                  >
                    <X className="size-5" aria-hidden />
                  </button>
                ) : null}
              </div>
            )}
            <div className="min-h-0 flex-1 overflow-y-auto scrollbar-thin">{children}</div>
          </motion.div>
        </div>
      ) : null}
    </AnimatePresence>,
    document.body,
  )
}
