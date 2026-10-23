'use client'

import { useEffect, useState } from 'react'
import { WifiOff } from 'lucide-react'
import { useI18n } from '@/components/i18n'

/**
 * A notice near the bottom while the device has no network, above the composer and tabs so it never covers a
 * chat's exit or report buttons; chats and searches pick up again on their own.
 */
export function OfflineBanner() {
  const { t } = useI18n()
  const [offline, setOffline] = useState(false)
  useEffect(() => {
    const update = () => setOffline(!navigator.onLine)
    update()
    window.addEventListener('online', update)
    window.addEventListener('offline', update)
    return () => {
      window.removeEventListener('online', update)
      window.removeEventListener('offline', update)
    }
  }, [])
  if (!offline) return null
  return (
    <div
      role="status"
      className="pointer-events-none fixed inset-x-0 bottom-[calc(5.5rem+env(safe-area-inset-bottom))] z-[60] flex justify-center px-4 md:bottom-6"
    >
      <p className="flex items-center gap-2 rounded-full bg-[#262626] px-4 py-2 text-center text-xs font-semibold text-white shadow-e2">
        <WifiOff className="size-3.5 shrink-0" aria-hidden />
        {t('net.offline')}
      </p>
    </div>
  )
}
