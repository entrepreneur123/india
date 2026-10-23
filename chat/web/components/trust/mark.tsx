'use client'

import { useI18n } from '@/components/i18n'
import { cn } from '@/lib/utils'

// The verified mark (035), on its own so the sign-up screen can show it without the signed-in app's code.

/**
 * Yaari's verified mark: a flat saffron rounded shield with a black tick (8:1). Drawn like the room glyphs; never an
 * emoji or a borrowed badge.
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
    >
      {name ? <title>{name}</title> : null}
      <path
        d="M12 1.9c2.3 1.55 4.85 2.45 7.55 2.7.68.07 1.2.63 1.2 1.31v5.15c0 4.86-3.3 8.86-8.15 10.85-.38.16-.82.16-1.2 0C6.55 19.92 3.25 15.92 3.25 11.06V5.9c0-.68.52-1.24 1.2-1.3C7.15 4.35 9.7 3.45 12 1.9z"
        fill="#ff8a00"
      />
      <path d="M8 12.3l2.75 2.7 5.3-5.75" fill="none" stroke="#111111" strokeWidth="2.35" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}
