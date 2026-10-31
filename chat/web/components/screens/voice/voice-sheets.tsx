'use client'

import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { Check, ChevronRight, Copy, Crown, Eraser, Flag, ImagePlus, Link2, Lock, LockOpen, LogOut, Mic, MicOff, Paintbrush, Pin, Search, Settings2, ShieldCheck, ShieldOff, UserPlus, UserRound, UserX, ArrowDownToLine, Volume2, VolumeX, X } from 'lucide-react'
import { VOICE_SEATS, type ReportReason, type VoiceAction, type VoiceLine, type VoiceMember, type VoiceRoom } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { fixedVolume } from '@/lib/rtc'
import { volumeOf } from '@/lib/voice-volumes'
import { flagEmoji } from '@/lib/format'
import type { TKey } from '@/lib/i18n'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Field, TextArea, TextInput } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { RoomLangSelect } from '@/components/social/room-lang'
import { useSocialT } from '@/components/social/social-text'
import { ReactionIcon } from '@/components/ui/topic-icons'
import { useToast } from '@/components/ui/toast'
import { ReportSheet } from '@/components/screens/chat/report-sheet'
import { cn } from '@/lib/utils'
import { canManage, isMod, memberOf, roleLabel } from './voice-text'
import { useRoomT } from './room-t'
import { LOST_FACE, LostBadge } from './voice-stage'
import { TrustBadge } from '@/components/trust/badge'
import { LookName, NameText } from '@/components/looks/look-name'
import { useLooksT } from '@/components/looks/looks-text'
import { FriendButton, useFriendship } from '@/components/social/friend-button'
import { ageText, WALLS, type ItemId } from '@/lib/looks'
import { useShop } from '@/components/looks/use-shop'
import { Wall } from '@/components/looks/wall'
import { UserAvatar } from '@/components/looks/user-avatar'
import { RoomExtrasFields, typedTags } from '@/components/screens/rooms/room-extras'

export type SheetState =
  | { kind: 'person'; userId: string }
  | { kind: 'requests' }
  | { kind: 'audience'; inviteSeat?: number }
  | { kind: 'seat'; seat: number }
  | { kind: 'queue' }
  | { kind: 'options' }
  | { kind: 'reportRoom' }
  | { kind: 'settings' }
  /** `draft`: a chat line pinned from its menu ("Announcement"), to check before saving. */
  | { kind: 'notice'; draft?: NoticeDraft }
  | { kind: 'theme' }
  | { kind: 'clear' }
  | { kind: 'invite' }
  | { kind: 'people' }
  | null

export const freeSeats = (room: VoiceRoom) => {
  const taken = new Set(room.members.map((x) => x.seat))
  return room.seats.filter((s) => s.index > 0 && s.index < VOICE_SEATS && !s.locked && !taken.has(s.index)).length
}

function Row({ icon, label, onClick, danger, disabled }: { icon: ReactNode; label: string; onClick: () => void; danger?: boolean; disabled?: boolean }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={cn(
        'flex h-13 w-full items-center gap-4 px-6 text-start text-[15px] font-medium transition-colors duration-150 hover:bg-muted disabled:opacity-45 disabled:hover:bg-transparent',
        danger && 'text-destructive',
      )}
    >
      <span className={cn('shrink-0 [&>svg]:size-5', danger ? 'text-destructive' : 'text-muted-foreground')} aria-hidden>
        {icon}
      </span>
      <span className="min-w-0 flex-1 truncate">{label}</span>
    </button>
  )
}

function PersonLine({ m, trailing, onClick }: { m: VoiceMember; trailing?: ReactNode; onClick?: () => void }) {
  const { t } = useI18n()
  const { rt } = useRoomT()
  const role = roleLabel(m, t)
  const body = (
    <>
      <span className="relative shrink-0">
        <UserAvatar userId={m.profile.id} name={m.profile.name} src={m.profile.photoUrl} size={40} className={cn(m.lost && LOST_FACE)} />
        {m.lost ? <LostBadge size={18} /> : null}
      </span>
      <span className="min-w-0 flex-1">
        <span className="flex items-center gap-1.5">
          <NameText userId={m.profile.id} className="truncate text-[15px] font-semibold">{m.profile.name}</NameText>
          <TrustBadge userId={m.profile.id} size={15} />
          {role ? <span className="shrink-0 rounded-full bg-primary-soft px-1.5 text-[10px] font-bold text-primary-text">{role}</span> : null}
          {m.hand ? <ReactionIcon kind="hand" size={16} /> : null}
        </span>
        <span className="flex min-w-0 text-xs">
          {m.lost ? <span className="truncate font-semibold text-destructive">{rt('lost')}</span> : <LookName
            userId={m.profile.id}
            handle={m.profile.handle}
            className="font-semibold"
            fallback={
              <span className="truncate text-muted-foreground">
                {ageText(m.profile)} <span aria-hidden>{flagEmoji(m.profile.country)}</span>
              </span>
            }
          />}
        </span>
      </span>
    </>
  )
  return (
    <div className="flex items-center gap-3 px-6 py-2">
      {onClick ? (
        <button type="button" onClick={onClick} className="-m-1 flex min-w-0 flex-1 items-center gap-3 rounded-xl p-1 text-start hover:bg-muted">
          {body}
        </button>
      ) : (
        <span className="flex min-w-0 flex-1 items-center gap-3">{body}</span>
      )}
      {trailing}
    </div>
  )
}

/** Tapping a person: their profile, and the host's and co-hosts' tools (Clubhouse). */
export function PersonSheet({
  room,
  userId,
  onClose,
  act,
}: {
  room: VoiceRoom
  userId: string | null
  onClose: () => void
  act: (a: VoiceAction) => Promise<boolean>
}) {
  const { t } = useI18n()
  const { rt } = useRoomT()
  const toast = useToast()
  const { openProfile } = useApp()
  const [reportOpen, setReportOpen] = useState(false)
  // A step that asks first: removing them, or handing them the room (079).
  const [confirm, setConfirm] = useState<'kick' | 'host' | null>(null)
  const [busy, setBusy] = useState(false)
  // Keep showing the last person while the sheet animates out.
  const [shownId, setShownId] = useState(userId)
  useEffect(() => {
    if (userId) setShownId(userId)
  }, [userId])
  const target = shownId ? memberOf(room, shownId) : null
  const me = memberOf(room, room.meId)
  if (!target) return null

  const self = target.profile.id === room.meId
  const mod = isMod(me) && !self && target.role !== 'host'
  const host = me?.role === 'host' && !self
  // 036: the host moves and removes anyone else; a co-host only listeners and speakers (the server refuses the rest).
  const manage = canManage(me, target)
  const run = async (a: VoiceAction, done?: string) => {
    const ok = await act(a)
    if (ok && done) toast.show(done, 'success')
    if (ok) onClose()
  }

  return (
    <>
      <Modal open={!!userId && !reportOpen && !confirm} onClose={onClose} variant="sheet" hideHeader>
        <div className="flex flex-col items-center gap-2 px-6 pt-6 pb-3 text-center">
          <UserAvatar userId={target.profile.id} name={target.profile.name} src={target.profile.photoUrl} size={72} />
          <h2 className="mt-1 text-lg font-bold">
            <NameText userId={target.profile.id}>{target.profile.name}</NameText>
            {self ? ` (${t('voice.you')})` : null}
          </h2>
          <LookName userId={target.profile.id} handle={target.profile.handle} className="-mt-1.5 text-sm font-semibold" />
          <p className="text-sm text-muted-foreground">
            {ageText(target.profile)} <span aria-hidden>{flagEmoji(target.profile.country)}</span>
          </p>
          <p className="flex flex-wrap justify-center gap-1.5">
            {roleLabel(target, t) ? (
              <span className="rounded-full bg-primary-soft px-2.5 py-0.5 text-xs font-bold text-primary-text">{roleLabel(target, t)}</span>
            ) : null}
            <span className="rounded-full bg-muted px-2.5 py-0.5 text-xs font-medium text-muted-foreground">
              {target.seat === null ? t('voice.inAudience') : target.seat === 0 ? t('voice.hostSeat') : t('voice.seat', { n: target.seat })}
            </span>
          </p>
        </div>
        <div className="flex flex-col pb-4">
          {self ? null : (
            <Row
              icon={<UserRound />}
              label={t('voice.viewProfile')}
              onClick={() => {
                onClose()
                openProfile(target.profile.id)
              }}
            />
          )}
          {self ? null : <FriendRow userId={target.profile.id} name={target.profile.name} />}
          {!self && room.voice && target.seat !== null ? <VolumeRow roomId={room.id} userId={target.profile.id} name={target.profile.name} /> : null}
          {self && target.seat !== null ? (
            <Row icon={<ArrowDownToLine />} label={t('voice.leaveStage')} onClick={() => void run({ type: 'unseat' })} />
          ) : null}
          {mod && target.seat === null ? (
            <Row
              icon={<UserPlus />}
              label={t('voice.inviteSpeak')}
              disabled={freeSeats(room) === 0}
              onClick={() => void run({ type: 'invite', userId: target.profile.id }, t('voice.invited', { name: target.profile.name }))}
            />
          ) : null}
          {mod && target.seat !== null && !target.muted ? (
            <Row icon={<MicOff />} label={t('voice.muteThem')} onClick={() => void run({ type: 'mute', userId: target.profile.id })} />
          ) : null}
          {manage && target.seat !== null ? (
            <Row icon={<ArrowDownToLine />} label={rt('moveToListeners')} onClick={() => void run({ type: 'toAudience', userId: target.profile.id })} />
          ) : null}
          {host && target.role !== 'host' ? (
            <Row
              icon={target.role === 'cohost' ? <ShieldOff /> : <ShieldCheck />}
              label={target.role === 'cohost' ? t('voice.removeCohost') : t('voice.makeCohost')}
              onClick={() => void run({ type: 'cohost', userId: target.profile.id, on: target.role !== 'cohost' })}
            />
          ) : null}
          {host && target.role === 'cohost' && !target.lost ? <Row icon={<Crown />} label={rt('makeHost')} onClick={() => setConfirm('host')} /> : null}
          {manage ? <Row icon={<UserX />} label={t('voice.kick')} danger onClick={() => setConfirm('kick')} /> : null}
          {self ? null : <Row icon={<Flag />} label={t('voice.report')} danger onClick={() => setReportOpen(true)} />}
        </div>
      </Modal>
      <Modal
        open={confirm !== null}
        onClose={() => setConfirm(null)}
        title={confirm === 'host' ? rt('makeHostTitle', { name: target.profile.name }) : t('voice.kickTitle', { name: target.profile.name })}
        description={confirm === 'host' ? rt('makeHostBody', { name: target.profile.name }) : t('voice.kickBody')}
      >
        <div className="flex gap-3 p-6 pt-5">
          <Button variant="outline" className="flex-1" onClick={() => setConfirm(null)}>
            {t('common.cancel')}
          </Button>
          <Button
            variant={confirm === 'host' ? 'brand' : 'danger'}
            className="flex-1"
            loading={busy}
            onClick={async () => {
              setBusy(true)
              await run(confirm === 'host' ? { type: 'makeHost', userId: target.profile.id } : { type: 'kick', userId: target.profile.id })
              setBusy(false)
              setConfirm(null)
            }}
          >
            {confirm === 'host' ? rt('makeHost') : t('voice.kickConfirm')}
          </Button>
        </div>
      </Modal>
      <ReportSheet
        open={reportOpen}
        onClose={() => {
          setReportOpen(false)
          onClose()
        }}
        partner={target.profile}
        matchId={null}
      />
    </>
  )
}

/**
 * 51: how loud this person is for me (remembered for every room). iPhone and iPad can't set a volume, so there it is
 * "mute for me" (and 0 anywhere stops their voice from downloading at all).
 */
function VolumeRow({ roomId, userId, name }: { roomId: string; userId: string; name: string }) {
  const backend = useBackend()
  const { rt } = useRoomT()
  const [v, setV] = useState(() => volumeOf(userId))
  const set = (x: number) => {
    setV(x)
    backend.voiceVolume(roomId, userId, x)
  }
  if (fixedVolume(navigator.userAgent, navigator.maxTouchPoints))
    return <Row icon={v === 0 ? <Volume2 /> : <VolumeX />} label={rt(v === 0 ? 'volumeUnmute' : 'volumeMute')} onClick={() => set(v === 0 ? 1 : 0)} />
  return (
    <label className="flex h-13 w-full items-center gap-4 px-6 text-[15px] font-medium">
      <span className="shrink-0 text-muted-foreground [&>svg]:size-5" aria-hidden>
        {v === 0 ? <VolumeX /> : <Volume2 />}
      </span>
      <span className="shrink-0">{rt('volume')}</span>
      <input
        type="range"
        min={0}
        max={100}
        step={5}
        value={Math.round(v * 100)}
        onChange={(e) => set(Number(e.target.value) / 100)}
        aria-label={rt('volumeOf', { name })}
        className="min-w-0 flex-1 accent-primary"
      />
      <span className="w-10 shrink-0 text-end text-sm tabular-nums text-muted-foreground">{Math.round(v * 100)}%</span>
    </label>
  )
}

/** Add friend in a room's person sheet (Accept when they asked me; withdraw a request I sent; nothing once friends). */
function FriendRow({ userId, name }: { userId: string; name: string }) {
  const { t } = useI18n()
  const f = useFriendship(userId, name)
  if (f.state === null || f.state === 'friends') return null
  if (f.state === 'incoming') return <Row icon={<Check />} label={t('profile.acceptRequest')} disabled={f.busy} onClick={() => void f.accept()} />
  if (f.state === 'requested') return <Row icon={<UserPlus />} label={t('profile.withdraw')} disabled={f.busy} onClick={() => void f.withdraw()} />
  return <Row icon={<UserPlus />} label={t('profile.add')} disabled={f.busy} onClick={() => void f.add()} />
}

/**
 * Everyone in the room (the header's count opens it): the stage first, by seat, then the listeners (raised hands
 * first), with search. Each row has the person's @ID, their role and a friend button with the real state.
 */
export function PeopleSheet({ room, open, onClose, onPerson }: { room: VoiceRoom; open: boolean; onClose: () => void; onPerson: (id: string) => void }) {
  const { t, num } = useI18n()
  const { rt } = useRoomT()
  const { lt } = useLooksT()
  const id = useId()
  const [query, setQuery] = useState('')
  useEffect(() => {
    if (open) setQuery('')
  }, [open])
  const q = query.trim().toLowerCase()
  const match = (x: VoiceMember) => !q || x.profile.name.toLowerCase().includes(q)
  const stage = room.members.filter((x) => x.seat !== null && match(x)).sort((a, b) => a.seat! - b.seat!)
  const listeners = room.members.filter((x) => x.seat === null && match(x)).sort((a, b) => Number(b.hand) - Number(a.hand))
  const groups = [
    { key: 'stage', title: rt('onStage'), list: stage },
    { key: 'listening', title: rt('listening'), list: listeners },
  ].filter((g) => g.list.length)
  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={`${lt('people.title')} · ${num(room.members.length)}`} className="md:h-[min(40rem,85dvh)]">
      <div className="sticky top-0 z-10 bg-card px-6 pt-3 pb-2">
        <label htmlFor={`${id}-q`} className="sr-only">
          {t('voice.searchPeople')}
        </label>
        <div className="relative">
          <Search className="pointer-events-none absolute top-1/2 inset-s-3.5 size-4.5 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <TextInput id={`${id}-q`} value={query} onChange={(e) => setQuery(e.target.value)} placeholder={t('voice.searchPeople')} className="h-11 ps-10" type="search" autoComplete="off" />
        </div>
      </div>
      {groups.length === 0 ? (
        <p className="px-6 py-8 text-center text-sm text-muted-foreground">{t('voice.noMatch')}</p>
      ) : (
        groups.map((g) => (
          <section key={g.key} aria-labelledby={`${id}-${g.key}`} className="pb-3">
            <h3 id={`${id}-${g.key}`} className="px-6 pt-2 pb-1 text-xs font-bold tracking-wider text-muted-foreground uppercase">
              {g.title} · <span className="tabular-nums">{num(g.list.length)}</span>
            </h3>
            <ul>
              {g.list.map((x) => (
                <li key={x.profile.id}>
                  <PersonLine
                    m={x}
                    onClick={() => onPerson(x.profile.id)}
                    trailing={x.profile.id === room.meId ? null : <FriendButton userId={x.profile.id} name={x.profile.name} />}
                  />
                </li>
              ))}
            </ul>
          </section>
        ))
      )}
    </Modal>
  )
}

/** The host's queue of raised hands. */
export function RequestsSheet({ room, open, onClose, act, onPerson }: { room: VoiceRoom; open: boolean; onClose: () => void; act: (a: VoiceAction) => Promise<boolean>; onPerson: (id: string) => void }) {
  const { t } = useI18n()
  const hands = room.members.filter((x) => x.hand && x.seat === null)
  const full = freeSeats(room) === 0
  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={t('voice.requests')} description={full ? t('voice.stageFull') : t('voice.requestsDesc')}>
      <div className="pt-2 pb-4">
        {hands.length === 0 ? (
          <div className="flex flex-col items-center gap-3 px-6 py-8 text-center">
            <span className="grid size-14 place-items-center rounded-full bg-muted">
              <ReactionIcon kind="hand" size={28} />
            </span>
            <p className="text-sm text-muted-foreground">{t('voice.noRequests')}</p>
          </div>
        ) : (
          <ul aria-live="polite">
            {hands.map((x) => (
              <li key={x.profile.id}>
                <PersonLine
                  m={x}
                  onClick={() => onPerson(x.profile.id)}
                  trailing={
                    <span className="flex shrink-0 items-center gap-1.5">
                      <Button size="icon-sm" variant="ghost" onClick={() => void act({ type: 'answer', userId: x.profile.id, accept: false })} aria-label={t('voice.declineName', { name: x.profile.name })}>
                        <X className="size-4.5" aria-hidden />
                      </Button>
                      <Button size="sm" variant="brand" disabled={full} onClick={() => void act({ type: 'answer', userId: x.profile.id, accept: true })} aria-label={t('voice.acceptName', { name: x.profile.name })}>
                        <Mic className="size-4" aria-hidden /> {t('voice.accept')}
                      </Button>
                    </span>
                  }
                />
              </li>
            ))}
          </ul>
        )}
      </div>
    </Modal>
  )
}

/** Everyone listening, with search. Opened from an empty seat by the host, it invites someone to that seat. */
export function AudienceSheet({
  room,
  open,
  inviteSeat,
  onClose,
  act,
  onPerson,
}: {
  room: VoiceRoom
  open: boolean
  inviteSeat?: number
  onClose: () => void
  act: (a: VoiceAction) => Promise<boolean>
  onPerson: (id: string) => void
}) {
  const { t, num } = useI18n()
  const toast = useToast()
  const id = useId()
  const [query, setQuery] = useState('')
  useEffect(() => {
    if (open) setQuery('')
  }, [open])
  const audience = room.members.filter((x) => x.seat === null)
  const q = query.trim().toLowerCase()
  const shown = (q ? audience.filter((x) => x.profile.name.toLowerCase().includes(q)) : audience)
    // raised hands first, then me, then everyone else
    .sort((a, b) => Number(b.hand) - Number(a.hand) || Number(b.profile.id === room.meId) - Number(a.profile.id === room.meId))
  const inviting = inviteSeat !== undefined
  return (
    <Modal
      open={open}
      onClose={onClose}
      variant="sheet"
      title={inviting ? t('voice.inviteTo', { n: inviteSeat }) : t('voice.listeningN', { n: num(audience.length) })}
      className="md:h-[min(40rem,85dvh)]"
    >
      <div className="sticky top-0 z-10 bg-card px-6 pt-3 pb-2">
        <label htmlFor={`${id}-q`} className="sr-only">
          {t('voice.searchPeople')}
        </label>
        <div className="relative">
          <Search className="pointer-events-none absolute top-1/2 inset-s-3.5 size-4.5 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <TextInput id={`${id}-q`} value={query} onChange={(e) => setQuery(e.target.value)} placeholder={t('voice.searchPeople')} className="h-11 ps-10" type="search" autoComplete="off" />
        </div>
      </div>
      <ul className="pb-4">
        {shown.length === 0 ? (
          <li className="px-6 py-8 text-center text-sm text-muted-foreground">{audience.length ? t('voice.noMatch') : t('voice.noListeners')}</li>
        ) : (
          shown.map((x) => (
            <li key={x.profile.id}>
              <PersonLine
                m={x}
                onClick={() => onPerson(x.profile.id)}
                trailing={
                  inviting && x.profile.id !== room.meId ? (
                    <Button
                      size="sm"
                      variant="secondary"
                      onClick={async () => {
                        if (await act({ type: 'invite', userId: x.profile.id, seat: inviteSeat })) {
                          toast.show(t('voice.invited', { name: x.profile.name }), 'success')
                          onClose()
                        }
                      }}
                    >
                      {t('voice.invite')}
                    </Button>
                  ) : (
                    <ChevronRight className="size-4.5 shrink-0 text-muted-foreground rtl:rotate-180" aria-hidden />
                  )
                }
              />
            </li>
          ))
        )}
      </ul>
    </Modal>
  )
}

/** The host taps an empty seat: invite someone to it, or lock / unlock it. */
export function SeatSheet({ room, seat, onClose, onInvite, act }: { room: VoiceRoom; seat: number | null; onClose: () => void; onInvite: (seat: number) => void; act: (a: VoiceAction) => Promise<boolean> }) {
  const { t } = useI18n()
  const [shown, setShown] = useState(seat)
  useEffect(() => {
    if (seat !== null) setShown(seat)
  }, [seat])
  if (shown === null) return null
  const locked = room.seats[shown]?.locked ?? false
  return (
    <Modal open={seat !== null} onClose={onClose} variant="sheet" title={t('voice.seat', { n: shown })}>
      <div className="flex flex-col pt-2 pb-4">
        <Row icon={<UserPlus />} label={t('voice.inviteTo', { n: shown })} disabled={locked} onClick={() => onInvite(shown)} />
        <Row
          icon={locked ? <LockOpen /> : <Lock />}
          label={locked ? t('voice.unlockSeat') : t('voice.lockSeat')}
          onClick={async () => {
            if (await act({ type: 'lock', seat: shown, locked: !locked })) onClose()
          }}
        />
      </div>
    </Modal>
  )
}

export function OptionsSheet({
  open,
  onClose,
  isHost,
  code,
  canShare,
  onInvite,
  onShare,
  onCopyCode,
  onSettings,
  onNotice,
  onTheme,
  onClearChat,
  onReport,
  onLeave,
}: {
  open: boolean
  onClose: () => void
  isHost: boolean
  /** The room's code (host only). */
  code: string | null
  /** The link works for others (a private room's link carries the code, which only the host has). */
  canShare: boolean
  onInvite: () => void
  onShare: () => void
  onCopyCode: () => void
  onSettings: () => void
  /** The host or a co-host: the room's notice. */
  onNotice?: () => void
  /** The host or a co-host: the room's wall (45). */
  onTheme?: () => void
  /** The host or a co-host: empty the chat for everyone (50). */
  onClearChat?: () => void
  onReport: () => void
  onLeave: () => void
}) {
  const { t } = useI18n()
  const { st } = useSocialT()
  const { rt } = useRoomT()
  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={t('voice.options')}>
      <div className="flex flex-col pt-2 pb-4">
        <Row icon={<UserPlus />} label={st('invite.title')} onClick={onInvite} />
        {canShare ? <Row icon={<Link2 />} label={t('voice.share')} onClick={onShare} /> : null}
        {code ? <Row icon={<Copy />} label={t('room.copy', { code })} onClick={onCopyCode} /> : null}
        {onNotice ? <Row icon={<Pin />} label={t('room.announcement')} onClick={onNotice} /> : null}
        {onTheme ? <Row icon={<Paintbrush />} label={t('room.theme')} onClick={onTheme} /> : null}
        {onClearChat ? <Row icon={<Eraser />} label={rt('clearChat')} onClick={onClearChat} /> : null}
        {isHost ? <Row icon={<Settings2 />} label={t('room.settings')} onClick={onSettings} /> : null}
        {isHost ? null : <Row icon={<Flag />} label={t('voice.reportRoom')} onClick={onReport} />}
        <Row icon={<LogOut />} label={isHost ? t('voice.endConfirm') : t('voice.leaveQuietly')} danger onClick={onLeave} />
      </div>
    </Modal>
  )
}

const ROOM_REASONS: { value: ReportReason; label: TKey; detail: TKey }[] = [
  { value: 'harassment', label: 'report.harassment', detail: 'report.harassmentDetail' },
  { value: 'nudity', label: 'report.nudity', detail: 'report.nudityDetail' },
  { value: 'minor', label: 'report.minor', detail: 'report.minorDetail' },
  { value: 'other', label: 'report.other', detail: 'report.otherDetail' },
]

/** 50: the host or a co-host empties the chat for everyone (the lines stay on the server for reports). */
export function ClearChatSheet({ roomId, open, onClose }: { roomId: string; open: boolean; onClose: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { t } = useI18n()
  const { rt } = useRoomT()
  const [busy, setBusy] = useState(false)
  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={rt('clearChatTitle')} description={rt('clearChatBody')}>
      <div className="flex justify-end gap-2 px-6 pt-2 pb-6">
        <Button variant="ghost" disabled={busy} onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Button
          variant="danger"
          loading={busy}
          autoFocus
          onClick={async () => {
            setBusy(true)
            try {
              await backend.clearVoiceChat(roomId)
              onClose()
            } catch (err) {
              toast.error(err)
            } finally {
              setBusy(false)
            }
          }}
        >
          {rt('clearChat')}
        </Button>
      </div>
    </Modal>
  )
}

/** Host: the room's title, language and password (the notice has its own sheet). */
export function SettingsSheet({ room, open, onClose }: { room: VoiceRoom; open: boolean; onClose: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { t } = useI18n()
  const id = useId()
  const [title, setTitle] = useState(room.title)
  const [lang, setLang] = useState(room.lang ?? '')
  const [pw, setPw] = useState(room.password ?? '')
  const shownTags = room.tags.map((x) => `#${x}`).join(' ')
  const [cover, setCover] = useState<File | null | undefined>(undefined)
  const [tags, setTags] = useState(shownTags)
  const { st } = useSocialT()
  const [busy, setBusy] = useState(false)
  useEffect(() => {
    if (!open) return
    setTitle(room.title)
    setLang(room.lang ?? '')
    setPw(room.password ?? '')
    setCover(undefined)
    setTags(shownTags)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])
  const newPw = pw.trim()
  const valid = title.trim().length >= 2 && title.trim().length <= 40 && (!newPw || newPw.length >= 4)
  const save = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!valid || busy) return
    setBusy(true)
    try {
      await backend.updateVoiceRoom(room.id, { title: title.trim(), lang: lang || undefined })
      if (newPw !== (room.password ?? '')) await backend.setRoomPassword(room.id, newPw || null)
      if (cover !== undefined || typedTags(tags).join() !== room.tags.join()) await backend.setRoomExtras(room.id, { cover, tags: typedTags(tags) })
      toast.show(t('room.saved'), 'success')
      onClose()
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }
  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={t('room.settings')} description={t('room.settingsDesc')}>
      <form onSubmit={(e) => void save(e)} className="flex flex-col gap-5 p-6 pt-4">
        <Field label={t('create.name')} htmlFor={`${id}-t`} trailing={<span className="text-xs tabular-nums text-muted-foreground">{title.length}/40</span>}>
          <TextInput id={`${id}-t`} value={title} maxLength={40} onChange={(e) => setTitle(e.target.value)} data-autofocus />
        </Field>
        <div className="flex items-center justify-between gap-4">
          <span className="text-sm font-semibold">{st('rooms.lang')}</span>
          <RoomLangSelect value={lang} onChange={setLang} label={st('rooms.lang')} anyLabel={lang ? undefined : '—'} className="w-48" />
        </div>
        <RoomExtrasFields cover={cover} coverUrl={room.coverUrl} onCover={setCover} tags={tags} onTags={setTags} />
        <Field label={t('room.password')} htmlFor={`${id}-p`} hint={t('room.passwordHint')}>
          <TextInput id={`${id}-p`} value={pw} maxLength={12} autoComplete="off" placeholder="••••" onChange={(e) => setPw(e.target.value)} />
        </Field>
        <Button type="submit" variant="brand" size="lg" loading={busy} disabled={!valid}>
          {t('common.save')}
        </Button>
      </form>
    </Modal>
  )
}

/** The host or a co-host (27): the notice pinned at the top of the room; saving a new one tells the chat. */
/**
 * 45: the host or a co-host dresses the room in one of the walls they bought (everyone in the room sees it). Walls
 * they don't have yet open the Looks shop.
 */
export function ThemeSheet({ room, open, onClose }: { room: VoiceRoom; open: boolean; onClose: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { t } = useI18n()
  const { lt } = useLooksT()
  const { openShop } = useApp()
  const { data: shop } = useShop(open)
  const [busy, setBusy] = useState<string | null>(null)
  const owned = new Set<string>(shop?.owned ?? [])
  const set = async (id: ItemId | null) => {
    if (id && !owned.has(id)) {
      onClose()
      return openShop('wall')
    }
    setBusy(id ?? 'none')
    try {
      await backend.setRoomTheme(room.id, id)
      toast.show(t('room.themeSaved'), 'success')
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(null)
    }
  }
  const tile = (id: ItemId | null, label: string) => {
    const on = (room.theme ?? null) === id
    return (
      <li key={id ?? 'none'}>
        <button
          type="button"
          aria-pressed={on}
          disabled={!!busy || !shop}
          onClick={() => void set(id)}
          className={cn('flex w-full flex-col gap-1.5 rounded-2xl p-1.5 text-start ring-1 transition-shadow disabled:opacity-60', on ? 'ring-2 ring-primary' : 'ring-border hover:shadow-e1')}
        >
          <span className="relative isolate block h-16 w-full overflow-hidden rounded-xl bg-chat-wall">
            <Wall id={id} />
            {id && shop && !owned.has(id) ? (
              <span className="absolute inset-0 grid place-items-center bg-black/35 text-white">
                <Lock className="size-4" aria-hidden />
              </span>
            ) : null}
          </span>
          <span className="flex items-center gap-1 px-1 text-xs font-semibold">
            {on ? <Check className="size-3.5 shrink-0 text-primary-text" strokeWidth={3} aria-hidden /> : null}
            <span className="truncate">{label}</span>
          </span>
        </button>
      </li>
    )
  }
  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={t('room.theme')} description={t('room.themeHint')}>
      <ul className="grid grid-cols-3 gap-2.5 p-5 pt-3 sm:grid-cols-4">
        {tile(null, lt('looks.none'))}
        {WALLS.map((id) => tile(id, lt(`item.${id}`)))}
      </ul>
    </Modal>
  )
}

/** A notice to save: its text, and its photo (a photo line of the chat, or one picked here and sent on saving). */
export type NoticeDraft = { text: string; photo: { line: number; url: string } | null }
type NoticePhoto = { line: number; url: string } | { file: File; url: string }

/**
 * The host or a co-host (27, 106): the notice pinned at the top of the room, text and a photo (KakaoTalk's notice). A
 * photo picked here goes into the chat on saving, as any photo (checked on this device first), and the notice shows it.
 */
export function NoticeSheet({
  room,
  open,
  draft,
  onPhoto,
  onClose,
}: {
  room: VoiceRoom
  open: boolean
  draft?: NoticeDraft
  onPhoto: (photo: File) => Promise<VoiceLine | null>
  onClose: () => void
}) {
  const backend = useBackend()
  const toast = useToast()
  const { t } = useI18n()
  const id = useId()
  const fileRef = useRef<HTMLInputElement>(null)
  const [note, setNote] = useState('')
  const [photo, setPhoto] = useState<NoticePhoto | null>(null)
  const [busy, setBusy] = useState<'save' | 'remove' | null>(null)
  useEffect(() => {
    if (!open) return
    const d = draft ?? { text: room.announcement ?? '', photo: room.noticeLine !== null ? { line: room.noticeLine, url: room.noticePhoto ?? '' } : null }
    setNote(d.text)
    setPhoto(d.photo)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, draft])
  // A picked file's preview link goes when it is replaced or the sheet closes.
  useEffect(() => (photo && 'file' in photo ? () => URL.revokeObjectURL(photo.url) : undefined), [photo])
  const text = note.trim()
  const changed = text !== (room.announcement ?? '') || (photo === null ? null : 'line' in photo ? photo.line : -1) !== room.noticeLine
  const save = async (remove: boolean) => {
    setBusy(remove ? 'remove' : 'save')
    try {
      let line = !remove && photo && 'line' in photo ? photo.line : null
      if (!remove && photo && 'file' in photo) {
        const sent = await onPhoto(photo.file) // says why itself when it isn't sent
        if (!sent) return
        line = sent.id
      }
      await backend.setRoomNotice(room.id, remove ? '' : text, line)
      toast.show(t(remove ? 'notice.removed' : 'notice.saved'), 'success')
      onClose()
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(null)
    }
  }
  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={t('room.announcement')} description={t('create.announcementHint')}>
      <form
        onSubmit={(e) => {
          e.preventDefault()
          if ((text || photo) && changed && text.length <= 200 && !busy) void save(false)
        }}
        className="flex flex-col gap-4 p-6 pt-4"
      >
        <Field label={t('room.announcement')} htmlFor={`${id}-n`} trailing={<span className={cn('text-xs tabular-nums', note.length > 200 ? 'text-destructive' : 'text-muted-foreground')}>{note.length}/200</span>}>
          <TextArea id={`${id}-n`} value={note} maxLength={Math.max(200, note.length)} rows={4} placeholder={t('create.announcementPlaceholder')} onChange={(e) => setNote(e.target.value)} data-autofocus />
        </Field>
        <input
          ref={fileRef}
          type="file"
          accept="image/*"
          className="hidden"
          tabIndex={-1}
          onChange={(e) => {
            const file = e.target.files?.[0]
            e.target.value = ''
            if (file) setPhoto({ file, url: URL.createObjectURL(file) })
          }}
        />
        {photo ? (
          <div className="relative w-fit">
            <img src={photo.url} alt={t('bubble.photo')} className="h-28 max-w-full rounded-xl bg-muted object-contain" />
            <button
              type="button"
              onClick={() => setPhoto(null)}
              aria-label={t('me.removePhoto')}
              className="absolute -top-2 -end-2 grid size-7 place-items-center rounded-full bg-foreground text-background shadow-e2"
            >
              <X className="size-4" aria-hidden />
            </button>
          </div>
        ) : (
          <Button type="button" variant="secondary" className="w-fit" onClick={() => fileRef.current?.click()}>
            <ImagePlus className="size-4.5" aria-hidden /> {t('photo.add')}
          </Button>
        )}
        <Button type="submit" variant="brand" size="lg" loading={busy === 'save'} disabled={!(text || photo) || !changed || text.length > 200 || !!busy}>
          {t('common.save')}
        </Button>
        {room.announcement || room.noticeLine !== null ? (
          <Button variant="ghost" size="lg" className="text-destructive hover:text-destructive" loading={busy === 'remove'} disabled={!!busy} onClick={() => void save(true)}>
            {t('notice.remove')}
          </Button>
        ) : null}
      </form>
    </Modal>
  )
}

export function ReportRoomSheet({ roomId, open, onClose }: { roomId: string; open: boolean; onClose: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { t } = useI18n()
  const [busy, setBusy] = useState<ReportReason | null>(null)
  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={t('voice.reportRoom')} description={t('voice.reportRoomDesc')}>
      <ul className="flex flex-col pt-2 pb-4">
        {ROOM_REASONS.map((r) => (
          <li key={r.value}>
            <button
              type="button"
              disabled={busy !== null}
              onClick={async () => {
                setBusy(r.value)
                try {
                  await backend.reportVoiceRoom(roomId, r.value)
                  toast.show(t('voice.reportedRoom'), 'success')
                  onClose()
                } catch (err) {
                  toast.error(err)
                } finally {
                  setBusy(null)
                }
              }}
              className="flex w-full items-center gap-4 px-6 py-3 text-start transition-colors duration-150 hover:bg-muted disabled:opacity-60"
            >
              <span className="min-w-0 flex-1">
                <span className="block text-[15px] font-semibold">{t(r.label)}</span>
                <span className="block text-xs text-muted-foreground">{t(r.detail)}</span>
              </span>
              <ChevronRight className="size-4.5 text-muted-foreground rtl:rotate-180" aria-hidden />
            </button>
          </li>
        ))}
      </ul>
    </Modal>
  )
}
