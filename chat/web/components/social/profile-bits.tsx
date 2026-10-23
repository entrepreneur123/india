'use client'

import type { CSSProperties, ReactNode } from 'react'
import type { Accent, Profile, ProfilePrompt, SpokenLang } from '@/lib/backend'
import { flagEmoji } from '@/lib/format'
import { accentVars, EMPTY_STYLE, nativeName } from '@/lib/profile-style'
import { isVip, nameAge, type FrameId } from '@/lib/looks'
import { Avatar } from '@/components/ui/avatar'
import { cn } from '@/lib/utils'
import { VerifiedMark } from '@/components/trust/badge'
import { FrameAvatar, FrameCard } from '@/components/looks/frames'
import { LookName, useLook } from '@/components/looks/look-name'
import { useFeedT } from './feed-text'
import { useLooksT } from '@/components/looks/looks-text'

// A profile made its own: the frame (040, a bought look) around the card and the photo, the name and the decorated
// @ID, the status, languages and prompt cards (033). The status, languages and prompts use the brand's colours as CSS
// variables (lib/profile-style.ts accentVars; the old per-profile colour is gone).

/** The accent's variables for a subtree (`--acc-via/to/ink/night`). */
export const accentStyle = (accent: Accent | null | undefined) => accentVars(accent) as CSSProperties

/** Text in the accent: the deep tone on light screens, the light one at night. */
export const ACCENT_TEXT = 'text-[var(--acc-ink)] night:text-[var(--acc-night)]'

/** An avatar inside a thin ring of the person's accent (like a story ring). */
export function AccentAvatar({
  name,
  src,
  size,
  accent,
  className,
}: {
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
        <Avatar name={name} src={src} size={size} />
      </span>
    </span>
  )
}

/**
 * The profile card in its frame: the photo in the frame's ring, name, age and flag, the decorated @ID, city, status,
 * bio and languages. `slot`: buttons in the card's top corner (settings, close); `statusSlot`: shown when there is no
 * status (my own "Add a status").
 */
export function ProfileHero({
  profile,
  slot,
  statusSlot,
  badge,
  verified,
  facts,
  className,
}: {
  profile: Profile
  slot?: ReactNode
  statusSlot?: ReactNode
  badge?: ReactNode
  /** The verified mark after the name (035). */
  verified?: boolean
  /** Under the name: the facts from the ID (components/trust/trust-cards.tsx IdentityFacts). */
  facts?: ReactNode
  className?: string
}) {
  const style = profile.style ?? EMPTY_STYLE
  const frame = (useLook(profile.id)?.frame ?? null) as FrameId | null
  const { lt } = useLooksT()
  return (
    <FrameCard frame={frame} className={className}>
      {slot}
      <section className="flex flex-col items-center px-2 pt-14 pb-6 text-center sm:pt-10" style={accentStyle(style.accent)}>
        <FrameAvatar frame={frame} name={profile.name} src={profile.photoUrl} size={104} />
        <h2 className="mt-3 flex flex-wrap items-center justify-center gap-x-2 px-4 text-2xl font-bold tracking-tight">
          <span className="inline-flex items-center gap-1.5">
            <span>{nameAge(profile.name, profile.age)}</span>
            {verified ? <VerifiedMark size={24} /> : null}
          </span>
          <span aria-hidden>{flagEmoji(profile.country)}</span>
          {badge}
        </h2>
        <LookName userId={profile.id} handle={profile.handle} className="mt-0.5 max-w-full px-4 text-base font-semibold" />
        {profile.city ? <p className="text-sm text-muted-foreground">{profile.city}</p> : null}
        {isVip(profile) ? (
          <p className="mt-2 max-w-sm rounded-full bg-primary px-3.5 py-1.5 text-xs font-semibold text-balance text-primary-foreground">{lt('vip.seen')}</p>
        ) : null}
        {facts ? <div className="mt-2 flex max-w-full justify-center px-3">{facts}</div> : null}
        {style.status ? (
          <p
            className={cn(
              'mt-3 max-w-sm rounded-full bg-primary-soft px-4 py-1.5 text-sm font-semibold text-balance',
              ACCENT_TEXT,
            )}
          >
            {style.status}
          </p>
        ) : (
          statusSlot
        )}
        {profile.bio ? <p className="mt-3 max-w-md px-4 text-[15px] leading-relaxed text-pretty">{profile.bio}</p> : null}
        <LanguageChips languages={style.languages} className="mt-3 justify-center px-4" />
      </section>
    </FrameCard>
  )
}

export function LanguageChips({ languages, className }: { languages: SpokenLang[]; className?: string }) {
  const { ft } = useFeedT()
  if (!languages.length) return null
  return (
    <div className={cn('flex flex-wrap items-center gap-1.5', className)}>
      <span className="text-xs font-semibold text-muted-foreground">{ft('profile.speaks')}</span>
      <ul className="contents">
        {languages.map((l) => (
          <li
            key={l}
            lang={l}
            className="rounded-full border border-border px-2.5 py-0.5 text-xs font-semibold"
          >
            {nativeName(l)}
          </li>
        ))}
      </ul>
    </div>
  )
}

/** Answered prompts as plain cards, the question in the accent (Hinge-like). */
export function PromptCards({ prompts, accent, className }: { prompts: ProfilePrompt[]; accent: Accent | null | undefined; className?: string }) {
  const { ft } = useFeedT()
  if (!prompts.length) return null
  return (
    <ul className={cn('grid gap-3', className)} style={accentStyle(accent)}>
      {prompts.map((p) => (
        <li key={p.q} className="rounded-3xl bg-card px-5 pt-4 pb-5 ring-1 ring-border">
          <p className={cn('text-[13px] font-bold', ACCENT_TEXT)}>{ft(`prompt.${p.q}`)}</p>
          <p className="mt-1.5 text-lg leading-snug font-bold tracking-tight text-pretty">{p.a}</p>
        </li>
      ))}
    </ul>
  )
}
