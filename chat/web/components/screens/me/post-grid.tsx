'use client'

import { motion } from 'motion/react'
import { useState } from 'react'
import type { Post } from '@/lib/backend'
import { timeAgo } from '@/lib/format'
import { EmptyState } from '@/components/ui/ambient'
import { Modal } from '@/components/ui/modal'

/** Instagram-style 3-column grid; text-only posts become a gradient tile. */
export function PostGrid({ posts, owner, emptyAction }: { posts: Post[]; owner: string; emptyAction?: React.ReactNode }) {
  const [open, setOpen] = useState<Post | null>(null)
  if (posts.length === 0) {
    return <EmptyState art="posts" title="No posts yet" body={emptyAction ? 'Share a moment for people you meet to see.' : `${owner} hasn’t posted anything yet.`} action={emptyAction} />
  }
  return (
    <>
      <ul className="grid grid-cols-3 gap-0.5">
        {posts.map((p, i) => (
          <motion.li
            key={p.id}
            initial={{ opacity: 0, scale: 0.94 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ delay: Math.min(i, 12) * 0.03 }}
          >
            <button
              type="button"
              onClick={() => setOpen(p)}
              className="group relative block aspect-square w-full overflow-hidden bg-muted"
              aria-label={p.text ? `Post: ${p.text}` : 'Photo post'}
            >
              {p.imageUrl ? (
                <img src={p.imageUrl} alt="" loading="lazy" className="size-full object-cover transition-transform duration-500 group-hover:scale-108" />
              ) : (
                <span className="grid size-full place-items-center bg-brand p-3 text-center text-sm font-semibold text-primary-foreground">
                  <span className="line-clamp-4">{p.text}</span>
                </span>
              )}
              <span className="absolute inset-0 flex items-end bg-gradient-to-t from-black/60 via-transparent to-transparent p-2.5 text-left text-xs font-medium text-white opacity-0 transition-opacity duration-300 group-hover:opacity-100">
                <span className="line-clamp-2">{p.imageUrl ? p.text : timeAgo(p.createdAt)}</span>
              </span>
            </button>
          </motion.li>
        ))}
      </ul>
      <Modal open={open !== null} onClose={() => setOpen(null)} hideHeader className="max-w-lg">
        {open ? (
          <figure>
            {open.imageUrl ? <img src={open.imageUrl} alt="" className="max-h-[70dvh] w-full object-contain bg-black" /> : null}
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
