'use client'

import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import type { Backend } from './backend'
import { DEMO_MODE, SUPABASE_ANON_KEY, SUPABASE_URL } from './config'

const BackendContext = createContext<Backend | null>(null)

/**
 * The live server when its keys are configured (NEXT_PUBLIC_SUPABASE_*), the in-browser demo otherwise. Each is
 * its own chunk: the live site never downloads the demo, the demo never downloads supabase-js.
 */
function loadBackend(): Promise<Backend> {
  return DEMO_MODE
    ? import('./mockBackend').then((m) => new m.MockBackend())
    : import('./supabaseBackend').then((m) => new m.SupabaseBackend(SUPABASE_URL, SUPABASE_ANON_KEY))
}

let pending: Promise<Backend> | null = null
/** Starts the download as early as possible (called when the app script runs, before React renders). */
export function preloadBackend() {
  pending ??= loadBackend()
  return pending
}

export function BackendProvider({
  backend,
  children,
  fallback,
}: {
  backend?: Backend
  children: ReactNode
  fallback: ReactNode
}) {
  const [instance, setInstance] = useState<Backend | null>(backend ?? null)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (instance) return
    let alive = true
    let retry: ReturnType<typeof setTimeout> | undefined
    preloadBackend().then(
      (b) => alive && setInstance(b),
      () => {
        // The chunk could not be downloaded (offline or a dropped connection): try again in a few seconds.
        pending = null
        if (alive) retry = setTimeout(() => setAttempt((a) => a + 1), 3000)
      },
    )
    return () => {
      alive = false
      clearTimeout(retry)
    }
  }, [instance, attempt])

  if (!instance) return <>{fallback}</>
  return <BackendContext.Provider value={instance}>{children}</BackendContext.Provider>
}

export function useBackend(): Backend {
  const ctx = useContext(BackendContext)
  if (!ctx) throw new Error('useBackend must be used inside <BackendProvider>')
  return ctx
}
