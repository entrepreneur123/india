'use client'

import { AnimatePresence } from 'motion/react'
import * as m from 'motion/react-m'
import { useEffect, useRef, useState, type CSSProperties } from 'react'
import { ChevronDown, ChevronRight, EllipsisVertical, Headphones, Link2, LogOut, Mic, MicOff, SmilePlus } from 'lucide-react'
import { useI18n } from '@/components/i18n'
import { Avatar } from '@/components/ui/avatar'
import { Skeleton } from '@/components/ui/bits'
import { Button } from '@/components/ui/pressable'
import { ReactionIcon, TopicChip, TopicIcon, topicColor } from '@/components/ui/topic-icons'
import { useToast } from '@/components/ui/toast'
import { categoryLabel } from '@/components/screens/rooms/room-text'
import { cn } from '@/lib/utils'
import { VoiceChat, VoiceComposer } from './voice-chat'
import { MusicPanel, QueueSheet } from './voice-music'
import type { VoiceSession } from './voice-session'
import { AudienceSheet, OptionsSheet, PersonSheet, ReportRoomSheet, RequestsSheet, SeatSheet, type SheetState } from './voice-sheets'
import { Stage } from './voice-stage'
import { isMod, REACTIONS } from './voice-text'

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

/**
 * A voice room, full screen and always dark (party rooms are a night-time thing, and the stage reads better).
 * Phones: stage on top, chat below, controls at the bottom. Wide screens: stage in the middle, chat on the right
 * (Discord stage channels).
 */
export function VoiceRoomScreen({ s, onMinimise, onLeave }: { s: VoiceSession; onMinimise: () => void; onLeave: () => void }) {
  const { t, num } = useI18n()
  const toast = useToast()
  const wide = useWide()
  const [sheet, setSheet] = useState<SheetState>(null)
  const [musicOpen, setMusicOpen] = useState(true)
  const { room, me } = s

  if (!room || !me)
    return (
      <div className="theme-night relative flex min-h-0 flex-1 flex-col bg-background text-foreground" aria-busy>
        <header className="flex items-center gap-2 px-2 py-2">
          <Button size="icon" variant="ghost" onClick={onMinimise} aria-label={t('voice.minimise')}>
            <ChevronDown className="size-6" aria-hidden />
          </Button>
          <Skeleton className="h-5 w-48" />
        </header>
        <div className="flex flex-col items-center gap-6 px-4 pt-6">
          <Skeleton className="size-18 rounded-full" />
          <div className="grid w-full max-w-xl grid-cols-4 gap-4">
            {Array.from({ length: 8 }, (_, i) => (
              <Skeleton key={i} className="mx-auto size-13 rounded-full" />
            ))}
          </div>
          <p className="text-sm text-muted-foreground" role="status">
            {t('voice.joining')}
          </p>
        </div>
      </div>
    )

  const isHost = me.role === 'host'
  const mod = isMod(me)
  const audience = room.members.filter((x) => x.seat === null)
  const hands = mod ? audience.filter((x) => x.hand).length : 0
  const close = () => setSheet(null)
  const person = (userId: string) => setSheet({ kind: 'person', userId })

  const share = async () => {
    const url = `${location.origin}/?voice=${encodeURIComponent(room.id)}`
    try {
      await navigator.clipboard.writeText(url)
      toast.show(t('voice.linkCopied'), 'success')
    } catch {
      toast.show(url)
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

  // Phones: the player sits above the stage. Wide screens: at the top of the chat column, so the whole stage
  // stays in view (16:9 at 368 px wide is still above YouTube's 200 px minimum).
  const music = room.track ? (
    <MusicPanel
      track={room.track}
      queue={room.queue}
      isHost={isHost}
      open={musicOpen}
      onOpenChange={setMusicOpen}
      onQueue={() => setSheet({ kind: 'queue' })}
      act={s.act}
    />
  ) : null

  const top = (
    <>
      {music && !wide ? <div className="pt-1 pb-2">{music}</div> : null}
      <Stage
        room={room}
        levels={s.levels}
        reactions={s.reactions}
        size={wide ? 'lg' : room.track && musicOpen ? 'sm' : 'md'}
        canManage={mod}
        onPerson={person}
        onEmpty={(seat) => void emptySeat(seat)}
      />
      <button
        type="button"
        onClick={() => setSheet({ kind: 'audience' })}
        className="mx-4 mb-3 flex w-[calc(100%-2rem)] items-center gap-3 rounded-2xl bg-muted/60 px-3.5 py-2.5 text-left transition-colors duration-150 hover:bg-muted"
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
    </>
  )

  return (
    <div className="theme-night relative flex min-h-0 flex-1 overflow-hidden bg-background text-foreground">
      {/* One soft glow in the room's topic colour. */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-x-0 top-0 h-[420px]"
        style={{ background: `radial-gradient(70% 70% at 50% 0%, color-mix(in srgb, ${topicColor(room.category)} 24%, transparent), transparent)` } as CSSProperties}
      />
      <div className="relative flex min-h-0 min-w-0 flex-1 flex-col">
        <header className="flex items-center gap-1 px-2 pt-[max(0.5rem,env(safe-area-inset-top))] pb-1 sm:px-4">
          <Button size="icon" variant="ghost" onClick={onMinimise} aria-label={t('voice.minimise')}>
            <ChevronDown className="size-6" aria-hidden />
          </Button>
          <div className="min-w-0 flex-1 px-1">
            <h1 className="truncate text-base leading-tight font-bold">{room.title}</h1>
            <p className="mt-1 flex items-center gap-2 text-xs text-muted-foreground">
              <TopicChip topic={room.category} label={categoryLabel(room.category, t)} className="h-6 text-[11px]" />
              <span className="flex min-w-0 items-center gap-1 whitespace-nowrap tabular-nums">
                <Headphones className="size-3.5 shrink-0" aria-hidden /> {t('voice.listening', { n: num(audience.length) })}
              </span>
            </p>
          </div>
          <Button size="icon" variant="ghost" onClick={() => void share()} aria-label={t('voice.share')}>
            <Link2 className="size-5" aria-hidden />
          </Button>
          <Button size="icon" variant="ghost" onClick={() => setSheet({ kind: 'options' })} aria-label={t('voice.more')}>
            <EllipsisVertical className="size-5" aria-hidden />
          </Button>
        </header>

        {wide ? (
          <div className="min-h-0 flex-1 overflow-y-auto scrollbar-thin">
            <div className="mx-auto flex max-w-3xl flex-col pt-2 pb-6">{top}</div>
          </div>
        ) : (
          <>
            <div className="max-h-[60%] shrink-0 overflow-y-auto no-scrollbar">{top}</div>
            <VoiceChat lines={s.lines} onPerson={person} className="flex-1 border-t border-border pt-1" />
          </>
        )}

        <ControlBar
          s={s}
          wide={wide}
          hands={hands}
          onRequests={() => setSheet({ kind: 'requests' })}
          onMusic={() => setSheet({ kind: 'queue' })}
          onLeave={onLeave}
        />
      </div>

      {wide ? (
        <aside className="relative flex w-[400px] shrink-0 flex-col border-l border-border bg-black/20" aria-label={t('voice.chat')}>
          {music ? <div className="border-b border-border pt-4 pb-3">{music}</div> : null}
          <h2 className="px-4 pt-4 pb-1 text-sm font-semibold">{t('voice.chat')}</h2>
          <VoiceChat lines={s.lines} onPerson={person} className="flex-1" />
          <VoiceComposer onSend={s.send} className="border-t border-border p-3" />
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
      <QueueSheet open={sheet?.kind === 'queue'} onClose={close} track={room.track} queue={room.queue} isHost={isHost} meName={me.profile.name} act={s.act} />
      <OptionsSheet
        open={sheet?.kind === 'options'}
        onClose={close}
        isHost={isHost}
        onShare={() => {
          close()
          void share()
        }}
        onReport={() => setSheet({ kind: 'reportRoom' })}
        onLeave={() => {
          close()
          onLeave()
        }}
      />
      <ReportRoomSheet roomId={room.id} open={sheet?.kind === 'reportRoom'} onClose={close} />
    </div>
  )
}

function ControlBar({
  s,
  wide,
  hands,
  onRequests,
  onMusic,
  onLeave,
}: {
  s: VoiceSession
  wide: boolean
  hands: number
  onRequests: () => void
  onMusic: () => void
  onLeave: () => void
}) {
  const { t, num } = useI18n()
  const toast = useToast()
  const me = s.me!
  const onStage = me.seat !== null
  const mod = isMod(me)

  const hand = async () => {
    const up = !me.hand
    if ((await s.act({ type: 'hand', up })) && up) toast.show(t('voice.handSent'), 'info')
  }

  return (
    <div className="relative z-10 border-t border-border bg-background px-3 pt-2 pb-[max(0.75rem,env(safe-area-inset-bottom))] sm:px-4">
      {wide ? null : <VoiceComposer onSend={s.send} className="mb-2" />}
      <div className={cn('flex items-center gap-2', wide && 'mx-auto max-w-3xl')}>
        <Button variant="secondary" onClick={onLeave} className="text-destructive">
          <LogOut className="size-4.5" aria-hidden /> {t('voice.leave')}
        </Button>
        <span className="flex-1" />
        {mod ? (
          <Button size="icon" variant="secondary" onClick={onRequests} aria-label={t('voice.requestsN', { n: num(hands) })}>
            <ReactionIcon kind="hand" size={22} />
            {hands > 0 ? (
              <span className="absolute -top-1 -right-1 grid h-5 min-w-5 place-items-center rounded-full bg-primary px-1 text-[11px] font-bold text-primary-foreground ring-2 ring-background tabular-nums">
                {num(hands)}
              </span>
            ) : null}
          </Button>
        ) : null}
        {me.role === 'host' ? (
          <Button size="icon" variant="secondary" onClick={onMusic} aria-label={t('voice.music')}>
            <TopicIcon topic="music" size={22} />
          </Button>
        ) : null}
        <ReactionTray onReact={(kind) => void s.act({ type: 'react', kind })} />
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
            onClick={() => void hand()}
            aria-pressed={me.hand}
            className={cn(
              'flex h-12 shrink-0 items-center gap-2 rounded-full pr-5 pl-4 text-sm font-semibold transition-colors duration-150',
              me.hand ? 'bg-primary-soft text-primary-text ring-1 ring-primary-text/50' : 'bg-foreground text-background hover:opacity-90',
            )}
          >
            <ReactionIcon kind="hand" size={20} className={cn(me.hand && 'motion-safe:animate-pulse')} />
            {me.hand ? t('voice.handRaised') : t('voice.raiseHand')}
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
            className="absolute right-3 bottom-full mb-2 flex origin-bottom-right gap-1 rounded-full bg-card p-1.5 shadow-e3 ring-1 ring-border sm:right-4 rtl:right-auto rtl:left-3"
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
