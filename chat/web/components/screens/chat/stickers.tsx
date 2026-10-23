'use client'

import { useState } from 'react'
import { Lock, Sticker } from 'lucide-react'
import { STICKER_PACKS, type PackId } from '@/lib/looks'
import { STICKERS, stickerBody, type StickerArt } from '@/lib/stickers'
import { useApp } from '@/components/app-state'
import { useLooksT, type LooksKey } from '@/components/looks/looks-text'
import { useShop } from '@/components/looks/use-shop'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { cn } from '@/lib/utils'

// Stickers (046): the card each one is drawn as, and the picker beside a chat's text box (packs I own; the others
// locked, with a way to the shop). Sending one is an ordinary message (lib/stickers.ts).

/** Each pack's card, in the theme's brand colours. */
const TONES: Record<string, { card: string; cap: string }> = {
  sticker_namaste: { card: 'bg-primary-soft ring-1 ring-primary/50', cap: 'bg-primary text-primary-foreground' },
  sticker_chai: { card: 'bg-card ring-2 ring-primary', cap: 'bg-primary text-primary-foreground' },
  sticker_cricket: { card: 'bg-card ring-1 ring-border', cap: 'bg-foreground text-background' },
  sticker_love: { card: 'bg-primary', cap: 'bg-background text-primary-text' },
}

/** One sticker: a rounded card with the big emoji and its caption (unknown art: a plain sticker mark). */
export function StickerCard({ pack, art, size = 128, className }: { pack: string; art: StickerArt | null; size?: number; className?: string }) {
  const tone = TONES[pack] ?? TONES.sticker_namaste
  const small = size < 56 // a shop card's thumbnail: the emoji only
  return (
    <span
      role="img"
      aria-label={art?.caption ?? 'Sticker'}
      className={cn('flex shrink-0 flex-col items-center justify-center gap-[6%] rounded-[22%] shadow-e1 select-none', tone.card, className)}
      style={{ width: size, height: size }}
    >
      <span aria-hidden className="leading-none" style={{ fontSize: size * (small ? 0.5 : 0.4) }}>
        {art?.emoji ?? '✨'}
      </span>
      {art && !small ? (
        // Two lines at most, so longer captions ("Chai pe charcha") still fit a small card.
        <span
          aria-hidden
          className={cn('line-clamp-2 max-w-[88%] rounded-[0.7em] px-[0.6em] py-[0.2em] text-center leading-[1.1] font-extrabold tracking-tight', tone.cap)}
          style={{ fontSize: Math.max(8.5, size * 0.1) }}
        >
          {art.caption}
        </span>
      ) : null}
    </span>
  )
}

/** The sticker button and its sheet; `onSend` gets the message body and says whether it went. */
export function StickerButton({ onSend, disabled, className }: { onSend: (body: string) => Promise<boolean>; disabled?: boolean; className?: string }) {
  const { lt } = useLooksT()
  const { openShop } = useApp()
  const { data: shop } = useShop()
  const [open, setOpen] = useState(false)
  const [pack, setPack] = useState<PackId | null>(null)
  const [busy, setBusy] = useState(false)
  const owned = new Set(shop?.owned ?? [])
  const shown = pack ?? STICKER_PACKS.find((p) => owned.has(p)) ?? STICKER_PACKS[0]
  const mine = owned.has(shown)

  const send = async (n: number) => {
    setBusy(true)
    const ok = await onSend(stickerBody(shown, n))
    setBusy(false)
    if (ok) setOpen(false)
  }

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        disabled={disabled}
        aria-label={lt('looks.tab.sticker')}
        title={lt('looks.tab.sticker')}
        className={cn(
          'grid size-11 shrink-0 place-items-center rounded-full text-muted-foreground transition-colors duration-150 hover:bg-muted hover:text-foreground disabled:pointer-events-none disabled:opacity-40',
          className,
        )}
      >
        <Sticker className="size-5" aria-hidden />
      </button>
      <Modal open={open} onClose={() => setOpen(false)} variant="sheet" title={lt('looks.tab.sticker')} className="md:max-w-md">
        <div className="flex flex-col gap-4 px-5 pt-1 pb-[max(1.25rem,env(safe-area-inset-bottom))]">
          <div role="tablist" aria-label={lt('looks.tab.sticker')} className="-mx-5 flex gap-2 overflow-x-auto px-5 no-scrollbar">
            {STICKER_PACKS.map((p) => (
              <button
                key={p}
                type="button"
                role="tab"
                aria-selected={p === shown}
                onClick={() => setPack(p)}
                className={cn(
                  'flex h-10 shrink-0 items-center gap-1.5 rounded-full border px-3.5 text-sm font-semibold transition-colors duration-150',
                  p === shown ? 'border-primary-text bg-primary-soft text-foreground' : 'border-border text-muted-foreground hover:bg-muted',
                )}
              >
                <span aria-hidden className="text-lg leading-none">{STICKERS[p][0].emoji}</span>
                {lt(`item.${p}` as LooksKey)}
                {owned.has(p) ? null : <Lock className="size-3.5 text-primary-text" aria-label={lt('sticker.locked')} />}
              </button>
            ))}
          </div>
          <ul role="tabpanel" aria-label={lt(`item.${shown}` as LooksKey)} className={cn('grid grid-cols-4 gap-2.5', !mine && 'opacity-45')}>
            {STICKERS[shown].map((art, i) => (
              <li key={i}>
                <button
                  type="button"
                  disabled={!mine || busy}
                  onClick={() => void send(i + 1)}
                  aria-label={art.caption}
                  className="block w-full rounded-[22%] transition-transform duration-150 enabled:hover:scale-105 enabled:active:scale-95 disabled:cursor-default"
                >
                  <StickerCard pack={shown} art={art} size={72} className="mx-auto" />
                </button>
              </li>
            ))}
          </ul>
          {shop && !mine ? (
            <div className="flex items-center gap-3 rounded-2xl bg-muted px-4 py-3">
              <Lock className="size-4.5 shrink-0 text-primary-text" aria-hidden />
              <p className="min-w-0 flex-1 text-sm leading-snug">{lt('sticker.locked')}</p>
              <Button
                variant="brand"
                size="sm"
                className="shrink-0"
                onClick={() => {
                  setOpen(false)
                  openShop('sticker')
                }}
              >
                {lt('sticker.getPack')}
              </Button>
            </div>
          ) : null}
        </div>
      </Modal>
    </>
  )
}
