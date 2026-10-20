'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import type { Message, VideoStatus } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { useToast } from '@/components/ui/toast'
import { useI18n } from '@/components/i18n'

const TYPING_THROTTLE_MS = 2000
const TYPING_TIMEOUT_MS = 6000

/** `live` = an open random match; threads (friends, past matches) only carry messages and typing. */
export function useMatchChat(matchId: string, partnerName: string, live: boolean) {
  const backend = useBackend()
  const toast = useToast()
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
  const [partnerLeft, setPartnerLeft] = useState(false)
  const [partnerRead, setPartnerRead] = useState(0)
  const lastTypingSent = useRef(0)
  const typingTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const append = useCallback((m: Message) => {
    setMessages((prev) => {
      const list = prev ?? []
      return list.some((x) => x.id === m.id) ? list : [...list, m].sort((a, b) => a.id - b.id)
    })
  }, [])

  useEffect(() => {
    let alive = true
    setMessages(null)
    setTyping(false)
    setVideo('none')
    setPartnerLeft(false)
    setPartnerRead(0)

    backend
      .history(matchId)
      .then((h) => {
        if (!alive) return
        setMessages((prev) => {
          const current = prev ?? []
          const ids = new Set(h.map((m) => m.id))
          return [...h, ...current.filter((m) => !ids.has(m.id))].sort((a, b) => a.id - b.id)
        })
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
          if (!live) break
          // Only a refusal of my own pending request is news; an old "declined" from the first sync is not.
          if (e.status === 'declined' && videoRef.current === 'requested_by_me') {
            toast.show(tRef.current('chat.declined', { name: partnerName }))
          }
          setVideo(e.status === 'declined' ? 'none' : e.status)
          break
        case 'read':
          setPartnerRead((r) => Math.max(r, e.upto))
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
    const mark = () => { if (document.visibilityState === 'visible') backend.markRead(matchId, lastIncoming) }
    mark()
    document.addEventListener('visibilitychange', mark)
    return () => document.removeEventListener('visibilitychange', mark)
  }, [backend, matchId, lastIncoming])

  const notifyTyping = useCallback(() => {
    const now = Date.now()
    if (now - lastTypingSent.current < TYPING_THROTTLE_MS) return
    lastTypingSent.current = now
    backend.sendTyping(matchId)
  }, [backend, matchId])

  const send = useCallback(
    async (text: string | null, media: File | null) => {
      try {
        const msg = await backend.send(matchId, text, media)
        append(msg)
        return true
      } catch (err) {
        toast.error(err)
        return false
      }
    },
    [backend, matchId, append, toast],
  )

  return { messages, typing, video, setVideo, partnerLeft, partnerRead, send, notifyTyping }
}
