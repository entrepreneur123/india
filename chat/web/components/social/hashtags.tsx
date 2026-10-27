'use client'

import { useState } from 'react'
import useSWR from 'swr'
import { Hash } from 'lucide-react'
import { useBackend } from '@/lib/backend-context'
import { splitTags } from '@/lib/hashtags'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Skeleton } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { UserAvatar } from '@/components/looks/user-avatar'
import { cn } from '@/lib/utils'

/** A post's text with its #tags as links (072): a tag opens the posts that carry it. */
export function TagText({ text, className, tagClass = 'text-primary-text' }: { text: string; className?: string; tagClass?: string }) {
  const [tag, setTag] = useState<string | null>(null)
  return (
    <>
      <span className={className}>
        {splitTags(text).map((part, i) =>
          part.tag ? (
            <button
              key={i}
              type="button"
              // The post's own taps (double tap to like) stay out of it.
              onPointerUp={(e) => e.stopPropagation()}
              onClick={(e) => {
                e.stopPropagation()
                setTag(part.tag)
              }}
              className={cn('font-semibold underline-offset-2 hover:underline', tagClass)}
            >
              {part.text}
            </button>
          ) : (
            part.text
          ),
        )}
      </span>
      {tag ? <TagPostsSheet tag={tag} onClose={() => setTag(null)} /> : null}
    </>
  )
}

function TagPostsSheet({ tag, onClose }: { tag: string; onClose: () => void }) {
  return (
    <Modal open onClose={onClose} variant="sheet" title={`#${tag}`}>
      <div className="px-3 pt-1 pb-5">
        <TagPosts tag={tag} onOpen={onClose} />
      </div>
    </Modal>
  )
}

/** The posts I may see with a hashtag, newest first (Explore's "#..." search shows the same list). */
export function TagPosts({ tag, onOpen }: { tag: string; onOpen?: () => void }) {
  const backend = useBackend()
  const { t, timeAgo } = useI18n()
  const { openPost } = useApp()
  const { data } = useSWR(['posts-tag', tag], () => backend.postsByTag(tag))
  if (!data) return <Skeleton className="h-20 w-full rounded-2xl" />
  if (!data.length)
    return (
      <p className="flex flex-col items-center gap-2 py-10 text-center text-sm text-muted-foreground">
        <Hash className="size-6" aria-hidden />
        {t('explore.noResults')}
      </p>
    )
  return (
    <ul className="flex flex-col">
      {data.map((p) => (
        <li key={p.id}>
          <button
            type="button"
            onClick={() => {
              onOpen?.()
              openPost(p.id, { initial: p })
            }}
            className="flex w-full items-start gap-3 rounded-2xl p-2.5 text-start transition-colors hover:bg-muted"
          >
            <UserAvatar userId={p.author.id} name={p.author.name} src={p.author.photoUrl} size={40} />
            <span className="min-w-0 flex-1">
              <span className="flex items-baseline gap-2">
                <span className="truncate text-sm font-bold">{p.author.name}</span>
                <span className="shrink-0 text-xs text-muted-foreground">{timeAgo(p.createdAt)}</span>
              </span>
              <span className={cn('mt-0.5 line-clamp-2 text-sm text-pretty', !p.text && 'text-muted-foreground')}>{p.text}</span>
            </span>
            {p.imageUrl ? <img src={p.imageUrl} alt="" className="size-14 shrink-0 rounded-xl object-cover" loading="lazy" /> : null}
          </button>
        </li>
      ))}
    </ul>
  )
}
