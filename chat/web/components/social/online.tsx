'use client'

import useSWR from 'swr'
import { useBackend } from '@/lib/backend-context'
import { PresenceDot } from '@/components/ui/bits'
import { cn } from '@/lib/utils'
import { useSocialT } from './social-text'

/** "N people online": the server's count (at most 10 s old), asked every 15 s while shown. */
export function OnlineCount({ active, className }: { active: boolean; className?: string }) {
  const backend = useBackend()
  const { plural } = useSocialT()
  const { data } = useSWR(active ? 'online' : null, () => backend.onlineCount(), {
    refreshInterval: 15_000, refreshWhenHidden: false, dedupingInterval: 10_000,
  })
  if (!data) return <span className={cn('h-5', className)} aria-hidden />
  return (
    <span className={cn('inline-flex h-5 items-center gap-2 text-sm font-medium text-muted-foreground', className)}>
      <PresenceDot />
      <span className="tabular-nums">{plural(data, 'online.one', 'online.other')}</span>
    </span>
  )
}
