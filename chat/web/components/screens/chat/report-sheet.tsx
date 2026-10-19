'use client'

import { useState } from 'react'
import { ChevronRight } from 'lucide-react'
import type { Profile, ReportReason } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { errorCode } from '@/lib/format'
import type { TKey } from '@/lib/i18n'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Modal } from '@/components/ui/modal'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'

const REASONS: { value: ReportReason; label: TKey; detail: TKey }[] = [
  { value: 'nudity', label: 'report.nudity', detail: 'report.nudityDetail' },
  { value: 'minor', label: 'report.minor', detail: 'report.minorDetail' },
  { value: 'harassment', label: 'report.harassment', detail: 'report.harassmentDetail' },
  { value: 'fake', label: 'report.fake', detail: 'report.fakeDetail' },
  { value: 'other', label: 'report.other', detail: 'report.otherDetail' },
]

export function ReportSheet({
  open,
  onClose,
  partner,
  matchId,
  onReported,
}: {
  open: boolean
  onClose: () => void
  partner: Profile
  matchId: string | null
  onReported?: () => void
}) {
  const backend = useBackend()
  const toast = useToast()
  const { t } = useI18n()
  const { reported } = useApp()
  const [busy, setBusy] = useState<ReportReason | null>(null)

  const report = async (reason: ReportReason) => {
    setBusy(reason)
    try {
      try {
        await backend.report(partner.id, reason, matchId)
      } catch (err) {
        // "not_found": the account was already deleted — there is nothing left to report, the chat is over.
        if (errorCode(err) !== 'not_found') throw err
      }
      toast.show(t('report.thanks', { name: partner.name }), 'success')
      onClose()
      onReported?.()
      reported(partner.id)
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(null)
    }
  }

  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={t('report.title', { name: partner.name })} description={t('report.desc')}>
      <ul className="flex flex-col p-3 pb-5">
        {REASONS.map((r) => (
          <li key={r.value}>
            <button
              type="button"
              disabled={busy !== null}
              onClick={() => void report(r.value)}
              className={cn(
                'flex w-full items-center gap-3 rounded-xl px-4 py-3.5 text-left transition-colors duration-150 hover:bg-muted disabled:opacity-60',
                busy === r.value && 'bg-muted',
              )}
            >
              <span className="flex min-w-0 flex-1 flex-col">
                <span className={cn('text-[15px] font-semibold', r.value === 'minor' && 'text-destructive')}>{t(r.label)}</span>
                <span className="text-xs text-muted-foreground">{t(r.detail)}</span>
              </span>
              <ChevronRight className="size-4 shrink-0 text-muted-foreground rtl:rotate-180" aria-hidden />
            </button>
          </li>
        ))}
      </ul>
    </Modal>
  )
}
