// Small copies of profile photos (2026-10-28, the owner: "photos load slowly, piece by piece"). A face in a list is
// 28–104 px, but it downloaded the whole 1280 px photo. Every profile photo now has a 256 px copy beside it
// ("<uid>/avatar/<id>.jpg" → "<uid>/avatar/<id>_s.jpg", the same read rules as the photo), uploaded with it; photos
// from before get theirs from their owner's app (SupabaseBackend.backfillThumbs). No imports (node --test).

/** The small copy's size, a 2x face of up to 128 px. */
export const THUMB_PX = 256

/** Profile photos (and only those) have a small copy. */
export const hasThumb = (path: string) => /^[^/]+\/avatar\/[^/]+\.jpg$/.test(path) && !path.endsWith('_s.jpg')
export const thumbPath = (path: string) => path.replace(/\.jpg$/, '_s.jpg')

const thumbs = new Map<string, string>()
/** Remembers that this photo URL has a small copy at that URL. */
export const registerThumb = (full: string, small: string) => void thumbs.set(full, small)
/** The small copy of this photo URL, when there is one. */
export const thumbOf = (url: string | null | undefined) => (url ? thumbs.get(url) : undefined)
