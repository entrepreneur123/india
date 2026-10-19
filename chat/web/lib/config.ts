// Same values as the Android app (chat/app/.../SettingsScreen.kt `Support`). The grievance officer's real
// name and email must be filled in before launch (IT Rules 2021); see chat/README.md.
export const PRIVACY_URL = 'https://entrepreneur123.github.io/india/yaari-privacy.html'
export const SUPPORT_EMAIL = 'support@yaari.app'
export const GRIEVANCE_OFFICER = {
  name: 'Grievance Officer, Yaari',
  email: 'grievance@yaari.app',
}

/** Set once the app is live on Google Play; until then the paywall says the Android app is coming. */
export const PLAY_STORE_URL: string | null = null

export const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL ?? ''
export const SUPABASE_ANON_KEY = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? ''

/** Without server keys the site runs on the in-browser demo backend (any 6-digit code works). */
export const DEMO_MODE = !SUPABASE_URL || !SUPABASE_ANON_KEY

export const FREE_EXIT_LOCK_MS = 5000
export const PLUS_EXIT_LOCK_MS = 200

export const COMMUNITY_RULES = [
  'Adults only (18+). Anyone who looks under 18 is removed.',
  'No nudity or sexual content, in chat or on video.',
  'No harassment, hate, threats or scams.',
  'Don’t share anyone’s private photos or details.',
  'Breaking the rules means a permanent ban.',
]
