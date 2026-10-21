'use client'

import { useEffect, useId, useState, type CSSProperties } from 'react'
import { ChevronDown, ChevronUp, Headphones, ListMusic, LoaderCircle, Pause, Play, Plus, SkipForward, Trash2 } from 'lucide-react'
import type { VoiceAction, VoiceQueueItem, VoiceTrack } from '@/lib/backend'
import { thumbnail, videoTitle, youTubeId } from '@/lib/youtube'
import { useI18n } from '@/components/i18n'
import { TextInput } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { TopicIcon } from '@/components/ui/topic-icons'
import { useToast } from '@/components/ui/toast'
import { kindColor } from '@/components/screens/rooms/room-text'
import { cn } from '@/lib/utils'
import { PlayerSlot, position, useRoomPlayer } from './room-player'
import { useRoomT } from './room-t'
import { LiveBars } from './voice-stage'

/** Requests in the order they'll play: most votes first, then the oldest. */
export const byVotes = (queue: VoiceQueueItem[]) =>
  queue.map((q, i) => [q, i] as const).sort((a, b) => b[0].votes - a[0].votes || a[1] - b[1]).map(([q]) => q)

/** "Song – Film · Artist" titles carry the artist after the last " · ": shown as the song, then its artist. */
const songOf = (title: string) => (title.includes(' · ') ? title.slice(0, title.lastIndexOf(' · ')) : title)
const artistOf = (title: string) => (title.includes(' · ') ? title.slice(title.lastIndexOf(' · ') + 3) : '')
const mmss = (s: number) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`

/**
 * "Listen together" outside a music room: the video with its controls, or (hidden) a compact now-playing strip.
 * Hiding only shrinks it: the room's player (room-player.tsx) plays on either way.
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
  const { rt } = useRoomT()
  const player = useRoomPlayer()
  const title = track.title || player?.title || t('voice.ytVideo')
  const upNext = queue.length
  const playPause = isHost ? (
    <Button
      size="icon-sm"
      variant="ghost"
      onClick={() => void act({ type: track.pausedAt ? 'resume' : 'pause' })}
      aria-label={track.pausedAt ? t('voice.resume') : t('voice.pause')}
    >
      {track.pausedAt ? <Play className="size-4.5" aria-hidden /> : <Pause className="size-4.5" aria-hidden />}
    </Button>
  ) : null

  if (!open)
    return (
      <div className="mx-3 flex h-14 items-center gap-1 rounded-2xl bg-[color-mix(in_srgb,var(--topic-music)_12%,var(--card))] pr-1.5 pl-2 ring-1 ring-[color-mix(in_srgb,var(--topic-music)_26%,transparent)] sm:mx-4">
        <button type="button" onClick={() => onOpenChange(true)} className="flex min-w-0 flex-1 items-center gap-2.5 rounded-xl py-1 pl-1 text-left">
          <span className="relative grid h-10 w-14 shrink-0 place-items-center overflow-hidden rounded-lg bg-muted">
            <img src={thumbnail(track.videoId)} alt="" className="absolute inset-0 size-full object-cover" />
          </span>
          <span className="min-w-0 flex-1">
            <span className="block truncate text-sm font-semibold">{title}</span>
            <span className="flex items-center gap-1.5 truncate text-xs text-muted-foreground">
              {track.pausedAt ? t('voice.pausedByHost') : (
                <>
                  <LiveBars className="shrink-0" /> {rt('playing')}
                </>
              )}
            </span>
          </span>
        </button>
        {player?.blocked && !track.pausedAt ? (
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
        <span className="min-w-0 flex-1 pr-1">
          <span className="flex items-center gap-1.5">
            <TopicIcon topic="music" size={16} />
            <span className="truncate text-sm font-semibold">{title}</span>
          </span>
          <span className="block truncate text-xs text-muted-foreground">
            {track.pausedAt ? t('voice.pausedByHost') : t('voice.addedBy', { name: track.addedBy })}
          </span>
        </span>
        {playPause}
        {isHost ? (
          <Button size="icon-sm" variant="ghost" onClick={() => void act({ type: 'skip', trackId: track.id })} aria-label={t('voice.skip')}>
            <SkipForward className="size-4.5" aria-hidden />
          </Button>
        ) : null}
        <Button size="sm" variant="ghost" onClick={onQueue} className="px-2.5" aria-label={t('voice.upNextN', { n: upNext })}>
          <ListMusic className="size-4.5" aria-hidden />
          <span className="tabular-nums">{upNext}</span>
        </Button>
        <Button size="icon-sm" variant="ghost" onClick={() => onOpenChange(false)} aria-label={t('voice.collapse')} aria-expanded>
          <ChevronUp className="size-4.5" aria-hidden />
        </Button>
      </div>
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
  const player = useRoomPlayer()
  const ordered = byVotes(queue)
  const title = track ? songOf(track.title) || player?.title || t('voice.ytVideo') : ''
  const artist = track ? artistOf(track.title) || player?.author || '' : ''

  return (
    <section aria-label={t('voice.musicTitle')} className="flex flex-col gap-3 px-4" style={{ '--k': kindColor('music') } as CSSProperties}>
      {track ? (
        <div>
          <PlayerSlot track={track} className="shadow-[0_18px_40px_-18px_color-mix(in_srgb,var(--k)_70%,transparent)]" />
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
          <Progress track={track} seconds={player?.seconds ?? 0} />
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
