import type { CSSProperties, ReactNode } from 'react'
import { cn } from '@/lib/utils'

// Glyphs of identity and manners, drawn like the room glyphs (components/ui/topic-icons.tsx): rounded solid shapes
// in two tones, the colour (var(--c)) and a light tint of it (var(--cl)). No emoji.

const M = 'var(--c)'
const L = 'var(--cl)'

function Glyph({ color, size, className, children }: { color: string; size: number; className?: string; children: ReactNode }) {
  return (
    <svg
      viewBox="0 0 24 24"
      width={size}
      height={size}
      aria-hidden
      focusable="false"
      className={cn('shrink-0', className)}
      style={{ '--c': color, '--cl': `color-mix(in srgb, ${color} 40%, #fff)` } as CSSProperties}
    >
      {children}
    </svg>
  )
}

const BRAND = '#ff8a00'
const BRAND_DEEP = '#e67a00'

/** An ID card: the card, a photo and two lines. */
export function IdCardIcon({ size = 24, color = BRAND, className }: { size?: number; color?: string; className?: string }) {
  return (
    <Glyph color={color} size={size} className={className}>
      <rect x="2" y="4.5" width="20" height="15" rx="3.4" fill={M} />
      <circle cx="8" cy="10.6" r="2.3" fill={L} />
      <path d="M4.6 16.4c.5-1.8 1.8-2.8 3.4-2.8s2.9 1 3.4 2.8" fill={L} />
      <rect x="13.2" y="9" width="6" height="1.8" rx=".9" fill={L} />
      <rect x="13.2" y="12.6" width="4.2" height="1.8" rx=".9" fill={L} />
    </Glyph>
  )
}

/** A selfie: a face inside the camera's frame corners. */
export function SelfieIcon({ size = 24, color = BRAND, className }: { size?: number; color?: string; className?: string }) {
  return (
    <Glyph color={color} size={size} className={className}>
      <path d="M3 8V5.6A2.6 2.6 0 0 1 5.6 3H8M16 3h2.4A2.6 2.6 0 0 1 21 5.6V8M21 16v2.4a2.6 2.6 0 0 1-2.6 2.6H16M8 21H5.6A2.6 2.6 0 0 1 3 18.4V16" fill="none" stroke={L} strokeWidth="2" strokeLinecap="round" />
      <circle cx="12" cy="12" r="6" fill={M} />
      <circle cx="9.9" cy="11.2" r=".95" fill="#fff" />
      <circle cx="14.1" cy="11.2" r=".95" fill="#fff" />
      <path d="M9.7 13.6c1.3 1.2 3.3 1.2 4.6 0" fill="none" stroke="#fff" strokeWidth="1.4" strokeLinecap="round" />
    </Glyph>
  )
}

export function LockIcon({ size = 14, color = '#57534e', className }: { size?: number; color?: string; className?: string }) {
  return (
    <Glyph color={color} size={size} className={className}>
      <path d="M7.4 10.4V7.6a4.6 4.6 0 0 1 9.2 0v2.8" fill="none" stroke={L} strokeWidth="2.4" strokeLinecap="round" />
      <rect x="4.2" y="9.6" width="15.6" height="11.8" rx="3.4" fill={M} />
      <circle cx="12" cy="14.7" r="1.6" fill="#fff" />
      <rect x="11.2" y="15" width="1.6" height="3.4" rx=".8" fill="#fff" />
    </Glyph>
  )
}

export function ClockIcon({ size = 24, color = BRAND, className }: { size?: number; color?: string; className?: string }) {
  return (
    <Glyph color={color} size={size} className={className}>
      <circle cx="12" cy="12" r="9.5" fill={L} />
      <path d="M12 6.8V12l3.4 2.2" fill="none" stroke={M} strokeWidth="2.3" strokeLinecap="round" strokeLinejoin="round" />
    </Glyph>
  )
}

/** A fingerprint inside a shield: "what we keep". */
export function KeepIcon({ size = 24, color = BRAND, className }: { size?: number; color?: string; className?: string }) {
  return (
    <Glyph color={color} size={size} className={className}>
      <path d="M12 2.2c2.3 1.5 4.8 2.4 7.5 2.6.7.05 1.2.6 1.2 1.3v5c0 4.8-3.3 8.8-8.1 10.8-.4.15-.8.15-1.2 0C6.6 19.9 3.3 15.9 3.3 11.1v-5c0-.7.5-1.25 1.2-1.3 2.7-.2 5.2-1.1 7.5-2.6z" fill={M} />
      <path d="M8.4 12.2a3.6 3.6 0 0 1 7.2 0c0 1.8-.4 3.4-1 4.6M10.4 16.9c.6-1.3.9-2.9.9-4.6M12.7 12.2c0 2-.3 3.7-1 5.1M8.6 15.4c.3-.9.4-2 .4-3.2" fill="none" stroke={L} strokeWidth="1.45" strokeLinecap="round" />
    </Glyph>
  )
}

/** A face for a good chat (a smile) or one that wasn't (a frown), in green or red. */
export function ChatFeelIcon({ good, size = 40, className }: { good: boolean; size?: number; className?: string }) {
  const ink = good ? '#0c5a2f' : '#7a1216'
  return (
    <Glyph color={good ? '#22a35a' : '#e5484d'} size={size} className={className}>
      <circle cx="12" cy="12" r="9.6" fill={M} />
      <ellipse cx="7.2" cy="13.6" rx="1.7" ry="1.1" fill={L} opacity={good ? 1 : 0} />
      <ellipse cx="16.8" cy="13.6" rx="1.7" ry="1.1" fill={L} opacity={good ? 1 : 0} />
      {good ? (
        <>
          <path d="M7.4 9.6c.6-1 2-1 2.6 0M14 9.6c.6-1 2-1 2.6 0" fill="none" stroke={ink} strokeWidth="1.7" strokeLinecap="round" />
          <path d="M8.1 13.3h7.8a3.9 3.9 0 0 1-7.8 0z" fill={ink} />
        </>
      ) : (
        <>
          <circle cx="8.8" cy="10" r="1.25" fill={ink} />
          <circle cx="15.2" cy="10" r="1.25" fill={ink} />
          <path d="M8.4 16.6c2-2 5.2-2 7.2 0" fill="none" stroke={ink} strokeWidth="1.8" strokeLinecap="round" />
          <path d="M17.6 4.6c.9 1.4 1.4 2.4 1.4 3.2a1.4 1.4 0 0 1-2.8 0c0-.8.5-1.8 1.4-3.2z" fill={L} />
        </>
      )}
    </Glyph>
  )
}

/**
 * The verification sheet's picture: an ID card leaning back, a selfie frame and the verified shield in front, on a
 * soft blob of saffron. `tone` changes the shield for results that aren't a pass.
 */
export function VerifyHero({ tone = 'brand', size = 168, className }: { tone?: 'brand' | 'ok' | 'warn' | 'stop' | 'wait'; size?: number; className?: string }) {
  const shield = { brand: [BRAND, BRAND_DEEP], ok: ['#2fbf71', '#17894c'], warn: ['#f5a623', '#c77700'], stop: ['#ef5350', '#b3261e'], wait: ['#a8a29e', '#78716c'] }[tone]
  return (
    <svg viewBox="0 0 168 128" width={size} height={(size * 128) / 168} aria-hidden focusable="false" className={cn('shrink-0', className)}>
      <defs>
        <radialGradient id="vh-blob" cx="50%" cy="45%" r="60%">
          <stop offset="0" stopColor="#ffe8cc" />
          <stop offset="1" stopColor="#ffe8cc" stopOpacity="0" />
        </radialGradient>
      </defs>
      <ellipse cx="84" cy="66" rx="80" ry="60" fill="url(#vh-blob)" className="night:opacity-20" />
      <circle cx="30" cy="26" r="5" fill="#ffd199" />
      <circle cx="146" cy="96" r="4" fill="#d6d3d1" />
      <path d="M140 20c1 4.4 2.6 6 7 7-4.4 1-6 2.6-7 7-1-4.4-2.6-6-7-7 4.4-1 6-2.6 7-7z" fill={BRAND} />
      <g transform="rotate(-9 70 62)">
        <rect x="22" y="34" width="92" height="60" rx="12" fill="#111111" />
        <rect x="22" y="34" width="92" height="15" rx="7.5" fill="#2e2e2e" />
        <circle cx="47" cy="68" r="11" fill="#a8a29e" />
        <path d="M33 88c2.2-7 7.5-10.4 14-10.4S58.8 81 61 88" fill="#a8a29e" />
        <rect x="68" y="60" width="34" height="6" rx="3" fill="#a8a29e" />
        <rect x="68" y="72" width="24" height="6" rx="3" fill="#a8a29e" />
      </g>
      <g transform="translate(94 52)">
        <path d="M30 2c6 4 12.6 6.4 19.6 7 1.8.2 3.1 1.6 3.1 3.4v13.4c0 12.6-8.6 23-21.2 28.2-1 .4-2.1.4-3.1 0C15.8 48.8 7.2 38.4 7.2 25.8V12.4c0-1.8 1.4-3.2 3.1-3.4C17.4 8.4 24 6 30 2z" fill={shield[0]} />
        <path d="M30 2C24 6 17.4 8.4 10.3 9c-1.7.2-3.1 1.6-3.1 3.4v13.4c0 12.6 8.6 23 21.2 28.2.5.2 1 .3 1.6.3z" fill={shield[1]} />
        <ellipse cx="17" cy="15" rx="4.4" ry="2.7" transform="rotate(-32 17 15)" fill="#fff" opacity=".35" />
        {tone === 'stop' ? (
          <path d="M23.4 21.6l13.2 13.2M36.6 21.6 23.4 34.8" stroke="#fff" strokeWidth="5" strokeLinecap="round" />
        ) : tone === 'warn' ? (
          <>
            <path d="M30 18.6v10" stroke="#fff" strokeWidth="5" strokeLinecap="round" />
            <circle cx="30" cy="36.4" r="3" fill="#fff" />
          </>
        ) : tone === 'wait' ? (
          <>
            <circle cx="21.4" cy="28.4" r="3" fill="#fff" />
            <circle cx="30" cy="28.4" r="3" fill="#fff" opacity=".8" />
            <circle cx="38.6" cy="28.4" r="3" fill="#fff" opacity=".6" />
          </>
        ) : (
          <path d="M20.4 28.8l6.8 6.7 13-14.2" fill="none" stroke={tone === 'brand' ? '#111111' : '#fff'} strokeWidth="5.4" strokeLinecap="round" strokeLinejoin="round" />
        )}
      </g>
    </svg>
  )
}
