// Looks (040_handles_looks.sql): the catalogue the apps can draw, the ID colours and fonts, and the reading of shop()
// and looks(). The same ids, colours and fonts as the Android app (docs/LOOKS_SPEC.md); badges and frames are drawn in
// components/looks/. No imports but types, so the unit tests run it directly (node --test).
import type { Look, LookKind, LookWear, Shop, ShopItem } from './backend'

/** In the shop's order (cosmetic_items.sort). */
export const BADGES = ['badge_crown', 'badge_diamond', 'badge_flame', 'badge_star', 'badge_heart', 'badge_bolt'] as const
export const COLORS = ['color_gold', 'color_rose', 'color_ocean', 'color_emerald', 'color_violet', 'color_sunset', 'color_aurora', 'color_rainbow'] as const
export const FONTS = ['font_script', 'font_retro', 'font_bold', 'font_pixel', 'font_round', 'font_future'] as const
export const FRAMES = ['frame_frost', 'frame_fire', 'frame_jewel', 'frame_sand', 'frame_neon', 'frame_sakura'] as const

export type BadgeId = (typeof BADGES)[number]
export type ColorId = (typeof COLORS)[number]
export type FontId = (typeof FONTS)[number]
export type FrameId = (typeof FRAMES)[number]
export type ItemId = BadgeId | ColorId | FontId | FrameId

export const LOOK_KINDS: LookKind[] = ['badge', 'color', 'font', 'frame']
export const CATALOGUE: Record<LookKind, readonly ItemId[]> = { badge: BADGES, color: COLORS, font: FONTS, frame: FRAMES }
const KNOWN = new Set<string>([...BADGES, ...COLORS, ...FONTS, ...FRAMES])

/** An item this app can draw (a newer server may sell more: those are left out until the app knows them). */
export const isItem = (id: unknown): id is ItemId => typeof id === 'string' && KNOWN.has(id)
/** The kind is the id's first word (badge_crown → badge), as the server checks. */
export const kindOf = (id: ItemId): LookKind => id.slice(0, id.indexOf('_')) as LookKind

/**
 * ID colours: one tone for light screens and one for night screens, or a horizontal gradient (the same in both);
 * the rainbow slides slowly (6 s, still with reduced motion).
 */
export type ColorDesign = { light: string; dark: string } | { stops: string[]; slide?: boolean }
export const COLOR_DESIGNS: Record<ColorId, ColorDesign> = {
  color_gold: { light: '#A87400', dark: '#FFD54A' },
  color_rose: { light: '#C2185B', dark: '#FF8AB3' },
  color_ocean: { light: '#0277BD', dark: '#4FC3F7' },
  color_emerald: { light: '#00796B', dark: '#4DD0A8' },
  color_violet: { light: '#6A1B9A', dark: '#CE93D8' },
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
export const PLAY_URL = 'https://play.google.com/store/apps/details?id=com.yaari.chat'

export const NO_WEAR: LookWear = { badge: null, color: null, font: null, frame: null }

/** What a look wears, as equip_look takes it. */
export const wearOf = (look: Pick<Look, LookKind> | null | undefined): LookWear =>
  look ? { badge: look.badge, color: look.color, font: look.font, frame: look.frame } : NO_WEAR

/** The wear with this item on (or, with null, nothing of that kind). */
export const wearWith = (wear: LookWear, kind: LookKind, id: ItemId | null): LookWear => ({ ...wear, [kind]: id })

const item = (v: unknown, kind: LookKind) => (isItem(v) && kindOf(v) === kind ? v : null)

/** One look_json() row; null when it isn't one. Items of the wrong kind or unknown to this app are left off. */
export function parseLook(raw: unknown): Look | null {
  if (!raw || typeof raw !== 'object') return null
  const r = raw as Record<string, unknown>
  if (typeof r.id !== 'string' || typeof r.handle !== 'string' || !r.handle) return null
  return { id: r.id, handle: r.handle, badge: item(r.badge, 'badge'), color: item(r.color, 'color'), font: item(r.font, 'font'), frame: item(r.frame, 'frame') }
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
  const order = [...BADGES, ...COLORS, ...FONTS, ...FRAMES] as string[]
  items.sort((a, b) => order.indexOf(a.id) - order.indexOf(b.id))
  const next = typeof r.handle_next === 'string' ? time(r.handle_next) : NaN
  return {
    items,
    owned: (Array.isArray(r.owned) ? r.owned : []).filter(isItem),
    look: parseLook(r.look),
    handleNext: Number.isFinite(next) ? next : null,
  }
}
