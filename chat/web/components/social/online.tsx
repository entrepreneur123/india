'use client'

import useSWR from 'swr'
import { useBackend } from '@/lib/backend-context'
import { crowdOf } from '@/lib/format'
import { cn } from '@/lib/utils'
import { useSocialT } from './social-text'

/** 43: how busy the app is (lib/format crowdOf), never the head count. The server's count (at most 10 s old), every 15 s. */
const DOT = { calm: 'bg-online', normal: 'bg-amber-500', busy: 'bg-destructive' } as const

export function OnlineCount({ active, className }: { active: boolean; className?: string }) {
  const backend = useBackend()
  const { st } = useSocialT()
  const { data } = useSWR(active ? 'online' : null, () => backend.onlineCount(), {
    refreshInterval: 15_000, refreshWhenHidden: false, dedupingInterval: 10_000,
  })
  if (data === undefined) return <span className={cn('h-5', className)} aria-hidden />
  const crowd = crowdOf(data)
  return (
    <span className={cn('inline-flex h-5 items-center gap-2 text-sm font-medium text-muted-foreground', className)}>
      <span className={cn('size-2 rounded-full', DOT[crowd])} aria-hidden />
      {st(`crowd.${crowd}`)}
    </span>
  )
}
