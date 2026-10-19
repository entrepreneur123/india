'use client'

import { useState } from 'react'
import useSWR, { useSWRConfig } from 'swr'
import { Check, Flag, MessageCircle, UserPlus, X } from 'lucide-react'
import type { FriendState, Profile } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { flagEmoji } from '@/lib/format'
import { useApp } from '@/components/app-state'
import { Avatar } from '@/components/ui/avatar'
import { Skeleton } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { ReportSheet } from '@/components/screens/chat/report-sheet'
import { PostGrid } from '@/components/screens/me/post-grid'

/** Someone's profile with their posts. The server only shows people you have met (and not blocked). */
export function ProfileSheet({
  target,
  onClose,
}: {
  target: { userId: string; matchId: string | null } | null
  onClose: () => void
}) {
  return (
    <Modal open={target !== null} onClose={onClose} variant="sheet" hideHeader className="md:max-w-xl">
      {target ? <ProfileBody key={target.userId} userId={target.userId} matchId={target.matchId} onClose={onClose} /> : null}
    </Modal>
  )
}

function ProfileBody({ userId, matchId, onClose }: { userId: string; matchId: string | null; onClose: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { mutate } = useSWRConfig()
  const { me, openThread } = useApp()
  const isMe = userId === me.id
  const { data: profile, isLoading } = useSWR(isMe ? null : ['profile', userId], () => backend.profile(userId))
  const { data: friends, mutate: refreshFriends } = useSWR('friends', () => backend.friends())
  const [busy, setBusy] = useState(false)
  const [reportOpen, setReportOpen] = useState(false)
  const shown: Profile | null | undefined = isMe ? me : profile
  const state: FriendState = friends?.find((f) => f.profile.id === userId)?.state ?? 'none'

  const act = async (action: () => Promise<unknown>, done?: string) => {
    setBusy(true)
    try {
      await action()
      if (done) toast.show(done, 'success')
      await refreshFriends()
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="relative">
      <button
        type="button"
        onClick={onClose}
        className="absolute top-4 right-4 z-10 grid size-9 place-items-center rounded-full text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
        aria-label="Close"
      >
        <X className="size-5" aria-hidden />
      </button>

      {shown === undefined && isLoading ? (
        <div className="flex flex-col items-center gap-3 p-8">
          <Skeleton className="size-24 rounded-full" />
          <Skeleton className="h-5 w-40" />
          <Skeleton className="h-4 w-56" />
        </div>
      ) : !shown ? (
        <div className="p-10 text-center">
          <h2 className="text-lg font-bold">Profile not available</h2>
          <p className="mt-1 text-sm text-muted-foreground">You can only see people you’ve met, and not anyone you blocked.</p>
        </div>
      ) : (
        <>
          <div className="flex flex-col items-center px-6 pt-8 text-center">
            <span className="rounded-full bg-brand p-[3px]">
              <span className="block rounded-full bg-card p-[3px]">
                <Avatar name={shown.name} src={shown.photoUrl} size={96} />
              </span>
            </span>
            <h2 className="mt-4 text-2xl font-extrabold tracking-tight">
              {shown.name}, {shown.age} <span aria-hidden>{flagEmoji(shown.country)}</span>
            </h2>
            {shown.city ? <p className="text-sm text-muted-foreground">{shown.city}</p> : null}
            {shown.bio ? <p className="mt-3 max-w-sm text-[15px] leading-relaxed text-pretty">{shown.bio}</p> : null}
            <p className="mt-3 text-sm">
              <span className="font-bold">{shown.posts.length}</span> <span className="text-muted-foreground">posts</span>
            </p>

            {isMe ? null : (
              <div className="mt-5 flex w-full max-w-sm gap-2">
                {state === 'friends' ? (
                  <Button variant="brand" className="flex-1" onClick={() => openThread(shown)}>
                    <MessageCircle className="size-4.5" aria-hidden /> Message
                  </Button>
                ) : state === 'incoming' ? (
                  <Button
                    variant="brand"
                    className="flex-1"
                    loading={busy}
                    onClick={() => void act(() => backend.answerFriend(userId, true), `You and ${shown.name} are friends`)}
                  >
                    <Check className="size-4.5" aria-hidden /> Accept request
                  </Button>
                ) : state === 'requested' ? (
                  <Button variant="secondary" className="flex-1" loading={busy} onClick={() => void act(() => backend.removeFriend(userId))}>
                    Request sent · Withdraw
                  </Button>
                ) : (
                  <Button
                    variant="brand"
                    className="flex-1"
                    loading={busy}
                    onClick={() =>
                      void act(async () => {
                        const s = await backend.requestFriend(userId)
                        toast.show(s === 'friends' ? `You and ${shown.name} are friends` : 'Friend request sent', 'success')
                      })
                    }
                  >
                    <UserPlus className="size-4.5" aria-hidden /> Add friend
                  </Button>
                )}
                <Button variant="secondary" size="icon" onClick={() => setReportOpen(true)} aria-label={`Report ${shown.name}`}>
                  <Flag className="size-4.5" aria-hidden />
                </Button>
              </div>
            )}
          </div>
          <div className="mt-6 border-t border-border">
            <PostGrid posts={shown.posts} owner={shown.name} />
          </div>
          {isMe ? null : (
            <ReportSheet
              open={reportOpen}
              onClose={() => setReportOpen(false)}
              partner={shown}
              matchId={matchId}
              onReported={() => {
                void mutate('recents')
                void refreshFriends()
                onClose()
              }}
            />
          )}
        </>
      )}
    </div>
  )
}
