// The demo's IDs and looks (040), in their own chunk like the demo's trust: fetched the first time a name with an ID
// is on screen. Buying is instant here (the live web has no payments).
import { BackendError, type Look, type LookWear, type Profile, type Shop, type ShopItem } from './backend'
import { handleProblem, handleSpelling, type HandleCheck } from './handle'
import { isItem, kindOf, NO_WEAR, wearOf, type ItemId } from './looks'
import { stickerOf } from './stickers'

const wait = (ms: number) => new Promise<void>((r) => setTimeout(r, ms))
const COOLDOWN_MS = 30 * 24 * 3_600_000

/** cosmetic_items as the migration fills it. */
const PRICES: [ItemId, number][] = [
  ['badge_crown', 99], ['badge_diamond', 149], ['badge_flame', 79], ['badge_star', 79], ['badge_heart', 79], ['badge_bolt', 79],
  ['color_gold', 49], ['color_rose', 49], ['color_ocean', 49], ['color_emerald', 49], ['color_violet', 49],
  ['color_sunset', 99], ['color_aurora', 99], ['color_rainbow', 129],
  ['font_script', 69], ['font_retro', 69], ['font_bold', 69], ['font_pixel', 69], ['font_round', 69], ['font_future', 69],
  ['frame_frost', 199], ['frame_fire', 199], ['frame_jewel', 249], ['frame_sand', 199], ['frame_neon', 149], ['frame_sakura', 149],
  ['wall_saffron', 79], ['wall_midnight', 79], ['wall_paisley', 79], ['wall_mehndi', 79], ['wall_stars', 79], ['wall_waves', 79],
  ['sticker_namaste', 49], ['sticker_chai', 49], ['sticker_cricket', 49], ['sticker_love', 49],
  ['vip_anon', 299],
  ['cycle_photos', 199],
]

/** The demo people who wear something (everyone else shows a plain @ID). */
const WORN: Record<string, Partial<LookWear>> = {
  u_sofia: { badge: 'badge_heart', color: 'color_rose', font: 'font_script', frame: 'frame_sakura' },
  u_arjun: { badge: 'badge_bolt', color: 'color_ocean', font: 'font_bold', frame: 'frame_neon' },
  u_yuki: { badge: 'badge_star', color: 'color_aurora', font: 'font_pixel', frame: 'frame_frost' },
  u_amara: { badge: 'badge_crown', color: 'color_gold', frame: 'frame_jewel' },
  u_mateo: { badge: 'badge_flame', color: 'color_sunset', font: 'font_retro', frame: 'frame_fire' },
  u_kabir: { color: 'color_emerald', font: 'font_future', frame: 'frame_sand' },
  u_leila: { badge: 'badge_diamond', color: 'color_violet', font: 'font_round' },
  u_chloe: { color: 'color_rainbow' },
}

/** A steady number per id (the demo's people keep their look across visits). */
function hash(id: string) {
  let h = 0
  for (const ch of id) h = (h * 31 + ch.charCodeAt(0)) | 0
  return Math.abs(h)
}

/** A room person (v_priya_3): one in three wears a colour, some a badge too. */
function roomWear(id: string): LookWear {
  const h = hash(id)
  if (h % 3) return NO_WEAR
  const colors: ItemId[] = ['color_gold', 'color_rose', 'color_ocean', 'color_emerald', 'color_violet', 'color_sunset', 'color_aurora']
  const badges: (ItemId | null)[] = ['badge_star', 'badge_heart', null, 'badge_flame', null]
  return { ...NO_WEAR, color: colors[h % colors.length], badge: badges[h % badges.length] }
}

export interface LookHost {
  me: () => Profile | null
  setMe: (p: Profile) => void
  /** A demo person's free ID (main people and room people); null: nobody the demo knows. */
  handle: (id: string) => string | null
}

export class LookSim {
  private owned = new Set<ItemId>()
  private wear: LookWear = NO_WEAR
  private wall: ItemId | null = null
  private anon = false
  /** The photo gradient's photos (urls). */
  private cycle: string[] = []
  /** When I last chose an ID myself (null: the free one the app gave). */
  private handleSetAt: number | null = null

  constructor(private host: LookHost) {}

  private handleOf(id: string): string | null {
    const me = this.host.me()
    if (me && id === me.id) return me.handle ?? null
    return this.host.handle(id)
  }

  /** IDs others have: the demo people's, and a few common ones so "taken" can be tried. */
  private taken(h: string) {
    const low = h.toLowerCase()
    if (['priya', 'rahul', 'lisa', 'ananya', 'arjun'].includes(low)) return true
    return ['u_ananya', 'u_kabir', 'u_sofia', 'u_mateo', 'u_yuki', 'u_amara', 'u_lucas', 'u_leila', 'u_arjun', 'u_chloe', 'u_omar', 'u_riya']
      .some((id) => this.handleOf(id)?.toLowerCase() === low)
  }

  private myLook(): Look | null {
    const me = this.host.me()
    return me?.handle ? { id: me.id, handle: me.handle, ...this.wear, ...(this.cycle.length >= 2 ? { cycle: this.cycle } : {}) } : null
  }

  async handleCheck(raw: string): Promise<HandleCheck> {
    await wait(250)
    const h = handleSpelling(raw)
    const mine = this.host.me()?.handle ?? null
    if (handleProblem(h, mine) === 'invalid') return 'invalid'
    if (h === mine) return 'mine'
    if (handleProblem(h, mine) === 'reserved') return 'reserved'
    return this.taken(h) ? 'taken' : 'ok'
  }

  async setHandle(raw: string): Promise<string> {
    await wait(400)
    const me = this.host.me()
    if (!me) throw new BackendError('not_found')
    const h = handleSpelling(raw)
    const problem = handleProblem(h, me.handle)
    if (problem) throw new BackendError(`handle_${problem}`)
    if (h === me.handle) return h
    if (this.taken(h)) throw new BackendError('handle_taken')
    const sameId = h.toLowerCase() === me.handle?.toLowerCase()
    if (!sameId && this.handleSetAt !== null && Date.now() - this.handleSetAt < COOLDOWN_MS) throw new BackendError('handle_cooldown')
    if (!sameId) this.handleSetAt = Date.now()
    this.host.setMe({ ...me, handle: h })
    return h
  }

  async shop(): Promise<Shop> {
    await wait(300)
    const items: ShopItem[] = PRICES.map(([id, priceInr]) => ({ id, kind: kindOf(id), priceInr }))
    return {
      items, owned: [...this.owned], look: this.myLook(), handleNext: this.handleSetAt === null ? null : this.handleSetAt + COOLDOWN_MS,
      handlePasses: 0, wall: this.wall, anon: this.anon,
    }
  }

  async setWall(id: string | null) {
    await wait(250)
    if (id !== null && (!isItem(id) || kindOf(id) !== 'wall' || !this.owned.has(id))) throw new BackendError('not_owned')
    this.wall = id
  }

  async setAnon(on: boolean) {
    await wait(250)
    if (on && !this.owned.has('vip_anon')) throw new BackendError('not_owned')
    this.anon = on
  }

  /** Like set_cycle: 2–4 of my photos (photoPath or a gallery photo's id), or none. */
  async setCycle(paths: string[] | null) {
    await wait(250)
    const me = this.host.me()
    if (!me) throw new BackendError('not_found')
    if (!paths?.length) return void (this.cycle = [])
    if (!this.owned.has('cycle_photos')) throw new BackendError('not_owned')
    const url = (p: string) => (p === me.photoPath ? me.photoUrl : me.photos?.find((x) => x.id === p)?.url)
    const urls = [...new Set(paths)].map(url)
    if (urls.length < 2 || urls.length > 4 || urls.some((u) => !u)) throw new BackendError('bad_request')
    this.cycle = urls as string[]
  }

  /** Like chat_body_check: a sticker needs its pack. */
  checkBody(text: string | null) {
    const st = stickerOf(text)
    if (st && !this.owned.has(st.pack as ItemId)) throw new BackendError('sticker_not_owned')
  }

  async equipLook(next: LookWear): Promise<Look> {
    await wait(300)
    for (const id of Object.values(next)) {
      if (id === null) continue
      if (!isItem(id)) throw new BackendError('bad_request')
      if (!this.owned.has(id)) throw new BackendError('not_owned')
    }
    if ((Object.entries(next) as [keyof LookWear, string | null][]).some(([kind, id]) => id && kindOf(id as ItemId) !== kind)) throw new BackendError('bad_request')
    this.wear = wearOf(next)
    const look = this.myLook()
    if (!look) throw new BackendError('not_found')
    return look
  }

  /** Like redeem_cosmetic: owned for good, and the first look of a kind is worn at once (walls are chosen by hand). */
  async buy(id: string) {
    await wait(600)
    if (!isItem(id)) throw new BackendError('bad_request')
    if (this.owned.has(id)) return
    this.owned.add(id)
    const kind = kindOf(id)
    if (kind in this.wear && !this.wear[kind as keyof LookWear]) this.wear = { ...this.wear, [kind]: id }
  }

  async looks(ids: string[]): Promise<Look[]> {
    await wait(200)
    const me = this.host.me()
    return [...new Set(ids)].slice(0, 100).flatMap((id) => {
      if (me && id === me.id) return this.myLook() ?? []
      const handle = this.handleOf(id)
      if (!handle) return []
      const wear = id.startsWith('v_') ? roomWear(id) : { ...NO_WEAR, ...WORN[id] }
      return [{ id, handle, ...wear }]
    })
  }
}
