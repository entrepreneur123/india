'use client'

import { useEffect, useId, useState, type CSSProperties, type ReactNode } from 'react'
import { ChevronDown, ChevronUp, Headphones, ListMusic, LoaderCircle, Pause, Play, Settings, SkipForward, Square, Trash2, Volume1, Volume2, VolumeX } from 'lucide-react'
import type { VoiceAction, VoiceQueueItem, VoiceTrack } from '@/lib/backend'
import { thumbnail, videoTitle, youTubeId } from '@/lib/youtube'
import { useI18n } from '@/components/i18n'
import { TextInput } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { TopicIcon } from '@/components/ui/topic-icons'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'
import { PlayerSlot, position, useRoomPlayer } from './room-player'
import { useRoomT } from './room-t'
import { LiveBars } from './voice-stage'

/** Requests in the order they'll play: most votes first, then the oldest. */
export const byVotes = (queue: VoiceQueueItem[]) =>
  queue.map((q, i) => [q, i] as const).sort((a, b) => b[0].votes - a[0].votes || a[1] - b[1]).map(([q]) => q)

const mmss = (s: number) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`

/**
 * "Listen together": the video with its controls, or (hidden) a compact now-playing strip.
 * Hiding only shrinks it: the room's player (room-player.tsx) plays on either way. `canDJ`: the host or a co-host.
 */
export function MusicPanel({
  track,
  queue,
  canDJ,
  open,
  onOpenChange,
  onQueue,
  act,
}: {
  track: VoiceTrack
  queue: VoiceQueueItem[]
  canDJ: boolean
  open: boolean
  onOpenChange: (open: boolean) => void
  onQueue: () => void
  act: (a: VoiceAction) => Promise<boolean>
}) {
  const { t } = useI18n()
  const { rt } = useRoomT()
  const player = useRoomPlayer()
  const title = track.title || player?.title || t('voice.ytVideo')
  const upNext = queue.length
  const localPaused = !!player?.localPaused && !track.pausedAt
  // The host and co-hosts pause the room; anyone else pauses their own screen (the room plays on).
  const playPause = canDJ ? (
    <Button
      size="icon-sm"
      variant="ghost"
      onClick={() => void act({ type: track.pausedAt ? 'resume' : 'pause' })}
      aria-label={track.pausedAt ? rt('playAll') : rt('pauseAll')}
      title={track.pausedAt ? rt('playAll') : rt('pauseAll')}
    >
      {track.pausedAt ? <Play className="size-4.5" aria-hidden /> : <Pause className="size-4.5" aria-hidden />}
    </Button>
  ) : player && !track.pausedAt ? (
    <Button
      size="icon-sm"
      variant="ghost"
      onClick={localPaused ? player.play : player.pauseLocal}
      aria-label={localPaused ? rt('playHere') : rt('pauseHere')}
      title={localPaused ? rt('playHere') : rt('pauseHere')}
    >
      {localPaused ? <Play className="size-4.5" aria-hidden /> : <Pause className="size-4.5" aria-hidden />}
    </Button>
  ) : null
  const state = track.pausedAt ? t('voice.pausedByHost') : localPaused ? rt('pausedHere') : null

  if (!open)
    return (
      <div className="mx-3 flex h-14 items-center gap-1 rounded-2xl bg-card pe-1.5 ps-2 ring-1 ring-border sm:mx-4">
        <button type="button" onClick={() => onOpenChange(true)} className="flex min-w-0 flex-1 items-center gap-2.5 rounded-xl py-1 ps-1 text-start">
          <span className="relative grid h-10 w-14 shrink-0 place-items-center overflow-hidden rounded-lg bg-muted">
            <img src={thumbnail(track.videoId)} alt="" className="absolute inset-0 size-full object-cover" />
          </span>
          <span className="min-w-0 flex-1">
            <span className="block truncate text-sm font-semibold">{title}</span>
            <span className="flex items-center gap-1.5 truncate text-xs text-muted-foreground">
              {state ?? (
                <>
                  <LiveBars className="shrink-0" /> {rt('playing')}
                </>
              )}
            </span>
          </span>
        </button>
        {player?.blocked && !track.pausedAt && !localPaused ? (
          <Button size="sm" variant="brand" onClick={player.play}>
            <Headphones className="size-4" aria-hidden /> {t('voice.tapToListen')}
          </Button>
        ) : (
          playPause
        )}
        <Button size="icon-sm" variant="ghost" onClick={() => onOpenChange(true)} aria-label={rt('showPlayer')} aria-expanded={false}>
          <ChevronDown className="size-4.5" aria-hidden />
        </Button>
      </div>
    )

  return (
    <div className="px-4">
      <PlayerSlot track={track} />
      <div className="mt-2 flex items-center gap-1">
        <span className="min-w-0 flex-1 pe-1">
          <span className="flex items-center gap-1.5">
            <TopicIcon topic="music" size={16} />
            <span className="truncate text-sm font-semibold">{title}</span>
          </span>
          <span className="block truncate text-xs text-muted-foreground">
            {state ?? t('voice.addedBy', { name: track.addedBy })}
          </span>
        </span>
        <Button size="sm" variant="ghost" onClick={onQueue} className="px-2.5" aria-label={t('voice.upNextN', { n: upNext })}>
          <ListMusic className="size-4.5" aria-hidden />
          <span className="tabular-nums">{upNext}</span>
        </Button>
        <Button size="icon-sm" variant="ghost" onClick={() => onOpenChange(false)} aria-label={t('voice.collapse')} aria-expanded>
          <ChevronUp className="size-4.5" aria-hidden />
        </Button>
      </div>
      <PlayerControls track={track} act={act} small />
    </div>
  )
}

/**
 * Under the video: where the song is and what I can do with it. The host and co-hosts play, pause, skip and drag the
 * song's position for everyone; anyone else sees the position, pauses on their own screen (play again: back where
 * the room is now). Volume is everyone's own. Quality is YouTube's: its settings gear in the video.
 */
export function PlayerControls({ track, act, small, children }: { track: VoiceTrack; act: (a: VoiceAction) => Promise<boolean>; small?: boolean; children?: ReactNode }) {
  const { t } = useI18n()
  const { rt } = useRoomT()
  const player = useRoomPlayer()
  const can = !!player?.canControl
  const localPaused = !!player?.localPaused && !track.pausedAt
  const size = small ? 'icon-sm' : 'icon'
  const icon = small ? 'size-4.5' : 'size-5'
  return (
    <div className="mt-2">
      <SeekBar track={track} seconds={player?.seconds ?? 0} onSeek={can ? player?.seek : undefined} />
      <div className="mt-1 flex items-center gap-1.5">
        {can ? (
          <>
            <Button
              size={size}
              variant="secondary"
              onClick={() => void act({ type: track.pausedAt ? 'resume' : 'pause' })}
              aria-label={track.pausedAt ? rt('playAll') : rt('pauseAll')}
              title={track.pausedAt ? rt('playAll') : rt('pauseAll')}
            >
              {track.pausedAt ? <Play className={cn(icon, 'fill-current')} aria-hidden /> : <Pause className={cn(icon, 'fill-current')} aria-hidden />}
            </Button>
            <Button size={size} variant="brand" onClick={() => void act({ type: 'skip', trackId: track.id })} aria-label={rt('skipAll')} title={rt('skipAll')}>
              <SkipForward className={cn(icon, 'fill-current')} aria-hidden />
            </Button>
            <Button size={size} variant="ghost" onClick={() => void act({ type: 'stop' })} aria-label={rt('stopAll')} title={rt('stopAll')}>
              <Square className={cn(icon, 'fill-current')} aria-hidden />
            </Button>
          </>
        ) : player ? (
          <Button
            size={size}
            variant="secondary"
            disabled={!!track.pausedAt}
            onClick={localPaused ? player.play : player.pauseLocal}
            aria-label={localPaused ? rt('playHere') : rt('pauseHere')}
            title={track.pausedAt ? t('voice.pausedByHost') : localPaused ? rt('playHere') : rt('pauseHere')}
          >
            {localPaused || track.pausedAt ? <Play className={cn(icon, 'fill-current')} aria-hidden /> : <Pause className={cn(icon, 'fill-current')} aria-hidden />}
          </Button>
        ) : null}
        {player ? <VolumeControl /> : null}
        <span className="min-w-0 flex-1" />
        {children}
      </div>
      <p className="mt-1.5 flex items-center gap-1.5 text-[11px] leading-snug text-muted-foreground">
        <Settings className="size-3.5 shrink-0" aria-hidden />
        {can ? rt('djNote') : rt('listenerNote')} {rt('quality')}
      </p>
    </div>
  )
}

/** My own volume and mute (YouTube's own volume in the video works too; this one is kept for the next room). */
function VolumeControl() {
  const { rt } = useRoomT()
  const player = useRoomPlayer()!
  const level = player.muted ? 0 : player.volume
  const Icon = level === 0 ? VolumeX : level < 50 ? Volume1 : Volume2
  return (
    <span className="flex items-center gap-0.5">
      <Button
        size="icon-sm"
        variant="ghost"
        onClick={() => player.setMuted(!player.muted)}
        aria-label={player.muted ? rt('unmute') : rt('mute')}
        aria-pressed={player.muted}
        title={player.muted ? rt('unmute') : rt('mute')}
      >
        <Icon className="size-4.5" aria-hidden />
      </Button>
      <input
        type="range"
        min={0}
        max={100}
        step={1}
        value={level}
        onChange={(e) => player.setVolume(Number(e.target.value))}
        aria-label={rt('volume')}
        dir="ltr"
        className="room-range w-20 sm:w-24"
        style={{ '--p': `${level}%`, '--fill': 'var(--foreground)' } as CSSProperties}
      />
    </span>
  )
}

/**
 * Where the song is, as a bar and times (the length comes from the player once it has loaded). With `onSeek` (the
 * host and co-hosts) the bar is a slider: dragging moves the song for everyone when it's let go.
 */
function SeekBar({ track, seconds, onSeek }: { track: VoiceTrack; seconds: number; onSeek?: (seconds: number) => void }) {
  const { rt } = useRoomT()
  const [, tick] = useState(0)
  const [drag, setDrag] = useState<number | null>(null)
  useEffect(() => {
    if (track.pausedAt) return
    const id = setInterval(() => tick((x) => x + 1), 1000)
    return () => clearInterval(id)
  }, [track.pausedAt])
  // A new song (or the room moved meanwhile): a drag in progress for the old one is dropped.
  useEffect(() => setDrag(null), [track.id])
  const at = seconds ? Math.min(position(track), seconds) : position(track)
  const shown = drag ?? at
  const pct = seconds ? Math.min(100, (shown / seconds) * 100) : 0
  const commit = () => {
    if (drag === null || !onSeek) return
    onSeek(drag)
    setDrag(null)
  }
  return (
    <div style={{ '--p': `${pct}%` } as CSSProperties}>
      {onSeek && seconds ? (
        <input
          type="range"
          min={0}
          max={Math.floor(seconds)}
          step={1}
          value={Math.floor(shown)}
          onChange={(e) => setDrag(Number(e.target.value))}
          onPointerUp={commit}
          onKeyUp={commit}
          onBlur={commit}
          aria-label={rt('seekLabel')}
          aria-valuetext={`${mmss(shown)} / ${mmss(seconds)}`}
          dir="ltr"
          className="room-range block w-full"
        />
      ) : (
        <div className="py-[7.5px]" aria-hidden>
          <div className="h-[5px] overflow-hidden rounded-full bg-muted">
            <div className="h-full rounded-full bg-primary transition-[width] duration-1000 ease-linear" style={{ width: `${pct}%` }} />
          </div>
        </div>
      )}
      <div className="flex justify-between text-[11px] text-muted-foreground tabular-nums" dir="ltr">
        <span>{mmss(shown)}</span>
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
        item.voted ? 'bg-primary text-primary-foreground' : 'bg-card text-foreground ring-1 ring-border hover:bg-muted',
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
  canDJ,
  act,
}: {
  open: boolean
  onClose: () => void
  track: VoiceTrack | null
  queue: VoiceQueueItem[]
  /** The host or a co-host: what they add plays (or joins the queue); they play, remove and skip. */
  canDJ: boolean
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
    if (ok) toast.show(canDJ ? t('voice.added') : t('voice.suggested'), 'success')
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
    <Modal open={open} onClose={onClose} variant="sheet" title={t('voice.musicTitle')} description={canDJ ? t('voice.djDesc') : t('voice.suggestDesc')}>
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
            <Button type="submit" variant="brand" className="h-11" loading={!!pending} disabled={!link.trim() || !!pending}>
              {canDJ ? t('voice.add') : t('music.request')}
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
            {canDJ && ordered.length ? (
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
                    {canDJ ? (
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
