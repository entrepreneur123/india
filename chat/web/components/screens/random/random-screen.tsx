'use client'

import { AnimatePresence } from 'motion/react'
import * as m from 'motion/react-m'
import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Flag, MessageCircle, Shuffle, Undo2, Users, VenetianMask } from 'lucide-react'
import type { Match, Target } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { errorCode, flagEmoji } from '@/lib/format'
import { onPageVisibility, pageHidden } from '@/lib/visibility'
import { useApp, useReported } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Segmented } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { ScreenBoundary } from '@/components/ui/screen-boundary'
import { BellButton, InterestChips } from '@/components/social/bits'
import { OnlineCount } from '@/components/social/online'
import { retryImport } from '@/lib/retry-import'
import { MANNERS, shouldAskRating } from '@/lib/trust'
import { skippedRatings } from '@/components/trust/store'
import type { RateTarget } from '@/components/trust/rating-sheet'
import { TrustBadge } from '@/components/trust/badge'
import { COUNTRIES } from '@/lib/countries'
import { useCountryNames } from '@/components/screens/auth/country-select'
import { saveFlag, savedFlag } from '@/lib/flags'
import { showRecentSegment } from '@/components/screens/recent/segment'
import { GenderPassSheet, useGenderPass } from './gender-pass'
import { nameAge, type FrameId } from '@/lib/looks'
import { FrameAvatar } from '@/components/looks/frames'
import { useLook } from '@/components/looks/look-name'
import { UserAvatar } from '@/components/looks/user-avatar'
import { useBack } from '@/lib/back-stack'

// The chat (composer, bubbles, report sheet, video) downloads when a search starts, not with the lobby.
const loadChat = () => retryImport(() => import('@/components/screens/chat/chat-view'))
const ChatView = lazy(() => loadChat().then((mod) => ({ default: mod.ChatView })))
// The rating sheet (after a chat where both wrote) downloads with the chat too.
const loadRating = () => retryImport(() => import('@/components/trust/rating-sheet'))
const RateChatSheet = lazy(() => loadRating().then((mod) => ({ default: mod.RateChatSheet })))

const POLL_MS = 2000
/** A search in a background tab stops after this long (the server drops a queue spot after 20 s anyway). */
const HIDDEN_CANCEL_MS = 60_000
/** Errors that retrying can't fix; anything else (a weak network, a slow server) is retried. */
const RULE_ERRORS = new Set([
  'premium_required', 'banned', 'no_profile', 'not_signed_in', 'bad_target', 'under_18', 'not_allowed', 'verify_required', 'manner_low',
])
/** Waits between attempts while requests keep failing; the search gives up after a minute of failures. */
const RETRY_MS = [2000, 4000, 8000, 10_000]
const GIVE_UP_MS = 60_000

type Phase = 'lobby' | 'matching' | 'chat'
type Live = Match & { startedAt: number }

const fade = { initial: { opacity: 0 }, animate: { opacity: 1 }, exit: { opacity: 0 }, transition: { duration: 0.15 } }

/**
 * Random chat: lobby → matching (one request every 2 s while the browser tab is visible) → chat → exit choice.
 * Stays mounted while other tabs are open so a search or chat survives a quick look at Rooms or Recent.
 */
export function RandomScreen({ active }: { active: boolean }) {
  const backend = useBackend()
  const toast = useToast()
  const { t, lang, clock } = useI18n()
  const { me, setImmersive, setTab, freeForRandom, registerRandom, setChatBar, minimiseRandom, trust } = useApp()
  // Only a bought frame goes round my photo (no default ring: frames are a look you buy).
  const myFrame = (useLook(me?.id)?.frame ?? null) as FrameId | null
  // Rating a chat that ended (035): who both wrote in, and the one being rated now.
  const talked = useRef(new Map<string, boolean>())
  const ratedHere = useRef(new Set<string>())
  const [rate, setRate] = useState<RateTarget | null>(null)
  const [rateUsed, setRateUsed] = useState(false)
  const trustRef = useRef(trust)
  trustRef.current = trust
  const askRating = useCallback((m: Match) => {
    const ask = MANNERS && shouldAskRating({
      matchId: m.id,
      bothWrote: talked.current.get(m.id) === true,
      rated: [...ratedHere.current, ...(trustRef.current?.rated ?? [])],
      skipped: skippedRatings(),
    })
    talked.current.delete(m.id)
    if (!ask) return
    setRateUsed(true)
    setRate({ matchId: m.id, partner: m.partner })
  }, [])
  const [phase, setPhase] = useState<Phase>('lobby')
  const [target, setTarget] = useState<Target>('any')
  // 26: women or men only with a gender pass; back to everyone when it runs out.
  const pass = useGenderPass()
  const [passOpen, setPassOpen] = useState(false)
  useEffect(() => {
    if (!pass.until) setTarget('any')
  }, [pass.until])
  const [global, setGlobal] = useState(() => savedFlag('yaari_meet_global'))
  // 070: meet as a stranger (no name, photo or profile either way) or with my profile; each meets its own kind.
  const [anon, setAnon] = useState(() => savedFlag('yaari_meet_anon'))
  // 055: my country only, or everyone (an empty list). The server never pairs a my-country search with a foreigner.
  const nameOf = useCountryNames()
  const myCountry = COUNTRIES.find((c) => c.iso === me.country)
  const countries = useMemo(() => (global || !myCountry ? [] : [myCountry.iso]), [global, myCountry])
  const [match, setMatch] = useState<Live | null>(null)
  const [choiceOpen, setChoiceOpen] = useState(false)
  const [leaving, setLeaving] = useState(false)
  /** The last search request failed (weak network); it is being retried. */
  const [reconnecting, setReconnecting] = useState(false)
  // Each search has a number; only the newest one may open a chat (a reply can arrive after Cancel).
  const generation = useRef(0)
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  const searching = useRef(false)
  const tickRef = useRef<(() => void) | null>(null)
  const stopSignals = useRef<(() => void) | null>(null)
  const hiddenAt = useRef<number | null>(null)
  const hiddenTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  const stoppedWhileHidden = useRef(false)
  const visible = useRef(active)
  visible.current = active

  useEffect(() => setImmersive(phase !== 'lobby'), [phase, setImmersive])

  const endSignals = () => {
    stopSignals.current?.()
    stopSignals.current = null
  }

  const startMatching = useCallback(() => {
    void loadChat().catch(() => undefined)
    if (MANNERS) void loadRating().catch(() => undefined)
    const g = ++generation.current
    searching.current = true
    setReconnecting(false)
    setPhase('matching')
    let busy = false // one request at a time (the poll timer and a match signal can both ask)
    let failures = 0
    let failingSince: number | null = null
    const tick = async () => {
      if (g !== generation.current || busy) return
      if (pageHidden()) return // paused; the visibility listener resumes (or cancels) the search
      busy = true
      clearTimeout(timer.current)
      let m: Match | null
      try {
        m = await backend.findMatch(target, lang, countries, anon)
      } catch (err) {
        busy = false
        if (g !== generation.current) return
        // A weak network or a slow server: keep trying with growing waits, for up to a minute.
        const code = errorCode(err)
        failingSince ??= Date.now()
        if (!RULE_ERRORS.has(code) && Date.now() - failingSince < GIVE_UP_MS) {
          setReconnecting(true)
          timer.current = setTimeout(() => void tick(), RETRY_MS[Math.min(failures++, RETRY_MS.length - 1)])
          return
        }
        searching.current = false
        tickRef.current = null
        endSignals()
        setReconnecting(false)
        setPhase('lobby')
        if (code === 'premium_required') {
          setPassOpen(true) // the pass ran out on the server first
          void pass.mutate()
        } else toast.error(err)
        return
      }
      busy = false
      failures = 0
      failingSince = null
      setReconnecting(false)
      if (g !== generation.current) {
        if (m) backend.abandon(m.id) // paired just as the user cancelled: end it so the other person isn't left waiting
        // A request that landed after Cancel put us back in the queue: take us out again (unless searching anew).
        else if (!searching.current) backend.cancelSearch().catch(() => undefined)
        return
      }
      if (m) {
        searching.current = false
        tickRef.current = null
        endSignals()
        // A stranger is shown under one name in every screen (the chat, its bar, the rating sheet).
        const partner = m.partner.anon ? { ...m.partner, name: t('anon.name') } : m.partner
        setMatch({ ...m, partner, startedAt: Date.now() })
        setPhase('chat')
        if (!visible.current) {
          setTab('random') // matched while looking at another tab: bring the chat to the front
          toast.show(t('match.with', { name: m.partner.name }), 'success')
        }
        return
      }
      timer.current = setTimeout(() => void tick(), POLL_MS)
    }
    tickRef.current = () => void tick()
    // Someone paired with us: ask now instead of at the next 2 s poll.
    endSignals()
    stopSignals.current = backend.matchSignals(() => void tick())
    void tick()
  }, [backend, target, lang, countries, anon, toast, setTab, t, pass.mutate])

  const cancelMatching = useCallback(() => {
    generation.current++
    searching.current = false
    tickRef.current = null
    endSignals()
    setReconnecting(false)
    clearTimeout(timer.current)
    setPhase('lobby')
    backend.cancelSearch().catch(() => undefined)
  }, [backend])

  // Searching only while someone is looking: a hidden browser tab pauses the polling, and after 60 s hidden the
  // search is cancelled (so nobody gets matched with a person who walked away).
  useEffect(
    () =>
      onPageVisibility((shown) => {
        if (!shown) {
          if (!searching.current) return
          clearTimeout(timer.current)
          hiddenAt.current = Date.now()
          clearTimeout(hiddenTimer.current)
          hiddenTimer.current = setTimeout(() => {
            if (!searching.current) return
            stoppedWhileHidden.current = true
            cancelMatching()
          }, HIDDEN_CANCEL_MS)
          return
        }
        clearTimeout(hiddenTimer.current)
        const away = hiddenAt.current ? Date.now() - hiddenAt.current : 0
        hiddenAt.current = null
        if (searching.current && away > HIDDEN_CANCEL_MS) {
          stoppedWhileHidden.current = true
          cancelMatching() // background timers can run late
        }
        if (stoppedWhileHidden.current) {
          stoppedWhileHidden.current = false
          toast.show(t('matching.stopped'))
          return
        }
        if (searching.current) {
          clearTimeout(timer.current)
          tickRef.current?.()
        }
      }),
    [cancelMatching, toast, t],
  )

  // Leaving the page mid-search takes us out of the queue (only then: after sign-out there is no session to ask with).
  useEffect(
    () => () => {
      const wasSearching = searching.current
      generation.current++
      searching.current = false
      endSignals()
      clearTimeout(timer.current)
      clearTimeout(hiddenTimer.current)
      if (wasSearching) backend.cancelSearch().catch(() => undefined)
    },
    [backend],
  )

  const closeChat = () => {
    setChoiceOpen(false)
    setMatch(null)
    setPhase('lobby')
  }

  // Reported from a profile sheet or Recent while chatting with them: the server already ended the chat.
  useReported((id) => {
    if (match?.partner.id === id) closeChat()
  })

  const leavingRef = useRef(false) // a double tap must not leave twice (or start two searches)
  /** `ended`: the partner already left, so the chat is over on the server too and nothing needs sending. */
  const leave = async (next: boolean, ended = false) => {
    if (!match || leavingRef.current) return
    leavingRef.current = true
    setLeaving(true)
    try {
      if (!ended) await backend.leave(match.id)
    } catch (err) {
      if (errorCode(err) !== 'not_found') {
        // "not_found": the chat no longer exists (the other person deleted their account) — that is done too.
        toast.error(err)
        return
      }
    } finally {
      leavingRef.current = false
      setLeaving(false)
    }
    const ended_ = match
    closeChat()
    askRating(ended_)
    if (next) startMatching()
  }

  // One live session at a time: the shell asks here before a room opens (lib/live-session.ts).
  // Back does what the screen's own back does: stops the search, or asks "next or lobby?" in a chat.
  useBack(active && phase !== 'lobby', () => (phase === 'matching' ? cancelMatching() : setChoiceOpen(true)))
  const now = useRef({ phase, match, cancelMatching, closeChat, askRating })
  now.current = { phase, match, cancelMatching, closeChat, askRating }
  useEffect(() => {
    registerRandom({
      live: () => {
        const { phase, match } = now.current
        return phase === 'chat' && match ? { phase: 'chat', partner: match.partner.name, matchId: match.id } : phase === 'matching' ? { phase: 'searching' } : { phase: 'idle' }
      },
      cancelSearch: () => now.current.cancelMatching(),
      endChat: async (force) => {
        const { match } = now.current
        if (!match) return true
        try {
          await backend.leave(match.id)
        } catch (err) {
          // Opening a room ends the chat here anyway: inside the exit lock the server ends it (and this ends it too
          // once the lock allows).
          if (force) backend.abandon(match.id)
          else if (errorCode(err) !== 'not_found') {
            toast.error(err)
            return false
          }
        }
        now.current.closeChat()
        // Ended from its bar: ask about it. A room opening (force) goes straight to the room.
        if (!force) now.current.askRating(match)
        return true
      },
    })
    return () => registerRandom(null)
  }, [registerRandom, backend, toast])

  // The chat's bar for the other tabs: who, typing, unread (the chat view reports the last two).
  const [status, setStatus] = useState({ typing: false, unread: 0 })
  const barPartner = phase === 'chat' ? match?.partner ?? null : null
  useEffect(() => setChatBar(barPartner ? { partner: barPartner, ...status } : null), [barPartner, status, setChatBar])
  useEffect(() => () => setChatBar(null), [setChatBar])

  /** Start from the lobby: out of my room first (after a confirm), then the search. */
  const start = () => void freeForRandom().then((ok) => ok && startMatching())

  const gender: { value: Target; label: string; locked?: boolean }[] = [
    { value: 'any', label: t('lobby.everyone') },
    { value: 'female', label: t('lobby.women'), locked: !pass.until },
    { value: 'male', label: t('lobby.men'), locked: !pass.until },
  ]

  return (
    <div className="relative flex min-h-0 flex-1 flex-col" aria-hidden={!active}>
      <AnimatePresence mode="wait" initial={false}>
        {phase === 'lobby' ? (
          <m.section
            key="lobby"
            className="relative isolate flex min-h-0 flex-1 flex-col items-center justify-center overflow-y-auto px-4 pt-10 pb-24 md:pb-10"
            {...fade}
            aria-labelledby="lobby-title"
          >
            <BellButton className="absolute top-3 inset-e-3 md:hidden" />
            <div className="flex w-full max-w-md flex-col items-center text-center">
              {anon ? (
                <span className="grid size-32 place-items-center rounded-full bg-muted text-muted-foreground" aria-hidden>
                  <VenetianMask className="size-16" strokeWidth={1.5} />
                </span>
              ) : (
                <FrameAvatar frame={myFrame} userId={me.id} name={me.name} src={me.photoUrl} size={128} />
              )}
              <p className="mt-5 text-lg font-semibold">
                {anon ? t('anon.name') : `${me.name}, ${me.age}`} <span aria-hidden>{flagEmoji(me.country)}</span>
              </p>
              <h1 id="lobby-title" className="mt-8 text-3xl font-bold tracking-tight text-balance sm:text-4xl">
                {t('lobby.title')}
              </h1>
              <p className="mt-2 text-[15px] leading-relaxed text-pretty text-muted-foreground">{t('lobby.subtitle')}</p>
              <OnlineCount active={active} className="mt-4" />

              <Segmented
                className="mt-8 w-full"
                size="lg"
                label={t('anon.label')}
                value={anon ? 'anon' : 'profile'}
                options={[
                  { value: 'profile', label: t('anon.profile') },
                  { value: 'anon', label: t('anon.anon') },
                ]}
                onChange={(v) => {
                  setAnon(v === 'anon')
                  saveFlag('yaari_meet_anon', v === 'anon')
                }}
              />
              <p className="mt-2 text-xs text-muted-foreground">{t(anon ? 'anon.anonHint' : 'anon.profileHint')}</p>
              <Segmented
                className="mt-3 w-full"
                label={t('lobby.who')}
                lockedLabel={t('pass.title')}
                value={target}
                options={gender}
                onChange={(v) => (v === 'any' || pass.until ? setTarget(v) : setPassOpen(true))}
              />
              {pass.until ? <p className="mt-2 text-xs text-muted-foreground">{t('pass.on', { time: clock(pass.until) })}</p> : null}
              {myCountry ? (
                <Segmented
                  className="mt-3 w-full"
                  label={t('countries.label')}
                  value={global ? 'global' : 'mine'}
                  options={[
                    { value: 'mine', label: `${flagEmoji(myCountry.iso)} ${nameOf(myCountry)}` },
                    { value: 'global', label: `🌐 ${t('countries.anywhere')}` },
                  ]}
                  onChange={(v) => {
                    setGlobal(v === 'global')
                    saveFlag('yaari_meet_global', v === 'global')
                  }}
                />
              ) : null}

              <m.button
                type="button"
                onClick={start}
                onPointerEnter={() => void loadChat().catch(() => undefined)}
                whileTap={{ scale: 0.98 }}
                transition={{ duration: 0.1 }}
                className="mt-4 flex h-14 w-full items-center justify-center gap-3 rounded-full bg-primary text-lg font-semibold text-primary-foreground transition-[background-color,box-shadow] duration-150 hover:bg-primary-hover hover:shadow-e2"
              >
                <Shuffle className="size-5.5" aria-hidden />
                <span>{t('lobby.start')}</span>
              </m.button>

              {/* 49: many-to-many random chat starts from a friends' group (Recent → Groups → Party match). */}
              <button
                type="button"
                onClick={() => (showRecentSegment('groups'), setTab('recent'))}
                className="mt-3 flex items-center gap-1.5 text-sm font-semibold text-primary-text hover:underline"
              >
                <Users className="size-4" aria-hidden /> {t('lobby.party')}
              </button>

              <ul className="mt-8 flex flex-wrap justify-center gap-2 text-xs font-medium text-muted-foreground">
                <Pill icon={<MessageCircle className="size-3.5" aria-hidden />}>{t('lobby.textFirst')}</Pill>
                <Pill icon={<Flag className="size-3.5" aria-hidden />}>{t('lobby.reportLeave')}</Pill>
              </ul>
            </div>
          </m.section>
        ) : phase === 'matching' ? (
          <Matching key="matching" anon={anon} target={target} countries={countries} reconnecting={reconnecting} onCancel={cancelMatching} active={active} />
        ) : match ? (
          <m.div key={match.id} className="relative flex min-h-0 flex-1 flex-col" {...fade}>
            <ScreenBoundary>
              <Suspense fallback={<Matching anon={anon} target={target} countries={countries} onCancel={() => undefined} />}>
                <ChatView
                  matchId={match.id}
                  partner={match.partner}
                  mode="random"
                  visible={active}
                  startedAt={match.startedAt}
                  onExit={() => setChoiceOpen(true)}
                  onEnd={(next) => void leave(next, true)}
                  onLeave={(next) => void leave(next)}
                  onReported={closeChat}
                  onMinimise={minimiseRandom}
                  onStatus={setStatus}
                  onTalked={(both) => void talked.current.set(match.id, both)}
                />
              </Suspense>
            </ScreenBoundary>
            <MatchReveal key={match.id} partner={match.partner} />
          </m.div>
        ) : null}
      </AnimatePresence>

      {rateUsed ? (
        <Suspense fallback={null}>
          <RateChatSheet
            target={rate}
            onClose={(rated) => {
              if (rated && rate) ratedHere.current.add(rate.matchId)
              setRate(null)
            }}
          />
        </Suspense>
      ) : null}

      <GenderPassSheet open={passOpen} onClose={() => setPassOpen(false)} onGot={() => void pass.mutate()} />

      <Modal open={choiceOpen} onClose={() => setChoiceOpen(false)} variant="sheet" title={t('leave.title')}>
        <div className="flex flex-col gap-2.5 p-6 pt-4">
          <Button variant="brand" size="lg" loading={leaving} onClick={() => void leave(true)} data-autofocus>
            <Shuffle className="size-5" aria-hidden /> {t('leave.next')}
          </Button>
          <Button variant="secondary" size="lg" disabled={leaving} onClick={() => void leave(false)}>
            <Undo2 className="size-5 rtl:-scale-x-100" aria-hidden /> {t('leave.lobby')}
          </Button>
        </div>
      </Modal>
    </div>
  )
}

function Pill({ icon, children }: { icon: React.ReactNode; children: React.ReactNode }) {
  return (
    <li className="flex items-center gap-1.5 rounded-full border border-border px-3 py-1.5">
      {icon}
      {children}
    </li>
  )
}

function Matching({
  anon,
  target,
  countries,
  reconnecting = false,
  onCancel,
  active = false,
}: {
  anon: boolean
  target: Target
  countries: string[]
  reconnecting?: boolean
  onCancel: () => void
  active?: boolean
}) {
  const { me } = useApp()
  const myFrame = (useLook(me?.id)?.frame ?? null) as FrameId | null
  const { t } = useI18n()
  const who = target === 'any' ? t('lobby.everyone') : target === 'female' ? t('lobby.women') : t('lobby.men')
  return (
    <m.section
      className="relative flex min-h-0 flex-1 flex-col items-center justify-center overflow-hidden px-6 py-10"
      {...fade}
      aria-live="polite"
    >
      <span className="rounded-full border border-border px-3.5 py-1.5 text-xs font-medium text-muted-foreground">
        {countries.length ? `${who} · ${flagEmoji(countries[0])}` : t('matching.scope', { who })}
      </span>

      <div className="relative mt-12 grid size-56 place-items-center">
        {/* Two soft rings growing out of the avatar: transform and opacity only, cheap on low-end phones. */}
        <span className="absolute inset-6 animate-ping-soft rounded-full border-2 border-primary-text/50" aria-hidden />
        <span className="absolute inset-6 animate-ping-soft rounded-full border-2 border-primary-text/50 [animation-delay:1.2s]" aria-hidden />
        {anon ? (
          <span className="relative grid size-28 place-items-center rounded-full bg-muted text-muted-foreground" aria-hidden>
            <VenetianMask className="size-14" strokeWidth={1.5} />
          </span>
        ) : (
          <FrameAvatar frame={myFrame} userId={me.id} name={me.name} src={me.photoUrl} size={112} className="relative" />
        )}
      </div>

      <h2 className="mt-12 text-2xl font-bold tracking-tight">{t('matching.title')}</h2>
      <p className="mt-2 h-5 text-sm text-muted-foreground">
        {reconnecting ? t('matching.reconnecting') : null}
      </p>
      {reconnecting ? null : <OnlineCount active={active} className="mt-1" />}
      <Button variant="outline" size="lg" className="mt-8 w-full max-w-xs" onClick={onCancel}>
        {t('matching.cancel')}
      </Button>
    </m.section>
  )
}

/** The partner's card shows over the new chat for a moment, then gets out of the way. */
function MatchReveal({ partner }: { partner: Match['partner'] }) {
  const { t } = useI18n()
  const [show, setShow] = useState(true)
  useEffect(() => {
    const id = setTimeout(() => setShow(false), 1500)
    return () => clearTimeout(id)
  }, [])
  return (
    <AnimatePresence>
      {show ? (
        <m.button
          type="button"
          onClick={() => setShow(false)}
          className="absolute inset-0 z-40 grid place-items-center bg-overlay"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.15 }}
          aria-label={t('match.with', { name: partner.name })}
        >
          <m.span
            className="flex flex-col items-center gap-4 rounded-[28px] bg-card px-10 py-8 shadow-e3"
            initial={{ scale: 0.92, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            exit={{ scale: 0.96, opacity: 0 }}
            transition={{ type: 'tween', duration: 0.2, ease: [0.2, 0, 0, 1] }}
          >
            <span className="text-xs font-semibold tracking-wide text-primary-text uppercase">{t('match.its')}</span>
            {partner.anon ? (
              <span className="grid size-28 place-items-center rounded-full bg-muted text-muted-foreground" aria-hidden>
                <VenetianMask className="size-14" strokeWidth={1.5} />
              </span>
            ) : (
              <UserAvatar userId={partner.id} name={partner.name} src={partner.photoUrl} size={112} country={partner.country} />
            )}
            <span className="flex items-center gap-1.5 text-xl font-bold">
              {partner.anon ? partner.name : nameAge(partner.name, partner.age)}
              {partner.anon ? null : <TrustBadge userId={partner.id} size={20} />}
            </span>
            {partner.interests?.length ? (
              <InterestChips tags={partner.interests.slice(0, 3)} className="justify-center" />
            ) : null}
          </m.span>
        </m.button>
      ) : null}
    </AnimatePresence>
  )
}
