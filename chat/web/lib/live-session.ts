// One live session at a time: a voice room or a 1:1 random chat, never both (as in WhatsApp, X Spaces, Clubhouse).
// A friend's thread is not live: it never conflicts. The shell asks here what a step needs before it goes ahead.
// No imports, so the unit tests run it directly (node --test).

/** Where the random tab is. */
export type RandomLive = { phase: 'idle' } | { phase: 'searching' } | { phase: 'chat'; partner: string }

export interface LiveNow {
  /** The room I'm in (open or minimised). */
  room: { id: string; title: string } | null
  random: RandomLive
}

export type LiveStep = { kind: 'room'; roomId: string } | { kind: 'random' }

export type LivePlan =
  /** Nothing in the way. */
  | { do: 'go' }
  /** Searching, not matched yet: the search stops without a word. */
  | { do: 'stopSearch' }
  /** In a random chat: "End your chat with {name} to join this room?" */
  | { do: 'confirmEndChat'; partner: string }
  /** In a room: "Leave “{room}” to start a random chat?" */
  | { do: 'confirmLeaveRoom'; title: string }

export function planLive(now: LiveNow, step: LiveStep): LivePlan {
  if (step.kind === 'room') {
    // The room I'm already in (or another one, which simply takes its place) needs nothing from the random tab.
    if (now.random.phase === 'chat') return { do: 'confirmEndChat', partner: now.random.partner }
    if (now.random.phase === 'searching') return { do: 'stopSearch' }
    return { do: 'go' }
  }
  if (now.room) return { do: 'confirmLeaveRoom', title: now.room.title }
  return { do: 'go' }
}
