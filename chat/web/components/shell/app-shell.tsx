'use client'

import { AnimatePresence } from 'motion/react'
import * as m from 'motion/react-m'
import { Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import useSWR, { SWRConfig, useSWRConfig } from 'swr'
import { useBackend } from '@/lib/backend-context'
import type { Entitlements, Profile } from '@/lib/backend'
import { planLive } from '@/lib/live-session'
import { AppContext, type AppState, type RandomControl, type Tab, type Thread } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Modal, NightContext } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useSocialT } from '@/components/social/social-text'
import type { VoiceControl } from '@/components/screens/voice/voice-session'
import { useToast } from '@/components/ui/toast'
import { onboardingDue } from '@/components/social/first-run'
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
  InboxSheet,
  Onboarding,
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
  const [inboxOpen, setInboxOpen] = useState(false)
  const [inboxUsed, setInboxUsed] = useState(false)
  const [onboarding, setOnboarding] = useState(() => onboardingDue(!!initialMe.interests?.length))
  const { mutate } = useSWRConfig()
  const { st } = useSocialT()
  // One live session at a time (lib/live-session.ts): the room and the random tab say where they are, and a step
  // that would run both asks first.
  const voiceCtl = useRef<VoiceControl | null>(null)
  const randomCtl = useRef<RandomControl | null>(null)
  const [ask, setAsk] = useState<{ kind: 'leaveRoom'; title: string; host: boolean } | { kind: 'endChat'; partner: string } | null>(null)
  const [askBusy, setAskBusy] = useState(false)
  const answer = useRef<((ok: boolean) => void) | null>(null)
  const voiceRef = useRef(voice)
  voiceRef.current = voice

  const settle = (ok: boolean) => {
    answer.current?.(ok)
    answer.current = null
    setAsk(null)
  }
  const freeForRoom = useCallback(
    () =>
      new Promise<boolean>((resolve) => {
        const plan = planLive({ room: null, random: randomCtl.current?.live() ?? { phase: 'idle' } }, { kind: 'room', roomId: '' })
        if (plan.do === 'stopSearch') randomCtl.current?.cancelSearch()
        if (plan.do !== 'confirmEndChat') return resolve(true)
        answer.current?.(false)
        answer.current = resolve
        setAsk({ kind: 'endChat', partner: plan.partner })
      }),
    [],
  )
  const freeForRandom = useCallback(
    () =>
      new Promise<boolean>((resolve) => {
        const v = voiceRef.current
        const room = v ? { id: v.id, title: voiceCtl.current?.title ?? '' } : null
        const plan = planLive({ room, random: { phase: 'idle' } }, { kind: 'random' })
        if (plan.do !== 'confirmLeaveRoom') return resolve(true)
        answer.current?.(false)
        answer.current = resolve
        setAsk({ kind: 'leaveRoom', title: plan.title, host: !!voiceCtl.current?.isHost })
      }),
    [],
  )
  /** "Leave and start" / "End chat and join": what Leave or leaving the chat would do, then the step goes ahead. */
  const confirmAsk = async () => {
    if (!ask) return
    setAskBusy(true)
    let ok: boolean
    if (ask.kind === 'leaveRoom') ok = voiceCtl.current ? await voiceCtl.current.leave() : (setVoice(null), true)
    else ok = (await randomCtl.current?.endChat()) ?? true
    setAskBusy(false)
    settle(ok)
  }
  const registerRandom = useCallback((c: RandomControl | null) => {
    randomCtl.current = c
  }, [])
  const openVoice = useCallback(
    (id: string) => {
      if (voiceRef.current?.id === id) return setVoice((v) => v && { ...v, expanded: true })
      void freeForRoom().then((ok) => ok && setVoice({ id, expanded: true }))
    },
    [freeForRoom],
  )

  // Unread counts. Asking also tells the server the app is open (no phone pushes meanwhile): at start, every 30 s
  // while the tab is visible, and at once when one of my notifications arrives or changes.
  const { data: badges } = useSWR('badges', () => backend.badges(), {
    refreshInterval: 30_000, refreshWhenHidden: false, dedupingInterval: 2_000, focusThrottleInterval: 10_000,
  })
  const refreshBadges = useCallback(() => void mutate('badges'), [mutate])
  useEffect(
    () =>
      backend.inboxSignals(() => {
        void mutate('badges')
        void mutate('inbox')
        void mutate('friends')
      }),
    [backend, mutate],
  )

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

  // A shared room link opens that room: /r/<id> for a public room, /r/<code> for a private one (the code lets the
  // person in; yaari-app.tsx keeps it in sessionStorage, also through sign-up), and the older /?voice=<id>.
  useEffect(() => {
    let link: string | null = null
    try {
      link = sessionStorage.getItem('yaari_link')
      sessionStorage.removeItem('yaari_link')
    } catch {
      /* storage blocked */
    }
    const voiceParam = new URLSearchParams(location.search).get('voice')
    if (voiceParam) history.replaceState(null, '', location.pathname)
    link = voiceParam ?? link
    if (!link) return
    if (/^v-[0-9a-z]+$/i.test(link)) return openVoice(link)
    if (!/^[A-Z0-9]{6}$/i.test(link)) return
    backend.joinVoiceCode(link.toUpperCase()).then(openVoice, (err) => toast.error(err))
  }, [backend, toast, openVoice])

  // Once the app has settled, fetch every other screen in the background: later tab switches are instant and
  // still work if the connection drops.
  useEffect(() => {
    const id = setTimeout(prefetchScreens, 3000)
    return () => clearTimeout(id)
  }, [])

  // Going anywhere else in the app keeps me in the room: it shrinks to its bar (audio and music go on).
  const minimiseVoice = useCallback(() => setVoice((v) => (v?.expanded ? { ...v, expanded: false } : v)), [])
  const setTab = useCallback(
    (next: Tab) => {
      setThread(null)
      setTabState(next)
      minimiseVoice()
    },
    [minimiseVoice],
  )

  const openThread = useCallback(
    async (partner: Profile, matchId?: string) => {
      setProfileTarget(null)
      try {
        const id = matchId ?? (await backend.friendThread(partner.id))
        setThread({ matchId: id, partner })
        minimiseVoice()
      } catch (err) {
        toast.error(err)
      }
    },
    [backend, toast, minimiseVoice],
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
      openVoice,
      freeForRoom,
      freeForRandom,
      registerRandom,
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
      badges: badges ?? null,
      refreshBadges,
      openInbox: () => {
        setInboxUsed(true)
        setInboxOpen(true)
      },
    }),
    [me, phone, entitlements, tab, setTab, lang, setLang, t, openThread, openVoice, freeForRoom, freeForRandom, onSignedOut, thread, reportListeners, voice?.id, badges, refreshBadges],
  )

  const randomVisible = tab === 'random' && !thread
  const voiceOpen = !!voice?.expanded
  const night = randomVisible || thread !== null || voiceOpen
  const busyScreen = (randomVisible && immersive) || thread !== null
  const hideTabs = busyScreen || voiceOpen
  // The room's bar sits above the navigation (phones) or in the corner (wide screens): screens keep room for it.
  const pillShown = !!voice && !voice.expanded && !busyScreen

  return (
    <AppContext.Provider value={state}>
      <NightContext.Provider value={night}>
        <div className={cn('min-h-dvh bg-background text-foreground', night && 'theme-night')}>
          <div className="mx-auto flex max-w-[1520px]">
            <Sidebar className="hidden md:flex" />
            <main className="app-frame relative flex h-dvh min-w-0 flex-1 flex-col overflow-hidden">
              {/* Screens are layered, never stacked: a tab fading out must not push the next one aside. */}
              {/* While a voice room is full screen, what's under it can't be reached by keyboard or screen reader. */}
              <div className={cn('absolute inset-0 flex-col', randomVisible ? 'flex' : 'hidden', pillShown && 'pb-[5.5rem] md:pb-24')} inert={voiceOpen}>
                <RandomScreen active={randomVisible} />
              </div>
              <AnimatePresence mode="wait" initial={false} custom={randomVisible}>
                {!randomVisible && !thread ? (
                  <m.div
                    key={tab}
                    custom={randomVisible}
                    variants={TAB_MOTION}
                    className={cn('absolute inset-0 flex flex-col', !hideTabs && (pillShown ? 'pb-[9.5rem] md:pb-24' : 'pb-16 md:pb-0'))}
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
                        <ThreadScreen
                          thread={thread}
                          onClose={() => {
                            setThread(null)
                            refreshBadges() // what was read there
                          }}
                        />
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
                      control={voiceCtl}
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
        {inboxUsed ? (
          <ScreenBoundary>
            <Suspense fallback={null}>
              <InboxSheet open={inboxOpen} onClose={() => setInboxOpen(false)} />
            </Suspense>
          </ScreenBoundary>
        ) : null}
        {onboarding ? (
          <ScreenBoundary>
            <Suspense fallback={null}>
              <Onboarding onDone={() => setOnboarding(false)} />
            </Suspense>
          </ScreenBoundary>
        ) : null}
        <Modal
          open={!!ask}
          onClose={() => !askBusy && settle(false)}
          title={ask?.kind === 'leaveRoom' ? st('live.leaveRoomTitle', { room: ask.title }) : ask ? st('live.endChatTitle', { name: ask.partner }) : ''}
          description={ask?.kind === 'leaveRoom' ? (ask.host ? st('live.endRoomBody') : st('live.leaveRoomBody')) : st('live.endChatBody')}
        >
          <div className="flex gap-3 p-6 pt-5">
            <Button variant="outline" className="flex-1" disabled={askBusy} onClick={() => settle(false)} data-autofocus>
              {st('live.stay')}
            </Button>
            <Button variant={ask?.kind === 'leaveRoom' && ask.host ? 'danger' : 'brand'} className="flex-1" loading={askBusy} onClick={() => void confirmAsk()}>
              {ask?.kind === 'leaveRoom' ? st('live.leaveAndStart') : st('live.endAndJoin')}
            </Button>
          </div>
        </Modal>
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
