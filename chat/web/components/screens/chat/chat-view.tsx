'use client'

import { AnimatePresence } from 'motion/react'
import * as m from 'motion/react-m'
import { lazy, Suspense, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { ArrowLeft, BookUser, Check, ChevronDown, Clock, Flag, Phone, Shuffle, Undo2, UserPlus, Video } from 'lucide-react'
import { playRoomSound } from '@/lib/room-sounds'
import type { Profile } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { flagEmoji } from '@/lib/format'
import { nameAge } from '@/lib/looks'
import { wallVars } from '@/lib/walls'
import { EXIT_LOCK_MS as lockMs } from '@/lib/config'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { useSocialT } from '@/components/social/social-text'
import { Skeleton } from '@/components/ui/bits'
import { CallRequestModal } from '@/components/video/incoming-call'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { retryImport } from '@/lib/retry-import'
import { Composer } from './composer'
import { ExitButton } from './exit-button'
import { MessageBubble, TypingBubble } from './message-bubble'
import { LinkOpenProvider, LinkPanel, LinkSheet, useWideScreen } from './link-viewer'
import { ReportSheet } from './report-sheet'
import { useMatchChat } from './use-match-chat'
import { TrustBadge } from '@/components/trust/badge'
import { LookName, useLook } from '@/components/looks/look-name'
import { useLooksT } from '@/components/looks/looks-text'
import { useShop } from '@/components/looks/use-shop'
import { useFriendship } from '@/components/social/friend-button'
import { FriendContactsSheet } from '@/components/social/contacts'
import { UserAvatar } from '@/components/looks/user-avatar'

// The call screen (and, inside it, the LiveKit SDK) downloads only when a call is accepted.
const VideoCall = lazy(() => retryImport(() => import('@/components/video/video-call')).then((mod) => ({ default: mod.VideoCall })))

/**
 * One conversation. `random` is a live random match (exit lock, partner-left); `thread` is a message thread with a
 * friend or someone met before. Both can call (free, 051): voice or video, in a random chat and between friends.
 */
export function ChatView({
  matchId,
  partner,
  mode,
  visible = true,
  startedAt,
  onExit,
  onEnd,
  onLeave,
  onClose,
  onReported,
  onMinimise,
  onStatus,
  onTalked,
}: {
  matchId: string
  partner: Profile
  mode: 'random' | 'thread'
  /** False while another tab covers the chat (random chats stay alive in the background). */
  visible?: boolean
  /** Random only: when the chat opened here (the exit lock counts from it). */
  startedAt?: number
  /** Random only: the X was pressed after the lock (or the partner already left). */
  onExit?: () => void
  /** Random only, after the partner left: straight to the next person (`true`) or back to the lobby. */
  onEnd?: (next: boolean) => void
  /** Random only: "/n" (next person) or "/r" (lobby) typed in the chat; the exit lock still applies. */
  onLeave?: (next: boolean) => void
  /** Thread only: back arrow. */
  onClose?: () => void
  /** The person was reported (and blocked); the chat is over. */
  onReported: () => void
  /** Random only: back to the other tabs; the chat stays live behind its bar. */
  onMinimise?: () => void
  /** Random only: what the chat's bar shows while another tab is open (typing, messages not seen yet). */
  onStatus?: (s: { typing: boolean; unread: number }) => void
  /** Random only: whether both of us have written at least one line (then the chat can be rated when it ends). */
  onTalked?: (both: boolean) => void
}) {
  const backend = useBackend()
  const toast = useToast()
  const { t } = useI18n()
  const { me, openProfile, setTab } = useApp()
  // Each side's chat bubble (062), as both of us see it.
  const mySkin = useLook(me.id)?.bubble
  const theirSkin = useLook(partner.id)?.bubble
  const live = mode === 'random'
  const chat = useMatchChat(matchId, partner.name, live)
  const [reportOpen, setReportOpen] = useState(false)
  const [contactsOpen, setContactsOpen] = useState(false)
  const [callBusy, setCallBusy] = useState(false)
  const listRef = useRef<HTMLUListElement>(null)
  const stickToBottom = useRef(true)
  // "/n" = next, "/r" = lobby (random chats): never sent as a message; inside the exit lock only the lock's notice.
  const send = async (text: string | null, media: File | null) => {
    const cmd = mode === 'random' && !media ? text?.trim().toLowerCase() : null
    if (cmd !== '/n' && cmd !== '/r') return chat.send(text, media)
    if (!chat.partnerLeft && Date.now() - (startedAt ?? 0) < lockMs) {
      toast.show(t('error.exit_locked'))
      return false
    }
    // After the partner left there is nothing to leave on the server.
    const go = chat.partnerLeft ? onEnd : onLeave
    go?.(cmd === '/n')
    return true
  }
  const { st } = useSocialT()
  // 046: my chat wall, behind the messages and the text box (only I see it).
  const wall = wallVars(useShop().data?.wall)
  // A link opened from the chat: a column beside it on wide screens, a tall sheet on phones.
  const wide = useWideScreen()
  const [viewUrl, setViewUrl] = useState<string | null>(null)
  useEffect(() => setViewUrl(null), [matchId])

  // Messages that arrive while another tab is open count as unread for the bar, until the chat is shown again.
  const [unread, setUnread] = useState(0)
  const seen = useRef(0)
  const incoming = chat.messages?.filter((x) => !x.fromMe).length ?? 0
  useEffect(() => {
    if (visible) seen.current = incoming
    setUnread(visible ? 0 : Math.max(0, incoming - seen.current))
  }, [visible, incoming])
  const status = useRef(onStatus)
  status.current = onStatus
  useEffect(() => status.current?.({ typing: chat.typing, unread }), [chat.typing, unread])
  const talked = useRef(onTalked)
  talked.current = onTalked
  const both = incoming > 0 && !!chat.messages?.some((x) => x.fromMe)
  useEffect(() => {
    talked.current?.(both)
  }, [both])

  // Follow new messages unless the user scrolled up to read older ones.
  useLayoutEffect(() => {
    const el = listRef.current
    if (el && stickToBottom.current) el.scrollTop = el.scrollHeight
  }, [chat.messages, chat.typing])

  const requestCall = async (video: boolean) => {
    if (chat.partnerLeft || chat.video === 'requested_by_me' || chat.video === 'accepted') return
    setCallBusy(true)
    try {
      await backend.requestCall(matchId, video)
      chat.setCamera(video)
      chat.setVideo('requested_by_me')
    } catch (err) {
      toast.error(err)
    } finally {
      setCallBusy(false)
    }
  }

  const answerVideo = async (accept: boolean) => {
    try {
      await backend.answerVideo(matchId, accept)
      chat.setVideo(accept ? 'accepted' : 'none')
    } catch (err) {
      toast.error(err)
      chat.setVideo('none')
    }
  }

  const endVideo = () => {
    chat.setVideo('none')
    backend.endVideo(matchId).catch(() => undefined)
  }

  const ended = live && chat.partnerLeft
  // The partner left: a sound with the red line, so it is noticed (the owner, 2026-10-26).
  useEffect(() => {
    if (ended) playRoomSound('leave', true)
  }, [ended])
  // 040: a thread whose chat ended goes on only between friends; until then a friends bar replaces the composer.
  const friendship = useFriendship(partner.id, partner.name)
  const gated = !live && chat.ended && friendship.state !== null && friendship.state !== 'friends'
  // Calls: a random chat until it ends; a thread between friends (the server's call_open, 052).
  const callable = live ? !ended : friendship.state === 'friends'

  // Closing the chat (a thread) ends its call or my request, so neither is left standing on the server.
  const callNow = useRef(chat.video)
  callNow.current = chat.video
  useEffect(
    () => () => {
      if (callNow.current === 'accepted' || callNow.current === 'requested_by_me') void backend.endVideo(matchId).catch(() => undefined)
    },
    [backend, matchId],
  )

  // A call never runs off screen (camera and mic would stay live with no controls), and an incoming
  // request brings the chat back to the front so it can be answered.
  useEffect(() => {
    if (!live || visible) return
    if (chat.video === 'accepted') endVideo()
    else if (chat.video === 'requested_by_partner') setTab('random')
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [live, visible, chat.video])

  return (
    <div className="relative flex min-h-0 flex-1 bg-background">
      <LinkOpenProvider value={setViewUrl}>
      {/* While a call covers the chat, keyboard focus must not reach the chat behind it. */}
      <div className="flex min-h-0 min-w-0 flex-1 flex-col" inert={visible && chat.video === 'accepted'}>
        <header className="flex items-center gap-2 border-b border-border px-3 py-2 sm:px-5">
          {live && onMinimise ? (
            <Button size="icon" variant="ghost" onClick={onMinimise} aria-label={st('chatbar.minimise')} title={st('chatbar.minimise')}>
              <ChevronDown className="size-6" aria-hidden />
            </Button>
          ) : null}
          {live ? (
            <ExitButton
              startedAt={startedAt ?? 0}
              lockMs={chat.partnerLeft ? 0 : lockMs}
              onExit={() => onExit?.()}
              onLocked={() => toast.show(t('error.exit_locked'))}
            />
          ) : (
            <Button size="icon" variant="ghost" onClick={onClose} aria-label={t('common.back')}>
              <ArrowLeft className="size-5 rtl:rotate-180" aria-hidden />
            </Button>
          )}
          <button
            type="button"
            onClick={() => openProfile(partner.id, { matchId })}
            className="flex min-w-0 flex-1 items-center gap-3 rounded-full p-1 pe-3 text-start transition-colors duration-150 hover:bg-muted"
          >
            <UserAvatar userId={partner.id} name={partner.name} src={partner.photoUrl} size={40} />
            <span className="min-w-0">
              <span className="flex items-center gap-1.5 truncate text-[15px] font-semibold">
                {nameAge(partner.name, partner.age)} <TrustBadge userId={partner.id} size={16} /> <span aria-hidden>{flagEmoji(partner.country)}</span>
              </span>
              <span className="flex min-w-0 text-xs text-muted-foreground">
                {ended ? (
                  t('chat.left')
                ) : chat.typing ? (
                  <span className="text-primary-text">{t('chat.typing')}</span>
                ) : (
                  <LookName userId={partner.id} handle={partner.handle} className="font-semibold" />
                )}
              </span>
            </span>
          </button>
          {!live && friendship.state === 'friends' ? (
            <Button size="icon" variant="ghost" onClick={() => setContactsOpen(true)} aria-label={st('contacts.section')} title={st('contacts.section')}>
              <BookUser className="size-5" aria-hidden />
            </Button>
          ) : null}
          <Button
            size="icon"
            variant="ghost"
            onClick={() => setReportOpen(true)}
            aria-label={t('chat.report', { name: partner.name })}
            className="text-muted-foreground hover:text-destructive"
          >
            <Flag className="size-5" aria-hidden />
          </Button>
          {callable ? <CallButton busy={callBusy} disabled={chat.video === 'requested_by_me'} onCall={(video) => void requestCall(video)} /> : null}
        </header>

        <AnimatePresence>
          {chat.video === 'requested_by_me' ? (
            <m.div
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: 'auto', opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              transition={{ duration: 0.15 }}
              className="overflow-hidden"
            >
              <p className="flex items-center justify-center gap-2 bg-primary-soft px-4 py-2 text-center text-xs font-semibold text-primary-text">
                <span className="size-1.5 shrink-0 animate-pulse rounded-full bg-primary-text" aria-hidden />
                {t(chat.camera ? 'chat.videoWaiting' : 'chat.voiceWaiting', { name: partner.name })}
              </p>
            </m.div>
          ) : null}
        </AnimatePresence>

        <div className="flex min-h-0 flex-1 flex-col bg-chat-wall" style={wall}>
        <ul
          ref={listRef}
          onScroll={(e) => {
            const el = e.currentTarget
            stickToBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80
          }}
          className="flex w-full min-h-0 flex-1 flex-col overflow-y-auto px-[max(1.25rem,calc((100%-48rem)/2))] py-3 scrollbar-thin"
          aria-label={t('chat.conversation', { name: partner.name })}
        >
          {live ? (
            <li className="mx-auto mb-2 max-w-xs rounded-lg bg-bubble-in px-3 py-1.5 text-center text-xs leading-relaxed text-bubble-meta shadow-[0_1px_0.5px_rgb(11_20_26/0.13)]">
              {t('chat.intro', { name: partner.name })}
            </li>
          ) : null}
          {chat.messages === null ? (
            <>
              <Skeleton className="mt-3 h-10 w-48 self-start rounded-lg" />
              <Skeleton className="mt-3 h-10 w-40 self-end rounded-lg" />
              <Skeleton className="mt-3 h-10 w-56 self-start rounded-lg" />
            </>
          ) : (
            chat.messages.map((msg, i) => (
              <MessageBubble
                key={msg.id}
                message={msg}
                grouped={i > 0 && chat.messages![i - 1].fromMe === msg.fromMe}
                read={msg.fromMe && msg.id <= chat.partnerRead}
                skin={msg.fromMe ? mySkin : theirSkin}
              />
            ))
          )}
          <AnimatePresence>{chat.typing ? <TypingBubble key="typing" name={partner.name} /> : null}</AnimatePresence>
          {ended ? (
            <li role="alert" className="mx-auto mt-6 rounded-xl bg-destructive/10 px-4 py-2.5 text-sm font-bold text-destructive ring-1 ring-destructive/30">
              {t('chat.partnerLeft', { name: partner.name })}
            </li>
          ) : null}
        </ul>

        {ended ? (
          <div className="flex justify-center gap-3 border-t border-border bg-background px-4 py-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
            {onEnd ? (
              <>
                <Button variant="secondary" size="lg" onClick={() => onEnd(false)}>
                  <Undo2 className="size-5 rtl:-scale-x-100" aria-hidden /> {t('leave.lobby')}
                </Button>
                <Button variant="brand" size="lg" onClick={() => onEnd(true)} autoFocus>
                  <Shuffle className="size-5" aria-hidden /> {t('leave.next')}
                </Button>
              </>
            ) : (
              <Button variant="brand" size="lg" onClick={() => onExit?.()}>
                {t('chat.continue')}
              </Button>
            )}
          </div>
        ) : gated ? (
          <FriendsBar name={partner.name} f={friendship} />
        ) : (
          <Composer
            onSend={send}
            onTyping={chat.notifyTyping}
            allowMedia={live}
            placeholder={live ? t('chat.placeholder') : t('chat.messageTo', { name: partner.name })}
            // Over a wall the box's row gets a light veil, so its icons stay clear on dark walls too.
            className={wall ? 'bg-background/80 backdrop-blur-sm' : undefined}
          />
        )}
        </div>
      </div>
      </LinkOpenProvider>

      {viewUrl && wide ? (
        <LinkPanel
          url={viewUrl}
          onClose={() => setViewUrl(null)}
          className="w-[min(44vw,560px)] shrink-0 border-s border-border"
        />
      ) : null}
      {wide ? null : (
        <LinkSheet open={!!viewUrl && visible} onClose={() => setViewUrl(null)}>
          {viewUrl ? <LinkPanel url={viewUrl} onClose={() => setViewUrl(null)} /> : null}
        </LinkSheet>
      )}

      <ReportSheet open={reportOpen} onClose={() => setReportOpen(false)} partner={partner} matchId={matchId} onReported={onReported} />
      {contactsOpen ? <FriendContactsSheet open onClose={() => setContactsOpen(false)} userId={partner.id} name={partner.name} /> : null}

      <CallRequestModal open={visible && chat.video === 'requested_by_partner'} partner={partner} video={chat.camera} onAnswer={(accept) => void answerVideo(accept)} />

      <AnimatePresence>
        {visible && chat.video === 'accepted' ? (
          <Suspense key="call" fallback={null}>
            <VideoCall matchId={matchId} partner={partner} video={chat.camera} onEnd={endVideo} />
          </Suspense>
        ) : null}
      </AnimatePresence>
    </div>
  )
}

/**
 * An ended random chat with someone who isn't a friend (040): the history stays, a new line needs friendship. The bar
 * says so and holds the friend button; once they are friends the composer comes back.
 */
function FriendsBar({ name, f }: { name: string; f: ReturnType<typeof useFriendship> }) {
  const { t } = useI18n()
  const { lt } = useLooksT()
  const text = f.state === 'requested' ? lt('ended.waiting', { name }) : f.state === 'incoming' ? lt('ended.incoming', { name }) : lt('ended.add', { name })
  return (
    <div className="flex flex-wrap items-center gap-3 border-t border-border bg-background px-4 py-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] sm:flex-nowrap sm:px-5" role="status">
      <p className="min-w-0 flex-1 basis-56 text-sm leading-snug text-muted-foreground">{text}</p>
      {f.state === 'requested' ? (
        <Button variant="secondary" loading={f.busy} onClick={() => void f.withdraw()} aria-label={lt('friend.withdraw', { name })} className="shrink-0">
          <Clock className="size-4.5" aria-hidden /> {lt('friend.requested')}
        </Button>
      ) : f.state === 'incoming' ? (
        <Button variant="brand" loading={f.busy} onClick={() => void f.accept()} className="shrink-0">
          <Check className="size-4.5" aria-hidden /> {t('recent.accept')}
        </Button>
      ) : (
        <Button variant="brand" loading={f.busy} onClick={() => void f.add()} className="shrink-0">
          <UserPlus className="size-4.5" aria-hidden /> {t('profile.add')}
        </Button>
      )}
    </div>
  )
}

/** The call button: a small choice of a voice or a video call (free, 051). */
function CallButton({ busy, disabled, onCall }: { busy: boolean; disabled: boolean; onCall: (video: boolean) => void }) {
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
    <div ref={ref} className="relative">
      <Button
        size="icon"
        variant="ghost"
        onClick={() => setOpen((o) => !o)}
        loading={busy}
        disabled={disabled}
        aria-label={t('chat.call')}
        title={t('chat.call')}
        aria-haspopup="menu"
        aria-expanded={open}
        className="text-primary-text"
      >
        <Phone className="size-5" aria-hidden />
      </Button>
      {open ? (
        <div role="menu" className="absolute top-full inset-e-0 z-20 mt-1 flex w-48 flex-col rounded-2xl bg-card p-1.5 shadow-e3 ring-1 ring-border">
          {([false, true] as const).map((video) => (
            <button
              key={String(video)}
              type="button"
              role="menuitem"
              autoFocus={!video}
              onClick={() => {
                setOpen(false)
                onCall(video)
              }}
              className="flex h-11 items-center gap-3 rounded-xl px-3 text-start text-[15px] font-semibold transition-colors duration-150 hover:bg-muted"
            >
              {video ? <Video className="size-5 text-primary-text" aria-hidden /> : <Phone className="size-5 text-primary-text" aria-hidden />}
              {t(video ? 'chat.videoCall' : 'chat.voiceCall')}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  )
}
