'use client'

import { AnimatePresence } from 'motion/react'
import * as m from 'motion/react-m'
import { lazy, Suspense, useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { ChevronDown, ChevronRight, Copy, EllipsisVertical, Headphones, LogOut, Mic, MicOff, Pencil, Pin, SmilePlus, Users } from 'lucide-react'
import { useI18n } from '@/components/i18n'
import { Avatar } from '@/components/ui/avatar'
import { Skeleton } from '@/components/ui/bits'
import { Button } from '@/components/ui/pressable'
import { ReactionIcon, TopicChip, TopicIcon } from '@/components/ui/topic-icons'
import { useToast } from '@/components/ui/toast'
import { categoryLabel, kindColor, kindTopic } from '@/components/screens/rooms/room-text'
import { Modal } from '@/components/ui/modal'
import { useBackend } from '@/lib/backend-context'
import { retryImport } from '@/lib/retry-import'
import { cn } from '@/lib/utils'
import { VOICE_SEATS, type LudoGame, type VoiceRoom } from '@/lib/backend'
import { useLudoT } from './ludo/ludo-text'
import { Leaderboard } from './room-leaders'
import { VoiceChat, VoiceComposer } from './voice-chat'
import { MusicHero, MusicPanel, QueueSheet } from './voice-music'
import type { VoiceSession } from './voice-session'
import { AudienceSheet, OptionsSheet, PersonSheet, ReportRoomSheet, RequestsSheet, SeatSheet, SettingsSheet, type SheetState } from './voice-sheets'
import { ReactionLayer, SeatPerson, Stage } from './voice-stage'
import { isMod, REACTIONS } from './voice-text'

// The game is its own chunk, fetched when a room has one (or is a games room).
const loadLudo = () => retryImport(() => import('./ludo/ludo-panel'))
const LudoPanel = lazy(() => loadLudo().then((mod) => ({ default: mod.LudoPanel })))
const StartLudoCard = lazy(() => retryImport(() => import('./ludo/ludo-start')).then((mod) => ({ default: mod.StartLudoCard })))

function useWide() {
  const [wide, setWide] = useState(false)
  useEffect(() => {
    const mq = window.matchMedia('(min-width: 1024px)')
    const update = () => setWide(mq.matches)
    update()
    mq.addEventListener('change', update)
    return () => mq.removeEventListener('change', update)
  }, [])
  return wide
}

/** The first free, unlocked stage seat (open rooms take it at once). */
const freeSeat = (room: VoiceRoom) => {
  const taken = new Set(room.members.map((x) => x.seat))
  return room.seats.find((s) => s.index > 0 && s.index < VOICE_SEATS && !s.locked && !taken.has(s.index))?.index ?? null
}

/**
 * A room, full screen and always dark. Every room has the chat (text, icebreakers, photos), the stage, shared music
 * and Ludo; the category decides what comes first:
 *  - talk: the chat, under a slim strip of the people on the stage;
 *  - music: the shared player with the song requests by vote;
 *  - games: Ludo (or a card to start it) with the room's leaderboard.
 * Phones: that hero on top, the chat below, the controls at the bottom. Wide screens: the hero in the middle and the
 * chat on the right (talk rooms keep the chat in the middle).
 */
export function VoiceRoomScreen({ s, onMinimise, onLeave }: { s: VoiceSession; onMinimise: () => void; onLeave: () => void }) {
  const { t, num } = useI18n()
  const toast = useToast()
  const wide = useWide()
  const [sheet, setSheet] = useState<SheetState>(null)
  const [stageOpen, setStageOpen] = useState(false)
  const [musicOpen, setMusicOpen] = useState(false)
  const { room, me } = s
  const ludo = useLudo(s)

  if (!room || !me)
    return (
      <div className="theme-night relative flex min-h-0 flex-1 flex-col bg-background text-foreground" aria-busy>
        <header className="flex items-center gap-2 px-2 py-2">
          <Button size="icon" variant="ghost" onClick={onMinimise} aria-label={t('voice.minimise')}>
            <ChevronDown className="size-6" aria-hidden />
          </Button>
          <Skeleton className="h-5 w-48" />
        </header>
        <div className="flex flex-col gap-4 px-4 pt-4">
          <Skeleton className="h-16 w-full rounded-2xl" />
          {Array.from({ length: 4 }, (_, i) => (
            <div key={i} className="flex gap-2">
              <Skeleton className="size-7 rounded-full" />
              <Skeleton className="h-10 w-48 rounded-lg" />
            </div>
          ))}
          <p className="text-center text-sm text-muted-foreground" role="status">
            {t('voice.joining')}
          </p>
        </div>
      </div>
    )

  const kind = room.category
  const isHost = me.role === 'host'
  const mod = isMod(me)
  const audience = room.members.filter((x) => x.seat === null)
  const hands = mod ? audience.filter((x) => x.hand).length : 0
  const close = () => setSheet(null)
  const person = (userId: string) => setSheet({ kind: 'person', userId })
  const host = room.members.find((x) => x.profile.id === room.hostId)
  // Talk rooms keep the chat in the middle; the others put it in a column on the right on wide screens.
  const aside = wide && kind !== 'talk'

  const share = async () => {
    const url = `${location.origin}/?voice=${encodeURIComponent(room.id)}`
    try {
      await navigator.clipboard.writeText(url)
      toast.show(t('voice.linkCopied'), 'success')
    } catch {
      toast.show(url)
    }
  }
  const copyCode = async () => {
    if (!room.code) return
    try {
      await navigator.clipboard.writeText(room.code)
      toast.show(t('room.copied', { code: room.code }), 'success')
    } catch {
      toast.show(t('room.codeIs', { code: room.code }))
    }
  }

  const emptySeat = async (seat: number) => {
    const locked = room.seats[seat]?.locked
    if (mod) return setSheet({ kind: 'seat', seat })
    if (locked) return toast.show(t('voice.seatLocked', { n: seat }), 'info')
    if (me.seat !== null || room.mode === 'open') return void s.act({ type: 'seat', seat })
    if (!me.hand && !(await s.act({ type: 'hand', up: true }))) return
    toast.show(t('voice.handSent'), 'info')
  }

  // The people on the stage: a slim strip that opens into the full stage (seats, locks, invitations).
  const stage = (
    <div className="flex flex-col">
      <StageStrip s={s} open={stageOpen} onToggle={() => setStageOpen((o) => !o)} onPerson={person} />
      <AnimatePresence initial={false}>
        {stageOpen ? (
          <m.div
            key="stage"
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ type: 'tween', duration: 0.2, ease: [0.2, 0, 0, 1] }}
            className="overflow-hidden"
          >
            <Stage room={room} levels={s.levels} reactions={s.reactions} size={wide ? 'md' : 'sm'} canManage={mod} onPerson={person} onEmpty={(seat) => void emptySeat(seat)} />
            <button
              type="button"
              onClick={() => setSheet({ kind: 'audience' })}
              className="mx-4 mb-2 flex w-[calc(100%-2rem)] items-center gap-3 rounded-2xl bg-muted/60 px-3.5 py-2.5 text-left transition-colors duration-150 hover:bg-muted"
            >
              <span className="text-sm font-semibold whitespace-nowrap">{t('voice.listeningN', { n: num(audience.length) })}</span>
              <span className="flex min-w-0 flex-1 -space-x-2 overflow-hidden" aria-hidden>
                {audience.slice(0, 7).map((x) => (
                  <Avatar key={x.profile.id} name={x.profile.name} src={x.profile.photoUrl} size={26} className="rounded-full ring-2 ring-background" />
                ))}
              </span>
              {hands > 0 ? (
                <span className="flex shrink-0 items-center gap-1 text-xs font-semibold text-primary-text">
                  <ReactionIcon kind="hand" size={16} /> {num(hands)}
                </span>
              ) : null}
              <ChevronRight className="size-4.5 shrink-0 text-muted-foreground rtl:rotate-180" aria-hidden />
            </button>
          </m.div>
        ) : null}
      </AnimatePresence>
    </div>
  )

  // Music outside a music room: the slim "listen" bar (the player opens on a tap).
  const musicBar =
    room.track && kind !== 'music' ? (
      <MusicPanel track={room.track} queue={room.queue} isHost={isHost} open={musicOpen} onOpenChange={setMusicOpen} onQueue={() => setSheet({ kind: 'queue' })} act={s.act} />
    ) : null

  const game = ludo.shown ? (
    <Suspense fallback={<div className="mx-auto my-2 aspect-square w-[min(100%-1.5rem,560px)] animate-pulse rounded-[18px] bg-muted" aria-busy />}>
      <LudoPanel
        s={s}
        game={ludo.shown}
        wide={wide}
        onGame={s.setLudo}
        onStage={() => ludo.setView('stage')}
        onDismiss={() => ludo.dismiss(ludo.shown!.id)}
        onClose={() => ludo.setConfirmClose(true)}
        onPerson={person}
      />
    </Suspense>
  ) : null
  const backToGame = ludo.hidden ? (
    <div className="flex justify-center px-4">
      <Button size="sm" variant="secondary" onClick={() => ludo.setView('game')}>
        <TopicIcon topic="gaming" size={18} /> {ludo.lt('inProgress')} · {ludo.lt('backToGame')}
      </Button>
    </div>
  ) : null

  let top: ReactNode
  if (kind === 'music') {
    top = (
      <>
        {game ?? backToGame}
        <MusicHero track={room.track} queue={room.queue} isHost={isHost} hostName={host?.profile.name ?? ''} onQueue={() => setSheet({ kind: 'queue' })} act={s.act} />
        {stage}
      </>
    )
  } else if (kind === 'games') {
    top = (
      <>
        {game ??
          backToGame ?? (
            <Suspense fallback={<div className="mx-3 h-64 animate-pulse rounded-3xl bg-muted sm:mx-4" aria-busy />}>
              <StartLudoCard canStart={mod} onStart={ludo.open} wide={wide} />
            </Suspense>
          )}
        {aside ? null : <Leaderboard roomId={room.id} meId={room.meId} after={ludo.finishedId} className="mx-3 sm:mx-4" />}
        {musicBar}
        {stage}
      </>
    )
  } else {
    top = (
      <>
        {stage}
        {musicBar}
        {game ?? backToGame}
      </>
    )
  }

  const chat = (
    <VoiceChat lines={s.lines} onPerson={person} onIcebreaker={kind === 'talk' ? s.icebreaker : undefined} className={cn('flex-1', !aside && 'border-t border-border pt-1')} />
  )
  const composer = <VoiceComposer onSend={s.send} onPhoto={s.sendPhoto} onIcebreaker={s.icebreaker} />

  return (
    <div className="theme-night relative flex min-h-0 flex-1 overflow-hidden bg-background text-foreground" style={{ '--k': kindColor(kind) } as CSSProperties}>
      {/* One soft glow in the room's category colour. */}
      <div aria-hidden className="pointer-events-none absolute inset-x-0 top-0 h-[420px] bg-[radial-gradient(70%_70%_at_50%_0%,color-mix(in_srgb,var(--k)_24%,transparent),transparent)]" />
      <div className="relative flex min-h-0 min-w-0 flex-1 flex-col">
        <header className="flex items-center gap-1 px-2 pt-[max(0.5rem,env(safe-area-inset-top))] pb-1 sm:px-4">
          <Button size="icon" variant="ghost" onClick={onMinimise} aria-label={t('voice.minimise')}>
            <ChevronDown className="size-6" aria-hidden />
          </Button>
          <div className="min-w-0 flex-1 px-1">
            <h1 className="truncate text-base leading-tight font-bold">{room.title}</h1>
            <p className="mt-1 flex items-center gap-2 text-xs text-muted-foreground">
              <TopicChip topic={kindTopic(kind)} label={categoryLabel(kind, t)} className="h-6 text-[11px]" />
              <span className="flex min-w-0 items-center gap-1 whitespace-nowrap tabular-nums">
                <Users className="size-3.5 shrink-0" aria-hidden /> {t('rooms.here', { n: num(room.members.length), max: num(room.maxMembers) })}
              </span>
            </p>
          </div>
          {room.code ? (
            <button
              type="button"
              onClick={() => void copyCode()}
              className="hidden h-9 items-center gap-1.5 rounded-full bg-muted px-3 font-mono text-[13px] font-bold tracking-widest transition-colors duration-150 hover:bg-input/60 sm:flex"
              aria-label={t('room.copy', { code: room.code })}
              dir="ltr"
            >
              {room.code} <Copy className="size-3.5" aria-hidden />
            </button>
          ) : null}
          <Button size="icon" variant="ghost" onClick={() => setSheet({ kind: 'options' })} aria-label={t('voice.more')}>
            <EllipsisVertical className="size-5" aria-hidden />
          </Button>
          <Button size="icon" variant="ghost" onClick={onLeave} aria-label={t('voice.leave')} className="text-destructive hover:text-destructive">
            <LogOut className="size-5" aria-hidden />
          </Button>
        </header>
        {room.announcement ? <Announcement text={room.announcement} onEdit={isHost ? () => setSheet({ kind: 'settings' }) : undefined} /> : null}
        <AudioNotice s={s} />

        {aside ? (
          <div className="min-h-0 flex-1 overflow-y-auto scrollbar-thin">
            <div className="mx-auto flex max-w-3xl flex-col gap-3 pt-2 pb-6">{top}</div>
          </div>
        ) : wide ? (
          <div className="mx-auto flex min-h-0 w-full max-w-3xl flex-1 flex-col gap-2 pt-1">
            <div className="flex shrink-0 flex-col gap-2">{top}</div>
            {chat}
          </div>
        ) : (
          <>
            <div className={cn('flex shrink-0 flex-col gap-2 overflow-y-auto no-scrollbar', kind === 'talk' ? (game ? 'max-h-[70%]' : 'max-h-[45%]') : 'max-h-[62%]')}>{top}</div>
            {chat}
          </>
        )}

        <ControlBar
          s={s}
          composer={aside ? null : composer}
          wide={wide}
          hands={hands}
          onRequests={() => setSheet({ kind: 'requests' })}
          onMusic={() => setSheet({ kind: 'queue' })}
          onGame={ludo.button}
          gameLabel={ludo.lt(ludo.shown || ludo.hidden ? 'backToGame' : 'play')}
          onJoin={() => {
            const seat = me.role === 'host' ? 0 : room.mode === 'open' || mod ? freeSeat(room) : null
            if (seat !== null) return void s.act({ type: 'seat', seat })
            return void s.act({ type: 'hand', up: !me.hand }).then((ok) => ok && !me.hand && toast.show(t('voice.handSent'), 'info'))
          }}
        />
      </div>

      {aside ? (
        <aside className="relative flex w-[400px] shrink-0 flex-col border-l border-border bg-black/20" aria-label={t('voice.chat')}>
          {kind === 'games' ? <Leaderboard roomId={room.id} meId={room.meId} after={ludo.finishedId} className="mx-3 mt-3 bg-white/5" /> : null}
          <h2 className="px-4 pt-4 pb-1 text-sm font-semibold">{t('voice.chat')}</h2>
          {chat}
          <div className="border-t border-border p-3">{composer}</div>
        </aside>
      ) : null}

      <PersonSheet room={room} userId={sheet?.kind === 'person' ? sheet.userId : null} onClose={close} act={s.act} />
      <RequestsSheet room={room} open={sheet?.kind === 'requests'} onClose={close} act={s.act} onPerson={person} />
      <AudienceSheet
        room={room}
        open={sheet?.kind === 'audience'}
        inviteSeat={sheet?.kind === 'audience' ? sheet.inviteSeat : undefined}
        onClose={close}
        act={s.act}
        onPerson={person}
      />
      <SeatSheet
        room={room}
        seat={sheet?.kind === 'seat' ? sheet.seat : null}
        onClose={close}
        onInvite={(seat) => setSheet({ kind: 'audience', inviteSeat: seat })}
        act={s.act}
      />
      <QueueSheet open={sheet?.kind === 'queue'} onClose={close} track={room.track} queue={room.queue} isHost={isHost} act={s.act} />
      <OptionsSheet
        open={sheet?.kind === 'options'}
        onClose={close}
        isHost={isHost}
        code={room.code}
        onShare={() => {
          close()
          void share()
        }}
        onCopyCode={() => {
          close()
          void copyCode()
        }}
        onSettings={() => setSheet({ kind: 'settings' })}
        onReport={() => setSheet({ kind: 'reportRoom' })}
        onLeave={() => {
          close()
          onLeave()
        }}
      />
      {isHost ? <SettingsSheet room={room} open={sheet?.kind === 'settings'} onClose={close} /> : null}
      <ReportRoomSheet roomId={room.id} open={sheet?.kind === 'reportRoom'} onClose={close} />
      <Modal open={ludo.confirmClose} onClose={() => ludo.setConfirmClose(false)} title={ludo.lt('closeTitle')} description={ludo.lt('closeBody')}>
        <div className="flex gap-3 p-6 pt-5">
          <Button variant="outline" className="flex-1" onClick={() => ludo.setConfirmClose(false)}>
            {t('common.cancel')}
          </Button>
          <Button variant="danger" className="flex-1" onClick={() => void ludo.close()}>
            {ludo.lt('closeConfirm')}
          </Button>
        </div>
      </Modal>
    </div>
  )
}

/** The host's announcement, pinned under the header (the host edits it from here). */
function Announcement({ text, onEdit }: { text: string; onEdit?: () => void }) {
  const { t } = useI18n()
  return (
    <div className="mx-3 mb-2 flex items-start gap-2 rounded-xl bg-[color-mix(in_srgb,var(--k)_14%,transparent)] px-3 py-2 ring-1 ring-[color-mix(in_srgb,var(--k)_26%,transparent)] sm:mx-4">
      <Pin className="mt-0.5 size-3.5 shrink-0 text-[var(--k)]" aria-hidden />
      <p className="line-clamp-3 min-w-0 flex-1 text-[13px] leading-snug break-words">
        <span className="sr-only">{t('room.announcement')}: </span>
        {text}
      </p>
      {onEdit ? (
        <button type="button" onClick={onEdit} aria-label={t('room.settings')} className="-my-1 -mr-1 grid size-7 shrink-0 place-items-center rounded-full text-muted-foreground transition-colors duration-150 hover:bg-muted hover:text-foreground">
          <Pencil className="size-3.5" aria-hidden />
        </button>
      ) : null}
    </div>
  )
}

/** The people on the stage, small, with their speaking rings; or, with nobody there, why there's no sound. */
function StageStrip({ s, open, onToggle, onPerson }: { s: VoiceSession; open: boolean; onToggle: () => void; onPerson: (userId: string) => void }) {
  const { t, num } = useI18n()
  const room = s.room!
  const stage = room.members.filter((x) => x.seat !== null).sort((a, b) => a.seat! - b.seat!)
  return (
    <div className="relative mx-3 flex items-center gap-2 rounded-2xl bg-muted/50 py-1.5 pr-2 pl-2 sm:mx-4">
      {stage.length ? (
        <ol aria-label={t('voice.stage')} className="flex min-w-0 flex-1 items-start gap-1 overflow-x-auto no-scrollbar">
          {stage.map((x) => (
            <li key={x.profile.id} className="w-[4.25rem] shrink-0">
              <SeatPerson member={x} px={40} levels={s.levels} onClick={() => onPerson(x.profile.id)} />
            </li>
          ))}
        </ol>
      ) : (
        <p className="flex min-w-0 flex-1 items-center gap-2.5 py-1.5 pl-1 text-xs leading-snug text-muted-foreground">
          <span className="grid size-9 shrink-0 place-items-center rounded-full bg-card/70 text-muted-foreground">
            <MicOff className="size-4" aria-hidden />
          </span>
          {t('room.stageEmpty')}
        </p>
      )}
      <button
        type="button"
        onClick={onToggle}
        aria-expanded={open}
        className="flex h-9 shrink-0 items-center gap-1 self-center rounded-full bg-card/80 px-3 text-xs font-semibold ring-1 ring-border transition-colors duration-150 hover:bg-muted"
      >
        <span className="tabular-nums">{stage.length ? t('room.onStage', { n: num(stage.length) }) : t('voice.stage')}</span>
        <ChevronDown className={cn('size-4 transition-transform duration-150', open && 'rotate-180')} aria-hidden />
      </button>
      {open ? null : <ReactionLayer reactions={s.reactions} />}
    </div>
  )
}

/**
 * The room's Ludo game as this screen shows it: up for everyone when one opens; anyone can switch back to the
 * stage, and a result stays until it is dismissed. Hosts and co-hosts open and close games.
 */
function useLudo(s: VoiceSession) {
  const backend = useBackend()
  const toast = useToast()
  const { lt } = useLudoT()
  const [view, setView] = useState<'game' | 'stage'>('game')
  const [dismissed, setDismissed] = useState<string | null>(null)
  const [confirmClose, setConfirmClose] = useState(false)
  const closing = useRef(false)
  const g = s.ludo
  const open = !!g && (g.status === 'lobby' || g.status === 'playing')
  const on = open || (!!g && g.status === 'finished' && dismissed !== g.id)
  const mod = isMod(s.me)

  // A new game comes up for everyone.
  const id = g?.id
  useEffect(() => {
    if (id) setView('game')
  }, [id])
  // A game the host closed: the stage is back, with a word for those who didn't close it.
  const prev = useRef(g?.status)
  useEffect(() => {
    if (g?.status === 'cancelled' && (prev.current === 'lobby' || prev.current === 'playing') && !closing.current) toast.show(lt('closed'), 'info')
    prev.current = g?.status
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [g?.status])

  const act = async (fn: () => Promise<LudoGame | null>) => {
    try {
      s.setLudo(await fn())
      return true
    } catch (err) {
      toast.error(err)
      return false
    }
  }
  const room = s.room
  const openGame = async () => {
    if (room && (await act(() => backend.ludoAction(room.id, null, { type: 'open' })))) setView('game')
  }
  return {
    lt,
    shown: on && view === 'game' ? g : null,
    hidden: on && view === 'stage',
    /** The last game that ended with a winner (the leaderboard reads again after one). */
    finishedId: g?.status === 'finished' ? g.id : null,
    setView,
    dismiss: setDismissed,
    confirmClose,
    setConfirmClose,
    open: openGame,
    close: async () => {
      if (!g || !room) return
      closing.current = true
      await act(() => backend.ludoAction(room.id, g.id, { type: 'close' }))
      closing.current = false
      setConfirmClose(false)
    },
    /** The game button: back to the game, or (hosts) a new one. */
    button: on ? () => setView('game') : mod && room ? () => void openGame() : null,
  }
}

/** Only when the audio needs something: a tap (autoplay rules), or it is reconnecting / can't connect. */
function AudioNotice({ s }: { s: VoiceSession }) {
  const { t } = useI18n()
  const a = s.audio
  if (!a) return null
  if (a.needsTap)
    return (
      <div className="flex justify-center px-4 pb-2">
        <Button size="sm" variant="brand" onClick={s.startAudio}>
          <Headphones className="size-4" aria-hidden /> {t('voice.tapToListen')}
        </Button>
      </div>
    )
  if (a.status !== 'reconnecting' && a.status !== 'failed') return null
  return (
    <p className="px-4 pb-2 text-center text-xs text-muted-foreground" role="status">
      {a.status === 'reconnecting' ? t('voice.audioReconnecting') : t('voice.audioFailed')}
    </p>
  )
}

/**
 * The same controls in every room: the composer (text, photo, icebreaker), reactions, the music and Ludo, the
 * hands raised (hosts), and the main button: the microphone on the stage, else "Join voice" or "Raise hand".
 */
function ControlBar({
  s,
  composer,
  wide,
  hands,
  onRequests,
  onMusic,
  onGame,
  gameLabel,
  onJoin,
}: {
  s: VoiceSession
  /** Null when the chat has its own column. */
  composer: ReactNode
  wide: boolean
  hands: number
  onRequests: () => void
  onMusic: () => void
  /** Ludo: open a game (hosts) or go back to the one on. Null: nothing to show. */
  onGame: (() => void) | null
  gameLabel: string
  onJoin: () => void
}) {
  const { t, num } = useI18n()
  const me = s.me!
  const room = s.room!
  const onStage = me.seat !== null
  const mod = isMod(me)
  const joins = me.role === 'host' || room.mode === 'open' || mod

  return (
    <div className="relative z-10 border-t border-border bg-background px-3 pt-2 pb-[max(0.75rem,env(safe-area-inset-bottom))] sm:px-4">
      {composer ? <div className={cn('mb-2', wide && 'mx-auto max-w-3xl')}>{composer}</div> : null}
      <div className={cn('flex items-center gap-1.5 sm:gap-2', wide && 'mx-auto max-w-3xl')}>
        <ReactionTray onReact={(kind) => void s.act({ type: 'react', kind })} />
        <Button size="icon" variant="secondary" onClick={onMusic} aria-label={t('voice.music')} title={t('voice.music')}>
          <TopicIcon topic="music" size={22} />
        </Button>
        {onGame ? (
          <Button size="icon" variant="secondary" onClick={onGame} aria-label={gameLabel} title={gameLabel}>
            <TopicIcon topic="gaming" size={22} />
          </Button>
        ) : null}
        {mod && hands > 0 ? (
          <Button size="icon" variant="secondary" onClick={onRequests} aria-label={t('voice.requestsN', { n: num(hands) })}>
            <ReactionIcon kind="hand" size={22} />
            <span className="absolute -top-1 -right-1 grid h-5 min-w-5 place-items-center rounded-full bg-primary px-1 text-[11px] font-bold text-primary-foreground ring-2 ring-background tabular-nums">
              {num(hands)}
            </span>
          </Button>
        ) : null}
        <span className="flex-1" />
        {onStage ? (
          <m.button
            type="button"
            whileTap={{ scale: 0.94 }}
            onClick={() => void s.act({ type: 'mic', on: me.muted })}
            aria-label={me.muted ? t('voice.unmute') : t('voice.mute')}
            aria-pressed={!me.muted}
            className={cn(
              'grid size-14 shrink-0 place-items-center rounded-full transition-colors duration-150',
              me.muted ? 'bg-muted text-foreground hover:bg-input/60' : 'bg-primary text-primary-foreground hover:bg-primary-hover',
            )}
          >
            {me.muted ? <MicOff className="size-6" aria-hidden /> : <Mic className="size-6" aria-hidden />}
          </m.button>
        ) : (
          <m.button
            type="button"
            whileTap={{ scale: 0.96 }}
            onClick={onJoin}
            aria-pressed={joins ? undefined : me.hand}
            className={cn(
              'flex h-12 shrink-0 items-center gap-2 rounded-full pr-5 pl-4 text-sm font-semibold transition-colors duration-150',
              me.hand && !joins ? 'bg-primary-soft text-primary-text ring-1 ring-primary-text/50' : 'bg-foreground text-background hover:opacity-90',
            )}
          >
            {joins ? <Mic className="size-5" aria-hidden /> : <ReactionIcon kind="hand" size={20} className={cn(me.hand && 'motion-safe:animate-pulse')} />}
            {joins ? t('room.joinVoice') : me.hand ? t('voice.handRaised') : t('voice.raiseHand')}
          </m.button>
        )}
      </div>
    </div>
  )
}

/** Five reactions in a tray above the button; it stays open for a burst of taps (X Spaces). */
function ReactionTray({ onReact }: { onReact: (kind: (typeof REACTIONS)[number]) => void }) {
  const { t } = useI18n()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const away = (e: PointerEvent) => !ref.current?.contains(e.target as Node) && setOpen(false)
    const esc = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('pointerdown', away)
    document.addEventListener('keydown', esc)
    return () => {
      document.removeEventListener('pointerdown', away)
      document.removeEventListener('keydown', esc)
    }
  }, [open])

  return (
    // Not positioned itself: the tray anchors to the control bar, so it never runs off a narrow screen.
    <div ref={ref}>
      <Button size="icon" variant="secondary" onClick={() => setOpen((o) => !o)} aria-label={t('voice.react')} aria-expanded={open}>
        <SmilePlus className="size-5" aria-hidden />
      </Button>
      <AnimatePresence>
        {open ? (
          <m.div
            role="group"
            aria-label={t('voice.react')}
            initial={{ opacity: 0, y: 8, scale: 0.96 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 8, scale: 0.96 }}
            transition={{ type: 'tween', duration: 0.15, ease: [0.2, 0, 0, 1] }}
            className="absolute bottom-full left-3 mb-2 flex origin-bottom-left gap-1 rounded-full bg-card p-1.5 shadow-e3 ring-1 ring-border sm:left-4 rtl:right-3 rtl:left-auto"
          >
            {REACTIONS.map((kind) => (
              <m.button
                key={kind}
                type="button"
                whileTap={{ scale: 0.82 }}
                onClick={() => onReact(kind)}
                aria-label={t(`voice.r.${kind}`)}
                className="grid size-11 place-items-center rounded-full transition-colors duration-150 hover:bg-muted"
              >
                <ReactionIcon kind={kind} size={26} />
              </m.button>
            ))}
          </m.div>
        ) : null}
      </AnimatePresence>
    </div>
  )
}
