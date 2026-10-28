'use client'

import { AnimatePresence } from 'motion/react'
import * as m from 'motion/react-m'
import { useState } from 'react'
import { MessagesSquare, Shuffle, Users, type LucideIcon } from 'lucide-react'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { TOUR_KEY, write } from './first-run'
import { useI18n } from '@/components/i18n'
import { useSocialT, type SocialKey } from './social-text'

const CARDS: { icon: LucideIcon; title: SocialKey; body: SocialKey }[] = [
  { icon: Shuffle, title: 'tour.1.title', body: 'tour.1.body' },
  { icon: MessagesSquare, title: 'tour.2.title', body: 'tour.2.body' },
  { icon: Users, title: 'tour.3.title', body: 'tour.3.body' },
]

/** First run: three cards about the app (skippable, shown once). */
export function Onboarding({ onDone }: { onDone: () => void }) {
  const { st } = useSocialT()
  const { rtl } = useI18n()
  const [open, setOpen] = useState(true)
  const [card, setCard] = useState(0)

  const finish = () => {
    write(TOUR_KEY)
    setOpen(false)
    onDone()
  }
  const c = CARDS[card]
  const Icon = c.icon
  return (
    <Modal open={open} onClose={finish} title={st('tour.label')} hideHeader className="md:max-w-md">
      <div className="flex flex-col items-center px-6 pt-8 pb-6 text-center">
        <span className="text-xs font-semibold tracking-wide text-muted-foreground uppercase tabular-nums">
          {st('tour.step', { n: card + 1, total: CARDS.length })}
        </span>
        <AnimatePresence mode="wait" initial={false}>
          <m.div
            key={card}
            className="flex flex-col items-center"
            initial={{ opacity: 0, x: rtl ? -16 : 16 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: rtl ? 16 : -16 }}
            transition={{ duration: 0.18, ease: [0.2, 0, 0, 1] }}
          >
            <span className="mt-6 grid size-20 place-items-center rounded-[26px] bg-primary-soft text-primary-text">
              <Icon className="size-9" strokeWidth={2} aria-hidden />
            </span>
            <h2 className="mt-6 text-2xl font-bold tracking-tight text-balance">{st(c.title)}</h2>
            <p className="mt-2 max-w-sm text-[15px] leading-relaxed text-pretty text-muted-foreground">{st(c.body)}</p>
          </m.div>
        </AnimatePresence>
        <div className="mt-6 flex gap-1.5" aria-hidden>
          {CARDS.map((_, i) => (
            <span key={i} className={i === card ? 'h-1.5 w-5 rounded-full bg-primary' : 'size-1.5 rounded-full bg-input'} />
          ))}
        </div>
        <div className="mt-8 flex w-full gap-3">
          <Button variant="ghost" size="lg" className="flex-1" onClick={finish}>
            {st('tour.skip')}
          </Button>
          <Button
            variant="brand"
            size="lg"
            className="flex-1"
            data-autofocus
            onClick={() => (card < CARDS.length - 1 ? setCard(card + 1) : finish())}
          >
            {card < CARDS.length - 1 ? st('tour.next') : st('tour.done')}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
