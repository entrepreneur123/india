'use client'

import { useCallback, useEffect, useLayoutEffect, useRef, useState, type KeyboardEvent, type PointerEvent, type ReactNode, type RefObject } from 'react'
import {
  clampShare,
  clampWidth,
  nearestSnap,
  PHONE_DEFAULT,
  PHONE_MAX,
  readShare,
  readWidth,
  splitKey,
  stepSnap,
  WIDE_MAX_SHARE,
  WIDE_MIN,
  WIDE_STEP,
  wideDefault,
} from '@/lib/room-split'
import { useLooksT } from '@/components/looks/looks-text'
import { cn } from '@/lib/utils'

// The room and its chat share the screen (lib/room-split.ts has the rules): phones drag a handle between the two,
// wide screens a splitter between the room's column and the chat's. The choice is kept on this device.

function stored(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null // storage blocked: the room's default
  }
}

function store(key: string, value: number) {
  try {
    localStorage.setItem(key, String(value))
  } catch {
    /* storage blocked: the choice lasts while the room is open */
  }
}

/** Two taps on the handle within this long switch between the chat only and the room's default. */
const DOUBLE_TAP_MS = 350
/** A pointer that moves less than this is a tap, not a drag. */
const DRAG_PX = 4

/**
 * Phones: the room on top (its share of the height), the handle, then the chat. Dragging the handle down gives the
 * room more, up gives the chat more; it settles on chat only, 30 %, 50 % or 70 %. Chat only folds the room to
 * `strip` (given the way back). A double tap switches between chat only and the room's default.
 */
export function PhoneSplit({ top, strip, chat }: { top: ReactNode; strip: (expand: () => void) => ReactNode; chat: ReactNode }) {
  const { lt } = useLooksT()
  const key = splitKey('phone')
  const [share, setShare] = useState(() => readShare(stored(key)) ?? PHONE_DEFAULT)
  const [drag, setDrag] = useState<number | null>(null)
  const box = useRef<HTMLDivElement>(null)
  const press = useRef<{ y: number; moved: boolean } | null>(null)
  const lastTap = useRef(0)

  const commit = useCallback(
    (next: number) => {
      setShare(next)
      store(key, next)
    },
    [key],
  )
  const toggle = () => commit(share === 0 ? PHONE_DEFAULT : 0)
  const shown = drag ?? share

  const down = (e: PointerEvent<HTMLDivElement>) => {
    e.currentTarget.setPointerCapture(e.pointerId)
    press.current = { y: e.clientY, moved: false }
  }
  const move = (e: PointerEvent<HTMLDivElement>) => {
    const p = press.current
    const r = box.current?.getBoundingClientRect()
    if (!p || !r?.height) return
    if (!p.moved && Math.abs(e.clientY - p.y) < DRAG_PX) return
    p.moved = true
    setDrag(clampShare((e.clientY - r.top) / r.height))
  }
  const up = (e: PointerEvent<HTMLDivElement>) => {
    const p = press.current
    press.current = null
    if (!p) return
    if (p.moved) {
      commit(nearestSnap(drag ?? share))
      setDrag(null)
    } else if (e.timeStamp - lastTap.current < DOUBLE_TAP_MS) {
      lastTap.current = 0
      toggle()
    } else {
      lastTap.current = e.timeStamp
    }
  }
  const key_ = (e: KeyboardEvent<HTMLDivElement>) => {
    const next =
      e.key === 'ArrowDown' ? stepSnap(share, 1)
      : e.key === 'ArrowUp' ? stepSnap(share, -1)
      : e.key === 'Home' ? 0
      : e.key === 'End' ? PHONE_MAX
      : e.key === 'Enter' ? (share === 0 ? PHONE_DEFAULT : 0)
      : null
    if (next === null) return
    e.preventDefault()
    commit(next)
  }

  return (
    <div ref={box} className="flex min-h-0 flex-1 flex-col">
      {shown === 0 && drag === null ? (
        strip(() => commit(PHONE_DEFAULT))
      ) : (
        // Only as tall as what's in it (up to the share), so a big share leaves no empty band; exact while dragged.
        <div className="flex shrink-0 flex-col gap-2 overflow-y-auto pb-1 no-scrollbar" style={drag === null ? { maxHeight: `${shown * 100}%` } : { height: `${shown * 100}%` }}>
          {top}
        </div>
      )}
      <div
        role="separator"
        aria-orientation="horizontal"
        aria-label={lt('layout.resize')}
        aria-valuemin={0}
        aria-valuemax={Math.round(PHONE_MAX * 100)}
        aria-valuenow={Math.round(shown * 100)}
        aria-valuetext={`${Math.round(shown * 100)}%`}
        title={lt('layout.resizeHint')}
        tabIndex={0}
        onPointerDown={down}
        onPointerMove={move}
        onPointerUp={up}
        onPointerCancel={() => {
          press.current = null
          setDrag(null)
        }}
        onKeyDown={key_}
        className="group flex h-6 shrink-0 cursor-row-resize touch-none items-center justify-center border-t border-border outline-none"
      >
        <span
          aria-hidden
          className={cn(
            'h-1 w-9 rounded-full bg-input transition-colors duration-150 group-hover:bg-muted-foreground group-focus-visible:bg-primary-text',
            drag !== null && 'bg-primary-text',
          )}
        />
      </div>
      {chat}
    </div>
  )
}

/** Wide screens: the chat column's width, measured against the room's width in `box`. */
export function useWideSplit(box: RefObject<HTMLElement | null>, on: boolean) {
  const key = splitKey('wide')
  const [total, setTotal] = useState(0)
  const [chosen, setChosen] = useState<number | null>(() => readWidth(stored(key)))

  useLayoutEffect(() => {
    const el = box.current
    if (!on || !el) return
    setTotal(el.getBoundingClientRect().width)
    const watch = new ResizeObserver(([e]) => setTotal(e.contentRect.width))
    watch.observe(el)
    return () => watch.disconnect()
  }, [box, on])

  const width = total ? clampWidth(chosen ?? wideDefault(total), total) : null
  const set = useCallback(
    (px: number, save: boolean) => {
      const next = clampWidth(px, total)
      setChosen(next)
      if (save) store(key, next)
    },
    [key, total],
  )
  return { width, total, set }
}

/**
 * The line between the room's column and the chat's: drag it (mouse or touch), or focus it and use ← / →. The chat
 * column is at the end side, so in right-to-left languages the directions mirror.
 */
export function WideSplitter({ split, box, rtl }: { split: ReturnType<typeof useWideSplit>; box: RefObject<HTMLElement | null>; rtl: boolean }) {
  const { lt } = useLooksT()
  const [dragging, setDragging] = useState(false)
  const latest = useRef<number | null>(null)

  // A drag that ends outside the window still ends.
  useEffect(() => {
    if (!dragging) return
    const stop = () => setDragging(false)
    window.addEventListener('blur', stop)
    return () => window.removeEventListener('blur', stop)
  }, [dragging])

  if (split.width === null) return null
  const widthAt = (x: number) => {
    const r = box.current?.getBoundingClientRect()
    return r ? (rtl ? x - r.left : r.right - x) : split.width!
  }
  return (
    <div
      role="separator"
      aria-orientation="vertical"
      aria-label={lt('layout.resize')}
      aria-valuemin={WIDE_MIN}
      aria-valuemax={Math.round(Math.max(WIDE_MIN, split.total * WIDE_MAX_SHARE))}
      aria-valuenow={split.width}
      tabIndex={0}
      onPointerDown={(e) => {
        e.currentTarget.setPointerCapture(e.pointerId)
        setDragging(true)
      }}
      onPointerMove={(e) => {
        if (!dragging) return
        latest.current = widthAt(e.clientX)
        split.set(latest.current, false)
      }}
      onPointerUp={() => {
        if (dragging && latest.current !== null) split.set(latest.current, true)
        latest.current = null
        setDragging(false)
      }}
      onPointerCancel={() => setDragging(false)}
      onKeyDown={(e) => {
        if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return
        e.preventDefault()
        // The chat is at the end: moving the line toward the start makes it wider.
        const wider = (e.key === 'ArrowLeft') !== rtl
        split.set(split.width! + (wider ? WIDE_STEP : -WIDE_STEP), true)
      }}
      className="group relative z-10 -mx-1.5 w-3 shrink-0 cursor-col-resize touch-none outline-none"
    >
      <span
        aria-hidden
        className={cn(
          'absolute inset-y-0 start-1/2 w-px -translate-x-1/2 bg-border transition-colors duration-150 group-hover:w-0.5 group-hover:bg-muted-foreground group-focus-visible:w-0.5 group-focus-visible:bg-primary-text rtl:translate-x-1/2',
          dragging && 'w-0.5 bg-primary-text',
        )}
      />
    </div>
  )
}
