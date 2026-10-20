'use client'

import { AnimatePresence } from 'motion/react'
import * as m from 'motion/react-m'
import { Suspense, useCallback, useEffect, useMemo, useState } from 'react'
import { SWRConfig } from 'swr'
import { useBackend } from '@/lib/backend-context'
import type { Entitlements, Profile } from '@/lib/backend'
import { AppContext, type AppState, type Tab, type Thread } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { NightContext } from '@/components/ui/modal'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'
import { BottomTabs, Sidebar } from './nav'
import { RandomScreen } from '@/components/screens/random/random-screen'
import {
  MeScreen,
  Paywall,
  prefetchScreens,
  ProfileSheet,
  RecentScreen,
  RightPanel,
  RoomsScreen,
  SettingsScreen,
  ThreadScreen,
  VoiceHost,
} from './lazy-screens'
import { ScreenBoundary, ScreenLoading } from '@/components/ui/screen-boundary'

/** Tab change: a short fade (Material "fade through"). Going to Random the light screen goes at once. */
const TAB_MOTION = {
  enter: { opacity: 0 },
  shown: { opacity: 1, transition: { duration: 0.15, ease: [0.2, 0, 0, 1] as const } },
  leave: (toRandom: boolean) => (toRandom ? { opacity: 0, transition: { duration: 0 } } : { opacity: 0, transition: { duration: 0.09 } }),
}

function useMediaQuery(query: string) {
  const [match, setMatch] = useState(false)
  useEffect(() => {
    const mq = window.matchMedia(query)
    const update = () => setMatch(mq.matches)
    update()
    mq.addEventListener('change', update)
    return () => mq.removeEventListener('change', update)
  }, [query])
  return match
}

interface Props {
  initialMe: Profile
  entitlements: Entitlements
  phone: string | null
  onSignedOut: () => void
}

export function AppShell(props: Props) {
  // A fresh cache per signed-in session, so nothing from a previous account is ever shown.
  const [cache] = useState(() => new Map())
  return (
    <SWRConfig value={{ provider: () => cache, revalidateOnFocus: true }}>
      <Shell {...props} />
    </SWRConfig>
  )
}

function Shell({ initialMe, entitlements: initialEntitlements, phone, onSignedOut }: Props) {
  const backend = useBackend()
  const toast = useToast()
  const { t, lang, setLang } = useI18n()
  const [me, setMe] = useState(initialMe)
  const [tab, setTabState] = useState<Tab>('random')
  const [thread, setThread] = useState<Thread | null>(null)
  const [profileTarget, setProfileTarget] = useState<{ userId: string; matchId: string | null } | null>(null)
  const [paywallOpen, setPaywallOpen] = useState(false)
  // Overlays are downloaded the first time they open, then stay mounted so they can animate out.
  const [paywallUsed, setPaywallUsed] = useState(false)
  const [profileUsed, setProfileUsed] = useState(false)
  const [immersive, setImmersive] = useState(false)
  // The voice room I'm in: full screen, or minimised to a pill while I use the rest of the app.
  const [voice, setVoice] = useState<{ id: string; expanded: boolean } | null>(null)
  const [entitlements, setEntitlements] = useState(initialEntitlements)
  const [reportListeners] = useState(() => new Set<(id: string) => void>())
  // The friends/rooms panel exists only on wide screens; phones never load its data.
  const wide = useMediaQuery('(min-width: 1280px)')

  // Plus is bought in the Android app and can expire: re-read it when the tab comes back and every few minutes
  // (only while the tab is visible).
  useEffect(() => {
    const refresh = () => backend.entitlements().then(setEntitlements, () => undefined)
    const onVisible = () => document.visibilityState === 'visible' && void refresh()
    document.addEventListener('visibilitychange', onVisible)
    const timer = setInterval(() => document.visibilityState === 'visible' && void refresh(), 5 * 60_000)
    return () => {
      document.removeEventListener('visibilitychange', onVisible)
      clearInterval(timer)
    }
  }, [backend])

  // A shared room link (/?voice=<id>) opens that room.
  useEffect(() => {
    const id = new URLSearchParams(location.search).get('voice')
    if (!id) return
    setVoice({ id, expanded: true })
    history.replaceState(null, '', location.pathname)
  }, [])

  // Once the app has settled, fetch every other screen in the background: later tab switches are instant and
  // still work if the connection drops.
  useEffect(() => {
    const id = setTimeout(prefetchScreens, 3000)
    return () => clearTimeout(id)
  }, [])

  const setTab = useCallback((next: Tab) => {
    setThread(null)
    setTabState(next)
  }, [])

  const openThread = useCallback(
    async (partner: Profile, matchId?: string) => {
      setProfileTarget(null)
      try {
        const id = matchId ?? (await backend.friendThread(partner.id))
        setThread({ matchId: id, partner })
      } catch (err) {
        toast.error(err)
      }
    },
    [backend, toast],
  )

  const state = useMemo<AppState>(
    () => ({
      me,
      setMe,
      phone,
      entitlements,
      canChooseGender: entitlements.premium || entitlements.genderPass,
      tab,
      setTab,
      lang,
      setLang,
      t,
      openProfile: (userId, opts) => {
        setProfileUsed(true)
        setProfileTarget({ userId, matchId: opts?.matchId ?? null })
      },
      openPaywall: () => {
        setPaywallUsed(true)
        setPaywallOpen(true)
      },
      openThread: (partner, matchId) => void openThread(partner, matchId),
      openVoice: (id) => setVoice({ id, expanded: true }),
      voiceRoomId: voice?.id ?? null,
      setImmersive,
      randomVisible: tab === 'random' && thread === null,
      reported: (id) => {
        setThread((th) => (th?.partner.id === id ? null : th))
        setProfileTarget(null)
        reportListeners.forEach((l) => l(id))
      },
      onReported: (l) => {
        reportListeners.add(l)
        return () => void reportListeners.delete(l)
      },
      signedOut: onSignedOut,
    }),
    [me, phone, entitlements, tab, setTab, lang, setLang, t, openThread, onSignedOut, thread, reportListeners, voice?.id],
  )

  const randomVisible = tab === 'random' && !thread
  const voiceOpen = !!voice?.expanded
  const night = randomVisible || thread !== null || voiceOpen
  const busyScreen = (randomVisible && immersive) || thread !== null
  const hideTabs = busyScreen || voiceOpen

  return (
    <AppContext.Provider value={state}>
      <NightContext.Provider value={night}>
        <div className={cn('min-h-dvh bg-background text-foreground', night && 'theme-night')}>
          <div className="mx-auto flex max-w-[1520px]">
            <Sidebar className="hidden md:flex" />
            <main className="app-frame relative flex h-dvh min-w-0 flex-1 flex-col overflow-hidden">
              {/* Screens are layered, never stacked: a tab fading out must not push the next one aside. */}
              {/* While a voice room is full screen, what's under it can't be reached by keyboard or screen reader. */}
              <div className={cn('absolute inset-0 flex-col', randomVisible ? 'flex' : 'hidden')} inert={voiceOpen}>
                <RandomScreen active={randomVisible} />
              </div>
              <AnimatePresence mode="wait" initial={false} custom={randomVisible}>
                {!randomVisible && !thread ? (
                  <m.div
                    key={tab}
                    custom={randomVisible}
                    variants={TAB_MOTION}
                    className={cn('absolute inset-0 flex flex-col', !hideTabs && 'pb-16 md:pb-0')}
                    inert={voiceOpen}
                    initial="enter"
                    animate="shown"
                    exit="leave"
                  >
                    <ScreenBoundary key={tab}>
                      <Suspense fallback={<ScreenLoading />}>
                        {tab === 'rooms' ? <RoomsScreen /> : null}
                        {tab === 'recent' ? <RecentScreen /> : null}
                        {tab === 'me' ? <MeScreen /> : null}
                        {tab === 'settings' ? <SettingsScreen /> : null}
                      </Suspense>
                    </ScreenBoundary>
                  </m.div>
                ) : null}
              </AnimatePresence>
              <AnimatePresence>
                {thread ? (
                  <m.div
                    key={thread.matchId}
                    className="absolute inset-0 z-20 flex flex-col bg-background"
                    initial={{ opacity: 0, x: 24 }}
                    animate={{ opacity: 1, x: 0 }}
                    exit={{ opacity: 0, x: 24 }}
                    transition={{ type: 'tween', duration: 0.2, ease: [0.2, 0, 0, 1] }}
                  >
                    <ScreenBoundary>
                      <Suspense fallback={<ScreenLoading />}>
                        <ThreadScreen thread={thread} onClose={() => setThread(null)} />
                      </Suspense>
                    </ScreenBoundary>
                  </m.div>
                ) : null}
              </AnimatePresence>
              {voice ? (
                <ScreenBoundary key={voice.id}>
                  <Suspense fallback={null}>
                    <VoiceHost
                      roomId={voice.id}
                      expanded={voice.expanded}
                      pillHidden={busyScreen}
                      onExpand={() => setVoice((v) => v && { ...v, expanded: true })}
                      onMinimise={() => setVoice((v) => v && { ...v, expanded: false })}
                      onClose={() => setVoice(null)}
                    />
                  </Suspense>
                </ScreenBoundary>
              ) : null}
            </main>
            {wide && !voiceOpen ? (
              <Suspense fallback={null}>
                <RightPanel className="flex" />
              </Suspense>
            ) : null}
          </div>
          <BottomTabs hidden={hideTabs} />
        </div>

        {profileUsed ? (
          <ScreenBoundary>
            <Suspense fallback={null}>
              <ProfileSheet target={profileTarget} onClose={() => setProfileTarget(null)} />
            </Suspense>
          </ScreenBoundary>
        ) : null}
        {paywallUsed ? (
          <ScreenBoundary>
            <Suspense fallback={null}>
              <Paywall open={paywallOpen} onClose={() => setPaywallOpen(false)} />
            </Suspense>
          </ScreenBoundary>
        ) : null}
      </NightContext.Provider>
    </AppContext.Provider>
  )
}
