'use client'

import { useState } from 'react'
import { Lock, Users } from 'lucide-react'
import type { VoiceRoomSummary } from '@/lib/backend'
import { flagEmoji } from '@/lib/format'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { UserAvatar } from '@/components/looks/user-avatar'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { TopicIcon } from '@/components/ui/topic-icons'
import { useRoomT } from '@/components/screens/voice/room-t'
import { refusalLabel, roomConditions } from './room-text'
import { RoomCountry } from './room-country'

/**
 * A room tapped in a list opens this first (the owner, 2026-10-30): who is in it and what it is about, then "Join
 * room". The room I'm already in opens at once. Joining goes through the same door as before: a password or a
 * private room's code asks for it, any other refusal says why.
 */
export function useRoomPreview() {
  const { openVoice, voiceRoomId, askRoomKey } = useApp()
  const toast = useToast()
  const { t } = useI18n()
  const [room, setRoom] = useState<VoiceRoomSummary | null>(null)
  const enter = (r: VoiceRoomSummary) =>
    r.id === voiceRoomId || !r.refusal
      ? openVoice(r.id)
      : r.refusal === 'room_password' || r.refusal === 'room_private'
        ? askRoomKey(r.id, r.refusal === 'room_private' ? 'code' : 'password')
        : toast.show(t(`error.${r.refusal}` as const), 'info')
  return {
    open: (r: VoiceRoomSummary) => (r.id === voiceRoomId ? openVoice(r.id) : setRoom(r)),
    sheet: (
      <RoomPreview
        room={room}
        onClose={() => setRoom(null)}
        onJoin={(r) => {
          setRoom(null)
          enter(r)
        }}
      />
    ),
  }
}

function RoomPreview({ room, onClose, onJoin }: { room: VoiceRoomSummary | null; onClose: () => void; onJoin: (r: VoiceRoomSummary) => void }) {
  const { t, num } = useI18n()
  const { rt } = useRoomT()
  const r = room
  const locked = r?.refusal === 'room_password' || r?.refusal === 'room_private'
  const blocked = !!r?.refusal && !locked
  const conditions = r ? roomConditions(r, t, false) : []
  return (
    <Modal open={!!r} onClose={onClose} variant="sheet" title={r?.title ?? ''} hideHeader className="md:max-w-md">
      {r ? (
        <div className="flex flex-col">
          {r.coverUrl ? <img src={r.coverUrl} alt="" className="aspect-[16/7] w-full object-cover" /> : null}
          <div className="flex flex-col gap-4 px-5 pt-5 pb-5">
            <div className="flex flex-col gap-1.5">
              <span className="flex items-center gap-1.5">
                <RoomCountry iso={r.host.country} />
                {r.isPrivate || locked ? <Lock className="size-3.5 text-muted-foreground" aria-label={t(r.isPrivate ? 'rooms.private' : 'rooms.locked')} /> : null}
              </span>
              <h2 className="text-xl leading-snug font-bold tracking-tight">{r.title}</h2>
              {r.tags.length ? <p className="text-[13px] font-semibold text-primary-text">{r.tags.map((x) => `#${x}`).join(' ')}</p> : null}
            </div>
            <div className="flex items-center gap-2.5 text-sm">
              <UserAvatar userId={r.host.id} name={r.host.name} src={r.host.photoUrl} size={32} />
              <span className="min-w-0 flex-1 truncate">
                {t('voice.hostedBy', { name: r.host.name })} <span aria-hidden>{flagEmoji(r.host.country)}</span>
              </span>
            </div>
            {r.announcement ? <p className="rounded-2xl bg-muted px-4 py-3 text-sm leading-relaxed whitespace-pre-line" dir="auto">{r.announcement}</p> : null}
            {r.speakers.length ? (
              <section className="flex flex-col gap-2">
                {/* The list carries up to 4 of them (the host first), so no count here. */}
                <h3 className="text-xs font-semibold text-muted-foreground">{rt('onStage')}</h3>
                <ul className="flex flex-wrap gap-3">
                  {r.speakers.map((p) => (
                    <li key={p.id} className="flex w-16 flex-col items-center gap-1">
                      <UserAvatar userId={p.id} name={p.name} src={p.photoUrl} size={48} />
                      <span className="w-full truncate text-center text-xs">{p.name}</span>
                    </li>
                  ))}
                </ul>
              </section>
            ) : null}
            <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted-foreground">
              <span className="flex items-center gap-1 tabular-nums">
                <Users className="size-4" aria-hidden /> {t('rooms.here', { n: num(r.members), max: num(r.maxMembers) })}
              </span>
              {r.listeners ? <span className="tabular-nums">{t('voice.listening', { n: num(r.listeners) })}</span> : null}
            </p>
            {r.nowPlaying ? (
              <p className="flex min-w-0 items-center gap-1.5 text-sm">
                <TopicIcon topic="music" size={16} />
                <span className="sr-only">{t('voice.nowPlayingLabel')}:</span>
                <span className="truncate">{r.nowPlaying}</span>
              </p>
            ) : null}
            {conditions.length || blocked ? (
              <p className="flex flex-wrap gap-1">
                {blocked ? <span className="rounded-md bg-destructive/10 px-2 py-0.5 text-xs font-semibold text-destructive">{refusalLabel(r, t)}</span> : null}
                {conditions.map((c) => (
                  <span key={c} className="rounded-md bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground">{c}</span>
                ))}
              </p>
            ) : null}
            <div className="flex gap-2 pt-1">
              <Button variant="outline" size="lg" className="flex-1" onClick={onClose}>
                {t('common.cancel')}
              </Button>
              <Button variant="brand" size="lg" className="flex-[2]" disabled={blocked} onClick={() => onJoin(r)}>
                {locked ? <Lock className="size-4" aria-hidden /> : null}
                {t('join.submit')}
              </Button>
            </div>
          </div>
        </div>
      ) : null}
    </Modal>
  )
}
