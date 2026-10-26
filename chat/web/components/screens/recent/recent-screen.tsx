'use client'

import { AnimatePresence } from 'motion/react'
import * as m from 'motion/react-m'
import { useState } from 'react'
import useSWR from 'swr'
import { AtSign, Flag, MessageCircle, UserMinus, X } from 'lucide-react'
import type { Friend, Profile, RecentMatch } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { flagEmoji } from '@/lib/format'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { EmptyState } from '@/components/ui/ambient'
import { Segmented, Skeleton } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { ReportSheet } from '@/components/screens/chat/report-sheet'
import { BellButton, CountBadge } from '@/components/social/bits'
import { useSocialT } from '@/components/social/social-text'
import { cn } from '@/lib/utils'
import { TrustBadge } from '@/components/trust/badge'
import { LookName } from '@/components/looks/look-name'
import { useLooksT } from '@/components/looks/looks-text'
import { FindSheet } from '@/components/social/people-find'
import { nameAge } from '@/lib/looks'
import { UserAvatar } from '@/components/looks/user-avatar'
import { GroupList, useGroups } from './groups'

type Segment = 'friends' | 'matches' | 'groups' | 'sent'
/** The segment shown last: back from a thread or a group, the list is where I left it. */
let lastSegment: Segment = 'friends'

export function RecentScreen() {
  const backend = useBackend()
  const { t, setTab } = useApp()
  const [segment, setSegmentState] = useState<Segment>(lastSegment)
  const setSegment = (s: Segment) => setSegmentState((lastSegment = s))
  const [findOpen, setFindOpen] = useState(false)
  const { lt } = useLooksT()
  const friends = useSWR('friends', () => backend.friends())
  const recents = useSWR('recents', () => backend.recents())
  const groups = useGroups()
  const { st } = useSocialT()
  const incoming = friends.data?.filter((f) => f.state === 'incoming').length ?? 0
  const { badges } = useApp()
  const unreadMatches = recents.data?.filter((r) => (badges?.chats[r.matchId] ?? 0) > 0).length ?? 0
  const unreadFriends = friends.data?.filter((f) => f.threadId && (badges?.chats[f.threadId] ?? 0) > 0).length ?? 0

  return (
    <div className="min-h-0 flex-1 overflow-y-auto scrollbar-thin">
      <div className="mx-auto flex max-w-2xl flex-col gap-6 px-4 py-6 sm:px-8 sm:py-10">
        <div className="flex items-center justify-between gap-4">
          <h1 className="text-[28px] leading-tight font-bold tracking-tight">{t('recent.title')}</h1>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="icon" onClick={() => setFindOpen(true)} aria-label={lt('find.title')}>
              <AtSign className="size-5" aria-hidden />
            </Button>
            <BellButton className="md:hidden" />
          </div>
        </div>
        <FindSheet open={findOpen} onClose={() => setFindOpen(false)} />
        <Segmented
          className="w-full"
          size="lg"
          label={t('recent.show')}
          value={segment}
          onChange={setSegment}
          options={[
            {
              value: 'friends',
              label: (
                <>
                  {t('recent.friends')}
                  <CountBadge n={incoming + unreadFriends} className="ms-1.5" />
                </>
              ),
            },
            {
              value: 'matches',
              label: (
                <>
                  {t('recent.matches')}
                  <CountBadge n={unreadMatches} className="ms-1.5" />
                </>
              ),
            },
            // 48: group chats among friends.
            {
              value: 'groups',
              label: (
                <>
                  {st('group.tab')}
                  <CountBadge n={groups.data?.filter((g) => g.unread > 0).length ?? 0} className="ms-1.5" />
                </>
              ),
            },
            // Requests I sent that wait for an answer (withdrawn from here).
            { value: 'sent', label: t('recent.sentTab') },
          ]}
        />
        <AnimatePresence mode="wait" initial={false}>
          <m.div key={segment} initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.12 }}>
            {segment === 'groups' ? (
              <GroupList />
            ) : segment !== 'matches' ? (
              <FriendsList
                sent={segment === 'sent'}
                list={friends.data}
                failed={!!friends.error}
                retry={() => void friends.mutate()}
                refresh={() => void friends.mutate()}
                onFind={() => setTab('random')}
              />
            ) : (
              <MatchesList
                list={recents.data}
                failed={!!recents.error}
                retry={() => void recents.mutate()}
                onReported={(id) => {
                  void recents.mutate((l) => l?.filter((r) => r.partner.id !== id), { revalidate: false })
                  void friends.mutate()
                }}
                onFind={() => setTab('random')}
              />
            )}
          </m.div>
        </AnimatePresence>
      </div>
    </div>
  )
}

function ListSkeleton() {
  return (
    <ul className="flex flex-col gap-2">
      {Array.from({ length: 4 }, (_, i) => (
        <li key={i} className="flex items-center gap-3 p-2">
          <Skeleton className="size-12 rounded-full" />
          <div className="flex flex-1 flex-col gap-2">
            <Skeleton className="h-3.5 w-32" />
            <Skeleton className="h-3 w-48" />
          </div>
        </li>
      ))}
    </ul>
  )
}

function Failed({ retry }: { retry: () => void }) {
  const { t } = useI18n()
  return (
    <EmptyState
      art="people"
      title={t('recent.loadFailed')}
      body={t('common.checkConnection')}
      action={<Button variant="outline" onClick={retry}>{t('common.tryAgain')}</Button>}
    />
  )
}

function Who({ p, detail }: { p: Profile; detail?: React.ReactNode }) {
  const { openProfile } = useApp()
  return (
    <button type="button" onClick={() => openProfile(p.id)} className="flex min-w-0 flex-1 items-center gap-3 rounded-lg text-start">
      <UserAvatar userId={p.id} name={p.name} src={p.photoUrl} size={48} country={p.country} />
      <span className="min-w-0">
        <span className="flex items-center gap-1.5 text-[15px] font-semibold">
          <span className="truncate">{nameAge(p.name, p.age)}</span>
          <TrustBadge userId={p.id} size={15} />
        </span>
        <LookName userId={p.id} handle={p.handle} className="text-[13px] font-semibold" />
        <span className="block truncate text-sm text-muted-foreground">{detail}</span>
      </span>
    </button>
  )
}

/** My friends and the requests waiting for me, or (`sent`) the requests I sent. */
function FriendsList({
  sent: sentView,
  list,
  failed,
  retry,
  refresh,
  onFind,
}: {
  sent: boolean
  list: Friend[] | undefined
  failed: boolean
  retry: () => void
  refresh: () => void
  onFind: () => void
}) {
  const backend = useBackend()
  const toast = useToast()
  const { t } = useI18n()
  const { openThread, badges } = useApp()
  const [busy, setBusy] = useState<string | null>(null)
  const [unfriend, setUnfriend] = useState<Profile | null>(null)

  if (!list) return failed ? <Failed retry={retry} /> : <ListSkeleton />

  const run = async (id: string, action: () => Promise<unknown>, done?: string) => {
    setBusy(id)
    try {
      await action()
      if (done) toast.show(done, 'success')
      refresh()
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(null)
    }
  }

  const incoming = sentView ? [] : list.filter((f) => f.state === 'incoming')
  const friends = sentView ? [] : list.filter((f) => f.state === 'friends')
  const sent = sentView ? list.filter((f) => f.state === 'requested') : []

  if (sentView && !sent.length) return <EmptyState art="people" title={t('recent.noSent')} body={t('recent.noSentBody')} />
  if (!sentView && !incoming.length && !friends.length) {
    return (
      <EmptyState
        art="people"
        title={t('recent.noFriends')}
        body={t('recent.noFriendsBody')}
        action={<Button variant="brand" onClick={onFind}>{t('recent.meet')}</Button>}
      />
    )
  }

  return (
    <div className="flex flex-col gap-8">
      {incoming.length ? (
        <Section title={t('recent.requests')} count={incoming.length}>
          {incoming.map((f) => (
            <Row key={f.profile.id} className="flex-wrap bg-muted sm:flex-nowrap">
              <Who p={f.profile} detail={t('recent.wants')} />
              <div className="ms-auto flex gap-2">
                <Button
                  size="sm"
                  variant="outline"
                  disabled={busy === f.profile.id}
                  onClick={() => void run(f.profile.id, () => backend.answerFriend(f.profile.id, false))}
                >
                  {t('recent.decline')}
                </Button>
                <Button
                  size="sm"
                  variant="brand"
                  loading={busy === f.profile.id}
                  onClick={() =>
                    void run(f.profile.id, () => backend.answerFriend(f.profile.id, true), t('recent.nowFriends', { name: f.profile.name }))
                  }
                >
                  {t('recent.accept')}
                </Button>
              </div>
            </Row>
          ))}
        </Section>
      ) : null}

      {friends.length ? (
        <Section title={t('recent.friends')} count={friends.length}>
          {friends.map((f) => (
            <Row key={f.profile.id}>
              <Who p={f.profile} />
              <Unread n={f.threadId ? badges?.chats[f.threadId] ?? 0 : 0} />
              <Button size="icon" variant="ghost" onClick={() => openThread(f.profile)} aria-label={t('panel.message', { name: f.profile.name })}>
                <MessageCircle className="size-5" aria-hidden />
              </Button>
              <Button
                size="icon"
                variant="ghost"
                className="text-muted-foreground"
                onClick={() => setUnfriend(f.profile)}
                aria-label={t('recent.removeAria', { name: f.profile.name })}
              >
                <UserMinus className="size-5" aria-hidden />
              </Button>
            </Row>
          ))}
        </Section>
      ) : null}

      {sent.length ? (
        <Section title={t('recent.sent')} count={sent.length}>
          {sent.map((f) => (
            <Row key={f.profile.id}>
              <Who p={f.profile} detail={t('recent.requestSent')} />
              <Button
                size="sm"
                variant="outline"
                loading={busy === f.profile.id}
                onClick={() => void run(f.profile.id, () => backend.removeFriend(f.profile.id))}
              >
                <X className="size-4" aria-hidden /> {t('recent.withdraw')}
              </Button>
            </Row>
          ))}
        </Section>
      ) : null}

      <Modal
        open={unfriend !== null}
        onClose={() => setUnfriend(null)}
        title={unfriend ? t('recent.removeTitle', { name: unfriend.name }) : ''}
        description={t('recent.removeBody')}
      >
        <div className="flex gap-3 p-6 pt-5">
          <Button variant="outline" className="flex-1" onClick={() => setUnfriend(null)}>
            {t('common.cancel')}
          </Button>
          <Button
            variant="danger"
            className="flex-1"
            loading={unfriend !== null && busy === unfriend.id}
            onClick={() => {
              const p = unfriend
              if (!p) return
              void run(p.id, () => backend.removeFriend(p.id)).then(() => setUnfriend(null))
            }}
          >
            {t('recent.remove')}
          </Button>
        </div>
      </Modal>
    </div>
  )
}

function MatchesList({
  list,
  failed,
  retry,
  onReported,
  onFind,
}: {
  list: RecentMatch[] | undefined
  failed: boolean
  retry: () => void
  onReported: (partnerId: string) => void
  onFind: () => void
}) {
  const { openThread, badges } = useApp()
  const { t, timeAgo } = useI18n()
  const [reporting, setReporting] = useState<RecentMatch | null>(null)

  if (!list) return failed ? <Failed retry={retry} /> : <ListSkeleton />
  if (list.length === 0) {
    return (
      <EmptyState
        art="chat"
        title={t('recent.noMatches')}
        body={t('recent.noMatchesBody')}
        action={<Button variant="brand" onClick={onFind}>{t('recent.startRandom')}</Button>}
      />
    )
  }

  return (
    <>
      <ul className="flex flex-col">
        {list.map((r) => (
          <li key={r.matchId}>
            <Row>
              <Who
                p={r.partner}
                detail={
                  <>
                    <span aria-hidden>{flagEmoji(r.partner.country)} </span>
                    {r.lastText ? `${r.lastText} · ` : ''}
                    {timeAgo(r.metAt)}
                  </>
                }
              />
              <Unread n={badges?.chats[r.matchId] ?? 0} />
              <Button size="icon" variant="ghost" onClick={() => openThread(r.partner, r.matchId)} aria-label={t('panel.message', { name: r.partner.name })}>
                <MessageCircle className="size-5" aria-hidden />
              </Button>
              <Button
                size="icon"
                variant="ghost"
                className="text-muted-foreground hover:text-destructive"
                onClick={() => setReporting(r)}
                aria-label={t('chat.report', { name: r.partner.name })}
              >
                <Flag className="size-5" aria-hidden />
              </Button>
            </Row>
          </li>
        ))}
      </ul>
      {reporting ? (
        <ReportSheet
          open
          onClose={() => setReporting(null)}
          partner={reporting.partner}
          matchId={reporting.matchId}
          onReported={() => onReported(reporting.partner.id)}
        />
      ) : null}
    </>
  )
}

function Section({ title, count, children }: { title: string; count: number; children: React.ReactNode }) {
  return (
    <section className="flex flex-col gap-2">
      <h2 className="px-1 text-sm font-semibold text-muted-foreground">
        {title} · <span className="tabular-nums">{count}</span>
      </h2>
      <div className="flex flex-col gap-1">{children}</div>
    </section>
  )
}

function Row({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <div className={cn('flex items-center gap-2 rounded-2xl p-2.5 transition-colors duration-150 hover:bg-muted', className)}>
      {children}
    </div>
  )
}

/** Unread messages in a chat (from my_badges). */
function Unread({ n }: { n: number }) {
  const { st } = useSocialT()
  if (n <= 0) return null
  return (
    <>
      <CountBadge n={n} />
      <span className="sr-only">{st('unread.count', { n })}</span>
    </>
  )
}
