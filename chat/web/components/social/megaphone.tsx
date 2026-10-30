'use client'

import { AnimatePresence } from 'motion/react'
import * as m from 'motion/react-m'
import { useEffect, useState } from 'react'
import useSWR from 'swr'
import { ChevronRight, Megaphone } from 'lucide-react'
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
import { cn } from '@/lib/utils'

// The megaphone (100), after MapleStory's (the owner, 2026-10-30): a bar above every tab, like one line of a chat,
// always showing the newest line with its time; a new one slides in from below. A tap opens the past day's lines (101),
// where a name opens that person's profile: someone who agrees with a line gets in touch from there.

const MAX = 80

/** The bar (hidden while a chat, a room or a random chat fills the screen), the list it opens and the writing sheet. */
export function MegaphoneBar({ shown }: { shown: boolean }) {
  const backend = useBackend()
  const { t, timeAgo } = useI18n()
  const [listOpen, setListOpen] = useState(false)
  const [composing, setComposing] = useState(false)
  const { data: history, mutate } = useSWR('megaphone-lines', () => backend.megaphoneHistory())
  const { data: blocks } = useSWR(history?.length ? 'blocks' : null, () => backend.blocks())
  useEffect(
    () => backend.megaphones((l) => void mutate((h) => (h?.some((x) => x.id === l.id) ? h : [l, ...(h ?? [])].slice(0, 50)), { revalidate: false })),
    [backend, mutate],
  )
  // "3 min ago" moves on by itself.
  const [, tick] = useState(0)
  useEffect(() => {
    const id = setInterval(() => tick((n) => n + 1), 30_000)
    return () => clearInterval(id)
  }, [])
  const lines = (history ?? []).filter((l) => !blocks?.some((b) => b.id === l.user.id))
  const l = lines[0]
  return (
    <>
      <div className={cn('absolute inset-x-0 top-0 z-10 h-12 pt-2', !shown && 'hidden')}>
        <button
          type="button"
          onClick={() => setListOpen(true)}
          className="mx-auto flex h-9 w-[calc(100%-2rem)] max-w-[52rem] items-center gap-2 overflow-hidden rounded-full bg-[#ffc23d]/15 ps-1 pe-3 text-start ring-1 ring-[#ffc23d]/60 transition-colors hover:bg-[#ffc23d]/25 sm:w-[calc(100%-4rem)]"
          aria-label={l ? `${t('mega.title')}: ${l.user.name} ${l.text}, ${timeAgo(l.at)}` : t('mega.title')}
        >
          <span className="grid size-7 shrink-0 place-items-center rounded-full bg-[#ffc23d] text-[#3b2600]">
            <Megaphone className="size-4" aria-hidden />
          </span>
          <span className="relative h-full min-w-0 flex-1 overflow-hidden" aria-hidden>
            <AnimatePresence initial={false}>
              <m.span
                key={l?.id ?? 'none'}
                className="absolute inset-0 flex items-center gap-1.5 text-[13px]"
                initial={{ y: '100%', opacity: 0 }}
                animate={{ y: 0, opacity: 1 }}
                exit={{ y: '-100%', opacity: 0 }}
                transition={{ type: 'spring', stiffness: 380, damping: 34 }}
              >
                {l ? (
                  <>
                    <UserAvatar userId={l.user.id} name={l.user.name} src={l.user.photoUrl} size={20} />
                    <NameText userId={l.user.id} className="shrink-0 font-bold">{l.user.name}</NameText>
                    <span className="min-w-0 truncate" dir="auto">{l.text}</span>
                    <span className="ms-auto shrink-0 ps-2 text-xs text-muted-foreground">{timeAgo(l.at)}</span>
                  </>
                ) : (
                  <span className="truncate text-muted-foreground">{t('mega.placeholder')}</span>
                )}
              </m.span>
            </AnimatePresence>
          </span>
          <ChevronRight className="size-4 shrink-0 text-muted-foreground rtl:rotate-180" aria-hidden />
        </button>
      </div>
      <MegaphoneList open={listOpen} lines={lines} onClose={() => setListOpen(false)} onWrite={() => setComposing(true)} />
      <MegaphoneSheet open={composing} onClose={() => setComposing(false)} />
    </>
  )
}

/** The past day's lines, newest first; a name or photo opens that person's profile. */
function MegaphoneList({ open, lines, onClose, onWrite }: { open: boolean; lines: MegaphoneLine[]; onClose: () => void; onWrite: () => void }) {
  const { openProfile } = useApp()
  const { t, timeAgo } = useI18n()
  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={t('mega.title')} className="md:max-w-md">
      <div className="flex max-h-[60dvh] flex-col">
        {lines.length ? (
          <ul className="flex-1 overflow-y-auto px-2">
            {lines.map((l) => (
              <li key={l.id} className="flex gap-3 rounded-2xl px-3 py-2.5">
                <button type="button" onClick={() => openProfile(l.user.id)} className="shrink-0 self-start" aria-label={l.user.name}>
                  <UserAvatar userId={l.user.id} name={l.user.name} src={l.user.photoUrl} size={40} />
                </button>
                <div className="min-w-0 flex-1">
                  <p className="flex min-w-0 items-baseline gap-1.5 text-sm">
                    <button type="button" onClick={() => openProfile(l.user.id)} className="min-w-0 truncate text-start hover:underline">
                      <NameText userId={l.user.id} className="font-semibold">{l.user.name}</NameText>
                      <span className="ms-1 text-muted-foreground">@{l.user.handle}</span>
                    </button>
                    <span className="shrink-0 text-xs text-muted-foreground">{timeAgo(l.at)}</span>
                  </p>
                  <p className="text-[15px] leading-snug break-words" dir="auto">{l.text}</p>
                </div>
              </li>
            ))}
          </ul>
        ) : (
          <p className="px-6 py-10 text-center text-sm text-muted-foreground">{t('mega.empty')}</p>
        )}
        <div className="border-t border-border p-4">
          <Button variant="brand" size="lg" className="w-full" onClick={onWrite}>
            <Megaphone className="size-4" aria-hidden /> {t('mega.write')}
          </Button>
        </div>
      </div>
    </Modal>
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
  // -1: unlimited (102)
  const has = count === -1 || (count ?? 0) > 0
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
          {count === undefined ? '…' : t('mega.left', { n: count === -1 ? '∞' : num(count) })}
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
