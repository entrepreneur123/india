'use client'

import { AnimatePresence } from 'motion/react'
import * as m from 'motion/react-m'
import { lazy, Suspense, useCallback, useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { ChevronDown, ChevronRight, Copy, EllipsisVertical, Headphones, LogOut, Mic, MicOff, Pencil, Pin, ScreenShare, ScreenShareOff, SmilePlus, UserPlus, Users, X } from 'lucide-react'
import { useI18n } from '@/components/i18n'
import { Avatar } from '@/components/ui/avatar'
import { Skeleton } from '@/components/ui/bits'
import { Button } from '@/components/ui/pressable'
import { ReactionIcon, TopicChip, TopicIcon } from '@/components/ui/topic-icons'
import { useToast } from '@/components/ui/toast'
import { categoryLabel, kindColor, kindTopic } from '@/components/screens/rooms/room-text'
import { LinkOpenProvider, LinkPanel, LinkSheet, useLinkMeta, useWideScreen } from '@/components/screens/chat/link-viewer'
import { hostOf, shareable } from '@/lib/links'
import { retryImport } from '@/lib/retry-import'
import { cn } from '@/lib/utils'
import { VOICE_SEATS, type VoiceMember, type VoiceRoom, type VoiceStage } from '@/lib/backend'
import type { Quotable } from '@/lib/voice-chat'
import { VoiceChat, VoiceComposer } from './voice-chat'
import { MusicHero, MusicPanel, QueueSheet } from './voice-music'
import { useRoomT } from './room-t'
import type { VoiceSession } from './voice-session'
import { AudienceSheet, OptionsSheet, PersonSheet, ReportRoomSheet, RequestsSheet, SeatSheet, SettingsSheet, type SheetState } from './voice-sheets'
import { ReactionLayer, SeatPerson, Stage } from './voice-stage'
import { isMod, REACTIONS, roleLabel } from './voice-text'
import { canShareLink, InviteSheet, roomLink } from '@/components/social/invite-sheet'
import { useSocialT } from '@/components/social/social-text'

// The poll form is its own chunk, fetched when a host or co-host first opens the composer's menu.
const loadPollSheet = () => retryImport(() => import('./poll-sheet'))
const PollSheet = lazy(() => loadPollSheet().then((mod) => ({ default: mod.PollSheet })))

/** The first free, unlocked stage seat (open rooms take it at once). */
const freeSeat = (room: VoiceRoom) => {
  const taken = new Set(room.members.map((x) => x.seat))
  return room.seats.find((s) => s.index > 0 && s.index < VOICE_SEATS && !s.locked && !taken.has(s.index))?.index ?? null
}

/** A shared page closed "for me", by room: it stays closed when the room is minimised and opened again. */
const closedStage = new Map<string, string>()
const stageKey = (st: VoiceStage) => `${st.url}#${st.at}`

/**
 * A room, full screen and always dark. Every room has the chat (text with replies, @mentions and links, icebreakers,
 * photos, polls), the stage and shared music; the category decides what comes first (the player in a music room).
 * The stage (seats, who's on it) and the listeners are two separate sections.
 *  - Phones: the music and the stage on top (it scrolls), the chat right under it on the same screen, the composer
 *    and the controls at the bottom. Links open in a tall sheet; a page shown to the room is a card to open.
 *  - Wide screens: the stage and music in the middle, the chat in a column on the right; a link, or the page shown to
 *    the room, opens in a column between them (under 1280 px it takes the middle's place while open).
 */
export function VoiceRoomScreen({ s, onMinimise, onLeave }: { s: VoiceSession; onMinimise: () => void; onLeave: () => void }) {
  const { t, num, rtl } = useI18n()
  const { rt } = useRoomT()
  const toast = useToast()
  const { st: sst } = useSocialT()
  const wide = useWideScreen()
  const [sheet, setSheet] = useState<SheetState>(null)
  const [seatsOpen, setSeatsOpen] = useState(false)
  const [musicOpen, setMusicOpen] = useState(false)
  const [replyTo, setReplyTo] = useState<Quotable | null>(null)
  const [pollOpen, setPollOpen] = useState(false)
  const [pollUsed, setPollUsed] = useState(false)
  /** A link I opened from the chat. */
  const [viewer, setViewer] = useState<string | null>(null)
  const [, setClosedTick] = useState(0)
  const openLink = useCallback((url: string) => setViewer(url), [])
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
  const inviteLabel = sst('invite.title')

  // The page shown to the room, unless I closed it for myself; a link I opened myself goes over it.
  const stage = room.stage
  const stageShown = !!stage && closedStage.get(room.id) !== stageKey(stage)
  const panelUrl = viewer ?? (stageShown && wide ? stage!.url : null)
  const panelShared = !!stage && panelUrl === stage.url
  const closePanel = () => {
    if (viewer) setViewer(null)
    if (panelShared && stage) {
      closedStage.set(room.id, stageKey(stage))
      setClosedTick((n) => n + 1)
    }
  }
  const reopenStage = () => {
    closedStage.delete(room.id)
    setClosedTick((n) => n + 1)
    if (!wide && stage) setViewer(stage.url)
  }
  const shareLink = async (url: string | null, title?: string | null) => {
    if (url !== null && !shareable(url)) return void toast.show(rt('httpsOnly'), 'info')
    if (await s.act({ type: 'shareLink', url, title })) {
      if (url !== null) closedStage.delete(room.id)
      toast.show(url === null ? rt('shareStopped') : rt('sharedNow'), 'success')
      if (url === null) setViewer(null)
    }
  }

  const share = async () => {
    const url = roomLink(room)
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

  // The stage (seats) and the listeners: two sections, one under the other.
  const stageSection = (
    <StageSection
      s={s}
      wide={wide}
      open={wide || seatsOpen}
      onToggle={() => setSeatsOpen((o) => !o)}
      onPerson={person}
      onEmpty={(seat) => void emptySeat(seat)}
    />
  )
  const listeners = <Listeners room={room} wide={wide} hands={hands} onAll={() => setSheet({ kind: 'audience' })} onPerson={person} />

  // Music outside a music room: the slim "listen" bar (the player opens on a tap).
  const musicBar =
    room.track && kind !== 'music' ? (
      <MusicPanel track={room.track} queue={room.queue} canDJ={mod} open={musicOpen} onOpenChange={setMusicOpen} onQueue={() => setSheet({ kind: 'queue' })} act={s.act} />
    ) : null
  // Phones: the page shown to the room is a card (the sheet would cover the chat); wide screens show it in its column.
  const stageCard =
    stage && !wide ? (
      <StageCard stage={stage} mine={stage.byId === room.meId} closed={!stageShown} onOpen={reopenStage} onClose={() => {
        closedStage.set(room.id, stageKey(stage))
        setClosedTick((n) => n + 1)
      }} />
    ) : null

  const top: ReactNode = (
    <>
      {kind === 'music' ? <MusicHero track={room.track} queue={room.queue} canDJ={mod} hostName={host?.profile.name ?? ''} onQueue={() => setSheet({ kind: 'queue' })} act={s.act} /> : null}
      {stageSection}
      {musicBar}
      {listeners}
      {stageCard}
    </>
  )

  const chat = (
    <VoiceChat
      s={s}
      onPerson={person}
      onReply={setReplyTo}
      onIcebreaker={kind === 'talk' ? s.icebreaker : undefined}
      className={cn('flex-1', !wide && 'border-t border-border pt-1')}
    />
  )
  const composer = (
    <VoiceComposer
      onSend={async (text) => {
        const ok = await s.send(text, replyTo?.id ?? null)
        if (ok) setReplyTo(null)
        return ok
      }}
      onPhoto={s.sendPhoto}
      onIcebreaker={s.icebreaker}
      onPoll={
        mod
          ? () => {
              setPollUsed(true)
              setPollOpen(true)
            }
          : undefined
      }
      onMenu={mod ? () => void loadPollSheet().catch(() => undefined) : undefined}
      onTyping={s.notifyTyping}
      members={room.members.filter((x) => x.profile.id !== room.meId).map((x) => x.profile)}
      reply={replyTo}
      onCancelReply={() => setReplyTo(null)}
    />
  )

  // Wide: closing the shared page closes it for me (until it changes); a phone's sheet just goes down.
  const panel = (url: string) => (
    <LinkPanel
      url={url}
      onClose={wide ? closePanel : () => setViewer(null)}
      closeLabel={panelShared && wide ? rt('closeForMe') : undefined}
      label={panelShared && stage ? (stage.byId === room.meId ? rt('sharedByYou') : rt('sharedBy', { name: stage.by })) : undefined}
      actions={
        mod ? (
          panelShared ? (
            <Button size="sm" variant="secondary" onClick={() => void shareLink(null)} className="shrink-0 px-3" aria-label={rt('stopSharing')}>
              <ScreenShareOff className="size-4" aria-hidden /> <span className="max-sm:sr-only">{rt('stopSharing')}</span>
            </Button>
          ) : shareable(url) ? (
            <ShowEveryone url={url} onShare={(title) => void shareLink(url, title)} />
          ) : null
        ) : null
      }
      footer={panelShared || (mod && shareable(url)) ? <p className="border-t border-border px-4 py-2 text-[11.5px] leading-snug text-muted-foreground">{rt('sharedNote')}</p> : null}
      className={wide ? 'h-full' : undefined}
    />
  )

  return (
    <LinkOpenProvider value={openLink}>
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
            {stage && wide && !stageShown && !viewer ? (
              <Button size="icon" variant="ghost" onClick={reopenStage} aria-label={rt('sharedPage')} title={rt('sharedPage')} className="text-primary-text">
                <ScreenShare className="size-5" aria-hidden />
              </Button>
            ) : null}
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
            <Button size="icon" variant="ghost" onClick={() => setSheet({ kind: 'invite' })} aria-label={inviteLabel}>
              <UserPlus className="size-5" aria-hidden />
            </Button>
            <Button size="icon" variant="ghost" onClick={() => setSheet({ kind: 'options' })} aria-label={t('voice.more')}>
              <EllipsisVertical className="size-5" aria-hidden />
            </Button>
            <Button size="icon" variant="ghost" onClick={onLeave} aria-label={t('voice.leave')} className="text-destructive hover:text-destructive">
              <LogOut className="size-5 rtl:-scale-x-100" aria-hidden />
            </Button>
          </header>
          {room.announcement ? <Announcement text={room.announcement} onEdit={isHost ? () => setSheet({ kind: 'settings' }) : undefined} /> : null}
          <AudioNotice s={s} />

          {wide ? (
            <div className="flex min-h-0 flex-1">
              <div className={cn('min-h-0 min-w-0 flex-1 overflow-y-auto scrollbar-thin', panelUrl && 'max-xl:hidden')}>
                <div className="mx-auto flex max-w-3xl flex-col gap-3 pt-2 pb-6">{top}</div>
              </div>
              {panelUrl ? (
                <m.div
                  key="panel"
                  initial={{ opacity: 0, x: rtl ? -16 : 16 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ type: 'tween', duration: 0.2, ease: [0.2, 0, 0, 1] }}
                  className="min-h-0 w-full shrink-0 overflow-hidden border-s border-border xl:w-[min(46%,560px)]"
                >
                  {panel(panelUrl)}
                </m.div>
              ) : null}
            </div>
          ) : (
            <>
              <div className={cn('flex shrink-0 flex-col gap-2 overflow-y-auto pb-1 no-scrollbar', kind === 'music' ? 'max-h-[58%]' : 'max-h-[46%]')}>{top}</div>
              {chat}
            </>
          )}

          <ControlBar
            s={s}
            composer={wide ? null : composer}
            wide={wide}
            hands={hands}
            onRequests={() => setSheet({ kind: 'requests' })}
            onMusic={() => setSheet({ kind: 'queue' })}
            onJoin={() => {
              const seat = me.role === 'host' ? 0 : room.mode === 'open' || mod ? freeSeat(room) : null
              if (seat !== null) return void s.act({ type: 'seat', seat })
              return void s.act({ type: 'hand', up: !me.hand }).then((ok) => ok && !me.hand && toast.show(t('voice.handSent'), 'info'))
            }}
          />
        </div>

        {wide ? (
          <aside className="relative flex w-[380px] shrink-0 flex-col border-s border-border bg-black/20 xl:w-[400px]" aria-label={t('voice.chat')}>
            <h2 className="px-4 pt-4 pb-1 text-sm font-semibold">{t('voice.chat')}</h2>
            {chat}
            <div className="border-t border-border p-3 pt-2">{composer}</div>
          </aside>
        ) : null}

        {wide ? null : (
          <LinkSheet open={!!viewer} onClose={() => setViewer(null)} night>
            {viewer ? panel(viewer) : null}
          </LinkSheet>
        )}

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
        <QueueSheet open={sheet?.kind === 'queue'} onClose={close} track={room.track} queue={room.queue} canDJ={mod} act={s.act} />
        <OptionsSheet
          open={sheet?.kind === 'options'}
          onClose={close}
          isHost={isHost}
          code={room.code}
          canShare={canShareLink(room)}
          onInvite={() => setSheet({ kind: 'invite' })}
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
        <InviteSheet room={room} open={sheet?.kind === 'invite'} onClose={close} />
        <ReportRoomSheet roomId={room.id} open={sheet?.kind === 'reportRoom'} onClose={close} />
        {pollUsed ? (
          <Suspense fallback={null}>
            <PollSheet open={pollOpen} onClose={() => setPollOpen(false)} onCreate={s.createPoll} />
          </Suspense>
        ) : null}
      </div>
    </LinkOpenProvider>
  )
}

/** "Show to everyone": the page's title (from its preview) goes with it. */
function ShowEveryone({ url, onShare }: { url: string; onShare: (title: string | null) => void }) {
  const { rt } = useRoomT()
  const { meta } = useLinkMeta(url)
  return (
    <Button size="sm" variant="brand" onClick={() => onShare(meta?.title ?? null)} className="shrink-0 px-3" aria-label={rt('showEveryone')}>
      <ScreenShare className="size-4" aria-hidden /> <span className="max-sm:sr-only">{rt('showEveryone')}</span>
    </Button>
  )
}

/** Phones: the page the host or a co-host shows the room, as a card above the chat (it opens in the sheet). */
function StageCard({ stage, mine, closed, onOpen, onClose }: { stage: VoiceStage; mine: boolean; closed: boolean; onOpen: () => void; onClose: () => void }) {
  const { rt } = useRoomT()
  if (closed)
    return (
      <button
        type="button"
        onClick={onOpen}
        className="mx-3 flex h-9 items-center gap-2 self-start rounded-full bg-muted/70 px-3 text-xs font-semibold text-primary-text transition-colors duration-150 hover:bg-muted sm:mx-4"
      >
        <ScreenShare className="size-4" aria-hidden /> {rt('sharedPage')}
      </button>
    )
  return (
    <div className="mx-3 flex items-center gap-3 rounded-2xl bg-[color-mix(in_srgb,var(--k)_12%,var(--card))] p-2 ps-3 ring-1 ring-[color-mix(in_srgb,var(--k)_30%,transparent)] sm:mx-4">
      <TopicIcon topic="globe" size={26} />
      <button type="button" onClick={onOpen} className="min-w-0 flex-1 text-start">
        <span className="block truncate text-[11px] font-semibold text-primary-text">{mine ? rt('sharedByYou') : rt('sharedBy', { name: stage.by })}</span>
        <span className="block truncate text-sm font-semibold">{stage.title || hostOf(stage.url)}</span>
      </button>
      <Button size="sm" variant="secondary" onClick={onOpen} className="shrink-0 px-3">
        {rt('openShared')}
      </Button>
      <Button size="icon-sm" variant="ghost" onClick={onClose} aria-label={rt('closeForMe')} title={rt('closeForMe')}>
        <X className="size-4" aria-hidden />
      </Button>
    </div>
  )
}

/** A section's title: an icon, the name and how many (Clubhouse / X Spaces). */
function SectionTitle({ icon, label, count, trailing, id }: { icon: ReactNode; label: string; count: number; trailing?: ReactNode; id: string }) {
  const { num } = useI18n()
  return (
    <div className="flex min-h-9 items-center gap-2">
      <h2 id={id} className="flex min-w-0 flex-1 items-center gap-2 text-[12px] font-bold tracking-wider text-muted-foreground uppercase">
        <span className="shrink-0 text-[var(--k)] [&>svg]:size-4" aria-hidden>
          {icon}
        </span>
        <span className="truncate">{label}</span>
        <span className="rounded-full bg-muted px-2 py-0.5 text-[11px] tracking-normal text-foreground tabular-nums">{num(count)}</span>
      </h2>
      {trailing}
    </div>
  )
}

/**
 * The stage: who's on it, with their speaking rings, and its seats. Wide screens always show the seats; phones show
 * the people on it in a row and open the seats on a tap. With nobody on it, why there's no sound.
 */
function StageSection({
  s,
  wide,
  open,
  onToggle,
  onPerson,
  onEmpty,
}: {
  s: VoiceSession
  wide: boolean
  open: boolean
  onToggle: () => void
  onPerson: (userId: string) => void
  onEmpty: (seat: number) => void
}) {
  const { t } = useI18n()
  const { rt } = useRoomT()
  const room = s.room!
  const mod = isMod(s.me)
  const onStage = room.members.filter((x) => x.seat !== null).sort((a, b) => a.seat! - b.seat!)
  const id = `stage-${room.id}`
  return (
    <section
      aria-labelledby={id}
      className="relative mx-3 rounded-3xl bg-[color-mix(in_srgb,var(--k)_7%,var(--card))] px-3 pt-2 pb-2 ring-1 ring-[color-mix(in_srgb,var(--k)_18%,transparent)] sm:mx-4 sm:px-4"
    >
      <SectionTitle
        id={id}
        icon={<Mic />}
        label={rt('onStage')}
        count={onStage.length}
        trailing={
          wide ? null : (
            <button
              type="button"
              onClick={onToggle}
              aria-expanded={open}
              className="flex h-8 shrink-0 items-center gap-1 rounded-full px-2.5 text-xs font-semibold text-muted-foreground transition-colors duration-150 hover:bg-muted hover:text-foreground"
            >
              {open ? rt('hideSeats') : rt('showSeats')}
              <ChevronDown className={cn('size-4 transition-transform duration-150', open && 'rotate-180')} aria-hidden />
            </button>
          )
        }
      />
      <AnimatePresence initial={false} mode="wait">
        {open ? (
          <m.div
            key="seats"
            initial={wide ? false : { height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ type: 'tween', duration: 0.2, ease: [0.2, 0, 0, 1] }}
            className="-mx-3 overflow-hidden sm:-mx-4"
          >
            <Stage room={room} levels={s.levels} reactions={s.reactions} size={wide ? 'md' : 'sm'} canManage={mod} onPerson={onPerson} onEmpty={onEmpty} />
          </m.div>
        ) : (
          <m.div key="row" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.12 }} className="relative">
            {onStage.length ? (
              <ol aria-label={t('voice.stage')} className="flex min-w-0 items-start gap-1 overflow-x-auto pb-1 no-scrollbar">
                {onStage.map((x) => (
                  <li key={x.profile.id} className="w-[4.5rem] shrink-0">
                    <SeatPerson member={x} px={44} levels={s.levels} onClick={() => onPerson(x.profile.id)} />
                  </li>
                ))}
              </ol>
            ) : (
              <p className="flex items-center gap-2.5 py-2 text-xs leading-snug text-muted-foreground">
                <span className="grid size-9 shrink-0 place-items-center rounded-full bg-card/70 text-muted-foreground">
                  <MicOff className="size-4" aria-hidden />
                </span>
                {t('room.stageEmpty')}
              </p>
            )}
            <ReactionLayer reactions={s.reactions} />
          </m.div>
        )}
      </AnimatePresence>
    </section>
  )
}

/**
 * Everyone listening (off the stage): their faces with names on wide screens, a row of faces on phones; raised hands
 * show to the host and co-hosts. "See all" opens the list with search.
 */
function Listeners({ room, wide, hands, onAll, onPerson }: { room: VoiceRoom; wide: boolean; hands: number; onAll: () => void; onPerson: (userId: string) => void }) {
  const { t, num } = useI18n()
  const { rt } = useRoomT()
  const list = room.members
    .filter((x) => x.seat === null)
    // raised hands first (they're waiting), then the hosts, then everyone in order of arrival
    .sort((a, b) => Number(b.hand) - Number(a.hand) || Number(isMod(b)) - Number(isMod(a)))
  const id = `listening-${room.id}`
  const max = wide ? 24 : 9
  const shown = list.slice(0, max)
  const more = list.length - shown.length
  return (
    <section aria-labelledby={id} className="mx-3 rounded-3xl bg-muted/40 px-3 pt-2 pb-2.5 sm:mx-4 sm:px-4">
      <SectionTitle
        id={id}
        icon={<Headphones />}
        label={rt('listening')}
        count={list.length}
        trailing={
          <span className="flex shrink-0 items-center gap-1">
            {hands > 0 ? (
              <span className="flex items-center gap-1 px-1 text-xs font-semibold text-primary-text" title={t('voice.requestsN', { n: num(hands) })}>
                <ReactionIcon kind="hand" size={16} /> <span className="tabular-nums">{num(hands)}</span>
              </span>
            ) : null}
            {list.length ? (
              <button
                type="button"
                onClick={onAll}
                className="flex h-8 items-center gap-0.5 rounded-full px-2.5 text-xs font-semibold text-muted-foreground transition-colors duration-150 hover:bg-muted hover:text-foreground"
              >
                {rt('seeAll')} <ChevronRight className="size-4 rtl:rotate-180" aria-hidden />
              </button>
            ) : null}
          </span>
        }
      />
      {list.length === 0 ? (
        <p className="py-1.5 text-xs text-muted-foreground">{t('voice.noListeners')}</p>
      ) : wide ? (
        <ul className="grid grid-cols-[repeat(auto-fill,minmax(4.5rem,1fr))] gap-x-1 gap-y-2 pt-1">
          {shown.map((x) => (
            <li key={x.profile.id}>
              <ListenerFace m={x} onClick={() => onPerson(x.profile.id)} />
            </li>
          ))}
          {more > 0 ? (
            <li>
              <button type="button" onClick={onAll} className="flex w-full flex-col items-center gap-1 rounded-2xl p-1 text-xs font-semibold text-muted-foreground hover:text-foreground">
                <span className="grid size-11 place-items-center rounded-full bg-card/80 text-sm tabular-nums ring-1 ring-border">+{num(more)}</span>
                {rt('seeAll')}
              </button>
            </li>
          ) : null}
        </ul>
      ) : (
        <button type="button" onClick={onAll} className="flex w-full items-center gap-2 py-1 text-start" aria-label={`${rt('listening')}: ${num(list.length)}. ${rt('seeAll')}`}>
          <span className="flex min-w-0 -space-x-2 overflow-hidden" aria-hidden>
            {shown.map((x) => (
              <span key={x.profile.id} className="relative shrink-0">
                <Avatar name={x.profile.name} src={x.profile.photoUrl} size={30} className="rounded-full ring-2 ring-background" />
                {x.hand ? <ReactionIcon kind="hand" size={14} className="absolute -inset-e-1 -bottom-1" /> : null}
              </span>
            ))}
          </span>
          {more > 0 ? <span className="shrink-0 text-xs font-semibold text-muted-foreground tabular-nums">+{num(more)}</span> : null}
        </button>
      )}
    </section>
  )
}

/** One listener on wide screens: the face, the first name, a hand or the host / co-host badge. */
function ListenerFace({ m: x, onClick }: { m: VoiceMember; onClick: () => void }) {
  const { t } = useI18n()
  const role = roleLabel(x, t)
  return (
    <button type="button" onClick={onClick} aria-label={[x.profile.name, role].filter(Boolean).join(', ')} className="group flex w-full min-w-0 flex-col items-center gap-1 rounded-2xl p-1">
      <span className="relative">
        <Avatar name={x.profile.name} src={x.profile.photoUrl} size={44} className="transition-transform duration-150 group-active:scale-95" />
        {x.hand ? <ReactionIcon kind="hand" size={18} className="absolute -inset-e-1 -bottom-1" /> : null}
      </span>
      <span className="max-w-full truncate text-[11px] leading-tight font-medium">{x.profile.name.split(' ')[0]}</span>
      {role ? <span className="-mt-0.5 rounded-full bg-primary-soft px-1.5 text-[9.5px] leading-tight font-bold text-primary-text">{role}</span> : null}
    </button>
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
        <button type="button" onClick={onEdit} aria-label={t('room.settings')} className="-my-1 -me-1 grid size-7 shrink-0 place-items-center rounded-full text-muted-foreground transition-colors duration-150 hover:bg-muted hover:text-foreground">
          <Pencil className="size-3.5" aria-hidden />
        </button>
      ) : null}
    </div>
  )
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
 * The same controls in every room: the composer (text, photo, icebreaker, polls), reactions, the music, the hands
 * raised (hosts), and the main button: the microphone on the stage, else "Join voice" or "Raise hand".
 */
function ControlBar({
  s,
  composer,
  wide,
  hands,
  onRequests,
  onMusic,
  onJoin,
}: {
  s: VoiceSession
  /** Null when the chat has its own column. */
  composer: ReactNode
  wide: boolean
  hands: number
  onRequests: () => void
  onMusic: () => void
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
        {mod && hands > 0 ? (
          <Button size="icon" variant="secondary" onClick={onRequests} aria-label={t('voice.requestsN', { n: num(hands) })}>
            <ReactionIcon kind="hand" size={22} />
            <span className="absolute -top-1 -inset-e-1 grid h-5 min-w-5 place-items-center rounded-full bg-primary px-1 text-[11px] font-bold text-primary-foreground ring-2 ring-background tabular-nums">
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
              'flex h-12 shrink-0 items-center gap-2 rounded-full pe-5 ps-4 text-sm font-semibold transition-colors duration-150',
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
