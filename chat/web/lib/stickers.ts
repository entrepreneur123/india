// Sticker packs (046): a sticker is a whole chat message ":st:<pack>/<n>:" (pack: the item id without "sticker_",
// n 1-8), and the server refuses one from a pack the sender doesn't own (sticker_not_owned). Each sticker is drawn as
// a card: a big emoji and a short caption, the same words for everyone (they are the art). No imports but types.
import type { PackId } from './looks'

export interface StickerArt {
  emoji: string
  caption: string
}

export const STICKERS: Record<PackId, StickerArt[]> = {
  sticker_namaste: [
    { emoji: '🙏', caption: 'Namaste' },
    { emoji: '👋', caption: 'Hello ji!' },
    { emoji: '😊', caption: 'Kaise ho?' },
    { emoji: '🌸', caption: 'Swagat hai' },
    { emoji: '🙌', caption: 'Shukriya' },
    { emoji: '🤗', caption: 'Aao baitho' },
    { emoji: '🌞', caption: 'Suprabhat' },
    { emoji: '🌙', caption: 'Shubh ratri' },
  ],
  sticker_chai: [
    { emoji: '☕', caption: 'Chai time' },
    { emoji: '🍵', caption: 'Ek cutting?' },
    { emoji: '🍪', caption: 'Chai + biscuit' },
    { emoji: '🌧️', caption: 'Baarish & chai' },
    { emoji: '😌', caption: 'Sukoon' },
    { emoji: '🍛', caption: 'Kuch khaoge?' },
    { emoji: '🗣️', caption: 'Chai pe charcha' },
    { emoji: '💯', caption: 'Ekdum kadak!' },
  ],
  sticker_cricket: [
    { emoji: '🏏', caption: 'Shot!' },
    { emoji: '💥', caption: 'Chhakka!' },
    { emoji: '⚡', caption: 'Chauka!' },
    { emoji: '🎯', caption: 'Howzat?' },
    { emoji: '😱', caption: 'Wicket!' },
    { emoji: '👑', caption: 'Captain' },
    { emoji: '🔥', caption: 'On fire' },
    { emoji: '🏆', caption: 'Champions!' },
  ],
  sticker_love: [
    { emoji: '❤️', caption: 'Pyaar' },
    { emoji: '💕', caption: 'Cutie' },
    { emoji: '😘', caption: 'Muah!' },
    { emoji: '💌', caption: 'For you' },
    { emoji: '🌹', caption: 'Gulab' },
    { emoji: '💞', caption: 'Tum aur main' },
    { emoji: '💖', caption: 'Dil se' },
    { emoji: '😍', caption: 'Uff!' },
  ],
}

const BODY = /^:st:([a-z0-9]{2,20})\/([1-8]):$/

/** A sticker message: its pack's item id, its number and its art (null: a pack this app doesn't know yet). */
export function stickerOf(body: string | null | undefined): { pack: string; n: number; art: StickerArt | null } | null {
  const m = body ? BODY.exec(body) : null
  if (!m) return null
  const pack = `sticker_${m[1]}`
  const n = Number(m[2])
  return { pack, n, art: Object.hasOwn(STICKERS, pack) ? STICKERS[pack as PackId][n - 1] : null }
}

/** The message that sends sticker n (1-8) of a pack. */
export const stickerBody = (pack: PackId, n: number) => `:st:${pack.slice('sticker_'.length)}/${n}:`

/** A line as a list or quote shows it: a sticker becomes its emoji and `label` ("🙏 Sticker"). */
export function previewText(body: string, label: string) {
  const st = stickerOf(body)
  return st ? (st.art ? `${st.art.emoji} ${label}` : label) : body
}
