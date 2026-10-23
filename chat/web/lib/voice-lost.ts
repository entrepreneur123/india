import type { VoiceRoom } from './backend'

/** 045: in a room I beat every 3 s; after 6 s without one the server shows me as lost, 7 s later it takes me out. */
export const VOICE_BEAT_MS = 3_000
export const VOICE_GONE_MS = 13_000

/** Back after a gap this long, the server has already taken me out: close the room instead of re-entering silently. */
export const droppedOut = (lastBeat: number, now: number) => now - lastBeat > VOICE_GONE_MS

/**
 * Who takes over when the host leaves (voice_hand_over): the co-host who came in first and is still connected. None:
 * leaving ends the room.
 */
export const nextHost = (r: Pick<VoiceRoom, 'hostId' | 'members'>) =>
  r.members
    .filter((m) => m.role === 'cohost' && !m.lost && m.profile.id !== r.hostId)
    .sort((a, b) => (a.joinedAt ?? 0) - (b.joinedAt ?? 0))[0] ?? null
