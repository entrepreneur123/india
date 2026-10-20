'use client'

import useSWR from 'swr'
import { Headphones, Plus } from 'lucide-react'
import type { VoiceRoomSummary } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { EmptyState } from '@/components/ui/ambient'
import { Avatar } from '@/components/ui/avatar'
import { Skeleton } from '@/components/ui/bits'
import { Button } from '@/components/ui/pressable'
import { TopicChip, TopicIcon } from '@/components/ui/topic-icons'
import { categoryLabel } from '@/components/screens/rooms/room-text'
import { cn } from '@/lib/utils'
import { LiveBars } from './voice-stage'

/** The live voice rooms on the Rooms tab. */
export function VoiceRooms({ onStart }: { onStart: () => void }) {
  const backend = useBackend()
  const { t } = useI18n()
  const { openVoice, voiceRoomId } = useApp()
  const { data: rooms, error, mutate } = useSWR('voiceRooms', () => backend.voiceRooms(), { refreshInterval: 15_000 })

  if (error && !rooms)
    return (
      <EmptyState
        art="rooms"
        title={t('voice.loadFailed')}
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
        <div className="grid gap-3 sm:grid-cols-2" aria-busy>
          {Array.from({ length: 4 }, (_, i) => (
            <Skeleton key={i} className="h-52 rounded-2xl" />
          ))}
        </div>
      ) : rooms.length === 0 ? (
        <EmptyState
          art="rooms"
          title={t('voice.noneTitle')}
          body={t('voice.noneBody')}
          action={
            <Button variant="brand" onClick={onStart}>
              <Plus className="size-4.5" aria-hidden /> {t('voice.start')}
            </Button>
          }
        />
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2">
          {rooms.map((r) => (
            <li key={r.id}>
              <VoiceRoomCard room={r} here={r.id === voiceRoomId} onOpen={() => openVoice(r.id)} />
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function VoiceRoomCard({ room, here, onOpen }: { room: VoiceRoomSummary; here: boolean; onOpen: () => void }) {
  const { t, num } = useI18n()
  return (
    <button
      type="button"
      onClick={onOpen}
      className={cn(
        'flex h-full w-full flex-col gap-3 rounded-2xl border bg-card p-4 text-left transition-[box-shadow,background-color] duration-150 hover:shadow-e2 active:bg-muted',
        here || room.mine ? 'border-primary' : 'border-border',
      )}
    >
      <span className="flex w-full items-center justify-between gap-3">
        <TopicChip topic={room.category} label={categoryLabel(room.category, t)} />
        <span className="flex shrink-0 items-center gap-1.5 text-xs font-bold text-primary-text">
          <LiveBars /> {here ? t('voice.inRoom') : t('voice.live')}
        </span>
      </span>
      <span className="line-clamp-2 text-[17px] leading-snug font-bold tracking-tight">{room.title}</span>
      <span className="flex min-w-0 items-center gap-2.5">
        <span className="flex shrink-0 -space-x-2" aria-hidden>
          {room.speakers.map((p) => (
            <Avatar key={p.id} name={p.name} src={p.photoUrl} size={30} className="rounded-full ring-2 ring-card" />
          ))}
        </span>
        <span className="min-w-0 truncate text-sm text-muted-foreground">{room.speakers.map((p) => p.name).join(', ')}</span>
      </span>
      {room.nowPlaying ? (
        <span className="-mt-1 flex min-w-0 items-center gap-1.5 text-xs font-medium text-foreground">
          <TopicIcon topic="music" size={15} />
          <span className="sr-only">{t('voice.nowPlayingLabel')}:</span>
          <span className="truncate">{room.nowPlaying}</span>
        </span>
      ) : null}
      <span className="mt-auto flex w-full items-center gap-2 border-t border-border pt-3 text-xs text-muted-foreground">
        <Avatar name={room.host.name} src={room.host.photoUrl} size={20} />
        <span className="min-w-0 flex-1 truncate">{room.mine ? t('voice.yourRoom') : t('voice.hostedBy', { name: room.host.name })}</span>
        <span className="flex shrink-0 items-center gap-1 tabular-nums">
          <Headphones className="size-3.5" aria-hidden /> {t('voice.listening', { n: num(room.listeners) })}
        </span>
      </span>
    </button>
  )
}
