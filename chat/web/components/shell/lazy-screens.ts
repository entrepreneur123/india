'use client'

import { lazy } from 'react'
import type { Tab } from '@/components/app-state'
import { retryImport } from '@/lib/retry-import'

// Screens that are not on screen at start are separate downloads, fetched when first needed (or when the
// pointer reaches their tab, so they are usually ready by the time of the click).
// A failed download is retried (see retryImport), and once the app is idle every screen is fetched ahead
// (prefetchScreens), so switching tabs later works even if the network drops.
const loaders = {
  rooms: () => retryImport(() => import('@/components/screens/rooms/rooms-screen')),
  post: () => retryImport(() => import('@/components/social/posts/post-sheet')),
  recent: () => retryImport(() => import('@/components/screens/recent/recent-screen')),
  explore: () => retryImport(() => import('@/components/screens/explore/explore-screen')),
  me: () => retryImport(() => import('@/components/screens/me/me-screen')),
  settings: () => retryImport(() => import('@/components/screens/settings/settings-screen')),
  thread: () => retryImport(() => import('@/components/screens/random/thread-screen')),
  profile: () => retryImport(() => import('@/components/screens/profile/profile-sheet')),
  chat: () => retryImport(() => import('@/components/screens/chat/chat-view')),
  roomDialogs: () => retryImport(() => import('@/components/screens/rooms/room-dialogs')),
  voice: () => retryImport(() => import('@/components/screens/voice/voice-session')),
  panel: () => retryImport(() => import('./right-panel')),
  inbox: () => retryImport(() => import('@/components/social/inbox-sheet')),
  verify: () => retryImport(() => import('@/components/trust/verify-sheet')),
  looks: () => retryImport(() => import('@/components/looks/looks-sheet')),
}

/** Fetches every screen in the background, unless the user asked the browser to save data. */
export function prefetchScreens() {
  const saveData = (navigator as Navigator & { connection?: { saveData?: boolean } }).connection?.saveData
  if (saveData) return
  const wide = window.matchMedia('(min-width: 1280px)').matches
  for (const [name, load] of Object.entries(loaders)) if (name !== 'panel' || wide) void load().catch(() => undefined)
}

export function preloadScreen(name: keyof typeof loaders | Tab) {
  if (name === 'random') return
  void loaders[name]().catch(() => undefined)
}

export const RoomsScreen = lazy(() => loaders.rooms().then((m) => ({ default: m.RoomsScreen })))
export const PostSheet = lazy(() => loaders.post().then((m) => ({ default: m.PostSheet })))
export const RecentScreen = lazy(() => loaders.recent().then((m) => ({ default: m.RecentScreen })))
export const ExploreScreen = lazy(() => loaders.explore().then((m) => ({ default: m.ExploreScreen })))
export const MeScreen = lazy(() => loaders.me().then((m) => ({ default: m.MeScreen })))
export const SettingsScreen = lazy(() => loaders.settings().then((m) => ({ default: m.SettingsScreen })))
export const ThreadScreen = lazy(() => loaders.thread().then((m) => ({ default: m.ThreadScreen })))
export const ProfileSheet = lazy(() => loaders.profile().then((m) => ({ default: m.ProfileSheet })))
export const VoiceHost = lazy(() => loaders.voice().then((m) => ({ default: m.VoiceHost })))
/** The friends / rooms column exists only on wide screens, so phones never download it. */
export const InboxSheet = lazy(() => loaders.inbox().then((m) => ({ default: m.InboxSheet })))
const loadOnboarding = () => retryImport(() => import('@/components/social/onboarding'))
export const Onboarding = lazy(() => loadOnboarding().then((m) => ({ default: m.Onboarding })))
export const RightPanel = lazy(() => loaders.panel().then((m) => ({ default: m.RightPanel })))
// Identity verification and the manner explanation (035): one chunk, opened from Me, a refusal or sign-up.
export const VerifySheet = lazy(() => loaders.verify().then((m) => ({ default: m.VerifySheet })))
export const MannerLowSheet = lazy(() => loaders.verify().then((m) => ({ default: m.MannerLowSheet })))
/** The Looks shop: opened from Me and Settings. */
export const LooksSheet = lazy(() => loaders.looks().then((m) => ({ default: m.LooksSheet })))
