// Chat walls (046, redrawn as moving scenes 067): the background behind the messages of my chats. Each is drawn in
// app/globals.css (.wall-<name>): a base and up to two layers that move (transform only, so the GPU does it; still
// with reduced motion). In a random chat I can also use my partner's wall for that chat (36). No imports but types
// (node --test).
import type { WallId } from './looks'

/** Dark walls: the chat's notices and the text box's row get a veil so they stay readable. */
export const WALL_DESIGNS: Record<WallId, { dark: boolean }> = {
  wall_aurora: { dark: true },
  wall_neon: { dark: true },
  wall_sakura: { dark: false },
  wall_galaxy: { dark: true },
  wall_ocean: { dark: true },
  wall_lava: { dark: true },
  wall_rain: { dark: true },
  wall_diya: { dark: true },
}

/** A wall this app can draw, or null (none, or one a newer server sells). */
export const wallOf = (id: unknown): WallId | null => (typeof id === 'string' && Object.hasOwn(WALL_DESIGNS, id) ? (id as WallId) : null)

/** The wall's classes (over the plain `bg-chat-wall`); undefined for none. */
export function wallClass(id: unknown): string | undefined {
  const w = wallOf(id)
  return w ? `wall wall-${w.slice(5)}` : undefined
}
