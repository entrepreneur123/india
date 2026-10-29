'use client'

import { UnfriendSheet } from '@/components/social/unfriend-sheet'
import { useState, type ReactNode } from 'react'
import useSWR, { useSWRConfig } from 'swr'
import { Ban, Check, Ellipsis, Flag, Heart, X } from 'lucide-react'
import type { FriendState, Profile, Trust } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Skeleton } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { ReportSheet } from '@/components/screens/chat/report-sheet'
import { PostGrid } from '@/components/screens/me/post-grid'
import { FriendContacts } from '@/components/social/contacts'
import { ProfileHero, ProfileStat } from '@/components/social/profile-bits'
import { Highlights } from '@/components/social/highlights'
import { useTrust } from '@/components/trust/badge'
import { MannerCard } from '@/components/trust/trust-cards'
import { cn } from '@/lib/utils'

const socialKey = (userId: string) => ['social', userId]
/** Instagram's profile buttons: equal, grey, softly squared (the main one in the brand colour). */
export const ACTION = 'h-9 min-w-0 flex-1 rounded-lg px-3 font-semibold'

/**
 * Someone's profile: the card in their frame with their @ID, status, languages, photos and posts. The server shows people I have
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
  const [reportOpen, setReportOpen] = useState<'report' | 'block' | null>(null)
  const [unfriendOpen, setUnfriendOpen] = useState(false)
  const [moreOpen, setMoreOpen] = useState(false)
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
            slot={
              <button
                type="button"
                onClick={onClose}
                className="-me-2 grid size-9 shrink-0 place-items-center rounded-full text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
                aria-label={t('common.close')}
              >
                <X className="size-5" aria-hidden />
              </button>
            }
            actions={
              isMe ? null : (
                <>
                  {state === 'friends' ? (
                    <Button className={ACTION} onClick={() => setUnfriendOpen(true)}>
                      <Check className="size-4" aria-hidden /> {t('profile.friends')}
                    </Button>
                  ) : state === 'incoming' ? (
                    <Button
                      variant="brand"
                      className={ACTION}
                      loading={busy}
                      onClick={() => void act(() => backend.answerFriend(userId, true), t('recent.nowFriends', { name: shown.name }))}
                    >
                      {t('profile.acceptRequest')}
                    </Button>
                  ) : state === 'requested' ? (
                    <Button className={ACTION} loading={busy} title={t('profile.withdraw')} onClick={() => void act(() => backend.removeFriend(userId))}>
                      {t('profile.requested')}
                    </Button>
                  ) : (
                    <Button
                      variant="brand"
                      className={ACTION}
                      loading={busy}
                      onClick={() =>
                        void act(async () => {
                          const s = await backend.requestFriend(userId)
                          toast.show(s === 'friends' ? t('recent.nowFriends', { name: shown.name }) : t('profile.requestSent'), 'success')
                        })
                      }
                    >
                      {t('profile.add')}
                    </Button>
                  )}
                  {state === 'friends' || social.data?.canMessage ? (
                    <Button className={ACTION} onClick={() => openThread(shown)}>
                      {t('profile.message')}
                    </Button>
                  ) : null}
                  <Button
                    size="icon-sm"
                    className={cn('rounded-lg', social.data?.liked && 'text-[#ff3040]')}
                    disabled={!social.data}
                    onClick={() => void like()}
                    aria-label={social.data?.liked ? t('profile.liked') : t('profile.like')}
                    aria-pressed={!!social.data?.liked}
                  >
                    <Heart className={cn('size-4.5', social.data?.liked && 'fill-current')} aria-hidden />
                  </Button>
                  <Button size="icon-sm" className="rounded-lg" onClick={() => setMoreOpen(true)} aria-label={t('voice.more')} title={t('voice.more')}>
                    <Ellipsis className="size-4.5" aria-hidden />
                  </Button>
                </>
              )
            }
          >
            {state === 'friends' ? <FriendContacts userId={userId} name={shown.name} className="mx-4 mt-6" /> : null}
          </ProfileContent>
          {isMe ? null : <UnfriendSheet person={unfriendOpen ? shown : null} onClose={() => setUnfriendOpen(false)} />}
          {/* Instagram's ⋯: the rarer things. A friend is removed first (the Friends button), then asked about blocking (8). */}
          <Modal open={moreOpen} onClose={() => setMoreOpen(false)} variant="sheet" title={shown.name} className="md:max-w-sm">
            <div className="flex flex-col p-2 pb-4">
              {state === 'friends' ? null : (
                <MoreItem icon={<Ban />} label={t('block.only')} onClick={() => (setMoreOpen(false), setReportOpen('block'))} />
              )}
              <MoreItem icon={<Flag />} label={t('chat.report', { name: shown.name })} danger onClick={() => (setMoreOpen(false), setReportOpen('report'))} />
            </div>
          </Modal>
          {isMe ? null : (
            <ReportSheet
              open={reportOpen !== null}
              blockOnly={reportOpen === 'block'}
              onClose={() => setReportOpen(null)}
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
 * A profile as everyone sees it, mine included (the Me tab adds its own buttons), laid out as Instagram's: the
 * header with likes and friends counted beside the photo, `actions` (a row of buttons), the highlights, `children`,
 * the manners card and the posts grid. Empty parts are left out.
 */
export function ProfileContent({
  profile,
  trust,
  slot,
  badge,
  actions,
  emptyPosts,
  children,
}: {
  profile: Profile
  trust: Trust | null
  slot: ReactNode
  badge?: ReactNode
  actions?: ReactNode
  /** The empty posts grid's button (mine: "Share your first post"). */
  emptyPosts?: ReactNode
  children?: ReactNode
}) {
  const { t, num } = useI18n()
  const backend = useBackend()
  const { data: social } = useSWR(socialKey(profile.id), () => backend.profileSocial(profile.id))
  return (
    <>
      <ProfileHero
        profile={profile}
        verified={!!trust?.verified}
        badge={badge}
        slot={slot}
        stats={
          <>
            <ProfileStat n={social ? num(social.likes) : '–'} label={t('profile.likes')} />
            <ProfileStat n={social ? num(social.friends) : '–'} label={t('profile.friends')} />
          </>
        }
      />
      {actions ? <div className="mt-4 flex gap-2 px-4">{actions}</div> : null}
      <Highlights userId={profile.id} owner={profile.name} className="mt-5" />
      {children}
      <MannerCard trust={trust} className="mx-4 mt-6" />
      <div className="mt-5 border-t border-border pt-0.5 pb-4">
        <PostGrid posts={profile.posts} owner={profile.name} accent={profile.style?.accent} emptyAction={emptyPosts} />
      </div>
    </>
  )
}

function MoreItem({ icon, label, danger, onClick }: { icon: ReactNode; label: string; danger?: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'flex min-h-12 items-center gap-3 rounded-xl px-4 text-start text-[15px] font-semibold transition-colors hover:bg-muted [&>svg]:size-5',
        danger && 'text-destructive',
      )}
    >
      {icon}
      {label}
    </button>
  )
}
