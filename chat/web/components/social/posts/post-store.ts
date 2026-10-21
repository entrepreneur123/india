'use client'

import { useCallback, useSyncExternalStore } from 'react'
import type { FeedPost } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { likeLocally } from '@/lib/feed'
import { useToast } from '@/components/ui/toast'

// The posts on screen, by id: a like, a comment or a delete in one place (the post sheet) shows at once everywhere
// else (the feed, a profile). The newest copy read from the server replaces what was there.
const posts = new Map<string, FeedPost>()
const gone = new Set<string>()
const listeners = new Set<() => void>()
const postedListeners = new Set<(p: FeedPost) => void>()
let version = 0

function emit() {
  version++
  for (const l of listeners) l()
}

export const postStore = {
  put(list: FeedPost[]) {
    for (const p of list) posts.set(p.id, p)
    if (list.length) emit()
  },
  patch(id: string, patch: Partial<FeedPost> | ((p: FeedPost) => FeedPost)) {
    const cur = posts.get(id)
    if (!cur) return
    posts.set(id, typeof patch === 'function' ? patch(cur) : { ...cur, ...patch })
    emit()
  },
  remove(id: string) {
    gone.add(id)
    posts.delete(id)
    emit()
  },
  get: (id: string) => posts.get(id),
  isGone: (id: string) => gone.has(id),
  /** A post I just wrote (the feed puts it on top). */
  posted(p: FeedPost) {
    posts.set(p.id, p)
    emit()
    for (const l of postedListeners) l(p)
  },
  onPosted(l: (p: FeedPost) => void) {
    postedListeners.add(l)
    return () => void postedListeners.delete(l)
  },
}

const subscribe = (l: () => void) => {
  listeners.add(l)
  return () => void listeners.delete(l)
}
const snapshot = () => version

/** The latest copy of this post (null once it was deleted). */
export function useLivePost<T extends FeedPost | null | undefined>(post: T): T | null {
  useSyncExternalStore(subscribe, snapshot, snapshot)
  if (!post) return post
  if (gone.has(post.id)) return null
  return (posts.get(post.id) as T | undefined) ?? post
}

/** Re-renders when any post changes (lists that drop deleted posts). */
export function usePostStoreVersion() {
  return useSyncExternalStore(subscribe, snapshot, snapshot)
}

/** My like, on screen at once; the server's answer sets the real count (a quick second tap wins). */
export function useLike() {
  const backend = useBackend()
  const toast = useToast()
  return useCallback(
    async (post: FeedPost, on: boolean) => {
      if (!posts.has(post.id)) posts.set(post.id, post)
      const before = posts.get(post.id)!
      if (before.liked === on) return
      const ticket = ++likeTickets
      lastTicket.set(post.id, ticket)
      postStore.patch(post.id, (p) => likeLocally(p, on))
      try {
        const res = await backend.likePost(post.id, on)
        if (lastTicket.get(post.id) === ticket) postStore.patch(post.id, { likes: res.likes, liked: res.liked })
      } catch (err) {
        if (lastTicket.get(post.id) === ticket) postStore.patch(post.id, (p) => likeLocally(p, !on))
        toast.error(err)
      }
    },
    [backend, toast],
  )
}
let likeTickets = 0
const lastTicket = new Map<string, number>()
