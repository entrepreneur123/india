import type { VoiceRoom } from './backend'

/**
 * 045: with the room in view, or on a seat, hosting or co-hosting, I beat every 3 s (voice_heartbeat3); after 6 s
 * without one the server shows me as lost, 7 s later it takes me out. A listener with the room in the background beats
 * every 20 s (voice_heartbeat2): out after 90 s, as a phone pauses a page in the background (a short switch to another
 * app mustn't drop them).
 */
export const VOICE_BEAT_MS = 3_000
export const VOICE_LISTENER_BEAT_MS = 20_000

/** Fast: anyone with the room in view, and, in the background too, whom the room hears or who may take it over. */
export const beatsFast = (r: Pick<VoiceRoom, 'members'> | null, me: string, visible: boolean) => {
  if (visible) return true
  const m = r?.members.find((x) => x.profile.id === me)
  return !m || m.seat !== null || m.role === 'host' || m.role === 'cohost'
}

/**
 * Back after a gap this long (since my last beat, of that kind), the server has already taken me out: close the room
 * instead of re-entering silently. 85 s leaves the 90 s rule room for the clocks and the request.
 */
export const droppedOut = (lastBeat: number, now: number, fast = true) => now - lastBeat > (fast ? 13_000 : 85_000)

/**
 * Who takes over when the host leaves (voice_hand_over): the co-host who came in first and is still connected. None:
 * leaving ends the room.
 */
export const nextHost = (r: Pick<VoiceRoom, 'hostId' | 'members'>) =>
  r.members
    .filter((m) => m.role === 'cohost' && !m.lost && m.profile.id !== r.hostId)
    .sort((a, b) => (a.joinedAt ?? 0) - (b.joinedAt ?? 0))[0] ?? null
