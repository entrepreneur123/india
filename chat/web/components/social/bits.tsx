'use client'

import { Bell } from 'lucide-react'
import type { Interest } from '@/lib/backend'
import { useApp } from '@/components/app-state'
import { cn } from '@/lib/utils'
import { useSocialT } from './social-text'

/** A small count on a tab, an icon or a row (99+ above that). */
export function CountBadge({ n, className }: { n: number; className?: string }) {
  if (n <= 0) return null
  return (
    <span
      className={cn(
        'inline-grid h-5 min-w-5 place-items-center rounded-full bg-primary px-1.5 text-[11px] leading-none font-bold text-primary-foreground tabular-nums',
        className,
      )}
      aria-hidden
    >
      {n > 99 ? '99+' : n}
    </span>
  )
}

/** The bell that opens Notifications, with the number of new ones. */
export function BellButton({ className }: { className?: string }) {
  const { badges, openInbox } = useApp()
  const { st } = useSocialT()
  const n = badges?.inbox ?? 0
  return (
    <button
      type="button"
      onClick={openInbox}
      aria-label={n ? st('inbox.openUnread', { n }) : st('inbox.open')}
      className={cn(
        'relative grid size-10 shrink-0 place-items-center rounded-full text-foreground transition-colors duration-150 hover:bg-muted',
        className,
      )}
    >
      <Bell className="size-5.5" strokeWidth={2} aria-hidden />
      <CountBadge n={n} className="absolute -top-0.5 -inset-e-0.5 ring-2 ring-background" />
    </button>
  )
}

/** Interest tags; the ones in `shared` stand out (both people like them). */
export function InterestChips({ tags, shared, className }: { tags: Interest[]; shared?: Interest[]; className?: string }) {
  const { tag } = useSocialT()
  if (!tags.length) return null
  return (
    <ul className={cn('flex flex-wrap gap-1.5', className)}>
      {tags.map((i) => {
        const both = shared?.includes(i)
        return (
          <li
            key={i}
            className={cn(
              'rounded-full px-3 py-1 text-xs font-semibold',
              both ? 'bg-primary-soft text-primary-text' : 'bg-muted text-muted-foreground',
            )}
          >
            {tag(i)}
          </li>
        )
      })}
    </ul>
  )
}
