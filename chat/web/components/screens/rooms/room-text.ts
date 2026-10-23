import type { RoomKind, RoomRefusal, RoomRules } from '@/lib/backend'
import type { T } from '@/lib/i18n'
import type { Topic } from '@/components/ui/topic-icons'

export const CATEGORIES: RoomKind[] = ['talk', 'music']

/** The glyph each category is drawn with. */
export const kindTopic = (k: RoomKind): Topic => (k === 'music' ? 'music' : 'chat')

// Anything but music reads as talk (an older room can still say 'games' for a moment).
export const categoryLabel = (k: RoomKind, t: T) => t(k === 'music' ? 'cat.music' : 'cat.talk')
export const categoryBody = (k: RoomKind, t: T) => t(k === 'music' ? 'cat.musicBody' : 'cat.talkBody')

/** Short labels for who may come in, e.g. ["Women only", "Ages 20–30", "Private"]. */
export function roomConditions(r: RoomRules, t: T, withPrivate = true): string[] {
  const out: string[] = []
  if (r.genderRule === 'female') out.push(t('rooms.womenOnly'))
  if (r.genderRule === 'male') out.push(t('rooms.menOnly'))
  if (r.minAge > 18 || r.maxAge < 100)
    out.push(r.maxAge >= 100 ? t('rooms.agesPlus', { min: r.minAge }) : t('rooms.ages', { min: r.minAge, max: r.maxAge }))
  if (r.isPrivate && withPrivate) out.push(t('rooms.private'))
  return out
}

export function refusalLabel(r: RoomRules & { refusal: RoomRefusal | null }, t: T) {
  switch (r.refusal) {
    case 'room_full':
      return t('rooms.full')
    case 'room_gender':
      return r.genderRule === 'female' ? t('rooms.womenOnly') : t('rooms.menOnly')
    case 'room_age':
      return t('rooms.outsideAge')
    case 'room_closed':
      return t('rooms.closed')
    default:
      return t('rooms.unavailable')
  }
}
