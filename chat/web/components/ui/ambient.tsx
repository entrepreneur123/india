import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

type Art = 'chat' | 'people' | 'posts' | 'rooms' | 'feed' | 'discover' | 'comments'

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
        {art === 'feed' ? (
          <>
            <rect x="34" y="24" width="44" height="10" rx="5" className="fill-card stroke-current" strokeWidth="3" />
            <rect x="26" y="34" width="60" height="52" rx="14" className="fill-card stroke-current" strokeWidth="3" />
            <path d="M56 76c-8-5.6-12.4-10-12.4-15.2a6.4 6.4 0 0 1 12.4-2.3 6.4 6.4 0 0 1 12.4 2.3C68.4 66 64 70.4 56 76z" fill="currentColor" />
          </>
        ) : null}
        {art === 'discover' ? (
          <>
            <circle cx="56" cy="56" r="30" className="fill-card stroke-current" strokeWidth="3" />
            <path d="M68 44l-6.5 17.5L44 68l6.5-17.5z" fill="currentColor" />
            <circle cx="56" cy="56" r="3.5" className="fill-card" />
            <circle cx="86" cy="30" r="5" fill="currentColor" />
            <circle cx="26" cy="84" r="3.5" fill="currentColor" />
          </>
        ) : null}
        {art === 'comments' ? (
          <>
            <path d="M22 44c0-9 7-15 16-15h24c9 0 16 6 16 15v6c0 9-7 15-16 15H44l-12 9v-10c-6-2-10-7-10-14z" fill="currentColor" />
            <path d="M48 62c0-8 6-13 14-13h16c8 0 14 5 14 13v4c0 8-6 13-14 13h-2v9l-11-9h-3c-8 0-14-5-14-13z" className="fill-card stroke-current" strokeWidth="3" />
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
