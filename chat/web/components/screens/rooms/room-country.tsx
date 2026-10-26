'use client'

import { COUNTRIES } from '@/lib/countries'
import { flagEmoji } from '@/lib/format'
import { useCountryNames } from '@/components/screens/auth/country-select'
import { cn } from '@/lib/utils'

/** A room's country (28: its host's) as a chip like the old category chip: flag and name; nothing when unknown. */
export function RoomCountry({ iso, className }: { iso: string | null | undefined; className?: string }) {
  const nameOf = useCountryNames()
  const c = COUNTRIES.find((x) => x.iso === iso)
  if (!c) return null
  return (
    <span
      className={cn(
        'inline-flex h-7 max-w-full items-center gap-1.5 rounded-full bg-card pe-3 ps-2 text-[12px] font-semibold text-foreground ring-1 ring-border',
        className,
      )}
    >
      <span aria-hidden>{flagEmoji(c.iso)}</span>
      <span className="truncate">{nameOf(c)}</span>
    </span>
  )
}
