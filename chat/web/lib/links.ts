// Links in chat lines (room chat, threads, random chats): found in the text and shown as links, never as HTML.
// Only http(s) addresses become links; everything else stays text (React escapes it).
import { youTubeId } from './youtube.ts'

export type TextPart = { kind: 'text'; text: string } | { kind: 'link'; text: string; url: string }

const FIND = /\bhttps?:\/\/[^\s<>"'`]+/gi
/** Punctuation that ends a sentence rather than the address ("see https://x.in/a." / "(https://x.in/a)"). */
const TRAILING = /[.,;:!?…'"\]}>]+$/
const MAX_URL = 2000

/** The address as a safe http(s) URL, or null (another scheme, no host, credentials, too long). */
export function safeUrl(raw: string): string | null {
  if (raw.length > MAX_URL) return null
  let u: URL
  try {
    u = new URL(raw)
  } catch {
    return null
  }
  if (u.protocol !== 'https:' && u.protocol !== 'http:') return null
  if (!u.hostname || u.username || u.password) return null
  return u.href
}

/** Strips what follows the address in prose: closing punctuation, and a ")" that has no "(" in the address. */
function trim(found: string): string {
  let s = found
  for (;;) {
    const before = s
    s = s.replace(TRAILING, '')
    if (s.endsWith(')') && (s.match(/\(/g)?.length ?? 0) < (s.match(/\)/g)?.length ?? 0)) s = s.slice(0, -1)
    if (s === before) return s
  }
}

/** The text cut into plain parts and links, in order (joined back, the parts are the text again). */
export function splitLinks(text: string): TextPart[] {
  const parts: TextPart[] = []
  let at = 0
  for (const match of text.matchAll(FIND)) {
    const start = match.index ?? 0
    const raw = trim(match[0])
    const url = safeUrl(raw)
    if (!url) continue
    if (start > at) parts.push({ kind: 'text', text: text.slice(at, start) })
    parts.push({ kind: 'link', text: raw, url })
    at = start + raw.length
  }
  if (at < text.length) parts.push({ kind: 'text', text: text.slice(at) })
  return parts
}

/** The first link in the text that gets a preview card (YouTube links don't: they keep their own behaviour). */
export function previewLink(text: string): string | null {
  for (const p of splitLinks(text)) if (p.kind === 'link' && !isYouTube(p.url)) return p.url
  return null
}

export const isYouTube = (url: string) => youTubeId(url) !== null

/** "www.example.com/a/b" → "example.com", for the card and the viewer's header. */
export function hostOf(url: string): string {
  try {
    return new URL(url).hostname.replace(/^www\./, '')
  } catch {
    return url
  }
}

/** What voice_share_link takes: https only, at most 500 characters. */
export const shareable = (url: string) => url.startsWith('https://') && url.length <= 500 && safeUrl(url) !== null

/**
 * The in-app viewer may show this page (else the link just opens in a new tab): the site lets other apps frame it
 * (link-meta's `frameable`), and it is not this app itself (`here`: its origin; the sandbox would not hold it).
 */
export function framable(meta: { url: string; frameable: boolean } | null, here: string) {
  if (!meta?.frameable) return false
  try {
    return new URL(meta.url).origin !== here
  } catch {
    return false
  }
}
