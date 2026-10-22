'use client'

import { AnimatePresence } from 'motion/react'
import * as m from 'motion/react-m'
import { lazy, Suspense, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { ArrowLeft, ChevronDown, Flag, Shuffle, Undo2, Video } from 'lucide-react'
import type { Profile } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { flagEmoji } from '@/lib/format'
import { FREE_EXIT_LOCK_MS, PLUS_EXIT_LOCK_MS } from '@/lib/config'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { useSocialT } from '@/components/social/social-text'
import { Avatar } from '@/components/ui/avatar'
import { Skeleton } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
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

// The call screen (and, inside it, the LiveKit SDK) downloads only when a call is accepted.
const VideoCall = lazy(() => retryImport(() => import('@/components/video/video-call')).then((mod) => ({ default: mod.VideoCall })))

/**
 * One conversation. `random` is a live random match (exit lock, video, partner-left); `thread` is a message
 * thread with a friend or someone met before (the server keeps those as closed chats: text only, no video).
 */
export function ChatView({
  matchId,
  partner,
  mode,
  visible = true,
  startedAt,
  onExit,
  onEnd,
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
  const { entitlements, openPaywall, openProfile, setTab } = useApp()
  const live = mode === 'random'
  const chat = useMatchChat(matchId, partner.name, live)
  const [reportOpen, setReportOpen] = useState(false)
  const [videoBusy, setVideoBusy] = useState(false)
  const listRef = useRef<HTMLUListElement>(null)
  const stickToBottom = useRef(true)
  const lockMs = entitlements.premium ? PLUS_EXIT_LOCK_MS : FREE_EXIT_LOCK_MS
  const { st } = useSocialT()
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

  const requestVideo = async () => {
    if (!entitlements.premium) {
      openPaywall()
      return
    }
    if (chat.partnerLeft || chat.video === 'requested_by_me' || chat.video === 'accepted') return
    setVideoBusy(true)
    try {
      await backend.requestVideo(matchId)
      chat.setVideo('requested_by_me')
    } catch (err) {
      toast.error(err)
    } finally {
      setVideoBusy(false)
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
      <div className="flex min-h-0 min-w-0 flex-1 flex-col" inert={live && visible && chat.video === 'accepted'}>
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
            className="flex min-w-0 flex-1 items-center gap-3 rounded-full p-1 pr-3 text-left transition-colors duration-150 hover:bg-muted"
          >
            <Avatar name={partner.name} src={partner.photoUrl} size={40} />
            <span className="min-w-0">
              <span className="flex items-center gap-1.5 truncate text-[15px] font-semibold">
                {partner.name}, {partner.age} <TrustBadge userId={partner.id} size={16} /> <span aria-hidden>{flagEmoji(partner.country)}</span>
              </span>
              <span className="block truncate text-xs text-muted-foreground">
                {ended ? t('chat.left') : chat.typing ? <span className="text-primary-text">{t('chat.typing')}</span> : partner.city}
              </span>
            </span>
          </button>
          <Button
            size="icon"
            variant="ghost"
            onClick={() => setReportOpen(true)}
            aria-label={t('chat.report', { name: partner.name })}
            className="text-muted-foreground hover:text-destructive"
          >
            <Flag className="size-5" aria-hidden />
          </Button>
          {live ? (
            <Button
              size="icon"
              variant="brand"
              onClick={() => void requestVideo()}
              loading={videoBusy}
              disabled={ended || chat.video === 'requested_by_me'}
              aria-label={entitlements.premium ? t('chat.videoRequest') : t('chat.videoPlus')}
            >
              <Video className="size-5" aria-hidden />
            </Button>
          ) : null}
        </header>

        <AnimatePresence>
          {live && chat.video === 'requested_by_me' ? (
            <m.div
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: 'auto', opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              transition={{ duration: 0.15 }}
              className="overflow-hidden"
            >
              <p className="flex items-center justify-center gap-2 bg-primary-soft px-4 py-2 text-center text-xs font-semibold text-primary-text">
                <span className="size-1.5 shrink-0 animate-pulse rounded-full bg-primary-text" aria-hidden />
                {t('chat.videoWaiting', { name: partner.name })}
              </p>
            </m.div>
          ) : null}
        </AnimatePresence>

        <ul
          ref={listRef}
          onScroll={(e) => {
            const el = e.currentTarget
            stickToBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80
          }}
          className="flex w-full min-h-0 flex-1 flex-col overflow-y-auto bg-chat-wall px-[max(1.25rem,calc((100%-48rem)/2))] py-3 scrollbar-thin"
          aria-label={t('chat.conversation', { name: partner.name })}
        >
          {live ? (
            <li className="mx-auto mb-2 max-w-xs rounded-lg bg-bubble-in px-3 py-1.5 text-center text-xs leading-relaxed text-bubble-meta shadow-[0_1px_0.5px_rgb(11_20_26/0.13)]">
              {partner.city
                ? t('chat.introCity', { name: partner.name, city: partner.city })
                : t('chat.intro', { name: partner.name })}
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
              <MessageBubble key={msg.id} message={msg} grouped={i > 0 && chat.messages![i - 1].fromMe === msg.fromMe} read={msg.fromMe && msg.id <= chat.partnerRead} />
            ))
          )}
          <AnimatePresence>{chat.typing ? <TypingBubble key="typing" name={partner.name} /> : null}</AnimatePresence>
          {ended ? (
            <li className="mx-auto mt-6 rounded-lg bg-bubble-in px-4 py-2 text-xs font-semibold text-bubble-meta shadow-[0_1px_0.5px_rgb(11_20_26/0.13)]">
              {t('chat.partnerLeft', { name: partner.name })}
            </li>
          ) : null}
        </ul>

        {ended ? (
          <div className="flex justify-center gap-3 border-t border-border px-4 py-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
            {onEnd ? (
              <>
                <Button variant="secondary" size="lg" onClick={() => onEnd(false)}>
                  <Undo2 className="size-5" aria-hidden /> {t('leave.lobby')}
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
        ) : (
          <Composer
            onSend={chat.send}
            onTyping={chat.notifyTyping}
            allowMedia={live}
            placeholder={live ? t('chat.placeholder') : t('chat.messageTo', { name: partner.name })}
          />
        )}
      </div>
      </LinkOpenProvider>

      {viewUrl && wide ? (
        <LinkPanel
          url={viewUrl}
          onClose={() => setViewUrl(null)}
          className="w-[min(44vw,560px)] shrink-0 border-l border-border"
        />
      ) : null}
      {wide ? null : (
        <LinkSheet open={!!viewUrl && visible} onClose={() => setViewUrl(null)}>
          {viewUrl ? <LinkPanel url={viewUrl} onClose={() => setViewUrl(null)} /> : null}
        </LinkSheet>
      )}

      <ReportSheet open={reportOpen} onClose={() => setReportOpen(false)} partner={partner} matchId={matchId} onReported={onReported} />

      <Modal open={live && visible && chat.video === 'requested_by_partner'} onClose={() => void answerVideo(false)} hideHeader className="max-w-sm">
        <div className="flex flex-col items-center gap-4 px-6 pt-8 pb-6 text-center">
          <span className="relative">
            <span className="absolute inset-0 animate-ping-soft rounded-full border-2 border-primary-text" aria-hidden />
            <Avatar name={partner.name} src={partner.photoUrl} size={88} />
          </span>
          <div>
            <h2 className="text-lg font-semibold">{t('chat.videoIncoming', { name: partner.name })}</h2>
            <p className="mt-1 text-sm text-muted-foreground">{t('chat.videoIncomingBody')}</p>
          </div>
          <div className="flex w-full gap-3">
            <Button variant="outline" size="lg" className="flex-1" onClick={() => void answerVideo(false)}>
              {t('chat.decline')}
            </Button>
            <Button variant="brand" size="lg" className="flex-1" onClick={() => void answerVideo(true)} data-autofocus>
              {t('chat.accept')}
            </Button>
          </div>
        </div>
      </Modal>

      <AnimatePresence>
        {live && visible && chat.video === 'accepted' ? (
          <Suspense key="call" fallback={null}>
            <VideoCall matchId={matchId} partner={partner} onEnd={endVideo} />
          </Suspense>
        ) : null}
      </AnimatePresence>
    </div>
  )
}
