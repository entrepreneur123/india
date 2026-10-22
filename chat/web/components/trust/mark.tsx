'use client'

import type { CSSProperties } from 'react'
import { useI18n } from '@/components/i18n'
import { cn } from '@/lib/utils'

// The verified mark (035), on its own so the sign-up screen can show it without the signed-in app's code.

/**
 * Yaari's verified mark: a rounded shield cut in two facets of the brand colour (deep on the left, bright on the
 * right), a soft highlight and a white tick. Drawn like the room glyphs; never an emoji or a borrowed badge.
 */
export function VerifiedMark({ size = 16, className, label }: { size?: number; className?: string; label?: string | null }) {
  const { t } = useI18n()
  const name = label === null ? undefined : label ?? t('trust.verified')
  return (
    <svg
      viewBox="0 0 24 24"
      width={size}
      height={size}
      role={name ? 'img' : undefined}
      aria-label={name}
      aria-hidden={name ? undefined : true}
      focusable="false"
      className={cn('inline-block shrink-0 align-[-0.125em]', className)}
      style={{ '--vm': '#ff3d68', '--vm-deep': '#c8174a' } as CSSProperties}
    >
      {name ? <title>{name}</title> : null}
      <path
        d="M12 1.9c2.3 1.55 4.85 2.45 7.55 2.7.68.07 1.2.63 1.2 1.31v5.15c0 4.86-3.3 8.86-8.15 10.85-.38.16-.82.16-1.2 0C6.55 19.92 3.25 15.92 3.25 11.06V5.9c0-.68.52-1.24 1.2-1.3C7.15 4.35 9.7 3.45 12 1.9z"
        fill="var(--vm)"
      />
      <path d="M12 1.9C9.7 3.45 7.15 4.35 4.45 4.6c-.68.06-1.2.62-1.2 1.3v5.16c0 4.86 3.3 8.86 8.15 10.85.19.08.4.12.6.12z" fill="var(--vm-deep)" />
      <ellipse cx="7.3" cy="7.4" rx="1.7" ry="1.05" transform="rotate(-32 7.3 7.4)" fill="#fff" opacity=".35" />
      <path d="M8 12.3l2.75 2.7 5.3-5.75" fill="none" stroke="#fff" strokeWidth="2.35" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}
