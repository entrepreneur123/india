'use client'

import { AnimatePresence } from 'motion/react'
import * as m from 'motion/react-m'
import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from 'react'
import { ChevronUp, LogOut, Mic, MicOff } from 'lucide-react'
import type { VoiceAction, VoiceAudioState, VoiceLine, VoiceMember, VoiceReaction, VoiceRoom } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { useReported } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Avatar } from '@/components/ui/avatar'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'
import { VoiceRoomScreen } from './voice-room'
import { LiveBars, SpeakingRing } from './voice-stage'
import { memberOf, SPEAKING } from './voice-text'

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
  act: (a: VoiceAction) => Promise<boolean>
  send: (text: string) => Promise<boolean>
  /** After a tap, when the browser blocked the room's audio. */
  startAudio: () => void
}

const REACTION_MS = 2600

function useVoiceSession(roomId: string, onGone: () => void, ending: React.RefObject<boolean>): VoiceSession {
  const backend = useBackend()
  const toast = useToast()
  const { t } = useI18n()
  const [room, setRoom] = useState<VoiceRoom | null>(null)
  const [lines, setLines] = useState<VoiceLine[] | null>(null)
  const [reactions, setReactions] = useState<Reaction[]>([])
  const [audio, setAudio] = useState<VoiceAudioState | null>(null)
  const [levels] = useState(() => new LevelStore())
  const gone = useRef(onGone)
  gone.current = onGone
  const tRef = useRef(t)
  tRef.current = t

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
        case 'line':
          addLines([e.line])
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
      }
    })
    ;(async () => {
      try {
        const r = await backend.joinVoice(roomId)
        if (!alive) return
        setRoom(r)
        const history = await backend.voiceLines(roomId)
        if (alive) addLines(history)
      } catch (err) {
        if (!alive) return
        toast.error(err)
        gone.current()
      }
    })()
    return () => {
      alive = false
      timers.forEach(clearTimeout)
      unsubscribe()
      levels.set({})
      void backend.leaveVoice(roomId).catch(() => undefined)
    }
  }, [backend, roomId, addLines, levels, toast, ending])

  const me = room ? memberOf(room, room.meId) : null

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
        toast.error(err)
        return false
      }
    },
    [backend, roomId, toast],
  )

  const send = useCallback(
    async (text: string) => {
      try {
        addLines([await backend.sendVoice(roomId, text)])
        return true
      } catch (err) {
        toast.error(err)
        return false
      }
    },
    [backend, roomId, addLines, toast],
  )

  const startAudio = useCallback(() => void backend.voiceStartAudio(roomId).catch(() => undefined), [backend, roomId])

  // Browsers keep audio off until the page is touched: any tap anywhere turns it on.
  const needsTap = !!audio?.needsTap
  useEffect(() => {
    if (!needsTap) return
    document.addEventListener('pointerdown', startAudio, { once: true })
    return () => document.removeEventListener('pointerdown', startAudio)
  }, [needsTap, startAudio])

  return { room, me, lines, levels, reactions, audio, act, send, startAudio }
}

/**
 * A voice room the user is in: full screen while expanded, a small pill while they browse the rest of the app
 * (they keep listening, as in X Spaces). Unmounting it leaves the room.
 */
export function VoiceHost({
  roomId,
  expanded,
  pillHidden,
  onExpand,
  onMinimise,
  onClose,
}: {
  roomId: string
  expanded: boolean
  pillHidden: boolean
  onExpand: () => void
  onMinimise: () => void
  onClose: () => void
}) {
  const backend = useBackend()
  const toast = useToast()
  const { t } = useI18n()
  const ending = useRef(false)
  const s = useVoiceSession(roomId, onClose, ending)
  const [confirmEnd, setConfirmEnd] = useState(false)
  const [busy, setBusy] = useState(false)
  const isHost = s.me?.role === 'host'

  // The host's room would carry on without them, so the host's "leave" ends it (after a confirm).
  const leave = () => (isHost ? setConfirmEnd(true) : onClose())
  const end = async () => {
    setBusy(true)
    ending.current = true
    try {
      await backend.endVoice(roomId)
      toast.show(t('voice.endedByYou'), 'success')
      onClose()
    } catch (err) {
      ending.current = false
      toast.error(err)
    } finally {
      setBusy(false)
      setConfirmEnd(false)
    }
  }

  return (
    <>
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
      <Modal open={confirmEnd} onClose={() => setConfirmEnd(false)} title={t('voice.endTitle')} description={t('voice.endBody')}>
        <div className="flex gap-3 p-6 pt-5">
          <Button variant="outline" className="flex-1" onClick={() => setConfirmEnd(false)}>
            {t('common.cancel')}
          </Button>
          <Button variant="danger" className="flex-1" loading={busy} onClick={() => void end()}>
            {t('voice.endConfirm')}
          </Button>
        </div>
      </Modal>
    </>
  )
}

function VoicePill({ s, onExpand, onLeave }: { s: VoiceSession; onExpand: () => void; onLeave: () => void }) {
  const { t, num } = useI18n()
  const room = s.room!
  const loudest = useLoudest(s.levels)
  const speaker = loudest ? memberOf(room, loudest) : null
  const face = speaker ?? memberOf(room, room.hostId)
  const listening = room.members.filter((x) => x.seat === null).length
  const onStage = s.me?.seat !== null && s.me?.seat !== undefined
  const muted = s.me?.muted ?? true

  return (
    <m.div
      className="theme-night absolute inset-x-3 bottom-[calc(4.5rem+env(safe-area-inset-bottom))] z-20 md:inset-x-auto md:bottom-5 md:left-5 md:w-[22rem]"
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: 16 }}
      transition={{ type: 'tween', duration: 0.2, ease: [0.2, 0, 0, 1] }}
      role="region"
      aria-label={t('voice.pill', { title: room.title })}
    >
      <div className="flex items-center gap-1 rounded-2xl bg-card p-2 text-foreground shadow-e3 ring-1 ring-border">
        <button type="button" onClick={onExpand} className="flex min-w-0 flex-1 items-center gap-3 rounded-xl p-1 text-left">
          <span className="relative shrink-0">
            {face ? (
              <>
                <SpeakingRing store={s.levels} id={face.profile.id} />
                <Avatar name={face.profile.name} src={face.profile.photoUrl} size={40} />
              </>
            ) : null}
          </span>
          <span className="min-w-0">
            <span className="block truncate text-sm font-semibold">{room.title}</span>
            <span className="flex items-center gap-1.5 text-xs text-muted-foreground" aria-live="off">
              <LiveBars className="shrink-0" />
              <span className="truncate">
                {speaker ? t('voice.isSpeaking', { name: speaker.profile.name }) : t('voice.listening', { n: num(listening) })}
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
          >
            {muted ? <MicOff className="size-5" aria-hidden /> : <Mic className="size-5" aria-hidden />}
          </Button>
        ) : null}
        <Button size="icon" variant="ghost" onClick={onExpand} aria-label={t('voice.expand')}>
          <ChevronUp className="size-5" aria-hidden />
        </Button>
        <Button size="icon" variant="ghost" onClick={onLeave} aria-label={t('voice.leave')} className={cn('text-muted-foreground hover:text-destructive')}>
          <LogOut className="size-5" aria-hidden />
        </Button>
      </div>
    </m.div>
  )
}
