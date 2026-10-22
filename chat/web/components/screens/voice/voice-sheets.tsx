'use client'

import { useEffect, useId, useState, type ReactNode } from 'react'
import { ChevronRight, Copy, Flag, Link2, Lock, LockOpen, LogOut, Mic, MicOff, Search, Settings2, ShieldCheck, ShieldOff, UserPlus, UserRound, UserX, ArrowDownToLine, X } from 'lucide-react'
import { VOICE_SEATS, type ReportReason, type VoiceAction, type VoiceMember, type VoiceRoom } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { flagEmoji } from '@/lib/format'
import type { TKey } from '@/lib/i18n'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Avatar } from '@/components/ui/avatar'
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
import { TrustBadge } from '@/components/trust/badge'

export type SheetState =
  | { kind: 'person'; userId: string }
  | { kind: 'requests' }
  | { kind: 'audience'; inviteSeat?: number }
  | { kind: 'seat'; seat: number }
  | { kind: 'queue' }
  | { kind: 'options' }
  | { kind: 'reportRoom' }
  | { kind: 'settings' }
  | { kind: 'invite' }
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
        'flex h-13 w-full items-center gap-4 px-6 text-left text-[15px] font-medium transition-colors duration-150 hover:bg-muted disabled:opacity-45 disabled:hover:bg-transparent',
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
  const role = roleLabel(m, t)
  const body = (
    <>
      <Avatar name={m.profile.name} src={m.profile.photoUrl} size={40} />
      <span className="min-w-0 flex-1">
        <span className="flex items-center gap-1.5">
          <span className="truncate text-[15px] font-semibold">{m.profile.name}</span>
          <TrustBadge userId={m.profile.id} size={15} />
          {role ? <span className="shrink-0 rounded-full bg-primary-soft px-1.5 text-[10px] font-bold text-primary-text">{role}</span> : null}
          {m.hand ? <ReactionIcon kind="hand" size={16} /> : null}
        </span>
        <span className="block truncate text-xs text-muted-foreground">
          {m.profile.age} · {m.profile.city} <span aria-hidden>{flagEmoji(m.profile.country)}</span>
        </span>
      </span>
    </>
  )
  return (
    <div className="flex items-center gap-3 px-6 py-2">
      {onClick ? (
        <button type="button" onClick={onClick} className="-m-1 flex min-w-0 flex-1 items-center gap-3 rounded-xl p-1 text-left hover:bg-muted">
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
  const [kickOpen, setKickOpen] = useState(false)
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
      <Modal open={!!userId && !reportOpen && !kickOpen} onClose={onClose} variant="sheet" hideHeader>
        <div className="flex flex-col items-center gap-2 px-6 pt-6 pb-3 text-center">
          <Avatar name={target.profile.name} src={target.profile.photoUrl} size={72} />
          <h2 className="mt-1 text-lg font-bold">{self ? `${target.profile.name} (${t('voice.you')})` : target.profile.name}</h2>
          <p className="text-sm text-muted-foreground">
            {target.profile.age} · {target.profile.city} <span aria-hidden>{flagEmoji(target.profile.country)}</span>
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
          {manage ? <Row icon={<UserX />} label={t('voice.kick')} danger onClick={() => setKickOpen(true)} /> : null}
          {self ? null : <Row icon={<Flag />} label={t('voice.report')} danger onClick={() => setReportOpen(true)} />}
        </div>
      </Modal>
      <Modal
        open={kickOpen}
        onClose={() => setKickOpen(false)}
        title={t('voice.kickTitle', { name: target.profile.name })}
        description={t('voice.kickBody')}
      >
        <div className="flex gap-3 p-6 pt-5">
          <Button variant="outline" className="flex-1" onClick={() => setKickOpen(false)}>
            {t('common.cancel')}
          </Button>
          <Button
            variant="danger"
            className="flex-1"
            loading={busy}
            onClick={async () => {
              setBusy(true)
              await run({ type: 'kick', userId: target.profile.id })
              setBusy(false)
              setKickOpen(false)
            }}
          >
            {t('voice.kickConfirm')}
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
  const shown = (q ? audience.filter((x) => x.profile.name.toLowerCase().includes(q) || x.profile.city.toLowerCase().includes(q)) : audience)
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
          <Search className="pointer-events-none absolute top-1/2 left-3.5 size-4.5 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <TextInput id={`${id}-q`} value={query} onChange={(e) => setQuery(e.target.value)} placeholder={t('voice.searchPeople')} className="h-11 pl-10" type="search" autoComplete="off" />
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
  onReport: () => void
  onLeave: () => void
}) {
  const { t } = useI18n()
  const { st } = useSocialT()
  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={t('voice.options')}>
      <div className="flex flex-col pt-2 pb-4">
        <Row icon={<UserPlus />} label={st('invite.title')} onClick={onInvite} />
        {canShare ? <Row icon={<Link2 />} label={t('voice.share')} onClick={onShare} /> : null}
        {code ? <Row icon={<Copy />} label={t('room.copy', { code })} onClick={onCopyCode} /> : null}
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

/** Host: the room's title and its pinned announcement. */
export function SettingsSheet({ room, open, onClose }: { room: VoiceRoom; open: boolean; onClose: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { t } = useI18n()
  const id = useId()
  const [title, setTitle] = useState(room.title)
  const [note, setNote] = useState(room.announcement ?? '')
  const [lang, setLang] = useState(room.lang ?? '')
  const { st } = useSocialT()
  const [busy, setBusy] = useState(false)
  useEffect(() => {
    if (!open) return
    setTitle(room.title)
    setNote(room.announcement ?? '')
    setLang(room.lang ?? '')
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])
  const valid = title.trim().length >= 2 && title.trim().length <= 40
  const save = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!valid || busy) return
    setBusy(true)
    try {
      await backend.updateVoiceRoom(room.id, { title: title.trim(), announcement: note.trim(), lang: lang || undefined })
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
        <Field
          label={t('create.announcement')}
          htmlFor={`${id}-n`}
          hint={t('create.announcementHint')}
          trailing={<span className="text-xs tabular-nums text-muted-foreground">{note.length}/200</span>}
        >
          <TextArea id={`${id}-n`} value={note} maxLength={200} rows={3} placeholder={t('create.announcementPlaceholder')} onChange={(e) => setNote(e.target.value)} />
        </Field>
        <div className="flex items-center justify-between gap-4">
          <span className="text-sm font-semibold">{st('rooms.lang')}</span>
          <RoomLangSelect value={lang} onChange={setLang} label={st('rooms.lang')} anyLabel={lang ? undefined : '—'} className="w-48" />
        </div>
        <Button type="submit" variant="brand" size="lg" loading={busy} disabled={!valid}>
          {t('common.save')}
        </Button>
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
              className="flex w-full items-center gap-4 px-6 py-3 text-left transition-colors duration-150 hover:bg-muted disabled:opacity-60"
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
