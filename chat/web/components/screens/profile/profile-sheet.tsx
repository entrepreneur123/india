'use client'

import { useState, type ReactNode } from 'react'
import useSWR, { useSWRConfig } from 'swr'
import { Check, Flag, Heart, MessageCircle, UserPlus, X } from 'lucide-react'
import type { FriendState, Interest, Profile, Trust } from '@/lib/backend'
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
import { Highlights } from '@/components/social/highlights'
import { useTrust } from '@/components/trust/badge'
import { IdentityFacts, MannerCard } from '@/components/trust/trust-cards'
import { isVip } from '@/lib/looks'
import { cn } from '@/lib/utils'

const socialKey = (userId: string) => ['social', userId]

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
  const { t } = useI18n()
  const isMe = userId === me.id
  const { data: profile, isLoading, error: loadError, mutate: reload } = useSWR(isMe ? null : ['profile', userId], () => backend.profile(userId))
  const { data: friends, mutate: refreshFriends } = useSWR('friends', () => backend.friends())
  const [busy, setBusy] = useState(false)
  const [reportOpen, setReportOpen] = useState(false)
  const social = useSWR(isMe ? null : socialKey(userId), () => backend.profileSocial(userId))
  const like = async () => {
    const was = social.data
    if (!was) return
    // Shown at once, then as the server has it.
    await social.mutate({ ...was, liked: !was.liked, likes: was.likes + (was.liked ? -1 : 1) }, { revalidate: false })
    try {
      await backend.likeProfile(userId, !was.liked)
    } catch (err) {
      toast.error(err)
    }
    void social.mutate()
  }
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
          <ProfileContent
            profile={shown}
            trust={trust}
            shared={isMe ? undefined : shown.interests?.filter((i) => me.interests?.includes(i))}
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
            actions={
              isMe ? null : (
                <>
                  {state !== 'friends' && social.data?.canMessage ? (
                    <Button variant="outline" size="icon" onClick={() => openThread(shown)} aria-label={t('profile.message')}>
                      <MessageCircle className="size-4.5" aria-hidden />
                    </Button>
                  ) : null}
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
                  <Button
                    variant={social.data?.liked ? 'brand' : 'outline'}
                    size="icon"
                    disabled={!social.data}
                    onClick={() => void like()}
                    aria-label={social.data?.liked ? t('profile.liked') : t('profile.like')}
                    aria-pressed={!!social.data?.liked}
                  >
                    <Heart className={cn('size-4.5', social.data?.liked && 'fill-current')} aria-hidden />
                  </Button>
                  <Button variant="outline" size="icon" onClick={() => setReportOpen(true)} aria-label={t('chat.report', { name: shown.name })}>
                    <Flag className="size-4.5" aria-hidden />
                  </Button>
                </>
              )
            }
          >
            {state === 'friends' ? <FriendContacts userId={userId} name={shown.name} className="mx-4 mt-6" /> : null}
          </ProfileContent>
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

/**
 * A profile as everyone sees it, mine included (the Me tab adds its own buttons): the card in its frame, interests,
 * the posts count, `actions` (a row under it), `children` (after the row), the manners card, prompts, photos and
 * posts. Empty parts are left out.
 */
export function ProfileContent({
  profile,
  trust,
  slot,
  badge,
  shared,
  actions,
  emptyPosts,
  children,
}: {
  profile: Profile
  trust: Trust | null
  slot: ReactNode
  badge?: ReactNode
  /** Interests we both have (marked). */
  shared?: Interest[]
  actions?: ReactNode
  /** The empty posts grid's button (mine: "Share your first post"). */
  emptyPosts?: ReactNode
  children?: ReactNode
}) {
  const { plural, t, num } = useI18n()
  const backend = useBackend()
  const { data: social } = useSWR(socialKey(profile.id), () => backend.profileSocial(profile.id))
  return (
    <>
      <div className="flex flex-col items-center px-3 pt-3 text-center">
        <ProfileHero
          profile={profile}
          className="w-full"
          verified={!!trust?.verified}
          // Incognito (046) hides the ID check too: no "not verified" for someone who may well be.
          facts={trust && !isVip(profile) ? <IdentityFacts profile={profile} verified={trust.verified} /> : null}
          badge={badge}
          slot={slot}
        />
        {profile.interests?.length ? <InterestChips tags={profile.interests} shared={shared} className="mt-3 justify-center px-3" /> : null}
        <p className="mt-3 text-sm text-muted-foreground">
          {plural(profile.posts.length, 'profile.posts.one', 'profile.posts.other')}
          {social ? ` · ${t('profile.likes')} ${num(social.likes)} · ${t('profile.friends')} ${num(social.friends)}` : null}
        </p>
        {actions ? <div className="mt-5 flex w-full max-w-sm gap-2">{actions}</div> : null}
      </div>
      <Highlights userId={profile.id} owner={profile.name} className="mt-6" />
      {children}
      <MannerCard trust={trust} className="mx-4 mt-6" />
      {profile.style?.prompts.length ? <PromptCards prompts={profile.style.prompts} accent={profile.style.accent} className="mt-6 px-4" /> : null}
      {profile.photos?.length ? <Gallery photos={profile.photos} owner={profile.name} className="mt-6 px-4" /> : null}
      <div className="mt-6 border-t border-border px-1 pt-1 pb-4">
        <PostGrid posts={profile.posts} owner={profile.name} accent={profile.style?.accent} emptyAction={emptyPosts} />
      </div>
    </>
  )
}
