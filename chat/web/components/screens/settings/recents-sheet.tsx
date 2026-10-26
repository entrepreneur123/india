'use client'

import { useState } from 'react'
import useSWR from 'swr'
import { Check } from 'lucide-react'
import { useBackend } from '@/lib/backend-context'
import { useI18n } from '@/components/i18n'
import { Skeleton } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'
import { UserAvatar } from '@/components/looks/user-avatar'

/** Settings › Recent chats: takes people off my recents (048), some or all. They come back only if we meet again. */
export function RecentsSheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { t, timeAgo } = useI18n()
  const list = useSWR(open ? 'recents' : null, () => backend.recents())
  const [picked, setPicked] = useState<ReadonlySet<string>>(new Set())
  const [confirm, setConfirm] = useState<string[] | null>(null)
  const [busy, setBusy] = useState(false)
  const people = list.data ?? []

  const close = () => {
    setPicked(new Set())
    onClose()
  }
  const toggle = (id: string) =>
    setPicked((s) => {
      const next = new Set(s)
      if (!next.delete(id)) next.add(id)
      return next
    })

  const hide = async (ids: string[]) => {
    setBusy(true)
    try {
      await backend.hideRecents([...new Set(ids)]) // one row per person (the server refuses a repeat)
      await list.mutate((l) => l?.filter((r) => !ids.includes(r.partner.id)))
      setPicked(new Set())
      setConfirm(null)
      toast.show(t('settings.recentsDeleted'), 'success')
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <Modal open={open} onClose={close} variant="sheet" title={t('settings.recents')} description={t('settings.recentsNote')}>
        <div className="max-h-[60dvh] overflow-y-auto px-3 scrollbar-thin">
          {list.error && !list.data ? (
            <p className="px-3 py-4 text-sm text-destructive">{t('recent.loadFailed')}</p>
          ) : !list.data ? (
            <Skeleton className="h-14 w-full rounded-xl" />
          ) : people.length === 0 ? (
            <p className="px-3 py-6 text-center text-sm text-muted-foreground">{t('settings.recentsEmpty')}</p>
          ) : (
            <ul className="flex flex-col">
              {people.map((r) => {
                const on = picked.has(r.partner.id)
                return (
                  <li key={r.matchId}>
                    <button
                      type="button"
                      role="checkbox"
                      aria-checked={on}
                      onClick={() => toggle(r.partner.id)}
                      className={cn('flex w-full items-center gap-3 rounded-xl p-2.5 text-start transition-colors duration-150 hover:bg-muted', on && 'bg-primary-soft hover:bg-primary-soft')}
                    >
                      <UserAvatar userId={r.partner.id} name={r.partner.name} src={r.partner.photoUrl} size={44} />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-[15px] font-semibold">{r.partner.name}</span>
                        <span className="block truncate text-sm text-muted-foreground">{timeAgo(r.metAt)}</span>
                      </span>
                      <span
                        aria-hidden
                        className={cn('grid size-6 shrink-0 place-items-center rounded-full border-2', on ? 'border-primary bg-primary text-primary-foreground' : 'border-input')}
                      >
                        {on ? <Check className="size-4" strokeWidth={3} /> : null}
                      </span>
                    </button>
                  </li>
                )
              })}
            </ul>
          )}
        </div>
        <div className="flex gap-3 p-6 pt-4">
          <Button variant="outline" className="flex-1" disabled={!people.length || busy} onClick={() => setConfirm(people.map((r) => r.partner.id))}>
            {t('settings.recentsDeleteAll')}
          </Button>
          <Button variant="danger" className="flex-1" disabled={!picked.size || busy} onClick={() => setConfirm([...picked])}>
            {t('settings.recentsDelete', { n: picked.size })}
          </Button>
        </div>
      </Modal>

      <Modal open={confirm !== null} onClose={() => !busy && setConfirm(null)} title={t('settings.recentsConfirm')} description={t('settings.recentsConfirmBody')}>
        <div className="flex gap-3 p-6 pt-5">
          <Button variant="outline" className="flex-1" disabled={busy} onClick={() => setConfirm(null)}>
            {t('common.cancel')}
          </Button>
          <Button variant="danger" className="flex-1" loading={busy} onClick={() => confirm && void hide(confirm)}>
            {t('settings.recentsDeleteConfirm')}
          </Button>
        </div>
      </Modal>
    </>
  )
}
