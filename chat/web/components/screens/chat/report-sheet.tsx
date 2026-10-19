'use client'

import { useState } from 'react'
import { ChevronRight } from 'lucide-react'
import type { Profile, ReportReason } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { errorCode } from '@/lib/format'
import { useApp } from '@/components/app-state'
import { Modal } from '@/components/ui/modal'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'

const REASONS: { value: ReportReason; label: string; detail: string }[] = [
  { value: 'nudity', label: 'Nudity or sexual content', detail: 'Explicit photos, video or requests' },
  { value: 'minor', label: 'Looks under 18', detail: 'We act on these first' },
  { value: 'harassment', label: 'Harassment or hate', detail: 'Abuse, threats, slurs' },
  { value: 'fake', label: 'Fake profile or scam', detail: 'Impersonation, spam, money requests' },
  { value: 'other', label: 'Something else', detail: 'Anything that felt wrong' },
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
      toast.show(`Thanks. We’ll review ${partner.name}’s account.`, 'success')
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
    <Modal
      open={open}
      onClose={onClose}
      variant="sheet"
      title={`Report ${partner.name}`}
      description="Reports are anonymous. They won’t know it was you."
    >
      <ul className="flex flex-col gap-1 p-3 pb-5">
        {REASONS.map((r) => (
          <li key={r.value}>
            <button
              type="button"
              disabled={busy !== null}
              onClick={() => void report(r.value)}
              className={cn(
                'flex w-full items-center gap-3 rounded-2xl px-4 py-3.5 text-left transition-colors hover:bg-muted disabled:opacity-60',
                busy === r.value && 'bg-muted',
              )}
            >
              <span className="flex min-w-0 flex-1 flex-col">
                <span className={cn('text-[15px] font-semibold', r.value === 'minor' && 'text-destructive')}>{r.label}</span>
                <span className="text-xs text-muted-foreground">{r.detail}</span>
              </span>
              <ChevronRight className="size-4 text-muted-foreground" aria-hidden />
            </button>
          </li>
        ))}
      </ul>
    </Modal>
  )
}
