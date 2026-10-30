'use client'

import { AnimatePresence } from 'motion/react'
import * as m from 'motion/react-m'
import { useCallback, useEffect, useReducer, useRef, useState, useSyncExternalStore } from 'react'
import { ChevronUp, LogOut, Mic, MicOff } from 'lucide-react'
import { useSWRConfig, type Key } from 'swr'
import { BackendError, type VoiceAction, type VoiceRoomSummary, type VoiceAudioState, type VoiceLine, type VoiceMember, type VoiceReaction, type VoiceReads, type VoiceRoom, type VoiceVideo } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { errorCode } from '@/lib/format'
import { retryImport } from '@/lib/retry-import'
import { laterReads, movesReads, readByOthers } from '@/lib/voice-chat'
import { pollsReducer, POLLS_PER_READ, type PollAction, type PollMap } from '@/lib/voice-polls'
import { nextExpiry, TYPING_SEND_MS, typingIds, type TypingSeen } from '@/lib/typing'
import { nextHost } from '@/lib/voice-lost'
import { crowdChange, playRoomSound, type Crowd } from '@/lib/room-sounds'
import { useApp, useReported } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { TopicIcon } from '@/components/ui/topic-icons'
import { cn } from '@/lib/utils'
import { RoomPlayerProvider } from './room-player'
import { useRoomT } from './room-t'
import { VoiceRoomScreen } from './voice-room'
import { LiveBars, SpeakingRing } from './voice-stage'
import { isMod, memberOf, SPEAKING } from './voice-text'
import { UserAvatar } from '@/components/looks/user-avatar'

/**
 * Audio levels arrive several times a second. They live outside React state: each seat subscribes to its own
 * person's level, so only the rings of people talking re-render.
 */
export class LevelStore {
  private levels: Record<string, number> = {}
  private subs = new Set<() => void>()
  set(levels: Record<string, number>) {
    this.levels = levels
    this.subs.forEach((f) => f())
  }
  get = (id: string) => this.levels[id] ?? 0
  loudest = () => {
    let best: string | null = null
    let max = SPEAKING
    for (const [id, v] of Object.entries(this.levels)) if (v > max) [best, max] = [id, v]
    return best
  }
  subscribe = (f: () => void) => {
    this.subs.add(f)
    return () => void this.subs.delete(f)
  }
}

/** The rooms list's SWR keys (all rooms, or a search). */
const isRoomList = (k: Key) => k === 'voiceRooms' || (Array.isArray(k) && k[0] === 'voiceRooms')

export const useLevel = (store: LevelStore, id: string) => useSyncExternalStore(store.subscribe, () => store.get(id), () => 0)
export const useLoudest = (store: LevelStore) => useSyncExternalStore(store.subscribe, store.loudest, () => null)

export interface Reaction {
  key: number
  userId: string
  kind: VoiceReaction
  /** Horizontal start of its float, 0..1. */
  x: number
}

export interface VoiceSession {
  room: VoiceRoom | null
  me: VoiceMember | null
  lines: VoiceLine[] | null
  levels: LevelStore
  reactions: Reaction[]
  /** The live audio (null in the demo, or before it starts). */
  audio: VoiceAudioState | null
  /** Cameras on now, by user id (24). */
  videos: Record<string, VoiceVideo>
  /** My connection dropped (beats failing or realtime down): the room shows "Reconnecting…". */
  offline: boolean
  act: (a: VoiceAction) => Promise<boolean>
  /** `replyTo`: the line this one answers. */
  send: (text: string, replyTo?: number | null) => Promise<boolean>
  /** Checks the photo for nudity on this device, then sends it (refused with a message when it looks nude). */
  sendPhoto: (photo: File) => Promise<boolean>
  /** Posts a conversation starter card. */
  /** After a tap, when the browser blocked the room's audio. */
  startAudio: () => void
  /** 46: whether I hear the room (off when I come in; on when I take a seat). */
  sound: boolean
  setSound: (on: boolean) => void
  /** The polls of the poll lines on screen, by poll id (absent: still being read). */
  polls: PollMap
  createPoll: (question: string, options: string[]) => Promise<boolean>
  /** Shown at once, then as the server counted it. */
  votePoll: (pollId: number, choice: number) => void
  closePoll: (pollId: number) => Promise<boolean>
  /** People writing in the chat right now (user ids, in the room, never me), oldest first. */
  typing: string[]
  /** The text in my box changed: tells the room, at most every few seconds. */
  notifyTyping: () => void
  /** 104: my lines up to this id have been read by someone else (two ticks). */
  readBy: number
  /** I have seen the chat up to this line: sent only when it moves the room's marks. */
  markRead: (upto: number) => void
}

const REACTION_MS = 2600
const CAMERA_ERRORS: Partial<Record<string, 'video.camDenied' | 'video.noCamera' | 'video.deviceBusy'>> = {
  camera_denied: 'video.camDenied',
  camera_missing: 'video.noCamera',
  camera_busy: 'video.deviceBusy',
}

function useVoiceSession(roomId: string, onGone: () => void, ending: React.RefObject<boolean>): VoiceSession {
  const backend = useBackend()
  const { mutate } = useSWRConfig()
  const toast = useToast()
  const { t } = useI18n()
  const [room, setRoom] = useState<VoiceRoom | null>(null)
  const [lines, setLines] = useState<VoiceLine[] | null>(null)
  const [reactions, setReactions] = useState<Reaction[]>([])
  const [audio, setAudio] = useState<VoiceAudioState | null>(null)
  const [videos, setVideos] = useState<Record<string, VoiceVideo>>({})
  const [offline, setOffline] = useState(false)
  const [polls, dispatch] = useReducer(pollsReducer, {})
  const pollsRef = useRef(polls)
  pollsRef.current = polls
  /** Polls read with my own vote (a live update alone doesn't say whether I voted). */
  const mineKnown = useRef(new Set<number>())
  const dispatchPoll = useCallback((a: PollAction) => {
    if (a.type === 'mine') mineKnown.current.add(a.poll.id)
    dispatch(a)
  }, [])
  const [reads, setReads] = useState<VoiceReads | null>(null)
  const readsRef = useRef(reads)
  readsRef.current = reads
  const gotReads = useCallback((r: VoiceReads) => setReads((a) => laterReads(a, r)), [])
  const [levels] = useState(() => new LevelStore())
  const gone = useRef(onGone)
  gone.current = onGone
  const { askRoomKey } = useApp()
  const askKey = useRef(askRoomKey)
  askKey.current = askRoomKey
  const tRef = useRef(t)
  tRef.current = t
  const { rt } = useRoomT()
  const rtRef = useRef(rt)
  rtRef.current = rt
  /** Typing signals by user id (with when), and a tick to show them going stale. */
  const typingSeen = useRef<TypingSeen>(new Map())
  const [typingTick, setTypingTick] = useState(0)

  const addLines = useCallback((list: VoiceLine[]) => {
    setLines((prev) => {
      const current = prev ?? []
      const ids = new Set(current.map((l) => l.id))
      const added = list.filter((l) => !ids.has(l.id))
      // A long session keeps the latest 300 lines (the server keeps none: rooms are not recorded).
      return added.length ? [...current, ...added].sort((a, b) => a.id - b.id).slice(-300) : current
    })
  }, [])

  // Enter, follow everything that happens, and leave on the way out (minimising keeps this mounted).
  useEffect(() => {
    let alive = true
    let seq = 0
    const timers = new Set<ReturnType<typeof setTimeout>>()
    const unsubscribe = backend.subscribeVoice(roomId, (e) => {
      if (!alive) return
      switch (e.type) {
        case 'room':
          setRoom(e.room)
          break
        case 'levels':
          levels.set(e.levels)
          break
        case 'audio':
          setAudio(e.audio)
          break
        case 'videos':
          setVideos(e.videos)
          break
        case 'connection':
          setOffline(e.lost)
          break
        case 'line':
          addLines([e.line])
          // Their line is here: they're not typing it any more.
          if ((e.line.kind === 'text' || e.line.kind === 'photo') && typingSeen.current.delete(e.line.sender.id)) setTypingTick((n) => n + 1)
          break
        // 50: the host or a co-host hid lines, or emptied the chat (a note at the top says who).
        case 'hidden':
          setLines((list) => list?.filter((l) => !e.ids.includes(l.id)) ?? list)
          break
        case 'cleared':
          setLines([{ id: 0, kind: 'system', event: 'cleared', name: e.by, at: Date.now() }])
          break
        case 'typing':
          typingSeen.current.set(e.userId, { name: e.name, at: Date.now() })
          setTypingTick((n) => n + 1)
          break
        case 'poll':
          dispatchPoll({ type: 'room', poll: e.poll })
          break
        case 'read':
          gotReads(e.reads)
          break
        case 'resync':
          readPolls(true)
          void backend.voiceRead(roomId, 0).then(gotReads, () => undefined) // the marks may have moved meanwhile
          break
        case 'reaction': {
          const r: Reaction = { key: ++seq, userId: e.userId, kind: e.kind, x: 0.12 + Math.random() * 0.76 }
          setReactions((list) => [...list.slice(-11), r])
          const id = setTimeout(() => {
            timers.delete(id)
            setReactions((list) => list.filter((x) => x !== r))
          }, REACTION_MS)
          timers.add(id)
          break
        }
        case 'ended':
          if (!ending.current) toast.show(tRef.current('voice.ended'), 'info')
          gone.current()
          break
        case 'removed':
          toast.show(tRef.current('voice.removed'), 'info')
          gone.current()
          break
        case 'refused':
          toast.error(new BackendError(e.code))
          gone.current()
          break
        case 'idle':
          toast.show(tRef.current('voice.idleLeft'), 'info')
          gone.current()
          break
        case 'dropped':
          toast.show(rtRef.current('lostLeft'), 'info')
          gone.current()
          break
        case 'left':
          gone.current()
          break
      }
    })
    ;(async () => {
      try {
        const r = await backend.joinVoice(roomId)
        if (!alive) return
        setRoom(r)
        const history = await backend.voiceLines(roomId)
        if (!alive) return
        addLines(history)
        void backend.voiceRead(roomId, 0).then(gotReads, () => undefined)
      } catch (err) {
        if (!alive) return
        const code = errorCode(err)
        if (code === 'room_password') askKey.current(roomId, 'password')
        else if (code !== 'in_random_chat') toast.error(err) // a random chat has me now: nothing to say
        gone.current()
      }
    })()
    return () => {
      alive = false
      timers.forEach(clearTimeout)
      typingSeen.current.clear()
      unsubscribe()
      levels.set({})
      // The list shows it at once: my room gone after I end it, one person fewer after I leave.
      void backend.leaveVoice(roomId).catch(() => undefined).finally(() => void mutate(isRoomList))
    }
  }, [backend, roomId, addLines, gotReads, levels, toast, ending]) // not mutate: it is a new function on renders, and a re-run here leaves the room (a host's leave ends it)

  const me = room ? memberOf(room, room.meId) : null
  const meId = room?.meId ?? ''
  const markRead = useCallback(
    (upto: number) => {
      if (meId && movesReads(readsRef.current, meId, upto)) void backend.voiceRead(roomId, upto).then(gotReads, () => undefined)
    },
    [backend, roomId, meId, gotReads],
  )

  // Who's typing: re-read when a signal arrives, the member list changes, or the oldest one goes stale.
  const memberKey = room?.members.map((x) => x.profile.id).join(',') ?? ''
  const [typing, setTyping] = useState<string[]>([])
  useEffect(() => {
    const now = Date.now()
    for (const [id, x] of typingSeen.current) if (now - x.at > 60_000) typingSeen.current.delete(id) // forget old ones
    const ids = typingIds(typingSeen.current, now, new Set(memberKey ? memberKey.split(',') : []), room?.meId ?? '')
    setTyping((prev) => (prev.join(',') === ids.join(',') ? prev : ids))
    const next = nextExpiry(typingSeen.current, now)
    if (next === null) return
    const id = setTimeout(() => setTypingTick((n) => n + 1), next - now + 20)
    return () => clearTimeout(id)
  }, [typingTick, memberKey, room?.meId])

  // Chimes: I came in or left; someone else came or went, or anyone stepped on or off the stage (rooms up to 50
  // people; a crowd would ring all the time).
  const stageKey = room?.members.flatMap((x) => (x.seat === null ? [] : [x.profile.id])).join(',') ?? ''
  const crowd = useRef<Crowd | null>(null)
  useEffect(() => {
    if (!room) return
    const now: Crowd = { all: memberKey.split(','), stage: stageKey ? stageKey.split(',') : [] }
    const before = crowd.current
    crowd.current = now
    if (!before) return playRoomSound('join', true)
    const change = now.all.length <= 50 ? crowdChange(before, now, room.meId) : null
    // My own step on or off the stage always chimes, however fast I tap.
    if (change) playRoomSound(change, before.stage.includes(room.meId) !== now.stage.includes(room.meId))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [memberKey, stageKey])
  useEffect(() => () => void (crowd.current && playRoomSound('leave', true)), [])

  const lastTypingSent = useRef(0)
  const meName = me?.profile.name ?? ''
  const notifyTyping = useCallback(() => {
    const now = Date.now()
    if (!meName || now - lastTypingSent.current < TYPING_SEND_MS) return
    lastTypingSent.current = now
    backend.voiceTyping(roomId, meName)
  }, [backend, roomId, meName])

  // Poll lines show their votes: the ones not read yet are read (with my own vote) as they arrive; after the room
  // was read again following a gap, every open one is read again too.
  const lineIds = useRef<number[]>([])
  const reading = useRef(new Set<number>())
  const readPolls = useCallback(
    (all = false) => {
      const want = lineIds.current.filter((id) => !reading.current.has(id) && (all ? !pollsRef.current[id]?.closed : !mineKnown.current.has(id)))
      if (!want.length) return
      const ids = want.slice(-POLLS_PER_READ)
      ids.forEach((id) => reading.current.add(id))
      backend.voicePolls(ids).then(
        (list) => list.forEach((poll) => dispatchPoll({ type: 'mine', poll })),
        () => undefined,
      ).finally(() => ids.forEach((id) => reading.current.delete(id)))
    },
    [backend, dispatchPoll],
  )
  const pollKey = lines?.flatMap((l) => (l.kind === 'poll' ? [l.pollId] : [])).join(',') ?? ''
  useEffect(() => {
    lineIds.current = pollKey ? pollKey.split(',').map(Number) : []
    readPolls()
  }, [pollKey, readPolls])

  // Coming up on the stage (invited, or a raised hand accepted) is worth a word: the mic starts muted.
  const seat = me?.seat ?? null
  const prevSeat = useRef<number | null>(null)
  useEffect(() => {
    if (prevSeat.current === null && seat !== null && me?.role !== 'host') toast.show(t('voice.onStage'), 'success')
    prevSeat.current = seat
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [seat])

  // Someone reported from here is blocked: their lines go at once.
  useReported((id) => setLines((list) => list?.filter((l) => l.kind !== 'text' || l.sender.id !== id) ?? list))

  const act = useCallback(
    async (a: VoiceAction) => {
      try {
        await backend.voiceAction(roomId, a)
        return true
      } catch (err) {
        const camera = CAMERA_ERRORS[errorCode(err)]
        if (camera) toast.show(t(camera), 'error')
        else toast.error(err)
        return false
      }
    },
    [backend, roomId, toast, t],
  )

  const send = useCallback(
    async (text: string, replyTo?: number | null) => {
      try {
        addLines([await backend.sendVoice(roomId, text, replyTo ?? null)])
        return true
      } catch (err) {
        toast.error(err)
        return false
      }
    },
    [backend, roomId, addLines, toast],
  )

  const sendPhoto = useCallback(
    async (photo: File) => {
      if (!photo.type.startsWith('image/')) {
        toast.error(new BackendError('bad_media'))
        return false
      }
      // The model runs here, before anything is uploaded; a photo that can't be checked isn't sent either.
      try {
        const { classifyImage } = await retryImport(() => import('@/lib/nudity'))
        if (await classifyImage(photo)) {
          toast.error(new BackendError('nude_photo'))
          return false
        }
      } catch {
        toast.error(new BackendError('network'))
        return false
      }
      try {
        addLines([await backend.sendVoicePhoto(roomId, photo)])
        return true
      } catch (err) {
        toast.error(err)
        return false
      }
    },
    [backend, roomId, addLines, toast],
  )


  const createPoll = useCallback(
    async (question: string, options: string[]) => {
      try {
        addLines([await backend.voicePollCreate(roomId, question, options)])
        return true
      } catch (err) {
        toast.error(err)
        return false
      }
    },
    [backend, roomId, addLines, toast],
  )

  const votePoll = useCallback(
    (pollId: number, choice: number) => {
      const before = pollsRef.current[pollId]
      if (!before || before.closed || before.mine === choice) return
      dispatchPoll({ type: 'vote', id: pollId, choice })
      backend.voicePollVote(pollId, choice).then(
        (poll) => dispatchPoll({ type: 'mine', poll }),
        (err) => {
          dispatchPoll({ type: 'restore', poll: before })
          toast.error(err)
          // Closed meanwhile, or gone: show it as it is now.
          if (errorCode(err) === 'poll_closed') backend.voicePolls([pollId]).then((l) => l.forEach((poll) => dispatchPoll({ type: 'mine', poll })), () => undefined)
        },
      )
    },
    [backend, toast, dispatchPoll],
  )

  const closePoll = useCallback(
    async (pollId: number) => {
      try {
        dispatchPoll({ type: 'mine', poll: await backend.voicePollClose(pollId) })
        return true
      } catch (err) {
        toast.error(err)
        return false
      }
    },
    [backend, toast, dispatchPoll],
  )

  const startAudio = useCallback(() => void backend.voiceStartAudio(roomId).catch(() => undefined), [backend, roomId])
  const [sound, setSoundState] = useState(false)
  const setSound = useCallback(
    (on: boolean) => {
      setSoundState(on)
      void backend.voiceSound(roomId, on).catch(() => undefined)
    },
    [backend, roomId],
  )
  // On a seat I need to hear the people I talk with.
  const seated = me?.seat != null
  useEffect(() => {
    if (seated) setSound(true)
  }, [seated, setSound])

  // Browsers keep audio off until the page is touched: any tap anywhere turns it on (once I want to hear the room).
  const needsTap = !!audio?.needsTap && sound
  useEffect(() => {
    if (!needsTap) return
    document.addEventListener('pointerdown', startAudio, { once: true })
    return () => document.removeEventListener('pointerdown', startAudio)
  }, [needsTap, startAudio])

  // My camera is checked on this device like a call's video (lib/nudity): looking nude, it turns off for everyone.
  const myVideo = me ? videos[me.profile.id] : undefined
  useEffect(() => {
    if (!myVideo) return
    let stopped = false
    let watch: { stop(): void } | null = null
    const el = document.createElement('video')
    el.muted = true
    el.playsInline = true
    myVideo.attach(el)
    void el.play().catch(() => undefined)
    void retryImport(() => import('@/lib/nudity'))
      .then(({ watchNudity }) => watchNudity(el, (hide) => {
        if (!hide || stopped) return
        void backend.voiceAction(roomId, { type: 'camera', on: false }).catch(() => undefined)
        toast.show(t('voice.cameraNude'), 'error')
      }))
      .then((w) => (stopped ? w.stop() : (watch = w)))
      .catch(() => undefined) // no model on this device: the camera stays as it is, like a call
    return () => {
      stopped = true
      watch?.stop()
      myVideo.detach(el)
    }
  }, [myVideo, backend, roomId, toast, t])

  return {
    room, me, lines, levels, reactions, audio, videos, offline, act, send, sendPhoto, startAudio, sound, setSound,
    polls, createPoll, votePoll, closePoll, typing, notifyTyping, readBy: readByOthers(reads, meId), markRead,
  }
}

/** What the shell needs from the room I'm in (one live session at a time: see lib/live-session.ts). */
export interface VoiceControl {
  title: string
  isHost: boolean
  /** Leaves without asking: a co-host takes over the host's room, or with none it ends. */
  leave: () => Promise<boolean>
}

/**
 * A voice room the user is in: full screen while expanded, a bar above the navigation while they browse the rest of
 * the app (they keep listening and the music plays on, as on X Spaces or a WhatsApp call). Unmounting it leaves the
 * room; only Leave (or the shell, see VoiceControl) does that.
 */
export function VoiceHost({
  roomId,
  expanded,
  pillHidden,
  control,
  onExpand,
  onMinimise,
  onClose,
}: {
  roomId: string
  expanded: boolean
  pillHidden: boolean
  /** Filled in for the shell while I'm in the room. */
  control: React.RefObject<VoiceControl | null>
  onExpand: () => void
  onMinimise: () => void
  onClose: () => void
}) {
  const backend = useBackend()
  const { mutate } = useSWRConfig()
  const toast = useToast()
  const { t } = useI18n()
  const ending = useRef(false)
  const s = useVoiceSession(roomId, onClose, ending)
  const [confirmEnd, setConfirmEnd] = useState(false)
  const [busy, setBusy] = useState(false)
  const isHost = s.me?.role === 'host'
  // A co-host takes over when the host leaves (the server's voice_hand_over); with none, leaving ends the room.
  const heir = isHost && s.room ? nextHost(s.room) : null

  /** `quiet`: the shell says it its own way (a random search took me out of my room). */
  const end = async (quiet = false) => {
    setBusy(true)
    ending.current = true
    try {
      await backend.endVoice(roomId)
      // Gone from the list at once (then the list is read again).
      void mutate<VoiceRoomSummary[]>(isRoomList, (list) => list?.filter((r) => r.id !== roomId))
      if (!quiet) toast.show(t('voice.endedByYou'), 'success')
      onClose()
      return true
    } catch (err) {
      ending.current = false
      toast.error(err)
      return false
    } finally {
      setBusy(false)
      setConfirmEnd(false)
    }
  }
  // The host is asked first: hand the room to a co-host, or end it for everyone.
  const leave = () => (isHost ? setConfirmEnd(true) : onClose())

  const title = s.room?.title ?? ''
  useEffect(() => {
    control.current = { title, isHost, leave: async () => (isHost && !heir ? end(true) : (onClose(), true)) }
    return () => {
      control.current = null
    }
  })

  return (
    <RoomPlayerProvider track={s.room?.track ?? null} canControl={isMod(s.me)} shown={expanded} act={s.act}>
      <AnimatePresence>
        {expanded ? (
          <m.div
            key="room"
            className="absolute inset-0 z-20 flex flex-col"
            initial={{ opacity: 0, y: 32 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 32 }}
            transition={{ type: 'tween', duration: 0.22, ease: [0.2, 0, 0, 1] }}
          >
            <VoiceRoomScreen s={s} onMinimise={onMinimise} onLeave={leave} />
          </m.div>
        ) : null}
      </AnimatePresence>
      <AnimatePresence>
        {!expanded && !pillHidden && s.room ? <VoicePill s={s} onExpand={onExpand} onLeave={leave} /> : null}
      </AnimatePresence>
      <Modal
        open={confirmEnd}
        onClose={() => setConfirmEnd(false)}
        title={heir ? t('voice.handOverTitle') : t('voice.endTitle')}
        description={heir ? t('voice.handOverBody', { name: heir.profile.name }) : t('voice.endBody')}
      >
        {heir ? (
          <div className="flex flex-col gap-2 p-6 pt-5">
            <Button variant="brand" size="lg" disabled={busy} onClick={onClose}>
              {t('voice.handOverLeave', { name: heir.profile.name })}
            </Button>
            <Button variant="outline" size="lg" className="text-destructive hover:text-destructive" loading={busy} onClick={() => void end()}>
              {t('voice.endConfirm')}
            </Button>
            <Button variant="ghost" size="lg" onClick={() => setConfirmEnd(false)}>
              {t('common.cancel')}
            </Button>
          </div>
        ) : (
          <div className="flex gap-3 p-6 pt-5">
            <Button variant="outline" className="flex-1" onClick={() => setConfirmEnd(false)}>
              {t('common.cancel')}
            </Button>
            <Button variant="danger" className="flex-1" loading={busy} onClick={() => void end()}>
              {t('voice.endConfirm')}
            </Button>
          </div>
        )}
      </Modal>
    </RoomPlayerProvider>
  )
}

/**
 * The room while I'm elsewhere in the app: who is talking (or how many listen), the live mark and the song playing;
 * my microphone when I'm on the stage, and Leave. A tap anywhere else opens the room again.
 */
function VoicePill({ s, onExpand, onLeave }: { s: VoiceSession; onExpand: () => void; onLeave: () => void }) {
  const { t, num } = useI18n()
  const room = s.room!
  const loudest = useLoudest(s.levels)
  const speaker = loudest ? memberOf(room, loudest) : null
  const face = speaker ?? memberOf(room, room.hostId)
  const listening = room.members.filter((x) => x.seat === null).length
  const onStage = s.me?.seat !== null && s.me?.seat !== undefined
  const muted = s.me?.muted ?? true
  const song = room.track && !room.track.pausedAt ? room.track.title : null

  return (
    <m.div
      className="absolute inset-x-3 bottom-[calc(4.75rem+env(safe-area-inset-bottom))] z-20 md:inset-x-auto md:bottom-6 md:inset-s-6 md:w-[24rem]"
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: 16 }}
      transition={{ type: 'tween', duration: 0.2, ease: [0.2, 0, 0, 1] }}
      role="region"
      aria-label={t('voice.pill', { title: room.title })}
    >
      <div className="relative flex items-center gap-1 overflow-hidden rounded-[22px] bg-card p-2 text-foreground shadow-e3 ring-1 ring-border">
        {/* The room's colour, washed in from the left. */}
        <button type="button" onClick={onExpand} className="relative flex min-w-0 flex-1 items-center gap-3 rounded-2xl p-1 text-start" aria-label={`${t('voice.expand')}: ${room.title}`}>
          <span className="relative shrink-0">
            {face ? (
              <>
                <SpeakingRing store={s.levels} id={face.profile.id} />
                <UserAvatar userId={face.profile.id} name={face.profile.name} src={face.profile.photoUrl} size={42} />
              </>
            ) : null}
          </span>
          <span className="min-w-0">
            <span className="flex items-center gap-2">
              <span className="flex shrink-0 items-center gap-1 rounded-full bg-primary-strong px-1.5 py-0.5 text-[10px] leading-none font-bold tracking-wide text-primary-foreground uppercase">
                <LiveBars className="h-2.5 [&>span]:bg-primary-foreground" /> {t('voice.live')}
              </span>
              <span className="truncate text-sm font-bold">{room.title}</span>
            </span>
            <span className="mt-0.5 flex items-center gap-1.5 text-xs text-muted-foreground" aria-live="off">
              {song && !speaker ? <TopicIcon topic="music" size={14} className="shrink-0" /> : null}
              <span className="truncate">
                {speaker ? t('voice.isSpeaking', { name: speaker.profile.name }) : song || t('voice.listening', { n: num(listening) })}
              </span>
            </span>
          </span>
        </button>
        {onStage ? (
          <Button
            size="icon"
            variant={muted ? 'secondary' : 'brand'}
            onClick={() => void s.act({ type: 'mic', on: muted })}
            aria-label={muted ? t('voice.unmute') : t('voice.mute')}
            aria-pressed={!muted}
            className="relative"
          >
            {muted ? <MicOff className="size-5" aria-hidden /> : <Mic className="size-5" aria-hidden />}
          </Button>
        ) : null}
        <Button size="icon" variant="ghost" onClick={onExpand} aria-label={t('voice.expand')} className="relative max-sm:hidden">
          <ChevronUp className="size-5" aria-hidden />
        </Button>
        <Button size="icon" variant="ghost" onClick={onLeave} aria-label={t('voice.leave')} className={cn('relative text-destructive hover:text-destructive')}>
          <LogOut className="size-5 rtl:-scale-x-100" aria-hidden />
        </Button>
      </div>
    </m.div>
  )
}
