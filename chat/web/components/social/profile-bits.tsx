'use client'

import type { CSSProperties, ReactNode } from 'react'
import type { Accent, Profile, ProfilePrompt, ProfileStyle, SpokenLang } from '@/lib/backend'
import { flagEmoji } from '@/lib/format'
import { accentVars, EMPTY_STYLE, nativeName } from '@/lib/profile-style'
import { Avatar } from '@/components/ui/avatar'
import { cn } from '@/lib/utils'
import { VerifiedMark } from '@/components/trust/badge'
import { useFeedT } from './feed-text'

// A profile made its own (033): the cover (or the accent's gradient), the name ring, the status, languages and
// prompt cards. Colours come from the accent as CSS variables (lib/profile-style.ts accentVars).

/** The accent's variables for a subtree (`--acc-from/via/to/ink/night`). */
export const accentStyle = (accent: Accent | null | undefined) => accentVars(accent) as CSSProperties

/** Text in the accent: the deep tone on light screens, the light one at night. */
export const ACCENT_TEXT = 'text-[var(--acc-ink)] night:text-[var(--acc-night)]'

/** An avatar inside a ring of the person's accent gradient (like a story ring). */
export function AccentAvatar({
  name,
  src,
  size,
  accent,
  ring = 'thin',
  className,
}: {
  name: string
  src?: string | null
  size: number
  accent: Accent | null | undefined
  ring?: 'thin' | 'thick'
  className?: string
}) {
  const pad = ring === 'thick' ? Math.max(3, Math.round(size * 0.04)) : 2
  const gap = ring === 'thick' ? Math.max(3, Math.round(size * 0.035)) : 2
  return (
    <span
      className={cn('relative inline-grid shrink-0 place-items-center rounded-full', className)}
      style={{
        ...accentStyle(accent),
        width: size + 2 * (pad + gap),
        height: size + 2 * (pad + gap),
        padding: pad,
        background: 'conic-gradient(from 210deg, var(--acc-from), var(--acc-via), var(--acc-to), var(--acc-from))',
      }}
    >
      <span className="grid place-items-center rounded-full bg-card" style={{ padding: gap }}>
        <Avatar name={name} src={src} size={size} />
      </span>
    </span>
  )
}

/** The top of a profile: the cover photo, or the accent's gradient with soft shapes. */
export function ProfileCover({
  style,
  className,
  children,
}: {
  style: Pick<ProfileStyle, 'cover' | 'accent'>
  className?: string
  children?: ReactNode
}) {
  return (
    <div
      className={cn('relative overflow-hidden rounded-[28px]', className)}
      style={{ ...accentStyle(style.accent), background: 'linear-gradient(135deg, var(--acc-from) 0%, var(--acc-via) 55%, var(--acc-to) 100%)' }}
    >
      {style.cover ? (
        <>
          <img src={style.cover.url} alt="" className="absolute inset-0 size-full object-cover" decoding="async" />
          <span className="absolute inset-0 bg-gradient-to-b from-black/10 via-transparent to-black/25" aria-hidden />
        </>
      ) : (
        <span aria-hidden>
          <span className="absolute -top-16 -inset-s-10 size-56 rounded-full bg-white/18" />
          <span className="absolute -inset-e-12 -bottom-24 size-72 rounded-full bg-white/12" />
          <span className="absolute top-6 inset-e-[22%] size-14 rounded-full bg-white/16" />
          <span className="absolute bottom-5 inset-s-[30%] size-6 rounded-full bg-white/25" />
        </span>
      )}
      {children}
    </div>
  )
}

/**
 * Cover, avatar in its ring, name, age and flag, city, status, bio and languages. `coverSlot`: buttons over the
 * cover (settings, close); `statusSlot`: shown when there is no status (my own "Add a status").
 */
export function ProfileHero({
  profile,
  coverSlot,
  statusSlot,
  badge,
  verified,
  facts,
  className,
}: {
  profile: Profile
  coverSlot?: ReactNode
  statusSlot?: ReactNode
  badge?: ReactNode
  /** The verified mark after the name (035). */
  verified?: boolean
  /** Under the name: the facts from the ID (components/trust/trust-cards.tsx IdentityFacts). */
  facts?: ReactNode
  className?: string
}) {
  const style = profile.style ?? EMPTY_STYLE
  return (
    <section className={cn('flex flex-col items-center text-center', className)} style={accentStyle(style.accent)}>
      <ProfileCover style={style} className="h-36 w-full sm:h-48">
        {coverSlot}
      </ProfileCover>
      <AccentAvatar name={profile.name} src={profile.photoUrl} size={104} accent={style.accent} ring="thick" className="-mt-16 shadow-e2" />
      <h2 className="mt-3 flex flex-wrap items-center justify-center gap-x-2 px-4 text-2xl font-bold tracking-tight">
        <span className="inline-flex items-center gap-1.5">
          <span>
            {profile.name}, {profile.age}
          </span>
          {verified ? <VerifiedMark size={24} /> : null}
        </span>
        <span aria-hidden>{flagEmoji(profile.country)}</span>
        {badge}
      </h2>
      {profile.city ? <p className="text-sm text-muted-foreground">{profile.city}</p> : null}
      {facts ? <div className="mt-2 flex max-w-full justify-center px-3">{facts}</div> : null}
      {style.status ? (
        <p
          className={cn(
            'mt-3 max-w-sm rounded-full bg-[color-mix(in_srgb,var(--acc-via)_12%,var(--card))] px-4 py-1.5 text-sm font-semibold text-balance',
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
            className={cn(
              'rounded-full border border-[color-mix(in_srgb,var(--acc-via)_35%,transparent)] px-2.5 py-0.5 text-xs font-semibold',
              ACCENT_TEXT,
            )}
          >
            {nativeName(l)}
          </li>
        ))}
      </ul>
    </div>
  )
}

/** Answered prompts as cards tinted with the accent (Hinge-like). */
export function PromptCards({ prompts, accent, className }: { prompts: ProfilePrompt[]; accent: Accent | null | undefined; className?: string }) {
  const { ft } = useFeedT()
  if (!prompts.length) return null
  return (
    <ul className={cn('grid gap-3', className)} style={accentStyle(accent)}>
      {prompts.map((p, i) => (
        <li
          key={p.q}
          className="relative overflow-hidden rounded-3xl bg-[color-mix(in_srgb,var(--acc-via)_9%,var(--card))] px-5 pt-4 pb-5 ring-1 ring-[color-mix(in_srgb,var(--acc-via)_22%,transparent)]"
        >
          <span
            aria-hidden
            className="pointer-events-none absolute -top-14 -inset-e-10 size-36 rounded-full"
            style={{ background: `radial-gradient(closest-side, color-mix(in srgb, var(--acc-${i % 2 ? 'from' : 'via'}) 32%, transparent), transparent)` }}
          />
          <p className={cn('relative text-[13px] font-bold', ACCENT_TEXT)}>{ft(`prompt.${p.q}`)}</p>
          <p className="relative mt-1.5 text-lg leading-snug font-bold tracking-tight text-pretty">{p.a}</p>
        </li>
      ))}
    </ul>
  )
}
