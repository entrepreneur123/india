// Room chat replies and @mentions (029): the quote a reply carries, and finding "@Name" in what people type.
// No imports but types, so the unit tests run it directly (node --test).
import type { VoiceLine, VoiceReads, VoiceReply } from './backend'

/** The server keeps this much of a text line in a reply's quote. */
export const REPLY_TEXT_MAX = 120

/** Lines that can be answered. */
export type Quotable = Extract<VoiceLine, { kind: 'text' | 'photo' | 'poll' }>

export const quotable = (l: VoiceLine): l is Quotable => l.kind === 'text' || l.kind === 'photo' || l.kind === 'poll'

/** The quote of a line, as voice_send2 builds it (the composer shows it while I write the answer). */
export function replyOf(l: Quotable): VoiceReply {
  return {
    id: l.id,
    sender: l.sender.id,
    name: l.sender.name,
    kind: l.kind,
    text: l.kind === 'text' ? l.text.slice(0, REPLY_TEXT_MAX) : l.kind === 'poll' ? l.question : null,
  }
}

/** payload.reply of a voice_messages row, checked: null when it is missing or not a reply. */
export function parseReply(payload: unknown): VoiceReply | null {
  const r = payload && typeof payload === 'object' ? (payload as { reply?: unknown }).reply : null
  if (!r || typeof r !== 'object') return null
  const o = r as Record<string, unknown>
  const id = Number(o.id)
  const kind = o.kind === 'photo' || o.kind === 'poll' ? o.kind : o.kind === 'text' ? 'text' : null
  if (!Number.isFinite(id) || !kind) return null
  return {
    id,
    sender: typeof o.sender === 'string' ? o.sender : '',
    name: typeof o.name === 'string' ? o.name : '',
    kind,
    text: kind === 'photo' || typeof o.text !== 'string' ? null : o.text.slice(0, REPLY_TEXT_MAX),
  }
}

const WORD = /[\p{L}\p{N}_]/u

/** Where "@name" (any case) stands in the text as a whole word: [start, end) of each, "@" included. */
export function mentionRanges(text: string, name: string): [number, number][] {
  const n = name.trim()
  if (!n) return []
  const hay = text.toLocaleLowerCase()
  const needle = `@${n.toLocaleLowerCase()}`
  const out: [number, number][] = []
  let from = 0
  for (;;) {
    const i = hay.indexOf(needle, from)
    if (i < 0) break
    const end = i + needle.length
    // Not inside a word or an e-mail address, and not the start of a longer name ("@Ana" in "@Anaya").
    const before = i === 0 || !WORD.test(text[i - 1])
    const after = end >= text.length || !WORD.test(text[end])
    if (before && after) out.push([i, end])
    from = end
  }
  return out
}

/** The line mentions this name. */
export const mentions = (text: string, name: string) => mentionRanges(text, name).length > 0

/**
 * The "@..." being typed right before the caret (after a space or at the start), for the member list: the text after
 * "@" and where the "@" is. Null when the caret isn't in one.
 */
export function mentionQuery(text: string, caret: number): { query: string; at: number } | null {
  const head = text.slice(0, caret)
  const at = head.lastIndexOf('@')
  if (at < 0 || (at > 0 && !/\s/.test(head[at - 1]))) return null
  const query = head.slice(at + 1)
  // A finished mention ("@Rohan B. hi") or a long run is not a search any more.
  if (query.length > 30 || /\s{2}|\n/.test(query) || query.split(/\s+/).length > 3) return null
  return { query, at }
}

/** Puts "@Name " in place of the "@..." being typed; returns the new text and where the caret goes. */
export function insertMention(text: string, caret: number, at: number, name: string): { text: string; caret: number } {
  const tail = text.slice(caret).replace(/^\S*/, '').replace(/^ /, '')
  const insert = `@${name} `
  return { text: text.slice(0, at) + insert + tail, caret: at + insert.length }
}

/** People whose name starts with (else contains) what was typed, at most `max` (the caller leaves me out). */
export function mentionMatches<T extends { name: string }>(people: T[], query: string, max = 6): T[] {
  const q = query.trim().toLocaleLowerCase()
  if (!q) return people.slice(0, max)
  const starts = people.filter((p) => p.name.toLocaleLowerCase().startsWith(q))
  const has = people.filter((p) => !starts.includes(p) && p.name.toLocaleLowerCase().includes(q))
  return [...starts, ...has].slice(0, max)
}

/**
 * Room reactions are moments: one older than `maxMs` (replayed) is not shown. Ages are judged on the server's clock,
 * the newest row time seen so far, never the device's: a computer whose clock runs fast would drop every reaction.
 * Feed it every row time (`reaction` false for other rows); true = show the reaction.
 */
export function reactionClock(maxMs = 15_000) {
  let newest = 0
  return (at: number, reaction: boolean) => {
    newest = Math.max(newest, at)
    return reaction && newest - at < maxMs
  }
}

// ---- read marks (104) ----

/** The furthest line someone other than me has read: my lines up to it show two ticks. */
export const readByOthers = (r: VoiceReads | null, me: string) => (!r ? 0 : r.by === me ? r.hi2 : r.hi)

/** Marks only grow, and answers can arrive out of order: keep the further one. */
export const laterReads = (a: VoiceReads | null, b: VoiceReads) => (a && (a.hi > b.hi || (a.hi === b.hi && a.hi2 > b.hi2)) ? a : b)

/** Whether my reading up to `upto` would move a mark (voice_read writes only then): otherwise it isn't sent. */
export const movesReads = (r: VoiceReads | null, me: string, upto: number) => !r || upto > r.hi || (r.by !== me && upto > r.hi2)

/** voice_read's rule for `who` having read up to line `v`: the room's new marks, or null when none moves. */
export const nextReads = (k: VoiceReads, who: string, v: number): VoiceReads | null =>
  !movesReads(k, who, v) ? null : v > k.hi ? { hi: v, by: who, hi2: k.by === who ? k.hi2 : k.hi } : { ...k, hi2: v }
