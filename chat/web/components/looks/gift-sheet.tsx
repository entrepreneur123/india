'use client'

import { useState } from 'react'
import useSWR from 'swr'
import { Check, ExternalLink, Gift } from 'lucide-react'
import type { GiftCheck } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { PLAY_URL, type ItemId } from '@/lib/looks'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { UserAvatar } from './user-avatar'
import { useLooksT } from './looks-text'

/**
 * 33: a look for a friend. Each friend says whether they can get it (gift_check: not if they have it already); the
 * live web sends the gift from the Android app (no payments here), the demo gives it at once.
 */
export function GiftSheet({ item, name, price, onClose }: { item: ItemId | null; name: string; price: string; onClose: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { lt } = useLooksT()
  const { data: friends } = useSWR(item ? 'friends' : null, () => backend.friends())
  const list = friends?.filter((f) => f.state === 'friends') ?? []
  const { data: checks, mutate } = useSWR(item && list.length ? ['gift-check', item, list.map((f) => f.profile.id).join()] : null, async () =>
    Object.fromEntries(await Promise.all(list.map(async (f) => [f.profile.id, await backend.giftCheck(f.profile.id, item!)] as const))) as Record<string, GiftCheck>,
  )
  const [busy, setBusy] = useState<string | null>(null)
  const give = async (friendId: string, friendName: string) => {
    if (!item || !backend.giftLook) return
    setBusy(friendId)
    try {
      await backend.giftLook(friendId, item)
      await mutate()
      toast.show(lt('gift.sent', { item: name, name: friendName }), 'success')
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(null)
    }
  }
  return (
    <Modal open={item !== null} onClose={onClose} variant="sheet" title={lt('gift.title', { item: name })} description={lt('gift.desc', { price })}>
      <div className="flex flex-col gap-3 p-4 pt-2">
        {friends && !list.length ? <p className="px-2 py-6 text-center text-sm text-muted-foreground">{lt('gift.noFriends')}</p> : null}
        <ul className="flex max-h-[min(24rem,55dvh)] flex-col overflow-y-auto scrollbar-thin">
          {list.map((f) => {
            const check = checks?.[f.profile.id]
            return (
              <li key={f.profile.id} className="flex items-center gap-3 rounded-xl p-2">
                <UserAvatar userId={f.profile.id} name={f.profile.name} src={f.profile.photoUrl} size={40} />
                <span className="min-w-0 flex-1 truncate text-sm font-semibold">{f.profile.name}</span>
                {check === 'owned' ? (
                  <span className="flex items-center gap-1 text-xs font-medium text-muted-foreground">
                    <Check className="size-3.5" aria-hidden /> {lt('gift.has')}
                  </span>
                ) : check === 'ok' && backend.giftLook ? (
                  <Button size="sm" variant="premium" loading={busy === f.profile.id} disabled={busy !== null} onClick={() => void give(f.profile.id, f.profile.name)}>
                    <Gift className="size-4" aria-hidden /> {lt('gift.give')}
                  </Button>
                ) : check === 'ok' ? (
                  <a
                    href={PLAY_URL}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="shine-button relative inline-flex h-9 items-center gap-1.5 rounded-full px-3.5 text-sm font-bold text-white"
                  >
                    {lt('gift.inApp')} <ExternalLink className="size-3.5 rtl:-scale-x-100" aria-hidden />
                  </a>
                ) : null}
              </li>
            )
          })}
        </ul>
      </div>
    </Modal>
  )
}
