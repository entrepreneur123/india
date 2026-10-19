'use client'

import { useState } from 'react'
import type { Post } from '@/lib/backend'
import { useI18n } from '@/components/i18n'
import { EmptyState } from '@/components/ui/ambient'
import { Modal } from '@/components/ui/modal'

/** 3-column grid; text-only posts become a tinted tile. */
export function PostGrid({ posts, owner, emptyAction }: { posts: Post[]; owner: string; emptyAction?: React.ReactNode }) {
  const { t, timeAgo } = useI18n()
  const [open, setOpen] = useState<Post | null>(null)
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
    <>
      <ul className="grid grid-cols-3 gap-0.5">
        {posts.map((p) => (
          <li key={p.id}>
            <button
              type="button"
              onClick={() => setOpen(p)}
              className="group relative block aspect-square w-full overflow-hidden bg-muted"
              aria-label={p.text ? t('posts.aria', { text: p.text }) : t('posts.photo')}
            >
              {p.imageUrl ? (
                <img src={p.imageUrl} alt="" loading="lazy" decoding="async" className="size-full object-cover" />
              ) : (
                <span className="grid size-full place-items-center bg-primary-soft p-3 text-center text-sm font-medium text-foreground">
                  <span className="line-clamp-4">{p.text}</span>
                </span>
              )}
              <span className="absolute inset-0 bg-black/0 transition-colors duration-150 group-hover:bg-black/10" aria-hidden />
            </button>
          </li>
        ))}
      </ul>
      <Modal open={open !== null} onClose={() => setOpen(null)} hideHeader className="max-w-lg">
        {open ? (
          <figure>
            {open.imageUrl ? <img src={open.imageUrl} alt="" className="max-h-[70dvh] w-full bg-black object-contain" /> : null}
            <figcaption className="flex flex-col gap-1 p-5">
              {open.text ? <p className="text-[15px] leading-relaxed whitespace-pre-wrap">{open.text}</p> : null}
              <p className="text-xs text-muted-foreground">{timeAgo(open.createdAt)}</p>
            </figcaption>
          </figure>
        ) : null}
      </Modal>
    </>
  )
}
