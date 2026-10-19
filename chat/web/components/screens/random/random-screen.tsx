'use client'

import { AnimatePresence, motion } from 'motion/react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Flag, MessageCircle, Shuffle, Undo2, Video } from 'lucide-react'
import type { Match, Target } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { SHOWCASE_FLAGS } from '@/lib/countries'
import { errorCode, flagEmoji } from '@/lib/format'
import { useApp } from '@/components/app-state'
import { Aurora } from '@/components/ui/ambient'
import { Avatar } from '@/components/ui/avatar'
import { Segmented } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { ChatView } from '@/components/screens/chat/chat-view'

const POLL_MS = 2000

type Phase = 'lobby' | 'matching' | 'chat'
type Live = Match & { startedAt: number }

/**
 * Random chat: lobby → matching (one request every 2 s) → chat → exit choice. Stays mounted while other tabs
 * are open so a search or chat survives a quick look at Rooms or Recent.
 */
export function RandomScreen({ active }: { active: boolean }) {
  const backend = useBackend()
  const toast = useToast()
  const { me, t, canChooseGender, openPaywall, setImmersive } = useApp()
  const [phase, setPhase] = useState<Phase>('lobby')
  const [target, setTarget] = useState<Target>('any')
  const [match, setMatch] = useState<Live | null>(null)
  const [choiceOpen, setChoiceOpen] = useState(false)
  const [leaving, setLeaving] = useState(false)
  // Each search has a number; only the newest one may open a chat (a reply can arrive after Cancel).
  const generation = useRef(0)
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  useEffect(() => setImmersive(phase !== 'lobby'), [phase, setImmersive])
  useEffect(() => {
    if (!canChooseGender) setTarget('any') // the pass or subscription ran out
  }, [canChooseGender])

  const startMatching = useCallback(() => {
    const g = ++generation.current
    setPhase('matching')
    const tick = async () => {
      if (g !== generation.current) return
      let m: Match | null
      try {
        m = await backend.findMatch(target)
      } catch (err) {
        if (g !== generation.current) return
        setPhase('lobby')
        if (errorCode(err) === 'premium_required') openPaywall()
        else toast.error(err)
        return
      }
      if (g !== generation.current) {
        if (m) backend.abandon(m.id) // paired just as the user cancelled: end it so the other person isn't left waiting
        return
      }
      if (m) {
        setMatch({ ...m, startedAt: Date.now() })
        setPhase('chat')
        return
      }
      timer.current = setTimeout(() => void tick(), POLL_MS)
    }
    void tick()
  }, [backend, target, openPaywall, toast])

  const cancelMatching = useCallback(() => {
    generation.current++
    clearTimeout(timer.current)
    setPhase('lobby')
    backend.cancelSearch().catch(() => undefined)
  }, [backend])

  // Leaving the page mid-search takes us out of the queue.
  useEffect(
    () => () => {
      generation.current++
      clearTimeout(timer.current)
      backend.cancelSearch().catch(() => undefined)
    },
    [backend],
  )

  const closeChat = () => {
    setChoiceOpen(false)
    setMatch(null)
    setPhase('lobby')
  }

  const leave = async (next: boolean) => {
    if (!match) return
    setLeaving(true)
    try {
      await backend.leave(match.id)
    } catch (err) {
      if (errorCode(err) !== 'not_found') {
        // "not_found": the chat no longer exists (the other person deleted their account) — that is done too.
        toast.error(err)
        setLeaving(false)
        return
      }
    }
    setLeaving(false)
    closeChat()
    if (next) startMatching()
  }

  const gender: { value: Target; label: string; locked?: boolean }[] = [
    { value: 'any', label: t('lobby.everyone') },
    { value: 'female', label: t('lobby.women'), locked: !canChooseGender },
    { value: 'male', label: t('lobby.men'), locked: !canChooseGender },
  ]

  return (
    <div className="relative flex min-h-0 flex-1 flex-col" aria-hidden={!active}>
      <AnimatePresence mode="wait" initial={false}>
        {phase === 'lobby' ? (
          <motion.section
            key="lobby"
            className="relative isolate flex min-h-0 flex-1 flex-col items-center justify-center overflow-hidden px-6 pt-10 pb-24 md:pb-10"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0, scale: 0.98 }}
            transition={{ duration: 0.3 }}
            aria-labelledby="lobby-title"
          >
            <Aurora />
            <div className="flex w-full max-w-md flex-col items-center text-center">
              <GlowRing>
                <Avatar name={me.name} src={me.photoUrl} size={148} />
              </GlowRing>
              <p className="mt-6 text-xl font-bold">
                {me.name}, {me.age} <span aria-hidden>{flagEmoji(me.country)}</span>
              </p>
              <h1 id="lobby-title" className="mt-6 text-3xl font-extrabold tracking-tight text-balance sm:text-4xl">
                {t('lobby.title')}
              </h1>
              <p className="mt-2 text-sm leading-relaxed text-muted-foreground text-pretty">{t('lobby.subtitle')}</p>

              <Segmented
                className="mt-8 w-full bg-ink-foreground/8 ring-1 ring-ink-foreground/10 backdrop-blur-md"
                size="lg"
                label="Who to meet"
                value={target}
                options={gender}
                onChange={(v) => {
                  if (v !== 'any' && !canChooseGender) openPaywall()
                  else setTarget(v)
                }}
              />

              <motion.button
                type="button"
                onClick={startMatching}
                whileHover={{ y: -3, scale: 1.01 }}
                whileTap={{ scale: 0.96 }}
                transition={{ type: 'spring', stiffness: 420, damping: 22 }}
                className="group relative mt-4 flex h-16 w-full items-center justify-center gap-3 overflow-hidden rounded-full bg-brand text-lg font-bold text-primary-foreground shadow-[0_20px_50px_-14px_rgb(255_61_104/0.85)] transition-shadow hover:shadow-[0_26px_60px_-12px_rgb(255_61_104/0.95)]"
              >
                <span
                  className="absolute inset-y-0 -left-1/3 w-1/3 animate-shimmer bg-gradient-to-r from-transparent via-white/35 to-transparent"
                  aria-hidden
                />
                <Shuffle className="relative size-5.5 transition-transform group-hover:rotate-180" aria-hidden />
                <span className="relative">{t('lobby.start')}</span>
              </motion.button>

              <ul className="mt-8 flex flex-wrap justify-center gap-2 text-xs font-medium text-muted-foreground">
                <Pill icon={<MessageCircle className="size-3.5" aria-hidden />}>Text first</Pill>
                <Pill icon={<Video className="size-3.5" aria-hidden />}>Video only if both agree</Pill>
                <Pill icon={<Flag className="size-3.5" aria-hidden />}>Report &amp; leave anytime</Pill>
              </ul>
            </div>
          </motion.section>
        ) : phase === 'matching' ? (
          <Matching key="matching" target={target} onCancel={cancelMatching} />
        ) : match ? (
          <motion.div
            key={match.id}
            className="relative flex min-h-0 flex-1 flex-col"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
          >
            <ChatView
              matchId={match.id}
              partner={match.partner}
              mode="random"
              startedAt={match.startedAt}
              onExit={() => setChoiceOpen(true)}
              onReported={closeChat}
            />
            <MatchReveal key={match.id} partner={match.partner} />
          </motion.div>
        ) : null}
      </AnimatePresence>

      <Modal open={choiceOpen} onClose={() => setChoiceOpen(false)} variant="sheet" title="Leave this chat?" night>
        <div className="flex flex-col gap-2.5 p-6 pt-4">
          <Button variant="brand" size="lg" loading={leaving} onClick={() => void leave(true)} data-autofocus>
            <Shuffle className="size-5" aria-hidden /> Next match
          </Button>
          <Button variant="secondary" size="lg" disabled={leaving} onClick={() => void leave(false)}>
            <Undo2 className="size-5" aria-hidden /> Back to lobby
          </Button>
        </div>
      </Modal>
    </div>
  )
}

function Pill({ icon, children }: { icon: React.ReactNode; children: React.ReactNode }) {
  return (
    <li className="flex items-center gap-1.5 rounded-full bg-ink-foreground/6 px-3 py-1.5 ring-1 ring-ink-foreground/10 backdrop-blur-sm">
      {icon}
      {children}
    </li>
  )
}

/** Avatar inside a slowly turning gradient ring with a soft glow. */
function GlowRing({ children }: { children: React.ReactNode }) {
  return (
    <motion.div
      className="relative grid place-items-center rounded-full p-1.5"
      initial={{ scale: 0.85, opacity: 0 }}
      animate={{ scale: 1, opacity: 1 }}
      transition={{ type: 'spring', stiffness: 220, damping: 18 }}
    >
      <span className="absolute -inset-6 rounded-full bg-primary/25 blur-3xl" aria-hidden />
      <span
        className="absolute inset-0 animate-spin-slow rounded-full"
        style={{ background: 'conic-gradient(from 0deg, #ff3d68, #ff7a45, transparent 55%, #ff3d68)' }}
        aria-hidden
      />
      <span className="relative rounded-full bg-background p-1.5">{children}</span>
    </motion.div>
  )
}

function Matching({ target, onCancel }: { target: Target; onCancel: () => void }) {
  const { me } = useApp()
  const flags = SHOWCASE_FLAGS.slice(0, 10)
  return (
    <motion.section
      className="relative isolate flex min-h-0 flex-1 flex-col items-center justify-center overflow-hidden px-6 py-10"
      initial={{ opacity: 0, scale: 1.02 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.3 }}
      aria-live="polite"
    >
      <Aurora intensity="soft" />
      <span className="rounded-full bg-ink-foreground/8 px-3.5 py-1.5 text-xs font-semibold text-muted-foreground ring-1 ring-ink-foreground/10">
        {target === 'any' ? 'Everyone' : target === 'female' ? 'Women' : 'Men'} · Worldwide
      </span>

      <div className="relative mt-10 grid size-[300px] place-items-center sm:size-[360px]">
        {[0, 1, 2].map((i) => (
          <motion.span
            key={i}
            className="absolute inset-0 rounded-full border border-primary/40"
            initial={{ scale: 0.35, opacity: 0.8 }}
            animate={{ scale: 1.1, opacity: 0 }}
            transition={{ duration: 3, repeat: Infinity, delay: i, ease: 'easeOut' }}
            aria-hidden
          />
        ))}
        <span className="absolute inset-[12%] rounded-full border border-dashed border-ink-foreground/12" aria-hidden />
        <motion.div
          className="absolute inset-[12%]"
          animate={{ rotate: 360 }}
          transition={{ duration: 40, repeat: Infinity, ease: 'linear' }}
          aria-hidden
        >
          {flags.map((code, i) => {
            const angle = (i / flags.length) * 2 * Math.PI
            return (
              <span
                key={code}
                className="absolute grid size-10 -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full bg-card text-lg shadow-lg ring-1 ring-border"
                style={{ left: `${50 + 50 * Math.cos(angle)}%`, top: `${50 + 50 * Math.sin(angle)}%` }}
              >
                <motion.span animate={{ rotate: -360 }} transition={{ duration: 40, repeat: Infinity, ease: 'linear' }}>
                  {flagEmoji(code)}
                </motion.span>
              </span>
            )
          })}
        </motion.div>
        <motion.div animate={{ scale: [1, 1.05, 1] }} transition={{ duration: 1.8, repeat: Infinity, ease: 'easeInOut' }}>
          <Avatar name={me.name} src={me.photoUrl} size={112} className="ring-4 ring-primary/50 rounded-full" />
        </motion.div>
      </div>

      <h2 className="mt-10 text-2xl font-extrabold tracking-tight">
        Finding someone
        <motion.span animate={{ opacity: [0.2, 1, 0.2] }} transition={{ duration: 1.4, repeat: Infinity }}>
          …
        </motion.span>
      </h2>
      <p className="mt-1.5 text-sm text-muted-foreground">Usually takes a few seconds</p>
      <Button variant="glass" size="lg" className="mt-8 w-full max-w-xs" onClick={onCancel}>
        Cancel
      </Button>
    </motion.section>
  )
}

/** The partner's card springs in over the new chat for a moment, then gets out of the way. */
function MatchReveal({ partner }: { partner: Match['partner'] }) {
  const [show, setShow] = useState(true)
  useEffect(() => {
    const id = setTimeout(() => setShow(false), 1500)
    return () => clearTimeout(id)
  }, [])
  return (
    <AnimatePresence>
      {show ? (
        <motion.button
          type="button"
          onClick={() => setShow(false)}
          className="absolute inset-0 z-40 grid place-items-center bg-overlay backdrop-blur-md"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          aria-label={`You matched with ${partner.name}`}
        >
          <motion.span
            className="flex flex-col items-center gap-4 rounded-[32px] bg-card px-10 py-8 shadow-2xl ring-1 ring-border"
            initial={{ scale: 0.6, y: 40, rotate: -4 }}
            animate={{ scale: 1, y: 0, rotate: 0 }}
            exit={{ scale: 0.9, y: -20, opacity: 0 }}
            transition={{ type: 'spring', stiffness: 300, damping: 18 }}
          >
            <span className="text-xs font-bold tracking-widest text-primary uppercase">It’s a match</span>
            <Avatar name={partner.name} src={partner.photoUrl} size={120} country={partner.country} />
            <span className="text-xl font-extrabold">
              {partner.name}, {partner.age}
            </span>
            {partner.city ? <span className="-mt-3 text-sm text-muted-foreground">{partner.city}</span> : null}
          </motion.span>
        </motion.button>
      ) : null}
    </AnimatePresence>
  )
}
