'use client'

import { useState } from 'react'
import { Ban, ChevronRight } from 'lucide-react'
import type { Profile, ReportReason } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { errorCode } from '@/lib/format'
import type { TKey } from '@/lib/i18n'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Button } from '@/components/ui/pressable'
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
  const [busy, setBusy] = useState<ReportReason | 'block' | null>(null)
  const [confirmBlock, setConfirmBlock] = useState(false)

  const close = () => {
    setConfirmBlock(false)
    onClose()
  }

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
      close()
      onReported?.()
      reported(partner.id)
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(null)
    }
  }

  // Google Play asks for a block that doesn't need a report. Same effect on our side: the chat closes and they disappear.
  const block = async () => {
    setBusy('block')
    try {
      try {
        await backend.block(partner.id)
      } catch (err) {
        if (errorCode(err) !== 'not_found') throw err
      }
      toast.show(t('block.done', { name: partner.name }), 'success')
      close()
      onReported?.()
      reported(partner.id)
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(null)
    }
  }

  return (
    <Modal open={open} onClose={close} variant="sheet" title={t('report.title', { name: partner.name })} description={t('report.desc')}>
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
      <div className="border-t border-border px-3 pb-5 pt-3">
        {confirmBlock ? (
          <div className="flex flex-col gap-3 px-4 py-2" role="group" aria-label={t('block.title', { name: partner.name })}>
            <p className="text-[15px] font-semibold">{t('block.title', { name: partner.name })}</p>
            <p className="text-sm text-muted-foreground">{t('block.text')}</p>
            <div className="flex justify-end gap-2">
              <Button variant="ghost" disabled={busy !== null} onClick={() => setConfirmBlock(false)}>
                {t('common.cancel')}
              </Button>
              <Button variant="danger" loading={busy === 'block'} disabled={busy !== null} onClick={() => void block()} autoFocus>
                {t('block.action')}
              </Button>
            </div>
          </div>
        ) : (
          <button
            type="button"
            disabled={busy !== null}
            onClick={() => setConfirmBlock(true)}
            className="flex w-full items-center gap-3 rounded-xl px-4 py-3.5 text-left transition-colors duration-150 hover:bg-muted disabled:opacity-60"
          >
            <Ban className="size-4 shrink-0 text-muted-foreground" aria-hidden />
            <span className="flex min-w-0 flex-1 flex-col">
              <span className="text-[15px] font-semibold">{t('block.only')}</span>
              <span className="text-xs text-muted-foreground">{t('block.text')}</span>
            </span>
          </button>
        )}
      </div>
    </Modal>
  )
}
