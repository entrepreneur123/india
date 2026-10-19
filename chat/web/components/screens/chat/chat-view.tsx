'use client'

import { AnimatePresence, motion } from 'motion/react'
import { useLayoutEffect, useRef, useState } from 'react'
import { ArrowLeft, Flag, Video } from 'lucide-react'
import type { Profile } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { flagEmoji } from '@/lib/format'
import { FREE_EXIT_LOCK_MS, PLUS_EXIT_LOCK_MS } from '@/lib/config'
import { useApp } from '@/components/app-state'
import { Avatar } from '@/components/ui/avatar'
import { Skeleton } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { Composer } from './composer'
import { ExitButton } from './exit-button'
import { MessageBubble, TypingBubble } from './message-bubble'
import { ReportSheet } from './report-sheet'
import { useMatchChat } from './use-match-chat'
import { VideoCall } from '@/components/video/video-call'

/**
 * One conversation. `random` is a live random match (exit lock, video, partner-left); `thread` is a message
 * thread with a friend or someone met before (the server keeps those as closed chats: text only, no video).
 */
export function ChatView({
  matchId,
  partner,
  mode,
  startedAt,
  onExit,
  onClose,
  onReported,
}: {
  matchId: string
  partner: Profile
  mode: 'random' | 'thread'
  /** Random only: when the chat opened here (the exit lock counts from it). */
  startedAt?: number
  /** Random only: the X was pressed after the lock (or the partner already left). */
  onExit?: () => void
  /** Thread only: back arrow. */
  onClose?: () => void
  /** The person was reported (and blocked); the chat is over. */
  onReported: () => void
}) {
  const backend = useBackend()
  const toast = useToast()
  const { entitlements, openPaywall, openProfile } = useApp()
  const live = mode === 'random'
  const chat = useMatchChat(matchId, partner.name, live)
  const [reportOpen, setReportOpen] = useState(false)
  const [videoBusy, setVideoBusy] = useState(false)
  const listRef = useRef<HTMLUListElement>(null)
  const stickToBottom = useRef(true)
  const lockMs = entitlements.premium ? PLUS_EXIT_LOCK_MS : FREE_EXIT_LOCK_MS

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

  return (
    <div className="relative flex min-h-0 flex-1 flex-col bg-background">
      <header className="flex items-center gap-3 border-b border-border px-3 py-2.5 sm:px-5">
        {live ? (
          <ExitButton
            startedAt={startedAt ?? 0}
            lockMs={chat.partnerLeft ? 0 : lockMs}
            onExit={() => onExit?.()}
            onLocked={() => toast.show('Wait a moment before leaving')}
          />
        ) : (
          <Button size="icon" variant="ghost" onClick={onClose} aria-label="Back">
            <ArrowLeft className="size-5" aria-hidden />
          </Button>
        )}
        <button
          type="button"
          onClick={() => openProfile(partner.id, { matchId })}
          className="group flex min-w-0 flex-1 items-center gap-3 rounded-2xl p-1 text-left transition-colors hover:bg-muted"
        >
          <Avatar name={partner.name} src={partner.photoUrl} size={42} />
          <span className="min-w-0">
            <span className="flex items-center gap-1.5 truncate text-[15px] font-bold">
              {partner.name}, {partner.age} <span aria-hidden>{flagEmoji(partner.country)}</span>
            </span>
            <span className="block truncate text-xs text-muted-foreground">
              {ended ? 'Left the chat' : chat.typing ? <span className="text-primary">typing…</span> : partner.city}
            </span>
          </span>
        </button>
        <Button
          size="icon"
          variant="ghost"
          onClick={() => setReportOpen(true)}
          aria-label={`Report ${partner.name}`}
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
            aria-label={entitlements.premium ? 'Request a video call' : 'Video calls are a Yaari Plus feature'}
          >
            <Video className="size-5" aria-hidden />
          </Button>
        ) : null}
      </header>

      <AnimatePresence>
        {live && chat.video === 'requested_by_me' ? (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            className="overflow-hidden"
          >
            <p className="flex items-center justify-center gap-2 bg-primary/10 py-2 text-xs font-semibold text-primary">
              <span className="size-1.5 animate-pulse rounded-full bg-primary" aria-hidden />
              Waiting for {partner.name} to accept the video call…
            </p>
          </motion.div>
        ) : null}
      </AnimatePresence>

      <ul
        ref={listRef}
        onScroll={(e) => {
          const el = e.currentTarget
          stickToBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80
        }}
        className="mx-auto flex w-full max-w-3xl min-h-0 flex-1 flex-col overflow-y-auto px-4 py-4 scrollbar-thin sm:px-6"
        aria-label={`Conversation with ${partner.name}`}
      >
        {live ? (
          <li className="mx-auto mb-2 max-w-xs text-center text-xs leading-relaxed text-muted-foreground">
            You matched with {partner.name}
            {partner.city ? ` from ${partner.city}` : ''}. Be kind — you can report and leave at any time.
          </li>
        ) : null}
        {chat.messages === null ? (
          <>
            <Skeleton className="mt-3 h-10 w-48 self-start rounded-3xl" />
            <Skeleton className="mt-3 h-10 w-40 self-end rounded-3xl" />
            <Skeleton className="mt-3 h-10 w-56 self-start rounded-3xl" />
          </>
        ) : (
          chat.messages.map((m, i) => (
            <MessageBubble key={m.id} message={m} grouped={i > 0 && chat.messages![i - 1].fromMe === m.fromMe} />
          ))
        )}
        <AnimatePresence>{chat.typing ? <TypingBubble key="typing" name={partner.name} /> : null}</AnimatePresence>
        {ended ? (
          <motion.li
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            className="mx-auto mt-6 rounded-full bg-muted px-4 py-2 text-xs font-semibold text-muted-foreground"
          >
            {partner.name} left the chat
          </motion.li>
        ) : null}
      </ul>

      {ended ? (
        <div className="flex justify-center gap-3 border-t border-border px-4 py-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
          <Button variant="brand" size="lg" onClick={() => onExit?.()}>
            Continue
          </Button>
        </div>
      ) : (
        <Composer
          onSend={chat.send}
          onTyping={chat.notifyTyping}
          allowMedia={live}
          placeholder={live ? 'Say something nice…' : `Message ${partner.name}`}
        />
      )}

      <ReportSheet
        open={reportOpen}
        onClose={() => setReportOpen(false)}
        partner={partner}
        matchId={matchId}
        onReported={onReported}
      />

      <Modal
        open={live && chat.video === 'requested_by_partner'}
        onClose={() => void answerVideo(false)}
        hideHeader
        className="max-w-sm"
      >
        <div className="flex flex-col items-center gap-4 px-6 pt-8 pb-6 text-center">
          <span className="relative">
            <span className="absolute -inset-3 animate-ping-soft rounded-full bg-primary/30" aria-hidden />
            <Avatar name={partner.name} src={partner.photoUrl} size={88} />
          </span>
          <div>
            <h2 className="text-lg font-bold">{partner.name} wants to video call</h2>
            <p className="mt-1 text-sm text-muted-foreground">Your camera turns on only if you accept.</p>
          </div>
          <div className="flex w-full gap-3">
            <Button variant="secondary" size="lg" className="flex-1" onClick={() => void answerVideo(false)}>
              Decline
            </Button>
            <Button variant="brand" size="lg" className="flex-1" onClick={() => void answerVideo(true)} data-autofocus>
              Accept
            </Button>
          </div>
        </div>
      </Modal>

      <AnimatePresence>
        {live && chat.video === 'accepted' ? (
          <VideoCall key="call" matchId={matchId} partner={partner} onEnd={endVideo} />
        ) : null}
      </AnimatePresence>
    </div>
  )
}
