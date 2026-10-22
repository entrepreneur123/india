'use client'

import { lazy, Suspense, useCallback, useEffect, useRef, useState } from 'react'
import { ImagePlus, Plus, RefreshCw } from 'lucide-react'
import type { FeedPost, FeedTab } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { appendPage, FEED_PAGE, refreshTop } from '@/lib/feed'
import { retryImport } from '@/lib/retry-import'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { EmptyState } from '@/components/ui/ambient'
import { Segmented, Skeleton } from '@/components/ui/bits'
import { Button } from '@/components/ui/pressable'
import { BellButton } from '@/components/social/bits'
import { useFeedT } from '@/components/social/feed-text'
import { AccentAvatar } from '@/components/social/profile-bits'
import { PostCard } from '@/components/social/posts/post-card'
import { postStore, usePostStoreVersion } from '@/components/social/posts/post-store'
import { cn } from '@/lib/utils'

const loadComposer = () => retryImport(() => import('@/components/social/posts/post-composer'))
const PostComposer = lazy(() => loadComposer().then((m) => ({ default: m.PostComposer })))

interface TabState {
  posts: FeedPost[]
  /** No older posts. */
  done: boolean
  loading: boolean
  failed: boolean
}
const EMPTY: TabState = { posts: [], done: false, loading: false, failed: false }

// What was shown, for coming back to the tab in this visit (re-read from the top in the background).
const kept: Partial<Record<FeedTab, TabState>> = {}
let lastTab: FeedTab = 'friends'

/** Posts from friends (and mine), or public posts from everyone (Discover); newest first, more as I scroll. */
export function FeedScreen() {
  const backend = useBackend()
  const { me } = useApp()
  const { t } = useI18n()
  const { ft } = useFeedT()
  const [tab, setTabState] = useState<FeedTab>(lastTab)
  const [state, setState] = useState<Record<FeedTab, TabState>>(() => ({ friends: kept.friends ?? EMPTY, discover: kept.discover ?? EMPTY }))
  const [composer, setComposer] = useState(false)
  const [composerUsed, setComposerUsed] = useState(false)
  const scrollRef = useRef<HTMLDivElement>(null)
  const sentinel = useRef<HTMLDivElement>(null)
  const stateRef = useRef(state)
  stateRef.current = state
  usePostStoreVersion() // deleted posts drop out

  const update = useCallback((which: FeedTab, fn: (s: TabState) => TabState) => {
    setState((all) => {
      const next = { ...all, [which]: fn(all[which]) }
      kept[which] = next[which]
      return next
    })
  }, [])

  /** The first page (again); older posts already shown stay below it. */
  const loadTop = useCallback(
    async (which: FeedTab) => {
      update(which, (s) => ({ ...s, loading: true, failed: false }))
      try {
        const page = await backend.feed(which)
        postStore.put(page)
        update(which, (s) => ({ posts: refreshTop(s.posts, page), done: page.length < FEED_PAGE || (s.done && s.posts.length > 0), loading: false, failed: false }))
      } catch {
        update(which, (s) => ({ ...s, loading: false, failed: true }))
      }
    },
    [backend, update],
  )

  const loadMore = useCallback(
    async (which: FeedTab) => {
      const s = stateRef.current[which]
      if (s.loading || s.done || !s.posts.length) return
      update(which, (x) => ({ ...x, loading: true, failed: false }))
      try {
        const page = await backend.feed(which, s.posts[s.posts.length - 1].cursor)
        postStore.put(page)
        update(which, (x) => ({ posts: appendPage(x.posts, page), done: page.length < FEED_PAGE, loading: false, failed: false }))
      } catch {
        update(which, (x) => ({ ...x, loading: false, failed: true }))
      }
    },
    [backend, update],
  )

  useEffect(() => {
    void loadTop(tab)
  }, [tab, loadTop])

  // My new post goes on top (in Discover too when it's for everyone).
  useEffect(
    () =>
      postStore.onPosted((p) => {
        update('friends', (s) => ({ ...s, posts: [p, ...s.posts.filter((x) => x.id !== p.id)] }))
        if (p.visibility === 'public') update('discover', (s) => ({ ...s, posts: [p, ...s.posts.filter((x) => x.id !== p.id)] }))
      }),
    [update],
  )

  // More as the end comes near.
  useEffect(() => {
    const el = sentinel.current
    if (!el) return
    const io = new IntersectionObserver((entries) => entries.some((e) => e.isIntersecting) && void loadMore(tab), {
      root: scrollRef.current,
      rootMargin: '600px 0px',
    })
    io.observe(el)
    return () => io.disconnect()
  }, [tab, loadMore, state[tab].posts.length])

  const setTab = (next: FeedTab) => {
    lastTab = next
    setTabState(next)
    scrollRef.current?.scrollTo({ top: 0 })
  }
  const openComposer = () => {
    setComposerUsed(true)
    setComposer(true)
  }

  const cur = state[tab]
  const posts = cur.posts.filter((p) => !postStore.isGone(p.id))
  const firstLoad = !posts.length && (cur.loading || (!cur.failed && !cur.done))

  return (
    <div ref={scrollRef} className="min-h-0 flex-1 overflow-y-auto scrollbar-thin">
      <div className="mx-auto flex max-w-xl flex-col px-3 pb-12 sm:px-6">
        <header className="flex items-center justify-between gap-3 px-1 pt-5 sm:pt-10">
          <div className="min-w-0">
            <h1 className="text-[28px] leading-tight font-bold tracking-tight">{ft('feed.title')}</h1>
            <p className="mt-0.5 hidden text-sm text-muted-foreground sm:block">{ft('feed.subtitle')}</p>
          </div>
          <div className="flex shrink-0 items-center gap-1.5">
            <BellButton className="md:hidden" />
            <Button variant="brand" size="icon" className="sm:hidden" onClick={openComposer} onPointerDown={() => void loadComposer()} aria-label={ft('feed.new')}>
              <Plus className="size-5" strokeWidth={2.4} aria-hidden />
            </Button>
            <Button variant="brand" className="hidden sm:inline-flex" onClick={openComposer} onPointerEnter={() => void loadComposer()}>
              <Plus className="size-4.5" aria-hidden /> {ft('feed.new')}
            </Button>
          </div>
        </header>

        <div className="sticky top-0 z-10 -mx-3 mt-3 bg-background/92 px-3 py-2.5 backdrop-blur sm:-mx-6 sm:px-6">
          <Segmented<FeedTab>
            label={ft('feed.tabs')}
            value={tab}
            onChange={setTab}
            size="lg"
            className="w-full"
            options={[
              { value: 'friends', label: ft('feed.friends') },
              { value: 'discover', label: ft('feed.discover') },
            ]}
          />
        </div>

        {/* The way in to a new post, Facebook-style. */}
        <button
          type="button"
          onClick={openComposer}
          onPointerEnter={() => void loadComposer()}
          className="mt-2 flex items-center gap-3 rounded-[28px] bg-card p-3 pe-4 text-start shadow-e1 ring-1 ring-border/70 transition-shadow duration-150 hover:shadow-e2"
        >
          <AccentAvatar name={me.name} src={me.photoUrl} size={40} accent={me.style?.accent} />
          <span className="min-w-0 flex-1 truncate rounded-full bg-muted px-4 py-2.5 text-[15px] text-muted-foreground">
            {ft('feed.prompt', { name: me.name })}
          </span>
          <ImagePlus className="size-6 shrink-0 text-primary-text" aria-hidden />
        </button>

        <div className="mt-4 flex flex-col gap-4" aria-busy={cur.loading}>
          {firstLoad ? (
            [0, 1].map((i) => (
              <div key={i} className="rounded-[28px] bg-card p-4 shadow-e1 ring-1 ring-border/70">
                <div className="flex items-center gap-3">
                  <Skeleton className="size-11 rounded-full" />
                  <div className="flex flex-col gap-2">
                    <Skeleton className="h-3.5 w-32" />
                    <Skeleton className="h-3 w-20" />
                  </div>
                </div>
                <Skeleton className="mt-4 h-64 w-full rounded-3xl" />
              </div>
            ))
          ) : !posts.length && cur.failed ? (
            <EmptyState
              art="feed"
              title={ft('feed.loadFailed')}
              body={t('common.checkConnection')}
              action={<Button variant="outline" onClick={() => void loadTop(tab)}>{t('common.tryAgain')}</Button>}
            />
          ) : !posts.length ? (
            tab === 'friends' ? (
              <EmptyState
                art="feed"
                title={ft('feed.emptyFriends')}
                body={ft('feed.emptyFriendsBody')}
                action={
                  <div className="flex flex-wrap justify-center gap-2">
                    <Button variant="brand" onClick={openComposer}>
                      <Plus className="size-4.5" aria-hidden /> {ft('feed.new')}
                    </Button>
                    <Button variant="outline" onClick={() => setTab('discover')}>
                      {ft('feed.goDiscover')}
                    </Button>
                  </div>
                }
              />
            ) : (
              <EmptyState
                art="discover"
                title={ft('feed.emptyDiscover')}
                body={ft('feed.emptyDiscoverBody')}
                action={
                  <Button variant="brand" onClick={openComposer}>
                    <Plus className="size-4.5" aria-hidden /> {ft('feed.new')}
                  </Button>
                }
              />
            )
          ) : (
            <FeedList posts={posts} />
          )}
        </div>

        <div ref={sentinel} className="h-px" aria-hidden />
        {posts.length ? (
          <div className="flex justify-center pt-5">
            {cur.loading ? (
              <Skeleton className="h-9 w-32 rounded-full" />
            ) : cur.failed ? (
              <Button variant="outline" size="sm" onClick={() => void loadMore(tab)}>
                <RefreshCw className="size-4" aria-hidden /> {t('common.tryAgain')}
              </Button>
            ) : cur.done ? (
              <p className="text-sm font-semibold text-muted-foreground">{ft('feed.end')}</p>
            ) : (
              <Button variant="ghost" size="sm" onClick={() => void loadMore(tab)}>
                {ft('feed.more')}
              </Button>
            )}
          </div>
        ) : null}
      </div>
      {composerUsed ? (
        <Suspense fallback={null}>
          <PostComposer open={composer} onClose={() => setComposer(false)} />
        </Suspense>
      ) : null}
    </div>
  )
}

function FeedList({ posts }: { posts: FeedPost[] }) {
  const { openPost } = useApp()
  return (
    <ul className="flex flex-col gap-4">
      {posts.map((p) => (
        <li key={p.id} className={cn('[content-visibility:auto] [contain-intrinsic-size:auto_520px]')}>
          <PostCard post={p} onComments={(focus) => openPost(p.id, { initial: postStore.get(p.id) ?? p, focus })} />
        </li>
      ))}
    </ul>
  )
}
