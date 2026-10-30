'use client'

import { AnimatePresence } from 'motion/react'
import * as m from 'motion/react-m'
import { useEffect, useState } from 'react'
import useSWR from 'swr'
import { Megaphone } from 'lucide-react'
import type { MegaphoneLine } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { NameText } from '@/components/looks/look-name'
import { UserAvatar } from '@/components/looks/user-avatar'
import { TextInput } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'

// The megaphone (100), after MapleStory's: one bought line at the top of everyone's screen in my country, with the
// name and photo, a few seconds each, one after another. A tap on it opens that person's profile (to report too).

const SHOWN_MS = 7000
const MAX = 80

/** The banner on top of every screen; lines from people I blocked are left out. */
export function MegaphoneBanner() {
  const backend = useBackend()
  const { openProfile } = useApp()
  const { t } = useI18n()
  const [queue, setQueue] = useState<MegaphoneLine[]>([])
  const [composing, setComposing] = useState(false)
  // My blocks only once a line comes (most opens never see one).
  const { data: blocks } = useSWR(queue.length ? 'blocks' : null, () => backend.blocks())
  useEffect(() => backend.megaphones((l) => setQueue((q) => (q.some((x) => x.id === l.id) ? q : [...q, l].slice(-10)))), [backend])
  const shown = queue.find((l) => !blocks?.some((b) => b.id === l.user.id)) ?? null
  useEffect(() => {
    if (!shown) return
    const id = setTimeout(() => setQueue((q) => q.filter((x) => x.id !== shown.id)), SHOWN_MS)
    return () => clearTimeout(id)
  }, [shown])
  return (
    <>
      <div className="pointer-events-none fixed inset-x-0 top-2 z-[60] flex justify-center px-3" aria-live="polite">
        {/* One at a time: the next slides in after the last has gone. */}
        <AnimatePresence mode="wait">
          {shown ? (
            <m.div
              key={shown.id}
              initial={{ y: -64, opacity: 0 }}
              animate={{ y: 0, opacity: 1 }}
              exit={{ y: -64, opacity: 0 }}
              transition={{ type: 'spring', stiffness: 420, damping: 32 }}
              className="pointer-events-auto flex w-full max-w-xl items-center gap-2 rounded-2xl bg-[#ffc23d] py-1.5 ps-2 pe-1.5 text-[#3b2600] shadow-e3"
            >
              <button type="button" onClick={() => openProfile(shown.user.id)} className="flex min-w-0 flex-1 items-center gap-2 text-start">
                <Megaphone className="size-4.5 shrink-0" aria-label={t('mega.title')} />
                <UserAvatar userId={shown.user.id} name={shown.user.name} src={shown.user.photoUrl} size={28} />
                <span className="min-w-0 flex-1 text-sm leading-snug">
                  <NameText userId={shown.user.id} className="font-bold">{shown.user.name}</NameText>
                  <span aria-hidden>{' '}</span>
                  <span className="break-words" dir="auto">{shown.text}</span>
                </span>
              </button>
              <button
                type="button"
                onClick={() => setComposing(true)}
                className="shrink-0 rounded-xl bg-[#3b2600]/10 px-2.5 py-1.5 text-xs font-bold transition-colors hover:bg-[#3b2600]/20"
              >
                {t('mega.send')}
              </button>
            </m.div>
          ) : null}
        </AnimatePresence>
      </div>
      <MegaphoneSheet open={composing} onClose={() => setComposing(false)} />
    </>
  )
}

/** Writing a megaphone line: how many I have, the line (80 at most), Send; none left: buy one (demo) or the app note. */
export function MegaphoneSheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { t, num } = useI18n()
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const { data: count, mutate } = useSWR(open ? 'megaphones' : null, () => backend.megaphoneCount())
  const run = async (work: () => Promise<unknown>) => {
    setBusy(true)
    try {
      await work()
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
      void mutate()
    }
  }
  const send = () =>
    run(async () => {
      await backend.sendMegaphone(text.trim())
      toast.show(t('mega.sent'), 'success')
      setText('')
      onClose()
    })
  const has = (count ?? 0) > 0
  return (
    <Modal open={open} onClose={onClose} title={t('mega.title')} description={t('mega.desc')} className="md:max-w-md">
      <form
        className="flex flex-col gap-3 p-6 pt-4"
        onSubmit={(e) => {
          e.preventDefault()
          if (has && text.trim()) void send()
        }}
      >
        <p className="flex items-center gap-2 text-sm font-semibold">
          <span className="grid size-8 place-items-center rounded-full bg-[#ffc23d] text-[#3b2600]">
            <Megaphone className="size-4" aria-hidden />
          </span>
          {count === undefined ? '…' : t('mega.left', { n: num(count) })}
        </p>
        <div className="flex flex-col gap-1">
          <TextInput value={text} maxLength={MAX} placeholder={t('mega.placeholder')} onChange={(e) => setText(e.target.value)} aria-label={t('mega.placeholder')} />
          <span className="self-end text-xs text-muted-foreground tabular-nums">{[...text].length}/{MAX}</span>
        </div>
        {has ? (
          <Button type="submit" variant="brand" size="lg" loading={busy} disabled={!text.trim()}>
            {t('mega.send')}
          </Button>
        ) : backend.buyMegaphone ? (
          <Button variant="premium" size="lg" loading={busy} onClick={() => void run(() => backend.buyMegaphone!())}>
            {t('mega.buy')}
          </Button>
        ) : (
          <p className="text-center text-xs text-muted-foreground">{t('mega.appNote')}</p>
        )}
      </form>
    </Modal>
  )
}
