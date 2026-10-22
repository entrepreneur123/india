'use client'

import * as m from 'motion/react-m'
import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import { cn } from '@/lib/utils'

export interface MenuAction {
  icon: ReactNode
  label: string
  onClick: () => void
}

/** How long a press opens a line's menu (touch). */
export const PRESS_MS = 480

/**
 * A line's actions (long press on touch, right click with a mouse): a small list beside the bubble, above it when
 * the bubble is in the lower half of the screen. Escape, a tap elsewhere or choosing an action closes it.
 */
export function MessageMenu({
  actions,
  label,
  side,
  onClose,
}: {
  actions: MenuAction[]
  label: string
  /** The bubble's side: the menu lines up with it. */
  side: 'start' | 'end'
  onClose: () => void
}) {
  const ref = useRef<HTMLDivElement>(null)
  const [above, setAbove] = useState(false)
  const close = useRef(onClose)
  close.current = onClose

  useLayoutEffect(() => {
    const host = ref.current?.parentElement
    if (host) setAbove(host.getBoundingClientRect().top > window.innerHeight / 2)
    ref.current?.querySelector<HTMLButtonElement>('[role="menuitem"]')?.focus({ preventScroll: true })
  }, [])

  useEffect(() => {
    const away = (e: PointerEvent) => !ref.current?.contains(e.target as Node) && close.current()
    const key = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.stopPropagation()
        close.current()
        return
      }
      if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return
      const items = [...(ref.current?.querySelectorAll<HTMLButtonElement>('[role="menuitem"]') ?? [])]
      const at = items.indexOf(document.activeElement as HTMLButtonElement)
      items[(at + (e.key === 'ArrowDown' ? 1 : items.length - 1)) % items.length]?.focus()
      e.preventDefault()
    }
    // The press that opened the menu must not close it again.
    const id = setTimeout(() => document.addEventListener('pointerdown', away), 0)
    const scrolled = () => close.current()
    document.addEventListener('keydown', key, true)
    window.addEventListener('scroll', scrolled, { capture: true, once: true })
    return () => {
      clearTimeout(id)
      document.removeEventListener('pointerdown', away)
      document.removeEventListener('keydown', key, true)
      window.removeEventListener('scroll', scrolled, { capture: true })
    }
  }, [])

  return (
    <m.div
      ref={ref}
      role="menu"
      aria-label={label}
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ type: 'tween', duration: 0.12, ease: [0.2, 0, 0, 1] }}
      className={cn(
        'absolute z-30 min-w-44 overflow-hidden rounded-xl bg-card py-1 text-card-foreground shadow-e3 ring-1 ring-border',
        above ? 'bottom-full mb-1' : 'top-full mt-1',
        side === 'end' ? 'inset-e-0' : 'inset-s-0',
      )}
    >
      {actions.map((a) => (
        <button
          key={a.label}
          type="button"
          role="menuitem"
          onClick={() => {
            onClose()
            a.onClick()
          }}
          className="flex w-full items-center gap-3 px-3.5 py-2.5 text-start text-sm font-medium transition-colors duration-150 hover:bg-muted focus-visible:bg-muted focus-visible:outline-none"
        >
          <span className="grid size-5 shrink-0 place-items-center text-muted-foreground">{a.icon}</span>
          {a.label}
        </button>
      ))}
    </m.div>
  )
}

/**
 * Long press (touch and pen) and right click (mouse) open a line's menu. The tap that ends a long press is not also
 * a tap on what's under it (a photo, a link). Right click on a link or with text selected keeps the browser's menu.
 */
export function useMenuGesture(open: (() => void) | null) {
  const timer = useRef<{ x: number; y: number; id: ReturnType<typeof setTimeout> } | null>(null)
  const fired = useRef(false)
  const cancel = () => {
    if (timer.current) clearTimeout(timer.current.id)
    timer.current = null
  }
  useEffect(() => cancel, [])
  if (!open) return {}
  return {
    onPointerDown: (e: React.PointerEvent) => {
      if (e.pointerType === 'mouse') return
      fired.current = false
      cancel()
      const id = setTimeout(() => {
        fired.current = true
        timer.current = null
        navigator.vibrate?.(12)
        open()
      }, PRESS_MS)
      timer.current = { x: e.clientX, y: e.clientY, id }
    },
    onPointerMove: (e: React.PointerEvent) => {
      const s = timer.current
      if (s && (Math.abs(e.clientX - s.x) > 6 || Math.abs(e.clientY - s.y) > 6)) cancel()
    },
    onPointerUp: cancel,
    onPointerCancel: cancel,
    onClickCapture: (e: React.MouseEvent) => {
      if (!fired.current) return
      fired.current = false
      e.preventDefault()
      e.stopPropagation()
    },
    onContextMenu: (e: React.MouseEvent) => {
      // Touch browsers fire this on a long press too: the timer above handles that one.
      if (fired.current || timer.current) return e.preventDefault()
      if ((e.target as HTMLElement).closest('a') || window.getSelection()?.toString()) return
      e.preventDefault()
      open()
    },
  }
}
