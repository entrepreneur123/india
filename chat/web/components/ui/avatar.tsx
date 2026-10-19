'use client'

import { useState } from 'react'
import { cn } from '@/lib/utils'
import { flagEmoji } from '@/lib/format'

interface AvatarProps {
  name: string
  src?: string | null
  size?: number
  country?: string | null
  online?: boolean
  className?: string
}

export function Avatar({ name, src, size = 40, country, online, className }: AvatarProps) {
  const [failed, setFailed] = useState<string | null>(null) // the src that failed; a new photo gets a new try
  const initials = name
    .split(/\s+/)
    .map((w) => w[0])
    .join('')
    .slice(0, 2)
    .toUpperCase()
  const showImage = src && failed !== src
  const badge = Math.max(14, Math.round(size * 0.36))

  return (
    <span className={cn('relative inline-flex shrink-0', className)} style={{ width: size, height: size }}>
      <span className="block size-full overflow-hidden rounded-full bg-brand">
        {showImage ? (
          <img
            src={src}
            alt=""
            className="size-full object-cover"
            loading="lazy"
            draggable={false}
            onError={() => setFailed(src)}
          />
        ) : (
          <span
            className="grid size-full place-items-center font-bold text-primary-foreground"
            style={{ fontSize: Math.max(11, size * 0.38) }}
            aria-hidden
          >
            {initials}
          </span>
        )}
      </span>
      {country ? (
        <span
          className="absolute -right-0.5 -bottom-0.5 grid place-items-center rounded-full bg-card leading-none ring-2 ring-card"
          style={{ width: badge, height: badge, fontSize: badge * 0.72 }}
          aria-hidden
        >
          {flagEmoji(country)}
        </span>
      ) : null}
      {online ? (
        <span className="absolute top-0 right-0 flex size-3" aria-hidden>
          <span className="absolute inline-flex size-full animate-ping-soft rounded-full bg-online" />
          <span className="relative inline-flex size-3 rounded-full bg-online ring-2 ring-card" />
        </span>
      ) : null}
      <span className="sr-only">{name}</span>
    </span>
  )
}
