'use client'

import { createContext, useContext } from 'react'
import type { Entitlements, Profile } from '@/lib/backend'
import type { Lang, TKey } from '@/lib/i18n'

export type Tab = 'rooms' | 'random' | 'recent' | 'me' | 'settings'

export interface Thread {
  matchId: string
  partner: Profile
}

export interface AppState {
  me: Profile
  setMe: (p: Profile) => void
  phone: string | null
  entitlements: Entitlements
  canChooseGender: boolean
  tab: Tab
  setTab: (t: Tab) => void
  lang: Lang
  setLang: (l: Lang) => void
  t: (key: TKey) => string
  openProfile: (userId: string, opts?: { matchId?: string | null }) => void
  openPaywall: () => void
  openThread: (partner: Profile, matchId?: string) => void
  setImmersive: (on: boolean) => void
  signedOut: () => void
}

export const AppContext = createContext<AppState | null>(null)

export function useApp() {
  const ctx = useContext(AppContext)
  if (!ctx) throw new Error('useApp must be used inside <AppShell>')
  return ctx
}
