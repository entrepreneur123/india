'use client'

import { AnimatePresence, motion } from 'motion/react'
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import useSWR from 'swr'
import { ArrowLeft, ArrowRight, Copy, KeyRound, Lock, Pin, Plus, Power } from 'lucide-react'
import { BackendError, type Room, type RoomMessage } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { compactNumber, errorCode, flagEmoji, friendlyError } from '@/lib/format'
import { useApp, useReported } from '@/components/app-state'
import { EmptyState } from '@/components/ui/ambient'
import { Avatar } from '@/components/ui/avatar'
import { PresenceDot, Skeleton } from '@/components/ui/bits'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { Composer } from '@/components/screens/chat/composer'
import { Modal } from '@/components/ui/modal'
import { CreateRoomDialog, JoinCodeDialog, roomConditions } from './room-dialogs'
import { cn } from '@/lib/utils'

const FEATURED = 'lounge'
const HEARTBEAT_MS = 30_000 // the server counts a member as present for 90 s after the last join

/** A leave still on its way to the server, per room: re-entering waits for it so the join lands last. */
const leaving = new Map<string, Promise<unknown>>()

/** One line on what each server room is for; rooms without one just show their title. */
const ABOUT: Record<string, string> = {
  lounge: 'Everyone, everywhere. Say hi to the world.',
  language: 'Practise a language with native speakers.',
  music: 'Share what’s on repeat.',
  movies: 'Recommendations, reviews, no spoilers.',
  gaming: 'Find people to play with.',
  sports: 'Match talk, live.',
  night: 'For when you can’t sleep.',
  memes: 'Post the funniest thing you saw today.',
  study: 'Study together, ask for help.',
  travel: 'Tips, stories and places to go.',
}

const TINTS = [
  'bg-[#fff1f3] hover:shadow-[0_18px_40px_-18px_rgb(255_61_104/0.45)]',
  'bg-[#fff4ec] hover:shadow-[0_18px_40px_-18px_rgb(255_122_69/0.45)]',
  'bg-[#eef4ff] hover:shadow-[0_18px_40px_-18px_rgb(80_120_255/0.4)]',
  'bg-[#effaf3] hover:shadow-[0_18px_40px_-18px_rgb(34_197_94/0.4)]',
  'bg-[#f5f0ff] hover:shadow-[0_18px_40px_-18px_rgb(139_92_246/0.4)]',
  'bg-[#fffbea] hover:shadow-[0_18px_40px_-18px_rgb(234_179_8/0.4)]',
]

export function RoomsScreen({ openRoomId, onOpenRoom }: { openRoomId: string | null; onOpenRoom: (id: string | null) => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { t } = useApp()
  const { data: rooms, error, mutate } = useSWR('rooms', () => backend.rooms(), { refreshInterval: 30_000 })
  const [createOpen, setCreateOpen] = useState(false)
  const [codeOpen, setCodeOpen] = useState(false)
  const open = rooms?.find((r) => r.id === openRoomId) ?? null

  if (openRoomId && open) return <RoomChat room={open} onBack={() => onOpenRoom(null)} />

  const featured = rooms?.find((r) => r.id === FEATURED) ?? null
  const rest = rooms?.filter((r) => r.official && r.id !== FEATURED) ?? []
  const people = rooms?.filter((r) => !r.official).sort((a, b) => Number(b.mine) - Number(a.mine)) ?? []
  // A room I can't enter explains why instead of failing on the server.
  const enter = (r: Room) => (r.refusal ? toast.show(friendlyError(new BackendError(r.refusal)), 'info') : onOpenRoom(r.id))

  return (
    <div className="min-h-0 flex-1 overflow-y-auto scrollbar-thin">
      <div className="mx-auto flex max-w-4xl flex-col gap-7 px-4 py-6 sm:px-8 sm:py-10">
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <h1 className="text-3xl font-extrabold tracking-tight">{t('rooms.title')}</h1>
            <p className="mt-1 text-sm text-muted-foreground">{t('rooms.subtitle')}</p>
          </div>
          <div className="flex gap-2">
            <Button variant="secondary" onClick={() => setCodeOpen(true)}>
              <KeyRound className="size-4.5" aria-hidden /> Enter code
            </Button>
            <Button variant="brand" onClick={() => setCreateOpen(true)}>
              <Plus className="size-4.5" aria-hidden /> Create room
            </Button>
          </div>
        </header>

        {error && !rooms ? (
          <EmptyState
            art="rooms"
            title="Couldn’t load rooms"
            body="Check your connection and try again."
            action={<Button variant="secondary" onClick={() => void mutate()}>Try again</Button>}
          />
        ) : !rooms ? (
          <>
            <Skeleton className="h-48 w-full rounded-[28px]" />
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
              {Array.from({ length: 6 }, (_, i) => <Skeleton key={i} className="h-36 rounded-3xl" />)}
            </div>
          </>
        ) : (
          <>
            {featured ? <FeaturedRoom room={featured} onOpen={() => onOpenRoom(featured.id)} /> : null}
            <section aria-labelledby="rooms-people">
              <div className="mb-3 flex items-baseline justify-between">
                <h2 id="rooms-people" className="text-lg font-bold tracking-tight">Rooms by people</h2>
                <span className="text-xs text-muted-foreground">Open to anyone who fits the host’s rules</span>
              </div>
              {people.length === 0 ? (
                <button
                  type="button"
                  onClick={() => setCreateOpen(true)}
                  className="flex w-full items-center gap-4 rounded-3xl border-2 border-dashed border-input p-5 text-left transition-colors hover:border-primary hover:bg-primary/5"
                >
                  <span className="grid size-12 place-items-center rounded-2xl bg-brand text-primary-foreground">
                    <Plus className="size-6" aria-hidden />
                  </span>
                  <span>
                    <span className="block font-bold">No rooms by people right now</span>
                    <span className="block text-sm text-muted-foreground">Open one: pick a topic, who can join and how many.</span>
                  </span>
                </button>
              ) : (
                <ul className="grid gap-2 sm:grid-cols-2">
                  {people.map((r, i) => (
                    <motion.li
                      key={r.id}
                      initial={{ opacity: 0, y: 12 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: 0.03 * i, type: 'spring', stiffness: 320, damping: 28 }}
                    >
                      <UserRoomCard room={r} onOpen={() => enter(r)} />
                    </motion.li>
                  ))}
                </ul>
              )}
            </section>
            <section aria-labelledby="rooms-all">
              <h2 id="rooms-all" className="mb-3 text-lg font-bold tracking-tight">Official rooms</h2>
              <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                {rest.map((r, i) => (
                  <motion.li
                    key={r.id}
                    initial={{ opacity: 0, y: 16 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: 0.04 * i, type: 'spring', stiffness: 300, damping: 26 }}
                  >
                    <motion.button
                      type="button"
                      onClick={() => onOpenRoom(r.id)}
                      whileHover={{ y: -4 }}
                      whileTap={{ scale: 0.97 }}
                      transition={{ type: 'spring', stiffness: 400, damping: 24 }}
                      className={cn(
                        'group flex h-full w-full flex-col items-start gap-1 rounded-3xl p-4 text-left text-[#0d0c11] transition-shadow sm:p-5',
                        TINTS[i % TINTS.length],
                      )}
                    >
                      <span className="text-3xl transition-transform duration-300 group-hover:scale-115 group-hover:-rotate-6" aria-hidden>
                        {r.emoji}
                      </span>
                      <span className="mt-2 text-[15px] font-bold leading-tight">{r.title}</span>
                      {ABOUT[r.id] ? <span className="line-clamp-2 text-xs leading-relaxed text-[#6b6a75]">{ABOUT[r.id]}</span> : null}
                      <span className="mt-auto flex items-center gap-1.5 pt-3 text-xs font-semibold tabular-nums text-[#3f3e47]">
                        <PresenceDot /> {compactNumber(r.online)} online
                      </span>
                    </motion.button>
                  </motion.li>
                ))}
              </ul>
            </section>
          </>
        )}
      </div>
      <CreateRoomDialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={(id, code) => {
          setCreateOpen(false)
          toast.show(`Room created · code ${code}`, 'success')
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
    </div>
  )
}

function UserRoomCard({ room, onOpen }: { room: Room; onOpen: () => void }) {
  const blocked = !!room.refusal
  const conditions = roomConditions(room)
  return (
    <motion.button
      type="button"
      onClick={onOpen}
      whileHover={blocked ? undefined : { y: -3 }}
      whileTap={{ scale: 0.98 }}
      aria-disabled={blocked}
      className={cn(
        'group flex w-full items-center gap-4 rounded-3xl bg-card p-4 text-left ring-1 ring-border transition-shadow',
        blocked ? 'opacity-55' : 'hover:shadow-[0_16px_40px_-20px_rgb(255_61_104/0.5)]',
        room.mine && 'ring-2 ring-primary/40',
      )}
    >
      <span className="grid size-14 shrink-0 place-items-center rounded-2xl bg-muted text-3xl transition-transform group-hover:scale-105" aria-hidden>
        {room.emoji}
      </span>
      <span className="min-w-0 flex-1">
        <span className="flex items-center gap-1.5 truncate text-[15px] font-bold">
          {room.isPrivate ? <Lock className="size-3.5 shrink-0" aria-label="Private" /> : null}
          <span className="truncate">{room.title}</span>
        </span>
        <span className="block truncate text-xs text-muted-foreground">
          {room.mine ? 'Your room' : `Host: ${room.ownerName ?? 'someone'}`}
          {room.refusal ? ` · ${refusalLabel(room)}` : ''}
        </span>
        {conditions.length ? (
          <span className="mt-1.5 flex flex-wrap gap-1">
            {conditions.map((c) => (
              <span key={c} className="rounded-full bg-muted px-2 py-0.5 text-[11px] font-semibold text-muted-foreground">
                {c}
              </span>
            ))}
          </span>
        ) : null}
      </span>
      <span className="flex shrink-0 items-center gap-1.5 text-xs font-semibold tabular-nums text-muted-foreground">
        <PresenceDot /> {room.online}
        {room.maxMembers ? `/${room.maxMembers}` : ''}
      </span>
    </motion.button>
  )
}

function refusalLabel(r: Room) {
  switch (r.refusal) {
    case 'room_full': return 'Full'
    case 'room_gender': return r.genderRule === 'female' ? 'Women only' : 'Men only'
    case 'room_age': return 'Outside age range'
    case 'room_closed': return 'Closed'
    default: return 'Not available'
  }
}

function FeaturedRoom({ room, onOpen }: { room: Room; onOpen: () => void }) {
  return (
    <motion.button
      type="button"
      onClick={onOpen}
      initial={{ opacity: 0, y: 18 }}
      animate={{ opacity: 1, y: 0 }}
      whileHover={{ y: -4, scale: 1.005 }}
      whileTap={{ scale: 0.985 }}
      transition={{ type: 'spring', stiffness: 300, damping: 24 }}
      className="group relative isolate overflow-hidden rounded-[28px] p-6 text-left text-primary-foreground shadow-[0_24px_60px_-24px_rgb(255_61_104/0.8)] sm:p-8"
      style={{ backgroundImage: 'linear-gradient(120deg, #ff3d68, #ff7a45, #ff3d68)', backgroundSize: '200% 200%' }}
    >
      <span className="absolute inset-0 -z-10 animate-pan" style={{ backgroundImage: 'inherit', backgroundSize: 'inherit' }} aria-hidden />
      <span className="absolute -top-20 -right-16 -z-10 size-72 rounded-full bg-white/20 blur-3xl transition-transform duration-700 group-hover:scale-125" aria-hidden />
      <span className="flex items-start justify-between gap-4">
        <motion.span
          className="text-5xl"
          animate={{ rotate: [0, 8, -6, 0] }}
          transition={{ duration: 6, repeat: Infinity, ease: 'easeInOut' }}
          aria-hidden
        >
          {room.emoji}
        </motion.span>
        <span className="flex items-center gap-1.5 rounded-full bg-white/20 px-3 py-1.5 text-xs font-bold tabular-nums backdrop-blur-md">
          <PresenceDot /> {compactNumber(room.online)} here now
        </span>
      </span>
      <span className="mt-6 block text-3xl font-extrabold tracking-tight sm:text-4xl">{room.title}</span>
      <span className="mt-1 block max-w-sm text-sm leading-relaxed text-white/85">{ABOUT[room.id]}</span>
      <span className="mt-6 inline-flex h-11 items-center gap-2 rounded-full bg-white px-5 text-sm font-bold text-[#0d0c11] shadow-lg transition-transform group-hover:translate-x-1">
        Join <ArrowRight className="size-4" aria-hidden />
      </span>
    </motion.button>
  )
}

function RoomChat({ room, onBack }: { room: Room; onBack: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { openProfile } = useApp()
  const [messages, setMessages] = useState<RoomMessage[] | null>(null)
  const [joined, setJoined] = useState(false)
  const [closed, setClosed] = useState(false)
  const [confirmClose, setConfirmClose] = useState(false)
  const [closing, setClosing] = useState(false)
  const listRef = useRef<HTMLUListElement>(null)
  const stick = useRef(true)
  const conditions = room.official ? [] : roomConditions(room)

  const copyCode = async () => {
    if (!room.code) return
    try {
      await navigator.clipboard.writeText(room.code)
      toast.show(`Code ${room.code} copied`, 'success')
    } catch {
      toast.show(`Room code: ${room.code}`)
    }
  }

  const closeRoom = async () => {
    setClosing(true)
    try {
      await backend.closeRoom(room.id)
      toast.show('Room closed', 'success')
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

  // Enter the room (the server only lets members post) and stay listed as present while it is open.
  useEffect(() => {
    let alive = true
    let beat: ReturnType<typeof setInterval> | undefined
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
      setJoined(true)
      beat = setInterval(
        () => void backend.joinRoom(room.id).catch((err) => alive && errorCode(err) === 'room_closed' && setClosed(true)),
        HEARTBEAT_MS,
      )
      // History only after joining: the server shows a room's messages to the people in it.
      try {
        const h = await backend.roomHistory(room.id)
        if (alive) add(h)
      } catch (err) {
        if (!alive) return
        setMessages((m) => m ?? [])
        toast.error(err)
      }
    })()
    const unsubscribe = backend.subscribeRoom(room.id, (m) => alive && add([m]))
    return () => {
      alive = false
      clearInterval(beat)
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
    <motion.div
      className="flex min-h-0 flex-1 flex-col"
      initial={{ opacity: 0, x: 30 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ type: 'spring', stiffness: 360, damping: 34 }}
    >
      <header className="flex items-center gap-3 border-b border-border px-3 py-2.5 sm:px-5">
        <Button size="icon" variant="ghost" onClick={onBack} aria-label="Back to rooms">
          <ArrowLeft className="size-5" aria-hidden />
        </Button>
        <span className="grid size-11 place-items-center rounded-2xl bg-muted text-2xl" aria-hidden>
          {room.emoji}
        </span>
        <span className="min-w-0 flex-1">
          <h1 className="flex items-center gap-1.5 truncate text-[15px] font-bold">
            {room.isPrivate ? <Lock className="size-3.5 shrink-0" aria-label="Private" /> : null}
            <span className="truncate">{room.title}</span>
          </h1>
          <span className="flex items-center gap-1.5 truncate text-xs text-muted-foreground tabular-nums">
            <PresenceDot /> {compactNumber(room.online)}
            {room.maxMembers ? `/${room.maxMembers}` : ''} online
            {room.official ? '' : room.mine ? ' · you host' : ` · host ${room.ownerName ?? ''}`}
          </span>
        </span>
        {room.mine && room.code ? (
          <>
            <button
              type="button"
              onClick={() => void copyCode()}
              className="flex h-10 items-center gap-2 rounded-full bg-muted px-3.5 font-mono text-sm font-bold tracking-widest transition-colors hover:bg-input"
              aria-label={`Copy room code ${room.code}`}
            >
              {room.code} <Copy className="size-4" aria-hidden />
            </button>
            <Button size="icon" variant="ghost" onClick={() => setConfirmClose(true)} aria-label="Close room" className="text-muted-foreground hover:text-destructive">
              <Power className="size-5" aria-hidden />
            </Button>
          </>
        ) : null}
      </header>
      <p className="flex flex-wrap items-center justify-center gap-1.5 bg-muted/60 px-4 py-2 text-center text-xs text-muted-foreground">
        <Pin className="size-3.5 shrink-0" aria-hidden />
        {room.isPrivate ? 'Private room — only people with the code can join.' : 'Anyone here can read what you write. Be kind.'}
        {conditions.filter((c) => c !== 'Private').map((c) => (
          <span key={c} className="rounded-full bg-card px-2 py-0.5 font-semibold ring-1 ring-border">
            {c}
          </span>
        ))}
      </p>
      {closed ? (
        <p className="bg-destructive/10 px-4 py-2 text-center text-sm font-semibold text-destructive" role="status">
          This room was closed by its host.
        </p>
      ) : null}

      <ul
        ref={listRef}
        onScroll={(e) => {
          const el = e.currentTarget
          stick.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80
        }}
        className="mx-auto flex w-full max-w-3xl min-h-0 flex-1 flex-col gap-3 overflow-y-auto px-4 py-4 scrollbar-thin sm:px-6"
        aria-label={`${room.title} messages`}
        aria-live="polite"
      >
        {messages === null ? (
          Array.from({ length: 5 }, (_, i) => (
            <li key={i} className="flex gap-3">
              <Skeleton className="size-9 rounded-full" />
              <Skeleton className="h-14 w-56 rounded-3xl" />
            </li>
          ))
        ) : messages.length === 0 ? (
          <li className="m-auto">
            <EmptyState art="chat" title="It’s quiet in here" body="Be the first to say something." />
          </li>
        ) : (
          <AnimatePresence initial={false}>
            {messages.map((m) => (
              <motion.li
                key={m.id}
                layout="position"
                initial={{ opacity: 0, y: 12, scale: 0.96 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                transition={{ type: 'spring', stiffness: 420, damping: 30 }}
                className={cn('flex max-w-[85%] gap-2.5', m.fromMe ? 'self-end flex-row-reverse' : 'self-start')}
              >
                {m.fromMe ? null : (
                  <button type="button" onClick={() => openProfile(m.sender.id)} className="self-end rounded-full" aria-label={`View ${m.sender.name}`}>
                    <Avatar name={m.sender.name} src={m.sender.photoUrl} size={36} />
                  </button>
                )}
                <div className={cn('flex min-w-0 flex-col gap-1', m.fromMe && 'items-end')}>
                  {m.fromMe ? null : (
                    <button
                      type="button"
                      onClick={() => openProfile(m.sender.id)}
                      className="flex items-center gap-1.5 self-start px-1 text-xs font-semibold hover:underline"
                    >
                      {m.sender.name}
                      <span className="font-normal text-muted-foreground">{m.sender.age}</span>
                      <span aria-hidden>{flagEmoji(m.sender.country)}</span>
                    </button>
                  )}
                  <p
                    className={cn(
                      'rounded-3xl px-4 py-2.5 text-[15px] leading-relaxed break-words whitespace-pre-wrap',
                      m.fromMe ? 'rounded-br-lg bg-brand text-primary-foreground' : 'rounded-bl-lg bg-muted',
                    )}
                  >
                    {m.text}
                  </p>
                </div>
              </motion.li>
            ))}
          </AnimatePresence>
        )}
      </ul>
      <Composer
        onSend={send}
        allowMedia={false}
        disabled={!joined || closed}
        disabledPlaceholder={closed ? 'This room was closed' : 'Joining…'}
        placeholder={`Message ${room.title}`}
        maxLength={500}
      />
      <Modal
        open={confirmClose}
        onClose={() => setConfirmClose(false)}
        title="Close this room?"
        description="Everyone in it can no longer write, and it disappears from the list. This can’t be undone."
      >
        <div className="flex gap-3 p-6 pt-5">
          <Button variant="secondary" className="flex-1" onClick={() => setConfirmClose(false)}>
            Keep open
          </Button>
          <Button variant="danger" className="flex-1" loading={closing} onClick={() => void closeRoom()}>
            Close room
          </Button>
        </div>
      </Modal>
    </motion.div>
  )
}
