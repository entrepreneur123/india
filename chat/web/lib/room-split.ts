// How a room's screen is shared between the room (stage, music, listeners) and its chat (docs/LOOKS_SPEC.md §1.3):
// talk rooms give the chat the most room, music rooms the player. People can change it; the choice is kept per
// category and device kind on this device. No imports but types, so the unit tests run it directly (node --test).
import type { RoomKind } from './backend'

/** Phones: the room's share of the height. 0 is chat only (the room folds to a strip). */
export const PHONE_SNAPS = [0, 0.3, 0.5, 0.7] as const
export const PHONE_MAX = 0.7
export const PHONE_DEFAULT: Record<RoomKind, number> = { talk: 0.3, music: 0.55 }

/** Wide screens: the chat column's width, 300 px up to 60 % of the room's width; arrow keys move it 32 px. */
export const WIDE_MIN = 300
export const WIDE_MAX_SHARE = 0.6
export const WIDE_STEP = 32
/** Talk rooms start with half the width for the chat, music rooms with a narrow chat beside the player. */
export const wideDefault = (kind: RoomKind, total: number) => clampWidth(kind === 'talk' ? total * 0.5 : 340, total)

export const splitKey = (kind: RoomKind, device: 'phone' | 'wide') => `yaari_room_split_${kind}_${device}`

/** The nearest phone snap point to a dragged share. */
export function nearestSnap(share: number): number {
  return PHONE_SNAPS.reduce((best, s) => (Math.abs(s - share) < Math.abs(best - share) ? s : best), PHONE_SNAPS[0])
}

/** The next snap point up (`dir` 1: a bigger room) or down (-1) from a share, for the keyboard. */
export function stepSnap(share: number, dir: 1 | -1): number {
  const list = dir > 0 ? PHONE_SNAPS.filter((s) => s > share + 0.01) : PHONE_SNAPS.filter((s) => s < share - 0.01).reverse()
  return list[0] ?? share
}

export const clampShare = (share: number) => Math.min(PHONE_MAX, Math.max(0, share))

export function clampWidth(px: number, total: number): number {
  const max = Math.max(WIDE_MIN, total * WIDE_MAX_SHARE)
  return Math.round(Math.min(max, Math.max(WIDE_MIN, px)))
}

/** A stored phone share: one of the snap points or a room's default, else null (use the default). */
export function readShare(raw: string | null): number | null {
  const n = raw === null ? NaN : Number(raw)
  return Number.isFinite(n) && n >= 0 && n <= PHONE_MAX ? n : null
}

/** A stored chat width in px, else null. */
export function readWidth(raw: string | null): number | null {
  const n = raw === null ? NaN : Number(raw)
  return Number.isFinite(n) && n >= WIDE_MIN ? n : null
}
