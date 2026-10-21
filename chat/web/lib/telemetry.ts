// Crash reports and a few funnel events, sent to our own server (021: log_events) — no third-party service.
// Errors carry their message and stack; events only a fixed name. No chat text, no phone numbers, no ids beyond
// the signed-in account the server already knows. Batched, sent when the page is hidden or closed (keepalive).
import { DEMO_MODE, SUPABASE_ANON_KEY, SUPABASE_URL } from './config'

export type FunnelEvent =
  | 'signup_done' | 'match_found' | 'chat_message_sent' | 'room_created' | 'room_joined' | 'call_started'
  | 'plus_purchased'

type Item = { kind: 'error' | 'event'; name: string; message?: string; stack?: string; version: string; platform: 'web'; route: string }

const VERSION = 'web-0.8.0'
/** A keepalive request may carry 64 KB at most. */
const MAX_BATCH_BYTES = 48_000
const queue: Item[] = []
const seen = new Set<string>()
let errorsSent = 0
let timer: ReturnType<typeof setTimeout> | undefined

const enabled = () => !DEMO_MODE && typeof window !== 'undefined'

/** A step people reach (once per page for chat_message_sent: it's the first message that counts). */
export function track(name: FunnelEvent) {
  if (name === 'chat_message_sent' && seen.has(name)) return
  seen.add(name)
  push({ kind: 'event', name })
}

export function reportError(err: unknown, where?: string) {
  const e = err instanceof Error ? err : null
  const name = (where ? `${where}:` : '') + (e?.name || typeof err)
  const message = (e?.message ?? String(err)).slice(0, 2000)
  const stack = e?.stack?.slice(0, 4000)
  if (/ResizeObserver loop|chrome-extension:|moz-extension:/.test(`${message} ${stack ?? ''}`)) return
  const key = `${name}|${message.slice(0, 200)}`
  if (seen.has(key) || errorsSent >= 30) return // the same error once per page; a broken page doesn't flood
  seen.add(key)
  errorsSent++
  push({ kind: 'error', name: name.slice(0, 100), message, stack })
}

function push(item: Omit<Item, 'version' | 'platform' | 'route'>) {
  if (!enabled()) return
  queue.push({ ...item, version: VERSION, platform: 'web', route: location.pathname.slice(0, 200) })
  if (queue.length >= 20) flush()
  else if (!timer) timer = setTimeout(() => flush(), 60_000)
}

/** The signed-in session (supabase-js keeps it under this key, see lib/supabaseBackend.ts), if still valid. */
function accessToken(): string | null {
  try {
    const s = JSON.parse(localStorage.getItem('yaari_auth') ?? 'null') as { access_token?: string; expires_at?: number } | null
    return s?.access_token && (s.expires_at ?? 0) * 1000 > Date.now() + 10_000 ? s.access_token : null
  } catch {
    return null
  }
}

export function flush(keepalive = false) {
  clearTimeout(timer)
  timer = undefined
  while (queue.length) {
    const batch: Item[] = []
    let bytes = 0
    while (queue.length && batch.length < 20 && bytes + JSON.stringify(queue[0]).length < MAX_BATCH_BYTES) {
      bytes += JSON.stringify(queue[0]).length
      batch.push(queue.shift()!)
    }
    if (!batch.length) batch.push(queue.shift()!) // one item alone is under the limit (stack is cut to 4k)
    const token = accessToken()
    void fetch(`${SUPABASE_URL}/rest/v1/rpc/${token ? 'log_events' : 'log_events_anon'}`, {
      method: 'POST',
      keepalive,
      headers: { apikey: SUPABASE_ANON_KEY, Authorization: `Bearer ${token ?? SUPABASE_ANON_KEY}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ p_events: batch }),
    }).catch(() => undefined)
  }
}

let installed = false
/** Uncaught errors and rejected promises anywhere on the page; sent as the page goes away. */
export function installErrorReporting() {
  if (installed || !enabled()) return
  installed = true
  window.addEventListener('error', (e) => reportError(e.error ?? e.message, 'window'))
  window.addEventListener('unhandledrejection', (e) => reportError(e.reason, 'promise'))
  window.addEventListener('pagehide', () => flush(true))
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') flush(true)
  })
}
