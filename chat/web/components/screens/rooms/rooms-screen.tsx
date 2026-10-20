'use client'

import { AnimatePresence } from 'motion/react'
import * as m from 'motion/react-m'
import { lazy, Suspense, useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import useSWR from 'swr'
import { ArrowLeft, ArrowRight, Copy, KeyRound, Lock, MessagesSquare, Mic, Pin, Plus, Power } from 'lucide-react'
import type { Room, RoomMessage } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { errorCode, flagEmoji } from '@/lib/format'
import { onPageVisibility, pageHidden } from '@/lib/visibility'
import { useApp, useReported } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { EmptyState } from '@/components/ui/ambient'
import { Avatar } from '@/components/ui/avatar'
import { PresenceDot, Segmented, Skeleton } from '@/components/ui/bits'
import { VoiceRooms } from '@/components/screens/voice/voice-list'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { Composer } from '@/components/screens/chat/composer'
import { Bubble, MetaSpacer } from '@/components/screens/chat/message-bubble'
import { Modal } from '@/components/ui/modal'
import { refusalLabel, roomAbout, roomConditions, roomTitle, roomTopic } from './room-text'
import { TopicIcon, TopicTile } from '@/components/ui/topic-icons'
import { cn } from '@/lib/utils'
import { retryImport } from '@/lib/retry-import'

// The create / enter-code forms download when first opened.
const loadDialogs = () => retryImport(() => import('./room-dialogs'))
const CreateRoomDialog = lazy(() => loadDialogs().then((mod) => ({ default: mod.CreateRoomDialog })))
const JoinCodeDialog = lazy(() => loadDialogs().then((mod) => ({ default: mod.JoinCodeDialog })))
const loadVoiceCreate = () => retryImport(() => import('@/components/screens/voice/voice-create'))
const CreateVoiceDialog = lazy(() => loadVoiceCreate().then((mod) => ({ default: mod.CreateVoiceDialog })))

type Kind = 'voice' | 'chat'
/** The last Voice / Chat choice, for coming back to the tab in this visit. */
let lastKind: Kind = 'voice'

const FEATURED = 'lounge'
const HEARTBEAT_MS = 30_000 // the server counts a member as present for 90 s after the last join

/** A leave still on its way to the server, per room: re-entering waits for it so the join lands last. */
const leaving = new Map<string, Promise<unknown>>()

export function RoomsScreen({ openRoomId, onOpenRoom }: { openRoomId: string | null; onOpenRoom: (id: string | null) => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { t } = useI18n()
  const { data: rooms, error, mutate } = useSWR('rooms', () => backend.rooms(), { refreshInterval: 30_000 })
  const [createOpen, setCreateOpen] = useState(false)
  const [codeOpen, setCodeOpen] = useState(false)
  const [dialogsUsed, setDialogsUsed] = useState(false)
  const [voiceOpen, setVoiceOpen] = useState(false)
  const [voiceUsed, setVoiceUsed] = useState(false)
  const [kind, setKindState] = useState<Kind>(() => (openRoomId ? 'chat' : lastKind))
  const { openVoice } = useApp()
  const setKind = (k: Kind) => {
    lastKind = k
    setKindState(k)
  }
  const open = rooms?.find((r) => r.id === openRoomId) ?? null
  // A chat room opened from elsewhere (the side panel): coming back from it lands on the chat rooms.
  useEffect(() => {
    if (openRoomId) setKind('chat')
  }, [openRoomId])

  if (openRoomId && open) return <RoomChat room={open} onBack={() => onOpenRoom(null)} />

  const featured = rooms?.find((r) => r.id === FEATURED) ?? null
  const rest = rooms?.filter((r) => r.official && r.id !== FEATURED) ?? []
  const people = rooms?.filter((r) => !r.official).sort((a, b) => Number(b.mine) - Number(a.mine)) ?? []
  // A room I can't enter explains why instead of failing on the server.
  const enter = (r: Room) => (r.refusal ? toast.show(t(`error.${r.refusal}` as const), 'info') : onOpenRoom(r.id))
  const openCreate = () => {
    setDialogsUsed(true)
    setCreateOpen(true)
  }
  const startVoice = () => {
    setVoiceUsed(true)
    setVoiceOpen(true)
  }

  return (
    <div className="min-h-0 flex-1 overflow-y-auto scrollbar-thin">
      <div className="mx-auto flex max-w-4xl flex-col gap-8 px-4 py-6 sm:px-8 sm:py-10">
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div className="min-w-0">
            <h1 className="text-[28px] leading-tight font-bold tracking-tight">{t('rooms.title')}</h1>
            <p className="mt-1 text-sm text-muted-foreground">{kind === 'voice' ? t('voice.subtitle') : t('rooms.subtitle')}</p>
          </div>
          <div className="flex flex-wrap gap-2">
            {kind === 'voice' ? (
              <Button variant="brand" onPointerEnter={() => void loadVoiceCreate()} onClick={startVoice}>
                <Plus className="size-4.5" aria-hidden /> {t('voice.start')}
              </Button>
            ) : (
              <>
                <Button
                  variant="outline"
                  onPointerEnter={() => void loadDialogs()}
                  onClick={() => {
                    setDialogsUsed(true)
                    setCodeOpen(true)
                  }}
                >
                  <KeyRound className="size-4.5" aria-hidden /> {t('rooms.enterCode')}
                </Button>
                <Button variant="brand" onPointerEnter={() => void loadDialogs()} onClick={openCreate}>
                  <Plus className="size-4.5" aria-hidden /> {t('rooms.create')}
                </Button>
              </>
            )}
          </div>
        </header>

        <div id="rooms-kind" className="-mt-3">
          <Segmented
            className="w-full sm:w-72"
            label={t('rooms.kind')}
            value={kind}
            onChange={setKind}
            options={[
              { value: 'voice', label: <span className="flex items-center gap-1.5"><Mic className="size-4" aria-hidden /> {t('rooms.voice')}</span> },
              { value: 'chat', label: <span className="flex items-center gap-1.5"><MessagesSquare className="size-4" aria-hidden /> {t('rooms.chat')}</span> },
            ]}
          />
        </div>

        {kind === 'voice' ? (
          <VoiceRooms onStart={startVoice} />
        ) : error && !rooms ? (
          <EmptyState
            art="rooms"
            title={t('rooms.loadFailed')}
            body={t('common.checkConnection')}
            action={<Button variant="outline" onClick={() => void mutate()}>{t('common.tryAgain')}</Button>}
          />
        ) : !rooms ? (
          <>
            <Skeleton className="h-44 w-full rounded-3xl" />
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
              {Array.from({ length: 6 }, (_, i) => <Skeleton key={i} className="h-32 rounded-2xl" />)}
            </div>
          </>
        ) : (
          <>
            {featured ? <FeaturedRoom room={featured} onOpen={() => onOpenRoom(featured.id)} /> : null}
            <section aria-labelledby="rooms-people" className="flex flex-col gap-3">
              <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
                <h2 id="rooms-people" className="text-lg font-semibold">{t('rooms.byPeople')}</h2>
                <span className="text-xs text-muted-foreground">{t('rooms.byPeopleHint')}</span>
              </div>
              {people.length === 0 ? (
                <button
                  type="button"
                  onClick={openCreate}
                  className="flex w-full items-center gap-4 rounded-2xl border border-dashed border-input p-5 text-left transition-colors duration-150 hover:bg-muted"
                >
                  <span className="grid size-12 shrink-0 place-items-center rounded-full bg-primary-soft text-primary-text">
                    <Plus className="size-6" aria-hidden />
                  </span>
                  <span className="min-w-0">
                    <span className="block font-semibold">{t('rooms.noneTitle')}</span>
                    <span className="block text-sm text-muted-foreground">{t('rooms.noneBody')}</span>
                  </span>
                </button>
              ) : (
                <ul className="grid gap-3 sm:grid-cols-2">
                  {people.map((r) => (
                    <li key={r.id}>
                      <UserRoomCard room={r} onOpen={() => enter(r)} />
                    </li>
                  ))}
                </ul>
              )}
            </section>
            <section aria-labelledby="rooms-all" className="flex flex-col gap-3">
              <h2 id="rooms-all" className="text-lg font-semibold">{t('rooms.official')}</h2>
              <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                {rest.map((r) => {
                  const about = roomAbout(r, t)
                  return (
                    <li key={r.id}>
                      <button
                        type="button"
                        onClick={() => onOpenRoom(r.id)}
                        className="flex h-full w-full flex-col items-start gap-1 rounded-2xl border border-border bg-card p-4 text-left transition-[box-shadow,background-color] duration-150 hover:shadow-e2 active:bg-muted"
                      >
                        <TopicTile topic={roomTopic(r)} size={44} />
                        <span className="mt-2 text-[15px] leading-snug font-semibold">{roomTitle(r, t)}</span>
                        {about ? <span className="line-clamp-2 text-xs leading-relaxed text-muted-foreground">{about}</span> : null}
                        <span className="mt-auto flex items-center gap-1.5 pt-3 text-xs font-medium tabular-nums text-muted-foreground">
                          <PresenceDot /> <OnlineCount n={r.online} />
                        </span>
                      </button>
                    </li>
                  )
                })}
              </ul>
            </section>
          </>
        )}
      </div>
      {voiceUsed ? (
        <Suspense fallback={null}>
          <CreateVoiceDialog
            open={voiceOpen}
            onClose={() => setVoiceOpen(false)}
            onCreated={(id) => {
              setVoiceOpen(false)
              openVoice(id)
            }}
          />
        </Suspense>
      ) : null}
      {dialogsUsed ? (
        <Suspense fallback={null}>
          <CreateRoomDialog
            open={createOpen}
            onClose={() => setCreateOpen(false)}
            onCreated={(id, code) => {
              setCreateOpen(false)
              toast.show(t('rooms.created', { code }), 'success')
              onOpenRoom(id)
            }}
          />
          <JoinCodeDialog
            open={codeOpen}
            onClose={() => setCodeOpen(false)}
            onJoined={(id) => {
              setCodeOpen(false)
              onOpenRoom(id)
            }}
          />
        </Suspense>
      ) : null}
    </div>
  )
}

function OnlineCount({ n, max }: { n: number; max?: number | null }) {
  const { t, num } = useI18n()
  return <>{max ? t('rooms.onlineOf', { n: num(n), max }) : t('rooms.online', { n: num(n) })}</>
}

function UserRoomCard({ room, onOpen }: { room: Room; onOpen: () => void }) {
  const { t } = useI18n()
  const blocked = !!room.refusal
  const conditions = roomConditions(room, t)
  return (
    <button
      type="button"
      onClick={onOpen}
      aria-disabled={blocked}
      className={cn(
        'flex w-full items-center gap-4 rounded-2xl border bg-card p-4 text-left transition-[box-shadow,background-color] duration-150',
        blocked ? 'border-border opacity-60' : 'border-border hover:shadow-e2 active:bg-muted',
        room.mine && 'border-primary',
      )}
    >
      <TopicTile topic={roomTopic(room)} size={48} />
      <span className="min-w-0 flex-1">
        <span className="flex items-center gap-1.5 text-[15px] font-semibold">
          {room.isPrivate ? <Lock className="size-3.5 shrink-0" aria-label={t('rooms.private')} /> : null}
          <span className="truncate">{room.title}</span>
        </span>
        <span className="block truncate text-xs text-muted-foreground">
          {room.mine ? t('rooms.yours') : t('rooms.host', { name: room.ownerName ?? t('rooms.someone') })}
          {room.refusal ? ` · ${refusalLabel(room, t)}` : ''}
        </span>
        {conditions.length ? (
          <span className="mt-1.5 flex flex-wrap gap-1">
            {conditions.map((c) => (
              <span key={c} className="rounded-md bg-muted px-2 py-0.5 text-[11px] font-medium text-muted-foreground">
                {c}
              </span>
            ))}
          </span>
        ) : null}
      </span>
      <span className="flex shrink-0 items-center gap-1.5 text-xs font-medium tabular-nums text-muted-foreground">
        <PresenceDot /> {room.online}
        {room.maxMembers ? `/${room.maxMembers}` : ''}
      </span>
    </button>
  )
}

function FeaturedRoom({ room, onOpen }: { room: Room; onOpen: () => void }) {
  const { t, num } = useI18n()
  return (
    <button
      type="button"
      onClick={onOpen}
      className="group relative overflow-hidden rounded-3xl bg-primary-soft p-6 text-left text-foreground transition-shadow duration-150 hover:shadow-e2 sm:p-8"
    >
      <span className="flex items-start justify-between gap-4">
        <span className="grid size-14 place-items-center rounded-full bg-card" aria-hidden>
          <TopicIcon topic={roomTopic(room)} size={30} />
        </span>
        <span className="flex items-center gap-1.5 rounded-full bg-card px-3 py-1.5 text-xs font-medium text-muted-foreground tabular-nums">
          <PresenceDot /> {t('rooms.hereNow', { n: num(room.online) })}
        </span>
      </span>
      <span className="mt-6 block text-2xl font-bold tracking-tight sm:text-3xl">{roomTitle(room, t)}</span>
      <span className="mt-1 block max-w-sm text-sm leading-relaxed text-muted-foreground">{roomAbout(room, t)}</span>
      <span className="mt-6 inline-flex h-10 items-center gap-2 rounded-full bg-primary px-5 text-sm font-semibold text-primary-foreground transition-colors duration-150 group-hover:bg-primary-hover">
        {t('rooms.join')} <ArrowRight className="size-4 rtl:rotate-180" aria-hidden />
      </span>
    </button>
  )
}

function RoomChat({ room, onBack }: { room: Room; onBack: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { t, num, clock } = useI18n()
  const { openProfile } = useApp()
  const [messages, setMessages] = useState<RoomMessage[] | null>(null)
  const [joined, setJoined] = useState(false)
  const [closed, setClosed] = useState(false)
  const [confirmClose, setConfirmClose] = useState(false)
  const [closing, setClosing] = useState(false)
  const listRef = useRef<HTMLUListElement>(null)
  const stick = useRef(true)
  const conditions = room.official ? [] : roomConditions(room, t, false)
  const title = roomTitle(room, t)

  const copyCode = async () => {
    if (!room.code) return
    try {
      await navigator.clipboard.writeText(room.code)
      toast.show(t('room.copied', { code: room.code }), 'success')
    } catch {
      toast.show(t('room.codeIs', { code: room.code }))
    }
  }

  const closeRoom = async () => {
    setClosing(true)
    try {
      await backend.closeRoom(room.id)
      toast.show(t('room.closedToast'), 'success')
      onBack()
    } catch (err) {
      toast.error(err)
    } finally {
      setClosing(false)
      setConfirmClose(false)
    }
  }

  const add = useCallback((list: RoomMessage[]) => {
    setMessages((prev) => {
      const current = prev ?? []
      const ids = new Set(current.map((m) => m.id))
      const added = list.filter((m) => !ids.has(m.id))
      return added.length ? [...current, ...added].sort((a, b) => a.id - b.id) : current
    })
  }, [])

  // Enter the room (the server only lets members read and post) and stay listed as present while it is open.
  // The heartbeat pauses while the browser tab is hidden and runs at once when it is shown again.
  useEffect(() => {
    let alive = true
    let inside = false
    let beat: ReturnType<typeof setInterval> | undefined
    const heartbeat = () => {
      if (!inside || pageHidden()) return
      void backend.joinRoom(room.id).catch((err) => alive && errorCode(err) === 'room_closed' && setClosed(true))
    }
    ;(async () => {
      await leaving.get(room.id)
      if (!alive) return
      try {
        await backend.joinRoom(room.id)
      } catch (err) {
        if (!alive) return
        toast.error(err) // banned, or the room is gone: nothing to do here
        onBack()
        return
      }
      if (!alive) return
      inside = true
      setJoined(true)
      beat = setInterval(heartbeat, HEARTBEAT_MS)
      // Messages are only readable once inside (the server checks presence), so history comes after the join.
      backend.roomHistory(room.id).then(
        (h) => alive && add(h),
        (err) => {
          if (!alive) return
          setMessages((m) => m ?? [])
          toast.error(err)
        },
      )
    })()
    const unsubscribe = backend.subscribeRoom(room.id, (m) => alive && add([m]))
    const stopWatching = onPageVisibility((visible) => visible && heartbeat())
    return () => {
      alive = false
      clearInterval(beat)
      stopWatching()
      unsubscribe()
      const done = backend.leaveRoom(room.id).catch(() => undefined)
      leaving.set(room.id, done)
      void done.then(() => leaving.get(room.id) === done && leaving.delete(room.id))
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [backend, room.id, add, toast])

  // Someone reported from this room is blocked: their messages go at once (the server hides them from now on).
  useReported((id) => setMessages((list) => list?.filter((m) => m.sender.id !== id) ?? list))

  useLayoutEffect(() => {
    const el = listRef.current
    if (el && stick.current) el.scrollTop = el.scrollHeight
  }, [messages])

  const send = async (text: string | null) => {
    if (!text) return false
    try {
      try {
        add([await backend.sendRoom(room.id, text)])
      } catch (err) {
        // Presence lapsed (the laptop slept, timers were throttled): the server no longer counts us as in the room.
        if (errorCode(err) !== 'blocked') throw err
        await backend.joinRoom(room.id)
        add([await backend.sendRoom(room.id, text)])
      }
      stick.current = true
      return true
    } catch (err) {
      if (errorCode(err) === 'room_closed') setClosed(true) // the host closed it while we were inside
      toast.error(err)
      return false
    }
  }

  return (
    <m.div
      className="flex min-h-0 flex-1 flex-col"
      initial={{ opacity: 0, x: 24 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ type: 'tween', duration: 0.2, ease: [0.2, 0, 0, 1] }}
    >
      <header className="flex items-center gap-3 border-b border-border px-3 py-2.5 sm:px-5">
        <Button size="icon" variant="ghost" onClick={onBack} aria-label={t('room.back')}>
          <ArrowLeft className="size-5" aria-hidden />
        </Button>
        <TopicTile topic={roomTopic(room)} size={40} />
        <span className="min-w-0 flex-1">
          <h1 className="flex items-center gap-1.5 text-[15px] font-semibold">
            {room.isPrivate ? <Lock className="size-3.5 shrink-0" aria-label={t('rooms.private')} /> : null}
            <span className="truncate">{title}</span>
          </h1>
          <span className="flex items-center gap-1.5 truncate text-xs text-muted-foreground tabular-nums">
            <PresenceDot /> <OnlineCount n={joined ? Math.max(room.online, 1) : room.online} max={room.maxMembers} />
            {room.official ? '' : room.mine ? ` · ${t('room.youHost')}` : ` · ${t('room.hostName', { name: room.ownerName ?? '' })}`}
          </span>
        </span>
        {room.mine && room.code ? (
          <>
            <button
              type="button"
              onClick={() => void copyCode()}
              className="flex h-10 items-center gap-2 rounded-full bg-muted px-3.5 font-mono text-sm font-bold tracking-widest transition-colors duration-150 hover:bg-input/60"
              aria-label={t('room.copy', { code: room.code })}
              dir="ltr"
            >
              {room.code} <Copy className="size-4" aria-hidden />
            </button>
            <Button size="icon" variant="ghost" onClick={() => setConfirmClose(true)} aria-label={t('room.close')} className="text-muted-foreground hover:text-destructive">
              <Power className="size-5" aria-hidden />
            </Button>
          </>
        ) : null}
      </header>
      <p className="flex flex-wrap items-center justify-center gap-1.5 bg-muted px-4 py-2 text-center text-xs text-muted-foreground">
        <Pin className="size-3.5 shrink-0" aria-hidden />
        {room.isPrivate ? t('room.privateNote') : t('room.publicNote')}
        {conditions.map((c) => (
          <span key={c} className="rounded-md bg-card px-2 py-0.5 font-medium">
            {c}
          </span>
        ))}
      </p>
      {closed ? (
        <p className="bg-primary-soft px-4 py-2 text-center text-sm font-semibold text-primary-text" role="status">
          {t('room.closedBanner')}
        </p>
      ) : null}

      <ul
        ref={listRef}
        onScroll={(e) => {
          const el = e.currentTarget
          stick.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80
        }}
        className="flex w-full min-h-0 flex-1 flex-col overflow-y-auto bg-chat-wall px-[max(1.25rem,calc((100%-48rem)/2))] py-3 scrollbar-thin"
        aria-label={t('room.messages', { title })}
        aria-live="polite"
      >
        {messages === null ? (
          Array.from({ length: 5 }, (_, i) => (
            <li key={i} className="flex gap-3">
              <Skeleton className="size-9 rounded-full" />
              <Skeleton className="h-14 w-56 rounded-lg" />
            </li>
          ))
        ) : messages.length === 0 ? (
          <li className="m-auto">
            <EmptyState art="chat" title={t('room.quiet')} body={t('room.first')} />
          </li>
        ) : (
          <AnimatePresence initial={false}>
            {messages.map((msg, i) => {
              const grouped = i > 0 && messages[i - 1].sender.id === msg.sender.id
              const meta = clock(msg.at)
              return (
                <m.li
                  key={msg.id}
                  initial={{ opacity: 0, y: 6 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ type: 'tween', duration: 0.15, ease: [0.2, 0, 0, 1] }}
                  className={cn('flex max-w-[85%] gap-2 md:max-w-[65%]', msg.fromMe ? 'self-end' : 'self-start', grouped ? 'mt-0.5' : 'mt-2')}
                >
                  {msg.fromMe ? null : grouped ? (
                    <span className="w-8 shrink-0" aria-hidden />
                  ) : (
                    <button type="button" onClick={() => openProfile(msg.sender.id)} className="shrink-0 self-start rounded-full" aria-label={t('room.view', { name: msg.sender.name })}>
                      <Avatar name={msg.sender.name} src={msg.sender.photoUrl} size={32} />
                    </button>
                  )}
                  <Bubble mine={msg.fromMe} tail={!grouped} className="px-2.5 pb-1.5 pt-1">
                    {msg.fromMe || grouped ? null : (
                      <button
                        type="button"
                        onClick={() => openProfile(msg.sender.id)}
                        className="flex max-w-full items-center gap-1.5 text-[13px] font-semibold leading-5 hover:underline"
                        style={{ color: nameColor(msg.sender.id) }}
                      >
                        <span className="truncate">{msg.sender.name}</span>
                        <span className="font-normal text-bubble-meta">{msg.sender.age}</span>
                        <span aria-hidden>{flagEmoji(msg.sender.country)}</span>
                      </button>
                    )}
                    <p className={cn('relative text-[15px] leading-[1.35] break-words whitespace-pre-wrap', msg.fromMe || grouped ? 'pt-0.5' : '')}>
                      {msg.text}
                      <MetaSpacer>{meta}</MetaSpacer>
                      <span className="absolute -bottom-0.5 right-0 text-[11px] leading-none text-bubble-meta">{meta}</span>
                    </p>
                  </Bubble>
                </m.li>
              )
            })}
          </AnimatePresence>
        )}
      </ul>
      <Composer
        onSend={send}
        allowMedia={false}
        disabled={!joined || closed}
        disabledPlaceholder={closed ? t('room.closedPlaceholder') : t('room.joining')}
        placeholder={t('room.messageTo', { title })}
        maxLength={500}
      />
      <Modal open={confirmClose} onClose={() => setConfirmClose(false)} title={t('room.confirmTitle')} description={t('room.confirmBody')}>
        <div className="flex gap-3 p-6 pt-5">
          <Button variant="outline" className="flex-1" onClick={() => setConfirmClose(false)}>
            {t('room.keepOpen')}
          </Button>
          <Button variant="danger" className="flex-1" loading={closing} onClick={() => void closeRoom()}>
            {t('room.close')}
          </Button>
        </div>
      </Modal>
    </m.div>
  )
}

/** A steady colour per person, like names in a group chat. */
function nameColor(id: string) {
  let h = 0
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) | 0
  return `var(--name-${Math.abs(h) % 8})`
}
