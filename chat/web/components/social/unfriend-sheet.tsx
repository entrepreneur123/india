'use client'

import { useEffect, useState } from 'react'
import { useSWRConfig } from 'swr'
import { Ban } from 'lucide-react'
import { useBackend } from '@/lib/backend-context'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'

/**
 * 8 (the owner): removing a friend asks first, then whether to block them too — a friend is never blocked in one tap,
 * and someone who isn't a friend is blocked from their profile (block_user, 084: 10 a day).
 */
export function UnfriendSheet({ person, onClose, onDone }: { person: { id: string; name: string } | null; onClose: () => void; onDone?: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { t } = useI18n()
  const { reported } = useApp()
  // The shell's own cache (AppShell gives each signed-in session one), not swr's global one.
  const { mutate } = useSWRConfig()
  const [step, setStep] = useState<'confirm' | 'block'>('confirm')
  const [busy, setBusy] = useState(false)
  useEffect(() => {
    if (person) setStep('confirm')
  }, [person])
  const run = async (action: () => Promise<void>) => {
    setBusy(true)
    try {
      await action()
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }
  const remove = () =>
    run(async () => {
      await backend.removeFriend(person!.id)
      void mutate('friends')
      onDone?.()
      setStep('block')
    })
  const block = () =>
    run(async () => {
      await backend.block(person!.id)
      toast.show(t('block.done', { name: person!.name }), 'success')
      reported(person!.id) // every open chat or view with them closes
      void mutate('recents')
      onClose()
    })
  const asking = step === 'block'
  return (
    <Modal
      open={person !== null}
      onClose={onClose}
      title={person ? (asking ? t('unfriend.blockTitle', { name: person.name }) : t('recent.removeTitle', { name: person.name })) : ''}
      description={asking ? t('block.text') : t('recent.removeBody')}
    >
      <div className="flex gap-3 p-6 pt-5">
        <Button variant="outline" className="flex-1" disabled={busy} onClick={onClose}>
          {asking ? t('unfriend.keep') : t('common.cancel')}
        </Button>
        <Button variant="danger" className="flex-1" loading={busy} onClick={() => void (asking ? block() : remove())}>
          {asking ? (
            <>
              <Ban className="size-4" aria-hidden /> {t('block.action')}
            </>
          ) : (
            t('recent.remove')
          )}
        </Button>
      </div>
    </Modal>
  )
}
