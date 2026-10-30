'use client'

import { AnimatePresence } from 'motion/react'
import * as m from 'motion/react-m'
import { useEffect, useRef, useState } from 'react'
import useSWR from 'swr'
import { Megaphone, MessageCircle, SendHorizontal } from 'lucide-react'
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

// The megaphone (100), after MapleStory's (the owner, 2026-10-30): a bar above every tab showing the newest line; its
// chat button (or the bar) opens the past day's lines (101) as a chat, oldest at the top, where I can send one myself
// when I have a megaphone. A name or photo there opens that person's profile, to get in touch.

const MAX = 80
const LINES = 'megaphone-lines'

/** The bar (hidden while a chat, a room or a random chat fills the screen); it also keeps the lines coming in. */
export function MegaphoneBar({ shown }: { shown: boolean }) {
  const backend = useBackend()
  const { t } = useI18n()
  const [open, setOpen] = useState(false)
  const { data: history, mutate } = useSWR(LINES, () => backend.megaphoneHistory())
  const { data: blocks } = useSWR(history?.length ? 'blocks' : null, () => backend.blocks())
  useEffect(
    () => backend.megaphones((l) => void mutate((h) => (h?.some((x) => x.id === l.id) ? h : [l, ...(h ?? [])].slice(0, 50)), { revalidate: false })),
    [backend, mutate],
  )
  const l = history?.find((x) => !blocks?.some((b) => b.id === x.user.id))
  return (
    <>
      <div className={cn('absolute inset-x-0 top-0 z-10 h-12 pt-2', !shown && 'hidden')}>
        <button
          type="button"
          onClick={() => setOpen(true)}
          className="mx-auto flex h-9 w-[calc(100%-2rem)] max-w-[52rem] items-center gap-2 overflow-hidden rounded-full bg-[#ffc23d]/15 ps-1 pe-1 text-start ring-1 ring-[#ffc23d]/60 transition-colors hover:bg-[#ffc23d]/25 sm:w-[calc(100%-4rem)]"
          aria-label={l ? `${t('mega.title')}: ${l.user.name} ${l.text}` : t('mega.title')}
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
                  </>
                ) : (
                  <span className="truncate text-muted-foreground">{t('mega.placeholder')}</span>
                )}
              </m.span>
            </AnimatePresence>
          </span>
          {/* The chat: every line of the past day, and my own. */}
          <span className="grid size-7 shrink-0 place-items-center rounded-full bg-background/80 text-foreground ring-1 ring-[#ffc23d]/60">
            <MessageCircle className="size-4" aria-hidden />
          </span>
        </button>
      </div>
      <MegaphoneChat open={open} onClose={() => setOpen(false)} />
    </>
  )
}

/** The day's lines as a chat (mine on the other side), and a field to send one while I have a megaphone. */
export function MegaphoneChat({ open, onClose }: { open: boolean; onClose: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { me, openProfile } = useApp()
  const { t, num, timeAgo } = useI18n()
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const { data: history } = useSWR(open ? LINES : null, () => backend.megaphoneHistory())
  const { data: blocks } = useSWR(open && history?.length ? 'blocks' : null, () => backend.blocks())
  const { data: count, mutate } = useSWR(open ? 'megaphones' : null, () => backend.megaphoneCount())
  const lines = (history ?? []).filter((l) => !blocks?.some((b) => b.id === l.user.id)).reverse()
  // The newest at the bottom, in sight, as a chat opens; and again when one comes.
  const list = useRef<HTMLUListElement>(null)
  useEffect(() => {
    if (open) requestAnimationFrame(() => list.current?.scrollTo({ top: list.current.scrollHeight }))
  }, [open, lines.length])
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
  // -1: unlimited (102)
  const has = count === -1 || (count ?? 0) > 0
  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={t('mega.title')} description={t('mega.desc')} className="md:max-w-md">
      <div className="flex h-[min(70dvh,40rem)] flex-col">
        {lines.length ? (
          <ul ref={list} className="flex flex-1 flex-col gap-3 overflow-y-auto px-4 py-2">
            {lines.map((l) => {
              const mine = l.user.id === me?.id
              return (
                <li key={l.id} className={cn('flex items-end gap-2', mine && 'flex-row-reverse')}>
                  {mine ? null : (
                    <button type="button" onClick={() => openProfile(l.user.id)} className="shrink-0" aria-label={l.user.name}>
                      <UserAvatar userId={l.user.id} name={l.user.name} src={l.user.photoUrl} size={32} />
                    </button>
                  )}
                  <div className={cn('flex min-w-0 max-w-[80%] flex-col gap-0.5', mine && 'items-end')}>
                    {mine ? null : (
                      <button type="button" onClick={() => openProfile(l.user.id)} className="min-w-0 truncate ps-1 text-start text-xs hover:underline">
                        <NameText userId={l.user.id} className="font-semibold">{l.user.name}</NameText>
                        <span className="ms-1 text-muted-foreground">@{l.user.handle}</span>
                      </button>
                    )}
                    <div className={cn('flex items-end gap-1.5', mine && 'flex-row-reverse')}>
                      <p className={cn('rounded-2xl px-3 py-2 text-[15px] leading-snug break-words', mine ? 'bg-primary text-primary-foreground' : 'bg-muted')} dir="auto">
                        {l.text}
                      </p>
                      <span className="shrink-0 pb-0.5 text-[11px] whitespace-nowrap text-muted-foreground">{timeAgo(l.at)}</span>
                    </div>
                  </div>
                </li>
              )
            })}
          </ul>
        ) : (
          <p className="grid flex-1 place-items-center px-6 text-center text-sm text-muted-foreground">{t('mega.empty')}</p>
        )}
        <div className="border-t border-border p-3">
          {count === undefined ? null : has ? (
            <form
              className="flex flex-col gap-1"
              onSubmit={(e) => {
                e.preventDefault()
                const body = text.trim()
                if (body && !busy) void run(async () => (await backend.sendMegaphone(body), setText('')))
              }}
            >
              <div className="flex items-center gap-2">
                <TextInput className="flex-1" value={text} maxLength={MAX} placeholder={t('mega.placeholder')} onChange={(e) => setText(e.target.value)} aria-label={t('mega.placeholder')} />
                <Button type="submit" size="icon" variant="brand" loading={busy} disabled={!text.trim()} aria-label={t('mega.send')}>
                  <SendHorizontal className="size-[18px] rtl:rotate-180" aria-hidden />
                </Button>
              </div>
              <p className="flex justify-between px-1 text-xs text-muted-foreground tabular-nums">
                <span className="flex items-center gap-1">
                  <Megaphone className="size-3.5" aria-hidden /> {t('mega.left', { n: count === -1 ? '∞' : num(count) })}
                </span>
                <span>{[...text].length}/{MAX}</span>
              </p>
            </form>
          ) : backend.buyMegaphone ? (
            <Button variant="premium" size="lg" className="w-full" loading={busy} onClick={() => void run(() => backend.buyMegaphone!())}>
              {t('mega.buy')}
            </Button>
          ) : (
            <p className="py-1 text-center text-xs text-muted-foreground">{t('mega.appNote')}</p>
          )}
        </div>
      </div>
    </Modal>
  )
}
