// Links opened from KakaoTalk, Instagram, Facebook, LINE and similar apps open in their built-in browser, which on
// iPhone and iPad (and in many Android web views) gets no microphone or camera: voice rooms and calls stay silent.
// Detecting it lets us send people to their real browser first.

export type InAppBrowser = 'kakao' | 'instagram' | 'facebook' | 'line' | 'other'

export function inAppBrowser(ua: string): InAppBrowser | null {
  if (/KAKAOTALK/i.test(ua)) return 'kakao'
  if (/Instagram/.test(ua)) return 'instagram'
  if (/FBAN|FBAV|FB_IAB|FBIOS/.test(ua)) return 'facebook'
  if (/\bLine\//.test(ua)) return 'line'
  if (/NAVER\(inapp|DaumApps|Snapchat|everytimeApp|BAND\//.test(ua)) return 'other'
  // A plain Android web view inside some app ("; wv)"), not Chrome itself.
  if (/Android/.test(ua) && /; wv\)/.test(ua)) return 'other'
  return null
}

/**
 * A link that leaves the built-in browser, or null when the app offers none (then people use the app's own
 * "open in browser" menu).
 */
export function externalLink(kind: InAppBrowser, url: string, ua: string): string | null {
  if (kind === 'kakao') return `kakaotalk://web/openExternal?url=${encodeURIComponent(url)}`
  if (kind === 'line') return url + (url.includes('?') ? '&' : '?') + 'openExternalBrowser=1'
  if (/Android/.test(ua)) {
    const u = new URL(url)
    return `intent://${u.host}${u.pathname}${u.search}#Intent;scheme=https;package=com.android.chrome;S.browser_fallback_url=${encodeURIComponent(url)};end`
  }
  return null
}
