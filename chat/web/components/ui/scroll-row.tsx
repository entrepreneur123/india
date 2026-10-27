'use client'

import { useEffect, useRef, useState, type HTMLAttributes } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useI18n } from '@/components/i18n'
import { cn } from '@/lib/utils'

/**
 * A row that scrolls sideways (tabs, chips, faces, photos). A mouse has no sideways wheel, so a mouse or trackpad
 * gets a round arrow at each end that has more, moving the row by most of its width (YouTube's chip bar, Instagram's
 * stories and multi-photo posts); touch screens swipe and see no arrows. `className` styles the scrolling list itself,
 * `outer` the box the arrows sit in (a row that runs to the sheet's edges puts its negative margin there).
 */
export function ScrollRow({ as: Tag = 'div', outer, className, children, ...rest }: HTMLAttributes<HTMLElement> & { as?: 'div' | 'ul'; outer?: string }) {
  const { t, rtl } = useI18n()
  const ref = useRef<HTMLElement>(null)
  const [more, setMore] = useState({ start: false, end: false })

  useEffect(() => {
    const el = ref.current
    if (!el) return
    const update = () => {
      const x = Math.abs(el.scrollLeft) // negative in right-to-left layouts
      const max = el.scrollWidth - el.clientWidth
      setMore((m) => (m.start === x > 1 && m.end === x < max - 1 ? m : { start: x > 1, end: x < max - 1 }))
    }
    update()
    el.addEventListener('scroll', update, { passive: true })
    // The row's own size, and its items' (they load, or a photo arrives), change what is hidden.
    const ro = new ResizeObserver(update)
    ro.observe(el)
    for (const c of el.children) ro.observe(c)
    return () => {
      el.removeEventListener('scroll', update)
      ro.disconnect()
    }
  }, [children])

  const go = (dir: 1 | -1) => {
    const el = ref.current
    if (el) el.scrollBy({ left: dir * (rtl ? -1 : 1) * el.clientWidth * 0.8, behavior: 'smooth' })
  }
  // Each arrow sits on a fade of the sheet's colour, so it never lands on half a word (YouTube's chip bar).
  const edge = 'pointer-events-none absolute inset-y-0 z-10 flex w-14 items-center from-card from-40% to-transparent pointer-coarse:hidden'
  const arrow = 'pointer-events-auto grid size-8 place-items-center rounded-full bg-card text-foreground shadow-e2 ring-1 ring-border transition-colors duration-150 hover:bg-muted'

  return (
    <div className={cn('relative min-w-0', outer)}>
      <Tag ref={ref as never} className={className} {...rest}>
        {children}
      </Tag>
      {more.start ? (
        <span className={cn(edge, 'start-0 justify-start bg-linear-to-r ps-1 rtl:bg-linear-to-l')}>
          <button type="button" tabIndex={-1} onClick={() => go(-1)} aria-label={t('common.back')} className={arrow}>
            <ChevronLeft className="size-4.5 rtl:rotate-180" aria-hidden />
          </button>
        </span>
      ) : null}
      {more.end ? (
        <span className={cn(edge, 'end-0 justify-end bg-linear-to-l pe-1 rtl:bg-linear-to-r')}>
          <button type="button" tabIndex={-1} onClick={() => go(1)} aria-label={t('photo.next')} className={arrow}>
            <ChevronRight className="size-4.5 rtl:rotate-180" aria-hidden />
          </button>
        </span>
      ) : null}
    </div>
  )
}
