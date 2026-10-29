'use client'

import type { Accent, FeedPost, Post } from '@/lib/backend'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { EmptyState } from '@/components/ui/ambient'
import { LikeIcon } from '@/components/ui/topic-icons'
import { accentStyle } from '@/components/social/profile-bits'
import { usePostStoreVersion, postStore } from '@/components/social/posts/post-store'

/**
 * Instagram's grid: 3 square columns with hairline gaps, no corners; text-only posts become a tile in the owner's
 * colour. Pointing at one (computers) shows its likes, as Instagram's web does. A tap opens the post.
 */
export function PostGrid({ posts: all, owner, accent, emptyAction }: { posts: Post[]; owner: string; accent?: Accent | null; emptyAction?: React.ReactNode }) {
  const { t, num } = useI18n()
  const { openPost } = useApp()
  usePostStoreVersion()
  const posts = all.filter((p) => !postStore.isGone(p.id)).map((p) => postStore.get(p.id) ?? p)
  if (posts.length === 0) {
    return (
      <EmptyState
        art="posts"
        title={t('posts.none')}
        body={emptyAction ? t('posts.noneMine') : t('posts.noneOther', { name: owner })}
        action={emptyAction}
      />
    )
  }
  return (
    <ul className="grid grid-cols-3 gap-0.5" style={accentStyle(accent)}>
      {posts.map((p) => (
        <li key={p.id}>
          <button
            type="button"
            onClick={() => openPost(p.id, { initial: 'author' in p ? (p as FeedPost) : null })}
            className="group relative block aspect-square w-full overflow-hidden bg-muted"
            aria-label={p.text ? t('posts.aria', { text: p.text }) : t('posts.photo')}
          >
            {p.imageUrl ? (
              <img src={p.imageUrl} alt="" loading="lazy" decoding="async" className="size-full object-cover" />
            ) : (
              <span
                className="grid size-full place-items-center p-3 text-center text-sm font-bold text-white"
                style={{ background: 'var(--acc-to)' }}
              >
                <span className="line-clamp-4">{p.text}</span>
              </span>
            )}
            <span
              className="absolute inset-0 flex items-center justify-center gap-1.5 bg-black/30 text-[15px] font-bold text-white tabular-nums opacity-0 transition-opacity duration-150 group-hover:opacity-100"
              aria-hidden
            >
              <LikeIcon on size={18} className="text-white" />
              {num(p.likes ?? 0)}
            </span>
          </button>
        </li>
      ))}
    </ul>
  )
}
