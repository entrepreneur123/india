// Light or dark (the owner, 2026-11-01), chosen in Settings as WhatsApp's Theme and Discord's Appearance do:
// the device's setting (default), light or dark. The choice lives in this browser; `dark` on <html> switches the
// colour tokens (app/globals.css). THEME_SCRIPT runs in <head> before the first paint (app/layout.tsx), so a dark page
// never flashes white; scripts/csp-hashes.mjs pins it like Next's own inline scripts.
export type Theme = 'system' | 'light' | 'dark'
export const THEMES: Theme[] = ['system', 'light', 'dark']
const KEY = 'yaari_theme'
const DARK = '(prefers-color-scheme: dark)'
/** The browser bar's colour per theme: the page's --background. */
const BAR = { light: '#ffffff', dark: '#0f0e0d' }

export const THEME_SCRIPT = `try{var t=localStorage.getItem('${KEY}');if(t==='dark'||(t!=='light'&&matchMedia('${DARK}').matches))document.documentElement.classList.add('dark')}catch(e){}`

export function savedTheme(): Theme {
  try {
    const t = localStorage.getItem(KEY)
    return t === 'light' || t === 'dark' ? t : 'system'
  } catch {
    return 'system'
  }
}

/** Puts [theme] on the page (and keeps it, unless storage is blocked: then for this visit). */
export function applyTheme(theme: Theme) {
  try {
    if (theme === 'system') localStorage.removeItem(KEY)
    else localStorage.setItem(KEY, theme)
  } catch {
    /* blocked: this visit only */
  }
  const dark = theme === 'dark' || (theme === 'system' && matchMedia(DARK).matches)
  document.documentElement.classList.toggle('dark', dark)
  document.querySelectorAll('meta[name="theme-color"]').forEach((m) => m.setAttribute('content', BAR[dark ? 'dark' : 'light']))
}

/** While the choice is 'system': follows the device when it switches (sunset, the OS's own schedule). */
export function followDevice() {
  const mq = matchMedia(DARK)
  const on = () => savedTheme() === 'system' && applyTheme('system')
  mq.addEventListener('change', on)
  return () => mq.removeEventListener('change', on)
}
