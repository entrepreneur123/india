'use client'

import { useReducedMotion } from 'motion/react'
import { useState } from 'react'
import { useI18n } from '@/components/i18n'
import { Button } from '@/components/ui/pressable'
import { TopicIcon } from '@/components/ui/topic-icons'
import { cn } from '@/lib/utils'
import { LUDO_CSS, LudoBoard, PALETTE, type TokenView } from './ludo-board'
import { useLudoT } from './ludo-text'

const PREVIEW: TokenView[] = Array.from({ length: 16 }, (_, idx) => ({ idx, pos: -1, label: '', movable: false }))

/** A games room with no game on: the board, the four colours, and (hosts and co-hosts) the button to open one. */
export function StartLudoCard({ canStart, onStart, wide }: { canStart: boolean; onStart: () => Promise<unknown>; wide: boolean }) {
  const { t } = useI18n()
  const { lt, colour } = useLudoT()
  const reduce = !!useReducedMotion()
  const [busy, setBusy] = useState(false)
  return (
    <section aria-label={t('games.start')} className="ludo mx-3 rounded-3xl bg-card/80 p-4 ring-1 ring-border sm:mx-4 sm:p-5">
      <style>{LUDO_CSS}</style>
      <div className={cn('flex gap-5', wide ? 'items-center' : 'flex-col items-center')}>
        <LudoBoard
          tokens={PREVIEW}
          seats={[true, true, true, true]}
          turn={null}
          reduce={reduce}
          label={lt('board')}
          className={cn('block aspect-square shrink-0 rounded-[16px] shadow-[0_10px_30px_-10px_rgb(0_0_0/0.65)]', wide ? 'w-60' : 'w-48')}
        />
        <div className={cn('flex w-full min-w-0 flex-col gap-3', !wide && 'items-center text-center')}>
          <div>
            <h2 className={cn('flex items-center gap-2 text-xl font-bold', !wide && 'justify-center')}>
              <TopicIcon topic="gaming" size={24} /> {t('games.start')}
            </h2>
            <p className="mt-1 text-[13.5px] leading-snug text-muted-foreground">{t('games.startBody')}</p>
          </div>
          <ul className="flex flex-wrap gap-1.5" aria-hidden>
            {[0, 1, 2, 3].map((seat) => (
              <li
                key={seat}
                className="flex h-7 items-center gap-1.5 rounded-full px-2.5 text-xs font-semibold"
                style={{ background: `color-mix(in srgb, ${PALETTE.seats[seat]} 16%, transparent)` }}
              >
                <span className="size-2.5 rounded-full" style={{ background: PALETTE.seats[seat] }} />
                {colour(seat)}
              </li>
            ))}
          </ul>
          {canStart ? (
            <Button
              variant="brand"
              size="lg"
              loading={busy}
              className={cn(!wide && 'w-full')}
              onClick={async () => {
                setBusy(true)
                await onStart()
                setBusy(false)
              }}
            >
              {t('games.start')}
            </Button>
          ) : (
            <p className="rounded-xl bg-muted px-3 py-2 text-sm text-muted-foreground" role="status">
              {t('games.waitHost')}
            </p>
          )}
        </div>
      </div>
    </section>
  )
}
