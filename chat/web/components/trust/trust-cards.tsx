'use client'

import type { CSSProperties } from 'react'
import { ChevronRight } from 'lucide-react'
import type { Profile, Trust } from '@/lib/backend'
import { flagEmoji } from '@/lib/format'
import { LEVEL_COLORS, mannerLevel, MANNERS, mannerShown, type MannerLevel } from '@/lib/trust'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Skeleton } from '@/components/ui/bits'
import { cn } from '@/lib/utils'
import { ClockIcon, LockIcon } from './art'
import { VerifiedMark } from './badge'
import { useTrustT, type TrustKey } from './trust-text'

/** A country's name in the app's language (the code itself when the browser can't name it). */
function useCountryName() {
  const { locale } = useI18n()
  let names: Intl.DisplayNames | null = null
  try {
    names = new Intl.DisplayNames([locale], { type: 'region' })
  } catch {
    names = null
  }
  return (code: string) => {
    try {
      return names?.of(code.toUpperCase()) ?? code
    } catch {
      return code
    }
  }
}

/** "ID verified · Woman · 24 · India" with a lock: these come from the identity document and can't be changed. */
export function IdentityFacts({ profile, className }: { profile: Profile; className?: string }) {
  const { t } = useI18n()
  const { tt } = useTrustT()
  const country = useCountryName()
  const gender = profile.gender === 'female' ? t('pf.woman') : profile.gender === 'male' ? t('pf.man') : t('pf.other')
  const parts = [gender, profile.age > 0 ? tt('facts.age', { n: profile.age }) : null, profile.country ? `${flagEmoji(profile.country)} ${country(profile.country)}` : null].filter(Boolean)
  return (
    <p
      className={cn(
        'inline-flex max-w-full flex-wrap items-center justify-center gap-x-2 gap-y-0.5 rounded-full bg-primary-soft py-1.5 pe-3 ps-2 text-[13px] font-semibold',
        className,
      )}
    >
      <span className="inline-flex items-center gap-1.5 text-primary-text">
        <VerifiedMark size={17} label={null} />
        {tt('facts.verified')}
      </span>
      {parts.map((p) => (
        <span key={p} className="inline-flex items-center gap-2">
          <span aria-hidden className="text-muted-foreground">·</span>
          {p}
        </span>
      ))}
      <span title={tt('facts.locked')} className="inline-flex">
        <LockIcon size={14} className="opacity-80" />
        <span className="sr-only">{tt('facts.locked')}</span>
      </span>
    </p>
  )
}

const ARC = 'M14 64 A50 50 0 0 1 114 64'

/** A half-circle gauge from red to teal, filled up to the score, with a knob at its end. */
export function MannerGauge({ score, size = 168, muted }: { score: number; size?: number; muted?: boolean }) {
  const s = Math.min(100, Math.max(0, score))
  const a = (Math.PI * s) / 100
  const knob = { x: 64 - 50 * Math.cos(a), y: 64 - 50 * Math.sin(a) }
  const color = LEVEL_COLORS[mannerLevel(s)]
  return (
    <svg viewBox="0 0 128 74" width={size} height={(size * 74) / 128} aria-hidden focusable="false" className="shrink-0 overflow-visible">
      <defs>
        <linearGradient id="manner-arc" x1="14" y1="0" x2="114" y2="0" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#e5484d" />
          <stop offset=".3" stopColor="#f2711c" />
          <stop offset=".55" stopColor="#f2c230" />
          <stop offset=".78" stopColor="#22a35a" />
          <stop offset="1" stopColor="#0e9f9a" />
        </linearGradient>
      </defs>
      <path d={ARC} fill="none" stroke="var(--muted)" strokeWidth="13" strokeLinecap="round" />
      {muted ? null : (
        <>
          <path d={ARC} fill="none" stroke="url(#manner-arc)" strokeWidth="13" strokeLinecap="round" pathLength={100} strokeDasharray={`${Math.max(0.5, s)} 200`} />
          <circle cx={knob.x} cy={knob.y} r="8.2" fill="#fff" stroke={color} strokeWidth="3.6" />
        </>
      )}
    </svg>
  )
}

const levelKey = (l: MannerLevel) => `manner.level.${l}` as TrustKey

/**
 * Someone's manners: the gauge with the score and its level, and the good words people used most ("Kind ×12").
 * Under 3 ratings: "New here". Nothing while manners are off (MANNERS).
 */
export function MannerCard({ trust, className }: { trust: Trust | null; className?: string }) {
  const { num } = useI18n()
  const { tt, plural } = useTrustT()
  if (!MANNERS) return null
  if (!trust) return <Skeleton className={cn('h-40 rounded-[28px]', className)} />
  const shown = mannerShown(trust.ratings)
  const level = mannerLevel(trust.manner)
  const color = LEVEL_COLORS[level]
  return (
    <section
      className={cn(
        'relative overflow-hidden rounded-[28px] px-5 pt-4 pb-5 ring-1',
        shown ? 'bg-[color-mix(in_srgb,var(--lv)_8%,var(--card))] ring-[color-mix(in_srgb,var(--lv)_22%,transparent)]' : 'bg-card ring-border',
        className,
      )}
      style={{ '--lv': color } as CSSProperties}
      aria-label={tt('manner.title')}
    >
      <span
        aria-hidden
        className="pointer-events-none absolute -top-16 -inset-e-14 size-44 rounded-full"
        style={{ background: shown ? `radial-gradient(closest-side, color-mix(in srgb, ${color} 26%, transparent), transparent)` : undefined }}
      />
      <div className="relative flex items-center justify-between gap-3">
        <h3 className="text-sm font-bold">{tt('manner.title')}</h3>
        {shown ? (
          <span className="rounded-full px-2.5 py-0.5 text-xs font-bold text-white" style={{ background: color }}>
            {tt(levelKey(level))}
          </span>
        ) : (
          <span className="rounded-full bg-muted px-2.5 py-0.5 text-xs font-bold text-muted-foreground">{tt('manner.new')}</span>
        )}
      </div>
      <div className="relative mt-2 flex flex-col items-center">
        <div className="relative">
          <MannerGauge score={shown ? trust.manner : 0} muted={!shown} />
          <div className="absolute inset-x-0 bottom-0 flex flex-col items-center">
            {shown ? (
              <>
                <span className="text-[34px] leading-none font-extrabold tracking-tight tabular-nums" style={{ color }}>
                  {num(trust.manner)}
                </span>
                <span className="mt-0.5 text-[11px] font-semibold text-muted-foreground">{tt('manner.outOf')}</span>
              </>
            ) : (
              <span className="text-lg leading-none font-extrabold text-muted-foreground">{tt('manner.new')}</span>
            )}
          </div>
        </div>
        <p className="mt-2 text-xs text-muted-foreground">
          {shown ? plural(trust.ratings, 'manner.from.one', 'manner.from.other') : tt('manner.newBody')}
        </p>
      </div>
      {shown && trust.tags.length ? (
        <ul className="relative mt-3 flex flex-wrap justify-center gap-1.5">
          {trust.tags.map((x) => (
            <li
              key={x.tag}
              className="rounded-full bg-card px-3 py-1 text-xs font-bold shadow-[0_1px_2px_rgb(0_0_0/0.08)] ring-1 ring-[color-mix(in_srgb,var(--lv)_30%,transparent)]"
            >
              {tt('manner.tagCount', { tag: tt(`tag.${x.tag}` as TrustKey), n: num(x.n) })}
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  )
}

/**
 * My verification on Me: one row until I'm verified (the check, its pending or failed result), a quiet line while
 * checks aren't open yet, nothing once verified (the mark after my name says it).
 */
export function VerifyCard({ className }: { className?: string }) {
  const { trust, kyc, openVerify } = useApp()
  const { tt } = useTrustT()
  if (!trust || trust.verified) return null
  const result = kyc.result ?? trust.kyc?.result ?? null
  if (kyc.avail === 'off') {
    return (
      <div className={cn('flex items-center gap-3 rounded-3xl bg-muted px-4 py-3', className)}>
        <VerifiedMark size={28} label={null} className="opacity-60 grayscale-[.4]" />
        <span className="min-w-0">
          <span className="block text-sm font-bold">{tt('card.soonTitle')}</span>
          <span className="block text-xs text-muted-foreground">{tt('card.soonBody')}</span>
        </span>
      </div>
    )
  }
  if (result === 'pending') {
    return (
      <button
        type="button"
        onClick={() => openVerify('me')}
        className={cn('flex w-full items-center gap-3 rounded-3xl bg-card px-4 py-3.5 text-start ring-1 ring-border', className)}
      >
        <ClockIcon size={34} />
        <span className="min-w-0 flex-1">
          <span className="block text-[15px] font-bold">{tt('card.pending')}</span>
          <span className="block text-sm text-muted-foreground">{tt('card.pendingBody')}</span>
        </span>
        <ChevronRight className="size-5 text-muted-foreground rtl:rotate-180" aria-hidden />
      </button>
    )
  }
  if (result === 'duplicate' || result === 'minor') {
    return (
      <button type="button" onClick={() => openVerify('me')} className={cn('w-full rounded-3xl bg-muted px-4 py-3 text-start', className)}>
        <span className="block text-sm font-bold">{tt(result === 'duplicate' ? 'verify.duplicateTitle' : 'verify.minorTitle')}</span>
      </button>
    )
  }
  const retry = result === 'declined' || result === 'unreadable'
  return (
    <button
      type="button"
      onClick={() => openVerify('me')}
      className={cn('flex w-full items-center gap-3 rounded-2xl bg-card px-4 py-3 text-start ring-1 ring-border transition-colors hover:bg-muted', className)}
    >
      <VerifiedMark size={30} label={null} />
      <span className="min-w-0 flex-1">
        <span className="block text-[15px] font-bold">{tt(retry ? 'card.retry' : 'card.title')}</span>
        <span className="block text-[13px] leading-snug text-muted-foreground">{tt(retry ? 'card.retryBody' : 'card.body')}</span>
      </span>
      <ChevronRight className="size-5 shrink-0 text-muted-foreground rtl:rotate-180" aria-hidden />
    </button>
  )
}
