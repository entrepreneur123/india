'use client'

import { useEffect, useState } from 'react'
import useSWR from 'swr'
import { Clock, Users } from 'lucide-react'
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
      <div className="flex flex-col items-center gap-4 p-6 pt-2 text-center">
        {/* 42: the shop's dark stage — the two choices it opens, and the 24 hours. */}
        <div className="premium-stage flex w-full flex-col items-center gap-3 rounded-[28px] px-4 pt-6 pb-5 text-white">
          <span className="grid size-16 place-items-center rounded-2xl bg-linear-to-br from-[#ff8a1f] to-[#c2410c] shadow-[0_10px_30px_-8px_rgb(234_106_0/0.8)]" aria-hidden>
            <Users className="size-8" />
          </span>
          <span className="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1 text-xs font-bold tracking-wide ring-1 ring-white/15">
            <Clock className="size-3.5" aria-hidden /> 24h
          </span>
          <p className="text-[15px] leading-relaxed font-medium text-white/85">{t('pass.body')}</p>
        </div>
        {backend.buyGenderPass ? (
          <Button variant="premium" size="lg" className="w-full" onClick={() => void buy()} loading={busy}>
            {t('pass.get')}
          </Button>
        ) : (
          <p className="text-xs leading-relaxed text-muted-foreground">{t('pass.app')}</p>
        )}
      </div>
    </Modal>
  )
}
