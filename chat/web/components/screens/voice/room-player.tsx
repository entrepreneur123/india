'use client'

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { LoaderCircle } from 'lucide-react'
import type { VoiceAction, VoiceTrack } from '@/lib/backend'
import { loadYouTube, thumbnail, YT_BUFFERING, YT_ENDED, YT_PAUSED, YT_PLAYING, type YTPlayer } from '@/lib/youtube'
import { useI18n } from '@/components/i18n'
import { cn } from '@/lib/utils'

/** Where the shared track is now, in seconds (the room stores when it started, not a position). */
export const position = (tr: VoiceTrack) => Math.max(0, ((tr.pausedAt ?? Date.now()) - tr.startedAt) / 1000)
const DRIFT_S = 2.5
/** A track that should play but hasn't started this long after the player is ready: the browser blocked it. */
const BLOCKED_MS = 3000

export interface RoomPlayer {
  /** failed: YouTube can't be reached; unavailable: this video won't play embedded (or here). */
  status: 'loading' | 'ready' | 'failed' | 'unavailable'
  /** What the player knows about the video (the room's title wins when it has one). */
  title: string
  author: string
  seconds: number
  /** Should be playing but the browser is holding it back until a tap (autoplay rules). */
  blocked: boolean
  /** Starts it after that tap. */
  play: () => void
  /** A slot in the room screen shows the video there; with none, it plays on out of sight. */
  dock: (el: HTMLElement) => () => void
}

const PlayerContext = createContext<RoomPlayer | null>(null)

export const useRoomPlayer = () => useContext(PlayerContext)

/**
 * "Listen together": the room's one YouTube player (youtube-nocookie.com). It lives as long as I'm in the room, not
 * as long as a screen shows it: the room minimised to its bar, the player hidden, another tab open, the music plays
 * on. The iframe sits in a box on <body> that follows the slot the room screen shows it in (moving an iframe in the
 * page would reload it); with no slot it stays rendered, out of sight, so the browser keeps it playing.
 */
export function RoomPlayerProvider({
  track,
  isHost,
  shown,
  act,
  children,
}: {
  track: VoiceTrack | null
  isHost: boolean
  /** The room is open full screen (a slot may be on screen). */
  shown: boolean
  act: (a: VoiceAction) => Promise<boolean>
  children: ReactNode
}) {
  const boxRef = useRef<HTMLDivElement>(null)
  const hostRef = useRef<HTMLDivElement>(null)
  const playerRef = useRef<YTPlayer | null>(null)
  const loadedId = useRef<string | null>(null)
  const trackRef = useRef(track)
  trackRef.current = track
  const hostNow = useRef(isHost)
  hostNow.current = isHost
  const actRef = useRef(act)
  actRef.current = act
  const [status, setStatus] = useState<RoomPlayer['status']>('loading')
  const [meta, setMeta] = useState({ title: '', author: '', seconds: 0 })
  const [playing, setPlaying] = useState(false)
  const [blocked, setBlocked] = useState(false)
  const [slot, setSlot] = useState<HTMLElement | null>(null)
  const on = !!track

  // One player while the room has a track; it goes when the music stops or I leave the room.
  useEffect(() => {
    if (!on) return
    let alive = true
    let player: YTPlayer | null = null
    setStatus('loading')
    const tell = (p: YTPlayer) => {
      const d = p.getVideoData()
      setMeta({ title: d.title ?? '', author: d.author ?? '', seconds: p.getDuration?.() || 0 })
    }
    loadYouTube().then(
      (YT) => {
        const tr = trackRef.current
        if (!alive || !hostRef.current || !tr) return
        const el = document.createElement('div') // the API swaps this for its iframe
        hostRef.current.appendChild(el)
        loadedId.current = tr.videoId
        player = new YT.Player(el, {
          host: 'https://www.youtube-nocookie.com',
          videoId: tr.videoId,
          width: '100%',
          height: '100%',
          playerVars: { autoplay: tr.pausedAt ? 0 : 1, start: Math.floor(position(tr)), playsinline: 1, rel: 0, origin: location.origin },
          events: {
            onReady: () => {
              if (!alive || !player) return
              playerRef.current = player
              setStatus('ready')
              tell(player)
            },
            onStateChange: (e) => {
              if (!alive || !player) return
              setPlaying(e.data === YT_PLAYING || e.data === YT_BUFFERING)
              if (e.data === YT_PLAYING) {
                setBlocked(false)
                tell(player)
              }
              // The host's player moves the room on (the server ignores a skip of a track that already changed).
              const tr = trackRef.current
              if (e.data === YT_ENDED && hostNow.current && tr) void actRef.current({ type: 'skip', trackId: tr.id })
            },
            onError: () => alive && setStatus('unavailable'),
          },
        })
      },
      () => alive && setStatus('failed'),
    )
    return () => {
      alive = false
      playerRef.current = null
      loadedId.current = null
      setPlaying(false)
      setBlocked(false)
      try {
        player?.destroy()
      } catch {
        /* the iframe is already gone */
      }
      if (hostRef.current) hostRef.current.textContent = ''
    }
  }, [on])

  // Follow the room: a new track, the host pausing or resuming.
  useEffect(() => {
    const p = playerRef.current
    if (!p || status !== 'ready' || !track) return
    if (loadedId.current !== track.videoId) {
      loadedId.current = track.videoId
      setMeta({ title: '', author: '', seconds: 0 })
      p.loadVideoById({ videoId: track.videoId, startSeconds: position(track) })
      if (track.pausedAt) p.pauseVideo()
      return
    }
    if (Math.abs(p.getCurrentTime() - position(track)) > DRIFT_S) p.seekTo(position(track), true)
    if (track.pausedAt) p.pauseVideo()
    else if (p.getPlayerState() === YT_PAUSED) p.playVideo()
  }, [status, track?.videoId, track?.startedAt, track?.pausedAt, track])

  // Stay in step with everyone while playing (a slow phone or a buffering pause drifts behind).
  useEffect(() => {
    if (status !== 'ready') return
    const id = setInterval(() => {
      const p = playerRef.current
      const tr = trackRef.current
      if (p && tr && !tr.pausedAt && p.getPlayerState() === YT_PLAYING && Math.abs(p.getCurrentTime() - position(tr)) > DRIFT_S)
        p.seekTo(position(tr), true)
    }, 5000)
    return () => clearInterval(id)
  }, [status])

  // Ready, meant to play, still silent a moment later: the browser wants a tap first.
  const paused = !!track?.pausedAt
  useEffect(() => {
    if (status !== 'ready' || paused || playing) return setBlocked(false)
    const id = setTimeout(() => setBlocked(true), BLOCKED_MS)
    return () => clearTimeout(id)
  }, [status, paused, playing, track?.id])

  // The box follows its slot every frame while one is on screen (the room's top scrolls, the stage opens and
  // closes): same place and size, clipped to whatever scrolls around it. Otherwise it waits out of sight.
  const docked = shown ? slot : null
  useEffect(() => {
    const box = boxRef.current
    if (!box) return
    if (!docked) {
      box.style.cssText = HIDDEN
      box.inert = true
      return
    }
    box.inert = false
    const clips: HTMLElement[] = []
    for (let el = docked.parentElement; el && el !== document.body; el = el.parentElement) {
      const o = getComputedStyle(el)
      if (/(auto|scroll|hidden|clip)/.test(o.overflow + o.overflowX + o.overflowY)) clips.push(el)
    }
    let raf = 0
    let last = ''
    const follow = () => {
      const r = docked.getBoundingClientRect()
      let [top, right, bottom, left] = [r.top, r.right, r.bottom, r.left]
      for (const c of clips) {
        const cr = c.getBoundingClientRect()
        top = Math.max(top, cr.top)
        left = Math.max(left, cr.left)
        right = Math.min(right, cr.right)
        bottom = Math.min(bottom, cr.bottom)
      }
      const css =
        r.width < 1 || bottom - top < 1 || right - left < 1
          ? HIDDEN
          : `position:fixed;z-index:21;left:${r.left}px;top:${r.top}px;width:${r.width}px;height:${r.height}px;` +
            `border-radius:16px;overflow:hidden;clip-path:inset(${top - r.top}px ${r.right - right}px ${r.bottom - bottom}px ${left - r.left}px round 16px)`
      if (css !== last) box.style.cssText = last = css
      raf = requestAnimationFrame(follow)
    }
    follow()
    return () => cancelAnimationFrame(raf)
  }, [docked, on])

  const dock = useCallback((el: HTMLElement) => {
    setSlot(el)
    return () => setSlot((now) => (now === el ? null : now))
  }, [])
  const play = useCallback(() => {
    const p = playerRef.current
    const tr = trackRef.current
    if (!p || !tr) return
    p.seekTo(position(tr), true)
    p.playVideo()
  }, [])

  const api = useMemo<RoomPlayer>(
    () => ({ status, ...meta, blocked, play, dock }),
    [status, meta, blocked, play, dock],
  )

  return (
    <PlayerContext.Provider value={api}>
      {children}
      {on && typeof document !== 'undefined'
        ? createPortal(
            <div ref={boxRef} style={HIDDEN_STYLE} aria-hidden={!docked} className={cn(status !== 'ready' && '[&_iframe]:invisible')}>
              <div ref={hostRef} className="size-full [&>iframe]:size-full" />
            </div>,
            document.body,
          )
        : null}
    </PlayerContext.Provider>
  )
}

/** Out of sight but still rendered (a display:none iframe may stop playing): behind the app, see-through, inert. */
const HIDDEN = 'position:fixed;z-index:-1;left:0;bottom:0;width:200px;height:200px;opacity:0;pointer-events:none'
const HIDDEN_STYLE = { position: 'fixed', zIndex: -1, left: 0, bottom: 0, width: 200, height: 200, opacity: 0, pointerEvents: 'none' } as const

/**
 * Where the room's player shows: 16:9 and at least 200×200 (YouTube's minimum). Until the player is ready, or when
 * YouTube can't be reached, the thumbnail and a status stand in for it.
 */
export function PlayerSlot({ track, className }: { track: VoiceTrack; className?: string }) {
  const { t } = useI18n()
  const player = useRoomPlayer()
  const ref = useRef<HTMLDivElement>(null)
  const [thumbOk, setThumbOk] = useState(true)
  const dock = player?.dock
  useEffect(() => (dock && ref.current ? dock(ref.current) : undefined), [dock])
  const status = player?.status ?? 'loading'

  return (
    <div ref={ref} className={cn('relative aspect-video min-h-[200px] w-full overflow-hidden rounded-2xl bg-black', className)}>
      {status !== 'ready' ? (
        <div className={cn('absolute inset-0 grid place-items-center', status !== 'loading' && 'bg-black')}>
          {thumbOk ? (
            <img src={thumbnail(track.videoId)} alt="" className="absolute inset-0 size-full object-cover opacity-45" onError={() => setThumbOk(false)} />
          ) : null}
          <p className="relative flex max-w-[85%] items-center gap-2 rounded-full bg-black/65 px-4 py-2 text-center text-sm font-semibold text-white" role="status">
            {status === 'loading' ? <LoaderCircle className="size-4 shrink-0 animate-spin" aria-hidden /> : null}
            {status === 'loading' ? t('voice.ytConnecting') : status === 'failed' ? t('voice.ytFailed') : t('voice.ytUnavailable')}
          </p>
        </div>
      ) : null}
    </div>
  )
}
