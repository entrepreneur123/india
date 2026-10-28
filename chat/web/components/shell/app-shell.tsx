'use client'

import { AnimatePresence } from 'motion/react'
import * as m from 'motion/react-m'
import { Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import useSWR, { SWRConfig, useSWRConfig } from 'swr'
import { useBackend } from '@/lib/backend-context'
import type { Badges, ItemKind, Profile } from '@/lib/backend'
import { planLive } from '@/lib/live-session'
import { useBack } from '@/lib/back-stack'
import { AppContext, type AppState, type ChatBar, type KycInfo, type RandomControl, type Tab, type Thread, type VerifyWhy } from '@/components/app-state'
import { onGate } from '@/lib/gate'
import { errorCode } from '@/lib/format'
import { kycOffCached, markKycOff, markVerifyAsked, rememberRequired, verifyAsked } from '@/components/trust/store'
import { useI18n } from '@/components/i18n'
import { useSocialT } from '@/components/social/social-text'
import type { VoiceControl } from '@/components/screens/voice/voice-session'
import { useToast } from '@/components/ui/toast'
import { onboardingDue } from '@/components/social/first-run'
import { cn } from '@/lib/utils'
import { ChatPill } from './chat-pill'
import { BottomTabs, Sidebar } from './nav'
import { RandomScreen } from '@/components/screens/random/random-screen'
import type { PostTarget } from '@/components/social/posts/post-sheet'
import {
  PostSheet,
  MeScreen,
  prefetchScreens,
  ProfileSheet,
  RecentScreen,
  ExploreScreen,
  RightPanel,
  RoomsScreen,
  SettingsScreen,
  ThreadScreen,
  GroupScreen,
  VoiceHost,
  InboxSheet,
  Onboarding,
  VerifySheet,
  MannerLowSheet,
  LooksSheet,
  RoomPasswordDialog,
  JoinCodeDialog,
} from './lazy-screens'
import { ScreenBoundary, ScreenLoading } from '@/components/ui/screen-boundary'
import { IncomingCall } from '@/components/video/incoming-call'

/** Tab change: a short fade (Material "fade through"). Going to Random (already drawn underneath) the tab goes at once. */
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

function Shell({ initialMe, phone, onSignedOut }: Props) {
  const backend = useBackend()
  const toast = useToast()
  const { t, lang, setLang, rtl } = useI18n()
  const [me, setMe] = useState(initialMe)
  const [tab, setTabState] = useState<Tab>('random')
  const [thread, setThread] = useState<Thread | null>(null)
  // 11: a friend's thread in a small window beside the friends panel (wide screens), over whatever is open.
  const [mini, setMini] = useState<Thread | null>(null)
  const [group, setGroup] = useState<string | null>(null)
  const [profileTarget, setProfileTarget] = useState<{ userId: string; matchId: string | null } | null>(null)
  // Overlays are downloaded the first time they open, then stay mounted so they can animate out.
  const [shop, setShop] = useState<{ open: boolean; tab: ItemKind } | null>(null)
  const [roomKey, setRoomKey] = useState<{ id: string | null; kind: 'password' | 'code' } | null>(null)
  const [profileUsed, setProfileUsed] = useState(false)
  const [postTarget, setPostTarget] = useState<PostTarget | null>(null)
  const [postUsed, setPostUsed] = useState(false)
  const [immersive, setImmersive] = useState(false)
  // The voice room I'm in: full screen, or minimised to a pill while I use the rest of the app.
  const [voice, setVoice] = useState<{ id: string; expanded: boolean } | null>(null)
  const [reportListeners] = useState(() => new Set<(id: string) => void>())
  // The friends/rooms panel exists only on wide screens; phones never load its data.
  const wide = useMediaQuery('(min-width: 1280px)')
  const [inboxOpen, setInboxOpen] = useState(false)
  const [inboxUsed, setInboxUsed] = useState(false)
  const [onboarding, setOnboarding] = useState(() => onboardingDue())
  const { mutate } = useSWRConfig()
  // Identity and manners (035): my badge and score, whether checks are open, and the sheets they open.
  const { data: trust } = useSWR('my_trust', () => backend.myTrust(), { revalidateOnFocus: false, dedupingInterval: 5_000 })
  const refreshTrust = useCallback(() => void mutate('my_trust'), [mutate])
  const [kyc, setKyc] = useState<KycInfo>(() => ({ avail: kycOffCached() ? 'off' : 'unknown', result: null }))
  const [verify, setVerify] = useState<{ open: boolean; why: VerifyWhy }>({ open: false, why: 'me' })
  const [verifyUsed, setVerifyUsed] = useState(false)
  const [mannerLow, setMannerLow] = useState(false)
  const [mannerLowUsed, setMannerLowUsed] = useState(false)
  const openVerify = useCallback((why: VerifyWhy = 'me') => {
    setVerifyUsed(true)
    setVerify({ open: true, why })
  }, [])
  const { st } = useSocialT()
  // One live session at a time (lib/live-session.ts): entering a room ends a random chat (a search just stops), and
  // starting a search leaves the room, each with a word. The server does the same, so either side may get there first.
  const voiceCtl = useRef<VoiceControl | null>(null)
  const randomCtl = useRef<RandomControl | null>(null)
  const voiceRef = useRef(voice)
  voiceRef.current = voice
  const [chatBar, setChatBar] = useState<ChatBar | null>(null)
  const stRef = useRef(st)
  stRef.current = st

  const freeForRoom = useCallback(async () => {
    const plan = planLive({ room: null, random: randomCtl.current?.live() ?? { phase: 'idle' } }, { kind: 'room', roomId: '' })
    if (plan.do === 'stopSearch') randomCtl.current?.cancelSearch()
    if (plan.do === 'endChat') {
      await randomCtl.current?.endChat(true)
      toast.show(stRef.current('live.endedChat', { name: plan.partner }), 'info')
    }
    return true
  }, [toast])
  const freeForRandom = useCallback(async () => {
    const v = voiceRef.current
    const plan = planLive({ room: v ? { id: v.id, title: voiceCtl.current?.title ?? '' } : null, random: { phase: 'idle' } }, { kind: 'random' })
    if (plan.do !== 'leaveRoom') return true
    const ok = voiceCtl.current ? await voiceCtl.current.leave() : (setVoice(null), true)
    if (ok && plan.title) toast.show(stRef.current('live.leftRoom', { room: plan.title }), 'info')
    return ok
  }, [toast])
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
  // My friends, read once at start on every screen size: rooms show a VIP friend as they are (35, supabaseBackend).
  useSWR('friends', () => backend.friends(), { revalidateOnFocus: false })
  // 48: my groups, read at start and again whenever a line arrives in one of them (Realtime, no polling); their
  // unread counts join the Recent tab's.
  const { data: groups } = useSWR('groups', () => backend.groups(), { revalidateOnFocus: false })
  const groupIds = groups?.map((g) => g.id).join(',') ?? ''
  useEffect(() => (groupIds ? backend.watchGroups(groupIds.split(','), () => void mutate('groups')) : undefined), [backend, groupIds, mutate])
  const allBadges = useMemo(
    () => (badges && groups?.length ? { ...badges, chats: { ...badges.chats, ...Object.fromEntries(groups.map((g) => [`g:${g.id}`, g.unread])) } } : badges),
    [badges, groups],
  )
  useEffect(
    () =>
      backend.inboxSignals(() => {
        void mutate('badges')
        void mutate('inbox')
        void mutate('friends')
      }),
    [backend, mutate],
  )

  // 'verify_required' and 'manner_low' open their sheets instead of a message (components/ui/toast.tsx hands them over).
  useEffect(
    () =>
      onGate((code) => {
        void mutate('my_trust')
        if (code === 'verify_required') return openVerify('gate')
        setMannerLowUsed(true)
        setMannerLow(true)
      }),
    [openVerify, mutate],
  )

  // Back from the identity check (/?kyc=done): read the result and show it; the address goes back to what it was.
  useEffect(() => {
    const q = new URLSearchParams(location.search)
    if (q.get('kyc') !== 'done') return
    q.delete('kyc')
    const rest = q.toString()
    history.replaceState(history.state, '', location.pathname + (rest ? `?${rest}` : '') + location.hash)
    openVerify('return')
  }, [openVerify])

  // Unverified: are checks open yet, and how did my last one end? Asked once a start (the "coming soon" answer is
  // believed for a while, so nobody is nagged about something they can't do).
  const unverified = trust ? !trust.verified : false
  useEffect(() => {
    if (!unverified || kyc.avail !== 'unknown') return
    let alive = true
    backend.kycCheck().then(
      (result) => alive && setKyc({ avail: 'on', result }),
      (err) => {
        if (!alive || errorCode(err) !== 'kyc_unavailable') return
        markKycOff(true)
        setKyc({ avail: 'off', result: null })
      },
    )
    return () => {
      alive = false
    }
  }, [backend, unverified, kyc.avail])
  useEffect(() => {
    if (trust) rememberRequired(trust.required)
  }, [trust])

  // Right after sign-up (once the first-run steps are done): the check, once per browser, or at every start while
  // verification is required. Not while checks aren't open.
  const asked = useRef(false)
  useEffect(() => {
    if (asked.current || onboarding || !trust || trust.verified || kyc.avail !== 'on') return
    if (kyc.result === 'pending' || kyc.result === 'duplicate' || kyc.result === 'minor') return
    if (!trust.required && verifyAsked()) return
    asked.current = true
    markVerifyAsked()
    openVerify('signup')
  }, [onboarding, trust, kyc, openVerify])

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
  /** The last tab other than Random: where minimising a random chat goes back to. */
  const lastTab = useRef<Tab>('rooms')
  // Back goes through the tabs visited before, each once (Instagram's way); Random, the home, starts over.
  const tabRef = useRef(tab)
  tabRef.current = tab
  const trail = useRef<Tab[]>([])
  const goTab = useCallback(
    (next: Tab, back: boolean) => {
      const cur = tabRef.current
      if (!back && next !== cur) trail.current = next === 'random' ? [] : [...trail.current.filter((x) => x !== next && x !== cur), cur]
      setThread(null)
      setGroup(null)
      setTabState(next)
      if (next !== 'random') lastTab.current = next
      minimiseVoice()
    },
    [minimiseVoice],
  )
  const setTab = useCallback((next: Tab) => goTab(next, false), [goTab])
  const minimiseRandom = useCallback(() => setTab(lastTab.current), [setTab])
  const closeThread = useCallback(() => {
    setThread(null)
    refreshBadges() // what was read there
  }, [refreshBadges])
  useBack(tab !== 'random', () => goTab(trail.current.pop() ?? 'random', true))
  useBack(!!thread, closeThread)
  const closeMini = useCallback(() => {
    setMini(null)
    refreshBadges()
  }, [refreshBadges])
  useBack(!!mini, closeMini)
  useEffect(() => {
    if (!wide) setMini(null) // the window lives beside the panel only
  }, [wide])
  useBack(!!group, () => setGroup(null))
  useBack(!!voice?.expanded, minimiseVoice)

  const openThread = useCallback(
    async (partner: Profile, matchId?: string) => {
      setProfileTarget(null)
      try {
        const id = matchId ?? (await backend.friendThread(partner.id))
        setThread({ matchId: id, partner })
        setMini((m) => (m?.matchId === id ? null : m)) // one view of a thread at a time
        minimiseVoice()
      } catch (err) {
        toast.error(err)
      }
    },
    [backend, toast, minimiseVoice],
  )

  // Only what changes the panel's line (not the chat bar's typing and unread counts).
  const myPresence: AppState['myPresence'] = voice ? 'room' : chatBar ? 'chat' : immersive ? 'matching' : 'online'
  // 088: friends see me come online, change what I'm doing and leave at once (no waiting for their next poll).
  // Shortly after a change, so the server already has the room, chat or queue the change came from.
  useEffect(() => {
    const ping = () => backend.presencePing(true)
    const timer = setTimeout(ping, 1_500)
    const onShow = () => document.visibilityState === 'visible' && ping()
    const onHide = () => backend.presencePing(false)
    document.addEventListener('visibilitychange', onShow)
    window.addEventListener('pagehide', onHide)
    return () => {
      clearTimeout(timer)
      document.removeEventListener('visibilitychange', onShow)
      window.removeEventListener('pagehide', onHide)
    }
  }, [backend, myPresence, me.hideOnline])
  // My older profile photos get their small copies (lib/thumbs.ts), once, after the first screen is up.
  useEffect(() => {
    const timer = setTimeout(() => void backend.backfillThumbs?.().catch(() => undefined), 5_000)
    return () => clearTimeout(timer)
  }, [backend])
  // ...and their changes reach me the same way.
  useEffect(() => backend.friendSignals(() => void mutate('friend-activity')), [backend, mutate])
  const state = useMemo<AppState>(
    () => ({
      me,
      setMe,
      phone,
      tab,
      setTab,
      lang,
      setLang,
      t,
      openProfile: (userId, opts) => {
        setProfileUsed(true)
        setProfileTarget({ userId, matchId: opts?.matchId ?? null })
      },
      openPost: (postId, opts) => {
        setPostUsed(true)
        setPostTarget({ postId, initial: opts?.initial ?? null, focus: opts?.focus, reactions: opts?.reactions })
      },
      openShop: (tab = 'badge') => setShop({ open: true, tab }),
      openThread: (partner, matchId) => void openThread(partner, matchId),
      openChat: (partner) => {
        if (!wide) return void openThread(partner)
        if (thread?.partner.id === partner.id) return
        backend.friendThread(partner.id).then((matchId) => setMini({ matchId, partner }), (err) => toast.error(err))
      },
      openGroup: (groupId) => {
        setProfileTarget(null)
        setThread(null)
        setGroup(groupId)
        minimiseVoice()
      },
      openVoice,
      askRoomKey: (id, kind) => setRoomKey({ id, kind }),
      freeForRoom,
      freeForRandom,
      registerRandom,
      setChatBar,
      minimiseRandom,
      voiceRoomId: voice?.id ?? null,
      myPresence,
      setImmersive,
      randomVisible: tab === 'random' && thread === null && group === null,
      reported: (id) => {
        setThread((th) => (th?.partner.id === id ? null : th))
        setMini((th) => (th?.partner.id === id ? null : th))
        setProfileTarget(null)
        reportListeners.forEach((l) => l(id))
      },
      onReported: (l) => {
        reportListeners.add(l)
        return () => void reportListeners.delete(l)
      },
      signedOut: onSignedOut,
      badges: allBadges ?? null,
      refreshBadges,
      openInbox: () => {
        setInboxUsed(true)
        setInboxOpen(true)
        // Opening marks everything read (inbox-sheet.tsx): the bell clears at once, before its code has even loaded.
        void mutate('badges', (b?: Badges) => b && { ...b, inbox: 0 }, { revalidate: false })
      },
      trust: trust ?? null,
      refreshTrust,
      kyc,
      setKyc,
      openVerify,
    }),
    [me, phone, tab, setTab, lang, setLang, t, mutate, openThread, wide, backend, toast, openVoice, freeForRoom, freeForRandom, registerRandom, minimiseRandom, onSignedOut, thread, group, minimiseVoice, reportListeners, voice?.id, myPresence, allBadges, refreshBadges, trust, refreshTrust, kyc, openVerify],
  )

  const randomVisible = tab === 'random' && !thread && !group
  const voiceOpen = !!voice?.expanded
  const busyScreen = (randomVisible && immersive) || thread !== null || group !== null
  const hideTabs = busyScreen || voiceOpen
  // The room's bar sits above the navigation (phones) or in the corner (wide screens): screens keep room for it.
  // One bar at a time: the room's, or (a room and a random chat never run together) the random chat's.
  const chatPill = !!chatBar && !voice && !randomVisible && !thread && !group
  const pillShown = (!!voice && !voice.expanded && !busyScreen) || chatPill

  return (
    <AppContext.Provider value={state}>
        <div className="min-h-dvh bg-background text-foreground">
          <div className="mx-auto flex max-w-[1520px]">
            <Sidebar className="hidden md:flex" />
            <main className="app-frame relative flex h-dvh min-w-0 flex-1 flex-col overflow-hidden">
              {/* Screens are layered, never stacked: a tab fading out must not push the next one aside. */}
              {/* While a voice room is full screen, what's under it can't be reached by keyboard or screen reader. */}
              <div className={cn('absolute inset-0 flex-col', randomVisible ? 'flex' : 'hidden', pillShown && 'pb-[5.5rem] md:pb-24')} inert={voiceOpen}>
                <RandomScreen active={randomVisible} />
              </div>
              <AnimatePresence mode="wait" initial={false} custom={randomVisible}>
                {!randomVisible && !thread && !group ? (
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
                        {tab === 'explore' ? <ExploreScreen /> : null}
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
                    initial={{ opacity: 0, x: rtl ? -24 : 24 }}
                    animate={{ opacity: 1, x: 0 }}
                    exit={{ opacity: 0, x: rtl ? -24 : 24 }}
                    transition={{ type: 'tween', duration: 0.2, ease: [0.2, 0, 0, 1] }}
                  >
                    <ScreenBoundary>
                      <Suspense fallback={<ScreenLoading />}>
                        <ThreadScreen
                          thread={thread}
                          onClose={closeThread}
                        />
                      </Suspense>
                    </ScreenBoundary>
                  </m.div>
                ) : null}
              </AnimatePresence>
              <AnimatePresence>
                {group ? (
                  <m.div
                    key={group}
                    className="absolute inset-0 z-20 flex flex-col bg-background"
                    initial={{ opacity: 0, x: rtl ? -24 : 24 }}
                    animate={{ opacity: 1, x: 0 }}
                    exit={{ opacity: 0, x: rtl ? -24 : 24 }}
                    transition={{ type: 'tween', duration: 0.2, ease: [0.2, 0, 0, 1] }}
                  >
                    <ScreenBoundary>
                      <Suspense fallback={<ScreenLoading />}>
                        <GroupScreen groupId={group} onClose={() => setGroup(null)} />
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
              <AnimatePresence>
                {chatPill ? <ChatPill key="chat" bar={chatBar!} onOpen={() => setTab('random')} onEnd={() => void randomCtl.current?.endChat(false)} /> : null}
              </AnimatePresence>
            </main>
            {/* 11: the friends panel stays beside a room or a random chat too. */}
            {wide ? (
              <Suspense fallback={null}>
                <RightPanel className="flex" />
              </Suspense>
            ) : null}
            {wide && mini ? (
              // Docked at the bottom, just left of the panel (the page is centred in at most 1520px).
              <div className="fixed bottom-0 end-[max(21rem,calc((100vw-95rem)/2+21rem))] z-40 flex h-[min(560px,80dvh)] w-[360px] flex-col overflow-hidden rounded-t-2xl border border-b-0 border-border bg-background shadow-e3">
                <ScreenBoundary key={mini.matchId}>
                  <Suspense fallback={<ScreenLoading />}>
                    <ThreadScreen thread={mini} onClose={closeMini} docked />
                  </Suspense>
                </ScreenBoundary>
              </div>
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
        {postUsed ? (
          <ScreenBoundary>
            <Suspense fallback={null}>
              <PostSheet target={postTarget} onClose={() => setPostTarget(null)} />
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
        {verifyUsed ? (
          <ScreenBoundary>
            <Suspense fallback={null}>
              <VerifySheet open={verify.open} why={verify.why} onClose={() => setVerify((v) => ({ ...v, open: false }))} />
            </Suspense>
          </ScreenBoundary>
        ) : null}
        {mannerLowUsed ? (
          <ScreenBoundary>
            <Suspense fallback={null}>
              <MannerLowSheet open={mannerLow} onClose={() => setMannerLow(false)} />
            </Suspense>
          </ScreenBoundary>
        ) : null}
        <IncomingCall
          // Their thread, or the random chat with them, on screen asks itself.
          hidden={(id) => {
            const r = randomCtl.current?.live()
            return thread?.matchId === id || mini?.matchId === id || (r?.phase === 'chat' && r.matchId === id)
          }}
          onAccepted={(caller, matchId) => void openThread(caller, matchId)}
        />
        {shop ? (
          <ScreenBoundary>
            <Suspense fallback={null}>
              <LooksSheet open={shop.open} tab={shop.tab} onClose={() => setShop({ ...shop, open: false })} />
            </Suspense>
          </ScreenBoundary>
        ) : null}
        {roomKey ? (
          <ScreenBoundary>
            <Suspense fallback={null}>
              <RoomPasswordDialog
                roomId={roomKey.kind === 'password' ? roomKey.id : null}
                onClose={() => setRoomKey({ ...roomKey, id: null })}
                onUnlocked={(id) => {
                  setRoomKey({ ...roomKey, id: null })
                  openVoice(id)
                }}
              />
              <JoinCodeDialog
                open={roomKey.kind === 'code' && roomKey.id !== null}
                onClose={() => setRoomKey({ ...roomKey, id: null })}
                onJoined={(id) => {
                  setRoomKey({ ...roomKey, id: null })
                  openVoice(id)
                }}
              />
            </Suspense>
          </ScreenBoundary>
        ) : null}
    </AppContext.Provider>
  )
}
