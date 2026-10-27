// Hashtags (072/073): the server's hashtags() on screen. A #word is letters of any script, digits and "_", 1-30 long
// (a longer word is no tag); lower-cased, each once in first-appearance order, at most 10. Rooms keep 5.
const TAG = /#((?:[^\s\p{P}\p{S}]|_){1,30})(?![^\s\p{P}\p{S}]|_)/gu
export const ROOM_TAGS_MAX = 5

export function hashtagsOf(text: string): string[] {
  return [...new Set([...text.matchAll(TAG)].map((m) => m[1].toLowerCase()))].slice(0, 10)
}

/** A room's tags from what was typed ("#music, travel" or chips): cleaned and capped like voice_set_extras. */
export const roomTags = (typed: readonly string[]) => hashtagsOf(typed.map((t) => `#${t.replace(/^#+/, '')}`).join(' ')).slice(0, ROOM_TAGS_MAX)

/** A text cut into plain runs and #tags, for showing the tags as links. */
export function splitTags(text: string): { text: string; tag: string | null }[] {
  const out: { text: string; tag: string | null }[] = []
  let at = 0
  for (const m of text.matchAll(TAG)) {
    if (m.index > at) out.push({ text: text.slice(at, m.index), tag: null })
    out.push({ text: m[0], tag: m[1].toLowerCase() })
    at = m.index + m[0].length
  }
  if (at < text.length) out.push({ text: text.slice(at), tag: null })
  return out
}
