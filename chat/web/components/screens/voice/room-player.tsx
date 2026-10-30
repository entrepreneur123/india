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
/** How often the player is checked against the room (a scrub in YouTube's own bar reaches the room by then). */
const SYNC_MS = 1000
/** After my own seek, pause or play for the room, the old state isn't forced back while the room's answer is on its way. */
const SEEK_HOLD_MS = 3000
/** A host's play or pause in YouTube's own controls counts once it has held this long (a drag on its bar may pause it). */
const SETTLE_MS = 600
/** A track that should play but hasn't started this long after the player is ready: the browser blocked it. */
const BLOCKED_MS = 3000
const VOLUME_KEY = 'yaari_room_volume'

export interface RoomPlayer {
  /** failed: YouTube can't be reached; unavailable: this video won't play embedded (or here). */
  status: 'loading' | 'ready' | 'failed' | 'unavailable'
  /** What the player knows about the video (the room's title wins when it has one). */
  title: string
  author: string
  seconds: number
  /** Should be playing but the browser is holding it back until a tap (autoplay rules). */
  blocked: boolean
  /** Starts it after that tap; also ends my own pause (back in step with the room). */
  play: () => void
  /** Paused on this screen only (my button, or YouTube's own controls); the room plays on. */
  localPaused: boolean
  pauseLocal: () => void
  /** This device's volume, 0-100, and mute. */
  volume: number
  muted: boolean
  setVolume: (volume: number) => void
  setMuted: (muted: boolean) => void
  /** The host and co-hosts: the song plays from here for everyone. */
  canControl: boolean
  seek: (seconds: number) => void
  /** A slot in the room screen shows the video there; with none, it plays on out of sight. */
  dock: (el: HTMLElement) => () => void
}

const PlayerContext = createContext<RoomPlayer | null>(null)

/**
 * A tap or a key in YouTube's own controls: the iframe has the focus. A pause by the phone or the browser (a call, the
 * tab in the background) doesn't move it, so that one stays on this screen.
 */
const inPlayer = (p: YTPlayer) => document.activeElement === p.getIframe()

export const useRoomPlayer = () => useContext(PlayerContext)

function savedVolume(): { volume: number; muted: boolean } {
  try {
    const v = JSON.parse(localStorage.getItem(VOLUME_KEY) ?? 'null') as { volume?: unknown; muted?: unknown } | null
    const volume = typeof v?.volume === 'number' && v.volume >= 0 && v.volume <= 100 ? Math.round(v.volume) : 100
    return { volume, muted: v?.muted === true }
  } catch {
    return { volume: 100, muted: false }
  }
}

/**
 * "Listen together": the room's one YouTube player (youtube-nocookie.com). It lives as long as I'm in the room, not
 * as long as a screen shows it: the room minimised to its bar, the player hidden, another tab open, the music plays
 * on. The iframe sits in a box on <body> that follows the slot the room screen shows it in (moving an iframe in the
 * page would reload it); with no slot it stays rendered, out of sight, so the browser keeps it playing.
 *
 * The room's position is the host's and co-hosts' (`canControl`): their play, pause, seek and skip act for everyone,
 * from our bar or from YouTube's own controls in the video (the owner, 2026-10-30: both work alike). Everyone may pause
 * on their own screen (resuming goes back to where the room is now), set their own volume (ours or YouTube's), and
 * pick a quality in YouTube's own settings menu; anyone else's scrub in YouTube's bar snaps back to the room.
 */
export function RoomPlayerProvider({
  track,
  canControl,
  shown,
  act,
  children,
}: {
  track: VoiceTrack | null
  /** The host or a co-host. */
  canControl: boolean
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
  const canRef = useRef(canControl)
  canRef.current = canControl
  const actRef = useRef(act)
  actRef.current = act
  const [status, setStatus] = useState<RoomPlayer['status']>('loading')
  const [meta, setMeta] = useState({ title: '', author: '', seconds: 0 })
  const [playing, setPlaying] = useState(false)
  const [blocked, setBlocked] = useState(false)
  const [slot, setSlot] = useState<HTMLElement | null>(null)
  const [localPaused, setLocalPausedState] = useState(false)
  const localRef = useRef(false)
  const setLocalPaused = useCallback((on: boolean) => {
    localRef.current = on
    setLocalPausedState(on)
  }, [])
  const [sound, setSound] = useState(savedVolume)
  const soundRef = useRef(sound)
  soundRef.current = sound
  const seekHold = useRef(0)
  /** The player's time at the last check, to tell a jump (a scrub) from playing on. */
  const lastAt = useRef<{ at: number; t: number; playing: boolean } | null>(null)
  const on = !!track

  /** Plays from where the room is now (or holds still there while the room is paused). */
  const resync = useCallback((p: YTPlayer, tr: VoiceTrack) => {
    p.seekTo(position(tr), true)
    if (tr.pausedAt) p.pauseVideo()
    else p.playVideo()
  }, [])

  /**
   * A host's or co-host's play or pause in YouTube's own controls becomes the room's once it has settled (as our button);
   * refused, the player goes back in step with the room.
   */
  const settle = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  /** The latest such change while it is on its way (0: none): the player isn't put back in step with the room meanwhile. */
  const settling = useRef(0)
  const settled = useRef(0)
  const fromPlayer = () => {
    const n = (settling.current = ++settled.current)
    // Only the latest change ends the wait, a moment after the room's answer (so the old state isn't forced back first).
    const done = () => {
      if (settling.current !== n) return
      settling.current = 0
      seekHold.current = Date.now() + SYNC_MS
    }
    clearTimeout(settle.current)
    settle.current = setTimeout(() => {
      const p = playerRef.current
      const tr = trackRef.current
      const state = p?.getPlayerState()
      const a: VoiceAction | null = !tr ? null : state === YT_PAUSED && !tr.pausedAt ? { type: 'pause' } : state === YT_PLAYING && tr.pausedAt ? { type: 'resume' } : null
      if (a) void actRef.current(a).finally(done)
      else done()
    }, SETTLE_MS)
  }

  const applySound = (p: YTPlayer) => {
    try {
      p.setVolume(soundRef.current.volume)
      if (soundRef.current.muted) p.mute()
      else p.unMute()
    } catch {
      /* an old player without these: its own controls still work */
    }
  }

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
          // YouTube's own controls stay: its settings menu is the only way to pick a quality (the IFrame API's
          // setPlaybackQuality does nothing any more).
          playerVars: {
            autoplay: tr.pausedAt || localRef.current ? 0 : 1, start: Math.floor(position(tr)), playsinline: 1, rel: 0, origin: location.origin,
          },
          events: {
            onReady: () => {
              if (!alive || !player) return
              playerRef.current = player
              applySound(player)
              setStatus('ready')
              tell(player)
            },
            onStateChange: (e) => {
              if (!alive || !player) return
              const now = trackRef.current
              setPlaying(e.data === YT_PLAYING || e.data === YT_BUFFERING)
              if (e.data === YT_PLAYING) {
                setBlocked(false)
                tell(player)
                if (now?.pausedAt) {
                  // The room is paused: a host's or co-host's play in YouTube resumes it for everyone; anyone else's
                  // doesn't start it on one screen.
                  if (canRef.current && inPlayer(player)) fromPlayer()
                  else player.pauseVideo()
                } else if (localRef.current && now) {
                  // My own pause ended from YouTube's own button: back to where the room is.
                  setLocalPaused(false)
                  player.seekTo(position(now), true)
                }
              }
              // Paused while the room plays: a host's or co-host's pause in YouTube pauses the room (as our button);
              // anyone else's, or the phone's, pauses this screen only.
              if (e.data === YT_PAUSED && now && !now.pausedAt) {
                if (canRef.current && inPlayer(player)) fromPlayer()
                else setLocalPaused(true)
              }
              // A host's or co-host's player moves the room on (the server ignores a skip of a track that already changed).
              if (e.data === YT_ENDED && canRef.current && now) void actRef.current({ type: 'skip', trackId: now.id })
            },
            onError: () => alive && setStatus('unavailable'),
          },
        })
      },
      () => alive && setStatus('failed'),
    )
    return () => {
      alive = false
      clearTimeout(settle.current)
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [on])

  // Follow the room: a new track, a pause, a resume or a seek by the host or a co-host.
  useEffect(() => {
    const p = playerRef.current
    if (!p || status !== 'ready' || !track) return
    if (loadedId.current !== track.videoId) {
      loadedId.current = track.videoId
      setMeta({ title: '', author: '', seconds: 0 })
      p.loadVideoById({ videoId: track.videoId, startSeconds: position(track) })
      if (track.pausedAt || localRef.current) p.pauseVideo()
      return
    }
    if (localRef.current) return // paused here: catches up when I resume
    if (Math.abs(p.getCurrentTime() - position(track)) > DRIFT_S) p.seekTo(position(track), true)
    if (track.pausedAt) p.pauseVideo()
    else if (p.getPlayerState() === YT_PAUSED) p.playVideo()
  }, [status, track?.videoId, track?.startedAt, track?.pausedAt, track])

  // Stay in step with everyone (a slow phone or a buffering pause drifts behind; a listener's scrub in YouTube's own
  // bar jumps away): back to the room's position. A host's or co-host's scrub there moves the room instead. Not while
  // paused here. YouTube's own volume, changed in the video, becomes mine (our slider shows it).
  useEffect(() => {
    if (status !== 'ready') return
    const id = setInterval(() => {
      const p = playerRef.current
      const tr = trackRef.current
      if (!p || !tr) return
      const state = p.getPlayerState()
      const at = p.getCurrentTime()
      const last = lastAt.current
      const now = Date.now()
      lastAt.current = { at, t: now, playing: state === YT_PLAYING }
      const mine = inPlayer(p)
      if (mine && (p.getVolume() !== soundRef.current.volume || p.isMuted() !== soundRef.current.muted)) {
        setSound({ volume: p.getVolume(), muted: p.isMuted() })
      }
      if (localRef.current) return
      const jumped = !!last && Math.abs(at - (last.at + (last.playing ? (now - last.t) / 1000 : 0))) > DRIFT_S
      if (jumped && mine && canRef.current && Math.abs(at - position(tr)) > DRIFT_S) return seek(at)
      if (now < seekHold.current || settling.current) return
      if (tr.pausedAt) {
        if (state === YT_PLAYING) p.pauseVideo()
        if (state === YT_PAUSED && Math.abs(at - position(tr)) > DRIFT_S) p.seekTo(position(tr), true)
      } else if (state === YT_PLAYING && Math.abs(at - position(tr)) > DRIFT_S) {
        p.seekTo(position(tr), true)
      }
    }, SYNC_MS)
    return () => clearInterval(id)
  }, [status])

  // Ready, meant to play, still silent a moment later: the browser wants a tap first (not when I paused it).
  const paused = !!track?.pausedAt
  useEffect(() => {
    if (status !== 'ready' || paused || playing || localPaused) return setBlocked(false)
    const id = setTimeout(() => setBlocked(true), BLOCKED_MS)
    return () => clearTimeout(id)
  }, [status, paused, playing, localPaused, track?.id])

  // My volume, kept for the next room too.
  useEffect(() => {
    const p = playerRef.current
    if (p && status === 'ready') applySound(p)
    try {
      localStorage.setItem(VOLUME_KEY, JSON.stringify(sound))
    } catch {
      /* private mode: it lasts for this visit */
    }
  }, [sound, status])

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
    setLocalPaused(false)
    if (p && tr) resync(p, tr)
  }, [resync, setLocalPaused])
  const pauseLocal = useCallback(() => {
    setLocalPaused(true)
    playerRef.current?.pauseVideo()
  }, [setLocalPaused])
  const setVolume = useCallback((volume: number) => setSound({ volume: Math.max(0, Math.min(100, Math.round(volume))), muted: volume <= 0 }), [])
  const setMuted = useCallback((muted: boolean) => setSound((s) => ({ volume: !muted && s.volume === 0 ? 50 : s.volume, muted })), [])
  const seek = useCallback((seconds: number) => {
    const tr = trackRef.current
    if (!canRef.current || !tr) return
    const at = Math.max(0, seconds)
    seekHold.current = Date.now() + SEEK_HOLD_MS
    playerRef.current?.seekTo(at, true) // at once here; everyone else follows the room's change
    void actRef.current({ type: 'seek', ms: at * 1000 }).then((ok) => {
      if (!ok) seekHold.current = 0 // refused: back in step with the room on the next check
    })
  }, [])

  const api = useMemo<RoomPlayer>(
    () => ({
      status, ...meta, blocked, play, localPaused, pauseLocal, volume: sound.volume, muted: sound.muted, setVolume, setMuted,
      canControl, seek, dock,
    }),
    [status, meta, blocked, play, localPaused, pauseLocal, sound, setVolume, setMuted, canControl, seek, dock],
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
