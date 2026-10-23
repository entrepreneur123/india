'use client'

import { Component, type ReactNode } from 'react'
import { WifiOff } from 'lucide-react'
import { useI18n } from '@/components/i18n'
import { Button } from '@/components/ui/pressable'
import { reportError } from '@/lib/telemetry'

/**
 * Catches a screen that failed to load (its download broke off on a weak network) or crashed, and offers a
 * reload, instead of the whole page going blank.
 */
export class ScreenBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }

  static getDerivedStateFromError() {
    return { failed: true }
  }

  componentDidCatch(error: unknown) {
    console.error(error)
    reportError(error, 'screen')
  }

  render() {
    return this.state.failed ? <ScreenError /> : this.props.children
  }
}

function ScreenError() {
  const { t } = useI18n()
  return (
    <div className="grid min-h-0 flex-1 place-items-center px-6 py-16 text-center" role="alert">
      <div className="flex flex-col items-center gap-3">
        <span className="grid size-12 place-items-center rounded-full bg-muted text-muted-foreground">
          <WifiOff className="size-5" aria-hidden />
        </span>
        <h2 className="text-lg font-bold">{t('error.screenTitle')}</h2>
        <p className="max-w-xs text-sm text-muted-foreground">{t('common.checkConnection')}</p>
        <Button variant="brand" className="mt-2" onClick={() => window.location.reload()}>
          {t('common.tryAgain')}
        </Button>
      </div>
    </div>
  )
}

/** Shown while a screen's code downloads (on a slow network the first visit to a tab can take a moment). */
export function ScreenLoading() {
  return (
    <div className="grid min-h-0 flex-1 place-items-center" aria-busy="true">
      <span className="size-7 animate-spin rounded-full border-[3px] border-muted border-t-primary" />
    </div>
  )
}
