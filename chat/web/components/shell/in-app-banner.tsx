'use client'

import { useEffect, useState } from 'react'
import { ExternalLink, Link2, X } from 'lucide-react'
import { externalLink, inAppBrowser, type InAppBrowser } from '@/lib/in-app-browser'
import { useI18n } from '@/components/i18n'
import { useToast } from '@/components/ui/toast'

const SEEN = 'yaari_inapp_ok'

/**
 * Shown on top when Yaari was opened inside another app's browser (a KakaoTalk or Instagram link): voice and calls
 * need the real browser there. One tap leaves where the app allows it; otherwise the link can be copied.
 */
export function InAppBanner() {
  const { t } = useI18n()
  const toast = useToast()
  const [kind, setKind] = useState<InAppBrowser | null>(null)

  useEffect(() => {
    let closed = false
    try {
      closed = sessionStorage.getItem(SEEN) === '1'
    } catch {
      /* no storage: show it */
    }
    if (!closed) setKind(inAppBrowser(navigator.userAgent))
  }, [])

  if (!kind) return null
  const link = externalLink(kind, location.href, navigator.userAgent)
  const close = () => {
    try {
      sessionStorage.setItem(SEEN, '1')
    } catch {
      /* fine */
    }
    setKind(null)
  }
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(location.href)
      toast.show(t('inapp.copied'), 'success')
    } catch {
      toast.show(location.href)
    }
  }

  return (
    <div className="fixed inset-x-3 top-[calc(0.75rem+env(safe-area-inset-top))] z-50 mx-auto max-w-md" role="alert">
      <div className="flex items-start gap-3 rounded-[20px] bg-card p-3 text-foreground shadow-e3 ring-1 ring-primary-text/30">
        <div className="min-w-0 flex-1">
          <p className="text-sm font-bold">{t('inapp.title')}</p>
          <p className="mt-0.5 text-xs text-muted-foreground">{t(link ? 'inapp.body' : 'inapp.bodyMenu')}</p>
          <div className="mt-2.5 flex flex-wrap gap-2">
            {link ? (
              <a href={link} className="inline-flex h-9 items-center gap-1.5 rounded-full bg-primary px-4 text-sm font-semibold text-primary-foreground">
                <ExternalLink className="size-4" aria-hidden />
                {t('inapp.open')}
              </a>
            ) : null}
            <button type="button" onClick={() => void copy()} className="inline-flex h-9 items-center gap-1.5 rounded-full bg-muted px-4 text-sm font-semibold">
              <Link2 className="size-4" aria-hidden />
              {t('inapp.copy')}
            </button>
          </div>
        </div>
        <button type="button" onClick={close} className="grid size-8 shrink-0 place-items-center rounded-full text-muted-foreground" aria-label={t('common.close')}>
          <X className="size-4" aria-hidden />
        </button>
      </div>
    </div>
  )
}
