'use client'

import { useEffect, useState } from 'react'
import useSWR from 'swr'
import { Users } from 'lucide-react'
import { useBackend } from '@/lib/backend-context'
import { useI18n } from '@/components/i18n'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'

/** My gender pass (26): until when I may choose women or men; asks again the moment it runs out. */
export function useGenderPass() {
  const backend = useBackend()
  const { data: until = null, mutate } = useSWR('gender-pass', () => backend.genderPass())
  useEffect(() => {
    if (!until) return
    const timer = setTimeout(() => void mutate(), Math.min(until - Date.now() + 500, 2 ** 31 - 1))
    return () => clearTimeout(timer)
  }, [until, mutate])
  return { until, mutate }
}

/** Choosing women or men is paid, like Azar's filter: a 24-hour pass (bought in the Android app; the demo grants it). */
export function GenderPassSheet({ open, onClose, onGot }: { open: boolean; onClose: () => void; onGot: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { t } = useI18n()
  const [busy, setBusy] = useState(false)
  const buy = async () => {
    setBusy(true)
    try {
      await backend.buyGenderPass!()
      toast.show(t('pass.got'), 'success')
      onGot()
      onClose()
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }
  return (
    <Modal open={open} onClose={onClose} title={t('pass.title')} className="max-w-sm">
      <div className="flex flex-col items-center gap-3 p-6 pt-2 text-center">
        <span className="grid size-14 place-items-center rounded-2xl bg-primary-soft text-primary-text" aria-hidden>
          <Users className="size-7" />
        </span>
        <p className="text-[15px] leading-relaxed text-muted-foreground">{t('pass.body')}</p>
        {backend.buyGenderPass ? (
          <Button variant="brand" size="lg" className="mt-2 w-full" onClick={() => void buy()} loading={busy}>
            {t('pass.get')}
          </Button>
        ) : (
          <p className="text-xs leading-relaxed text-muted-foreground">{t('pass.app')}</p>
        )}
      </div>
    </Modal>
  )
}
