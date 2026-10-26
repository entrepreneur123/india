'use client'

import { createContext, useContext, useEffect, useRef } from 'react'
import type { Badges, FeedPost, ItemKind, KycResult, MyTrust, Profile } from '@/lib/backend'
import type { Lang, T } from '@/lib/i18n'
import type { RandomLive } from '@/lib/live-session'

export type Tab = 'rooms' | 'explore' | 'random' | 'recent' | 'me' | 'settings'

export interface Thread {
  matchId: string
  partner: Profile
}

/** What the shell needs from the random tab (one live session at a time: lib/live-session.ts). */
export interface RandomControl {
  live: () => RandomLive
  /** Stops a search without a word. */
  cancelSearch: () => void
  /**
   * Ends the open chat (leave_match). `force` (a room is opening): it ends here whatever the server says (inside the
   * exit lock the server ends it itself); otherwise false when it couldn't (the reason was shown).
   */
  endChat: (force: boolean) => Promise<boolean>
}

/** The random chat while another tab is open: its bar (like a room's) says who, typing, unread. */
export interface ChatBar {
  partner: Profile
  typing: boolean
  unread: number
}

/** Why the verification sheet opened: from Me, a 'verify_required' refusal, right after sign-up, or back from the check. */
export type VerifyWhy = 'me' | 'gate' | 'signup' | 'return'

/** Whether identity checks can be started ('off': no provider yet, so nobody is asked) and my latest result. */
export interface KycInfo {
  avail: 'unknown' | 'on' | 'off'
  result: KycResult | null
}

export interface AppState {
  me: Profile
  setMe: (p: Profile) => void
  phone: string | null
  tab: Tab
  setTab: (t: Tab) => void
  lang: Lang
  setLang: (l: Lang) => void
  t: T
  openProfile: (userId: string, opts?: { matchId?: string | null }) => void
  /** The Looks shop (walls and VIP too, 046; effects and bubbles, 062), on this tab. */
  openShop: (tab?: ItemKind) => void
  /** A post with its comments, over whatever is on screen. `initial`: shown while it is read again; `focus`: the comment box. */
  openPost: (postId: string, opts?: { initial?: FeedPost | null; focus?: boolean }) => void
  openThread: (partner: Profile, matchId?: string) => void
  /**
   * Enters a voice room full screen (leaving the one I'm in, if another). A random chat in progress ends (with a
   * word); a search just stops.
   */
  openVoice: (roomId: string) => void
  /** A room with a password (058): asks for it, then openVoice. */
  askRoomPassword: (roomId: string) => void
  /** Before making a room: ends a random chat or search, as openVoice does. */
  freeForRoom: () => Promise<boolean>
  /** Before a random search: leaves my room (the host's ends, as Leave does); false if that failed. */
  freeForRandom: () => Promise<boolean>
  /** The random tab tells the shell where it is (null when it goes). */
  registerRandom: (c: RandomControl | null) => void
  /** The open random chat for its bar (null: none). */
  setChatBar: (bar: ChatBar | null) => void
  /** Leaves the random chat on screen for the tab before it (the chat goes on behind its bar). */
  minimiseRandom: () => void
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
  /** My badge, manners and whether verification is required (null until the first answer). */
  trust: MyTrust | null
  refreshTrust: () => void
  kyc: KycInfo
  setKyc: (k: KycInfo) => void
  openVerify: (why?: VerifyWhy) => void
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
