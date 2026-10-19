'use client'

import { AnimatePresence, motion } from 'motion/react'
import { useState } from 'react'
import useSWR from 'swr'
import { Flag, MessageCircle, UserMinus, X } from 'lucide-react'
import type { Friend, Profile, RecentMatch } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { flagEmoji, timeAgo } from '@/lib/format'
import { useApp } from '@/components/app-state'
import { EmptyState } from '@/components/ui/ambient'
import { Avatar } from '@/components/ui/avatar'
import { Segmented, Skeleton } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { ReportSheet } from '@/components/screens/chat/report-sheet'

type Segment = 'friends' | 'matches'

export function RecentScreen() {
  const backend = useBackend()
  const { t, setTab } = useApp()
  const [segment, setSegment] = useState<Segment>('friends')
  const friends = useSWR('friends', () => backend.friends())
  const recents = useSWR('recents', () => backend.recents())
  const incoming = friends.data?.filter((f) => f.state === 'incoming').length ?? 0

  return (
    <div className="min-h-0 flex-1 overflow-y-auto scrollbar-thin">
      <div className="mx-auto flex max-w-2xl flex-col gap-6 px-4 py-6 sm:px-8 sm:py-10">
        <h1 className="text-3xl font-extrabold tracking-tight">{t('recent.title')}</h1>
        <Segmented
          className="w-full"
          size="lg"
          label="Show"
          value={segment}
          onChange={setSegment}
          options={[
            {
              value: 'friends',
              label: (
                <>
                  {t('recent.friends')}
                  {incoming > 0 ? (
                    <span className="grid min-w-5 place-items-center rounded-full bg-primary px-1.5 text-[11px] font-bold text-primary-foreground">
                      {incoming}
                    </span>
                  ) : null}
                </>
              ),
            },
            { value: 'matches', label: t('recent.matches') },
          ]}
        />
        <AnimatePresence mode="wait" initial={false}>
          <motion.div
            key={segment}
            initial={{ opacity: 0, x: segment === 'friends' ? -16 : 16 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.22 }}
          >
            {segment === 'friends' ? (
              <FriendsList
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
          </motion.div>
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
          <Skeleton className="size-14 rounded-full" />
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
  return (
    <EmptyState
      art="people"
      title="Couldn’t load this"
      body="Check your connection and try again."
      action={<Button variant="secondary" onClick={retry}>Try again</Button>}
    />
  )
}

function Who({ p, detail }: { p: Profile; detail?: React.ReactNode }) {
  const { openProfile } = useApp()
  return (
    <button type="button" onClick={() => openProfile(p.id)} className="flex min-w-0 flex-1 items-center gap-3 text-left">
      <Avatar name={p.name} src={p.photoUrl} size={54} country={p.country} />
      <span className="min-w-0">
        <span className="block truncate text-[15px] font-bold">
          {p.name}, {p.age}
        </span>
        <span className="block truncate text-sm text-muted-foreground">{detail ?? p.city}</span>
      </span>
    </button>
  )
}

function FriendsList({
  list,
  failed,
  retry,
  refresh,
  onFind,
}: {
  list: Friend[] | undefined
  failed: boolean
  retry: () => void
  refresh: () => void
  onFind: () => void
}) {
  const backend = useBackend()
  const toast = useToast()
  const { openThread } = useApp()
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

  const incoming = list.filter((f) => f.state === 'incoming')
  const friends = list.filter((f) => f.state === 'friends')
  const sent = list.filter((f) => f.state === 'requested')

  if (list.length === 0) {
    return (
      <EmptyState
        art="people"
        title="No friends yet"
        body="Add people you click with in a random chat or a room — once they accept, you can message them anytime."
        action={<Button variant="brand" onClick={onFind}>Meet someone</Button>}
      />
    )
  }

  return (
    <div className="flex flex-col gap-7">
      {incoming.length ? (
        <Section title="Friend requests" count={incoming.length}>
          {incoming.map((f) => (
            <Row key={f.profile.id} className="bg-muted/70">
              <Who p={f.profile} detail="Wants to be friends" />
              <Button
                size="sm"
                variant="outline"
                disabled={busy === f.profile.id}
                onClick={() => void run(f.profile.id, () => backend.answerFriend(f.profile.id, false))}
              >
                Decline
              </Button>
              <Button
                size="sm"
                variant="brand"
                loading={busy === f.profile.id}
                onClick={() => void run(f.profile.id, () => backend.answerFriend(f.profile.id, true), `You and ${f.profile.name} are friends`)}
              >
                Accept
              </Button>
            </Row>
          ))}
        </Section>
      ) : null}

      {friends.length ? (
        <Section title="Friends" count={friends.length}>
          {friends.map((f) => (
            <Row key={f.profile.id}>
              <Who p={f.profile} />
              <Button size="icon" variant="secondary" onClick={() => openThread(f.profile)} aria-label={`Message ${f.profile.name}`}>
                <MessageCircle className="size-5" aria-hidden />
              </Button>
              <Button
                size="icon"
                variant="ghost"
                className="text-muted-foreground"
                onClick={() => setUnfriend(f.profile)}
                aria-label={`Remove ${f.profile.name} from friends`}
              >
                <UserMinus className="size-5" aria-hidden />
              </Button>
            </Row>
          ))}
        </Section>
      ) : null}

      {sent.length ? (
        <Section title="Sent requests" count={sent.length}>
          {sent.map((f) => (
            <Row key={f.profile.id}>
              <Who p={f.profile} detail="Request sent" />
              <Button
                size="sm"
                variant="secondary"
                loading={busy === f.profile.id}
                onClick={() => void run(f.profile.id, () => backend.removeFriend(f.profile.id))}
              >
                <X className="size-4" aria-hidden /> Withdraw
              </Button>
            </Row>
          ))}
        </Section>
      ) : null}

      <Modal
        open={unfriend !== null}
        onClose={() => setUnfriend(null)}
        title={unfriend ? `Remove ${unfriend.name}?` : ''}
        description="You won’t be able to message each other until one of you sends a new request."
      >
        <div className="flex gap-3 p-6 pt-5">
          <Button variant="secondary" className="flex-1" onClick={() => setUnfriend(null)}>
            Cancel
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
            Remove
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
  const { openThread } = useApp()
  const [reporting, setReporting] = useState<RecentMatch | null>(null)

  if (!list) return failed ? <Failed retry={retry} /> : <ListSkeleton />
  if (list.length === 0) {
    return (
      <EmptyState
        art="chat"
        title="No matches yet"
        body="People you meet in random chat show up here, so you can message them again."
        action={<Button variant="brand" onClick={onFind}>Start a random chat</Button>}
      />
    )
  }

  return (
    <>
      <ul className="flex flex-col gap-1">
        {list.map((r, i) => (
          <motion.li
            key={r.matchId}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: Math.min(i, 10) * 0.03 }}
          >
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
              <Button size="icon" variant="secondary" onClick={() => openThread(r.partner, r.matchId)} aria-label={`Message ${r.partner.name}`}>
                <MessageCircle className="size-5" aria-hidden />
              </Button>
              <Button
                size="icon"
                variant="ghost"
                className="text-muted-foreground hover:text-destructive"
                onClick={() => setReporting(r)}
                aria-label={`Report ${r.partner.name}`}
              >
                <Flag className="size-5" aria-hidden />
              </Button>
            </Row>
          </motion.li>
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
      <h2 className="px-1 text-sm font-bold">
        {title} <span className="text-primary">· {count}</span>
      </h2>
      <div className="flex flex-col gap-1">{children}</div>
    </section>
  )
}

function Row({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <motion.div
      layout
      whileHover={{ x: 2 }}
      className={`flex items-center gap-2 rounded-3xl p-2.5 transition-colors hover:bg-muted ${className ?? ''}`}
    >
      {children}
    </motion.div>
  )
}
