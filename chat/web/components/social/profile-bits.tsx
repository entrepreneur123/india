'use client'

import { useState, type CSSProperties, type ReactNode } from 'react'
import type { Accent, Profile } from '@/lib/backend'
import { flagEmoji } from '@/lib/format'
import { accentVars, EMPTY_STYLE } from '@/lib/profile-style'
import { isVip, nameAge, type FrameId } from '@/lib/looks'
import { Avatar } from '@/components/ui/avatar'
import { cn } from '@/lib/utils'
import { VerifiedMark } from '@/components/trust/badge'
import { FrameAvatar } from '@/components/looks/frames'
import { LookName, PersonName, useLook } from '@/components/looks/look-name'
import { useLooksT } from '@/components/looks/looks-text'
import { UserAvatar } from '@/components/looks/user-avatar'
import { PhotoViewer } from '@/components/ui/photo-viewer'

// A profile laid out as Instagram's (the owner, 2026-10-29): the @ID up top, the photo in its frame (040, a bought
// look) beside the counts, then the name and bio. No card, MBTI or language chips.

/** The accent's variables for a subtree (`--acc-via/to/ink`). */
export const accentStyle = (accent: Accent | null | undefined) => accentVars(accent) as CSSProperties

/** An avatar inside a thin ring of the person's accent (like a story ring). */
export function AccentAvatar({
  userId,
  name,
  src,
  size,
  accent,
  className,
}: {
  /** Shows their photo gradient (062). */
  userId?: string
  name: string
  src?: string | null
  size: number
  accent: Accent | null | undefined
  className?: string
}) {
  const pad = 2
  const gap = 2
  return (
    <span
      className={cn('relative inline-grid shrink-0 place-items-center rounded-full', className)}
      style={{
        ...accentStyle(accent),
        width: size + 2 * (pad + gap),
        height: size + 2 * (pad + gap),
        padding: pad,
        background: 'var(--acc-via)',
      }}
    >
      <span className="grid place-items-center rounded-full bg-card" style={{ padding: gap }}>
        {userId ? <UserAvatar userId={userId} name={name} src={src} size={size} /> : <Avatar name={name} src={src} size={size} />}
      </span>
    </span>
  )
}

/** A count over its word, as Instagram's posts / followers / following; a tap opens the people behind it. */
export function ProfileStat({ n, label, onClick }: { n: string; label: string; onClick?: () => void }) {
  return (
    <button type="button" onClick={onClick} disabled={!onClick} className="flex min-w-0 flex-1 flex-col items-center rounded-lg px-1 leading-tight enabled:active:opacity-60">
      <span className="text-[17px] font-bold tabular-nums">{n}</span>
      {/* Three columns share the row; a long label wraps (two words) or breaks (one long word: overflow-wrap anywhere, which
          also lets the column shrink below the word) instead of overlapping its neighbour. */}
      <span className="text-center text-[13px] text-muted-foreground [overflow-wrap:anywhere]">{label}</span>
    </button>
  )
}

/**
 * The top of a profile, laid out as Instagram's: the @ID in the bar with `slot` (buttons) at its end; the photo in its
 * frame (040) with the note (32) over it, and `stats` beside it; then the name, age, verified mark and flag, and the
 * bio. Tapping the photo opens it large with the extra photos (39).
 */
export function ProfileHero({
  profile,
  slot,
  badge,
  verified,
  stats,
  className,
}: {
  profile: Profile
  slot?: ReactNode
  badge?: ReactNode
  /** The verified mark after the name (035). */
  verified?: boolean
  /** Beside the photo: ProfileStat columns. */
  stats?: ReactNode
  className?: string
}) {
  const style = profile.style ?? EMPTY_STYLE
  const frame = (useLook(profile.id)?.frame ?? null) as FrameId | null
  const { lt } = useLooksT()
  const [shown, setShown] = useState<number | null>(null)
  const photos = profile.photoUrl ? [profile.photoUrl, ...(profile.photos ?? []).map((p) => p.url)] : []
  const avatar = <FrameAvatar frame={frame} userId={profile.id} name={profile.name} src={profile.photoUrl} size={84} />
  return (
    <section className={cn('px-4', className)} style={accentStyle(style.accent)}>
      <div className="flex h-12 items-center gap-2">
        <LookName userId={profile.id} handle={profile.handle} className="min-w-0 flex-1 truncate text-lg font-bold" />
        {slot}
      </div>
      <div className={cn('flex items-center gap-4', style.status ? 'pt-9' : 'pt-2')}>
        <span className="relative shrink-0">
          {style.status ? (
            <span
              dir="auto"
              className="absolute bottom-[calc(100%-0.75rem)] inset-s-0 z-10 w-max max-w-36 rounded-2xl bg-card px-2.5 py-1.5 text-center text-[11px] leading-tight shadow-e1 ring-1 ring-border"
            >
              <span className="line-clamp-2 break-words">{style.status}</span>
            </span>
          ) : null}
          {photos.length ? (
            <button type="button" onClick={() => setShown(0)} className="rounded-full" aria-label={profile.name}>
              {avatar}
            </button>
          ) : avatar}
        </span>
        <PhotoViewer photos={photos} index={shown} onIndex={setShown} label={profile.name} />
        {stats ? <div className="flex min-w-0 flex-1 justify-around">{stats}</div> : null}
      </div>
      <h2 className="mt-3 flex flex-wrap items-center gap-x-1.5 text-[15px] font-semibold">
        {isVip(profile) ? <span className="vip-gold italic">{nameAge(profile.name, profile.age)}</span> : <PersonName p={profile} />}
        {verified ? <VerifiedMark size={16} /> : null}
        <span aria-hidden>{flagEmoji(profile.country)}</span>
        {badge}
      </h2>
      {isVip(profile) ? <p className="mt-0.5 text-xs font-semibold text-[#8a6510]">{lt('vip.seen')}</p> : null}
      {profile.bio ? <p className="mt-1 text-sm leading-snug whitespace-pre-line text-pretty" dir="auto">{profile.bio}</p> : null}
    </section>
  )
}
