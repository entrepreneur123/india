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
import { useToast } from '@/components/ui/toast'
import { TopicIcon } from '@/components/ui/topic-icons'
import { refusalLabel, roomConditions } from '@/components/screens/rooms/room-text'
import { RoomCountry } from '@/components/screens/rooms/room-country'
import { cn } from '@/lib/utils'
import { flagEmoji } from '@/lib/format'
import { langName } from '@/components/social/room-lang'
import { useSocialT } from '@/components/social/social-text'
import { LiveBars } from './voice-stage'
import { UserAvatar } from '@/components/looks/user-avatar'

/** The live rooms on the Rooms tab, narrowed by its search. */
export function VoiceRooms({ query = {}, onCreate }: { query?: RoomQuery; onCreate: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { t } = useI18n()
  const { openVoice, voiceRoomId, askRoomPassword } = useApp()
  const { st } = useSocialT()
  const searching = !!(query.text || query.lang || query.countries?.length || query.locked === false)
  // Without a search this is the same list (and request) as the right panel's.
  const key = searching ? ['voiceRooms', query.text ?? '', query.lang ?? '', query.countries?.join() ?? '', query.locked ?? true] : 'voiceRooms'
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

  // A room I can't enter says why instead of failing on the server.
  const open = (r: VoiceRoomSummary) =>
    r.id === voiceRoomId || !r.refusal ? openVoice(r.id) : r.refusal === 'room_password' ? askRoomPassword(r.id) : toast.show(t(`error.${r.refusal}` as const), 'info')

  return (
    <section aria-labelledby="voice-live" className="flex flex-col gap-3">
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <h2 id="voice-live" className="flex items-center gap-2 text-lg font-semibold">
          <LiveBars /> {t('voice.liveNow')}
        </h2>
        <span className="text-xs text-muted-foreground">{t('voice.liveHint')}</span>
      </div>
      {!rooms ? (
        <div className="grid gap-3 sm:grid-cols-2" aria-busy>
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
        <ul className="grid gap-3 sm:grid-cols-2">
          {rooms.map((r) => (
            <li key={r.id}>
              <RoomCard room={r} here={r.id === voiceRoomId} onOpen={() => open(r)} />
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function RoomCard({ room, here, onOpen }: { room: VoiceRoomSummary; here: boolean; onOpen: () => void }) {
  const { t, num } = useI18n()
  const live = room.speakers.length > 0
  // A password isn't a no: the card opens the password prompt (058).
  const locked = room.refusal === 'room_password' && !here
  const blocked = !!room.refusal && !locked && !here
  const conditions = roomConditions(room, t, false)
  return (
    <button
      type="button"
      onClick={onOpen}
      aria-disabled={blocked}
      className={cn(
        'relative flex h-full w-full flex-col gap-3 overflow-hidden rounded-2xl border bg-card p-4 text-start transition-[box-shadow,background-color] duration-150',
        blocked ? 'opacity-60' : 'hover:shadow-e2 active:bg-muted',
        here || room.mine ? 'border-primary' : 'border-border',
      )}
    >
      <span className="flex w-full items-center justify-between gap-3">
        <span className="flex min-w-0 items-center gap-1.5">
          <RoomCountry iso={room.host.country} />
          {room.isPrivate || locked ? <Lock className="size-3.5 shrink-0 text-muted-foreground" aria-label={t(locked ? 'rooms.locked' : 'rooms.private')} /> : null}
          {langName(room.lang) ? (
            <span lang={room.lang ?? undefined} className="truncate rounded-full bg-muted px-2 py-0.5 text-[11px] font-semibold text-muted-foreground">
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
      <span className="line-clamp-2 text-[17px] leading-snug font-bold tracking-tight">{room.title}</span>
      {live ? (
        <span className="flex min-w-0 items-center gap-2.5">
          <span className="flex shrink-0 -space-x-2" aria-hidden>
            {room.speakers.map((p) => (
              <UserAvatar key={p.id} userId={p.id} name={p.name} src={p.photoUrl} size={30} className="rounded-full ring-2 ring-card" />
            ))}
          </span>
          <span className="min-w-0 truncate text-sm text-muted-foreground">{room.speakers.map((p) => p.name).join(', ')}</span>
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
      <span className="mt-auto flex w-full items-center gap-2 border-t border-border pt-3 text-xs text-muted-foreground">
        <UserAvatar userId={room.host.id} name={room.host.name} src={room.host.photoUrl} size={20} />
        <span className="min-w-0 flex-1 truncate">
          {room.mine ? t('voice.yourRoom') : t('voice.hostedBy', { name: room.host.name })}
          {room.mine ? null : <span aria-hidden>{` ${flagEmoji(room.host.country)}`}</span>}
          {room.mine && room.code ? <span className="font-mono font-semibold tracking-wider" dir="ltr">{` · ${room.code}`}</span> : null}
        </span>
        <span className="flex shrink-0 items-center gap-1 tabular-nums">
          <Users className="size-3.5" aria-hidden /> {t('rooms.here', { n: num(room.members), max: num(room.maxMembers) })}
        </span>
      </span>
    </button>
  )
}
