'use client'

import { AnimatePresence, useReducedMotion } from 'motion/react'
import * as m from 'motion/react-m'
import { useEffect, useLayoutEffect, useRef, useState } from 'react'
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

// The megaphone (100), after MapleStory's (the owner, 2026-10-30): a bought line passes across a thin bar at the top of
// everyone's screen in my country, without getting in the way. A tap on the bar opens the past day's lines (101), where
// a name opens that person's profile: someone who agrees with a line gets in touch from there.

/** The text moves at this many pixels a second, whatever its length. */
const SPEED = 70
const MAX = 80
const REPLAY_MS = 120_000

/** The bar on top of every screen, the line list it opens and the writing sheet. Lines from people I blocked are left out. */
export function MegaphoneBanner() {
  const backend = useBackend()
  const [queue, setQueue] = useState<MegaphoneLine[]>([])
  const [listOpen, setListOpen] = useState(false)
  const [composing, setComposing] = useState(false)
  const { data: history, mutate } = useSWR('megaphone-lines', () => backend.megaphoneHistory())
  const { data: blocks } = useSWR(history?.length || queue.length ? 'blocks' : null, () => backend.blocks())
  const add = (ls: MegaphoneLine[], q: MegaphoneLine[]) => [...q, ...ls.filter((l) => !q.some((x) => x.id === l.id))].slice(-10)
  useEffect(
    () =>
      backend.megaphones((l) => {
        void mutate((h) => (h?.some((x) => x.id === l.id) ? h : [l, ...(h ?? [])].slice(0, 50)), { revalidate: false })
        setQueue((q) => add([l], q))
      }),
    [backend, mutate],
  )
  // An app opened late still sees the past 2 minutes' lines pass, oldest first.
  const replayed = useRef(false)
  useEffect(() => {
    if (!history || replayed.current) return
    replayed.current = true
    setQueue((q) => add(history.filter((l) => l.at > Date.now() - REPLAY_MS).slice(0, 3).reverse(), q))
  }, [history])
  const ok = (l: MegaphoneLine) => !blocks?.some((b) => b.id === l.user.id)
  const shown = queue.find(ok) ?? null
  const done = (id: number) => setQueue((q) => q.filter((x) => x.id !== id))
  return (
    <>
      <div className="pointer-events-none fixed inset-x-0 top-[max(0.5rem,env(safe-area-inset-top))] z-[60] flex justify-center" aria-live="polite">
        {/* One at a time; narrow enough on a phone that the top bar's buttons at the edges stay free. */}
        <AnimatePresence mode="wait">
          {shown ? <Ticker key={shown.id} line={shown} onOpen={() => setListOpen(true)} onDone={() => done(shown.id)} /> : null}
        </AnimatePresence>
      </div>
      <MegaphoneList
        open={listOpen}
        lines={(history ?? []).filter(ok)}
        onClose={() => setListOpen(false)}
        onWrite={() => setComposing(true)}
      />
      <MegaphoneSheet open={composing} onClose={() => setComposing(false)} />
    </>
  )
}

/** The thin bar: the line moves across it once, then the bar goes. Reduced motion: it stands still for a few seconds. */
function Ticker({ line, onOpen, onDone }: { line: MegaphoneLine; onOpen: () => void; onDone: () => void }) {
  const { t } = useI18n()
  const reduce = useReducedMotion()
  const box = useRef<HTMLSpanElement>(null)
  const text = useRef<HTMLSpanElement>(null)
  const [run, setRun] = useState<{ from: number; to: number; s: number } | null>(null)
  const finish = useRef(onDone)
  finish.current = onDone
  useLayoutEffect(() => {
    const w = box.current!.clientWidth
    const tw = text.current!.scrollWidth
    // Physical pixels: right to left, or left to right in a right-to-left language.
    const rtl = getComputedStyle(box.current!).direction === 'rtl'
    setRun({ from: rtl ? -tw : w, to: rtl ? w : -tw, s: (w + tw) / SPEED })
  }, [])
  useEffect(() => {
    if (!reduce) return
    const id = setTimeout(() => finish.current(), 7000)
    return () => clearTimeout(id)
  }, [reduce])
  return (
    <m.button
      type="button"
      onClick={onOpen}
      initial={{ y: -48, opacity: 0 }}
      animate={{ y: 0, opacity: 1 }}
      exit={{ y: -48, opacity: 0 }}
      transition={{ type: 'spring', stiffness: 420, damping: 34 }}
      className="pointer-events-auto flex h-8 w-[calc(100%-7rem)] max-w-md items-center gap-1.5 overflow-hidden rounded-full bg-[#ffc23d] ps-1 text-[#3b2600] shadow-e2"
      aria-label={`${t('mega.title')}: ${line.user.name} ${line.text}`}
    >
      <span className="grid size-6 shrink-0 place-items-center rounded-full bg-[#3b2600]/12">
        <Megaphone className="size-3.5" aria-hidden />
      </span>
      <span ref={box} className="relative h-full min-w-0 flex-1 overflow-hidden" aria-hidden>
        <m.span
          ref={text}
          className="absolute top-0 left-0 flex h-full items-center gap-1.5 text-[13px] whitespace-nowrap"
          initial={false}
          animate={run && !reduce ? { x: [run.from, run.to] } : { x: 0 }}
          transition={run && !reduce ? { duration: run.s, ease: 'linear' } : { duration: 0 }}
          onAnimationComplete={() => run && !reduce && finish.current()}
          style={{ visibility: run ? 'visible' : 'hidden' }}
        >
          <NameText userId={line.user.id} className="font-bold">{line.user.name}</NameText>
          <span dir="auto">{line.text}</span>
        </m.span>
      </span>
    </m.button>
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
