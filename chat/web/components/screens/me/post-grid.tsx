'use client'

import type { Accent, FeedPost, Post } from '@/lib/backend'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { EmptyState } from '@/components/ui/ambient'
import { LikeIcon } from '@/components/ui/topic-icons'
import { accentStyle } from '@/components/social/profile-bits'
import { usePostStoreVersion, postStore } from '@/components/social/posts/post-store'

/** 3-column grid; text-only posts become a tile in the owner's colour. A tap opens the post with its comments. */
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
    <ul className="grid grid-cols-3 gap-1 sm:gap-1.5" style={accentStyle(accent)}>
      {posts.map((p) => (
        <li key={p.id}>
          <button
            type="button"
            onClick={() => openPost(p.id, { initial: 'author' in p ? (p as FeedPost) : null })}
            className="group relative block aspect-square w-full overflow-hidden rounded-xl bg-muted sm:rounded-2xl"
            aria-label={p.text ? t('posts.aria', { text: p.text }) : t('posts.photo')}
          >
            {p.imageUrl ? (
              <img src={p.imageUrl} alt="" loading="lazy" decoding="async" className="size-full object-cover" />
            ) : (
              <span
                className="grid size-full place-items-center p-3 text-center text-sm font-bold text-white"
                style={{ background: 'linear-gradient(140deg, var(--acc-to), color-mix(in srgb, var(--acc-via) 70%, var(--acc-to)))' }}
              >
                <span className="line-clamp-4">{p.text}</span>
              </span>
            )}
            <span className="absolute inset-0 bg-black/0 transition-colors duration-150 group-hover:bg-black/15" aria-hidden />
            {p.likes ? (
              <span className="absolute bottom-1.5 left-1.5 flex items-center gap-1 rounded-full bg-black/45 py-0.5 pr-2 pl-1 text-[11px] font-bold text-white tabular-nums" aria-hidden>
                <LikeIcon on size={14} />
                {num(p.likes)}
              </span>
            ) : null}
          </button>
        </li>
      ))}
    </ul>
  )
}
