'use client'

import { useState } from 'react'
import useSWR, { useSWRConfig } from 'swr'
import { Check, Flag, MessageCircle, UserPlus, X } from 'lucide-react'
import type { FriendState, Profile } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Skeleton } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { ReportSheet } from '@/components/screens/chat/report-sheet'
import { PostGrid } from '@/components/screens/me/post-grid'
import { InterestChips } from '@/components/social/bits'
import { FriendContacts } from '@/components/social/contacts'
import { Gallery } from '@/components/social/gallery'
import { ProfileHero, PromptCards } from '@/components/social/profile-bits'
import { useTrust } from '@/components/trust/badge'
import { IdentityFacts, MannerCard } from '@/components/trust/trust-cards'
import { isVip } from '@/lib/looks'

/**
 * Someone's profile: the card in their frame with their @ID, status, languages, prompts, photos and posts. The server shows people I have
 * met, and anyone who posts publicly (033), never someone blocked.
 */
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
  const { me, openThread, trust: myTrust } = useApp()
  const { t, plural } = useI18n()
  const isMe = userId === me.id
  const { data: profile, isLoading, error: loadError, mutate: reload } = useSWR(isMe ? null : ['profile', userId], () => backend.profile(userId))
  const { data: friends, mutate: refreshFriends } = useSWR('friends', () => backend.friends())
  const [busy, setBusy] = useState(false)
  const [reportOpen, setReportOpen] = useState(false)
  const shown: Profile | null | undefined = isMe ? me : profile
  const state: FriendState = friends?.find((f) => f.profile.id === userId)?.state ?? 'none'
  const theirTrust = useTrust(isMe ? null : userId)
  const trust = isMe ? myTrust : theirTrust

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
      {shown ? null : (
        <button
          type="button"
          onClick={onClose}
          className="absolute top-4 inset-e-4 z-10 grid size-9 place-items-center rounded-full text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
          aria-label={t('common.close')}
        >
          <X className="size-5" aria-hidden />
        </button>
      )}

      {shown === undefined && isLoading ? (
        <div className="flex flex-col items-center gap-3 p-8">
          <Skeleton className="size-24 rounded-full" />
          <Skeleton className="h-5 w-40" />
          <Skeleton className="h-4 w-56" />
        </div>
      ) : !shown && loadError ? (
        <div className="p-10 text-center">
          <h2 className="text-lg font-semibold">{t('profile.loadFailed')}</h2>
          <p className="mt-1 text-sm text-muted-foreground">{t('common.checkConnection')}</p>
          <Button variant="outline" className="mt-4" onClick={() => void reload()}>
            {t('common.tryAgain')}
          </Button>
        </div>
      ) : !shown ? (
        <div className="p-10 text-center">
          <h2 className="text-lg font-semibold">{t('profile.unavailable')}</h2>
          <p className="mt-1 text-sm text-muted-foreground">{t('profile.unavailableBody')}</p>
        </div>
      ) : (
        <>
          <div className="flex flex-col items-center px-3 pt-3 text-center">
            <ProfileHero
              profile={shown}
              className="w-full"
              verified={!!trust?.verified}
              // Incognito (046) hides the ID check too: no "not verified" for someone who may well be.
              facts={trust && !isVip(shown) ? <IdentityFacts profile={shown} verified={trust.verified} /> : null}
              slot={
                <button
                  type="button"
                  onClick={onClose}
                  className="absolute top-3 inset-e-3 z-10 grid size-9 place-items-center rounded-full text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
                  aria-label={t('common.close')}
                >
                  <X className="size-5" aria-hidden />
                </button>
              }
            />
            {shown.interests?.length ? (
              <InterestChips
                tags={shown.interests}
                shared={isMe ? undefined : shown.interests.filter((i) => me.interests?.includes(i))}
                className="mt-3 justify-center px-3"
              />
            ) : null}
            <p className="mt-3 text-sm text-muted-foreground">{plural(shown.posts.length, 'profile.posts.one', 'profile.posts.other')}</p>

            {isMe ? null : (
              <div className="mt-5 flex w-full max-w-sm gap-2">
                {state === 'friends' ? (
                  <Button variant="brand" className="flex-1" onClick={() => openThread(shown)}>
                    <MessageCircle className="size-4.5" aria-hidden /> {t('profile.message')}
                  </Button>
                ) : state === 'incoming' ? (
                  <Button
                    variant="brand"
                    className="flex-1"
                    loading={busy}
                    onClick={() => void act(() => backend.answerFriend(userId, true), t('recent.nowFriends', { name: shown.name }))}
                  >
                    <Check className="size-4.5" aria-hidden /> {t('profile.acceptRequest')}
                  </Button>
                ) : state === 'requested' ? (
                  <Button variant="outline" className="flex-1" loading={busy} onClick={() => void act(() => backend.removeFriend(userId))}>
                    {t('profile.withdraw')}
                  </Button>
                ) : (
                  <Button
                    variant="brand"
                    className="flex-1"
                    loading={busy}
                    onClick={() =>
                      void act(async () => {
                        const s = await backend.requestFriend(userId)
                        toast.show(s === 'friends' ? t('recent.nowFriends', { name: shown.name }) : t('profile.requestSent'), 'success')
                      })
                    }
                  >
                    <UserPlus className="size-4.5" aria-hidden /> {t('profile.add')}
                  </Button>
                )}
                <Button variant="outline" size="icon" onClick={() => setReportOpen(true)} aria-label={t('chat.report', { name: shown.name })}>
                  <Flag className="size-4.5" aria-hidden />
                </Button>
              </div>
            )}
          </div>
          {state === 'friends' ? <FriendContacts userId={userId} name={shown.name} className="mx-4 mt-6" /> : null}
          <MannerCard trust={trust} className="mx-4 mt-6" />
          {shown.style?.prompts.length ? <PromptCards prompts={shown.style.prompts} accent={shown.style.accent} className="mt-6 px-4" /> : null}
          {shown.photos?.length ? <Gallery photos={shown.photos} owner={shown.name} className="mt-6 px-4" /> : null}
          <div className="mt-6 border-t border-border px-1 pt-1 pb-4">
            <PostGrid posts={shown.posts} owner={shown.name} accent={shown.style?.accent} />
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
