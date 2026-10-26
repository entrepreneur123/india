'use client'

import { useEffect, useState } from 'react'
import { useSWRConfig } from 'swr'
import { Check, ExternalLink, Gift } from 'lucide-react'
import type { ItemKind, Look, LookKind, LookWear, Shop } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { CATALOGUE, ITEM_KINDS, LOOK_KINDS, nameAge, NO_WEAR, PLAY_URL, wearOf, wearWith, type BadgeId, type FrameId, type ItemId } from '@/lib/looks'
import { wallVars } from '@/lib/walls'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Bubble } from '@/components/screens/chat/message-bubble'
import { Avatar } from '@/components/ui/avatar'
import { Skeleton } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'
import { LookBadge } from './badges'
import { FrameAvatar, FrameCard } from './frames'
import { lookKey, LookText } from './look-name'
import { useLooksT, type LooksKey } from './looks-text'
import { GiftSheet } from './gift-sheet'
import { useAnonSwitch, useShop } from './use-shop'

/**
 * The Looks shop: a preview on top (my card with what I'm trying on, a chat on the wall, my photo effect or chat
 * bubble, how strangers see me in VIP mode), tabs of item cards, and the action for the chosen one at the bottom.
 * Looks: a tap tries one on ("None" first); what I own I wear (equip_look, one of each kind). Walls (046): set_wall,
 * "None" is the plain one. Photo effects and bubbles (062): set_worn. VIP incognito is a switch (set_anon). Everything
 * is bought in the Android app (Google Play); the demo sells at once.
 */
export function LooksSheet({ open, onClose, tab = 'badge' }: { open: boolean; onClose: () => void; tab?: ItemKind }) {
  const { lt } = useLooksT()
  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={lt('looks.title')} className="md:max-w-xl">
      {open ? <ShopBody tab={tab} /> : null}
    </Modal>
  )
}

const itemName = (lt: (k: LooksKey) => string, id: ItemId) => lt(`item.${id}` as LooksKey)

function ShopBody({ tab }: { tab: ItemKind }) {
  const backend = useBackend()
  const toast = useToast()
  const { mutate } = useSWRConfig()
  const { me } = useApp()
  const { t, locale } = useI18n()
  const { lt } = useLooksT()
  const { data: shop, error, mutate: reload } = useShop(true)
  const anon = useAnonSwitch()
  const [kind, setKind] = useState<ItemKind>(tab)
  /** What I wear now (the server's), and what I'm trying on over it. */
  const [worn, setWorn] = useState<LookWear | null>(null)
  const [trying, setTrying] = useState<LookWear>(NO_WEAR)
  /** The wall being looked at (undefined: my wall). */
  const [wallTry, setWallTry] = useState<ItemId | null | undefined>(undefined)
  const [busy, setBusy] = useState(false)
  const [gift, setGift] = useState<ItemId | null>(null)
  const [cyclePick, setCyclePick] = useState<string[] | undefined>(undefined)
  /** The photo effect and bubble being looked at (missing: the one I wear). */
  const [extraTry, setExtraTry] = useState<Partial<Record<'effect' | 'bubble', ItemId | null>>>({})

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
  const look = (LOOK_KINDS as ItemKind[]).includes(kind) ? (kind as LookKind) : null
  const myWall = shop.wall as ItemId | null
  const shownWall = wallTry === undefined ? myWall : wallTry
  const extra = kind === 'effect' || kind === 'bubble' ? kind : null
  const myExtra = extra ? ((shop.look?.[extra] ?? null) as ItemId | null) : null
  const shownExtra = extra && extra in extraTry ? (extraTry[extra] ?? null) : myExtra
  // Kinds where one item (or none) is in use, chosen here: walls, photo effects and bubbles.
  const single = kind === 'wall' ? { mine: myWall, shown: shownWall } : extra ? { mine: myExtra, shown: shownExtra } : null
  const tried: ItemId | null = look ? (trying[look] as ItemId | null) : single ? single.shown : kind === 'cycle' ? 'cycle_photos' : 'vip_anon'
  // The photo gradient (062): my photos to pick from (the main one and the extra ones), and what is picked.
  const myPhotos = [...(me.photoPath && me.photoUrl ? [{ id: me.photoPath, url: me.photoUrl }] : []), ...(me.photos ?? [])]
  const picked = cyclePick ?? myPhotos.filter((x) => shop.look?.cycle?.includes(x.url)).map((x) => x.id)
  const saveCycle = async () => {
    setBusy(true)
    try {
      await backend.setCycle(picked.length ? picked : null)
      await Promise.all([mutate(lookKey(me.id)), reload()])
      toast.show(lt('looks.saved'), 'success')
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }
  const sale = new Set(shop.items.map((i) => i.id))
  const list = CATALOGUE[kind].filter((id) => sale.has(id))
  const choose = (id: ItemId | null) =>
    look ? setTrying((w) => wearWith(w, look, id)) : kind === 'wall' ? setWallTry(id) : extra && setExtraTry((x) => ({ ...x, [extra]: id }))
  const saveExtra = async (id: ItemId | null) => {
    if (!extra) return
    setBusy(true)
    try {
      await backend.setWorn(extra, id)
      await Promise.all([mutate(lookKey(me.id)), reload()])
      setExtraTry(({ [extra]: _, ...rest }) => rest)
      toast.show(lt('looks.saved'), 'success')
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }
  /** Takes off what is in use of this kind ("None"). */
  const takeOff = () => (look ? wear(null) : extra ? saveExtra(null) : setWall(null))
  const usingLabel = lt(kind === 'wall' ? 'wall.inUse' : 'looks.wearing')

  /** Saves what I wear with this kind changed; the rest stays as worn (never an item only tried on). */
  const wear = async (id: ItemId | null) => {
    if (!look) return
    setBusy(true)
    try {
      const saved = await backend.equipLook(wearWith(worn, look, id))
      setWorn(wearOf(saved))
      setTrying((w) => ({ ...wearOf(saved), [look]: w[look] }))
      await Promise.all([mutate(lookKey(me.id), saved, { revalidate: false }), mutate('shop', { ...shop, look: saved } satisfies Shop, { revalidate: false })])
      toast.show(lt('looks.saved'), 'success')
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }

  const setWall = async (id: ItemId | null) => {
    setBusy(true)
    try {
      await backend.setWall(id)
      await mutate('shop', { ...shop, wall: id } satisfies Shop, { revalidate: false })
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
      const mine: Look | null = fresh?.look ?? null
      setWorn(wearOf(mine))
      if (mine) await mutate(lookKey(me.id), mine, { revalidate: false })
      toast.show(lt('looks.bought', { item: itemName(lt, id) }), 'success')
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }

  const status = (id: ItemId) => ((look ? worn[look] === id : single?.mine === id) ? usingLabel : owned.has(id) ? lt('looks.owned') : price(id))
  const inUse = (label: string) => (
    <Button variant="secondary" size="lg" className="w-full" disabled>
      <Check className="size-4.5" aria-hidden /> {label}
    </Button>
  )

  let action: React.ReactNode
  if (tried === null) {
    // "None": takes off what I wear of this kind (or my wall).
    const on = look ? worn[look] : single?.mine
    action = on ? (
      <Button variant="outline" size="lg" className="w-full" loading={busy} onClick={() => void takeOff()}>
        {lt(kind === 'wall' ? 'wall.remove' : 'looks.takeOff')}
      </Button>
    ) : (
      inUse(usingLabel)
    )
  } else if (owned.has(tried)) {
    if (kind === 'cycle') {
      action = (
        <Button variant="brand" size="lg" className="w-full" loading={busy} disabled={picked.length === 1} onClick={() => void saveCycle()}>
          {lt('cycle.save')}
        </Button>
      )
    } else if (kind === 'vip') {
      action = <AnonSwitch on={shop.anon} busy={anon.busy} onChange={(on) => void anon.set(on)} />
    } else if (extra) {
      action = myExtra === tried ? inUse(lt('looks.wearing')) : (
        <Button variant="brand" size="lg" className="w-full" loading={busy} onClick={() => void saveExtra(tried)}>
          {lt('looks.wear')}
        </Button>
      )
    } else if (kind === 'wall') {
      action = myWall === tried ? inUse(lt('wall.inUse')) : (
        <Button variant="brand" size="lg" className="w-full" loading={busy} onClick={() => void setWall(tried)}>
          {lt('wall.use')}
        </Button>
      )
    } else {
      action = worn[look!] === tried ? inUse(lt('looks.wearing')) : (
        <Button variant="brand" size="lg" className="w-full" loading={busy} onClick={() => void wear(tried)}>
          {lt('looks.wear')}
        </Button>
      )
    }
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

  /** What an item card shows: the look on my @ID (a framed photo for frames), a wall swatch, my photo or a bubble. */
  const sample = (id: ItemId | null) =>
    kind === 'frame' ? (
      <FrameAvatar frame={id as FrameId | null} name={me.name} src={me.photoUrl} size={36} />
    ) : kind === 'wall' ? (
      <span className="block h-12 w-full rounded-xl bg-chat-wall ring-1 ring-border" style={wallVars(id)} />
    ) : kind === 'bubble' ? (
      <Bubble mine tail={false} skin={id} className="max-w-full truncate px-3 py-1.5 text-[13px] font-semibold">
        {me.name}
      </Bubble>
    ) : kind === 'effect' ? (
      <Avatar name={me.name} src={me.photoUrl} effect={id} size={40} />
    ) : kind === 'badge' && id ? (
      <LookBadge id={id as BadgeId} className="size-10" />
    ) : handle && look ? (
      <LookText handle={handle} wear={wearWith(NO_WEAR, look, id)} className="text-sm font-semibold" />
    ) : null

  let preview: React.ReactNode
  if (kind === 'effect') {
    preview = (
      <div className="flex w-full flex-col items-center gap-3 rounded-[28px] bg-linear-to-b from-muted to-card px-4 pt-7 pb-5 ring-1 ring-border">
        <Avatar name={me.name} src={me.photoUrl} effect={shownExtra} size={104} />
        <p className="mt-2 text-lg font-bold">{me.name}</p>
        <p className="max-w-xs text-center text-sm leading-relaxed text-muted-foreground">{lt('effect.what')}</p>
      </div>
    )
  } else if (kind === 'wall') {
    preview = <WallPreview wall={shownWall} />
  } else if (kind === 'bubble') {
    // A short chat on the plain wall: their plain bubble, then mine in the bubble being looked at.
    preview = (
      <div className="flex w-full flex-col gap-1.5 rounded-[28px] bg-chat-wall px-4 py-5 ring-1 ring-border">
        <Bubble mine={false} tail className="self-start px-3 py-2 text-[15px]">
          👋
        </Bubble>
        <Bubble mine tail skin={shownExtra} className="max-w-[85%] self-end px-3 py-2 text-[15px] leading-snug">
          {lt('bubble.what')}
        </Bubble>
      </div>
    )
  } else if (kind === 'cycle') {
    const urls = myPhotos.filter((x) => picked.includes(x.id)).map((x) => x.url)
    preview = (
      <div className="flex w-full flex-col items-center gap-3">
        <Avatar name={me.name} src={me.photoUrl} srcs={urls.length >= 2 ? urls : myPhotos.slice(0, 4).map((x) => x.url)} size={96} />
        <p className="text-center text-sm leading-relaxed text-muted-foreground">{lt('cycle.what')}</p>
        {owned.has('cycle_photos') ? (
          myPhotos.length < 2 ? (
            <p className="text-sm font-medium text-destructive">{lt('cycle.needPhotos')}</p>
          ) : (
            <>
              <p className="text-sm font-semibold">{lt('cycle.pick')}</p>
              <ul className="flex flex-wrap justify-center gap-2">
                {myPhotos.map((x) => {
                  const on = picked.includes(x.id)
                  return (
                    <li key={x.id}>
                      <button
                        type="button"
                        aria-pressed={on}
                        disabled={!on && picked.length >= 4}
                        onClick={() => setCyclePick(on ? picked.filter((y) => y !== x.id) : [...picked, x.id])}
                        className={cn('relative block size-16 overflow-hidden rounded-xl ring-2 transition disabled:opacity-40', on ? 'ring-primary' : 'ring-transparent')}
                      >
                        <img src={x.url} alt="" className="size-full object-cover" />
                        {on ? (
                          <span className="absolute top-1 inset-e-1 grid size-5 place-items-center rounded-full bg-primary text-[11px] font-bold text-primary-foreground">
                            {picked.indexOf(x.id) + 1}
                          </span>
                        ) : null}
                      </button>
                    </li>
                  )
                })}
              </ul>
            </>
          )
        ) : null}
      </div>
    )
  } else if (kind === 'vip') {
    preview = (
      <div className="flex w-full flex-col gap-3">
        <div className="grid grid-cols-2 gap-3">
          <SeenAs label={lt('vip.strangers')} name="VIP" />
          <SeenAs label={lt('vip.friends')} name={nameAge(me.name, me.age)} src={me.photoUrl} />
        </div>
        <p className="text-sm leading-relaxed text-muted-foreground">{lt('vip.what')}</p>
      </div>
    )
  } else {
    preview = (
      <FrameCard frame={trying.frame as FrameId | null} className="w-full">
        <div className="flex flex-col items-center gap-1.5 px-4 pt-6 pb-5 text-center">
          <FrameAvatar frame={trying.frame as FrameId | null} userId={me.id} name={me.name} src={me.photoUrl} size={72} />
          <p className="mt-1 text-lg font-bold">{me.name}</p>
          {handle ? <LookText handle={handle} wear={trying} className="text-[17px] font-semibold" /> : null}
        </div>
      </FrameCard>
    )
  }
  const caption = look || extra ? lt('looks.everyone') : kind === 'wall' ? lt('wall.onlyYou') : null

  return (
    <div className="flex flex-col">
      <div className="flex flex-col gap-5 px-6 pt-3 pb-5">
        <section aria-label={lt('looks.preview')} className="flex flex-col items-center gap-2">
          {preview}
          {caption ? <p className="text-xs font-medium text-muted-foreground">{caption}</p> : null}
        </section>

        <div role="radiogroup" aria-label={lt('looks.tabs')} className="-mx-6 flex gap-2 overflow-x-auto px-6 no-scrollbar">
          {ITEM_KINDS.filter((k) => CATALOGUE[k].some((id) => sale.has(id))).map((k) => (
            <button
              key={k}
              type="button"
              role="radio"
              aria-checked={k === kind}
              onClick={() => setKind(k)}
              className={cn(
                'h-9 shrink-0 rounded-full px-4 text-sm font-semibold transition-colors duration-150',
                k === kind ? 'bg-primary-soft text-primary-text ring-1 ring-primary-text/50' : 'bg-muted text-muted-foreground hover:text-foreground',
              )}
            >
              {lt(`looks.tab.${k}`)}
            </button>
          ))}
        </div>

        {kind === 'vip' || kind === 'cycle' ? null : (
          <ul role="radiogroup" aria-label={lt(`looks.tab.${kind}`)} className="grid grid-cols-3 gap-2.5 sm:grid-cols-4">
            <li>
              <ItemCard
                on={tried === null}
                label={lt('looks.none')}
                sub={(look ? worn[look] : single?.mine) === null ? usingLabel : ''}
                worn={(look ? worn[look] : single?.mine) === null}
                onClick={() => choose(null)}
              >
                {kind === 'wall' || kind === 'frame' || extra ? sample(null) : handle ? <LookText handle={handle} wear={NO_WEAR} className="text-sm font-semibold" /> : null}
              </ItemCard>
            </li>
            {list.map((id) => (
              <li key={id}>
                <ItemCard on={tried === id} label={itemName(lt, id)} sub={status(id)} worn={look ? worn[look] === id : single?.mine === id} onClick={() => choose(id)}>
                  {sample(id)}
                </ItemCard>
              </li>
            ))}
          </ul>
        )}
        {backend.buyLook ? null : <p className="text-xs leading-relaxed text-muted-foreground">{lt('looks.appNote')}</p>}
      </div>
      <div className="sticky bottom-0 flex flex-col gap-2 border-t border-border bg-card px-6 py-4">
        {action}
        {tried !== null ? (
          <Button variant="ghost" className="w-full" onClick={() => setGift(tried)}>
            <Gift className="size-4.5" aria-hidden /> {lt('gift.button')}
          </Button>
        ) : null}
      </div>
      <GiftSheet item={gift} name={gift ? itemName(lt, gift) : ''} price={gift ? price(gift) : ''} onClose={() => setGift(null)} />
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

/** A chat on this wall (null: the plain one), the way only I will see it. */
function WallPreview({ wall }: { wall: ItemId | null }) {
  const bar = 'h-8 rounded-lg shadow-[0_1px_0.5px_rgb(11_20_26/0.13)]'
  return (
    <div aria-hidden className="flex h-44 w-full flex-col justify-end gap-1.5 overflow-hidden rounded-[28px] bg-chat-wall p-4 ring-1 ring-border" style={wallVars(wall)}>
      <span className={cn(bar, 'w-1/2 self-start rounded-ss-none bg-bubble-in')} />
      <span className={cn(bar, 'w-2/5 self-end rounded-se-none bg-bubble-out')} />
      <span className={cn(bar, 'w-3/5 self-start rounded-ss-none bg-bubble-in')} />
    </div>
  )
}

/** VIP: one of the two cards of how I am seen (strangers, friends). */
function SeenAs({ label, name, src }: { label: string; name: string; src?: string | null }) {
  return (
    <div className="flex min-w-0 flex-col items-center gap-2 rounded-3xl bg-muted px-3 pt-4 pb-3 text-center">
      <Avatar name={name} src={src} size={56} />
      <span className="w-full truncate text-[15px] font-bold">{name}</span>
      <span className="text-[11px] font-semibold tracking-wide text-muted-foreground uppercase">{label}</span>
    </div>
  )
}

/** VIP incognito's switch (once owned). */
function AnonSwitch({ on, busy, onChange }: { on: boolean; busy: boolean; onChange: (on: boolean) => void }) {
  const { lt } = useLooksT()
  return (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      disabled={busy}
      onClick={() => onChange(!on)}
      className="flex w-full items-center gap-3 rounded-2xl bg-muted px-4 py-3 text-start disabled:opacity-70"
    >
      <span className="flex min-w-0 flex-1 flex-col">
        <span className="text-[15px] font-semibold">{lt('vip.toggle')}</span>
        <span className="mt-0.5 text-xs leading-relaxed text-muted-foreground">{lt('vip.toggleBody')}</span>
      </span>
      <span aria-hidden className={cn('relative h-6 w-10 shrink-0 rounded-full transition-colors duration-150', on ? 'bg-primary' : 'bg-input')}>
        <span className={cn('absolute top-0.5 size-5 rounded-full bg-white shadow transition-[inset-inline-start] duration-150', on ? 'inset-s-[1.125rem]' : 'inset-s-0.5')} />
      </span>
    </button>
  )
}
