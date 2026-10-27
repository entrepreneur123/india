// Crash reports go to Sentry, the main steps to PostHog (EU). Both libraries download after the page has loaded, so
// the first screen never waits for them; anything reported before then waits in their promise.
// Errors carry their message, stack and screen; events a fixed name plus the app language. No chat text, no phone
// numbers: the signed-in account is its random id with its country. Nothing is kept in the browser (PostHog keeps
// its ids in memory), no session replays, no click tracking.
import { DEMO_MODE, POSTHOG_HOST, POSTHOG_KEY, SENTRY_DSN } from './config'

export type FunnelEvent =
  | 'signup_done' | 'match_found' | 'chat_message_sent' | 'room_created' | 'room_joined' | 'call_started'
  | 'plus_purchased'

const VERSION = 'web-0.9.0'
type Sentry = typeof import('./sentry')
type PostHog = (typeof import('posthog-js'))['default']

let sentry: Promise<Sentry> | null = null
let posthog: Promise<PostHog> | null = null
const seen = new Set<string>()
let errorsSent = 0
let identified: string | null = null

const afterLoad = () =>
  new Promise<void>((resolve) => {
    if (document.readyState === 'complete') resolve()
    else window.addEventListener('load', () => resolve(), { once: true })
  })

/** A step people reach (once per page for chat_message_sent: it's the first message that counts). */
export function track(name: FunnelEvent) {
  if (name === 'chat_message_sent' && seen.has(name)) return
  seen.add(name)
  void posthog?.then((p) => p.capture(name))
}

export function reportError(err: unknown, where?: string) {
  const e = err instanceof Error ? err : null
  const message = e?.message ?? String(err)
  if (/ResizeObserver loop|chrome-extension:|moz-extension:/.test(`${message} ${e?.stack ?? ''}`)) return
  const key = `${where}|${e?.name}|${message.slice(0, 200)}`
  if (seen.has(key) || errorsSent >= 30) return // the same error once per page; a broken page doesn't flood
  seen.add(key)
  errorsSent++
  void sentry?.then((s) => s.captureException(err, { tags: { where: where ?? 'app' } }))
}

/** The signed-in account (null: signed out). Its id is random; country and language help compare countries. */
export function identify(me: { id: string; country?: string | null } | null, lang: string) {
  if ((me?.id ?? null) === identified) return
  const was = identified
  identified = me?.id ?? null
  void sentry?.then((s) => s.setUser(me ? { id: me.id } : null))
  void posthog?.then((p) => {
    if (me) p.identify(me.id, { country: me.country ?? null, lang })
    else if (was) p.reset()
  })
}

let installed = false
/** Uncaught errors and rejected promises anywhere on the page, from the first line on. */
export function installErrorReporting() {
  if (installed || DEMO_MODE || typeof window === 'undefined') return
  installed = true
  window.addEventListener('error', (e) => reportError(e.error ?? e.message, 'window'))
  window.addEventListener('unhandledrejection', (e) => reportError(e.reason, 'promise'))
  if (SENTRY_DSN)
    sentry = afterLoad().then(async () => {
      const s = await import('./sentry')
      s.init({
        dsn: SENTRY_DSN,
        release: VERSION,
        environment: location.hostname,
        // The listeners above already report (they're there before Sentry downloads).
        integrations: (all) => all.filter((i) => i.name !== 'GlobalHandlers'),
        // Requests and page changes only: clicks and console lines can hold names or message text.
        beforeBreadcrumb: (b) => (b.category === 'fetch' || b.category === 'xhr' || b.category === 'navigation' ? b : null),
      })
      return s
    })
  if (POSTHOG_KEY)
    posthog = afterLoad().then(async () => {
      const p = (await import('posthog-js')).default
      p.init(POSTHOG_KEY, {
        api_host: POSTHOG_HOST,
        persistence: 'memory',
        person_profiles: 'identified_only',
        autocapture: false,
        capture_pageview: true, // one per visit: country, device, where people came from
        capture_pageleave: true,
        capture_exceptions: false, // Sentry has them
        capture_performance: false,
        capture_heatmaps: false,
        capture_dead_clicks: false,
        disable_session_recording: true,
        disable_surveys: true,
        disable_product_tours: true,
        disable_conversations: true,
        disable_web_experiments: true,
        advanced_disable_flags: true,
        disable_external_dependency_loading: true,
      })
      p.register({ app_version: VERSION })
      return p
    })
}
