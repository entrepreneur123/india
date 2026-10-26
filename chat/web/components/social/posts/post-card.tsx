'use client'

import { useEffect, useRef, useState } from 'react'
import { Ellipsis, Flag, Trash2, UserRound } from 'lucide-react'
import type { FeedPost, Profile } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { flagEmoji } from '@/lib/format'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Modal } from '@/components/ui/modal'
import { PhotoViewer } from '@/components/ui/photo-viewer'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { AudienceIcon, BigHeart, CommentIcon, LikeIcon } from '@/components/ui/topic-icons'
import { ReportSheet } from '@/components/screens/chat/report-sheet'
import { cn } from '@/lib/utils'
import { useFeedT } from '../feed-text'
import { AccentAvatar, accentStyle } from '../profile-bits'
import { postStore, useLike, useLivePost } from './post-store'
import { VerifiedMark } from '@/components/trust/badge'

/** Text-only posts up to this long are set large on the author's colour (ShareChat-style). */
const TILE_MAX = 140
const LONG_TEXT = 260

/** A post's author as the report sheet takes a person. */
export const authorProfile = (a: FeedPost['author']): Profile => ({
  id: a.id, name: a.name, gender: a.gender, age: a.age, city: a.city ?? '', bio: '', photoUrl: a.photoUrl, country: a.country, posts: [],
})

/**
 * One post: the author (tap: their profile), the text, the photo (double tap: like), the heart and comment counts
 * and a "..." menu (report, or delete my own), at the top of the post sheet. `onComments`: goes to the comment box.
 */
export function PostCard({ post: initial, onComments }: { post: FeedPost; onComments: () => void }) {
  const post = useLivePost(initial)
  const { me, openProfile } = useApp()
  const { timeAgo, num } = useI18n()
  const { ft, plural } = useFeedT()
  const like = useLike()
  const [menuOpen, setMenuOpen] = useState(false)
  const [expanded, setExpanded] = useState(false)
  const [burst, setBurst] = useState(0)
  const [pop, setPop] = useState(0)
  const lastTap = useRef<{ at: number; x: number; y: number } | null>(null)
  // 39: one tap on the photo opens it large (zoom, save) — once it's clear it wasn't the first of a double tap (a like).
  const [large, setLarge] = useState<number | null>(null)
  const opening = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  useEffect(() => () => clearTimeout(opening.current), [])
  if (!post) return null
  const a = post.author
  const mine = a.id === me.id
  const tile = !post.imageUrl && post.text.length > 0 && post.text.length <= TILE_MAX
  const long = !tile && post.text.length > LONG_TEXT && !expanded

  const toggleLike = (on = !post.liked) => {
    if (on) setPop((n) => n + 1)
    void like(post, on)
  }
  // Instagram: two taps on the photo like it (never unlike), with a big heart.
  const onMediaTap = (e: React.PointerEvent, photo = false) => {
    if (e.pointerType === 'mouse' && e.button !== 0) return
    const now = performance.now()
    const prev = lastTap.current
    clearTimeout(opening.current)
    if (prev && now - prev.at < 320 && Math.hypot(e.clientX - prev.x, e.clientY - prev.y) < 40) {
      lastTap.current = null
      setBurst((n) => n + 1)
      if (!post.liked) toggleLike(true)
      else setPop((n) => n + 1)
      return
    }
    lastTap.current = { at: now, x: e.clientX, y: e.clientY }
    if (photo) opening.current = setTimeout(() => setLarge(0), 320)
  }

  return (
    <article
      style={accentStyle(a.accent)}
      aria-label={ft('post.by', { name: a.name })}
    >
      <header className="flex items-center gap-3 px-4 pt-4">
        <button type="button" onClick={() => openProfile(a.id)} className="flex min-w-0 flex-1 items-center gap-3 rounded-2xl text-start">
          <AccentAvatar userId={a.id} name={a.name} src={a.photoUrl} size={40} accent={a.accent} />
          <span className="min-w-0">
            <span className="flex items-center gap-1.5 text-[15px] leading-tight font-bold">
              <span className="truncate">{a.name}</span>
              {a.verified ? <VerifiedMark size={16} /> : null}
              {a.age > 0 ? <span className="shrink-0 font-semibold text-muted-foreground">{a.age}</span> : null}
              <span className="shrink-0" aria-hidden>
                {flagEmoji(a.country)}
              </span>
            </span>
            <span className="mt-0.5 flex items-center gap-1.5 text-xs text-muted-foreground">
              <time dateTime={new Date(post.createdAt).toISOString()}>{timeAgo(post.createdAt)}</time>
              <span aria-hidden>·</span>
              <AudienceIcon visibility={post.visibility} size={13} />
              <span>{post.visibility === 'public' ? ft('post.public') : ft('post.friendsOnly')}</span>
            </span>
          </span>
        </button>
        <button
          type="button"
          onClick={() => setMenuOpen(true)}
          className="-me-1 grid size-10 shrink-0 place-items-center rounded-full text-muted-foreground transition-colors duration-150 hover:bg-muted hover:text-foreground"
          aria-label={ft('post.menu')}
        >
          <Ellipsis className="size-5" aria-hidden />
        </button>
      </header>

      {tile ? (
        <div
          className="relative mx-3 mt-3 grid min-h-48 touch-manipulation place-items-center overflow-hidden rounded-3xl px-7 py-10 text-center select-none"
          style={{ background: 'var(--acc-to)' }}
          onPointerUp={onMediaTap}
        >
          <p className="relative text-[22px] leading-snug font-bold tracking-tight text-balance whitespace-pre-wrap text-white">
            {post.text}
          </p>
          <Burst n={burst} />
        </div>
      ) : post.text ? (
        <div className="px-4 pt-3">
          <p className={cn('text-[15px] leading-relaxed whitespace-pre-wrap text-pretty', long && 'line-clamp-5')}>{post.text}</p>
          {long ? (
            <button type="button" onClick={() => setExpanded(true)} className="mt-0.5 text-sm font-semibold text-muted-foreground hover:text-foreground">
              {ft('post.readMore')}
            </button>
          ) : null}
        </div>
      ) : null}

      {post.imageUrl ? (
        <div className="relative mt-3 cursor-zoom-in touch-manipulation overflow-hidden bg-muted select-none sm:mx-3 sm:rounded-3xl" onPointerUp={(e) => onMediaTap(e, true)}>
          <img
            src={post.imageUrl}
            alt={ft('post.photo', { name: a.name })}
            className="max-h-[min(72dvh,640px)] w-full object-cover"
            loading="lazy"
            decoding="async"
            draggable={false}
          />
          <Burst n={burst} />
          <PhotoViewer photos={[post.imageUrl]} index={large} onIndex={setLarge} label={a.name} />
        </div>
      ) : null}

      <div className="flex items-center gap-1 px-2 pt-1.5 pb-1">
        <button
          type="button"
          onClick={() => toggleLike()}
          aria-pressed={post.liked}
          aria-label={`${ft('post.like')}, ${plural(post.likes, 'post.likes.one', 'post.likes.other')}`}
          className={cn(
            'flex h-11 items-center gap-1.5 rounded-full px-2.5 text-sm font-bold tabular-nums transition-colors duration-150 hover:bg-muted',
            post.liked ? 'text-[#d61f4f]' : 'text-foreground',
          )}
        >
          <span key={pop} className={cn('grid', pop > 0 && 'animate-heart-pop')}>
            <LikeIcon on={post.liked} size={26} />
          </span>
          {post.likes > 0 ? <span aria-hidden>{num(post.likes)}</span> : null}
        </button>
        <button
          type="button"
          onClick={onComments}
          aria-label={`${ft('post.comment')}, ${plural(post.comments, 'post.comments.one', 'post.comments.other')}`}
          className="flex h-11 items-center gap-1.5 rounded-full px-2.5 text-sm font-bold text-foreground tabular-nums transition-colors duration-150 hover:bg-muted"
        >
          <CommentIcon size={25} />
          {post.comments > 0 ? <span aria-hidden>{num(post.comments)}</span> : null}
        </button>
      </div>
      <span className="block h-2" aria-hidden />

      <PostMenu post={post} mine={mine} open={menuOpen} onClose={() => setMenuOpen(false)} />
    </article>
  )
}

/** The big heart over the photo, once per double tap. */
function Burst({ n }: { n: number }) {
  if (!n) return null
  return (
    <span key={n} className="pointer-events-none absolute inset-0 grid place-items-center" aria-hidden>
      <BigHeart size={104} className="animate-heart-burst opacity-0" />
    </span>
  )
}

/** "...": report someone else's post (or see who wrote it), delete my own. */
function PostMenu({ post, mine, open, onClose }: { post: FeedPost; mine: boolean; open: boolean; onClose: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { me, setMe, openProfile } = useApp()
  const { t } = useI18n()
  const { ft } = useFeedT()
  const [confirm, setConfirm] = useState(false)
  const [busy, setBusy] = useState(false)
  const [reportOpen, setReportOpen] = useState(false)
  const close = () => {
    setConfirm(false)
    onClose()
  }
  const remove = async () => {
    setBusy(true)
    try {
      await backend.deletePost(post.id)
      postStore.remove(post.id)
      setMe({ ...me, posts: me.posts.filter((p) => p.id !== post.id) })
      toast.show(ft('post.deleted'), 'success')
      close()
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }
  const row = 'flex w-full items-center gap-3 rounded-2xl px-4 py-3.5 text-start text-[15px] font-semibold transition-colors duration-150 hover:bg-muted'
  return (
    <>
      <Modal open={open} onClose={close} variant="sheet" hideHeader className="md:max-w-sm">
        <div className="p-3 pb-5">
          {confirm ? (
            <div className="flex flex-col gap-3 px-3 pt-3" role="group" aria-label={ft('post.deleteTitle')}>
              <p className="text-lg font-bold tracking-tight">{ft('post.deleteTitle')}</p>
              <p className="text-sm leading-relaxed text-muted-foreground">{ft('post.deleteBody')}</p>
              <div className="mt-2 flex justify-end gap-2">
                <Button variant="ghost" disabled={busy} onClick={() => setConfirm(false)}>
                  {t('common.cancel')}
                </Button>
                <Button variant="danger" loading={busy} onClick={() => void remove()} autoFocus>
                  {ft('post.delete')}
                </Button>
              </div>
            </div>
          ) : mine ? (
            <button type="button" className={cn(row, 'text-destructive')} onClick={() => setConfirm(true)}>
              <Trash2 className="size-5" aria-hidden /> {ft('post.delete')}
            </button>
          ) : (
            <>
              <button
                type="button"
                className={row}
                onClick={() => {
                  close()
                  openProfile(post.author.id)
                }}
              >
                <UserRound className="size-5 text-muted-foreground" aria-hidden /> {ft('post.viewProfile')}
              </button>
              <button
                type="button"
                className={cn(row, 'text-destructive')}
                onClick={() => {
                  close()
                  setReportOpen(true)
                }}
              >
                <Flag className="size-5" aria-hidden /> {ft('post.report')}
              </button>
            </>
          )}
        </div>
      </Modal>
      {mine ? null : (
        <ReportSheet
          open={reportOpen}
          onClose={() => setReportOpen(false)}
          partner={authorProfile(post.author)}
          matchId={null}
          about={{ post: post.id, title: ft('post.reportTitle'), thanks: ft('post.reported') }}
          onReported={() => postStore.remove(post.id)}
        />
      )}
    </>
  )
}
