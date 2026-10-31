'use client'

import useSWR from 'swr'
import { Lock, Plus, Users } from 'lucide-react'
import type { RoomQuery, VoiceRoomSummary } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { EmptyState } from '@/components/ui/ambient'
import { Skeleton } from '@/components/ui/bits'
import { Button } from '@/components/ui/pressable'
import { TopicIcon } from '@/components/ui/topic-icons'
import { refusalLabel, roomConditions } from '@/components/screens/rooms/room-text'
import { RoomCountry } from '@/components/screens/rooms/room-country'
import { useRoomPreview } from '@/components/screens/rooms/room-preview'
import { cn } from '@/lib/utils'
import { flagEmoji } from '@/lib/format'
import { langName } from '@/components/social/room-lang'
import { useSocialT } from '@/components/social/social-text'
import { LiveBars } from './voice-stage'
import { UserAvatar } from '@/components/looks/user-avatar'

/** The live rooms on the Rooms tab, narrowed by its search. */
export function VoiceRooms({ query = {}, onCreate, onTag }: { query?: RoomQuery; onCreate: () => void; onTag?: (tag: string) => void }) {
  const backend = useBackend()
  const { t } = useI18n()
  const { voiceRoomId } = useApp()
  const preview = useRoomPreview()
  const { st } = useSocialT()
  const searching = !!(query.text || query.lang || query.countries?.length || query.locked === false || (query.sort && query.sort !== 'members'))
  // Without a search this is the same list (and request) as the right panel's.
  const key = searching ? ['voiceRooms', query.text ?? '', query.lang ?? '', query.countries?.join() ?? '', query.locked ?? true, query.sort ?? 'members'] : 'voiceRooms'
  const { data: rooms, error, mutate } = useSWR(key, () => backend.voiceRooms(searching ? query : undefined), {
    keepPreviousData: true,
    // The list is a server summary rebuilt every 5 s: every 30 s while the tab is shown is plenty, never hidden.
    refreshInterval: 30_000, refreshWhenHidden: false, focusThrottleInterval: 30_000, dedupingInterval: 5_000,
  })

  if (error && !rooms)
    return (
      <EmptyState
        art="rooms"
        title={t('rooms.loadFailed')}
        body={t('common.checkConnection')}
        action={<Button variant="outline" onClick={() => void mutate()}>{t('common.tryAgain')}</Button>}
      />
    )

  return (
    <section aria-labelledby="voice-live" className="flex flex-col gap-3">
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <h2 id="voice-live" className="flex items-center gap-2 text-lg font-semibold">
          <LiveBars /> {t('voice.liveNow')}
        </h2>
        <span className="text-xs text-muted-foreground">{t('voice.liveHint')}</span>
      </div>
      {!rooms ? (
        <div className="grid grid-cols-2 gap-2 sm:gap-3" aria-busy>
          {Array.from({ length: 4 }, (_, i) => (
            <Skeleton key={i} className="h-52 rounded-2xl" />
          ))}
        </div>
      ) : rooms.length === 0 && searching ? (
        <EmptyState
          art="rooms"
          title={st('rooms.noMatch')}
          body={st('rooms.noMatchBody')}
          action={
            <Button variant="brand" onClick={onCreate}>
              <Plus className="size-4.5" aria-hidden /> {t('rooms.create')}
            </Button>
          }
        />
      ) : rooms.length === 0 ? (
        <EmptyState
          art="rooms"
          title={t('rooms.noneTitle')}
          body={t('rooms.noneBody')}
          action={
            <Button variant="brand" onClick={onCreate}>
              <Plus className="size-4.5" aria-hidden /> {t('rooms.create')}
            </Button>
          }
        />
      ) : (
        <ul className="grid grid-cols-2 gap-2 sm:gap-3">
          {rooms.map((r) => (
            <li key={r.id}>
              <RoomCard room={r} here={r.id === voiceRoomId} onOpen={() => preview.open(r)} onTag={onTag} />
            </li>
          ))}
        </ul>
      )}
      {preview.sheet}
    </section>
  )
}

/** A room in the list; two to a row on phones too (the owner, 2026-10-30), smaller there. */
function RoomCard({ room, here, onOpen, onTag }: { room: VoiceRoomSummary; here: boolean; onOpen: () => void; onTag?: (tag: string) => void }) {
  const { t, num } = useI18n()
  const live = room.speakers.length > 0
  // A password or a private room's code isn't a no: the card opens the prompt (058, 065).
  const locked = (room.refusal === 'room_password' || room.refusal === 'room_private') && !here
  const blocked = !!room.refusal && !locked && !here
  // The rule that keeps me out is shown once, in red, not again among the others.
  const conditions = roomConditions(room, t, false).filter((c) => !blocked || c !== refusalLabel(room, t))
  // The whole card opens the room (a button stretched under it); the hashtags on top search for themselves (072).
  return (
    <div
      className={cn(
        'relative flex h-full w-full flex-col gap-2 overflow-hidden rounded-2xl border bg-card p-3 text-start sm:gap-3 sm:p-4 transition-[box-shadow,background-color] duration-150',
        blocked ? 'opacity-60' : 'hover:shadow-e2 has-[>button:active]:bg-muted',
        here || room.mine ? 'border-primary' : 'border-border',
      )}
    >
      <button type="button" onClick={onOpen} aria-disabled={blocked} aria-label={room.title} className="absolute inset-0 rounded-2xl" />
      {room.coverUrl ? (
        <img src={room.coverUrl} alt="" className="pointer-events-none -mx-3 -mt-3 aspect-[16/9] w-[calc(100%+1.5rem)] max-w-none object-cover sm:-mx-4 sm:-mt-4 sm:aspect-[16/7] sm:w-[calc(100%+2rem)]" loading="lazy" decoding="async" />
      ) : null}
      <span className="flex w-full items-center justify-between gap-3">
        <span className="flex min-w-0 items-center gap-1.5">
          <RoomCountry iso={room.host.country} />
          {room.isPrivate || locked ? <Lock className="size-3.5 shrink-0 text-muted-foreground" aria-label={t(room.isPrivate ? 'rooms.private' : 'rooms.locked')} /> : null}
          {langName(room.lang) ? (
            <span lang={room.lang ?? undefined} className="hidden truncate rounded-full bg-muted px-2 py-0.5 text-[11px] font-semibold text-muted-foreground sm:inline">
              {langName(room.lang)}
            </span>
          ) : null}
        </span>
        {live || here ? (
          <span className="flex shrink-0 items-center gap-1.5 text-xs font-bold text-primary-text">
            <LiveBars /> {here ? t('voice.inRoom') : t('voice.live')}
          </span>
        ) : null}
      </span>
      <span className="line-clamp-2 text-[15px] leading-snug font-bold tracking-tight sm:text-[17px]">{room.title}</span>
      {room.tags.length ? (
        <span className="-mt-2 flex flex-wrap gap-x-2">
          {room.tags.map((tag) => (
            <button
              key={tag}
              type="button"
              disabled={!onTag}
              onClick={() => onTag?.(tag)}
              // 24 px tall (WCAG 2.5.8) without growing into the card's own tap area.
              className="relative z-10 py-0.5 text-[13px] font-semibold text-primary-text enabled:hover:underline disabled:pointer-events-none"
            >
              #{tag}
            </button>
          ))}
        </span>
      ) : null}
      {live ? (
        <span className="flex min-w-0 items-center gap-2.5">
          <span className="flex shrink-0 -space-x-2" aria-hidden>
            {room.speakers.map((p) => (
              <UserAvatar key={p.id} userId={p.id} name={p.name} src={p.photoUrl} size={26} className="rounded-full ring-2 ring-card" />
            ))}
          </span>
          <span className="hidden min-w-0 truncate text-sm text-muted-foreground sm:inline">{room.speakers.map((p) => p.name).join(', ')}</span>
        </span>
      ) : (
        <span className="flex min-w-0 items-center gap-2 text-sm text-muted-foreground">
          <TopicIcon topic="chat" size={18} />
          <span className="truncate">{t('rooms.chatting')}</span>
        </span>
      )}
      {room.nowPlaying ? (
        <span className="-mt-1 flex min-w-0 items-center gap-1.5 text-xs font-medium text-foreground">
          <TopicIcon topic="music" size={15} />
          <span className="sr-only">{t('voice.nowPlayingLabel')}:</span>
          <span className="truncate">{room.nowPlaying}</span>
        </span>
      ) : null}
      {conditions.length || blocked ? (
        <span className="flex flex-wrap gap-1">
          {blocked ? (
            <span className="rounded-md bg-destructive/10 px-2 py-0.5 text-[11px] font-semibold text-destructive">{refusalLabel(room, t)}</span>
          ) : null}
          {conditions.map((c) => (
            <span key={c} className="rounded-md bg-muted px-2 py-0.5 text-[11px] font-medium text-muted-foreground">
              {c}
            </span>
          ))}
        </span>
      ) : null}
      <span className="mt-auto flex w-full items-center gap-2 border-t border-border pt-2 text-xs text-muted-foreground sm:pt-3">
        <UserAvatar userId={room.host.id} name={room.host.name} src={room.host.photoUrl} size={20} />
        <span className="hidden min-w-0 flex-1 truncate sm:inline">
          {room.mine ? t('voice.yourRoom') : t('voice.hostedBy', { name: room.host.name })}
          {room.mine ? null : <span aria-hidden>{` ${flagEmoji(room.host.country)}`}</span>}
          {room.mine && room.code ? <span className="font-mono font-semibold tracking-wider" dir="ltr">{` · ${room.code}`}</span> : null}
        </span>
        <span className="ms-auto flex shrink-0 items-center gap-1 tabular-nums">
          <Users className="size-3.5" aria-hidden /> {t('rooms.here', { n: num(room.members), max: num(room.maxMembers) })}
        </span>
      </span>
    </div>
  )
}
