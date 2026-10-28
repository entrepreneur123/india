'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { useSWRConfig } from 'swr'
import type { Badges, Message, Profile, VideoStatus } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { errorCode } from '@/lib/format'
import { useToast } from '@/components/ui/toast'
import { useI18n } from '@/components/i18n'
import { echoId, mergeLines, useOutbox } from './use-outbox'

const TYPING_THROTTLE_MS = 2000
const TYPING_TIMEOUT_MS = 6000

/** A line of mine on its way: its text, file, and the file's preview shown meanwhile. */
type Outgoing = { text: string | null; media: File | null; preview: string | null }

/** `live` = an open random match; threads (friends, past matches) carry messages, typing and calls. */
export function useMatchChat(matchId: string, partnerName: string, live: boolean) {
  const backend = useBackend()
  const toast = useToast()
  const { mutate } = useSWRConfig()
  const { t } = useI18n()
  const tRef = useRef(t) // the language can change mid-chat without re-subscribing
  tRef.current = t
  const [messages, setMessages] = useState<Message[] | null>(null)
  const [typing, setTyping] = useState(false)
  const [video, setVideoState] = useState<VideoStatus>('none')
  const videoRef = useRef<VideoStatus>('none')
  const setVideo = useCallback((v: VideoStatus) => {
    videoRef.current = v
    setVideoState(v)
  }, [])
  /** The call asked for (or on) is a video call; false: voice. */
  const [camera, setCamera] = useState(true)
  const [partnerLeft, setPartnerLeft] = useState(false)
  /** Threads: the chat is over (040: only friends write on). */
  const [ended, setEnded] = useState(false)
  const [partnerRead, setPartnerRead] = useState(0)
  /** 35: the partner's card after they turned VIP off or on (null: as the chat opened with). */
  const [partner, setPartner] = useState<Profile | null>(null)
  const lastTypingSent = useRef(0)
  const typingTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const append = useCallback((m: Message) => setMessages((prev) => mergeLines(prev ?? [], [m])), [])

  useEffect(() => {
    let alive = true
    setMessages(null)
    setTyping(false)
    setVideo('none')
    setPartnerLeft(false)
    setEnded(false)
    setPartnerRead(0)

    backend
      .history(matchId)
      .then((h) => {
        if (!alive) return
        setMessages((prev) => mergeLines(prev ?? [], h))
      })
      .catch((err) => {
        if (!alive) return
        setMessages([])
        toast.error(err)
      })

    const unsubscribe = backend.subscribe(matchId, (e) => {
      if (!alive) return
      switch (e.type) {
        case 'message':
          append(e.message)
          setTyping(false)
          break
        case 'typing':
          setTyping(e.on)
          if (typingTimer.current) clearTimeout(typingTimer.current)
          if (e.on) typingTimer.current = setTimeout(() => setTyping(false), TYPING_TIMEOUT_MS)
          break
        case 'video':
          setCamera(e.camera)
          // Only a refusal of my own pending request is news; an old "declined" from the first sync is not.
          if (e.status === 'declined' && videoRef.current === 'requested_by_me') {
            toast.show(tRef.current(e.camera ? 'chat.declined' : 'chat.voiceDeclined', { name: partnerName }))
          }
          setVideo(e.status === 'declined' ? 'none' : e.status)
          break
        case 'read':
          setPartnerRead((r) => Math.max(r, e.upto))
          break
        case 'ended':
          if (!live) setEnded(true)
          break
        case 'partner':
          setPartner(e.partner)
          break
        case 'partner_left':
          if (!live) break
          setPartnerLeft(true)
          setVideo('none')
          setTyping(false)
          break
      }
    }, { live })

    return () => {
      alive = false
      unsubscribe()
      if (typingTimer.current) clearTimeout(typingTimer.current)
    }
  }, [backend, matchId, append, toast, partnerName, live, setVideo])

  // Read ticks: what's on screen while the tab is visible counts as read.
  let lastIncoming = 0
  for (const m of messages ?? []) if (!m.fromMe) lastIncoming = m.id // sorted by id
  useEffect(() => {
    if (!lastIncoming) return
    const mark = () => {
      if (document.visibilityState !== 'visible') return
      // This chat's count drops at once (the Recent tab with it); the server's counts are read again once it has
      // the mark (it also marks the chat's notification read, so the bell follows).
      void mutate('badges', (b?: Badges) => (b?.chats[matchId] ? { ...b, chats: { ...b.chats, [matchId]: 0 } } : b), { revalidate: false })
      void backend.markRead(matchId, lastIncoming).then(() => mutate('badges'))
    }
    mark()
    document.addEventListener('visibilitychange', mark)
    return () => document.removeEventListener('visibilitychange', mark)
  }, [backend, matchId, lastIncoming, mutate])

  const notifyTyping = useCallback(() => {
    const now = Date.now()
    if (now - lastTypingSent.current < TYPING_THROTTLE_MS) return
    lastTypingSent.current = now
    backend.sendTyping(matchId)
  }, [backend, matchId])

  const outbox = useOutbox<Message, Outgoing>(
    matchId,
    setMessages,
    (id, line, clientId) => backend.send(id, line.text, line.media, clientId),
    (err) => {
      // The server refused a line in a chat that ended between non-friends: the friends bar takes the composer's place.
      if (!live && errorCode(err) === 'chat_closed') setEnded(true)
      // Offline is already said by the connection bar and the line's own "Not sent"; anything else is explained.
      if (errorCode(err) !== 'network') toast.error(err)
    },
    (line) => line.preview && URL.revokeObjectURL(line.preview),
  )

  /** The line shows at once (Slack, KakaoTalk); one that fails stays with "Retry" and "Delete". */
  const { send: post } = outbox
  const send = useCallback(
    async (text: string | null, media: File | null) => {
      const preview = media ? URL.createObjectURL(media) : null
      post({ text, media, preview }, (clientId) => ({
        id: echoId(), fromMe: true, text, at: Date.now(), clientId, state: 'sending',
        media: preview && media ? { url: preview, kind: media.type.startsWith('video') ? 'video' : 'image' } : null,
      }))
      return true
    },
    [post],
  )

  return { messages, typing, video, setVideo, camera, setCamera, partnerLeft, ended, partnerRead, partner, send, retry: outbox.retry, discard: outbox.discard, notifyTyping }
}
