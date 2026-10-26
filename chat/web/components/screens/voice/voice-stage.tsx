'use client'

import { AnimatePresence, useReducedMotion } from 'motion/react'
import * as m from 'motion/react-m'
import { useEffect, useRef } from 'react'
import { Lock, Maximize2, MicOff, Minimize2, Plus, WifiOff } from 'lucide-react'
import { VOICE_SEATS, type VoiceMember, type VoiceRoom, type VoiceVideo } from '@/lib/backend'
import { useI18n } from '@/components/i18n'
import { ReactionIcon } from '@/components/ui/topic-icons'
import { cn } from '@/lib/utils'
import { flagEmoji } from '@/lib/format'
import { useLevel, type LevelStore, type Reaction } from './voice-session'
import { roleLabel, SPEAKING } from './voice-text'
import { useRoomT } from './room-t'
import { TrustBadge } from '@/components/trust/badge'
import { UserAvatar } from '@/components/looks/user-avatar'

/** Three bars that bounce while a room is live (still under reduced motion). */
export function LiveBars({ className }: { className?: string }) {
  return (
    <span className={cn('inline-flex h-3 items-end gap-[2px]', className)} aria-hidden>
      {[0, 0.25, 0.5].map((d) => (
        <span key={d} className="h-full w-[3px] origin-bottom animate-eq rounded-full bg-primary-text" style={{ animationDelay: `${-d}s` }} />
      ))}
    </span>
  )
}

/**
 * The ring around someone talking: it grows and brightens with their audio level (X Spaces / party apps).
 * With reduced motion it is a plain ring that shows while they speak.
 */
export function SpeakingRing({ store, id }: { store: LevelStore; id: string }) {
  const level = useLevel(store, id)
  const reduce = useReducedMotion()
  const on = level > SPEAKING
  return (
    <>
      {reduce ? null : (
        <span
          aria-hidden
          className="pointer-events-none absolute -inset-1 rounded-full bg-primary-text/25 transition-[opacity,transform] duration-150 ease-out"
          style={{ opacity: on ? level : 0, transform: `scale(${on ? 1.06 + level * 0.16 : 1})` }}
        />
      )}
      <span
        aria-hidden
        className="pointer-events-none absolute -inset-[3px] rounded-full border-2 border-primary-text transition-[opacity,transform] duration-150 ease-out"
        style={{ opacity: on ? (reduce ? 1 : 0.55 + level * 0.45) : 0, transform: reduce ? undefined : `scale(${on ? 1 + level * 0.05 : 0.96})` }}
      />
    </>
  )
}

/** A face whose connection dropped (045) is dimmed with this badge; the server takes them out 7 s later unless they're back. */
export const LOST_FACE = 'opacity-40 grayscale'
export function LostBadge({ size, className }: { size: number; className?: string }) {
  return (
    <span
      aria-hidden
      className={cn('absolute -top-0.5 -inset-e-0.5 grid place-items-center rounded-full bg-destructive text-background ring-2 ring-background', className)}
      style={{ width: size, height: size }}
    >
      <WifiOff style={{ width: size * 0.58, height: size * 0.58 }} />
    </span>
  )
}

/**
 * The cameras on the stage (24), above the seats: one tile, or three across (eight seats fit in three rows); a tile lights up while its
 * person talks and opens their profile; its corner button (47) makes it big in the room's main area, like Discord. My
 * own is mirrored, as in a mirror.
 */
export function VideoGrid({
  room,
  videos,
  levels,
  onPerson,
  onExpand,
}: {
  room: VoiceRoom
  videos: Record<string, VoiceVideo>
  levels: LevelStore
  onPerson: (userId: string) => void
  onExpand: (userId: string) => void
}) {
  const { t } = useI18n()
  const people = room.members.filter((x) => x.seat !== null && videos[x.profile.id]).sort((a, b) => a.seat! - b.seat!)
  if (!people.length) return null
  return (
    <ul aria-label={t('voice.stage')} className={cn('mx-auto grid w-full gap-1.5 px-1 pt-2 pb-1', people.length === 1 ? 'max-w-56' : people.length === 2 ? 'max-w-md grid-cols-2' : 'max-w-lg grid-cols-3')}>
      {people.map((x) => (
        <li key={x.profile.id}>
          <VideoTile member={x} video={videos[x.profile.id]} levels={levels} onClick={() => onPerson(x.profile.id)} onExpand={() => onExpand(x.profile.id)} />
        </li>
      ))}
    </ul>
  )
}

/** A camera's picture in a <video> (sound comes from the room's audio, so the picture stays muted). */
function useVideo(video: VoiceVideo) {
  const ref = useRef<HTMLVideoElement>(null)
  useEffect(() => {
    const el = ref.current!
    video.attach(el)
    return () => video.detach(el)
  }, [video])
  return ref
}

/** The name (and a muted mic) over a picture's corner. */
function NameChip({ member, className }: { member: VoiceMember; className?: string }) {
  return (
    <span className={cn('absolute start-1.5 bottom-1.5 flex max-w-[calc(100%-0.75rem)] items-center gap-1 rounded-full bg-black/55 px-2 py-0.5 text-xs font-semibold text-white', className)}>
      <span className="truncate">{member.profile.name}</span>
      {member.muted ? <MicOff className="size-3.5 shrink-0" aria-hidden /> : null}
    </span>
  )
}

function VideoTile({ member, video, levels, onClick, onExpand }: { member: VoiceMember; video: VoiceVideo; levels: LevelStore; onClick: () => void; onExpand: () => void }) {
  const { rt } = useRoomT()
  const ref = useVideo(video)
  const talking = useLevel(levels, member.profile.id) > SPEAKING
  return (
    <div className={cn('relative aspect-[4/3] w-full overflow-hidden rounded-2xl bg-black ring-2 transition-shadow duration-150', talking ? 'ring-primary-text' : 'ring-transparent')}>
      <button type="button" onClick={onClick} aria-label={member.profile.name} className="block size-full">
        <video ref={ref} autoPlay playsInline muted className={cn('size-full object-cover', video.mine && '-scale-x-100')} />
      </button>
      <NameChip member={member} />
      <button
        type="button"
        onClick={onExpand}
        aria-label={rt('videoBig', { name: member.profile.name })}
        title={rt('videoBig', { name: member.profile.name })}
        className="absolute top-1.5 inset-e-1.5 grid size-8 place-items-center rounded-full bg-black/55 text-white transition-colors hover:bg-black/75"
      >
        <Maximize2 className="size-4" aria-hidden />
      </button>
    </div>
  )
}

/** 47: one camera filling the room's main area (the chat stays beside it); its button, or Esc, makes it small again. */
export function FocusVideo({ member, video, levels, onClose, className }: { member: VoiceMember; video: VoiceVideo; levels: LevelStore; onClose: () => void; className?: string }) {
  const { rt } = useRoomT()
  const ref = useVideo(video)
  const talking = useLevel(levels, member.profile.id) > SPEAKING
  useEffect(() => {
    const esc = (e: KeyboardEvent) => e.key === 'Escape' && !document.querySelector('[role=dialog]') && onClose()
    document.addEventListener('keydown', esc)
    return () => document.removeEventListener('keydown', esc)
  }, [onClose])
  return (
    <div className={cn('relative overflow-hidden bg-black ring-2 ring-inset transition-shadow duration-150', talking ? 'ring-primary-text' : 'ring-transparent', className)}>
      <video ref={ref} autoPlay playsInline muted className={cn('size-full object-contain', video.mine && '-scale-x-100')} />
      <NameChip member={member} className="start-3 bottom-3 px-3 py-1 text-sm" />
      <button
        type="button"
        onClick={onClose}
        aria-label={rt('videoSmall')}
        title={rt('videoSmall')}
        className="absolute top-3 inset-e-3 grid size-10 place-items-center rounded-full bg-black/55 text-white transition-colors hover:bg-black/75"
      >
        <Minimize2 className="size-5" aria-hidden />
      </button>
    </div>
  )
}

export type StageSize = 'sm' | 'md' | 'lg'
const HOST_PX: Record<StageSize, number> = { sm: 56, md: 72, lg: 96 }
const SEAT_PX: Record<StageSize, number> = { sm: 44, md: 54, lg: 72 }

export function Stage({
  room,
  levels,
  reactions,
  layer = true,
  size,
  canManage,
  onPerson,
  onEmpty,
}: {
  room: VoiceRoom
  levels: LevelStore
  reactions: Reaction[]
  /** False when the screen floats the reactions itself (wide rooms). */
  layer?: boolean
  size: StageSize
  canManage: boolean
  onPerson: (userId: string) => void
  onEmpty: (seat: number) => void
}) {
  const { t } = useI18n()
  const bySeat = new Map(room.members.filter((x) => x.seat !== null).map((x) => [x.seat!, x]))
  // The newest reaction of each person, shown on their seat while it floats.
  const latest = new Map(reactions.map((r) => [r.userId, r]))
  const host = bySeat.get(0) ?? null

  return (
    <section aria-label={t('voice.stage')} className="relative px-4 pt-3 pb-4">
      <div className="flex justify-center">
        {host ? (
          <SeatPerson member={host} px={HOST_PX[size]} levels={levels} reaction={latest.get(host.profile.id)} onClick={() => onPerson(host.profile.id)} wide />
        ) : null}
      </div>
      <ol className={cn('mx-auto mt-4 grid max-w-xl grid-cols-4 gap-x-2', size === 'sm' ? 'gap-y-3' : 'gap-y-5')}>
        {Array.from({ length: VOICE_SEATS - 1 }, (_, i) => i + 1).map((seat) => {
          const who = bySeat.get(seat)
          return (
            <li key={seat} className="flex justify-center">
              {who ? (
                <SeatPerson member={who} px={SEAT_PX[size]} levels={levels} reaction={latest.get(who.profile.id)} onClick={() => onPerson(who.profile.id)} />
              ) : (
                <EmptySeat seat={seat} px={SEAT_PX[size]} locked={room.seats[seat]?.locked ?? false} canManage={canManage} onClick={() => onEmpty(seat)} />
              )}
            </li>
          )
        })}
      </ol>
      {layer ? <ReactionLayer reactions={reactions} /> : null}
    </section>
  )
}

export function SeatPerson({
  member,
  px,
  levels,
  reaction,
  onClick,
  wide,
}: {
  member: VoiceMember
  px: number
  levels: LevelStore
  reaction?: Reaction
  onClick: () => void
  wide?: boolean
}) {
  const { t } = useI18n()
  const { rt } = useRoomT()
  const role = roleLabel(member, t)
  const label = [member.profile.name, role, member.lost ? rt('lost') : member.muted ? t('voice.micOff') : null].filter(Boolean).join(', ')
  const badge = Math.max(18, Math.round(px * 0.34))
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      className={cn('group flex min-w-0 flex-col items-center gap-1.5 rounded-2xl p-1 outline-offset-0', wide ? 'w-32' : 'w-full')}
    >
      <span className="relative" style={{ width: px, height: px }}>
        {member.lost ? null : <SpeakingRing store={levels} id={member.profile.id} />}
        <UserAvatar userId={member.profile.id} name={member.profile.name} src={member.profile.photoUrl} size={px} className={cn('transition-transform duration-150 group-active:scale-95', member.lost && LOST_FACE)} />
        {member.lost ? <LostBadge size={badge} /> : null}
        {member.muted ? (
          <span
            className="absolute -inset-e-0.5 -bottom-0.5 grid place-items-center rounded-full bg-muted text-foreground ring-2 ring-background"
            style={{ width: badge, height: badge }}
          >
            <MicOff style={{ width: badge * 0.56, height: badge * 0.56 }} aria-hidden />
          </span>
        ) : null}
        <AnimatePresence>
          {reaction ? (
            <m.span
              key={reaction.key}
              className="absolute -top-1 -inset-e-1.5 grid place-items-center rounded-full bg-card shadow-e2 ring-2 ring-background"
              style={{ width: badge + 4, height: badge + 4 }}
              initial={{ opacity: 0, scale: 0.4 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.6 }}
              transition={{ type: 'spring', stiffness: 500, damping: 26 }}
            >
              <ReactionIcon kind={reaction.kind} size={Math.round(badge * 0.78)} />
            </m.span>
          ) : null}
        </AnimatePresence>
      </span>
      <span className="flex max-w-full min-w-0 flex-col items-center">
        <span className={cn('flex max-w-full min-w-0 items-center gap-0.5 font-semibold leading-tight', wide ? 'text-sm' : 'text-xs')}>
          <span className="truncate">{member.profile.name}</span>
          <TrustBadge userId={member.profile.id} size={wide ? 14 : 12} />
          {member.role === 'host' && member.profile.country ? <span aria-hidden className="ms-0.5">{flagEmoji(member.profile.country)}</span> : null}
        </span>
        {member.lost ? (
          <span className="mt-0.5 text-[10px] leading-tight font-bold whitespace-nowrap text-destructive">{rt('lost')}</span>
        ) : role ? (
          <span className="mt-0.5 rounded-full bg-primary-soft px-1.5 py-px text-[10px] leading-tight font-bold whitespace-nowrap text-primary-text">{role}</span>
        ) : null}
      </span>
    </button>
  )
}

function EmptySeat({ seat, px, locked, canManage, onClick }: { seat: number; px: number; locked: boolean; canManage: boolean; onClick: () => void }) {
  const { t } = useI18n()
  const label = locked ? t('voice.seatLocked', { n: seat }) : t('voice.seatEmpty', { n: seat })
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      aria-disabled={locked && !canManage}
      className={cn('group flex w-full min-w-0 flex-col items-center gap-1.5 rounded-2xl p-1', locked && !canManage && 'cursor-default')}
    >
      <span
        className={cn(
          'grid place-items-center rounded-full text-muted-foreground transition-colors duration-150',
          locked ? 'bg-muted/60' : 'border-[1.5px] border-dashed border-input group-hover:border-foreground/40 group-hover:text-foreground',
        )}
        style={{ width: px, height: px }}
      >
        {locked ? <Lock className="size-[38%]" aria-hidden /> : <Plus className="size-[42%]" aria-hidden />}
      </span>
      <span className="text-[11px] leading-tight font-medium text-muted-foreground tabular-nums">{t('voice.seat', { n: seat })}</span>
    </button>
  )
}

/** Reactions float up over the stage (X Spaces); under reduced motion they only fade in and out in place. */
export function ReactionLayer({ reactions }: { reactions: Reaction[] }) {
  const reduce = useReducedMotion()
  return (
    <div className="pointer-events-none absolute inset-0 overflow-hidden" aria-hidden>
      <AnimatePresence>
        {reactions.map((r) => (
          <m.span
            key={r.key}
            className="absolute bottom-2"
            style={{ left: `${r.x * 100}%` }}
            initial={{ opacity: 0, y: 0, scale: 0.6 }}
            animate={
              reduce
                ? { opacity: [0, 1, 1, 0], transition: { duration: 2.4, times: [0, 0.1, 0.8, 1] } }
                : { opacity: [0, 1, 1, 0], y: -220, scale: [0.6, 1.15, 1, 0.9], x: [0, 10, -8, 6], transition: { duration: 2.4, ease: 'easeOut', times: [0, 0.15, 0.7, 1] } }
            }
            exit={{ opacity: 0, transition: { duration: 0.1 } }}
          >
            <ReactionIcon kind={r.kind} size={30} />
          </m.span>
        ))}
      </AnimatePresence>
    </div>
  )
}
