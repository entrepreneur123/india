import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

export function Aurora({ className, intensity = 'normal' }: { className?: string; intensity?: 'soft' | 'normal' }) {
  const o = intensity === 'soft' ? 'opacity-30' : 'opacity-55'
  return (
    <div className={cn('pointer-events-none absolute inset-0 overflow-hidden', className)} aria-hidden>
      <div
        className={cn('absolute -top-1/3 -left-1/4 size-[70vmax] animate-aurora rounded-full blur-[110px]', o)}
        style={{ background: 'radial-gradient(circle, #ff3d68 0%, transparent 62%)' }}
      />
      <div
        className={cn('absolute -right-1/4 -bottom-1/3 size-[60vmax] animate-drift rounded-full blur-[120px]', o)}
        style={{ background: 'radial-gradient(circle, #ff7a45 0%, transparent 60%)' }}
      />
      <div
        className="absolute inset-0 opacity-[0.06] mix-blend-overlay"
        style={{
          backgroundImage:
            'url("data:image/svg+xml,%3Csvg xmlns=%27http://www.w3.org/2000/svg%27 width=%27160%27 height=%27160%27%3E%3Cfilter id=%27n%27%3E%3CfeTurbulence type=%27fractalNoise%27 baseFrequency=%270.9%27 numOctaves=%272%27/%3E%3C/filter%3E%3Crect width=%27100%25%27 height=%27100%25%27 filter=%27url(%23n)%27/%3E%3C/svg%3E")',
        }}
      />
    </div>
  )
}

type Art = 'chat' | 'people' | 'posts' | 'rooms'

function EmptyArt({ art }: { art: Art }) {
  return (
    <div className="relative size-28" aria-hidden>
      <div className="absolute inset-2 rounded-full bg-primary/8" />
      <div className="absolute inset-6 rounded-full bg-primary/10" />
      <svg viewBox="0 0 112 112" className="absolute inset-0 size-full">
        <defs>
          <linearGradient id={`g-${art}`} x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#ff3d68" />
            <stop offset="100%" stopColor="#ff7a45" />
          </linearGradient>
        </defs>
        {art === 'chat' ? (
          <>
            <rect x="26" y="34" width="48" height="32" rx="12" fill={`url(#g-${art})`} />
            <path d="M36 66l-6 10 14-10z" fill="#ff3d68" />
            <rect x="48" y="50" width="40" height="26" rx="10" className="fill-card stroke-border" strokeWidth="2" />
            <circle cx="60" cy="63" r="2.5" className="fill-muted-foreground" />
            <circle cx="68" cy="63" r="2.5" className="fill-muted-foreground" />
            <circle cx="76" cy="63" r="2.5" className="fill-muted-foreground" />
          </>
        ) : null}
        {art === 'people' ? (
          <>
            <circle cx="44" cy="46" r="11" fill={`url(#g-${art})`} />
            <path d="M24 82c2-13 10-20 20-20s18 7 20 20z" fill={`url(#g-${art})`} />
            <circle cx="70" cy="50" r="9" className="fill-card stroke-border" strokeWidth="2" />
            <path d="M54 82c2-11 8-16 16-16s14 5 16 16z" className="fill-card stroke-border" strokeWidth="2" />
          </>
        ) : null}
        {art === 'posts' ? (
          <>
            <rect x="28" y="30" width="56" height="52" rx="12" className="fill-card stroke-border" strokeWidth="2" />
            <circle cx="44" cy="46" r="6" fill={`url(#g-${art})`} />
            <path d="M32 76l16-16 10 10 8-8 14 14z" fill={`url(#g-${art})`} />
          </>
        ) : null}
        {art === 'rooms' ? (
          <>
            <rect x="24" y="34" width="28" height="28" rx="9" fill={`url(#g-${art})`} />
            <rect x="58" y="34" width="28" height="28" rx="9" className="fill-card stroke-border" strokeWidth="2" />
            <rect x="24" y="66" width="62" height="14" rx="7" className="fill-card stroke-border" strokeWidth="2" />
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
    <div className={cn('flex flex-col items-center gap-3 px-6 py-12 text-center', className)}>
      <EmptyArt art={art} />
      <h3 className="text-base font-bold tracking-tight">{title}</h3>
      {body ? <p className="max-w-xs text-sm leading-relaxed text-muted-foreground text-pretty">{body}</p> : null}
      {action ? <div className="mt-2">{action}</div> : null}
    </div>
  )
}
