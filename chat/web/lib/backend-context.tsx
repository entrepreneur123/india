'use client'

import { createContext, useContext, useState, type ReactNode } from 'react'
import type { Backend } from './backend'
import { DEMO_MODE, SUPABASE_ANON_KEY, SUPABASE_URL } from './config'
import { MockBackend } from './mockBackend'
import { SupabaseBackend } from './supabaseBackend'

const BackendContext = createContext<Backend | null>(null)

/** The live server when its keys are configured (NEXT_PUBLIC_SUPABASE_*), the in-browser demo otherwise. */
export function BackendProvider({ backend, children }: { backend?: Backend; children: ReactNode }) {
  const [instance] = useState<Backend>(
    () => backend ?? (DEMO_MODE ? new MockBackend() : new SupabaseBackend(SUPABASE_URL, SUPABASE_ANON_KEY)),
  )
  return <BackendContext.Provider value={instance}>{children}</BackendContext.Provider>
}

export function useBackend(): Backend {
  const ctx = useContext(BackendContext)
  if (!ctx) throw new Error('useBackend must be used inside <BackendProvider>')
  return ctx
}
