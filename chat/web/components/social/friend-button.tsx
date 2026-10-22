'use client'

import { useState } from 'react'
import useSWR from 'swr'
import { Check, Clock, UserCheck, UserPlus } from 'lucide-react'
import type { FriendState } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { useI18n } from '@/components/i18n'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { useLooksT } from '@/components/looks/looks-text'
import { cn } from '@/lib/utils'

/**
 * My friendship with someone, read from the friends list every screen shares (SWR 'friends'), and the actions on it:
 * ask, accept, withdraw. Each action reads the list again, so every screen showing them follows.
 */
export function useFriendship(userId: string, name: string) {
  const backend = useBackend()
  const toast = useToast()
  const { t } = useI18n()
  const { data, mutate } = useSWR('friends', () => backend.friends())
  const [busy, setBusy] = useState(false)
  const state: FriendState | null = data ? (data.find((f) => f.profile.id === userId)?.state ?? 'none') : null

  const run = async (action: () => Promise<string | null>) => {
    setBusy(true)
    try {
      const done = await action()
      if (done) toast.show(done, 'success')
      await mutate()
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }
  return {
    /** Null until the list has loaded. */
    state,
    busy,
    /** Re-reads the list (a screen coming back, the other side may have answered meanwhile). */
    refresh: () => void mutate(),
    add: () =>
      run(async () => ((await backend.requestFriend(userId)) === 'friends' ? t('recent.nowFriends', { name }) : t('profile.requestSent'))),
    accept: () =>
      run(async () => {
        await backend.answerFriend(userId, true)
        return t('recent.nowFriends', { name })
      }),
    withdraw: () =>
      run(async () => {
        await backend.removeFriend(userId)
        return null
      }),
  }
}

/** A small friend button with the real state: Add friend, Requested (tap to withdraw), Accept, or Friends. */
export function FriendButton({ userId, name, className }: { userId: string; name: string; className?: string }) {
  const { t } = useI18n()
  const { lt } = useLooksT()
  const f = useFriendship(userId, name)
  if (f.state === null) return <span className={cn('h-9 w-24 shrink-0', className)} aria-hidden />
  if (f.state === 'friends')
    return (
      <span className={cn('inline-flex h-9 shrink-0 items-center gap-1.5 px-2 text-sm font-semibold text-muted-foreground', className)}>
        <UserCheck className="size-4" aria-hidden /> {t('recent.friends')}
      </span>
    )
  if (f.state === 'requested')
    return (
      <Button size="sm" variant="secondary" loading={f.busy} onClick={() => void f.withdraw()} aria-label={lt('friend.withdraw', { name })} className={cn('shrink-0', className)}>
        <Clock className="size-4" aria-hidden /> {lt('friend.requested')}
      </Button>
    )
  return f.state === 'incoming' ? (
    <Button size="sm" variant="brand" loading={f.busy} onClick={() => void f.accept()} aria-label={`${t('recent.accept')}: ${name}`} className={cn('shrink-0', className)}>
      <Check className="size-4" aria-hidden /> {t('recent.accept')}
    </Button>
  ) : (
    <Button size="sm" variant="secondary" loading={f.busy} onClick={() => void f.add()} aria-label={`${t('profile.add')}: ${name}`} className={cn('shrink-0 text-primary-text', className)}>
      <UserPlus className="size-4" aria-hidden /> {t('profile.add')}
    </Button>
  )
}
