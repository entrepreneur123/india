import type { VoiceRoom } from './backend'

/**
 * 045: on a seat, hosting or co-hosting I beat every 3 s (voice_heartbeat3); after 6 s without one the server shows
 * me as lost, 7 s later it takes me out. A listener beats every 20 s (voice_heartbeat2): out after 90 s.
 */
export const VOICE_BEAT_MS = 3_000
export const VOICE_LISTENER_BEAT_MS = 20_000

/** Whom the room hears or who may take it over beats fast; a listener slowly (6× fewer writes for most of a room). */
export const beatsFast = (r: Pick<VoiceRoom, 'members'> | null, me: string) => {
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
