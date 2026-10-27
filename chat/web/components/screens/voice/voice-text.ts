import type { VoiceLine, VoiceMember, VoiceReaction, VoiceRoom } from '@/lib/backend'
import type { T } from '@/lib/i18n'

export const REACTIONS: VoiceReaction[] = ['heart', 'laugh', 'fire', 'clap', 'wow']

/** Above this level a person counts as speaking (their ring shows). */
export const SPEAKING = 0.12

export function systemText(line: Extract<VoiceLine, { kind: 'system' }>, t: T) {
  switch (line.event) {
    case 'joined':
      return t('voice.sys.joined', { name: line.name })
    case 'left':
      return t('voice.sys.left', { name: line.name })
    case 'seat':
      return t('voice.sys.seat', { name: line.name, n: line.seat ?? '' })
    case 'audience':
      return t('voice.sys.audience', { name: line.name })
    case 'cohost':
      return t('voice.sys.cohost', { name: line.name })
    case 'host':
      return t('voice.sys.host', { name: line.name })
    case 'track':
      return t('voice.sys.track', { name: line.name, title: line.title || t('voice.ytVideo') })
    case 'removed':
      return t('voice.sys.removed', { name: line.name })
    case 'notice':
      return t('voice.sys.notice', { name: line.name })
    case 'cleared':
      return t('voice.sys.cleared', { name: line.name })
    default:
      return '' // an event this app doesn't know yet: not shown
  }
}

export function roleLabel(m: VoiceMember, t: T) {
  return m.role === 'host' ? t('voice.host') : m.role === 'cohost' ? t('voice.cohost') : null
}

export const memberOf = (room: VoiceRoom, userId: string) => room.members.find((m) => m.profile.id === userId) ?? null
export const isMod = (m: VoiceMember | null) => m?.role === 'host' || m?.role === 'cohost'

/**
 * Whether `me` may move `target` off the stage or remove them from the room (036): the host anyone but themself,
 * a co-host only listeners and speakers. The server checks the same; the sheet hides what it would refuse.
 */
export const canManage = (me: VoiceMember | null, target: VoiceMember) =>
  !!me && me.profile.id !== target.profile.id && target.role !== 'host' && (me.role === 'host' || (me.role === 'cohost' && target.role !== 'cohost'))
