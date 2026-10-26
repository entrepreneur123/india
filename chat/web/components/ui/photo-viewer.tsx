'use client'

import { useEffect, useRef, useState } from 'react'
import { ChevronLeft, ChevronRight, Download, X, ZoomIn, ZoomOut } from 'lucide-react'
import { useI18n } from '@/components/i18n'
import { Modal } from '@/components/ui/modal'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'

const MAX = 5
const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v))
/** React events bubble out of a portal to where it was rendered (a post's double tap, a message's long press): not these. */
const stop = (e: { stopPropagation: () => void }) => e.stopPropagation()

/**
 * 39: a photo on the whole screen, wherever one is tapped (chats, rooms, posts, profiles, highlights): pinch, the
 * wheel, a double tap or the buttons zoom (up to 5×) and a drag moves it once zoomed; a swipe or the arrows go to
 * the next one; Save downloads it. `index` null: closed.
 */
export function PhotoViewer({ photos, index, onIndex, label }: { photos: string[]; index: number | null; onIndex: (i: number | null) => void; label?: string }) {
  const { t } = useI18n()
  const toast = useToast()
  const i = clamp(index ?? 0, 0, Math.max(0, photos.length - 1))
  const url = photos[i]
  const [z, setZ] = useState({ s: 1, x: 0, y: 0 })
  const [moving, setMoving] = useState(false)
  const box = useRef<HTMLDivElement>(null)
  const pic = useRef<HTMLImageElement>(null)
  const pointers = useRef(new Map<number, { x: number; y: number }>())
  const gesture = useRef<{ dist: number; s: number; x0: number; y0: number; zx: number; zy: number } | null>(null)
  const lastTap = useRef(0)
  useEffect(() => setZ({ s: 1, x: 0, y: 0 }), [url])

  /** Keeps a zoomed photo's edges from being dragged in past the screen's (a side smaller than the screen stays centred). */
  const fit = (s: number, x: number, y: number) => {
    const r = box.current?.getBoundingClientRect()
    const img = pic.current
    const mx = r && img ? Math.max(0, (img.offsetWidth * s - r.width) / 2) : 0
    const my = r && img ? Math.max(0, (img.offsetHeight * s - r.height) / 2) : 0
    return { s, x: clamp(x, -mx, mx), y: clamp(y, -my, my) }
  }
  /** Zooms to `s` keeping the point (cx, cy) — relative to the box's centre — where it is. */
  const zoomTo = (s: number, cx = 0, cy = 0) =>
    setZ((p) => {
      const n = clamp(s, 1, MAX)
      return fit(n, cx - ((cx - p.x) * n) / p.s, cy - ((cy - p.y) * n) / p.s)
    })
  const centre = (e: { clientX: number; clientY: number }) => {
    const r = box.current!.getBoundingClientRect()
    return [e.clientX - r.left - r.width / 2, e.clientY - r.top - r.height / 2] as const
  }
  const go = (by: number) => {
    const n = i + by
    if (n >= 0 && n < photos.length) onIndex(n)
  }

  useEffect(() => {
    if (index === null) return
    const key = (e: KeyboardEvent) => {
      if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') go((e.key === 'ArrowRight') !== (document.dir === 'rtl') ? 1 : -1)
      if (e.key === '+' || e.key === '=') zoomTo(z.s * 1.5)
      if (e.key === '-') zoomTo(z.s / 1.5)
    }
    document.addEventListener('keydown', key)
    return () => document.removeEventListener('keydown', key)
  })

  const save = async () => {
    if (!url) return
    const a = document.createElement('a')
    a.download = `yaari-${Date.now()}.jpg`
    try {
      // A link to another site downloads only as a file of our own: fetched first (data: and blob: links download as they are).
      const local = url.startsWith('data:') || url.startsWith('blob:') || new URL(url, location.href).origin === location.origin
      a.href = local ? url : URL.createObjectURL(await (await fetch(url)).blob())
      a.click()
      if (!local) setTimeout(() => URL.revokeObjectURL(a.href), 4000)
      toast.show(t('photo.saved'), 'success')
    } catch {
      toast.show(t('photo.saveFailed'), 'error')
    }
  }

  const tool = 'grid size-10 place-items-center rounded-full text-white transition-colors hover:bg-white/15 disabled:opacity-40'
  return (
    <span className="contents" onClick={stop} onPointerDown={stop} onPointerUp={stop} onPointerMove={stop} onContextMenu={stop} onTouchStart={stop} onTouchEnd={stop}>
    <Modal open={index !== null && !!url} onClose={() => onIndex(null)} variant="full" hideHeader>
      <div className="relative flex h-dvh flex-col select-none">
        <div className="absolute inset-x-0 top-0 z-10 flex items-center gap-1 bg-linear-to-b from-black/70 to-transparent px-3 pt-[max(0.75rem,env(safe-area-inset-top))] pb-6">
          <span className="min-w-0 flex-1 truncate px-2 text-sm font-semibold">
            {label}
            {photos.length > 1 ? <span className="ms-2 font-medium text-white/70 tabular-nums">{t('photo.count', { n: i + 1, total: photos.length })}</span> : null}
          </span>
          <button type="button" className={tool} onClick={() => zoomTo(z.s / 1.5)} disabled={z.s <= 1} aria-label={t('photo.zoomOut')} title={t('photo.zoomOut')}>
            <ZoomOut className="size-5" aria-hidden />
          </button>
          <button type="button" className={tool} onClick={() => zoomTo(z.s * 1.5)} disabled={z.s >= MAX} aria-label={t('photo.zoomIn')} title={t('photo.zoomIn')}>
            <ZoomIn className="size-5" aria-hidden />
          </button>
          <button type="button" className={tool} onClick={() => void save()} aria-label={t('photo.save')} title={t('photo.save')}>
            <Download className="size-5" aria-hidden />
          </button>
          <button type="button" className={tool} onClick={() => onIndex(null)} aria-label={t('common.close')} title={t('common.close')} data-autofocus>
            <X className="size-6" aria-hidden />
          </button>
        </div>

        <div
          ref={box}
          className="relative flex min-h-0 flex-1 touch-none items-center justify-center overflow-hidden"
          onWheel={(e) => {
            const [cx, cy] = centre(e)
            zoomTo(z.s * Math.exp(-e.deltaY * 0.0015), cx, cy)
          }}
          onPointerDown={(e) => {
            e.currentTarget.setPointerCapture(e.pointerId)
            pointers.current.set(e.pointerId, { x: e.clientX, y: e.clientY })
            const [a, b] = [...pointers.current.values()]
            gesture.current = { dist: b ? Math.hypot(a.x - b.x, a.y - b.y) : 0, s: z.s, x0: e.clientX, y0: e.clientY, zx: z.x, zy: z.y }
            setMoving(true)
          }}
          onPointerMove={(e) => {
            const g = gesture.current
            if (!g || !pointers.current.has(e.pointerId)) return
            pointers.current.set(e.pointerId, { x: e.clientX, y: e.clientY })
            const [a, b] = [...pointers.current.values()]
            if (b && g.dist) {
              const [cx, cy] = centre({ clientX: (a.x + b.x) / 2, clientY: (a.y + b.y) / 2 })
              zoomTo(g.s * (Math.hypot(a.x - b.x, a.y - b.y) / g.dist), cx, cy)
            } else if (z.s > 1) {
              setZ((p) => fit(p.s, g.zx + e.clientX - g.x0, g.zy + e.clientY - g.y0))
            }
          }}
          onPointerUp={(e) => {
            const g = gesture.current
            const pinched = pointers.current.size > 1
            pointers.current.delete(e.pointerId)
            if (pointers.current.size) return
            gesture.current = null
            setMoving(false)
            if (!g || pinched) return
            const dx = e.clientX - g.x0
            const dy = e.clientY - g.y0
            if (z.s === 1 && Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy)) return go((dx < 0) !== (document.dir === 'rtl') ? 1 : -1)
            if (Math.hypot(dx, dy) > 10) return
            // A double tap zooms in where it lands, or back out.
            const now = performance.now()
            if (now - lastTap.current < 300) {
              lastTap.current = 0
              const [cx, cy] = centre(e)
              return z.s > 1 ? setZ({ s: 1, x: 0, y: 0 }) : zoomTo(2.5, cx, cy)
            }
            lastTap.current = now
          }}
          onPointerCancel={() => {
            pointers.current.clear()
            gesture.current = null
            setMoving(false)
          }}
        >
          <img
            key={url}
            ref={pic}
            src={url}
            alt={label ?? ''}
            draggable={false}
            className={cn('max-h-full max-w-full object-contain', !moving && 'transition-transform duration-200 ease-out')}
            style={{ transform: `translate3d(${z.x}px, ${z.y}px, 0) scale(${z.s})` }}
          />
        </div>

        {photos.length > 1 ? (
          <>
            <button type="button" className={cn(tool, 'absolute top-1/2 start-3 -translate-y-1/2 bg-black/40 max-sm:hidden')} onClick={() => go(-1)} disabled={i === 0} aria-label={t('common.back')}>
              <ChevronLeft className="size-6 rtl:rotate-180" aria-hidden />
            </button>
            <button type="button" className={cn(tool, 'absolute top-1/2 end-3 -translate-y-1/2 bg-black/40 max-sm:hidden')} onClick={() => go(1)} disabled={i === photos.length - 1} aria-label={t('photo.next')}>
              <ChevronRight className="size-6 rtl:rotate-180" aria-hidden />
            </button>
            <div className="absolute inset-x-0 bottom-[max(1rem,env(safe-area-inset-bottom))] flex justify-center gap-1.5" aria-hidden>
              {photos.map((p, k) => (
                <span key={`${p}-${k}`} className={cn('size-1.5 rounded-full bg-white/40', k === i && 'bg-white')} />
              ))}
            </div>
          </>
        ) : null}
      </div>
    </Modal>
    </span>
  )
}
