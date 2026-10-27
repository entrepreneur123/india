// Looks (040_handles_looks.sql): the catalogue the apps can draw, the ID colours and fonts, and the reading of shop()
// and looks(). The same ids, colours and fonts as the Android app (docs/LOOKS_SPEC.md); badges and frames are drawn in
// components/looks/, chat walls in lib/walls.ts (046), chat bubbles below (062). No imports but types, so the
// unit tests run it directly (node --test).
import type { ItemKind, Look, LookKind, LookWear, Shop, ShopItem } from './backend'

/** In the shop's order (cosmetic_items.sort). */
export const BADGES = ['badge_crown', 'badge_diamond', 'badge_flame', 'badge_star', 'badge_heart', 'badge_bolt'] as const
export const COLORS = ['color_gold', 'color_rose', 'color_ocean', 'color_emerald', 'color_violet', 'color_sunset', 'color_aurora', 'color_rainbow'] as const
export const FONTS = ['font_script', 'font_retro', 'font_bold', 'font_pixel', 'font_round', 'font_future'] as const
export const FRAMES = ['frame_frost', 'frame_fire', 'frame_jewel', 'frame_sand', 'frame_neon', 'frame_sakura'] as const
/** 046: my chat background (only I see it), the incognito mode. */
export const WALLS = ['wall_aurora', 'wall_neon', 'wall_sakura', 'wall_galaxy', 'wall_ocean', 'wall_lava', 'wall_rain', 'wall_diya'] as const
export const VIP_ITEMS = ['vip_anon'] as const
/** 062: the profile photo gradient. */
export const CYCLE_ITEMS = ['cycle_photos'] as const
/** 062: light moving over the profile photo. */
export const EFFECTS = ['effect_aurora', 'effect_prism'] as const
export type EffectId = (typeof EFFECTS)[number]
/** 062: my chat bubbles, as everyone sees them. */
export const BUBBLES = ['bubble_peach', 'bubble_ocean', 'bubble_mint', 'bubble_galaxy', 'bubble_gold', 'bubble_neon'] as const
export type BubbleId = (typeof BUBBLES)[number]

export type BadgeId = (typeof BADGES)[number]
export type ColorId = (typeof COLORS)[number]
export type FontId = (typeof FONTS)[number]
export type FrameId = (typeof FRAMES)[number]
export type WallId = (typeof WALLS)[number]
export type ItemId = BadgeId | ColorId | FontId | FrameId | WallId | (typeof VIP_ITEMS)[number] | (typeof CYCLE_ITEMS)[number] | EffectId | BubbleId

export const LOOK_KINDS: LookKind[] = ['badge', 'color', 'font', 'frame']
/** The shop's tabs, in order. */
export const ITEM_KINDS: ItemKind[] = [...LOOK_KINDS, 'effect', 'bubble', 'cycle', 'wall', 'vip']
export const CATALOGUE: Record<ItemKind, readonly ItemId[]> = {
  badge: BADGES, color: COLORS, font: FONTS, frame: FRAMES, wall: WALLS, vip: VIP_ITEMS, cycle: CYCLE_ITEMS, effect: EFFECTS, bubble: BUBBLES,
}
const ORDER: string[] = Object.values(CATALOGUE).flat()
const KNOWN = new Set(ORDER)

/** An item this app can draw (a newer server may sell more: those are left out until the app knows them). */
export const isItem = (id: unknown): id is ItemId => typeof id === 'string' && KNOWN.has(id)
/** The kind is the id's first word (badge_crown → badge), as the server checks. */
export const kindOf = (id: ItemId): ItemKind => id.slice(0, id.indexOf('_')) as ItemKind
/** A bubble's design, or null for one this app doesn't know (or none). */
export const bubbleOf = (id: unknown): BubbleDesign | null =>
  typeof id === 'string' && Object.hasOwn(BUBBLE_DESIGNS, id) ? BUBBLE_DESIGNS[id as BubbleId] : null

/**
 * ID colours: one tone (≥ 4.5:1 on white) or a horizontal gradient; the rainbow slides slowly (6 s, still with
 * reduced motion).
 */
export type ColorDesign = string | { stops: string[]; slide?: boolean }
/**
 * Chat bubbles (062): the background, its top colour (the tail is drawn in it), the text and time colours, an outline
 * or glow, and how it moves (gold's light sweeps across, neon's glow breathes). Text is at least 4.5:1 on every stop.
 */
export interface BubbleDesign { bg: string; top: string; text: string; meta: string; shadow?: string; motion?: 'bubble-sheen' | 'bubble-glow' }
const STARS = [
  [18, 28, 1], [72, 20, 1], [42, 72, 1.5], [88, 64, 1], [8, 82, 1], [58, 44, 1], [30, 12, 1.5],
].map(([x, y, r]) => `radial-gradient(${r}px ${r}px at ${x}% ${y}%, rgb(255 255 255 / 0.7) 99%, transparent)`).join(', ')
export const BUBBLE_DESIGNS: Record<BubbleId, BubbleDesign> = {
  bubble_peach: { bg: 'linear-gradient(160deg, #FFE3D3, #FFC2D4 60%, #FFB1CF)', top: '#FFE3D3', text: '#4A1530', meta: '#8A3B5C' },
  bubble_ocean: { bg: 'linear-gradient(160deg, #1976D2, #3346C4 60%, #4A2FB8)', top: '#1976D2', text: '#FFFFFF', meta: 'rgb(255 255 255 / 0.8)' },
  bubble_mint: { bg: 'linear-gradient(160deg, #E6FCEE, #BDF3D8 60%, #A6EBD0)', top: '#E6FCEE', text: '#053D2E', meta: '#2F7A60' },
  bubble_galaxy: { bg: `${STARS}, linear-gradient(160deg, #3B1D8F, #24186B 55%, #15123F)`, top: '#3B1D8F', text: '#F3F0FF', meta: '#BBA9FF' },
  bubble_gold: {
    bg: 'linear-gradient(105deg, transparent 38%, rgb(255 255 255 / 0.7) 50%, transparent 62%), linear-gradient(160deg, #FFF3C4, #F6CB5C 55%, #E2A72B)',
    top: '#FFF3C4', text: '#3A2600', meta: '#6E4D0B', shadow: '0 1px 2px rgb(120 80 0 / 0.35)', motion: 'bubble-sheen',
  },
  bubble_neon: { bg: 'linear-gradient(160deg, #111A2E, #0B1120)', top: '#111A2E', text: '#E6FDFF', meta: '#6FE7F5', motion: 'bubble-glow' },
}

export const COLOR_DESIGNS: Record<ColorId, ColorDesign> = {
  color_gold: '#A87400',
  color_rose: '#C2185B',
  color_ocean: '#0277BD',
  color_emerald: '#00796B',
  color_violet: '#6A1B9A',
  color_sunset: { stops: ['#FF6A3D', '#FF2E8B', '#A63DFF'] },
  color_aurora: { stops: ['#00C9A7', '#3D8BFF', '#B44DFF'] },
  color_rainbow: { stops: ['#FF4D4D', '#FFA53D', '#F5D300', '#34C759', '#3DB8FF', '#9B5CFF'], slide: true },
}

/**
 * ID fonts: the family inside each bundled file (public/fonts/looks, @font-face in app/globals.css) and the size it is
 * drawn at (the pixel font is wide). Latin letters only; other scripts fall back to the app's font by themselves.
 */
export const FONT_DESIGNS: Record<FontId, { family: string; scale: number }> = {
  font_script: { family: 'Yaari Look Script', scale: 1 },
  font_retro: { family: 'Yaari Look Retro', scale: 1 },
  font_bold: { family: 'Yaari Look Bold', scale: 1 },
  font_pixel: { family: 'Yaari Look Pixel', scale: 0.8 },
  font_round: { family: 'Yaari Look Round', scale: 1 },
  font_future: { family: 'Yaari Look Future', scale: 1 },
}

/** Where looks are bought (the web has no payments). */
/** 42: an item's rarity in the shop, read from its price so the two never disagree: rare under ₹99, epic under ₹199. */
export type Rarity = 'rare' | 'epic' | 'legendary'
export const rarityOf = (inr: number): Rarity => (inr >= 199 ? 'legendary' : inr >= 99 ? 'epic' : 'rare')

export const PLAY_URL = 'https://play.google.com/store/apps/details?id=com.yaari.chat'

export const NO_WEAR: LookWear = { badge: null, color: null, font: null, frame: null }

/** What a look wears, as equip_look takes it. */
export const wearOf = (look: Pick<Look, LookKind> | null | undefined): LookWear =>
  look ? { badge: look.badge, color: look.color, font: look.font, frame: look.frame } : NO_WEAR

/** The wear with this item on (or, with null, nothing of that kind). */
export const wearWith = (wear: LookWear, kind: LookKind, id: ItemId | null): LookWear => ({ ...wear, [kind]: id })

const item = (v: unknown, kind: ItemKind) => (isItem(v) && kindOf(v) === kind ? v : null)

/** One look_json() row; null when it isn't one. Items of the wrong kind or unknown to this app are left off. */
export function parseLook(raw: unknown): Look | null {
  if (!raw || typeof raw !== 'object') return null
  const r = raw as Record<string, unknown>
  if (typeof r.id !== 'string' || typeof r.handle !== 'string' || !r.handle) return null
  const cycle = Array.isArray(r.cycle) ? r.cycle.filter((x): x is string => typeof x === 'string').slice(0, 5) : []
  return {
    id: r.id, handle: r.handle, badge: item(r.badge, 'badge'), color: item(r.color, 'color'), font: item(r.font, 'font'), frame: item(r.frame, 'frame'),
    ...(cycle.length >= 2 ? { cycle } : {}),
    ...(isItem(r.effect) && kindOf(r.effect) === 'effect' ? { effect: r.effect } : {}),
    ...(isItem(r.bubble) && kindOf(r.bubble) === 'bubble' ? { bubble: r.bubble } : {}),
    ...(isItem(r.wall) && kindOf(r.wall) === 'wall' ? { wall: r.wall } : {}),
  }
}

export function parseLooks(raw: unknown): Look[] {
  return Array.isArray(raw) ? raw.flatMap((r) => parseLook(r) ?? []) : []
}

/** shop(): the items this app knows, in catalogue order; `time` reads the server's timestamps. */
export function parseShop(raw: unknown, time: (ts: string) => number): Shop | null {
  if (!raw || typeof raw !== 'object') return null
  const r = raw as Record<string, unknown>
  const items: ShopItem[] = (Array.isArray(r.items) ? r.items : []).flatMap((x) => {
    const i = x as Record<string, unknown> | null
    if (!i || !isItem(i.id) || i.kind !== kindOf(i.id) || typeof i.price_inr !== 'number') return []
    return [{ id: i.id, kind: kindOf(i.id), priceInr: i.price_inr }]
  })
  items.sort((a, b) => ORDER.indexOf(a.id) - ORDER.indexOf(b.id))
  const next = typeof r.handle_next === 'string' ? time(r.handle_next) : NaN
  return {
    items,
    owned: (Array.isArray(r.owned) ? r.owned : []).filter(isItem),
    look: parseLook(r.look),
    handleNext: Number.isFinite(next) ? next : null,
    handlePasses: typeof r.handle_passes === 'number' ? r.handle_passes : 0,
    wall: item(r.wall, 'wall'),
    anon: r.anon === true,
  }
}

/**
 * 046 incognito: how the server shows someone in VIP mode to strangers (name "VIP", age 0, no photo, no ID). A room
 * line's sender carries no age, so there the name and the missing photo tell.
 */
export const isVip = (p: { name: string; age?: number; photoUrl?: string | null }) => p.name === 'VIP' && !p.age && !p.photoUrl

/** "Name, 24", or only the name when the age is hidden (0: VIP incognito). */
export const nameAge = (name: string, age: number) => (age > 0 ? `${name}, ${age}` : name)
/** The age, or nothing when it is hidden (VIP). Cities are not shown (the owner, 2026-10-26: country only). */
export const ageText = (p: { age: number }) => (p.age > 0 ? String(p.age) : '')
