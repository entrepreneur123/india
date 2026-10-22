import type { CSSProperties, ReactNode } from 'react'
import type { RoomCategory, VoiceReaction } from '@/lib/backend'
import { cn } from '@/lib/utils'

// Yaari's own glyphs for room topics and reactions (no emoji: they render differently on every phone and look
// cheap next to the rest of the UI). 24×24, solid rounded shapes in two tones: the topic colour (var(--c)) and a
// light tint of it (var(--cl)) for the secondary part. Colours are CSS variables (globals.css), lighter at night.

/** A room category, plus the built-in rooms that have no category of their own. */
export type Topic = RoomCategory | 'globe' | 'memes'

const M = 'var(--c)'
const L = 'var(--cl)'
/** Features drawn on a face (eyes, mouth). */
const INK = 'color-mix(in srgb, var(--c) 30%, #3d2400)'

const HAND =
  'M7.5 9.6V5.1a1.75 1.75 0 0 1 3.5 0V3.9a1.75 1.75 0 0 1 3.5 0v1.3a1.75 1.75 0 0 1 3.5 0V15a6 6 0 0 1-6 6h-.6a6 6 0 0 1-4.75-2.33L3.4 14.6a1.6 1.6 0 0 1 2.42-2.1L7.5 14z'

const LAUGH = (
  <>
    <circle cx="12" cy="12" r="9.5" fill={M} />
    <path d="M6.4 9.6c.6-1.1 2.4-1.1 3 0M14.6 9.6c.6-1.1 2.4-1.1 3 0" fill="none" stroke={INK} strokeWidth="1.8" strokeLinecap="round" />
    <path d="M6.9 12.6h10.2a5.1 5.1 0 0 1-10.2 0z" fill={INK} />
    <path d="M9.3 16.2a4 4 0 0 1 5.4 0 5.1 5.1 0 0 1-5.4 0z" fill={L} />
  </>
)

const TOPIC_GLYPHS: Record<Topic, ReactNode> = {
  chat: (
    <>
      <path
        d="M2.5 6.5A3.5 3.5 0 0 1 6 3h12a3.5 3.5 0 0 1 3.5 3.5v7A3.5 3.5 0 0 1 18 17h-6.2l-4.3 3.4c-.66.52-1.62.05-1.62-.79V17H6a3.5 3.5 0 0 1-3.5-3.5z"
        fill={M}
      />
      <circle cx="7.8" cy="10" r="1.45" fill={L} />
      <circle cx="12" cy="10" r="1.45" fill={L} />
      <circle cx="16.2" cy="10" r="1.45" fill={L} />
    </>
  ),
  globe: (
    <>
      <circle cx="12" cy="12" r="9.5" fill={M} />
      <path
        d="M2.9 12h18.2M12 2.6c2.5 2.6 3.8 5.7 3.8 9.4s-1.3 6.8-3.8 9.4c-2.5-2.6-3.8-5.7-3.8-9.4S9.5 5.2 12 2.6zM4.6 7.2h14.8M4.6 16.8h14.8"
        fill="none"
        stroke={L}
        strokeWidth="1.6"
        strokeLinecap="round"
      />
    </>
  ),
  friends: (
    <>
      <circle cx="16.4" cy="7.4" r="2.9" fill={L} />
      <path d="M11 18.6a5.4 5.4 0 0 1 10.8 0c0 .55-.45 1-1 1h-8.8c-.55 0-1-.45-1-1z" fill={L} />
      <circle cx="8.6" cy="8.2" r="3.4" fill={M} />
      <path d="M2.2 19.4a6.4 6.4 0 0 1 12.8 0c0 .6-.5 1.1-1.1 1.1H3.3c-.6 0-1.1-.5-1.1-1.1z" fill={M} />
    </>
  ),
  language: (
    <>
      <path d="M10 5.8A3.3 3.3 0 0 1 13.3 2.5h5.4A3.3 3.3 0 0 1 22 5.8v3.4a3.3 3.3 0 0 1-2 3.03v1.86c0 .5-.6.77-.98.44L16.6 12.5h-3.3A3.3 3.3 0 0 1 10 9.2z" fill={L} />
      <path d="M2 11.3A3.3 3.3 0 0 1 5.3 8h7.4a3.3 3.3 0 0 1 3.3 3.3v4.4a3.3 3.3 0 0 1-3.3 3.3H8.6l-3.62 2.88c-.38.3-.98.03-.98-.46v-2.73a3.3 3.3 0 0 1-2-3.03z" fill={M} />
      <path d="M6.1 16.6 9 10.8l2.9 5.8M7.15 14.6h3.7" fill="none" stroke="#fff" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
    </>
  ),
  music: (
    <>
      <path d="M8.6 6.6 20.6 3.4v4.1L8.6 10.7z" fill={L} />
      <rect x="8.6" y="6.2" width="2.1" height="11.6" rx="1.05" fill={M} />
      <rect x="18.5" y="3.6" width="2.1" height="11.6" rx="1.05" fill={M} />
      <ellipse cx="6.9" cy="17.7" rx="3.4" ry="2.7" transform="rotate(-18 6.9 17.7)" fill={M} />
      <ellipse cx="16.8" cy="15.1" rx="3.4" ry="2.7" transform="rotate(-18 16.8 15.1)" fill={M} />
    </>
  ),
  movies: (
    <>
      <rect x="2.5" y="10" width="19" height="11" rx="2.6" fill={M} />
      <g transform="rotate(-13 3 9.6)">
        <rect x="2.6" y="4.9" width="19" height="4.3" rx="1.4" fill={L} />
        <path d="M7.2 4.9h2.9l-2.2 4.3H5zM13.3 4.9h2.9L14 9.2h-2.9z" fill={M} />
      </g>
      <path d="M10.3 13.2v4.6c0 .45.5.72.87.47l3.4-2.3a.57.57 0 0 0 0-.94l-3.4-2.3a.56.56 0 0 0-.87.47z" fill={L} />
    </>
  ),
  gaming: (
    <>
      <path
        d="M7.2 6h9.6c2.6 0 4.6 1.9 5 4.5l.85 5.2c.36 2.3-1.36 4.3-3.6 4.3-1.38 0-2.62-.8-3.2-2.05L15.2 16.6H8.8l-.65 1.35C7.57 19.2 6.33 20 4.95 20c-2.24 0-3.96-2-3.6-4.3l.85-5.2c.4-2.6 2.4-4.5 5-4.5z"
        fill={M}
      />
      <rect x="5.4" y="10.4" width="5.2" height="1.8" rx=".9" fill={L} />
      <rect x="7.1" y="8.7" width="1.8" height="5.2" rx=".9" fill={L} />
      <circle cx="16.1" cy="10" r="1.2" fill={L} />
      <circle cx="18.1" cy="12.6" r="1.2" fill={L} />
    </>
  ),
  sports: (
    <>
      <g transform="rotate(40 10 12)">
        <rect x="9" y="1.4" width="2" height="6.6" rx="1" fill={L} />
        <path d="M7.3 8.6c0-.6.45-1.05 1-1.05h3.4c.55 0 1 .45 1 1.05v11c0 1-.8 1.8-1.8 1.8H9.1c-1 0-1.8-.8-1.8-1.8z" fill={M} />
      </g>
      <circle cx="18.4" cy="18.2" r="3.3" fill={M} />
      <path d="M16.2 16a3.3 3.3 0 0 1 4.4 4.4" fill="none" stroke={L} strokeWidth="1.3" strokeLinecap="round" />
    </>
  ),
  study: (
    <>
      <path d="M12 6.4C10 4.9 7.4 4.2 4.2 4.4c-.7.05-1.2.6-1.2 1.3v11.5c0 .75.63 1.33 1.38 1.3 2.9-.15 5.4.55 7.62 2.1z" fill={M} />
      <path d="M12 6.4c2-1.5 4.6-2.2 7.8-2 .7.05 1.2.6 1.2 1.3v11.5c0 .75-.63 1.33-1.38 1.3-2.9-.15-5.4.55-7.62 2.1z" fill={L} />
    </>
  ),
  travel: (
    <g transform="rotate(45 12 12)">
      <path d="M10.6 9.4 2.4 14.2v2.3l8.2-2.3zM13.4 9.4l8.2 4.8v2.3l-8.2-2.3z" fill={M} />
      <path d="M10.6 17.4 7.3 19.6v1.7l3.3-.8zM13.4 17.4l3.3 2.2v1.7l-3.3-.8z" fill={L} />
      <rect x="10.4" y="1.8" width="3.2" height="19.6" rx="1.6" fill={M} />
    </g>
  ),
  night: (
    <>
      <path d="M10.93 4.2A8.6 8.6 0 1 0 19.63 14.52 7.4 7.4 0 0 1 10.93 4.2z" fill={M} />
      <path d="M17.6 3.2c.3 1.9 1 2.7 2.9 3-1.9.3-2.6 1.1-2.9 3-.3-1.9-1-2.7-2.9-3 1.9-.3 2.6-1.1 2.9-3z" fill={L} />
    </>
  ),
  memes: LAUGH,
}

const REACTION_GLYPHS: Record<VoiceReaction | 'hand', ReactNode> = {
  heart: (
    <>
      <path d="M12 20.8c-.3 0-.6-.1-.85-.3C6.3 16.7 2.8 13.6 2.8 9.6c0-2.75 2.1-4.9 4.8-4.9 1.75 0 3.3.9 4.4 2.35C13.1 5.6 14.65 4.7 16.4 4.7c2.7 0 4.8 2.15 4.8 4.9 0 4-3.5 7.1-8.35 10.9-.25.2-.55.3-.85.3z" fill={M} />
      <ellipse cx="7.4" cy="8.9" rx="1.9" ry="1.25" transform="rotate(-40 7.4 8.9)" fill={L} />
    </>
  ),
  laugh: LAUGH,
  fire: (
    <>
      <path d="M12.6 2.4c.6 3.2-1 5-2.7 6.8C8.3 10.9 6 13 6 16c0 3.4 2.7 6 6 6s6-2.6 6-6c0-2.9-1.4-5-2.7-6.4-.3 1.4-1 2.4-2 2.9.5-3.7-.4-7.4-2.7-10.1z" fill={M} />
      <path d="M12 22c-2 0-3.4-1.4-3.4-3.3 0-1.8 1.2-3 2.4-4.1.4 1 1 1.6 1.8 1.9 0-1 .3-2 .9-2.8 1.2 1.2 2 2.7 2 4.6 0 2.1-1.6 3.7-3.7 3.7z" fill={L} />
    </>
  ),
  clap: (
    <>
      <g transform="rotate(18 9.4 13)">
        <rect x="2.6" y="11.6" width="2.6" height="6.2" rx="1.3" fill={L} transform="rotate(-32 3.9 14.7)" />
        <rect x="5.4" y="5.6" width="7.4" height="15" rx="3.7" fill={L} />
      </g>
      <g transform="rotate(-18 14.6 13)">
        <rect x="18.8" y="11.6" width="2.6" height="6.2" rx="1.3" fill={M} transform="rotate(32 20.1 14.7)" />
        <rect x="11.2" y="5.6" width="7.4" height="15" rx="3.7" fill={M} />
      </g>
      <rect x="11.15" y=".8" width="1.7" height="3.2" rx=".85" fill={M} />
      <rect x="6.5" y="2.1" width="1.7" height="2.8" rx=".85" fill={M} transform="rotate(-35 7.35 3.5)" />
      <rect x="15.8" y="2.1" width="1.7" height="2.8" rx=".85" fill={M} transform="rotate(35 16.65 3.5)" />
    </>
  ),
  wow: (
    <>
      <path d="M10 3.4c.6 5.6 2.5 7.6 8.1 8.4-5.6.8-7.5 2.8-8.1 8.4-.6-5.6-2.5-7.6-8.1-8.4 5.6-.8 7.5-2.8 8.1-8.4z" fill={M} />
      <path d="M18.6 2.2c.28 1.75.95 2.5 2.7 2.8-1.75.3-2.42 1.05-2.7 2.8-.28-1.75-.95-2.5-2.7-2.8 1.75-.3 2.42-1.05 2.7-2.8z" fill={L} />
      <circle cx="19.2" cy="18.6" r="1.6" fill={L} />
    </>
  ),
  hand: (
    <>
      <path d={HAND} fill={M} transform="translate(1.6 0)" />
      <rect x="1.4" y="4.2" width="1.8" height="4" rx=".9" fill={L} transform="rotate(-28 2.3 6.2)" />
      <rect x="20.9" y="4.2" width="1.8" height="4" rx=".9" fill={L} transform="rotate(28 21.8 6.2)" />
    </>
  ),
}

const REACTION_COLORS: Record<VoiceReaction | 'hand', string> = {
  heart: '#F2416B',
  laugh: '#F7B500',
  fire: '#FF6A2B',
  clap: '#F59E0B',
  wow: '#8E5CF7',
  hand: '#F59E0B',
}

/** The topic's colour, for tinted tiles and chips. */
export const topicColor = (topic: Topic) => `var(--topic-${topic})`

function Glyph({ color, size, className, children }: { color: string; size: number; className?: string; children: ReactNode }) {
  return (
    <svg
      viewBox="0 0 24 24"
      width={size}
      height={size}
      aria-hidden
      focusable="false"
      className={cn('shrink-0', className)}
      style={{ '--c': color, '--cl': `color-mix(in srgb, ${color} 42%, #fff)` } as CSSProperties}
    >
      {children}
    </svg>
  )
}

export function TopicIcon({ topic, size = 20, className }: { topic: Topic; size?: number; className?: string }) {
  return (
    <Glyph color={topicColor(topic)} size={size} className={className}>
      {TOPIC_GLYPHS[topic]}
    </Glyph>
  )
}

export function ReactionIcon({ kind, size = 24, className }: { kind: VoiceReaction | 'hand'; size?: number; className?: string }) {
  return (
    <Glyph color={REACTION_COLORS[kind]} size={size} className={className}>
      {REACTION_GLYPHS[kind]}
    </Glyph>
  )
}

/** The icebreaker card's glyph: a speech bubble with a question mark, in the talk colour. */
export function IcebreakerIcon({ size = 20, className }: { size?: number; className?: string }) {
  return (
    <Glyph color={topicColor('chat')} size={size} className={className}>
      <path
        d="M2.5 6.5A3.5 3.5 0 0 1 6 3h12a3.5 3.5 0 0 1 3.5 3.5v7A3.5 3.5 0 0 1 18 17h-6.2l-4.3 3.4c-.66.52-1.62.05-1.62-.79V17H6a3.5 3.5 0 0 1-3.5-3.5z"
        fill={M}
      />
      <path d="M9.7 7.9a2.35 2.35 0 1 1 3.2 2.2c-.55.25-.9.75-.9 1.35v.35" fill="none" stroke={L} strokeWidth="1.9" strokeLinecap="round" />
      <circle cx="12" cy="14.3" r="1.15" fill={L} />
    </Glyph>
  )
}

/** A room poll's glyph: a card with three result bars, in the study violet (the poll cards' colour). */
export function PollIcon({ size = 20, className }: { size?: number; className?: string }) {
  return (
    <Glyph color={topicColor('study')} size={size} className={className}>
      <rect x="2.5" y="3" width="19" height="18" rx="4.2" fill={L} />
      <rect x="5.6" y="6.6" width="12.8" height="2.8" rx="1.4" fill={M} />
      <rect x="5.6" y="10.6" width="8.4" height="2.8" rx="1.4" fill={M} />
      <rect x="5.6" y="14.6" width="10.6" height="2.8" rx="1.4" fill={M} />
    </Glyph>
  )
}

/** The leading tile of a room: the glyph in full colour on a light wash of it (never a loud colour block). */
export function TopicTile({ topic, size = 44, className }: { topic: Topic; size?: number; className?: string }) {
  return (
    <span
      className={cn('grid shrink-0 place-items-center rounded-full bg-[color-mix(in_srgb,var(--t)_12%,var(--card))] night:bg-[color-mix(in_srgb,var(--t)_18%,var(--card))]', className)}
      style={{ width: size, height: size, '--t': topicColor(topic) } as CSSProperties}
      aria-hidden
    >
      <TopicIcon topic={topic} size={Math.round(size * 0.52)} />
    </span>
  )
}

/** A white rounded chip with the glyph and its label (Naver Map style category chips). */
export function TopicChip({ topic, label, className }: { topic: Topic; label: string; className?: string }) {
  return (
    <span
      className={cn(
        'inline-flex h-7 max-w-full items-center gap-1.5 rounded-full bg-card pe-3 ps-2 text-[12px] font-semibold text-foreground shadow-[0_1px_3px_rgb(0_0_0/0.12)] night:bg-[#26262c] night:shadow-none',
        className,
      )}
    >
      <TopicIcon topic={topic} size={16} />
      <span className="truncate">{label}</span>
    </span>
  )
}

const HEART_PATH =
  'M12 20.8c-.3 0-.6-.1-.85-.3C6.3 16.7 2.8 13.6 2.8 9.6c0-2.75 2.1-4.9 4.8-4.9 1.75 0 3.3.9 4.4 2.35C13.1 5.6 14.65 4.7 16.4 4.7c2.7 0 4.8 2.15 4.8 4.9 0 4-3.5 7.1-8.35 10.9-.25.2-.55.3-.85.3z'
const BUBBLE_PATH =
  'M12 3.2c5.1 0 9.2 3.6 9.2 8.1s-4.1 8.1-9.2 8.1c-1.2 0-2.4-.2-3.5-.6l-3.9 1.7c-.6.25-1.2-.3-1-.9l1-3.3C3.4 14.9 2.8 13.2 2.8 11.3c0-4.5 4.1-8.1 9.2-8.1z'

/**
 * A post's like and comment glyphs, in the same rounded shapes as the rest. The like is the reaction heart when on
 * (two tones, in the heart's red), an outline in the text colour when off; the comment is always an outline.
 */
export function LikeIcon({ on, size = 26, className }: { on: boolean; size?: number; className?: string }) {
  if (on) return <ReactionIcon kind="heart" size={size} className={className} />
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} aria-hidden focusable="false" className={cn('shrink-0', className)}>
      <path d={HEART_PATH} fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinejoin="round" />
    </svg>
  )
}

export function CommentIcon({ size = 26, className }: { size?: number; className?: string }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} aria-hidden focusable="false" className={cn('shrink-0', className)}>
      <path d={BUBBLE_PATH} fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinejoin="round" />
    </svg>
  )
}

/** The big heart that pops over a photo on a double tap (Instagram): two tones on a soft shadow. */
export function BigHeart({ size = 96, className }: { size?: number; className?: string }) {
  return (
    <Glyph color={REACTION_COLORS.heart} size={size} className={cn('drop-shadow-[0_6px_18px_rgb(0_0_0/0.35)]', className)}>
      <path d={HEART_PATH} fill="#fff" />
      <path d={HEART_PATH} fill={M} transform="translate(12 12.4) scale(.86) translate(-12 -12.4)" />
      <ellipse cx="7.9" cy="9.2" rx="1.7" ry="1.1" transform="rotate(-40 7.9 9.2)" fill={L} />
    </Glyph>
  )
}

/** The Feed tab: a post card with a heart on it, drawn like the navigation's line icons (stroke, current colour). */
export function FeedIcon({ className, strokeWidth = 2 }: { className?: string; strokeWidth?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" aria-hidden focusable="false" className={className}>
      <path d="M7 3h10" />
      <rect x="3.5" y="6" width="17" height="15" rx="4" />
      <path d="M12 17.1c-2.2-1.5-3.4-2.7-3.4-4.1 0-1 .8-1.8 1.8-1.8.7 0 1.2.35 1.6.9.4-.55.9-.9 1.6-.9 1 0 1.8.8 1.8 1.8 0 1.4-1.2 2.6-3.4 4.1z" fill="currentColor" strokeWidth={Math.max(1, strokeWidth - 0.6)} />
    </svg>
  )
}

/** A post's audience, small: the globe (everyone) or two people (friends), in their topic colours. */
export function AudienceIcon({ visibility, size = 14, className }: { visibility: 'public' | 'friends'; size?: number; className?: string }) {
  return <TopicIcon topic={visibility === 'public' ? 'globe' : 'friends'} size={size} className={className} />
}
