import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

/** A very light, static brand tint behind the random-chat lobby (no blur, no animation: cheap on any phone). */
export function Aurora({ className }: { className?: string }) {
  return (
    <div
      className={cn('pointer-events-none absolute inset-0', className)}
      style={{ background: 'radial-gradient(60% 45% at 50% 0%, rgb(255 61 104 / 0.10), transparent 70%)' }}
      aria-hidden
    />
  )
}

type Art = 'chat' | 'people' | 'posts' | 'rooms'

/** Simple two-tone line art in the brand colour, like empty states in Google apps. */
function EmptyArt({ art }: { art: Art }) {
  return (
    <div className="grid size-24 place-items-center rounded-full bg-primary-soft" aria-hidden>
      <svg viewBox="0 0 112 112" className="size-20 text-primary-text">
        {art === 'chat' ? (
          <>
            <rect x="24" y="32" width="48" height="32" rx="12" fill="currentColor" />
            <path d="M34 64l-6 10 14-10z" fill="currentColor" />
            <rect x="46" y="48" width="42" height="28" rx="11" className="fill-card stroke-current" strokeWidth="3" />
            <circle cx="59" cy="62" r="2.5" fill="currentColor" />
            <circle cx="67" cy="62" r="2.5" fill="currentColor" />
            <circle cx="75" cy="62" r="2.5" fill="currentColor" />
          </>
        ) : null}
        {art === 'people' ? (
          <>
            <circle cx="44" cy="46" r="11" fill="currentColor" />
            <path d="M24 82c2-13 10-20 20-20s18 7 20 20z" fill="currentColor" />
            <circle cx="70" cy="50" r="9" className="fill-card stroke-current" strokeWidth="3" />
            <path d="M54 82c2-11 8-16 16-16s14 5 16 16z" className="fill-card stroke-current" strokeWidth="3" />
          </>
        ) : null}
        {art === 'posts' ? (
          <>
            <rect x="28" y="30" width="56" height="52" rx="12" className="fill-card stroke-current" strokeWidth="3" />
            <circle cx="45" cy="47" r="6" fill="currentColor" />
            <path d="M33 75l15-15 10 10 8-8 13 13z" fill="currentColor" />
          </>
        ) : null}
        {art === 'rooms' ? (
          <>
            <rect x="24" y="34" width="28" height="28" rx="9" fill="currentColor" />
            <rect x="58" y="34" width="28" height="28" rx="9" className="fill-card stroke-current" strokeWidth="3" />
            <rect x="24" y="68" width="62" height="12" rx="6" className="fill-card stroke-current" strokeWidth="3" />
          </>
        ) : null}
      </svg>
    </div>
  )
}

export function EmptyState({
  art,
  title,
  body,
  action,
  className,
}: {
  art: Art
  title: string
  body?: string
  action?: ReactNode
  className?: string
}) {
  return (
    <div className={cn('flex flex-col items-center gap-2 px-6 py-12 text-center', className)}>
      <EmptyArt art={art} />
      <h3 className="mt-3 text-base font-semibold">{title}</h3>
      {body ? <p className="max-w-xs text-sm leading-relaxed text-pretty text-muted-foreground">{body}</p> : null}
      {action ? <div className="mt-4">{action}</div> : null}
    </div>
  )
}
