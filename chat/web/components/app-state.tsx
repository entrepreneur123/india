'use client'

import { createContext, useContext, useEffect, useRef } from 'react'
import type { Badges, Entitlements, Profile } from '@/lib/backend'
import type { Lang, T } from '@/lib/i18n'
import type { RandomLive } from '@/lib/live-session'

export type Tab = 'rooms' | 'random' | 'recent' | 'me' | 'settings'

export interface Thread {
  matchId: string
  partner: Profile
}

/** What the shell needs from the random tab (one live session at a time: lib/live-session.ts). */
export interface RandomControl {
  live: () => RandomLive
  /** Stops a search without a word. */
  cancelSearch: () => void
  /** Ends the open chat (leave_match); false when it couldn't (the exit lock, the network: the reason was shown). */
  endChat: () => Promise<boolean>
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
  t: T
  openProfile: (userId: string, opts?: { matchId?: string | null }) => void
  openPaywall: () => void
  openThread: (partner: Profile, matchId?: string) => void
  /**
   * Enters a voice room full screen (leaving the one I'm in, if another). A random chat in progress is ended first,
   * after a confirm; a search just stops.
   */
  openVoice: (roomId: string) => void
  /** Before making a room: true once nothing live is in the way (the same confirm as openVoice), false to stay. */
  freeForRoom: () => Promise<boolean>
  /** Before a random search: true once I'm out of my room (after a confirm), false to stay in it. */
  freeForRandom: () => Promise<boolean>
  /** The random tab tells the shell where it is (null when it goes). */
  registerRandom: (c: RandomControl | null) => void
  /** The voice room I'm in, open or minimised. */
  voiceRoomId: string | null
  setImmersive: (on: boolean) => void
  /** The random screen is on screen (its tab is selected and no thread covers it). */
  randomVisible: boolean
  /** Someone was reported (and so blocked): every open chat, thread or room view with them closes. */
  reported: (partnerId: string) => void
  onReported: (listener: (partnerId: string) => void) => () => void
  signedOut: () => void
  /** Unread counts (null until the first answer). */
  badges: Badges | null
  refreshBadges: () => void
  openInbox: () => void
}

export const AppContext = createContext<AppState | null>(null)

export function useApp() {
  const ctx = useContext(AppContext)
  if (!ctx) throw new Error('useApp must be used inside <AppShell>')
  return ctx
}

/** Runs `listener` whenever someone is reported, from any screen. */
export function useReported(listener: (partnerId: string) => void) {
  const { onReported } = useApp()
  const ref = useRef(listener)
  ref.current = listener
  useEffect(() => onReported((id) => ref.current(id)), [onReported])
}
