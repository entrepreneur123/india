'use client'

import { useState } from 'react'
import useSWR, { useSWRConfig } from 'swr'
import type { Shop } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { useToast } from '@/components/ui/toast'
import { useLooksT } from './looks-text'

/**
 * My shop() (what I own, my wall, incognito), shared under one SWR key by the shop, the chats (wall) and
 * Settings. Outside the shop it is not asked again on every chat: `fresh` (the shop itself) always asks.
 */
export function useShop(fresh = false) {
  const backend = useBackend()
  return useSWR('shop', () => backend.shop(), fresh ? undefined : { revalidateIfStale: false, revalidateOnFocus: false })
}

/** VIP incognito on or off (set_anon), with the shop's copy updated and a word about it. */
export function useAnonSwitch() {
  const backend = useBackend()
  const toast = useToast()
  const { lt } = useLooksT()
  const { mutate } = useSWRConfig()
  const [busy, setBusy] = useState(false)
  const set = async (on: boolean) => {
    setBusy(true)
    try {
      await backend.setAnon(on)
      await mutate<Shop>('shop', (s) => (s ? { ...s, anon: on } : s), { revalidate: false })
      toast.show(lt(on ? 'vip.on' : 'vip.off'), 'success')
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }
  return { busy, set }
}
