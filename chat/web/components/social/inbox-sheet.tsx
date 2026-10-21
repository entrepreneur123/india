'use client'

import { useEffect, useRef, useState } from 'react'
import useSWR, { useSWRConfig } from 'swr'
import { Check, Headphones, MessageCircle, X } from 'lucide-react'
import type { InboxItem } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { EmptyState } from '@/components/ui/ambient'
import { Avatar } from '@/components/ui/avatar'
import { Skeleton } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'
import { useSocialT } from './social-text'

/**
 * Notifications: friend requests and acceptances, friends' messages (one line per chat) and room invites. Opening it
 * marks everything read; what was new stays highlighted until it closes.
 */
export function InboxSheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  const backend = useBackend()
  const { mutate } = useSWRConfig()
  const { st } = useSocialT()
  const { t } = useI18n()
  const { data, error, mutate: reload } = useSWR(open ? 'inbox' : null, () => backend.inbox())
  // Unread when the sheet opened: they keep their highlight while it is open.
  const [fresh, setFresh] = useState<Set<number> | null>(null)
  const marked = useRef(false)

  useEffect(() => {
    if (!open) {
      setFresh(null)
      marked.current = false
      return
    }
    if (!data || marked.current) return
    marked.current = true
    const unread = data.filter((i) => !i.read)
    setFresh(new Set(unread.map((i) => i.id)))
    if (unread.length) {
      void backend.markInboxRead().then(() => mutate('badges'), () => undefined)
    }
  }, [open, data, backend, mutate])

  const isNew = (i: InboxItem) => fresh?.has(i.id) ?? !i.read
  const newer = data?.filter(isNew) ?? []
  const older = data?.filter((i) => !isNew(i)) ?? []

  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={st('inbox.title')} className="md:max-w-lg">
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
        ) : data.length === 0 ? (
          <EmptyState art="people" title={st('inbox.empty')} body={st('inbox.emptyBody')} className="py-10" />
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
  const { openProfile, openThread, openVoice } = useApp()
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
    default:
      text = st('inbox.invite', { name, room: item.roomTitle ?? '' })
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
  } else if (item.kind === 'message' || item.kind === 'friend_accept') {
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
        {item.kind === 'room_invite' ? (
          <span className="absolute -right-1 -bottom-1 grid size-6 place-items-center rounded-full bg-primary text-primary-foreground ring-2 ring-card">
            <Headphones className="size-3.5" aria-hidden />
          </span>
        ) : null}
      </button>
      <div className="min-w-0 flex-1">
        <p className="text-[14px] leading-snug">{text}</p>
        <p className="mt-0.5 text-xs text-muted-foreground">{timeAgo(item.at)}</p>
      </div>
      <div className="ml-auto shrink-0">{action}</div>
    </li>
  )
}
