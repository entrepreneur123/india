// Chat walls (046): my background behind the messages of my chats (only I see it). Each is a CSS background: a
// gradient, most with a faint pattern tile (inline SVG) over it. Kept quiet enough that the bubbles (white / soft
// orange) stay easy to read. No imports but types (node --test).
import type { CSSProperties } from 'react'
import type { WallId } from './looks'

/** A pattern tile as a CSS image: the SVG's markup inside a w×h box. */
const tile = (w: number, h: number, body: string) =>
  `url("data:image/svg+xml,${encodeURIComponent(`<svg xmlns='http://www.w3.org/2000/svg' width='${w}' height='${h}'>${body}</svg>`)}")`

const paisley = (c: string, o: number) =>
  tile(
    96,
    96,
    `<g fill='none' stroke='${c}' stroke-opacity='${o}' stroke-width='1.4'><path d='M30 10c12 4 18 16 14 28s-18 18-26 10c-5-5-3-13 3-14 5-1 8 4 5 7'/><path d='M30 16c8 4 11 12 8 20'/><path d='M78 52c-10 4-14 15-10 25s15 13 21 6c4-5 2-11-3-11-4 0-6 4-3 6' /><circle cx='70' cy='18' r='2'/><circle cx='20' cy='76' r='2'/></g><g fill='${c}' fill-opacity='${o}'><circle cx='48' cy='54' r='1.6'/><circle cx='86' cy='30' r='1.2'/><circle cx='8' cy='40' r='1.2'/></g>`,
  )

const mehndi = (c: string, o: number) =>
  tile(
    72,
    72,
    `<g fill='none' stroke='${c}' stroke-opacity='${o}' stroke-width='1.2'><circle cx='36' cy='36' r='5'/><circle cx='36' cy='36' r='14'/>${[0, 45, 90, 135, 180, 225, 270, 315]
      .map((a) => `<ellipse cx='36' cy='18' rx='4' ry='8' transform='rotate(${a} 36 36)'/>`)
      .join('')}</g><g fill='${c}' fill-opacity='${o}'><circle cx='0' cy='0' r='2'/><circle cx='72' cy='0' r='2'/><circle cx='0' cy='72' r='2'/><circle cx='72' cy='72' r='2'/><circle cx='36' cy='36' r='1.6'/></g>`,
  )

const stars = (o: number) =>
  tile(
    140,
    140,
    `<g fill='#fff' fill-opacity='${o}'><circle cx='12' cy='20' r='1'/><circle cx='54' cy='8' r='0.8'/><circle cx='98' cy='30' r='1.3'/><circle cx='128' cy='12' r='0.8'/><circle cx='30' cy='70' r='0.9'/><circle cx='76' cy='60' r='0.7'/><circle cx='118' cy='84' r='1'/><circle cx='18' cy='118' r='1.2'/><circle cx='64' cy='104' r='0.8'/><circle cx='104' cy='128' r='0.9'/></g><g fill='#FFB24D' fill-opacity='${o}'><path d='M84 92l1.5 4.5 4.5 1.5-4.5 1.5-1.5 4.5-1.5-4.5-4.5-1.5 4.5-1.5z'/><path d='M40 34l1 3 3 1-3 1-1 3-1-3-3-1 3-1z'/></g>`,
  )

const waves = (c: string, o: number) =>
  tile(
    120,
    48,
    `<g fill='none' stroke='${c}' stroke-opacity='${o}' stroke-width='1.6' stroke-linecap='round'><path d='M0 14q15-10 30 0t30 0 30 0 30 0'/><path d='M-30 38q15-10 30 0t30 0 30 0 30 0 30 0'/></g>`,
  )

export const WALL_DESIGNS: Record<WallId, string> = {
  wall_saffron: `radial-gradient(120% 70% at 85% 0%, #FFD08A 0%, transparent 60%), linear-gradient(170deg, #FFF1DE 0%, #FFD9A6 100%)`,
  wall_midnight: `radial-gradient(60% 40% at 82% 8%, rgb(234 106 0 / 0.28) 0%, transparent 70%), linear-gradient(180deg, #1B2140 0%, #343A66 100%)`,
  wall_paisley: `${paisley('#C2620A', 0.3)}, linear-gradient(180deg, #FFF8EE 0%, #FBE9D2 100%)`,
  wall_mehndi: `${mehndi('#8A4B1F', 0.26)}, linear-gradient(180deg, #F7EAD7 0%, #EFD9BC 100%)`,
  wall_stars: `${stars(0.75)}, radial-gradient(90% 60% at 50% 0%, #3E3A7A 0%, transparent 70%), linear-gradient(180deg, #1A1B44 0%, #2C2559 100%)`,
  wall_waves: `${waves('#3D97A3', 0.28)}, linear-gradient(180deg, #EEF8F8 0%, #D5ECEE 100%)`,
}

/** A wall this app can draw, or null (none, or one a newer server sells). */
export const wallOf = (id: unknown): string | null =>
  typeof id === 'string' && Object.hasOwn(WALL_DESIGNS, id) ? WALL_DESIGNS[id as WallId] : null

/** A wall's inline style (over the plain `bg-chat-wall`); undefined for none. */
export function wallVars(id: unknown): CSSProperties | undefined {
  const w = wallOf(id)
  return w ? { background: w } : undefined
}
