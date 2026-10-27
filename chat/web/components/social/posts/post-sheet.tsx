'use client'

import { useEffect, useRef, useState } from 'react'
import useSWR from 'swr'
import { ArrowUp, X } from 'lucide-react'
import type { FeedPost, PersonRow, PostComment } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { COMMENT_MAX, COMMENTS_PAGE, threadComments } from '@/lib/feed'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { EmptyState } from '@/components/ui/ambient'
import { Skeleton } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { ReportSheet } from '@/components/screens/chat/report-sheet'
import { cn } from '@/lib/utils'
import { useFeedT } from '../feed-text'
import { AccentAvatar } from '../profile-bits'
import { authorProfile, PostCard } from './post-card'
import { VerifiedMark } from '@/components/trust/badge'
import { postStore, useLivePost } from './post-store'
import { UserAvatar } from '@/components/looks/user-avatar'
import { ScrollRow } from '@/components/ui/scroll-row'

export interface PostTarget {
  postId: string
  /** What the screen already has (shown at once while the post is read again). */
  initial?: FeedPost | null
  /** Put the cursor in the comment box. */
  focus?: boolean
  /** Scroll down to the likers and comments (opened from a count in the feed). */
  reactions?: boolean
}

/** A post with its comments: from a profile's grid or a like / comment notification. */
export function PostSheet({ target, onClose }: { target: PostTarget | null; onClose: () => void }) {
  return (
    <Modal open={target !== null} onClose={onClose} variant="sheet" hideHeader className="md:max-w-xl">
      {target ? <PostBody key={target.postId} target={target} onClose={onClose} /> : null}
    </Modal>
  )
}

function PostBody({ target, onClose }: { target: PostTarget; onClose: () => void }) {
  const backend = useBackend()
  const { t } = useI18n()
  const { ft } = useFeedT()
  const { data, error, isLoading, mutate } = useSWR(['post', target.postId], async () => {
    const p = await backend.post(target.postId)
    if (p) postStore.put([p])
    return p
  })
  const post = useLivePost(data ?? (data === null ? null : target.initial ?? undefined))
  const inputRef = useRef<HTMLTextAreaElement>(null)

  return (
    <div className="flex min-h-0 flex-col">
      <div className="flex items-center justify-between gap-3 px-5 pt-3 pb-1">
        <h2 className="text-lg font-bold tracking-tight">{ft('comments.title')}</h2>
        <button
          type="button"
          onClick={onClose}
          className="-me-2 grid size-9 place-items-center rounded-full text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
          aria-label={t('common.close')}
        >
          <X className="size-5" aria-hidden />
        </button>
      </div>
      {post ? (
        <>
          <PostCard post={post} onComments={() => inputRef.current?.focus()} />
          <Likers post={post} scrollTo={!!target.reactions} />
          <div className="border-t border-border" />
          <Comments post={post} inputRef={inputRef} autoFocus={!!target.focus} />
        </>
      ) : post === null || (data === null && !isLoading) ? (
        <EmptyState art="posts" title={ft('post.gone')} body={ft('post.goneBody')} />
      ) : error ? (
        <EmptyState
          art="posts"
          title={ft('feed.loadFailed')}
          body={t('common.checkConnection')}
          action={<Button variant="outline" onClick={() => void mutate()}>{t('common.tryAgain')}</Button>}
        />
      ) : (
        <div className="flex flex-col gap-3 p-5" aria-busy>
          <div className="flex items-center gap-3">
            <Skeleton className="size-11 rounded-full" />
            <Skeleton className="h-4 w-40" />
          </div>
          <Skeleton className="h-56 w-full rounded-3xl" />
        </div>
      )}
    </div>
  )
}

/** 080: who liked it, newest first, above the comments in the same sheet; read again when the count changes. */
function Likers({ post, scrollTo }: { post: FeedPost; scrollTo: boolean }) {
  const backend = useBackend()
  const { data } = useSWR(post.likes > 0 ? ['likers', post.id, post.likes] : null, () => backend.postLikers(post.id), { keepPreviousData: true })
  // Opened from a count: the likers (and the comments under them) come into view, the post scrolled up past.
  const ref = useRef<HTMLDivElement>(null)
  const scrolled = useRef(false)
  useEffect(() => {
    if (!scrollTo || scrolled.current || (post.likes > 0 && !data)) return
    scrolled.current = true
    ref.current?.scrollIntoView({ block: 'start' })
  }, [scrollTo, data, post.likes])
  return (
    <div ref={ref} className="scroll-mt-2">
      {post.likes > 0 ? <LikerList post={post} people={data} /> : null}
    </div>
  )
}

function LikerList({ post, people: data }: { post: FeedPost; people: PersonRow[] | undefined }) {
  const { openProfile } = useApp()
  const { ft, plural } = useFeedT()
  return (
    <section aria-label={ft('post.likers')} className="flex flex-col gap-2 px-4 pb-3">
      <h3 className="px-1 text-sm font-semibold">{plural(post.likes, 'post.likes.one', 'post.likes.other')}</h3>
      <ScrollRow as="ul" className="flex gap-2 overflow-x-auto no-scrollbar">
        {data
          ? data.map((p) => (
              <li key={p.id} className="shrink-0">
                <button type="button" onClick={() => openProfile(p.id)} className="flex w-14 flex-col items-center gap-1 rounded-xl p-0.5 text-center">
                  <UserAvatar userId={p.id} name={p.name} src={p.photoUrl} size={44} />
                  <span className="w-full truncate text-[11px] font-medium">{p.name}</span>
                </button>
              </li>
            ))
          : Array.from({ length: Math.min(post.likes, 5) }, (_, i) => <Skeleton key={i} className="size-11 shrink-0 rounded-full" />)}
      </ScrollRow>
    </section>
  )
}

function Comments({ post, inputRef, autoFocus }: { post: FeedPost; inputRef: React.RefObject<HTMLTextAreaElement | null>; autoFocus: boolean }) {
  const backend = useBackend()
  const toast = useToast()
  const { me } = useApp()
  const { t } = useI18n()
  const { ft } = useFeedT()
  const [list, setList] = useState<PostComment[] | null>(null)
  const [more, setMore] = useState(false)
  const [failed, setFailed] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [text, setText] = useState('')
  const [replyTo, setReplyTo] = useState<{ id: number; name: string } | null>(null)
  const [sending, setSending] = useState(false)
  const [report, setReport] = useState<PostComment | null>(null)
  const endRef = useRef<HTMLDivElement>(null)

  const load = async (after?: number) => {
    setFailed(false)
    try {
      const page = await backend.comments(post.id, after)
      setList((cur) => {
        const base = after === undefined ? [] : cur ?? []
        const ids = new Set(base.map((c) => c.id))
        return [...base, ...page.filter((c) => !ids.has(c.id))]
      })
      setMore(page.length >= COMMENTS_PAGE)
    } catch {
      setFailed(true)
    }
  }
  useEffect(() => {
    void load()
    if (autoFocus) requestAnimationFrame(() => inputRef.current?.focus())
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [post.id])

  const send = async (e?: React.FormEvent) => {
    e?.preventDefault()
    const body = text.trim()
    if (!body || sending) return
    setSending(true)
    try {
      const c = await backend.addComment(post.id, body, replyTo?.id ?? null)
      setList((cur) => [...(cur ?? []), c])
      postStore.patch(post.id, (p) => ({ ...p, comments: p.comments + 1 }))
      setText('')
      setReplyTo(null)
      requestAnimationFrame(() => endRef.current?.scrollIntoView({ block: 'nearest', behavior: 'smooth' }))
    } catch (err) {
      toast.error(err)
    } finally {
      setSending(false)
    }
  }

  const remove = async (c: PostComment) => {
    try {
      await backend.removeComment(c.id)
      setList((cur) => cur?.filter((x) => x.id !== c.id) ?? cur)
      postStore.patch(post.id, (p) => ({ ...p, comments: Math.max(0, p.comments - 1) }))
      toast.show(ft('comments.removed'), 'success')
    } catch (err) {
      toast.error(err)
    }
  }

  const reply = (c: PostComment) => {
    setReplyTo({ id: c.id, name: c.author.name })
    requestAnimationFrame(() => inputRef.current?.focus())
  }

  return (
    <>
      <section aria-label={ft('comments.title')} className="flex flex-col px-3 pt-2 pb-2">
        {list === null ? (
          failed ? (
            <div className="flex flex-col items-center gap-3 py-8 text-center">
              <p className="text-sm font-semibold">{ft('comments.loadFailed')}</p>
              <Button variant="outline" size="sm" onClick={() => void load()}>
                {t('common.tryAgain')}
              </Button>
            </div>
          ) : (
            <ul className="flex flex-col gap-4 px-2 py-3" aria-busy>
              {[0, 1].map((i) => (
                <li key={i} className="flex gap-3">
                  <Skeleton className="size-9 rounded-full" />
                  <div className="flex flex-1 flex-col gap-2">
                    <Skeleton className="h-3.5 w-28" />
                    <Skeleton className="h-3.5 w-48" />
                  </div>
                </li>
              ))}
            </ul>
          )
        ) : list.length === 0 ? (
          <EmptyState art="comments" title={ft('comments.none')} body={ft('comments.noneBody')} className="py-6" />
        ) : (
          <ul className="flex flex-col">
            {threadComments(list).map(({ comment, depth }) => (
              <CommentRow
                key={comment.id}
                comment={comment}
                depth={depth}
                onReply={() => reply(comment)}
                onRemove={() => void remove(comment)}
                onReport={() => setReport(comment)}
              />
            ))}
          </ul>
        )}
        {more ? (
          <Button
            variant="ghost"
            size="sm"
            className="mx-auto mt-1"
            loading={loadingMore}
            onClick={async () => {
              setLoadingMore(true)
              await load(list?.[list.length - 1]?.id)
              setLoadingMore(false)
            }}
          >
            {ft('comments.more')}
          </Button>
        ) : null}
        <div ref={endRef} />
      </section>

      <form
        onSubmit={(e) => void send(e)}
        className="sticky bottom-0 z-10 border-t border-border bg-card/95 px-3 pt-2 pb-3 backdrop-blur supports-[backdrop-filter]:bg-card/85"
      >
        {replyTo ? (
          <div className="mb-2 flex items-center justify-between gap-2 rounded-full bg-muted py-1 pe-1 ps-4 text-xs font-semibold text-muted-foreground">
            <span className="truncate">{ft('comments.replyingTo', { name: replyTo.name })}</span>
            <button
              type="button"
              onClick={() => setReplyTo(null)}
              className="grid size-7 shrink-0 place-items-center rounded-full hover:bg-input/60 hover:text-foreground"
              aria-label={ft('comments.cancelReply')}
            >
              <X className="size-4" aria-hidden />
            </button>
          </div>
        ) : null}
        <div className="flex items-end gap-2">
          <AccentAvatar name={me.name} src={me.photoUrl} size={34} accent={me.style?.accent} className="mb-0.5" />
          <label className="flex min-h-11 min-w-0 flex-1 items-end rounded-3xl border border-input bg-card py-1 pe-1 ps-4 transition-[border-color,box-shadow] focus-within:border-primary focus-within:ring-1 focus-within:ring-primary">
            <span className="sr-only">{ft('comments.label')}</span>
            <textarea
              ref={inputRef}
              value={text}
              rows={1}
              maxLength={COMMENT_MAX}
              placeholder={replyTo ? ft('comments.placeholderReply', { name: replyTo.name }) : ft('comments.placeholder')}
              onChange={(e) => setText(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
                  e.preventDefault()
                  void send()
                }
              }}
              className="max-h-28 min-h-9 flex-1 resize-none bg-transparent py-1.5 text-[15px] leading-snug outline-none [field-sizing:content] placeholder:text-muted-foreground"
            />
            <button
              type="submit"
              disabled={!text.trim() || sending}
              className="grid size-9 shrink-0 place-items-center rounded-full bg-primary text-primary-foreground transition-opacity duration-150 disabled:opacity-40"
              aria-label={ft('comments.send')}
            >
              <ArrowUp className="size-5" strokeWidth={2.4} aria-hidden />
            </button>
          </label>
        </div>
        {text.length > COMMENT_MAX - 40 ? (
          <p className={cn('mt-1 text-end text-xs tabular-nums', text.length >= COMMENT_MAX ? 'text-destructive' : 'text-muted-foreground')}>
            {text.length}/{COMMENT_MAX}
          </p>
        ) : null}
      </form>

      {report ? (
        <ReportSheet
          open
          onClose={() => setReport(null)}
          partner={authorProfile(report.author)}
          matchId={null}
          about={{ comment: report.id, title: ft('comments.reportTitle'), thanks: ft('comments.reported') }}
          onReported={() => setList((cur) => cur?.filter((x) => x.id !== report.id) ?? cur)}
        />
      ) : null}
    </>
  )
}

function CommentRow({
  comment: c,
  depth,
  onReply,
  onRemove,
  onReport,
}: {
  comment: PostComment
  depth: 0 | 1
  onReply: () => void
  onRemove: () => void
  onReport: () => void
}) {
  const { openProfile } = useApp()
  const { timeAgo } = useI18n()
  const { ft } = useFeedT()
  const link = 'rounded-md px-1 py-0.5 text-xs font-bold text-muted-foreground transition-colors hover:text-foreground'
  return (
    <li className={cn('flex gap-3 rounded-2xl px-2 py-2.5', depth === 1 && 'ms-11')}>
      <button type="button" onClick={() => openProfile(c.author.id)} className="shrink-0 self-start rounded-full" aria-label={c.author.name}>
        <AccentAvatar userId={c.author.id} name={c.author.name} src={c.author.photoUrl} size={depth ? 28 : 34} accent={c.author.accent} />
      </button>
      <div className="min-w-0 flex-1">
        <div className="rounded-2xl rounded-ss-md bg-muted px-3.5 py-2">
          <p className="flex items-baseline gap-2 text-[13px]">
            <button type="button" onClick={() => openProfile(c.author.id)} className="inline-flex min-w-0 items-center gap-1 font-bold hover:underline">
              <span className="truncate">{c.author.name}</span>
              {c.author.verified ? <VerifiedMark size={13} /> : null}
            </button>
            <time className="shrink-0 text-xs text-muted-foreground" dateTime={new Date(c.at).toISOString()}>
              {timeAgo(c.at)}
            </time>
          </p>
          {c.replyName ? <p className="mt-0.5 text-xs font-semibold text-muted-foreground">{ft('comments.replyingTo', { name: c.replyName })}</p> : null}
          <p className="mt-0.5 text-[15px] leading-snug break-words whitespace-pre-wrap">{c.text}</p>
        </div>
        <div className="mt-1 flex gap-2 ps-2">
          <button type="button" className={link} onClick={onReply}>
            {ft('comments.reply')}
          </button>
          {c.canRemove ? (
            <button type="button" className={link} onClick={onRemove}>
              {ft('comments.remove')}
            </button>
          ) : null}
          {c.mine ? null : (
            <button type="button" className={link} onClick={onReport}>
              {ft('comments.report')}
            </button>
          )}
        </div>
      </div>
    </li>
  )
}
