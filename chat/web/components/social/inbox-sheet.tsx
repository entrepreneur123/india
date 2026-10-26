'use client'

import { useEffect, useState } from 'react'
import useSWR, { useSWRConfig } from 'swr'
import { Check, Headphones, MessageCircle, Phone, X } from 'lucide-react'
import type { InboxItem, InboxKind } from '@/lib/backend'
import { likeLine } from '@/lib/feed'
import { useBackend } from '@/lib/backend-context'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { EmptyState } from '@/components/ui/ambient'
import { Avatar } from '@/components/ui/avatar'
import { Skeleton } from '@/components/ui/bits'
import { CommentIcon, LikeIcon } from '@/components/ui/topic-icons'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'
import { useSocialT } from './social-text'

/** The sheet's sections (the owner, 2026-10-26): invites, friends' messages, friend requests, likes and comments. */
type Section = 'all' | 'invites' | 'messages' | 'friends' | 'reactions'
const SECTION_OF: Record<InboxKind, Exclude<Section, 'all'>> = {
  room_invite: 'invites', call: 'invites', message: 'messages', friend_request: 'friends', friend_accept: 'friends',
  post_like: 'reactions', post_comment: 'reactions',
}

/**
 * Notifications: friend requests and acceptances, friends' messages (one line per chat), friends' calls, room invites,
 * and likes and comments on my posts (one line per post). Opening it marks everything read; what was new stays highlighted until it
 * closes. Lines of a kind this app doesn't know never reach here (lib/feed.ts inboxKind).
 */
export function InboxSheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  const backend = useBackend()
  const { mutate } = useSWRConfig()
  const { st } = useSocialT()
  const { t } = useI18n()
  // Read here on each opening (below), never from what was cached at the last one: that list may predate the items
  // the bell counts, and judging by it left them unread.
  const { data, error, mutate: reload } = useSWR(open ? 'inbox' : null, () => backend.inbox(), { revalidateOnMount: false })
  // Unread when the sheet opened: they keep their highlight while it is open.
  const [fresh, setFresh] = useState<Set<number> | null>(null)
  const [section, setSection] = useState<Section>('all')

  useEffect(() => {
    if (!open) {
      setFresh(null)
      setSection('all')
      return
    }
    let alive = true
    // The bell has cleared already (the shell's openInbox); everything is marked read once the list, with what was
    // new, has been read.
    reload()
      .then((list) => {
        if (!list) return
        // Closed before the list came, or nothing new: nothing is marked read.
        if (!alive || list.every((i) => i.read)) return
        setFresh(new Set(list.filter((i) => !i.read).map((i) => i.id)))
        return backend.markInboxRead()
      })
      .catch(() => undefined)
      .finally(() => void mutate('badges'))
    return () => {
      alive = false
    }
  }, [open, backend, mutate, reload])

  const isNew = (i: InboxItem) => fresh?.has(i.id) ?? !i.read
  const shown = data?.filter((i) => section === 'all' || SECTION_OF[i.kind] === section) ?? []
  const newer = shown.filter(isNew)
  const older = shown.filter((i) => !isNew(i))
  // A dot on the sections with something new.
  const label = (s: Section, text: string) => (
    <span className="relative">
      {text}
      {s !== 'all' && data?.some((i) => SECTION_OF[i.kind] === s && isNew(i)) ? (
        <span className="absolute -top-0.5 -end-2 size-1.5 rounded-full bg-primary" aria-hidden />
      ) : null}
    </span>
  )

  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={st('inbox.title')} className="md:max-w-lg">
      <div role="tablist" aria-label={st('inbox.title')} className="no-scrollbar flex gap-2 overflow-x-auto px-4 pb-2">
        {(['all', 'invites', 'messages', 'friends', 'reactions'] as const).map((x) => (
          <button
            key={x}
            type="button"
            role="tab"
            aria-selected={section === x}
            onClick={() => setSection(x)}
            className={cn(
              'shrink-0 rounded-full border px-3.5 py-1.5 text-sm font-medium whitespace-nowrap transition-colors',
              section === x ? 'border-primary bg-primary-soft text-primary-text' : 'border-border text-muted-foreground hover:bg-muted',
            )}
          >
            {label(x, st(`inbox.${x}`))}
          </button>
        ))}
      </div>
      <div className="max-h-[70dvh] overflow-y-auto pb-4 scrollbar-thin">
        {!data ? (
          error ? (
            <EmptyState
              art="people"
              title={st('inbox.loadFailed')}
              body={t('common.checkConnection')}
              action={<Button variant="outline" onClick={() => void reload()}>{t('common.tryAgain')}</Button>}
            />
          ) : (
            <ul className="flex flex-col gap-1 px-4 pt-2" aria-busy>
              {Array.from({ length: 4 }, (_, i) => (
                <li key={i} className="flex items-center gap-3 p-2">
                  <Skeleton className="size-11 rounded-full" />
                  <div className="flex flex-1 flex-col gap-2">
                    <Skeleton className="h-3.5 w-48" />
                    <Skeleton className="h-3 w-20" />
                  </div>
                </li>
              ))}
            </ul>
          )
        ) : shown.length === 0 ? (
          <EmptyState art="people" title={st('inbox.empty')} body={section === 'all' ? st('inbox.emptyBody') : undefined} className="py-10" />
        ) : (
          <>
            {newer.length ? <Group title={st('inbox.new')} items={newer} highlight onDone={onClose} /> : null}
            {older.length ? <Group title={st('inbox.earlier')} items={older} onDone={onClose} /> : null}
          </>
        )}
      </div>
    </Modal>
  )
}

function Group({ title, items, highlight, onDone }: { title: string; items: InboxItem[]; highlight?: boolean; onDone: () => void }) {
  return (
    <section className="flex flex-col pt-2">
      <h3 className="px-6 pt-2 pb-1 text-xs font-semibold tracking-wide text-muted-foreground uppercase">{title}</h3>
      <ul className="flex flex-col">
        {items.map((i) => (
          <Row key={i.id} item={i} highlight={highlight} onDone={onDone} />
        ))}
      </ul>
    </section>
  )
}

function Row({ item, highlight, onDone }: { item: InboxItem; highlight?: boolean; onDone: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { mutate } = useSWRConfig()
  const { t, timeAgo } = useI18n()
  const { st, plural } = useSocialT()
  const { openProfile, openThread, openVoice, openPost } = useApp()
  const [busy, setBusy] = useState(false)
  const name = item.actor.name

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true)
    try {
      await action()
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }
  const answer = (accept: boolean) =>
    run(async () => {
      await backend.answerFriend(item.actor.id, accept)
      if (accept) toast.show(t('recent.nowFriends', { name }), 'success')
      void mutate('friends')
      void mutate('badges')
      await mutate('inbox')
    })

  let text: string
  switch (item.kind) {
    case 'message':
      text = item.count > 1 ? plural(item.count, 'inbox.message.one', 'inbox.message.other').replace('{name}', name) : st('inbox.message.one', { name })
      break
    case 'friend_request':
      text = st(item.pending ? 'inbox.request' : 'inbox.requestDone', { name })
      break
    case 'friend_accept':
      text = st('inbox.accept', { name })
      break
    case 'room_invite':
      text = st('inbox.invite', { name, room: item.roomTitle ?? '' })
      break
    case 'call':
      text = st('inbox.call', { name })
      break
    case 'post_like': {
      const line = likeLine(item.count)
      text = st(`inbox.like.${line.key}`, { name, n: line.others })
      break
    }
    case 'post_comment':
      text = item.count > 1 ? st('inbox.comment.other', { name, n: item.count - 1 }) : st('inbox.comment.single', { name })
      break
    default:
      return null
  }
  const post = item.kind === 'post_like' || item.kind === 'post_comment' ? item.post : null
  const showPost = () => {
    if (!post) return
    onDone()
    openPost(post.id, { focus: false })
  }

  let action: React.ReactNode = null
  if (item.kind === 'friend_request' && item.pending) {
    action = (
      <div className="flex gap-2">
        <Button size="icon-sm" variant="outline" disabled={busy} onClick={() => void answer(false)} aria-label={t('recent.decline')}>
          <X className="size-4" aria-hidden />
        </Button>
        <Button size="sm" variant="brand" loading={busy} onClick={() => void answer(true)}>
          <Check className="size-4" aria-hidden /> {t('recent.accept')}
        </Button>
      </div>
    )
  } else if (item.kind === 'message' || item.kind === 'friend_accept' || item.kind === 'call') {
    action = (
      <Button
        size="sm"
        variant="secondary"
        onClick={() => {
          onDone()
          openThread(item.actor, item.matchId ?? undefined)
        }}
      >
        <MessageCircle className="size-4" aria-hidden /> {item.kind === 'message' ? st('inbox.reply') : t('profile.message')}
      </Button>
    )
  } else if (post) {
    // The post it is about: its photo, or the start of its text.
    action = (
      <button
        type="button"
        onClick={showPost}
        className="grid size-12 place-items-center overflow-hidden rounded-xl bg-primary-soft text-[10px] leading-tight font-semibold text-primary-text"
        aria-label={st('inbox.viewPost')}
      >
        {post.imageUrl ? (
          <img src={post.imageUrl} alt="" className="size-full object-cover" loading="lazy" />
        ) : (
          <span className="line-clamp-3 px-1 text-center break-words">{post.text}</span>
        )}
      </button>
    )
  } else if (item.kind === 'room_invite') {
    action = item.roomOpen ? (
      <Button
        size="sm"
        variant="brand"
        loading={busy}
        onClick={() =>
          void run(async () => {
            const roomId = await backend.joinInvite(item.id)
            onDone()
            openVoice(roomId)
          })
        }
      >
        {st('inbox.join')}
      </Button>
    ) : (
      <span className="text-xs text-muted-foreground">{st('inbox.inviteEnded')}</span>
    )
  }

  return (
    <li className={cn('flex flex-wrap items-center gap-3 px-4 py-2.5 sm:flex-nowrap sm:px-6', highlight && 'bg-primary-soft/40')}>
      <button type="button" onClick={() => openProfile(item.actor.id)} className="relative shrink-0 rounded-full" aria-label={name}>
        <Avatar name={name} src={item.actor.photoUrl} size={44} />
        {item.kind === 'room_invite' || item.kind === 'call' ? (
          <span className="absolute -inset-e-1 -bottom-1 grid size-6 place-items-center rounded-full bg-primary-strong text-primary-foreground ring-2 ring-card">
            {item.kind === 'call' ? <Phone className="size-3.5" aria-hidden /> : <Headphones className="size-3.5" aria-hidden />}
          </span>
        ) : item.kind === 'post_like' ? (
          <span className="absolute -inset-e-1 -bottom-1 grid size-6 place-items-center rounded-full bg-card ring-2 ring-card">
            <LikeIcon on size={18} />
          </span>
        ) : item.kind === 'post_comment' ? (
          <span className="absolute -inset-e-1 -bottom-1 grid size-6 place-items-center rounded-full bg-primary text-primary-foreground ring-2 ring-card">
            <CommentIcon size={14} />
          </span>
        ) : null}
      </button>
      {post ? (
        <button type="button" onClick={showPost} className="min-w-0 flex-1 rounded-lg text-start">
          <span className="block text-[14px] leading-snug">{text}</span>
          {post.text ? <span className="mt-0.5 block truncate text-xs text-muted-foreground">“{post.text}”</span> : null}
          <span className="mt-0.5 block text-xs text-muted-foreground">{timeAgo(item.at)}</span>
        </button>
      ) : (
        <div className="min-w-0 flex-1">
          <p className="text-[14px] leading-snug">{text}</p>
          <p className="mt-0.5 text-xs text-muted-foreground">{timeAgo(item.at)}</p>
        </div>
      )}
      <div className="ms-auto shrink-0">{action}</div>
    </li>
  )
}
