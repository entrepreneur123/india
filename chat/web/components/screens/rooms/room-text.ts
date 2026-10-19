import type { Room, RoomCategory } from '@/lib/backend'
import { EN, type T, type TKey } from '@/lib/i18n'

export const CATEGORIES: { id: RoomCategory; emoji: string }[] = [
  { id: 'chat', emoji: '💬' },
  { id: 'friends', emoji: '🤝' },
  { id: 'language', emoji: '🗣️' },
  { id: 'music', emoji: '🎧' },
  { id: 'movies', emoji: '🎬' },
  { id: 'gaming', emoji: '🎮' },
  { id: 'sports', emoji: '⚽' },
  { id: 'study', emoji: '🎓' },
  { id: 'travel', emoji: '✈️' },
  { id: 'night', emoji: '🌙' },
]

export const categoryLabel = (id: RoomCategory, t: T) => t(`cat.${id}` as TKey)

const known = (key: string): key is TKey => key in EN

/** Official rooms are named by the server in English; the ones we know are shown in the user's language. */
export function roomTitle(r: Room, t: T) {
  const key = `roomTitle.${r.id}`
  return r.official && known(key) ? t(key) : r.title
}

/** One line on what an official room is for (null when there is none). */
export function roomAbout(r: Room, t: T) {
  const key = `about.${r.id}`
  return r.official && known(key) ? t(key) : null
}

/** Short labels for who may join a user room, e.g. ["Women only", "Ages 20–30", "Private"]. */
export function roomConditions(r: Room, t: T, withPrivate = true): string[] {
  const out: string[] = []
  if (r.genderRule === 'female') out.push(t('rooms.womenOnly'))
  if (r.genderRule === 'male') out.push(t('rooms.menOnly'))
  if (r.minAge > 18 || r.maxAge < 100)
    out.push(r.maxAge >= 100 ? t('rooms.agesPlus', { min: r.minAge }) : t('rooms.ages', { min: r.minAge, max: r.maxAge }))
  if (r.isPrivate && withPrivate) out.push(t('rooms.private'))
  return out
}

export function refusalLabel(r: Room, t: T) {
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
