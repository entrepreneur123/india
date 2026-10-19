'use client'

import { lazy } from 'react'
import type { Tab } from '@/components/app-state'

// Screens that are not on screen at start are separate downloads, fetched when first needed (or when the
// pointer reaches their tab, so they are usually ready by the time of the click).
const loaders = {
  rooms: () => import('@/components/screens/rooms/rooms-screen'),
  recent: () => import('@/components/screens/recent/recent-screen'),
  me: () => import('@/components/screens/me/me-screen'),
  settings: () => import('@/components/screens/settings/settings-screen'),
  thread: () => import('@/components/screens/random/thread-screen'),
  profile: () => import('@/components/screens/profile/profile-sheet'),
  paywall: () => import('@/components/screens/paywall'),
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
