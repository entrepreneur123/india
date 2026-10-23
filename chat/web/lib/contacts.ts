// 044_friend_contacts.sql: the messenger contacts friends can exchange, in the order the editor shows them.
export const CONTACT_APPS = ['whatsapp', 'phone', 'telegram', 'instagram', 'line', 'kakaotalk', 'wechat', 'snapchat', 'discord'] as const
export type ContactApp = (typeof CONTACT_APPS)[number]
export type Contacts = Partial<Record<ContactApp, string>>

/** Brand names, never translated. */
export const CONTACT_NAMES: Record<Exclude<ContactApp, 'phone'>, string> = {
  whatsapp: 'WhatsApp', telegram: 'Telegram', instagram: 'Instagram', line: 'LINE', kakaotalk: 'KakaoTalk', wechat: 'WeChat',
  snapchat: 'Snapchat', discord: 'Discord',
}

export const isNumber = (app: ContactApp) => app === 'phone' || app === 'whatsapp'

/** Like contacts_clean: what the server keeps ('' for nothing), or null when it would refuse it (contact_invalid). */
export function cleanContact(app: ContactApp, raw: string): string | null {
  const v = raw.trim()
  if (!v) return ''
  if (isNumber(app)) {
    const n = v.replace(/[\s().-]/g, '')
    return /^\+?[0-9]{6,16}$/.test(n) ? n : null
  }
  const h = v.replace(/^@+/, '')
  return /^[A-Za-z0-9._-]{1,40}$/.test(h) ? h : null
}

/** Where tapping a friend's contact goes; null: the app has no public link, so it is copied instead. */
export function contactLink(app: ContactApp, v: string): string | null {
  const e = encodeURIComponent(v) // stored values are URL-safe already; this keeps an odd one from changing the link
  switch (app) {
    case 'phone': return `tel:${v}`
    case 'whatsapp': return `https://wa.me/${v.replace(/^\+/, '')}`
    case 'telegram': return `https://t.me/${e}`
    case 'instagram': return `https://instagram.com/${e}`
    case 'line': return `https://line.me/ti/p/~${e}`
    case 'snapchat': return `https://snapchat.com/add/${e}`
    default: return null
  }
}
