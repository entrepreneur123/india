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
  recent: () => retryImport(() => import('@/components/screens/recent/recent-screen')),
  me: () => retryImport(() => import('@/components/screens/me/me-screen')),
  settings: () => retryImport(() => import('@/components/screens/settings/settings-screen')),
  thread: () => retryImport(() => import('@/components/screens/random/thread-screen')),
  profile: () => retryImport(() => import('@/components/screens/profile/profile-sheet')),
  paywall: () => retryImport(() => import('@/components/screens/paywall')),
  chat: () => retryImport(() => import('@/components/screens/chat/chat-view')),
  roomDialogs: () => retryImport(() => import('@/components/screens/rooms/room-dialogs')),
  voice: () => retryImport(() => import('@/components/screens/voice/voice-session')),
  panel: () => retryImport(() => import('./right-panel')),
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
export const RecentScreen = lazy(() => loaders.recent().then((m) => ({ default: m.RecentScreen })))
export const MeScreen = lazy(() => loaders.me().then((m) => ({ default: m.MeScreen })))
export const SettingsScreen = lazy(() => loaders.settings().then((m) => ({ default: m.SettingsScreen })))
export const ThreadScreen = lazy(() => loaders.thread().then((m) => ({ default: m.ThreadScreen })))
export const ProfileSheet = lazy(() => loaders.profile().then((m) => ({ default: m.ProfileSheet })))
export const Paywall = lazy(() => loaders.paywall().then((m) => ({ default: m.Paywall })))
export const VoiceHost = lazy(() => loaders.voice().then((m) => ({ default: m.VoiceHost })))
/** The friends / rooms column exists only on wide screens, so phones never download it. */
export const RightPanel = lazy(() => loaders.panel().then((m) => ({ default: m.RightPanel })))
