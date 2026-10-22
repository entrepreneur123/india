'use client'

import { AnimatePresence, useReducedMotion } from 'motion/react'
import * as m from 'motion/react-m'
import { Lock, MicOff, Plus } from 'lucide-react'
import { VOICE_SEATS, type VoiceMember, type VoiceRoom } from '@/lib/backend'
import { useI18n } from '@/components/i18n'
import { Avatar } from '@/components/ui/avatar'
import { ReactionIcon } from '@/components/ui/topic-icons'
import { cn } from '@/lib/utils'
import { useLevel, type LevelStore, type Reaction } from './voice-session'
import { roleLabel, SPEAKING } from './voice-text'
import { TrustBadge } from '@/components/trust/badge'

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

export type StageSize = 'sm' | 'md' | 'lg'
const HOST_PX: Record<StageSize, number> = { sm: 56, md: 72, lg: 96 }
const SEAT_PX: Record<StageSize, number> = { sm: 44, md: 54, lg: 72 }

export function Stage({
  room,
  levels,
  reactions,
  size,
  canManage,
  onPerson,
  onEmpty,
}: {
  room: VoiceRoom
  levels: LevelStore
  reactions: Reaction[]
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
      <ReactionLayer reactions={reactions} />
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
  const role = roleLabel(member, t)
  const label = [member.profile.name, role, member.muted ? t('voice.micOff') : null].filter(Boolean).join(', ')
  const badge = Math.max(18, Math.round(px * 0.34))
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      className={cn('group flex min-w-0 flex-col items-center gap-1.5 rounded-2xl p-1 outline-offset-0', wide ? 'w-32' : 'w-full')}
    >
      <span className="relative" style={{ width: px, height: px }}>
        <SpeakingRing store={levels} id={member.profile.id} />
        <Avatar name={member.profile.name} src={member.profile.photoUrl} size={px} className="transition-transform duration-150 group-active:scale-95" />
        {member.muted ? (
          <span
            className="absolute -right-0.5 -bottom-0.5 grid place-items-center rounded-full bg-muted text-foreground ring-2 ring-background"
            style={{ width: badge, height: badge }}
          >
            <MicOff style={{ width: badge * 0.56, height: badge * 0.56 }} aria-hidden />
          </span>
        ) : null}
        <AnimatePresence>
          {reaction ? (
            <m.span
              key={reaction.key}
              className="absolute -top-1 -right-1.5 grid place-items-center rounded-full bg-card shadow-e2 ring-2 ring-background"
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
        </span>
        {role ? (
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
