'use client'

import { AnimatePresence, motion } from 'motion/react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { SWRConfig } from 'swr'
import { useBackend } from '@/lib/backend-context'
import type { Entitlements, Profile } from '@/lib/backend'
import { DICTIONARIES, type Lang, type TKey } from '@/lib/i18n'
import { AppContext, type AppState, type Tab, type Thread } from '@/components/app-state'
import { NightContext } from '@/components/ui/modal'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'
import { BottomTabs, Sidebar } from './nav'
import { RightPanel } from './right-panel'
import { RandomScreen } from '@/components/screens/random/random-screen'
import { RoomsScreen } from '@/components/screens/rooms/rooms-screen'
import { RecentScreen } from '@/components/screens/recent/recent-screen'
import { MeScreen } from '@/components/screens/me/me-screen'
import { SettingsScreen } from '@/components/screens/settings/settings-screen'
import { ThreadScreen } from '@/components/screens/random/thread-screen'
import { ProfileSheet } from '@/components/screens/profile/profile-sheet'
import { Paywall } from '@/components/screens/paywall'

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
  const [me, setMe] = useState(initialMe)
  const [tab, setTabState] = useState<Tab>('random')
  const [lang, setLang] = useState<Lang>('en')
  const [thread, setThread] = useState<Thread | null>(null)
  const [profileTarget, setProfileTarget] = useState<{ userId: string; matchId: string | null } | null>(null)
  const [paywallOpen, setPaywallOpen] = useState(false)
  const [immersive, setImmersive] = useState(false)
  const [openRoomId, setOpenRoomId] = useState<string | null>(null)
  const [entitlements, setEntitlements] = useState(initialEntitlements)

  // Plus is bought in the Android app and can expire: re-read it when the tab comes back and every few minutes.
  useEffect(() => {
    const refresh = () => backend.entitlements().then(setEntitlements, () => undefined)
    const onVisible = () => document.visibilityState === 'visible' && void refresh()
    document.addEventListener('visibilitychange', onVisible)
    const timer = setInterval(refresh, 5 * 60_000)
    return () => {
      document.removeEventListener('visibilitychange', onVisible)
      clearInterval(timer)
    }
  }, [backend])

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

  const t = useCallback((key: TKey) => DICTIONARIES[lang][key], [lang])

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
      openProfile: (userId, opts) => setProfileTarget({ userId, matchId: opts?.matchId ?? null }),
      openPaywall: () => setPaywallOpen(true),
      openThread: (partner, matchId) => void openThread(partner, matchId),
      setImmersive,
      signedOut: onSignedOut,
    }),
    [me, phone, entitlements, tab, setTab, lang, t, openThread, onSignedOut],
  )

  const randomVisible = tab === 'random' && !thread
  const night = randomVisible || thread !== null
  const hideTabs = (randomVisible && immersive) || thread !== null || (tab === 'rooms' && openRoomId !== null)

  return (
    <AppContext.Provider value={state}>
      <NightContext.Provider value={night}>
        <div
          lang={lang === 'hi' ? 'hi' : 'en'}
          className={cn('min-h-dvh bg-background text-foreground transition-colors duration-500', night && 'theme-night')}
        >
          <div className="mx-auto flex max-w-[1520px]">
            <Sidebar className="hidden md:flex" />
            <main className="relative flex h-dvh min-w-0 flex-1 flex-col overflow-hidden">
              <div className={cn('min-h-0 flex-1 flex-col', randomVisible ? 'flex' : 'hidden')}>
                <RandomScreen active={randomVisible} />
              </div>
              <AnimatePresence mode="wait" initial={false}>
                {!randomVisible && !thread ? (
                  <motion.div
                    key={tab}
                    className={cn('flex min-h-0 flex-1 flex-col', !hideTabs && 'pb-16 md:pb-0')}
                    initial={{ opacity: 0, y: 14 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -8 }}
                    transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
                  >
                    {tab === 'rooms' ? <RoomsScreen openRoomId={openRoomId} onOpenRoom={setOpenRoomId} /> : null}
                    {tab === 'recent' ? <RecentScreen /> : null}
                    {tab === 'me' ? <MeScreen /> : null}
                    {tab === 'settings' ? <SettingsScreen /> : null}
                  </motion.div>
                ) : null}
              </AnimatePresence>
              <AnimatePresence>
                {thread ? (
                  <motion.div
                    key={thread.matchId}
                    className="absolute inset-0 z-20 flex flex-col bg-background"
                    initial={{ opacity: 0, x: 40 }}
                    animate={{ opacity: 1, x: 0 }}
                    exit={{ opacity: 0, x: 40 }}
                    transition={{ type: 'spring', stiffness: 360, damping: 36 }}
                  >
                    <ThreadScreen thread={thread} onClose={() => setThread(null)} />
                  </motion.div>
                ) : null}
              </AnimatePresence>
            </main>
            <RightPanel
              className="hidden xl:flex"
              onOpenRoom={(id) => {
                setOpenRoomId(id)
                setTab('rooms')
              }}
            />
          </div>
          <BottomTabs hidden={hideTabs} />
        </div>

        <ProfileSheet
          target={profileTarget}
          onClose={() => setProfileTarget(null)}
        />
        <Paywall open={paywallOpen} onClose={() => setPaywallOpen(false)} />
      </NightContext.Provider>
    </AppContext.Provider>
  )
}
