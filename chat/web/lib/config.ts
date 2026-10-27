// The policy pages are chat/docs/*.html, copied into this site at build time (scripts/copy-legal.mjs).
// The grievance officer's real name and email must be filled in before launch (IT Rules 2021); see chat/README.md.
export const PRIVACY_URL = '/privacy.html'
export const TERMS_URL = '/terms.html'
export const CHILD_SAFETY_URL = '/child-safety.html'
export const SUPPORT_EMAIL = 'support@yaari.app'
export const GRIEVANCE_OFFICER = {
  name: 'Grievance Officer, Yaari',
  email: 'grievance@yaari.app',
}

export const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL ?? ''
export const SUPABASE_ANON_KEY = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? ''

/** Without server keys the site runs on the in-browser demo backend (any 6-digit code works). */
export const DEMO_MODE = !SUPABASE_URL || !SUPABASE_ANON_KEY

/** Crash reports (Sentry) and product analytics (PostHog, EU region): set on Vercel only, so no other build sends any. */
export const SENTRY_DSN = process.env.NEXT_PUBLIC_SENTRY_DSN ?? ''
export const POSTHOG_KEY = process.env.NEXT_PUBLIC_POSTHOG_KEY ?? ''
export const POSTHOG_HOST = 'https://eu.i.posthog.com'

/** A random chat can be left 5 s after it starts (the server's exit_lock()). */
export const EXIT_LOCK_MS = 5000

/** The community rules, as dictionary keys (text in lib/locales/*). Same rules as the Android app. */
export const COMMUNITY_RULES = ['rules.1', 'rules.2', 'rules.3', 'rules.4', 'rules.5'] as const
