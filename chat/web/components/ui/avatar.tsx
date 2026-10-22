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

// Letter avatars pick one of these by name; each keeps white text at ≥ 4.5:1.
const TONES = ['#c8174a', '#1a73e8', '#188038', '#a142f4', '#c5221f', '#007b83', '#b06000', '#5f6368']

function tone(name: string) {
  let h = 0
  for (const ch of name) h = (h * 31 + ch.codePointAt(0)!) | 0
  return TONES[Math.abs(h) % TONES.length]
}

export function Avatar({ name, src, size = 40, country, online, className }: AvatarProps) {
  const [failed, setFailed] = useState<string | null>(null) // the src that failed; a new photo gets a new try
  const initials = [...name.trim()]
    .slice(0, 1)
    .join('')
    .toUpperCase()
  const showImage = src && failed !== src
  const badge = Math.max(14, Math.round(size * 0.36))

  return (
    <span className={cn('relative inline-flex shrink-0', className)} style={{ width: size, height: size }}>
      <span className="block size-full overflow-hidden rounded-full" style={showImage ? undefined : { background: tone(name) }}>
        {showImage ? (
          <img
            src={src}
            alt=""
            width={size}
            height={size}
            className="size-full bg-muted object-cover"
            loading="lazy"
            decoding="async"
            draggable={false}
            onError={() => setFailed(src)}
          />
        ) : (
          <span
            className="grid size-full place-items-center font-semibold text-white"
            style={{ fontSize: Math.max(11, size * 0.42) }}
            aria-hidden
          >
            {initials}
          </span>
        )}
      </span>
      {country ? (
        <span
          className="absolute -inset-e-0.5 -bottom-0.5 grid place-items-center rounded-full bg-card leading-none ring-2 ring-card"
          style={{ width: badge, height: badge, fontSize: badge * 0.72 }}
          aria-hidden
        >
          {flagEmoji(country)}
        </span>
      ) : null}
      {online ? (
        <span className="absolute top-0 inset-e-0 flex size-3" aria-hidden>
          <span className="relative inline-flex size-3 rounded-full bg-online ring-2 ring-card" />
        </span>
      ) : null}
      <span className="sr-only">{name}</span>
    </span>
  )
}
