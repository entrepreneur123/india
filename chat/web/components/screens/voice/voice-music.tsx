'use client'

import { useEffect, useId, useRef, useState, type CSSProperties } from 'react'
import { ChevronUp, ListMusic, LoaderCircle, Pause, Play, Plus, SkipForward, Trash2 } from 'lucide-react'
import type { VoiceAction, VoiceQueueItem, VoiceTrack } from '@/lib/backend'
import { thumbnail, videoTitle, youTubeId, YT_ENDED, YT_PAUSED, YT_PLAYING, loadYouTube, type YTPlayer } from '@/lib/youtube'
import { useI18n } from '@/components/i18n'
import { TextInput } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { TopicIcon } from '@/components/ui/topic-icons'
import { useToast } from '@/components/ui/toast'
import { kindColor } from '@/components/screens/rooms/room-text'
import { cn } from '@/lib/utils'

/** Where the shared track is now, in seconds (the room stores when it started, not a position). */
const position = (tr: VoiceTrack) => Math.max(0, ((tr.pausedAt ?? Date.now()) - tr.startedAt) / 1000)
const DRIFT_S = 2.5

/** Requests in the order they'll play: most votes first, then the oldest. */
export const byVotes = (queue: VoiceQueueItem[]) =>
  queue.map((q, i) => [q, i] as const).sort((a, b) => b[0].votes - a[0].votes || a[1] - b[1]).map(([q]) => q)

/** "Song – Film · Artist" titles carry the artist after the last " · ": shown as the song, then its artist. */
const songOf = (title: string) => (title.includes(' · ') ? title.slice(0, title.lastIndexOf(' · ')) : title)
const artistOf = (title: string) => (title.includes(' · ') ? title.slice(title.lastIndexOf(' · ') + 3) : '')
const mmss = (s: number) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`

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
  const upNext = queue.length

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
      <Player track={track} onTitle={setPlayerTitle} onEnded={isHost ? () => void act({ type: 'skip', trackId: track.id }) : undefined} />
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
            <Button size="icon-sm" variant="ghost" onClick={() => void act({ type: 'skip', trackId: track.id })} aria-label={t('voice.skip')}>
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
function Player({
  track,
  onTitle,
  onEnded,
  onMeta,
  className,
}: {
  track: VoiceTrack
  onTitle: (title: string) => void
  onEnded?: () => void
  /** The channel and the length, once the player knows them. */
  onMeta?: (author: string, seconds: number) => void
  className?: string
}) {
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
  const metaRef = useRef(onMeta)
  metaRef.current = onMeta
  const tellMeta = (p: YTPlayer) => metaRef.current?.(p.getVideoData().author ?? '', p.getDuration?.() || 0)
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
              tellMeta(player)
            },
            onStateChange: (e) => {
              if (!alive || !player) return
              if (e.data === YT_PLAYING) {
                titleRef.current(player.getVideoData().title ?? '')
                tellMeta(player)
              }
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
    <div className={cn('relative aspect-video min-h-[200px] w-full overflow-hidden rounded-2xl bg-black', className)}>
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

/**
 * The music room's hero: the shared player big, the song, its progress and the DJ, and the requests by vote under it.
 * With nothing playing, an invitation to request the first song.
 */
export function MusicHero({
  track,
  queue,
  isHost,
  hostName,
  onQueue,
  act,
}: {
  track: VoiceTrack | null
  queue: VoiceQueueItem[]
  isHost: boolean
  hostName: string
  onQueue: () => void
  act: (a: VoiceAction) => Promise<boolean>
}) {
  const { t, num, plural } = useI18n()
  const [playerTitle, setPlayerTitle] = useState('')
  const [meta, setMeta] = useState<{ author: string; seconds: number }>({ author: '', seconds: 0 })
  const ordered = byVotes(queue)
  const title = track ? songOf(track.title) || playerTitle || t('voice.ytVideo') : ''
  const artist = track ? artistOf(track.title) || meta.author : ''
  useEffect(() => setMeta({ author: '', seconds: 0 }), [track?.id])

  return (
    <section aria-label={t('voice.musicTitle')} className="flex flex-col gap-3 px-4" style={{ '--k': kindColor('music') } as CSSProperties}>
      {track ? (
        <div>
          <Player
            track={track}
            onTitle={setPlayerTitle}
            onMeta={(author, seconds) => setMeta({ author, seconds })}
            onEnded={isHost ? () => void act({ type: 'skip', trackId: track.id }) : undefined}
            className="shadow-[0_18px_40px_-18px_color-mix(in_srgb,var(--k)_70%,transparent)]"
          />
          <div className="mt-3 flex items-center gap-3">
            <div className="min-w-0 flex-1">
              <h2 className="truncate text-lg leading-tight font-bold">{title}</h2>
              <p className="mt-0.5 truncate text-sm text-muted-foreground">
                {[artist, track.pausedAt ? t('voice.pausedByHost') : t('music.dj', { name: hostName })].filter(Boolean).join(' · ')}
              </p>
            </div>
            {isHost ? (
              <div className="flex shrink-0 items-center gap-1.5">
                <Button
                  size="icon"
                  variant="secondary"
                  onClick={() => void act({ type: track.pausedAt ? 'resume' : 'pause' })}
                  aria-label={track.pausedAt ? t('voice.resume') : t('voice.pause')}
                >
                  {track.pausedAt ? <Play className="size-5 fill-current" aria-hidden /> : <Pause className="size-5 fill-current" aria-hidden />}
                </Button>
                <Button size="icon" variant="brand" onClick={() => void act({ type: 'skip', trackId: track.id })} aria-label={t('music.next')} title={t('music.next')}>
                  <SkipForward className="size-5 fill-current" aria-hidden />
                </Button>
              </div>
            ) : null}
          </div>
          <Progress track={track} seconds={meta.seconds} />
          <p className="mt-1 text-xs text-muted-foreground">{t('voice.addedBy', { name: track.addedBy })}</p>
        </div>
      ) : (
        <div className="flex flex-col items-center gap-3 rounded-3xl bg-[color-mix(in_srgb,var(--k)_12%,var(--card))] px-6 py-8 text-center ring-1 ring-[color-mix(in_srgb,var(--k)_28%,transparent)]">
          <TopicIcon topic="music" size={52} />
          <div>
            <h2 className="text-lg font-bold">{t('music.nothing')}</h2>
            <p className="mt-1 text-sm leading-relaxed text-muted-foreground">{t('music.nothingBody')}</p>
          </div>
          <Button variant="brand" onClick={onQueue}>
            <Plus className="size-4.5" aria-hidden /> {isHost ? t('music.add') : t('music.request')}
          </Button>
        </div>
      )}

      <div className="rounded-2xl bg-muted/50 p-3">
        <div className="flex items-center justify-between gap-2 pb-1">
          <h3 className="flex min-w-0 items-baseline gap-2 text-sm font-semibold whitespace-nowrap">
            {t('voice.upNext')}
            <span className="truncate text-xs font-normal text-muted-foreground max-sm:hidden">{t('music.mostVoted')}</span>
          </h3>
          <Button size="sm" variant="ghost" onClick={onQueue} className="shrink-0 px-2.5" aria-label={`${t('music.request')} · ${num(queue.length)}`}>
            <ListMusic className="size-4.5" aria-hidden /> {t('music.request')}
          </Button>
        </div>
        {ordered.length === 0 ? (
          <p className="py-2 text-sm text-muted-foreground">{t('music.noRequests')}</p>
        ) : (
          <ol className="flex flex-col">
            {ordered.slice(0, 3).map((q) => (
              <li key={q.id}>
                <TrackRow videoId={q.videoId} title={songOf(q.title) || t('voice.ytVideo')} sub={`${t('voice.addedBy', { name: q.addedBy })} · ${plural(q.votes, 'music.votes.one', 'music.votes.other')}`}>
                  <VoteButton item={q} act={act} />
                </TrackRow>
              </li>
            ))}
          </ol>
        )}
      </div>
    </section>
  )
}

/** Where the song is, as a bar and times (the length comes from the player once it has loaded). */
function Progress({ track, seconds }: { track: VoiceTrack; seconds: number }) {
  const [, tick] = useState(0)
  useEffect(() => {
    if (track.pausedAt) return
    const id = setInterval(() => tick((x) => x + 1), 1000)
    return () => clearInterval(id)
  }, [track.pausedAt])
  const at = seconds ? Math.min(position(track), seconds) : position(track)
  return (
    <div className="mt-2.5">
      <div className="h-1.5 overflow-hidden rounded-full bg-muted" aria-hidden>
        <div className="h-full rounded-full bg-[var(--k)] transition-[width] duration-1000 ease-linear" style={{ width: seconds ? `${(at / seconds) * 100}%` : '0%' }} />
      </div>
      <div className="mt-1 flex justify-between text-[11px] text-muted-foreground tabular-nums" dir="ltr">
        <span>{mmss(at)}</span>
        <span>{seconds ? mmss(seconds) : ''}</span>
      </div>
    </div>
  )
}

/** One vote per person and song; tapping again takes it back. */
export function VoteButton({ item, act }: { item: VoiceQueueItem; act: (a: VoiceAction) => Promise<boolean> }) {
  const { t, num } = useI18n()
  const title = item.title || t('voice.ytVideo')
  return (
    <button
      type="button"
      onClick={() => void act({ type: 'vote', itemId: item.id, on: !item.voted })}
      aria-pressed={item.voted}
      aria-label={item.voted ? t('music.unvote', { title }) : t('music.vote', { title })}
      className={cn(
        'flex h-9 min-w-14 items-center justify-center gap-1 rounded-full px-3 text-sm font-bold tabular-nums transition-colors duration-150',
        item.voted ? 'bg-[var(--k,var(--primary))] text-white' : 'bg-card text-foreground ring-1 ring-border hover:bg-muted',
      )}
    >
      <ChevronUp className="size-4.5" strokeWidth={2.6} aria-hidden /> {num(item.votes)}
    </button>
  )
}

/** "Up next": the requests by vote, the box to add one, and (for the host) play now and remove. */
export function QueueSheet({
  open,
  onClose,
  track,
  queue,
  isHost,
  act,
}: {
  open: boolean
  onClose: () => void
  track: VoiceTrack | null
  queue: VoiceQueueItem[]
  isHost: boolean
  act: (a: VoiceAction) => Promise<boolean>
}) {
  const { t, plural } = useI18n()
  const toast = useToast()
  const id = useId()
  const [link, setLink] = useState('')
  const [error, setError] = useState<string | null>(null)
  /** The video being added (its title is looked up first); the box is free for the next link meanwhile. */
  const [pending, setPending] = useState<string | null>(null)
  const ordered = byVotes(queue)

  useEffect(() => {
    if (open) {
      setLink('')
      setError(null)
    }
  }, [open])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (pending) return
    const sent = link
    const videoId = youTubeId(sent)
    if (!videoId) {
      setError(t('voice.badLink'))
      return
    }
    setLink('')
    setError(null)
    setPending(videoId)
    // Everyone's request carries its real title (the room card and the queue show it to everyone).
    const title = await videoTitle(videoId)
    const ok = await act({ type: 'addTrack', videoId, title })
    setPending(null)
    if (ok) toast.show(isHost ? t('voice.added') : t('voice.suggested'), 'success')
    else {
      // Not added (the reason is in the toast): the link comes back, unless something new was typed meanwhile.
      setLink((now) => (now.trim() ? now : sent))
      setError(t('error.generic'))
    }
  }
  const adding = pending ? (
    <li aria-live="polite">
      <TrackRow videoId={pending} title={t('music.adding')} sub={`youtu.be/${pending}`}>
        <LoaderCircle className="size-4.5 animate-spin text-muted-foreground" aria-hidden />
      </TrackRow>
    </li>
  ) : null

  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={t('voice.musicTitle')} description={isHost ? t('voice.djDesc') : t('voice.suggestDesc')}>
      <div className="flex flex-col gap-6 p-6 pt-4" style={{ '--k': kindColor('music') } as CSSProperties}>
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
            <Button type="submit" variant="brand" className="h-11" loading={!!pending} disabled={!link.trim() || !!pending}>
              {isHost ? t('voice.add') : t('music.request')}
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

        <section className="flex flex-col gap-2">
          <div className="flex items-center justify-between gap-2">
            <h3 className="text-sm font-semibold">{t('voice.upNext')}</h3>
            {isHost && ordered.length ? (
              <Button size="sm" variant="secondary" onClick={() => void act({ type: 'skip', trackId: track?.id })}>
                <SkipForward className="size-4" aria-hidden /> {t('music.next')}
              </Button>
            ) : (
              <span className="text-xs text-muted-foreground">{t('music.mostVoted')}</span>
            )}
          </div>
          {ordered.length === 0 && !pending ? (
            <p className="rounded-xl bg-muted px-4 py-3 text-sm text-muted-foreground">{t('music.noRequests')}</p>
          ) : (
            <ol className="flex flex-col gap-1">
              {adding}
              {ordered.map((q) => (
                <li key={q.id}>
                  <TrackRow
                    videoId={q.videoId}
                    title={q.title || t('voice.ytVideo')}
                    sub={`${t('voice.addedBy', { name: q.addedBy })} · ${plural(q.votes, 'music.votes.one', 'music.votes.other')}`}
                  >
                    {isHost ? (
                      <>
                        <Button size="icon-sm" variant="ghost" onClick={() => void act({ type: 'removeItem', itemId: q.id })} aria-label={t('voice.remove')}>
                          <Trash2 className="size-4.5" aria-hidden />
                        </Button>
                        <Button size="icon-sm" variant="ghost" onClick={() => void act({ type: 'play', itemId: q.id })} aria-label={t('voice.playNow')}>
                          <Play className="size-4" aria-hidden />
                        </Button>
                      </>
                    ) : null}
                    <VoteButton item={q} act={act} />
                  </TrackRow>
                </li>
              ))}
            </ol>
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
