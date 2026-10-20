'use client'

import { useEffect, useId, useRef, useState } from 'react'
import { Check, ChevronUp, ListMusic, LoaderCircle, Pause, Play, SkipForward, Trash2, X } from 'lucide-react'
import type { VoiceAction, VoiceQueueItem, VoiceTrack } from '@/lib/backend'
import { thumbnail, youTubeId, YT_ENDED, YT_PAUSED, YT_PLAYING, loadYouTube, type YTPlayer } from '@/lib/youtube'
import { useI18n } from '@/components/i18n'
import { TextInput } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { TopicIcon } from '@/components/ui/topic-icons'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'

/** Where the shared track is now, in seconds (the room stores when it started, not a position). */
const position = (tr: VoiceTrack) => Math.max(0, ((tr.pausedAt ?? Date.now()) - tr.startedAt) / 1000)
const DRIFT_S = 2.5

/**
 * "Listen together": the room's YouTube track. Open, it plays in a 16:9 player at least 200×200 (YouTube's minimum);
 * closed, the local player is gone (no hidden background playback) and a slim bar says it's paused for you.
 */
export function MusicPanel({
  track,
  queue,
  isHost,
  open,
  onOpenChange,
  onQueue,
  act,
}: {
  track: VoiceTrack
  queue: VoiceQueueItem[]
  isHost: boolean
  open: boolean
  onOpenChange: (open: boolean) => void
  onQueue: () => void
  act: (a: VoiceAction) => Promise<boolean>
}) {
  const { t } = useI18n()
  const [playerTitle, setPlayerTitle] = useState('')
  const title = track.title || playerTitle || t('voice.ytVideo')
  const upNext = queue.filter((q) => !q.pending).length

  if (!open)
    return (
      <div className="mx-4 flex h-14 items-center gap-3 rounded-2xl bg-muted/70 pr-2 pl-3">
        <TopicIcon topic="music" size={22} />
        <span className="min-w-0 flex-1">
          <span className="block truncate text-sm font-semibold">{title}</span>
          <span className="block truncate text-xs text-muted-foreground">
            {track.pausedAt ? t('voice.pausedByHost') : t('voice.pausedForYou')}
          </span>
        </span>
        <Button size="sm" variant="brand" onClick={() => onOpenChange(true)}>
          <Play className="size-4 fill-current" aria-hidden /> {t('voice.listen')}
        </Button>
      </div>
    )

  return (
    <div className="px-4">
      <Player track={track} onTitle={setPlayerTitle} onEnded={isHost ? () => void act({ type: 'skip' }) : undefined} />
      <div className="mt-2 flex items-center gap-1">
        <span className="min-w-0 flex-1 pr-1">
          <span className="flex items-center gap-1.5">
            <TopicIcon topic="music" size={16} />
            <span className="truncate text-sm font-semibold">{title}</span>
          </span>
          <span className="block truncate text-xs text-muted-foreground">
            {track.pausedAt ? t('voice.pausedByHost') : t('voice.addedBy', { name: track.addedBy })}
          </span>
        </span>
        {isHost ? (
          <>
            <Button
              size="icon-sm"
              variant="ghost"
              onClick={() => void act({ type: track.pausedAt ? 'resume' : 'pause' })}
              aria-label={track.pausedAt ? t('voice.resume') : t('voice.pause')}
            >
              {track.pausedAt ? <Play className="size-4.5" aria-hidden /> : <Pause className="size-4.5" aria-hidden />}
            </Button>
            <Button size="icon-sm" variant="ghost" onClick={() => void act({ type: 'skip' })} aria-label={t('voice.skip')}>
              <SkipForward className="size-4.5" aria-hidden />
            </Button>
          </>
        ) : null}
        <Button size="sm" variant="ghost" onClick={onQueue} className="px-2.5" aria-label={t('voice.upNextN', { n: upNext })}>
          <ListMusic className="size-4.5" aria-hidden />
          <span className="tabular-nums">{upNext}</span>
        </Button>
        <Button size="icon-sm" variant="ghost" onClick={() => onOpenChange(false)} aria-label={t('voice.collapse')}>
          <ChevronUp className="size-4.5" aria-hidden />
        </Button>
      </div>
    </div>
  )
}

/**
 * The YouTube IFrame player (youtube-nocookie.com). The API script loads with the first track; until it is ready,
 * or when YouTube can't be reached, the thumbnail and a status stand in for it.
 */
function Player({ track, onTitle, onEnded }: { track: VoiceTrack; onTitle: (title: string) => void; onEnded?: () => void }) {
  const { t } = useI18n()
  const hostRef = useRef<HTMLDivElement>(null)
  const playerRef = useRef<YTPlayer | null>(null)
  const loadedId = useRef<string | null>(null)
  const trackRef = useRef(track)
  trackRef.current = track
  const endedRef = useRef(onEnded)
  endedRef.current = onEnded
  const titleRef = useRef(onTitle)
  titleRef.current = onTitle
  // failed: YouTube can't be reached; unavailable: this video won't play embedded (or here).
  const [state, setState] = useState<'loading' | 'ready' | 'failed' | 'unavailable'>('loading')
  const [thumbOk, setThumbOk] = useState(true)

  // One player for as long as the panel is open; leaving the room or closing the panel destroys it.
  useEffect(() => {
    let alive = true
    let player: YTPlayer | null = null
    loadYouTube().then(
      (YT) => {
        if (!alive || !hostRef.current) return
        const tr = trackRef.current
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
              setState('ready')
              titleRef.current(player.getVideoData().title ?? '')
            },
            onStateChange: (e) => {
              if (!alive || !player) return
              if (e.data === YT_PLAYING) titleRef.current(player.getVideoData().title ?? '')
              if (e.data === YT_ENDED) endedRef.current?.()
            },
            onError: () => alive && setState('unavailable'),
          },
        })
      },
      () => alive && setState('failed'),
    )
    return () => {
      alive = false
      playerRef.current = null
      try {
        player?.destroy()
      } catch {
        /* the iframe is already gone */
      }
    }
  }, [])

  // Follow the room: a new track, the host pausing or resuming.
  useEffect(() => {
    const p = playerRef.current
    if (!p || state !== 'ready') return
    if (loadedId.current !== track.videoId) {
      loadedId.current = track.videoId
      p.loadVideoById({ videoId: track.videoId, startSeconds: position(track) })
      if (track.pausedAt) p.pauseVideo()
      return
    }
    if (Math.abs(p.getCurrentTime() - position(track)) > DRIFT_S) p.seekTo(position(track), true)
    if (track.pausedAt) p.pauseVideo()
    else if (p.getPlayerState() === YT_PAUSED) p.playVideo()
  }, [state, track.videoId, track.startedAt, track.pausedAt, track])

  // Stay in step with everyone while playing (a slow phone or a buffering pause drifts behind).
  useEffect(() => {
    if (state !== 'ready') return
    const id = setInterval(() => {
      const p = playerRef.current
      const tr = trackRef.current
      if (p && !tr.pausedAt && p.getPlayerState() === YT_PLAYING && Math.abs(p.getCurrentTime() - position(tr)) > DRIFT_S)
        p.seekTo(position(tr), true)
    }, 5000)
    return () => clearInterval(id)
  }, [state])

  return (
    <div className="relative aspect-video min-h-[200px] w-full overflow-hidden rounded-2xl bg-black">
      <div ref={hostRef} className="absolute inset-0 [&>iframe]:size-full" />
      {state !== 'ready' ? (
        <div className={cn('absolute inset-0 grid place-items-center', state !== 'loading' && 'bg-black')}>
          {thumbOk ? (
            <img
              src={thumbnail(track.videoId)}
              alt=""
              className="absolute inset-0 size-full object-cover opacity-45"
              onError={() => setThumbOk(false)}
            />
          ) : null}
          <p className="relative flex max-w-[85%] items-center gap-2 rounded-full bg-black/65 px-4 py-2 text-center text-sm font-semibold text-white" role="status">
            {state === 'loading' ? <LoaderCircle className="size-4 shrink-0 animate-spin" aria-hidden /> : null}
            {state === 'loading' ? t('voice.ytConnecting') : state === 'failed' ? t('voice.ytFailed') : t('voice.ytUnavailable')}
          </p>
        </div>
      ) : null}
    </div>
  )
}

/** "Up next": the queue, the host's approvals, and the box to add (host) or suggest (everyone else) a link. */
export function QueueSheet({
  open,
  onClose,
  track,
  queue,
  isHost,
  meName,
  act,
}: {
  open: boolean
  onClose: () => void
  track: VoiceTrack | null
  queue: VoiceQueueItem[]
  isHost: boolean
  meName: string
  act: (a: VoiceAction) => Promise<boolean>
}) {
  const { t } = useI18n()
  const toast = useToast()
  const id = useId()
  const [link, setLink] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const next = queue.filter((q) => !q.pending)
  // The host sees every suggestion; others see only their own, waiting.
  const pending = queue.filter((q) => q.pending && (isHost || q.addedBy === meName))

  useEffect(() => {
    if (open) {
      setLink('')
      setError(null)
    }
  }, [open])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    const videoId = youTubeId(link)
    if (!videoId) {
      setError(t('voice.badLink'))
      return
    }
    setBusy(true)
    const ok = await act(isHost ? { type: 'addTrack', videoId } : { type: 'suggest', videoId })
    setBusy(false)
    if (ok) {
      setLink('')
      setError(null)
      toast.show(isHost ? t('voice.added') : t('voice.suggested'), 'success')
    }
  }

  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={t('voice.musicTitle')} description={isHost ? t('voice.djDesc') : t('voice.suggestDesc')}>
      <div className="flex flex-col gap-6 p-6 pt-4">
        <form onSubmit={(e) => void submit(e)} className="flex flex-col gap-1.5">
          <label htmlFor={`${id}-l`} className="sr-only">
            {t('voice.pasteLink')}
          </label>
          <div className="flex gap-2">
            <TextInput
              id={`${id}-l`}
              value={link}
              onChange={(e) => {
                setLink(e.target.value)
                setError(null)
              }}
              placeholder={t('voice.pasteLink')}
              inputMode="url"
              autoComplete="off"
              dir="ltr"
              aria-invalid={!!error}
              aria-describedby={`${id}-h`}
              data-autofocus
              className="h-11 min-w-0 flex-1"
            />
            <Button type="submit" variant="brand" className="h-11" loading={busy} disabled={!link.trim()}>
              {isHost ? t('voice.add') : t('voice.suggest')}
            </Button>
          </div>
          <p id={`${id}-h`} className={cn('text-xs leading-relaxed', error ? 'font-medium text-destructive' : 'text-muted-foreground')} role={error ? 'alert' : undefined}>
            {error ?? t('voice.linkHint')}
          </p>
        </form>

        {track ? (
          <section className="flex flex-col gap-2">
            <h3 className="text-sm font-semibold">{t('voice.nowPlayingLabel')}</h3>
            <TrackRow videoId={track.videoId} title={track.title || t('voice.ytVideo')} sub={t('voice.addedBy', { name: track.addedBy })} playing />
          </section>
        ) : null}

        {isHost && pending.length ? (
          <section className="flex flex-col gap-2">
            <h3 className="text-sm font-semibold">{t('voice.suggestions')}</h3>
            <ul className="flex flex-col gap-1">
              {pending.map((q) => (
                <li key={q.id}>
                  <TrackRow videoId={q.videoId} title={q.title || t('voice.ytVideo')} sub={t('voice.suggestedBy', { name: q.addedBy })}>
                    <Button size="icon-sm" variant="ghost" onClick={() => void act({ type: 'approve', itemId: q.id, accept: false })} aria-label={t('voice.decline')}>
                      <X className="size-4.5" aria-hidden />
                    </Button>
                    <Button size="icon-sm" variant="brand" onClick={() => void act({ type: 'approve', itemId: q.id, accept: true })} aria-label={t('voice.approve')}>
                      <Check className="size-4.5" aria-hidden />
                    </Button>
                  </TrackRow>
                </li>
              ))}
            </ul>
          </section>
        ) : null}

        <section className="flex flex-col gap-2">
          <h3 className="text-sm font-semibold">{t('voice.upNext')}</h3>
          {next.length === 0 && (isHost || pending.length === 0) ? (
            <p className="rounded-xl bg-muted px-4 py-3 text-sm text-muted-foreground">{t('voice.queueEmpty')}</p>
          ) : (
            <ul className="flex flex-col gap-1">
              {next.map((q) => (
                <li key={q.id}>
                  <TrackRow videoId={q.videoId} title={q.title || t('voice.ytVideo')} sub={t('voice.addedBy', { name: q.addedBy })}>
                    {isHost ? (
                      <>
                        <Button size="icon-sm" variant="ghost" onClick={() => void act({ type: 'removeItem', itemId: q.id })} aria-label={t('voice.remove')}>
                          <Trash2 className="size-4.5" aria-hidden />
                        </Button>
                        <Button size="icon-sm" variant="secondary" onClick={() => void act({ type: 'play', itemId: q.id })} aria-label={t('voice.playNow')}>
                          <Play className="size-4" aria-hidden />
                        </Button>
                      </>
                    ) : null}
                  </TrackRow>
                </li>
              ))}
              {isHost
                ? null
                : pending.map((q) => (
                    <li key={q.id}>
                      <TrackRow videoId={q.videoId} title={q.title || t('voice.ytVideo')} sub={t('voice.waiting')} />
                    </li>
                  ))}
            </ul>
          )}
        </section>
      </div>
    </Modal>
  )
}

function TrackRow({ videoId, title, sub, playing, children }: { videoId: string; title: string; sub: string; playing?: boolean; children?: React.ReactNode }) {
  const [thumbOk, setThumbOk] = useState(true)
  return (
    <div className="flex items-center gap-3 rounded-xl py-1.5">
      <span className="relative grid h-10 w-[4.5rem] shrink-0 place-items-center overflow-hidden rounded-lg bg-muted">
        {thumbOk ? (
          <img src={thumbnail(videoId)} alt="" loading="lazy" className="size-full object-cover" onError={() => setThumbOk(false)} />
        ) : (
          <TopicIcon topic="music" size={20} />
        )}
      </span>
      <span className="min-w-0 flex-1">
        <span className={cn('block truncate text-sm font-semibold', playing && 'text-primary-text')}>{title}</span>
        <span className="block truncate text-xs text-muted-foreground">{sub}</span>
      </span>
      {children ? <span className="flex shrink-0 items-center gap-1">{children}</span> : null}
    </div>
  )
}
