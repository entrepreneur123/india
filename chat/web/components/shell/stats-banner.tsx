'use client'

import { useEffect, useState } from 'react'
import { setStats, statsAsk } from '@/lib/telemetry'
import { useI18n } from '@/components/i18n'

/**
 * Europe only (lib/telemetry statsAsk): usage statistics wait for a yes. Two equal buttons, no pre-ticked choice;
 * the same switch is in Settings. Nothing else on the site needs consent (sign-in and preferences are strictly
 * necessary storage).
 */
export function StatsBanner() {
  const { t } = useI18n()
  const [ask, setAsk] = useState(false)
  useEffect(() => setAsk(statsAsk()), [])
  if (!ask) return null
  const answer = (on: boolean) => (setStats(on), setAsk(false))
  const btn = 'h-9 flex-1 rounded-full px-4 text-sm font-semibold'
  return (
    <div className="fixed inset-x-3 bottom-[calc(5rem+env(safe-area-inset-bottom))] z-50 mx-auto max-w-md sm:bottom-4" role="region" aria-label={t('settings.stats')}>
      <div className="rounded-[20px] bg-card p-3 text-foreground shadow-e3 ring-1 ring-border">
        <p className="text-sm font-bold">{t('settings.stats')}</p>
        <p className="mt-0.5 text-xs text-muted-foreground">{t('settings.statsBody')}</p>
        <div className="mt-2.5 flex gap-2">
          <button type="button" className={`${btn} bg-muted`} onClick={() => answer(false)}>{t('stats.no')}</button>
          <button type="button" className={`${btn} bg-muted`} onClick={() => answer(true)}>{t('stats.yes')}</button>
        </div>
      </div>
    </div>
  )
}
