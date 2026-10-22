// "Asha is typing…" in a room: typing signals arrive over a public channel (vtyping-<room>), like random chat's.
// Nothing is stored; someone counts as typing for a few seconds after their last signal.

/** A signal goes out at most this often while the text in the box changes. */
export const TYPING_SEND_MS = 2500
/** Someone stops counting as typing this long after their last signal. */
export const TYPING_TTL_MS = 4000

export type TypingSeen = Map<string, { name: string; at: number }>

/**
 * The people typing now (user ids), oldest signal first: never me, only people still in the room's member list (a
 * signal from someone who left, or from outside the room, is ignored), and only fresh signals. The screen shows the
 * names the room's member list has for them, not the name a signal carried.
 */
export function typingIds(seen: TypingSeen, now: number, present: ReadonlySet<string>, me: string): string[] {
  return [...seen.entries()]
    .filter(([id, s]) => id !== me && present.has(id) && now - s.at < TYPING_TTL_MS)
    .sort((a, b) => a[1].at - b[1].at)
    .map(([id]) => id)
}

/** When the next of these signals runs out (to re-render then), or null when none is fresh. */
export function nextExpiry(seen: TypingSeen, now: number): number | null {
  let next: number | null = null
  for (const s of seen.values()) {
    const end = s.at + TYPING_TTL_MS
    if (end > now && (next === null || end < next)) next = end
  }
  return next
}
