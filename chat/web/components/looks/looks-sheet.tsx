'use client'

import { useEffect, useState } from 'react'
import useSWR, { useSWRConfig } from 'swr'
import { Check, ExternalLink } from 'lucide-react'
import type { Look, LookKind, LookWear, Shop } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { CATALOGUE, kindOf, LOOK_KINDS, NO_WEAR, PLAY_URL, wearOf, wearWith, type FrameId, type ItemId } from '@/lib/looks'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Segmented, Skeleton } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'
import { FrameAvatar, FrameCard } from './frames'
import { lookKey, LookText } from './look-name'
import { useLooksT, type LooksKey } from './looks-text'

/**
 * The Looks shop: my card on top with what I'm trying on, four tabs (badges, colours, fonts, frames) of item cards
 * with "None" first. A tap tries an item on; what I own I wear (equip_look, one of each kind). Looks are bought in
 * the Android app (Google Play); the demo sells them at once.
 */
export function LooksSheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { lt } = useLooksT()
  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={lt('looks.title')} className="md:max-w-xl">
      {open ? <ShopBody /> : null}
    </Modal>
  )
}

const itemName = (lt: (k: LooksKey) => string, id: ItemId) => lt(`item.${id}` as LooksKey)

function ShopBody() {
  const backend = useBackend()
  const toast = useToast()
  const { mutate } = useSWRConfig()
  const { me } = useApp()
  const { t, locale } = useI18n()
  const { lt } = useLooksT()
  const { data: shop, error, mutate: reload } = useSWR('shop', () => backend.shop())
  const [kind, setKind] = useState<LookKind>('badge')
  /** What I wear now (the server's), and what I'm trying on over it. */
  const [worn, setWorn] = useState<LookWear | null>(null)
  const [trying, setTrying] = useState<LookWear>(NO_WEAR)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!shop || worn) return
    setWorn(wearOf(shop.look))
    setTrying(wearOf(shop.look))
  }, [shop, worn])

  if (!shop || !worn) {
    return error && !shop ? (
      <div className="flex flex-col items-center gap-3 px-6 py-10 text-center">
        <p className="font-semibold">{lt('looks.loadFailed')}</p>
        <Button variant="outline" onClick={() => void reload()}>
          {t('common.tryAgain')}
        </Button>
      </div>
    ) : (
      <div className="flex flex-col gap-4 px-6 pt-3 pb-6" aria-busy>
        <Skeleton className="h-44 w-full rounded-[28px]" />
        <Skeleton className="h-11 w-full rounded-full" />
        <div className="grid grid-cols-3 gap-3">
          {Array.from({ length: 6 }, (_, i) => (
            <Skeleton key={i} className="h-28 rounded-2xl" />
          ))}
        </div>
      </div>
    )
  }

  const handle = shop.look?.handle ?? me.handle ?? ''
  const owned = new Set<string>(shop.owned)
  const price = (id: ItemId) => {
    const inr = shop.items.find((i) => i.id === id)?.priceInr
    return inr === undefined ? '' : new Intl.NumberFormat(locale, { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(inr)
  }
  const tried = trying[kind] as ItemId | null
  const sale = new Set(shop.items.map((i) => i.id))
  const list = CATALOGUE[kind].filter((id) => sale.has(id))

  /** Saves what I wear with this kind changed; the rest stays as worn (never an item only tried on). */
  const wear = async (id: ItemId | null) => {
    setBusy(true)
    try {
      const look = await backend.equipLook(wearWith(worn, kind, id))
      setWorn(wearOf(look))
      setTrying((w) => ({ ...wearOf(look), [kind]: w[kind] }))
      await Promise.all([mutate(lookKey(me.id), look, { revalidate: false }), mutate('shop', { ...shop, look } satisfies Shop, { revalidate: false })])
      toast.show(lt('looks.saved'), 'success')
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }

  const buy = async (id: ItemId) => {
    if (!backend.buyLook) return
    setBusy(true)
    try {
      await backend.buyLook(id)
      // The first item of a kind is worn at once (as after a purchase in the app).
      const fresh = await reload()
      const look: Look | null = fresh?.look ?? null
      setWorn(wearOf(look))
      if (look) await mutate(lookKey(me.id), look, { revalidate: false })
      toast.show(lt('looks.bought', { item: itemName(lt, id) }), 'success')
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }

  const status = (id: ItemId) => (worn[kindOf(id)] === id ? lt('looks.wearing') : owned.has(id) ? lt('looks.owned') : price(id))

  let action: React.ReactNode
  if (tried === null) {
    action = worn[kind] ? (
      <Button variant="outline" size="lg" className="w-full" loading={busy} onClick={() => void wear(null)}>
        {lt('looks.takeOff')}
      </Button>
    ) : (
      <Button variant="secondary" size="lg" className="w-full" disabled>
        <Check className="size-4.5" aria-hidden /> {lt('looks.wearing')}
      </Button>
    )
  } else if (owned.has(tried)) {
    action =
      worn[kind] === tried ? (
        <Button variant="secondary" size="lg" className="w-full" disabled>
          <Check className="size-4.5" aria-hidden /> {lt('looks.wearing')}
        </Button>
      ) : (
        <Button variant="brand" size="lg" className="w-full" loading={busy} onClick={() => void wear(tried)}>
          {lt('looks.wear')}
        </Button>
      )
  } else if (backend.buyLook) {
    action = (
      <Button variant="brand" size="lg" className="w-full" loading={busy} onClick={() => void buy(tried)}>
        {lt('looks.buy', { price: price(tried) })}
      </Button>
    )
  } else {
    action = (
      <div className="flex items-center gap-3">
        <span className="shrink-0 text-lg font-bold tabular-nums">{price(tried)}</span>
        <a
          href={PLAY_URL}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex h-12 min-w-0 flex-1 items-center justify-center gap-2 rounded-full bg-primary px-5 text-[15px] font-semibold text-primary-foreground transition-colors duration-150 hover:bg-primary-hover"
        >
          <span className="truncate">{lt('looks.getInApp')}</span>
          <ExternalLink className="size-4 shrink-0 rtl:-scale-x-100" aria-hidden />
        </a>
      </div>
    )
  }

  return (
    <div className="flex flex-col">
      <div className="flex flex-col gap-5 px-6 pt-3 pb-5">
        <section aria-label={lt('looks.preview')} className="flex flex-col items-center gap-2">
          <FrameCard frame={trying.frame as FrameId | null} className="w-full">
            <div className="flex flex-col items-center gap-1.5 px-4 pt-6 pb-5 text-center">
              <FrameAvatar frame={trying.frame as FrameId | null} name={me.name} src={me.photoUrl} size={72} />
              <p className="mt-1 text-lg font-bold">{me.name}</p>
              {handle ? <LookText handle={handle} wear={trying} className="text-[17px] font-semibold" /> : null}
            </div>
          </FrameCard>
          <p className="text-xs font-medium text-muted-foreground">{lt('looks.everyone')}</p>
        </section>

        <Segmented<LookKind>
          label={lt('looks.tabs')}
          value={kind}
          onChange={setKind}
          className="w-full"
          options={LOOK_KINDS.map((k) => ({ value: k, label: lt(`looks.tab.${k}`) }))}
        />

        <ul role="radiogroup" aria-label={lt(`looks.tab.${kind}`)} className="grid grid-cols-3 gap-2.5 sm:grid-cols-4">
          <li>
            <ItemCard on={tried === null} label={lt('looks.none')} sub={worn[kind] === null ? lt('looks.wearing') : ''} worn={worn[kind] === null} onClick={() => setTrying((w) => wearWith(w, kind, null))}>
              {kind === 'frame' ? (
                <FrameAvatar frame={null} name={me.name} src={me.photoUrl} size={36} />
              ) : handle ? (
                <LookText handle={handle} wear={NO_WEAR} className="text-sm font-semibold" />
              ) : null}
            </ItemCard>
          </li>
          {list.map((id) => (
            <li key={id}>
              <ItemCard on={tried === id} label={itemName(lt, id)} sub={status(id)} worn={worn[kind] === id} onClick={() => setTrying((w) => wearWith(w, kind, id))}>
                {kind === 'frame' ? (
                  <FrameAvatar frame={id as FrameId} name={me.name} src={me.photoUrl} size={36} />
                ) : handle ? (
                  <LookText handle={handle} wear={wearWith(NO_WEAR, kind, id)} className="text-sm font-semibold" />
                ) : null}
              </ItemCard>
            </li>
          ))}
        </ul>
        {backend.buyLook ? null : <p className="text-xs leading-relaxed text-muted-foreground">{lt('looks.appNote')}</p>}
      </div>
      <div className="sticky bottom-0 border-t border-border bg-card px-6 py-4">{action}</div>
    </div>
  )
}

/** One item: its preview on my @ID (a framed photo for frames), its name and whether I wear it, own it or its price. */
function ItemCard({
  on,
  worn,
  label,
  sub,
  onClick,
  children,
}: {
  on: boolean
  worn?: boolean
  label: string
  sub: string
  onClick: () => void
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={on}
      aria-label={sub ? `${label}, ${sub}` : label}
      onClick={onClick}
      className={cn(
        'flex h-full w-full min-w-0 flex-col items-center gap-1.5 rounded-2xl border px-1.5 pt-3 pb-2 text-center transition-colors duration-150',
        on ? 'border-primary-text bg-primary-soft' : 'border-border hover:bg-muted',
      )}
    >
      <span className="grid h-14 w-full min-w-0 place-items-center px-1">{children}</span>
      <span className="w-full truncate text-[13px] font-semibold">{label}</span>
      <span className={cn('flex min-h-4 items-center gap-1 text-[11px] font-semibold tabular-nums', worn ? 'text-primary-text' : 'text-muted-foreground')}>
        {worn ? <Check className="size-3" strokeWidth={3} aria-hidden /> : null}
        {sub}
      </span>
    </button>
  )
}
